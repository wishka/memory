package ru.wishka.memory.ui.stories

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.User
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.Tab
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.Avatar
import ru.wishka.memory.ui.components.GlassCard
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.ScreenTitle
import ru.wishka.memory.ui.feed.PostCard
import ru.wishka.memory.ui.theme.Palette

/**
 * Вкладка «Истории»: записи только тех, на кого человек подписан.
 *
 * Сверху ряд подписок в светящихся кольцах; нажатие оставляет записи
 * одного человека, повторное — снова всех.
 */
@Composable
fun StoriesScreen(vm: AppViewModel, state: UiState, contentPadding: PaddingValues) {
    var only by rememberSaveable { mutableStateOf<Int?>(null) }
    // Отписались от выбранного — фильтр снимается сам
    val selected = only?.takeIf { it in state.following }
    val posts = state.stories.filter { selected == null || it.author.id == selected }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenTitle(
                "Истории",
                if (state.following.isEmpty()) "Пока ни одной подписки"
                else "Подписки: ${state.following.size}",
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        if (state.followedPeople.isNotEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(state.followedPeople, key = { it.id }) { person ->
                        StoryRing(
                            person,
                            active = selected == null || selected == person.id,
                            onClick = { only = if (selected == person.id) null else person.id },
                            onLongClick = { vm.openPerson(person.id) },
                        )
                    }
                }
            }
        }

        if (posts.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            if (state.following.isEmpty()) {
                                "Здесь будут записи людей, на которых вы подписаны. " +
                                    "Подписаться можно прямо в ленте — кнопкой у имени автора."
                            } else {
                                "У ваших подписок пока нет записей."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = Palette.Ink,
                        )
                        GlowButton("Открыть ленту", onClick = { vm.selectTab(Tab.Feed) })
                    }
                }
            }
        }

        items(posts, key = { it.id }) { post ->
            PostCard(
                post,
                onLike = { vm.toggleLike(post.id) },
                following = true,
                onFollow = { vm.toggleFollow(post.author.id) },
                onAuthor = { vm.openPerson(post.author.id) },
                onComments = { vm.openComments(post.id) },
                onOpenMedia = vm::openMedia,
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

/**
 * Аватар в кольце-градиенте, как у историй; неактивные приглушены.
 * Нажатие — записи только этого человека, долгое нажатие — его страница.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoryRing(person: User, active: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .alpha(if (active) 1f else 0.45f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .border(
                    2.dp,
                    Brush.sweepGradient(listOf(Palette.Lilac, Palette.Orchid, Palette.Sky, Palette.Lilac)),
                    CircleShape,
                )
                .padding(4.dp),
        ) {
            Avatar(person, size = 58.dp, showOnline = true)
        }
        Text(
            person.name.substringBefore(' '),
            style = MaterialTheme.typography.labelLarge,
            color = Palette.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
