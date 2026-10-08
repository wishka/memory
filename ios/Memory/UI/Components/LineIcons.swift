import SwiftUI

/// Свои тонкие линейные значки — те же, что на Android. Системные
/// SF Symbols рядом с тонким Unbounded выглядят чужими. Всё рисуется
/// линией в 1.5pt, с кристаллом-ромбом как общим мотивом.
enum LineIcon {
    case feed, stories, reels, groups, pin, gift, link, music, calendar, exit
    case chats, profile, heart, heartFilled, comment, send, back, edit, plus, bell, info
}

struct Glyph: View {
    let kind: LineIcon
    var color: Color = Palette.ink
    var size: CGFloat = 24

    var body: some View {
        Canvas { context, canvasSize in
            Self.draw(kind, in: &context, s: canvasSize.width, color: color)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }

    private static let stroke = StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round)

    private static func p(_ s: CGFloat, _ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: s * x, y: s * y) }

    private static func line(_ s: CGFloat, _ x1: CGFloat, _ y1: CGFloat, _ x2: CGFloat, _ y2: CGFloat) -> Path {
        var path = Path()
        path.move(to: p(s, x1, y1))
        path.addLine(to: p(s, x2, y2))
        return path
    }

    private static func circle(_ s: CGFloat, _ cx: CGFloat, _ cy: CGFloat, _ r: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: s * (cx - r), y: s * (cy - r), width: s * r * 2, height: s * r * 2))
    }

    private static func roundRect(_ s: CGFloat, _ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, _ r: CGFloat) -> Path {
        Path(roundedRect: CGRect(x: s * x, y: s * y, width: s * w, height: s * h), cornerRadius: s * r)
    }

    private static func diamond(_ s: CGFloat, _ cx: CGFloat, _ cy: CGFloat, _ r: CGFloat) -> Path {
        var path = Path()
        path.move(to: p(s, cx, cy - r * 1.25))
        path.addLine(to: p(s, cx + r, cy))
        path.addLine(to: p(s, cx, cy + r * 1.25))
        path.addLine(to: p(s, cx - r, cy))
        path.closeSubpath()
        return path
    }

    // swiftlint:disable:next function_body_length cyclomatic_complexity
    static func draw(_ kind: LineIcon, in context: inout GraphicsContext, s: CGFloat, color: Color) {
        let shading = GraphicsContext.Shading.color(color)
        func strokePath(_ path: Path) { context.stroke(path, with: shading, style: stroke) }
        func fillPath(_ path: Path) { context.fill(path, with: shading) }

        switch kind {
        case .feed:
            // Ромб-кристалл и три строки под ним
            strokePath(diamond(s, 0.5, 0.22, 0.13))
            for (i, w) in [0.62, 0.44, 0.62].enumerated() {
                let y = 0.5 + CGFloat(i) * 0.16
                strokePath(line(s, 0.5 - w / 2, y, 0.5 + w / 2, y))
            }
        case .stories:
            // Кольцо-история из дуг с разрывами и кристалл внутри
            var arcs = Path()
            for k in 0..<4 {
                var arc = Path()
                arc.addRelativeArc(
                    center: p(s, 0.5, 0.5), radius: s * 0.36,
                    startAngle: .degrees(-80 + Double(k) * 90), delta: .degrees(70)
                )
                arcs.addPath(arc)
            }
            strokePath(arcs)
            strokePath(diamond(s, 0.5, 0.5, 0.12))
        case .reels:
            // Вертикальный кадр со скруглением и треугольник «пуск»
            strokePath(roundRect(s, 0.24, 0.12, 0.52, 0.76, 0.1))
            var play = Path()
            play.move(to: p(s, 0.43, 0.38))
            play.addLine(to: p(s, 0.62, 0.5))
            play.addLine(to: p(s, 0.43, 0.62))
            play.closeSubpath()
            strokePath(play)
        case .groups:
            // Трое: в центре ближе и крупнее
            strokePath(circle(s, 0.5, 0.36, 0.11))
            var body = Path()
            body.move(to: p(s, 0.3, 0.78))
            body.addCurve(to: p(s, 0.7, 0.78), control1: p(s, 0.32, 0.56), control2: p(s, 0.68, 0.56))
            strokePath(body)
            strokePath(circle(s, 0.24, 0.42, 0.08))
            strokePath(circle(s, 0.76, 0.42, 0.08))
            strokePath(line(s, 0.1, 0.7, 0.2, 0.6))
            strokePath(line(s, 0.9, 0.7, 0.8, 0.6))
        case .pin:
            var pin = Path()
            pin.move(to: p(s, 0.5, 0.86))
            pin.addCurve(to: p(s, 0.5, 0.14), control1: p(s, 0.2, 0.54), control2: p(s, 0.24, 0.14))
            pin.addCurve(to: p(s, 0.5, 0.86), control1: p(s, 0.76, 0.14), control2: p(s, 0.8, 0.54))
            pin.closeSubpath()
            strokePath(pin)
            strokePath(circle(s, 0.5, 0.4, 0.08))
        case .gift:
            strokePath(roundRect(s, 0.18, 0.42, 0.64, 0.42, 0.05))
            strokePath(line(s, 0.14, 0.42, 0.86, 0.42))
            strokePath(line(s, 0.5, 0.42, 0.5, 0.84))
            var bow = Path()
            bow.move(to: p(s, 0.5, 0.42))
            bow.addCurve(to: p(s, 0.5, 0.42), control1: p(s, 0.3, 0.14), control2: p(s, 0.18, 0.36))
            bow.move(to: p(s, 0.5, 0.42))
            bow.addCurve(to: p(s, 0.5, 0.42), control1: p(s, 0.7, 0.14), control2: p(s, 0.82, 0.36))
            strokePath(bow)
        case .link:
            strokePath(roundRect(s, 0.12, 0.38, 0.42, 0.24, 0.12))
            strokePath(roundRect(s, 0.46, 0.38, 0.42, 0.24, 0.12))
        case .music:
            strokePath(line(s, 0.4, 0.7, 0.4, 0.22))
            strokePath(line(s, 0.4, 0.22, 0.76, 0.16))
            strokePath(line(s, 0.76, 0.16, 0.76, 0.62))
            strokePath(circle(s, 0.3, 0.72, 0.1))
            strokePath(circle(s, 0.66, 0.64, 0.1))
        case .calendar:
            strokePath(roundRect(s, 0.16, 0.24, 0.68, 0.6, 0.08))
            strokePath(line(s, 0.16, 0.42, 0.84, 0.42))
            strokePath(line(s, 0.34, 0.16, 0.34, 0.3))
            strokePath(line(s, 0.66, 0.16, 0.66, 0.3))
        case .exit:
            var door = Path()
            door.move(to: p(s, 0.5, 0.2))
            door.addLine(to: p(s, 0.22, 0.2))
            door.addLine(to: p(s, 0.22, 0.8))
            door.addLine(to: p(s, 0.5, 0.8))
            strokePath(door)
            strokePath(line(s, 0.42, 0.5, 0.84, 0.5))
            var arrow = Path()
            arrow.move(to: p(s, 0.7, 0.36))
            arrow.addLine(to: p(s, 0.84, 0.5))
            arrow.addLine(to: p(s, 0.7, 0.64))
            strokePath(arrow)
        case .chats, .comment:
            var bubble = Path()
            bubble.move(to: p(s, 0.2, 0.24))
            bubble.addLine(to: p(s, 0.8, 0.24))
            bubble.addQuadCurve(to: p(s, 0.86, 0.3), control: p(s, 0.86, 0.24))
            bubble.addLine(to: p(s, 0.86, 0.62))
            bubble.addQuadCurve(to: p(s, 0.8, 0.68), control: p(s, 0.86, 0.68))
            bubble.addLine(to: p(s, 0.42, 0.68))
            bubble.addLine(to: p(s, 0.26, 0.82))
            bubble.addLine(to: p(s, 0.28, 0.68))
            bubble.addLine(to: p(s, 0.2, 0.68))
            bubble.addQuadCurve(to: p(s, 0.14, 0.62), control: p(s, 0.14, 0.68))
            bubble.addLine(to: p(s, 0.14, 0.3))
            bubble.addQuadCurve(to: p(s, 0.2, 0.24), control: p(s, 0.14, 0.24))
            bubble.closeSubpath()
            strokePath(bubble)
            if kind == .chats {
                for i in 0..<3 {
                    fillPath(circle(s, 0.36 + CGFloat(i) * 0.14, 0.46, 0.035))
                }
            }
        case .profile:
            strokePath(circle(s, 0.5, 0.36, 0.15))
            var shoulders = Path()
            shoulders.move(to: p(s, 0.2, 0.84))
            shoulders.addCurve(to: p(s, 0.8, 0.84), control1: p(s, 0.24, 0.6), control2: p(s, 0.76, 0.6))
            strokePath(shoulders)
        case .heart, .heartFilled:
            var heart = Path()
            heart.move(to: p(s, 0.5, 0.82))
            heart.addCurve(to: p(s, 0.32, 0.22), control1: p(s, 0.1, 0.56), control2: p(s, 0.08, 0.24))
            heart.addCurve(to: p(s, 0.5, 0.34), control1: p(s, 0.42, 0.21), control2: p(s, 0.48, 0.28))
            heart.addCurve(to: p(s, 0.68, 0.22), control1: p(s, 0.52, 0.28), control2: p(s, 0.58, 0.21))
            heart.addCurve(to: p(s, 0.5, 0.82), control1: p(s, 0.92, 0.24), control2: p(s, 0.9, 0.56))
            heart.closeSubpath()
            if kind == .heartFilled { fillPath(heart) }
            strokePath(heart)
        case .send:
            var plane = Path()
            plane.move(to: p(s, 0.16, 0.2))
            plane.addLine(to: p(s, 0.86, 0.5))
            plane.addLine(to: p(s, 0.16, 0.8))
            plane.addLine(to: p(s, 0.28, 0.5))
            plane.closeSubpath()
            strokePath(plane)
            strokePath(line(s, 0.28, 0.5, 0.56, 0.5))
        case .back:
            var chevron = Path()
            chevron.move(to: p(s, 0.6, 0.2))
            chevron.addLine(to: p(s, 0.3, 0.5))
            chevron.addLine(to: p(s, 0.6, 0.8))
            strokePath(chevron)
        case .edit:
            var pencil = Path()
            pencil.move(to: p(s, 0.2, 0.8))
            pencil.addLine(to: p(s, 0.24, 0.62))
            pencil.addLine(to: p(s, 0.64, 0.22))
            pencil.addLine(to: p(s, 0.78, 0.36))
            pencil.addLine(to: p(s, 0.38, 0.76))
            pencil.closeSubpath()
            strokePath(pencil)
        case .plus:
            strokePath(line(s, 0.5, 0.22, 0.5, 0.78))
            strokePath(line(s, 0.22, 0.5, 0.78, 0.5))
        case .bell:
            var bell = Path()
            bell.move(to: p(s, 0.24, 0.7))
            bell.addLine(to: p(s, 0.76, 0.7))
            bell.addLine(to: p(s, 0.7, 0.6))
            bell.addLine(to: p(s, 0.7, 0.42))
            bell.addCurve(to: p(s, 0.3, 0.42), control1: p(s, 0.7, 0.18), control2: p(s, 0.3, 0.18))
            bell.addLine(to: p(s, 0.3, 0.6))
            bell.closeSubpath()
            strokePath(bell)
            strokePath(line(s, 0.44, 0.8, 0.56, 0.8))
        case .info:
            strokePath(diamond(s, 0.5, 0.5, 0.34))
            strokePath(line(s, 0.5, 0.46, 0.5, 0.64))
            fillPath(circle(s, 0.5, 0.36, 0.035))
        }
    }
}
