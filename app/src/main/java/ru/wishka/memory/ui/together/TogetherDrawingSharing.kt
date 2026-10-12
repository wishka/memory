package ru.wishka.memory.ui.together

import ru.wishka.memory.data.InviteAction
import ru.wishka.memory.data.Unlock
import ru.wishka.memory.ui.components.shareInvite
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.CollabDrawing
import ru.wishka.memory.data.DrawStroke
import ru.wishka.memory.data.MediaFiles
import ru.wishka.memory.data.MediaKind
import ru.wishka.memory.data.STICKER_COLORS
import ru.wishka.memory.data.ShareItem
import ru.wishka.memory.data.ShareKind
import ru.wishka.memory.data.ShareStatus
import ru.wishka.memory.data.StickerArt
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.AttachButton
import ru.wishka.memory.ui.components.Avatar
import ru.wishka.memory.ui.components.Chip
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.MediaTile
import ru.wishka.memory.ui.components.PendingAttachments
import ru.wishka.memory.ui.components.rememberMediaPicker
import ru.wishka.memory.ui.profile.DrawControls
import ru.wishka.memory.ui.profile.RoundIconButton
import ru.wishka.memory.ui.theme.Palette

// ==================== 7. Рисунок по эстафете ====================

/** Рисунок на тёмном фоне — для показа и публикации. */
internal fun drawingBitmap(strokes: List<DrawStroke>): Bitmap {
    val art = StickerArt.drawing(strokes)
    val out = Bitmap.createBitmap(art.width, art.height, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    c.drawColor(0xFF0F153A.toInt())
    c.drawBitmap(art, 0f, 0f, null)
    return out
}

private fun saveDrawing(context: Context, d: CollabDrawing): Attachment? = runCatching {
    val file = MediaFiles.newFile(context, "png")
    file.outputStream().use { drawingBitmap(d.strokes).compress(Bitmap.CompressFormat.PNG, 100, it) }
    Attachment(file.nameWithoutExtension, MediaKind.Photo, file.absolutePath, "drawing.png", file.length(), "image/png")
}.getOrNull()

@Composable
private fun DrawingPreview(strokes: List<DrawStroke>, modifier: Modifier = Modifier) {
    val image = remember(strokes) { drawingBitmap(strokes).asImageBitmap() }
    Image(image, contentDescription = null, modifier = modifier.aspectRatio(1f).clip(RoundedCornerShape(18.dp)))
}

@Composable
internal fun DrawingSection(vm: AppViewModel, state: UiState) {
    val context = LocalContext.current
    val me = state.me.id
    val drawings = state.together.drawings
    var drawingId by remember { mutableStateOf<Long?>(null) }
    var creating by remember { mutableStateOf(false) }
    Card {
        Hint(t("Один начинает рисунок и передаёт следующему, тот дорисовывает своё — и так по кругу. Получается общее произведение."))
        GlowButton(t("Начать рисунок"), onClick = { creating = true })
    }
    val myTurn = drawings.filter { it.waitingFor == me && !it.finished }
    if (myTurn.isNotEmpty()) Caption(t("Ваша очередь"))
    myTurn.forEach { d -> DrawingCard(state, d, action = t("Рисовать")) { drawingId = d.id } }
    val waiting = drawings.filter { it.waitingFor != null && it.waitingFor != me && !it.finished }
    if (waiting.isNotEmpty()) Caption(t("Рисуют другие"))
    waiting.forEach { d -> DrawingCard(state, d, action = null) {} }
    val done = drawings.filter { it.finished }
    if (done.isNotEmpty()) Caption(t("Готовые"))
    done.forEach { d ->
        DrawingCard(state, d, action = t("В ленту")) {
            saveDrawing(context, d)?.let { image ->
                vm.publish(tf("Нарисовали вместе: «%s» ✦", d.title), listOf(image))
                Toast.makeText(context, t("Опубликовано"), Toast.LENGTH_SHORT).show()
            }
        }
    }
    if (creating) {
        var title by remember { mutableStateOf("") }
        var turns by remember { mutableIntStateOf(4) }
        TogetherDialog(t("Новый рисунок"), onDismiss = { creating = false }) {
            Field(title, { title = it.take(40) }, t("Тема: «Город, которого нет»"))
            Caption(t("Сколько ходов"))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(3, 4, 5, 6).forEach { n -> Chip("$n", active = turns == n) { turns = n } } }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(t("Отмена"), onClick = { creating = false })
                GlowButton(t("Начать"), enabled = title.isNotBlank(), onClick = {
                    drawingId = vm.startDrawing(title, turns)
                    creating = false
                })
            }
        }
    }
    drawings.firstOrNull { it.id == drawingId && it.waitingFor == me }?.let { d ->
        DrawTurnEditor(vm, state, d) { drawingId = null }
    }
}

@Composable
private fun DrawingCard(state: UiState, d: CollabDrawing, action: String?, onAction: () -> Unit) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DrawingPreview(d.strokes, Modifier.size(96.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(d.title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                Text(tf("Ходов: %s из %s", d.turns.size, d.maxTurns), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                d.waitingFor?.let { if (it != state.me.id) Text(tf("Сейчас рисует: %s", personOf(state, it).name), style = MaterialTheme.typography.bodyMedium, color = Palette.Lilac) }
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    d.turns.map { it.authorId }.distinct().forEach { Avatar(personOf(state, it), size = 26.dp) }
                }
            }
        }
        if (action != null) GlowButton(action, onClick = onAction)
    }
}

/** Свой ход: видно, что нарисовали до вас; рисуете сверху и передаёте дальше. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DrawTurnEditor(vm: AppViewModel, state: UiState, d: CollabDrawing, onClose: () -> Unit) {
    val context = LocalContext.current
    var strokes by remember { mutableStateOf<List<DrawStroke>>(emptyList()) }
    var current by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var color by remember { mutableIntStateOf(STICKER_COLORS[4]) }
    var width by remember { mutableFloatStateOf(0.03f) }
    var eraser by remember { mutableStateOf(false) }
    val last = d.turns.size + 1 >= d.maxTurns
    val friends = (state.followedPeople + ru.wishka.memory.data.Demo.people).distinctBy { it.id }.filter { it.id !in state.blocked }
    var passTo by remember { mutableStateOf(friends.firstOrNull()?.id) }
    val preview = remember(strokes, current, color, width, eraser) {
        drawingBitmap(d.strokes + strokes + listOfNotNull(current.takeIf { it.isNotEmpty() }?.let { DrawStroke(it, color, width, eraser) })).asImageBitmap()
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Palette.Void).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(LineIcon.Close, Palette.Ink, onClick = onClose)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                    Text(tf("Ваш ход: %s из %s", d.turns.size + 1, d.maxTurns), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                }
            }
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .pointerInput(color, width, eraser) {
                        detectDragGestures(
                            onDragStart = { o -> current = listOf(o.x / size.width to o.y / size.height) },
                            onDragEnd = {
                                if (current.isNotEmpty()) strokes = strokes + DrawStroke(current, color, width, eraser)
                                current = emptyList()
                            },
                            onDrag = { change, _ -> current = current + (change.position.x / size.width to change.position.y / size.height) },
                        )
                    },
            ) { Image(preview, contentDescription = null, modifier = Modifier.fillMaxSize()) }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DrawControls(
                    color = color, onColor = { color = it; eraser = false },
                    width = width, onWidth = { width = it },
                    eraser = eraser, onEraser = { eraser = it },
                    canUndo = strokes.isNotEmpty(), onUndo = { strokes = strokes.dropLast(1) }, onClear = { strokes = emptyList() },
                )
                if (!last) {
                    Caption(t("Кому передать"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        friends.forEach { p -> Chip(p.name.substringBefore(' '), active = passTo == p.id) { passTo = p.id } }
                    }
                    GhostButton(t("Позвать того, кого нет в Memory"), onClick = { shareInvite(context, vm, InviteAction.Drawing, d.id.toString(), d.title) })
                } else {
                    Hint(t("Это последний ход — после него рисунок готов."))
                }
                GlowButton(if (last) t("Закончить рисунок") else t("Передать дальше"), enabled = strokes.isNotEmpty() && (last || passTo != null), onClick = {
                    vm.drawTurn(d.id, strokes, if (last) null else passTo)
                    onClose()
                })
            }
        }
    }
}

// ==================== 8. Обмен без денег ====================

@Composable
internal fun SharingSection(vm: AppViewModel, state: UiState) {
    val context = LocalContext.current
    var kind by remember { mutableStateOf(ShareKind.Book) }
    var adding by remember { mutableStateOf(false) }
    var handing by remember { mutableStateOf<ShareItem?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ShareKind.entries.forEach { k -> Chip("${k.emoji} ${k.title}", active = kind == k) { kind = k } }
    }
    Card {
        Hint(
            when (kind) {
                ShareKind.Book -> t("Книжный клуб с обменом: прочитали — передайте другому. Видно, у кого книга сейчас и где она уже побывала.")
                ShareKind.Seeds -> t("Семена, черенки, излишки урожая — садоводы делятся друг с другом.")
                ShareKind.Repair -> t("Вещи, которые можно починить, и люди, готовые помочь. Вторая жизнь вместо мусорки.")
            }
        )
        GlowButton(
            when (kind) {
                ShareKind.Book -> t("Поделиться книгой")
                ShareKind.Seeds -> t("Поделиться семенами")
                ShareKind.Repair -> t("Нужна помощь с ремонтом")
            },
            onClick = { adding = true },
        )
    }
    // Хранитель книг: ваши книги — первыми в обмене
    val keeper = state.unlocked(Unlock.Keeper)
    if (kind == ShareKind.Book) {
        val k = state.unlocks.first { it.unlock == Unlock.Keeper }
        Hint(if (k.open) "📚 " + t("Вы хранитель книг — ваши книги стоят первыми") else "📚 " + k.unlock.how + " · " + tf("осталось %s", k.left))
    }
    state.together.shares.filter { it.kind == kind && it.ownerId !in state.blocked }
        .sortedByDescending { keeper && it.ownerId == state.me.id }.forEach { item -> ShareCard(vm, state, item) { handing = item } }
    if (adding) {
        var title by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var photo by remember { mutableStateOf<List<Attachment>>(emptyList()) }
        val picker = rememberMediaPicker { picked -> photo = picked.filter { it.kind == MediaKind.Photo }.take(1) }
        TogetherDialog("${kind.emoji} ${kind.title}", onDismiss = { adding = false }) {
            Field(title, { title = it.take(80) }, when (kind) {
                ShareKind.Book -> t("Книга и автор")
                ShareKind.Seeds -> t("Что отдаёте: «Семена базилика»")
                ShareKind.Repair -> t("Что сломалось: «Торшер»")
            })
            Field(description, { description = it.take(500) }, t("Пара слов"), singleLine = false)
            PendingAttachments(photo, onRemove = { photo = emptyList(); MediaFiles.delete(it) })
            AttachButton(picker, documents = false, size = 40.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(t("Отмена"), onClick = { adding = false })
                GlowButton(t("Опубликовать"), enabled = title.isNotBlank(), onClick = {
                    if (vm.addShare(kind, title, description, photo.firstOrNull())) adding = false
                })
            }
        }
    }
    handing?.let { item ->
        val people = (state.followedPeople + ru.wishka.memory.data.Demo.people).distinctBy { it.id }.filter { it.id !in state.blocked }
        TogetherDialog(tf("Кому передать «%s»?", item.title), onDismiss = { handing = null }) {
            people.forEach { p ->
                PersonLine(p, "", onClick = { vm.handOver(item.id, p.id); handing = null })
            }
            GhostButton(t("Тому, кого нет в Memory"), onClick = { shareInvite(context, vm, InviteAction.Book, item.id.toString(), item.title) })
            GhostButton(t("Отмена"), onClick = { handing = null })
        }
    }
}

@Composable
private fun ShareCard(vm: AppViewModel, state: UiState, item: ShareItem, onHand: () -> Unit) {
    val me = state.me.id
    val owner = personOf(state, item.ownerId)
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.photo != null) MediaTile(item.photo, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)), maxSide = 256, onClick = { vm.openMedia(item.photo) })
            else EmojiBadge(item.kind.emoji, 56)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                Text(tf("Делится: %s", owner.name), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
            }
        }
        if (item.description.isNotEmpty()) Text(item.description, style = MaterialTheme.typography.bodyMedium, color = Palette.Ink)
        when (item.kind) {
            ShareKind.Book -> {
                Text(tf("Сейчас у: %s", personOf(state, item.holderId).name), style = MaterialTheme.typography.labelLarge, color = Palette.Lilac)
                if (item.history.isNotEmpty()) {
                    Text(
                        tf("Побывала у: %s", (item.history).joinToString(" → ") { personOf(state, it).name.substringBefore(' ') }),
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Mist,
                    )
                }
            }
            ShareKind.Repair -> if (item.helpers.isNotEmpty()) {
                Text(tf("Готовы помочь: %s", item.helpers.joinToString(", ") { personOf(state, it).name.substringBefore(' ') }), style = MaterialTheme.typography.bodyMedium, color = Palette.Lilac)
            }
            ShareKind.Seeds -> Unit
        }
        val requester = item.requestedBy?.let { personOf(state, it) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                item.kind == ShareKind.Repair && item.status == ShareStatus.Fixed -> Text(t("Починили ✓"), style = MaterialTheme.typography.titleMedium, color = Palette.Sky)
                item.kind == ShareKind.Repair && item.ownerId == me -> GlowButton(t("Починили"), onClick = { vm.markFixed(item.id) })
                item.kind == ShareKind.Repair -> if (me in item.helpers) GhostButton(t("Вы помогаете ✓"), onClick = {}) else GlowButton(t("Помогу починить"), onClick = { vm.offerHelp(item.id) })
                item.status == ShareStatus.Given -> Text(if (item.holderId == me) t("Теперь у вас ✓") else t("Уже забрали"), style = MaterialTheme.typography.titleMedium, color = Palette.Sky)
                item.holderId == me && requester != null -> {
                    GlowButton(tf("Отдать: %s", requester.name.substringBefore(' ')), onClick = { vm.handOver(item.id, requester.id) })
                }
                item.holderId == me -> GlowButton(if (item.kind == ShareKind.Book) t("Передать дальше") else t("Отдать"), onClick = onHand)
                item.status == ShareStatus.Requested && item.requestedBy == me -> GhostButton(t("Вы попросили — ждём"), onClick = {})
                item.status == ShareStatus.Requested -> GhostButton(t("Уже попросили"), onClick = {})
                else -> GlowButton(if (item.kind == ShareKind.Book) t("Взять почитать") else t("Забрать"), onClick = { vm.requestShare(item.id) })
            }
            if (item.holderId != me) GhostButton(t("Написать"), onClick = { vm.writeTo(item.holderId) })
        }
    }
}
