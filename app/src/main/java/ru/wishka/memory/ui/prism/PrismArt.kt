package ru.wishka.memory.ui.prism

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import ru.wishka.memory.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * «Видео» PRISM, пока нет настоящих роликов: живая туманность в цветах
 * палитры, вращающийся кристалл и всплывающие искры. Всё — функция от
 * времени ролика (timeMs), поэтому пауза и перемотка работают честно.
 */
@Composable
fun PrismArt(seed: Int, timeMs: Long, modifier: Modifier = Modifier) {
    val spec = remember(seed) { PrismSpec(seed) }
    Canvas(modifier = modifier) {
        val t = timeMs / 1000f
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF05071A), Palette.Night, Color(0xFF1A1F5C))))
        // Облака плывут по своим орбитам
        spec.blobs.forEach { b ->
            val cx = (b.x + 0.12f * sin(t * b.speed + b.phase)) * w
            val cy = (b.y + 0.08f * cos(t * b.speed * 0.8f + b.phase)) * h
            drawRect(
                Brush.radialGradient(
                    listOf(b.color.copy(alpha = b.alpha), b.color.copy(alpha = b.alpha * 0.3f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = b.r * w,
                )
            )
        }
        // Искры всплывают снизу вверх и мерцают
        spec.sparks.forEach { s ->
            val y = ((s.y - t * s.speed) % 1f + 1f) % 1f
            val x = s.x + 0.02f * sin(t * 2f + s.phase)
            val a = 0.3f + 0.7f * ((sin(t * 3f + s.phase) + 1f) / 2f)
            drawCircle(Palette.Ink.copy(alpha = a * 0.8f), radius = s.size, center = Offset(x * w, y * h))
        }
        // Кристалл: ромб, который медленно поворачивается вокруг вертикали
        val cx = w / 2
        val cy = h * 0.42f
        val ch = h * 0.18f
        val turn = cos(t * 0.9f + spec.phase)
        val cw = ch * 0.55f * (0.35f + 0.65f * kotlin.math.abs(turn))
        val crystal = Path().apply {
            moveTo(cx, cy - ch); lineTo(cx + cw, cy); lineTo(cx, cy + ch); lineTo(cx - cw, cy); close()
            moveTo(cx - cw, cy); lineTo(cx + cw, cy)
            moveTo(cx, cy - ch); lineTo(cx + cw * turn * 0.4f, cy); lineTo(cx, cy + ch)
        }
        drawCircle(
            Brush.radialGradient(listOf(Palette.Lilac.copy(alpha = 0.35f), Color.Transparent), center = Offset(cx, cy), radius = ch * 1.6f),
            radius = ch * 1.6f, center = Offset(cx, cy),
        )
        drawPath(crystal, Brush.linearGradient(listOf(Color(0xFFFF9EDE), Color(0xFFC6A2FF), Color(0xFF8CC4FF))), style = Stroke(width = 3f))
        drawCircle(Palette.Lilac.copy(alpha = 0.3f), ch * 1.25f, Offset(cx, cy), style = Stroke(width = 1.5f))
    }
}

private class PrismSpec(seed: Int) {
    class Blob(val x: Float, val y: Float, val r: Float, val color: Color, val alpha: Float, val speed: Float, val phase: Float)
    class Spark(val x: Float, val y: Float, val size: Float, val speed: Float, val phase: Float)

    val phase: Float
    val blobs: List<Blob>
    val sparks: List<Spark>

    init {
        val rnd = Random(seed)
        val colors = listOf(Palette.Orchid, Palette.Violet, Palette.Lilac, Palette.Ocean, Palette.Sky, Palette.Plum)
        phase = rnd.nextFloat() * (2 * PI).toFloat()
        blobs = List(6) {
            Blob(
                x = rnd.nextFloat(), y = 0.2f + rnd.nextFloat() * 0.8f, r = 0.4f + rnd.nextFloat() * 0.5f,
                color = colors[rnd.nextInt(colors.size)], alpha = 0.35f + rnd.nextFloat() * 0.35f,
                speed = 0.2f + rnd.nextFloat() * 0.5f, phase = rnd.nextFloat() * 6.28f,
            )
        }
        sparks = List(70) {
            Spark(
                x = rnd.nextFloat(), y = rnd.nextFloat(), size = 1.2f + rnd.nextFloat() * 2.8f,
                speed = 0.03f + rnd.nextFloat() * 0.08f, phase = rnd.nextFloat() * 6.28f,
            )
        }
    }
}
