import CryptoKit
import XCTest
@testable import Memory

/// Раунд 20: восстановление доступа, лента активности, поиск, хештеги и
/// упоминания, правка записи, ссылки, выгрузка данных, отчёты о сбоях,
/// исчезающие и зашифрованные сообщения, эфиры. Те же проверки, что
/// Round20Test на Android (кроме проверки подписей значков — она про Compose).
final class Round20Tests: XCTestCase {
    private var nowMs: Int64 = 1_000_000
    private let store = MemoryStore()
    private let mail = RecordingMail()

    override func setUp() {
        super.setUp()
        L10n.shared.setLanguage(.ru)
        L10n.shared.systemLanguage = "ru"
    }

    override func tearDown() {
        Telemetry.sink = nil
        Telemetry.crashesAllowed = false
        Telemetry.analyticsAllowed = false
        _ = Telemetry.drain()
        super.tearDown()
    }

    private func auth() -> LocalAuth {
        LocalAuth(store: store, clock: { [unowned self] in self.nowMs }, iterations: 1_000, mail: mail)
    }

    private func error(_ result: AuthResult) -> String {
        if case .error(let message) = result { return message }
        XCTFail("ожидалась ошибка, а пришло \(result)")
        return ""
    }

    private func isOk(_ result: AuthResult) -> Bool {
        if case .ok = result { return true }
        return false
    }

    private func ms(_ value: Int64) -> Date { Date(timeIntervalSince1970: Double(value) / 1000) }

    private func matches(_ pattern: String, _ s: String) -> Bool {
        guard let re = try? NSRegularExpression(pattern: pattern) else { return false }
        return re.firstMatch(in: s, range: NSRange(location: 0, length: (s as NSString).length)) != nil
    }

    // MARK: - 1. Восстановление доступа

    func testRecoveryCodesAreOneTimeAndHashed() {
        let a = auth()
        XCTAssertTrue(isOk(a.register(username: "anna", displayName: "Анна", password: "secret123", repeatPassword: "secret123")))
        let codes = a.newRecoveryCodes("anna")
        XCTAssertEqual(LocalAuth.recoveryCodes, codes.count)
        XCTAssertEqual(codes.count, Set(codes).count)
        XCTAssertTrue(codes.allSatisfy { matches("^[A-Z2-9]{4}-[A-Z2-9]{4}$", $0) })
        // Сами коды нигде не лежат
        XCTAssertFalse(store.values.values.contains { v in codes.contains { v.contains(RecoveryCodes.normalize($0)) || v.contains($0) } })
        a.logout()

        // Код подходит в любом написании — и только один раз
        let typed = codes[3].lowercased().replacingOccurrences(of: "-", with: " ")
        XCTAssertTrue(isOk(a.recoverWithCode(username: "anna", code: typed, new: "newpass99", repeatPassword: "newpass99")))
        XCTAssertEqual(9, a.recoveryLeft("anna"))
        a.logout()
        XCTAssertEqual("Неверный логин или код", error(a.recoverWithCode(username: "anna", code: codes[3], new: "other1234", repeatPassword: "other1234")))
        // Новый пароль работает, старый — нет
        XCTAssertTrue(isOk(a.login(username: "anna", password: "newpass99")))
        a.logout()
        XCTAssertFalse(isOk(a.login(username: "anna", password: "secret123")))
    }

    func testRecoveryChecksPasswordRulesAndLocks() {
        let a = auth()
        _ = a.register(username: "boris", displayName: "Борис", password: "secret123", repeatPassword: "secret123")
        let codes = a.newRecoveryCodes("boris")
        a.logout()
        XCTAssertEqual("Пароль — от 8 символов", error(a.recoverWithCode(username: "boris", code: codes[0], new: "short1", repeatPassword: "short1")))
        XCTAssertEqual("Пароли не совпадают", error(a.recoverWithCode(username: "boris", code: codes[0], new: "secret999", repeatPassword: "secret998")))
        // Подбор кода упирается в ту же блокировку, что и вход
        for _ in 0..<LocalAuth.maxFailures {
            _ = a.recoverWithCode(username: "boris", code: "AAAA-AAAA", new: "secret999", repeatPassword: "secret999")
        }
        XCTAssertTrue(error(a.recoverWithCode(username: "boris", code: codes[0], new: "secret999", repeatPassword: "secret999")).hasPrefix("Слишком много попыток"))
        nowMs += LocalAuth.lockMs + 1
        XCTAssertTrue(isOk(a.recoverWithCode(username: "boris", code: codes[0], new: "secret999", repeatPassword: "secret999")))
        // Новые коды заменяют старые
        let fresh = a.newRecoveryCodes("boris")
        a.logout()
        XCTAssertFalse(isOk(a.recoverWithCode(username: "boris", code: codes[1], new: "secret777", repeatPassword: "secret777")))
        XCTAssertTrue(isOk(a.recoverWithCode(username: "boris", code: fresh[1], new: "secret777", repeatPassword: "secret777")))
    }

    func testEmailIsConfirmedByCodeAndRestoresAccess() {
        let a = auth()
        _ = a.register(username: "vera", displayName: "Вера", password: "secret123", repeatPassword: "secret123")
        XCTAssertEqual("Похоже, в адресе почты ошибка", a.setEmail("vera", "vera@"))
        XCTAssertNil(a.setEmail("vera", " Vera@Mail.ru "))
        XCTAssertEqual("vera@mail.ru", a.email("vera"))
        XCTAssertFalse(a.emailConfirmed("vera"))
        let code = mail.sent.last!.code
        XCTAssertEqual("vera@mail.ru", mail.sent.last!.email)
        XCTAssertEqual("Код не подходит", a.confirmEmail("vera", code == "000000" ? "111111" : "000000"))
        XCTAssertNil(a.confirmEmail("vera", code))
        XCTAssertTrue(a.emailConfirmed("vera"))
        XCTAssertEqual("v•••@mail.ru", a.maskedEmail("vera"))
        a.logout()

        // Ответ одинаковый, есть почта или нет; письмо — только при подтверждённой
        let before = mail.sent.count
        let reply = a.startEmailRecovery("nobody")
        XCTAssertEqual(reply, a.startEmailRecovery("vera"))
        XCTAssertEqual(before + 1, mail.sent.count)
        let reset = mail.sent.last!.code
        XCTAssertTrue(isOk(a.recoverWithEmail(username: "vera", code: reset, new: "newpass99", repeatPassword: "newpass99")))
        // Код из письма одноразовый
        a.logout()
        XCTAssertFalse(isOk(a.recoverWithEmail(username: "vera", code: reset, new: "newpass77", repeatPassword: "newpass77")))
    }

    func testEmailCodeExpiresAndLimitsTries() {
        let a = auth()
        _ = a.register(username: "gleb", displayName: "Глеб", password: "secret123", repeatPassword: "secret123")
        _ = a.setEmail("gleb", "gleb@mail.ru")
        nowMs += LocalAuth.codeTtlMs + 1
        XCTAssertEqual("Код устарел — запросите новый", a.confirmEmail("gleb", mail.sent.last!.code))
        _ = a.setEmail("gleb", "gleb@mail.ru")
        let good = mail.sent.last!.code
        let bad = good == "123456" ? "654321" : "123456"
        for _ in 0..<(LocalAuth.codeTries - 1) {
            XCTAssertEqual("Код не подходит", a.confirmEmail("gleb", bad))
        }
        XCTAssertEqual("Слишком много попыток — запросите новый код", a.confirmEmail("gleb", bad))
        XCTAssertNotNil(a.confirmEmail("gleb", good))
    }

    func testDeletingAccountForgetsRecovery() {
        let a = auth()
        _ = a.register(username: "dina", displayName: "Дина", password: "secret123", repeatPassword: "secret123")
        _ = a.newRecoveryCodes("dina")
        _ = a.setEmail("dina", "dina@mail.ru")
        XCTAssertTrue(a.checkPasswordOf("Dina", "secret123"))
        XCTAssertNil(a.deleteAccount(username: "dina", password: "secret123"))
        XCTAssertFalse(store.values.keys.contains { $0.hasPrefix("acc.dina.") })
    }

    // MARK: - 2. Лента активности

    func testActivityGroupsLikesAndCountsUnread() {
        let me = Demo.me
        let vera = Demo.people[0]
        let mark = Demo.people[1]
        let day = Activity.mergeMs
        let items = [
            ActivityItem(id: 1, kind: .like, actorId: vera.id, at: ms(1000), targetId: 5, preview: "Закат"),
            ActivityItem(id: 2, kind: .like, actorId: mark.id, at: ms(2000), targetId: 5, preview: "Закат"),
            ActivityItem(id: 3, kind: .like, actorId: vera.id, at: ms(3000), targetId: 5, preview: "Закат"),
            ActivityItem(id: 4, kind: .comment, actorId: mark.id, at: ms(4000), targetId: 5, preview: "Красиво"),
            ActivityItem(id: 5, kind: .like, actorId: mark.id, at: ms(1000 + 2 * day), targetId: 5),
        ]
        let groups = Activity.group(items, me)
        // Свежий лайк через двое суток — отдельно; три старых — одной строкой, без повтора Веры
        XCTAssertEqual(3, groups.count)
        let merged = groups.first { $0.kind == .like && $0.ids.count == 3 }!
        XCTAssertEqual(2, merged.actors.count)
        XCTAssertTrue(Activity.text(merged).hasPrefix(Activity.who(merged.actors) + " оценили вашу запись"))
        let single = Activity.text(groups.first { $0.kind == .comment }!)
        XCTAssertTrue(single.contains("прокомментировал") && single.contains("«Красиво»"), single)
        XCTAssertEqual(2, Activity.unread(items, ms(3000)))
        // Заблокированный пропадает из ленты и из счётчика
        XCTAssertEqual(0, Activity.unread(items, ms(3000), blocked: [mark.id]))
        XCTAssertEqual(2, Activity.unread(items, ms(0), blocked: [mark.id]))
        XCTAssertTrue(Activity.group(items, me, blocked: [mark.id]).allSatisfy { g in !g.actors.contains { $0.id == mark.id } })
        let four = [vera, mark, Demo.people[2], Demo.people[3]]
        let names = four.map(\.firstName)
        XCTAssertEqual("\(names[0]), \(names[1]) и ещё 2", Activity.who(four))
        XCTAssertEqual(ActivityKind.prismLike, ActivityKind.of("prism_like"))
        XCTAssertNil(ActivityKind.of("future_kind"))
    }

    func testActivitySurvivesSavingAndSkipsUnknownKinds() throws {
        let item = ActivityItem(id: 7, kind: .mention, actorId: 900, at: ms(5_000), targetId: 3, preview: "@demo", count: 1,
                                actorName: "Мария Звёздная", actorHue: 0.25)
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .millisecondsSince1970
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .millisecondsSince1970
        let data = try encoder.encode(LossyList([item]))
        let json = String(data: data, encoding: .utf8)!
        XCTAssertTrue(json.contains("\"actor\":900") && json.contains("\"target\":3") && json.contains("\"name\""))
        // Событие неизвестного вида (из более новой версии) пропускается, остальные читаются
        let mixed = "[{\"id\":1,\"kind\":\"future\",\"actor\":1,\"at\":1000}," + String(json.dropFirst())
        let list = try decoder.decode(LossyList<ActivityItem>.self, from: Data(mixed.utf8))
        XCTAssertEqual([item], list.items)
        // Чужой (не из демо) человек показывается по сохранённому имени
        XCTAssertEqual("Мария Звёздная", Activity.actor(item, Demo.me).name)
    }

    func testDemoLifeFillsActivityAndOpeningMarksRead() {
        let clock = TestClock(Date(timeIntervalSince1970: 5_000))
        let vm = AppModel(clock: { clock.now }, demoReplies: false)
        vm.publish("Закат над рекой #закат")
        for i in 0..<300 {
            var rng = Round20Rng(seed: UInt64(i))
            vm.demoEvent(using: &rng)
        }
        let s = vm.state
        XCTAssertTrue(s.activity.contains { $0.kind == .like })
        XCTAssertTrue(s.activity.contains { $0.kind == .comment || $0.kind == .mention })
        XCTAssertTrue(s.activity.contains { $0.kind == .friendRequest })
        XCTAssertGreaterThan(s.activityUnread, 0)
        vm.openActivity()
        XCTAssertTrue(vm.state.activityOpen)
        clock.now = clock.now.addingTimeInterval(1)
        vm.closeActivity()
        XCTAssertEqual(0, vm.state.activityUnread)
        // Упоминание — с вашим логином в тексте комментария
        if let mention = vm.state.activity.first(where: { $0.kind == .mention }) {
            XCTAssertTrue(mention.preview.contains("@" + vm.state.me.handle))
        }
        // Нажатие на заявку ведёт в «Заявки»
        let req = Activity.group(vm.state.activity, vm.state.me).first { $0.kind == .friendRequest }!
        vm.openActivityItem(req)
        XCTAssertEqual(ProfileSection.requests, vm.state.profileSection)
        XCTAssertFalse(vm.state.activityOpen)
    }

    // MARK: - 3. Поиск, теги, упоминания

    func testTagsAndMentionsAreFound() {
        let text = "Вечер с @vera.moon и @Mark_7 #Закат #закат #yerevan_2026 #երևան email a@b.ru, цвет #1 и https://example.com/#x"
        XCTAssertEqual(["закат", "yerevan_2026", "երևան"], Tags.hashtags(text))
        XCTAssertEqual(["vera.moon", "mark_7"], Tags.mentions(text))
        let spans = Tags.spans(text)
        XCTAssertTrue(spans.contains { $0.kind == .url && $0.value == "https://example.com/#x" })
        // Спаны не пересекаются и идут по порядку
        for i in 1..<spans.count { XCTAssertTrue(spans[i - 1].end <= spans[i].start) }
        // Ссылка в конце предложения — без точки
        XCTAssertEqual("https://x.am/p", Tags.spans("Смотрите https://x.am/p.").first?.value)
        // Подсказка упоминания при наборе
        let typing = Tags.typingMention("Это @ве")
        XCTAssertEqual(4, typing?.at)
        XCTAssertEqual("ве", typing?.part)
        XCTAssertNil(Tags.typingMention("почта a@ве"))
        XCTAssertEqual("Это @vera.moon ", Tags.completeMention("Это @ве", at: 4, cursor: 7, handle: "vera.moon"))
    }

    func testSearchFindsPeoplePostsTagsAndGroups() {
        let vm = AppModel(demoReplies: false)
        vm.publish("Первый снег в горах #зима #горы")
        vm.publish("Чай и плед #зима")
        let s = vm.state
        let vera = Demo.people[0]
        // Логином, с «@» и по началу имени
        XCTAssertEqual(vera.id, Search.people(vera.handle, s.searchablePeople).first?.id)
        XCTAssertEqual(vera.id, Search.people("@" + String(vera.handle.prefix(3)), s.searchablePeople).first?.id)
        XCTAssertTrue(Search.people(String(vera.name.prefix(3)).lowercased(), s.searchablePeople).contains { $0.id == vera.id })
        // Тег — только записи с ним
        XCTAssertEqual(2, Search.posts("#зима", s.searchablePosts).count)
        XCTAssertEqual(1, Search.posts("#горы", s.searchablePosts).count)
        XCTAssertEqual(TagCount(tag: "зима", count: 2), Search.tags("#зи", s.searchablePosts).first)
        XCTAssertEqual("зима", Search.popularTags(s.searchablePosts).first?.tag)
        // Обычные слова — все должны встретиться
        XCTAssertEqual(1, Search.posts("снег горах", s.searchablePosts).count)
        XCTAssertTrue(Search.all("ничегоподобного", s.searchablePeople, s.searchablePosts, s.groups).empty)
        // Заблокированных не найти
        vm.block(vera.id)
        XCTAssertFalse(vm.state.searchablePeople.contains { $0.id == vera.id })
        // История: без повторов, новые первыми
        let h = Search.remember(Search.remember(Search.remember([], "зима"), "горы"), "Зима")
        XCTAssertEqual(["Зима", "горы"], h)
    }

    func testTapOnTagAndMentionNavigates() {
        let vm = AppModel(demoReplies: false)
        vm.openTag("#Закат")
        XCTAssertTrue(vm.state.searchOpen)
        XCTAssertEqual("#закат", vm.state.searchQuery)
        XCTAssertEqual("#закат", vm.state.searchHistory.first)
        let vera = Demo.people[0]
        vm.openHandle(vera.handle.uppercased())
        XCTAssertEqual(vera.id, vm.state.openPersonId)
        // Назад: со страницы человека — в поиск, из поиска — в ленту
        vm.back()
        XCTAssertTrue(vm.state.searchOpen)
        vm.back()
        XCTAssertFalse(vm.state.searchOpen)
        vm.openHandle("nobody_here")
        XCTAssertEqual("@nobody_here", vm.state.searchQuery)
        // Подсказки после «@»: друзья первыми
        if !vm.state.following.isEmpty {
            XCTAssertTrue(vm.state.following.contains(vm.mentionCandidates("").first!.id))
        }
        vm.commitSearch("горы")
        XCTAssertEqual("горы", vm.state.searchHistory.first)
        vm.clearSearchHistory()
        XCTAssertTrue(vm.state.searchHistory.isEmpty)
    }

    // MARK: - 4. Правка записи

    func testEditOwnPostOnly() {
        let clock = TestClock(Date(timeIntervalSince1970: 7_000))
        let vm = AppModel(clock: { clock.now }, demoReplies: false)
        vm.publish("Опечатка в тексте")
        let mine = vm.state.myPosts.first!
        clock.now = clock.now.addingTimeInterval(60)
        vm.startEdit(mine.id)
        XCTAssertEqual(mine.id, vm.state.editingPostId)
        XCTAssertNil(vm.saveEdit(mine.id, "  Исправленный текст  ", mine.media))
        let edited = vm.state.findPost(mine.id)!
        XCTAssertEqual("Исправленный текст", edited.text)
        XCTAssertEqual(clock.now, edited.editedAt)
        XCTAssertNil(vm.state.editingPostId)
        // Чужую — нельзя
        let other = vm.state.posts.first { $0.author.id != vm.state.me.id }!
        vm.startEdit(other.id)
        XCTAssertNil(vm.state.editingPostId)
        XCTAssertEqual("Править можно только свои записи", vm.saveEdit(other.id, "взлом", []))
        XCTAssertEqual("Запись не может быть пустой", vm.saveEdit(mine.id, "   ", []))
        // Правка переживает сохранение
        let vm2 = AppModel(demoReplies: false)
        vm2.restore(vm.snapshot())
        XCTAssertEqual(clock.now, vm2.state.findPost(mine.id)?.editedAt)
    }

    // MARK: - 5. Ссылки

    func testLinksRoundTrip() {
        let targets: [LinkTarget] = [.postLink(12345), .profile("vera.moon"), .prismLink(7), .groupLink(3), .tagLink("закат")]
        for target in targets {
            XCTAssertEqual(target, Links.parse(Links.web(target)), Links.web(target))
            XCTAssertEqual(target, Links.parse(Links.app(target)), Links.app(target))
        }
        XCTAssertEqual(LinkTarget.profile("vera"), Links.parse("https://wishka.github.io/memory/l/?u=@Vera"))
        XCTAssertNil(Links.parse("https://example.com/?p=1"))
        XCTAssertNil(Links.parse("memory://u/<script>"))
        XCTAssertNil(Links.parse("memory://invite?c=ABCDEF"))
        XCTAssertEqual("https://wishka.github.io/memory/l/?t=%D0%B7%D0%B0%D0%BA%D0%B0%D1%82", Links.tag("#Закат"))
        let first = Demo.posts.first!
        XCTAssertTrue(Links.shareText(first).hasSuffix(Links.post(first.id)))
    }

    func testOpenLinkRoutes() {
        let vm = AppModel(demoReplies: false)
        let post = vm.state.feed.first!
        XCTAssertTrue(vm.openLink(.postLink(post.id)))
        XCTAssertEqual(post.id, vm.state.openPostId)
        vm.back()
        XCTAssertNil(vm.state.openPostId)
        XCTAssertFalse(vm.openLink(.postLink(999_999_999)))
        XCTAssertTrue(vm.openLink(.profile(Demo.people[1].handle)))
        XCTAssertEqual(Demo.people[1].id, vm.state.openPersonId)
        XCTAssertFalse(vm.openLink(.profile("nobody_here")))
        XCTAssertTrue(vm.openLink(.tagLink("закат")))
        XCTAssertTrue(vm.state.searchOpen)
        let prism = vm.state.prisms.first!
        XCTAssertTrue(vm.openLink(.prismLink(prism.id)))
        XCTAssertEqual(AppTab.prism, vm.state.tab)
    }

    // MARK: - 6. Мои данные

    /// Имена файлов архива — по локальным заголовкам zip; заодно проверяются CRC и содержимое.
    private func zipEntries(_ data: Data) -> [(name: String, body: Data, crc: UInt32)] {
        let bytes = [UInt8](data)
        func u16(_ i: Int) -> Int { Int(bytes[i]) | Int(bytes[i + 1]) << 8 }
        func u32(_ i: Int) -> UInt32 { UInt32(bytes[i]) | UInt32(bytes[i + 1]) << 8 | UInt32(bytes[i + 2]) << 16 | UInt32(bytes[i + 3]) << 24 }
        var out: [(name: String, body: Data, crc: UInt32)] = []
        var i = 0
        while i + 30 <= bytes.count && u32(i) == 0x0403_4b50 {
            XCTAssertEqual(0x0800, u16(i + 6) & 0x0800, "имя — в UTF-8")
            XCTAssertEqual(0, u16(i + 8), "без сжатия")
            let crc = u32(i + 14)
            let size = Int(u32(i + 18))
            let nameLength = u16(i + 26)
            let extra = u16(i + 28)
            let name = String(decoding: bytes[(i + 30)..<(i + 30 + nameLength)], as: UTF8.self)
            let start = i + 30 + nameLength + extra
            out.append((name, Data(bytes[start..<(start + size)]), crc))
            i = start + size
        }
        // Конец архива: столько же записей в оглавлении
        let end = bytes.count - 22
        XCTAssertEqual(0x0605_4b50, u32(end))
        XCTAssertEqual(out.count, u16(end + 10))
        XCTAssertEqual(UInt32(i), u32(end + 16), "оглавление сразу за файлами")
        return out
    }

    func testDataExportZipHasEverything() throws {
        let vm = AppModel(demoReplies: false)
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("memory-export-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: dir) }
        let photo = dir.appendingPathComponent("photo.jpg")
        let voice = dir.appendingPathComponent("photo.m4a")
        try Data([1, 2, 3]).write(to: photo)
        try Data([4, 5]).write(to: voice)
        vm.publish("С фото", media: [Attachment(id: "a", kind: .photo, fileName: photo.path)])
        let chat = vm.state.chats.first!
        vm.send(chat.id, "", attachments: [Attachment(id: "v", kind: .document, fileName: voice.path, voiceFlag: true)])
        let snap = vm.snapshot()
        let files = DataExport.files(snap, avatar: "")
        XCTAssertTrue(files.contains(photo.path) && files.contains(voice.path))
        XCTAssertEqual(5, DataExport.mediaBytes(files))
        let account = AccountExport(login: "anna", displayName: "Анна", info: ProfileInfo(bio: "Привет"), email: "anna@mail.ru",
                                    emailConfirmed: true, consentVersion: "2026-10-14", consentAt: 1000, recoveryCodesLeft: 9)
        let now = Date(timeIntervalSince1970: 2_000)
        let zip = dir.appendingPathComponent("out.zip")
        let count = try DataExport.write(to: zip, account: account, stateJson: "{\"posts\":[]}", files: files + ["/нет/такого.jpg"],
                                         withMedia: true, now: now)
        XCTAssertEqual(2, count)
        let entries = zipEntries(try Data(contentsOf: zip))
        XCTAssertEqual(["README.txt", "account.json", "memory.json", "media/photo.jpg", "media/photo.m4a"], entries.map { $0.name })
        for e in entries { XCTAssertEqual(CRC32.checksum(e.body), e.crc, e.name) }
        XCTAssertEqual(Data([1, 2, 3]), entries[3].body)
        XCTAssertEqual("{\"posts\":[]}", String(data: entries[2].body, encoding: .utf8))
        let accountJson = try JSONSerialization.jsonObject(with: entries[1].body) as? [String: Any]
        XCTAssertEqual("anna", accountJson?["login"] as? String)
        XCTAssertEqual(9, accountJson?["recoveryCodesLeft"] as? Int)
        // Без медиа — только описание и данные
        let light = dir.appendingPathComponent("light.zip")
        XCTAssertEqual(0, try DataExport.write(to: light, account: account, stateJson: "{}", files: files, withMedia: false, now: now))
        XCTAssertEqual(3, zipEntries(try Data(contentsOf: light)).count)
        let readme = DataExport.readme(account, now, 0, false)
        XCTAssertTrue(readme.contains("@anna") && readme.contains("не вошли"))
        XCTAssertTrue(matches("^memory-anna-\\d{4}-\\d{2}-\\d{2}\\.zip$", DataExport.fileName("anna", now)))
        // Сохранённое целиком — читаемым JSON
        XCTAssertTrue(StateStore.exportJson(snap).contains("\"posts\""))
        // CRC-32 по известному значению
        XCTAssertEqual(0xCBF4_3926, CRC32.checksum(Data("123456789".utf8)))
    }

    // MARK: - 7. Сбои и статистика

    func testTelemetryOnlyWithConsent() {
        let sink = RecordingSink()
        Telemetry.sink = sink
        let prefs = TelemetryPrefs(store)
        prefs.apply()
        XCTAssertFalse(prefs.asked)
        // Без согласия ничего не считается и не уходит
        Telemetry.track(.postPublished)
        XCTAssertFalse(Telemetry.crash(CrashReport.of(Round20Error("x"), at: ms(1))))
        XCTAssertTrue(sink.sent.isEmpty)
        prefs.set(crashes: true, analytics: true)
        XCTAssertTrue(prefs.asked)
        Telemetry.track(.postPublished)
        Telemetry.track(.postPublished)
        Telemetry.track(.searchUsed)
        XCTAssertTrue(Telemetry.crash(CrashReport.of(Round20Error("x"), at: ms(1))))
        // Статистика копится за день и уходит на следующий
        XCTAssertNil(prefs.roll("2026-10-14"))
        Telemetry.track(.appOpen)
        let report = prefs.roll("2026-10-15")
        XCTAssertEqual("2026-10-14", report?.day)
        XCTAssertEqual(["post_published": 2, "search_used": 1], report?.counts)
        // Отказ — счётчики стираются
        prefs.set(crashes: false, analytics: false)
        Telemetry.track(.appOpen)
        XCTAssertNil(prefs.roll("2026-10-16"))
        XCTAssertFalse(Telemetry.crash(CrashReport.of(Round20Error("y"), at: ms(2))))
        XCTAssertTrue(sink.sent.contains("crumb:post_published"))
        Telemetry.sink = nil
    }

    func testCrashReportsAreScrubbed() {
        let s = Scrub.text("Нет записи для anna@mail.ru и @vera.moon, тел +374 91 123456, файл state-anna.json, https://x.am/p?token=abc")
        XCTAssertFalse(s.contains("anna@mail.ru") || s.contains("vera.moon") || s.contains("123456") || s.contains("state-anna") || s.contains("token=abc"), s)
        let r = CrashReport.of(Round20Error("ключ anna@mail.ru"), at: ms(5))
        XCTAssertEqual(r, CrashReport.parse(ms(5), r.text()))
        XCTAssertTrue(r.title.hasPrefix("Round20Error: ключ <email>"), r.title)
        // Последний сбой — файлом: переживает перезапуск
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("memory-crash-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: dir) }
        CrashLog(directory: dir).save(r)
        XCTAssertEqual(r, CrashLog(directory: dir).last())
        CrashLog(directory: dir).clear()
        XCTAssertNil(CrashLog(directory: dir).last())
    }

    // MARK: - 8. Шифрование и исчезающие сообщения

    private func hex(_ s: String) -> [UInt8] { [UInt8](hex: s)! }

    func testX25519MatchesRfc7748() throws {
        let alice = hex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a")
        let bob = hex("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb")
        XCTAssertEqual("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a", ChatKeyPair.of(alice).publicKey.hex)
        XCTAssertEqual("de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f", ChatKeyPair.of(bob).publicKey.hex)
        func shared(_ scalar: [UInt8], _ u: [UInt8]) throws -> String {
            let priv = try Curve25519.KeyAgreement.PrivateKey(rawRepresentation: scalar)
            let pub = try Curve25519.KeyAgreement.PublicKey(rawRepresentation: u)
            let bytes: [UInt8] = try priv.sharedSecretFromKeyAgreement(with: pub).withUnsafeBytes { Array($0) }
            return bytes.hex
        }
        let secret = "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742"
        XCTAssertEqual(secret, try shared(alice, ChatKeyPair.of(bob).publicKey))
        XCTAssertEqual(secret, try shared(bob, ChatKeyPair.of(alice).publicKey))
        // RFC 7748, 5.2: одно умножение
        XCTAssertEqual(
            "c3da55379de9c6908e94ea4df28d084f32eccf03491c71f754b4075577a28552",
            try shared(hex("a546e36bf0527c9d3b16154b82465edd62144c0ac1fc5a18506a2244ba449ac4"),
                       hex("e6db6867583030db3594c1a424b15f7c726624ec26b3353b10a903a6d0ab1c4c"))
        )
    }

    /// Тот же вектор проверяет Android (своя X25519) — конверты понятны обеим платформам.
    func testEnvelopeInteropVector() throws {
        let a = ChatKeyPair.of(hex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a"))
        let b = ChatKeyPair.of(hex("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb"))
        let key = try ChatCrypto.sessionKey(a, b.publicKey)
        XCTAssertEqual("4c5cb8e873937bc60ae7614087877146a9c5193ad72322af4d802a9d1649f5e2", key.hex)
        XCTAssertEqual("23383 46330 71713 51949 49409 91008 18830 17904 66910 68117 93253 68369", ChatCrypto.safetyNumber(a.publicKey, b.publicKey))
        XCTAssertEqual("Привет, Վերա!", ChatCrypto.open(key, "AAECAwQFBgcICQoLAAgW1Sh0HWCpbT0kb/62CgbIjAH8LIjfdV96rgitGfkmPMPGT5iw", aad: "memory-chat:anna|vera"))
        XCTAssertEqual("d70cbf2d1757aeb59b25ade516e884df48ab3215336f7aaa8b61aee1b9afa031", ChatCrypto.demoPeer(1).publicKey.hex)
    }

    func testHkdfMatchesRfc5869() {
        // RFC 5869, тест 1
        let okm = ChatCrypto.hkdf(hex(String(repeating: "0b", count: 22)), salt: hex("000102030405060708090a0b0c"),
                                  info: hex("f0f1f2f3f4f5f6f7f8f9"), length: 42)
        XCTAssertEqual("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865", okm.hex)
    }

    func testSealedMessagesOpenOnlyForTheTwo() throws {
        let a = ChatKeyPair.generate()
        let b = ChatKeyPair.generate()
        let eve = ChatKeyPair.generate()
        let kA = try ChatCrypto.sessionKey(a, b.publicKey)
        XCTAssertEqual(kA.hex, try ChatCrypto.sessionKey(b, a.publicKey).hex)
        let env = try ChatCrypto.seal(kA, "Привет, это секрет", aad: "chat:1")
        XCTAssertFalse(String(decoding: Data(base64Encoded: env)!, as: UTF8.self).contains("секрет"))
        XCTAssertEqual("Привет, это секрет", ChatCrypto.open(try ChatCrypto.sessionKey(b, a.publicKey), env, aad: "chat:1"))
        XCTAssertNil(ChatCrypto.open(try ChatCrypto.sessionKey(eve, a.publicKey), env, aad: "chat:1"))
        XCTAssertNil(ChatCrypto.open(kA, env, aad: "chat:2"))
        // Подделанный конверт не откроется
        var raw = [UInt8](Data(base64Encoded: env)!)
        raw[20] ^= 1
        XCTAssertNil(ChatCrypto.open(kA, Data(raw).base64EncodedString(), aad: "chat:1"))
        XCTAssertNil(ChatCrypto.open(kA, "не base64", aad: "chat:1"))
        // Код безопасности одинаков у обоих и выглядит как 12 групп по 5 цифр
        let n = ChatCrypto.safetyNumber(a.publicKey, b.publicKey)
        XCTAssertEqual(n, ChatCrypto.safetyNumber(b.publicKey, a.publicKey))
        XCTAssertTrue(matches("^(\\d{5} ){11}\\d{5}$", n), n)
        XCTAssertNotEqual(n, ChatCrypto.safetyNumber(a.publicKey, eve.publicKey))
        // Свой ключ переживает перезапуск и хранится в защищённой части («session.» — Связка ключей)
        let keys = ChatKeys(store)
        let k1 = keys.identity("Anna")
        XCTAssertEqual(k1.publicKey.hex, ChatKeys(store).identity("anna").publicKey.hex)
        XCTAssertTrue(store.values.keys.contains { $0.hasPrefix("session.e2e.") })
        keys.forget("anna")
        XCTAssertNotEqual(k1.publicKey.hex, keys.identity("anna").publicKey.hex)
    }

    func testSecretChatSendsOnlyEnvelopes() {
        let vm = AppModel(demoReplies: false)
        vm.attachKeys(store)
        let chat = vm.state.chats.first!
        vm.setSecret(chat.id, true)
        vm.send(chat.id, "Пароль от вайфая 12345678")
        let env = vm.lastEnvelope!
        XCTAssertFalse((String(data: Data(base64Encoded: env)!, encoding: .isoLatin1) ?? "").contains("вайфая"))
        let last = vm.state.chats.first { $0.id == chat.id }!.messages.last!
        XCTAssertTrue(last.secret)
        XCTAssertEqual("Пароль от вайфая 12345678", last.text)
        let code = vm.safetyNumber(chat.id)!
        XCTAssertEqual(code, vm.safetyNumber(chat.id))
        vm.markVerified(chat.id)
        XCTAssertTrue(vm.state.chats.first { $0.id == chat.id }!.verified)
        // Выключили — отметка о сверке снимается
        vm.setSecret(chat.id, false)
        XCTAssertFalse(vm.state.chats.first { $0.id == chat.id }!.verified)
    }

    func testDisappearingMessagesExpire() throws {
        let clock = TestClock(Date(timeIntervalSince1970: 10_000))
        let vm = AppModel(clock: { clock.now }, demoReplies: false)
        let chat = vm.state.chats.first!
        let before = vm.state.chats.first { $0.id == chat.id }!.messages.count
        vm.send(chat.id, "Останется")
        vm.setDisappearing(chat.id, .fiveMinutes)
        vm.send(chat.id, "Исчезнет")
        let msgs = vm.state.chats.first { $0.id == chat.id }!.messages
        XCTAssertNil(msgs[msgs.count - 2].expiresAt)
        XCTAssertEqual(clock.now.addingTimeInterval(Double(Disappear.fiveMinutes.ms) / 1000), msgs.last?.expiresAt)
        clock.now = clock.now.addingTimeInterval(Double(Disappear.fiveMinutes.ms - 1) / 1000)
        XCTAssertTrue(vm.sweepExpired().isEmpty)
        XCTAssertEqual(before + 2, vm.state.chats.first { $0.id == chat.id }!.messages.count)
        clock.now = clock.now.addingTimeInterval(0.002)
        vm.sweepExpired()
        let after = vm.state.chats.first { $0.id == chat.id }!.messages
        XCTAssertEqual(before + 1, after.count)
        XCTAssertEqual("Останется", after.last?.text)
        // Настройки переписки переживают сохранение — и файл на телефоне
        let vm2 = AppModel(demoReplies: false)
        vm2.restore(vm.snapshot())
        XCTAssertEqual(Disappear.fiveMinutes.ms, vm2.state.chats.first { $0.id == chat.id }?.disappearMs)
        XCTAssertEqual(Disappear.fiveMinutes, Disappear.of(300_000))
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("memory-r20-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: dir) }
        let stateStore = StateStore(directory: dir)
        vm.setSecret(chat.id, true)
        vm.send(chat.id, "Секрет")
        stateStore.save("anna", vm.snapshot())
        let loaded = try XCTUnwrap(stateStore.load("anna"))
        let saved = loaded.chats.first { $0.id == chat.id }!
        XCTAssertEqual(Disappear.fiveMinutes.ms, saved.disappearMs)
        XCTAssertTrue(saved.secret)
        XCTAssertTrue(saved.messages.last!.secret)
        XCTAssertNotNil(saved.messages.last!.expiresAt)
    }

    func testActivityAndSearchHistorySurviveSaving() throws {
        let clock = TestClock(Date(timeIntervalSince1970: 12_000))
        let vm = AppModel(clock: { clock.now }, demoReplies: false)
        vm.receiveFriendRequest(Demo.people.first { !vm.state.following.contains($0.id) }?.id ?? Demo.people[0].id)
        vm.commitSearch("закат")
        vm.markActivitySeen()
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("memory-r20-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: dir) }
        let stateStore = StateStore(directory: dir)
        stateStore.save("anna", vm.snapshot())
        let vm2 = AppModel(demoReplies: false)
        vm2.restore(try XCTUnwrap(stateStore.load("anna")))
        XCTAssertEqual(vm.state.activity, vm2.state.activity)
        XCTAssertEqual(clock.now, vm2.state.activitySeen)
        XCTAssertEqual(["закат"], vm2.state.searchHistory)
    }

    // MARK: - 9. Эфиры

    func testLiveLifecycle() {
        let clock = TestClock(Date(timeIntervalSince1970: 20_000))
        let vm = AppModel(clock: { clock.now }, demoReplies: false)
        XCTAssertEqual("Назовите эфир — так друзья поймут, о чём он", vm.startLive("  ", friendsOnly: false))
        XCTAssertNil(vm.startLive("Закат с крыши", friendsOnly: true))
        let live = vm.state.myLive!
        XCTAssertEqual(live.id, vm.state.watchingLiveId)
        XCTAssertEqual("Эфир уже идёт", vm.startLive("Ещё один", friendsOnly: false))
        for i in 0..<30 {
            clock.now = clock.now.addingTimeInterval(2)
            var rng = Round20Rng(seed: UInt64(i))
            vm.liveTick(using: &rng)
        }
        let going = vm.state.myLive!
        XCTAssertTrue(going.peakViewers >= going.viewers)
        XCTAssertTrue(going.hearts > 0 && !going.comments.isEmpty)
        vm.sendLiveComment("Всем привет")
        XCTAssertEqual("Всем привет", vm.state.myLive?.comments.last?.text)
        // «Назад» у ведущего — вопрос, а не выход
        vm.back()
        XCTAssertTrue(vm.state.confirmEndLive)
        XCTAssertNotNil(vm.state.myLive)
        vm.endLive()
        XCTAssertNil(vm.state.myLive)
        XCTAssertNil(vm.state.watchingLiveId)
        let summary = vm.state.liveSummary!
        XCTAssertEqual(60_000, summary.durationMs(clock.now))
        XCTAssertTrue(Lives.summary(summary, clock.now).hasPrefix("Эфир шёл 1:00"))
    }

    func testFriendLiveCanBeWatched() {
        let vm = AppModel(demoReplies: false)
        let friend = vm.state.friends.first!
        var rng = Round20Rng(seed: 1)
        vm.friendGoesLive(friend, using: &rng)
        XCTAssertEqual(1, vm.state.visibleLives.count)
        let live = vm.state.visibleLives[0]
        XCTAssertEqual(friend.id, live.hostId)
        XCTAssertTrue(vm.state.activity.contains { $0.kind == .live && $0.targetId == live.id })
        vm.watchLive(live.id)
        XCTAssertEqual(live.viewers + 1, vm.state.watchingLive?.viewers)
        vm.sendHeart()
        XCTAssertEqual(live.hearts + 1, vm.state.watchingLive?.hearts)
        vm.back()
        XCTAssertNil(vm.state.watchingLiveId)
        // Уведомление о друге в эфире ведёт прямо в эфир
        vm.openNudge(Nudges.liveStarted(friend, live.title))
        XCTAssertEqual(live.id, vm.state.watchingLiveId)
        vm.back()
        // Заблокировали — эфир не виден
        vm.block(friend.id)
        XCTAssertTrue(vm.state.visibleLives.isEmpty)
        XCTAssertEqual("1:02:05", Lives.clock(3_725_000))
        XCTAssertEqual("0:07", Lives.clock(7_000))
    }
}

/// Часы, которые тест двигает сам.
final class TestClock {
    var now: Date
    init(_ now: Date) { self.now = now }
}

/// Письма с кодами — в список, вместо отправки.
final class RecordingMail: MailSender {
    var sent: [(email: String, code: String)] = []
    func send(email: String, code: String) { sent.append((email, code)) }
}

/// Что ушло бы в Sentry.
final class RecordingSink: TelemetrySink {
    var sent: [String] = []
    func crash(_ report: CrashReport) { sent.append("crash:" + report.type) }
    func usage(day: String, counts: [String: Int]) { sent.append("usage:\(day):\(counts)") }
    func breadcrumb(_ event: String) { sent.append("crumb:" + event) }
}

/// Ошибка для отчёта о сбое.
struct Round20Error: Error, CustomStringConvertible {
    let description: String
    init(_ description: String) { self.description = description }
}

/// Повторяемый генератор для проверок (SplitMix64).
struct Round20Rng: RandomNumberGenerator {
    private var state: UInt64
    init(seed: UInt64) { state = seed &+ 0x9E37_79B9_7F4A_7C15 }
    mutating func next() -> UInt64 {
        state = state &+ 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }
}
