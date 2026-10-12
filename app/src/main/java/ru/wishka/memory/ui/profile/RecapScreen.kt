package ru.wishka.memory.ui.profile

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.wishka.memory.data.Chat
import ru.wishka.memory.data.MediaFiles
import ru.wishka.memory.data.Recap
import ru.wishka.memory.data.RecapImage
import ru.wishka.memory.data.RecapPeriod
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.Avatar
import ru.wishka.memory.ui.components.Chip
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassCard
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.ScreenTitle
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.theme.Palette
import java.time.LocalDate

/**
 * Итоги месяца или года: картинка с личной статистикой — фильмы,
 * километры, книги, места, записи. Её можно опубликовать (в ленту и
 * истории), отправить другу в чат или сохранить в галерею. Стрелки
 * листают прошлые периоды.
 */
@Composable
fun RecapScreen(vm: AppViewModel, state: UiState, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val period = state.recapPeriod
    val back = state.recapBack
    val today = LocalDate.now()
    val recap = remember(state.posts, state.prisms, period, back) {
        if (period == RecapPeriod.Year) vm.recap(RecapPeriod.Year, today.year - back, 0)
        else today.minusMonths(back.toLong()).let { vm.recap(RecapPeriod.Month, it.year, it.monthValue) }
    }
    val poster by produceState<ImageBitmap?>(null, recap, state.me) {
        value = withContext(Dispatchers.Default) { RecapImage.render(recap, state.me).asImageBitmap() }
    }
    var choosingChat by remember { mutableStateOf(false) }

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
                RoundIconButton(LineIcon.Back, Palette.Ink) { vm.closeProfileSection() }
                Spacer(Modifier.width(14.dp))
                ScreenTitle(t("Итоги"), t("Ваш месяц и год в цифрах"))
            }
        }
        item {
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(t("Месяц"), period == RecapPeriod.Month) { vm.setRecapPeriod(RecapPeriod.Month) }
                Chip(t("Год"), period == RecapPeriod.Year) { vm.setRecapPeriod(RecapPeriod.Year) }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(LineIcon.Back) { vm.setRecapBack(back + 1) }
                Text(
                    recap.title + if (back == 0) " · " + t("пока идёт") else "",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Ink,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                if (back > 0) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).glass(CircleShape).clickable { vm.setRecapBack(back - 1) }.graphicsLayer { rotationZ = 180f },
                        contentAlignment = Alignment.Center,
                    ) { Glyph(LineIcon.Back, Palette.Lilac) }
                }
            }
        }
        item {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .aspectRatio(RecapImage.W / RecapImage.H.toFloat())
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.verticalGradient(listOf(Palette.Void, Palette.Night))),
                contentAlignment = Alignment.Center,
            ) {
                poster?.let { Image(it, contentDescription = recap.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlowButton(t("Опубликовать в ленте и историях"), modifier = Modifier.fillMaxWidth(), onClick = {
                    scope.launch {
                        val image = withContext(Dispatchers.IO) { RecapImage.saveAttachment(context, recap, state.me) }
                        if (image != null) {
                            vm.publish(tf("Мои итоги: %s ✦", recap.title), listOf(image))
                            Toast.makeText(context, t("Опубликовано"), Toast.LENGTH_SHORT).show()
                        }
                    }
                })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton(t("Отправить в чат"), onClick = { choosingChat = true })
                    GhostButton(t("В галерею"), onClick = {
                        scope.launch {
                            val saved = withContext(Dispatchers.IO) { RecapImage.saveToGallery(context, recap, state.me) }
                            if (saved) {
                                Toast.makeText(context, t("Сохранено в галерею: Pictures/Memory"), Toast.LENGTH_SHORT).show()
                            } else {
                                // Старый Android — через «Поделиться»: там есть «Сохранить»
                                val image = withContext(Dispatchers.IO) { RecapImage.saveAttachment(context, recap, state.me) }
                                if (image != null) MediaFiles.share(context, recap.title, listOf(image))
                            }
                        }
                    })
                }
                if (recap.empty) {
                    Text(
                        t("Пока считать нечего: отмечайте чек-ины в ленте — фильмы, книги, пробежки, места."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Mist,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (choosingChat) {
        ChatChooser(state.chatList, onPick = { chat ->
            choosingChat = false
            scope.launch {
                val image = withContext(Dispatchers.IO) { RecapImage.saveAttachment(context, recap, state.me) }
                if (image != null) {
                    vm.send(chat.id, tf("Мои итоги: %s", recap.title), listOf(image))
                    Toast.makeText(context, tf("Отправлено: %s", chat.peer.name), Toast.LENGTH_SHORT).show()
                }
            }
        }, onDismiss = { choosingChat = false })
    }
}

/** Кому отправить: список переписок. */
@Composable
private fun ChatChooser(chats: List<Chat>, onPick: (Chat) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.Night).padding(vertical = 16.dp),
        ) {
            Text(t("Отправить в чат"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                items(chats, key = { it.id }) { c ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onPick(c) }.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(c.peer, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(c.peer.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                    }
                }
            }
            GhostButton(t("Отмена"), onClick = onDismiss, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}

/**
 * Карточка «Итоги» в профиле: главные цифры месяца и кнопки «Месяц» /
 * «Год» — открывают картинку итогов.
 */
@Composable
fun RecapCard(vm: AppViewModel, state: UiState) {
    val today = LocalDate.now()
    val recap: Recap = remember(state.posts, state.prisms) { vm.recap(RecapPeriod.Month, today.year, today.monthValue) }
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("ИТОГИ"), style = MaterialTheme.typography.labelSmall, color = Palette.Mist)
            Text(recap.title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
            if (recap.empty) {
                Text(
                    t("Отмечайте чек-ины — в конце месяца здесь соберутся фильмы, километры и книги."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recap.tiles.take(3).forEach { tile ->
                        Column(
                            modifier = Modifier.weight(1f).glass(RoundedCornerShape(16.dp)).padding(10.dp),
                        ) {
                            Text("${tile.emoji} ${tile.value}", style = MaterialTheme.typography.titleMedium, color = Palette.Ink, maxLines = 1)
                            Text(tile.label, style = MaterialTheme.typography.labelSmall, color = Palette.Mist, maxLines = 2)
                        }
                    }
                    repeat(3 - recap.tiles.take(3).size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlowButton(t("Итоги месяца"), onClick = { vm.openRecap(RecapPeriod.Month) })
                GhostButton(t("Итоги года"), onClick = { vm.openRecap(RecapPeriod.Year) })
            }
        }
    }
}
