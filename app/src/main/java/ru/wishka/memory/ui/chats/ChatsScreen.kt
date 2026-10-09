package ru.wishka.memory.ui.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.Chat
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.messagePreview
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.Avatar
import ru.wishka.memory.ui.components.FadeLine
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.ScreenTitle
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.theme.Palette

/** Вкладка «Чаты»: поиск и список переписок. */
@Composable
fun ChatsScreen(vm: AppViewModel, state: UiState, contentPadding: PaddingValues) {
    var query by rememberSaveable { mutableStateOf("") }
    val needle = query.trim().lowercase()
    val chats = state.chats.filter {
        needle.isEmpty() || it.peer.name.lowercase().contains(needle) || it.peer.handle.contains(needle)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item {
            ScreenTitle(
                "Чаты",
                if (state.unreadTotal > 0) "Непрочитанных: ${state.unreadTotal}" else "Все прочитано",
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        item {
            GlassField(
                query, { query = it }, "Поиск по имени",
                singleLine = true,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(50)),
            )
        }
        if (chats.isEmpty()) {
            item {
                Text(
                    "Никого не нашли",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        items(chats, key = { it.id }) { chat ->
            ChatRow(chat) { vm.openChat(chat.id) }
            FadeLine(Modifier.padding(horizontal = 24.dp))
        }
    }
}

@Composable
private fun ChatRow(chat: Chat, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                Text(chat.last?.time.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val preview = when {
                    chat.typing -> "печатает…"
                    chat.last == null -> "Нет сообщений"
                    chat.last?.fromMe == true -> "Вы: ${chat.last?.let { messagePreview(it) }}"
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
                            .background(Brush.linearGradient(listOf(Palette.Orchid, Palette.Sky)))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text("${chat.unread}", style = MaterialTheme.typography.labelLarge, color = Palette.Ink)
                    }
                }
            }
        }
    }
}
