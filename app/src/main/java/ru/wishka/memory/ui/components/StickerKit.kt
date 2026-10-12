package ru.wishka.memory.ui.components

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.MediaFiles
import ru.wishka.memory.data.MediaKind
import ru.wishka.memory.data.Sticker
import ru.wishka.memory.i18n.t
import ru.wishka.memory.ui.theme.Palette
import java.io.File

/**
 * Свой стикер как вложение: копия PNG из «Студии». Копия — чтобы
 * сообщение или запись не потеряли картинку, если стикер потом удалят.
 */
fun stickerAttachment(context: Context, sticker: Sticker): Attachment? =
    MediaFiles.copyLocal(context, File(sticker.path), MediaKind.Photo, "sticker.png", "image/png")?.copy(sticker = true)

/** Стикер без рамки и фона: прозрачная картинка как есть. */
@Composable
fun StickerImage(attachment: Attachment, modifier: Modifier = Modifier, size: Dp = 140.dp, onClick: (() -> Unit)? = null) {
    val image = rememberThumbnail(attachment, 512)
    Box(
        modifier = modifier
            .size(size)
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) Image(image, contentDescription = t("Стикер"), contentScale = ContentScale.Fit, modifier = Modifier.size(size))
    }
}

/** Стикеры записи — рядком, крупно. */
@Composable
fun StickerRow(stickers: List<Attachment>, modifier: Modifier = Modifier, size: Dp = 120.dp) {
    if (stickers.isEmpty()) return
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        stickers.forEach { StickerImage(it, size = size) }
    }
}

/**
 * Выбор своего стикера, смайла или значка из «Студии». Пусто — подсказка,
 * где их сделать. onPick получает уже скопированное вложение.
 */
@Composable
fun StickerPicker(
    stickers: List<Sticker>,
    onPick: (Attachment) -> Unit,
    onDismiss: () -> Unit,
    onOpenStudio: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Palette.Night)
                .padding(16.dp),
        ) {
            Text(t("Мои стикеры"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
            Spacer(Modifier.height(10.dp))
            if (stickers.isEmpty()) {
                Text(
                    t("Стикеров пока нет. Сделайте значок, смайл или стикер из фото в «Студии» профиля."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton(t("Закрыть"), onClick = onDismiss)
                    if (onOpenStudio != null) GlowButton(t("Открыть Студию"), onClick = { onDismiss(); onOpenStudio() })
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(stickers, key = { it.id }) { sticker ->
                        val preview = Attachment(sticker.id, MediaKind.Photo, sticker.path, sticker = true)
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Palette.Plum.copy(alpha = 0.35f))
                                .clickable {
                                    stickerAttachment(context, sticker)?.let(onPick)
                                    onDismiss()
                                },
                            contentAlignment = Alignment.Center,
                        ) { StickerImage(preview, size = 84.dp) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                GhostButton(t("Закрыть"), onClick = onDismiss)
            }
        }
    }
}
