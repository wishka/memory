import AVFoundation
import CoreImage
import UIKit

/// Что известно о видео до правок: длительность и размер кадра с учётом поворота.
struct VideoInfo {
    let durationMs: Int64
    let width: Int
    let height: Int
}

/// Собранный по правкам ролик: композиция, обработка кадров и звук.
/// Одно и то же — для предпросмотра (AVPlayerItem) и для файла
/// (AVAssetExportSession), поэтому предпросмотр точно совпадает с итогом.
struct EditedVideo {
    let composition: AVMutableComposition
    let videoComposition: AVMutableVideoComposition
    let audioMix: AVAudioMix?

    func playerItem() -> AVPlayerItem {
        let item = AVPlayerItem(asset: composition)
        item.videoComposition = videoComposition
        item.audioMix = audioMix
        return item
    }
}

/// Сборка ролика PRISM по правкам (VideoEdit) в готовый mp4 на iPhone —
/// AVFoundation и Core Image: обрезка, скорость, поворот, кадр, цвет,
/// текст и стикеры, музыка и громкость. Те же правила, что
/// VideoExporter.kt на Android.
enum VideoExporter {
    /// Длительность и размер видео; nil — файл не читается.
    static func probe(_ url: URL) async -> VideoInfo? {
        let asset = AVURLAsset(url: url)
        guard let duration = try? await asset.load(.duration),
              let track = try? await asset.loadTracks(withMediaType: .video).first,
              let size = try? await track.load(.naturalSize),
              let transform = try? await track.load(.preferredTransform) else { return nil }
        let turned = size.applying(transform)
        let w = Int(abs(turned.width).rounded())
        let h = Int(abs(turned.height).rounded())
        let ms = Int64(CMTimeGetSeconds(duration) * 1000)
        guard ms > 0, w > 0, h > 0 else { return nil }
        return VideoInfo(durationMs: ms, width: w, height: h)
    }

    /// Кадр видео в момент ms — для обложки.
    static func frame(_ url: URL, ms: Int64, maxPixel: CGFloat = 720) async -> UIImage? {
        let generator = AVAssetImageGenerator(asset: AVURLAsset(url: url))
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = CGSize(width: maxPixel, height: maxPixel)
        generator.requestedTimeToleranceBefore = CMTime(value: 1, timescale: 10)
        generator.requestedTimeToleranceAfter = CMTime(value: 1, timescale: 10)
        guard let result = try? await generator.image(at: time(ms)) else { return nil }
        return UIImage(cgImage: result.image)
    }

    static func time(_ ms: Int64) -> CMTime { CMTime(value: ms, timescale: 1000) }

    /// Композиция по правкам. musicURL — файл трека из «Моей музыки» или nil.
    static func build(_ edit: VideoEdit, musicURL: URL?) async throws -> EditedVideo {
        let source = AVURLAsset(url: edit.sourceURL)
        guard let videoTrack = try await source.loadTracks(withMediaType: .video).first else {
            throw CocoaError(.fileReadCorruptFile)
        }
        let audioTrack = try await source.loadTracks(withMediaType: .audio).first
        let transform = try await videoTrack.load(.preferredTransform)

        let composition = AVMutableComposition()
        let range = CMTimeRange(start: time(edit.trimStartMs), end: time(edit.trimEndMs))
        guard let video = composition.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid) else {
            throw CocoaError(.fileWriteUnknown)
        }
        try video.insertTimeRange(range, of: videoTrack, at: .zero)
        video.preferredTransform = transform

        var mixParams: [AVMutableAudioMixInputParameters] = []
        if edit.originalVolume > 0, let audioTrack,
           let audio = composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid) {
            try audio.insertTimeRange(range, of: audioTrack, at: .zero)
            let params = AVMutableAudioMixInputParameters(track: audio)
            params.setVolume(Float(edit.originalVolume), at: .zero)
            mixParams.append(params)
        }

        // Скорость: весь ролик растягивается или сжимается
        if edit.speed != 1 {
            let scaled = CMTimeMultiplyByFloat64(range.duration, multiplier: 1 / edit.speed)
            composition.scaleTimeRange(CMTimeRange(start: .zero, duration: range.duration), toDuration: scaled)
        }

        // Музыка — по кругу на всю длину ролика
        var musicSource: (AVAssetTrack, CMTimeRange)?
        if let musicURL, let track = try await AVURLAsset(url: musicURL).loadTracks(withMediaType: .audio).first {
            let trackRange = try await track.load(.timeRange)
            if trackRange.duration > .zero { musicSource = (track, trackRange) }
        }
        if let source = musicSource,
           let music = composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid) {
            let (musicTrack, trackRange) = source
            let total = composition.duration
            var at = CMTime.zero
            var from = min(time(edit.musicStartMs), trackRange.duration)
            while at < total {
                let available = trackRange.duration - from
                if available <= .zero {
                    from = .zero
                    continue
                }
                let chunk = min(available, total - at)
                try music.insertTimeRange(CMTimeRange(start: from, duration: chunk), of: musicTrack, at: at)
                at = at + chunk
                from = .zero
            }
            let params = AVMutableAudioMixInputParameters(track: music)
            params.setVolume(Float(edit.musicVolume), at: .zero)
            mixParams.append(params)
        }

        let mix = AVMutableAudioMix()
        mix.inputParameters = mixParams

        let size = edit.outputSize()
        let render = CGSize(width: size.width, height: size.height)
        let overlay = overlayImage(edit, size: render).flatMap { CIImage(image: $0) }
        let matrix = edit.colorIsIdentity ? nil : edit.colorMatrix
        let rotation = edit.rotation
        let mirror = edit.mirror
        let videoComposition = AVMutableVideoComposition(asset: composition) { request in
            var image = request.sourceImage
            // Поворот по часовой и отражение
            if mirror { image = image.transformed(by: CGAffineTransform(scaleX: -1, y: 1)) }
            if rotation != 0 {
                image = image.transformed(by: CGAffineTransform(rotationAngle: -CGFloat(rotation) * .pi / 180))
            }
            image = image.transformed(by: CGAffineTransform(translationX: -image.extent.minX, y: -image.extent.minY))
            // Кадр нужных пропорций: заполнить и обрезать по центру
            let k = max(render.width / image.extent.width, render.height / image.extent.height)
            image = image.transformed(by: CGAffineTransform(scaleX: k, y: k))
            image = image.transformed(by: CGAffineTransform(
                translationX: (render.width - image.extent.width) / 2 - image.extent.minX,
                y: (render.height - image.extent.height) / 2 - image.extent.minY
            ))
            image = image.cropped(to: CGRect(origin: .zero, size: render))
            if let matrix { image = applyColor(image, matrix) }
            if let overlay { image = overlay.composited(over: image) }
            request.finish(with: image, context: nil)
        }
        videoComposition.renderSize = render
        return EditedVideo(composition: composition, videoComposition: videoComposition, audioMix: mixParams.isEmpty ? nil : mix)
    }

    /// Матрица 3×4 → CIColorMatrix.
    static func applyColor(_ image: CIImage, _ m: [Double]) -> CIImage {
        guard let filter = CIFilter(name: "CIColorMatrix") else { return image }
        filter.setValue(image, forKey: kCIInputImageKey)
        filter.setValue(CIVector(x: m[0], y: m[1], z: m[2], w: 0), forKey: "inputRVector")
        filter.setValue(CIVector(x: m[4], y: m[5], z: m[6], w: 0), forKey: "inputGVector")
        filter.setValue(CIVector(x: m[8], y: m[9], z: m[10], w: 0), forKey: "inputBVector")
        filter.setValue(CIVector(x: 0, y: 0, z: 0, w: 1), forKey: "inputAVector")
        filter.setValue(CIVector(x: m[3], y: m[7], z: m[11], w: 0), forKey: "inputBiasVector")
        return filter.outputImage?.cropped(to: image.extent) ?? image
    }

    /// Текст и стикеры одной прозрачной картинкой размером с итоговый кадр; nil — их нет.
    static func overlayImage(_ edit: VideoEdit, size: CGSize) -> UIImage? {
        if edit.overlays.isEmpty { return nil }
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        return UIGraphicsImageRenderer(size: size, format: format).image { _ in
            for o in edit.overlays { draw(o, in: size) }
        }
    }

    /// Нарисовать текст или стикер на текущем холсте — так же, как в предпросмотре.
    static func draw(_ o: VideoOverlay, in size: CGSize) {
        let fontSize = CGFloat(o.size) * size.height
        let font: UIFont
        if o.sticker {
            font = UIFont.systemFont(ofSize: fontSize)
        } else {
            font = Fonts.uiFont(o.font == .unbounded ? Fonts.unboundedRegular : Fonts.interSemiBold, size: fontSize)
        }
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = .center
        paragraph.minimumLineHeight = fontSize * 1.2
        paragraph.maximumLineHeight = fontSize * 1.2
        let color = UIColor(rgb: o.color)
        var attrs: [NSAttributedString.Key: Any] = [.font: font, .paragraphStyle: paragraph, .foregroundColor: color]
        let shadow = NSShadow()
        if !o.sticker {
            switch o.style {
            case .plain:
                shadow.shadowColor = UIColor.black.withAlphaComponent(0.55)
                shadow.shadowBlurRadius = fontSize * 0.12
                shadow.shadowOffset = CGSize(width: 0, height: fontSize * 0.04)
                attrs[.shadow] = shadow
            case .glow:
                shadow.shadowColor = color
                shadow.shadowBlurRadius = fontSize * 0.45
                attrs[.shadow] = shadow
            case .box:
                attrs[.foregroundColor] = UIColor(rgb: contrastOn(o.color))
            }
        }
        let text = NSAttributedString(string: o.text, attributes: attrs)
        let bounds = text.boundingRect(with: CGSize(width: size.width, height: .greatestFiniteMagnitude),
                                       options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil)
        let rect = CGRect(
            x: CGFloat(o.x) * size.width - bounds.width / 2,
            y: CGFloat(o.y) * size.height - bounds.height / 2,
            width: bounds.width, height: bounds.height
        )
        if !o.sticker && o.style == .box {
            let pad = fontSize * 0.35
            color.setFill()
            UIBezierPath(roundedRect: rect.insetBy(dx: -pad, dy: -pad * 0.5), cornerRadius: fontSize * 0.4).fill()
        }
        text.draw(with: rect, options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil)
    }

    /// Чёрный или белый — что читается на цвете фона.
    static func contrastOn(_ rgb: UInt32) -> UInt32 {
        let r = Double((rgb >> 16) & 0xFF)
        let g = Double((rgb >> 8) & 0xFF)
        let b = Double(rgb & 0xFF)
        return 0.299 * r + 0.587 * g + 0.114 * b > 150 ? 0x000000 : 0xFFFFFF
    }

    /// Собрать ролик в mp4 в папке вложений. progress — 0…1. nil — ошибка или отмена.
    static func export(_ edit: VideoEdit, musicURL: URL?, progress: @escaping (Double) -> Void) async -> URL? {
        guard let built = try? await build(edit, musicURL: musicURL),
              let session = AVAssetExportSession(asset: built.composition, presetName: AVAssetExportPresetHighestQuality) else { return nil }
        let out = MediaFiles.directory.appendingPathComponent("\(UUID().uuidString).mp4")
        session.outputURL = out
        session.outputFileType = .mp4
        session.shouldOptimizeForNetworkUse = true
        session.videoComposition = built.videoComposition
        session.audioMix = built.audioMix
        let watcher = Task { @MainActor in
            while !Task.isCancelled {
                progress(Double(session.progress))
                try? await Task.sleep(nanoseconds: 250_000_000)
            }
        }
        await withTaskCancellationHandler {
            await session.export()
        } onCancel: {
            session.cancelExport()
        }
        watcher.cancel()
        guard session.status == .completed else {
            try? FileManager.default.removeItem(at: out)
            return nil
        }
        await MainActor.run { progress(1) }
        return out
    }
}

extension UIColor {
    /// Цвет из 0xRRGGBB.
    convenience init(rgb: UInt32) {
        self.init(
            red: CGFloat((rgb >> 16) & 0xFF) / 255,
            green: CGFloat((rgb >> 8) & 0xFF) / 255,
            blue: CGFloat(rgb & 0xFF) / 255,
            alpha: 1
        )
    }
}
