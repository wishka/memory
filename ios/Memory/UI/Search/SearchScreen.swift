import SwiftUI

/// Поиск по Memory: люди, записи, группы и #теги. Пустой запрос —
/// недавние запросы и популярные теги. «#тег» — записи с тегом,
/// «@логин» — люди. То же — SearchScreen.kt.
struct SearchScreen: View {
    let model: AppModel
    @FocusState private var focused: Bool

    private var query: Binding<String> {
        Binding(get: { model.state.searchQuery }, set: { model.setSearchQuery($0) })
    }

    var body: some View {
        let state = model.state
        let q = state.searchQuery
        let blank = q.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        ScrollView {
            LazyVStack(spacing: 12) {
                HStack(spacing: 10) {
                    RoundGlyphButton(icon: .back) { model.closeSearch() }
                    GlassField(placeholder: t("Люди, записи, #теги"), text: query, limit: 80, singleLine: true)
                        .glass(Capsule())
                        .focused($focused)
                        .submitLabel(.search)
                        .onSubmit { model.commitSearch() }
                    if !q.isEmpty {
                        RoundGlyphButton(icon: .close, color: Palette.mist) { model.setSearchQuery("") }
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 4)

                if blank {
                    emptyQuery(state)
                } else {
                    results(state, q)
                }
            }
            .padding(.top, 12)
            .padding(.bottom, tabBarSpace)
        }
        .scrollDismissesKeyboard(.interactively)
        .onAppear { if state.searchQuery.isEmpty { focused = true } }
    }

    @ViewBuilder
    private func emptyQuery(_ state: UiState) -> some View {
        let popular = Search.popularTags(state.searchablePosts)
        let suggest = state.searchablePeople.filter { !state.following.contains($0.id) }
        if !state.searchHistory.isEmpty {
            HStack {
                SearchSection(title: t("Недавние"))
                Spacer()
                Button { model.clearSearchHistory() } label: {
                    Text(t("Очистить")).textStyle(.bodySmall, Palette.lilac)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 24)
            FlowLayout(spacing: 8) {
                ForEach(state.searchHistory, id: \.self) { item in
                    Chip(text: item, active: false) { model.setSearchQuery(item) }
                }
            }
            .padding(.horizontal, 16)
        }
        if !popular.isEmpty {
            SearchSection(title: t("Популярные теги")).padding(.horizontal, 24)
            FlowLayout(spacing: 8) {
                ForEach(popular) { tc in
                    Chip(text: "#\(tc.tag) · \(tc.count)", active: false) { model.openTag(tc.tag) }
                }
            }
            .padding(.horizontal, 16)
        }
        if !suggest.isEmpty {
            SearchSection(title: t("Возможно, вы знакомы")).padding(.horizontal, 24)
            ForEach(suggest) { user in
                SearchPersonRow(user: user) {
                    model.commitSearch()
                    model.openPerson(user.id)
                }
            }
        }
        Text(t("Совет: нажмите на #тег или @имя в любой записи — откроется поиск или страница человека."))
            .textStyle(.bodySmall, Palette.mist)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 24)
            .padding(.vertical, 8)
    }

    @ViewBuilder
    private func results(_ state: UiState, _ q: String) -> some View {
        let results = state.searchResults
        let activeTag = (q.hasPrefix("#") ? String(q.dropFirst()) : q).lowercased()
        if results.empty {
            Text(t("Ничего не нашлось. Попробуйте другое слово или #тег."))
                .textStyle(.bodySmall, Palette.mist)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 24)
                .padding(.vertical, 16)
        }
        if !results.people.isEmpty {
            SearchSection(title: t("Люди")).padding(.horizontal, 24)
            ForEach(results.people) { user in
                SearchPersonRow(user: user) {
                    model.commitSearch()
                    model.openPerson(user.id)
                }
            }
        }
        if !results.tags.isEmpty {
            SearchSection(title: t("Теги")).padding(.horizontal, 24)
            FlowLayout(spacing: 8) {
                ForEach(results.tags) { tc in
                    Chip(text: "#\(tc.tag) · \(tc.count)", active: activeTag == tc.tag.lowercased()) { model.openTag(tc.tag) }
                }
            }
            .padding(.horizontal, 16)
        }
        if !results.groups.isEmpty {
            SearchSection(title: t("Группы")).padding(.horizontal, 24)
            ForEach(results.groups) { g in
                Button {
                    model.commitSearch()
                    model.openGroup(g.id)
                } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(g.name).textStyle(.title).lineLimit(1)
                        Text(t(g.topic) + " · " + plural(g.members, "участник", "участника", "участников"))
                            .textStyle(.bodySmall, Palette.mist)
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .glass(radius: 18)
                    .contentShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 16)
            }
        }
        if !results.posts.isEmpty {
            SearchSection(title: t("Записи")).padding(.horizontal, 24)
            ForEach(results.posts) { post in
                PostCard(
                    post: post,
                    onLike: { model.likeAny(post.id) },
                    onAuthor: {
                        model.commitSearch()
                        model.openPerson(post.author.id)
                    },
                    onComments: {
                        model.commitSearch()
                        model.openComments(post.id)
                    },
                    onOpenMedia: model.openMedia,
                    onVote: { model.votePost(post.id, $0) },
                    onReport: model.isMine(post) ? nil : { model.reportPost(post) }
                )
            }
        }
    }
}

private struct SearchSection: View {
    let title: String

    var body: some View {
        Text(title.uppercased())
            .textStyle(.label, Palette.mist)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityAddTraits(.isHeader)
    }
}

private struct SearchPersonRow: View {
    let user: User
    let onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            HStack(spacing: 12) {
                Avatar(user: user, size: 44, showOnline: true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(user.name).textStyle(.title).lineLimit(1)
                    Text(["@" + user.handle, user.city].filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }.joined(separator: " · "))
                        .textStyle(.bodySmall, Palette.mist)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 6)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 16)
    }
}
