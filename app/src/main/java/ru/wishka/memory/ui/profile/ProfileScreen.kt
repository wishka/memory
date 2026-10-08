package ru.wishka.memory.ui.profile

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.plural
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.Avatar
import ru.wishka.memory.ui.components.FadeLine
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassCard
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.ScreenTitle
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.feed.PostCard
import ru.wishka.memory.ui.theme.Palette

/** Вкладка «Профиль»: карточка с аватаром, счётчики, свои записи, настройки. */
@Composable
fun ProfileScreen(
    vm: AppViewModel,
    state: UiState,
    contentPadding: PaddingValues,
    version: String,
    /** Новое имя — ещё и в сохранённый аккаунт, чтобы пережило перезапуск. */
    onRename: (String) -> Unit,
    onLogout: () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val me = state.me

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
                            name = me.name,
                            bio = me.bio,
                            onSave = { name, bio ->
                                vm.updateProfile(name, bio)
                                if (name.isNotBlank()) onRename(name)
                                editing = false
                            },
                            onCancel = { editing = false },
                        )
                    } else {
                        Text(me.name.uppercase(), style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
                        if (me.bio.isNotBlank()) {
                            Text(
                                me.bio,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Palette.Mist,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        FadeLine()
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            Stat(state.myPosts.size, "записи", Modifier.weight(1f))
                            Stat(128, "подписчики", Modifier.weight(1f))
                            Stat(64, "подписки", Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Column {
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

        item {
            Text(
                if (state.myPosts.isEmpty()) "МОИ ЗАПИСИ" else "МОИ ЗАПИСИ · ${state.myPosts.size}",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Mist,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }
        if (state.myPosts.isEmpty()) {
            item {
                Text(
                    "Пока ни одной. Поделитесь первым моментом во вкладке «Лента».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
        items(state.myPosts, key = { it.id }) { post ->
            PostCard(post, onLike = { vm.toggleLike(post.id) })
        }
    }
}

@Composable
private fun Stat(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Mist)
    }
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

@Composable
private fun EditForm(name: String, bio: String, onSave: (String, String) -> Unit, onCancel: () -> Unit) {
    var newName by rememberSaveable { mutableStateOf(name) }
    var newBio by rememberSaveable { mutableStateOf(bio) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassField(
            newName, { newName = it.take(40) }, "Имя",
            singleLine = true,
            modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)),
        )
        GlassField(
            newBio, { newBio = it.take(160) }, "О себе",
            maxLines = 4,
            modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)),
        )
        Text(
            plural(160 - newBio.length, "символ остался", "символа осталось", "символов осталось"),
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Mist,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Отмена", onClick = onCancel)
            GlowButton("Сохранить", enabled = newName.isNotBlank(), onClick = { onSave(newName, newBio) })
        }
    }
}
