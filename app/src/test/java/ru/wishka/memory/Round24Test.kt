package ru.wishka.memory

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.wishka.memory.data.ChatCrypto
import ru.wishka.memory.data.Demo
import ru.wishka.memory.data.Directory
import ru.wishka.memory.data.StateStore
import ru.wishka.memory.data.auth.KeyValueStore
import ru.wishka.memory.data.net.Net
import ru.wishka.memory.data.net.Session
import ru.wishka.memory.i18n.L10n
import ru.wishka.memory.i18n.Lang
import ru.wishka.memory.ui.AppViewModel
import java.util.Base64

/**
 * Раунд 24: настоящий сервер. Люди с сервера (справочник), свой номер на
 * сервере, время сообщений по часам телефона, новые маршруты демо-сервера.
 */
class Round24Test {
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
        Directory.clear()
        Directory.selfId = 0
    }

    @After
    fun tearDown() {
        Directory.clear()
        Directory.selfId = 0
    }

    private fun serverUser(id: Int, name: String, handle: String) =
        JSONObject().put("id", id).put("name", name).put("handle", handle).put("hue", 0.3)

    @Test
    fun directoryKnowsServerPeople() {
        assertNull(Demo.person(100_002))
        val key = ByteArray(32) { it.toByte() }
        Directory.remember(serverUser(100_002, "Борис", "boris").put("publicKey", Base64.getEncoder().encodeToString(key)))
        assertEquals("Борис", Demo.person(100_002)?.name)
        assertEquals(key.toList(), Directory.key(100_002)?.toList())
        // Демо-люди — по-прежнему свои
        assertEquals("Вера Лунная", Demo.person(2)?.name)
        // Ключ не той длины — как будто нет
        Directory.remember(serverUser(100_003, "Кира", "kira").put("publicKey", "AAAA"))
        assertNull(Directory.key(100_003))

        val saved = Directory.json()
        Directory.clear()
        assertNull(Demo.person(100_002))
        Directory.load(saved)
        assertEquals("boris", Demo.person(100_002)?.handle)
        assertNotNull(Directory.key(100_002))
    }

    @Test
    fun ownServerIdIsMe() {
        Directory.selfId = 100_001
        assertEquals(Demo.me.id, Directory.local(100_001))
        assertEquals(100_001, Directory.remote(Demo.me.id))
        assertEquals(100_005, Directory.local(100_005))
        // Себя в справочник не записываем
        assertNull(Directory.remember(serverUser(100_001, "Я", "me")))

        val c = StateStore.Codec(Demo.me)
        val post = c.postOf(JSONObject().put("id", 10_000_001L).put("author", 100_001).put("text", "Моё"))
        assertEquals(Demo.me.id, post.author.id)
        val msg = c.messageOf(JSONObject().put("id", 7).put("fromMe", true).put("text", "x").put("authorId", 100_001).put("sentAt", now))
        assertEquals(Demo.me.id, msg.authorId)
    }

    @Test
    fun authorInfoFillsDirectoryAndTimeFromSentAt() {
        val c = StateStore.Codec(Demo.me)
        val post = c.postOf(JSONObject().put("id", 10_000_002L).put("author", 100_007).put("text", "Горы")
            .put("authorInfo", serverUser(100_007, "Лия", "liya")))
        assertEquals("Лия", post.author.name)
        val m = c.messageOf(JSONObject().put("id", 3).put("fromMe", false).put("text", "Привет").put("time", "")
            .put("authorId", 100_008).put("sentAt", now).put("authorInfo", serverUser(100_008, "Ян", "yan")))
        assertTrue(m.time.matches(Regex("\\d\\d:\\d\\d")))
        assertEquals("Ян", Demo.person(m.authorId)?.name)
        // Без sentAt и без времени — пусто, а не падение
        assertEquals("", c.messageOf(JSONObject().put("id", 4).put("fromMe", false).put("text", "x").put("time", "")).time)
    }

    @Test
    fun savedStateKeepsServerPeople() {
        val vm = AppViewModel(clock = { now }, demoReplies = false)
        Directory.remember(serverUser(100_002, "Борис", "boris"))
        val c = StateStore.Codec(vm.state.value.me)
        val json = c.json(vm.snapshot())
        Directory.clear()
        c.snapshot(json)
        assertEquals("Борис", Demo.person(100_002)?.name)
    }

    @Test
    fun sessionRemembersServerId() {
        val store = MapStore()
        val s = Session(store, "anna")
        s.save(JSONObject().put("access", "a").put("refresh", "r").put("deviceId", "d").put("user", JSONObject().put("id", 100_001)))
        assertEquals(100_001, Session(store, "anna").userId)
        // Выход: токенов нет, номер остаётся (записи с ним уже на телефоне)
        s.clear()
        assertEquals(100_001, Session(store, "anna").userId)
        assertEquals(0, Session(store, "boris").userId)
    }

    @Test
    fun demoServerAnswersNewRoutes() = runBlocking {
        val vm = AppViewModel(clock = { now }, demoReplies = false)
        val net = Net.demo({ now }, { vm.state.value.me })
        net.signIn("wishka", "x", "Pixel", "android")
        assertTrue(net.api.searchUsers("ве").isEmpty())
        // Демо-собеседник с ключом: тот же, что демо-шифрование
        val vera = net.api.user(2)
        assertEquals("Вера Лунная", vera?.name)
        assertEquals(ChatCrypto.demoPeer(2).publicKey.toList(), Directory.key(2)?.toList())
        assertEquals(2, net.api.directChat(2))
        net.api.putKey(ByteArray(32))
        assertTrue(net.api.chats().isEmpty())
    }

    @Test
    fun secretNeedsPeersKey() {
        val vm = AppViewModel(clock = { now }, demoReplies = false)
        Directory.remember(serverUser(100_009, "Ева", "eva"))
        val chatId = 1_600_000_000
        vm.onRealtime(ru.wishka.memory.data.net.RtEvent.NewMessage(chatId, JSONObject().put("id", 1).put("fromMe", false).put("text", "Привет")
            .put("time", "").put("authorId", 100_009).put("sentAt", now).put("attachments", JSONArray())))
        val chat = vm.state.value.chats.first { it.id == chatId }
        assertEquals("Ева", chat.peer.name)
        vm.setSecret(chatId, true)
        assertTrue(!vm.state.value.chats.first { it.id == chatId }.secret)
        assertEquals("У собеседника пока нет ключа шифрования", vm.state.value.notice)
        // Ключ пришёл — можно
        Directory.remember(serverUser(100_009, "Ева", "eva").put("publicKey", Base64.getEncoder().encodeToString(ru.wishka.memory.data.ChatKeyPair.generate().publicKey)))
        vm.setSecret(chatId, true)
        assertTrue(vm.state.value.chats.first { it.id == chatId }.secret)
    }
}
