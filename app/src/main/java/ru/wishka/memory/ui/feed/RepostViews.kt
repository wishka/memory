package ru.wishka.memory.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.Demo
import ru.wishka.memory.data.Post
import ru.wishka.memory.data.SharedPlace
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.Avatar
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.MentionSuggestions
import ru.wishka.memory.ui.components.PostRefCard
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.theme.Palette

/** «📍 Кофейня у моря» под шапкой записи. Нажатие — карта. */
@Composable
fun PlaceLine(place: SharedPlace, onOpen: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClickLabel = t("Показать на карте"), role = Role.Button, onClick = onOpen)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(LineIcon.Pin, Palette.Lilac, size = 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(place.title.ifEmpty { t("Место на карте") }, style = MaterialTheme.typography.labelLarge, color = Palette.Lilac, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** «На фото: Вера, Марк» под снимками. Нажатие — список отмеченных. */
@Composable
fun TaggedLine(userIds: List<Int>, onOpen: () -> Unit) {
    val names = userIds.mapNotNull { Demo.person(it)?.name?.substringBefore(' ') }
    if (names.isEmpty()) return
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClickLabel = t("Кто отмечен"), role = Role.Button, onClick = onOpen)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(LineIcon.PersonTag, Palette.Mist, size = 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            if (names.size <= 2) tf("На фото: %s", names.joinToString(", ")) else tf("На фото: %s и ещё %s", names.take(2).joinToString(", "), names.size - 2),
            style = MaterialTheme.typography.labelLarge, color = Palette.Mist, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Кто отмечен на фото — нажатие открывает страницу человека. */
@Composable
fun TaggedDialog(userIds: List<Int>, onPerson: (Int) -> Unit, meId: Int? = null, onUntagMe: () -> Unit = {}, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Palette.Night,
        title = { Text(t("Отмечены на фото"), color = Palette.Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                userIds.mapNotNull { Demo.person(it) }.forEach { u ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { onPerson(u.id) }.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(u, size = 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(u.name, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(t("Закрыть"), color = Palette.Lilac) } },
        dismissButton = if (meId != null && meId in userIds) {
            { TextButton(onClick = onUntagMe) { Text(t("Убрать отметку с собой"), color = Palette.PearlPink) } }
        } else null,
    )
}

/**
 * Репост: оригинал карточкой и свои слова над ним (по желанию).
 * Внизу — «Отправить в чат», если хочется не в ленту, а кому-то лично.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepostSheet(vm: AppViewModel, post: Post) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by rememberSaveable(post.id) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val ref = remember(post.id) { vm.postRef(post) }
    ModalBottomSheet(onDismissRequest = vm::closeRepost, sheetState = sheet, containerColor = Palette.Night, contentColor = Palette.Ink) {
        Column(
            Modifier.navigationBarsPadding().imePadding().padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(t("Репост").uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Mist, modifier = Modifier.padding(horizontal = 20.dp))
            GlassField(
                text, { text = it.take(2000); error = null }, t("Добавьте пару слов — или оставьте пустым"),
                maxLines = 6,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().glass(RoundedCornerShape(18.dp)),
            )
            MentionSuggestions(text, { text = it.take(2000) }, Modifier.padding(horizontal = 16.dp))
            PostRefCard(ref, onOpen = {}, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth())
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp)) }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                GhostButton(t("В чат"), onClick = { vm.openSendToChat(post.id) })
                Spacer(Modifier.weight(1f))
                GlowButton(t("Опубликовать"), onClick = { error = vm.repost(post.id, text) })
            }
        }
    }
}

/** Кому отправить запись: список переписок и подпись к ней. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendToChatSheet(vm: AppViewModel, state: UiState, post: Post) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by rememberSaveable(post.id) { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    val needle = query.trim().lowercase()
    val chats = state.chatList.filter { !state.isBlocked(it.peer.id) && (needle.isEmpty() || it.peer.name.lowercase().contains(needle)) }
    ModalBottomSheet(onDismissRequest = vm::closeRepost, sheetState = sheet, containerColor = Palette.Night, contentColor = Palette.Ink) {
        Column(
            Modifier.navigationBarsPadding().imePadding().padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(t("Отправить в чат").uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Mist, modifier = Modifier.padding(horizontal = 20.dp))
            GlassField(
                query, { query = it }, t("Поиск по имени"), singleLine = true,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().glass(RoundedCornerShape(50)),
            )
            GlassField(
                text, { text = it.take(1000) }, t("Сообщение (необязательно)"), maxLines = 3,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().glass(RoundedCornerShape(18.dp)),
            )
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                if (chats.isEmpty()) {
                    item { Text(t("Никого не нашли"), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, modifier = Modifier.padding(20.dp)) }
                }
                items(chats, key = { it.id }) { chat ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClickLabel = tf("Отправить: %s", chat.peer.name), role = Role.Button) { vm.sendPostToChat(post.id, chat.id, text) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(chat.peer, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(chat.peer.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, modifier = Modifier.weight(1f))
                        Glyph(LineIcon.Send, Palette.Lilac, size = 20.dp)
                    }
                }
            }
        }
    }
}
