package ru.wishka.memory.ui.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.Chat
import ru.wishka.memory.data.Disappear
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.theme.Palette

/** Подпись под именем собеседника: «🔒 шифрование · ⏱ 1 день». "" — ничего не включено. */
fun secretStatus(chat: Chat): String = listOfNotNull(
    if (chat.secret) "🔒 " + t("шифрование") else null,
    if (chat.disappearMs > 0) "⏱ " + Disappear.of(chat.disappearMs).title else null,
).joinToString(" · ")

/** Полоса над перепиской: шифрование включено, код ещё не сверен. */
@Composable
fun SecretBanner(chat: Chat, onOpen: () -> Unit) {
    if (!chat.secret) return
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .glass(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(LineIcon.Lock, Palette.Lilac, size = 18.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            if (chat.verified) t("Сквозное шифрование. Код безопасности сверен.")
            else t("Сквозное шифрование: сообщения читаете только вы двое. Сверьте код безопасности."),
            style = MaterialTheme.typography.bodyMedium,
            color = if (chat.verified) Palette.Mist else Palette.Ink,
        )
    }
}

/**
 * Код безопасности: 60 цифр из ключей обоих. Сверьте с собеседником
 * при встрече или по другому каналу — совпало, значит между вами никого.
 */
@Composable
fun SafetyNumberDialog(chat: Chat, number: String, onVerify: (Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Night,
        title = { Text(t("Код безопасности"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    tf("Сравните код с тем, что видит %s у себя. Совпадает — переписку никто не перехватывает.", chat.peer.name.substringBefore(' ')),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                )
                number.split(' ').chunked(4).forEach { row ->
                    Text(row.joinToString("  "), style = MaterialTheme.typography.titleMedium, color = Palette.Ink, fontFamily = FontFamily.Monospace)
                }
                if (chat.verified) Text("✓ " + t("Сверено"), style = MaterialTheme.typography.bodyMedium, color = Palette.Lilac)
            }
        },
        confirmButton = {
            TextButton(onClick = { onVerify(!chat.verified); onDismiss() }) {
                Text(if (chat.verified) t("Снять отметку") else t("Код совпадает"), color = Palette.Lilac)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Закрыть"), color = Palette.Mist) } },
    )
}

/** Выбор срока исчезающих сообщений. */
@Composable
fun DisappearDialog(current: Disappear, onPick: (Disappear) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Night,
        title = { Text(t("Исчезающие сообщения"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    t("Новые сообщения в этой переписке исчезнут у обоих через выбранное время. Уже отправленные не изменятся."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                )
                Disappear.entries.forEach { d ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(selected = d == current, role = Role.RadioButton) { onPick(d); onDismiss() }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(d.title, style = MaterialTheme.typography.bodyLarge, color = if (d == current) Palette.Ink else Palette.Mist, modifier = Modifier.weight(1f))
                        if (d == current) Glyph(LineIcon.Check, Palette.Lilac, size = 20.dp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Закрыть"), color = Palette.Mist) } },
    )
}
