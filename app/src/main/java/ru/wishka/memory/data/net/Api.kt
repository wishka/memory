package ru.wishka.memory.data.net

import org.json.JSONArray
import org.json.JSONObject
import ru.wishka.memory.data.Message
import ru.wishka.memory.data.Post
import ru.wishka.memory.data.StateStore
import ru.wishka.memory.data.User

/**
 * Клиент API Memory (контракт — docs/openapi.yaml). Знает пути и формат,
 * но не то, как запрос летит: это Transport (OkHttp или демо-сервер).
 *
 * Записи и сообщения в JSON — тем же кодеком, что сохранение на телефоне
 * (StateStore.Codec): один формат и на диске, и в сети.
 *
 * Все изменения несут Idempotency-Key: если ответ потерялся и очередь
 * повторит запрос, сервер вернёт тот же результат, а не создаст дубль.
 */
class Api(
    private val transport: Transport,
    private val me: () -> User,
    private val token: () -> String? = { null },
) {
    private fun codec() = StateStore.Codec(me())

    /** Запрос с токеном; не 2xx — NetError, нет сети — NetError(Offline). */
    suspend fun call(request: HttpRequest): HttpResponse {
        val headers = request.headers.toMutableMap()
        token()?.let { headers["Authorization"] = "Bearer $it" }
        headers.putIfAbsent("Accept", "application/json")
        val response = try {
            transport.send(request.copy(headers = headers))
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw NetError.of(e)
        }
        if (!response.ok) throw NetError.of(response)
        return response
    }

    private suspend fun getJson(path: String, query: Map<String, String> = emptyMap()): JSONObject =
        parse(call(HttpRequest("GET", path, query)))

    private suspend fun sendJson(method: String, path: String, body: JSONObject, key: String?): JSONObject {
        val headers = if (key != null) mapOf("Idempotency-Key" to key) else emptyMap()
        val r = call(HttpRequest(method, path, headers = headers, body = body.toString().toByteArray(Charsets.UTF_8), contentType = JSON))
        return if (r.body.isEmpty()) JSONObject() else parse(r)
    }

    private fun parse(r: HttpResponse): JSONObject =
        runCatching { JSONObject(r.text) }.getOrElse { throw NetError(NetError.Kind.BadData, r.code, "Не JSON") }

    private fun query(cursor: String?, limit: Int): Map<String, String> =
        buildMap { put("limit", limit.toString()); if (cursor != null) put("cursor", cursor) }

    private fun posts(o: JSONObject): Page<Post> {
        val c = codec()
        val arr = o.optJSONArray("items") ?: JSONArray()
        val items = (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { runCatching { c.postOf(it) }.getOrNull() } }
        return Page(items, o.optString("next").ifEmpty { null }.takeUnless { o.isNull("next") })
    }

    // ── Записи ───────────────────────────────────────────────────────

    /** Лента: новые сверху, по limit штук; cursor — из прошлой страницы. */
    suspend fun feed(cursor: String?, limit: Int = PAGE): Page<Post> = posts(getJson("/v1/feed", query(cursor, limit)))

    suspend fun userPosts(userId: Int, cursor: String?, limit: Int = PAGE): Page<Post> =
        posts(getJson("/v1/users/$userId/posts", query(cursor, limit)))

    /**
     * Опубликовать. media — уже загруженные файлы: путь вложения заменён
     * адресом загрузки (Upload-Location tus). Ответ — запись с номером сервера.
     */
    suspend fun createPost(post: Post, key: String): Post {
        val c = codec()
        val o = sendJson("POST", "/v1/posts", c.postJson(post), key)
        return c.postOf(o)
    }

    suspend fun like(postId: Long, liked: Boolean, key: String) {
        sendJson(if (liked) "PUT" else "DELETE", "/v1/posts/$postId/like", JSONObject(), key)
    }

    // ── Переписка ────────────────────────────────────────────────────

    /** История переписки: от новых к старым, before — курсор. */
    suspend fun messages(chatId: Int, cursor: String?, limit: Int = PAGE): Page<Message> {
        val o = getJson("/v1/chats/$chatId/messages", query(cursor, limit))
        val c = codec()
        val arr = o.optJSONArray("items") ?: JSONArray()
        val items = (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { runCatching { c.messageOf(it) }.getOrNull() } }
        return Page(items, o.optString("next").ifEmpty { null }.takeUnless { o.isNull("next") })
    }

    suspend fun sendMessage(chatId: Int, message: Message, key: String): Message {
        val c = codec()
        return c.messageOf(sendJson("POST", "/v1/chats/$chatId/messages", c.messageJson(message), key))
    }

    companion object {
        const val PAGE = 20
        const val JSON = "application/json; charset=utf-8"
    }
}
