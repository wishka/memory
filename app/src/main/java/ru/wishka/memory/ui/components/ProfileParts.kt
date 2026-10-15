package ru.wishka.memory.ui.components

import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import ru.wishka.memory.data.User
import ru.wishka.memory.ui.theme.Palette

/** Число и подпись под ним: «128 ДРУЗЕЙ». */
@Composable
fun Stat(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(compact(value), style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
        // Разрядка меньше обычной: «ПОДПИСЧИКИ» помещается в одну строку
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.04.em),
            color = Palette.Mist,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** 1284 → «1,2 тыс.»: длинные числа не распирают колонку. */
fun compact(n: Int): String {
    if (ru.wishka.memory.i18n.L10n.english) return when {
        n >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", n / 1_000_000f)
        n >= 10_000 -> "${n / 1000}K"
        n >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", n / 1000f)
        else -> n.toString()
    }
    return when {
        n >= 1_000_000 -> String.format(java.util.Locale("ru"), t("%.1f млн"), n / 1_000_000f)
        n >= 10_000 -> tf("%s тыс.", n / 1000)
        n >= 1_000 -> String.format(java.util.Locale("ru"), t("%.1f тыс."), n / 1000f)
        else -> n.toString()
    }
}

/** Строка сведения: значок, подпись и значение. Пустое значение не показывается. */
@Composable
fun InfoRow(icon: LineIcon, label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(icon, Palette.Lilac, size = 20.dp)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
    }
}

/** Сведения о человеке: город, день рождения, сайт, «на Memory с». */
@Composable
fun UserInfo(user: User) {
    Column {
        InfoRow(LineIcon.Pin, t("Город"), user.city)
        InfoRow(LineIcon.Gift, t("День рождения"), user.birthday)
        InfoRow(LineIcon.Link, t("Сайт"), user.website)
        InfoRow(LineIcon.Calendar, t("В Memory с"), user.joined)
    }
}

/** Интересы метками-капсулами. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterestChips(interests: List<String>, modifier: Modifier = Modifier) {
    if (interests.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        interests.forEach {
            Text(
                ru.wishka.memory.i18n.t(it),
                style = MaterialTheme.typography.labelLarge,
                color = Palette.Lilac,
                modifier = Modifier
                    .glass(RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
