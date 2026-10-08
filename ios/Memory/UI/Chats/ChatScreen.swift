import SwiftUI

/// Переписка: шапка с собеседником, сообщения и поле ввода над клавиатурой.
struct ChatScreen: View {
    let model: AppModel
    let chat: Chat
    @State private var text = ""

    var body: some View {
        VStack(spacing: 0) {
            header
            FadeLine()
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 8) {
                        ForEach(chat.messages) { message in
                            Bubble(message: message).id(message.id)
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
            inputRow
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

    private var canSend: Bool { !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

    private var inputRow: some View {
        HStack(alignment: .bottom, spacing: 10) {
            GlassField(placeholder: "Сообщение", text: $text, limit: 2000, maxLines: 5)
                .glass(RoundedRectangle(cornerRadius: 24, style: .continuous))
            Button {
                model.send(chat.id, text)
                text = ""
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

private struct Bubble: View {
    let message: Message

    var body: some View {
        let mine = message.fromMe
        let shape = UnevenRoundedRectangle(
            topLeadingRadius: 20,
            bottomLeadingRadius: mine ? 20 : 6,
            bottomTrailingRadius: mine ? 6 : 20,
            topTrailingRadius: 20,
            style: .continuous
        )
        HStack(spacing: 0) {
            if mine { Spacer(minLength: 0) }
            VStack(alignment: .trailing, spacing: 2) {
                Text(message.text)
                    .textStyle(.body)
                    .fixedSize(horizontal: false, vertical: true)
                Text(message.time).textStyle(.label, Palette.inkRGB.opacity(0.6))
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 9)
            .background {
                if mine {
                    shape.fill(LinearGradient(colors: [Palette.violet, Palette.ocean], startPoint: .topLeading, endPoint: .bottomTrailing))
                }
            }
            .modifier(GlassIf(enabled: !mine, shape: shape))
            // Пузырь не шире 290: длинное сообщение переносится
            .frame(maxWidth: 290, alignment: mine ? .trailing : .leading)
            if !mine { Spacer(minLength: 0) }
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
