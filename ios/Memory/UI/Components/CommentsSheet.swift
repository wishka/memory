import SwiftUI

/// Обсуждение записи или PRISM — шторка снизу: комментарии и поле ввода.
/// total — сколько всего комментариев (у демо-записей видна только часть).
/// Свой комментарий удаляется значком корзины рядом с ним.
struct CommentsSheet: View {
    let total: Int
    let comments: [Comment]
    let myId: Int
    let onSend: (String) -> Void
    let onDelete: (Int64) -> Void
    let onAuthor: (Int) -> Void

    @State private var text = ""
    @FocusState private var focused: Bool

    private var canSend: Bool { !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

    var body: some View {
        VStack(spacing: 0) {
            Text(plural(total, "комментарий", "комментария", "комментариев").uppercased())
                .textStyle(.label, Palette.mist)
                .padding(.top, 18)
                .padding(.bottom, 6)

            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 14) {
                        if comments.isEmpty {
                            Text("Пока никто не написал. Будьте первым.")
                                .textStyle(.bodySmall, Palette.mist)
                                .padding(.vertical, 20)
                        }
                        ForEach(comments) { c in
                            row(c).id(c.id)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                }
                .onChange(of: comments.count) { _, _ in
                    if let last = comments.last {
                        withAnimation { proxy.scrollTo(last.id, anchor: .bottom) }
                    }
                }
            }

            FadeLine()
            HStack(alignment: .bottom, spacing: 10) {
                GlassField(placeholder: "Комментарий", text: $text, limit: 1000, maxLines: 4)
                    .glass(RoundedRectangle(cornerRadius: 22, style: .continuous))
                    .focused($focused)
                Button {
                    onSend(text)
                    text = ""
                } label: {
                    Glyph(kind: .send, color: canSend ? Palette.ink : Palette.mist)
                        .frame(width: 46, height: 46)
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
        .background(Palette.night.ignoresSafeArea())
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .presentationBackground(Palette.night)
    }

    private func row(_ c: Comment) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Button { onAuthor(c.author.id) } label: {
                Avatar(user: c.author, size: 34)
            }
            .buttonStyle(.plain)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 8) {
                    Text(c.author.name).textStyle(.title).lineLimit(1)
                    Text(agoTitle(minutesSince(c.createdAt, c.minutesAgo))).textStyle(.bodySmall, Palette.mist)
                }
                Text(c.text).textStyle(.body).fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
            if c.author.id == myId {
                Button { onDelete(c.id) } label: {
                    Glyph(kind: .trash, color: Palette.mist, size: 18)
                        .frame(width: 36, height: 36)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Удалить комментарий")
            }
        }
    }
}
