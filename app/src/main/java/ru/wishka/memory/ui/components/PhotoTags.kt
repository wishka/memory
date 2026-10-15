package ru.wishka.memory.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.Demo
import ru.wishka.memory.data.PhotoTag
import ru.wishka.memory.data.User
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.theme.Palette

/** Где на экране лежит снимок при ContentScale.Fit: левый верхний угол и размер (в dp). */
private data class FitRect(val left: Dp, val top: Dp, val width: Dp, val height: Dp)

private fun fit(boxW: Dp, boxH: Dp, image: ImageBitmap?): FitRect {
    if (image == null || image.width <= 0 || image.height <= 0) return FitRect(0.dp, 0.dp, boxW, boxH)
    val scale = minOf(boxW.value / image.width, boxH.value / image.height)
    val w = (image.width * scale).dp
    val h = (image.height * scale).dp
    return FitRect((boxW - w) / 2, (boxH - h) / 2, w, h)
}

/** Подпись-отметка на фото: имя в «пилюле» над точкой. */
@Composable
private fun TagLabel(name: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Text(
        name,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.66f))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Отметки поверх снимка, разложенного по Fit (для просмотра и редактора). */
@Composable
private fun TagsOverlay(tags: List<PhotoTag>, rect: FitRect, onTagClick: ((PhotoTag) -> Unit)?) {
    tags.forEach { tag ->
        val name = Demo.person(tag.userId)?.name?.substringBefore(' ') ?: return@forEach
        TagLabel(
            name,
            Modifier.offset(x = rect.left + rect.width * tag.x - 30.dp, y = rect.top + rect.height * tag.y - 12.dp),
            onClick = onTagClick?.let { { it(tag) } },
        )
    }
}

/**
 * Отметить людей на фото: коснуться места на снимке и выбрать друга.
 * Нажатие на отметку — убрать её. Отмечать можно только друзей.
 */
@Composable
fun PhotoTagEditor(attachment: Attachment, friends: List<User>, onDone: (List<PhotoTag>) -> Unit, onDismiss: () -> Unit) {
    var tags by remember(attachment.id) { mutableStateOf(attachment.tags) }
    var point by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    val image = rememberThumbnail(attachment, maxSide = 1440)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val rect = fit(maxWidth, maxHeight, image)
                val density = LocalDensity.current
                if (image != null) Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                Box(
                    Modifier.fillMaxSize().pointerInput(rect) {
                        detectTapGestures { offset ->
                            val x = with(density) { offset.x.toDp() }
                            val y = with(density) { offset.y.toDp() }
                            val fx = (x - rect.left) / rect.width
                            val fy = (y - rect.top) / rect.height
                            if (fx in 0f..1f && fy in 0f..1f) point = fx to fy
                        }
                    },
                )
                TagsOverlay(tags, rect) { tag -> tags = tags - tag }
            }
            Row(
                Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Palette.Void.copy(alpha = 0.6f)).clickable(onClickLabel = t("Отмена"), role = Role.Button, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { Glyph(LineIcon.Close, Color.White, description = t("Отмена")) }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (friends.isEmpty()) t("Отмечать можно друзей — пока их нет") else t("Коснитесь человека на фото"),
                    style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.weight(1f),
                )
                GlowButton(t("Готово"), onClick = { onDone(tags) })
            }
            if (tags.isNotEmpty()) {
                Text(
                    t("Нажмите на имя, чтобы убрать отметку"),
                    style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
                )
            }
        }
    }
    point?.let { (x, y) ->
        val already = tags.map { it.userId }.toSet()
        val choices = friends.filter { it.id !in already }
        AlertDialog(
            onDismissRequest = { point = null },
            containerColor = Palette.Night,
            title = { Text(t("Кто это?"), color = Palette.Ink) },
            text = {
                if (choices.isEmpty()) {
                    Text(t("Все друзья уже отмечены"), color = Palette.Mist)
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(choices, key = { it.id }) { u ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .clickable(role = Role.Button) {
                                        tags = (tags + PhotoTag(u.id, x, y)).take(MAX_TAGS)
                                        point = null
                                    }
                                    .padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(u, size = 36.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(u.name, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { point = null }) { Text(t("Отмена"), color = Palette.Lilac) } },
        )
    }
}

/** Сколько человек можно отметить на одном снимке. */
const val MAX_TAGS = 20

/**
 * Отметки при просмотре фото: значок внизу показывает и прячет имена.
 * Нажатие на имя — страница человека.
 */
@Composable
fun PhotoTagsLayer(attachment: Attachment, onPerson: ((Int) -> Unit)?) {
    if (attachment.tags.isEmpty()) return
    var shown by remember(attachment.id) { mutableStateOf(true) }
    val image = rememberThumbnail(attachment, maxSide = 2048)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rect = fit(maxWidth, maxHeight, image)
        if (shown) TagsOverlay(attachment.tags, rect, onPerson?.let { open -> { tag: PhotoTag -> open(tag.userId) } })
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(16.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClickLabel = if (shown) t("Скрыть отметки") else t("Показать отметки"), role = Role.Button) { shown = !shown }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(LineIcon.PersonTag, Color.White, size = 18.dp)
            Spacer(Modifier.width(6.dp))
            Text(tf("Отмечено: %s", attachment.tags.size), style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}

/** Значок «👤 N» на плитке вложения в редакторе записи. */
@Composable
fun TagCountBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.66f)).padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(LineIcon.PersonTag, Color.White, size = 11.dp)
        Text("$count", style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}
