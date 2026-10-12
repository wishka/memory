package ru.wishka.memory.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import ru.wishka.memory.i18n.t
import kotlin.random.Random

/**
 * Картинка итогов 1080 × 1350 (как пост в ленте): ночное небо с
 * туманностью и звёздами, период, имя и плитки с цифрами. Её публикуют в
 * ленту, отправляют в чат или сохраняют в галерею. Та же раскладка — в
 * RecapImage.swift.
 */
object RecapImage {
    const val W = 1080
    const val H = 1350

    fun render(recap: Recap, me: User): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // Небо
        p.shader = LinearGradient(0f, 0f, 0f, H.toFloat(), intArrayOf(0xFF07081A.toInt(), 0xFF0F153A.toInt(), 0xFF2A2152.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        // Туманность
        for ((x, y, r, col) in listOf(
            Quad(0.2f, 0.25f, 0.55f, 0x88844CAD.toInt()), Quad(0.85f, 0.15f, 0.45f, 0x66195699.toInt()),
            Quad(0.75f, 0.85f, 0.6f, 0x775F3F96.toInt()), Quad(0.1f, 0.9f, 0.4f, 0x55307EC0.toInt()),
        )) {
            p.shader = RadialGradient(x * W, y * H, r * W, col, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawCircle(x * W, y * H, r * W, p)
        }
        p.shader = null
        // Звёзды — одинаковые для одного периода
        val rnd = Random(recap.key.hashCode())
        repeat(160) {
            p.color = Color.argb(80 + rnd.nextInt(175), 255, 255, 255)
            c.drawCircle(rnd.nextFloat() * W, rnd.nextFloat() * H, 1f + rnd.nextFloat() * 2.6f, p)
        }

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        // Шапка
        text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        text.textSize = 30f
        text.letterSpacing = 0.4f
        text.color = 0xFFBC92CD.toInt()
        c.drawText("MEMORY · " + t("ИТОГИ"), 80f, 130f, text)
        text.letterSpacing = 0f
        text.color = Color.WHITE
        text.textSize = 92f
        text.shader = LinearGradient(80f, 0f, 900f, 0f, intArrayOf(0xFFFF9EDE.toInt(), 0xFFC6A2FF.toInt(), 0xFF8CC4FF.toInt()), null, Shader.TileMode.CLAMP)
        c.drawText(recap.title, 80f, 250f, text)
        text.shader = null
        text.typeface = Typeface.DEFAULT
        text.textSize = 36f
        text.color = 0xFFC9C2E6.toInt()
        c.drawText("${me.name} · @${me.handle}", 80f, 310f, text)

        val tiles = recap.tiles.take(8)
        if (tiles.isEmpty()) {
            text.textSize = 42f
            text.color = Color.WHITE
            var y = 520f
            wrap(t("Отмечайте чек-ины — фильмы, книги, пробежки, места. Здесь появятся ваши цифры."), text, W - 160f).forEach {
                c.drawText(it, 80f, y, text); y += 60f
            }
        } else {
            // Плитки по две: значок, цифра, подпись
            val top = 380f
            val gap = 28f
            val tw = (W - 160f - gap) / 2
            val th = 190f
            val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(28, 255, 255, 255) }
            val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(70, 255, 255, 255) }
            tiles.forEachIndexed { i, tile ->
                val x = 80f + (i % 2) * (tw + gap)
                val y = top + (i / 2) * (th + gap)
                val r = RectF(x, y, x + tw, y + th)
                c.drawRoundRect(r, 36f, 36f, glass)
                c.drawRoundRect(r, 36f, 36f, edge)
                text.textSize = 52f
                text.color = Color.WHITE
                c.drawText(tile.emoji, x + 30f, y + 78f, text)
                text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                text.textSize = 64f
                c.drawText(tile.value, x + 110f, y + 82f, text)
                text.typeface = Typeface.DEFAULT
                text.textSize = 32f
                text.color = 0xFFC9C2E6.toInt()
                wrap(tile.label, text, tw - 60f).take(2).forEachIndexed { k, line -> c.drawText(line, x + 30f, y + 135f + k * 38f, text) }
            }
        }
        // Подвал
        text.typeface = Typeface.DEFAULT
        text.textSize = 34f
        text.color = 0xFFBC92CD.toInt()
        recap.favorite?.let { c.drawText(t("Чаще всего:") + " " + it, 80f, H - 150f, text) }
        text.color = Color.argb(150, 255, 255, 255)
        text.textSize = 28f
        c.drawText("✦ memory", 80f, H - 80f, text)
        return bmp
    }

    /** Сохранить в папку вложений — для записи и сообщения. */
    fun saveAttachment(context: Context, recap: Recap, me: User): Attachment? = runCatching {
        val file = MediaFiles.newFile(context, "png")
        file.outputStream().use { render(recap, me).compress(Bitmap.CompressFormat.PNG, 100, it) }
        Attachment(file.nameWithoutExtension, MediaKind.Photo, file.absolutePath, "memory-recap-${recap.key}.png", file.length(), "image/png")
    }.getOrNull()

    /**
     * В галерею телефона, папка «Pictures/Memory». На Android 9 и раньше
     * без разрешения на память это нельзя — false, тогда предлагаем
     * «Поделиться».
     */
    fun saveToGallery(context: Context, recap: Recap, me: User): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "memory-recap-${recap.key}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Memory")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            context.contentResolver.openOutputStream(uri)?.use { render(recap, me).compress(Bitmap.CompressFormat.PNG, 100, it) } ?: return false
            true
        }.getOrDefault(false)
    }

    private data class Quad(val x: Float, val y: Float, val r: Float, val color: Int)

    /** Перенос строк по ширине. */
    private fun wrap(s: String, paint: Paint, width: Float): List<String> {
        val lines = mutableListOf<String>()
        var line = ""
        for (word in s.split(' ')) {
            val next = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(next) <= width || line.isEmpty()) line = next
            else { lines += line; line = word }
        }
        if (line.isNotEmpty()) lines += line
        return lines
    }
}
