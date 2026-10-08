import SwiftUI

/// Вкладка «Лента»: поле новой записи и записи друзей.
struct FeedScreen: View {
    let model: AppModel

    var body: some View {
        let state = model.state
        ScrollView {
            LazyVStack(spacing: 14) {
                HStack(alignment: .center) {
                    ScreenTitle(title: "Лента", subtitle: "Моменты, которые остаются")
                    Spacer()
                    // Кристалл из значка приложения — знак Memory в шапке
                    Image("MemoryCrystal")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 52, height: 52)
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 8)

                Composer { text, media in model.publish(text, media: media) }

                ForEach(state.posts) { post in
                    PostCard(
                        post: post,
                        onLike: { model.toggleLike(post.id) },
                        following: post.author.id == state.me.id ? nil : state.following.contains(post.author.id),
                        onFollow: { model.toggleFollow(post.author.id) },
                        onAuthor: { model.openPerson(post.author.id) },
                        onComments: { model.openComments(post.id) },
                        onOpenMedia: model.openMedia
                    )
                }
            }
            .padding(.top, 12)
            .padding(.bottom, tabBarSpace)
        }
        .scrollDismissesKeyboard(.interactively)
    }
}

/// Новая запись: текст, фото и видео из галереи или с камеры. Вложения
/// видны плитками под полем, крестик убирает лишнее.
private struct Composer: View {
    let onPublish: (String, [Attachment]) -> Void
    @State private var text = ""
    @State private var media: [Attachment] = []

    private var canPublish: Bool {
        !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !media.isEmpty
    }

    private var hint: String {
        if !media.isEmpty { return plural(media.count, "вложение", "вложения", "вложений") }
        return text.isEmpty ? "Видят ваши подписчики" : "\(text.count) / 2000"
    }

    var body: some View {
        GlassCard {
            VStack(spacing: 0) {
                GlassField(placeholder: "Чем хочется поделиться?", text: $text, limit: 2000)
                PendingAttachments(items: media) { item in
                    media.removeAll { $0.id == item.id }
                    MediaFiles.delete(item)
                }
                .padding(.bottom, media.isEmpty ? 0 : 10)
                HStack(spacing: 10) {
                    AttachButton(documents: false, size: 40) { picked in
                        media = Array((media + picked).prefix(AppModel.maxMedia))
                    }
                    Text(hint).textStyle(.bodySmall, Palette.mist).lineLimit(1)
                    Spacer()
                    GlowButton(title: "Поделиться", enabled: canPublish) {
                        onPublish(text, media)
                        text = ""
                        media = []
                    }
                }
                .padding(.horizontal, 12)
                .padding(.bottom, 12)
            }
        }
        .cardPadding()
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

    var body: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    author
                    Spacer(minLength: 0)
                    if let following {
                        if following {
                            GhostButton(title: "Вы подписаны", action: onFollow)
                        } else {
                            GlowButton(title: "Подписаться", action: onFollow)
                        }
                    }
                }
                if !post.text.isEmpty {
                    Text(post.text)
                        .textStyle(.body)
                        .fixedSize(horizontal: false, vertical: true)
                }
                PostMediaView(media: post.media, onOpen: onOpenMedia)
                if let seed = post.artSeed {
                    Nebula(seed: seed)
                        .frame(height: 200)
                        .frame(maxWidth: .infinity)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                HStack(spacing: 14) {
                    Button(action: onLike) {
                        HStack(spacing: 6) {
                            Glyph(kind: post.liked ? .heartFilled : .heart, color: post.liked ? Palette.lilac : Palette.mist, size: 22)
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
