package ru.wishka.memory.ui.person

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.User
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.Avatar
import ru.wishka.memory.ui.components.FadeLine
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassCard
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.InterestChips
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.Stat
import ru.wishka.memory.ui.components.UserInfo
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.feed.PostCard
import ru.wishka.memory.ui.theme.Palette

/**
 * Страница другого человека: карточка, сведения, интересы, подписка,
 * «Написать» и его записи. Открывается нажатием на автора записи.
 */
@Composable
fun PersonScreen(vm: AppViewModel, state: UiState, person: User, contentPadding: PaddingValues) {
    val following = person.id in state.following
    val posts = state.postsOf(person.id)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .glass(CircleShape)
                        .clickable { vm.closePerson() },
                    contentAlignment = Alignment.Center,
                ) { Glyph(LineIcon.Back, Palette.Ink) }
                Spacer(Modifier.size(12.dp))
                Text("@${person.handle}", style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
            }
        }

        item {
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Avatar(person, size = 104.dp, ring = true, showOnline = true)
                    Text(person.name.uppercase(), style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
                    Text(
                        if (person.online) "в сети" else "@${person.handle}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (person.online) Palette.Lilac else Palette.Mist,
                    )
                    if (person.bio.isNotBlank()) {
                        Text(person.bio, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(4.dp))
                    FadeLine()
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Stat(posts.size, "записи", Modifier.weight(1f))
                        Stat(person.followers + if (following) 1 else 0, "подписчики", Modifier.weight(1f))
                        Stat(person.followingCount, "подписки", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                        if (following) GhostButton("Вы подписаны", onClick = { vm.toggleFollow(person.id) })
                        else GlowButton("Подписаться", onClick = { vm.toggleFollow(person.id) })
                        GhostButton("Написать", onClick = { vm.writeTo(person.id) })
                    }
                }
            }
        }

        if (person.city.isNotBlank() || person.birthday.isNotBlank() || person.website.isNotBlank() || person.interests.isNotEmpty()) {
            item {
                GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        UserInfo(person)
                        InterestChips(person.interests)
                    }
                }
            }
        }

        item {
            Text(
                if (posts.isEmpty()) "ЗАПИСЕЙ ПОКА НЕТ" else "ЗАПИСИ · ${posts.size}",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Mist,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }
        items(posts, key = { it.id }) { post ->
            PostCard(
                post,
                onLike = { vm.toggleLike(post.id) },
                onComments = { vm.openComments(post.id) },
                onOpenMedia = vm::openMedia,
            )
        }
    }
}
