import ImageIO
import SwiftUI
import UIKit

// MARK: - Гифки

/// Чтение гифок: кадры и их длительность — в одну «живую» UIImage. Уже
/// прочитанные лежат в кэше: в ленте и в выборе одна гифка встречается часто.
enum GifDecoder {
    private static let cache = NSCache<NSString, UIImage>()

    /// Вызывать не в главном потоке. maxPixel — сторона кадра (лёгкие превью в сетке).
    static func load(_ url: URL, maxPixel: CGFloat = 640) -> UIImage? {
        let key = "\(url.path)#\(Int(maxPixel))" as NSString
        if let hit = cache.object(forKey: key) { return hit }
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
        let count = CGImageSourceGetCount(source)
        guard count > 0 else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixel,
        ]
        var frames: [UIImage] = []
        var total: Double = 0
        for i in 0..<count {
            guard let cg = CGImageSourceCreateThumbnailAtIndex(source, i, options as CFDictionary) else { continue }
            frames.append(UIImage(cgImage: cg))
            total += delay(source, i)
        }
        guard !frames.isEmpty else { return nil }
        let image = frames.count == 1 ? frames[0] : UIImage.animatedImage(with: frames, duration: total)
        if let image { cache.setObject(image, forKey: key) }
        return image
    }

    /// Сколько кадр держится на экране; совсем короткие — как в браузерах, 0,1 с.
    private static func delay(_ source: CGImageSource, _ i: Int) -> Double {
        guard let props = CGImageSourceCopyPropertiesAtIndex(source, i, nil) as? [CFString: Any],
              let gif = props[kCGImagePropertyGIFDictionary] as? [CFString: Any] else { return 0.1 }
        let value = (gif[kCGImagePropertyGIFUnclampedDelayTime] as? Double) ?? (gif[kCGImagePropertyGIFDelayTime] as? Double) ?? 0.1
        return value < 0.02 ? 0.1 : value
    }
}

/// UIImageView в рамке SwiftUI: живая картинка играет сама.
private struct AnimatedImageView: UIViewRepresentable {
    let image: UIImage?
    var crop: Bool

    func makeUIView(context: Context) -> UIView {
        let container = UIView()
        container.clipsToBounds = true
        let view = UIImageView()
        view.frame = container.bounds
        view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.tag = 1
        container.addSubview(view)
        return container
    }

    func updateUIView(_ container: UIView, context: Context) {
        guard let view = container.viewWithTag(1) as? UIImageView else { return }
        view.contentMode = crop ? .scaleAspectFill : .scaleAspectFit
        if view.image !== image {
            view.image = image
            view.startAnimating()
        }
    }
}

/// Гифка, которая играет сама. crop — заполнить рамку (плитки), иначе вписать.
struct GifImage: View {
    let url: URL?
    var crop: Bool = false
    var maxPixel: CGFloat = 640
    @State private var image: UIImage?

    var body: some View {
        AnimatedImageView(image: image, crop: crop)
            .task(id: url) {
                guard let url else { image = nil; return }
                let side = maxPixel
                image = await Task.detached(priority: .userInitiated) { GifDecoder.load(url, maxPixel: side) }.value
            }
    }
}

extension GifImage {
    /// Гифка-вложение из папки вложений.
    init(attachment: Attachment, crop: Bool = false, maxPixel: CGFloat = 640) {
        self.init(url: MediaFiles.url(attachment), crop: crop, maxPixel: maxPixel)
    }
}

/// Метка «GIF» на живой картинке.
struct GifBadge: View {
    var body: some View {
        Text("GIF")
            .textStyle(.label)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(RoundedRectangle(cornerRadius: 6, style: .continuous).fill(Palette.voidRGB.opacity(0.6)))
    }
}

// MARK: - Живые стикеры

/// Живой стикер из готовых картинок — и для студии, где кадры ещё не
/// сохранены: кадры по кругу и движение (прыжок, пульс, мерцание…).
struct LiveFrames: View {
    let images: [UIImage]
    var fps: Int = 0
    var animation: String = ""

    var body: some View {
        let anim = StickerAnim.of(animation)
        if images.isEmpty {
            Color.clear
        } else if anim == .none && images.count < 2 {
            Image(uiImage: images[0]).resizable().scaledToFit()
        } else {
            TimelineView(.animation) { timeline in
                let time = timeline.date.timeIntervalSinceReferenceDate
                let step = 1.0 / Double(min(24, max(2, fps == 0 ? 6 : fps)))
                let frame = images.count > 1 ? Int(time / step) % images.count : 0
                let period = Double(anim.periodMs) / 1000
                let phase = anim == .none ? 0 : time.truncatingRemainder(dividingBy: period) / period
                posed(images[frame], anim.pose(phase))
            }
        }
    }

    private func posed(_ image: UIImage, _ pose: StickerPose) -> some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            ZStack {
                Image(uiImage: image).resizable().scaledToFit()
                if pose.shine >= 0 {
                    // Блик по стикеру — только поверх нарисованного
                    let x = -w * 0.6 + w * 2.2 * pose.shine
                    LinearGradient(
                        colors: [.white.opacity(0), .white.opacity(0.7), .white.opacity(0)],
                        startPoint: UnitPoint(x: w > 0 ? x / w : 0, y: 0),
                        endPoint: UnitPoint(x: w > 0 ? (x + w * 0.4) / w : 0, y: 1)
                    )
                    .mask(Image(uiImage: image).resizable().scaledToFit())
                }
            }
            .frame(width: w, height: h)
            .scaleEffect(pose.scale)
            .rotationEffect(.degrees(pose.rotation))
            .offset(y: pose.dy * h)
        }
    }
}

/// Живой стикер из файлов папки вложений: кадры по кругу и движение.
/// Неподвижный — просто картинка.
struct LiveSticker: View {
    /// Имена файлов кадров; один — неподвижная картинка.
    let files: [String]
    var fps: Int = 0
    var animation: String = ""
    @State private var images: [UIImage] = []

    var body: some View {
        LiveFrames(images: images, fps: fps, animation: animation)
            .task(id: files) {
                let names = files
                let dir = MediaFiles.directory
                images = await Task.detached(priority: .userInitiated) {
                    names.compactMap { UIImage(contentsOfFile: dir.appendingPathComponent($0).path) }
                }.value
            }
    }
}

extension LiveSticker {
    init(attachment: Attachment) {
        self.init(files: attachment.frames.isEmpty ? [attachment.fileName] : attachment.frames,
                  fps: attachment.fps, animation: attachment.animation)
    }

    init(sticker: Sticker) {
        self.init(files: sticker.files, fps: sticker.fps, animation: sticker.animation)
    }
}

// MARK: - Увеличение

/// Увеличение двумя пальцами прямо в ленте: картинка растёт в рамке
/// своей карточки, а когда пальцы отпускают — плавно возвращается. Одним
/// пальцем лента листается как обычно. highPriority — для листалки
/// нескольких фото: два пальца увеличивают, а не листают.
private struct PinchZoom: ViewModifier {
    var highPriority = false
    @State private var scale: CGFloat = 1
    @State private var anchor: UnitPoint = .center
    @State private var active = false

    @ViewBuilder
    func body(content: Content) -> some View {
        let zoomed = content
            .scaleEffect(scale, anchor: anchor)
            .zIndex(active ? 10 : 0)
        if highPriority {
            zoomed.highPriorityGesture(magnify)
        } else {
            zoomed.simultaneousGesture(magnify)
        }
    }

    private var magnify: some Gesture {
        MagnifyGesture()
            .onChanged { value in
                if !active {
                    active = true
                    anchor = value.startAnchor
                }
                scale = min(4, max(1, value.magnification))
            }
            .onEnded { _ in
                withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) { scale = 1 }
                Task { @MainActor in
                    try? await Task.sleep(nanoseconds: 350_000_000)
                    if scale == 1 { active = false }
                }
            }
    }
}

extension View {
    /// Увеличить двумя пальцами, отпустил — вернулось.
    func pinchZoom(highPriority: Bool = false) -> some View { modifier(PinchZoom(highPriority: highPriority)) }
}

/// Картинка на весь экран: двумя пальцами — увеличить и подвинуть,
/// двойное касание — увеличить в точке / вернуть.
struct ZoomableBox<Content: View>: View {
    @ViewBuilder let content: () -> Content
    @State private var scale: CGFloat = 1
    @State private var baseScale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var baseOffset: CGSize = .zero

    var body: some View {
        GeometryReader { geo in
            content()
                .frame(width: geo.size.width, height: geo.size.height)
                .scaleEffect(scale)
                .offset(offset)
                .contentShape(Rectangle())
                .gesture(
                    SimultaneousGesture(
                        MagnifyGesture()
                            .onChanged { value in
                                scale = min(5, max(1, baseScale * value.magnification))
                                if scale == 1 { offset = .zero }
                            }
                            .onEnded { _ in
                                baseScale = scale
                                if scale == 1 {
                                    offset = .zero
                                    baseOffset = .zero
                                }
                            },
                        DragGesture()
                            .onChanged { value in
                                guard scale > 1 else { return }
                                offset = CGSize(width: baseOffset.width + value.translation.width,
                                                height: baseOffset.height + value.translation.height)
                            }
                            .onEnded { _ in baseOffset = offset }
                    )
                )
                .onTapGesture(count: 2) { tap in
                    withAnimation(.easeOut(duration: 0.25)) {
                        if scale > 1 {
                            scale = 1
                            offset = .zero
                        } else {
                            // Точка под пальцем остаётся под ним
                            scale = 2.5
                            offset = CGSize(width: (geo.size.width / 2 - tap.x) * 1.5, height: (geo.size.height / 2 - tap.y) * 1.5)
                        }
                    }
                    baseScale = scale
                    baseOffset = offset
                }
        }
    }
}

// MARK: - Подсказка внизу

/// Короткая подсказка внизу экрана — сама уходит через пару секунд.
private struct ToastModifier: ViewModifier {
    @Binding var text: String?

    func body(content: Content) -> some View {
        content.overlay(alignment: .bottom) {
            if let text {
                Text(text)
                    .textStyle(.bodySmall)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Capsule().fill(Palette.night.opacity(0.95)))
                    .glass(Capsule(), strong: true)
                    .padding(.horizontal, 24)
                    .padding(.bottom, 90)
                    .transition(.opacity)
                    .task(id: text) {
                        try? await Task.sleep(nanoseconds: 2_800_000_000)
                        withAnimation { self.text = nil }
                    }
            }
        }
        .animation(.easeOut(duration: 0.2), value: text)
    }
}

extension View {
    func toast(_ text: Binding<String?>) -> some View { modifier(ToastModifier(text: text)) }
}
