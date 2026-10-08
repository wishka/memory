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
                    onLogout: { auth.logout() }
                )
                .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.5), value: stage)
        // Вошедший человек — в профиль и в свои записи
        .onChange(of: auth.account) { _, account in
            if let account {
                model.setAccount(username: account.username, displayName: account.displayName, info: account.info)
            } else {
                model.reset()
            }
        }
        .task { auth.restore() }
    }
}
