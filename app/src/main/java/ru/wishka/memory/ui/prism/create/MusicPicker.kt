package ru.wishka.memory.ui.prism.create

import ru.wishka.memory.ui.components.label
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.wishka.memory.data.CatalogResult
import ru.wishka.memory.data.CatalogSource
import ru.wishka.memory.data.CatalogTrack
import ru.wishka.memory.data.MusicCatalog
import ru.wishka.memory.data.MusicTrack
import ru.wishka.memory.data.durationTitle
import ru.wishka.memory.i18n.t
import ru.wishka.memory.ui.components.Chip
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.prism.CatalogCover
import ru.wishka.memory.ui.theme.Palette

/**
 * Музыка для ролика — при съёмке и в редакторе: «Моя музыка» или
 * медиатека (Jamendo, Openverse). Трек можно послушать; выбранный из
 * медиатеки сначала скачивается в «Мою музыку» (если площадка разрешает)
 * — из файла ролик и собирается. onPick получает трек «Моей музыки».
 */
@Composable
fun MusicPicker(
    tracks: List<MusicTrack>,
    onAddTracks: (List<MusicTrack>) -> Unit,
    onPick: (MusicTrack) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var library by rememberSaveable { mutableStateOf(tracks.isEmpty()) }
    var source by rememberSaveable { mutableStateOf(if (MusicCatalog.hasJamendoKey) CatalogSource.Jamendo else CatalogSource.Openverse) }
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<List<CatalogTrack>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf<String?>(null) }

    // Прослушивание: один плеер на окно
    val player = remember { ExoPlayer.Builder(context).build() }
    var playingId by remember { mutableStateOf<String?>(null) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) playingId = null
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    fun preview(id: String, uri: String) {
        if (playingId == id) {
            player.pause(); playingId = null
            return
        }
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.play()
        playingId = id
    }

    LaunchedEffect(library, source, submitted, genre) {
        if (!library) return@LaunchedEffect
        loading = true
        error = null
        when (val r = withContext(Dispatchers.IO) { MusicCatalog.search(source, submitted, genre, 0) }) {
            is CatalogResult.Tracks -> found = r.tracks
            is CatalogResult.Failure -> { found = emptyList(); error = r.message }
        }
        loading = false
    }

    fun choose(track: CatalogTrack) {
        // Уже скачан — берём из «Моей музыки»
        tracks.firstOrNull { it.sourceUrl.isNotEmpty() && it.sourceUrl == track.pageUrl }?.let { onPick(it); return }
        if (!track.downloadable) {
            Toast.makeText(context, t("Этот трек можно только слушать — площадка не разрешает скачивание"), Toast.LENGTH_SHORT).show()
            return
        }
        saving = track.id
        scope.launch {
            val saved = withContext(Dispatchers.IO) { MusicCatalog.download(context, track, System.currentTimeMillis()) }
            saving = null
            if (saved != null) {
                onAddTracks(listOf(saved))
                onPick(saved)
            } else {
                Toast.makeText(context, t("Не удалось скачать трек"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.Night)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).glass(CircleShape).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { Glyph(LineIcon.Close, Palette.Ink, description = LineIcon.Close.label) }
                Spacer(Modifier.width(12.dp))
                Text(t("Музыка для ролика"), style = MaterialTheme.typography.titleLarge, color = Palette.Ink)
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(t("Моя музыка"), active = !library) { library = false }
                Chip(t("Медиатека"), active = library) { library = true }
            }
            if (library) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CatalogSource.entries.forEach { s -> Chip(s.title, active = s == source) { source = s } }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassField(
                            query, { query = it.take(80) }, t("Исполнитель, трек, настроение"),
                            singleLine = true,
                            modifier = Modifier.weight(1f).glass(RoundedCornerShape(22.dp)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Palette.Orchid, Palette.Ocean)))
                                .clickable { submitted = query.trim() },
                            contentAlignment = Alignment.Center,
                        ) { Glyph(LineIcon.Search, Palette.Ink, size = 20.dp, description = LineIcon.Search.label) }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(t("Популярное"), active = genre == null) { genre = null }
                        MusicCatalog.GENRES.forEach { (tag, title) ->
                            Chip(t(title), active = genre == tag) { genre = if (genre == tag) null else tag }
                        }
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!library) {
                    if (tracks.isEmpty()) {
                        item {
                            Text(
                                t("В «Моей музыке» пока пусто — выберите трек в медиатеке."),
                                style = MaterialTheme.typography.bodyMedium, color = Palette.Mist,
                            )
                        }
                    }
                    items(tracks, key = { it.id }) { track ->
                        PickerRow(
                            title = track.title,
                            subtitle = listOf(track.artist, durationTitle(track.durationMs), track.license).filter { it.isNotBlank() }.joinToString(" · "),
                            cover = { Glyph(LineIcon.Music, Palette.Ink, size = 20.dp) },
                            playing = playingId == track.id,
                            busy = false,
                            onPreview = { preview(track.id, track.path) },
                            onChoose = { onPick(track) },
                        )
                    }
                } else {
                    if (loading) item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Palette.Lilac, modifier = Modifier.size(28.dp))
                        }
                    }
                    error?.let { message ->
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(message, style = MaterialTheme.typography.bodyMedium, color = Palette.Ink)
                                if (source == CatalogSource.Jamendo) GhostButton(t("Открыть Openverse"), onClick = { source = CatalogSource.Openverse })
                            }
                        }
                    }
                    items(found, key = { it.id }) { track ->
                        PickerRow(
                            title = track.title,
                            subtitle = listOf(track.artist, if (track.durationMs > 0) durationTitle(track.durationMs) else "", track.license)
                                .filter { it.isNotBlank() }.joinToString(" · "),
                            cover = { CatalogCover(track, Modifier.fillMaxSize()) },
                            playing = playingId == track.id,
                            busy = saving == track.id,
                            onPreview = { preview(track.id, track.streamUrl) },
                            onChoose = { if (saving == null) choose(track) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    title: String,
    subtitle: String,
    cover: @Composable () -> Unit,
    playing: Boolean,
    busy: Boolean,
    onPreview: () -> Unit,
    onChoose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(Palette.Violet, Palette.Ocean)))
                .clickable(onClick = onPreview),
            contentAlignment = Alignment.Center,
        ) {
            cover()
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(Palette.Void.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) { Glyph(if (playing) LineIcon.Pause else LineIcon.Play, Palette.Ink, size = 14.dp) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).clickable(onClick = onPreview)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (playing) Palette.Lilac else Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Brush.linearGradient(listOf(Palette.Orchid, Palette.Ocean)))
                .clickable(onClick = onChoose)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) CircularProgressIndicator(color = Palette.Ink, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            else Text(t("ВЫБРАТЬ"), style = MaterialTheme.typography.labelSmall, color = Palette.Ink)
        }
    }
}
