import PhotosUI
import SwiftUI
import UniformTypeIdentifiers

/// Фон переписки. Все фоны коллекции рисуются кодом — тёмные, в цветах
/// Memory, чтобы пузыри читались. Своё фото — с затемнением. Те же фоны —
/// ChatBackdrop.kt.
struct BackdropView: View {
    let backdrop: Backdrop

    var body: some View {
        let style = backdrop.kind
        switch style {
        case .photo:
            BackdropPhoto(fileName: backdrop.photo, dim: backdrop.dim)
        case .cosmos:
            CosmicBackground()
        default:
            let dots = Self.dots(style)
            Canvas { context, size in
                Self.draw(style, dots, in: &context, size: size)
            }
            .ignoresSafeArea()
        }
    }

    private static func dots(_ style: BackdropStyle) -> [(Double, Double, Double)] {
        var rnd = SeededRandom(style.index * 31 + 7)
        return (0..<90).map { _ in (rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble()) }
    }

    private static func rgb(_ hex: UInt32) -> RGB { RGB(hex) }

    // swiftlint:disable:next function_body_length cyclomatic_complexity
    private static func draw(_ style: BackdropStyle, _ dots: [(Double, Double, Double)], in ctx: inout GraphicsContext, size: CGSize) {
        let w = size.width
        let h = size.height
        let rect = Path(CGRect(origin: .zero, size: size))

        func fill(_ color: Color) { ctx.fill(rect, with: .color(color)) }
        func vertical(_ colors: [Color]) {
            ctx.fill(rect, with: .linearGradient(Gradient(colors: colors), startPoint: .zero, endPoint: CGPoint(x: 0, y: h)))
        }
        func glow(_ color: Color, _ x: Double, _ y: Double, _ r: Double) {
            ctx.fill(rect, with: .radialGradient(
                Gradient(colors: [color, color.opacity(0)]),
                center: CGPoint(x: w * x, y: h * y), startRadius: 0, endRadius: w * r
            ))
        }
        func stars(_ alpha: Double = 0.5, count: Int = 90) {
            for (x, y, s) in dots.prefix(count) {
                let r = 0.6 + s * 1.4
                ctx.fill(Path(ellipseIn: CGRect(x: x * w - r, y: y * h - r, width: r * 2, height: r * 2)),
                         with: .color(Palette.inkRGB.opacity(min(1, 0.12 + s * alpha))))
            }
        }
        func diamond(_ cx: Double, _ cy: Double, _ dw: Double, _ dh: Double) -> Path {
            var p = Path()
            p.move(to: CGPoint(x: cx, y: cy - dh))
            p.addLine(to: CGPoint(x: cx + dw, y: cy))
            p.addLine(to: CGPoint(x: cx, y: cy + dh))
            p.addLine(to: CGPoint(x: cx - dw, y: cy))
            p.closeSubpath()
            return p
        }
        func line(_ a: CGPoint, _ b: CGPoint, _ color: Color, _ width: CGFloat) {
            var p = Path()
            p.move(to: a)
            p.addLine(to: b)
            ctx.stroke(p, with: .color(color), lineWidth: width)
        }

        switch style {
        case .cosmos, .photo:
            break
        case .nebula:
            fill(Palette.void)
            glow(Palette.orchidRGB.opacity(0.45), 0.25, 0.3, 0.8)
            glow(RGB(0xFF9EDE).opacity(0.18), 0.75, 0.5, 0.6)
            glow(Palette.oceanRGB.opacity(0.40), 0.6, 0.85, 0.9)
            stars(0.5)
        case .aurora:
            vertical([rgb(0x061226).color, Palette.void])
            // Занавеси сияния: волнистые полосы, тающие книзу
            let curtains: [(RGB, Double)] = [(RGB(0x7BE0B0), 0.18), (Palette.lilacRGB, 0.30), (Palette.skyRGB, 0.42)]
            for (i, item) in curtains.enumerated() {
                let (c, top) = item
                var path = Path()
                path.move(to: CGPoint(x: 0, y: h * top))
                var x: Double = 0
                while x <= w {
                    let wave = sin(x / w * 2 * Double.pi * (1.3 + Double(i) * 0.4) + Double(i))
                    path.addLine(to: CGPoint(x: x, y: h * top + wave * h * 0.05))
                    x += w / 40
                }
                path.addLine(to: CGPoint(x: w, y: h * (top + 0.35)))
                path.addLine(to: CGPoint(x: 0, y: h * (top + 0.35)))
                path.closeSubpath()
                ctx.fill(path, with: .linearGradient(
                    Gradient(colors: [c.opacity(0.30), c.opacity(0)]),
                    startPoint: CGPoint(x: 0, y: h * top - h * 0.05), endPoint: CGPoint(x: 0, y: h * (top + 0.35))
                ))
            }
            stars(0.4, count: 50)
        case .crystals:
            vertical([Palette.night, Palette.void])
            glow(Palette.violetRGB.opacity(0.35), 0.8, 0.15, 0.7)
            for (x, y, s) in dots.prefix(26) {
                let r = w * (0.03 + s * 0.07)
                let d = diamond(x * w, y * h, r * 0.6, r)
                ctx.fill(d, with: .color(Palette.lilacRGB.opacity(0.05 + s * 0.06)))
                ctx.stroke(d, with: .color(Palette.lilacRGB.opacity(0.12 + s * 0.12)), lineWidth: 1.2)
            }
        case .constellations:
            fill(Palette.void)
            glow(Palette.oceanRGB.opacity(0.25), 0.1, 0.9, 0.9)
            stars(0.6)
            // Созвездия: соседние звёзды из первых — по линиям
            let pts = dots.prefix(24).map { CGPoint(x: $0.0 * w, y: $0.1 * h) }
            var i = 0
            while i < pts.count - 1 {
                line(pts[i], pts[i + 1], Palette.lilacRGB.opacity(0.22), 1.2)
                if i + 2 < pts.count { line(pts[i + 1], pts[i + 2], Palette.lilacRGB.opacity(0.18), 1.2) }
                i += 3
            }
            for p in pts {
                ctx.fill(Path(ellipseIn: CGRect(x: p.x - 2.2, y: p.y - 2.2, width: 4.4, height: 4.4)), with: .color(Palette.inkRGB.opacity(0.8)))
            }
        case .prism:
            fill(Palette.void)
            // Луч входит в кристалл и выходит радугой
            let cx = w * 0.5
            let cy = h * 0.42
            line(CGPoint(x: 0, y: cy - h * 0.12), CGPoint(x: cx - w * 0.08, y: cy), Palette.inkRGB.opacity(0.35), 3)
            let rainbow: [RGB] = [RGB(0xFF8A65), RGB(0xFFD86B), RGB(0x7BE0B0), Palette.skyRGB, Palette.orchidRGB, RGB(0xFF9EDE)]
            for (i, c) in rainbow.enumerated() {
                let spread = (Double(i) - 2.5) * h * 0.035
                var beam = Path()
                beam.move(to: CGPoint(x: cx + w * 0.06, y: cy))
                beam.addLine(to: CGPoint(x: w, y: cy + h * 0.12 + spread - h * 0.017))
                beam.addLine(to: CGPoint(x: w, y: cy + h * 0.12 + spread + h * 0.017))
                beam.closeSubpath()
                ctx.fill(beam, with: .color(c.opacity(0.22)))
            }
            let crystal = diamond(cx, cy, w * 0.08, w * 0.14)
            ctx.fill(crystal, with: .color(Palette.lilacRGB.opacity(0.18)))
            ctx.stroke(crystal, with: .color(Palette.inkRGB.opacity(0.45)), lineWidth: 2)
            stars(0.35, count: 40)
        case .dusk:
            vertical([rgb(0x120B2E).color, rgb(0x3A1E5C).color, rgb(0x6B2F6E).color])
            glow(RGB(0xFF9EDE).opacity(0.22), 0.5, 1.05, 0.9)
            stars(0.5, count: 40)
        case .deep:
            vertical([rgb(0x0B2A52).color, rgb(0x071634).color, Palette.void])
            glow(Palette.skyRGB.opacity(0.20), 0.5, -0.05, 0.9)
            for (x, y, s) in dots.prefix(30) {
                let r = 3 + s * 10
                ctx.stroke(Path(ellipseIn: CGRect(x: x * w - r, y: y * h - r, width: r * 2, height: r * 2)),
                           with: .color(Palette.skyRGB.opacity(0.10 + s * 0.12)), lineWidth: 1.2)
            }
        case .lilac:
            ctx.fill(rect, with: .linearGradient(
                Gradient(colors: [rgb(0x2B1E46).color, rgb(0x4A2F6E).color, rgb(0x2B1E46).color]),
                startPoint: .zero, endPoint: CGPoint(x: w, y: h)
            ))
            glow(Palette.lilacRGB.opacity(0.22), 0.7, 0.3, 0.7)
            for (x, y, s) in dots.prefix(40) {
                let r = 1.5 + s * 3
                ctx.fill(Path(ellipseIn: CGRect(x: x * w - r, y: y * h - r, width: r * 2, height: r * 2)),
                         with: .color(Palette.lilacRGB.opacity(0.08 + s * 0.10)))
            }
        case .plain:
            fill(Palette.night)
        }
    }
}

/// Своё фото фоном: обрезано по экрану и затемнено, чтобы пузыри читались.
private struct BackdropPhoto: View {
    let fileName: String
    let dim: Double
    @State private var image: UIImage?

    var body: some View {
        ZStack {
            Palette.void
            if let image {
                GeometryReader { geo in
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                        .frame(width: geo.size.width, height: geo.size.height)
                        .clipped()
                }
            }
            Color.black.opacity(min(0.85, max(0, dim)))
        }
        .ignoresSafeArea()
        .task(id: fileName) {
            let item = Attachment(id: fileName, kind: .photo, fileName: fileName)
            image = await Task.detached(priority: .userInitiated) { MediaFiles.thumbnail(item, maxPixel: 1600) }.value
        }
    }
}

/// Выбор фона: коллекция плитками, своё фото и затемнение. В чате ещё
/// «Как во всех переписках» — сбросить свой фон к общему.
struct BackdropChooser: View {
    let selected: Backdrop
    let onPick: (Backdrop) -> Void
    var onReset: (() -> Void)? = nil
    /// Подпись кнопки сброса: в переписке — «Как во всех переписках», в группе — «Как везде».
    var resetLabel: String = t("Как во всех переписках")
    @State private var photoItem: PhotosPickerItem?
    @State private var showPicker = false

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 10), count: 3)

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            preview
            LazyVGrid(columns: columns, spacing: 10) {
                ForEach(BackdropStyle.collection + [.photo]) { style in
                    tile(style)
                }
            }
            if selected.kind == .photo {
                Text(t("Затемнение — чтобы сообщения читались")).textStyle(.bodySmall, Palette.mist)
                Slider(value: Binding(get: { selected.dim }, set: { v in
                    var b = selected
                    b.dim = v
                    onPick(b)
                }), in: 0...0.8)
                .tint(Palette.lilac)
            }
            if let onReset {
                GhostButton(title: resetLabel, action: onReset)
            }
        }
        .photosPicker(isPresented: $showPicker, selection: $photoItem, matching: .images)
        .onChange(of: photoItem) { _, item in
            guard let item else { return }
            photoItem = nil
            let dim = selected.dim
            Task {
                guard let data = try? await item.loadTransferable(type: Data.self) else { return }
                let ext = item.supportedContentTypes.first?.preferredFilenameExtension ?? "jpg"
                // Копия — не в главном потоке: фото бывает в десятки мегабайт.
                // Поворот снимка (EXIF) остаётся в файле, его учитывает MediaFiles.thumbnail
                let saved = await Task.detached(priority: .userInitiated) { MediaFiles.save(data, kind: .photo, ext: ext) }.value
                if let copy = saved {
                    onPick(Backdrop(style: BackdropStyle.photo.id, photo: copy.fileName, dim: dim))
                }
            }
        }
    }

    /// Как будет выглядеть переписка.
    private var preview: some View {
        ZStack(alignment: .topLeading) {
            BackdropView(backdrop: selected)
            VStack(alignment: .leading, spacing: 8) {
                Text(t("Привет! Как тебе новый фон?"))
                    .textStyle(.bodySmall)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Palette.plumRGB.opacity(0.9)))
                    .frame(maxWidth: 220, alignment: .leading)
                HStack {
                    Spacer()
                    Text(t("Очень по-космически ✦"))
                        .textStyle(.bodySmall)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .fill(LinearGradient(colors: [Palette.orchid, Palette.violet], startPoint: .topLeading, endPoint: .bottomTrailing)))
                }
            }
            .padding(14)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 170)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(Palette.line, lineWidth: 1))
    }

    private func tile(_ style: BackdropStyle) -> some View {
        let active = selected.kind == style
        let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
        return Button {
            if style == .photo {
                showPicker = true
            } else {
                onPick(Backdrop(style: style.id, photo: "", dim: selected.dim))
            }
        } label: {
            VStack(spacing: 4) {
                Color.clear
                    .aspectRatio(0.62, contentMode: .fit)
                    .overlay {
                        ZStack {
                            if style == .photo {
                                if selected.kind == .photo {
                                    BackdropView(backdrop: selected)
                                } else {
                                    Palette.plumRGB.opacity(0.5)
                                }
                                Glyph(kind: .image, color: Palette.ink, size: 26)
                            } else {
                                BackdropView(backdrop: Backdrop(style: style.id))
                            }
                        }
                    }
                    .clipShape(shape)
                    .overlay(shape.stroke(active ? Palette.lilac : Palette.line, lineWidth: active ? 2.5 : 1))
                Text(style.title).textStyle(.label, active ? Palette.ink : Palette.mist).lineLimit(1)
            }
        }
        .buttonStyle(.plain)
    }
}
