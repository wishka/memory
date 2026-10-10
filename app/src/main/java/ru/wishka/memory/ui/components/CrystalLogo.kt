package ru.wishka.memory.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Знак Memory в шапке ленты: вытянутый кристалл (две четырёхгранные
 * пирамиды, как в PRISM), который медленно вращается вокруг своей оси
 * по часовой стрелке, если смотреть сверху. Грани — перламутровое стекло,
 * как у слова MEMORY на заставке: свет падает слева сверху, повёрнутые к
 * нему грани светлеют, остальные уходят в тень; рёбра и контур — белые,
 * по стеклу пробегает блик. Тот же кристалл — на значке приложения
 * (icon/crystal_icon.py) и в CrystalLogo.swift на iPhone.
 */
object CrystalMark {
    /** Вершины и экватор — доли стороны: верх, низ, «экватор» и полуширина. */
    const val TOP_Y = 0.04f
    const val BOTTOM_Y = 0.96f
    const val MID_Y = 0.5f
    const val HALF_W = 0.2f

    /** Один оборот, мс. */
    const val TURN_MS = 9_000

    /** Перламутр — те же цвета, что у слова MEMORY. */
    val IRIDESCENT = listOf(
        Color(0xFFFF9EDE), Color(0xFFC6A2FF), Color(0xFF8CC4FF), Color(0xFFFFF4FF),
        Color(0xFFFFDB8F), Color(0xFFD08CFF), Color(0xFF7AA8FF),
    )

    /** Куда светит свет: слева сверху и спереди. */
    private val LIGHT = floatArrayOf(-0.45f, -0.55f, 0.70f).let { v ->
        val n = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]); floatArrayOf(v[0] / n, v[1] / n, v[2] / n)
    }

    /**
     * Нарисовать кристалл, повёрнутый на angle (радианы) вокруг вертикали.
     * По часовой сверху — угол убывает. sweep — положение блика 0…1 или вне.
     */
    fun DrawScope.drawCrystal(angle: Float, sweep: Float) {
        val s = size.width
        val cx = s * 0.5f
        val cy = s * MID_Y
        val half = s * (BOTTOM_Y - TOP_Y) / 2
        val r = s * HALF_W
        // Экватор: четыре вершины квадратного сечения; z — к зрителю
        val eq = List(4) { k ->
            val phi = angle + k * (PI / 2).toFloat()
            floatArrayOf(r * sin(phi), 0f, r * cos(phi))
        }
        val top = floatArrayOf(0f, -half, 0f)
        val bottom = floatArrayOf(0f, half, 0f)
        fun screen(p: FloatArray) = Offset(cx + p[0], cy + p[1])

        // Ореол за стеклом
        drawCircle(
            Brush.radialGradient(listOf(Color(0x66C49BFF), Color.Transparent), center = Offset(cx, cy), radius = s * 0.5f),
            radius = s * 0.5f, center = Offset(cx, cy),
        )

        val body = Brush.linearGradient(IRIDESCENT + IRIDESCENT.reversed(), start = Offset(s * 0.2f, 0f), end = Offset(s * 0.8f, s))
        val silhouette = Path()
        for (k in 0 until 4) {
            val a = eq[k]
            val b = eq[(k + 1) % 4]
            for (apex in listOf(top, bottom)) {
                // Нормаль грани наружу; видна — если смотрит на зрителя
                val u = floatArrayOf(a[0] - apex[0], a[1] - apex[1], a[2] - apex[2])
                val v = floatArrayOf(b[0] - apex[0], b[1] - apex[1], b[2] - apex[2])
                var n = floatArrayOf(u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
                val c = floatArrayOf((a[0] + b[0] + apex[0]) / 3, (a[1] + b[1] + apex[1]) / 3, (a[2] + b[2] + apex[2]) / 3)
                if (n[0] * c[0] + n[1] * c[1] + n[2] * c[2] < 0) n = floatArrayOf(-n[0], -n[1], -n[2])
                if (n[2] <= 0f) continue
                val len = sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2])
                val light = (n[0] * LIGHT[0] + n[1] * LIGHT[1] + n[2] * LIGHT[2]) / len
                val face = Path().apply {
                    val p0 = screen(apex); val p1 = screen(a); val p2 = screen(b)
                    moveTo(p0.x, p0.y); lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); close()
                }
                silhouette.addPath(face)
                drawPath(face, body)
                // Свет и тень грани
                if (light > 0.55f) drawPath(face, Color.White.copy(alpha = ((light - 0.55f) * 1.1f).coerceAtMost(0.5f)))
                else drawPath(face, Color(0xFF4A2F9A).copy(alpha = ((0.55f - light) * 0.75f).coerceAtMost(0.45f)))
                drawPath(face, Color.White.copy(alpha = 0.75f), style = Stroke(s * 0.012f + 0.6f, join = StrokeJoin.Round))
            }
        }
        // Блик по стеклу
        if (sweep in 0f..1.2f) {
            clipPath(silhouette) {
                val y = s * sweep
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                        start = Offset(0f, y - s * 0.18f), end = Offset(s * 0.3f, y + s * 0.18f),
                    ),
                )
            }
        }
        // Контур — по крайним вершинам экватора
        val left = eq.minByOrNull { it[0] }!!
        val right = eq.maxByOrNull { it[0] }!!
        val outline = Path().apply {
            val t = screen(top); val rr = screen(right); val b = screen(bottom); val l = screen(left)
            moveTo(t.x, t.y); lineTo(rr.x, rr.y); lineTo(b.x, b.y); lineTo(l.x, l.y); close()
        }
        drawPath(outline, Color.White, style = Stroke(s * 0.018f + 0.8f, join = StrokeJoin.Round))
        // Блики-звёздочки — мерцают, пока кристалл поворачивается
        val twinkle = 0.5f + 0.5f * sin(angle * 2)
        for ((sx, sy, rad) in listOf(Triple(0.44f, 0.30f, 0.05f), Triple(0.57f, 0.68f, 0.035f))) {
            val rr = s * rad * (0.6f + 0.4f * twinkle)
            val c = Offset(s * sx, s * sy)
            drawLine(Color.White, Offset(c.x - rr, c.y), Offset(c.x + rr, c.y), s * 0.012f + 0.5f, StrokeCap.Round)
            drawLine(Color.White, Offset(c.x, c.y - rr), Offset(c.x, c.y + rr), s * 0.012f + 0.5f, StrokeCap.Round)
        }
    }
}

/** Кристалл в шапке ленты: вращается по часовой стрелке (вид сверху), по стеклу пробегает блик. */
@Composable
fun CrystalLogo(modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val turn = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            turn.snapTo(0f)
            turn.animateTo(1f, tween(CrystalMark.TURN_MS, easing = LinearEasing))
        }
    }
    Canvas(modifier.size(size)) {
        val t = turn.value
        // По часовой сверху: угол убывает; блик — в начале каждого оборота
        val angle = -t * 2 * PI.toFloat() + 0.35f
        val sweep = t * 2.4f - 0.6f
        with(CrystalMark) { drawCrystal(angle, sweep) }
    }
}
