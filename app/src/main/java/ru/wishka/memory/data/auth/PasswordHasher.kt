package ru.wishka.memory.data.auth

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Хранение паролей: PBKDF2-HMAC-SHA256 с солью на каждый аккаунт.
 *
 * Сам пароль нигде не сохраняется — только соль и производный ключ.
 * Даже если кто-то вытащит файл настроек с телефона, пароль из него не
 * восстановить, а перебор упирается в 120 000 итераций на каждую попытку.
 */
object PasswordHasher {
    const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private val random = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { random.nextBytes(it) }

    fun hash(password: String, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Сравнение за постоянное время: по времени ответа не угадать, сколько байт совпало. */
    fun verify(password: String, salt: ByteArray, expected: ByteArray, iterations: Int = ITERATIONS): Boolean =
        MessageDigest.isEqual(hash(password, salt, iterations), expected)

    fun randomToken(): String = ByteArray(32).also { random.nextBytes(it) }.toHex()
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

fun String.fromHex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
