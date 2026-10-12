import SwiftUI

/// Новая запись — в Ленте и в Историях: текст, фото и видео из галереи
/// или с камеры, свои стикеры, гифки, музыка, опрос и чек-ин («смотрю
/// фильм», «пробежал 5 км»). Треки, коллекции и недавние гифки — из модели.
/// Вложения видны плитками под полем, крестик убирает лишнее.
struct PostComposer: View {
    let model: AppModel
    var placeholder: String = t("Чем хочется поделиться?")
    var hintText: String = t("Видят ваши подписчики")

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
                if let poll {
                    PendingPoll(draft: poll) { self.poll = nil }
                        .padding(.horizontal, 12)
                        .padding(.bottom, 10)
                }
                PendingAttachments(items: media) { item in
                    media.removeAll { $0.id == item.id }
                    deleteAttachmentFiles(item)
                }
                .padding(.bottom, media.isEmpty ? 0 : 10)
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
                    Text(hint).textStyle(.bodySmall, Palette.mist).lineLimit(1)
                    Spacer()
                    GlowButton(title: t("Поделиться"), enabled: canPublish) {
                        model.publish(text, media: media, checkIn: checkIn, poll: poll)
                        text = ""
                        media = []
                        checkIn = nil
                        poll = nil
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
