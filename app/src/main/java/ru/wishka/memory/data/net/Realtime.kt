package ru.wishka.memory.data.net

import org.json.JSONObject

/**
 * Событие живого канала (WebSocket /v1/realtime, docs/REALTIME.md): новое
 * сообщение, «печатает», «прочитано», состав группы. Один JSON на кадр:
 * {"type": "...", "chat": 12, ...}.
 */
sealed class RtEvent {
    abstract val chatId: Int

    /** Новое сообщение; message — JSON в формате API (StateStore.Codec). */
    data class NewMessage(override val chatId: Int, val message: JSONObject) : RtEvent()
    data class Typing(override val chatId: Int, val userId: Int, val typing: Boolean) : RtEvent()
    /** userId прочитал всё до upTo (номер сообщения) включительно. */
    data class Read(override val chatId: Int, val userId: Int, val upTo: Long) : RtEvent()
    /** Сменился состав или название группы — перечитать её. */
    data class GroupChanged(override val chatId: Int, val title: String, val memberIds: List<Int>, val adminIds: List<Int>) : RtEvent()

    fun json(): JSONObject = when (this) {
        is NewMessage -> JSONObject().put("type", "message").put("chat", chatId).put("message", message)
        is Typing -> JSONObject().put("type", "typing").put("chat", chatId).put("user", userId).put("on", typing)
        is Read -> JSONObject().put("type", "read").put("chat", chatId).put("user", userId).put("upTo", upTo)
        is GroupChanged -> JSONObject().put("type", "group").put("chat", chatId).put("title", title)
            .put("members", org.json.JSONArray(memberIds)).put("admins", org.json.JSONArray(adminIds))
    }

    companion object {
        fun of(o: JSONObject): RtEvent? {
            val chat = o.optInt("chat")
            return when (o.optString("type")) {
                "message" -> o.optJSONObject("message")?.let { NewMessage(chat, it) }
                "typing" -> Typing(chat, o.optInt("user"), o.optBoolean("on"))
                "read" -> Read(chat, o.optInt("user"), o.optLong("upTo"))
                "group" -> {
                    fun ints(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optInt(it) } }.orEmpty()
                    GroupChanged(chat, o.optString("title"), ints("members"), ints("admins"))
                }
                else -> null
            }
        }

        fun parse(text: String): RtEvent? = runCatching { of(JSONObject(text)) }.getOrNull()
    }
}

/**
 * Живой канал: сервер шлёт события сам, без опроса. Клиент шлёт только
 * «печатает» и «прочитано». Обрыв — переподключение с паузой (Backoff).
 */
interface Realtime {
    val connected: Boolean
    fun connect(onEvent: (RtEvent) -> Unit)
    fun send(event: RtEvent)
    fun close()
}

/**
 * Живой канал демо-сервера: события, которые «пришли бы» с сервера,
 * копятся с временем и выдаются tick(now) — в приложении раз в секунду,
 * в тестах — когда нужно. Так демо-друзья читают и печатают.
 */
class DemoRealtime(private val clock: () -> Long) : Realtime {
    private val queue = mutableListOf<Pair<Long, RtEvent>>()
    private var listener: ((RtEvent) -> Unit)? = null
    /** Что клиент отправил (для тестов). */
    val sent = mutableListOf<RtEvent>()

    override val connected: Boolean get() = listener != null

    override fun connect(onEvent: (RtEvent) -> Unit) {
        listener = onEvent
    }

    override fun send(event: RtEvent) {
        synchronized(sent) { sent += event }
    }

    override fun close() {
        listener = null
    }

    /** Сервер «пришлёт» событие через afterMs. */
    fun schedule(event: RtEvent, afterMs: Long) = synchronized(queue) { queue += (clock() + afterMs) to event }

    /** Выдать созревшие события. Ответ — сколько выдали. */
    fun tick(now: Long = clock()): Int {
        val due = synchronized(queue) {
            val d = queue.filter { it.first <= now }.sortedBy { it.first }
            queue.removeAll(d)
            d
        }
        val l = listener ?: return 0
        due.forEach { l(it.second) }
        return due.size
    }

    val pending: Int get() = synchronized(queue) { queue.size }
}
