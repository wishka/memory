import XCTest
@testable import Memory

/// Вход по логину и паролю: регистрация, запоминание, защита от подбора.
/// Те же проверки, что LocalAuthTest на Android.
final class LocalAuthTests: XCTestCase {
    private var now: Int64 = 1_000_000
    private let store = MemoryStore()

    // Меньше итераций, чтобы тесты шли быстро; логика та же
    private func auth() -> LocalAuth {
        LocalAuth(store: store, clock: { [unowned self] in self.now }, iterations: 1_000)
    }

    private func error(_ result: AuthResult) -> String {
        if case .error(let message) = result { return message }
        XCTFail("ожидалась ошибка, а пришло \(result)")
        return ""
    }

    private func isOk(_ result: AuthResult) -> Bool {
        if case .ok = result { return true }
        return false
    }

    func testRegisterSignsInAndIsRemembered() {
        XCTAssertTrue(isOk(auth().register(username: "Vera.Moon", displayName: "Вера", password: "secret123", repeatPassword: "secret123")))
        // «Перезапуск»: новый объект на том же хранилище — человек всё ещё вошёл
        XCTAssertEqual(auth().current()?.username, "vera.moon")
        XCTAssertEqual(auth().current()?.displayName, "Вера")
    }

    func testPasswordIsNotStored() {
        _ = auth().register(username: "mark", displayName: "Марк", password: "secret123", repeatPassword: "secret123")
        XCTAssertFalse(store.values.values.contains { $0.contains("secret123") })
    }

    func testRegistrationRules() {
        let a = auth()
        XCTAssertTrue(error(a.register(username: "ab", displayName: "Имя", password: "secret123", repeatPassword: "secret123")).contains("3"))
        XCTAssertTrue(error(a.register(username: "кириллица", displayName: "Имя", password: "secret123", repeatPassword: "secret123")).contains("латинские"))
        XCTAssertTrue(error(a.register(username: "alina", displayName: "", password: "secret123", repeatPassword: "secret123")).contains("имя"))
        XCTAssertTrue(error(a.register(username: "alina", displayName: "Алина", password: "short1", repeatPassword: "short1")).contains("8"))
        XCTAssertTrue(error(a.register(username: "alina", displayName: "Алина", password: "onlyletters", repeatPassword: "onlyletters")).contains("цифры"))
        XCTAssertTrue(error(a.register(username: "alina", displayName: "Алина", password: "secret123", repeatPassword: "secret124")).contains("не совпадают"))
        XCTAssertTrue(isOk(a.register(username: "alina", displayName: "Алина", password: "secret123", repeatPassword: "secret123")))
        XCTAssertTrue(error(a.register(username: "@ALINA", displayName: "Другая", password: "secret123", repeatPassword: "secret123")).contains("занят"))
    }

    func testLoginLogout() {
        let a = auth()
        _ = a.register(username: "sofia", displayName: "София", password: "secret123", repeatPassword: "secret123")
        a.logout()
        XCTAssertNil(a.current())
        XCTAssertEqual(error(a.login(username: "sofia", password: "wrong1234")), "Неверный логин или пароль")
        // Нет такого логина — тот же ответ, чтобы нельзя было перебирать логины
        XCTAssertEqual(error(a.login(username: "nobody", password: "secret123")), "Неверный логин или пароль")
        XCTAssertTrue(isOk(a.login(username: " @Sofia ", password: "secret123")))
        XCTAssertEqual(a.current()?.username, "sofia")
    }

    func testLockAfterTooManyFailures() {
        let a = auth()
        _ = a.register(username: "timur", displayName: "Тимур", password: "secret123", repeatPassword: "secret123")
        a.logout()
        for _ in 0..<LocalAuth.maxFailures {
            _ = a.login(username: "timur", password: "wrong1234")
        }
        // Даже верный пароль не пускает, пока действует блокировка
        XCTAssertTrue(error(a.login(username: "timur", password: "secret123")).contains("Слишком много"))
        now += LocalAuth.lockMs + 1
        XCTAssertTrue(isOk(a.login(username: "timur", password: "secret123")))
    }

    func testDemoAccountWorks() {
        let a = auth()
        a.ensureDemoAccount()
        a.ensureDemoAccount() // второй вызов ничего не меняет
        XCTAssertTrue(isOk(a.login(username: LocalAuth.demoLogin, password: LocalAuth.demoPassword)))
    }

    func testRenameSurvivesRestart() {
        _ = auth().register(username: "alex", displayName: "Алекс", password: "secret123", repeatPassword: "secret123")
        auth().rename(username: "alex", displayName: "Александр")
        auth().saveProfile(username: "alex", info: ProfileInfo(bio: "Привет", city: "Тверь", interests: ["Фото", "Книги"]))
        let restored = auth().current()
        XCTAssertEqual(restored?.displayName, "Александр")
        XCTAssertEqual(restored?.info.city, "Тверь")
        XCTAssertEqual(restored?.info.bio, "Привет")
        XCTAssertEqual(restored?.info.interests, ["Фото", "Книги"])
    }

    /// Хеш совпадает с эталоном PBKDF2-HMAC-SHA256 (RFC 7914, тест 1 для
    /// SHA-256: пароль «passwd», соль «salt», 1 итерация).
    func testPbkdf2MatchesReference() {
        let key = PasswordHasher.hash("passwd", salt: Array("salt".utf8), iterations: 1)
        XCTAssertEqual(key.hex, "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc")
    }
}
