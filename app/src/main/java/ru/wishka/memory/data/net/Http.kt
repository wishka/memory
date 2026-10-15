package ru.wishka.memory.data.net

import java.io.IOException

/**
 * Запрос к серверу Memory. path — от корня API («/v1/feed»), query —
 * параметры строки запроса. Тело — байты: JSON или кусок файла (tus).
 */
data class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val contentType: String? = null,
) {
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/** Ответ сервера: код, заголовки, тело. */
class HttpResponse(val code: Int, val headers: Map<String, String> = emptyMap(), val body: ByteArray = ByteArray(0)) {
    val ok: Boolean get() = code in 200..299
    val text: String get() = String(body, Charsets.UTF_8)
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

/**
 * Как запрос доходит до сервера. В приложении — OkHttp (OkHttpTransport)
 * или встроенный демо-сервер (DemoServer), в тестах — тоже демо-сервер.
 * Нет сети — IOException.
 */
interface Transport {
    suspend fun send(request: HttpRequest): HttpResponse
}

/** Что пошло не так при обращении к серверу. */
class NetError(val kind: Kind, val code: Int = 0, message: String = "", val retryAfterMs: Long = 0) : Exception(message.ifEmpty { kind.name }) {
    enum class Kind { Offline, Timeout, Server, Client, Auth, Conflict, BadData }

    /** Можно ли повторить позже: сеть, таймаут, сбой сервера, «слишком часто». */
    val retryable: Boolean
        get() = kind == Kind.Offline || kind == Kind.Timeout || kind == Kind.Server || code == 429 || code == 408

    companion object {
        fun of(response: HttpResponse): NetError {
            val kind = when (response.code) {
                401, 403 -> Kind.Auth
                409 -> Kind.Conflict
                408 -> Kind.Timeout
                in 500..599 -> Kind.Server
                else -> Kind.Client
            }
            val retryAfter = response.header("Retry-After")?.toLongOrNull()?.times(1000) ?: 0
            return NetError(kind, response.code, response.text.take(200), retryAfter)
        }

        fun of(e: Throwable): NetError = when (e) {
            is NetError -> e
            is java.net.SocketTimeoutException -> NetError(Kind.Timeout, message = e.message.orEmpty())
            is IOException -> NetError(Kind.Offline, message = e.message.orEmpty())
            else -> NetError(Kind.BadData, message = e.message.orEmpty())
        }
    }
}

/**
 * Когда повторять: 1, 2, 4, 8… с, но не реже раза в 5 минут; плюс
 * случайный разброс до 20 %, чтобы тысячи телефонов после сбоя не
 * постучались в одну и ту же секунду. Retry-After сервера — главнее.
 */
object Backoff {
    const val BASE_MS = 1_000L
    const val MAX_MS = 5 * 60_000L

    fun delayMs(attempt: Int, jitter: Double = 0.0, retryAfterMs: Long = 0): Long {
        if (retryAfterMs > 0) return retryAfterMs.coerceAtMost(MAX_MS)
        val exp = BASE_MS shl attempt.coerceIn(0, 20)
        val capped = exp.coerceAtMost(MAX_MS)
        return (capped * (1.0 + 0.2 * jitter.coerceIn(0.0, 1.0))).toLong().coerceAtMost(MAX_MS)
    }
}

/** Страница списка: элементы и курсор следующей; next == null — дальше ничего. */
data class Page<T>(val items: List<T>, val next: String?)
