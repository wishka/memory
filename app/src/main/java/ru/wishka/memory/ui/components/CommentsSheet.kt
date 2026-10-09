package ru.wishka.memory.ui.components

import ru.wishka.memory.i18n.t
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import ru.wishka.memory.data.Comment
import ru.wishka.memory.data.agoTitle
import ru.wishka.memory.data.minutesSince
import ru.wishka.memory.data.plural
import ru.wishka.memory.ui.theme.Palette

/**
 * Обсуждение записи или PRISM — шторка снизу: комментарии и поле ввода.
 * total — сколько всего комментариев (у демо-записей видна только часть).
 * Свой комментарий удаляется значком корзины рядом с ним.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(
    total: Int,
    comments: List<Comment>,
    myId: Int,
    onSend: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onLike: (Long) -> Unit,
    onAuthor: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(comments.size) { if (comments.isNotEmpty()) list.animateScrollToItem(comments.lastIndex) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = Palette.Night,
        contentColor = Palette.Ink,
    ) {
        Column(modifier = Modifier.navigationBarsPadding().imePadding()) {
            Text(
                plural(total, "комментарий", "комментария", "комментариев").uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Mist,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            if (comments.isEmpty()) {
                Text(
                    t("Пока никто не написал. Будьте первым."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                    modifier = Modifier.padding(20.dp),
                )
            }
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(comments, key = { it.id }) { c ->
                    CommentRow(
                        c,
                        mine = c.author.id == myId,
                        onDelete = { onDelete(c.id) },
                        onLike = { onLike(c.id) },
                        onAuthor = { onAuthor(c.author.id) },
                    )
                }
            }
            FadeLine()
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlassField(
                    text, { text = it.take(1000) }, t("Комментарий"),
                    maxLines = 4,
                    modifier = Modifier.weight(1f).glass(RoundedCornerShape(22.dp)),
                )
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            if (text.isBlank()) Brush.linearGradient(listOf(Palette.Plum, Palette.Plum))
                            else Brush.linearGradient(listOf(Palette.Orchid, Palette.Ocean))
                        )
                        .clickable(enabled = text.isNotBlank()) {
                            onSend(text)
                            text = ""
                        },
                    contentAlignment = Alignment.Center,
                ) { Glyph(LineIcon.Send, if (text.isBlank()) Palette.Mist else Palette.Ink) }
            }
        }
    }
}

@Composable
private fun CommentRow(comment: Comment, mine: Boolean, onDelete: () -> Unit, onLike: () -> Unit, onAuthor: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.clip(CircleShape).clickable(onClick = onAuthor)) { Avatar(comment.author, size = 34.dp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(comment.author.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                Spacer(Modifier.width(8.dp))
                Text(
                    agoTitle(minutesSince(comment.createdAt, comment.minutesAgo)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                )
            }
            Text(comment.text, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
        }
        if (mine) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) { Glyph(LineIcon.Trash, Palette.Mist, size = 18.dp) }
        }
        // «Нравится» комментарию: сердце и число под ним
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onLike)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LikeHeart(comment.liked, if (comment.liked) Palette.Lilac else Palette.Mist, size = 18.dp)
            if (comment.likes > 0) {
                Text(
                    "${comment.likes}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (comment.liked) Palette.Lilac else Palette.Mist,
                )
            }
        }
    }
}
