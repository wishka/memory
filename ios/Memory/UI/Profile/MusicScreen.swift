import SwiftUI
import UniformTypeIdentifiers

/// Трек, для которого открыто меню (или выбор плейлиста).
private struct TrackMenu: Identifiable {
    let track: MusicTrack
    var id: String { track.id }
}

/// Плейлист в окне названия: nil — новый.
private struct PlaylistNaming: Identifiable {
    let playlist: Playlist?
    var id: Int64 { playlist?.id ?? -1 }
}

/// «Моя музыка»: свои треки и плейлисты. Файлы хранятся только на этом
/// телефоне и видны только владельцу. Плеер — внизу экрана: играет
/// очередь (все треки или плейлист), по окончании трека — следующий.
struct MusicScreen: View {
    let model: AppModel

    @State private var player = MusicPlayer()
    @State private var showPlaylists = false
    @State private var openPlaylist: Int64?
    @State private var importing = false
    @State private var showImporter = false
    @State private var importFailed = false
    @State private var menu: TrackMenu?
    @State private var choosing: TrackMenu?
    @State private var playlistMenu: Playlist?
    @State private var naming: PlaylistNaming?

    var body: some View {
        let state = model.state
        let playlist = state.playlists.first { $0.id == openPlaylist }
        let tracks = playlist.map { state.playlistTracks($0) } ?? state.tracks
        ZStack(alignment: .bottom) {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 14) {
                        RoundGlyphButton(icon: .back) {
                            if playlist != nil { openPlaylist = nil } else { model.closeProfileSection() }
                        }
                        ScreenTitle(title: playlist?.name ?? t("Моя музыка"), subtitle: plural(tracks.count, "трек", "трека", "треков"))
                        Spacer()
                        if playlist == nil {
                            RoundGlyphButton(icon: .plus, color: Palette.lilac) { if !importing { showImporter = true } }
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 4)

                    if playlist == nil {
                        HStack(spacing: 8) {
                            Chip(text: t("Треки"), active: !showPlaylists) { showPlaylists = false }
                            Chip(text: tf("Плейлисты · %s", state.playlists.count), active: showPlaylists) { showPlaylists = true }
                        }
                        .padding(.horizontal, 16)
                    }
                    if importing { hint(t("Добавляем треки…")) }

                    if playlist == nil && showPlaylists {
                        GlowButton(title: t("Новый плейлист")) { naming = PlaylistNaming(playlist: nil) }
                            .padding(.horizontal, 16)
                        if state.playlists.isEmpty {
                            hint(t("Соберите треки в плейлисты: для дороги, для работы, для настроения."))
                        }
                        ForEach(state.playlists) { p in
                            PlaylistLine(
                                playlist: p,
                                tracks: state.playlistTracks(p),
                                onOpen: { openPlaylist = p.id },
                                onPlay: { player.play(state.playlistTracks(p), from: 0) },
                                onMenu: { playlistMenu = p }
                            )
                        }
                    } else {
                        if let playlist {
                            HStack(spacing: 10) {
                                GlowButton(title: t("Слушать"), enabled: !tracks.isEmpty) { player.play(tracks, from: 0) }
                                GhostButton(title: t("Переименовать")) { naming = PlaylistNaming(playlist: playlist) }
                            }
                            .padding(.horizontal, 16)
                        }
                        if tracks.isEmpty {
                            hint(playlist != nil
                                 ? t("В плейлисте пока пусто. Добавьте треки через «⋯» в списке треков.")
                                 : t("Загрузите свои треки кнопкой «+» — mp3, m4a, flac и другие. Они будут только на этом телефоне."))
                        }
                        ForEach(Array(tracks.enumerated()), id: \.element.id) { index, track in
                            TrackLine(
                                track: track,
                                playing: player.current?.id == track.id && player.isPlaying,
                                onPlay: {
                                    if player.current?.id == track.id { player.toggle() } else { player.play(tracks, from: index) }
                                },
                                onMenu: { menu = TrackMenu(track: track) }
                            )
                        }
                        hint(t("Музыка хранится только на этом телефоне и видна только вам. Загружайте треки, которые вы купили или на которые у вас есть права."))
                    }
                }
                .padding(.top, 12)
                .padding(.bottom, tabBarSpace + (player.current != nil ? 84 : 0))
            }

            if let track = player.current {
                MiniPlayer(track: track, playing: player.isPlaying, progress: player.progress,
                           onToggle: player.toggle, onNext: player.next, onPrevious: player.previous)
                    .padding(.horizontal, 16)
                    .padding(.bottom, tabBarSpace - 4)
            }
        }
        .onDisappear { player.stop() }
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [.audio], allowsMultipleSelection: true) { result in
            guard case let .success(urls) = result, !urls.isEmpty else { return }
            importing = true
            Task { @MainActor in
                var added: [MusicTrack] = []
                for url in urls {
                    if let track = await MusicLibrary.importTrack(url) { added.append(track) }
                }
                importing = false
                model.addTracks(added)
                if added.count < urls.count { importFailed = true }
            }
        }
        .glassActions(
            $importFailed,
            title: t("Не все файлы удалось добавить: нужен аудиофайл до 200 МБ"),
            actions: [GlassAction(icon: .check, title: t("Понятно")) {}]
        )
        .glassActions(item: $menu, title: { $0.track.title }) { item in
            trackActions(item.track, in: playlist)
        }
        .glassActions(item: $choosing, title: { _ in t("Добавить в плейлист") }, cancel: true) { item in
            if model.state.playlists.isEmpty {
                return [GlassAction(icon: .info, title: t("Сначала создайте плейлист")) {}]
            }
            return model.state.playlists.map { p in
                GlassAction(icon: .music, title: p.name) { model.addToPlaylist(p.id, item.track.id) }
            }
        }
        .glassActions(item: $playlistMenu, title: { $0.name }) { p in
            [
                GlassAction(icon: .play, title: t("Слушать")) { player.play(model.state.playlistTracks(p), from: 0) },
                GlassAction(icon: .edit, title: t("Переименовать")) { naming = PlaylistNaming(playlist: p) },
                GlassAction(icon: .trash, title: t("Удалить плейлист"), destructive: true) {
                    if openPlaylist == p.id { openPlaylist = nil }
                    model.deletePlaylist(p.id)
                },
            ]
        }
        .sheet(item: $naming) { n in
            PlaylistNameSheet(initial: n.playlist?.name ?? "", title: n.playlist != nil ? t("Название плейлиста") : t("Новый плейлист")) { name in
                if let p = n.playlist {
                    model.renamePlaylist(p.id, name)
                } else if let id = model.createPlaylist(name) {
                    showPlaylists = true
                    openPlaylist = id
                }
                naming = nil
            } onCancel: {
                naming = nil
            }
        }
    }

    private func trackActions(_ track: MusicTrack, in playlist: Playlist?) -> [GlassAction] {
        var actions = [GlassAction(icon: .plus, title: t("Добавить в плейлист")) { choosing = TrackMenu(track: track) }]
        if let playlist {
            actions.append(GlassAction(icon: .chevronDown, title: t("Выше")) { model.moveInPlaylist(playlist.id, track.id, step: -1) })
            actions.append(GlassAction(icon: .chevronDown, title: t("Ниже")) { model.moveInPlaylist(playlist.id, track.id, step: 1) })
            actions.append(GlassAction(icon: .close, title: t("Убрать из плейлиста")) { model.removeFromPlaylist(playlist.id, track.id) })
        } else {
            actions.append(GlassAction(icon: .trash, title: t("Удалить трек"), destructive: true) {
                if player.current?.id == track.id { player.stop() }
                model.deleteTrack(track.id)
                MusicLibrary.delete(track)
            })
        }
        return actions
    }

    private func hint(_ text: String) -> some View {
        Text(text)
            .textStyle(.bodySmall, Palette.mist)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, 24)
            .padding(.vertical, 4)
    }
}

/// Трек: обложка-градиент с «играть», название, исполнитель и длительность, «⋯».
private struct TrackLine: View {
    let track: MusicTrack
    let playing: Bool
    let onPlay: () -> Void
    let onMenu: () -> Void

    var body: some View {
        GlassCard {
            HStack(spacing: 12) {
                Button(action: onPlay) {
                    HStack(spacing: 12) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(LinearGradient(colors: [Palette.orchid, Palette.ocean], startPoint: .topLeading, endPoint: .bottomTrailing))
                            Glyph(kind: playing ? .pause : .play, color: Palette.ink, size: 20)
                        }
                        .frame(width: 42, height: 42)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(track.title).textStyle(.title, playing ? Palette.lilac : Palette.ink).lineLimit(1)
                            Text([track.artist, durationTitle(track.durationMs)].filter { !$0.isEmpty }.joined(separator: " · "))
                                .textStyle(.bodySmall, Palette.mist)
                                .lineLimit(1)
                        }
                        Spacer(minLength: 0)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                Button(action: onMenu) {
                    Glyph(kind: .more, color: Palette.mist, size: 20).frame(width: 36, height: 36).contentShape(Circle())
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
        }
        .cardPadding()
    }
}

/// Плейлист: название, сколько треков и сколько звучит; «играть» и «⋯».
private struct PlaylistLine: View {
    let playlist: Playlist
    let tracks: [MusicTrack]
    let onOpen: () -> Void
    let onPlay: () -> Void
    let onMenu: () -> Void

    var body: some View {
        GlassCard {
            HStack(spacing: 12) {
                Button(action: onOpen) {
                    HStack(spacing: 12) {
                        ZStack {
                            Nebula(seed: Int(playlist.id % 97))
                            Glyph(kind: .music, color: Palette.ink, size: 22)
                        }
                        .frame(width: 52, height: 52)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(playlist.name).textStyle(.title).lineLimit(1)
                            Text(tf("%s · %s", plural(tracks.count, "трек", "трека", "треков"), durationTitle(tracks.reduce(0) { $0 + $1.durationMs })))
                                .textStyle(.bodySmall, Palette.mist)
                                .lineLimit(1)
                        }
                        Spacer(minLength: 0)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                if !tracks.isEmpty {
                    RoundGlyphButton(icon: .play, color: Palette.lilac, iconSize: 20, action: onPlay)
                }
                Button(action: onMenu) {
                    Glyph(kind: .more, color: Palette.mist, size: 20).frame(width: 36, height: 36).contentShape(Circle())
                }
                .buttonStyle(.plain)
            }
            .padding(12)
        }
        .cardPadding()
    }
}

/// Плеер внизу экрана: трек, ход, назад / пауза / дальше.
private struct MiniPlayer: View {
    let track: MusicTrack
    let playing: Bool
    let progress: Double
    let onToggle: () -> Void
    let onNext: () -> Void
    let onPrevious: () -> Void

    var body: some View {
        VStack(spacing: 8) {
            HStack(spacing: 10) {
                Glyph(kind: .music, color: Palette.lilac, size: 20)
                VStack(alignment: .leading, spacing: 1) {
                    Text(track.title).textStyle(.labelLarge).lineLimit(1)
                    if !track.artist.isEmpty {
                        Text(track.artist).textStyle(.bodySmall, Palette.mist).lineLimit(1)
                    }
                }
                Spacer(minLength: 0)
                Button(action: onPrevious) {
                    Glyph(kind: .back, color: Palette.lilac, size: 20).frame(width: 36, height: 36)
                }
                .buttonStyle(.plain)
                Button(action: onToggle) {
                    Glyph(kind: playing ? .pause : .play, color: Palette.ink, size: 22)
                        .frame(width: 42, height: 42)
                        .background(Circle().fill(LinearGradient(colors: [Palette.orchid, Palette.ocean], startPoint: .topLeading, endPoint: .bottomTrailing)))
                }
                .buttonStyle(.plain)
                Button(action: onNext) {
                    Glyph(kind: .back, color: Palette.lilac, size: 20)
                        .scaleEffect(x: -1, y: 1)
                        .frame(width: 36, height: 36)
                }
                .buttonStyle(.plain)
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Palette.plum)
                    Capsule()
                        .fill(LinearGradient(colors: [Palette.lilac, Palette.sky], startPoint: .leading, endPoint: .trailing))
                        .frame(width: geo.size.width * CGFloat(min(1, max(0, progress))))
                }
            }
            .frame(height: 3)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .glass(RoundedRectangle(cornerRadius: 22, style: .continuous), strong: true)
    }
}

/// Название плейлиста: новое или переименование.
private struct PlaylistNameSheet: View {
    let title: String
    let onDone: (String) -> Void
    let onCancel: () -> Void
    @State private var name: String

    init(initial: String, title: String, onDone: @escaping (String) -> Void, onCancel: @escaping () -> Void) {
        _name = State(initialValue: initial)
        self.title = title
        self.onDone = onDone
        self.onCancel = onCancel
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(title).textStyle(.title)
            GlassField(placeholder: t("Например, «Ночная дорога»"), text: $name, limit: 60, singleLine: true)
                .glass(RoundedRectangle(cornerRadius: 16, style: .continuous))
            HStack {
                Spacer()
                GhostButton(title: t("Отмена"), action: onCancel)
                GlowButton(title: t("Готово"), enabled: !name.trimmingCharacters(in: .whitespaces).isEmpty) { onDone(name) }
            }
        }
        .padding(20)
        .presentationDetents([.height(220)])
        .presentationDragIndicator(.visible)
        .presentationBackground(Palette.night)
        .presentationCornerRadius(28)
    }
}
