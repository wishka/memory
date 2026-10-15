package ru.wishka.memory.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.MediaFiles
import ru.wishka.memory.data.Post
import ru.wishka.memory.i18n.t
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.MentionSuggestions
import ru.wishka.memory.ui.components.PendingAttachments
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.profile.RoundIconButton
import ru.wishka.memory.ui.theme.Palette

/**
 * Правка своей записи: текст и вложения (лишние можно убрать). Чек-ин и
 * опрос остаются как были. После сохранения у записи пометка «изменено».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPostSheet(vm: AppViewModel, post: Post) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by rememberSaveable(post.id) { mutableStateOf(post.text) }
    var media by remember(post.id) { mutableStateOf(post.media) }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(
        onDismissRequest = vm::cancelEdit,
        sheetState = sheet,
        containerColor = Palette.Night,
        contentColor = Palette.Ink,
    ) {
        Column(
            modifier = Modifier.navigationBarsPadding().imePadding().padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                t("Изменить запись").uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Mist,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            GlassField(
                text, { text = it.take(2000); error = null }, t("Текст записи"),
                maxLines = 10,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().glass(RoundedCornerShape(18.dp)),
            )
            MentionSuggestions(text, { text = it.take(2000) }, Modifier.padding(horizontal = 16.dp))
            PendingAttachments(media, onRemove = { item -> media = media - item })
            if (post.checkIn != null || post.poll != null) {
                Text(
                    t("Чек-ин и опрос остаются как были."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp))
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(t("Отмена"), onClick = vm::cancelEdit)
                GlowButton(t("Сохранить"), onClick = {
                    val removed = post.media.filter { a -> media.none { it.id == a.id } }
                    error = vm.saveEdit(post.id, text, media)
                    // Убранные фото и видео больше не нужны — их копии удаляются с телефона
                    if (error == null) removed.forEach { MediaFiles.delete(it) }
                })
            }
        }
    }
}

/**
 * Запись, открытая по ссылке: отдельным экраном, с обсуждением и
 * «нравится». Назад — туда, где были.
 */
@Composable
fun PostScreen(vm: AppViewModel, state: UiState, post: Post, contentPadding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(LineIcon.Back, Palette.Ink) { vm.closePost() }
                Spacer(Modifier.width(14.dp))
                Text(t("Запись").uppercase(), style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
            }
        }
        item {
            val mine = post.author.id == state.me.id
            PostCard(
                post,
                onLike = { vm.likeAny(post.id) },
                friend = if (mine) null else state.friendState(post.author.id),
                onFriend = { vm.toggleFriend(post.author.id) },
                onAuthor = { vm.closePost(); vm.openPerson(post.author.id) },
                onComments = { vm.openComments(post.id) },
                onOpenMedia = vm::openMedia,
                onVote = { vm.votePost(post.id, it) },
                onReport = if (mine) null else ({ vm.reportPost(post) }),
            )
        }
    }
}
