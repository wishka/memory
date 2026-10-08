package ru.wishka.memory.ui.splash

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Физика заставки: слово, собранное из точек-«атомов», которое
 * рассыпается начиная с последней буквы.
 *
 * Чистый Kotlin без Android: положение каждого атома считается формулой
 * от времени, а не шагами симуляции. Поэтому кадр не зависит от частоты
 * экрана (60 или 120 Гц — картинка одна и та же), ничего не копится между
 * кадрами, а правила можно проверить обычным юнит-тестом.
 *
 * xs, ys — точки букв в пикселях экрана. Время — в миллисекундах от
 * начала заставки.
 */
class AtomField(
    private val xs: FloatArray,
    private val ys: FloatArray,
    seed: Int = 7,
    val timing: Timing = Timing(),
) {
    /** Тайминги в миллисекундах. */
    data class Timing(
        /** Слово проявляется. */
        val appearMs: Long = 650,
        /** Слово стоит целиком перед распадом. */
        val holdMs: Long = 350,
        /** Волна распада проходит от последней буквы к первой. */
        val sweepMs: Long = 1150,
        /** Разброс момента отрыва соседних атомов — край волны «рваный». */
        val jitterMs: Long = 220,
        /** Жизнь атома после отрыва. */
        val lifeMs: Long = 950,
        /** Пустой экран после последнего атома перед переходом. */
        val tailMs: Long = 120,
    )

    val size: Int = xs.size

    private val minX = xs.minOrNull() ?: 0f
    private val maxX = xs.maxOrNull() ?: 1f
    private val span = max(1f, maxX - minX)

    private val release = LongArray(size)
    private val vx = FloatArray(size)
    private val vy = FloatArray(size)
    private val wobbleAmp = FloatArray(size)
    private val wobblePhase = FloatArray(size)
    private val radius = FloatArray(size)
    /** 0..1 — оттенок атома после отрыва: сирень → синий. */
    val hue = FloatArray(size)

    init {
        val rnd = Random(seed)
        val t = timing
        for (i in 0 until size) {
            // 0 — левый край слова (M), 1 — правый (Y)
            val xn = (xs[i] - minX) / span
            // Волна идёт с конца: правые атомы отрываются первыми
            release[i] = t.appearMs + t.holdMs +
                ((1f - xn) * t.sweepMs).toLong() + (rnd.nextFloat() * t.jitterMs).toLong()
            // Разлёт вправо-вверх, как дым на ветру, с разбросом направлений
            val angle = (-PI / 2 + 0.95 + (rnd.nextFloat() - 0.5) * 1.9).toFloat()
            val speed = 0.08f + rnd.nextFloat() * 0.2f   // пикс./мс
            vx[i] = cos(angle) * speed
            vy[i] = sin(angle) * speed
            wobbleAmp[i] = 4f + rnd.nextFloat() * 14f
            wobblePhase[i] = rnd.nextFloat() * (2 * PI).toFloat()
            radius[i] = 0.75f + rnd.nextFloat() * 0.5f
            hue[i] = rnd.nextFloat()
        }
    }

    /** Когда заставка полностью закончилась. */
    val totalMs: Long =
        (release.maxOrNull() ?: (timing.appearMs + timing.holdMs)) + timing.lifeMs + timing.tailMs

    /** Момент отрыва атома i. */
    fun releaseAt(i: Int): Long = release[i]

    /** Возраст атома после отрыва; отрицательный — ещё в букве. */
    fun age(i: Int, t: Long): Long = t - release[i]

    /** Атом ещё виден. */
    fun alive(i: Int, t: Long): Boolean = age(i, t) < timing.lifeMs

    /** Доля распада для атома: 0 — в букве, 1 — растворился. */
    fun decay(i: Int, t: Long): Float =
        (age(i, t).toFloat() / timing.lifeMs).coerceIn(0f, 1f)

    fun x(i: Int, t: Long): Float {
        val a = max(0L, age(i, t)).toFloat()
        val wobble = if (a > 0) sin(a * 0.012f + wobblePhase[i]) * wobbleAmp[i] * (a / timing.lifeMs) else 0f
        return xs[i] + vx[i] * a + wobble
    }

    fun y(i: Int, t: Long): Float {
        val a = max(0L, age(i, t)).toFloat()
        // Лёгкое ускорение вверх: атомы «всплывают»
        return ys[i] + vy[i] * a - 0.00009f * a * a
    }

    /** Прозрачность: проявление слова, затем угасание оторвавшегося атома. */
    fun alpha(i: Int, t: Long): Float {
        val appear = smooth(min(1f, t.toFloat() / timing.appearMs))
        val d = decay(i, t)
        return appear * (1f - d.pow(1.4f))
    }

    /** Множитель радиуса: при отрыве атом вспыхивает и затем тает. */
    fun radiusScale(i: Int, t: Long): Float {
        val d = decay(i, t)
        if (d <= 0f) return radius[i]
        val flash = if (d < 0.15f) 1f + d / 0.15f * 0.6f else 1.6f - (d - 0.15f) / 0.85f * 1.2f
        return radius[i] * max(0.2f, flash)
    }

    private fun smooth(v: Float) = v * v * (3 - 2 * v)
}
