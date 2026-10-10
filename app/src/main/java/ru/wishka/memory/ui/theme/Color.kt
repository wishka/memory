package ru.wishka.memory.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Палитра Memory — фиолетово-синий космос.
 *
 * Семь цветов из референса (от светлой сирени до небесно-синего) плюс
 * два служебных: почти чёрный фон и светлый текст с лиловым оттенком —
 * чистый белый на таком фоне режет глаз.
 */
object Palette {
    val Lilac = Color(0xFFBC92CD)   // 1 — акценты, выбранная вкладка
    val Orchid = Color(0xFF844CAD)  // 2 — градиенты, кнопки
    val Violet = Color(0xFF5F3F96)  // 3 — свои сообщения, заливки
    val Plum = Color(0xFF3C2C59)    // 4 — стеклянные карточки
    val Night = Color(0xFF0F153A)   // 5 — основной фон
    val Ocean = Color(0xFF195699)   // 6 — вторичный акцент
    val Sky = Color(0xFF307EC0)     // 7 — ссылки, индикаторы

    val Void = Color(0xFF07081A)    // глубина фона, заставка
    val Ink = Color(0xFFEDE6F5)     // основной текст
    val Mist = Color(0xFF9C93B8)    // второстепенный текст
    val Line = Color(0x33BC92CD)    // тонкие линии и рамки
    val PearlPink = Color(0xFFFF9EDE) // удаление и прочее необратимое — как на iPhone
}
