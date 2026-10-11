import SwiftUI
import UIKit

/// Обсуждение записи или PRISM — шторка снизу: комментарии и поле ввода.
/// total — сколько всего комментариев (у демо-записей видна только часть).
/// Долгое нажатие на комментарий — меню: ответить, скопировать,
/// «нравится», страница автора, пожаловаться (на чужой) или удалить (свой).
struct CommentsSheet: View {
    let total: Int
    let comments: [Comment]
    let myId: Int
    let onSend: (String, String?) -> Void
    let onDelete: (Int64) -> Void
    let onReport: (Int64) -> Void
    let onLike: (Int64) -> Void
    let onAuthor: (Int) -> Void

    @State private var text = ""
    /// Кому отвечаем: имя автора комментария; nil — просто комментарий.
    @State private var replyTo: String?
    @State private var menu: Comment?
    @State private var notice: String?
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
                            Text(t("Пока никто не написал. Будьте первым."))
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

            if let notice {
                Text(notice)
                    .textStyle(.labelLarge, Palette.lilac)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .glass(Capsule())
                    .padding(.bottom, 6)
                    .transition(.opacity)
            }
            FadeLine()
            if let replyTo {
                HStack(spacing: 8) {
                    Glyph(kind: .reply, color: Palette.lilac, size: 16)
                    Text(tf("Ответ для %s", replyTo)).textStyle(.labelLarge, Palette.lilac)
                    Spacer()
                    Button { self.replyTo = nil } label: {
                        Glyph(kind: .close, color: Palette.mist, size: 16).frame(width: 32, height: 32)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.leading, 20)
                .padding(.trailing, 8)
                .padding(.top, 6)
            }
            HStack(alignment: .bottom, spacing: 10) {
                GlassField(placeholder: t("Комментарий"), text: $text, limit: 1000, maxLines: 4)
                    .glass(RoundedRectangle(cornerRadius: 22, style: .continuous))
                    .focused($focused)
                Button {
                    onSend(text, replyTo)
                    text = ""
                    replyTo = nil
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
        .glassActions(item: $menu, title: { $0.author.name }, message: { String($0.text.prefix(80)) }) { c in
            actions(c)
        }
    }

    private func actions(_ c: Comment) -> [GlassAction] {
        var list = [
            GlassAction(icon: .reply, title: t("Ответить")) {
                replyTo = c.author.name
                focused = true
            },
            GlassAction(icon: .copy, title: t("Скопировать")) {
                UIPasteboard.general.string = c.text
                show(t("Скопировано"))
            },
            GlassAction(icon: .profile, title: t("Страница автора")) { onAuthor(c.author.id) },
        ]
        if c.author.id == myId {
            list.append(GlassAction(icon: .trash, title: t("Удалить"), destructive: true) { onDelete(c.id) })
        } else {
            list.append(GlassAction(icon: .flag, title: t("Пожаловаться"), destructive: true) {
                onReport(c.id)
                show(t("Жалоба отправлена. Комментарий скрыт."))
            })
        }
        return list
    }

    private func show(_ text: String) {
        withAnimation { notice = text }
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            withAnimation { if notice == text { notice = nil } }
        }
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
                if let to = c.replyTo, !to.isEmpty {
                    Text(tf("ответ для %s", to)).textStyle(.label, Palette.lilac)
                }
                Text(c.text).textStyle(.body).fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
            .onLongPressGesture(minimumDuration: 0.4) {
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                menu = c
            }
            .accessibilityAction(named: t("Действия")) { menu = c }
            // «Нравится» комментарию: сердце и число под ним
            Button { onLike(c.id) } label: {
                VStack(spacing: 0) {
                    LikeHeart(liked: c.liked, color: c.liked ? Palette.lilac : Palette.mist, size: 18)
                    if c.likes > 0 {
                        Text("\(c.likes)").textStyle(.label, c.liked ? Palette.lilac : Palette.mist)
                    }
                }
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .sensoryFeedback(.selection, trigger: c.liked)
        }
    }
}
