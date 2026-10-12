import PhotosUI
import SwiftUI

extension StickerKind: Identifiable {
    var id: String { rawValue }
}

/// «Студия» в профиле: свои значки, смайлы и стикеры. Четыре способа —
/// из фото (обрезка формой, обводка, подпись), рисунок пальцем,
/// конструктор смайлов и значок с текстом на кристальной подложке.
/// Плюс вырезка объекта с фото (как в iOS 17). Эффекты — контур, комикс,
/// объём, блеск; движение — прыжок, пульс, мерцание…; рисунок бывает
/// покадровым. Стикеры собираются в коллекции. Готовое — прозрачные
/// PNG-картинки; их видно в витрине профиля и можно отправить.
struct StickerStudioScreen: View {
    let model: AppModel
    @State private var editor: StickerKind?
    @State private var menu: Sticker?
    @State private var sharing: Sticker?
    /// Открытая коллекция; "" — все стикеры.
    @State private var packId = ""
    /// Выбор коллекции для стикера.
    @State private var moving: Sticker?
    /// Стикер, который уйдёт в новую коллекцию, когда её назовут.
    @State private var pendingMove: Sticker?
    /// Окно имени: новая коллекция или переименование.
    @State private var naming: PackNaming?

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 10), count: 4)

    var body: some View {
        let state = model.state
        let pack = state.stickerPacks.first { $0.id == packId }
        let shown = pack.map { p in state.stickers.filter { $0.packId == p.id } } ?? state.stickers
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
                    createTile(.scissors, t("Вырезка"), t("Объект с фото — как на iPhone")) { editor = .cutout }
                    createTile(.image, t("Из фото"), t("Форма, обводка, подпись")) { editor = .photo }
                    createTile(.brush, t("Рисунок"), t("Кисти, кадры, ластик")) { editor = .drawing }
                    createTile(.smile, t("Смайл"), t("Лицо, глаза, рот, детали")) { editor = .smiley }
                    createTile(.award, t("Значок"), t("Текст или эмодзи на кристалле")) { editor = .badge }
                    createTile(.grid, t("Коллекция"), t("Соберите свой набор стикеров")) { naming = PackNaming(pack: nil) }
                }
                .padding(.horizontal, 16)

                if !state.stickerPacks.isEmpty {
                    collections(state, pack)
                }

                Text(pack?.name.uppercased() ?? t("Мои стикеры").uppercased()).textStyle(.label, Palette.mist).padding(.horizontal, 24)
                if shown.isEmpty {
                    Text(pack == nil
                         ? t("Здесь появятся ваши значки, смайлы и стикеры. Выберите способ выше.")
                         : t("В коллекции пока пусто. Новый стикер, сделанный здесь, попадёт сюда; готовый — через его меню «В коллекцию…»."))
                        .textStyle(.bodySmall, Palette.mist)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, 24)
                }
                LazyVGrid(columns: columns, spacing: 10) {
                    ForEach(shown) { sticker in
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
            StickerEditor(initial: kind, packId: pack?.id ?? "", onClose: { editor = nil }) { sticker in
                model.addSticker(sticker)
                editor = nil
            }
        }
        .glassActions(item: $menu, title: { $0.kind.title }) { sticker in
            [
                GlassAction(icon: .grid, title: state.stickerPacks.isEmpty ? t("Новая коллекция…") : t("В коллекцию…")) {
                    if state.stickerPacks.isEmpty {
                        pendingMove = sticker
                        naming = PackNaming(pack: nil)
                    } else {
                        moving = sticker
                    }
                },
                GlassAction(icon: .share, title: t("Поделиться")) { sharing = sticker },
                GlassAction(icon: .trash, title: t("Удалить"), destructive: true) {
                    sticker.files.forEach { try? FileManager.default.removeItem(at: MediaFiles.directory.appendingPathComponent($0)) }
                    model.deleteSticker(sticker.id)
                },
            ]
        }
        .glassActions(item: $moving, title: { _ in t("В коллекцию") }, cancel: true) { sticker in
            let options = [StickerPack(id: "", name: t("Без коллекции"))] + state.stickerPacks
            var actions: [GlassAction] = options.map { p in
                GlassAction(icon: sticker.packId == p.id ? .check : .grid, title: p.name) {
                    model.moveSticker(sticker.id, p.id)
                }
            }
            actions.append(GlassAction(icon: .plus, title: t("Новая коллекция")) {
                pendingMove = sticker
                naming = PackNaming(pack: nil)
            })
            return actions
        }
        .sheet(item: $naming, onDismiss: { pendingMove = nil }) { item in
            PackNameSheet(title: item.pack == nil ? t("Новая коллекция") : t("Название коллекции"), initial: item.pack?.name ?? "") { name in
                if let editing = item.pack {
                    model.renameStickerPack(editing.id, name)
                } else if let created = model.createStickerPack(name) {
                    packId = created.id
                    if let sticker = pendingMove { model.moveSticker(sticker.id, created.id) }
                }
                pendingMove = nil
            }
        }
        .sheet(item: $sharing) { sticker in
            ShareSheet(items: sticker.files.map { MediaFiles.directory.appendingPathComponent($0) as Any })
        }
    }

    private func collections(_ state: UiState, _ pack: StickerPack?) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(t("КОЛЛЕКЦИИ")).textStyle(.label, Palette.mist).padding(.horizontal, 24)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    Chip(text: t("Все"), active: pack == nil) { packId = "" }
                    ForEach(state.stickerPacks) { p in
                        let count = state.stickers.filter { $0.packId == p.id }.count
                        Chip(text: "\(p.name) · \(count)", active: p.id == pack?.id) { packId = p.id }
                    }
                    Chip(text: "+ " + t("Новая"), active: false) { naming = PackNaming(pack: nil) }
                }
                .padding(.horizontal, 16)
            }
            if let pack {
                HStack(spacing: 10) {
                    GhostButton(title: t("Переименовать")) { naming = PackNaming(pack: pack) }
                    GhostButton(title: t("Удалить коллекцию")) {
                        model.deleteStickerPack(pack.id)
                        packId = ""
                    }
                }
                .padding(.horizontal, 16)
            }
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

/// Окно имени коллекции: pack == nil — новая.
private struct PackNaming: Identifiable {
    let id = UUID()
    let pack: StickerPack?
}

/// Имя коллекции: поле и «Готово».
private struct PackNameSheet: View {
    let title: String
    let initial: String
    let onDone: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(title).textStyle(.headline)
            GlassField(placeholder: t("Например, «Котики»"), text: $name, limit: 40, singleLine: true)
                .glass(radius: 16)
            HStack(spacing: 10) {
                GhostButton(title: t("Отмена")) { dismiss() }
                Spacer()
                GlowButton(title: t("Готово"), enabled: !name.trimmingCharacters(in: .whitespaces).isEmpty) {
                    onDone(name)
                    dismiss()
                }
            }
            Spacer(minLength: 0)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.night.ignoresSafeArea())
        .presentationDetents([.height(220)])
        .presentationBackground(Palette.night)
        .presentationCornerRadius(28)
        .onAppear { name = initial }
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

/// Картинка стикера из файла; живой — двигается и листает кадры.
struct StickerImage: View {
    let sticker: Sticker
    @State private var image: UIImage?

    var body: some View {
        if sticker.animated {
            LiveSticker(sticker: sticker)
        } else {
            ZStack {
                if let image { Image(uiImage: image).resizable().scaledToFit() }
            }
            .task(id: sticker.fileName) {
                let url = sticker.url
                image = await Task.detached { UIImage(contentsOfFile: url.path) }.value
            }
        }
    }
}

// MARK: - Редактор

/// Входы картинки с эффектом — ключ пересчёта предпросмотра.
private struct PreviewKey: Equatable {
    let kind: StickerKind
    let effect: StickerEffect
    let photo: ObjectIdentifier?
    let cut: ObjectIdentifier?
    let photoParts: PhotoParts
    let strokes: [DrawStroke]
    let smiley: SmileyParts
    let badge: BadgeParts
}

/// Редактор стикера во весь экран: предпросмотр сверху, инструменты снизу.
/// Общие для всех способов — эффект (запекается в картинку) и движение.
/// Рисунок может быть покадровым: прошлый кадр просвечивает «калькой».
private struct StickerEditor: View {
    let initial: StickerKind
    /// Открытая в студии коллекция: новый стикер попадёт в неё.
    let packId: String
    let onClose: () -> Void
    let onSaved: (Sticker) -> Void

    @State private var kind: StickerKind
    @State private var photo: UIImage?
    @State private var photoItem: PhotosPickerItem?
    @State private var showPicker = false
    @State private var photoParts = PhotoParts()
    @State private var cut: UIImage?
    @State private var cutting = false
    /// Кадры рисунка: у каждого свои линии.
    @State private var frames: [[DrawStroke]] = [[]]
    @State private var frameIx = 0
    @State private var fps = 6
    @State private var playing = false
    @State private var liveImages: [UIImage] = []
    @State private var current: [CGPoint] = []
    @State private var brushColor: UInt32 = stickerColors[4]
    @State private var brushWidth: CGFloat = 0.03
    @State private var eraser = false
    @State private var smiley = SmileyParts()
    @State private var badge = BadgeParts()
    @State private var effect: StickerEffect
    @State private var anim: StickerAnim = .none
    @State private var dragStart: CGPoint?
    @State private var saving = false
    @State private var failed = false
    @State private var notice: String?
    /// Картинка с эффектом для предпросмотра — считается не в главном потоке.
    @State private var effectPreview: UIImage?

    /// Больше кадров не нужно: дюжины хватает на живой стикер.
    private static let maxFrames = 12

    init(initial: StickerKind, packId: String, onClose: @escaping () -> Void, onSaved: @escaping (Sticker) -> Void) {
        self.initial = initial
        self.packId = packId
        self.onClose = onClose
        self.onSaved = onSaved
        _kind = State(initialValue: initial)
        _effect = State(initialValue: initial == .cutout ? .outline : .none)
    }

    private var strokes: [DrawStroke] { frames.indices.contains(frameIx) ? frames[frameIx] : [] }

    private func setStrokes(_ list: [DrawStroke]) {
        guard frames.indices.contains(frameIx) else { return }
        frames[frameIx] = list
    }

    private var drawnFrames: [[DrawStroke]] { frames.filter { f in f.contains { !$0.eraser } } }

    private func base(_ frame: [DrawStroke]? = nil, live: Bool = false) -> UIImage? {
        switch kind {
        case .photo: return photo.map { StickerArt.photo($0, photoParts) }
        case .cutout: return cut
        case .drawing:
            var all = frame ?? strokes
            if live && !current.isEmpty { all.append(DrawStroke(points: current, color: brushColor, width: brushWidth, eraser: eraser)) }
            return StickerArt.drawing(all)
        case .smiley: return StickerArt.smiley(smiley)
        case .badge: return StickerArt.badge(badge)
        }
    }

    /// Пока палец рисует — без эффекта (он тяжёлый), после — сразу с ним.
    /// Пока палец на рамке — всегда без эффекта и сразу; без эффекта —
    /// тоже сразу. С эффектом — готовая картинка из фона, пока она
    /// считается — без эффекта.
    private func preview() -> UIImage? {
        if !current.isEmpty || effect == .none { return base(live: true) }
        return effectPreview ?? base()
    }

    /// Всё, от чего зависит картинка с эффектом: изменилось — пересчитать.
    private var previewKey: PreviewKey {
        PreviewKey(
            kind: kind, effect: effect, photo: photo.map { ObjectIdentifier($0) }, cut: cut.map { ObjectIdentifier($0) },
            photoParts: photoParts, strokes: strokes, smiley: smiley, badge: badge
        )
    }

    /// Эффект — в фоне, с паузой ~30 мс: быстрые правки не копят очередь.
    private func renderEffect() async {
        guard effect != .none, let raw = base() else {
            effectPreview = nil
            return
        }
        try? await Task.sleep(nanoseconds: 30_000_000)
        if Task.isCancelled { return }
        let fx = effect
        let done = await Task.detached(priority: .userInitiated) { StickerFx.apply(raw, fx) }.value
        if !Task.isCancelled { effectPreview = done }
    }

    private var empty: Bool {
        switch kind {
        case .photo: return photo == nil
        case .cutout: return cut == nil
        case .drawing: return drawnFrames.isEmpty
        case .badge: return badge.text.trimmingCharacters(in: .whitespaces).isEmpty
        case .smiley: return false
        }
    }

    private var canPlay: Bool { anim != .none || (kind == .drawing && drawnFrames.count > 1) }

    /// Кадры для «Смотреть»: готовые, с эффектом.
    private func refreshLive() {
        guard playing else {
            liveImages = []
            return
        }
        let list = kind == .drawing ? (drawnFrames.isEmpty ? [strokes] : drawnFrames) : [strokes]
        let bases = list.compactMap { base($0) }
        let fx = effect
        // Эффекты тяжёлые — в фоне; кадры появятся, когда будут готовы
        Task {
            let images = await Task.detached(priority: .userInitiated) { bases.map { StickerFx.apply($0, fx) } }.value
            if playing && fx == effect { liveImages = images }
        }
    }

    private func setPlaying(_ on: Bool) {
        playing = on
        refreshLive()
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                RoundGlyphButton(icon: .close, action: onClose)
                Text(kind.title).textStyle(.headline)
                Spacer()
                GlowButton(title: t("Сохранить"), enabled: !empty && !saving && !cutting) { save() }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)

            previewBox
                .padding(.horizontal, 32)
                .padding(.vertical, 8)

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    controls
                    liveControls
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .background(Palette.void.ignoresSafeArea())
        .onAppear { if initial == .photo || initial == .cutout { showPicker = true } }
        .photosPicker(isPresented: $showPicker, selection: $photoItem, matching: .images)
        .onChange(of: showPicker) { _, open in
            if !open && photo == nil && photoItem == nil && (kind == .photo || kind == .cutout) {
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
                    let loaded = downscaled(image, maxSide: 1024)
                    photo = loaded
                    if kind == .cutout {
                        cut = nil
                        await cutOut(loaded)
                    }
                } else if photo == nil {
                    notice = t("Не удалось открыть фото")
                    onClose()
                }
                photoItem = nil
            }
        }
        .onChange(of: effect) { _, _ in refreshLive() }
        .onChange(of: fps) { _, _ in refreshLive() }
        .task(id: previewKey) { await renderEffect() }
        .glassActions($failed, title: t("Не удалось сохранить"), actions: [GlassAction(icon: .check, title: t("Понятно")) {}])
        .toast($notice)
    }

    /// Вырезать объект; не нашёлся — обрезка формой, как «Из фото».
    @MainActor
    private func cutOut(_ source: UIImage) async {
        cutting = true
        let result = await SubjectCutout.cut(source)
        cutting = false
        if let result {
            cut = result
        } else {
            notice = t("Объект не нашёлся — обрежем фото формой")
            kind = .photo
            if effect == .outline { effect = .none }
        }
        refreshLive()
    }

    private func save() {
        saving = true
        let list = kind == .drawing ? drawnFrames : [strokes]
        let bases = list.compactMap { base($0) }
        let fx = effect
        let (kind, anim, fps, packId) = (self.kind, self.anim, self.fps, self.packId)
        // Эффекты и запись файлов — не в главном потоке
        Task {
            let sticker = await Task.detached(priority: .userInitiated) {
                StickerArt.saveAnimated(bases.map { StickerFx.apply($0, fx) }, kind: kind, animation: anim, fps: fps, packId: packId)
            }.value
            saving = false
            if let sticker { onSaved(sticker) } else { failed = true }
        }
    }

    private func downscaled(_ image: UIImage, maxSide: CGFloat) -> UIImage {
        let side = max(image.size.width, image.size.height)
        let size: CGSize
        if side > maxSide {
            let k = maxSide / side
            size = CGSize(width: image.size.width * k, height: image.size.height * k)
        } else {
            size = image.size
        }
        // Перерисовка ещё и ставит снимок «прямо» — для Vision и обрезки
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: size, format: format).image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
    }

    /// Предпросмотр на «шахматке» — так видно прозрачные места.
    private var previewBox: some View {
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
                if playing && !liveImages.isEmpty {
                    LiveFrames(images: liveImages, fps: fps, animation: anim.id)
                } else {
                    if kind == .drawing && frameIx > 0 && frames.indices.contains(frameIx - 1) {
                        // Калька: прошлый кадр просвечивает
                        Image(uiImage: StickerArt.drawing(frames[frameIx - 1])).resizable().opacity(0.28)
                    }
                    if let image = preview() {
                        Image(uiImage: image).resizable().interpolation(.high)
                    }
                }
                if cutting {
                    Text(t("Вырезаю объект…"))
                        .textStyle(.title)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Palette.night.opacity(0.8)))
                }
            }
            .frame(width: side, height: side)
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).stroke(Palette.line, lineWidth: 1))
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        guard !playing else { return }
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
                        // Линия — в кадр, открытый в момент отпускания пальца (состояние читается сейчас, не из прошлого)
                        if kind == .drawing, !current.isEmpty, frames.indices.contains(frameIx) {
                            frames[frameIx].append(DrawStroke(points: current, color: brushColor, width: brushWidth, eraser: eraser))
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
        case .cutout:
            Text(t("Объект с фото вырезается сам — человек, питомец, предмет. Добавьте контур или блеск и движение ниже."))
                .textStyle(.bodySmall, Palette.mist)
                .fixedSize(horizontal: false, vertical: true)
            GhostButton(title: t("Другое фото")) { showPicker = true }
        case .drawing:
            frameControls
            HStack(spacing: 8) {
                Chip(text: t("Кисть"), active: !eraser) { eraser = false }
                Chip(text: t("Ластик"), active: eraser) { eraser = true }
                Spacer()
                RoundGlyphButton(icon: .undo, color: strokes.isEmpty ? Palette.mist : Palette.lilac) {
                    if !strokes.isEmpty { setStrokes(Array(strokes.dropLast())) }
                }
                RoundGlyphButton(icon: .trash, color: strokes.isEmpty ? Palette.mist : Palette.lilac) {
                    if !strokes.isEmpty { setStrokes([]) }
                }
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

    /// Кадры покадрового рисунка: выбор, новый, копия, удалить и скорость.
    @ViewBuilder
    private var frameControls: some View {
        label(t("Кадры"))
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(frames.indices, id: \.self) { i in
                    Chip(text: "\(i + 1)", active: i == frameIx) {
                        frameIx = i
                        setPlaying(false)
                    }
                }
                if frames.count < Self.maxFrames {
                    Chip(text: "+ " + t("Кадр"), active: false) {
                        frames.append([])
                        frameIx = frames.count - 1
                        setPlaying(false)
                    }
                    Chip(text: t("Копия"), active: false) {
                        frames.insert(strokes, at: frameIx + 1)
                        frameIx += 1
                        setPlaying(false)
                    }
                }
                Chip(text: t("Удалить кадр"), active: false) {
                    if frames.count > 1 {
                        frames.remove(at: frameIx)
                        frameIx = min(frameIx, frames.count - 1)
                    } else {
                        frames = [[]]
                    }
                    refreshLive()
                }
            }
        }
        if frames.count > 1 {
            label(t("Скорость"))
            choices([4, 6, 8, 12], fps, title: { t("%d к/с").replacingOccurrences(of: "%d", with: "\($0)") }) { fps = $0 }
        } else {
            Text(t("Добавьте кадры — и рисунок оживёт. Прошлый кадр просвечивает, чтобы следующий лёг точно."))
                .textStyle(.bodySmall, Palette.mist)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    /// Эффект (запекается в картинку) и движение (играет при показе).
    @ViewBuilder
    private var liveControls: some View {
        label(t("Эффект"))
        choices(StickerEffect.allCases, effect, title: \.title) { effect = $0 }
        label(t("Движение"))
        choices(StickerAnim.allCases, anim, title: \.title) { a in
            anim = a
            setPlaying(a != .none || frames.count > 1)
        }
        if canPlay {
            HStack(spacing: 8) {
                Chip(text: t("Смотреть"), active: playing) { setPlaying(true) }
                Chip(text: t("Править"), active: !playing) { setPlaying(false) }
            }
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
