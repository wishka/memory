package ru.wishka.memory.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.wishka.memory.data.Guide
import ru.wishka.memory.data.GuideAction
import ru.wishka.memory.data.GuideChapter
import ru.wishka.memory.data.GuideTopic
import ru.wishka.memory.data.plural
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.ProfileSection
import ru.wishka.memory.ui.SettingsPage
import ru.wishka.memory.ui.Tab
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassCard
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.theme.Palette

/**
 * Что раскрыто в гиде и что ищут — живёт на экране настроек, чтобы
 * пережить переход между главами.
 */
class GuideUi(
    val query: String,
    val onQuery: (String) -> Unit,
    val expanded: Set<String>,
    val onToggle: (String) -> Unit,
)

/**
 * Гид по Memory — страница настроек: список глав с поиском или одна глава.
 * Тексты — на выбранном языке (GuideText), кнопки ведут туда, о чём тема.
 */
fun LazyListScope.guidePage(vm: AppViewModel, state: UiState, ui: GuideUi) {
    val chapter = Guide.chapter(state.guideChapter)
    if (chapter != null) {
        chapterPage(vm, state, chapter, ui)
        return
    }
    item { GuideIntro(state, ui) }
    val hits = Guide.search(ui.query)
    when {
        ui.query.isBlank() -> Guide.chapters.forEach { c ->
            item(key = "guide-" + c.id) { ChapterCard(c, state) { vm.openGuideChapter(c.id) } }
        }
        hits.isEmpty() -> item {
            Text(
                t("Ничего не нашлось. Попробуйте другое слово."),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Mist,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        else -> hits.forEach { hit ->
            val key = hit.chapter.key(hit.topic)
            item(key = "hit-$key") {
                TopicCard(
                    topic = hit.topic,
                    label = hit.chapter.emoji + "  " + hit.chapter.title.text,
                    expanded = key in ui.expanded,
                    read = key in state.guideRead,
                    onToggle = {
                        if (key !in ui.expanded) vm.markGuideRead(hit.chapter, hit.topic)
                        ui.onToggle(key)
                    },
                    onAction = vm::guideGo,
                )
            }
        }
    }
}

private fun LazyListScope.chapterPage(vm: AppViewModel, state: UiState, chapter: GuideChapter, ui: GuideUi) {
    item(key = "head-" + chapter.id) { ChapterHead(chapter, state) }
    chapter.topics.forEachIndexed { i, topic ->
        val key = chapter.key(topic)
        item(key = "topic-$key") {
            TopicCard(
                topic = topic,
                label = null,
                number = i + 1,
                expanded = key in ui.expanded,
                read = key in state.guideRead,
                onToggle = {
                    if (key !in ui.expanded) vm.markGuideRead(chapter, topic)
                    ui.onToggle(key)
                },
                onAction = vm::guideGo,
            )
        }
    }
    item(key = "next-" + chapter.id) {
        val chapters = Guide.chapters
        val next = chapters.getOrNull(chapters.indexOfFirst { it.id == chapter.id } + 1)
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GhostButton(t("Все главы"), onClick = vm::closeGuideChapter)
            if (next != null) GlowButton(
                tf("Дальше: %s", next.title.text),
                onClick = { vm.openGuideChapter(next.id) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ==================== ЧАСТИ ====================

/** Вверху списка глав: что это, сколько прочитано и поиск. */
@Composable
private fun GuideIntro(state: UiState, ui: GuideUi) {
    val total = Guide.topicCount
    val read = state.guideRead.size.coerceAtMost(total)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    t("Как устроен Memory — по шагам. Раскройте тему, а кнопка внизу темы откроет нужный экран."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Ink,
                )
                ProgressLine(read, total)
                Text(
                    tf("Прочитано %s из %s", read, plural(total, "тема", "темы", "тем")),
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.Mist,
                )
            }
        }
        GlassField(
            ui.query,
            ui.onQuery,
            t("Найти в гиде: «витрина», «пароль», «голосовые»…"),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)),
        )
    }
}

/** Полоска прогресса в цветах приложения. */
@Composable
private fun ProgressLine(done: Int, total: Int) {
    val part = if (total == 0) 0f else done.toFloat() / total
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(50))
            .background(Palette.Plum),
    ) {
        if (part > 0f) Box(
            Modifier
                .fillMaxWidth(part)
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(Palette.Lilac, Palette.Sky))),
        )
    }
}

@Composable
private fun EmojiBadge(emoji: String, big: Boolean = false) {
    Box(
        Modifier
            .size(if (big) 64.dp else 48.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Palette.Orchid.copy(alpha = 0.55f), Palette.Ocean.copy(alpha = 0.45f))))
            .border(1.dp, Palette.Line, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = if (big) 30.sp else 22.sp) }
}

/** Глава в списке: значок, название, о чём, сколько прочитано. */
@Composable
private fun ChapterCard(chapter: GuideChapter, state: UiState, onOpen: () -> Unit) {
    val read = chapter.topics.count { chapter.key(it) in state.guideRead }
    val done = read == chapter.topics.size
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(chapter.emoji)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(chapter.title.text, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(chapter.lead.text, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (done) t("Прочитано") else if (read == 0) plural(chapter.topics.size, "тема", "темы", "тем")
                    else tf("%s из %s", read, plural(chapter.topics.size, "тема", "темы", "тем")),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (done) Palette.Lilac else Palette.Mist,
                )
            }
            Spacer(Modifier.width(8.dp))
            Glyph(if (done) LineIcon.Check else LineIcon.ChevronDown, if (done) Palette.Lilac else Palette.Mist, size = 20.dp)
        }
    }
}

/** Шапка главы. */
@Composable
private fun ChapterHead(chapter: GuideChapter, state: UiState) {
    val read = chapter.topics.count { chapter.key(it) in state.guideRead }
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EmojiBadge(chapter.emoji, big = true)
        Text(chapter.title.text, style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
        Text(chapter.lead.text, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
        Box(Modifier.padding(horizontal = 40.dp)) { ProgressLine(read, chapter.topics.size) }
    }
}

/**
 * Тема: заголовок, по нажатию — шаги по номерам, совет и кнопка
 * «Открыть …». label — глава (в результатах поиска).
 */
@Composable
private fun TopicCard(
    topic: GuideTopic,
    label: String?,
    expanded: Boolean,
    read: Boolean,
    onToggle: () -> Unit,
    onAction: (GuideAction) -> Unit,
    number: Int? = null,
) {
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (number != null) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (read) Palette.Lilac.copy(alpha = 0.22f) else Palette.Plum),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (read) Glyph(LineIcon.Check, Palette.Lilac, size = 16.dp)
                        else Text("$number", style = MaterialTheme.typography.labelMedium, color = Palette.Ink)
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    if (label != null) Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Mist, maxLines = 1)
                    Text(topic.title.text, style = MaterialTheme.typography.titleSmall, color = Palette.Ink)
                }
                Spacer(Modifier.width(8.dp))
                Glyph(if (expanded) LineIcon.Close else LineIcon.ChevronDown, Palette.Mist, size = 18.dp)
            }
            if (expanded) Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                topic.steps.forEachIndexed { i, step ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Palette.Orchid, Palette.Ocean))),
                            contentAlignment = Alignment.Center,
                        ) { Text("${i + 1}", fontSize = 11.sp, color = Palette.Ink) }
                        Spacer(Modifier.width(10.dp))
                        Text(step.text, style = MaterialTheme.typography.bodyMedium, color = Palette.Ink, modifier = Modifier.weight(1f))
                    }
                }
                topic.tip?.let { tip ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Palette.Sky.copy(alpha = 0.12f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Glyph(LineIcon.Info, Palette.Sky, size = 18.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(tip.text, style = MaterialTheme.typography.bodyMedium, color = Palette.Ink, modifier = Modifier.weight(1f))
                    }
                }
                topic.action?.let { action ->
                    GlowButton(tf("Открыть: %s", action.title), onClick = { onAction(action) })
                }
            }
        }
    }
}

/** Название места, куда ведёт кнопка темы. */
val GuideAction.title: String
    get() = when (this) {
        GuideAction.Feed -> Tab.Feed.title
        GuideAction.Stories -> Tab.Stories.title
        GuideAction.Prism -> Tab.Prism.title
        GuideAction.NewPrism -> t("Новый ролик")
        GuideAction.Chats -> Tab.Chats.title
        GuideAction.Groups -> Tab.Groups.title
        GuideAction.Together -> Tab.Together.title
        GuideAction.Profile -> Tab.Profile.title
        GuideAction.Friends -> ProfileSection.Friends.title
        GuideAction.Requests -> ProfileSection.Requests.title
        GuideAction.Saved -> ProfileSection.Saved.title
        GuideAction.Music -> ProfileSection.Music.title
        GuideAction.Studio -> ProfileSection.Stickers.title
        GuideAction.Shelf -> ProfileSection.Shelf.title
        GuideAction.Recap -> ProfileSection.Recap.title
        GuideAction.Settings -> SettingsPage.Main.title
        GuideAction.Privacy -> SettingsPage.Privacy.title
        GuideAction.Security -> SettingsPage.Security.title
        GuideAction.Blacklist -> SettingsPage.Blacklist.title
        GuideAction.Notifications -> SettingsPage.Notifications.title
        GuideAction.Sounds -> SettingsPage.Sounds.title
        GuideAction.Wallpaper -> SettingsPage.Wallpaper.title
        GuideAction.Language -> SettingsPage.Language.title
        GuideAction.Legal -> SettingsPage.Legal.title
        GuideAction.Growth -> SettingsPage.Growth.title
    }

/** Карточка в ленте для новичка: открыть гид или скрыть карточку. */
@Composable
fun GuideFeedCard(vm: AppViewModel, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            EmojiBadge("🧭")
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("Гид по Memory"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                Text(
                    t("Как здесь всё устроено — коротко и по шагам: записи, PRISM, группы, витрины, друзья."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlowButton(t("Открыть гид"), onClick = { vm.openGuide() })
                    GhostButton(t("Скрыть"), onClick = vm::hideGuideCard)
                }
            }
        }
    }
}
