import SwiftUI

/// Вкладка PRISM: короткие вертикальные ролики во весь экран, листаются
/// вверх-вниз. Нажатие — пауза, двойное нажатие — «нравится». Справа —
/// «нравится», обсуждение, «поделиться» и «сохранить». Играет только
/// ролик на экране: соседние стоят, чтобы не тратить батарею.
struct PrismScreen: View {
    let model: AppModel
    @State private var current: Int64?
    @State private var confirmDelete: Prism?
    /// Вкладки сверху: ролики или медиатека.
    @State private var library = false

    var body: some View {
        let state = model.state
        // Ролики тех, кто в чёрном списке, не показываются
        let prisms = state.prisms.filter { !state.isBlocked($0.author.id) && state.canSee($0.author) }
        GeometryReader { geo in
            ZStack(alignment: .topLeading) {
            if library {
                MediaLibraryScreen(model: model, safeTop: geo.safeAreaInsets.top)
                    .ignoresSafeArea(edges: .top)
            } else {
            ScrollView(.vertical, showsIndicators: false) {
                LazyVStack(spacing: 0) {
                    ForEach(prisms) { prism in
                        PrismPage(
                            prism: prism,
                            active: (current ?? prisms.first?.id) == prism.id,
                            friend: prism.author.id == state.me.id ? nil : state.friendState(prism.author.id),
                            safeTop: geo.safeAreaInsets.top,
                            onLike: { model.togglePrismLike(prism.id) },
                            onAuthor: { model.openPerson(prism.author.id) },
                            onFriend: { model.toggleFriend(prism.author.id) },
                            onComments: { model.openPrismComments(prism.id) },
                            onSave: { model.togglePrismSave(prism.id) },
                            mine: prism.author.id == state.me.id && prism.video != nil,
                            onDelete: { confirmDelete = prism },
                            onReport: prism.author.id == state.me.id ? nil : { model.reportPrism(prism) }
                        )
                        // Ролик — ровно во весь экран: так листание «страницами» встаёт точно
                        .containerRelativeFrame([.horizontal, .vertical])
                        .id(prism.id)
                    }
                }
                .scrollTargetLayout()
            }
            .scrollTargetBehavior(.paging)
            .scrollPosition(id: $current)
            .ignoresSafeArea()
            // Открыли из «Сохранённого» — сразу на нужный ролик
            .onAppear { jumpToStart() }
            .onChange(of: state.prismStartId) { _, _ in jumpToStart() }
            // «+» — снять или выбрать свой ролик
            .overlay(alignment: .topTrailing) {
                RoundGlyphButton(icon: .plus, iconSize: 22, strong: true) { model.openPrismCreator() }
                    .padding(.top, geo.safeAreaInsets.top + 8)
                    .padding(.trailing, 12)
            }
            }
            // «PRISM · МЕДИАТЕКА» — вкладки поверх роликов
            HStack(spacing: 4) {
                tabLabel("PRISM", active: !library) { library = false }
                tabLabel(t("Медиатека").uppercased(), active: library) { library = true }
            }
            .padding(.top, geo.safeAreaInsets.top + 12)
            .padding(.leading, 8)
            }
            .ignoresSafeArea(edges: .top)
        }
        .glassActions(
            item: $confirmDelete,
            title: { _ in t("Удалить ролик?") },
            message: { _ in t("Ролик, его обложка и комментарии удалятся. Отменить будет нельзя.") },
            cancel: true
        ) { prism in
            [GlassAction(icon: .trash, title: t("Удалить"), destructive: true) {
                model.deletePrism(prism.id)
                if let video = prism.video { MediaFiles.delete(video) }
                if let cover = prism.cover { MediaFiles.delete(cover) }
            }]
        }
    }

    /// Вкладка сверху: подпись капслоком и светящаяся черта под выбранной.
    private func tabLabel(_ text: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Text(text).textStyle(.label, active ? Palette.ink : Palette.mist)
                Capsule()
                    .fill(LinearGradient(colors: [Palette.lilac, Palette.sky], startPoint: .leading, endPoint: .trailing))
                    .frame(width: active ? 24 : 0, height: 2)
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 6)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .animation(.easeOut(duration: 0.2), value: active)
        .accessibilityAddTraits(active ? .isSelected : [])
    }

    private func jumpToStart() {
        guard let id = model.state.prismStartId else { return }
        current = id
        model.consumePrismStart()
    }
}

/// Текст для «Поделиться»: подпись, автор и где это смотреть.
func shareText(_ prism: Prism) -> String {
    tf("%s\n— %s (@%s) в Memory · PRISM", prism.caption, prism.author.name, prism.author.handle)
}

private struct PrismPage: View {
    let prism: Prism
    let active: Bool
    let friend: FriendState?
    let safeTop: CGFloat
    let onLike: () -> Void
    let onAuthor: () -> Void
    let onFriend: () -> Void
    let onComments: () -> Void
    let onSave: () -> Void
    let mine: Bool
    let onDelete: () -> Void
    /// Пожаловаться на чужой PRISM; nil — свой, кнопки нет.
    var onReport: (() -> Void)? = nil

    @State private var paused = false
    /// Ход своего видео 0…1.
    @State private var videoProgress: Double = 0
    /// Сколько ролик уже проиграл до текущего запуска, мс.
    @State private var elapsed: Double = 0
    /// Когда ролик запущен; nil — стоит.
    @State private var startedAt: Date?

    private var playing: Bool { active && !paused }

    private func timeMs(_ now: Date) -> Double {
        let running = startedAt.map { now.timeIntervalSince($0) * 1000 } ?? 0
        return (elapsed + running).truncatingRemainder(dividingBy: prism.durationMs)
    }

    var body: some View {
        let spec = PrismSpec(seed: prism.seed)
        // Своё видео ведёт время само — часы для рисованного кадра не нужны
        TimelineView(.animation(minimumInterval: nil, paused: !playing || prism.video != nil)) { timeline in
            let ms = timeMs(timeline.date)
            ZStack {
                if let video = prism.video {
                    PrismVideo(url: MediaFiles.url(video), playing: playing) { videoProgress = $0 }
                } else {
                    PrismArt(spec: spec, timeMs: ms)
                }

                // Затемнение снизу, чтобы подпись читалась на любом кадре
                LinearGradient(
                    stops: [
                        .init(color: Palette.voidRGB.opacity(0), location: 0.55),
                        .init(color: Palette.voidRGB.opacity(0.85), location: 1),
                    ],
                    startPoint: .top, endPoint: .bottom
                )

                VStack(alignment: .leading, spacing: 10) {
                    progress(prism.video != nil ? videoProgress * prism.durationMs : ms)
                    // Место под вкладки «PRISM · МЕДИАТЕКА» (они — поверх, в PrismScreen)
                    Color.clear.frame(height: 18)
                    Spacer()
                    HStack(alignment: .bottom, spacing: 12) {
                        details
                        Spacer(minLength: 0)
                        sideButtons
                    }
                }
                .padding(.top, safeTop + 12)
                .padding(.horizontal, 16)
                .padding(.bottom, tabBarSpace + 24)

                if paused {
                    Glyph(kind: .play, color: Palette.ink, size: 36)
                        .frame(width: 72, height: 72)
                        .background(Circle().fill(Palette.voidRGB.opacity(0.5)))
                        .allowsHitTesting(false)
                }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture(count: 2) { if !prism.liked { onLike() } }
        .onTapGesture { togglePause() }
        .onAppear { if active { play() } }
        .onChange(of: active) { _, isActive in
            // Пролистали на другой ролик — этот начнётся сначала, когда вернётесь
            if isActive {
                play()
            } else {
                elapsed = 0
                startedAt = nil
                paused = false
            }
        }
    }

    private func play() {
        if startedAt == nil && !paused { startedAt = Date() }
    }

    private func togglePause() {
        if paused {
            paused = false
            startedAt = Date()
        } else {
            if let startedAt { elapsed += Date().timeIntervalSince(startedAt) * 1000 }
            startedAt = nil
            paused = true
        }
    }

    private func progress(_ t: Double) -> some View {
        GeometryReader { bar in
            ZStack(alignment: .leading) {
                Capsule().fill(Palette.inkRGB.opacity(0.25))
                Capsule()
                    .fill(LinearGradient(colors: [Palette.lilac, Palette.sky], startPoint: .leading, endPoint: .trailing))
                    .frame(width: bar.size.width * CGFloat(t / prism.durationMs))
            }
        }
        .frame(height: 2)
    }

    private var details: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                Button(action: onAuthor) {
                    HStack(spacing: 10) {
                        Avatar(user: prism.author, size: 36)
                        Text(prism.author.name).textStyle(.title).lineLimit(1)
                    }
                }
                .buttonStyle(.plain)
                if let friend {
                    FriendButton(state: friend, name: prism.author.name, onToggle: onFriend)
                }
            }
            RichText(text: prism.caption).fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 6) {
                Glyph(kind: .music, color: Palette.lilac, size: 16)
                Text(prism.music).textStyle(.bodySmall, Palette.mist).lineLimit(1)
            }
        }
    }

    private var sideButtons: some View {
        VStack(spacing: 18) {
            Button(action: onLike) {
                VStack(spacing: 2) {
                    LikeHeart(liked: prism.liked, color: Palette.ink, size: 30)
                    Text(compact(prism.likes)).textStyle(.label).lineLimit(1).fixedSize()
                }
                .padding(4)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .sensoryFeedback(.impact(weight: .light), trigger: prism.liked)
            .accessibilityLabel(spoken(.heart, compact(prism.likes)))
            .accessibilityAddTraits(prism.liked ? .isSelected : [])
            Button(action: onComments) {
                sideLabel(.comment, compact(prism.comments))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(spoken(.comment, compact(prism.comments)))
            ShareLink(item: shareText(prism)) {
                sideLabel(.share, t("Поделиться"))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(spoken(.share, t("Поделиться")))
            Button(action: onSave) {
                sideLabel(prism.saved ? .bookmarkFilled : .bookmark, prism.saved ? t("В закладках") : t("Сохранить"))
            }
            .buttonStyle(.plain)
            .sensoryFeedback(.selection, trigger: prism.saved)
            .accessibilityLabel(prism.saved ? t("В закладках") : t("Сохранить"))
            if mine {
                Button(action: onDelete) { sideLabel(.trash, t("Удалить")) }
                    .buttonStyle(.plain)
                    .accessibilityLabel(spoken(.trash, t("Удалить")))
            }
            if let onReport {
                Button(action: onReport) { sideLabel(.flag, t("Пожаловаться")) }
                    .buttonStyle(.plain)
                    .accessibilityLabel(spoken(.flag, t("Пожаловаться")))
            }
        }
    }

    /// «Нравится, 1,2 тыс.», «Комментарии, 40», «Поделиться» — для VoiceOver.
    private func spoken(_ icon: LineIcon, _ text: String) -> String {
        icon.label == text ? text : icon.label + ", " + text
    }

    private func sideLabel(_ icon: LineIcon, _ text: String) -> some View {
        VStack(spacing: 2) {
            Glyph(kind: icon, color: Palette.ink, size: 30)
            Text(text).textStyle(.label).lineLimit(1).fixedSize()
        }
        .padding(4)
        .contentShape(Rectangle())
    }
}
