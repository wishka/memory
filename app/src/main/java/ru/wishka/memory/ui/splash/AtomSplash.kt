package ru.wishka.memory.ui.splash

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.res.ResourcesCompat
import ru.wishka.memory.R
import ru.wishka.memory.ui.theme.Palette

/**
 * Как ведёт себя слово на заставке. Поменяйте здесь, чтобы сравнить:
 * Dissolve — рассыпается на атомы с последней буквы, Assemble —
 * собирается из атомов с первой.
 */
val SPLASH_MODE: SplashMode = SplashMode.Dissolve

/**
 * Заставка: на фоне кристалла в центре слово MEMORY из атомов. Когда
 * анимация заканчивается — onFinished, и открывается приложение. Нажатие
 * пропускает заставку.
 */
@Composable
fun AtomSplash(onFinished: () -> Unit, mode: SplashMode = SPLASH_MODE) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val finish by rememberUpdatedState(onFinished)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Void)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { finish() },
            ),
    ) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val field = remember(width, height, mode) { sampleWord(context, "MEMORY", width, height, density, mode) }

        var time by remember { mutableLongStateOf(0L) }
        LaunchedEffect(field) {
            val start = withFrameMillis { it }
            while (time < field.totalMs) {
                time = withFrameMillis { it } - start
            }
            finish()
        }

        // Кристалл: проявляется из темноты и медленно приближается —
        // будто камера подлетает к нему, пока слово живёт
        Image(
            painter = painterResource(R.drawable.splash_crystal),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Время читается здесь, а не в теле функции: так каждый
                    // кадр меняет только слой, без перестройки экрана
                    alpha = (time / 600f).coerceIn(0f, 1f)
                    val zoom = 1.04f + 0.08f * (time.toFloat() / field.totalMs).coerceIn(0f, 1f)
                    scaleX = zoom
                    scaleY = zoom
                },
        )

        val dot = 1.1f * density
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Тёмная дымка за словом: кристалл яркий, и без неё буквы
            // терялись бы на его гранях
            drawRect(
                Brush.radialGradient(
                    colors = listOf(Palette.Void.copy(alpha = 0.78f), Palette.Void.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(size.width / 2, size.height / 2),
                    radius = size.width * 0.62f,
                )
            )
            val t = time
            // Собранное слово мягко светится сиренью
            if (mode == SplashMode.Assemble && t > field.wordCompleteMs) {
                val glow = ((t - field.wordCompleteMs) / 400f).coerceIn(0f, 1f)
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Palette.Orchid.copy(alpha = 0.35f * glow), Color.Transparent),
                        center = Offset(size.width / 2, size.height / 2),
                        radius = size.width * 0.45f,
                    )
                )
            }
            for (i in 0 until field.size) {
                if (!field.alive(i, t)) continue
                val a = field.alpha(i, t)
                if (a <= 0.01f) continue
                val d = field.decay(i, t)
                // В букве — светлый, в полёте — сирень или синий
                val tone = if (field.hue[i] < 0.5f) Palette.Lilac else Palette.Sky
                val color = lerp(Palette.Ink, tone, (d * 2.2f).coerceAtMost(1f))
                val center = Offset(field.x(i, t), field.y(i, t))
                val r = dot * field.radiusScale(i, t)
                if (d > 0f) {
                    // Ореол вокруг летящего атома
                    drawCircle(color.copy(alpha = a * 0.12f), radius = r * 3f, center = center)
                }
                drawCircle(color.copy(alpha = a), radius = r, center = center)
            }
        }
    }
}

/**
 * Слово рисуется во внеэкранное изображение шрифтом Unbounded, и каждая
 * непрозрачная точка с шагом step становится атомом.
 */
private fun sampleWord(
    context: Context,
    word: String,
    width: Int,
    height: Int,
    density: Float,
    mode: SplashMode,
): AtomField {
    if (width <= 0 || height <= 0) return AtomField(FloatArray(0), FloatArray(0), mode)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(context, R.font.unbounded_regular)
        color = android.graphics.Color.WHITE
        letterSpacing = 0.12f
        textAlign = Paint.Align.CENTER
        textSize = 100f
    }
    // Ширина слова — 78% экрана
    paint.textSize = 100f * (width * 0.78f) / paint.measureText(word)

    val boxHeight = (paint.textSize * 1.6f).toInt()
    val bitmap = Bitmap.createBitmap(width, boxHeight, Bitmap.Config.ALPHA_8)
    val baseline = boxHeight / 2f - (paint.ascent() + paint.descent()) / 2f
    AndroidCanvas(bitmap).drawText(word, width / 2f, baseline, paint)

    // Строка изображения может быть длиннее ширины (выравнивание) — шаг по rowBytes
    val stride = bitmap.rowBytes
    val pixels = ByteArray(stride * boxHeight)
    bitmap.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(pixels))
    bitmap.recycle()

    // Шаг ~1.7dp: атомов достаточно, чтобы буквы читались сплошными,
    // и не так много, чтобы старые телефоны теряли кадры
    val step = maxOf(2, (1.7f * density).toInt())
    val top = (height - boxHeight) / 2f
    val xs = ArrayList<Float>()
    val ys = ArrayList<Float>()
    var y = 0
    while (y < boxHeight) {
        var x = (y / step) % 2   // сдвиг через ряд — без видимой сетки
        while (x < width) {
            if ((pixels[y * stride + x].toInt() and 0xFF) > 110) {
                xs += x.toFloat()
                ys += top + y
            }
            x += step
        }
        y += step
    }
    return AtomField(xs.toFloatArray(), ys.toFloatArray(), mode)
}
