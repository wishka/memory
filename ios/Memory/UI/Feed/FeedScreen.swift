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
                post.media.forEach { MediaFiles.delete($0) }
                model.deletePost(post.id)
            },
        ]
    }

    var body: some View {
        let state = model.state
        ScrollView {
            LazyVStack(spacing: 14) {
                HStack(alignment: .center) {
                    ScreenTitle(title: t("Лента"), subtitle: t("Моменты, которые остаются"))
                    Spacer()
                    // Знак Memory: кристалл со стеклом, как слово MEMORY на заставке
                    CrystalLogo(size: 56)
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 8)

                PostComposer(model: model)

                // «Год назад вы…»: своя запись из этого же дня — пока не закрыли сегодня
                let memories = memoriesOfDay(state.posts, meId: state.me.id)
                if let first = memories.first, state.memorySeen != dayKey() {
                    MemoryCard(model: model, memory: first, count: memories.count)
                }

                ForEach(state.feed) { post in
                    PostCard(
                        post: post,
                        onLike: { model.toggleLike(post.id) },
                        following: post.author.id == state.me.id ? nil : state.following.contains(post.author.id),
                        onFollow: { model.toggleFollow(post.author.id) },
                        onAuthor: { model.openPerson(post.author.id) },
                        onComments: { model.openComments(post.id) },
                        onOpenMedia: model.openMedia,
                        menu: ownMenu(post)
                    )
                }
            }
            .padding(.top, 12)
            .padding(.bottom, tabBarSpace)
        }
        .scrollDismissesKeyboard(.interactively)
        .shareSheet($sharing)
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

/// Карточка записи. following: nil — это своя запись (кнопки подписки
/// нет), true / false — подписан ли человек на автора.
struct PostCard: View {
    let post: Post
    let onLike: () -> Void
    var following: Bool? = nil
    var onFollow: () -> Void = {}
    /// Нажатие на аватар или имя — страница автора; nil — не нажимается.
    var onAuthor: (() -> Void)? = nil
    /// Открыть обсуждение; nil — значок комментариев не нажимается.
    var onComments: (() -> Void)? = nil
    /// Фото или видео записи на весь экран.
    var onOpenMedia: (Attachment) -> Void = { _ in }
    /// Закреплена в группе — метка над текстом.
    var pinned = false
    /// Пункты меню «⋯» (закрепить, удалить…); пусто — меню нет.
    var menu: [GlassAction] = []

    @State private var showMenu = false

    var body: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    author
                    Spacer(minLength: 0)
                    if let following {
                        if following {
                            GhostButton(title: t("Вы подписаны"), action: onFollow)
                        } else {
                            GlowButton(title: t("Подписаться"), action: onFollow)
                        }
                    }
                    if !menu.isEmpty {
                        Button { showMenu = true } label: {
                            Glyph(kind: .more, color: Palette.mist, size: 20)
                                .frame(width: 36, height: 36)
                                .contentShape(Circle())
                        }
                        .buttonStyle(.plain)
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
                if !post.text.isEmpty {
                    Text(post.text)
                        .textStyle(.body)
                        .fixedSize(horizontal: false, vertical: true)
                }
                PostMediaView(media: post.media.filter { $0.kind != .document && !$0.sticker }, onOpen: onOpenMedia)
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
                }
            }
            .padding(16)
        }
        .cardPadding()
        .glassActions($showMenu, cancel: true, actions: menu)
    }

    @ViewBuilder
    private var author: some View {
        let row = HStack(spacing: 12) {
            Avatar(user: post.author, size: 40)
            VStack(alignment: .leading, spacing: 2) {
                Text(post.author.name).textStyle(.title).lineLimit(1)
                Text("@\(post.author.handle) · \(agoTitle(minutesSince(post.createdAt, post.minutesAgo)))")
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
