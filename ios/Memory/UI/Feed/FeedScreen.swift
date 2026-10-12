import SwiftUI

/// Вкладка «Лента»: поле новой записи и записи друзей.
struct FeedScreen: View {
    let model: AppModel

    /// Своя запись: закрепить в витрине профиля или удалить.
    private func ownMenu(_ post: Post) -> [GlassAction] {
        guard post.author.id == model.state.me.id else { return [] }
        let pinned = model.state.profilePins.contains(post.id)
        return [
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
    }
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
