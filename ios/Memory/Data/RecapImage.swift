import Photos
import UIKit

/// Картинка итогов 1080 × 1350 (как пост в ленте): ночное небо с
/// туманностью и звёздами, период, имя и плитки с цифрами. Её публикуют в
/// ленту, отправляют в чат или сохраняют в «Фото». Та же раскладка — в
/// RecapImage.kt.
enum RecapImage {
    static let width: CGFloat = 1080
    static let height: CGFloat = 1350

    /// scale — во сколько раз крупнее: Memory+ сохраняет в 2160 × 2700.
    static func render(_ recap: Recap, me: User, scale: Int = 1) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        // Раскладка — в точках 1080 × 1350, пикселей — в scale раз больше
        format.scale = CGFloat(max(1, scale))
        format.opaque = true
        return UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format).image { ctx in
            let c = ctx.cgContext
            let space = CGColorSpaceCreateDeviceRGB()
            // Небо
            let sky = CGGradient(colorsSpace: space, colors: [RGB(0x07081A).ui.cgColor, RGB(0x0F153A).ui.cgColor, RGB(0x2A2152).ui.cgColor] as CFArray, locations: [0, 0.5, 1])!
            c.drawLinearGradient(sky, start: .zero, end: CGPoint(x: 0, y: height), options: [])
            // Туманность
            for (x, y, r, hex, a) in [(0.2, 0.25, 0.55, 0x844CAD, 0.53), (0.85, 0.15, 0.45, 0x195699, 0.4),
                                      (0.75, 0.85, 0.6, 0x5F3F96, 0.47), (0.1, 0.9, 0.4, 0x307EC0, 0.33)] as [(CGFloat, CGFloat, CGFloat, UInt32, CGFloat)] {
                let color = RGB(hex).ui
                let blob = CGGradient(colorsSpace: space, colors: [color.withAlphaComponent(a).cgColor, color.withAlphaComponent(0).cgColor] as CFArray, locations: [0, 1])!
                let center = CGPoint(x: x * width, y: y * height)
                c.drawRadialGradient(blob, startCenter: center, startRadius: 0, endCenter: center, endRadius: r * width, options: [])
            }
            // Звёзды — одинаковые для одного периода
            var rng = SeededRandom(recap.key.unicodeScalars.reduce(0) { $0 &* 31 &+ Int($1.value) })
            for _ in 0..<160 {
                let alpha = 0.3 + rng.nextDouble() * 0.7
                let r = 1 + rng.nextDouble() * 2.6
                UIColor.white.withAlphaComponent(alpha).setFill()
                UIBezierPath(ovalIn: CGRect(x: rng.nextDouble() * width, y: rng.nextDouble() * height, width: r * 2, height: r * 2)).fill()
            }

            func draw(_ text: String, _ x: CGFloat, _ y: CGFloat, size: CGFloat, weight: UIFont.Weight = .regular, color: UIColor = .white, kern: CGFloat = 0, width w: CGFloat = 920) {
                let attrs: [NSAttributedString.Key: Any] = [
                    .font: UIFont.systemFont(ofSize: size, weight: weight), .foregroundColor: color, .kern: kern,
                ]
                (text as NSString).draw(in: CGRect(x: x, y: y, width: w, height: size * 3), withAttributes: attrs)
            }
            let lilac = RGB(0xBC92CD).ui
            let soft = RGB(0xC9C2E6).ui
            draw("MEMORY · " + t("ИТОГИ"), 80, 96, size: 30, weight: .bold, color: lilac, kern: 12)
            draw(recap.title, 80, 150, size: 92, weight: .bold, color: RGB(0xE7C8FF).ui)
            draw("\(me.name) · @\(me.handle)", 80, 272, size: 36, color: soft)

            let tiles = Array(recap.tiles.prefix(8))
            if tiles.isEmpty {
                draw(t("Отмечайте чек-ины — фильмы, книги, пробежки, места. Здесь появятся ваши цифры."), 80, 470, size: 42)
            } else {
                // Плитки по две: значок, цифра, подпись
                let gap: CGFloat = 28
                let tw = (width - 160 - gap) / 2
                let th: CGFloat = 190
                for (i, tile) in tiles.enumerated() {
                    let x = 80 + CGFloat(i % 2) * (tw + gap)
                    let y = 380 + CGFloat(i / 2) * (th + gap)
                    let rect = CGRect(x: x, y: y, width: tw, height: th)
                    let path = UIBezierPath(roundedRect: rect, cornerRadius: 36)
                    UIColor.white.withAlphaComponent(0.11).setFill()
                    path.fill()
                    UIColor.white.withAlphaComponent(0.27).setStroke()
                    path.lineWidth = 2
                    path.stroke()
                    draw(tile.emoji, x + 28, y + 26, size: 52, width: 80)
                    draw(tile.value, x + 110, y + 20, size: 64, weight: .bold, width: tw - 130)
                    draw(tile.label, x + 30, y + 104, size: 32, color: soft, width: tw - 60)
                }
            }
            if let favorite = recap.favorite {
                draw(t("Чаще всего:") + " " + favorite, 80, height - 186, size: 34, color: lilac)
            }
            draw("✦ memory", 80, height - 110, size: 28, color: UIColor.white.withAlphaComponent(0.6))
        }
    }

    /// PNG в папку вложений — для записи и сообщения.
    static func attachment(_ recap: Recap, me: User) -> Attachment? {
        guard let data = render(recap, me: me).pngData() else { return nil }
        return MediaFiles.save(data, kind: .photo, ext: "png", name: "memory-recap-\(recap.key).png")
    }

    /// Можно ли сохранять в «Фото»: в Info.plist есть объяснение для
    /// доступа к медиатеке. Без него iOS закрывает приложение при попытке.
    static var canSaveToPhotos: Bool {
        Bundle.main.object(forInfoDictionaryKey: "NSPhotoLibraryAddUsageDescription") != nil
    }

    /// В «Фото». false — нет разрешения или объяснения в Info.plist.
    static func saveToPhotos(_ recap: Recap, me: User, scale: Int = 1) async -> Bool {
        guard canSaveToPhotos else { return false }
        let status = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
        guard status == .authorized || status == .limited else { return false }
        let image = render(recap, me: me, scale: scale)
        do {
            try await PHPhotoLibrary.shared().performChanges {
                PHAssetChangeRequest.creationRequestForAsset(from: image)
            }
            return true
        } catch {
            return false
        }
    }
}

private extension RGB {
    var ui: UIColor { UIColor(red: r, green: g, blue: b, alpha: 1) }
}
