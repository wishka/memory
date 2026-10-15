package ru.wishka.memory.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import ru.wishka.memory.i18n.L10n

private fun scheme(light: Boolean): ColorScheme {
    val p = if (light) Palette.Light else Palette.Dark
    val error = if (light) androidx.compose.ui.graphics.Color(0xFFB3266F) else androidx.compose.ui.graphics.Color(0xFFE58FB0)
    return if (light) lightColorScheme(
        primary = p.lilac, onPrimary = p.night, primaryContainer = p.violet, onPrimaryContainer = p.ink,
        secondary = p.sky, onSecondary = p.night, background = p.void, onBackground = p.ink,
        surface = p.night, onSurface = p.ink, surfaceVariant = p.plum, onSurfaceVariant = p.mist,
        outline = p.line, error = error, onError = p.night,
    ) else darkColorScheme(
        primary = p.lilac, onPrimary = p.void, primaryContainer = p.violet, onPrimaryContainer = p.ink,
        secondary = p.sky, onSecondary = p.void, background = p.void, onBackground = p.ink,
        surface = p.night, onSurface = p.ink, surfaceVariant = p.plum, onSurfaceVariant = p.mist,
        outline = p.line, error = error, onError = p.void,
    )
}

/**
 * Тема приложения. Размер текста из настроек умножает системный: все
 * размеры в sp растут вместе, вёрстка в dp остаётся прежней.
 */
@Composable
fun MemoryTheme(content: @Composable () -> Unit) {
    // Тема системы — в ThemeState: Palette читает его при каждом обращении
    val systemDark = isSystemInDarkTheme()
    LaunchedEffect(systemDark) { ThemeState.systemDark = systemDark }
    val base = LocalDensity.current
    val scaled = Density(base.density, base.fontScale * L10n.textScale)
    CompositionLocalProvider(LocalDensity provides scaled) {
        MaterialTheme(
            colorScheme = scheme(ThemeState.light),
            typography = MemoryTypography,
            content = content,
        )
    }
}
