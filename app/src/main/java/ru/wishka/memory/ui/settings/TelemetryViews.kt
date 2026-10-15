package ru.wishka.memory.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.agoTitle
import ru.wishka.memory.data.minutesSince
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.telemetry.TelemetrySetup
import ru.wishka.memory.ui.components.FadeLine
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassCard
import ru.wishka.memory.ui.theme.Palette

/** Переключатель с заголовком и пояснением. */
@Composable
private fun ConsentSwitch(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.toggleable(value = checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
        }
        MemorySwitch(checked, onChange = null)
    }
}

private val CRASH_HINT get() = t("Что сломалось и где в коде — без ваших записей, имён и переписки.")
private val USAGE_HINT get() = t("Раз в день — сколько раз открывали Memory, публиковали записи, искали. Без текстов и без того, кто именно.")

/**
 * «Приватность → Сбои и статистика»: два отдельных согласия и последний
 * сбой, который можно отправить вручную.
 */
@Composable
fun TelemetryCard() {
    val context = LocalContext.current
    var crashes by remember { mutableStateOf(TelemetrySetup.crashesOn) }
    var usage by remember { mutableStateOf(TelemetrySetup.analyticsOn) }
    var last by remember { mutableStateOf(TelemetrySetup.lastCrash()) }
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("Сбои и статистика").uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Mist)
            ConsentSwitch(t("Отчёты о сбоях"), CRASH_HINT, crashes) {
                crashes = it
                TelemetrySetup.update(context, crashes, usage)
            }
            FadeLine()
            ConsentSwitch(t("Статистика использования"), USAGE_HINT, usage) {
                usage = it
                TelemetrySetup.update(context, crashes, usage)
            }
            if (!TelemetrySetup.hasKey) {
                Text(
                    t("В этой сборке отправка выключена: всё остаётся на телефоне."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                )
            }
            last?.let { report ->
                FadeLine()
                Text(
                    tf("Последний сбой — %s: %s", agoTitle(minutesSince(report.at, 0)), report.title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Ink,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (TelemetrySetup.hasKey) {
                        GhostButton(t("Отправить отчёт"), onClick = {
                            val sent = TelemetrySetup.sendLastCrash(context)
                            Toast.makeText(context, if (sent) t("Спасибо! Отчёт отправлен.") else t("Не получилось отправить."), Toast.LENGTH_SHORT).show()
                            if (sent) last = null
                        })
                    }
                    GhostButton(t("Удалить"), onClick = { TelemetrySetup.clearCrash(); last = null })
                }
            }
            Text(
                t("Отчёты обрабатывает сервис Sentry (Functional Software, Inc., США) — подробнее в политике конфиденциальности."),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Mist,
            )
        }
    }
}

/**
 * Один раз после входа — спросить, можно ли присылать отчёты о сбоях и
 * статистику. По умолчанию — нет: включает только сам человек.
 */
@Composable
fun TelemetryConsentDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    var crashes by remember { mutableStateOf(false) }
    var usage by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        containerColor = Palette.Night,
        title = { Text(t("Помочь сделать Memory лучше?"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ConsentSwitch(t("Отчёты о сбоях"), CRASH_HINT, crashes) { crashes = it }
                ConsentSwitch(t("Статистика использования"), USAGE_HINT, usage) { usage = it }
                Text(t("Изменить можно в любой момент: Настройки → Приватность."), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
            }
        },
        confirmButton = {
            TextButton(onClick = { TelemetrySetup.update(context, crashes, usage); onDone() }) { Text(t("Сохранить"), color = Palette.Lilac) }
        },
        dismissButton = {
            TextButton(onClick = { TelemetrySetup.update(context, false, false); onDone() }) { Text(t("Не сейчас"), color = Palette.Mist) }
        },
    )
}
