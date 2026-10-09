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
enum class LineIcon { Reply, Forward, ChevronDown, Pushpin, Feed, Stories, Prism, Share, Bookmark, BookmarkFilled, Attach, Camera, Video, Image, Document, Trash, Play, Close, Groups, Pin, Gift, Link, Music, Calendar, Exit, Chats, Profile, Heart, HeartFilled, Comment, Send, Back, Edit, Plus, Bell, Info, Gear, Shield, Lock, Block, TextSize, Globe, BellOff, More, Eye, Check, Flash, Flip, Scissors, Speed, Crop, Filter, Sliders, Text, Sticker, Volume, Rotate, Mirror, Pause }

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
            LineIcon.Pushpin -> {
                // Канцелярская кнопка, наклонённая вправо
                drawPath(Path().apply {
                    moveTo(s * 0.56f, s * 0.12f); lineTo(s * 0.88f, s * 0.44f)
                    lineTo(s * 0.74f, s * 0.48f); lineTo(s * 0.58f, s * 0.64f)
                    lineTo(s * 0.58f, s * 0.78f); lineTo(s * 0.22f, s * 0.42f)
                    lineTo(s * 0.36f, s * 0.42f); lineTo(s * 0.52f, s * 0.26f); close()
                }, color, style = stroke)
                drawLine(color, Offset(s * 0.40f, s * 0.60f), Offset(s * 0.14f, s * 0.86f), stroke.width, StrokeCap.Round)
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
            LineIcon.Gear -> {
                // Шестерёнка: кольцо, восемь зубцов и ступица
                drawCircle(color, s * 0.24f, Offset(s * 0.5f, s * 0.5f), style = stroke)
                drawCircle(color, s * 0.09f, Offset(s * 0.5f, s * 0.5f), style = stroke)
                for (k in 0 until 8) {
                    val a = Math.toRadians(k * 45.0)
                    val cx = kotlin.math.cos(a).toFloat()
                    val cy = kotlin.math.sin(a).toFloat()
                    drawLine(
                        color, Offset(s * (0.5f + cx * 0.25f), s * (0.5f + cy * 0.25f)),
                        Offset(s * (0.5f + cx * 0.37f), s * (0.5f + cy * 0.37f)), stroke.width * 1.6f, StrokeCap.Round,
                    )
                }
            }
            LineIcon.Shield -> {
                drawPath(Path().apply {
                    moveTo(s * 0.5f, s * 0.12f); lineTo(s * 0.8f, s * 0.24f); lineTo(s * 0.8f, s * 0.46f)
                    cubicTo(s * 0.8f, s * 0.68f, s * 0.64f, s * 0.82f, s * 0.5f, s * 0.88f)
                    cubicTo(s * 0.36f, s * 0.82f, s * 0.2f, s * 0.68f, s * 0.2f, s * 0.46f)
                    lineTo(s * 0.2f, s * 0.24f); close()
                }, color, style = stroke)
                diamond(Offset(s * 0.5f, s * 0.48f), s * 0.1f, color, stroke)
            }
            LineIcon.Lock -> {
                drawRoundRect(
                    color, Offset(s * 0.24f, s * 0.44f), Size(s * 0.52f, s * 0.4f),
                    CornerRadius(s * 0.08f, s * 0.08f), style = stroke,
                )
                drawArc(
                    color, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(s * 0.34f, s * 0.18f), size = Size(s * 0.32f, s * 0.4f), style = stroke,
                )
                drawLine(color, Offset(s * 0.34f, s * 0.38f), Offset(s * 0.34f, s * 0.44f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(s * 0.66f, s * 0.38f), Offset(s * 0.66f, s * 0.44f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(s * 0.5f, s * 0.58f), Offset(s * 0.5f, s * 0.7f), stroke.width, StrokeCap.Round)
            }
            LineIcon.Block -> {
                drawCircle(color, s * 0.32f, Offset(s * 0.5f, s * 0.5f), style = stroke)
                drawLine(color, Offset(s * 0.28f, s * 0.28f), Offset(s * 0.72f, s * 0.72f), stroke.width, StrokeCap.Round)
            }
            LineIcon.TextSize -> {
                // Большая и маленькая «А»
                drawPath(Path().apply {
                    moveTo(s * 0.1f, s * 0.8f); lineTo(s * 0.34f, s * 0.2f); lineTo(s * 0.58f, s * 0.8f)
                }, color, style = stroke)
                drawLine(color, Offset(s * 0.19f, s * 0.58f), Offset(s * 0.49f, s * 0.58f), stroke.width, StrokeCap.Round)
                drawPath(Path().apply {
                    moveTo(s * 0.6f, s * 0.8f); lineTo(s * 0.75f, s * 0.44f); lineTo(s * 0.9f, s * 0.8f)
                }, color, style = stroke)
                drawLine(color, Offset(s * 0.66f, s * 0.67f), Offset(s * 0.84f, s * 0.67f), stroke.width, StrokeCap.Round)
            }
            LineIcon.Globe -> {
                drawCircle(color, s * 0.34f, Offset(s * 0.5f, s * 0.5f), style = stroke)
                drawOval(color, Offset(s * 0.36f, s * 0.16f), Size(s * 0.28f, s * 0.68f), style = stroke)
                drawLine(color, Offset(s * 0.16f, s * 0.5f), Offset(s * 0.84f, s * 0.5f), stroke.width, StrokeCap.Round)
            }
            LineIcon.BellOff -> {
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
                drawLine(color, Offset(s * 0.16f, s * 0.16f), Offset(s * 0.84f, s * 0.84f), stroke.width, StrokeCap.Round)
            }
            LineIcon.More -> {
                for (i in 0..2) drawCircle(color, s * 0.05f, Offset(s * (0.28f + i * 0.22f), s * 0.5f))
            }
            LineIcon.Eye -> {
                drawPath(Path().apply {
                    moveTo(s * 0.1f, s * 0.5f)
                    quadraticTo(s * 0.5f, s * 0.1f, s * 0.9f, s * 0.5f)
                    quadraticTo(s * 0.5f, s * 0.9f, s * 0.1f, s * 0.5f)
                    close()
                }, color, style = stroke)
                drawCircle(color, s * 0.11f, Offset(s * 0.5f, s * 0.5f), style = stroke)
            }
            LineIcon.Check -> {
                drawPath(Path().apply {
                    moveTo(s * 0.22f, s * 0.52f); lineTo(s * 0.42f, s * 0.72f); lineTo(s * 0.8f, s * 0.3f)
                }, color, style = stroke)
            }
            LineIcon.Flash -> {
                drawPath(Path().apply {
                    moveTo(s * 0.56f, s * 0.12f); lineTo(s * 0.26f, s * 0.56f); lineTo(s * 0.48f, s * 0.56f)
                    lineTo(s * 0.42f, s * 0.88f); lineTo(s * 0.74f, s * 0.42f); lineTo(s * 0.52f, s * 0.42f); close()
                }, color, style = stroke)
            }
            LineIcon.Flip -> {
                // Две стрелки по кругу — сменить камеру
                drawArc(color, 200f, 140f, false, Offset(s * 0.18f, s * 0.18f), Size(s * 0.64f, s * 0.64f), style = stroke)
                drawArc(color, 20f, 140f, false, Offset(s * 0.18f, s * 0.18f), Size(s * 0.64f, s * 0.64f), style = stroke)
                drawPath(Path().apply { moveTo(s * 0.70f, s * 0.14f); lineTo(s * 0.76f, s * 0.30f); lineTo(s * 0.60f, s * 0.32f) }, color, style = stroke)
                drawPath(Path().apply { moveTo(s * 0.30f, s * 0.86f); lineTo(s * 0.24f, s * 0.70f); lineTo(s * 0.40f, s * 0.68f) }, color, style = stroke)
            }
            LineIcon.Scissors -> {
                drawCircle(color, s * 0.11f, Offset(s * 0.28f, s * 0.74f), style = stroke)
                drawCircle(color, s * 0.11f, Offset(s * 0.72f, s * 0.74f), style = stroke)
                drawLine(color, Offset(s * 0.36f, s * 0.66f), Offset(s * 0.68f, s * 0.16f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(s * 0.64f, s * 0.66f), Offset(s * 0.32f, s * 0.16f), stroke.width, StrokeCap.Round)
            }
            LineIcon.Speed -> {
                drawArc(color, 160f, 220f, false, Offset(s * 0.14f, s * 0.2f), Size(s * 0.72f, s * 0.72f), style = stroke)
                drawLine(color, Offset(s * 0.5f, s * 0.56f), Offset(s * 0.68f, s * 0.38f), stroke.width, StrokeCap.Round)
                drawCircle(color, s * 0.04f, Offset(s * 0.5f, s * 0.56f))
            }
            LineIcon.Crop -> {
                drawPath(Path().apply { moveTo(s * 0.28f, s * 0.12f); lineTo(s * 0.28f, s * 0.72f); lineTo(s * 0.88f, s * 0.72f) }, color, style = stroke)
                drawPath(Path().apply { moveTo(s * 0.12f, s * 0.28f); lineTo(s * 0.72f, s * 0.28f); lineTo(s * 0.72f, s * 0.88f) }, color, style = stroke)
            }
            LineIcon.Filter -> {
                drawCircle(color, s * 0.2f, Offset(s * 0.38f, s * 0.4f), style = stroke)
                drawCircle(color, s * 0.2f, Offset(s * 0.62f, s * 0.4f), style = stroke)
                drawCircle(color, s * 0.2f, Offset(s * 0.5f, s * 0.62f), style = stroke)
            }
            LineIcon.Sliders -> {
                for ((i, k) in listOf(0.34f, 0.62f, 0.46f).withIndex()) {
                    val y = s * (0.26f + i * 0.24f)
                    drawLine(color, Offset(s * 0.14f, y), Offset(s * 0.86f, y), stroke.width, StrokeCap.Round)
                    drawCircle(color, s * 0.07f, Offset(s * k, y), style = Fill)
                }
            }
            LineIcon.Text -> {
                drawLine(color, Offset(s * 0.2f, s * 0.22f), Offset(s * 0.8f, s * 0.22f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(s * 0.5f, s * 0.22f), Offset(s * 0.5f, s * 0.82f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(s * 0.38f, s * 0.82f), Offset(s * 0.62f, s * 0.82f), stroke.width, StrokeCap.Round)
            }
            LineIcon.Sticker -> {
                drawPath(Path().apply {
                    moveTo(s * 0.2f, s * 0.2f); lineTo(s * 0.8f, s * 0.2f); lineTo(s * 0.8f, s * 0.58f)
                    lineTo(s * 0.58f, s * 0.8f); lineTo(s * 0.2f, s * 0.8f); close()
                }, color, style = stroke)
                drawPath(Path().apply { moveTo(s * 0.8f, s * 0.58f); lineTo(s * 0.58f, s * 0.58f); lineTo(s * 0.58f, s * 0.8f) }, color, style = stroke)
                diamond(Offset(s * 0.42f, s * 0.42f), s * 0.08f, color, stroke)
            }
            LineIcon.Volume -> {
                drawPath(Path().apply {
                    moveTo(s * 0.14f, s * 0.4f); lineTo(s * 0.3f, s * 0.4f); lineTo(s * 0.5f, s * 0.22f)
                    lineTo(s * 0.5f, s * 0.78f); lineTo(s * 0.3f, s * 0.6f); lineTo(s * 0.14f, s * 0.6f); close()
                }, color, style = stroke)
                drawArc(color, -45f, 90f, false, Offset(s * 0.42f, s * 0.32f), Size(s * 0.24f, s * 0.36f), style = stroke)
                drawArc(color, -50f, 100f, false, Offset(s * 0.42f, s * 0.2f), Size(s * 0.4f, s * 0.6f), style = stroke)
            }
            LineIcon.Rotate -> {
                drawArc(color, -60f, 290f, false, Offset(s * 0.18f, s * 0.2f), Size(s * 0.64f, s * 0.64f), style = stroke)
                drawPath(Path().apply { moveTo(s * 0.66f, s * 0.12f); lineTo(s * 0.68f, s * 0.3f); lineTo(s * 0.5f, s * 0.28f) }, color, style = stroke)
            }
            LineIcon.Mirror -> {
                drawLine(color, Offset(s * 0.5f, s * 0.12f), Offset(s * 0.5f, s * 0.88f), stroke.width * 0.7f, StrokeCap.Round)
                drawPath(Path().apply { moveTo(s * 0.4f, s * 0.24f); lineTo(s * 0.4f, s * 0.76f); lineTo(s * 0.14f, s * 0.76f); close() }, color, style = stroke)
                drawPath(Path().apply { moveTo(s * 0.6f, s * 0.24f); lineTo(s * 0.6f, s * 0.76f); lineTo(s * 0.86f, s * 0.76f); close() }, color, style = stroke)
            }
            LineIcon.Pause -> {
                drawLine(color, Offset(s * 0.36f, s * 0.24f), Offset(s * 0.36f, s * 0.76f), stroke.width * 1.6f, StrokeCap.Round)
                drawLine(color, Offset(s * 0.64f, s * 0.24f), Offset(s * 0.64f, s * 0.76f), stroke.width * 1.6f, StrokeCap.Round)
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
