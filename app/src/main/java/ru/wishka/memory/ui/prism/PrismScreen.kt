package ru.wishka.memory.ui.prism

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
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.Prism
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
 * Вкладка PRISM: короткие вертикальные ролики во весь экран, листаются
 * вверх-вниз. Нажатие — пауза, двойное нажатие — «нравится». Справа —
 * «нравится», обсуждение, «поделиться» и «сохранить». Играет только
 * ролик на экране: соседние стоят, чтобы не тратить батарею.
 */
@Composable
fun PrismScreen(vm: AppViewModel, state: UiState, contentPadding: PaddingValues) {
    val prisms = state.prisms
    val context = LocalContext.current
    val pager = rememberPagerState(pageCount = { prisms.size })
    // Открыли из «Сохранённого» — сразу на нужный ролик
    LaunchedEffect(state.prismStartId) {
        val id = state.prismStartId ?: return@LaunchedEffect
        val index = prisms.indexOfFirst { it.id == id }
        if (index >= 0) pager.scrollToPage(index)
        vm.consumePrismStart()
    }
    VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
        val prism = prisms[page]
        PrismPage(
            prism = prism,
            active = pager.currentPage == page,
            following = if (prism.author.id == state.me.id) null else prism.author.id in state.following,
            contentPadding = contentPadding,
            onLike = { vm.togglePrismLike(prism.id) },
            onAuthor = { vm.openPerson(prism.author.id) },
            onFollow = { vm.toggleFollow(prism.author.id) },
            onComments = { vm.openPrismComments(prism.id) },
            onShare = { sharePrism(context, prism) },
            onSave = { vm.togglePrismSave(prism.id) },
        )
    }
}

/** «Поделиться»: системное меню — мессенджеры, почта, копирование ссылки. */
private fun sharePrism(context: android.content.Context, prism: Prism) {
    val text = "${prism.caption}\n— ${prism.author.name} (@${prism.author.handle}) в Memory · PRISM"
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Поделиться").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
private fun PrismPage(
    prism: Prism,
    active: Boolean,
    following: Boolean?,
    contentPadding: PaddingValues,
    onLike: () -> Unit,
    onAuthor: () -> Unit,
    onFollow: () -> Unit,
    onComments: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    var paused by remember(prism.id) { mutableStateOf(false) }
    var time by remember(prism.id) { mutableLongStateOf(0L) }
    // Пролистали на другой ролик — этот начнётся сначала, когда вернётесь
    LaunchedEffect(active) { if (!active) { time = 0L; paused = false } }
    LaunchedEffect(active, paused) {
        if (!active || paused) return@LaunchedEffect
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
        PrismArt(prism.seed, time, Modifier.fillMaxSize())

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
                        .fillMaxWidth(time.toFloat() / prism.durationMs)
                        .height(2.dp)
                        .background(Brush.horizontalGradient(listOf(Palette.Lilac, Palette.Sky))),
                )
            }
            Text(
                "PRISM",
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
            SideButton(if (prism.liked) LineIcon.HeartFilled else LineIcon.Heart, compact(prism.likes), onLike)
            SideButton(LineIcon.Comment, compact(prism.comments), onComments)
            SideButton(LineIcon.Share, "Поделиться", onShare)
            SideButton(if (prism.saved) LineIcon.BookmarkFilled else LineIcon.Bookmark, if (prism.saved) "В закладках" else "Сохранить", onSave)
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
                if (following == false) GlowButton("Подписаться", onClick = onFollow)
                else if (following == true) GhostButton("Вы подписаны", onClick = onFollow)
            }
            Text(prism.caption, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
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
private fun SideButton(icon: LineIcon, label: String, onClick: (() -> Unit)?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(4.dp),
    ) {
        Glyph(icon, Palette.Ink, size = 30.dp)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Ink, modifier = Modifier.padding(top = 2.dp))
    }
}
