import QuickLook
import SwiftUI

/// Переписка: шапка с собеседником, сообщения и поле ввода над клавиатурой.
/// Скрепка — фото, видео (из галереи или с камеры) и документы. Долгое
/// нажатие на сообщение — ответить, переслать, закрепить или удалить у
/// себя или у всех. Закреплённое сообщение — полоской под шапкой.
/// Пустое поле — вместо «отправить» микрофон: голосовое записывается, его
/// можно удалить или отправить. В скрепке — свои стикеры из «Студии»,
/// гифки, опрос и геолокация. За «⋯» в шапке — свой фон переписки.
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
    @State private var recorder = VoiceRecorder()
    @State private var choosingSticker = false
    @State private var choosingGif = false
    @State private var editingPoll = false
    @State private var headerMenu = false
    @State private var choosingBackdrop = false
    @State private var viewingPlace: SharedPlace?
    @State private var locating = false
    @State private var notice: String?
    @State private var micDenied = false
    @State private var tooShort = false

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
                                },
                                onVote: { poll, i in model.voteMessage(chat.id, poll.id, i) },
                                onClosePoll: { poll in model.closeMessagePoll(chat.id, poll.id) },
                                onPlace: { viewingPlace = $0 },
                                onReport: { model.reportMessage(chat.id, message) }
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
                deleteAttachmentFiles(item)
            }
            .padding(.top, pending.isEmpty ? 0 : 8)
            inputRow
        }
        // Фон переписки: свой у чата или общий из настроек
        .background { BackdropView(backdrop: chat.backdropOr(model.state.backdrop)) }
        .quickLookPreview($preview)
        .sheet(isPresented: $choosingSticker) { picker(gifs: false) }
        .sheet(isPresented: $choosingGif) { picker(gifs: true) }
        .sheet(isPresented: $editingPoll) {
            PollEditorSheet { draft in
                model.send(chat.id, "", replyTo: replyTo, poll: draft)
                replyTo = nil
            }
        }
        .sheet(isPresented: $choosingBackdrop) {
            BackdropSheet(model: model, chatId: chat.id)
        }
        .fullScreenCover(item: $viewingPlace) { place in
            PlaceViewer(place: place) { viewingPlace = nil }
        }
        .glassActions($headerMenu, actions: [
            GlassAction(icon: .image, title: t("Фон переписки")) { choosingBackdrop = true },
        ])
        .toast($notice)
        .glassActions($micDenied, title: t("Разрешите доступ к микрофону, чтобы записывать голосовые"),
                      actions: [GlassAction(icon: .check, title: t("Понятно")) {}])
        .glassActions($tooShort, title: t("Слишком короткое — держите запись хотя бы секунду"),
                      actions: [GlassAction(icon: .check, title: t("Понятно")) {}])
        .onDisappear { recorder.cancel() }
        .onAppear { recorder.onLimit = { sendVoice() } }
        // Переслать: выбор переписки — шторкой в стиле приложения
        .glassActions(item: $forwarding, title: { _ in t("Переслать") }, cancel: true) { message in
            otherChats.map { target in
                GlassAction(icon: .forward, title: target.peer.name) {
                    model.forward(chat.id, message.id, to: target.id)
                }
            }
        }
    }

    /// Стикеры и гифки — один выбор; из «GIF» он открывается сразу на гифках.
    private func picker(gifs: Bool) -> some View {
        StickerPicker(
            stickers: model.state.stickers,
            onPick: { sticker in
                model.send(chat.id, "", attachments: [sticker], replyTo: replyTo)
                replyTo = nil
            },
            onOpenStudio: {
                model.closeChat()
                model.openProfileSection(.stickers)
            },
            packs: model.state.stickerPacks,
            recentGifs: model.state.recentGifs,
            gifs: true,
            startOnGifs: gifs
        )
    }

    /// Где я сейчас — точкой на карте в переписку.
    private func shareLocation() {
        guard !locating else { return }
        locating = true
        Task { @MainActor in
            let here = await Locator.shared.current()
            locating = false
            if let here {
                model.sendPlace(chat.id, here)
                replyTo = nil
            } else {
                notice = t("Не удалось определить, где вы. Включите геолокацию.")
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
            Button { headerMenu = true } label: {
                Glyph(kind: .more, color: Palette.ink).frame(width: 44, height: 44).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(t("Фон переписки"))
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
        } else if recorder.recording {
            RecordingBar(recorder: recorder, onCancel: { recorder.cancel() }, onSend: sendVoice)
        } else {
            messageRow
        }
    }

    private func sendVoice() {
        if let voice = recorder.finish() {
            model.send(chat.id, "", attachments: [voice], replyTo: replyTo)
            replyTo = nil
        } else {
            tooShort = true
        }
    }

    private var messageRow: some View {
        HStack(alignment: .bottom, spacing: 10) {
            AttachButton(
                documents: true, size: 50,
                onSticker: { choosingSticker = true },
                onGif: { choosingGif = true },
                onPoll: { editingPoll = true },
                onLocation: shareLocation
            ) { picked in
                pending = Array((pending + picked).prefix(AppModel.maxMedia))
            }
            GlassField(placeholder: t("Сообщение"), text: $text, limit: 2000, maxLines: 5)
                .glass(RoundedRectangle(cornerRadius: 24, style: .continuous))
            // Есть что отправить — «отправить»; пусто — микрофон для голосового
            Button {
                if canSend {
                    model.send(chat.id, text, attachments: pending, replyTo: replyTo)
                    text = ""
                    pending = []
                    replyTo = nil
                } else {
                    Task { @MainActor in
                        if !(await recorder.start()) { micDenied = true }
                    }
                }
            } label: {
                Glyph(kind: canSend ? .send : .mic, color: Palette.ink)
                    .frame(width: 50, height: 50)
                    .background(
                        Circle().fill(
                            canSend
                                ? LinearGradient(colors: [Palette.orchid, Palette.ocean], startPoint: .topLeading, endPoint: .bottomTrailing)
                                : LinearGradient(colors: [Palette.violet, Palette.plum], startPoint: .top, endPoint: .bottom)
                        )
                    )
            }
            .buttonStyle(.plain)
            .accessibilityLabel(canSend ? t("Отправить") : t("Записать голосовое"))
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
    let onVote: (Poll, Int) -> Void
    let onClosePoll: (Poll) -> Void
    let onPlace: (SharedPlace) -> Void
    /// Пожаловаться на сообщение собеседника (на свои — нет).
    var onReport: () -> Void = {}
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
        let visual = message.attachments.filter { $0.kind != .document && !$0.sticker }
        let stickers = message.attachments.filter(\.sticker)
        let documents = message.attachments.filter { $0.kind == .document }
        // Только стикер — без пузыря, картинкой
        let stickerOnly = message.text.isEmpty && !message.attachments.isEmpty && stickers.count == message.attachments.count
            && message.replyTo == nil && message.forwardedFrom == nil && message.poll == nil && message.place == nil
        HStack(spacing: 0) {
            if mine { Spacer(minLength: 0) }
            if stickerOnly {
                VStack(alignment: mine ? .trailing : .leading, spacing: 2) {
                    ForEach(stickers) { StickerView(attachment: $0, size: 150) }
                    Text(message.time).textStyle(.label, Palette.mist)
                }
                .contentShape(Rectangle())
                .onLongPressGesture(minimumDuration: 0.4) { menu = true }
                .glassActions($menu, actions: menuActions)
            } else {
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
                ForEach(stickers) { StickerView(attachment: $0, size: 120) }
                ForEach(documents) { doc in
                    if doc.voice {
                        VoiceBubble(attachment: doc)
                    } else {
                        DocumentRow(attachment: doc) { onOpen(doc) }
                            .frame(maxWidth: 260)
                    }
                }
                if let poll = message.poll {
                    PollCard(poll: poll, onVote: { onVote(poll, $0) }, onClose: mine ? { onClosePoll(poll) } : nil)
                        .frame(width: 270)
                }
                if let place = message.place {
                    PlaceBubble(place: place) { onPlace(place) }
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
            .glassActions($menu, actions: menuActions)
            // Пузырь не шире 290: длинное сообщение переносится
            .frame(maxWidth: 290, alignment: mine ? .trailing : .leading)
            }
            if !mine { Spacer(minLength: 0) }
        }
    }

    private var menuActions: [GlassAction] {
        var list = [
            GlassAction(icon: .reply, title: t("Ответить"), action: onReply),
            GlassAction(icon: .forward, title: t("Переслать"), action: onForward),
            GlassAction(icon: .pushpin, title: pinned ? t("Открепить") : t("Закрепить"), action: onPin),
            GlassAction(icon: .trash, title: t("Удалить у себя"), destructive: true) { onDelete(false) },
            GlassAction(icon: .trash, title: t("Удалить у всех"), destructive: true) { onDelete(true) },
        ]
        if !message.fromMe {
            list.append(GlassAction(icon: .flag, title: t("Пожаловаться"), destructive: true, action: onReport))
        }
        return list
    }
}

/// «Фон переписки» из меню чата: свой фон или как во всех переписках.
private struct BackdropSheet: View {
    let model: AppModel
    let chatId: Int
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let chat = model.state.chats.first { $0.id == chatId }
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(t("Фон переписки")).textStyle(.headline)
                Spacer()
                GlowButton(title: t("Готово")) { dismiss() }
            }
            ScrollView {
                BackdropChooser(
                    selected: chat?.backdropOr(model.state.backdrop) ?? model.state.backdrop,
                    onPick: { model.setChatBackdrop(chatId, $0) },
                    onReset: chat?.backdrop != nil ? { model.setChatBackdrop(chatId, nil) } : nil
                )
                .padding(.bottom, 16)
            }
        }
        .padding(16)
        .background(Palette.night.ignoresSafeArea())
        .presentationDetents([.large])
        .presentationBackground(Palette.night)
        .presentationCornerRadius(28)
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
