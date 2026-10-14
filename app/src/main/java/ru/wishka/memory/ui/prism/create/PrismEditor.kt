@file:OptIn(ExperimentalLayoutApi::class)

package ru.wishka.memory.ui.prism.create

import androidx.annotation.OptIn as AndroidOptIn
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ru.wishka.memory.data.Aspect
import ru.wishka.memory.data.ColorMatrix
import ru.wishka.memory.data.MusicTrack
import ru.wishka.memory.data.OVERLAY_COLORS
import ru.wishka.memory.data.OverlayFont
import ru.wishka.memory.data.OverlayPainter
import ru.wishka.memory.data.OverlayStyle
import ru.wishka.memory.data.STICKERS
import ru.wishka.memory.data.VideoEdit
import ru.wishka.memory.data.VideoExporter
import ru.wishka.memory.data.VideoFilter
import ru.wishka.memory.data.VideoOverlay
import ru.wishka.memory.data.durationTitle
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.components.Chip
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.theme.Inter
import ru.wishka.memory.ui.theme.Palette
import ru.wishka.memory.ui.theme.Unbounded
import java.io.File

/** Инструменты редактора — вкладки внизу. */
@AndroidOptIn(UnstableApi::class)
enum class EditorTool(val title: String, val icon: LineIcon) {
    Trim("Обрезка", LineIcon.Scissors),
    Speed("Скорость", LineIcon.Speed),
    Frame("Кадр", LineIcon.Crop),
    Filters("Фильтры", LineIcon.Filter),
    Adjust("Коррекция", LineIcon.Sliders),
    Text("Текст", LineIcon.Text),
    Stickers("Стикеры", LineIcon.Sticker),
    Music("Музыка", LineIcon.Music),
    Sound("Звук", LineIcon.Volume),
    Cover("Обложка", LineIcon.Image),
}

/**
 * Редактор PRISM: предпросмотр с правками и инструменты. Видео не
 * меняется до публикации — правки копятся в VideoEdit, предпросмотр
 * показывает их сразу (цвет, кадр, скорость, звук — через ExoPlayer,
 * текст и стикеры — поверх, их можно двигать пальцем).
 */
@AndroidOptIn(UnstableApi::class)
@Composable
fun PrismEditor(
    /** Memory+: все фильтры и цветокоррекция. */
    plus: Boolean,
    onLocked: (ru.wishka.memory.data.PremiumFeature) -> Unit,
    initial: VideoEdit,
    tracks: List<MusicTrack>,
    onAddTracks: (List<MusicTrack>) -> Unit,
    onBack: () -> Unit,
    onNext: (VideoEdit) -> Unit,
) {
    var choosingMusic by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(initial) }
    var tool by remember { mutableStateOf(EditorTool.Trim) }
    var selected by remember { mutableStateOf<Long?>(null) }
    var editingText by remember { mutableStateOf<VideoOverlay?>(null) }
    var nextOverlayId by remember { mutableLongStateOf(1L) }
    val musicPath = edit.musicTrackId?.let { id -> tracks.firstOrNull { it.id == id }?.path }

    fun update(change: (VideoEdit) -> VideoEdit) { edit = change(edit) }
    fun updateOverlay(id: Long, change: (VideoOverlay) -> VideoOverlay) {
        edit = edit.copy(overlays = edit.overlays.map { if (it.id == id) change(it) else it })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Void)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundTool(LineIcon.Back, onBack)
            Text(
                tf("%s · %s", t("Редактор"), durationTitle(edit.outputDurationMs)),
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Ink,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            GlowButton(t("Далее"), onClick = { onNext(edit) })
        }

        // Предпросмотр: кадр нужных пропорций, текст и стикеры поверх
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(edit.outputAspect(), matchHeightConstraintsFirst = edit.outputAspect() < 1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black),
            ) {
                EditorPreview(edit, musicPath, Modifier.fillMaxSize())
                OverlayLayer(
                    overlays = edit.overlays,
                    selected = selected,
                    onSelect = { id ->
                        selected = id
                        tool = if (edit.overlays.firstOrNull { it.id == id }?.sticker == true) EditorTool.Stickers else EditorTool.Text
                    },
                    onMove = { id, dx, dy -> updateOverlay(id) { it.copy(x = (it.x + dx).coerceIn(0f, 1f), y = (it.y + dy).coerceIn(0f, 1f)) } },
                )
            }
        }

        // Панель выбранного инструмента
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (tool) {
                EditorTool.Trim -> TrimPanel(edit) { start, end -> update { it.copy(trimStartMs = start, trimEndMs = end, coverMs = it.coverMs.coerceIn(start, end)) } }
                EditorTool.Speed -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoEdit.SPEEDS.forEach { s ->
                        Chip(speedTitle(s), active = edit.speed == s) { update { it.copy(speed = s) } }
                    }
                }
                EditorTool.Frame -> FramePanel(edit, ::update)
                EditorTool.Filters -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoFilter.entries.forEach { f ->
                        val locked = !plus && f !in ru.wishka.memory.data.Premium.FREE_FILTERS
                        FilterChip(f, active = edit.filter == f, locked = locked) {
                            if (locked) onLocked(ru.wishka.memory.data.PremiumFeature.PrismFilters)
                            else update { it.copy(filter = f) }
                        }
                    }
                }
                EditorTool.Adjust -> if (!plus) {
                    Text(
                        t("Яркость, контраст, насыщенность и тепло ролика — в Memory+."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Mist,
                    )
                    GhostButton(t("Подробнее"), onClick = { onLocked(ru.wishka.memory.data.PremiumFeature.PrismAdjust) })
                } else {
                    AdjustSlider(t("Яркость"), edit.brightness) { v -> update { it.copy(brightness = v) } }
                    AdjustSlider(t("Контраст"), edit.contrast) { v -> update { it.copy(contrast = v) } }
                    AdjustSlider(t("Насыщенность"), edit.saturation) { v -> update { it.copy(saturation = v) } }
                    AdjustSlider(t("Тепло"), edit.warmth) { v -> update { it.copy(warmth = v) } }
                    GhostButton(t("Сбросить"), onClick = { update { it.copy(brightness = 0f, contrast = 0f, saturation = 0f, warmth = 0f) } })
                }
                EditorTool.Text -> TextPanel(
                    selected = edit.overlays.firstOrNull { it.id == selected && !it.sticker },
                    onAdd = { editingText = VideoOverlay(id = -1, text = "") },
                    onEdit = { editingText = it },
                    onChange = { o -> updateOverlay(o.id) { o } },
                    onDelete = { id -> update { e -> e.copy(overlays = e.overlays.filterNot { it.id == id }) }; selected = null },
                )
                EditorTool.Stickers -> StickerPanel(
                    selected = edit.overlays.firstOrNull { it.id == selected && it.sticker },
                    onAdd = { emoji ->
                        val o = VideoOverlay(nextOverlayId++, emoji, size = 0.09f, sticker = true)
                        update { it.copy(overlays = it.overlays + o) }
                        selected = o.id
                    },
                    onChange = { o -> updateOverlay(o.id) { o } },
                    onDelete = { id -> update { e -> e.copy(overlays = e.overlays.filterNot { it.id == id }) }; selected = null },
                )
                EditorTool.Music -> MusicPanel(edit, tracks, ::update) { choosingMusic = true }
                EditorTool.Sound -> {
                    LabeledSlider(t("Звук видео"), edit.originalVolume, 0f..1f, percent(edit.originalVolume)) { v -> update { it.copy(originalVolume = v) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(t("Без звука"), active = edit.originalVolume == 0f) { update { it.copy(originalVolume = 0f) } }
                        Chip(t("Как снято"), active = edit.originalVolume == 1f) { update { it.copy(originalVolume = 1f) } }
                    }
                    if (edit.musicTrackId != null) {
                        LabeledSlider(t("Музыка"), edit.musicVolume, 0f..1f, percent(edit.musicVolume)) { v -> update { it.copy(musicVolume = v) } }
                    }
                }
                EditorTool.Cover -> CoverPanel(edit) { ms -> update { it.copy(coverMs = ms) } }
            }
        }

        // Вкладки инструментов
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EditorTool.entries.forEach { t0 ->
                val active = t0 == tool
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .then(if (active) Modifier.glass(RoundedCornerShape(14.dp), strong = true) else Modifier)
                        .clickable { tool = t0 }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Glyph(t0.icon, if (active) Palette.Ink else Palette.Lilac, size = 22.dp)
                    Text(t(t0.title), style = MaterialTheme.typography.labelSmall, color = if (active) Palette.Ink else Palette.Mist, maxLines = 1)
                }
            }
        }
    }

    // Ввод и правка текста — поверх редактора
    editingText?.let { draft ->
        TextEditorSheet(
            draft = draft,
            onDone = { result ->
                editingText = null
                if (result.text.isBlank()) return@TextEditorSheet
                if (result.id < 0) {
                    val o = result.copy(id = nextOverlayId++)
                    edit = edit.copy(overlays = edit.overlays + o)
                    selected = o.id
                } else {
                    updateOverlay(result.id) { result }
                }
            },
            onCancel = { editingText = null },
        )
    }

    // Медиатека: трек скачивается в «Мою музыку» и сразу встаёт на ролик
    if (choosingMusic) {
        MusicPicker(
            tracks = tracks,
            onAddTracks = onAddTracks,
            onPick = { picked ->
                choosingMusic = false
                edit = edit.copy(musicTrackId = picked.id, musicStartMs = 0)
            },
            onDismiss = { choosingMusic = false },
        )
    }
}

@AndroidOptIn(UnstableApi::class)
fun speedTitle(s: Float): String = (if (s % 1f == 0f) "${s.toInt()}" else "$s".replace('.', ',')) + "×"

@AndroidOptIn(UnstableApi::class)
private fun percent(v: Float) = "${(v * 100).toInt()}%"

/**
 * Предпросмотр: ExoPlayer по кругу с теми же эффектами, что попадут в
 * файл (поворот, кадр, цвет), скоростью и громкостью. Музыка — вторым
 * плеером. Эффекты меняются с небольшой задержкой: плеер собирается
 * заново, пока человек тянет ползунок, это было бы слишком часто.
 */
@AndroidOptIn(UnstableApi::class)
@Composable
private fun EditorPreview(edit: VideoEdit, musicPath: String?, modifier: Modifier) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE } }
    val music = remember { ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE } }
    DisposableEffect(Unit) {
        onDispose {
            player.release()
            music.release()
        }
    }
    val visualKey = listOf(
        edit.trimStartMs, edit.trimEndMs, edit.rotation, edit.mirror, edit.aspect,
        edit.colorMatrix().toList(),
    )
    LaunchedEffect(visualKey) {
        delay(180)
        val item = MediaItem.Builder()
            .setUri(Uri.fromFile(File(edit.sourcePath)))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(edit.trimStartMs)
                    .setEndPositionMs(edit.trimEndMs)
                    .build()
            )
            .build()
        player.stop()
        player.setVideoEffects(VideoExporter.videoEffects(edit))
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
    }
    LaunchedEffect(edit.speed) { player.playbackParameters = PlaybackParameters(edit.speed) }
    LaunchedEffect(edit.originalVolume) { player.volume = edit.originalVolume }
    LaunchedEffect(musicPath, edit.musicStartMs) {
        if (musicPath == null) {
            music.stop()
            return@LaunchedEffect
        }
        music.setMediaItem(
            MediaItem.Builder()
                .setUri(Uri.fromFile(File(musicPath)))
                .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(edit.musicStartMs).build())
                .build()
        )
        music.prepare()
        music.playWhenReady = true
    }
    LaunchedEffect(edit.musicVolume) { music.volume = edit.musicVolume }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                this.player = player
            }
        },
    )
}

/** Текст и стикеры поверх кадра: центр каждого — в (x, y); тянется пальцем. */
@AndroidOptIn(UnstableApi::class)
@Composable
private fun OverlayLayer(
    overlays: List<VideoOverlay>,
    selected: Long?,
    onSelect: (Long) -> Unit,
    onMove: (Long, Float, Float) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val move by rememberUpdatedState(onMove)
        Layout(
            content = {
                overlays.forEach { o ->
                    Box(
                        modifier = Modifier
                            .then(if (o.id == selected) Modifier.border(1.dp, Palette.Lilac, RoundedCornerShape(8.dp)) else Modifier)
                            .pointerInput(o.id) {
                                detectDragGestures(onDragStart = { onSelect(o.id) }) { change, drag ->
                                    change.consume()
                                    move(o.id, drag.x / w, drag.y / h)
                                }
                            }
                            .clickable { onSelect(o.id) }
                            .padding(4.dp),
                    ) {
                        OverlayText(o, with(density) { (o.size * h).toSp() })
                    }
                }
            },
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEachIndexed { i, p ->
                    val o = overlays[i]
                    p.place((o.x * w - p.width / 2f).toInt(), (o.y * h - p.height / 2f).toInt())
                }
            }
        }
    }
}

/** Текст или стикер так же, как он будет нарисован в файле (OverlayPainter). */
@AndroidOptIn(UnstableApi::class)
@Composable
fun OverlayText(o: VideoOverlay, fontSize: androidx.compose.ui.unit.TextUnit) {
    val color = Color(o.color)
    val family = if (o.font == OverlayFont.Unbounded) Unbounded else Inter
    val base = TextStyle(fontSize = fontSize, lineHeight = fontSize * 1.2f, fontFamily = if (o.sticker) null else family, textAlign = TextAlign.Center)
    when {
        o.sticker -> Text(o.text, style = base.copy(color = color))
        o.style == OverlayStyle.Box -> Text(
            o.text,
            style = base.copy(color = Color(OverlayPainter.contrastOn(o.color.toInt()))),
            modifier = Modifier.background(color, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 2.dp),
        )
        o.style == OverlayStyle.Glow -> Text(o.text, style = base.copy(color = color, shadow = Shadow(color, blurRadius = 24f)))
        else -> Text(o.text, style = base.copy(color = color, shadow = Shadow(Color.Black.copy(alpha = 0.55f), blurRadius = 6f)))
    }
}

@AndroidOptIn(UnstableApi::class)
@Composable
private fun TrimPanel(edit: VideoEdit, onChange: (Long, Long) -> Unit) {
    val total = edit.sourceDurationMs.toFloat()
    Text(
        tf("%s — %s · %s", durationTitle(edit.trimStartMs), durationTitle(edit.trimEndMs), durationTitle(edit.trimEndMs - edit.trimStartMs)),
        style = MaterialTheme.typography.labelLarge,
        color = Palette.Ink,
    )
    RangeSlider(
        value = edit.trimStartMs.toFloat()..edit.trimEndMs.toFloat(),
        onValueChange = { r ->
            var start = r.start.toLong()
            var end = r.endInclusive.toLong()
            // Не короче секунды и не длиннее минуты
            if (end - start < VideoEdit.MIN_DURATION_MS) {
                if (start != edit.trimStartMs) start = end - VideoEdit.MIN_DURATION_MS else end = start + VideoEdit.MIN_DURATION_MS
            }
            if (end - start > VideoEdit.MAX_DURATION_MS) {
                if (start != edit.trimStartMs) end = start + VideoEdit.MAX_DURATION_MS else start = end - VideoEdit.MAX_DURATION_MS
            }
            onChange(start.coerceIn(0, edit.sourceDurationMs), end.coerceIn(0, edit.sourceDurationMs))
        },
        valueRange = 0f..total,
        colors = sliderColors(),
    )
    Text(t("PRISM — до минуты. Перетащите края, чтобы оставить нужное."), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
}

@AndroidOptIn(UnstableApi::class)
@Composable
private fun FramePanel(edit: VideoEdit, update: ((VideoEdit) -> VideoEdit) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Aspect.entries.forEach { a -> Chip(t(a.title), active = edit.aspect == a) { update { it.copy(aspect = a) } } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ToolButton(LineIcon.Rotate, t("Повернуть")) { update { it.copy(rotation = (it.rotation + 90) % 360) } }
        ToolButton(LineIcon.Mirror, t("Отразить"), active = edit.mirror) { update { it.copy(mirror = !it.mirror) } }
    }
}

/** Фильтр: образец цвета (сиреневый кружок через матрицу фильтра) и название. */
@AndroidOptIn(UnstableApi::class)
@Composable
private fun FilterChip(f: VideoFilter, active: Boolean, locked: Boolean = false, onClick: () -> Unit) {
    val sample = remember(f) {
        val (r, g, b) = ColorMatrix.apply(f.matrix, 0.74f, 0.57f, 0.8f)
        Color(r, g, b)
    }
    Row(
        modifier = Modifier
            .glass(RoundedCornerShape(50), strong = active)
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(sample))
        Spacer(Modifier.width(6.dp))
        Text(t(f.title), style = MaterialTheme.typography.labelLarge, color = if (active) Palette.Ink else Palette.Lilac)
        if (locked) {
            Spacer(Modifier.width(6.dp))
            ru.wishka.memory.ui.premium.PlusBadge()
        }
    }
}

@AndroidOptIn(UnstableApi::class)
@Composable
private fun AdjustSlider(title: String, value: Float, onChange: (Float) -> Unit) {
    LabeledSlider(title, value, -1f..1f, "${if (value > 0) "+" else ""}${(value * 100).toInt()}", onChange)
}

@AndroidOptIn(UnstableApi::class)
@Composable
fun LabeledSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Palette.Ink, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, colors = sliderColors())
    }
}

@AndroidOptIn(UnstableApi::class)
@Composable
fun sliderColors() = SliderDefaults.colors(
    thumbColor = Palette.Ink,
    activeTrackColor = Palette.Orchid,
    inactiveTrackColor = Palette.Plum,
)

@AndroidOptIn(UnstableApi::class)
@Composable
private fun TextPanel(
    selected: VideoOverlay?,
    onAdd: () -> Unit,
    onEdit: (VideoOverlay) -> Unit,
    onChange: (VideoOverlay) -> Unit,
    onDelete: (Long) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlowButton(t("Добавить текст"), onClick = onAdd)
        if (selected != null) GhostButton(t("Изменить"), onClick = { onEdit(selected) })
    }
    if (selected == null) {
        Text(t("Нажмите на текст в кадре, чтобы выбрать его; перетаскивайте пальцем."), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
        return
    }
    OverlayStyleControls(selected, onChange)
    LabeledSlider(t("Размер"), selected.size, 0.025f..0.14f, "") { v -> onChange(selected.copy(size = v)) }
    GhostButton(t("Удалить текст"), onClick = { onDelete(selected.id) })
}

/** Цвет, оформление и шрифт текста. */
@AndroidOptIn(UnstableApi::class)
@Composable
private fun OverlayStyleControls(o: VideoOverlay, onChange: (VideoOverlay) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OVERLAY_COLORS.forEach { c ->
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(if (o.color == c) 2.dp else 1.dp, if (o.color == c) Palette.Lilac else Palette.Line, CircleShape)
                    .clickable { onChange(o.copy(color = c)) },
            )
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OverlayStyle.entries.forEach { s -> Chip(t(s.title), active = o.style == s) { onChange(o.copy(style = s)) } }
        OverlayFont.entries.forEach { f -> Chip(f.title, active = o.font == f) { onChange(o.copy(font = f)) } }
    }
}

@AndroidOptIn(UnstableApi::class)
@Composable
private fun StickerPanel(
    selected: VideoOverlay?,
    onAdd: (String) -> Unit,
    onChange: (VideoOverlay) -> Unit,
    onDelete: (Long) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        STICKERS.forEach { s ->
            Box(
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).glass(RoundedCornerShape(12.dp)).clickable { onAdd(s) },
                contentAlignment = Alignment.Center,
            ) { Text(s, style = TextStyle(fontSize = MaterialTheme.typography.titleMedium.fontSize * 1.3f, color = Palette.Lilac)) }
        }
    }
    if (selected != null) {
        LabeledSlider(t("Размер"), selected.size, 0.04f..0.3f, "") { v -> onChange(selected.copy(size = v)) }
        GhostButton(t("Удалить стикер"), onClick = { onDelete(selected.id) })
    }
}

@AndroidOptIn(UnstableApi::class)
@Composable
private fun MusicPanel(edit: VideoEdit, tracks: List<MusicTrack>, update: ((VideoEdit) -> VideoEdit) -> Unit, onLibrary: () -> Unit) {
    // Медиатека — открытая музыка Jamendo и Openverse прямо из редактора
    GlowButton(t("Медиатека"), onClick = onLibrary)
    if (tracks.isEmpty()) {
        Text(
            t("В «Моей музыке» пока нет треков — выберите трек в медиатеке или загрузите свои в профиле."),
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Mist,
        )
        return
    }
    Chip(t("Без музыки"), active = edit.musicTrackId == null) { update { it.copy(musicTrackId = null) } }
    tracks.forEach { track ->
        val active = track.id == edit.musicTrackId
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(14.dp), strong = active)
                .clickable { update { it.copy(musicTrackId = track.id, musicStartMs = 0) } }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(LineIcon.Music, if (active) Palette.Ink else Palette.Lilac, size = 18.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (track.artist.isNotBlank()) Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, maxLines = 1)
            }
            Text(durationTitle(track.durationMs), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
        }
    }
    val current = tracks.firstOrNull { it.id == edit.musicTrackId } ?: return
    LabeledSlider(
        t("Начало трека"), edit.musicStartMs.toFloat(), 0f..current.durationMs.coerceAtLeast(1).toFloat(),
        durationTitle(edit.musicStartMs),
    ) { v -> update { it.copy(musicStartMs = v.toLong()) } }
    LabeledSlider(t("Громкость музыки"), edit.musicVolume, 0f..1f, percent(edit.musicVolume)) { v -> update { it.copy(musicVolume = v) } }
}

@AndroidOptIn(UnstableApi::class)
@Composable
private fun CoverPanel(edit: VideoEdit, onChange: (Long) -> Unit) {
    val frame by produceState<ImageBitmap?>(null, edit.sourcePath, edit.coverMs / 250) {
        value = withContext(Dispatchers.IO) { VideoExporter.frame(edit.sourcePath, edit.coverMs, 360)?.asImageBitmap() }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(72.dp).height(128.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Plum)) {
            frame?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t("Кадр для обложки"), style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
            Text(t("Обложку видно в «Сохранённом» и в профиле. Фильтры и текст попадут на неё тоже."), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
        }
    }
    Slider(
        value = edit.coverMs.coerceIn(edit.trimStartMs, edit.trimEndMs).toFloat(),
        onValueChange = { onChange(it.toLong()) },
        valueRange = edit.trimStartMs.toFloat()..edit.trimEndMs.coerceAtLeast(edit.trimStartMs + 1).toFloat(),
        colors = sliderColors(),
    )
}

/** Ввод текста: поле, цвет, оформление и шрифт — с образцом сверху. */
@AndroidOptIn(UnstableApi::class)
@Composable
private fun TextEditorSheet(draft: VideoOverlay, onDone: (VideoOverlay) -> Unit, onCancel: () -> Unit) {
    var o by remember { mutableStateOf(draft) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Void.copy(alpha = 0.92f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .clickable(enabled = false) {},
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                if (o.text.isNotBlank()) OverlayText(o, MaterialTheme.typography.headlineSmall.fontSize)
            }
            GlassField(
                o.text, { o = o.copy(text = it.take(120)) }, t("Текст на видео"),
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)),
            )
            OverlayStyleControls(o) { o = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(t("Отмена"), onClick = onCancel)
                GlowButton(t("Готово"), enabled = o.text.isNotBlank(), onClick = { onDone(o) })
            }
        }
    }
}

@AndroidOptIn(UnstableApi::class)
@Composable
private fun ToolButton(icon: LineIcon, title: String, active: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .glass(RoundedCornerShape(50), strong = active)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(icon, Palette.Lilac, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, color = Palette.Ink)
    }
}

@AndroidOptIn(UnstableApi::class)
@Composable
fun RoundTool(icon: LineIcon, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape).glass(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(icon, Palette.Ink) }
}
