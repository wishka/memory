import SwiftUI

/// Вкладка «Профиль»: карточка с аватаром и счётчиками, сведения о себе
/// и настройки. Записи, подписчики, подписки, группы и сохранённое — на
/// своих страницах: их открывают счётчики и строка «Сохранённое».
struct ProfileScreen: View {
    let model: AppModel
    /// Имя и сведения — ещё и в сохранённый аккаунт, чтобы пережили перезапуск.
    let onSaveProfile: (String, ProfileInfo) -> Void
    let onLogout: () -> Void
    @State private var editing = false
    @State private var confirmLogout = false

    private var version: String {
        let info = Bundle.main.infoDictionary
        let short = info?["CFBundleShortVersionString"] as? String ?? ""
        let build = info?["CFBundleVersion"] as? String ?? ""
        return build.isEmpty ? short : "\(short) (\(build))"
    }

    var body: some View {
        let state = model.state
        let me = state.me
        let hasInfo = !me.city.isEmpty || !me.birthday.isEmpty || !me.website.isEmpty || !me.interests.isEmpty

        ScrollView {
            LazyVStack(spacing: 14) {
                HStack {
                    ScreenTitle(title: "Профиль", subtitle: "@\(me.handle)")
                    Spacer()
                    if !editing {
                        RoundGlyphButton(icon: .edit, color: Palette.lilac, iconSize: 20) { editing = true }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 8)

                GlassCard {
                    VStack(spacing: 10) {
                        Avatar(user: me, size: 104, ring: true)
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
                                statButton(state.myPosts.count, "записи", .posts)
                                statButton(state.followers.count, "подписчики", .followers)
                                statButton(state.following.count, "подписки", .following)
                                statButton(state.myGroups.count, "группы", .groups)
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
                            Text("О СЕБЕ").textStyle(.label, Palette.mist)
                            if hasInfo || !me.joined.isEmpty { UserInfo(user: me) }
                            InterestChips(interests: me.interests)
                            if !hasInfo {
                                Text("Добавьте город, день рождения, сайт и интересы — так вас проще найти.")
                                    .textStyle(.bodySmall, Palette.mist)
                                    .fixedSize(horizontal: false, vertical: true)
                                GhostButton(title: "Заполнить") { editing = true }
                            }
                        }
                        .padding(16)
                    }
                    .cardPadding()
                }

                settings(state)

            }
            .padding(.top, 12)
            .padding(.bottom, tabBarSpace)
        }
        .scrollDismissesKeyboard(.interactively)
        .confirmationDialog("Выйти из аккаунта?", isPresented: $confirmLogout, titleVisibility: .visible) {
            Button("Выйти", role: .destructive, action: onLogout)
            Button("Отмена", role: .cancel) {}
        }
    }

    private func statButton(_ value: Int, _ label: String, _ section: ProfileSection) -> some View {
        Button { model.openProfileSection(section) } label: {
            Stat(value: value, label: label)
                .padding(.vertical, 6)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func settings(_ state: UiState) -> some View {
        GlassCard {
            VStack(spacing: 0) {
                Button { model.openProfileSection(.saved) } label: {
                    SettingRow(icon: .bookmark, title: "Сохранённое") {
                        Text("\(state.savedPrisms.count)").textStyle(.bodySmall, Palette.mist)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                FadeLine().padding(.horizontal, 16)
                SettingRow(icon: .bell, title: "Уведомления") {
                    Toggle("", isOn: Binding(get: { state.notifications }, set: { model.setNotifications($0) }))
                        .labelsHidden()
                        .tint(Palette.orchid)
                }
                FadeLine().padding(.horizontal, 16)
                SettingRow(icon: .info, title: "О приложении") {
                    Text("Memory \(version)").textStyle(.bodySmall, Palette.mist)
                }
                FadeLine().padding(.horizontal, 16)
                Button { confirmLogout = true } label: {
                    SettingRow(icon: .exit, title: "Выйти") { EmptyView() }
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .cardPadding()
    }
}

private struct SettingRow<Trailing: View>: View {
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
            field("Имя", $name, limit: 40)
            field("О себе", $bio, limit: 160, multiline: true)
            Text(plural(max(0, 160 - bio.count), "символ остался", "символа осталось", "символов осталось"))
                .textStyle(.bodySmall, Palette.mist)
            field("Город", $city, limit: 40)
            field("День рождения, например 14 марта", $birthday, limit: 20)
            field("Сайт или соцсеть", $website, limit: 80, keyboard: .URL)
            // Интересов много — список раскрывается по нажатию на «Интересы»
            DisclosurePill(
                text: interests.isEmpty ? "Интересы" : "Интересы · \(interests.count)",
                open: showInterests,
                active: !interests.isEmpty
            ) {
                withAnimation(.easeOut(duration: 0.2)) { showInterests.toggle() }
            }
            if showInterests {
                FlowLayout(spacing: 8) {
                    ForEach(interestTopics, id: \.self) { tag in
                        let on = interests.contains(tag)
                        Chip(text: tag, active: on) {
                            if on {
                                interests.removeAll { $0 == tag }
                            } else {
                                interests.append(tag)
                            }
                        }
                    }
                }
            }
            HStack(spacing: 10) {
                GhostButton(title: "Отмена", action: onCancel)
                GlowButton(title: "Сохранить", enabled: !name.trimmingCharacters(in: .whitespaces).isEmpty) {
                    let trim: (String) -> String = { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                    onSave(trim(name), ProfileInfo(
                        bio: trim(bio), city: trim(city), birthday: trim(birthday),
                        website: trim(website), interests: interests
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
