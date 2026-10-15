package ru.wishka.memory.server

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Пароли, токены, отпечатки — только стандартная криптография JDK. */
object Crypto {
    private val rng = SecureRandom()
    private const val ITERATIONS = 210_000

    fun randomToken(bytes: Int = 32): String {
        val b = ByteArray(bytes).also(rng::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    }

    fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun newSalt(): String = randomToken(16)

    /** PBKDF2-HMAC-SHA256: в базе — только это, сам пароль не хранится. */
    fun hashPassword(password: String, salt: String, iterations: Int = ITERATIONS): String {
        val spec = PBEKeySpec(password.toCharArray(), salt.toByteArray(Charsets.UTF_8), iterations, 256)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return "pbkdf2$" + iterations + "$" + Base64.getEncoder().encodeToString(raw)
    }

    fun checkPassword(password: String, salt: String, stored: String): Boolean {
        val iterations = stored.split('$').getOrNull(1)?.toIntOrNull() ?: return false
        val candidate = hashPassword(password, salt, iterations)
        // Сравнение за постоянное время
        return MessageDigest.isEqual(candidate.toByteArray(), stored.toByteArray())
    }
}
