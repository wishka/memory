package ru.wishka.memory.data

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Вырезать объект с фото — как «Создать стикер» в iOS 17. ML Kit Subject
 * Segmentation: модель приходит через Google Play (первый раз — скачивание).
 * null — объекта не нашлось или модель недоступна; тогда студия предлагает
 * обрезку формой. На iPhone то же делает Vision (SubjectCutout.swift).
 */
object SubjectCutout {
    /** Вырезать и уложить в квадрат стикера. Тяжёлая обрезка полей — не в главном потоке. */
    suspend fun cut(source: Bitmap): Bitmap? {
        val fg = foreground(source) ?: return null
        return withContext(Dispatchers.Default) { StickerFx.fit(StickerFx.trim(fg)) }
    }

    private suspend fun foreground(source: Bitmap): Bitmap? = suspendCancellableCoroutine { cont ->
        val segmenter = runCatching {
            SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
        }.getOrNull()
        if (segmenter == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        cont.invokeOnCancellation { runCatching { segmenter.close() } }
        segmenter.process(InputImage.fromBitmap(source, 0))
            .addOnSuccessListener { result ->
                val fg = result.foregroundBitmap
                segmenter.close()
                if (cont.isActive) cont.resume(fg)
            }
            .addOnFailureListener {
                segmenter.close()
                if (cont.isActive) cont.resume(null)
            }
    }
}
