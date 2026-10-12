import SwiftUI
import UIKit

/// Этапы создания PRISM.
private enum CreateStage { case camera, editor, publish }

/// Свой PRISM от съёмки до публикации: камера (или галерея) → редактор →
/// подпись и сборка ролика. Исходник после сборки удаляется: в ролик
/// уже вошло всё нужное.
struct PrismCreateFlow: View {
    let model: AppModel

    @State private var stage: CreateStage = .camera
    @State private var edit: VideoEdit?
    @State private var failed = false
    /// Трек, выбранный при съёмке: сразу ставится в редактор.
    @State private var captureMusic: String?

    var body: some View {
        ZStack {
            Palette.void.ignoresSafeArea()
            switch stage {
            case .camera:
                PrismCamera(model: model, onVideo: { url, music in
                    captureMusic = music?.id
                    open(url)
                }, onClose: close)
            case .editor:
                if let binding = Binding($edit) {
                    PrismEditor(
                        model: model,
                        edit: binding,
                        onBack: {
                            discardSource()
                            stage = .camera
                        },
                        onNext: { stage = .publish }
                    )
                } else {
                    ProgressView().tint(Palette.lilac)
                }
            case .publish:
                if let current = edit {
                    PublishPrism(model: model, edit: current, onBack: { stage = .editor }) { video, cover, caption, music in
                        discardSource()
                        model.publishPrism(video: video, cover: cover, caption: caption, music: music, durationMs: Double(current.outputDurationMs))
                    }
                }
            }
        }
        .glassActions(
            $failed,
            title: t("Видео слишком короткое или не читается"),
            actions: [GlassAction(icon: .check, title: t("Понятно")) {}]
        )
    }

    /// Новый исходник — читаем длительность и размер, начальные правки.
    private func open(_ url: URL) {
        stage = .editor
        edit = nil
        Task { @MainActor in
            guard let info = await VideoExporter.probe(url), info.durationMs >= VideoEdit.minDurationMs else {
                try? FileManager.default.removeItem(at: url)
                stage = .camera
                failed = true
                return
            }
            var next = VideoEdit(sourceURL: url, sourceDurationMs: info.durationMs, sourceWidth: info.width, sourceHeight: info.height)
            if let id = captureMusic {
                next.musicTrackId = id
                // Под музыку микрофон тише: в нём и так слышна музыка из динамика
                next.originalVolume = 0.4
            }
            edit = next
        }
    }

    private func discardSource() {
        if let url = edit?.sourceURL { try? FileManager.default.removeItem(at: url) }
        edit = nil
    }

    private func close() {
        discardSource()
        model.closePrismCreator()
    }
}

/// Подпись, обложка и сборка ролика.
private struct PublishPrism: View {
    let model: AppModel
    let edit: VideoEdit
    let onBack: () -> Void
    let onPublished: (Attachment, Attachment?, String, String) -> Void

    @State private var caption = ""
    @State private var progress: Double = -1
    @State private var job: Task<Void, Never>?
    @State private var cover: UIImage?
    @State private var failed = false

    private var musicTitle: String {
        guard let id = edit.musicTrackId, let track = model.state.track(id) else { return "" }
        // Трек из медиатеки — с лицензией: автора и лицензию нужно указывать
        let credit = track.artist.isEmpty ? track.title : "\(track.artist) — \(track.title)"
        if let license = track.license, !license.isEmpty { return "\(credit) · \(license)" }
        return credit
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 12) {
                if progress < 0 { RoundGlyphButton(icon: .back, action: onBack) }
                Text(t("Публикация")).textStyle(.headline)
            }
            HStack(alignment: .top, spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Palette.plum)
                    if let cover { Image(uiImage: cover).resizable().scaledToFill() }
                }
                .frame(width: 108, height: 192)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                GlassField(placeholder: t("Подпись к ролику"), text: $caption, limit: 300, maxLines: 6)
                    .frame(maxWidth: .infinity, minHeight: 192, maxHeight: 192, alignment: .topLeading)
                    .glass(RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            Text(musicTitle.isEmpty ? t("Звук: оригинальный") : tf("Музыка: %s", musicTitle))
                .textStyle(.bodySmall, Palette.mist)
            Spacer()
            if progress >= 0 {
                Text(tf("Собираем ролик… %s", "\(Int(progress * 100))%")).textStyle(.labelLarge)
                ProgressView(value: min(1, max(0, progress))).tint(Palette.lilac)
                GhostButton(title: t("Отмена")) {
                    job?.cancel()
                    job = nil
                    progress = -1
                }
            } else {
                GlowButton(title: t("Опубликовать"), fullWidth: true, action: publish)
            }
        }
        .padding(16)
        .task {
            cover = await VideoExporter.frame(edit.sourceURL, ms: edit.coverMs, maxPixel: 480)
        }
        .onDisappear { job?.cancel() }
        .glassActions(
            $failed,
            title: t("Не удалось собрать ролик. Попробуйте ещё раз."),
            actions: [GlassAction(icon: .check, title: t("Понятно")) {}]
        )
    }

    private func publish() {
        progress = 0
        let music = edit.musicTrackId.flatMap { model.state.track($0) }?.url
        let edit = self.edit
        job = Task { @MainActor in
            let file = await VideoExporter.export(edit, musicURL: music) { p in progress = p }
            if Task.isCancelled {
                if let file { try? FileManager.default.removeItem(at: file) }
                return
            }
            job = nil
            guard let file else {
                progress = -1
                failed = true
                return
            }
            let coverFile = await saveCover(file, ms: edit.coverOutputMs)
            let size = (try? FileManager.default.attributesOfItem(atPath: file.path)[.size] as? Int64) ?? 0
            let id = file.deletingPathExtension().lastPathComponent
            let video = Attachment(id: id, kind: .video, fileName: file.lastPathComponent, name: file.lastPathComponent, size: size)
            onPublished(video, coverFile, caption, musicTitle)
        }
    }

    /// Обложка — кадр готового ролика (с фильтрами и текстом) в JPEG рядом с вложениями.
    private func saveCover(_ video: URL, ms: Int64) async -> Attachment? {
        guard let frame = await VideoExporter.frame(video, ms: ms, maxPixel: 1080),
              let data = frame.jpegData(compressionQuality: 0.88) else { return nil }
        return MediaFiles.save(data, kind: .photo, ext: "jpg")
    }
}
