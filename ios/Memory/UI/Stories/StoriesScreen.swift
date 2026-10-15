import SwiftUI

/// Вкладка «Истории»: истории друзей на сутки, эфиры и записи друзей.
///
/// Сверху ряд кружков: своя история (с «+»), эфир, эфиры друзей и
/// истории друзей. Ниже — «Записи друзей» с фильтром по человеку.
struct StoriesScreen: View {
    let model: AppModel
    @State private var only: Int?

    var body: some View {
        let state = model.state
        // Выбранный больше не друг — фильтр снимается сам
        let selected = only.flatMap { state.following.contains($0) ? $0 : nil }
        let posts = state.friendPosts.filter { selected == nil || $0.author.id == selected }

        ScrollView {
            LazyVStack(spacing: 14) {
                ScreenTitle(
                    title: t("Истории"),
                    subtitle: state.friends.isEmpty ? t("Пока нет друзей") : plural(state.friends.count, "друг", "друга", "друзей")
                )
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 20)
                .padding(.vertical, 8)

                // Истории (сутки), свой эфир и эфиры друзей — одним рядом кружков
                StoriesRow(model: model)

                // Под историями — записи друзей; фильтр по человеку
                if !state.friends.isEmpty {
                    SectionTitle(text: t("Записи друзей"))
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            Chip(text: t("Все"), active: selected == nil) { only = nil }
                            ForEach(state.friends) { person in
                                Chip(text: person.firstName, active: selected == person.id) {
                                    only = selected == person.id ? nil : person.id
                                }
                            }
                        }
                        .padding(.horizontal, 16)
                    }
                }

                if posts.isEmpty {
                    GlassCard {
                        VStack(alignment: .leading, spacing: 12) {
                            Text(state.following.isEmpty
                                 ? t("Здесь будут записи ваших друзей. ") + t("Добавить в друзья можно прямо в ленте — кнопкой у имени автора.")
                                 : t("У ваших друзей пока нет записей."))
                                .textStyle(.body)
                                .fixedSize(horizontal: false, vertical: true)
                            GlowButton(title: t("Открыть ленту")) { model.selectTab(.feed) }
                        }
                        .padding(20)
                    }
                    .cardPadding()
                }

                ForEach(posts) { post in
                    PostCard(
                        post: post,
                        onLike: { model.toggleLike(post.id) },
                        friend: post.author.id == state.me.id ? nil : state.friendState(post.author.id),
                        onFriend: { model.toggleFriend(post.author.id) },
                        onAuthor: { model.openPerson(post.author.id) },
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
    }
}
