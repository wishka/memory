import XCTest
@testable import Memory

/// Жалобы, рекомендации по согласию, документы. Те же проверки, что
/// Round14Test на Android, и пара своих: сохранение и согласие.
final class Round14Tests: XCTestCase {

    override func setUp() {
        super.setUp()
        L10n.shared.setLanguage(.ru)
    }

    private func makeModel() -> AppModel {
        AppModel(clock: { Date(timeIntervalSince1970: 1_790_000_000) }, demoReplies: false)
    }

    // MARK: - Жалобы

    func testReportingAPostHidesItAndQueuesTheReport() throws {
        let vm = makeModel()
        let post = try XCTUnwrap(vm.state.feed.first { !vm.isMine($0) })
        vm.reportPost(post)
        let target = try XCTUnwrap(vm.state.reporting)
        XCTAssertEqual(.post, target.kind)
        vm.report(target, .spam, note: "  реклама  ", block: false)
        let s = vm.state
        XCTAssertNil(s.reporting)
        XCTAssertTrue(s.posts.allSatisfy { $0.id != post.id })
        XCTAssertEqual(1, s.reports.count)
        XCTAssertEqual("реклама", s.reports.first?.note)
        XCTAssertFalse(s.blocked.contains(post.author.id))
    }

    func testReportWithBlockBlocksTheAuthor() throws {
        let vm = makeModel()
        let prism = try XCTUnwrap(vm.state.prisms.first { $0.author.id != vm.state.me.id })
        vm.reportPrism(prism)
        vm.report(try XCTUnwrap(vm.state.reporting), .abuse, block: true)
        XCTAssertTrue(vm.state.prisms.allSatisfy { $0.id != prism.id })
        XCTAssertTrue(vm.state.blocked.contains(prism.author.id))
    }

    func testReportingAMessageRemovesOnlyIt() throws {
        let vm = makeModel()
        let chat = try XCTUnwrap(vm.state.chats.first { c in c.messages.contains { !$0.fromMe } })
        let msg = try XCTUnwrap(chat.messages.first { !$0.fromMe })
        if let mine = chat.messages.first(where: { $0.fromMe }) {
            vm.reportMessage(chat.id, mine)
        }
        XCTAssertNil(vm.state.reporting) // на свои не жалуются
        vm.reportMessage(chat.id, msg)
        vm.report(try XCTUnwrap(vm.state.reporting), .other)
        let after = try XCTUnwrap(vm.state.chats.first { $0.id == chat.id })
        XCTAssertTrue(after.messages.allSatisfy { $0.id != msg.id })
        XCTAssertEqual(chat.messages.count - 1, after.messages.count)
    }

    func testReportingACommentHidesIt() throws {
        let vm = makeModel()
        let me = vm.state.me.id
        let post = try XCTUnwrap(vm.state.posts.first { p in p.commentList.contains { $0.author.id != me } })
        let c = try XCTUnwrap(post.commentList.first { $0.author.id != me })
        vm.reportComment(c)
        vm.report(try XCTUnwrap(vm.state.reporting), .hate)
        XCTAssertTrue(vm.state.reportedComments.contains(c.id))
    }

    func testCancelledReportChangesNothing() throws {
        let vm = makeModel()
        let post = try XCTUnwrap(vm.state.feed.first { !vm.isMine($0) })
        vm.reportPost(post)
        vm.cancelReport()
        XCTAssertNil(vm.state.reporting)
        XCTAssertTrue(vm.state.reports.isEmpty)
        XCTAssertTrue(vm.state.posts.contains { $0.id == post.id })
    }

    func testReportsSurviveSnapshot() throws {
        let vm = makeModel()
        let person = try XCTUnwrap(Demo.people.first)
        vm.reportPerson(person)
        vm.report(try XCTUnwrap(vm.state.reporting), .impersonation, note: "не он")
        let data = try JSONEncoder().encode(vm.snapshot())
        let restored = makeModel()
        restored.restore(try JSONDecoder().decode(Snapshot.self, from: data))
        XCTAssertEqual(vm.state.reports, restored.state.reports)
        XCTAssertEqual(.person, restored.state.reports.first?.target.kind)
    }

    // MARK: - Рекомендации

    func testPersonalRecommendationsCanBeTurnedOff() {
        let vm = makeModel()
        var privacy = vm.state.privacy
        XCTAssertTrue(privacy.personalRecs)
        privacy.personalRecs = false
        vm.setPrivacy(privacy)
        XCTAssertTrue(vm.state.recommendations("film").isEmpty)
        XCTAssertTrue(vm.state.recommendations("book").isEmpty)
    }

    func testOldPrivacyDecodesWithRecommendationsOn() throws {
        // Сохранение до раунда 14: без personalRecsFlag
        let json = #"{"profileInfo":"Все","followLists":"Все","messages":"Все","comments":"Все","showOnline":true,"showBirthday":true}"#
        let old = try JSONDecoder().decode(Privacy.self, from: Data(json.utf8))
        XCTAssertTrue(old.personalRecs)
    }

    // MARK: - Документы и согласие

    func testLegalDefaults() {
        XCTAssertEqual(16, Legal.minAge)
        XCTAssertFalse(Legal.filled)
        XCTAssertEqual("privacy.md", LegalDoc.privacy.file)
        XCTAssertEqual(.other, ReportReason.of("???"))
        XCTAssertEqual(.selfHarm, ReportReason.of("self_harm"))
    }

    func testConsentIsPerAccountAndVersion() throws {
        let suite = "Round14Tests.consent"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defaults.removePersistentDomain(forName: suite)
        let store = ConsentStore(defaults: defaults)
        XCTAssertFalse(store.accepted("Anna"))
        store.accept("@Anna ", now: Date(timeIntervalSince1970: 1_790_000_000))
        XCTAssertTrue(store.accepted("anna"))
        XCTAssertEqual(Legal.version, store.version("anna"))
        XCTAssertEqual(1_790_000_000_000, store.acceptedAt("anna"))
        XCTAssertEqual("\(Legal.version)|1790000000000", defaults.string(forKey: "consent.anna"))
        XCTAssertFalse(store.accepted("boris"))
        // Старая версия документов — нужно принять заново
        defaults.set("2020-01-01|1", forKey: "consent.boris")
        XCTAssertFalse(store.accepted("boris"))
        store.forget("anna")
        XCTAssertFalse(store.accepted("anna"))
        defaults.removePersistentDomain(forName: suite)
    }
}
