import PhotosUI
import UniformTypeIdentifiers
import SwiftUI
import UIKit

/// Зелёный «близких друзей» — кольцо истории и галочка в списке.
private let closeGreen = RGB(0x3FCF8E)
private let closeTeal = RGB(0x1F7A6E)

/// Кольцо вокруг аватара: новые — сиренево-синее, «для близких» — зелёное,
/// просмотренные — тонкое серое. То же — ringBrush в StoryViews.kt.
private func storyRing(seen: Bool, close: Bool) -> AngularGradient {
    if seen {
        let grey = Palette.mistRGB.opacity(0.5)
        return AngularGradient(colors: [grey, grey], center: .center)
    }
    if close {
        return AngularGradient(colors: [closeGreen.color, closeTeal.color, closeGreen.color], center: .center)
    }
    return AngularGradient(colors: [Palette.lilac, Palette.sky, Palette.orchid, Palette.lilac], center: .center)
}

/// Сама история: фото, видео, демо-картинка или градиент — и текст поверх.
/// То же — StoryArt в StoryViews.kt.
struct StoryArt: View {
    let story: Story
    var playVideo: Bool = false
    /// Видео играет (не на паузе).
    var playing: Bool = true
    var textSize: CGFloat = 26

    var body: some View {
        ZStack {
            Rectangle().fill(storyGradient(story.bg))
            if let media = story.media {
                if media.kind == .video && playVideo {
                    PrismVideo(url: MediaFiles.url(media), playing: playing)
                        .id(story.id)
                } else {
                    Color.clear.overlay { MediaThumbnail(attachment: media, maxPixel: 1600, placeholder: false) }.clipped()
                }
            } else if story.seed != 0 {
                Nebula(seed: story.seed)
            }
            if !story.text.isEmpty {
                Text(story.text)
                    .font(.custom(Fonts.interMedium, size: textSize))
                    .foregroundStyle(Palette.onMedia)
                    .multilineTextAlignment(.center)
                    .shadow(color: .black.opacity(0.35), radius: 4)
                    .padding(.horizontal, textSize > 12 ? 24 : 4)
            }
        }
        .clipped()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Stories.preview(story))
    }
}

/// Маленькая обложка истории — для архива и «Актуального».
struct StoryThumb: View {
    let story: Story
    var size: CGFloat = 54

    var body: some View {
        StoryArt(story: story, textSize: 9)
            .frame(width: size, height: size)
            .clipShape(Circle())
    }
}

/// Ряд историй над записями друзей: своя (с «+»), эфир, эфиры друзей и
/// кружки историй друзей — непросмотренные первыми. То же — StoriesRow в StoryViews.kt.
struct StoriesRow: View {
    let model: AppModel

    var body: some View {
        let state = model.state
        let now = Date()
        let mine = state.myActiveStories(now)
        let circles = state.storyCircles(now)
        let seenAll = mine.allSatisfy { state.seenStories.contains($0.id) }
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(alignment: .top, spacing: 12) {
                RingItem(
                    user: state.me, title: t("Ваша история"),
                    ring: mine.isEmpty ? nil : storyRing(seen: seenAll, close: false),
                    description: mine.isEmpty ? t("Новая история") : t("Ваша история"),
                    plus: true,
                    onTap: { if mine.isEmpty { model.openStoryComposer() } else { model.openStories(state.me.id) } },
                    onLongPress: { model.openStoryComposer() },
                    longPressName: t("Новая история")
                )
                LiveStartCircle(model: model)
                ForEach(state.visibleLives) { live in
                    LiveCircle(model: model, live: live)
                }
                ForEach(circles) { c in
                    RingItem(
                        user: c.user, title: c.user.firstName,
                        ring: storyRing(seen: c.seen, close: c.close),
                        description: tf("История: %s", c.user.name) + (c.seen ? "" : ", " + t("новая")),
                        onTap: { model.openStories(c.user.id) },
                        onLongPress: { model.openPerson(c.user.id) },
                        longPressName: c.user.name
                    )
                }
            }
            .padding(.horizontal, 16)
        }
    }
}

/// Кружок ряда историй: аватар в кольце, «+» у своего, подпись.
private struct RingItem: View {
    let user: User
    let title: String
    let ring: AngularGradient?
    let description: String
    var plus: Bool = false
    let onTap: () -> Void
    let onLongPress: () -> Void
    /// Как VoiceOver назовёт долгое нажатие.
    var longPressName: String = ""
    @State private var pressCount = 0

    var body: some View {
        VStack(spacing: 4) {
            ZStack(alignment: .bottomTrailing) {
                Avatar(user: user, size: 54)
                    .padding(4)
                    .overlay {
                        if let ring { Circle().strokeBorder(ring, lineWidth: 2) }
                    }
                if plus {
                    Text("+")
                        .font(.custom(Fonts.interSemiBold, size: 14))
                        .foregroundStyle(Palette.ink)
                        .frame(width: 20, height: 20)
                        .background(Circle().fill(LinearGradient(colors: [Palette.orchid, Palette.ocean], startPoint: .topLeading, endPoint: .bottomTrailing)))
                }
            }
            Text(title)
                .textStyle(.labelLarge)
                .lineLimit(1)
                .truncationMode(.tail)
        }
        .frame(width: 72)
        .padding(.vertical, 4)
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
        .onLongPressGesture(minimumDuration: 0.4) {
            pressCount += 1
            onLongPress()
        }
        .sensoryFeedback(.impact(weight: .medium), trigger: pressCount)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(description)
        .accessibilityAddTraits(.isButton)
        .accessibilityAction { onTap() }
        .accessibilityAction(named: longPressName.isEmpty ? description : longPressName, onLongPress)
    }
}

/// Что держит таймер истории: сменилось — таймер перезапускается.
private struct StoryTimerKey: Equatable {
    let storyId: Int64
    let running: Bool
}

/// Просмотр историй во весь экран. Касание справа — дальше, слева — назад,
/// удержание — пауза. Чужая — ответ в личку и сердечко; своя — кто
/// посмотрел, удалить, добавить в «Актуальное». То же — StoryViewerScreen в StoryViews.kt.
struct StoryViewerScreen: View {
    let model: AppModel
    @State private var holding = false
    @State private var progress: Double = 0
    @State private var progressFor: Int64 = 0
    @State private var reply = ""
    @State private var menu = false
    @State private var addingTo = false
    @State private var pressStart: Date?
    @FocusState private var replyFocused: Bool

    var body: some View {
        let state = model.state
        ZStack {
            Color.black.ignoresSafeArea()
            if let v = state.storyViewer {
                let list = model.storiesOf(v.author)
                if list.indices.contains(v.index) {
                    let story = list[v.index]
                    let mine = story.authorId == state.me.id
                    if let author = mine ? state.me : Demo.person(story.authorId) {
                        content(state: state, viewer: v, list: list, story: story, mine: mine, author: author)
                    }
                }
            }
        }
        .statusBarHidden()
    }

    @ViewBuilder
    private func content(state: UiState, viewer v: StoryViewer, list: [Story], story: Story, mine: Bool, author: User) -> some View {
        let typing = !reply.isEmpty || replyFocused
        let running = !(holding || typing || menu || addingTo || state.storyViewersOf != nil)
        // 5 секунд на историю (видео — 15); пауза — при удержании, наборе ответа и в меню
        let total = Double(story.media?.kind == .video ? 15_000 : Stories.showMs)
        ZStack {
            StoryArt(story: story, playVideo: true, playing: running)
                .ignoresSafeArea()
            // Зоны касания: левая треть — назад, остальное — дальше; удержание — пауза
            GeometryReader { geo in
                Color.clear
                    .contentShape(Rectangle())
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { _ in
                                if pressStart == nil { pressStart = Date() }
                                holding = true
                            }
                            .onEnded { value in
                                let held = pressStart.map { Date().timeIntervalSince($0) } ?? 0
                                pressStart = nil
                                holding = false
                                let moved = abs(value.translation.width) + abs(value.translation.height)
                                // Смахнули вниз — закрыть
                                if value.translation.height > 120 && abs(value.translation.width) < 80 {
                                    model.closeStories()
                                    return
                                }
                                guard held < 0.35 && moved < 12 else { return }
                                if replyFocused {
                                    replyFocused = false
                                    return
                                }
                                if value.location.x < geo.size.width / 3 { model.previousStory() } else { model.nextStory() }
                            }
                    )
            }
            .ignoresSafeArea()
            .accessibilityHidden(true)

            VStack(spacing: 0) {
                // Полоски: сколько историй и сколько прошло у текущей
                HStack(spacing: 4) {
                    ForEach(Array(list.enumerated()), id: \.element.id) { i, _ in
                        let f: Double = i < v.index ? 1 : i == v.index ? (progressFor == story.id ? progress : 0) : 0
                        GeometryReader { g in
                            ZStack(alignment: .leading) {
                                Capsule().fill(Color.white.opacity(0.3))
                                Capsule().fill(Color.white).frame(width: g.size.width * f)
                            }
                        }
                        .frame(height: 3)
                    }
                }
                .padding(8)
                .accessibilityHidden(true)

                header(state: state, viewer: v, story: story, mine: mine, author: author)

                Spacer(minLength: 0)
                if mine {
                    Button { model.openStoryViewers(story.id) } label: {
                        HStack(spacing: 6) {
                            Glyph(kind: .eye, color: Palette.onMedia, size: 18)
                            Text(tf("Просмотры: %s", story.views.count) + (story.likes > 0 ? " · ♥ \(story.likes)" : ""))
                                .textStyle(.labelLarge, Palette.onMedia)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(Capsule().fill(Color.black.opacity(0.4)))
                    }
                    .buttonStyle(.plain)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16)
                } else {
                    replyRow(state: state, story: story, author: author)
                }
            }
        }
        .task(id: StoryTimerKey(storyId: story.id, running: running)) {
            if progressFor != story.id {
                progressFor = story.id
                progress = 0
            }
            guard running else { return }
            while progress < 1 {
                try? await Task.sleep(nanoseconds: 50_000_000)
                if Task.isCancelled { return }
                progress = min(1, progress + 50 / total)
            }
            model.nextStory()
        }
        .glassActions($menu, cancel: true, actions: [
            GlassAction(icon: .plus, title: t("В «Актуальное»")) { addingTo = true },
            GlassAction(icon: .trash, title: t("Удалить историю"), destructive: true) {
                if let media = story.media { MediaFiles.delete(media) }
                model.deleteStory(story.id)
            },
        ])
        .sheet(isPresented: viewersShown) {
            if let id = model.state.storyViewersOf, let shown = model.state.story(id) {
                StoryViewersSheet(model: model, story: shown)
            }
        }
        .sheet(isPresented: $addingTo) {
            AddToHighlightSheet(model: model, story: story) { addingTo = false }
        }
    }

    private var viewersShown: Binding<Bool> {
        Binding(
            get: { model.state.storyViewersOf != nil },
            set: { if !$0 { model.closeStoryViewers() } }
        )
    }

    private func header(state: UiState, viewer v: StoryViewer, story: Story, mine: Bool, author: User) -> some View {
        let title = v.highlightId.flatMap { id in state.highlights.first { $0.id == id }?.title } ?? author.name
        return HStack(spacing: 10) {
            Avatar(user: author, size: 36)
            VStack(alignment: .leading, spacing: 1) {
                Text(title).textStyle(.title, Palette.onMedia).lineLimit(1)
                Text(agoTitle(minutesSince(story.createdAt, 0)) + (story.closeFriends ? " · " + t("для близких") : ""))
                    .textStyle(.bodySmall, Palette.onMediaRGB.opacity(0.75))
            }
            .accessibilityElement(children: .combine)
            Spacer(minLength: 4)
            if mine {
                Button { menu = true } label: {
                    Glyph(kind: .more, color: Palette.onMedia)
                        .frame(width: 40, height: 40)
                        .contentShape(Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(LineIcon.more.label)
            }
            Button { model.closeStories() } label: {
                Glyph(kind: .close, color: Palette.onMedia)
                    .frame(width: 40, height: 40)
                    .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(LineIcon.close.label)
        }
        .padding(.horizontal, 12)
    }

    private func replyRow(state: UiState, story: Story, author: User) -> some View {
        let liked = story.views.contains { $0.userId == state.me.id && $0.liked }
        let empty = reply.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        return HStack(spacing: 8) {
            GlassField(placeholder: tf("Ответить %s…", author.firstName), text: $reply, limit: 1000, singleLine: true)
                .focused($replyFocused)
                .submitLabel(.send)
                .onSubmit { send(story) }
                .background(Capsule().fill(Color.black.opacity(0.35)))
                .overlay(Capsule().stroke(Color.white.opacity(0.4), lineWidth: 1))
            if !empty {
                Button { send(story) } label: {
                    Glyph(kind: .send, color: Palette.onMedia)
                        .frame(width: 44, height: 44)
                        .contentShape(Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(LineIcon.send.label)
            } else {
                Button { model.likeStory(story.id) } label: {
                    Glyph(kind: liked ? .heartFilled : .heart, color: liked ? Palette.pearlPink : Palette.onMedia)
                        .frame(width: 44, height: 44)
                        .contentShape(Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(t("Сердечко"))
                .accessibilityAddTraits(liked ? .isSelected : [])
            }
        }
        .padding(12)
    }

    private func send(_ story: Story) {
        guard !reply.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        model.replyToStory(story.id, reply)
        reply = ""
        replyFocused = false
    }
}

/// Кто посмотрел мою историю — новые сверху, с сердечком у тех, кому понравилось.
private struct StoryViewersSheet: View {
    let model: AppModel
    let story: Story

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(tf("Просмотры: %s", story.views.count).uppercased())
                .textStyle(.label, Palette.mist)
                .padding(.horizontal, 20)
                .padding(.vertical, 4)
                .accessibilityAddTraits(.isHeader)
            if story.views.isEmpty {
                Text(t("Пока никто не посмотрел."))
                    .textStyle(.bodySmall, Palette.mist)
                    .padding(20)
            }
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(story.views.stableSorted { $0.at > $1.at }, id: \.userId) { view in
                        if let person = Demo.person(view.userId) {
                            Button {
                                model.closeStories()
                                model.openPerson(person.id)
                            } label: {
                                HStack(spacing: 12) {
                                    Avatar(user: person, size: 40)
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(person.name).textStyle(.title).lineLimit(1)
                                        Text(agoTitle(minutesSince(view.at, 0))).textStyle(.bodySmall, Palette.mist)
                                    }
                                    Spacer(minLength: 0)
                                    if view.liked {
                                        Glyph(kind: .heartFilled, color: Palette.pearlPink, size: 20)
                                            .accessibilityLabel(t("Сердечко"))
                                    }
                                }
                                .padding(.horizontal, 20)
                                .padding(.vertical, 8)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
        .padding(.top, 20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.night.ignoresSafeArea())
        .presentationDetents([.medium, .large])
        .presentationBackground(Palette.night)
        .presentationCornerRadius(28)
    }
}

/// Новая история: фото или видео из галереи или с камеры либо текст на
/// градиенте; кому видна — всем друзьям или «Близким друзьям».
/// То же — StoryComposerScreen в StoryViews.kt.
struct StoryComposerScreen: View {
    let model: AppModel
    @State private var media: Attachment?
    @State private var text = ""
    @State private var bg = 0
    @State private var close = false
    @State private var error: String?
    @State private var editingClose = false
    @State private var showGallery = false
    @State private var galleryItem: PhotosPickerItem?
    @State private var showCamera = false
    @State private var loading = false

    var body: some View {
        let draft = Story(id: 0, authorId: model.state.me.id, createdAt: Date(), media: media, text: text, bg: bg)
        ZStack {
            Color.black.ignoresSafeArea()
            StoryArt(story: draft)
                .ignoresSafeArea()
                .onTapGesture { hideKeyboard() }
            VStack(spacing: 10) {
                HStack(spacing: 8) {
                    roundButton(.close, label: LineIcon.close.label) { cancel() }
                    Spacer()
                    Text(t("Новая история"))
                        .textStyle(.title, Palette.onMedia)
                        .accessibilityAddTraits(.isHeader)
                    Spacer()
                    if loading {
                        ProgressView().tint(Palette.onMedia).frame(width: 44, height: 44)
                    } else {
                        roundButton(.image, label: t("Фото или видео")) { showGallery = true }
                    }
                    if CameraPicker.isAvailable {
                        roundButton(.camera, label: LineIcon.camera.label) { showCamera = true }
                    }
                }
                Spacer(minLength: 0)
                if media == nil {
                    HStack(spacing: 10) {
                        ForEach(Stories.backgrounds.indices, id: \.self) { i in
                            Button { bg = i } label: {
                                Circle()
                                    .fill(storyGradient(i))
                                    .frame(width: 30, height: 30)
                                    .overlay(Circle().stroke(Color.white, lineWidth: i == bg ? 2 : 0))
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(tf("Фон %s", i + 1))
                            .accessibilityAddTraits(i == bg ? .isSelected : [])
                        }
                        Spacer(minLength: 0)
                    }
                }
                GlassField(placeholder: media == nil ? t("Напишите что-нибудь") : t("Подпись"), text: $text, limit: Stories.maxText, maxLines: 4)
                    .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Color.black.opacity(0.35)))
                FlowLayout(spacing: 8) {
                    Chip(text: t("Все друзья"), active: !close) { close = false }
                    Chip(text: tf("Близкие друзья · %s", model.state.closeFriends.count), active: close) { close = true }
                    Chip(text: t("Изменить список"), active: false) { editingClose = true }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if let error {
                    Text(error)
                        .textStyle(.bodySmall, Palette.pearlPink)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                GlowButton(title: t("Опубликовать"), fullWidth: true) {
                    error = model.publishStory(media, text, bg: bg, closeFriends: close)
                    if error == nil { media = nil }
                }
            }
            .padding(12)
        }
        .onChange(of: text) { _, _ in error = nil }
        .photosPicker(isPresented: $showGallery, selection: $galleryItem, matching: .any(of: [.images, .videos]))
        .onChange(of: galleryItem) { _, item in
            guard let item else { return }
            galleryItem = nil
            load(item)
        }
        .fullScreenCover(isPresented: $showCamera) {
            CameraPicker { attachment in
                showCamera = false
                if let attachment { use(attachment) }
            }
            .ignoresSafeArea()
        }
        .sheet(isPresented: $editingClose) {
            CloseFriendsSheet(model: model) { editingClose = false }
        }
    }

    private func roundButton(_ icon: LineIcon, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Glyph(kind: icon, color: Palette.onMedia)
                .frame(width: 44, height: 44)
                .background(Circle().fill(Color.black.opacity(0.35)))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    /// Передумали: снятое или выбранное для истории — удаляем.
    private func cancel() {
        if let media { MediaFiles.delete(media) }
        model.closeStoryComposer()
    }

    /// В историю — одно фото или видео; прежнее удаляем.
    private func use(_ attachment: Attachment) {
        if let media { MediaFiles.delete(media) }
        if attachment.kind == .document {
            MediaFiles.delete(attachment)
            media = nil
        } else {
            media = attachment
        }
        error = nil
    }

    private func load(_ item: PhotosPickerItem) {
        loading = true
        Task {
            var picked: Attachment?
            let isVideo = item.supportedContentTypes.contains { $0.conforms(to: .movie) }
            if isVideo {
                if let movie = try? await item.loadTransferable(type: PickedMovie.self) {
                    picked = MediaFiles.importFile(at: movie.url, kind: .video)
                    try? FileManager.default.removeItem(at: movie.url)
                }
            } else if let data = try? await item.loadTransferable(type: Data.self) {
                let ext = item.supportedContentTypes.first?.preferredFilenameExtension ?? "jpg"
                picked = MediaFiles.save(data, kind: .photo, ext: ext)
            }
            loading = false
            if let picked { use(picked) }
        }
    }

    private func hideKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }
}

/// «Близкие друзья»: кому видны истории «для близких». Список знаете только вы.
/// То же — CloseFriendsDialog в StoryViews.kt.
struct CloseFriendsSheet: View {
    let model: AppModel
    let onDismiss: () -> Void

    var body: some View {
        let state = model.state
        VStack(alignment: .leading, spacing: 10) {
            Text(t("Близкие друзья"))
                .textStyle(.headline)
                .accessibilityAddTraits(.isHeader)
            Text(t("Им видны истории «для близких». Никто не узнает, что он в списке или нет."))
                .textStyle(.bodySmall, Palette.mist)
                .fixedSize(horizontal: false, vertical: true)
            if state.friends.isEmpty {
                Text(t("Сначала добавьте друзей.")).textStyle(.bodySmall, Palette.mist)
            }
            ScrollView {
                VStack(spacing: 4) {
                    ForEach(state.friends) { f in
                        let on = state.closeFriends.contains(f.id)
                        Button { model.toggleCloseFriend(f.id) } label: {
                            HStack(spacing: 10) {
                                Avatar(user: f, size: 36)
                                Text(f.name).textStyle(.body)
                                Spacer(minLength: 0)
                                if on { Glyph(kind: .check, color: closeGreen.color, size: 22) }
                            }
                            .padding(.vertical, 8)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(f.name + ", " + (on ? t("в списке") : t("не в списке")))
                        .accessibilityAddTraits(on ? [.isButton, .isSelected] : .isButton)
                    }
                }
            }
            HStack {
                Spacer()
                GlowButton(title: t("Готово"), action: onDismiss)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.night.ignoresSafeArea())
        .presentationDetents([.medium, .large])
        .presentationBackground(Palette.night)
        .presentationCornerRadius(28)
    }
}

/// «Актуальное» в профиле: подборки историй кружками. У себя — «Новое»
/// первым, долгое нажатие на подборку — переименовать или удалить.
/// То же — HighlightsRow в StoryViews.kt.
struct HighlightsRow: View {
    let model: AppModel
    let ownerId: Int
    @State private var creating = false
    @State private var editing: Highlight?

    var body: some View {
        let state = model.state
        let mine = ownerId == state.me.id
        let list = state.highlightsOf(ownerId)
        if mine || !list.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 14) {
                    if mine {
                        Button { creating = true } label: {
                            VStack(spacing: 4) {
                                Glyph(kind: .plus, color: Palette.lilac, size: 24)
                                    .frame(width: 58, height: 58)
                                    .glass(Circle())
                                Text(t("Новое")).textStyle(.labelLarge).lineLimit(1)
                            }
                            .frame(width: 68)
                            .padding(.vertical, 4)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(t("Новое «Актуальное»"))
                        .sheet(isPresented: $creating) {
                            HighlightEditor(model: model, existing: nil) { creating = false }
                        }
                    }
                    ForEach(list) { h in
                        let cover = state.story(h.coverId) ?? h.storyIds.lazy.compactMap { state.story($0) }.first
                        VStack(spacing: 4) {
                            Group {
                                if let cover {
                                    StoryThumb(story: cover, size: 54)
                                } else {
                                    Circle().fill(Color.clear).frame(width: 54, height: 54).glass(Circle())
                                }
                            }
                            .padding(3)
                            .overlay(Circle().stroke(Palette.line, lineWidth: 1.5))
                            Text(h.title).textStyle(.labelLarge).lineLimit(1)
                        }
                        .frame(width: 68)
                        .padding(.vertical, 4)
                        .contentShape(Rectangle())
                        .onTapGesture { model.openHighlight(h.id) }
                        .onLongPressGesture(minimumDuration: 0.4) { if mine { editing = h } }
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(tf("Актуальное: %s", h.title))
                        .accessibilityAddTraits(.isButton)
                        .accessibilityAction { model.openHighlight(h.id) }
                        .accessibilityAction(named: t("Изменить «Актуальное»")) { if mine { editing = h } }
                    }
                }
                .padding(.horizontal, 16)
            }
            .sheet(item: $editing) { h in
                HighlightEditor(model: model, existing: h) { editing = nil }
            }
        }
    }
}

/// Новая подборка (или правка своей): название и истории из архива.
private struct HighlightEditor: View {
    let model: AppModel
    let existing: Highlight?
    let onDismiss: () -> Void
    @State private var title: String
    @State private var picked: [Int64]

    init(model: AppModel, existing: Highlight?, onDismiss: @escaping () -> Void) {
        self.model = model
        self.existing = existing
        self.onDismiss = onDismiss
        _title = State(initialValue: existing?.title ?? "")
        _picked = State(initialValue: existing?.storyIds ?? [])
    }

    var body: some View {
        let archive = model.state.storyArchive
        VStack(alignment: .leading, spacing: 12) {
            Text(existing == nil ? t("Новое «Актуальное»") : t("Изменить «Актуальное»"))
                .textStyle(.headline)
                .accessibilityAddTraits(.isHeader)
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    GlassField(placeholder: t("Название"), text: $title, limit: Stories.maxTitle, singleLine: true)
                        .glass(radius: 16)
                    if archive.isEmpty {
                        Text(t("Архив пуст: выложите историю — через сутки она останется здесь."))
                            .textStyle(.bodySmall, Palette.mist)
                            .fixedSize(horizontal: false, vertical: true)
                    } else {
                        Text(t("Выберите истории из архива")).textStyle(.bodySmall, Palette.mist)
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 64, maximum: 80), spacing: 8)], spacing: 8) {
                            ForEach(archive) { st in
                                let on = picked.contains(st.id)
                                Button {
                                    if on { picked.removeAll { $0 == st.id } } else { picked.append(st.id) }
                                } label: {
                                    StoryArt(story: st, textSize: 8)
                                        .frame(width: 64, height: 64)
                                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                .stroke(Palette.lilac, lineWidth: on ? 2 : 0)
                                        )
                                        .overlay(alignment: .topTrailing) {
                                            if on {
                                                Glyph(kind: .check, color: Palette.night, size: 14)
                                                    .frame(width: 18, height: 18)
                                                    .background(Circle().fill(Palette.lilac))
                                                    .padding(4)
                                            }
                                        }
                                }
                                .buttonStyle(.plain)
                                .accessibilityElement(children: .ignore)
                                .accessibilityLabel(Stories.preview(st) + ", " + (on ? t("выбрано") : t("не выбрано")))
                                .accessibilityAddTraits(on ? [.isButton, .isSelected] : .isButton)
                            }
                        }
                    }
                    if let existing {
                        Button {
                            model.deleteHighlight(existing.id)
                            onDismiss()
                        } label: {
                            Text(t("Удалить «Актуальное»")).textStyle(.bodySmall, Palette.pearlPink)
                                .padding(.vertical, 6)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            HStack(spacing: 10) {
                GhostButton(title: t("Отмена"), action: onDismiss)
                Spacer()
                GlowButton(title: t("Сохранить"), enabled: !picked.isEmpty) { save() }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.night.ignoresSafeArea())
        .presentationDetents([.large])
        .presentationBackground(Palette.night)
        .presentationCornerRadius(28)
    }

    private func save() {
        if let existing {
            model.renameHighlight(existing.id, title)
            picked.filter { !existing.storyIds.contains($0) }.forEach { model.addToHighlight(existing.id, $0) }
            existing.storyIds.filter { !picked.contains($0) }.forEach { model.removeFromHighlight(existing.id, $0) }
        } else {
            _ = model.createHighlight(title, picked)
        }
        onDismiss()
    }
}

/// Добавить историю в подборку — в существующую или новую.
private struct AddToHighlightSheet: View {
    let model: AppModel
    let story: Story
    let onDismiss: () -> Void
    @State private var title = ""

    var body: some View {
        let mine = model.state.highlightsOf(model.state.me.id)
        let ready = !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        VStack(alignment: .leading, spacing: 10) {
            Text(t("В «Актуальное»"))
                .textStyle(.headline)
                .accessibilityAddTraits(.isHeader)
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(mine) { h in
                        let inside = h.storyIds.contains(story.id)
                        Button {
                            model.addToHighlight(h.id, story.id)
                            onDismiss()
                        } label: {
                            Text((inside ? "✓ " : "") + h.title)
                                .textStyle(.body, inside ? Palette.mist : Palette.ink)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.vertical, 10)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .disabled(inside)
                    }
                }
            }
            GlassField(placeholder: t("Новая подборка"), text: $title, limit: Stories.maxTitle, singleLine: true)
                .glass(radius: 16)
            HStack(spacing: 10) {
                GhostButton(title: t("Отмена"), action: onDismiss)
                Spacer()
                GlowButton(title: t("Создать"), enabled: ready) {
                    _ = model.createHighlight(title, [story.id])
                    onDismiss()
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.night.ignoresSafeArea())
        .presentationDetents([.medium, .large])
        .presentationBackground(Palette.night)
        .presentationCornerRadius(28)
    }
}
