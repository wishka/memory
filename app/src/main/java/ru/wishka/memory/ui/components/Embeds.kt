package ru.wishka.memory.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.CallRecord
import ru.wishka.memory.data.Calls
import ru.wishka.memory.data.MediaKind
import ru.wishka.memory.data.PostRef
import ru.wishka.memory.data.StoryRef
import ru.wishka.memory.data.agoTitle
import ru.wishka.memory.data.minutesSince
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.theme.Palette

/** Картинка из копии: своё фото, демо-туманность или градиент истории. */
@Composable
private fun RefPicture(photo: String, seed: Int, bg: Int?, modifier: Modifier) {
    Box(modifier) {
        when {
            photo.isNotEmpty() -> rememberThumbnail(Attachment("ref", MediaKind.Photo, photo), maxSide = 360)?.let {
                Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } ?: Box(Modifier.fillMaxSize().background(Palette.Plum))
            seed != 0 -> Nebula(seed, Modifier.fillMaxSize())
            bg != null -> Box(Modifier.fillMaxSize().background(ru.wishka.memory.ui.stories.storyBrush(bg)))
            else -> Box(Modifier.fillMaxSize().background(Palette.Plum))
        }
    }
}

/** Над ответом на историю — сама история маленькой картинкой и «Ответ на историю». */
@Composable
fun StoryRefRow(ref: StoryRef, mine: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        RefPicture(ref.photo, ref.seed, ref.bg, Modifier.width(40.dp).height(64.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                if (mine) t("Ответ на историю") else t("Ответ на вашу историю"),
                style = MaterialTheme.typography.labelSmall, color = Palette.Mist,
            )
            Text(ref.preview, style = MaterialTheme.typography.bodyMedium, color = Palette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Запись внутри репоста или сообщения: автор, начало текста и картинка.
 * Нажатие — открыть оригинал.
 */
@Composable
fun PostRefCard(ref: PostRef, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(16.dp))
            .background(Palette.Plum.copy(alpha = 0.25f))
            .clickable(onClickLabel = t("Открыть запись"), role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) { contentDescription = tf("Запись %s: %s", ref.authorName, ref.text.take(80)) }
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                ref.authorName + if (ref.createdAt > 0) " · " + agoTitle(minutesSince(ref.createdAt, 0)) else "",
                style = MaterialTheme.typography.labelLarge, color = Palette.Lilac, maxLines = 1,
            )
            if (ref.text.isNotEmpty()) {
                Text(ref.text, style = MaterialTheme.typography.bodyMedium, color = Palette.Ink, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
        if (ref.photo.isNotEmpty() || ref.seed != 0) {
            Spacer(Modifier.width(10.dp))
            RefPicture(ref.photo, ref.seed, null, Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)))
        }
    }
}

/**
 * Строка звонка в переписке: «Видеозвонок · 3:12», «Пропущенный звонок».
 * Нажатие — перезвонить тем же видом звонка.
 */
@Composable
fun CallRow(record: CallRecord, onCallBack: () -> Unit, modifier: Modifier = Modifier) {
    val bad = record.missed || record.declined
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .glass(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = t("Перезвонить"), role = Role.Button, onClick = onCallBack)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(if (bad) Palette.PearlPink.copy(alpha = 0.2f) else Palette.Violet), contentAlignment = Alignment.Center) {
            Glyph(if (record.video) LineIcon.Video else LineIcon.Phone, if (bad) Palette.PearlPink else Palette.Lilac, size = 18.dp)
        }
        Column {
            Text(Calls.title(record), style = MaterialTheme.typography.bodyMedium, color = if (bad) Palette.PearlPink else Palette.Ink)
            Text(if (record.outgoing) t("Исходящий") else t("Входящий"), style = MaterialTheme.typography.labelSmall, color = Palette.Mist)
        }
    }
}
