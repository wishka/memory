import PhotosUI
import SwiftUI
import UIKit

/// Аватар в профиле с меню: выбрать фото из галереи, сделать снимок или
/// убрать фото. Выбранное фото открывается в обрезке по кругу; готовый
/// квадрат 512 × 512 приходит в onPicked именем файла в папке вложений.
struct EditableAvatar: View {
    let user: User
    let onPicked: (String) -> Void
    let onRemove: () -> Void

    @State private var showGallery = false
    @State private var galleryItem: PhotosPickerItem?
    @State private var showCamera = false
    @State private var cropping: CropSource?
    /// Снимок с камеры ждёт, пока закроется камера: два полноэкранных окна
    /// подряд SwiftUI показать не даёт.
    @State private var captured: UIImage?

    var body: some View {
        Menu {
            Button { showGallery = true } label: {
                Label(t("Выбрать из галереи"), systemImage: "photo")
            }
            Button { showCamera = true } label: {
                Label(t("Сделать фото"), systemImage: "camera")
            }
            .disabled(!CameraPicker.isAvailable)
            if user.hasAvatar {
                Button(role: .destructive, action: onRemove) {
                    Label(t("Убрать фото"), systemImage: "trash")
                }
            }
        } label: {
            ZStack(alignment: .bottomTrailing) {
                Avatar(user: user, size: 104, ring: true)
                // Значок камеры: аватар можно сменить
                Glyph(kind: .camera, color: Palette.ink, size: 16)
                    .frame(width: 32, height: 32)
                    .glass(Circle(), strong: true)
            }
        }
        .photosPicker(isPresented: $showGallery, selection: $galleryItem, matching: .images)
        .onChange(of: galleryItem) { _, item in
            guard let item else { return }
            galleryItem = nil
            Task {
                if let data = try? await item.loadTransferable(type: Data.self), let image = UIImage(data: data) {
                    cropping = CropSource(image: image)
                }
            }
        }
        .fullScreenCover(isPresented: $showCamera, onDismiss: {
            if let image = captured {
                captured = nil
                cropping = CropSource(image: image)
            }
        }) {
            CameraPicker(photoOnly: true) { attachment in
                if let attachment {
                    // Снимок — во временной копии; для обрезки нужна картинка
                    captured = UIImage(contentsOfFile: MediaFiles.url(attachment).path)
                    MediaFiles.delete(attachment)
                }
                showCamera = false
            }
            .ignoresSafeArea()
        }
        .fullScreenCover(item: $cropping) { source in
            AvatarCropper(image: source.image) { fileName in
                cropping = nil
                if let fileName { onPicked(fileName) }
            }
        }
    }
}

/// Фото, которое обрезаем; Identifiable — для fullScreenCover(item:).
struct CropSource: Identifiable {
    let id = UUID()
    let image: UIImage
}

/// Обрезка аватара: фото под круглой рамкой, его можно двигать пальцем и
/// увеличивать щипком. «Готово» сохраняет квадрат под кругом.
struct AvatarCropper: View {
    let image: UIImage
    let onDone: (String?) -> Void

    @State private var zoom: CGFloat = 1
    @State private var pan: CGSize = .zero
    @GestureState private var pinch: CGFloat = 1
    @GestureState private var drag: CGSize = .zero
    @State private var saving = false

    var body: some View {
        VStack(spacing: 0) {
            Text(t("Переместите и масштабируйте").uppercased())
                .textStyle(.label, Palette.mist)
                .padding(.top, 20)
                .padding(.bottom, 12)
            GeometryReader { geo in
                let circle = max(1, min(geo.size.width, geo.size.height) - 48)
                let geometry = CropGeometry(imageSize: image.size, circle: circle)
                let liveZoom = min(5, max(1, zoom * pinch))
                let livePan = geometry.clamp(CGSize(width: pan.width + drag.width, height: pan.height + drag.height), zoom: liveZoom)
                let scale = geometry.scale(zoom: liveZoom)
                ZStack {
                    Image(uiImage: image)
                        .resizable()
                        .frame(width: image.size.width * scale, height: image.size.height * scale)
                        .offset(livePan)
                    // Затемнение вокруг круга и тонкий светлый контур
                    CircleMask(diameter: circle)
                        .fill(Color.black.opacity(0.62), style: FillStyle(eoFill: true))
                        .allowsHitTesting(false)
                    Circle()
                        .stroke(Palette.lilac, lineWidth: 1.5)
                        .frame(width: circle, height: circle)
                        .allowsHitTesting(false)
                }
                .frame(width: geo.size.width, height: geo.size.height)
                .clipped()
                .contentShape(Rectangle())
                .gesture(
                    SimultaneousGesture(
                        MagnifyGesture()
                            .updating($pinch) { value, state, _ in state = value.magnification }
                            .onEnded { value in
                                zoom = min(5, max(1, zoom * value.magnification))
                                pan = geometry.clamp(pan, zoom: zoom)
                            },
                        DragGesture()
                            .updating($drag) { value, state, _ in state = value.translation }
                            .onEnded { value in
                                pan = geometry.clamp(
                                    CGSize(width: pan.width + value.translation.width, height: pan.height + value.translation.height),
                                    zoom: zoom
                                )
                            }
                    )
                )
                .onChange(of: saving) { _, now in
                    guard now else { return }
                    let fileName = Self.crop(image, geometry: geometry, zoom: zoom, pan: pan)
                    onDone(fileName)
                }
            }
            HStack(spacing: 12) {
                GhostButton(title: t("Отмена")) { onDone(nil) }
                GlowButton(title: t("Готово"), enabled: !saving) { saving = true }
            }
            .padding(20)
        }
        .background(Palette.void.ignoresSafeArea())
    }

    /// Квадрат под кругом → 512 × 512 JPEG в папке вложений; имя файла или nil.
    private static func crop(_ image: UIImage, geometry: CropGeometry, zoom: CGFloat, pan: CGSize) -> String? {
        let square = geometry.sourceSquare(zoom: zoom, pan: pan)
        let side = CropGeometry.avatarPixels
        let k = side / square.width
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let rendered = UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format).image { _ in
            image.draw(in: CGRect(
                x: -square.minX * k, y: -square.minY * k,
                width: image.size.width * k, height: image.size.height * k
            ))
        }
        guard let data = rendered.jpegData(compressionQuality: 0.9) else { return nil }
        return MediaFiles.save(data, kind: .photo, ext: "jpg")?.fileName
    }
}

/// Прямоугольник во весь экран с круглым окном — для затемнения вокруг круга.
private struct CircleMask: Shape {
    let diameter: CGFloat

    func path(in rect: CGRect) -> Path {
        var path = Path(rect)
        path.addEllipse(in: CGRect(
            x: rect.midX - diameter / 2, y: rect.midY - diameter / 2,
            width: diameter, height: diameter
        ))
        return path
    }
}

/// Геометрия обрезки. Фото вписано так, что при zoom = 1 круг диаметром
/// circle закрыт целиком; pan — сдвиг центра фото от центра круга. Те же
/// правила, что CropGeometry на Android.
struct CropGeometry {
    let imageSize: CGSize
    let circle: CGFloat

    static let avatarPixels: CGFloat = 512

    /// Масштаб «точка фото → точка экрана».
    func scale(zoom: CGFloat) -> CGFloat {
        circle / max(1, min(imageSize.width, imageSize.height)) * zoom
    }

    /// Не даём сдвинуть фото так, чтобы в круге появилась пустота.
    func clamp(_ pan: CGSize, zoom: CGFloat) -> CGSize {
        let s = scale(zoom: zoom)
        let maxX = max(0, (imageSize.width * s - circle) / 2)
        let maxY = max(0, (imageSize.height * s - circle) / 2)
        return CGSize(width: min(maxX, max(-maxX, pan.width)), height: min(maxY, max(-maxY, pan.height)))
    }

    /// Квадрат фото под кругом — в точках фото.
    func sourceSquare(zoom: CGFloat, pan: CGSize) -> CGRect {
        let s = scale(zoom: zoom)
        let p = clamp(pan, zoom: zoom)
        let side = min(circle / s, min(imageSize.width, imageSize.height))
        let x = ((imageSize.width * s - circle) / 2 - p.width) / s
        let y = ((imageSize.height * s - circle) / 2 - p.height) / s
        return CGRect(
            x: min(max(0, x), imageSize.width - side),
            y: min(max(0, y), imageSize.height - side),
            width: side, height: side
        )
    }
}
