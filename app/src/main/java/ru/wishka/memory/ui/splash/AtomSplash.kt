package ru.wishka.memory.ui.splash

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.res.ResourcesCompat
import ru.wishka.memory.R
import ru.wishka.memory.ui.splash.SplashSky.drawSky
import ru.wishka.memory.ui.theme.Palette

/**
 * Как ведёт себя слово на заставке:
 * Assemble — собирается из атомов с первой буквы и становится кристальным
 * стеклом; AssembleFromCenter — то же, но от середины к краям;
 * Dissolve — рассыпается на атомы с последней буквы.
 */
val SPLASH_MODE: SplashMode = SplashMode.Assemble

/** Перламутровые цвета атомов в полёте — те же, что у стекла букв. */
private val ATOM_TONES = listOf(
    Color(0xFFFF9EDE), Color(0xFFC6A2FF), Color(0xFF8CC4FF), Color(0xFFFFF4FF), Color(0xFFFFDB8F),
)

/**
 * Заставка: ночное небо с облаками в оттенках палитры и мерцающими
 * звёздами, в центре слово MEMORY из атомов. Когда анимация заканчивается, вызывается
 * onFinished, и открывается приложение. Нажатие пропускает заставку.
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
        val scene = remember(width, height, mode) { buildScene(context, "MEMORY", width, height, density, mode) }
        val field = scene.field
        val crystal = remember(scene) { scene.art?.crystal?.asImageBitmap() }
        val mask = remember(scene) { scene.art?.mask?.asImageBitmap() }
        DisposableEffect(scene) {
            onDispose {
                scene.art?.crystal?.recycle()
                scene.art?.mask?.recycle()
            }
        }

        var time by remember { mutableLongStateOf(0L) }
        LaunchedEffect(field) {
            val start = withFrameMillis { it }
            while (time < field.totalMs) {
                time = withFrameMillis { it } - start
            }
            finish()
        }

        val dot = 1.1f * density
        Canvas(modifier = Modifier.fillMaxSize()) {
            val t = time
            // Небо с облаками и звёздами — проявляется из темноты
            drawSky(appear = (t / 500f).coerceIn(0f, 1f), time = t, density = density)
            val center = Offset(size.width / 2, scene.top + scene.boxHeight / 2f)
            // Лёгкая дымка за словом, чтобы стекло читалось на небе
            drawRect(
                Brush.radialGradient(
                    colors = listOf(Palette.Void.copy(alpha = 0.45f), Color.Transparent),
                    center = center,
                    radius = size.width * 0.6f,
                )
            )

            // Атомы
            for (i in 0 until field.size) {
                if (!field.alive(i, t)) continue
                val a = field.alpha(i, t)
                if (a <= 0.01f) continue
                val d = field.decay(i, t)
                val tone = ATOM_TONES[(field.hue[i] * ATOM_TONES.size).toInt().coerceIn(0, ATOM_TONES.lastIndex)]
                val color = lerp(Palette.Ink, tone, (d * 2.2f).coerceAtMost(1f))
                val p = Offset(field.x(i, t), field.y(i, t))
                val r = dot * field.radiusScale(i, t)
                if (d > 0f) drawCircle(color.copy(alpha = a * 0.14f), radius = r * 3f, center = p)
                drawCircle(color.copy(alpha = a), radius = r, center = p)
            }

            // Кристалл: атомы растворяются в стекле
            val k = field.crystal(t)
            if (crystal != null && k > 0f) {
                drawImage(crystal, topLeft = Offset(0f, scene.top), alpha = k)
            }

            // Блик пробегает по стеклу слева направо
            val s = field.shimmer(t)
            val art = scene.art
            if (mask != null && art != null && s != null) {
                val b = art.bounds
                val bandX = b.left - b.width() * 0.3f + s * b.width() * 1.6f
                val layer = Rect(0f, scene.top, size.width, scene.top + scene.boxHeight)
                drawIntoCanvas { it.saveLayer(layer, Paint()) }
                drawImage(mask, topLeft = Offset(0f, scene.top))
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.85f), Color.Transparent),
                        start = Offset(bandX - b.height() * 0.6f, scene.top),
                        end = Offset(bandX + b.height() * 0.6f, scene.top + scene.boxHeight),
                    ),
                    topLeft = Offset(0f, scene.top),
                    size = layer.size,
                    blendMode = BlendMode.SrcIn,
                )
                drawIntoCanvas { it.restore() }
            }
        }
    }
}

/** Всё, что заставке нужно заранее: атомы, кристальное слово и где оно стоит. */
private class SplashScene(
    val field: AtomField,
    val art: CrystalWord.Art?,
    val top: Float,
    val boxHeight: Int,
)

/**
 * Слово рисуется во внеэкранное изображение шрифтом Unbounded, и каждая
 * непрозрачная точка с шагом step становится атомом. Для режимов сборки
 * рядом рисуется кристальная версия слова.
 */
private fun buildScene(
    context: Context,
    word: String,
    width: Int,
    height: Int,
    density: Float,
    mode: SplashMode,
): SplashScene {
    if (width <= 0 || height <= 0) return SplashScene(AtomField(FloatArray(0), FloatArray(0), mode), null, 0f, 0)
    val crystalMode = mode != SplashMode.Dissolve
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        // Стеклу нужна толщина: для кристалла — полужирный, для атомов — обычный
        typeface = ResourcesCompat.getFont(
            context, if (crystalMode) R.font.unbounded_semibold else R.font.unbounded_regular,
        )
        color = android.graphics.Color.WHITE
        letterSpacing = 0.1f
        textAlign = AndroidPaint.Align.CENTER
        textSize = 100f
    }
    // Ширина слова — 82% экрана
    paint.textSize = 100f * (width * 0.82f) / paint.measureText(word)

    val boxHeight = (paint.textSize * 1.8f).toInt()
    val baseline = boxHeight / 2f - (paint.ascent() + paint.descent()) / 2f
    // Слово — в центре экрана, на кристалле
    val top = height * 0.5f - boxHeight / 2f

    val bitmap = Bitmap.createBitmap(width, boxHeight, Bitmap.Config.ALPHA_8)
    AndroidCanvas(bitmap).drawText(word, width / 2f, baseline, paint)
    // Строка изображения может быть длиннее ширины (выравнивание) — шаг по rowBytes
    val stride = bitmap.rowBytes
    val pixels = ByteArray(stride * boxHeight)
    bitmap.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(pixels))
    bitmap.recycle()

    // Шаг ~1.7dp: атомов достаточно, чтобы буквы читались сплошными,
    // и не так много, чтобы старые телефоны теряли кадры
    val step = maxOf(2, (1.7f * density).toInt())
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
    val art = if (crystalMode) CrystalWord.render(paint, word, width, boxHeight, width / 2f, baseline) else null
    return SplashScene(AtomField(xs.toFloatArray(), ys.toFloatArray(), mode), art, top, boxHeight)
}
