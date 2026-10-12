package ru.wishka.memory.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ru.wishka.memory.data.BookStatus
import ru.wishka.memory.data.Demo
import ru.wishka.memory.data.DemoTaste
import ru.wishka.memory.data.EchoLine
import ru.wishka.memory.data.InviteAction
import ru.wishka.memory.data.PlanReason
import ru.wishka.memory.data.Recommendation
import ru.wishka.memory.data.RunPlan
import ru.wishka.memory.data.ShareCardSpec
import ru.wishka.memory.data.Shelf
import ru.wishka.memory.data.ShelfItem
import ru.wishka.memory.data.SportStats
import ru.wishka.memory.data.amountNumber
import ru.wishka.memory.data.minutesTitle
import ru.wishka.memory.data.paceClock
import ru.wishka.memory.data.plural
import ru.wishka.memory.data.tasteMatch
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.ShelfTab
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.Avatar
import ru.wishka.memory.ui.components.Chip
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassCard
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.ScreenTitle
import ru.wishka.memory.ui.components.Stars
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.components.shareCard
import ru.wishka.memory.ui.theme.Palette

/**
 * Полка — профиль, который собирается сам: кино, книги, спорт, места из
 * чек-инов, подборка «для вас» по вкусу друзей, план бега и права,
 * которые открываются за действия. Ничего не надо заполнять вручную.
 */
@Composable
fun ShelfScreen(vm: AppViewModel, state: UiState, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shelf = remember(state.posts, state.shelfMarks, state.me.id) { state.shelf }
    // Подборка и план — не на каждую перерисовку: каждый вызов заново собирает полку
    val recs = remember(shelf, state.dismissedRecs, state.blocked) {
        state.recommendations("film", shelf = shelf) to state.recommendations("book", shelf = shelf)
    }
    val plan = remember(state.posts, state.me.id) { state.runPlan }
    var rating by remember { mutableStateOf<Pair<String, String>?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(LineIcon.Back, Palette.Ink) { vm.closeProfileSection() }
                Spacer(Modifier.width(14.dp))
                ScreenTitle(t("Полка"), t("Собирается сама из ваших чек-инов"), Modifier.weight(1f))
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShelfTab.entries.forEach { tab -> Chip("${tab.emoji} ${tab.title}", state.shelfTab == tab) { vm.selectShelfTab(tab) } }
            }
        }
        when (state.shelfTab) {
            ShelfTab.ForYou -> forYou(vm, state, shelf, recs, plan) { rating = it }
            ShelfTab.Films -> films(vm, shelf) { rating = it }
            ShelfTab.Books -> books(shelf) { rating = it }
            ShelfTab.Sport -> sport(shelf)
            ShelfTab.Places -> places(shelf)
            ShelfTab.Progress -> progress(state)
        }
        item {
            GhostButton(
                t("Поделиться полкой"),
                modifier = Modifier.padding(horizontal = 16.dp),
                onClick = {
                    val spec = ShareCardSpec(
                        kicker = t("Полка"), emoji = "📚", title = tf("Полка: %s", state.me.name),
                        subtitle = t("Что я смотрю, читаю и где бегаю"),
                        lines = shelfLines(shelf),
                        author = "${state.me.name} · @${state.me.handle}",
                    )
                    shareCard(context, scope, vm, spec, "shelf", InviteAction.Shelf)
                },
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    rating?.let { (activity, subject) ->
        RateDialog(subject, onRate = { vm.rateOnShelf(activity, subject, it); rating = null }, onDismiss = { rating = null })
    }
}

/** Главное с полки — для карточки «Поделиться». */
fun shelfLines(shelf: Shelf): List<EchoLine> = buildList {
    shelf.watched.filter { it.rating >= 4 }.take(2).forEach { add(EchoLine(it.emoji, "${it.subject} · ${"★".repeat(it.rating)}")) }
    shelf.books(BookStatus.Reading).firstOrNull()?.let { add(EchoLine("📖", tf("Читаю: %s", it.subject))) }
    shelf.books(BookStatus.Done).firstOrNull()?.let { add(EchoLine("🏁", tf("Прочитал: %s", it.subject))) }
    shelf.sport.firstOrNull { it.totalKm > 0 }?.let { add(EchoLine(it.emoji, tf("%s км всего", amountNumber(Math.round(it.totalKm * 10) / 10.0)))) }
    if (isEmpty()) add(EchoLine("✨", t("Полка только начинается")))
}.take(5)

// ---------- Для вас ----------

private fun LazyListScope.forYou(
    vm: AppViewModel,
    state: UiState,
    shelf: Shelf,
    recs: Pair<List<Recommendation>, List<Recommendation>>,
    plan: RunPlan,
    onRate: (Pair<String, String>) -> Unit,
) {
    item { RunPlanCard(plan) }
    val (films, books) = recs
    if (shelf.rated.size < 3) item {
        Hint(t("Оцените ещё пару фильмов или книг — подборка станет точнее. Мы советуем то, что любят люди с похожим вкусом."))
    }
    if (films.isNotEmpty()) {
        item { Caption(t("Кино для вас")) }
        items(films, key = { "f" + it.key }) { RecCard(vm, it, onSeen = { onRate("movie" to it.subject) }) }
    }
    if (books.isNotEmpty()) {
        item { Caption(t("Книги для вас")) }
        items(books, key = { "b" + it.key }) { RecCard(vm, it, onSeen = { onRate("book" to it.subject) }) }
    }
    // С кем совпадает вкус
    val mine = shelf.rated
    val matches = DemoTaste.ratings.filterKeys { it !in state.blocked }
        .map { (id, list) -> id to tasteMatch(mine, list) }
        .filter { it.second.common >= 2 }
        .sortedByDescending { it.second.similarity }
    if (matches.isNotEmpty()) {
        item { Caption(t("Похожий вкус")) }
        items(matches, key = { "m" + it.first }) { (id, m) ->
            val person = Demo.person(id) ?: return@items
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Row(Modifier.clickable { vm.openPerson(id) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(person, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(person.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                        Text(tf("Общих оценок: %s", m.common), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                    }
                    Text("${m.percent}%", style = MaterialTheme.typography.titleMedium, color = Palette.PearlPink)
                }
            }
        }
    }
}

@Composable
private fun RecCard(vm: AppViewModel, r: Recommendation, onSeen: () -> Unit) {
    val person = Demo.person(r.personId)
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.emoji, fontSize = 30.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.subject, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                    val who = person?.name?.substringBefore(' ').orEmpty()
                    Text(
                        if (r.match.common >= 2) tf("%s ставит %s★ · вкус совпадает на %s%%", who, r.theirRating, r.match.percent)
                        else tf("%s ставит %s★", who, r.theirRating),
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Mist,
                    )
                    if (r.friends > 1) Text(tf("Нравится друзьям: %s", r.friends), style = MaterialTheme.typography.labelMedium, color = Palette.Lilac)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlowButton(if (r.kind == "book") t("Хочу прочитать") else t("Хочу посмотреть"), onClick = { vm.planOnShelf(r.kind, r.subject) })
                GhostButton(if (r.kind == "book") t("Читал") else t("Смотрел"), onClick = onSeen)
                GhostButton(t("Не моё"), onClick = { vm.dismissRecommendation(r.key) })
            }
        }
    }
}

@Composable
private fun RunPlanCard(plan: RunPlan) {
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("ПЛАН БЕГА НА НЕДЕЛЮ"), style = MaterialTheme.typography.labelSmall, color = Palette.Mist)
            Text(
                tf("%s из %s км", amountNumber(Math.round(plan.doneKm * 10) / 10.0), amountNumber(plan.targetKm)),
                style = MaterialTheme.typography.titleLarge, color = Palette.Ink,
            )
            Bar(plan.progress)
            Text(
                when {
                    plan.done -> t("План выполнен — на следующей неделе он чуть вырастет.")
                    plan.reason == PlanReason.Start -> t("Начнём с 5 км за неделю: две-три лёгкие пробежки.")
                    plan.reason == PlanReason.Return -> t("Вы пропустили неделю — начинаем мягче, чем раньше.")
                    else -> t("На 10% больше прошлой недели — так растут без травм.")
                },
                style = MaterialTheme.typography.bodyMedium, color = Palette.Mist,
            )
            // Четыре прошлые недели столбиками
            val max = (plan.weeksKm + plan.targetKm).maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
            Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                (plan.weeksKm + plan.doneKm).forEachIndexed { i, km ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.fillMaxWidth().height((44 * (km / max)).dp.coerceAtLeast(3.dp)).clip(RoundedCornerShape(6.dp))
                                .background(if (i == 4) Palette.PearlPink else Palette.Lilac.copy(alpha = 0.6f)),
                        )
                        Text(if (i == 4) t("эта") else "−${4 - i}", style = MaterialTheme.typography.labelSmall, color = Palette.Mist)
                    }
                }
            }
        }
    }
}

// ---------- Кино и книги ----------

private fun LazyListScope.films(vm: AppViewModel, shelf: Shelf, onRate: (Pair<String, String>) -> Unit) {
    if (shelf.films.isEmpty()) item { Hint(t("Отметьте «Посмотрел фильм» в ленте — фильм встанет на полку с вашей оценкой.")) }
    if (shelf.watched.isNotEmpty()) {
        item { Caption(tf("Посмотрено · %s", shelf.watched.size)) }
        items(shelf.watched, key = { "w" + it.key }) { ItemRow(it, onRate = { onRate(it.activity to it.subject) }) }
    }
    if (shelf.plannedFilms.isNotEmpty()) {
        item { Caption(tf("В планах · %s", shelf.plannedFilms.size)) }
        items(shelf.plannedFilms, key = { "p" + it.key }) { ItemRow(it, action = t("Посмотрел"), onRate = { onRate(it.activity to it.subject) }) }
    }
}

private fun LazyListScope.books(shelf: Shelf, onRate: (Pair<String, String>) -> Unit) {
    if (shelf.books.isEmpty()) item { Hint(t("Отметьте книгу в ленте: «Читаю», «Прочитал» или «Хочу прочитать».")) }
    listOf(BookStatus.Reading, BookStatus.Done, BookStatus.Want).forEach { status ->
        val list = shelf.books(status)
        if (list.isNotEmpty()) {
            item { Caption("${status.title} · ${list.size}") }
            items(list, key = { status.id + it.key }) { ItemRow(it, action = if (status != BookStatus.Done) t("Прочитал") else null, onRate = { onRate("book" to it.subject) }) }
        }
    }
}

@Composable
private fun ItemRow(item: ShelfItem, action: String? = null, onRate: () -> Unit) {
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(item.emoji, fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.subject, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                val details = listOfNotNull(
                    if (item.times > 1) plural(item.times, "раз", "раза", "раз") else null,
                    if (item.pages > 0) tf("%s стр.", amountNumber(item.pages)) else null,
                ).joinToString(" · ")
                if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                if (item.rating > 0) Stars(item.rating, onRate = null, size = 16)
            }
            if (action != null) GhostButton(action, onClick = onRate)
            else if (item.rating == 0) GhostButton(t("Оценить"), onClick = onRate)
        }
    }
}

// ---------- Спорт и места ----------

private fun LazyListScope.sport(shelf: Shelf) {
    if (shelf.sport.isEmpty()) item { Hint(t("Отметьте пробежку, заезд или тренировку — здесь соберутся километры, рекорды и лучший темп.")) }
    items(shelf.sport, key = { it.activity }) { SportCard(it) }
}

@Composable
private fun SportCard(s: SportStats) {
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${s.emoji}  " + plural(s.count, "раз", "раза", "раз"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.totalKm > 0) {
                    Tile(amountNumber(Math.round(s.totalKm * 10) / 10.0), t("км всего"), Modifier.weight(1f))
                    Tile(amountNumber(Math.round(s.monthKm * 10) / 10.0), t("км за месяц"), Modifier.weight(1f))
                    Tile(amountNumber(s.longestKm), t("самая длинная"), Modifier.weight(1f))
                } else {
                    Tile(minutesTitle(s.minutes), t("всего"), Modifier.weight(1f))
                }
            }
            s.bestPace?.let { Text(tf("Лучший темп: %s /км", paceClock(it)), style = MaterialTheme.typography.bodyMedium, color = Palette.PearlPink) }
        }
    }
}

private fun LazyListScope.places(shelf: Shelf) {
    if (shelf.places.isEmpty()) item { Hint(t("Отметьте «Я здесь», кофейню или концерт — получится ваша карта мест.")) }
    items(shelf.places, key = { "pl" + it.subject }) { p ->
        GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(p.emoji, fontSize = 24.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.subject, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                    Text(plural(p.count, "раз", "раза", "раз"), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                }
                if (p.rating > 0) Stars(p.rating, onRate = null, size = 14)
            }
        }
    }
    if (shelf.other.isNotEmpty()) {
        item { Caption(t("Ещё занятия")) }
        // Без ключа по названию: удалённый и заново созданный свой чек-ин с тем же именем — две группы
        items(shelf.other) { (emoji, title, count) ->
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(emoji, fontSize = 22.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, modifier = Modifier.weight(1f))
                    Text(plural(count, "раз", "раза", "раз"), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                }
            }
        }
    }
}

// ---------- Права ----------

private fun LazyListScope.progress(state: UiState) {
    item { Hint(t("Здесь не значки, а возможности: делаете — открываете новое.")) }
    items(state.unlocks, key = { it.unlock.name }) { u ->
        GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(u.unlock.emoji, fontSize = 26.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(u.unlock.title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                        Text(u.unlock.reward, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                    }
                    Text(if (u.open) "🔓" else "🔒", fontSize = 20.sp)
                }
                Bar(u.fraction)
                Text(
                    if (u.open) t("Открыто") else u.unlock.how + " · " + tf("осталось %s", u.left),
                    style = MaterialTheme.typography.labelMedium, color = if (u.open) Palette.PearlPink else Palette.Lilac,
                )
            }
        }
    }
    val km = state.movedKm
    state.together.missions.filter { it.unlockKm > 0 }.forEach { m ->
        item(key = "km" + m.id) {
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${m.emoji} ${m.title}", style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                    Bar((km / m.unlockKm).toFloat().coerceIn(0f, 1f))
                    Text(
                        if (km >= m.unlockKm) t("Маршрут открыт — он во «Вместе → Миссии»")
                        else tf("%s из %s км пробежек, прогулок и заездов", amountNumber(Math.round(km * 10) / 10.0), amountNumber(m.unlockKm)),
                        style = MaterialTheme.typography.labelMedium, color = Palette.Lilac,
                    )
                }
            }
        }
    }
}

// ---------- Мелочи ----------

@Composable
private fun RateDialog(subject: String, onRate: (Int) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(24.dp)).background(Palette.Night).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(subject, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
            Text(t("Ваша оценка"), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
            Stars(0, onRate = onRate, size = 36)
            GhostButton(t("Отмена"), onClick = onDismiss)
        }
    }
}

@Composable
internal fun Bar(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Palette.Plum)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(Palette.Lilac))
    }
}

@Composable
private fun Tile(value: String, label: String, modifier: Modifier) {
    Column(modifier.glass(RoundedCornerShape(16.dp)).padding(10.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Mist, maxLines = 2)
    }
}

@Composable
private fun Caption(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Mist, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, modifier = Modifier.padding(horizontal = 20.dp))
}

/** Карточка «Полка» в профиле: что уже собралось и вход на полку. */
@Composable
fun ShelfCard(vm: AppViewModel, state: UiState) {
    val shelf = remember(state.posts, state.shelfMarks, state.me.id) { state.shelf }
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("ПОЛКА"), style = MaterialTheme.typography.labelSmall, color = Palette.Mist)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile("${shelf.watched.size}", t("фильмов"), Modifier.weight(1f).clickable { vm.openShelf(ShelfTab.Films) })
                Tile("${shelf.books(BookStatus.Done).size}", t("книг прочитано"), Modifier.weight(1f).clickable { vm.openShelf(ShelfTab.Books) })
                Tile(amountNumber(Math.round(shelf.sport.sumOf { it.totalKm } * 10) / 10.0), t("км"), Modifier.weight(1f).clickable { vm.openShelf(ShelfTab.Sport) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlowButton(t("Для вас"), onClick = { vm.openShelf(ShelfTab.ForYou) })
                GhostButton(t("Вся полка"), onClick = { vm.openShelf(ShelfTab.Films) })
            }
        }
    }
}
