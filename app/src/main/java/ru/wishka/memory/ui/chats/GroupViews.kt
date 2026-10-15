package ru.wishka.memory.ui.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.Chat
import ru.wishka.memory.data.ChatGroup
import ru.wishka.memory.data.Demo
import ru.wishka.memory.data.User
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.Avatar
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.MemoryMenu
import ru.wishka.memory.ui.components.MenuRow
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.theme.Palette

/** Выбор друзей галочками: для новой группы и «Добавить участников». */
@Composable
private fun FriendPicker(people: List<User>, chosen: Set<Int>, onToggle: (Int) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.heightIn(max = 340.dp)) {
        if (people.isEmpty()) {
            item { Text(t("Некого добавить — все друзья уже здесь."), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, modifier = Modifier.padding(16.dp)) }
        }
        items(people, key = { it.id }) { u ->
            val on = u.id in chosen
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Checkbox, onClickLabel = if (on) t("Убрать") else t("Выбрать")) { onToggle(u.id) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(u, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Text(u.name, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink, modifier = Modifier.weight(1f))
                if (on) Glyph(LineIcon.Check, Palette.Lilac, size = 20.dp, description = t("Выбрано"))
            }
        }
    }
}

/** Новая группа: название и друзья. */
@Composable
fun NewGroupDialog(vm: AppViewModel, state: UiState, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var chosen by remember { mutableStateOf(setOf<Int>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Night,
        title = { Text(t("Новая группа"), color = Palette.Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassField(title, { title = it.take(ChatGroup.MAX_TITLE) }, t("Название группы"), singleLine = true, modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(50)))
                Text(tf("Выбрано: %s", chosen.size), style = MaterialTheme.typography.labelLarge, color = Palette.Mist)
                FriendPicker(state.friends.filter { it.id !in state.blocked }, chosen, { id -> chosen = if (id in chosen) chosen - id else chosen + id })
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && chosen.isNotEmpty(), onClick = {
                vm.createGroup(title, chosen.toList())?.let { id -> onDismiss(); vm.openChat(id) }
            }) { Text(t("Создать"), color = Palette.Lilac) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Отмена"), color = Palette.Mist) } },
    )
}

/** О группе: название, участники и роли, добавить, выйти. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoSheet(vm: AppViewModel, state: UiState, chat: Chat, onDismiss: () -> Unit) {
    val g = chat.group ?: return
    val me = state.me.id
    val admin = g.isAdmin(me)
    var title by rememberSaveable(chat.id) { mutableStateOf(g.title) }
    var adding by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Palette.Night, contentColor = Palette.Ink) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (admin) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassField(title, { title = it.take(ChatGroup.MAX_TITLE) }, t("Название группы"), singleLine = true, modifier = Modifier.weight(1f).glass(RoundedCornerShape(50)))
                    if (title.trim() != g.title && title.isNotBlank()) GlowButton(t("Сохранить"), onClick = { vm.renameGroup(chat.id, title) })
                }
            } else {
                Text(g.title, style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
            }
            Text(tf("Участников: %s", g.memberIds.size), style = MaterialTheme.typography.labelLarge, color = Palette.Mist)
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(g.memberIds, key = { it }) { uid ->
                    val u = if (uid == me) state.me else Demo.person(uid) ?: return@items
                    MemberRow(u, g, me, onRemove = { vm.removeGroupMember(chat.id, uid) }, onAdmin = { on -> vm.setGroupAdmin(chat.id, uid, on) }, onOpen = { onDismiss(); vm.openPerson(uid) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (admin) GhostButton(t("Добавить участников"), onClick = { adding = true })
                GhostButton(t("Выйти из группы"), onClick = { confirmLeave = true })
            }
        }
    }
    if (adding) {
        var chosen by remember { mutableStateOf(setOf<Int>()) }
        AlertDialog(
            onDismissRequest = { adding = false },
            containerColor = Palette.Night,
            title = { Text(t("Добавить участников"), color = Palette.Ink) },
            text = { FriendPicker(state.friends.filter { it.id !in g.memberIds && it.id !in state.blocked }, chosen, { id -> chosen = if (id in chosen) chosen - id else chosen + id }) },
            confirmButton = { TextButton(enabled = chosen.isNotEmpty(), onClick = { vm.addGroupMembers(chat.id, chosen.toList()); adding = false }) { Text(t("Добавить"), color = Palette.Lilac) } },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(t("Отмена"), color = Palette.Mist) } },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = Palette.Night,
            title = { Text(t("Выйти из группы?"), color = Palette.Ink) },
            text = { Text(t("Переписка пропадёт из списка. Вернуть может админ группы."), color = Palette.Mist) },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onDismiss(); vm.leaveGroup(chat.id) }) { Text(t("Выйти"), color = Palette.PearlPink) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(t("Отмена"), color = Palette.Mist) } },
        )
    }
}

@Composable
private fun MemberRow(u: User, g: ChatGroup, me: Int, onRemove: () -> Unit, onAdmin: (Boolean) -> Unit, onOpen: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val canManage = g.isAdmin(me) && u.id != me && u.id != g.ownerId
    androidx.compose.foundation.layout.Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .clickable(onClickLabel = if (canManage) t("Действия с участником") else t("Открыть страницу"), role = Role.Button) { if (canManage) menu = true else if (u.id != me) onOpen() }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(u, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (u.id == me) t("Вы") else u.name, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
                val role = when {
                    u.id == g.ownerId -> t("создатель")
                    u.id in g.adminIds -> t("админ")
                    else -> ""
                }
                if (role.isNotEmpty()) Text(role, style = MaterialTheme.typography.labelSmall, color = Palette.Lilac)
            }
        }
        MemoryMenu(expanded = menu, onDismiss = { menu = false }) {
            MenuRow(LineIcon.Profile, t("Открыть страницу")) { menu = false; onOpen() }
            if (g.ownerId == me) {
                val isAdmin = u.id in g.adminIds
                MenuRow(LineIcon.Shield, if (isAdmin) t("Снять админа") else t("Сделать админом")) { menu = false; onAdmin(!isAdmin) }
            }
            MenuRow(LineIcon.Trash, t("Убрать из группы"), color = Palette.PearlPink) { menu = false; onRemove() }
        }
    }
}
