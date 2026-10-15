import XCTest
@testable import Memory

/// Чек-ины, Итоги, закрытый профиль и персональные уведомления.
/// Те же проверки, что Round10Test на Android.
final class Round10Tests: XCTestCase {

    override func setUp() {
        super.setUp()
        L10n.shared.setLanguage(.ru)
    }

    private func movie(_ subject: String) -> CheckIn {
        checkInActivities.first { $0.id == "movie" }!.checkIn(subject, 0)
    }

    private func run(_ km: Double) -> CheckIn {
        checkInActivities.first { $0.id == "run" }!.checkIn("Парк", km)
    }

    private func september15() -> Date {
        Calendar.current.date(from: DateComponents(year: 2026, month: 9, day: 15))!
    }

    func testCheckInPublishesWithoutText() throws {
        let vm = AppModel(demoReplies: false)
        vm.publish("", checkIn: movie("Дюна"))
        let post = try XCTUnwrap(vm.state.myPosts.first)
        XCTAssertEqual(post.checkIn?.subject, "Дюна")
        XCTAssertEqual(post.checkIn?.displayTitle, "Смотрю фильм")
    }

    func testCustomTemplateCreatedOnceAndDeleted() throws {
        let vm = AppModel(demoReplies: false)
        XCTAssertNil(vm.createCheckInTemplate(emoji: "🧗", title: "Скалодром", unit: "мин", hint: "Где?"))
        XCTAssertNotNil(vm.createCheckInTemplate(emoji: "🧗", title: "скалодром", unit: "", hint: ""))
        XCTAssertNotNil(vm.createCheckInTemplate(emoji: "⭐", title: "Смотрю фильм", unit: "", hint: ""))
        let tpl = try XCTUnwrap(vm.state.checkInTemplates.first)
        XCTAssertEqual(vm.state.checkInTemplates.count, 1)
        XCTAssertEqual(tpl.checkIn("Зал", 45).amount, 45)
        vm.deleteCheckInTemplate(tpl.id)
        XCTAssertTrue(vm.state.checkInTemplates.isEmpty)
    }

    func testRecapCountsFilmsKmAndPosts() throws {
        let day = september15()
        let vm = AppModel(clock: { day }, demoReplies: false)
        vm.publish("", checkIn: movie("Дюна"))
        vm.publish("", checkIn: movie("Солярис"))
        vm.publish("утро", checkIn: run(5.5))
        vm.publish("вечер", checkIn: run(2))
        let r = vm.recap(.month, year: 2026, month: 9)
        XCTAssertEqual(r.checkIns, 4)
        XCTAssertEqual(r.tiles.first { $0.emoji == "🎬" }?.value, "2")
        XCTAssertEqual(r.tiles.first { $0.emoji == "🎬" }?.label, "фильма")
        XCTAssertEqual(r.tiles.first { $0.emoji == "🏃" }?.value, "7,5")
        XCTAssertTrue(r.headline.hasPrefix("2 фильма"))
        XCTAssertTrue(vm.recap(.month, year: 2026, month: 8).empty)
        XCTAssertEqual(vm.recap(.year, year: 2026, month: 0).checkIns, 4)
        XCTAssertEqual(r.title, "Сентябрь 2026")
    }

    func testRecapNudgeOncePerMonth() {
        let day = september15()
        let vm = AppModel(clock: { day }, demoReplies: false)
        var received: [Nudge] = []
        vm.onNudge = { received.append($0) }
        vm.publish("", checkIn: movie("Дюна"))
        let october = Calendar.current.date(from: DateComponents(year: 2026, month: 10, day: 2))!
        vm.checkRecapReady(today: october)
        vm.checkRecapReady(today: october)
        XCTAssertTrue(vm.state.recapNotified.contains("2026-09"))
        XCTAssertEqual(received.count, 1)
        XCTAssertEqual(received.first?.kind, .recap)
    }

    func testClosedPeopleHiddenUntilAccepted() {
        let vm = AppModel(demoReplies: false)
        XCTAssertTrue(Demo.person(3)?.closed ?? false)
        XCTAssertFalse(vm.state.feed.contains { $0.author.id == 3 })
        vm.toggleFriend(3)
        XCTAssertTrue(vm.state.sentRequests.contains(3))
        XCTAssertFalse(vm.state.following.contains(3))
        vm.acceptSentRequest(3)
        XCTAssertTrue(vm.state.following.contains(3))
        XCTAssertTrue(vm.state.feed.contains { $0.author.id == 3 })
    }

    func testFriendRequestsRegardlessOfClosedProfile() {
        let vm = AppModel(demoReplies: false)
        // Открытый профиль — заявка в друзья всё равно приходит
        vm.receiveFriendRequest(5)
        XCTAssertEqual(vm.state.followRequests, [5])
        XCTAssertEqual(vm.state.friendState(5), .incoming)
        // Повторная — не дублируется; от друга (6) — не приходит
        vm.receiveFriendRequest(5)
        vm.receiveFriendRequest(6)
        XCTAssertEqual(vm.state.followRequests, [5])
        vm.approveRequest(5)
        XCTAssertTrue(vm.state.following.contains(5))
        XCTAssertTrue(vm.state.friends.contains { $0.id == 5 })
        XCTAssertTrue(vm.state.followRequests.isEmpty)
        // Закрыли и снова открыли профиль — ждущая заявка не одобряется сама
        let vm2 = AppModel(demoReplies: false)
        var closed = Privacy()
        closed.closedProfile = true
        vm2.setPrivacy(closed)
        vm2.receiveFriendRequest(3)
        vm2.setPrivacy(Privacy())
        XCTAssertEqual(vm2.state.followRequests, [3])
        XCTAssertFalse(vm2.state.following.contains(3))
        // Отклонили — заявка исчезает, в друзья не попадает
        vm2.declineRequest(3)
        XCTAssertTrue(vm2.state.followRequests.isEmpty)
        XCTAssertFalse(vm2.state.following.contains(3))
        // Из чёрного списка заявки не приходят
        vm2.block(5)
        vm2.receiveFriendRequest(5)
        XCTAssertTrue(vm2.state.followRequests.isEmpty)
    }

    func testSendingRequestToRequesterMakesFriendsAtOnce() {
        let vm = AppModel(demoReplies: false)
        vm.receiveFriendRequest(5)
        vm.toggleFriend(5)
        let s = vm.state
        XCTAssertTrue(s.following.contains(5))
        XCTAssertTrue(s.followRequests.isEmpty)
        XCTAssertTrue(s.sentRequests.isEmpty)
    }

    func testOpenProfileRequestAcceptedAndCancelled() throws {
        let vm = AppModel(demoReplies: false)
        let timur = try XCTUnwrap(Demo.person(5))
        let alina = try XCTUnwrap(Demo.person(4))
        // Открытый профиль (Тимур) — тоже по заявке
        XCTAssertFalse(timur.closed)
        vm.toggleFriend(5)
        XCTAssertEqual(vm.state.friendState(5), .requested)
        XCTAssertFalse(vm.state.following.contains(5))
        // Повторное нажатие — заявка отозвана; принять отозванную нельзя
        vm.toggleFriend(5)
        XCTAssertTrue(vm.state.sentRequests.isEmpty)
        vm.acceptSentRequest(5)
        XCTAssertFalse(vm.state.following.contains(5))
        // Отправили снова — приняли: друзья и уведомление о принятой заявке
        vm.toggleFriend(5)
        vm.acceptSentRequest(5)
        XCTAssertEqual(vm.state.friendState(5), .friends)
        XCTAssertEqual(Nudges.requestAccepted(timur).text, "Тимур принял вашу заявку в друзья")
        XCTAssertEqual(Nudges.requestAccepted(alina).text, "Алина приняла вашу заявку в друзья")
        XCTAssertEqual(Nudges.followRequest(timur, common: []).text, "Тимур хочет добавить вас в друзья")
    }

    func testFriendsSurviveSnapshotAndDemoRequestsCome() {
        let vm = AppModel(demoReplies: false)
        vm.onNudge = { _ in }
        vm.receiveFriendRequest(3)
        vm.toggleFriend(5)
        let snap = vm.snapshot()
        let vm2 = AppModel(demoReplies: false)
        vm2.restore(snap)
        XCTAssertEqual(vm2.state.followRequests, [3])
        XCTAssertEqual(vm2.state.sentRequests, [5])
        XCTAssertEqual(vm2.state.following, vm.state.following)
        // Демо-жизнь шлёт заявки и при открытом профиле
        let vm3 = AppModel(demoReplies: false)
        vm3.onNudge = { _ in }
        for seed in 0..<200 {
            var rng = SplitMix(seed: UInt64(seed))
            vm3.demoEvent(using: &rng)
        }
        XCTAssertFalse(vm3.state.followRequests.isEmpty)
        XCTAssertTrue(vm3.state.followRequests.allSatisfy { !vm3.state.following.contains($0) })
    }

    func testOldApprovedFollowersBecomeFriends() throws {
        let vm = AppModel(demoReplies: false)
        var snap = vm.snapshot()
        XCTAssertNil(snap.approvedFollowers) // больше не записывается
        snap.approvedFollowers = [3]
        snap.followRequests = [3, 5]
        let vm2 = AppModel(demoReplies: false)
        vm2.restore(snap)
        XCTAssertTrue(vm2.state.following.contains(3))
        XCTAssertEqual(vm2.state.followRequests, [5])
    }

    func testNudgeMentionsDiscussedFilm() throws {
        let vera = try XCTUnwrap(Demo.person(2))
        XCTAssertTrue(Nudges.female(vera))
        XCTAssertFalse(Nudges.female(User(id: 9, name: "Илья", handle: "i")))
        XCTAssertTrue(Nudges.discussed("Дюна", ["Вчера пересмотрел дюну, мощно"]))
        let n = Nudges.friendCheckIn(vera, movie("Дюна"), discussed: true, myKm: 0, postId: 1)
        XCTAssertEqual(n.text, "Ваша подруга Вера только что отметила фильм «Дюна», который вы обсуждали")
        let timur = try XCTUnwrap(Demo.person(5))
        let r = Nudges.friendCheckIn(timur, run(7), discussed: false, myKm: 12, postId: 2)
        XCTAssertEqual(r.text, "Тимур пробежал 7 км. У вас за месяц — 12 км, не отставайте!")
    }

    func testNudgeRespectsSettings() throws {
        let group = try XCTUnwrap(Demo.groups.first)
        let n = Nudges.groupMember(group, User(id: 9, name: "Мария", handle: "m"))
        XCTAssertEqual(n.text, "В вашей группе «Ночное небо» новый участник — Мария")
        XCTAssertTrue(Nudges.allowed(n, Notifications()))
        var off = Notifications()
        off.groups = false
        XCTAssertFalse(Nudges.allowed(n, off))
        XCTAssertFalse(Nudges.allowed(n, Notifications(all: false)))
    }

    func testDemoFriendCheckInPrefersDiscussedFilm() {
        let vm = AppModel(demoReplies: false)
        vm.onNudge = { _ in }
        vm.publish("Кто смотрел Солярис? Хочу обсудить")
        for seed in 0..<200 {
            var rng = SplitMix(seed: UInt64(seed))
            vm.demoEvent(using: &rng)
        }
        let checks = vm.state.posts.compactMap(\.checkIn).filter { $0.activity == "movie" }
        XCTAssertFalse(checks.isEmpty)
        XCTAssertTrue(checks.contains { $0.subject == "Солярис" })
    }

    func testPushPayloadBuildsText() {
        let n = Nudges.fromPush(["type": "friend_checkin", "actor": "Вера Лунная", "activity": "book", "subject": "Пикник на обочине", "discussed": "1"])
        XCTAssertEqual(n?.text, "Ваша подруга Вера начала читать «Пикник на обочине» — книгу, которую вы обсуждали")
        XCTAssertNil(Nudges.fromPush(["type": "unknown"]))
    }

    func testVoiceAndStickerPreview() {
        let voice = Attachment(id: "v", kind: .document, fileName: "v.m4a", voiceFlag: true, durationMs: 12_000)
        XCTAssertFalse(voice.isAudio)
        let message = Message(id: 1, fromMe: true, text: "", time: "12:00", attachments: [voice])
        XCTAssertEqual(previewOf(message), "Голосовое сообщение · 0:12")
        XCTAssertEqual(VoiceRecorder.squeeze([1, 5, 9, 2], 2), [5, 9])
    }
}

/// Повторяемый генератор для проверок.
private struct SplitMix: RandomNumberGenerator {
    var state: UInt64
    init(seed: UInt64) { state = seed &+ 0x9E37_79B9_7F4A_7C15 }
    mutating func next() -> UInt64 {
        state = state &+ 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }
}
