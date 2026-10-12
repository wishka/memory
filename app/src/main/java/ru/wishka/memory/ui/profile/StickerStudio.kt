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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.delay
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
import ru.wishka.memory.data.StickerAnim
import ru.wishka.memory.data.StickerEffect
import ru.wishka.memory.data.StickerFx
import ru.wishka.memory.data.StickerPack
import ru.wishka.memory.data.SubjectCutout
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
import ru.wishka.memory.ui.components.LiveFrames
import ru.wishka.memory.ui.components.LiveSticker
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
 * Плюс вырезка объекта с фото (как в iOS 17). Эффекты — контур, комикс,
 * объём, блеск; движение — прыжок, пульс, мерцание…; рисунок бывает
 * покадровым. Стикеры собираются в коллекции. Готовое — прозрачные
 * PNG-картинки; их видно в витрине профиля и можно отправить.
 */
@Composable
fun StickerStudioScreen(vm: AppViewModel, state: UiState, contentPadding: PaddingValues) {
    var editor by remember { mutableStateOf<StickerKind?>(null) }
    // Открытая коллекция; "" — все стикеры
    var packId by remember { mutableStateOf("") }
    var naming by remember { mutableStateOf<StickerPack?>(null) }
    var creating by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<Sticker?>(null) }
    val pack = state.stickerPacks.firstOrNull { it.id == packId }
    val shown = if (pack == null) state.stickers else state.stickers.filter { it.packId == pack.id }
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
                    CreateTile(LineIcon.Scissors, t("Вырезка"), t("Объект с фото — как на iPhone"), Modifier.weight(1f)) { editor = StickerKind.Cutout }
                    CreateTile(LineIcon.Image, t("Из фото"), t("Форма, обводка, подпись"), Modifier.weight(1f)) { editor = StickerKind.Photo }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CreateTile(LineIcon.Brush, t("Рисунок"), t("Кисти, кадры, ластик"), Modifier.weight(1f)) { editor = StickerKind.Drawing }
                    CreateTile(LineIcon.Smile, t("Смайл"), t("Лицо, глаза, рот, детали"), Modifier.weight(1f)) { editor = StickerKind.Smiley }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CreateTile(LineIcon.Award, t("Значок"), t("Текст или эмодзи на кристалле"), Modifier.weight(1f)) { editor = StickerKind.Badge }
                    CreateTile(LineIcon.Grid, t("Коллекция"), t("Соберите свой набор стикеров"), Modifier.weight(1f)) { creating = true }
                }
            }
        }
        if (state.stickerPacks.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("КОЛЛЕКЦИИ"), style = MaterialTheme.typography.labelSmall, color = Palette.Mist, modifier = Modifier.padding(horizontal = 24.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Chip(t("Все"), active = pack == null) { packId = "" }
                        state.stickerPacks.forEach { p ->
                            val count = state.stickers.count { it.packId == p.id }
                            Chip("${p.name} · $count", active = p.id == pack?.id) { packId = p.id }
                        }
                        Chip("+ " + t("Новая"), active = false) { creating = true }
                    }
                    if (pack != null) {
                        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GhostButton(t("Переименовать"), onClick = { naming = pack })
                            GhostButton(t("Удалить коллекцию"), onClick = { vm.deleteStickerPack(pack.id); packId = "" })
                        }
                    }
                }
            }
        }
        item {
            Text(
                pack?.name?.uppercase() ?: t("МОИ СТИКЕРЫ"),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Mist,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    if (pack == null) t("Здесь появятся ваши значки, смайлы и стикеры. Выберите способ выше.")
                    else t("В коллекции пока пусто. Новый стикер, сделанный здесь, попадёт сюда; готовый — через его меню «В коллекцию…»."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
        shown.chunked(4).forEach { row ->
            item(key = row.first().id) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { sticker -> StickerTile(sticker, vm, Modifier.weight(1f), hasPacks = state.stickerPacks.isNotEmpty()) { moving = sticker } }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    editor?.let { kind ->
        StickerEditor(kind, pack?.id.orEmpty(), onClose = { editor = null }, onSaved = { sticker ->
            vm.addSticker(sticker)
            editor = null
        })
    }

    if (creating || naming != null) {
        var name by remember(naming) { mutableStateOf(naming?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { creating = false; naming = null },
            containerColor = Palette.Night,
            title = { Text(if (naming != null) t("Название коллекции") else t("Новая коллекция"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink) },
            text = {
                GlassField(name, { name = it.take(40) }, t("Например, «Котики»"), singleLine = true,
                    modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)))
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val editing = naming
                    if (editing != null) vm.renameStickerPack(editing.id, name)
                    else {
                        val created = vm.createStickerPack(name)
                        if (created != null) {
                            packId = created.id
                            moving?.let { vm.moveSticker(it.id, created.id) }
                        }
                    }
                    creating = false
                    naming = null
                    moving = null
                }) { Text(t("Готово"), color = Palette.Lilac) }
            },
            dismissButton = { TextButton(onClick = { creating = false; naming = null }) { Text(t("Отмена"), color = Palette.Mist) } },
        )
    } else moving?.let { sticker ->
        AlertDialog(
            onDismissRequest = { moving = null },
            containerColor = Palette.Night,
            title = { Text(t("В коллекцию"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (listOf(StickerPack("", t("Без коллекции"))) + state.stickerPacks).forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.moveSticker(sticker.id, p.id); moving = null }.padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink, modifier = Modifier.weight(1f))
                            if (sticker.packId == p.id) Glyph(LineIcon.Check, Palette.Lilac, size = 20.dp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { creating = true }) { Text(t("Новая коллекция"), color = Palette.Lilac) } },
            dismissButton = { TextButton(onClick = { moving = null }) { Text(t("Отмена"), color = Palette.Mist) } },
        )
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

/** Картинка стикера из файла; живой — двигается и листает кадры. */
@Composable
fun StickerImage(sticker: Sticker, modifier: Modifier = Modifier) {
    if (sticker.animated) {
        LiveSticker(sticker.path, sticker.frames, sticker.fps, sticker.animation, modifier)
        return
    }
    val image by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, sticker.path) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(sticker.path)?.asImageBitmap() }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        image?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerTile(sticker: Sticker, vm: AppViewModel, modifier: Modifier, hasPacks: Boolean, onMove: () -> Unit) {
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
            MenuRow(LineIcon.Grid, if (hasPacks) t("В коллекцию…") else t("Новая коллекция…")) {
                menu = false
                onMove()
            }
            MenuRow(LineIcon.Share, t("Поделиться")) {
                menu = false
                val file = File(sticker.path)
                MediaFiles.share(context, "", listOf(Attachment(sticker.id, MediaKind.Photo, sticker.path, file.name, file.length(), "image/png")))
            }
            MenuRow(LineIcon.Trash, t("Удалить"), color = Palette.PearlPink) {
                menu = false
                (sticker.frames.ifEmpty { listOf(sticker.path) }).forEach { File(it).delete() }
                vm.deleteSticker(sticker.id)
            }
        }
    }
}

// ==================== РЕДАКТОР ====================

/**
 * Редактор стикера во весь экран: предпросмотр сверху, инструменты снизу.
 * Общие для всех способов — эффект (запекается в картинку) и движение.
 * Рисунок может быть покадровым: прошлый кадр просвечивает «калькой».
 */
@Composable
private fun StickerEditor(initial: StickerKind, packId: String, onClose: () -> Unit, onSaved: (Sticker) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(initial) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var photoParts by remember { mutableStateOf(PhotoParts()) }
    var cut by remember { mutableStateOf<Bitmap?>(null) }
    var cutting by remember { mutableStateOf(false) }
    var frames by remember { mutableStateOf(listOf<List<DrawStroke>>(emptyList())) }
    var frameIx by remember { mutableStateOf(0) }
    var fps by remember { mutableStateOf(6) }
    var playing by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var brushColor by remember { mutableStateOf(STICKER_COLORS[4]) }
    var brushWidth by remember { mutableStateOf(0.03f) }
    var eraser by remember { mutableStateOf(false) }
    var smiley by remember { mutableStateOf(SmileyParts()) }
    var badge by remember { mutableStateOf(BadgeParts()) }
    var effect by remember { mutableStateOf(if (initial == StickerKind.Cutout) StickerEffect.Outline else StickerEffect.None) }
    var anim by remember { mutableStateOf(StickerAnim.None) }
    var saving by remember { mutableStateOf(false) }
    val strokes = frames.getOrElse(frameIx) { emptyList() }
    fun setStrokes(list: List<DrawStroke>) {
        frames = frames.toMutableList().also { it[frameIx] = list }
    }
    // Жесты живут дольше одной перерисовки — им нужны линии кадра на момент касания
    fun latestStrokes(): List<DrawStroke> = frames.getOrElse(frameIx) { emptyList() }

    suspend fun cutOut(source: Bitmap) {
        cutting = true
        val result = SubjectCutout.cut(source)
        cutting = false
        if (result != null) cut = result
        else {
            Toast.makeText(context, t("Объект не нашёлся — обрежем фото формой"), Toast.LENGTH_LONG).show()
            kind = StickerKind.Photo
            if (effect == StickerEffect.Outline) effect = StickerEffect.None
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) {
            if (photo == null) onClose()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            photo = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input -> MediaFiles.decodeUpright(input.readBytes(), 1024) }
                }.getOrNull()
            }
            val loaded = photo
            if (loaded == null) {
                Toast.makeText(context, t("Не удалось открыть фото"), Toast.LENGTH_SHORT).show()
                onClose()
            } else if (kind == StickerKind.Cutout) {
                cut = null
                cutOut(loaded)
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(initial) {
        if (initial == StickerKind.Photo || initial == StickerKind.Cutout) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun base(frame: List<DrawStroke> = strokes, live: Boolean = false): Bitmap? = when (kind) {
        StickerKind.Photo -> photo?.let { StickerArt.photo(it, photoParts) }
        StickerKind.Cutout -> cut
        StickerKind.Drawing -> StickerArt.drawing(frame + listOfNotNull(
            current.takeIf { live && it.isNotEmpty() }?.let { DrawStroke(it, brushColor, brushWidth, eraser) },
        ))
        StickerKind.Smiley -> StickerArt.smiley(smiley)
        StickerKind.Badge -> StickerArt.badge(badge)
    }
    fun finished(frame: List<DrawStroke> = strokes): Bitmap? = base(frame)?.let { StickerFx.apply(it, effect) }

    // Пока палец рисует — быстрый рисунок без эффекта прямо здесь; остальное
    // (эффект, фото, значок) — не в главном потоке, с короткой паузой на ползунки
    val drawing = kind == StickerKind.Drawing && (current.isNotEmpty() || effect == StickerEffect.None)
    val live = if (drawing) remember(strokes, current, brushColor, brushWidth, eraser) { base(live = true)?.asImageBitmap() } else null
    val settled by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, kind, photo, photoParts, cut, strokes, smiley, badge, effect) {
        delay(30)
        value = withContext(Dispatchers.Default) { finished()?.asImageBitmap() }
    }
    val preview = live ?: settled
    val onion = remember(kind, frames, frameIx) {
        if (kind == StickerKind.Drawing && frameIx > 0) StickerArt.drawing(frames[frameIx - 1]).asImageBitmap() else null
    }
    val drawnFrames = frames.filter { f -> f.any { !it.eraser } }
    val liveImages by androidx.compose.runtime.produceState<List<androidx.compose.ui.graphics.ImageBitmap>>(emptyList(), playing, frames, effect, kind, photoParts, cut, smiley, badge) {
        value = if (!playing) emptyList() else withContext(Dispatchers.Default) {
            val list = if (kind == StickerKind.Drawing) drawnFrames.ifEmpty { listOf(strokes) } else listOf(strokes)
            list.mapNotNull { finished(it)?.asImageBitmap() }
        }
    }
    val empty = when (kind) {
        StickerKind.Photo -> photo == null
        StickerKind.Cutout -> cut == null
        StickerKind.Drawing -> drawnFrames.isEmpty()
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
                GlowButton(t("Сохранить"), enabled = !empty && !saving && !cutting, onClick = {
                    saving = true
                    scope.launch {
                        val bitmaps = withContext(Dispatchers.Default) {
                            val list = if (kind == StickerKind.Drawing) drawnFrames else listOf(strokes)
                            list.mapNotNull { finished(it) }
                        }
                        val saved = withContext(Dispatchers.IO) {
                            StickerArt.saveAnimated(context, bitmaps, kind, System.currentTimeMillis(), anim, fps, packId)
                        }
                        saving = false
                        if (saved != null) onSaved(saved)
                        else Toast.makeText(context, t("Не удалось сохранить"), Toast.LENGTH_SHORT).show()
                    }
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
                contentAlignment = Alignment.Center,
            ) {
                Checker(Modifier.fillMaxSize())
                if (playing && liveImages.isNotEmpty()) {
                    LiveFrames(liveImages, fps, anim.id, Modifier.fillMaxSize())
                } else {
                    onion?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), alpha = 0.28f) }
                    preview?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
                }
                if (cutting) {
                    Text(
                        t("Вырезаю объект…"),
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.Ink,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Palette.Night.copy(alpha = 0.8f)).padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
                // Жесты: рисунок — линии пальцем, фото — сдвиг
                if (!playing) Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(kind, brushColor, brushWidth, eraser, frameIx) {
                            if (kind == StickerKind.Drawing) {
                                detectDragGestures(
                                    onDragStart = { o -> current = listOf(o.x / size.width to o.y / size.height) },
                                    onDragEnd = {
                                        if (current.isNotEmpty()) setStrokes(latestStrokes() + DrawStroke(current, brushColor, brushWidth, eraser))
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
                        .pointerInput(kind, brushColor, brushWidth, eraser, frameIx) {
                            if (kind == StickerKind.Drawing) {
                                detectTapGestures { o ->
                                    setStrokes(latestStrokes() + DrawStroke(listOf(o.x / size.width to o.y / size.height), brushColor, brushWidth, eraser))
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
                    StickerKind.Cutout -> {
                        Text(
                            t("Объект с фото вырезается сам — человек, питомец, предмет. Добавьте контур или блеск и движение ниже."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.Mist,
                        )
                        GhostButton(t("Другое фото"), onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                    }
                    StickerKind.Drawing -> {
                        FrameControls(
                            count = frames.size, index = frameIx, fps = fps,
                            onSelect = { frameIx = it; playing = false },
                            onAdd = { frames = frames + listOf(emptyList()); frameIx = frames.lastIndex; playing = false },
                            onDuplicate = { frames = frames.take(frameIx + 1) + listOf(strokes) + frames.drop(frameIx + 1); frameIx += 1; playing = false },
                            onDelete = {
                                if (frames.size > 1) {
                                    frames = frames.filterIndexed { i, _ -> i != frameIx }
                                    frameIx = frameIx.coerceAtMost(frames.lastIndex)
                                } else frames = listOf(emptyList())
                            },
                            onFps = { fps = it },
                        )
                        DrawControls(
                            color = brushColor, onColor = { brushColor = it; eraser = false },
                            width = brushWidth, onWidth = { brushWidth = it },
                            eraser = eraser, onEraser = { eraser = it },
                            canUndo = strokes.isNotEmpty(),
                            onUndo = { setStrokes(strokes.dropLast(1)) },
                            onClear = { setStrokes(emptyList()) },
                        )
                    }
                    StickerKind.Smiley -> SmileyControls(smiley) { smiley = it }
                    StickerKind.Badge -> BadgeControls(badge) { badge = it }
                }
                LiveControls(
                    effect = effect, onEffect = { effect = it },
                    anim = anim, onAnim = { anim = it; playing = it != StickerAnim.None || frames.size > 1 },
                    canPlay = anim != StickerAnim.None || (kind == StickerKind.Drawing && drawnFrames.size > 1),
                    playing = playing, onPlaying = { playing = it },
                )
            }
        }
    }
}

/** Кадры покадрового рисунка: выбор, новый, копия, удалить и скорость. */
@Composable
private fun FrameControls(
    count: Int, index: Int, fps: Int,
    onSelect: (Int) -> Unit, onAdd: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit, onFps: (Int) -> Unit,
) {
    Label(t("Кадры"))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { i -> Chip("${i + 1}", active = i == index) { onSelect(i) } }
        if (count < 12) {
            Chip("+ " + t("Кадр"), active = false, onClick = onAdd)
            Chip(t("Копия"), active = false, onClick = onDuplicate)
        }
        Chip(t("Удалить кадр"), active = false, onClick = onDelete)
    }
    if (count > 1) {
        Label(t("Скорость"))
        Choices(listOf(4, 6, 8, 12), fps, { t("%d к/с").format(it) }, onFps)
    } else {
        Text(t("Добавьте кадры — и рисунок оживёт. Прошлый кадр просвечивает, чтобы следующий лёг точно."), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
    }
}

/** Эффект (запекается в картинку) и движение (играет при показе). */
@Composable
private fun LiveControls(
    effect: StickerEffect, onEffect: (StickerEffect) -> Unit,
    anim: StickerAnim, onAnim: (StickerAnim) -> Unit,
    canPlay: Boolean, playing: Boolean, onPlaying: (Boolean) -> Unit,
) {
    Label(t("Эффект"))
    Choices(StickerEffect.entries, effect, { it.title }, onEffect)
    Label(t("Движение"))
    Choices(StickerAnim.entries, anim, { it.title }, onAnim)
    if (canPlay) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(t("Смотреть"), active = playing) { onPlaying(true) }
            Chip(t("Править"), active = !playing) { onPlaying(false) }
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
