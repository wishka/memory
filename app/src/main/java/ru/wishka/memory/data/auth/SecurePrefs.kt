package ru.wishka.memory.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * SharedPreferences, где данные сессии зашифрованы ключом AES-GCM из
 * AndroidKeyStore. Ключ не покидает защищённое хранилище телефона, поэтому
 * скопированный файл настроек не даёт войти в чужой аккаунт.
 *
 * Шифруются ключи с префиксом «session.»; соль и хеш пароля и так не
 * раскрывают сам пароль.
 */
class SecurePrefs(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("memory_auth", Context.MODE_PRIVATE)

    override fun get(key: String): String? {
        val raw = prefs.getString(key, null) ?: return null
        if (!key.startsWith(SECURE_PREFIX)) return raw
        // Не расшифровалось (ключ хранилища сброшен, например после
        // восстановления из резервной копии) — считаем, что сессии нет
        return runCatching { decrypt(raw) }.getOrNull()
    }

    override fun put(key: String, value: String) {
        val stored = if (key.startsWith(SECURE_PREFIX)) encrypt(value) else value
        prefs.edit().putString(key, stored).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val data = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val data = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, data.copyOfRange(0, IV_BYTES)))
        return String(cipher.doFinal(data.copyOfRange(IV_BYTES, data.size)), Charsets.UTF_8)
    }

    private companion object {
        const val SECURE_PREFIX = "session."
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "memory_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
