import SwiftUI

/// Правка своей записи: текст и вложения (лишние можно убрать). Чек-ин и
/// опрос остаются как были. После сохранения у записи пометка «изменено».
/// То же — EditPostSheet в PostExtras.kt.
struct EditPostSheet: View {
    let model: AppModel
    let post: Post
    @State private var text: String
    @State private var media: [Attachment]
    @State private var error: String?

    init(model: AppModel, post: Post) {
        self.model = model
        self.post = post
        _text = State(initialValue: post.text)
        _media = State(initialValue: post.media)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                Text(t("Изменить запись").uppercased())
                    .textStyle(.label, Palette.mist)
                    .padding(.horizontal, 20)
                    .accessibilityAddTraits(.isHeader)
                GlassField(placeholder: t("Текст записи"), text: $text, limit: 2000, maxLines: 10)
                    .glass(radius: 18)
                    .padding(.horizontal, 16)
                MentionSuggestions(text: $text)
                    .padding(.horizontal, 16)
                PendingAttachments(items: media) { item in
                    media.removeAll { $0.id == item.id }
                }
                if post.checkIn != nil || post.poll != nil {
                    Text(t("Чек-ин и опрос остаются как были."))
                        .textStyle(.bodySmall, Palette.mist)
                        .padding(.horizontal, 20)
                }
                if let error {
                    Text(error)
                        .textStyle(.bodySmall, Palette.error)
                        .padding(.horizontal, 20)
                }
                HStack(spacing: 10) {
                    GhostButton(title: t("Отмена")) { model.cancelEdit() }
                    GlowButton(title: t("Сохранить")) { save() }
                }
                .padding(.horizontal, 16)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 20)
            .padding(.bottom, 12)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Palette.night.ignoresSafeArea())
        .onChange(of: text) { _, _ in error = nil }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .presentationBackground(Palette.night)
        .presentationCornerRadius(28)
    }

    private func save() {
        let removed = post.media.filter { a in !media.contains(where: { $0.id == a.id }) }
        error = model.saveEdit(post.id, text, media)
        // Убранные фото и видео больше не нужны — их копии удаляются с телефона
        if error == nil { removed.forEach(deleteAttachmentFiles) }
    }
}

/// Запись, открытая по ссылке: отдельным экраном, с обсуждением и
/// «нравится». Назад — туда, где были.
struct PostScreen: View {
    let model: AppModel
    let post: Post

    var body: some View {
        let state = model.state
        let mine = post.author.id == state.me.id
        ScrollView {
            LazyVStack(spacing: 12) {
                HStack(spacing: 14) {
                    RoundGlyphButton(icon: .back) { model.closePost() }
                    Text(t("Запись").uppercased())
                        .textStyle(.headline)
                        .lineLimit(1)
                        .accessibilityAddTraits(.isHeader)
                    Spacer()
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 4)

                PostCard(
                    post: post,
                    onLike: { model.likeAny(post.id) },
                    friend: mine ? nil : state.friendState(post.author.id),
                    onFriend: { model.toggleFriend(post.author.id) },
                    onAuthor: {
                        model.closePost()
                        model.openPerson(post.author.id)
                    },
                    onComments: { model.openComments(post.id) },
                    onOpenMedia: model.openMedia,
                    onVote: { model.votePost(post.id, $0) },
                    onReport: mine ? nil : { model.reportPost(post) }
                )
            }
            .padding(.top, 12)
            .padding(.bottom, tabBarSpace)
        }
    }
}
