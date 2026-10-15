package ru.wishka.memory.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.agoTitle
import ru.wishka.memory.data.minutesSince
import ru.wishka.memory.data.net.NetError
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.FeedPaging
import ru.wishka.memory.ui.NetStatus
import ru.wishka.memory.ui.theme.Palette

/** Что карточкам знать о сети: есть ли она и сколько ушло у каждой отправки. */
class SyncInfo(val status: NetStatus, val progress: (String) -> Float?)

val LocalSync = compositionLocalOf { SyncInfo(NetStatus.Online) { null } }

/** Полоска сверху: «Нет сети» / «Сервер не отвечает» и сколько ждёт отправки. */
@Composable
fun NetBanner(status: NetStatus, waiting: Int, modifier: Modifier = Modifier) {
    if (status == NetStatus.Online) return
    val text = when (status) {
        NetStatus.Offline -> t("Нет сети — показываем сохранённое")
        else -> t("Сервер не отвечает — попробуем ещё")
    } + if (waiting > 0) " · " + tf("ждут отправки: %s", waiting) else ""
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Palette.Void.copy(alpha = 0.85f))
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(if (status == NetStatus.Offline) Palette.PearlPink else Palette.Sky))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.Ink)
    }
}

/** Под своей записью, пока она не на сервере: «Отправляется · 45 %» или «Ждёт сети». */
@Composable
fun SyncLine(owner: String) {
    if (owner.isEmpty()) return
    val sync = LocalSync.current
    val progress = sync.progress(owner)
    val offline = sync.status != NetStatus.Online
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            when {
                offline -> t("Ждёт сети — отправится сама")
                progress != null && progress < 1f -> tf("Отправляется · %s", "${(progress * 100).toInt()}%")
                else -> t("Отправляется…")
            },
            style = MaterialTheme.typography.labelSmall,
            color = Palette.Mist,
        )
        if (progress != null && progress < 1f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)),
                color = Palette.Lilac,
                trackColor = Palette.Line,
            )
        }
    }
}

/**
 * Низ ленты: подгружается старое, «это всё» или «нет сети».
 * onVisible — дошли до низа (подгрузить следующую страницу).
 */
@Composable
fun FeedFooter(paging: FeedPaging, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        when {
            paging.loading -> CircularProgressIndicator(Modifier.size(24.dp), color = Palette.Lilac, strokeWidth = 2.dp)
            paging.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    when (paging.error) {
                        NetError.Kind.Offline -> t("Нет сети — дальше загрузим, когда появится")
                        else -> t("Не загрузилось")
                    } + if (paging.fromCache && paging.cachedAt > 0) " · " + tf("сохранено %s", agoTitle(minutesSince(paging.cachedAt, 0))) else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                )
                Text(
                    t("Повторить"),
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.Lilac,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onRetry).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            paging.end -> Text(t("Вы всё посмотрели ✦"), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
            else -> Spacer(Modifier.height(24.dp))
        }
    }
}
