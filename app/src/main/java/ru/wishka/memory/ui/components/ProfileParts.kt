package ru.wishka.memory.ui.components

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
import ru.wishka.memory.data.User
import ru.wishka.memory.ui.theme.Palette

/** Число и подпись под ним: «128 ПОДПИСЧИКОВ». */
@Composable
fun Stat(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(compact(value), style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Mist)
    }
}

/** 1284 → «1,2 тыс.»: длинные числа не распирают колонку. */
fun compact(n: Int): String = when {
    n >= 1_000_000 -> String.format(java.util.Locale("ru"), "%.1f млн", n / 1_000_000f)
    n >= 10_000 -> "${n / 1000} тыс."
    n >= 1_000 -> String.format(java.util.Locale("ru"), "%.1f тыс.", n / 1000f)
    else -> n.toString()
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
        InfoRow(LineIcon.Pin, "Город", user.city)
        InfoRow(LineIcon.Gift, "День рождения", user.birthday)
        InfoRow(LineIcon.Link, "Сайт", user.website)
        InfoRow(LineIcon.Calendar, "В Memory с", user.joined)
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
                it,
                style = MaterialTheme.typography.labelLarge,
                color = Palette.Lilac,
                modifier = Modifier
                    .glass(RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
