import SwiftUI
import UIKit

@main
struct MemoryApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    init() {
        Fonts.register()
        // Оформление — до первого экрана, чтобы не мигало
        DevicePrefs().load()
        ThemeState.shared.systemDark = MemoryApp.systemIsDark()
        // Отчёты о сбоях и статистика — до любого экрана; без согласия ничего не уходит
        TelemetrySetup.initialize()
        Telemetry.track(.appOpen)
    }

    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }

    /// Тема телефона. preferredColorScheme меняет тему окна, а не экрана —
    /// поэтому системная читается с экрана, а не из окружения SwiftUI.
    static func systemIsDark() -> Bool {
        UIScreen.main.traitCollection.userInterfaceStyle != .light
    }
}

/// Что сейчас на экране: заставка, знакомство, вход, согласие с
/// документами или само приложение.
private enum Stage: Equatable {
    case splash, onboarding, auth, consent, main
}

struct RootView: View {
    @State private var model = AppModel()
    @Environment(\.scenePhase) private var scenePhase
    @State private var auth = AuthModel()
    /// Заставка показывается при холодном запуске; возврат в приложение её не повторяет.
    @State private var splashDone = false
    /// Чьё сохранённое состояние загружено: до загрузки ничего не пишем в файл.
    @State private var loadedFor: String?
    @State private var saveTask: Task<Void, Never>?
    private let store = StateStore()
    private let push = PushCenter.shared
    private let device = DevicePrefs()
    /// Знакомство «выберите любимое» — один раз на телефоне, до регистрации.
    @State private var onboarded = DevicePrefs().onboarded
    /// После знакомства — сразу регистрация.
    @State private var registerNext = false
    /// Ссылка-приглашение, открытая до входа: её разберём после загрузки аккаунта.
    @State private var pendingInvite: Invite?
    /// Ссылка на запись, профиль, PRISM, группу или тег, открытая до входа: откроется после загрузки.
    @State private var pendingLink: LinkTarget?
    /// «Это недоступно на этом телефоне…» — ссылка вела туда, чего здесь нет.
    @State private var linkNotice: String?

    private var stage: Stage {
        // Заставка держится, пока проверяется сохранённая сессия: иначе на
        // долю секунды мелькнул бы экран входа у уже вошедшего человека
        if !splashDone || auth.checking { return .splash }
        if auth.account == nil && !onboarded { return .onboarding }
        guard let account = auth.account else { return .auth }
        // Документы (соглашение, правила, согласие на обработку) не приняты
        // в текущей версии — сначала они, потом приложение. consentTick
        // меняется, когда их приняли: так этот расчёт повторяется
        _ = auth.consentTick
        return auth.hasConsent(account.username) ? .main : .consent
    }

    var body: some View {
        ZStack {
            Palette.void.ignoresSafeArea()
            switch stage {
            case .splash:
                AtomSplash { splashDone = true }
                    .transition(.opacity)
            case .onboarding:
                OnboardingScreen(
                    onDone: { picks in
                        device.savePicks(picks)
                        device.setOnboarded()
                        registerNext = true
                        onboarded = true
                    },
                    onSkip: {
                        device.setOnboarded()
                        onboarded = true
                    }
                )
                .transition(.opacity)
            case .auth:
                AuthScreen(auth: auth, startRegistering: registerNext)
                    .transition(.opacity)
            case .consent:
                ConsentGate(onAccept: { auth.acceptDocuments() }, onLogout: { auth.logout() })
                    .transition(.opacity)
            case .main:
                MainScreen(
                    model: model,
                    onSaveProfile: { name, info in auth.saveProfile(name, info) },
                    account: AccountActions(
                        logout: { auth.logout() },
                        changePassword: { current, new, repeated in
                            await auth.changePassword(current, new, repeated)
                        },
                        deleteAccount: { password in
                            let login = auth.account?.username
                            let error = await auth.deleteAccount(password)
                            if error == nil, let login {
                                saveTask?.cancel()
                                store.delete(login)
                                // Ключ сквозного шифрования уходит вместе с аккаунтом
                                ChatKeys(SecureStore()).forget(login)
                            }
                            return error
                        },
                        recovery: RecoveryActions(
                            left: { auth.recoveryLeft() },
                            regenerate: { password in await auth.regenerateRecoveryCodes(password) },
                            email: { auth.email() },
                            emailConfirmed: { auth.emailConfirmed() },
                            setEmail: { auth.setEmail($0) },
                            confirmEmail: { auth.confirmEmail($0) },
                            removeEmail: { auth.removeEmail() },
                            demoCode: { auth.demoCode() }
                        ),
                        data: dataActions
                    )
                )
                .transition(.opacity)
            }
            // Коды восстановления — сразу после регистрации, поверх любого экрана
            if let codes = auth.recoveryCodes, !codes.isEmpty {
                RecoveryCodesDialog(codes: codes) { auth.dismissRecoveryCodes() }
                    .zIndex(10)
            }
        }
        .toast($linkNotice)
        // Светлая тема — по выбору в «Оформлении»; что всегда ночное — тёмное.
        // Здесь, а не в сцене: тело экрана следит за ThemeState и перерисовывается
        .preferredColorScheme(ThemeState.shared.light ? .light : .dark)
        .tint(Palette.lilac)
        .animation(.easeInOut(duration: 0.5), value: stage)
        // Вошедший человек — в профиль и в свои записи; его записи,
        // переписка и закладки — из файла на телефоне
        .onChange(of: auth.account) { old, account in
            if old?.username == account?.username, let account {
                // Тот же человек поменял имя или сведения в профиле
                model.setAccount(username: account.username, displayName: account.displayName, info: account.info)
                return
            }
            loadedFor = nil
            if let account {
                // Ключ сквозного шифрования — в Связке ключей телефона
                model.attachKeys(SecureStore())
                model.setAccount(username: account.username, displayName: account.displayName, info: account.info)
                let saved = store.load(account.username)
                if let saved {
                    model.restore(saved)
                } else {
                    // Любимое из знакомства — только на полку нового аккаунта (сохранённого ещё нет).
                    // Вошли в старый — выбор ждёт следующей регистрации на этом телефоне.
                    let picks = device.pendingPicks()
                    if !picks.isEmpty {
                        model.addFavorites(picks)
                        device.clearPicks()
                    }
                }
                loadedFor = account.username
                // Открыли по ссылке-приглашению до входа — кто позвал и к чему
                if let invite = pendingInvite {
                    model.openInvite(invite)
                    pendingInvite = nil
                }
                // Открыли по ссылке на запись или профиль до входа — туда
                if let link = pendingLink {
                    pendingLink = nil
                    open(link)
                }
                // Истории друзей и их «Актуальное» — в демо появляются сами
                model.ensureDemoStories()
                // Сохранённое на месте — можно считать итоги и оживлять демо
                model.checkRecapReady()
                model.startDemoLife()
                push.requestPermission()
            } else {
                model.reset()
            }
        }
        // Любое изменение — в файл, через полсекунды тишины: при наборе
        // комментария не пишем на диск после каждой буквы
        .onChange(of: model.state) { _, _ in
            guard let login = loadedFor else { return }
            saveTask?.cancel()
            let snapshot = model.snapshot()
            let store = self.store
            saveTask = Task {
                try? await Task.sleep(nanoseconds: 500_000_000)
                guard !Task.isCancelled else { return }
                await Task.detached(priority: .utility) { store.save(login, snapshot) }.value
            }
        }
        .task {
            // Входящий звонок: приложение открыто — он и так во весь экран
            model.isForeground = { UIApplication.shared.applicationState == .active }
            // Уведомления: приложение открыто — плашкой сверху, свёрнуто — системным
            model.onNudge = { nudge in
                if UIApplication.shared.applicationState == .active {
                    model.showBanner(nudge)
                    // Плашка звучит тем же звуком, что и пуш этого вида
                    SoundPlayer.play(SoundPrefs().get(nudge.kind))
                } else {
                    PushCenter.shared.show(nudge)
                }
            }
            auth.restore()
        }
        // Исчезающие сообщения: раз в 10 секунд убираем истёкшие (и их файлы)
        .task {
            while !Task.isCancelled {
                model.sweepExpired().forEach { MediaFiles.delete($0) }
                try? await Task.sleep(nanoseconds: 10_000_000_000)
            }
        }
        // Свернули приложение — накопленная за день статистика сохраняется (если её разрешили)
        .onChange(of: scenePhase) { _, phase in
            if phase == .background { TelemetrySetup.flush() }
            // Тему телефона могли сменить, пока приложение было свёрнуто
            if phase == .active { ThemeState.shared.systemDark = MemoryApp.systemIsDark() }
        }
        // Заставка, знакомство, вход и документы — на ночном небе: всегда тёмные
        .onAppear { ThemeState.shared.stageDark = stage != .main }
        .onChange(of: stage) { _, current in ThemeState.shared.stageDark = current != .main }
        // Пуш, пока приложение открыто, — плашкой (если раздел не выключен)
        .onChange(of: push.incoming) { _, nudge in
            guard let nudge else { return }
            if stage == .main { model.notify(nudge) }
            push.incoming = nil
        }
        // Открыли приложение нажатием на уведомление — туда, о чём оно
        .onChange(of: push.opened) { _, nudge in
            guard let nudge, stage == .main else { return }
            model.openNudge(nudge)
            push.opened = nil
        }
        // Ссылка-приглашение: memory://invite?c=КОД&a=drawing&id=… (и страница-приглашение в вебе)
        // Ссылка на запись, профиль, PRISM, группу или тег: memory://post/… (и страница-ссылка в вебе)
        .onOpenURL { url in
            if let invite = Invites.parse(url.absoluteString) {
                if loadedFor != nil {
                    model.openInvite(invite)
                } else {
                    pendingInvite = invite
                }
            } else if let link = Links.parse(url.absoluteString) {
                if loadedFor != nil {
                    open(link)
                } else {
                    pendingLink = link
                }
            }
        }
        .onChange(of: stage) { _, current in
            if current == .main, let nudge = push.opened {
                model.openNudge(nudge)
                push.opened = nil
            }
        }
    }

    /// Открыть ссылку Memory; такого на телефоне нет — сказать об этом.
    private func open(_ link: LinkTarget) {
        if !model.openLink(link) {
            linkNotice = t("Это недоступно на этом телефоне — возможно, запись удалили или профиль закрыт.")
        }
    }

    /// «Скачать мои данные»: размер медиа и архив во временный файл.
    private var dataActions: DataActions {
        let model = self.model
        let auth = self.auth
        return DataActions(
            fileName: { DataExport.fileName(auth.account?.username ?? "", Date()) },
            mediaBytes: {
                let snapshot = model.snapshot()
                let avatar = model.state.me.avatar ?? ""
                return await Task.detached(priority: .utility) {
                    DataExport.mediaBytes(DataExport.files(snapshot, avatar: avatar))
                }.value
            },
            export: { target, withMedia in
                guard let account = auth.account else { return nil }
                let snapshot = model.snapshot()
                let me = model.state.me
                let consent = auth.consentInfo()
                let info = AccountExport(
                    login: account.username, displayName: me.name, info: model.profileInfo(),
                    email: auth.email(), emailConfirmed: auth.emailConfirmed(),
                    consentVersion: consent.version, consentAt: consent.at,
                    recoveryCodesLeft: auth.recoveryLeft()
                )
                Telemetry.track(.dataExported)
                let avatar = me.avatar ?? ""
                return await Task.detached(priority: .userInitiated) { () -> Int? in
                    try? DataExport.write(
                        to: target, account: info, stateJson: StateStore.exportJson(snapshot),
                        files: DataExport.files(snapshot, avatar: avatar), withMedia: withMedia, now: Date()
                    )
                }.value
            }
        )
    }
}
