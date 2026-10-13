package ru.wishka.memory.ui.prism.create

import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextOverflow
import ru.wishka.memory.data.MusicTrack
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.wishka.memory.data.MediaFiles
import ru.wishka.memory.data.MediaKind
import ru.wishka.memory.data.VideoEdit
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.theme.Palette
import java.io.File
import java.util.UUID

/** Таймер перед началом записи: выкл., 3 или 10 секунд. */
val CAMERA_TIMERS = listOf(0, 3, 10)

/** Сколько длится запись: 15, 30 или 60 секунд. */
val CAMERA_LENGTHS = listOf(15, 30, 60)

/**
 * Камера PRISM: передняя или задняя, вспышка, таймер перед записью,
 * длина ролика, пауза и продолжение записи (кнопка записи), выбор видео
 * из галереи. Музыку можно выбрать до съёмки — из «Моей музыки» или
 * медиатеки: она звучит во время записи (под неё удобно снимать) и сразу
 * стоит в редакторе. Готовое видео — путь к файлу и выбранный трек —
 * уходит в onVideo.
 */
@Composable
fun PrismCamera(
    /** Memory+: ролики до 60 секунд. */
    plus: Boolean,
    onLocked: (ru.wishka.memory.data.PremiumFeature) -> Unit,
    tracks: List<MusicTrack>,
    onAddTracks: (List<MusicTrack>) -> Unit,
    onVideo: (path: String, music: MusicTrack?) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    var cameraAllowed by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        cameraAllowed = result[Manifest.permission.CAMERA] == true || granted(Manifest.permission.CAMERA)
    }
    LaunchedEffect(Unit) {
        if (!cameraAllowed || !granted(Manifest.permission.RECORD_AUDIO)) {
            permissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.VIDEO_CAPTURE)
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
    }
    LaunchedEffect(cameraAllowed) { if (cameraAllowed) controller.bindToLifecycle(owner) }
    DisposableEffect(Unit) { onDispose { controller.unbind() } }

    var front by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    var timer by remember { mutableIntStateOf(0) }
    var length by remember { mutableIntStateOf(30) }
    var countdown by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var paused by remember { mutableStateOf(false) }
    var recordedMs by remember { mutableLongStateOf(0L) }
    var file by remember { mutableStateOf<File?>(null) }
    var importing by remember { mutableStateOf(false) }
    var music by remember { mutableStateOf<MusicTrack?>(null) }
    var choosingMusic by remember { mutableStateOf(false) }

    // Музыка во время записи: играет с начала, на паузе — молчит
    val musicPlayer = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(musicPlayer) { onDispose { musicPlayer.release() } }
    LaunchedEffect(music) {
        val m = music
        if (m == null) musicPlayer.stop() else { musicPlayer.setMediaItem(MediaItem.fromUri(m.path)); musicPlayer.prepare() }
    }

    fun finish() {
        recording?.stop()
        musicPlayer.pause()
    }

    // Звук пишется, только если разрешение на микрофон уже выдано (granted)
    @SuppressLint("MissingPermission")
    fun startRecording() {
        val target = File(File(context.filesDir, "media").apply { mkdirs() }, "${UUID.randomUUID()}.mp4")
        file = target
        recordedMs = 0
        paused = false
        val audio = if (granted(Manifest.permission.RECORD_AUDIO)) AudioConfig.create(true) else AudioConfig.AUDIO_DISABLED
        recording = controller.startRecording(
            FileOutputOptions.Builder(target).build(),
            audio,
            ContextCompat.getMainExecutor(context),
        ) { event ->
            when (event) {
                is VideoRecordEvent.Status -> {
                    recordedMs = event.recordingStats.recordedDurationNanos / 1_000_000
                    if (recordedMs >= length * 1000L) finish()
                }
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    val ok = !event.hasError() || event.error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED
                    musicPlayer.pause()
                    if (ok && target.exists() && target.length() > 0) onVideo(target.absolutePath, music)
                    else {
                        target.delete()
                        Toast.makeText(context, t("Не удалось записать видео"), Toast.LENGTH_SHORT).show()
                    }
                }
                else -> Unit
            }
        }
    }

    /** Кнопка записи: старт (с таймером), пауза, продолжение. */
    fun onShutter() {
        val current = recording
        when {
            countdown > 0 -> Unit
            current == null -> scope.launch {
                for (i in timer downTo 1) {
                    countdown = i
                    delay(1000)
                }
                countdown = 0
                if (music != null) { musicPlayer.seekTo(0); musicPlayer.play() }
                startRecording()
            }
            paused -> { current.resume(); paused = false; if (music != null) musicPlayer.play() }
            else -> { current.pause(); paused = true; musicPlayer.pause() }
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            val item = withContext(Dispatchers.IO) { MediaFiles.import(context, uri, MediaKind.Video) }
            importing = false
            if (item != null) onVideo(item.path, music)
            else Toast.makeText(context, t("Не удалось открыть видео (до 200 МБ)"), Toast.LENGTH_SHORT).show()
        }
    }

    Box(Modifier.fillMaxSize().background(Palette.Void)) {
        if (cameraAllowed) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        this.controller = controller
                    }
                },
            )
        } else {
            Text(
                t("Нет доступа к камере. Разрешите его в настройках телефона или выберите видео из галереи."),
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.Mist,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        }

        // Верх: закрыть, вспышка, таймер, длина, смена камеры
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CamButton(LineIcon.Close) { recording?.stop(); musicPlayer.stop(); onClose() }
            Spacer(Modifier.weight(1f))
            if (recording == null) {
                CamChip(if (timer == 0) t("Таймер") else tf("%s с", timer), active = timer > 0) {
                    timer = CAMERA_TIMERS[(CAMERA_TIMERS.indexOf(timer) + 1) % CAMERA_TIMERS.size]
                }
                CamChip(tf("%s с", length) + if (!plus && length == 30) " · 60 PLUS" else "", active = true) {
                    val next = CAMERA_LENGTHS[(CAMERA_LENGTHS.indexOf(length) + 1) % CAMERA_LENGTHS.size]
                    // 60 секунд — Memory+: без него после 30 — снова 15 и шторка
                    if (next !in ru.wishka.memory.data.Premium.FREE_LENGTHS && !plus) {
                        onLocked(ru.wishka.memory.data.PremiumFeature.PrismLong)
                        length = CAMERA_LENGTHS.first()
                    } else length = next
                }
            }
            CamButton(LineIcon.Flash, active = torch) {
                torch = !torch
                controller.enableTorch(torch)
            }
            if (recording == null) {
                CamButton(LineIcon.Flip) {
                    front = !front
                    controller.cameraSelector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                }
            }
        }

        // Музыка для ролика: выбрать до съёмки — звучит во время записи
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 64.dp)
                .clip(RoundedCornerShape(50))
                .glass(RoundedCornerShape(50), strong = music != null)
                .clickable(enabled = recording == null) { choosingMusic = true }
                .padding(start = 14.dp, end = if (music != null && recording == null) 6.dp else 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(LineIcon.Music, if (music != null) Palette.Ink else Palette.Lilac, size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                music?.let { if (it.artist.isBlank()) it.title else "${it.artist} — ${it.title}" } ?: t("Добавить музыку"),
                style = MaterialTheme.typography.labelLarge,
                color = Palette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp),
            )
            if (music != null && recording == null) {
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape).clickable { music = null },
                    contentAlignment = Alignment.Center,
                ) { Glyph(LineIcon.Close, Palette.Mist, size = 14.dp) }
            }
        }

        // Обратный отсчёт
        if (countdown > 0) {
            Text(
                "$countdown",
                fontSize = 120.sp,
                color = Palette.Ink,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // Низ: галерея, запись, готово
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                when {
                    recording == null -> t("Нажмите, чтобы снимать")
                    paused -> tf("Пауза · %s", seconds(recordedMs))
                    else -> tf("%s / %s", seconds(recordedMs), seconds(length * 1000L))
                },
                style = MaterialTheme.typography.labelLarge,
                color = Palette.Ink,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    if (recording == null) {
                        CamButton(LineIcon.Image, size = 56) {
                            if (!importing) gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        }
                    }
                }
                Shutter(
                    progress = recordedMs / (length * 1000f),
                    recording = recording != null,
                    paused = paused,
                    onClick = ::onShutter,
                )
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    if (recording != null && recordedMs >= VideoEdit.MIN_DURATION_MS) {
                        CamButton(LineIcon.Check, size = 56, active = true) { finish() }
                    }
                }
            }
        }
    }

    if (choosingMusic) {
        MusicPicker(
            tracks = tracks,
            onAddTracks = onAddTracks,
            onPick = { picked -> music = picked; choosingMusic = false },
            onDismiss = { choosingMusic = false },
        )
    }
}

private fun seconds(ms: Long): String = "0:${(ms / 1000).toString().padStart(2, '0')}"

/** Большая кнопка записи с кольцом хода записи. */
@Composable
private fun Shutter(progress: Float, recording: Boolean, paused: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(84.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            drawCircle(Palette.Ink.copy(alpha = 0.35f), radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
            if (recording) {
                drawArc(
                    Brush.sweepGradient(listOf(Palette.Lilac, Palette.Sky, Palette.Orchid, Palette.Lilac)),
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(if (recording && !paused) 30.dp else 62.dp)
                .clip(if (recording && !paused) RoundedCornerShape(8.dp) else CircleShape)
                .background(Brush.linearGradient(listOf(Palette.Orchid, Palette.Ocean))),
        )
    }
}

@Composable
private fun CamButton(icon: LineIcon, size: Int = 44, active: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .glass(CircleShape, strong = active)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(icon, if (active) Palette.Ink else Palette.Lilac, size = (size * 0.45f).dp) }
}

@Composable
private fun CamChip(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (active) Palette.Ink else Palette.Lilac,
        modifier = Modifier
            .glass(RoundedCornerShape(50), strong = active)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
