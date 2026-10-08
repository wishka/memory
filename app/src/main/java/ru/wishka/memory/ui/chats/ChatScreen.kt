package ru.wishka.memory.ui.chats

import android.widget.Toast
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.Chat
import ru.wishka.memory.data.MediaFiles
import ru.wishka.memory.data.MediaKind
import ru.wishka.memory.data.Message
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.components.AttachButton
import ru.wishka.memory.ui.components.Avatar
import ru.wishka.memory.ui.components.DocumentRow
import ru.wishka.memory.ui.components.FadeLine
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.MediaTile
import ru.wishka.memory.ui.components.MenuRow
import ru.wishka.memory.ui.components.PendingAttachments
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.components.rememberMediaPicker
import ru.wishka.memory.ui.theme.Palette

/**
 * Переписка: шапка с собеседником, сообщения и поле ввода над клавиатурой.
 * Скрепка — фото, видео (из галереи или с камеры) и документы. Долгое
 * нажатие на сообщение — удалить у себя или у обоих.
 */
@Composable
fun ChatScreen(vm: AppViewModel, chat: Chat) {
    val context = LocalContext.current
    var text by rememberSaveable(chat.id) { mutableStateOf("") }
    var pending by remember(chat.id) { mutableStateOf<List<Attachment>>(emptyList()) }
    val picker = rememberMediaPicker { picked -> pending = (pending + picked).take(AppViewModel.MAX_MEDIA) }
    val listState = rememberLazyListState()
    // Новое сообщение — прокрутка вниз (список перевёрнут: низ — это 0)
    LaunchedEffect(chat.messages.size, chat.typing) { listState.animateScrollToItem(0) }
    val canSend = text.isNotBlank() || pending.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable { vm.closeChat() },
                contentAlignment = Alignment.Center,
            ) { Glyph(LineIcon.Back, Palette.Ink) }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { vm.openPerson(chat.peer.id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(chat.peer, size = 40.dp, showOnline = true)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(chat.peer.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                    Text(
                        when {
                            chat.typing -> "печатает…"
                            chat.peer.online -> "в сети"
                            else -> "@${chat.peer.handle}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (chat.typing || chat.peer.online) Palette.Lilac else Palette.Mist,
                    )
                }
            }
        }
        FadeLine()

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (chat.typing) item(key = "typing") { TypingBubble() }
            items(chat.messages.asReversed(), key = { it.id }) { message ->
                Bubble(
                    message,
                    peerName = chat.peer.name.substringBefore(' '),
                    onOpen = { att ->
                        if (att.kind == MediaKind.Document) {
                            if (!MediaFiles.open(context, att)) {
                                Toast.makeText(context, "Нет приложения, чтобы открыть файл", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            vm.openMedia(att)
                        }
                    },
                    onDelete = { forEveryone ->
                        vm.deleteMessage(chat.id, message.id, forEveryone)
                        // Копии вложений больше не нужны
                        message.attachments.forEach(MediaFiles::delete)
                    },
                )
            }
        }

        PendingAttachments(
            pending,
            onRemove = { item ->
                pending = pending - item
                MediaFiles.delete(item)
            },
            modifier = Modifier.padding(top = 8.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AttachButton(picker, documents = true, size = 50.dp)
            GlassField(
                text, { text = it.take(2000) }, "Сообщение",
                maxLines = 5,
                modifier = Modifier
                    .weight(1f)
                    .glass(RoundedCornerShape(24.dp)),
            )
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(
                        if (!canSend) Brush.linearGradient(listOf(Palette.Plum, Palette.Plum))
                        else Brush.linearGradient(listOf(Palette.Orchid, Palette.Ocean))
                    )
                    .clickable(enabled = canSend) {
                        vm.send(chat.id, text, pending)
                        text = ""
                        pending = emptyList()
                    },
                contentAlignment = Alignment.Center,
            ) { Glyph(LineIcon.Send, if (!canSend) Palette.Mist else Palette.Ink) }
        }
    }
}

/**
 * Сообщение: текст и вложения. Долгое нажатие — меню удаления:
 * «у себя» (у собеседника останется) или «у меня и у <имя>».
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(message: Message, peerName: String, onOpen: (Attachment) -> Unit, onDelete: (Boolean) -> Unit) {
    val mine = message.fromMe
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        val shape = RoundedCornerShape(
            topStart = 20.dp, topEnd = 20.dp,
            bottomStart = if (mine) 20.dp else 6.dp,
            bottomEnd = if (mine) 6.dp else 20.dp,
        )
        Box {
            Column(
                modifier = Modifier
                    .widthIn(max = 290.dp)
                    .then(
                        if (mine) Modifier
                            .clip(shape)
                            .background(Brush.linearGradient(listOf(Palette.Violet, Palette.Ocean)))
                        else Modifier.glass(shape)
                    )
                    .combinedClickable(onClick = {}, onLongClick = { menu = true })
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val visual = message.attachments.filter { it.kind != MediaKind.Document }
                val documents = message.attachments.filter { it.kind == MediaKind.Document }
                if (visual.isNotEmpty()) {
                    MediaGrid(visual, onOpen)
                }
                documents.forEach { doc ->
                    DocumentRow(doc, Modifier.fillMaxWidth(), onClick = { onOpen(doc) })
                }
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
                    if (message.text.isNotEmpty()) {
                        Text(message.text, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
                    }
                    Text(
                        message.time,
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.Ink.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                MenuRow(LineIcon.Trash, "Удалить у себя") {
                    menu = false
                    onDelete(false)
                }
                MenuRow(LineIcon.Trash, "Удалить у меня и у $peerName", color = Palette.Lilac) {
                    menu = false
                    onDelete(true)
                }
            }
        }
    }
}

/** Фото и видео в сообщении: одно — крупно, несколько — сеткой по два. */
@Composable
private fun MediaGrid(items: List<Attachment>, onOpen: (Attachment) -> Unit) {
    if (items.size == 1) {
        MediaTile(
            items[0],
            Modifier.width(260.dp).height(200.dp).clip(RoundedCornerShape(16.dp)),
            onClick = { onOpen(items[0]) },
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { item ->
                    MediaTile(
                        item,
                        Modifier.size(128.dp).clip(RoundedCornerShape(12.dp)),
                        maxSide = 384,
                        onClick = { onOpen(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(
        modifier = Modifier
            .glass(RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(3) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Palette.Lilac.copy(alpha = 0.5f + it * 0.2f)),
            )
        }
    }
}
