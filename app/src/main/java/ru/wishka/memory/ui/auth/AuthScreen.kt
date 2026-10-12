package ru.wishka.memory.ui.auth

import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.auth.LocalAuth
import ru.wishka.memory.ui.components.GlassCard
import ru.wishka.memory.ui.components.ConsentChecks
import ru.wishka.memory.ui.components.ConsentState
import ru.wishka.memory.ui.components.LegalDocViewer
import ru.wishka.memory.ui.components.LegalLinks
import ru.wishka.memory.data.LegalDoc
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.splash.SplashSky.drawSky
import ru.wishka.memory.ui.theme.Palette

/**
 * Вход и регистрация на том же ночном небе, что и заставка: после
 * анимации слова человек остаётся «в той же сцене».
 *
 * После входа приложение запоминает человека — этот экран он увидит
 * снова только если выйдет из профиля.
 */
@Composable
fun AuthScreen(vm: AuthViewModel, state: AuthState, startRegistering: Boolean = false) {
    var registering by rememberSaveable { mutableStateOf(startRegistering) }
    var login by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    // Пароли не сохраняются при повороте экрана: им нечего делать в
    // сохранённом состоянии активности
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    // Возраст, соглашение и согласие на обработку — три отдельные галочки
    val consent = remember { ConsentState() }
    var openDoc by remember { mutableStateOf<LegalDoc?>(null) }
    val density = LocalDensity.current.density

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) { drawSky(appear = 1f, time = 0L, density = density) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(40.dp))
            Text("MEMORY", style = MaterialTheme.typography.displaySmall, color = Palette.Ink)
            Text(
                t("МОМЕНТЫ, КОТОРЫЕ ОСТАЮТСЯ"),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Mist,
            )
            Spacer(Modifier.height(28.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Переключатель «Вход | Регистрация»
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glass(RoundedCornerShape(50)),
                    ) {
                        listOf(false to t("Вход"), true to t("Регистрация")).forEach { (mode, title) ->
                            val active = registering == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .then(if (active) Modifier.glass(RoundedCornerShape(50), strong = true) else Modifier)
                                    .clickable {
                                        registering = mode
                                        vm.clearError()
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    title.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) Palette.Lilac else Palette.Mist,
                                )
                            }
                        }
                    }

                    Field(login, { login = it.take(24) }, t("Логин"), keyboard = KeyboardType.Ascii)
                    if (registering) Field(name, { name = it.take(40) }, t("Имя — как вас видят другие"))
                    Field(password, { password = it.take(64) }, t("Пароль"), password = true)
                    if (registering) Field(repeat, { repeat = it.take(64) }, t("Пароль ещё раз"), password = true)

                    if (registering) ConsentChecks(consent) { openDoc = it }

                    state.error?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }

                    GlowButton(
                        if (state.busy) t("Подождите…") else if (registering) t("Создать аккаунт") else t("Войти"),
                        enabled = !state.busy && login.isNotBlank() && password.isNotEmpty() && (!registering || consent.all),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (registering) vm.register(login, name, password, repeat, consented = consent.all) else vm.login(login, password)
                        },
                    )

                    Text(
                        if (registering) {
                            t("Логин — латиница, цифры, точка и «_», от 3 символов. ") +
                                t("Пароль — от 8 символов, с буквами и цифрами.")
                        } else {
                            tf("Попробовать без регистрации: логин %s, ", LocalAuth.DEMO_LOGIN) +
                                tf("пароль %s", LocalAuth.DEMO_PASSWORD)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Mist,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                t("Аккаунты пока хранятся только на этом телефоне."),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Mist,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            LegalLinks(listOf(LegalDoc.Terms, LegalDoc.Privacy), onOpen = { openDoc = it })
        }
    }
    openDoc?.let { LegalDocViewer(it) { openDoc = null } }
}

@Composable
private fun Field(
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
        modifier = Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(16.dp)),
    )
}
