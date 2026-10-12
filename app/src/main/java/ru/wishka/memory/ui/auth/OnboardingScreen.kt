package ru.wishka.memory.ui.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.wishka.memory.data.Catalog
import ru.wishka.memory.data.CatalogTitle
import ru.wishka.memory.data.Demo
import ru.wishka.memory.data.DemoTaste
import ru.wishka.memory.data.Rated
import ru.wishka.memory.data.recommend
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassCard
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.splash.SplashSky.drawSky
import ru.wishka.memory.ui.theme.Palette

/** Сколько любимого выбрать, чтобы полка и подборка ожили. */
const val ONBOARDING_PICKS = 3

/**
 * Знакомство до регистрации — как обучение в игре, а не серая форма:
 * как здесь всё устроено → выберите три любимых фильма или книги → вот
 * ваша полка и первый совет от человека с похожим вкусом → «Сохранить
 * мою полку» ведёт к регистрации.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(onDone: (List<CatalogTitle>) -> Unit, onSkip: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(0) }
    var picked by rememberSaveable { mutableStateOf(listOf<String>()) }
    val picks = picked.mapNotNull { key -> Catalog.all.firstOrNull { it.kind + ":" + it.title == key } }
    val density = LocalDensity.current.density

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) { drawSky(appear = 1f, time = 0L, density = density) }
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            Text("MEMORY", style = MaterialTheme.typography.displaySmall, color = Palette.Ink, modifier = Modifier.align(Alignment.CenterHorizontally))
            when (step) {
                0 -> {
                    Text(
                        t("Приложение, которое помнит, что вы любите"),
                        style = MaterialTheme.typography.titleLarge, color = Palette.Ink, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    listOf(
                        "🎬" to t("Отмечаете в одно касание: «посмотрел фильм», «пробежал 5 км», «читаю книгу»."),
                        "📚" to t("Полка собирается сама — ничего не нужно заполнять вручную."),
                        "✨" to t("Советы — от людей с похожим вкусом, а не «самое популярное»."),
                        "🔓" to t("Чем больше делаете, тем больше открывается: свои клады, миссии, цели."),
                    ).forEach { (emoji, text) ->
                        GlassCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(emoji, fontSize = 28.sp)
                                Spacer(Modifier.width(12.dp))
                                Text(text, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
                            }
                        }
                    }
                    GlowButton(t("Начать — выбрать любимое"), modifier = Modifier.fillMaxWidth(), onClick = { step = 1 })
                    GhostButton(t("У меня уже есть аккаунт"), modifier = Modifier.fillMaxWidth(), onClick = onSkip)
                }
                1 -> {
                    Text(
                        tf("Выберите %s любимых фильма или книги", ONBOARDING_PICKS),
                        style = MaterialTheme.typography.titleLarge, color = Palette.Ink, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        tf("Выбрано: %s", picks.size), style = MaterialTheme.typography.bodyMedium, color = Palette.Lilac,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                    listOf(t("Кино") to Catalog.films, t("Книги") to Catalog.books).forEach { (title, list) ->
                        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Mist)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            list.forEach { c ->
                                val key = c.kind + ":" + c.title
                                val on = key in picked
                                Row(
                                    Modifier.clip(RoundedCornerShape(50)).glass(RoundedCornerShape(50), strong = on)
                                        .clickable { picked = if (on) picked - key else picked + key }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(c.emoji, fontSize = 16.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text((if (on) "✓ " else "") + c.title, style = MaterialTheme.typography.labelLarge, color = if (on) Palette.Ink else Palette.Mist)
                                }
                            }
                        }
                    }
                    GlowButton(
                        if (picks.size >= ONBOARDING_PICKS) t("Посмотреть мою полку") else tf("Ещё %s", ONBOARDING_PICKS - picks.size),
                        enabled = picks.size >= ONBOARDING_PICKS, modifier = Modifier.fillMaxWidth(), onClick = { step = 2 },
                    )
                    GhostButton(t("Назад"), modifier = Modifier.fillMaxWidth(), onClick = { step = 0 })
                }
                else -> {
                    Text(t("Ваша полка"), style = MaterialTheme.typography.titleLarge, color = Palette.Ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            picks.forEach { Text("${it.emoji}  ${it.title} · ★★★★★", style = MaterialTheme.typography.bodyLarge, color = Palette.Ink) }
                        }
                    }
                    val rated = picks.map { Rated(it.kind, it.title, 5) }
                    val keys = rated.map { it.key }.toSet()
                    val recs = remember(picked) {
                        (recommend(rated, keys, DemoTaste.ratings, "film", 1) + recommend(rated, keys, DemoTaste.ratings, "book", 1))
                    }
                    if (recs.isNotEmpty()) {
                        Text(t("ПЕРВЫЕ СОВЕТЫ"), style = MaterialTheme.typography.labelSmall, color = Palette.Mist)
                        recs.forEach { r ->
                            GlassCard(Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(r.emoji, fontSize = 28.sp)
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(r.subject, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                                        val who = Demo.person(r.personId)?.name?.substringBefore(' ').orEmpty()
                                        Text(
                                            if (r.match.common > 0) tf("%s тоже любит то, что выбрали вы, и ставит этому %s★", who, r.theirRating)
                                            else tf("%s ставит %s★", who, r.theirRating),
                                            style = MaterialTheme.typography.bodyMedium, color = Palette.Mist,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        t("Аккаунт хранится на этом телефоне: ни почты, ни номера не нужно."),
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                    GlowButton(t("Сохранить мою полку"), modifier = Modifier.fillMaxWidth(), onClick = { onDone(picks) })
                    GhostButton(t("Назад"), modifier = Modifier.fillMaxWidth(), onClick = { step = 1 })
                }
            }
        }
    }
}
