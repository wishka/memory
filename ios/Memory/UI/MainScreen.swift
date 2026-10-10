import SwiftUI

/// Само приложение: космический фон, экран вкладки и парящая панель
/// вкладок. Поверх вкладки могут быть открыты страница человека, группа
/// или переписка — как на Android, без NavigationStack.
struct MainScreen: View {
    let model: AppModel
    let onSaveProfile: (String, ProfileInfo) -> Void
    let account: AccountActions

    var body: some View {
        let state = model.state
        ZStack(alignment: .bottom) {
            CosmicBackground()

            if let chat = state.openChat {
                // Переписка — во весь экран, панель вкладок прячется
                ChatScreen(
                    model: model, chat: chat, otherChats: state.chats.filter { $0.id != chat.id },
                    blocked: state.isBlocked(chat.peer.id)
                )
                    .transition(.move(edge: .trailing))
                    .simultaneousGesture(backSwipe)
            } else {
                Group {
                    if let person = state.openPerson {
                        PersonScreen(model: model, person: person)
                            .transition(.move(edge: .trailing))
                            .simultaneousGesture(backSwipe)
                    } else if let group = state.openGroup, state.groupSettingsOpen {
                        GroupSettingsScreen(model: model, group: group)
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
        // Обсуждение записи или PRISM — шторкой поверх любого экрана
        .sheet(isPresented: commentsShown) { comments }
        // Фото или видео — на весь экран
        .fullScreenCover(item: viewing) { attachment in
            MediaViewer(attachment: attachment) { model.closeMedia() }
        }
        // Свой PRISM: камера → редактор → публикация
        .fullScreenCover(isPresented: creatingPrism) {
            PrismCreateFlow(model: model)
        }
        .animation(.easeOut(duration: 0.22), value: state.tab)
        .animation(.easeOut(duration: 0.25), value: state.openChatId)
        .animation(.easeOut(duration: 0.25), value: state.openPersonId)
        .animation(.easeOut(duration: 0.25), value: state.openGroupId)
        .animation(.easeOut(duration: 0.25), value: state.groupSettingsOpen)
        .animation(.easeOut(duration: 0.2), value: state.profileSection)
        .animation(.easeOut(duration: 0.2), value: state.settingsPage)
        .animation(.easeOut(duration: 0.2), value: state.personSection)
    }

    private var commentsShown: Binding<Bool> {
        Binding(
            get: { model.state.commentsPostId != nil || model.state.commentsPrismId != nil },
            set: { if !$0 { model.closeComments() } }
        )
    }

    private var creatingPrism: Binding<Bool> {
        Binding(
            get: { model.state.creatingPrism },
            set: { if !$0 { model.closePrismCreator() } }
        )
    }

    private var viewing: Binding<Attachment?> {
        Binding(
            get: { model.state.viewing },
            set: { if $0 == nil { model.closeMedia() } }
        )
    }

    /// Автор комментария: шторка закрывается, открывается его страница.
    private func openAuthor(_ userId: Int) {
        model.closeComments()
        model.openPerson(userId)
    }

    @ViewBuilder
    private var comments: some View {
        let state = model.state
        if let id = state.commentsPostId, let post = state.findPost(id) {
            CommentsSheet(
                total: post.comments,
                comments: post.commentList.filter { !state.isBlocked($0.author.id) && !state.reportedComments.contains($0.id) },
                myId: state.me.id,
                onSend: { model.addComment(id, $0, replyTo: $1) },
                onDelete: { model.deleteComment(id, $0) },
                onReport: { model.reportComment($0) },
                onLike: { model.toggleCommentLike(id, $0) },
                onAuthor: openAuthor
            )
        } else if let id = state.commentsPrismId, let prism = state.findPrism(id) {
            CommentsSheet(
                total: prism.comments,
                comments: prism.commentList.filter { !state.isBlocked($0.author.id) && !state.reportedComments.contains($0.id) },
                myId: state.me.id,
                onSend: { model.addPrismComment(id, $0, replyTo: $1) },
                onDelete: { model.deletePrismComment(id, $0) },
                onReport: { model.reportComment($0) },
                onLike: { model.togglePrismCommentLike(id, $0) },
                onAuthor: openAuthor
            )
        }
    }

    @ViewBuilder
    private func tabContent(_ tab: AppTab) -> some View {
        switch tab {
        case .feed: FeedScreen(model: model)
        case .stories: StoriesScreen(model: model)
        case .prism: PrismScreen(model: model)
        case .chats: ChatsScreen(model: model)
        case .groups: GroupsScreen(model: model)
        case .profile:
            if let page = model.state.settingsPage {
                SettingsScreen(model: model, page: page, account: account)
            } else if model.state.profileSection == .music {
                MusicScreen(model: model)
            } else if model.state.profileSection == .stickers {
                StickerStudioScreen(model: model)
            } else if let section = model.state.profileSection {
                ProfileSectionScreen(model: model, section: section)
            } else {
                ProfileScreen(model: model, onSaveProfile: onSaveProfile)
            }
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
