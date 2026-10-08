package ru.wishka.memory.ui.feed

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ru.wishka.memory.R
import ru.wishka.memory.data.Post
import ru.wishka.memory.data.agoTitle
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.Avatar
import ru.wishka.memory.ui.components.GlassCard
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.Nebula
import ru.wishka.memory.ui.components.ScreenTitle
import ru.wishka.memory.ui.theme.Palette

/** Вкладка «Лента»: поле новой записи и записи друзей. */
@Composable
fun FeedScreen(vm: AppViewModel, state: UiState, contentPadding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScreenTitle("Лента", "Моменты, которые остаются", Modifier.weight(1f))
                // Кристалл из значка приложения — знак Memory в шапке
                Image(
                    painter = painterResource(R.drawable.memory_crystal),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                )
            }
        }
        item { Composer(onPublish = vm::publish) }
        items(state.posts, key = { it.id }) { post ->
            PostCard(
                post,
                onLike = { vm.toggleLike(post.id) },
                following = if (post.author.id == state.me.id) null else post.author.id in state.following,
                onFollow = { vm.toggleFollow(post.author.id) },
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun Composer(onPublish: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column {
            GlassField(text, { text = it.take(2000) }, "Чем хочется поделиться?")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (text.isEmpty()) "Видят ваши подписчики" else "${text.length} / 2000",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                    modifier = Modifier.weight(1f),
                )
                GlowButton("Поделиться", enabled = text.isNotBlank(), onClick = {
                    onPublish(text)
                    text = ""
                })
            }
        }
    }
}

/**
 * Карточка записи. following: null — это своя запись (кнопки подписки
 * нет), true / false — подписан ли человек на автора.
 */
@Composable
fun PostCard(post: Post, onLike: () -> Unit, following: Boolean? = null, onFollow: () -> Unit = {}) {
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(post.author, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(post.author.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                    Text(
                        "@${post.author.handle} · ${agoTitle(post.minutesAgo)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Mist,
                    )
                }
                if (following != null) {
                    if (following) GhostButton("Вы подписаны", onClick = onFollow)
                    else GlowButton("Подписаться", onClick = onFollow)
                }
            }
            Text(post.text, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
            post.artSeed?.let { seed ->
                Nebula(
                    seed,
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onLike)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(
                        if (post.liked) LineIcon.HeartFilled else LineIcon.Heart,
                        if (post.liked) Palette.Lilac else Palette.Mist,
                        size = 22.dp,
                    )
                    Text(
                        "${post.likes}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (post.liked) Palette.Lilac else Palette.Mist,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Glyph(LineIcon.Comment, Palette.Mist, size = 22.dp)
                Text(
                    "${post.comments}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.Mist,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}
