import SwiftUI

@main
struct MemoryApp: App {
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

/// Что сейчас на экране: заставка, вход или само приложение.
private enum Stage: Equatable {
    case splash, auth, main
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

    private var stage: Stage {
        // Заставка держится, пока проверяется сохранённая сессия: иначе на
        // долю секунды мелькнул бы экран входа у уже вошедшего человека
        if !splashDone || auth.checking { return .splash }
        return auth.account == nil ? .auth : .main
    }

    var body: some View {
        ZStack {
            Palette.void.ignoresSafeArea()
            switch stage {
            case .splash:
                AtomSplash { splashDone = true }
                    .transition(.opacity)
            case .auth:
                AuthScreen(auth: auth)
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
                if let snapshot = store.load(account.username) {
                    model.restore(snapshot)
                }
                loadedFor = account.username
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
        .task { auth.restore() }
    }
}
