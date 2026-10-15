package ru.wishka.memory.ui.auth

import ru.wishka.memory.i18n.t
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.wishka.memory.data.ConsentStore
import ru.wishka.memory.data.ProfileInfo
import ru.wishka.memory.data.auth.Account
import ru.wishka.memory.data.auth.AuthResult
import ru.wishka.memory.data.auth.LocalAuth
import ru.wishka.memory.data.auth.DemoMail
import ru.wishka.memory.data.auth.SecurePrefs

data class AuthState(
    /** Идёт проверка сохранённой сессии — пока заставка, решать рано. */
    val checking: Boolean = true,
    val account: Account? = null,
    val busy: Boolean = false,
    val error: String? = null,
    /** Меняется, когда человек принял документы: экран пересчитывает, нужен ли запрос. */
    val consentTick: Int = 0,
    /** Только что созданные коды восстановления — показать один раз и забыть. */
    val recoveryCodes: List<String>? = null,
    /** «Забыли пароль?»: письмо с кодом отправлено (текст для человека). */
    val resetSent: String? = null,
)

/**
 * Вход и регистрация. Хеширование пароля намеренно медленное
 * (PBKDF2, 120 000 итераций), поэтому всё — не в главном потоке:
 * иначе экран замирал бы на полсекунды при каждом нажатии «Войти».
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = LocalAuth(SecurePrefs(application))
    private val consents = ConsentStore(application)

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val account = withContext(Dispatchers.Default) {
                auth.ensureDemoAccount()
                auth.current()
            }
            _state.update { it.copy(checking = false, account = account) }
        }
    }

    /**
     * Пароль последнего входа — один раз, чтобы войти и на сервер (не
     * хранится: забирается takePassword() и стирается).
     */
    @Volatile private var pendingPassword: String? = null

    fun takePassword(): String? = pendingPassword.also { pendingPassword = null }

    fun login(username: String, password: String) = run(onOk = { pendingPassword = password }) { auth.login(username, password) }

    /** consented — человек отметил возраст, соглашение и согласие на обработку: запоминаем версию документов. */
    fun register(username: String, displayName: String, password: String, repeat: String, consented: Boolean = false) {
        // Коды восстановления — сразу при регистрации: без сервера это единственный путь назад.
        // Хешируются они не в главном потоке — вместе с паролем
        var codes: List<String> = emptyList()
        run(onOk = { account ->
            pendingPassword = password
            if (consented) consents.accept(account.username, System.currentTimeMillis())
            ru.wishka.memory.data.Telemetry.track(ru.wishka.memory.data.AppEvent.SignUp)
            _state.update { it.copy(recoveryCodes = codes) }
        }) {
            auth.register(username, displayName, password, repeat).also { r ->
                if (r is AuthResult.Ok) codes = auth.newRecoveryCodes(r.account.username)
            }
        }
    }

    /** «Забыли пароль?» — вход по одноразовому коду восстановления с новым паролем. */
    fun recoverWithCode(username: String, code: String, new: String, repeat: String) =
        run(onOk = { ru.wishka.memory.data.Telemetry.track(ru.wishka.memory.data.AppEvent.RecoveryUsed) }) { auth.recoverWithCode(username, code, new, repeat) }

    /** «Забыли пароль?» по почте: письмо с кодом (если почта подтверждена). */
    fun startEmailRecovery(username: String) {
        if (username.isBlank()) return
        viewModelScope.launch {
            val text = withContext(Dispatchers.Default) { auth.startEmailRecovery(username) }
            _state.update { it.copy(resetSent = text, error = null) }
        }
    }

    fun recoverWithEmail(username: String, code: String, new: String, repeat: String) =
        run(onOk = { ru.wishka.memory.data.Telemetry.track(ru.wishka.memory.data.AppEvent.RecoveryUsed) }) { auth.recoverWithEmail(username, code, new, repeat) }

    /** Коды показаны и сохранены человеком. */
    fun dismissRecoveryCodes() {
        _state.update { it.copy(recoveryCodes = null) }
    }

    /** Новые коды из «Безопасности» — после проверки пароля. null — неверный пароль. */
    suspend fun regenerateRecoveryCodes(password: String): List<String>? {
        val account = _state.value.account ?: return null
        return withContext(Dispatchers.Default) {
            if (auth.checkPasswordOf(account.username, password)) auth.newRecoveryCodes(account.username) else null
        }
    }

    fun recoveryLeft(): Int = _state.value.account?.let { auth.recoveryLeft(it.username) } ?: 0

    fun email(): String = _state.value.account?.let { auth.email(it.username) }.orEmpty()

    fun emailConfirmed(): Boolean = _state.value.account?.let { auth.emailConfirmed(it.username) } ?: false

    /** Указать почту — уходит письмо с кодом. null — отправлено. */
    fun setEmail(email: String): String? = _state.value.account?.let { auth.setEmail(it.username, email) } ?: t("Вы не вошли")

    fun confirmEmail(code: String): String? = _state.value.account?.let { auth.confirmEmail(it.username, code) } ?: t("Вы не вошли")

    fun removeEmail() {
        _state.value.account?.let { auth.removeEmail(it.username) }
    }

    /** Какую версию документов и когда принял человек — для выгрузки данных. */
    fun consentInfo(): Pair<String?, Long> = _state.value.account?.let { consents.version(it.username) to consents.acceptedAt(it.username) } ?: (null to 0L)

    /** Демо: код из «письма» — пока сервера нет, его показывает экран. */
    fun demoCode(): String? = DemoMail.last?.second

    fun clearResetSent() {
        _state.update { it.copy(resetSent = null) }
    }

    /** Согласие с текущей версией документов. */
    fun hasConsent(username: String): Boolean = consents.accepted(username)

    fun acceptDocuments() {
        val account = _state.value.account ?: return
        consents.accept(account.username, System.currentTimeMillis())
        _state.update { it.copy(consentTick = it.consentTick + 1) }
    }

    fun logout() {
        auth.logout()
        _state.update { it.copy(account = null, error = null) }
    }

    /** Имя и сведения профиля — в сохранённый аккаунт, чтобы пережили перезапуск. */
    fun saveProfile(displayName: String, info: ProfileInfo) {
        val account = _state.value.account ?: return
        auth.rename(account.username, displayName)
        auth.saveProfile(account.username, info)
        _state.update { it.copy(account = account.copy(displayName = displayName.trim(), info = info)) }
    }

    /** Смена пароля в «Безопасности». Ответ — текст ошибки или null; пароль проверяется не в главном потоке. */
    suspend fun changePassword(current: String, new: String, repeat: String): String? {
        val account = _state.value.account ?: return t("Вы не вошли")
        return withContext(Dispatchers.Default) { auth.changePassword(account.username, current, new, repeat) }
    }

    /** Удалить аккаунт с телефона. null — удалён и выполнен выход. */
    suspend fun deleteAccount(password: String): String? {
        val account = _state.value.account ?: return t("Вы не вошли")
        val error = withContext(Dispatchers.Default) { auth.deleteAccount(account.username, password) }
        if (error == null) {
            consents.forget(account.username)
            _state.update { it.copy(account = null, error = null) }
        }
        return error
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun run(onOk: (Account) -> Unit = {}, block: () -> AuthResult) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { block() }
            if (result is AuthResult.Ok) onOk(result.account)
            _state.update {
                when (result) {
                    is AuthResult.Ok -> it.copy(busy = false, account = result.account)
                    is AuthResult.Error -> it.copy(busy = false, error = result.message)
                }
            }
        }
    }
}
