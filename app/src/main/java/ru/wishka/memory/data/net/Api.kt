package ru.wishka.memory.data.net

import org.json.JSONArray
import org.json.JSONObject
import ru.wishka.memory.data.Directory
import ru.wishka.memory.data.Message
import ru.wishka.memory.data.Post
import ru.wishka.memory.data.StateStore
import ru.wishka.memory.data.User

/**
 * Клиент API Memory (контракт — docs/openapi.yaml). Знает пути и формат,
 * но не то, как запрос летит: это Transport (OkHttp или демо-сервер).
 *
 * Записи и сообщения в JSON — тем же кодеком, что сохранение на телефоне
 * (StateStore.Codec): один формат и на диске, и в сети.
 *
 * Все изменения несут Idempotency-Key: если ответ потерялся и очередь
 * повторит запрос, сервер вернёт тот же результат, а не создаст дубль.
 */
class Api(
    private val transport: Transport,
    private val me: () -> User,
    /** Вход на сервер (токены); null — без входа (тесты сети без авторизации). */
    val session: Session? = null,
) {
    private fun codec() = StateStore.Codec(me())

    /**
     * Запрос с токеном; не 2xx — NetError, нет сети — NetError(Offline).
     * 401 — обновить токен и повторить один раз; не обновился — сессия
     * стёрта, наружу NetError(Auth).
     */
    suspend fun call(request: HttpRequest): HttpResponse {
        val first = raw(request)
        if (first.code != 401 || request.path.startsWith("/v1/auth/")) return checked(first)
        val s = session ?: return checked(first)
        if (s.refresh == null || !refreshTokens()) return checked(first)
        return checked(raw(request))
    }

    private suspend fun raw(request: HttpRequest): HttpResponse {
        val headers = request.headers.toMutableMap()
        session?.access?.let { headers["Authorization"] = "Bearer $it" }
        headers.putIfAbsent("Accept", "application/json")
        return try {
            transport.send(request.copy(headers = headers))
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw NetError.of(e)
        }
    }

    private fun checked(r: HttpResponse): HttpResponse {
        if (!r.ok) throw NetError.of(r)
        return r
    }

    /** Новая пара токенов по токену обновления. false — не вышло (вышли с этого устройства). */
    suspend fun refreshTokens(): Boolean {
        val s = session ?: return false
        val token = s.refresh ?: return false
        val r = raw(HttpRequest("POST", "/v1/auth/refresh", body = JSONObject().put("refresh", token).toString().toByteArray(), contentType = JSON))
        if (r.code == 401 || r.code == 403) {
            s.clear()
            return false
        }
        checked(r)
        s.save(parse(r))
        return true
    }

    // ── Вход и устройства ────────────────────────────────────────────

    /** Войти: логин, пароль, это устройство («Pixel 8», «android»). */
    suspend fun signIn(login: String, password: String, device: String, platform: String) {
        val body = JSONObject().put("login", login).put("password", password)
            .put("device", JSONObject().put("name", device).put("platform", platform))
        val r = call(HttpRequest("POST", "/v1/auth/login", body = body.toString().toByteArray(), contentType = JSON))
        session?.save(parse(r))
    }

    /**
     * Новый аккаунт на сервере (настоящем; демо-серверу не нужен — он
     * пускает любого). 409 — логин занят: NetError(Conflict).
     */
    suspend fun register(login: String, password: String, name: String, device: String, platform: String) {
        val body = JSONObject().put("login", login).put("password", password).put("name", name)
            .put("device", JSONObject().put("name", device).put("platform", platform))
        val r = call(HttpRequest("POST", "/v1/auth/register", body = body.toString().toByteArray(), contentType = JSON))
        session?.save(parse(r))
    }

    /** Выйти с этого устройства: токены на сервере отзываются. */
    suspend fun signOut() {
        runCatching { call(HttpRequest("POST", "/v1/auth/logout")) }
        session?.clear()
    }

    suspend fun devices(): List<DeviceInfo> {
        val arr = getJson("/v1/devices").optJSONArray("items") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { d ->
                DeviceInfo(d.optString("id"), d.optString("name"), d.optString("platform"), d.optLong("lastSeen"), d.optBoolean("current"))
            }
        }
    }

    suspend fun revokeDevice(id: String) {
        call(HttpRequest("DELETE", "/v1/devices/$id"))
    }

    suspend fun revokeOtherDevices() {
        call(HttpRequest("POST", "/v1/devices/revoke-others"))
    }

    /** Профиль с сервера; null — на сервере его ещё нет (первый вход). */
    suspend fun profile(): ProfileSync? = try {
        val o = getJson("/v1/me")
        ProfileSync(o, o.optLong("updatedAt"))
    } catch (e: NetError) {
        if (e.code == 404) null else throw e
    }

    // ── Люди ─────────────────────────────────────────────────────────

    /** Поиск людей на сервере (от 2 знаков): они же попадают в справочник. */
    suspend fun searchUsers(query: String): List<User> {
        val arr = getJson("/v1/users", mapOf("q" to query)).optJSONArray("items") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { Directory.remember(arr.optJSONObject(it)) }
    }

    /** Человек по номеру (имя, ключ шифрования). */
    suspend fun user(id: Int): User? = Directory.remember(getJson("/v1/users/$id"))

    /** Личная переписка с человеком: номер выбирает сервер, у пары она одна. */
    suspend fun directChat(userId: Int): Int =
        sendJson("POST", "/v1/chats/direct", JSONObject().put("user", userId), null).getInt("id")

    /** Свой открытый ключ — чтобы собеседники могли писать в секретную переписку. */
    suspend fun putKey(publicKey: ByteArray) {
        sendJson("PUT", "/v1/me/key", JSONObject().put("publicKey", java.util.Base64.getEncoder().encodeToString(publicKey)), null)
    }

    // ── Подписки ─────────────────────────────────────────────────────

    /** Подписаться: ответ — "following" (открытый профиль) или "requested" (заявка). */
    suspend fun follow(userId: Int): String = sendJson("PUT", "/v1/users/$userId/follow", JSONObject(), null).optString("state")

    private suspend fun people(path: String): List<User> {
        val arr = getJson(path).optJSONArray("items") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { Directory.remember(arr.optJSONObject(it)) }
    }

    /** На кого я подписан. */
    suspend fun following(): List<User> = people("/v1/me/following")

    /** Мои заявки, которые ещё ждут ответа. */
    suspend fun sentRequests(): List<User> = people("/v1/me/sent")

    /** Заявки ко мне. */
    suspend fun followRequests(): List<User> = people("/v1/me/requests")

    /** «Интересное»: записи людей, на которых я не подписан. */
    suspend fun explore(cursor: String?, limit: Int = PAGE): Page<Post> = posts(getJson("/v1/explore", query(cursor, limit)))

    // ── Комментарии и истории ────────────────────────────────────────

    /** Комментарии к записи по порядку (до 100 — больше под одной записью не показываем). */
    suspend fun comments(postId: Long): List<ru.wishka.memory.data.Comment> {
        val arr = getJson("/v1/posts/$postId/comments", mapOf("limit" to "100")).optJSONArray("items") ?: JSONArray()
        val c = codec()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { runCatching { c.commentOf(it) }.getOrNull() } }
    }

    /** Комментарий: ответ — он же с номером сервера. */
    suspend fun addComment(postId: Long, comment: ru.wishka.memory.data.Comment, key: String): ru.wishka.memory.data.Comment {
        val c = codec()
        val body = JSONObject().put("text", comment.text).put("replyTo", comment.replyTo)
        comment.sticker?.let { body.put("sticker", c.attachmentJson(it)) }
        return c.commentOf(sendJson("POST", "/v1/posts/$postId/comments", body, key))
    }

    /** Живые истории тех, на кого подписан, и свои; второе — смотрел ли я её. */
    suspend fun stories(): List<Pair<ru.wishka.memory.data.Story, Boolean>> {
        val arr = getJson("/v1/stories").optJSONArray("items") ?: JSONArray()
        val c = codec()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { o -> runCatching { c.storyOf(o) to o.optBoolean("seen") }.getOrNull() }
        }
    }

    /** Мои переписки на сервере (личные и группы) — JSON как есть. */
    suspend fun chats(): List<JSONObject> {
        val arr = getJson("/v1/chats").optJSONArray("items") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    /** Скачать загруженный файл (GET /v1/uploads/{id}) — фото чужой записи или сообщения. */
    suspend fun download(path: String): ByteArray = call(HttpRequest("GET", path, headers = mapOf("Accept" to "*/*"))).body

    private suspend fun getJson(path: String, query: Map<String, String> = emptyMap()): JSONObject =
        parse(call(HttpRequest("GET", path, query)))

    private suspend fun sendJson(method: String, path: String, body: JSONObject, key: String?): JSONObject {
        val headers = if (key != null) mapOf("Idempotency-Key" to key) else emptyMap()
        val r = call(HttpRequest(method, path, headers = headers, body = body.toString().toByteArray(Charsets.UTF_8), contentType = JSON))
        return if (r.body.isEmpty()) JSONObject() else parse(r)
    }

    private fun parse(r: HttpResponse): JSONObject =
        runCatching { JSONObject(r.text) }.getOrElse { throw NetError(NetError.Kind.BadData, r.code, "Не JSON") }

    private fun query(cursor: String?, limit: Int): Map<String, String> =
        buildMap { put("limit", limit.toString()); if (cursor != null) put("cursor", cursor) }

    private fun posts(o: JSONObject): Page<Post> {
        val c = codec()
        val arr = o.optJSONArray("items") ?: JSONArray()
        val items = (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { runCatching { c.postOf(it) }.getOrNull() } }
        return Page(items, o.optString("next").ifEmpty { null }.takeUnless { o.isNull("next") })
    }

    // ── Записи ───────────────────────────────────────────────────────

    /** Лента: новые сверху, по limit штук; cursor — из прошлой страницы. */
    suspend fun feed(cursor: String?, limit: Int = PAGE): Page<Post> = posts(getJson("/v1/feed", query(cursor, limit)))

    suspend fun userPosts(userId: Int, cursor: String?, limit: Int = PAGE): Page<Post> =
        posts(getJson("/v1/users/$userId/posts", query(cursor, limit)))

    /**
     * Опубликовать. media — уже загруженные файлы: путь вложения заменён
     * адресом загрузки (Upload-Location tus). Ответ — запись с номером сервера.
     */
    suspend fun createPost(post: Post, key: String): Post {
        val c = codec()
        val o = sendJson("POST", "/v1/posts", c.postJson(post), key)
        return c.postOf(o)
    }

    suspend fun like(postId: Long, liked: Boolean, key: String) {
        sendJson(if (liked) "PUT" else "DELETE", "/v1/posts/$postId/like", JSONObject(), key)
    }

    // ── Переписка ────────────────────────────────────────────────────

    /** История переписки: от новых к старым, before — курсор. */
    suspend fun messages(chatId: Int, cursor: String?, limit: Int = PAGE): Page<Message> {
        val o = getJson("/v1/chats/$chatId/messages", query(cursor, limit))
        val c = codec()
        val arr = o.optJSONArray("items") ?: JSONArray()
        val items = (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { runCatching { c.messageOf(it) }.getOrNull() } }
        return Page(items, o.optString("next").ifEmpty { null }.takeUnless { o.isNull("next") })
    }

    /** Любой запрос из очереди (группы, «прочитано», профиль): метод, путь, тело. */
    suspend fun send(method: String, path: String, body: JSONObject?, key: String): JSONObject {
        if (body == null) {
            val r = call(HttpRequest(method, path, headers = mapOf("Idempotency-Key" to key)))
            return if (r.body.isEmpty()) JSONObject() else parse(r)
        }
        return sendJson(method, path, body, key)
    }

    /** Сообщение как есть (JSON) — ответ сервера тоже JSON: с номером и sentAt. */
    suspend fun sendRaw(chatId: Int, message: JSONObject, key: String): JSONObject =
        sendJson("POST", "/v1/chats/$chatId/messages", message, key)

    suspend fun sendMessage(chatId: Int, message: Message, key: String): Message {
        val c = codec()
        return c.messageOf(sendJson("POST", "/v1/chats/$chatId/messages", c.messageJson(message), key))
    }

    companion object {
        const val PAGE = 20
        const val JSON = "application/json; charset=utf-8"
    }
}
