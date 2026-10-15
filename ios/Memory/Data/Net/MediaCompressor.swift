import AVFoundation
import Foundation
import ImageIO
import UniformTypeIdentifiers

/// Сжать фото или видео перед отправкой (правила — MediaPlan). Оригинал на
/// телефоне не трогаем: сжатая копия — в Caches/uploads, удаляется после
/// отправки (cleanup). Не вышло сжать — отправляем как есть.
/// То же — MediaCompressor.kt на Android.
enum MediaCompressor {
    private static var outDir: URL {
        let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        let dir = caches.appendingPathComponent("uploads", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    private static func fileSize(_ url: URL) -> Int64 {
        let attrs = try? FileManager.default.attributesOfItem(atPath: url.path)
        return (attrs?[.size] as? NSNumber)?.int64Value ?? 0
    }

    static func prepare(_ job: UploadJob, quality: MediaQuality, wifi: Bool) async -> UploadJob {
        let src = UploadFiles.url(job.path)
        guard FileManager.default.fileExists(atPath: src.path) else { return job }
        let out: URL
        if job.video {
            out = await video(src, id: job.id, quality: quality, wifi: wifi)
        } else {
            let id = job.id
            out = await Task.detached(priority: .utility) { MediaCompressor.photo(src, id: id, quality: quality, wifi: wifi) }.value
        }
        var next = job
        // Не сжимали — отправляем исходное вложение по его имени (путь к папке приложения меняется после обновления)
        next.uploadPath = out == src ? job.path : out.path
        next.size = fileSize(out)
        return next
    }

    /// Фото: ImageIO читает сразу уменьшенным (с поворотом из EXIF), JPEG нужного качества.
    private static func photo(_ src: URL, id: String, quality: MediaQuality, wifi: Bool) -> URL {
        if src.pathExtension.lowercased() == "gif" { return src }
        let plan = MediaPlan.photo(quality, wifi: wifi)
        if plan.maxSide == 0 { return src }
        guard let source = CGImageSourceCreateWithURL(src as CFURL, nil),
              let props = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any] else { return src }
        let width = (props[kCGImagePropertyPixelWidth] as? NSNumber)?.intValue ?? 0
        let height = (props[kCGImagePropertyPixelHeight] as? NSNumber)?.intValue ?? 0
        if width <= 0 || height <= 0 { return src }
        let original = fileSize(src)
        // Уже маленькое и лёгкое — не пережимаем (каждое пережатие — потеря качества)
        if max(width, height) <= plan.maxSide && original < 1_500_000 { return src }
        var options: [CFString: Any] = [:]
        options[kCGImageSourceCreateThumbnailFromImageAlways] = true
        // Поворот — в пикселях: EXIF при сжатии теряется, а фото не должно лечь на бок
        options[kCGImageSourceCreateThumbnailWithTransform] = true
        options[kCGImageSourceThumbnailMaxPixelSize] = plan.maxSide
        options[kCGImageSourceShouldCacheImmediately] = true
        guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return src }
        let out = outDir.appendingPathComponent("\(id).jpg")
        try? FileManager.default.removeItem(at: out)
        guard let dest = CGImageDestinationCreateWithURL(out as CFURL, UTType.jpeg.identifier as CFString, 1, nil) else { return src }
        var props2: [CFString: Any] = [:]
        props2[kCGImageDestinationLossyCompressionQuality] = Double(plan.jpegQuality) / 100
        CGImageDestinationAddImage(dest, image, props2 as CFDictionary)
        guard CGImageDestinationFinalize(dest) else {
            try? FileManager.default.removeItem(at: out)
            return src
        }
        // Сжатое вышло больше исходного — шлём исходное
        if fileSize(out) >= original {
            try? FileManager.default.removeItem(at: out)
            return src
        }
        return out
    }

    /// Видео: AVAssetExportSession с пресетом 720p или 480p, mp4 для сети.
    private static func video(_ src: URL, id: String, quality: MediaQuality, wifi: Bool) async -> URL {
        let plan = MediaPlan.video(quality, wifi: wifi)
        if plan.height == 0 { return src }
        let asset = AVURLAsset(url: src)
        guard let tracks = try? await asset.loadTracks(withMediaType: .video), let track = tracks.first else { return src }
        guard let natural = try? await track.load(.naturalSize) else { return src }
        let transform = (try? await track.load(.preferredTransform)) ?? .identity
        let rate = (try? await track.load(.estimatedDataRate)) ?? 0
        // Снято вертикально — стороны на экране меняются местами
        let shown = CGRect(origin: .zero, size: natural).applying(transform)
        let width = Int(abs(shown.width).rounded())
        let height = Int(abs(shown.height).rounded())
        let bitrate = Int(rate)
        if width <= 0 || height <= 0 || !MediaPlan.needsVideoWork(width, height, bitrate: bitrate, plan) { return src }
        let preset = plan.height <= 480 ? AVAssetExportPreset640x480 : AVAssetExportPreset1280x720
        guard let export = AVAssetExportSession(asset: asset, presetName: preset) else { return src }
        let out = outDir.appendingPathComponent("\(id).mp4")
        try? FileManager.default.removeItem(at: out)
        export.outputURL = out
        export.outputFileType = .mp4
        export.shouldOptimizeForNetworkUse = true
        await withCheckedContinuation { (done: CheckedContinuation<Void, Never>) in
            export.exportAsynchronously { done.resume() }
        }
        let size = fileSize(out)
        if export.status == .completed && size > 0 && size < fileSize(src) { return out }
        try? FileManager.default.removeItem(at: out)
        return src
    }

    /// Сжатая копия больше не нужна — запись ушла.
    static func cleanup(_ job: UploadJob) {
        if job.uploadPath.isEmpty || job.uploadPath == job.path { return }
        try? FileManager.default.removeItem(at: UploadFiles.url(job.uploadPath))
    }
}
