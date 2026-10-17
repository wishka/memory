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
    /// Меняется, когда человек принял документы: корень пересчитывает, нужен ли запрос.
    var consentTick = 0
    /// Только что созданные коды восстановления — показать один раз и забыть.
    var recoveryCodes: [String]?
    /// «Забыли пароль?»: письмо с кодом отправлено (текст для человека).
    var resetSent: String?

    @ObservationIgnored private let auth: LocalAuth
    @ObservationIgnored private let consents: ConsentStore

    @ObservationIgnored private var restoring = false

    /// Пароль последнего входа — один раз, чтобы войти и на сервер (не
    /// хранится: забирается takePassword() и стирается).
    @ObservationIgnored private var pendingPassword: String?

    func takePassword() -> String? {
        let p = pendingPassword
        pendingPassword = nil
        return p
    }

    init(auth: LocalAuth = LocalAuth(store: SecureStore()), consents: ConsentStore = ConsentStore()) {
        self.auth = auth
        self.consents = consents
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
        run(onOk: { [weak self] _ in self?.pendingPassword = password }) { $0.login(username: username, password: password) }
    }

    /// consented — человек отметил возраст, соглашение и согласие на
    /// обработку: после удачной регистрации запоминаем версию документов.
    func register(_ username: String, _ displayName: String, _ password: String, _ repeated: String, consented: Bool = false) {
        let consents = self.consents
        // Коды восстановления — сразу при регистрации: без сервера это единственный путь назад.
        // Хешируются в фоне, вместе с паролем, а не в главном потоке
        let box = RecoveryCodesBox()
        run(onOk: { [weak self] account in
            self?.pendingPassword = password
            if consented { consents.accept(account.username) }
            Telemetry.track(.signUp)
            self?.recoveryCodes = box.codes
        }) { auth in
            let result = auth.register(username: username, displayName: displayName, password: password, repeatPassword: repeated)
            if case .ok(let account) = result { box.codes = auth.newRecoveryCodes(account.username) }
            return result
        }
    }

    /// «Забыли пароль?» — вход по одноразовому коду восстановления с новым паролем.
    func recoverWithCode(_ username: String, _ code: String, _ new: String, _ repeated: String) {
        run(onOk: { _ in Telemetry.track(.recoveryUsed) }) {
            $0.recoverWithCode(username: username, code: code, new: new, repeatPassword: repeated)
        }
    }

    /// «Забыли пароль?» по почте: письмо с кодом (если почта подтверждена).
    func startEmailRecovery(_ username: String) {
        if username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return }
        let auth = self.auth
        Task.detached(priority: .userInitiated) {
            let text = auth.startEmailRecovery(username)
            await MainActor.run { [weak self] in
                self?.resetSent = text
                self?.error = nil
            }
        }
    }

    func recoverWithEmail(_ username: String, _ code: String, _ new: String, _ repeated: String) {
        run(onOk: { _ in Telemetry.track(.recoveryUsed) }) {
            $0.recoverWithEmail(username: username, code: code, new: new, repeatPassword: repeated)
        }
    }

    /// Коды показаны и сохранены человеком.
    func dismissRecoveryCodes() {
        recoveryCodes = nil
    }

    /// Новые коды из «Безопасности» — после проверки пароля. nil — неверный пароль.
    func regenerateRecoveryCodes(_ password: String) async -> [String]? {
        guard let login = account?.username else { return nil }
        let auth = self.auth
        return await Task.detached(priority: .userInitiated) { () -> [String]? in
            auth.checkPasswordOf(login, password) ? auth.newRecoveryCodes(login) : nil
        }.value
    }

    func recoveryLeft() -> Int { account.map { auth.recoveryLeft($0.username) } ?? 0 }

    func email() -> String { account.map { auth.email($0.username) } ?? "" }

    func emailConfirmed() -> Bool { account.map { auth.emailConfirmed($0.username) } ?? false }

    /// Указать почту — уходит письмо с кодом. nil — отправлено.
    func setEmail(_ email: String) -> String? {
        guard let login = account?.username else { return t("Вы не вошли") }
        return auth.setEmail(login, email)
    }

    func confirmEmail(_ code: String) -> String? {
        guard let login = account?.username else { return t("Вы не вошли") }
        return auth.confirmEmail(login, code)
    }

    func removeEmail() {
        if let login = account?.username { auth.removeEmail(login) }
    }

    /// Какую версию документов и когда (мс) принял человек — для выгрузки данных.
    func consentInfo() -> (version: String?, at: Int64) {
        guard let login = account?.username else { return (nil, 0) }
        return (consents.version(login), consents.acceptedAt(login))
    }

    /// Демо: код из «письма» — пока сервера нет, его показывает экран.
    func demoCode() -> String? { DemoMail.last?.code }

    func clearResetSent() {
        resetSent = nil
    }

    /// Согласие с текущей версией документов.
    func hasConsent(_ username: String) -> Bool { consents.accepted(username) }

    func acceptDocuments() {
        guard let account else { return }
        consents.accept(account.username)
        consentTick += 1
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

    /// Подойдёт ли смена пароля (ничего не меняет). Ответ — текст ошибки или nil.
    func checkPasswordChange(_ current: String, _ new: String, _ repeated: String) async -> String? {
        guard let login = account?.username else { return t("Вы не вошли") }
        let auth = self.auth
        return await Task.detached(priority: .userInitiated) {
            auth.checkChange(username: login, current: current, new: new, repeatPassword: repeated)
        }.value
    }

    /// Проверка пароля аккаунта login для модели (вход на сервер с плашки).
    /// Медленная (PBKDF2) — звать не в главном потоке.
    func passwordChecker(_ login: String) -> (String) -> Bool {
        let auth = self.auth
        return { password in auth.checkPasswordOf(login, password) }
    }

    /// Подходит ли пароль текущего аккаунта (проверка — не в главном потоке).
    func checkPassword(_ password: String) async -> Bool {
        guard let login = account?.username else { return false }
        let auth = self.auth
        return await Task.detached(priority: .userInitiated) {
            auth.checkPasswordOf(login, password)
        }.value
    }

    /// Смена пароля в «Безопасности». Ответ — текст ошибки или nil;
    /// пароль проверяется не в главном потоке.
    func changePassword(_ current: String, _ new: String, _ repeated: String) async -> String? {
        guard let login = account?.username else { return t("Вы не вошли") }
        let auth = self.auth
        return await Task.detached(priority: .userInitiated) {
            auth.changePassword(username: login, current: current, new: new, repeatPassword: repeated)
        }.value
    }

    /// Удалить аккаунт с телефона. nil — удалён и выполнен выход.
    func deleteAccount(_ password: String) async -> String? {
        guard let login = account?.username else { return t("Вы не вошли") }
        let auth = self.auth
        let error = await Task.detached(priority: .userInitiated) {
            auth.deleteAccount(username: login, password: password)
        }.value
        if error == nil {
            consents.forget(login)
            await MainActor.run { [weak self] in
                self?.account = nil
                self?.error = nil
            }
        }
        return error
    }

    func clearError() {
        error = nil
    }

    /// onOk — до того, как аккаунт появится в account: согласие должно
    /// быть записано раньше, чем корень решит, показывать ли запрос.
    private func run(_ block: @escaping (LocalAuth) -> AuthResult) {
        run(onOk: { _ in }, block)
    }

    private func run(onOk: @escaping (Account) -> Void, _ block: @escaping (LocalAuth) -> AuthResult) {
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
                case .ok(let account):
                    onOk(account)
                    self.account = account
                case .error(let message): self.error = message
                }
            }
        }
    }
}

/// Коды восстановления, созданные в фоне при регистрации, — для показа в главном потоке.
private final class RecoveryCodesBox {
    var codes: [String] = []
}
