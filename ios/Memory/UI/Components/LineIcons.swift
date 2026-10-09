import SwiftUI

/// Свои тонкие линейные значки — те же, что на Android. Системные
/// SF Symbols рядом с тонким Unbounded выглядят чужими. Всё рисуется
/// линией в 1.5pt, с кристаллом-ромбом как общим мотивом.
enum LineIcon {
    case feed, stories, prism, groups, pin, gift, link, music, calendar, exit
    case share, bookmark, bookmarkFilled, attach, camera, video, image, document, trash, play, close
    case reply, forward, chevronDown, pushpin
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

    /// Перламутр кристалла — те же цвета, что у стекла слова MEMORY.
    static let crystalColors: [Color] = ([0xFF9EDE, 0xC6A2FF, 0x8CC4FF, 0xFFF4FF, 0xD08CFF] as [UInt32]).map { RGB($0).color }

    private static func crystalShading(_ s: CGFloat) -> GraphicsContext.Shading {
        .linearGradient(Gradient(colors: crystalColors), startPoint: .zero, endPoint: CGPoint(x: s, y: s))
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
        case .prism:
            // Призма: треугольник, входящий луч и веер лучей-спектра
            var tri = Path()
            tri.move(to: p(s, 0.42, 0.18))
            tri.addLine(to: p(s, 0.70, 0.80))
            tri.addLine(to: p(s, 0.14, 0.80))
            tri.closeSubpath()
            strokePath(tri)
            strokePath(line(s, 0.04, 0.58, 0.30, 0.52))
            for k in 0..<3 {
                strokePath(line(s, 0.56, 0.50, 0.94, 0.40 + CGFloat(k) * 0.12))
            }
        case .reply:
            // Изогнутая стрелка влево
            var head = Path()
            head.move(to: p(s, 0.40, 0.24))
            head.addLine(to: p(s, 0.16, 0.46))
            head.addLine(to: p(s, 0.40, 0.68))
            strokePath(head)
            var tail = Path()
            tail.move(to: p(s, 0.16, 0.46))
            tail.addLine(to: p(s, 0.58, 0.46))
            tail.addCurve(to: p(s, 0.84, 0.80), control1: p(s, 0.80, 0.46), control2: p(s, 0.86, 0.62))
            strokePath(tail)
        case .forward:
            // Изогнутая стрелка вправо
            var head = Path()
            head.move(to: p(s, 0.60, 0.24))
            head.addLine(to: p(s, 0.84, 0.46))
            head.addLine(to: p(s, 0.60, 0.68))
            strokePath(head)
            var tail = Path()
            tail.move(to: p(s, 0.84, 0.46))
            tail.addLine(to: p(s, 0.42, 0.46))
            tail.addCurve(to: p(s, 0.16, 0.80), control1: p(s, 0.20, 0.46), control2: p(s, 0.14, 0.62))
            strokePath(tail)
        case .pushpin:
            // Канцелярская кнопка, наклонённая вправо
            var head = Path()
            head.move(to: p(s, 0.56, 0.12))
            head.addLine(to: p(s, 0.88, 0.44))
            head.addLine(to: p(s, 0.74, 0.48))
            head.addLine(to: p(s, 0.58, 0.64))
            head.addLine(to: p(s, 0.58, 0.78))
            head.addLine(to: p(s, 0.22, 0.42))
            head.addLine(to: p(s, 0.36, 0.42))
            head.addLine(to: p(s, 0.52, 0.26))
            head.closeSubpath()
            strokePath(head)
            strokePath(line(s, 0.40, 0.60, 0.14, 0.86))
        case .chevronDown:
            var chevron = Path()
            chevron.move(to: p(s, 0.26, 0.38))
            chevron.addLine(to: p(s, 0.5, 0.62))
            chevron.addLine(to: p(s, 0.74, 0.38))
            strokePath(chevron)
        case .play:
            var play = Path()
            play.move(to: p(s, 0.34, 0.22))
            play.addLine(to: p(s, 0.80, 0.5))
            play.addLine(to: p(s, 0.34, 0.78))
            play.closeSubpath()
            fillPath(play)
        case .share:
            // Стрелка из коробки
            strokePath(line(s, 0.5, 0.14, 0.5, 0.6))
            var arrow = Path()
            arrow.move(to: p(s, 0.34, 0.3))
            arrow.addLine(to: p(s, 0.5, 0.14))
            arrow.addLine(to: p(s, 0.66, 0.3))
            strokePath(arrow)
            var box = Path()
            box.move(to: p(s, 0.36, 0.44))
            box.addLine(to: p(s, 0.22, 0.44))
            box.addLine(to: p(s, 0.22, 0.86))
            box.addLine(to: p(s, 0.78, 0.86))
            box.addLine(to: p(s, 0.78, 0.44))
            box.addLine(to: p(s, 0.64, 0.44))
            strokePath(box)
        case .bookmark, .bookmarkFilled:
            var mark = Path()
            mark.move(to: p(s, 0.26, 0.14))
            mark.addLine(to: p(s, 0.74, 0.14))
            mark.addLine(to: p(s, 0.74, 0.86))
            mark.addLine(to: p(s, 0.5, 0.68))
            mark.addLine(to: p(s, 0.26, 0.86))
            mark.closeSubpath()
            if kind == .bookmarkFilled { context.fill(mark, with: crystalShading(s)) }
            strokePath(mark)
        case .attach:
            // Скрепка
            var clip = Path()
            clip.move(to: p(s, 0.62, 0.3))
            clip.addLine(to: p(s, 0.36, 0.56))
            clip.addCurve(to: p(s, 0.46, 0.68), control1: p(s, 0.28, 0.64), control2: p(s, 0.38, 0.76))
            clip.addLine(to: p(s, 0.74, 0.40))
            clip.addCurve(to: p(s, 0.56, 0.22), control1: p(s, 0.88, 0.26), control2: p(s, 0.70, 0.08))
            clip.addLine(to: p(s, 0.26, 0.52))
            clip.addCurve(to: p(s, 0.52, 0.78), control1: p(s, 0.06, 0.72), control2: p(s, 0.32, 0.98))
            clip.addLine(to: p(s, 0.80, 0.50))
            strokePath(clip)
        case .camera:
            strokePath(roundRect(s, 0.12, 0.3, 0.76, 0.52, 0.1))
            var top = Path()
            top.move(to: p(s, 0.34, 0.3))
            top.addLine(to: p(s, 0.4, 0.2))
            top.addLine(to: p(s, 0.6, 0.2))
            top.addLine(to: p(s, 0.66, 0.3))
            strokePath(top)
            strokePath(circle(s, 0.5, 0.56, 0.13))
        case .video:
            strokePath(roundRect(s, 0.1, 0.28, 0.56, 0.44, 0.08))
            var lens = Path()
            lens.move(to: p(s, 0.66, 0.44))
            lens.addLine(to: p(s, 0.9, 0.32))
            lens.addLine(to: p(s, 0.9, 0.68))
            lens.addLine(to: p(s, 0.66, 0.56))
            strokePath(lens)
        case .image:
            strokePath(roundRect(s, 0.14, 0.18, 0.72, 0.64, 0.08))
            var hills = Path()
            hills.move(to: p(s, 0.18, 0.74))
            hills.addLine(to: p(s, 0.4, 0.5))
            hills.addLine(to: p(s, 0.56, 0.64))
            hills.addLine(to: p(s, 0.66, 0.56))
            hills.addLine(to: p(s, 0.84, 0.74))
            strokePath(hills)
            strokePath(circle(s, 0.64, 0.36, 0.06))
        case .document:
            var doc = Path()
            doc.move(to: p(s, 0.24, 0.12))
            doc.addLine(to: p(s, 0.58, 0.12))
            doc.addLine(to: p(s, 0.78, 0.32))
            doc.addLine(to: p(s, 0.78, 0.88))
            doc.addLine(to: p(s, 0.24, 0.88))
            doc.closeSubpath()
            doc.move(to: p(s, 0.58, 0.12))
            doc.addLine(to: p(s, 0.58, 0.32))
            doc.addLine(to: p(s, 0.78, 0.32))
            strokePath(doc)
            strokePath(line(s, 0.36, 0.52, 0.66, 0.52))
            strokePath(line(s, 0.36, 0.68, 0.6, 0.68))
        case .trash:
            strokePath(line(s, 0.16, 0.26, 0.84, 0.26))
            var bin = Path()
            bin.move(to: p(s, 0.38, 0.26))
            bin.addLine(to: p(s, 0.42, 0.14))
            bin.addLine(to: p(s, 0.58, 0.14))
            bin.addLine(to: p(s, 0.62, 0.26))
            bin.move(to: p(s, 0.24, 0.26))
            bin.addLine(to: p(s, 0.3, 0.86))
            bin.addLine(to: p(s, 0.7, 0.86))
            bin.addLine(to: p(s, 0.76, 0.26))
            strokePath(bin)
        case .close:
            strokePath(line(s, 0.26, 0.26, 0.74, 0.74))
            strokePath(line(s, 0.74, 0.26, 0.26, 0.74))
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
            if kind == .heartFilled {
                // «Нравится» — кристальное сердце: перламутровый перелив,
                // светлый контур и блик-звёздочка, как у слова MEMORY
                context.fill(heart, with: crystalShading(s))
                context.stroke(heart, with: .color(.white.opacity(0.9)), style: stroke)
                let r: CGFloat = 0.07
                let sparkle = StrokeStyle(lineWidth: 1.05, lineCap: .round)
                context.stroke(line(s, 0.36 - r, 0.36, 0.36 + r, 0.36), with: .color(.white), style: sparkle)
                context.stroke(line(s, 0.36, 0.36 - r, 0.36, 0.36 + r), with: .color(.white), style: sparkle)
            } else {
                strokePath(heart)
            }
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
