package ru.wishka.memory.server

import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Удаление аккаунта и бан — на PostgreSQL (там всё держится на ON DELETE CASCADE).
 * Работает, когда задан TEST_DATABASE_URL; иначе пропускается.
 */
class PgModerationTest {
    @Test
    fun cascadesOnPostgres() {
        val url = System.getenv("TEST_DATABASE_URL").orEmpty()
        assumeTrue("TEST_DATABASE_URL не задан", url.isNotEmpty())
        val pg = PgStore(url, System.getenv("TEST_DB_USER").orEmpty(), System.getenv("TEST_DB_PASSWORD").orEmpty())
        pg.wipe()
        val t = ModerationTestOn(pg)
        t.run()
    }
}

/** Те же шаги, что ModerationTest.deleteAccountRemovesEverything и блокировка, — на заданном хранилище. */
private class ModerationTestOn(private val store: Store) {
    fun run() {
        val core = Core(store, java.nio.file.Files.createTempDirectory("pg-mod").toFile(), Hub(), NoPush, { it.run() }, admins = setOf("moder")) { 1_700_000_000_000L }
        fun call(method: String, path: String, token: String? = null, body: String? = null) =
            core.handle(Req(method, path, emptyMap(), token?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap(), body?.toByteArray()))
        fun reg(login: String) = call("POST", "/v1/auth/register", body = """{"login":"$login","password":"secret1"}""").json()
        val a = reg("anna"); val b = reg("boris")
        val at = a.getString("access"); val bt = b.getString("access")
        val aid = a.getJSONObject("user").getInt("id"); val bid = b.getJSONObject("user").getInt("id")
        call("PUT", "/v1/users/$bid/follow", at)
        val post = call("POST", "/v1/posts", at, """{"text":"x"}""").json().getLong("id")
        call("POST", "/v1/posts/$post/comments", bt, """{"text":"y"}""")
        call("POST", "/v1/stories", at, """{"text":"s"}""")
        call("PUT", "/v1/users/$aid/block", bt)
        check(store.blockedEither(aid, bid))
        check(call("DELETE", "/v1/me", at, """{"password":"secret1"}""").code == 204)
        check(store.userByLogin("anna") == null)
        check(store.post(post) == null)
        check(!store.blockedEither(aid, bid))
        check(call("POST", "/v1/reports", bt, """{"kind":"person","target":$aid,"reason":"spam"}""").code == 201)
    }
}
