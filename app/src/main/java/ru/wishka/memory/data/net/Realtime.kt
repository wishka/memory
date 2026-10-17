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
    /** Подписка: state — "request" (заявка ко мне), "follow" (подписались), "accepted" (мою заявку приняли). */
    data class Follow(val userId: Int, val state: String) : RtEvent() {
        override val chatId: Int get() = 0
    }
    /** Комментарий к моей записи. */
    data class NewComment(val postId: Long, val comment: JSONObject) : RtEvent() {
        override val chatId: Int get() = 0
    }

    /**
     * Звонок: state — "ringing" (звонят мне), "accepted", "declined",
     * "cancelled" (не дождались), "ended". user — кто (для справочника).
     */
    data class Call(val id: Long, val state: String, val from: Int, val video: Boolean, val user: JSONObject? = null) : RtEvent() {
        override val chatId: Int get() = 0
    }
    /** Эфир: state — "started" (live — сам эфир), "ended", "counts" (зрители и сердечки). */
    data class Live(val id: Long, val state: String, val live: JSONObject? = null, val viewers: Int = 0, val hearts: Int = 0) : RtEvent() {
        override val chatId: Int get() = 0
    }
    /** Комментарий в эфире. */
    data class LiveComment(val liveId: Long, val comment: JSONObject) : RtEvent() {
        override val chatId: Int get() = 0
    }

    fun json(): JSONObject = when (this) {
        is NewMessage -> JSONObject().put("type", "message").put("chat", chatId).put("message", message)
        is Typing -> JSONObject().put("type", "typing").put("chat", chatId).put("user", userId).put("on", typing)
        is Read -> JSONObject().put("type", "read").put("chat", chatId).put("user", userId).put("upTo", upTo)
        is GroupChanged -> JSONObject().put("type", "group").put("chat", chatId).put("title", title)
            .put("members", org.json.JSONArray(memberIds)).put("admins", org.json.JSONArray(adminIds))
        is Follow -> JSONObject().put("type", "follow").put("user", userId).put("state", state)
        is NewComment -> JSONObject().put("type", "comment").put("post", postId).put("comment", comment)
        is Call -> JSONObject().put("type", "call").put("id", id).put("state", state).put("from", from).put("video", video).put("user", user ?: JSONObject.NULL)
        is Live -> JSONObject().put("type", "live").put("id", id).put("state", state).put("live", live ?: JSONObject.NULL).put("viewers", viewers).put("hearts", hearts)
        is LiveComment -> JSONObject().put("type", "live_comment").put("id", liveId).put("comment", comment)
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
                "follow" -> Follow(o.optInt("user"), o.optString("state"))
                "comment" -> o.optJSONObject("comment")?.let { NewComment(o.optLong("post"), it) }
                "call" -> Call(o.optLong("id"), o.optString("state"), o.optInt("from"), o.optBoolean("video"), o.optJSONObject("user"))
                "live" -> Live(o.optLong("id"), o.optString("state"), o.optJSONObject("live"), o.optInt("viewers"), o.optInt("hearts"))
                "live_comment" -> o.optJSONObject("comment")?.let { LiveComment(o.optLong("id"), it) }
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
    /** Подключиться заново сейчас (после входа — с новым токеном). */
    fun reconnect() {}
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
