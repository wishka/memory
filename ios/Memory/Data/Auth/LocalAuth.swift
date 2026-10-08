import Foundation

/// Хранилище «ключ → строка». На телефоне — UserDefaults и Связка ключей
/// для сессии (SecureStore), в тестах — обычный словарь.
protocol KeyValueStore: AnyObject {
    func get(_ key: String) -> String?
    func put(_ key: String, _ value: String)
    func remove(_ key: String)
}

/// Хранилище в памяти — для тестов и превью.
final class MemoryStore: KeyValueStore {
    private(set) var values: [String: String] = [:]
    private let lock = NSLock()

    func get(_ key: String) -> String? {
        lock.lock(); defer { lock.unlock() }
        return values[key]
    }

    func put(_ key: String, _ value: String) {
        lock.lock(); defer { lock.unlock() }
        values[key] = value
    }

    func remove(_ key: String) {
        lock.lock(); defer { lock.unlock() }
        values[key] = nil
    }
}

/// Аккаунт на этом устройстве и то, что человек рассказал о себе.
struct Account: Equatable {
    var username: String
    var displayName: String
    var info: ProfileInfo = ProfileInfo()
}

enum AuthResult: Equatable {
    case ok(Account)
    case error(String)
}

/// Вход по логину и паролю без сервера: аккаунты живут на телефоне.
///
/// Сессия запоминается: после входа приложение больше не спрашивает
/// пароль, пока человек сам не выйдет из профиля. Когда у Memory появится
/// сервер, вместо этого класса встанет сетевой — экраны не изменятся:
/// они знают только register / login / logout / current.
///
/// Защита от подбора: после maxFailures неудачных попыток подряд вход
/// для этого логина закрывается на lockMs.
///
/// Правила те же, что в Android-версии (LocalAuth.kt).
final class LocalAuth {
    static let maxFailures = 5
    static let lockMs: Int64 = 30_000
    static let demoLogin = "demo"
    static let demoPassword = "memory2026"

    static let sessionUser = "session.user"
    static let sessionToken = "session.token"

    private let store: KeyValueStore
    private let clock: () -> Int64
    private let iterations: Int

    init(
        store: KeyValueStore,
        clock: @escaping () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) },
        iterations: Int = PasswordHasher.iterations
    ) {
        self.store = store
        self.clock = clock
        self.iterations = iterations
    }

    /// Текущий вошедший пользователь или nil. Сессия переживает перезапуск.
    func current() -> Account? {
        guard let username = store.get(Self.sessionUser) else { return nil }
        guard let token = store.get(Self.sessionToken), !token.isEmpty else { return nil }
        return account(username)
    }

    func register(username: String, displayName: String, password: String, repeatPassword repeated: String) -> AuthResult {
        let login = Self.normalize(username)
        if let error = Self.validateLogin(login) { return .error(error) }
        let name = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        if name.isEmpty { return .error("Укажите имя") }
        if name.count > 40 { return .error("Имя длиннее 40 символов") }
        if let error = Self.validatePassword(password) { return .error(error) }
        if password != repeated { return .error("Пароли не совпадают") }
        if store.get(key(login, "hash")) != nil { return .error("Логин @\(login) уже занят") }

        let salt = PasswordHasher.newSalt()
        store.put(key(login, "salt"), salt.hex)
        store.put(key(login, "hash"), PasswordHasher.hash(password, salt: salt, iterations: iterations).hex)
        store.put(key(login, "iter"), String(iterations))
        store.put(key(login, "name"), name)
        return startSession(login)
    }

    func login(username: String, password: String) -> AuthResult {
        let login = Self.normalize(username)
        if login.isEmpty || password.isEmpty { return .error("Введите логин и пароль") }

        let lockedUntil = store.get(key(login, "locked")).flatMap { Int64($0) } ?? 0
        let now = clock()
        if now < lockedUntil {
            let sec = max(1, (lockedUntil - now) / 1000)
            return .error("Слишком много попыток. Попробуйте через \(sec) с")
        }

        let hash = store.get(key(login, "hash"))
        let salt = store.get(key(login, "salt"))
        let iter = store.get(key(login, "iter")).flatMap { Int($0) } ?? PasswordHasher.iterations
        // Одинаковый ответ для «нет такого логина» и «неверный пароль»:
        // иначе по ответу можно узнать, какие логины существуют
        var ok = false
        if let hash, let salt, let saltBytes = [UInt8](hex: salt), let hashBytes = [UInt8](hex: hash) {
            ok = PasswordHasher.verify(password, salt: saltBytes, expected: hashBytes, iterations: iter)
        }
        if !ok {
            if hash != nil { registerFailure(login, now: now) }
            return .error("Неверный логин или пароль")
        }
        store.remove(key(login, "fails"))
        store.remove(key(login, "locked"))
        return startSession(login)
    }

    func logout() {
        store.remove(Self.sessionUser)
        store.remove(Self.sessionToken)
    }

    /// Новое имя для профиля.
    func rename(username: String, displayName: String) {
        let name = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        if !name.isEmpty { store.put(key(Self.normalize(username), "name"), String(name.prefix(40))) }
    }

    /// Сведения профиля: переживают перезапуск вместе с аккаунтом.
    func saveProfile(username: String, info: ProfileInfo) {
        let login = Self.normalize(username)
        store.put(key(login, "p.bio"), Self.clip(info.bio, 160))
        store.put(key(login, "p.city"), Self.clip(info.city, 40))
        store.put(key(login, "p.birthday"), Self.clip(info.birthday, 20))
        store.put(key(login, "p.website"), Self.clip(info.website, 80))
        store.put(key(login, "p.interests"), info.interests.joined(separator: "|"))
    }

    /// Демо-аккаунт для первого знакомства с приложением; ничего не делает, если он уже есть.
    func ensureDemoAccount() {
        if store.get(key(Self.demoLogin, "hash")) != nil { return }
        let salt = PasswordHasher.newSalt()
        store.put(key(Self.demoLogin, "salt"), salt.hex)
        store.put(key(Self.demoLogin, "hash"), PasswordHasher.hash(Self.demoPassword, salt: salt, iterations: iterations).hex)
        store.put(key(Self.demoLogin, "iter"), String(iterations))
        store.put(key(Self.demoLogin, "name"), "Гость Memory")
    }

    // MARK: - Внутреннее

    private func startSession(_ login: String) -> AuthResult {
        store.put(Self.sessionUser, login)
        store.put(Self.sessionToken, PasswordHasher.randomToken())
        return .ok(account(login) ?? Account(username: login, displayName: login))
    }

    private func account(_ login: String) -> Account? {
        guard store.get(key(login, "hash")) != nil else { return nil }
        let interests = (store.get(key(login, "p.interests")) ?? "")
            .split(separator: "|")
            .map(String.init)
            .filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
        let info = ProfileInfo(
            bio: store.get(key(login, "p.bio")) ?? "",
            city: store.get(key(login, "p.city")) ?? "",
            birthday: store.get(key(login, "p.birthday")) ?? "",
            website: store.get(key(login, "p.website")) ?? "",
            interests: interests
        )
        return Account(username: login, displayName: store.get(key(login, "name")) ?? login, info: info)
    }

    private func registerFailure(_ login: String, now: Int64) {
        let fails = (store.get(key(login, "fails")).flatMap { Int($0) } ?? 0) + 1
        if fails >= Self.maxFailures {
            store.put(key(login, "locked"), String(now + Self.lockMs))
            store.remove(key(login, "fails"))
        } else {
            store.put(key(login, "fails"), String(fails))
        }
    }

    private func key(_ login: String, _ field: String) -> String { "acc.\(login).\(field)" }

    private static func clip(_ s: String, _ n: Int) -> String {
        String(s.trimmingCharacters(in: .whitespacesAndNewlines).prefix(n))
    }

    // MARK: - Правила

    static func normalize(_ username: String) -> String {
        var s = username.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.hasPrefix("@") { s.removeFirst() }
        return s.lowercased()
    }

    private static let loginChars = Set("abcdefghijklmnopqrstuvwxyz0123456789._")

    static func validateLogin(_ login: String) -> String? {
        if login.count < 3 { return "Логин — от 3 символов" }
        if login.count > 20 { return "Логин — не длиннее 20 символов" }
        if !login.allSatisfy({ loginChars.contains($0) }) {
            return "Логин: латинские буквы, цифры, точка и подчёркивание"
        }
        return nil
    }

    static func validatePassword(_ password: String) -> String? {
        if password.count < 8 { return "Пароль — от 8 символов" }
        if !password.contains(where: { $0.isLetter }) || !password.contains(where: { $0.isNumber }) {
            return "В пароле нужны буквы и цифры"
        }
        return nil
    }
}
