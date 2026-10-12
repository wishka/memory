package ru.wishka.memory.ui.together

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.CHECK_IN_EMOJI
import ru.wishka.memory.data.Journey
import ru.wishka.memory.data.JourneyEntry
import ru.wishka.memory.data.MediaFiles
import ru.wishka.memory.data.agoTitle
import ru.wishka.memory.data.minutesSince
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.AttachButton
import ru.wishka.memory.ui.components.Chip
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.PendingAttachments
import ru.wishka.memory.ui.components.PostMedia
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.components.rememberMediaPicker
import ru.wishka.memory.ui.theme.Palette

/**
 * Дневники процесса: «День 3: учусь жонглировать». Выкладывают не
 * результат, а путь, а сообщество отвечает советами — вместо «нравится».
 */
@Composable
internal fun JourneysSection(vm: AppViewModel, state: UiState) {
    val journeys = state.together.journeys
    var openId by remember { mutableStateOf<Long?>(null) }
    var creating by remember { mutableStateOf(false) }
    val open = journeys.firstOrNull { it.id == openId }
    if (open != null) {
        JourneyPage(vm, state, open) { openId = null }
        return
    }
    Card {
        Hint(t("Короткий челлендж: выкладывайте процесс по дням — «День 3: учусь жонглировать». Вместо лайков здесь советы, а автор отмечает полезные."))
        GlowButton(t("Начать свой челлендж"), onClick = { creating = true })
    }
    val mine = journeys.filter { it.authorId == state.me.id }
    if (mine.isNotEmpty()) {
        Caption(t("Мои челленджи"))
        mine.forEach { JourneyCard(state, it) { openId = it.id } }
    }
    Caption(t("Сообщество учится"))
    journeys.filter { it.authorId != state.me.id && it.authorId !in state.blocked }.forEach { JourneyCard(state, it) { openId = it.id } }

    if (creating) {
        var emoji by remember { mutableStateOf("🔥") }
        var title by remember { mutableStateOf("") }
        var days by remember { mutableStateOf(30) }
        TogetherDialog(t("Свой челлендж"), onDismiss = { creating = false }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("🔥", "🤹", "🎸", "🗣️", "🏃", "🎨", "🧘", "📖", "🧶", "🍳") + CHECK_IN_EMOJI.take(8)) { e ->
                    Text(
                        e, fontSize = 22.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).glass(RoundedCornerShape(50), strong = e == emoji).clickable { emoji = e }.padding(8.dp),
                    )
                }
            }
            Field(title, { title = it.take(60) }, t("Чему учитесь? «Учусь жонглировать»"))
            Caption(t("Сколько дней"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 14, 30, 100).forEach { d -> Chip(tf("%s дн.", d), active = days == d) { days = d } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(t("Отмена"), onClick = { creating = false })
                GlowButton(t("Начать"), enabled = title.isNotBlank(), onClick = { if (vm.startJourney(emoji, title, days)) creating = false })
            }
        }
    }
}

@Composable
private fun JourneyCard(state: UiState, j: Journey, onOpen: () -> Unit) {
    val author = personOf(state, j.authorId)
    val last = j.entries.lastOrNull()
    Card {
        Row(Modifier.clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(j.emoji)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(j.title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                Text(tf("%s · день %s из %s", author.name, j.entries.size, j.days), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
            }
        }
        ProgressBar(j.entries.size.toFloat() / j.days)
        last?.let { Text(tf("День %s: %s", it.day, it.text), style = MaterialTheme.typography.bodyMedium, color = Palette.Ink, maxLines = 2) }
        val tips = j.entries.sumOf { it.tips.size }
        Text(tf("Советов: %s", tips), style = MaterialTheme.typography.labelLarge, color = Palette.Lilac)
    }
}

@Composable
private fun JourneyPage(vm: AppViewModel, state: UiState, j: Journey, onBack: () -> Unit) {
    val mine = j.authorId == state.me.id
    var text by remember(j.id) { mutableStateOf("") }
    var media by remember(j.id) { mutableStateOf<List<Attachment>>(emptyList()) }
    val picker = rememberMediaPicker { picked -> media = (media + picked).take(4) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        GhostButton(t("Назад"), onClick = onBack)
        Spacer(Modifier.width(12.dp))
        Text("${j.emoji} ${j.title}", style = MaterialTheme.typography.titleMedium, color = Palette.Ink, modifier = Modifier.weight(1f))
    }
    Card {
        Text(tf("%s · день %s из %s", personOf(state, j.authorId).name, j.entries.size, j.days), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
        ProgressBar(j.entries.size.toFloat() / j.days)
    }
    if (mine) {
        Card {
            Caption(tf("День %s", j.nextDay))
            Field(text, { text = it.take(1000) }, t("Что получилось и что нет?"), singleLine = false)
            PendingAttachments(media, onRemove = { media = media - it; MediaFiles.delete(it) })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AttachButton(picker, documents = false, size = 40.dp)
                GlowButton(t("Записать день"), enabled = text.isNotBlank() || media.isNotEmpty(), onClick = {
                    vm.addJourneyEntry(j.id, text, media)
                    text = ""
                    media = emptyList()
                })
            }
        }
    }
    if (j.entries.isEmpty()) Hint(t("Пока ни одного дня. Первый шаг — самый важный."))
    j.entries.asReversed().forEach { e -> EntryCard(vm, state, j, e, mine) }
}

@Composable
private fun EntryCard(vm: AppViewModel, state: UiState, j: Journey, e: JourneyEntry, mine: Boolean) {
    var tip by remember(e.id) { mutableStateOf("") }
    Card {
        Text(tf("День %s", e.day), style = MaterialTheme.typography.labelLarge, color = Palette.Lilac)
        Text(e.text, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
        PostMedia(e.media, onOpen = vm::openMedia)
        Text(agoTitle(minutesSince(e.createdAt, 0)), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
        if (e.tips.isNotEmpty()) Caption(tf("Советы · %s", e.tips.size))
        e.tips.sortedByDescending { it.helpful }.forEach { tp ->
            val author = personOf(state, tp.authorId)
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PersonLine(author, tp.text, onClick = { vm.openPerson(author.id) })
                Chip(
                    (if (tp.markedByMe) "✓ " else "") + tf("Полезно · %s", tp.helpful),
                    active = tp.markedByMe,
                ) { vm.toggleHelpful(j.id, e.id, tp.id) }
            }
        }
        if (!mine) {
            Field(tip, { tip = it.take(500) }, t("Дать совет — как сделать лучше?"), singleLine = false)
            GlowButton(t("Отправить совет"), enabled = tip.isNotBlank(), onClick = { vm.addTip(j.id, e.id, tip); tip = "" })
        }
    }
}
