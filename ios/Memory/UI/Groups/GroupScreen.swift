import SwiftUI

/// Страница группы: заставка, описание, вступление, записи участников.
/// Шестерёнка на заставке — настройки (у создателя — правка, заставка,
/// удаление; у участника — уведомления и выход).
struct GroupScreen: View {
    let model: AppModel
    let group: MemoryGroup
    @State private var text = ""
    @State private var media: [Attachment] = []
    @State private var choosingMusic = false
    @State private var choosingSticker = false
    @State private var choosingGif = false
    @State private var editingPoll = false
    @State private var poll: PollDraft?

    private var canPost: Bool { group.joined && (!group.onlyOwnerPosts || group.mine) }

    private var posts: [Post] {
        let visible = group.posts.filter { !model.state.isBlocked($0.author.id) }
        let pinned = visible.first { $0.id == group.pinnedPostId }
        return (pinned.map { [$0] } ?? []) + visible.filter { $0.id != group.pinnedPostId }
    }

    var body: some View {
        GeometryReader { geo in
            ScrollView {
                LazyVStack(spacing: 14) {
                    cover(safeTop: geo.safeAreaInsets.top)
                    about
                    composer
                    let posts = self.posts
                    SectionTitle(text: posts.isEmpty ? t("Записей пока нет") : tf("Записи · %s", posts.count))
                    ForEach(posts) { post in
                        PostCard(
                            post: post,
                            onLike: { model.toggleGroupPostLike(group.id, post.id) },
                            onAuthor: { model.openPerson(post.author.id) },
                            onComments: { model.openComments(post.id) },
                            onOpenMedia: model.openMedia,
                            pinned: post.id == group.pinnedPostId,
                            onVote: { model.votePost(post.id, $0) },
                            onClosePoll: model.isMine(post) ? { model.closePostPoll(post.id) } : nil,
                            menu: menu(post)
                        )
                    }
                }
                .padding(.bottom, tabBarSpace)
            }
            .ignoresSafeArea(edges: .top)
            .scrollDismissesKeyboard(.interactively)
        }
        .sheet(isPresented: $choosingSticker) { picker(gifs: false) }
        .sheet(isPresented: $choosingGif) { picker(gifs: true) }
        .sheet(isPresented: $editingPoll) {
            PollEditorSheet { poll = $0 }
        }
        .sheet(isPresented: $choosingMusic) {
            MusicChooser(tracks: model.state.tracks, onPicked: { picked in
                media = Array((media + picked).prefix(AppModel.maxMedia))
            }, onClose: { choosingMusic = false })
        }
    }

    /// Стикеры и гифки — один выбор; из «GIF» он открывается сразу на гифках.
    private func picker(gifs: Bool) -> some View {
        StickerPicker(
            stickers: model.state.stickers,
            onPick: { media = Array((media + [$0]).prefix(AppModel.maxMedia)) },
            onOpenStudio: { model.openProfileSection(.stickers) },
            packs: model.state.stickerPacks,
            recentGifs: model.state.recentGifs,
            gifs: true,
            startOnGifs: gifs
        )
    }

    private func menu(_ post: Post) -> [GlassAction] {
        let mine = post.author.id == model.state.me.id
        guard group.mine || mine else { return [] }
        var items: [GlassAction] = []
        if group.mine {
            let pinned = post.id == group.pinnedPostId
            items.append(GlassAction(icon: .pushpin, title: pinned ? t("Открепить") : t("Закрепить")) {
                model.togglePinGroupPost(group.id, post.id)
            })
        }
        items.append(GlassAction(icon: .trash, title: t("Удалить запись"), destructive: true) {
            post.media.forEach(deleteAttachmentFiles)
            model.deleteGroupPost(group.id, post.id)
        })
        return items
    }

    /// Заставка во всю ширину, под строкой состояния.
    private func cover(safeTop: CGFloat) -> some View {
        ZStack(alignment: .topLeading) {
            GroupCover(group: group)
            LinearGradient(
                stops: [
                    .init(color: Palette.voidRGB.opacity(0), location: 0.4),
                    .init(color: Palette.void, location: 1),
                ],
                startPoint: .top, endPoint: .bottom
            )
            HStack {
                RoundGlyphButton(icon: .back, strong: true) { model.closeGroup() }
                Spacer()
                if group.joined {
                    RoundGlyphButton(icon: .gear, strong: true) { model.openGroupSettings() }
                }
            }
            .padding(.top, safeTop + 12)
            .padding(.horizontal, 12)
            VStack(alignment: .leading, spacing: 2) {
                Spacer()
                Text(group.name.uppercased()).textStyle(.headline)
                Text("\(t(group.topic)) · \(plural(group.members, "участник", "участника", "участников"))".uppercased())
                    .textStyle(.label, Palette.mist)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
        }
        .frame(height: 220 + safeTop)
        .clipped()
    }

    private var about: some View {
        VStack(alignment: .leading, spacing: 12) {
            if !group.description.isEmpty {
                Text(group.description).textStyle(.body).fixedSize(horizontal: false, vertical: true)
            }
            if !group.joined {
                GlowButton(title: t("Вступить")) { model.toggleGroup(group.id) }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 20)
    }

    @ViewBuilder
    private var composer: some View {
        if canPost {
            GlassCard {
                VStack(spacing: 0) {
                    GlassField(placeholder: t("Написать в группу"), text: $text, limit: 2000)
                    if let poll {
                        PendingPoll(draft: poll) { self.poll = nil }
                            .padding(.horizontal, 12)
                            .padding(.bottom, 10)
                    }
                    PendingAttachments(items: media) { item in
                        media.removeAll { $0.id == item.id }
                        deleteAttachmentFiles(item)
                    }
                    .padding(.bottom, media.isEmpty ? 0 : 10)
                    HStack(spacing: 10) {
                        AttachButton(
                            documents: true, size: 40,
                            onMusic: { choosingMusic = true },
                            onSticker: { choosingSticker = true },
                            onGif: { choosingGif = true },
                            onPoll: { editingPoll = true }
                        ) { picked in
                            media = Array((media + picked).prefix(AppModel.maxMedia))
                        }
                        Text(media.isEmpty ? t("Фото, видео, документы, музыка") : plural(media.count, "вложение", "вложения", "вложений"))
                            .textStyle(.bodySmall, Palette.mist)
                            .lineLimit(1)
                        Spacer()
                        GlowButton(title: t("Опубликовать"), enabled: !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !media.isEmpty || poll != nil) {
                            model.postInGroup(group.id, text, media: media, poll: poll)
                            text = ""
                            media = []
                            poll = nil
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.bottom, 12)
                }
            }
            .cardPadding()
        } else {
            Text(group.joined ? t("В этой группе пишет только её создатель.") : t("Вступите, чтобы писать в группу."))
                .textStyle(.bodySmall, Palette.mist)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 24)
        }
    }
}
