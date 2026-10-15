package ru.wishka.memory

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.Demo
import ru.wishka.memory.data.FileCrypto
import ru.wishka.memory.data.FileKey
import ru.wishka.memory.data.HiddenReason
import ru.wishka.memory.data.MediaKind
import ru.wishka.memory.data.Message
import ru.wishka.memory.data.Moderation
import ru.wishka.memory.data.ModerationPrefs
import ru.wishka.memory.data.RateLimiter
import ru.wishka.memory.data.StateStore
import ru.wishka.memory.data.auth.KeyValueStore
import ru.wishka.memory.data.net.DemoRealtime
import ru.wishka.memory.data.net.HttpRequest
import ru.wishka.memory.data.net.MemoryCache
import ru.wishka.memory.data.net.Net
import ru.wishka.memory.data.net.NetError
import ru.wishka.memory.data.net.RtEvent
import ru.wishka.memory.i18n.L10n
import ru.wishka.memory.i18n.Lang
import ru.wishka.memory.ui.AppViewModel
import java.io.File
import java.security.MessageDigest

/**
 * Раунд 23: групповые чаты и живой канал (прочитано, печатает), фоновая
 * отправка и шифрование файлов секретных переписок, вход на сервер и
 * устройства, модерация и запросы на переписку.
 */
class Round23Test {
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

    private fun vm() = AppViewModel(clock = { now }, demoReplies = false)

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    // ── Группы ───────────────────────────────────────────────────────

    @Test
    fun groupLifecycle() = runBlocking {
        val vm = vm()
        val me = vm.state.value.me.id
        val friends = vm.state.value.following.toList().take(3)
        val stranger = Demo.people.first { it.id !in vm.state.value.following }.id
        assertNull(vm.createGroup("  ", friends))
        val id = vm.createGroup("Походы", friends + stranger)!!
        val chat = vm.state.value.chats.first { it.id == id }
        assertTrue(chat.isGroup)
        assertEquals("Походы", chat.peer.name)
        assertEquals(listOf(me) + friends, chat.group!!.memberIds) // незнакомого не добавить
        assertTrue(chat.group!!.isAdmin(me))
        assertEquals(AppViewModel.SYSTEM_AUTHOR, chat.messages.single().authorId)
        vm.pumpNet()
        assertEquals(0, vm.state.value.outboxCount) // группа создана на сервере

        vm.renameGroup(id, "Походы 2026")
        assertEquals("Походы 2026", vm.state.value.chats.first { it.id == id }.peer.name)
        vm.removeGroupMember(id, friends[0])
        vm.removeGroupMember(id, me) // создателя не убрать
        vm.setGroupAdmin(id, friends[1], true)
        val g = vm.state.value.chats.first { it.id == id }.group!!
        assertEquals(listOf(me, friends[1], friends[2]), g.memberIds)
        assertTrue(g.isAdmin(friends[1]))
        vm.addGroupMembers(id, listOf(friends[0]))
        assertTrue(friends[0] in vm.state.value.chats.first { it.id == id }.group!!.memberIds)
        vm.pumpNet()
        assertEquals(0, vm.state.value.outboxCount)
        // Сервер прислал новый состав (кто-то другой поменял) — применяем
        vm.onRealtime(RtEvent.GroupChanged(id, "Горы", listOf(me, friends[2]), listOf(me)))
        assertEquals("Горы", vm.state.value.chats.first { it.id == id }.peer.name)
        assertEquals(listOf(me, friends[2]), vm.state.value.chats.first { it.id == id }.group!!.memberIds)
        // Группы — без сквозного шифрования
        vm.setSecret(id, true)
        assertFalse(vm.state.value.chats.first { it.id == id }.secret)
        vm.leaveGroup(id)
        assertTrue(vm.state.value.chats.none { it.id == id })
    }

    // ── Живой канал ──────────────────────────────────────────────────

    @Test
    fun readReceiptsAndTyping() = runBlocking {
        val vm = vm()
        val chat = vm.state.value.chats.first { !it.isGroup }
        vm.send(chat.id, "Привет!")
        vm.pumpNet()
        val sent = vm.state.value.chats.first { it.id == chat.id }.messages.last()
        assertTrue(sent.delivered && sent.sentAt > 0 && sent.readBy.isEmpty())
        // Собеседник читает через пару секунд — событием живого канала
        assertEquals(0, vm.tickRealtime())
        now += 3_000
        assertEquals(1, vm.tickRealtime())
        assertEquals(listOf(chat.peer.id), vm.state.value.chats.first { it.id == chat.id }.messages.last().readBy)
        // «Печатает» в группе — по именам
        val friends = vm.state.value.following.toList().take(2)
        val gid = vm.createGroup("Двое", friends)!!
        vm.onRealtime(RtEvent.Typing(gid, friends[0], true))
        vm.onRealtime(RtEvent.Typing(gid, friends[1], true))
        val title = vm.state.value.typingTitle(vm.state.value.chats.first { it.id == gid })!!
        assertTrue(title.endsWith("печатают…"))
        // Пришло сообщение — автор больше не печатает
        vm.onRealtime(RtEvent.NewMessage(gid, StateStore.Codec(vm.state.value.me).messageJson(Message(0, false, "Иду!", "12:00", authorId = friends[0], sentAt = now))))
        val gchat = vm.state.value.chats.first { it.id == gid }
        assertEquals(setOf(friends[1]), gchat.typingIds)
        assertEquals(friends[0], gchat.messages.last().authorId)
        // Свой «печатает» — не чаще раза в 3 секунды
        val rt = vm.net.realtime as DemoRealtime
        vm.typing(gid); vm.typing(gid)
        assertEquals(1, rt.sent.count { it is RtEvent.Typing })
        // События — в JSON и обратно
        listOf(RtEvent.Read(1, 2, 3), RtEvent.Typing(1, 2, true), RtEvent.GroupChanged(5, "x", listOf(1, 2), listOf(1))).forEach {
            assertEquals(it, RtEvent.parse(it.json().toString()))
        }
        assertNull(RtEvent.parse("{\"type\":\"?\"}"))
    }

    // ── Запросы на переписку ─────────────────────────────────────────

    @Test
    fun strangersLandInRequests() = runBlocking {
        val vm = vm()
        val stranger = Demo.people.first { p -> p.id !in vm.state.value.following && vm.state.value.chats.none { it.peer.id == p.id } }
        val codec = StateStore.Codec(vm.state.value.me)
        fun write(text: String) = vm.onRealtime(RtEvent.NewMessage(2_000_000 + stranger.id, codec.messageJson(Message(0, false, text, "10:00", authorId = stranger.id))))
        // «Никто не может писать» — чужое не приходит вовсе
        vm.setPrivacy(vm.state.value.privacy.copy(messages = ru.wishka.memory.data.Audience.Nobody))
        write("Эй")
        assertTrue(vm.state.value.chats.none { it.peer.id == stranger.id })
        vm.setPrivacy(vm.state.value.privacy.copy(messages = ru.wishka.memory.data.Audience.Everyone))
        val unreadBefore = vm.state.value.unreadTotal
        write("Здравствуйте!")
        assertEquals(1, vm.state.value.requestChats.size)
        assertTrue(vm.state.value.chatList.none { it.request })
        assertEquals(unreadBefore, vm.state.value.unreadTotal) // запросы не считаются в непрочитанные
        // Заблокировать из запроса — больше не приходит
        vm.blockRequest(vm.state.value.requestChats.single().id)
        assertTrue(vm.state.value.isBlocked(stranger.id))
        write("Эй!")
        assertTrue(vm.state.value.requestChats.isEmpty())
        // Разблокировали, написал снова — приняли: переписка как обычно
        vm.unblock(stranger.id)
        write("Это снова я")
        vm.acceptRequest(vm.state.value.requestChats.single().id)
        assertTrue(vm.state.value.requestChats.isEmpty())
        assertTrue(vm.state.value.chatList.any { it.peer.id == stranger.id })
    }

    // ── Модерация ────────────────────────────────────────────────────

    @Test
    fun moderationCatchesTricks() {
        val p = ModerationPrefs(hiddenWords = listOf("спойлер", "Кот"))
        assertEquals(HiddenReason.Word, Moderation.check("Без СПОЙЛЕРОВ, пожалуйста", p))
        assertEquals(HiddenReason.Word, Moderation.check("коты лучше", p))
        assertNull(Moderation.check("скот на ферме", p)) // не с середины слова
        assertEquals(HiddenReason.Offensive, Moderation.check("ты д у р а к", p).let { Moderation.check("ты дуурак", p) })
        assertEquals(HiddenReason.Offensive, Moderation.check("ну ты и ид10т", p))
        assertEquals(HiddenReason.Offensive, Moderation.check("what an IDIOT", p))
        assertNull(Moderation.check("дурацкая погода", p.copy(hiddenWords = emptyList())).takeIf { false })
        assertNull(Moderation.check("ты дура", p.copy(hideOffensive = false)))
        assertEquals(HiddenReason.Spam, Moderation.check("Заработок без вложений http://a.ru", p))
        assertEquals(HiddenReason.Spam, Moderation.check("a http://1.ru http://2.ru http://3.ru", p))
        assertEquals(HiddenReason.Spam, Moderation.check("купи купи купи купи купи купи", p))
        assertNull(Moderation.check("Обычный комментарий про горы", p))
        assertEquals(listOf("спойлер"), Moderation.addWord(Moderation.addWord(emptyList(), " спойлер "), "СПОЙЛЕР"))
        // Лимит частоты
        val rl = RateLimiter(2, 1000)
        assertTrue(rl.allow(0)); assertTrue(rl.allow(10)); assertFalse(rl.allow(20)); assertTrue(rl.allow(1001))
    }

    @Test
    fun moderationInApp() {
        val vm = vm()
        val s0 = vm.state.value
        vm.addHiddenWord("спойлер")
        assertEquals(listOf("спойлер"), vm.state.value.privacy.hiddenWords)
        val stranger = Demo.people.first { it.id !in s0.following }.id
        val friendChat = s0.chats.first { !it.isGroup && it.peer.id in s0.following }
        assertEquals(HiddenReason.Word, vm.state.value.hiddenReason("будет спойлер", stranger))
        assertNull(vm.state.value.hiddenReason("будет спойлер", s0.me.id)) // своё не скрываем
        assertNull(vm.state.value.hiddenReason("будет спойлер", friendChat.peer.id, friendChat)) // друг в переписке
        vm.removeHiddenWord("спойлер")
        assertNull(vm.state.value.hiddenReason("будет спойлер", stranger))
        // Комментарии выключены — чужие не добавить, свои можно
        vm.publish("Моя запись")
        val post = vm.state.value.posts.first()
        vm.toggleComments(post.id)
        assertTrue(vm.state.value.posts.first().commentsOff)
        vm.addComment(post.id, "Мой комментарий")
        assertEquals(1, vm.state.value.posts.first().commentList.size)
        // Не чаще 10 комментариев в минуту
        repeat(12) { vm.addComment(post.id, "флуд $it") }
        assertEquals(10, vm.state.value.posts.first().commentList.size)
        assertNotNull(vm.state.value.notice)
        vm.dismissNotice()
        now += 61_000
        vm.addComment(post.id, "через минуту")
        assertEquals(11, vm.state.value.posts.first().commentList.size)
    }

    // ── Шифрование файлов ────────────────────────────────────────────

    @Test
    fun fileCryptoMatchesVectorAndDetectsTampering() {
        val key = FileKey(ByteArray(32) { it.toByte() }, byteArrayOf(9, 8, 7, 6))
        val data = "Memory secret file: 0123456789abcdef!".toByteArray()
        val enc = FileCrypto.encrypt(data, key, chunk = 16)
        // Вектор посчитан отдельно (Python cryptography) — так же обязан шифровать и iPhone
        assertEquals(94, enc.size)
        assertEquals("661585dac000a2260cc1c8d5d115fb6e1d7ce3f6f594dc764db0fa95e892b74f", hex(MessageDigest.getInstance("SHA-256").digest(enc)))
        assertTrue(FileCrypto.decrypt(enc, key).contentEquals(data))
        // Пустой и ровно кратный куску
        assertTrue(FileCrypto.decrypt(FileCrypto.encrypt(ByteArray(0), key, 16), key).isEmpty())
        val even = ByteArray(32) { 1 }
        assertTrue(FileCrypto.decrypt(FileCrypto.encrypt(even, key, 16), key).contentEquals(even))
        fun fails(b: ByteArray) = try { FileCrypto.decrypt(b, key); false } catch (e: Exception) { true }
        assertTrue(fails(enc.copyOf(enc.size - 21))) // отрезали последний кусок
        assertTrue(fails(enc.clone().also { it[20] = (it[20] + 1).toByte() })) // подменили байт
        val swapped = enc.copyOfRange(0, 9) + enc.copyOfRange(41, 73) + enc.copyOfRange(9, 41) + enc.copyOfRange(73, enc.size)
        assertTrue(fails(swapped)) // переставили куски
        assertTrue(fails(FileCrypto.encrypt(data, FileKey.random(), 16)))
        assertEquals(key, FileKey.decode(key.encoded()))
        assertNull(FileKey.decode("коротко"))
    }

    @Test
    fun secretChatUploadsOnlyCiphertext() = runBlocking {
        val vm = vm()
        val chat = vm.state.value.chats.first { !it.isGroup }
        vm.setSecret(chat.id, true)
        val file = File.createTempFile("secret", ".jpg").apply { writeBytes(ByteArray(3000) { (it % 7).toByte() }); deleteOnExit() }
        vm.send(chat.id, "Смотри фото", listOf(Attachment("s1", MediaKind.Photo, file.path)))
        val key = FileKey.decode(vm.lastFileKeys.getValue("s1"))!!
        vm.pumpNet()
        val onServer = vm.net.api.messages(chat.id, null).items.single()
        val att = onServer.attachments.single()
        assertTrue(att.name.startsWith(FileCrypto.SEALED)) // ключ — только запечатанным
        assertFalse(att.name.contains(vm.lastFileKeys.getValue("s1")))
        val stored = vm.net.demo!!.uploaded(att.path)!!
        assertFalse(stored.contentEquals(file.readBytes()))
        assertTrue(FileCrypto.decrypt(stored, key).contentEquals(file.readBytes()))
        assertFalse(onServer.text.contains("Смотри"))
    }

    // ── Вход и устройства ────────────────────────────────────────────

    @Test
    fun sessionRefreshesAndDevices() = runBlocking {
        val store = MapStore()
        val n = Net.demo({ now }, { Demo.me }, tokens = store, login = "anna")
        assertFalse(n.session.signedIn)
        // Без входа — 401
        val noAuth = runCatching { n.api.devices() }.exceptionOrNull() as NetError
        assertEquals(NetError.Kind.Auth, noAuth.kind)
        n.signIn("anna", "secret", "Pixel 8", "android")
        assertTrue(n.session.signedIn)
        assertTrue(store.map.keys.all { it.startsWith("session.api.anna.") }) // в зашифрованной части хранилища
        val list = n.api.devices()
        assertEquals(3, list.size)
        assertEquals("Pixel 8", list.single { it.current }.name)
        // Токен доступа истёк — Api сам обновил и повторил
        val oldAccess = n.session.access
        n.demo!!.expireAccessTokens()
        assertEquals(3, n.api.devices().size)
        assertFalse(oldAccess == n.session.access)
        // Выйти на остальных
        n.api.revokeOtherDevices()
        assertEquals(1, n.api.devices().size)
        // Нас самих «выкинули» с другого устройства: обновить нельзя — сессия стёрта
        n.demo!!.expireAccessTokens()
        val raw = n.demo!!.send(HttpRequest("GET", "/v1/devices"))
        assertEquals(401, raw.code)
        // Сессия переживает перезапуск
        val again = Net.demo({ now }, { Demo.me }, tokens = store, login = "anna")
        assertTrue(again.session.signedIn)
    }

    @Test
    fun staleTokensSignInAgain() = runBlocking {
        val store = MapStore()
        val vm = vm()
        vm.attachNet(Net.demo({ now }, { vm.state.value.me }, tokens = store, login = "demo"))
        vm.startSessionNow("demo", "x", fresh = false)
        // «Перезапуск»: токены на телефоне есть, а демо-сервер новый и их не знает
        val vm2 = vm()
        vm2.attachNet(Net.demo({ now }, { vm2.state.value.me }, tokens = store, login = "demo"))
        assertTrue(vm2.net.session.signedIn)
        vm2.startSessionNow("demo", null, fresh = false)
        assertFalse(vm2.state.value.signedOut)
        assertEquals(1, vm2.net.demo!!.deviceCount() - 2) // вошли заново этим телефоном
    }

    @Test
    fun outboxWaitsWhenSignedOut() = runBlocking {
        val vm = vm()
        vm.startSessionNow("demo", "x", fresh = false)
        vm.pumpNet()
        // Сессию закрыли на сервере — очередь ждёт, ничего не теряем
        vm.net.session.let { s -> s.clear() }
        vm.net.queueCall("PUT", "/v1/me", org.json.JSONObject().put("name", "X"))
        vm.pumpNet()
        assertTrue(vm.state.value.signedOut)
        assertEquals(1, vm.state.value.outboxCount)
    }

    @Test
    fun profileSyncsBetweenPhones() = runBlocking {
        // Первый телефон: вошли, поменяли профиль — ушло на сервер
        val vm1 = vm()
        vm1.startSessionNow("demo", "x", fresh = false)
        vm1.updateProfile("Анна Новая", ru.wishka.memory.data.ProfileInfo(bio = "Люблю горы"))
        vm1.addHiddenWord("спойлер")
        vm1.pumpNet()
        val server = vm1.net.demo!!
        // Второй телефон, тот же сервер: новый аккаунт на телефоне — профиль приехал
        val vm2 = vm()
        vm2.attachNet(Net(server, MemoryCache(), { now }, { vm2.state.value.me }, tokens = MapStore(), login = "demo"))
        vm2.startSessionNow("demo", "x", fresh = true)
        assertEquals("Анна Новая", vm2.state.value.me.name)
        assertEquals("Люблю горы", vm2.state.value.me.bio)
        assertEquals(listOf("спойлер"), vm2.state.value.privacy.hiddenWords)
    }

    // ── Фоновая отправка ─────────────────────────────────────────────

    @Test
    fun reconcileAfterBackgroundSend() = runBlocking {
        val vm = vm()
        val cache = MemoryCache()
        val server = vm.net.demo!!
        val n = Net(server, cache, { now }, { vm.state.value.me })
        vm.attachNet(n)
        vm.setDemoOffline(true)
        vm.publish("Ушла в фоне")
        val chat = vm.state.value.chats.first { !it.isGroup }
        vm.send(chat.id, "И это")
        assertTrue(vm.state.value.posts.first().outboxKey.isNotEmpty())
        // «Приложение закрыто»: очередь отправил фоновый процесс (свой Net поверх той же базы)
        val worker = Net(server, cache, { now }, { vm.state.value.me })
        server.online = true
        worker.pump()
        assertEquals(0, worker.outbox.count)
        // Приложение открылось снова — сверка с очередью
        vm.attachNet(Net(server, cache, { now }, { vm.state.value.me }))
        assertEquals("", vm.state.value.posts.first().outboxKey)
        assertFalse(vm.state.value.chats.first { it.id == chat.id }.messages.last().pending)
    }

    @Suppress("unused")
    private fun neverCalled() = fail()
}
