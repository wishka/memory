package ru.wishka.memory.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ru.wishka.memory.R

/**
 * Два шрифта, как в референсах: широкий тонкий Unbounded — для заголовков
 * и слова MEMORY, спокойный Inter — для текста. Оба под лицензией OFL
 * (docs/licenses), с кириллицей.
 */
val Unbounded = FontFamily(
    Font(R.font.unbounded_extralight, FontWeight.ExtraLight),
    Font(R.font.unbounded_regular, FontWeight.Normal),
)

val Inter = FontFamily(
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

val MemoryTypography = Typography(
    // Заголовок экрана: «ЛЕНТА», «ЧАТЫ», «ПРОФИЛЬ»
    displaySmall = TextStyle(
        fontFamily = Unbounded, fontWeight = FontWeight.ExtraLight,
        fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = 0.06.em,
    ),
    headlineSmall = TextStyle(
        fontFamily = Unbounded, fontWeight = FontWeight.ExtraLight,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.04.em,
    ),
    titleMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, letterSpacing = 0.02.em,
    ),
    // Подписи вкладок и мелкие метки — капслоком с разрядкой
    labelSmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 10.sp, letterSpacing = 0.14.em,
    ),
)
