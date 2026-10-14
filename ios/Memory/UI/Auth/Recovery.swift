import SwiftUI
import UIKit

/// Что «Безопасности» нужно для восстановления доступа: коды и почта.
/// Реализация — AuthModel; когда появится сервер, экраны не изменятся.
/// То же — RecoveryActions в Recovery.kt.
struct RecoveryActions {
    let left: () -> Int
    /// Новые коды после проверки пароля; nil — пароль не подошёл.
    let regenerate: (_ password: String) async -> [String]?
    let email: () -> String
    let emailConfirmed: () -> Bool
    /// nil — письмо с кодом ушло, иначе текст ошибки.
    let setEmail: (String) -> String?
    let confirmEmail: (String) -> String?
    let removeEmail: () -> Void
    /// Демо: код последнего «письма» — пока сервера нет, он виден на экране.
    let demoCode: () -> String?
}

/// Коды восстановления — показываются один раз: сразу после регистрации и
/// после «Новые коды». Закрыть можно, только отметив, что коды сохранены.
struct RecoveryCodesDialog: View {
    let codes: [String]
    let onDone: () -> Void
    @State private var saved = false
    @State private var copied = false

    var body: some View {
        CenterDialog(onDismiss: {}) {
            Text(t("Коды восстановления"))
                .textStyle(.title)
                .accessibilityAddTraits(.isHeader)
            Text(t("Если забудете пароль, войдите по одному из этих кодов. Каждый код работает один раз. Сохраните их там, где их не увидят другие: в менеджере паролей или на бумаге."))
                .textStyle(.bodySmall, Palette.mist)
                .fixedSize(horizontal: false, vertical: true)
            ForEach(Array(stride(from: 0, to: codes.count, by: 2)), id: \.self) { i in
                HStack(spacing: 18) {
                    ForEach(Array(codes[i..<min(i + 2, codes.count)]), id: \.self) { code in
                        Text(code)
                            .font(.system(size: 17, weight: .medium, design: .monospaced))
                            .foregroundStyle(Palette.ink)
                            .textSelection(.enabled)
                    }
                }
            }
            HStack(spacing: 8) {
                Chip(text: copied ? t("Скопировано") : t("Скопировать"), active: false) {
                    copyText(codes.joined(separator: "\n"))
                    copied = true
                }
                Chip(text: saved ? t("✓ Сохранил") : t("Я сохранил коды"), active: saved) { saved.toggle() }
                    .accessibilityAddTraits(saved ? .isSelected : [])
            }
            HStack {
                Spacer()
                GlowButton(title: t("Готово"), enabled: saved, action: onDone)
            }
        }
        .transition(.opacity)
    }
}

/// Как восстановить доступ: кодом восстановления или письмом на почту.
private enum RecoverBy {
    case code, email
}

/// «Забыли пароль?» на экране входа. По коду — сразу новый пароль; по
/// почте — сначала письмо с кодом, потом код и новый пароль.
struct ForgotPasswordForm: View {
    let auth: AuthModel
    @Binding var login: String
    let onBack: () -> Void

    @State private var by: RecoverBy = .code
    @State private var code = ""
    @State private var password = ""
    @State private var repeated = ""

    private var codeBinding: Binding<String> {
        Binding(
            get: { code },
            set: { value in
                code = by == .email
                    ? String(value.filter { $0.isASCII && $0.isNumber }.prefix(6))
                    : String(value.prefix(12))
            }
        )
    }

    var body: some View {
        let emailStep2 = by == .email && auth.resetSent != nil
        VStack(alignment: .leading, spacing: 12) {
            Text(t("Восстановление доступа"))
                .textStyle(.title)
                .accessibilityAddTraits(.isHeader)
            HStack(spacing: 8) {
                Chip(text: t("Код восстановления"), active: by == .code) {
                    by = .code
                    code = ""
                    auth.clearError()
                    auth.clearResetSent()
                }
                Chip(text: t("Письмо на почту"), active: by == .email) {
                    by = .email
                    code = ""
                    auth.clearError()
                }
            }
            RecoveryField(placeholder: t("Логин"), text: $login, limit: 24, keyboard: .asciiCapable)
                .textContentType(.username)

            if by == .email && !emailStep2 {
                Text(t("Пришлём код на почту, которую вы подтвердили в «Безопасности»."))
                    .textStyle(.bodySmall, Palette.mist)
                    .fixedSize(horizontal: false, vertical: true)
                errorText
                GlowButton(
                    title: t("Прислать код"),
                    enabled: !login.trimmingCharacters(in: .whitespaces).isEmpty,
                    fullWidth: true
                ) { auth.startEmailRecovery(login) }
            } else {
                if emailStep2 {
                    Text(auth.resetSent ?? "")
                        .textStyle(.bodySmall, Palette.mist)
                        .fixedSize(horizontal: false, vertical: true)
                    if let demo = auth.demoCode() { DemoCodeHint(code: demo) }
                }
                RecoveryField(
                    placeholder: by == .email ? t("Код из письма") : t("Код восстановления, например K7QF-2MXP"),
                    text: codeBinding,
                    limit: 12,
                    keyboard: by == .email ? .numberPad : .asciiCapable
                )
                .textContentType(.oneTimeCode)
                RecoveryField(placeholder: t("Новый пароль"), text: $password, limit: 64, secure: true)
                    .textContentType(.newPassword)
                RecoveryField(placeholder: t("Новый пароль ещё раз"), text: $repeated, limit: 64, secure: true)
                    .textContentType(.newPassword)
                errorText
                GlowButton(
                    title: auth.busy ? t("Подождите…") : t("Сменить пароль и войти"),
                    enabled: !auth.busy && !login.trimmingCharacters(in: .whitespaces).isEmpty && !code.trimmingCharacters(in: .whitespaces).isEmpty && !password.isEmpty,
                    fullWidth: true
                ) {
                    if by == .code {
                        auth.recoverWithCode(login, code, password, repeated)
                    } else {
                        auth.recoverWithEmail(login, code, password, repeated)
                    }
                }
                Text(by == .code
                     ? t("Коды восстановления показываются при регистрации. Каждый код работает один раз.")
                     : t("Код из письма действует 15 минут."))
                    .textStyle(.bodySmall, Palette.mist)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Button {
                auth.clearError()
                auth.clearResetSent()
                onBack()
            } label: {
                Text(t("← Назад ко входу")).textStyle(.bodySmall, Palette.lilac)
            }
            .buttonStyle(.plain)
        }
    }

    @ViewBuilder
    private var errorText: some View {
        if let error = auth.error {
            Text(error)
                .textStyle(.bodySmall, Palette.error)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

/// Пока писем нет — код «письма» прямо на экране, с пометкой «демо».
struct DemoCodeHint: View {
    let code: String

    var body: some View {
        Text(tf("Демо: письма пока не отправляются — ваш код %s", code))
            .textStyle(.bodySmall, Palette.lilac)
            .fixedSize(horizontal: false, vertical: true)
            .textSelection(.enabled)
    }
}

/// Поле формы восстановления: одна строка на стекле.
private struct RecoveryField: View {
    let placeholder: String
    @Binding var text: String
    var limit: Int = 64
    var secure: Bool = false
    var keyboard: UIKeyboardType = .default

    var body: some View {
        GlassField(placeholder: placeholder, text: $text, limit: limit, singleLine: true, secure: secure, keyboard: keyboard, plain: true)
            .glass(radius: 16)
    }
}

/// «Безопасность → Восстановление доступа»: сколько кодов осталось, новые
/// коды (по паролю) и почта с подтверждением.
struct RecoverySettings: View {
    let actions: RecoveryActions

    @State private var left: Int
    @State private var regenerating = false
    @State private var password = ""
    @State private var codes: [String]?
    @State private var error: String?

    @State private var email: String
    @State private var confirmed: Bool
    @State private var editing = false
    @State private var address = ""
    @State private var code = ""
    @State private var emailError: String?

    init(actions: RecoveryActions) {
        self.actions = actions
        _left = State(initialValue: actions.left())
        _email = State(initialValue: actions.email())
        _confirmed = State(initialValue: actions.emailConfirmed())
    }

    private var codeBinding: Binding<String> {
        Binding(get: { code }, set: { value in
            code = String(value.filter { $0.isASCII && $0.isNumber }.prefix(6))
            emailError = nil
        })
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(t("Восстановление доступа").uppercased())
                .textStyle(.label, Palette.mist)
                .accessibilityAddTraits(.isHeader)
            Text(left > 0
                 ? tf("Кодов восстановления осталось: %s из %s", left, LocalAuth.recoveryCodes)
                 : t("Кодов восстановления нет. Создайте новые — без них забытый пароль не вернуть."))
                .textStyle(.bodySmall, left > 2 ? Palette.mist : Palette.lilac)
                .fixedSize(horizontal: false, vertical: true)
            if !regenerating {
                GhostButton(title: t("Новые коды")) { regenerating = true }
            } else {
                RecoveryField(placeholder: t("Пароль для подтверждения"), text: $password, limit: 64, secure: true)
                Text(t("Старые коды перестанут работать.")).textStyle(.bodySmall, Palette.mist)
                if let error {
                    Text(error).textStyle(.bodySmall, Palette.error)
                }
                HStack(spacing: 10) {
                    GhostButton(title: t("Отмена")) {
                        regenerating = false
                        password = ""
                        error = nil
                    }
                    GlowButton(title: t("Создать"), enabled: !password.isEmpty) {
                        let typed = password
                        Task { @MainActor in
                            if let fresh = await actions.regenerate(typed) {
                                codes = fresh
                                regenerating = false
                                password = ""
                                left = fresh.count
                            } else {
                                error = t("Пароль не подходит")
                            }
                        }
                    }
                }
            }

            // Почта: указать → код из письма → подтверждена
            Text(t("Почта")).textStyle(.title).padding(.top, 6)
            if !email.isEmpty && confirmed && !editing {
                Text(tf("%s — подтверждена. По ней можно восстановить пароль.", email))
                    .textStyle(.bodySmall, Palette.mist)
                    .fixedSize(horizontal: false, vertical: true)
                HStack(spacing: 10) {
                    GhostButton(title: t("Изменить")) {
                        editing = true
                        address = email
                    }
                    GhostButton(title: t("Убрать")) {
                        actions.removeEmail()
                        email = ""
                        confirmed = false
                    }
                }
            } else if !email.isEmpty && !confirmed && !editing {
                Text(tf("Письмо с кодом ушло на %s.", email))
                    .textStyle(.bodySmall, Palette.mist)
                    .fixedSize(horizontal: false, vertical: true)
                if let demo = actions.demoCode() { DemoCodeHint(code: demo) }
                RecoveryField(placeholder: t("Код из письма"), text: codeBinding, limit: 6, keyboard: .numberPad)
                    .textContentType(.oneTimeCode)
                if let emailError {
                    Text(emailError).textStyle(.bodySmall, Palette.error)
                }
                HStack(spacing: 10) {
                    GhostButton(title: t("Прислать снова")) {
                        emailError = actions.setEmail(email)
                        code = ""
                    }
                    GlowButton(title: t("Подтвердить"), enabled: code.count == 6) {
                        emailError = actions.confirmEmail(code)
                        if emailError == nil {
                            confirmed = true
                            code = ""
                        }
                    }
                }
            } else {
                Text(t("Подтверждённая почта — второй способ вернуть доступ, если коды потеряются."))
                    .textStyle(.bodySmall, Palette.mist)
                    .fixedSize(horizontal: false, vertical: true)
                RecoveryField(placeholder: t("Адрес почты"), text: $address, limit: 80, keyboard: .emailAddress)
                    .textContentType(.emailAddress)
                if let emailError {
                    Text(emailError).textStyle(.bodySmall, Palette.error)
                }
                HStack(spacing: 10) {
                    if editing {
                        GhostButton(title: t("Отмена")) {
                            editing = false
                            emailError = nil
                        }
                    }
                    GlowButton(title: t("Прислать код"), enabled: !address.trimmingCharacters(in: .whitespaces).isEmpty) {
                        emailError = actions.setEmail(address)
                        if emailError == nil {
                            email = address.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                            confirmed = false
                            editing = false
                            code = ""
                        }
                    }
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .onChange(of: password) { _, _ in error = nil }
        .onChange(of: address) { _, _ in emailError = nil }
        .fullScreenCover(isPresented: Binding(get: { codes != nil }, set: { if !$0 { codes = nil } })) {
            ZStack {
                if let codes {
                    RecoveryCodesDialog(codes: codes) { self.codes = nil }
                }
            }
            .presentationBackground(.clear)
        }
    }
}
