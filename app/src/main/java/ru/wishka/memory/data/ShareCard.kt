package ru.wishka.memory.data

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
import ru.wishka.memory.i18n.t
import kotlin.random.Random

/**
 * Карточка-картинка для чего угодно: чек-ин, полка, дневник, вылазка.
 * 1080 × 1350 на звёздном небе, как Итоги; внизу — код приглашения, чтобы
 * увидевший картинку мог прийти и сделать своё. Та же раскладка — в
 * ShareCard.swift.
 */
data class ShareCardSpec(
    /** Надпись сверху: «ПОЛКА», «ЧЕК-ИН». */
    val kicker: String,
    val emoji: String,
    val title: String,
    val subtitle: String = "",
    /** Строки-плитки: значок и текст. */
    val lines: List<EchoLine> = emptyList(),
    /** Картинка внутри (готовый рисунок); null — без неё. */
    val picture: Bitmap? = null,
    val author: String = "",
    val inviteCode: String = "",
) {
    /** Для звёзд: у одной карточки — одни и те же. */
    val seed: Int get() = (kicker + title).hashCode()
}

object ShareCard {
    const val W = 1080
    const val H = 1350

    fun render(spec: ShareCardSpec): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, H.toFloat(), intArrayOf(0xFF07081A.toInt(), 0xFF0F153A.toInt(), 0xFF2A2152.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        for ((x, y, r, col) in listOf(
            Glow(0.15f, 0.2f, 0.5f, 0x88844CAD.toInt()), Glow(0.9f, 0.3f, 0.45f, 0x66195699.toInt()), Glow(0.6f, 0.95f, 0.55f, 0x775F3F96.toInt()),
        )) {
            p.shader = RadialGradient(x * W, y * H, r * W, col, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawCircle(x * W, y * H, r * W, p)
        }
        p.shader = null
        val rnd = Random(spec.seed)
        repeat(140) {
            p.color = Color.argb(80 + rnd.nextInt(175), 255, 255, 255)
            c.drawCircle(rnd.nextFloat() * W, rnd.nextFloat() * H, 1f + rnd.nextFloat() * 2.4f, p)
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        text.textSize = 30f
        text.letterSpacing = 0.4f
        text.color = 0xFFBC92CD.toInt()
        c.drawText("MEMORY · " + spec.kicker.uppercase(), 80f, 130f, text)
        text.letterSpacing = 0f
        // Значок и заголовок
        text.typeface = Typeface.DEFAULT
        text.textSize = 120f
        text.color = Color.WHITE
        c.drawText(spec.emoji, 80f, 290f, text)
        text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        text.textSize = 76f
        text.shader = LinearGradient(80f, 0f, 1000f, 0f, intArrayOf(0xFFFF9EDE.toInt(), 0xFFC6A2FF.toInt(), 0xFF8CC4FF.toInt()), null, Shader.TileMode.CLAMP)
        var y = 410f
        wrap(spec.title, text, W - 160f).take(2).forEach { c.drawText(it, 80f, y, text); y += 88f }
        text.shader = null
        text.typeface = Typeface.DEFAULT
        if (spec.subtitle.isNotEmpty()) {
            text.textSize = 38f
            text.color = 0xFFC9C2E6.toInt()
            wrap(spec.subtitle, text, W - 160f).take(2).forEach { c.drawText(it, 80f, y, text); y += 52f }
        }
        y += 24f
        spec.picture?.let { pic ->
            val side = (H - 260f - y).coerceAtMost(W - 160f)
            if (side > 200) {
                val left = (W - side) / 2
                c.drawBitmap(pic, null, RectF(left, y, left + side, y + side), Paint(Paint.FILTER_BITMAP_FLAG))
                y += side + 30f
            }
        }
        // Строки-плитки
        val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(28, 255, 255, 255) }
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(70, 255, 255, 255) }
        for (line in spec.lines) {
            if (y > H - 300f) break
            val r = RectF(80f, y, W - 80f, y + 104f)
            c.drawRoundRect(r, 32f, 32f, glass)
            c.drawRoundRect(r, 32f, 32f, edge)
            text.textSize = 46f
            text.color = Color.WHITE
            c.drawText(line.emoji, 110f, y + 68f, text)
            text.textSize = 36f
            c.drawText(ellipsize(line.text, text, W - 330f), 200f, y + 64f, text)
            y += 124f
        }
        // Подвал: кто и код приглашения
        text.textSize = 34f
        text.color = 0xFFBC92CD.toInt()
        if (spec.author.isNotEmpty()) c.drawText(spec.author, 80f, H - 150f, text)
        text.color = Color.argb(170, 255, 255, 255)
        text.textSize = 30f
        c.drawText("✦ memory", 80f, H - 80f, text)
        if (spec.inviteCode.isNotEmpty()) {
            val label = t("код приглашения") + " " + spec.inviteCode
            c.drawText(label, W - 80f - text.measureText(label), H - 80f, text)
        }
        return bmp
    }

    /** В папку вложений: для «Поделиться» и для записи в ленту. */
    fun save(context: Context, spec: ShareCardSpec, name: String): Attachment? = runCatching {
        val file = MediaFiles.newFile(context, "png")
        file.outputStream().use { render(spec).compress(Bitmap.CompressFormat.PNG, 100, it) }
        Attachment(file.nameWithoutExtension, MediaKind.Photo, file.absolutePath, "memory-$name.png", file.length(), "image/png")
    }.getOrNull()

    private data class Glow(val x: Float, val y: Float, val r: Float, val color: Int)

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

    private fun ellipsize(s: String, paint: Paint, width: Float): String {
        if (paint.measureText(s) <= width) return s
        var cut = s
        while (cut.isNotEmpty() && paint.measureText("$cut…") > width) cut = cut.dropLast(1)
        return "$cut…"
    }
}
