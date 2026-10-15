package ru.wishka.memory.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.wishka.memory.data.User
import ru.wishka.memory.ui.theme.Inter
import ru.wishka.memory.ui.theme.Palette
import ru.wishka.memory.ui.theme.ThemeState
import kotlin.random.Random

/**
 * Фон всех экранов: глубокий тёмно-синий, два размытых свечения —
 * фиолетовое сверху справа и синее снизу слева — и редкие звёзды.
 * Звёзды неподвижны и одинаковы при каждом запуске: мерцающий фон
 * отвлекал бы от ленты.
 */
@Composable
fun CosmicBackground(modifier: Modifier = Modifier) {
    val stars = remember {
        val rnd = Random(42)
        List(70) { Triple(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat()) }
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Palette.Night, Palette.Void, Palette.Void)))
        val light = ThemeState.light
        drawRect(
            Brush.radialGradient(
                listOf(Palette.Orchid.copy(alpha = 0.32f), Color.Transparent),
                center = Offset(size.width * 0.92f, size.height * 0.06f),
                radius = size.width * 0.9f,
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(Palette.Ocean.copy(alpha = 0.30f), Color.Transparent),
                center = Offset(size.width * 0.05f, size.height * 0.88f),
                radius = size.width * 1.0f,
            )
        )
        // Днём звёзд не видно
        if (!light) stars.forEach { (x, y, s) ->
            drawCircle(
                Palette.Ink.copy(alpha = 0.15f + s * 0.45f),
                radius = 0.6f + s * 1.3f,
                center = Offset(x * size.width, y * size.height),
            )
        }
    }
}

/** Стекло: полупрозрачная сливовая заливка и тонкая сиренево-синяя кромка. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(22.dp), strong: Boolean = false): Modifier =
    this
        .clip(shape)
        // На светлом фоне стекло — почти белое, иначе карточки сливаются с фоном
        .background(Palette.Plum.copy(alpha = if (ThemeState.light) (if (strong) 0.95f else 0.72f) else if (strong) 0.7f else 0.38f))
        .border(
            1.dp,
            Brush.linearGradient(listOf(Palette.Lilac.copy(alpha = 0.38f), Palette.Sky.copy(alpha = 0.12f))),
            shape,
        )

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.glass(shape), content = content)
}

/** Градиент аватара по оттенку человека: от сирени через фиолетовый к синему. */
fun avatarBrush(hue: Float): Brush {
    val a = lerp(Palette.Lilac, Palette.Sky, hue)
    val b = lerp(Palette.Violet, Palette.Ocean, 1f - hue)
    return Brush.linearGradient(listOf(a, b))
}

/**
 * Аватар: градиентный кружок с инициалами. ring — светящееся кольцо
 * (свой профиль); online — точка «в сети».
 */
@Composable
fun Avatar(user: User, size: Dp = 44.dp, ring: Boolean = false, showOnline: Boolean = false) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size)
                .then(
                    if (ring) Modifier.border(
                        1.5.dp,
                        Brush.sweepGradient(listOf(Palette.Lilac, Palette.Sky, Palette.Orchid, Palette.Lilac)),
                        CircleShape,
                    ) else Modifier
                )
                .padding(if (ring) 5.dp else 0.dp)
                .clip(CircleShape)
                .background(avatarBrush(user.hue)),
            contentAlignment = Alignment.Center,
        ) {
            // Своё фото — поверх градиента; пока читается, видны буквы
            val photo = if (user.avatar.isNotEmpty()) {
                rememberThumbnail(
                    ru.wishka.memory.data.Attachment(user.avatar, ru.wishka.memory.data.MediaKind.Photo, user.avatar),
                    maxSide = 512,
                )
            } else null
            if (photo != null) {
                androidx.compose.foundation.Image(
                    photo,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                Text(
                    user.initials,
                    color = Palette.Ink,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (size.value * 0.34f).sp,
                )
            }
        }
        if (showOnline && user.online) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.26f)
                    .clip(CircleShape)
                    .background(Palette.Void)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Palette.Sky),
            )
        }
    }
}

/** Заголовок экрана — тонкий широкий Unbounded капслоком, как в референсах. */
@Composable
fun ScreenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier.semantics(mergeDescendants = true) { heading() }) {
        Text(title.uppercase(), style = MaterialTheme.typography.displaySmall, color = Palette.Ink)
        if (subtitle != null) {
            Text(
                subtitle.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Mist,
            )
        }
    }
}

/** Тонкая разделительная линия с затуханием к краям. */
@Composable
fun FadeLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Palette.Lilac.copy(alpha = 0.35f), Color.Transparent)
                )
            ),
    )
}
