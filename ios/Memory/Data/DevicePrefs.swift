import Foundation

/// Настройки телефона, а не аккаунта: знакомство «выберите любимое» и
/// любимое, выбранное до регистрации, оформление. То же — в DevicePrefs.kt.
struct DevicePrefs {
    var defaults: UserDefaults = .standard

    /// Оформление — до первого экрана, чтобы не мигало. Язык и размер текста L10n читает сам.
    func load() {
        ThemeState.shared.mode = ThemeMode.of(defaults.string(forKey: "theme"))
    }

    /// Оформление — на этом телефоне, как язык и размер текста.
    func setTheme(_ mode: ThemeMode) {
        ThemeState.shared.mode = mode
        defaults.set(mode.id, forKey: "theme")
    }

    /// Качество фото и видео при отправке («Данные и сеть»).
    var mediaQuality: MediaQuality { MediaQuality.of(defaults.string(forKey: "mediaQuality")) }

    func setMediaQuality(_ q: MediaQuality) {
        defaults.set(q.rawValue, forKey: "mediaQuality")
    }

    /// Демо: «как будто нет сети».
    var demoOffline: Bool { defaults.bool(forKey: "demoOffline") }

    func setDemoOffline(_ on: Bool) {
        defaults.set(on, forKey: "demoOffline")
    }

    /// Знакомство уже показали на этом телефоне.
    var onboarded: Bool { defaults.bool(forKey: "onboarded") }

    func setOnboarded() {
        defaults.set(true, forKey: "onboarded")
    }

    /// Любимое, выбранное до регистрации: попадёт на полку нового аккаунта.
    func pendingPicks() -> [CatalogTitle] {
        (defaults.string(forKey: "picks") ?? "").components(separatedBy: "\n").compactMap { line in
            guard let tab = line.firstIndex(of: "\t") else { return nil }
            return Catalog.find(String(line[..<tab]), String(line[line.index(after: tab)...]))
        }
    }

    func savePicks(_ picks: [CatalogTitle]) {
        defaults.set(picks.map { $0.kind + "\t" + $0.title }.joined(separator: "\n"), forKey: "picks")
    }

    func clearPicks() {
        defaults.removeObject(forKey: "picks")
    }
}
