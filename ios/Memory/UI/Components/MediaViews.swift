import AVKit
import PhotosUI
import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// Уменьшенная картинка вложения; читается не в главном потоке.
struct MediaThumbnail: View {
    let attachment: Attachment
    var maxPixel: CGFloat = 900
    var contentMode: ContentMode = .fill
    /// Показывать подложку со значком, пока картинка читается.
    var placeholder: Bool = true

    @State private var image: UIImage?

    var body: some View {
        ZStack {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: contentMode)
            } else if placeholder {
                Palette.plumRGB.opacity(0.6)
                Glyph(kind: attachment.kind == .video ? .video : .image, color: Palette.mist)
            }
        }
        .task(id: attachment.id) {
            let item = attachment
            let side = maxPixel
            image = await Task.detached(priority: .userInitiated) {
                MediaFiles.thumbnail(item, maxPixel: side)
            }.value
        }
    }
}

/// Плитка фото или видео: картинка с обрезкой по рамке, у видео — значок «пуск».
struct MediaTile: View {
    let attachment: Attachment
    var maxPixel: CGFloat = 900
    var onTap: (() -> Void)? = nil

    var body: some View {
        let tile = ZStack {
            if attachment.isGif {
                // Гифка играет сама, с меткой «GIF»
                Color.clear.overlay { GifImage(attachment: attachment, crop: true, maxPixel: min(maxPixel, 640)) }.clipped()
                    .background(Palette.plumRGB.opacity(0.6))
                    .overlay(alignment: .bottomLeading) { GifBadge().padding(8) }
            } else {
                Color.clear.overlay { MediaThumbnail(attachment: attachment, maxPixel: maxPixel) }.clipped()
            }
            if attachment.kind == .video {
                Glyph(kind: .play, color: Palette.ink, size: 22)
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(Palette.voidRGB.opacity(0.55)))
            }
        }
        .contentShape(Rectangle())
        if let onTap {
            Button(action: onTap) { tile }
                .buttonStyle(.plain)
                .accessibilityLabel(kindLabel)
                .accessibilityHint(t("Открыть на весь экран"))
                .accessibilityAddTraits(.isImage)
        } else {
            tile
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(kindLabel)
                .accessibilityAddTraits(.isImage)
        }
    }

    /// «Фото», «Видео» или «Гифка» — для VoiceOver.
    private var kindLabel: String {
        if attachment.isGif { return t("Гифка") }
        return attachment.kind == .video ? t("Видео") : t("Фото")
    }
}

/// Фото и видео записи. Одно — во всю ширину; несколько — листаются вбок,
/// с номером «2 / 5». Нажатие открывает на весь экран, двумя пальцами —
/// увеличить прямо в ленте.
struct PostMediaView: View {
    let media: [Attachment]
    var height: CGFloat = 260
    let onOpen: (Attachment) -> Void
    @State private var page = 0

    var body: some View {
        if media.count == 1 {
            // Увеличение — внутри рамки карточки: рамка обрезает
            MediaTile(attachment: media[0]) { onOpen(media[0]) }
                .frame(height: height)
                .frame(maxWidth: .infinity)
                .pinchZoom()
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        } else if media.count > 1 {
            TabView(selection: $page) {
                ForEach(Array(media.enumerated()), id: \.element.id) { index, item in
                    MediaTile(attachment: item) { onOpen(item) }
                        .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            // Двумя пальцами — увеличение, а не перелистывание: жест важнее листалки
            .pinchZoom(highPriority: true)
            .frame(height: height)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(alignment: .topTrailing) {
                Text("\(page + 1) / \(media.count)")
                    .textStyle(.label)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(Capsule().fill(Palette.voidRGB.opacity(0.6)))
                    .padding(10)
            }
        }
    }
}

/// Документ: значок, имя и размер.
struct DocumentRow: View {
    let attachment: Attachment
    var onTap: (() -> Void)? = nil

    var body: some View {
        let row = HStack(spacing: 10) {
            Glyph(kind: .document, color: Palette.lilac, size: 28)
            VStack(alignment: .leading, spacing: 1) {
                Text(attachment.name.isEmpty ? t("Документ") : attachment.name)
                    .textStyle(.bodySmall)
                    .lineLimit(1)
                    .truncationMode(.middle)
                if attachment.size > 0 {
                    Text(sizeTitle(attachment.size)).textStyle(.label, Palette.mist)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Palette.voidRGB.opacity(0.35)))
        .contentShape(Rectangle())
        if let onTap {
            Button(action: onTap) { row }.buttonStyle(.plain)
        } else {
            row
        }
    }
}

/// Фото или видео на весь экран.
struct MediaViewer: View {
    let attachment: Attachment
    let onClose: () -> Void
    /// Нажали на имя отметки на фото — страница человека; nil — имена не нажимаются.
    var onPerson: ((Int) -> Void)? = nil
    @State private var player: AVPlayer?

    var body: some View {
        ZStack(alignment: .topLeading) {
            Color.black.ignoresSafeArea()
            switch attachment.kind {
            case .video:
                if let player {
                    VideoPlayer(player: player).ignoresSafeArea()
                }
            default:
                // Двумя пальцами — увеличить и подвинуть, двойное касание — в точке
                ZoomableBox {
                    if attachment.isGif {
                        GifImage(attachment: attachment, maxPixel: 1200)
                    } else {
                        MediaThumbnail(attachment: attachment, maxPixel: 2400, contentMode: .fit)
                    }
                }
                .ignoresSafeArea()
            }
            // Кто отмечен на фото — имена поверх снимка и значок «Отмечено: N»
            if attachment.kind == .photo {
                PhotoTagsLayer(attachment: attachment, onPerson: onPerson)
            }
            RoundGlyphButton(icon: .close, strong: true, action: onClose)
                .padding(12)
        }
        .onAppear {
            if attachment.kind == .video {
                let p = AVPlayer(url: MediaFiles.url(attachment))
                player = p
                p.play()
            }
        }
        .onDisappear { player?.pause() }
    }
}

/// Видео из галереи: файл копируется во временную папку, пока галерея его отдаёт.
struct PickedMovie: Transferable {
    let url: URL

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(contentType: .movie) { movie in
            SentTransferredFile(movie.url)
        } importing: { received in
            let ext = received.file.pathExtension.isEmpty ? "mov" : received.file.pathExtension
            let copy = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + "." + ext)
            try FileManager.default.copyItem(at: received.file, to: copy)
            return PickedMovie(url: copy)
        }
    }
}

/// Камера: снять фото или видео. Результат копируется в папку вложений.
struct CameraPicker: UIViewControllerRepresentable {
    /// Только фото — для аватара.
    var photoOnly = false
    let onFinish: (Attachment?) -> Void

    static var isAvailable: Bool { UIImagePickerController.isSourceTypeAvailable(.camera) }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let controller = UIImagePickerController()
        controller.sourceType = .camera
        controller.mediaTypes = photoOnly ? [UTType.image.identifier] : [UTType.image.identifier, UTType.movie.identifier]
        controller.videoQuality = .typeHigh
        controller.delegate = context.coordinator
        return controller
    }

    func updateUIViewController(_ controller: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onFinish: onFinish) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let onFinish: (Attachment?) -> Void

        init(onFinish: @escaping (Attachment?) -> Void) {
            self.onFinish = onFinish
        }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let url = info[.mediaURL] as? URL {
                onFinish(MediaFiles.importFile(at: url, kind: .video))
            } else if let image = info[.originalImage] as? UIImage, let data = image.jpegData(compressionQuality: 0.9) {
                onFinish(MediaFiles.save(data, kind: .photo, ext: "jpg"))
            } else {
                onFinish(nil)
            }
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            onFinish(nil)
        }
    }
}

/// Кнопка-скрепка с меню: галерея (фото и видео, до 10), камера и — в
/// чатах — документы из «Файлов». Выбранное копируется в папку вложений
/// и приходит в onPicked.
struct AttachButton: View {
    var documents: Bool = false
    var size: CGFloat = 44
    /// «Музыка» в меню — трек из «Моей музыки» или файл; nil — пункта нет.
    var onMusic: (() -> Void)? = nil
    /// «Стикер» в меню — свои стикеры из «Студии»; nil — пункта нет.
    var onSticker: (() -> Void)? = nil
    /// «GIF» — встроенные, свои и поиск GIPHY; nil — пункта нет.
    var onGif: (() -> Void)? = nil
    /// «Опрос»; nil — пункта нет.
    var onPoll: (() -> Void)? = nil
    /// «Геолокация» — где я сейчас, точкой на карте; nil — пункта нет.
    var onLocation: (() -> Void)? = nil
    let onPicked: ([Attachment]) -> Void

    @State private var showGallery = false
    @State private var galleryItems: [PhotosPickerItem] = []
    @State private var showCamera = false
    @State private var showFiles = false
    @State private var loading = false
    @State private var failed = false
    @State private var showMenu = false

    private var actions: [GlassAction] {
        var items = [GlassAction(icon: .image, title: t("Фото или видео из галереи")) { showGallery = true }]
        if CameraPicker.isAvailable {
            items.append(GlassAction(icon: .camera, title: t("Снять фото или видео")) { showCamera = true })
        }
        if documents {
            items.append(GlassAction(icon: .document, title: t("Документ")) { showFiles = true })
        }
        if let onMusic {
            items.append(GlassAction(icon: .music, title: t("Музыка"), action: onMusic))
        }
        if let onSticker {
            items.append(GlassAction(icon: .sticker, title: t("Стикер"), action: onSticker))
        }
        if let onGif {
            items.append(GlassAction(icon: .gif, title: t("GIF"), action: onGif))
        }
        if let onPoll {
            items.append(GlassAction(icon: .poll, title: t("Опрос"), action: onPoll))
        }
        if let onLocation {
            items.append(GlassAction(icon: .globe, title: t("Геолокация"), action: onLocation))
        }
        return items
    }

    var body: some View {
        Button { showMenu = true } label: {
            ZStack {
                if loading {
                    ProgressView().tint(Palette.lilac)
                } else {
                    Glyph(kind: .attach, color: Palette.lilac, size: 22)
                }
            }
            .frame(width: size, height: size)
            .glass(Circle())
        }
        .buttonStyle(.plain)
        .disabled(loading)
        .accessibilityLabel(LineIcon.attach.label)
        .glassActions($showMenu, actions: actions)
        .photosPicker(
            isPresented: $showGallery,
            selection: $galleryItems,
            maxSelectionCount: AppModel.maxMedia,
            matching: .any(of: [.images, .videos])
        )
        .onChange(of: galleryItems) { _, items in
            guard !items.isEmpty else { return }
            galleryItems = []
            loadGallery(items)
        }
        .fullScreenCover(isPresented: $showCamera) {
            CameraPicker { attachment in
                showCamera = false
                if let attachment { onPicked([attachment]) }
            }
            .ignoresSafeArea()
        }
        .fileImporter(isPresented: $showFiles, allowedContentTypes: [.item], allowsMultipleSelection: true) { result in
            guard case .success(let urls) = result else { return }
            let picked = urls.prefix(AppModel.maxMedia).compactMap { MediaFiles.importFile(at: $0, kind: .document) }
            if picked.count < urls.count { failed = true }
            if !picked.isEmpty { onPicked(picked) }
        }
        .alert(t("Не все файлы удалось добавить"), isPresented: $failed) {
            Button(t("Понятно"), role: .cancel) {}
        } message: {
            Text(t("Можно приложить файлы до 200 МБ."))
        }
    }

    private func loadGallery(_ items: [PhotosPickerItem]) {
        loading = true
        Task {
            var picked: [Attachment] = []
            for item in items {
                let isVideo = item.supportedContentTypes.contains { $0.conforms(to: .movie) }
                if isVideo {
                    if let movie = try? await item.loadTransferable(type: PickedMovie.self) {
                        if let attachment = MediaFiles.importFile(at: movie.url, kind: .video) {
                            picked.append(attachment)
                        }
                        try? FileManager.default.removeItem(at: movie.url)
                    }
                } else if let data = try? await item.loadTransferable(type: Data.self) {
                    let ext = item.supportedContentTypes.first?.preferredFilenameExtension ?? "jpg"
                    if let attachment = MediaFiles.save(data, kind: .photo, ext: ext) {
                        picked.append(attachment)
                    }
                }
            }
            loading = false
            if picked.count < items.count { failed = true }
            if !picked.isEmpty { onPicked(picked) }
        }
    }
}

/// Вложения, которые ещё не отправлены: маленькие плитки с крестиком.
struct PendingAttachments: View {
    let items: [Attachment]
    let onRemove: (Attachment) -> Void
    /// Нажатие на фото — отметить людей; nil — плитки не нажимаются.
    var onTag: ((Attachment) -> Void)? = nil

    var body: some View {
        if !items.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(items) { item in
                        ZStack(alignment: .topTrailing) {
                            if item.kind == .document {
                                DocumentRow(attachment: item).frame(width: 180, height: 64)
                            } else {
                                let taggable = onTag != nil && item.kind == .photo && !item.sticker && !item.isGif
                                MediaTile(attachment: item, maxPixel: 256, onTap: taggable ? { onTag?(item) } : nil)
                                    .frame(width: 64, height: 64)
                                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                                    .overlay(alignment: .bottomLeading) {
                                        TagCountBadge(count: item.tags.count).padding(3)
                                    }
                                    .accessibilityHint(taggable ? LineIcon.personTag.label : "")
                            }
                            Button { onRemove(item) } label: {
                                Glyph(kind: .close, color: Palette.ink, size: 14)
                                    .frame(width: 22, height: 22)
                                    .background(Circle().fill(Palette.voidRGB.opacity(0.75)))
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(LineIcon.trash.label)
                            .padding(2)
                        }
                    }
                }
                .padding(.horizontal, 12)
            }
        }
    }
}
