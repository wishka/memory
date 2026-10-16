import XCTest
@testable import Memory

/// Раунд 26: вход на сервер с плашки, модерация (жалобы и блокировки на
/// сервере), удаление аккаунта и смена пароля и на сервере. Здесь — то, что
/// проверяется без сервера. Те же проверки, что Round26Test на Android.
final class Round26Tests: XCTestCase {
    private var clock = TestClock(Date(timeIntervalSince1970: 1_800_000_000))

    /// Часы сети — в мс, как у Android.
    private var now: Int64 { Json.ms(clock.now) }

    override func setUp() {
        super.setUp()
        L10n.shared.setLanguage(.ru)
        L10n.shared.systemLanguage = "ru"
        clock = TestClock(Date(timeIntervalSince1970: 1_800_000_000))
    }

    private func makeModel() -> AppModel {
        let clock = self.clock
        return AppModel(clock: { clock.now }, demoReplies: false)
    }

    private func isOk(_ r: AuthResult) -> Bool {
        if case .ok = r { return true }
        return false
    }

    func testCheckChangeDoesNotChangePassword() {
        let store = MemoryStore()
        let ms = now
        let auth = LocalAuth(store: store, clock: { ms }, iterations: 1_000)
        XCTAssertTrue(isOk(auth.register(username: "anna", displayName: "Анна", password: "secret12", repeatPassword: "secret12")))
        let wrong = auth.checkChange(username: "anna", current: "wrong", new: "newpass12", repeatPassword: "newpass12")
        XCTAssertEqual("Текущий пароль не подходит", wrong)
        let mismatch = auth.checkChange(username: "anna", current: "secret12", new: "newpass12", repeatPassword: "other")
        XCTAssertEqual("Пароли не совпадают", mismatch)
        XCTAssertNil(auth.checkChange(username: "anna", current: "secret12", new: "newpass12", repeatPassword: "newpass12"))
        // Проверка ничего не поменяла: старый пароль по-прежнему подходит
        XCTAssertTrue(auth.checkPasswordOf("anna", "secret12"))
        XCTAssertNil(auth.changePassword(username: "anna", current: "secret12", new: "newpass12", repeatPassword: "newpass12"))
        XCTAssertTrue(auth.checkPasswordOf("anna", "newpass12"))
    }

    @MainActor
    func testDemoHasNoServerThings() async {
        let vm = makeModel()
        XCTAssertFalse(vm.state.serverMode)
        // С демо-сервером удалять и менять на сервере нечего
        let deleted = await vm.deleteOnServer("x")
        XCTAssertNil(deleted)
        let changed = await vm.changeServerPassword("x", "y")
        XCTAssertNil(changed)
        // Жалоба на демо-человека остаётся на телефоне: в очередь сети ничего не уходит
        let before = vm.state.outboxCount
        let p = Demo.posts[0]
        let target = ReportTarget(kind: .post, id: p.id, authorId: p.author.id, authorName: p.author.name, preview: p.text)
        vm.report(target, .spam)
        XCTAssertEqual(before, vm.state.outboxCount)
        XCTAssertEqual(1, vm.state.reports.count)
    }
}
