import SwiftUI

/// Вкладка «Профиль»: карточка с аватаром и счётчиками, сведения о себе
/// и «Сохранённое». Настройки — за шестерёнкой вверху, рядом с карандашом.
/// Нажатие на аватар — сменить или убрать фото.
struct ProfileScreen: View {
    let model: AppModel
    /// Имя и сведения — ещё и в сохранённый аккаунт, чтобы пережили перезапуск.
    let onSaveProfile: (String, ProfileInfo) -> Void
    @State private var editing = false

    var body: some View {
        let state = model.state
        let me = state.me
        let hasInfo = !me.city.isEmpty || !me.birthday.isEmpty || !me.website.isEmpty || !me.interests.isEmpty

        ScrollView {
            LazyVStack(spacing: 14) {
                HStack(spacing: 10) {
                    ScreenTitle(title: t("Профиль"), subtitle: "@\(me.handle)")
                    Spacer()
                    RoundGlyphButton(icon: .gear, color: Palette.lilac, iconSize: 20) { model.openSettings() }
                    if !editing {
                        RoundGlyphButton(icon: .edit, color: Palette.lilac, iconSize: 20) { editing = true }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 8)

                GlassCard {
                    VStack(spacing: 10) {
                        EditableAvatar(user: me, onPicked: setAvatar, onRemove: { setAvatar("") })
                        if editing {
                            EditForm(
                                user: me,
                                onSave: { name, info in
                                    model.updateProfile(name: name, info: info)
                                    onSaveProfile(name, info)
                                    editing = false
                                },
                                onCancel: { editing = false }
                            )
                        } else {
                            Text(me.name.uppercased()).textStyle(.headline).multilineTextAlignment(.center)
                            if !me.bio.isEmpty {
                                Text(me.bio).textStyle(.bodySmall, Palette.mist).multilineTextAlignment(.center)
                            }
                            FadeLine().padding(.top, 6)
                            // Счётчики — кнопки: каждый открывает свой раздел
                            HStack {
                                StatButton(value: state.myPosts.count, label: t("записи")) { model.openProfileSection(.posts) }
                                StatButton(value: state.followers.count, label: t("подписчики")) { model.openProfileSection(.followers) }
                                StatButton(value: state.following.count, label: t("подписки")) { model.openProfileSection(.following) }
                                StatButton(value: state.myGroups.count, label: t("группы")) { model.openProfileSection(.groups) }
                            }
                            .padding(.top, 6)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 24)
                    .padding(.horizontal, 16)
                }
                .cardPadding()

                if !editing {
                    GlassCard {
                        VStack(alignment: .leading, spacing: 10) {
                            Text(t("О СЕБЕ")).textStyle(.label, Palette.mist)
                            if hasInfo || !me.joined.isEmpty { UserInfo(user: me) }
                            InterestChips(interests: me.interests)
                            if !hasInfo {
                                Text(t("Добавьте город, день рождения, сайт и интересы — так вас проще найти."))
                                    .textStyle(.bodySmall, Palette.mist)
                                    .fixedSize(horizontal: false, vertical: true)
                                GhostButton(title: t("Заполнить")) { editing = true }
                            }
                        }
                        .padding(16)
                    }
                    .cardPadding()

                    GlassCard {
                        Button { model.openProfileSection(.saved) } label: {
                            SettingRow(icon: .bookmark, title: t("Сохранённое")) {
                                Text("\(state.savedPrisms.count)").textStyle(.bodySmall, Palette.mist)
                            }
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                    .cardPadding()
                }
            }
            .padding(.top, 12)
            .padding(.bottom, tabBarSpace)
        }
        .scrollDismissesKeyboard(.interactively)
    }

    /// Новое фото (или "" — убрать): в профиль и в аккаунт; старый файл удаляется.
    private func setAvatar(_ fileName: String) {
        let old = model.state.me.avatar ?? ""
        model.setAvatar(fileName)
        onSaveProfile(model.state.me.name, model.profileInfo())
        if !old.isEmpty && old != fileName {
            MediaFiles.delete(Attachment(id: old, kind: .photo, fileName: old))
        }
    }
}

/// Счётчик профиля, который можно нажать.
struct StatButton: View {
    let value: Int
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Stat(value: value, label: label)
                .padding(.vertical, 6)
                .frame(maxWidth: .infinity)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

struct SettingRow<Trailing: View>: View {
    let icon: LineIcon
    let title: String
    @ViewBuilder let trailing: () -> Trailing

    var body: some View {
        HStack(spacing: 14) {
            Glyph(kind: icon, color: Palette.lilac, size: 22)
            Text(title).textStyle(.body)
            Spacer()
            trailing()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
}

/// Правка профиля: имя, о себе, город, день рождения, сайт, интересы.
private struct EditForm: View {
    let user: User
    let onSave: (String, ProfileInfo) -> Void
    let onCancel: () -> Void

    @State private var name: String
    @State private var bio: String
    @State private var city: String
    @State private var birthday: String
    @State private var website: String
    @State private var interests: [String]
    @State private var showInterests = false

    init(user: User, onSave: @escaping (String, ProfileInfo) -> Void, onCancel: @escaping () -> Void) {
        self.user = user
        self.onSave = onSave
        self.onCancel = onCancel
        _name = State(initialValue: user.name)
        _bio = State(initialValue: user.bio)
        _city = State(initialValue: user.city)
        _birthday = State(initialValue: user.birthday)
        _website = State(initialValue: user.website)
        _interests = State(initialValue: user.interests)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            field(t("Имя"), $name, limit: 40)
            field(t("О себе"), $bio, limit: 160, multiline: true)
            Text(plural(max(0, 160 - bio.count), "символ остался", "символа осталось", "символов осталось"))
                .textStyle(.bodySmall, Palette.mist)
            field(t("Город"), $city, limit: 40)
            field(t("День рождения, например 14 марта"), $birthday, limit: 20)
            field(t("Сайт или соцсеть"), $website, limit: 80, keyboard: .URL)
            // Интересов много — список раскрывается по нажатию на «Интересы»
            DisclosurePill(
                text: interests.isEmpty ? t("Интересы") : tf("Интересы · %s", interests.count),
                open: showInterests,
                active: !interests.isEmpty
            ) {
                withAnimation(.easeOut(duration: 0.2)) { showInterests.toggle() }
            }
            if showInterests {
                // Интересы раскрываются по одному, с небольшой задержкой
                FlowLayout(spacing: 8) {
                    ForEach(Array(interestTopics.enumerated()), id: \.element) { i, tag in
                        let on = interests.contains(tag)
                        Chip(text: t(tag), active: on) {
                            if on {
                                interests.removeAll { $0 == tag }
                            } else {
                                interests.append(tag)
                            }
                        }
                        .staggerIn(i)
                    }
                }
            }
            HStack(spacing: 10) {
                GhostButton(title: t("Отмена"), action: onCancel)
                GlowButton(title: t("Сохранить"), enabled: !name.trimmingCharacters(in: .whitespaces).isEmpty) {
                    let trim: (String) -> String = { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                    onSave(trim(name), ProfileInfo(
                        bio: trim(bio), city: trim(city), birthday: trim(birthday),
                        website: trim(website), interests: interests, avatar: user.avatar ?? ""
                    ))
                }
            }
            .padding(.top, 4)
        }
    }

    private func field(_ placeholder: String, _ text: Binding<String>, limit: Int, multiline: Bool = false, keyboard: UIKeyboardType = .default) -> some View {
        GlassField(
            placeholder: placeholder, text: text, limit: limit,
            singleLine: !multiline, maxLines: 4, keyboard: keyboard, plain: keyboard == .URL
        )
        .glass(radius: 16)
    }
}
