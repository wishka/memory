package ru.wishka.memory.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Люди с сервера: кого встретили в ленте, в переписках, в поиске. Демо-люди
 * (Demo.people) — отдельно; Demo.person ищет и здесь.
 *
 * Номера на сервере — с 100 000, так что с демо-людьми (2–6) не путаются.
 * Свой номер на сервере (selfId) телефон знает после входа: всё, что пришло
 * с ним, — «я» (Demo.me.id), а на сервер «я» уходит его номером.
 *
 * Сохраняется вместе с состоянием (StateStore: "people"), чтобы после
 * перезапуска авторы записей не превратились в «Неизвестного».
 */
object Directory {
    /** Свой номер на сервере; 0 — не вошли (демо). */
    @Volatile var selfId: Int = 0

    private val people = ConcurrentHashMap<Int, User>()
    /** Открытые ключи X25519 (сквозное шифрование) — base64. */
    private val keys = ConcurrentHashMap<Int, String>()

    /** Сколько раз менялся справочник — экранам, чтобы перерисоваться. */
    @Volatile var version: Int = 0
        private set

    fun get(id: Int): User? = people[id]

    fun all(): List<User> = people.values.sortedBy { it.name.lowercase() }

    /** Открытый ключ человека; null — ещё не прислал (секретная переписка недоступна). */
    fun key(id: Int): ByteArray? = keys[id]?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }?.takeIf { it.size == 32 }

    /** Номер с сервера → номер в приложении: свой — это Demo.me.id. */
    fun local(id: Int): Int = if (selfId != 0 && id == selfId) Demo.me.id else id

    /** Номер в приложении → номер на сервере. */
    fun remote(id: Int): Int = if (selfId != 0 && id == Demo.me.id) selfId else id

    /** Человек с сервера: GET /v1/users/{id}, поиск, authorInfo в записи или сообщении. */
    fun remember(o: JSONObject?): User? {
        if (o == null) return null
        val id = o.optInt("id")
        if (id <= 0 || local(id) == Demo.me.id) return null
        val old = people[id]
        val user = User(
            id = id,
            name = o.optString("name").ifEmpty { old?.name ?: o.optString("handle") },
            handle = o.optString("handle").ifEmpty { o.optString("login").ifEmpty { old?.handle.orEmpty() } },
            bio = if (o.has("bio")) o.optString("bio") else old?.bio.orEmpty(),
            hue = if (o.has("hue")) o.optDouble("hue", 0.5).toFloat() else old?.hue ?: 0.5f,
            city = if (o.has("city")) o.optString("city") else old?.city.orEmpty(),
        )
        val key = o.optString("publicKey")
        val changed = old != user || (key.isNotEmpty() && keys[id] != key)
        people[id] = user
        if (key.isNotEmpty()) keys[id] = key
        if (changed) version++
        return user
    }

    fun json(): JSONArray = JSONArray(people.values.map { u ->
        JSONObject().put("id", u.id).put("name", u.name).put("handle", u.handle).put("bio", u.bio)
            .put("hue", u.hue.toDouble()).put("city", u.city).put("publicKey", keys[u.id].orEmpty())
    })

    /** Справочник аккаунта (при открытии): прежний забывается. */
    fun load(arr: JSONArray?) {
        people.clear()
        keys.clear()
        if (arr != null) for (i in 0 until arr.length()) remember(arr.optJSONObject(i))
        version++
    }

    fun clear() = load(null)
}
