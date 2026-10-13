package ru.wishka.memory.ui.groups

import androidx.compose.runtime.collectAsState
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.wishka.memory.data.Group
import ru.wishka.memory.data.Backdrop
import ru.wishka.memory.data.GroupAccent
import ru.wishka.memory.data.GroupInfo
import ru.wishka.memory.ui.components.BackdropChooser
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.input.KeyboardType
import ru.wishka.memory.data.MediaFiles
import ru.wishka.memory.data.MediaKind
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.components.FadeLine
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassCard
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.GroupCover
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.profile.RoundIconButton
import ru.wishka.memory.ui.profile.SettingRow
import ru.wishka.memory.ui.settings.MemorySwitch
import ru.wishka.memory.ui.theme.Palette

/**
 * Настройки группы за шестерёнкой. Создатель меняет заставку, название,
 * описание, тему, кто может писать, и может удалить группу. Участник —
 * уведомления и выход.
 */
@Composable
fun GroupSettingsScreen(vm: AppViewModel, group: Group, contentPadding: PaddingValues) {
    val state = vm.state.collectAsState().value
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable(group.id) { mutableStateOf(group.name) }
    var description by rememberSaveable(group.id) { mutableStateOf(group.description) }
    var topic by rememberSaveable(group.id) { mutableStateOf(group.topic) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val changed = name != group.name || description != group.description || topic != group.topic

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val picked = withContext(Dispatchers.IO) { MediaFiles.import(context, uri, MediaKind.Photo) }
            if (picked == null) {
                Toast.makeText(context, t("Не удалось открыть фото (до 200 МБ)"), Toast.LENGTH_SHORT).show()
            } else {
                group.cover?.let { MediaFiles.delete(it) }
                vm.setGroupCover(group.id, picked)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(LineIcon.Back, Palette.Ink) { vm.closeGroupSettings() }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(t("НАСТРОЙКИ ГРУППЫ"), style = MaterialTheme.typography.headlineSmall, color = Palette.Ink, maxLines = 1)
                    Text(group.name, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (group.mine) {
            item {
                Card {
                    Caption(t("Заставка"))
                    GroupCover(
                        group,
                        Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(18.dp)),
                    )
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GlowButton(t("Сменить заставку"), onClick = {
                            coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                        if (group.cover != null) {
                            GhostButton(t("Туманность"), onClick = {
                                group.cover.let { MediaFiles.delete(it) }
                                vm.setGroupCover(group.id, null)
                            })
                        }
                    }
                }
            }
            item {
                Card {
                    Caption(t("О группе"))
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassField(
                            name, { name = it.take(40); error = null }, t("Название"),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)),
                        )
                        GlassField(
                            description, { description = it.take(500) }, t("О чём группа"),
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)),
                        )
                        Text(t("Тема"), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                        TopicPicker(selected = topic, onSelect = { picked -> picked?.let { topic = it } }, modifier = Modifier.fillMaxWidth())
                        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                    }
                    Row(Modifier.padding(16.dp)) {
                        GlowButton(t("Сохранить"), enabled = changed && name.isNotBlank(), onClick = {
                            error = vm.updateGroup(group.id, name, description, topic)
                            if (error == null) Toast.makeText(context, t("Сохранено"), Toast.LENGTH_SHORT).show()
                        })
                    }
                }
            }
            item { InfoCard(vm, group, business = state.plan == ru.wishka.memory.data.PlanTier.Business) }
            item {
                Card {
                    Caption(t("Оформление"))
                    Text(t("Цвет группы — кнопки, цены и метки"), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, modifier = Modifier.padding(horizontal = 16.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GroupAccent.entries.forEach { a ->
                            val active = group.accentColor == a
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(a.color)
                                    .border(if (active) 3.dp else 1.dp, if (active) Palette.Ink else Palette.Line, CircleShape)
                                    .clickable { vm.setGroupAccent(group.id, a) },
                            )
                        }
                    }
                    Text(t("Фон страницы группы"), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, modifier = Modifier.padding(horizontal = 16.dp))
                    BackdropChooser(
                        group.backdrop ?: Backdrop.DEFAULT,
                        onPick = { vm.setGroupBackdrop(group.id, it) },
                        modifier = Modifier.padding(16.dp),
                        onReset = if (group.backdrop != null) ({ vm.setGroupBackdrop(group.id, null) }) else null,
                        resetLabel = t("Как везде"),
                        // Фон страницы группы — Memory+ Бизнес
                        locked = { state.plan != ru.wishka.memory.data.PlanTier.Business },
                        onLocked = { vm.require(ru.wishka.memory.data.PremiumFeature.GroupBackdrop) },
                        business = true,
                    )
                    Hint(t("Товары витрины добавляются и меняются на странице группы — вкладка «Витрина»."))
                }
            }
            item {
                Card {
                    Caption(t("Кто может писать"))
                    Choice(t("Все участники"), selected = !group.onlyOwnerPosts) { vm.setGroupOnlyOwnerPosts(group.id, false) }
                    Choice(t("Только я"), selected = group.onlyOwnerPosts) { vm.setGroupOnlyOwnerPosts(group.id, true) }
                    Hint(t("Закрепить запись — «⋯» на записи в группе."))
                }
            }
        }

        item {
            Card {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(if (group.muted) LineIcon.BellOff else LineIcon.Bell, Palette.Lilac, size = 22.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(t("Уведомления группы"), style = MaterialTheme.typography.bodyLarge, color = Palette.Ink, modifier = Modifier.weight(1f))
                    MemorySwitch(!group.muted) { vm.toggleGroupMute(group.id) }
                }
                FadeLine(Modifier.padding(horizontal = 16.dp))
                Box(Modifier.clickable { confirm = true }) {
                    SettingRow(if (group.mine) LineIcon.Trash else LineIcon.Exit, if (group.mine) t("Удалить группу") else t("Выйти из группы")) {}
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Palette.Night,
            title = {
                Text(
                    if (group.mine) t("Удалить группу?") else t("Выйти из группы?"),
                    style = MaterialTheme.typography.titleMedium, color = Palette.Ink,
                )
            },
            text = {
                Text(
                    if (group.mine) tf("Удалить «%s» вместе с записями? Отменить будет нельзя.", group.name)
                    else tf("Вы перестанете видеть записи «%s» в своих группах. Вернуться можно в любой момент.", group.name),
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Mist,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    if (group.mine) {
                        group.cover?.let { MediaFiles.delete(it) }
                        group.posts.forEach { p -> p.media.forEach { MediaFiles.delete(it) } }
                        // Фото товаров своей группы и её фон: на них никто не ссылается
                        group.shop.forEach { p -> p.photos.forEach { MediaFiles.delete(it) } }
                        group.backdrop?.photo?.takeIf { it.isNotEmpty() }?.let { java.io.File(it).delete() }
                        vm.deleteGroup(group.id)
                    } else {
                        vm.closeGroupSettings()
                        vm.toggleGroup(group.id)
                    }
                }) { Text(if (group.mine) t("Удалить") else t("Выйти"), color = Palette.PearlPink) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(t("Отмена"), color = Palette.Mist) } },
        )
    }
}

/** Подробно о группе: описание, сайт и контакты — видны всем на странице группы. */
@Composable
private fun InfoCard(vm: AppViewModel, group: Group, business: Boolean) {
    val context = LocalContext.current
    var about by rememberSaveable(group.id) { mutableStateOf(group.info.about) }
    var website by rememberSaveable(group.id) { mutableStateOf(group.info.website) }
    var phone by rememberSaveable(group.id) { mutableStateOf(group.info.phone) }
    var email by rememberSaveable(group.id) { mutableStateOf(group.info.email) }
    var address by rememberSaveable(group.id) { mutableStateOf(group.info.address) }
    var hours by rememberSaveable(group.id) { mutableStateOf(group.info.hours) }
    val info = GroupInfo(about, website, phone, email, address, hours)
    val field = Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp))
    Card {
        Caption(t("Подробнее и контакты"))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassField(about, { about = it.take(2000) }, t("Подробное описание: чем занимаетесь, условия, доставка"), maxLines = 8, modifier = field)
            GlassField(website, { website = it.take(200) }, t("Сайт"), singleLine = true, keyboardType = KeyboardType.Uri, modifier = field)
            if (business) {
                GlassField(phone, { phone = it.take(40) }, t("Телефон"), singleLine = true, keyboardType = KeyboardType.Phone, modifier = field)
                GlassField(email, { email = it.take(80) }, t("Почта"), singleLine = true, keyboardType = KeyboardType.Email, modifier = field)
                GlassField(address, { address = it.take(160) }, t("Адрес"), singleLine = true, modifier = field)
                GlassField(hours, { hours = it.take(80) }, t("Часы работы — например, «Пн–Пт 10–19»"), singleLine = true, modifier = field)
            } else {
                // Контакты — в Бизнесе; уже заполненные остаются в карточке группы
                Row(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp))
                        .clickable { vm.require(ru.wishka.memory.data.PremiumFeature.GroupContacts) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t("Телефон, почта, адрес и часы работы"), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, modifier = Modifier.weight(1f))
                    ru.wishka.memory.ui.premium.PlusBadge(business = true)
                }
            }
        }
        Row(Modifier.padding(16.dp)) {
            GlowButton(t("Сохранить"), enabled = info != group.info, onClick = {
                vm.updateGroupInfo(group.id, info)
                // Как сохранилось: без пробелов по краям, сайт — с https://
                vm.state.value.groups.firstOrNull { it.id == group.id }?.info?.let { saved ->
                    about = saved.about; website = saved.website; phone = saved.phone
                    email = saved.email; address = saved.address; hours = saved.hours
                }
                Toast.makeText(context, t("Сохранено"), Toast.LENGTH_SHORT).show()
            })
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(Modifier.padding(vertical = 6.dp)) { content() }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Mist, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
private fun Choice(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = if (selected) Palette.Ink else Palette.Mist, modifier = Modifier.weight(1f))
        if (selected) Glyph(LineIcon.Check, Palette.Lilac, size = 20.dp)
    }
}
