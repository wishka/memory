package ru.wishka.memory.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.MediaFiles
import ru.wishka.memory.data.MediaKind
import ru.wishka.memory.data.durationTitle
import ru.wishka.memory.i18n.t
import ru.wishka.memory.ui.theme.Palette
import java.io.File
import kotlin.math.log10
import kotlin.math.max

/**
 * Запись голосового: AAC в .m4a в папке вложений. Пока идёт запись,
 * sample() раз в 100 мс берёт громкость — из неё складывается волна.
 */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private val levels = mutableListOf<Int>()

    val recording: Boolean get() = recorder != null

    /** Начать; false — микрофон недоступен. Разрешение спрашивает ChatScreen до вызова. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        cancel()
        val target = MediaFiles.newFile(context, "m4a")
        return runCatching {
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44_100)
            r.setAudioEncodingBitRate(64_000)
            r.setOutputFile(target.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = target
            startedAt = System.currentTimeMillis()
            levels.clear()
            true
        }.getOrElse {
            target.delete()
            recorder = null
            false
        }
    }

    /** Громкость сейчас, 0…100 — по логарифму, как слышит ухо. */
    fun sample(): Int {
        val amp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
        val level = if (amp <= 0) 0 else ((20 * log10(amp / 32767.0) + 48) / 48 * 100).toInt().coerceIn(0, 100)
        if (recorder != null) levels += level
        return level
    }

    val elapsedMs: Long get() = if (recorder == null) 0 else System.currentTimeMillis() - startedAt

    /** Закончить и получить вложение; null — слишком коротко или не записалось. */
    fun finish(): Attachment? {
        val r = recorder ?: return null
        val f = file
        val duration = elapsedMs
        recorder = null
        // Файл теперь принадлежит сообщению: cancel() его больше не тронет
        file = null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        if (!ok || f == null || !f.exists() || duration < MIN_MS) {
            f?.delete()
            return null
        }
        return Attachment(
            f.nameWithoutExtension, MediaKind.Document, f.absolutePath, t("Голосовое сообщение") + ".m4a",
            f.length(), "audio/mp4", voice = true, durationMs = duration, waveform = squeeze(levels, BARS),
        )
    }

    /** Передумали: запись удаляется. */
    fun cancel() {
        recorder?.let { r -> runCatching { r.stop() }; r.release() }
        recorder = null
        file?.delete()
        file = null
    }

    companion object {
        const val MIN_MS = 700L
        const val MAX_MS = 5 * 60_000L
        const val BARS = 40

        /** Ужать уровни до n столбиков: в каждом — громкий пик своей части. */
        fun squeeze(levels: List<Int>, n: Int): List<Int> {
            if (levels.isEmpty()) return List(n) { 8 }
            return List(n) { i ->
                val from = i * levels.size / n
                val to = max(from + 1, (i + 1) * levels.size / n).coerceAtMost(levels.size)
                levels.subList(from.coerceAtMost(levels.size - 1), to).maxOrNull() ?: 0
            }
        }
    }
}

/** Волна: столбики; пройденная часть — светлая, остальное приглушено. */
@Composable
fun Waveform(levels: List<Int>, progress: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (levels.isEmpty()) return@Canvas
        val gap = 2.dp.toPx()
        val w = ((size.width - gap * (levels.size - 1)) / levels.size).coerceAtLeast(1f)
        levels.forEachIndexed { i, v ->
            val h = (size.height * (0.12f + 0.88f * v / 100f)).coerceAtLeast(2f)
            val x = i * (w + gap)
            val played = (i + 0.5f) / levels.size <= progress
            drawRoundRect(
                color.copy(alpha = if (played) 1f else 0.38f),
                topLeft = Offset(x, (size.height - h) / 2),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2, w / 2),
            )
        }
    }
}

/**
 * Голосовое в пузыре: «играть / пауза», волна (нажатие — перемотать
 * туда) и время. Плеер создаётся при первом нажатии — в длинной
 * переписке не держим по плееру на каждое голосовое.
 */
@Composable
fun VoiceBubble(file: Attachment, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var player by remember(file.path) { mutableStateOf<ExoPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var position by remember { mutableLongStateOf(0L) }
    DisposableEffect(file.path) { onDispose { player?.release(); player = null } }
    fun obtain(): ExoPlayer = player ?: ExoPlayer.Builder(context).build().also { p ->
        p.setMediaItem(MediaItem.fromUri(file.path))
        p.prepare()
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) { p.seekTo(0); p.pause(); progress = 0f; position = 0 }
            }
        })
        player = p
    }
    LaunchedEffect(playing) {
        while (playing) {
            val p = player ?: break
            val d = p.duration.takeIf { it > 0 } ?: file.durationMs
            position = p.currentPosition
            progress = if (d > 0) position.toFloat() / d else 0f
            delay(80)
        }
    }
    Row(modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Palette.Orchid, Palette.Ocean)))
                .clickable { val p = obtain(); if (p.isPlaying) p.pause() else p.play() },
            contentAlignment = Alignment.Center,
        ) { Glyph(if (playing) LineIcon.Pause else LineIcon.Play, Palette.Ink, size = 18.dp) }
        Spacer(Modifier.width(10.dp))
        Waveform(
            file.waveform, progress, Palette.Ink,
            Modifier
                .width(150.dp)
                .height(30.dp)
                .pointerInput(file.path) {
                    detectTapGestures { offset ->
                        val p = obtain()
                        val d = file.durationMs.takeIf { it > 0 } ?: p.duration.coerceAtLeast(0)
                        val f = (offset.x / size.width).coerceIn(0f, 1f)
                        p.seekTo((d * f).toLong())
                        progress = f
                        position = (d * f).toLong()
                        if (!p.isPlaying) p.play()
                    }
                },
        )
        Spacer(Modifier.width(10.dp))
        Text(
            durationTitle(if (playing || position > 0) position else file.durationMs),
            style = MaterialTheme.typography.labelLarge,
            color = Palette.Ink.copy(alpha = 0.8f),
        )
    }
}

/**
 * Полоса записи вместо поля ввода: мигающая точка, время, живая волна,
 * «удалить» и «отправить».
 */
@Composable
fun RecordingBar(elapsedMs: Long, levels: List<Int>, onCancel: () -> Unit, onSend: () -> Unit, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "rec")
    val alpha by pulse.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "dot")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(50.dp).clip(CircleShape).glass(CircleShape).clickable(onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) { Glyph(LineIcon.Trash, Palette.PearlPink, size = 22.dp) }
        Spacer(Modifier.width(10.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .glass(RoundedCornerShape(24.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).alpha(alpha).clip(CircleShape).background(Palette.PearlPink))
            Spacer(Modifier.width(8.dp))
            Text(durationTitle(elapsedMs), style = MaterialTheme.typography.labelLarge, color = Palette.Ink)
            Spacer(Modifier.width(10.dp))
            Waveform(levels.takeLast(32), 1f, Palette.Lilac, Modifier.weight(1f).height(26.dp))
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Palette.Orchid, Palette.Ocean)))
                .clickable(onClick = onSend),
            contentAlignment = Alignment.Center,
        ) { Glyph(LineIcon.Send, Palette.Ink) }
    }
}
