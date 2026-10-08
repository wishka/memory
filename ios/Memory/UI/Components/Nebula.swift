import SwiftUI

/// Процедурная «фотография» к записи — туманность с кристаллом. Пока у
/// Memory нет сервера и загрузки фото, так записи с картинкой выглядят
/// живыми. seed задаёт картину: одна и та же запись всегда выглядит одинаково.
struct Nebula: View {
    let seed: Int

    private struct Blob {
        let x: Double
        let y: Double
        let r: Double
        let color: RGB
        let alpha: Double
    }

    private struct Spec {
        let blobs: [Blob]
        let stars: [(Double, Double, Double)]
        let crystalX: Double

        init(seed: Int) {
            var rnd = SeededRandom(seed)
            let colors = [Palette.orchidRGB, Palette.skyRGB, Palette.violetRGB, Palette.lilacRGB, Palette.oceanRGB]
            var blobs: [Blob] = []
            for _ in 0..<4 {
                let x = rnd.nextDouble()
                let y = rnd.nextDouble()
                let r = 0.35 + rnd.nextDouble() * 0.5
                let color = colors[rnd.nextInt(colors.count)]
                let alpha = 0.35 + rnd.nextDouble() * 0.35
                blobs.append(Blob(x: x, y: y, r: r, color: color, alpha: alpha))
            }
            self.blobs = blobs
            var stars: [(Double, Double, Double)] = []
            for _ in 0..<60 {
                stars.append((rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble()))
            }
            self.stars = stars
            crystalX = 0.3 + rnd.nextDouble() * 0.4
        }
    }

    var body: some View {
        let spec = Spec(seed: seed)
        Canvas { context, size in
            let w = size.width
            let h = size.height
            let rect = CGRect(origin: .zero, size: size)
            context.fill(Path(rect), with: .linearGradient(
                Gradient(colors: [Palette.night, Palette.void]),
                startPoint: .zero, endPoint: CGPoint(x: 0, y: h)
            ))
            for b in spec.blobs {
                context.fill(Path(rect), with: .radialGradient(
                    Gradient(colors: [b.color.opacity(b.alpha), b.color.opacity(0)]),
                    center: CGPoint(x: b.x * w, y: b.y * h),
                    startRadius: 0, endRadius: b.r * w
                ))
            }
            for (x, y, s) in spec.stars {
                let r = (0.6 + s * 1.6) / 2
                context.fill(
                    SplashSky.circle(CGPoint(x: x * w, y: y * h), r),
                    with: .color(Palette.inkRGB.opacity(0.25 + s * 0.6))
                )
            }
            // Кристалл-ромб тонкой линией — фирменный знак Memory
            let cx = spec.crystalX * w + w * 0.15
            let cy = h * 0.5
            let ch = h * 0.32
            let cw = ch * 0.55
            var crystal = Path()
            crystal.move(to: CGPoint(x: cx, y: cy - ch))
            crystal.addLine(to: CGPoint(x: cx + cw, y: cy))
            crystal.addLine(to: CGPoint(x: cx, y: cy + ch))
            crystal.addLine(to: CGPoint(x: cx - cw, y: cy))
            crystal.closeSubpath()
            crystal.move(to: CGPoint(x: cx - cw, y: cy))
            crystal.addLine(to: CGPoint(x: cx + cw, y: cy))
            crystal.move(to: CGPoint(x: cx, y: cy - ch))
            crystal.addLine(to: CGPoint(x: cx, y: cy + ch))
            context.stroke(
                crystal,
                with: .linearGradient(
                    Gradient(colors: [Palette.lilac, Palette.sky]),
                    startPoint: CGPoint(x: cx - cw, y: cy - ch), endPoint: CGPoint(x: cx + cw, y: cy + ch)
                ),
                lineWidth: 1
            )
            context.stroke(
                SplashSky.circle(CGPoint(x: cx, y: cy), ch * 0.9),
                with: .color(Palette.lilacRGB.opacity(0.25)),
                lineWidth: 0.6
            )
        }
    }
}
