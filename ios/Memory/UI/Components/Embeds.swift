import SwiftUI

/// Градиент текстовой истории по номеру фона. То же — storyBrush в StoryViews.kt.
func storyGradient(_ bg: Int) -> LinearGradient {
    let list = Stories.backgrounds
    let n = list.count
    let pair = list[((bg % n) + n) % n]
    return LinearGradient(
        colors: [RGB(pair.0 & 0xFFFFFF).color, RGB(pair.1 & 0xFFFFFF).color],
        startPoint: .topLeading, endPoint: .bottomTrailing
    )
}

/// Картинка из копии: своё фото, демо-туманность или градиент истории.
private struct RefPicture: View {
    let photo: String
    let seed: Int
    var bg: Int? = nil

    var body: some View {
        ZStack {
            if !photo.isEmpty {
                Palette.plum
                MediaThumbnail(attachment: Attachment(id: "ref-" + photo, kind: .photo, fileName: photo), maxPixel: 360, placeholder: false)
            } else if seed != 0 {
                Nebula(seed: seed)
            } else if let bg {
                Rectangle().fill(storyGradient(bg))
            } else {
                Palette.plum
            }
        }
        .clipped()
        .accessibilityHidden(true)
    }
}

/// Над ответом на историю — сама история маленькой картинкой и «Ответ на историю».
struct StoryRefRow: View {
    let ref: StoryRef
    let mine: Bool

    var body: some View {
        HStack(spacing: 8) {
            RefPicture(photo: ref.photo, seed: ref.seed, bg: ref.bg)
                .frame(width: 40, height: 64)
                .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(mine ? t("Ответ на историю") : t("Ответ на вашу историю"))
                    .textStyle(.label, Palette.mist)
                Text(ref.preview)
                    .textStyle(.bodySmall)
                    .lineLimit(2)
            }
            Spacer(minLength: 0)
        }
        .padding(4)
        .accessibilityElement(children: .combine)
    }
}

/// Запись внутри репоста или сообщения: автор, начало текста и картинка.
/// Нажатие — открыть оригинал. То же — PostRefCard в Embeds.kt.
struct PostRefCard: View {
    let ref: PostRef
    let onOpen: () -> Void

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
        Button(action: onOpen) {
            HStack(alignment: .top, spacing: 10) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(ref.authorName + (ref.createdAt != nil ? " · " + agoTitle(minutesSince(ref.createdAt, 0)) : ""))
                        .textStyle(.labelLarge, Palette.lilac)
                        .lineLimit(1)
                    if !ref.text.isEmpty {
                        Text(ref.text)
                            .textStyle(.bodySmall)
                            .lineLimit(4)
                            .multilineTextAlignment(.leading)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if !ref.photo.isEmpty || ref.seed != 0 {
                    RefPicture(photo: ref.photo, seed: ref.seed)
                        .frame(width: 64, height: 64)
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
            }
            .padding(10)
            .background(shape.fill(Palette.plumRGB.opacity(0.25)))
            .overlay(shape.stroke(Palette.line, lineWidth: 1))
            .contentShape(shape)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(tf("Запись %s: %s", ref.authorName, String(ref.text.prefix(80))))
        .accessibilityHint(t("Открыть запись"))
        .accessibilityAddTraits(.isButton)
    }
}

/// Строка звонка в переписке: «Видеозвонок · 3:12», «Пропущенный звонок».
/// Нажатие — перезвонить тем же видом звонка. То же — CallRow в Embeds.kt.
struct CallRow: View {
    let record: CallRecord
    let onCallBack: () -> Void

    var body: some View {
        let bad = record.missed || record.declined
        Button(action: onCallBack) {
            HStack(spacing: 10) {
                Glyph(kind: record.video ? .video : .phone, color: bad ? Palette.pearlPink : Palette.lilac, size: 18)
                    .frame(width: 32, height: 32)
                    .background(Circle().fill(bad ? Palette.current.pearlPink.opacity(0.2) : Palette.violet))
                VStack(alignment: .leading, spacing: 1) {
                    Text(Calls.title(record)).textStyle(.bodySmall, bad ? Palette.pearlPink : Palette.ink)
                    Text(record.outgoing ? t("Исходящий") : t("Входящий")).textStyle(.label, Palette.mist)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .glass(radius: 16)
            .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityHint(t("Перезвонить"))
        .accessibilityAddTraits(.isButton)
    }
}
