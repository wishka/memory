package ru.wishka.memory.ui.chats

import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.Chat
import ru.wishka.memory.ui.calls.callLogItems
import ru.wishka.memory.ui.components.MemoryMenu
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.messagePreview
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.Avatar
import ru.wishka.memory.ui.components.FadeLine
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.MenuRow
import ru.wishka.memory.ui.components.ScreenTitle
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.theme.Palette

/** Вкладка «Чаты»: поиск и список переписок. */
@Composable
fun ChatsScreen(vm: AppViewModel, state: UiState, contentPadding: PaddingValues) {
    var query by rememberSaveable { mutableStateOf("") }
    // 0 — переписки, 1 — запросы на переписку, 2 — звонки
    var section by rememberSaveable { mutableStateOf(0) }
    var newGroup by remember { mutableStateOf(false) }
    val calls = section == 2
    val context = androidx.compose.ui.platform.LocalContext.current
    val missed = state.callLog.count { it.missed && !it.outgoing && it.at > 0 }
    val needle = query.trim().lowercase()
    val source = if (section == 1) state.requestChats else state.chatList
    val chats = source.filter {
        needle.isEmpty() || it.peer.name.lowercase().contains(needle) || it.peer.handle.contains(needle)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item {
            Row(Modifier.padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle(
                    t("Чаты"),
                    if (state.unreadTotal > 0) tf("Непрочитанных: %s", state.unreadTotal) else t("Все прочитано"),
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp).weight(1f),
                )
                ru.wishka.memory.ui.profile.RoundIconButton(LineIcon.Groups, Palette.Lilac, description = t("Новая группа")) { newGroup = true }
            }
        }
        item {
            androidx.compose.foundation.layout.Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ru.wishka.memory.ui.components.Chip(t("Переписки"), active = section == 0) { section = 0 }
                val requests = state.requestChats.size
                if (requests > 0 || section == 1) {
                    ru.wishka.memory.ui.components.Chip(if (requests > 0) tf("Запросы · %s", requests) else t("Запросы"), active = section == 1) { section = 1 }
                }
                ru.wishka.memory.ui.components.Chip(
                    if (missed > 0) tf("Звонки · %s", missed) else t("Звонки"),
                    active = calls,
                ) { section = 2 }
            }
        }
        if (calls) {
            callLogItems(vm, state) { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }
            return@LazyColumn
        }
        item {
            GlassField(
                query, { query = it }, t("Поиск по имени"),
                singleLine = true,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(50)),
            )
        }
        if (section == 1) {
            item {
                Text(
                    t("Пишут не друзья. Пока вы не ответите, они не узнают, что вы прочитали. Скрытые слова и фильтр работают и здесь."),
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Mist,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
        }
        if (chats.isEmpty()) {
            item {
                Text(
                    if (section == 1) t("Запросов нет") else t("Никого не нашли"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        items(chats, key = { it.id }) { chat ->
            ChatRow(chat, blocked = state.isBlocked(chat.peer.id), vm = vm) { vm.openChat(chat.id) }
            FadeLine(Modifier.padding(horizontal = 24.dp))
        }
    }
    if (newGroup) NewGroupDialog(vm, state) { newGroup = false }
}

/** Что подтверждаем в диалоге: очистку истории или удаление переписки. */
private enum class ChatConfirm { Clear, Delete }

/**
 * Строка переписки. Долгое нажатие — меню: закрепить, без звука,
 * прочитано, очистить историю, заблокировать, удалить.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(chat: Chat, blocked: Boolean, vm: AppViewModel, onClick: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<ChatConfirm?>(null) }
    Box {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menu = true })
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(chat.peer, size = 50.dp, showOnline = true)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.peer.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Ink,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (chat.muted) Glyph(LineIcon.BellOff, Palette.Mist, size = 16.dp, modifier = Modifier.padding(end = 4.dp))
                if (chat.onTop) Glyph(LineIcon.Pushpin, Palette.Lilac, size = 16.dp, modifier = Modifier.padding(end = 4.dp))
                Text(chat.last?.time.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val preview = when {
                    chat.typing -> t("печатает…")
                    chat.last == null -> t("Нет сообщений")
                    chat.last?.fromMe == true -> tf("Вы: %s", chat.last?.let { messagePreview(it) })
                    else -> chat.last?.let { messagePreview(it) }.orEmpty()
                }
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (chat.typing) Palette.Lilac else Palette.Mist,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (chat.unread > 0) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(CircleShape)
                            // Без звука — счётчик приглушённый
                            .background(
                                if (chat.muted) Brush.linearGradient(listOf(Palette.Plum, Palette.Plum))
                                else Brush.linearGradient(listOf(Palette.Orchid, Palette.Sky))
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text("${chat.unread}", style = MaterialTheme.typography.labelLarge, color = Palette.Ink)
                    }
                }
            }
        }
    }
    MemoryMenu(expanded = menu, onDismiss = { menu = false }) {
        MenuRow(LineIcon.Pushpin, if (chat.onTop) t("Открепить") else t("Закрепить")) {
            menu = false
            vm.toggleChatOnTop(chat.id)
        }
        MenuRow(if (chat.muted) LineIcon.Bell else LineIcon.BellOff, if (chat.muted) t("Включить звук") else t("Без звука")) {
            menu = false
            vm.toggleChatMute(chat.id)
        }
        if (chat.unread > 0) {
            MenuRow(LineIcon.Check, t("Отметить прочитанным")) { menu = false; vm.markRead(chat.id) }
        } else {
            MenuRow(LineIcon.Chats, t("Отметить непрочитанным")) { menu = false; vm.markUnread(chat.id) }
        }
        MenuRow(LineIcon.Block, if (blocked) t("Разблокировать") else t("Заблокировать")) {
            menu = false
            if (blocked) vm.unblock(chat.peer.id) else vm.block(chat.peer.id)
        }
        MenuRow(LineIcon.Trash, t("Очистить историю")) { menu = false; confirm = ChatConfirm.Clear }
        MenuRow(LineIcon.Trash, t("Удалить чат"), color = Palette.Lilac) { menu = false; confirm = ChatConfirm.Delete }
    }
    }
    confirm?.let { what ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Palette.Night,
            title = {
                Text(
                    if (what == ChatConfirm.Delete) tf("Удалить переписку с %s?", chat.peer.name) else t("Очистить историю?"),
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Ink,
                )
            },
            text = {
                Text(
                    if (what == ChatConfirm.Delete) t("Переписка исчезнет из списка вместе со всеми сообщениями.")
                    else t("Сообщения удалятся, сама переписка останется в списке."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    if (what == ChatConfirm.Delete) vm.deleteChat(chat.id) else vm.clearHistory(chat.id)
                }) { Text(if (what == ChatConfirm.Delete) t("Удалить") else t("Очистить"), color = Palette.Lilac) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(t("Отмена"), color = Palette.Mist) } },
        )
    }
}
