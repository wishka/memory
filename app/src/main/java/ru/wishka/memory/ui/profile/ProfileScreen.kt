package ru.wishka.memory.ui.profile

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
import ru.wishka.memory.ui.components.Nebula
import ru.wishka.memory.ui.components.ScreenTitle
import ru.wishka.memory.ui.components.Stat
import ru.wishka.memory.ui.components.UserInfo
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.feed.PostCard
import ru.wishka.memory.ui.prism.PrismArt
import ru.wishka.memory.ui.theme.Palette


/**
 * Вкладка «Профиль»: карточка с аватаром и счётчиками, сведения о себе,
 * интересы, группы и подписки, настройки и свои записи.
 */
@Composable
fun ProfileScreen(
    vm: AppViewModel,
    state: UiState,
    contentPadding: PaddingValues,
    version: String,
    /** Имя и сведения — ещё и в сохранённый аккаунт, чтобы пережили перезапуск. */
    onSaveProfile: (String, ProfileInfo) -> Unit,
    onLogout: () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val me = state.me
    val hasInfo = me.city.isNotBlank() || me.birthday.isNotBlank() || me.website.isNotBlank() || me.interests.isNotEmpty()

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
            ) {
                ScreenTitle("Профиль", "@${me.handle}", Modifier.weight(1f))
                if (!editing) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .glass(CircleShape)
                            .clickable { editing = true },
                        contentAlignment = Alignment.Center,
                    ) { Glyph(LineIcon.Edit, Palette.Lilac, size = 20.dp) }
                }
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
                    Avatar(me, size = 104.dp, ring = true)
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
                            StatButton(state.myPosts.size, "записи", Modifier.weight(1f)) { vm.openProfileSection(ProfileSection.Posts) }
                            StatButton(state.followers.size, "подписчики", Modifier.weight(1f)) { vm.openProfileSection(ProfileSection.Followers) }
                            StatButton(state.following.size, "подписки", Modifier.weight(1f)) { vm.openProfileSection(ProfileSection.Following) }
                            StatButton(state.myGroups.size, "группы", Modifier.weight(1f)) { vm.openProfileSection(ProfileSection.Groups) }
                        }
                    }
                }
            }
        }

        if (!editing) {
            item {
                GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("О СЕБЕ", style = MaterialTheme.typography.labelSmall, color = Palette.Mist)
                        if (hasInfo || me.joined.isNotBlank()) UserInfo(me)
                        InterestChips(me.interests)
                        if (!hasInfo) {
                            Text(
                                "Добавьте город, день рождения, сайт и интересы — так вас проще найти.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Palette.Mist,
                            )
                            GhostButton("Заполнить", onClick = { editing = true })
                        }
                    }
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Column {
                    Box(Modifier.clickable { vm.openProfileSection(ProfileSection.Saved) }) {
                        SettingRow(LineIcon.Bookmark, "Сохранённое") {
                            Text("${state.savedPrisms.size}", style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                        }
                    }
                    FadeLine(Modifier.padding(horizontal = 16.dp))
                    SettingRow(LineIcon.Bell, "Уведомления") {
                        Switch(
                            checked = state.notifications,
                            onCheckedChange = vm::setNotifications,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Palette.Ink,
                                checkedTrackColor = Palette.Orchid,
                                uncheckedThumbColor = Palette.Mist,
                                uncheckedTrackColor = Palette.Plum,
                                uncheckedBorderColor = Palette.Line,
                            ),
                        )
                    }
                    FadeLine(Modifier.padding(horizontal = 16.dp))
                    SettingRow(LineIcon.Info, "О приложении") {
                        Text("Memory $version", style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                    }
                    FadeLine(Modifier.padding(horizontal = 16.dp))
                    Box(Modifier.clickable(onClick = onLogout)) {
                        SettingRow(LineIcon.Exit, "Выйти") {}
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

/** Счётчик профиля, который можно нажать. */
@Composable
private fun StatButton(value: Int, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
private fun SettingRow(icon: LineIcon, title: String, trailing: @Composable () -> Unit) {
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
        Field(name, { name = it.take(40) }, "Имя")
        Field(bio, { bio = it.take(160) }, "О себе", singleLine = false)
        Text(
            plural(160 - bio.length, "символ остался", "символа осталось", "символов осталось"),
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Mist,
        )
        Field(city, { city = it.take(40) }, "Город")
        Field(birthday, { birthday = it.take(20) }, "День рождения, например 14 марта")
        Field(website, { website = it.take(80) }, "Сайт или соцсеть")
        // Интересов много — список раскрывается по нажатию на «Интересы»
        var showInterests by rememberSaveable { mutableStateOf(false) }
        DisclosurePill(
            if (interests.isEmpty()) "Интересы" else "Интересы · ${interests.size}",
            open = showInterests,
            active = interests.isNotEmpty(),
            onClick = { showInterests = !showInterests },
        )
        if (showInterests) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            INTERESTS.forEach { tag ->
                val on = tag in interests
                Text(
                    tag,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) Palette.Ink else Palette.Lilac,
                    modifier = Modifier
                        .glass(RoundedCornerShape(50), strong = on)
                        .clickable { interests = if (on) interests - tag else interests + tag }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Отмена", onClick = onCancel)
            GlowButton("Сохранить", enabled = name.isNotBlank(), onClick = {
                onSave(name.trim(), ProfileInfo(bio.trim(), city.trim(), birthday.trim(), website.trim(), interests))
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
