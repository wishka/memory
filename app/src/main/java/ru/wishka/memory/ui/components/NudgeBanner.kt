package ru.wishka.memory.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.wishka.memory.data.Nudge
import ru.wishka.memory.data.NudgeKind
import ru.wishka.memory.ui.theme.Palette

/**
 * Уведомление, пока приложение открыто: стеклянная плашка сверху.
 * Нажатие — туда, о чём оно; сама уходит через пять секунд.
 */
@Composable
fun NudgeBanner(nudge: Nudge, onOpen: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val appear = remember(nudge) { Animatable(0f) }
    LaunchedEffect(nudge) {
        appear.animateTo(1f, tween(260))
        delay(5_000)
        appear.animateTo(0f, tween(200))
        onDismiss()
    }
    Row(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * -40.dp.toPx()
            }
            .clip(RoundedCornerShape(22.dp))
            .background(Palette.Night.copy(alpha = 0.92f))
            .glass(RoundedCornerShape(22.dp), strong = true)
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Palette.Orchid, Palette.Ocean))),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(
                when (nudge.kind) {
                    NudgeKind.Friends -> LineIcon.Pin
                    NudgeKind.Groups -> LineIcon.Groups
                    NudgeKind.Reactions -> LineIcon.Heart
                    NudgeKind.Follows -> LineIcon.Profile
                    NudgeKind.Messages -> LineIcon.Chats
                    NudgeKind.Recap -> LineIcon.Award
                    NudgeKind.Prism -> LineIcon.Prism
                },
                Palette.Ink, size = 20.dp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(nudge.title, style = MaterialTheme.typography.labelLarge, color = Palette.Lilac, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(nudge.text, style = MaterialTheme.typography.bodyMedium, color = Palette.Ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) { Glyph(LineIcon.Close, Palette.Mist, size = 14.dp) }
    }
}
