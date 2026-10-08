package ru.wishka.memory.ui.splash

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import ru.wishka.memory.ui.theme.Palette
import kotlin.math.sin
import kotlin.random.Random

/**
 * Фон заставки без картинок: ночное небо в оттенках палитры —
 * сиреневые, сливовые и синие облака-туманности и звёзды. Всё рисуется
 * кодом, поэтому чёткое на любом экране и ничего не весит.
 */
object SplashSky {

    /** Облако: центр и радиус в долях экрана, цвет, прозрачность. */
    private class Cloud(val x: Float, val y: Float, val r: Float, val color: Color, val alpha: Float)

    private val clouds = listOf(
        Cloud(0.50f, 1.02f, 0.95f, Palette.Plum, 0.85f),     // сливовая дымка снизу
        Cloud(0.22f, 0.74f, 0.70f, Palette.Orchid, 0.55f),   // орхидея слева
        Cloud(0.42f, 0.86f, 0.55f, Palette.Lilac, 0.38f),    // светлая сирень
        Cloud(0.72f, 0.60f, 0.62f, Palette.Violet, 0.60f),   // фиолетовый
        Cloud(0.90f, 0.42f, 0.55f, Palette.Ocean, 0.55f),    // синий справа
        Cloud(0.95f, 0.30f, 0.35f, Palette.Sky, 0.30f),      // небесная кромка
        Cloud(0.10f, 0.30f, 0.45f, Palette.Violet, 0.30f),   // отсвет слева сверху
        Cloud(0.55f, 0.50f, 0.40f, Palette.Orchid, 0.22f),   // мягкий свет за словом
    )

    /** Звезда: положение 0..1, яркость, фаза мерцания. */
    private class Star(val x: Float, val y: Float, val b: Float, val phase: Float, val speed: Float)

    private val stars: List<Star> = Random(42).let { rnd ->
        List(260) {
            Star(
                x = rnd.nextFloat(),
                // гуще наверху, реже в облаках внизу
                y = rnd.nextFloat().let { it * it } * 0.95f,
                b = rnd.nextFloat().let { it * it * it },
                phase = rnd.nextFloat() * 6.283f,
                speed = 0.0015f + rnd.nextFloat() * 0.003f,
            )
        }
    }

    /**
     * Рисует небо. appear — 0..1, проявление из темноты; time — мс, для
     * мерцания звёзд.
     */
    fun DrawScope.drawSky(appear: Float, time: Long, density: Float) {
        drawRect(
            Brush.verticalGradient(
                0f to Color(0xFF05071A),
                0.45f to Palette.Night,
                0.8f to Color(0xFF1A1F5C),
                1f to Color(0xFF221A4A),
            ),
            alpha = appear,
        )
        val w = size.width
        val h = size.height
        for (c in clouds) {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(c.color.copy(alpha = c.alpha), c.color.copy(alpha = c.alpha * 0.35f), Color.Transparent),
                    center = Offset(c.x * w, c.y * h),
                    radius = c.r * w,
                ),
                alpha = appear,
            )
        }
        for (s in stars) {
            val twinkle = 0.7f + 0.3f * sin(time * s.speed + s.phase)
            val a = (0.25f + 0.75f * s.b) * twinkle * appear
            val r = (0.6f + 1.3f * s.b) * density * 0.7f
            val center = Offset(s.x * w, s.y * h)
            if (s.b > 0.5f) drawCircle(Palette.Lilac.copy(alpha = a * 0.15f), radius = r * 2.5f, center = center)
            drawCircle(Palette.Ink.copy(alpha = a), radius = r, center = center)
        }
    }
}
