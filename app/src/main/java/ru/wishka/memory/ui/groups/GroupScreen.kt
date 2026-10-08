package ru.wishka.memory.ui.groups

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.Group
import ru.wishka.memory.data.plural
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassCard
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.Nebula
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.feed.PostCard
import ru.wishka.memory.ui.theme.Palette

/** Страница группы: обложка, описание, вступление, записи участников. */
@Composable
fun GroupScreen(vm: AppViewModel, state: UiState, group: Group, contentPadding: PaddingValues) {
    var text by rememberSaveable(group.id) { mutableStateOf("") }
    var confirmDelete by rememberSaveable(group.id) { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            // Обложка во всю ширину, под строкой состояния
            Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                Nebula(group.seed, Modifier.fillMaxSize())
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Palette.Void)),
                )
                Box(
                    modifier = Modifier
                        .padding(top = contentPadding.calculateTopPadding(), start = 12.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .glass(CircleShape, strong = true)
                        .clickable { vm.closeGroup() },
                    contentAlignment = Alignment.Center,
                ) { Glyph(LineIcon.Back, Palette.Ink) }
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(group.name.uppercase(), style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
                    Text(
                        "${group.topic.uppercase()} · ${plural(group.members, "участник", "участника", "участников").uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.Mist,
                    )
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (group.description.isNotBlank()) {
                    Text(group.description, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        group.mine -> GhostButton("Удалить группу", onClick = { confirmDelete = true })
                        group.joined -> GhostButton("Выйти из группы", onClick = { vm.toggleGroup(group.id) })
                        else -> GlowButton("Вступить", onClick = { vm.toggleGroup(group.id) })
                    }
                }
                if (confirmDelete) {
                    GlassCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Удалить «${group.name}» вместе с записями? Отменить будет нельзя.",
                                style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GhostButton("Отмена", onClick = { confirmDelete = false })
                                GlowButton("Удалить", onClick = { vm.deleteGroup(group.id) })
                            }
                        }
                    }
                }
            }
        }

        if (group.joined) {
            item {
                GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Column {
                        GlassField(text, { text = it.take(2000) }, "Написать в группу")
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            GlowButton("Опубликовать", enabled = text.isNotBlank(), onClick = {
                                vm.postInGroup(group.id, text)
                                text = ""
                            })
                        }
                    }
                }
            }
        } else {
            item {
                Text(
                    "Вступите, чтобы писать в группу.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }

        item {
            Text(
                if (group.posts.isEmpty()) "ЗАПИСЕЙ ПОКА НЕТ" else "ЗАПИСИ · ${group.posts.size}",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Mist,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        items(group.posts, key = { it.id }) { post ->
            PostCard(
                post,
                onLike = { vm.toggleGroupPostLike(group.id, post.id) },
                onAuthor = { vm.openPerson(post.author.id) },
                onComments = { vm.openComments(post.id) },
                onOpenMedia = vm::openMedia,
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
