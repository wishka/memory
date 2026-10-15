import SwiftUI

/// Уведомление, пока приложение открыто: стеклянная плашка сверху.
/// Нажатие — туда, о чём оно; сама уходит через пять секунд.
struct NudgeBanner: View {
    let nudge: Nudge
    let onOpen: () -> Void
    let onDismiss: () -> Void

    private var icon: LineIcon {
        switch nudge.kind {
        case .friends: return .pin
        case .groups: return .groups
        case .reactions: return .heart
        case .follows: return .profile
        case .messages: return .chats
        case .recap: return .award
        case .prism: return .prism
        }
    }

    var body: some View {
        HStack(spacing: 12) {
            Glyph(kind: icon, color: Palette.ink, size: 20)
                .frame(width: 40, height: 40)
                .background(Circle().fill(LinearGradient(colors: [Palette.orchid, Palette.ocean], startPoint: .topLeading, endPoint: .bottomTrailing)))
            VStack(alignment: .leading, spacing: 2) {
                Text(nudge.title).textStyle(.labelLarge, Palette.lilac).lineLimit(1)
                Text(nudge.text).textStyle(.bodySmall).lineLimit(3).fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
            Button(action: onDismiss) {
                Glyph(kind: .close, color: Palette.mist, size: 14).frame(width: 32, height: 32).contentShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(t("Закрыть"))
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(Palette.night.opacity(0.92)))
        .glass(RoundedRectangle(cornerRadius: 22, style: .continuous), strong: true)
        .padding(.horizontal, 12)
        .padding(.top, 8)
        .contentShape(Rectangle())
        .onTapGesture(perform: onOpen)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
        .task(id: nudge.id) {
            try? await Task.sleep(nanoseconds: 5_000_000_000)
            if !Task.isCancelled { onDismiss() }
        }
    }
}
