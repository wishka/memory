import SwiftUI

/// Вкладка «Чаты»: поиск и список переписок.
struct ChatsScreen: View {
    let model: AppModel
    @State private var query = ""
    /// Что подтверждаем: очистку истории или удаление переписки.
    @State private var confirm: ChatConfirm?
    /// Переписка, для которой открыто меню (долгое нажатие).
    @State private var menuChat: Chat?

    var body: some View {
        let state = model.state
        let needle = query.trimmingCharacters(in: .whitespaces).lowercased()
        let chats = state.chatList.filter {
            needle.isEmpty || $0.peer.name.lowercased().contains(needle) || $0.peer.handle.contains(needle)
        }

        ScrollView {
            LazyVStack(spacing: 0) {
                ScreenTitle(
                    title: t("Чаты"),
                    subtitle: state.unreadTotal > 0 ? tf("Непрочитанных: %s", state.unreadTotal) : t("Все прочитано")
                )
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 20)
                .padding(.vertical, 8)

                GlassField(placeholder: t("Поиск по имени"), text: $query, limit: 40, singleLine: true)
                    .glass(Capsule())
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)

                if chats.isEmpty {
                    Text(t("Никого не нашли"))
                        .textStyle(.bodySmall, Palette.mist)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(24)
                }

                ForEach(chats) { chat in
                    // Долгое нажатие — меню переписки
                    ChatRow(chat: chat, onOpen: { model.openChat(chat.id) }, onMenu: { menuChat = chat })
                    FadeLine().padding(.horizontal, 24)
                }
            }
            .padding(.top, 12)
            .padding(.bottom, tabBarSpace)
        }
        .scrollDismissesKeyboard(.interactively)
        .glassActions(item: $menuChat, title: { $0.peer.name }) { chat in
            menu(chat, blocked: model.state.isBlocked(chat.peer.id))
        }
        .background(
            Color.clear.glassActions(
                item: $confirm,
                title: { $0.delete ? tf("Удалить переписку с %s?", $0.peer) : t("Очистить историю?") },
                message: {
                    $0.delete
                        ? t("Переписка исчезнет из списка вместе со всеми сообщениями.")
                        : t("Сообщения удалятся, сама переписка останется в списке.")
                },
                cancel: true
            ) { c in
                [GlassAction(icon: .trash, title: c.delete ? t("Удалить") : t("Очистить"), destructive: true) {
                    if c.delete { model.deleteChat(c.chatId) } else { model.clearHistory(c.chatId) }
                }]
            }
        )
    }

    private func menu(_ chat: Chat, blocked: Bool) -> [GlassAction] {
        var items = [
            GlassAction(icon: .pushpin, title: chat.onTop ? t("Открепить") : t("Закрепить")) { model.toggleChatOnTop(chat.id) },
            GlassAction(icon: chat.muted ? .bell : .bellOff, title: chat.muted ? t("Включить звук") : t("Без звука")) { model.toggleChatMute(chat.id) },
        ]
        if chat.unread > 0 {
            items.append(GlassAction(icon: .check, title: t("Отметить прочитанным")) { model.markRead(chat.id) })
        } else {
            items.append(GlassAction(icon: .chats, title: t("Отметить непрочитанным")) { model.markUnread(chat.id) })
        }
        items.append(GlassAction(icon: .block, title: blocked ? t("Разблокировать") : t("Заблокировать")) {
            if blocked { model.unblock(chat.peer.id) } else { model.block(chat.peer.id) }
        })
        items.append(GlassAction(icon: .trash, title: t("Очистить историю")) {
            confirm = ChatConfirm(chatId: chat.id, peer: chat.peer.name, delete: false)
        })
        items.append(GlassAction(icon: .trash, title: t("Удалить чат"), destructive: true) {
            confirm = ChatConfirm(chatId: chat.id, peer: chat.peer.name, delete: true)
        })
        return items
    }
}

/// Подтверждение очистки или удаления переписки.
private struct ChatConfirm: Identifiable {
    var id: Int { chatId }
    let chatId: Int
    let peer: String
    let delete: Bool
}

private struct ChatRow: View {
    let chat: Chat
    let onOpen: () -> Void
    let onMenu: () -> Void

    private var preview: String {
        if chat.typing { return t("печатает…") }
        guard let last = chat.last else { return t("Нет сообщений") }
        return last.fromMe ? tf("Вы: %s", previewOf(last)) : previewOf(last)
    }

    var body: some View {
        content
            .contentShape(Rectangle())
            // Нажатие — переписка, долгое нажатие — меню
            .onTapGesture(perform: onOpen)
            .onLongPressGesture(minimumDuration: 0.4) {
                pressCount += 1
                onMenu()
            }
            .sensoryFeedback(.impact(weight: .medium), trigger: pressCount)
    }

    @State private var pressCount = 0

    private var content: some View {
            HStack(spacing: 14) {
                Avatar(user: chat.peer, size: 50, showOnline: true)
                VStack(alignment: .leading, spacing: 3) {
                    HStack {
                        Text(chat.peer.name).textStyle(.title).lineLimit(1)
                        Spacer()
                        if chat.muted { Glyph(kind: .bellOff, color: Palette.mist, size: 16) }
                        if chat.onTop { Glyph(kind: .pushpin, color: Palette.lilac, size: 16) }
                        Text(chat.last?.time ?? "").textStyle(.bodySmall, Palette.mist)
                    }
                    HStack {
                        Text(preview)
                            .textStyle(.bodySmall, chat.typing ? Palette.lilac : Palette.mist)
                            .lineLimit(1)
                        Spacer()
                        if chat.unread > 0 {
                            Text("\(chat.unread)")
                                .textStyle(.labelLarge)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 2)
                                // Без звука — счётчик приглушённый
                                .background(Capsule().fill(chat.muted
                                    ? LinearGradient(colors: [Palette.plum, Palette.plum], startPoint: .leading, endPoint: .trailing)
                                    : LinearGradient(colors: [Palette.orchid, Palette.sky], startPoint: .leading, endPoint: .trailing)))
                        }
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
    }
}

/// Текст сообщения для списка чатов; без текста — что приложено.
func previewOf(_ message: Message) -> String {
    if !message.text.isEmpty { return message.text }
    if let poll = message.poll { return t("Опрос") + ": " + poll.question }
    if let place = message.place { return t("Геолокация") + (place.title.isEmpty ? "" : ": " + place.title) }
    let a = message.attachments
    if a.isEmpty { return "" }
    if a.allSatisfy(\.isGif) { return "GIF" }
    if a.count == 1, a[0].voice { return t("Голосовое сообщение") + " · " + durationTitle(a[0].durationMs ?? 0) }
    if a.allSatisfy(\.sticker) { return t("Стикер") }
    if a.allSatisfy({ $0.kind == .photo }) { return a.count == 1 ? t("Фото") : tf("%s фото", a.count) }
    if a.allSatisfy({ $0.kind == .video }) { return a.count == 1 ? t("Видео") : tf("%s видео", a.count) }
    if a.allSatisfy({ $0.kind == .document }) {
        if a.count == 1, !a[0].name.isEmpty { return a[0].name }
        return plural(a.count, "файл", "файла", "файлов")
    }
    return plural(a.count, "вложение", "вложения", "вложений")
}
