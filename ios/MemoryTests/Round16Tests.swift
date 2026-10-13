import XCTest
@testable import Memory

/// Гид по Memory: содержание, поиск, навигация, отметки «прочитано».
/// Те же проверки, что Round16Test на Android, и пара своих.
final class Round16Tests: XCTestCase {

    override func setUp() {
        super.setUp()
        L10n.shared.setLanguage(.ru)
    }

    override func tearDown() {
        L10n.shared.setLanguage(.ru)
        super.tearDown()
    }

    private func makeModel() -> AppModel {
        AppModel(clock: { Date(timeIntervalSince1970: 1_790_000_000) }, demoReplies: false)
    }

    func testContentIsCompleteInBothLanguages() throws {
        XCTAssertGreaterThanOrEqual(Guide.chapters.count, 10)
        XCTAssertGreaterThanOrEqual(Guide.topicCount, 50)
        XCTAssertEqual(Guide.chapters.count, Set(Guide.chapters.map(\.id)).count)
        for c in Guide.chapters {
            XCTAssertEqual(c.topics.count, Set(c.topics.map(\.id)).count, c.id)
            for topic in c.topics {
                XCTAssertFalse(topic.steps.isEmpty, c.key(topic))
                let texts = [topic.title] + topic.steps + (topic.tip.map { [$0] } ?? [])
                for text in texts {
                    XCTAssertFalse(text.ru.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, c.key(topic))
                    XCTAssertFalse(text.en.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, c.key(topic))
                }
            }
        }
        // Темы о новом — на месте
        XCTAssertTrue(try XCTUnwrap(Guide.chapter("groups")).topics.contains { $0.id == "shop-buyer" })
        XCTAssertTrue(try XCTUnwrap(Guide.chapter("friends")).topics.contains { $0.id == "requests" })
    }

    func testTextFollowsLanguage() throws {
        let title = try XCTUnwrap(Guide.chapters.first).title
        XCTAssertEqual(title.text, title.ru)
        L10n.shared.setLanguage(.en)
        XCTAssertEqual(title.text, title.en)
    }

    func testSearchFindsInBothLanguages() throws {
        let ru = Guide.search("витрина продавцу")
        XCTAssertFalse(ru.isEmpty)
        XCTAssertEqual(ru.first?.chapter.id, "groups")
        XCTAssertTrue(Guide.search("password").contains { $0.topic.id == "password" })
        XCTAssertTrue(Guide.search("  ").isEmpty)
        XCTAssertTrue(Guide.search("абракадабра").isEmpty)
        // Совпадение в заголовке — выше
        XCTAssertEqual(Guide.search("голосовые").first?.topic.id, "voice")
    }

    func testNavigationAndBack() {
        let vm = makeModel()
        vm.openGuide()
        XCTAssertEqual(vm.state.settingsPage, .guide)
        XCTAssertEqual(vm.state.tab, .profile)
        XCTAssertEqual(vm.state.guideReturn, .feed)
        vm.openGuideChapter("prism")
        XCTAssertEqual(vm.state.guideChapter, "prism")
        vm.openGuideChapter("нет-такой")
        XCTAssertEqual(vm.state.guideChapter, "prism")
        // «Назад»: глава → список глав → лента, откуда пришли
        XCTAssertTrue(vm.back())
        XCTAssertNil(vm.state.guideChapter)
        XCTAssertTrue(vm.back())
        XCTAssertNil(vm.state.settingsPage)
        XCTAssertEqual(vm.state.tab, .feed)
        // Из настроек — назад в настройки
        vm.openSettings()
        vm.openGuide("chats")
        XCTAssertEqual(vm.state.guideChapter, "chats")
        vm.back()
        vm.back()
        XCTAssertEqual(vm.state.settingsPage, .main)
    }

    func testActionsLeadToScreens() {
        let vm = makeModel()
        vm.openGuide("groups")
        vm.guideGo(.groups)
        XCTAssertNil(vm.state.settingsPage)
        XCTAssertEqual(vm.state.tab, .groups)
        vm.guideGo(.requests)
        XCTAssertEqual(vm.state.tab, .profile)
        XCTAssertEqual(vm.state.profileSection, .requests)
        vm.guideGo(.privacy)
        XCTAssertEqual(vm.state.settingsPage, .privacy)
        XCTAssertNil(vm.state.profileSection)
        vm.guideGo(.newPrism)
        XCTAssertEqual(vm.state.tab, .prism)
        XCTAssertTrue(vm.state.creatingPrism)
        // Каждое действие куда-то ведёт и не падает
        GuideAction.allCases.forEach { vm.guideGo($0) }
    }

    func testReadMarksAndCardSurviveRestart() throws {
        let vm = makeModel()
        let chapter = try XCTUnwrap(Guide.chapter("start"))
        vm.markGuideRead(chapter, chapter.topics[0])
        vm.markGuideRead(chapter, chapter.topics[0])
        vm.markGuideRead(chapter, chapter.topics[1])
        XCTAssertEqual(vm.state.guideRead.count, 2)
        XCTAssertFalse(vm.state.guideHidden)
        vm.hideGuideCard()
        var snap = vm.snapshot()
        snap.guideRead = (snap.guideRead ?? []) + ["старая/тема"]
        let again = makeModel()
        again.restore(snap)
        XCTAssertTrue(again.state.guideHidden)
        // Отметки убранных тем не считаются
        XCTAssertEqual(again.state.guideRead, ["start/signup", "start/taste"])
    }

    // MARK: - Свои проверки

    func testGuideSurvivesJsonAndOldFilesReadWithoutIt() throws {
        let vm = makeModel()
        let chapter = try XCTUnwrap(Guide.chapter("start"))
        vm.markGuideRead(chapter, chapter.topics[0])
        vm.hideGuideCard()
        let data = try JSONEncoder().encode(vm.snapshot())
        let decoded = try JSONDecoder().decode(Snapshot.self, from: data)
        let vm2 = makeModel()
        vm2.restore(decoded)
        XCTAssertEqual(vm2.state.guideRead, [chapter.key(chapter.topics[0])])
        XCTAssertTrue(vm2.state.guideHidden)

        // Старое сохранение — без полей гида
        var old = vm.snapshot()
        old.guideRead = nil
        old.guideHidden = nil
        let vm3 = makeModel()
        vm3.restore(try JSONDecoder().decode(Snapshot.self, from: try JSONEncoder().encode(old)))
        XCTAssertTrue(vm3.state.guideRead.isEmpty)
        XCTAssertFalse(vm3.state.guideHidden)
    }

    func testTabClearsGuideAndActionTitlesAreSet() {
        let vm = makeModel()
        vm.openGuide("prism")
        vm.selectTab(.chats)
        XCTAssertNil(vm.state.guideChapter)
        XCTAssertNil(vm.state.guideReturn)
        XCTAssertNil(vm.state.settingsPage)
        for action in GuideAction.allCases {
            XCTAssertFalse(action.title.isEmpty, action.rawValue)
        }
        XCTAssertEqual(GuideAction.newPrism.title, "Новый ролик")
    }
}
