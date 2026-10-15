package ru.wishka.memory.server

import org.junit.Assume.assumeTrue
import org.junit.Before

/**
 * Те же проверки, что CoreTest, — на настоящем PostgreSQL. Работает, когда
 * задан TEST_DATABASE_URL (CI: .github/workflows/server.yml); иначе пропускается.
 */
class PgStoreTest : CoreTest() {
    companion object {
        private val url = System.getenv("TEST_DATABASE_URL").orEmpty()
        private val pg by lazy { PgStore(url, System.getenv("TEST_DB_USER").orEmpty(), System.getenv("TEST_DB_PASSWORD").orEmpty()) }
    }

    @Before
    fun needsDatabase() = assumeTrue("TEST_DATABASE_URL не задан", url.isNotEmpty())

    override fun newStore(): Store = pg.also { it.wipe() }
}
