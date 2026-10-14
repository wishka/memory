package ru.wishka.memory.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalContext
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
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
import androidx.compose.runtime.remember
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
 * Долгое нажатие на комментарий — меню: ответить, скопировать,
 * «нравится», страница автора, пожаловаться (на чужой) или удалить (свой).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(
    total: Int,
    comments: List<Comment>,
    myId: Int,
    onSend: (text: String, replyTo: String, sticker: ru.wishka.memory.data.Attachment?) -> Unit,
    onDelete: (Long) -> Unit,
    onReport: (Long) -> Unit,
    onLike: (Long) -> Unit,
    onAuthor: (Int) -> Unit,
    onDismiss: () -> Unit,
    /** Свои стикеры из «Студии» — ответить стикером. */
    stickers: List<ru.wishka.memory.data.Sticker> = emptyList(),
) {
    var choosingSticker by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    // Кому отвечаем: имя автора комментария; "" — просто комментарий
    var replyTo by rememberSaveable { mutableStateOf("") }
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
                        onReply = { replyTo = c.author.name },
                        onCopy = {
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                .setPrimaryClip(ClipData.newPlainText("comment", c.text))
                            Toast.makeText(context, t("Скопировано"), Toast.LENGTH_SHORT).show()
                        },
                        onReport = {
                            onReport(c.id)
                            Toast.makeText(context, t("Жалоба отправлена. Комментарий скрыт."), Toast.LENGTH_SHORT).show()
                        },
                        onDelete = { onDelete(c.id) },
                        onLike = { onLike(c.id) },
                        onAuthor = { onAuthor(c.author.id) },
                    )
                }
            }
            FadeLine()
            if (replyTo.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(LineIcon.Reply, Palette.Lilac, size = 16.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(tf("Ответ для %s", replyTo), style = MaterialTheme.typography.labelLarge, color = Palette.Lilac, modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier.size(32.dp).clip(CircleShape).clickable { replyTo = "" },
                        contentAlignment = Alignment.Center,
                    ) { Glyph(LineIcon.Close, Palette.Mist, size = 16.dp, description = LineIcon.Close.label) }
                }
            }
            MentionSuggestions(text, { text = it.take(1000) }, Modifier.padding(horizontal = 12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier.size(46.dp).clip(CircleShape).glass(CircleShape).clickable { choosingSticker = true },
                    contentAlignment = Alignment.Center,
                ) { Glyph(LineIcon.Sticker, Palette.Lilac, size = 22.dp, description = LineIcon.Sticker.label) }
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
                            onSend(text, replyTo, null)
                            text = ""
                            replyTo = ""
                        },
                    contentAlignment = Alignment.Center,
                ) { Glyph(LineIcon.Send, if (text.isBlank()) Palette.Mist else Palette.Ink) }
            }
        }
    }
    if (choosingSticker) {
        StickerPicker(
            stickers,
            onPick = { sticker -> onSend(text, replyTo, sticker); text = ""; replyTo = "" },
            onDismiss = { choosingSticker = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentRow(
    comment: Comment,
    mine: Boolean,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onReport: () -> Unit,
    onDelete: () -> Unit,
    onLike: () -> Unit,
    onAuthor: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.clip(CircleShape).clickable(onClick = onAuthor)) { Avatar(comment.author, size = 34.dp) }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(onClick = {}, onLongClick = { menu = true }),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(comment.author.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        agoTitle(minutesSince(comment.createdAt, comment.minutesAgo)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Mist,
                    )
                }
                if (comment.replyTo.isNotEmpty()) {
                    Text(tf("ответ для %s", comment.replyTo), style = MaterialTheme.typography.labelMedium, color = Palette.Lilac)
                }
                if (comment.text.isNotEmpty()) RichText(comment.text)
                comment.sticker?.let { StickerImage(it, size = 110.dp) }
            }
            MemoryMenu(expanded = menu, onDismiss = { menu = false }) {
                MenuRow(LineIcon.Reply, t("Ответить")) { menu = false; onReply() }
                MenuRow(LineIcon.Copy, t("Скопировать")) { menu = false; onCopy() }
                MenuRow(LineIcon.Profile, t("Страница автора")) { menu = false; onAuthor() }
                if (mine) {
                    MenuRow(LineIcon.Trash, t("Удалить"), color = Palette.PearlPink) { menu = false; onDelete() }
                } else {
                    MenuRow(LineIcon.Flag, t("Пожаловаться"), color = Palette.PearlPink) { menu = false; onReport() }
                }
            }
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
