package ru.wishka.memory.data

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Ключ одного файла секретной переписки: 32 байта ключа AES и 4 байта
 * начала нонса. Едет внутри сообщения, запечатанный ключом переписки
 * (ChatCrypto) — сервер видит только зашифрованный файл.
 */
data class FileKey(val key: ByteArray, val prefix: ByteArray) {
    fun encoded(): String = Base64.getEncoder().encodeToString(key + prefix)

    override fun equals(other: Any?) = other is FileKey && key.contentEquals(other.key) && prefix.contentEquals(other.prefix)
    override fun hashCode() = key.contentHashCode() * 31 + prefix.contentHashCode()

    companion object {
        fun random(rng: SecureRandom = SecureRandom()): FileKey =
            FileKey(ByteArray(32).also(rng::nextBytes), ByteArray(4).also(rng::nextBytes))

        fun decode(s: String): FileKey? = runCatching {
            val raw = Base64.getDecoder().decode(s)
            if (raw.size != 36) null else FileKey(raw.copyOfRange(0, 32), raw.copyOfRange(32, 36))
        }.getOrNull()
    }
}

/**
 * Шифрование файлов секретной переписки — формат MEMF1, одинаковый на
 * Android и iPhone (CryptoKit AES.GCM):
 *
 *     "MEMF" | версия 1 (1 байт) | размер куска (4 байта, big-endian)
 *     затем куски: шифротекст + тег 16 байт
 *
 * Каждый кусок — AES-256-GCM, нонс = prefix (4 байта) + номер куска (8 байт,
 * big-endian), AAD = заголовок (9 байт) + флаг «последний» (1 байт). Номер
 * в нонсе не даёт переставить куски, флаг — незаметно отрезать хвост.
 * Кусками — чтобы большое видео не держать в памяти целиком.
 */
object FileCrypto {
    const val CHUNK = 64 * 1024
    private const val TAG = 16
    private val MAGIC = "MEMF".toByteArray(Charsets.US_ASCII)

    private fun header(chunk: Int): ByteArray = ByteBuffer.allocate(9).put(MAGIC).put(1).putInt(chunk).array()

    private fun nonce(prefix: ByteArray, index: Long): ByteArray = ByteBuffer.allocate(12).put(prefix).putLong(index).array()

    private fun cipher(mode: Int, key: FileKey, index: Long, aad: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key.key, "AES"), GCMParameterSpec(TAG * 8, nonce(key.prefix, index)))
            updateAAD(aad)
        }

    /** Читать ровно n байт (или сколько осталось до конца). */
    private fun readFully(input: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var got = 0
        while (got < n) {
            val r = input.read(buf, got, n - got)
            if (r < 0) break
            got += r
        }
        return if (got == n) buf else buf.copyOf(got)
    }

    fun encrypt(input: InputStream, output: OutputStream, key: FileKey, chunk: Int = CHUNK) {
        val head = header(chunk)
        output.write(head)
        var index = 0L
        var current = readFully(input, chunk)
        while (true) {
            val next = if (current.size == chunk) readFully(input, chunk) else ByteArray(0)
            val last = next.isEmpty()
            output.write(cipher(Cipher.ENCRYPT_MODE, key, index, head + byteArrayOf(if (last) 1 else 0)).doFinal(current))
            if (last) break
            current = next
            index++
        }
    }

    /** Расшифровать; подделка, перестановка или обрезка — исключение. */
    fun decrypt(input: InputStream, output: OutputStream, key: FileKey) {
        val head = readFully(input, 9)
        require(head.size == 9 && head.copyOfRange(0, 4).contentEquals(MAGIC) && head[4] == 1.toByte()) { "Не MEMF1" }
        val chunk = ByteBuffer.wrap(head, 5, 4).int
        require(chunk in 1..(16 shl 20)) { "Странный размер куска" }
        var index = 0L
        var current = readFully(input, chunk + TAG)
        while (true) {
            val next = if (current.size == chunk + TAG) readFully(input, chunk + TAG) else ByteArray(0)
            val last = next.isEmpty()
            output.write(cipher(Cipher.DECRYPT_MODE, key, index, head + byteArrayOf(if (last) 1 else 0)).doFinal(current))
            if (last) break
            current = next
            index++
        }
    }

    fun encrypt(bytes: ByteArray, key: FileKey, chunk: Int = CHUNK): ByteArray =
        java.io.ByteArrayOutputStream().also { encrypt(bytes.inputStream(), it, key, chunk) }.toByteArray()

    fun decrypt(bytes: ByteArray, key: FileKey): ByteArray =
        java.io.ByteArrayOutputStream().also { decrypt(bytes.inputStream(), it, key) }.toByteArray()

    fun encryptFile(src: File, dst: File, key: FileKey) {
        src.inputStream().buffered().use { i -> dst.outputStream().buffered().use { o -> encrypt(i, o, key) } }
    }

    fun decryptFile(src: File, dst: File, key: FileKey) {
        src.inputStream().buffered().use { i -> dst.outputStream().buffered().use { o -> decrypt(i, o, key) } }
    }

    /** Ключ файла в имени вложения на проводе: «ek:» + конверт ChatCrypto. */
    const val SEALED = "ek:"
}
