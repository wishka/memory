import SwiftUI

/// Вкладка «Истории»: записи только тех, на кого человек подписан.
///
/// Сверху ряд подписок в светящихся кольцах; нажатие оставляет записи
/// одного человека, повторное — снова всех. Долгое нажатие — его страница.
struct StoriesScreen: View {
    let model: AppModel
    @State private var only: Int?

    var body: some View {
        let state = model.state
        // Отписались от выбранного — фильтр снимается сам
        let selected = only.flatMap { state.following.contains($0) ? $0 : nil }
        let posts = state.stories.filter { selected == nil || $0.author.id == selected }

        ScrollView {
            LazyVStack(spacing: 14) {
                ScreenTitle(
                    title: "Истории",
                    subtitle: state.following.isEmpty ? "Пока ни одной подписки" : "Подписки: \(state.following.count)"
                )
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 20)
                .padding(.vertical, 8)

                if !state.followedPeople.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 14) {
                            ForEach(state.followedPeople) { person in
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

                if posts.isEmpty {
                    GlassCard {
                        VStack(alignment: .leading, spacing: 12) {
                            Text(state.following.isEmpty
                                 ? "Здесь будут записи людей, на которых вы подписаны. Подписаться можно прямо в ленте — кнопкой у имени автора."
                                 : "У ваших подписок пока нет записей.")
                                .textStyle(.body)
                                .fixedSize(horizontal: false, vertical: true)
                            GlowButton(title: "Открыть ленту") { model.selectTab(.feed) }
                        }
                        .padding(20)
                    }
                    .cardPadding()
                }

                ForEach(posts) { post in
                    PostCard(
                        post: post,
                        onLike: { model.toggleLike(post.id) },
                        following: true,
                        onFollow: { model.toggleFollow(post.author.id) },
                        onAuthor: { model.openPerson(post.author.id) },
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
        .accessibilityHint("Долгое нажатие — страница человека")
    }
}
