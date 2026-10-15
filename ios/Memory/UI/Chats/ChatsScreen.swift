import SwiftUI

/// Вкладка «Чаты»: поиск и список переписок.
struct ChatsScreen: View {
    let model: AppModel
    @State private var query = ""
    /// Что подтверждаем: очистку истории или удаление переписки.
    @State private var confirm: ChatConfirm?
    /// Переписка, для которой открыто меню (долгое нажатие).
    @State private var menuChat: Chat?
    /// Раздел: переписки, запросы на переписку или звонки.
    @State private var section: ChatsSection = .chats
    @State private var notice: String?
    @State private var newGroup = false

    /// Переписки раздела, отобранные поиском.
    private var shownChats: [Chat] {
        let state = model.state
        let needle = query.trimmingCharacters(in: .whitespaces).lowercased()
        let source: [Chat] = section == .requests ? state.requestChats : state.chatList
        return source.filter {
            needle.isEmpty || $0.peer.name.lowercased().contains(needle) || $0.peer.handle.contains(needle)
        }
    }

    var body: some View {
        let chats = shownChats

        ScrollView {
            LazyVStack(spacing: 0) {
                titleRow
                sectionChips

                if section == .calls {
                    CallLogList(model: model) { notice = $0 }
                } else {
                    GlassField(placeholder: t("Поиск по имени"), text: $query, limit: 40, singleLine: true)
                        .glass(Capsule())
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)

                    if section == .requests {
                        Text(t("Пишут не друзья. Пока вы не ответите, они не узнают, что вы прочитали. Скрытые слова и фильтр работают и здесь."))
                            .textStyle(.bodySmall, Palette.mist)
                            .fixedSize(horizontal: false, vertical: true)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 24)
                            .padding(.vertical, 4)
                    }

                    if chats.isEmpty {
                        Text(section == .requests ? t("Запросов нет") : t("Никого не нашли"))
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
            }
            .padding(.top, 12)
            .padding(.bottom, tabBarSpace)
        }
        .scrollDismissesKeyboard(.interactively)
        .toast($notice)
        .sheet(isPresented: $newGroup) { NewGroupSheet(model: model) }
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

    /// «Чаты», непрочитанные и кнопка «Новая группа».
    private var titleRow: some View {
        let unread = model.state.unreadTotal
        let subtitle = unread > 0 ? tf("Непрочитанных: %s", unread) : t("Все прочитано")
        return HStack(spacing: 8) {
            ScreenTitle(title: t("Чаты"), subtitle: subtitle)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 20)
                .padding(.vertical, 8)
            RoundGlyphButton(icon: .groups, color: Palette.lilac, label: t("Новая группа")) { newGroup = true }
                .padding(.trailing, 16)
        }
    }

    /// Переписки, запросы (если есть или выбраны) и звонки (с числом пропущенных).
    private var sectionChips: some View {
        let state = model.state
        let missed = state.callLog.filter { $0.missed && !$0.outgoing }.count
        let requests = state.requestChats.count
        let callsTitle = missed > 0 ? tf("Звонки · %s", missed) : t("Звонки")
        let requestsTitle = requests > 0 ? tf("Запросы · %s", requests) : t("Запросы")
        return HStack(spacing: 8) {
            Chip(text: t("Переписки"), active: section == .chats) { section = .chats }
            if requests > 0 || section == .requests {
                Chip(text: requestsTitle, active: section == .requests) { section = .requests }
            }
            Chip(text: callsTitle, active: section == .calls) { section = .calls }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 4)
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

/// Раздел вкладки «Чаты».
private enum ChatsSection {
    case chats, requests, calls
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
    if let product = message.product { return t("Товар") + ": " + product.title }
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
