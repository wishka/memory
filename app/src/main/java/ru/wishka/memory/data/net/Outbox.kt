package ru.wishka.memory.data.net

import org.json.JSONObject

enum class OpKind(val id: String) {
    CreatePost("post"),
    Like("like"),
    Unlike("unlike"),
    SendMessage("message"),
    /** История: payload — Story в JSON, вложение — после загрузки. */
    CreateStory("story"),
    /** Любой запрос: группы, «прочитано», профиль. payload — {method, path, body, coalesce}. */
    Call("call");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id }
    }
}

/**
 * Действие, которое ждёт отправки: запись, «нравится», сообщение.
 * key — Idempotency-Key: тот же при каждом повторе, поэтому сервер не
 * создаст дубль, даже если первый раз ответ потерялся в пути.
 * target — номер записи (для «нравится») или переписки (для сообщения).
 * payload — что отправить (JSON записи или сообщения в формате API).
 */
data class OutboxOp(
    val key: String,
    val kind: OpKind,
    val target: Long = 0,
    val payload: String = "",
    val createdAt: Long = 0,
    val attempts: Int = 0,
    val nextAt: Long = 0,
    val error: String = "",
) {
    fun json(): JSONObject = JSONObject()
        .put("key", key).put("kind", kind.id).put("target", target).put("payload", payload)
        .put("createdAt", createdAt).put("attempts", attempts).put("nextAt", nextAt).put("error", error)

    companion object {
        fun of(o: JSONObject): OutboxOp? {
            val kind = OpKind.of(o.optString("kind")) ?: return null
            return OutboxOp(
                o.getString("key"), kind, o.optLong("target"), o.optString("payload"),
                o.optLong("createdAt"), o.optInt("attempts"), o.optLong("nextAt"), o.optString("error"),
            )
        }
    }
}

/** Итог прохода по очереди. */
data class FlushResult(val sent: Int, val waiting: Int, val failed: Int, val offline: Boolean, val authLost: Boolean = false)

/**
 * Очередь отправки: что сделали без сети (или пока сеть моргнула), уходит
 * по порядку, когда сервер доступен. Лежит в кэше — переживает перезапуск.
 *
 * Порядок важен для переписки: следующее сообщение в той же переписке не
 * обгоняет предыдущее. «Нравится» к одной записи схлопываются — уходит
 * только последнее нажатие.
 */
class Outbox(private val cache: OfflineCache, private val clock: () -> Long) {
    private fun cacheKey(key: String) = CacheKeys.OP + key

    fun all(): List<OutboxOp> = cache.keys(CacheKeys.OP).mapNotNull { k ->
        cache.get(k)?.let { runCatching { OutboxOp.of(JSONObject(it.value)) }.getOrNull() }
    }

    val count: Int get() = cache.keys(CacheKeys.OP).size

    fun get(key: String): OutboxOp? = cache.get(cacheKey(key))?.let { runCatching { OutboxOp.of(JSONObject(it.value)) }.getOrNull() }

    fun enqueue(op: OutboxOp) {
        if (op.kind == OpKind.Like || op.kind == OpKind.Unlike) {
            all().filter { (it.kind == OpKind.Like || it.kind == OpKind.Unlike) && it.target == op.target }.forEach { remove(it.key) }
        }
        // Профиль, «прочитано»: важно только последнее — прежнее такое же снимаем
        coalesceOf(op)?.let { c -> all().filter { it.kind == OpKind.Call && coalesceOf(it) == c }.forEach { remove(it.key) } }
        save(op.copy(createdAt = if (op.createdAt == 0L) clock() else op.createdAt))
    }

    fun remove(key: String) = cache.remove(cacheKey(key))

    private fun coalesceOf(op: OutboxOp): String? =
        if (op.kind != OpKind.Call) null else runCatching { JSONObject(op.payload).optString("coalesce").ifEmpty { null } }.getOrNull()

    /** Снять паузу перед повтором (сеть вернулась). */
    fun retryNow(key: String) {
        get(key)?.let { save(it.copy(nextAt = 0, attempts = 0)) }
    }

    private fun save(op: OutboxOp) = cache.put(cacheKey(op.key), op.json().toString(), clock())

    /**
     * Отправить, что готово. send — как отправить одно действие (знает Net):
     * ответ сервера или NetError. ready — готово ли (вложения загружены).
     * Нет сети — проход останавливается: остальным тоже не дойти.
     */
    suspend fun flush(
        ready: (OutboxOp) -> Boolean,
        failedDeps: (OutboxOp) -> Boolean,
        send: suspend (OutboxOp) -> JSONObject?,
        onSent: (OutboxOp, JSONObject?) -> Unit,
        onFailed: (OutboxOp, NetError) -> Unit,
    ): FlushResult {
        val now = clock()
        var sent = 0
        var waiting = 0
        var failed = 0
        val blockedChats = HashSet<Long>()
        for (op in all()) {
            val chatOrdered = op.kind == OpKind.SendMessage
            if (chatOrdered && op.target in blockedChats) { waiting++; continue }
            if (failedDeps(op)) {
                remove(op.key)
                failed++
                onFailed(op, NetError(NetError.Kind.Client, message = "upload"))
                continue
            }
            if (op.nextAt > now || !ready(op)) {
                waiting++
                if (chatOrdered) blockedChats += op.target
                continue
            }
            try {
                val answer = send(op)
                remove(op.key)
                sent++
                onSent(op, answer)
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val err = NetError.of(e)
                if (err.kind == NetError.Kind.Auth) {
                    // Вход закрыт — ничего не теряем: ждём, пока войдут снова
                    save(op.copy(attempts = op.attempts + 1, nextAt = now + Backoff.MAX_MS, error = err.kind.name))
                    return FlushResult(sent, count, failed, offline = false, authLost = true)
                }
                if (err.retryable) {
                    save(op.copy(attempts = op.attempts + 1, nextAt = now + Backoff.delayMs(op.attempts, retryAfterMs = err.retryAfterMs), error = err.kind.name))
                    waiting++
                    if (chatOrdered) blockedChats += op.target
                    if (err.kind == NetError.Kind.Offline) {
                        return FlushResult(sent, count, failed, offline = true)
                    }
                } else {
                    remove(op.key)
                    failed++
                    onFailed(op, err)
                }
            }
        }
        return FlushResult(sent, waiting, failed, offline = false)
    }
}
