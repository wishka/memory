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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.Chat
import ru.wishka.memory.data.Message
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.components.Avatar
import ru.wishka.memory.ui.components.FadeLine
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.theme.Palette

/** Переписка: шапка с собеседником, сообщения и поле ввода над клавиатурой. */
@Composable
fun ChatScreen(vm: AppViewModel, chat: Chat) {
    var text by rememberSaveable(chat.id) { mutableStateOf("") }
    val listState = rememberLazyListState()
    // Новое сообщение — прокрутка вниз (список перевёрнут: низ — это 0)
    LaunchedEffect(chat.messages.size, chat.typing) { listState.animateScrollToItem(0) }

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
            items(chat.messages.asReversed(), key = { it.id }) { Bubble(it) }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
                        if (text.isBlank()) Brush.linearGradient(listOf(Palette.Plum, Palette.Plum))
                        else Brush.linearGradient(listOf(Palette.Orchid, Palette.Ocean))
                    )
                    .clickable(enabled = text.isNotBlank()) {
                        vm.send(chat.id, text)
                        text = ""
                    },
                contentAlignment = Alignment.Center,
            ) { Glyph(LineIcon.Send, if (text.isBlank()) Palette.Mist else Palette.Ink) }
        }
    }
}

@Composable
private fun Bubble(message: Message) {
    val mine = message.fromMe
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        val shape = RoundedCornerShape(
            topStart = 20.dp, topEnd = 20.dp,
            bottomStart = if (mine) 20.dp else 6.dp,
            bottomEnd = if (mine) 6.dp else 20.dp,
        )
        Column(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .then(
                    if (mine) Modifier
                        .clip(shape)
                        .background(Brush.linearGradient(listOf(Palette.Violet, Palette.Ocean)))
                    else Modifier.glass(shape)
                )
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Text(message.text, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
            Text(
                message.time,
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Ink.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.End),
            )
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
