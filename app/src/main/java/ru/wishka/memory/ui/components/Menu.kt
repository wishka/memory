package ru.wishka.memory.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import ru.wishka.memory.ui.theme.Palette

/**
 * Всплывающее меню в стиле приложения: тёмно-сливовое стекло со
 * скруглёнными углами и сиренево-синей кромкой — вместо стандартного
 * чёрного прямоугольника Material.
 */
@Composable
fun MemoryMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        containerColor = Palette.Night,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(listOf(Palette.Lilac.copy(alpha = 0.45f), Palette.Sky.copy(alpha = 0.18f))),
        ),
        content = content,
    )
}
