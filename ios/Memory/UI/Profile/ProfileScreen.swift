import SwiftUI

/// Интересы на выбор в профиле: темы групп и ещё несколько.
private let interestOptions = groupTopics + ["Астрономия", "Кулинария", "Наука", "Игры"]

/// Вкладка «Профиль»: карточка с аватаром и счётчиками, сведения о себе,
/// интересы, группы и подписки, настройки и свои записи.
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
                            HStack {
                                Stat(value: state.myPosts.count, label: "записи")
                                Stat(value: 128, label: "подписчики")
                                Stat(value: state.following.count, label: "подписки")
                                Stat(value: state.myGroups.count, label: "группы")
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

                if !state.myGroups.isEmpty {
                    SectionTitle(text: "Мои группы · \(state.myGroups.count)")
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 10) {
                            ForEach(state.myGroups) { g in
                                Button { model.openGroup(g.id) } label: {
                                    VStack(spacing: 6) {
                                        Nebula(seed: g.seed)
                                            .frame(width: 80, height: 80)
                                            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                                        Text(g.name).textStyle(.labelLarge).lineLimit(1)
                                    }
                                    .frame(width: 96)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal, 16)
                    }
                }

                if !state.savedPrisms.isEmpty {
                    SectionTitle(text: "Сохранённое · \(state.savedPrisms.count)")
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 10) {
                            ForEach(state.savedPrisms) { p in
                                Button { model.openPrism(p.id) } label: {
                                    VStack(alignment: .leading, spacing: 6) {
                                        ZStack {
                                            PrismArt(spec: PrismSpec(seed: p.seed), timeMs: 1_500)
                                            Glyph(kind: .play, color: Palette.ink, size: 26)
                                        }
                                        .frame(width: 96, height: 150)
                                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                                        Text(p.caption).textStyle(.labelLarge).lineLimit(2)
                                    }
                                    .frame(width: 96)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal, 16)
                    }
                }

                if !state.followedPeople.isEmpty {
                    SectionTitle(text: "Подписки · \(state.followedPeople.count)")
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 12) {
                            ForEach(state.followedPeople) { person in
                                Button { model.openPerson(person.id) } label: {
                                    VStack(spacing: 6) {
                                        Avatar(user: person, size: 56, showOnline: true)
                                        Text(person.firstName).textStyle(.labelLarge).lineLimit(1)
                                    }
                                    .frame(width: 68)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal, 16)
                    }
                }

                settings(state)

                SectionTitle(text: state.myPosts.isEmpty ? "Мои записи" : "Мои записи · \(state.myPosts.count)")
                if state.myPosts.isEmpty {
                    Text("Пока ни одной. Поделитесь первым моментом во вкладке «Лента».")
                        .textStyle(.bodySmall, Palette.mist)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 24)
                }
                ForEach(state.myPosts) { post in
                    PostCard(
                        post: post,
                        onLike: { model.toggleLike(post.id) },
                        onComments: { model.openComments(post.id) },
                        onOpenMedia: model.openMedia
                    )
                }
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

    private func settings(_ state: UiState) -> some View {
        GlassCard {
            VStack(spacing: 0) {
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
            Text("Интересы").textStyle(.bodySmall, Palette.mist)
            FlowLayout(spacing: 8) {
                ForEach(interestOptions, id: \.self) { tag in
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
