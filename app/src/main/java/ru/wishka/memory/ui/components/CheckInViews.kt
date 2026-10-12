package ru.wishka.memory.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ru.wishka.memory.data.CHECK_IN_ACTIVITIES
import ru.wishka.memory.data.CHECK_IN_EMOJI
import ru.wishka.memory.data.CheckIn
import ru.wishka.memory.data.CheckInTemplate
import ru.wishka.memory.data.checkIn
import ru.wishka.memory.i18n.t
import ru.wishka.memory.ui.theme.Palette

/**
 * Чек-ин в карточке записи: значок занятия в кристальном круге, что
 * делает человек, что именно и сколько.
 */
@Composable
fun CheckInBadge(checkIn: CheckIn, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Palette.Violet.copy(alpha = 0.55f), Palette.Ocean.copy(alpha = 0.35f))))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Palette.Orchid, Palette.Ocean))),
            contentAlignment = Alignment.Center,
        ) { Text(checkIn.emoji, fontSize = 24.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(checkIn.displayTitle.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Lilac, maxLines = 1)
            if (checkIn.subject.isNotEmpty()) {
                Text(checkIn.subject, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (checkIn.amountTitle.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(
                checkIn.amountTitle,
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Ink,
                modifier = Modifier.glass(RoundedCornerShape(50), strong = true).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

private sealed interface CheckInStep {
    data object Choose : CheckInStep
    data class Fill(val emoji: String, val title: String, val hint: String, val unit: String, val make: (String, Double) -> CheckIn) : CheckInStep
    data object Create : CheckInStep
}

/**
 * Выбор чек-ина: встроенные занятия и свои, «+ Свой чек-ин» создаёт
 * шаблон. Затем — что именно и сколько. Долгое нажатие на свой — удалить.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CheckInPicker(
    templates: List<CheckInTemplate>,
    onDone: (CheckIn) -> Unit,
    onCreate: (emoji: String, title: String, unit: String, hint: String) -> String?,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf<CheckInStep>(CheckInStep.Choose) }
    var deleting by remember { mutableStateOf<CheckInTemplate?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Palette.Night)
                .padding(16.dp),
        ) {
            when (val s = step) {
                CheckInStep.Choose -> {
                    Text(t("Чек-ин"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                    Text(t("Чем вы сейчас заняты?"), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                    Spacer(Modifier.height(12.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "new") {
                            ActivityTile("＋", t("Свой чек-ин"), accent = true, onClick = { step = CheckInStep.Create })
                        }
                        items(templates, key = { it.id }) { tpl ->
                            ActivityTile(tpl.emoji, tpl.title, onLongClick = { deleting = tpl }, onClick = {
                                step = CheckInStep.Fill(tpl.emoji, tpl.title, tpl.hint.ifBlank { t("Что именно?") }, tpl.unit, tpl::checkIn)
                            })
                        }
                        items(CHECK_IN_ACTIVITIES, key = { it.id }) { a ->
                            ActivityTile(a.emoji, t(a.title), onClick = {
                                step = CheckInStep.Fill(a.emoji, t(a.title), t(a.hint), if (a.unit.isEmpty()) "" else t(a.unit), a::checkIn)
                            })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    GhostButton(t("Отмена"), onClick = onDismiss)
                }
                is CheckInStep.Fill -> FillStep(s, onBack = { step = CheckInStep.Choose }, onDone = { onDone(it); onDismiss() })
                CheckInStep.Create -> CreateStep(onBack = { step = CheckInStep.Choose }, onCreate = { e, title, unit, hint ->
                    val error = onCreate(e, title, unit, hint)
                    if (error == null) step = CheckInStep.Choose
                    error
                })
            }
        }
    }
    deleting?.let { tpl ->
        Dialog(onDismissRequest = { deleting = null }) {
            Column(
                Modifier.clip(RoundedCornerShape(24.dp)).background(Palette.Night).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("${tpl.emoji} ${tpl.title}", style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                Text(t("Удалить свой чек-ин? Записи с ним останутся."), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton(t("Отмена"), onClick = { deleting = null })
                    GlowButton(t("Удалить"), onClick = { onDelete(tpl.id); deleting = null })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActivityTile(emoji: String, title: String, accent: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(18.dp))
            .glass(RoundedCornerShape(18.dp), strong = accent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(emoji, fontSize = 28.sp, color = Palette.Lilac)
        Spacer(Modifier.height(6.dp))
        Text(
            title, style = MaterialTheme.typography.labelMedium, color = Palette.Ink,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FillStep(s: CheckInStep.Fill, onBack: () -> Unit, onDone: (CheckIn) -> Unit) {
    var subject by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(s.emoji, fontSize = 32.sp)
        Spacer(Modifier.width(12.dp))
        Text(s.title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
    }
    Spacer(Modifier.height(14.dp))
    GlassField(subject, { subject = it.take(80) }, s.hint, Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)), singleLine = true)
    if (s.unit.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassField(
                amount, { v -> amount = v.filter { it.isDigit() || it == ',' || it == '.' }.take(7) }, t("Сколько?"),
                Modifier.weight(1f).glass(RoundedCornerShape(18.dp)), singleLine = true, keyboardType = KeyboardType.Decimal,
            )
            Spacer(Modifier.width(10.dp))
            Text(s.unit, style = MaterialTheme.typography.titleMedium, color = Palette.Lilac)
        }
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GhostButton(t("Назад"), onClick = onBack)
        GlowButton(t("Готово"), onClick = {
            onDone(s.make(subject, amount.replace(',', '.').toDoubleOrNull() ?: 0.0))
        })
    }
}

@Composable
private fun CreateStep(onBack: () -> Unit, onCreate: (String, String, String, String) -> String?) {
    var emoji by remember { mutableStateOf(CHECK_IN_EMOJI.first()) }
    var title by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Text(t("Свой чек-ин"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
    Text(t("Например: «Скалодром», «Медитация», «Прочитал главу»"), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
    Spacer(Modifier.height(12.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(CHECK_IN_EMOJI) { e ->
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .glass(CircleShape, strong = e == emoji)
                    .clickable { emoji = e },
                contentAlignment = Alignment.Center,
            ) { Text(e, fontSize = 20.sp) }
        }
    }
    Spacer(Modifier.height(10.dp))
    GlassField(title, { title = it.take(40); error = null }, t("Название"), Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)), singleLine = true)
    Spacer(Modifier.height(8.dp))
    GlassField(hint, { hint = it.take(60) }, t("Подсказка: «Где?», «Что именно?»"), Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)), singleLine = true)
    Spacer(Modifier.height(8.dp))
    GlassField(unit, { unit = it.take(12) }, t("Единица (необязательно): км, мин, раз"), Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)), singleLine = true)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("км", "мин", "ч", "раз", "стр.").forEach { u -> Chip(t(u), unit == t(u)) { unit = if (unit == t(u)) "" else t(u) } }
    }
    error?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.PearlPink)
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GhostButton(t("Назад"), onClick = onBack)
        GlowButton(t("Создать"), enabled = title.isNotBlank(), onClick = { error = onCreate(emoji, title, unit, hint) })
    }
}
