import SwiftUI
import UIKit

/// Рисунок по эстафете и обмен без денег. То же — в TogetherDrawingSharing.kt.

// MARK: - 7. Рисунок по эстафете

/// Рисунок на тёмном фоне (0x0F153A) — для показа и публикации.
func drawingImage(_ strokes: [DrawStroke]) -> UIImage {
    let art = StickerArt.drawing(strokes)
    let format = UIGraphicsImageRendererFormat()
    format.scale = 1
    format.opaque = true
    return UIGraphicsImageRenderer(size: art.size, format: format).image { ctx in
        UIColor(rgb: 0x0F153A).setFill()
        ctx.fill(CGRect(origin: .zero, size: art.size))
        art.draw(at: .zero)
    }
}

/// Готовый рисунок — картинкой PNG в папке вложений, как картинка Итогов.
private func saveDrawing(_ d: CollabDrawing) -> Attachment? {
    guard let data = drawingImage(d.strokes).pngData() else { return nil }
    return MediaFiles.save(data, kind: .photo, ext: "png", name: "drawing.png")
}

private struct DrawingPreview: View {
    let strokes: [DrawStroke]
    @State private var image: UIImage?

    var body: some View {
        ZStack {
            Palette.night
            if let image { Image(uiImage: image).resizable().scaledToFit() }
        }
        .aspectRatio(1, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .task(id: strokes) { image = drawingImage(strokes) }
    }
}

struct DrawingSection: View {
    let model: AppModel
    let geo: TogetherGeo
    @State private var drawingId: Int64?
    @State private var creating = false
    /// Новый рисунок открывается, когда окно создания закрылось.
    @State private var pendingId: Int64?
    @State private var sharing: SharePayload?

    var body: some View {
        let state = model.state
        let me = state.me.id
        let drawings = state.together.drawings
        let myTurn = drawings.filter { $0.waitingFor == me && !$0.finished }
        let waiting = drawings.filter { $0.waitingFor != nil && $0.waitingFor != me && !$0.finished }
        let done = drawings.filter(\.finished)
        VStack(alignment: .leading, spacing: 12) {
            TogetherCard {
                TogetherHint(text: t("Один начинает рисунок и передаёт следующему, тот дорисовывает своё — и так по кругу. Получается общее произведение."))
                GlowButton(title: t("Начать рисунок")) { creating = true }
            }
            if !myTurn.isEmpty { TogetherCaption(text: t("Ваша очередь")) }
            ForEach(myTurn) { d in
                DrawingCard(state: state, drawing: d, action: t("Рисовать")) { drawingId = d.id }
            }
            if !waiting.isEmpty { TogetherCaption(text: t("Рисуют другие")) }
            ForEach(waiting) { d in
                DrawingCard(state: state, drawing: d, action: nil) {}
            }
            if !done.isEmpty { TogetherCaption(text: t("Готовые")) }
            ForEach(done) { d in
                DrawingCard(state: state, drawing: d, action: t("В ленту"), onShare: {
                    // Карточка с самим рисунком и кто рисовал — с кодом приглашения
                    var seen = Set<String>()
                    let names = d.turns.map { personOf(state, $0.authorId).firstName }.filter { seen.insert($0).inserted }
                    let spec = ShareCardSpec(
                        kicker: t("Рисунок"), emoji: "🎨", title: d.title,
                        subtitle: tf("Нарисовали вместе: %s", names.joined(separator: ", ")),
                        picture: drawingImage(d.strokes),
                        author: "\(state.me.name) · @\(state.me.handle)"
                    )
                    sharing = shareCard(model, spec, name: "drawing-\(d.id)", action: .drawing, targetId: "\(d.id)", title: d.title)
                }) {
                    if let image = saveDrawing(d) {
                        model.publish(tf("Нарисовали вместе: «%s» ✦", d.title), media: [image])
                        geo.show(t("Опубликовано"))
                    }
                }
            }
        }
        .shareSheet($sharing)
        .sheet(isPresented: $creating, onDismiss: {
            if let id = pendingId {
                pendingId = nil
                drawingId = id
            }
        }) {
            NewDrawingSheet(model: model, onClose: { creating = false }) { id in
                pendingId = id
                creating = false
            }
        }
        .fullScreenCover(isPresented: Binding(get: { drawingId != nil }, set: { if !$0 { drawingId = nil } })) {
            if let d = model.state.together.drawings.first(where: { $0.id == drawingId && $0.waitingFor == model.state.me.id }) {
                DrawTurnEditor(model: model, drawing: d) { drawingId = nil }
            } else {
                // Ход уже сделан — закрыть
                Color.clear.onAppear { drawingId = nil }
            }
        }
    }
}

private struct DrawingCard: View {
    let state: UiState
    let drawing: CollabDrawing
    let action: String?
    /// «Поделиться» карточкой с рисунком; nil — кнопки нет.
    var onShare: (() -> Void)? = nil
    let onAction: () -> Void

    /// Кто уже рисовал — по разу, в порядке ходов.
    private var authors: [Int] {
        var seen = Set<Int>()
        return drawing.turns.map(\.authorId).filter { seen.insert($0).inserted }
    }

    var body: some View {
        let d = drawing
        TogetherCard {
            HStack(spacing: 12) {
                DrawingPreview(strokes: d.strokes).frame(width: 96, height: 96)
                VStack(alignment: .leading, spacing: 4) {
                    Text(d.title).textStyle(.title)
                    Text(tf("Ходов: %s из %s", d.turns.count, d.maxTurns)).textStyle(.bodySmall, Palette.mist)
                    if let waiting = d.waitingFor, waiting != state.me.id {
                        Text(tf("Сейчас рисует: %s", personOf(state, waiting).name)).textStyle(.bodySmall, Palette.lilac)
                    }
                    HStack(spacing: -8) {
                        ForEach(authors, id: \.self) { id in
                            Avatar(user: personOf(state, id), size: 26)
                        }
                    }
                }
                Spacer(minLength: 0)
            }
            HStack(spacing: 10) {
                if let action {
                    GlowButton(title: action, action: onAction)
                }
                if let onShare {
                    GhostButton(title: t("Поделиться"), action: onShare)
                }
            }
        }
    }
}

private struct NewDrawingSheet: View {
    let model: AppModel
    let onClose: () -> Void
    let onStarted: (Int64) -> Void
    @State private var title = ""
    @State private var turns = 4

    var body: some View {
        TogetherSheet(title: t("Новый рисунок")) {
            TogetherField(placeholder: t("Тема: «Город, которого нет»"), text: $title, limit: 40)
            TogetherCaption(text: t("Сколько ходов"))
            HStack(spacing: 6) {
                ForEach([3, 4, 5, 6], id: \.self) { n in
                    Chip(text: "\(n)", active: turns == n) { turns = n }
                }
            }
            HStack(spacing: 10) {
                GhostButton(title: t("Отмена"), action: onClose)
                GlowButton(title: t("Начать"), enabled: !title.trimmingCharacters(in: .whitespaces).isEmpty) {
                    if let id = model.startDrawing(title: title, turns: turns) {
                        onStarted(id)
                    } else {
                        onClose()
                    }
                }
            }
        }
    }
}

/// Свой ход: видно, что нарисовали до вас; рисуете сверху и передаёте дальше.
private struct DrawTurnEditor: View {
    let model: AppModel
    let drawing: CollabDrawing
    let onClose: () -> Void
    @State private var strokes: [DrawStroke] = []
    @State private var current: [CGPoint] = []
    @State private var color: UInt32 = stickerColors[4]
    @State private var width: CGFloat = 0.03
    @State private var eraser = false
    @State private var passTo: Int?
    @State private var sharing: SharePayload?

    init(model: AppModel, drawing: CollabDrawing, onClose: @escaping () -> Void) {
        self.model = model
        self.drawing = drawing
        self.onClose = onClose
        _passTo = State(initialValue: togetherPeople(model.state).first?.id)
    }

    private var all: [DrawStroke] {
        var list = drawing.strokes + strokes
        if !current.isEmpty { list.append(DrawStroke(points: current, color: color, width: width, eraser: eraser)) }
        return list
    }

    var body: some View {
        let d = drawing
        let last = d.turns.count + 1 >= d.maxTurns
        let friends = togetherPeople(model.state)
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                RoundGlyphButton(icon: .close, action: onClose)
                VStack(alignment: .leading, spacing: 2) {
                    Text(d.title).textStyle(.title)
                    Text(tf("Ваш ход: %s из %s", d.turns.count + 1, d.maxTurns)).textStyle(.bodySmall, Palette.mist)
                }
                Spacer()
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)

            canvas
                .padding(.horizontal, 16)

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 8) {
                        Chip(text: t("Кисть"), active: !eraser) { eraser = false }
                        Chip(text: t("Ластик"), active: eraser) { eraser = true }
                        Spacer()
                        RoundGlyphButton(icon: .undo, color: strokes.isEmpty ? Palette.mist : Palette.lilac) {
                            if !strokes.isEmpty { strokes.removeLast() }
                        }
                        RoundGlyphButton(icon: .trash, color: strokes.isEmpty ? Palette.mist : Palette.lilac) { strokes = [] }
                    }
                    TogetherCaption(text: t("Цвет"))
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 10) {
                            ForEach(stickerColors, id: \.self) { c in
                                Button {
                                    color = c
                                    eraser = false
                                } label: {
                                    Circle()
                                        .fill(RGB(c).color)
                                        .frame(width: 34, height: 34)
                                        .overlay(Circle().stroke(c == color && !eraser ? Palette.ink : Palette.line, lineWidth: c == color && !eraser ? 3 : 1))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    TogetherCaption(text: t("Толщина"))
                    Slider(value: $width, in: 0.008...0.09).tint(Palette.lilac)
                    if !last {
                        TogetherCaption(text: t("Кому передать"))
                        FlowLayout(spacing: 8) {
                            ForEach(friends) { p in
                                Chip(text: p.firstName, active: passTo == p.id) { passTo = p.id }
                            }
                        }
                        GhostButton(title: t("Позвать того, кого нет в Memory")) {
                            sharing = shareInvite(model, .drawing, targetId: "\(d.id)", title: d.title)
                        }
                    } else {
                        TogetherHint(text: t("Это последний ход — после него рисунок готов."))
                    }
                    GlowButton(title: last ? t("Закончить рисунок") : t("Передать дальше"),
                               enabled: !strokes.isEmpty && (last || passTo != nil)) {
                        model.drawTurn(d.id, strokes: strokes, passTo: last ? nil : passTo)
                        onClose()
                    }
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .background(Palette.void.ignoresSafeArea())
        .shareSheet($sharing)
    }

    /// Холст: прежние ходы и ваши линии поверх — та же картинка, что уйдёт в ленту.
    private var canvas: some View {
        GeometryReader { geo in
            let side = geo.size.width
            Image(uiImage: drawingImage(all))
                .resizable()
                .interpolation(.high)
                .frame(width: side, height: side)
                .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(Palette.line, lineWidth: 1))
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { value in
                            current.append(CGPoint(x: value.location.x / side, y: value.location.y / side))
                        }
                        .onEnded { _ in
                            if !current.isEmpty {
                                strokes.append(DrawStroke(points: current, color: color, width: width, eraser: eraser))
                                current = []
                            }
                        }
                )
        }
        .aspectRatio(1, contentMode: .fit)
    }
}

// MARK: - 8. Обмен без денег

struct SharingSection: View {
    let model: AppModel
    @State private var kind: ShareKind = .book
    @State private var adding = false
    @State private var handing: ShareItem?

    private var hint: String {
        switch kind {
        case .book: return t("Книжный клуб с обменом: прочитали — передайте другому. Видно, у кого книга сейчас и где она уже побывала.")
        case .seeds: return t("Семена, черенки, излишки урожая — садоводы делятся друг с другом.")
        case .repair: return t("Вещи, которые можно починить, и люди, готовые помочь. Вторая жизнь вместо мусорки.")
        }
    }

    private var addTitle: String {
        switch kind {
        case .book: return t("Поделиться книгой")
        case .seeds: return t("Поделиться семенами")
        case .repair: return t("Нужна помощь с ремонтом")
        }
    }

    var body: some View {
        let state = model.state
        // Хранитель книг: ваши книги — первыми в обмене
        let keeper = state.unlocked(.keeper)
        let items = state.together.shares.filter { $0.kind == kind && !state.blocked.contains($0.ownerId) }
            .togetherSorted(descending: true) { keeper && $0.ownerId == state.me.id ? 1 : 0 }
        VStack(alignment: .leading, spacing: 12) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(ShareKind.allCases) { k in
                        Chip(text: "\(k.emoji) \(k.title)", active: kind == k) { kind = k }
                    }
                }
            }
            TogetherCard {
                TogetherHint(text: hint)
                GlowButton(title: addTitle) { adding = true }
            }
            if kind == .book, let k = state.unlocks.first(where: { $0.unlock == .keeper }) {
                TogetherHint(text: k.open
                             ? "📚 " + t("Вы хранитель книг — ваши книги стоят первыми")
                             : "📚 " + k.unlock.how + " · " + tf("осталось %s", k.left))
            }
            ForEach(items) { item in
                ShareItemCard(model: model, item: item) { handing = item }
            }
        }
        .sheet(isPresented: $adding) {
            AddShareSheet(model: model, kind: kind) { adding = false }
        }
        .sheet(item: $handing) { item in
            HandOverSheet(model: model, item: item) { handing = nil }
        }
    }
}

/// Кому передать вещь: знакомым — сразу, тому, кого нет в Memory, — приглашением.
private struct HandOverSheet: View {
    let model: AppModel
    let item: ShareItem
    let onClose: () -> Void
    @State private var sharing: SharePayload?

    var body: some View {
        TogetherSheet(title: tf("Кому передать «%s»?", item.title)) {
            ForEach(togetherPeople(model.state)) { p in
                PersonLine(user: p, subtitle: "") {
                    model.handOver(item.id, to: p.id)
                    onClose()
                }
            }
            GhostButton(title: t("Тому, кого нет в Memory")) {
                sharing = shareInvite(model, .book, targetId: "\(item.id)", title: item.title)
            }
            GhostButton(title: t("Отмена"), action: onClose)
        }
        .shareSheet($sharing)
    }
}

private struct AddShareSheet: View {
    let model: AppModel
    let kind: ShareKind
    let onClose: () -> Void
    @State private var title = ""
    @State private var description = ""
    @State private var photo: [Attachment] = []

    private var placeholder: String {
        switch kind {
        case .book: return t("Книга и автор")
        case .seeds: return t("Что отдаёте: «Семена базилика»")
        case .repair: return t("Что сломалось: «Торшер»")
        }
    }

    var body: some View {
        TogetherSheet(title: "\(kind.emoji) \(kind.title)") {
            TogetherField(placeholder: placeholder, text: $title, limit: 80)
            TogetherField(placeholder: t("Пара слов"), text: $description, limit: 500, singleLine: false)
            PendingAttachments(items: photo) { item in
                photo = []
                MediaFiles.delete(item)
            }
            AttachButton(documents: false, size: 40) { picked in
                photo = Array(picked.filter { $0.kind == .photo }.prefix(1))
                for extra in picked where !photo.contains(extra) { MediaFiles.delete(extra) }
            }
            HStack(spacing: 10) {
                GhostButton(title: t("Отмена"), action: onClose)
                GlowButton(title: t("Опубликовать"), enabled: !title.trimmingCharacters(in: .whitespaces).isEmpty) {
                    if model.addShare(kind: kind, title: title, description: description, photo: photo.first) { onClose() }
                }
            }
        }
    }
}

private struct ShareItemCard: View {
    let model: AppModel
    let item: ShareItem
    let onHand: () -> Void

    var body: some View {
        let state = model.state
        let me = state.me.id
        let owner = personOf(state, item.ownerId)
        let requester = item.requestedBy.map { personOf(state, $0) }
        TogetherCard {
            HStack(spacing: 12) {
                if let photo = item.photo {
                    MediaTile(attachment: photo, maxPixel: 256) { model.openMedia(photo) }
                        .frame(width: 64, height: 64)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                } else {
                    EmojiBadge(emoji: item.kind.emoji, size: 56)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.title).textStyle(.title)
                    Text(tf("Делится: %s", owner.name)).textStyle(.bodySmall, Palette.mist)
                }
                Spacer(minLength: 0)
            }
            if !item.description.isEmpty {
                Text(item.description).textStyle(.bodySmall).fixedSize(horizontal: false, vertical: true)
            }
            switch item.kind {
            case .book:
                Text(tf("Сейчас у: %s", personOf(state, item.holderId).name)).textStyle(.labelLarge, Palette.lilac)
                if !item.history.isEmpty {
                    Text(tf("Побывала у: %s", item.history.map { personOf(state, $0).firstName }.joined(separator: " → ")))
                        .textStyle(.bodySmall, Palette.mist)
                }
            case .repair:
                if !item.helpers.isEmpty {
                    Text(tf("Готовы помочь: %s", item.helpers.map { personOf(state, $0).firstName }.joined(separator: ", ")))
                        .textStyle(.bodySmall, Palette.lilac)
                }
            case .seeds:
                EmptyView()
            }
            HStack(spacing: 10) {
                actionButton(me: me, requester: requester)
                if item.holderId != me {
                    GhostButton(title: t("Написать")) { model.writeTo(item.holderId) }
                }
            }
        }
    }

    @ViewBuilder
    private func actionButton(me: Int, requester: User?) -> some View {
        if item.kind == .repair && item.status == .fixed {
            Text(t("Починили ✓")).textStyle(.title, Palette.sky)
        } else if item.kind == .repair && item.ownerId == me {
            GlowButton(title: t("Починили")) { model.markFixed(item.id) }
        } else if item.kind == .repair {
            if item.helpers.contains(me) {
                GhostButton(title: t("Вы помогаете ✓")) {}
            } else {
                GlowButton(title: t("Помогу починить")) { model.offerHelp(item.id) }
            }
        } else if item.status == .given {
            Text(item.holderId == me ? t("Теперь у вас ✓") : t("Уже забрали")).textStyle(.title, Palette.sky)
        } else if item.holderId == me, let requester {
            GlowButton(title: tf("Отдать: %s", requester.firstName)) { model.handOver(item.id, to: requester.id) }
        } else if item.holderId == me {
            GlowButton(title: item.kind == .book ? t("Передать дальше") : t("Отдать"), action: onHand)
        } else if item.status == .requested && item.requestedBy == me {
            GhostButton(title: t("Вы попросили — ждём")) {}
        } else if item.status == .requested {
            GhostButton(title: t("Уже попросили")) {}
        } else {
            GlowButton(title: item.kind == .book ? t("Взять почитать") : t("Забрать")) { model.requestShare(item.id) }
        }
    }
}
