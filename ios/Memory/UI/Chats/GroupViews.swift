import SwiftUI

/// Выбор друзей галочками: для новой группы и «Добавить участников».
private struct FriendPicker: View {
    let people: [User]
    let chosen: [Int]
    let onToggle: (Int) -> Void

    var body: some View {
        LazyVStack(spacing: 0) {
            if people.isEmpty {
                Text(t("Некого добавить — все друзья уже здесь."))
                    .textStyle(.bodySmall, Palette.mist)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16)
            }
            ForEach(people) { user in
                FriendPickerRow(user: user, on: chosen.contains(user.id)) { onToggle(user.id) }
            }
        }
    }
}

private struct FriendPickerRow: View {
    let user: User
    let on: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Avatar(user: user, size: 40)
                Text(user.name).textStyle(.body).lineLimit(1)
                Spacer(minLength: 8)
                if on { Glyph(kind: .check, color: Palette.lilac, size: 20) }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
        .accessibilityHint(on ? t("Убрать") : t("Выбрать"))
    }
}

/// Добавить или убрать номер из выбранных (порядок выбора сохраняется).
private func toggled(_ list: [Int], _ id: Int) -> [Int] {
    if list.contains(id) { return list.filter { $0 != id } }
    return list + [id]
}

/// Новая группа: название и друзья.
struct NewGroupSheet: View {
    let model: AppModel
    @State private var title = ""
    @State private var chosen: [Int] = []
    @Environment(\.dismiss) private var dismiss

    private var canCreate: Bool {
        !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !chosen.isEmpty
    }

    var body: some View {
        let state = model.state
        let people: [User] = state.friends.filter { !state.blocked.contains($0.id) }
        VStack(alignment: .leading, spacing: 12) {
            Text(t("Новая группа")).textStyle(.headline)
            GlassField(placeholder: t("Название группы"), text: $title, limit: ChatGroup.maxTitle, singleLine: true)
                .glass(Capsule())
            Text(tf("Выбрано: %s", chosen.count)).textStyle(.labelLarge, Palette.mist)
            ScrollView {
                FriendPicker(people: people, chosen: chosen) { id in chosen = toggled(chosen, id) }
            }
            HStack(spacing: 10) {
                GhostButton(title: t("Отмена")) { dismiss() }
                GlowButton(title: t("Создать"), enabled: canCreate) { create() }
            }
        }
        .padding(16)
        .background(Palette.night.ignoresSafeArea())
        .presentationDetents([.large])
        .presentationBackground(Palette.night)
        .presentationCornerRadius(28)
    }

    private func create() {
        guard let id = model.createGroup(title, chosen) else { return }
        dismiss()
        model.openChat(id)
    }
}

/// Участник, для которого открыто меню.
private struct MemberMenu: Identifiable {
    let user: User
    var id: Int { user.id }
}

/// О группе: название, участники и роли, добавить, выйти.
struct GroupInfoSheet: View {
    let model: AppModel
    let chatId: Int
    @State private var title = ""
    @State private var adding = false
    @State private var confirmLeave = false
    @State private var menu: MemberMenu?
    @Environment(\.dismiss) private var dismiss

    private var chat: Chat? { model.state.chats.first { $0.id == chatId } }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let g = chat?.group {
                content(g)
            } else {
                Spacer()
            }
        }
        .padding(16)
        .background(Palette.night.ignoresSafeArea())
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .presentationBackground(Palette.night)
        .presentationCornerRadius(28)
        .onAppear { title = chat?.group?.title ?? "" }
        .sheet(isPresented: $adding) {
            AddMembersSheet(model: model, chatId: chatId)
        }
        .glassActions(
            $confirmLeave,
            title: t("Выйти из группы?"),
            message: t("Переписка пропадёт из списка. Вернуть может админ группы."),
            cancel: true,
            actions: [GlassAction(icon: .exit, title: t("Выйти"), destructive: true) { leave() }]
        )
        .glassActions(item: $menu, title: { $0.user.name }) { m in
            memberActions(m.user)
        }
    }

    @ViewBuilder
    private func content(_ g: ChatGroup) -> some View {
        let me = model.state.me.id
        titleRow(g, admin: g.isAdmin(me))
        Text(tf("Участников: %s", g.memberIds.count)).textStyle(.labelLarge, Palette.mist)
        ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(g.memberIds, id: \.self) { uid in
                    if let user = person(uid) {
                        MemberRow(user: user, group: g, me: me) { open(user, group: g) }
                    }
                }
            }
        }
        HStack(spacing: 10) {
            if g.isAdmin(me) {
                GhostButton(title: t("Добавить участников")) { adding = true }
            }
            GhostButton(title: t("Выйти из группы")) { confirmLeave = true }
        }
    }

    @ViewBuilder
    private func titleRow(_ g: ChatGroup, admin: Bool) -> some View {
        if admin {
            let clean = title.trimmingCharacters(in: .whitespacesAndNewlines)
            HStack(spacing: 8) {
                GlassField(placeholder: t("Название группы"), text: $title, limit: ChatGroup.maxTitle, singleLine: true)
                    .glass(Capsule())
                if !clean.isEmpty && clean != g.title {
                    GlowButton(title: t("Сохранить")) { model.renameGroup(chatId, title) }
                }
            }
        } else {
            Text(g.title).textStyle(.headline)
        }
    }

    private func person(_ uid: Int) -> User? {
        let state = model.state
        if uid == state.me.id { return state.me }
        return Demo.person(uid)
    }

    /// Нажали на участника: админу — меню, остальным — страница человека.
    private func open(_ user: User, group g: ChatGroup) {
        let me = model.state.me.id
        if g.isAdmin(me) && user.id != me && user.id != g.ownerId {
            menu = MemberMenu(user: user)
        } else if user.id != me {
            openPage(user.id)
        }
    }

    private func openPage(_ uid: Int) {
        dismiss()
        model.openPerson(uid)
    }

    private func leave() {
        dismiss()
        model.leaveGroup(chatId)
    }

    private func memberActions(_ user: User) -> [GlassAction] {
        let uid = user.id
        var list = [GlassAction(icon: .profile, title: t("Открыть страницу")) { openPage(uid) }]
        if let g = chat?.group, g.ownerId == model.state.me.id {
            let isAdmin = g.adminIds.contains(uid)
            let label = isAdmin ? t("Снять админа") : t("Сделать админом")
            list.append(GlassAction(icon: .shield, title: label) { model.setGroupAdmin(chatId, uid, !isAdmin) })
        }
        list.append(GlassAction(icon: .trash, title: t("Убрать из группы"), destructive: true) {
            model.removeGroupMember(chatId, uid)
        })
        return list
    }
}

/// Участник группы: аватар, имя («Вы» — для себя) и роль.
private struct MemberRow: View {
    let user: User
    let group: ChatGroup
    let me: Int
    let action: () -> Void

    private var role: String {
        if user.id == group.ownerId { return t("создатель") }
        if group.adminIds.contains(user.id) { return t("админ") }
        return ""
    }

    private var canManage: Bool {
        group.isAdmin(me) && user.id != me && user.id != group.ownerId
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Avatar(user: user, size: 40)
                VStack(alignment: .leading, spacing: 2) {
                    Text(user.id == me ? t("Вы") : user.name).textStyle(.body).lineLimit(1)
                    if !role.isEmpty {
                        Text(role).textStyle(.label, Palette.lilac)
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityHint(canManage ? t("Действия с участником") : t("Открыть страницу"))
    }
}

/// «Добавить участников»: друзья, которых ещё нет в группе.
private struct AddMembersSheet: View {
    let model: AppModel
    let chatId: Int
    @State private var chosen: [Int] = []
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let state = model.state
        let members: [Int] = state.chats.first { $0.id == chatId }?.group?.memberIds ?? []
        let people: [User] = state.friends.filter { !members.contains($0.id) && !state.blocked.contains($0.id) }
        VStack(alignment: .leading, spacing: 12) {
            Text(t("Добавить участников")).textStyle(.headline)
            ScrollView {
                FriendPicker(people: people, chosen: chosen) { id in chosen = toggled(chosen, id) }
            }
            HStack(spacing: 10) {
                GhostButton(title: t("Отмена")) { dismiss() }
                GlowButton(title: t("Добавить"), enabled: !chosen.isEmpty) {
                    model.addGroupMembers(chatId, chosen)
                    dismiss()
                }
            }
        }
        .padding(16)
        .background(Palette.night.ignoresSafeArea())
        .presentationDetents([.medium, .large])
        .presentationBackground(Palette.night)
        .presentationCornerRadius(28)
    }
}
