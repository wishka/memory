package ru.wishka.memory.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.wishka.memory.data.Legal
import ru.wishka.memory.data.LegalDoc
import ru.wishka.memory.data.ReportKind
import ru.wishka.memory.data.ReportReason
import ru.wishka.memory.data.ReportTarget
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.settings.MemorySwitch
import ru.wishka.memory.ui.theme.Palette

/**
 * Документ из docs/legal: простая разметка — заголовки «#», «##»,
 * пункты «- », жирное «**…**», таблицы показываются строками.
 */
@Composable
fun LegalText(text: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        text.lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() || line.startsWith("| ---") -> Unit
                line.startsWith("# ") -> Text(plain(line.drop(2)), style = MaterialTheme.typography.titleLarge, color = Palette.Ink)
                line.startsWith("## ") -> Text(plain(line.drop(3)), style = MaterialTheme.typography.titleMedium, color = Palette.Lilac, modifier = Modifier.padding(top = 6.dp))
                line.trimStart().startsWith("- ") -> Row(Modifier.padding(start = if (line.startsWith("  ")) 16.dp else 0.dp)) {
                    Text("◇", style = MaterialTheme.typography.bodyMedium, color = Palette.Lilac)
                    Spacer(Modifier.width(10.dp))
                    Text(plain(line.trimStart().drop(2)), style = MaterialTheme.typography.bodyMedium, color = Palette.Ink)
                }
                line.startsWith("|") -> Text(
                    line.trim('|').split('|').joinToString(" · ") { plain(it.trim()) },
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Ink,
                )
                else -> Text(plain(line), style = MaterialTheme.typography.bodyMedium, color = Palette.Ink)
            }
        }
    }
}

/** Без разметки: жирное и ссылки — простым текстом. */
private fun plain(s: String): String =
    s.replace("**", "").replace(Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)"), "$1").replace("`", "")

/** Документ во весь экран. */
@Composable
fun LegalDocViewer(doc: LegalDoc, onClose: () -> Unit) {
    val context = LocalContext.current
    val text = remember(doc) { doc.load(context) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Palette.Void).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Glyph(LineIcon.Close, Palette.Ink) }
                Spacer(Modifier.width(8.dp))
                Text(doc.title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, maxLines = 2)
            }
            FadeLine()
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
                if (!Legal.filled) {
                    Text(
                        t("Черновик: данные оператора ещё не заполнены."),
                        style = MaterialTheme.typography.labelLarge, color = Palette.PearlPink,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                LegalText(text.ifEmpty { t("Документ не найден в сборке.") })
            }
        }
    }
}

/** Ссылки на документы рядком — под галочками согласия. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LegalLinks(docs: List<LegalDoc>, onOpen: (LegalDoc) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        docs.forEach { doc ->
            Text(
                doc.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Lilac,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onOpen(doc) }.padding(vertical = 2.dp),
            )
        }
    }
}

/** Галочка с подписью: вся строка нажимается. */
@Composable
fun CheckRow(checked: Boolean, text: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.Ink, modifier = Modifier.weight(1f))
    }
}

/**
 * Три отдельные галочки: возраст, соглашение с
 * правилами и — отдельно — согласие на обработку данных.
 */
class ConsentState(age: Boolean = false, terms: Boolean = false, data: Boolean = false) {
    var age by mutableStateOf(age)
    var terms by mutableStateOf(terms)
    var data by mutableStateOf(data)
    val all: Boolean get() = age && terms && data
}

@Composable
fun ConsentChecks(state: ConsentState, onOpen: (LegalDoc) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        CheckRow(state.age, tf("Мне есть %s лет", Legal.MIN_AGE)) { state.age = it }
        CheckRow(state.terms, t("Принимаю пользовательское соглашение и правила сообщества")) { state.terms = it }
        CheckRow(state.data, t("Даю согласие на обработку персональных данных")) { state.data = it }
        LegalLinks(listOf(LegalDoc.Terms, LegalDoc.Community, LegalDoc.Privacy, LegalDoc.Consent), onOpen, Modifier.padding(start = 12.dp, top = 4.dp))
    }
}

/**
 * Документы обновились или аккаунт создан до них: войти дальше — только
 * приняв. Не согласен — выход из аккаунта (данные на телефоне остаются).
 */
@Composable
fun ConsentGate(onAccept: () -> Unit, onLogout: () -> Unit) {
    val consent = remember { ConsentState() }
    var open by remember { mutableStateOf<LegalDoc?>(null) }
    Box(Modifier.fillMaxSize()) {
        CosmicBackground()
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            Text("MEMORY", style = MaterialTheme.typography.displaySmall, color = Palette.Ink)
            Text(t("Документы Memory"), style = MaterialTheme.typography.titleLarge, color = Palette.Ink)
            Text(
                t("Перед тем как продолжить, прочитайте и примите пользовательское соглашение, правила сообщества и согласие на обработку данных. Когда они изменятся, мы попросим об этом снова."),
                style = MaterialTheme.typography.bodyMedium, color = Palette.Mist,
            )
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) { ConsentChecks(consent) { open = it } }
            }
            GlowButton(t("Принять и продолжить"), enabled = consent.all, modifier = Modifier.fillMaxWidth(), onClick = onAccept)
            GhostButton(t("Не принимаю — выйти"), onClick = onLogout, modifier = Modifier.fillMaxWidth())
        }
    }
    open?.let { LegalDocViewer(it) { open = null } }
}

/**
 * Жалоба: причина, пара слов и — для чужого — «заблокировать автора».
 * Отправленное сразу скрывается у вас.
 */
@Composable
fun ReportDialog(target: ReportTarget, onSend: (ReportReason, String, Boolean) -> Unit, onDismiss: () -> Unit, onRules: () -> Unit) {
    var reason by remember { mutableStateOf<ReportReason?>(null) }
    var note by remember { mutableStateOf("") }
    var block by remember { mutableStateOf(target.kind == ReportKind.Person) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Night,
        title = {
            Text(
                when (target.kind) {
                    ReportKind.Post -> t("Пожаловаться на запись")
                    ReportKind.Comment -> t("Пожаловаться на комментарий")
                    ReportKind.Prism -> t("Пожаловаться на PRISM")
                    ReportKind.Message -> t("Пожаловаться на сообщение")
                    ReportKind.Person -> t("Пожаловаться на человека")
                },
                style = MaterialTheme.typography.titleMedium, color = Palette.Ink,
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t("Что не так? Жалоба анонимна для автора."), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                Spacer(Modifier.height(6.dp))
                ReportReason.entries.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { reason = r }.padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(r.title, style = MaterialTheme.typography.bodyLarge, color = if (reason == r) Palette.Ink else Palette.Mist, modifier = Modifier.weight(1f), fontWeight = if (reason == r) FontWeight.SemiBold else FontWeight.Normal)
                        if (reason == r) Glyph(LineIcon.Check, Palette.Lilac, size = 18.dp)
                    }
                }
                if (reason != null) {
                    GlassField(note, { note = it.take(500) }, t("Подробнее — необязательно"), modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)))
                }
                if (target.authorId != 0) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (target.authorName.isNotEmpty()) tf("Заблокировать: %s", target.authorName) else t("Заблокировать автора"),
                            style = MaterialTheme.typography.bodyLarge, color = Palette.Ink, modifier = Modifier.weight(1f),
                        )
                        MemorySwitch(block) { block = it }
                    }
                }
                Text(
                    t("Правила сообщества"),
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Lilac,
                    modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onRules),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = reason != null, onClick = { reason?.let { onSend(it, note, block) } }) {
                Text(t("Отправить"), color = if (reason != null) Palette.Lilac else Palette.Mist)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Отмена"), color = Palette.Mist) } },
    )
}
