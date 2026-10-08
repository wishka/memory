package ru.wishka.memory.ui.splash

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.random.Random

/**
 * Слово из кристального стекла: перламутровое тело (розовый, сирень,
 * голубой, бледное золото), косые грани — светлые и тёмные полосы, как
 * сколы на кристалле, светлый внутренний кант, тонкий контур и несколько
 * бликов-звёздочек. Рисуется один раз при запуске заставки.
 *
 * Все размеры — от размера шрифта, поэтому кристалл одинаковый на любом
 * экране.
 */
object CrystalWord {

    /** Перламутр: цвета переливаются по диагонали слова. */
    private val IRIDESCENT = intArrayOf(
        0xFFFF9EDE.toInt(), // розовый
        0xFFC6A2FF.toInt(), // сирень
        0xFF8CC4FF.toInt(), // голубой
        0xFFFFF4FF.toInt(), // жемчужный блик
        0xFFFFDB8F.toInt(), // золото
        0xFFD08CFF.toInt(), // орхидея
        0xFF7AA8FF.toInt(), // небесный
    )

    class Art(
        /** Кристальное слово с ореолом. */
        val crystal: Bitmap,
        /** Белый силуэт слова — по нему пробегает блик. */
        val mask: Bitmap,
        val bounds: RectF,
    )

    fun render(paint: Paint, word: String, width: Int, height: Int, x: Float, baseline: Float): Art {
        val ts = paint.textSize
        val path = Path()
        paint.getTextPath(word, 0, word.length, x, baseline, path)
        // Контуры букв в шрифте могут перекрываться — объединяем, иначе
        // кант и контур рисуются и по внутренним швам глифа
        path.op(Path(), Path.Op.UNION)
        val b = RectF()
        path.computeBounds(b, true)

        val crystal = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(crystal)

        // 1. Ореол: свет, рассеянный стеклом
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xC0C49BFF.toInt()
            maskFilter = BlurMaskFilter(ts * 0.10f, BlurMaskFilter.Blur.NORMAL)
        })

        c.save()
        c.clipPath(path)

        // 2. Тело — перламутровый градиент по диагонали, чуть прозрачный
        c.drawRect(b, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                b.left, b.top, b.left + b.height() * 2.2f, b.bottom,
                IRIDESCENT, null, Shader.TileMode.MIRROR,
            )
            alpha = 250
        })

        // 3. Грани: косые полосы двух наклонов — светлые сколы и тени
        val rnd = Random(5)
        val facet = Paint(Paint.ANTI_ALIAS_FLAG)
        for (slant in floatArrayOf(0.55f, -0.35f)) {
            var fx = b.left - b.height()
            var light = true
            while (fx < b.right + b.height()) {
                val w = ts * (0.05f + rnd.nextFloat() * 0.16f)
                facet.color = if (light) 0x60FFFFFF else 0x3F4A2F9A
                if (slant < 0) facet.alpha = facet.alpha / 2
                val shift = b.height() * slant
                val poly = Path().apply {
                    moveTo(fx, b.top); lineTo(fx + w, b.top)
                    lineTo(fx + w + shift, b.bottom); lineTo(fx + shift, b.bottom); close()
                }
                c.drawPath(poly, facet)
                fx += w + ts * (0.02f + rnd.nextFloat() * 0.12f)
                light = !light
            }
        }

        // 4. Внутренний кант: светлая кромка вдоль края и тень за ней
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ts * 0.12f
            color = 0x40FFFFFF
        })
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ts * 0.05f
            color = 0x668A6FD8
        })
        c.restore()

        // 5. Контур стекла
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ts * 0.014f + 1.2f
            color = 0xFFFFFFFF.toInt()
        })

        // 6. Блики-звёздочки на гранях
        val star = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            strokeWidth = ts * 0.012f + 0.8f
            strokeCap = Paint.Cap.ROUND
        }
        repeat(5) {
            val sx = b.left + rnd.nextFloat() * b.width()
            val sy = b.top + rnd.nextFloat() * b.height()
            val r = ts * (0.04f + rnd.nextFloat() * 0.05f)
            c.drawLine(sx - r, sy, sx + r, sy, star)
            c.drawLine(sx, sy - r, sx, sy + r, star)
            c.drawCircle(sx, sy, r * 0.18f, star)
        }

        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(mask).drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })

        return Art(crystal, mask, b)
    }
}
