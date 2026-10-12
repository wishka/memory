import AVFoundation
import SwiftUI

/// Запись голосового: AAC в .m4a в папке вложений. Пока идёт запись,
/// раз в 100 мс берётся громкость — из неё складывается волна. Те же
/// правила — в Voice.kt на Android.
@Observable
final class VoiceRecorder {
    private(set) var recording = false
    private(set) var elapsedMs: Int64 = 0
    private(set) var levels: [Int] = []

    @ObservationIgnored private var recorder: AVAudioRecorder?
    @ObservationIgnored private var fileName: String?
    @ObservationIgnored private var timer: Timer?
    @ObservationIgnored private var startedAt = Date()
    /// Сработало, когда запись упёрлась в предел.
    @ObservationIgnored var onLimit: (() -> Void)?

    static let minMs: Int64 = 700
    static let maxMs: Int64 = 5 * 60_000
    static let bars = 40

    /// Спросить микрофон и начать; false — доступа нет или запись не пошла.
    @MainActor
    func start() async -> Bool {
        cancel()
        let granted = await AVAudioApplication.requestRecordPermission()
        guard granted else { return false }
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .allowBluetoothA2DP])
        try? session.setActive(true)
        let name = "\(UUID().uuidString).m4a"
        let url = MediaFiles.directory.appendingPathComponent(name)
        let settings: [String: Any] = [
            AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
            AVSampleRateKey: 44_100,
            AVNumberOfChannelsKey: 1,
            AVEncoderBitRateKey: 64_000,
        ]
        guard let r = try? AVAudioRecorder(url: url, settings: settings) else { return false }
        r.isMeteringEnabled = true
        guard r.record() else { return false }
        recorder = r
        fileName = name
        startedAt = Date()
        levels = []
        elapsedMs = 0
        recording = true
        timer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in self?.tick() }
        return true
    }

    private func tick() {
        guard let r = recorder else { return }
        r.updateMeters()
        // −48 дБ — тишина, 0 — громко: по логарифму, как слышит ухо
        let db = Double(r.averagePower(forChannel: 0))
        levels.append(Int(max(0, min(100, (db + 48) / 48 * 100))))
        elapsedMs = Int64(Date().timeIntervalSince(startedAt) * 1000)
        if elapsedMs >= Self.maxMs { onLimit?() }
    }

    /// Закончить и получить вложение; nil — слишком коротко или не записалось.
    func finish() -> Attachment? {
        guard let r = recorder, let name = fileName else { return nil }
        let duration = Int64(Date().timeIntervalSince(startedAt) * 1000)
        r.stop()
        stopTimer()
        recorder = nil
        fileName = nil
        recording = false
        // Микрофон больше не нужен: звук — обычным воспроизведением
        try? AVAudioSession.sharedInstance().setCategory(.playback)
        let url = MediaFiles.directory.appendingPathComponent(name)
        let size = (try? FileManager.default.attributesOfItem(atPath: url.path)[.size] as? Int64) ?? 0
        guard duration >= Self.minMs, size > 0 else {
            try? FileManager.default.removeItem(at: url)
            return nil
        }
        return Attachment(
            id: (name as NSString).deletingPathExtension, kind: .document, fileName: name,
            name: t("Голосовое сообщение") + ".m4a", size: size,
            voiceFlag: true, durationMs: duration, waveform: Self.squeeze(levels, Self.bars)
        )
    }

    /// Передумали: запись удаляется.
    func cancel() {
        recorder?.stop()
        recorder?.deleteRecording()
        recorder = nil
        fileName = nil
        stopTimer()
        recording = false
    }

    private func stopTimer() {
        timer?.invalidate()
        timer = nil
    }

    /// Ужать уровни до n столбиков: в каждом — громкий пик своей части.
    static func squeeze(_ levels: [Int], _ n: Int) -> [Int] {
        guard !levels.isEmpty else { return Array(repeating: 8, count: n) }
        return (0..<n).map { i in
            let from = min(i * levels.count / n, levels.count - 1)
            let to = min(max(from + 1, (i + 1) * levels.count / n), levels.count)
            return levels[from..<to].max() ?? 0
        }
    }
}

/// Волна: столбики; пройденная часть — светлая, остальное приглушено.
struct Waveform: View {
    let levels: [Int]
    var progress: Double = 1
    var color: Color = Palette.ink

    var body: some View {
        Canvas { ctx, size in
            guard !levels.isEmpty else { return }
            let gap: CGFloat = 2
            let w = max(1, (size.width - gap * CGFloat(levels.count - 1)) / CGFloat(levels.count))
            for (i, v) in levels.enumerated() {
                let h = max(2, size.height * (0.12 + 0.88 * CGFloat(v) / 100))
                let x = CGFloat(i) * (w + gap)
                let played = (Double(i) + 0.5) / Double(levels.count) <= progress
                let rect = CGRect(x: x, y: (size.height - h) / 2, width: w, height: h)
                ctx.fill(Path(roundedRect: rect, cornerRadius: w / 2), with: .color(color.opacity(played ? 1 : 0.38)))
            }
        }
    }
}

/// Плеер голосового: один на пузырь, живёт, пока пузырь на экране.
@Observable
final class VoicePlayer: NSObject, AVAudioPlayerDelegate {
    private(set) var playing = false
    private(set) var position: Double = 0
    @ObservationIgnored private var player: AVAudioPlayer?
    @ObservationIgnored private var timer: Timer?

    func toggle(_ url: URL) {
        if playing {
            player?.pause()
            playing = false
            timer?.invalidate()
            return
        }
        if player == nil {
            try? AVAudioSession.sharedInstance().setCategory(.playback)
            player = try? AVAudioPlayer(contentsOf: url)
            player?.delegate = self
        }
        player?.play()
        playing = player?.isPlaying ?? false
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 0.08, repeats: true) { [weak self] _ in
            guard let self, let p = self.player else { return }
            self.position = p.currentTime
        }
    }

    /// Перемотать на долю 0…1 и играть.
    func seek(_ url: URL, to fraction: Double) {
        if player == nil {
            try? AVAudioSession.sharedInstance().setCategory(.playback)
            player = try? AVAudioPlayer(contentsOf: url)
            player?.delegate = self
        }
        guard let p = player else { return }
        p.currentTime = p.duration * fraction
        position = p.currentTime
        if !playing { toggle(url) }
    }

    var duration: Double { player?.duration ?? 0 }

    func stop() {
        player?.stop()
        timer?.invalidate()
        playing = false
    }

    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        timer?.invalidate()
        playing = false
        position = 0
        player.currentTime = 0
    }
}

/// Голосовое в пузыре: «играть / пауза», волна (нажатие — перемотать туда) и время.
struct VoiceBubble: View {
    let attachment: Attachment
    @State private var player = VoicePlayer()

    private var totalMs: Int64 { attachment.durationMs ?? 0 }
    private var progress: Double {
        let total = player.duration > 0 ? player.duration : Double(totalMs) / 1000
        return total > 0 ? player.position / total : 0
    }

    var body: some View {
        let url = MediaFiles.url(attachment)
        HStack(spacing: 10) {
            Button { player.toggle(url) } label: {
                Glyph(kind: player.playing ? .pause : .play, color: Palette.ink, size: 18)
                    .frame(width: 40, height: 40)
                    .background(Circle().fill(LinearGradient(colors: [Palette.orchid, Palette.ocean], startPoint: .topLeading, endPoint: .bottomTrailing)))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(player.playing ? t("Пауза") : t("Слушать"))
            GeometryReader { geo in
                Waveform(levels: attachment.waveform ?? [], progress: progress)
                    .contentShape(Rectangle())
                    .onTapGesture { point in
                        player.seek(url, to: min(1, max(0, point.x / max(1, geo.size.width))))
                    }
            }
            .frame(width: 150, height: 30)
            Text(durationTitle(player.playing || player.position > 0 ? Int64(player.position * 1000) : totalMs))
                .textStyle(.labelLarge, Palette.inkRGB.opacity(0.8))
                .monospacedDigit()
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 2)
        .onDisappear { player.stop() }
    }
}

/// Полоса записи вместо поля ввода: мигающая точка, время, живая волна,
/// «удалить» и «отправить».
struct RecordingBar: View {
    let recorder: VoiceRecorder
    let onCancel: () -> Void
    let onSend: () -> Void
    @State private var blink = false

    var body: some View {
        HStack(spacing: 10) {
            Button(action: onCancel) {
                Glyph(kind: .trash, color: Palette.pearlPink, size: 22)
                    .frame(width: 50, height: 50)
                    .glass(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(t("Удалить"))
            HStack(spacing: 8) {
                Circle().fill(Palette.pearlPink).frame(width: 10, height: 10).opacity(blink ? 1 : 0.3)
                Text(durationTitle(recorder.elapsedMs)).textStyle(.labelLarge).monospacedDigit()
                Waveform(levels: Array(recorder.levels.suffix(32)), color: Palette.lilac)
                    .frame(height: 26)
            }
            .padding(.horizontal, 14)
            .frame(height: 50)
            .glass(RoundedRectangle(cornerRadius: 24, style: .continuous))
            Button(action: onSend) {
                Glyph(kind: .send, color: Palette.ink)
                    .frame(width: 50, height: 50)
                    .background(Circle().fill(LinearGradient(colors: [Palette.orchid, Palette.ocean], startPoint: .topLeading, endPoint: .bottomTrailing)))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(t("Отправить"))
        }
        .padding(12)
        .onAppear {
            withAnimation(.easeInOut(duration: 0.6).repeatForever(autoreverses: true)) { blink = true }
        }
    }
}
