import SwiftUI

/// Вкладка «Лента»: поле новой записи и записи друзей.
struct FeedScreen: View {
    let model: AppModel
    @State private var sharing: SharePayload?

    /// Своя запись: поделиться карточкой чек-ина, закрепить в витрине профиля или удалить.
    private func ownMenu(_ post: Post) -> [GlassAction] {
        guard post.author.id == model.state.me.id else { return [] }
        let pinned = model.state.profilePins.contains(post.id)
        var card: [GlassAction] = []
        if let c = post.checkIn {
            card.append(GlassAction(icon: .share, title: t("Поделиться карточкой")) {
                sharing = shareCard(model, checkInCard(c, model.state), name: "checkin-\(post.id)", action: .app)
            })
        }
        return card + [
            GlassAction(icon: .pushpin, title: pinned ? t("Убрать из витрины") : t("Закрепить в профиле")) {
                model.toggleProfilePin(post.id)
            },
            GlassAction(icon: .trash, title: t("Удалить запись"), destructive: true) {
                post.media.forEach(deleteAttachmentFiles)
                model.deletePost(post.id)
            },
        ]
    }

    /// Карточка записи — одна и та же в ленте и в «Интересном».
    private func feedCard(_ post: Post, _ state: UiState) -> some View {
        let mine = model.isMine(post)
        let friend: FriendState? = post.author.id == state.me.id ? nil : state.friendState(post.author.id)
        return PostCard(
            post: post,
            onLike: { model.toggleLike(post.id) },
            friend: friend,
            onFriend: { model.toggleFriend(post.author.id) },
            onAuthor: { model.openPerson(post.author.id) },
            onComments: { model.openComments(post.id) },
            onOpenMedia: model.openMedia,
            onVote: { model.votePost(post.id, $0) },
            onClosePoll: mine ? { model.closePostPoll(post.id) } : nil,
            menu: ownMenu(post),
            onReport: mine ? nil : { model.reportPost(post) }
        )
    }

    private var exploreTitle: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(t("Интересное"))
                .textStyle(.headline, Palette.ink)
            Text(t("Люди, на которых вы ещё не подписаны"))
                .textStyle(.bodySmall, Palette.mist)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 20)
        .padding(.vertical, 8)
    }

    /// Низ «Интересного»: дошли — следующая страница.
    private func exploreFooter(_ state: UiState) -> some View {
        let paging = state.explorePaging
        let count: Int = state.explore.count
        return Group {
            if paging.loading {
                FeedFooter(paging: paging) { model.loadMoreExplore() }
            } else {
                Color.clear.frame(height: 1)
            }
        }
        .task(id: count) { model.loadMoreExplore() }
    }

    var body: some View {
        let state = model.state
        ScrollView {
            LazyVStack(spacing: 14) {
                HStack(alignment: .center) {
                    ScreenTitle(title: t("Лента"), subtitle: t("Моменты, которые остаются"))
                    Spacer(minLength: 8)
                    // Поиск людей, записей и #тегов; колокольчик — лента активности
                    RoundGlyphButton(icon: .search, color: Palette.lilac) { model.openSearch() }
                    ActivityBell(unread: state.activityUnread) { model.openActivity() }
                        .padding(.leading, 4)
                    // Знак Memory: кристалл со стеклом, как слово MEMORY на заставке
                    CrystalLogo(size: 44)
                        .accessibilityHidden(true)
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 8)

                PostComposer(model: model)

                // С сервером, но вход на него не удался (пароль телефон не хранит) — спросить один раз
                if state.serverMode && state.signedOut {
                    ServerSignInCard(model: model)
                }

                // Новичку — гид по приложению, пока карточку не скрыли
                if !state.guideHidden {
                    GuideFeedCard(model: model)
                }

                // «Год назад вы…»: своя запись из этого же дня — пока не закрыли сегодня
                let memories = memoriesOfDay(state.posts, meId: state.me.id)
                if let first = memories.first, state.memorySeen != dayKey() {
                    MemoryCard(model: model, memory: first, count: memories.count)
                }

                // Запись или сообщение не ушли — сказать один раз, можно закрыть
                if let notice = state.netNotice {
                    NetNoticeCard(text: notice) { model.dismissNetNotice() }
                }

                ForEach(state.feedWithOlder) { post in
                    feedCard(post, state)
                }

                // Низ ленты: дошли — подгрузить старое с сервера
                FeedFooter(paging: state.feedPaging) { model.loadMoreFeed() }
                    .task(id: footerKey(state)) { model.loadMoreFeed() }

                // «Интересное»: лента друзей кончилась — записи остальных людей сервера
                let explore: [Post] = state.exploreVisible
                if state.feedPaging.end && !explore.isEmpty {
                    exploreTitle
                    // Свои ключи («x» + номер): не путаются с записями ленты выше
                    ForEach(explore, id: \.exploreKey) { post in
                        feedCard(post, state)
                    }
                    exploreFooter(state)
                }
            }
            .padding(.top, 12)
            .padding(.bottom, tabBarSpace)
        }
        .scrollDismissesKeyboard(.interactively)
        // Потянули вниз — свежее с сервера
        .refreshable { await model.refreshFeedNow() }
        .shareSheet($sharing)
    }
}

private extension Post {
    /// Ключ записи в «Интересном» — отдельный от ключей ленты.
    var exploreKey: String { "x\(id)" }
}

/// Низ ленты появился, подгрузилось старое или вернулась сеть — пробовать дальше.
private func footerKey(_ state: UiState) -> String {
    let count: Int = state.olderFeed.count
    return "\(count)-\(state.netStatus)"
}

/// «Войдите на сервер»: пароль Memory ещё раз — и друзья видят записи, можно переписываться.
private struct ServerSignInCard: View {
    let model: AppModel
    @State private var password = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(t("Войдите на сервер"))
                .textStyle(.title, Palette.ink)
            Text(t("Введите пароль Memory ещё раз — так друзья увидят ваши записи и смогут вам написать. Пароль нужен один раз."))
                .textStyle(.bodySmall, Palette.mist)
                .fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 10) {
                GlassField(placeholder: t("Пароль"), text: $password, limit: 128, singleLine: true, secure: true, plain: true)
                    .glass(radius: 14)
                GlowButton(title: t("Войти"), enabled: !password.isEmpty) {
                    model.signInToServer(password)
                    password = ""
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glass(radius: 20)
        .padding(.horizontal, 16)
    }
}

/// «Запись не отправлена…» — стеклянная карточка над лентой с крестиком.
private struct NetNoticeCard: View {
    let text: String
    let onDismiss: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Text(text)
                .textStyle(.bodySmall, Palette.ink)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: onDismiss) {
                Glyph(kind: .close, color: Palette.mist, size: 16)
                    .frame(width: 32, height: 32)
                    .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(t("Закрыть"))
        }
        .padding(12)
        .glass(radius: 16)
        .padding(.horizontal, 16)
    }
}

/// Воспоминание дня: запись, чек-ин или фото, и сколько прошло.
private struct MemoryCard: View {
    let model: AppModel
    let memory: MemoryOfDay
    let count: Int

    var body: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 10) {
                Text(t("В ЭТОТ ДЕНЬ") + " · " + memory.label.uppercased()).textStyle(.label, Palette.lilac)
                if let c = memory.post.checkIn { CheckInBadge(checkIn: c) }
                if !memory.post.text.isEmpty {
                    Text(memory.post.text).textStyle(.body).lineLimit(4)
                }
                if !memory.post.media.isEmpty {
                    PostMediaView(media: memory.post.media.filter { $0.kind != .document && !$0.sticker }) { model.openMedia($0) }
                }
                if count > 1 {
                    Text(tf("И ещё воспоминаний: %s", count - 1)).textStyle(.bodySmall, Palette.mist)
                }
                HStack(spacing: 10) {
                    GlowButton(title: t("Все записи")) { model.openProfileSection(.posts) }
                    GhostButton(title: t("Скрыть до завтра")) { model.dismissMemory() }
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .cardPadding()
    }
}

/// Карточка-картинка чек-ина: что, оценка, темп и статус книги.
func checkInCard(_ c: CheckIn, _ state: UiState) -> ShareCardSpec {
    var lines: [EchoLine] = []
    if c.rating > 0 {
        lines.append(EchoLine(emoji: "⭐", text: String(repeating: "★", count: c.rating) + String(repeating: "☆", count: max(0, 5 - c.rating))))
    }
    if !c.amountTitle.isEmpty { lines.append(EchoLine(emoji: "📏", text: c.amountTitle)) }
    if !c.paceTitle.isEmpty { lines.append(EchoLine(emoji: "⚡", text: tf("Темп: %s", c.paceTitle))) }
    if !c.statusTitle.isEmpty { lines.append(EchoLine(emoji: "📖", text: c.statusTitle)) }
    return ShareCardSpec(
        kicker: t("Чек-ин"), emoji: c.emoji, title: c.subject.isEmpty ? c.displayTitle : c.subject,
        subtitle: c.displayTitle, lines: lines, author: "\(state.me.name) · @\(state.me.handle)"
    )
}

/// Карточка записи. friend: nil — это своя запись (кнопки дружбы нет),
/// иначе — что между вами и автором: «Добавить в друзья», «Заявка
/// отправлена», «Принять заявку» или «В друзьях».
struct PostCard: View {
    let post: Post
    let onLike: () -> Void
    var friend: FriendState? = nil
    /// AppModel.toggleFriend автора; удаление из друзей кнопка сначала подтверждает.
    var onFriend: () -> Void = {}
    /// Нажатие на аватар или имя — страница автора; nil — не нажимается.
    var onAuthor: (() -> Void)? = nil
    /// Открыть обсуждение; nil — значок комментариев не нажимается.
    var onComments: (() -> Void)? = nil
    /// Фото или видео записи на весь экран.
    var onOpenMedia: (Attachment) -> Void = { _ in }
    /// Закреплена в группе — метка над текстом.
    var pinned = false
    /// Голос в опросе записи; nil — опрос только для чтения.
    var onVote: ((Int) -> Void)? = nil
    /// Завершить опрос — только у автора; nil — кнопки нет.
    var onClosePoll: (() -> Void)? = nil
    /// Пункты меню «⋯» (закрепить, удалить…); пусто — меню нет.
    var menu: [GlassAction] = []
    /// Пожаловаться на чужую запись — пункт в меню «⋯»; nil — пункта нет.
    var onReport: (() -> Void)? = nil

    @State private var showMenu = false
    /// Место записи на карте во весь экран.
    @State private var placeOpen: SharedPlace?
    /// Список отмеченных на фото.
    @State private var tagsOpen = false
    /// Ссылка на запись и правка своей — в меню любой карточки, где бы она ни была.
    @Environment(\.linkActions) private var links

    /// Меню «⋯»: «Изменить» своё, свои пункты, ссылка на запись и «Пожаловаться» для чужой записи.
    private var menuItems: [GlassAction] {
        var list: [GlassAction] = []
        let post = self.post
        if let links, post.author.id == links.meId, post.artSeed == nil {
            list.append(GlassAction(icon: .edit, title: t("Изменить")) { links.onEdit(post) })
        }
        list += menu
        if let links {
            list.append(GlassAction(icon: .share, title: t("Поделиться ссылкой")) { links.onShareLink(post) })
            list.append(GlassAction(icon: .link, title: t("Скопировать ссылку")) { links.onCopyLink(post) })
        }
        if let onReport {
            list.append(GlassAction(icon: .flag, title: t("Пожаловаться"), destructive: true, action: onReport))
        }
        return list
    }

    var body: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    author
                    Spacer(minLength: 0)
                    if let friend {
                        FriendButton(state: friend, name: post.author.name, onToggle: onFriend)
                    }
                    if !menu.isEmpty || onReport != nil || links != nil {
                        Button { showMenu = true } label: {
                            Glyph(kind: .more, color: Palette.mist, size: 20)
                                .frame(width: 36, height: 36)
                                .contentShape(Circle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(LineIcon.more.label)
                        .accessibilityHint(t("Действия с записью"))
                    }
                }
                if pinned {
                    HStack(spacing: 6) {
                        Glyph(kind: .pushpin, color: Palette.lilac, size: 16)
                        Text(t("Закреплено").uppercased()).textStyle(.label, Palette.lilac)
                    }
                }
                if let checkIn = post.checkIn {
                    CheckInBadge(checkIn: checkIn)
                }
                if let place = post.place {
                    PlaceLine(place: place) { placeOpen = place }
                }
                // Репост без своих слов — «поделился записью» над оригиналом
                if post.repostOf != nil && post.text.isEmpty {
                    HStack(spacing: 6) {
                        Glyph(kind: .repost, color: Palette.lilac, size: 16)
                        Text(Nudges.female(post.author) ? t("поделилась записью") : t("поделился записью"))
                            .textStyle(.labelLarge, Palette.lilac)
                    }
                }
                if !post.text.isEmpty {
                    RichText(text: post.text)
                        .fixedSize(horizontal: false, vertical: true)
                }
                // Своя запись ещё не на сервере: «Отправляется · 45%» или «Ждёт сети»
                SyncLine(owner: post.outboxKey)
                if let ref = post.repostOf {
                    PostRefCard(ref: ref) { links?.onOpenRef(ref) }
                }
                if let poll = post.poll {
                    PollCard(poll: poll, onVote: { onVote?($0) }, onClose: onClosePoll)
                }
                PostMediaView(media: post.media.filter { $0.kind != .document && !$0.sticker }, onOpen: onOpenMedia)
                if !tagged.isEmpty {
                    TaggedLine(userIds: tagged) { tagsOpen = true }
                }
                StickerRow(stickers: post.media.filter(\.sticker))
                PostFiles(files: post.media.filter { $0.kind == .document })
                if let seed = post.artSeed {
                    Nebula(seed: seed)
                        .frame(height: 200)
                        .frame(maxWidth: .infinity)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                HStack(spacing: 14) {
                    Button(action: onLike) {
                        HStack(spacing: 6) {
                            LikeHeart(liked: post.liked, color: post.liked ? Palette.lilac : Palette.mist, size: 22)
                            Text("\(post.likes)").textStyle(.labelLarge, post.liked ? Palette.lilac : Palette.mist)
                        }
                        .padding(.horizontal, 6)
                        .padding(.vertical, 4)
                        .contentShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .sensoryFeedback(.selection, trigger: post.liked)
                    .accessibilityLabel(tf("Нравится: %s", post.likes))
                    .accessibilityValue(post.liked ? t("отмечено") : t("не отмечено"))
                    .accessibilityAddTraits(post.liked ? .isSelected : [])
                    Button { onComments?() } label: {
                        HStack(spacing: 6) {
                            Glyph(kind: .comment, color: Palette.mist, size: 22)
                            Text("\(post.comments)").textStyle(.labelLarge, Palette.mist)
                        }
                        .padding(.horizontal, 6)
                        .padding(.vertical, 4)
                        .contentShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .disabled(onComments == nil)
                    .accessibilityLabel(tf("Комментарии: %s", post.comments))
                    // Репост в свою ленту и «Отправить в чат» — у записей, не у PRISM-картинок
                    if let links, post.artSeed == nil {
                        Button { links.onRepost(post) } label: {
                            HStack(spacing: 6) {
                                Glyph(kind: .repost, color: Palette.mist, size: 22)
                                if post.reposts > 0 {
                                    Text("\(post.reposts)").textStyle(.labelLarge, Palette.mist)
                                }
                            }
                            .padding(.horizontal, 6)
                            .padding(.vertical, 4)
                            .contentShape(Capsule())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(tf("Репосты: %s", post.reposts))
                        Spacer(minLength: 0)
                        Button { links.onSendToChat(post) } label: {
                            Glyph(kind: .send, color: Palette.mist, size: 20)
                                .frame(width: 36, height: 36)
                                .contentShape(Circle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(t("Отправить в чат"))
                    }
                }
            }
            .padding(16)
        }
        .cardPadding()
        .glassActions($showMenu, cancel: true, actions: menuItems)
        .fullScreenCover(item: $placeOpen) { place in
            PlaceViewer(place: place) { placeOpen = nil }
        }
        .sheet(isPresented: $tagsOpen) {
            TaggedSheet(
                userIds: tagged,
                onPerson: { id in
                    tagsOpen = false
                    links?.onPerson(id)
                },
                meId: links?.meId,
                onUntagMe: {
                    tagsOpen = false
                    links?.onUntagMe(post)
                },
                onClose: { tagsOpen = false }
            )
        }
    }

    /// Кто отмечен на фото записи — без повторов, по порядку.
    private var tagged: [Int] {
        var seen = Set<Int>()
        return post.media.flatMap(\.tags).map(\.userId).filter { seen.insert($0).inserted }
    }

    @ViewBuilder
    private var author: some View {
        let row = HStack(spacing: 12) {
            Avatar(user: post.author, size: 40)
            VStack(alignment: .leading, spacing: 2) {
                Text(post.author.name).textStyle(.title).lineLimit(1)
                Text("@\(post.author.handle) · \(agoTitle(minutesSince(post.createdAt, post.minutesAgo)))" + (post.editedAt != nil ? " · " + t("изменено") : ""))
                    .textStyle(.bodySmall, Palette.mist)
                    .lineLimit(1)
            }
        }
        if let onAuthor {
            Button(action: onAuthor) { row.contentShape(Rectangle()) }
                .buttonStyle(.plain)
        } else {
            row
        }
    }
}

/// Отступ снизу у прокручиваемых экранов: под парящей панелью вкладок.
let tabBarSpace: CGFloat = 100
