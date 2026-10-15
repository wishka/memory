package ru.wishka.memory.ui.activity

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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.Activity
import ru.wishka.memory.data.ActivityGroup
import ru.wishka.memory.data.agoTitle
import ru.wishka.memory.data.minutesSince
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.Avatar
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassCard
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.profile.RoundIconButton
import ru.wishka.memory.ui.theme.Palette

/**
 * Лента активности: кто оценил, прокомментировал, упомянул, позвал в
 * друзья. Сверху — заявки в друзья с кнопками, ниже — «Новое» и «Раньше».
 */
@Composable
fun ActivityScreen(vm: AppViewModel, state: UiState, contentPadding: PaddingValues) {
    val groups = remember(state.activity, state.blocked, state.me) { Activity.group(state.activity, state.me, state.blocked) }
    val fresh = groups.filter { it.at > state.activitySeen }
    val older = groups.filter { it.at <= state.activitySeen }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(LineIcon.Back, Palette.Ink) { vm.closeActivity() }
                Spacer(Modifier.width(14.dp))
                Text(t("Активность").uppercase(), style = MaterialTheme.typography.headlineSmall, color = Palette.Ink, modifier = Modifier.weight(1f))
                if (fresh.isNotEmpty()) {
                    Text(
                        t("Прочитано"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Lilac,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.markActivitySeen() }.padding(8.dp),
                    )
                }
            }
        }
        // Заявки в друзья — с ответом прямо здесь
        if (state.requestPeople.isNotEmpty()) {
            item { Section(tf("Заявки в друзья · %s", state.requestPeople.size)) }
            items(state.requestPeople, key = { "req" + it.id }) { person ->
                GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.clip(CircleShape).clickable { vm.openPerson(person.id) }) { Avatar(person, size = 44.dp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(person.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("@" + person.handle, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, maxLines = 1)
                        }
                        GhostButton(t("Нет"), onClick = { vm.declineRequest(person.id) })
                        Spacer(Modifier.width(6.dp))
                        GlowButton(t("Принять"), onClick = { vm.approveRequest(person.id) })
                    }
                }
            }
        }
        if (groups.isEmpty() && state.requestPeople.isEmpty()) {
            item {
                Text(
                    t("Здесь появится, кто оценил и прокомментировал ваши записи, упомянул вас или позвал в друзья."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                )
            }
        }
        if (fresh.isNotEmpty()) {
            item { Section(t("Новое")) }
            items(fresh, key = { it.key }) { ActivityRow(vm, it, unread = true) }
        }
        if (older.isNotEmpty()) {
            item { Section(t("Раньше")) }
            items(older, key = { it.key }) { ActivityRow(vm, it, unread = false) }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Palette.Mist,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )
}

@Composable
private fun ActivityRow(vm: AppViewModel, g: ActivityGroup, unread: Boolean) {
    val text = Activity.text(g)
    val ago = agoTitle(minutesSince(g.at, 0))
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .then(if (unread) Modifier.glass(RoundedCornerShape(18.dp)) else Modifier)
            .clickable { vm.openActivityItem(g) }
            .semantics(mergeDescendants = true) { contentDescription = "$text, $ago" }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Два аватара внахлёст, если людей несколько
        Box(Modifier.size(48.dp)) {
            val first = g.actors.firstOrNull()
            if (first != null) Avatar(first, size = if (g.actors.size > 1) 36.dp else 44.dp)
            g.actors.getOrNull(1)?.let { second ->
                Box(Modifier.align(Alignment.BottomEnd).offset(2.dp, 2.dp)) { Avatar(second, size = 30.dp) }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(ago, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
        }
        if (unread) Box(Modifier.size(8.dp).clip(CircleShape).background(Palette.Lilac))
    }
}

/** Колокольчик с точкой-счётчиком новых событий — для шапки «Ленты». */
@Composable
fun ActivityBell(unread: Int, onClick: () -> Unit) {
    Box(
        Modifier.semantics {
            contentDescription = if (unread > 0) tf("Активность, новых: %s", unread) else t("Активность")
        },
    ) {
        RoundIconButton(LineIcon.Bell, Palette.Lilac, onClick = onClick)
        if (unread > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(Palette.Orchid)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(if (unread > 99) "99+" else "$unread", style = MaterialTheme.typography.labelSmall, color = Palette.Ink)
            }
        }
    }
}
