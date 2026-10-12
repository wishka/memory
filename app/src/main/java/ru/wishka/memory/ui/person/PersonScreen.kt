package ru.wishka.memory.ui.person

import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.User
import ru.wishka.memory.ui.components.MemoryMenu
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.ProfileSection
import ru.wishka.memory.ui.components.MenuRow
import ru.wishka.memory.ui.profile.StatButton
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
    state.personSection?.let { section ->
        PersonSectionScreen(vm, state, person, section, contentPadding)
        return
    }
    val following = person.id in state.following
    val blocked = state.isBlocked(person.id)
    // Закрытый профиль: записи видны только подписчикам
    val hidden = !state.canSee(person)
    val requested = person.id in state.sentRequests
    val posts = if (blocked || hidden) emptyList() else state.postsOf(person.id)
    var menu by remember { mutableStateOf(false) }

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
                Text("@${person.handle}", style = MaterialTheme.typography.titleMedium, color = Palette.Ink, modifier = Modifier.weight(1f))
                // «⋯»: заблокировать или разблокировать
                Box {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .glass(CircleShape)
                            .clickable { menu = true },
                        contentAlignment = Alignment.Center,
                    ) { Glyph(LineIcon.More, Palette.Ink) }
                    MemoryMenu(expanded = menu, onDismiss = { menu = false }) {
                        if (blocked) {
                            MenuRow(LineIcon.Block, t("Разблокировать")) { menu = false; vm.unblock(person.id) }
                        } else {
                            MenuRow(LineIcon.Block, t("Заблокировать"), color = Palette.Lilac) { menu = false; vm.block(person.id) }
                        }
                        MenuRow(LineIcon.Flag, t("Пожаловаться"), color = Palette.PearlPink) { menu = false; vm.reportPerson(person) }
                    }
                }
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
                    if (person.closed) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Glyph(LineIcon.Lock, Palette.Lilac, size = 16.dp)
                            Spacer(Modifier.size(6.dp))
                            Text(t("Закрытый профиль"), style = MaterialTheme.typography.labelLarge, color = Palette.Lilac)
                        }
                    }
                    Text(
                        if (person.online) t("в сети") else "@${person.handle}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (person.online) Palette.Lilac else Palette.Mist,
                    )
                    if (person.bio.isNotBlank()) {
                        Text(person.bio, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(4.dp))
                    FadeLine()
                    // Счётчики — кнопки: записи, подписчики и подписки человека
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        StatButton(if (hidden) state.postsOf(person.id).size else posts.size, t("записи"), Modifier.weight(1f)) {
                            if (!hidden) vm.openPersonSection(ProfileSection.Posts)
                        }
                        StatButton(person.followers + if (following) 1 else 0, t("подписчики"), Modifier.weight(1f)) {
                            vm.openPersonSection(ProfileSection.Followers)
                        }
                        StatButton(person.followingCount, t("подписки"), Modifier.weight(1f)) {
                            vm.openPersonSection(ProfileSection.Following)
                        }
                    }
                    if (blocked) {
                        Text(
                            t("Вы заблокировали этого человека: его записи и сообщения вам не видны."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.Mist,
                            textAlign = TextAlign.Center,
                        )
                        GhostButton(t("Разблокировать"), onClick = { vm.unblock(person.id) })
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                            when {
                                following -> GhostButton(t("Вы подписаны"), onClick = { vm.toggleFollow(person.id) })
                                requested -> GhostButton(t("Заявка отправлена"), onClick = { vm.toggleFollow(person.id) })
                                person.closed -> GlowButton(t("Отправить заявку"), onClick = { vm.toggleFollow(person.id) })
                                else -> GlowButton(t("Подписаться"), onClick = { vm.toggleFollow(person.id) })
                            }
                            GhostButton(t("Написать"), onClick = { vm.writeTo(person.id) })
                        }
                    }
                }
            }
        }

        if (!blocked && (person.city.isNotBlank() || person.birthday.isNotBlank() || person.website.isNotBlank() || person.interests.isNotEmpty())) {
            item {
                GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        UserInfo(person)
                        InterestChips(person.interests)
                    }
                }
            }
        }

        if (hidden && !blocked) item {
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph(LineIcon.Lock, Palette.Lilac, size = 28.dp)
                    Spacer(Modifier.size(12.dp))
                    Text(
                        if (requested) t("Заявка отправлена. Когда её примут, здесь появятся записи и чек-ины.")
                        else t("Это закрытый профиль. Записи и чек-ины видят только подписчики — отправьте заявку."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Ink,
                    )
                }
            }
        }

        if (!hidden) item {
            Text(
                if (posts.isEmpty()) t("ЗАПИСЕЙ ПОКА НЕТ") else tf("ЗАПИСИ · %s", posts.size),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Mist,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }
        items(posts, key = { it.id }) { post ->
            PostCard(
                post,
                onLike = { vm.toggleLike(post.id) },
                onVote = { vm.votePost(post.id, it) },
                onClosePoll = if (vm.isMine(post)) ({ vm.closePostPoll(post.id) }) else null,
                onReport = if (vm.isMine(post)) null else ({ vm.reportPost(post) }),
                onComments = { vm.openComments(post.id) },
                onOpenMedia = vm::openMedia,
            )
        }
    }
}
