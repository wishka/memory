package ru.wishka.memory.ui.components

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * Знак Memory в шапке ленты: вытянутый кристалл, как во вкладке PRISM, —
 * с гранями и ребром, а внутри — кристальное стекло, как у слова MEMORY
 * на заставке: перламутр, светлые и тёмные грани, кант, контур и блики.
 * По стеклу время от времени пробегает блик.
 */
object CrystalMark {
    /** Перламутр — те же цвета, что у слова MEMORY. */
    private val IRIDESCENT = intArrayOf(
        0xFFFF9EDE.toInt(), 0xFFC6A2FF.toInt(), 0xFF8CC4FF.toInt(), 0xFFFFF4FF.toInt(),
        0xFFFFDB8F.toInt(), 0xFFD08CFF.toInt(), 0xFF7AA8FF.toInt(),
    )

    // Вершины в долях стороны: верх, право, низ, лево и точка ребра на «экваторе»
    const val TOP_Y = 0.04f
    const val BOTTOM_Y = 0.96f
    const val MID_Y = 0.5f
    const val HALF_W = 0.2f
    const val RIDGE_X = 0.545f

    private fun outline(s: Float) = Path().apply {
        moveTo(s * 0.5f, s * TOP_Y); lineTo(s * (0.5f + HALF_W), s * MID_Y)
        lineTo(s * 0.5f, s * BOTTOM_Y); lineTo(s * (0.5f - HALF_W), s * MID_Y); close()
    }

    private fun tri(s: Float, a: Pair<Float, Float>, b: Pair<Float, Float>, c: Pair<Float, Float>) = Path().apply {
        moveTo(s * a.first, s * a.second); lineTo(s * b.first, s * b.second); lineTo(s * c.first, s * c.second); close()
    }

    fun render(px: Int): Bitmap {
        val s = px.toFloat()
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val path = outline(s)

        // 1. Ореол: свет, рассеянный стеклом
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xA0C49BFF.toInt()
            maskFilter = BlurMaskFilter(s * 0.06f, BlurMaskFilter.Blur.NORMAL)
        })

        c.save()
        c.clipPath(path)
        // 2. Тело — перламутр по диагонали
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(s * 0.3f, 0f, s * 0.7f, s, IRIDESCENT, null, Shader.TileMode.MIRROR)
        })
        // 3. Грани: четыре плоскости кристалла — светлые и в тени
        val top = 0.5f to TOP_Y
        val bottom = 0.5f to BOTTOM_Y
        val left = (0.5f - HALF_W) to MID_Y
        val right = (0.5f + HALF_W) to MID_Y
        val ridge = RIDGE_X to MID_Y
        val light = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFFFFF }
        val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x3A4A2F9A }
        c.drawPath(tri(s, top, left, ridge), light)
        c.drawPath(tri(s, top, ridge, right), shade)
        c.drawPath(tri(s, left, bottom, ridge), shade)
        c.drawPath(tri(s, ridge, bottom, right), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x30FFFFFF })
        // Косые сколы, как у слова MEMORY
        val rnd = Random(11)
        val facet = Paint(Paint.ANTI_ALIAS_FLAG)
        var fx = -s * 0.2f
        var lit = true
        while (fx < s) {
            val w = s * (0.02f + rnd.nextFloat() * 0.05f)
            facet.color = if (lit) 0x33FFFFFF else 0x224A2F9A
            c.drawPath(Path().apply {
                moveTo(fx, 0f); lineTo(fx + w, 0f); lineTo(fx + w + s * 0.3f, s); lineTo(fx + s * 0.3f, s); close()
            }, facet)
            fx += w + s * (0.02f + rnd.nextFloat() * 0.06f)
            lit = !lit
        }
        // 4. Внутренний кант: светлая кромка и тень за ней
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = s * 0.05f; color = 0x40FFFFFF })
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = s * 0.02f; color = 0x668A6FD8 })
        c.restore()

        // 5. Рёбра: «экватор» и переднее ребро — как у кристалла в PRISM
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = s * 0.012f + 0.6f; color = 0xCCFFFFFF.toInt()
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        c.drawLine(s * left.first, s * MID_Y, s * right.first, s * MID_Y, edge)
        c.drawPath(Path().apply {
            moveTo(s * 0.5f, s * TOP_Y); lineTo(s * RIDGE_X, s * MID_Y); lineTo(s * 0.5f, s * BOTTOM_Y)
        }, edge)
        // 6. Контур стекла
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = s * 0.016f + 0.8f; color = 0xFFFFFFFF.toInt(); strokeJoin = Paint.Join.ROUND
        })
        // 7. Блики-звёздочки
        val star = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); strokeWidth = s * 0.012f + 0.5f; strokeCap = Paint.Cap.ROUND }
        for ((sx, sy, r) in listOf(Triple(0.44f, 0.30f, 0.05f), Triple(0.58f, 0.68f, 0.035f))) {
            c.drawLine(s * (sx - r), s * sy, s * (sx + r), s * sy, star)
            c.drawLine(s * sx, s * (sy - r), s * sx, s * (sy + r), star)
        }
        return bitmap
    }
}

@Composable
fun CrystalLogo(modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(8)
    val image = remember(px) { CrystalMark.render(px).asImageBitmap() }
    // Блик по стеклу: проходит сверху вниз, потом пауза
    val sweep = remember { Animatable(-0.6f) }
    LaunchedEffect(Unit) {
        while (true) {
            sweep.snapTo(-0.6f)
            sweep.animateTo(1.8f, tween(3600, easing = LinearEasing))
        }
    }
    Box(modifier.size(size)) {
        Image(image, contentDescription = null, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val t = sweep.value
            if (t !in 0f..1.2f) return@Canvas
            val s = this.size.width
            val shape = androidx.compose.ui.graphics.Path().apply {
                moveTo(s * 0.5f, s * CrystalMark.TOP_Y); lineTo(s * (0.5f + CrystalMark.HALF_W), s * CrystalMark.MID_Y)
                lineTo(s * 0.5f, s * CrystalMark.BOTTOM_Y); lineTo(s * (0.5f - CrystalMark.HALF_W), s * CrystalMark.MID_Y); close()
            }
            clipPath(shape) {
                val y = s * t
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                        start = Offset(0f, y - s * 0.18f),
                        end = Offset(s * 0.3f, y + s * 0.18f),
                    ),
                )
            }
        }
    }
}
