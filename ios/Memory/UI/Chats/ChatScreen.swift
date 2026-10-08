import QuickLook
import SwiftUI

/// Переписка: шапка с собеседником, сообщения и поле ввода над клавиатурой.
/// Скрепка — фото, видео (из галереи или с камеры) и документы. Долгое
/// нажатие на сообщение — удалить у себя или у обоих.
struct ChatScreen: View {
    let model: AppModel
    let chat: Chat
    @State private var text = ""
    @State private var pending: [Attachment] = []
    /// Документ, открытый в просмотре (QuickLook).
    @State private var preview: URL?

    private var canSend: Bool {
        !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !pending.isEmpty
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            FadeLine()
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 8) {
                        ForEach(chat.messages) { message in
                            Bubble(
                                message: message,
                                peerName: chat.peer.firstName,
                                onOpen: open,
                                onDelete: { forEveryone in
                                    model.deleteMessage(chat.id, message.id, forEveryone: forEveryone)
                                    // Копии вложений больше не нужны
                                    message.attachments.forEach(MediaFiles.delete)
                                }
                            )
                            .id(message.id)
                        }
                        if chat.typing {
                            TypingBubble().id("typing")
                        }
                        Color.clear.frame(height: 1).id("bottom")
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
                }
                .scrollDismissesKeyboard(.interactively)
                .defaultScrollAnchor(.bottom)
                .onChange(of: chat.messages.count) { _, _ in
                    withAnimation(.easeOut(duration: 0.25)) { proxy.scrollTo("bottom", anchor: .bottom) }
                }
                .onChange(of: chat.typing) { _, _ in
                    withAnimation(.easeOut(duration: 0.25)) { proxy.scrollTo("bottom", anchor: .bottom) }
                }
            }
            PendingAttachments(items: pending) { item in
                pending.removeAll { $0.id == item.id }
                MediaFiles.delete(item)
            }
            .padding(.top, pending.isEmpty ? 0 : 8)
            inputRow
        }
        .quickLookPreview($preview)
    }

    private func open(_ attachment: Attachment) {
        if attachment.kind == .document {
            preview = MediaFiles.url(attachment)
        } else {
            model.openMedia(attachment)
        }
    }

    private var header: some View {
        HStack(spacing: 12) {
            Button { model.closeChat() } label: {
                Glyph(kind: .back, color: Palette.ink).frame(width: 44, height: 44).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            Button { model.openPerson(chat.peer.id) } label: {
                HStack(spacing: 12) {
                    Avatar(user: chat.peer, size: 40, showOnline: true)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(chat.peer.name).textStyle(.title).lineLimit(1)
                        Text(chat.typing ? "печатает…" : chat.peer.online ? "в сети" : "@\(chat.peer.handle)")
                            .textStyle(.bodySmall, chat.typing || chat.peer.online ? Palette.lilac : Palette.mist)
                    }
                }
            }
            .buttonStyle(.plain)
            Spacer()
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 8)
    }

    private var inputRow: some View {
        HStack(alignment: .bottom, spacing: 10) {
            AttachButton(documents: true, size: 50) { picked in
                pending = Array((pending + picked).prefix(AppModel.maxMedia))
            }
            GlassField(placeholder: "Сообщение", text: $text, limit: 2000, maxLines: 5)
                .glass(RoundedRectangle(cornerRadius: 24, style: .continuous))
            Button {
                model.send(chat.id, text, attachments: pending)
                text = ""
                pending = []
            } label: {
                Glyph(kind: .send, color: canSend ? Palette.ink : Palette.mist)
                    .frame(width: 50, height: 50)
                    .background(
                        Circle().fill(
                            canSend
                                ? LinearGradient(colors: [Palette.orchid, Palette.ocean], startPoint: .topLeading, endPoint: .bottomTrailing)
                                : LinearGradient(colors: [Palette.plum, Palette.plum], startPoint: .top, endPoint: .bottom)
                        )
                    )
            }
            .buttonStyle(.plain)
            .disabled(!canSend)
        }
        .padding(12)
    }
}

/// Сообщение: текст и вложения. Долгое нажатие — меню удаления:
/// «у себя» (у собеседника останется) или «у меня и у <имя>».
private struct Bubble: View {
    let message: Message
    let peerName: String
    let onOpen: (Attachment) -> Void
    let onDelete: (Bool) -> Void

    var body: some View {
        let mine = message.fromMe
        let shape = UnevenRoundedRectangle(
            topLeadingRadius: 20,
            bottomLeadingRadius: mine ? 20 : 6,
            bottomTrailingRadius: mine ? 6 : 20,
            topTrailingRadius: 20,
            style: .continuous
        )
        let visual = message.attachments.filter { $0.kind != .document }
        let documents = message.attachments.filter { $0.kind == .document }
        HStack(spacing: 0) {
            if mine { Spacer(minLength: 0) }
            VStack(alignment: .trailing, spacing: 6) {
                if !visual.isEmpty {
                    MediaGrid(items: visual, onOpen: onOpen)
                }
                ForEach(documents) { doc in
                    DocumentRow(attachment: doc) { onOpen(doc) }
                        .frame(maxWidth: 260)
                }
                VStack(alignment: .trailing, spacing: 2) {
                    if !message.text.isEmpty {
                        Text(message.text)
                            .textStyle(.body)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Text(message.time).textStyle(.label, Palette.inkRGB.opacity(0.6))
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
            }
            .padding(6)
            .background {
                if mine {
                    shape.fill(LinearGradient(colors: [Palette.violet, Palette.ocean], startPoint: .topLeading, endPoint: .bottomTrailing))
                }
            }
            .modifier(GlassIf(enabled: !mine, shape: shape))
            .contentShape(.contextMenuPreview, shape)
            .contextMenu {
                Button(role: .destructive) { onDelete(false) } label: {
                    Label("Удалить у себя", systemImage: "trash")
                }
                Button(role: .destructive) { onDelete(true) } label: {
                    Label("Удалить у меня и у \(peerName)", systemImage: "trash.fill")
                }
            }
            // Пузырь не шире 290: длинное сообщение переносится
            .frame(maxWidth: 290, alignment: mine ? .trailing : .leading)
            if !mine { Spacer(minLength: 0) }
        }
    }
}

/// Фото и видео в сообщении: одно — крупно, несколько — сеткой по два.
private struct MediaGrid: View {
    let items: [Attachment]
    let onOpen: (Attachment) -> Void

    var body: some View {
        if items.count == 1 {
            MediaTile(attachment: items[0]) { onOpen(items[0]) }
                .frame(width: 260, height: 200)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        } else {
            LazyVGrid(columns: [GridItem(.fixed(128), spacing: 4), GridItem(.fixed(128), spacing: 4)], spacing: 4) {
                ForEach(items) { item in
                    MediaTile(attachment: item, maxPixel: 384) { onOpen(item) }
                        .frame(width: 128, height: 128)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
            }
        }
    }
}

/// Стекло только для чужих сообщений: свои залиты градиентом.
private struct GlassIf<S: Shape>: ViewModifier {
    let enabled: Bool
    let shape: S

    func body(content: Content) -> some View {
        if enabled {
            content.glass(shape)
        } else {
            content.clipShape(shape)
        }
    }
}

private struct TypingBubble: View {
    var body: some View {
        HStack {
            HStack(spacing: 5) {
                ForEach(0..<3, id: \.self) { i in
                    Circle()
                        .fill(Palette.lilacRGB.opacity(0.5 + Double(i) * 0.2))
                        .frame(width: 6, height: 6)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .glass(RoundedRectangle(cornerRadius: 20, style: .continuous))
            Spacer()
        }
    }
}
