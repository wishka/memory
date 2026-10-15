import SwiftUI

/// «📍 Кофейня у моря» под шапкой записи. Нажатие — карта (если у места есть точка).
/// То же — PlaceLine в RepostViews.kt.
struct PlaceLine: View {
    let place: SharedPlace
    let onOpen: () -> Void

    var body: some View {
        let row = HStack(spacing: 6) {
            Glyph(kind: .pin, color: Palette.lilac, size: 16)
            Text(place.title.isEmpty ? t("Место на карте") : place.title)
                .textStyle(.labelLarge, Palette.lilac)
                .lineLimit(1)
        }
        .padding(.vertical, 2)
        .contentShape(Capsule())
        if place.lat != 0 || place.lon != 0 {
            Button(action: onOpen) { row }
                .buttonStyle(.plain)
                .accessibilityHint(t("Показать на карте"))
        } else {
            row
        }
    }
}

/// «На фото: Вера, Марк» под снимками. Нажатие — список отмеченных.
/// То же — TaggedLine в RepostViews.kt.
struct TaggedLine: View {
    let userIds: [Int]
    let onOpen: () -> Void

    var body: some View {
        let names = userIds.compactMap { Demo.person($0)?.firstName }
        if !names.isEmpty {
            Button(action: onOpen) {
                HStack(spacing: 6) {
                    Glyph(kind: .personTag, color: Palette.mist, size: 16)
                    Text(names.count <= 2
                         ? tf("На фото: %s", names.joined(separator: ", "))
                         : tf("На фото: %s и ещё %s", names.prefix(2).joined(separator: ", "), names.count - 2))
                        .textStyle(.labelLarge, Palette.mist)
                        .lineLimit(1)
                }
                .padding(.vertical, 2)
                .contentShape(Capsule())
            }
            .buttonStyle(.plain)
            .accessibilityHint(t("Кто отмечен"))
        }
    }
}

/// Кто отмечен на фото — нажатие открывает страницу человека; себя можно
/// убрать. То же — TaggedDialog в RepostViews.kt.
struct TaggedSheet: View {
    let userIds: [Int]
    let onPerson: (Int) -> Void
    var meId: Int? = nil
    var onUntagMe: () -> Void = {}
    let onClose: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(t("Отмечены на фото"))
                .textStyle(.headline)
                .accessibilityAddTraits(.isHeader)
            ScrollView {
                VStack(spacing: 4) {
                    ForEach(userIds.compactMap { Demo.person($0) }) { u in
                        Button { onPerson(u.id) } label: {
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
            HStack(spacing: 10) {
                if let meId, userIds.contains(meId) {
                    Button(action: onUntagMe) {
                        Text(t("Убрать отметку с собой")).textStyle(.labelLarge, Palette.pearlPink)
                            .padding(.vertical, 10)
                    }
                    .buttonStyle(.plain)
                }
                Spacer()
                GhostButton(title: t("Закрыть"), action: onClose)
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

/// Репост: оригинал карточкой и свои слова над ним (по желанию).
/// Внизу — «В чат», если хочется не в ленту, а кому-то лично.
/// То же — RepostSheet в RepostViews.kt.
struct RepostSheet: View {
    let model: AppModel
    let post: Post
    @State private var text = ""
    @State private var error: String?

    var body: some View {
        let ref = model.postRef(post)
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                Text(t("Репост").uppercased())
                    .textStyle(.label, Palette.mist)
                    .padding(.horizontal, 20)
                    .accessibilityAddTraits(.isHeader)
                GlassField(placeholder: t("Добавьте пару слов — или оставьте пустым"), text: $text, limit: 2000, maxLines: 6)
                    .glass(radius: 18)
                    .padding(.horizontal, 16)
                MentionSuggestions(text: $text)
                    .padding(.horizontal, 16)
                PostRefCard(ref: ref, onOpen: {})
                    .padding(.horizontal, 16)
                    .allowsHitTesting(false)
                if let error {
                    Text(error)
                        .textStyle(.bodySmall, Palette.error)
                        .padding(.horizontal, 20)
                }
                HStack(spacing: 10) {
                    GhostButton(title: t("В чат")) { model.openSendToChat(post.id) }
                    Spacer()
                    GlowButton(title: t("Опубликовать")) { error = model.repost(post.id, text) }
                }
                .padding(.horizontal, 16)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 20)
            .padding(.bottom, 12)
        }
        .scrollDismissesKeyboard(.interactively)
        .onChange(of: text) { _, _ in error = nil }
        .background(Palette.night.ignoresSafeArea())
        .presentationDetents([.medium, .large])
        .presentationBackground(Palette.night)
        .presentationCornerRadius(28)
    }
}

/// Кому отправить запись: список переписок и подпись к ней.
/// То же — SendToChatSheet в RepostViews.kt.
struct SendToChatSheet: View {
    let model: AppModel
    let post: Post
    @State private var text = ""
    @State private var query = ""

    var body: some View {
        let state = model.state
        let needle = query.trimmingCharacters(in: .whitespaces).lowercased()
        let chats = state.chatList.filter {
            !state.isBlocked($0.peer.id) && (needle.isEmpty || $0.peer.name.lowercased().contains(needle))
        }
        VStack(alignment: .leading, spacing: 10) {
            Text(t("Отправить в чат").uppercased())
                .textStyle(.label, Palette.mist)
                .padding(.horizontal, 20)
                .accessibilityAddTraits(.isHeader)
            GlassField(placeholder: t("Поиск по имени"), text: $query, limit: 40, singleLine: true)
                .glass(Capsule())
                .padding(.horizontal, 16)
            GlassField(placeholder: t("Сообщение (необязательно)"), text: $text, limit: 1000, maxLines: 3)
                .glass(radius: 18)
                .padding(.horizontal, 16)
            ScrollView {
                LazyVStack(spacing: 0) {
                    if chats.isEmpty {
                        Text(t("Никого не нашли"))
                            .textStyle(.bodySmall, Palette.mist)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(20)
                    }
                    ForEach(chats) { chat in
                        Button { model.sendPostToChat(post.id, chat.id, text) } label: {
                            HStack(spacing: 12) {
                                Avatar(user: chat.peer, size: 40)
                                Text(chat.peer.name).textStyle(.title).lineLimit(1)
                                Spacer(minLength: 0)
                                Glyph(kind: .send, color: Palette.lilac, size: 20)
                            }
                            .padding(.horizontal, 20)
                            .padding(.vertical, 10)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(tf("Отправить: %s", chat.peer.name))
                    }
                }
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .padding(.top, 20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.night.ignoresSafeArea())
        .presentationDetents([.large])
        .presentationBackground(Palette.night)
        .presentationCornerRadius(28)
    }
}
