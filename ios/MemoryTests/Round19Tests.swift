import XCTest
@testable import Memory

/// Армянский язык, Memory+ скрыт (всё бесплатно), документы на трёх
/// языках. Те же проверки, что Round19Test на Android.
final class Round19Tests: XCTestCase {

    override func setUp() {
        super.setUp()
        L10n.shared.setLanguage(.ru)
        L10n.shared.systemLanguage = "ru"
    }

    override func tearDown() {
        L10n.shared.setLanguage(.ru)
        L10n.shared.systemLanguage = "ru"
        super.tearDown()
    }

    /// Есть ли в строке армянские буквы.
    private func hasArmenian(_ s: String) -> Bool {
        s.unicodeScalars.contains { (0x0531...0x0587).contains($0.value) }
    }

    /// Подстановки строки: %s, %d, %.1f… — по порядку сортировки.
    private func placeholders(_ s: String) -> [String] {
        guard let re = try? NSRegularExpression(pattern: "%(?:\\d+\\$)?[-+ 0#]*\\d*(?:\\.\\d+)?[sdf]") else { return [] }
        let ns = s as NSString
        return re.matches(in: s, range: NSRange(location: 0, length: ns.length))
            .map { ns.substring(with: $0.range) }
            .sorted()
    }

    func testArmenianCoversWholeInterface() {
        let missing = english.keys.filter { armenian[$0] == nil }
        XCTAssertTrue(missing.isEmpty, "нет перевода: \(Array(missing.prefix(5)))")
        for (ru, hy) in armenian {
            XCTAssertEqual(placeholders(ru), placeholders(hy), ru)
            XCTAssertFalse(hy.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, ru)
        }
        XCTAssertGreaterThan(Double(armenian.values.filter { hasArmenian($0) }.count), Double(armenian.count) * 0.9)
    }

    func testLanguageChoice() {
        L10n.shared.setLanguage(.hy)
        XCTAssertTrue(L10n.shared.armenian)
        XCTAssertFalse(L10n.shared.english)
        XCTAssertEqual(armenian["Лента"], t("Лента"))
        XCTAssertTrue(hasArmenian(tf("Прочитано %s из %s", 3, 10)))
        // После числа по-армянски — единственное число
        XCTAssertEqual("5 " + (armenian["запись"] ?? ""), plural(5, "запись", "записи", "записей"))
        XCTAssertEqual("Հայերեն", Lang.hy.title)
        XCTAssertTrue(Lang.allCases.contains(.hy))
        XCTAssertEqual("hy_AM", L10n.shared.locale.identifier)
        XCTAssertEqual("hy", L10n.shared.pick(ru: "ru", en: "en", hy: "hy"))
        // Выбор сохраняется кодом «hy» и читается обратно
        let suite = "Round19Tests.lang"
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        L10n(defaults: defaults).setLanguage(.hy)
        XCTAssertEqual("hy", defaults.string(forKey: "lang"))
        XCTAssertEqual(Lang.hy, L10n(defaults: defaults).choice)
        defaults.removePersistentDomain(forName: suite)
        // «Как в системе»: армянский телефон — армянский
        L10n.shared.setLanguage(.system)
        L10n.shared.systemLanguage = "hy"
        XCTAssertEqual(Lang.hy, L10n.shared.lang)
        L10n.shared.systemLanguage = "ru"
        XCTAssertEqual(Lang.ru, L10n.shared.lang)
        L10n.shared.systemLanguage = "de"
        XCTAssertEqual(Lang.en, L10n.shared.lang)
    }

    func testFormattingInArmenian() {
        L10n.shared.setLanguage(.hy)
        XCTAssertEqual("2 ԿԲ", sizeTitle(2048))
        XCTAssertEqual("12 Բ", sizeTitle(12))
        XCTAssertTrue(sizeTitle(5 * 1024 * 1024).hasSuffix("ՄԲ"))
        XCTAssertTrue(hasArmenian(outingTime(Date(timeIntervalSince1970: 1_790_000_000))))
    }

    func testGuideAndSoundsInArmenian() {
        L10n.shared.setLanguage(.hy)
        for c in Guide.chapters {
            // Бренды (PRISM) не переводятся
            XCTAssertTrue(c.title.hy == c.title.en || hasArmenian(c.title.text), c.id)
            for topic in c.topics {
                for step in topic.steps { XCTAssertTrue(hasArmenian(step.text), c.key(topic)) }
            }
        }
        XCTAssertTrue(!Guide.search("Մարիմբա").isEmpty || !Guide.search("ընկեր").isEmpty)
        for sound in NotifySound.allCases { XCTAssertTrue(hasArmenian(sound.title), sound.rawValue) }
        for style in SoundStyle.allCases { XCTAssertTrue(hasArmenian(style.title), style.rawValue) }
    }

    func testPremiumHiddenEverythingFree() {
        XCTAssertFalse(Premium.enabled)
        let model = AppModel(clock: { Date(timeIntervalSince1970: 1_790_000_000) }, demoReplies: false)
        XCTAssertEqual(Premium.openTier, model.state.plan)
        XCTAssertEqual(Premium.openTier, model.plan())
        for feature in PremiumFeature.allCases { XCTAssertTrue(model.has(feature), feature.rawValue) }
        XCTAssertTrue(model.require(.sounds))
        XCTAssertNil(model.state.paywall)
        // Экран тарифов не открывается
        model.openPremium()
        XCTAssertNotEqual(ProfileSection.premium, model.state.profileSection)
        // Что раньше было платным — работает: плейлисты без счёта
        for i in 0..<5 { XCTAssertNotNil(model.createPlaylist("Плейлист \(i)")) }
        model.refreshPlan()
        XCTAssertEqual(Premium.openTier, model.state.plan)
        // В гиде нет тем о Memory+
        XCTAssertTrue(Guide.chapters.flatMap(\.topics).allSatisfy { $0.action != .premium })
        XCTAssertTrue(PlanTier.business.covers(.plus))
    }

    func testLegalDocsInThreeLanguages() throws {
        XCTAssertEqual("2026-10-14", Legal.version)
        for doc in LegalDoc.allCases {
            XCTAssertEqual("\(doc.id).hy.md", doc.fileFor(.hy))
            XCTAssertEqual("\(doc.id).en.md", doc.fileFor(.en))
            XCTAssertEqual("\(doc.id).md", doc.fileFor(.ru))
            for suffix in ["", ".hy", ".en"] {
                let name = doc.id + suffix
                let url = Bundle.main.url(forResource: name, withExtension: "md")
                    ?? Bundle.main.url(forResource: name, withExtension: "md", subdirectory: "Legal")
                let found = try XCTUnwrap(url, name)
                let text = try String(contentsOf: found, encoding: .utf8)
                XCTAssertFalse(text.contains("152-ФЗ") || text.contains("Роскомнадзор"), name)
            }
            L10n.shared.setLanguage(.hy)
            XCTAssertTrue(hasArmenian(doc.load()), doc.id)
            L10n.shared.setLanguage(.ru)
        }
    }
}
