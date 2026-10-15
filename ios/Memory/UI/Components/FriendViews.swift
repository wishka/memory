import SwiftUI

/// Кнопка дружбы: «Добавить в друзья», «Заявка отправлена» (нажатие —
/// отозвать), «Принять заявку» или «В друзьях» (нажатие — спросить
/// «Удалить из друзей?»). onToggle — AppModel.toggleFriend. Та же кнопка —
/// FriendViews.kt.
struct FriendButton: View {
    let state: FriendState
    let name: String
    let onToggle: () -> Void
    @State private var confirm = false

    var body: some View {
        Group {
            switch state {
            case .notFriends:
                GlowButton(title: t("Добавить в друзья"), action: onToggle)
            case .requested:
                GhostButton(title: t("Заявка отправлена"), action: onToggle)
            case .incoming:
                GlowButton(title: t("Принять заявку"), action: onToggle)
            case .friends:
                GhostButton(title: t("В друзьях")) { confirm = true }
            }
        }
        .removeFriendConfirm($confirm, name: name, onConfirm: onToggle)
    }
}

extension View {
    /// «Удалить из друзей?» — перед тем как дружба прекратится.
    func removeFriendConfirm(_ isPresented: Binding<Bool>, name: String, onConfirm: @escaping () -> Void) -> some View {
        glassActions(
            isPresented,
            title: t("Удалить из друзей?"),
            message: tf("%s исчезнет из ваших друзей. Снова стать друзьями можно только по новой заявке.", name),
            cancel: true,
            actions: [GlassAction(icon: .trash, title: t("Удалить"), destructive: true, action: onConfirm)]
        )
    }
}
