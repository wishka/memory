import SwiftUI

/// Страница другого человека: карточка, сведения, интересы, подписка,
/// «Написать» и его записи. Открывается нажатием на автора записи.
struct PersonScreen: View {
    let model: AppModel
    let person: User

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
        let posts = blocked ? [] : state.postsOf(person.id)
        let hasInfo = !blocked && (!person.city.isEmpty || !person.birthday.isEmpty || !person.website.isEmpty || !person.interests.isEmpty)

        return ScrollView {
            LazyVStack(spacing: 14) {
                HStack(spacing: 12) {
                    RoundGlyphButton(icon: .back) { model.closePerson() }
                    Text("@\(person.handle)").textStyle(.title)
                    Spacer()
                    // «⋯»: заблокировать или разблокировать
                    Menu {
                        if blocked {
                            Button(t("Разблокировать")) { model.unblock(person.id) }
                        } else {
                            Button(t("Заблокировать"), role: .destructive) { model.block(person.id) }
                        }
                    } label: {
                        Glyph(kind: .more, color: Palette.ink, size: 24)
                            .frame(width: 44, height: 44)
                            .glass(Circle())
                    }
                }
                .padding(.horizontal, 12)

                GlassCard {
                    VStack(spacing: 10) {
                        Avatar(user: person, size: 104, ring: true, showOnline: true)
                        Text(person.name.uppercased()).textStyle(.headline).multilineTextAlignment(.center)
                        Text(person.online ? t("в сети") : "@\(person.handle)")
                            .textStyle(.bodySmall, person.online ? Palette.lilac : Palette.mist)
                        if !person.bio.isEmpty {
                            Text(person.bio).textStyle(.body).multilineTextAlignment(.center)
                        }
                        FadeLine().padding(.top, 4)
                        // Счётчики — кнопки: записи, подписчики и подписки человека
                        HStack {
                            StatButton(value: posts.count, label: t("записи")) { model.openPersonSection(.posts) }
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

                SectionTitle(text: posts.isEmpty ? t("Записей пока нет") : tf("Записи · %s", posts.count))
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
