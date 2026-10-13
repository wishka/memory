package ru.wishka.memory.ui.settings

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import ru.wishka.memory.data.Guide
import ru.wishka.memory.ui.guide.guidePage
import ru.wishka.memory.ui.guide.GuideUi
import ru.wishka.memory.i18n.t
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.wishka.memory.data.Audience
import ru.wishka.memory.data.Legal
import ru.wishka.memory.data.LegalDoc
import ru.wishka.memory.data.NotifySound
import ru.wishka.memory.data.NudgeKind
import ru.wishka.memory.data.SOUND_KINDS
import ru.wishka.memory.data.SoundPlayer
import ru.wishka.memory.data.SoundPrefs
import ru.wishka.memory.data.soundTitle
import ru.wishka.memory.push.MemoryNotifications
import ru.wishka.memory.ui.components.BackdropChooser
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import ru.wishka.memory.data.Feedback
import ru.wishka.memory.data.InviteAction
import ru.wishka.memory.data.amountNumber
import ru.wishka.memory.data.growthStats
import ru.wishka.memory.ui.components.shareInvite
import ru.wishka.memory.data.DevicePrefs
import ru.wishka.memory.data.Notifications
import ru.wishka.memory.data.Privacy
import ru.wishka.memory.data.User
import ru.wishka.memory.i18n.L10n
import ru.wishka.memory.i18n.Lang
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.SettingsPage
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.Avatar
import ru.wishka.memory.ui.components.Chip
import ru.wishka.memory.ui.components.FadeLine
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassCard
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.profile.RoundIconButton
import ru.wishka.memory.ui.profile.SettingRow
import ru.wishka.memory.ui.theme.Palette

/** Что настройкам нужно от экрана входа: выход, смена пароля, удаление аккаунта. */
class AccountActions(
    val logout: () -> Unit,
    /** Ответ — текст ошибки или null. */
    val changePassword: suspend (current: String, new: String, repeat: String) -> String?,
    /** Ответ — текст ошибки или null (аккаунт удалён, выполнен выход). */
    val deleteAccount: suspend (password: String) -> String?,
)

/**
 * Настройки за шестерёнкой в профиле. Список разделов и страница
 * каждого раздела — на одном экране, «назад» ведёт со страницы в список.
 */
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    state: UiState,
    page: SettingsPage,
    contentPadding: PaddingValues,
    version: String,
    prefs: DevicePrefs,
    account: AccountActions,
) {
    // Гид: поиск и раскрытые темы переживают переход между главами
    var guideQuery by rememberSaveable { mutableStateOf("") }
    var guideOpen by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val guide = GuideUi(
        query = guideQuery,
        onQuery = { guideQuery = it.take(60) },
        expanded = guideOpen.toSet(),
        onToggle = { key -> guideOpen = if (key in guideOpen) guideOpen - key else guideOpen + key },
    )
    // Новая страница или глава гида — с самого верха
    val list = rememberLazyListState()
    LaunchedEffect(page, state.guideChapter) { list.scrollToItem(0) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = list,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(LineIcon.Back, Palette.Ink) { vm.settingsBack() }
                Spacer(Modifier.width(14.dp))
                Text(
                    page.title.uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Palette.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when (page) {
            SettingsPage.Main -> mainPage(vm, state, version, account)
            SettingsPage.Security -> item { SecurityPage(account) }
            SettingsPage.Privacy -> item { PrivacyPage(state.privacy, vm::setPrivacy) { vm.openLegal(LegalDoc.Recommendations) } }
            SettingsPage.Legal -> item { LegalPage(vm) }
            SettingsPage.Guide -> guidePage(vm, state, guide)
            SettingsPage.Blacklist -> blacklistPage(vm, state.blockedPeople)
            SettingsPage.Notifications -> item { NotificationsPage(vm, state) }
            SettingsPage.TextSize -> item { TextSizePage(prefs) }
            SettingsPage.Language -> item { LanguagePage(prefs) }
            SettingsPage.About -> item { AboutPage(version, vm) }
            SettingsPage.Growth -> item { GrowthPage(vm, state, version) }
            SettingsPage.Sounds -> item { SoundsPage(state) }
            SettingsPage.Wallpaper -> item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BackdropChooser(state.backdrop, vm::setBackdrop)
                    Text(
                        t("Фон для всех переписок. Свой фон для одной переписки — в её меню «⋯» вверху."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Mist,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ==================== СПИСОК РАЗДЕЛОВ ====================

private fun LazyListScope.mainPage(vm: AppViewModel, state: UiState, version: String, account: AccountActions) {
    item {
        Card {
            NavRow(
                LineIcon.Info, t("Гид по Memory"),
                tf("%s из %s", state.guideRead.size.coerceAtMost(Guide.topicCount), Guide.topicCount),
            ) { vm.openGuide() }
        }
    }
    item {
        Card {
            NavRow(LineIcon.Shield, t("Безопасность"), "") { vm.openSettings(SettingsPage.Security) }
            Divider()
            NavRow(LineIcon.Lock, t("Приватность"), "") { vm.openSettings(SettingsPage.Privacy) }
            Divider()
            NavRow(LineIcon.Block, t("Чёрный список"), if (state.blocked.isEmpty()) "" else "${state.blocked.size}") {
                vm.openSettings(SettingsPage.Blacklist)
            }
        }
    }
    item {
        Card {
            NavRow(LineIcon.Bell, t("Уведомления"), if (state.notifications.all) t("Вкл.") else t("Выкл.")) {
                vm.openSettings(SettingsPage.Notifications)
            }
            Divider()
            NavRow(LineIcon.Image, t("Фон переписок"), state.backdrop.kind.title) { vm.openSettings(SettingsPage.Wallpaper) }
            Divider()
            NavRow(LineIcon.TextSize, t("Размер текста"), textScaleTitle(L10n.textScale)) { vm.openSettings(SettingsPage.TextSize) }
            Divider()
            NavRow(LineIcon.Globe, t("Язык"), L10n.choice.title) { vm.openSettings(SettingsPage.Language) }
            Divider()
            NavRow(LineIcon.Info, t("О приложении"), "Memory $version") { vm.openSettings(SettingsPage.About) }
            Divider()
            NavRow(LineIcon.Document, t("Документы"), "${Legal.MIN_AGE}+") { vm.openSettings(SettingsPage.Legal) }
        }
    }
    item {
        Card {
            NavRow(LineIcon.Gift, t("Друзья и идеи"), state.inviteCode) { vm.openSettings(SettingsPage.Growth) }
        }
    }
    item {
        var confirm by rememberSaveable { mutableStateOf(false) }
        Card {
            Box(Modifier.clickable { confirm = true }) { SettingRow(LineIcon.Exit, t("Выйти")) {} }
        }
        if (confirm) {
            AlertDialog(
                onDismissRequest = { confirm = false },
                containerColor = Palette.Night,
                title = { Text(t("Выйти из аккаунта?"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink) },
                text = {
                    Text(
                        t("Записи и переписка останутся на этом телефоне — они вернутся, когда вы войдёте снова."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Mist,
                    )
                },
                confirmButton = {
                    TextButton(onClick = { confirm = false; account.logout() }) { Text(t("Выйти"), color = Palette.Lilac) }
                },
                dismissButton = {
                    TextButton(onClick = { confirm = false }) { Text(t("Отмена"), color = Palette.Mist) }
                },
            )
        }
    }
}

// ==================== БЕЗОПАСНОСТЬ ====================

@Composable
private fun SecurityPage(account: AccountActions) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }

    var deleting by rememberSaveable { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }
    var deleteError by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Caption(t("Смена пароля"))
                PasswordField(current, { current = it; message = null }, t("Текущий пароль"))
                PasswordField(new, { new = it; message = null }, t("Новый пароль"))
                PasswordField(repeat, { repeat = it; message = null }, t("Повторите новый пароль"))
                Text(
                    t("От 8 символов, с буквами и цифрами."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                )
                message?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = if (success) Palette.Lilac else MaterialTheme.colorScheme.error)
                }
                GlowButton(
                    t("Сменить пароль"),
                    enabled = !busy && current.isNotEmpty() && new.isNotEmpty() && repeat.isNotEmpty(),
                    onClick = {
                        busy = true
                        scope.launch {
                            val error = account.changePassword(current, new, repeat)
                            busy = false
                            success = error == null
                            message = error ?: t("Пароль изменён")
                            if (error == null) { current = ""; new = ""; repeat = "" }
                        }
                    },
                )
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Caption(t("Как защищён аккаунт"))
                Bullet(t("Пароль не хранится — только его отпечаток (PBKDF2, 120 000 итераций)."))
                Bullet(t("После пяти неверных попыток вход закрывается на 30 секунд."))
                Bullet(t("Сессия зашифрована ключом из хранилища Android Keystore."))
                Bullet(t("Записи, переписка и фото хранятся только на этом телефоне."))
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Caption(t("Удаление аккаунта"))
                Text(
                    t("Аккаунт, записи и переписка будут удалены с этого телефона. Отменить будет нельзя."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                )
                if (!deleting) {
                    GhostButton(t("Удалить аккаунт"), onClick = { deleting = true })
                } else {
                    PasswordField(deletePassword, { deletePassword = it; deleteError = null }, t("Пароль для подтверждения"))
                    deleteError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton(t("Отмена"), onClick = { deleting = false; deletePassword = ""; deleteError = null })
                        GlowButton(t("Удалить навсегда"), enabled = deletePassword.isNotEmpty() && !busy, onClick = {
                            busy = true
                            scope.launch {
                                deleteError = account.deleteAccount(deletePassword)
                                busy = false
                            }
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, placeholder: String) {
    GlassField(
        value, { onChange(it.take(64)) }, placeholder,
        password = true,
        modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)),
    )
}

// ==================== ПРИВАТНОСТЬ ====================

@Composable
private fun PrivacyPage(privacy: Privacy, onChange: (Privacy) -> Unit, onRecsDoc: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card {
            SwitchRow(t("Закрытый профиль"), privacy.closedProfile) { onChange(privacy.copy(closedProfile = it)) }
        }
        Hint(
            if (privacy.closedProfile) t("Записи и чек-ины видят только ваши друзья. Заявки в друзья можно принять или отклонить в профиле.")
            else t("Закройте профиль, чтобы записи и чек-ины видели только ваши друзья. Нынешние друзья останутся.")
        )
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AudiencePicker(t("Кто видит сведения о себе"), t("Город, день рождения, сайт и интересы"), privacy.profileInfo) {
                    onChange(privacy.copy(profileInfo = it))
                }
                FadeLine()
                AudiencePicker(t("Кто видит моих друзей"), null, privacy.followLists) {
                    onChange(privacy.copy(followLists = it))
                }
                FadeLine()
                AudiencePicker(t("Кто может написать мне"), null, privacy.messages) {
                    onChange(privacy.copy(messages = it))
                }
                FadeLine()
                AudiencePicker(t("Кто может комментировать записи"), null, privacy.comments) {
                    onChange(privacy.copy(comments = it))
                }
            }
        }
        Card {
            SwitchRow(t("Показывать, что я в сети"), privacy.showOnline) { onChange(privacy.copy(showOnline = it)) }
            Divider()
            SwitchRow(t("Показывать день рождения"), privacy.showBirthday) { onChange(privacy.copy(showBirthday = it)) }
        }
        Hint(t("«Друзья» — люди, с которыми вы друзья: вы приняли их заявку или они вашу. Чтобы скрыть от одного человека всё сразу, добавьте его в чёрный список."))
        Card {
            SwitchRow(t("Персональные рекомендации"), privacy.personalRecs) { onChange(privacy.copy(personalRecs = it)) }
        }
        Hint(
            if (privacy.personalRecs) t("Полка советует фильмы и книги по вашим оценкам и вкусу похожих людей. Лента рекомендациями не сортируется.")
            else t("Рекомендации выключены: подборка «Для вас», совет после чек-ина и «Похожий вкус» не показываются.")
        )
        Text(
            t("Как работают рекомендации"),
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Lilac,
            modifier = Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onRecsDoc),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AudiencePicker(title: String, subtitle: String?, value: Audience, onPick: (Audience) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Audience.entries.forEach { a -> Chip(a.title, active = a == value) { onPick(a) } }
        }
    }
}

// ==================== ЧЁРНЫЙ СПИСОК ====================

private fun LazyListScope.blacklistPage(vm: AppViewModel, people: List<User>) {
    if (people.isEmpty()) {
        item {
            Hint(t("Здесь пусто. Чтобы заблокировать человека, откройте его страницу и нажмите «⋯» вверху справа — или долго удерживайте переписку с ним в «Чатах»."))
        }
    } else {
        item { Hint(t("Записи, комментарии и сообщения этих людей вам не видны, и они не могут вам написать.")) }
    }
    items(people, key = { it.id }) { person ->
        GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(person, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(person.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("@${person.handle}", style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                }
                GhostButton(t("Разблокировать"), onClick = { vm.unblock(person.id) })
            }
        }
    }
}

// ==================== УВЕДОМЛЕНИЯ ====================

@Composable
private fun NotificationsPage(vm: AppViewModel, state: UiState) {
    val n = state.notifications
    fun set(change: (Notifications) -> Notifications) = vm.setNotifications(change(n))
    val mutedGroups = state.groups.filter { it.joined && it.muted }
    val mutedChats = state.chats.filter { it.muted }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card {
            SwitchRow(t("Все уведомления"), n.all) { v -> set { it.copy(all = v) } }
        }
        Card {
            SwitchRow(t("Сообщения"), n.messages, enabled = n.all) { v -> set { it.copy(messages = v) } }
            Divider()
            SwitchRow(t("Новые PRISM друзей"), n.prism, enabled = n.all) { v -> set { it.copy(prism = v) } }
            Divider()
            SwitchRow(t("Лайки и комментарии"), n.reactions, enabled = n.all) { v -> set { it.copy(reactions = v) } }
            Divider()
            SwitchRow(t("Заявки в друзья"), n.follows, enabled = n.all) { v -> set { it.copy(follows = v) } }
            Divider()
            SwitchRow(t("Группы: новые записи и участники"), n.groups, enabled = n.all) { v -> set { it.copy(groups = v) } }
            Divider()
            SwitchRow(t("Друзья: чек-ины, фильмы, книги, тренировки"), n.friends, enabled = n.all) { v -> set { it.copy(friends = v) } }
            Divider()
            SwitchRow(t("Итоги месяца и года"), n.recap, enabled = n.all) { v -> set { it.copy(recap = v) } }
        }
        Card {
            NavRow(LineIcon.Volume, t("Звуки"), t("Свой у каждого вида")) { vm.openSettings(SettingsPage.Sounds) }
        }
        Hint(t("Уведомления — про то, что важно именно вам: друг отметил фильм, который вы обсуждали, в вашей группе новый участник, готовы итоги месяца. Никаких «вы давно не заходили»."))
        if (mutedGroups.isNotEmpty() || mutedChats.isNotEmpty()) {
            Caption(t("Без звука"), Modifier.padding(horizontal = 24.dp))
            Card {
                Column {
                    mutedGroups.forEachIndexed { i, g ->
                        if (i > 0) Divider()
                        MutedRow(g.name, t("Группа")) { vm.toggleGroupMute(g.id) }
                    }
                    mutedChats.forEachIndexed { i, c ->
                        if (i > 0 || mutedGroups.isNotEmpty()) Divider()
                        MutedRow(c.peer.name, t("Переписка")) { vm.toggleChatMute(c.id) }
                    }
                }
            }
        } else {
            Hint(t("Отключить уведомления отдельной группы можно на её странице, отдельной переписки — долгим нажатием в «Чатах»."))
        }
    }
}

@Composable
private fun MutedRow(title: String, kind: String, onUnmute: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(LineIcon.BellOff, Palette.Mist, size = 20.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(kind, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
        }
        GhostButton(t("Включить"), onClick = onUnmute)
    }
}

// ==================== ЗВУКИ ====================

/**
 * Свой звук у каждого вида уведомлений — их узнаёшь, не глядя на экран.
 * Касание варианта — сразу слышно, как звучит.
 */
@Composable
private fun SoundsPage(state: UiState) {
    val context = LocalContext.current
    val prefs = remember { SoundPrefs(context) }
    var version by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<NudgeKind?>(null) }
    val current = remember(version) { prefs.all() }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card {
            SOUND_KINDS.forEachIndexed { i, kind ->
                if (i > 0) Divider()
                NavRow(LineIcon.Volume, kind.soundTitle, current.getValue(kind).title) { editing = kind }
            }
        }
        Hint(t("Звуки Memory — стекло, звёзды и космос, в стиле приложения. Звук играет и в пуше, и в плашке, когда приложение открыто."))
        if (!state.notifications.all) Hint(t("Сейчас все уведомления выключены — звуков не будет."))
    }
    editing?.let { kind ->
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = Palette.Night,
            title = { Text(kind.soundTitle, style = MaterialTheme.typography.titleMedium, color = Palette.Ink) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    NotifySound.entries.forEach { sound ->
                        ChoiceRow(sound.title + if (sound == NotifySound.default(kind)) " · " + t("по умолчанию") else "", current.getValue(kind) == sound) {
                            MemoryNotifications.changeSound(context, kind, sound)
                            SoundPlayer.play(context, sound)
                            version++
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editing = null }) { Text(t("Готово"), color = Palette.Lilac) } },
        )
    }
}

// ==================== РАЗМЕР ТЕКСТА ====================

fun textScaleTitle(scale: Float): String = when {
    scale < 0.95f -> t("Мелкий")
    scale < 1.05f -> t("Обычный")
    scale < 1.2f -> t("Крупный")
    else -> t("Очень крупный")
}

@Composable
private fun TextSizePage(prefs: DevicePrefs) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card {
            Column {
                DevicePrefs.TEXT_SCALES.forEachIndexed { i, scale ->
                    if (i > 0) Divider()
                    ChoiceRow(textScaleTitle(scale), selected = L10n.textScale == scale) { prefs.setTextScale(scale) }
                }
            }
        }
        // Образец — сразу в выбранном размере
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Caption(t("Образец"))
                Text(t("Вера Лунная"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                Text(
                    t("Сегодня небо было как акварель: фиолетовый перетекал в синий прямо над крышами."),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.Ink,
                )
                Text(t("@vera.moon · 12 мин"), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
            }
        }
        Hint(t("Размер текста меняется во всём приложении, на этом телефоне."))
    }
}

// ==================== ЯЗЫК ====================

@Composable
private fun LanguagePage(prefs: DevicePrefs) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card {
            Column {
                Lang.entries.forEachIndexed { i, lang ->
                    if (i > 0) Divider()
                    ChoiceRow(lang.title, selected = L10n.choice == lang) { prefs.setLanguage(lang) }
                }
            }
        }
        Hint(t("Переводится интерфейс. Записи и сообщения остаются на том языке, на котором их написали."))
    }
}

// ==================== О ПРИЛОЖЕНИИ ====================

@Composable
private fun AboutPage(version: String, vm: AppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("MEMORY", style = MaterialTheme.typography.displaySmall, color = Palette.Ink)
                Text(tf("Версия %s", version), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                Text(
                    t("Соцсеть о моментах: лента, истории, PRISM, чаты и группы."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                )
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Caption(t("Данные"))
                Bullet(t("Пока у Memory нет сервера, всё хранится на этом телефоне."))
                Bullet(t("Фото и видео копируются в память приложения и не видны другим приложениям."))
            }
        }
        Card {
            NavRow(LineIcon.Document, t("Документы"), "${Legal.MIN_AGE}+") { vm.openSettings(SettingsPage.Legal) }
            Divider()
            NavRow(LineIcon.Info, t("Лицензии и авторы"), "") { vm.openLegal(LegalDoc.Licenses) }
        }
        Hint(t("Карты © участники OpenStreetMap. Гифки — Powered by GIPHY. Шрифты Unbounded и Inter — SIL Open Font License."))
    }
}

// ==================== ДОКУМЕНТЫ ====================

/**
 * Соглашение, политика, согласие, правила, рекомендации и лицензии — те
 * же тексты, что на сайте (docs/legal). Контакты для обращений и жалоб.
 */
@Composable
private fun LegalPage(vm: AppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card {
            LegalDoc.entries.forEachIndexed { i, doc ->
                if (i > 0) Divider()
                NavRow(LineIcon.Document, doc.title, "") { vm.openLegal(doc) }
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Caption(t("Связаться"))
                Bullet(tf("Вопросы о данных и отзыв согласия: %s", Legal.PRIVACY_EMAIL))
                Bullet(tf("Жалобы на контент: %s — или «Пожаловаться» в меню записи, сообщения, PRISM", Legal.ABUSE_EMAIL))
                Bullet(tf("Оператор: %s", Legal.OPERATOR))
            }
        }
        Card {
            NavRow(LineIcon.Shield, t("Удалить аккаунт и отозвать согласие"), "") { vm.openSettings(SettingsPage.Security) }
        }
        Hint(tf("Memory — для людей от %s лет. Версия документов: %s.", Legal.MIN_AGE, Legal.VERSION))
        if (!Legal.filled) Hint(t("Черновик: данные оператора ещё не заполнены."))
    }
}

// ==================== ДРУЗЬЯ И ИДЕИ ====================

/**
 * Пригласить друзей, свои цифры роста и «Предложить идею». K = i × c:
 * сколько приглашений отправлено и какая доля приходит — вторую цифру
 * посчитает сервер, до него показываем оценку.
 */
@Composable
private fun GrowthPage(vm: AppViewModel, state: UiState, version: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val stats = growthStats(state.growth)
    var text by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Caption(t("Ваш код приглашения"))
                Text(state.inviteCode, style = MaterialTheme.typography.displaySmall, color = Palette.Ink)
                Text(
                    t("Друг откроет ссылку — Memory запомнит, кто его позвал. Лучше всего работают приглашения к делу: дорисовать рисунок, взять книгу, пойти на вылазку."),
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Mist,
                )
                GlowButton(t("Пригласить друзей"), onClick = { shareInvite(context, vm, InviteAction.App) })
            }
        }
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Caption(t("Ваш вклад в рост"))
                Bullet(tf("Приглашений отправлено: %s", stats.invitesSent))
                Bullet(tf("Карточек отправлено: %s", stats.cardsShared))
                Bullet(tf("Чек-инов: %s", stats.checkIns))
                state.invitedBy?.let { Bullet(tf("Вас позвал(а) %s", it.from.ifEmpty { it.code })) }
                Text(
                    tf("Оценка K ≈ %s: приглашения × доля пришедших (берём 10%%, точную посчитает сервер).", amountNumber(Math.round(stats.kEstimate() * 100) / 100.0)),
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Mist,
                )
            }
        }
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Caption(t("Идеи и ошибки"))
                Text(
                    t("Что добавить или что сломалось? Идея, которую предлагают многие, точно попадёт в приложение. Откроется GitHub — понадобится учётная запись."),
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Mist,
                )
                GlassField(text, { text = it.take(1000) }, t("Опишите в паре предложений"), Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlowButton(t("Предложить идею"), enabled = text.isNotBlank(), onClick = {
                        vm.logFeedback(true)
                        runCatching { uri.openUri(Feedback.link(true, text.trim(), version)) }
                        text = ""
                    })
                    GhostButton(t("Сообщить об ошибке"), onClick = {
                        vm.logFeedback(false)
                        runCatching { uri.openUri(Feedback.link(false, text.trim().ifEmpty { t("Опишите, что случилось") }, version)) }
                        text = ""
                    })
                }
            }
        }
    }
}

// ==================== ОБЩЕЕ ====================

@Composable
private fun Card(content: @Composable () -> Unit) {
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column { content() }
    }
}

@Composable
private fun Divider() = FadeLine(Modifier.padding(horizontal = 16.dp))

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Mist, modifier = modifier)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, modifier = Modifier.padding(horizontal = 24.dp))
}

@Composable
private fun Bullet(text: String) {
    Row {
        Text("◇", style = MaterialTheme.typography.bodyMedium, color = Palette.Lilac)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.Ink)
    }
}

/** Строка-переход: значок, название, значение справа и стрелка. */
@Composable
private fun NavRow(icon: LineIcon, title: String, value: String, onClick: () -> Unit) {
    Box(Modifier.clickable(onClick = onClick)) {
        SettingRow(icon, title) {
            if (value.isNotEmpty()) {
                Text(value, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, maxLines = 1)
                Spacer(Modifier.width(6.dp))
            }
            // Стрелка «назад», развёрнутая вправо
            Glyph(LineIcon.Back, Palette.Mist, size = 16.dp, modifier = Modifier.padding(start = 2.dp).graphicsLayer { scaleX = -1f })
        }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) Palette.Ink else Palette.Mist,
            modifier = Modifier.weight(1f),
        )
        MemorySwitch(checked, enabled, onChange)
    }
}

@Composable
fun MemorySwitch(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Palette.Ink,
            checkedTrackColor = Palette.Orchid,
            uncheckedThumbColor = Palette.Mist,
            uncheckedTrackColor = Palette.Plum,
            uncheckedBorderColor = Palette.Line,
        ),
    )
}

/** Вариант из нескольких: галочка у выбранного. */
@Composable
private fun ChoiceRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = if (selected) Palette.Ink else Palette.Mist, modifier = Modifier.weight(1f))
        if (selected) Glyph(LineIcon.Check, Palette.Lilac, size = 20.dp)
    }
}
