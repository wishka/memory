package ru.wishka.memory.server

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

/** Подписки, «Интересное», комментарии, истории, пуши. PgSocialTest — то же на PostgreSQL. */
open class SocialTest {
    private var now = 1_700_000_000_000L
    private val dir = Files.createTempDirectory("memory-social").toFile()
    protected open fun newStore(): Store = MemoryStore()

    /** Пуши в тесте: кому что ушло; токен "dead" — как будто приложение удалили. */
    private class FakePusher : Pusher {
        val sent = mutableListOf<Pair<String, Map<String, String>>>()
        override fun push(token: String, data: Map<String, String>): Boolean {
            sent += token to data
            return token != "dead"
        }
    }

    private val pusher = FakePusher()
    private val store by lazy { newStore() }
    private val core by lazy { Core(store, dir, Hub(), pusher, { it.run() }) { now } }

    private class Inbox : Hub.Peer {
        val frames = mutableListOf<JSONObject>()
        override fun send(text: String) { frames += JSONObject(text) }
        fun of(type: String) = frames.filter { it.optString("type") == type }
    }

    private fun call(method: String, path: String, token: String? = null, body: Any? = null, query: Map<String, String> = emptyMap()): Resp {
        val h = HashMap<String, String>()
        if (token != null) h["Authorization"] = "Bearer $token"
        return core.handle(Req(method, path, query, h, body?.toString()?.toByteArray()))
    }

    private data class Who(val id: Int, val access: String)

    private fun register(login: String): Who {
        val o = call("POST", "/v1/auth/register", body = JSONObject().put("login", login).put("password", "secret1").put("name", login.replaceFirstChar { it.uppercase() })).json()
        return Who(o.getJSONObject("user").getInt("id"), o.getString("access"))
    }

    private fun closeProfile(w: Who, closed: Boolean = true) =
        call("PUT", "/v1/me", w.access, JSONObject().put("name", "x").put("privacy", JSONObject().put("closedProfile", closed)))

    private fun items(r: Resp): JSONArray = r.json().getJSONArray("items")

    private fun post(w: Who, text: String): Long {
        now += 1000
        return call("POST", "/v1/posts", w.access, JSONObject().put("text", text)).json().getLong("id")
    }

    @Test
    fun followOpenAndClosedProfiles() {
        val a = register("anna")
        val b = register("boris")
        val c = register("clara")
        val boxA = Inbox().also { core.hub.join(a.id, it) }
        val boxC = Inbox().also { core.hub.join(c.id, it) }
        post(a, "Анна")
        post(c, "Клара")
        closeProfile(c)

        // Открытый профиль — сразу подписка
        assertEquals("following", call("PUT", "/v1/users/${a.id}/follow", b.access).json().getString("state"))
        assertEquals("follow", boxA.of("follow").single().getString("state"))
        assertEquals(1, items(call("GET", "/v1/feed", b.access)).length())
        assertEquals("following", call("GET", "/v1/users/${a.id}", b.access).json().getString("followState"))
        assertEquals(1, call("GET", "/v1/users/${a.id}", b.access).json().getInt("followers"))

        // Закрытый — заявка; записи и «Интересное» закрыты
        assertEquals("requested", call("PUT", "/v1/users/${c.id}/follow", b.access).json().getString("state"))
        assertEquals("request", boxC.of("follow").single().getString("state"))
        assertEquals(403, call("GET", "/v1/users/${c.id}/posts", b.access).code)
        assertEquals(0, items(call("GET", "/v1/explore", b.access)).length())
        assertEquals(b.id, items(call("GET", "/v1/me/requests", c.access)).getJSONObject(0).getInt("id"))

        // Приняла — Борис узнаёт, лента открывается
        val boxB = Inbox().also { core.hub.join(b.id, it) }
        assertEquals(204, call("POST", "/v1/me/requests/${b.id}/accept", c.access).code)
        assertEquals("accepted", boxB.of("follow").single().getString("state"))
        assertEquals(2, items(call("GET", "/v1/feed", b.access)).length())
        assertEquals(200, call("GET", "/v1/users/${c.id}/posts", b.access).code)
        assertEquals(listOf(a.id, c.id), items(call("GET", "/v1/me/following", b.access)).let { arr -> (0 until arr.length()).map { arr.getJSONObject(it).getInt("id") } })

        // Убрать подписчика, отписаться
        assertEquals(204, call("DELETE", "/v1/me/followers/${b.id}", c.access).code)
        assertEquals(403, call("GET", "/v1/users/${c.id}/posts", b.access).code)
        assertEquals("none", call("DELETE", "/v1/users/${a.id}/follow", b.access).json().getString("state"))
        assertEquals(0, items(call("GET", "/v1/feed", b.access)).length())
        assertEquals(400, call("PUT", "/v1/users/${b.id}/follow", b.access).code)
    }

    @Test
    fun openingProfileAcceptsRequests() {
        val b = register("boris")
        val c = register("clara")
        closeProfile(c)
        call("PUT", "/v1/users/${c.id}/follow", b.access)
        val d = register("dina")
        call("PUT", "/v1/users/${c.id}/follow", d.access)
        call("DELETE", "/v1/me/requests/${d.id}", c.access)
        assertEquals("none", call("GET", "/v1/users/${c.id}", d.access).json().getString("followState"))
        closeProfile(c, closed = false)
        assertEquals("following", call("GET", "/v1/users/${c.id}", b.access).json().getString("followState"))
        assertEquals(0, items(call("GET", "/v1/me/requests", c.access)).length())
    }

    @Test
    fun acceptingRequestMakesMutualFriends() {
        val a = register("anna")
        val b = register("boris")
        closeProfile(a)
        closeProfile(b)
        // Анна просит Бориса; Борис принимает и подписывается в ответ — у закрытой Анны это не новая заявка
        assertEquals("requested", call("PUT", "/v1/users/${b.id}/follow", a.access).json().getString("state"))
        call("POST", "/v1/me/requests/${a.id}/accept", b.access)
        assertEquals("following", call("PUT", "/v1/users/${a.id}/follow", b.access).json().getString("state"))
        assertEquals(0, items(call("GET", "/v1/me/requests", a.access)).length())
        assertEquals(200, call("GET", "/v1/users/${a.id}/posts", b.access).code)
        // Чужому закрытому — комментарий не лайкнуть
        val postId = post(a, "Закрытое")
        val cid = call("POST", "/v1/posts/$postId/comments", b.access, JSONObject().put("text", "Ок")).json().getLong("id")
        assertTrue(cid >= 10_000_001)
        val d = register("dina")
        assertEquals(403, call("PUT", "/v1/comments/$cid/like", d.access).code)
    }

    @Test
    fun commentsWithRightsAndCounts() {
        val a = register("anna")
        val b = register("boris")
        val boxA = Inbox().also { core.hub.join(a.id, it) }
        val postId = post(a, "Горы")
        call("PUT", "/v1/users/${a.id}/follow", b.access)

        val c1 = call("POST", "/v1/posts/$postId/comments", b.access, JSONObject().put("text", "Красиво!"))
        assertEquals(201, c1.code)
        assertEquals(b.id, c1.json().getInt("author"))
        assertEquals("Boris", c1.json().getJSONObject("authorInfo").getString("name"))
        assertEquals("Красиво!", boxA.of("comment").single().getJSONObject("comment").getString("text"))
        call("POST", "/v1/posts/$postId/comments", a.access, JSONObject().put("text", "Спасибо").put("replyTo", "Boris"))
        call("POST", "/v1/posts/$postId/comments", b.access, JSONObject().put("text", "Где это?"))
        assertEquals(400, call("POST", "/v1/posts/$postId/comments", b.access, JSONObject().put("text", "  ")).code)

        // Под записью в ленте — счётчик и два последних
        val inFeed = items(call("GET", "/v1/feed", b.access)).getJSONObject(0)
        assertEquals(3, inFeed.getInt("comments"))
        assertEquals(listOf("Спасибо", "Где это?"), inFeed.getJSONArray("commentList").let { arr -> (0 until arr.length()).map { arr.getJSONObject(it).getString("text") } })

        // Все — страницами, по порядку
        val p1 = call("GET", "/v1/posts/$postId/comments", b.access, query = mapOf("limit" to "2")).json()
        assertEquals("Красиво!", p1.getJSONArray("items").getJSONObject(0).getString("text"))
        val p2 = call("GET", "/v1/posts/$postId/comments", b.access, query = mapOf("limit" to "2", "cursor" to p1.getString("next"))).json()
        assertEquals("Где это?", p2.getJSONArray("items").getJSONObject(0).getString("text"))

        // «Нравится» комментарию
        val cid = c1.json().getLong("id")
        call("PUT", "/v1/comments/$cid/like", a.access)
        val first = items(call("GET", "/v1/posts/$postId/comments", a.access)).getJSONObject(0)
        assertEquals(1, first.getInt("likes"))
        assertTrue(first.getBoolean("liked"))

        // Удалить: свой — можно, чужой — только автор записи
        val c3 = items(call("GET", "/v1/posts/$postId/comments", b.access)).getJSONObject(2).getLong("id")
        val stranger = register("clara")
        assertEquals(403, call("DELETE", "/v1/comments/$c3", stranger.access).code)
        assertEquals(204, call("DELETE", "/v1/comments/$c3", a.access).code)
        assertEquals(204, call("DELETE", "/v1/comments/$cid", b.access).code)
        assertEquals(1, items(call("GET", "/v1/posts/$postId/comments", b.access)).length())

        // Выключенные комментарии — только автору
        call("PATCH", "/v1/posts/$postId", a.access, JSONObject().put("commentsOff", true))
        assertEquals(403, call("POST", "/v1/posts/$postId/comments", b.access, JSONObject().put("text", "?")).code)
        assertEquals(201, call("POST", "/v1/posts/$postId/comments", a.access, JSONObject().put("text", "Закрываю")).code)

        // Закрытый профиль: посторонний не комментирует
        closeProfile(a)
        assertEquals(403, call("POST", "/v1/posts/$postId/comments", stranger.access, JSONObject().put("text", "?")).code)
        assertEquals(403, call("GET", "/v1/posts/$postId/comments", stranger.access).code)
    }

    @Test
    fun storiesForFollowersAndCloseFriends() {
        val a = register("anna")
        val b = register("boris")
        val c = register("clara")
        call("PUT", "/v1/users/${a.id}/follow", b.access)
        call("PUT", "/v1/users/${a.id}/follow", c.access)
        assertEquals(400, call("POST", "/v1/stories", a.access, JSONObject().put("text", " ")).code)
        val s1 = call("POST", "/v1/stories", a.access, JSONObject().put("text", "Утро").put("bg", 2)).json()
        assertEquals(a.id, s1.getInt("author"))
        assertEquals(2, s1.getInt("bg"))
        now += 1000
        call("PUT", "/v1/me/close-friends", a.access, JSONObject().put("ids", JSONArray(listOf(b.id))))
        call("POST", "/v1/stories", a.access, JSONObject().put("text", "Для своих").put("close", true))

        assertEquals(2, items(call("GET", "/v1/stories", b.access)).length())
        assertEquals(1, items(call("GET", "/v1/stories", c.access)).length())
        // Не подписан — не видно
        val d = register("dina")
        assertEquals(0, items(call("GET", "/v1/stories", d.access)).length())
        assertEquals(403, call("POST", "/v1/stories/${s1.getLong("id")}/view", d.access).code)

        // Просмотр: зрителю — «просмотрено», автору — кто смотрел
        call("POST", "/v1/stories/${s1.getLong("id")}/view", b.access, JSONObject().put("liked", true))
        assertTrue(items(call("GET", "/v1/stories", b.access)).getJSONObject(0).getBoolean("seen"))
        assertEquals(0, items(call("GET", "/v1/stories", b.access)).getJSONObject(0).getJSONArray("views").length())
        val own = items(call("GET", "/v1/stories", a.access)).getJSONObject(0)
        assertEquals(b.id, own.getJSONArray("views").getJSONObject(0).getInt("user"))
        assertTrue(own.getJSONArray("views").getJSONObject(0).getBoolean("liked"))

        // Удалить может только автор; через сутки исчезают сами
        assertEquals(403, call("DELETE", "/v1/stories/${s1.getLong("id")}", b.access).code)
        assertEquals(204, call("DELETE", "/v1/stories/${s1.getLong("id")}", a.access).code)
        assertEquals(1, items(call("GET", "/v1/stories", b.access)).length())
        now += Core.STORY_TTL
        // Токен доступа за сутки истёк — войти заново
        val again = call("POST", "/v1/auth/login", body = JSONObject().put("login", "boris").put("password", "secret1")).json().getString("access")
        assertEquals(0, items(call("GET", "/v1/stories", again)).length())
    }

    @Test
    fun pushesGoToOfflinePhones() {
        val a = register("anna")
        val b = register("boris")
        assertEquals(204, call("PUT", "/v1/devices/current/push", b.access, JSONObject().put("token", "tok-b")).code)
        val chat = call("POST", "/v1/chats/direct", a.access, JSONObject().put("user", b.id)).json().getInt("id")

        call("POST", "/v1/chats/$chat/messages", a.access, JSONObject().put("text", "Привет"))
        val (token, data) = pusher.sent.single()
        assertEquals("tok-b", token)
        assertEquals("message", data["type"])
        assertEquals("Привет", data["body"])
        assertEquals(chat.toString(), data["chatId"])
        assertEquals("Anna", data["actor"])
        assertEquals(a.id.toString(), data["actorId"])

        // Секретное — без текста
        call("POST", "/v1/chats/$chat/messages", a.access, JSONObject().put("text", "AAAA").put("secret", true))
        assertEquals("", pusher.sent.last().second["body"])

        // На связи по живому каналу — пуш не нужен
        core.hub.join(b.id, Inbox())
        call("POST", "/v1/chats/$chat/messages", a.access, JSONObject().put("text", "Ещё"))
        assertEquals(2, pusher.sent.size)

        // Подписка и комментарий — тоже пуши
        call("PUT", "/v1/devices/current/push", a.access, JSONObject().put("token", "tok-a"))
        call("PUT", "/v1/users/${a.id}/follow", b.access)
        assertEquals("new_follower", pusher.sent.last().second["type"])
        val postId = post(a, "Запись")
        call("POST", "/v1/posts/$postId/comments", b.access, JSONObject().put("text", "Класс"))
        assertEquals("comment", pusher.sent.last().second["type"])
        assertEquals(postId.toString(), pusher.sent.last().second["postId"])

        // Приложение удалили — токен забывается, больше не шлём
        call("PUT", "/v1/devices/current/push", a.access, JSONObject().put("token", "dead"))
        call("POST", "/v1/posts/$postId/comments", b.access, JSONObject().put("text", "Ау"))
        val before = pusher.sent.size
        call("POST", "/v1/posts/$postId/comments", b.access, JSONObject().put("text", "Ау-ау"))
        assertEquals(before, pusher.sent.size)
        assertTrue(store.devices(a.id).all { it.pushToken.isEmpty() })
    }

    @Test
    fun fcmAssertionIsSignedJwt() {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder().encodeToString(kp.private.encoded) + "\n-----END PRIVATE KEY-----\n"
        val creds = JSONObject().put("client_email", "push@memory.iam.gserviceaccount.com").put("project_id", "memory-1")
            .put("token_uri", "https://oauth2.googleapis.com/token").put("private_key", pem).toString()
        val jwt = FcmPusher(creds, clock = { now }).assertion()
        val parts = jwt.split('.')
        assertEquals(3, parts.size)
        val dec = Base64.getUrlDecoder()
        val header = JSONObject(String(dec.decode(parts[0])))
        val claims = JSONObject(String(dec.decode(parts[1])))
        assertEquals("RS256", header.getString("alg"))
        assertEquals("push@memory.iam.gserviceaccount.com", claims.getString("iss"))
        assertEquals(FcmPusher.SCOPE, claims.getString("scope"))
        assertEquals(now / 1000 + 3600, claims.getLong("exp"))
        val ok = Signature.getInstance("SHA256withRSA").apply {
            initVerify(kp.public)
            update((parts[0] + "." + parts[1]).toByteArray())
        }.verify(dec.decode(parts[2]))
        assertTrue(ok)
        assertFalse(jwt.contains("="))
    }
}
