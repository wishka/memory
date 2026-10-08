import Foundation
import Observation

/// Вход и регистрация. Хеширование пароля намеренно медленное
/// (PBKDF2, 120 000 итераций), поэтому оно идёт не в главном потоке:
/// иначе экран замирал бы при каждом нажатии «Войти».
@Observable
final class AuthModel {
    /// Идёт проверка сохранённой сессии — пока заставка, решать рано.
    var checking = true
    var account: Account?
    var busy = false
    var error: String?

    @ObservationIgnored private let auth: LocalAuth

    @ObservationIgnored private var restoring = false

    init(auth: LocalAuth = LocalAuth(store: SecureStore())) {
        self.auth = auth
    }

    /// Проверить сохранённую сессию и завести демо-аккаунт. Вызывается
    /// один раз при запуске, пока идёт заставка.
    func restore() {
        if restoring { return }
        restoring = true
        let auth = self.auth
        Task.detached(priority: .userInitiated) {
            auth.ensureDemoAccount()
            let account = auth.current()
            await MainActor.run { [weak self] in
                self?.account = account
                self?.checking = false
            }
        }
    }

    func login(_ username: String, _ password: String) {
        run { $0.login(username: username, password: password) }
    }

    func register(_ username: String, _ displayName: String, _ password: String, _ repeated: String) {
        run { $0.register(username: username, displayName: displayName, password: password, repeatPassword: repeated) }
    }

    func logout() {
        auth.logout()
        account = nil
        error = nil
    }

    /// Имя и сведения профиля — в сохранённый аккаунт, чтобы пережили перезапуск.
    func saveProfile(_ displayName: String, _ info: ProfileInfo) {
        guard var current = account else { return }
        auth.rename(username: current.username, displayName: displayName)
        auth.saveProfile(username: current.username, info: info)
        current.displayName = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        current.info = info
        account = current
    }

    func clearError() {
        error = nil
    }

    private func run(_ block: @escaping (LocalAuth) -> AuthResult) {
        if busy { return }
        busy = true
        error = nil
        let auth = self.auth
        Task.detached(priority: .userInitiated) {
            let result = block(auth)
            await MainActor.run { [weak self] in
                guard let self else { return }
                self.busy = false
                switch result {
                case .ok(let account): self.account = account
                case .error(let message): self.error = message
                }
            }
        }
    }
}
