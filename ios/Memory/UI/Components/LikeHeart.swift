import SwiftUI

/// Сердце «нравится» с анимацией: при нажатии сначала вспыхивает искра —
/// четырёхлучевая звезда и разлетающиеся лучи, — а из неё проявляется
/// кристальное сердце с лёгким перебросом. Снятие лайка — без вспышки.
/// Анимация только на нажатие: при первом показе уже отмеченное сердце
/// просто видно. Ход анимации тот же, что на Android (LikeHeart.kt).
struct LikeHeart: View {
    let liked: Bool
    var color: Color = Palette.mist
    var size: CGFloat = 22

    /// Начало анимации; nil — покой.
    @State private var start: Date?

    var body: some View {
        TimelineView(.animation(paused: start == nil)) { timeline in
            let p = progress(at: timeline.date)
            let k = liked ? LikeMotion.heartScale(p) : 1
            Glyph(kind: liked ? .heartFilled : .heart, color: color, size: size)
                .scaleEffect(k)
                .opacity(liked ? min(1, k * 1.4) : 1)
                // Искра — поверх и шире значка: на вёрстку не влияет
                .overlay {
                    if liked && p < 1 {
                        Canvas { context, canvas in
                            LikeMotion.drawSpark(&context, progress: p, canvas: canvas.width, icon: size)
                        }
                        .frame(width: size * 3, height: size * 3)
                        .allowsHitTesting(false)
                    }
                }
        }
        .onChange(of: liked) { _, now in
            start = now ? Date() : nil
        }
        .task(id: start) {
            // Анимация кончилась — часы останавливаются
            guard start != nil else { return }
            try? await Task.sleep(nanoseconds: UInt64(LikeMotion.duration * 1_000_000_000))
            start = nil
        }
    }

    private func progress(at date: Date) -> Double {
        guard let start else { return 1 }
        return min(1, max(0, date.timeIntervalSince(start) / LikeMotion.duration))
    }
}

/// Ход анимации лайка: масштаб сердца и рисунок искры по времени 0…1.
enum LikeMotion {
    /// Длительность всей анимации, секунд.
    static let duration = 0.7

    /// Первые 25% — пусто (горит искра), затем сердце вырастает до 1.28 и
    /// опускается к 1.
    static func heartScale(_ p: Double) -> Double {
        if p < 0.25 { return 0 }
        if p < 0.6 { return 1.28 * easeOut((p - 0.25) / 0.35) }
        return 1.28 - 0.28 * easeOut((p - 0.6) / 0.4)
    }

    private static func easeOut(_ t: Double) -> Double { 1 - (1 - t) * (1 - t) }

    /// Искра в центре холста шириной canvas; icon — размер самого сердца.
    static func drawSpark(_ context: inout GraphicsContext, progress p: Double, canvas: CGFloat, icon s: CGFloat) {
        let c = CGPoint(x: canvas / 2, y: canvas / 2)
        // Вспышка: звезда растёт и гаснет за первые 45%
        if p < 0.45 {
            let t = p / 0.45
            let r = s * CGFloat(0.25 + 0.55 * easeOut(t))
            let a = 1 - t
            var star = Path()
            star.move(to: CGPoint(x: c.x, y: c.y - r))
            star.addQuadCurve(to: CGPoint(x: c.x + r, y: c.y), control: c)
            star.addQuadCurve(to: CGPoint(x: c.x, y: c.y + r), control: c)
            star.addQuadCurve(to: CGPoint(x: c.x - r, y: c.y), control: c)
            star.addQuadCurve(to: CGPoint(x: c.x, y: c.y - r), control: c)
            star.closeSubpath()
            context.fill(star, with: .color(RGB(0xFFF4FF).color.opacity(a)))
            let glow = Path(ellipseIn: CGRect(x: c.x - r * 0.9, y: c.y - r * 0.9, width: r * 1.8, height: r * 1.8))
            context.fill(glow, with: .color(Glyph.crystalColors[1].opacity(0.35 * a)))
        }
        // Лучи разлетаются: восемь штрихов в цветах кристалла
        if p >= 0.08 && p <= 0.85 {
            let t = (p - 0.08) / 0.77
            let inner = s * CGFloat(0.35 + 0.55 * easeOut(t))
            let outer = inner + s * 0.22 * CGFloat(1 - t)
            for k in 0..<8 {
                let angle = Double(k) * .pi / 4 + .pi / 8
                let dx = CGFloat(cos(angle))
                let dy = CGFloat(sin(angle))
                var ray = Path()
                ray.move(to: CGPoint(x: c.x + dx * inner, y: c.y + dy * inner))
                ray.addLine(to: CGPoint(x: c.x + dx * outer, y: c.y + dy * outer))
                let colorIndex = k % Glyph.crystalColors.count
                context.stroke(
                    ray,
                    with: .color(Glyph.crystalColors[colorIndex].opacity(1 - t)),
                    style: StrokeStyle(lineWidth: 1.6, lineCap: .round)
                )
            }
        }
    }
}
