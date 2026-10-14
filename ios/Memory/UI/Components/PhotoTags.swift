import SwiftUI
import UIKit

/// Сколько человек можно отметить на одном снимке. То же — MAX_TAGS в PhotoTags.kt.
let maxPhotoTags = 20

/// Где на экране лежит снимок, вписанный целиком (как .fit): рамка в box.
private func fitRect(_ box: CGSize, _ image: UIImage?) -> CGRect {
    guard let image, image.size.width > 0, image.size.height > 0 else { return CGRect(origin: .zero, size: box) }
    let scale = min(box.width / image.size.width, box.height / image.size.height)
    let w = image.size.width * scale
    let h = image.size.height * scale
    return CGRect(x: (box.width - w) / 2, y: (box.height - h) / 2, width: w, height: h)
}

/// Подпись-отметка на фото: имя в «пилюле» над точкой.
private struct TagLabel: View {
    let name: String
    var hint: String? = nil
    var onTap: (() -> Void)? = nil

    var body: some View {
        let pill = Text(name)
            .textStyle(.labelLarge, .white)
            .lineLimit(1)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(Capsule().fill(Color.black.opacity(0.66)))
        if let onTap {
            Button(action: onTap) { pill }
                .buttonStyle(.plain)
                .accessibilityLabel(name)
                .accessibilityHint(hint ?? "")
        } else {
            pill
        }
    }
}

/// Отметки поверх снимка, вписанного в рамку (для просмотра и редактора).
private struct TagsOverlay: View {
    let tags: [PhotoTag]
    let rect: CGRect
    var hint: String? = nil
    var onTag: ((PhotoTag) -> Void)? = nil

    var body: some View {
        ZStack(alignment: .topLeading) {
            Color.clear
            ForEach(tags, id: \.self) { tag in
                if let person = Demo.person(tag.userId) {
                    TagLabel(name: person.firstName, hint: hint, onTap: onTag == nil ? nil : { onTag?(tag) })
                        .fixedSize()
                        .position(x: rect.minX + rect.width * tag.x, y: rect.minY + rect.height * tag.y)
                }
            }
        }
    }
}

/// Куда коснулись на снимке — доли ширины и высоты.
private struct TagPoint: Identifiable {
    let id = UUID()
    let x: Double
    let y: Double
}

/// Отметить людей на фото: коснуться места на снимке и выбрать друга.
/// Нажатие на отметку — убрать её. Отмечать можно только друзей.
/// То же — PhotoTagEditor в PhotoTags.kt.
struct PhotoTagEditor: View {
    let attachment: Attachment
    let friends: [User]
    let onDone: ([PhotoTag]) -> Void
    let onDismiss: () -> Void

    @State private var tags: [PhotoTag]
    @State private var point: TagPoint?
    @State private var image: UIImage?

    init(attachment: Attachment, friends: [User], onDone: @escaping ([PhotoTag]) -> Void, onDismiss: @escaping () -> Void) {
        self.attachment = attachment
        self.friends = friends
        self.onDone = onDone
        self.onDismiss = onDismiss
        _tags = State(initialValue: attachment.tags)
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            GeometryReader { geo in
                let rect = fitRect(geo.size, image)
                ZStack(alignment: .topLeading) {
                    if let image {
                        Image(uiImage: image)
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(width: geo.size.width, height: geo.size.height)
                            .accessibilityHidden(true)
                    }
                    Color.clear
                        .contentShape(Rectangle())
                        .onTapGesture { at in
                            let fx = (at.x - rect.minX) / max(rect.width, 1)
                            let fy = (at.y - rect.minY) / max(rect.height, 1)
                            if (0...1).contains(fx) && (0...1).contains(fy) {
                                point = TagPoint(x: Double(fx), y: Double(fy))
                            }
                        }
                        .accessibilityLabel(t("Коснитесь человека на фото"))
                    TagsOverlay(tags: tags, rect: rect, hint: t("Нажмите на имя, чтобы убрать отметку")) { tag in
                        tags.removeAll { $0 == tag }
                    }
                }
            }
            VStack(spacing: 0) {
                HStack(spacing: 10) {
                    Button(action: onDismiss) {
                        Glyph(kind: .close, color: .white)
                            .frame(width: 44, height: 44)
                            .background(Circle().fill(Palette.dark.void.opacity(0.6)))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(t("Отмена"))
                    Text(friends.isEmpty ? t("Отмечать можно друзей — пока их нет") : t("Коснитесь человека на фото"))
                        .textStyle(.bodySmall, .white)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    GlowButton(title: t("Готово")) { onDone(tags) }
                }
                .padding(12)
                Spacer()
                if !tags.isEmpty {
                    Text(t("Нажмите на имя, чтобы убрать отметку"))
                        .textStyle(.label, Color.white.opacity(0.7))
                        .padding(16)
                }
            }
        }
        .task(id: attachment.id) {
            let item = attachment
            image = await Task.detached(priority: .userInitiated) {
                MediaFiles.thumbnail(item, maxPixel: 1440)
            }.value
        }
        .sheet(item: $point) { at in
            TagPersonChooser(choices: friends.filter { f in !tags.contains { $0.userId == f.id } }) { user in
                tags = Array((tags + [PhotoTag(userId: user.id, x: at.x, y: at.y)]).prefix(maxPhotoTags))
                point = nil
            } onCancel: {
                point = nil
            }
        }
    }
}

/// «Кто это?» — выбор друга для отметки.
private struct TagPersonChooser: View {
    let choices: [User]
    let onPick: (User) -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(t("Кто это?"))
                .textStyle(.headline)
                .accessibilityAddTraits(.isHeader)
            if choices.isEmpty {
                Text(t("Все друзья уже отмечены")).textStyle(.bodySmall, Palette.mist)
            } else {
                ScrollView {
                    VStack(spacing: 4) {
                        ForEach(choices) { u in
                            Button { onPick(u) } label: {
                                HStack(spacing: 10) {
                                    Avatar(user: u, size: 36)
                                    Text(u.name).textStyle(.body)
                                    Spacer(minLength: 0)
                                }
                                .padding(6)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            HStack {
                Spacer()
                GhostButton(title: t("Отмена"), action: onCancel)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.night.ignoresSafeArea())
        .presentationDetents([.medium, .large])
        .presentationBackground(Palette.night)
        .presentationCornerRadius(28)
    }
}

/// Отметки при просмотре фото: значок внизу показывает и прячет имена.
/// Нажатие на имя — страница человека. То же — PhotoTagsLayer в PhotoTags.kt.
struct PhotoTagsLayer: View {
    let attachment: Attachment
    var onPerson: ((Int) -> Void)? = nil
    @State private var shown = true
    @State private var image: UIImage?

    var body: some View {
        if !attachment.tags.isEmpty {
            ZStack(alignment: .bottomLeading) {
                GeometryReader { geo in
                    if shown {
                        TagsOverlay(
                            tags: attachment.tags, rect: fitRect(geo.size, image),
                            hint: nil,
                            onTag: onPerson == nil ? nil : { tag in onPerson?(tag.userId) }
                        )
                    }
                }
                .ignoresSafeArea()
                Button { shown.toggle() } label: {
                    HStack(spacing: 6) {
                        Glyph(kind: .personTag, color: .white, size: 18)
                        Text(tf("Отмечено: %s", attachment.tags.count)).textStyle(.labelLarge, .white)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Capsule().fill(Color.black.opacity(0.6)))
                }
                .buttonStyle(.plain)
                .accessibilityHint(shown ? t("Скрыть отметки") : t("Показать отметки"))
                .padding(16)
            }
            .task(id: attachment.id) {
                let item = attachment
                image = await Task.detached(priority: .userInitiated) {
                    MediaFiles.thumbnail(item, maxPixel: 512)
                }.value
            }
        }
    }
}

/// Значок «👤 N» на плитке вложения в редакторе записи.
struct TagCountBadge: View {
    let count: Int

    var body: some View {
        if count > 0 {
            HStack(spacing: 2) {
                Glyph(kind: .personTag, color: .white, size: 11)
                Text("\(count)").textStyle(.label, .white)
            }
            .padding(.horizontal, 5)
            .padding(.vertical, 2)
            .background(Capsule().fill(Color.black.opacity(0.66)))
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(tf("Отмечено: %s", count))
        }
    }
}
