package ru.wishka.memory.ui.profile

import ru.wishka.memory.i18n.tf
import ru.wishka.memory.i18n.t
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.INTERESTS
import ru.wishka.memory.data.ProfileInfo
import ru.wishka.memory.data.User
import ru.wishka.memory.data.plural
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.ProfileSection
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.Avatar
import ru.wishka.memory.ui.components.DisclosurePill
import ru.wishka.memory.ui.components.FadeLine
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassCard
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.InterestChips
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.MenuRow
import ru.wishka.memory.ui.components.Chip
import ru.wishka.memory.ui.components.StaggerIn
import ru.wishka.memory.ui.components.Nebula
import ru.wishka.memory.ui.components.ScreenTitle
import ru.wishka.memory.ui.components.Stat
import ru.wishka.memory.ui.components.UserInfo
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.feed.PostCard
import ru.wishka.memory.ui.prism.PrismArt
import ru.wishka.memory.ui.theme.Palette


/**
 * Вкладка «Профиль»: карточка с аватаром и счётчиками, сведения о себе и
 * «Сохранённое». Настройки — за шестерёнкой вверху, рядом с карандашом.
 * Нажатие на аватар — сменить или убрать фото.
 */
@Composable
fun ProfileScreen(
    vm: AppViewModel,
    state: UiState,
    contentPadding: PaddingValues,
    /** Имя и сведения — ещё и в сохранённый аккаунт, чтобы пережили перезапуск. */
    onSaveProfile: (String, ProfileInfo) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val me = state.me
    val hasInfo = me.city.isNotBlank() || me.birthday.isNotBlank() || me.website.isNotBlank() || me.interests.isNotEmpty()

    // Новый аватар: выбранное фото → обрезка по кругу → в профиль и в аккаунт
    var cropping by rememberSaveable { mutableStateOf<String?>(null) }
    var avatarMenu by remember { mutableStateOf(false) }
    val picker = rememberAvatarPicker { path -> cropping = path }
    fun applyAvatar(path: String) {
        val old = me.avatar
        vm.setAvatar(path)
        onSaveProfile(me.name, vm.profileInfo())
        if (old.isNotEmpty() && old != path) java.io.File(old).delete()
    }
    cropping?.let { source ->
        AvatarCropper(source) { result ->
            cropping = null
            if (result != null) applyAvatar(result)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ScreenTitle(t("Профиль"), "@${me.handle}", Modifier.weight(1f))
                RoundIconButton(LineIcon.Gear) { vm.openSettings() }
                if (!editing) RoundIconButton(LineIcon.Edit) { editing = true }
            }
        }

        item {
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { avatarMenu = true },
                        ) { Avatar(me, size = 104.dp, ring = true) }
                        // Значок камеры: аватар можно сменить
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(32.dp)
                                .clip(CircleShape)
                                .glass(CircleShape, strong = true)
                                .clickable { avatarMenu = true },
                            contentAlignment = Alignment.Center,
                        ) { Glyph(LineIcon.Camera, Palette.Ink, size = 16.dp) }
                        DropdownMenu(
                            expanded = avatarMenu,
                            onDismissRequest = { avatarMenu = false },
                        ) {
                            MenuRow(LineIcon.Image, t("Выбрать из галереи")) { avatarMenu = false; picker.pickPhoto() }
                            MenuRow(LineIcon.Camera, t("Сделать фото")) { avatarMenu = false; picker.takePhoto() }
                            if (me.avatar.isNotEmpty()) {
                                MenuRow(LineIcon.Trash, t("Убрать фото"), color = Palette.Lilac) {
                                    avatarMenu = false
                                    val old = me.avatar
                                    vm.setAvatar("")
                                    onSaveProfile(me.name, vm.profileInfo())
                                    java.io.File(old).delete()
                                }
                            }
                        }
                    }
                    if (editing) {
                        EditForm(
                            user = me,
                            onSave = { name, info ->
                                vm.updateProfile(name, info)
                                onSaveProfile(name, info)
                                editing = false
                            },
                            onCancel = { editing = false },
                        )
                    } else {
                        Text(me.name.uppercase(), style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
                        if (me.bio.isNotBlank()) {
                            Text(me.bio, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(6.dp))
                        FadeLine()
                        // Счётчики — кнопки: каждый открывает свой раздел
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            StatButton(state.myPosts.size, t("записи"), Modifier.weight(1f)) { vm.openProfileSection(ProfileSection.Posts) }
                            StatButton(state.followers.size, t("подписчики"), Modifier.weight(1f)) { vm.openProfileSection(ProfileSection.Followers) }
                            StatButton(state.following.size, t("подписки"), Modifier.weight(1f)) { vm.openProfileSection(ProfileSection.Following) }
                            StatButton(state.myGroups.size, t("группы"), Modifier.weight(1f)) { vm.openProfileSection(ProfileSection.Groups) }
                        }
                    }
                }
            }
        }

        if (!editing) {
            item {
                GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(t("О СЕБЕ"), style = MaterialTheme.typography.labelSmall, color = Palette.Mist)
                        if (hasInfo || me.joined.isNotBlank()) UserInfo(me)
                        InterestChips(me.interests)
                        if (!hasInfo) {
                            Text(
                                t("Добавьте город, день рождения, сайт и интересы — так вас проще найти."),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Palette.Mist,
                            )
                            GhostButton(t("Заполнить"), onClick = { editing = true })
                        }
                    }
                }
            }
            item {
                GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Box(Modifier.clickable { vm.openProfileSection(ProfileSection.Saved) }) {
                        SettingRow(LineIcon.Bookmark, t("Сохранённое")) {
                            Text("${state.savedPrisms.size}", style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

/** Круглая стеклянная кнопка со значком — шестерёнка, карандаш, «назад». */
@Composable
fun RoundIconButton(icon: LineIcon, color: androidx.compose.ui.graphics.Color = Palette.Lilac, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .glass(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(icon, color, size = 20.dp) }
}

/** Счётчик профиля, который можно нажать. */
@Composable
fun StatButton(value: Int, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Stat(value, label)
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
fun SettingRow(icon: LineIcon, title: String, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(icon, Palette.Lilac, size = 22.dp)
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink, modifier = Modifier.weight(1f))
        trailing()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditForm(user: User, onSave: (String, ProfileInfo) -> Unit, onCancel: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(user.name) }
    var bio by rememberSaveable { mutableStateOf(user.bio) }
    var city by rememberSaveable { mutableStateOf(user.city) }
    var birthday by rememberSaveable { mutableStateOf(user.birthday) }
    var website by rememberSaveable { mutableStateOf(user.website) }
    var interests by remember { mutableStateOf(user.interests) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Field(name, { name = it.take(40) }, t("Имя"))
        Field(bio, { bio = it.take(160) }, t("О себе"), singleLine = false)
        Text(
            plural(160 - bio.length, "символ остался", "символа осталось", "символов осталось"),
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Mist,
        )
        Field(city, { city = it.take(40) }, t("Город"))
        Field(birthday, { birthday = it.take(20) }, t("День рождения, например 14 марта"))
        Field(website, { website = it.take(80) }, t("Сайт или соцсеть"))
        // Интересов много — список раскрывается по нажатию на «Интересы»
        var showInterests by rememberSaveable { mutableStateOf(false) }
        DisclosurePill(
            if (interests.isEmpty()) t("Интересы") else tf("Интересы · %s", interests.size),
            open = showInterests,
            active = interests.isNotEmpty(),
            onClick = { showInterests = !showInterests },
        )
        if (showInterests) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Интересы раскрываются по одному, с небольшой задержкой
            INTERESTS.forEachIndexed { i, tag ->
                val on = tag in interests
                StaggerIn(i) { Chip(t(tag), active = on) { interests = if (on) interests - tag else interests + tag } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton(t("Отмена"), onClick = onCancel)
            GlowButton(t("Сохранить"), enabled = name.isNotBlank(), onClick = {
                onSave(name.trim(), ProfileInfo(bio.trim(), city.trim(), birthday.trim(), website.trim(), interests, user.avatar))
            })
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, placeholder: String, singleLine: Boolean = true) {
    GlassField(
        value, onChange, placeholder,
        singleLine = singleLine,
        maxLines = 4,
        modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)),
    )
}
