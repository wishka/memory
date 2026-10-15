package ru.wishka.memory.data

import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf

/** Что сейчас со звонком. */
enum class CallPhase {
    /** Я звоню — у собеседника звонит. */
    Calling,
    /** Звонят мне — принять или отклонить. */
    Incoming,
    /** Разговор идёт. */
    Active,
}

/**
 * Звонок в истории и в переписке. missed — не ответили (мне — пропущенный,
 * я звонил — «не ответил»), declined — отклонили.
 */
data class CallRecord(
    val id: Long,
    val peerId: Int,
    val video: Boolean,
    val outgoing: Boolean,
    val at: Long,
    val durationMs: Long = 0,
    val missed: Boolean = false,
    val declined: Boolean = false,
)

/** Звонок, который идёт сейчас: этап, когда соединились, кнопки. */
data class ActiveCall(
    val id: Long,
    val peerId: Int,
    val video: Boolean,
    val outgoing: Boolean,
    val phase: CallPhase,
    val startedAt: Long,
    val connectedAt: Long = 0,
    val muted: Boolean = false,
    val cameraOn: Boolean = true,
    val frontCamera: Boolean = true,
    val speaker: Boolean = false,
) {
    val room: String get() = "call-$id"

    fun durationMs(now: Long): Long = if (connectedAt > 0) (now - connectedAt).coerceAtLeast(0) else 0
}

/**
 * Связь звонка. Демо — ничего не передаёт. С сервером — LiveKit, как у
 * эфиров: комната call-<id> на двоих, токен выдаёт сервер Memory
 * (POST /calls/token), входящий звонок приходит пушем (VoIP-пуш на iPhone,
 * high-priority FCM на Android). См. docs/LIVE.md.
 */
interface CallTransport {
    fun connect(room: String, token: String, video: Boolean)
    fun setMuted(muted: Boolean)
    fun setCamera(on: Boolean, front: Boolean)
    fun hangUp()
    val connected: Boolean
}

object DemoCallTransport : CallTransport {
    override fun connect(room: String, token: String, video: Boolean) {}
    override fun setMuted(muted: Boolean) {}
    override fun setCamera(on: Boolean, front: Boolean) {}
    override fun hangUp() {}
    override val connected: Boolean = false
}

object Calls {
    /** Сколько звонит, пока не станет «нет ответа». */
    const val RING_MS = 30_000L
    /** Сколько звонков хранить в истории. */
    const val LOG_LIMIT = 100

    /** «Видеозвонок · 3:12», «Пропущенный звонок», «Отклонён», «Нет ответа». */
    fun title(r: CallRecord): String {
        val kind = if (r.video) t("Видеозвонок") else t("Звонок")
        return when {
            r.declined -> tf("%s отклонён", kind)
            r.missed && !r.outgoing -> if (r.video) t("Пропущенный видеозвонок") else t("Пропущенный звонок")
            r.missed -> tf("%s — нет ответа", kind)
            else -> kind + " · " + Lives.clock(r.durationMs)
        }
    }

    /** Звонок закончился — запись для истории. */
    fun record(c: ActiveCall, now: Long, declined: Boolean = false): CallRecord =
        CallRecord(c.id, c.peerId, c.video, c.outgoing, c.startedAt, c.durationMs(now), missed = c.connectedAt == 0L && !declined, declined = declined)

    /** Может ли человек позвонить мне при этих настройках. */
    fun allowed(from: Int, friends: Set<Int>, blocked: Set<Int>, who: Audience): Boolean = from !in blocked && when (who) {
        Audience.Everyone -> true
        Audience.Following -> from in friends
        Audience.Nobody -> false
    }

    fun add(log: List<CallRecord>, r: CallRecord): List<CallRecord> = (listOf(r) + log.filter { it.id != r.id }).take(LOG_LIMIT)
}
