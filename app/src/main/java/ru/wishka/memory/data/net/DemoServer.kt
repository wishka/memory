package ru.wishka.memory.data.net

import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import ru.wishka.memory.data.Demo
import ru.wishka.memory.data.Post
import ru.wishka.memory.data.StateStore
import ru.wishka.memory.data.User
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Base64

/**
 * Сервер Memory прямо на телефоне — пока настоящего нет. Отвечает по тому
 * же контракту (docs/openapi.yaml), что и будущий: лента страницами,
 * публикация с Idempotency-Key, «нравится», переписка, загрузки tus.
 *
 * Умеет притворяться плохой сетью: online = false — «нет сети»,
 * latencyMs — задержка, failNext — столько следующих запросов ответят 503,
 * dropAfterBytes — оборвать загрузку на середине куска (проверка докачки).
 *
 * Загруженное хранится в dir (на телефоне — папка в кэше приложения),
 * без dir — в памяти (тесты).
 */
class DemoServer(
    private val me: () -> User,
    private val clock: () -> Long,
    private val dir: File? = null,
    /** Сколько старых записей друзей «на сервере» — для ленты страницами. */
    historySize: Int = 150,
) : Transport {
    @Volatile var online: Boolean = true
    @Volatile var latencyMs: Long = 0
    @Volatile var failNext: Int = 0
    @Volatile var dropAfterBytes: Long = -1

    /** Живой канал: сюда сервер «шлёт» события (прочитано, новые сообщения). */
    @Volatile var realtime: DemoRealtime? = null

    /** Через сколько демо-собеседник «прочитает» (мс). */
    @Volatile var readDelayMs: Long = 2_500

    /** Сколько живёт токен доступа (мс) — в тестах можно «состарить». */
    @Volatile var accessTtlMs: Long = 15 * 60_000L

    /** Сколько запросов пришло — для тестов и экрана «Данные и сеть». */
    @Volatile var requests: Int = 0
        private set

    private val lock = Any()
    private val posts = mutableListOf<Post>()
    private val byKey = HashMap<String, JSONObject>()
    private val liked = HashSet<Long>()
    private val messages = HashMap<Int, MutableList<JSONObject>>()
    private val uploads = HashMap<String, Blob>()
    private var nextId = 6_000_000L
    private var uploadSeq = 0

    // Вход и устройства
    private class Device(val id: String, val name: String, val platform: String, var lastSeen: Long, var refresh: String, var access: String, var accessUntil: Long)
    private val devices = mutableListOf<Device>()
    private var tokenSeq = 0
    // Профиль (синхронизация между телефонами)
    private var profile: JSONObject? = null
    // Участники переписок: кто «прочитает» и кому дойдёт
    private val members = HashMap<Int, MutableList<Int>>()
    private val groups = HashMap<Int, JSONObject>()

    private class Blob(val length: Long, val meta: String, val file: File?) {
        val memory = ByteArrayOutputStream()
        var offset = 0L
        fun append(bytes: ByteArray) {
            if (file != null) FileOutputStream(file, true).use { it.write(bytes) } else memory.write(bytes)
            offset += bytes.size
        }
    }

    init {
        val start = clock() - 2L * 24 * 60 * 60 * 1000
        val people = Demo.people
        repeat(historySize) { i ->
            val author = people[i % people.size]
            posts += Post(
                id = 5_000_000L + i,
                author = author,
                text = HISTORY[i % HISTORY.size],
                minutesAgo = 0,
                artSeed = if (i % 3 == 0) 7000 + i else null,
                likes = (i * 7) % 40,
                comments = (i * 3) % 9,
                createdAt = start - i * 3L * 60 * 60 * 1000 - (i % 5) * 60_000L,
            )
        }
    }

    override suspend fun send(request: HttpRequest): HttpResponse {
        if (latencyMs > 0) delay(latencyMs)
        if (!online) throw IOException("Нет сети (демо)")
        requests++
        if (failNext > 0) {
            failNext--
            return HttpResponse(503, mapOf("Retry-After" to "1"), "Сервер занят".toByteArray())
        }
        val path = request.path.substringAfter("://").let { if (request.path.contains("://")) "/" + it.substringAfter('/') else it }
        return synchronized(lock) { route(request, path) }
    }

    private fun route(r: HttpRequest, path: String): HttpResponse {
        val parts = path.trim('/').split('/')
        // Вход и всё «своё» — только с токеном
        if (path.startsWith("/v1/auth/")) return auth(r, path)
        val needsAuth = path == "/v1/me" || path.startsWith("/v1/devices")
        val device = deviceOf(r)
        if (needsAuth && device == null) return HttpResponse(401, body = "Нужен вход".toByteArray())
        device?.lastSeen = clock()
        return when {
            path == "/v1/me" && r.method == "GET" -> profile?.let { json(it) } ?: notFound()
            path == "/v1/me" && r.method == "PUT" -> {
                val body = bodyJson(r) ?: return HttpResponse(400)
                profile = body.put("updatedAt", clock())
                json(profile!!)
            }
            path == "/v1/devices" && r.method == "GET" -> json(JSONObject().put("items", JSONArray(devices.map { d ->
                JSONObject().put("id", d.id).put("name", d.name).put("platform", d.platform).put("lastSeen", d.lastSeen).put("current", d === device)
            })))
            path == "/v1/devices/revoke-others" && r.method == "POST" -> {
                devices.removeAll { it !== device }
                HttpResponse(204)
            }
            parts.size == 3 && parts[1] == "devices" && r.method == "DELETE" -> {
                if (devices.removeAll { it.id == parts[2] && it !== device }) HttpResponse(204) else notFound()
            }
            // Люди: демо-люди уже на телефоне — сервер новых не знает
            r.method == "GET" && path == "/v1/users" -> json(JSONObject().put("items", JSONArray()))
            r.method == "GET" && parts.size == 3 && parts[1] == "users" -> {
                val u = parts[2].toIntOrNull()?.let { ru.wishka.memory.data.Demo.person(it) } ?: return notFound()
                json(JSONObject().put("id", u.id).put("name", u.name).put("handle", u.handle).put("hue", u.hue.toDouble()).put("bio", u.bio).put("city", u.city)
                    .put("publicKey", Base64.getEncoder().encodeToString(ru.wishka.memory.data.ChatCrypto.demoPeer(u.id).publicKey)))
            }
            r.method == "PUT" && path == "/v1/me/key" -> HttpResponse(204)
            r.method == "GET" && path == "/v1/chats" -> json(JSONObject().put("items", JSONArray()))
            // Демо: личная переписка с человеком — его номер
            r.method == "POST" && path == "/v1/chats/direct" -> {
                val uid = bodyJson(r)?.optInt("user") ?: return HttpResponse(400)
                json(JSONObject().put("id", uid).put("kind", "direct").put("title", "").put("members", JSONArray(listOf(me().id, uid))).put("accepted", true))
            }
            r.method == "POST" && path == "/v1/chats" -> createChat(r)
            parts.size == 3 && parts[1] == "chats" && r.method == "PATCH" -> {
                val id = parts[2].toIntOrNull() ?: return notFound()
                val g = groups[id] ?: return notFound()
                bodyJson(r)?.optString("title")?.takeIf { it.isNotBlank() }?.let { g.put("title", it) }
                groupChanged(id)
                json(g)
            }
            parts.size == 5 && parts[1] == "chats" && (parts[3] == "members" || parts[3] == "admins") -> {
                val id = parts[2].toIntOrNull() ?: return notFound()
                val uid = parts[4].toIntOrNull() ?: return notFound()
                val g = groups[id] ?: return notFound()
                val key = parts[3]
                val list = (0 until g.getJSONArray(key).length()).map { g.getJSONArray(key).getInt(it) }.toMutableList()
                when (r.method) {
                    "PUT" -> if (uid !in list) list += uid
                    "DELETE" -> list.remove(uid)
                    else -> return HttpResponse(405)
                }
                g.put(key, JSONArray(list))
                if (key == "members") members[id] = list.toMutableList()
                groupChanged(id)
                HttpResponse(204)
            }
            parts.size == 4 && parts[1] == "chats" && parts[3] == "read" && r.method == "POST" -> HttpResponse(204)
            parts.size == 4 && parts[1] == "chats" && parts[3] == "accept" && r.method == "POST" -> HttpResponse(204)
            r.method == "GET" && path == "/v1/feed" -> page(posts, r)
            r.method == "GET" && parts.size == 4 && parts[1] == "users" && parts[3] == "posts" ->
                page(posts.filter { it.author.id == parts[2].toIntOrNull() }, r)
            r.method == "POST" && path == "/v1/posts" -> createPost(r)
            parts.size == 4 && parts[1] == "posts" && parts[3] == "like" -> {
                val id = parts[2].toLongOrNull() ?: return notFound()
                when (r.method) {
                    "PUT" -> liked += id
                    "DELETE" -> liked -= id
                    else -> return HttpResponse(405)
                }
                HttpResponse(204)
            }
            parts.size == 4 && parts[1] == "chats" && parts[3] == "messages" -> {
                val chat = parts[2].toIntOrNull() ?: return notFound()
                if (r.method == "POST") sendMessage(chat, r) else chatPage(chat, r)
            }
            r.method == "OPTIONS" && path == "/v1/uploads" ->
                HttpResponse(204, mapOf("Tus-Version" to TusClient.VERSION, "Tus-Max-Size" to MAX_UPLOAD.toString(), "Tus-Extension" to "creation"))
            r.method == "POST" && path == "/v1/uploads" -> createUpload(r)
            parts.size == 3 && parts[1] == "uploads" -> upload(parts[2], r)
            else -> notFound()
        }
    }

    private fun notFound() = HttpResponse(404, body = "Не найдено".toByteArray())

    private fun bodyJson(r: HttpRequest): JSONObject? = runCatching { JSONObject(String(r.body ?: ByteArray(0), Charsets.UTF_8)) }.getOrNull()

    // ── Вход ─────────────────────────────────────────────────────────

    private fun deviceOf(r: HttpRequest): Device? {
        val token = r.header("Authorization")?.removePrefix("Bearer ")?.trim() ?: return null
        return devices.firstOrNull { it.access == token && it.accessUntil > clock() }
    }

    private fun tokens(d: Device): JSONObject {
        d.access = "a.${++tokenSeq}.${d.id}"
        d.refresh = "r.${++tokenSeq}.${d.id}"
        d.accessUntil = clock() + accessTtlMs
        return JSONObject().put("access", d.access).put("refresh", d.refresh).put("expiresIn", accessTtlMs / 1000).put("deviceId", d.id)
    }

    private fun auth(r: HttpRequest, path: String): HttpResponse = when (path) {
        "/v1/auth/login", "/v1/auth/register" -> {
            val body = bodyJson(r) ?: JSONObject()
            if (body.optString("login").isBlank()) HttpResponse(400) else {
                // Демо: пароль проверяет телефон (LocalAuth); здесь — любой
                if (devices.isEmpty()) {
                    // «Другие устройства» этого человека — чтобы было что показать в списке
                    devices += Device("d-ipad", "iPad", "ios", clock() - 3 * 86_400_000L, "r.old1", "", 0)
                    devices += Device("d-web", "Chrome · macOS", "web", clock() - 20 * 86_400_000L, "r.old2", "", 0)
                }
                val dev = body.optJSONObject("device")
                val d = Device("d${devices.size + 1}x${clock() % 10000}", dev?.optString("name").orEmpty().ifEmpty { "Телефон" }, dev?.optString("platform").orEmpty(), clock(), "", "", 0)
                devices += d
                json(tokens(d))
            }
        }
        "/v1/auth/refresh" -> {
            val token = bodyJson(r)?.optString("refresh").orEmpty()
            val d = devices.firstOrNull { it.refresh == token }
            if (d == null) HttpResponse(401, body = "Сессия закрыта".toByteArray()) else { d.lastSeen = clock(); json(tokens(d)) }
        }
        "/v1/auth/logout" -> {
            deviceOf(r)?.let { devices.remove(it) }
            HttpResponse(204)
        }
        else -> notFound()
    }

    /** Токен доступа «истёк» — для проверки обновления. */
    fun expireAccessTokens() = synchronized(lock) { devices.forEach { it.accessUntil = 0 } }

    fun deviceCount(): Int = synchronized(lock) { devices.size }

    // ── Группы ───────────────────────────────────────────────────────

    /** Демо: сервер узнаёт участников переписок, которые были до него. */
    fun registerChat(chatId: Int, memberIds: List<Int>) = synchronized(lock) { members.getOrPut(chatId) { memberIds.toMutableList() } }

    private fun createChat(r: HttpRequest): HttpResponse {
        val key = r.header("Idempotency-Key")
        if (key != null) byKey[key]?.let { return json(it, 200) }
        val body = bodyJson(r) ?: return HttpResponse(400)
        val id = body.optInt("id")
        if (id <= 0) return HttpResponse(400, body = "Нужен id".toByteArray())
        if (groups.containsKey(id)) return HttpResponse(409)
        val memberArr = body.optJSONArray("members") ?: JSONArray()
        val list = (0 until memberArr.length()).map { memberArr.getInt(it) }.distinct().take(ru.wishka.memory.data.ChatGroup.MAX_MEMBERS)
        val g = JSONObject().put("id", id).put("title", body.optString("title").take(ru.wishka.memory.data.ChatGroup.MAX_TITLE))
            .put("members", JSONArray(list)).put("admins", JSONArray(listOf(me().id))).put("owner", me().id)
        groups[id] = g
        members[id] = list.toMutableList()
        if (key != null) byKey[key] = g
        return json(g, 201)
    }

    private fun groupChanged(id: Int) {
        val g = groups[id] ?: return
        fun ints(k: String) = (0 until g.getJSONArray(k).length()).map { g.getJSONArray(k).getInt(it) }
        realtime?.schedule(RtEvent.GroupChanged(id, g.optString("title"), ints("members"), ints("admins")), 0)
    }

    /** Демо: кто-то со стороны пишет — через живой канал, как настоящий сервер. */
    fun deliver(chatId: Int, message: JSONObject, afterMs: Long = 0) {
        realtime?.schedule(RtEvent.NewMessage(chatId, message.put("sentAt", clock() + afterMs)), afterMs)
    }

    private fun json(o: JSONObject, code: Int = 200, headers: Map<String, String> = emptyMap()) =
        HttpResponse(code, headers + ("Content-Type" to Api.JSON), o.toString().toByteArray(Charsets.UTF_8))

    private fun codec() = StateStore.Codec(me())

    // ── Лента ────────────────────────────────────────────────────────

    /** Курсор — «время_номер» последней отданной записи: новые записи не сдвигают страницы. */
    private fun page(source: List<Post>, r: HttpRequest): HttpResponse {
        val limit = (r.query["limit"]?.toIntOrNull() ?: Api.PAGE).coerceIn(1, 100)
        val sorted = source.sortedWith(compareByDescending<Post> { it.createdAt }.thenByDescending { it.id })
        val after = r.query["cursor"]?.let(::decodeCursor)
        val rest = if (after == null) sorted else sorted.filter { it.createdAt < after.first || (it.createdAt == after.first && it.id < after.second) }
        val chunk = rest.take(limit).map { if (it.id in liked && !it.liked) it.copy(liked = true, likes = it.likes + 1) else it }
        val next = if (rest.size > limit) chunk.last().let { encodeCursor(it.createdAt, it.id) } else null
        val c = codec()
        return json(JSONObject().put("items", JSONArray(chunk.map { c.postJson(it) })).put("next", next ?: JSONObject.NULL))
    }

    private fun createPost(r: HttpRequest): HttpResponse {
        val key = r.header("Idempotency-Key")
        if (key != null) byKey[key]?.let { return json(it, 200) }
        val body = runCatching { JSONObject(String(r.body ?: ByteArray(0), Charsets.UTF_8)) }.getOrNull()
            ?: return HttpResponse(400, body = "Нужен JSON".toByteArray())
        val c = codec()
        val post = runCatching { c.postOf(body) }.getOrNull() ?: return HttpResponse(422)
        // Вложения — только уже загруженные целиком
        if (post.media.any { a -> a.path.startsWith("/v1/uploads/") && uploads[a.path.substringAfterLast('/')]?.let { it.offset == it.length } != true }) {
            return HttpResponse(422, body = "Загрузка не закончена".toByteArray())
        }
        val saved = post.copy(id = nextId++, author = me(), createdAt = clock(), outboxKey = "")
        posts += saved
        val out = c.postJson(saved)
        if (key != null) byKey[key] = out
        return json(out, 201)
    }

    // ── Переписка ────────────────────────────────────────────────────

    private fun sendMessage(chat: Int, r: HttpRequest): HttpResponse {
        val key = r.header("Idempotency-Key")
        if (key != null) byKey[key]?.let { return json(it, 200) }
        val body = runCatching { JSONObject(String(r.body ?: ByteArray(0), Charsets.UTF_8)) }.getOrNull()
            ?: return HttpResponse(400)
        val c = codec()
        val m = runCatching { c.messageOf(body) }.getOrNull() ?: return HttpResponse(422)
        val sentAt = clock()
        val saved = c.messageJson(m.copy(id = nextId++, pending = false, delivered = true, authorId = me().id, sentAt = sentAt))
        messages.getOrPut(chat) { mutableListOf() } += saved
        if (key != null) byKey[key] = saved
        // Демо: участники «читают» через пару секунд — событием живого канала
        members[chat].orEmpty().filter { it != me().id }.forEachIndexed { i, uid ->
            realtime?.schedule(RtEvent.Read(chat, uid, sentAt), readDelayMs + i * 700L)
        }
        return json(saved, 201)
    }

    private fun chatPage(chat: Int, r: HttpRequest): HttpResponse {
        val limit = (r.query["limit"]?.toIntOrNull() ?: Api.PAGE).coerceIn(1, 100)
        val all = messages[chat].orEmpty().sortedByDescending { it.optLong("id") }
        val before = r.query["cursor"]?.toLongOrNull()
        val rest = if (before == null) all else all.filter { it.optLong("id") < before }
        val chunk = rest.take(limit)
        val next = if (rest.size > limit) chunk.last().optLong("id").toString() else null
        return json(JSONObject().put("items", JSONArray(chunk)).put("next", next ?: JSONObject.NULL))
    }

    // ── Загрузки (tus 1.0) ───────────────────────────────────────────

    private fun createUpload(r: HttpRequest): HttpResponse {
        if (r.header("Tus-Resumable") != TusClient.VERSION) return HttpResponse(412, mapOf("Tus-Version" to TusClient.VERSION))
        val length = r.header("Upload-Length")?.toLongOrNull() ?: return HttpResponse(400)
        if (length > MAX_UPLOAD) return HttpResponse(413)
        val id = "u${++uploadSeq}x${clock() % 100000}"
        uploads[id] = Blob(length, r.header("Upload-Metadata").orEmpty(), dir?.let { File(it.apply { mkdirs() }, id) })
        return HttpResponse(201, mapOf("Location" to "/v1/uploads/$id", "Tus-Resumable" to TusClient.VERSION))
    }

    private fun upload(id: String, r: HttpRequest): HttpResponse {
        val blob = uploads[id] ?: return notFound()
        val tus = mapOf("Tus-Resumable" to TusClient.VERSION)
        return when (r.method) {
            "HEAD" -> HttpResponse(200, tus + mapOf("Upload-Offset" to blob.offset.toString(), "Upload-Length" to blob.length.toString(), "Cache-Control" to "no-store"))
            "PATCH" -> {
                if (r.contentType?.startsWith("application/offset+octet-stream") != true) return HttpResponse(415)
                val offset = r.header("Upload-Offset")?.toLongOrNull() ?: return HttpResponse(400)
                if (offset != blob.offset) return HttpResponse(409, tus, "Смещение не совпало".toByteArray())
                val bytes = r.body ?: ByteArray(0)
                if (blob.offset + bytes.size > blob.length) return HttpResponse(400)
                val drop = dropAfterBytes
                if (drop in 0 until bytes.size) {
                    // Связь оборвалась посреди куска: часть дошла, ответа не будет
                    blob.append(bytes.copyOfRange(0, drop.toInt()))
                    dropAfterBytes = -1
                    throw IOException("Соединение оборвалось (демо)")
                }
                blob.append(bytes)
                HttpResponse(204, tus + ("Upload-Offset" to blob.offset.toString()))
            }
            "GET" -> {
                if (blob.offset != blob.length) return HttpResponse(409)
                HttpResponse(200, mapOf("Content-Type" to "application/octet-stream"), blob.file?.readBytes() ?: blob.memory.toByteArray())
            }
            "DELETE" -> {
                uploads.remove(id)?.file?.delete()
                HttpResponse(204, tus)
            }
            else -> HttpResponse(405)
        }
    }

    /** Что лежит в загрузке (для тестов). */
    fun uploaded(id: String): ByteArray? = synchronized(lock) {
        val b = uploads[id.substringAfterLast('/')] ?: return null
        b.file?.readBytes() ?: b.memory.toByteArray()
    }

    /** Имя файла из Upload-Metadata (для тестов). */
    fun uploadName(id: String): String? = synchronized(lock) {
        val meta = uploads[id.substringAfterLast('/')]?.meta ?: return null
        meta.split(',').map { it.trim().split(' ') }.firstOrNull { it[0] == "filename" }?.getOrNull(1)
            ?.let { String(Base64.getDecoder().decode(it), Charsets.UTF_8) }
    }

    fun postCount(): Int = synchronized(lock) { posts.size }

    fun messagesIn(chat: Int): Int = synchronized(lock) { messages[chat]?.size ?: 0 }

    fun isLiked(postId: Long): Boolean = synchronized(lock) { postId in liked }

    companion object {
        const val MAX_UPLOAD = 2L * 1024 * 1024 * 1024

        fun encodeCursor(at: Long, id: Long): String = Base64.getUrlEncoder().withoutPadding().encodeToString("$at:$id".toByteArray())

        fun decodeCursor(s: String): Pair<Long, Long>? = runCatching {
            val raw = String(Base64.getUrlDecoder().decode(s))
            raw.substringBefore(':').toLong() to raw.substringAfter(':').toLong()
        }.getOrNull()

        /** Тексты старых записей друзей — то, что видно, когда листаешь ленту далеко вниз. */
        private val HISTORY = listOf(
            "Утро начинается с кофе и хорошей книги",
            "Наконец-то выбрались за город — воздух совсем другой",
            "Кто знает хорошую пекарню в центре?",
            "Пробежала 8 км, ноги гудят, настроение отличное",
            "Пересмотрел «Солярис» — каждый раз как впервые",
            "Новый плейлист на неделю готов",
            "Дождь весь день, зато дома тепло",
            "Закат с крыши — лучшая награда за длинный день",
            "Приготовила толму по бабушкиному рецепту",
            "Записался на курсы фотографии, страшно и интересно",
            "Вечером — настолки с друзьями, кто с нами?",
            "Горы зовут: в субботу на Арагац",
        )
    }
}
