import SwiftUI
import UIKit

/// Само приложение: космический фон, экран вкладки и парящая панель
/// вкладок. Поверх вкладки могут быть открыты страница человека, группа
/// или переписка — как на Android, без NavigationStack.
struct MainScreen: View {
    let model: AppModel
    let onSaveProfile: (String, ProfileInfo) -> Void
    let account: AccountActions
    /// «Поделиться ссылкой» на запись — системное меню.
    @State private var sharing: SharePayload?
    /// Короткая подсказка внизу: «Ссылка скопирована», «Это недоступно…».
    @State private var notice: String?
    /// Один раз — спросить про отчёты о сбоях и статистику (если сборка умеет их отправлять).
    @State private var askTelemetry = TelemetrySetup.hasKey && !TelemetrySetup.asked

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
                    // Активность и поиск — поверх вкладки; с них можно уйти на человека или группу
                    if state.activityOpen {
                        ActivityScreen(model: model)
                            .transition(.move(edge: .trailing))
                            .simultaneousGesture(backSwipe)
                    } else if let post = state.openPostId.flatMap({ state.findPost($0) }), state.openPerson == nil, state.openGroup == nil {
                        PostScreen(model: model, post: post)
                            .transition(.move(edge: .trailing))
                            .simultaneousGesture(backSwipe)
                    } else if state.searchOpen && state.openPerson == nil && state.openGroup == nil {
                        SearchScreen(model: model)
                            .transition(.move(edge: .trailing))
                            .simultaneousGesture(backSwipe)
                    } else if let person = state.openPerson {
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
        // «Нет сети» / «Сервер не отвечает» — пилюлей сверху, под строкой состояния
        .overlay(alignment: .top) {
            NetBanner(status: state.netStatus, waiting: state.outboxCount)
                .padding(.top, 4)
                .allowsHitTesting(false)
                .transition(.opacity)
        }
        .animation(.easeOut(duration: 0.2), value: state.netStatus)
        // Уведомление, пока приложение открыто, — плашкой сверху
        .overlay(alignment: .top) {
            if let nudge = state.banner {
                NudgeBanner(nudge: nudge, onOpen: { model.openNudge(nudge) }, onDismiss: { model.dismissBanner() })
                    .id(nudge.id)
                    .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .animation(.easeOut(duration: 0.25), value: state.banner?.id)
        // Отклик на чек-ин и приглашение — окном посреди экрана
        .overlay {
            if let echo = state.echo {
                EchoDialog(model: model, echo: echo)
                    .transition(.opacity)
            } else if let invite = state.openedInvite {
                InviteDialog(model: model, invite: invite)
                    .transition(.opacity)
            }
        }
        .animation(.easeOut(duration: 0.2), value: state.echo != nil)
        .animation(.easeOut(duration: 0.2), value: state.openedInvite != nil)
        // Запуск эфира, итог эфира и вопрос про отчёты о сбоях — окном посреди экрана
        .overlay {
            if state.liveStarting {
                LiveStartDialog(model: model)
                    .transition(.opacity)
            } else if let summary = state.liveSummary {
                LiveSummaryDialog(model: model, live: summary)
                    .transition(.opacity)
            } else if askTelemetry && state.banner == nil {
                TelemetryConsentDialog { askTelemetry = false }
                    .transition(.opacity)
            }
        }
        .animation(.easeOut(duration: 0.2), value: state.liveStarting)
        .animation(.easeOut(duration: 0.2), value: state.liveSummary != nil)
        // Эфир — во весь экран, поверх всего
        .fullScreenCover(isPresented: watchingLive) {
            LiveCover(model: model)
        }
        // Правка своей записи — шторкой
        .sheet(item: editingPost) { post in
            EditPostSheet(model: model, post: post)
        }
        // Репост и «Отправить в чат» — шторкой; из репоста можно перейти в выбор переписки
        .sheet(item: postShare) { share in
            Group {
                switch share {
                case .repost(let post): RepostSheet(model: model, post: post)
                case .send(let post): SendToChatSheet(model: model, post: post)
                }
            }
        }
        .shareSheet($sharing)
        .toast($notice)
        // Шторка «Это в Memory+» — поверх всего, где бы ни нажали
        .paywall(model)
        // Подписка Memory+ сверяется с часами: закончилась — тариф бесплатный
        .task {
            while !Task.isCancelled {
                model.refreshPlan()
                try? await Task.sleep(nanoseconds: 60_000_000_000)
            }
        }
        // Обсуждение записи или PRISM — шторкой поверх любого экрана
        .sheet(isPresented: commentsShown) {
            // Жалоба на комментарий — окном поверх самой шторки
            comments.reportSheet(model: model)
        }
        // Жалоба: причина, пара слов, заблокировать автора
        .reportSheet(model: model, active: !commentsShown.wrappedValue && model.state.watchingLiveId == nil)
        // Документ: соглашение, политика, правила, лицензии
        .fullScreenCover(item: legalDoc) { doc in
            LegalDocViewer(doc: doc) { model.closeLegal() }
        }
        // Фото или видео — на весь экран; имя отметки на фото — страница человека
        .fullScreenCover(item: viewing) { attachment in
            MediaViewer(attachment: attachment, onClose: { model.closeMedia() }, onPerson: { id in
                model.closeMedia()
                model.openPerson(id)
            })
        }
        // Свой PRISM: камера → редактор → публикация
        .fullScreenCover(isPresented: creatingPrism) {
            PrismCreateFlow(model: model)
        }
        // Истории — новая и просмотр — во весь экран
        .fullScreenCover(isPresented: storyComposer) {
            StoryComposerScreen(model: model)
        }
        .fullScreenCover(isPresented: storyViewer) {
            StoryViewerScreen(model: model)
        }
        // Звонок — своим окном поверх всего, даже поверх шторок; жестом не закрывается
        .callWindow(model)
        // PRISM, эфир, звонок, история, фото на весь экран и переписка
        // с «космическим» фоном — тёмные и в светлой теме
        .onChange(of: overlayDark, initial: true) { _, dark in
            ThemeState.shared.overlayDark = dark
        }
        .animation(.easeOut(duration: 0.22), value: state.tab)
        .animation(.easeOut(duration: 0.25), value: state.openChatId)
        .animation(.easeOut(duration: 0.25), value: state.openPersonId)
        .animation(.easeOut(duration: 0.25), value: state.openGroupId)
        .animation(.easeOut(duration: 0.25), value: state.groupSettingsOpen)
        .animation(.easeOut(duration: 0.2), value: state.profileSection)
        .animation(.easeOut(duration: 0.2), value: state.settingsPage)
        .animation(.easeOut(duration: 0.2), value: state.personSection)
        .animation(.easeOut(duration: 0.25), value: state.activityOpen)
        .animation(.easeOut(duration: 0.25), value: state.searchOpen)
        .animation(.easeOut(duration: 0.25), value: state.openPostId)
        // #теги, @упоминания и ссылки в тексте; ссылка на запись и правка — в меню любой карточки
        .environment(\.linkActions, links)
        // Своя запись в очереди: есть ли сеть и сколько ушло
        .environment(\.syncInfo, sync)
    }

    /// Сеть и прогресс отправок — для SyncLine под своими записями.
    private var sync: SyncInfo {
        let model = self.model
        return SyncInfo(status: model.state.netStatus, progress: { owner in model.state.uploadProgress(owner) })
    }

    /// Что делают ссылки в тексте и меню «⋯» записи — для всех экранов сразу.
    private var links: LinkActions {
        LinkActions(
            meId: model.state.me.id,
            onTag: { model.openTag($0) },
            onMention: { model.openHandle($0) },
            onUrl: { openUrl($0) },
            suggest: { model.mentionCandidates($0) },
            onShareLink: { post in
                Telemetry.track(.linkShared)
                sharing = SharePayload(items: [Links.shareText(post)])
            },
            onCopyLink: { post in
                copyText(Links.post(post.id))
                notice = t("Ссылка скопирована")
            },
            onEdit: { model.startEdit($0.id) },
            onRepost: { model.openRepost($0.id) },
            onSendToChat: { model.openSendToChat($0.id) },
            onOpenRef: { ref in
                if !model.openPostRef(ref) {
                    notice = t("Это недоступно на этом телефоне — возможно, запись удалили или профиль закрыт.")
                }
            },
            onPerson: { model.openPerson($0) },
            onUntagMe: { model.untagMe($0.id) }
        )
    }

    /// Что всегда ночное — PRISM, эфир, звонок, история, фото на весь экран —
    /// тёмное и в светлой теме. Переписка с «космическим» фоном — тоже:
    /// светлый текст шапки на тёмной картинке. То же — overlayDark в MainActivity.kt.
    private var overlayDark: Bool {
        let s = model.state
        let darkBackdrop = s.openChat.map { $0.backdropOr(s.backdrop).style != BackdropStyle.plain.id } ?? false
        return s.tab == .prism || s.viewing != nil || s.creatingPrism || s.watchingLiveId != nil || darkBackdrop
            || s.storyViewer != nil || s.storyComposer || s.call != nil
    }

    private var storyComposer: Binding<Bool> {
        Binding(
            get: { model.state.storyComposer },
            set: { if !$0 && model.state.storyComposer { model.closeStoryComposer() } }
        )
    }

    private var storyViewer: Binding<Bool> {
        Binding(
            get: { model.state.storyViewer != nil },
            set: { if !$0 && model.state.storyViewer != nil { model.closeStories() } }
        )
    }

    private var postShare: Binding<PostShare?> {
        Binding(
            get: {
                let s = model.state
                if let id = s.repostingPostId, let post = s.findPost(id) { return .repost(post) }
                if let id = s.sharingPostId, let post = s.findPost(id) { return .send(post) }
                return nil
            },
            set: {
                if $0 == nil && (model.state.repostingPostId != nil || model.state.sharingPostId != nil) {
                    model.closeRepost()
                }
            }
        )
    }

    /// Ссылка Memory — в приложении; остальные — в браузере.
    private func openUrl(_ link: String) {
        if let target = Links.parse(link) {
            if !model.openLink(target) {
                notice = t("Это недоступно на этом телефоне — возможно, запись удалили или профиль закрыт.")
            }
        } else if let url = URL(string: link) {
            UIApplication.shared.open(url)
        }
    }

    private var watchingLive: Binding<Bool> {
        Binding(
            get: { model.state.watchingLive != nil },
            // Закрыли жестом — как «назад»: зритель выходит, ведущему — вопрос
            set: { if !$0 && model.state.watchingLiveId != nil { _ = model.back() } }
        )
    }

    private var editingPost: Binding<Post?> {
        Binding(
            get: { model.state.editingPostId.flatMap { model.state.findPost($0) } },
            set: { if $0 == nil && model.state.editingPostId != nil { model.cancelEdit() } }
        )
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

    private var legalDoc: Binding<LegalDoc?> {
        Binding(
            get: { model.state.legalDoc },
            set: { if $0 == nil { model.closeLegal() } }
        )
    }

    /// Пожаловаться на комментарий: открыть окно жалобы с ним.
    private func reportComment(_ list: [Comment], _ commentId: Int64) {
        if let comment = list.first(where: { $0.id == commentId }) {
            model.reportComment(comment)
        }
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
                onSend: { model.addComment(id, $0, replyTo: $1, sticker: $2) },
                onDelete: { model.deleteComment(id, $0) },
                onReport: { reportComment(post.commentList, $0) },
                onLike: { model.toggleCommentLike(id, $0) },
                onAuthor: openAuthor,
                stickers: state.stickers,
                // К своей записи — фильтр: скрытые слова, оскорбления, спам
                hiddenOf: commentFilter(post),
                closed: post.commentsOff,
                onToggleClosed: commentsToggle(post)
            )
        } else if let id = state.commentsPrismId, let prism = state.findPrism(id) {
            CommentsSheet(
                total: prism.comments,
                comments: prism.commentList.filter { !state.isBlocked($0.author.id) && !state.reportedComments.contains($0.id) },
                myId: state.me.id,
                onSend: { model.addPrismComment(id, $0, replyTo: $1, sticker: $2) },
                onDelete: { model.deletePrismComment(id, $0) },
                onReport: { reportComment(prism.commentList, $0) },
                onLike: { model.togglePrismCommentLike(id, $0) },
                onAuthor: openAuthor,
                stickers: state.stickers
            )
        }
    }

    /// Фильтр комментариев — только к своей записи.
    private func commentFilter(_ post: Post) -> (Comment) -> HiddenReason? {
        let state = model.state
        guard post.author.id == state.me.id else { return { _ in nil } }
        return { c in state.hiddenReason(c.text, c.author.id) }
    }

    /// «Выключить / Включить комментарии» — только у своей записи.
    private func commentsToggle(_ post: Post) -> (() -> Void)? {
        guard post.author.id == model.state.me.id else { return nil }
        let postId = post.id
        return { model.toggleComments(postId) }
    }

    @ViewBuilder
    private func tabContent(_ tab: AppTab) -> some View {
        switch tab {
        case .feed: FeedScreen(model: model)
        case .stories: StoriesScreen(model: model)
        case .prism: PrismScreen(model: model)
        case .chats: ChatsScreen(model: model)
        case .groups: GroupsScreen(model: model)
        case .together: TogetherScreen(model: model)
        case .profile:
            if let page = model.state.settingsPage {
                SettingsScreen(model: model, page: page, account: account)
            } else if model.state.profileSection == .music {
                MusicScreen(model: model)
            } else if model.state.profileSection == .stickers {
                StickerStudioScreen(model: model)
            } else if model.state.profileSection == .recap {
                RecapScreen(model: model)
            } else if model.state.profileSection == .shelf {
                ShelfScreen(model: model)
            } else if model.state.profileSection == .premium {
                PremiumScreen(model: model)
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

/// Шторка записи: репост в свою ленту или отправка в переписку.
private enum PostShare: Identifiable {
    case repost(Post)
    case send(Post)

    var id: String {
        switch self {
        case .repost(let post): return "repost-\(post.id)"
        case .send(let post): return "send-\(post.id)"
        }
    }
}
