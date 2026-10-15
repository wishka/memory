package ru.wishka.memory.ui.prism

import ru.wishka.memory.ui.components.label
import ru.wishka.memory.ui.FriendState
import ru.wishka.memory.ui.components.FriendButton
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.MediaFiles
import ru.wishka.memory.data.Prism
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.Avatar
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.LikeHeart
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.compact
import ru.wishka.memory.ui.theme.Palette

/**
 * Вкладка PRISM: короткие вертикальные ролики во весь экран, листаются
 * вверх-вниз. Нажатие — пауза, двойное нажатие — «нравится». Справа —
 * «нравится», обсуждение, «поделиться» и «сохранить». Играет только
 * ролик на экране: соседние стоят, чтобы не тратить батарею.
 */
@Composable
fun PrismScreen(vm: AppViewModel, state: UiState, contentPadding: PaddingValues) {
    // Закрытый профиль — только друзьям, как в ленте
    val prisms = state.prisms.filter { it.author.id !in state.blocked && state.canSee(it.author) }
    val context = LocalContext.current
    val pager = rememberPagerState(pageCount = { prisms.size })
    // Открыли из «Сохранённого» — сразу на нужный ролик
    LaunchedEffect(state.prismStartId) {
        val id = state.prismStartId ?: return@LaunchedEffect
        val index = prisms.indexOfFirst { it.id == id }
        if (index >= 0) pager.scrollToPage(index)
        vm.consumePrismStart()
    }
    var confirmDelete by remember { mutableStateOf<Prism?>(null) }
    // Вкладки сверху: ролики или медиатека
    var library by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
    if (library) {
        MediaLibraryScreen(vm, state, contentPadding)
    } else VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
        val prism = prisms[page]
        PrismPage(
            prism = prism,
            mine = prism.author.id == state.me.id && prism.video != null,
            onDelete = { confirmDelete = prism },
            active = pager.currentPage == page,
            friend = if (prism.author.id == state.me.id) null else state.friendState(prism.author.id),
            contentPadding = contentPadding,
            onLike = { vm.togglePrismLike(prism.id) },
            onAuthor = { vm.openPerson(prism.author.id) },
            onFriend = { vm.toggleFriend(prism.author.id) },
            onComments = { vm.openPrismComments(prism.id) },
            onShare = { sharePrism(context, prism) },
            onSave = { vm.togglePrismSave(prism.id) },
            onReport = if (prism.author.id == state.me.id) null else ({ vm.reportPrism(prism) }),
        )
    }

    // «PRISM · МЕДИАТЕКА» — вкладки поверх роликов
    Row(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(top = contentPadding.calculateTopPadding() + 14.dp, start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrismTabLabel("PRISM", active = !library) { library = false }
        PrismTabLabel(t("МЕДИАТЕКА"), active = library) { library = true }
    }

    // «+» — снять или выбрать свой ролик
    if (!library) Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = contentPadding.calculateTopPadding() + 22.dp, end = 12.dp)
            .size(44.dp)
            .clip(CircleShape)
            .glass(CircleShape, strong = true)
            .clickable { vm.openPrismCreator() },
        contentAlignment = Alignment.Center,
    ) { Glyph(LineIcon.Plus, Palette.Ink, size = 22.dp, description = LineIcon.Plus.label) }
    }

    confirmDelete?.let { prism ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = Palette.Night,
            title = { Text(t("Удалить ролик?"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink) },
            text = { Text(t("Ролик, его обложка и комментарии удалятся. Отменить будет нельзя."), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    vm.deletePrism(prism.id)
                    prism.video?.let { MediaFiles.delete(it) }
                    prism.cover?.let { MediaFiles.delete(it) }
                }) { Text(t("Удалить"), color = Palette.Lilac) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(t("Отмена"), color = Palette.Mist) } },
        )
    }
}

/** Вкладка сверху: подпись капслоком и светящаяся черта под выбранной. */
@Composable
private fun PrismTabLabel(text: String, active: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = androidx.compose.ui.semantics.Role.Tab, onClick = onClick)
            .semantics { selected = active }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = if (active) Palette.Ink else Palette.Mist)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .width(if (active) 24.dp else 0.dp)
                .height(2.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(Palette.Lilac, Palette.Sky))),
        )
    }
}

/** «Поделиться»: системное меню — мессенджеры, почта, копирование ссылки. */
private fun sharePrism(context: android.content.Context, prism: Prism) {
    val text = tf("%s\n— %s (@%s) в Memory · PRISM", prism.caption, prism.author.name, prism.author.handle)
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, t("Поделиться")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
private fun PrismPage(
    prism: Prism,
    mine: Boolean,
    onDelete: () -> Unit,
    active: Boolean,
    /** Дружба с автором; null — свой ролик, кнопки нет. */
    friend: FriendState?,
    contentPadding: PaddingValues,
    onLike: () -> Unit,
    onAuthor: () -> Unit,
    onFriend: () -> Unit,
    onComments: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onReport: (() -> Unit)? = null,
) {
    var paused by remember(prism.id) { mutableStateOf(false) }
    var time by remember(prism.id) { mutableLongStateOf(0L) }
    // Пролистали на другой ролик — этот начнётся сначала, когда вернётесь
    LaunchedEffect(active) { if (!active) { time = 0L; paused = false } }
    LaunchedEffect(active, paused) {
        // Своё видео ведёт время само (PrismVideo)
        if (!active || paused || prism.video != null) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            time = (time + (now - last)) % prism.durationMs
            last = now
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(prism.id) {
                detectTapGestures(
                    onTap = { paused = !paused },
                    onDoubleTap = { if (!prism.liked) onLike() },
                )
            },
    ) {
        val video = prism.video
        var videoLength by remember(prism.id) { mutableLongStateOf(prism.durationMs.toLong()) }
        if (video != null) {
            PrismVideo(video.path, playing = active && !paused, modifier = Modifier.fillMaxSize()) { position, length ->
                time = position
                videoLength = length
            }
        } else {
            PrismArt(prism.seed, time, Modifier.fillMaxSize())
        }

        // Затемнение снизу, чтобы подпись читалась на любом кадре
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Palette.Void.copy(alpha = 0.85f))),
        )

        // Полоса времени сверху
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = contentPadding.calculateTopPadding(), start = 16.dp, end = 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(Palette.Ink.copy(alpha = 0.25f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((time.toFloat() / (if (video != null) videoLength else prism.durationMs.toLong())).coerceIn(0f, 1f))
                        .height(2.dp)
                        .background(Brush.horizontalGradient(listOf(Palette.Lilac, Palette.Sky))),
                )
            }
        }

        if (paused) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Palette.Void.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) { Glyph(LineIcon.Play, Palette.Ink, size = 36.dp) }
        }

        // Кнопки справа
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = contentPadding.calculateBottomPadding()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SideButton(LineIcon.Heart, compact(prism.likes), onLike) {
                LikeHeart(prism.liked, Palette.Ink, size = 30.dp)
            }
            SideButton(LineIcon.Comment, compact(prism.comments), onComments)
            SideButton(LineIcon.Share, t("Поделиться"), onShare)
            SideButton(if (prism.saved) LineIcon.BookmarkFilled else LineIcon.Bookmark, if (prism.saved) t("В закладках") else t("Сохранить"), onSave)
            if (mine) SideButton(LineIcon.Trash, t("Удалить"), onDelete)
            if (onReport != null) SideButton(LineIcon.Flag, t("Пожаловаться"), onReport)
        }

        // Автор, подпись и музыка
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 96.dp, bottom = contentPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onAuthor),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(prism.author, size = 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(prism.author.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                }
                Spacer(Modifier.width(10.dp))
                if (friend != null) FriendButton(friend, prism.author.name, onFriend)
            }
            ru.wishka.memory.ui.components.RichText(prism.caption)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(LineIcon.Music, Palette.Lilac, size = 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    prism.music,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SideButton(icon: LineIcon, label: String, onClick: (() -> Unit)?, iconContent: (@Composable () -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick) else Modifier)
            // «Нравится, 1,2 тыс.», «Комментарии, 40», «Поделиться»
            .semantics(mergeDescendants = true) { contentDescription = if (icon.label == label) label else icon.label + ", " + label }
            .padding(4.dp),
    ) {
        if (iconContent != null) iconContent() else Glyph(icon, Palette.Ink, size = 30.dp)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Ink, modifier = Modifier.padding(top = 2.dp))
    }
}
