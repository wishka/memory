package ru.wishka.memory.data.auth

/**
 * Хранилище «ключ → строка». На телефоне — SharedPreferences с
 * шифрованием сессии (SecurePrefs), в тестах — обычная карта.
 */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

/** Аккаунт на этом устройстве. */
data class Account(val username: String, val displayName: String)

sealed class AuthResult {
    data class Ok(val account: Account) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

/**
 * Вход по логину и паролю без сервера: аккаунты живут на телефоне.
 *
 * Сессия запоминается: после входа приложение больше не спрашивает
 * пароль, пока человек сам не выйдет из профиля. Когда у Memory появится
 * сервер, вместо этого класса встанет сетевой — экраны не изменятся:
 * они знают только register / login / logout / current.
 *
 * Защита от подбора: после MAX_FAILURES неудачных попыток подряд вход
 * для этого логина закрывается на LOCK_MS.
 */
class LocalAuth(
    private val store: KeyValueStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val iterations: Int = PasswordHasher.ITERATIONS,
) {
    /** Текущий вошедший пользователь или null. Сессия переживает перезапуск. */
    fun current(): Account? {
        val username = store.get(SESSION_USER) ?: return null
        if (store.get(SESSION_TOKEN).isNullOrEmpty()) return null
        return account(username)
    }

    fun register(username: String, displayName: String, password: String, repeat: String): AuthResult {
        val login = normalize(username)
        validateLogin(login)?.let { return AuthResult.Error(it) }
        val name = displayName.trim()
        if (name.isEmpty()) return AuthResult.Error("Укажите имя")
        if (name.length > 40) return AuthResult.Error("Имя длиннее 40 символов")
        validatePassword(password)?.let { return AuthResult.Error(it) }
        if (password != repeat) return AuthResult.Error("Пароли не совпадают")
        if (store.get(key(login, "hash")) != null) return AuthResult.Error("Логин @$login уже занят")

        val salt = PasswordHasher.newSalt()
        store.put(key(login, "salt"), salt.toHex())
        store.put(key(login, "hash"), PasswordHasher.hash(password, salt, iterations).toHex())
        store.put(key(login, "iter"), iterations.toString())
        store.put(key(login, "name"), name)
        return startSession(login)
    }

    fun login(username: String, password: String): AuthResult {
        val login = normalize(username)
        if (login.isEmpty() || password.isEmpty()) return AuthResult.Error("Введите логин и пароль")

        val lockedUntil = store.get(key(login, "locked"))?.toLongOrNull() ?: 0L
        val now = clock()
        if (now < lockedUntil) {
            val sec = ((lockedUntil - now) / 1000).coerceAtLeast(1)
            return AuthResult.Error("Слишком много попыток. Попробуйте через $sec с")
        }

        val hash = store.get(key(login, "hash"))
        val salt = store.get(key(login, "salt"))
        val iter = store.get(key(login, "iter"))?.toIntOrNull() ?: PasswordHasher.ITERATIONS
        // Одинаковый ответ для «нет такого логина» и «неверный пароль»:
        // иначе по ответу можно узнать, какие логины существуют
        val ok = hash != null && salt != null &&
            PasswordHasher.verify(password, salt.fromHex(), hash.fromHex(), iter)
        if (!ok) {
            if (hash != null) registerFailure(login, now)
            return AuthResult.Error("Неверный логин или пароль")
        }
        store.remove(key(login, "fails"))
        store.remove(key(login, "locked"))
        return startSession(login)
    }

    fun logout() {
        store.remove(SESSION_USER)
        store.remove(SESSION_TOKEN)
    }

    /** Новое имя для профиля. */
    fun rename(username: String, displayName: String) {
        val name = displayName.trim()
        if (name.isNotEmpty()) store.put(key(normalize(username), "name"), name.take(40))
    }

    /** Демо-аккаунт для первого знакомства с приложением; ничего не делает, если он уже есть. */
    fun ensureDemoAccount() {
        if (store.get(key(DEMO_LOGIN, "hash")) != null) return
        val salt = PasswordHasher.newSalt()
        store.put(key(DEMO_LOGIN, "salt"), salt.toHex())
        store.put(key(DEMO_LOGIN, "hash"), PasswordHasher.hash(DEMO_PASSWORD, salt, iterations).toHex())
        store.put(key(DEMO_LOGIN, "iter"), iterations.toString())
        store.put(key(DEMO_LOGIN, "name"), "Гость Memory")
    }

    private fun startSession(login: String): AuthResult {
        store.put(SESSION_USER, login)
        store.put(SESSION_TOKEN, PasswordHasher.randomToken())
        return AuthResult.Ok(account(login) ?: Account(login, login))
    }

    private fun account(login: String): Account? {
        if (store.get(key(login, "hash")) == null) return null
        return Account(login, store.get(key(login, "name")) ?: login)
    }

    private fun registerFailure(login: String, now: Long) {
        val fails = (store.get(key(login, "fails"))?.toIntOrNull() ?: 0) + 1
        if (fails >= MAX_FAILURES) {
            store.put(key(login, "locked"), (now + LOCK_MS).toString())
            store.remove(key(login, "fails"))
        } else {
            store.put(key(login, "fails"), fails.toString())
        }
    }

    companion object {
        const val MAX_FAILURES = 5
        const val LOCK_MS = 30_000L
        const val DEMO_LOGIN = "demo"
        const val DEMO_PASSWORD = "memory2026"

        const val SESSION_USER = "session.user"
        const val SESSION_TOKEN = "session.token"

        private val LOGIN_RE = Regex("^[a-z0-9._]{3,20}$")

        fun normalize(username: String): String = username.trim().removePrefix("@").lowercase()

        fun validateLogin(login: String): String? = when {
            login.length < 3 -> "Логин — от 3 символов"
            login.length > 20 -> "Логин — не длиннее 20 символов"
            !LOGIN_RE.matches(login) -> "Логин: латинские буквы, цифры, точка и подчёркивание"
            else -> null
        }

        fun validatePassword(password: String): String? = when {
            password.length < 8 -> "Пароль — от 8 символов"
            password.none { it.isLetter() } || password.none { it.isDigit() } -> "В пароле нужны буквы и цифры"
            else -> null
        }

        private fun key(login: String, field: String) = "acc.$login.$field"
    }
}
