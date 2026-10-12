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
import ru.wishka.memory.data.auth.SecurePrefs

data class AuthState(
    /** Идёт проверка сохранённой сессии — пока заставка, решать рано. */
    val checking: Boolean = true,
    val account: Account? = null,
    val busy: Boolean = false,
    val error: String? = null,
    /** Меняется, когда человек принял документы: экран пересчитывает, нужен ли запрос. */
    val consentTick: Int = 0,
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

    fun login(username: String, password: String) = run { auth.login(username, password) }

    /** consented — человек отметил возраст, соглашение и согласие на обработку: запоминаем версию документов. */
    fun register(username: String, displayName: String, password: String, repeat: String, consented: Boolean = false) =
        run(onOk = { account -> if (consented) consents.accept(account.username, System.currentTimeMillis()) }) {
            auth.register(username, displayName, password, repeat)
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
