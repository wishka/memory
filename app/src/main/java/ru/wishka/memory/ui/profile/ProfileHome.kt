package ru.wishka.memory.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.MediaKind
import ru.wishka.memory.data.Post
import ru.wishka.memory.data.Prism
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.ProfileSection
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.Avatar
import ru.wishka.memory.ui.components.Chip
import ru.wishka.memory.ui.components.CrystalColors
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassCard
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.GroupCover
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.MediaTile
import ru.wishka.memory.ui.components.Nebula
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.feed.PostCard
import ru.wishka.memory.ui.prism.PrismArt
import ru.wishka.memory.ui.theme.Palette

/** Достижение-кристалл в профиле: за что даётся и получено ли. */
data class Achievement(val title: String, val hint: String, val earned: Boolean)

/** Достижения считаются по тому, что человек уже сделал в Memory. */
fun achievements(state: UiState): List<Achievement> {
    val myId = state.me.id
    val myPrisms = state.prisms.count { it.author.id == myId }
    val likes = state.myPosts.sumOf { it.likes } + state.prisms.filter { it.author.id == myId }.sumOf { it.likes }
    val comments = myComments(state).size
    return listOf(
        Achievement(t("Первый момент"), t("Опубликовать первую запись"), state.myPosts.isNotEmpty()),
        Achievement(t("Режиссёр"), t("Снять свой PRISM"), myPrisms > 0),
        Achievement(t("Летописец"), t("10 записей в ленте"), state.myPosts.size >= 10),
        Achievement(t("Душа компании"), t("Состоять в 5 группах"), state.myGroups.size >= 5),
        Achievement(t("Основатель"), t("Создать свою группу"), state.groups.any { it.mine }),
        Achievement(t("Меломан"), t("10 треков в «Моей музыке»"), state.tracks.size >= 10),
        Achievement(t("Художник"), t("Сделать стикер в «Студии»"), state.stickers.isNotEmpty()),
        Achievement(t("Собеседник"), t("Написать 20 комментариев"), comments >= 20),
        Achievement(t("Звезда"), t("Собрать 100 «нравится»"), likes >= 100),
        Achievement(t("Свой образ"), t("Поставить фото в профиль"), state.me.avatar.isNotEmpty()),
    )
}

/** Моменты своих комментариев — в ленте, группах и PRISM. */
private fun myComments(state: UiState): List<Long> {
    val myId = state.me.id
    val posts = state.posts + state.groups.flatMap { it.posts }
    return posts.flatMap { it.commentList }.filter { it.author.id == myId }.map { it.createdAt } +
        state.prisms.flatMap { it.commentList }.filter { it.author.id == myId }.map { it.createdAt }
}

/** Активность за 14 дней: сколько записей, PRISM и комментариев в каждый день; последний — сегодня. */
fun activity(state: UiState, now: Long = System.currentTimeMillis()): List<Int> {
    val day = 24L * 60 * 60 * 1000
    val today = now / day
    val myId = state.me.id
    val moments = state.myPosts.map { it.createdAt } +
        state.groups.flatMap { it.posts }.filter { it.author.id == myId }.map { it.createdAt } +
        state.prisms.filter { it.author.id == myId }.map { it.createdAt } +
        myComments(state)
    val counts = IntArray(14)
    moments.filter { it > 0 }.forEach { ms ->
        val back = (today - ms / day).toInt()
        if (back in 0..13) counts[13 - back]++
    }
    return counts.toList()
}

@Composable
private fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Mist, modifier = modifier)
}

/**
 * Витрина: закреплённые записи, свои стикеры из «Студии» и «Моя музыка».
 */
@Composable
fun ShowcaseCard(vm: AppViewModel, state: UiState) {
    val pinned = state.profilePins.mapNotNull { id -> state.myPosts.firstOrNull { it.id == id } }
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardTitle(t("Витрина"), Modifier.padding(horizontal = 16.dp))
            if (pinned.isEmpty()) {
                Text(
                    t("Закрепите до трёх своих записей — «⋯» на записи в ленте."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            } else {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pinned.forEach { post -> PinnedTile(post, Modifier.weight(1f)) { vm.openComments(post.id) } }
                    repeat(3 - pinned.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            // Свои стикеры — лентой, в конце «Студия»
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.stickers.take(12), key = { it.id }) { sticker ->
                    StickerImage(
                        sticker,
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .glass(RoundedCornerShape(16.dp))
                            .clickable { vm.openProfileSection(ProfileSection.Stickers) }
                            .padding(6.dp),
                    )
                }
                item {
                    Column(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .glass(RoundedCornerShape(16.dp), strong = true)
                            .clickable { vm.openProfileSection(ProfileSection.Stickers) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Glyph(LineIcon.Plus, Palette.Lilac, size = 20.dp)
                        Text(t("Студия"), style = MaterialTheme.typography.labelSmall, color = Palette.Lilac)
                    }
                }
            }
            // «Моя музыка»: сколько треков и последний добавленный
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.openProfileSection(ProfileSection.Music) }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(Palette.Orchid, Palette.Ocean))),
                    contentAlignment = Alignment.Center,
                ) { Glyph(LineIcon.Music, Palette.Ink, size = 20.dp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    val last = state.tracks.firstOrNull()
                    Text(t("Моя музыка"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                    Text(
                        if (last == null) t("Загрузите свои треки")
                        else tf("Новое: %s", if (last.artist.isBlank()) last.title else "${last.artist} — ${last.title}"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Mist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text("${state.tracks.size}", style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
            }
        }
    }
}

@Composable
private fun PinnedTile(post: Post, modifier: Modifier, onClick: () -> Unit) {
    val visual = post.media.firstOrNull { it.kind != MediaKind.Document && !it.sticker }
    Box(
        modifier = modifier
            .aspectRatio(0.8f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        when {
            visual != null -> MediaTile(visual, Modifier.fillMaxSize(), maxSide = 400)
            post.artSeed != null -> Nebula(post.artSeed, Modifier.fillMaxSize())
            else -> Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Palette.Violet, Palette.Ocean))))
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.45f to androidx.compose.ui.graphics.Color.Transparent, 1f to Palette.Void.copy(alpha = 0.85f))),
        )
        Text(
            post.text.ifEmpty { t("Момент") },
            style = MaterialTheme.typography.labelLarge,
            color = Palette.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
        )
        Glyph(LineIcon.Pushpin, Palette.Ink, size = 14.dp, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
    }
}

/** Активность за две недели и кристаллы-достижения. */
@Composable
fun AchievementsCard(state: UiState) {
    // Подсказка к выбранному кристаллу — в карточке, в стиле приложения
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val list = achievements(state)
    val days = activity(state)
    val myId = state.me.id
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardTitle(t("Активность и достижения"), Modifier.weight(1f))
                Text(tf("%s из %s", list.count { it.earned }, list.size), style = MaterialTheme.typography.labelLarge, color = Palette.Lilac)
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                MiniStat(state.myPosts.size, t("записи"))
                MiniStat(state.prisms.count { it.author.id == myId }, t("PRISM"))
                MiniStat(state.myPosts.sumOf { it.likes } + state.prisms.filter { it.author.id == myId }.sumOf { it.likes }, t("нравится"))
                MiniStat(state.stickers.size, t("стикеры"))
            }
            ActivityBars(days, Modifier.fillMaxWidth().height(56.dp))
            Text(
                if (days.sum() == 0) t("За две недели пока тихо — самое время поделиться моментом.")
                else tf("За две недели: %s", ru.wishka.memory.data.plural(days.sum(), "действие", "действия", "действий")),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Mist,
            )
            // Кристаллы по пять в ряд
            list.chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { a ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected == a.title) Palette.Plum.copy(alpha = 0.7f) else Color.Transparent)
                                .clickable { selected = if (selected == a.title) null else a.title }
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AchievementCrystal(a.earned, Modifier.size(36.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                a.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (a.earned) Palette.Ink else Palette.Mist,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                        }
                    }
                    repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            list.firstOrNull { it.title == selected }?.let { a ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(16.dp), strong = a.earned)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AchievementCrystal(a.earned, Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                        Text(
                            if (a.earned) tf("Получено: %s", a.hint) else tf("Чтобы получить: %s", a.hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (a.earned) Palette.Lilac else Palette.Mist,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.titleLarge, color = Palette.Ink)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Mist)
    }
}

/** Столбики активности: последний — сегодня. */
@Composable
private fun ActivityBars(days: List<Int>, modifier: Modifier) {
    val max = (days.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(modifier) {
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (days.size - 1)) / days.size
        days.forEachIndexed { i, v ->
            val h = if (v == 0) 3.dp.toPx() else (size.height * v / max).coerceAtLeast(6.dp.toPx())
            val x = i * (w + gap)
            drawRoundRect(
                brush = if (v == 0) Brush.linearGradient(listOf(Palette.Plum, Palette.Plum))
                else Brush.verticalGradient(listOf(Palette.Lilac, Palette.Ocean), startY = size.height - h, endY = size.height),
                topLeft = Offset(x, size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 3),
            )
        }
    }
}

/** Кристалл достижения: получено — перламутр, нет — тусклый контур. */
@Composable
fun AchievementCrystal(earned: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val s = size.width
        val path = Path().apply {
            moveTo(s * 0.5f, s * 0.02f); lineTo(s * 0.84f, s * 0.5f); lineTo(s * 0.5f, s * 0.98f); lineTo(s * 0.16f, s * 0.5f); close()
        }
        if (earned) {
            drawPath(path, Brush.linearGradient(CrystalColors, Offset.Zero, Offset(s, s)))
            drawLine(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f), Offset(s * 0.16f, s * 0.5f), Offset(s * 0.84f, s * 0.5f), s * 0.03f)
            drawLine(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f), Offset(s * 0.5f, s * 0.02f), Offset(s * 0.56f, s * 0.5f), s * 0.03f)
            drawPath(path, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f), style = Stroke(s * 0.04f))
        } else {
            drawPath(path, Palette.Plum.copy(alpha = 0.6f))
            drawPath(path, Palette.Mist.copy(alpha = 0.5f), style = Stroke(s * 0.04f))
        }
    }
}

/** Свои записи, PRISM и медиа — вкладками. */
@Composable
fun ProfileContentTabs(vm: AppViewModel, state: UiState) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val myId = state.me.id
    val myPrisms = state.prisms.filter { it.author.id == myId }
    val media: List<Attachment> = state.myPosts.flatMap { p -> p.media.filter { it.kind != MediaKind.Document && !it.sticker } } +
        myPrisms.mapNotNull { it.cover }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(tf("Записи · %s", state.myPosts.size), active = tab == 0) { tab = 0 }
            Chip(tf("PRISM · %s", myPrisms.size), active = tab == 1) { tab = 1 }
            Chip(tf("Медиа · %s", media.size), active = tab == 2) { tab = 2 }
        }
        when (tab) {
            0 -> {
                if (state.myPosts.isEmpty()) EmptyNote(t("Пока ни одной записи. Поделитесь первым моментом во вкладке «Лента»."))
                state.myPosts.take(3).forEach { post ->
                    PostCard(
                        post,
                        onLike = { vm.toggleLike(post.id) },
                        onComments = { vm.openComments(post.id) },
                        onOpenMedia = vm::openMedia,
                    )
                }
                if (state.myPosts.size > 3) {
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        GhostButton(t("Все записи"), onClick = { vm.openProfileSection(ProfileSection.Posts) })
                    }
                }
            }
            1 -> {
                if (myPrisms.isEmpty()) EmptyNote(t("Своих PRISM пока нет — «+» во вкладке PRISM."))
                TileGrid(myPrisms, columns = 3) { prism -> PrismTile(prism) { vm.openPrism(prism.id) } }
            }
            else -> {
                if (media.isEmpty()) EmptyNote(t("Здесь соберутся фото и видео из ваших записей и обложки PRISM."))
                TileGrid(media, columns = 3) { a ->
                    MediaTile(a, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)), maxSide = 360) { vm.openMedia(a) }
                }
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, modifier = Modifier.padding(horizontal = 24.dp))
}

@Composable
private fun <T> TileGrid(items: List<T>, columns: Int, cell: @Composable (T) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { item -> Box(Modifier.weight(1f)) { cell(item) } }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PrismTile(prism: Prism, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.62f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        val cover = prism.cover
        if (cover != null) MediaTile(cover, Modifier.fillMaxSize(), maxSide = 360)
        else PrismArt(prism.seed, 1_500, Modifier.fillMaxSize())
        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(LineIcon.Play, Palette.Ink, size = 12.dp)
            Spacer(Modifier.width(4.dp))
            Text("${prism.likes}", style = MaterialTheme.typography.labelSmall, color = Palette.Ink)
        }
    }
}

/** Мои группы и взаимные подписки — лентами. */
@Composable
fun PeopleRows(vm: AppViewModel, state: UiState) {
    val mutual = state.followers.filter { it.id in state.following && it.id !in state.blocked }
    if (state.myGroups.isEmpty() && mutual.isEmpty()) return
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.myGroups.isNotEmpty()) {
                CardTitle(tf("Мои группы · %s", state.myGroups.size), Modifier.padding(horizontal = 16.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.myGroups, key = { it.id }) { group ->
                        Column(
                            modifier = Modifier.width(84.dp).clip(RoundedCornerShape(14.dp)).clickable { vm.openGroup(group.id) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            GroupCover(group, Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)))
                            Spacer(Modifier.height(6.dp))
                            Text(group.name, style = MaterialTheme.typography.labelLarge, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (mutual.isNotEmpty()) {
                CardTitle(tf("Взаимные подписки · %s", mutual.size), Modifier.padding(horizontal = 16.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(mutual, key = { it.id }) { person ->
                        Column(
                            modifier = Modifier.width(72.dp).clip(RoundedCornerShape(14.dp)).clickable { vm.openPerson(person.id) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Avatar(person, size = 56.dp, ring = true, showOnline = true)
                            Spacer(Modifier.height(6.dp))
                            Text(person.name.substringBefore(' '), style = MaterialTheme.typography.labelLarge, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
