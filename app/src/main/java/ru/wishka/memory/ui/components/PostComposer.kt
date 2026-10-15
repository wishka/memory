package ru.wishka.memory.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import ru.wishka.memory.data.QUICK_SCENARIOS
import ru.wishka.memory.data.activity
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
import ru.wishka.memory.data.MusicTrack
import ru.wishka.memory.data.PollDraft
import ru.wishka.memory.data.SharedPlace
import ru.wishka.memory.data.User
import ru.wishka.memory.data.StickerPack
import ru.wishka.memory.data.plural
import ru.wishka.memory.i18n.t
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.theme.Palette

/**
 * Новая запись — в Ленте и в Историях: текст, фото и видео из галереи
 * или с камеры, свои стикеры, гифки, музыка, опрос и чек-ин («смотрю
 * фильм», «пробежал 5 км»).
 * Вложения видны плитками под полем, крестик убирает лишнее.
 */
@Composable
fun PostComposer(
    stickers: List<Sticker>,
    templates: List<CheckInTemplate>,
    onPublish: (String, List<Attachment>, CheckIn?, PollDraft?, SharedPlace?) -> Unit,
    onCreateTemplate: (String, String, String, String) -> String?,
    onDeleteTemplate: (String) -> Unit,
    onOpenStudio: () -> Unit,
    placeholder: String = t("Чем хочется поделиться?"),
    hint: String = t("Видно в ленте"),
    /** «Моя музыка» — для пункта «Музыка». */
    tracks: List<MusicTrack> = emptyList(),
    packs: List<StickerPack> = emptyList(),
    recentGifs: List<Attachment> = emptyList(),
    /** Кого можно отметить на фото — друзья. */
    friends: List<User> = emptyList(),
) {
    var text by rememberSaveable { mutableStateOf("") }
    var media by remember { mutableStateOf<List<Attachment>>(emptyList()) }
    var checkIn by remember { mutableStateOf<CheckIn?>(null) }
    var choosingSticker by remember { mutableStateOf(false) }
    var choosingCheckIn by remember { mutableStateOf(false) }
    var choosingGif by remember { mutableStateOf(false) }
    var choosingMusic by remember { mutableStateOf(false) }
    var editingPoll by remember { mutableStateOf(false) }
    var poll by remember { mutableStateOf<PollDraft?>(null) }
    var scenario by remember { mutableStateOf<String?>(null) }
    var place by remember { mutableStateOf<SharedPlace?>(null) }
    var choosingPlace by remember { mutableStateOf(false) }
    var tagging by remember { mutableStateOf<Attachment?>(null) }
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
                    ) { Glyph(LineIcon.Close, Palette.Ink, size = 14.dp, description = LineIcon.Close.label) }
                }
            }
            // Не «чистый лист»: сначала готовые сценарии — одно нажатие, и запись наполовину готова
            if (checkIn == null && text.isEmpty() && media.isEmpty() && poll == null) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QUICK_SCENARIOS.mapNotNull { activity(it) }.forEach { a ->
                        ScenarioChip(a.emoji, t(SCENARIO_TITLES[a.id] ?: a.title)) { scenario = a.id; choosingCheckIn = true }
                    }
                    ScenarioChip("＋", t("Другое")) { scenario = null; choosingCheckIn = true }
                }
            }
            GlassField(text, { text = it.take(2000) }, if (checkIn != null) t("Пара слов к чек-ину") else if (media.isEmpty()) t("…или просто напишите") else placeholder)
            MentionSuggestions(text, { text = it.take(2000) }, Modifier.padding(horizontal = 12.dp))
            poll?.let { p -> PendingPoll(p, onRemove = { poll = null }, modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp).fillMaxWidth()) }
            place?.let { p ->
                Row(
                    Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp).clip(RoundedCornerShape(50)).glass(RoundedCornerShape(50)).padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(LineIcon.Globe, Palette.Lilac, size = 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(p.title.ifEmpty { t("Место на карте") }, style = MaterialTheme.typography.labelLarge, color = Palette.Ink, maxLines = 1)
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).clickable(onClickLabel = t("Убрать место")) { place = null },
                        contentAlignment = Alignment.Center,
                    ) { Glyph(LineIcon.Close, Palette.Mist, size = 14.dp, description = t("Убрать место")) }
                }
            }
            PendingAttachments(
                media,
                onRemove = { item ->
                    media = media - item
                    MediaFiles.delete(item)
                },
                modifier = Modifier.padding(bottom = 10.dp),
                onTag = { tagging = it },
            )
            if (media.any { it.kind == ru.wishka.memory.data.MediaKind.Photo && !it.sticker && !it.isGif } && media.none { it.tags.isNotEmpty() }) {
                Text(
                    t("Нажмите на фото, чтобы отметить друзей"),
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Mist,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
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
                    onMusic = { choosingMusic = true },
                    onGif = { choosingGif = true },
                    onPoll = { editingPoll = true },
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
                ) { Glyph(LineIcon.Pin, Palette.Lilac, size = 20.dp, description = LineIcon.Pin.label) }
                Spacer(Modifier.width(8.dp))
                // Место — подпись «📍 …» под шапкой записи и точка на карте
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .glass(CircleShape, strong = place != null)
                        .clickable(onClickLabel = t("Добавить место")) { choosingPlace = true },
                    contentAlignment = Alignment.Center,
                ) { Glyph(LineIcon.Globe, Palette.Lilac, size = 20.dp, description = t("Добавить место")) }
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
                GlowButton(t("Поделиться"), enabled = text.isNotBlank() || media.isNotEmpty() || checkIn != null || poll != null, onClick = {
                    onPublish(text, media, checkIn, poll, place)
                    text = ""
                    media = emptyList()
                    checkIn = null
                    poll = null
                    place = null
                })
            }
        }
    }
    if (choosingSticker || choosingGif) {
        StickerPicker(
            stickers,
            onPick = { media = (media + it).take(AppViewModel.MAX_MEDIA) },
            onDismiss = { choosingSticker = false; choosingGif = false },
            onOpenStudio = onOpenStudio,
            packs = packs,
            recentGifs = recentGifs,
            gifs = true,
            startOnGifs = choosingGif,
        )
    }
    if (choosingMusic) {
        MusicChooser(
            tracks = tracks,
            onPicked = { picked -> media = (media + picked).take(AppViewModel.MAX_MEDIA) },
            onDismiss = { choosingMusic = false },
        )
    }
    if (choosingPlace) PlacePickerDialog(place, onDone = { place = it; choosingPlace = false }, onDismiss = { choosingPlace = false })
    tagging?.let { item ->
        PhotoTagEditor(item, friends, onDone = { tags ->
            media = media.map { if (it.id == item.id) it.copy(tags = tags) else it }
            tagging = null
        }, onDismiss = { tagging = null })
    }
    if (editingPoll) PollEditorDialog(onDismiss = { editingPoll = false }, onCreate = { poll = it })
    if (choosingCheckIn) {
        CheckInPicker(
            templates,
            onDone = { checkIn = it },
            onCreate = onCreateTemplate,
            onDelete = onDeleteTemplate,
            onDismiss = { choosingCheckIn = false; scenario = null },
            start = scenario,
        )
    }
}

/** Как сценарии звучат на кнопках: действие, а не занятие. */
private val SCENARIO_TITLES = mapOf(
    "movie" to "Посмотрел фильм",
    "run" to "Пробежка",
    "book" to "Книга",
    "place" to "Я здесь",
)

@Composable
private fun ScenarioChip(emoji: String, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .glass(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 18.sp)
        Spacer(Modifier.width(6.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, color = Palette.Ink)
    }
}

/**
 * Место для записи: название и, по желанию, точка «где я сейчас».
 * Без точки место — просто подпись; с точкой — открывается на карте.
 */
@Composable
fun PlacePickerDialog(current: SharedPlace?, onDone: (SharedPlace?) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(current?.title.orEmpty()) }
    var at by remember { mutableStateOf(current?.takeIf { it.lat != 0.0 || it.lon != 0.0 }?.at) }
    val locate = rememberLocate()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Night,
        title = { Text(t("Где это было?"), color = Palette.Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassField(
                    title, { title = it.take(80) }, t("Название: кафе, парк, город"), singleLine = true,
                    modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(50)),
                )
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).glass(RoundedCornerShape(14.dp), strong = at != null)
                        .clickable(enabled = !locate.busy) { locate.locate { found -> if (found != null) at = found } }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(LineIcon.Pin, Palette.Lilac, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            locate.busy -> t("Ищем, где вы…")
                            at != null -> t("Точка на карте добавлена")
                            else -> t("Отметить, где я сейчас")
                        },
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Ink,
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = title.isNotBlank() || at != null,
                onClick = { onDone(SharedPlace(at?.lat ?: 0.0, at?.lon ?: 0.0, title.trim())) },
            ) { Text(t("Готово"), color = Palette.Lilac) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = { if (current != null) onDone(null) else onDismiss() }) {
                Text(if (current != null) t("Убрать") else t("Отмена"), color = Palette.Mist)
            }
        },
    )
}
