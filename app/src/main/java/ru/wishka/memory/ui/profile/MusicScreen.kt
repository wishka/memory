package ru.wishka.memory.ui.profile

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.wishka.memory.data.MusicLibrary
import ru.wishka.memory.data.MusicTrack
import ru.wishka.memory.data.Playlist
import ru.wishka.memory.data.durationTitle
import ru.wishka.memory.data.plural
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
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
import ru.wishka.memory.ui.theme.Palette
import java.io.File

/**
 * «Моя музыка»: свои треки и плейлисты. Файлы хранятся только на этом
 * телефоне и видны только владельцу. Плеер — внизу экрана: играет
 * очередь (все треки или плейлист), по окончании трека — следующий.
 */
@Composable
fun MusicScreen(vm: AppViewModel, state: UiState, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPlaylists by rememberSaveable { mutableStateOf(false) }
    var openPlaylist by rememberSaveable { mutableStateOf<Long?>(null) }
    var naming by remember { mutableStateOf<Playlist?>(null) }
    var creating by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    val player = rememberMusicPlayer()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            val now = System.currentTimeMillis()
            val added = withContext(Dispatchers.IO) { uris.mapNotNull { MusicLibrary.import(context, it, now) } }
            importing = false
            vm.addTracks(added)
            if (added.size < uris.size) Toast.makeText(context, t("Не все файлы удалось добавить: нужен аудиофайл до 200 МБ"), Toast.LENGTH_LONG).show()
        }
    }

    val playlist = state.playlists.firstOrNull { it.id == openPlaylist }
    val tracks = if (playlist != null) state.playlistTracks(playlist) else state.tracks

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + if (player.current != null) 84.dp else 0.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundIconButton(LineIcon.Back, Palette.Ink) {
                        if (playlist != null) openPlaylist = null else vm.closeProfileSection()
                    }
                    Spacer(Modifier.width(14.dp))
                    ScreenTitle(
                        playlist?.name ?: t("Моя музыка"),
                        plural(tracks.size, "трек", "трека", "треков"),
                        Modifier.weight(1f),
                    )
                    if (playlist == null) RoundIconButton(LineIcon.Plus) { if (!importing) picker.launch(arrayOf("audio/*")) }
                }
            }
            if (playlist == null) {
                item {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(t("Треки"), active = !showPlaylists) { showPlaylists = false }
                        Chip(tf("Плейлисты · %s", state.playlists.size), active = showPlaylists) { showPlaylists = true }
                    }
                }
            }
            if (importing) item { Hint(t("Добавляем треки…")) }

            if (playlist == null && showPlaylists) {
                item {
                    Row(Modifier.padding(horizontal = 16.dp)) { GlowButton(t("Новый плейлист"), onClick = { creating = true }) }
                }
                if (state.playlists.isEmpty()) item { Hint(t("Соберите треки в плейлисты: для дороги, для работы, для настроения.")) }
                items(state.playlists, key = { it.id }) { p ->
                    PlaylistRow(
                        p, state.playlistTracks(p),
                        onOpen = { openPlaylist = p.id },
                        onPlay = { player.play(state.playlistTracks(p), 0) },
                        onRename = { naming = p },
                        onDelete = { vm.deletePlaylist(p.id) },
                    )
                }
            } else {
                if (playlist != null) {
                    item {
                        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlowButton(t("Слушать"), enabled = tracks.isNotEmpty(), onClick = { player.play(tracks, 0) })
                            GhostButton(t("Переименовать"), onClick = { naming = playlist })
                        }
                    }
                }
                if (tracks.isEmpty()) {
                    item {
                        Hint(
                            if (playlist != null) t("В плейлисте пока пусто. Добавьте треки через «⋯» в списке треков.")
                            else t("Загрузите свои треки кнопкой «+» — mp3, m4a, flac и другие. Они будут только на этом телефоне.")
                        )
                    }
                }
                items(tracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        playing = player.current?.id == track.id && player.isPlaying,
                        playlists = state.playlists,
                        inPlaylist = playlist,
                        onPlay = { player.play(tracks, tracks.indexOf(track)) },
                        onAdd = { pid -> vm.addToPlaylist(pid, track.id) },
                        onRemoveFromPlaylist = { playlist?.let { vm.removeFromPlaylist(it.id, track.id) } },
                        onMove = { step -> playlist?.let { vm.moveInPlaylist(it.id, track.id, step) } },
                        onDelete = {
                            if (player.current?.id == track.id) player.stop()
                            vm.deleteTrack(track.id)
                            MusicLibrary.delete(track)
                        },
                    )
                }
                item {
                    Hint(t("Музыка хранится только на этом телефоне и видна только вам. Загружайте треки, которые вы купили или на которые у вас есть права."))
                }
            }
        }

        player.current?.let { track ->
            MiniPlayer(
                track = track,
                playing = player.isPlaying,
                progress = player.progress,
                onToggle = player::toggle,
                onNext = player::next,
                onPrev = player::previous,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = contentPadding.calculateBottomPadding() - 4.dp, start = 16.dp, end = 16.dp),
            )
        }
    }

    if (creating || naming != null) {
        var name by remember { mutableStateOf(naming?.name ?: "") }
        AlertDialog(
            onDismissRequest = { creating = false; naming = null },
            containerColor = Palette.Night,
            title = { Text(if (naming != null) t("Название плейлиста") else t("Новый плейлист"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink) },
            text = {
                GlassField(name, { name = it.take(60) }, t("Например, «Ночная дорога»"), singleLine = true,
                    modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)))
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val editing = naming
                    if (editing != null) vm.renamePlaylist(editing.id, name)
                    else vm.createPlaylist(name)?.let { openPlaylist = it; showPlaylists = true }
                    creating = false
                    naming = null
                }) { Text(t("Готово"), color = Palette.Lilac) }
            },
            dismissButton = { TextButton(onClick = { creating = false; naming = null }) { Text(t("Отмена"), color = Palette.Mist) } },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
}

@Composable
private fun TrackRow(
    track: MusicTrack,
    playing: Boolean,
    playlists: List<Playlist>,
    inPlaylist: Playlist?,
    onPlay: () -> Unit,
    onAdd: (Long) -> Unit,
    onRemoveFromPlaylist: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(Palette.Orchid, Palette.Ocean))),
                contentAlignment = Alignment.Center,
            ) { Glyph(if (playing) LineIcon.Pause else LineIcon.Play, Palette.Ink, size = 20.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleMedium, color = if (playing) Palette.Lilac else Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(track.artist, durationTitle(track.durationMs)).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                Box(Modifier.size(36.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) {
                    Glyph(LineIcon.More, Palette.Mist, size = 20.dp)
                }
                MemoryMenu(expanded = menu, onDismiss = { menu = false; choosing = false }) {
                    if (choosing) {
                        if (playlists.isEmpty()) MenuRow(LineIcon.Info, t("Сначала создайте плейлист")) { menu = false; choosing = false }
                        playlists.forEach { p ->
                            MenuRow(LineIcon.Music, p.name) { menu = false; choosing = false; onAdd(p.id) }
                        }
                    } else {
                        MenuRow(LineIcon.Plus, t("Добавить в плейлист")) { choosing = true }
                        if (inPlaylist != null) {
                            MenuRow(LineIcon.ChevronDown, t("Выше")) { menu = false; onMove(-1) }
                            MenuRow(LineIcon.ChevronDown, t("Ниже")) { menu = false; onMove(1) }
                            MenuRow(LineIcon.Close, t("Убрать из плейлиста")) { menu = false; onRemoveFromPlaylist() }
                        } else {
                            MenuRow(LineIcon.Trash, t("Удалить трек"), color = Palette.Lilac) { menu = false; onDelete() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    tracks: List<MusicTrack>,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Palette.Violet, Palette.Sky))),
                contentAlignment = Alignment.Center,
            ) { Glyph(LineIcon.Music, Palette.Ink, size = 24.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(playlist.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    tf("%s · %s", plural(tracks.size, "трек", "трека", "треков"), durationTitle(tracks.sumOf { it.durationMs })),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                )
            }
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled = tracks.isNotEmpty(), onClick = onPlay), contentAlignment = Alignment.Center) {
                Glyph(LineIcon.Play, if (tracks.isEmpty()) Palette.Mist else Palette.Lilac, size = 22.dp)
            }
            Box {
                Box(Modifier.size(36.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) {
                    Glyph(LineIcon.More, Palette.Mist, size = 20.dp)
                }
                MemoryMenu(expanded = menu, onDismiss = { menu = false }) {
                    MenuRow(LineIcon.Edit, t("Переименовать")) { menu = false; onRename() }
                    MenuRow(LineIcon.Trash, t("Удалить плейлист"), color = Palette.Lilac) { menu = false; onDelete() }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    track: MusicTrack,
    playing: Boolean,
    progress: Float,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), strong = true)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph(LineIcon.Music, Palette.Lilac, size = 20.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (track.artist.isNotBlank()) Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, maxLines = 1)
            }
            PlayerButton(LineIcon.Back, onPrev)
            PlayerButton(if (playing) LineIcon.Pause else LineIcon.Play, onToggle)
            PlayerButton(LineIcon.Forward, onNext)
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Palette.Plum)) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(2.dp).background(Brush.horizontalGradient(listOf(Palette.Lilac, Palette.Sky))))
        }
    }
}

@Composable
private fun PlayerButton(icon: LineIcon, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Glyph(icon, Palette.Ink, size = 22.dp)
    }
}

/** Плеер «Моей музыки»: очередь треков, текущий, пауза, следующий, ход. */
class MusicPlayerState(private val player: ExoPlayer) {
    var queue by mutableStateOf<List<MusicTrack>>(emptyList())
        private set
    var index by mutableStateOf(0)
        private set
    var isPlaying by mutableStateOf(false)
        internal set
    var progress by mutableFloatStateOf(0f)
        internal set

    val current: MusicTrack? get() = queue.getOrNull(index)

    fun play(tracks: List<MusicTrack>, start: Int) {
        if (tracks.isEmpty()) return
        queue = tracks
        index = start.coerceIn(0, tracks.lastIndex)
        load()
    }

    fun toggle() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() {
        if (queue.isEmpty()) return
        index = (index + 1) % queue.size
        load()
    }

    fun previous() {
        if (queue.isEmpty()) return
        // В начале трека — предыдущий, иначе — сначала этот же
        if (player.currentPosition > 3000) player.seekTo(0) else {
            index = (index - 1 + queue.size) % queue.size
            load()
        }
    }

    fun stop() {
        player.stop()
        queue = emptyList()
        isPlaying = false
    }

    internal fun tick() {
        val d = player.duration
        progress = if (d > 0) player.currentPosition.toFloat() / d else 0f
    }

    private fun load() {
        val track = current ?: return
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(track.path))))
        player.prepare()
        player.play()
    }
}

@Composable
fun rememberMusicPlayer(): MusicPlayerState {
    val context = LocalContext.current
    val exo = remember { ExoPlayer.Builder(context).build() }
    val state = remember { MusicPlayerState(exo) }
    val onEnded by rememberUpdatedState { state.next() }
    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                state.isPlaying = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onEnded()
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }
    LaunchedEffect(exo) {
        while (true) {
            state.tick()
            delay(250)
        }
    }
    return state
}
