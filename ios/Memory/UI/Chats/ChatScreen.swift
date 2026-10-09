import QuickLook
import SwiftUI

/// Переписка: шапка с собеседником, сообщения и поле ввода над клавиатурой.
/// Скрепка — фото, видео (из галереи или с камеры) и документы. Долгое
/// нажатие на сообщение — ответить, переслать, закрепить или удалить у
/// себя или у всех. Закреплённое сообщение — полоской под шапкой.
struct ChatScreen: View {
    let model: AppModel
    let chat: Chat
    /// Куда можно переслать сообщение.
    let otherChats: [Chat]
    /// Собеседник в чёрном списке: вместо поля ввода — напоминание.
    var blocked: Bool = false
    @State private var text = ""
    @State private var pending: [Attachment] = []
    /// На какое сообщение отвечаем; nil — обычное сообщение.
    @State private var replyTo: ReplyRef?
    /// Какое сообщение пересылаем: выбор переписки.
    @State private var forwarding: Message?
    /// К какому сообщению прокрутить (нажатие на закреплённое).
    @State private var scrollTarget: Int64?
    /// Документ, открытый в просмотре (QuickLook).
    @State private var preview: URL?

    private var canSend: Bool {
        !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !pending.isEmpty
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            FadeLine()
            if let pin = chat.pinned {
                PinnedBar(
                    message: pin,
                    onOpen: { scrollTarget = pin.id },
                    onUnpin: { model.unpin(chat.id) }
                )
            }
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 8) {
                        ForEach(chat.messages) { message in
                            Bubble(
                                message: message,
                                peerName: chat.peer.firstName,
                                onReply: { replyTo = model.replyRef(chat.id, message) },
                                onForward: { forwarding = message },
                                pinned: chat.pinnedId == message.id,
                                onPin: { model.togglePin(chat.id, message.id) },
                                onQuote: { id in
                                    // Нажатие на цитату — к исходному сообщению
                                    withAnimation { proxy.scrollTo(id, anchor: .center) }
                                },
                                onOpen: open,
                                onDelete: { forEveryone in
                                    model.deleteMessage(chat.id, message.id, forEveryone: forEveryone)
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
                // Нажатие на закреплённое — к самому сообщению
                .onChange(of: scrollTarget) { _, target in
                    guard let target else { return }
                    withAnimation { proxy.scrollTo(target, anchor: .center) }
                    scrollTarget = nil
                }
            }
            if let ref = replyTo {
                ReplyBar(ref: ref) { replyTo = nil }
            }
            PendingAttachments(items: pending) { item in
                pending.removeAll { $0.id == item.id }
                MediaFiles.delete(item)
            }
            .padding(.top, pending.isEmpty ? 0 : 8)
            inputRow
        }
        .quickLookPreview($preview)
        // Переслать: выбор переписки — шторкой в стиле приложения
        .glassActions(item: $forwarding, title: { _ in t("Переслать") }, cancel: true) { message in
            otherChats.map { target in
                GlassAction(icon: .forward, title: target.peer.name) {
                    model.forward(chat.id, message.id, to: target.id)
                }
            }
        }
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
                        Text(chat.typing ? t("печатает…") : chat.peer.online ? t("в сети") : "@\(chat.peer.handle)")
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

    @ViewBuilder
    private var inputRow: some View {
        if blocked {
            // Заблокированному не пишем: напоминание и кнопка
            HStack(spacing: 10) {
                Text(tf("Вы заблокировали %s", chat.peer.name))
                    .textStyle(.bodySmall, Palette.mist)
                Spacer(minLength: 8)
                GhostButton(title: t("Разблокировать")) { model.unblock(chat.peer.id) }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .glass(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .padding(12)
        } else {
            messageRow
        }
    }

    private var messageRow: some View {
        HStack(alignment: .bottom, spacing: 10) {
            AttachButton(documents: true, size: 50) { picked in
                pending = Array((pending + picked).prefix(AppModel.maxMedia))
            }
            GlassField(placeholder: t("Сообщение"), text: $text, limit: 2000, maxLines: 5)
                .glass(RoundedRectangle(cornerRadius: 24, style: .continuous))
            Button {
                model.send(chat.id, text, attachments: pending, replyTo: replyTo)
                text = ""
                pending = []
                replyTo = nil
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

/// Сообщение: пометка «Переслано», цитата ответа, вложения и текст.
/// Долгое нажатие — меню: ответить, переслать, закрепить, удалить «у
/// себя» (у собеседника останется) или «у всех».
private struct Bubble: View {
    let message: Message
    let peerName: String
    let onReply: () -> Void
    let onForward: () -> Void
    let pinned: Bool
    let onPin: () -> Void
    let onQuote: (Int64) -> Void
    let onOpen: (Attachment) -> Void
    let onDelete: (Bool) -> Void
    @State private var menu = false

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
                if let from = message.forwardedFrom {
                    HStack(spacing: 6) {
                        Glyph(kind: .forward, color: Palette.lilac, size: 14)
                        Text(tf("Переслано от %s", from)).textStyle(.labelLarge, Palette.lilac).lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 8)
                    .padding(.top, 2)
                }
                if let ref = message.replyTo {
                    Button { onQuote(ref.messageId) } label: {
                        HStack(spacing: 8) {
                            LinearGradient(colors: [Palette.lilac, Palette.sky], startPoint: .top, endPoint: .bottom)
                                .frame(width: 3)
                            VStack(alignment: .leading, spacing: 1) {
                                Text(ref.author).textStyle(.labelLarge, Palette.lilac).lineLimit(1)
                                Text(ref.preview).textStyle(.bodySmall, Palette.inkRGB.opacity(0.75)).lineLimit(1)
                            }
                            Spacer(minLength: 0)
                        }
                        .frame(height: 40)
                        .padding(.trailing, 10)
                        .background(Palette.voidRGB.opacity(0.3))
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
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
            .contentShape(shape)
            // Долгое нажатие — меню сообщения шторкой
            .onLongPressGesture(minimumDuration: 0.4) { menu = true }
            .sensoryFeedback(.impact(weight: .medium), trigger: menu)
            .glassActions($menu, actions: [
                GlassAction(icon: .reply, title: t("Ответить"), action: onReply),
                GlassAction(icon: .forward, title: t("Переслать"), action: onForward),
                GlassAction(icon: .pushpin, title: pinned ? t("Открепить") : t("Закрепить"), action: onPin),
                GlassAction(icon: .trash, title: t("Удалить у себя"), destructive: true) { onDelete(false) },
                GlassAction(icon: .trash, title: t("Удалить у всех"), destructive: true) { onDelete(true) },
            ])
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

/// Полоска «Ответ …» над полем ввода; крестик — передумать.
private struct ReplyBar: View {
    let ref: ReplyRef
    let onCancel: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            Glyph(kind: .reply, color: Palette.lilac, size: 20)
            VStack(alignment: .leading, spacing: 1) {
                Text(tf("Ответ: %s", ref.author)).textStyle(.labelLarge, Palette.lilac).lineLimit(1)
                Text(ref.preview).textStyle(.bodySmall, Palette.mist).lineLimit(1)
            }
            Spacer(minLength: 0)
            Button(action: onCancel) {
                Glyph(kind: .close, color: Palette.mist, size: 18)
                    .frame(width: 36, height: 36)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(.leading, 12)
        .padding(.vertical, 6)
        .glass(radius: 16)
        .padding(.horizontal, 12)
        .padding(.top, 8)
    }
}

/// Закреплённое сообщение под шапкой: нажатие — к нему, крестик — открепить.
private struct PinnedBar: View {
    let message: Message
    let onOpen: () -> Void
    let onUnpin: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                Button(action: onOpen) {
                    HStack(spacing: 10) {
                        Glyph(kind: .pushpin, color: Palette.lilac, size: 20)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(t("Закреплённое сообщение")).textStyle(.labelLarge, Palette.lilac).lineLimit(1)
                            Text(previewOf(message)).textStyle(.bodySmall).lineLimit(1)
                        }
                        Spacer(minLength: 0)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                Button(action: onUnpin) {
                    Glyph(kind: .close, color: Palette.mist, size: 18)
                        .frame(width: 36, height: 36)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(t("Открепить"))
            }
            .padding(.leading, 16)
            .padding(.trailing, 6)
            .padding(.vertical, 6)
            FadeLine()
        }
    }
}
