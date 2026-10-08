import SwiftUI

/// «Видео» PRISM, пока нет настоящих роликов: живая туманность в цветах
/// палитры, вращающийся кристалл и всплывающие искры. Всё — функция от
/// времени ролика (timeMs), поэтому пауза и перемотка работают честно.
struct PrismSpec {
    struct Blob {
        let x: Double
        let y: Double
        let r: Double
        let color: RGB
        let alpha: Double
        let speed: Double
        let phase: Double
    }

    struct Spark {
        let x: Double
        let y: Double
        let size: Double
        let speed: Double
        let phase: Double
    }

    let phase: Double
    let blobs: [Blob]
    let sparks: [Spark]

    init(seed: Int) {
        var rnd = SeededRandom(seed)
        let colors = [Palette.orchidRGB, Palette.violetRGB, Palette.lilacRGB, Palette.oceanRGB, Palette.skyRGB, Palette.plumRGB]
        phase = rnd.nextDouble() * 2 * Double.pi
        var blobs: [Blob] = []
        for _ in 0..<6 {
            let x = rnd.nextDouble()
            let y = 0.2 + rnd.nextDouble() * 0.8
            let r = 0.4 + rnd.nextDouble() * 0.5
            let color = colors[rnd.nextInt(colors.count)]
            let alpha = 0.35 + rnd.nextDouble() * 0.35
            let speed = 0.2 + rnd.nextDouble() * 0.5
            let phase = rnd.nextDouble() * 6.28
            blobs.append(Blob(x: x, y: y, r: r, color: color, alpha: alpha, speed: speed, phase: phase))
        }
        self.blobs = blobs
        var sparks: [Spark] = []
        for _ in 0..<70 {
            let x = rnd.nextDouble()
            let y = rnd.nextDouble()
            let size = (1.2 + rnd.nextDouble() * 2.8) / 2
            let speed = 0.03 + rnd.nextDouble() * 0.08
            let phase = rnd.nextDouble() * 6.28
            sparks.append(Spark(x: x, y: y, size: size, speed: speed, phase: phase))
        }
        self.sparks = sparks
    }

    func draw(_ context: inout GraphicsContext, size: CGSize, timeMs: Double) {
        let t = timeMs / 1000
        let w = size.width
        let h = size.height
        let rect = CGRect(origin: .zero, size: size)
        context.fill(Path(rect), with: .linearGradient(
            Gradient(colors: [RGB(0x05071A).color, Palette.night, RGB(0x1A1F5C).color]),
            startPoint: .zero, endPoint: CGPoint(x: 0, y: h)
        ))
        // Облака плывут по своим орбитам
        for b in blobs {
            let cx = (b.x + 0.12 * sin(t * b.speed + b.phase)) * w
            let cy = (b.y + 0.08 * cos(t * b.speed * 0.8 + b.phase)) * h
            context.fill(Path(rect), with: .radialGradient(
                Gradient(colors: [b.color.opacity(b.alpha), b.color.opacity(b.alpha * 0.3), b.color.opacity(0)]),
                center: CGPoint(x: cx, y: cy), startRadius: 0, endRadius: b.r * w
            ))
        }
        // Искры всплывают снизу вверх и мерцают
        for s in sparks {
            let raw = (s.y - t * s.speed).truncatingRemainder(dividingBy: 1)
            let y = raw < 0 ? raw + 1 : raw
            let x = s.x + 0.02 * sin(t * 2 + s.phase)
            let a = 0.3 + 0.7 * ((sin(t * 3 + s.phase) + 1) / 2)
            context.fill(
                SplashSky.circle(CGPoint(x: x * w, y: y * h), s.size),
                with: .color(Palette.inkRGB.opacity(a * 0.8))
            )
        }
        // Кристалл: ромб, который медленно поворачивается вокруг вертикали
        let cx = w / 2
        let cy = h * 0.42
        let ch = h * 0.18
        let turn = cos(t * 0.9 + phase)
        let cw = ch * 0.55 * (0.35 + 0.65 * abs(turn))
        var crystal = Path()
        crystal.move(to: CGPoint(x: cx, y: cy - ch))
        crystal.addLine(to: CGPoint(x: cx + cw, y: cy))
        crystal.addLine(to: CGPoint(x: cx, y: cy + ch))
        crystal.addLine(to: CGPoint(x: cx - cw, y: cy))
        crystal.closeSubpath()
        crystal.move(to: CGPoint(x: cx - cw, y: cy))
        crystal.addLine(to: CGPoint(x: cx + cw, y: cy))
        crystal.move(to: CGPoint(x: cx, y: cy - ch))
        crystal.addLine(to: CGPoint(x: cx + cw * turn * 0.4, y: cy))
        crystal.addLine(to: CGPoint(x: cx, y: cy + ch))
        let glowRadius = ch * 1.6
        context.fill(
            SplashSky.circle(CGPoint(x: cx, y: cy), glowRadius),
            with: .radialGradient(
                Gradient(colors: [Palette.lilacRGB.opacity(0.35), Palette.lilacRGB.opacity(0)]),
                center: CGPoint(x: cx, y: cy), startRadius: 0, endRadius: glowRadius
            )
        )
        context.stroke(
            crystal,
            with: .linearGradient(
                Gradient(colors: [RGB(0xFF9EDE).color, RGB(0xC6A2FF).color, RGB(0x8CC4FF).color]),
                startPoint: CGPoint(x: cx - cw, y: cy - ch), endPoint: CGPoint(x: cx + cw, y: cy + ch)
            ),
            style: StrokeStyle(lineWidth: 1.5, lineJoin: .round)
        )
        context.stroke(
            SplashSky.circle(CGPoint(x: cx, y: cy), ch * 1.25),
            with: .color(Palette.lilacRGB.opacity(0.3)),
            lineWidth: 0.8
        )
    }
}

struct PrismArt: View {
    let spec: PrismSpec
    let timeMs: Double

    var body: some View {
        Canvas { context, size in
            spec.draw(&context, size: size, timeMs: timeMs)
        }
    }
}
