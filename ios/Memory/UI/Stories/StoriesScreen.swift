import SwiftUI

/// Вкладка «Истории»: свои записи и записи друзей.
///
/// Сверху ряд друзей в светящихся кольцах; нажатие оставляет записи
/// одного человека, повторное — снова всех. Долгое нажатие — его страница.
struct StoriesScreen: View {
    let model: AppModel
    @State private var only: Int?

    var body: some View {
        let state = model.state
        // Выбранный больше не друг — фильтр снимается сам
        let selected = only.flatMap { state.following.contains($0) ? $0 : nil }
        let posts = state.stories.filter { selected == nil || $0.author.id == selected }

        ScrollView {
            LazyVStack(spacing: 14) {
                ScreenTitle(
                    title: t("Истории"),
                    subtitle: state.friends.isEmpty ? t("Пока нет друзей") : plural(state.friends.count, "друг", "друга", "друзей")
                )
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 20)
                .padding(.vertical, 8)

                if !state.friends.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 14) {
                            ForEach(state.friends) { person in
                                StoryRing(
                                    person: person,
                                    active: selected == nil || selected == person.id,
                                    onTap: { only = selected == person.id ? nil : person.id },
                                    onLongPress: { model.openPerson(person.id) }
                                )
                            }
                        }
                        .padding(.horizontal, 16)
                    }
                }

                // Своя история: те же чек-ины, стикеры и фото, что и в ленте
                PostComposer(model: model, placeholder: t("Что у вас сейчас?"), hintText: t("Видно в Историях и в Ленте"))

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

/// Аватар в кольце-градиенте, как у историй; неактивные приглушены.
private struct StoryRing: View {
    let person: User
    let active: Bool
    let onTap: () -> Void
    let onLongPress: () -> Void

    var body: some View {
        VStack(spacing: 6) {
            Avatar(user: person, size: 58, showOnline: true)
                .padding(4)
                .overlay(
                    Circle().strokeBorder(
                        AngularGradient(colors: [Palette.lilac, Palette.orchid, Palette.sky, Palette.lilac], center: .center),
                        lineWidth: 2
                    )
                )
            Text(person.firstName)
                .textStyle(.labelLarge)
                .lineLimit(1)
                .truncationMode(.tail)
        }
        .frame(width: 72)
        .opacity(active ? 1 : 0.45)
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
        .onLongPressGesture(perform: onLongPress)
        .accessibilityAddTraits(.isButton)
        .accessibilityHint(t("Долгое нажатие — страница человека"))
    }
}
