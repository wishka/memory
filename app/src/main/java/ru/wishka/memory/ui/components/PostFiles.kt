package ru.wishka.memory.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.items
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.Group
import ru.wishka.memory.data.MediaFiles
import ru.wishka.memory.data.MediaKind
import ru.wishka.memory.data.sizeTitle
import ru.wishka.memory.i18n.t
import ru.wishka.memory.ui.theme.Palette

/**
 * Файлы записи: музыка играет прямо в карточке, документы открываются
 * в подходящем приложении.
 */
@Composable
fun PostFiles(files: List<Attachment>, modifier: Modifier = Modifier) {
    if (files.isEmpty()) return
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        files.forEach { file ->
            if (file.isAudio) {
                AudioRow(file)
            } else {
                DocumentRow(file, Modifier.fillMaxWidth()) {
                    if (!MediaFiles.open(context, file)) {
                        Toast.makeText(context, t("Нет приложения, чтобы открыть файл"), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

/** Трек во вложении: «играть / пауза», название и ход. Плеер живёт, пока строка на экране. */
@Composable
fun AudioRow(file: Attachment, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(file.path) {
        ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(file.path)); prepare() }
    }
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) { player.seekTo(0); player.pause() }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    LaunchedEffect(playing) {
        while (playing) {
            val d = player.duration
            progress = if (d > 0) player.currentPosition.toFloat() / d else 0f
            delay(250)
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Void.copy(alpha = 0.35f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Palette.Orchid, Palette.Ocean)))
                .clickable { if (player.isPlaying) player.pause() else player.play() },
            contentAlignment = Alignment.Center,
        ) { Glyph(if (playing) LineIcon.Pause else LineIcon.Play, Palette.Ink, size = 18.dp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                file.name.substringBeforeLast('.').ifEmpty { t("Трек") },
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Palette.Plum)) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxSize()
                        .background(Brush.horizontalGradient(listOf(Palette.Lilac, Palette.Sky))),
                )
            }
        }
        if (file.size > 0) {
            Spacer(Modifier.width(10.dp))
            Text(sizeTitle(file.size), style = MaterialTheme.typography.labelSmall, color = Palette.Mist)
        }
        Spacer(Modifier.width(4.dp))
        Glyph(LineIcon.Music, Palette.Lilac, size = 18.dp)
    }
}

/** Заставка группы: своё фото или туманность. */
@Composable
fun GroupCover(group: Group, modifier: Modifier = Modifier) {
    val cover = group.cover
    if (cover != null && cover.kind == MediaKind.Photo) {
        MediaTile(cover, modifier, maxSide = 1080)
    } else {
        Nebula(group.seed, modifier)
    }
}

/**
 * Выбор музыки для записи: трек из «Моей музыки» или звуковой файл с
 * телефона. Выбранное копируется отдельным вложением.
 */
@Composable
fun MusicChooser(
    tracks: List<ru.wishka.memory.data.MusicTrack>,
    onPicked: (List<Attachment>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val files = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val items = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                uris.mapNotNull { MediaFiles.import(context, it, MediaKind.Document) }
            }
            if (items.size < uris.size) Toast.makeText(context, t("Не все файлы удалось добавить (до 200 МБ)"), Toast.LENGTH_SHORT).show()
            onPicked(items)
            onDismiss()
        }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Palette.Night)
                .padding(vertical = 16.dp),
        ) {
            Text(t("Музыка"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(8.dp))
            if (tracks.isEmpty()) {
                Text(
                    t("В «Моей музыке» пока нет треков. Можно выбрать файл с телефона."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().height(if (tracks.isEmpty()) 0.dp else 300.dp)) {
                items(tracks, key = { it.id }) { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val title = if (track.artist.isBlank()) track.title else "${track.artist} — ${track.title}"
                                val ext = track.path.substringAfterLast('.', "mp3")
                                val copy = MediaFiles.copyLocal(context, java.io.File(track.path), MediaKind.Document, "$title.$ext", "audio/*")
                                if (copy != null) onPicked(listOf(copy))
                                onDismiss()
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph(LineIcon.Music, Palette.Lilac, size = 20.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.title, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (track.artist.isNotBlank()) {
                                Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(t("Отмена"), onClick = onDismiss)
                GlowButton(t("Файл с телефона"), onClick = { files.launch(arrayOf("audio/*")) })
            }
        }
    }
}
