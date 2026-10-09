package ru.wishka.memory.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import ru.wishka.memory.ui.theme.Palette

/** Поле ввода без рамок Material: текст на стекле, курсор сиреневый. */
@Composable
fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    maxLines: Int = 6,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine || password,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
        maxLines = maxLines,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Ink),
        cursorBrush = SolidColor(Palette.Lilac),
        modifier = modifier,
        decorationBox = { inner ->
            Box(modifier = Modifier.padding(padding)) {
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = Palette.Mist)
                }
                inner()
            }
        },
    )
}

/** Кнопка-капсула с градиентом сирень → синий. */
@Composable
fun GlowButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(Palette.Orchid, Palette.Ocean)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Ink)
    }
}

/** Тихая кнопка: только тонкая рамка. */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .glass(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Lilac)
    }
}

/**
 * Таблетка, которая раскрывает список под собой: «Все темы ▾»,
 * «Интересы · 3 ▾». open — список раскрыт, стрелка смотрит вверх.
 */
@Composable
fun DisclosurePill(text: String, open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .glass(RoundedCornerShape(50), strong = active || open)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (active || open) Palette.Ink else Palette.Lilac)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 6.dp))
        Glyph(
            LineIcon.ChevronDown,
            Palette.Lilac,
            size = 18.dp,
            modifier = Modifier.graphicsLayer { rotationZ = if (open) 180f else 0f },
        )
    }
}

/** Метка-капсула: тема, интерес. active — выбрана. */
@Composable
fun Chip(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (active) Palette.Ink else Palette.Lilac,
        modifier = Modifier
            .glass(RoundedCornerShape(50), strong = active)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/**
 * Появление по очереди: элемент номер index проявляется и чуть
 * поднимается через index × STAGGER_MS после показа списка. Так метки
 * тем и интересов раскрываются одна за другой, а не все разом.
 */
@Composable
fun StaggerIn(index: Int, content: @Composable () -> Unit) {
    val progress = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * STAGGER_MS)
        progress.animateTo(1f, androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    androidx.compose.foundation.layout.Box(
        Modifier.graphicsLayer {
            val p = progress.value
            alpha = p
            scaleX = 0.82f + 0.18f * p
            scaleY = 0.82f + 0.18f * p
            translationY = (1f - p) * 10.dp.toPx()
        },
    ) { content() }
}

/** Шаг между появлением соседних меток. */
const val STAGGER_MS = 35L
