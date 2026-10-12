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
import ru.wishka.memory.ui.components.PollCard
import ru.wishka.memory.ui.components.rememberMediaPicker
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.ProfileSection
import ru.wishka.memory.ui.components.CheckInBadge
import ru.wishka.memory.ui.components.PostComposer
import ru.wishka.memory.ui.components.StickerRow
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
import ru.wishka.memory.data.CheckIn
import ru.wishka.memory.data.EchoLine
import ru.wishka.memory.data.InviteAction
import ru.wishka.memory.data.MemoryOfDay
import ru.wishka.memory.data.ShareCardSpec
import ru.wishka.memory.data.memoriesOfDay
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.components.PostMedia
import ru.wishka.memory.ui.components.shareCard

/** Вкладка «Лента»: поле новой записи и записи друзей. */
@Composable
fun FeedScreen(vm: AppViewModel, state: UiState, contentPadding: PaddingValues) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val memories = remember(state.posts) { memoriesOfDay(state.posts, state.me.id) }
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
        item {
            PostComposer(
                stickers = state.stickers,
                templates = state.checkInTemplates,
                onPublish = vm::publish,
                onCreateTemplate = vm::createCheckInTemplate,
                onDeleteTemplate = vm::deleteCheckInTemplate,
                onOpenStudio = { vm.openProfileSection(ProfileSection.Stickers) },
                tracks = state.tracks,
                packs = state.stickerPacks,
                recentGifs = state.recentGifs,
            )
        }
        // «Год назад вы…»: своя запись из этого же дня — пока не закрыли сегодня
        if (memories.isNotEmpty() && state.memorySeen != java.time.LocalDate.now().toString()) {
            item(key = "memory") { MemoryCard(vm, memories.first(), memories.size) }
        }
        items(state.feed, key = { it.id }) { post ->
            val mine = post.author.id == state.me.id
            PostCard(
                post,
                onLike = { vm.toggleLike(post.id) },
                onVote = { vm.votePost(post.id, it) },
                onClosePoll = if (vm.isMine(post)) ({ vm.closePostPoll(post.id) }) else null,
                following = if (mine) null else post.author.id in state.following,
                onFollow = { vm.toggleFollow(post.author.id) },
                onAuthor = { vm.openPerson(post.author.id) },
                onComments = { vm.openComments(post.id) },
                onOpenMedia = vm::openMedia,
                // Своя запись: закрепить в витрине профиля или удалить
                menu = if (!mine) null else { close ->
                    val pinned = post.id in state.profilePins
                    post.checkIn?.let { c ->
                        MenuRow(LineIcon.Share, t("Поделиться карточкой")) {
                            close()
                            shareCard(context, scope, vm, checkInCard(c, state), "checkin-${post.id}", InviteAction.App)
                        }
                    }
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

/** Воспоминание дня: запись, чек-ин или фото, и сколько прошло. */
@Composable
private fun MemoryCard(vm: AppViewModel, memory: MemoryOfDay, count: Int) {
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t("В ЭТОТ ДЕНЬ") + " · " + memory.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Lilac)
            memory.post.checkIn?.let { CheckInBadge(it) }
            if (memory.post.text.isNotEmpty()) Text(memory.post.text, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink, maxLines = 4)
            if (memory.post.media.isNotEmpty()) PostMedia(memory.post.media, onOpen = vm::openMedia)
            if (count > 1) Text(tf("И ещё воспоминаний: %s", count - 1), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlowButton(t("Все записи"), onClick = { vm.openProfileSection(ru.wishka.memory.ui.ProfileSection.Posts) })
                GhostButton(t("Скрыть до завтра"), onClick = vm::dismissMemory)
            }
        }
    }
}

/** Карточка-картинка чек-ина: что, оценка, темп и что изменилось на полке. */
fun checkInCard(c: CheckIn, state: UiState): ShareCardSpec {
    val lines = buildList {
        if (c.rating > 0) add(EchoLine("⭐", "★".repeat(c.rating) + "☆".repeat(5 - c.rating)))
        if (c.amountTitle.isNotEmpty()) add(EchoLine("📏", c.amountTitle))
        if (c.paceTitle.isNotEmpty()) add(EchoLine("⚡", tf("Темп: %s", c.paceTitle)))
        if (c.statusTitle.isNotEmpty()) add(EchoLine("📖", c.statusTitle))
    }
    return ShareCardSpec(
        kicker = t("Чек-ин"), emoji = c.emoji, title = c.subject.ifEmpty { c.displayTitle },
        subtitle = c.displayTitle, lines = lines, author = "${state.me.name} · @${state.me.handle}",
    )
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
    /** Голос в опросе записи; null — опрос только для чтения. */
    onVote: ((Int) -> Unit)? = null,
    /** Завершить опрос — только у автора; null — кнопки нет. */
    onClosePoll: (() -> Unit)? = null,
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
            post.checkIn?.let { CheckInBadge(it) }
            if (post.text.isNotEmpty()) {
                Text(post.text, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
            }
            post.poll?.let { poll -> PollCard(poll, onVote = { onVote?.invoke(it) }, modifier = Modifier.fillMaxWidth(), onClose = onClosePoll) }
            PostMedia(post.media.filter { it.kind != MediaKind.Document && !it.sticker }, onOpen = onOpenMedia)
            StickerRow(post.media.filter { it.sticker })
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
