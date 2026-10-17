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
    /** Пуши (FCM); NoPush — выключены. */
    private val pusher: Pusher = NoPush,
    /** Где отправлять пуши — не в потоке запроса (тесты: сразу). */
    private val background: (Runnable) -> Unit = { pushPool.execute(it) },
    /** Логины модераторов (ADMINS): им — жалобы и админка /admin. */
    private val admins: Set<String> = emptySet(),
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
        const val STORY_TTL = 24 * 60 * 60_000L
        const val MAX_COMMENT = 1000
        private val pushPool = java.util.concurrent.Executors.newFixedThreadPool(2) { r -> Thread(r, "push").apply { isDaemon = true } }
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

    private fun isAdmin(userId: Int): Boolean = admins.isNotEmpty() && store.user(userId)?.login in admins

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
                // Логины модераторов (ADMINS) занять нельзя: иначе тот, кто успел, стал бы модератором
                if (login in admins) return error(409, "Логин занят")
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
                if (user.banned) return error(403, "Аккаунт заблокирован модератором")
                json(issue(user, "d" + Crypto.randomToken(9), devName, platform))
            }
            "/v1/auth/refresh" -> {
                val d = store.deviceByRefresh(Crypto.sha256(body.optString("refresh"))) ?: return error(401, "Сессия закрыта")
                val user = store.user(d.userId)?.takeIf { !it.banned } ?: return error(401, "Нет такого человека")
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

    /** Человек для того, кто смотрит (viewer): с подпиской и счётчиками. */
    fun userJson(u: UserRow, viewer: Int = 0): JSONObject {
        val profile = runCatching { JSONObject(u.profile.ifEmpty { "{}" }) }.getOrDefault(JSONObject())
        val o = JSONObject().put("id", u.id).put("login", u.login).put("handle", u.login).put("name", u.name)
            .put("hue", u.hue.toDouble()).put("bio", profile.optString("bio")).put("city", profile.optString("city"))
            .put("publicKey", u.publicKey).put("closed", u.closed)
            .put("followers", store.followers(u.id).size).put("following", store.following(u.id).size)
        if (viewer > 0) {
            val f = store.follow(viewer, u.id)
            val state = when {
                viewer == u.id -> "self"
                f == null -> "none"
                f.accepted -> "following"
                else -> "requested"
            }
            o.put("followState", state).put("followsMe", store.follow(u.id, viewer)?.accepted == true)
        }
        return o
    }

    /** Комментарий виден: свой, или автор не заблокирован (в любую сторону) и не забанен. */
    private fun visibleComment(viewer: Int, c: CommentRow): Boolean =
        c.author == viewer || (!store.blockedEither(viewer, c.author) && store.user(c.author)?.banned == false)

    /** Видит ли viewer записи и истории автора: свой, открытый профиль или подписан. */
    private fun canSee(viewer: Int, author: Int): Boolean {
        if (viewer == author) return true
        val u = store.user(author) ?: return false
        if (u.banned || store.blockedEither(viewer, author)) return false
        return !u.closed || store.follow(viewer, author)?.accepted == true
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
        // Модерация в браузере: страница сама входит через API (только логины из ADMINS)
        if (path == "/admin" && r.method == "GET") {
            return Resp(200, mapOf("Cache-Control" to "no-store", "X-Frame-Options" to "DENY"), AdminPage.HTML.toByteArray(), contentType = "text/html; charset=utf-8")
        }
        if (path.startsWith("/v1/auth/")) return auth(Req(r.method, path, r.query, mapOf("Authorization" to (r.header("Authorization") ?: "")), r.body))
        if (r.method == "OPTIONS" && path == "/v1/uploads") {
            return empty(204, mapOf("Tus-Version" to TUS, "Tus-Max-Size" to MAX_UPLOAD.toString(), "Tus-Extension" to "creation,termination"))
        }
        val me = userByAccess(r.header("Authorization")) ?: return error(401, "Нужен вход")
        val p = path.trim('/').split('/')
        return when {
            path == "/v1/me" && r.method == "DELETE" -> deleteMe(me, r)
            path == "/v1/me/password" && r.method == "PUT" -> {
                val u = store.user(me) ?: return error(404, "Нет")
                val body = bodyJson(r) ?: return error(400, "Нужен JSON")
                if (tooManyFailures("pw:$me")) return json(JSONObject().put("error", "Слишком много попыток"), 429, mapOf("Retry-After" to "900"))
                if (!Crypto.checkPassword(body.optString("current"), u.salt, u.passHash)) {
                    failed("pw:$me")
                    return error(403, "Пароль не подходит")
                }
                val next = body.optString("new")
                if (next.length < 6) return error(400, "Пароль не короче 6 знаков")
                val salt = Crypto.newSalt()
                store.updatePassword(me, Crypto.hashPassword(next, salt), salt)
                // Другие устройства выходят: пароль сменили — старые входы больше не действуют
                val current = deviceByAccess(r)?.id
                store.devices(me).filter { it.id != current }.forEach { store.deleteDevice(it.id) }
                hub.kick(me)
                empty()
            }
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
                hub.kick(me)
                empty()
            }
            p.size == 3 && p[1] == "devices" && r.method == "DELETE" -> {
                val d = store.device(p[2])
                if (d == null || d.userId != me || d.id == deviceByAccess(r)?.id) return error(404, "Нет такого устройства")
                store.deleteDevice(d.id)
                hub.kick(me)
                empty()
            }
            path == "/v1/users" && r.method == "GET" -> {
                val q = r.query["q"].orEmpty().trim().lowercase()
                if (q.length < 2) return json(JSONObject().put("items", JSONArray()))
                val found = store.searchUsers(q, 40).filter { !it.banned && !store.blockedEither(me, it.id) }.take(20)
                json(JSONObject().put("items", JSONArray(found.map { userJson(it, me) })))
            }
            p.size == 3 && p[1] == "users" && r.method == "GET" -> {
                val u = p[2].toIntOrNull()?.let(store::user)?.takeIf { !it.banned && !store.blockedEither(me, it.id) } ?: return error(404, "Нет такого человека")
                json(userJson(u, me))
            }
            p.size == 4 && p[1] == "users" && p[3] == "follow" -> follow(me, p[2].toIntOrNull(), r.method)
            path == "/v1/me/following" && r.method == "GET" -> people(store.following(me), me)
            path == "/v1/me/followers" && r.method == "GET" -> people(store.followers(me), me)
            path == "/v1/me/requests" && r.method == "GET" -> people(store.followers(me, accepted = false), me)
            path == "/v1/me/sent" && r.method == "GET" -> people(store.following(me, accepted = false), me)
            p.size == 4 && p[1] == "me" && p[2] == "requests" && r.method == "DELETE" -> {
                val from = p[3].toIntOrNull() ?: return error(404, "Нет")
                if (store.follow(from, me)?.accepted == false) store.removeFollow(from, me)
                empty()
            }
            p.size == 5 && p[1] == "me" && p[2] == "requests" && p[4] == "accept" && r.method == "POST" -> {
                val from = p[3].toIntOrNull() ?: return error(404, "Нет")
                val f = store.follow(from, me) ?: return error(404, "Нет такой заявки")
                if (!f.accepted) {
                    store.saveFollow(f.copy(accepted = true))
                    event(from, JSONObject().put("type", "follow").put("user", me).put("state", "accepted"))
                    notify(from, me, mapOf("type" to "request_accepted"))
                }
                empty()
            }
            p.size == 4 && p[1] == "me" && p[2] == "followers" && r.method == "DELETE" -> {
                p[3].toIntOrNull()?.let { store.removeFollow(it, me) }
                empty()
            }
            path == "/v1/me/close-friends" && r.method == "PUT" -> {
                val arr = bodyJson(r)?.optJSONArray("ids") ?: return error(400, "Нужен ids")
                store.setCloseFriends(me, (0 until arr.length()).map { arr.optInt(it) }.filter { it > 0 && it != me }.distinct().take(500))
                empty()
            }
            p.size == 4 && p[1] == "users" && p[3] == "block" -> block(me, p[2].toIntOrNull(), r.method)
            path == "/v1/me/blocked" && r.method == "GET" -> json(JSONObject().put("ids", JSONArray(store.blockedBy(me).toList())))
            path == "/v1/reports" && r.method == "POST" -> idempotent(me, r) { createReport(me, r) }
            path == "/v1/admin/reports" && r.method == "GET" -> {
                if (!isAdmin(me)) return error(403, "Только модераторам")
                adminReports(r.query["status"]?.takeIf { it != "all" } ?: if (r.query["status"] == "all") null else "open")
            }
            p.size == 4 && p[1] == "admin" && p[2] == "reports" && r.method == "POST" -> {
                if (!isAdmin(me)) return error(403, "Только модераторам")
                resolveReport(p[3].toLongOrNull() ?: return error(404, "Нет"), bodyJson(r)?.optString("action").orEmpty())
            }
            path == "/v1/me/close-friends" && r.method == "GET" -> json(JSONObject().put("ids", JSONArray(store.closeFriends(me).toList())))
            path == "/v1/devices/current/push" && r.method == "PUT" -> {
                val d = deviceByAccess(r) ?: return error(401, "Нужен вход")
                val token = bodyJson(r)?.optString("token").orEmpty().take(4096)
                // Один телефон — у одного человека: токен с другого аккаунта отсюда же снимается
                if (token.isNotEmpty()) store.clearPushToken(token)
                store.saveDevice(d.copy(pushToken = token))
                empty()
            }
            path == "/v1/feed" && r.method == "GET" -> page(me, PageKind.Feed, r)
            path == "/v1/explore" && r.method == "GET" -> page(me, PageKind.Explore, r)
            p.size == 4 && p[1] == "users" && p[3] == "posts" && r.method == "GET" -> {
                val author = p[2].toIntOrNull() ?: return error(404, "Нет")
                if (!canSee(me, author)) return error(403, "Закрытый профиль")
                page(me, PageKind.Author(author), r)
            }
            p.size == 4 && p[1] == "posts" && p[3] == "comments" -> {
                val post = p[2].toLongOrNull()?.let(store::post) ?: return error(404, "Нет такой записи")
                if (!canSeePost(me, post)) return error(403, "Закрытый профиль")
                if (r.method == "POST") idempotent(me, r) { addComment(me, post, r) } else commentPage(me, post, r)
            }
            p.size == 3 && p[1] == "comments" && r.method == "DELETE" -> {
                val c = p[2].toLongOrNull()?.let(store::comment) ?: return error(404, "Нет такого комментария")
                val post = store.post(c.postId)
                // Удалить может автор комментария, автор записи и владелец сообщества
                val owner = post?.community?.takeIf { it != 0 }?.let(store::community)?.owner
                if (c.author != me && post?.author != me && owner != me) return error(403, "Не ваш комментарий")
                store.deleteComment(c.id)
                empty()
            }
            p.size == 4 && p[1] == "comments" && p[3] == "like" -> {
                val c = p[2].toLongOrNull()?.let(store::comment) ?: return error(404, "Нет такого комментария")
                val post = store.post(c.postId) ?: return error(404, "Нет такой записи")
                if (!canSeePost(me, post)) return error(403, "Закрытый профиль")
                when (r.method) {
                    "PUT" -> store.setCommentLike(c.id, me, true)
                    "DELETE" -> store.setCommentLike(c.id, me, false)
                    else -> return error(405, "PUT или DELETE")
                }
                empty()
            }
            path == "/v1/stories" && r.method == "GET" -> stories(me)
            path == "/v1/stories" && r.method == "POST" -> idempotent(me, r) { createStory(me, r) }
            p.size == 3 && p[1] == "stories" && r.method == "DELETE" -> {
                val st = p[2].toLongOrNull()?.let(store::story) ?: return error(404, "Нет такой истории")
                if (st.author != me) return error(403, "Не ваша история")
                store.deleteStory(st.id)
                empty()
            }
            p.size == 4 && p[1] == "stories" && p[3] == "view" && r.method == "POST" -> {
                val st = p[2].toLongOrNull()?.let(store::story) ?: return error(404, "Нет такой истории")
                if (!storyVisible(me, st)) return error(403, "Не видно")
                if (st.author != me) {
                    val liked = bodyJson(r)?.optBoolean("liked") ?: false
                    store.saveStoryView(StoryViewRow(st.id, me, clock(), liked))
                }
                empty()
            }
            path == "/v1/posts" && r.method == "POST" -> idempotent(me, r) { createPost(me, r) }
            p.size == 3 && p[1] == "posts" && r.method == "PATCH" -> {
                val post = p[2].toLongOrNull()?.let(store::post) ?: return error(404, "Нет такой записи")
                if (post.author != me) return error(403, "Не ваша запись")
                val body = bodyJson(r) ?: return error(400, "Нужен JSON")
                if (body.has("commentsOff")) store.setCommentsOff(post.id, body.optBoolean("commentsOff"))
                if (body.has("text")) {
                    val text = body.optString("text")
                    if (text.length > MAX_TEXT) return error(400, "Текст длиннее $MAX_TEXT знаков")
                    val o = runCatching { JSONObject(post.body) }.getOrDefault(JSONObject())
                    // У PRISM текст — подпись (caption)
                    o.put(if (post.kind == "prism") "caption" else "text", text).put("editedAt", clock())
                    // keep — номера вложений, что остались (убрать можно, добавить — нет)
                    val keep = body.optJSONArray("keep")
                    val media = o.optJSONArray("media")
                    if (keep != null && media != null) {
                        val ids = (0 until keep.length()).map { keep.optString(it) }.toSet()
                        val kept = JSONArray()
                        for (i in 0 until media.length()) {
                            val a = media.optJSONObject(i) ?: continue
                            if (a.optString("id") in ids) kept.put(a)
                            else purgeMedia(JSONObject().put("media", JSONArray().put(a)).toString())
                        }
                        o.put("media", kept)
                    }
                    store.updatePostBody(post.id, o.toString())
                }
                empty()
            }
            p.size == 3 && p[1] == "posts" && r.method == "DELETE" -> {
                val post = p[2].toLongOrNull()?.let(store::post) ?: return error(404, "Нет такой записи")
                // Свою — автор; в сообществе — ещё и его владелец
                val owner = post.community.takeIf { it != 0 }?.let(store::community)?.owner
                if (post.author != me && owner != me) return error(403, "Не ваша запись")
                purgeMedia(post.body)
                store.deletePost(post.id)
                empty()
            }
            path == "/v1/prisms" && r.method == "GET" -> prismPage(me, r)
            path == "/v1/prisms" && r.method == "POST" -> idempotent(me, r) { createPrism(me, r) }
            path == "/v1/communities" && r.method == "GET" -> {
                val q = r.query["q"]?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
                val topic = r.query["topic"]?.takeIf { it.isNotEmpty() }
                json(JSONObject().put("items", JSONArray(store.communities(q, topic, 60).filter { communityVisible(me, it) }.take(40).map { communityJson(it, me) })))
            }
            path == "/v1/communities" && r.method == "POST" -> idempotent(me, r) { createCommunity(me, r) }
            path == "/v1/me/communities" && r.method == "GET" ->
                json(JSONObject().put("items", JSONArray(store.communitiesOf(me).filter { communityVisible(me, it) }.map { communityJson(it, me) })))
            p.size == 3 && p[1] == "communities" -> {
                val c = p[2].toIntOrNull()?.let(store::community)?.takeIf { communityVisible(me, it) } ?: return error(404, "Нет такой группы")
                when (r.method) {
                    "GET" -> json(communityJson(c, me))
                    "PATCH" -> updateCommunity(me, c, r)
                    "DELETE" -> {
                        if (c.owner != me) return error(403, "Не ваша группа")
                        purgeCommunity(c)
                        empty()
                    }
                    else -> error(405, "GET, PATCH или DELETE")
                }
            }
            p.size == 4 && p[1] == "communities" && p[3] == "join" -> {
                val c = p[2].toIntOrNull()?.let(store::community)?.takeIf { communityVisible(me, it) } ?: return error(404, "Нет такой группы")
                when (r.method) {
                    "PUT" -> store.setMember(c.id, me, true, clock())
                    "DELETE" -> {
                        if (c.owner == me) return error(400, "Создатель не может выйти — только удалить группу")
                        store.setMember(c.id, me, false, clock())
                    }
                    else -> return error(405, "PUT или DELETE")
                }
                json(communityJson(c, me))
            }
            p.size == 4 && p[1] == "communities" && p[3] == "posts" -> {
                val c = p[2].toIntOrNull()?.let(store::community)?.takeIf { communityVisible(me, it) } ?: return error(404, "Нет такой группы")
                if (r.method == "POST") idempotent(me, r) { createPost(me, r, c) } else communityPage(me, c, r)
            }
            path == "/v1/me/highlights" && r.method == "PUT" -> saveHighlights(me, r)
            p.size == 4 && p[1] == "users" && p[3] == "highlights" && r.method == "GET" -> {
                val owner = p[2].toIntOrNull() ?: return error(404, "Нет")
                if (!canSee(me, owner)) return error(403, "Закрытый профиль")
                highlightsOf(me, owner)
            }
            p.size == 4 && p[1] == "posts" && p[3] == "like" -> {
                val id = p[2].toLongOrNull() ?: return error(404, "Нет")
                val post = store.post(id) ?: return error(404, "Нет такой записи")
                if (!canSeePost(me, post)) return error(403, "Закрытый профиль")
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
                val closed = body.optJSONObject("privacy")?.optBoolean("closedProfile", u.closed) ?: u.closed
                store.updateUser(u.copy(name = name, profile = body.toString(), profileUpdated = now, closed = closed))
                // Профиль открыли — все ждущие заявки принимаются
                if (u.closed && !closed) store.followers(me, accepted = false).forEach { from ->
                    store.saveFollow(FollowRow(from, me, true, now))
                    event(from, JSONObject().put("type", "follow").put("user", me).put("state", "accepted"))
                }
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
    /** Запись для того, кто смотрит: номер, автор, счётчики, «нравится ли мне», последние комментарии. */
    private fun postJson(row: PostRow, liked: Boolean, viewer: Int, comments: Int = 0): JSONObject {
        val o = runCatching { JSONObject(row.body) }.getOrDefault(JSONObject())
        val last = if (comments > 0) store.lastComments(row.id, 4).filter { visibleComment(viewer, it) }.takeLast(2) else emptyList()
        val likedComments = store.commentsLikedBy(viewer, last.map { it.id })
        o.put("id", row.id).put("author", row.author).put("createdAt", row.created).put("minutesAgo", 0)
            .put("likes", row.likes).put("liked", liked).put("commentsOff", row.commentsOff)
            .put("comments", comments).put("commentList", JSONArray(last.map { commentJson(it, it.id in likedComments) })).put("outboxKey", "")
        authorInfo(row.author)?.let { o.put("authorInfo", it) }
        return o
    }

    /** Чья лента: друзья (и свои), «Интересное» или записи одного человека. */
    private sealed class PageKind {
        object Feed : PageKind()
        object Explore : PageKind()
        class Author(val id: Int) : PageKind()
    }

    private fun page(me: Int, kind: PageKind, r: Req): Resp {
        val limit = (r.query["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
        val after = r.query["cursor"]?.let(::decodeCursor)
        val mine = store.following(me) + me
        val rows = when (kind) {
            PageKind.Feed -> store.postsBy(mine, after?.first, after?.second, limit + 1)
            PageKind.Explore -> store.explore(mine, after?.first, after?.second, limit + 1)
            is PageKind.Author -> store.posts(kind.id, after?.first, after?.second, limit + 1)
        }
        // Курсор — по всем строкам; показываем только то, что можно видеть (бан, блокировки)
        val page = rows.take(limit)
        val chunk = page.filter { canSee(me, it.author) }
        val liked = store.likedBy(me, chunk.map { it.id })
        val counts = store.commentCounts(chunk.map { it.id })
        val next = if (rows.size > limit) page.last().let { encodeCursor(it.created, it.id) } else null
        val items = JSONArray(chunk.map { postJson(it, it.id in liked, me, counts[it.id] ?: 0) })
        return json(JSONObject().put("items", items).put("next", next ?: JSONObject.NULL))
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

    private fun createPost(me: Int, r: Req, community: CommunityRow? = null): Resp {
        if (community != null) {
            if (!store.isMember(community.id, me)) return error(403, "Сначала вступите в группу")
            val onlyOwner = runCatching { JSONObject(community.body).optBoolean("onlyOwnerPosts") }.getOrDefault(false)
            if (onlyOwner && community.owner != me) return error(403, "Писать в группу может только создатель")
        }
        val body = bodyJson(r) ?: return error(400, "Нужен JSON")
        if (body.optString("text").length > MAX_TEXT) return error(400, "Текст длиннее $MAX_TEXT знаков")
        val media = cleanMedia(me, body.optJSONArray("media"), 10) ?: return error(422, "Загрузка не закончена")
        listOf("id", "author", "authorInfo", "likes", "liked", "comments", "commentList", "createdAt", "outboxKey", "commentsOff", "minutesAgo").forEach(body::remove)
        body.put("media", media)
        if (!body.has("text")) body.put("text", "")
        body.remove("editedAt")
        body.remove("community")
        val row = store.createPost(me, clock(), body.toString(), community = community?.id ?: 0)
        return json(postJson(row, false, me), 201)
    }

    /**
     * Видна ли запись: личная — как автор (canSee); в сообществе — всем,
     * кроме заблокированных и при бане автора (сообщества открытые).
     */
    private fun canSeePost(viewer: Int, post: PostRow): Boolean {
        if (post.community == 0) return canSee(viewer, post.author)
        if (post.author == viewer) return true
        val c = store.community(post.community) ?: return false
        if (!communityVisible(viewer, c)) return false
        return store.user(post.author)?.banned == false && !store.blockedEither(viewer, post.author)
    }

    // ── Сообщества ───────────────────────────────────────────────────

    /** Сообщество видно, если создатель не забанен и не заблокирован (в любую сторону). */
    private fun communityVisible(viewer: Int, c: CommunityRow): Boolean =
        c.owner == viewer || (store.user(c.owner)?.banned == false && !store.blockedEither(viewer, c.owner))

    private fun communityJson(c: CommunityRow, me: Int): JSONObject {
        val o = runCatching { JSONObject(c.body) }.getOrDefault(JSONObject())
        o.put("id", c.id).put("name", c.name).put("topic", c.topic).put("ownerId", c.owner)
            .put("members", store.memberCount(c.id)).put("joined", store.isMember(c.id, me)).put("mine", c.owner == me)
            .put("muted", false).put("posts", JSONArray()).put("createdAt", c.created)
        if (!o.has("description")) o.put("description", "")
        authorInfo(c.owner)?.let { o.put("ownerInfo", it) }
        return o
    }

    /**
     * Тело сообщества — только известные поля: описание, заставка
     * (seed или загруженное фото), сведения, витрина, фон (без своего
     * фото — его файл живёт на телефоне), цвет, правила записей.
     * null — загрузка ещё не закончена или не своя.
     */
    private fun cleanCommunity(me: Int, b: JSONObject, communityId: Int): JSONObject? {
        val o = JSONObject().put("description", b.optString("description").take(1000)).put("seed", b.optInt("seed"))
            .put("onlyOwnerPosts", b.optBoolean("onlyOwnerPosts")).put("accent", b.optString("accent").take(20))
        val cover = b.optJSONObject("cover")
        if (cover != null) {
            val m = cleanMedia(me, JSONArray().put(cover), 1) ?: return null
            o.put("cover", if (m.length() > 0) m.getJSONObject(0) else JSONObject.NULL)
        } else o.put("cover", JSONObject.NULL)
        val info = b.optJSONObject("info") ?: JSONObject()
        o.put("info", JSONObject().apply {
            for (k in listOf("about", "website", "phone", "email", "address", "hours")) put(k, info.optString(k).take(if (k == "about") 2000 else 200))
        })
        val shop = JSONArray()
        val items = b.optJSONArray("shop") ?: JSONArray()
        for (i in 0 until minOf(items.length(), 100)) {
            val pr = items.optJSONObject(i) ?: continue
            val photos = cleanMedia(me, pr.optJSONArray("photos"), 10) ?: return null
            shop.put(JSONObject().put("id", pr.optLong("id")).put("title", pr.optString("title").take(120)).put("price", pr.optLong("price"))
                .put("description", pr.optString("description").take(2000)).put("photos", photos).put("category", pr.optString("category").take(60))
                .put("inStock", pr.optBoolean("inStock", true)).put("url", pr.optString("url").take(500)).put("seed", pr.optInt("seed")))
        }
        o.put("shop", shop)
        val bd = b.optJSONObject("backdrop")
        o.put("backdrop", if (bd != null && bd.optString("style") != "photo") JSONObject().put("style", bd.optString("style").take(20)).put("dim", bd.optDouble("dim", 0.35)) else JSONObject.NULL)
        // Закрепить можно только запись этой группы
        val pin = if (b.isNull("pinnedPostId")) 0L else b.optLong("pinnedPostId")
        o.put("pinnedPostId", if (pin > 0 && communityId != 0 && store.post(pin)?.community == communityId) pin else JSONObject.NULL)
        return o
    }

    private fun createCommunity(me: Int, r: Req): Resp {
        val b = bodyJson(r) ?: return error(400, "Нужен JSON")
        val name = b.optString("name").trim().take(MAX_TITLE)
        if (name.isEmpty()) return error(400, "Нужно название")
        if (store.communitiesOf(me).count { it.owner == me } >= 50) return error(429, "Не больше 50 своих групп")
        val body = cleanCommunity(me, b, 0) ?: return error(422, "Загрузка не закончена")
        val row = store.createCommunity(me, name, b.optString("topic").take(40), clock(), body.toString())
        return json(communityJson(row, me), 201)
    }

    /** PATCH — тело целиком (как его видит владелец), название и тема — если есть. */
    private fun updateCommunity(me: Int, c: CommunityRow, r: Req): Resp {
        if (c.owner != me) return error(403, "Не ваша группа")
        val b = bodyJson(r) ?: return error(400, "Нужен JSON")
        val name = b.optString("name").trim().take(MAX_TITLE).ifEmpty { c.name }
        val topic = if (b.has("topic")) b.optString("topic").take(40) else c.topic
        val body = cleanCommunity(me, b, c.id) ?: return error(422, "Загрузка не закончена")
        // Файлы, которых больше нет в группе (старая заставка, фото товаров), — удалить
        val before = mediaPaths(c.body)
        store.updateCommunity(c.id, name, topic, body.toString())
        (before - mediaPaths(body.toString())).forEach(::dropUpload)
        return json(communityJson(store.community(c.id) ?: c, me))
    }

    /** Загрузки в теле сообщества: заставка и фото товаров. */
    private fun mediaPaths(body: String): Set<String> {
        val o = runCatching { JSONObject(body) }.getOrNull() ?: return emptySet()
        val out = HashSet<String>()
        o.optJSONObject("cover")?.optString("path")?.let(out::add)
        val shop = o.optJSONArray("shop") ?: JSONArray()
        for (i in 0 until shop.length()) {
            val ph = shop.optJSONObject(i)?.optJSONArray("photos") ?: continue
            for (j in 0 until ph.length()) ph.optJSONObject(j)?.optString("path")?.let(out::add)
        }
        return out.filter { it.contains("/v1/uploads/") }.toSet()
    }

    private fun dropUpload(path: String) {
        val id = path.substringAfterLast('/')
        store.deleteUpload(id)
        fileOf(id).delete()
    }

    /** Удалить сообщество: записи, их файлы и файлы самой группы. */
    private fun purgeCommunity(c: CommunityRow) {
        var after: PostRow? = null
        while (true) {
            val chunk = store.communityPosts(c.id, after?.created, after?.id, 200)
            chunk.forEach { purgeMedia(it.body) }
            if (chunk.size < 200) break
            after = chunk.last()
        }
        mediaPaths(c.body).forEach(::dropUpload)
        store.deleteCommunity(c.id)
    }

    private fun communityPage(me: Int, c: CommunityRow, r: Req): Resp {
        val limit = (r.query["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
        val after = r.query["cursor"]?.let(::decodeCursor)
        val rows = store.communityPosts(c.id, after?.first, after?.second, limit + 1)
        val page = rows.take(limit)
        val chunk = page.filter { canSeePost(me, it) }
        val liked = store.likedBy(me, chunk.map { it.id })
        val counts = store.commentCounts(chunk.map { it.id })
        val next = if (rows.size > limit) page.last().let { encodeCursor(it.created, it.id) } else null
        val items = JSONArray(chunk.map { postJson(it, it.id in liked, me, counts[it.id] ?: 0).put("community", c.id) })
        return json(JSONObject().put("items", items).put("next", next ?: JSONObject.NULL))
    }

    // ── PRISM ────────────────────────────────────────────────────────

    private fun prismJson(row: PostRow, liked: Boolean, viewer: Int, comments: Int = 0): JSONObject {
        val o = postJson(row, liked, viewer, comments)
        o.remove("outboxKey"); o.remove("minutesAgo")
        if (!o.has("caption")) o.put("caption", "")
        o.put("saved", false)
        return o
    }

    private fun createPrism(me: Int, r: Req): Resp {
        val b = bodyJson(r) ?: return error(400, "Нужен JSON")
        val video = b.optJSONObject("video") ?: return error(400, "Нужно видео")
        val v = cleanMedia(me, JSONArray().put(video), 1) ?: return error(422, "Загрузка не закончена")
        if (v.length() == 0 || !v.getJSONObject(0).optString("path").contains("/v1/uploads/")) return error(400, "Видео — загрузкой")
        val cover = b.optJSONObject("cover")?.let { cleanMedia(me, JSONArray().put(it), 1) ?: return error(422, "Загрузка не закончена") }
        val caption = b.optString("caption")
        if (caption.length > MAX_TEXT) return error(400, "Подпись длиннее $MAX_TEXT знаков")
        val clean = JSONObject().put("caption", caption).put("music", b.optString("music").take(120))
            .put("seed", b.optInt("seed")).put("durationMs", b.optInt("durationMs", 9_000).coerceIn(0, 10 * 60_000))
            .put("video", v.getJSONObject(0))
            .put("cover", if (cover != null && cover.length() > 0) cover.getJSONObject(0) else JSONObject.NULL)
        val row = store.createPost(me, clock(), clean.toString(), kind = "prism")
        return json(prismJson(row, false, me), 201)
    }

    private fun prismPage(me: Int, r: Req): Resp {
        val limit = (r.query["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
        val after = r.query["cursor"]?.let(::decodeCursor)
        val rows = store.prisms(after?.first, after?.second, limit + 1)
        val page = rows.take(limit)
        val chunk = page.filter { canSee(me, it.author) }
        val liked = store.likedBy(me, chunk.map { it.id })
        val counts = store.commentCounts(chunk.map { it.id })
        val next = if (rows.size > limit) page.last().let { encodeCursor(it.created, it.id) } else null
        val items = JSONArray(chunk.map { prismJson(it, it.id in liked, me, counts[it.id] ?: 0) })
        return json(JSONObject().put("items", items).put("next", next ?: JSONObject.NULL))
    }

    // ── «Актуальное» ─────────────────────────────────────────────────

    /** Подборки своих историй: только свои истории, не больше 50 подборок по 100. */
    private fun saveHighlights(me: Int, r: Req): Resp {
        val arr = bodyJson(r)?.optJSONArray("items") ?: return error(400, "Нужен items")
        val out = JSONArray()
        for (i in 0 until minOf(arr.length(), 50)) {
            val h = arr.optJSONObject(i) ?: continue
            val ids = h.optJSONArray("stories") ?: JSONArray()
            val mine = (0 until ids.length()).map { ids.optLong(it) }.distinct()
                .filter { id -> store.story(id)?.author == me }.take(100)
            if (mine.isEmpty()) continue
            val cover = h.optLong("cover").takeIf { it in mine } ?: mine.first()
            out.put(JSONObject().put("id", h.optLong("id")).put("title", h.optString("title").take(40)).put("stories", JSONArray(mine)).put("cover", cover))
        }
        store.setHighlights(me, out.toString())
        return empty()
    }

    /** «Актуальное» человека: истории внутри — целиком, даже старше суток. */
    private fun highlightsOf(me: Int, owner: Int): Resp {
        val arr = runCatching { JSONArray(store.highlights(owner)) }.getOrDefault(JSONArray())
        val close = owner == me || me in store.closeFriends(owner)
        val items = JSONArray()
        for (i in 0 until arr.length()) {
            val h = arr.optJSONObject(i) ?: continue
            val ids = h.optJSONArray("stories") ?: JSONArray()
            val rows = (0 until ids.length()).mapNotNull { store.story(ids.optLong(it)) }
                .filter { it.author == owner && (!it.closeFriends || close) }
            if (rows.isEmpty()) continue
            val cover = h.optLong("cover").takeIf { c -> rows.any { it.id == c } } ?: rows.first().id
            items.put(JSONObject().put("id", h.optLong("id")).put("owner", owner).put("title", h.optString("title"))
                .put("stories", JSONArray(rows.map { it.id })).put("cover", cover)
                .put("storyList", JSONArray(rows.map { storyJson(it, me, true) })))
        }
        return json(JSONObject().put("items", items))
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
        // Заблокированных (в любую сторону) в группу не добавить — иначе блокировку обошли бы группой
        val ids = (0 until arr.length()).map { arr.optInt(it) }
            .filter { it != me && store.user(it)?.banned == false && !store.blockedEither(me, it) }.distinct().take(MAX_MEMBERS - 1)
        val members = listOf(MemberRow(id, me, admin = true, accepted = true)) + ids.map { MemberRow(id, it, admin = false, accepted = true) }
        if (!store.createChat(ChatRow(id, "group", title, me, clock()), members)) return error(409, "Номер занят")
        groupChanged(id)
        return json(chatJson(store.chat(id)!!, me), 201)
    }

    /** Личная переписка с человеком: одна на пару, номер выбирает сервер. */
    private fun direct(me: Int, r: Req): Resp {
        val other = bodyJson(r)?.optInt("user") ?: return error(400, "Нужен user")
        if (other == me) return error(400, "Это вы")
        store.user(other)?.takeIf { !it.banned } ?: return error(404, "Нет такого человека")
        if (store.blockedEither(me, other)) return error(403, "Нельзя написать")
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
                    if (store.user(userId)?.banned != false) return error(404, "Нет такого человека")
                    if (store.blockedEither(me, userId)) return error(403, "Нельзя добавить")
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
        // Личная переписка с тем, кто заблокировал (или кого заблокировали), — закрыта
        if (chat.direct && ms.any { it.userId != me && store.blockedEither(me, it.userId) }) return error(403, "Нельзя написать")
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
        // Тем, кто заблокировал автора (общая группа), — ни события, ни пуша
        val to = ms.filter { it.userId != me && !store.blockedEither(me, it.userId) }
        to.forEach { m -> hub.send(m.userId, JSONObject(event.toString()).put("message", messageJson(row, m.userId)).toString()) }
        // Кто не на связи — пуш. Секретное — без текста: сервер его и не знает
        val secret = body.optBoolean("secret")
        val preview = if (secret) "" else body.optString("text").take(120)
        val data = mapOf("type" to "message", "chatId" to chat.id.toString(), "body" to preview)
        to.filter { !hub.online(it.userId) }.forEach { m -> notify(m.userId, me, data) }
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
        // Аккаунт удалён или забанен, пока соединение было открыто, — молчим
        if (store.user(me)?.banned != false) return
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return
        val chatId = o.optInt("chat")
        when (o.optString("type")) {
            "read" -> read(me, chatId, o.optLong("upTo"))
            "typing" -> {
                val ms = store.members(chatId)
                val mine = ms.firstOrNull { it.userId == me } ?: return
                if (!mine.accepted || ms.size > 50) return
                if (ms.size == 2 && ms.any { it.userId != me && store.blockedEither(me, it.userId) }) return
                val ev = JSONObject().put("type", "typing").put("chat", chatId).put("user", me).put("on", o.optBoolean("on")).toString()
                hub.sendAll(ms.filter { it.userId != me }.map { it.userId }, ev)
            }
        }
    }

    // ── Подписки ─────────────────────────────────────────────────────

    private fun people(ids: List<Int>, me: Int): Resp =
        json(JSONObject().put("items", JSONArray(ids.mapNotNull(store::user).map { userJson(it, me) })))

    /** Подписаться (закрытому профилю — заявка) или отписаться / отозвать заявку. */
    private fun follow(me: Int, target: Int?, method: String): Resp {
        val u = target?.let(store::user) ?: return error(404, "Нет такого человека")
        if (u.id == me) return error(400, "Это вы")
        if (method == "PUT" && (u.banned || store.blockedEither(me, u.id))) return error(403, "Нельзя подписаться")
        val existing = store.follow(me, u.id)
        return when (method) {
            "PUT" -> {
                if (existing == null || !existing.accepted) {
                    // Закрытый профиль — заявка; но если он сам на меня подписан (принимаю его заявку) — сразу
                    val accepted = !u.closed || store.follow(u.id, me)?.accepted == true
                    store.saveFollow(FollowRow(me, u.id, accepted, clock()))
                    event(u.id, JSONObject().put("type", "follow").put("user", me).put("state", if (accepted) "follow" else "request"))
                    notify(u.id, me, mapOf("type" to if (accepted) "new_follower" else "follow_request"))
                }
                val state = if (store.follow(me, u.id)?.accepted == true) "following" else "requested"
                json(JSONObject().put("state", state))
            }
            "DELETE" -> {
                store.removeFollow(me, u.id)
                json(JSONObject().put("state", "none"))
            }
            else -> error(405, "PUT или DELETE")
        }
    }

    // ── Комментарии ──────────────────────────────────────────────────

    private fun commentJson(c: CommentRow, liked: Boolean): JSONObject {
        val o = runCatching { JSONObject(c.body) }.getOrDefault(JSONObject())
        o.put("id", c.id).put("post", c.postId).put("author", c.author).put("createdAt", c.created).put("minutesAgo", 0)
            .put("likes", c.likes).put("liked", liked)
        if (!o.has("text")) o.put("text", "")
        if (!o.has("replyTo")) o.put("replyTo", "")
        authorInfo(c.author)?.let { o.put("authorInfo", it) }
        return o
    }

    private fun commentPage(me: Int, post: PostRow, r: Req): Resp {
        val limit = (r.query["limit"]?.toIntOrNull() ?: 50).coerceIn(1, 100)
        val rows = store.comments(post.id, r.query["cursor"]?.toLongOrNull(), limit + 1)
        val chunk = rows.take(limit)
        val liked = store.commentsLikedBy(me, chunk.map { it.id })
        val next = if (rows.size > limit) chunk.last().id.toString() else null
        // Курсор — по всем, а показываем без заблокированных и забаненных
        val shown = chunk.filter { visibleComment(me, it) }
        return json(JSONObject().put("items", JSONArray(shown.map { commentJson(it, it.id in liked) })).put("next", next ?: JSONObject.NULL))
    }

    private fun addComment(me: Int, post: PostRow, r: Req): Resp {
        if (post.commentsOff && post.author != me) return error(403, "Автор выключил комментарии")
        val body = bodyJson(r) ?: return error(400, "Нужен JSON")
        val text = body.optString("text").trim()
        if (text.length > MAX_COMMENT) return error(400, "Комментарий длиннее $MAX_COMMENT знаков")
        val sticker = body.optJSONObject("sticker")
        if (text.isEmpty() && sticker == null) return error(400, "Пустой комментарий")
        val clean = JSONObject().put("text", text).put("replyTo", body.optString("replyTo").take(60))
        if (sticker != null) clean.put("sticker", sticker)
        val row = store.createComment(post.id, me, clock(), clean.toString())
        val out = commentJson(row, false)
        if (post.author != me) {
            event(post.author, JSONObject().put("type", "comment").put("post", post.id).put("comment", out))
            notify(post.author, me, mapOf("type" to "comment", "postId" to post.id.toString(), "body" to text.take(120)))
        }
        return json(out, 201)
    }

    // ── Истории ──────────────────────────────────────────────────────

    /** Видит ли историю: свой, можно смотреть автора и (если «для близких») — в близких. */
    private fun storyVisible(me: Int, st: StoryRow): Boolean {
        if (st.author == me) return true
        if (!canSee(me, st.author)) return false
        if (store.follow(me, st.author)?.accepted != true) return false
        return !st.closeFriends || me in store.closeFriends(st.author)
    }

    private fun storyJson(st: StoryRow, me: Int, seen: Boolean): JSONObject {
        val o = runCatching { JSONObject(st.body) }.getOrDefault(JSONObject())
        o.put("id", st.id).put("author", st.author).put("createdAt", st.created).put("expiresAt", st.expires)
            .put("close", st.closeFriends).put("seen", seen)
        if (!o.has("text")) o.put("text", "")
        // Кто смотрел — только автору
        val views = if (st.author == me) store.storyViews(st.id) else emptyList()
        o.put("views", JSONArray(views.map { JSONObject().put("user", it.userId).put("at", it.at).put("liked", it.liked) }))
        authorInfo(st.author)?.let { o.put("authorInfo", it) }
        return o
    }

    private fun stories(me: Int): Resp {
        val now = clock()
        val rows = store.stories(store.following(me) + me, now).filter { storyVisible(me, it) }
        val seen = store.storiesSeenBy(me, rows.map { it.id })
        return json(JSONObject().put("items", JSONArray(rows.map { storyJson(it, me, it.id in seen) })))
    }

    private fun createStory(me: Int, r: Req): Resp {
        val body = bodyJson(r) ?: return error(400, "Нужен JSON")
        val text = body.optString("text").take(300)
        val mediaArr = body.optJSONObject("media")?.let { JSONArray().put(it) }
        val media = cleanMedia(me, mediaArr, 1) ?: return error(422, "Загрузка не закончена")
        if (text.isBlank() && media.length() == 0) return error(400, "Пустая история")
        val clean = JSONObject().put("text", text).put("bg", body.optInt("bg")).put("seed", body.optInt("seed"))
            .put("media", if (media.length() > 0) media.getJSONObject(0) else JSONObject.NULL)
        val now = clock()
        val row = store.createStory(me, now, now + STORY_TTL, clean.toString(), body.optBoolean("close"))
        return json(storyJson(row, me, false), 201)
    }

    // ── Модерация ────────────────────────────────────────────────────

    /**
     * Заблокировать: подписки в обе стороны снимаются, друг друга не видно
     * (записи, истории, комментарии, поиск), написать нельзя.
     */
    private fun block(me: Int, target: Int?, method: String): Resp {
        val u = target?.let(store::user) ?: return error(404, "Нет такого человека")
        if (u.id == me) return error(400, "Это вы")
        when (method) {
            "PUT" -> {
                store.setBlock(me, u.id, true)
                store.removeFollow(me, u.id)
                store.removeFollow(u.id, me)
                store.setCloseFriends(me, store.closeFriends(me) - u.id)
            }
            "DELETE" -> store.setBlock(me, u.id, false)
            else -> return error(405, "PUT или DELETE")
        }
        return empty()
    }

    private val REPORT_KINDS = setOf("post", "comment", "story", "prism", "message", "person")

    /** Жалоба — модераторам (не больше 100 в сутки от одного человека). */
    private fun createReport(me: Int, r: Req): Resp {
        val body = bodyJson(r) ?: return error(400, "Нужен JSON")
        val kind = body.optString("kind")
        if (kind !in REPORT_KINDS) return error(400, "kind: " + REPORT_KINDS.joinToString())
        val today = store.reports(null, 500).count { it.reporter == me && clock() - it.created < 86_400_000L }
        if (today >= 100) return json(JSONObject().put("error", "Слишком много жалоб"), 429, mapOf("Retry-After" to "3600"))
        val target = body.optLong("target")
        var preview = body.optString("preview").take(300)
        // Автора сервер определяет сам. Чего проверить нельзя (PRISM пока на телефонах) —
        // автор «со слов», и заблокировать его по такой жалобе модератор не сможет
        var author = 0
        when (kind) {
            "post" -> store.post(target)?.takeIf { it.kind == "post" }?.let { author = it.author }
            "prism" -> store.post(target)?.takeIf { it.kind == "prism" }?.let { author = it.author }
            "comment" -> store.comment(target)?.let { author = it.author }
            "story" -> store.story(target)?.let { author = it.author }
            "person" -> author = target.toInt()
            "message" -> {
                // Сообщение — по переписке, автору и времени сервера; жалуется участник
                val chatId = body.optInt("chat")
                val m = if (store.members(chatId).any { it.userId == me }) store.messageAt(chatId, body.optInt("author"), body.optLong("sentAt")) else null
                if (m != null) {
                    author = m.author
                    val o = runCatching { JSONObject(m.body) }.getOrNull()
                    if (o != null && !o.optBoolean("secret")) preview = o.optString("text").take(300)
                }
            }
        }
        val verified = author != 0
        if (!verified) author = body.optInt("author")
        val row = store.createReport(ReportRow(
            0, me, kind, target, author, body.optString("reason").take(40).ifEmpty { "other" },
            body.optString("note").take(500), preview, clock(), status = if (verified) "open" else "open-unverified",
        ))
        return json(JSONObject().put("id", row.id), 201)
    }

    private fun adminReports(status: String?): Resp {
        val rows = if (status == "open") (store.reports("open", 200) + store.reports("open-unverified", 200)).sortedByDescending { it.id }
        else store.reports(status, 400)
        val items = rows.map { rep ->
            val author = store.user(rep.author)
            val reporter = store.user(rep.reporter)
            // Что сейчас на сервере — текст записи или комментария, если они ещё есть
            val current = when (rep.kind) {
                "post" -> store.post(rep.target)?.body?.let { runCatching { JSONObject(it).optString("text") }.getOrNull() }
                "prism" -> store.post(rep.target)?.body?.let { runCatching { JSONObject(it).optString("caption") }.getOrNull() }
                "comment" -> store.comment(rep.target)?.body?.let { runCatching { JSONObject(it).optString("text") }.getOrNull() }
                "story" -> store.story(rep.target)?.body?.let { runCatching { JSONObject(it).optString("text") }.getOrNull() }
                else -> null
            }
            JSONObject().put("id", rep.id).put("kind", rep.kind).put("target", rep.target).put("reason", rep.reason)
                .put("note", rep.note).put("preview", rep.preview).put("current", current ?: JSONObject.NULL)
                .put("created", rep.created).put("status", if (rep.status == "open-unverified") "open" else rep.status)
                .put("verified", rep.status != "open-unverified")
                .put("author", JSONObject().put("id", rep.author).put("login", author?.login ?: "—").put("name", author?.name ?: "удалён").put("banned", author?.banned ?: false))
                .put("reporter", reporter?.login ?: "—")
        }
        return json(JSONObject().put("items", JSONArray(items)))
    }

    /** dismiss — оставить; remove — удалить то, на что жалоба; ban — удалить и заблокировать автора. */
    private fun resolveReport(id: Long, action: String): Resp {
        val rep = store.report(id) ?: return error(404, "Нет такой жалобы")
        when (action) {
            "dismiss" -> store.setReportStatus(id, "dismissed")
            "remove", "ban" -> {
                if (action == "ban" && rep.status == "open-unverified") return error(400, "Автор не подтверждён — заблокировать нельзя")
                when (rep.kind) {
                    "post", "prism" -> store.post(rep.target)?.let { purgeMedia(it.body); store.deletePost(it.id) }
                    "comment" -> store.comment(rep.target)?.let { purgeMedia(it.body); store.deleteComment(it.id) }
                    "story" -> store.story(rep.target)?.let { purgeMedia(it.body); store.deleteStory(it.id) }
                }
                if (action == "ban") store.user(rep.author)?.let { u ->
                    store.updateUser(u.copy(banned = true))
                    store.devices(u.id).forEach { store.deleteDevice(it.id) }
                    hub.kick(u.id)
                }
                store.setReportStatus(id, if (action == "ban") "banned" else "removed")
            }
            else -> return error(400, "action: dismiss, remove, ban")
        }
        return empty()
    }

    /**
     * Файлы удалённого содержимого (фото и видео записи, истории,
     * комментария): ссылки на них больше не работают ни у кого.
     */
    private fun purgeMedia(body: String) {
        val o = runCatching { JSONObject(body) }.getOrNull() ?: return
        val paths = ArrayList<String>()
        o.optJSONArray("media")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.optString("path")?.let(paths::add) }
        o.optJSONObject("media")?.optString("path")?.let(paths::add)
        o.optJSONObject("video")?.optString("path")?.let(paths::add)
        o.optJSONObject("cover")?.optString("path")?.let(paths::add)
        o.optJSONObject("sticker")?.optString("path")?.let(paths::add)
        paths.filter { it.contains("/v1/uploads/") }.map { it.substringAfterLast('/') }.forEach { id ->
            store.deleteUpload(id)
            fileOf(id).delete()
        }
    }

    /** Удалить аккаунт: всё, что человек выложил, и его файлы. Нужен пароль. */
    private fun deleteMe(me: Int, r: Req): Resp {
        val u = store.user(me) ?: return error(404, "Нет")
        if (tooManyFailures("pw:$me")) return json(JSONObject().put("error", "Слишком много попыток"), 429, mapOf("Retry-After" to "900"))
        val password = bodyJson(r)?.optString("password").orEmpty()
        if (!Crypto.checkPassword(password, u.salt, u.passHash)) {
            failed("pw:$me")
            return error(403, "Пароль не подходит")
        }
        // Свои сообщества — с записями участников и их файлами
        store.communitiesOf(me).filter { it.owner == me }.forEach(::purgeCommunity)
        val files = store.uploadsOf(me).map { it.id }
        store.deleteUser(me)
        // Файлы — после базы: не вышло стереть из базы — файлы целы
        files.forEach { fileOf(it).delete() }
        hub.kick(me)
        return empty()
    }

    // ── Живой канал и пуши ───────────────────────────────────────────

    private fun event(userId: Int, o: JSONObject) = hub.send(userId, o.toString())

    /**
     * Пуш человеку на все его телефоны: факты (кто, что), текст — на
     * телефоне. Отправляется в фоне; токен, который FCM больше не знает, забывается.
     */
    fun notify(userId: Int, actorId: Int, data: Map<String, String>) {
        if (pusher === NoPush) return
        val tokens = store.devices(userId).map { it.pushToken }.filter { it.isNotEmpty() }.distinct()
        if (tokens.isEmpty()) return
        val actor = store.user(actorId)
        val full = data + mapOf("actorId" to actorId.toString(), "actor" to (actor?.name ?: ""))
        background(Runnable {
            for (t in tokens) {
                val alive = runCatching { pusher.push(t, full) }.getOrDefault(true)
                if (!alive) store.clearPushToken(t)
            }
        })
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
