import SwiftUI

/// Страница другого человека: карточка, сведения, интересы, подписка,
/// «Написать» и его записи. Открывается нажатием на автора записи.
struct PersonScreen: View {
    let model: AppModel
    let person: User
    @State private var showMenu = false

    var body: some View {
        if let section = model.state.personSection {
            PersonSectionScreen(model: model, person: person, section: section)
        } else {
            page
        }
    }

    private var page: some View {
        let state = model.state
        let following = state.following.contains(person.id)
        let blocked = state.isBlocked(person.id)
        // Закрытый профиль: записи видны только подписчикам
        let hidden = !state.canSee(person)
        let requested = state.sentRequests.contains(person.id)
        let posts = blocked || hidden ? [] : state.postsOf(person.id)
        let hasInfo = !blocked && (!person.city.isEmpty || !person.birthday.isEmpty || !person.website.isEmpty || !person.interests.isEmpty)

        return ScrollView {
            LazyVStack(spacing: 14) {
                HStack(spacing: 12) {
                    RoundGlyphButton(icon: .back) { model.closePerson() }
                    Text("@\(person.handle)").textStyle(.title)
                    Spacer()
                    // «⋯»: заблокировать или разблокировать
                    RoundGlyphButton(icon: .more) { showMenu = true }
                        .glassActions($showMenu, title: person.name, actions: [
                            blocked
                                ? GlassAction(icon: .block, title: t("Разблокировать")) { model.unblock(person.id) }
                                : GlassAction(icon: .block, title: t("Заблокировать"), destructive: true) { model.block(person.id) },
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
                            Text(person.bio).textStyle(.body).multilineTextAlignment(.center)
                        }
                        FadeLine().padding(.top, 4)
                        // Счётчики — кнопки: записи, подписчики и подписки человека
                        HStack {
                            StatButton(value: hidden ? state.postsOf(person.id).count : posts.count, label: t("записи")) {
                                if !hidden { model.openPersonSection(.posts) }
                            }
                            StatButton(value: person.followers + (following ? 1 : 0), label: t("подписчики")) {
                                model.openPersonSection(.followers)
                            }
                            StatButton(value: person.followingCount, label: t("подписки")) { model.openPersonSection(.following) }
                        }
                        .padding(.top, 6)
                        if blocked {
                            Text(t("Вы заблокировали этого человека: его записи и сообщения вам не видны."))
                                .textStyle(.bodySmall, Palette.mist)
                                .multilineTextAlignment(.center)
                            GhostButton(title: t("Разблокировать")) { model.unblock(person.id) }
                        } else {
                            HStack(spacing: 10) {
                                if following {
                                    GhostButton(title: t("Вы подписаны")) { model.toggleFollow(person.id) }
                                } else if requested {
                                    GhostButton(title: t("Заявка отправлена")) { model.toggleFollow(person.id) }
                                } else if person.closed {
                                    GlowButton(title: t("Отправить заявку")) { model.toggleFollow(person.id) }
                                } else {
                                    GlowButton(title: t("Подписаться")) { model.toggleFollow(person.id) }
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

                if hidden && !blocked {
                    GlassCard {
                        HStack(spacing: 12) {
                            Glyph(kind: .lock, color: Palette.lilac, size: 28)
                            Text(requested
                                 ? t("Заявка отправлена. Когда её примут, здесь появятся записи и чек-ины.")
                                 : t("Это закрытый профиль. Записи и чек-ины видят только подписчики — отправьте заявку."))
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
                        onOpenMedia: model.openMedia
                    )
                }
            }
            .padding(.top, 12)
            .padding(.bottom, tabBarSpace)
        }
    }
}
