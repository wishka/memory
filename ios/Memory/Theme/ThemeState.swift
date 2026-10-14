import Foundation
import Observation

/// Оформление: как в системе, всегда тёмное или всегда светлое. То же — в Color.kt.
enum ThemeMode: String, CaseIterable, Identifiable {
    case system = "system"
    case dark = "dark"
    case light = "light"

    var id: String { rawValue }

    /// Подпись в настройках на выбранном языке.
    var title: String {
        switch self {
        case .system: return t("Как в системе")
        case .dark: return t("Тёмное")
        case .light: return t("Светлое")
        }
    }

    static func of(_ id: String?) -> ThemeMode { id.flatMap { ThemeMode(rawValue: $0) } ?? .system }
}

/// Какая тема сейчас. @Observable: поменяли — экраны перерисовываются
/// сами, потому что Palette читает это состояние при каждом обращении.
///
/// forceDark — тёмная поверх всего, что всегда ночное: заставка и вход
/// (ночное небо), PRISM, эфир, звонок, история, просмотр фото на весь экран.
@Observable
final class ThemeState {
    static let shared = ThemeState()

    var mode: ThemeMode = .system
    /// Тема телефона; обновляет MemoryApp.
    var systemDark = true
    /// Тёмная, пока на экране заставка, вход или знакомство — ночное небо.
    var stageDark = false
    /// Тёмная, пока открыто то, что всегда ночное: PRISM, эфир, звонок, история, фото на весь экран.
    var overlayDark = false

    var forceDark: Bool { stageDark || overlayDark }

    /// Выбор человека без временного «тёмного поверх» — для настроек и строки состояния.
    var chosenLight: Bool {
        switch mode {
        case .system: return !systemDark
        case .light: return true
        case .dark: return false
        }
    }

    /// Светлая ли тема на экране сейчас.
    var light: Bool { !forceDark && chosenLight }
}
