import AVFoundation
import QuickLook
import SwiftUI
import UniformTypeIdentifiers

/// Файлы записи: музыка играет прямо в карточке, документы открываются
/// в просмотре.
struct PostFiles: View {
    let files: [Attachment]
    @State private var preview: URL?

    var body: some View {
        if !files.isEmpty {
            VStack(spacing: 8) {
                ForEach(files) { file in
                    if file.isAudio {
                        AudioRow(file: file)
                    } else {
                        DocumentRow(attachment: file) { preview = MediaFiles.url(file) }
                    }
                }
            }
            .quickLookPreview($preview)
        }
    }
}

/// Плеер одного трека во вложении.
@Observable
private final class AudioRowPlayer {
    private(set) var playing = false
    private(set) var progress: Double = 0
    @ObservationIgnored private var player: AVPlayer?
    @ObservationIgnored private var timeObserver: Any?
    @ObservationIgnored private var endObserver: NSObjectProtocol?

    func toggle(_ url: URL) {
        if player == nil {
            let p = AVPlayer(url: url)
            player = p
            timeObserver = p.addPeriodicTimeObserver(forInterval: CMTime(value: 1, timescale: 4), queue: .main) { [weak self] time in
                guard let self, let d = p.currentItem?.duration, d.isNumeric, CMTimeGetSeconds(d) > 0 else { return }
                self.progress = CMTimeGetSeconds(time) / CMTimeGetSeconds(d)
            }
            endObserver = NotificationCenter.default.addObserver(
                forName: .AVPlayerItemDidPlayToEndTime, object: p.currentItem, queue: .main
            ) { [weak self] _ in
                self?.player?.seek(to: .zero)
                self?.playing = false
                self?.progress = 0
            }
        }
        try? AVAudioSession.sharedInstance().setCategory(.playback)
        if playing { player?.pause() } else { player?.play() }
        playing.toggle()
    }

    func stop() {
        player?.pause()
        playing = false
    }

    deinit {
        if let timeObserver { player?.removeTimeObserver(timeObserver) }
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
    }
}

/// Трек во вложении: «играть / пауза», название и ход.
struct AudioRow: View {
    let file: Attachment
    @State private var audio = AudioRowPlayer()

    var body: some View {
        HStack(spacing: 10) {
            Button { audio.toggle(MediaFiles.url(file)) } label: {
                Glyph(kind: audio.playing ? .pause : .play, color: Palette.ink, size: 18)
                    .frame(width: 40, height: 40)
                    .background(Circle().fill(LinearGradient(colors: [Palette.orchid, Palette.ocean], startPoint: .topLeading, endPoint: .bottomTrailing)))
            }
            .buttonStyle(.plain)
            VStack(alignment: .leading, spacing: 6) {
                Text(title).textStyle(.bodySmall).lineLimit(1)
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Palette.plum)
                        Capsule()
                            .fill(LinearGradient(colors: [Palette.lilac, Palette.sky], startPoint: .leading, endPoint: .trailing))
                            .frame(width: geo.size.width * CGFloat(min(1, max(0, audio.progress))))
                    }
                }
                .frame(height: 3)
            }
            if file.size > 0 {
                Text(sizeTitle(file.size)).textStyle(.label, Palette.mist)
            }
            Glyph(kind: .music, color: Palette.lilac, size: 18)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Palette.voidRGB.opacity(0.35)))
        .onDisappear { audio.stop() }
    }

    private var title: String {
        let name = (file.name as NSString).deletingPathExtension
        return name.isEmpty ? t("Трек") : name
    }
}

/// Заставка группы: своё фото или туманность.
struct GroupCover: View {
    let group: MemoryGroup
    var maxPixel: CGFloat = 1080

    var body: some View {
        if let cover = group.cover, cover.kind == .photo {
            Color.clear.overlay { MediaThumbnail(attachment: cover, maxPixel: maxPixel) }.clipped()
        } else {
            Nebula(seed: group.seed)
        }
    }
}

/// Выбор музыки для записи: трек из «Моей музыки» или звуковой файл с
/// телефона. Выбранное копируется отдельным вложением — удалят трек,
/// запись файл не потеряет.
struct MusicChooser: View {
    let tracks: [MusicTrack]
    let onPicked: ([Attachment]) -> Void
    let onClose: () -> Void
    @State private var showFiles = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(t("Музыка")).textStyle(.title).padding(.horizontal, 20).padding(.top, 18).padding(.bottom, 8)
            if tracks.isEmpty {
                Text(t("В «Моей музыке» пока нет треков. Можно выбрать файл с телефона."))
                    .textStyle(.bodySmall, Palette.mist)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 6)
            }
            ScrollView {
                VStack(spacing: 0) {
                    ForEach(tracks) { track in
                        Button { pick(track) } label: {
                            HStack(spacing: 12) {
                                Glyph(kind: .music, color: Palette.lilac, size: 20)
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(track.title).textStyle(.body).lineLimit(1)
                                    if !track.artist.isEmpty {
                                        Text(track.artist).textStyle(.bodySmall, Palette.mist).lineLimit(1)
                                    }
                                }
                                Spacer(minLength: 0)
                            }
                            .padding(.horizontal, 20)
                            .padding(.vertical, 10)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            HStack(spacing: 10) {
                GhostButton(title: t("Отмена"), action: onClose)
                GlowButton(title: t("Файл с телефона")) { showFiles = true }
            }
            .padding(16)
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .presentationBackground(Palette.night)
        .presentationCornerRadius(28)
        .fileImporter(isPresented: $showFiles, allowedContentTypes: [.audio], allowsMultipleSelection: true) { result in
            guard case .success(let urls) = result else { return }
            let picked = urls.prefix(AppModel.maxMedia).compactMap { MediaFiles.importFile(at: $0, kind: .document) }
            onPicked(picked)
            onClose()
        }
    }

    private func pick(_ track: MusicTrack) {
        let title = track.artist.isEmpty ? track.title : "\(track.artist) — \(track.title)"
        if var copy = MediaFiles.importFile(at: track.url, kind: .document) {
            copy.name = "\(title).\(track.url.pathExtension)"
            onPicked([copy])
        }
        onClose()
    }
}
