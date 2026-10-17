package ru.wishka.memory.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.wishka.memory.data.CallTransport
import ru.wishka.memory.data.LiveTransport

/**
 * Видео звонка или эфира для экрана: собеседник (ведущий) и своя камера.
 * Пока видео нет (демо, ещё соединяемся) — экран рисует свою картинку.
 */
interface VideoViews {
    /** Есть видео собеседника (ведущего). */
    val remoteVideo: StateFlow<Boolean>

    /** Своя камера идёт в комнату. */
    val localVideo: StateFlow<Boolean>

    @Composable
    fun Remote(modifier: Modifier)

    @Composable
    fun Local(modifier: Modifier)

    /** Разрешения дали только что — включить микрофон и камеру, которые не включились. */
    fun permissionsGranted()
}

/**
 * Одна комната LiveKit: подключиться по адресу и токену от сервера Memory,
 * публиковать микрофон и камеру, показывать чужое видео. Пустой токен —
 * демо: ничего не делаем.
 */
class LiveKitSession(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var room: Room? = null
    private var job: Job? = null
    private var wantMic = false
    private var wantCamera = false
    private var front = true

    val remote = MutableStateFlow<VideoTrack?>(null)
    val local = MutableStateFlow<VideoTrack?>(null)

    /** Кто-то ещё уже в комнате. */
    val peerJoined: Boolean get() = room?.remoteParticipants?.isNotEmpty() == true

    /** Подключение начато с настоящим токеном (а не демо). */
    @Volatile
    var active = false
        private set

    fun connect(url: String, token: String, mic: Boolean, camera: Boolean) {
        leave()
        if (url.isEmpty() || token.isEmpty()) return
        active = true
        wantMic = mic
        wantCamera = camera
        front = true
        val r = LiveKit.create(context.applicationContext)
        room = r
        job = scope.launch {
            launch {
                r.events.collect { e ->
                    when (e) {
                        is RoomEvent.TrackSubscribed -> (e.track as? VideoTrack)?.let { remote.value = it }
                        is RoomEvent.TrackUnsubscribed -> if (e.track === remote.value) remote.value = null
                        is RoomEvent.ParticipantDisconnected -> if (r.remoteParticipants.isEmpty()) remote.value = null
                        else -> Unit
                    }
                }
            }
            try {
                r.connect(url, token)
                applyLocal()
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                // Не соединились — экран остаётся с картинкой; звонок можно положить
            }
        }
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    /** Включить, что нужно и на что есть разрешение. */
    private suspend fun applyLocal() {
        val r = room ?: return
        val p = r.localParticipant
        runCatching { p.setMicrophoneEnabled(wantMic && granted(Manifest.permission.RECORD_AUDIO)) }
        val cam = wantCamera && granted(Manifest.permission.CAMERA)
        runCatching { p.setCameraEnabled(cam) }
        val track = p.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack
        if (cam) runCatching { track?.switchCamera(position = if (front) CameraPosition.FRONT else CameraPosition.BACK) }
        local.value = if (cam) track else null
    }

    fun setMic(on: Boolean) {
        wantMic = on
        scope.launch { applyLocal() }
    }

    fun setCamera(on: Boolean, front: Boolean) {
        wantCamera = on
        this.front = front
        scope.launch { applyLocal() }
    }

    fun refresh() {
        scope.launch { applyLocal() }
    }

    fun leave() {
        job?.cancel()
        job = null
        // Сначала убрать видео с экрана — потом освобождать дорожки
        remote.value = null
        local.value = null
        active = false
        val r = room
        room = null
        r?.let {
            runCatching { it.disconnect() }
            runCatching { it.release() }
        }
    }

    /** Видеодорожка на экране (TextureView — поверх неё рисуются кнопки). */
    @Composable
    fun Track(track: VideoTrack?, modifier: Modifier, mirror: Boolean) {
        val r = room ?: return
        if (track == null) return
        key(track) {
            AndroidView(
                factory = { ctx ->
                    TextureViewRenderer(ctx).also { v ->
                        r.initVideoRenderer(v)
                        v.setMirror(mirror)
                        track.addRenderer(v)
                    }
                },
                modifier = modifier,
                onRelease = { v ->
                    // Комнату могли уже освободить (положили трубку) — дорожки больше нет
                    runCatching { track.removeRenderer(v) }
                    runCatching { v.release() }
                },
            )
        }
    }
}

private class SessionViews(private val s: LiveKitSession) : VideoViews {
    private val hasRemote = MutableStateFlow(false)
    private val hasLocal = MutableStateFlow(false)
    override val remoteVideo: StateFlow<Boolean> get() = hasRemote
    override val localVideo: StateFlow<Boolean> get() = hasLocal

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            launch { s.remote.collect { hasRemote.value = it != null } }
            s.local.collect { hasLocal.value = it != null }
        }
    }

    @Composable
    override fun Remote(modifier: Modifier) {
        val t by s.remote.collectAsState()
        s.Track(t, modifier, mirror = false)
    }

    @Composable
    override fun Local(modifier: Modifier) {
        val t by s.local.collectAsState()
        s.Track(t, modifier, mirror = true)
    }

    override fun permissionsGranted() = s.refresh()
}

/** Звонок через LiveKit: комната на двоих, микрофон и (в видеозвонке) камера. */
class LiveKitCallTransport private constructor(private val session: LiveKitSession) : CallTransport, VideoViews by SessionViews(session) {
    constructor(context: Context) : this(LiveKitSession(context))

    override fun connect(url: String, room: String, token: String, video: Boolean) = session.connect(url, token, mic = true, camera = video)
    override fun setMuted(muted: Boolean) = session.setMic(!muted)
    override fun setCamera(on: Boolean, front: Boolean) = session.setCamera(on, front)
    override fun hangUp() = session.leave()
    override val connected: Boolean get() = session.active
    override val peerJoined: Boolean get() = session.peerJoined
}

/** Эфир через LiveKit: ведущий публикует камеру и микрофон, зрители только смотрят. */
class LiveKitLiveTransport private constructor(private val session: LiveKitSession) : LiveTransport, VideoViews by SessionViews(session) {
    constructor(context: Context) : this(LiveKitSession(context))

    override fun publish(url: String, room: String, token: String) = session.connect(url, token, mic = true, camera = true)
    override fun join(url: String, room: String, token: String) = session.connect(url, token, mic = false, camera = false)
    override fun leave() = session.leave()
    override val connected: Boolean get() = session.active
}

/**
 * Попросить камеру и микрофон для звонка или эфира, если их ещё нет; дали —
 * сказать связи (onGranted), чтобы она включила то, что не включилось.
 */
@Composable
fun AskCallPermissions(video: Boolean, onGranted: () -> Unit) {
    val context = LocalContext.current
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) onGranted()
    }
    LaunchedEffect(video) {
        val need = listOfNotNull(Manifest.permission.RECORD_AUDIO, if (video) Manifest.permission.CAMERA else null).filter { !granted(it) }
        if (need.isNotEmpty()) ask.launch(need.toTypedArray())
    }
}

/**
 * Связь звонков и эфиров — одна на приложение: экран пересоздаётся (тема,
 * язык, размер шрифта), а разговор продолжается в той же комнате.
 */
object LiveLinks {
    private var call: LiveKitCallTransport? = null
    private var live: LiveKitLiveTransport? = null

    @Synchronized
    fun call(context: Context): LiveKitCallTransport = call ?: LiveKitCallTransport(context.applicationContext).also { call = it }

    @Synchronized
    fun live(context: Context): LiveKitLiveTransport = live ?: LiveKitLiveTransport(context.applicationContext).also { live = it }
}
