import SwiftUI

/// Вкладка Reels: короткие вертикальные ролики во весь экран, листаются
/// вверх-вниз. Нажатие — пауза, двойное нажатие — «нравится». Играет только
/// ролик на экране: соседние стоят, чтобы не тратить батарею.
struct ReelsScreen: View {
    let model: AppModel
    @State private var current: Int64?

    var body: some View {
        let state = model.state
        GeometryReader { geo in
            ScrollView(.vertical, showsIndicators: false) {
                LazyVStack(spacing: 0) {
                    ForEach(state.reels) { reel in
                        ReelPage(
                            reel: reel,
                            active: (current ?? state.reels.first?.id) == reel.id,
                            following: reel.author.id == state.me.id ? nil : state.following.contains(reel.author.id),
                            safeTop: geo.safeAreaInsets.top,
                            onLike: { model.toggleReelLike(reel.id) },
                            onAuthor: { model.openPerson(reel.author.id) },
                            onFollow: { model.toggleFollow(reel.author.id) }
                        )
                        // Ролик — ровно во весь экран: так листание «страницами» встаёт точно
                        .containerRelativeFrame([.horizontal, .vertical])
                        .id(reel.id)
                    }
                }
                .scrollTargetLayout()
            }
            .scrollTargetBehavior(.paging)
            .scrollPosition(id: $current)
            .ignoresSafeArea()
        }
    }
}

private struct ReelPage: View {
    let reel: Reel
    let active: Bool
    let following: Bool?
    let safeTop: CGFloat
    let onLike: () -> Void
    let onAuthor: () -> Void
    let onFollow: () -> Void

    @State private var paused = false
    /// Сколько ролик уже проиграл до текущего запуска, мс.
    @State private var elapsed: Double = 0
    /// Когда ролик запущен; nil — стоит.
    @State private var startedAt: Date?

    private var playing: Bool { active && !paused }

    private func timeMs(_ now: Date) -> Double {
        let running = startedAt.map { now.timeIntervalSince($0) * 1000 } ?? 0
        return (elapsed + running).truncatingRemainder(dividingBy: reel.durationMs)
    }

    var body: some View {
        let spec = ReelSpec(seed: reel.seed)
        TimelineView(.animation(minimumInterval: nil, paused: !playing)) { timeline in
            let t = timeMs(timeline.date)
            ZStack {
                ReelArt(spec: spec, timeMs: t)

                // Затемнение снизу, чтобы подпись читалась на любом кадре
                LinearGradient(
                    stops: [
                        .init(color: Palette.voidRGB.opacity(0), location: 0.55),
                        .init(color: Palette.voidRGB.opacity(0.85), location: 1),
                    ],
                    startPoint: .top, endPoint: .bottom
                )

                VStack(alignment: .leading, spacing: 10) {
                    progress(t)
                    Text("REELS").textStyle(.label)
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
                    Glyph(kind: .reels, color: Palette.ink, size: 36)
                        .frame(width: 72, height: 72)
                        .background(Circle().fill(Palette.voidRGB.opacity(0.5)))
                        .allowsHitTesting(false)
                }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture(count: 2) { if !reel.liked { onLike() } }
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
                    .frame(width: bar.size.width * CGFloat(t / reel.durationMs))
            }
        }
        .frame(height: 2)
    }

    private var details: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                Button(action: onAuthor) {
                    HStack(spacing: 10) {
                        Avatar(user: reel.author, size: 36)
                        Text(reel.author.name).textStyle(.title).lineLimit(1)
                    }
                }
                .buttonStyle(.plain)
                if let following {
                    if following {
                        GhostButton(title: "Вы подписаны", action: onFollow)
                    } else {
                        GlowButton(title: "Подписаться", action: onFollow)
                    }
                }
            }
            Text(reel.caption).textStyle(.body).fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 6) {
                Glyph(kind: .music, color: Palette.lilac, size: 16)
                Text(reel.music).textStyle(.bodySmall, Palette.mist).lineLimit(1)
            }
        }
    }

    private var sideButtons: some View {
        VStack(spacing: 18) {
            Button(action: onLike) {
                VStack(spacing: 2) {
                    Glyph(kind: reel.liked ? .heartFilled : .heart, color: reel.liked ? Palette.pearlPink : Palette.ink, size: 30)
                    Text(compact(reel.likes)).textStyle(.label)
                }
                .padding(4)
            }
            .buttonStyle(.plain)
            .sensoryFeedback(.impact(weight: .light), trigger: reel.liked)
            VStack(spacing: 2) {
                Glyph(kind: .comment, color: Palette.ink, size: 30)
                Text(compact(reel.comments)).textStyle(.label)
            }
            .padding(4)
        }
    }
}
