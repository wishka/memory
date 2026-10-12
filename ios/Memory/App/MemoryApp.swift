import SwiftUI
import UIKit

@main
struct MemoryApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    init() {
        Fonts.register()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .preferredColorScheme(.dark)
                .tint(Palette.lilac)
        }
    }
}

/// Что сейчас на экране: заставка, знакомство, вход или само приложение.
private enum Stage: Equatable {
    case splash, onboarding, auth, main
}

struct RootView: View {
    @State private var model = AppModel()
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

    private var stage: Stage {
        // Заставка держится, пока проверяется сохранённая сессия: иначе на
        // долю секунды мелькнул бы экран входа у уже вошедшего человека
        if !splashDone || auth.checking { return .splash }
        if auth.account == nil && !onboarded { return .onboarding }
        return auth.account == nil ? .auth : .main
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
                            }
                            return error
                        }
                    )
                )
                .transition(.opacity)
            }
        }
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
        .onOpenURL { url in
            guard let invite = Invites.parse(url.absoluteString) else { return }
            if loadedFor != nil {
                model.openInvite(invite)
            } else {
                pendingInvite = invite
            }
        }
        .onChange(of: stage) { _, current in
            if current == .main, let nudge = push.opened {
                model.openNudge(nudge)
                push.opened = nil
            }
        }
    }
}
