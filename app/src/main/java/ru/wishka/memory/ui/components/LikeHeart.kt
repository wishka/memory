package ru.wishka.memory.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Сердце «нравится» с анимацией: при нажатии сначала вспыхивает искра —
 * четырёхлучевая звезда и разлетающиеся лучи, — а из неё проявляется
 * кристальное сердце с лёгким перебросом. Снятие лайка — без вспышки.
 * Анимация только на нажатие: при первом показе уже отмеченное сердце
 * просто видно.
 */
@Composable
fun LikeHeart(liked: Boolean, color: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    // 0..1 — ход анимации; 1 — покой
    val progress = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(liked) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        if (liked) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(LIKE_MS, easing = LinearEasing))
        } else {
            progress.snapTo(1f)
        }
    }
    val p = progress.value

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        // Искра рисуется за пределами значка — Canvas не обрезает
        if (liked && p < 1f) {
            Canvas(Modifier.size(size)) { drawSpark(p, this.size.width) }
        }
        Glyph(
            if (liked) LineIcon.HeartFilled else LineIcon.Heart,
            color,
            size = size,
            modifier = Modifier.graphicsLayer {
                val k = if (liked) heartScale(p) else 1f
                scaleX = k
                scaleY = k
                alpha = if (liked) (k * 1.4f).coerceAtMost(1f) else 1f
            },
        )
    }
}

/** Длительность всей анимации. */
const val LIKE_MS = 700

/**
 * Масштаб сердца: первые 25% — пусто (горит искра), затем вырастает до
 * 1.28 и опускается к 1.
 */
fun heartScale(p: Float): Float = when {
    p < 0.25f -> 0f
    p < 0.6f -> {
        val t = (p - 0.25f) / 0.35f
        1.28f * easeOut(t)
    }
    else -> {
        val t = (p - 0.6f) / 0.4f
        1.28f - 0.28f * easeOut(t)
    }
}

private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)

private val SparkWhite = Color(0xFFFFF4FF)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpark(p: Float, s: Float) {
    val c = Offset(s / 2, s / 2)
    // Вспышка: звезда растёт и гаснет за первые 45%
    if (p < 0.45f) {
        val t = p / 0.45f
        val r = s * (0.25f + 0.55f * easeOut(t))
        val a = 1f - t
        val star = Path().apply {
            moveTo(c.x, c.y - r)
            quadraticTo(c.x, c.y, c.x + r, c.y)
            quadraticTo(c.x, c.y, c.x, c.y + r)
            quadraticTo(c.x, c.y, c.x - r, c.y)
            quadraticTo(c.x, c.y, c.x, c.y - r)
            close()
        }
        drawPath(star, SparkWhite.copy(alpha = a))
        drawCircle(CrystalColors[1].copy(alpha = 0.35f * a), r * 0.9f, c)
    }
    // Лучи разлетаются: восемь штрихов в цветах кристалла
    if (p in 0.08f..0.85f) {
        val t = (p - 0.08f) / 0.77f
        val inner = s * (0.35f + 0.55f * easeOut(t))
        val outer = inner + s * 0.22f * (1f - t)
        for (k in 0 until 8) {
            val angle = k * PI.toFloat() / 4f + PI.toFloat() / 8f
            val dx = cos(angle)
            val dy = sin(angle)
            drawLine(
                CrystalColors[k % CrystalColors.size].copy(alpha = 1f - t),
                Offset(c.x + dx * inner, c.y + dy * inner),
                Offset(c.x + dx * outer, c.y + dy * outer),
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}
