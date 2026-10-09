import Foundation
import Observation

/// Язык приложения: как в системе телефона, русский или английский.
enum Lang: String, CaseIterable, Identifiable {
    case system, ru, en

    var id: String { rawValue }

    /// Названия языков — на своём языке; «Как в системе» переводится.
    var title: String {
        switch self {
        case .system: return t("Как в системе")
        case .ru: return "Русский"
        case .en: return "English"
        }
    }
}

/// Перевод интерфейса и размер текста — настройки телефона, а не
/// аккаунта: одни на всех, кто входит с этого iPhone, и действуют уже на
/// экране входа.
///
/// Строки в коде — по-русски; t() возвращает английский вариант из
/// словаря `english`, если выбран английский. Класс наблюдаемый: смена
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

    /// Русский — для русского и близких языков интерфейса телефона, иначе английский.
    var english: Bool {
        switch choice {
        case .en: return true
        case .ru: return false
        case .system: return !Self.russianSpeaking.contains(systemLanguage)
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
    L10n.shared.english ? (english[ru] ?? ru) : ru
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
