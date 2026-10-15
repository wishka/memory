package ru.wishka.memory.data.net

/**
 * Качество фото и видео при отправке — «Настройки → Данные и сеть».
 * Standard — фото до 2048 px, видео 720p; Saver — 1280 px и 480p;
 * OriginalOnWifi — по Wi‑Fi без сжатия, в мобильной сети как Standard.
 */
enum class MediaQuality(val id: String) {
    Standard("standard"),
    Saver("saver"),
    OriginalOnWifi("original");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: Standard
    }
}

/** Во что сжать: 0 — не трогать. */
data class PhotoPlan(val maxSide: Int, val jpegQuality: Int)
data class VideoPlan(val height: Int, val bitrate: Int)

/**
 * Правила сжатия — одинаковые на Android и iPhone (Data/MediaPlan.swift).
 * Только расчёт: сами кодеки — MediaCompressor на каждой платформе.
 */
object MediaPlan {
    fun photo(quality: MediaQuality, wifi: Boolean): PhotoPlan = when {
        quality == MediaQuality.OriginalOnWifi && wifi -> PhotoPlan(0, 95)
        quality == MediaQuality.Saver -> PhotoPlan(1280, 75)
        else -> PhotoPlan(2048, 82)
    }

    fun video(quality: MediaQuality, wifi: Boolean): VideoPlan = when {
        quality == MediaQuality.OriginalOnWifi && wifi -> VideoPlan(0, 0)
        quality == MediaQuality.Saver -> VideoPlan(480, 1_200_000)
        else -> VideoPlan(720, 2_500_000)
    }

    /** Размер после сжатия: длинная сторона не больше maxSide, пропорции те же, не увеличиваем. */
    fun fit(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
        if (maxSide <= 0 || width <= 0 || height <= 0) return width to height
        val long = maxOf(width, height)
        if (long <= maxSide) return width to height
        val k = maxSide.toDouble() / long
        return maxOf(1, Math.round(width * k).toInt()) to maxOf(1, Math.round(height * k).toInt())
    }

    /**
     * Видео: короткая сторона — target (720), обе стороны чётные (так
     * требуют кодеки). Уже меньше — не трогаем.
     */
    fun fitVideo(width: Int, height: Int, target: Int): Pair<Int, Int> {
        if (target <= 0 || width <= 0 || height <= 0) return width to height
        val short = minOf(width, height)
        if (short <= target) return width to height
        val k = target.toDouble() / short
        fun even(v: Double) = maxOf(2, (Math.round(v / 2) * 2).toInt())
        return even(width * k) to even(height * k)
    }

    /** Во сколько раз уменьшить при чтении (степень двойки) — чтобы не держать в памяти 50 Мп. */
    fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        if (maxSide <= 0) return 1
        var s = 1
        while (maxOf(width, height) / (s * 2) >= maxSide) s *= 2
        return s
    }

    /** Стоит ли пересжимать видео: выше цели или битрейт заметно больше. */
    fun needsVideoWork(width: Int, height: Int, bitrate: Int, plan: VideoPlan): Boolean =
        plan.height > 0 && (minOf(width, height) > plan.height || (bitrate > 0 && bitrate > plan.bitrate * 3 / 2))
}
