package ru.wishka.memory.ui.profile

import ru.wishka.memory.i18n.t
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.Group
import ru.wishka.memory.data.Prism
import ru.wishka.memory.data.User
import ru.wishka.memory.data.plural
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.ProfileSection
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.Avatar
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassCard
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.Nebula
import ru.wishka.memory.ui.components.MediaTile
import ru.wishka.memory.ui.components.ScreenTitle
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.components.MemoryMenu
import ru.wishka.memory.ui.components.MenuRow
import ru.wishka.memory.ui.components.RemoveFriendDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ru.wishka.memory.ui.feed.PostCard
import ru.wishka.memory.ui.prism.PrismArt
import ru.wishka.memory.ui.theme.Palette

/**
 * Раздел профиля на отдельной странице: свои записи, друзья, заявки
 * в друзья, группы или сохранённые PRISM. Открывается нажатием на
 * счётчик в профиле; «назад» возвращает в профиль.
 */
@Composable
fun ProfileSectionScreen(vm: AppViewModel, state: UiState, section: ProfileSection, contentPadding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .glass(CircleShape)
                        .clickable { vm.closeProfileSection() },
                    contentAlignment = Alignment.Center,
                ) { Glyph(LineIcon.Back, Palette.Ink) }
                Spacer(Modifier.width(14.dp))
                ScreenTitle(section.title, subtitle(state, section))
            }
        }

        when (section) {
            ProfileSection.Posts -> {
                if (state.myPosts.isEmpty()) item { Empty(t("Пока ни одной записи. Поделитесь первым моментом во вкладке «Лента».")) }
                items(state.myPosts, key = { it.id }) { post ->
                    PostCard(
                        post,
                        onLike = { vm.toggleLike(post.id) },
                        onVote = { vm.votePost(post.id, it) },
                        onClosePoll = if (vm.isMine(post)) ({ vm.closePostPoll(post.id) }) else null,
                        onComments = { vm.openComments(post.id) },
                        onOpenMedia = vm::openMedia,
                    )
                }
            }
            ProfileSection.Friends -> {
                if (state.friends.isEmpty()) item { Empty(t("У вас пока нет друзей. Добавить в друзья можно в ленте — кнопкой у имени автора.")) }
                items(state.friends, key = { it.id }) { person ->
                    PersonRow(person, onOpen = { vm.openPerson(person.id) }) {
                        FriendActions(person, onWrite = { vm.writeTo(person.id) }, onRemove = { vm.removeFriend(person.id) })
                    }
                }
            }
            ProfileSection.Groups -> {
                if (state.myGroups.isEmpty()) item { Empty(t("Вы пока ни в одной группе. Найти группу можно во вкладке «Группы».")) }
                items(state.myGroups, key = { it.id }) { group -> GroupRow(group) { vm.openGroup(group.id) } }
            }
            ProfileSection.Saved -> {
                if (state.savedPrisms.isEmpty()) item { Empty(t("Здесь будут PRISM, которые вы сохранили кнопкой «Сохранить».")) }
                // Сохранённые ролики — плитками по две в ряд
                items(state.savedPrisms.chunked(2), key = { row -> row.first().id }) { row ->
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        row.forEach { prism -> SavedTile(prism, Modifier.weight(1f)) { vm.openPrism(prism.id) } }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            ProfileSection.Requests -> {
                item {
                    Empty(
                        if (state.privacy.closedProfile) t("Профиль закрыт: записи и чек-ины видят только друзья. Примите заявку — и человек станет вашим другом.")
                        else t("Примите заявку — и человек станет вашим другом. Закрыть профиль от всех, кроме друзей, можно в настройках приватности.")
                    )
                }
                if (state.requestPeople.isEmpty()) item { Empty(t("Новых заявок нет.")) }
                items(state.requestPeople, key = { it.id }) { person ->
                    RequestRow(
                        person,
                        onOpen = { vm.openPerson(person.id) },
                        onApprove = { vm.approveRequest(person.id) },
                        onDecline = { vm.declineRequest(person.id) },
                    )
                }
                // Мои заявки, которые ещё ждут ответа: их можно отозвать
                if (state.sentRequestPeople.isNotEmpty()) {
                    item {
                        Text(
                            t("ОТПРАВЛЕННЫЕ") + " · ${state.sentRequestPeople.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.Mist,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
                        )
                    }
                    items(state.sentRequestPeople, key = { "sent-${it.id}" }) { person ->
                        PersonRow(person, onOpen = { vm.openPerson(person.id) }) {
                            GhostButton(t("Отменить"), onClick = { vm.toggleFriend(person.id) })
                        }
                    }
                }
            }
            // «Моя музыка», «Студия» и «Итоги» — свои экраны
            ProfileSection.Music, ProfileSection.Stickers, ProfileSection.Recap, ProfileSection.Shelf, ProfileSection.Premium -> Unit
        }
    }
}

private fun subtitle(state: UiState, section: ProfileSection): String = when (section) {
    ProfileSection.Posts -> plural(state.myPosts.size, "запись", "записи", "записей")
    ProfileSection.Friends -> plural(state.friends.size, "друг", "друга", "друзей")
    ProfileSection.Groups -> plural(state.myGroups.size, "группа", "группы", "групп")
    ProfileSection.Saved -> plural(state.savedPrisms.size, "ролик", "ролика", "роликов")
    ProfileSection.Music -> plural(state.tracks.size, "трек", "трека", "треков")
    ProfileSection.Stickers -> plural(state.stickers.size, "стикер", "стикера", "стикеров")
    ProfileSection.Shelf -> ""
    ProfileSection.Recap -> ""
    ProfileSection.Premium -> ""
    ProfileSection.Requests -> plural(state.followRequests.size, "заявка", "заявки", "заявок")
}

/** Заявка в друзья ко мне: принять или отклонить. */
@Composable
private fun RequestRow(person: User, onOpen: () -> Unit, onApprove: () -> Unit, onDecline: () -> Unit) {
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(person, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(person.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, maxLines = 1)
                    Text("@${person.handle}", style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, maxLines = 1)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlowButton(t("Принять"), onClick = onApprove)
                GhostButton(t("Отклонить"), onClick = onDecline)
            }
        }
    }
}

@Composable
fun Empty(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = Palette.Mist,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

/**
 * Человек в списке: аватар, имя и справа — кнопки (trailing: дружба,
 * «Написать»…; null — без кнопок). Нажатие — его страница.
 */
@Composable
fun PersonRow(person: User, onOpen: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(person, size = 48.dp, showOnline = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(person.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (person.city.isNotBlank()) "@${person.handle} · ${person.city}" else "@${person.handle}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
    }
}

/** Друг в моём списке: «Написать» и «⋯» → «Удалить из друзей» (с подтверждением). */
@Composable
private fun FriendActions(person: User, onWrite: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        GhostButton(t("Написать"), onClick = onWrite)
        Box {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).clickable { menu = true },
                contentAlignment = Alignment.Center,
            ) { Glyph(LineIcon.More, Palette.Mist, size = 20.dp) }
            MemoryMenu(expanded = menu, onDismiss = { menu = false }) {
                MenuRow(LineIcon.Trash, t("Удалить из друзей"), color = Palette.PearlPink) { menu = false; confirm = true }
            }
        }
    }
    if (confirm) RemoveFriendDialog(person.name, onDismiss = { confirm = false }, onConfirm = { confirm = false; onRemove() })
}

@Composable
private fun GroupRow(group: Group, onOpen: () -> Unit) {
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ru.wishka.memory.ui.components.GroupCover(group, Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(group.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${t(group.topic)} · ${plural(group.members, "участник", "участника", "участников")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                )
            }
            if (group.mine) Text(t("ВАША"), style = MaterialTheme.typography.labelSmall, color = Palette.Lilac)
        }
    }
}

@Composable
private fun SavedTile(prism: Prism, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier = modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)) {
        Box(contentAlignment = Alignment.Center) {
            val cover = prism.cover
            if (cover != null) MediaTile(cover, Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(18.dp)), maxSide = 480)
            else PrismArt(prism.seed, 1_500L, Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(18.dp)))
            Glyph(LineIcon.Play, Palette.Ink, size = 30.dp)
        }
        Text(
            prism.caption,
            style = MaterialTheme.typography.labelLarge,
            color = Palette.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
        Text("@${prism.author.handle}", style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, modifier = Modifier.padding(horizontal = 2.dp))
    }
}
