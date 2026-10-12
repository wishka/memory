package ru.wishka.memory.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ru.wishka.memory.data.CheckInEcho
import ru.wishka.memory.data.Invite
import ru.wishka.memory.data.InviteAction
import ru.wishka.memory.data.ShareCardSpec
import ru.wishka.memory.data.shelfKind
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.ShelfTab
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.theme.Palette

/**
 * Отклик на чек-ин — не конфетти, а то, что изменилось: полка, вклад в
 * общую цель, план недели, что откроется дальше, совет по вкусу.
 */
@Composable
fun EchoDialog(vm: AppViewModel, state: UiState, echo: CheckInEcho) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = vm::dismissEcho) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.Night).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(t("Отмечено ✦"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
            CheckInBadge(echo.checkIn)
            echo.lines.forEach { line ->
                Row(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(line.emoji, fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(line.text, style = MaterialTheme.typography.bodyMedium, color = Palette.Ink)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlowButton(t("Готово"), onClick = vm::dismissEcho)
                GhostButton(t("Поделиться"), onClick = {
                    val c = echo.checkIn
                    val spec = ShareCardSpec(
                        kicker = t("Чек-ин"), emoji = c.emoji, title = c.subject.ifEmpty { c.displayTitle }, subtitle = c.displayTitle,
                        lines = echo.lines.take(4), author = "${state.me.name} · @${state.me.handle}",
                    )
                    shareCard(context, scope, vm, spec, "checkin", InviteAction.App)
                })
                if (shelfKind(echo.checkIn.activity) in setOf("film", "book")) GhostButton(t("Полка"), onClick = {
                    vm.dismissEcho()
                    vm.openShelf(if (shelfKind(echo.checkIn.activity) == "book") ShelfTab.Books else ShelfTab.Films)
                })
            }
        }
    }
}

/** Открыли приложение по чужой ссылке: кто позвал и к чему. */
@Composable
fun InviteDialog(vm: AppViewModel, invite: Invite) {
    Dialog(onDismissRequest = vm::dismissInvite) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.Night).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("✦", fontSize = 32.sp, color = Palette.Lilac)
            Text(
                if (invite.from.isNotEmpty()) tf("%s зовёт вас", invite.from) else t("Вас позвали в Memory"),
                style = MaterialTheme.typography.titleMedium, color = Palette.Ink,
            )
            Text(invite.action.title.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge, color = Palette.Lilac)
            Text(
                if (invite.action == InviteAction.App) t("Отметьте первый фильм, книгу или пробежку — с этого начинается ваша полка.")
                else t("Мы открыли нужный раздел. Пока у Memory нет сервера, сам объект появится, когда друг будет рядом в приложении."),
                style = MaterialTheme.typography.bodyMedium, color = Palette.Mist,
            )
            GlowButton(t("Понятно"), onClick = vm::dismissInvite)
        }
    }
}
