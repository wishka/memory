package ru.wishka.memory.ui.components

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
import kotlin.random.Random

/**
 * Процедурная «фотография» к записи — туманность с кристаллом. Пока у
 * Memory нет сервера и загрузки фото, так записи с картинкой выглядят
 * живыми. seed задаёт картину: одна и та же запись всегда выглядит одинаково.
 */
@Composable
fun Nebula(seed: Int, modifier: Modifier = Modifier) {
    val spec = remember(seed) {
        val rnd = Random(seed)
        val blobs = List(4) {
            Blob(
                x = rnd.nextFloat(), y = rnd.nextFloat(),
                r = 0.35f + rnd.nextFloat() * 0.5f,
                color = listOf(Palette.Orchid, Palette.Sky, Palette.Violet, Palette.Lilac, Palette.Ocean)[rnd.nextInt(5)],
                alpha = 0.35f + rnd.nextFloat() * 0.35f,
            )
        }
        val stars = List(60) { Triple(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat()) }
        Triple(blobs, stars, 0.3f + rnd.nextFloat() * 0.4f)
    }
    val (blobs, stars, crystalX) = spec
    Canvas(modifier = modifier) {
        drawRect(Brush.verticalGradient(listOf(Palette.Night, Palette.Void)))
        blobs.forEach { b ->
            drawRect(
                Brush.radialGradient(
                    listOf(b.color.copy(alpha = b.alpha), Color.Transparent),
                    center = Offset(b.x * size.width, b.y * size.height),
                    radius = b.r * size.width,
                )
            )
        }
        stars.forEach { (x, y, s) ->
            drawCircle(Palette.Ink.copy(alpha = 0.25f + s * 0.6f), 0.6f + s * 1.6f, Offset(x * size.width, y * size.height))
        }
        // Кристалл-ромб тонкой линией — фирменный знак Memory
        val cx = crystalX * size.width + size.width * 0.15f
        val cy = size.height * 0.5f
        val h = size.height * 0.32f
        val w = h * 0.55f
        val crystal = Path().apply {
            moveTo(cx, cy - h); lineTo(cx + w, cy); lineTo(cx, cy + h); lineTo(cx - w, cy); close()
            moveTo(cx - w, cy); lineTo(cx + w, cy)
            moveTo(cx, cy - h); lineTo(cx, cy + h)
        }
        drawPath(crystal, Brush.linearGradient(listOf(Palette.Lilac, Palette.Sky)), style = Stroke(width = 2f))
        drawCircle(Palette.Lilac.copy(alpha = 0.25f), h * 0.9f, Offset(cx, cy), style = Stroke(width = 1.2f))
    }
}

private data class Blob(val x: Float, val y: Float, val r: Float, val color: Color, val alpha: Float)
