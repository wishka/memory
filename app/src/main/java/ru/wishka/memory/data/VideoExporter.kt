@file:OptIn(UnstableApi::class)

package ru.wishka.memory.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextureOverlay
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import ru.wishka.memory.R
import java.io.File
import java.util.UUID

/** Что известно о видео до правок: длительность и размер кадра с учётом поворота. */
data class VideoInfo(val durationMs: Long, val width: Int, val height: Int)

/**
 * Сборка ролика PRISM по правкам (VideoEdit) в готовый mp4 на телефоне —
 * Media3 Transformer: обрезка, скорость, поворот, кадр, цвет, текст и
 * стикеры, музыка и громкость. Работает с главного потока: Transformer
 * сам уходит в фон и сообщает о ходе работы.
 */
object VideoExporter {

    /** Длительность и размер видео; null — файл не читается. */
    fun probe(path: String): VideoInfo? = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(path)
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (duration <= 0 || w <= 0 || h <= 0) null
            else if (rotation % 180 != 0) VideoInfo(duration, h, w) else VideoInfo(duration, w, h)
        } finally {
            r.release()
        }
    }.getOrNull()

    /** Кадр видео в момент timeMs — для обложки и полосы обрезки. */
    fun frame(path: String, timeMs: Long, maxSide: Int = 720): Bitmap? = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(path)
            val frame = r.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return@runCatching null
            val side = maxOf(frame.width, frame.height)
            if (side <= maxSide) frame
            else {
                val k = maxSide.toFloat() / side
                Bitmap.createScaledBitmap(frame, (frame.width * k).toInt().coerceAtLeast(1), (frame.height * k).toInt().coerceAtLeast(1), true)
            }
        } finally {
            r.release()
        }
    }.getOrNull()

    /** Поворот, отражение, кадр и цвет — общие для предпросмотра и итогового файла. */
    fun videoEffects(edit: VideoEdit, overlay: Bitmap? = null): List<Effect> {
        val effects = mutableListOf<Effect>()
        if (edit.rotation != 0 || edit.mirror) {
            effects += ScaleAndRotateTransformation.Builder()
                // Media3 крутит против часовой, у нас поворот — по часовой
                .setRotationDegrees(-edit.rotation.toFloat())
                .setScale(if (edit.mirror) -1f else 1f, 1f)
                .build()
        }
        val (w, h) = edit.outputSize()
        effects += Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
        if (!edit.colorIsIdentity) effects += ColorEffect(edit.colorMatrix())
        if (overlay != null) {
            effects += OverlayEffect(listOf<TextureOverlay>(BitmapOverlay.createStaticBitmapOverlay(overlay)))
        }
        return effects
    }

    /** Текст и стикеры одной прозрачной картинкой размером с итоговый кадр; null — их нет. */
    fun overlayBitmap(context: Context, edit: VideoEdit): Bitmap? {
        if (edit.overlays.isEmpty()) return null
        val (w, h) = edit.outputSize()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        OverlayPainter.draw(context, Canvas(bitmap), w, h, edit.overlays)
        return bitmap
    }

    /**
     * Собрать ролик. musicPath — файл трека из «Моей музыки» или null.
     * onProgress — 0…1; onDone — готовый файл или null при ошибке.
     * Ответ — Transformer, им можно отменить сборку (cancel()).
     */
    fun export(
        context: Context,
        edit: VideoEdit,
        musicPath: String?,
        onProgress: (Float) -> Unit,
        onDone: (File?) -> Unit,
    ): Transformer {
        val out = File(File(context.filesDir, "media").apply { mkdirs() }, "${UUID.randomUUID()}.mp4")
        val clip = MediaItem.Builder()
            .setUri(Uri.fromFile(File(edit.sourcePath)))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(edit.trimStartMs)
                    .setEndPositionMs(edit.trimEndMs)
                    .build()
            )
            .build()
        val mute = edit.originalVolume <= 0f
        val audio: List<AudioProcessor> = if (!mute && edit.originalVolume != 1f) listOf(volume(edit.originalVolume)) else emptyList()
        val video = EditedMediaItem.Builder(clip)
            .setRemoveAudio(mute)
            .setEffects(Effects(audio, videoEffects(edit, overlayBitmap(context, edit))))
            .apply { if (edit.speed != 1f) setSpeed(ConstantSpeed(edit.speed)) }
            .build()
        val sequences = mutableListOf(
            if (mute) EditedMediaItemSequence.withVideoFrom(listOf(video))
            else EditedMediaItemSequence.withAudioAndVideoFrom(listOf(video))
        )
        if (musicPath != null) {
            val track = MediaItem.Builder()
                .setUri(Uri.fromFile(File(musicPath)))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setStartPositionMs(edit.musicStartMs).build()
                )
                .build()
            val music = EditedMediaItem.Builder(track)
                .setRemoveVideo(true)
                .setEffects(Effects(listOf(volume(edit.musicVolume)), emptyList()))
                .build()
            sequences += EditedMediaItemSequence.withAudioFrom(listOf(music))
                .buildUpon()
                .setIsLooping(true)
                .build()
        }
        val composition = Composition.Builder(sequences).build()

        val handler = Handler(Looper.getMainLooper())
        var finished = false
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    finished = true
                    onProgress(1f)
                    onDone(out)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    finished = true
                    out.delete()
                    onDone(null)
                }
            })
            .build()
        transformer.start(composition, out.absolutePath)

        // Ход сборки — раз в четверть секунды
        val holder = ProgressHolder()
        handler.post(object : Runnable {
            override fun run() {
                if (finished) return
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress / 100f)
                }
                handler.postDelayed(this, 250)
            }
        })
        return transformer
    }

    /** Громкость: 0 — тишина, 1 — как есть. Для моно и стерео. */
    fun volume(v: Float): AudioProcessor = ChannelMixingAudioProcessor().apply {
        putChannelMixingMatrix(ChannelMixingMatrix.createForConstantGain(1, 1).scaleBy(v))
        putChannelMixingMatrix(ChannelMixingMatrix.createForConstantGain(2, 2).scaleBy(v))
    }

    /** Постоянная скорость для всего ролика. */
    private class ConstantSpeed(private val speed: Float) : SpeedProvider {
        override fun getSpeed(timeUs: Long): Float = speed
        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
    }

    /** Фильтр и коррекция — одна цветовая матрица. */
    class ColorEffect(matrix: FloatArray) : RgbMatrix {
        private val gl = ColorMatrix.toGl(matrix)
        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = gl
    }
}

/** Рисует тексты и стикеры на холсте размером w × h — для итогового кадра. */
object OverlayPainter {
    fun draw(context: Context, canvas: Canvas, w: Int, h: Int, overlays: List<VideoOverlay>) {
        val inter = runCatching { ResourcesCompat.getFont(context, R.font.inter_semibold) }.getOrNull()
        val unbounded = runCatching { ResourcesCompat.getFont(context, R.font.unbounded_regular) }.getOrNull()
        for (o in overlays) {
            val size = o.size * h
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                textAlign = Paint.Align.CENTER
                typeface = when {
                    o.sticker -> Typeface.DEFAULT
                    o.font == OverlayFont.Unbounded -> unbounded ?: Typeface.DEFAULT_BOLD
                    else -> inter ?: Typeface.DEFAULT_BOLD
                }
            }
            val color = o.color.toInt()
            val lines = o.text.split('\n')
            val lineHeight = size * 1.2f
            val cx = o.x * w
            val top = o.y * h - lineHeight * lines.size / 2
            if (!o.sticker && o.style == OverlayStyle.Box) {
                val width = lines.maxOf { paint.measureText(it) }
                val pad = size * 0.35f
                val rect = RectF(cx - width / 2 - pad, top - pad * 0.5f, cx + width / 2 + pad, top + lineHeight * lines.size + pad * 0.5f)
                val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
                canvas.drawRoundRect(rect, size * 0.4f, size * 0.4f, bg)
                paint.color = contrastOn(color)
            } else {
                paint.color = color
                when {
                    o.sticker -> Unit
                    o.style == OverlayStyle.Glow -> paint.setShadowLayer(size * 0.45f, 0f, 0f, color)
                    else -> paint.setShadowLayer(size * 0.12f, 0f, size * 0.04f, Color.argb(140, 0, 0, 0))
                }
            }
            lines.forEachIndexed { i, line ->
                // Базовая линия: от верха строки — на высоту букв
                val baseline = top + lineHeight * i + lineHeight * 0.5f - (paint.descent() + paint.ascent()) / 2
                canvas.drawText(line, cx, baseline, paint)
            }
        }
    }

    /** Чёрный или белый — что читается на цвете фона. */
    fun contrastOn(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return if (0.299 * r + 0.587 * g + 0.114 * b > 150) Color.BLACK else Color.WHITE
    }
}
