package ru.wishka.memory.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.Poll
import ru.wishka.memory.data.PollDraft
import ru.wishka.memory.data.barFraction
import ru.wishka.memory.data.plural
import ru.wishka.memory.i18n.t
import ru.wishka.memory.ui.settings.MemorySwitch
import ru.wishka.memory.ui.theme.Palette

/**
 * Опрос в записи, в группе или в переписке. До голоса — варианты
 * кнопками, после — полосы с процентами (свой выбор отмечен галочкой).
 * Голос можно поменять, пока опрос открыт; автор может его завершить.
 */
@Composable
fun PollCard(poll: Poll, onVote: (Int) -> Unit, modifier: Modifier = Modifier, onClose: (() -> Unit)? = null) {
    val showResults = poll.voted || poll.closed
    val percents = poll.percents()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Void.copy(alpha = 0.35f))
            .border(1.dp, Palette.Line, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(LineIcon.Poll, Palette.Lilac, size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    poll.closed -> t("Опрос завершён")
                    poll.multiple -> t("Опрос · можно несколько")
                    else -> t("Опрос")
                },
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Mist,
            )
        }
        Text(poll.question, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
        poll.options.forEachIndexed { i, option ->
            val mine = i in poll.mine
            val leader = poll.closed && poll.leader == i
            val fraction by animateFloatAsState(if (showResults) poll.barFraction(i) else 0f, animationSpec = tween(600), label = "poll")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Palette.Plum.copy(alpha = 0.45f))
                    // Полоса доли голосов
                    .drawBehind {
                        if (fraction > 0f) drawRect(
                            Brush.horizontalGradient(
                                if (mine || leader) listOf(Palette.Orchid.copy(alpha = 0.75f), Palette.Violet.copy(alpha = 0.6f))
                                else listOf(Palette.Violet.copy(alpha = 0.45f), Palette.Plum.copy(alpha = 0.35f)),
                            ),
                            size = Size(size.width * fraction, size.height),
                        )
                    }
                    .border(1.dp, if (mine) Palette.Lilac else Palette.Line, RoundedCornerShape(14.dp))
                    .then(if (!poll.closed) Modifier.clickable { onVote(i) } else Modifier),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (mine) {
                        Glyph(LineIcon.Check, Palette.Ink, size = 16.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(option.text, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink, modifier = Modifier.weight(1f))
                    if (showResults) Text("${percents.getOrElse(i) { 0 }}%", style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (poll.voters == 0) t("Пока никто не голосовал") else plural(poll.voters, "голос", "голоса", "голосов"),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Mist,
                modifier = Modifier.weight(1f),
            )
            if (onClose != null && !poll.closed) {
                Text(
                    t("Завершить"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Lilac,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClose).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** Новый опрос: вопрос, от 2 до 10 вариантов, один или несколько ответов. */
@Composable
fun PollEditorDialog(onDismiss: () -> Unit, onCreate: (PollDraft) -> Unit) {
    var question by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    var multiple by remember { mutableStateOf(false) }
    val draft = PollDraft(question, options, multiple)
    val valid = draft.build(0) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Night,
        title = { Text(t("Новый опрос"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassField(question, { question = it.take(200) }, t("Вопрос"), modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)))
                options.forEachIndexed { i, value ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassField(
                            value,
                            { v -> options = options.toMutableList().also { it[i] = v.take(100) } },
                            t("Вариант") + " ${i + 1}",
                            singleLine = true,
                            modifier = Modifier.weight(1f).glass(RoundedCornerShape(16.dp)),
                        )
                        if (options.size > Poll.MIN_OPTIONS) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                Modifier.clip(RoundedCornerShape(12.dp)).clickable { options = options.filterIndexed { k, _ -> k != i } }.padding(6.dp),
                            ) { Glyph(LineIcon.Close, Palette.Mist, size = 18.dp) }
                        }
                    }
                }
                if (options.size < Poll.MAX_OPTIONS) {
                    Text(
                        "+ " + t("Добавить вариант"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Palette.Lilac,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { options = options + "" }.padding(vertical = 6.dp, horizontal = 4.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Можно выбрать несколько"), style = MaterialTheme.typography.bodyLarge, color = Palette.Ink, modifier = Modifier.weight(1f))
                    MemorySwitch(multiple) { multiple = it }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onCreate(draft); onDismiss() }) { Text(t("Готово"), color = if (valid) Palette.Lilac else Palette.Mist) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Отмена"), color = Palette.Mist) } },
    )
}

/** Опрос, готовый к отправке, — над полем ввода, с крестиком. */
@Composable
fun PendingPoll(draft: PollDraft, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .glass(RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(LineIcon.Poll, Palette.Lilac, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(draft.question, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink, maxLines = 1)
            Text(plural(draft.options.count { it.isNotBlank() }, "вариант", "варианта", "вариантов"), style = MaterialTheme.typography.labelSmall, color = Palette.Mist)
        }
        Box(Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onRemove).padding(4.dp)) { Glyph(LineIcon.Close, Palette.Mist, size = 16.dp) }
    }
}
