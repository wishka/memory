package ru.wishka.memory.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.CheckIn
import ru.wishka.memory.data.CheckInTemplate
import ru.wishka.memory.data.MediaFiles
import ru.wishka.memory.data.Sticker
import ru.wishka.memory.data.plural
import ru.wishka.memory.i18n.t
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.theme.Palette

/**
 * Новая запись — в Ленте и в Историях: текст, фото и видео из галереи
 * или с камеры, свои стикеры и чек-ин («смотрю фильм», «пробежал 5 км»).
 * Вложения видны плитками под полем, крестик убирает лишнее.
 */
@Composable
fun PostComposer(
    stickers: List<Sticker>,
    templates: List<CheckInTemplate>,
    onPublish: (String, List<Attachment>, CheckIn?) -> Unit,
    onCreateTemplate: (String, String, String, String) -> String?,
    onDeleteTemplate: (String) -> Unit,
    onOpenStudio: () -> Unit,
    placeholder: String = t("Чем хочется поделиться?"),
    hint: String = t("Видят ваши подписчики"),
) {
    var text by rememberSaveable { mutableStateOf("") }
    var media by remember { mutableStateOf<List<Attachment>>(emptyList()) }
    var checkIn by remember { mutableStateOf<CheckIn?>(null) }
    var choosingSticker by remember { mutableStateOf(false) }
    var choosingCheckIn by remember { mutableStateOf(false) }
    val picker = rememberMediaPicker { picked ->
        media = (media + picked).take(AppViewModel.MAX_MEDIA)
    }
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column {
            checkIn?.let { c ->
                Box(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
                    CheckInBadge(c)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .clickable { checkIn = null },
                        contentAlignment = Alignment.Center,
                    ) { Glyph(LineIcon.Close, Palette.Ink, size = 14.dp) }
                }
            }
            GlassField(text, { text = it.take(2000) }, if (checkIn != null) t("Пара слов к чек-ину") else placeholder)
            PendingAttachments(
                media,
                onRemove = { item ->
                    media = media - item
                    MediaFiles.delete(item)
                },
                modifier = Modifier.padding(bottom = 10.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AttachButton(
                    picker, documents = false, size = 40.dp,
                    onSticker = { choosingSticker = true },
                    onCheckIn = { choosingCheckIn = true },
                )
                Spacer(Modifier.width(8.dp))
                // Чек-ин — отдельной кнопкой: им пользуются часто
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .glass(CircleShape, strong = checkIn != null)
                        .clickable { choosingCheckIn = true },
                    contentAlignment = Alignment.Center,
                ) { Glyph(LineIcon.Pin, Palette.Lilac, size = 20.dp) }
                Spacer(Modifier.width(10.dp))
                Text(
                    when {
                        media.isNotEmpty() -> plural(media.size, "вложение", "вложения", "вложений")
                        text.isEmpty() -> hint
                        else -> "${text.length} / 2000"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                    modifier = Modifier.weight(1f),
                )
                GlowButton(t("Поделиться"), enabled = text.isNotBlank() || media.isNotEmpty() || checkIn != null, onClick = {
                    onPublish(text, media, checkIn)
                    text = ""
                    media = emptyList()
                    checkIn = null
                })
            }
        }
    }
    if (choosingSticker) {
        StickerPicker(
            stickers,
            onPick = { media = (media + it).take(AppViewModel.MAX_MEDIA) },
            onDismiss = { choosingSticker = false },
            onOpenStudio = onOpenStudio,
        )
    }
    if (choosingCheckIn) {
        CheckInPicker(
            templates,
            onDone = { checkIn = it },
            onCreate = onCreateTemplate,
            onDelete = onDeleteTemplate,
            onDismiss = { choosingCheckIn = false },
        )
    }
}
