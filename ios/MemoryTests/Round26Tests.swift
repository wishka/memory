import XCTest
@testable import Memory

/// Раунд 26: вход на сервер с плашки, модерация (жалобы и блокировки на
/// сервере), удаление аккаунта и смена пароля и на сервере. Здесь — то, что
/// проверяется без сервера. Те же проверки, что Round26Test на Android.
final class Round26Tests: XCTestCase {
    private var clock = TestClock(Date(timeIntervalSince1970: 1_800_000_000))

    /// Часы сети — в мс, как у Android.
    private var now: Int64 { Json.ms(clock.now) }

    override func setUp() {
        super.setUp()
        L10n.shared.setLanguage(.ru)
        L10n.shared.systemLanguage = "ru"
        clock = TestClock(Date(timeIntervalSince1970: 1_800_000_000))
    }

    private func makeModel() -> AppModel {
        let clock = self.clock
        return AppModel(clock: { clock.now }, demoReplies: false)
    }

    private func isOk(_ r: AuthResult) -> Bool {
        if case .ok = r { return true }
        return false
    }

    func testCheckChangeDoesNotChangePassword() {
        let store = MemoryStore()
        let ms = now
        let auth = LocalAuth(store: store, clock: { ms }, iterations: 1_000)
        XCTAssertTrue(isOk(auth.register(username: "anna", displayName: "Анна", password: "secret12", repeatPassword: "secret12")))
        let wrong = auth.checkChange(username: "anna", current: "wrong", new: "newpass12", repeatPassword: "newpass12")
        XCTAssertEqual("Текущий пароль не подходит", wrong)
        let mismatch = auth.checkChange(username: "anna", current: "secret12", new: "newpass12", repeatPassword: "other")
        XCTAssertEqual("Пароли не совпадают", mismatch)
        XCTAssertNil(auth.checkChange(username: "anna", current: "secret12", new: "newpass12", repeatPassword: "newpass12"))
        // Проверка ничего не поменяла: старый пароль по-прежнему подходит
        XCTAssertTrue(auth.checkPasswordOf("anna", "secret12"))
        XCTAssertNil(auth.changePassword(username: "anna", current: "secret12", new: "newpass12", repeatPassword: "newpass12"))
        XCTAssertTrue(auth.checkPasswordOf("anna", "newpass12"))
    }

    @MainActor
    func testDemoHasNoServerThings() async {
        let vm = makeModel()
        XCTAssertFalse(vm.state.serverMode)
        // С демо-сервером удалять и менять на сервере нечего
        let deleted = await vm.deleteOnServer("x")
        XCTAssertNil(deleted)
        let changed = await vm.changeServerPassword("x", "y")
        XCTAssertNil(changed)
        // Жалоба на демо-человека остаётся на телефоне: в очередь сети ничего не уходит
        let before = vm.state.outboxCount
        let p = Demo.posts[0]
        let target = ReportTarget(kind: .post, id: p.id, authorId: p.author.id, authorName: p.author.name, preview: p.text)
        vm.report(target, .spam)
        XCTAssertEqual(before, vm.state.outboxCount)
        XCTAssertEqual(1, vm.state.reports.count)
    }
}

/// Раунд 27: сообщества, PRISM и «Актуальное» на сервере. Здесь — то, что
/// проверяется без сервера. Те же проверки, что Round27Test на Android.
final class Round27Tests: XCTestCase {
    private var clock = TestClock(Date(timeIntervalSince1970: 1_800_000_000))

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

    private let codec = ApiCodec(me: Demo.me)

    func testGroupAndPrismSurviveJson() throws {
        let cover = Attachment(id: "c1", kind: .photo, fileName: "/v1/uploads/abc")
        var g = MemoryGroup(id: 50_000_007, name: "Читатели", description: "о книгах", topic: "Книги", members: 3, seed: 7, joined: true)
        g.cover = cover
        g.info = GroupInfo(website: "https://ex.am")
        g.shop = [Product(id: 1, title: "Книга", price: 1500, photos: [cover])]
        g.accent = "mint"
        let back = try XCTUnwrap(codec.groupOf(codec.groupJson(g)))
        XCTAssertEqual(g, back)
        var p = Prism(id: 10_000_001, author: Demo.me, caption: "Закат", music: "трек", seed: 3, likes: 1, comments: 0)
        p.video = Attachment(id: "v1", kind: .video, fileName: "/v1/uploads/v")
        p.createdAt = clock.now
        XCTAssertEqual(p, codec.prismOf(codec.prismJson(p)))
    }

    func testCommunityBodyKeepsOnlyWhatServerStores() {
        let net = Net.demo(clock: ms(), me: { Demo.me })
        var g = MemoryGroup(id: 50_000_007, name: "Читатели", description: "о книгах", topic: "Книги", members: 3, seed: 7, joined: true, mine: true)
        g.ownerId = 1
        g.posts = [Post(id: 1, author: Demo.me, text: "x", minutesAgo: 0)]
        let body = net.api.communityBody(g)
        for k in ["posts", "members", "joined", "mine", "muted", "ownerId", "id"] {
            XCTAssertNil(body[k], k)
        }
        XCTAssertEqual("Читатели", Json.string(body, "name"))
        XCTAssertEqual("о книгах", Json.string(body, "description"))
    }

    func testCommunityPhotosUploadOnce() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let local = dir.appendingPathComponent("cover.jpg")
        try Data(count: 10).write(to: local)
        let net = Net.demo(clock: ms(), me: { Demo.me })
        // Файл с сервера — второй раз не загружается; свой новый — загружается
        XCTAssertEqual("/v1/uploads/abc", net.remoteFor("/v1/uploads/abc"))
        XCTAssertNil(net.remoteFor(local.path))
        var g = MemoryGroup(id: 50_000_007, name: "Читатели", description: "", topic: "Книги", members: 1, seed: 7, mine: true)
        g.cover = Attachment(id: "c1", kind: .photo, fileName: local.path)
        let old = Attachment(id: "p1", kind: .photo, fileName: "/v1/uploads/old")
        g.shop = [Product(id: 1, title: "Книга", photos: [old])]
        let key = net.queueCommunity(g)
        let jobs: [String] = net.uploads.all().filter { $0.owner == key }.map(\.attachmentId)
        XCTAssertEqual(["c1"], jobs)
        let op = try XCTUnwrap(net.outbox.get(key))
        XCTAssertEqual(50_000_007, op.target)
        let payload = try XCTUnwrap(Json.parse(op.payload))
        XCTAssertTrue(Json.objects(payload, "posts").isEmpty)
        try? FileManager.default.removeItem(at: dir)
    }

    func testPersonPostsShowOnTheirPage() {
        let person = Demo.people[0]
        let theirs = Post(id: 10_000_123, author: person, text: "с сервера", minutesAgo: 0)
        var s = UiState()
        s.posts = []
        s.personPosts = [person.id: [theirs]]
        XCTAssertEqual([theirs], s.postsOf(person.id))
        XCTAssertEqual(theirs, s.findPost(theirs.id))
    }

    func testDemoGroupsKeepSmallIds() throws {
        let clock = self.clock
        let vm = AppModel(clock: { clock.now }, demoReplies: false)
        XCTAssertNil(vm.createGroup(name: "Новая группа", description: "", topic: "Книги"))
        let g = try XCTUnwrap(vm.state.groups.first { $0.name == "Новая группа" })
        XCTAssertTrue(g.id <= AppModel.communityIdBase)
        XCTAssertTrue(g.mine && g.joined)
    }

    func testCommunitySavesGoInOrder() async {
        let clock = self.clock
        let outbox = Outbox(cache: MemoryCache(), clock: { Json.ms(clock.now) })
        outbox.enqueue(OutboxOp(key: "a", kind: .saveCommunity, target: 50_000_007, payload: "{}"))
        clock.now = clock.now.addingTimeInterval(0.001)
        outbox.enqueue(OutboxOp(key: "b", kind: .saveCommunity, target: 50_000_007, payload: "{}"))
        outbox.enqueue(OutboxOp(key: "c", kind: .saveCommunity, target: 50_000_008, payload: "{}"))
        var sent: [String] = []
        // «a» ждёт загрузку фото — «b» той же группы не обгоняет, другая группа уходит
        _ = await outbox.flush(ready: { $0.key != "a" }, failedDeps: { _ in false }, send: { _ in nil },
                               onSent: { op, _ in sent.append(op.key) }, onFailed: { _, _ in })
        XCTAssertEqual(["c"], sent)
        _ = await outbox.flush(ready: { _ in true }, failedDeps: { _ in false }, send: { _ in nil },
                               onSent: { op, _ in sent.append(op.key) }, onFailed: { _, _ in })
        XCTAssertEqual(["c", "a", "b"], sent)
    }

    func testDiscardedPendingPostNeverLeaves() {
        let net = Net.demo(clock: ms(), me: { Demo.me })
        let key = net.queuePost(Post(id: 5, author: Demo.me, text: "передумаю", minutesAgo: 0))
        net.discard(key)
        XCTAssertNil(net.outbox.get(key))
    }
}
