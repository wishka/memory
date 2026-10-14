package ru.wishka.memory.ui.live

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import ru.wishka.memory.data.Demo
import ru.wishka.memory.data.LiveStream
import ru.wishka.memory.data.Lives
import ru.wishka.memory.data.User
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.Avatar
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.Nebula
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.components.label
import ru.wishka.memory.ui.settings.MemorySwitch
import ru.wishka.memory.ui.theme.Palette

/** Ведущий эфира: я или друг из демо-данных. */
private fun host(state: UiState, live: LiveStream): User =
    if (live.hostId == state.me.id) state.me else Demo.person(live.hostId) ?: User(live.hostId, t("Кто-то"), "unknown")

/** Метка «В ЭФИРЕ». */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Text(
        t("В ЭФИРЕ"),
        style = MaterialTheme.typography.labelSmall,
        color = Palette.Ink,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.horizontalGradient(listOf(Palette.PearlPink, Palette.Orchid)))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/**
 * Строка над «Историями»: «Начать эфир» и эфиры друзей, которые идут
 * сейчас. Свой идущий эфир — первым, вернуться в него.
 */
@Composable
fun LiveRow(vm: AppViewModel, state: UiState) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "start") {
            val mine = state.myLive
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(76.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button) { if (mine != null) vm.returnToMyLive() else vm.openLiveStart() }
                    .padding(vertical = 4.dp),
            ) {
                Box(
                    Modifier.size(60.dp).clip(CircleShape).glass(CircleShape, strong = mine != null),
                    contentAlignment = Alignment.Center,
                ) { Glyph(if (mine != null) LineIcon.Video else LineIcon.Plus, Palette.Lilac, size = 26.dp) }
                Text(
                    if (mine != null) t("Мой эфир") else t("Эфир"),
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.Ink,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        items(state.visibleLives, key = { "live" + it.id }) { live ->
            val person = host(state, live)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(76.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button) { vm.watchLive(live.id) }
                    .semantics(mergeDescendants = true) { contentDescription = tf("%s в эфире: %s", person.name, live.title) }
                    .padding(vertical = 4.dp),
            ) {
                Box(contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.border(2.dp, Brush.sweepGradient(listOf(Palette.PearlPink, Palette.Orchid, Palette.PearlPink)), CircleShape).padding(4.dp)) {
                        Avatar(person, size = 52.dp)
                    }
                    LiveBadge(Modifier.padding(bottom = 0.dp))
                }
                Text(person.name.substringBefore(' '), style = MaterialTheme.typography.labelLarge, color = Palette.Ink, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Запуск эфира: название и кто видит. */
@Composable
fun LiveStartDialog(vm: AppViewModel) {
    var title by rememberSaveable { mutableStateOf("") }
    var friendsOnly by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = vm::closeLiveStart,
        containerColor = Palette.Night,
        title = { Text(t("Прямой эфир"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassField(
                    title, { title = it.take(Lives.MAX_TITLE); error = null }, t("О чём эфир"),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)),
                )
                Row(
                    Modifier.toggleable(value = friendsOnly, role = Role.Switch) { friendsOnly = it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t("Только для друзей"), style = MaterialTheme.typography.bodyLarge, color = Palette.Ink, modifier = Modifier.weight(1f))
                    MemorySwitch(friendsOnly, onChange = null)
                }
                Text(
                    t("Друзья получат уведомление. Эфир не сохраняется — он есть, пока идёт."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                )
                error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { error = vm.startLive(title, friendsOnly) }) { Text(t("Начать"), color = Palette.Lilac) }
        },
        dismissButton = { TextButton(onClick = vm::closeLiveStart) { Text(t("Отмена"), color = Palette.Mist) } },
    )
}

/**
 * Эфир во весь экран. Ведущий видит свою камеру; зритель — пока
 * демо-картинку (видео пойдёт через LiveKit). Сверху — кто, «В ЭФИРЕ»,
 * зрители и время; снизу — чат эфира, поле и сердечко.
 */
@Composable
fun LiveScreen(vm: AppViewModel, state: UiState, live: LiveStream) {
    val mine = state.myLive?.id == live.id
    val person = host(state, live)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(live.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    BackHandler { vm.back() }

    Box(Modifier.fillMaxSize().background(Palette.Void)) {
        if (mine) HostCamera(Modifier.fillMaxSize()) else {
            Nebula(live.seed, Modifier.fillMaxSize())
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(person, size = 96.dp)
                if (!vm.liveTransport.connected) {
                    Text(
                        t("Демо: видео эфира появится, когда подключим сервер"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Mist,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
        // Затемнение сверху и снизу — чтобы текст читался на любом видео
        Box(Modifier.fillMaxWidth().height(160.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(320.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))))

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(person, size = 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(person.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(live.title, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    LiveBadge()
                    Text(
                        "👁 ${live.viewers} · ${Lives.clock(live.durationMs(now))}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Palette.Ink,
                        modifier = Modifier.semantics { contentDescription = tf("Зрителей: %s", live.viewers) },
                    )
                }
                if (!mine) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).glass(CircleShape)
                            .clickable(role = Role.Button) { vm.reportPerson(person) },
                        contentAlignment = Alignment.Center,
                    ) { Glyph(LineIcon.Flag, Palette.PearlPink, size = 18.dp, description = LineIcon.Flag.label) }
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(40.dp).clip(CircleShape).glass(CircleShape)
                        .clickable(role = Role.Button) { if (mine) vm.back() else vm.leaveLive() },
                    contentAlignment = Alignment.Center,
                ) { Glyph(LineIcon.Close, Palette.Ink, size = 18.dp, description = if (mine) t("Закончить эфир") else LineIcon.Close.label) }
            }
            Spacer(Modifier.weight(1f))
            LiveChat(live, Modifier.fillMaxWidth().height(240.dp).padding(horizontal = 12.dp))
            LiveInput(vm, mine)
        }
    }

    if (state.confirmEndLive && mine) {
        AlertDialog(
            onDismissRequest = vm::cancelEndLive,
            containerColor = Palette.Night,
            title = { Text(t("Закончить эфир?"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink) },
            text = { Text(tf("Зрителей сейчас: %s. Эфир не сохранится.", live.viewers), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist) },
            confirmButton = { TextButton(onClick = vm::endLive) { Text(t("Закончить"), color = Palette.Lilac) } },
            dismissButton = { TextButton(onClick = vm::cancelEndLive) { Text(t("Продолжить"), color = Palette.Mist) } },
        )
    }
}

/** Чат эфира: новые снизу, диктор читает новые вслух (liveRegion). */
@Composable
private fun LiveChat(live: LiveStream, modifier: Modifier) {
    val list = rememberLazyListState()
    LaunchedEffect(live.comments.size) { if (live.comments.isNotEmpty()) list.animateScrollToItem(live.comments.lastIndex) }
    LazyColumn(
        state = list,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(live.comments, key = { it.id }) { c ->
            Row(verticalAlignment = Alignment.Top) {
                Text(c.name.substringBefore(' ') + "  ", style = MaterialTheme.typography.labelLarge, color = Palette.Lilac)
                Text(c.text, style = MaterialTheme.typography.bodyMedium, color = Palette.Ink)
            }
        }
    }
}

@Composable
private fun LiveInput(vm: AppViewModel, mine: Boolean) {
    var text by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassField(
            text, { text = it.take(Lives.MAX_COMMENT) }, if (mine) t("Написать зрителям") else t("Комментарий"),
            singleLine = true,
            modifier = Modifier.weight(1f).glass(RoundedCornerShape(22.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(46.dp).clip(CircleShape).glass(CircleShape)
                .clickable(enabled = text.isNotBlank(), role = Role.Button) { vm.sendLiveComment(text); text = "" },
            contentAlignment = Alignment.Center,
        ) { Glyph(LineIcon.Send, if (text.isBlank()) Palette.Mist else Palette.Ink, description = LineIcon.Send.label) }
        if (!mine) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Palette.PearlPink, Palette.Orchid)))
                    .clickable(role = Role.Button) { vm.sendHeart() },
                contentAlignment = Alignment.Center,
            ) { Glyph(LineIcon.HeartFilled, Palette.Ink, description = t("Сердечко")) }
        }
    }
}

/** Своя камера — фронтальная, как в видеозвонке; просит разрешение, если его нет. */
@Composable
private fun HostCamera(modifier: Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    var allowed by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        allowed = r[Manifest.permission.CAMERA] == true || granted(Manifest.permission.CAMERA)
    }
    LaunchedEffect(Unit) {
        if (!allowed || !granted(Manifest.permission.RECORD_AUDIO)) ask.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        }
    }
    LaunchedEffect(allowed) { if (allowed) controller.bindToLifecycle(owner) }
    DisposableEffect(Unit) { onDispose { controller.unbind() } }
    Box(modifier) {
        if (allowed) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.controller = controller
                }
            })
        } else {
            Text(
                t("Нет доступа к камере. Разрешите его в настройках телефона."),
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.Mist,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        }
    }
}

/** Итог своего эфира. */
@Composable
fun LiveSummaryDialog(vm: AppViewModel, live: LiveStream) {
    AlertDialog(
        onDismissRequest = vm::dismissLiveSummary,
        containerColor = Palette.Night,
        title = { Text(t("Эфир закончен"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink) },
        text = { Text(Lives.summary(live, live.endedAt), style = MaterialTheme.typography.bodyLarge, color = Palette.Ink) },
        confirmButton = { TextButton(onClick = vm::dismissLiveSummary) { Text(t("Готово"), color = Palette.Lilac) } },
    )
}
