import UIKit

/// Карточка-картинка для чего угодно: чек-ин, полка, дневник, вылазка.
/// 1080 × 1350 на звёздном небе, как Итоги; внизу — код приглашения, чтобы
/// увидевший картинку мог прийти и сделать своё. Та же раскладка — в
/// ShareCard.kt.
struct ShareCardSpec {
    /// Надпись сверху: «ПОЛКА», «ЧЕК-ИН».
    var kicker: String
    var emoji: String
    var title: String
    var subtitle: String = ""
    /// Строки-плитки: значок и текст.
    var lines: [EchoLine] = []
    /// Картинка внутри (готовый рисунок); nil — без неё.
    var picture: UIImage? = nil
    var author: String = ""
    var inviteCode: String = ""

    /// Для звёзд: у одной карточки — одни и те же (хеш строки, как в Java).
    var seed: Int {
        var h: Int32 = 0
        for unit in (kicker + title).utf16 { h = 31 &* h &+ Int32(unit) }
        return Int(h)
    }
}

enum ShareCard {
    static let width: CGFloat = 1080
    static let height: CGFloat = 1350

    static func render(_ spec: ShareCardSpec) -> UIImage {
        let W = width
        let H = height
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: CGSize(width: W, height: H), format: format).image { ctx in
            let c = ctx.cgContext
            let space = CGColorSpaceCreateDeviceRGB()
            // Небо
            let sky = CGGradient(colorsSpace: space, colors: [color(0x07081A).cgColor, color(0x0F153A).cgColor, color(0x2A2152).cgColor] as CFArray, locations: [0, 0.5, 1])!
            c.drawLinearGradient(sky, start: .zero, end: CGPoint(x: 0, y: H), options: [])
            // Свечения
            for (x, y, r, hex, a) in [(0.15, 0.2, 0.5, 0x844CAD, 0x88), (0.9, 0.3, 0.45, 0x195699, 0x66), (0.6, 0.95, 0.55, 0x5F3F96, 0x77)]
                as [(CGFloat, CGFloat, CGFloat, UInt32, Int)] {
                let base = color(hex)
                let alpha = CGFloat(a) / 255
                let glow = CGGradient(colorsSpace: space, colors: [base.withAlphaComponent(alpha).cgColor, base.withAlphaComponent(0).cgColor] as CFArray, locations: [0, 1])!
                let center = CGPoint(x: x * W, y: y * H)
                c.drawRadialGradient(glow, startCenter: center, startRadius: 0, endCenter: center, endRadius: r * W, options: [])
            }
            // Звёзды — одинаковые для одной карточки
            var rnd = SeededRandom(spec.seed)
            for _ in 0..<140 {
                let alpha = CGFloat(80 + rnd.nextInt(175)) / 255
                let x = CGFloat(rnd.nextDouble()) * W
                let y = CGFloat(rnd.nextDouble()) * H
                let r = 1 + CGFloat(rnd.nextDouble()) * 2.4
                UIColor.white.withAlphaComponent(alpha).setFill()
                UIBezierPath(ovalIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)).fill()
            }

            let lilac = color(0xBC92CD)
            let soft = color(0xC9C2E6)
            let bold = { (size: CGFloat) in UIFont.systemFont(ofSize: size, weight: .bold) }
            let regular = { (size: CGFloat) in UIFont.systemFont(ofSize: size) }

            draw("MEMORY · " + spec.kicker.uppercased(), x: 80, baseline: 130, font: bold(30), color: lilac, kern: 12)
            // Значок и заголовок
            draw(spec.emoji, x: 80, baseline: 290, font: regular(120), color: .white)
            let titleFont = bold(76)
            let gradient = titleGradient(W)
            var y: CGFloat = 410
            for line in wrap(spec.title, font: titleFont, width: W - 160).prefix(2) {
                draw(line, x: 80, baseline: y, font: titleFont, color: gradient)
                y += 88
            }
            if !spec.subtitle.isEmpty {
                let font = regular(38)
                for line in wrap(spec.subtitle, font: font, width: W - 160).prefix(2) {
                    draw(line, x: 80, baseline: y, font: font, color: soft)
                    y += 52
                }
            }
            y += 24
            if let picture = spec.picture {
                let side = min(H - 260 - y, W - 160)
                if side > 200 {
                    let left = (W - side) / 2
                    picture.draw(in: CGRect(x: left, y: y, width: side, height: side))
                    y += side + 30
                }
            }
            // Строки-плитки
            for line in spec.lines {
                if y > H - 300 { break }
                let rect = CGRect(x: 80, y: y, width: W - 160, height: 104)
                let path = UIBezierPath(roundedRect: rect, cornerRadius: 32)
                UIColor.white.withAlphaComponent(28.0 / 255).setFill()
                path.fill()
                UIColor.white.withAlphaComponent(70.0 / 255).setStroke()
                path.lineWidth = 2
                path.stroke()
                draw(line.emoji, x: 110, baseline: y + 68, font: regular(46), color: .white)
                let font = regular(36)
                draw(ellipsize(line.text, font: font, width: W - 330), x: 200, baseline: y + 64, font: font, color: .white)
                y += 124
            }
            // Подвал: кто и код приглашения
            if !spec.author.isEmpty {
                draw(spec.author, x: 80, baseline: H - 150, font: regular(34), color: lilac)
            }
            let footer = regular(30)
            let dim = UIColor.white.withAlphaComponent(170.0 / 255)
            draw("✦ memory", x: 80, baseline: H - 80, font: footer, color: dim)
            if !spec.inviteCode.isEmpty {
                let label = t("код приглашения") + " " + spec.inviteCode
                draw(label, x: W - 80 - measure(label, font: footer), baseline: H - 80, font: footer, color: dim)
            }
        }
    }

    /// В папку вложений: для «Поделиться» и для записи в ленту.
    static func save(_ spec: ShareCardSpec, name: String) -> Attachment? {
        guard let data = render(spec).pngData() else { return nil }
        return MediaFiles.save(data, kind: .photo, ext: "png", name: "memory-\(name).png")
    }

    // MARK: Текст

    private static func color(_ hex: UInt32) -> UIColor {
        UIColor(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
    }

    /// Строка от базовой линии — как drawText на Android.
    private static func draw(_ text: String, x: CGFloat, baseline: CGFloat, font: UIFont, color: UIColor, kern: CGFloat = 0) {
        let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color, .kern: kern]
        (text as NSString).draw(at: CGPoint(x: x, y: baseline - font.ascender), withAttributes: attrs)
    }

    private static func measure(_ text: String, font: UIFont) -> CGFloat {
        (text as NSString).size(withAttributes: [.font: font]).width
    }

    /// Заголовок — переливом розовый → сиреневый → голубой слева направо
    /// (от x = 80 до x = 1000): цвет-узор из полоски градиента.
    private static func titleGradient(_ width: CGFloat) -> UIColor {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let strip = UIGraphicsImageRenderer(size: CGSize(width: width, height: 1), format: format).image { ctx in
            let colors = [color(0xFF9EDE).cgColor, color(0xC6A2FF).cgColor, color(0x8CC4FF).cgColor] as CFArray
            let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 0.5, 1])!
            ctx.cgContext.drawLinearGradient(gradient, start: CGPoint(x: 80, y: 0), end: CGPoint(x: 1000, y: 0),
                                             options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
        }
        return UIColor(patternImage: strip)
    }

    private static func wrap(_ s: String, font: UIFont, width: CGFloat) -> [String] {
        var lines: [String] = []
        var line = ""
        for word in s.components(separatedBy: " ") {
            let next = line.isEmpty ? word : line + " " + word
            if measure(next, font: font) <= width || line.isEmpty {
                line = next
            } else {
                lines.append(line)
                line = word
            }
        }
        if !line.isEmpty { lines.append(line) }
        return lines
    }

    private static func ellipsize(_ s: String, font: UIFont, width: CGFloat) -> String {
        if measure(s, font: font) <= width { return s }
        var cut = s
        while !cut.isEmpty && measure(cut + "…", font: font) > width { cut.removeLast() }
        return cut + "…"
    }
}
