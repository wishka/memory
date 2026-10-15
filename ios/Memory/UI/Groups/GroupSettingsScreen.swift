import PhotosUI
import SwiftUI

/// Настройки группы за шестерёнкой. Создатель меняет заставку, название,
/// описание, тему, сведения и контакты, оформление (цвет и фон страницы),
/// кто может писать, и может удалить группу. Участник — уведомления и выход.
struct GroupSettingsScreen: View {
    let model: AppModel
    let group: MemoryGroup

    @State private var name: String
    @State private var description: String
    @State private var topic: String
    @State private var error: String?
    @State private var saved = false
    @State private var confirm = false
    @State private var coverItem: PhotosPickerItem?

    init(model: AppModel, group: MemoryGroup) {
        self.model = model
        self.group = group
        _name = State(initialValue: group.name)
        _description = State(initialValue: group.description)
        _topic = State(initialValue: group.topic)
    }

    private var changed: Bool { name != group.name || description != group.description || topic != group.topic }

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                HStack(spacing: 14) {
                    RoundGlyphButton(icon: .back) { model.closeGroupSettings() }
                    ScreenTitle(title: t("Настройки группы"), subtitle: group.name)
                    Spacer()
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 4)

                if group.mine {
                    card {
                        caption(t("Заставка"))
                        GroupCover(group: group)
                            .frame(height: 150)
                            .frame(maxWidth: .infinity)
                            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                            .padding(.horizontal, 16)
                        HStack(spacing: 10) {
                            PhotosPicker(selection: $coverItem, matching: .images) {
                                Text(t("Сменить заставку").uppercased())
                                    .textStyle(.label)
                                    .padding(.horizontal, 20)
                                    .padding(.vertical, 10)
                                    .background(Capsule().fill(LinearGradient(colors: [Palette.orchid, Palette.ocean], startPoint: .leading, endPoint: .trailing)))
                            }
                            if let cover = group.cover {
                                GhostButton(title: t("Туманность")) {
                                    MediaFiles.delete(cover)
                                    model.setGroupCover(group.id, nil)
                                }
                            }
                        }
                        .padding(16)
                    }
                    card {
                        caption(t("О группе"))
                        VStack(alignment: .leading, spacing: 10) {
                            GlassField(placeholder: t("Название"), text: $name, limit: 40, singleLine: true)
                                .glass(radius: 16)
                                .onChange(of: name) { _, _ in error = nil; saved = false }
                            GlassField(placeholder: t("О чём группа"), text: $description, limit: 500, maxLines: 6)
                                .glass(radius: 16)
                            Text(t("Тема")).textStyle(.bodySmall, Palette.mist)
                            TopicPicker(selected: topic) { picked in
                                if let picked { topic = picked }
                            }
                            if let error {
                                Text(error).textStyle(.bodySmall, Palette.error)
                            }
                            HStack(spacing: 10) {
                                GlowButton(title: t("Сохранить"), enabled: changed && !name.trimmingCharacters(in: .whitespaces).isEmpty) {
                                    error = model.updateGroup(group.id, name: name, description: description, topic: topic)
                                    saved = error == nil
                                }
                                if saved { Text(t("Сохранено")).textStyle(.labelLarge, Palette.lilac) }
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.bottom, 16)
                    }
                    GroupInfoForm(model: model, group: group, business: model.state.plan == .business)
                    card {
                        caption(t("Оформление"))
                        Text(t("Цвет группы — кнопки, цены и метки"))
                            .textStyle(.bodySmall, Palette.mist)
                            .padding(.horizontal, 16)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 10) {
                                ForEach(GroupAccent.allCases) { a in
                                    let active = group.accentColor == a
                                    Button { model.setGroupAccent(group.id, a) } label: {
                                        Circle()
                                            .fill(a.color)
                                            .frame(width: 40, height: 40)
                                            .overlay(Circle().strokeBorder(active ? Palette.ink : Palette.line, lineWidth: active ? 3 : 1))
                                    }
                                    .buttonStyle(.plain)
                                    .accessibilityLabel(a.title)
                                }
                            }
                            .padding(16)
                        }
                        Text(t("Фон страницы группы"))
                            .textStyle(.bodySmall, Palette.mist)
                            .padding(.horizontal, 16)
                        BackdropChooser(
                            selected: group.backdrop ?? Backdrop.default,
                            onPick: { model.setGroupBackdrop(group.id, $0) },
                            onReset: group.backdrop != nil ? { model.setGroupBackdrop(group.id, nil) } : nil,
                            resetLabel: t("Как везде"),
                            // Фон страницы группы — Memory+ Бизнес
                            locked: { _ in model.state.plan != .business },
                            onLocked: { _ in model.require(.groupBackdrop) },
                            business: true
                        )
                        .padding(16)
                        Text(t("Товары витрины добавляются и меняются на странице группы — вкладка «Витрина»."))
                            .textStyle(.bodySmall, Palette.mist)
                            .fixedSize(horizontal: false, vertical: true)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                    }
                    card {
                        caption(t("Кто может писать"))
                        choice(t("Все участники"), selected: !group.onlyOwnerPosts) { model.setGroupOnlyOwnerPosts(group.id, false) }
                        choice(t("Только я"), selected: group.onlyOwnerPosts) { model.setGroupOnlyOwnerPosts(group.id, true) }
                        Text(t("Закрепить запись — «⋯» на записи в группе."))
                            .textStyle(.bodySmall, Palette.mist)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                    }
                }

                card {
                    HStack(spacing: 14) {
                        Glyph(kind: group.muted ? .bellOff : .bell, color: Palette.lilac, size: 22)
                        Text(t("Уведомления группы")).textStyle(.body)
                            .accessibilityHidden(true)
                        Spacer()
                        Toggle("", isOn: Binding(get: { !group.muted }, set: { _ in model.toggleGroupMute(group.id) }))
                            .labelsHidden()
                            .tint(Palette.orchid)
                            .accessibilityLabel(t("Уведомления группы"))
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    FadeLine().padding(.horizontal, 16)
                    Button { confirm = true } label: {
                        SettingRow(icon: group.mine ? .trash : .exit, title: group.mine ? t("Удалить группу") : t("Выйти из группы")) { EmptyView() }
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.top, 12)
            .padding(.bottom, tabBarSpace)
        }
        .scrollDismissesKeyboard(.interactively)
        .onChange(of: coverItem) { _, item in
            guard let item else { return }
            coverItem = nil
            Task {
                guard let data = try? await item.loadTransferable(type: Data.self),
                      let picked = MediaFiles.save(data, kind: .photo, ext: "jpg") else { return }
                if let old = group.cover { MediaFiles.delete(old) }
                model.setGroupCover(group.id, picked)
            }
        }
        .glassActions(
            $confirm,
            title: group.mine ? t("Удалить группу?") : t("Выйти из группы?"),
            message: group.mine
                ? tf("Удалить «%s» вместе с записями? Отменить будет нельзя.", group.name)
                : tf("Вы перестанете видеть записи «%s» в своих группах. Вернуться можно в любой момент.", group.name),
            cancel: true,
            actions: [GlassAction(icon: group.mine ? .trash : .exit, title: group.mine ? t("Удалить") : t("Выйти"), destructive: true) {
                if group.mine {
                    if let cover = group.cover { MediaFiles.delete(cover) }
                    group.posts.forEach { p in p.media.forEach { MediaFiles.delete($0) } }
                    // Фото товаров своей группы и её фон: на них никто не ссылается
                    group.shop.forEach { p in p.photos.forEach { MediaFiles.delete($0) } }
                    if let photo = group.backdrop?.photo, !photo.isEmpty {
                        MediaFiles.delete(Attachment(id: "", kind: .photo, fileName: photo))
                    }
                    model.deleteGroup(group.id)
                } else {
                    model.closeGroupSettings()
                    model.toggleGroup(group.id)
                }
            }]
        )
    }

    private func card<Content: View>(@ViewBuilder _ content: @escaping () -> Content) -> some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 0) { content() }
                .padding(.vertical, 6)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .cardPadding()
    }

    private func caption(_ text: String) -> some View {
        Text(text.uppercased())
            .textStyle(.label, Palette.mist)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
    }

    private func choice(_ title: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Text(title).textStyle(.body, selected ? Palette.ink : Palette.mist)
                Spacer()
                if selected { Glyph(kind: .check, color: Palette.lilac, size: 20) }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// Подробно о группе: описание, сайт и контакты — видны всем на странице группы.
private struct GroupInfoForm: View {
    let model: AppModel
    let group: MemoryGroup
    /// Контакты — в Бизнесе.
    let business: Bool

    @State private var about: String
    @State private var website: String
    @State private var phone: String
    @State private var email: String
    @State private var address: String
    @State private var hours: String
    @State private var saved = false

    init(model: AppModel, group: MemoryGroup, business: Bool) {
        self.model = model
        self.group = group
        self.business = business
        _about = State(initialValue: group.info.about)
        _website = State(initialValue: group.info.website)
        _phone = State(initialValue: group.info.phone)
        _email = State(initialValue: group.info.email)
        _address = State(initialValue: group.info.address)
        _hours = State(initialValue: group.info.hours)
    }

    private var info: GroupInfo {
        GroupInfo(about: about, website: website, phone: phone, email: email, address: address, hours: hours)
    }

    var body: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 10) {
                Text(t("Подробнее и контакты").uppercased())
                    .textStyle(.label, Palette.mist)
                    .padding(.vertical, 4)
                GlassField(placeholder: t("Подробное описание: чем занимаетесь, условия, доставка"), text: $about, limit: 2000, maxLines: 8)
                    .glass(radius: 16)
                GlassField(placeholder: t("Сайт"), text: $website, limit: 200, singleLine: true, keyboard: .URL, plain: true)
                    .glass(radius: 16)
                if business {
                    GlassField(placeholder: t("Телефон"), text: $phone, limit: 40, singleLine: true, keyboard: .phonePad)
                        .glass(radius: 16)
                    GlassField(placeholder: t("Почта"), text: $email, limit: 80, singleLine: true, keyboard: .emailAddress, plain: true)
                        .glass(radius: 16)
                    GlassField(placeholder: t("Адрес"), text: $address, limit: 160, singleLine: true)
                        .glass(radius: 16)
                    GlassField(placeholder: t("Часы работы — например, «Пн–Пт 10–19»"), text: $hours, limit: 80, singleLine: true)
                        .glass(radius: 16)
                } else {
                    // Контакты — в Бизнесе; уже заполненные остаются в карточке группы
                    Button { model.require(.groupContacts) } label: {
                        HStack(spacing: 10) {
                            Text(t("Телефон, почта, адрес и часы работы"))
                                .textStyle(.bodySmall, Palette.mist)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            PlusBadge(business: true)
                        }
                        .padding(14)
                        .glass(radius: 16)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
                HStack(spacing: 10) {
                    GlowButton(title: t("Сохранить"), enabled: info != group.info) {
                        model.updateGroupInfo(group.id, info)
                        // Как сохранилось: без пробелов по краям, сайт — с https://
                        if let stored = model.state.groups.first(where: { $0.id == group.id })?.info {
                            about = stored.about
                            website = stored.website
                            phone = stored.phone
                            email = stored.email
                            address = stored.address
                            hours = stored.hours
                        }
                        saved = true
                    }
                    if saved { Text(t("Сохранено")).textStyle(.labelLarge, Palette.lilac) }
                }
                .padding(.top, 6)
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .cardPadding()
        .onChange(of: info) { _, _ in
            if info != group.info { saved = false }
        }
    }
}
