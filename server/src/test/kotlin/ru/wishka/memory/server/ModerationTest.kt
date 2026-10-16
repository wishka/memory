package ru.wishka.memory.server

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** Блокировки, жалобы, модерация (/admin), бан, удаление аккаунта. */
class ModerationTest {
    private var now = 1_700_000_000_000L
    private val store = MemoryStore()
    private val core = Core(store, Files.createTempDirectory("memory-mod").toFile(), Hub(), NoPush, { it.run() }, admins = setOf("moder")) { now }

    private fun call(method: String, path: String, token: String? = null, body: Any? = null, query: Map<String, String> = emptyMap()): Resp {
        val h = HashMap<String, String>()
        if (token != null) h["Authorization"] = "Bearer $token"
        return core.handle(Req(method, path, query, h, body?.toString()?.toByteArray()))
    }

    private data class Who(val id: Int, val access: String)

    private fun register(login: String): Who {
        val o = call("POST", "/v1/auth/register", body = JSONObject().put("login", login).put("password", "secret1").put("name", login)).json()
        return Who(o.getJSONObject("user").getInt("id"), o.getString("access"))
    }

    private fun items(r: Resp): JSONArray = r.json().getJSONArray("items")

    /** Модератор заводится заранее (зарегистрироваться логином из ADMINS нельзя). */
    private fun moderator(): Who {
        val salt = Crypto.newSalt()
        store.createUser("moder", "Модератор", 0.5f, Crypto.hashPassword("secret1", salt), salt, now)
        val o = call("POST", "/v1/auth/login", body = JSONObject().put("login", "moder").put("password", "secret1")).json()
        return Who(o.getJSONObject("user").getInt("id"), o.getString("access"))
    }

    private class Inbox : Hub.Peer {
        val frames = mutableListOf<JSONObject>()
        var closed = false
        override fun send(text: String) { frames += JSONObject(text) }
        override fun close() { closed = true }
    }

    @Test
    fun blockHidesBothWays() {
        val a = register("anna")
        val b = register("boris")
        call("PUT", "/v1/users/${a.id}/follow", b.access)
        call("PUT", "/v1/users/${b.id}/follow", a.access)
        now += 1000
        val post = call("POST", "/v1/posts", a.access, JSONObject().put("text", "Привет")).json().getLong("id")
        val chat = call("POST", "/v1/chats/direct", b.access, JSONObject().put("user", a.id)).json().getInt("id")

        assertEquals(204, call("PUT", "/v1/users/${b.id}/block", a.access).code)
        assertEquals(listOf(b.id), call("GET", "/v1/me/blocked", a.access).json().getJSONArray("ids").let { arr -> (0 until arr.length()).map { arr.getInt(it) } })
        // Подписки сняты, ничего не видно, написать нельзя
        assertEquals(0, items(call("GET", "/v1/feed", b.access)).length())
        assertEquals(403, call("GET", "/v1/users/${a.id}/posts", b.access).code)
        assertEquals(403, call("POST", "/v1/posts/$post/comments", b.access, JSONObject().put("text", "?")).code)
        assertEquals(403, call("PUT", "/v1/users/${a.id}/follow", b.access).code)
        assertEquals(403, call("POST", "/v1/chats/$chat/messages", b.access, JSONObject().put("text", "Ау")).code)
        assertEquals(403, call("POST", "/v1/chats/direct", b.access, JSONObject().put("user", a.id)).code)
        assertEquals(0, items(call("GET", "/v1/users", b.access, query = mapOf("q" to "ann"))).length())
        assertEquals(0, items(call("GET", "/v1/explore", b.access)).length())
        // Через группу не обойти: в новую не добавить, в свою — тоже
        val g = call("POST", "/v1/chats", b.access, JSONObject().put("id", 555).put("title", "Обход").put("members", JSONArray(listOf(a.id)))).json()
        assertEquals(1, g.getJSONArray("members").length())
        assertEquals(403, call("PUT", "/v1/chats/555/members/${a.id}", b.access).code)
        // Профиль не открыть
        assertEquals(404, call("GET", "/v1/users/${a.id}", b.access).code)
        // Разблокировала — снова можно (подписка — заново)
        call("DELETE", "/v1/users/${b.id}/block", a.access)
        assertEquals(201, call("POST", "/v1/chats/$chat/messages", b.access, JSONObject().put("text", "Ау")).code)
        assertEquals(1, items(call("GET", "/v1/explore", b.access)).length())
    }

    @Test
    fun reportsGoToModeratorWhoCanRemoveAndBan() {
        val a = register("anna")
        val b = register("boris")
        // Логин модератора занять нельзя
        assertEquals(409, call("POST", "/v1/auth/register", body = JSONObject().put("login", "moder").put("password", "secret1")).code)
        val moder = moderator()
        now += 1000
        val post = call("POST", "/v1/posts", b.access, JSONObject().put("text", "Плохая запись")).json().getLong("id")
        val second = call("POST", "/v1/posts", b.access, JSONObject().put("text", "Ещё одна")).json().getLong("id")

        // Автора сервер берёт сам, не со слов жалобщика
        val r1 = call("POST", "/v1/reports", a.access, JSONObject().put("kind", "post").put("target", post).put("author", a.id).put("reason", "abuse").put("note", "грубо"))
        assertEquals(201, r1.code)
        assertEquals(400, call("POST", "/v1/reports", a.access, JSONObject().put("kind", "что-то")).code)
        // Сообщение: сервер находит его в переписке и берёт автора и текст сам
        val chat = call("POST", "/v1/chats/direct", b.access, JSONObject().put("user", a.id)).json().getInt("id")
        val sent = call("POST", "/v1/chats/$chat/messages", b.access, JSONObject().put("text", "Купи подписку")).json()
        call("POST", "/v1/reports", a.access, JSONObject().put("kind", "message").put("target", 77).put("author", b.id).put("reason", "spam")
            .put("preview", "подделка").put("chat", chat).put("sentAt", sent.getLong("sentAt")))
        // Не подтвердить (PRISM) — автор «со слов», заблокировать по такой нельзя
        call("POST", "/v1/reports", a.access, JSONObject().put("kind", "prism").put("target", 5).put("author", moder.id).put("reason", "spam"))

        // Не модератор — нельзя
        assertEquals(403, call("GET", "/v1/admin/reports", a.access).code)
        val list = items(call("GET", "/v1/admin/reports", moder.access))
        assertEquals(3, list.length())
        val prism = (0 until list.length()).map { list.getJSONObject(it) }.first { it.getString("kind") == "prism" }
        assertEquals(false, prism.getBoolean("verified"))
        assertEquals(400, call("POST", "/v1/admin/reports/${prism.getLong("id")}", moder.access, JSONObject().put("action", "ban")).code)
        call("POST", "/v1/admin/reports/${prism.getLong("id")}", moder.access, JSONObject().put("action", "dismiss"))
        val msgReport = (0 until list.length()).map { list.getJSONObject(it) }.first { it.getString("kind") == "message" }
        assertEquals("Купи подписку", msgReport.getString("preview"))
        assertEquals(true, msgReport.getBoolean("verified"))
        val first = (0 until list.length()).map { list.getJSONObject(it) }.first { it.getString("kind") == "post" }
        assertEquals(b.id, first.getJSONObject("author").getInt("id"))
        assertEquals("Плохая запись", first.getString("current"))
        assertEquals("anna", first.getString("reporter"))

        // Удалить запись
        assertEquals(204, call("POST", "/v1/admin/reports/${first.getLong("id")}", moder.access, JSONObject().put("action", "remove")).code)
        assertNull(store.post(post))
        assertEquals(1, items(call("GET", "/v1/admin/reports", moder.access)).length())
        assertEquals(3, items(call("GET", "/v1/admin/reports", moder.access, query = mapOf("status" to "all"))).length())

        // Заблокировать автора: войти не может, токены не действуют, живой канал закрыт, профиля не видно
        val box = Inbox().also { core.hub.join(b.id, it) }
        val msg = items(call("GET", "/v1/admin/reports", moder.access)).getJSONObject(0).getLong("id")
        assertEquals(204, call("POST", "/v1/admin/reports/$msg", moder.access, JSONObject().put("action", "ban")).code)
        assertEquals(401, call("GET", "/v1/feed", b.access).code)
        assertTrue(box.closed)
        assertEquals(403, call("POST", "/v1/auth/login", body = JSONObject().put("login", "boris").put("password", "secret1")).code)
        assertEquals(404, call("GET", "/v1/users/${b.id}", a.access).code)
        assertEquals(0, items(call("GET", "/v1/explore", a.access)).length())
        assertTrue(store.post(second) != null) // записи остаются, но их не видно
        assertEquals(0, items(call("GET", "/v1/admin/reports", moder.access)).length())

        // Страница модерации отдаётся без входа (данные — только через API)
        val page = call("GET", "/admin")
        assertEquals(200, page.code)
        assertTrue(page.contentType!!.startsWith("text/html"))
        assertTrue(page.text.contains("/v1/admin/reports"))
    }

    @Test
    fun changePasswordLogsOutOtherDevices() {
        val a = register("anna")
        val other = call("POST", "/v1/auth/login", body = JSONObject().put("login", "anna").put("password", "secret1")).json().getString("access")
        assertEquals(403, call("PUT", "/v1/me/password", a.access, JSONObject().put("current", "x").put("new", "newpass1")).code)
        assertEquals(400, call("PUT", "/v1/me/password", a.access, JSONObject().put("current", "secret1").put("new", "123")).code)
        assertEquals(204, call("PUT", "/v1/me/password", a.access, JSONObject().put("current", "secret1").put("new", "newpass1")).code)
        assertEquals(200, call("GET", "/v1/feed", a.access).code)
        assertEquals(401, call("GET", "/v1/feed", other).code)
        assertEquals(401, call("POST", "/v1/auth/login", body = JSONObject().put("login", "anna").put("password", "secret1")).code)
        assertEquals(200, call("POST", "/v1/auth/login", body = JSONObject().put("login", "anna").put("password", "newpass1")).code)
    }

    @Test
    fun deleteAccountRemovesEverything() {
        val a = register("anna")
        val b = register("boris")
        call("PUT", "/v1/users/${b.id}/follow", a.access)
        now += 1000
        val post = call("POST", "/v1/posts", a.access, JSONObject().put("text", "Моё")).json().getLong("id")
        call("POST", "/v1/stories", a.access, JSONObject().put("text", "История"))
        val tus = mapOf("Tus-Resumable" to "1.0.0", "Upload-Length" to "3", "Authorization" to "Bearer ${a.access}")
        val loc = core.handle(Req("POST", "/v1/uploads", headers = tus)).headers.getValue("Location")

        assertEquals(403, call("DELETE", "/v1/me", a.access, JSONObject().put("password", "wrong")).code)
        // Своя группа с Борисом — после удаления достанется ему; лайк Анны с записи Бориса уйдёт
        call("POST", "/v1/chats", a.access, JSONObject().put("id", 777).put("title", "Поход").put("members", JSONArray(listOf(b.id))))
        val bPost = call("POST", "/v1/posts", b.access, JSONObject().put("text", "Борис")).json().getLong("id")
        call("PUT", "/v1/posts/$bPost/like", a.access)
        assertEquals(204, call("DELETE", "/v1/me", a.access, JSONObject().put("password", "secret1")).code)
        assertEquals(b.id, store.chat(777)?.owner)
        assertEquals(0, store.post(bPost)?.likes)
        assertNull(store.userByLogin("anna"))
        assertNull(store.post(post))
        assertTrue(store.devices(a.id).isEmpty())
        assertTrue(store.followers(b.id).isEmpty())
        assertNull(store.upload(loc.substringAfterLast('/')))
        assertEquals(401, call("GET", "/v1/feed", a.access).code)
        // Логин снова свободен
        assertEquals(201, call("POST", "/v1/auth/register", body = JSONObject().put("login", "anna").put("password", "secret1")).code)
    }
}
