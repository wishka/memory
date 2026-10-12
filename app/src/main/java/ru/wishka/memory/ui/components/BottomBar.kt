package ru.wishka.memory.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.wishka.memory.ui.Tab
import ru.wishka.memory.ui.theme.Palette

/**
 * Нижняя панель: парящая «капсула» с шестью вкладками —
 * Лента · Истории · PRISM · Чаты · Группы · Профиль.
 *
 * Почти непрозрачная: под ней прокручивается лента, и сквозь слишком
 * прозрачное стекло текст записей мешал читать значки. Подпись видна
 * только у выбранной вкладки — шесть подписей на узком экране не
 * помещаются, а значки и так узнаваемы. Выбранная вкладка светится сиренью, под ней
 * короткая светящаяся черта. На «Чатах» — счётчик непрочитанных.
 */
@Composable
fun BottomBar(selected: Tab, unread: Int, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Palette.Night.copy(alpha = 0.92f))
            .glass(RoundedCornerShape(28.dp), strong = true)
            .height(68.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { tab ->
            val active = tab == selected
            val tint by animateColorAsState(if (active) Palette.Lilac else Palette.Mist, label = "tint")
            val glow by animateFloatAsState(if (active) 1f else 0f, label = "glow")
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(tab) },
                    )
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // Мягкое свечение за значком выбранной вкладки
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .alpha(glow)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(listOf(Palette.Orchid.copy(alpha = 0.45f), Color.Transparent))
                            ),
                    )
                    Glyph(
                        when (tab) {
                            Tab.Feed -> LineIcon.Feed
                            Tab.Stories -> LineIcon.Stories
                            Tab.Prism -> LineIcon.Prism
                            Tab.Chats -> LineIcon.Chats
                            Tab.Groups -> LineIcon.Groups
                            Tab.Together -> LineIcon.Together
                            Tab.Profile -> LineIcon.Profile
                        },
                        tint,
                    )
                    if (tab == Tab.Chats && unread > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(start = 18.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Palette.Orchid, Palette.Sky)))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text("$unread", color = Palette.Ink, fontSize = 10.sp)
                        }
                    }
                }
                if (active) {
                    Text(
                        tab.title.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .width(18.dp)
                        .height(2.dp)
                        .alpha(glow)
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(Palette.Lilac, Palette.Sky))),
                )
            }
        }
    }
}
