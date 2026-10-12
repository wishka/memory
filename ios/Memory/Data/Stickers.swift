import UIKit

/// Свой значок, смайл или стикер: прозрачная картинка PNG 512 × 512 в
/// папке вложений. Создаётся в «Студии» профиля — из фото, рисунком,
/// в конструкторе смайлов или как значок с текстом.
struct Sticker: Identifiable, Equatable, Codable {
    let id: String
    let fileName: String
    let kind: StickerKind
    var createdAt: Date? = nil

    var url: URL { MediaFiles.directory.appendingPathComponent(fileName) }
}

enum StickerKind: String, Codable, CaseIterable {
    case photo = "Из фото"
    case drawing = "Рисунок"
    case smiley = "Смайл"
    case badge = "Значок"

    var title: String { t(rawValue) }
}

/// Форма стикера и фона значка.
enum StickerShape: String, CaseIterable {
    case circle = "Круг"
    case heart = "Сердце"
    case crystal = "Кристалл"
    case star = "Звезда"
    case square = "Квадрат"

    var title: String { t(rawValue) }
}

/// Линия рисунка: точки — доли стороны 0…1. eraser — стирает нарисованное.
/// Codable — штрихи общего рисунка во «Вместе» сохраняются на телефоне.
struct DrawStroke: Equatable, Codable {
    var points: [CGPoint]
    var color: UInt32
    var width: CGFloat
    var eraser = false
}

enum SmileEyes: String, CaseIterable {
    case dots = "Точки", happy = "Радость", hearts = "Сердечки", stars = "Звёзды"
    case wink = "Подмигивание", sleepy = "Сонные", crystal = "Кристаллы"
    var title: String { t(rawValue) }
}

enum SmileMouth: String, CaseIterable {
    case smile = "Улыбка", grin = "Смех", wow = "Ого", tongue = "Язык", flat = "Ровно", sad = "Грусть"
    var title: String { t(rawValue) }
}

enum SmileExtra: String, CaseIterable {
    case none = "Ничего", blush = "Румянец", crown = "Корона", halo = "Нимб", glasses = "Очки", tear = "Слеза"
    var title: String { t(rawValue) }
}

struct SmileyParts: Equatable {
    var face: StickerShape = .circle
    var color: UInt32 = stickerColors[2]
    var eyes: SmileEyes = .happy
    var mouth: SmileMouth = .smile
    var extra: SmileExtra = .blush
}

/// Значок: текст или эмодзи на кристальной подложке.
struct BadgeParts: Equatable {
    var text = "✦"
    var shape: StickerShape = .crystal
    var gradient = 0
    var glass = true
}

/// Фото-стикер: обрезка формой, обводка, подпись. zoom ≥ 1, сдвиг — доли стороны.
struct PhotoParts: Equatable {
    var shape: StickerShape = .circle
    var zoom: CGFloat = 1
    var dx: CGFloat = 0
    var dy: CGFloat = 0
    var outline = 2
    var outlineColor: UInt32 = 0xFFFFFF
    var caption = ""
    var captionColor: UInt32 = 0xFFFFFF
}

/// Цвета студии — те же, что на Android (0xRRGGBB).
let stickerColors: [UInt32] = [0xFFFFFF, 0xF3EEFF, 0xFFD86B, 0xFF9EDE, 0xBC92CD, 0x8CC4FF, 0x307EC0, 0x7BE0B0, 0xFF8A65, 0x0B0820]

/// Подложки значков — пары цветов градиента.
let badgeGradients: [(UInt32, UInt32)] = [
    (0x844CAD, 0x195699), (0xFF9EDE, 0x844CAD), (0x8CC4FF, 0x5F3F96),
    (0xFFD86B, 0xFF8A65), (0x7BE0B0, 0x307EC0), (0x3C2C59, 0x07081A),
]

/// Рисование стикеров. Одни и те же функции дают предпросмотр в студии и
/// итоговую картинку. Те же правила — в Stickers.kt на Android.
enum StickerArt {
    static let size: CGFloat = 512

    /// Контур формы в квадрате стороной s с отступом inset.
    static func shapePath(_ shape: StickerShape, _ s: CGFloat, inset: CGFloat = 0) -> UIBezierPath {
        let a = inset
        let w = s - inset * 2
        func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: a + w * x, y: a + w * y) }
        let path = UIBezierPath()
        switch shape {
        case .circle:
            return UIBezierPath(ovalIn: CGRect(x: a, y: a, width: w, height: w))
        case .square:
            return UIBezierPath(roundedRect: CGRect(x: a, y: a, width: w, height: w), cornerRadius: w * 0.22)
        case .heart:
            path.move(to: p(0.5, 0.92))
            path.addCurve(to: p(0.16, 0.18), controlPoint1: p(0.08, 0.64), controlPoint2: p(0.0, 0.34))
            path.addCurve(to: p(0.5, 0.26), controlPoint1: p(0.30, 0.04), controlPoint2: p(0.46, 0.10))
            path.addCurve(to: p(0.84, 0.18), controlPoint1: p(0.54, 0.10), controlPoint2: p(0.70, 0.04))
            path.addCurve(to: p(0.5, 0.92), controlPoint1: p(1.0, 0.34), controlPoint2: p(0.92, 0.64))
            path.close()
        case .crystal:
            // Вытянутый кристалл, как в PRISM и в шапке ленты
            path.move(to: p(0.5, 0.02))
            path.addLine(to: p(0.86, 0.5))
            path.addLine(to: p(0.5, 0.98))
            path.addLine(to: p(0.14, 0.5))
            path.close()
        case .star:
            for i in 0..<10 {
                let r = i % 2 == 0 ? w / 2 : w * 0.22
                let ang = CGFloat(-90 + i * 36) * .pi / 180
                let pt = CGPoint(x: s / 2 + r * cos(ang), y: s / 2 + w * 0.04 + r * sin(ang))
                if i == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
            }
            path.close()
        }
        return path
    }

    /// Дуга эллипса в рамке rect: углы в градусах от «трёх часов» по часовой — как на Android.
    static func arc(_ rect: CGRect, _ start: CGFloat, _ sweep: CGFloat) -> UIBezierPath {
        let path = UIBezierPath(arcCenter: .zero, radius: 1, startAngle: start * .pi / 180, endAngle: (start + sweep) * .pi / 180, clockwise: true)
        path.apply(CGAffineTransform(scaleX: rect.width / 2, y: rect.height / 2))
        path.apply(CGAffineTransform(translationX: rect.midX, y: rect.midY))
        return path
    }

    private static func render(_ draw: (CGContext) -> Void) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        return UIGraphicsImageRenderer(size: CGSize(width: size, height: size), format: format).image { ctx in
            draw(ctx.cgContext)
        }
    }

    private static func color(_ rgb: UInt32, _ alpha: CGFloat = 1) -> UIColor {
        UIColor(rgb: rgb).withAlphaComponent(alpha)
    }

    private static func stroke(_ path: UIBezierPath, _ c: UIColor, _ width: CGFloat) {
        c.setStroke()
        path.lineWidth = width
        path.lineCapStyle = .round
        path.lineJoinStyle = .round
        path.stroke()
    }

    /// Подпись с тёмной обводкой — читается на любом фоне.
    private static func caption(_ text: String, _ rgb: UInt32, centerY: CGFloat, fontSize: CGFloat) {
        let trimmed = text.trimmingCharacters(in: .whitespaces)
        if trimmed.isEmpty { return }
        var font = UIFont.systemFont(ofSize: fontSize, weight: .heavy)
        while (trimmed as NSString).size(withAttributes: [.font: font]).width > size * 0.92 && font.pointSize > 24 {
            font = font.withSize(font.pointSize - 4)
        }
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = .center
        let outline: [NSAttributedString.Key: Any] = [
            .font: font, .paragraphStyle: paragraph,
            .strokeColor: color(0x07081A, 0.8), .strokeWidth: 16,
        ]
        let fill: [NSAttributedString.Key: Any] = [.font: font, .paragraphStyle: paragraph, .foregroundColor: color(rgb)]
        let h = font.lineHeight
        let rect = CGRect(x: 0, y: centerY - h / 2, width: size, height: h)
        (trimmed as NSString).draw(in: rect, withAttributes: outline)
        (trimmed as NSString).draw(in: rect, withAttributes: fill)
    }

    static func photo(_ source: UIImage, _ p: PhotoParts) -> UIImage {
        render { ctx in
            let s = size
            let path = shapePath(p.shape, s, inset: s * 0.05)
            ctx.saveGState()
            path.addClip()
            let k = max(s / source.size.width, s / source.size.height) * p.zoom
            let w = source.size.width * k
            let h = source.size.height * k
            source.draw(in: CGRect(x: (s - w) / 2 + p.dx * s, y: (s - h) / 2 + p.dy * s, width: w, height: h))
            ctx.restoreGState()
            if p.outline > 0 { stroke(path, color(p.outlineColor), s * 0.018 * CGFloat(p.outline)) }
            caption(p.caption, p.captionColor, centerY: s * 0.86, fontSize: s * 0.12)
        }
    }

    static func drawing(_ strokes: [DrawStroke]) -> UIImage {
        render { ctx in
            let s = size
            for st in strokes {
                ctx.setBlendMode(st.eraser ? .clear : .normal)
                if st.points.count == 1, let pt = st.points.first {
                    color(st.color).setFill()
                    let r = st.width * s / 2
                    UIBezierPath(ovalIn: CGRect(x: pt.x * s - r, y: pt.y * s - r, width: r * 2, height: r * 2)).fill()
                    continue
                }
                let path = UIBezierPath()
                for (i, pt) in st.points.enumerated() {
                    let q = CGPoint(x: pt.x * s, y: pt.y * s)
                    if i == 0 { path.move(to: q) } else { path.addLine(to: q) }
                }
                stroke(path, color(st.color), st.width * s)
            }
            ctx.setBlendMode(.normal)
        }
    }

    static func smiley(_ p: SmileyParts) -> UIImage {
        render { ctx in
            let s = size
            let ink = color(0x2A1840)
            let face = shapePath(p.face, s, inset: s * 0.08)
            color(p.color).setFill()
            face.fill()
            ctx.saveGState()
            face.addClip()
            UIColor.white.withAlphaComponent(0.27).setFill()
            UIBezierPath(ovalIn: CGRect(x: s * 0.16, y: s * 0.08, width: s * 0.44, height: s * 0.44)).fill()
            ctx.restoreGState()
            stroke(face, ink, s * 0.03)

            let ey = p.face == .heart ? s * 0.42 : s * 0.44
            let lx = s * 0.37
            let rx = s * 0.63
            let line = s * 0.035
            func dot(_ cx: CGFloat, _ r: CGFloat) {
                ink.setFill()
                UIBezierPath(ovalIn: CGRect(x: cx - r, y: ey - r, width: r * 2, height: r * 2)).fill()
            }
            func mini(_ shape: StickerShape, _ cx: CGFloat, _ r: CGFloat, _ c: UInt32) {
                let mp = shapePath(shape, r * 2)
                mp.apply(CGAffineTransform(translationX: cx - r, y: ey - r))
                color(c).setFill()
                mp.fill()
            }
            func happy(_ cx: CGFloat) { stroke(arc(CGRect(x: cx - s * 0.06, y: ey - s * 0.03, width: s * 0.12, height: s * 0.12), 200, 140), ink, line) }
            switch p.eyes {
            case .dots: dot(lx, s * 0.045); dot(rx, s * 0.045)
            case .happy: happy(lx); happy(rx)
            case .hearts: mini(.heart, lx, s * 0.07, 0xE8457A); mini(.heart, rx, s * 0.07, 0xE8457A)
            case .stars: mini(.star, lx, s * 0.075, 0xFFC23D); mini(.star, rx, s * 0.075, 0xFFC23D)
            case .wink: dot(lx, s * 0.045); happy(rx)
            case .sleepy:
                for cx in [lx, rx] {
                    stroke(arc(CGRect(x: cx - s * 0.06, y: ey - s * 0.06, width: s * 0.12, height: s * 0.10), 20, 140), ink, line)
                }
            case .crystal:
                for cx in [lx, rx] {
                    let d = UIBezierPath()
                    d.move(to: CGPoint(x: cx, y: ey - s * 0.07))
                    d.addLine(to: CGPoint(x: cx + s * 0.045, y: ey))
                    d.addLine(to: CGPoint(x: cx, y: ey + s * 0.07))
                    d.addLine(to: CGPoint(x: cx - s * 0.045, y: ey))
                    d.close()
                    color(0x6D4BD8).setFill()
                    d.fill()
                    stroke(d, UIColor.white.withAlphaComponent(0.63), s * 0.008)
                }
            }

            let my = s * 0.64
            switch p.mouth {
            case .smile:
                stroke(arc(CGRect(x: s * 0.36, y: my - s * 0.10, width: s * 0.28, height: s * 0.16), 20, 140), ink, line)
            case .grin:
                let m = UIBezierPath()
                m.move(to: CGPoint(x: s * 0.34, y: my - s * 0.03))
                m.addLine(to: CGPoint(x: s * 0.66, y: my - s * 0.03))
                m.addQuadCurve(to: CGPoint(x: s * 0.5, y: my + s * 0.14), controlPoint: CGPoint(x: s * 0.64, y: my + s * 0.14))
                m.addQuadCurve(to: CGPoint(x: s * 0.34, y: my - s * 0.03), controlPoint: CGPoint(x: s * 0.36, y: my + s * 0.14))
                m.close()
                ink.setFill()
                m.fill()
                ctx.saveGState()
                m.addClip()
                color(0xE8457A).setFill()
                UIBezierPath(ovalIn: CGRect(x: s * 0.43, y: my + s * 0.08, width: s * 0.14, height: s * 0.14)).fill()
                ctx.restoreGState()
            case .wow:
                ink.setFill()
                UIBezierPath(ovalIn: CGRect(x: s * 0.45, y: my - s * 0.04, width: s * 0.10, height: s * 0.13)).fill()
            case .tongue:
                stroke(arc(CGRect(x: s * 0.36, y: my - s * 0.10, width: s * 0.28, height: s * 0.16), 20, 140), ink, line)
                color(0xE8457A).setFill()
                UIBezierPath(roundedRect: CGRect(x: s * 0.47, y: my + s * 0.03, width: s * 0.10, height: s * 0.10), cornerRadius: s * 0.05).fill()
            case .flat:
                let f = UIBezierPath()
                f.move(to: CGPoint(x: s * 0.39, y: my))
                f.addLine(to: CGPoint(x: s * 0.61, y: my))
                stroke(f, ink, line)
            case .sad:
                stroke(arc(CGRect(x: s * 0.38, y: my, width: s * 0.24, height: s * 0.14), 200, 140), ink, line)
            }

            switch p.extra {
            case .none: break
            case .blush:
                color(0xFF7FA8, 0.43).setFill()
                UIBezierPath(ovalIn: CGRect(x: s * 0.20, y: s * 0.52, width: s * 0.12, height: s * 0.07)).fill()
                UIBezierPath(ovalIn: CGRect(x: s * 0.68, y: s * 0.52, width: s * 0.12, height: s * 0.07)).fill()
            case .crown:
                let c = UIBezierPath()
                let pts: [(CGFloat, CGFloat)] = [(0.32, 0.20), (0.36, 0.04), (0.44, 0.14), (0.5, 0.02), (0.56, 0.14), (0.64, 0.04), (0.68, 0.20)]
                for (i, q) in pts.enumerated() {
                    let pt = CGPoint(x: s * q.0, y: s * q.1)
                    if i == 0 { c.move(to: pt) } else { c.addLine(to: pt) }
                }
                c.close()
                color(0xFFC23D).setFill()
                c.fill()
                stroke(c, ink, s * 0.018)
            case .halo:
                stroke(UIBezierPath(ovalIn: CGRect(x: s * 0.30, y: s * 0.01, width: s * 0.40, height: s * 0.10)), color(0xFFD86B), s * 0.03)
            case .glasses:
                let r = s * 0.10
                stroke(UIBezierPath(ovalIn: CGRect(x: lx - r, y: ey - r, width: r * 2, height: r * 2)), ink, s * 0.025)
                stroke(UIBezierPath(ovalIn: CGRect(x: rx - r, y: ey - r, width: r * 2, height: r * 2)), ink, s * 0.025)
                let bridge = UIBezierPath()
                bridge.move(to: CGPoint(x: lx + r, y: ey))
                bridge.addLine(to: CGPoint(x: rx - r, y: ey))
                stroke(bridge, ink, s * 0.025)
            case .tear:
                let tp = UIBezierPath()
                tp.move(to: CGPoint(x: rx + s * 0.02, y: ey + s * 0.06))
                tp.addQuadCurve(to: CGPoint(x: rx + s * 0.02, y: ey + s * 0.19), controlPoint: CGPoint(x: rx + s * 0.08, y: ey + s * 0.16))
                tp.addQuadCurve(to: CGPoint(x: rx + s * 0.02, y: ey + s * 0.06), controlPoint: CGPoint(x: rx - s * 0.04, y: ey + s * 0.16))
                tp.close()
                color(0x8CC4FF).setFill()
                tp.fill()
            }
        }
    }

    static func badge(_ p: BadgeParts) -> UIImage {
        render { ctx in
            let s = size
            let path = shapePath(p.shape, s, inset: s * 0.06)
            let (c1, c2) = badgeGradients[min(max(0, p.gradient), badgeGradients.count - 1)]
            ctx.saveGState()
            path.addClip()
            let colors = [color(c1).cgColor, color(c2).cgColor] as CFArray
            if let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 1]) {
                ctx.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: s, y: s), options: [])
            }
            if p.glass {
                // Кристальное стекло: блик сверху и светлые грани, как у слова MEMORY
                let shine = [UIColor.white.withAlphaComponent(0.4).cgColor, UIColor.white.withAlphaComponent(0).cgColor] as CFArray
                if let g = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: shine, locations: [0, 1]) {
                    ctx.drawLinearGradient(g, start: .zero, end: CGPoint(x: 0, y: s * 0.46), options: [])
                }
                if p.shape == .crystal {
                    let facets = UIBezierPath()
                    facets.move(to: CGPoint(x: s * 0.19, y: s * 0.5)); facets.addLine(to: CGPoint(x: s * 0.81, y: s * 0.5))
                    facets.move(to: CGPoint(x: s * 0.5, y: s * 0.07)); facets.addLine(to: CGPoint(x: s * 0.56, y: s * 0.5))
                    facets.addLine(to: CGPoint(x: s * 0.5, y: s * 0.93))
                    stroke(facets, UIColor.white.withAlphaComponent(0.47), s * 0.008)
                }
            }
            ctx.restoreGState()
            if p.glass { stroke(path, UIColor.white.withAlphaComponent(0.67), s * 0.016) }

            let text = String(p.text.trimmingCharacters(in: .whitespaces).prefix(8))
            if !text.isEmpty {
                let target: CGFloat
                switch text.count {
                case 1: target = s * 0.42
                case 2: target = s * 0.32
                case 3: target = s * 0.26
                default: target = s * 0.20
                }
                var font = UIFont.systemFont(ofSize: target, weight: .heavy)
                while (text as NSString).size(withAttributes: [.font: font]).width > s * 0.62 && font.pointSize > 20 {
                    font = font.withSize(font.pointSize - 4)
                }
                let shadow = NSShadow()
                shadow.shadowColor = UIColor.black.withAlphaComponent(0.4)
                shadow.shadowBlurRadius = s * 0.02
                shadow.shadowOffset = CGSize(width: 0, height: s * 0.008)
                let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: UIColor.white, .shadow: shadow]
                let sz = (text as NSString).size(withAttributes: attrs)
                (text as NSString).draw(at: CGPoint(x: (s - sz.width) / 2, y: (s - sz.height) / 2), withAttributes: attrs)
            }
        }
    }

    /// Сохранить картинку стикером в папку вложений.
    static func save(_ image: UIImage, kind: StickerKind) -> Sticker? {
        guard let data = image.pngData() else { return nil }
        let id = UUID().uuidString
        let fileName = "sticker-\(id).png"
        do {
            try data.write(to: MediaFiles.directory.appendingPathComponent(fileName), options: .atomic)
        } catch {
            return nil
        }
        return Sticker(id: id, fileName: fileName, kind: kind, createdAt: Date())
    }
}
