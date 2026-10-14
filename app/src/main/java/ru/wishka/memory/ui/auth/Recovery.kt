package ru.wishka.memory.ui.auth

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.wishka.memory.data.auth.LocalAuth
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.components.Chip
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.theme.Palette

/**
 * Что «Безопасности» нужно для восстановления доступа: коды и почта.
 * Реализация — AuthViewModel; когда появится сервер, экраны не изменятся.
 */
class RecoveryActions(
    val left: () -> Int,
    /** Новые коды после проверки пароля; null — пароль не подошёл. */
    val regenerate: suspend (password: String) -> List<String>?,
    val email: () -> String,
    val emailConfirmed: () -> Boolean,
    /** null — письмо с кодом ушло, иначе текст ошибки. */
    val setEmail: (String) -> String?,
    val confirmEmail: (String) -> String?,
    val removeEmail: () -> Unit,
    /** Демо: код последнего «письма» — пока сервера нет, он виден на экране. */
    val demoCode: () -> String?,
)

/** Скопировать текст и сказать об этом. */
fun copyText(context: Context, label: String, text: String, toast: String = t("Скопировано")) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
}

/**
 * Коды восстановления — показываются один раз: сразу после регистрации и
 * после «Новые коды». Закрыть можно, только отметив, что коды сохранены.
 */
@Composable
fun RecoveryCodesDialog(codes: List<String>, onDone: () -> Unit) {
    val context = LocalContext.current
    var saved by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        containerColor = Palette.Night,
        title = { Text(t("Коды восстановления"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    t("Если забудете пароль, войдите по одному из этих кодов. Каждый код работает один раз. Сохраните их там, где их не увидят другие: в менеджере паролей или на бумаге."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                )
                codes.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        pair.forEach { Text(it, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, fontFamily = FontFamily.Monospace) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(t("Скопировать"), active = false) { copyText(context, "Memory", codes.joinToString("\n")) }
                    Chip(if (saved) t("✓ Сохранил") else t("Я сохранил коды"), active = saved) { saved = !saved }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDone, enabled = saved) { Text(t("Готово"), color = if (saved) Palette.Lilac else Palette.Mist) }
        },
    )
}

/** Как восстановить доступ: кодом восстановления или письмом на почту. */
private enum class RecoverBy { Code, Email }

/**
 * «Забыли пароль?» на экране входа. По коду — сразу новый пароль; по
 * почте — сначала письмо с кодом, потом код и новый пароль.
 */
@Composable
fun ForgotPasswordForm(vm: AuthViewModel, state: AuthState, login: String, onLogin: (String) -> Unit, onBack: () -> Unit) {
    var by by rememberSaveable { mutableStateOf(RecoverBy.Code) }
    var code by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(t("Восстановление доступа"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(t("Код восстановления"), active = by == RecoverBy.Code) { by = RecoverBy.Code; code = ""; vm.clearError(); vm.clearResetSent() }
            Chip(t("Письмо на почту"), active = by == RecoverBy.Email) { by = RecoverBy.Email; code = ""; vm.clearError() }
        }
        RecoveryField(login, { onLogin(it.take(24)) }, t("Логин"), keyboard = KeyboardType.Ascii)

        val emailStep2 = by == RecoverBy.Email && state.resetSent != null
        if (by == RecoverBy.Email && !emailStep2) {
            Text(
                t("Пришлём код на почту, которую вы подтвердили в «Безопасности»."),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Mist,
            )
            state.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            GlowButton(t("Прислать код"), enabled = login.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = { vm.startEmailRecovery(login) })
        } else {
            if (emailStep2) {
                Text(state.resetSent.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                vm.demoCode()?.let { DemoCodeHint(it) }
            }
            RecoveryField(
                code,
                { code = if (by == RecoverBy.Email) it.filter(Char::isDigit).take(6) else it.take(12) },
                if (by == RecoverBy.Email) t("Код из письма") else t("Код восстановления, например K7QF-2MXP"),
                keyboard = if (by == RecoverBy.Email) KeyboardType.Number else KeyboardType.Ascii,
            )
            RecoveryField(password, { password = it.take(64) }, t("Новый пароль"), password = true)
            RecoveryField(repeat, { repeat = it.take(64) }, t("Новый пароль ещё раз"), password = true)
            state.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            GlowButton(
                if (state.busy) t("Подождите…") else t("Сменить пароль и войти"),
                enabled = !state.busy && login.isNotBlank() && code.isNotBlank() && password.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (by == RecoverBy.Code) vm.recoverWithCode(login, code, password, repeat)
                    else vm.recoverWithEmail(login, code, password, repeat)
                },
            )
            Text(
                if (by == RecoverBy.Code) t("Коды восстановления показываются при регистрации. Каждый код работает один раз.")
                else t("Код из письма действует 15 минут."),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Mist,
            )
        }
        Text(
            t("← Назад ко входу"),
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Lilac,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                vm.clearError()
                vm.clearResetSent()
                onBack()
            },
        )
    }
}

/** Пока писем нет — код «письма» прямо на экране, с пометкой «демо». */
@Composable
fun DemoCodeHint(code: String) {
    Text(
        tf("Демо: письма пока не отправляются — ваш код %s", code),
        style = MaterialTheme.typography.bodyMedium,
        color = Palette.Lilac,
    )
}

@Composable
private fun RecoveryField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    GlassField(
        value, onValueChange, placeholder,
        singleLine = true,
        password = password,
        keyboardType = keyboard,
        modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)),
    )
}

/**
 * «Безопасность → Восстановление доступа»: сколько кодов осталось, новые
 * коды (по паролю) и почта с подтверждением.
 */
@Composable
fun RecoverySettings(actions: RecoveryActions, caption: @Composable (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var left by remember { mutableStateOf(actions.left()) }
    var regenerating by rememberSaveable { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var codes by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    var email by remember { mutableStateOf(actions.email()) }
    var confirmed by remember { mutableStateOf(actions.emailConfirmed()) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var address by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        caption(t("Восстановление доступа"))
        Text(
            if (left > 0) tf("Кодов восстановления осталось: %s из %s", left, LocalAuth.RECOVERY_CODES)
            else t("Кодов восстановления нет. Создайте новые — без них забытый пароль не вернуть."),
            style = MaterialTheme.typography.bodyMedium,
            color = if (left > 2) Palette.Mist else Palette.Lilac,
        )
        if (!regenerating) {
            GhostButton(t("Новые коды"), onClick = { regenerating = true })
        } else {
            RecoveryField(password, { password = it.take(64); error = null }, t("Пароль для подтверждения"), password = true)
            Text(t("Старые коды перестанут работать."), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(t("Отмена"), onClick = { regenerating = false; password = ""; error = null })
                GlowButton(t("Создать"), enabled = password.isNotEmpty(), onClick = {
                    scope.launch {
                        val fresh = actions.regenerate(password)
                        if (fresh == null) error = t("Пароль не подходит") else {
                            codes = fresh
                            regenerating = false
                            password = ""
                            left = fresh.size
                        }
                    }
                })
            }
        }

        // Почта: указать → код из письма → подтверждена
        Text(t("Почта"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
        when {
            email.isNotEmpty() && confirmed && !editing -> {
                Text(tf("%s — подтверждена. По ней можно восстановить пароль.", email), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton(t("Изменить"), onClick = { editing = true; address = email })
                    GhostButton(t("Убрать"), onClick = { actions.removeEmail(); email = ""; confirmed = false })
                }
            }
            email.isNotEmpty() && !confirmed && !editing -> {
                Text(tf("Письмо с кодом ушло на %s.", email), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                actions.demoCode()?.let { DemoCodeHint(it) }
                RecoveryField(code, { code = it.filter(Char::isDigit).take(6); emailError = null }, t("Код из письма"), keyboard = KeyboardType.Number)
                emailError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton(t("Прислать снова"), onClick = { emailError = actions.setEmail(email); code = "" })
                    GlowButton(t("Подтвердить"), enabled = code.length == 6, onClick = {
                        emailError = actions.confirmEmail(code)
                        if (emailError == null) { confirmed = true; code = "" }
                    })
                }
            }
            else -> {
                Text(t("Подтверждённая почта — второй способ вернуть доступ, если коды потеряются."), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                RecoveryField(address, { address = it.take(80); emailError = null }, t("Адрес почты"), keyboard = KeyboardType.Email)
                emailError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (editing) GhostButton(t("Отмена"), onClick = { editing = false; emailError = null })
                    GlowButton(t("Прислать код"), enabled = address.isNotBlank(), onClick = {
                        emailError = actions.setEmail(address)
                        if (emailError == null) { email = address.trim().lowercase(); confirmed = false; editing = false; code = "" }
                    })
                }
            }
        }
    }
    codes?.let { RecoveryCodesDialog(it) { codes = null } }
}
