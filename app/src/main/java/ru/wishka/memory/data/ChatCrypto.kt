package ru.wishka.memory.data

import ru.wishka.memory.data.auth.KeyValueStore
import ru.wishka.memory.i18n.t
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * X25519 (RFC 7748): обмен ключами по кривой Curve25519. Своя реализация
 * на BigInteger — без внешних библиотек, проверена векторами из RFC.
 * Формат ключей — 32 байта, как у CryptoKit (Curve25519.KeyAgreement) на
 * iPhone, так что Android и iPhone понимают друг друга.
 *
 * Перед выходом с сервером стоит заменить на библиотечную реализацию с
 * постоянным временем (Tink / libsodium): BigInteger не защищает от
 * атак по времени на самом телефоне.
 */
object X25519 {
    private val P: BigInteger = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    private val A24: BigInteger = BigInteger.valueOf(121665)
    private val BASE = ByteArray(32).also { it[0] = 9 }
    private val random = SecureRandom()

    fun newPrivateKey(): ByteArray = ByteArray(32).also { random.nextBytes(it) }

    fun publicKey(privateKey: ByteArray): ByteArray = scalarMult(privateKey, BASE)

    /** Общий секрет: мой закрытый ключ × открытый ключ собеседника. */
    fun sharedSecret(privateKey: ByteArray, peerPublic: ByteArray): ByteArray {
        val out = scalarMult(privateKey, peerPublic)
        require(out.any { it != 0.toByte() }) { "weak key" }
        return out
    }

    fun scalarMult(scalar: ByteArray, u: ByteArray): ByteArray {
        require(scalar.size == 32 && u.size == 32)
        val k = scalar.copyOf().also {
            it[0] = (it[0].toInt() and 248).toByte()
            it[31] = (it[31].toInt() and 127).toByte()
            it[31] = (it[31].toInt() or 64).toByte()
        }
        val kn = le(k)
        val x1 = le(u.copyOf().also { it[31] = (it[31].toInt() and 127).toByte() }).mod(P)
        var x2 = BigInteger.ONE
        var z2 = BigInteger.ZERO
        var x3 = x1
        var z3 = BigInteger.ONE
        var swap = 0
        for (t in 254 downTo 0) {
            val kt = if (kn.testBit(t)) 1 else 0
            swap = swap xor kt
            if (swap == 1) { val a = x2; x2 = x3; x3 = a; val b = z2; z2 = z3; z3 = b }
            swap = kt
            val a = x2.add(z2).mod(P)
            val aa = a.multiply(a).mod(P)
            val b = x2.subtract(z2).mod(P)
            val bb = b.multiply(b).mod(P)
            val e = aa.subtract(bb).mod(P)
            val c = x3.add(z3).mod(P)
            val d = x3.subtract(z3).mod(P)
            val da = d.multiply(a).mod(P)
            val cb = c.multiply(b).mod(P)
            x3 = da.add(cb).pow(2).mod(P)
            z3 = x1.multiply(da.subtract(cb).pow(2)).mod(P)
            x2 = aa.multiply(bb).mod(P)
            z2 = e.multiply(aa.add(A24.multiply(e))).mod(P)
        }
        if (swap == 1) { x2 = x3; z2 = z3 }
        return toLe(x2.multiply(z2.modPow(P.subtract(BigInteger.TWO), P)).mod(P))
    }

    private fun le(b: ByteArray): BigInteger = BigInteger(1, b.reversedArray())

    private fun toLe(n: BigInteger): ByteArray {
        val be = n.toByteArray().let { if (it.size > 32) it.copyOfRange(it.size - 32, it.size) else it }
        val out = ByteArray(32)
        for (i in be.indices) out[i] = be[be.size - 1 - i]
        return out
    }
}

/** Пара ключей: закрытый остаётся на телефоне, открытый уходит собеседнику. */
class ChatKeyPair(val privateKey: ByteArray, val publicKey: ByteArray) {
    companion object {
        fun generate(): ChatKeyPair = X25519.newPrivateKey().let { ChatKeyPair(it, X25519.publicKey(it)) }
        fun of(privateKey: ByteArray) = ChatKeyPair(privateKey, X25519.publicKey(privateKey))
    }
}

/**
 * Сквозное шифрование переписки.
 *
 *  ключ сессии = HKDF-SHA256(X25519(мой закрытый, его открытый),
 *                            salt = «memory-e2e-v1», info = открытые ключи по порядку)
 *  сообщение   = AES-256-GCM, конверт = base64(nonce 12 байт | шифртекст | тег 16 байт)
 *
 * Тот же формат даёт CryptoKit (HKDF<SHA256>, AES.GCM.SealedBox.combined).
 * Сервер видит только конверты — прочитать их могут лишь два телефона.
 * «Код безопасности» — 60 цифр из обоих открытых ключей: совпал у обоих —
 * между вами никто не встал.
 */
object ChatCrypto {
    private const val SALT = "memory-e2e-v1"
    private val random = SecureRandom()

    private fun ordered(a: ByteArray, b: ByteArray): ByteArray {
        val first = if (compare(a, b) <= 0) a else b
        val second = if (first === a) b else a
        return first + second
    }

    private fun compare(a: ByteArray, b: ByteArray): Int {
        for (i in a.indices) {
            val d = (a[i].toInt() and 0xff) - (b[i].toInt() and 0xff)
            if (d != 0) return d
        }
        return 0
    }

    /** HKDF-SHA256 (RFC 5869). */
    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int = 32): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val out = java.io.ByteArrayOutputStream()
        var t = ByteArray(0)
        var i = 1
        while (out.size() < length) {
            mac.update(t)
            mac.update(info)
            mac.update(i.toByte())
            t = mac.doFinal()
            out.write(t)
            i++
        }
        return out.toByteArray().copyOf(length)
    }

    fun sessionKey(mine: ChatKeyPair, peerPublic: ByteArray): ByteArray =
        hkdf(X25519.sharedSecret(mine.privateKey, peerPublic), SALT.toByteArray(), ordered(mine.publicKey, peerPublic))

    /** Зашифровать текст: конверт для сервера. aad — к чему привязан (переписка), шифруется вместе с проверкой. */
    fun seal(key: ByteArray, plaintext: String, aad: String = ""): String {
        val nonce = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        if (aad.isNotEmpty()) cipher.updateAAD(aad.toByteArray())
        return Base64.getEncoder().encodeToString(nonce + cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)))
    }

    /** Расшифровать конверт; null — подделан, чужой ключ или испорчен. */
    fun open(key: ByteArray, envelope: String, aad: String = ""): String? = runCatching {
        val raw = Base64.getDecoder().decode(envelope)
        if (raw.size < 12 + 16) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        if (aad.isNotEmpty()) cipher.updateAAD(aad.toByteArray())
        String(cipher.doFinal(raw.copyOfRange(12, raw.size)), Charsets.UTF_8)
    }.getOrNull()

    /** Код безопасности: 12 групп по 5 цифр, одинаковый у обоих собеседников. */
    fun safetyNumber(a: ByteArray, b: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-512").digest(SALT.toByteArray() + ordered(a, b))
        return (0 until 12).joinToString(" ") { i ->
            var v = 0L
            for (j in 0 until 5) v = (v shl 8) or (digest[i * 5 + j].toLong() and 0xff)
            (v % 100_000).toString().padStart(5, '0')
        }
    }

    /**
     * Ключи демо-собеседника: пока сервера нет, «его телефон» живёт здесь же.
     * Детерминированы от номера, чтобы код безопасности не менялся.
     */
    fun demoPeer(peerId: Int): ChatKeyPair =
        ChatKeyPair.of(MessageDigest.getInstance("SHA-256").digest("memory-demo-peer-$peerId".toByteArray()))
}

/**
 * Свой ключ шифрования переписки — на телефоне, в зашифрованном хранилище
 * (SecurePrefs: ключи с префиксом «session.» шифруются ключом Android
 * Keystore). Один ключ на аккаунт; новый телефон — новый ключ.
 */
class ChatKeys(private val store: KeyValueStore) {
    fun identity(login: String): ChatKeyPair {
        val key = "session.e2e.${login.lowercase()}"
        store.get(key)?.takeIf { it.length == 64 }?.let { hex ->
            return ChatKeyPair.of(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        }
        val pair = ChatKeyPair.generate()
        store.put(key, pair.privateKey.joinToString("") { "%02x".format(it) })
        return pair
    }

    fun forget(login: String) {
        store.remove("session.e2e.${login.lowercase()}")
    }
}

/** Сколько живут исчезающие сообщения. 0 — не исчезают. */
enum class Disappear(val ms: Long, private val ru: String) {
    Off(0, "Выкл."),
    FiveMinutes(5 * 60_000L, "5 минут"),
    Hour(60 * 60_000L, "1 час"),
    Day(24 * 60 * 60_000L, "1 день"),
    Week(7 * 24 * 60 * 60_000L, "1 неделя"),
    ;

    val title: String get() = t(ru)

    companion object {
        fun of(ms: Long): Disappear = entries.firstOrNull { it.ms == ms } ?: Off
    }
}
