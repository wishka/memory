import Foundation

/// Юридическое: данные оператора, версия документов, возраст и согласие.
/// Тексты документов — файлы .md в docs/legal (их же публикует сайт);
/// iPhone читает свои копии из ios/Memory/Legal (scripts/sync_legal.py).
/// Данные оператора в квадратных скобках заполняет владелец — здесь,
/// в Legal.kt и в docs/legal. Те же правила — Legal.kt на Android.
enum Legal {
    /// Версия документов: поменялась — каждый вошедший заново принимает их.
    static let version = "2026-10-14"

    /// С какого возраста можно пользоваться Memory (и рейтинг в магазинах).
    static let minAge = 16

    static let operatorName = "[Название оператора: ООО «…» / ИП …, ՀՎՀՀ …]"
    static let privacyEmail = "[privacy@домен]"
    static let abuseEmail = "[abuse@домен]"
    /// Где опубликованы документы (GitHub Pages из docs или свой домен).
    static let site = "[https://сайт-memory]"

    /// Заполнены ли данные оператора — до этого в приложении видна пометка «черновик».
    static var filled: Bool {
        ![operatorName, privacyEmail, abuseEmail, site].contains { $0.hasPrefix("[") }
    }
}

/// Документы, которые видны в приложении: «Настройки → Документы», регистрация.
enum LegalDoc: String, CaseIterable, Identifiable {
    case terms, privacy, consent, community, recommendations, licenses

    var id: String { rawValue }

    private var ru: String {
        switch self {
        case .terms: return "Пользовательское соглашение"
        case .privacy: return "Политика конфиденциальности"
        case .consent: return "Согласие на обработку персональных данных"
        case .community: return "Правила сообщества"
        case .recommendations: return "Как работают рекомендации"
        case .licenses: return "Лицензии и авторы"
        }
    }

    var title: String { t(ru) }

    /// Файл на языке интерфейса: terms.hy.md (армянский — основная редакция),
    /// terms.md (русский), terms.en.md (английский).
    var file: String { fileFor(L10n.shared.lang) }

    func fileFor(_ lang: Lang) -> String {
        switch lang {
        case .hy: return "\(id).hy.md"
        case .en: return "\(id).en.md"
        default: return "\(id).md"
        }
    }

    /// Текст из сборки на языке интерфейса; нет перевода — русская редакция.
    /// Синхронизированная папка Xcode кладёт файлы в корень сборки, но
    /// сначала ищем в Legal — на случай, если папку скопируют целиком.
    func load(bundle: Bundle = .main) -> String {
        Self.read(file, bundle: bundle) ?? Self.read("\(id).md", bundle: bundle) ?? ""
    }

    private static func read(_ name: String, bundle: Bundle) -> String? {
        // «terms.hy.md» → ресурс «terms.hy» с расширением «md»
        let base = String(name.dropLast(3))
        guard let url = bundle.url(forResource: base, withExtension: "md", subdirectory: "Legal")
            ?? bundle.url(forResource: base, withExtension: "md"),
              let text = try? String(contentsOf: url, encoding: .utf8) else { return nil }
        return text
    }
}

/// Согласие с документами — на телефоне, по аккаунту: версия и время
/// («2026-10-14|1790000000000»). С сервером оно же уйдёт в журнал
/// согласий оператора.
struct ConsentStore {
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func accepted(_ username: String) -> Bool { version(username) == Legal.version }

    func version(_ username: String) -> String? {
        guard let value = defaults.string(forKey: key(username)) else { return nil }
        return value.split(separator: "|", maxSplits: 1, omittingEmptySubsequences: false).first.map(String.init)
    }

    /// Когда приняты документы, мс с 1970; 0 — не приняты.
    func acceptedAt(_ username: String) -> Int64 {
        guard let value = defaults.string(forKey: key(username)),
              let bar = value.firstIndex(of: "|") else { return 0 }
        return Int64(value[value.index(after: bar)...]) ?? 0
    }

    func accept(_ username: String, now: Date = Date()) {
        let millis = Int64((now.timeIntervalSince1970 * 1000).rounded())
        defaults.set("\(Legal.version)|\(millis)", forKey: key(username))
    }

    func forget(_ username: String) {
        defaults.removeObject(forKey: key(username))
    }

    private func key(_ username: String) -> String {
        var name = username.trimmingCharacters(in: .whitespacesAndNewlines)
        if name.hasPrefix("@") { name.removeFirst() }
        return "consent." + name.lowercased()
    }
}
