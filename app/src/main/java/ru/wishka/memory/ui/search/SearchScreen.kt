package ru.wishka.memory.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.Search
import ru.wishka.memory.data.User
import ru.wishka.memory.data.plural
import ru.wishka.memory.i18n.t
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.Avatar
import ru.wishka.memory.ui.components.Chip
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.feed.PostCard
import ru.wishka.memory.ui.profile.RoundIconButton
import ru.wishka.memory.ui.theme.Palette

/**
 * Поиск по Memory: люди, записи, группы и #теги. Пустой запрос —
 * недавние запросы и популярные теги. «#тег» — записи с тегом,
 * «@логин» — люди.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(vm: AppViewModel, state: UiState, contentPadding: PaddingValues) {
    val query = state.searchQuery
    val results = remember(query, state.posts, state.groups, state.blocked, state.following) { state.searchResults }
    val popular = remember(state.posts, state.groups) { Search.popularTags(state.searchablePosts) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(LineIcon.Back, Palette.Ink) { vm.closeSearch() }
                Spacer(Modifier.width(10.dp))
                GlassField(
                    query, vm::setSearchQuery, t("Люди, записи, #теги"),
                    singleLine = true,
                    modifier = Modifier.weight(1f).glass(RoundedCornerShape(50)),
                )
                if (query.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    RoundIconButton(LineIcon.Close, Palette.Mist) { vm.setSearchQuery("") }
                }
            }
        }
        if (query.isBlank()) {
            if (state.searchHistory.isNotEmpty()) {
                item {
                    Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Section(t("Недавние"), Modifier.weight(1f))
                        Text(
                            t("Очистить"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.Lilac,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button) { vm.clearSearchHistory() },
                        )
                    }
                }
                item {
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.searchHistory.forEach { q -> Chip(q, active = false) { vm.setSearchQuery(q) } }
                    }
                }
            }
            if (popular.isNotEmpty()) {
                item { Section(t("Популярные теги"), Modifier.padding(horizontal = 24.dp)) }
                item {
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        popular.forEach { (tag, n) -> Chip("#$tag · $n", active = false) { vm.openTag(tag) } }
                    }
                }
            }
            val suggest = state.searchablePeople.filter { it.id !in state.following }
            if (suggest.isNotEmpty()) {
                item { Section(t("Возможно, вы знакомы"), Modifier.padding(horizontal = 24.dp)) }
                items(suggest, key = { "s" + it.id }) { PersonRow(vm, it) }
            }
            item {
                Text(
                    t("Совет: нажмите на #тег или @имя в любой записи — откроется поиск или страница человека."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            return@LazyColumn
        }
        if (results.empty) {
            item {
                Text(
                    t("Ничего не нашлось. Попробуйте другое слово или #тег."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        }
        if (results.people.isNotEmpty()) {
            item { Section(t("Люди"), Modifier.padding(horizontal = 24.dp)) }
            items(results.people, key = { "p" + it.id }) { PersonRow(vm, it) }
        }
        if (results.tags.isNotEmpty()) {
            item { Section(t("Теги"), Modifier.padding(horizontal = 24.dp)) }
            item {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    results.tags.forEach { (tag, n) -> Chip("#$tag · $n", active = query.removePrefix("#").equals(tag, true)) { vm.openTag(tag) } }
                }
            }
        }
        if (results.groups.isNotEmpty()) {
            item { Section(t("Группы"), Modifier.padding(horizontal = 24.dp)) }
            items(results.groups, key = { "g" + it.id }) { g ->
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .glass(RoundedCornerShape(18.dp))
                        .clickable(role = Role.Button) { vm.commitSearch(); vm.openGroup(g.id) }
                        .padding(14.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(g.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            t(g.topic) + " · " + plural(g.members, "участник", "участника", "участников"),
                            style = MaterialTheme.typography.bodyMedium, color = Palette.Mist,
                        )
                    }
                }
            }
        }
        if (results.posts.isNotEmpty()) {
            item { Section(t("Записи"), Modifier.padding(horizontal = 24.dp)) }
            items(results.posts, key = { "post" + it.id }) { post ->
                PostCard(
                    post,
                    onLike = { vm.likeAny(post.id) },
                    onAuthor = { vm.commitSearch(); vm.openPerson(post.author.id) },
                    onComments = { vm.commitSearch(); vm.openComments(post.id) },
                    onOpenMedia = vm::openMedia,
                    onVote = { vm.votePost(post.id, it) },
                    onReport = if (vm.isMine(post)) null else ({ vm.reportPost(post) }),
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, modifier: Modifier = Modifier) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Palette.Mist,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
private fun PersonRow(vm: AppViewModel, user: User) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button) { vm.commitSearch(); vm.openPerson(user.id) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box { Avatar(user, size = 44.dp, showOnline = true) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(user.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf("@" + user.handle, user.city).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, maxLines = 1,
            )
        }
    }
}
