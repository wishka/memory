package ru.wishka.memory.ui.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.BADGE_GRADIENTS
import ru.wishka.memory.data.BadgeParts
import ru.wishka.memory.data.DrawStroke
import ru.wishka.memory.data.MediaFiles
import ru.wishka.memory.data.MediaKind
import ru.wishka.memory.data.PhotoParts
import ru.wishka.memory.data.STICKER_COLORS
import ru.wishka.memory.data.SmileEyes
import ru.wishka.memory.data.SmileExtra
import ru.wishka.memory.data.SmileMouth
import ru.wishka.memory.data.SmileyParts
import ru.wishka.memory.data.Sticker
import ru.wishka.memory.data.StickerArt
import ru.wishka.memory.data.StickerKind
import ru.wishka.memory.data.StickerShape
import ru.wishka.memory.data.plural
import ru.wishka.memory.i18n.t
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.Chip
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassCard
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.MemoryMenu
import ru.wishka.memory.ui.components.MenuRow
import ru.wishka.memory.ui.components.ScreenTitle
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.settings.MemorySwitch
import ru.wishka.memory.ui.theme.Palette
import java.io.File

/**
 * «Студия» в профиле: свои значки, смайлы и стикеры. Четыре способа —
 * из фото (обрезка формой, обводка, подпись), рисунок пальцем,
 * конструктор смайлов и значок с текстом на кристальной подложке.
 * Готовое — прозрачная PNG-картинка; её видно в витрине профиля и можно
 * отправить через «Поделиться».
 */
@Composable
fun StickerStudioScreen(vm: AppViewModel, state: UiState, contentPadding: PaddingValues) {
    var editor by remember { mutableStateOf<StickerKind?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(LineIcon.Back, Palette.Ink) { vm.closeProfileSection() }
                Spacer(Modifier.width(14.dp))
                ScreenTitle(t("Студия"), plural(state.stickers.size, "стикер", "стикера", "стикеров"), Modifier.weight(1f))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CreateTile(LineIcon.Image, t("Из фото"), t("Форма, обводка, подпись"), Modifier.weight(1f)) { editor = StickerKind.Photo }
                    CreateTile(LineIcon.Brush, t("Рисунок"), t("Кисти, цвета, ластик"), Modifier.weight(1f)) { editor = StickerKind.Drawing }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CreateTile(LineIcon.Smile, t("Смайл"), t("Лицо, глаза, рот, детали"), Modifier.weight(1f)) { editor = StickerKind.Smiley }
                    CreateTile(LineIcon.Award, t("Значок"), t("Текст или эмодзи на кристалле"), Modifier.weight(1f)) { editor = StickerKind.Badge }
                }
            }
        }
        item {
            Text(
                t("МОИ СТИКЕРЫ"),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Mist,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        if (state.stickers.isEmpty()) {
            item {
                Text(
                    t("Здесь появятся ваши значки, смайлы и стикеры. Выберите способ выше."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
        state.stickers.chunked(4).forEach { row ->
            item(key = row.first().id) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { sticker -> StickerTile(sticker, vm, Modifier.weight(1f)) }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    editor?.let { kind ->
        StickerEditor(kind, onClose = { editor = null }, onSaved = { sticker ->
            vm.addSticker(sticker)
            editor = null
        })
    }
}

@Composable
private fun CreateTile(icon: LineIcon, title: String, hint: String, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Palette.Orchid, Palette.Ocean))),
                contentAlignment = Alignment.Center,
            ) { Glyph(icon, Palette.Ink, size = 22.dp) }
            Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, maxLines = 2)
        }
    }
}

/** Картинка стикера из файла; читается не в главном потоке. */
@Composable
fun StickerImage(sticker: Sticker, modifier: Modifier = Modifier) {
    val image by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, sticker.path) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(sticker.path)?.asImageBitmap() }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        image?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerTile(sticker: Sticker, vm: AppViewModel, modifier: Modifier) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        StickerImage(
            sticker,
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .glass(RoundedCornerShape(18.dp))
                .combinedClickable(onClick = { menu = true }, onLongClick = { menu = true })
                .padding(8.dp),
        )
        MemoryMenu(expanded = menu, onDismiss = { menu = false }) {
            MenuRow(LineIcon.Share, t("Поделиться")) {
                menu = false
                val file = File(sticker.path)
                MediaFiles.share(context, "", listOf(Attachment(sticker.id, MediaKind.Photo, sticker.path, file.name, file.length(), "image/png")))
            }
            MenuRow(LineIcon.Trash, t("Удалить"), color = Palette.PearlPink) {
                menu = false
                File(sticker.path).delete()
                vm.deleteSticker(sticker.id)
            }
        }
    }
}

// ==================== РЕДАКТОР ====================

/** Редактор стикера во весь экран: предпросмотр сверху, инструменты снизу. */
@Composable
private fun StickerEditor(kind: StickerKind, onClose: () -> Unit, onSaved: (Sticker) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var photoParts by remember { mutableStateOf(PhotoParts()) }
    var strokes by remember { mutableStateOf<List<DrawStroke>>(emptyList()) }
    var current by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var brushColor by remember { mutableStateOf(STICKER_COLORS[4]) }
    var brushWidth by remember { mutableStateOf(0.03f) }
    var eraser by remember { mutableStateOf(false) }
    var smiley by remember { mutableStateOf(SmileyParts()) }
    var badge by remember { mutableStateOf(BadgeParts()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) {
            if (photo == null) onClose()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            photo = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readBytes()
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        var sample = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1024) sample *= 2
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    }
                }.getOrNull()
            }
            if (photo == null) {
                Toast.makeText(context, t("Не удалось открыть фото"), Toast.LENGTH_SHORT).show()
                onClose()
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(kind) {
        if (kind == StickerKind.Photo) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun render(): Bitmap? = when (kind) {
        StickerKind.Photo -> photo?.let { StickerArt.photo(it, photoParts) }
        StickerKind.Drawing -> StickerArt.drawing(strokes + listOfNotNull(
            current.takeIf { it.isNotEmpty() }?.let { DrawStroke(it, brushColor, brushWidth, eraser) },
        ))
        StickerKind.Smiley -> StickerArt.smiley(smiley)
        StickerKind.Badge -> StickerArt.badge(badge)
    }
    val preview = remember(kind, photo, photoParts, strokes, current, smiley, badge, brushColor, brushWidth, eraser) {
        render()?.asImageBitmap()
    }
    val empty = when (kind) {
        StickerKind.Photo -> photo == null
        StickerKind.Drawing -> strokes.none { !it.eraser }
        StickerKind.Badge -> badge.text.isBlank()
        StickerKind.Smiley -> false
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.Void)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(LineIcon.Close, Palette.Ink, onClick = onClose)
                Spacer(Modifier.width(12.dp))
                Text(t(kind.ru), style = MaterialTheme.typography.titleLarge, color = Palette.Ink, modifier = Modifier.weight(1f))
                GlowButton(t("Сохранить"), enabled = !empty, onClick = {
                    val bitmap = render() ?: return@GlowButton
                    val saved = StickerArt.save(context, bitmap, kind, System.currentTimeMillis())
                    if (saved != null) onSaved(saved)
                    else Toast.makeText(context, t("Не удалось сохранить"), Toast.LENGTH_SHORT).show()
                })
            }
            // Предпросмотр на «шахматке» — так видно прозрачные места
            Box(
                modifier = Modifier
                    .padding(horizontal = 32.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, Palette.Line, RoundedCornerShape(24.dp)),
            ) {
                Checker(Modifier.fillMaxSize())
                preview?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
                // Жесты: рисунок — линии пальцем, фото — сдвиг
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(kind, brushColor, brushWidth, eraser) {
                            if (kind == StickerKind.Drawing) {
                                detectDragGestures(
                                    onDragStart = { o -> current = listOf(o.x / size.width to o.y / size.height) },
                                    onDragEnd = {
                                        if (current.isNotEmpty()) strokes = strokes + DrawStroke(current, brushColor, brushWidth, eraser)
                                        current = emptyList()
                                    },
                                    onDrag = { change, _ ->
                                        current = current + (change.position.x / size.width to change.position.y / size.height)
                                    },
                                )
                            } else if (kind == StickerKind.Photo) {
                                detectDragGestures { _, drag ->
                                    photoParts = photoParts.copy(
                                        dx = StickerArt.clamp(photoParts.dx + drag.x / size.width, -1f, 1f),
                                        dy = StickerArt.clamp(photoParts.dy + drag.y / size.height, -1f, 1f),
                                    )
                                }
                            }
                        }
                        .pointerInput(kind, brushColor, brushWidth, eraser) {
                            if (kind == StickerKind.Drawing) {
                                detectTapGestures { o ->
                                    strokes = strokes + DrawStroke(listOf(o.x / size.width to o.y / size.height), brushColor, brushWidth, eraser)
                                }
                            }
                        },
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (kind) {
                    StickerKind.Photo -> PhotoControls(photoParts, { photoParts = it }) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    StickerKind.Drawing -> DrawControls(
                        color = brushColor, onColor = { brushColor = it; eraser = false },
                        width = brushWidth, onWidth = { brushWidth = it },
                        eraser = eraser, onEraser = { eraser = it },
                        canUndo = strokes.isNotEmpty(),
                        onUndo = { strokes = strokes.dropLast(1) },
                        onClear = { strokes = emptyList() },
                    )
                    StickerKind.Smiley -> SmileyControls(smiley) { smiley = it }
                    StickerKind.Badge -> BadgeControls(badge) { badge = it }
                }
            }
        }
    }
}

/** «Шахматка» под прозрачной картинкой. */
@Composable
private fun Checker(modifier: Modifier) {
    Canvas(modifier) {
        val cell = size.width / 16
        drawRect(Palette.Night)
        for (i in 0 until 16) for (j in 0 until 16) {
            if ((i + j) % 2 == 0) drawRect(Palette.Plum.copy(alpha = 0.55f), Offset(i * cell, j * cell), Size(cell, cell))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Mist)
}

@Composable
private fun ColorRow(colors: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { c ->
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(if (c == selected) 3.dp else 1.dp, if (c == selected) Palette.Ink else Palette.Line, CircleShape)
                    .clickable { onPick(c) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choices(items: List<T>, selected: T, title: (T) -> String, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item -> Chip(title(item), active = item == selected) { onPick(item) } }
    }
}

@Composable
private fun studioSlider() = SliderDefaults.colors(
    thumbColor = Palette.Ink,
    activeTrackColor = Palette.Lilac,
    inactiveTrackColor = Palette.Plum,
)

@Composable
private fun PhotoControls(p: PhotoParts, onChange: (PhotoParts) -> Unit, onPickAgain: () -> Unit) {
    Label(t("Форма"))
    Choices(StickerShape.entries, p.shape, { t(it.ru) }) { onChange(p.copy(shape = it)) }
    Label(t("Масштаб — сдвигайте фото пальцем"))
    Slider(value = p.zoom, onValueChange = { onChange(p.copy(zoom = it)) }, valueRange = 1f..3f, colors = studioSlider())
    Label(t("Обводка"))
    Choices(listOf(0, 1, 2, 3), p.outline, { listOf(t("Нет"), t("Тонкая"), t("Средняя"), t("Толстая"))[it] }) { onChange(p.copy(outline = it)) }
    if (p.outline > 0) ColorRow(STICKER_COLORS, p.outlineColor) { onChange(p.copy(outlineColor = it)) }
    Label(t("Подпись"))
    GlassField(
        p.caption, { onChange(p.copy(caption = it.take(24))) }, t("Например, «Ура!»"),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)),
    )
    if (p.caption.isNotBlank()) ColorRow(STICKER_COLORS, p.captionColor) { onChange(p.copy(captionColor = it)) }
    GhostButton(t("Другое фото"), onClick = onPickAgain)
}

@Composable
internal fun DrawControls(
    color: Int, onColor: (Int) -> Unit,
    width: Float, onWidth: (Float) -> Unit,
    eraser: Boolean, onEraser: (Boolean) -> Unit,
    canUndo: Boolean, onUndo: () -> Unit, onClear: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Chip(t("Кисть"), active = !eraser) { onEraser(false) }
        Chip(t("Ластик"), active = eraser) { onEraser(true) }
        Spacer(Modifier.weight(1f))
        RoundIconButton(LineIcon.Undo, if (canUndo) Palette.Lilac else Palette.Mist) { if (canUndo) onUndo() }
        RoundIconButton(LineIcon.Trash, if (canUndo) Palette.Lilac else Palette.Mist) { if (canUndo) onClear() }
    }
    Label(t("Цвет"))
    ColorRow(STICKER_COLORS, if (eraser) 0 else color, onColor)
    Label(t("Толщина"))
    Slider(value = width, onValueChange = onWidth, valueRange = 0.008f..0.09f, colors = studioSlider())
    Text(t("Рисуйте пальцем в рамке. Касание — точка."), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
}

@Composable
private fun SmileyControls(p: SmileyParts, onChange: (SmileyParts) -> Unit) {
    Label(t("Лицо"))
    Choices(StickerShape.entries, p.face, { t(it.ru) }) { onChange(p.copy(face = it)) }
    ColorRow(STICKER_COLORS.dropLast(1), p.color) { onChange(p.copy(color = it)) }
    Label(t("Глаза"))
    Choices(SmileEyes.entries, p.eyes, { t(it.ru) }) { onChange(p.copy(eyes = it)) }
    Label(t("Рот"))
    Choices(SmileMouth.entries, p.mouth, { t(it.ru) }) { onChange(p.copy(mouth = it)) }
    Label(t("Детали"))
    Choices(SmileExtra.entries, p.extra, { t(it.ru) }) { onChange(p.copy(extra = it)) }
}

@Composable
private fun BadgeControls(p: BadgeParts, onChange: (BadgeParts) -> Unit) {
    Label(t("Текст или эмодзи"))
    GlassField(
        p.text, { onChange(p.copy(text = it.take(8))) }, t("До 8 знаков: «MEM», «✦», «🔥»"),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)),
    )
    Label(t("Форма"))
    Choices(StickerShape.entries, p.shape, { t(it.ru) }) { onChange(p.copy(shape = it)) }
    Label(t("Подложка"))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BADGE_GRADIENTS.forEachIndexed { i, (a, b) ->
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(a), Color(b))))
                    .border(if (i == p.gradient) 3.dp else 1.dp, if (i == p.gradient) Palette.Ink else Palette.Line, CircleShape)
                    .clickable { onChange(p.copy(gradient = i)) },
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(t("Кристальное стекло"), style = MaterialTheme.typography.bodyLarge, color = Palette.Ink, modifier = Modifier.weight(1f))
        MemorySwitch(p.glass) { onChange(p.copy(glass = it)) }
    }
}
