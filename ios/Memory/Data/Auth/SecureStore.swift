import Foundation
import Security

/// Хранилище аккаунтов на iPhone.
///
/// Ключи сессии («session.*») — в Связке ключей: она зашифрована
/// системой и недоступна другим приложениям. Остальное (соль, хеш
/// пароля, сведения профиля) — в UserDefaults: пароля там нет, только
/// производный ключ PBKDF2.
final class SecureStore: KeyValueStore {
    private let defaults: UserDefaults
    private let service: String

    init(defaults: UserDefaults = .standard, service: String = "ru.wishka.memory") {
        self.defaults = defaults
        self.service = service
    }

    func get(_ key: String) -> String? {
        isSecret(key) ? keychainGet(key) : defaults.string(forKey: key)
    }

    func put(_ key: String, _ value: String) {
        if isSecret(key) {
            keychainPut(key, value)
        } else {
            defaults.set(value, forKey: key)
        }
    }

    func remove(_ key: String) {
        if isSecret(key) {
            keychainRemove(key)
        } else {
            defaults.removeObject(forKey: key)
        }
    }

    private func isSecret(_ key: String) -> Bool { key.hasPrefix("session.") }

    // MARK: - Связка ключей

    private func query(_ key: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
        ]
    }

    private func keychainGet(_ key: String) -> String? {
        var q = query(key)
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        guard SecItemCopyMatching(q as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func keychainPut(_ key: String, _ value: String) {
        let data = Data(value.utf8)
        let update: [String: Any] = [kSecValueData as String: data]
        let status = SecItemUpdate(query(key) as CFDictionary, update as CFDictionary)
        if status == errSecItemNotFound {
            var add = query(key)
            add[kSecValueData as String] = data
            // Доступно после первой разблокировки и не уезжает в резервные копии на другие устройства
            add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            SecItemAdd(add as CFDictionary, nil)
        }
    }

    private func keychainRemove(_ key: String) {
        SecItemDelete(query(key) as CFDictionary)
    }
}
