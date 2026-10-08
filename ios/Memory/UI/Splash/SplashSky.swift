import SwiftUI

/// Фон заставки без картинок: ночное небо в оттенках палитры —
/// сиреневые, сливовые и синие облака-туманности и звёзды. Всё рисуется
/// кодом, поэтому чёткое на любом экране и ничего не весит.
enum SplashSky {
    /// Облако: центр и радиус в долях ширины экрана, цвет, прозрачность.
    private struct Cloud {
        let x: Double
        let y: Double
        let r: Double
        let color: RGB
        let alpha: Double
    }

    private static let clouds: [Cloud] = [
        Cloud(x: 0.50, y: 1.02, r: 0.95, color: Palette.plumRGB, alpha: 0.85),    // сливовая дымка снизу
        Cloud(x: 0.22, y: 0.74, r: 0.70, color: Palette.orchidRGB, alpha: 0.55),  // орхидея слева
        Cloud(x: 0.42, y: 0.86, r: 0.55, color: Palette.lilacRGB, alpha: 0.38),   // светлая сирень
        Cloud(x: 0.72, y: 0.60, r: 0.62, color: Palette.violetRGB, alpha: 0.60),  // фиолетовый
        Cloud(x: 0.90, y: 0.42, r: 0.55, color: Palette.oceanRGB, alpha: 0.55),   // синий справа
        Cloud(x: 0.95, y: 0.30, r: 0.35, color: Palette.skyRGB, alpha: 0.30),     // небесная кромка
        Cloud(x: 0.10, y: 0.30, r: 0.45, color: Palette.violetRGB, alpha: 0.30),  // отсвет слева сверху
        Cloud(x: 0.55, y: 0.50, r: 0.40, color: Palette.orchidRGB, alpha: 0.22),  // мягкий свет за словом
    ]

    /// Звезда: положение 0...1, яркость, фаза и скорость мерцания.
    private struct Star {
        let x: Double
        let y: Double
        let b: Double
        let phase: Double
        let speed: Double
    }

    private static let stars: [Star] = {
        var rnd = SeededRandom(42)
        return (0..<260).map { _ in
            let x = rnd.nextDouble()
            let yRaw = rnd.nextDouble()
            let bRaw = rnd.nextDouble()
            // гуще наверху, реже в облаках внизу
            return Star(
                x: x,
                y: yRaw * yRaw * 0.95,
                b: bRaw * bRaw * bRaw,
                phase: rnd.nextDouble() * 6.283,
                speed: 0.0015 + rnd.nextDouble() * 0.003
            )
        }
    }()

    /// Рисует небо. appear — 0...1, проявление из темноты; time — мс, для мерцания звёзд.
    static func draw(_ context: inout GraphicsContext, size: CGSize, appear: Double, time: Double) {
        let rect = CGRect(origin: .zero, size: size)
        let w = size.width
        let h = size.height
        context.fill(Path(rect), with: .color(Palette.void))

        var sky = context
        sky.opacity = appear
        sky.fill(Path(rect), with: .linearGradient(
            Gradient(stops: [
                .init(color: RGB(0x05071A).color, location: 0),
                .init(color: Palette.night, location: 0.45),
                .init(color: RGB(0x1A1F5C).color, location: 0.8),
                .init(color: RGB(0x221A4A).color, location: 1),
            ]),
            startPoint: CGPoint(x: 0, y: 0),
            endPoint: CGPoint(x: 0, y: h)
        ))
        for c in clouds {
            sky.fill(Path(rect), with: .radialGradient(
                Gradient(colors: [c.color.opacity(c.alpha), c.color.opacity(c.alpha * 0.35), c.color.opacity(0)]),
                center: CGPoint(x: c.x * w, y: c.y * h),
                startRadius: 0,
                endRadius: c.r * w
            ))
        }
        for s in stars {
            let twinkle = 0.7 + 0.3 * sin(time * s.speed + s.phase)
            let a = (0.25 + 0.75 * s.b) * twinkle * appear
            let r = (0.6 + 1.3 * s.b) * 0.7
            let center = CGPoint(x: s.x * w, y: s.y * h)
            if s.b > 0.5 {
                context.fill(circle(center, r * 2.5), with: .color(Palette.lilacRGB.opacity(a * 0.15)))
            }
            context.fill(circle(center, r), with: .color(Palette.inkRGB.opacity(a)))
        }
    }

    static func circle(_ c: CGPoint, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2))
    }
}

/// Небо заставки как отдельный фон — для экрана входа.
struct SplashSkyView: View {
    var body: some View {
        Canvas { context, size in
            SplashSky.draw(&context, size: size, appear: 1, time: 0)
        }
        .ignoresSafeArea()
    }
}
