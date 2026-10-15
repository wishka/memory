package ru.wishka.memory.ui.prism

import ru.wishka.memory.ui.components.label
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import ru.wishka.memory.data.CatalogResult
import ru.wishka.memory.data.CatalogSource
import ru.wishka.memory.data.CatalogTrack
import ru.wishka.memory.data.MusicCatalog
import ru.wishka.memory.data.durationTitle
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.Chip
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassCard
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.MemoryMenu
import ru.wishka.memory.ui.components.MenuRow
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.theme.Palette

/**
 * Медиатека во вкладке PRISM: открытая музыка Jamendo и Openverse —
 * поиск, жанры, прослушивание потоком. Понравившийся трек можно
 * сохранить в «Мою музыку» (если площадка разрешает скачивание) и
 * поставить в свой ролик. Под каждым треком — автор и лицензия.
 */
@Composable
fun MediaLibraryScreen(vm: AppViewModel, state: UiState, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by rememberSaveable { mutableStateOf(if (MusicCatalog.hasJamendoKey) CatalogSource.Jamendo else CatalogSource.Openverse) }
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    var tracks by remember { mutableStateOf<List<CatalogTrack>>(emptyList()) }
    var page by remember { mutableStateOf(0) }
    var more by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Плеер медиатеки: один на экран, играет поток выбранного трека
    val player = remember { ExoPlayer.Builder(context).build() }
    var current by remember { mutableStateOf<CatalogTrack?>(null) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_ENDED) { player.seekTo(0); player.pause() }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    LaunchedEffect(playing) {
        while (playing) {
            val d = player.duration
            progress = if (d > 0) player.currentPosition.toFloat() / d else 0f
            delay(300)
        }
    }
    fun play(track: CatalogTrack) {
        if (current?.id == track.id) {
            if (player.isPlaying) player.pause() else player.play()
            return
        }
        current = track
        progress = 0f
        player.setMediaItem(MediaItem.fromUri(track.streamUrl))
        player.prepare()
        player.play()
    }

    suspend fun load(reset: Boolean) {
        loading = true
        error = null
        val next = if (reset) 0 else page + 1
        val result = withContext(Dispatchers.IO) { MusicCatalog.search(source, submitted, genre, next) }
        loading = false
        when (result) {
            is CatalogResult.Tracks -> {
                tracks = if (reset) result.tracks else (tracks + result.tracks).distinctBy { it.id }
                page = next
                more = result.more
            }
            is CatalogResult.Failure -> {
                error = result.message
                if (reset) tracks = emptyList()
                more = false
            }
        }
    }
    LaunchedEffect(source, submitted, genre) { load(reset = true) }

    fun save(track: CatalogTrack) {
        if (state.tracks.any { it.sourceUrl.isNotEmpty() && it.sourceUrl == track.pageUrl }) {
            Toast.makeText(context, t("Этот трек уже в «Моей музыке»"), Toast.LENGTH_SHORT).show()
            return
        }
        saving = saving + track.id
        scope.launch {
            val saved = withContext(Dispatchers.IO) { MusicCatalog.download(context, track, System.currentTimeMillis()) }
            saving = saving - track.id
            if (saved != null) {
                vm.addTracks(listOf(saved))
                Toast.makeText(context, t("Сохранено в «Мою музыку» — трек можно поставить в свой PRISM"), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, t("Не удалось скачать трек"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 64.dp,
                bottom = contentPadding.calculateBottomPadding() + if (current != null) 84.dp else 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CatalogSource.entries.forEach { s ->
                            Chip(s.title, active = s == source) { source = s }
                        }
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
                    Text(
                        if (source == CatalogSource.Jamendo) t("Jamendo — независимые музыканты, лицензии Creative Commons. Указывайте автора; треки с NC — только для некоммерческих роликов.")
                        else t("Openverse — открытое аудио под CC0 и CC BY. Для CC BY указывайте автора."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Mist,
                    )
                }
            }
            error?.let { message ->
                item {
                    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(message, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GhostButton(t("Повторить"), onClick = { scope.launch { load(reset = true) } })
                                if (source == CatalogSource.Jamendo) {
                                    GhostButton(t("Открыть Openverse"), onClick = { source = CatalogSource.Openverse })
                                }
                            }
                        }
                    }
                }
            }
            if (!loading && error == null && tracks.isEmpty()) {
                item {
                    Text(
                        t("Ничего не нашлось. Попробуйте другое слово или жанр."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Mist,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            items(tracks, key = { it.id }) { track ->
                CatalogRow(
                    track = track,
                    playing = current?.id == track.id && playing,
                    saving = track.id in saving,
                    saved = state.tracks.any { it.sourceUrl.isNotEmpty() && it.sourceUrl == track.pageUrl },
                    onPlay = { play(track) },
                    onSave = { save(track) },
                    onOpen = {
                        val url = track.pageUrl.ifBlank { track.licenseUrl }
                        if (url.isNotBlank()) runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    },
                    onLicense = {
                        if (track.licenseUrl.isNotBlank()) runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(track.licenseUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    },
                )
            }
            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Palette.Lilac, modifier = Modifier.size(28.dp))
                    }
                }
            } else if (more && error == null) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        GhostButton(t("Ещё треки"), onClick = { scope.launch { load(reset = false) } })
                    }
                }
            }
        }

        current?.let { track ->
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = contentPadding.calculateBottomPadding())
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(22.dp), strong = true)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CatalogCover(track, Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.labelLarge, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(track.artist, track.license).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Palette.Plum)) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .height(3.dp)
                                .background(Brush.horizontalGradient(listOf(Palette.Lilac, Palette.Sky))),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Palette.Orchid, Palette.Ocean)))
                        .clickable { play(track) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (buffering) CircularProgressIndicator(color = Palette.Ink, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    else Glyph(if (playing) LineIcon.Pause else LineIcon.Play, Palette.Ink, size = 20.dp)
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).clickable {
                        player.stop()
                        current = null
                    },
                    contentAlignment = Alignment.Center,
                ) { Glyph(LineIcon.Close, Palette.Mist, size = 18.dp, description = LineIcon.Close.label) }
            }
        }
    }
}

@Composable
internal fun CatalogCover(track: CatalogTrack, modifier: Modifier) {
    val image by produceState<ImageBitmap?>(null, track.imageUrl) {
        value = withContext(Dispatchers.IO) { MusicCatalog.cover(track.imageUrl)?.asImageBitmap() }
    }
    Box(
        modifier.background(Brush.linearGradient(listOf(Palette.Violet, Palette.Ocean))),
        contentAlignment = Alignment.Center,
    ) {
        val img = image
        if (img != null) Image(img, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Glyph(LineIcon.Music, Palette.Ink, size = 20.dp)
    }
}

@Composable
private fun CatalogRow(
    track: CatalogTrack,
    playing: Boolean,
    saving: Boolean,
    saved: Boolean,
    onPlay: () -> Unit,
    onSave: () -> Unit,
    onOpen: () -> Unit,
    onLicense: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                CatalogCover(track, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Palette.Void.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) { Glyph(if (playing) LineIcon.Pause else LineIcon.Play, Palette.Ink, size = 14.dp) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleMedium, color = if (playing) Palette.Lilac else Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(track.artist, if (track.durationMs > 0) durationTitle(track.durationMs) else "").filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (track.license.isNotBlank()) {
                    Text(
                        track.license,
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.Lilac,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Palette.Plum.copy(alpha = 0.7f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Box {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).clickable { menu = true },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        saving -> CircularProgressIndicator(color = Palette.Lilac, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        saved -> Glyph(LineIcon.Check, Palette.Lilac, size = 20.dp)
                        else -> Glyph(LineIcon.More, Palette.Mist, size = 20.dp)
                    }
                }
                MemoryMenu(expanded = menu, onDismiss = { menu = false }) {
                    MenuRow(if (playing) LineIcon.Pause else LineIcon.Play, if (playing) t("Пауза") else t("Слушать")) { menu = false; onPlay() }
                    if (track.downloadable && !saved) {
                        MenuRow(LineIcon.Music, t("В «Мою музыку»")) { menu = false; onSave() }
                    }
                    MenuRow(LineIcon.Link, tf("Страница на %s", track.source.title)) { menu = false; onOpen() }
                    if (track.licenseUrl.isNotBlank()) {
                        MenuRow(LineIcon.Info, tf("Лицензия: %s", track.license.ifBlank { "CC" })) { menu = false; onLicense() }
                    }
                }
            }
        }
    }
}
