package ru.wishka.memory.data.auth

import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.data.ProfileInfo

/**
 * Хранилище «ключ → строка». На телефоне — SharedPreferences с
 * шифрованием сессии (SecurePrefs), в тестах — обычная карта.
 */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

/** Аккаунт на этом устройстве и то, что человек рассказал о себе. */
data class Account(val username: String, val displayName: String, val info: ProfileInfo = ProfileInfo())

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
    /** Кто отправляет письма с кодом. Пока сервера нет — демо: код показывается на экране. */
    private val mail: MailSender = DemoMail,
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
        if (name.isEmpty()) return AuthResult.Error(t("Укажите имя"))
        if (name.length > 40) return AuthResult.Error(t("Имя длиннее 40 символов"))
        validatePassword(password)?.let { return AuthResult.Error(it) }
        if (password != repeat) return AuthResult.Error(t("Пароли не совпадают"))
        if (store.get(key(login, "hash")) != null) return AuthResult.Error(tf("Логин @%s уже занят", login))

        val salt = PasswordHasher.newSalt()
        store.put(key(login, "salt"), salt.toHex())
        store.put(key(login, "hash"), PasswordHasher.hash(password, salt, iterations).toHex())
        store.put(key(login, "iter"), iterations.toString())
        store.put(key(login, "name"), name)
        return startSession(login)
    }

    fun login(username: String, password: String): AuthResult {
        val login = normalize(username)
        if (login.isEmpty() || password.isEmpty()) return AuthResult.Error(t("Введите логин и пароль"))

        val lockedUntil = store.get(key(login, "locked"))?.toLongOrNull() ?: 0L
        val now = clock()
        if (now < lockedUntil) {
            val sec = ((lockedUntil - now) / 1000).coerceAtLeast(1)
            return AuthResult.Error(tf("Слишком много попыток. Попробуйте через %s с", sec))
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
            return AuthResult.Error(t("Неверный логин или пароль"))
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

    /** Сведения профиля: переживают перезапуск вместе с аккаунтом. */
    fun saveProfile(username: String, info: ProfileInfo) {
        val login = normalize(username)
        store.put(key(login, "p.bio"), info.bio.trim().take(160))
        store.put(key(login, "p.city"), info.city.trim().take(40))
        store.put(key(login, "p.birthday"), info.birthday.trim().take(20))
        store.put(key(login, "p.website"), info.website.trim().take(80))
        store.put(key(login, "p.interests"), info.interests.joinToString("|"))
        store.put(key(login, "p.avatar"), info.avatar)
    }

    /**
     * Сменить пароль: нужен текущий. null — готово, иначе текст ошибки.
     * Сессия остаётся: человек не выходит из профиля.
     */
    fun changePassword(username: String, current: String, new: String, repeat: String): String? {
        val login = normalize(username)
        if (current.isEmpty()) return t("Введите текущий пароль")
        if (!checkPassword(login, current)) return t("Текущий пароль не подходит")
        validatePassword(new)?.let { return it }
        if (new != repeat) return t("Пароли не совпадают")
        if (new == current) return t("Новый пароль совпадает с текущим")
        val salt = PasswordHasher.newSalt()
        store.put(key(login, "salt"), salt.toHex())
        store.put(key(login, "hash"), PasswordHasher.hash(new, salt, iterations).toHex())
        store.put(key(login, "iter"), iterations.toString())
        return null
    }

    /**
     * Удалить аккаунт с этого телефона — после проверки пароля. Сессия
     * завершается. null — удалён, иначе текст ошибки.
     */
    fun deleteAccount(username: String, password: String): String? {
        val login = normalize(username)
        if (password.isEmpty()) return t("Введите пароль")
        if (!checkPassword(login, password)) return t("Пароль не подходит")
        for (field in listOf("salt", "hash", "iter", "name", "fails", "locked", "p.bio", "p.city", "p.birthday", "p.website", "p.interests", "p.avatar") + RECOVERY_FIELDS) {
            store.remove(key(login, field))
        }
        if (store.get(SESSION_USER) == login) logout()
        return null
    }

    // ── Восстановление доступа ────────────────────────────────────────

    /**
     * Десять новых одноразовых кодов восстановления; прежние перестают
     * работать. Сами коды не хранятся — только их хеши, как у пароля:
     * показать их можно один раз, сразу после создания.
     */
    fun newRecoveryCodes(username: String): List<String> {
        val login = normalize(username)
        if (store.get(key(login, "hash")) == null) return emptyList()
        val salt = PasswordHasher.newSalt()
        val codes = List(RECOVERY_CODES) { RecoveryCodes.generate() }
        store.put(key(login, "rc.salt"), salt.toHex())
        store.put(key(login, "rc"), codes.joinToString(",") { PasswordHasher.hash(RecoveryCodes.normalize(it), salt, recoveryIterations).toHex() })
        return codes
    }

    /** Сколько неиспользованных кодов осталось. */
    fun recoveryLeft(username: String): Int =
        store.get(key(normalize(username), "rc")).orEmpty().split(',').count { it.isNotEmpty() }

    /**
     * Вход по коду восстановления с новым паролем. Код одноразовый: после
     * входа он больше не подходит. Неверный код считается неудачной
     * попыткой входа — подбор упирается в ту же блокировку.
     */
    fun recoverWithCode(username: String, code: String, new: String, repeat: String): AuthResult {
        val login = normalize(username)
        if (login.isEmpty() || code.isBlank()) return AuthResult.Error(t("Введите логин и код"))
        locked(login)?.let { return AuthResult.Error(it) }
        validatePassword(new)?.let { return AuthResult.Error(it) }
        if (new != repeat) return AuthResult.Error(t("Пароли не совпадают"))
        val salt = store.get(key(login, "rc.salt"))
        val hashes = store.get(key(login, "rc")).orEmpty().split(',').filter { it.isNotEmpty() }
        val normalized = RecoveryCodes.normalize(code)
        val hit = if (salt == null || normalized.length != RecoveryCodes.LENGTH) -1 else {
            val h = PasswordHasher.hash(normalized, salt.fromHex(), recoveryIterations).toHex()
            hashes.indexOfFirst { java.security.MessageDigest.isEqual(it.toByteArray(), h.toByteArray()) }
        }
        if (hit < 0) {
            if (store.get(key(login, "hash")) != null) registerFailure(login, clock())
            return AuthResult.Error(t("Неверный логин или код"))
        }
        store.put(key(login, "rc"), hashes.filterIndexed { i, _ -> i != hit }.joinToString(","))
        setPassword(login, new)
        return startSession(login)
    }

    /** Почта аккаунта ("" — не указана) и подтверждена ли она. */
    fun email(username: String): String = store.get(key(normalize(username), "email")).orEmpty()

    fun emailConfirmed(username: String): Boolean = store.get(key(normalize(username), "email.ok")) == "1"

    /**
     * Указать почту: на неё уходит шестизначный код подтверждения. До
     * подтверждения почта не годится для восстановления. null — письмо
     * отправлено, иначе текст ошибки.
     */
    fun setEmail(username: String, email: String): String? {
        val login = normalize(username)
        val address = email.trim().lowercase()
        if (!EMAIL_RE.matches(address)) return t("Похоже, в адресе почты ошибка")
        store.put(key(login, "email"), address)
        store.remove(key(login, "email.ok"))
        sendCode(login, address, "email")
        return null
    }

    /** Код из письма подтверждает почту. null — подтверждена. */
    fun confirmEmail(username: String, code: String): String? {
        val login = normalize(username)
        checkCode(login, "email", code)?.let { return it }
        store.put(key(login, "email.ok"), "1")
        return null
    }

    /** Убрать почту из аккаунта. */
    fun removeEmail(username: String) {
        val login = normalize(username)
        listOf("email", "email.ok", "code.email", "code.email.until", "code.email.tries").forEach { store.remove(key(login, it)) }
    }

    /**
     * «Забыли пароль?» по почте: если у логина есть подтверждённая почта,
     * на неё уходит код. Ответ одинаковый, есть почта или нет, — иначе по
     * нему можно было бы узнать, какие логины существуют.
     */
    fun startEmailRecovery(username: String): String {
        val login = normalize(username)
        if (emailConfirmed(login) && email(login).isNotEmpty()) sendCode(login, email(login), "reset")
        return t("Если у этого логина есть подтверждённая почта, на неё пришло письмо с кодом.")
    }

    /** Код из письма + новый пароль: вход с новым паролем. */
    fun recoverWithEmail(username: String, code: String, new: String, repeat: String): AuthResult {
        val login = normalize(username)
        locked(login)?.let { return AuthResult.Error(it) }
        validatePassword(new)?.let { return AuthResult.Error(it) }
        if (new != repeat) return AuthResult.Error(t("Пароли не совпадают"))
        checkCode(login, "reset", code)?.let {
            if (store.get(key(login, "hash")) != null) registerFailure(login, clock())
            return AuthResult.Error(it)
        }
        setPassword(login, new)
        return startSession(login)
    }

    /** Почта в виде «a•••@mail.ru» — для подсказок, без раскрытия адреса целиком. */
    fun maskedEmail(username: String): String = maskEmail(email(username))

    private fun sendCode(login: String, address: String, purpose: String) {
        val code = RecoveryCodes.digits()
        store.put(key(login, "code.$purpose"), PasswordHasher.hash(code, login.toByteArray(), 1_000).toHex())
        store.put(key(login, "code.$purpose.until"), (clock() + CODE_TTL_MS).toString())
        store.remove(key(login, "code.$purpose.tries"))
        mail.send(address, code)
    }

    private fun checkCode(login: String, purpose: String, code: String): String? {
        val saved = store.get(key(login, "code.$purpose")) ?: return t("Код устарел — запросите новый")
        val until = store.get(key(login, "code.$purpose.until"))?.toLongOrNull() ?: 0
        if (clock() > until) return t("Код устарел — запросите новый")
        val tries = (store.get(key(login, "code.$purpose.tries"))?.toIntOrNull() ?: 0) + 1
        val ok = PasswordHasher.hash(code.filter { it.isDigit() }, login.toByteArray(), 1_000).toHex() == saved
        if (!ok) {
            if (tries >= CODE_TRIES) {
                store.remove(key(login, "code.$purpose"))
                return t("Слишком много попыток — запросите новый код")
            }
            store.put(key(login, "code.$purpose.tries"), tries.toString())
            return t("Код не подходит")
        }
        listOf("code.$purpose", "code.$purpose.until", "code.$purpose.tries").forEach { store.remove(key(login, it)) }
        return null
    }

    private fun locked(login: String): String? {
        val lockedUntil = store.get(key(login, "locked"))?.toLongOrNull() ?: 0L
        val now = clock()
        if (now >= lockedUntil) return null
        val sec = ((lockedUntil - now) / 1000).coerceAtLeast(1)
        return tf("Слишком много попыток. Попробуйте через %s с", sec)
    }

    private fun setPassword(login: String, password: String) {
        val salt = PasswordHasher.newSalt()
        store.put(key(login, "salt"), salt.toHex())
        store.put(key(login, "hash"), PasswordHasher.hash(password, salt, iterations).toHex())
        store.put(key(login, "iter"), iterations.toString())
        store.remove(key(login, "fails"))
        store.remove(key(login, "locked"))
    }

    private val recoveryIterations: Int get() = (iterations / 12).coerceAtLeast(1)

    /** Пароль подходит — для действий, которые просят подтверждения (новые коды). */
    fun checkPasswordOf(username: String, password: String): Boolean = checkPassword(normalize(username), password)

    private fun checkPassword(login: String, password: String): Boolean {
        val hash = store.get(key(login, "hash")) ?: return false
        val salt = store.get(key(login, "salt")) ?: return false
        val iter = store.get(key(login, "iter"))?.toIntOrNull() ?: PasswordHasher.ITERATIONS
        return PasswordHasher.verify(password, salt.fromHex(), hash.fromHex(), iter)
    }

    /** Демо-аккаунт для первого знакомства с приложением; ничего не делает, если он уже есть. */
    fun ensureDemoAccount() {
        if (store.get(key(DEMO_LOGIN, "hash")) != null) return
        val salt = PasswordHasher.newSalt()
        store.put(key(DEMO_LOGIN, "salt"), salt.toHex())
        store.put(key(DEMO_LOGIN, "hash"), PasswordHasher.hash(DEMO_PASSWORD, salt, iterations).toHex())
        store.put(key(DEMO_LOGIN, "iter"), iterations.toString())
        store.put(key(DEMO_LOGIN, "name"), t("Гость Memory"))
    }

    private fun startSession(login: String): AuthResult {
        store.put(SESSION_USER, login)
        store.put(SESSION_TOKEN, PasswordHasher.randomToken())
        return AuthResult.Ok(account(login) ?: Account(login, login))
    }

    private fun account(login: String): Account? {
        if (store.get(key(login, "hash")) == null) return null
        val info = ProfileInfo(
            bio = store.get(key(login, "p.bio")).orEmpty(),
            city = store.get(key(login, "p.city")).orEmpty(),
            birthday = store.get(key(login, "p.birthday")).orEmpty(),
            website = store.get(key(login, "p.website")).orEmpty(),
            interests = store.get(key(login, "p.interests")).orEmpty().split('|').filter { it.isNotBlank() },
            avatar = store.get(key(login, "p.avatar")).orEmpty(),
        )
        return Account(login, store.get(key(login, "name")) ?: login, info)
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

        const val RECOVERY_CODES = 10
        const val CODE_TTL_MS = 15 * 60_000L
        const val CODE_TRIES = 5

        private val RECOVERY_FIELDS = listOf(
            "rc", "rc.salt", "email", "email.ok",
            "code.email", "code.email.until", "code.email.tries", "code.reset", "code.reset.until", "code.reset.tries",
        )
        private val EMAIL_RE = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")

        /** «anna@mail.ru» → «a•••@mail.ru». */
        fun maskEmail(email: String): String {
            if ('@' !in email) return email
            val name = email.substringBefore('@')
            return name.take(1) + "•••@" + email.substringAfter('@')
        }

        const val SESSION_USER = "session.user"
        const val SESSION_TOKEN = "session.token"

        private val LOGIN_RE = Regex("^[a-z0-9._]{3,20}$")

        fun normalize(username: String): String = username.trim().removePrefix("@").lowercase()

        fun validateLogin(login: String): String? = when {
            login.length < 3 -> t("Логин — от 3 символов")
            login.length > 20 -> t("Логин — не длиннее 20 символов")
            !LOGIN_RE.matches(login) -> t("Логин: латинские буквы, цифры, точка и подчёркивание")
            else -> null
        }

        fun validatePassword(password: String): String? = when {
            password.length < 8 -> t("Пароль — от 8 символов")
            password.none { it.isLetter() } || password.none { it.isDigit() } -> t("В пароле нужны буквы и цифры")
            else -> null
        }

        private fun key(login: String, field: String) = "acc.$login.$field"
    }
}

/** Отправка письма с кодом. Когда появится сервер — письмо уйдёт с него. */
fun interface MailSender {
    fun send(email: String, code: String)
}

/**
 * Демо-почта: сервера нет, письмо не отправить — последний код виден на
 * экране подтверждения («Демо: код 123456»). Заменить на сетевую
 * отправку — передать в LocalAuth свой MailSender.
 */
object DemoMail : MailSender {
    @Volatile
    var last: Pair<String, String>? = null
        private set

    override fun send(email: String, code: String) {
        last = email to code
    }
}

/** Коды восстановления: «K7QF-2MXP» — 8 знаков без похожих букв и цифр. */
object RecoveryCodes {
    const val LENGTH = 8
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val random = java.security.SecureRandom()

    fun generate(): String {
        val raw = (0 until LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
        return raw.take(4) + "-" + raw.drop(4)
    }

    /** Шестизначный код для письма. */
    fun digits(): String = (0 until 6).map { random.nextInt(10) }.joinToString("")

    /** Как человек ни ввёл — «k7qf 2mxp», «K7QF-2MXP» — одно и то же. */
    fun normalize(code: String): String = code.uppercase().filter { it in ALPHABET }
}
