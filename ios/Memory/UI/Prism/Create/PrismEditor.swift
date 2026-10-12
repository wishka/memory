import AVFoundation
import SwiftUI
import UIKit

/// Инструменты редактора — вкладки внизу.
enum EditorTool: String, CaseIterable {
    case trim = "Обрезка"
    case speed = "Скорость"
    case frame = "Кадр"
    case filters = "Фильтры"
    case adjust = "Коррекция"
    case text = "Текст"
    case stickers = "Стикеры"
    case music = "Музыка"
    case sound = "Звук"
    case cover = "Обложка"

    var title: String { t(rawValue) }

    var icon: LineIcon {
        switch self {
        case .trim: return .scissors
        case .speed: return .speed
        case .frame: return .crop
        case .filters: return .filter
        case .adjust: return .sliders
        case .text: return .text
        case .stickers: return .sticker
        case .music: return .music
        case .sound: return .volume
        case .cover: return .image
        }
    }
}

/// Текст в окне правки: новый или уже стоящий в кадре.
private struct TextDraft: Identifiable {
    var overlay: VideoOverlay
    let isNew: Bool
    var id: Int64 { overlay.id }
}

/// Редактор PRISM: предпросмотр с правками и инструменты. Видео не
/// меняется до публикации — правки копятся в VideoEdit, предпросмотр
/// собирается из них той же композицией, что и итоговый файл. Текст и
/// стикеры в предпросмотре — поверх видео, их можно таскать пальцем.
struct PrismEditor: View {
    let model: AppModel
    @Binding var edit: VideoEdit
    let onBack: () -> Void
    let onNext: () -> Void

    @State private var looper = LoopingPlayer()
    @State private var tool: EditorTool = .trim
    @State private var playing = true
    @State private var selected: Int64?
    @State private var draft: TextDraft?
    @State private var rebuild: Task<Void, Never>?
    @State private var loaded = false
    @State private var choosingMusic = false

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                RoundGlyphButton(icon: .back) { onBack() }
                Text(tf("%s · %s", t("Редактор"), durationTitle(edit.outputDurationMs)))
                    .textStyle(.title)
                    .lineLimit(1)
                Spacer()
                GlowButton(title: t("Далее")) {
                    looper.set(playing: false)
                    onNext()
                }
            }
            .padding(.horizontal, 12)
            .padding(.top, 8)
            .padding(.bottom, 8)

            preview
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(.horizontal, 12)

            ScrollView {
                VStack(alignment: .leading, spacing: 12) { panel }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .frame(height: 210)
            .scrollIndicators(.hidden)

            toolBar
        }
        .background(Palette.void.ignoresSafeArea())
        .task { await reload(keepPosition: false) }
        .onDisappear {
            rebuild?.cancel()
            looper.set(playing: false)
        }
        .onChange(of: previewKey) { _, _ in
            rebuild?.cancel()
            rebuild = Task {
                try? await Task.sleep(nanoseconds: 180_000_000)
                if Task.isCancelled { return }
                await reload(keepPosition: true)
            }
        }
        .fullScreenCover(isPresented: $choosingMusic) {
            // Медиатека: трек скачивается в «Мою музыку» и сразу встаёт на ролик
            MusicPicker(model: model, onPick: { picked in
                choosingMusic = false
                update { e in
                    e.musicTrackId = picked.id
                    e.musicStartMs = 0
                }
            }, onDismiss: { choosingMusic = false })
        }
        .sheet(item: $draft) { d in
            TextEditorSheet(draft: d) { done in
                if d.isNew {
                    edit.overlays.append(done)
                } else {
                    edit.overlays = edit.overlays.map { $0.id == done.id ? done : $0 }
                }
                selected = done.id
                draft = nil
            } onCancel: {
                draft = nil
            }
        }
    }

    /// Всё, от чего зависит видео предпросмотра. Текст и обложка — нет:
    /// текст рисуется поверх, обложка выбирается отдельно.
    private var previewKey: VideoEdit {
        var e = edit
        e.overlays = []
        e.coverMs = 0
        return e
    }

    private func musicURL(_ e: VideoEdit) -> URL? {
        e.musicTrackId.flatMap { model.state.track($0) }?.url
    }

    @MainActor
    private func reload(keepPosition: Bool) async {
        let e = previewKey
        guard let built = try? await VideoExporter.build(e, musicURL: musicURL(e)) else { return }
        if Task.isCancelled { return }
        looper.load(built.playerItem(), playing: playing, keepPosition: keepPosition && loaded)
        loaded = true
    }

    private func update(_ change: (inout VideoEdit) -> Void) {
        var e = edit
        change(&e)
        edit = e
    }

    // MARK: - Предпросмотр

    private var preview: some View {
        GeometryReader { geo in
            let aspect = CGFloat(edit.outputAspect)
            let width = min(geo.size.width, geo.size.height * aspect)
            let height = width / aspect
            ZStack {
                PlayerLayerView(player: looper.player, gravity: .resizeAspect)
                    .onTapGesture {
                        if selected != nil {
                            selected = nil
                        } else {
                            playing.toggle()
                            looper.set(playing: playing)
                        }
                    }
                ForEach(edit.overlays) { o in
                    OverlayLabel(overlay: o, frameHeight: height, selected: o.id == selected)
                        .position(x: CGFloat(o.x) * width, y: CGFloat(o.y) * height)
                        .gesture(
                            DragGesture(minimumDistance: 2, coordinateSpace: .named("preview"))
                                .onChanged { value in
                                    selected = o.id
                                    moveOverlay(o.id, to: value.location, in: CGSize(width: width, height: height))
                                }
                        )
                        .onTapGesture(count: 2) {
                            if !o.sticker { draft = TextDraft(overlay: o, isNew: false) }
                        }
                        .onTapGesture {
                            selected = o.id
                            tool = o.sticker ? .stickers : .text
                        }
                }
                if !playing {
                    Glyph(kind: .play, color: Palette.ink, size: 44)
                        .opacity(0.8)
                        .allowsHitTesting(false)
                }
            }
            .frame(width: width, height: height)
            .coordinateSpace(.named("preview"))
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(Palette.line, lineWidth: 1))
            .position(x: geo.size.width / 2, y: geo.size.height / 2)
        }
    }

    private func moveOverlay(_ id: Int64, to point: CGPoint, in size: CGSize) {
        let x = min(0.98, max(0.02, point.x / size.width))
        let y = min(0.98, max(0.02, point.y / size.height))
        edit.overlays = edit.overlays.map { o in
            guard o.id == id else { return o }
            var o = o
            o.x = Double(x)
            o.y = Double(y)
            return o
        }
    }

    private var selectedOverlay: VideoOverlay? {
        edit.overlays.first { $0.id == selected }
    }

    private func changeOverlay(_ o: VideoOverlay) {
        edit.overlays = edit.overlays.map { $0.id == o.id ? o : $0 }
    }

    private func deleteOverlay(_ id: Int64) {
        edit.overlays.removeAll { $0.id == id }
        selected = nil
    }

    private func newOverlayId() -> Int64 {
        (edit.overlays.map(\.id).max() ?? 0) + 1
    }

    // MARK: - Инструменты

    private var toolBar: some View {
        ScrollView(.horizontal) {
            HStack(spacing: 4) {
                ForEach(EditorTool.allCases, id: \.self) { item in
                    Button {
                        tool = item
                    } label: {
                        VStack(spacing: 4) {
                            Glyph(kind: item.icon, color: item == tool ? Palette.ink : Palette.lilac, size: 22)
                            Text(item.title).textStyle(.label, item == tool ? Palette.ink : Palette.mist)
                        }
                        .frame(width: 72, height: 58)
                        .background {
                            if item == tool {
                                RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Palette.plum.opacity(0.7))
                            }
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 8)
        }
        .scrollIndicators(.hidden)
        .padding(.bottom, 8)
    }

    @ViewBuilder
    private var panel: some View {
        switch tool {
        case .trim: trimPanel
        case .speed:
            FlowLayout {
                ForEach(VideoEdit.speeds, id: \.self) { s in
                    Chip(text: speedTitle(s), active: edit.speed == s) { update { $0.speed = s } }
                }
            }
        case .frame: framePanel
        case .filters:
            FlowLayout {
                ForEach(VideoFilter.allCases, id: \.self) { f in
                    FilterSwatch(filter: f, active: edit.filter == f) { update { $0.filter = f } }
                }
            }
        case .adjust:
            AdjustSlider(title: t("Яркость"), value: $edit.brightness)
            AdjustSlider(title: t("Контраст"), value: $edit.contrast)
            AdjustSlider(title: t("Насыщенность"), value: $edit.saturation)
            AdjustSlider(title: t("Тепло"), value: $edit.warmth)
            GhostButton(title: t("Сбросить")) {
                update { $0.brightness = 0; $0.contrast = 0; $0.saturation = 0; $0.warmth = 0 }
            }
        case .text: textPanel
        case .stickers: stickerPanel
        case .music: musicPanel
        case .sound:
            LabeledSlider(title: t("Звук видео"), value: $edit.originalVolume, range: 0...1, caption: percent(edit.originalVolume))
            FlowLayout {
                Chip(text: t("Без звука"), active: edit.originalVolume == 0) { update { $0.originalVolume = 0 } }
                Chip(text: t("Как снято"), active: edit.originalVolume == 1) { update { $0.originalVolume = 1 } }
            }
            if edit.musicTrackId != nil {
                LabeledSlider(title: t("Музыка"), value: $edit.musicVolume, range: 0...1, caption: percent(edit.musicVolume))
            }
        case .cover: CoverPanel(edit: $edit)
        }
    }

    private var trimPanel: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(tf("%s — %s · %s", durationTitle(edit.trimStartMs), durationTitle(edit.trimEndMs), durationTitle(edit.trimEndMs - edit.trimStartMs)))
                .textStyle(.title)
            TrimSlider(
                start: edit.trimStartMs, end: edit.trimEndMs, total: edit.sourceDurationMs,
                minGap: VideoEdit.minDurationMs, maxGap: VideoEdit.maxDurationMs
            ) { start, end in
                update { e in
                    e.trimStartMs = start
                    e.trimEndMs = end
                    e.coverMs = min(max(e.coverMs, start), end)
                }
            }
            Text(t("PRISM — до минуты. Перетащите края, чтобы оставить нужное."))
                .textStyle(.bodySmall, Palette.mist)
        }
    }

    private var framePanel: some View {
        VStack(alignment: .leading, spacing: 12) {
            FlowLayout {
                ForEach(Aspect.allCases, id: \.self) { a in
                    Chip(text: a.title, active: edit.aspect == a) { update { $0.aspect = a } }
                }
            }
            HStack(spacing: 10) {
                ToolButton(icon: .rotate, title: t("Повернуть")) { update { $0.rotation = ($0.rotation + 90) % 360 } }
                ToolButton(icon: .mirror, title: t("Отразить"), active: edit.mirror) { update { $0.mirror.toggle() } }
            }
        }
    }

    @ViewBuilder
    private var textPanel: some View {
        HStack(spacing: 10) {
            GlowButton(title: t("Добавить текст")) {
                draft = TextDraft(overlay: VideoOverlay(id: newOverlayId(), text: ""), isNew: true)
            }
            if let o = selectedOverlay, !o.sticker {
                GhostButton(title: t("Изменить")) { draft = TextDraft(overlay: o, isNew: false) }
            }
        }
        if let o = selectedOverlay, !o.sticker {
            OverlayStyleControls(overlay: o, onChange: changeOverlay)
            LabeledSlider(
                title: t("Размер"),
                value: Binding(get: { o.size }, set: { v in var c = o; c.size = v; changeOverlay(c) }),
                range: 0.025...0.14, caption: ""
            )
            GhostButton(title: t("Удалить текст")) { deleteOverlay(o.id) }
        } else {
            Text(t("Нажмите на текст в кадре, чтобы выбрать его; перетаскивайте пальцем."))
                .textStyle(.bodySmall, Palette.mist)
        }
    }

    @ViewBuilder
    private var stickerPanel: some View {
        if let o = selectedOverlay, o.sticker {
            LabeledSlider(
                title: t("Размер"),
                value: Binding(get: { o.size }, set: { v in var c = o; c.size = v; changeOverlay(c) }),
                range: 0.04...0.3, caption: ""
            )
            GhostButton(title: t("Удалить стикер")) { deleteOverlay(o.id) }
        }
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 8), spacing: 6) {
            ForEach(stickers, id: \.self) { s in
                Button {
                    let o = VideoOverlay(
                        id: newOverlayId(), text: s,
                        x: 0.5 + Double.random(in: -0.15...0.15), y: 0.4 + Double.random(in: -0.15...0.15),
                        size: 0.1, color: 0xF3EEFF,
                        sticker: true
                    )
                    edit.overlays.append(o)
                    selected = o.id
                } label: {
                    Text(s)
                        .font(.system(size: 26))
                        .foregroundStyle(Palette.ink)
                        .frame(width: 40, height: 40)
                        .glass(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .buttonStyle(.plain)
            }
        }
    }

    @ViewBuilder
    private var musicPanel: some View {
        let tracks = model.state.tracks
        GlowButton(title: t("Медиатека")) { choosingMusic = true }
        if tracks.isEmpty {
            Text(t("В «Моей музыке» пока нет треков — выберите трек в медиатеке или загрузите свои в профиле."))
                .textStyle(.bodySmall, Palette.mist)
        }
        Chip(text: t("Без музыки"), active: edit.musicTrackId == nil) { update { $0.musicTrackId = nil } }
        ForEach(tracks) { track in
            Button {
                update { e in
                    if e.musicTrackId != track.id {
                        e.musicTrackId = track.id
                        e.musicStartMs = 0
                    }
                }
            } label: {
                HStack(spacing: 12) {
                    Glyph(kind: .music, color: edit.musicTrackId == track.id ? Palette.ink : Palette.lilac, size: 20)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(track.title).textStyle(.body).lineLimit(1)
                        Text(track.artist.isEmpty ? durationTitle(track.durationMs) : tf("%s · %s", track.artist, durationTitle(track.durationMs)))
                            .textStyle(.bodySmall, Palette.mist)
                            .lineLimit(1)
                    }
                    Spacer()
                    if edit.musicTrackId == track.id { Glyph(kind: .check, color: Palette.lilac, size: 18) }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .glass(RoundedRectangle(cornerRadius: 16, style: .continuous), strong: edit.musicTrackId == track.id)
            }
            .buttonStyle(.plain)
        }
        if let id = edit.musicTrackId, let current = model.state.track(id) {
            LabeledSlider(
                title: t("Начало трека"),
                value: Binding(get: { Double(edit.musicStartMs) }, set: { v in update { $0.musicStartMs = Int64(v) } }),
                range: 0...Double(max(1, current.durationMs)),
                caption: durationTitle(edit.musicStartMs)
            )
            LabeledSlider(title: t("Громкость музыки"), value: $edit.musicVolume, range: 0...1, caption: percent(edit.musicVolume))
        }
    }

    private func percent(_ v: Double) -> String { "\(Int((v * 100).rounded()))%" }
}

/// «0.5×», «1×», «1.5×».
func speedTitle(_ s: Double) -> String {
    let text = s == s.rounded() ? String(Int(s)) : String(s)
    return text + "×"
}

// MARK: - Части редактора

/// Текст или стикер в предпросмотре — так же, как его нарисует экспорт.
private struct OverlayLabel: View {
    let overlay: VideoOverlay
    let frameHeight: CGFloat
    let selected: Bool

    var body: some View {
        let o = overlay
        let size = max(6, CGFloat(o.size) * frameHeight)
        let color = RGB(o.color).color
        let font: Font = o.sticker
            ? .system(size: size)
            : .custom(o.font == .unbounded ? Fonts.unboundedRegular : Fonts.interSemiBold, size: size)
        let label = Text(o.text)
            .font(font)
            .multilineTextAlignment(.center)
            .fixedSize()
        Group {
            if o.sticker {
                label
            } else {
                switch o.style {
                case .plain:
                    label.foregroundStyle(color).shadow(color: .black.opacity(0.55), radius: size * 0.06, y: size * 0.04)
                case .glow:
                    label.foregroundStyle(color).shadow(color: color, radius: size * 0.22)
                case .box:
                    label
                        .foregroundStyle(RGB(VideoExporter.contrastOn(o.color)).color)
                        .padding(.horizontal, size * 0.35)
                        .padding(.vertical, size * 0.17)
                        .background(RoundedRectangle(cornerRadius: size * 0.4, style: .continuous).fill(color))
                }
            }
        }
        .padding(4)
        .overlay {
            if selected {
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .stroke(Palette.lilac, style: StrokeStyle(lineWidth: 1.5, dash: [5, 4]))
            }
        }
        .contentShape(Rectangle())
    }
}

/// Обрезка: две ручки на полосе исходного видео. Между ручками — не
/// меньше minGap и не больше maxGap.
private struct TrimSlider: View {
    let start: Int64
    let end: Int64
    let total: Int64
    let minGap: Int64
    let maxGap: Int64
    let onChange: (Int64, Int64) -> Void

    private let thumb: CGFloat = 26

    var body: some View {
        GeometryReader { geo in
            let track = max(1, geo.size.width - thumb)
            let x0 = CGFloat(Double(start) / Double(max(1, total))) * track
            let x1 = CGFloat(Double(end) / Double(max(1, total))) * track
            ZStack(alignment: .leading) {
                Capsule().fill(Palette.plum.opacity(0.8)).frame(height: 6)
                    .padding(.horizontal, thumb / 2)
                Capsule()
                    .fill(LinearGradient(colors: [Palette.orchid, Palette.sky], startPoint: .leading, endPoint: .trailing))
                    .frame(width: max(0, x1 - x0), height: 6)
                    .offset(x: x0 + thumb / 2)
                handle.offset(x: x0).gesture(drag(track: track, low: true))
                handle.offset(x: x1).gesture(drag(track: track, low: false))
            }
            .frame(height: thumb)
            .coordinateSpace(.named("trim"))
        }
        .frame(height: 26)
    }

    private var handle: some View {
        Circle()
            .fill(Palette.ink)
            .frame(width: thumb, height: thumb)
            .overlay(Circle().stroke(Palette.lilac, lineWidth: 3))
            .shadow(color: Palette.orchid.opacity(0.6), radius: 6)
    }

    private func drag(track: CGFloat, low: Bool) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .named("trim"))
            .onChanged { value in
                let ms = Int64(Double(min(max(0, value.location.x - thumb / 2), track) / track) * Double(total))
                if low {
                    var s = min(ms, end - min(minGap, end))
                    s = max(s, end - maxGap, 0)
                    onChange(s, end)
                } else {
                    var e = max(ms, start + minGap)
                    e = min(e, start + maxGap, total)
                    onChange(start, e)
                }
            }
    }
}

/// Ползунок с подписью и значением.
private struct LabeledSlider: View {
    let title: String
    @Binding var value: Double
    let range: ClosedRange<Double>
    let caption: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(title).textStyle(.labelLarge, Palette.mist)
                Spacer()
                Text(caption).textStyle(.labelLarge, Palette.lilac)
            }
            Slider(value: $value, in: range).tint(Palette.lilac)
        }
    }
}

/// Коррекция: −100…+100, двойное касание по названию — сброс.
private struct AdjustSlider: View {
    let title: String
    @Binding var value: Double

    var body: some View {
        let shown = Int((value * 100).rounded())
        LabeledSlider(title: title, value: $value, range: -1...1, caption: shown > 0 ? "+\(shown)" : "\(shown)")
            .onTapGesture(count: 2) { value = 0 }
    }
}

/// Фильтр: образец цвета и название.
private struct FilterSwatch: View {
    let filter: VideoFilter
    let active: Bool
    let action: () -> Void

    private static let samples: [UInt32] = [0xFF9EDE, 0xBC92CD, 0x307EC0, 0xFFE08A]

    var body: some View {
        Button(action: action) {
            VStack(spacing: 6) {
                LinearGradient(colors: Self.samples.map(colored), startPoint: .topLeading, endPoint: .bottomTrailing)
                    .frame(width: 56, height: 56)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .stroke(active ? Palette.ink : Palette.line, lineWidth: active ? 2 : 1)
                    )
                Text(filter.title).textStyle(.label, active ? Palette.ink : Palette.mist)
            }
        }
        .buttonStyle(.plain)
    }

    private func colored(_ hex: UInt32) -> Color {
        let c = RGB(hex)
        let (r, g, b) = ColorMatrix.apply(filter.matrix, c.r, c.g, c.b)
        return RGB(r: r, g: g, b: b).color
    }
}

/// Кнопка-инструмент: значок и подпись на стекле.
private struct ToolButton: View {
    let icon: LineIcon
    let title: String
    var active = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Glyph(kind: icon, color: active ? Palette.ink : Palette.lilac, size: 20)
                Text(title).textStyle(.labelLarge, active ? Palette.ink : Palette.lilac)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .glass(Capsule(), strong: active)
        }
        .buttonStyle(.plain)
    }
}

/// Цвет, оформление и шрифт выбранного текста.
private struct OverlayStyleControls: View {
    let overlay: VideoOverlay
    let onChange: (VideoOverlay) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                ForEach(overlayColors, id: \.self) { c in
                    Button {
                        var o = overlay
                        o.color = c
                        onChange(o)
                    } label: {
                        Circle()
                            .fill(RGB(c).color)
                            .frame(width: 28, height: 28)
                            .overlay(Circle().stroke(overlay.color == c ? Palette.ink : Palette.line, lineWidth: overlay.color == c ? 2.5 : 1))
                    }
                    .buttonStyle(.plain)
                }
            }
            FlowLayout {
                ForEach(OverlayStyle.allCases, id: \.self) { s in
                    Chip(text: s.title, active: overlay.style == s) {
                        var o = overlay
                        o.style = s
                        onChange(o)
                    }
                }
                ForEach(OverlayFont.allCases, id: \.self) { f in
                    Chip(text: f.rawValue, active: overlay.font == f) {
                        var o = overlay
                        o.font = f
                        onChange(o)
                    }
                }
            }
        }
    }
}

/// Обложка: кадр из обрезанного куска и ползунок по нему.
private struct CoverPanel: View {
    @Binding var edit: VideoEdit
    @State private var image: UIImage?

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Palette.plum.opacity(0.6))
                if let image {
                    Image(uiImage: image).resizable().scaledToFill()
                }
            }
            .frame(width: 72, height: 128)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            VStack(alignment: .leading, spacing: 6) {
                Text(t("Кадр для обложки")).textStyle(.body)
                Text(t("Обложку видно в «Сохранённом» и в профиле. Фильтры и текст попадут на неё тоже."))
                    .textStyle(.bodySmall, Palette.mist)
                    .fixedSize(horizontal: false, vertical: true)
                Slider(
                    value: Binding(
                        get: { Double(min(max(edit.coverMs, edit.trimStartMs), edit.trimEndMs)) },
                        set: { edit.coverMs = Int64($0) }
                    ),
                    in: Double(edit.trimStartMs)...Double(max(edit.trimStartMs + 1, edit.trimEndMs))
                )
                .tint(Palette.lilac)
            }
        }
        .task(id: edit.coverMs) {
            try? await Task.sleep(nanoseconds: 120_000_000)
            if Task.isCancelled { return }
            image = await VideoExporter.frame(edit.sourceURL, ms: min(max(edit.coverMs, edit.trimStartMs), edit.trimEndMs), maxPixel: 320)
        }
    }
}

/// Окно ввода текста для кадра.
private struct TextEditorSheet: View {
    @State private var overlay: VideoOverlay
    let onDone: (VideoOverlay) -> Void
    let onCancel: () -> Void

    init(draft: TextDraft, onDone: @escaping (VideoOverlay) -> Void, onCancel: @escaping () -> Void) {
        _overlay = State(initialValue: draft.overlay)
        self.onDone = onDone
        self.onCancel = onCancel
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            GlassField(placeholder: t("Текст на видео"), text: $overlay.text, limit: 120, maxLines: 4)
                .glass(RoundedRectangle(cornerRadius: 18, style: .continuous))
            OverlayStyleControls(overlay: overlay) { overlay = $0 }
            HStack {
                Spacer()
                GhostButton(title: t("Отмена"), action: onCancel)
                GlowButton(title: t("Готово"), enabled: !overlay.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) {
                    onDone(overlay)
                }
            }
        }
        .padding(20)
        .presentationDetents([.height(320)])
        .presentationDragIndicator(.visible)
        .presentationBackground(Palette.night)
        .presentationCornerRadius(28)
    }
}
