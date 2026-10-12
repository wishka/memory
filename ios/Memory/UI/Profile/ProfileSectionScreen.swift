import SwiftUI

/// Раздел профиля на отдельной странице: свои записи, подписчики,
/// подписки, группы или сохранённые PRISM. Открывается нажатием на
/// счётчик в профиле; «назад» возвращает в профиль.
struct ProfileSectionScreen: View {
    let model: AppModel
    let section: ProfileSection

    var body: some View {
        let state = model.state
        ScrollView {
            LazyVStack(spacing: 12) {
                HStack(spacing: 14) {
                    RoundGlyphButton(icon: .back) { model.closeProfileSection() }
                    ScreenTitle(title: section.title, subtitle: subtitle(state))
                    Spacer()
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 4)

                content(state)
            }
            .padding(.top, 12)
            .padding(.bottom, tabBarSpace)
        }
    }

    private func subtitle(_ state: UiState) -> String {
        switch section {
        case .posts: return plural(state.myPosts.count, "запись", "записи", "записей")
        case .followers: return plural(state.followers.count, "подписчик", "подписчика", "подписчиков")
        case .following: return plural(state.followedPeople.count, "подписка", "подписки", "подписок")
        case .groups: return plural(state.myGroups.count, "группа", "группы", "групп")
        case .saved: return plural(state.savedPrisms.count, "ролик", "ролика", "роликов")
        case .music: return plural(state.tracks.count, "трек", "трека", "треков")
        case .stickers: return plural(state.stickers.count, "стикер", "стикера", "стикеров")
        case .recap: return ""
        case .shelf: return ""
        case .requests: return plural(state.followRequests.count, "заявка", "заявки", "заявок")
        }
    }

    @ViewBuilder
    private func content(_ state: UiState) -> some View {
        switch section {
        case .posts:
            if state.myPosts.isEmpty { empty(t("Пока ни одной записи. Поделитесь первым моментом во вкладке «Лента».")) }
            ForEach(state.myPosts) { post in
                PostCard(
                    post: post,
                    onLike: { model.toggleLike(post.id) },
                    onComments: { model.openComments(post.id) },
                    onOpenMedia: model.openMedia,
                    onVote: { model.votePost(post.id, $0) },
                    onClosePoll: model.isMine(post) ? { model.closePostPoll(post.id) } : nil
                )
            }
        case .followers:
            if state.followers.isEmpty { empty(t("Пока на вас никто не подписан.")) }
            ForEach(state.followers) { person in
                PersonRow(
                    person: person,
                    following: state.following.contains(person.id),
                    followLabel: t("Подписаться в ответ"),
                    onOpen: { model.openPerson(person.id) },
                    onToggle: { model.toggleFollow(person.id) }
                )
            }
        case .following:
            if state.followedPeople.isEmpty { empty(t("Вы пока ни на кого не подписаны. Подписаться можно в ленте — кнопкой у имени автора.")) }
            ForEach(state.followedPeople) { person in
                PersonRow(
                    person: person,
                    following: true,
                    followLabel: t("Подписаться"),
                    onOpen: { model.openPerson(person.id) },
                    onToggle: { model.toggleFollow(person.id) }
                )
            }
        case .groups:
            if state.myGroups.isEmpty { empty(t("Вы пока ни в одной группе. Найти группу можно во вкладке «Группы».")) }
            ForEach(state.myGroups) { group in
                GroupLine(group: group) { model.openGroup(group.id) }
            }
        case .saved:
            if state.savedPrisms.isEmpty { empty(t("Здесь будут PRISM, которые вы сохранили кнопкой «Сохранить».")) }
            // Сохранённые ролики — плитками по две в ряд
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 16) {
                ForEach(state.savedPrisms) { prism in
                    SavedTile(prism: prism) { model.openPrism(prism.id) }
                }
            }
            .padding(.horizontal, 16)
        case .requests:
            empty(state.privacy.closedProfile
                  ? t("Профиль закрыт: подписаться на вас можно только по заявке. Одобренные увидят ваши записи и чек-ины.")
                  : t("Профиль открыт — подписаться может любой. Закрыть его можно в настройках приватности."))
            if state.requestPeople.isEmpty { empty(t("Новых заявок нет.")) }
            ForEach(state.requestPeople) { person in
                RequestRow(
                    person: person,
                    onOpen: { model.openPerson(person.id) },
                    onApprove: { model.approveRequest(person.id) },
                    onDecline: { model.declineRequest(person.id) }
                )
            }
        case .music, .stickers, .recap, .shelf:
            // «Моя музыка», «Студия», «Итоги» и «Полка» — свои экраны (MainScreen), сюда не попадают
            EmptyView()
        }
    }

    private func empty(_ text: String) -> some View {
        Text(text)
            .textStyle(.bodySmall, Palette.mist)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 24)
            .padding(.vertical, 12)
    }
}

/// Человек в списке: аватар, имя, подписка. Нажатие — его страница.
struct PersonRow: View {
    let person: User
    let following: Bool
    let followLabel: String
    let onOpen: () -> Void
    let onToggle: () -> Void
    var showButton: Bool = true

    var body: some View {
        GlassCard {
            HStack(spacing: 12) {
                Button(action: onOpen) {
                    HStack(spacing: 12) {
                        Avatar(user: person, size: 48, showOnline: true)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(person.name).textStyle(.title).lineLimit(1)
                            Text(person.city.isEmpty ? "@\(person.handle)" : "@\(person.handle) · \(person.city)")
                                .textStyle(.bodySmall, Palette.mist)
                                .lineLimit(1)
                        }
                        Spacer(minLength: 0)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                if showButton {
                    if following {
                        GhostButton(title: t("Вы подписаны"), action: onToggle)
                    } else {
                        GlowButton(title: followLabel, action: onToggle)
                    }
                }
            }
            .padding(12)
        }
        .cardPadding()
    }
}

private struct GroupLine: View {
    let group: MemoryGroup
    let onOpen: () -> Void

    var body: some View {
        GlassCard {
            Button(action: onOpen) {
                HStack(spacing: 12) {
                    GroupCover(group: group, maxPixel: 240)
                        .frame(width: 56, height: 56)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(group.name).textStyle(.title).lineLimit(1)
                        Text("\(t(group.topic)) · \(plural(group.members, "участник", "участника", "участников"))")
                            .textStyle(.bodySmall, Palette.mist)
                            .lineLimit(1)
                    }
                    Spacer(minLength: 0)
                    if group.mine {
                        Text(t("ВАША")).textStyle(.label, Palette.lilac)
                    }
                }
                .padding(12)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .cardPadding()
    }
}

private struct SavedTile: View {
    let prism: Prism
    let onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            VStack(alignment: .leading, spacing: 4) {
                ZStack {
                    if let cover = prism.cover {
                        MediaTile(attachment: cover, maxPixel: 480)
                    } else {
                        PrismArt(spec: PrismSpec(seed: prism.seed), timeMs: 1_500)
                    }
                    Glyph(kind: .play, color: Palette.ink, size: 30)
                }
                .frame(height: 240)
                .frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                Text(prism.caption).textStyle(.labelLarge).lineLimit(2)
                Text("@\(prism.author.handle)").textStyle(.bodySmall, Palette.mist).lineLimit(1)
            }
        }
        .buttonStyle(.plain)
    }
}

/// Заявка на подписку: принять или отклонить.
private struct RequestRow: View {
    let person: User
    let onOpen: () -> Void
    let onApprove: () -> Void
    let onDecline: () -> Void

    var body: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 10) {
                Button(action: onOpen) {
                    HStack(spacing: 12) {
                        Avatar(user: person, size: 48)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(person.name).textStyle(.title).lineLimit(1)
                            Text("@\(person.handle)").textStyle(.bodySmall, Palette.mist).lineLimit(1)
                        }
                        Spacer(minLength: 0)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                HStack(spacing: 10) {
                    GlowButton(title: t("Принять"), action: onApprove)
                    GhostButton(title: t("Отклонить"), action: onDecline)
                }
            }
            .padding(12)
        }
        .cardPadding()
    }
}
