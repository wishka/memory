import AVFoundation
import PhotosUI
import SwiftUI
import UIKit

/// Таймер перед началом записи: выкл., 3 или 10 секунд.
let cameraTimers = [0, 3, 10]
/// Сколько длится запись: 15, 30 или 60 секунд.
let cameraLengths = [15, 30, 60]

/// Камера PRISM на AVFoundation: передняя или задняя, вспышка, запись
/// частями (пауза — конец части, продолжение — новая часть). Части
/// склеиваются в одно видео, когда запись закончена.
@Observable
final class PrismCameraModel: NSObject, AVCaptureFileOutputRecordingDelegate {
    let session = AVCaptureSession()
    private(set) var allowed = true
    private(set) var front = false
    private(set) var torch = false
    private(set) var recording = false
    private(set) var paused = false
    /// Сколько уже снято, мс — все части и текущая.
    private(set) var recordedMs: Int64 = 0

    @ObservationIgnored private let output = AVCaptureMovieFileOutput()
    @ObservationIgnored private let queue = DispatchQueue(label: "memory.camera")
    @ObservationIgnored private var segments: [URL] = []
    @ObservationIgnored private var segmentsMs: Int64 = 0
    @ObservationIgnored private var ticker: Timer?
    /// Когда последняя часть записана — закончить запись целиком.
    @ObservationIgnored private var finishAfterSegment = false
    /// Часть остановлена, но ещё не дописана в файл.
    @ObservationIgnored private var stopping = false
    @ObservationIgnored var onFinished: ((URL?) -> Void)?

    /// Запросить доступ и запустить камеру.
    func start() {
        Task { @MainActor in
            let video = await AVCaptureDevice.requestAccess(for: .video)
            let audio = await AVCaptureDevice.requestAccess(for: .audio)
            allowed = video
            guard video else { return }
            let front = self.front
            queue.async { [weak self] in
                self?.configure(front: front, audio: audio)
                self?.session.startRunning()
            }
        }
    }

    /// Закрыть камеру: записанные части больше не нужны.
    func stop() {
        ticker?.invalidate()
        onFinished = nil
        if output.isRecording { output.stopRecording() }
        for part in segments { try? FileManager.default.removeItem(at: part) }
        segments = []
        queue.async { [weak self] in self?.session.stopRunning() }
    }

    /// Звук сессии настраиваем сами: микрофон пишет, а музыка для ролика
    /// при этом играет из динамика (иначе камера глушит плеер).
    static func prepareAudio() {
        try? AVAudioSession.sharedInstance().setCategory(.playAndRecord, mode: .videoRecording, options: [.defaultToSpeaker, .allowBluetoothA2DP, .mixWithOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
    }

    private func configure(front: Bool, audio: Bool) {
        session.beginConfiguration()
        session.automaticallyConfiguresApplicationAudioSession = false
        Self.prepareAudio()
        session.sessionPreset = session.canSetSessionPreset(.hd1920x1080) ? .hd1920x1080 : .high
        for input in session.inputs where (input as? AVCaptureDeviceInput)?.device.hasMediaType(.video) == true {
            session.removeInput(input)
        }
        if let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: front ? .front : .back),
           let input = try? AVCaptureDeviceInput(device: device), session.canAddInput(input) {
            session.addInput(input)
        }
        if audio, !session.inputs.contains(where: { ($0 as? AVCaptureDeviceInput)?.device.hasMediaType(.audio) == true }),
           let mic = AVCaptureDevice.default(for: .audio),
           let input = try? AVCaptureDeviceInput(device: mic), session.canAddInput(input) {
            session.addInput(input)
        }
        if !session.outputs.contains(output), session.canAddOutput(output) {
            session.addOutput(output)
        }
        // Вертикальное видео; передняя камера — зеркально, как в зеркале
        if let connection = output.connection(with: .video) {
            if connection.isVideoRotationAngleSupported(90) { connection.videoRotationAngle = 90 }
            if connection.isVideoMirroringSupported {
                connection.automaticallyAdjustsVideoMirroring = false
                connection.isVideoMirrored = front
            }
        }
        session.commitConfiguration()
    }

    func flip() {
        guard !recording else { return }
        front.toggle()
        torch = false
        let front = self.front
        queue.async { [weak self] in self?.configure(front: front, audio: true) }
    }

    func toggleTorch() {
        guard let device = (session.inputs.first { ($0 as? AVCaptureDeviceInput)?.device.hasMediaType(.video) == true } as? AVCaptureDeviceInput)?.device,
              device.hasTorch, (try? device.lockForConfiguration()) != nil else { return }
        torch.toggle()
        device.torchMode = torch ? .on : .off
        device.unlockForConfiguration()
    }

    /// Начать запись или продолжить после паузы.
    func record() {
        guard !output.isRecording else { return }
        Self.prepareAudio()
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("\(UUID().uuidString).mov")
        output.startRecording(to: url, recordingDelegate: self)
        recording = true
        paused = false
        ticker?.invalidate()
        ticker = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
            guard let self, self.output.isRecording else { return }
            self.recordedMs = self.segmentsMs + Int64(CMTimeGetSeconds(self.output.recordedDuration) * 1000)
        }
    }

    /// Пауза: текущая часть записывается в файл.
    func pause() {
        guard output.isRecording else { return }
        paused = true
        stopping = true
        output.stopRecording()
    }

    /// Закончить: дописать часть и склеить все части.
    func finish() {
        if output.isRecording || stopping {
            finishAfterSegment = true
            if output.isRecording {
                stopping = true
                output.stopRecording()
            }
        } else {
            merge()
        }
    }

    func fileOutput(_ output: AVCaptureFileOutput, didFinishRecordingTo url: URL, from connections: [AVCaptureConnection], error: Error?) {
        // Ошибка «записано успешно» бывает и у целого файла — например, при паузе
        let ok = error == nil
            || ((error as NSError?)?.userInfo[AVErrorRecordingSuccessfullyFinishedKey] as? Bool) == true
        Task { @MainActor in
            stopping = false
            let duration = ok ? (try? await AVURLAsset(url: url).load(.duration)) : nil
            let ms = duration.map { Int64(CMTimeGetSeconds($0) * 1000) } ?? 0
            if ms > 0 && onFinished != nil {
                segments.append(url)
                segmentsMs += ms
                recordedMs = segmentsMs
            } else {
                try? FileManager.default.removeItem(at: url)
            }
            if finishAfterSegment {
                finishAfterSegment = false
                merge()
            }
        }
    }

    /// Части — в одно видео в папке вложений.
    private func merge() {
        ticker?.invalidate()
        recording = false
        let parts = segments
        segments = []
        segmentsMs = 0
        Task { @MainActor in
            let url = await Self.concatenate(parts)
            for part in parts { try? FileManager.default.removeItem(at: part) }
            onFinished?(url)
        }
    }

    static func concatenate(_ parts: [URL]) async -> URL? {
        guard !parts.isEmpty else { return nil }
        let composition = AVMutableComposition()
        guard let video = composition.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid) else { return nil }
        let audio = composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid)
        var at = CMTime.zero
        for part in parts {
            let asset = AVURLAsset(url: part)
            guard let duration = try? await asset.load(.duration),
                  let v = try? await asset.loadTracks(withMediaType: .video).first else { continue }
            let range = CMTimeRange(start: .zero, duration: duration)
            try? video.insertTimeRange(range, of: v, at: at)
            if let t = try? await v.load(.preferredTransform) { video.preferredTransform = t }
            if let a = try? await asset.loadTracks(withMediaType: .audio).first {
                try? audio?.insertTimeRange(range, of: a, at: at)
            }
            at = at + duration
        }
        // Без микрофона звуковая дорожка пустая — её не должно быть в файле
        if let audio, audio.segments.isEmpty { composition.removeTrack(audio) }
        guard at > .zero,
              let session = AVAssetExportSession(asset: composition, presetName: AVAssetExportPresetHighestQuality) else { return nil }
        let out = MediaFiles.directory.appendingPathComponent("\(UUID().uuidString).mp4")
        session.outputURL = out
        session.outputFileType = .mp4
        await session.export()
        return session.status == .completed ? out : nil
    }
}

/// Камера внутри экрана.
private struct PrismCameraPreview: UIViewRepresentable {
    let session: AVCaptureSession

    func makeUIView(context: Context) -> PreviewUIView {
        let view = PreviewUIView()
        view.previewLayer.session = session
        view.previewLayer.videoGravity = .resizeAspectFill
        view.backgroundColor = .black
        return view
    }

    func updateUIView(_ view: PreviewUIView, context: Context) {}

    final class PreviewUIView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }
}

/// Экран съёмки PRISM: таймер перед записью, длина ролика, вспышка,
/// смена камеры, пауза и продолжение, выбор видео из галереи. Музыку
/// можно выбрать до съёмки — из «Моей музыки» или медиатеки: она звучит
/// во время записи и сразу стоит в редакторе. Готовое видео и выбранный
/// трек уходят в onVideo.
struct PrismCamera: View {
    let model: AppModel
    let onVideo: (URL, MusicTrack?) -> Void
    let onClose: () -> Void

    @State private var music: MusicTrack?
    @State private var musicPlayer: AVAudioPlayer?
    @State private var choosingMusic = false

    @State private var camera = PrismCameraModel()
    @State private var timer = 0
    @State private var length = 30
    @State private var countdown = 0
    @State private var merging = false
    @State private var showGallery = false
    @State private var galleryItem: PhotosPickerItem?
    @State private var importing = false
    @State private var failed = false

    var body: some View {
        ZStack {
            Palette.void.ignoresSafeArea()
            if camera.allowed {
                PrismCameraPreview(session: camera.session).ignoresSafeArea()
            } else {
                Text(t("Нет доступа к камере. Разрешите его в настройках телефона или выберите видео из галереи."))
                    .textStyle(.body, Palette.mist)
                    .multilineTextAlignment(.center)
                    .padding(32)
            }

            if countdown > 0 {
                Text("\(countdown)")
                    .font(.custom(Fonts.unboundedExtraLight, size: 120))
                    .foregroundStyle(Palette.ink)
            }
            if merging || importing {
                ProgressView().tint(Palette.lilac).scaleEffect(1.5)
            }

            VStack {
                HStack(spacing: 8) {
                    RoundGlyphButton(icon: .close) {
                        musicPlayer?.stop()
                        camera.stop()
                        onClose()
                    }
                    Spacer()
                    if !camera.recording {
                        chip(timer == 0 ? t("Таймер") : tf("%s с", timer), active: timer > 0) {
                            timer = cameraTimers[(cameraTimers.firstIndex(of: timer)! + 1) % cameraTimers.count]
                        }
                        let plus = model.state.plan != .free
                        chip(tf("%s с", length) + (!plus && length == 30 ? " · 60 PLUS" : ""), active: true) {
                            let next = cameraLengths[((cameraLengths.firstIndex(of: length) ?? 0) + 1) % cameraLengths.count]
                            // 60 секунд — Memory+: без него после 30 — снова 15 и шторка
                            if !Premium.freeLengths.contains(next) && !plus {
                                model.require(.prismLong)
                                length = cameraLengths[0]
                            } else {
                                length = next
                            }
                        }
                    }
                    RoundGlyphButton(icon: .flash, color: camera.torch ? Palette.ink : Palette.lilac, strong: camera.torch) {
                        camera.toggleTorch()
                    }
                    if !camera.recording {
                        RoundGlyphButton(icon: .flip, color: Palette.lilac) { camera.flip() }
                    }
                }
                .padding(.horizontal, 12)
                .padding(.top, 8)

                musicChip.padding(.top, 8)

                Spacer()

                Text(status).textStyle(.labelLarge).padding(.bottom, 16)
                HStack {
                    ZStack {
                        if !camera.recording {
                            RoundGlyphButton(icon: .image, color: Palette.lilac) { showGallery = true }
                        }
                    }
                    .frame(width: 56, height: 56)
                    Spacer()
                    shutter
                    Spacer()
                    ZStack {
                        if camera.recording && camera.recordedMs >= VideoEdit.minDurationMs {
                            RoundGlyphButton(icon: .check, strong: true) { finish() }
                        }
                    }
                    .frame(width: 56, height: 56)
                }
                .padding(.horizontal, 32)
                .padding(.bottom, 24)
            }
        }
        .onAppear {
            camera.onFinished = { url in
                merging = false
                musicPlayer?.stop()
                if let url { onVideo(url, music) } else { failed = true }
            }
            camera.start()
        }
        .onDisappear {
            musicPlayer?.stop()
            camera.stop()
        }
        .fullScreenCover(isPresented: $choosingMusic) {
            MusicPicker(model: model, onPick: { picked in
                choosingMusic = false
                setMusic(picked)
            }, onDismiss: { choosingMusic = false })
        }
        .onChange(of: camera.recordedMs) { _, ms in
            if camera.recording && !camera.paused && ms >= Int64(length) * 1000 { finish() }
        }
        .photosPicker(isPresented: $showGallery, selection: $galleryItem, matching: .videos)
        .onChange(of: galleryItem) { _, item in
            guard let item else { return }
            galleryItem = nil
            importing = true
            Task {
                var url: URL?
                if let movie = try? await item.loadTransferable(type: PickedMovie.self) {
                    url = MediaFiles.importFile(at: movie.url, kind: .video).map { MediaFiles.url($0) }
                    try? FileManager.default.removeItem(at: movie.url)
                }
                importing = false
                if let url { onVideo(url, music) } else { failed = true }
            }
        }
        .glassActions(
            $failed,
            title: t("Не удалось открыть видео (до 200 МБ)"),
            actions: [GlassAction(icon: .check, title: t("Понятно")) {}]
        )
    }

    private var status: String {
        if !camera.recording { return t("Нажмите, чтобы снимать") }
        if camera.paused { return tf("Пауза · %s", seconds(camera.recordedMs)) }
        return tf("%s / %s", seconds(camera.recordedMs), seconds(Int64(length) * 1000))
    }

    private func seconds(_ ms: Int64) -> String { "\(ms / 60_000):" + String(format: "%02d", Int(ms / 1000 % 60)) }

    private func finish() {
        merging = true
        musicPlayer?.pause()
        camera.finish()
    }

    /// Выбранный трек: готовим плеер, чтобы он заиграл вместе с записью.
    private func setMusic(_ track: MusicTrack?) {
        musicPlayer?.stop()
        music = track
        musicPlayer = track.flatMap { try? AVAudioPlayer(contentsOf: $0.url) }
        musicPlayer?.prepareToPlay()
    }

    /// Музыка для ролика: выбрать до съёмки — звучит во время записи.
    private var musicChip: some View {
        HStack(spacing: 8) {
            Button { if !camera.recording { choosingMusic = true } } label: {
                HStack(spacing: 8) {
                    Glyph(kind: .music, color: music != nil ? Palette.ink : Palette.lilac, size: 18)
                    Text(music.map { $0.artist.isEmpty ? $0.title : "\($0.artist) — \($0.title)" } ?? t("Добавить музыку"))
                        .textStyle(.labelLarge)
                        .lineLimit(1)
                        .frame(maxWidth: 220)
                }
            }
            .buttonStyle(.plain)
            .disabled(camera.recording)
            if music != nil && !camera.recording {
                Button { setMusic(nil) } label: {
                    Glyph(kind: .close, color: Palette.mist, size: 14).frame(width: 26, height: 26)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(LineIcon.close.label)
            }
        }
        .padding(.leading, 14)
        .padding(.trailing, music != nil && !camera.recording ? 6 : 14)
        .padding(.vertical, 8)
        .glass(Capsule(), strong: music != nil)
    }

    /// Кнопка записи: старт (с таймером), пауза, продолжение.
    private var shutter: some View {
        let progress = Double(camera.recordedMs) / Double(length * 1000)
        let active = camera.recording && !camera.paused
        return Button {
            if countdown > 0 || merging { return }
            if !camera.recording {
                Task { @MainActor in
                    var left = timer
                    while left > 0 {
                        countdown = left
                        try? await Task.sleep(nanoseconds: 1_000_000_000)
                        left -= 1
                    }
                    countdown = 0
                    musicPlayer?.currentTime = 0
                    musicPlayer?.play()
                    camera.record()
                }
            } else if camera.paused {
                musicPlayer?.play()
                camera.record()
            } else {
                musicPlayer?.pause()
                camera.pause()
            }
        } label: {
            ZStack {
                Circle().stroke(Palette.inkRGB.opacity(0.35), lineWidth: 5)
                if camera.recording {
                    Circle()
                        .trim(from: 0, to: min(1, progress))
                        .stroke(
                            AngularGradient(colors: [Palette.lilac, Palette.sky, Palette.orchid, Palette.lilac], center: .center),
                            style: StrokeStyle(lineWidth: 5, lineCap: .round)
                        )
                        .rotationEffect(.degrees(-90))
                }
                RoundedRectangle(cornerRadius: active ? 8 : 31, style: .continuous)
                    .fill(LinearGradient(colors: [Palette.orchid, Palette.ocean], startPoint: .topLeading, endPoint: .bottomTrailing))
                    .frame(width: active ? 30 : 62, height: active ? 30 : 62)
            }
            .frame(width: 84, height: 84)
            .animation(.easeOut(duration: 0.2), value: active)
        }
        .buttonStyle(.plain)
        .sensoryFeedback(.impact(weight: .medium), trigger: camera.recording)
    }

    private func chip(_ text: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text)
                .textStyle(.labelLarge, active ? Palette.ink : Palette.lilac)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .glass(Capsule(), strong: active)
        }
        .buttonStyle(.plain)
    }
}
