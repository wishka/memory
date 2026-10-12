import SwiftUI

/// Новая запись — в Ленте и в Историях: текст, фото и видео из галереи
/// или с камеры, свои стикеры и чек-ин («смотрю фильм», «пробежал 5 км»).
/// Вложения видны плитками под полем, крестик убирает лишнее.
struct PostComposer: View {
    let model: AppModel
    var placeholder: String = t("Чем хочется поделиться?")
    var hintText: String = t("Видят ваши подписчики")

    @State private var text = ""
    @State private var media: [Attachment] = []
    @State private var checkIn: CheckIn?
    @State private var choosingSticker = false
    @State private var choosingCheckIn = false

    private var canPublish: Bool {
        !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !media.isEmpty || checkIn != nil
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
                GlassField(placeholder: checkIn != nil ? t("Пара слов к чек-ину") : placeholder, text: $text, limit: 2000)
                PendingAttachments(items: media) { item in
                    media.removeAll { $0.id == item.id }
                    MediaFiles.delete(item)
                }
                .padding(.bottom, media.isEmpty ? 0 : 10)
                HStack(spacing: 8) {
                    AttachButton(documents: false, size: 40, onSticker: { choosingSticker = true }) { picked in
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
                        model.publish(text, media: media, checkIn: checkIn)
                        text = ""
                        media = []
                        checkIn = nil
                    }
                }
                .padding(.horizontal, 12)
                .padding(.bottom, 12)
            }
        }
        .cardPadding()
        .sheet(isPresented: $choosingSticker) {
            StickerPicker(stickers: model.state.stickers, onPick: { media = Array((media + [$0]).prefix(AppModel.maxMedia)) }) {
                model.openProfileSection(.stickers)
            }
        }
        .sheet(isPresented: $choosingCheckIn) {
            CheckInPicker(model: model) { checkIn = $0 }
        }
    }
}
