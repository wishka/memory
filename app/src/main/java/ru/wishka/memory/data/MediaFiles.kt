package ru.wishka.memory.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Фото, видео и документы на телефоне.
 *
 * Пока у Memory нет сервера, вложение копируется в память приложения
 * (files/media): так оно переживает перезапуск и не пропадает, если
 * человек удалит оригинал из галереи. Другие приложения эту папку не
 * видят; наружу файл отдаётся только через FileProvider — например, чтобы
 * открыть документ.
 */
object MediaFiles {
    /** Самый большой файл, который принимаем: 200 МБ. */
    const val MAX_BYTES = 200L * 1024 * 1024

    private fun dir(context: Context): File = File(context.filesDir, "media").apply { mkdirs() }

    /**
     * Скопировать выбранный файл (галерея, документы) в память приложения.
     * kind = null — определить по типу файла. null в ответе — файл не
     * прочитался или слишком большой.
     */
    fun import(context: Context, uri: Uri, kind: MediaKind? = null): Attachment? {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri).orEmpty()
        var name = ""
        var size = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0).orEmpty()
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        }
        if (size > MAX_BYTES) return null
        val type = kind ?: when {
            mime.startsWith("image/") -> MediaKind.Photo
            mime.startsWith("video/") -> MediaKind.Video
            else -> MediaKind.Document
        }
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?: name.substringAfterLast('.', "").ifEmpty { null }
            ?: when (type) {
                MediaKind.Photo -> "jpg"
                MediaKind.Video -> "mp4"
                MediaKind.Document -> "bin"
            }
        val id = UUID.randomUUID().toString()
        val target = File(dir(context), "$id.$ext")
        return try {
            var copied = 0L
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        copied += n
                        if (copied > MAX_BYTES) error("too big")
                        output.write(buffer, 0, n)
                    }
                }
            } ?: return null
            Attachment(id, type, target.absolutePath, name.ifEmpty { target.name }, copied, mime)
        } catch (e: Exception) {
            target.delete()
            null
        }
    }

    /** Новый пустой файл в папке вложений — для записи голосового или картинки итогов. */
    fun newFile(context: Context, ext: String): File = File(dir(context), "${UUID.randomUUID()}.$ext")

    /**
     * Пустой файл для съёмки камерой и адрес, по которому камера его
     * запишет. После съёмки — capturedAttachment().
     */
    fun newCapture(context: Context, kind: MediaKind): Pair<File, Uri> {
        val ext = if (kind == MediaKind.Video) "mp4" else "jpg"
        val file = File(dir(context), "${UUID.randomUUID()}.$ext")
        return file to FileProvider.getUriForFile(context, authority(context), file)
    }

    /** Снятый камерой файл как вложение; null — съёмку отменили. */
    fun captured(file: File, kind: MediaKind): Attachment? {
        if (!file.exists() || file.length() == 0L) {
            file.delete()
            return null
        }
        return Attachment(
            file.nameWithoutExtension, kind, file.absolutePath, file.name, file.length(),
            if (kind == MediaKind.Video) "video/mp4" else "image/jpeg",
        )
    }

    /**
     * Скопировать файл, который уже лежит в памяти приложения (трек из
     * «Моей музыки», кадр), как отдельное вложение: удалят трек — запись
     * не потеряет файл. null — не скопировалось.
     */
    fun copyLocal(context: Context, source: File, kind: MediaKind, name: String, mime: String = ""): Attachment? {
        if (!source.exists() || source.length() > MAX_BYTES) return null
        val id = UUID.randomUUID().toString()
        val target = File(dir(context), "$id.${source.extension.ifEmpty { "bin" }}")
        return runCatching {
            source.copyTo(target)
            Attachment(id, kind, target.absolutePath, name, target.length(), mime)
        }.getOrElse {
            target.delete()
            null
        }
    }

    /** Удалить копию вложения (из удалённого сообщения или черновика). */
    fun delete(attachment: Attachment) {
        runCatching {
            File(attachment.path).delete()
            attachment.frames.forEach { File(it).delete() }
        }
    }

    /** Открыть документ в подходящем приложении. false — открыть нечем. */
    fun open(context: Context, attachment: Attachment): Boolean {
        val file = File(attachment.path)
        if (!file.exists()) return false
        val uri = FileProvider.getUriForFile(context, authority(context), file)
        val mime = attachment.mime.ifEmpty {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "*/*"
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(Intent.createChooser(intent, attachment.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /**
     * «Поделиться» сообщением через системное меню: текст и вложения.
     * Файлы отдаются по временному разрешению через FileProvider.
     */
    fun share(context: Context, text: String, attachments: List<Attachment>) {
        val uris = ArrayList(attachments.map { File(it.path) }.filter { it.exists() }
            .map { FileProvider.getUriForFile(context, authority(context), it) })
        val intent = when {
            uris.isEmpty() -> Intent(Intent.ACTION_SEND).setType("text/plain")
            uris.size == 1 -> Intent(Intent.ACTION_SEND).setType(attachments.first().mime.ifEmpty { "*/*" })
                .putExtra(Intent.EXTRA_STREAM, uris.first())
            else -> Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        if (text.isNotEmpty()) intent.putExtra(Intent.EXTRA_TEXT, text)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(Intent.createChooser(intent, ru.wishka.memory.i18n.t("Поделиться")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            // Делиться некуда — ничего не делаем
        }
    }

    /**
     * Уменьшенная картинка для ленты и чата: фото — с прореживанием при
     * чтении (полноразмерный снимок с камеры занял бы десятки мегабайт
     * памяти), видео — первый кадр. Вызывать не в главном потоке.
     */
    fun thumbnail(attachment: Attachment, maxSide: Int): Bitmap? = runCatching {
        when (attachment.kind) {
            MediaKind.Photo -> decodeUpright(attachment.path, maxSide)
            MediaKind.Video -> {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(attachment.path)
                    retriever.getFrameAtTime(0)?.let { scaleDown(it, maxSide) }
                } finally {
                    retriever.release()
                }
            }
            MediaKind.Document -> null
        }
    }.getOrNull()

    /**
     * Фото с прореживанием до maxSide (примерно) и с поворотом по EXIF:
     * снимок с камеры часто лежит боком, а BitmapFactory тег не читает.
     */
    fun decodeUpright(bytes: ByteArray, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds, maxSide) }) ?: return null
        return upright(raw, runCatching { ExifInterface(bytes.inputStream()) }.getOrNull())
    }

    fun decodeUpright(path: String, maxSide: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val raw = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds, maxSide) }) ?: return null
        upright(raw, runCatching { ExifInterface(path) }.getOrNull())
    }.getOrNull()

    private fun sampleFor(bounds: BitmapFactory.Options, maxSide: Int): Int {
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return sample
    }

    private fun upright(raw: Bitmap, exif: ExifInterface?): Bitmap {
        val degrees = when (exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return raw
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(degrees) }, true)
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val side = maxOf(bitmap.width, bitmap.height)
        if (side <= maxSide) return bitmap
        val k = maxSide.toFloat() / side
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * k).toInt().coerceAtLeast(1), (bitmap.height * k).toInt().coerceAtLeast(1), true)
    }

    private fun authority(context: Context) = "${context.packageName}.files"
}

/** «2,4 МБ» — размер документа. */
fun sizeTitle(bytes: Long): String {
    val en = ru.wishka.memory.i18n.L10n.english
    val hy = ru.wishka.memory.i18n.L10n.armenian
    val locale = ru.wishka.memory.i18n.L10n.locale
    // Единицы: GB/MB/KB по-английски, ԳԲ/ՄԲ/ԿԲ по-армянски, ГБ/МБ/КБ по-русски
    val (gb, mb, kb, b) = when {
        en -> listOf("GB", "MB", "KB", "B")
        hy -> listOf("ԳԲ", "ՄԲ", "ԿԲ", "Բ")
        else -> listOf("ГБ", "МБ", "КБ", "Б")
    }
    return when {
        bytes >= 1024L * 1024 * 1024 -> String.format(locale, "%.1f $gb", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 -> String.format(locale, "%.1f $mb", bytes / (1024.0 * 1024))
        bytes >= 1024 -> "${bytes / 1024} $kb"
        else -> "$bytes $b"
    }
}
