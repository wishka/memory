import PhotosUI
import SwiftUI

extension StickerKind: Identifiable {
    var id: String { rawValue }
}

/// «Студия» в профиле: свои значки, смайлы и стикеры. Четыре способа —
/// из фото (обрезка формой, обводка, подпись), рисунок пальцем,
/// конструктор смайлов и значок с текстом на кристальной подложке.
/// Готовое — прозрачная PNG-картинка: её видно в витрине профиля, и её
/// можно отправить через «Поделиться».
struct StickerStudioScreen: View {
    let model: AppModel
    @State private var editor: StickerKind?
    @State private var menu: Sticker?
    @State private var sharing: Sticker?

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 10), count: 4)

    var body: some View {
        let state = model.state
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 14) {
                    RoundGlyphButton(icon: .back) { model.closeProfileSection() }
                    ScreenTitle(title: t("Студия"), subtitle: plural(state.stickers.count, "стикер", "стикера", "стикеров"))
                    Spacer()
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 4)

                LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                    createTile(.image, t("Из фото"), t("Форма, обводка, подпись")) { editor = .photo }
                    createTile(.brush, t("Рисунок"), t("Кисти, цвета, ластик")) { editor = .drawing }
                    createTile(.smile, t("Смайл"), t("Лицо, глаза, рот, детали")) { editor = .smiley }
                    createTile(.award, t("Значок"), t("Текст или эмодзи на кристалле")) { editor = .badge }
                }
                .padding(.horizontal, 16)

                Text(t("Мои стикеры").uppercased()).textStyle(.label, Palette.mist).padding(.horizontal, 24)
                if state.stickers.isEmpty {
                    Text(t("Здесь появятся ваши значки, смайлы и стикеры. Выберите способ выше."))
                        .textStyle(.bodySmall, Palette.mist)
                        .padding(.horizontal, 24)
                }
                LazyVGrid(columns: columns, spacing: 10) {
                    ForEach(state.stickers) { sticker in
                        Button { menu = sticker } label: {
                            StickerImage(sticker: sticker)
                                .padding(8)
                                .aspectRatio(1, contentMode: .fit)
                                .glass(RoundedRectangle(cornerRadius: 18, style: .continuous))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 16)
            }
            .padding(.top, 12)
            .padding(.bottom, tabBarSpace)
        }
        .fullScreenCover(item: $editor) { kind in
            StickerEditor(kind: kind, onClose: { editor = nil }) { sticker in
                model.addSticker(sticker)
                editor = nil
            }
        }
        .glassActions(item: $menu, title: { $0.kind.title }) { sticker in
            [
                GlassAction(icon: .share, title: t("Поделиться")) { sharing = sticker },
                GlassAction(icon: .trash, title: t("Удалить"), destructive: true) {
                    try? FileManager.default.removeItem(at: sticker.url)
                    model.deleteSticker(sticker.id)
                },
            ]
        }
        .sheet(item: $sharing) { sticker in
            ShareSheet(items: [sticker.url])
        }
    }

    private func createTile(_ icon: LineIcon, _ title: String, _ hint: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 6) {
                Glyph(kind: icon, color: Palette.ink, size: 22)
                    .frame(width: 40, height: 40)
                    .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(LinearGradient(colors: [Palette.orchid, Palette.ocean], startPoint: .topLeading, endPoint: .bottomTrailing)))
                Text(title).textStyle(.title)
                Text(hint).textStyle(.bodySmall, Palette.mist).lineLimit(2).multilineTextAlignment(.leading)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .glass(radius: 22)
        }
        .buttonStyle(.plain)
    }
}

/// Системное меню «Поделиться».
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

/// Картинка стикера из файла.
struct StickerImage: View {
    let sticker: Sticker
    @State private var image: UIImage?

    var body: some View {
        ZStack {
            if let image { Image(uiImage: image).resizable().scaledToFit() }
        }
        .task(id: sticker.fileName) {
            let url = sticker.url
            image = await Task.detached { UIImage(contentsOfFile: url.path) }.value
        }
    }
}

// MARK: - Редактор

/// Редактор стикера во весь экран: предпросмотр сверху, инструменты снизу.
private struct StickerEditor: View {
    let kind: StickerKind
    let onClose: () -> Void
    let onSaved: (Sticker) -> Void

    @State private var photo: UIImage?
    @State private var photoItem: PhotosPickerItem?
    @State private var showPicker = false
    @State private var photoParts = PhotoParts()
    @State private var strokes: [DrawStroke] = []
    @State private var current: [CGPoint] = []
    @State private var brushColor: UInt32 = stickerColors[4]
    @State private var brushWidth: CGFloat = 0.03
    @State private var eraser = false
    @State private var smiley = SmileyParts()
    @State private var badge = BadgeParts()
    @State private var dragStart: CGPoint?
    @State private var failed = false

    private func render() -> UIImage? {
        switch kind {
        case .photo: return photo.map { StickerArt.photo($0, photoParts) }
        case .drawing:
            var all = strokes
            if !current.isEmpty { all.append(DrawStroke(points: current, color: brushColor, width: brushWidth, eraser: eraser)) }
            return StickerArt.drawing(all)
        case .smiley: return StickerArt.smiley(smiley)
        case .badge: return StickerArt.badge(badge)
        }
    }

    private var empty: Bool {
        switch kind {
        case .photo: return photo == nil
        case .drawing: return !strokes.contains { !$0.eraser }
        case .badge: return badge.text.trimmingCharacters(in: .whitespaces).isEmpty
        case .smiley: return false
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                RoundGlyphButton(icon: .close, action: onClose)
                Text(kind.title).textStyle(.headline)
                Spacer()
                GlowButton(title: t("Сохранить"), enabled: !empty) {
                    if let image = render(), let sticker = StickerArt.save(image, kind: kind) {
                        onSaved(sticker)
                    } else {
                        failed = true
                    }
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)

            preview
                .padding(.horizontal, 32)
                .padding(.vertical, 8)

            ScrollView {
                VStack(alignment: .leading, spacing: 12) { controls }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .background(Palette.void.ignoresSafeArea())
        .onAppear { if kind == .photo { showPicker = true } }
        .photosPicker(isPresented: $showPicker, selection: $photoItem, matching: .images)
        .onChange(of: showPicker) { _, open in
            if !open && photo == nil && photoItem == nil && kind == .photo {
                // Отказались выбирать фото — закрыть редактор
                Task { @MainActor in
                    try? await Task.sleep(nanoseconds: 300_000_000)
                    if photo == nil && photoItem == nil { onClose() }
                }
            }
        }
        .onChange(of: photoItem) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self), let image = UIImage(data: data) {
                    photo = downscaled(image, maxSide: 1024)
                } else {
                    failed = true
                }
                photoItem = nil
            }
        }
        .glassActions($failed, title: t("Не удалось сохранить"), actions: [GlassAction(icon: .check, title: t("Понятно")) {}])
    }

    private func downscaled(_ image: UIImage, maxSide: CGFloat) -> UIImage {
        let side = max(image.size.width, image.size.height)
        guard side > maxSide else { return image }
        let k = maxSide / side
        let size = CGSize(width: image.size.width * k, height: image.size.height * k)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: size, format: format).image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
    }

    /// Предпросмотр на «шахматке» — так видно прозрачные места.
    private var preview: some View {
        GeometryReader { geo in
            let side = geo.size.width
            ZStack {
                Canvas { ctx, size in
                    let cell = size.width / 16
                    ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Palette.night))
                    for i in 0..<16 {
                        for j in 0..<16 where (i + j) % 2 == 0 {
                            ctx.fill(Path(CGRect(x: CGFloat(i) * cell, y: CGFloat(j) * cell, width: cell, height: cell)), with: .color(Palette.plum.opacity(0.55)))
                        }
                    }
                }
                if let image = render() {
                    Image(uiImage: image).resizable().interpolation(.high)
                }
            }
            .frame(width: side, height: side)
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).stroke(Palette.line, lineWidth: 1))
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        let pt = CGPoint(x: value.location.x / side, y: value.location.y / side)
                        switch kind {
                        case .drawing: current.append(pt)
                        case .photo:
                            let start = dragStart ?? CGPoint(x: photoParts.dx, y: photoParts.dy)
                            if dragStart == nil { dragStart = start }
                            photoParts.dx = min(1, max(-1, start.x + value.translation.width / side))
                            photoParts.dy = min(1, max(-1, start.y + value.translation.height / side))
                        default: break
                        }
                    }
                    .onEnded { _ in
                        if kind == .drawing, !current.isEmpty {
                            strokes.append(DrawStroke(points: current, color: brushColor, width: brushWidth, eraser: eraser))
                            current = []
                        }
                        dragStart = nil
                    }
            )
        }
        .aspectRatio(1, contentMode: .fit)
    }

    @ViewBuilder
    private var controls: some View {
        switch kind {
        case .photo:
            label(t("Форма"))
            choices(StickerShape.allCases, photoParts.shape, title: \.title) { photoParts.shape = $0 }
            label(t("Масштаб — сдвигайте фото пальцем"))
            Slider(value: $photoParts.zoom, in: 1...3).tint(Palette.lilac)
            label(t("Обводка"))
            choices([0, 1, 2, 3], photoParts.outline, title: { [t("Нет"), t("Тонкая"), t("Средняя"), t("Толстая")][$0] }) { photoParts.outline = $0 }
            if photoParts.outline > 0 { colorRow(stickerColors, photoParts.outlineColor) { photoParts.outlineColor = $0 } }
            label(t("Подпись"))
            GlassField(placeholder: t("Например, «Ура!»"), text: $photoParts.caption, limit: 24, singleLine: true)
                .glass(radius: 16)
            if !photoParts.caption.isEmpty { colorRow(stickerColors, photoParts.captionColor) { photoParts.captionColor = $0 } }
            GhostButton(title: t("Другое фото")) { showPicker = true }
        case .drawing:
            HStack(spacing: 8) {
                Chip(text: t("Кисть"), active: !eraser) { eraser = false }
                Chip(text: t("Ластик"), active: eraser) { eraser = true }
                Spacer()
                RoundGlyphButton(icon: .undo, color: strokes.isEmpty ? Palette.mist : Palette.lilac) { if !strokes.isEmpty { strokes.removeLast() } }
                RoundGlyphButton(icon: .trash, color: strokes.isEmpty ? Palette.mist : Palette.lilac) { strokes = [] }
            }
            label(t("Цвет"))
            colorRow(stickerColors, eraser ? 0x1 : brushColor) { brushColor = $0; eraser = false }
            label(t("Толщина"))
            Slider(value: $brushWidth, in: 0.008...0.09).tint(Palette.lilac)
            Text(t("Рисуйте пальцем в рамке. Касание — точка.")).textStyle(.bodySmall, Palette.mist)
        case .smiley:
            label(t("Лицо"))
            choices(StickerShape.allCases, smiley.face, title: \.title) { smiley.face = $0 }
            colorRow(Array(stickerColors.dropLast()), smiley.color) { smiley.color = $0 }
            label(t("Глаза"))
            choices(SmileEyes.allCases, smiley.eyes, title: \.title) { smiley.eyes = $0 }
            label(t("Рот"))
            choices(SmileMouth.allCases, smiley.mouth, title: \.title) { smiley.mouth = $0 }
            label(t("Детали"))
            choices(SmileExtra.allCases, smiley.extra, title: \.title) { smiley.extra = $0 }
        case .badge:
            label(t("Текст или эмодзи"))
            GlassField(placeholder: t("До 8 знаков: «MEM», «✦», «🔥»"), text: $badge.text, limit: 8, singleLine: true)
                .glass(radius: 16)
            label(t("Форма"))
            choices(StickerShape.allCases, badge.shape, title: \.title) { badge.shape = $0 }
            label(t("Подложка"))
            HStack(spacing: 10) {
                ForEach(badgeGradients.indices, id: \.self) { i in
                    Button { badge.gradient = i } label: {
                        Circle()
                            .fill(LinearGradient(colors: [RGB(badgeGradients[i].0).color, RGB(badgeGradients[i].1).color], startPoint: .topLeading, endPoint: .bottomTrailing))
                            .frame(width: 38, height: 38)
                            .overlay(Circle().stroke(i == badge.gradient ? Palette.ink : Palette.line, lineWidth: i == badge.gradient ? 3 : 1))
                    }
                    .buttonStyle(.plain)
                }
            }
            Toggle(isOn: $badge.glass) { Text(t("Кристальное стекло")).textStyle(.body) }
                .tint(Palette.orchid)
        }
    }

    private func label(_ text: String) -> some View {
        Text(text.uppercased()).textStyle(.label, Palette.mist)
    }

    private func colorRow(_ colors: [UInt32], _ selected: UInt32, onPick: @escaping (UInt32) -> Void) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(colors, id: \.self) { c in
                    Button { onPick(c) } label: {
                        Circle()
                            .fill(RGB(c).color)
                            .frame(width: 34, height: 34)
                            .overlay(Circle().stroke(c == selected ? Palette.ink : Palette.line, lineWidth: c == selected ? 3 : 1))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private func choices<T: Hashable>(_ items: [T], _ selected: T, title: @escaping (T) -> String, onPick: @escaping (T) -> Void) -> some View {
        FlowLayout(spacing: 8) {
            ForEach(items, id: \.self) { item in
                Chip(text: title(item), active: item == selected) { onPick(item) }
            }
        }
    }
}
