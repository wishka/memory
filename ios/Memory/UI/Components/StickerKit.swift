import PhotosUI
import SwiftUI
import UniformTypeIdentifiers

/// Свой стикер как вложение: копия PNG из «Студии» (у покадрового — все
/// кадры). Копия — чтобы сообщение или запись не потеряли картинку, если
/// стикер потом удалят. Движение и частота кадров едут вместе с ним.
func stickerAttachment(_ sticker: Sticker) -> Attachment? {
    guard let data = try? Data(contentsOf: sticker.url),
          var attachment = MediaFiles.save(data, kind: .photo, ext: "png", name: "sticker.png") else { return nil }
    attachment.stickerFlag = true
    attachment.animation = sticker.animation
    if sticker.frames.count > 1 {
        var names = [attachment.fileName]
        for name in sticker.frames.dropFirst() {
            if let frame = try? Data(contentsOf: MediaFiles.directory.appendingPathComponent(name)),
               let copy = MediaFiles.save(frame, kind: .photo, ext: "png", name: "sticker.png") {
                names.append(copy.fileName)
            }
        }
        if names.count > 1 {
            attachment.frames = names
            attachment.fps = sticker.fps
        }
    }
    return attachment
}

/// Удалить вложение-стикер со всеми кадрами.
func deleteAttachmentFiles(_ attachment: Attachment) {
    MediaFiles.delete(attachment)
    for name in attachment.frames.dropFirst() {
        try? FileManager.default.removeItem(at: MediaFiles.directory.appendingPathComponent(name))
    }
}

/// Стикер-вложение без рамки и фона: прозрачная картинка как есть;
/// живой — двигается, гифка — играет.
struct StickerView: View {
    let attachment: Attachment
    var size: CGFloat = 140
    @State private var image: UIImage?

    var body: some View {
        content
            .frame(width: size, height: size)
            .accessibilityLabel(t("Стикер"))
    }

    @ViewBuilder
    private var content: some View {
        if attachment.isGif {
            GifImage(attachment: attachment)
        } else if attachment.animated {
            LiveSticker(attachment: attachment)
        } else {
            ZStack {
                if let image { Image(uiImage: image).resizable().scaledToFit() }
            }
            .task(id: attachment.fileName) {
                let url = MediaFiles.url(attachment)
                image = await Task.detached { UIImage(contentsOfFile: url.path) }.value
            }
        }
    }
}

/// Стикеры записи — рядком, крупно.
struct StickerRow: View {
    let stickers: [Attachment]
    var size: CGFloat = 120

    var body: some View {
        if !stickers.isEmpty {
            HStack(spacing: 8) {
                ForEach(stickers) { StickerView(attachment: $0, size: size) }
            }
        }
    }
}

/// Вкладки выбора: все стикеры, коллекция или гифки.
private enum PickerTab: Equatable {
    case all
    case pack(String)
    case gifs
}

/// Выбор стикера или гифки. Стикеры — из «Студии», по коллекциям, как
/// наборы на iPhone; гифки — встроенные Memory, недавние, своя из
/// галереи и поиск GIPHY. onPick получает уже скопированное вложение.
struct StickerPicker: View {
    let stickers: [Sticker]
    let onPick: (Attachment) -> Void
    var onOpenStudio: (() -> Void)?
    var packs: [StickerPack]
    var recentGifs: [Attachment]
    /// Показывать вкладку «GIF».
    var gifs: Bool
    @Environment(\.dismiss) private var dismiss
    @State private var tab: PickerTab

    init(
        stickers: [Sticker],
        onPick: @escaping (Attachment) -> Void,
        onOpenStudio: (() -> Void)? = nil,
        packs: [StickerPack] = [],
        recentGifs: [Attachment] = [],
        gifs: Bool = false,
        startOnGifs: Bool = false
    ) {
        self.stickers = stickers
        self.onPick = onPick
        self.onOpenStudio = onOpenStudio
        self.packs = packs
        self.recentGifs = recentGifs
        self.gifs = gifs
        _tab = State(initialValue: startOnGifs && gifs ? .gifs : .all)
    }

    private var shown: [Sticker] {
        switch tab {
        case .all: return stickers
        case .pack(let id): return stickers.filter { $0.packId == id }
        case .gifs: return []
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(tab == .gifs ? t("Гифки") : t("Мои стикеры")).textStyle(.headline)
            if !packs.isEmpty || gifs {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        Chip(text: t("Все"), active: tab == .all) { tab = .all }
                        ForEach(packs) { p in
                            Chip(text: p.name, active: tab == .pack(p.id)) { tab = .pack(p.id) }
                        }
                        if gifs {
                            Chip(text: "GIF", active: tab == .gifs) { tab = .gifs }
                        }
                    }
                }
            }
            if tab == .gifs {
                GifPane(recentAll: recentGifs) { gif in
                    onPick(gif)
                    dismiss()
                }
            } else if shown.isEmpty {
                Text(stickers.isEmpty
                     ? t("Стикеров пока нет. Сделайте значок, смайл или стикер из фото в «Студии» профиля.")
                     : t("В этой коллекции пока пусто."))
                    .textStyle(.bodySmall, Palette.mist)
                    .fixedSize(horizontal: false, vertical: true)
                HStack(spacing: 10) {
                    GhostButton(title: t("Закрыть")) { dismiss() }
                    if let onOpenStudio {
                        GlowButton(title: t("Открыть Студию")) {
                            dismiss()
                            onOpenStudio()
                        }
                    }
                }
                Spacer()
            } else {
                ScrollView {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 3), spacing: 8) {
                        ForEach(shown) { sticker in
                            Button {
                                // Копия кадров — не в главном потоке
                                Task {
                                    let attachment = await Task.detached(priority: .userInitiated) { stickerAttachment(sticker) }.value
                                    if let attachment { onPick(attachment) }
                                    dismiss()
                                }
                            } label: {
                                StickerImage(sticker: sticker)
                                    .padding(10)
                                    .frame(maxWidth: .infinity)
                                    .aspectRatio(1, contentMode: .fit)
                                    .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Palette.plum.opacity(0.35)))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.night.ignoresSafeArea())
        .presentationDetents([.medium, .large])
    }
}

private enum GifSource: String, CaseIterable {
    case memory = "Memory"
    case recent = "Недавние"
    case giphy = "GIPHY"

    var title: String { t(rawValue) }
}

/// Гифки: встроенные Memory, недавние, своя из галереи и поиск GIPHY.
private struct GifPane: View {
    let recentAll: [Attachment]
    let onPick: (Attachment) -> Void
    @State private var source: GifSource?
    @State private var busy = false
    @State private var notice: String?
    @State private var galleryItem: PhotosPickerItem?
    @State private var showGallery = false

    /// Сообщение с гифкой могли удалить вместе с файлом — такие не показываем.
    private var recent: [Attachment] {
        recentAll.filter { FileManager.default.fileExists(atPath: MediaFiles.url($0).path) }
    }

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 3)

    var body: some View {
        let current: GifSource = source ?? (recent.isEmpty ? .memory : .recent)
        VStack(alignment: .leading, spacing: 10) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(GifSource.allCases, id: \.self) { s in
                        Chip(text: s.title, active: s == current) { source = s }
                    }
                    Chip(text: t("Из галереи"), active: false) { showGallery = true }
                }
            }
            switch current {
            case .memory:
                ScrollView {
                    LazyVGrid(columns: columns, spacing: 8) {
                        ForEach(Gifs.builtIn) { gif in
                            GifCell(url: Gifs.bundleURL(gif), label: gif.title) {
                                copy { Gifs.attachment(gif) }
                            }
                        }
                    }
                }
            case .recent:
                if recent.isEmpty {
                    Text(t("Здесь будут гифки, которые вы отправляли.")).textStyle(.bodySmall, Palette.mist)
                    Spacer()
                } else {
                    ScrollView {
                        LazyVGrid(columns: columns, spacing: 8) {
                            ForEach(recent) { gif in
                                GifCell(url: MediaFiles.url(gif), label: gif.name) {
                                    // Копия: недавняя гифка может уйти из списка, а сообщение — останется
                                    copy { Gifs.copy(gif) }
                                }
                            }
                        }
                    }
                }
            case .giphy:
                GiphyPane(columns: columns) { gif in
                    guard !busy else { return }
                    busy = true
                    Task {
                        let item = await Giphy.download(gif)
                        busy = false
                        if let item { onPick(item) } else { notice = t("Не удалось скачать гифку") }
                    }
                }
            }
            if busy {
                Text(t("Загружаю…")).textStyle(.bodySmall, Palette.mist)
            }
            if let notice {
                Text(notice).textStyle(.bodySmall, Palette.pearlPink)
            }
        }
        .photosPicker(isPresented: $showGallery, selection: $galleryItem, matching: .images)
        .onChange(of: galleryItem) { _, item in
            guard let item else { return }
            galleryItem = nil
            busy = true
            Task {
                let data = try? await item.loadTransferable(type: Data.self)
                // Только гифки: обычное фото здесь не к месту
                let saved: Attachment?
                if let data, Gifs.isGif(data) {
                    saved = await Task.detached(priority: .userInitiated) { MediaFiles.save(data, kind: .photo, ext: "gif") }.value
                } else {
                    saved = nil
                }
                busy = false
                if let saved {
                    onPick(saved)
                } else {
                    notice = t("Не удалось добавить")
                }
            }
        }
    }

    /// Копия гифки во вложения — не в главном потоке.
    private func copy(_ work: @escaping () -> Attachment?) {
        guard !busy else { return }
        busy = true
        Task {
            let item = await Task.detached(priority: .userInitiated) { work() }.value
            busy = false
            if let item { onPick(item) } else { notice = t("Не удалось добавить") }
        }
    }
}

/// Клетка с гифкой; пока не прочиталась — подпись.
private struct GifCell: View {
    let url: URL?
    let label: String
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            Color.clear
                .aspectRatio(1, contentMode: .fit)
                .overlay {
                    ZStack {
                        Palette.plum.opacity(0.35)
                        if url == nil {
                            Text(label).textStyle(.label, Palette.mist)
                        } else {
                            GifImage(url: url, crop: true, maxPixel: 240)
                        }
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                .contentShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

/// Поиск GIPHY: пусто — популярное. Подпись «Powered by GIPHY» — условие GIPHY.
private struct GiphyPane: View {
    let columns: [GridItem]
    let onPick: (GiphyGif) -> Void
    @State private var query = ""
    @State private var result: GiphyResult?

    var body: some View {
        if !Giphy.available {
            Text(t("Поиск GIPHY появится, когда в сборку добавят ключ GIPHY_API_KEY. Пока — гифки Memory и свои из галереи."))
                .textStyle(.bodySmall, Palette.mist)
                .fixedSize(horizontal: false, vertical: true)
            Spacer()
        } else {
            VStack(alignment: .leading, spacing: 8) {
                GlassField(placeholder: t("Найти гифку"), text: $query, limit: 50, singleLine: true)
                    .glass(radius: 16)
                switch result {
                case nil:
                    Text(t("Загружаю…")).textStyle(.bodySmall, Palette.mist)
                case .some(.failure):
                    Text(t("GIPHY не отвечает. Проверьте интернет.")).textStyle(.bodySmall, Palette.mist)
                case .some(.gifs(let gifs)):
                    if gifs.isEmpty {
                        Text(t("Ничего не нашлось")).textStyle(.bodySmall, Palette.mist)
                    } else {
                        ScrollView {
                            LazyVGrid(columns: columns, spacing: 8) {
                                ForEach(gifs) { gif in
                                    GiphyCell(gif: gif) { onPick(gif) }
                                }
                            }
                        }
                    }
                }
                Spacer(minLength: 0)
                Text("Powered by GIPHY · " + t("поиск уходит в GIPHY (США)")).textStyle(.label, Palette.mist)
            }
            .task(id: query) {
                // Пока человек печатает — не дёргаем поиск на каждую букву
                if !query.isEmpty { try? await Task.sleep(nanoseconds: 450_000_000) }
                if Task.isCancelled { return }
                let found = await Giphy.search(query)
                if !Task.isCancelled { result = found }
            }
        }
    }
}

/// Превью из поиска: сначала в кэш, потом гифкой.
private struct GiphyCell: View {
    let gif: GiphyGif
    let onTap: () -> Void
    @State private var file: URL?

    var body: some View {
        GifCell(url: file, label: "GIF", onTap: onTap)
            .task(id: gif.previewUrl) {
                file = await Giphy.cachePreview(gif.previewUrl)
            }
    }
}
