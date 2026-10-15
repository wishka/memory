package ru.wishka.memory.server

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/** Запрос, как его видит ядро (Ktor-обёртка — Main.kt). */
class Req(
    val method: String,
    val path: String,
    val query: Map<String, String> = emptyMap(),
    headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
) {
    private val h = headers.mapKeys { it.key.lowercase() }
    fun header(name: String): String? = h[name.lowercase()]
}

/** Ответ: код, заголовки и либо байты, либо файл (скачивание загрузки). */
class Resp(val code: Int, val headers: Map<String, String> = emptyMap(), val body: ByteArray = ByteArray(0), val file: File? = null, val contentType: String? = null) {
    val text: String get() = String(body, Charsets.UTF_8)
    fun json(): JSONObject = JSONObject(text)
}

/**
 * Сервер Memory — вся логика контракта (docs/openapi.yaml, docs/REALTIME.md):
 * вход и устройства, профиль, люди, лента, записи, «нравится», переписки и
 * группы, загрузки tus, живой канал. Без Ktor: так её проверяют тесты, а
 * Main.kt только переводит HTTP в Req/Resp.
 */
class Core(
    private val store: Store,
    private val dataDir: File,
    val hub: Hub = Hub(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val uploadsDir = File(dataDir, "uploads").apply { mkdirs() }
    private val lock = Any()
    private val failures = ConcurrentHashMap<String, Pair<Int, Long>>()

    companion object {
        const val ACCESS_TTL = 15 * 60_000L
        const val MAX_UPLOAD = 2L * 1024 * 1024 * 1024
        const val MAX_TEXT = 2000
        /** Текст сообщения; секретное — конверт base64, он длиннее самого текста. */
        const val MAX_ENVELOPE = 64 * 1024
        const val MAX_TITLE = 60
        const val MAX_MEMBERS = 200
        const val TUS = "1.0.0"
        const val JSON = "application/json; charset=utf-8"
        private val LOGIN = Regex("^[a-z0-9._]{3,32}$")
    }

    // ── Ответы ───────────────────────────────────────────────────────

    private fun json(o: Any, code: Int = 200, headers: Map<String, String> = emptyMap()) =
        Resp(code, headers, o.toString().toByteArray(Charsets.UTF_8), contentType = JSON)

    private fun error(code: Int, message: String) = json(JSONObject().put("error", message), code)
    private fun empty(code: Int = 204, headers: Map<String, String> = emptyMap()) = Resp(code, headers)

    private fun bodyJson(r: Req): JSONObject? =
        runCatching { JSONObject(String(r.body ?: ByteArray(0), Charsets.UTF_8)) }.getOrNull()

    // ── Вход ─────────────────────────────────────────────────────────

    /** Кто это: по токену доступа; null — не вошёл или токен истёк. */
    fun userByAccess(token: String?): Int? {
        val t = token?.removePrefix("Bearer ")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val d = store.deviceByAccess(Crypto.sha256(t)) ?: return null
        if (d.accessUntil <= clock()) return null
        if (clock() - d.lastSeen > 60_000) store.saveDevice(d.copy(lastSeen = clock()))
        return d.userId
    }

    /** Живой канал: кто и до какого момента действует токен (потом — закрыть кодом 4401). */
    fun realtimeSession(token: String?): Pair<Int, Long>? {
        val t = token?.removePrefix("Bearer ")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val d = store.deviceByAccess(Crypto.sha256(t)) ?: return null
        return if (d.accessUntil > clock()) d.userId to d.accessUntil else null
    }

    fun now(): Long = clock()

    private fun deviceByAccess(r: Req): DeviceRow? {
        val t = r.header("Authorization")?.removePrefix("Bearer ")?.trim() ?: return null
        return store.deviceByAccess(Crypto.sha256(t))?.takeIf { it.accessUntil > clock() }
    }

    private fun issue(user: UserRow, deviceId: String, name: String, platform: String): JSONObject {
        val access = Crypto.randomToken()
        val refresh = Crypto.randomToken()
        store.saveDevice(DeviceRow(deviceId, user.id, name.take(60).ifEmpty { "Телефон" }, platform.take(20), clock(), Crypto.sha256(refresh), Crypto.sha256(access), clock() + ACCESS_TTL))
        return JSONObject().put("access", access).put("refresh", refresh).put("expiresIn", ACCESS_TTL / 1000)
            .put("deviceId", deviceId).put("user", userJson(user))
    }

    /** Не больше 10 неудачных входов за 15 минут на логин. */
    private fun tooManyFailures(login: String): Boolean {
        val (n, since) = failures[login] ?: return false
        if (clock() - since > 15 * 60_000) { failures.remove(login); return false }
        return n >= 10
    }

    private fun failed(login: String) {
        val (n, since) = failures[login] ?: (0 to clock())
        failures[login] = (n + 1) to since
    }

    private fun auth(r: Req): Resp {
        val body = bodyJson(r) ?: JSONObject()
        val dev = body.optJSONObject("device")
        val devName = dev?.optString("name").orEmpty()
        val platform = dev?.optString("platform").orEmpty()
        return when (r.path) {
            "/v1/auth/register" -> {
                if (r.method != "POST") return error(405, "POST")
                val login = body.optString("login").trim().lowercase()
                val password = body.optString("password")
                val name = body.optString("name").trim().take(MAX_TITLE).ifEmpty { login }
                if (!LOGIN.matches(login)) return error(400, "Логин: 3–32 знака, латиница, цифры, точка, подчёркивание")
                if (password.length < 6) return error(400, "Пароль не короче 6 знаков")
                val salt = Crypto.newSalt()
                val user = store.createUser(login, name, (login.hashCode() and 0xffff) / 65535f, Crypto.hashPassword(password, salt), salt, clock())
                    ?: return error(409, "Логин занят")
                json(issue(user, "d" + Crypto.randomToken(9), devName, platform), 201)
            }
            "/v1/auth/login" -> {
                val login = body.optString("login").trim().lowercase()
                if (tooManyFailures(login)) return json(JSONObject().put("error", "Слишком много попыток"), 429, mapOf("Retry-After" to "900"))
                val user = store.userByLogin(login)
                if (user == null || !Crypto.checkPassword(body.optString("password"), user.salt, user.passHash)) {
                    failed(login)
                    return error(401, "Неверный логин или пароль")
                }
                failures.remove(login)
                json(issue(user, "d" + Crypto.randomToken(9), devName, platform))
            }
            "/v1/auth/refresh" -> {
                val d = store.deviceByRefresh(Crypto.sha256(body.optString("refresh"))) ?: return error(401, "Сессия закрыта")
                val user = store.user(d.userId) ?: return error(401, "Нет такого человека")
                // Новый токен обновления, старый больше не действует
                json(issue(user, d.id, d.name, d.platform))
            }
            "/v1/auth/logout" -> {
                deviceByAccess(r)?.let { store.deleteDevice(it.id) }
                empty()
            }
            else -> error(404, "Не найдено")
        }
    }

    // ── Люди ─────────────────────────────────────────────────────────

    fun userJson(u: UserRow): JSONObject {
        val profile = runCatching { JSONObject(u.profile.ifEmpty { "{}" }) }.getOrDefault(JSONObject())
        return JSONObject().put("id", u.id).put("login", u.login).put("handle", u.login).put("name", u.name)
            .put("hue", u.hue.toDouble()).put("bio", profile.optString("bio")).put("city", profile.optString("city"))
            .put("publicKey", u.publicKey)
    }

    private fun authorInfo(id: Int): JSONObject? = store.user(id)?.let { u ->
        JSONObject().put("id", u.id).put("name", u.name).put("handle", u.login).put("hue", u.hue.toDouble())
    }

    // ── Маршруты ─────────────────────────────────────────────────────

    fun handle(r: Req): Resp = try {
        route(r)
    } catch (e: Exception) {
        error(500, "Ошибка сервера")
    }

    private fun route(r: Req): Resp {
        val path = r.path.trimEnd('/').ifEmpty { "/" }
        if (path == "/health") return json(JSONObject().put("ok", true))
        if (path.startsWith("/v1/auth/")) return auth(Req(r.method, path, r.query, mapOf("Authorization" to (r.header("Authorization") ?: "")), r.body))
        if (r.method == "OPTIONS" && path == "/v1/uploads") {
            return empty(204, mapOf("Tus-Version" to TUS, "Tus-Max-Size" to MAX_UPLOAD.toString(), "Tus-Extension" to "creation,termination"))
        }
        val me = userByAccess(r.header("Authorization")) ?: return error(401, "Нужен вход")
        val p = path.trim('/').split('/')
        return when {
            path == "/v1/me" -> me(r, me)
            path == "/v1/me/key" && r.method == "PUT" -> {
                val key = bodyJson(r)?.optString("publicKey").orEmpty()
                if (key.length !in 40..200) return error(400, "Ключ")
                store.user(me)?.let { store.updateUser(it.copy(publicKey = key)) }
                empty()
            }
            path == "/v1/devices" && r.method == "GET" -> {
                val current = deviceByAccess(r)?.id
                json(JSONObject().put("items", JSONArray(store.devices(me).sortedByDescending { it.lastSeen }.map { d ->
                    JSONObject().put("id", d.id).put("name", d.name).put("platform", d.platform).put("lastSeen", d.lastSeen).put("current", d.id == current)
                })))
            }
            path == "/v1/devices/revoke-others" && r.method == "POST" -> {
                val current = deviceByAccess(r)?.id
                store.devices(me).filter { it.id != current }.forEach { store.deleteDevice(it.id) }
                empty()
            }
            p.size == 3 && p[1] == "devices" && r.method == "DELETE" -> {
                val d = store.device(p[2])
                if (d == null || d.userId != me || d.id == deviceByAccess(r)?.id) return error(404, "Нет такого устройства")
                store.deleteDevice(d.id)
                empty()
            }
            path == "/v1/users" && r.method == "GET" -> {
                val q = r.query["q"].orEmpty().trim().lowercase()
                if (q.length < 2) return json(JSONObject().put("items", JSONArray()))
                json(JSONObject().put("items", JSONArray(store.searchUsers(q, 20).map(::userJson))))
            }
            p.size == 3 && p[1] == "users" && r.method == "GET" -> {
                val u = p[2].toIntOrNull()?.let(store::user) ?: return error(404, "Нет такого человека")
                json(userJson(u))
            }
            path == "/v1/feed" && r.method == "GET" -> page(me, null, r)
            p.size == 4 && p[1] == "users" && p[3] == "posts" && r.method == "GET" -> page(me, p[2].toIntOrNull() ?: return error(404, "Нет"), r)
            path == "/v1/posts" && r.method == "POST" -> idempotent(me, r) { createPost(me, r) }
            p.size == 3 && p[1] == "posts" && r.method == "PATCH" -> {
                val post = p[2].toLongOrNull()?.let(store::post) ?: return error(404, "Нет такой записи")
                if (post.author != me) return error(403, "Не ваша запись")
                val body = bodyJson(r) ?: return error(400, "Нужен JSON")
                if (body.has("commentsOff")) store.setCommentsOff(post.id, body.optBoolean("commentsOff"))
                empty()
            }
            p.size == 4 && p[1] == "posts" && p[3] == "like" -> {
                val id = p[2].toLongOrNull() ?: return error(404, "Нет")
                store.post(id) ?: return error(404, "Нет такой записи")
                when (r.method) {
                    "PUT" -> store.setLike(id, me, true)
                    "DELETE" -> store.setLike(id, me, false)
                    else -> return error(405, "PUT или DELETE")
                }
                empty()
            }
            path == "/v1/chats" && r.method == "GET" -> myChats(me)
            path == "/v1/chats" && r.method == "POST" -> idempotent(me, r) { createGroup(me, r) }
            path == "/v1/chats/direct" && r.method == "POST" -> direct(me, r)
            p.size == 3 && p[1] == "chats" && r.method == "PATCH" -> renameGroup(me, p[2].toIntOrNull(), r)
            p.size == 5 && p[1] == "chats" && (p[3] == "members" || p[3] == "admins") ->
                membership(me, p[2].toIntOrNull(), p[3], p[4].toIntOrNull(), r.method)
            p.size == 4 && p[1] == "chats" && p[3] == "messages" -> {
                val chat = p[2].toIntOrNull() ?: return error(404, "Нет")
                if (r.method == "POST") idempotent(me, r) { sendMessage(me, chat, r) } else chatPage(me, chat, r)
            }
            p.size == 4 && p[1] == "chats" && p[3] == "read" && r.method == "POST" -> {
                read(me, p[2].toIntOrNull() ?: return error(404, "Нет"), bodyJson(r)?.optLong("upTo") ?: 0)
                empty()
            }
            p.size == 4 && p[1] == "chats" && p[3] == "accept" && r.method == "POST" -> {
                val chat = p[2].toIntOrNull() ?: return error(404, "Нет")
                val m = store.members(chat).firstOrNull { it.userId == me } ?: return error(404, "Нет такой переписки")
                store.saveMember(m.copy(accepted = true))
                empty()
            }
            path == "/v1/uploads" && r.method == "POST" -> createUpload(me, r)
            p.size == 3 && p[1] == "uploads" -> upload(me, p[2], r)
            else -> error(404, "Не найдено")
        }
    }

    /** Повтор с тем же Idempotency-Key — тот же ответ, без второго действия. */
    private fun idempotent(me: Int, r: Req, block: () -> Resp): Resp {
        val key = r.header("Idempotency-Key")?.take(80) ?: return block()
        synchronized(lock) {
            store.idem(me, key)?.let { return Resp(if (it.status == 201) 200 else it.status, body = it.body.toByteArray(), contentType = JSON) }
            val resp = block()
            if (resp.code in 200..299) store.saveIdem(me, key, IdemRow(resp.code, resp.text), clock())
            return resp
        }
    }

    // ── Профиль ──────────────────────────────────────────────────────

    private fun me(r: Req, me: Int): Resp {
        val u = store.user(me) ?: return error(404, "Нет")
        return when (r.method) {
            "GET" -> if (u.profile.isEmpty()) error(404, "Профиль ещё не сохраняли")
            else json(JSONObject(u.profile).put("updatedAt", u.profileUpdated).put("id", u.id).put("login", u.login))
            "PUT" -> {
                val body = bodyJson(r) ?: return error(400, "Нужен JSON")
                if ((r.body?.size ?: 0) > 64 * 1024) return error(413, "Слишком большой профиль")
                val now = clock()
                val name = body.optString("name").trim().take(MAX_TITLE).ifEmpty { u.name }
                body.remove("id"); body.remove("login"); body.remove("updatedAt")
                store.updateUser(u.copy(name = name, profile = body.toString(), profileUpdated = now))
                json(body.put("updatedAt", now).put("id", u.id).put("login", u.login))
            }
            else -> error(405, "GET или PUT")
        }
    }

    // ── Записи ───────────────────────────────────────────────────────

    private fun encodeCursor(at: Long, id: Long): String = Base64.getUrlEncoder().withoutPadding().encodeToString("$at:$id".toByteArray())

    private fun decodeCursor(s: String): Pair<Long, Long>? = runCatching {
        val raw = String(Base64.getUrlDecoder().decode(s))
        raw.substringBefore(':').toLong() to raw.substringAfter(':').toLong()
    }.getOrNull()

    /** Запись для того, кто смотрит: номер, автор, счётчики, «нравится ли мне». */
    private fun postJson(row: PostRow, liked: Boolean): JSONObject {
        val o = runCatching { JSONObject(row.body) }.getOrDefault(JSONObject())
        o.put("id", row.id).put("author", row.author).put("createdAt", row.created).put("minutesAgo", 0)
            .put("likes", row.likes).put("liked", liked).put("commentsOff", row.commentsOff)
            .put("comments", 0).put("commentList", JSONArray()).put("outboxKey", "")
        authorInfo(row.author)?.let { o.put("authorInfo", it) }
        return o
    }

    private fun page(me: Int, author: Int?, r: Req): Resp {
        val limit = (r.query["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
        val after = r.query["cursor"]?.let(::decodeCursor)
        val rows = store.posts(author, after?.first, after?.second, limit + 1)
        val chunk = rows.take(limit)
        val liked = store.likedBy(me, chunk.map { it.id })
        val next = if (rows.size > limit) chunk.last().let { encodeCursor(it.created, it.id) } else null
        return json(JSONObject().put("items", JSONArray(chunk.map { postJson(it, it.id in liked) })).put("next", next ?: JSONObject.NULL))
    }

    /**
     * Вложения: загруженные (…/v1/uploads/<id>) — только свои и целиком;
     * стикеры и гифки клиент шлёт как есть — их не трогаем.
     */
    private fun cleanMedia(me: Int, arr: JSONArray?, max: Int): JSONArray? {
        val out = JSONArray()
        if (arr == null) return out
        for (i in 0 until minOf(arr.length(), max)) {
            val a = arr.optJSONObject(i) ?: continue
            val path = a.optString("path")
            if (path.contains("/v1/uploads/")) {
                val up = store.upload(path.substringAfter("/v1/uploads/").substringBefore('?')) ?: return null
                if (up.owner != me || up.offset != up.length) return null
                a.put("path", "/v1/uploads/" + up.id)
            }
            out.put(a)
        }
        return out
    }

    private fun createPost(me: Int, r: Req): Resp {
        val body = bodyJson(r) ?: return error(400, "Нужен JSON")
        if (body.optString("text").length > MAX_TEXT) return error(400, "Текст длиннее $MAX_TEXT знаков")
        val media = cleanMedia(me, body.optJSONArray("media"), 10) ?: return error(422, "Загрузка не закончена")
        listOf("id", "author", "authorInfo", "likes", "liked", "comments", "commentList", "createdAt", "outboxKey", "commentsOff", "minutesAgo").forEach(body::remove)
        body.put("media", media)
        if (!body.has("text")) body.put("text", "")
        val row = store.createPost(me, clock(), body.toString())
        return json(postJson(row, false), 201)
    }

    // ── Переписки ────────────────────────────────────────────────────

    private fun chatJson(c: ChatRow, me: Int): JSONObject {
        val ms = store.members(c.id)
        val o = JSONObject().put("id", c.id).put("kind", c.kind).put("title", c.title).put("owner", c.owner)
            .put("members", JSONArray(ms.map { it.userId })).put("admins", JSONArray(ms.filter { it.admin }.map { it.userId }))
            .put("accepted", ms.firstOrNull { it.userId == me }?.accepted ?: false)
        if (c.direct) ms.firstOrNull { it.userId != me }?.let { m -> store.user(m.userId)?.let { o.put("user", userJson(it)) } }
        return o
    }

    /** Переписки человека — немного, без страниц; новые сверху. */
    private fun myChats(me: Int): Resp =
        json(JSONObject().put("items", JSONArray(store.chatsOf(me).sortedByDescending { it.created }.map { chatJson(it, me) })))

    private fun createGroup(me: Int, r: Req): Resp {
        val body = bodyJson(r) ?: return error(400, "Нужен JSON")
        val id = body.optInt("id")
        if (id !in 1..1_499_999_999) return error(400, "Номер группы")
        val title = body.optString("title").trim().take(MAX_TITLE)
        if (title.isEmpty()) return error(400, "Нужно название")
        val arr = body.optJSONArray("members") ?: JSONArray()
        val ids = (0 until arr.length()).map { arr.optInt(it) }.filter { it != me && store.user(it) != null }.distinct().take(MAX_MEMBERS - 1)
        val members = listOf(MemberRow(id, me, admin = true, accepted = true)) + ids.map { MemberRow(id, it, admin = false, accepted = true) }
        if (!store.createChat(ChatRow(id, "group", title, me, clock()), members)) return error(409, "Номер занят")
        groupChanged(id)
        return json(chatJson(store.chat(id)!!, me), 201)
    }

    /** Личная переписка с человеком: одна на пару, номер выбирает сервер. */
    private fun direct(me: Int, r: Req): Resp {
        val other = bodyJson(r)?.optInt("user") ?: return error(400, "Нужен user")
        if (other == me) return error(400, "Это вы")
        store.user(other) ?: return error(404, "Нет такого человека")
        synchronized(lock) {
            store.directChat(me, other)?.let { return json(chatJson(it, me)) }
            var id: Int
            do { id = 1_500_000_000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(600_000_000) } while (store.chat(id) != null)
            // Тот, кому пишут, видит переписку как запрос, пока не ответит или не примет
            store.createChat(ChatRow(id, "direct", "", me, clock()), listOf(MemberRow(id, me, false, true), MemberRow(id, other, false, false)))
            return json(chatJson(store.chat(id)!!, me), 201)
        }
    }

    private fun renameGroup(me: Int, chatId: Int?, r: Req): Resp {
        val chat = chatId?.let(store::chat) ?: return error(404, "Нет такой группы")
        if (chat.direct) return error(400, "Это не группа")
        if (store.members(chat.id).none { it.userId == me && (it.admin || chat.owner == me) }) return error(403, "Только админы")
        val title = bodyJson(r)?.optString("title")?.trim()?.take(MAX_TITLE).orEmpty()
        if (title.isEmpty()) return error(400, "Нужно название")
        store.updateChatTitle(chat.id, title)
        groupChanged(chat.id)
        return json(chatJson(store.chat(chat.id)!!, me))
    }

    private fun membership(me: Int, chatId: Int?, what: String, userId: Int?, method: String): Resp {
        val chat = chatId?.let(store::chat) ?: return error(404, "Нет такой группы")
        if (chat.direct || userId == null) return error(400, "Это не группа")
        val ms = store.members(chat.id)
        val mine = ms.firstOrNull { it.userId == me } ?: return error(403, "Вы не в группе")
        val isAdmin = mine.admin || chat.owner == me
        if (what == "members") {
            when (method) {
                "PUT" -> {
                    if (!isAdmin) return error(403, "Только админы")
                    if (store.user(userId) == null) return error(404, "Нет такого человека")
                    if (ms.size >= MAX_MEMBERS) return error(409, "Группа заполнена")
                    if (ms.none { it.userId == userId }) store.saveMember(MemberRow(chat.id, userId, false, true))
                }
                "DELETE" -> {
                    if (userId != me && !isAdmin) return error(403, "Только админы")
                    if (userId == chat.owner && userId != me) return error(403, "Создателя не убрать")
                    store.removeMember(chat.id, userId)
                    // Убранному — тоже: пусть его телефон уберёт переписку
                    hub.send(userId, groupEvent(chat.id).toString())
                }
                else -> return error(405, "PUT или DELETE")
            }
        } else {
            if (chat.owner != me) return error(403, "Только создатель")
            val m = ms.firstOrNull { it.userId == userId } ?: return error(404, "Не в группе")
            when (method) {
                "PUT" -> store.saveMember(m.copy(admin = true))
                "DELETE" -> store.saveMember(m.copy(admin = false))
                else -> return error(405, "PUT или DELETE")
            }
        }
        groupChanged(chat.id)
        return empty()
    }

    private fun groupEvent(chatId: Int): JSONObject {
        val c = store.chat(chatId)
        val ms = store.members(chatId)
        return JSONObject().put("type", "group").put("chat", chatId).put("title", c?.title.orEmpty())
            .put("members", JSONArray(ms.map { it.userId })).put("admins", JSONArray(ms.filter { it.admin }.map { it.userId }))
    }

    private fun groupChanged(chatId: Int) {
        val ev = groupEvent(chatId).toString()
        hub.sendAll(store.members(chatId).map { it.userId }, ev)
    }

    /** Сообщение для того, кто смотрит: «моё ли», кто автор. */
    private fun messageJson(row: MessageRow, viewer: Int): JSONObject {
        val o = runCatching { JSONObject(row.body) }.getOrDefault(JSONObject())
        o.put("id", row.id).put("fromMe", row.author == viewer).put("authorId", row.author).put("sentAt", row.sentAt)
            .put("delivered", true).put("pending", false).put("readBy", JSONArray())
        if (!o.has("time")) o.put("time", "")
        authorInfo(row.author)?.let { o.put("authorInfo", it) }
        return o
    }

    private fun sendMessage(me: Int, chatId: Int, r: Req): Resp {
        val chat = store.chat(chatId) ?: return error(404, "Нет такой переписки")
        val ms = store.members(chatId)
        val mine = ms.firstOrNull { it.userId == me } ?: return error(403, "Вы не в переписке")
        val body = bodyJson(r) ?: return error(400, "Нужен JSON")
        if (body.optString("text").length > MAX_ENVELOPE) return error(400, "Слишком длинное сообщение")
        val media = cleanMedia(me, body.optJSONArray("attachments"), 10) ?: return error(422, "Загрузка не закончена")
        listOf("id", "fromMe", "authorId", "authorInfo", "sentAt", "delivered", "pending", "readBy").forEach(body::remove)
        body.put("attachments", media)
        if (!body.has("text")) body.put("text", "")
        // Ответил — значит, принял переписку
        if (!mine.accepted) store.saveMember(mine.copy(accepted = true))
        val row = store.addMessage(chat.id, me, clock(), body.toString())
        val event = JSONObject().put("type", "message").put("chat", chat.id)
        ms.filter { it.userId != me }.forEach { m -> hub.send(m.userId, JSONObject(event.toString()).put("message", messageJson(row, m.userId)).toString()) }
        return json(messageJson(row, me), 201)
    }

    private fun chatPage(me: Int, chatId: Int, r: Req): Resp {
        if (store.members(chatId).none { it.userId == me }) return error(403, "Вы не в переписке")
        val limit = (r.query["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
        val rows = store.messages(chatId, r.query["cursor"]?.toLongOrNull(), limit + 1)
        val chunk = rows.take(limit)
        val next = if (rows.size > limit) chunk.last().id.toString() else null
        return json(JSONObject().put("items", JSONArray(chunk.map { messageJson(it, me) })).put("next", next ?: JSONObject.NULL))
    }

    /**
     * «Прочитано» — остальным участникам. Кто ещё не принял запрос на
     * переписку, «прочитано» не отправляет: автор не узнает, что прочли.
     */
    fun read(me: Int, chatId: Int, upTo: Long) {
        val ms = store.members(chatId)
        val mine = ms.firstOrNull { it.userId == me } ?: return
        if (!mine.accepted || upTo <= 0) return
        val ev = JSONObject().put("type", "read").put("chat", chatId).put("user", me).put("upTo", upTo).toString()
        hub.sendAll(ms.filter { it.userId != me }.map { it.userId }, ev)
    }

    /** Кадр от телефона по живому каналу: «печатает» и «прочитано». */
    fun onClientFrame(me: Int, text: String) {
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return
        val chatId = o.optInt("chat")
        when (o.optString("type")) {
            "read" -> read(me, chatId, o.optLong("upTo"))
            "typing" -> {
                val ms = store.members(chatId)
                val mine = ms.firstOrNull { it.userId == me } ?: return
                if (!mine.accepted || ms.size > 50) return
                val ev = JSONObject().put("type", "typing").put("chat", chatId).put("user", me).put("on", o.optBoolean("on")).toString()
                hub.sendAll(ms.filter { it.userId != me }.map { it.userId }, ev)
            }
        }
    }

    // ── Загрузки (tus 1.0: creation, termination) ───────────────────

    private fun fileOf(id: String) = File(uploadsDir, id.filter { it.isLetterOrDigit() || it == '-' || it == '_' })

    private fun createUpload(me: Int, r: Req): Resp {
        if (r.header("Tus-Resumable") != TUS) return empty(412, mapOf("Tus-Version" to TUS))
        val length = r.header("Upload-Length")?.toLongOrNull() ?: return error(400, "Нужен Upload-Length")
        if (length < 0 || length > MAX_UPLOAD) return error(413, "Файл больше 2 ГБ")
        val id = Crypto.randomToken(12)
        store.createUpload(UploadRow(id, me, length, 0, r.header("Upload-Metadata").orEmpty().take(1000), clock()))
        fileOf(id).writeBytes(ByteArray(0))
        return empty(201, mapOf("Location" to "/v1/uploads/$id", "Tus-Resumable" to TUS))
    }

    private fun metaType(meta: String): String? = meta.split(',').map { it.trim().split(' ') }
        .firstOrNull { it.firstOrNull() == "filetype" }?.getOrNull(1)
        ?.let { runCatching { String(Base64.getDecoder().decode(it)) }.getOrNull() }
        ?.takeIf { Regex("^[a-z]+/[a-z0-9.+-]+$").matches(it) }

    private fun upload(me: Int, id: String, r: Req): Resp {
        val up = store.upload(id) ?: return error(404, "Нет такой загрузки")
        val tus = mapOf("Tus-Resumable" to TUS)
        if (r.method == "GET") {
            // Скачать может любой вошедший: так фото записи видят все, кому она видна;
            // файлы секретных переписок здесь — шифротекст
            if (up.offset != up.length) return error(409, "Загрузка не закончена")
            return Resp(200, mapOf("Cache-Control" to "private, max-age=31536000, immutable"), file = fileOf(id), contentType = metaType(up.meta) ?: "application/octet-stream")
        }
        if (up.owner != me) return error(403, "Не ваша загрузка")
        return when (r.method) {
            "HEAD" -> empty(200, tus + mapOf("Upload-Offset" to up.offset.toString(), "Upload-Length" to up.length.toString(), "Cache-Control" to "no-store"))
            "PATCH" -> {
                if (r.header("Content-Type")?.startsWith("application/offset+octet-stream") != true) return error(415, "Нужен application/offset+octet-stream")
                val offset = r.header("Upload-Offset")?.toLongOrNull() ?: return error(400, "Нужен Upload-Offset")
                synchronized(lock) {
                    val cur = store.upload(id) ?: return error(404, "Нет")
                    if (offset != cur.offset) return empty(409, tus)
                    val bytes = r.body ?: ByteArray(0)
                    if (cur.offset + bytes.size > cur.length) return error(400, "Больше заявленного")
                    RandomAccessFile(fileOf(id), "rw").use { f -> f.seek(cur.offset); f.write(bytes) }
                    val next = cur.offset + bytes.size
                    store.setUploadOffset(id, next)
                    empty(204, tus + ("Upload-Offset" to next.toString()))
                }
            }
            "DELETE" -> {
                fileOf(id).delete()
                store.deleteUpload(id)
                empty(204, tus)
            }
            else -> error(405, "HEAD, PATCH, GET или DELETE")
        }
    }
}
