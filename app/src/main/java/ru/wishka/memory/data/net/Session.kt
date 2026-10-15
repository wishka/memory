package ru.wishka.memory.data.net

import org.json.JSONObject
import ru.wishka.memory.data.auth.KeyValueStore

/**
 * Вход на сервер: короткий токен доступа (15 минут) и долгий токен
 * обновления. Хранятся в зашифрованном хранилище телефона (SecurePrefs —
 * ключи «session.…» шифруются), у каждого аккаунта свои.
 *
 * Токен доступа истёк — Api сам обновит его одним запросом и повторит
 * исходный. Обновить не вышло (вышли с этого устройства на другом) —
 * сессия стирается, и приложение просит войти заново.
 */
class Session(private val store: KeyValueStore?, login: String) {
    private val prefix = "session.api.${login.lowercase()}."

    @Volatile var access: String? = store?.get(prefix + "access")
        private set
    @Volatile var refresh: String? = store?.get(prefix + "refresh")
        private set
    @Volatile var deviceId: String? = store?.get(prefix + "device")
        private set

    /** Свой номер на сервере (ответ входа: user.id); 0 — неизвестен (демо-сервер его не шлёт). */
    @Volatile var userId: Int = store?.get(prefix + "user")?.toIntOrNull() ?: 0
        private set

    val signedIn: Boolean get() = refresh != null

    fun save(o: JSONObject) {
        access = o.optString("access").ifEmpty { null }
        refresh = o.optString("refresh").ifEmpty { refresh }
        o.optString("deviceId").ifEmpty { null }?.let { deviceId = it }
        o.optJSONObject("user")?.optInt("id")?.takeIf { it > 0 }?.let { userId = it }
        store?.let { s ->
            access?.let { s.put(prefix + "access", it) }
            refresh?.let { s.put(prefix + "refresh", it) }
            deviceId?.let { s.put(prefix + "device", it) }
            if (userId > 0) s.put(prefix + "user", userId.toString())
        }
    }

    fun clear() {
        access = null
        refresh = null
        deviceId = null
        // Номер на сервере остаётся: он не меняется, а записи с ним уже на телефоне
        store?.let { s -> listOf("access", "refresh", "device").forEach { s.remove(prefix + it) } }
    }
}

/** Устройство, с которого вошли: «Pixel 8 · Android», «этот телефон». */
data class DeviceInfo(val id: String, val name: String, val platform: String, val lastSeen: Long, val current: Boolean)

/** Профиль для синхронизации между телефонами: что человек рассказал о себе и настройки приватности. */
data class ProfileSync(val json: JSONObject, val updatedAt: Long)
