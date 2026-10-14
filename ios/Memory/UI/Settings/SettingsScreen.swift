import SwiftUI

/// Что настройкам нужно от входа: выход, смена пароля, удаление аккаунта.
struct AccountActions {
    let logout: () -> Void
    /// Ответ — текст ошибки или nil.
    let changePassword: (_ current: String, _ new: String, _ repeated: String) async -> String?
    /// Ответ — текст ошибки или nil (аккаунт удалён, выполнен выход).
    let deleteAccount: (_ password: String) async -> String?
    /// Коды восстановления и почта; nil — недоступно (в превью).
    var recovery: RecoveryActions? = nil
    /// «Скачать мои данные»; nil — недоступно (в превью).
    var data: DataActions? = nil
}

/// Настройки за шестерёнкой в профиле. Список разделов и страница
/// каждого раздела — на одном экране, «назад» ведёт со страницы в список.
struct SettingsScreen: View {
    let model: AppModel
    let page: SettingsPage
    let account: AccountActions
    // Гид: поиск и раскрытые темы переживают переход между главами
    @State private var guideQuery = ""
    @State private var guideOpen: Set<String> = []

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 14) {
                    HStack(spacing: 14) {
                        RoundGlyphButton(icon: .back) { model.settingsBack() }
                        Text(page.title.uppercased())
                            .textStyle(.headline)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                            .accessibilityAddTraits(.isHeader)
                        Spacer()
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 4)
                    .id(Self.top)

                    switch page {
                    case .main: MainSettings(model: model, account: account)
                    case .security: SecurityPage(account: account)
                    case .privacy:
                        PrivacyPage(model: model)
                        // Право на доступ к своим данным: архив со всем, что хранится
                        if let data = account.data { MyDataCard(actions: data) }
                        // Отчёты о сбоях и статистика — только с согласия
                        TelemetryCard()
                    case .blacklist: BlacklistPage(model: model)
                    case .notifications: NotificationsPage(model: model)
                    case .textSize: TextSizePage()
                    case .language: LanguagePage()
                    case .about: AboutPage(model: model)
                    case .legal: LegalPage(model: model)
                    case .growth: GrowthPage(model: model)
                    case .sounds: SoundsPage(model: model)
                    case .guide: GuidePage(model: model, query: $guideQuery, expanded: $guideOpen)
                    case .theme: ThemePage()
                    case .wallpaper:
                        VStack(alignment: .leading, spacing: 12) {
                            BackdropChooser(
                                selected: model.state.backdrop,
                                onPick: model.setBackdrop,
                                // Бесплатно — четыре фона; остальные и своё фото — Memory+
                                locked: { !Premium.freeBackdrops.contains($0) && model.state.plan == .free },
                                onLocked: { _ in model.require(.backdrops) }
                            )
                            Text(t("Фон для всех переписок. Свой фон для одной переписки — в её меню «⋯» вверху."))
                                .textStyle(.bodySmall, Palette.mist)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .padding(.horizontal, 16)
                    }
                }
                .padding(.top, 12)
                .padding(.bottom, tabBarSpace)
            }
            .scrollDismissesKeyboard(.interactively)
            // Новая страница или глава гида — с самого верха
            .onChange(of: page) { _, _ in proxy.scrollTo(Self.top, anchor: .top) }
            .onChange(of: model.state.guideChapter) { _, _ in proxy.scrollTo(Self.top, anchor: .top) }
        }
    }

    private static let top = "settings-top"
}

/// Версия приложения: «1.4 (12)».
var appVersion: String {
    let info = Bundle.main.infoDictionary
    let short = info?["CFBundleShortVersionString"] as? String ?? ""
    let build = info?["CFBundleVersion"] as? String ?? ""
    return build.isEmpty ? short : "\(short) (\(build))"
}

// MARK: - Список разделов

private struct MainSettings: View {
    let model: AppModel
    let account: AccountActions
    @State private var confirmLogout = false

    var body: some View {
        let state = model.state
        SettingsCard {
            NavRow(
                icon: .info, title: t("Гид по Memory"),
                value: tf("%s из %s", min(state.guideRead.count, Guide.topicCount), Guide.topicCount)
            ) { model.openGuide() }
        }
        SettingsCard {
            NavRow(icon: .shield, title: t("Безопасность")) { model.openSettings(.security) }
            Divider()
            NavRow(icon: .lock, title: t("Приватность")) { model.openSettings(.privacy) }
            Divider()
            NavRow(icon: .block, title: t("Чёрный список"), value: state.blocked.isEmpty ? "" : "\(state.blocked.count)") {
                model.openSettings(.blacklist)
            }
        }
        SettingsCard {
            NavRow(icon: .bell, title: t("Уведомления"), value: state.notifications.all ? t("Вкл.") : t("Выкл.")) {
                model.openSettings(.notifications)
            }
            Divider()
            NavRow(icon: .image, title: t("Фон переписок"), value: state.backdrop.kind.title) { model.openSettings(.wallpaper) }
            Divider()
            NavRow(icon: .textSize, title: t("Размер текста"), value: textScaleTitle(L10n.shared.textScale)) {
                model.openSettings(.textSize)
            }
            Divider()
            NavRow(icon: .eye, title: t("Оформление"), value: ThemeState.shared.mode.title) { model.openSettings(.theme) }
            Divider()
            NavRow(icon: .globe, title: t("Язык"), value: L10n.shared.choice.title) { model.openSettings(.language) }
            Divider()
            NavRow(icon: .info, title: t("О приложении"), value: "Memory \(appVersion)") { model.openSettings(.about) }
            Divider()
            NavRow(icon: .document, title: t("Документы"), value: "\(Legal.minAge)+") { model.openSettings(.legal) }
        }
        SettingsCard {
            NavRow(icon: .gift, title: t("Друзья и идеи"), value: state.inviteCode) { model.openSettings(.growth) }
        }
        SettingsCard {
            Button { confirmLogout = true } label: {
                SettingRow(icon: .exit, title: t("Выйти")) { EmptyView() }
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .glassActions(
            $confirmLogout,
            title: t("Выйти из аккаунта?"),
            message: t("Записи и переписка останутся на этом телефоне — они вернутся, когда вы войдёте снова."),
            cancel: true,
            actions: [GlassAction(icon: .exit, title: t("Выйти"), destructive: true, action: account.logout)]
        )
    }
}

// MARK: - Безопасность

private struct SecurityPage: View {
    let account: AccountActions

    @State private var current = ""
    @State private var new = ""
    @State private var repeated = ""
    @State private var busy = false
    @State private var message: String?
    @State private var success = false

    @State private var deleting = false
    @State private var deletePassword = ""
    @State private var deleteError: String?

    var body: some View {
        SettingsCard {
            VStack(alignment: .leading, spacing: 10) {
                Caption(text: t("Смена пароля"))
                PasswordField(placeholder: t("Текущий пароль"), text: $current)
                PasswordField(placeholder: t("Новый пароль"), text: $new)
                PasswordField(placeholder: t("Повторите новый пароль"), text: $repeated)
                Text(t("От 8 символов, с буквами и цифрами.")).textStyle(.bodySmall, Palette.mist)
                if let message {
                    Text(t(message)).textStyle(.bodySmall, success ? Palette.lilac : Palette.error)
                }
                GlowButton(
                    title: t("Сменить пароль"),
                    enabled: !busy && !current.isEmpty && !new.isEmpty && !repeated.isEmpty
                ) {
                    busy = true
                    Task {
                        let error = await account.changePassword(current, new, repeated)
                        busy = false
                        success = error == nil
                        message = error ?? t("Пароль изменён")
                        if error == nil {
                            current = ""
                            new = ""
                            repeated = ""
                        }
                    }
                }
            }
            .padding(16)
        }
        .onChange(of: current) { _, _ in message = nil }
        .onChange(of: new) { _, _ in message = nil }
        .onChange(of: repeated) { _, _ in message = nil }

        if let recovery = account.recovery {
            SettingsCard {
                RecoverySettings(actions: recovery)
            }
        }

        SettingsCard {
            VStack(alignment: .leading, spacing: 8) {
                Caption(text: t("Как защищён аккаунт"))
                Bullet(text: t("Пароль не хранится — только его отпечаток (PBKDF2, 120 000 итераций)."))
                Bullet(text: t("После пяти неверных попыток вход закрывается на 30 секунд."))
                Bullet(text: t("Сессия хранится в связке ключей iPhone."))
                Bullet(text: t("Записи, переписка и фото хранятся только на этом телефоне."))
            }
            .padding(16)
        }

        SettingsCard {
            VStack(alignment: .leading, spacing: 10) {
                Caption(text: t("Удаление аккаунта"))
                Text(t("Аккаунт, записи и переписка будут удалены с этого телефона. Отменить будет нельзя."))
                    .textStyle(.bodySmall, Palette.mist)
                    .fixedSize(horizontal: false, vertical: true)
                if !deleting {
                    GhostButton(title: t("Удалить аккаунт")) { deleting = true }
                } else {
                    PasswordField(placeholder: t("Пароль для подтверждения"), text: $deletePassword)
                    if let deleteError {
                        Text(t(deleteError)).textStyle(.bodySmall, Palette.error)
                    }
                    HStack(spacing: 10) {
                        GhostButton(title: t("Отмена")) {
                            deleting = false
                            deletePassword = ""
                            deleteError = nil
                        }
                        GlowButton(title: t("Удалить навсегда"), enabled: !deletePassword.isEmpty && !busy) {
                            busy = true
                            Task {
                                deleteError = await account.deleteAccount(deletePassword)
                                busy = false
                            }
                        }
                    }
                }
            }
            .padding(16)
        }
    }
}

private struct PasswordField: View {
    let placeholder: String
    @Binding var text: String

    var body: some View {
        GlassField(placeholder: placeholder, text: $text, limit: 64, singleLine: true, secure: true, plain: true)
            .glass(radius: 16)
    }
}

// MARK: - Приватность

private struct PrivacyPage: View {
    let model: AppModel

    var body: some View {
        let privacy = model.state.privacy
        SettingsCard {
            SwitchRow(title: t("Закрытый профиль"), isOn: privacy.closedProfile) { v in update { $0.closedProfile = v } }
        }
        Hint(text: privacy.closedProfile
             ? t("Записи и чек-ины видят только ваши друзья. Заявки в друзья можно принять или отклонить в профиле.")
             : t("Закройте профиль, чтобы записи и чек-ины видели только ваши друзья. Нынешние друзья останутся."))
        SettingsCard {
            VStack(alignment: .leading, spacing: 16) {
                AudiencePicker(title: t("Кто видит сведения о себе"), subtitle: t("Город, день рождения, сайт и интересы"),
                               value: privacy.profileInfo) { v in update { $0.profileInfo = v } }
                FadeLine()
                AudiencePicker(title: t("Кто видит моих друзей"), value: privacy.followLists) { v in
                    update { $0.followLists = v }
                }
                FadeLine()
                AudiencePicker(title: t("Кто может написать мне"), value: privacy.messages) { v in
                    update { $0.messages = v }
                }
                FadeLine()
                AudiencePicker(title: t("Кто может мне звонить"), value: privacy.calls) { v in
                    update { $0.calls = v }
                }
                FadeLine()
                AudiencePicker(title: t("Кто может комментировать записи"), value: privacy.comments) { v in
                    update { $0.comments = v }
                }
            }
            .padding(16)
        }
        SettingsCard {
            SwitchRow(title: t("Показывать, что я в сети"), isOn: privacy.showOnline) { v in update { $0.showOnline = v } }
            Divider()
            SwitchRow(title: t("Показывать день рождения"), isOn: privacy.showBirthday) { v in update { $0.showBirthday = v } }
        }
        Hint(text: t("«Друзья» — люди, с которыми вы друзья: вы приняли их заявку или они вашу. Чтобы скрыть от одного человека всё сразу, добавьте его в чёрный список."))
        SettingsCard {
            SwitchRow(title: t("Персональные рекомендации"), isOn: privacy.personalRecs) { v in update { $0.personalRecs = v } }
        }
        Hint(text: privacy.personalRecs
             ? t("Полка советует фильмы и книги по вашим оценкам и вкусу похожих людей. Лента рекомендациями не сортируется.")
             : t("Рекомендации выключены: подборка «Для вас», совет после чек-ина и «Похожий вкус» не показываются."))
        LinkRow(text: t("Как работают рекомендации")) { model.openLegal(.recommendations) }
    }

    private func update(_ change: (inout Privacy) -> Void) {
        var p = model.state.privacy
        change(&p)
        model.setPrivacy(p)
    }
}

private struct AudiencePicker: View {
    let title: String
    var subtitle: String? = nil
    let value: Audience
    let onPick: (Audience) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(t(title)).textStyle(.title)
            if let subtitle {
                Text(t(subtitle)).textStyle(.bodySmall, Palette.mist)
            }
            FlowLayout(spacing: 8) {
                ForEach(Audience.allCases, id: \.self) { a in
                    Chip(text: a.title, active: a == value) { onPick(a) }
                }
            }
        }
    }
}

// MARK: - Чёрный список

private struct BlacklistPage: View {
    let model: AppModel

    var body: some View {
        let people = model.state.blockedPeople
        if people.isEmpty {
            Hint(text: t("Здесь пусто. Чтобы заблокировать человека, откройте его страницу и нажмите «⋯» вверху справа — или долго удерживайте переписку с ним в «Чатах»."))
        } else {
            Hint(text: t("Записи, комментарии и сообщения этих людей вам не видны, и они не могут вам написать."))
        }
        ForEach(people) { person in
            GlassCard {
                HStack(spacing: 12) {
                    Avatar(user: person, size: 44)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(person.name).textStyle(.title).lineLimit(1)
                        Text("@\(person.handle)").textStyle(.bodySmall, Palette.mist)
                    }
                    Spacer(minLength: 8)
                    GhostButton(title: t("Разблокировать")) { model.unblock(person.id) }
                }
                .padding(12)
            }
            .cardPadding()
        }
    }
}

// MARK: - Уведомления

private struct NotificationsPage: View {
    let model: AppModel

    var body: some View {
        let n = model.state.notifications
        let mutedGroups = model.state.groups.filter { $0.joined && $0.muted }
        let mutedChats = model.state.chats.filter { $0.muted }
        SettingsCard {
            SwitchRow(title: t("Все уведомления"), isOn: n.all) { v in update { $0.all = v } }
        }
        SettingsCard {
            SwitchRow(title: t("Сообщения"), isOn: n.messages, enabled: n.all) { v in update { $0.messages = v } }
            Divider()
            SwitchRow(title: t("Новые PRISM друзей"), isOn: n.prism, enabled: n.all) { v in update { $0.prism = v } }
            Divider()
            SwitchRow(title: t("Лайки и комментарии"), isOn: n.reactions, enabled: n.all) { v in update { $0.reactions = v } }
            Divider()
            SwitchRow(title: t("Заявки в друзья"), isOn: n.follows, enabled: n.all) { v in update { $0.follows = v } }
            Divider()
            SwitchRow(title: t("Группы: новые записи и участники"), isOn: n.groups, enabled: n.all) { v in update { $0.groups = v } }
            Divider()
            SwitchRow(title: t("Друзья: чек-ины, фильмы, книги, тренировки"), isOn: n.friends, enabled: n.all) { v in update { $0.friends = v } }
            Divider()
            SwitchRow(title: t("Итоги месяца и года"), isOn: n.recap, enabled: n.all) { v in update { $0.recap = v } }
        }
        SettingsCard {
            NavRow(icon: .volume, title: t("Звуки"), value: t("Свой у каждого вида")) { model.openSettings(.sounds) }
        }
        Hint(text: t("Уведомления — про то, что важно именно вам: друг отметил фильм, который вы обсуждали, в вашей группе новый участник, готовы итоги месяца. Никаких «вы давно не заходили»."))
        if mutedGroups.isEmpty && mutedChats.isEmpty {
            Hint(text: t("Отключить уведомления отдельной группы можно на её странице, отдельной переписки — долгим нажатием в «Чатах»."))
        } else {
            SectionTitle(text: t("Без звука"))
            SettingsCard {
                ForEach(mutedGroups) { g in
                    MutedRow(title: g.name, kind: t("Группа")) { model.toggleGroupMute(g.id) }
                }
                ForEach(mutedChats) { c in
                    MutedRow(title: c.peer.name, kind: t("Переписка")) { model.toggleChatMute(c.id) }
                }
            }
        }
    }

    private func update(_ change: (inout Notifications) -> Void) {
        var n = model.state.notifications
        change(&n)
        model.setNotifications(n)
    }
}

// MARK: - Звуки

/// Свой звук у каждого вида уведомлений — их узнаёшь, не глядя на экран.
/// Касание варианта — сразу слышно, как звучит.
private struct SoundsPage: View {
    let model: AppModel
    @State private var current = SoundPrefs().all()
    @State private var editing: NudgeKind?

    var body: some View {
        SettingsCard {
            ForEach(Array(soundKinds.enumerated()), id: \.element) { i, kind in
                if i > 0 { Divider() }
                NavRow(icon: .volume, title: kind.soundTitle, value: (current[kind] ?? NotifySound.default(kind)).title) { editing = kind }
            }
        }
        Hint(text: "57 звуков Memory в девяти стилях — от стекла и маримбы до лоу-фай и 8-бита. Звук играет и в пуше, и в плашке, когда приложение открыто.")
        if !model.state.notifications.all {
            Hint(text: t("Сейчас все уведомления выключены — звуков не будет."))
        }
        Color.clear.frame(height: 0)
            .sheet(item: $editing) { kind in
                SoundChooser(
                    kind: kind, selected: current[kind] ?? NotifySound.default(kind), free: model.state.plan == .free,
                    onLocked: { model.require(.sounds) }
                ) { sound in
                    SoundPrefs().set(kind, sound)
                    SoundPlayer.play(sound)
                    current = SoundPrefs().all()
                }
            }
    }
}

extension NudgeKind: Identifiable {
    var id: String { rawValue }
}

/// Звуки на выбор для одного вида уведомлений.
private struct SoundChooser: View {
    let kind: NudgeKind
    let selected: NotifySound
    /// Бесплатный тариф: звуки Memory, кроме звука по умолчанию, — в Memory+.
    let free: Bool
    let onLocked: () -> Void
    let onPick: (NotifySound) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var chosen: NotifySound?

    var body: some View {
        let active = chosen ?? selected
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(kind.soundTitle).textStyle(.headline)
                Spacer()
                GlowButton(title: t("Готово")) { dismiss() }
            }
            .padding(.horizontal, 16)
            .padding(.top, 18)
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    // По стилям: стекло, маримба, пузыри… и в конце — звук телефона и тишина
                    ForEach(SoundStyle.allCases) { style in
                        Text(style.title.uppercased())
                            .textStyle(.label, Palette.lilac)
                            .padding(.horizontal, 16)
                            .padding(.top, 16)
                            .padding(.bottom, 4)
                        ForEach(Array(NotifySound.inStyle(style).enumerated()), id: \.element) { i, sound in
                        if i > 0 { Divider() }
                        // Бесплатно — по одному-два звука каждого стиля и звук по умолчанию
                        let locked = free && !sound.free && sound != NotifySound.default(kind)
                        ChoiceRow(
                            title: sound.title + (sound == NotifySound.default(kind) ? " · " + t("по умолчанию") : "") + (locked ? " · PLUS" : ""),
                            selected: active == sound
                        ) {
                            if locked {
                                // Послушать можно, выбрать — в Memory+
                                SoundPlayer.play(sound)
                                onLocked()
                                dismiss()
                            } else {
                                chosen = sound
                                onPick(sound)
                            }
                        }
                        }
                    }
                }
            }
        }
        .background(Palette.night.ignoresSafeArea())
        .presentationDetents([.medium, .large])
        .presentationBackground(Palette.night)
        .presentationCornerRadius(28)
    }
}

private struct MutedRow: View {
    let title: String
    let kind: String
    let onUnmute: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Glyph(kind: .bellOff, color: Palette.mist, size: 20)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).textStyle(.body).lineLimit(1)
                Text(t(kind)).textStyle(.bodySmall, Palette.mist)
            }
            Spacer(minLength: 8)
            GhostButton(title: t("Включить"), action: onUnmute)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }
}

// MARK: - Размер текста

func textScaleTitle(_ scale: Double) -> String {
    if scale < 0.95 { return t("Мелкий") }
    if scale < 1.05 { return t("Обычный") }
    if scale < 1.2 { return t("Крупный") }
    return t("Очень крупный")
}

private struct TextSizePage: View {
    var body: some View {
        let current = L10n.shared.textScale
        SettingsCard {
            ForEach(Array(L10n.textScales.enumerated()), id: \.offset) { i, scale in
                if i > 0 { Divider() }
                ChoiceRow(title: textScaleTitle(scale), selected: current == scale) {
                    L10n.shared.setTextScale(scale)
                }
            }
        }
        // Образец — сразу в выбранном размере
        SettingsCard {
            VStack(alignment: .leading, spacing: 6) {
                Caption(text: t("Образец"))
                Text(t("Вера Лунная")).textStyle(.title)
                Text(t("Сегодня небо было как акварель: фиолетовый перетекал в синий прямо над крышами."))
                    .textStyle(.body)
                    .fixedSize(horizontal: false, vertical: true)
                Text(t("@vera.moon · 12 мин")).textStyle(.bodySmall, Palette.mist)
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        Hint(text: t("Размер текста меняется во всём приложении, на этом телефоне."))
    }
}

// MARK: - Язык

private struct LanguagePage: View {
    var body: some View {
        SettingsCard {
            ForEach(Array(Lang.allCases.enumerated()), id: \.element) { i, lang in
                if i > 0 { Divider() }
                ChoiceRow(title: lang.title, selected: L10n.shared.choice == lang) {
                    L10n.shared.setLanguage(lang)
                }
            }
        }
        Hint(text: t("Переводится интерфейс. Записи и сообщения остаются на том языке, на котором их написали."))
    }
}

// MARK: - Оформление

/// Как в системе, тёмное «ночное» или светлое «рассветное». Выбор — на этом телефоне.
private struct ThemePage: View {
    var body: some View {
        let current = ThemeState.shared.mode
        SettingsCard {
            ForEach(Array(ThemeMode.allCases.enumerated()), id: \.element) { i, mode in
                if i > 0 { Divider() }
                ChoiceRow(title: mode.title, selected: current == mode) {
                    DevicePrefs().setTheme(mode)
                }
            }
        }
        Hint(text: t("«Как в системе» — днём светлое, ночью тёмное, если так настроен телефон. PRISM, эфиры, звонки и фото на весь экран всегда тёмные."))
    }
}

// MARK: - О приложении

private struct AboutPage: View {
    let model: AppModel

    var body: some View {
        SettingsCard {
            VStack(spacing: 6) {
                Text("MEMORY").textStyle(.display)
                Text(tf("Версия %s", appVersion)).textStyle(.bodySmall, Palette.mist)
                Text(t("Соцсеть о моментах: лента, истории, PRISM, чаты и группы."))
                    .textStyle(.bodySmall, Palette.mist)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 24)
            .padding(.horizontal, 16)
        }
        SettingsCard {
            VStack(alignment: .leading, spacing: 8) {
                Caption(text: t("Данные"))
                Bullet(text: t("Пока у Memory нет сервера, всё хранится на этом телефоне."))
                Bullet(text: t("Фото и видео копируются в память приложения и не видны другим приложениям."))
            }
            .padding(16)
        }
        SettingsCard {
            NavRow(icon: .document, title: t("Документы"), value: "\(Legal.minAge)+") { model.openSettings(.legal) }
            Divider()
            NavRow(icon: .info, title: t("Лицензии и авторы")) { model.openLegal(.licenses) }
        }
        Hint(text: t("Карты — Apple Maps. Гифки — Powered by GIPHY. Шрифты Unbounded и Inter — SIL Open Font License."))
    }
}

// MARK: - Документы

/// Соглашение, политика, согласие, правила, рекомендации и лицензии — те
/// же тексты, что на сайте (docs/legal). Контакты для обращений и жалоб.
private struct LegalPage: View {
    let model: AppModel

    var body: some View {
        SettingsCard {
            ForEach(Array(LegalDoc.allCases.enumerated()), id: \.element) { i, doc in
                if i > 0 { Divider() }
                NavRow(icon: .document, title: doc.title) { model.openLegal(doc) }
            }
        }
        SettingsCard {
            VStack(alignment: .leading, spacing: 8) {
                Caption(text: t("Связаться"))
                Bullet(text: tf("Вопросы о данных и отзыв согласия: %s", Legal.privacyEmail))
                Bullet(text: tf("Жалобы на контент: %s — или «Пожаловаться» в меню записи, сообщения, PRISM", Legal.abuseEmail))
                Bullet(text: tf("Оператор: %s", Legal.operatorName))
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        SettingsCard {
            NavRow(icon: .shield, title: t("Удалить аккаунт и отозвать согласие")) { model.openSettings(.security) }
        }
        Hint(text: tf("Memory — для людей от %s лет. Версия документов: %s.", Legal.minAge, Legal.version))
        if !Legal.filled {
            Hint(text: t("Черновик: данные оператора ещё не заполнены."))
        }
    }
}

// MARK: - Друзья и идеи

/// Пригласить друзей, свои цифры роста и «Предложить идею». K = i × c:
/// сколько приглашений отправлено и какая доля приходит — вторую цифру
/// посчитает сервер, до него показываем оценку.
private struct GrowthPage: View {
    let model: AppModel
    @Environment(\.openURL) private var openURL
    @State private var text = ""
    @State private var sharing: SharePayload?

    var body: some View {
        let state = model.state
        let stats = growthStats(state.growth)
        SettingsCard {
            VStack(alignment: .leading, spacing: 10) {
                Caption(text: "Ваш код приглашения")
                Text(state.inviteCode).textStyle(.display)
                Text(t("Друг откроет ссылку — Memory запомнит, кто его позвал. Лучше всего работают приглашения к делу: дорисовать рисунок, взять книгу, пойти на вылазку."))
                    .textStyle(.bodySmall, Palette.mist)
                    .fixedSize(horizontal: false, vertical: true)
                GlowButton(title: t("Пригласить друзей")) { sharing = shareInvite(model, .app) }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        SettingsCard {
            VStack(alignment: .leading, spacing: 8) {
                Caption(text: "Ваш вклад в рост")
                Bullet(text: tf("Приглашений отправлено: %s", stats.invitesSent))
                Bullet(text: tf("Карточек отправлено: %s", stats.cardsShared))
                Bullet(text: tf("Чек-инов: %s", stats.checkIns))
                if let invitedBy = state.invitedBy {
                    Bullet(text: tf("Вас позвал(а) %s", invitedBy.from.isEmpty ? invitedBy.code : invitedBy.from))
                }
                // На Android «%%» остаётся двумя знаками — здесь показываем один
                Text(tf("Оценка K ≈ %s: приглашения × доля пришедших (берём 10%%, точную посчитает сервер).",
                        amountNumber((stats.kEstimate() * 100).rounded() / 100)).replacingOccurrences(of: "%%", with: "%"))
                    .textStyle(.bodySmall, Palette.mist)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        SettingsCard {
            VStack(alignment: .leading, spacing: 10) {
                Caption(text: "Идеи и ошибки")
                Text(t("Что добавить или что сломалось? Идея, которую предлагают многие, точно попадёт в приложение. Откроется GitHub — понадобится учётная запись."))
                    .textStyle(.bodySmall, Palette.mist)
                    .fixedSize(horizontal: false, vertical: true)
                GlassField(placeholder: t("Опишите в паре предложений"), text: $text, limit: 1000)
                    .glass(radius: 18)
                HStack(spacing: 10) {
                    GlowButton(title: t("Предложить идею"), enabled: !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) {
                        model.logFeedback(true)
                        openLink(Feedback.link(idea: true, text: text.trimmingCharacters(in: .whitespacesAndNewlines), version: appVersion))
                        text = ""
                    }
                    GhostButton(title: t("Сообщить об ошибке")) {
                        model.logFeedback(false)
                        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                        openLink(Feedback.link(idea: false, text: trimmed.isEmpty ? t("Опишите, что случилось") : trimmed, version: appVersion))
                        text = ""
                    }
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .shareSheet($sharing)
    }

    private func openLink(_ link: String) {
        if let url = URL(string: link) { openURL(url) }
    }
}

// MARK: - Общее

private struct SettingsCard<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        GlassCard {
            VStack(spacing: 0) { content() }
        }
        .cardPadding()
    }
}

private struct Divider: View {
    var body: some View { FadeLine().padding(.horizontal, 16) }
}

private struct Caption: View {
    let text: String

    var body: some View {
        Text(t(text).uppercased())
            .textStyle(.label, Palette.mist)
            .accessibilityAddTraits(.isHeader)
    }
}

private struct Hint: View {
    let text: String

    var body: some View {
        Text(t(text))
            .textStyle(.bodySmall, Palette.mist)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 24)
    }
}

/// Ссылка под карточкой: «Как работают рекомендации».
private struct LinkRow: View {
    let text: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text).textStyle(.bodySmall, Palette.lilac)
        }
        .buttonStyle(.plain)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 24)
    }
}

private struct Bullet: View {
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Text("◇").textStyle(.bodySmall, Palette.lilac)
            Text(t(text)).textStyle(.bodySmall).fixedSize(horizontal: false, vertical: true)
        }
    }
}

/// Строка-переход: значок, название, значение справа и стрелка.
private struct NavRow: View {
    let icon: LineIcon
    let title: String
    var value: String = ""
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            SettingRow(icon: icon, title: title) {
                HStack(spacing: 6) {
                    if !value.isEmpty {
                        Text(value).textStyle(.bodySmall, Palette.mist).lineLimit(1)
                    }
                    // Стрелка «назад», развёрнутая вправо
                    Glyph(kind: .back, color: Palette.mist, size: 16)
                        .scaleEffect(x: -1, y: 1)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

private struct SwitchRow: View {
    let title: String
    let isOn: Bool
    var enabled: Bool = true
    let onChange: (Bool) -> Void

    var body: some View {
        Toggle(isOn: Binding(get: { isOn }, set: onChange)) {
            Text(t(title)).textStyle(.body, enabled ? Palette.ink : Palette.mist)
        }
        .tint(Palette.orchid)
        .disabled(!enabled)
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }
}

/// Вариант из нескольких: галочка у выбранного.
private struct ChoiceRow: View {
    let title: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                Text(title).textStyle(.body, selected ? Palette.ink : Palette.mist)
                Spacer()
                if selected { Glyph(kind: .check, color: Palette.lilac, size: 20) }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
