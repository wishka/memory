package ru.wishka.memory.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.io.File
import java.util.UUID
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Свой значок, смайл или стикер: прозрачная картинка PNG 512 × 512 в
 * памяти приложения. Создаётся в «Студии» профиля — из фото, рисунком,
 * в конструкторе смайлов или как значок с текстом.
 */
data class Sticker(
    val id: String,
    val path: String,
    val kind: StickerKind,
    val createdAt: Long = 0,
)

enum class StickerKind(val ru: String) { Photo("Из фото"), Drawing("Рисунок"), Smiley("Смайл"), Badge("Значок") }

/** Форма стикера и фона значка. */
enum class StickerShape(val ru: String) { Circle("Круг"), Heart("Сердце"), Crystal("Кристалл"), Star("Звезда"), Square("Квадрат") }

/** Линия рисунка: точки — доли стороны 0…1. eraser — стирает нарисованное. */
data class DrawStroke(val points: List<Pair<Float, Float>>, val color: Int, val width: Float, val eraser: Boolean = false)

/** Части смайла в конструкторе. */
enum class SmileEyes(val ru: String) { Dots("Точки"), Happy("Радость"), Hearts("Сердечки"), Stars("Звёзды"), Wink("Подмигивание"), Sleepy("Сонные"), Crystal("Кристаллы") }
enum class SmileMouth(val ru: String) { Smile("Улыбка"), Grin("Смех"), Wow("Ого"), Tongue("Язык"), Flat("Ровно"), Sad("Грусть") }
enum class SmileExtra(val ru: String) { None("Ничего"), Blush("Румянец"), Crown("Корона"), Halo("Нимб"), Glasses("Очки"), Tear("Слеза") }

data class SmileyParts(
    val face: StickerShape = StickerShape.Circle,
    val color: Int = STICKER_COLORS[2],
    val eyes: SmileEyes = SmileEyes.Happy,
    val mouth: SmileMouth = SmileMouth.Smile,
    val extra: SmileExtra = SmileExtra.Blush,
)

/** Значок: текст или эмодзи на кристальной подложке. */
data class BadgeParts(
    val text: String = "✦",
    val shape: StickerShape = StickerShape.Crystal,
    val gradient: Int = 0,
    val glass: Boolean = true,
)

/** Фото-стикер: обрезка формой, обводка, подпись. zoom ≥ 1, сдвиг — доли стороны. */
data class PhotoParts(
    val shape: StickerShape = StickerShape.Circle,
    val zoom: Float = 1f,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val outline: Int = 2,
    val outlineColor: Int = Color.WHITE,
    val caption: String = "",
    val captionColor: Int = Color.WHITE,
)

/** Цвета студии — из палитры приложения и несколько ярких. */
val STICKER_COLORS = listOf(
    0xFFFFFFFF.toInt(), 0xFFF3EEFF.toInt(), 0xFFFFD86B.toInt(), 0xFFFF9EDE.toInt(), 0xFFBC92CD.toInt(),
    0xFF8CC4FF.toInt(), 0xFF307EC0.toInt(), 0xFF7BE0B0.toInt(), 0xFFFF8A65.toInt(), 0xFF0B0820.toInt(),
)

/** Подложки значков — пары цветов градиента. */
val BADGE_GRADIENTS = listOf(
    0xFF844CAD.toInt() to 0xFF195699.toInt(),
    0xFFFF9EDE.toInt() to 0xFF844CAD.toInt(),
    0xFF8CC4FF.toInt() to 0xFF5F3F96.toInt(),
    0xFFFFD86B.toInt() to 0xFFFF8A65.toInt(),
    0xFF7BE0B0.toInt() to 0xFF307EC0.toInt(),
    0xFF3C2C59.toInt() to 0xFF07081A.toInt(),
)

/**
 * Рисование стикеров. Одни и те же функции дают предпросмотр в студии и
 * итоговую картинку — что видно, то и сохранится. Те же правила — в
 * Stickers.swift на iPhone.
 */
object StickerArt {
    const val SIZE = 512

    /** Контур формы в квадрате стороной s с отступом inset. */
    fun shapePath(shape: StickerShape, s: Float, inset: Float = 0f): Path {
        val a = inset
        val w = s - inset * 2
        fun x(f: Float) = a + w * f
        fun y(f: Float) = a + w * f
        return Path().apply {
            when (shape) {
                StickerShape.Circle -> addCircle(s / 2, s / 2, w / 2, Path.Direction.CW)
                StickerShape.Square -> addRoundRect(RectF(a, a, a + w, a + w), w * 0.22f, w * 0.22f, Path.Direction.CW)
                StickerShape.Heart -> {
                    moveTo(x(0.5f), y(0.92f))
                    cubicTo(x(0.08f), y(0.64f), x(0.0f), y(0.34f), x(0.16f), y(0.18f))
                    cubicTo(x(0.30f), y(0.04f), x(0.46f), y(0.10f), x(0.5f), y(0.26f))
                    cubicTo(x(0.54f), y(0.10f), x(0.70f), y(0.04f), x(0.84f), y(0.18f))
                    cubicTo(x(1.0f), y(0.34f), x(0.92f), y(0.64f), x(0.5f), y(0.92f))
                    close()
                }
                StickerShape.Crystal -> {
                    // Вытянутый кристалл, как в PRISM и в шапке ленты
                    moveTo(x(0.5f), y(0.02f)); lineTo(x(0.86f), y(0.5f)); lineTo(x(0.5f), y(0.98f)); lineTo(x(0.14f), y(0.5f)); close()
                }
                StickerShape.Star -> {
                    for (i in 0 until 10) {
                        val r = if (i % 2 == 0) w / 2 else w * 0.22f
                        val ang = Math.toRadians((-90 + i * 36).toDouble())
                        val px = s / 2 + (r * cos(ang)).toFloat()
                        val py = s / 2 + w * 0.04f + (r * sin(ang)).toFloat()
                        if (i == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
            }
        }
    }

    private fun paint(color: Int, style: Paint.Style = Paint.Style.FILL, width: Float = 0f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; this.style = style; strokeWidth = width
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }

    private fun blank(): Pair<Bitmap, Canvas> {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        return bitmap to Canvas(bitmap)
    }

    /** Подпись с тёмной обводкой — читается на любом фоне. */
    private fun caption(canvas: Canvas, text: String, color: Int, y: Float, size: Float) {
        if (text.isBlank()) return
        val fill = paint(color).apply { textSize = size; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        var t = text
        while (fill.measureText(t) > SIZE * 0.92f && fill.textSize > 24f) fill.textSize = fill.textSize - 4f
        val stroke = paint(0xCC07081A.toInt(), Paint.Style.STROKE, fill.textSize * 0.16f).apply {
            textSize = fill.textSize; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        canvas.drawText(t, SIZE / 2f, y, stroke)
        canvas.drawText(t, SIZE / 2f, y, fill)
    }

    fun photo(source: Bitmap, p: PhotoParts): Bitmap {
        val (bitmap, canvas) = blank()
        val s = SIZE.toFloat()
        val inset = s * 0.05f
        val path = shapePath(p.shape, s, inset)
        canvas.save()
        canvas.clipPath(path)
        // Фото заполняет форму; масштаб и сдвиг — как выставил человек
        val k = max(s / source.width, s / source.height) * p.zoom
        val w = source.width * k
        val h = source.height * k
        val left = (s - w) / 2 + p.dx * s
        val top = (s - h) / 2 + p.dy * s
        canvas.drawBitmap(source, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
        if (p.outline > 0) {
            canvas.drawPath(path, paint(p.outlineColor, Paint.Style.STROKE, s * 0.018f * p.outline))
        }
        caption(canvas, p.caption, p.captionColor, s * 0.9f, s * 0.12f)
        return bitmap
    }

    fun drawing(strokes: List<DrawStroke>): Bitmap {
        val (bitmap, canvas) = blank()
        val s = SIZE.toFloat()
        for (st in strokes) {
            val pt = paint(st.color, Paint.Style.STROKE, st.width * s)
            if (st.eraser) pt.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            if (st.points.size == 1) {
                val (x, y) = st.points[0]
                pt.style = Paint.Style.FILL
                canvas.drawCircle(x * s, y * s, st.width * s / 2, pt)
                continue
            }
            val path = Path()
            st.points.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x * s, y * s) else path.lineTo(x * s, y * s) }
            canvas.drawPath(path, pt)
        }
        return bitmap
    }

    fun smiley(p: SmileyParts): Bitmap {
        val (bitmap, canvas) = blank()
        val s = SIZE.toFloat()
        val face = shapePath(p.face, s, s * 0.08f)
        val ink = 0xFF2A1840.toInt()
        // Лицо: заливка, лёгкий объём сверху и тёмный контур
        canvas.drawPath(face, paint(p.color))
        canvas.save()
        canvas.clipPath(face)
        val shine = paint(Color.WHITE).apply { alpha = 70 }
        canvas.drawCircle(s * 0.38f, s * 0.30f, s * 0.22f, shine)
        canvas.restore()
        canvas.drawPath(face, paint(ink, Paint.Style.STROKE, s * 0.03f))

        val ey = if (p.face == StickerShape.Heart) s * 0.42f else s * 0.44f
        val lx = s * 0.37f
        val rx = s * 0.63f
        val line = paint(ink, Paint.Style.STROKE, s * 0.035f)
        val fill = paint(ink)
        fun heart(cx: Float, cy: Float, r: Float, color: Int) {
            val hp = shapePath(StickerShape.Heart, r * 2)
            canvas.save(); canvas.translate(cx - r, cy - r); canvas.drawPath(hp, paint(color)); canvas.restore()
        }
        fun star(cx: Float, cy: Float, r: Float, color: Int) {
            val sp = shapePath(StickerShape.Star, r * 2)
            canvas.save(); canvas.translate(cx - r, cy - r); canvas.drawPath(sp, paint(color)); canvas.restore()
        }
        when (p.eyes) {
            SmileEyes.Dots -> { canvas.drawCircle(lx, ey, s * 0.045f, fill); canvas.drawCircle(rx, ey, s * 0.045f, fill) }
            SmileEyes.Happy -> for (cx in listOf(lx, rx)) {
                canvas.drawArc(RectF(cx - s * 0.06f, ey - s * 0.03f, cx + s * 0.06f, ey + s * 0.09f), 200f, 140f, false, line)
            }
            SmileEyes.Hearts -> { heart(lx, ey, s * 0.07f, 0xFFE8457A.toInt()); heart(rx, ey, s * 0.07f, 0xFFE8457A.toInt()) }
            SmileEyes.Stars -> { star(lx, ey, s * 0.075f, 0xFFFFC23D.toInt()); star(rx, ey, s * 0.075f, 0xFFFFC23D.toInt()) }
            SmileEyes.Wink -> {
                canvas.drawCircle(lx, ey, s * 0.045f, fill)
                canvas.drawArc(RectF(rx - s * 0.06f, ey - s * 0.03f, rx + s * 0.06f, ey + s * 0.09f), 200f, 140f, false, line)
            }
            SmileEyes.Sleepy -> for (cx in listOf(lx, rx)) {
                canvas.drawArc(RectF(cx - s * 0.06f, ey - s * 0.06f, cx + s * 0.06f, ey + s * 0.04f), 20f, 140f, false, line)
            }
            SmileEyes.Crystal -> for (cx in listOf(lx, rx)) {
                val d = Path().apply {
                    moveTo(cx, ey - s * 0.07f); lineTo(cx + s * 0.045f, ey); lineTo(cx, ey + s * 0.07f); lineTo(cx - s * 0.045f, ey); close()
                }
                canvas.drawPath(d, paint(0xFF6D4BD8.toInt()))
                canvas.drawPath(d, paint(Color.WHITE, Paint.Style.STROKE, s * 0.008f).apply { alpha = 160 })
            }
        }

        val my = s * 0.64f
        when (p.mouth) {
            SmileMouth.Smile -> canvas.drawArc(RectF(s * 0.36f, my - s * 0.10f, s * 0.64f, my + s * 0.06f), 20f, 140f, false, line)
            SmileMouth.Grin -> {
                val m = Path().apply {
                    moveTo(s * 0.34f, my - s * 0.03f)
                    lineTo(s * 0.66f, my - s * 0.03f)
                    quadTo(s * 0.64f, my + s * 0.14f, s * 0.5f, my + s * 0.14f)
                    quadTo(s * 0.36f, my + s * 0.14f, s * 0.34f, my - s * 0.03f)
                    close()
                }
                canvas.drawPath(m, fill)
                canvas.save(); canvas.clipPath(m)
                canvas.drawCircle(s * 0.5f, my + s * 0.15f, s * 0.07f, paint(0xFFE8457A.toInt()))
                canvas.restore()
            }
            SmileMouth.Wow -> canvas.drawOval(RectF(s * 0.45f, my - s * 0.04f, s * 0.55f, my + s * 0.09f), fill)
            SmileMouth.Tongue -> {
                canvas.drawArc(RectF(s * 0.36f, my - s * 0.10f, s * 0.64f, my + s * 0.06f), 20f, 140f, false, line)
                canvas.drawRoundRect(RectF(s * 0.47f, my + s * 0.03f, s * 0.57f, my + s * 0.13f), s * 0.05f, s * 0.05f, paint(0xFFE8457A.toInt()))
            }
            SmileMouth.Flat -> canvas.drawLine(s * 0.39f, my, s * 0.61f, my, line)
            SmileMouth.Sad -> canvas.drawArc(RectF(s * 0.38f, my, s * 0.62f, my + s * 0.14f), 200f, 140f, false, line)
        }

        when (p.extra) {
            SmileExtra.None -> Unit
            SmileExtra.Blush -> {
                val blush = paint(0xFFFF7FA8.toInt()).apply { alpha = 110 }
                canvas.drawOval(RectF(s * 0.20f, s * 0.52f, s * 0.32f, s * 0.59f), blush)
                canvas.drawOval(RectF(s * 0.68f, s * 0.52f, s * 0.80f, s * 0.59f), blush)
            }
            SmileExtra.Crown -> {
                val c = Path().apply {
                    moveTo(s * 0.32f, s * 0.20f); lineTo(s * 0.36f, s * 0.04f); lineTo(s * 0.44f, s * 0.14f)
                    lineTo(s * 0.5f, s * 0.02f); lineTo(s * 0.56f, s * 0.14f); lineTo(s * 0.64f, s * 0.04f); lineTo(s * 0.68f, s * 0.20f); close()
                }
                canvas.drawPath(c, paint(0xFFFFC23D.toInt()))
                canvas.drawPath(c, paint(ink, Paint.Style.STROKE, s * 0.018f))
            }
            SmileExtra.Halo -> canvas.drawOval(RectF(s * 0.30f, s * 0.01f, s * 0.70f, s * 0.11f), paint(0xFFFFD86B.toInt(), Paint.Style.STROKE, s * 0.03f))
            SmileExtra.Glasses -> {
                val g = paint(ink, Paint.Style.STROKE, s * 0.025f)
                canvas.drawCircle(lx, ey, s * 0.10f, g)
                canvas.drawCircle(rx, ey, s * 0.10f, g)
                canvas.drawLine(lx + s * 0.10f, ey, rx - s * 0.10f, ey, g)
            }
            SmileExtra.Tear -> {
                val t = Path().apply {
                    moveTo(rx + s * 0.02f, ey + s * 0.06f)
                    quadTo(rx + s * 0.08f, ey + s * 0.16f, rx + s * 0.02f, ey + s * 0.19f)
                    quadTo(rx - s * 0.04f, ey + s * 0.16f, rx + s * 0.02f, ey + s * 0.06f)
                    close()
                }
                canvas.drawPath(t, paint(0xFF8CC4FF.toInt()))
            }
        }
        return bitmap
    }

    fun badge(p: BadgeParts): Bitmap {
        val (bitmap, canvas) = blank()
        val s = SIZE.toFloat()
        val path = shapePath(p.shape, s, s * 0.06f)
        val (c1, c2) = BADGE_GRADIENTS[p.gradient.coerceIn(0, BADGE_GRADIENTS.lastIndex)]
        canvas.drawPath(path, paint(c1).apply { shader = LinearGradient(0f, 0f, s, s, c1, c2, Shader.TileMode.CLAMP) })
        if (p.glass) {
            // Кристальное стекло: блик сверху и светлая кромка, как у слова MEMORY
            canvas.save()
            canvas.clipPath(path)
            canvas.drawRect(RectF(0f, 0f, s, s * 0.46f), paint(Color.WHITE).apply {
                shader = LinearGradient(0f, 0f, 0f, s * 0.46f, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            })
            if (p.shape == StickerShape.Crystal) {
                val facet = paint(Color.WHITE, Paint.Style.STROKE, s * 0.008f).apply { alpha = 120 }
                canvas.drawLine(s * 0.14f + s * 0.05f, s * 0.5f, s * 0.86f - s * 0.05f, s * 0.5f, facet)
                canvas.drawLine(s * 0.5f, s * 0.07f, s * 0.56f, s * 0.5f, facet)
                canvas.drawLine(s * 0.56f, s * 0.5f, s * 0.5f, s * 0.93f, facet)
            }
            canvas.restore()
            canvas.drawPath(path, paint(Color.WHITE, Paint.Style.STROKE, s * 0.016f).apply { alpha = 170 })
        }
        val text = p.text.trim().take(8)
        if (text.isNotEmpty()) {
            val fill = paint(Color.WHITE).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
            val target = when (text.codePointCount(0, text.length)) {
                1 -> s * 0.42f
                2 -> s * 0.32f
                3 -> s * 0.26f
                else -> s * 0.20f
            }
            fill.textSize = target
            while (fill.measureText(text) > s * 0.62f && fill.textSize > 20f) fill.textSize = fill.textSize - 4f
            fill.setShadowLayer(s * 0.02f, 0f, s * 0.008f, 0x66000000)
            val cy = s / 2 - (fill.ascent() + fill.descent()) / 2
            canvas.drawText(text, s / 2, cy, fill)
        }
        return bitmap
    }

    /** Сохранить картинку стикером в память приложения. */
    fun save(context: Context, bitmap: Bitmap, kind: StickerKind, now: Long): Sticker? = runCatching {
        val dir = File(context.filesDir, "media").apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val file = File(dir, "sticker-$id.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Sticker(id, file.absolutePath, kind, now)
    }.getOrNull()

    /** Ограничить значение рамками — для жестов в студии. */
    fun clamp(v: Float, lo: Float, hi: Float) = min(hi, max(lo, v))
}
