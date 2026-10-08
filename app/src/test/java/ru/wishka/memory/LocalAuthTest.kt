package ru.wishka.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.wishka.memory.data.auth.AuthResult
import ru.wishka.memory.data.auth.KeyValueStore
import ru.wishka.memory.data.auth.LocalAuth

/** Вход по логину и паролю: регистрация, запоминание, защита от подбора. */
class LocalAuthTest {

    private class MapStore : KeyValueStore {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
    }

    private var now = 1_000_000L
    private val store = MapStore()
    // Меньше итераций, чтобы тесты шли быстро; логика та же
    private fun auth() = LocalAuth(store, clock = { now }, iterations = 1_000)

    private fun AuthResult.error() = (this as AuthResult.Error).message

    @Test
    fun registerSignsInAndIsRemembered() {
        val r = auth().register("Vera.Moon", "Вера", "secret123", "secret123")
        assertTrue(r is AuthResult.Ok)
        // «Перезапуск»: новый объект на том же хранилище — человек всё ещё вошёл
        assertEquals("vera.moon", auth().current()?.username)
        assertEquals("Вера", auth().current()?.displayName)
    }

    @Test
    fun passwordIsNotStored() {
        auth().register("mark", "Марк", "secret123", "secret123")
        assertFalse(store.map.values.any { it.contains("secret123") })
    }

    @Test
    fun registrationRules() {
        val a = auth()
        assertTrue(a.register("ab", "Имя", "secret123", "secret123").error().contains("3"))
        assertTrue(a.register("кириллица", "Имя", "secret123", "secret123").error().contains("латинские"))
        assertTrue(a.register("alina", "", "secret123", "secret123").error().contains("имя"))
        assertTrue(a.register("alina", "Алина", "short1", "short1").error().contains("8"))
        assertTrue(a.register("alina", "Алина", "onlyletters", "onlyletters").error().contains("цифры"))
        assertTrue(a.register("alina", "Алина", "secret123", "secret124").error().contains("не совпадают"))
        assertTrue(a.register("alina", "Алина", "secret123", "secret123") is AuthResult.Ok)
        assertTrue(a.register("@ALINA", "Другая", "secret123", "secret123").error().contains("занят"))
    }

    @Test
    fun loginLogout() {
        val a = auth()
        a.register("sofia", "София", "secret123", "secret123")
        a.logout()
        assertNull(a.current())
        assertEquals("Неверный логин или пароль", a.login("sofia", "wrong1234").error())
        // Нет такого логина — тот же ответ, чтобы нельзя было перебирать логины
        assertEquals("Неверный логин или пароль", a.login("nobody", "secret123").error())
        assertTrue(a.login(" @Sofia ", "secret123") is AuthResult.Ok)
        assertEquals("sofia", a.current()?.username)
    }

    @Test
    fun lockAfterTooManyFailures() {
        val a = auth()
        a.register("timur", "Тимур", "secret123", "secret123")
        a.logout()
        repeat(LocalAuth.MAX_FAILURES) { a.login("timur", "wrong1234") }
        // Даже верный пароль не пускает, пока действует блокировка
        assertTrue(a.login("timur", "secret123").error().contains("Слишком много"))
        now += LocalAuth.LOCK_MS + 1
        assertTrue(a.login("timur", "secret123") is AuthResult.Ok)
    }

    @Test
    fun demoAccountWorks() {
        val a = auth()
        a.ensureDemoAccount()
        a.ensureDemoAccount() // второй вызов ничего не меняет
        assertTrue(a.login(LocalAuth.DEMO_LOGIN, LocalAuth.DEMO_PASSWORD) is AuthResult.Ok)
    }

    @Test
    fun renameSurvivesRestart() {
        auth().register("alex", "Алекс", "secret123", "secret123")
        auth().rename("alex", "Александр")
        assertEquals("Александр", auth().current()?.displayName)
    }
}
