import SwiftUI
import UIKit

/// Как ведёт себя слово на заставке:
/// assemble — собирается из атомов с первой буквы и становится кристальным
/// стеклом; assembleFromCenter — то же, но от середины к краям;
/// dissolve — рассыпается на атомы с последней буквы.
let splashMode: SplashMode = .assemble

/// Перламутровые цвета атомов в полёте — те же, что у стекла букв.
private let atomTones: [RGB] = [RGB(0xFF9EDE), RGB(0xC6A2FF), RGB(0x8CC4FF), RGB(0xFFF4FF), RGB(0xFFDB8F)]

/// Заставка: ночное небо с облаками в оттенках палитры и мерцающими
/// звёздами, в центре слово MEMORY из атомов. Когда анимация
/// заканчивается, вызывается onFinished. Нажатие пропускает заставку.
struct AtomSplash: View {
    var mode: SplashMode = splashMode
    let onFinished: () -> Void

    @Environment(\.displayScale) private var displayScale
    @State private var scene: SplashScene?
    @State private var start = Date()
    @State private var finished = false

    var body: some View {
        GeometryReader { geo in
            TimelineView(.animation) { timeline in
                let t = max(0, timeline.date.timeIntervalSince(start) * 1000)
                Canvas { context, size in
                    // Небо с облаками и звёздами — проявляется из темноты
                    SplashSky.draw(&context, size: size, appear: min(1, t / 500), time: t)
                    if let scene {
                        scene.draw(&context, size: size, t: t)
                    }
                }
            }
            .onAppear { prepare(geo.size) }
            .onChange(of: geo.size) { _, newSize in prepare(newSize) }
        }
        .ignoresSafeArea()
        .background(Palette.void)
        .contentShape(Rectangle())
        .onTapGesture { finish() }
        .task(id: scene?.field.totalMs ?? -1) {
            guard let total = scene?.field.totalMs else { return }
            let left = max(0, total - Date().timeIntervalSince(start) * 1000)
            try? await Task.sleep(nanoseconds: UInt64(left * 1_000_000))
            if !Task.isCancelled { finish() }
        }
    }

    private func prepare(_ size: CGSize) {
        guard size.width > 0, size.height > 0 else { return }
        if let scene, scene.pointSize == size { return }
        scene = SplashScene.build(word: "MEMORY", size: size, scale: displayScale, mode: mode)
        start = Date()
    }

    private func finish() {
        if finished { return }
        finished = true
        onFinished()
    }
}

/// Всё, что заставке нужно заранее: атомы, кристальное слово и где оно стоит.
/// Внутри — пиксели экрана, как на Android: те же скорости и шаги атомов.
final class SplashScene {
    let field: AtomField
    let art: CrystalWord.Art?
    /// Контур слова в координатах полосы слова (пиксели).
    let path: CGPath?
    /// Верх полосы слова на экране, пиксели.
    let top: CGFloat
    let boxHeight: Int
    let widthPx: Int
    let scale: CGFloat
    let pointSize: CGSize

    init(field: AtomField, art: CrystalWord.Art?, path: CGPath?, top: CGFloat, boxHeight: Int, widthPx: Int, scale: CGFloat, pointSize: CGSize) {
        self.field = field
        self.art = art
        self.path = path
        self.top = top
        self.boxHeight = boxHeight
        self.widthPx = widthPx
        self.scale = scale
        self.pointSize = pointSize
    }

    /// Слово рисуется во внеэкранное изображение шрифтом Unbounded, и
    /// каждая непрозрачная точка с шагом step становится атомом. Для
    /// режимов сборки рядом рисуется кристальная версия слова.
    static func build(word: String, size: CGSize, scale: CGFloat, mode: SplashMode) -> SplashScene {
        let s = max(1, scale)
        let width = Int((size.width * s).rounded())
        let height = Int((size.height * s).rounded())
        let crystalMode = mode != .dissolve
        // Стеклу нужна толщина: для кристалла — полужирный, для атомов — обычный
        let fontName = crystalMode ? Fonts.unboundedSemiBold : Fonts.unboundedRegular

        // Ширина слова — 82% экрана; разрядка — 0.1 кегля, как на Android
        let probe = Fonts.uiFont(fontName, size: 100)
        let probeWidth = max(1, CrystalWord.wordWidth(word, font: probe, kern: 10))
        let textSize = 100 * CGFloat(width) * 0.82 / probeWidth
        let font = Fonts.uiFont(fontName, size: textSize)

        let boxHeight = max(1, Int(textSize * 1.8))
        let baseline = CGFloat(boxHeight) / 2 + (font.ascender + font.descender) / 2
        // Слово — в центре экрана
        let top = CGFloat(height) * 0.5 - CGFloat(boxHeight) / 2
        let path = CrystalWord.wordPath(word, font: font, kern: textSize * 0.1, centerX: CGFloat(width) / 2, baseline: baseline)

        // Маска слова в оттенках серого: 0 — пусто, 255 — буква
        var xs: [Double] = []
        var ys: [Double] = []
        if let mask = CGContext(
            data: nil, width: width, height: boxHeight, bitsPerComponent: 8, bytesPerRow: 0,
            space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGImageAlphaInfo.none.rawValue
        ) {
            mask.translateBy(x: 0, y: CGFloat(boxHeight))
            mask.scaleBy(x: 1, y: -1)
            mask.setFillColor(gray: 1, alpha: 1)
            mask.addPath(path)
            mask.fillPath()
            if let data = mask.data {
                let stride = mask.bytesPerRow
                let pixels = data.bindMemory(to: UInt8.self, capacity: stride * boxHeight)
                // Шаг ~1.7pt: атомов достаточно, чтобы буквы читались
                // сплошными, и не так много, чтобы терялись кадры
                let step = max(2, Int(1.7 * s))
                var y = 0
                while y < boxHeight {
                    var x = (y / step) % 2   // сдвиг через ряд — без видимой сетки
                    while x < width {
                        if pixels[y * stride + x] > 110 {
                            xs.append(Double(x))
                            ys.append(Double(top) + Double(y))
                        }
                        x += step
                    }
                    y += step
                }
            }
        }
        let art = crystalMode ? CrystalWord.render(path: path, ts: textSize, width: width, height: boxHeight) : nil
        return SplashScene(
            field: AtomField(xs: xs, ys: ys, mode: mode),
            art: art, path: path, top: top, boxHeight: boxHeight,
            widthPx: width, scale: s, pointSize: size
        )
    }

    /// Кадр заставки поверх неба. t — мс от начала.
    func draw(_ context: inout GraphicsContext, size: CGSize, t: Double) {
        var px = context
        // Дальше всё в пикселях экрана
        px.scaleBy(x: 1 / scale, y: 1 / scale)
        let widthPx = CGFloat(self.widthPx)
        let full = CGRect(x: 0, y: 0, width: widthPx, height: size.height * scale)
        let center = CGPoint(x: widthPx / 2, y: top + CGFloat(boxHeight) / 2)

        // Лёгкая дымка за словом, чтобы стекло читалось на небе
        px.fill(Path(full), with: .radialGradient(
            Gradient(colors: [Palette.voidRGB.opacity(0.45), Palette.voidRGB.opacity(0)]),
            center: center, startRadius: 0, endRadius: widthPx * 0.6
        ))

        drawAtoms(&px, t: t)

        // Кристалл: атомы растворяются в стекле
        let k = field.crystal(t)
        if let art, k > 0 {
            var glass = px
            glass.opacity = k
            glass.draw(
                Image(decorative: art.crystal, scale: 1),
                in: CGRect(x: 0, y: top, width: widthPx, height: CGFloat(boxHeight))
            )
        }

        // Блик пробегает по стеклу слева направо
        if let s = field.shimmer(t), let art, let path {
            let b = art.bounds
            let bandX = b.minX - b.width * 0.3 + CGFloat(s) * b.width * 1.6
            var band = px
            band.translateBy(x: 0, y: top)
            band.clip(to: Path(path))
            band.fill(
                Path(CGRect(x: 0, y: 0, width: widthPx, height: CGFloat(boxHeight))),
                with: .linearGradient(
                    Gradient(colors: [.white.opacity(0), .white.opacity(0.85), .white.opacity(0)]),
                    startPoint: CGPoint(x: bandX - b.height * 0.6, y: 0),
                    endPoint: CGPoint(x: bandX + b.height * 0.6, y: CGFloat(boxHeight))
                )
            )
        }
    }

    /// Атомы. Тысячи кружков за кадр рисуются пачками: цвет и прозрачность
    /// округляются до ступеней, и каждая ступень — один путь.
    private func drawAtoms(_ context: inout GraphicsContext, t: Double) {
        let mixSteps = 4
        let alphaSteps = 12
        let buckets = atomTones.count * mixSteps * alphaSteps
        var cores = [Path](repeating: Path(), count: buckets)
        var glows = [Path](repeating: Path(), count: buckets)
        var used = [Bool](repeating: false, count: buckets)
        let dot = 1.1 * Double(scale)

        for i in 0..<field.size {
            if !field.alive(i, t) { continue }
            let a = field.alpha(i, t)
            if a <= 0.01 { continue }
            let d = field.decay(i, t)
            let tone = min(atomTones.count - 1, max(0, Int(field.hue[i] * Double(atomTones.count))))
            let mix = min(mixSteps - 1, Int((min(1, d * 2.2) * Double(mixSteps - 1)).rounded()))
            let level = min(alphaSteps - 1, max(0, Int((a * Double(alphaSteps - 1)).rounded())))
            let key = (tone * mixSteps + mix) * alphaSteps + level
            let x = field.x(i, t)
            let y = field.y(i, t)
            let r = dot * field.radiusScale(i, t)
            cores[key].addEllipse(in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
            if d > 0 {
                glows[key].addEllipse(in: CGRect(x: x - r * 3, y: y - r * 3, width: r * 6, height: r * 6))
            }
            used[key] = true
        }

        for key in 0..<buckets where used[key] {
            let level = key % alphaSteps
            let mix = (key / alphaSteps) % mixSteps
            let tone = key / (alphaSteps * mixSteps)
            let a = Double(level) / Double(alphaSteps - 1)
            let rgb = Palette.inkRGB.lerp(atomTones[tone], Double(mix) / Double(mixSteps - 1))
            if !glows[key].isEmpty {
                context.fill(glows[key], with: .color(rgb.opacity(a * 0.14)))
            }
            context.fill(cores[key], with: .color(rgb.opacity(a)))
        }
    }
}
