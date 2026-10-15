import SwiftUI

/// Новая запись — в Ленте и в Историях: текст, фото и видео из галереи
/// или с камеры, свои стикеры, гифки, музыка, опрос и чек-ин («смотрю
/// фильм», «пробежал 5 км»). Треки, коллекции и недавние гифки — из модели.
/// Вложения видны плитками под полем, крестик убирает лишнее.
struct PostComposer: View {
    let model: AppModel
    var placeholder: String = t("Чем хочется поделиться?")
    var hintText: String = t("Видно в ленте")

    @State private var text = ""
    @State private var media: [Attachment] = []
    @State private var checkIn: CheckIn?
    @State private var choosingSticker = false
    @State private var choosingGif = false
    @State private var choosingMusic = false
    @State private var editingPoll = false
    @State private var poll: PollDraft?
    @State private var choosingCheckIn = false
    /// Сценарий быстрого чек-ина: сразу к заполнению; nil — выбор занятия.
    @State private var scenario: String?
    /// Где это было: подпись «📍 …» под шапкой записи и точка на карте.
    @State private var place: SharedPlace?
    @State private var choosingPlace = false
    /// Фото, на котором сейчас отмечают друзей.
    @State private var tagging: Attachment?

    private var canPublish: Bool {
        !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !media.isEmpty || checkIn != nil || poll != nil
    }

    private var hint: String {
        if !media.isEmpty { return plural(media.count, "вложение", "вложения", "вложений") }
        return text.isEmpty ? hintText : "\(text.count) / 2000"
    }

    var body: some View {
        GlassCard {
            VStack(spacing: 0) {
                if let checkIn {
                    ZStack(alignment: .topTrailing) {
                        CheckInBadge(checkIn: checkIn)
                        Button { self.checkIn = nil } label: {
                            Glyph(kind: .close, color: Palette.ink, size: 14).frame(width: 26, height: 26).contentShape(Circle())
                        }
                        .buttonStyle(.plain)
                        .padding(4)
                        .accessibilityLabel(t("Убрать чек-ин"))
                    }
                    .padding([.horizontal, .top], 12)
                }
                // Не «чистый лист»: сначала готовые сценарии — одно нажатие, и запись наполовину готова
                if checkIn == nil && text.isEmpty && media.isEmpty && poll == nil {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(quickScenarios.compactMap { activity($0) }) { a in
                                scenarioChip(a.emoji, t(Self.scenarioTitles[a.id] ?? a.title)) {
                                    scenario = a.id
                                    choosingCheckIn = true
                                }
                            }
                            scenarioChip("＋", t("Другое")) {
                                scenario = nil
                                choosingCheckIn = true
                            }
                        }
                        .padding(.horizontal, 12)
                        .padding(.top, 12)
                    }
                }
                GlassField(placeholder: checkIn != nil ? t("Пара слов к чек-ину") : media.isEmpty ? t("…или просто напишите") : placeholder,
                           text: $text, limit: 2000)
                // «@ве…» — подсказки, кого упомянуть
                MentionSuggestions(text: $text)
                    .padding(.horizontal, 12)
                if let poll {
                    PendingPoll(draft: poll) { self.poll = nil }
                        .padding(.horizontal, 12)
                        .padding(.bottom, 10)
                }
                if let place {
                    HStack(spacing: 6) {
                        Glyph(kind: .globe, color: Palette.lilac, size: 16)
                        Text(place.title.isEmpty ? t("Место на карте") : place.title)
                            .textStyle(.labelLarge)
                            .lineLimit(1)
                        Button { self.place = nil } label: {
                            Glyph(kind: .close, color: Palette.mist, size: 14)
                                .frame(width: 32, height: 32)
                                .contentShape(Circle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(t("Убрать место"))
                    }
                    .padding(.leading, 12)
                    .glass(Capsule())
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 12)
                    .padding(.bottom, 10)
                }
                PendingAttachments(items: media, onRemove: { item in
                    media.removeAll { $0.id == item.id }
                    deleteAttachmentFiles(item)
                }, onTag: { tagging = $0 })
                .padding(.bottom, media.isEmpty ? 0 : 10)
                if media.contains(where: { $0.kind == .photo && !$0.sticker && !$0.isGif }) && media.allSatisfy({ $0.tags.isEmpty }) {
                    Text(t("Нажмите на фото, чтобы отметить друзей"))
                        .textStyle(.label, Palette.mist)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 16)
                        .padding(.bottom, 8)
                }
                HStack(spacing: 8) {
                    AttachButton(
                        documents: false, size: 40,
                        onMusic: { choosingMusic = true },
                        onSticker: { choosingSticker = true },
                        onGif: { choosingGif = true },
                        onPoll: { editingPoll = true }
                    ) { picked in
                        media = Array((media + picked).prefix(AppModel.maxMedia))
                    }
                    // Чек-ин — отдельной кнопкой: им пользуются часто
                    Button { choosingCheckIn = true } label: {
                        Glyph(kind: .pin, color: Palette.lilac, size: 20)
                            .frame(width: 40, height: 40)
                            .glass(Circle(), strong: checkIn != nil)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(t("Чек-ин"))
                    // Место — подпись «📍 …» под шапкой записи и точка на карте
                    Button { choosingPlace = true } label: {
                        Glyph(kind: .globe, color: Palette.lilac, size: 20)
                            .frame(width: 40, height: 40)
                            .glass(Circle(), strong: place != nil)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(t("Добавить место"))
                    Text(hint).textStyle(.bodySmall, Palette.mist).lineLimit(1)
                    Spacer()
                    GlowButton(title: t("Поделиться"), enabled: canPublish) {
                        model.publish(text, media: media, checkIn: checkIn, poll: poll, place: place)
                        text = ""
                        media = []
                        checkIn = nil
                        poll = nil
                        place = nil
                    }
                }
                .padding(.horizontal, 12)
                .padding(.bottom, 12)
            }
        }
        .cardPadding()
        .sheet(isPresented: $choosingSticker) { picker(gifs: false) }
        .sheet(isPresented: $choosingGif) { picker(gifs: true) }
        .sheet(isPresented: $choosingMusic) {
            MusicChooser(tracks: model.state.tracks, onPicked: { picked in
                media = Array((media + picked).prefix(AppModel.maxMedia))
            }, onClose: { choosingMusic = false })
        }
        .sheet(isPresented: $editingPoll) {
            PollEditorSheet { poll = $0 }
        }
        .sheet(isPresented: $choosingCheckIn, onDismiss: { scenario = nil }) {
            CheckInPicker(model: model, start: scenario) { checkIn = $0 }
        }
        .sheet(isPresented: $choosingPlace) {
            PlacePickerSheet(current: place, onDone: { place = $0; choosingPlace = false }, onDismiss: { choosingPlace = false })
        }
        // Отметить друзей на фото — снимок во весь экран
        .fullScreenCover(item: $tagging) { item in
            PhotoTagEditor(attachment: item, friends: model.state.friends, onDone: { tags in
                media = media.map { a in
                    guard a.id == item.id else { return a }
                    var x = a
                    x.tags = tags
                    return x
                }
                tagging = nil
            }, onDismiss: { tagging = nil })
        }
    }

    /// Стикеры и гифки — один выбор; из «GIF» он открывается сразу на гифках.
    private func picker(gifs: Bool) -> some View {
        StickerPicker(
            stickers: model.state.stickers,
            onPick: { media = Array((media + [$0]).prefix(AppModel.maxMedia)) },
            onOpenStudio: { model.openProfileSection(.stickers) },
            packs: model.state.stickerPacks,
            recentGifs: model.state.recentGifs,
            gifs: true,
            startOnGifs: gifs
        )
    }

    /// Как сценарии звучат на кнопках: действие, а не занятие.
    private static let scenarioTitles: [String: String] = [
        "movie": "Посмотрел фильм",
        "run": "Пробежка",
        "book": "Книга",
        "place": "Я здесь",
    ]

    private func scenarioChip(_ emoji: String, _ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Text(emoji).font(.system(size: 18))
                Text(title).textStyle(.labelLarge)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 9)
            .glass(Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// Место для записи: название и, по желанию, точка «где я сейчас».
/// Без точки место — просто подпись; с точкой — открывается на карте.
/// То же — PlacePickerDialog в PostComposer.kt.
struct PlacePickerSheet: View {
    let current: SharedPlace?
    let onDone: (SharedPlace?) -> Void
    let onDismiss: () -> Void
    @State private var title: String
    @State private var at: LatLon?
    @State private var busy = false
    @State private var failed = false

    init(current: SharedPlace?, onDone: @escaping (SharedPlace?) -> Void, onDismiss: @escaping () -> Void) {
        self.current = current
        self.onDone = onDone
        self.onDismiss = onDismiss
        _title = State(initialValue: current?.title ?? "")
        _at = State(initialValue: current.flatMap { $0.lat != 0 || $0.lon != 0 ? $0.at : nil })
    }

    var body: some View {
        let ready = !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || at != nil
        VStack(alignment: .leading, spacing: 12) {
            Text(t("Где это было?"))
                .textStyle(.headline)
                .accessibilityAddTraits(.isHeader)
            GlassField(placeholder: t("Название: кафе, парк, город"), text: $title, limit: 80, singleLine: true)
                .glass(Capsule())
            Button(action: locate) {
                HStack(spacing: 8) {
                    if busy {
                        ProgressView().tint(Palette.lilac)
                    } else {
                        Glyph(kind: .pin, color: Palette.lilac, size: 18)
                    }
                    Text(busy ? t("Ищем, где вы…") : at != nil ? t("Точка на карте добавлена") : t("Отметить, где я сейчас"))
                        .textStyle(.bodySmall)
                    Spacer(minLength: 0)
                }
                .padding(12)
                .glass(radius: 14, strong: at != nil)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(busy)
            if failed {
                Text(t("Не удалось определить, где вы. Включите геолокацию.")).textStyle(.bodySmall, Palette.error)
            }
            Spacer(minLength: 0)
            HStack(spacing: 10) {
                GhostButton(title: current != nil ? t("Убрать") : t("Отмена")) {
                    if current != nil { onDone(nil) } else { onDismiss() }
                }
                Spacer()
                GlowButton(title: t("Готово"), enabled: ready) {
                    onDone(SharedPlace(lat: at?.lat ?? 0, lon: at?.lon ?? 0,
                                       title: title.trimmingCharacters(in: .whitespacesAndNewlines)))
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.night.ignoresSafeArea())
        .presentationDetents([.medium])
        .presentationBackground(Palette.night)
        .presentationCornerRadius(28)
    }

    /// Где я сейчас — через Locator, как геолокация в переписке.
    private func locate() {
        guard !busy else { return }
        busy = true
        failed = false
        Task { @MainActor in
            let here = await Locator.shared.current()
            busy = false
            if let here { at = here } else { failed = true }
        }
    }
}
