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
                        title: t("Группы"),
                        subtitle: tf("Вы в %s", plural(state.myGroups.count, "группе", "группах", "группах"))
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

                GlassField(placeholder: t("Поиск групп"), text: $query, limit: 40, singleLine: true)
                    .glass(Capsule())
                    .padding(.horizontal, 16)

                TopicPicker(selected: topic, allLabel: t("Все темы")) { topic = $0 }
                    .padding(.horizontal, 16)

                if found.isEmpty {
                    Text(t("Ничего не нашли. Создайте свою группу — кнопкой «+» вверху."))
                        .textStyle(.bodySmall, Palette.mist)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 12)
                }
                if !mine.isEmpty {
                    SectionTitle(text: t("Мои группы"))
                    ForEach(mine) { g in
                        GroupRow(group: g, onOpen: { model.openGroup(g.id) }, onToggle: { model.toggleGroup(g.id) })
                    }
                }
                if !others.isEmpty {
                    SectionTitle(text: needle.isEmpty && topic == nil ? t("Рекомендуем") : t("Найдено"))
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
                        GroupCover(group: group, maxPixel: 240)
                            .frame(width: 56, height: 56)
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                        VStack(alignment: .leading, spacing: 2) {
                            HStack(spacing: 6) {
                                Text(group.name).textStyle(.title).lineLimit(1)
                                if group.muted { Glyph(kind: .bellOff, color: Palette.mist, size: 16) }
                            }
                            Text("\(t(group.topic)) · \(plural(group.members, "участник", "участника", "участников"))" + (group.shop.isEmpty ? "" : " · " + t("Витрина")))
                                .textStyle(.bodySmall, Palette.mist)
                                .lineLimit(1)
                        }
                        Spacer(minLength: 0)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                if group.mine {
                    Text(t("ВАША")).textStyle(.label, Palette.lilac)
                } else if group.joined {
                    GhostButton(title: t("Вы в группе"), action: onToggle)
                } else {
                    GlowButton(title: t("Вступить"), action: onToggle)
                }
            }
            .padding(12)
        }
        .cardPadding()
    }
}

/// Тема группы: таблетка с выбранной темой («Все темы ▾»), по нажатию под
/// ней раскрываются все темы метками. Выбор метки сворачивает список.
/// allLabel != nil — первая метка «все темы» (для поиска), её выбор даёт nil.
struct TopicPicker: View {
    let selected: String?
    var allLabel: String? = nil
    let onSelect: (String?) -> Void
    @State private var open = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            DisclosurePill(
                text: selected.map { t($0) } ?? allLabel ?? t("Выберите тему"),
                open: open,
                active: selected != nil
            ) {
                withAnimation(.easeOut(duration: 0.2)) { open.toggle() }
            }
            if open {
                // Метки раскрываются по одной, с небольшой задержкой
                let shift = allLabel == nil ? 0 : 1
                FlowLayout(spacing: 8) {
                    if let allLabel {
                        Chip(text: allLabel, active: selected == nil) { pick(nil) }
                            .staggerIn(0)
                    }
                    ForEach(Array(groupTopics.enumerated()), id: \.element) { i, topic in
                        Chip(text: t(topic), active: topic == selected) { pick(topic) }
                            .staggerIn(i + shift)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func pick(_ topic: String?) {
        onSelect(topic)
        withAnimation(.easeOut(duration: 0.2)) { open = false }
    }
}

/// Форма новой группы: название, описание, тема.
private struct CreateGroupCard: View {
    let model: AppModel
    let onClose: () -> Void
    @State private var name = ""
    @State private var description = ""
    @State private var topic = groupTopics.last ?? t("Другое")
    @State private var error: String?

    var body: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 10) {
                Text(t("НОВАЯ ГРУППА")).textStyle(.label, Palette.mist)
                GlassField(placeholder: t("Название"), text: $name, limit: 40, singleLine: true)
                    .glass(radius: 16)
                    .onChange(of: name) { _, _ in error = nil }
                GlassField(placeholder: t("О чём группа"), text: $description, limit: 300, maxLines: 4)
                    .glass(radius: 16)
                Text(t("Тема")).textStyle(.bodySmall, Palette.mist)
                TopicPicker(selected: topic) { picked in
                    if let picked { topic = picked }
                }
                if let error {
                    Text(error).textStyle(.bodySmall, Palette.error)
                }
                HStack(spacing: 10) {
                    GhostButton(title: t("Отмена"), action: onClose)
                    GlowButton(title: t("Создать"), enabled: !name.trimmingCharacters(in: .whitespaces).isEmpty) {
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
