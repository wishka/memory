import CoreImage
import CoreImage.CIFilterBuiltins
import UIKit

/// Живые стикеры, как в iOS 17: эффект запекается в картинку (контур,
/// комикс, объём, блеск), а движение — играет при показе (прыжок, пульс,
/// покачивание…). Покадровый рисунок — несколько PNG по кругу. Стикеры
/// собираются в коллекции. Те же эффекты и движения — в StickerFx.kt.
enum StickerEffect: String, CaseIterable, Identifiable {
    case none = ""
    case outline, comic, puffy, shiny

    var id: String { rawValue }

    private var ru: String {
        switch self {
        case .none: return "Без эффекта"
        case .outline: return "Контур"
        case .comic: return "Комикс"
        case .puffy: return "Объём"
        case .shiny: return "Блеск"
        }
    }

    var title: String { t(ru) }
}

enum StickerAnim: String, CaseIterable, Identifiable {
    case none = ""
    case bounce, pulse, wiggle, spin
    case float = "float"
    case shimmer

    var id: String { rawValue }

    private var ru: String {
        switch self {
        case .none: return "Без движения"
        case .bounce: return "Прыжок"
        case .pulse: return "Пульс"
        case .wiggle: return "Покачивание"
        case .spin: return "Вращение"
        case .float: return "Парение"
        case .shimmer: return "Мерцание"
        }
    }

    var title: String { t(ru) }

    static func of(_ id: String?) -> StickerAnim { allCases.first { $0.rawValue == id } ?? StickerAnim.none }

    /// Положение в момент phase (0…1 — один цикл). Одна формула для Android и iPhone.
    func pose(_ phase: Double) -> StickerPose {
        let a = phase * 2 * Double.pi
        switch self {
        case .none: return StickerPose()
        case .bounce: return StickerPose(dy: -0.08 * abs(sin(a / 2)), scale: 1 + 0.03 * abs(sin(a / 2)))
        case .pulse: return StickerPose(scale: 1 + 0.08 * sin(a))
        case .wiggle: return StickerPose(rotation: 9 * sin(a * 2))
        case .spin: return StickerPose(rotation: 360 * phase)
        case .float: return StickerPose(dy: 0.04 * sin(a), rotation: 4 * cos(a))
        case .shimmer: return StickerPose(shine: phase)
        }
    }

    /// Сколько длится цикл движения, мс.
    var periodMs: Int {
        switch self {
        case .spin: return 3200
        case .shimmer: return 1800
        case .float: return 2600
        default: return 1200
        }
    }
}

/// Положение живого стикера: сдвиг по вертикали (доли размера), масштаб,
/// поворот в градусах, сдвиг блика 0…1 (-1 — блика нет).
struct StickerPose: Equatable {
    var dy: Double = 0
    var scale: Double = 1
    var rotation: Double = 0
    var shine: Double = -1
}

/// Коллекция стикеров — как наборы в iOS: имя и свои стикеры (Sticker.packId).
struct StickerPack: Identifiable, Equatable, Codable {
    let id: String
    var name: String
}

enum StickerFx {
    private static let s = StickerArt.size
    private static let ci = CIContext()

    /// Запечь эффект в стикер 512 × 512. Картинка чуть уменьшается, чтобы контуру было куда лечь.
    static func apply(_ src: UIImage, _ effect: StickerEffect) -> UIImage {
        if effect == .none { return src }
        let inner = shrink(src, 0.86)
        switch effect {
        case .none: return inner
        case .outline: return outlined(inner, .white, s * 0.028)
        case .comic: return comic(inner)
        case .puffy: return puffy(inner)
        case .shiny: return shiny(inner)
        }
    }

    private static var full: CGRect { CGRect(x: 0, y: 0, width: s, height: s) }

    private static func render(_ draw: (CGContext) -> Void) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        return UIGraphicsImageRenderer(size: CGSize(width: s, height: s), format: format).image { ctx in
            draw(ctx.cgContext)
        }
    }

    private static func rgb(_ hex: UInt32, _ alpha: CGFloat = 1) -> UIColor {
        UIColor(rgb: hex).withAlphaComponent(alpha)
    }

    private static func shrink(_ src: UIImage, _ k: CGFloat) -> UIImage {
        render { _ in
            let w = s * k
            let o = (s - w) / 2
            src.draw(in: CGRect(x: o, y: o, width: w, height: w))
        }
    }

    /// Силуэт картинки одним цветом.
    private static func tinted(_ src: UIImage, _ color: UIColor) -> UIImage {
        render { ctx in
            src.draw(in: full)
            ctx.setBlendMode(.sourceIn)
            color.setFill()
            ctx.fill(full)
        }
    }

    private static func blurred(_ image: UIImage, _ radius: CGFloat) -> UIImage {
        guard radius > 0, let cg = image.cgImage else { return image }
        let input = CIImage(cgImage: cg)
        let filter = CIFilter.gaussianBlur()
        filter.inputImage = input
        // BlurMaskFilter на Android берёт «радиус», CIGaussianBlur — сигму: примерно половина
        filter.radius = Float(radius * 0.58)
        guard let output = filter.outputImage?.cropped(to: input.extent),
              let result = ci.createCGImage(output, from: input.extent) else { return image }
        return UIImage(cgImage: result)
    }

    /// Силуэт стикера, обведённый по кругу: копии маски со сдвигом во все стороны.
    private static func silhouette(_ src: UIImage, _ color: UIColor, _ r: CGFloat, blur: CGFloat = 0) -> UIImage {
        let mask = tinted(src, color.withAlphaComponent(1))
        let ring = render { _ in
            let steps = 24
            for k in 0..<steps {
                let a = 2 * Double.pi * Double(k) / Double(steps)
                mask.draw(in: full.offsetBy(dx: CGFloat(cos(a)) * r, dy: CGFloat(sin(a)) * r))
            }
            mask.draw(in: full)
        }
        return blurred(ring, blur)
    }

    private static func alpha(_ color: UIColor) -> CGFloat {
        var a: CGFloat = 1
        color.getRed(nil, green: nil, blue: nil, alpha: &a)
        return a
    }

    static func outlined(_ src: UIImage, _ color: UIColor, _ r: CGFloat) -> UIImage {
        // Мягкая тень под контуром — стикер «лежит» на сообщении
        let shadow = silhouette(src, .black, r, blur: r * 0.8)
        let edge = silhouette(src, color, r)
        return render { _ in
            shadow.draw(in: full, blendMode: .normal, alpha: 0.33)
            edge.draw(in: full, blendMode: .normal, alpha: alpha(color))
            src.draw(in: full)
        }
    }

    /// Меньше оттенков: каждый канал — одной из четырёх ступеней.
    private static func posterize(_ image: UIImage) -> UIImage {
        guard let cg = image.cgImage else { return image }
        let w = cg.width
        let h = cg.height
        guard let ctx = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                                  space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue),
              let data = ctx.data else { return image }
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        let px = data.bindMemory(to: UInt8.self, capacity: w * h * 4)
        for i in stride(from: 0, to: w * h * 4, by: 4) {
            let a = Int(px[i + 3])
            if a == 0 { continue }
            for c in 0..<3 {
                let v = min(255, Int(px[i + c]) * 255 / a)
                let q = min(255, (v / 64) * 64 + 32)
                px[i + c] = UInt8(q * a / 255)
            }
        }
        guard let out = ctx.makeImage() else { return image }
        return UIImage(cgImage: out)
    }

    /// Комикс: меньше оттенков, тёмный контур и белая кромка снаружи.
    private static func comic(_ src: UIImage) -> UIImage {
        let poster = posterize(src)
        let white = silhouette(poster, .white, s * 0.034)
        let ink = silhouette(poster, rgb(0x1A1240), s * 0.014)
        return render { _ in
            white.draw(in: full)
            ink.draw(in: full)
            poster.draw(in: full)
        }
    }

    /// Объём: белый контур, свет сверху слева и тень снизу справа внутри силуэта.
    private static func puffy(_ src: UIImage) -> UIImage {
        let base = outlined(src, .white, s * 0.03)
        let light = blurred(tinted(base, .white), s * 0.04)
        let shade = blurred(tinted(base, rgb(0x260F55)), s * 0.05)
        return render { _ in
            base.draw(in: full)
            // Только внутри силуэта: sourceAtop рисует там, где уже есть стикер
            light.draw(in: full.offsetBy(dx: -s * 0.03, dy: -s * 0.03), blendMode: .sourceAtop, alpha: 0.4)
            shade.draw(in: full.offsetBy(dx: s * 0.035, dy: s * 0.035), blendMode: .sourceAtop, alpha: 0.25)
        }
    }

    /// Блеск: голографический перелив розовый → сиреневый → голубой и диагональный блик.
    private static func shiny(_ src: UIImage) -> UIImage {
        let base = outlined(src, .white, s * 0.024)
        return render { ctx in
            base.draw(in: full)
            ctx.setBlendMode(.sourceAtop)
            let space = CGColorSpaceCreateDeviceRGB()
            let holo = [rgb(0xFF9EDE, 0.4), rgb(0xC6A2FF, 0.33), rgb(0x8CC4FF, 0.4), rgb(0xFFD86B, 0.33), rgb(0xFF9EDE, 0.4)].map(\.cgColor) as CFArray
            if let g = CGGradient(colorsSpace: space, colors: holo, locations: [0, 0.25, 0.5, 0.75, 1]) {
                ctx.drawLinearGradient(g, start: .zero, end: CGPoint(x: s, y: s), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
            }
            let band = [UIColor.white.withAlphaComponent(0), UIColor.white.withAlphaComponent(0.6), UIColor.white.withAlphaComponent(0)].map(\.cgColor) as CFArray
            if let g = CGGradient(colorsSpace: space, colors: band, locations: [0, 0.5, 1]) {
                ctx.drawLinearGradient(g, start: CGPoint(x: s * 0.25, y: 0), end: CGPoint(x: s * 0.55, y: s * 0.5), options: [])
            }
        }
    }

    /// Вырезанный объект — в квадрат 512 с полями, по центру, без искажений.
    static func fit(_ cut: UIImage) -> UIImage {
        render { _ in
            let w0 = max(1, cut.size.width)
            let h0 = max(1, cut.size.height)
            let k = min(s * 0.92 / w0, s * 0.92 / h0)
            let w = w0 * k
            let h = h0 * k
            cut.draw(in: CGRect(x: (s - w) / 2, y: (s - h) / 2, width: w, height: h))
        }
    }

    /// Обрезать прозрачные поля вокруг объекта — до вписывания в квадрат.
    static func trim(_ src: UIImage) -> UIImage {
        guard let cg = src.cgImage else { return src }
        let w = cg.width
        let h = cg.height
        guard let ctx = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                                  space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue),
              let data = ctx.data else { return src }
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        let px = data.bindMemory(to: UInt8.self, capacity: w * h * 4)
        var l = w, t = h, r = -1, b = -1
        for y in 0..<h {
            for x in 0..<w where px[(y * w + x) * 4 + 3] > 16 {
                l = min(l, x)
                r = max(r, x)
                t = min(t, y)
                b = max(b, y)
            }
        }
        if r < l || b < t { return src }
        // Строки в памяти — сверху вниз, как и у cropping(to:)
        guard let image = ctx.makeImage(), let cropped = image.cropping(to: CGRect(x: l, y: t, width: r - l + 1, height: b - t + 1)) else { return src }
        return UIImage(cgImage: cropped)
    }
}
