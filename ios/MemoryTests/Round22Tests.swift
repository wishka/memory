import XCTest
@testable import Memory

/// Раунд 22: сетевой слой (демо-сервер по контракту API), лента страницами,
/// офлайн-кэш, очередь отправки с Idempotency-Key, сжатие и загрузка tus
/// с докачкой. Те же проверки, что Round22Test на Android, и сверх них —
/// JSON записей и сообщений в формате Android.
final class Round22Tests: XCTestCase {
    private var clock = TestClock(Date(timeIntervalSince1970: 1_800_000_000))
    private let me = Demo.me

    /// Часы сети — в мс, как у Android.
    private var now: Int64 {
        get { Json.ms(clock.now) }
        set { clock.now = Date(timeIntervalSince1970: Double(newValue) / 1000) }
    }

    override func setUp() {
        super.setUp()
        L10n.shared.setLanguage(.ru)
        L10n.shared.systemLanguage = "ru"
        clock = TestClock(Date(timeIntervalSince1970: 1_800_000_000))
    }

    private func ms() -> () -> Int64 {
        let clock = self.clock
        return { Json.ms(clock.now) }
    }

    private func net(_ cache: MemoryCache = MemoryCache(), chunk: Int = 1024) -> Net {
        let me = self.me
        return Net.demo(clock: ms(), me: { me }, cache: cache, chunkSize: chunk)
    }

    private func makeServer() -> DemoServer {
        let me = self.me
        return DemoServer(me: { me }, clock: ms())
    }

    private func photo(_ id: String, _ path: String) -> Attachment {
        Attachment(id: id, kind: .photo, fileName: path)
    }

    private func message(_ id: Int64, _ text: String, _ attachments: [Attachment] = []) -> Message {
        var m = Message(id: id, fromMe: true, text: text, time: "12:00")
        m.attachments = attachments
        return m
    }

    // MARK: - Лента страницами

    func testFeedPagesWithoutGapsOrDuplicates() async throws {
        let n = net()
        let pager = n.feedPager()
        let first = await pager.refresh()
        XCTAssertNil(first)
        XCTAssertEqual(20, pager.state.items.count)
        while !pager.state.end {
            let err = await pager.loadMore()
            XCTAssertNil(err)
        }
        let ids = pager.state.items.map(\.id)
        XCTAssertEqual(150, ids.count)
        XCTAssertEqual(ids.count, Set(ids).count)
        // Новые сверху
        let times = pager.state.items.map { Json.ms($0.createdAt) }
        XCTAssertEqual(times.sorted(by: >), times)
        // Курсор «время:номер»: новая запись между страницами не сдвигает следующие
        let p2 = n.feedPager()
        await p2.refresh()
        _ = try await n.api.createPost(Post(id: 0, author: me, text: "Свежая", minutesAgo: 0), key: "k-fresh")
        await p2.loadMore()
        XCTAssertEqual(40, Set(p2.state.items.map(\.id)).count)
    }

    func testOfflineShowsCacheThenRefreshes() async throws {
        let cache = MemoryCache()
        let n = net(cache)
        let pager = n.feedPager()
        await pager.refresh()
        await pager.loadMore()
        XCTAssertNotNil(cache.get(CacheKeys.feed))
        // Новый запуск, сети нет: сохранённое видно сразу
        let again = net(cache)
        let demo = try XCTUnwrap(again.demo)
        demo.online = false
        let p = again.feedPager()
        XCTAssertTrue(p.restore())
        XCTAssertTrue(p.state.fromCache)
        XCTAssertEqual(40, p.state.items.count)
        let err = await p.loadMore()
        XCTAssertEqual(NetError.Kind.offline, err?.kind)
        XCTAssertEqual(40, p.state.items.count) // сохранённое не пропало
        demo.online = true
        let fresh = await p.refresh()
        XCTAssertNil(fresh)
        XCTAssertFalse(p.state.fromCache)
        // «Очистить кэш» не трогает очередь
        again.queueLike(5_000_001, liked: true)
        cache.clearPages()
        XCTAssertNil(cache.get(CacheKeys.feed))
        XCTAssertEqual(1, again.outbox.count)
    }

    // MARK: - Ошибки и повторы

    func testErrorsAndBackoff() async throws {
        let n = net()
        let demo = try XCTUnwrap(n.demo)
        demo.failNext = 1
        do {
            _ = try await n.api.feed(nil)
            XCTFail("ждали 503")
        } catch let e as NetError {
            XCTAssertEqual(NetError.Kind.server, e.kind)
            XCTAssertTrue(e.retryable)
            XCTAssertEqual(1000, e.retryAfterMs)
        }
        do {
            _ = try await n.api.call(HttpRequest(method: "GET", path: "/v1/nope"))
            XCTFail("ждали 404")
        } catch let nf as NetError {
            XCTAssertEqual(NetError.Kind.client, nf.kind)
            XCTAssertFalse(nf.retryable)
        }
        XCTAssertEqual(1000, Backoff.delayMs(0))
        XCTAssertEqual(8000, Backoff.delayMs(3))
        XCTAssertEqual(Backoff.maxMs, Backoff.delayMs(30))
        XCTAssertEqual(9600, Backoff.delayMs(3, jitter: 1.0))
        XCTAssertEqual(1500, Backoff.delayMs(5, retryAfterMs: 1500))
    }

    // MARK: - Очередь отправки

    func testOutboxSendsOnceEvenIfRetried() async throws {
        let n = net()
        let server = try XCTUnwrap(n.demo)
        let before = server.postCount()
        server.online = false
        var offlinePost = Post(id: 1, author: me, text: "Без сети", minutesAgo: 0)
        offlinePost.createdAt = clock.now
        n.queuePost(offlinePost)
        n.queueLike(5_000_003, liked: true)
        n.queueLike(5_000_003, liked: false)
        n.queueLike(5_000_003, liked: true) // схлопываются: уходит последнее
        XCTAssertEqual(2, n.outbox.count)
        let r1 = await n.pump()
        XCTAssertTrue(r1.offline)
        XCTAssertEqual(2, n.outbox.count)
        // Сеть вернулась, но первая попытка — 503: ждёт паузу
        server.online = true
        server.failNext = 1
        now += 60_000
        let r2 = await n.pump()
        XCTAssertEqual(1, r2.sent.count)
        XCTAssertEqual(1, n.outbox.count)
        now += 60_000
        let r3 = await n.pump()
        XCTAssertEqual(1, r3.sent.count)
        XCTAssertEqual(0, n.outbox.count)
        XCTAssertEqual(before + 1, server.postCount())
        XCTAssertTrue(server.isLiked(5_000_003))
        // Повтор с тем же ключом (ответ «потерялся») — без дубля
        let post = Post(id: 2, author: me, text: "Один раз", minutesAgo: 0)
        let a = try await n.api.createPost(post, key: "same-key")
        let b = try await n.api.createPost(post, key: "same-key")
        XCTAssertEqual(a.id, b.id)
        XCTAssertEqual(before + 2, server.postCount())
    }

    func testMessagesKeepOrderInChat() async throws {
        let server = makeServer()
        let me = self.me
        let n = Net(
            transport: server, cache: MemoryCache(), clock: ms(), me: { me },
            source: { p in p == "/tmp/ok.jpg" ? BytesSource(Data(count: 10)) : nil },
            chunkSize: 1024
        )
        n.queueMessage(1, message(1, "с фото", [photo("ph", "/tmp/ok.jpg")]))
        n.queueMessage(1, message(2, "второе"))
        n.queueMessage(2, message(3, "в другой чат"))
        // Загрузка фото споткнулась (503): первое ждёт, второе не обгоняет его; другой чат не ждёт
        server.failNext = 1
        _ = await n.pump()
        XCTAssertEqual(0, server.messagesIn(1))
        XCTAssertEqual(1, server.messagesIn(2))
        now += 60_000
        _ = await n.pump()
        let chat1 = try await n.api.messages(1, nil)
        XCTAssertEqual(["второе", "с фото"], chat1.items.map(\.text)) // новые сверху
        XCTAssertEqual(0, n.outbox.count)
        // Файл пропал — сообщение с ним уходит в «не отправлено», остальное идёт дальше
        n.queueMessage(3, message(4, "битое", [photo("x", "/tmp/none.jpg")]))
        n.queueMessage(3, message(5, "после"))
        let r = await n.pump()
        XCTAssertEqual([OpKind.sendMessage], r.failed.map(\.kind))
        let chat3 = try await n.api.messages(3, nil)
        XCTAssertEqual(["после"], chat3.items.map(\.text))
    }

    // MARK: - Загрузка tus с докачкой

    func testUploadResumesAfterDropAndRestart() async throws {
        let cache = MemoryCache()
        let bytes = Data((0..<5000).map { UInt8($0 % 251) })
        let src = BytesSource(bytes)
        let server = makeServer()
        let me = self.me
        let clock = ms()
        func make() -> Net {
            Net(transport: server, cache: cache, clock: clock, me: { me }, source: { _ in src }, chunkSize: 1024)
        }
        let n = make()
        var post = Post(id: 7, author: me, text: "Фото", minutesAgo: 0)
        post.media = [photo("a1", "/tmp/p.jpg")]
        post.createdAt = self.clock.now
        let key = n.queuePost(post)
        // Обрыв посреди третьего куска
        server.dropAfterBytes = -1
        var steps = 0
        await n.uploads.runOnce { _ in
            steps += 1
            if steps == 2 { server.dropAfterBytes = 300 }
        }
        let all = n.uploads.all()
        XCTAssertEqual(1, all.count)
        let half = try XCTUnwrap(all.first)
        XCTAssertEqual(UploadState.waiting, half.state)
        XCTAssertFalse(half.url.isEmpty)
        XCTAssertEqual(1024, half.offset) // в кэше — сколько точно подтвердил сервер (ещё 300 байт дошли без ответа)
        // «Перезапуск приложения»: новый Net поверх того же кэша
        now += 10_000
        let again = make()
        let r = await again.pump()
        XCTAssertTrue(r.sent.contains { $0.op.key == key }) // запись ушла после загрузки
        XCTAssertTrue(again.uploads.all().isEmpty) // готовые загрузки убраны
        XCTAssertEqual(bytes, server.uploaded(half.url)) // сервер собрал файл ровно, с куском после обрыва
        XCTAssertEqual("p.jpg", server.uploadName(half.url))
        let sent = try XCTUnwrap(r.sent.first { $0.op.key == key }?.answer)
        let media = Json.objects(sent, "media")
        XCTAssertTrue(Json.string(try XCTUnwrap(media.first), "path").hasPrefix("/v1/uploads/"))
    }

    func testUploadPauseResumeAndConflict() async throws {
        let src = BytesSource(Data(repeating: 1, count: 3000))
        let me = self.me
        let n = Net(transport: makeServer(), cache: MemoryCache(), clock: ms(), me: { me }, source: { _ in src }, chunkSize: 1000)
        n.uploads.add(UploadJob(id: "j1", attachmentId: "a", owner: "owner", path: "/tmp/x.mp4", video: true))
        n.uploads.pause("j1")
        let none = await n.uploads.runOnce()
        XCTAssertNil(none)
        n.uploads.resume("j1")
        let finished = await n.uploads.runOnce()
        let done = try XCTUnwrap(finished)
        XCTAssertEqual(UploadState.done, done.state)
        XCTAssertEqual(3000, done.offset)
        XCTAssertEqual(1, done.progress)
        XCTAssertEqual(["a": done.url], n.uploads.doneFor("owner"))
    }

    // MARK: - Сжатие

    func testCompressionPlan() {
        XCTAssertEqual(2048, MediaPlan.photo(.standard, wifi: true).maxSide)
        XCTAssertEqual(1280, MediaPlan.photo(.saver, wifi: true).maxSide)
        XCTAssertEqual(0, MediaPlan.photo(.originalOnWifi, wifi: true).maxSide)
        XCTAssertEqual(2048, MediaPlan.photo(.originalOnWifi, wifi: false).maxSide)
        XCTAssertEqual(720, MediaPlan.video(.standard, wifi: false).height)
        XCTAssertEqual(480, MediaPlan.video(.saver, wifi: true).height)
        XCTAssertEqual(MediaSize(2048, 1536), MediaPlan.fit(4032, 3024, 2048))
        XCTAssertEqual(MediaSize(1536, 2048), MediaPlan.fit(3024, 4032, 2048))
        XCTAssertEqual(MediaSize(800, 600), MediaPlan.fit(800, 600, 2048)) // не увеличиваем
        XCTAssertEqual(MediaSize(1280, 720), MediaPlan.fitVideo(1920, 1080, 720))
        XCTAssertEqual(MediaSize(720, 1280), MediaPlan.fitVideo(1080, 1920, 720))
        XCTAssertEqual(MediaSize(640, 360), MediaPlan.fitVideo(640, 360, 720))
        let odd = MediaPlan.fitVideo(1000, 1777, 720)
        XCTAssertTrue(odd.width % 2 == 0 && odd.height % 2 == 0)
        XCTAssertEqual(1, MediaPlan.sampleSize(2000, 1500, 2048))
        // Читаем не меньше нужного: 4032 → 2016 было бы уже мельче 2048
        XCTAssertEqual(1, MediaPlan.sampleSize(4032, 3024, 2048))
        XCTAssertEqual(2, MediaPlan.sampleSize(8064, 6048, 2048))
        XCTAssertEqual(4, MediaPlan.sampleSize(16128, 12096, 2048))
        let plan = MediaPlan.video(.standard, wifi: false)
        XCTAssertTrue(MediaPlan.needsVideoWork(1920, 1080, bitrate: 8_000_000, plan))
        XCTAssertFalse(MediaPlan.needsVideoWork(1280, 720, bitrate: 2_000_000, plan))
        XCTAssertTrue(MediaPlan.needsVideoWork(1280, 720, bitrate: 9_000_000, plan))
        XCTAssertEqual(MediaQuality.saver, MediaQuality.of("saver"))
        XCTAssertEqual(MediaQuality.standard, MediaQuality.of("?"))
    }

    func testCursorRoundTrip() {
        let c = DemoServer.encodeCursor(123_456_789, 42)
        let decoded = DemoServer.decodeCursor(c)
        XCTAssertEqual(123_456_789, decoded?.at)
        XCTAssertEqual(42, decoded?.id)
        XCTAssertFalse(c.contains("="))
        XCTAssertNil(DemoServer.decodeCursor("мусор"))
    }

    // MARK: - JSON в формате Android

    func testPostAndMessageJsonUseAndroidKeys() throws {
        let codec = ApiCodec(me: me)
        var post = Post(id: 9, author: Demo.people[0], text: "Привет", minutesAgo: 0)
        post.createdAt = Date(timeIntervalSince1970: 1_800_000_000)
        post.media = [photo("m1", "img.jpg")]
        post.outboxKey = "k1"
        let o = codec.postJson(post)
        XCTAssertEqual(Demo.people[0].id, o["author"] as? Int)
        XCTAssertEqual(1_800_000_000_000, o["createdAt"] as? Int64)
        XCTAssertEqual("k1", o["outboxKey"] as? String)
        let media = try XCTUnwrap(Json.objects(o, "media").first)
        XCTAssertEqual("Photo", media["kind"] as? String)
        XCTAssertEqual("img.jpg", media["path"] as? String)
        let back = try XCTUnwrap(codec.postOf(try XCTUnwrap(Json.parse(Json.string(o)))))
        XCTAssertEqual(post.id, back.id)
        XCTAssertEqual(post.author.id, back.author.id)
        XCTAssertEqual(post.createdAt, back.createdAt)
        XCTAssertEqual("img.jpg", back.media.first?.fileName)
        XCTAssertEqual("k1", back.outboxKey)
        var m = message(5, "Ты где?")
        m.pending = true
        let mo = codec.messageJson(m)
        XCTAssertEqual(true, mo["pending"] as? Bool)
        let mBack = try XCTUnwrap(codec.messageOf(try XCTUnwrap(Json.parse(Json.string(mo)))))
        XCTAssertTrue(mBack.pending)
        XCTAssertEqual("Ты где?", mBack.text)
        // Сохранение на телефоне помнит, что запись и сообщение ещё не ушли
        let saved = try JSONDecoder().decode(Post.self, from: JSONEncoder().encode(post))
        XCTAssertEqual("k1", saved.outboxKey)
        let savedMessage = try JSONDecoder().decode(Message.self, from: JSONEncoder().encode(m))
        XCTAssertTrue(savedMessage.pending)
    }

    // MARK: - Приложение целиком

    private func makeModel() -> AppModel {
        let clock = self.clock
        return AppModel(clock: { clock.now }, demoReplies: false)
    }

    func testOfflinePostWaitsThenGoes() async throws {
        let vm = makeModel()
        let server = try XCTUnwrap(vm.net.demo)
        let before = server.postCount()
        vm.setDemoOffline(true)
        XCTAssertEqual(NetStatus.offline, vm.state.netStatus)
        vm.publish("Без сети, но с душой")
        let p = try XCTUnwrap(vm.state.posts.first)
        XCTAssertFalse(p.outboxKey.isEmpty) // видна сразу, «ждёт сети»
        XCTAssertEqual(1, vm.state.outboxCount)
        await vm.pumpNet()
        XCTAssertEqual(before, server.postCount())
        vm.setDemoOffline(false)
        await vm.pumpNet()
        XCTAssertEqual("", vm.state.posts.first?.outboxKey)
        XCTAssertEqual(0, vm.state.outboxCount)
        XCTAssertEqual(before + 1, server.postCount())
        // «Нравится» к чужой записи — тоже через очередь
        let meId = vm.state.me.id
        let other = try XCTUnwrap(vm.state.feed.first { $0.author.id != meId })
        vm.toggleLike(other.id)
        await vm.pumpNet()
        XCTAssertTrue(server.isLiked(other.id))
    }

    func testMessagesShowPendingUntilSent() async throws {
        let vm = makeModel()
        let chat = try XCTUnwrap(vm.state.chats.first)
        vm.setDemoOffline(true)
        vm.send(chat.id, "Ты где?")
        let m = vm.state.chats.first { $0.id == chat.id }?.messages.last
        XCTAssertEqual(true, m?.pending)
        vm.setDemoOffline(false)
        await vm.pumpNet()
        XCTAssertEqual(false, vm.state.chats.first { $0.id == chat.id }?.messages.last?.pending)
        XCTAssertEqual(1, try XCTUnwrap(vm.net.demo).messagesIn(chat.id))
    }

    func testSecretChatSendsOnlyEnvelope() async throws {
        let vm = makeModel()
        let chat = try XCTUnwrap(vm.state.chats.first)
        vm.setSecret(chat.id, true)
        vm.send(chat.id, "пароль от вайфая 12345")
        await vm.pumpNet()
        let page = try await vm.net.api.messages(chat.id, nil)
        XCTAssertEqual(1, page.items.count)
        let onServer = try XCTUnwrap(page.items.first)
        XCTAssertFalse(onServer.text.contains("вайфая"))
        XCTAssertEqual(vm.lastEnvelope, onServer.text)
    }

    func testFeedLoadsOlderPagesAndRemembersThem() async throws {
        let vm = makeModel()
        let local = vm.state.feed.count
        await vm.loadMoreFeedNow()
        XCTAssertEqual(20, vm.state.olderFeed.count)
        await vm.loadMoreFeedNow()
        XCTAssertEqual(40, vm.state.olderFeed.count)
        let all = vm.state.feedWithOlder
        XCTAssertTrue(all.count > local)
        XCTAssertEqual(all.count, Set(all.map(\.id)).count)
        // Заблокировали автора — и старые его записи пропали
        let victim = try XCTUnwrap(vm.state.olderFeed.first).author.id
        vm.block(victim)
        XCTAssertFalse(vm.state.feedWithOlder.contains { $0.author.id == victim })
        // Без сети — ошибка внизу ленты, подгруженное на месте
        vm.setDemoOffline(true)
        await vm.loadMoreFeedNow()
        XCTAssertEqual(NetError.Kind.offline, vm.state.feedPaging.error)
        XCTAssertEqual(40, vm.state.olderFeed.count)
        XCTAssertTrue(vm.state.cacheBytes > 0)
        let localNow = vm.state.feed.count
        vm.clearNetCache()
        XCTAssertEqual(0, vm.state.cacheBytes)
        // Очередь и загрузки не тронуты, лента с телефона — на месте
        XCTAssertEqual(localNow, vm.state.feed.count)
        XCTAssertTrue(local >= localNow)
    }

    func testCancelUploadKeepsPostLocal() async throws {
        let vm = makeModel()
        vm.setDemoOffline(true)
        vm.publish("Фото", media: [photo("ph1", "/tmp/nope.jpg")])
        XCTAssertEqual(1, vm.state.uploads.count)
        let up = try XCTUnwrap(vm.state.uploads.first)
        vm.pauseUpload(up.id)
        XCTAssertEqual(UploadState.paused, vm.state.uploads.first?.state)
        vm.cancelUpload(up.id)
        XCTAssertTrue(vm.state.uploads.isEmpty)
        XCTAssertEqual(0, vm.state.outboxCount)
        XCTAssertEqual("", vm.state.posts.first?.outboxKey)
        XCTAssertEqual("Фото", vm.state.posts.first?.text)
        XCTAssertNil(vm.state.uploadProgress("nobody"))
    }

    func testDevicePrefsRememberNetworkChoices() {
        let defaults = UserDefaults(suiteName: "round22-net")!
        defaults.removePersistentDomain(forName: "round22-net")
        let prefs = DevicePrefs(defaults: defaults)
        XCTAssertEqual(MediaQuality.standard, prefs.mediaQuality)
        XCTAssertFalse(prefs.demoOffline)
        prefs.setMediaQuality(.originalOnWifi)
        prefs.setDemoOffline(true)
        XCTAssertEqual(MediaQuality.originalOnWifi, prefs.mediaQuality)
        XCTAssertTrue(prefs.demoOffline)
        XCTAssertEqual("original", defaults.string(forKey: "mediaQuality"))
    }
}
