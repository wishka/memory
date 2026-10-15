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

    /// Сколько кодов восстановления выдаётся за раз.
    static let recoveryCodes = 10
    /// Сколько живёт код из письма, мс.
    static let codeTtlMs: Int64 = 15 * 60_000
    /// Сколько раз можно ошибиться в коде из письма.
    static let codeTries = 5

    private static let recoveryFields = [
        "rc", "rc.salt", "email", "email.ok",
        "code.email", "code.email.until", "code.email.tries", "code.reset", "code.reset.until", "code.reset.tries",
    ]

    private let store: KeyValueStore
    private let clock: () -> Int64
    private let iterations: Int
    /// Кто отправляет письма с кодом. Пока сервера нет — демо: код показывается на экране.
    private let mail: MailSender

    init(
        store: KeyValueStore,
        clock: @escaping () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) },
        iterations: Int = PasswordHasher.iterations,
        mail: MailSender = DemoMail.shared
    ) {
        self.store = store
        self.clock = clock
        self.iterations = iterations
        self.mail = mail
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
        if name.isEmpty { return .error(t("Укажите имя")) }
        if name.count > 40 { return .error(t("Имя длиннее 40 символов")) }
        if let error = Self.validatePassword(password) { return .error(error) }
        if password != repeated { return .error(t("Пароли не совпадают")) }
        if store.get(key(login, "hash")) != nil { return .error(tf("Логин @%s уже занят", login)) }

        let salt = PasswordHasher.newSalt()
        store.put(key(login, "salt"), salt.hex)
        store.put(key(login, "hash"), PasswordHasher.hash(password, salt: salt, iterations: iterations).hex)
        store.put(key(login, "iter"), String(iterations))
        store.put(key(login, "name"), name)
        return startSession(login)
    }

    func login(username: String, password: String) -> AuthResult {
        let login = Self.normalize(username)
        if login.isEmpty || password.isEmpty { return .error(t("Введите логин и пароль")) }

        let lockedUntil = store.get(key(login, "locked")).flatMap { Int64($0) } ?? 0
        let now = clock()
        if now < lockedUntil {
            let sec = max(1, (lockedUntil - now) / 1000)
            return .error(tf("Слишком много попыток. Попробуйте через %s с", sec))
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
            return .error(t("Неверный логин или пароль"))
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
        store.put(key(login, "p.avatar"), info.avatar)
    }

    /// Сменить пароль: нужен текущий. nil — готово, иначе текст ошибки.
    /// Сессия остаётся: человек не выходит из профиля.
    func changePassword(username: String, current: String, new: String, repeatPassword repeated: String) -> String? {
        let login = Self.normalize(username)
        if current.isEmpty { return t("Введите текущий пароль") }
        if !checkPassword(login, current) { return t("Текущий пароль не подходит") }
        if let error = Self.validatePassword(new) { return error }
        if new != repeated { return t("Пароли не совпадают") }
        if new == current { return t("Новый пароль совпадает с текущим") }
        let salt = PasswordHasher.newSalt()
        store.put(key(login, "salt"), salt.hex)
        store.put(key(login, "hash"), PasswordHasher.hash(new, salt: salt, iterations: iterations).hex)
        store.put(key(login, "iter"), String(iterations))
        return nil
    }

    /// Удалить аккаунт с этого телефона — после проверки пароля. Сессия
    /// завершается. nil — удалён, иначе текст ошибки.
    func deleteAccount(username: String, password: String) -> String? {
        let login = Self.normalize(username)
        if password.isEmpty { return t("Введите пароль") }
        if !checkPassword(login, password) { return t("Пароль не подходит") }
        for field in ["salt", "hash", "iter", "name", "fails", "locked",
                      "p.bio", "p.city", "p.birthday", "p.website", "p.interests", "p.avatar"] + Self.recoveryFields {
            store.remove(key(login, field))
        }
        if store.get(Self.sessionUser) == login { logout() }
        return nil
    }

    // MARK: - Восстановление доступа

    /// Десять новых одноразовых кодов восстановления; прежние перестают
    /// работать. Сами коды не хранятся — только их хеши, как у пароля:
    /// показать их можно один раз, сразу после создания.
    func newRecoveryCodes(_ username: String) -> [String] {
        let login = Self.normalize(username)
        guard store.get(key(login, "hash")) != nil else { return [] }
        let salt = PasswordHasher.newSalt()
        let codes = (0..<Self.recoveryCodes).map { _ in RecoveryCodes.generate() }
        store.put(key(login, "rc.salt"), salt.hex)
        store.put(key(login, "rc"), codes.map {
            PasswordHasher.hash(RecoveryCodes.normalize($0), salt: salt, iterations: recoveryIterations).hex
        }.joined(separator: ","))
        return codes
    }

    /// Сколько неиспользованных кодов осталось.
    func recoveryLeft(_ username: String) -> Int {
        (store.get(key(Self.normalize(username), "rc")) ?? "").split(separator: ",").filter { !$0.isEmpty }.count
    }

    /// Вход по коду восстановления с новым паролем. Код одноразовый: после
    /// входа он больше не подходит. Неверный код считается неудачной
    /// попыткой входа — подбор упирается в ту же блокировку.
    func recoverWithCode(username: String, code: String, new: String, repeatPassword repeated: String) -> AuthResult {
        let login = Self.normalize(username)
        if login.isEmpty || code.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return .error(t("Введите логин и код")) }
        if let error = locked(login) { return .error(error) }
        if let error = Self.validatePassword(new) { return .error(error) }
        if new != repeated { return .error(t("Пароли не совпадают")) }
        let salt = store.get(key(login, "rc.salt")).flatMap { [UInt8](hex: $0) }
        let hashes = (store.get(key(login, "rc")) ?? "").split(separator: ",").map(String.init).filter { !$0.isEmpty }
        let normalized = RecoveryCodes.normalize(code)
        var hit = -1
        if let salt, normalized.count == RecoveryCodes.length {
            let h = PasswordHasher.hash(normalized, salt: salt, iterations: recoveryIterations).hex
            hit = hashes.firstIndex { Self.sameText($0, h) } ?? -1
        }
        if hit < 0 {
            if store.get(key(login, "hash")) != nil { registerFailure(login, now: clock()) }
            return .error(t("Неверный логин или код"))
        }
        store.put(key(login, "rc"), hashes.enumerated().filter { $0.offset != hit }.map { $0.element }.joined(separator: ","))
        setPassword(login, new)
        return startSession(login)
    }

    /// Почта аккаунта ("" — не указана).
    func email(_ username: String) -> String { store.get(key(Self.normalize(username), "email")) ?? "" }

    /// Подтверждена ли почта.
    func emailConfirmed(_ username: String) -> Bool { store.get(key(Self.normalize(username), "email.ok")) == "1" }

    /// Указать почту: на неё уходит шестизначный код подтверждения. До
    /// подтверждения почта не годится для восстановления. nil — письмо
    /// отправлено, иначе текст ошибки.
    func setEmail(_ username: String, _ email: String) -> String? {
        let login = Self.normalize(username)
        let address = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if !Self.matches(Self.emailPattern, address) { return t("Похоже, в адресе почты ошибка") }
        store.put(key(login, "email"), address)
        store.remove(key(login, "email.ok"))
        sendCode(login, address, "email")
        return nil
    }

    /// Код из письма подтверждает почту. nil — подтверждена.
    func confirmEmail(_ username: String, _ code: String) -> String? {
        let login = Self.normalize(username)
        if let error = checkCode(login, "email", code) { return error }
        store.put(key(login, "email.ok"), "1")
        return nil
    }

    /// Убрать почту из аккаунта.
    func removeEmail(_ username: String) {
        let login = Self.normalize(username)
        for field in ["email", "email.ok", "code.email", "code.email.until", "code.email.tries"] {
            store.remove(key(login, field))
        }
    }

    /// «Забыли пароль?» по почте: если у логина есть подтверждённая почта,
    /// на неё уходит код. Ответ одинаковый, есть почта или нет, — иначе по
    /// нему можно было бы узнать, какие логины существуют.
    func startEmailRecovery(_ username: String) -> String {
        let login = Self.normalize(username)
        if emailConfirmed(login) && !email(login).isEmpty { sendCode(login, email(login), "reset") }
        return t("Если у этого логина есть подтверждённая почта, на неё пришло письмо с кодом.")
    }

    /// Код из письма + новый пароль: вход с новым паролем.
    func recoverWithEmail(username: String, code: String, new: String, repeatPassword repeated: String) -> AuthResult {
        let login = Self.normalize(username)
        if let error = locked(login) { return .error(error) }
        if let error = Self.validatePassword(new) { return .error(error) }
        if new != repeated { return .error(t("Пароли не совпадают")) }
        if let error = checkCode(login, "reset", code) {
            if store.get(key(login, "hash")) != nil { registerFailure(login, now: clock()) }
            return .error(error)
        }
        setPassword(login, new)
        return startSession(login)
    }

    /// Почта в виде «a•••@mail.ru» — для подсказок, без раскрытия адреса целиком.
    func maskedEmail(_ username: String) -> String { Self.maskEmail(email(username)) }

    /// «anna@mail.ru» → «a•••@mail.ru».
    static func maskEmail(_ email: String) -> String {
        guard let at = email.firstIndex(of: "@") else { return email }
        let name = email[..<at]
        return String(name.prefix(1)) + "•••@" + String(email[email.index(after: at)...])
    }

    /// Пароль подходит — для действий, которые просят подтверждения (новые коды).
    func checkPasswordOf(_ username: String, _ password: String) -> Bool {
        checkPassword(Self.normalize(username), password)
    }

    private func sendCode(_ login: String, _ address: String, _ purpose: String) {
        let code = RecoveryCodes.digits()
        store.put(key(login, "code.\(purpose)"), PasswordHasher.hash(code, salt: Array(login.utf8), iterations: 1_000).hex)
        store.put(key(login, "code.\(purpose).until"), String(clock() + Self.codeTtlMs))
        store.remove(key(login, "code.\(purpose).tries"))
        mail.send(email: address, code: code)
    }

    private func checkCode(_ login: String, _ purpose: String, _ code: String) -> String? {
        guard let saved = store.get(key(login, "code.\(purpose)")) else { return t("Код устарел — запросите новый") }
        let until = store.get(key(login, "code.\(purpose).until")).flatMap { Int64($0) } ?? 0
        if clock() > until { return t("Код устарел — запросите новый") }
        let tries = (store.get(key(login, "code.\(purpose).tries")).flatMap { Int($0) } ?? 0) + 1
        let digits = code.filter { $0.isASCII && $0.isNumber }
        let ok = Self.sameText(PasswordHasher.hash(digits, salt: Array(login.utf8), iterations: 1_000).hex, saved)
        if !ok {
            if tries >= Self.codeTries {
                store.remove(key(login, "code.\(purpose)"))
                return t("Слишком много попыток — запросите новый код")
            }
            store.put(key(login, "code.\(purpose).tries"), String(tries))
            return t("Код не подходит")
        }
        for field in ["code.\(purpose)", "code.\(purpose).until", "code.\(purpose).tries"] {
            store.remove(key(login, field))
        }
        return nil
    }

    /// Вход закрыт после неудачных попыток — текст ошибки; nil — открыт.
    private func locked(_ login: String) -> String? {
        let lockedUntil = store.get(key(login, "locked")).flatMap { Int64($0) } ?? 0
        let now = clock()
        if now >= lockedUntil { return nil }
        let sec = max(1, (lockedUntil - now) / 1000)
        return tf("Слишком много попыток. Попробуйте через %s с", sec)
    }

    private func setPassword(_ login: String, _ password: String) {
        let salt = PasswordHasher.newSalt()
        store.put(key(login, "salt"), salt.hex)
        store.put(key(login, "hash"), PasswordHasher.hash(password, salt: salt, iterations: iterations).hex)
        store.put(key(login, "iter"), String(iterations))
        store.remove(key(login, "fails"))
        store.remove(key(login, "locked"))
    }

    private var recoveryIterations: Int { max(1, iterations / 12) }

    /// Сравнение за постоянное время.
    private static func sameText(_ a: String, _ b: String) -> Bool {
        let x = Array(a.utf8), y = Array(b.utf8)
        guard x.count == y.count else { return false }
        var diff: UInt8 = 0
        for i in 0..<x.count { diff |= x[i] ^ y[i] }
        return diff == 0
    }

    private static let emailPattern = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$"

    private static func matches(_ pattern: String, _ s: String) -> Bool {
        guard let re = try? NSRegularExpression(pattern: pattern) else { return false }
        let range = NSRange(s.startIndex..<s.endIndex, in: s)
        return re.firstMatch(in: s, range: range) != nil
    }

    private func checkPassword(_ login: String, _ password: String) -> Bool {
        guard let hash = store.get(key(login, "hash")), let salt = store.get(key(login, "salt")),
              let saltBytes = [UInt8](hex: salt), let hashBytes = [UInt8](hex: hash) else { return false }
        let iter = store.get(key(login, "iter")).flatMap { Int($0) } ?? PasswordHasher.iterations
        return PasswordHasher.verify(password, salt: saltBytes, expected: hashBytes, iterations: iter)
    }

    /// Демо-аккаунт для первого знакомства с приложением; ничего не делает, если он уже есть.
    func ensureDemoAccount() {
        if store.get(key(Self.demoLogin, "hash")) != nil { return }
        let salt = PasswordHasher.newSalt()
        store.put(key(Self.demoLogin, "salt"), salt.hex)
        store.put(key(Self.demoLogin, "hash"), PasswordHasher.hash(Self.demoPassword, salt: salt, iterations: iterations).hex)
        store.put(key(Self.demoLogin, "iter"), String(iterations))
        store.put(key(Self.demoLogin, "name"), t("Гость Memory"))
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
            interests: interests,
            avatar: store.get(key(login, "p.avatar")) ?? ""
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
        if login.count < 3 { return t("Логин — от 3 символов") }
        if login.count > 20 { return t("Логин — не длиннее 20 символов") }
        if !login.allSatisfy({ loginChars.contains($0) }) {
            return t("Логин: латинские буквы, цифры, точка и подчёркивание")
        }
        return nil
    }

    static func validatePassword(_ password: String) -> String? {
        if password.count < 8 { return t("Пароль — от 8 символов") }
        if !password.contains(where: { $0.isLetter }) || !password.contains(where: { $0.isNumber }) {
            return t("В пароле нужны буквы и цифры")
        }
        return nil
    }
}

/// Отправка письма с кодом. Когда появится сервер — письмо уйдёт с него.
protocol MailSender: AnyObject {
    func send(email: String, code: String)
}

/// Демо-почта: сервера нет, письмо не отправить — последний код виден на
/// экране подтверждения («Демо: код 123456»). Заменить на сетевую
/// отправку — передать в LocalAuth свой MailSender.
final class DemoMail: MailSender {
    static let shared = DemoMail()

    private static let lock = NSLock()
    private static var lastSent: (email: String, code: String)?

    /// Последнее «письмо»: адрес и код.
    static var last: (email: String, code: String)? {
        lock.lock(); defer { lock.unlock() }
        return lastSent
    }

    func send(email: String, code: String) {
        Self.lock.lock(); defer { Self.lock.unlock() }
        Self.lastSent = (email, code)
    }
}

/// Коды восстановления: «K7QF-2MXP» — 8 знаков без похожих букв и цифр.
enum RecoveryCodes {
    static let length = 8
    private static let alphabet = Array("ABCDEFGHJKLMNPQRSTUVWXYZ23456789")
    private static let alphabetSet = Set(alphabet)

    static func generate() -> String {
        var rng = SystemRandomNumberGenerator()
        let raw = String((0..<length).map { _ in alphabet[Int.random(in: 0..<alphabet.count, using: &rng)] })
        return String(raw.prefix(4)) + "-" + String(raw.dropFirst(4))
    }

    /// Шестизначный код для письма.
    static func digits() -> String {
        var rng = SystemRandomNumberGenerator()
        return (0..<6).map { _ in String(Int.random(in: 0..<10, using: &rng)) }.joined()
    }

    /// Как человек ни ввёл — «k7qf 2mxp», «K7QF-2MXP» — одно и то же.
    static func normalize(_ code: String) -> String {
        String(code.uppercased().filter { alphabetSet.contains($0) })
    }
}
