import AVFoundation
import Foundation
import ImageIO
import UIKit
import UniformTypeIdentifiers

/// Фото, видео и документы на iPhone.
///
/// Пока у Memory нет сервера, вложение копируется в папку приложения
/// (Application Support/media): так оно переживает перезапуск и не
/// пропадает, если человек удалит оригинал из галереи. Другие приложения
/// эту папку не видят. В резервную копию iCloud папка не попадает —
/// видео быстро съели бы место.
enum MediaFiles {
    /// Самый большой файл, который принимаем: 200 МБ.
    static let maxBytes: Int64 = 200 * 1024 * 1024

    static var directory: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        var dir = base.appendingPathComponent("media", isDirectory: true)
        if !FileManager.default.fileExists(atPath: dir.path) {
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            var values = URLResourceValues()
            values.isExcludedFromBackup = true
            try? dir.setResourceValues(values)
        }
        return dir
    }

    static func url(_ attachment: Attachment) -> URL {
        directory.appendingPathComponent(attachment.fileName)
    }

    /// Сохранить данные (фото из галереи или камеры) как вложение.
    static func save(_ data: Data, kind: MediaKind, ext: String, name: String = "") -> Attachment? {
        guard Int64(data.count) <= maxBytes else { return nil }
        let id = UUID().uuidString
        let fileName = "\(id).\(ext)"
        do {
            try data.write(to: directory.appendingPathComponent(fileName), options: .atomic)
        } catch {
            return nil
        }
        return Attachment(id: id, kind: kind, fileName: fileName, name: name.isEmpty ? fileName : name, size: Int64(data.count))
    }

    /// Скопировать файл (видео, документ) в папку вложений. kind = nil —
    /// определить по типу файла. Для документов из «Файлов» нужен доступ
    /// к защищённому адресу — он открывается на время копирования.
    static func importFile(at source: URL, kind: MediaKind? = nil) -> Attachment? {
        let scoped = source.startAccessingSecurityScopedResource()
        defer { if scoped { source.stopAccessingSecurityScopedResource() } }
        let size = fileSize(source)
        guard size <= maxBytes else { return nil }
        let type = kind ?? detectKind(source)
        let ext = source.pathExtension.isEmpty ? (type == .video ? "mov" : "bin") : source.pathExtension
        let id = UUID().uuidString
        let fileName = "\(id).\(ext.lowercased())"
        let target = directory.appendingPathComponent(fileName)
        do {
            try FileManager.default.copyItem(at: source, to: target)
        } catch {
            return nil
        }
        let copied = fileSize(target)
        let actualSize = copied > 0 ? copied : size
        return Attachment(id: id, kind: type, fileName: fileName, name: source.lastPathComponent, size: actualSize)
    }

    private static func fileSize(_ url: URL) -> Int64 {
        guard let values = try? url.resourceValues(forKeys: [.fileSizeKey]), let size = values.fileSize else { return 0 }
        return Int64(size)
    }

    private static func detectKind(_ url: URL) -> MediaKind {
        guard let type = UTType(filenameExtension: url.pathExtension) else { return .document }
        if type.conforms(to: .image) { return .photo }
        if type.conforms(to: .movie) || type.conforms(to: .video) { return .video }
        return .document
    }

    /// Удалить копию вложения (из удалённого сообщения или черновика).
    static func delete(_ attachment: Attachment) {
        try? FileManager.default.removeItem(at: url(attachment))
    }

    /// Уменьшенная картинка для ленты и чата: фото читается сразу
    /// уменьшенным (полный снимок занял бы десятки мегабайт памяти),
    /// у видео — первый кадр. Вызывать не в главном потоке.
    static func thumbnail(_ attachment: Attachment, maxPixel: CGFloat) -> UIImage? {
        let fileURL = url(attachment)
        switch attachment.kind {
        case .photo:
            guard let source = CGImageSourceCreateWithURL(fileURL as CFURL, nil) else { return nil }
            let options: [CFString: Any] = [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceThumbnailMaxPixelSize: maxPixel,
            ]
            guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return nil }
            return UIImage(cgImage: image)
        case .video:
            let generator = AVAssetImageGenerator(asset: AVURLAsset(url: fileURL))
            generator.appliesPreferredTrackTransform = true
            generator.maximumSize = CGSize(width: maxPixel, height: maxPixel)
            guard let image = try? generator.copyCGImage(at: .zero, actualTime: nil) else { return nil }
            return UIImage(cgImage: image)
        case .document:
            return nil
        }
    }
}

/// «2,4 МБ» — размер документа.
func sizeTitle(_ bytes: Int64) -> String {
    let en = L10n.shared.english
    let locale = Locale(identifier: en ? "en_US" : "ru_RU")
    let kb = 1024.0
    let value = Double(bytes)
    if value >= kb * kb * kb { return String(format: en ? "%.1f GB" : "%.1f ГБ", locale: locale, value / (kb * kb * kb)) }
    if value >= kb * kb { return String(format: en ? "%.1f MB" : "%.1f МБ", locale: locale, value / (kb * kb)) }
    if value >= kb { return en ? "\(bytes / 1024) KB" : "\(bytes / 1024) КБ" }
    return en ? "\(bytes) B" : "\(bytes) Б"
}
