import SwiftUI

/// Вкладка «Чаты»: поиск и список переписок.
struct ChatsScreen: View {
    let model: AppModel
    @State private var query = ""

    var body: some View {
        let state = model.state
        let needle = query.trimmingCharacters(in: .whitespaces).lowercased()
        let chats = state.chats.filter {
            needle.isEmpty || $0.peer.name.lowercased().contains(needle) || $0.peer.handle.contains(needle)
        }

        ScrollView {
            LazyVStack(spacing: 0) {
                ScreenTitle(
                    title: "Чаты",
                    subtitle: state.unreadTotal > 0 ? "Непрочитанных: \(state.unreadTotal)" : "Все прочитано"
                )
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 20)
                .padding(.vertical, 8)

                GlassField(placeholder: "Поиск по имени", text: $query, limit: 40, singleLine: true)
                    .glass(Capsule())
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)

                if chats.isEmpty {
                    Text("Никого не нашли")
                        .textStyle(.bodySmall, Palette.mist)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(24)
                }

                ForEach(chats) { chat in
                    ChatRow(chat: chat) { model.openChat(chat.id) }
                    FadeLine().padding(.horizontal, 24)
                }
            }
            .padding(.top, 12)
            .padding(.bottom, tabBarSpace)
        }
        .scrollDismissesKeyboard(.interactively)
    }
}

private struct ChatRow: View {
    let chat: Chat
    let onOpen: () -> Void

    private var preview: String {
        if chat.typing { return "печатает…" }
        guard let last = chat.last else { return "Нет сообщений" }
        return last.fromMe ? "Вы: \(previewOf(last))" : previewOf(last)
    }

    var body: some View {
        Button(action: onOpen) {
            HStack(spacing: 14) {
                Avatar(user: chat.peer, size: 50, showOnline: true)
                VStack(alignment: .leading, spacing: 3) {
                    HStack {
                        Text(chat.peer.name).textStyle(.title).lineLimit(1)
                        Spacer()
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
                                .background(Capsule().fill(LinearGradient(colors: [Palette.orchid, Palette.sky], startPoint: .leading, endPoint: .trailing)))
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
    if a.allSatisfy({ $0.kind == .photo }) { return a.count == 1 ? "Фото" : "\(a.count) фото" }
    if a.allSatisfy({ $0.kind == .video }) { return a.count == 1 ? "Видео" : "\(a.count) видео" }
    if a.allSatisfy({ $0.kind == .document }) {
        if a.count == 1, !a[0].name.isEmpty { return a[0].name }
        return plural(a.count, "файл", "файла", "файлов")
    }
    return plural(a.count, "вложение", "вложения", "вложений")
}
