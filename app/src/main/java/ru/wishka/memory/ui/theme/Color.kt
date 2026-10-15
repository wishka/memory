package ru.wishka.memory.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Оформление: как в системе, всегда тёмное или всегда светлое. */
enum class ThemeMode(val id: String) {
    System("system"), Dark("dark"), Light("light");

    companion object {
        fun of(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: System
    }
}

/**
 * Какая тема сейчас. Значения — состояние Compose: поменяли — экраны
 * перерисовываются сами, потому что Palette читает их при каждом обращении.
 *
 * forceDark — тёмная поверх всего, что всегда ночное: заставка и вход
 * (ночное небо), PRISM, эфир, звонок, просмотр фото на весь экран.
 */
object ThemeState {
    var mode by mutableStateOf(ThemeMode.System)
    var systemDark by mutableStateOf(true)
    /** Тёмная, пока на экране заставка, вход или знакомство — ночное небо. */
    var stageDark by mutableStateOf(false)
    /** Тёмная, пока открыто то, что всегда ночное: PRISM, эфир, звонок, история, фото на весь экран. */
    var overlayDark by mutableStateOf(false)

    val forceDark: Boolean get() = stageDark || overlayDark

    /** Выбор человека без временного «тёмного поверх» — для настроек и строки состояния. */
    val chosenLight: Boolean
        get() = when (mode) {
            ThemeMode.System -> !systemDark
            ThemeMode.Light -> true
            ThemeMode.Dark -> false
        }

    val light: Boolean
        get() = !forceDark && when (mode) {
            ThemeMode.System -> !systemDark
            ThemeMode.Light -> true
            ThemeMode.Dark -> false
        }
}

/** Набор цветов одной темы — названия те же, что у Palette. */
class PaletteColors(
    val lilac: Color, val orchid: Color, val violet: Color, val plum: Color,
    val night: Color, val ocean: Color, val sky: Color,
    val void: Color, val ink: Color, val mist: Color, val line: Color, val pearlPink: Color,
)

/**
 * Палитра Memory — фиолетово-синий космос.
 *
 * Семь цветов из референса (от светлой сирени до небесно-синего) плюс
 * два служебных: почти чёрный фон и светлый текст с лиловым оттенком —
 * чистый белый на таком фоне режет глаз.
 *
 * Светлая тема — «рассвет» той же гаммы: заливки (кнопки, свои
 * сообщения, стекло) — пастельные, текст и значки — тёмные. Названия
 * цветов значат одно и то же в обеих темах: Ink — текст, Mist —
 * второстепенный текст, Orchid/Ocean — градиент кнопок, Lilac — акцент.
 */
object Palette {
    val Dark = PaletteColors(
        lilac = Color(0xFFBC92CD),   // 1 — акценты, выбранная вкладка
        orchid = Color(0xFF844CAD),  // 2 — градиенты, кнопки
        violet = Color(0xFF5F3F96),  // 3 — свои сообщения, заливки
        plum = Color(0xFF3C2C59),    // 4 — стеклянные карточки
        night = Color(0xFF0F153A),   // 5 — основной фон
        ocean = Color(0xFF195699),   // 6 — вторичный акцент
        sky = Color(0xFF307EC0),     // 7 — ссылки, индикаторы
        void = Color(0xFF07081A),    // глубина фона, заставка
        ink = Color(0xFFEDE6F5),     // основной текст
        mist = Color(0xFF9C93B8),    // второстепенный текст
        line = Color(0x33BC92CD),    // тонкие линии и рамки
        pearlPink = Color(0xFFFF9EDE), // удаление и прочее необратимое — как на iPhone
    )

    val Light = PaletteColors(
        lilac = Color(0xFF7A47A8),   // акцент — темнее, чтобы читался на светлом
        orchid = Color(0xFFD7B9F0),  // градиенты и кнопки — пастель
        violet = Color(0xFFE6D6F7),  // свои сообщения, заливки
        plum = Color(0xFFFFFFFF),    // стекло — белое
        night = Color(0xFFF7F3FD),   // основной фон
        ocean = Color(0xFFBDD8F5),   // вторичный акцент — пастель
        sky = Color(0xFF2C6CAE),     // ссылки, индикаторы
        void = Color(0xFFEDE6F8),    // глубина фона
        ink = Color(0xFF1F1A33),     // основной текст
        mist = Color(0xFF6A6285),    // второстепенный текст
        line = Color(0x337A47A8),    // линии и рамки
        pearlPink = Color(0xFFC0398C), // удаление — насыщеннее, чтобы читалось
    )

    val current: PaletteColors get() = if (ThemeState.light) Light else Dark

    val Lilac: Color get() = current.lilac
    val Orchid: Color get() = current.orchid
    val Violet: Color get() = current.violet
    val Plum: Color get() = current.plum
    val Night: Color get() = current.night
    val Ocean: Color get() = current.ocean
    val Sky: Color get() = current.sky
    val Void: Color get() = current.void
    val Ink: Color get() = current.ink
    val Mist: Color get() = current.mist
    val Line: Color get() = current.line
    val PearlPink: Color get() = current.pearlPink

    /** Текст и значки поверх фото и видео — светлые в любой теме. */
    val OnMedia: Color get() = Dark.ink
}
