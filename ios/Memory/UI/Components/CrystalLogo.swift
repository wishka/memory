import SwiftUI

/// Знак Memory в шапке ленты: вытянутый кристалл (две четырёхгранные
/// пирамиды, как в PRISM), который медленно вращается вокруг своей оси
/// по часовой стрелке, если смотреть сверху. Грани — перламутровое
/// стекло, как у слова MEMORY на заставке: свет падает слева сверху,
/// повёрнутые к нему грани светлеют, остальные уходят в тень; рёбра и
/// контур — белые, по стеклу пробегает блик. Те же правила — в
/// CrystalLogo.kt на Android; тот же кристалл — на значке приложения.
enum CrystalMark {
    static let topY: CGFloat = 0.04
    static let bottomY: CGFloat = 0.96
    static let midY: CGFloat = 0.5
    static let halfW: CGFloat = 0.2
    /// Один оборот, с.
    static let turn: Double = 9

    /// Перламутр — те же цвета, что у слова MEMORY.
    static let iridescent: [Color] = ([0xFF9EDE, 0xC6A2FF, 0x8CC4FF, 0xFFF4FF, 0xFFDB8F, 0xD08CFF, 0x7AA8FF] as [UInt32]).map { RGB($0).color }

    /// Куда светит свет: слева сверху и спереди.
    private static let light: (Double, Double, Double) = {
        let v = (-0.45, -0.55, 0.70)
        let n = (v.0 * v.0 + v.1 * v.1 + v.2 * v.2).squareRoot()
        return (v.0 / n, v.1 / n, v.2 / n)
    }()

    /// Нарисовать кристалл, повёрнутый на angle (радианы) вокруг вертикали.
    /// По часовой сверху — угол убывает. sweep — положение блика 0…1 или вне.
    static func draw(_ ctx: GraphicsContext, side s: CGFloat, angle: Double, sweep: Double) {
        let cx = s * 0.5
        let cy = s * midY
        let half = s * (bottomY - topY) / 2
        let r = Double(s * halfW)
        // Экватор: четыре вершины квадратного сечения; z — к зрителю
        let eq: [(Double, Double, Double)] = (0..<4).map { k in
            let phi = angle + Double(k) * .pi / 2
            return (r * sin(phi), 0, r * cos(phi))
        }
        let top = (0.0, -Double(half), 0.0)
        let bottom = (0.0, Double(half), 0.0)
        func screen(_ p: (Double, Double, Double)) -> CGPoint { CGPoint(x: cx + p.0, y: cy + p.1) }

        // Ореол за стеклом
        ctx.fill(
            Path(ellipseIn: CGRect(x: 0, y: 0, width: s, height: s)),
            with: .radialGradient(Gradient(colors: [RGB(0xC49BFF).color.opacity(0.4), .clear]), center: CGPoint(x: cx, y: cy), startRadius: 0, endRadius: s * 0.5)
        )

        let body = GraphicsContext.Shading.linearGradient(
            Gradient(colors: iridescent + iridescent.reversed()),
            startPoint: CGPoint(x: s * 0.2, y: 0), endPoint: CGPoint(x: s * 0.8, y: s)
        )
        var silhouette = Path()
        let edge = StrokeStyle(lineWidth: s * 0.012 + 0.6, lineJoin: .round)
        for k in 0..<4 {
            let a = eq[k]
            let b = eq[(k + 1) % 4]
            for apex in [top, bottom] {
                // Нормаль грани наружу; видна — если смотрит на зрителя
                let u = (a.0 - apex.0, a.1 - apex.1, a.2 - apex.2)
                let v = (b.0 - apex.0, b.1 - apex.1, b.2 - apex.2)
                var n = (u.1 * v.2 - u.2 * v.1, u.2 * v.0 - u.0 * v.2, u.0 * v.1 - u.1 * v.0)
                let c = ((a.0 + b.0 + apex.0) / 3, (a.1 + b.1 + apex.1) / 3, (a.2 + b.2 + apex.2) / 3)
                if n.0 * c.0 + n.1 * c.1 + n.2 * c.2 < 0 { n = (-n.0, -n.1, -n.2) }
                guard n.2 > 0 else { continue }
                let len = (n.0 * n.0 + n.1 * n.1 + n.2 * n.2).squareRoot()
                let lit = (n.0 * light.0 + n.1 * light.1 + n.2 * light.2) / len
                var face = Path()
                face.move(to: screen(apex))
                face.addLine(to: screen(a))
                face.addLine(to: screen(b))
                face.closeSubpath()
                silhouette.addPath(face)
                ctx.fill(face, with: body)
                // Свет и тень грани
                if lit > 0.55 {
                    ctx.fill(face, with: .color(.white.opacity(min(0.5, (lit - 0.55) * 1.1))))
                } else {
                    ctx.fill(face, with: .color(RGB(0x4A2F9A).color.opacity(min(0.45, (0.55 - lit) * 0.75))))
                }
                ctx.stroke(face, with: .color(.white.opacity(0.75)), style: edge)
            }
        }
        // Блик по стеклу
        if (0...1.2).contains(sweep) {
            var clipped = ctx
            clipped.clip(to: silhouette)
            let y = s * sweep
            clipped.fill(
                Path(CGRect(x: 0, y: 0, width: s, height: s)),
                with: .linearGradient(
                    Gradient(colors: [.clear, .white.opacity(0.55), .clear]),
                    startPoint: CGPoint(x: 0, y: y - s * 0.18), endPoint: CGPoint(x: s * 0.3, y: y + s * 0.18)
                )
            )
        }
        // Контур — по крайним вершинам экватора
        if let left = eq.min(by: { $0.0 < $1.0 }), let right = eq.max(by: { $0.0 < $1.0 }) {
            var outline = Path()
            outline.move(to: screen(top))
            outline.addLine(to: screen(right))
            outline.addLine(to: screen(bottom))
            outline.addLine(to: screen(left))
            outline.closeSubpath()
            ctx.stroke(outline, with: .color(.white), style: StrokeStyle(lineWidth: s * 0.018 + 0.8, lineJoin: .round))
        }
        // Блики-звёздочки — мерцают, пока кристалл поворачивается
        let twinkle = 0.5 + 0.5 * sin(angle * 2)
        for (sx, sy, rad) in [(0.44, 0.30, 0.05), (0.57, 0.68, 0.035)] as [(CGFloat, CGFloat, CGFloat)] {
            let rr = s * rad * (0.6 + 0.4 * twinkle)
            let c = CGPoint(x: s * sx, y: s * sy)
            var star = Path()
            star.move(to: CGPoint(x: c.x - rr, y: c.y)); star.addLine(to: CGPoint(x: c.x + rr, y: c.y))
            star.move(to: CGPoint(x: c.x, y: c.y - rr)); star.addLine(to: CGPoint(x: c.x, y: c.y + rr))
            ctx.stroke(star, with: .color(.white), style: StrokeStyle(lineWidth: s * 0.012 + 0.5, lineCap: .round))
        }
    }
}

/// Кристалл в шапке ленты: вращается по часовой стрелке (вид сверху), по стеклу пробегает блик.
struct CrystalLogo: View {
    var size: CGFloat = 56

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30)) { timeline in
            let t = timeline.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: CrystalMark.turn) / CrystalMark.turn
            Canvas { ctx, sz in
                // По часовой сверху: угол убывает; блик — в начале каждого оборота
                CrystalMark.draw(ctx, side: sz.width, angle: -t * 2 * .pi + 0.35, sweep: t * 2.4 - 0.6)
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}
