import SwiftUI

/// Вкладка «Группы»: поиск по названию, описанию и теме, фильтр по теме,
/// свои группы отдельно от остальных, создание новой группы.
struct GroupsScreen: View {
    let model: AppModel
    @State private var query = ""
    @State private var topic: String?
    @State private var creating = false

    var body: some View {
        let state = model.state
        let needle = query.trimmingCharacters(in: .whitespaces).lowercased()
        let found = state.groups.filter { g in
            (topic == nil || g.topic == topic) &&
                (needle.isEmpty || "\(g.name) \(g.description) \(g.topic)".lowercased().contains(needle))
        }
        let mine = found.filter { $0.joined }
        let others = found.filter { !$0.joined }

        ScrollView {
            LazyVStack(spacing: 12) {
                HStack {
                    ScreenTitle(
                        title: "Группы",
                        subtitle: "Вы в \(plural(state.myGroups.count, "группе", "группах", "группах"))"
                    )
                    Spacer()
                    if !creating {
                        RoundGlyphButton(icon: .plus, color: Palette.lilac, iconSize: 22) {
                            withAnimation(.easeOut(duration: 0.2)) { creating = true }
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 8)

                if creating {
                    CreateGroupCard(model: model) {
                        withAnimation(.easeOut(duration: 0.2)) { creating = false }
                    }
                }

                GlassField(placeholder: "Поиск групп", text: $query, limit: 40, singleLine: true)
                    .glass(Capsule())
                    .padding(.horizontal, 16)

                TopicDropdown(selected: topic, allLabel: "Все темы") { topic = $0 }
                    .padding(.horizontal, 16)

                if found.isEmpty {
                    Text("Ничего не нашли. Создайте свою группу — кнопкой «+» вверху.")
                        .textStyle(.bodySmall, Palette.mist)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 12)
                }
                if !mine.isEmpty {
                    SectionTitle(text: "Мои группы")
                    ForEach(mine) { g in
                        GroupRow(group: g, onOpen: { model.openGroup(g.id) }, onToggle: { model.toggleGroup(g.id) })
                    }
                }
                if !others.isEmpty {
                    SectionTitle(text: needle.isEmpty && topic == nil ? "Рекомендуем" : "Найдено")
                    ForEach(others) { g in
                        GroupRow(group: g, onOpen: { model.openGroup(g.id) }, onToggle: { model.toggleGroup(g.id) })
                    }
                }
            }
            .padding(.top, 12)
            .padding(.bottom, tabBarSpace)
        }
        .scrollDismissesKeyboard(.interactively)
    }
}

private struct GroupRow: View {
    let group: MemoryGroup
    let onOpen: () -> Void
    let onToggle: () -> Void

    var body: some View {
        GlassCard {
            HStack(spacing: 12) {
                Button(action: onOpen) {
                    HStack(spacing: 12) {
                        Nebula(seed: group.seed)
                            .frame(width: 56, height: 56)
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(group.name).textStyle(.title).lineLimit(1)
                            Text("\(group.topic) · \(plural(group.members, "участник", "участника", "участников"))")
                                .textStyle(.bodySmall, Palette.mist)
                                .lineLimit(1)
                        }
                        Spacer(minLength: 0)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                if group.mine {
                    Text("ВАША").textStyle(.label, Palette.lilac)
                } else if group.joined {
                    GhostButton(title: "Вы в группе", action: onToggle)
                } else {
                    GlowButton(title: "Вступить", action: onToggle)
                }
            }
            .padding(12)
        }
        .cardPadding()
    }
}

/// Тема группы — выпадающим списком: тем много, и рядом меток они
/// занимали бы пол-экрана. allLabel != nil — первый пункт «все темы»
/// (для поиска), выбор его даёт nil.
struct TopicDropdown: View {
    let selected: String?
    var allLabel: String? = nil
    let onSelect: (String?) -> Void

    var body: some View {
        Menu {
            if let allLabel {
                Button { onSelect(nil) } label: {
                    if selected == nil { Label(allLabel, systemImage: "checkmark") } else { Text(allLabel) }
                }
            }
            ForEach(groupTopics, id: \.self) { t in
                Button { onSelect(t) } label: {
                    if t == selected { Label(t, systemImage: "checkmark") } else { Text(t) }
                }
            }
        } label: {
            HStack {
                Text(selected ?? allLabel ?? "Выберите тему")
                    .textStyle(.body, selected == nil ? Palette.mist : Palette.ink)
                    .lineLimit(1)
                Spacer()
                Glyph(kind: .chevronDown, color: Palette.lilac, size: 20)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .glass(radius: 16)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// Форма новой группы: название, описание, тема.
private struct CreateGroupCard: View {
    let model: AppModel
    let onClose: () -> Void
    @State private var name = ""
    @State private var description = ""
    @State private var topic = groupTopics.last ?? "Другое"
    @State private var error: String?

    var body: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("НОВАЯ ГРУППА").textStyle(.label, Palette.mist)
                GlassField(placeholder: "Название", text: $name, limit: 40, singleLine: true)
                    .glass(radius: 16)
                    .onChange(of: name) { _, _ in error = nil }
                GlassField(placeholder: "О чём группа", text: $description, limit: 300, maxLines: 4)
                    .glass(radius: 16)
                Text("Тема").textStyle(.bodySmall, Palette.mist)
                TopicDropdown(selected: topic) { picked in
                    if let picked { topic = picked }
                }
                if let error {
                    Text(error).textStyle(.bodySmall, Palette.error)
                }
                HStack(spacing: 10) {
                    GhostButton(title: "Отмена", action: onClose)
                    GlowButton(title: "Создать", enabled: !name.trimmingCharacters(in: .whitespaces).isEmpty) {
                        error = model.createGroup(name: name, description: description, topic: topic)
                        if error == nil { onClose() }
                    }
                }
                .padding(.top, 2)
            }
            .padding(16)
        }
        .cardPadding()
    }
}
