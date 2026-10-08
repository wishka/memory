import SwiftUI

/// Само приложение: космический фон, экран вкладки и парящая панель
/// вкладок. Поверх вкладки могут быть открыты страница человека, группа
/// или переписка — как на Android, без NavigationStack.
struct MainScreen: View {
    let model: AppModel
    let onSaveProfile: (String, ProfileInfo) -> Void
    let onLogout: () -> Void

    var body: some View {
        let state = model.state
        ZStack(alignment: .bottom) {
            CosmicBackground()

            if let chat = state.openChat {
                // Переписка — во весь экран, панель вкладок прячется
                ChatScreen(model: model, chat: chat)
                    .transition(.move(edge: .trailing))
                    .simultaneousGesture(backSwipe)
            } else {
                Group {
                    if let person = state.openPerson {
                        PersonScreen(model: model, person: person)
                            .transition(.move(edge: .trailing))
                            .simultaneousGesture(backSwipe)
                    } else if let group = state.openGroup {
                        GroupScreen(model: model, group: group)
                            .transition(.move(edge: .trailing))
                            .simultaneousGesture(backSwipe)
                    } else {
                        tabContent(state.tab)
                            .transition(.opacity)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)

                BottomBar(selected: state.tab, unread: state.unreadTotal) { tab in
                    model.selectTab(tab)
                }
                .padding(.bottom, 4)
                // Клавиатура не поднимает панель: она остаётся внизу, под клавиатурой
                .ignoresSafeArea(.keyboard, edges: .bottom)
            }
        }
        .animation(.easeOut(duration: 0.22), value: state.tab)
        .animation(.easeOut(duration: 0.25), value: state.openChatId)
        .animation(.easeOut(duration: 0.25), value: state.openPersonId)
        .animation(.easeOut(duration: 0.25), value: state.openGroupId)
    }

    @ViewBuilder
    private func tabContent(_ tab: AppTab) -> some View {
        switch tab {
        case .feed: FeedScreen(model: model)
        case .stories: StoriesScreen(model: model)
        case .reels: ReelsScreen(model: model)
        case .chats: ChatsScreen(model: model)
        case .groups: GroupsScreen(model: model)
        case .profile: ProfileScreen(model: model, onSaveProfile: onSaveProfile, onLogout: onLogout)
        }
    }

    /// Свайп от левого края вправо — «назад», как в системных приложениях.
    private var backSwipe: some Gesture {
        DragGesture(minimumDistance: 24)
            .onEnded { value in
                if value.startLocation.x < 32 && value.translation.width > 80 && abs(value.translation.height) < 80 {
                    model.back()
                }
            }
    }
}
