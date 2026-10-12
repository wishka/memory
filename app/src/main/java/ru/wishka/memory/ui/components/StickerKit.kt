package ru.wishka.memory.ui.components

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.wishka.memory.data.Gifs
import ru.wishka.memory.data.Giphy
import ru.wishka.memory.data.GiphyGif
import ru.wishka.memory.data.GiphyResult
import ru.wishka.memory.data.StickerPack
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
 * Свой стикер как вложение: копия PNG из «Студии» (у покадрового — все
 * кадры). Копия — чтобы сообщение или запись не потеряли картинку, если
 * стикер потом удалят. Движение и частота кадров едут вместе с ним.
 */
fun stickerAttachment(context: Context, sticker: Sticker): Attachment? {
    val first = MediaFiles.copyLocal(context, File(sticker.path), MediaKind.Photo, "sticker.png", "image/png") ?: return null
    val frames = if (sticker.frames.size > 1) {
        listOf(first.path) + sticker.frames.drop(1).mapNotNull { MediaFiles.copyLocal(context, File(it), MediaKind.Photo, "sticker.png", "image/png")?.path }
    } else emptyList()
    return first.copy(sticker = true, animation = sticker.animation, frames = frames, fps = if (frames.size > 1) sticker.fps else 0)
}

/** Стикер без рамки и фона: прозрачная картинка как есть; живой — двигается, гифка — играет. */
@Composable
fun StickerImage(attachment: Attachment, modifier: Modifier = Modifier, size: Dp = 140.dp, onClick: (() -> Unit)? = null) {
    val box = modifier
        .size(size)
        .then(if (onClick != null) Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick) else Modifier)
    when {
        attachment.isGif -> GifImage(attachment.path, box)
        attachment.animated -> LiveSticker(attachment, box)
        else -> {
            val image = rememberThumbnail(attachment, 512)
            Box(modifier = box, contentAlignment = Alignment.Center) {
                if (image != null) Image(image, contentDescription = t("Стикер"), contentScale = ContentScale.Fit, modifier = Modifier.size(size))
            }
        }
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

/** Вкладки выбора: все стикеры, коллекция или гифки. */
private sealed class PickerTab {
    data object All : PickerTab()
    data class Pack(val id: String) : PickerTab()
    data object Gifs : PickerTab()
}

private enum class GifSource(val ru: String) { Memory("Memory"), Recent("Недавние"), Giphy("GIPHY") }

/**
 * Выбор стикера или гифки. Стикеры — из «Студии», по коллекциям, как
 * наборы на iPhone; гифки — встроенные Memory, недавние, своя из
 * галереи и поиск GIPHY. onPick получает уже скопированное вложение.
 */
@Composable
fun StickerPicker(
    stickers: List<Sticker>,
    onPick: (Attachment) -> Unit,
    onDismiss: () -> Unit,
    onOpenStudio: (() -> Unit)? = null,
    packs: List<StickerPack> = emptyList(),
    recentGifs: List<Attachment> = emptyList(),
    /** Показывать вкладку «GIF». */
    gifs: Boolean = false,
    /** Открыть сразу на гифках. */
    startOnGifs: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf<PickerTab>(if (startOnGifs && gifs) PickerTab.Gifs else PickerTab.All) }
    val shown = when (val t = tab) {
        PickerTab.All -> stickers
        is PickerTab.Pack -> stickers.filter { it.packId == t.id }
        PickerTab.Gifs -> emptyList()
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Palette.Night)
                .padding(16.dp),
        ) {
            Text(if (tab == PickerTab.Gifs) t("Гифки") else t("Мои стикеры"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
            Spacer(Modifier.height(10.dp))
            if (packs.isNotEmpty() || gifs) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(t("Все"), active = tab == PickerTab.All) { tab = PickerTab.All }
                    packs.forEach { p -> Chip(p.name, active = tab == PickerTab.Pack(p.id)) { tab = PickerTab.Pack(p.id) } }
                    if (gifs) Chip("GIF", active = tab == PickerTab.Gifs) { tab = PickerTab.Gifs }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (tab == PickerTab.Gifs) {
                GifPane(recentGifs, onPick = { onPick(it); onDismiss() })
                Spacer(Modifier.height(10.dp))
                GhostButton(t("Закрыть"), onClick = onDismiss)
            } else if (shown.isEmpty()) {
                Text(
                    if (stickers.isEmpty()) t("Стикеров пока нет. Сделайте значок, смайл или стикер из фото в «Студии» профиля.")
                    else t("В этой коллекции пока пусто."),
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
                    items(shown, key = { it.id }) { sticker ->
                        val preview = Attachment(sticker.id, MediaKind.Photo, sticker.path, sticker = true, animation = sticker.animation, frames = sticker.frames, fps = sticker.fps)
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Palette.Plum.copy(alpha = 0.35f))
                                .clickable {
                                    // Копия кадров — не в главном потоке
                                    scope.launch {
                                        withContext(Dispatchers.IO) { stickerAttachment(context, sticker) }?.let(onPick)
                                        onDismiss()
                                    }
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

/** Гифки: встроенные Memory, недавние, своя из галереи и поиск GIPHY. */
@Composable
private fun GifPane(recentAll: List<Attachment>, onPick: (Attachment) -> Unit) {
    val context = LocalContext.current
    // Сообщение с гифкой могли удалить вместе с файлом — такие не показываем
    val recent = remember(recentAll) { recentAll.filter { File(it.path).exists() } }
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf(if (recent.isNotEmpty()) GifSource.Recent else GifSource.Memory) }
    var busy by remember { mutableStateOf(false) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val item = withContext(Dispatchers.IO) { MediaFiles.import(context, uri, MediaKind.Photo) }
            if (item != null) onPick(item) else Toast.makeText(context, t("Не удалось добавить"), Toast.LENGTH_SHORT).show()
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GifSource.entries.forEach { s -> Chip(t(s.ru), active = s == source) { source = s } }
        Chip(t("Из галереи"), active = false) { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.SingleMimeType("image/gif"))) }
    }
    Spacer(Modifier.height(10.dp))
    val grid = Modifier.fillMaxWidth().heightIn(max = 360.dp)
    when (source) {
        GifSource.Memory -> LazyVerticalGrid(GridCells.Fixed(3), grid, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Gifs.BUILT_IN, key = { it.id }) { gif ->
                val file by produceState<File?>(null, gif.id) { value = withContext(Dispatchers.IO) { Gifs.previewFile(context, gif) } }
                GifCell(file?.path, gif.ru) {
                    if (!busy) {
                        busy = true
                        scope.launch {
                            val item = withContext(Dispatchers.IO) { Gifs.builtIn(context, gif) }
                            busy = false
                            item?.let(onPick)
                        }
                    }
                }
            }
        }
        GifSource.Recent -> if (recent.isEmpty()) {
            Text(t("Здесь будут гифки, которые вы отправляли."), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
        } else LazyVerticalGrid(GridCells.Fixed(3), grid, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(recent, key = { it.id }) { gif ->
                GifCell(gif.path, gif.name) {
                    // Копия: недавняя гифка может уйти из списка, а сообщение — останется
                    scope.launch {
                        withContext(Dispatchers.IO) { MediaFiles.copyLocal(context, File(gif.path), MediaKind.Photo, gif.name, "image/gif") }?.let(onPick)
                    }
                }
            }
        }
        GifSource.Giphy -> GiphyPane(grid) { gif ->
            if (!busy) {
                busy = true
                scope.launch {
                    val item = withContext(Dispatchers.IO) { Giphy.download(context, gif) }
                    busy = false
                    if (item != null) onPick(item) else Toast.makeText(context, t("Не удалось скачать гифку"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    if (busy) Text(t("Загружаю…"), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun GifCell(path: String?, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Plum.copy(alpha = 0.35f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (path != null) GifImage(path, Modifier.fillMaxSize(), crop = true)
        else Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Mist)
    }
}

/** Поиск GIPHY: пусто — популярное. Подпись «Powered by GIPHY» — условие GIPHY. */
@Composable
private fun GiphyPane(grid: Modifier, onPick: (GiphyGif) -> Unit) {
    val context = LocalContext.current
    if (!Giphy.available) {
        Text(
            t("Поиск GIPHY появится, когда в сборку добавят ключ GIPHY_API_KEY. Пока — гифки Memory и свои из галереи."),
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Mist,
        )
        return
    }
    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<GiphyResult?>(null) }
    LaunchedEffect(query) {
        delay(if (query.isEmpty()) 0 else 450)
        result = withContext(Dispatchers.IO) { Giphy.search(query) }
    }
    GlassField(query, { query = it.take(50) }, t("Найти гифку"), singleLine = true, modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)))
    Spacer(Modifier.height(8.dp))
    when (val r = result) {
        null -> Text(t("Загружаю…"), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
        is GiphyResult.Failure -> Text(t("GIPHY не отвечает. Проверьте интернет."), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
        is GiphyResult.Gifs -> if (r.gifs.isEmpty()) {
            Text(t("Ничего не нашлось"), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
        } else LazyVerticalGrid(GridCells.Fixed(3), grid, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(r.gifs, key = { it.id }) { gif ->
                val file by produceState<File?>(null, gif.previewUrl) { value = withContext(Dispatchers.IO) { Giphy.cachePreview(context, gif.previewUrl) } }
                GifCell(file?.path, "GIF") { onPick(gif) }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Text("Powered by GIPHY", style = MaterialTheme.typography.labelSmall, color = Palette.Mist)
}
