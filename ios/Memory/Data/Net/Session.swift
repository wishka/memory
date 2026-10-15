import Foundation

/// Вход на сервер: короткий токен доступа (15 минут) и долгий токен
/// обновления. Хранятся в Связке ключей (SecureStore — ключи «session.…»
/// уходят туда), у каждого аккаунта свои.
///
/// Токен доступа истёк — Api сам обновит его одним запросом и повторит
/// исходный. Обновить не вышло (вышли с этого устройства на другом) —
/// сессия стирается, и приложение просит войти заново. То же — в Session.kt.
final class Session {
    private let store: KeyValueStore?
    private let prefix: String
    private let lock = NSLock()
    private var _access: String?
    private var _refresh: String?
    private var _deviceId: String?
    private var _userId: Int

    init(store: KeyValueStore?, login: String) {
        self.store = store
        self.prefix = "session.api.\(login.lowercased())."
        _access = store?.get(prefix + "access")
        _refresh = store?.get(prefix + "refresh")
        _deviceId = store?.get(prefix + "device")
        _userId = Int(store?.get(prefix + "user") ?? "") ?? 0
    }

    var access: String? {
        lock.lock()
        defer { lock.unlock() }
        return _access
    }

    var refresh: String? {
        lock.lock()
        defer { lock.unlock() }
        return _refresh
    }

    var deviceId: String? {
        lock.lock()
        defer { lock.unlock() }
        return _deviceId
    }

    /// Свой номер на сервере (ответ входа: user.id); 0 — неизвестен (демо-сервер его не шлёт).
    var userId: Int {
        lock.lock()
        defer { lock.unlock() }
        return _userId
    }

    var signedIn: Bool { refresh != nil }

    /// Ответ входа или обновления: {access, refresh, expiresIn, deviceId, user}.
    func save(_ o: JSONObject) {
        let access = Json.string(o, "access")
        let refresh = Json.string(o, "refresh")
        let device = Json.string(o, "deviceId")
        let user: Int = Json.object(o, "user").map { Json.int($0, "id") } ?? 0
        lock.lock()
        _access = access.isEmpty ? nil : access
        if !refresh.isEmpty { _refresh = refresh }
        if !device.isEmpty { _deviceId = device }
        if user > 0 { _userId = user }
        let a = _access
        let r = _refresh
        let d = _deviceId
        let u = _userId
        lock.unlock()
        guard let store else { return }
        if let a { store.put(prefix + "access", a) }
        if let r { store.put(prefix + "refresh", r) }
        if let d { store.put(prefix + "device", d) }
        if u > 0 { store.put(prefix + "user", String(u)) }
    }

    func clear() {
        lock.lock()
        _access = nil
        _refresh = nil
        _deviceId = nil
        // Номер на сервере остаётся: он не меняется, а записи с ним уже на телефоне
        lock.unlock()
        guard let store else { return }
        for name in ["access", "refresh", "device"] { store.remove(prefix + name) }
    }
}

/// Устройство, с которого вошли: «iPhone», «Pixel 8», «Chrome · macOS».
struct DeviceInfo: Equatable, Identifiable {
    let id: String
    let name: String
    let platform: String
    /// Когда было в сети (мс).
    let lastSeen: Int64
    /// Это устройство.
    let current: Bool
}

/// Профиль для синхронизации между телефонами: что человек рассказал о себе и настройки приватности.
struct ProfileSync {
    let json: JSONObject
    let updatedAt: Int64
}
