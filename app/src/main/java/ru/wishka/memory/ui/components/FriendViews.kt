package ru.wishka.memory.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.FriendState
import ru.wishka.memory.ui.theme.Palette

/**
 * Кнопка дружбы: «Добавить в друзья», «Заявка отправлена» (нажатие —
 * отозвать), «Принять заявку» или «В друзьях» (нажатие — спросить
 * «Удалить из друзей?»). onToggle — AppViewModel.toggleFriend.
 */
@Composable
fun FriendButton(state: FriendState, name: String, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    var confirm by remember { mutableStateOf(false) }
    when (state) {
        FriendState.None -> GlowButton(t("Добавить в друзья"), onClick = onToggle, modifier = modifier)
        FriendState.Requested -> GhostButton(t("Заявка отправлена"), onClick = onToggle, modifier = modifier)
        FriendState.Incoming -> GlowButton(t("Принять заявку"), onClick = onToggle, modifier = modifier)
        FriendState.Friends -> GhostButton(t("В друзьях"), onClick = { confirm = true }, modifier = modifier)
    }
    if (confirm) RemoveFriendDialog(name, onDismiss = { confirm = false }, onConfirm = { confirm = false; onToggle() })
}

/** «Удалить из друзей?» — перед тем как дружба прекратится. */
@Composable
fun RemoveFriendDialog(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Night,
        title = { Text(t("Удалить из друзей?"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink) },
        text = {
            Text(
                tf("%s исчезнет из ваших друзей. Снова стать друзьями можно только по новой заявке.", name),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Mist,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(t("Удалить"), color = Palette.Lilac) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Отмена"), color = Palette.Mist) } },
    )
}
