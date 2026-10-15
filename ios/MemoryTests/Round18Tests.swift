import XCTest
@testable import Memory

/// Звуки уведомлений: 57 своих в девяти стилях, бесплатные в каждом стиле,
/// файлы в приложении. Те же проверки, что Round18Test на Android.
final class Round18Tests: XCTestCase {

    override func setUp() {
        super.setUp()
        L10n.shared.setLanguage(.ru)
    }

    override func tearDown() {
        L10n.shared.setLanguage(.ru)
        super.tearDown()
    }

    func testCatalog() throws {
        let own = NotifySound.allCases.filter { $0.file != nil }
        XCTAssertEqual(57, own.count)
        XCTAssertEqual(own.count, Set(own.map(\.rawValue)).count)
        for style in SoundStyle.allCases where style != .phone {
            let list = NotifySound.inStyle(style)
            XCTAssertGreaterThanOrEqual(list.count, 3, style.rawValue)
            XCTAssertTrue(list.contains { $0.free }, style.rawValue)
        }
        XCTAssertTrue(own.contains { !$0.free })
        XCTAssertTrue(NotifySound.system.free && NotifySound.silent.free)
        // Прежние id сохранились — выбор людей не сбился
        for id in ["crystal", "stardust", "comet", "moon", "prism", "nebula", "orbit", "droplet"] {
            XCTAssertEqual(SoundStyle.glass, NotifySound.of(id)?.style, id)
        }
        let defaults = NudgeKind.allCases.map { NotifySound.default($0) }
        XCTAssertEqual(defaults.count, Set(defaults).count)
        XCTAssertEqual("Маримба", NotifySound.of("marimba")?.title)
        L10n.shared.setLanguage(.en)
        XCTAssertEqual("Marimba", NotifySound.of("marimba")?.title)
        XCTAssertEqual("Pops & bubbles", SoundStyle.pop.title)
    }

    func testFilesInBundle() throws {
        // Файлы — в приложении (CAF годится и для пушей)
        for sound in NotifySound.allCases {
            guard let file = sound.file else { continue }
            XCTAssertNotNil(Bundle.main.url(forResource: file, withExtension: "caf"), file)
        }
        XCTAssertNotNil(NotifySound.marimba.notificationSound)
        XCTAssertNil(NotifySound.silent.notificationSound)
    }
}
