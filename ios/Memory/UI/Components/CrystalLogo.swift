import SwiftUI
import UIKit

/// Знак Memory в шапке ленты: вытянутый кристалл, как во вкладке PRISM, —
/// с гранями и ребром, а внутри — кристальное стекло, как у слова MEMORY
/// на заставке: перламутр, светлые и тёмные грани, кант, контур и блики.
/// По стеклу время от времени пробегает блик. Те же правила — в
/// CrystalLogo.kt на Android.
enum CrystalMark {
    /// Перламутр — те же цвета, что у слова MEMORY.
    private static let iridescent: [UInt32] = [0xFF9EDE, 0xC6A2FF, 0x8CC4FF, 0xFFF4FF, 0xFFDB8F, 0xD08CFF, 0x7AA8FF]

    // Вершины в долях стороны: верх, право, низ, лево и точка ребра на «экваторе»
    static let topY: CGFloat = 0.04
    static let bottomY: CGFloat = 0.96
    static let midY: CGFloat = 0.5
    static let halfW: CGFloat = 0.2
    static let ridgeX: CGFloat = 0.545

    static func outline(_ s: CGFloat) -> UIBezierPath {
        let p = UIBezierPath()
        p.move(to: CGPoint(x: s * 0.5, y: s * topY))
        p.addLine(to: CGPoint(x: s * (0.5 + halfW), y: s * midY))
        p.addLine(to: CGPoint(x: s * 0.5, y: s * bottomY))
        p.addLine(to: CGPoint(x: s * (0.5 - halfW), y: s * midY))
        p.close()
        return p
    }

    private static func tri(_ s: CGFloat, _ a: CGPoint, _ b: CGPoint, _ c: CGPoint) -> UIBezierPath {
        let p = UIBezierPath()
        p.move(to: CGPoint(x: s * a.x, y: s * a.y))
        p.addLine(to: CGPoint(x: s * b.x, y: s * b.y))
        p.addLine(to: CGPoint(x: s * c.x, y: s * c.y))
        p.close()
        return p
    }

    static func render(side s: CGFloat, scale: CGFloat) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        return UIGraphicsImageRenderer(size: CGSize(width: s, height: s), format: format).image { context in
            let ctx = context.cgContext
            let path = outline(s)

            // 1. Ореол: свет, рассеянный стеклом
            ctx.saveGState()
            ctx.setShadow(offset: .zero, blur: s * 0.12, color: UIColor(rgb: 0xC49BFF).withAlphaComponent(0.63).cgColor)
            UIColor(rgb: 0xC49BFF).withAlphaComponent(0.5).setFill()
            path.fill()
            ctx.restoreGState()

            ctx.saveGState()
            path.addClip()
            // 2. Тело — перламутр по диагонали
            let colors = iridescent.map { UIColor(rgb: $0).cgColor } as CFArray
            if let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: nil) {
                ctx.drawLinearGradient(gradient, start: CGPoint(x: s * 0.3, y: 0), end: CGPoint(x: s * 0.7, y: s), options: [])
            }
            // 3. Грани: четыре плоскости кристалла — светлые и в тени
            let top = CGPoint(x: 0.5, y: topY)
            let bottom = CGPoint(x: 0.5, y: bottomY)
            let left = CGPoint(x: 0.5 - halfW, y: midY)
            let right = CGPoint(x: 0.5 + halfW, y: midY)
            let ridge = CGPoint(x: ridgeX, y: midY)
            UIColor.white.withAlphaComponent(0.33).setFill()
            tri(s, top, left, ridge).fill()
            UIColor(rgb: 0x4A2F9A).withAlphaComponent(0.23).setFill()
            tri(s, top, ridge, right).fill()
            tri(s, left, bottom, ridge).fill()
            UIColor.white.withAlphaComponent(0.19).setFill()
            tri(s, ridge, bottom, right).fill()
            // Косые сколы, как у слова MEMORY (свой генератор — тот же рисунок при каждом запуске)
            var seed: UInt64 = 11
            func next() -> CGFloat {
                seed = seed &* 6364136223846793005 &+ 1442695040888963407
                return CGFloat(seed >> 33) / CGFloat(UInt64(1) << 31)
            }
            var fx = -s * 0.2
            var lit = true
            while fx < s {
                let w = s * (0.02 + next() * 0.05)
                (lit ? UIColor.white.withAlphaComponent(0.2) : UIColor(rgb: 0x4A2F9A).withAlphaComponent(0.13)).setFill()
                let band = UIBezierPath()
                band.move(to: CGPoint(x: fx, y: 0))
                band.addLine(to: CGPoint(x: fx + w, y: 0))
                band.addLine(to: CGPoint(x: fx + w + s * 0.3, y: s))
                band.addLine(to: CGPoint(x: fx + s * 0.3, y: s))
                band.close()
                band.fill()
                fx += w + s * (0.02 + next() * 0.06)
                lit.toggle()
            }
            // 4. Внутренний кант: светлая кромка и тень за ней
            UIColor.white.withAlphaComponent(0.25).setStroke()
            path.lineWidth = s * 0.05
            path.stroke()
            UIColor(rgb: 0x8A6FD8).withAlphaComponent(0.4).setStroke()
            path.lineWidth = s * 0.02
            path.stroke()
            ctx.restoreGState()

            // 5. Рёбра: «экватор» и переднее ребро — как у кристалла в PRISM
            let edges = UIBezierPath()
            edges.move(to: CGPoint(x: s * left.x, y: s * midY))
            edges.addLine(to: CGPoint(x: s * right.x, y: s * midY))
            edges.move(to: CGPoint(x: s * 0.5, y: s * topY))
            edges.addLine(to: CGPoint(x: s * ridgeX, y: s * midY))
            edges.addLine(to: CGPoint(x: s * 0.5, y: s * bottomY))
            edges.lineWidth = s * 0.012 + 0.6
            edges.lineCapStyle = .round
            edges.lineJoinStyle = .round
            UIColor.white.withAlphaComponent(0.8).setStroke()
            edges.stroke()
            // 6. Контур стекла
            path.lineWidth = s * 0.016 + 0.8
            path.lineJoinStyle = .round
            UIColor.white.setStroke()
            path.stroke()
            // 7. Блики-звёздочки
            for (sx, sy, r) in [(0.44, 0.30, 0.05), (0.58, 0.68, 0.035)] as [(CGFloat, CGFloat, CGFloat)] {
                let star = UIBezierPath()
                star.move(to: CGPoint(x: s * (sx - r), y: s * sy))
                star.addLine(to: CGPoint(x: s * (sx + r), y: s * sy))
                star.move(to: CGPoint(x: s * sx, y: s * (sy - r)))
                star.addLine(to: CGPoint(x: s * sx, y: s * (sy + r)))
                star.lineWidth = s * 0.012 + 0.5
                star.lineCapStyle = .round
                star.stroke()
            }
        }
    }
}

/// Кристалл в шапке ленты с пробегающим бликом.
struct CrystalLogo: View {
    var size: CGFloat = 56
    @Environment(\.displayScale) private var displayScale
    @State private var image: UIImage?

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30)) { timeline in
            // Блик проходит сверху вниз за 3,6 с, потом пауза
            let t = timeline.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 3.6) / 3.6 * 2.4 - 0.6
            ZStack {
                if let image { Image(uiImage: image).resizable() }
                if (0...1.2).contains(t) {
                    Canvas { ctx, sz in
                        let s = sz.width
                        ctx.clip(to: Path(CrystalMark.outline(s).cgPath))
                        let y = s * t
                        ctx.fill(
                            Path(CGRect(origin: .zero, size: sz)),
                            with: .linearGradient(
                                Gradient(colors: [.clear, .white.opacity(0.55), .clear]),
                                startPoint: CGPoint(x: 0, y: y - s * 0.18),
                                endPoint: CGPoint(x: s * 0.3, y: y + s * 0.18)
                            )
                        )
                    }
                }
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
        .onAppear {
            if image == nil { image = CrystalMark.render(side: size, scale: displayScale) }
        }
    }
}
