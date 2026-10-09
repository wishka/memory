package ru.wishka.memory.data

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Правки ролика в редакторе PRISM. Видео не меняется, пока человек
 * правит: правки — это описание, по нему строится предпросмотр и
 * итоговый файл (VideoExporter). Те же правила — в VideoEdit.swift на
 * iPhone, чтобы ролик выглядел одинаково на обоих телефонах.
 */
data class VideoEdit(
    /** Исходное видео (снятое камерой или из галереи) — копия в памяти приложения. */
    val sourcePath: String,
    val sourceDurationMs: Long,
    /** Размер кадра с учётом поворота, записанного камерой. */
    val sourceWidth: Int,
    val sourceHeight: Int,
    val trimStartMs: Long = 0,
    val trimEndMs: Long = sourceDurationMs,
    val speed: Float = 1f,
    /** Поворот по часовой стрелке: 0, 90, 180, 270. */
    val rotation: Int = 0,
    val mirror: Boolean = false,
    val aspect: Aspect = Aspect.Portrait,
    val filter: VideoFilter = VideoFilter.None,
    /** Коррекция: −1…1, 0 — без изменений. */
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val overlays: List<VideoOverlay> = emptyList(),
    /** Музыка из «Моей музыки»; null — без музыки. */
    val musicTrackId: String? = null,
    val musicStartMs: Long = 0,
    val musicVolume: Float = 0.8f,
    /** Громкость звука самого видео: 0 — без звука. */
    val originalVolume: Float = 1f,
    /** Кадр-обложка — время в исходном видео. */
    val coverMs: Long = 0,
) {
    /** Сколько длится итоговый ролик. */
    val outputDurationMs: Long get() = ((trimEndMs - trimStartMs) / speed).toLong().coerceAtLeast(0)

    /** Кадр с учётом поворота пользователя. */
    private val turnedWidth: Int get() = if (rotation % 180 == 0) sourceWidth else sourceHeight
    private val turnedHeight: Int get() = if (rotation % 180 == 0) sourceHeight else sourceWidth

    /**
     * Размер итогового кадра. Для заданных пропорций — 1080 по ширине;
     * «Оригинал» — как снято, длинная сторона не больше 1920. Стороны
     * чётные: так требует кодек H.264.
     */
    fun outputSize(): Pair<Int, Int> {
        val (w, h) = if (aspect == Aspect.Original) {
            val k = minOf(1f, MAX_SIDE.toFloat() / max(turnedWidth, turnedHeight).coerceAtLeast(1))
            (turnedWidth * k).roundToInt() to (turnedHeight * k).roundToInt()
        } else {
            1080 to (1080f * aspect.h / aspect.w).roundToInt()
        }
        return even(w) to even(h)
    }

    /** Пропорции кадра (ширина / высота) — для предпросмотра. */
    fun outputAspect(): Float = outputSize().let { (w, h) -> w.toFloat() / h }

    /** Цвет: фильтр и коррекция одной матрицей 3×4 (см. ColorMatrix). */
    fun colorMatrix(): FloatArray = with(ColorMatrix) { adjust(brightness, contrast, saturation, warmth).then(filter.matrix) }

    /** Цвет не меняется — эффект можно не добавлять. */
    val colorIsIdentity: Boolean
        get() = filter == VideoFilter.None && brightness == 0f && contrast == 0f && saturation == 0f && warmth == 0f

    /** Время обложки в итоговом ролике. */
    fun coverOutputMs(): Long = ((coverMs.coerceIn(trimStartMs, trimEndMs) - trimStartMs) / speed).toLong()

    private fun even(n: Int) = max(2, n - n % 2)

    companion object {
        const val MAX_SIDE = 1920
        /** Самый длинный PRISM. */
        const val MAX_DURATION_MS = 60_000L
        const val MIN_DURATION_MS = 1_000L
        val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.5f, 2f, 3f)
    }
}

/** Пропорции кадра. */
enum class Aspect(val w: Int, val h: Int, val title: String) {
    Portrait(9, 16, "9:16"),
    Tall(4, 5, "4:5"),
    Square(1, 1, "1:1"),
    Original(0, 0, "Оригинал"),
}

/** Текст или стикер поверх видео. x, y — центр, доли кадра; size — высота строки, доля высоты кадра. */
data class VideoOverlay(
    val id: Long,
    val text: String,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val size: Float = 0.05f,
    /** Цвет ARGB. */
    val color: Long = 0xFFFFFFFF,
    val style: OverlayStyle = OverlayStyle.Plain,
    val font: OverlayFont = OverlayFont.Inter,
    val sticker: Boolean = false,
)

enum class OverlayStyle(val title: String) {
    Plain("Обычный"),
    Box("С фоном"),
    Glow("Свечение"),
}

enum class OverlayFont(val title: String) {
    Inter("Inter"),
    Unbounded("Unbounded"),
}

/** Цвета текста — из палитры приложения и пара контрастных. */
val OVERLAY_COLORS = listOf(
    0xFFFFFFFF, 0xFFF3EEFF, 0xFFBC92CD, 0xFFFF9EDE, 0xFF8CC4FF, 0xFF307EC0, 0xFFFFE08A, 0xFF0B0820,
)

/** Стикеры: эмодзи и свои кристальные знаки. */
val STICKERS = listOf(
    "✦", "✧", "◇", "☾", "★", "✨", "💜", "🔮", "🌙", "⭐", "💫", "🌌",
    "🔥", "😂", "😍", "🥹", "😎", "🤍", "🎉", "🎵", "🎬", "📍", "👀", "🫶",
    "🌈", "⚡", "☕", "🌸", "🍀", "🐾", "🚀", "💎",
)

/** Фильтры — готовые цветовые настроения. */
enum class VideoFilter(val title: String) {
    None("Нет"),
    Crystal("Кристалл"),
    Lilac("Сирень"),
    Night("Ночь"),
    Noir("Ч/Б"),
    Sepia("Сепия"),
    Warm("Тёплый"),
    Cold("Холодный"),
    Vintage("Винтаж"),
    Neon("Неон"),
    Film("Плёнка"),
    Drama("Драма"),
    Pastel("Пастель"),
    ;

    /** Матрица 3×4 фильтра. */
    val matrix: FloatArray
        get() = with(ColorMatrix) {
            when (this@VideoFilter) {
                None -> IDENTITY
                Crystal -> offset(0.02f, 0.02f, 0.02f).then(scale(0.96f, 1f, 1.1f)).then(saturation(1.1f))
                Lilac -> offset(0.03f, 0f, 0.05f).then(scale(1.06f, 0.92f, 1.08f))
                Night -> scale(0.82f, 0.88f, 1.05f).then(saturation(0.85f)).then(contrast(1.1f))
                Noir -> saturation(0f).then(contrast(1.15f))
                Sepia -> SEPIA
                Warm -> scale(1.1f, 1.02f, 0.86f)
                Cold -> scale(0.88f, 1f, 1.14f)
                Vintage -> offset(0.06f, 0.06f, 0.06f).then(contrast(0.85f)).then(mix(SEPIA, 0.45f))
                Neon -> saturation(1.7f).then(contrast(1.1f))
                Film -> offset(0.03f, 0.04f, 0.02f).then(saturation(0.8f)).then(contrast(0.92f))
                Drama -> saturation(0.85f).then(contrast(1.35f))
                Pastel -> offset(0.08f, 0.08f, 0.08f).then(contrast(0.8f)).then(saturation(0.9f))
            }
        }
}

/**
 * Цветовые матрицы 3×4: строки — новые R, G, B; в каждой три множителя
 * и сдвиг. a.then(b) — сначала b, потом a. Цвет — 0…1.
 */
object ColorMatrix {
    val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f)

    val SEPIA = floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f,
        0.349f, 0.686f, 0.168f, 0f,
        0.272f, 0.534f, 0.131f, 0f,
    )

    fun scale(r: Float, g: Float, b: Float) = floatArrayOf(r, 0f, 0f, 0f, 0f, g, 0f, 0f, 0f, 0f, b, 0f)

    fun offset(r: Float, g: Float, b: Float) = floatArrayOf(1f, 0f, 0f, r, 0f, 1f, 0f, g, 0f, 0f, 1f, b)

    /** Насыщенность: 0 — серое, 1 — как есть, больше 1 — ярче. */
    fun saturation(s: Float): FloatArray {
        val lr = 0.2126f * (1 - s)
        val lg = 0.7152f * (1 - s)
        val lb = 0.0722f * (1 - s)
        return floatArrayOf(lr + s, lg, lb, 0f, lr, lg + s, lb, 0f, lr, lg, lb + s, 0f)
    }

    /** Контраст вокруг середины серого. */
    fun contrast(k: Float): FloatArray {
        val o = 0.5f * (1 - k)
        return floatArrayOf(k, 0f, 0f, o, 0f, k, 0f, o, 0f, 0f, k, o)
    }

    /** Смесь двух матриц: amount — доля второй. */
    fun mix(m: FloatArray, amount: Float): FloatArray =
        FloatArray(12) { i -> IDENTITY[i] * (1 - amount) + m[i] * amount }

    /** Коррекция из ползунков −1…1. */
    fun adjust(bright: Float, contr: Float, sat: Float, warm: Float): FloatArray =
        scale(1 + 0.12f * warm, 1f, 1 - 0.12f * warm)
            .then(saturation(1 + sat))
            .then(contrast(1 + 0.6f * contr))
            .then(offset(0.25f * bright, 0.25f * bright, 0.25f * bright))

    /** Сначала other, потом this. */
    fun FloatArray.then(other: FloatArray): FloatArray {
        val a = this
        val out = FloatArray(12)
        for (r in 0..2) {
            for (c in 0..2) {
                out[r * 4 + c] = a[r * 4] * other[c] + a[r * 4 + 1] * other[4 + c] + a[r * 4 + 2] * other[8 + c]
            }
            out[r * 4 + 3] = a[r * 4] * other[3] + a[r * 4 + 1] * other[7] + a[r * 4 + 2] * other[11] + a[r * 4 + 3]
        }
        return out
    }

    /** Применить к цвету (r, g, b) — для проверок и обложки. */
    fun apply(m: FloatArray, r: Float, g: Float, b: Float): Triple<Float, Float, Float> = Triple(
        (m[0] * r + m[1] * g + m[2] * b + m[3]).coerceIn(0f, 1f),
        (m[4] * r + m[5] * g + m[6] * b + m[7]).coerceIn(0f, 1f),
        (m[8] * r + m[9] * g + m[10] * b + m[11]).coerceIn(0f, 1f),
    )

    /** Та же матрица 4×4 по столбцам — для OpenGL (Media3 RgbMatrix). */
    fun toGl(m: FloatArray): FloatArray {
        val out = FloatArray(16)
        for (r in 0..2) {
            for (c in 0..3) out[c * 4 + r] = m[r * 4 + c]
        }
        out[15] = 1f
        return out
    }
}
