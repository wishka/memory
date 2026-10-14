import SwiftUI

/// Вход и регистрация на том же ночном небе, что и заставка: после
/// анимации слова человек остаётся «в той же сцене».
///
/// После входа приложение запоминает человека — этот экран он увидит
/// снова, только если выйдет из профиля.
struct AuthScreen: View {
    let auth: AuthModel

    @State private var registering: Bool

    /// startRegistering — после знакомства сразу регистрация: полку нужно сохранить.
    init(auth: AuthModel, startRegistering: Bool = false) {
        self.auth = auth
        _registering = State(initialValue: startRegistering)
    }
    @State private var login = ""
    @State private var name = ""
    @State private var password = ""
    @State private var repeated = ""
    /// Возраст, соглашение и согласие на обработку — три отдельные галочки.
    @State private var consent = ConsentState()
    @State private var openDoc: LegalDoc?
    /// «Забыли пароль?» — вместо формы входа форма восстановления.
    @State private var forgot = false

    private enum FieldId: Hashable { case login, name, password, repeated }
    @FocusState private var focus: FieldId?

    var body: some View {
        ZStack {
            SplashSkyView()
            ScrollView {
                VStack(spacing: 0) {
                    Spacer(minLength: 40)
                    Text("MEMORY").textStyle(.display)
                    Text(t("МОМЕНТЫ, КОТОРЫЕ ОСТАЮТСЯ")).textStyle(.label, Palette.mist)
                    Spacer().frame(height: 28)

                    GlassCard {
                        if forgot {
                            ForgotPasswordForm(auth: auth, login: $login) {
                                withAnimation(.easeOut(duration: 0.2)) { forgot = false }
                            }
                            .padding(18)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        } else {
                        VStack(spacing: 12) {
                            modeSwitch
                            GlassField(placeholder: t("Логин"), text: $login, limit: 24, singleLine: true, keyboard: .asciiCapable, plain: true)
                                .glass(radius: 16)
                                .focused($focus, equals: .login)
                                .submitLabel(.next)
                                .onSubmit { focus = registering ? .name : .password }
                                .textContentType(.username)
                            if registering {
                                GlassField(placeholder: t("Имя — как вас видят другие"), text: $name, limit: 40, singleLine: true)
                                    .glass(radius: 16)
                                    .focused($focus, equals: .name)
                                    .submitLabel(.next)
                                    .onSubmit { focus = .password }
                                    .textContentType(.name)
                            }
                            GlassField(placeholder: t("Пароль"), text: $password, limit: 64, secure: true, plain: true)
                                .glass(radius: 16)
                                .focused($focus, equals: .password)
                                .submitLabel(registering ? .next : .go)
                                .onSubmit { if registering { focus = .repeated } else { submit() } }
                                .textContentType(registering ? .newPassword : .password)
                            if registering {
                                GlassField(placeholder: t("Пароль ещё раз"), text: $repeated, limit: 64, secure: true, plain: true)
                                    .glass(radius: 16)
                                    .focused($focus, equals: .repeated)
                                    .submitLabel(.go)
                                    .onSubmit(submit)
                                    .textContentType(.newPassword)
                            }

                            if registering {
                                ConsentChecks(consent: $consent) { openDoc = $0 }
                            }

                            if let error = auth.error {
                                Text(error)
                                    .textStyle(.bodySmall, Palette.error)
                                    .multilineTextAlignment(.center)
                                    .frame(maxWidth: .infinity)
                            }

                            GlowButton(
                                title: auth.busy ? t("Подождите…") : registering ? t("Создать аккаунт") : t("Войти"),
                                enabled: !auth.busy && !login.trimmingCharacters(in: .whitespaces).isEmpty && !password.isEmpty
                                    && (!registering || consent.all),
                                fullWidth: true,
                                action: submit
                            )

                            if !registering {
                                Button {
                                    auth.clearError()
                                    withAnimation(.easeOut(duration: 0.2)) { forgot = true }
                                } label: {
                                    Text(t("Забыли пароль?")).textStyle(.bodySmall, Palette.lilac)
                                }
                                .buttonStyle(.plain)
                            }

                            Text(registering
                                 ? t("Логин — латиница, цифры, точка и «_», от 3 символов. Пароль — от 8 символов, с буквами и цифрами.")
                                 : tf("Попробовать без регистрации: логин %s, пароль %s", LocalAuth.demoLogin, LocalAuth.demoPassword))
                                .textStyle(.bodySmall, Palette.mist)
                                .multilineTextAlignment(.center)
                                .fixedSize(horizontal: false, vertical: true)
                                .frame(maxWidth: .infinity)
                        }
                        .padding(18)
                        }
                    }

                    Text(t("Аккаунты пока хранятся только на этом iPhone."))
                        .textStyle(.bodySmall, Palette.mist)
                        .multilineTextAlignment(.center)
                        .padding(.top, 16)
                    LegalLinks(docs: [.terms, .privacy]) { openDoc = $0 }
                        .padding(.top, 8)
                    Spacer(minLength: 24)
                }
                .padding(.horizontal, 20)
                .frame(maxWidth: 520)
                .frame(maxWidth: .infinity)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .fullScreenCover(item: $openDoc) { doc in
            LegalDocViewer(doc: doc) { openDoc = nil }
        }
    }

    /// Переключатель «Вход | Регистрация».
    private var modeSwitch: some View {
        HStack(spacing: 0) {
            ForEach([false, true], id: \.self) { mode in
                let active = registering == mode
                Button {
                    withAnimation(.easeOut(duration: 0.2)) { registering = mode }
                    auth.clearError()
                } label: {
                    Text((mode ? t("Регистрация") : t("Вход")).uppercased())
                        .textStyle(.label, active ? Palette.lilac : Palette.mist)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background {
                            if active {
                                Capsule().fill(Palette.plumRGB.opacity(0.7))
                            }
                        }
                        .contentShape(Capsule())
                }
                .buttonStyle(.plain)
            }
        }
        .glass(Capsule())
    }

    private func submit() {
        guard !auth.busy, !login.isEmpty, !password.isEmpty else { return }
        focus = nil
        if registering {
            // Без трёх галочек аккаунт не создаётся (кнопка тоже неактивна)
            guard consent.all else { return }
            auth.register(login, name, password, repeated, consented: consent.all)
        } else {
            auth.login(login, password)
        }
    }
}
