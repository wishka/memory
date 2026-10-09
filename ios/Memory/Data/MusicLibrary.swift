import AVFoundation
import Foundation
import Observation

/// «Моя музыка» на iPhone: выбранный файл копируется в папку приложения
/// (как вложения), из него читаются название, исполнитель и
/// длительность. Треки видит и слушает только владелец: это личная
/// библиотека, а не публикация.
enum MusicLibrary {
    /// Скопировать трек; nil — файл не читается, это не аудио или он больше 200 МБ.
    static func importTrack(_ url: URL) async -> MusicTrack? {
        guard let copy = MediaFiles.importFile(at: url, kind: .document) else { return nil }
        let file = MediaFiles.url(copy)
        let asset = AVURLAsset(url: file)
        let duration = (try? await asset.load(.duration)).map { CMTimeGetSeconds($0) } ?? 0
        guard duration.isFinite, duration > 0 else {
            MediaFiles.delete(copy)
            return nil
        }
        let metadata = (try? await asset.load(.commonMetadata)) ?? []
        let title = await string(metadata, .commonIdentifierTitle)
        let artist = await string(metadata, .commonIdentifierArtist)
        let fallback = (copy.name as NSString).deletingPathExtension
        return MusicTrack(
            id: copy.id,
            title: String((title.isEmpty ? (fallback.isEmpty ? copy.name : fallback) : title).prefix(120)),
            artist: String(artist.prefix(120)),
            fileName: copy.fileName,
            durationMs: Int64(duration * 1000),
            addedAt: Date()
        )
    }

    static func delete(_ track: MusicTrack) {
        try? FileManager.default.removeItem(at: track.url)
    }

    private static func string(_ items: [AVMetadataItem], _ id: AVMetadataIdentifier) async -> String {
        guard let item = AVMetadataItem.metadataItems(from: items, filteredByIdentifier: id).first,
              let value = try? await item.load(.stringValue) else { return "" }
        return value
    }
}

/// Плеер «Моей музыки»: очередь треков, текущий, пауза, следующий, ход.
/// Играет и при выключенном звонке (категория «воспроизведение»).
@Observable
final class MusicPlayer: NSObject, AVAudioPlayerDelegate {
    private(set) var queue: [MusicTrack] = []
    private(set) var index = 0
    private(set) var isPlaying = false
    private(set) var progress: Double = 0

    @ObservationIgnored private var player: AVAudioPlayer?
    @ObservationIgnored private var ticker: Timer?

    var current: MusicTrack? { queue.indices.contains(index) ? queue[index] : nil }

    func play(_ tracks: [MusicTrack], from start: Int) {
        guard !tracks.isEmpty else { return }
        queue = tracks
        index = min(max(0, start), tracks.count - 1)
        load()
    }

    func toggle() {
        guard let player else { return }
        if player.isPlaying { player.pause() } else { player.play() }
        isPlaying = player.isPlaying
    }

    func next() {
        guard !queue.isEmpty else { return }
        index = (index + 1) % queue.count
        load()
    }

    func previous() {
        guard !queue.isEmpty else { return }
        // В начале трека — предыдущий, иначе — сначала этот же
        if let player, player.currentTime > 3 {
            player.currentTime = 0
        } else {
            index = (index - 1 + queue.count) % queue.count
            load()
        }
    }

    func stop() {
        player?.stop()
        player = nil
        queue = []
        isPlaying = false
        ticker?.invalidate()
    }

    private func load() {
        guard let track = current else { return }
        try? AVAudioSession.sharedInstance().setCategory(.playback)
        try? AVAudioSession.sharedInstance().setActive(true)
        player = try? AVAudioPlayer(contentsOf: track.url)
        player?.delegate = self
        player?.play()
        isPlaying = player?.isPlaying ?? false
        ticker?.invalidate()
        ticker = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in
            guard let self, let p = self.player, p.duration > 0 else { return }
            self.progress = p.currentTime / p.duration
        }
    }

    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        next()
    }
}
