package ru.wishka.memory.ui.stories

import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.Demo
import ru.wishka.memory.data.Highlight
import ru.wishka.memory.data.MediaFiles
import ru.wishka.memory.data.MediaKind
import ru.wishka.memory.data.Stories
import ru.wishka.memory.data.Story
import ru.wishka.memory.data.User
import ru.wishka.memory.data.agoTitle
import ru.wishka.memory.data.minutesSince
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.Avatar
import ru.wishka.memory.ui.components.Chip
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.MemoryMenu
import ru.wishka.memory.ui.components.MenuRow
import ru.wishka.memory.ui.components.Nebula
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.components.label
import ru.wishka.memory.ui.components.rememberMediaPicker
import ru.wishka.memory.ui.components.rememberThumbnail
import ru.wishka.memory.ui.live.LiveBadge
import ru.wishka.memory.ui.theme.Palette

/** Градиент текстовой истории. */
fun storyBrush(bg: Int): Brush {
    val (a, b) = Stories.BACKGROUNDS[Math.floorMod(bg, Stories.BACKGROUNDS.size)]
    return Brush.linearGradient(listOf(Color(a), Color(b)))
}

/** Кольцо вокруг аватара: новые — сиренево-синее, «для близких» — зелёное, просмотренные — тонкое серое. */
private fun ringBrush(seen: Boolean, close: Boolean): Brush = when {
    seen -> Brush.linearGradient(listOf(Palette.Mist.copy(alpha = 0.5f), Palette.Mist.copy(alpha = 0.5f)))
    close -> Brush.sweepGradient(listOf(Color(0xFF3FCF8E), Color(0xFF1F7A6E), Color(0xFF3FCF8E)))
    else -> Brush.sweepGradient(listOf(Palette.Lilac, Palette.Sky, Palette.Orchid, Palette.Lilac))
}

/** Сама история: фото, видео, демо-картинка или градиент — и текст поверх. */
@Composable
fun StoryArt(story: Story, modifier: Modifier = Modifier, playVideo: Boolean = false, textSize: Int = 26) {
    Box(modifier.background(storyBrush(story.bg)), contentAlignment = Alignment.Center) {
        val media = story.media
        when {
            media != null && media.kind == MediaKind.Video && playVideo -> AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoURI(Uri.fromFile(java.io.File(media.path)))
                        setOnPreparedListener { it.isLooping = true; start() }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            media != null -> rememberThumbnail(media, maxSide = 1600)?.let {
                Image(it, contentDescription = Stories.preview(story), modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            story.seed != 0 -> Nebula(story.seed, Modifier.fillMaxSize())
        }
        if (story.text.isNotEmpty()) {
            Text(
                story.text,
                color = Palette.OnMedia,
                fontSize = textSize.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

/** Маленькая обложка истории — для архива и «Актуального». */
@Composable
fun StoryThumb(story: Story, size: Dp, modifier: Modifier = Modifier) {
    StoryArt(story, modifier.size(size).clip(CircleShape), textSize = 9)
}

/**
 * Ряд историй над записями друзей: своя (с «+»), эфир, эфиры друзей и
 * кружки историй друзей — непросмотренные первыми.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StoriesRow(vm: AppViewModel, state: UiState) {
    val now = System.currentTimeMillis()
    val mine = state.myActiveStories(now)
    val circles = state.storyCircles(now)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "me") {
            val seen = mine.all { it.id in state.seenStories }
            RingItem(
                state.me, t("Ваша история"),
                ring = if (mine.isEmpty()) null else ringBrush(seen, false),
                plus = true,
                description = if (mine.isEmpty()) t("Новая история") else t("Ваша история"),
                onClick = { if (mine.isEmpty()) vm.openStoryComposer() else vm.openStories(state.me.id) },
                onLongClick = { vm.openStoryComposer() },
            )
        }
        item(key = "live") { ru.wishka.memory.ui.live.LiveStartCircle(vm, state) }
        items(state.visibleLives, key = { "live" + it.id }) { live -> ru.wishka.memory.ui.live.LiveCircle(vm, state, live) }
        items(circles, key = { "st" + it.user.id }) { c ->
            RingItem(
                c.user, c.user.name.substringBefore(' '),
                ring = ringBrush(c.seen, c.close),
                description = tf("История: %s", c.user.name) + if (c.seen) "" else ", " + t("новая"),
                onClick = { vm.openStories(c.user.id) },
                onLongClick = { vm.openPerson(c.user.id) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RingItem(
    user: User,
    title: String,
    ring: Brush?,
    description: String,
    plus: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onLongClick = onLongClick, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(vertical = 4.dp),
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                Modifier
                    .then(if (ring != null) Modifier.border(2.dp, ring, CircleShape) else Modifier)
                    .padding(4.dp),
            ) { Avatar(user, size = 54.dp) }
            if (plus) {
                Box(
                    Modifier.size(20.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Palette.Orchid, Palette.Ocean))),
                    contentAlignment = Alignment.Center,
                ) { Text("+", color = Palette.Ink, fontSize = 14.sp) }
            }
        }
        Text(title, style = MaterialTheme.typography.labelLarge, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

/**
 * Просмотр историй во весь экран. Касание справа — дальше, слева — назад,
 * удержание — пауза. Чужая — ответ в личку и сердечко; своя — кто
 * посмотрел, удалить, добавить в «Актуальное».
 */
@Composable
fun StoryViewerScreen(vm: AppViewModel, state: UiState) {
    val v = state.storyViewer ?: return
    val list = vm.storiesOf(v.author)
    val story = list.getOrNull(v.index) ?: return
    val mine = story.authorId == state.me.id
    val author = if (mine) state.me else Demo.person(story.authorId) ?: return
    var paused by remember { mutableStateOf(false) }
    var progress by remember(story.id) { mutableFloatStateOf(0f) }
    var reply by remember(story.id) { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var addingTo by remember { mutableStateOf(false) }
    val typing = reply.isNotEmpty()
    BackHandler { vm.closeStories() }

    // Таймер: 5 секунд на историю (видео — 15), пауза — при удержании, наборе ответа и в меню
    val total = if (story.media?.kind == MediaKind.Video) 15_000L else Stories.SHOW_MS
    LaunchedEffect(story.id, paused, typing, menu, addingTo, state.storyViewersOf) {
        if (paused || typing || menu || addingTo || state.storyViewersOf != null) return@LaunchedEffect
        while (progress < 1f) {
            delay(50)
            progress = (progress + 50f / total).coerceAtMost(1f)
        }
        vm.nextStory()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        StoryArt(story, Modifier.fillMaxSize(), playVideo = true)
        // Зоны касания: левая треть — назад, остальное — дальше; удержание — пауза
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier.weight(1f).fillMaxHeight().pointerInput(story.id) {
                    detectTapGestures(onPress = { paused = true; tryAwaitRelease(); paused = false }, onTap = { vm.previousStory() })
                },
            )
            Box(
                Modifier.weight(2f).fillMaxHeight().pointerInput(story.id) {
                    detectTapGestures(onPress = { paused = true; tryAwaitRelease(); paused = false }, onTap = { vm.nextStory() })
                },
            )
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            // Полоски: сколько историй и сколько прошло у текущей
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                list.forEachIndexed { i, _ ->
                    val f = when {
                        i < v.index -> 1f
                        i == v.index -> progress
                        else -> 0f
                    }
                    Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.3f))) {
                        Box(Modifier.fillMaxWidth(f).height(3.dp).background(Color.White))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(author, size = 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (v.highlightId != null) state.highlights.firstOrNull { it.id == v.highlightId }?.title.orEmpty() else author.name,
                        style = MaterialTheme.typography.titleMedium, color = Palette.OnMedia, maxLines = 1,
                    )
                    Text(
                        agoTitle(minutesSince(story.createdAt, 0)) + if (story.closeFriends) " · " + t("для близких") else "",
                        style = MaterialTheme.typography.bodyMedium, color = Palette.OnMedia.copy(alpha = 0.75f),
                    )
                }
                if (mine) {
                    Box {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button) { menu = true },
                            contentAlignment = Alignment.Center,
                        ) { Glyph(LineIcon.More, Palette.OnMedia, description = LineIcon.More.label) }
                        MemoryMenu(expanded = menu, onDismiss = { menu = false }) {
                            MenuRow(LineIcon.Plus, t("В «Актуальное»")) { menu = false; addingTo = true }
                            MenuRow(LineIcon.Trash, t("Удалить историю"), color = Palette.PearlPink) {
                                menu = false
                                // Файл удаляем, только если историю не держит «Актуальное» — его и удалим
                                story.media?.let { MediaFiles.delete(it) }
                                vm.deleteStory(story.id)
                            }
                        }
                    }
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button) { vm.closeStories() },
                    contentAlignment = Alignment.Center,
                ) { Glyph(LineIcon.Close, Palette.OnMedia, description = LineIcon.Close.label) }
            }
            Spacer(Modifier.weight(1f))
            if (mine) {
                Row(
                    Modifier
                        .padding(16.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(role = Role.Button) { vm.openStoryViewers(story.id) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(LineIcon.Eye, Palette.OnMedia, size = 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        tf("Просмотры: %s", story.views.size) + if (story.likes > 0) " · ♥ ${story.likes}" else "",
                        style = MaterialTheme.typography.labelLarge, color = Palette.OnMedia,
                    )
                }
            } else {
                val liked = story.views.any { it.userId == state.me.id && it.liked }
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassField(
                        reply, { reply = it.take(1000) }, tf("Ответить %s…", author.name.substringBefore(' ')),
                        singleLine = true,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(Color.Black.copy(alpha = 0.35f)).border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
                    )
                    Spacer(Modifier.width(8.dp))
                    if (reply.isNotBlank()) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) {
                                vm.replyToStory(story.id, reply)
                                reply = ""
                            },
                            contentAlignment = Alignment.Center,
                        ) { Glyph(LineIcon.Send, Palette.OnMedia, description = LineIcon.Send.label) }
                    } else {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { vm.likeStory(story.id) },
                            contentAlignment = Alignment.Center,
                        ) { Glyph(if (liked) LineIcon.HeartFilled else LineIcon.Heart, if (liked) Palette.PearlPink else Palette.OnMedia, description = t("Сердечко")) }
                    }
                }
            }
        }
    }
    state.storyViewersOf?.let { id -> state.story(id)?.let { StoryViewersSheet(vm, it) } }
    if (addingTo) AddToHighlightDialog(vm, state, story) { addingTo = false }
}

/** Кто посмотрел мою историю — новые сверху, с сердечком у тех, кому понравилось. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoryViewersSheet(vm: AppViewModel, story: Story) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = vm::closeStoryViewers, sheetState = sheet, containerColor = Palette.Night, contentColor = Palette.Ink) {
        Text(
            tf("Просмотры: %s", story.views.size).uppercase(),
            style = MaterialTheme.typography.labelSmall, color = Palette.Mist,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        if (story.views.isEmpty()) {
            Text(t("Пока никто не посмотрел."), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, modifier = Modifier.padding(20.dp))
        }
        LazyColumn(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            items(story.views.sortedByDescending { it.at }, key = { it.userId }) { view ->
                val person = Demo.person(view.userId) ?: return@items
                Row(
                    Modifier.fillMaxWidth().clickable { vm.closeStories(); vm.openPerson(person.id) }.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(person, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(person.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                        Text(agoTitle(minutesSince(view.at, 0)), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                    }
                    if (view.liked) Glyph(LineIcon.HeartFilled, Palette.PearlPink, size = 20.dp, description = t("Сердечко"))
                }
            }
        }
    }
}

/**
 * Новая история: фото или видео из галереи или с камеры либо текст на
 * градиенте; кому видна — всем друзьям или «Близким друзьям».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoryComposerScreen(vm: AppViewModel, state: UiState) {
    var media by remember { mutableStateOf<Attachment?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    var bg by rememberSaveable { mutableStateOf(0) }
    var close by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editingClose by remember { mutableStateOf(false) }
    val picker = rememberMediaPicker { picked ->
        // В историю — одно фото или видео; лишнее удаляем
        picked.drop(1).forEach { MediaFiles.delete(it) }
        media?.let { MediaFiles.delete(it) }
        media = picked.firstOrNull { it.kind != MediaKind.Document }
    }
    BackHandler { media?.let { MediaFiles.delete(it) }; vm.closeStoryComposer() }
    val draft = Story(0, state.me.id, 0, media, text, bg)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        StoryArt(draft, Modifier.fillMaxSize(), playVideo = false)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
                        .clickable(role = Role.Button) { media?.let { MediaFiles.delete(it) }; vm.closeStoryComposer() },
                    contentAlignment = Alignment.Center,
                ) { Glyph(LineIcon.Close, Palette.OnMedia, description = LineIcon.Close.label) }
                Spacer(Modifier.weight(1f))
                Text(t("Новая история"), style = MaterialTheme.typography.titleMedium, color = Palette.OnMedia)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
                        .clickable(role = Role.Button) { picker.pickMedia() },
                    contentAlignment = Alignment.Center,
                ) { Glyph(LineIcon.Image, Palette.OnMedia, description = t("Фото или видео")) }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
                        .clickable(role = Role.Button) { picker.takePhoto() },
                    contentAlignment = Alignment.Center,
                ) { Glyph(LineIcon.Camera, Palette.OnMedia, description = LineIcon.Camera.label) }
            }
            Spacer(Modifier.weight(1f))
            if (media == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    Stories.BACKGROUNDS.indices.forEach { i ->
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(storyBrush(i))
                                .border(if (i == bg) 2.dp else 0.dp, Color.White, CircleShape)
                                .clickable(role = Role.RadioButton) { bg = i }
                                .semantics { contentDescription = tf("Фон %s", i + 1) },
                        )
                    }
                }
            }
            GlassField(
                text, { text = it.take(Stories.MAX_TEXT); error = null }, if (media == null) t("Напишите что-нибудь") else t("Подпись"),
                maxLines = 4,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.35f)),
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(t("Все друзья"), active = !close) { close = false }
                Chip(tf("Близкие друзья · %s", state.closeFriends.size), active = close) { close = true }
                Chip(t("Изменить список"), active = false) { editingClose = true }
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.PearlPink, modifier = Modifier.padding(top = 6.dp)) }
            Spacer(Modifier.height(10.dp))
            GlowButton(t("Опубликовать"), modifier = Modifier.fillMaxWidth(), onClick = {
                error = vm.publishStory(media, text, bg, close)
                if (error == null) media = null
            })
        }
    }
    if (editingClose) CloseFriendsDialog(vm, state) { editingClose = false }
}

/** «Близкие друзья»: кому видны истории «для близких». Список знаете только вы. */
@Composable
fun CloseFriendsDialog(vm: AppViewModel, state: UiState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Night,
        title = { Text(t("Близкие друзья"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    t("Им видны истории «для близких». Никто не узнает, что он в списке или нет."),
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Mist,
                )
                if (state.friends.isEmpty()) Text(t("Сначала добавьте друзей."), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                state.friends.forEach { f ->
                    val on = f.id in state.closeFriends
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Checkbox) { vm.toggleCloseFriend(f.id) }
                            .semantics { contentDescription = f.name + ", " + if (on) t("в списке") else t("не в списке") }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(f, size = 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(f.name, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink, modifier = Modifier.weight(1f))
                        if (on) Glyph(LineIcon.Check, Color(0xFF3FCF8E), size = 22.dp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Готово"), color = Palette.Lilac) } },
    )
}

/**
 * «Актуальное» в профиле: подборки историй кружками. У себя — «Новое»
 * первым, долгое нажатие на подборку — переименовать или удалить.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HighlightsRow(vm: AppViewModel, state: UiState, ownerId: Int) {
    val mine = ownerId == state.me.id
    val list = state.highlightsOf(ownerId)
    if (!mine && list.isEmpty()) return
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Highlight?>(null) }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (mine) item(key = "new") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(68.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { creating = true }.padding(vertical = 4.dp),
            ) {
                Box(Modifier.size(58.dp).clip(CircleShape).glass(CircleShape), contentAlignment = Alignment.Center) {
                    Glyph(LineIcon.Plus, Palette.Lilac, size = 24.dp)
                }
                Text(t("Новое"), style = MaterialTheme.typography.labelLarge, color = Palette.Ink, modifier = Modifier.padding(top = 4.dp))
            }
        }
        items(list, key = { it.id }) { h ->
            val cover = state.story(h.coverId) ?: h.storyIds.firstNotNullOfOrNull { state.story(it) }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(68.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .combinedClickable(onLongClick = if (mine) ({ editing = h }) else null, onClick = { vm.openHighlight(h.id) })
                    .semantics(mergeDescendants = true) { contentDescription = tf("Актуальное: %s", h.title) }
                    .padding(vertical = 4.dp),
            ) {
                Box(Modifier.border(1.5.dp, Palette.Line, CircleShape).padding(3.dp)) {
                    if (cover != null) StoryThumb(cover, 54.dp) else Box(Modifier.size(54.dp).clip(CircleShape).glass(CircleShape))
                }
                Text(h.title, style = MaterialTheme.typography.labelLarge, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
    if (creating) HighlightEditor(vm, state, null) { creating = false }
    editing?.let { h -> HighlightEditor(vm, state, h) { editing = null } }
}

/** Новая подборка (или правка своей): название и истории из архива. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HighlightEditor(vm: AppViewModel, state: UiState, existing: Highlight?, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var picked by remember { mutableStateOf(existing?.storyIds.orEmpty()) }
    val archive = state.storyArchive
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Night,
        title = { Text(if (existing == null) t("Новое «Актуальное»") else t("Изменить «Актуальное»"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassField(
                    title, { title = it.take(Stories.MAX_TITLE) }, t("Название"),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)),
                )
                if (archive.isEmpty()) {
                    Text(t("Архив пуст: выложите историю — через сутки она останется здесь."), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                } else {
                    Text(t("Выберите истории из архива"), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        archive.forEach { st ->
                            val on = st.id in picked
                            Box(
                                Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                                    .border(if (on) 2.dp else 0.dp, Palette.Lilac, RoundedCornerShape(12.dp))
                                    .clickable(role = Role.Checkbox) { picked = if (on) picked - st.id else picked + st.id }
                                    .semantics { contentDescription = Stories.preview(st) + ", " + if (on) t("выбрано") else t("не выбрано") },
                            ) {
                                StoryArt(st, Modifier.fillMaxSize(), textSize = 8)
                                if (on) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).clip(CircleShape).background(Palette.Lilac), contentAlignment = Alignment.Center) {
                                    Glyph(LineIcon.Check, Palette.Night, size = 14.dp)
                                }
                            }
                        }
                    }
                }
                if (existing != null) {
                    Text(
                        t("Удалить «Актуальное»"),
                        style = MaterialTheme.typography.bodyMedium, color = Palette.PearlPink,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { vm.deleteHighlight(existing.id); onDismiss() },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = picked.isNotEmpty(), onClick = {
                if (existing == null) vm.createHighlight(title, picked)
                else {
                    vm.renameHighlight(existing.id, title)
                    (picked - existing.storyIds.toSet()).forEach { vm.addToHighlight(existing.id, it) }
                    (existing.storyIds - picked.toSet()).forEach { vm.removeFromHighlight(existing.id, it) }
                }
                onDismiss()
            }) { Text(t("Сохранить"), color = if (picked.isNotEmpty()) Palette.Lilac else Palette.Mist) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Отмена"), color = Palette.Mist) } },
    )
}

/** Добавить историю в подборку — в существующую или новую. */
@Composable
private fun AddToHighlightDialog(vm: AppViewModel, state: UiState, story: Story, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    val mine = state.highlightsOf(state.me.id)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Night,
        title = { Text(t("В «Актуальное»"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                mine.forEach { h ->
                    val inside = story.id in h.storyIds
                    Text(
                        (if (inside) "✓ " else "") + h.title,
                        style = MaterialTheme.typography.bodyLarge, color = if (inside) Palette.Mist else Palette.Ink,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !inside) { vm.addToHighlight(h.id, story.id); onDismiss() }
                            .padding(vertical = 10.dp),
                    )
                }
                GlassField(title, { title = it.take(Stories.MAX_TITLE) }, t("Новая подборка"), singleLine = true, modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)))
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = { vm.createHighlight(title, listOf(story.id)); onDismiss() }) {
                Text(t("Создать"), color = if (title.isNotBlank()) Palette.Lilac else Palette.Mist)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Отмена"), color = Palette.Mist) } },
    )
}
