package ru.wishka.memory.ui.calls

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import ru.wishka.memory.call.AskCallPermissions
import ru.wishka.memory.call.VideoViews
import ru.wishka.memory.data.ActiveCall
import ru.wishka.memory.data.CallPhase
import ru.wishka.memory.data.Calls
import ru.wishka.memory.data.Demo
import ru.wishka.memory.data.Lives
import ru.wishka.memory.data.agoTitle
import ru.wishka.memory.data.minutesSince
import ru.wishka.memory.i18n.t
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.Avatar
import ru.wishka.memory.ui.components.CameraPreview
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.Nebula
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.theme.Palette

/** Что написать под именем: «Вызов…», «Входящий видеозвонок», «3:12». */
private fun status(call: ActiveCall, now: Long): String = when (call.phase) {
    CallPhase.Calling -> t("Вызов…")
    CallPhase.Incoming -> if (call.video) t("Входящий видеозвонок") else t("Входящий звонок")
    CallPhase.Active -> Lives.clock(call.durationMs(now))
}

/**
 * Звонок во весь экран. Голосовой — большой аватар; видео — собеседник
 * во весь экран и своя камера окошком. Входящий — «Принять» и «Отклонить».
 */
@Composable
fun CallScreen(vm: AppViewModel, state: UiState, call: ActiveCall) {
    val peer = Demo.person(call.peerId) ?: return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(call.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    BackHandler { /* звонок не сбрасывается «назад» — только кнопкой */ }
    val videoOn = call.video && call.cameraOn && call.phase != CallPhase.Incoming
    // Связь LiveKit: видео собеседника и своя камера идут через неё
    val views = vm.callTransport as? VideoViews
    val noVideo = remember { MutableStateFlow(false) }
    val remoteOn by (views?.remoteVideo ?: noVideo).collectAsState()
    if (views != null) AskCallPermissions(call.video) { views.permissionsGranted() }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF241A4A), Color(0xFF07081A))))) {
        if (call.video && call.phase == CallPhase.Active) {
            // Собеседник: его видео; пока его нет (демо, соединяемся) — картинка
            if (remoteOn && views != null) views.Remote(Modifier.fillMaxSize())
            else Nebula(peer.id * 37 + 11, Modifier.fillMaxSize())
        }
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            Avatar(peer, size = if (call.video && call.phase == CallPhase.Active) 72.dp else 128.dp)
            Spacer(Modifier.height(16.dp))
            Text(peer.name, style = MaterialTheme.typography.headlineSmall, color = Palette.OnMedia, textAlign = TextAlign.Center)
            Text(
                status(call, now),
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.OnMedia.copy(alpha = 0.8f),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (!vm.callTransport.connected) {
                Text(
                    t("Демо: голос и видео пойдут, когда подключим сервер"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.OnMedia.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (call.phase == CallPhase.Incoming) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BigButton(LineIcon.HangUp, t("Отклонить"), Color(0xFFE5536F), vm::declineCall)
                    BigButton(if (call.video) LineIcon.Video else LineIcon.Phone, t("Принять"), Color(0xFF3FCF8E), vm::acceptCall)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SmallButton(if (call.muted) LineIcon.MicOff else LineIcon.Mic, if (call.muted) t("Включить микрофон") else t("Выключить микрофон"), call.muted, vm::toggleMute)
                    if (call.video) {
                        SmallButton(if (call.cameraOn) LineIcon.Video else LineIcon.VideoOff, if (call.cameraOn) t("Выключить камеру") else t("Включить камеру"), !call.cameraOn, vm::toggleCamera)
                        SmallButton(LineIcon.Flip, t("Сменить камеру"), false, vm::flipCamera)
                    }
                    SmallButton(LineIcon.Volume, if (call.speaker) t("Динамик выключить") else t("Громкая связь"), call.speaker, vm::toggleSpeaker)
                }
                Spacer(Modifier.height(24.dp))
                BigButton(LineIcon.HangUp, t("Завершить"), Color(0xFFE5536F), vm::endCall)
            }
            Spacer(Modifier.height(16.dp))
        }
        // Своя камера — окошком в углу
        if (videoOn) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .size(width = 110.dp, height = 160.dp)
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                // Камера занята звонком — показываем то, что уходит собеседнику
                if (views != null && vm.callTransport.connected) views.Local(Modifier.fillMaxSize())
                else CameraPreview(Modifier.fillMaxSize(), front = call.frontCamera)
            }
        }
    }
}

@Composable
private fun BigButton(icon: LineIcon, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(color).clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Glyph(icon, Color.White, size = 30.dp, description = label) }
        Text(label, style = MaterialTheme.typography.labelLarge, color = Palette.OnMedia, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun SmallButton(icon: LineIcon, label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (active) Color.White else Color.White.copy(alpha = 0.16f))
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(icon, if (active) Color(0xFF241A4A) else Color.White, size = 24.dp, description = label) }
}

/**
 * История звонков — во вкладке «Чаты», переключатель «Звонки»: кто, видео
 * или голос, входящий или исходящий, пропущенный. Нажатие — перезвонить.
 */
fun LazyListScope.callLogItems(vm: AppViewModel, state: UiState, onError: (String) -> Unit) {
    if (state.callLog.isEmpty()) {
        item {
            Text(
                t("Звонков пока не было. Позвонить можно из переписки — значки трубки и камеры вверху."),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Mist,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
    items(state.callLog, key = { "call-" + it.id }) { r ->
        val peer = Demo.person(r.peerId) ?: return@items
        val bad = r.missed || r.declined
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .glass(RoundedCornerShape(18.dp))
                .clickable(onClickLabel = t("Перезвонить"), role = Role.Button) { vm.callBack(r)?.let(onError) }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(peer, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(peer.name, style = MaterialTheme.typography.titleMedium, color = if (bad && !r.outgoing) Palette.PearlPink else Palette.Ink)
                Text(
                    (if (r.outgoing) "↗ " else "↙ ") + Calls.title(r) + " · " + agoTitle(minutesSince(r.at, 0)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                )
            }
            Glyph(if (r.video) LineIcon.Video else LineIcon.Phone, Palette.Lilac, size = 22.dp)
        }
    }
    if (state.callLog.isNotEmpty()) {
        item {
            Text(
                t("Очистить историю звонков"),
                style = MaterialTheme.typography.labelLarge,
                color = Palette.Mist,
                modifier = Modifier
                    .padding(20.dp)
                    .clickable(role = Role.Button, onClick = vm::clearCallLog)
                    .padding(8.dp),
            )
        }
    }
}
