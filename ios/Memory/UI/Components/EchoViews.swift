import SwiftUI

/// Окно посреди экрана поверх затемнения — как Dialog на Android.
struct CenterDialog<Content: View>: View {
    let onDismiss: () -> Void
    @ViewBuilder let content: () -> Content

    var body: some View {
        ZStack {
            Color.black.opacity(0.55)
                .ignoresSafeArea()
                .onTapGesture(perform: onDismiss)
            ScrollView {
                VStack(alignment: .leading, spacing: 12) { content() }
                    .padding(18)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .scrollBounceBehavior(.basedOnSize)
            .fixedSize(horizontal: false, vertical: true)
            .background(RoundedRectangle(cornerRadius: 24, style: .continuous).fill(Palette.night))
            .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).stroke(Palette.line, lineWidth: 1))
            .padding(.horizontal, 24)
            .padding(.vertical, 60)
        }
    }
}

/// Отклик на чек-ин — не конфетти, а то, что изменилось: полка, вклад в
/// общую цель, план недели, что откроется дальше, совет по вкусу.
/// То же — в EchoViews.kt.
struct EchoDialog: View {
    let model: AppModel
    let echo: CheckInEcho
    @State private var sharing: SharePayload?

    var body: some View {
        let state = model.state
        let kind = shelfKind(echo.checkIn.activity)
        CenterDialog(onDismiss: { model.dismissEcho() }) {
            Text(t("Отмечено ✦")).textStyle(.title)
            CheckInBadge(checkIn: echo.checkIn)
            ForEach(Array(echo.lines.enumerated()), id: \.offset) { _, line in
                HStack(spacing: 10) {
                    Text(line.emoji).font(.system(size: 20))
                    Text(line.text).textStyle(.bodySmall).fixedSize(horizontal: false, vertical: true)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .glass(radius: 16)
            }
            FlowLayout(spacing: 8) {
                GlowButton(title: t("Готово")) { model.dismissEcho() }
                GhostButton(title: t("Поделиться")) {
                    let c = echo.checkIn
                    let spec = ShareCardSpec(
                        kicker: t("Чек-ин"), emoji: c.emoji, title: c.subject.isEmpty ? c.displayTitle : c.subject,
                        subtitle: c.displayTitle, lines: Array(echo.lines.prefix(4)),
                        author: "\(state.me.name) · @\(state.me.handle)"
                    )
                    sharing = shareCard(model, spec, name: "checkin", action: .app)
                }
                if kind == "film" || kind == "book" {
                    GhostButton(title: t("Полка")) {
                        model.dismissEcho()
                        model.openShelf(kind == "book" ? .books : .films)
                    }
                }
            }
        }
        .shareSheet($sharing)
    }
}

/// Открыли приложение по чужой ссылке: кто позвал и к чему.
struct InviteDialog: View {
    let model: AppModel
    let invite: Invite

    var body: some View {
        CenterDialog(onDismiss: { model.dismissInvite() }) {
            Text("✦").font(.system(size: 32)).foregroundStyle(Palette.lilac)
            Text(invite.from.isEmpty ? t("Вас позвали в Memory") : tf("%s зовёт вас", invite.from)).textStyle(.title)
            Text(capitalizedFirst(invite.action.title)).textStyle(.body, Palette.lilac)
            Text(invite.action == .app
                 ? t("Отметьте первый фильм, книгу или пробежку — с этого начинается ваша полка.")
                 : t("Мы открыли нужный раздел. Пока у Memory нет сервера, сам объект появится, когда друг будет рядом в приложении."))
                .textStyle(.bodySmall, Palette.mist)
                .fixedSize(horizontal: false, vertical: true)
            GlowButton(title: t("Понятно")) { model.dismissInvite() }
        }
    }
}

/// «дорисовать рисунок» → «Дорисовать рисунок».
func capitalizedFirst(_ s: String) -> String {
    guard let first = s.first else { return s }
    return String(first).uppercased() + s.dropFirst()
}
