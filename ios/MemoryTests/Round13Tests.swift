import XCTest
@testable import Memory

/// Опросы, гифки, коллекции стикеров, живые стикеры, фоны, звуки, место
/// в переписке. Те же проверки, что Round13Test на Android.
final class Round13Tests: XCTestCase {

    override func setUp() {
        super.setUp()
        L10n.shared.setLanguage(.ru)
    }

    private func vm() -> AppModel {
        AppModel(clock: { Date(timeIntervalSince1970: 1_790_000_000) }, demoReplies: false)
    }

    // MARK: - Опросы

    func testPollNeedsQuestionAndTwoDistinctOptions() throws {
        XCTAssertNil(Poll.create(id: 1, question: "  ", options: ["а", "б"], multiple: false))
        XCTAssertNil(Poll.create(id: 1, question: "Куда?", options: ["Кино", " кино "], multiple: false))
        let poll = try XCTUnwrap(Poll.create(id: 1, question: " Куда идём? ", options: [" Кино", "", "Парк", "кино"], multiple: false))
        XCTAssertEqual("Куда идём?", poll.question)
        XCTAssertEqual(["Кино", "Парк"], poll.options.map(\.text))
        let many = try XCTUnwrap(Poll.create(id: 1, question: "?", options: (1...15).map { "в\($0)" }, multiple: false))
        XCTAssertEqual(Poll.maxOptions, many.options.count)
    }

    func testPercentsAlwaysSumTo100() {
        let poll = Poll(id: 1, question: "?", options: [PollOption(text: "a", votes: 1), PollOption(text: "b", votes: 1), PollOption(text: "c", votes: 1)])
        XCTAssertEqual(100, poll.percents().reduce(0, +))
        XCTAssertEqual([34, 33, 33], poll.percents())
        let empty = Poll(id: 1, question: "?", options: [PollOption(text: "a"), PollOption(text: "b")])
        XCTAssertEqual([0, 0], empty.percents())
        XCTAssertEqual(0, empty.barFraction(0))
    }

    func testSingleVoteMovesMultipleToggles() throws {
        let single = try XCTUnwrap(Poll.create(id: 1, question: "?", options: ["a", "b"], multiple: false))
        let first = single.vote(0)
        XCTAssertEqual([0], first.mine)
        let moved = first.vote(1)
        XCTAssertEqual([1], moved.mine)
        XCTAssertEqual([0, 1], moved.options.map(\.votes))
        XCTAssertTrue(moved.vote(1).mine.isEmpty)

        let multi = try XCTUnwrap(Poll.create(id: 2, question: "?", options: ["a", "b", "c"], multiple: true)).vote(0).vote(2)
        XCTAssertEqual([0, 2], multi.mine)
        XCTAssertEqual(1, multi.voters)
        XCTAssertEqual([2], multi.vote(0).mine)
    }

    func testClosedPollRefusesVotes() throws {
        let closed = try XCTUnwrap(Poll.create(id: 1, question: "?", options: ["a", "b"], multiple: false)).addVotes([1: 3]).close()
        XCTAssertEqual(closed, closed.vote(0))
        XCTAssertEqual(1, closed.leader)
    }

    func testPublishedPollCanBeVotedAndClosedByAuthor() throws {
        let vm = vm()
        vm.publish("", poll: PollDraft(question: "Кино или парк?", options: ["Кино", "Парк"]))
        let post = vm.state.posts[0]
        XCTAssertNotNil(post.poll)
        XCTAssertTrue(vm.isMine(post))
        vm.votePost(post.id, 1)
        XCTAssertEqual([1], vm.state.posts[0].poll?.mine)
        vm.closePostPoll(post.id)
        vm.votePost(post.id, 0)
        let after = try XCTUnwrap(vm.state.posts[0].poll)
        XCTAssertTrue(after.closed)
        XCTAssertEqual([1], after.mine)
    }

    func testInvalidPollAloneIsNotPublished() {
        let vm = vm()
        let before = vm.state.posts.count
        vm.publish("", poll: PollDraft(question: "?", options: ["один"]))
        XCTAssertEqual(before, vm.state.posts.count)
    }

    func testChatPollAndPlace() throws {
        let vm = vm()
        let chat = vm.state.chats[0]
        vm.send(chat.id, "", poll: PollDraft(question: "Во сколько?", options: ["18:00", "19:00"]))
        let message = try XCTUnwrap(vm.state.chats.first { $0.id == chat.id }?.messages.last)
        let poll = try XCTUnwrap(message.poll)
        vm.voteMessage(chat.id, poll.id, 0)
        XCTAssertEqual([0], vm.state.chats.first { $0.id == chat.id }?.messages.last?.poll?.mine)
        XCTAssertEqual("Опрос: Во сколько?", previewOf(message))

        vm.sendPlace(chat.id, LatLon(lat: 55.75, lon: 37.62), title: "Парк")
        let place = try XCTUnwrap(vm.state.chats.first { $0.id == chat.id }?.messages.last)
        XCTAssertEqual(55.75, try XCTUnwrap(place.place).lat, accuracy: 1e-9)
        XCTAssertEqual("Геолокация: Парк", previewOf(place))
    }

    func testForwardedPollStartsClean() throws {
        let vm = vm()
        let chats = vm.state.chats
        let from = chats[0]
        let to = chats[1]
        vm.send(from.id, "", poll: PollDraft(question: "?", options: ["a", "b"]))
        let original = try XCTUnwrap(vm.state.chats.first { $0.id == from.id }?.messages.last)
        let originalPoll = try XCTUnwrap(original.poll)
        vm.voteMessage(from.id, originalPoll.id, 1)
        vm.forward(from.id, original.id, to: to.id)
        let copy = try XCTUnwrap(vm.state.chats.first { $0.id == to.id }?.messages.last?.poll)
        XCTAssertTrue(copy.mine.isEmpty)
        XCTAssertEqual(0, copy.total)
        XCTAssertNotEqual(copy.id, originalPoll.id)
    }

    // MARK: - Гифки

    func testSentGifsGoToRecentWithoutDuplicates() {
        let vm = vm()
        let chat = vm.state.chats[0]
        let gif = Attachment(id: "g1", kind: .photo, fileName: "hello.gif", name: "hello.gif", size: 10)
        XCTAssertTrue(gif.isGif)
        vm.send(chat.id, "", attachments: [gif])
        vm.publish("", media: [Attachment(id: "g2", kind: .photo, fileName: "hello.gif", name: "hello.gif", size: 10)])
        vm.send(chat.id, "", attachments: [Attachment(id: "p", kind: .photo, fileName: "a.jpg", name: "a.jpg", size: 10)])
        XCTAssertEqual(1, vm.state.recentGifs.count)
        let sent = vm.state.chats.first { $0.id == chat.id }?.messages.first { $0.attachments.first?.isGif == true }
        XCTAssertEqual("GIF", sent.map(previewOf))
    }

    // MARK: - Стикеры

    func testStickerPacks() throws {
        let vm = vm()
        let pack = try XCTUnwrap(vm.createStickerPack(" Котики "))
        XCTAssertEqual("Котики", pack.name)
        XCTAssertNil(vm.createStickerPack("котики"))
        XCTAssertNil(vm.createStickerPack("  "))
        vm.addSticker(Sticker(id: "s1", fileName: "s1.png", kind: .cutout, createdAt: Date(), pack: pack.id))
        vm.addSticker(Sticker(id: "s2", fileName: "a.png", kind: .drawing, createdAt: Date(),
                              animationId: "bounce", frameFiles: ["a.png", "b.png"], frameRate: 6))
        vm.moveSticker("s2", pack.id)
        XCTAssertEqual(2, vm.state.stickers.filter { $0.packId == pack.id }.count)
        vm.renameStickerPack(pack.id, "Коты")
        XCTAssertEqual(["Коты"], vm.state.stickerPacks.map(\.name))
        vm.deleteStickerPack(pack.id)
        XCTAssertTrue(vm.state.stickerPacks.isEmpty)
        XCTAssertTrue(vm.state.stickers.allSatisfy { $0.packId.isEmpty })
        XCTAssertEqual(true, vm.state.stickers.first { $0.id == "s2" }?.animated)
    }

    func testStickerMotion() {
        XCTAssertEqual(1, StickerAnim.none.pose(0.3).scale)
        XCTAssertEqual(180, StickerAnim.spin.pose(0.5).rotation, accuracy: 0.01)
        XCTAssertEqual(0.4, StickerAnim.shimmer.pose(0.4).shine, accuracy: 0.0001)
        XCTAssertLessThan(StickerAnim.bounce.pose(0.5).dy, 0)
        XCTAssertEqual(StickerAnim.pulse, StickerAnim.of("pulse"))
        XCTAssertEqual(StickerAnim.none, StickerAnim.of("???"))
    }

    // MARK: - Фоны и звуки

    func testBackdrops() throws {
        let vm = vm()
        XCTAssertEqual(BackdropStyle.cosmos, Backdrop.default.kind)
        XCTAssertEqual(BackdropStyle.cosmos, Backdrop(style: BackdropStyle.photo.id).kind)
        XCTAssertFalse(BackdropStyle.collection.contains(.photo))
        vm.setBackdrop(Backdrop(style: BackdropStyle.aurora.id))
        let chat = vm.state.chats[0]
        XCTAssertEqual(BackdropStyle.aurora, chat.backdropOr(vm.state.backdrop).kind)
        vm.setChatBackdrop(chat.id, Backdrop(style: BackdropStyle.prism.id))
        let own = try XCTUnwrap(vm.state.chats.first { $0.id == chat.id })
        XCTAssertEqual(BackdropStyle.prism, own.backdropOr(vm.state.backdrop).kind)
        vm.setChatBackdrop(chat.id, nil)
        XCTAssertEqual(BackdropStyle.aurora, vm.state.chats.first { $0.id == chat.id }?.backdropOr(vm.state.backdrop).kind)
    }

    func testEveryKindHasItsOwnSound() {
        let defaults = NudgeKind.allCases.map { NotifySound.default($0) }
        XCTAssertEqual(defaults.count, Set(defaults).count)
        XCTAssertNil(NotifySound.silent.file)
        XCTAssertNil(NotifySound.system.file)
        XCTAssertEqual("snd_crystal", NotifySound.crystal.file)
        XCTAssertNil(NotifySound.silent.notificationSound)
    }

    // MARK: - Старые сохранения

    func testOldDataDecodesWithoutNewFields() throws {
        let message = #"{"id":1,"fromMe":true,"text":"hi","time":"12:00","attachments":[{"id":"a","kind":"photo","fileName":"a.jpg","name":"a.jpg","size":0}]}"#
        let m = try JSONDecoder().decode(Message.self, from: Data(message.utf8))
        XCTAssertNil(m.poll)
        XCTAssertNil(m.place)
        XCTAssertFalse(m.attachments[0].animated)
        let notifications = #"{"all":true,"messages":true,"reactions":true,"groups":true,"follows":true}"#
        XCTAssertTrue(try JSONDecoder().decode(Notifications.self, from: Data(notifications.utf8)).prism)
        let sticker = #"{"id":"s","fileName":"s.png","kind":"Смайл"}"#
        let s = try JSONDecoder().decode(Sticker.self, from: Data(sticker.utf8))
        XCTAssertEqual("", s.packId)
        XCTAssertFalse(s.animated)
        XCTAssertEqual(0.35, try JSONDecoder().decode(Backdrop.self, from: Data(#"{"style":"aurora"}"#.utf8)).dim, accuracy: 1e-9)
        // Новый Nudge с сервера: PRISM друга
        let nudge = try XCTUnwrap(Nudges.fromPush(["type": "prism", "actor": "Вера Лунная", "actorId": "2", "prismId": "7", "caption": "Огни"]))
        XCTAssertEqual(NudgeKind.prism, nudge.kind)
        XCTAssertEqual(7, nudge.targetId)
    }

    // MARK: - Демо-жизнь

    func testSocialEventsAddVotesPrismsOrPlaces() throws {
        let vm = vm()
        vm.publish("", poll: PollDraft(question: "Куда?", options: ["a", "b"]))
        let prisms = vm.state.prisms.count
        let messages = vm.state.chats.reduce(0) { $0 + $1.messages.count }
        var rng = Seeded(seed: 7)
        for _ in 0..<12 { vm.socialEvent(using: &rng) }
        let s = vm.state
        let votes = try XCTUnwrap(s.posts[0].poll).total
        XCTAssertTrue(votes > 0 || s.prisms.count > prisms || s.chats.reduce(0) { $0 + $1.messages.count } > messages)
        // Закрытый опрос голосов больше не получает
        vm.closePostPoll(s.posts[0].id)
        let closedTotal = try XCTUnwrap(vm.state.posts[0].poll).total
        for _ in 0..<12 { vm.socialEvent(using: &rng) }
        XCTAssertEqual(closedTotal, vm.state.posts[0].poll?.total)
    }
}

/// Повторяемый генератор для проверок.
private struct Seeded: RandomNumberGenerator {
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
