package ru.wishka.memory

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.Demo
import ru.wishka.memory.data.MediaKind
import ru.wishka.memory.data.Message
import ru.wishka.memory.data.Post
import ru.wishka.memory.data.net.Backoff
import ru.wishka.memory.data.net.BytesSource
import ru.wishka.memory.data.net.CacheKeys
import ru.wishka.memory.data.net.DemoServer
import ru.wishka.memory.data.net.HttpRequest
import ru.wishka.memory.data.net.MediaPlan
import ru.wishka.memory.data.net.MediaQuality
import ru.wishka.memory.data.net.MemoryCache
import ru.wishka.memory.data.net.Net
import ru.wishka.memory.data.net.NetError
import ru.wishka.memory.data.net.OpKind
import ru.wishka.memory.data.net.UploadJob
import ru.wishka.memory.data.net.UploadState
import ru.wishka.memory.i18n.L10n
import ru.wishka.memory.i18n.Lang
import ru.wishka.memory.ui.AppViewModel

/**
 * Раунд 22: сетевой слой (демо-сервер по контракту API), лента страницами,
 * офлайн-кэш, очередь отправки с Idempotency-Key, сжатие и загрузка tus
 * с докачкой.
 */
class Round22Test {
    private var now = 1_800_000_000_000L
    private val me = Demo.me

    @Before
    fun setUp() {
        L10n.choice = Lang.Ru
    }

    private fun net(cache: MemoryCache = MemoryCache(), chunk: Int = 1024) = Net.demo({ now }, { me }, cache, chunkSize = chunk)

    // ── Лента страницами ─────────────────────────────────────────────

    @Test
    fun feedPagesWithoutGapsOrDuplicates() = runBlocking {
        val n = net()
        val pager = n.feedPager()
        assertNull(pager.refresh())
        assertEquals(20, pager.state.items.size)
        while (!pager.state.end) assertNull(pager.loadMore())
        val ids = pager.state.items.map { it.id }
        assertEquals(150, ids.size)
        assertEquals(ids.size, ids.toSet().size)
        // Новые сверху
        val times = pager.state.items.map { it.createdAt }
        assertEquals(times.sortedDescending(), times)
        // Курсор «время_номер»: новая запись между страницами не сдвигает следующие
        val p2 = n.feedPager()
        p2.refresh()
        n.api.createPost(Post(0, me, "Свежая", 0), "k-fresh")
        p2.loadMore()
        assertEquals(40, p2.state.items.map { it.id }.toSet().size)
    }

    @Test
    fun offlineShowsCacheThenRefreshes() = runBlocking {
        val cache = MemoryCache()
        val n = net(cache)
        val pager = n.feedPager()
        pager.refresh()
        pager.loadMore()
        assertTrue(cache.get(CacheKeys.FEED) != null)
        // Новый запуск, сети нет: сохранённое видно сразу
        val again = net(cache)
        again.demo!!.online = false
        val p = again.feedPager()
        assertTrue(p.restore())
        assertTrue(p.state.fromCache)
        assertEquals(40, p.state.items.size)
        val err = p.loadMore()
        assertEquals(NetError.Kind.Offline, err?.kind)
        assertEquals(40, p.state.items.size) // сохранённое не пропало
        again.demo!!.online = true
        assertNull(p.refresh())
        assertFalse(p.state.fromCache)
        // «Очистить кэш» не трогает очередь
        again.queueLike(5_000_001, true)
        cache.clearPages()
        assertNull(cache.get(CacheKeys.FEED))
        assertEquals(1, again.outbox.count)
    }

    // ── Ошибки и повторы ─────────────────────────────────────────────

    @Test
    fun errorsAndBackoff() = runBlocking {
        val n = net()
        n.demo!!.failNext = 1
        val e = runCatching { n.api.feed(null) }.exceptionOrNull() as NetError
        assertEquals(NetError.Kind.Server, e.kind)
        assertTrue(e.retryable)
        assertEquals(1000, e.retryAfterMs)
        val nf = runCatching { n.api.call(HttpRequest("GET", "/v1/nope")) }.exceptionOrNull() as NetError
        assertEquals(NetError.Kind.Client, nf.kind)
        assertFalse(nf.retryable)
        assertEquals(1000, Backoff.delayMs(0))
        assertEquals(8000, Backoff.delayMs(3))
        assertEquals(Backoff.MAX_MS, Backoff.delayMs(30))
        assertEquals(9600, Backoff.delayMs(3, jitter = 1.0))
        assertEquals(1500, Backoff.delayMs(5, retryAfterMs = 1500))
    }

    // ── Очередь отправки ─────────────────────────────────────────────

    @Test
    fun outboxSendsOnceEvenIfRetried() = runBlocking {
        val n = net()
        val server = n.demo!!
        val before = server.postCount()
        server.online = false
        n.queuePost(Post(1, me, "Без сети", 0, createdAt = now))
        n.queueLike(5_000_003, true)
        n.queueLike(5_000_003, false)
        n.queueLike(5_000_003, true) // схлопываются: уходит последнее
        assertEquals(2, n.outbox.count)
        val r1 = n.pump()
        assertTrue(r1.offline)
        assertEquals(2, n.outbox.count)
        // Сеть вернулась, но первая попытка — 503: ждёт паузу
        server.online = true
        server.failNext = 1
        now += 60_000
        val r2 = n.pump()
        assertEquals(1, r2.sent.size)
        assertEquals(1, n.outbox.count)
        now += 60_000
        val r3 = n.pump()
        assertEquals(1, r3.sent.size)
        assertEquals(0, n.outbox.count)
        assertEquals(before + 1, server.postCount())
        assertTrue(server.isLiked(5_000_003))
        // Повтор с тем же ключом (ответ «потерялся») — без дубля
        val post = Post(2, me, "Один раз", 0)
        val a = n.api.createPost(post, "same-key")
        val b = n.api.createPost(post, "same-key")
        assertEquals(a.id, b.id)
        assertEquals(before + 2, server.postCount())
    }

    @Test
    fun messagesKeepOrderInChat() = runBlocking {
        val server = DemoServer({ me }, { now })
        val n = Net(server, MemoryCache(), { now }, { me }, source = { p -> if (p == "/tmp/ok.jpg") BytesSource(ByteArray(10)) else null }, chunkSize = 1024)
        val photo = Attachment("ph", MediaKind.Photo, "/tmp/ok.jpg")
        n.queueMessage(1, Message(1, true, "с фото", "12:00", attachments = listOf(photo)))
        n.queueMessage(1, Message(2, true, "второе", "12:00"))
        n.queueMessage(2, Message(3, true, "в другой чат", "12:00"))
        // Загрузка фото споткнулась (503): первое ждёт, второе не обгоняет его; другой чат не ждёт
        server.failNext = 1
        n.pump()
        assertEquals(0, server.messagesIn(1))
        assertEquals(1, server.messagesIn(2))
        now += 60_000
        n.pump()
        assertEquals(listOf("второе", "с фото"), n.api.messages(1, null).items.map { it.text }) // новые сверху
        assertEquals(0, n.outbox.count)
        // Файл пропал — сообщение с ним уходит в «не отправлено», остальное идёт дальше
        n.queueMessage(3, Message(4, true, "битое", "12:00", attachments = listOf(Attachment("x", MediaKind.Photo, "/tmp/none.jpg"))))
        n.queueMessage(3, Message(5, true, "после", "12:00"))
        val r = n.pump()
        assertEquals(listOf(OpKind.SendMessage), r.failed.map { it.kind })
        assertEquals(listOf("после"), n.api.messages(3, null).items.map { it.text })
    }

    // ── Загрузка tus с докачкой ──────────────────────────────────────

    @Test
    fun uploadResumesAfterDropAndRestart() = runBlocking {
        val cache = MemoryCache()
        val bytes = ByteArray(5000) { (it % 251).toByte() }
        val src = BytesSource(bytes)
        val server = DemoServer({ me }, { now })
        fun make() = Net(server, cache, { now }, { me }, source = { src }, chunkSize = 1024)
        val n = make()
        val post = Post(7, me, "Фото", 0, media = listOf(Attachment("a1", MediaKind.Photo, "/tmp/p.jpg")), createdAt = now)
        val key = n.queuePost(post)
        // Обрыв посреди третьего куска
        server.dropAfterBytes = -1
        var steps = 0
        n.uploads.runOnce { s -> steps++; if (steps == 2) server.dropAfterBytes = 300 }
        val half = n.uploads.all().single()
        assertEquals(UploadState.Waiting, half.state)
        assertTrue(half.url.isNotEmpty())
        assertEquals(1024, half.offset) // в кэше — сколько точно подтвердил сервер (ещё 300 байт дошли без ответа)
        // «Перезапуск приложения»: новый Net поверх того же кэша
        now += 10_000
        val again = make()
        val r = again.pump()
        val done = again.uploads.all()
        assertTrue(r.sent.any { it.first.key == key }) // запись ушла после загрузки
        assertTrue(done.isEmpty()) // готовые загрузки убраны
        assertTrue(server.uploaded(half.url)!!.contentEquals(bytes)) // сервер собрал файл ровно, с куском после обрыва
        assertEquals("p.jpg", server.uploadName(half.url))
        val sentPost = r.sent.first { it.first.key == key }.second!!
        assertTrue(sentPost.getJSONArray("media").getJSONObject(0).getString("path").startsWith("/v1/uploads/"))
    }

    @Test
    fun uploadPauseResumeAndConflict() = runBlocking {
        val cache = MemoryCache()
        val src = BytesSource(ByteArray(3000) { 1 })
        val n = Net(DemoServer({ me }, { now }), cache, { now }, { me }, source = { src }, chunkSize = 1000)
        n.uploads.add(UploadJob("j1", "a", "owner", "/tmp/x.mp4", video = true))
        n.uploads.pause("j1")
        assertNull(n.uploads.runOnce())
        n.uploads.resume("j1")
        // Сервер ждёт с другого места (сюда дошёл кусок без ответа) — HEAD и дальше
        val done = n.uploads.runOnce()!!
        assertEquals(UploadState.Done, done.state)
        assertEquals(3000, done.offset)
        assertEquals(1f, done.progress)
        assertEquals(mapOf("a" to done.url), n.uploads.doneFor("owner"))
    }

    // ── Сжатие ───────────────────────────────────────────────────────

    @Test
    fun compressionPlan() {
        assertEquals(2048, MediaPlan.photo(MediaQuality.Standard, wifi = true).maxSide)
        assertEquals(1280, MediaPlan.photo(MediaQuality.Saver, wifi = true).maxSide)
        assertEquals(0, MediaPlan.photo(MediaQuality.OriginalOnWifi, wifi = true).maxSide)
        assertEquals(2048, MediaPlan.photo(MediaQuality.OriginalOnWifi, wifi = false).maxSide)
        assertEquals(720, MediaPlan.video(MediaQuality.Standard, false).height)
        assertEquals(480, MediaPlan.video(MediaQuality.Saver, true).height)
        assertEquals(2048 to 1536, MediaPlan.fit(4032, 3024, 2048))
        assertEquals(1536 to 2048, MediaPlan.fit(3024, 4032, 2048))
        assertEquals(800 to 600, MediaPlan.fit(800, 600, 2048)) // не увеличиваем
        assertEquals(1280 to 720, MediaPlan.fitVideo(1920, 1080, 720))
        assertEquals(720 to 1280, MediaPlan.fitVideo(1080, 1920, 720))
        assertEquals(640 to 360, MediaPlan.fitVideo(640, 360, 720))
        val (w, h) = MediaPlan.fitVideo(1000, 1777, 720)
        assertTrue(w % 2 == 0 && h % 2 == 0)
        assertEquals(1, MediaPlan.sampleSize(2000, 1500, 2048))
        // Читаем не меньше нужного: 4032 → 2016 было бы уже мельче 2048
        assertEquals(1, MediaPlan.sampleSize(4032, 3024, 2048))
        assertEquals(2, MediaPlan.sampleSize(8064, 6048, 2048))
        assertEquals(4, MediaPlan.sampleSize(16128, 12096, 2048))
        val plan = MediaPlan.video(MediaQuality.Standard, false)
        assertTrue(MediaPlan.needsVideoWork(1920, 1080, 8_000_000, plan))
        assertFalse(MediaPlan.needsVideoWork(1280, 720, 2_000_000, plan))
        assertTrue(MediaPlan.needsVideoWork(1280, 720, 9_000_000, plan))
        assertEquals(MediaQuality.Saver, MediaQuality.of("saver"))
        assertEquals(MediaQuality.Standard, MediaQuality.of("?"))
    }

    @Test
    fun cursorRoundTrip() {
        val c = DemoServer.encodeCursor(123456789L, 42)
        assertEquals(123456789L to 42L, DemoServer.decodeCursor(c))
        assertNull(DemoServer.decodeCursor("мусор"))
    }

    // ── Приложение целиком ───────────────────────────────────────────

    private fun vm() = AppViewModel(clock = { now }, demoReplies = false)

    @Test
    fun offlinePostWaitsThenGoes() = runBlocking {
        val vm = vm()
        val server = vm.net.demo!!
        val before = server.postCount()
        vm.setDemoOffline(true)
        assertEquals(ru.wishka.memory.ui.NetStatus.Offline, vm.state.value.netStatus)
        vm.publish("Без сети, но с душой")
        val p = vm.state.value.posts.first()
        assertTrue(p.outboxKey.isNotEmpty()) // видна сразу, «ждёт сети»
        assertEquals(1, vm.state.value.outboxCount)
        vm.pumpNet()
        assertEquals(before, server.postCount())
        vm.setDemoOffline(false)
        vm.pumpNet()
        assertEquals("", vm.state.value.posts.first().outboxKey)
        assertEquals(0, vm.state.value.outboxCount)
        assertEquals(before + 1, server.postCount())
        // «Нравится» к чужой записи — тоже через очередь
        val other = vm.state.value.feed.first { it.author.id != vm.state.value.me.id }
        vm.toggleLike(other.id)
        vm.pumpNet()
        assertTrue(server.isLiked(other.id))
    }

    @Test
    fun messagesShowPendingUntilSent() = runBlocking {
        val vm = vm()
        val chat = vm.state.value.chats.first()
        vm.setDemoOffline(true)
        vm.send(chat.id, "Ты где?")
        val m = vm.state.value.chats.first { it.id == chat.id }.messages.last()
        assertTrue(m.pending)
        vm.setDemoOffline(false)
        vm.pumpNet()
        assertFalse(vm.state.value.chats.first { it.id == chat.id }.messages.last().pending)
        assertEquals(1, vm.net.demo!!.messagesIn(chat.id))
    }

    @Test
    fun secretChatSendsOnlyEnvelope() = runBlocking {
        val vm = vm()
        val chat = vm.state.value.chats.first()
        vm.setSecret(chat.id, true)
        vm.send(chat.id, "пароль от вайфая 12345")
        vm.pumpNet()
        val onServer = vm.net.api.messages(chat.id, null).items.single()
        assertFalse(onServer.text.contains("вайфая"))
        assertEquals(vm.lastEnvelope, onServer.text)
    }

    @Test
    fun feedLoadsOlderPagesAndRemembersThem() = runBlocking {
        val vm = vm()
        val local = vm.state.value.feed.size
        vm.loadMoreFeedNow()
        assertEquals(20, vm.state.value.olderFeed.size)
        vm.loadMoreFeedNow()
        assertEquals(40, vm.state.value.olderFeed.size)
        val all = vm.state.value.feedWithOlder
        assertTrue(all.size > local)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        // Заблокировали автора — и старые его записи пропали
        val victim = vm.state.value.olderFeed.first().author.id
        vm.block(victim)
        assertTrue(vm.state.value.feedWithOlder.none { it.author.id == victim })
        // Без сети — ошибка внизу ленты, подгруженное на месте
        vm.setDemoOffline(true)
        vm.loadMoreFeedNow()
        assertEquals(NetError.Kind.Offline, vm.state.value.feedPaging.error)
        assertEquals(40, vm.state.value.olderFeed.size)
        assertTrue(vm.state.value.cacheBytes > 0)
        val localNow = vm.state.value.feed.size
        vm.clearNetCache()
        assertEquals(0, vm.state.value.cacheBytes)
        // Очередь и загрузки не тронуты, лента с телефона — на месте
        assertEquals(localNow, vm.state.value.feed.size)
        assertTrue(local >= localNow)
    }

    @Test
    fun cancelUploadKeepsPostLocal() = runBlocking {
        val vm = vm()
        vm.setDemoOffline(true)
        vm.publish("Фото", listOf(Attachment("ph1", MediaKind.Photo, "/tmp/nope.jpg")))
        val up = vm.state.value.uploads.single()
        vm.pauseUpload(up.id)
        assertEquals(UploadState.Paused, vm.state.value.uploads.single().state)
        vm.cancelUpload(up.id)
        assertTrue(vm.state.value.uploads.isEmpty())
        assertEquals(0, vm.state.value.outboxCount)
        assertEquals("", vm.state.value.posts.first().outboxKey)
        assertEquals("Фото", vm.state.value.posts.first().text)
        assertNull(vm.state.value.uploadProgress("nobody"))
    }
}
