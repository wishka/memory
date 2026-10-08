package ru.wishka.memory.ui.splash

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Как слово ведёт себя на заставке.
 *
 * Dissolve — слово стоит целиком и, начиная с последней буквы,
 * рассыпается на атомы. Assemble — атомы слетаются из пространства и
 * собирают слово с первой буквы. AssembleFromCenter — сборка начинается
 * с середины слова и расходится в обе стороны; атомы левой половины
 * прилетают слева, правой — справа.
 */
enum class SplashMode { Dissolve, Assemble, AssembleFromCenter }

/**
 * Физика заставки: слово из точек-«атомов».
 *
 * Чистый Kotlin без Android: положение каждого атома считается формулой
 * от времени, а не шагами симуляции. Поэтому кадр не зависит от частоты
 * экрана (60 или 120 Гц — картинка одна и та же), ничего не копится между
 * кадрами, а правила можно проверить обычным юнит-тестом.
 *
 * Сборка — это распад, пущенный назад во времени: атом летит по той же
 * траектории, только к своей точке в букве, а не от неё. Поэтому оба
 * режима выглядят как одно явление.
 *
 * xs, ys — точки букв в пикселях экрана. Время — в миллисекундах от
 * начала заставки.
 */
class AtomField(
    private val xs: FloatArray,
    private val ys: FloatArray,
    val mode: SplashMode = SplashMode.Dissolve,
    seed: Int = 7,
    val timing: Timing = Timing(),
) {
    /** Тайминги в миллисекундах. */
    data class Timing(
        /** Dissolve: слово проявляется. */
        val appearMs: Long = 500,
        /** Dissolve: слово стоит целиком перед распадом. Assemble: собранное слово стоит до перехода. */
        val holdMs: Long = 250,
        /** Волна проходит по слову: распад — с конца, сборка — с начала. */
        val sweepMs: Long = 800,
        /** Разброс момента соседних атомов — край волны «рваный». */
        val jitterMs: Long = 160,
        /** Полёт атома. */
        val lifeMs: Long = 700,
        /** Пауза в конце перед переходом в приложение. */
        val tailMs: Long = 120,
    )

    val size: Int = xs.size

    private val minX = xs.minOrNull() ?: 0f
    private val maxX = xs.maxOrNull() ?: 1f
    private val span = max(1f, maxX - minX)

    /** Dissolve: момент отрыва атома. Assemble: момент, когда он встал на место. */
    private val pivot = LongArray(size)
    private val vx = FloatArray(size)
    private val vy = FloatArray(size)
    private val wobbleAmp = FloatArray(size)
    private val wobblePhase = FloatArray(size)
    private val radius = FloatArray(size)
    /** 0..1 — оттенок атома в полёте: сирень → синий. */
    val hue = FloatArray(size)

    init {
        val rnd = Random(seed)
        val t = timing
        for (i in 0 until size) {
            // 0 — левый край слова (M), 1 — правый (Y)
            val xn = (xs[i] - minX) / span
            val jitter = (rnd.nextFloat() * t.jitterMs).toLong()
            pivot[i] = when (mode) {
                // Распад с конца: правые атомы отрываются первыми
                SplashMode.Dissolve -> t.appearMs + t.holdMs + ((1f - xn) * t.sweepMs).toLong() + jitter
                // Сборка с начала: левые атомы встают на место первыми;
                // первые вылетают сразу, на полёт уходит lifeMs
                SplashMode.Assemble -> t.lifeMs + (xn * t.sweepMs).toLong() + jitter
                // От середины к краям: чем дальше от центра, тем позже
                SplashMode.AssembleFromCenter ->
                    t.lifeMs + (kotlin.math.abs(xn - 0.5f) * 2f * t.sweepMs).toLong() + jitter
            }
            val angle = when (mode) {
                // Из центра в разные стороны: левая половина — влево, правая — вправо,
                // с разбросом вверх и вниз
                SplashMode.AssembleFromCenter -> {
                    val spread = ((rnd.nextFloat() - 0.5) * 1.6).toFloat()
                    if (xn < 0.5f) (PI.toFloat() + spread) else spread
                }
                // Разлёт вправо-вверх, как дым на ветру, с разбросом направлений
                else -> (-PI / 2 + 0.95 + (rnd.nextFloat() - 0.5) * 1.9).toFloat()
            }
            val speed = 0.1f + rnd.nextFloat() * 0.25f   // пикс./мс
            vx[i] = cos(angle) * speed
            vy[i] = sin(angle) * speed
            wobbleAmp[i] = 4f + rnd.nextFloat() * 14f
            wobblePhase[i] = rnd.nextFloat() * (2 * PI).toFloat()
            radius[i] = 0.75f + rnd.nextFloat() * 0.5f
            hue[i] = rnd.nextFloat()
        }
    }

    private val lastPivot = pivot.maxOrNull()

    /** Когда заставка полностью закончилась. */
    val totalMs: Long = when (mode) {
        SplashMode.Dissolve -> (lastPivot ?: (timing.appearMs + timing.holdMs)) + timing.lifeMs + timing.tailMs
        // После сборки слово «кристаллизуется» и по нему пробегает блик
        SplashMode.Assemble, SplashMode.AssembleFromCenter ->
            (lastPivot ?: timing.lifeMs) + CRYSTALLIZE_MS + SHIMMER_MS + timing.holdMs + timing.tailMs
    }

    /** Когда слово целиком собрано (Assemble) или когда начинается распад (Dissolve). */
    val wordCompleteMs: Long = when (mode) {
        SplashMode.Dissolve -> timing.appearMs
        SplashMode.Assemble, SplashMode.AssembleFromCenter -> lastPivot ?: 0L
    }

    /** 0..1 — насколько слово превратилось из атомов в кристалл. */
    fun crystal(t: Long): Float =
        if (mode == SplashMode.Dissolve) 0f
        else ((t - wordCompleteMs).toFloat() / CRYSTALLIZE_MS).coerceIn(0f, 1f)

    /** Положение блика по слову: null — блика нет, 0..1 — слева направо. */
    fun shimmer(t: Long): Float? {
        if (mode == SplashMode.Dissolve) return null
        val p = (t - wordCompleteMs - CRYSTALLIZE_MS).toFloat() / SHIMMER_MS
        return p.takeIf { it in 0f..1f }
    }

    /** Dissolve: момент отрыва атома i. Assemble: момент, когда он встал на место. */
    fun pivotAt(i: Int): Long = pivot[i]

    /** Время полёта атома: 0 — атом в букве, больше нуля — в пути. */
    private fun flight(i: Int, t: Long): Long = when (mode) {
        SplashMode.Dissolve -> max(0L, t - pivot[i])
        else -> max(0L, pivot[i] - t)
    }

    /** Атом ещё (или уже) виден. */
    fun alive(i: Int, t: Long): Boolean = flight(i, t) < timing.lifeMs

    /** Доля пути: 0 — в букве, 1 — растворён в пространстве. */
    fun decay(i: Int, t: Long): Float =
        (flight(i, t).toFloat() / timing.lifeMs).coerceIn(0f, 1f)

    fun x(i: Int, t: Long): Float {
        val a = flight(i, t).toFloat()
        val wobble = if (a > 0) sin(a * 0.012f + wobblePhase[i]) * wobbleAmp[i] * (a / timing.lifeMs) else 0f
        return xs[i] + vx[i] * a + wobble
    }

    fun y(i: Int, t: Long): Float {
        val a = flight(i, t).toFloat()
        // Лёгкое ускорение вверх: атомы «всплывают» (при сборке — опускаются на место)
        return ys[i] + vy[i] * a - 0.00009f * a * a
    }

    /** Прозрачность: атом проявляется по мере приближения к букве и тает при удалении. */
    fun alpha(i: Int, t: Long): Float {
        val fade = 1f - decay(i, t).pow(1.4f)
        return when (mode) {
            SplashMode.Dissolve -> smooth(min(1f, t.toFloat() / timing.appearMs)) * fade
            // Когда слово стало кристаллом, атомы растворяются в нём
            SplashMode.Assemble, SplashMode.AssembleFromCenter -> fade * (1f - crystal(t))
        }
    }

    /** Множитель радиуса: у буквы атом вспыхивает, вдали — тает. */
    fun radiusScale(i: Int, t: Long): Float {
        val d = decay(i, t)
        if (d <= 0f) return radius[i]
        val flash = if (d < 0.15f) 1f + d / 0.15f * 0.6f else 1.6f - (d - 0.15f) / 0.85f * 1.2f
        return radius[i] * max(0.2f, flash)
    }

    private fun smooth(v: Float) = v * v * (3 - 2 * v)

    companion object {
        const val CRYSTALLIZE_MS = 350L
        const val SHIMMER_MS = 550L
    }
}
