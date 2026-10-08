import SwiftUI

/// Страница другого человека: карточка, сведения, интересы, подписка,
/// «Написать» и его записи. Открывается нажатием на автора записи.
struct PersonScreen: View {
    let model: AppModel
    let person: User

    var body: some View {
        let state = model.state
        let following = state.following.contains(person.id)
        let posts = state.postsOf(person.id)
        let hasInfo = !person.city.isEmpty || !person.birthday.isEmpty || !person.website.isEmpty || !person.interests.isEmpty

        ScrollView {
            LazyVStack(spacing: 14) {
                HStack(spacing: 12) {
                    RoundGlyphButton(icon: .back) { model.closePerson() }
                    Text("@\(person.handle)").textStyle(.title)
                    Spacer()
                }
                .padding(.horizontal, 12)

                GlassCard {
                    VStack(spacing: 10) {
                        Avatar(user: person, size: 104, ring: true, showOnline: true)
                        Text(person.name.uppercased()).textStyle(.headline).multilineTextAlignment(.center)
                        Text(person.online ? "в сети" : "@\(person.handle)")
                            .textStyle(.bodySmall, person.online ? Palette.lilac : Palette.mist)
                        if !person.bio.isEmpty {
                            Text(person.bio).textStyle(.body).multilineTextAlignment(.center)
                        }
                        FadeLine().padding(.top, 4)
                        HStack {
                            Stat(value: posts.count, label: "записи")
                            Stat(value: person.followers + (following ? 1 : 0), label: "подписчики")
                            Stat(value: person.followingCount, label: "подписки")
                        }
                        .padding(.top, 6)
                        HStack(spacing: 10) {
                            if following {
                                GhostButton(title: "Вы подписаны") { model.toggleFollow(person.id) }
                            } else {
                                GlowButton(title: "Подписаться") { model.toggleFollow(person.id) }
                            }
                            GhostButton(title: "Написать") { model.writeTo(person.id) }
                        }
                        .padding(.top, 6)
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

                SectionTitle(text: posts.isEmpty ? "Записей пока нет" : "Записи · \(posts.count)")
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
