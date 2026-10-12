import SwiftUI

/// Что настройкам нужно от входа: выход, смена пароля, удаление аккаунта.
struct AccountActions {
    let logout: () -> Void
    /// Ответ — текст ошибки или nil.
    let changePassword: (_ current: String, _ new: String, _ repeated: String) async -> String?
    /// Ответ — текст ошибки или nil (аккаунт удалён, выполнен выход).
    let deleteAccount: (_ password: String) async -> String?
}

/// Настройки за шестерёнкой в профиле. Список разделов и страница
/// каждого раздела — на одном экране, «назад» ведёт со страницы в список.
struct SettingsScreen: View {
    let model: AppModel
    let page: SettingsPage
    let account: AccountActions

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 14) {
                HStack(spacing: 14) {
                    RoundGlyphButton(icon: .back) { model.settingsBack() }
                    Text(page.title.uppercased())
                        .textStyle(.headline)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                    Spacer()
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 4)

                switch page {
                case .main: MainSettings(model: model, account: account)
                case .security: SecurityPage(account: account)
                case .privacy: PrivacyPage(model: model)
                case .blacklist: BlacklistPage(model: model)
                case .notifications: NotificationsPage(model: model)
                case .textSize: TextSizePage()
                case .language: LanguagePage()
                case .about: AboutPage()
                }
            }
            .padding(.top, 12)
            .padding(.bottom, tabBarSpace)
        }
        .scrollDismissesKeyboard(.interactively)
    }
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
            NavRow(icon: .textSize, title: t("Размер текста"), value: textScaleTitle(L10n.shared.textScale)) {
                model.openSettings(.textSize)
            }
            Divider()
            NavRow(icon: .globe, title: t("Язык"), value: L10n.shared.choice.title) { model.openSettings(.language) }
            Divider()
            NavRow(icon: .info, title: t("О приложении"), value: "Memory \(appVersion)") { model.openSettings(.about) }
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
             ? t("Записи и чек-ины видят только подписчики. Новые подписчики — по заявке: принять или отклонить её можно в профиле.")
             : t("Закройте профиль, чтобы записи и чек-ины видели только те, чью заявку вы приняли. Нынешние подписчики останутся."))
        SettingsCard {
            VStack(alignment: .leading, spacing: 16) {
                AudiencePicker(title: t("Кто видит сведения о себе"), subtitle: t("Город, день рождения, сайт и интересы"),
                               value: privacy.profileInfo) { v in update { $0.profileInfo = v } }
                FadeLine()
                AudiencePicker(title: t("Кто видит подписки и подписчиков"), value: privacy.followLists) { v in
                    update { $0.followLists = v }
                }
                FadeLine()
                AudiencePicker(title: t("Кто может написать мне"), value: privacy.messages) { v in
                    update { $0.messages = v }
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
        Hint(text: t("«Мои подписки» — люди, на которых подписаны вы. Чтобы скрыть от одного человека всё сразу, добавьте его в чёрный список."))
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
            SwitchRow(title: t("Лайки и комментарии"), isOn: n.reactions, enabled: n.all) { v in update { $0.reactions = v } }
            Divider()
            SwitchRow(title: t("Новые подписчики"), isOn: n.follows, enabled: n.all) { v in update { $0.follows = v } }
            Divider()
            SwitchRow(title: t("Группы: новые записи и участники"), isOn: n.groups, enabled: n.all) { v in update { $0.groups = v } }
            Divider()
            SwitchRow(title: t("Друзья: чек-ины, фильмы, книги, тренировки"), isOn: n.friends, enabled: n.all) { v in update { $0.friends = v } }
            Divider()
            SwitchRow(title: t("Итоги месяца и года"), isOn: n.recap, enabled: n.all) { v in update { $0.recap = v } }
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

// MARK: - О приложении

private struct AboutPage: View {
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
            VStack(alignment: .leading, spacing: 8) {
                Caption(text: t("Шрифты"))
                Bullet(text: t("Unbounded и Inter — под лицензией SIL Open Font License."))
            }
            .padding(16)
        }
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
        Text(t(text).uppercased()).textStyle(.label, Palette.mist)
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
