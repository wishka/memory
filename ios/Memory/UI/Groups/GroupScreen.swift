import SwiftUI

/// Страница группы: заставка, описание, сведения и контакты, вступление,
/// записи участников и витрина. Шестерёнка на заставке — настройки (у
/// создателя — правка, заставка, оформление, удаление; у участника —
/// уведомления и выход).
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
    /// Витрина вместо записей.
    @State private var showShop = false
    /// Выбранная категория витрины; nil — все.
    @State private var category: String?
    @State private var adding = false

    private var canPost: Bool { group.joined && (!group.onlyOwnerPosts || group.mine) }

    private var posts: [Post] {
        let visible = group.posts.filter { !model.state.isBlocked($0.author.id) }
        let pinned = visible.first { $0.id == group.pinnedPostId }
        return (pinned.map { [$0] } ?? []) + visible.filter { $0.id != group.pinnedPostId }
    }

    /// Витрина видна, когда в ней есть товары или группа ваша.
    private var hasShop: Bool { !group.shop.isEmpty || group.mine }

    private var accent: GroupAccent { group.accentColor }

    /// Открытый товар этой группы — из витрины или из сообщения.
    private var openProduct: Binding<Product?> {
        Binding(
            get: {
                guard let key = model.state.openProduct, key.groupId == group.id else { return nil }
                return group.shop.first { $0.id == key.productId }
            },
            set: { value in
                if value == nil { model.closeProduct() }
            }
        )
    }

    var body: some View {
        ZStack {
            // Свой фон группы — под всей страницей
            if let backdrop = group.backdrop {
                BackdropView(backdrop: backdrop).ignoresSafeArea()
            }
            GeometryReader { geo in
                ScrollView {
                    LazyVStack(spacing: 14) {
                        cover(safeTop: geo.safeAreaInsets.top)
                        about
                        if !group.info.isEmpty {
                            GroupInfoCard(info: group.info, accent: accent).cardPadding()
                        }
                        if hasShop {
                            HStack(spacing: 8) {
                                TabPill(text: t("Записи"), active: !showShop, accent: accent) { showShop = false }
                                TabPill(text: group.shop.isEmpty ? t("Витрина") : t("Витрина") + " · \(group.shop.count)",
                                        active: showShop, accent: accent) { showShop = true }
                                Spacer(minLength: 0)
                            }
                            .padding(.horizontal, 16)
                        }
                        if showShop && hasShop {
                            shop
                        } else {
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
                                    menu: menu(post),
                                    onReport: model.isMine(post) ? nil : { model.reportPost(post) }
                                )
                            }
                        }
                    }
                    .padding(.bottom, tabBarSpace)
                }
                .ignoresSafeArea(edges: .top)
                .scrollDismissesKeyboard(.interactively)
            }
        }
        // Товар открыли из сообщения — сразу на витрину
        .onAppear { if model.state.openProduct?.groupId == group.id { showShop = true } }
        .onChange(of: model.state.openProduct) { _, key in
            if key?.groupId == group.id { showShop = true }
        }
        .fullScreenCover(item: openProduct) { product in
            ProductCover(model: model, group: group, product: product)
        }
        .fullScreenCover(isPresented: $adding) {
            ProductEditor(
                initial: nil, categories: group.shop.categories(), accent: accent,
                onSave: { model.saveProduct(group.id, $0) },
                onClose: { adding = false }
            )
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

    /// Витрина: «Добавить товар» у создателя, категории и товары по два в ряд.
    @ViewBuilder
    private var shop: some View {
        let categories = group.shop.categories()
        // Категорию убрали из витрины — фильтр сбрасывается сам
        let activeCategory = category.flatMap { categories.contains($0) ? $0 : nil }
        let goods = group.shop.filter { activeCategory == nil || $0.category.trimmingCharacters(in: .whitespacesAndNewlines) == activeCategory }
        if group.mine {
            AccentButton(title: t("Добавить товар"), accent: accent, fullWidth: true) { adding = true }
                .padding(.horizontal, 16)
        }
        CategoryChips(categories: categories, selected: activeCategory, accent: accent) { category = $0 }
        if goods.isEmpty {
            Text(group.mine
                 ? t("Витрина пуста. Добавьте товары с фото и ценой — покупатели напишут вам в чат.")
                 : t("Товаров пока нет."))
                .textStyle(.bodySmall, Palette.mist)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 24)
        }
        LazyVGrid(columns: [GridItem(.flexible(), spacing: 12, alignment: .top), GridItem(.flexible(), spacing: 12, alignment: .top)],
                  spacing: 12) {
            ForEach(goods) { product in
                ProductCard(product: product, accent: accent) { model.showProduct(group.id, product.id) }
            }
        }
        .padding(.horizontal, 16)
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
                AccentButton(title: t("Вступить"), accent: accent) { model.toggleGroup(group.id) }
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

/// Товар во весь экран со своими окнами поверх: правка товара и фото на весь экран.
private struct ProductCover: View {
    let model: AppModel
    let group: MemoryGroup
    let product: Product
    @State private var editing = false
    @State private var viewing: Attachment?
    @State private var notice: String?

    /// «Написать продавцу»: в своей группе и без известного владельца — некому.
    /// Не вышло — товар остаётся открытым, внизу подсказка.
    private var ask: (() -> Void)? {
        if group.mine || group.ownerId == 0 { return nil }
        let shopModel = model
        let groupId = group.id
        let productId = product.id
        return {
            if shopModel.askSeller(groupId, productId) == nil {
                notice = t("Не удалось написать продавцу")
            } else {
                shopModel.closeProduct()
            }
        }
    }

    /// Продавец в чёрном списке — написать ему нельзя.
    private var askBlockedReason: String? {
        !group.mine && model.state.isBlocked(group.ownerId) ? t("Вы заблокировали продавца — написать ему нельзя.") : nil
    }

    /// Переставить товар в своей витрине — когда товаров больше одного.
    private var move: ((Int) -> Void)? {
        guard group.mine && group.shop.count > 1 else { return nil }
        let shopModel = model
        let groupId = group.id
        let productId = product.id
        return { delta in shopModel.moveProduct(groupId, productId, delta) }
    }

    var body: some View {
        ProductSheet(
            product: product,
            accent: group.accentColor,
            mine: group.mine,
            onClose: { model.closeProduct() },
            onAsk: ask,
            onEdit: { editing = true },
            onDelete: {
                model.deleteProduct(group.id, product.id)
                model.closeProduct()
            },
            onOpenPhoto: { viewing = $0 },
            onMove: move,
            askBlockedReason: askBlockedReason
        )
        .toast($notice)
        .fullScreenCover(isPresented: $editing) {
            ProductEditor(
                initial: product, categories: group.shop.categories(), accent: group.accentColor,
                onSave: { model.saveProduct(group.id, $0) },
                onClose: { editing = false }
            )
        }
        .background {
            Color.clear.fullScreenCover(item: $viewing) { attachment in
                MediaViewer(attachment: attachment) { viewing = nil }
            }
        }
    }
}
