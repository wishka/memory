package ru.wishka.memory.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
enum class LineIcon { Reply, Forward, ChevronDown, Feed, Stories, Prism, Share, Bookmark, BookmarkFilled, Attach, Camera, Video, Image, Document, Trash, Play, Close, Groups, Pin, Gift, Link, Music, Calendar, Exit, Chats, Profile, Heart, HeartFilled, Comment, Send, Back, Edit, Plus, Bell, Info }

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
            LineIcon.Stories -> {
                // Кольцо-история из дуг с разрывами и кристалл внутри
                for (k in 0 until 4) {
                    drawArc(
                        color, startAngle = -80f + k * 90f, sweepAngle = 70f, useCenter = false,
                        topLeft = Offset(s * 0.14f, s * 0.14f), size = Size(s * 0.72f, s * 0.72f), style = stroke,
                    )
                }
                diamond(Offset(s * 0.5f, s * 0.5f), s * 0.12f, color, stroke)
            }
            LineIcon.Prism -> {
                // Призма: треугольник, входящий луч и веер лучей-спектра
                val tri = Path().apply {
                    moveTo(s * 0.42f, s * 0.18f); lineTo(s * 0.70f, s * 0.80f); lineTo(s * 0.14f, s * 0.80f); close()
                }
                drawPath(tri, color, style = stroke)
                drawLine(color, Offset(s * 0.04f, s * 0.58f), Offset(s * 0.30f, s * 0.52f), stroke.width, StrokeCap.Round)
                for (k in 0..2) {
                    drawLine(
                        color, Offset(s * 0.56f, s * 0.50f), Offset(s * 0.94f, s * (0.40f + k * 0.12f)),
                        stroke.width * 0.8f, StrokeCap.Round,
                    )
                }
            }
            LineIcon.Reply -> {
                // Изогнутая стрелка влево
                drawPath(Path().apply {
                    moveTo(s * 0.40f, s * 0.24f); lineTo(s * 0.16f, s * 0.46f); lineTo(s * 0.40f, s * 0.68f)
                }, color, style = stroke)
                drawPath(Path().apply {
                    moveTo(s * 0.16f, s * 0.46f); lineTo(s * 0.58f, s * 0.46f)
                    cubicTo(s * 0.80f, s * 0.46f, s * 0.86f, s * 0.62f, s * 0.84f, s * 0.80f)
                }, color, style = stroke)
            }
            LineIcon.Forward -> {
                // Изогнутая стрелка вправо
                drawPath(Path().apply {
                    moveTo(s * 0.60f, s * 0.24f); lineTo(s * 0.84f, s * 0.46f); lineTo(s * 0.60f, s * 0.68f)
                }, color, style = stroke)
                drawPath(Path().apply {
                    moveTo(s * 0.84f, s * 0.46f); lineTo(s * 0.42f, s * 0.46f)
                    cubicTo(s * 0.20f, s * 0.46f, s * 0.14f, s * 0.62f, s * 0.16f, s * 0.80f)
                }, color, style = stroke)
            }
            LineIcon.ChevronDown -> {
                drawPath(Path().apply {
                    moveTo(s * 0.26f, s * 0.38f); lineTo(s * 0.5f, s * 0.62f); lineTo(s * 0.74f, s * 0.38f)
                }, color, style = stroke)
            }
            LineIcon.Play -> {
                val play = Path().apply {
                    moveTo(s * 0.34f, s * 0.22f); lineTo(s * 0.80f, s * 0.5f); lineTo(s * 0.34f, s * 0.78f); close()
                }
                drawPath(play, color, style = Fill)
            }
            LineIcon.Share -> {
                // Стрелка из коробки
                drawLine(color, Offset(s * 0.5f, s * 0.14f), Offset(s * 0.5f, s * 0.6f), stroke.width, StrokeCap.Round)
                drawPath(Path().apply {
                    moveTo(s * 0.34f, s * 0.3f); lineTo(s * 0.5f, s * 0.14f); lineTo(s * 0.66f, s * 0.3f)
                }, color, style = stroke)
                drawPath(Path().apply {
                    moveTo(s * 0.36f, s * 0.44f); lineTo(s * 0.22f, s * 0.44f); lineTo(s * 0.22f, s * 0.86f)
                    lineTo(s * 0.78f, s * 0.86f); lineTo(s * 0.78f, s * 0.44f); lineTo(s * 0.64f, s * 0.44f)
                }, color, style = stroke)
            }
            LineIcon.Bookmark, LineIcon.BookmarkFilled -> {
                val p = Path().apply {
                    moveTo(s * 0.26f, s * 0.14f); lineTo(s * 0.74f, s * 0.14f); lineTo(s * 0.74f, s * 0.86f)
                    lineTo(s * 0.5f, s * 0.68f); lineTo(s * 0.26f, s * 0.86f); close()
                }
                if (kind == LineIcon.BookmarkFilled) drawPath(p, crystalBrush(s), style = Fill)
                drawPath(p, color, style = stroke)
            }
            LineIcon.Attach -> {
                // Скрепка
                drawPath(Path().apply {
                    moveTo(s * 0.62f, s * 0.3f); lineTo(s * 0.36f, s * 0.56f)
                    cubicTo(s * 0.28f, s * 0.64f, s * 0.38f, s * 0.76f, s * 0.46f, s * 0.68f)
                    lineTo(s * 0.74f, s * 0.40f)
                    cubicTo(s * 0.88f, s * 0.26f, s * 0.70f, s * 0.08f, s * 0.56f, s * 0.22f)
                    lineTo(s * 0.26f, s * 0.52f)
                    cubicTo(s * 0.06f, s * 0.72f, s * 0.32f, s * 0.98f, s * 0.52f, s * 0.78f)
                    lineTo(s * 0.80f, s * 0.50f)
                }, color, style = stroke)
            }
            LineIcon.Camera -> {
                drawRoundRect(color, topLeft = Offset(s * 0.12f, s * 0.3f), size = Size(s * 0.76f, s * 0.52f),
                    cornerRadius = CornerRadius(s * 0.1f, s * 0.1f), style = stroke)
                drawPath(Path().apply {
                    moveTo(s * 0.34f, s * 0.3f); lineTo(s * 0.4f, s * 0.2f); lineTo(s * 0.6f, s * 0.2f); lineTo(s * 0.66f, s * 0.3f)
                }, color, style = stroke)
                drawCircle(color, s * 0.13f, Offset(s * 0.5f, s * 0.56f), style = stroke)
            }
            LineIcon.Video -> {
                drawRoundRect(color, topLeft = Offset(s * 0.1f, s * 0.28f), size = Size(s * 0.56f, s * 0.44f),
                    cornerRadius = CornerRadius(s * 0.08f, s * 0.08f), style = stroke)
                drawPath(Path().apply {
                    moveTo(s * 0.66f, s * 0.44f); lineTo(s * 0.9f, s * 0.32f); lineTo(s * 0.9f, s * 0.68f); lineTo(s * 0.66f, s * 0.56f)
                }, color, style = stroke)
            }
            LineIcon.Image -> {
                drawRoundRect(color, topLeft = Offset(s * 0.14f, s * 0.18f), size = Size(s * 0.72f, s * 0.64f),
                    cornerRadius = CornerRadius(s * 0.08f, s * 0.08f), style = stroke)
                drawPath(Path().apply {
                    moveTo(s * 0.18f, s * 0.74f); lineTo(s * 0.4f, s * 0.5f); lineTo(s * 0.56f, s * 0.64f)
                    lineTo(s * 0.66f, s * 0.56f); lineTo(s * 0.84f, s * 0.74f)
                }, color, style = stroke)
                drawCircle(color, s * 0.06f, Offset(s * 0.64f, s * 0.36f), style = stroke)
            }
            LineIcon.Document -> {
                drawPath(Path().apply {
                    moveTo(s * 0.24f, s * 0.12f); lineTo(s * 0.58f, s * 0.12f); lineTo(s * 0.78f, s * 0.32f)
                    lineTo(s * 0.78f, s * 0.88f); lineTo(s * 0.24f, s * 0.88f); close()
                    moveTo(s * 0.58f, s * 0.12f); lineTo(s * 0.58f, s * 0.32f); lineTo(s * 0.78f, s * 0.32f)
                }, color, style = stroke)
                drawLine(color, Offset(s * 0.36f, s * 0.52f), Offset(s * 0.66f, s * 0.52f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(s * 0.36f, s * 0.68f), Offset(s * 0.6f, s * 0.68f), stroke.width, StrokeCap.Round)
            }
            LineIcon.Trash -> {
                drawLine(color, Offset(s * 0.16f, s * 0.26f), Offset(s * 0.84f, s * 0.26f), stroke.width, StrokeCap.Round)
                drawPath(Path().apply {
                    moveTo(s * 0.38f, s * 0.26f); lineTo(s * 0.42f, s * 0.14f); lineTo(s * 0.58f, s * 0.14f); lineTo(s * 0.62f, s * 0.26f)
                    moveTo(s * 0.24f, s * 0.26f); lineTo(s * 0.3f, s * 0.86f); lineTo(s * 0.7f, s * 0.86f); lineTo(s * 0.76f, s * 0.26f)
                }, color, style = stroke)
            }
            LineIcon.Close -> {
                drawLine(color, Offset(s * 0.26f, s * 0.26f), Offset(s * 0.74f, s * 0.74f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(s * 0.74f, s * 0.26f), Offset(s * 0.26f, s * 0.74f), stroke.width, StrokeCap.Round)
            }
            LineIcon.Groups -> {
                // Трое: в центре ближе и крупнее
                drawCircle(color, s * 0.11f, Offset(s * 0.5f, s * 0.36f), style = stroke)
                drawPath(Path().apply {
                    moveTo(s * 0.3f, s * 0.78f); cubicTo(s * 0.32f, s * 0.56f, s * 0.68f, s * 0.56f, s * 0.7f, s * 0.78f)
                }, color, style = stroke)
                drawCircle(color, s * 0.08f, Offset(s * 0.24f, s * 0.42f), style = stroke)
                drawCircle(color, s * 0.08f, Offset(s * 0.76f, s * 0.42f), style = stroke)
                drawLine(color, Offset(s * 0.1f, s * 0.7f), Offset(s * 0.2f, s * 0.6f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(s * 0.9f, s * 0.7f), Offset(s * 0.8f, s * 0.6f), stroke.width, StrokeCap.Round)
            }
            LineIcon.Pin -> {
                drawPath(Path().apply {
                    moveTo(s * 0.5f, s * 0.86f)
                    cubicTo(s * 0.2f, s * 0.54f, s * 0.24f, s * 0.14f, s * 0.5f, s * 0.14f)
                    cubicTo(s * 0.76f, s * 0.14f, s * 0.8f, s * 0.54f, s * 0.5f, s * 0.86f)
                    close()
                }, color, style = stroke)
                drawCircle(color, s * 0.08f, Offset(s * 0.5f, s * 0.4f), style = stroke)
            }
            LineIcon.Gift -> {
                drawRoundRect(color, topLeft = Offset(s * 0.18f, s * 0.42f), size = Size(s * 0.64f, s * 0.42f),
                    cornerRadius = CornerRadius(s * 0.05f, s * 0.05f), style = stroke)
                drawLine(color, Offset(s * 0.14f, s * 0.42f), Offset(s * 0.86f, s * 0.42f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(s * 0.5f, s * 0.42f), Offset(s * 0.5f, s * 0.84f), stroke.width, StrokeCap.Round)
                drawPath(Path().apply {
                    moveTo(s * 0.5f, s * 0.42f); cubicTo(s * 0.3f, s * 0.14f, s * 0.18f, s * 0.36f, s * 0.5f, s * 0.42f)
                    moveTo(s * 0.5f, s * 0.42f); cubicTo(s * 0.7f, s * 0.14f, s * 0.82f, s * 0.36f, s * 0.5f, s * 0.42f)
                }, color, style = stroke)
            }
            LineIcon.Link -> {
                drawRoundRect(color, topLeft = Offset(s * 0.12f, s * 0.38f), size = Size(s * 0.42f, s * 0.24f),
                    cornerRadius = CornerRadius(s * 0.12f, s * 0.12f), style = stroke)
                drawRoundRect(color, topLeft = Offset(s * 0.46f, s * 0.38f), size = Size(s * 0.42f, s * 0.24f),
                    cornerRadius = CornerRadius(s * 0.12f, s * 0.12f), style = stroke)
            }
            LineIcon.Music -> {
                drawLine(color, Offset(s * 0.4f, s * 0.7f), Offset(s * 0.4f, s * 0.22f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(s * 0.4f, s * 0.22f), Offset(s * 0.76f, s * 0.16f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(s * 0.76f, s * 0.16f), Offset(s * 0.76f, s * 0.62f), stroke.width, StrokeCap.Round)
                drawCircle(color, s * 0.1f, Offset(s * 0.3f, s * 0.72f), style = stroke)
                drawCircle(color, s * 0.1f, Offset(s * 0.66f, s * 0.64f), style = stroke)
            }
            LineIcon.Calendar -> {
                drawRoundRect(color, topLeft = Offset(s * 0.16f, s * 0.24f), size = Size(s * 0.68f, s * 0.6f),
                    cornerRadius = CornerRadius(s * 0.08f, s * 0.08f), style = stroke)
                drawLine(color, Offset(s * 0.16f, s * 0.42f), Offset(s * 0.84f, s * 0.42f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(s * 0.34f, s * 0.16f), Offset(s * 0.34f, s * 0.3f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(s * 0.66f, s * 0.16f), Offset(s * 0.66f, s * 0.3f), stroke.width, StrokeCap.Round)
            }
            LineIcon.Exit -> {
                val door = Path().apply {
                    moveTo(s * 0.5f, s * 0.2f); lineTo(s * 0.22f, s * 0.2f); lineTo(s * 0.22f, s * 0.8f); lineTo(s * 0.5f, s * 0.8f)
                }
                drawPath(door, color, style = stroke)
                drawLine(color, Offset(s * 0.42f, s * 0.5f), Offset(s * 0.84f, s * 0.5f), stroke.width, StrokeCap.Round)
                val arrow = Path().apply {
                    moveTo(s * 0.7f, s * 0.36f); lineTo(s * 0.84f, s * 0.5f); lineTo(s * 0.7f, s * 0.64f)
                }
                drawPath(arrow, color, style = stroke)
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
                if (kind == LineIcon.HeartFilled) {
                    // «Нравится» — кристальное сердце: перламутровый перелив,
                    // светлый контур и блик-звёздочка, как у слова MEMORY
                    drawPath(p, crystalBrush(s), style = Fill)
                    drawPath(p, Color.White.copy(alpha = 0.9f), style = stroke)
                    val sx = s * 0.36f
                    val sy = s * 0.36f
                    val r = s * 0.07f
                    drawLine(Color.White, Offset(sx - r, sy), Offset(sx + r, sy), stroke.width * 0.7f, StrokeCap.Round)
                    drawLine(Color.White, Offset(sx, sy - r), Offset(sx, sy + r), stroke.width * 0.7f, StrokeCap.Round)
                } else {
                    drawPath(p, color, style = stroke)
                }
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

/** Перламутр кристалла — те же цвета, что у стекла слова MEMORY. */
val CrystalColors = listOf(
    Color(0xFFFF9EDE), Color(0xFFC6A2FF), Color(0xFF8CC4FF), Color(0xFFFFF4FF), Color(0xFFD08CFF),
)

private fun crystalBrush(s: Float): Brush = Brush.linearGradient(CrystalColors, start = Offset(0f, 0f), end = Offset(s, s))

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
