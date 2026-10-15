package ru.wishka.memory.server

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** Ядро сервера целиком; PgStoreTest гоняет те же проверки на PostgreSQL. */
open class CoreTest {
    protected open fun newStore(): Store = MemoryStore()

    private var now = 1_700_000_000_000L
    private val dir = Files.createTempDirectory("memory-server").toFile()
    private val core by lazy { Core(newStore(), dir, Hub()) { now } }

    private class Inbox : Hub.Peer {
        val frames = mutableListOf<JSONObject>()
        override fun send(text: String) { frames += JSONObject(text) }
        fun of(type: String) = frames.filter { it.optString("type") == type }
    }

    private fun call(method: String, path: String, token: String? = null, body: Any? = null, headers: Map<String, String> = emptyMap(), query: Map<String, String> = emptyMap(), raw: ByteArray? = null): Resp {
        val h = HashMap(headers)
        if (token != null) h["Authorization"] = "Bearer $token"
        return core.handle(Req(method, path, query, h, raw ?: body?.toString()?.toByteArray()))
    }

    private data class Who(val id: Int, val access: String, val refresh: String)

    private fun register(login: String, password: String = "secret1", name: String = login.replaceFirstChar { it.uppercase() }): Who {
        val r = call("POST", "/v1/auth/register", body = JSONObject().put("login", login).put("password", password).put("name", name)
            .put("device", JSONObject().put("name", "Pixel").put("platform", "android")))
        assertEquals(r.text, 201, r.code)
        val o = r.json()
        return Who(o.getJSONObject("user").getInt("id"), o.getString("access"), o.getString("refresh"))
    }

    @Test
    fun registerLoginRefreshAndExpiry() {
        val a = register("anna")
        assertTrue(a.id >= 100_000)
        assertEquals(409, call("POST", "/v1/auth/register", body = JSONObject().put("login", "anna").put("password", "secret1")).code)
        assertEquals(400, call("POST", "/v1/auth/register", body = JSONObject().put("login", "Не латиница").put("password", "secret1")).code)
        assertEquals(400, call("POST", "/v1/auth/register", body = JSONObject().put("login", "boris").put("password", "123")).code)

        assertEquals(401, call("POST", "/v1/auth/login", body = JSONObject().put("login", "anna").put("password", "wrong")).code)
        val login = call("POST", "/v1/auth/login", body = JSONObject().put("login", "ANNA").put("password", "secret1"))
        assertEquals(200, login.code)
        assertEquals("anna", login.json().getJSONObject("user").getString("login"))
        assertEquals(900, login.json().getInt("expiresIn"))

        assertEquals(200, call("GET", "/v1/feed", a.access).code)
        assertEquals(401, call("GET", "/v1/feed").code)
        assertEquals(401, call("GET", "/v1/feed", "garbage").code)

        // Через 15 минут токен доступа больше не действует, обновление — да
        now += Core.ACCESS_TTL + 1
        assertEquals(401, call("GET", "/v1/feed", a.access).code)
        val r = call("POST", "/v1/auth/refresh", body = JSONObject().put("refresh", a.refresh))
        assertEquals(200, r.code)
        val fresh = r.json().getString("access")
        assertEquals(200, call("GET", "/v1/feed", fresh).code)
        // Токен обновления одноразовый
        assertEquals(401, call("POST", "/v1/auth/refresh", body = JSONObject().put("refresh", a.refresh)).code)

        assertEquals(204, call("POST", "/v1/auth/logout", fresh).code)
        assertEquals(401, call("GET", "/v1/feed", fresh).code)
        assertEquals(401, call("POST", "/v1/auth/refresh", body = JSONObject().put("refresh", r.json().getString("refresh"))).code)
    }

    @Test
    fun tooManyWrongPasswords() {
        register("anna")
        repeat(10) { assertEquals(401, call("POST", "/v1/auth/login", body = JSONObject().put("login", "anna").put("password", "x$it")).code) }
        val r = call("POST", "/v1/auth/login", body = JSONObject().put("login", "anna").put("password", "secret1"))
        assertEquals(429, r.code)
        assertEquals("900", r.headers["Retry-After"])
        now += 16 * 60_000
        assertEquals(200, call("POST", "/v1/auth/login", body = JSONObject().put("login", "anna").put("password", "secret1")).code)
    }

    @Test
    fun devicesListAndRevoke() {
        val a = register("anna")
        val second = call("POST", "/v1/auth/login", body = JSONObject().put("login", "anna").put("password", "secret1")
            .put("device", JSONObject().put("name", "iPhone").put("platform", "ios"))).json()
        val list = call("GET", "/v1/devices", a.access).json().getJSONArray("items")
        assertEquals(2, list.length())
        val other = (0 until list.length()).map { list.getJSONObject(it) }.first { !it.getBoolean("current") }
        assertEquals("iPhone", other.getString("name"))
        assertEquals(204, call("DELETE", "/v1/devices/${other.getString("id")}", a.access).code)
        assertEquals(401, call("GET", "/v1/feed", second.getString("access")).code)
        // Своё текущее так не удалить, чужое — тоже
        val mine = call("GET", "/v1/devices", a.access).json().getJSONArray("items").getJSONObject(0).getString("id")
        assertEquals(404, call("DELETE", "/v1/devices/$mine", a.access).code)
        val b = register("boris")
        assertEquals(404, call("DELETE", "/v1/devices/$mine", b.access).code)
        call("POST", "/v1/auth/login", body = JSONObject().put("login", "anna").put("password", "secret1"))
        assertEquals(204, call("POST", "/v1/devices/revoke-others", a.access).code)
        assertEquals(1, call("GET", "/v1/devices", a.access).json().getJSONArray("items").length())
    }

    @Test
    fun profileKeyAndPeople() {
        val a = register("anna", name = "Анна")
        val b = register("boris", name = "Борис")
        assertEquals(404, call("GET", "/v1/me", a.access).code)
        val put = call("PUT", "/v1/me", a.access, JSONObject().put("name", "Анна К.").put("bio", "Фото").put("city", "Ереван").put("id", 1))
        assertEquals(200, put.code)
        val me = call("GET", "/v1/me", a.access).json()
        assertEquals("Анна К.", me.getString("name"))
        assertEquals(a.id, me.getInt("id"))
        assertEquals(now, me.getLong("updatedAt"))

        assertEquals(400, call("PUT", "/v1/me/key", a.access, JSONObject().put("publicKey", "short")).code)
        val key = "A".repeat(44)
        assertEquals(204, call("PUT", "/v1/me/key", a.access, JSONObject().put("publicKey", key)).code)

        val seen = call("GET", "/v1/users/${a.id}", b.access).json()
        assertEquals("Анна К.", seen.getString("name"))
        assertEquals("anna", seen.getString("handle"))
        assertEquals("Ереван", seen.getString("city"))
        assertEquals(key, seen.getString("publicKey"))
        assertEquals(404, call("GET", "/v1/users/5", b.access).code)

        val found = call("GET", "/v1/users", b.access, query = mapOf("q" to "анн")).json().getJSONArray("items")
        assertEquals(1, found.length())
        assertEquals(a.id, found.getJSONObject(0).getInt("id"))
        assertEquals(0, call("GET", "/v1/users", b.access, query = mapOf("q" to "a")).json().getJSONArray("items").length())
        assertEquals(b.id, call("GET", "/v1/users", a.access, query = mapOf("q" to "bor")).json().getJSONArray("items").getJSONObject(0).getInt("id"))
    }

    @Test
    fun postsFeedLikesAndPages() {
        val a = register("anna", name = "Анна")
        val b = register("boris")
        val ids = (1..5).map { i ->
            now += 1000
            val r = call("POST", "/v1/posts", a.access, JSONObject().put("id", 7).put("author", 3).put("likes", 99).put("text", "Запись $i"))
            assertEquals(201, r.code)
            r.json().also { assertEquals(a.id, it.getInt("author")); assertEquals(0, it.getInt("likes")) }.getLong("id")
        }
        assertEquals(5, ids.toSet().size)

        val page1 = call("GET", "/v1/feed", b.access, query = mapOf("limit" to "3")).json()
        val items = page1.getJSONArray("items")
        assertEquals(3, items.length())
        assertEquals("Запись 5", items.getJSONObject(0).getString("text"))
        assertEquals("Анна", items.getJSONObject(0).getJSONObject("authorInfo").getString("name"))
        assertEquals("anna", items.getJSONObject(0).getJSONObject("authorInfo").getString("handle"))
        val page2 = call("GET", "/v1/feed", b.access, query = mapOf("limit" to "3", "cursor" to page1.getString("next"))).json()
        assertEquals(2, page2.getJSONArray("items").length())
        assertTrue(page2.isNull("next"))

        assertEquals(204, call("PUT", "/v1/posts/${ids[4]}/like", b.access).code)
        assertEquals(204, call("PUT", "/v1/posts/${ids[4]}/like", b.access).code)
        val forB = call("GET", "/v1/feed", b.access).json().getJSONArray("items").getJSONObject(0)
        assertEquals(1, forB.getInt("likes"))
        assertTrue(forB.getBoolean("liked"))
        val forA = call("GET", "/v1/feed", a.access).json().getJSONArray("items").getJSONObject(0)
        assertEquals(1, forA.getInt("likes"))
        assertFalse(forA.getBoolean("liked"))
        assertEquals(204, call("DELETE", "/v1/posts/${ids[4]}/like", b.access).code)
        assertEquals(0, call("GET", "/v1/feed", b.access).json().getJSONArray("items").getJSONObject(0).getInt("likes"))
        assertEquals(404, call("PUT", "/v1/posts/1/like", b.access).code)

        // Страница человека — только его записи
        call("POST", "/v1/posts", b.access, JSONObject().put("text", "Борис"))
        assertEquals(5, call("GET", "/v1/users/${a.id}/posts", b.access).json().getJSONArray("items").length())
        assertEquals(1, call("GET", "/v1/users/${b.id}/posts", a.access).json().getJSONArray("items").length())

        // Комментарии выключает только автор
        assertEquals(403, call("PATCH", "/v1/posts/${ids[0]}", b.access, JSONObject().put("commentsOff", true)).code)
        assertEquals(204, call("PATCH", "/v1/posts/${ids[0]}", a.access, JSONObject().put("commentsOff", true)).code)
        val last = call("GET", "/v1/users/${a.id}/posts", b.access).json().getJSONArray("items").getJSONObject(4)
        assertTrue(last.getBoolean("commentsOff"))
        assertEquals(400, call("POST", "/v1/posts", a.access, JSONObject().put("text", "x".repeat(2001))).code)
    }

    @Test
    fun idempotencyKeyRepeatsAnswer() {
        val a = register("anna")
        val h = mapOf("Idempotency-Key" to "k-1")
        val first = call("POST", "/v1/posts", a.access, JSONObject().put("text", "Один раз"), h)
        val again = call("POST", "/v1/posts", a.access, JSONObject().put("text", "Один раз"), h)
        assertEquals(201, first.code)
        assertEquals(200, again.code)
        assertEquals(first.json().getLong("id"), again.json().getLong("id"))
        assertEquals(1, call("GET", "/v1/feed", a.access).json().getJSONArray("items").length())
        // Ключи у разных людей не пересекаются
        val b = register("boris")
        assertEquals(201, call("POST", "/v1/posts", b.access, JSONObject().put("text", "Другой"), h).code)
    }

    @Test
    fun directChatIsARequestUntilAnswered() {
        val a = register("anna", name = "Анна")
        val b = register("boris")
        val boxA = Inbox().also { core.hub.join(a.id, it) }
        val boxB = Inbox().also { core.hub.join(b.id, it) }

        val created = call("POST", "/v1/chats/direct", a.access, JSONObject().put("user", b.id))
        assertEquals(201, created.code)
        val chat = created.json().getInt("id")
        assertEquals(b.id, created.json().getJSONObject("user").getInt("id"))
        assertEquals("direct", created.json().getString("kind"))
        assertEquals(chat, call("POST", "/v1/chats/direct", b.access, JSONObject().put("user", a.id)).json().getInt("id"))
        assertEquals(400, call("POST", "/v1/chats/direct", a.access, JSONObject().put("user", a.id)).code)
        assertEquals(404, call("POST", "/v1/chats/direct", a.access, JSONObject().put("user", 42)).code)

        val listB = call("GET", "/v1/chats", b.access).json().getJSONArray("items")
        assertEquals(1, listB.length())
        assertFalse(listB.getJSONObject(0).getBoolean("accepted"))
        assertEquals(a.id, listB.getJSONObject(0).getJSONObject("user").getInt("id"))

        val sent = call("POST", "/v1/chats/$chat/messages", a.access, JSONObject().put("id", 1).put("fromMe", true).put("text", "Привет").put("time", "12:00"))
        assertEquals(201, sent.code)
        assertTrue(sent.json().getBoolean("fromMe"))
        assertEquals(a.id, sent.json().getInt("authorId"))
        val ev = boxB.of("message").single()
        assertEquals(chat, ev.getInt("chat"))
        assertFalse(ev.getJSONObject("message").getBoolean("fromMe"))
        assertEquals("Анна", ev.getJSONObject("message").getJSONObject("authorInfo").getString("name"))
        assertTrue(boxA.of("message").isEmpty())

        // Пока Борис не принял — ни «печатает», ни «прочитано» Анна не видит
        core.onClientFrame(b.id, JSONObject().put("type", "typing").put("chat", chat).put("on", true).toString())
        core.onClientFrame(b.id, JSONObject().put("type", "read").put("chat", chat).put("upTo", now).toString())
        assertEquals(204, call("POST", "/v1/chats/$chat/read", b.access, JSONObject().put("upTo", now)).code)
        assertTrue(boxA.frames.isEmpty())

        // История — «моё» с точки зрения того, кто смотрит
        val hist = call("GET", "/v1/chats/$chat/messages", b.access).json().getJSONArray("items")
        assertFalse(hist.getJSONObject(0).getBoolean("fromMe"))
        assertTrue(call("GET", "/v1/chats/$chat/messages", a.access).json().getJSONArray("items").getJSONObject(0).getBoolean("fromMe"))

        // Ответил — принял
        call("POST", "/v1/chats/$chat/messages", b.access, JSONObject().put("text", "Привет!"))
        assertTrue(call("GET", "/v1/chats", b.access).json().getJSONArray("items").getJSONObject(0).getBoolean("accepted"))
        core.onClientFrame(b.id, JSONObject().put("type", "typing").put("chat", chat).put("on", true).toString())
        core.onClientFrame(b.id, JSONObject().put("type", "read").put("chat", chat).put("upTo", now).toString())
        assertEquals(1, boxA.of("typing").size)
        assertEquals(b.id, boxA.of("read").single().getInt("user"))

        // Посторонний не читает и не пишет
        val c = register("clara")
        assertEquals(403, call("GET", "/v1/chats/$chat/messages", c.access).code)
        assertEquals(403, call("POST", "/v1/chats/$chat/messages", c.access, JSONObject().put("text", "?")).code)
    }

    @Test
    fun messagePagesAndAccept() {
        val a = register("anna")
        val b = register("boris")
        val chat = call("POST", "/v1/chats/direct", a.access, JSONObject().put("user", b.id)).json().getInt("id")
        repeat(5) { call("POST", "/v1/chats/$chat/messages", a.access, JSONObject().put("text", "m$it")) }
        val p1 = call("GET", "/v1/chats/$chat/messages", b.access, query = mapOf("limit" to "3")).json()
        assertEquals("m4", p1.getJSONArray("items").getJSONObject(0).getString("text"))
        val p2 = call("GET", "/v1/chats/$chat/messages", b.access, query = mapOf("limit" to "3", "cursor" to p1.getString("next"))).json()
        assertEquals(listOf("m1", "m0"), (0 until 2).map { p2.getJSONArray("items").getJSONObject(it).getString("text") })
        assertTrue(p2.isNull("next"))
        assertEquals(204, call("POST", "/v1/chats/$chat/accept", b.access).code)
        assertTrue(call("GET", "/v1/chats", b.access).json().getJSONArray("items").getJSONObject(0).getBoolean("accepted"))
    }

    @Test
    fun groupsAdminsAndEvents() {
        val a = register("anna")
        val b = register("boris")
        val c = register("clara")
        val boxB = Inbox().also { core.hub.join(b.id, it) }
        val boxC = Inbox().also { core.hub.join(c.id, it) }
        val h = mapOf("Idempotency-Key" to "g-1")
        val body = JSONObject().put("id", 777).put("title", "Поход").put("members", JSONArray(listOf(b.id, 99)))
        val g = call("POST", "/v1/chats", a.access, body, h)
        assertEquals(201, g.code)
        assertEquals(listOf(a.id, b.id), g.json().getJSONArray("members").let { arr -> (0 until arr.length()).map { arr.getInt(it) } })
        assertEquals(200, call("POST", "/v1/chats", a.access, body, h).code)
        assertEquals(409, call("POST", "/v1/chats", b.access, body).code)
        assertEquals(1, boxB.of("group").size)

        assertEquals(403, call("PATCH", "/v1/chats/777", b.access, JSONObject().put("title", "Моё")).code)
        assertEquals(403, call("PUT", "/v1/chats/777/members/${c.id}", b.access).code)
        assertEquals(204, call("PUT", "/v1/chats/777/admins/${b.id}", a.access).code)
        assertEquals(403, call("PUT", "/v1/chats/777/admins/${b.id}", b.access).code)
        assertEquals(200, call("PATCH", "/v1/chats/777", b.access, JSONObject().put("title", "Поход в горы")).code)
        assertEquals(204, call("PUT", "/v1/chats/777/members/${c.id}", b.access).code)
        assertEquals("Поход в горы", boxC.of("group").last().getString("title"))

        call("POST", "/v1/chats/777/messages", c.access, JSONObject().put("text", "Я с вами"))
        assertEquals(1, boxB.of("message").size)

        assertEquals(403, call("DELETE", "/v1/chats/777/members/${a.id}", b.access).code)
        assertEquals(204, call("DELETE", "/v1/chats/777/members/${c.id}", b.access).code)
        val last = boxC.of("group").last().getJSONArray("members")
        assertFalse((0 until last.length()).any { last.getInt(it) == c.id })
        assertEquals(403, call("GET", "/v1/chats/777/messages", c.access).code)
        // Уйти сам может любой
        assertEquals(204, call("DELETE", "/v1/chats/777/members/${b.id}", b.access).code)
        assertEquals(1, call("GET", "/v1/chats", a.access).json().getJSONArray("items").getJSONObject(0).getJSONArray("members").length())
    }

    @Test
    fun tusUploadThenPostAndDownload() {
        val a = register("anna")
        val b = register("boris")
        val opts = call("OPTIONS", "/v1/uploads")
        assertEquals("1.0.0", opts.headers["Tus-Version"])
        assertEquals(412, call("POST", "/v1/uploads", a.access, headers = mapOf("Upload-Length" to "10")).code)
        val tus = mapOf("Tus-Resumable" to "1.0.0")
        val meta = "filetype " + java.util.Base64.getEncoder().encodeToString("image/jpeg".toByteArray())
        val created = call("POST", "/v1/uploads", a.access, headers = tus + mapOf("Upload-Length" to "10", "Upload-Metadata" to meta))
        assertEquals(201, created.code)
        val loc = created.headers.getValue("Location")
        val patch = tus + ("Content-Type" to "application/offset+octet-stream")

        assertEquals(204, call("PATCH", loc, a.access, headers = patch + ("Upload-Offset" to "0"), raw = "01234".toByteArray()).code)
        assertEquals(409, call("PATCH", loc, a.access, headers = patch + ("Upload-Offset" to "0"), raw = "01234".toByteArray()).code)
        assertEquals("5", call("HEAD", loc, a.access).headers["Upload-Offset"])
        assertEquals(403, call("PATCH", loc, b.access, headers = patch + ("Upload-Offset" to "5"), raw = "56789".toByteArray()).code)

        val photo = JSONObject().put("id", "p1").put("kind", "Photo").put("path", "https://api.example.am$loc")
        val post = JSONObject().put("text", "Фото").put("media", JSONArray().put(photo))
        assertEquals(422, call("POST", "/v1/posts", a.access, post).code)
        assertEquals(409, call("GET", loc, b.access).code)

        assertEquals(204, call("PATCH", loc, a.access, headers = patch + ("Upload-Offset" to "5"), raw = "56789".toByteArray()).code)
        assertEquals(400, call("PATCH", loc, a.access, headers = patch + ("Upload-Offset" to "10"), raw = "x".toByteArray()).code)
        // Чужую загрузку к своей записи не приложить
        assertEquals(422, call("POST", "/v1/posts", b.access, post).code)
        val saved = call("POST", "/v1/posts", a.access, post)
        assertEquals(201, saved.code)
        assertEquals(loc, saved.json().getJSONArray("media").getJSONObject(0).getString("path"))

        val got = call("GET", loc, b.access)
        assertEquals(200, got.code)
        assertEquals("image/jpeg", got.contentType)
        assertEquals("0123456789", got.file!!.readText())

        // Гифки и стикеры — как есть
        val gif = JSONObject().put("id", "g").put("kind", "Photo").put("path", "https://media.example/x.gif")
        assertEquals(201, call("POST", "/v1/posts", a.access, JSONObject().put("text", "").put("media", JSONArray().put(gif))).code)

        assertEquals(403, call("DELETE", loc, b.access).code)
        assertEquals(204, call("DELETE", loc, a.access).code)
        assertEquals(404, call("HEAD", loc, a.access).code)
        assertEquals(413, call("POST", "/v1/uploads", a.access, headers = tus + ("Upload-Length" to (Core.MAX_UPLOAD + 1).toString())).code)
    }

    @Test
    fun passwordsAreNeverStoredPlain() {
        val store = MemoryStore()
        val core = Core(store, dir) { now }
        val r = core.handle(Req("POST", "/v1/auth/register", body = JSONObject().put("login", "anna").put("password", "secret1").toString().toByteArray()))
        val access = r.json().getString("access")
        val user = store.userByLogin("anna")!!
        assertFalse(user.passHash.contains("secret1"))
        assertTrue(user.passHash.startsWith("pbkdf2$"))
        val dev = store.devices(user.id).single()
        assertNotEquals(access, dev.accessHash)
        assertEquals(Crypto.sha256(access), dev.accessHash)
        assertNull(store.deviceByAccess(access))
    }

    @Test
    fun healthAndUnknown() {
        assertEquals(200, call("GET", "/health").code)
        val a = register("anna")
        assertEquals(404, call("GET", "/v1/nothing", a.access).code)
    }
}
