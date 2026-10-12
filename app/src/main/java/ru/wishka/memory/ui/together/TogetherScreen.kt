package ru.wishka.memory.ui.together

import ru.wishka.memory.data.InviteAction
import ru.wishka.memory.data.Unlock
import ru.wishka.memory.ui.components.shareInvite
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import ru.wishka.memory.data.CommunityGoal
import ru.wishka.memory.data.DealStatus
import ru.wishka.memory.data.Demo
import ru.wishka.memory.data.LatLon
import ru.wishka.memory.data.Locator
import ru.wishka.memory.data.SkillDeal
import ru.wishka.memory.data.SkillOffer
import ru.wishka.memory.data.TogetherSection
import ru.wishka.memory.data.User
import ru.wishka.memory.data.amountNumber
import ru.wishka.memory.data.minutesTitle
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.Avatar
import ru.wishka.memory.ui.components.Chip
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassCard
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.ScreenTitle
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.theme.Palette
import java.text.NumberFormat
import java.util.Locale

/**
 * Вкладка «Вместе»: не соревнование, а общее дело. Сверху — разделы
 * метками; под ними — выбранный раздел.
 */
@Composable
fun TogetherScreen(vm: AppViewModel, state: UiState, contentPadding: PaddingValues) {
    val geo = rememberGeo(vm)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenTitle(t("Вместе"), t("Не соревнуемся — делаем вместе"), Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TogetherSection.entries) { s ->
                    Chip("${s.emoji} ${s.title}", active = s == state.togetherSection) { vm.selectTogetherSection(s) }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state.togetherSection) {
                    TogetherSection.Challenges -> ChallengesSection(vm, state)
                    TogetherSection.Skills -> SkillsSection(vm, state)
                    TogetherSection.Journeys -> JourneysSection(vm, state)
                    TogetherSection.Missions -> MissionsSection(vm, state, geo)
                    TogetherSection.Caches -> CachesSection(vm, state, geo)
                    TogetherSection.Outings -> OutingsSection(vm, state, geo)
                    TogetherSection.Drawing -> DrawingSection(vm, state)
                    TogetherSection.Sharing -> SharingSection(vm, state)
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ==================== Общее ====================

/** Человек по номеру: вы или знакомый из демо. */
internal fun personOf(state: UiState, id: Int): User =
    if (id == state.me.id) state.me else Demo.person(id) ?: User(id, t("Участник"), "member")

/** Где вы и как спросить разрешение на геолокацию. */
internal class Geo(val here: LatLon?, val locating: Boolean, val locate: (onDone: (LatLon?) -> Unit) -> Unit)

@Composable
internal fun rememberGeo(vm: AppViewModel): Geo {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var here by remember { mutableStateOf<LatLon?>(null) }
    var locating by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<((LatLon?) -> Unit)?>(null) }
    fun fetch(onDone: (LatLon?) -> Unit) {
        locating = true
        scope.launch {
            val place = Locator.current(context)
            locating = false
            if (place != null) {
                here = place
                vm.setLocation(place)
            }
            onDone(place)
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val done = pending ?: {}
        pending = null
        if (result.values.any { it }) fetch(done) else done(null)
    }
    // Разрешение уже есть — узнаём место сразу, без вопросов
    LaunchedEffect(Unit) { if (Locator.permitted(context)) fetch {} }
    return Geo(here, locating) { onDone ->
        if (Locator.permitted(context)) fetch(onDone)
        else {
            pending = onDone
            permission.launch(Locator.PERMISSIONS)
        }
    }
}

/** Карточка раздела: стекло и отступы. */
@Composable
internal fun Card(content: @Composable ColumnScope.() -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
internal fun Caption(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Mist)
}

@Composable
internal fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
}

/** Окно поверх: заголовок, содержимое с прокруткой. */
@Composable
internal fun TogetherDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Palette.Night)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
            content()
        }
    }
}

/** Поле ввода в стекле на всю ширину. */
@Composable
internal fun Field(value: String, onChange: (String) -> Unit, placeholder: String, singleLine: Boolean = true, keyboardType: KeyboardType = KeyboardType.Text) {
    GlassField(value, onChange, placeholder, Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)), singleLine = singleLine, keyboardType = keyboardType)
}

/** Человек строкой: аватар, имя и подпись. */
@Composable
internal fun PersonLine(user: User, subtitle: String, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(user, size = 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(user.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

/** Шкала прогресса: градиент по заполненной части. */
@Composable
internal fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Palette.Plum)) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Brush.horizontalGradient(listOf(Palette.PearlPink, Palette.Lilac, Palette.Sky))),
        )
    }
}

/** «734 210» — с разрядами. */
internal fun bigNumber(value: Double): String =
    NumberFormat.getIntegerInstance(Locale("ru")).format(value.toLong()).replace(' ', ' ')

/** Круглый значок-эмодзи. */
@Composable
internal fun EmojiBadge(emoji: String, size: Int = 44) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Palette.Orchid, Palette.Ocean))),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = (size / 2).sp) }
}

// ==================== 1. Общие цели ====================

@Composable
private fun ChallengesSection(vm: AppViewModel, state: UiState) {
    val together = state.together
    var contributing by remember { mutableStateOf<CommunityGoal?>(null) }
    // «Сейчас вместе» — живое присутствие: сколько людей сейчас двигают цели
    val now = together.goals.sumOf { it.participants } / 37 + 140
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Palette.Sky))
            Spacer(Modifier.width(8.dp))
            Text(tf("Сейчас вместе с вами: %s", bigNumber(now.toDouble())), style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
        }
        Hint(t("Не соревнование: все двигают одну шкалу. Ваши чек-ины — бег, книги, тренировки — засчитываются сами, если вы участвуете."))
    }
    together.goals.forEach { goal ->
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge(goal.emoji)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(goal.title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                    Text(tf("Участников: %s", bigNumber(goal.participants.toDouble())), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                }
            }
            ProgressBar(goal.progress)
            Text(
                tf("%s из %s %s · %s%%", bigNumber(goal.total), bigNumber(goal.target), t(goal.unit), (goal.progress * 100).toInt()),
                style = MaterialTheme.typography.labelLarge,
                color = Palette.Lilac,
            )
            Text(goal.description, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
            if (goal.joined && goal.mine > 0) {
                Text(tf("Ваш вклад: %s %s", amountNumber(Math.round(goal.mine * 10) / 10.0), t(goal.unit)), style = MaterialTheme.typography.bodyMedium, color = Palette.Ink)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!goal.joined) GlowButton(t("Участвовать"), onClick = { vm.joinGoal(goal.id) })
                else GhostButton(t("Вы участвуете"), onClick = {})
                GhostButton(t("Внести вклад"), onClick = { contributing = goal })
            }
        }
    }
    // Своя цель — право за вклад в чужие
    var creating by remember { mutableStateOf(false) }
    val own = state.unlocks.first { it.unlock == Unlock.OwnGoal }
    Card {
        if (own.open) {
            Hint(t("Вы открыли право предложить сообществу свою цель."))
            GlowButton(t("Своя цель"), onClick = { creating = true })
        } else {
            Hint("🔒 " + t("Своя общая цель") + ": " + own.unlock.how + " · " + tf("осталось %s", own.left))
        }
    }
    if (creating) {
        var emoji by remember { mutableStateOf("🎯") }
        var title by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var unit by remember { mutableStateOf("") }
        var target by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        TogetherDialog(t("Своя цель"), onDismiss = { creating = false }) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("🎯", "🌳", "🧹", "🚲", "📚", "🐾").forEach { e -> Chip(e, active = emoji == e) { emoji = e } }
            }
            Field(title, { title = it.take(60); error = null }, t("Цель: «Уберём 100 дворов»"))
            Field(description, { description = it.take(300) }, t("Как засчитывается вклад"), singleLine = false)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { Field(target, { v -> target = v.filter { it.isDigit() }.take(9) }, t("Сколько"), keyboardType = KeyboardType.Number) }
                Box(Modifier.weight(1f)) { Field(unit, { unit = it.take(12) }, t("Чего: дворов")) }
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.PearlPink) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(t("Отмена"), onClick = { creating = false })
                GlowButton(t("Создать"), enabled = title.isNotBlank(), onClick = {
                    error = vm.createGoal(emoji, title, description, unit, target.toDoubleOrNull() ?: 0.0)
                    if (error == null) creating = false
                })
            }
        }
    }
    contributing?.let { goal ->
        var amount by remember(goal.id) { mutableStateOf("") }
        TogetherDialog(goal.title, onDismiss = { contributing = null }) {
            Hint(tf("Сколько добавить, %s?", t(goal.unit)))
            Field(amount, { v -> amount = v.filter { it.isDigit() || it == ',' || it == '.' }.take(7) }, t("Например, 5"), keyboardType = KeyboardType.Decimal)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(t("Отмена"), onClick = { contributing = null })
                GlowButton(t("Добавить"), onClick = {
                    vm.contribute(goal.id, amount.replace(',', '.').toDoubleOrNull() ?: 0.0)
                    contributing = null
                })
            }
        }
    }
}

// ==================== 2. Банк времени ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkillsSection(vm: AppViewModel, state: UiState) {
    val together = state.together
    var adding by remember { mutableStateOf<Boolean?>(null) }
    var dealWith by remember { mutableStateOf<SkillOffer?>(null) }
    val context = LocalContext.current
    val mentor = state.unlocks.first { it.unlock == Unlock.Mentor }
    Card {
        Caption(t("Банк времени"))
        Text(minutesTitle(together.balance), style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
        Text(
            if (mentor.open) "🎓 " + t("Вы наставник — вас первым видят в подборе") else "🎓 " + mentor.unlock.how + " · " + tf("осталось %s", mentor.left),
            style = MaterialTheme.typography.labelLarge, color = Palette.Lilac,
        )
        Hint(t("Никаких денег: научили кого-то 30 минут — получили 30 минут, потратить их можно на любой навык у любого. Обмен «я тебе — ты мне» баланс не меняет."))
    }
    Card {
        Caption(t("Я научу"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            together.myTeach.forEach { s -> Chip("$s  ×", active = true) { vm.removeSkill(true, s) } }
            Chip("＋ " + t("Добавить"), active = false) { adding = true }
        }
        Caption(t("Хочу научиться"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            together.myWant.forEach { s -> Chip("$s  ×", active = true) { vm.removeSkill(false, s) } }
            Chip("＋ " + t("Добавить"), active = false) { adding = false }
        }
    }
    val deals = together.deals.filter { it.status != DealStatus.Declined }
    if (deals.isNotEmpty()) {
        Caption(t("Мои договорённости"))
        deals.forEach { deal -> DealCard(vm, state, deal) }
    }
    Caption(t("Подходят вам"))
    together.matches().filter { it.first.personId !in state.blocked }.forEach { (offer, score) ->
        val person = personOf(state, offer.personId)
        Card {
            PersonLine(person, offer.about, onClick = { vm.openPerson(person.id) })
            if (score == 2) Text(t("✦ Взаимный обмен"), style = MaterialTheme.typography.labelLarge, color = Palette.Lilac)
            Text(tf("Научит: %s", offer.teaches.joinToString(", ")), style = MaterialTheme.typography.bodyMedium, color = Palette.Ink)
            Text(tf("Хочет: %s", offer.wants.joinToString(", ")), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
            GlowButton(t("Договориться"), onClick = { dealWith = offer })
        }
    }
    // Нужного навыка нет среди знакомых — позвать того, кто умеет
    Card {
        Hint(t("Знаете того, кто умеет то, чему вы хотите научиться? Позовите — обменяетесь навыками."))
        GhostButton(t("Позвать в обмен навыками"), onClick = { shareInvite(context, vm, InviteAction.Lesson, title = together.myWant.firstOrNull().orEmpty()) })
    }
    adding?.let { teach ->
        var skill by remember(teach) { mutableStateOf("") }
        TogetherDialog(if (teach) t("Чему вы научите?") else t("Чему хотите научиться?"), onDismiss = { adding = null }) {
            Field(skill, { skill = it.take(40) }, t("Например, гитара, английский, фото"))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(t("Отмена"), onClick = { adding = null })
                GlowButton(t("Добавить"), enabled = skill.isNotBlank(), onClick = { vm.addSkill(teach, skill); adding = null })
            }
        }
    }
    dealWith?.let { offer -> DealDialog(vm, state, offer) { dealWith = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DealDialog(vm: AppViewModel, state: UiState, offer: SkillOffer, onClose: () -> Unit) {
    val person = personOf(state, offer.personId)
    var give by remember { mutableStateOf(state.together.myTeach.firstOrNull { t -> offer.wants.any { it.equals(t, true) } } ?: "") }
    var get by remember { mutableStateOf(offer.teaches.firstOrNull { t -> state.together.myWant.any { it.equals(t, true) } } ?: offer.teaches.firstOrNull() ?: "") }
    var minutes by remember { mutableStateOf(30) }
    var error by remember { mutableStateOf<String?>(null) }
    TogetherDialog(tf("Обмен с %s", person.name), onDismiss = onClose) {
        Caption(t("Вы научите"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(t("Ничему — заплачу часами"), active = give.isEmpty()) { give = "" }
            state.together.myTeach.forEach { s -> Chip(s, active = give == s) { give = s } }
        }
        Caption(t("Вас научат"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(t("Ничему — пусть будут часы"), active = get.isEmpty()) { get = "" }
            offer.teaches.forEach { s -> Chip(s, active = get == s) { get = s } }
        }
        Caption(t("Сколько"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 60, 90).forEach { m -> Chip(minutesTitle(m), active = minutes == m) { minutes = m } }
        }
        val change = (if (give.isNotEmpty()) minutes else 0) - (if (get.isNotEmpty()) minutes else 0)
        Hint(
            when {
                change > 0 -> tf("В банк времени придёт %s", minutesTitle(change))
                change < 0 -> tf("Из банка времени уйдёт %s (у вас %s)", minutesTitle(-change), minutesTitle(state.together.balance))
                else -> t("Обмен один к одному — баланс не меняется")
            }
        )
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.PearlPink) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton(t("Отмена"), onClick = onClose)
            GlowButton(t("Предложить"), onClick = {
                error = vm.proposeDeal(person.id, give, get, minutes)
                if (error == null) onClose()
            })
        }
    }
}

@Composable
private fun DealCard(vm: AppViewModel, state: UiState, deal: SkillDeal) {
    val person = personOf(state, deal.personId)
    Card {
        PersonLine(
            person,
            when {
                deal.give.isNotEmpty() && deal.get.isNotEmpty() -> tf("Вы — «%s», вам — «%s» · %s", deal.give, deal.get, minutesTitle(deal.minutes))
                deal.get.isNotEmpty() -> tf("Урок «%s» за %s", deal.get, minutesTitle(deal.minutes))
                else -> tf("Вы учите «%s» · %s", deal.give, minutesTitle(deal.minutes))
            },
            onClick = { vm.openPerson(person.id) },
        )
        Text(
            when (deal.status) {
                DealStatus.Proposed -> t("Ждём ответа")
                DealStatus.Agreed -> t("Договорились — встретьтесь и проведите занятие")
                DealStatus.Done -> t("Занятие прошло ✓")
                DealStatus.Declined -> t("Отменено")
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (deal.status == DealStatus.Done) Palette.Sky else Palette.Lilac,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (deal.status == DealStatus.Agreed) GlowButton(t("Занятие прошло"), onClick = { vm.setDeal(deal.id, DealStatus.Done) })
            if (deal.status != DealStatus.Done) GhostButton(t("Отменить"), onClick = { vm.setDeal(deal.id, DealStatus.Declined) })
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).glass(CircleShape).clickable { vm.writeTo(person.id) },
                contentAlignment = Alignment.Center,
            ) { Glyph(LineIcon.Chats, Palette.Lilac, size = 20.dp) }
        }
    }
}
