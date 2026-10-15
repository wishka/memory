import CryptoKit
import XCTest
@testable import Memory

/// Раунд 23: групповые чаты и живой канал (прочитано, печатает), фоновая
/// отправка и шифрование файлов секретных переписок, вход на сервер и
/// устройства, модерация и запросы на переписку. Те же проверки, что
/// Round23Test на Android, и сверх них — JSON новых полей в формате Android.
final class Round23Tests: XCTestCase {
    private var clock = TestClock(Date(timeIntervalSince1970: 1_800_000_000))

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

    private func makeModel() -> AppModel {
        let clock = self.clock
        return AppModel(clock: { clock.now }, demoReplies: false)
    }

    private func hex(_ d: Data) -> String { d.map { String(format: "%02x", $0) }.joined() }

    private func chatIn(_ vm: AppModel, _ id: Int) -> Chat? { vm.state.chats.first { $0.id == id } }

    /// Первые n друзей — по номеру, чтобы порядок был один и тот же.
    private func pickFriends(_ vm: AppModel, _ n: Int) -> [Int] { Array(vm.state.following.sorted().prefix(n)) }

    // MARK: - Группы

    func testGroupLifecycle() async throws {
        let vm = makeModel()
        let me = vm.state.me.id
        let friends = pickFriends(vm, 3)
        XCTAssertEqual(3, friends.count)
        let following = vm.state.following
        let stranger = try XCTUnwrap(Demo.people.first { !following.contains($0.id) }).id
        XCTAssertNil(vm.createGroup("  ", friends))
        let id = try XCTUnwrap(vm.createGroup("Походы", friends + [stranger]))
        let chat = try XCTUnwrap(chatIn(vm, id))
        XCTAssertTrue(chat.isGroup)
        XCTAssertEqual("Походы", chat.peer.name)
        let group = try XCTUnwrap(chat.group)
        XCTAssertEqual([me] + friends, group.memberIds) // незнакомого не добавить
        XCTAssertTrue(group.isAdmin(me))
        XCTAssertEqual(1, chat.messages.count)
        XCTAssertEqual(AppModel.systemAuthor, chat.messages.first?.authorId)
        await vm.pumpNet()
        XCTAssertEqual(0, vm.state.outboxCount) // группа создана на сервере

        vm.renameGroup(id, "Походы 2026")
        XCTAssertEqual("Походы 2026", chatIn(vm, id)?.peer.name)
        vm.removeGroupMember(id, friends[0])
        vm.removeGroupMember(id, me) // создателя не убрать
        vm.setGroupAdmin(id, friends[1], true)
        let g = try XCTUnwrap(chatIn(vm, id)?.group)
        XCTAssertEqual([me, friends[1], friends[2]], g.memberIds)
        XCTAssertTrue(g.isAdmin(friends[1]))
        vm.addGroupMembers(id, [friends[0]])
        XCTAssertTrue(chatIn(vm, id)?.group?.memberIds.contains(friends[0]) == true)
        await vm.pumpNet()
        XCTAssertEqual(0, vm.state.outboxCount)
        // Сервер прислал новый состав (кто-то другой поменял) — применяем
        vm.onRealtime(.groupChanged(chatId: id, title: "Горы", memberIds: [me, friends[2]], adminIds: [me]))
        XCTAssertEqual("Горы", chatIn(vm, id)?.peer.name)
        XCTAssertEqual([me, friends[2]], chatIn(vm, id)?.group?.memberIds)
        // Группы — без сквозного шифрования
        vm.setSecret(id, true)
        XCTAssertEqual(false, chatIn(vm, id)?.secret)
        vm.leaveGroup(id)
        XCTAssertNil(chatIn(vm, id))
        // Нас убрали из другой группы — она пропадает из списка
        let other = try XCTUnwrap(vm.createGroup("Ещё", [friends[1]]))
        vm.onRealtime(.groupChanged(chatId: other, title: "Ещё", memberIds: [friends[1]], adminIds: [friends[1]]))
        XCTAssertNil(chatIn(vm, other))
    }

    // MARK: - Живой канал

    func testReadReceiptsAndTyping() async throws {
        let vm = makeModel()
        let chat = try XCTUnwrap(vm.state.chats.first { !$0.isGroup })
        vm.send(chat.id, "Привет!")
        await vm.pumpNet()
        let sent = try XCTUnwrap(chatIn(vm, chat.id)?.messages.last)
        XCTAssertTrue(sent.delivered)
        XCTAssertTrue(sent.sentAt > 0)
        XCTAssertTrue(sent.readBy.isEmpty)
        // Собеседник читает через пару секунд — событием живого канала
        XCTAssertEqual(0, vm.tickRealtime())
        now += 3_000
        XCTAssertEqual(1, vm.tickRealtime())
        XCTAssertEqual([chat.peer.id], chatIn(vm, chat.id)?.messages.last?.readBy)
        // «Печатает» в группе — по именам
        let friends = pickFriends(vm, 2)
        let gid = try XCTUnwrap(vm.createGroup("Двое", friends))
        vm.onRealtime(.typing(chatId: gid, userId: friends[0], on: true))
        vm.onRealtime(.typing(chatId: gid, userId: friends[1], on: true))
        let group = try XCTUnwrap(chatIn(vm, gid))
        let title = try XCTUnwrap(vm.state.typingTitle(group))
        XCTAssertTrue(title.hasSuffix("печатают…"))
        // Пришло сообщение — автор больше не печатает
        var m = Message(id: 0, fromMe: false, text: "Иду!", time: "12:00")
        m.authorId = friends[0]
        m.sentAt = now
        vm.onRealtime(.newMessage(chatId: gid, message: ApiCodec(me: vm.state.me).messageJson(m)))
        let gchat = try XCTUnwrap(chatIn(vm, gid))
        XCTAssertEqual(Set([friends[1]]), gchat.typingIds)
        XCTAssertEqual(friends[0], gchat.messages.last?.authorId)
        XCTAssertEqual("Иду!", gchat.messages.last?.text)
        // Свой «печатает» — не чаще раза в 3 секунды
        let rt = try XCTUnwrap(vm.net.realtime as? DemoRealtime)
        vm.typing(gid)
        vm.typing(gid)
        XCTAssertEqual(1, rt.sent.filter(\.isTyping).count)
        // Открыли переписку — «прочитано» до последнего пришедшего
        vm.openChat(gid)
        XCTAssertTrue(rt.sent.contains(.read(chatId: gid, userId: vm.state.me.id, upTo: now)))
        // События — в JSON и обратно
        let events: [RtEvent] = [
            .read(chatId: 1, userId: 2, upTo: 3),
            .typing(chatId: 1, userId: 2, on: true),
            .groupChanged(chatId: 5, title: "x", memberIds: [1, 2], adminIds: [1]),
        ]
        for event in events {
            XCTAssertEqual(event, RtEvent.parse(Json.string(event.json())))
        }
        XCTAssertNil(RtEvent.parse("{\"type\":\"?\"}"))
        let read = try XCTUnwrap(Json.parse(Json.string(RtEvent.read(chatId: 12, userId: 5, upTo: 1_800_000_003_000).json())))
        XCTAssertEqual("read", Json.string(read, "type"))
        XCTAssertEqual(12, Json.int(read, "chat"))
        XCTAssertEqual(5, Json.int(read, "user"))
        XCTAssertEqual(1_800_000_003_000, Json.long(read, "upTo"))
    }

    // MARK: - Запросы на переписку

    func testStrangersLandInRequests() throws {
        let vm = makeModel()
        let s0 = vm.state
        let stranger = try XCTUnwrap(Demo.people.first { p in
            !s0.following.contains(p.id) && !s0.chats.contains { $0.peer.id == p.id }
        })
        let codec = ApiCodec(me: vm.state.me)
        func write(_ text: String) {
            var m = Message(id: 0, fromMe: false, text: text, time: "10:00")
            m.authorId = stranger.id
            vm.onRealtime(.newMessage(chatId: AppModel.strangerChatBase + stranger.id, message: codec.messageJson(m)))
        }
        // «Никто не может писать» — чужое не приходит вовсе
        var privacy = vm.state.privacy
        privacy.messages = .nobody
        vm.setPrivacy(privacy)
        write("Эй")
        XCTAssertFalse(vm.state.chats.contains { $0.peer.id == stranger.id })
        privacy.messages = .everyone
        vm.setPrivacy(privacy)
        let unreadBefore = vm.state.unreadTotal
        write("Здравствуйте!")
        XCTAssertEqual(1, vm.state.requestChats.count)
        XCTAssertFalse(vm.state.chatList.contains { $0.request })
        XCTAssertEqual(unreadBefore, vm.state.unreadTotal) // запросы не считаются в непрочитанные
        // Заблокировать из запроса — больше не приходит
        let request = try XCTUnwrap(vm.state.requestChats.first)
        vm.blockRequest(request.id)
        XCTAssertTrue(vm.state.isBlocked(stranger.id))
        write("Эй!")
        XCTAssertTrue(vm.state.requestChats.isEmpty)
        // Разблокировали, написал снова — приняли: переписка как обычно
        vm.unblock(stranger.id)
        write("Это снова я")
        let again = try XCTUnwrap(vm.state.requestChats.first)
        vm.acceptRequest(again.id)
        XCTAssertTrue(vm.state.requestChats.isEmpty)
        XCTAssertTrue(vm.state.chatList.contains { $0.peer.id == stranger.id })
    }

    // MARK: - Модерация

    func testModerationCatchesTricks() {
        let p = ModerationPrefs(hiddenWords: ["спойлер", "Кот"])
        XCTAssertEqual(HiddenReason.word, Moderation.check("Без СПОЙЛЕРОВ, пожалуйста", p))
        XCTAssertEqual(HiddenReason.word, Moderation.check("коты лучше", p))
        XCTAssertNil(Moderation.check("скот на ферме", p)) // не с середины слова
        XCTAssertEqual(HiddenReason.offensive, Moderation.check("ты дуурак", p))
        XCTAssertEqual(HiddenReason.offensive, Moderation.check("ну ты и ид10т", p))
        XCTAssertEqual(HiddenReason.offensive, Moderation.check("what an IDIOT", p))
        var mild = p
        mild.hideOffensive = false
        XCTAssertNil(Moderation.check("ты дура", mild))
        XCTAssertEqual(HiddenReason.spam, Moderation.check("Заработок без вложений http://a.ru", p))
        XCTAssertEqual(HiddenReason.spam, Moderation.check("a http://1.ru http://2.ru http://3.ru", p))
        XCTAssertEqual(HiddenReason.spam, Moderation.check("купи купи купи купи купи купи", p))
        XCTAssertNil(Moderation.check("Обычный комментарий про горы", p))
        XCTAssertNil(Moderation.check("   ", p))
        XCTAssertEqual(["спойлер"], Moderation.addWord(Moderation.addWord([], " спойлер "), "СПОЙЛЕР"))
        XCTAssertEqual([], Moderation.addWord([], "   "))
        XCTAssertEqual(Moderation.maxWord, Moderation.addWord([], String(repeating: "я", count: 100)).first?.count)
        // Лимит частоты
        let rl = RateLimiter(maxEvents: 2, windowMs: 1000)
        XCTAssertTrue(rl.allow(0))
        XCTAssertTrue(rl.allow(10))
        XCTAssertFalse(rl.allow(20))
        XCTAssertTrue(rl.allow(1001))
    }

    func testModerationInApp() throws {
        let vm = makeModel()
        let s0 = vm.state
        vm.addHiddenWord("спойлер")
        XCTAssertEqual(["спойлер"], vm.state.privacy.hiddenWords)
        let stranger = try XCTUnwrap(Demo.people.first { !s0.following.contains($0.id) }).id
        let friendChat = try XCTUnwrap(s0.chats.first { !$0.isGroup && s0.following.contains($0.peer.id) })
        XCTAssertEqual(HiddenReason.word, vm.state.hiddenReason("будет спойлер", stranger))
        XCTAssertNil(vm.state.hiddenReason("будет спойлер", s0.me.id)) // своё не скрываем
        XCTAssertNil(vm.state.hiddenReason("будет спойлер", friendChat.peer.id, chat: friendChat)) // друг в переписке
        XCTAssertNil(vm.state.hiddenReason("будет спойлер", AppModel.systemAuthor))
        vm.removeHiddenWord("спойлер")
        XCTAssertNil(vm.state.hiddenReason("будет спойлер", stranger))
        // Комментарии выключены — чужие не добавить, свои можно
        vm.publish("Моя запись")
        let post = try XCTUnwrap(vm.state.posts.first)
        vm.toggleComments(post.id)
        XCTAssertEqual(true, vm.state.posts.first?.commentsOff)
        vm.addComment(post.id, "Мой комментарий")
        XCTAssertEqual(1, vm.state.posts.first?.commentList.count)
        // Не чаще 10 комментариев в минуту
        for i in 0..<12 { vm.addComment(post.id, "флуд \(i)") }
        XCTAssertEqual(10, vm.state.posts.first?.commentList.count)
        XCTAssertNotNil(vm.state.notice)
        vm.dismissNotice()
        XCTAssertNil(vm.state.notice)
        now += 61_000
        vm.addComment(post.id, "через минуту")
        XCTAssertEqual(11, vm.state.posts.first?.commentList.count)
        // Чужое — не выключить
        let meId = s0.me.id
        let foreign = try XCTUnwrap(vm.state.posts.first { $0.author.id != meId })
        vm.toggleComments(foreign.id)
        XCTAssertEqual(false, vm.state.posts.first { $0.id == foreign.id }?.commentsOff)
    }

    // MARK: - Шифрование файлов

    func testFileCryptoMatchesVectorAndDetectsTampering() throws {
        let keyBytes: [UInt8] = Array(0..<32)
        let key = FileKey(key: keyBytes, prefix: [9, 8, 7, 6])
        let data = Data("Memory secret file: 0123456789abcdef!".utf8)
        let enc = try FileCrypto.encrypt(data, key, chunk: 16)
        // Вектор посчитан отдельно (Python cryptography) — так же шифрует и Android
        XCTAssertEqual(94, enc.count)
        let digest = Data(SHA256.hash(data: enc))
        XCTAssertEqual("661585dac000a2260cc1c8d5d115fb6e1d7ce3f6f594dc764db0fa95e892b74f", hex(digest))
        XCTAssertEqual(data, try FileCrypto.decrypt(enc, key))
        // Пустой и ровно кратный куску
        let empty = try FileCrypto.encrypt(Data(), key, chunk: 16)
        XCTAssertTrue(try FileCrypto.decrypt(empty, key).isEmpty)
        let even = Data(repeating: 1, count: 32)
        let evenEnc = try FileCrypto.encrypt(even, key, chunk: 16)
        XCTAssertEqual(even, try FileCrypto.decrypt(evenEnc, key))
        func fails(_ b: Data) -> Bool { (try? FileCrypto.decrypt(b, key)) == nil }
        XCTAssertTrue(fails(Data(enc.prefix(enc.count - 21)))) // отрезали последний кусок
        var flipped = enc
        flipped[20] = flipped[20] &+ 1
        XCTAssertTrue(fails(flipped)) // подменили байт
        var swapped = Data()
        swapped.append(enc.subdata(in: 0..<9))
        swapped.append(enc.subdata(in: 41..<73))
        swapped.append(enc.subdata(in: 9..<41))
        swapped.append(enc.subdata(in: 73..<enc.count))
        XCTAssertTrue(fails(swapped)) // переставили куски
        XCTAssertTrue(fails(try FileCrypto.encrypt(data, FileKey.random(), chunk: 16)))
        XCTAssertTrue(fails(Data("not memf".utf8)))
        XCTAssertEqual(key, FileKey.decode(key.encoded()))
        XCTAssertNil(FileKey.decode("коротко"))
        XCTAssertNotEqual(FileKey.random(), FileKey.random())
    }

    func testFileCryptoStreamsFiles() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("r23-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: dir) }
        // Больше одного куска 64 КиБ и не кратно ему
        let counter: [Int] = Array(0..<200_000)
        let raw: [UInt8] = counter.map { (i: Int) -> UInt8 in UInt8(truncatingIfNeeded: i &* 31) }
        let bytes = Data(raw)
        let src = dir.appendingPathComponent("video.mp4")
        let enc = dir.appendingPathComponent("video.memf")
        let back = dir.appendingPathComponent("video-back.mp4")
        try bytes.write(to: src)
        let key = FileKey.random()
        try FileCrypto.encryptFile(src, enc, key)
        let sealed = try Data(contentsOf: enc)
        // Заголовок 9 байт + 4 куска (3 полных и хвост), у каждого тег 16 байт
        let expectedSize: Int = 9 + 200_000 + 64
        XCTAssertEqual(expectedSize, sealed.count)
        XCTAssertEqual(sealed, try FileCrypto.encrypt(bytes, key)) // кусками — то же, что целиком
        try FileCrypto.decryptFile(enc, back, key)
        XCTAssertEqual(bytes, try Data(contentsOf: back))
        // Чужой ключ — ошибка и не остаётся недорасшифрованного файла
        XCTAssertThrowsError(try FileCrypto.decryptFile(enc, back, FileKey.random()))
        XCTAssertFalse(FileManager.default.fileExists(atPath: back.path))
    }

    func testSecretChatUploadsOnlyCiphertext() async throws {
        let vm = makeModel()
        let chat = try XCTUnwrap(vm.state.chats.first { !$0.isGroup })
        vm.setSecret(chat.id, true)
        let file = FileManager.default.temporaryDirectory.appendingPathComponent("secret-\(UUID().uuidString).jpg")
        let counter: [Int] = Array(0..<3000)
        let raw: [UInt8] = counter.map { (i: Int) -> UInt8 in UInt8(i % 7) }
        let bytes = Data(raw)
        try bytes.write(to: file)
        defer { try? FileManager.default.removeItem(at: file) }
        vm.send(chat.id, "Смотри фото", attachments: [Attachment(id: "s1", kind: .photo, fileName: file.path)])
        let encodedKey = try XCTUnwrap(vm.lastFileKeys["s1"])
        let key = try XCTUnwrap(FileKey.decode(encodedKey))
        // На телефоне — как было: своё фото и путь к нему
        XCTAssertEqual(file.path, chatIn(vm, chat.id)?.messages.last?.attachments.first?.fileName)
        await vm.pumpNet()
        let page = try await vm.net.api.messages(chat.id, nil)
        XCTAssertEqual(1, page.items.count)
        let onServer = try XCTUnwrap(page.items.first)
        let att = try XCTUnwrap(onServer.attachments.first)
        XCTAssertTrue(att.name.hasPrefix(FileCrypto.sealed)) // ключ — только запечатанным
        XCTAssertFalse(att.name.contains(encodedKey))
        let demo = try XCTUnwrap(vm.net.demo)
        let stored = try XCTUnwrap(demo.uploaded(att.fileName))
        XCTAssertNotEqual(bytes, stored)
        XCTAssertEqual(bytes, try FileCrypto.decrypt(stored, key))
        XCTAssertFalse(onServer.text.contains("Смотри"))
        // Имя файла на сервере ничего не говорит
        XCTAssertFalse(demo.uploadName(att.fileName)?.contains("secret") ?? true)
    }

    // MARK: - Вход и устройства

    func testSessionRefreshesAndDevices() async throws {
        let store = MemoryStore()
        let me = Demo.me
        let n = Net.demo(clock: ms(), me: { me }, tokens: store, login: "Anna")
        XCTAssertFalse(n.session.signedIn)
        // Без входа — 401
        do {
            _ = try await n.api.devices()
            XCTFail("ждали 401")
        } catch let e as NetError {
            XCTAssertEqual(NetError.Kind.auth, e.kind)
        }
        try await n.signIn(login: "anna", password: "secret", device: "iPhone 15", platform: "ios")
        XCTAssertTrue(n.session.signedIn)
        XCTAssertFalse(store.values.isEmpty)
        XCTAssertTrue(store.values.keys.allSatisfy { $0.hasPrefix("session.api.anna.") }) // в Связке ключей (SecureStore: «session.…»)
        let list = try await n.api.devices()
        XCTAssertEqual(3, list.count)
        XCTAssertEqual("iPhone 15", list.first { $0.current }?.name)
        // Токен доступа истёк — Api сам обновил и повторил
        let oldAccess = n.session.access
        let demo = try XCTUnwrap(n.demo)
        demo.expireAccessTokens()
        let renewed = try await n.api.devices()
        XCTAssertEqual(3, renewed.count)
        XCTAssertNotEqual(oldAccess, n.session.access)
        // Второй телефон того же человека; «Выйти на всех, кроме этого» — его сессия закрыта
        let phone2 = Net(transport: demo, cache: MemoryCache(), clock: ms(), me: { me }, tokens: MemoryStore(), login: "anna")
        try await phone2.signIn(login: "anna", password: "secret", device: "iPad", platform: "ios")
        try await n.api.revokeOtherDevices()
        let one = try await n.api.devices()
        XCTAssertEqual(1, one.count)
        demo.expireAccessTokens()
        do {
            _ = try await phone2.api.devices()
            XCTFail("ждали 401")
        } catch let e as NetError {
            XCTAssertEqual(NetError.Kind.auth, e.kind)
        }
        XCTAssertFalse(phone2.session.signedIn) // обновить нельзя — сессия стёрта
        // Без токена сервер не пускает
        let raw = try await demo.send(HttpRequest(method: "GET", path: "/v1/devices"))
        XCTAssertEqual(401, raw.code)
        // Сессия переживает перезапуск
        let again = Net.demo(clock: ms(), me: { me }, tokens: store, login: "anna")
        XCTAssertTrue(again.session.signedIn)
        // Выйти — токены стёрты
        await n.api.signOut()
        XCTAssertFalse(n.session.signedIn)
        XCTAssertTrue(store.values.isEmpty)
    }

    /// Как staleTokensSignInAgain на Android: токены на телефоне есть, а сервер их
    /// не знает — startSessionNow один раз входит заново, вход не закрыт.
    func testStaleTokensSignInAgain() async throws {
        let store = MemoryStore()
        let vm = makeModel()
        let me1: () -> User = { [weak vm] in vm?.state.me ?? Demo.me }
        vm.attachNet(Net.demo(clock: ms(), me: me1, tokens: store, login: "demo"))
        await vm.startSessionNow(login: "demo", password: "x", fresh: false)
        XCTAssertTrue(vm.net.session.signedIn)
        // «Перезапуск»: токены на телефоне есть, а демо-сервер новый и их не знает
        let vm2 = makeModel()
        let me2: () -> User = { [weak vm2] in vm2?.state.me ?? Demo.me }
        vm2.attachNet(Net.demo(clock: ms(), me: me2, tokens: store, login: "demo"))
        XCTAssertTrue(vm2.net.session.signedIn)
        await vm2.startSessionNow(login: "demo", password: nil, fresh: false)
        XCTAssertFalse(vm2.state.signedOut)
        XCTAssertTrue(vm2.net.session.signedIn)
        let demo = try XCTUnwrap(vm2.net.demo)
        XCTAssertEqual(1, demo.deviceCount() - 2) // вошли заново этим телефоном
    }

    func testOutboxWaitsWhenSignedOut() async throws {
        let vm = makeModel()
        await vm.startSessionNow(login: "demo", password: "x", fresh: false)
        XCTAssertTrue(vm.net.session.signedIn)
        await vm.pumpNet()
        XCTAssertEqual(0, vm.state.outboxCount)
        // Сессию закрыли на сервере — очередь ждёт, ничего не теряем
        vm.net.session.clear()
        let body: JSONObject = ["name": "X"]
        vm.net.queueCall("PUT", "/v1/me", body: body)
        await vm.pumpNet()
        XCTAssertTrue(vm.state.signedOut)
        XCTAssertEqual(1, vm.state.outboxCount)
        // Вошли снова — ушло сразу, без паузы
        await vm.startSessionNow(login: "demo", password: "x", fresh: false)
        XCTAssertFalse(vm.state.signedOut)
        await vm.pumpNet()
        XCTAssertEqual(0, vm.state.outboxCount)
    }

    func testDevicesInApp() async throws {
        let vm = makeModel()
        await vm.loadDevicesNow()
        XCTAssertNotNil(vm.state.notice) // без входа — не загрузить
        XCTAssertTrue(vm.state.signedOut)
        vm.dismissNotice()
        await vm.startSessionNow(login: "demo", password: "x", fresh: false)
        await vm.loadDevicesNow()
        XCTAssertEqual(3, vm.state.devices.count)
        XCTAssertEqual(true, vm.state.devices.first?.current) // это устройство — первым
        let other = try XCTUnwrap(vm.state.devices.last)
        await vm.revokeDeviceNow(other.id)
        XCTAssertEqual(2, vm.state.devices.count)
        await vm.revokeOtherDevicesNow()
        XCTAssertEqual(1, vm.state.devices.count)
    }

    func testProfileSyncsBetweenPhones() async throws {
        // Первый телефон: вошли, поменяли профиль — ушло на сервер
        let vm1 = makeModel()
        await vm1.startSessionNow(login: "demo", password: "x", fresh: false)
        vm1.updateProfile(name: "Анна Новая", info: ProfileInfo(bio: "Люблю горы"))
        vm1.addHiddenWord("спойлер")
        await vm1.pumpNet()
        XCTAssertEqual(0, vm1.state.outboxCount)
        let server = try XCTUnwrap(vm1.net.demo)
        // Второй телефон, тот же сервер: новый аккаунт на телефоне — профиль приехал
        let vm2 = makeModel()
        let me2: () -> User = { [weak vm2] in vm2?.state.me ?? Demo.me }
        vm2.attachNet(Net(transport: server, cache: MemoryCache(), clock: ms(), me: me2, tokens: MemoryStore(), login: "demo"))
        await vm2.startSessionNow(login: "demo", password: "x", fresh: true)
        XCTAssertEqual("Анна Новая", vm2.state.me.name)
        XCTAssertEqual("Люблю горы", vm2.state.me.bio)
        XCTAssertEqual(["спойлер"], vm2.state.privacy.hiddenWords)
    }

    // MARK: - Фоновая отправка

    func testReconcileAfterBackgroundSend() async throws {
        let vm = makeModel()
        let cache = MemoryCache()
        let server = try XCTUnwrap(vm.net.demo)
        let me: () -> User = { [weak vm] in vm?.state.me ?? Demo.me }
        vm.attachNet(Net(transport: server, cache: cache, clock: ms(), me: me))
        vm.setDemoOffline(true)
        vm.publish("Ушла в фоне")
        let chat = try XCTUnwrap(vm.state.chats.first { !$0.isGroup })
        vm.send(chat.id, "И это")
        XCTAssertFalse(vm.state.posts.first?.outboxKey.isEmpty ?? true)
        // «Приложение закрыто»: очередь отправил фоновый процесс (свой Net поверх той же базы)
        let worker = Net(transport: server, cache: cache, clock: ms(), me: me)
        server.online = true
        _ = await worker.pump()
        XCTAssertEqual(0, worker.outbox.count)
        // Приложение открылось снова — сверка с очередью
        vm.attachNet(Net(transport: server, cache: cache, clock: ms(), me: me))
        XCTAssertEqual("", vm.state.posts.first?.outboxKey)
        XCTAssertEqual(false, chatIn(vm, chat.id)?.messages.last?.pending)
        XCTAssertEqual(true, chatIn(vm, chat.id)?.messages.last?.delivered)
    }

    func testDevicePrefsRememberLastAccount() {
        let defaults = UserDefaults(suiteName: "round23-bg")!
        defaults.removePersistentDomain(forName: "round23-bg")
        let prefs = DevicePrefs(defaults: defaults)
        XCTAssertNil(prefs.lastLogin)
        prefs.setLastAccount("anna", meId: 7, meName: "Анна")
        XCTAssertEqual("anna", prefs.lastLogin)
        XCTAssertEqual(7, prefs.lastMeId)
        XCTAssertEqual("Анна", prefs.lastMeName)
        prefs.setLastAccount(nil)
        XCTAssertNil(prefs.lastLogin)
        XCTAssertEqual("ru.wishka.memory.net", BackgroundNet.identifier)
    }

    // MARK: - JSON новых полей — как у Android

    func testNewFieldsUseAndroidKeys() throws {
        let codec = ApiCodec(me: Demo.me)
        var m = Message(id: 5, fromMe: true, text: "x", time: "12:00")
        m.authorId = 7
        m.delivered = true
        m.readBy = [3, 4]
        m.sentAt = 1_800_000_000_123
        let mj = codec.messageJson(m)
        XCTAssertEqual(7, Json.int(mj, "authorId"))
        XCTAssertTrue(Json.bool(mj, "delivered"))
        XCTAssertEqual([3, 4], Json.ints(mj, "readBy"))
        XCTAssertEqual(1_800_000_000_123, Json.long(mj, "sentAt"))
        let back = try XCTUnwrap(codec.messageOf(Json.parse(Json.string(mj)) ?? [:]))
        XCTAssertEqual(m, back)
        // Старый JSON без новых полей читается
        var old = mj
        old.removeValue(forKey: "readBy")
        old.removeValue(forKey: "authorId")
        let oldMessage = try XCTUnwrap(codec.messageOf(old))
        XCTAssertEqual([], oldMessage.readBy)
        XCTAssertEqual(0, oldMessage.authorId)

        var post = Post(id: 1, author: Demo.me, text: "p", minutesAgo: 0)
        post.commentsOff = true
        XCTAssertTrue(Json.bool(codec.postJson(post), "commentsOff"))
        XCTAssertEqual(true, codec.postOf(codec.postJson(post))?.commentsOff)

        var privacy = Privacy()
        privacy.messages = .following
        privacy.hiddenWords = ["спойлер"]
        privacy.hideSpam = false
        let pj = codec.privacyJson(privacy)
        XCTAssertEqual("Following", Json.string(pj, "messages"))
        XCTAssertEqual("Everyone", Json.string(pj, "calls"))
        XCTAssertEqual(["спойлер"], Json.strings(pj, "hiddenWords"))
        XCTAssertFalse(Json.bool(pj, "hideSpam"))
        let restored = codec.privacyOf(pj)
        XCTAssertEqual(Audience.following, restored.messages)
        XCTAssertEqual(["спойлер"], restored.hiddenWords)
        XCTAssertFalse(restored.hideSpam)
        XCTAssertTrue(restored.hideOffensive)
        XCTAssertTrue(restored.personalRecs)
        // Нет полей — фильтр включён
        XCTAssertTrue(codec.privacyOf([:]).hideOffensive)
        XCTAssertTrue(codec.privacyOf([:]).hideSpam)

        // Загрузка с ключом файла и запрос из очереди
        var job = UploadJob(id: "u1", attachmentId: "a1", owner: "o1", path: "/tmp/x.jpg")
        job.secretKey = "k"
        XCTAssertEqual("k", UploadJob.of(job.json())?.secretKey)
        let n = Net.demo(clock: ms(), me: { Demo.me })
        n.queueCall("PUT", "/v1/me", body: ["name": "A"], coalesce: "me")
        n.queueCall("PUT", "/v1/me", body: ["name": "B"], coalesce: "me")
        n.queueCall("POST", "/v1/chats/1/accept")
        XCTAssertEqual(2, n.outbox.count) // профиль — только последний
        let op = try XCTUnwrap(n.outbox.all().first)
        XCTAssertEqual(OpKind.call, op.kind)
        XCTAssertEqual("call", op.json()["kind"] as? String)
        let payload = try XCTUnwrap(Json.parse(op.payload))
        XCTAssertEqual("B", Json.object(payload, "body").map { Json.string($0, "name") })

        // Снимок переписки-группы переживает сохранение
        var chat = Chat(id: 9, peer: Demo.me, messages: [m])
        chat.group = ChatGroup(title: "G", memberIds: [1, 2], adminIds: [1], ownerId: 1)
        chat.request = true
        let data = try JSONEncoder().encode(chat)
        let decoded = try JSONDecoder().decode(Chat.self, from: data)
        XCTAssertEqual(chat, decoded)
        XCTAssertEqual(-9, chat.group?.asUser(9).id)
    }
}
