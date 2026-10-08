package ru.wishka.memory.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Свои тонкие линейные значки. Стандартные значки Material толстые и
 * залитые — рядом с тонким Unbounded они выглядят чужими. Здесь всё
 * рисуется линией в 1.5dp, с кристаллом-ромбом как общим мотивом.
 */
enum class LineIcon { Feed, Chats, Profile, Heart, HeartFilled, Comment, Send, Back, Edit, Plus, Bell, Info }

@Composable
fun Glyph(kind: LineIcon, color: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            LineIcon.Feed -> {
                // Ромб-кристалл и три строки под ним
                diamond(Offset(s * 0.5f, s * 0.22f), s * 0.13f, color, stroke)
                for ((i, w) in listOf(0.62f, 0.44f, 0.62f).withIndex()) {
                    val y = s * (0.5f + i * 0.16f)
                    drawLine(color, Offset(s * (0.5f - w / 2), y), Offset(s * (0.5f + w / 2), y), stroke.width, StrokeCap.Round)
                }
            }
            LineIcon.Chats, LineIcon.Comment -> {
                val p = Path().apply {
                    moveTo(s * 0.2f, s * 0.24f)
                    lineTo(s * 0.8f, s * 0.24f)
                    quadraticTo(s * 0.86f, s * 0.24f, s * 0.86f, s * 0.3f)
                    lineTo(s * 0.86f, s * 0.62f)
                    quadraticTo(s * 0.86f, s * 0.68f, s * 0.8f, s * 0.68f)
                    lineTo(s * 0.42f, s * 0.68f)
                    lineTo(s * 0.26f, s * 0.82f)
                    lineTo(s * 0.28f, s * 0.68f)
                    lineTo(s * 0.2f, s * 0.68f)
                    quadraticTo(s * 0.14f, s * 0.68f, s * 0.14f, s * 0.62f)
                    lineTo(s * 0.14f, s * 0.3f)
                    quadraticTo(s * 0.14f, s * 0.24f, s * 0.2f, s * 0.24f)
                    close()
                }
                drawPath(p, color, style = stroke)
                if (kind == LineIcon.Chats) {
                    for (i in 0..2) drawCircle(color, s * 0.035f, Offset(s * (0.36f + i * 0.14f), s * 0.46f))
                }
            }
            LineIcon.Profile -> {
                drawCircle(color, s * 0.15f, Offset(s * 0.5f, s * 0.36f), style = stroke)
                val p = Path().apply {
                    moveTo(s * 0.2f, s * 0.84f)
                    cubicTo(s * 0.24f, s * 0.6f, s * 0.76f, s * 0.6f, s * 0.8f, s * 0.84f)
                }
                drawPath(p, color, style = stroke)
            }
            LineIcon.Heart, LineIcon.HeartFilled -> {
                val p = Path().apply {
                    moveTo(s * 0.5f, s * 0.82f)
                    cubicTo(s * 0.1f, s * 0.56f, s * 0.08f, s * 0.24f, s * 0.32f, s * 0.22f)
                    cubicTo(s * 0.42f, s * 0.21f, s * 0.48f, s * 0.28f, s * 0.5f, s * 0.34f)
                    cubicTo(s * 0.52f, s * 0.28f, s * 0.58f, s * 0.21f, s * 0.68f, s * 0.22f)
                    cubicTo(s * 0.92f, s * 0.24f, s * 0.9f, s * 0.56f, s * 0.5f, s * 0.82f)
                    close()
                }
                if (kind == LineIcon.HeartFilled) drawPath(p, color, style = Fill)
                drawPath(p, color, style = stroke)
            }
            LineIcon.Send -> {
                val p = Path().apply {
                    moveTo(s * 0.16f, s * 0.2f)
                    lineTo(s * 0.86f, s * 0.5f)
                    lineTo(s * 0.16f, s * 0.8f)
                    lineTo(s * 0.28f, s * 0.5f)
                    close()
                }
                drawPath(p, color, style = stroke)
                drawLine(color, Offset(s * 0.28f, s * 0.5f), Offset(s * 0.56f, s * 0.5f), stroke.width, StrokeCap.Round)
            }
            LineIcon.Back -> {
                val p = Path().apply {
                    moveTo(s * 0.6f, s * 0.2f)
                    lineTo(s * 0.3f, s * 0.5f)
                    lineTo(s * 0.6f, s * 0.8f)
                }
                drawPath(p, color, style = stroke)
            }
            LineIcon.Edit -> {
                val p = Path().apply {
                    moveTo(s * 0.2f, s * 0.8f)
                    lineTo(s * 0.24f, s * 0.62f)
                    lineTo(s * 0.64f, s * 0.22f)
                    lineTo(s * 0.78f, s * 0.36f)
                    lineTo(s * 0.38f, s * 0.76f)
                    close()
                }
                drawPath(p, color, style = stroke)
            }
            LineIcon.Plus -> {
                drawLine(color, Offset(s * 0.5f, s * 0.22f), Offset(s * 0.5f, s * 0.78f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(s * 0.22f, s * 0.5f), Offset(s * 0.78f, s * 0.5f), stroke.width, StrokeCap.Round)
            }
            LineIcon.Bell -> {
                val p = Path().apply {
                    moveTo(s * 0.24f, s * 0.7f)
                    lineTo(s * 0.76f, s * 0.7f)
                    lineTo(s * 0.7f, s * 0.6f)
                    lineTo(s * 0.7f, s * 0.42f)
                    cubicTo(s * 0.7f, s * 0.18f, s * 0.3f, s * 0.18f, s * 0.3f, s * 0.42f)
                    lineTo(s * 0.3f, s * 0.6f)
                    close()
                }
                drawPath(p, color, style = stroke)
                drawLine(color, Offset(s * 0.44f, s * 0.8f), Offset(s * 0.56f, s * 0.8f), stroke.width, StrokeCap.Round)
            }
            LineIcon.Info -> {
                diamond(Offset(s * 0.5f, s * 0.5f), s * 0.34f, color, stroke)
                drawLine(color, Offset(s * 0.5f, s * 0.46f), Offset(s * 0.5f, s * 0.64f), stroke.width, StrokeCap.Round)
                drawCircle(color, s * 0.035f, Offset(s * 0.5f, s * 0.36f))
            }
        }
    }
}

private fun DrawScope.diamond(c: Offset, r: Float, color: Color, stroke: Stroke) {
    val p = Path().apply {
        moveTo(c.x, c.y - r * 1.25f)
        lineTo(c.x + r, c.y)
        lineTo(c.x, c.y + r * 1.25f)
        lineTo(c.x - r, c.y)
        close()
    }
    drawPath(p, color, style = stroke)
}

@Suppress("unused")
private fun DrawScope.roundRect(color: Color, topLeft: Offset, size: Size, r: Float, stroke: Stroke) =
    drawRoundRect(color, topLeft, size, CornerRadius(r, r), style = stroke)
