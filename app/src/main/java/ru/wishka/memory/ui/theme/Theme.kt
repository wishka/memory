package ru.wishka.memory.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import ru.wishka.memory.i18n.L10n

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

/**
 * Тема приложения. Размер текста из настроек умножает системный: все
 * размеры в sp растут вместе, вёрстка в dp остаётся прежней.
 */
@Composable
fun MemoryTheme(content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val scaled = Density(base.density, base.fontScale * L10n.textScale)
    CompositionLocalProvider(LocalDensity provides scaled) {
        MaterialTheme(
            colorScheme = MemoryColors,
            typography = MemoryTypography,
            content = content,
        )
    }
}
