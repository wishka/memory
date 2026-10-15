package ru.wishka.memory.ui.components

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.widget.ImageView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.StickerAnim
import ru.wishka.memory.data.periodMs
import ru.wishka.memory.data.pose
import java.io.File

/**
 * Гифка, которая играет сама. Android 9+ — AnimatedImageDrawable (как в
 * галерее), раньше — первый кадр картинкой.
 */
@Composable
fun GifImage(path: String, modifier: Modifier = Modifier, crop: Boolean = false) {
    if (Build.VERSION.SDK_INT >= 28) {
        val drawable by produceState<android.graphics.drawable.Drawable?>(null, path) {
            value = withContext(Dispatchers.IO) {
                runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(File(path))) }.getOrNull()
            }
        }
        AndroidView(
            factory = { ImageView(it).apply { scaleType = if (crop) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER } },
            update = { view ->
                if (view.drawable !== drawable) {
                    view.setImageDrawable(drawable)
                    (drawable as? AnimatedImageDrawable)?.start()
                }
            },
            modifier = modifier,
        )
    } else {
        val bitmap by produceState<ImageBitmap?>(null, path) {
            value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
        }
        bitmap?.let { Image(it, contentDescription = null, modifier = modifier, contentScale = if (crop) ContentScale.Crop else ContentScale.Fit) }
    }
}

/**
 * Живой стикер: кадры по кругу (покадровый) и движение (прыжок, пульс,
 * мерцание…). Неподвижный — просто картинка.
 */
@Composable
fun LiveSticker(path: String, frames: List<String>, fps: Int, animation: String, modifier: Modifier = Modifier, size: Dp? = null) {
    val paths = frames.ifEmpty { listOf(path) }
    val images by produceState<List<ImageBitmap>>(emptyList(), paths) {
        value = withContext(Dispatchers.IO) { paths.mapNotNull { p -> BitmapFactory.decodeFile(p)?.asImageBitmap() } }
    }
    LiveFrames(images, fps, animation, modifier.then(if (size != null) Modifier.size(size) else Modifier))
}

/** Живой стикер из готовых картинок — и для студии, где кадры ещё не сохранены. */
@Composable
fun LiveFrames(images: List<ImageBitmap>, fps: Int, animation: String, modifier: Modifier = Modifier) {
    val anim = StickerAnim.of(animation)
    var frame by remember(images) { mutableIntStateOf(0) }
    if (images.size > 1) {
        LaunchedEffect(images, fps) {
            val step = 1000L / fps.coerceIn(2, 24)
            while (true) {
                delay(step)
                frame = (frame + 1) % images.size
            }
        }
    }
    val transition = rememberInfiniteTransition(label = "sticker")
    // Фаза читается только при рисовании: движение не перестраивает экран 60 раз в секунду
    val cycle = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(anim.periodMs(), easing = LinearEasing), RepeatMode.Restart), label = "phase")
    fun pose() = anim.pose(if (anim == StickerAnim.None) 0f else cycle.value)
    val image = images.getOrNull(frame % images.size.coerceAtLeast(1))
    Box(modifier, contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(
                image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val pose = pose()
                        translationY = pose.dy * this.size.height
                        scaleX = pose.scale
                        scaleY = pose.scale
                        rotationZ = pose.rotation
                        // Блик по стикеру — только поверх нарисованного
                        if (pose.shine >= 0f) compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        val pose = pose()
                        if (pose.shine >= 0f) {
                            val w = this.size.width
                            val x = -w * 0.6f + w * 2.2f * pose.shine
                            drawRect(
                                Brush.linearGradient(
                                    listOf(Color.Transparent, Color.White.copy(alpha = 0.7f), Color.Transparent),
                                    start = Offset(x, 0f),
                                    end = Offset(x + w * 0.4f, this.size.height),
                                ),
                                blendMode = BlendMode.SrcAtop,
                            )
                        }
                    },
            )
        }
    }
}

/** Живой стикер из вложения. */
@Composable
fun LiveSticker(attachment: Attachment, modifier: Modifier = Modifier, size: Dp? = null) =
    LiveSticker(attachment.path, attachment.frames, attachment.fps, attachment.animation, modifier, size)

/**
 * Увеличение двумя пальцами прямо в ленте: картинка растёт в своей
 * карточке, а когда пальцы отпускают — плавно возвращается. Одним пальцем
 * лента листается как обычно. Во весь экран — ZoomableBox.
 */
private class PinchState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var active by mutableStateOf(false)
}

fun Modifier.pinchZoom(): Modifier = composed {
    val st = remember { PinchState() }
    val scope = rememberCoroutineScope()
    val back = remember { Animatable(1f) }
    this
        .zIndex(if (st.active) 10f else 0f)
        .graphicsLayer {
            scaleX = st.scale
            scaleY = st.scale
            translationX = st.offset.x
            translationY = st.offset.y
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var zooming = false
                do {
                    // Раньше детей (пейджер, лента): два пальца — наши, листание их не увидит
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.count { it.pressed }
                    if (pressed >= 2) {
                        zooming = true
                        st.active = true
                        st.scale = (st.scale * event.calculateZoom()).coerceIn(1f, 4f)
                        st.offset += event.calculatePan()
                        event.changes.forEach { it.consume() }
                    } else if (zooming) {
                        event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })
                if (zooming) {
                    val startScale = st.scale
                    val startOffset = st.offset
                    scope.launch {
                        back.snapTo(0f)
                        back.animateTo(1f, spring()) {
                            st.scale = startScale + (1f - startScale) * value
                            st.offset = startOffset * (1f - value)
                        }
                        st.active = false
                    }
                }
            }
        }
}

/**
 * Картинка на весь экран: двумя пальцами — увеличить и подвинуть,
 * двойное касание — увеличить в точке / вернуть.
 */
@Composable
fun ZoomableBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val next = (scale * zoom).coerceIn(1f, 5f)
                    // Увеличиваем вокруг пальцев, а не вокруг центра: точка под пальцами остаётся под ними
                    val c = centroid - size.center()
                    offset = if (next == 1f) Offset.Zero else c + pan - (c - offset) * (next / scale)
                    scale = next
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { tap ->
                    if (scale > 1f) { scale = 1f; offset = Offset.Zero }
                    else { scale = 2.5f; offset = (size.center() - tap) * 1.5f }
                })
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun androidx.compose.ui.unit.IntSize.center(): Offset = Offset(width / 2f, height / 2f)
