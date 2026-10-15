package ru.wishka.memory.data

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import ru.wishka.memory.i18n.t
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Живые стикеры, как в iOS 17: эффект запекается в картинку (контур,
 * комикс, объём, блеск), а движение — играет при показе (прыжок, пульс,
 * покачивание…). Покадровый рисунок — несколько PNG по кругу. Стикеры
 * собираются в коллекции. Те же эффекты и движения — в StickerFx.swift.
 */
enum class StickerEffect(val id: String, private val ru: String) {
    None("", "Без эффекта"),
    Outline("outline", "Контур"),
    Comic("comic", "Комикс"),
    Puffy("puffy", "Объём"),
    Shiny("shiny", "Блеск"),
    ;

    val title: String get() = t(ru)
}

enum class StickerAnim(val id: String, private val ru: String) {
    None("", "Без движения"),
    Bounce("bounce", "Прыжок"),
    Pulse("pulse", "Пульс"),
    Wiggle("wiggle", "Покачивание"),
    Spin("spin", "Вращение"),
    Float("float", "Парение"),
    Shimmer("shimmer", "Мерцание"),
    ;

    val title: String get() = t(ru)

    companion object {
        fun of(id: String?): StickerAnim = entries.firstOrNull { it.id == id } ?: None
    }
}

/**
 * Положение живого стикера в момент phase (0…1 — один цикл): сдвиг по
 * вертикали (доли размера), масштаб, поворот в градусах, сдвиг блика 0…1.
 * Одна формула для Android и iPhone.
 */
data class StickerPose(val dy: Float = 0f, val scale: Float = 1f, val rotation: Float = 0f, val shine: Float = -1f)

fun StickerAnim.pose(phase: Float): StickerPose {
    val a = (phase * 2 * Math.PI).toFloat()
    return when (this) {
        StickerAnim.None -> StickerPose()
        StickerAnim.Bounce -> StickerPose(dy = -0.08f * kotlin.math.abs(sin(a / 2)), scale = 1f + 0.03f * kotlin.math.abs(sin(a / 2)))
        StickerAnim.Pulse -> StickerPose(scale = 1f + 0.08f * sin(a))
        StickerAnim.Wiggle -> StickerPose(rotation = 9f * sin(a * 2))
        StickerAnim.Spin -> StickerPose(rotation = 360f * phase)
        StickerAnim.Float -> StickerPose(dy = 0.04f * sin(a), rotation = 4f * cos(a))
        StickerAnim.Shimmer -> StickerPose(shine = phase)
    }
}

/** Сколько длится цикл движения, мс. */
fun StickerAnim.periodMs(): Int = when (this) {
    StickerAnim.Spin -> 3200
    StickerAnim.Shimmer -> 1800
    StickerAnim.Float -> 2600
    else -> 1200
}

/** Коллекция стикеров — как наборы в iOS: имя и свои стикеры (Sticker.packId). */
data class StickerPack(val id: String, val name: String)

object StickerFx {
    private const val S = StickerArt.SIZE

    /** Запечь эффект в стикер 512 × 512. Картинка чуть уменьшается, чтобы контуру было куда лечь. */
    fun apply(src: Bitmap, effect: StickerEffect): Bitmap {
        if (effect == StickerEffect.None) return src
        val inner = shrink(src, 0.86f)
        return when (effect) {
            StickerEffect.None -> inner
            StickerEffect.Outline -> outlined(inner, Color.WHITE, S * 0.028f)
            StickerEffect.Comic -> comic(inner)
            StickerEffect.Puffy -> puffy(inner)
            StickerEffect.Shiny -> shiny(inner)
        }
    }

    private fun blank(): Pair<Bitmap, Canvas> {
        val b = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        return b to Canvas(b)
    }

    private fun shrink(src: Bitmap, k: Float): Bitmap {
        val (b, c) = blank()
        val w = S * k
        val o = (S - w) / 2
        c.drawBitmap(src, null, RectF(o, o, o + w, o + w), Paint(Paint.FILTER_BITMAP_FLAG))
        return b
    }

    /** Силуэт стикера, обведённый по кругу: копии маски со сдвигом во все стороны. */
    private fun silhouette(c: Canvas, src: Bitmap, color: Int, r: Float, blur: Float = 0f) {
        val mask = src.extractAlpha()
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            if (blur > 0) maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        }
        val steps = 24
        for (k in 0 until steps) {
            val a = 2 * Math.PI * k / steps
            c.drawBitmap(mask, (cos(a) * r).toFloat(), (sin(a) * r).toFloat(), p)
        }
        c.drawBitmap(mask, 0f, 0f, p)
    }

    fun outlined(src: Bitmap, color: Int, r: Float): Bitmap {
        val (b, c) = blank()
        // Мягкая тень под контуром — стикер «лежит» на сообщении
        silhouette(c, src, 0x55000000, r, r * 0.8f)
        silhouette(c, src, color, r)
        c.drawBitmap(src, 0f, 0f, null)
        return b
    }

    /** Комикс: меньше оттенков, тёмный контур и белая кромка снаружи. */
    private fun comic(src: Bitmap): Bitmap {
        val poster = src.copy(Bitmap.Config.ARGB_8888, true)
        val px = IntArray(S * S)
        poster.getPixels(px, 0, S, 0, 0, S, S)
        for (i in px.indices) {
            val v = px[i]
            val a = v ushr 24
            if (a == 0) continue
            fun q(x: Int) = min(255, (x / 64) * 64 + 32)
            px[i] = (a shl 24) or (q((v shr 16) and 255) shl 16) or (q((v shr 8) and 255) shl 8) or q(v and 255)
        }
        poster.setPixels(px, 0, S, 0, 0, S, S)
        val (b, c) = blank()
        silhouette(c, poster, Color.WHITE, S * 0.034f)
        silhouette(c, poster, 0xFF1A1240.toInt(), S * 0.014f)
        c.drawBitmap(poster, 0f, 0f, null)
        return b
    }

    /** Объём: белый контур, свет сверху слева и тень снизу справа внутри силуэта. */
    private fun puffy(src: Bitmap): Bitmap {
        val base = outlined(src, Color.WHITE, S * 0.03f)
        val (b, c) = blank()
        c.drawBitmap(base, 0f, 0f, null)
        val mask = base.extractAlpha()
        val light = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x66FFFFFF
            maskFilter = BlurMaskFilter(S * 0.04f, BlurMaskFilter.Blur.NORMAL)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
        c.drawBitmap(mask, -S * 0.03f, -S * 0.03f, light)
        val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x40260F55
            maskFilter = BlurMaskFilter(S * 0.05f, BlurMaskFilter.Blur.NORMAL)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
        // Тень у нижнего правого края: маска со сдвигом, только внутри силуэта
        c.drawBitmap(mask, S * 0.035f, S * 0.035f, shade)
        return b
    }

    /** Блеск: голографический перелив розовый → сиреневый → голубой и диагональный блик. */
    private fun shiny(src: Bitmap): Bitmap {
        val base = outlined(src, Color.WHITE, S * 0.024f)
        val (b, c) = blank()
        c.drawBitmap(base, 0f, 0f, null)
        val holo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, S.toFloat(), S.toFloat(), intArrayOf(0x66FF9EDE, 0x55C6A2FF, 0x668CC4FF, 0x55FFD86B, 0x66FF9EDE), null, Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
        c.drawRect(0f, 0f, S.toFloat(), S.toFloat(), holo)
        val band = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(S * 0.25f, 0f, S * 0.55f, S * 0.5f, intArrayOf(0x00FFFFFF, 0x99FFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
        c.drawRect(0f, 0f, S.toFloat(), S.toFloat(), band)
        return b
    }

    /** Вырезанный объект — в квадрат 512 с полями, по центру, без искажений. */
    fun fit(cut: Bitmap): Bitmap {
        val (b, c) = blank()
        val k = min(S * 0.92f / cut.width, S * 0.92f / cut.height)
        val w = cut.width * k
        val h = cut.height * k
        c.drawBitmap(cut, null, RectF((S - w) / 2, (S - h) / 2, (S + w) / 2, (S + h) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
        return b
    }

    /** Обрезать прозрачные поля вокруг объекта — до вписывания в квадрат. */
    fun trim(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        var l = w; var t = h; var r = -1; var bt = -1
        for (y in 0 until h) for (x in 0 until w) if ((px[y * w + x] ushr 24) > 16) {
            l = min(l, x); r = max(r, x); t = min(t, y); bt = max(bt, y)
        }
        if (r < l || bt < t) return src
        return Bitmap.createBitmap(src, l, t, r - l + 1, bt - t + 1)
    }
}
