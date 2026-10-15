package ru.wishka.memory.net

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import ru.wishka.memory.data.net.MediaPlan
import ru.wishka.memory.data.net.MediaQuality
import ru.wishka.memory.data.net.UploadJob
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

/**
 * Сжать фото или видео перед отправкой (правила — MediaPlan). Оригинал на
 * телефоне не трогаем: сжатая копия — в кэше приложения, удаляется после
 * отправки. Не вышло сжать — отправляем как есть.
 */
object MediaCompressor {
    private fun outDir(context: Context) = File(context.cacheDir, "uploads").apply { mkdirs() }

    suspend fun prepare(context: Context, job: UploadJob, quality: MediaQuality, wifi: Boolean): UploadJob {
        val src = File(job.path)
        if (!src.exists()) return job
        val out = runCatching {
            if (job.video) video(context, src, job.id, quality, wifi) else photo(context, src, job.id, quality, wifi)
        }.getOrNull() ?: src
        return job.copy(uploadPath = out.absolutePath, size = out.length())
    }

    private suspend fun photo(context: Context, src: File, id: String, quality: MediaQuality, wifi: Boolean): File = withContext(Dispatchers.IO) {
        if (src.name.endsWith(".gif", ignoreCase = true)) return@withContext src
        val plan = MediaPlan.photo(quality, wifi)
        if (plan.maxSide == 0) return@withContext src
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.path, bounds)
        if (bounds.outWidth <= 0) return@withContext src
        // Уже маленькое и лёгкое — не пережимаем (каждое пережатие — потеря качества)
        if (maxOf(bounds.outWidth, bounds.outHeight) <= plan.maxSide && src.length() < 1_500_000) return@withContext src
        val opts = BitmapFactory.Options().apply { inSampleSize = MediaPlan.sampleSize(bounds.outWidth, bounds.outHeight, plan.maxSide) }
        val decoded = BitmapFactory.decodeFile(src.path, opts) ?: return@withContext src
        val (w, h) = MediaPlan.fit(decoded.width, decoded.height, plan.maxSide)
        val rotation = when (ExifInterface(src.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val m = Matrix().apply {
            postScale(w.toFloat() / decoded.width, h.toFloat() / decoded.height)
            // Поворот — в пикселях: EXIF при сжатии теряется, а фото не должно лечь на бок
            if (rotation != 0f) postRotate(rotation)
        }
        val ready = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
        val out = File(outDir(context), "$id.jpg")
        FileOutputStream(out).use { ready.compress(Bitmap.CompressFormat.JPEG, plan.jpegQuality, it) }
        if (ready !== decoded) ready.recycle()
        decoded.recycle()
        // Сжатое вышло больше исходного — шлём исходное
        if (out.length() >= src.length()) { out.delete(); src } else out
    }

    private suspend fun video(context: Context, src: File, id: String, quality: MediaQuality, wifi: Boolean): File {
        val plan = MediaPlan.video(quality, wifi)
        if (plan.height == 0) return src
        val (w, h, bitrate) = withContext(Dispatchers.IO) {
            // Не use {}: AutoCloseable у MediaMetadataRetriever только с Android 10, а minSdk 26
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(src.path)
                val rw = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val rh = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                val br = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
                // Снято вертикально — стороны на экране меняются местами
                if (rot == 90 || rot == 270) Triple(rh, rw, br) else Triple(rw, rh, br)
            } finally {
                r.release()
            }
        }
        if (w <= 0 || h <= 0 || !MediaPlan.needsVideoWork(w, h, bitrate, plan)) return src
        val (tw, th) = MediaPlan.fitVideo(w, h, plan.height)
        val out = File(outDir(context), "$id.mp4")
        val ok = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val item = EditedMediaItem.Builder(MediaItem.fromUri(android.net.Uri.fromFile(src)))
                    .setEffects(Effects(emptyList(), listOf(Presentation.createForWidthAndHeight(tw, th, Presentation.LAYOUT_SCALE_TO_FIT))))
                    .build()
                val composition = Composition.Builder(listOf(EditedMediaItemSequence.withAudioAndVideoFrom(listOf(item)))).build()
                val encoders = DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(plan.bitrate).build())
                    .setEnableFallback(true)
                    .build()
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setEncoderFactory(encoders)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            if (cont.isActive) cont.resume(false)
                        }
                    })
                    .build()
                cont.invokeOnCancellation { transformer.cancel() }
                transformer.start(composition, out.absolutePath)
            }
        }
        return if (ok && out.length() > 0 && out.length() < src.length()) out else { out.delete(); src }
    }

    /** Сжатая копия больше не нужна — запись ушла. */
    fun cleanup(job: UploadJob) {
        if (job.uploadPath.isNotEmpty() && job.uploadPath != job.path) File(job.uploadPath).delete()
    }
}
