package ru.wishka.memory.ui.together

import ru.wishka.memory.data.InviteAction
import ru.wishka.memory.data.Unlock
import ru.wishka.memory.data.amountNumber
import ru.wishka.memory.ui.components.Chip
import ru.wishka.memory.ui.components.shareInvite
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.CACHE_RADIUS_M
import ru.wishka.memory.data.Cache
import ru.wishka.memory.data.MediaFiles
import ru.wishka.memory.data.Mission
import ru.wishka.memory.data.Outing
import ru.wishka.memory.data.agoTitle
import ru.wishka.memory.data.distanceTitle
import ru.wishka.memory.data.minutesSince
import ru.wishka.memory.data.plural
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.AttachButton
import ru.wishka.memory.ui.components.Avatar
import ru.wishka.memory.ui.components.Chip
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.MapPin
import ru.wishka.memory.ui.components.MediaTile
import ru.wishka.memory.ui.components.OsmMap
import ru.wishka.memory.ui.components.PendingAttachments
import ru.wishka.memory.ui.components.rememberMediaPicker
import ru.wishka.memory.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Карта раздела: квадратная, со скруглением. */
@Composable
private fun SectionMap(state: UiState, geo: Geo, pins: List<MapPin>, onPin: (String) -> Unit = {}) {
    OsmMap(
        pins = pins,
        center = geo.here ?: state.together.anchor,
        me = geo.here,
        modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(20.dp)),
        onPin = onPin,
    )
    if (geo.here == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Hint(if (geo.locating) t("Ищем, где вы…") else t("Покажите, где вы, — и точки рядом с вами будут ближе"))
            Spacer(Modifier.width(8.dp))
            if (!geo.locating) GhostButton(t("Где я?"), onClick = { geo.locate {} })
        }
    }
}

// ==================== 4. Миссии ====================

@Composable
internal fun MissionsSection(vm: AppViewModel, state: UiState, geo: Geo) {
    val context = LocalContext.current
    val missions = state.together.missions
    var adding by remember { mutableStateOf<Mission?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    val shown = missions.filter { selected == null || it.id == selected }
    SectionMap(state, geo, shown.flatMap { m -> m.finds.filter { it.authorId !in state.blocked }.map { f -> MapPin("${m.id}:${f.id}", f.at, f.note, m.emoji, f.authorId == state.me.id) } })
    Hint(t("Задания по городу. Находки всех участников — на общей карте."))
    var creating by remember { mutableStateOf(false) }
    val own = state.unlocks.first { it.unlock == Unlock.OwnMission }
    Card {
        if (own.open) {
            Hint(t("Вы открыли право придумать миссию для своего района."))
            GlowButton(t("Своя миссия"), onClick = { creating = true })
        } else {
            Hint("🔒 " + t("Своя миссия района") + ": " + own.unlock.how + " · " + tf("осталось %s", own.left))
        }
    }
    val km = state.movedKm
    missions.forEach { m ->
        val mine = m.mineCount(state.me.id)
        val locked = m.locked(km)
        Card {
            Row(Modifier.clickable { selected = if (selected == m.id) null else m.id }, verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge(m.emoji)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                    Text(tf("Ваши находки: %s из %s · всего на карте: %s", mine, m.target, m.finds.size), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                }
            }
            ProgressBar(mine.toFloat() / m.target)
            Text(m.description, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
            val photos = m.finds.filter { it.authorId !in state.blocked }.mapNotNull { it.photo }
            if (photos.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    photos.takeLast(4).forEach { MediaTile(it, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)), maxSide = 256, onClick = { vm.openMedia(it) }) }
                }
            }
            if (locked) {
                ProgressBar((km / m.unlockKm).toFloat())
                Hint("🔒 " + tf("Откроется после %s км пробежек, прогулок и заездов — осталось %s км", amountNumber(m.unlockKm), amountNumber(Math.round((m.unlockKm - km) * 10) / 10.0)))
            } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!m.joined) GhostButton(t("Участвовать"), onClick = { vm.joinMission(m.id) })
                GlowButton(t("Добавить находку"), onClick = { adding = m })
            }
        }
    }
    if (creating) {
        var emoji by remember { mutableStateOf("🗺️") }
        var title by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var target by remember { mutableStateOf(5) }
        var error by remember { mutableStateOf<String?>(null) }
        TogetherDialog(t("Своя миссия"), onDismiss = { creating = false }) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("🗺️", "🧱", "🌳", "🎨", "🏛️", "🐦").forEach { e -> Chip(e, active = emoji == e) { emoji = e } }
            }
            Field(title, { title = it.take(60); error = null }, t("Что ищем? «Старые почтовые ящики»"))
            Field(description, { description = it.take(300) }, t("Пара слов: что считается находкой"), singleLine = false)
            Caption(tf("Сколько найти: %s", target))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(3, 5, 7, 10).forEach { n -> Chip("$n", active = target == n) { target = n } } }
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.PearlPink) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(t("Отмена"), onClick = { creating = false })
                GlowButton(t("Создать"), enabled = title.isNotBlank(), onClick = {
                    error = vm.createMission(emoji, title, description, target)
                    if (error == null) creating = false
                })
            }
        }
    }
    adding?.let { m ->
        var note by remember(m.id) { mutableStateOf("") }
        var photo by remember(m.id) { mutableStateOf<List<Attachment>>(emptyList()) }
        val picker = rememberMediaPicker { picked -> photo = picked.filter { it.kind == ru.wishka.memory.data.MediaKind.Photo }.take(1) }
        TogetherDialog("${m.emoji} ${m.title}", onDismiss = { adding = null }) {
            Hint(t("Сфотографируйте находку — она встанет на карту там, где вы сейчас."))
            Field(note, { note = it.take(200) }, t("Что это? «Птицы над входом»"))
            PendingAttachments(photo, onRemove = { photo = emptyList(); MediaFiles.delete(it) })
            AttachButton(picker, documents = false, size = 40.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(t("Отмена"), onClick = { adding = null })
                GlowButton(t("На карту"), enabled = note.isNotBlank() || photo.isNotEmpty(), onClick = {
                    geo.locate { here ->
                        if (here == null) {
                            Toast.makeText(context, t("Не удалось узнать, где вы. Включите геолокацию."), Toast.LENGTH_LONG).show()
                        } else {
                            vm.addFind(m.id, note, here, photo.firstOrNull())
                            adding = null
                        }
                    }
                })
            }
        }
    }
}

// ==================== 5. Клады ====================

@Composable
internal fun CachesSection(vm: AppViewModel, state: UiState, geo: Geo) {
    val context = LocalContext.current
    val caches = state.together.caches.filter { it.authorId !in state.blocked }
    var openId by remember { mutableStateOf<Long?>(null) }
    var hiding by remember { mutableStateOf(false) }
    SectionMap(state, geo, caches.map { MapPin("${it.id}", it.at, it.title, if (it.foundByMe) "✅" else "💎", it.authorId == state.me.id) }) { id ->
        openId = id.toLongOrNull()
    }
    Card {
        Hint(t("Спрячьте маленький контейнер, оставьте подсказку — другие найдут и отметятся. Отметиться можно, только оказавшись рядом."))
        val right = state.unlocks.first { it.unlock == Unlock.HideCache }
        if (right.open) GlowButton(t("Спрятать клад здесь"), onClick = { hiding = true })
        else {
            ProgressBar(right.fraction)
            Hint("🔒 " + t("Прятать клады можно, когда найдёте три чужих") + " · " + tf("осталось %s", right.left))
        }
    }
    val here = geo.here
    caches.sortedBy { c -> here?.distanceTo(c.at) ?: 0.0 }.forEach { c ->
        Card {
            Row(Modifier.clickable { openId = c.id }, verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge(if (c.foundByMe) "✅" else "💎")
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                    Text(
                        listOfNotNull(
                            here?.let { distanceTitle(it.distanceTo(c.at)) },
                            tf("сложность %s/5", c.difficulty), t(c.size),
                            plural(c.logs.size, "находка", "находки", "находок"),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Mist,
                    )
                }
            }
        }
    }
    caches.firstOrNull { it.id == openId }?.let { c -> CacheDialog(vm, state, geo, c) { openId = null } }
    if (hiding) {
        var title by remember { mutableStateOf("") }
        var hint by remember { mutableStateOf("") }
        var difficulty by remember { mutableIntStateOf(2) }
        var size by remember { mutableStateOf("Маленький") }
        TogetherDialog(t("Спрятать клад"), onDismiss = { hiding = false }) {
            Hint(t("Клад встанет на карту там, где вы сейчас. Прячьте так, чтобы никому не мешать: не в частных дворах и не в памятниках."))
            Field(title, { title = it.take(60) }, t("Название: «Звёздная капсула»"))
            Field(hint, { hint = it.take(300) }, t("Подсказка: где искать"), singleLine = false)
            Caption(t("Сложность"))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { (1..5).forEach { d -> Chip("$d", active = difficulty == d) { difficulty = d } } }
            Caption(t("Размер"))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Микро", "Маленький", "Средний").forEach { s -> Chip(t(s), active = size == s) { size = s } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(t("Отмена"), onClick = { hiding = false })
                GlowButton(t("Спрятать"), enabled = title.isNotBlank() && hint.isNotBlank(), onClick = {
                    geo.locate { here ->
                        if (here == null) Toast.makeText(context, t("Не удалось узнать, где вы. Включите геолокацию."), Toast.LENGTH_LONG).show()
                        else if (vm.hideCache(title, hint, difficulty, size, here)) hiding = false
                    }
                })
            }
        }
    }
}

@Composable
private fun CacheDialog(vm: AppViewModel, state: UiState, geo: Geo, c: Cache, onClose: () -> Unit) {
    val context = LocalContext.current
    var showHint by remember(c.id) { mutableStateOf(false) }
    var note by remember(c.id) { mutableStateOf("") }
    val author = personOf(state, c.authorId)
    val distance = geo.here?.distanceTo(c.at)
    TogetherDialog("💎 ${c.title}", onDismiss = onClose) {
        PersonLine(author, tf("спрятал(а) %s", agoTitle(minutesSince(c.createdAt, 0))), onClick = { onClose(); vm.openPerson(author.id) })
        Text(
            listOfNotNull(distance?.let { tf("До клада: %s", distanceTitle(it)) }, tf("Сложность %s/5", c.difficulty), t(c.size)).joinToString(" · "),
            style = MaterialTheme.typography.labelLarge, color = Palette.Lilac,
        )
        if (showHint || c.authorId == state.me.id) Text(c.hint, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
        else GhostButton(t("Показать подсказку"), onClick = { showHint = true })
        when {
            c.authorId == state.me.id -> Hint(t("Это ваш клад. Когда его найдут, придёт уведомление."))
            c.foundByMe -> Text(t("Вы нашли этот клад ✓"), style = MaterialTheme.typography.titleMedium, color = Palette.Sky)
            else -> {
                Field(note, { note = it.take(300) }, t("Запись в журнале: «Нашёл, оставил открытку»"))
                GlowButton(t("Нашёл!"), onClick = {
                    geo.locate { here ->
                        when {
                            here == null -> Toast.makeText(context, t("Не удалось узнать, где вы. Включите геолокацию."), Toast.LENGTH_LONG).show()
                            !vm.findCache(c.id, note, here) -> Toast.makeText(
                                context, tf("Вы в %s от клада — подойдите ближе (до %s)", distanceTitle(here.distanceTo(c.at)), distanceTitle(CACHE_RADIUS_M)), Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                })
            }
        }
        if (c.logs.isNotEmpty()) Caption(t("Журнал находок"))
        c.logs.asReversed().forEach { log ->
            PersonLine(personOf(state, log.authorId), log.text)
        }
        GhostButton(t("Закрыть"), onClick = onClose)
    }
}

// ==================== 6. Вылазки ====================

private val dateFormat = SimpleDateFormat("EEEE, d MMMM · HH:mm", Locale("ru"))

internal fun outingTime(ms: Long): String =
    (if (ru.wishka.memory.i18n.L10n.english) SimpleDateFormat("EEEE, MMMM d · HH:mm", Locale.US) else dateFormat).format(Date(ms))

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OutingsSection(vm: AppViewModel, state: UiState, geo: Geo) {
    val outings = state.together.outings.filter { it.organizerId !in state.blocked }
    var creating by remember { mutableStateOf(false) }
    SectionMap(state, geo, outings.mapNotNull { o -> o.at?.let { MapPin("${o.id}", it, o.title, o.emoji, o.organizerId == state.me.id) } })
    Card {
        Hint(tf("Малой группой — до %s человек: поход за грибами, фотопрогулка, велопробег.", Outing.MAX_PEOPLE))
        GlowButton(t("Собрать вылазку"), onClick = { creating = true })
    }
    outings.forEach { o -> OutingCard(vm, state, o) }
    if (creating) {
        val context = LocalContext.current
        var emoji by remember { mutableStateOf("🥾") }
        var title by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var place by remember { mutableStateOf("") }
        var dayOffset by remember { mutableIntStateOf(1) }
        var hour by remember { mutableIntStateOf(11) }
        var capacity by remember { mutableIntStateOf(6) }
        TogetherDialog(t("Собрать вылазку"), onDismiss = { creating = false }) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("🥾", "🍄", "📷", "🚴", "🏕️", "🛶", "🌌", "🎨", "☕").forEach { e -> Chip(e, active = emoji == e) { emoji = e } }
            }
            Field(title, { title = it.take(60) }, t("Что делаем? «Фотопрогулка по крышам»"))
            Field(description, { description = it.take(500) }, t("Пара слов: что взять, кому подойдёт"), singleLine = false)
            Field(place, { place = it.take(80) }, t("Где встречаемся"))
            Caption(t("Когда"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0 to t("Сегодня"), 1 to t("Завтра"), 2 to t("Послезавтра"), 6 to t("Через неделю")).forEach { (d, label) ->
                    Chip(label, active = dayOffset == d) { dayOffset = d }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(8, 10, 12, 15, 18, 20).forEach { h -> Chip("$h:00", active = hour == h) { hour = h } }
            }
            Caption(tf("Сколько человек: %s", capacity))
            Slider(
                value = capacity.toFloat(), onValueChange = { capacity = it.toInt() }, valueRange = 2f..Outing.MAX_PEOPLE.toFloat(), steps = Outing.MAX_PEOPLE - 3,
                colors = SliderDefaults.colors(thumbColor = Palette.Ink, activeTrackColor = Palette.Lilac, inactiveTrackColor = Palette.Plum),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(t("Отмена"), onClick = { creating = false })
                GlowButton(t("Собрать"), enabled = title.isNotBlank() && place.isNotBlank(), onClick = {
                    val start = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, dayOffset)
                        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                    }.timeInMillis
                    if (vm.createOuting(emoji, title, description, place, start, capacity, geo.here)) creating = false
                    else Toast.makeText(context, t("Укажите, что делаем и где встречаемся"), Toast.LENGTH_SHORT).show()
                })
            }
        }
    }
}

@Composable
private fun OutingCard(vm: AppViewModel, state: UiState, o: Outing) {
    val context = LocalContext.current
    val me = state.me.id
    val organizer = personOf(state, o.organizerId)
    val joined = me in o.participants
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(o.emoji)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(o.title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                Text(outingTime(o.startsAt), style = MaterialTheme.typography.bodyMedium, color = Palette.Lilac)
            }
        }
        Text("📍 ${o.place}", style = MaterialTheme.typography.bodyMedium, color = Palette.Ink)
        if (o.description.isNotEmpty()) Text(o.description, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp), verticalAlignment = Alignment.CenterVertically) {
            o.participants.take(8).forEach { Avatar(personOf(state, it), size = 30.dp) }
            Spacer(Modifier.width(16.dp))
            Text(tf("%s из %s · собирает %s", o.participants.size, o.capacity, organizer.name), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                o.organizerId == me -> GhostButton(t("Отменить вылазку"), onClick = { vm.cancelOuting(o.id) })
                joined -> GhostButton(t("Вы идёте ✓"), onClick = { vm.toggleOuting(o.id) })
                o.full -> GhostButton(t("Мест нет"), onClick = {})
                else -> GlowButton(t("Иду"), onClick = { vm.toggleOuting(o.id) })
            }
            if (o.organizerId != me) GhostButton(t("Написать"), onClick = { vm.writeTo(o.organizerId) })
            if (!o.full && (joined || o.organizerId == me)) GhostButton(t("Позвать"), onClick = { shareInvite(context, vm, InviteAction.Outing, o.id.toString(), o.title) })
        }
    }
}
