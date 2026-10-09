import SwiftUI

/// Записи, подписчики или подписки другого человека — по нажатию на
/// счётчик на его странице. Пока сервера нет, в списках видны только
/// знакомые люди; число на счётчике — настоящее.
struct PersonSectionScreen: View {
    let model: AppModel
    let person: User
    let section: ProfileSection

    var body: some View {
        let state = model.state
        let blocked = state.isBlocked(person.id)
        let posts = blocked ? [] : state.postsOf(person.id)
        let people: [User] = section == .followers ? state.followersOf(person)
            : section == .following ? state.followingOf(person) : []

        ScrollView {
            LazyVStack(spacing: 12) {
                HStack(spacing: 14) {
                    RoundGlyphButton(icon: .back) { model.closePersonSection() }
                    ScreenTitle(title: section.title, subtitle: "@\(person.handle) · \(subtitle(state, posts: posts.count))")
                    Spacer()
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 4)

                if section == .posts {
                    if posts.isEmpty { hint(t("Записей пока нет.")) }
                    ForEach(posts) { post in
                        PostCard(
                            post: post,
                            onLike: { model.toggleLike(post.id) },
                            onComments: { model.openComments(post.id) },
                            onOpenMedia: model.openMedia
                        )
                    }
                } else {
                    hint(t("Показаны люди, которых вы знаете в Memory."))
                    ForEach(people) { other in
                        let isMe = other.id == state.me.id
                        PersonRow(
                            person: other,
                            following: isMe || state.following.contains(other.id),
                            followLabel: t("Подписаться"),
                            onOpen: { model.openPerson(other.id) },
                            onToggle: { model.toggleFollow(other.id) },
                            showButton: !isMe
                        )
                    }
                }
            }
            .padding(.top, 12)
            .padding(.bottom, tabBarSpace)
        }
    }

    private func subtitle(_ state: UiState, posts: Int) -> String {
        switch section {
        case .posts: return plural(posts, "запись", "записи", "записей")
        case .followers:
            return plural(person.followers + (state.following.contains(person.id) ? 1 : 0), "подписчик", "подписчика", "подписчиков")
        default: return plural(person.followingCount, "подписка", "подписки", "подписок")
        }
    }

    private func hint(_ text: String) -> some View {
        Text(t(text))
            .textStyle(.bodySmall, Palette.mist)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 24)
            .padding(.vertical, 12)
    }
}
