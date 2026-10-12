import Foundation

/// Настройки телефона, а не аккаунта: знакомство «выберите любимое» и
/// любимое, выбранное до регистрации. То же — в DevicePrefs.kt.
struct DevicePrefs {
    var defaults: UserDefaults = .standard

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
