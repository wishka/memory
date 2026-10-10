@file:OptIn(UnstableApi::class)

package ru.wishka.memory.ui.prism.create

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.MediaKind
import ru.wishka.memory.data.VideoEdit
import ru.wishka.memory.data.VideoExporter
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.theme.Palette
import java.io.File
import java.util.UUID

/** Этапы создания PRISM. */
private enum class Stage { Camera, Editor, Publish }

/**
 * Свой PRISM от съёмки до публикации: камера (или галерея) → редактор →
 * подпись и сборка ролика. Исходник после сборки удаляется: в ролик
 * уже вошло всё нужное.
 */
@Composable
fun PrismCreateFlow(vm: AppViewModel, state: UiState) {
    val context = LocalContext.current
    var stage by rememberSaveable { mutableStateOf(Stage.Camera) }
    var source by rememberSaveable { mutableStateOf<String?>(null) }
    var edit by remember { mutableStateOf<VideoEdit?>(null) }

    fun discardSource() {
        source?.let { File(it).delete() }
        source = null
        edit = null
    }

    fun close() {
        discardSource()
        vm.closePrismCreator()
    }

    BackHandler {
        when (stage) {
            Stage.Camera -> close()
            Stage.Editor -> { discardSource(); stage = Stage.Camera }
            Stage.Publish -> stage = Stage.Editor
        }
    }

    // Новый исходник — читаем длительность и размер, начальные правки
    LaunchedEffect(source) {
        val path = source ?: return@LaunchedEffect
        if (edit?.sourcePath == path) return@LaunchedEffect
        val info = withContext(Dispatchers.IO) { VideoExporter.probe(path) }
        if (info == null || info.durationMs < VideoEdit.MIN_DURATION_MS) {
            Toast.makeText(context, t("Видео слишком короткое или не читается"), Toast.LENGTH_SHORT).show()
            discardSource()
            stage = Stage.Camera
            return@LaunchedEffect
        }
        edit = VideoEdit(
            sourcePath = path,
            sourceDurationMs = info.durationMs,
            sourceWidth = info.width,
            sourceHeight = info.height,
            trimEndMs = minOf(info.durationMs, VideoEdit.MAX_DURATION_MS),
        )
    }

    Box(Modifier.fillMaxSize().background(Palette.Void)) {
        when (stage) {
            Stage.Camera -> PrismCamera(
                onVideo = { path -> source = path; stage = Stage.Editor },
                onClose = ::close,
            )
            Stage.Editor -> edit?.let { current ->
                PrismEditor(
                    initial = current,
                    tracks = state.tracks,
                    onBack = { discardSource(); stage = Stage.Camera },
                    onNext = { edited -> edit = edited; stage = Stage.Publish },
                )
            }
            Stage.Publish -> edit?.let { current ->
                PublishPrism(
                    edit = current,
                    musicPath = current.musicTrackId?.let { id -> state.track(id)?.path },
                    // Трек из медиатеки — с лицензией: автора и лицензию нужно указывать
                    musicTitle = current.musicTrackId?.let { id -> state.track(id) }?.let { tr ->
                        val credit = if (tr.artist.isBlank()) tr.title else "${tr.artist} — ${tr.title}"
                        if (tr.license.isBlank()) credit else "$credit · ${tr.license}"
                    } ?: "",
                    onBack = { stage = Stage.Editor },
                    onPublished = { video, cover, caption, music ->
                        discardSource()
                        vm.publishPrism(video, cover, caption, music, current.outputDurationMs.toInt())
                    },
                )
            }
        }
    }
}

/** Подпись, обложка и сборка ролика. */
@Composable
private fun PublishPrism(
    edit: VideoEdit,
    musicPath: String?,
    musicTitle: String,
    onBack: () -> Unit,
    onPublished: (video: Attachment, cover: Attachment?, caption: String, music: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var caption by rememberSaveable { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(-1f) }
    var transformer by remember { mutableStateOf<Transformer?>(null) }
    DisposableEffect(Unit) { onDispose { transformer?.cancel() } }

    val cover by produceState<ImageBitmap?>(null, edit) {
        value = withContext(Dispatchers.IO) { VideoExporter.frame(edit.sourcePath, edit.coverMs, 480)?.asImageBitmap() }
    }

    fun publish() {
        progress = 0f
        transformer = VideoExporter.export(
            context, edit, musicPath,
            onProgress = { progress = it },
            onDone = { file ->
                transformer = null
                if (file == null) {
                    progress = -1f
                    Toast.makeText(context, t("Не удалось собрать ролик. Попробуйте ещё раз."), Toast.LENGTH_LONG).show()
                    return@export
                }
                scope.launch {
                    val coverFile = withContext(Dispatchers.IO) { saveCover(file, edit.coverOutputMs()) }
                    val video = Attachment(file.nameWithoutExtension, MediaKind.Video, file.absolutePath, file.name, file.length(), "video/mp4")
                    onPublished(video, coverFile, caption, musicTitle)
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (progress < 0f) RoundTool(LineIcon.Back, onBack)
            Text(
                t("Публикация"),
                style = MaterialTheme.typography.headlineSmall,
                color = Palette.Ink,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Row {
            Box(
                Modifier.width(108.dp).height(192.dp).clip(RoundedCornerShape(16.dp)).background(Palette.Plum),
            ) {
                cover?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
            Spacer(Modifier.width(14.dp))
            GlassField(
                caption, { caption = it.take(300) }, t("Подпись к ролику"),
                maxLines = 6,
                modifier = Modifier.weight(1f).height(192.dp).glass(RoundedCornerShape(16.dp)),
            )
        }
        Text(
            if (musicTitle.isBlank()) t("Звук: оригинальный") else tf("Музыка: %s", musicTitle),
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Mist,
        )
        Spacer(Modifier.weight(1f))
        if (progress >= 0f) {
            Text(tf("Собираем ролик… %s", "${(progress * 100).toInt()}%"), style = MaterialTheme.typography.labelLarge, color = Palette.Ink)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = Palette.Lilac,
                trackColor = Palette.Plum,
            )
            GhostButton(t("Отмена"), onClick = {
                transformer?.cancel()
                transformer = null
                progress = -1f
            })
        } else {
            GlowButton(t("Опубликовать"), onClick = ::publish, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Обложка — кадр готового ролика (с фильтрами и текстом) в JPEG рядом с вложениями. */
private fun saveCover(video: File, timeMs: Long): Attachment? {
    val frame = VideoExporter.frame(video.absolutePath, timeMs, 1080) ?: return null
    val id = UUID.randomUUID().toString()
    val file = File(video.parentFile, "$id.jpg")
    return runCatching {
        file.outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        Attachment(id, MediaKind.Photo, file.absolutePath, file.name, file.length(), "image/jpeg")
    }.getOrNull()
}
