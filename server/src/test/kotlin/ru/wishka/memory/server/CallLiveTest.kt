package ru.wishka.memory.server

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Звонки и эфиры: токены LiveKit, сигналы по живому каналу, пуши, правила «кто может звонить и смотреть». */
class CallLiveTest {
    private var now = 1_700_000_000_000L
    private val store = MemoryStore()
    private val pushes = mutableListOf<Map<String, String>>()
    private val pusher = object : Pusher {
        override fun push(token: String, data: Map<String, String>): Boolean { pushes += data; return true }
    }
    /** Что сервер просил у LiveKit (закрыть комнату, выгнать человека). */
    private val admin = mutableListOf<String>()
    private val lk = object : LiveKit("wss://memory.livekit.cloud", "APIkey", "secret-secret-secret") {
        override fun deleteRoom(room: String) { admin += "delete $room" }
        override fun removeParticipant(room: String, identity: Int) { admin += "remove $room $identity" }
    }
    private val hub = Hub()
    private val core = Core(store, Files.createTempDirectory("calls").toFile(), hub, pusher, { it.run() }, liveKit = lk) { now }

    private class Inbox : Hub.Peer {
        val frames = mutableListOf<JSONObject>()
        override fun send(text: String) { frames += JSONObject(text) }
        fun of(type: String) = frames.filter { it.optString("type") == type }
    }

    private data class Who(val id: Int, val access: String, val inbox: Inbox)

    private fun call(method: String, path: String, token: String? = null, body: Any? = null, c: Core = core): Resp =
        c.handle(Req(method, path, emptyMap(), token?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap(), body?.toString()?.toByteArray()))

    private fun register(login: String): Who {
        val o = call("POST", "/v1/auth/register", body = JSONObject().put("login", login).put("password", "secret1").put("name", login)).json()
        val w = Who(o.getJSONObject("user").getInt("id"), o.getString("access"), Inbox())
        hub.join(w.id, w.inbox)
        call("PUT", "/v1/devices/current/push", w.access, JSONObject().put("token", "tok-$login"))
        return w
    }

    private fun privacy(w: Who, calls: String) =
        call("PUT", "/v1/me", w.access, JSONObject().put("name", "x").put("privacy", JSONObject().put("calls", calls)))

    /** Полезная нагрузка JWT — и проверка подписи секретом. */
    private fun claims(token: String): JSONObject {
        val (h, b, s) = token.split('.')
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec("secret-secret-secret".toByteArray(), "HmacSHA256")) }
        assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal("$h.$b".toByteArray())), s)
        assertEquals("HS256", JSONObject(String(Base64.getUrlDecoder().decode(h))).getString("alg"))
        return JSONObject(String(Base64.getUrlDecoder().decode(b)))
    }

    @Test
    fun callRingsAnswersEnds() {
        val a = register("anna"); val b = register("boris")
        val r = call("POST", "/v1/calls", a.access, JSONObject().put("to", b.id).put("video", true))
        assertEquals(201, r.code)
        val o = r.json()
        val id = o.getLong("id")
        assertEquals("wss://memory.livekit.cloud", o.getString("url"))
        assertEquals("call-$id", o.getString("room"))
        val cl = claims(o.getString("token"))
        assertEquals("APIkey", cl.getString("iss")); assertEquals(a.id.toString(), cl.getString("sub"))
        assertEquals("call-$id", cl.getJSONObject("video").getString("room"))
        assertTrue(cl.getJSONObject("video").getBoolean("canPublish"))
        assertTrue(cl.getLong("exp") > now / 1000)
        // У Бориса звонит: событие, пуш, список «кто звонит»
        val ring = b.inbox.of("call").single()
        assertEquals("ringing", ring.getString("state")); assertEquals(a.id, ring.getInt("from")); assertTrue(ring.getBoolean("video"))
        assertEquals("call", pushes.last()["type"]); assertEquals(id.toString(), pushes.last()["callId"])
        assertEquals(id, call("GET", "/v1/calls/ringing", b.access).json().getJSONArray("items").getJSONObject(0).getLong("id"))
        // Занято: третьему — 409
        val c = register("clara")
        assertEquals(409, call("POST", "/v1/calls", c.access, JSONObject().put("to", b.id)).code)
        // Принять может только тот, кому звонят
        assertEquals(403, call("POST", "/v1/calls/$id/accept", a.access).code)
        val acc = call("POST", "/v1/calls/$id/accept", b.access).json()
        assertEquals(b.id.toString(), claims(acc.getString("token")).getString("sub"))
        assertEquals("accepted", a.inbox.of("call").last().getString("state"))
        assertEquals(0, call("GET", "/v1/calls/ringing", b.access).json().getJSONArray("items").length())
        // Чужой звонок не виден
        assertEquals(404, call("POST", "/v1/calls/$id/end", c.access).code)
        assertEquals(204, call("POST", "/v1/calls/$id/end", b.access).code)
        assertEquals("ended", a.inbox.of("call").last().getString("state"))
        // Комната закрыта: оставшиеся в ней отключаются
        assertEquals("delete call-$id", admin.last())
        // Теперь свободен
        assertEquals(201, call("POST", "/v1/calls", c.access, JSONObject().put("to", b.id)).code)
    }

    @Test
    fun declineCancelAndTimeout() {
        val a = register("anna"); val b = register("boris")
        val id = call("POST", "/v1/calls", a.access, JSONObject().put("to", b.id)).json().getLong("id")
        assertEquals(204, call("POST", "/v1/calls/$id/decline", b.access).code)
        assertEquals("declined", a.inbox.of("call").last().getString("state"))
        // Отменил, не дождавшись, — у Бориса пропущенный (событие и пуш)
        val id2 = call("POST", "/v1/calls", a.access, JSONObject().put("to", b.id)).json().getLong("id")
        call("POST", "/v1/calls/$id2/end", a.access)
        assertEquals("cancelled", b.inbox.of("call").last().getString("state"))
        assertEquals("missed_call", pushes.last()["type"])
        // Никто не ответил — через 45 с принять уже нельзя
        val id3 = call("POST", "/v1/calls", a.access, JSONObject().put("to", b.id)).json().getLong("id")
        now += Core.RING_MS + 1
        assertEquals(0, call("GET", "/v1/calls/ringing", b.access).json().getJSONArray("items").length())
        assertEquals(410, call("POST", "/v1/calls/$id3/accept", b.access).code)
        // И Анна уже не «занята»
        assertEquals(201, call("POST", "/v1/calls", a.access, JSONObject().put("to", b.id)).code)
    }

    @Test
    fun whoMayCall() {
        val a = register("anna"); val b = register("boris")
        privacy(b, "Nobody")
        assertEquals(403, call("POST", "/v1/calls", a.access, JSONObject().put("to", b.id)).code)
        privacy(b, "Following")
        assertEquals(403, call("POST", "/v1/calls", a.access, JSONObject().put("to", b.id)).code)
        // Борис подписан на Анну — ей можно
        call("PUT", "/v1/users/${a.id}/follow", b.access)
        assertEquals(201, call("POST", "/v1/calls", a.access, JSONObject().put("to", b.id)).code)
        val id = call("GET", "/v1/calls/ringing", b.access).json().getJSONArray("items").getJSONObject(0).getLong("id")
        call("POST", "/v1/calls/$id/decline", b.access)
        // Заблокировал — нельзя
        privacy(b, "Everyone")
        call("PUT", "/v1/users/${a.id}/block", b.access)
        assertEquals(403, call("POST", "/v1/calls", a.access, JSONObject().put("to", b.id)).code)
        assertEquals(404, call("POST", "/v1/calls", a.access, JSONObject().put("to", a.id)).code)
    }

    @Test
    fun withoutLiveKitCallsAreOff() {
        val off = Core(store, Files.createTempDirectory("off").toFile(), Hub(), NoPush, { it.run() }) { now }
        val a = call("POST", "/v1/auth/register", body = JSONObject().put("login", "anna").put("password", "secret1"), c = off).json()
        val b = call("POST", "/v1/auth/register", body = JSONObject().put("login", "boris").put("password", "secret1"), c = off).json()
        assertEquals(503, call("POST", "/v1/calls", a.getString("access"), JSONObject().put("to", b.getJSONObject("user").getInt("id")), c = off).code)
        assertEquals(503, call("POST", "/v1/lives", a.getString("access"), JSONObject().put("title", "x"), c = off).code)
    }

    @Test
    fun livesForFollowersAndViewers() {
        val host = register("anna"); val fan = register("boris"); val stranger = register("clara")
        call("PUT", "/v1/users/${host.id}/follow", fan.access)
        assertEquals(400, call("POST", "/v1/lives", host.access, JSONObject().put("title", " ")).code)
        val started = call("POST", "/v1/lives", host.access, JSONObject().put("title", "Закат").put("friendsOnly", true))
        assertEquals(201, started.code)
        val id = started.json().getLong("id")
        assertTrue(claims(started.json().getString("token")).getJSONObject("video").getBoolean("canPublish"))
        // Подписчику — событие и пуш; «для друзей» — чужой не видит
        assertEquals("started", fan.inbox.of("live").single().getString("state"))
        assertEquals("live", pushes.last()["type"])
        assertEquals(listOf(id), call("GET", "/v1/lives", fan.access).json().getJSONArray("items").let { a -> (0 until a.length()).map { a.getJSONObject(it).getLong("id") } })
        assertEquals(0, call("GET", "/v1/lives", stranger.access).json().getJSONArray("items").length())
        assertEquals(404, call("POST", "/v1/lives/$id/join", stranger.access).code)
        // Зритель — только смотрит
        val join = call("POST", "/v1/lives/$id/join", fan.access).json()
        val v = claims(join.getString("token")).getJSONObject("video")
        assertFalse(v.getBoolean("canPublish")); assertTrue(v.getBoolean("canSubscribe")); assertEquals("live-$id", v.getString("room"))
        assertEquals(1, host.inbox.of("live").last().getInt("viewers"))
        // Комментарии и сердечки — всем в эфире
        call("POST", "/v1/lives/$id/heart", fan.access)
        assertEquals(1, host.inbox.of("live").last().getInt("hearts"))
        assertEquals(201, call("POST", "/v1/lives/$id/comments", fan.access, JSONObject().put("text", "Красота")).code)
        assertEquals("Красота", host.inbox.of("live_comment").single().getJSONObject("comment").getString("text"))
        // Ведущий заблокировал зрителя — его комментарии больше не видит, а сам эфир зрителю не виден
        call("PUT", "/v1/users/${fan.id}/block", host.access)
        assertEquals("remove live-$id ${fan.id}", admin.last())
        assertEquals(404, call("POST", "/v1/lives/$id/comments", fan.access, JSONObject().put("text", "эй")).code)
        call("DELETE", "/v1/users/${fan.id}/block", host.access)
        call("PUT", "/v1/users/${host.id}/follow", fan.access)
        call("POST", "/v1/lives/$id/join", fan.access)
        // Конец эфира — зрителям и подписчикам
        assertEquals(404, call("DELETE", "/v1/lives/$id", fan.access).code)
        assertEquals(204, call("DELETE", "/v1/lives/$id", host.access).code)
        assertEquals("delete live-$id", admin.last())
        assertEquals("ended", fan.inbox.of("live").last().getString("state"))
        assertEquals(0, call("GET", "/v1/lives", fan.access).json().getJSONArray("items").length())
    }

    @Test
    fun forgottenLiveEnds() {
        val host = register("anna"); val fan = register("boris")
        call("POST", "/v1/lives", host.access, JSONObject().put("title", "Ночь"))
        assertEquals(1, call("GET", "/v1/lives", fan.access).json().getJSONArray("items").length())
        now += Core.LIVE_MAX_MS + 1
        val fresh = call("POST", "/v1/auth/login", body = JSONObject().put("login", "boris").put("password", "secret1")).json().getString("access")
        assertEquals(0, call("GET", "/v1/lives", fresh).json().getJSONArray("items").length())
    }

    @Test
    fun droppedCallFreesBothAfterSilence() {
        val a = register("anna"); val b = register("boris"); val c = register("clara")
        val id = call("POST", "/v1/calls", a.access, JSONObject().put("to", b.id)).json().getLong("id")
        call("POST", "/v1/calls/$id/accept", b.access)
        // Разговор идёт, телефоны отмечаются — занято
        now += 60_000
        assertEquals(204, call("POST", "/v1/calls/$id/alive", a.access).code)
        now += 60_000
        assertEquals(409, call("POST", "/v1/calls", c.access, JSONObject().put("to", b.id)).code)
        // Телефоны пропали (сел, убили приложение) — через полторы минуты свободны
        now += Core.CALL_ALIVE_MS + 1
        val fresh = call("POST", "/v1/auth/login", body = JSONObject().put("login", "clara").put("password", "secret1")).json().getString("access")
        assertEquals(201, call("POST", "/v1/calls", fresh, JSONObject().put("to", b.id)).code)
    }

    @Test
    fun heartsAndCommentsOnlyFromInside() {
        val host = register("anna"); val fan = register("boris")
        val id = call("POST", "/v1/lives", host.access, JSONObject().put("title", "Ночь")).json().getLong("id")
        assertEquals(403, call("POST", "/v1/lives/$id/heart", fan.access).code)
        assertEquals(403, call("POST", "/v1/lives/$id/comments", fan.access, JSONObject().put("text", "эй")).code)
        call("POST", "/v1/lives/$id/join", fan.access)
        assertEquals(201, call("POST", "/v1/lives/$id/comments", fan.access, JSONObject().put("text", "раз")).code)
        // Чаще раза в секунду — нет
        assertEquals(429, call("POST", "/v1/lives/$id/comments", fan.access, JSONObject().put("text", "два")).code)
        now += 1000
        assertEquals(201, call("POST", "/v1/lives/$id/comments", fan.access, JSONObject().put("text", "три")).code)
    }

    @Test
    fun banEndsLivesAndCalls() {
        val salt = Crypto.newSalt()
        val modCore = Core(store, Files.createTempDirectory("mod").toFile(), hub, pusher, { it.run() }, admins = setOf("moder"), liveKit = lk) { now }
        store.createUser("moder", "M", 0.5f, Crypto.hashPassword("secret1", salt), salt, now)
        val mTok = call("POST", "/v1/auth/login", body = JSONObject().put("login", "moder").put("password", "secret1"), c = modCore).json().getString("access")
        val a = register("anna"); val b = register("boris")
        val live = call("POST", "/v1/lives", a.access, JSONObject().put("title", "x"), c = modCore).json().getLong("id")
        val callId = call("POST", "/v1/calls", a.access, JSONObject().put("to", b.id), c = modCore).json().getLong("id")
        call("POST", "/v1/reports", b.access, JSONObject().put("kind", "person").put("target", a.id).put("reason", "spam"), c = modCore)
        val rep = call("GET", "/v1/admin/reports", mTok, c = modCore).json().getJSONArray("items").getJSONObject(0).getLong("id")
        call("POST", "/v1/admin/reports/$rep", mTok, JSONObject().put("action", "ban"), c = modCore)
        assertTrue("delete live-$live" in admin)
        assertTrue("delete call-$callId" in admin)
        assertEquals("ended", b.inbox.of("call").last().getString("state"))
    }

    @Test
    fun unfriendedLeavesFriendsOnlyLive() {
        val host = register("anna"); val fan = register("boris")
        call("PUT", "/v1/users/${host.id}/follow", fan.access)
        val id = call("POST", "/v1/lives", host.access, JSONObject().put("title", "Свои").put("friendsOnly", true)).json().getLong("id")
        call("POST", "/v1/lives/$id/join", fan.access)
        call("DELETE", "/v1/me/followers/${fan.id}", host.access)
        assertEquals("remove live-$id ${fan.id}", admin.last())
        assertEquals(404, call("POST", "/v1/lives/$id/join", fan.access).code)
    }
}
