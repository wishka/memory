package ru.wishka.memory

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import ru.wishka.memory.data.Demo
import ru.wishka.memory.data.ReportKind
import ru.wishka.memory.data.ReportReason
import ru.wishka.memory.data.ReportTarget
import ru.wishka.memory.data.auth.AuthResult
import ru.wishka.memory.data.auth.KeyValueStore
import ru.wishka.memory.data.auth.LocalAuth
import ru.wishka.memory.i18n.L10n
import ru.wishka.memory.i18n.Lang
import ru.wishka.memory.ui.AppViewModel

/**
 * Раунд 26: вход на сервер с плашки, модерация (жалобы и блокировки на
 * сервере), удаление аккаунта и смена пароля и на сервере. Здесь — то, что
 * проверяется без сервера; с сервером — интеграционная проверка против server/.
 */
class Round26Test {
    private var now = 1_800_000_000_000L

    private class MapStore : KeyValueStore {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
    }

    @Before
    fun setUp() {
        L10n.choice = Lang.Ru
    }

    @Test
    fun checkChangeDoesNotChangePassword() {
        val store = MapStore()
        val auth = LocalAuth(store, clock = { now }, iterations = 1_000)
        assert(auth.register("anna", "Анна", "secret12", "secret12") is AuthResult.Ok)
        assertEquals("Текущий пароль не подходит", auth.checkChange("anna", "wrong", "newpass12", "newpass12"))
        assertEquals("Пароли не совпадают", auth.checkChange("anna", "secret12", "newpass12", "other"))
        assertNull(auth.checkChange("anna", "secret12", "newpass12", "newpass12"))
        // Проверка ничего не поменяла: старый пароль по-прежнему подходит
        assert(auth.checkPasswordOf("anna", "secret12"))
        assertNull(auth.changePassword("anna", "secret12", "newpass12", "newpass12"))
        assert(auth.checkPasswordOf("anna", "newpass12"))
    }

    @Test
    fun demoHasNoServerThings() = runBlocking {
        val vm = AppViewModel(clock = { now }, demoReplies = false)
        assertFalse(vm.state.value.serverMode)
        // С демо-сервером удалять и менять на сервере нечего
        assertNull(vm.deleteOnServer("x"))
        assertNull(vm.changeServerPassword("x", "y"))
        // Жалоба на демо-человека остаётся на телефоне: в очередь сети ничего не уходит
        val before = vm.state.value.outboxCount
        val p = Demo.posts.first()
        vm.report(ReportTarget(ReportKind.Post, p.id, p.author.id, p.author.name, preview = p.text), ReportReason.Spam)
        assertEquals(before, vm.state.value.outboxCount)
        assertEquals(1, vm.state.value.reports.size)
    }
}
