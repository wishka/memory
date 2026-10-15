import AVFoundation
import SwiftUI

/// Плеер медиатеки: поток выбранного трека.
@Observable
final class CatalogPlayer {
    private(set) var current: CatalogTrack?
    private(set) var playing = false
    private(set) var progress: Double = 0
    @ObservationIgnored private let player = AVPlayer()
    @ObservationIgnored private var timeObserver: Any?
    @ObservationIgnored private var endObserver: NSObjectProtocol?

    init() {
        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(value: 1, timescale: 3), queue: .main) { [weak self] time in
            guard let self, let d = self.player.currentItem?.duration, d.isNumeric, CMTimeGetSeconds(d) > 0 else { return }
            self.progress = CMTimeGetSeconds(time) / CMTimeGetSeconds(d)
        }
    }

    deinit {
        if let timeObserver { player.removeTimeObserver(timeObserver) }
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
    }

    func play(_ track: CatalogTrack) {
        if current?.id == track.id {
            if playing { player.pause() } else { player.play() }
            playing.toggle()
            return
        }
        try? AVAudioSession.sharedInstance().setCategory(.playback)
        let item = AVPlayerItem(url: track.streamURL)
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        endObserver = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main) { [weak self] _ in
            self?.player.seek(to: .zero)
            self?.playing = false
        }
        player.replaceCurrentItem(with: item)
        current = track
        progress = 0
        player.play()
        playing = true
    }

    func stop() {
        player.pause()
        player.replaceCurrentItem(with: nil)
        current = nil
        playing = false
    }
}

/// Медиатека во вкладке PRISM: открытая музыка Jamendo и Openverse —
/// поиск, жанры, прослушивание потоком. Понравившийся трек можно
/// сохранить в «Мою музыку» (если площадка разрешает скачивание) и
/// поставить в свой ролик. Под каждым треком — автор и лицензия.
struct MediaLibraryScreen: View {
    let model: AppModel
    let safeTop: CGFloat

    @State private var source: CatalogSource = MusicCatalog.hasJamendoKey ? .jamendo : .openverse
    @State private var query = ""
    @State private var submitted = ""
    @State private var genre: String?
    @State private var tracks: [CatalogTrack] = []
    @State private var page = 0
    @State private var more = false
    @State private var loading = false
    @State private var error: String?
    @State private var saving: Set<String> = []
    @State private var notice: String?
    @State private var menu: CatalogTrack?
    @State private var player = CatalogPlayer()
    @Environment(\.openURL) private var openURL

    private var searchKey: String { "\(source.rawValue)|\(submitted)|\(genre ?? "")" }

    var body: some View {
        ZStack(alignment: .bottom) {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 10) {
                    controls
                    if let error {
                        GlassCard {
                            VStack(alignment: .leading, spacing: 10) {
                                Text(error).textStyle(.body).fixedSize(horizontal: false, vertical: true)
                                HStack(spacing: 10) {
                                    GhostButton(title: t("Повторить")) { Task { await load(reset: true) } }
                                    if source == .jamendo {
                                        GhostButton(title: t("Открыть Openverse")) { source = .openverse }
                                    }
                                }
                            }
                            .padding(16)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .cardPadding()
                    }
                    if !loading && error == nil && tracks.isEmpty {
                        Text(t("Ничего не нашлось. Попробуйте другое слово или жанр."))
                            .textStyle(.bodySmall, Palette.mist)
                            .padding(.horizontal, 24)
                    }
                    ForEach(tracks) { track in row(track) }
                    if loading {
                        ProgressView().tint(Palette.lilac).frame(maxWidth: .infinity).padding(16)
                    } else if more && error == nil {
                        GhostButton(title: t("Ещё треки")) { Task { await load(reset: false) } }
                            .frame(maxWidth: .infinity)
                    }
                }
                .padding(.top, safeTop + 64)
                .padding(.bottom, tabBarSpace + (player.current != nil ? 84 : 0))
            }
            .scrollDismissesKeyboard(.interactively)

            if let notice {
                Text(notice)
                    .textStyle(.labelLarge, Palette.lilac)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .glass(Capsule())
                    .padding(.bottom, tabBarSpace + (player.current != nil ? 90 : 10))
                    .transition(.opacity)
            }
            if let track = player.current { miniPlayer(track) }
        }
        .task(id: searchKey) { await load(reset: true) }
        .onDisappear { player.stop() }
        .glassActions(item: $menu, title: { $0.title }, message: { [$0.artist, $0.license].filter { !$0.isEmpty }.joined(separator: " · ") }) { track in
            actions(track)
        }
    }

    private var controls: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                ForEach(CatalogSource.allCases, id: \.self) { s in
                    Chip(text: s.rawValue, active: s == source) { source = s }
                }
            }
            HStack(spacing: 10) {
                GlassField(placeholder: t("Исполнитель, трек, настроение"), text: $query, limit: 80, singleLine: true)
                    .glass(RoundedRectangle(cornerRadius: 22, style: .continuous))
                    .onSubmit { submitted = query.trimmingCharacters(in: .whitespaces) }
                    .submitLabel(.search)
                Button { submitted = query.trimmingCharacters(in: .whitespaces) } label: {
                    Glyph(kind: .search, color: Palette.ink, size: 20)
                        .frame(width: 46, height: 46)
                        .background(Circle().fill(LinearGradient(colors: [Palette.orchid, Palette.ocean], startPoint: .topLeading, endPoint: .bottomTrailing)))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(LineIcon.search.label)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    Chip(text: t("Популярное"), active: genre == nil) { genre = nil }
                    ForEach(MusicCatalog.genres, id: \.tag) { g in
                        Chip(text: t(g.title), active: genre == g.tag) { genre = genre == g.tag ? nil : g.tag }
                    }
                }
            }
            Text(source == .jamendo
                 ? t("Jamendo — независимые музыканты, лицензии Creative Commons. Указывайте автора; треки с NC — только для некоммерческих роликов.")
                 : t("Openverse — открытое аудио под CC0 и CC BY. Для CC BY указывайте автора."))
                .textStyle(.bodySmall, Palette.mist)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 16)
    }

    private func isSaved(_ track: CatalogTrack) -> Bool {
        guard let page = track.pageURL?.absoluteString else { return false }
        return model.state.tracks.contains { $0.sourceUrl == page }
    }

    private func row(_ track: CatalogTrack) -> some View {
        let playing = player.current?.id == track.id && player.playing
        return GlassCard {
            HStack(spacing: 12) {
                Button { player.play(track) } label: {
                    HStack(spacing: 12) {
                        ZStack {
                            CoverImage(url: track.imageURL).frame(width: 52, height: 52).clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                            Glyph(kind: playing ? .pause : .play, color: Palette.ink, size: 14)
                                .frame(width: 30, height: 30)
                                .background(Circle().fill(Palette.voidRGB.opacity(0.55)))
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            Text(track.title).textStyle(.title, playing ? Palette.lilac : Palette.ink).lineLimit(1)
                            Text([track.artist, track.durationMs > 0 ? durationTitle(track.durationMs) : ""].filter { !$0.isEmpty }.joined(separator: " · "))
                                .textStyle(.bodySmall, Palette.mist)
                                .lineLimit(1)
                            if !track.license.isEmpty {
                                Text(track.license)
                                    .textStyle(.label, Palette.lilac)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 2)
                                    .background(Capsule().fill(Palette.plum.opacity(0.7)))
                                    .padding(.top, 2)
                            }
                        }
                        Spacer(minLength: 0)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                Button { menu = track } label: {
                    ZStack {
                        if saving.contains(track.id) {
                            ProgressView().tint(Palette.lilac)
                        } else if isSaved(track) {
                            Glyph(kind: .check, color: Palette.lilac, size: 20)
                        } else {
                            Glyph(kind: .more, color: Palette.mist, size: 20)
                        }
                    }
                    .frame(width: 40, height: 40)
                    .contentShape(Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(LineIcon.more.label)
            }
            .padding(10)
        }
        .cardPadding()
    }

    private func actions(_ track: CatalogTrack) -> [GlassAction] {
        let playing = player.current?.id == track.id && player.playing
        var list = [GlassAction(icon: playing ? .pause : .play, title: playing ? t("Пауза") : t("Слушать")) { player.play(track) }]
        if track.downloadable && !isSaved(track) {
            list.append(GlassAction(icon: .music, title: t("В «Мою музыку»")) { save(track) })
        }
        if let page = track.pageURL {
            list.append(GlassAction(icon: .link, title: tf("Страница на %s", track.source.rawValue)) { openURL(page) })
        }
        if let license = track.licenseURL {
            list.append(GlassAction(icon: .info, title: tf("Лицензия: %s", track.license.isEmpty ? "CC" : track.license)) { openURL(license) })
        }
        return list
    }

    private func save(_ track: CatalogTrack) {
        saving.insert(track.id)
        Task { @MainActor in
            let saved = await MusicCatalog.download(track)
            saving.remove(track.id)
            if let saved {
                model.addTracks([saved])
                show(t("Сохранено в «Мою музыку» — трек можно поставить в свой PRISM"))
            } else {
                show(t("Не удалось скачать трек"))
            }
        }
    }

    private func show(_ text: String) {
        withAnimation { notice = text }
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            withAnimation { if notice == text { notice = nil } }
        }
    }

    @MainActor
    private func load(reset: Bool) async {
        loading = true
        error = nil
        let next = reset ? 0 : page + 1
        do {
            let result = try await MusicCatalog.search(source, query: submitted, genre: genre, page: next)
            if Task.isCancelled { return }
            tracks = reset ? result.tracks : tracks + result.tracks.filter { x in !tracks.contains { $0.id == x.id } }
            page = next
            more = result.more
        } catch {
            if Task.isCancelled { return }
            self.error = error.localizedDescription
            if reset { tracks = [] }
            more = false
        }
        loading = false
    }

    private func miniPlayer(_ track: CatalogTrack) -> some View {
        HStack(spacing: 10) {
            CoverImage(url: track.imageURL).frame(width: 42, height: 42).clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            VStack(alignment: .leading, spacing: 3) {
                Text(track.title).textStyle(.labelLarge).lineLimit(1)
                Text([track.artist, track.license].filter { !$0.isEmpty }.joined(separator: " · "))
                    .textStyle(.bodySmall, Palette.mist)
                    .lineLimit(1)
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Palette.plum)
                        Capsule()
                            .fill(LinearGradient(colors: [Palette.lilac, Palette.sky], startPoint: .leading, endPoint: .trailing))
                            .frame(width: geo.size.width * CGFloat(min(1, max(0, player.progress))))
                    }
                }
                .frame(height: 3)
            }
            Button { player.play(track) } label: {
                Glyph(kind: player.playing ? .pause : .play, color: Palette.ink, size: 20)
                    .frame(width: 42, height: 42)
                    .background(Circle().fill(LinearGradient(colors: [Palette.orchid, Palette.ocean], startPoint: .topLeading, endPoint: .bottomTrailing)))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(player.playing ? LineIcon.pause.label : LineIcon.play.label)
            Button { player.stop() } label: {
                Glyph(kind: .close, color: Palette.mist, size: 18).frame(width: 36, height: 36)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(LineIcon.close.label)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .glass(RoundedRectangle(cornerRadius: 22, style: .continuous), strong: true)
        .padding(.horizontal, 16)
        .padding(.bottom, tabBarSpace - 6)
    }
}

/// Обложка трека из сети; пока грузится — градиент со значком.
struct CoverImage: View {
    let url: URL?
    @State private var image: UIImage?

    var body: some View {
        ZStack {
            LinearGradient(colors: [Palette.violet, Palette.ocean], startPoint: .topLeading, endPoint: .bottomTrailing)
            if let image {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                Glyph(kind: .music, color: Palette.ink, size: 20)
            }
        }
        .task(id: url) { image = await MusicCatalog.cover(url) }
    }
}
