package ru.wishka.memory.ui.premium

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ru.wishka.memory.data.BillingPeriod
import ru.wishka.memory.data.PlanTier
import ru.wishka.memory.data.Premium
import ru.wishka.memory.data.PremiumFeature
import ru.wishka.memory.data.Subscription
import ru.wishka.memory.data.plural
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassCard
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.profile.RoundIconButton
import ru.wishka.memory.ui.theme.Palette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Значок «PLUS» у премиум-вещей: сиренево-голубая плашка. */
@Composable
fun PlusBadge(modifier: Modifier = Modifier, business: Boolean = false) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(Palette.Lilac, Palette.Sky)))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(if (business) "BIZ" else "PLUS", fontSize = 9.sp, color = Palette.Void, style = MaterialTheme.typography.labelSmall)
    }
}

/** Плашка для премиум-функции: «PLUS» или «BIZ». */
@Composable
fun PlusBadge(feature: PremiumFeature, modifier: Modifier = Modifier) {
    PlusBadge(modifier, business = feature.tier == PlanTier.Business)
}

/** Дата окончания: «16 октября». */
private fun dateTitle(ms: Long): String {
    val locale = if (ru.wishka.memory.i18n.L10n.english) Locale.ENGLISH else Locale("ru")
    val pattern = if (ru.wishka.memory.i18n.L10n.english) "MMMM d" else "d MMMM"
    return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern, locale))
}

/** Состояние подписки одной строкой: «Memory+ · пробный, ещё 3 дня». */
fun subscriptionStatus(sub: Subscription, plan: PlanTier, now: Long): String = when {
    plan == PlanTier.Free && sub.tier != PlanTier.Free && sub.trial -> t("Пробный период закончился")
    plan == PlanTier.Free -> t("Бесплатный тариф")
    sub.trial -> tf("%s · пробный, ещё %s", plan.title, plural(sub.daysLeft(now), "день", "дня", "дней"))
    sub.autoRenew -> tf("%s · продлится %s", plan.title, dateTitle(sub.until))
    else -> tf("%s · до %s, без продления", plan.title, dateTitle(sub.until))
}

/** Карточка в профиле: какой тариф и вход на экран Memory+. */
@Composable
fun PremiumProfileCard(vm: AppViewModel, state: UiState) {
    val now = System.currentTimeMillis()
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clickable { vm.openPremium() }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Palette.Orchid, Palette.Sky))),
                contentAlignment = Alignment.Center,
            ) { Text("✦", fontSize = 22.sp, color = Palette.Ink) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Memory+", style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                    if (state.plan != PlanTier.Free) {
                        Spacer(Modifier.width(8.dp))
                        PlusBadge(business = state.plan == PlanTier.Business)
                    }
                }
                Text(
                    if (state.plan == PlanTier.Free && !state.subscription.trialUsed) tf("Попробуйте %s бесплатно", plural(Premium.TRIAL_DAYS, "день", "дня", "дней"))
                    else subscriptionStatus(state.subscription, state.plan, now),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                )
            }
            Text(if (state.plan == PlanTier.Free) t("Тарифы") else t("Управлять"), style = MaterialTheme.typography.labelLarge, color = Palette.Lilac)
        }
    }
}

/**
 * Экран «Memory+» в профиле: текущий тариф, пробный период, выбор тарифа
 * и срока, смена тарифа, отмена и возобновление продления.
 */
@Composable
fun PremiumScreen(vm: AppViewModel, state: UiState, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val sub = state.subscription
    var period by rememberSaveable { mutableStateOf(if (sub.tier != PlanTier.Free) sub.period else BillingPeriod.Year) }
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(LineIcon.Back, Palette.Ink) { vm.closeProfileSection() }
                Spacer(Modifier.width(14.dp))
                Text("MEMORY+", style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
            }
        }
        // Текущий тариф
        item {
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("Ваш тариф").uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Mist)
                    Text(state.plan.title, style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
                    Text(subscriptionStatus(sub, state.plan, now), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                    if (state.plan != PlanTier.Free && !sub.trial) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (sub.autoRenew) GhostButton(t("Отменить продление"), onClick = { confirm = "cancel" })
                        else GlowButton(t("Возобновить"), onClick = vm::resumeRenewal)
                    }
                    Text(
                        t("Оплата пока демонстрационная: деньги не списываются. Настоящая оплата — через Google Play и App Store."),
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.Mist,
                    )
                }
            }
        }
        // Срок
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BillingPeriod.entries.forEach { p ->
                    val active = p == period
                    Box(
                        Modifier
                            .weight(1f)
                            .glass(RoundedCornerShape(50), strong = active)
                            .border(if (active) 1.5.dp else 0.dp, if (active) Palette.Lilac else Palette.Line, RoundedCornerShape(50))
                            .clickable { period = p }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (p == BillingPeriod.Year) tf("%s · выгоднее до %s%", p.title, Premium.yearSaving(PlanTier.Plus)) else p.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) Palette.Ink else Palette.Mist,
                        )
                    }
                }
            }
        }
        listOf(PlanTier.Plus, PlanTier.Business).forEach { tier ->
            item(key = "plan-" + tier.id) {
                PlanCard(
                    tier = tier,
                    period = period,
                    current = state.plan == tier,
                    sameTerms = state.plan == tier && sub.period == period && !sub.trial,
                    trialAvailable = !sub.trialUsed,
                    onTrial = {
                        val error = vm.startTrial(tier)
                        Toast.makeText(context, error ?: tf("%s: пробный период начался", tier.title), Toast.LENGTH_SHORT).show()
                    },
                    onSubscribe = { confirm = tier.id },
                )
            }
        }
        // Бесплатный тариф
        item {
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(PlanTier.Free.title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                    Text(
                        t("Всегда бесплатно: лента, истории, PRISM, чаты, голосовые, друзья, группы, покупки в витринах, приватность и безопасность."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Mist,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    // Подтверждение: оформить или сменить тариф, отменить продление
    confirm?.let { what ->
        val tier = PlanTier.of(what)
        val cancel = what == "cancel"
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Palette.Night,
            title = {
                Text(
                    if (cancel) t("Отменить продление?")
                    else if (state.plan == PlanTier.Free) tf("Оформить %s?", tier.title) else tf("Перейти на %s?", tier.title),
                    style = MaterialTheme.typography.titleMedium, color = Palette.Ink,
                )
            },
            text = {
                Text(
                    if (cancel) tf("%s будет работать до %s, потом тариф станет бесплатным. Ничего из созданного не пропадёт.", state.plan.title, dateTitle(sub.until))
                    else tf("%s — %s. Новый тариф действует сразу, срок считается заново. Оплата демонстрационная.", tier.title, Premium.priceTitle(tier, period)),
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Mist,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (cancel) vm.cancelRenewal() else {
                        vm.subscribe(tier, period)
                        Toast.makeText(context, tf("Готово: %s", tier.title), Toast.LENGTH_SHORT).show()
                    }
                    confirm = null
                }) { Text(if (cancel) t("Отменить продление") else t("Оформить"), color = Palette.Lilac) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(t("Не сейчас"), color = Palette.Mist) } },
        )
    }
}

/** Карточка тарифа: цена, что входит, «Попробовать 3 дня», «Оформить». */
@Composable
private fun PlanCard(
    tier: PlanTier,
    period: BillingPeriod,
    current: Boolean,
    sameTerms: Boolean,
    trialAvailable: Boolean,
    onTrial: () -> Unit,
    onSubscribe: () -> Unit,
) {
    GlassCard(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .border(if (current) 1.5.dp else 0.dp, if (current) Palette.Lilac else Palette.Line, RoundedCornerShape(22.dp)),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tier.title, style = MaterialTheme.typography.titleLarge, color = Palette.Ink, modifier = Modifier.weight(1f))
                if (current) Text(t("Сейчас"), style = MaterialTheme.typography.labelLarge, color = Palette.Lilac)
            }
            Text(Premium.priceTitle(tier, period), style = MaterialTheme.typography.headlineSmall, color = Palette.Lilac)
            if (period == BillingPeriod.Year) Text(
                tf("Это %s в месяц — выгоднее на %s%", ru.wishka.memory.data.priceTitle(Premium.price(tier, period) / 12L), Premium.yearSaving(tier)),
                style = MaterialTheme.typography.labelMedium, color = Palette.Mist,
            )
            if (tier == PlanTier.Business) Text(t("Всё из Memory+, а ещё:"), style = MaterialTheme.typography.bodyMedium, color = Palette.Ink)
            Premium.features(tier).forEach { f ->
                Row(verticalAlignment = Alignment.Top) {
                    Glyph(LineIcon.Check, Palette.Lilac, size = 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.title, style = MaterialTheme.typography.titleSmall, color = Palette.Ink)
                        Text(f.what, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            if (trialAvailable) GhostButton(tf("Попробовать %s бесплатно", plural(Premium.TRIAL_DAYS, "день", "дня", "дней")), onClick = onTrial, modifier = Modifier.fillMaxWidth())
            if (!sameTerms) GlowButton(
                if (current) tf("Перейти на %s", period.title.lowercase()) else tf("Оформить за %s", Premium.priceTitle(tier, period)),
                onClick = onSubscribe,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Шторка «Это в Memory+»: что за функция, в каком тарифе, пробный период
 * и «Все тарифы».
 */
@Composable
fun PaywallSheet(vm: AppViewModel, state: UiState, feature: PremiumFeature) {
    val context = LocalContext.current
    Dialog(onDismissRequest = vm::closePaywall) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Palette.Night)
                .border(1.dp, Palette.Line, RoundedCornerShape(26.dp))
                .padding(22.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Palette.Orchid, Palette.Sky))),
                contentAlignment = Alignment.Center,
            ) { Text("✦", fontSize = 26.sp, color = Palette.Ink) }
            PlusBadge(feature)
            Text(feature.title, style = MaterialTheme.typography.titleLarge, color = Palette.Ink)
            Text(feature.what, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
            Text(
                tf("Входит в %s — от %s.", feature.tier.title, Premium.priceTitle(feature.tier, BillingPeriod.Month)),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Ink,
            )
            if (!state.subscription.trialUsed) GlowButton(
                tf("Попробовать %s бесплатно", plural(Premium.TRIAL_DAYS, "день", "дня", "дней")),
                onClick = {
                    val error = vm.startTrial(feature.tier)
                    Toast.makeText(context, error ?: tf("%s: пробный период начался", feature.tier.title), Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            GhostButton(t("Все тарифы"), onClick = vm::openPremium, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = vm::closePaywall) { Text(t("Не сейчас"), color = Palette.Mist) }
        }
    }
}
