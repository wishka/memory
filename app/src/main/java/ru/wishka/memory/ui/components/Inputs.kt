package ru.wishka.memory.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
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
