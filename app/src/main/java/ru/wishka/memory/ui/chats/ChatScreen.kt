package ru.wishka.memory.ui.chats

import ru.wishka.memory.i18n.t
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.wishka.memory.ui.components.RecordingBar
import ru.wishka.memory.ui.components.StickerImage
import ru.wishka.memory.ui.components.StickerPicker
import ru.wishka.memory.ui.components.BackdropChooser
import ru.wishka.memory.ui.components.BackdropView
import ru.wishka.memory.ui.components.PlaceBubble
import ru.wishka.memory.ui.components.PlaceViewer
import ru.wishka.memory.ui.components.PollCard
import ru.wishka.memory.ui.components.PollEditorDialog
import ru.wishka.memory.ui.components.rememberLocate
import ru.wishka.memory.data.Poll
import ru.wishka.memory.data.SharedPlace
import ru.wishka.memory.data.backdropOr
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import ru.wishka.memory.ui.components.VoiceBubble
import ru.wishka.memory.ui.components.VoiceRecorder
import ru.wishka.memory.ui.ProfileSection
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.Chat
import ru.wishka.memory.data.MediaFiles
import ru.wishka.memory.data.MediaKind
import ru.wishka.memory.data.Message
import ru.wishka.memory.data.ReplyRef
import ru.wishka.memory.ui.components.MemoryMenu
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.messagePreview
import ru.wishka.memory.ui.components.AttachButton
import ru.wishka.memory.ui.components.Avatar
import ru.wishka.memory.ui.components.DocumentRow
import ru.wishka.memory.ui.components.FadeLine
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.MediaTile
import ru.wishka.memory.ui.components.MenuRow
import ru.wishka.memory.ui.components.PendingAttachments
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.components.rememberMediaPicker
import ru.wishka.memory.ui.theme.Palette

/**
 * Переписка: шапка с собеседником, сообщения и поле ввода над клавиатурой.
 * Скрепка — фото, видео (из галереи или с камеры) и документы. Долгое
 * нажатие на сообщение — ответить, переслать, закрепить или удалить у
 * себя или у всех. Закреплённое сообщение — полоской под шапкой.
 * Пустое поле — вместо «отправить» микрофон: голосовое записывается,
 * его можно удалить или отправить. В скрепке — свои стикеры из «Студии».
 */
@Composable
fun ChatScreen(vm: AppViewModel, chat: Chat, chats: List<Chat>, blocked: Boolean = false) {
    val context = LocalContext.current
    var text by rememberSaveable(chat.id) { mutableStateOf("") }
    var pending by remember(chat.id) { mutableStateOf<List<Attachment>>(emptyList()) }
    val picker = rememberMediaPicker { picked -> pending = (pending + picked).take(AppViewModel.MAX_MEDIA) }
    val listState = rememberLazyListState()
    // Новое сообщение — прокрутка вниз (список перевёрнут: низ — это 0)
    LaunchedEffect(chat.messages.size, chat.typing) { listState.animateScrollToItem(0) }
    val canSend = text.isNotBlank() || pending.isNotEmpty()
    // На какое сообщение отвечаем; null — обычное сообщение
    var replyTo by remember(chat.id) { mutableStateOf<ReplyRef?>(null) }
    // Какое сообщение пересылаем: выбор переписки
    var forwarding by remember(chat.id) { mutableStateOf<Message?>(null) }
    val scope = rememberCoroutineScope()
    val reversed = chat.messages.asReversed()
    val ui = vm.state.collectAsState().value
    var choosingSticker by remember { mutableStateOf(false) }
    var choosingGif by remember { mutableStateOf(false) }
    var editingPoll by remember { mutableStateOf(false) }
    var headerMenu by remember { mutableStateOf(false) }
    var choosingBackdrop by remember { mutableStateOf(false) }
    var viewingPlace by remember { mutableStateOf<SharedPlace?>(null) }
    val locate = rememberLocate()

    // Голосовое: запись, время и волна, пока пишем
    val recorder = remember(chat.id) { VoiceRecorder(context.applicationContext) }
    var recording by remember(chat.id) { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var levels by remember { mutableStateOf<List<Int>>(emptyList()) }
    DisposableEffect(recorder) { onDispose { recorder.cancel() } }
    fun startRecording() {
        if (recorder.start()) {
            levels = emptyList()
            elapsed = 0
            recording = true
        } else {
            Toast.makeText(context, t("Микрофон недоступен"), Toast.LENGTH_SHORT).show()
        }
    }
    fun sendVoice() {
        recording = false
        val voice = recorder.finish()
        if (voice != null) {
            vm.send(chat.id, "", listOf(voice), replyTo)
            replyTo = null
        } else {
            Toast.makeText(context, t("Слишком короткое — держите запись хотя бы секунду"), Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(recording) {
        while (recording) {
            val level = recorder.sample()
            levels = levels + level
            elapsed = recorder.elapsedMs
            // Пять минут — предел: отправляем, что записалось
            if (elapsed >= VoiceRecorder.MAX_MS) { sendVoice(); break }
            delay(100)
        }
    }
    // Заблокировали собеседника посреди записи — запись отменяется
    LaunchedEffect(blocked) {
        if (blocked && recording) { recording = false; recorder.cancel() }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
        else Toast.makeText(context, t("Разрешите доступ к микрофону, чтобы записывать голосовые"), Toast.LENGTH_LONG).show()
    }

    // Фон переписки: свой у чата или общий из настроек
    BackdropView(chat.backdropOr(ui.backdrop), Modifier.fillMaxSize())
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable { vm.closeChat() },
                contentAlignment = Alignment.Center,
            ) { Glyph(LineIcon.Back, Palette.Ink) }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { vm.openPerson(chat.peer.id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(chat.peer, size = 40.dp, showOnline = true)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(chat.peer.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                    Text(
                        when {
                            chat.typing -> t("печатает…")
                            chat.peer.online -> t("в сети")
                            else -> "@${chat.peer.handle}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (chat.typing || chat.peer.online) Palette.Lilac else Palette.Mist,
                    )
                }
            }
            Box {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).clickable { headerMenu = true },
                    contentAlignment = Alignment.Center,
                ) { Glyph(LineIcon.More, Palette.Ink) }
                MemoryMenu(expanded = headerMenu, onDismiss = { headerMenu = false }) {
                    MenuRow(LineIcon.Image, t("Фон переписки")) { headerMenu = false; choosingBackdrop = true }
                }
            }
        }
        FadeLine()

        chat.pinned?.let { pin ->
            PinnedBar(
                pin,
                onOpen = {
                    val index = reversed.indexOfFirst { it.id == pin.id }
                    if (index >= 0) scope.launch { listState.animateScrollToItem(index + if (chat.typing) 1 else 0) }
                },
                onUnpin = { vm.unpin(chat.id) },
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (chat.typing) item(key = "typing") { TypingBubble() }
            items(reversed, key = { it.id }) { message ->
                Bubble(
                    message,
                    peerName = chat.peer.name.substringBefore(' '),
                    onReply = { replyTo = vm.replyRef(chat.id, message) },
                    onForward = { forwarding = message },
                    pinned = chat.pinnedId == message.id,
                    onPin = { vm.togglePin(chat.id, message.id) },
                    onQuote = { id ->
                        // Нажатие на цитату — к исходному сообщению
                        val index = reversed.indexOfFirst { it.id == id }
                        if (index >= 0) scope.launch { listState.animateScrollToItem(index + if (chat.typing) 1 else 0) }
                    },
                    onOpen = { att ->
                        if (att.kind == MediaKind.Document) {
                            if (!MediaFiles.open(context, att)) {
                                Toast.makeText(context, t("Нет приложения, чтобы открыть файл"), Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            vm.openMedia(att)
                        }
                    },
                    onDelete = { forEveryone -> vm.deleteMessage(chat.id, message.id, forEveryone) },
                    onVote = { poll, i -> vm.voteMessage(chat.id, poll.id, i) },
                    onClosePoll = { poll -> vm.closeMessagePoll(chat.id, poll.id) },
                    onPlace = { viewingPlace = it },
                    onReport = { vm.reportMessage(chat.id, message) },
                )
            }
        }

        replyTo?.let { ref ->
            ReplyBar(ref, onCancel = { replyTo = null })
        }

        PendingAttachments(
            pending,
            onRemove = { item ->
                pending = pending - item
                MediaFiles.delete(item)
            },
            modifier = Modifier.padding(top = 8.dp),
        )

        // Заблокированному не пишем: вместо поля — напоминание и кнопка
        if (recording && !blocked) {
            RecordingBar(
                elapsed, levels,
                onCancel = { recording = false; recorder.cancel() },
                onSend = ::sendVoice,
            )
        } else if (blocked) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .glass(RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tf("Вы заблокировали %s", chat.peer.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                    modifier = Modifier.weight(1f),
                )
                GhostButton(t("Разблокировать"), onClick = { vm.unblock(chat.peer.id) })
            }
        } else Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AttachButton(
                picker, documents = true, size = 50.dp,
                onSticker = { choosingSticker = true },
                onGif = { choosingGif = true },
                onPoll = { editingPoll = true },
                onLocation = {
                    locate.locate { at -> if (at != null) { vm.sendPlace(chat.id, at); replyTo = null } }
                },
            )
            GlassField(
                text, { text = it.take(2000) }, t("Сообщение"),
                maxLines = 5,
                modifier = Modifier
                    .weight(1f)
                    .glass(RoundedCornerShape(24.dp)),
            )
            // Есть что отправить — «отправить»; пусто — микрофон для голосового
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(
                        if (!canSend) Brush.linearGradient(listOf(Palette.Violet, Palette.Plum))
                        else Brush.linearGradient(listOf(Palette.Orchid, Palette.Ocean))
                    )
                    .clickable {
                        if (canSend) {
                            vm.send(chat.id, text, pending, replyTo)
                            text = ""
                            pending = emptyList()
                            replyTo = null
                        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            startRecording()
                        } else {
                            micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Glyph(if (canSend) LineIcon.Send else LineIcon.Mic, Palette.Ink) }
        }
    }

    if (choosingSticker || choosingGif) {
        StickerPicker(
            ui.stickers,
            onPick = { sticker -> vm.send(chat.id, "", listOf(sticker), replyTo); replyTo = null },
            onDismiss = { choosingSticker = false; choosingGif = false },
            onOpenStudio = { vm.closeChat(); vm.openProfileSection(ProfileSection.Stickers) },
            packs = ui.stickerPacks,
            recentGifs = ui.recentGifs,
            gifs = true,
            startOnGifs = choosingGif,
        )
    }
    if (editingPoll) {
        PollEditorDialog(onDismiss = { editingPoll = false }, onCreate = { draft ->
            vm.send(chat.id, "", replyTo = replyTo, poll = draft)
            replyTo = null
        })
    }
    if (choosingBackdrop) {
        AlertDialog(
            onDismissRequest = { choosingBackdrop = false },
            containerColor = Palette.Night,
            title = { Text(t("Фон переписки"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink) },
            text = {
                BackdropChooser(
                    chat.backdropOr(ui.backdrop),
                    onPick = { vm.setChatBackdrop(chat.id, it) },
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    onReset = if (chat.backdrop != null) ({ vm.setChatBackdrop(chat.id, null) }) else null,
                )
            },
            confirmButton = { TextButton(onClick = { choosingBackdrop = false }) { Text(t("Готово"), color = Palette.Lilac) } },
        )
    }
    viewingPlace?.let { place -> PlaceViewer(place, onClose = { viewingPlace = null }) }

    forwarding?.let { message ->
        ForwardDialog(
            chats = chats,
            onPick = { target ->
                forwarding = null
                vm.forward(chat.id, message.id, target.id)
            },
            onDismiss = { forwarding = null },
        )
    }
}

/** Закреплённое сообщение под шапкой: нажатие — к нему, крестик — открепить. */
@Composable
private fun PinnedBar(message: Message, onOpen: () -> Unit, onUnpin: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(LineIcon.Pushpin, Palette.Lilac, size = 20.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(t("Закреплённое сообщение"), style = MaterialTheme.typography.labelLarge, color = Palette.Lilac, maxLines = 1)
            Text(messagePreview(message), style = MaterialTheme.typography.bodyMedium, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onUnpin),
            contentAlignment = Alignment.Center,
        ) { Glyph(LineIcon.Close, Palette.Mist, size = 18.dp) }
    }
    FadeLine()
}

/** Полоска «Ответ …» над полем ввода; крестик — передумать. */
@Composable
private fun ReplyBar(ref: ReplyRef, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp)
            .glass(RoundedCornerShape(16.dp))
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(LineIcon.Reply, Palette.Lilac, size = 20.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(tf("Ответ: %s", ref.author), style = MaterialTheme.typography.labelLarge, color = Palette.Lilac, maxLines = 1)
            Text(ref.preview, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) { Glyph(LineIcon.Close, Palette.Mist, size = 18.dp) }
    }
}

/** Куда переслать: список переписок. */
@Composable
private fun ForwardDialog(chats: List<Chat>, onPick: (Chat) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Night,
        title = { Text(t("Переслать"), style = MaterialTheme.typography.headlineSmall, color = Palette.Ink) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                items(chats, key = { it.id }) { c ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(c) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(c.peer, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(c.peer.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("Отмена"), color = Palette.Lilac) }
        },
    )
}

/**
 * Сообщение: пометка «Переслано», цитата ответа, вложения и текст.
 * Долгое нажатие — меню: ответить, переслать, закрепить, удалить «у
 * себя» (у собеседника останется) или «у всех».
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    message: Message,
    peerName: String,
    onReply: () -> Unit,
    onForward: () -> Unit,
    pinned: Boolean,
    onPin: () -> Unit,
    onQuote: (Long) -> Unit,
    onOpen: (Attachment) -> Unit,
    onDelete: (Boolean) -> Unit,
    onVote: (Poll, Int) -> Unit,
    onClosePoll: (Poll) -> Unit,
    onPlace: (SharedPlace) -> Unit,
    onReport: () -> Unit,
) {
    val mine = message.fromMe
    var menu by remember { mutableStateOf(false) }
    // Только стикер — без пузыря, картинкой
    val stickerOnly = message.text.isEmpty() && message.attachments.isNotEmpty() && message.attachments.all { it.sticker } &&
        message.replyTo == null && message.forwardedFrom == null
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        val shape = RoundedCornerShape(
            topStart = 20.dp, topEnd = 20.dp,
            bottomStart = if (mine) 20.dp else 6.dp,
            bottomEnd = if (mine) 6.dp else 20.dp,
        )
        Box {
            if (stickerOnly) Column(
                modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { menu = true }),
                horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
            ) {
                message.attachments.forEach { StickerImage(it, size = 150.dp) }
                Text(message.time, style = MaterialTheme.typography.labelSmall, color = Palette.Mist)
            } else Column(
                modifier = Modifier
                    .widthIn(max = 290.dp)
                    .then(
                        if (mine) Modifier
                            .clip(shape)
                            .background(Brush.linearGradient(listOf(Palette.Violet, Palette.Ocean)))
                        else Modifier.glass(shape)
                    )
                    .combinedClickable(onClick = {}, onLongClick = { menu = true })
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                message.forwardedFrom?.let { from ->
                    Row(
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph(LineIcon.Forward, Palette.Lilac, size = 14.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(tf("Переслано от %s", from), style = MaterialTheme.typography.labelLarge, color = Palette.Lilac, maxLines = 1)
                    }
                }
                message.replyTo?.let { ref ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Palette.Void.copy(alpha = 0.3f))
                            .clickable { onQuote(ref.messageId) }
                            .padding(end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(3.dp).height(38.dp).background(Brush.verticalGradient(listOf(Palette.Lilac, Palette.Sky))))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(ref.author, style = MaterialTheme.typography.labelLarge, color = Palette.Lilac, maxLines = 1)
                            Text(ref.preview, style = MaterialTheme.typography.bodyMedium, color = Palette.Ink.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                val visual = message.attachments.filter { it.kind != MediaKind.Document }
                val documents = message.attachments.filter { it.kind == MediaKind.Document }
                val stickers = visual.filter { it.sticker }
                if (visual.size > stickers.size) {
                    MediaGrid(visual - stickers.toSet(), onOpen)
                }
                stickers.forEach { StickerImage(it, size = 120.dp) }
                documents.forEach { doc ->
                    if (doc.voice) VoiceBubble(doc)
                    else DocumentRow(doc, Modifier.fillMaxWidth(), onClick = { onOpen(doc) })
                }
                message.poll?.let { poll ->
                    PollCard(poll, onVote = { onVote(poll, it) }, modifier = Modifier.width(270.dp), onClose = if (mine) ({ onClosePoll(poll) }) else null)
                }
                message.place?.let { place -> PlaceBubble(place) { onPlace(place) } }
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
                    if (message.text.isNotEmpty()) {
                        Text(message.text, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
                    }
                    Text(
                        message.time,
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.Ink.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
            MemoryMenu(expanded = menu, onDismiss = { menu = false }) {
                MenuRow(LineIcon.Reply, t("Ответить")) {
                    menu = false
                    onReply()
                }
                MenuRow(LineIcon.Forward, t("Переслать")) {
                    menu = false
                    onForward()
                }
                MenuRow(LineIcon.Pushpin, if (pinned) t("Открепить") else t("Закрепить")) {
                    menu = false
                    onPin()
                }
                MenuRow(LineIcon.Trash, t("Удалить у себя")) {
                    menu = false
                    onDelete(false)
                }
                MenuRow(LineIcon.Trash, t("Удалить у всех"), color = Palette.Lilac) {
                    menu = false
                    onDelete(true)
                }
                if (!mine) MenuRow(LineIcon.Flag, t("Пожаловаться"), color = Palette.PearlPink) {
                    menu = false
                    onReport()
                }
            }
        }
    }
}

/** Фото и видео в сообщении: одно — крупно, несколько — сеткой по два. */
@Composable
private fun MediaGrid(items: List<Attachment>, onOpen: (Attachment) -> Unit) {
    if (items.size == 1) {
        MediaTile(
            items[0],
            Modifier.width(260.dp).height(200.dp).clip(RoundedCornerShape(16.dp)),
            onClick = { onOpen(items[0]) },
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { item ->
                    MediaTile(
                        item,
                        Modifier.size(128.dp).clip(RoundedCornerShape(12.dp)),
                        maxSide = 384,
                        onClick = { onOpen(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(
        modifier = Modifier
            .glass(RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(3) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Palette.Lilac.copy(alpha = 0.5f + it * 0.2f)),
            )
        }
    }
}
