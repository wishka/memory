package ru.wishka.memory.ui.groups

import android.widget.Toast
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.i18n.t
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.MediaFiles
import ru.wishka.memory.ui.components.AttachButton
import ru.wishka.memory.ui.components.PendingPoll
import ru.wishka.memory.ui.components.PollEditorDialog
import ru.wishka.memory.data.PollDraft
import ru.wishka.memory.ui.components.StickerPicker
import ru.wishka.memory.ui.ProfileSection
import ru.wishka.memory.ui.components.GroupCover
import ru.wishka.memory.ui.components.MenuRow
import ru.wishka.memory.ui.components.MusicChooser
import ru.wishka.memory.ui.components.PendingAttachments
import ru.wishka.memory.ui.components.rememberMediaPicker
import ru.wishka.memory.data.Group
import ru.wishka.memory.data.GroupAccent
import ru.wishka.memory.data.Product
import ru.wishka.memory.data.categories
import ru.wishka.memory.ui.components.BackdropView
import androidx.compose.runtime.LaunchedEffect
import ru.wishka.memory.data.plural
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassCard
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.Nebula
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.feed.PostCard
import ru.wishka.memory.ui.theme.Palette

/**
 * Страница группы: заставка, описание, вступление, записи участников.
 * Шестерёнка на заставке — настройки (у создателя — правка, заставка,
 * удаление; у участника — уведомления и выход).
 */
@Composable
fun GroupScreen(vm: AppViewModel, state: UiState, group: Group, contentPadding: PaddingValues) {
    val context = LocalContext.current
    var text by rememberSaveable(group.id) { mutableStateOf("") }
    var media by remember(group.id) { mutableStateOf<List<Attachment>>(emptyList()) }
    var choosingMusic by remember { mutableStateOf(false) }
    var choosingGif by remember { mutableStateOf(false) }
    var editingPoll by remember { mutableStateOf(false) }
    var poll by remember { mutableStateOf<PollDraft?>(null) }
    var choosingSticker by remember { mutableStateOf(false) }
    val picker = rememberMediaPicker { picked -> media = (media + picked).take(AppViewModel.MAX_MEDIA) }
    val canPost = group.joined && (!group.onlyOwnerPosts || group.mine)
    val visible = group.posts.filter { it.author.id !in state.blocked }
    val pinned = visible.firstOrNull { it.id == group.pinnedPostId }
    val posts = listOfNotNull(pinned) + visible.filter { it.id != group.pinnedPostId }
    val accent = group.accentColor
    // Витрина вместо записей: видна, когда в ней есть товары или группа ваша
    val hasShop = group.shop.isNotEmpty() || group.mine
    var showShop by rememberSaveable(group.id) { mutableStateOf(false) }
    var category by rememberSaveable(group.id) { mutableStateOf<String?>(null) }
    var editing by remember(group.id) { mutableStateOf<Product?>(null) }
    var adding by remember(group.id) { mutableStateOf(false) }
    val categories = group.shop.categories()
    // Категорию убрали из витрины — фильтр сбрасывается сам
    val activeCategory = category?.takeIf { it in categories }
    val goods = group.shop.filter { activeCategory == null || it.category.trim() == activeCategory }
    // Товар открыли из сообщения — сразу на витрину
    LaunchedEffect(state.openProduct) { if (state.openProduct?.first == group.id) showShop = true }

    Box(Modifier.fillMaxSize()) {
        // Свой фон группы — под всей страницей
        group.backdrop?.let { BackdropView(it, Modifier.fillMaxSize()) }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                // Заставка во всю ширину, под строкой состояния
                Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                    GroupCover(group, Modifier.fillMaxSize())
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Palette.Void)),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = contentPadding.calculateTopPadding(), start = 12.dp, end = 12.dp),
                    ) {
                        RoundGlass(LineIcon.Back) { vm.closeGroup() }
                        Spacer(Modifier.weight(1f))
                        if (group.joined) RoundGlass(LineIcon.Gear) { vm.openGroupSettings() }
                    }
                    Column(modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text(group.name.uppercase(), style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
                        Text(
                            "${t(group.topic).uppercase()} · ${plural(group.members, "участник", "участника", "участников").uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.Mist,
                        )
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (group.description.isNotBlank()) {
                        Text(group.description, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
                    }
                    if (!group.joined) AccentButton(t("Вступить"), accent, onClick = { vm.toggleGroup(group.id) })
                }
            }
            if (!group.info.isEmpty) item { GroupInfoCard(group.info, accent, Modifier.padding(horizontal = 16.dp).fillMaxWidth()) }
            if (hasShop) item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TabPill(t("Записи"), !showShop, accent) { showShop = false }
                    TabPill(if (group.shop.isEmpty()) t("Витрина") else t("Витрина") + " · " + group.shop.size, showShop, accent) { showShop = true }
                }
            }

            if (showShop && hasShop) {
                if (group.mine) item {
                    AccentButton(t("Добавить товар"), accent, Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { adding = true }
                }
                item { CategoryChips(categories, activeCategory, accent) { category = it } }
                if (goods.isEmpty()) item {
                    Hint(if (group.mine) t("Витрина пуста. Добавьте товары с фото и ценой — покупатели напишут вам в чат.") else t("Товаров пока нет."))
                }
                goods.chunked(2).forEach { row ->
                    item(key = "p" + row.first().id) {
                        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { p -> ProductCard(p, accent, Modifier.weight(1f)) { vm.showProduct(group.id, p.id) } }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            } else {

                when {
                    canPost -> item {
                        GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                            Column {
                                GlassField(text, { text = it.take(2000) }, t("Написать в группу"))
                                poll?.let { p -> PendingPoll(p, onRemove = { poll = null }, modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp).fillMaxWidth()) }
                                PendingAttachments(
                                    media,
                                    onRemove = { item -> media = media - item; MediaFiles.delete(item) },
                                    modifier = Modifier.padding(bottom = 10.dp),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AttachButton(
                                        picker, documents = true, size = 40.dp,
                                        onMusic = { choosingMusic = true },
                                        onSticker = { choosingSticker = true },
                                        onGif = { choosingGif = true },
                                        onPoll = { editingPoll = true },
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        if (media.isEmpty()) t("Фото, видео, документы, музыка")
                                        else plural(media.size, "вложение", "вложения", "вложений"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Palette.Mist,
                                        modifier = Modifier.weight(1f),
                                    )
                                    GlowButton(t("Опубликовать"), enabled = text.isNotBlank() || media.isNotEmpty() || poll != null, onClick = {
                                        vm.postInGroup(group.id, text, media, poll)
                                        text = ""
                                        media = emptyList()
                                        poll = null
                                    })
                                }
                            }
                        }
                    }
                    group.joined -> item { Hint(t("В этой группе пишет только её создатель.")) }
                    else -> item { Hint(t("Вступите, чтобы писать в группу.")) }
                }

                item {
                    Text(
                        if (posts.isEmpty()) t("ЗАПИСЕЙ ПОКА НЕТ") else tf("ЗАПИСИ · %s", posts.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.Mist,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
                items(posts, key = { it.id }) { post ->
                    val mine = post.author.id == state.me.id
                    PostCard(
                        post,
                        onLike = { vm.toggleGroupPostLike(group.id, post.id) },
                        onVote = { vm.votePost(post.id, it) },
                        onClosePoll = if (vm.isMine(post)) ({ vm.closePostPoll(post.id) }) else null,
                        onReport = if (vm.isMine(post)) null else ({ vm.reportPost(post) }),
                        onAuthor = { vm.openPerson(post.author.id) },
                        onComments = { vm.openComments(post.id) },
                        onOpenMedia = vm::openMedia,
                        pinned = post.id == group.pinnedPostId,
                        menu = if (!group.mine && !mine) null else { close ->
                            if (group.mine) {
                                MenuRow(LineIcon.Pushpin, if (post.id == group.pinnedPostId) t("Открепить") else t("Закрепить")) {
                                    close(); vm.togglePinGroupPost(group.id, post.id)
                                }
                            }
                            MenuRow(LineIcon.Trash, t("Удалить запись"), color = Palette.PearlPink) {
                                close()
                                post.media.forEach { MediaFiles.delete(it) }
                                vm.deleteGroupPost(group.id, post.id)
                            }
                        },
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    // Товар: из витрины или из сообщения
    val open = state.openProduct?.takeIf { it.first == group.id }?.let { (_, id) -> group.shop.firstOrNull { it.id == id } }
    open?.let { product ->
        ProductSheet(
            product, accent, mine = group.mine,
            onClose = vm::closeProduct,
            onAsk = if (group.mine || group.ownerId == 0) null else ({
                if (vm.askSeller(group.id, product.id) == null) Toast.makeText(context, t("Не удалось написать продавцу"), Toast.LENGTH_SHORT).show()
                else vm.closeProduct()
            }),
            askBlockedReason = if (!group.mine && state.isBlocked(group.ownerId)) t("Вы заблокировали продавца — написать ему нельзя.") else null,
            onMove = if (group.mine && group.shop.size > 1) ({ d -> vm.moveProduct(group.id, product.id, d) }) else null,
            onEdit = { editing = product },
            onDelete = { vm.deleteProduct(group.id, product.id); vm.closeProduct() },
            onOpenPhoto = vm::openMedia,
        )
    }
    if (adding || editing != null) {
        ProductEditor(
            initial = editing, categories = categories, accent = accent,
            onSave = { vm.saveProduct(group.id, it) },
            onClose = { adding = false; editing = null },
        )
    }

    if (choosingSticker || choosingGif) {
        StickerPicker(
            state.stickers,
            onPick = { media = (media + it).take(AppViewModel.MAX_MEDIA) },
            onDismiss = { choosingSticker = false; choosingGif = false },
            onOpenStudio = { vm.openProfileSection(ProfileSection.Stickers) },
            packs = state.stickerPacks,
            recentGifs = state.recentGifs,
            gifs = true,
            startOnGifs = choosingGif,
        )
    }
    if (editingPoll) PollEditorDialog(onDismiss = { editingPoll = false }, onCreate = { poll = it })
    if (choosingMusic) {
        MusicChooser(
            tracks = state.tracks,
            onPicked = { picked -> media = (media + picked).take(AppViewModel.MAX_MEDIA) },
            onDismiss = { choosingMusic = false },
        )
    }
}

/** Вкладка «Записи» / «Витрина» в цвете группы. */
@Composable
private fun TabPill(text: String, active: Boolean, accent: GroupAccent, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (active) Palette.Void else accent.color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(if (active) Modifier.background(accent.color) else Modifier.glass(RoundedCornerShape(50)))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, modifier = Modifier.padding(horizontal = 24.dp))
}

/** Круглая стеклянная кнопка на заставке. */
@Composable
internal fun RoundGlass(icon: LineIcon, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .glass(CircleShape, strong = true)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(icon, Palette.Ink) }
}
