import AVFoundation
import SwiftUI

/// Музыка для ролика — при съёмке и в редакторе PRISM. Две вкладки:
/// «Моя музыка» (уже загруженные треки) и «Медиатека» (Jamendo и
/// Openverse). Трек из медиатеки сначала скачивается в «Мою музыку» —
/// так он останется у человека и встанет в ролик. Треки можно
/// послушать прямо здесь. То же — в MusicPicker.kt на Android.
struct MusicPicker: View {
    let model: AppModel
    let onPick: (MusicTrack) -> Void
    let onDismiss: () -> Void

    private enum Tab { case mine, library }

    @State private var tab: Tab = .mine
    @State private var source: CatalogSource = MusicCatalog.hasJamendoKey ? .jamendo : .openverse
    @State private var query = ""
    @State private var submitted = ""
    @State private var genre: String?
    @State private var results: [CatalogTrack] = []
    @State private var loading = false
    @State private var error: String?
    @State private var saving: String?
    @State private var notice: String?
    @State private var player = CatalogPlayer()
    @State private var local: AVAudioPlayer?
    @State private var localId: String?

    private var searchKey: String { "\(source.rawValue)|\(submitted)|\(genre ?? "")" }

    var body: some View {
        ZStack(alignment: .bottom) {
            Palette.void.ignoresSafeArea()
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 12) {
                    RoundGlyphButton(icon: .close, action: dismiss)
                    Text(t("Музыка для ролика")).textStyle(.headline)
                }
                .padding(.horizontal, 16)
                HStack(spacing: 8) {
                    Chip(text: t("Моя музыка"), active: tab == .mine) { switchTab(.mine) }
                    Chip(text: t("Медиатека"), active: tab == .library) { switchTab(.library) }
                }
                .padding(.horizontal, 16)
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 8) {
                        if tab == .mine { mine } else { library }
                    }
                    .padding(.bottom, 24)
                }
                .scrollDismissesKeyboard(.interactively)
            }
            .padding(.top, 12)

            if let notice {
                Text(notice)
                    .textStyle(.labelLarge, Palette.lilac)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .glass(Capsule())
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
                    .transition(.opacity)
            }
        }
        .task(id: tab == .library ? searchKey : "") {
            if tab == .library { await load() }
        }
        .onDisappear(perform: stopAll)
    }

    // MARK: Моя музыка

    @ViewBuilder
    private var mine: some View {
        let tracks = model.state.tracks
        if tracks.isEmpty {
            Text(t("В «Моей музыке» пока пусто — выберите трек в медиатеке."))
                .textStyle(.bodySmall, Palette.mist)
                .padding(.horizontal, 24)
            GhostButton(title: t("Медиатека")) { switchTab(.library) }
                .padding(.horizontal, 16)
        }
        ForEach(tracks) { track in
            pickerRow(
                title: track.title,
                subtitle: [track.artist, track.durationMs > 0 ? durationTitle(track.durationMs) : "", track.license ?? ""]
                    .filter { !$0.isEmpty }.joined(separator: " · "),
                cover: nil,
                playing: localId == track.id,
                busy: false,
                onPlay: { toggleLocal(track) },
                onChoose: { choose(track) }
            )
        }
    }

    // MARK: Медиатека

    @ViewBuilder
    private var library: some View {
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
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    Chip(text: t("Популярное"), active: genre == nil) { genre = nil }
                    ForEach(MusicCatalog.genres, id: \.tag) { g in
                        Chip(text: t(g.title), active: genre == g.tag) { genre = genre == g.tag ? nil : g.tag }
                    }
                }
            }
        }
        .padding(.horizontal, 16)
        if let error {
            Text(error)
                .textStyle(.bodySmall, Palette.mist)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 24)
            GhostButton(title: t("Повторить")) { Task { await load() } }
                .padding(.horizontal, 16)
        }
        if !loading && error == nil && results.isEmpty {
            Text(t("Ничего не нашлось. Попробуйте другое слово или жанр."))
                .textStyle(.bodySmall, Palette.mist)
                .padding(.horizontal, 24)
        }
        ForEach(results) { track in
            pickerRow(
                title: track.title,
                subtitle: [track.artist, track.durationMs > 0 ? durationTitle(track.durationMs) : "", track.license]
                    .filter { !$0.isEmpty }.joined(separator: " · "),
                cover: track.imageURL,
                playing: player.current?.id == track.id && player.playing,
                busy: saving == track.id,
                onPlay: {
                    stopLocal()
                    player.play(track)
                },
                onChoose: { choose(track) }
            )
        }
        if loading {
            ProgressView().tint(Palette.lilac).frame(maxWidth: .infinity).padding(16)
        }
    }

    private func pickerRow(
        title: String,
        subtitle: String,
        cover: URL?,
        playing: Bool,
        busy: Bool,
        onPlay: @escaping () -> Void,
        onChoose: @escaping () -> Void
    ) -> some View {
        GlassCard {
            HStack(spacing: 12) {
                Button(action: onPlay) {
                    HStack(spacing: 12) {
                        ZStack {
                            CoverImage(url: cover).frame(width: 48, height: 48).clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                            Glyph(kind: playing ? .pause : .play, color: Palette.ink, size: 14)
                                .frame(width: 28, height: 28)
                                .background(Circle().fill(Palette.voidRGB.opacity(0.55)))
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            Text(title).textStyle(.title, playing ? Palette.lilac : Palette.ink).lineLimit(1)
                            Text(subtitle).textStyle(.bodySmall, Palette.mist).lineLimit(1)
                        }
                        Spacer(minLength: 0)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                if busy {
                    ProgressView().tint(Palette.lilac).frame(width: 80)
                } else {
                    Button(action: onChoose) {
                        Text(t("ВЫБРАТЬ"))
                            .textStyle(.labelLarge, Palette.ink)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(Capsule().fill(LinearGradient(colors: [Palette.orchid, Palette.ocean], startPoint: .topLeading, endPoint: .bottomTrailing)))
                    }
                    .buttonStyle(.plain)
                    .disabled(saving != nil)
                }
            }
            .padding(10)
        }
        .cardPadding()
    }

    // MARK: Действия

    private func switchTab(_ next: Tab) {
        stopAll()
        tab = next
    }

    private func choose(_ track: MusicTrack) {
        stopAll()
        onPick(track)
    }

    /// Трек медиатеки: уже скачанный берём из «Моей музыки», иначе — скачиваем.
    private func choose(_ track: CatalogTrack) {
        if let page = track.pageURL?.absoluteString,
           let saved = model.state.tracks.first(where: { $0.sourceUrl == page }) {
            choose(saved)
            return
        }
        guard track.downloadable else {
            show(t("Этот трек можно только слушать — площадка не разрешает скачивание"))
            return
        }
        saving = track.id
        Task { @MainActor in
            let saved = await MusicCatalog.download(track)
            saving = nil
            guard let saved else {
                show(t("Не удалось скачать трек"))
                return
            }
            model.addTracks([saved])
            choose(saved)
        }
    }

    private func toggleLocal(_ track: MusicTrack) {
        player.stop()
        if localId == track.id {
            stopLocal()
            return
        }
        stopLocal()
        try? AVAudioSession.sharedInstance().setCategory(.playback)
        guard let audio = try? AVAudioPlayer(contentsOf: track.url) else { return }
        audio.play()
        local = audio
        localId = track.id
    }

    private func stopLocal() {
        local?.stop()
        local = nil
        localId = nil
    }

    private func stopAll() {
        player.stop()
        stopLocal()
    }

    private func dismiss() {
        stopAll()
        onDismiss()
    }

    private func show(_ text: String) {
        withAnimation { notice = text }
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            withAnimation { if notice == text { notice = nil } }
        }
    }

    @MainActor
    private func load() async {
        loading = true
        error = nil
        do {
            let result = try await MusicCatalog.search(source, query: submitted, genre: genre, page: 0)
            if Task.isCancelled { return }
            results = result.tracks
        } catch {
            if Task.isCancelled { return }
            self.error = error.localizedDescription
            results = []
        }
        loading = false
    }
}
