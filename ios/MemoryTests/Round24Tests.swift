import XCTest
@testable import Memory

/// Раунд 24: настоящий сервер. Люди с сервера (справочник), свой номер на
/// сервере, время сообщений по часам телефона, новые маршруты демо-сервера.
/// Те же проверки, что Round24Test на Android.
final class Round24Tests: XCTestCase {
    private var clock = TestClock(Date(timeIntervalSince1970: 1_800_000_000))

    /// Часы сети — в мс, как у Android.
    private var now: Int64 { Json.ms(clock.now) }

    override func setUp() {
        super.setUp()
        L10n.shared.setLanguage(.ru)
        L10n.shared.systemLanguage = "ru"
        clock = TestClock(Date(timeIntervalSince1970: 1_800_000_000))
        Directory.clear()
        Directory.selfId = 0
    }

    override func tearDown() {
        Directory.clear()
        Directory.selfId = 0
        super.tearDown()
    }

    private func ms() -> () -> Int64 {
        let clock = self.clock
        return { Json.ms(clock.now) }
    }

    private func makeModel() -> AppModel {
        let clock = self.clock
        return AppModel(clock: { clock.now }, demoReplies: false)
    }

    private func serverUser(_ id: Int, _ name: String, _ handle: String) -> JSONObject {
        ["id": id, "name": name, "handle": handle, "hue": 0.3]
    }

    private func base64(_ bytes: [UInt8]) -> String { Data(bytes).base64EncodedString() }

    private func chatIn(_ vm: AppModel, _ id: Int) -> Chat? { vm.state.chats.first { $0.id == id } }

    func testDirectoryKnowsServerPeople() {
        XCTAssertNil(Demo.person(100_002))
        let key: [UInt8] = (0..<32).map { UInt8($0) }
        var boris = serverUser(100_002, "Борис", "boris")
        boris["publicKey"] = base64(key)
        Directory.remember(boris)
        XCTAssertEqual("Борис", Demo.person(100_002)?.name)
        XCTAssertEqual(key, Directory.key(100_002))
        // Демо-люди — по-прежнему свои
        XCTAssertEqual("Вера Лунная", Demo.person(2)?.name)
        // Ключ не той длины — как будто нет
        var kira = serverUser(100_003, "Кира", "kira")
        kira["publicKey"] = "AAAA"
        Directory.remember(kira)
        XCTAssertNil(Directory.key(100_003))

        let saved = Directory.json()
        Directory.clear()
        XCTAssertNil(Demo.person(100_002))
        Directory.load(saved)
        XCTAssertEqual("boris", Demo.person(100_002)?.handle)
        XCTAssertNotNil(Directory.key(100_002))
    }

    func testOwnServerIdIsMe() throws {
        Directory.selfId = 100_001
        XCTAssertEqual(Demo.me.id, Directory.local(100_001))
        XCTAssertEqual(100_001, Directory.remote(Demo.me.id))
        XCTAssertEqual(100_005, Directory.local(100_005))
        // Себя в справочник не записываем
        XCTAssertNil(Directory.remember(serverUser(100_001, "Я", "me")))

        let c = ApiCodec(me: Demo.me)
        let postJson: JSONObject = ["id": 10_000_001, "author": 100_001, "text": "Моё"]
        let post = try XCTUnwrap(c.postOf(postJson))
        XCTAssertEqual(Demo.me.id, post.author.id)
        let msgJson: JSONObject = ["id": 7, "fromMe": true, "text": "x", "authorId": 100_001, "sentAt": now]
        let msg = try XCTUnwrap(c.messageOf(msgJson))
        XCTAssertEqual(Demo.me.id, msg.authorId)
    }

    func testAuthorInfoFillsDirectoryAndTimeFromSentAt() throws {
        let c = ApiCodec(me: Demo.me)
        var postJson: JSONObject = ["id": 10_000_002, "author": 100_007, "text": "Горы"]
        postJson["authorInfo"] = serverUser(100_007, "Лия", "liya")
        let post = try XCTUnwrap(c.postOf(postJson))
        XCTAssertEqual("Лия", post.author.name)
        var msgJson: JSONObject = ["id": 3, "fromMe": false, "text": "Привет", "time": ""]
        msgJson["authorId"] = 100_008
        msgJson["sentAt"] = now
        msgJson["authorInfo"] = serverUser(100_008, "Ян", "yan")
        let m = try XCTUnwrap(c.messageOf(msgJson))
        XCTAssertNotNil(m.time.range(of: #"^\d\d:\d\d$"#, options: .regularExpression))
        XCTAssertEqual("Ян", Demo.person(m.authorId)?.name)
        // Без sentAt и без времени — пусто, а не падение
        let bare: JSONObject = ["id": 4, "fromMe": false, "text": "x", "time": ""]
        let empty = try XCTUnwrap(c.messageOf(bare))
        XCTAssertEqual("", empty.time)
    }

    func testSavedStateKeepsServerPeople() throws {
        let vm = makeModel()
        Directory.remember(serverUser(100_002, "Борис", "boris"))
        // Запись в файл и чтение — как после перезапуска
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let store = StateStore(directory: dir)
        store.save("anna", vm.snapshot())
        Directory.clear()
        let loaded = try XCTUnwrap(store.load("anna"))
        vm.restore(loaded)
        XCTAssertEqual("Борис", Demo.person(100_002)?.name)
        try? FileManager.default.removeItem(at: dir)
    }

    func testSessionRemembersServerId() {
        let store = MemoryStore()
        let s = Session(store: store, login: "anna")
        let user: JSONObject = ["id": 100_001]
        let answer: JSONObject = ["access": "a", "refresh": "r", "deviceId": "d", "user": user]
        s.save(answer)
        XCTAssertEqual(100_001, Session(store: store, login: "anna").userId)
        // Выход: токенов нет, номер остаётся (записи с ним уже на телефоне)
        s.clear()
        XCTAssertEqual(100_001, Session(store: store, login: "anna").userId)
        XCTAssertEqual(0, Session(store: store, login: "boris").userId)
    }

    func testDemoServerAnswersNewRoutes() async throws {
        let vm = makeModel()
        let me: () -> User = { [weak vm] in vm?.state.me ?? Demo.me }
        let net = Net.demo(clock: ms(), me: me)
        try await net.signIn(login: "wishka", password: "x", device: "iPhone", platform: "ios")
        let found = try await net.api.searchUsers("ве")
        XCTAssertTrue(found.isEmpty)
        // Демо-собеседник с ключом: тот же, что демо-шифрование
        let vera = try await net.api.user(2)
        XCTAssertEqual("Вера Лунная", vera?.name)
        XCTAssertEqual(ChatCrypto.demoPeer(2).publicKey, Directory.key(2))
        let direct = try await net.api.directChat(2)
        XCTAssertEqual(2, direct)
        try await net.api.putKey([UInt8](repeating: 0, count: 32))
        let chats = try await net.api.chats()
        XCTAssertTrue(chats.isEmpty)
    }

    func testSecretNeedsPeersKey() throws {
        let vm = makeModel()
        Directory.remember(serverUser(100_009, "Ева", "eva"))
        let chatId = 1_600_000_000
        var message: JSONObject = ["id": 1, "fromMe": false, "text": "Привет", "time": ""]
        message["authorId"] = 100_009
        message["sentAt"] = now
        message["attachments"] = [JSONObject]()
        vm.onRealtime(.newMessage(chatId: chatId, message: message))
        let chat = try XCTUnwrap(chatIn(vm, chatId))
        XCTAssertEqual("Ева", chat.peer.name)
        vm.setSecret(chatId, true)
        XCTAssertEqual(false, chatIn(vm, chatId)?.secret)
        XCTAssertEqual("У собеседника пока нет ключа шифрования", vm.state.notice)
        // Ключ пришёл — можно
        var eva = serverUser(100_009, "Ева", "eva")
        eva["publicKey"] = base64(ChatKeyPair.generate().publicKey)
        Directory.remember(eva)
        vm.setSecret(chatId, true)
        XCTAssertEqual(true, chatIn(vm, chatId)?.secret)
    }
}
