import CoreGraphics
import CoreText
import UIKit

/// Слово из кристального стекла: перламутровое тело (розовый, сирень,
/// голубой, бледное золото), косые грани — светлые и тёмные полосы, как
/// сколы на кристалле, светлый внутренний кант, тонкий контур и несколько
/// бликов-звёздочек. Рисуется один раз при запуске заставки. Перенос
/// CrystalWord.kt.
///
/// Все размеры — от размера шрифта и в пикселях экрана, поэтому кристалл
/// одинаковый на любом iPhone.
enum CrystalWord {
    /// Перламутр: цвета переливаются по диагонали слова.
    static let iridescent: [UInt32] = [
        0xFF9EDE, // розовый
        0xC6A2FF, // сирень
        0x8CC4FF, // голубой
        0xFFF4FF, // жемчужный блик
        0xFFDB8F, // золото
        0xD08CFF, // орхидея
        0x7AA8FF, // небесный
    ]

    static func cgColor(_ hex: UInt32, _ alpha: CGFloat = 1) -> CGColor {
        let c = RGB(hex)
        return CGColor(srgbRed: c.r, green: c.g, blue: c.b, alpha: alpha)
    }

    /// Контур слова: глифы шрифта, сведённые в один путь. y — вниз,
    /// как на экране; базовая линия — baseline, слово по центру centerX.
    static func wordPath(_ word: String, font: UIFont, kern: CGFloat, centerX: CGFloat, baseline: CGFloat) -> CGPath {
        let ctFont = CTFontCreateWithName(font.fontName as CFString, font.pointSize, nil)
        let attributes: [NSAttributedString.Key: Any] = [
            NSAttributedString.Key(kCTFontAttributeName as String): ctFont,
            NSAttributedString.Key(kCTKernAttributeName as String): kern,
        ]
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: word, attributes: attributes) as CFAttributedString)
        let raw = CGMutablePath()
        let runs = CTLineGetGlyphRuns(line) as? [CTRun] ?? []
        for run in runs {
            let count = CTRunGetGlyphCount(run)
            if count == 0 { continue }
            var glyphs = [CGGlyph](repeating: 0, count: count)
            var positions = [CGPoint](repeating: .zero, count: count)
            CTRunGetGlyphs(run, CFRange(location: 0, length: count), &glyphs)
            CTRunGetPositions(run, CFRange(location: 0, length: count), &positions)
            for i in 0..<count {
                // Глиф в шрифте — y вверх; переворачиваем под экран
                var transform = CGAffineTransform(translationX: positions[i].x, y: baseline).scaledBy(x: 1, y: -1)
                if let glyph = CTFontCreatePathForGlyph(ctFont, glyphs[i], &transform) {
                    raw.addPath(glyph)
                }
            }
        }
        // По центру: сдвиг по настоящим границам букв, без хвостовой разрядки
        let bounds = raw.boundingBoxOfPath
        var shift = CGAffineTransform(translationX: centerX - bounds.midX, y: 0)
        let centered = raw.copy(using: &shift) ?? raw
        // Контуры букв в шрифте могут перекрываться — объединяем, иначе
        // кант и контур рисуются и по внутренним швам глифа
        return centered.normalized(using: .winding)
    }

    /// Ширина слова по контурам букв при данном размере шрифта.
    static func wordWidth(_ word: String, font: UIFont, kern: CGFloat) -> CGFloat {
        wordPath(word, font: font, kern: kern, centerX: 0, baseline: 0).boundingBoxOfPath.width
    }

    struct Art {
        /// Кристальное слово с ореолом, размер width × height пикселей.
        let crystal: CGImage
        let bounds: CGRect
    }

    /// Нарисовать кристалл. path и размеры — в пикселях; ts — размер шрифта.
    static func render(path: CGPath, ts: CGFloat, width: Int, height: Int) -> Art? {
        guard width > 0, height > 0 else { return nil }
        let space = CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB()
        guard let c = CGContext(
            data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
            space: space, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return nil }
        // Координаты как на экране: начало сверху слева
        c.translateBy(x: 0, y: CGFloat(height))
        c.scaleBy(x: 1, y: -1)
        c.setShouldAntialias(true)
        let b = path.boundingBoxOfPath

        // 1. Ореол: свет, рассеянный стеклом
        c.saveGState()
        c.setShadow(offset: .zero, blur: ts * 0.22, color: cgColor(0xC49BFF, 0.75))
        c.addPath(path)
        c.setFillColor(cgColor(0xC49BFF, 0.75))
        c.fillPath()
        c.restoreGState()

        c.saveGState()
        c.addPath(path)
        c.clip()

        // 2. Тело — перламутровый градиент по диагонали, чуть прозрачный.
        // На Android градиент «зеркалится» за пределами отрезка — здесь
        // цвета повторены туда-обратно на всю длину слова
        let dx = b.height * 2.2
        let dy = b.height
        let len2 = dx * dx + dy * dy
        let reach = ((b.width + b.height) * dx + b.height * dy) / len2
        let reps = max(1, Int(ceil(reach)) + 1)
        var colors: [CGColor] = []
        for r in 0..<reps {
            let seq = r % 2 == 0 ? iridescent : Array(iridescent.reversed())
            let part = r == 0 ? seq : Array(seq.dropFirst())
            colors += part.map { cgColor($0, 250.0 / 255.0) }
        }
        if let gradient = CGGradient(colorsSpace: space, colors: colors as CFArray, locations: nil) {
            let start = CGPoint(x: b.minX, y: b.minY)
            let end = CGPoint(x: b.minX + dx * CGFloat(reps), y: b.minY + dy * CGFloat(reps))
            c.drawLinearGradient(gradient, start: start, end: end, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
        }

        // 3. Грани: косые полосы двух наклонов — светлые сколы и тени
        var rnd = SeededRandom(5)
        for slant in [0.55, -0.35] as [CGFloat] {
            var fx = b.minX - b.height
            var light = true
            while fx < b.maxX + b.height {
                let w = ts * (0.05 + CGFloat(rnd.nextDouble()) * 0.16)
                var alpha: CGFloat = light ? 0x60 / 255.0 : 0x3F / 255.0
                if slant < 0 { alpha /= 2 }
                c.setFillColor(light ? cgColor(0xFFFFFF, alpha) : cgColor(0x4A2F9A, alpha))
                let shift = b.height * slant
                c.beginPath()
                c.move(to: CGPoint(x: fx, y: b.minY))
                c.addLine(to: CGPoint(x: fx + w, y: b.minY))
                c.addLine(to: CGPoint(x: fx + w + shift, y: b.maxY))
                c.addLine(to: CGPoint(x: fx + shift, y: b.maxY))
                c.closePath()
                c.fillPath()
                fx += w + ts * (0.02 + CGFloat(rnd.nextDouble()) * 0.12)
                light.toggle()
            }
        }

        // 4. Внутренний кант: светлая кромка вдоль края и тень за ней
        c.setLineJoin(.round)
        c.addPath(path)
        c.setLineWidth(ts * 0.12)
        c.setStrokeColor(cgColor(0xFFFFFF, 0x40 / 255.0))
        c.strokePath()
        c.addPath(path)
        c.setLineWidth(ts * 0.05)
        c.setStrokeColor(cgColor(0x8A6FD8, 0x66 / 255.0))
        c.strokePath()
        c.restoreGState()

        // 5. Контур стекла
        c.addPath(path)
        c.setLineWidth(ts * 0.014 + 1.2)
        c.setStrokeColor(cgColor(0xFFFFFF))
        c.strokePath()

        // 6. Блики-звёздочки на гранях
        c.setStrokeColor(cgColor(0xFFFFFF))
        c.setFillColor(cgColor(0xFFFFFF))
        c.setLineWidth(ts * 0.012 + 0.8)
        c.setLineCap(.round)
        for _ in 0..<5 {
            let sx = b.minX + CGFloat(rnd.nextDouble()) * b.width
            let sy = b.minY + CGFloat(rnd.nextDouble()) * b.height
            let r = ts * (0.04 + CGFloat(rnd.nextDouble()) * 0.05)
            c.beginPath()
            c.move(to: CGPoint(x: sx - r, y: sy))
            c.addLine(to: CGPoint(x: sx + r, y: sy))
            c.move(to: CGPoint(x: sx, y: sy - r))
            c.addLine(to: CGPoint(x: sx, y: sy + r))
            c.strokePath()
            c.fillEllipse(in: CGRect(x: sx - r * 0.18, y: sy - r * 0.18, width: r * 0.36, height: r * 0.36))
        }

        guard let image = c.makeImage() else { return nil }
        return Art(crystal: image, bounds: b)
    }
}
