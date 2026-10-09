import SwiftUI

/// Вкладка «Чаты»: поиск и список переписок.
struct ChatsScreen: View {
    let model: AppModel
    @State private var query = ""
    /// Что подтверждаем: очистку истории или удаление переписки.
    @State private var confirm: ChatConfirm?

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
                    ChatRow(chat: chat) { model.openChat(chat.id) }
                        // Долгое нажатие — меню переписки
                        .contextMenu { menu(chat, blocked: state.isBlocked(chat.peer.id)) }
                    FadeLine().padding(.horizontal, 24)
                }
            }
            .padding(.top, 12)
            .padding(.bottom, tabBarSpace)
        }
        .scrollDismissesKeyboard(.interactively)
        .confirmationDialog(confirmTitle, isPresented: confirmShown, titleVisibility: .visible) {
            if let confirm {
                Button(confirm.delete ? t("Удалить") : t("Очистить"), role: .destructive) {
                    if confirm.delete { model.deleteChat(confirm.chatId) } else { model.clearHistory(confirm.chatId) }
                }
            }
            Button(t("Отмена"), role: .cancel) {}
        } message: {
            Text(confirm?.delete == true
                 ? t("Переписка исчезнет из списка вместе со всеми сообщениями.")
                 : t("Сообщения удалятся, сама переписка останется в списке."))
        }
    }

    @ViewBuilder
    private func menu(_ chat: Chat, blocked: Bool) -> some View {
        Button { model.toggleChatOnTop(chat.id) } label: {
            Label(chat.onTop ? t("Открепить") : t("Закрепить"), systemImage: chat.onTop ? "pin.slash" : "pin")
        }
        Button { model.toggleChatMute(chat.id) } label: {
            Label(chat.muted ? t("Включить звук") : t("Без звука"), systemImage: chat.muted ? "bell" : "bell.slash")
        }
        if chat.unread > 0 {
            Button { model.markRead(chat.id) } label: {
                Label(t("Отметить прочитанным"), systemImage: "checkmark.message")
            }
        } else {
            Button { model.markUnread(chat.id) } label: {
                Label(t("Отметить непрочитанным"), systemImage: "message.badge")
            }
        }
        Button {
            if blocked { model.unblock(chat.peer.id) } else { model.block(chat.peer.id) }
        } label: {
            Label(blocked ? t("Разблокировать") : t("Заблокировать"), systemImage: "nosign")
        }
        Button { confirm = ChatConfirm(chatId: chat.id, peer: chat.peer.name, delete: false) } label: {
            Label(t("Очистить историю"), systemImage: "eraser")
        }
        Button(role: .destructive) { confirm = ChatConfirm(chatId: chat.id, peer: chat.peer.name, delete: true) } label: {
            Label(t("Удалить чат"), systemImage: "trash")
        }
    }

    private var confirmShown: Binding<Bool> {
        Binding(get: { confirm != nil }, set: { if !$0 { confirm = nil } })
    }

    private var confirmTitle: String {
        guard let confirm else { return "" }
        return confirm.delete ? tf("Удалить переписку с %s?", confirm.peer) : t("Очистить историю?")
    }
}

/// Подтверждение очистки или удаления переписки.
private struct ChatConfirm {
    let chatId: Int
    let peer: String
    let delete: Bool
}

private struct ChatRow: View {
    let chat: Chat
    let onOpen: () -> Void

    private var preview: String {
        if chat.typing { return t("печатает…") }
        guard let last = chat.last else { return t("Нет сообщений") }
        return last.fromMe ? tf("Вы: %s", previewOf(last)) : previewOf(last)
    }

    var body: some View {
        Button(action: onOpen) {
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
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// Текст сообщения для списка чатов; без текста — что приложено.
func previewOf(_ message: Message) -> String {
    if !message.text.isEmpty { return message.text }
    let a = message.attachments
    if a.isEmpty { return "" }
    if a.allSatisfy({ $0.kind == .photo }) { return a.count == 1 ? t("Фото") : tf("%s фото", a.count) }
    if a.allSatisfy({ $0.kind == .video }) { return a.count == 1 ? t("Видео") : tf("%s видео", a.count) }
    if a.allSatisfy({ $0.kind == .document }) {
        if a.count == 1, !a[0].name.isEmpty { return a[0].name }
        return plural(a.count, "файл", "файла", "файлов")
    }
    return plural(a.count, "вложение", "вложения", "вложений")
}
