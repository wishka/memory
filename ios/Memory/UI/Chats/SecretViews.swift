import SwiftUI

/// Подпись под именем собеседника: «🔒 шифрование · ⏱ 1 день». "" — ничего не включено.
/// То же — secretStatus в SecretViews.kt.
func secretStatus(_ chat: Chat) -> String {
    var parts: [String] = []
    if chat.secret { parts.append("🔒 " + t("шифрование")) }
    if chat.disappearMs > 0 { parts.append("⏱ " + Disappear.of(chat.disappearMs).title) }
    return parts.joined(separator: " · ")
}

/// Полоса над перепиской: шифрование включено, код ещё не сверен.
struct SecretBanner: View {
    let chat: Chat
    let onOpen: () -> Void

    var body: some View {
        if chat.secret {
            Button(action: onOpen) {
                HStack(spacing: 10) {
                    Glyph(kind: .lock, color: Palette.lilac, size: 18)
                    Text(chat.verified
                         ? t("Сквозное шифрование. Код безопасности сверен.")
                         : t("Сквозное шифрование: сообщения читаете только вы двое. Сверьте код безопасности."))
                        .textStyle(.bodySmall, chat.verified ? Palette.mist : Palette.ink)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .glass(radius: 16)
                .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
        }
    }
}

/// Код безопасности: 60 цифр из ключей обоих. Сверьте с собеседником
/// при встрече или по другому каналу — совпало, значит между вами никого.
struct SafetyNumberDialog: View {
    let chat: Chat
    let number: String
    let onVerify: (Bool) -> Void
    let onDismiss: () -> Void

    var body: some View {
        let groups = number.split(separator: " ").map(String.init)
        let rows = stride(from: 0, to: groups.count, by: 4).map { i in
            groups[i..<min(i + 4, groups.count)].joined(separator: "  ")
        }
        CenterDialog(onDismiss: onDismiss) {
            Text(t("Код безопасности"))
                .textStyle(.title)
                .accessibilityAddTraits(.isHeader)
            Text(tf("Сравните код с тем, что видит %s у себя. Совпадает — переписку никто не перехватывает.", chat.peer.firstName))
                .textStyle(.bodySmall, Palette.mist)
                .fixedSize(horizontal: false, vertical: true)
            VStack(alignment: .leading, spacing: 6) {
                ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                    Text(row)
                        .font(.system(size: 17, weight: .medium, design: .monospaced))
                        .foregroundStyle(Palette.ink)
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                }
            }
            .textSelection(.enabled)
            if chat.verified {
                Text("✓ " + t("Сверено")).textStyle(.bodySmall, Palette.lilac)
            }
            HStack(spacing: 10) {
                GhostButton(title: t("Закрыть"), action: onDismiss)
                Spacer()
                GlowButton(title: chat.verified ? t("Снять отметку") : t("Код совпадает")) {
                    onVerify(!chat.verified)
                    onDismiss()
                }
            }
        }
    }
}

/// Выбор срока исчезающих сообщений.
struct DisappearDialog: View {
    let current: Disappear
    let onPick: (Disappear) -> Void
    let onDismiss: () -> Void

    var body: some View {
        CenterDialog(onDismiss: onDismiss) {
            Text(t("Исчезающие сообщения"))
                .textStyle(.title)
                .accessibilityAddTraits(.isHeader)
            Text(t("Новые сообщения в этой переписке исчезнут у обоих через выбранное время. Уже отправленные не изменятся."))
                .textStyle(.bodySmall, Palette.mist)
                .fixedSize(horizontal: false, vertical: true)
            VStack(spacing: 0) {
                ForEach(Disappear.allCases) { d in
                    Button {
                        onPick(d)
                        onDismiss()
                    } label: {
                        HStack {
                            Text(d.title).textStyle(.body, d == current ? Palette.ink : Palette.mist)
                            Spacer()
                            if d == current { Glyph(kind: .check, color: Palette.lilac, size: 20) }
                        }
                        .padding(.horizontal, 8)
                        .padding(.vertical, 12)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(d == current ? .isSelected : [])
                }
            }
            HStack {
                Spacer()
                GhostButton(title: t("Закрыть"), action: onDismiss)
            }
        }
    }
}
