package ru.wishka.memory.server

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** Сообщества, PRISM, «Актуальное», правка и удаление записей. PgCommunityTest — то же на PostgreSQL. */
open class CommunityTest {
    private var now = 1_700_000_000_000L
    private val dir = Files.createTempDirectory("memory-community").toFile()
    protected open fun newStore(): Store = MemoryStore()

    private val store by lazy { newStore() }
    private val core by lazy { Core(store, dir, Hub(), NoPush, { it.run() }, admins = setOf("moder")) { now } }

    private fun call(method: String, path: String, token: String? = null, body: Any? = null, query: Map<String, String> = emptyMap(), headers: Map<String, String> = emptyMap(), raw: ByteArray? = null): Resp {
        val h = HashMap(headers)
        if (token != null) h["Authorization"] = "Bearer $token"
        return core.handle(Req(method, path, query, h, raw ?: body?.toString()?.toByteArray()))
    }

    private data class Who(val id: Int, val access: String)

    private fun register(login: String): Who {
        val o = call("POST", "/v1/auth/register", body = JSONObject().put("login", login).put("password", "secret1")).json()
        return Who(o.getJSONObject("user").getInt("id"), o.getString("access"))
    }

    /** Войти заново (токен доступа живёт 15 минут). */
    private fun login(login: String): Who {
        val o = call("POST", "/v1/auth/login", body = JSONObject().put("login", login).put("password", "secret1")).json()
        return Who(o.getJSONObject("user").getInt("id"), o.getString("access"))
    }

    private fun moderator(): Who {
        val salt = Crypto.newSalt()
        store.createUser("moder", "Модератор", 0.5f, Crypto.hashPassword("secret1", salt), salt, now)
        return login("moder")
    }

    private fun items(r: Resp): JSONArray = r.json().getJSONArray("items")
    private fun ids(r: Resp): List<Long> = items(r).let { a -> (0 until a.length()).map { a.getJSONObject(it).getLong("id") } }

    /** Загрузка целиком (tus); путь — /v1/uploads/<id>. */
    private fun upload(w: Who, bytes: ByteArray = "0123456789".toByteArray()): String {
        val tus = mapOf("Tus-Resumable" to "1.0.0")
        val loc = call("POST", "/v1/uploads", w.access, headers = tus + ("Upload-Length" to bytes.size.toString())).headers.getValue("Location")
        assertEquals(204, call("PATCH", loc, w.access, headers = tus + mapOf("Content-Type" to "application/offset+octet-stream", "Upload-Offset" to "0"), raw = bytes).code)
        return loc
    }

    private fun media(path: String, kind: String = "Photo") = JSONObject().put("id", "m").put("kind", kind).put("path", path)

    private fun community(w: Who, name: String, extra: JSONObject.() -> Unit = {}): JSONObject {
        val body = JSONObject().put("name", name).put("topic", "Книги").put("description", "о книгах").put("seed", 7).apply(extra)
        val r = call("POST", "/v1/communities", w.access, body)
        assertEquals(201, r.code)
        return r.json()
    }

    @Test
    fun communityLifecycle() {
        val a = register("anna"); val b = register("boris"); val c = register("clara")
        val g = community(a, "Читатели") { put("onlyOwnerPosts", false).put("backdrop", JSONObject().put("style", "photo").put("photo", "/local/x.jpg")) }
        val id = g.getInt("id")
        assertTrue(id > 50_000_000)
        assertTrue(g.getBoolean("mine")); assertTrue(g.getBoolean("joined")); assertEquals(1, g.getInt("members"))
        assertEquals(0, g.getJSONArray("posts").length())
        // Фон «своё фото» не хранится: файл живёт на телефоне
        assertTrue(g.isNull("backdrop"))

        // Поиск и чужой взгляд
        val seen = items(call("GET", "/v1/communities", b.access, query = mapOf("q" to "чита"))).getJSONObject(0)
        assertEquals(id, seen.getInt("id")); assertFalse(seen.getBoolean("joined")); assertFalse(seen.getBoolean("mine"))
        assertEquals(a.id, seen.getInt("ownerId"))
        assertEquals(0, items(call("GET", "/v1/communities", b.access, query = mapOf("q" to "zzz"))).length())

        // Писать — только участникам
        assertEquals(403, call("POST", "/v1/communities/$id/posts", b.access, JSONObject().put("text", "привет")).code)
        assertTrue(call("PUT", "/v1/communities/$id/join", b.access).json().getBoolean("joined"))
        assertEquals(2, call("GET", "/v1/communities/$id", b.access).json().getInt("members"))
        assertEquals(listOf(id.toLong()), ids(call("GET", "/v1/me/communities", b.access)))
        now += 1000
        val bp = call("POST", "/v1/communities/$id/posts", b.access, JSONObject().put("text", "привет")).json().getLong("id")
        now += 1000
        val ap = call("POST", "/v1/communities/$id/posts", a.access, JSONObject().put("text", "правила")).json().getLong("id")
        // Записи группы — не в ленте и не на странице автора
        assertFalse(bp in ids(call("GET", "/v1/feed", b.access)))
        assertFalse(bp in ids(call("GET", "/v1/users/${b.id}/posts", a.access)))
        assertFalse(ap in ids(call("GET", "/v1/explore", c.access)))
        // Список группы видят и не участники
        val page = call("GET", "/v1/communities/$id/posts", c.access)
        assertEquals(listOf(ap, bp), ids(page))
        assertEquals(id, items(page).getJSONObject(0).getInt("community"))
        // Лайк и комментарий — от не участника тоже (группы открытые)
        assertEquals(204, call("PUT", "/v1/posts/$bp/like", c.access).code)
        val cm = call("POST", "/v1/posts/$bp/comments", c.access, JSONObject().put("text", "ок")).json().getLong("id")
        // Владелец группы может удалить чужой комментарий и запись
        assertEquals(204, call("DELETE", "/v1/comments/$cm", a.access).code)

        // Только создатель пишет
        assertEquals(403, call("PATCH", "/v1/communities/$id", b.access, JSONObject().put("name", "x")).code)
        val patched = call("PATCH", "/v1/communities/$id", a.access, JSONObject().put("name", "Читатели+").put("onlyOwnerPosts", true).put("pinnedPostId", ap)
            .put("info", JSONObject().put("website", "https://ex.am")).put("shop", JSONArray().put(JSONObject().put("id", 1).put("title", "Книга").put("price", 1500))))
        assertEquals(200, patched.code)
        val pj = patched.json()
        assertEquals("Читатели+", pj.getString("name")); assertEquals(ap, pj.getLong("pinnedPostId"))
        assertEquals("https://ex.am", pj.getJSONObject("info").getString("website"))
        assertEquals("Книга", pj.getJSONArray("shop").getJSONObject(0).getString("title"))
        // Закрепить чужую (не из группы) запись нельзя; PATCH — тело целиком
        val outside = call("POST", "/v1/posts", a.access, JSONObject().put("text", "лично")).json().getLong("id")
        val again = call("PATCH", "/v1/communities/$id", a.access, JSONObject().put("pinnedPostId", outside).put("onlyOwnerPosts", true)).json()
        assertTrue(again.isNull("pinnedPostId"))
        assertEquals("Читатели+", again.getString("name"))
        assertEquals(403, call("POST", "/v1/communities/$id/posts", b.access, JSONObject().put("text", "ещё")).code)

        // Создатель не выходит; участник — выходит
        assertEquals(400, call("DELETE", "/v1/communities/$id/join", a.access).code)
        assertEquals(1, call("DELETE", "/v1/communities/$id/join", b.access).json().getInt("members"))

        assertEquals(403, call("DELETE", "/v1/posts/$ap", b.access).code)
        assertEquals(204, call("DELETE", "/v1/posts/$bp", a.access).code)
        assertEquals(404, call("GET", "/v1/posts/$bp/comments", c.access).code)

        // Удалить группу — только создателю; записи уходят с ней
        assertEquals(403, call("DELETE", "/v1/communities/$id", b.access).code)
        assertEquals(204, call("DELETE", "/v1/communities/$id", a.access).code)
        assertEquals(404, call("GET", "/v1/communities/$id", b.access).code)
        assertEquals(null, store.post(ap))
    }

    @Test
    fun blockedOwnerHidesCommunity() {
        val a = register("anna"); val b = register("boris")
        val id = community(a, "Клуб").getInt("id")
        call("PUT", "/v1/communities/$id/join", b.access)
        val p = call("POST", "/v1/communities/$id/posts", a.access, JSONObject().put("text", "x")).json().getLong("id")
        call("PUT", "/v1/users/${a.id}/block", b.access)
        assertEquals(404, call("GET", "/v1/communities/$id", b.access).code)
        assertEquals(0, items(call("GET", "/v1/communities", b.access)).length())
        assertEquals(403, call("PUT", "/v1/posts/$p/like", b.access).code)
    }

    @Test
    fun coverUploadsCheckedAndCleaned() {
        val a = register("anna"); val b = register("boris")
        val mine = upload(a)
        val theirs = upload(b)
        // Чужая загрузка — нельзя
        assertEquals(422, call("POST", "/v1/communities", a.access, JSONObject().put("name", "X").put("cover", media(theirs))).code)
        val g = community(a, "С обложкой") { put("cover", media(mine)) }
        assertEquals(mine, g.getJSONObject("cover").getString("path"))
        // Сменили обложку — старый файл удалён
        val next = upload(a)
        call("PATCH", "/v1/communities/${g.getInt("id")}", a.access, JSONObject().put("cover", media(next)))
        assertEquals(404, call("GET", mine, a.access).code)
        assertEquals(200, call("GET", next, b.access).code)
    }

    @Test
    fun prisms() {
        val a = register("anna"); val b = register("boris")
        assertEquals(400, call("POST", "/v1/prisms", a.access, JSONObject().put("caption", "без видео")).code)
        assertEquals(400, call("POST", "/v1/prisms", a.access, JSONObject().put("video", media("https://giphy/x.mp4", "Video"))).code)
        val video = upload(a)
        now += 1000
        val pr = call("POST", "/v1/prisms", a.access, JSONObject().put("caption", "закат").put("music", "трек").put("seed", 3).put("durationMs", 12000)
            .put("video", media(video, "Video")))
        assertEquals(201, pr.code)
        val id = pr.json().getLong("id")
        assertEquals("закат", pr.json().getString("caption"))
        assertEquals(a.id, pr.json().getInt("author"))
        // В ленте и «Интересном» PRISM нет
        assertFalse(id in ids(call("GET", "/v1/feed", a.access)))
        assertFalse(id in ids(call("GET", "/v1/explore", b.access)))
        assertEquals(listOf(id), ids(call("GET", "/v1/prisms", b.access)))
        assertEquals(204, call("PUT", "/v1/posts/$id/like", b.access).code)
        assertEquals(201, call("POST", "/v1/posts/$id/comments", b.access, JSONObject().put("text", "вау")).code)
        val got = items(call("GET", "/v1/prisms", b.access)).getJSONObject(0)
        assertEquals(1, got.getInt("likes")); assertTrue(got.getBoolean("liked")); assertEquals(1, got.getInt("comments"))
        // Правка подписи — автору
        assertEquals(403, call("PATCH", "/v1/posts/$id", b.access, JSONObject().put("text", "x")).code)
        call("PATCH", "/v1/posts/$id", a.access, JSONObject().put("text", "рассвет"))
        assertEquals("рассвет", items(call("GET", "/v1/prisms", b.access)).getJSONObject(0).getString("caption"))
        // Жалоба на PRISM — автор проверен сервером, модератор удаляет вместе с видео
        val moder = moderator()
        call("POST", "/v1/reports", b.access, JSONObject().put("kind", "prism").put("target", id).put("author", 999).put("reason", "spam"))
        val rep = items(call("GET", "/v1/admin/reports", moder.access)).getJSONObject(0)
        assertTrue(rep.getBoolean("verified")); assertEquals(a.id, rep.getJSONObject("author").getInt("id"))
        assertEquals("рассвет", rep.getString("current"))
        call("POST", "/v1/admin/reports/${rep.getLong("id")}", moder.access, JSONObject().put("action", "remove"))
        assertEquals(0, items(call("GET", "/v1/prisms", b.access)).length())
        assertEquals(404, call("GET", video, b.access).code)
    }

    @Test
    fun closedProfilePrismsOnlyForFollowers() {
        val a = register("anna"); val b = register("boris")
        call("PUT", "/v1/me", a.access, JSONObject().put("name", "A").put("privacy", JSONObject().put("closedProfile", true)))
        call("POST", "/v1/prisms", a.access, JSONObject().put("video", media(upload(a), "Video")))
        assertEquals(0, items(call("GET", "/v1/prisms", b.access)).length())
        call("PUT", "/v1/users/${a.id}/follow", b.access)
        call("POST", "/v1/me/requests/${b.id}/accept", a.access)
        assertEquals(1, items(call("GET", "/v1/prisms", b.access)).length())
    }

    @Test
    fun editAndDeleteOwnPost() {
        val a = register("anna"); val b = register("boris")
        val photo = upload(a)
        val id = call("POST", "/v1/posts", a.access, JSONObject().put("text", "было").put("media", JSONArray().put(media(photo)))).json().getLong("id")
        now += 5000
        assertEquals(204, call("PATCH", "/v1/posts/$id", a.access, JSONObject().put("text", "стало")).code)
        val got = items(call("GET", "/v1/users/${a.id}/posts", b.access)).getJSONObject(0)
        assertEquals("стало", got.getString("text")); assertEquals(now, got.getLong("editedAt"))
        assertEquals(1, got.getJSONArray("media").length())
        // Убрали фото при правке — файл удалён
        val photo2 = upload(a)
        val id2 = call("POST", "/v1/posts", a.access, JSONObject().put("text", "два").put("media", JSONArray().put(media(photo2).put("id", "x1")).put(media(photo).put("id", "x2")))).json().getLong("id")
        call("PATCH", "/v1/posts/$id2", a.access, JSONObject().put("text", "два").put("keep", JSONArray().put("x2")))
        assertEquals(404, call("GET", photo2, b.access).code)
        assertEquals(1, items(call("GET", "/v1/users/${a.id}/posts", b.access)).getJSONObject(0).getJSONArray("media").length())
        call("DELETE", "/v1/posts/$id2", a.access)
        assertEquals(403, call("DELETE", "/v1/posts/$id", b.access).code)
        assertEquals(204, call("DELETE", "/v1/posts/$id", a.access).code)
        assertEquals(0, items(call("GET", "/v1/users/${a.id}/posts", b.access)).length())
        assertEquals(404, call("GET", photo, b.access).code)
    }

    @Test
    fun highlights() {
        val a = register("anna"); val b = register("boris"); val c = register("clara")
        val s1 = call("POST", "/v1/stories", a.access, JSONObject().put("text", "раз")).json().getLong("id")
        val s2 = call("POST", "/v1/stories", a.access, JSONObject().put("text", "для близких").put("close", true)).json().getLong("id")
        val other = call("POST", "/v1/stories", b.access, JSONObject().put("text", "чужая")).json().getLong("id")
        assertEquals(204, call("PUT", "/v1/me/highlights", a.access, JSONObject().put("items", JSONArray()
            .put(JSONObject().put("id", 1).put("title", "Лето").put("stories", JSONArray(listOf(s1, s2, other))).put("cover", s2))
            .put(JSONObject().put("id", 2).put("title", "Пусто").put("stories", JSONArray(listOf(other)))))).code)
        // Через неделю истории ушли из ленты историй, но в «Актуальном» остались
        now += 7 * 24 * 3600_000L
        val a2 = login("anna"); val c2 = login("clara")
        assertEquals(0, items(call("GET", "/v1/stories", a2.access)).length())
        val mine = items(call("GET", "/v1/users/${a.id}/highlights", a2.access))
        assertEquals(1, mine.length())
        assertEquals(listOf(s1, s2), mine.getJSONObject(0).getJSONArray("stories").let { x -> (0 until x.length()).map { x.getLong(it) } })
        assertEquals(2, mine.getJSONObject(0).getJSONArray("storyList").length())
        // Не близкий — без истории «для близких»; обложка — первая видимая
        val seen = items(call("GET", "/v1/users/${a.id}/highlights", c2.access)).getJSONObject(0)
        assertEquals(1, seen.getJSONArray("storyList").length())
        assertEquals(s1, seen.getLong("cover"))
        assertEquals("раз", seen.getJSONArray("storyList").getJSONObject(0).getString("text"))
        // Закрытый профиль — только подписчикам
        call("PUT", "/v1/me", a2.access, JSONObject().put("name", "A").put("privacy", JSONObject().put("closedProfile", true)))
        assertEquals(403, call("GET", "/v1/users/${a.id}/highlights", c2.access).code)
    }

    @Test
    fun deleteAccountRemovesOwnCommunities() {
        val a = register("anna"); val b = register("boris")
        val id = community(a, "Моя").getInt("id")
        val other = community(b, "Чужая").getInt("id")
        call("PUT", "/v1/communities/$other/join", a.access)
        call("PUT", "/v1/communities/$id/join", b.access)
        val photo = upload(b)
        val bp = call("POST", "/v1/communities/$id/posts", b.access, JSONObject().put("text", "x").put("media", JSONArray().put(media(photo)))).json().getLong("id")
        assertEquals(204, call("DELETE", "/v1/me", a.access, JSONObject().put("password", "secret1")).code)
        assertEquals(null, store.community(id))
        assertEquals(null, store.post(bp))
        assertEquals(404, call("GET", photo, b.access).code)
        assertEquals(1, call("GET", "/v1/communities/$other", b.access).json().getInt("members"))
    }
}
