package ru.wishka.memory.ui.groups

import ru.wishka.memory.i18n.tf
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.GROUP_TOPICS
import ru.wishka.memory.data.Group
import ru.wishka.memory.data.plural
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.DisclosurePill
import ru.wishka.memory.i18n.t
import ru.wishka.memory.ui.components.Chip
import ru.wishka.memory.ui.components.StaggerIn
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassCard
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.Nebula
import ru.wishka.memory.ui.components.ScreenTitle
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.theme.Palette

/**
 * Вкладка «Группы»: поиск по названию, описанию и теме, фильтр по теме,
 * свои группы отдельно от остальных, создание новой группы.
 */
@Composable
fun GroupsScreen(vm: AppViewModel, state: UiState, contentPadding: PaddingValues) {
    var query by rememberSaveable { mutableStateOf("") }
    var topic by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }

    val needle = query.trim().lowercase()
    val found = state.groups.filter { g ->
        (topic == null || g.topic == topic) &&
            (needle.isEmpty() || "${g.name} ${g.description} ${g.topic}".lowercase().contains(needle))
    }
    val mine = found.filter { it.joined }
    val others = found.filterNot { it.joined }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScreenTitle(
                    t("Группы"),
                    tf("Вы в %s", plural(state.myGroups.size, "группе", "группах", "группах")),
                    Modifier.weight(1f),
                )
                if (!creating) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .glass(CircleShape)
                            .clickable { creating = true },
                        contentAlignment = Alignment.Center,
                    ) { Glyph(LineIcon.Plus, Palette.Lilac, size = 22.dp) }
                }
            }
        }

        if (creating) {
            item { CreateGroupCard(vm, onClose = { creating = false }) }
        }

        item {
            GlassField(
                query, { query = it.take(40) }, t("Поиск групп"),
                singleLine = true,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(50)),
            )
        }
        item {
            TopicPicker(
                selected = topic,
                onSelect = { topic = it },
                allLabel = t("Все темы"),
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            )
        }

        if (found.isEmpty()) {
            item {
                Text(
                    t("Ничего не нашли. Создайте свою группу — кнопкой «+» вверху."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
        if (mine.isNotEmpty()) {
            item { SectionTitle(t("Мои группы")) }
            items(mine, key = { it.id }) { GroupRow(it, onOpen = { vm.openGroup(it.id) }, onToggle = { vm.toggleGroup(it.id) }) }
        }
        if (others.isNotEmpty()) {
            item { SectionTitle(if (needle.isEmpty() && topic == null) t("Рекомендуем") else t("Найдено")) }
            items(others, key = { it.id }) { GroupRow(it, onOpen = { vm.openGroup(it.id) }, onToggle = { vm.toggleGroup(it.id) }) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Palette.Mist,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )
}

@Composable
private fun GroupRow(group: Group, onOpen: () -> Unit, onToggle: () -> Unit) {
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ru.wishka.memory.ui.components.GroupCover(group, Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(group.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (group.muted && !group.mine) Glyph(LineIcon.BellOff, Palette.Mist, size = 16.dp, modifier = Modifier.padding(start = 6.dp))
                }
                Text(
                    "${t(group.topic)} · ${plural(group.members, "участник", "участника", "участников")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                )
            }
            Spacer(Modifier.width(8.dp))
            when {
                group.mine -> Row(verticalAlignment = Alignment.CenterVertically) {
                    if (group.muted) Glyph(LineIcon.BellOff, Palette.Mist, size = 16.dp, modifier = Modifier.padding(end = 6.dp))
                    Text(t("ВАША"), style = MaterialTheme.typography.labelSmall, color = Palette.Lilac)
                }
                group.joined -> GhostButton(t("Вы в группе"), onClick = onToggle)
                else -> GlowButton(t("Вступить"), onClick = onToggle)
            }
        }
    }
}

/**
 * Тема группы: таблетка с выбранной темой («Все темы ▾»), по нажатию под
 * ней раскрываются все темы метками. Выбор метки сворачивает список.
 * allLabel != null — первая метка «все темы» (для поиска), её выбор даёт null.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopicPicker(
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    allLabel: String? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DisclosurePill(
            selected?.let { t(it) } ?: allLabel ?: t("Выберите тему"),
            open = open,
            active = selected != null,
            onClick = { open = !open },
        )
        if (open) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Метки раскрываются по одной, с небольшой задержкой
                val offset = if (allLabel != null) 1 else 0
                if (allLabel != null) {
                    StaggerIn(0) { Chip(allLabel, active = selected == null) { onSelect(null); open = false } }
                }
                GROUP_TOPICS.forEachIndexed { i, topic ->
                    StaggerIn(i + offset) { Chip(t(topic), active = topic == selected) { onSelect(topic); open = false } }
                }
            }
        }
    }
}

/** Форма новой группы: название, описание, тема. */
@Composable
private fun CreateGroupCard(vm: AppViewModel, onClose: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var topic by rememberSaveable { mutableStateOf(GROUP_TOPICS.last()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t("НОВАЯ ГРУППА"), style = MaterialTheme.typography.labelSmall, color = Palette.Mist)
            GlassField(
                name, { name = it.take(40); error = null }, t("Название"),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)),
            )
            GlassField(
                description, { description = it.take(300) }, t("О чём группа"),
                maxLines = 4,
                modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)),
            )
            Text(t("Тема"), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
            TopicPicker(selected = topic, onSelect = { picked -> picked?.let { topic = it } }, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(t("Отмена"), onClick = onClose)
                GlowButton(t("Создать"), enabled = name.isNotBlank(), onClick = {
                    error = vm.createGroup(name, description, topic)
                    if (error == null) onClose()
                })
            }
        }
    }
}
