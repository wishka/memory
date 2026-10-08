package ru.wishka.memory.ui.reels

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.Reel
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.Avatar
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.compact
import ru.wishka.memory.ui.theme.Palette

/**
 * Вкладка Reels: короткие вертикальные ролики во весь экран, листаются
 * вверх-вниз. Нажатие — пауза, двойное нажатие — «нравится». Играет только
 * ролик на экране: соседние стоят, чтобы не тратить батарею.
 */
@Composable
fun ReelsScreen(vm: AppViewModel, state: UiState, contentPadding: PaddingValues) {
    val reels = state.reels
    val pager = rememberPagerState(pageCount = { reels.size })
    VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
        val reel = reels[page]
        ReelPage(
            reel = reel,
            active = pager.currentPage == page,
            following = if (reel.author.id == state.me.id) null else reel.author.id in state.following,
            contentPadding = contentPadding,
            onLike = { vm.toggleReelLike(reel.id) },
            onAuthor = { vm.openPerson(reel.author.id) },
            onFollow = { vm.toggleFollow(reel.author.id) },
        )
    }
}

@Composable
private fun ReelPage(
    reel: Reel,
    active: Boolean,
    following: Boolean?,
    contentPadding: PaddingValues,
    onLike: () -> Unit,
    onAuthor: () -> Unit,
    onFollow: () -> Unit,
) {
    var paused by remember(reel.id) { mutableStateOf(false) }
    var time by remember(reel.id) { mutableLongStateOf(0L) }
    // Пролистали на другой ролик — этот начнётся сначала, когда вернётесь
    LaunchedEffect(active) { if (!active) { time = 0L; paused = false } }
    LaunchedEffect(active, paused) {
        if (!active || paused) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            time = (time + (now - last)) % reel.durationMs
            last = now
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(reel.id) {
                detectTapGestures(
                    onTap = { paused = !paused },
                    onDoubleTap = { if (!reel.liked) onLike() },
                )
            },
    ) {
        ReelArt(reel.seed, time, Modifier.fillMaxSize())

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
                        .fillMaxWidth(time.toFloat() / reel.durationMs)
                        .height(2.dp)
                        .background(Brush.horizontalGradient(listOf(Palette.Lilac, Palette.Sky))),
                )
            }
            Text(
                "REELS",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Ink,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        if (paused) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Palette.Void.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) { Glyph(LineIcon.Reels, Palette.Ink, size = 36.dp) }
        }

        // Кнопки справа
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = contentPadding.calculateBottomPadding()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SideButton(if (reel.liked) LineIcon.HeartFilled else LineIcon.Heart, compact(reel.likes), reel.liked, onLike)
            SideButton(LineIcon.Comment, compact(reel.comments), false, onClick = null)
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
                    Avatar(reel.author, size = 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(reel.author.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                }
                Spacer(Modifier.width(10.dp))
                if (following == false) GlowButton("Подписаться", onClick = onFollow)
                else if (following == true) GhostButton("Вы подписаны", onClick = onFollow)
            }
            Text(reel.caption, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(LineIcon.Music, Palette.Lilac, size = 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    reel.music,
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
private fun SideButton(icon: LineIcon, label: String, active: Boolean, onClick: (() -> Unit)?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(4.dp),
    ) {
        Glyph(icon, if (active) Color(0xFFFF9EDE) else Palette.Ink, size = 30.dp)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Ink, modifier = Modifier.padding(top = 2.dp))
    }
}
