import SwiftUI

/// Страница другого человека: карточка, сведения, интересы, дружба,
/// «Написать» и его записи. Открывается нажатием на автора записи.
struct PersonScreen: View {
    let model: AppModel
    let person: User
    @State private var showMenu = false
    @State private var sharing: SharePayload?
    @State private var notice: String?

    var body: some View {
        if let section = model.state.personSection {
            PersonSectionScreen(model: model, person: person, section: section)
        } else {
            page
        }
    }

    private var page: some View {
        let state = model.state
        let friend = state.friendState(person.id)
        let blocked = state.isBlocked(person.id)
        // Закрытый профиль: записи видны только друзьям
        let hidden = !state.canSee(person)
        let requested = friend == .requested
        let posts = blocked || hidden ? [] : state.postsOf(person.id)
        let hasInfo = !blocked && (!person.city.isEmpty || !person.birthday.isEmpty || !person.website.isEmpty || !person.interests.isEmpty)

        return ScrollView {
            LazyVStack(spacing: 14) {
                HStack(spacing: 12) {
                    RoundGlyphButton(icon: .back) { model.closePerson() }
                    Text("@\(person.handle)").textStyle(.title)
                        .accessibilityAddTraits(.isHeader)
                    Spacer()
                    // «⋯»: ссылка на профиль, заблокировать или разблокировать, пожаловаться
                    RoundGlyphButton(icon: .more) { showMenu = true }
                        .glassActions($showMenu, title: person.name, actions: [
                            GlassAction(icon: .share, title: t("Поделиться профилем")) {
                                sharing = SharePayload(items: [person.name + "\n" + Links.profile(person.handle)])
                            },
                            GlassAction(icon: .link, title: t("Скопировать ссылку")) {
                                copyText(Links.profile(person.handle))
                                notice = t("Ссылка скопирована")
                            },
                            blocked
                                ? GlassAction(icon: .block, title: t("Разблокировать")) { model.unblock(person.id) }
                                : GlassAction(icon: .block, title: t("Заблокировать"), destructive: true) { model.block(person.id) },
                            GlassAction(icon: .flag, title: t("Пожаловаться"), destructive: true) { model.reportPerson(person) },
                        ])
                }
                .padding(.horizontal, 12)

                GlassCard {
                    VStack(spacing: 10) {
                        Avatar(user: person, size: 104, ring: true, showOnline: true)
                        Text(person.name.uppercased()).textStyle(.headline).multilineTextAlignment(.center)
                        if person.closed {
                            HStack(spacing: 6) {
                                Glyph(kind: .lock, color: Palette.lilac, size: 16)
                                Text(t("Закрытый профиль")).textStyle(.labelLarge, Palette.lilac)
                            }
                        }
                        Text(person.online ? t("в сети") : "@\(person.handle)")
                            .textStyle(.bodySmall, person.online ? Palette.lilac : Palette.mist)
                        if !person.bio.isEmpty {
                            RichText(text: person.bio).multilineTextAlignment(.center)
                        }
                        FadeLine().padding(.top, 4)
                        // Счётчики — кнопки: записи и друзья человека
                        HStack {
                            StatButton(value: hidden ? state.postsOf(person.id).count : posts.count, label: t("записи")) {
                                if !hidden { model.openPersonSection(.posts) }
                            }
                            StatButton(value: state.friendsOf(person).count, label: t("друзья")) {
                                model.openPersonSection(.friends)
                            }
                        }
                        .padding(.top, 6)
                        if blocked {
                            Text(t("Вы заблокировали этого человека: его записи и сообщения вам не видны."))
                                .textStyle(.bodySmall, Palette.mist)
                                .multilineTextAlignment(.center)
                            GhostButton(title: t("Разблокировать")) { model.unblock(person.id) }
                        } else {
                            // Человек сам прислал заявку — принять или отклонить
                            if friend == .incoming {
                                Text(t("Хочет добавить вас в друзья"))
                                    .textStyle(.bodySmall, Palette.lilac)
                                    .padding(.top, 6)
                                HStack(spacing: 10) {
                                    GlowButton(title: t("Принять заявку")) { model.approveRequest(person.id) }
                                    GhostButton(title: t("Отклонить")) { model.declineRequest(person.id) }
                                }
                            }
                            HStack(spacing: 10) {
                                if friend != .incoming {
                                    FriendButton(state: friend, name: person.name) { model.toggleFriend(person.id) }
                                }
                                GhostButton(title: t("Написать")) { model.writeTo(person.id) }
                            }
                            .padding(.top, 6)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 24)
                    .padding(.horizontal, 16)
                }
                .cardPadding()

                if hasInfo {
                    GlassCard {
                        VStack(alignment: .leading, spacing: 10) {
                            UserInfo(user: person)
                            InterestChips(interests: person.interests)
                        }
                        .padding(16)
                    }
                    .cardPadding()
                }

                // «Актуальное» человека — видно тем, кто видит его истории (друзьям)
                if !hidden && !blocked && state.following.contains(person.id) {
                    HighlightsRow(model: model, ownerId: person.id)
                }

                if hidden && !blocked {
                    GlassCard {
                        HStack(spacing: 12) {
                            Glyph(kind: .lock, color: Palette.lilac, size: 28)
                            Text(requested
                                 ? t("Заявка отправлена. Когда её примут, здесь появятся записи и чек-ины.")
                                 : t("Это закрытый профиль. Записи и чек-ины видят только друзья — добавьте человека в друзья."))
                                .textStyle(.bodySmall)
                                .fixedSize(horizontal: false, vertical: true)
                            Spacer(minLength: 0)
                        }
                        .padding(16)
                    }
                    .cardPadding()
                } else {
                    SectionTitle(text: posts.isEmpty ? t("Записей пока нет") : tf("Записи · %s", posts.count))
                }
                ForEach(posts) { post in
                    PostCard(
                        post: post,
                        onLike: { model.toggleLike(post.id) },
                        onComments: { model.openComments(post.id) },
                        onOpenMedia: model.openMedia,
                        onVote: { model.votePost(post.id, $0) },
                        onClosePoll: model.isMine(post) ? { model.closePostPoll(post.id) } : nil,
                        onReport: model.isMine(post) ? nil : { model.reportPost(post) }
                    )
                }
            }
            .padding(.top, 12)
            .padding(.bottom, tabBarSpace)
        }
        .shareSheet($sharing)
        .toast($notice)
    }
}
