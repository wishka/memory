import Foundation
import Observation

/// Язык приложения: как в системе телефона, русский, английский или армянский.
enum Lang: String, CaseIterable, Identifiable {
    case system, ru, en, hy

    var id: String { rawValue }

    /// Названия языков — на своём языке; «Как в системе» переводится.
    var title: String {
        switch self {
        case .system: return t("Как в системе")
        case .ru: return "Русский"
        case .en: return "English"
        case .hy: return "Հայերեն"
        }
    }
}

/// Перевод интерфейса и размер текста — настройки телефона, а не
/// аккаунта: одни на всех, кто входит с этого iPhone, и действуют уже на
/// экране входа.
///
/// Строки в коде — по-русски; t() возвращает английский вариант из
/// словаря `english` или армянский из словаря `armenian`, если выбран
/// английский или армянский. Класс наблюдаемый: смена
/// языка в настройках сразу перерисовывает экраны. Тексты людей (записи,
/// сообщения, имена) не переводятся — только интерфейс.
@Observable
final class L10n {
    static let shared = L10n()

    /// Варианты размера текста: мелкий, обычный, крупный, очень крупный.
    static let textScales: [Double] = [0.9, 1, 1.15, 1.3]

    private(set) var choice: Lang
    private(set) var textScale: Double

    /// Язык телефона — читается при запуске.
    @ObservationIgnored var systemLanguage: String
    @ObservationIgnored private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        choice = Lang(rawValue: defaults.string(forKey: "lang") ?? "") ?? .system
        let saved = defaults.double(forKey: "textScale")
        textScale = saved == 0 ? 1 : min(max(saved, Self.textScales.first!), Self.textScales.last!)
        systemLanguage = Locale.preferredLanguages.first.map { String($0.prefix(2)) } ?? "ru"
    }

    /// Язык, который действует сейчас. «Как в системе»: армянский телефон —
    /// армянский, русский и близкие — русский, остальные — английский.
    var lang: Lang {
        guard choice == .system else { return choice }
        if systemLanguage == "hy" { return .hy }
        return Self.russianSpeaking.contains(systemLanguage) ? .ru : .en
    }

    var english: Bool { lang == .en }

    var armenian: Bool { lang == .hy }

    /// Локаль для дат и чисел.
    var locale: Locale {
        switch lang {
        case .en: return Locale(identifier: "en_US")
        case .hy: return Locale(identifier: "hy_AM")
        default: return Locale(identifier: "ru")
        }
    }

    /// Готовая строка на одном из трёх языков (каталоги, гид).
    func pick(ru: String, en: String, hy: String) -> String {
        switch lang {
        case .en: return en
        case .hy: return hy
        default: return ru
        }
    }

    func setLanguage(_ lang: Lang) {
        choice = lang
        defaults.set(lang.rawValue, forKey: "lang")
    }

    func setTextScale(_ scale: Double) {
        textScale = scale
        defaults.set(scale, forKey: "textScale")
    }

    private static let russianSpeaking: Set<String> = ["ru", "be", "kk", "ky"]
}

/// Строка интерфейса на выбранном языке.
func t(_ ru: String) -> String {
    switch L10n.shared.lang {
    case .en: return english[ru] ?? ru
    case .hy: return armenian[ru] ?? ru
    default: return ru
    }
}

/// Строка с подстановками: tf("Вы в %s", x) — каждое %s по порядку.
func tf(_ ru: String, _ args: Any...) -> String {
    var out = t(ru)
    for a in args {
        if let r = out.range(of: "%s") {
            out.replaceSubrange(r, with: "\(a)")
        }
    }
    return out
}
