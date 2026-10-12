package ru.wishka.memory.ui.components

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.wishka.memory.data.Backdrop
import ru.wishka.memory.data.BackdropStyle
import ru.wishka.memory.data.MediaFiles
import ru.wishka.memory.data.MediaKind
import ru.wishka.memory.i18n.t
import ru.wishka.memory.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Фон переписки. Все фоны коллекции рисуются кодом — тёмные, в цветах
 * Memory, чтобы пузыри читались. Своё фото — с затемнением. Те же фоны —
 * ChatBackdrop.swift.
 */
@Composable
fun BackdropView(backdrop: Backdrop, modifier: Modifier = Modifier) {
    val style = backdrop.kind
    if (style == BackdropStyle.Photo) {
        val image by produceState<ImageBitmap?>(null, backdrop.photo) {
            value = withContext(Dispatchers.IO) {
                MediaFiles.decodeUpright(backdrop.photo, 1600)?.asImageBitmap()
            }
        }
        Box(modifier.fillMaxSize().background(Palette.Void)) {
            image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = backdrop.dim.coerceIn(0f, 0.85f))))
        }
        return
    }
    if (style == BackdropStyle.Cosmos) {
        CosmicBackground(modifier)
        return
    }
    val rnd = remember(style) { Random(style.ordinal * 31 + 7) }
    val dots = remember(style) { List(90) { Triple(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat()) } }
    Canvas(modifier.fillMaxSize()) { drawBackdrop(style, dots) }
}

private fun DrawScope.glow(color: Color, x: Float, y: Float, r: Float) {
    drawRect(Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(size.width * x, size.height * y), radius = size.width * r))
}

private fun DrawScope.stars(dots: List<Triple<Float, Float, Float>>, alpha: Float = 0.5f, count: Int = dots.size) {
    dots.take(count).forEach { (x, y, s) ->
        drawCircle(Palette.Ink.copy(alpha = (0.12f + s * alpha).coerceAtMost(1f)), radius = 0.6f + s * 1.4f, center = Offset(x * size.width, y * size.height))
    }
}

private fun diamond(cx: Float, cy: Float, w: Float, h: Float) = Path().apply {
    moveTo(cx, cy - h); lineTo(cx + w, cy); lineTo(cx, cy + h); lineTo(cx - w, cy); close()
}

private fun DrawScope.drawBackdrop(style: BackdropStyle, dots: List<Triple<Float, Float, Float>>) {
    val w = size.width
    val h = size.height
    when (style) {
        BackdropStyle.Cosmos, BackdropStyle.Photo -> Unit
        BackdropStyle.Nebula -> {
            drawRect(Palette.Void)
            glow(Palette.Orchid.copy(alpha = 0.45f), 0.25f, 0.3f, 0.8f)
            glow(Palette.PearlPink.copy(alpha = 0.18f), 0.75f, 0.5f, 0.6f)
            glow(Palette.Ocean.copy(alpha = 0.40f), 0.6f, 0.85f, 0.9f)
            stars(dots, 0.5f)
        }
        BackdropStyle.Aurora -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF061226), Palette.Void)))
            // Занавеси сияния: волнистые полосы, тающие книзу
            listOf(Color(0xFF7BE0B0) to 0.18f, Palette.Lilac to 0.30f, Palette.Sky to 0.42f).forEachIndexed { i, (c, top) ->
                val path = Path().apply {
                    moveTo(0f, h * top)
                    var x = 0f
                    while (x <= w) {
                        lineTo(x, h * top + sin(x / w * 2 * PI * (1.3 + i * 0.4) + i).toFloat() * h * 0.05f)
                        x += w / 40
                    }
                    lineTo(w, h * (top + 0.35f)); lineTo(0f, h * (top + 0.35f)); close()
                }
                drawPath(path, Brush.verticalGradient(listOf(c.copy(alpha = 0.30f), Color.Transparent), startY = h * top - h * 0.05f, endY = h * (top + 0.35f)))
            }
            stars(dots, 0.4f, 50)
        }
        BackdropStyle.Crystals -> {
            drawRect(Brush.verticalGradient(listOf(Palette.Night, Palette.Void)))
            glow(Palette.Violet.copy(alpha = 0.35f), 0.8f, 0.15f, 0.7f)
            dots.take(26).forEach { (x, y, s) ->
                val r = w * (0.03f + s * 0.07f)
                val d = diamond(x * w, y * h, r * 0.6f, r)
                drawPath(d, Palette.Lilac.copy(alpha = 0.05f + s * 0.06f))
                drawPath(d, Palette.Lilac.copy(alpha = 0.12f + s * 0.12f), style = Stroke(1.2f))
            }
        }
        BackdropStyle.Constellations -> {
            drawRect(Palette.Void)
            glow(Palette.Ocean.copy(alpha = 0.25f), 0.1f, 0.9f, 0.9f)
            stars(dots, 0.6f)
            // Созвездия: соседние звёзды из первых — по линиям
            val pts = dots.take(24).map { Offset(it.first * w, it.second * h) }
            for (i in 0 until pts.size - 1 step 3) {
                drawLine(Palette.Lilac.copy(alpha = 0.22f), pts[i], pts[i + 1], strokeWidth = 1.2f)
                if (i + 2 < pts.size) drawLine(Palette.Lilac.copy(alpha = 0.18f), pts[i + 1], pts[i + 2], strokeWidth = 1.2f)
            }
            pts.forEach { drawCircle(Palette.Ink.copy(alpha = 0.8f), radius = 2.2f, center = it) }
        }
        BackdropStyle.Prism -> {
            drawRect(Palette.Void)
            // Луч входит в кристалл и выходит радугой
            val cx = w * 0.5f
            val cy = h * 0.42f
            drawLine(Palette.Ink.copy(alpha = 0.35f), Offset(0f, cy - h * 0.12f), Offset(cx - w * 0.08f, cy), strokeWidth = 3f)
            val rainbow = listOf(Color(0xFFFF8A65), Color(0xFFFFD86B), Color(0xFF7BE0B0), Palette.Sky, Palette.Orchid, Palette.PearlPink)
            rainbow.forEachIndexed { i, c ->
                val spread = (i - 2.5f) * h * 0.035f
                val beam = Path().apply {
                    moveTo(cx + w * 0.06f, cy); lineTo(w, cy + h * 0.12f + spread - h * 0.017f); lineTo(w, cy + h * 0.12f + spread + h * 0.017f); close()
                }
                drawPath(beam, c.copy(alpha = 0.22f))
            }
            val crystal = diamond(cx, cy, w * 0.08f, w * 0.14f)
            drawPath(crystal, Palette.Lilac.copy(alpha = 0.18f))
            drawPath(crystal, Palette.Ink.copy(alpha = 0.45f), style = Stroke(2f))
            stars(dots, 0.35f, 40)
        }
        BackdropStyle.Dusk -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF120B2E), Color(0xFF3A1E5C), Color(0xFF6B2F6E))))
            glow(Palette.PearlPink.copy(alpha = 0.22f), 0.5f, 1.05f, 0.9f)
            stars(dots, 0.5f, 40)
        }
        BackdropStyle.Deep -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0B2A52), Color(0xFF071634), Palette.Void)))
            glow(Palette.Sky.copy(alpha = 0.20f), 0.5f, -0.05f, 0.9f)
            dots.take(30).forEach { (x, y, s) ->
                drawCircle(Palette.Sky.copy(alpha = 0.10f + s * 0.12f), radius = 3f + s * 10f, center = Offset(x * w, y * h), style = Stroke(1.2f))
            }
        }
        BackdropStyle.Lilac -> {
            drawRect(Brush.linearGradient(listOf(Color(0xFF2B1E46), Color(0xFF4A2F6E), Color(0xFF2B1E46)), start = Offset.Zero, end = Offset(w, h)))
            glow(Palette.Lilac.copy(alpha = 0.22f), 0.7f, 0.3f, 0.7f)
            dots.take(40).forEach { (x, y, s) -> drawCircle(Palette.Lilac.copy(alpha = 0.08f + s * 0.10f), radius = 1.5f + s * 3f, center = Offset(x * w, y * h)) }
        }
        BackdropStyle.Plain -> drawRect(Palette.Night)
    }
}

/**
 * Выбор фона: коллекция плитками, своё фото и затемнение. В чате ещё
 * «Как везде» — сбросить свой фон к общему.
 */
@Composable
fun BackdropChooser(selected: Backdrop, onPick: (Backdrop) -> Unit, modifier: Modifier = Modifier, onReset: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val item = withContext(Dispatchers.IO) { MediaFiles.import(context, uri, MediaKind.Photo) }
            item?.let { onPick(Backdrop(BackdropStyle.Photo.id, it.path, selected.dim)) }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Как будет выглядеть переписка
        Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(20.dp)).border(1.dp, Palette.Line, RoundedCornerShape(20.dp))) {
            BackdropView(selected, Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    t("Привет! Как тебе новый фон?"),
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Ink,
                    modifier = Modifier.widthIn(max = 220.dp).clip(RoundedCornerShape(16.dp)).background(Palette.Plum.copy(alpha = 0.9f)).padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Text(
                        t("Очень по-космически ✦"),
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Ink,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Palette.Orchid, Palette.Violet))).padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
        val tiles = BackdropStyle.COLLECTION + BackdropStyle.Photo
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { style ->
                    val active = selected.kind == style
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.62f)
                                .clip(RoundedCornerShape(16.dp))
                                .border(if (active) 2.5.dp else 1.dp, if (active) Palette.Lilac else Palette.Line, RoundedCornerShape(16.dp))
                                .clickable {
                                    if (style == BackdropStyle.Photo) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    else onPick(Backdrop(style.id, "", selected.dim))
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (style == BackdropStyle.Photo) {
                                if (selected.kind == BackdropStyle.Photo) BackdropView(selected, Modifier.fillMaxSize())
                                else Box(Modifier.fillMaxSize().background(Palette.Plum.copy(alpha = 0.5f)))
                                Glyph(LineIcon.Image, Palette.Ink, size = 26.dp)
                            } else BackdropView(Backdrop(style.id), Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(style.title, style = MaterialTheme.typography.labelSmall, color = if (active) Palette.Ink else Palette.Mist, maxLines = 1)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (selected.kind == BackdropStyle.Photo) {
            Text(t("Затемнение — чтобы сообщения читались"), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
            Slider(
                value = selected.dim, onValueChange = { onPick(selected.copy(dim = it)) }, valueRange = 0f..0.8f,
                colors = SliderDefaults.colors(thumbColor = Palette.Ink, activeTrackColor = Palette.Lilac, inactiveTrackColor = Palette.Plum),
            )
        }
        if (onReset != null) GhostButton(t("Как во всех переписках"), onClick = onReset)
    }
}
