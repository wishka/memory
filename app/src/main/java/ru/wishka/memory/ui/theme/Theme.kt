package ru.wishka.memory.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/** Только тёмная тема: приложение задумано «ночным», светлого варианта нет. */
private val MemoryColors = darkColorScheme(
    primary = Palette.Lilac,
    onPrimary = Palette.Void,
    primaryContainer = Palette.Violet,
    onPrimaryContainer = Palette.Ink,
    secondary = Palette.Sky,
    onSecondary = Palette.Void,
    background = Palette.Void,
    onBackground = Palette.Ink,
    surface = Palette.Night,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Plum,
    onSurfaceVariant = Palette.Mist,
    outline = Palette.Line,
    error = androidx.compose.ui.graphics.Color(0xFFE58FB0),
    onError = Palette.Void,
)

@Composable
fun MemoryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MemoryColors,
        typography = MemoryTypography,
        content = content,
    )
}
