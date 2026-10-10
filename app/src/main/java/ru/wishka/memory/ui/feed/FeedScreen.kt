package ru.wishka.memory.ui.feed

import ru.wishka.memory.i18n.t
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ru.wishka.memory.R
import ru.wishka.memory.data.Post
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.MediaFiles
import ru.wishka.memory.data.MediaKind
import ru.wishka.memory.ui.components.MemoryMenu
import ru.wishka.memory.ui.components.CrystalLogo
import ru.wishka.memory.ui.components.MenuRow
import ru.wishka.memory.ui.components.PostFiles
import ru.wishka.memory.data.agoTitle
import ru.wishka.memory.data.minutesSince
import ru.wishka.memory.data.plural
import ru.wishka.memory.ui.components.AttachButton
import ru.wishka.memory.ui.components.PendingAttachments
import ru.wishka.memory.ui.components.PostMedia
import ru.wishka.memory.ui.components.rememberMediaPicker
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.Avatar
import ru.wishka.memory.ui.components.GlassCard
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.LikeHeart
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.Nebula
import ru.wishka.memory.ui.components.ScreenTitle
import ru.wishka.memory.ui.theme.Palette

/** Вкладка «Лента»: поле новой записи и записи друзей. */
@Composable
fun FeedScreen(vm: AppViewModel, state: UiState, contentPadding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScreenTitle(t("Лента"), t("Моменты, которые остаются"), Modifier.weight(1f))
                // Знак Memory: кристалл со стеклом, как слово MEMORY на заставке
                CrystalLogo(size = 56.dp)
            }
        }
        item { Composer(onPublish = vm::publish) }
        items(state.feed, key = { it.id }) { post ->
            val mine = post.author.id == state.me.id
            PostCard(
                post,
                onLike = { vm.toggleLike(post.id) },
                following = if (mine) null else post.author.id in state.following,
                onFollow = { vm.toggleFollow(post.author.id) },
                onAuthor = { vm.openPerson(post.author.id) },
                onComments = { vm.openComments(post.id) },
                onOpenMedia = vm::openMedia,
                // Своя запись: закрепить в витрине профиля или удалить
                menu = if (!mine) null else { close ->
                    val pinned = post.id in state.profilePins
                    MenuRow(LineIcon.Pushpin, if (pinned) t("Убрать из витрины") else t("Закрепить в профиле")) {
                        close(); vm.toggleProfilePin(post.id)
                    }
                    MenuRow(LineIcon.Trash, t("Удалить запись"), color = Palette.PearlPink) {
                        close()
                        post.media.forEach { MediaFiles.delete(it) }
                        vm.deletePost(post.id)
                    }
                },
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

/**
 * Новая запись: текст, фото и видео из галереи или с камеры. Вложения
 * видны плитками под полем, крестик убирает лишнее.
 */
@Composable
private fun Composer(onPublish: (String, List<Attachment>) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var media by remember { mutableStateOf<List<Attachment>>(emptyList()) }
    val picker = rememberMediaPicker { picked ->
        media = (media + picked).take(AppViewModel.MAX_MEDIA)
    }
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column {
            GlassField(text, { text = it.take(2000) }, t("Чем хочется поделиться?"))
            PendingAttachments(
                media,
                onRemove = { item ->
                    media = media - item
                    MediaFiles.delete(item)
                },
                modifier = Modifier.padding(bottom = 10.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AttachButton(picker, documents = false, size = 40.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    when {
                        media.isNotEmpty() -> plural(media.size, "вложение", "вложения", "вложений")
                        text.isEmpty() -> t("Видят ваши подписчики")
                        else -> "${text.length} / 2000"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                    modifier = Modifier.weight(1f),
                )
                GlowButton(t("Поделиться"), enabled = text.isNotBlank() || media.isNotEmpty(), onClick = {
                    onPublish(text, media)
                    text = ""
                    media = emptyList()
                })
            }
        }
    }
}

/**
 * Карточка записи. following: null — это своя запись (кнопки подписки
 * нет), true / false — подписан ли человек на автора.
 */
@Composable
fun PostCard(
    post: Post,
    onLike: () -> Unit,
    following: Boolean? = null,
    onFollow: () -> Unit = {},
    /** Нажатие на аватар или имя — страница автора; null — не кликается. */
    onAuthor: (() -> Unit)? = null,
    /** Открыть обсуждение; null — значок комментариев не нажимается. */
    onComments: (() -> Unit)? = null,
    /** Фото или видео записи на весь экран. */
    onOpenMedia: (Attachment) -> Unit = {},
    /** Закреплена в группе — метка над текстом. */
    pinned: Boolean = false,
    /** Пункты меню «⋯» (закрепить, удалить…); null — меню нет. */
    menu: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .then(if (onAuthor != null) Modifier.clickable(onClick = onAuthor) else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                Avatar(post.author, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(post.author.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                    Text(
                        "@${post.author.handle} · ${agoTitle(minutesSince(post.createdAt, post.minutesAgo))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Mist,
                    )
                }
                }
                if (following != null) {
                    if (following) GhostButton(t("Вы подписаны"), onClick = onFollow)
                    else GlowButton(t("Подписаться"), onClick = onFollow)
                }
                if (menu != null) {
                    Box {
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape).clickable { menuOpen = true },
                            contentAlignment = Alignment.Center,
                        ) { Glyph(LineIcon.More, Palette.Mist, size = 20.dp) }
                        MemoryMenu(expanded = menuOpen, onDismiss = { menuOpen = false }) {
                            menu { menuOpen = false }
                        }
                    }
                }
            }
            if (pinned) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(LineIcon.Pushpin, Palette.Lilac, size = 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(t("ЗАКРЕПЛЕНО"), style = MaterialTheme.typography.labelSmall, color = Palette.Lilac)
                }
            }
            if (post.text.isNotEmpty()) {
                Text(post.text, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
            }
            PostMedia(post.media.filter { it.kind != MediaKind.Document }, onOpen = onOpenMedia)
            PostFiles(post.media.filter { it.kind == MediaKind.Document })
            post.artSeed?.let { seed ->
                Nebula(
                    seed,
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onLike)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LikeHeart(post.liked, if (post.liked) Palette.Lilac else Palette.Mist, size = 22.dp)
                    Text(
                        "${post.likes}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (post.liked) Palette.Lilac else Palette.Mist,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .then(if (onComments != null) Modifier.clickable(onClick = onComments) else Modifier)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(LineIcon.Comment, Palette.Mist, size = 22.dp)
                    Text(
                        "${post.comments}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Palette.Mist,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}
