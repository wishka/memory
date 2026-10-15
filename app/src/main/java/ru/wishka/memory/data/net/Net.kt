package ru.wishka.memory.data.net

import org.json.JSONObject
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.MediaKind
import ru.wishka.memory.data.Message
import ru.wishka.memory.data.Post
import ru.wishka.memory.data.StateStore
import ru.wishka.memory.data.User
import java.util.UUID

/** Что изменилось за один проход Net.pump — модели этого хватает, чтобы обновить экран. */
data class PumpResult(
    val uploads: List<UploadJob>,
    /** Отправленные действия и ответ сервера (запись или сообщение с номером сервера). */
    val sent: List<Pair<OutboxOp, JSONObject?>>,
    val failed: List<OutboxOp>,
    val offline: Boolean,
)

/**
 * Всё сетевое вместе: API, очередь отправки, загрузки, кэш. Одна штука на
 * вошедший аккаунт. Пока сервера нет — поверх DemoServer; с сервером —
 * поверх OkHttpTransport (адрес — API_URL в сборке).
 */
class Net(
    val transport: Transport,
    val cache: OfflineCache,
    private val clock: () -> Long,
    private val me: () -> User,
    token: () -> String? = { null },
    prepare: suspend (UploadJob) -> UploadJob = { it },
    source: (String) -> ByteSource? = { p -> java.io.File(p).takeIf { it.exists() }?.let(::FileSource) },
    chunkSize: Int = TusClient.DEFAULT_CHUNK,
) {
    val api = Api(transport, me, token)
    val tus = TusClient(api, chunkSize)
    val uploads = Uploads(cache, tus, clock, prepare, source)
    val outbox = Outbox(cache, clock)

    /** Встроенный демо-сервер, если работаем с ним (для «Данные и сеть»). */
    val demo: DemoServer? get() = transport as? DemoServer

    private fun codec() = StateStore.Codec(me())

    fun newKey(): String = UUID.randomUUID().toString()

    /** Лента страницами: старое — с сервера и из кэша. */
    fun feedPager(): Pager<Post> {
        val c = codec()
        return Pager(CacheKeys.FEED, cache, { it.id }, { c.postJson(it) }, { runCatching { c.postOf(it) }.getOrNull() }, clock) { cursor ->
            api.feed(cursor)
        }
    }

    // ── Постановка в очередь ─────────────────────────────────────────

    /** Фото и видео записи/сообщения — в загрузки (стикеры и гифки — как есть, они маленькие). */
    private fun queueMedia(owner: String, media: List<Attachment>) {
        media.filter { (it.kind == MediaKind.Photo || it.kind == MediaKind.Video) && !it.sticker && it.path.isNotEmpty() && !it.path.startsWith("/v1/") }
            .forEach { a ->
                uploads.add(UploadJob(newKey(), a.id, owner, a.path, video = a.kind == MediaKind.Video))
            }
    }

    /** Новая запись: вложения — в загрузки, сама запись — в очередь (уйдёт после них). */
    fun queuePost(post: Post): String {
        val key = post.outboxKey.ifEmpty { newKey() }
        queueMedia(key, post.media)
        outbox.enqueue(OutboxOp(key, OpKind.CreatePost, post.id, codec().postJson(post.copy(outboxKey = "")).toString()))
        return key
    }

    fun queueLike(postId: Long, liked: Boolean) {
        outbox.enqueue(OutboxOp(newKey(), if (liked) OpKind.Like else OpKind.Unlike, postId))
    }

    fun queueMessage(chatId: Int, message: Message): String {
        val key = newKey()
        queueMedia(key, message.attachments)
        outbox.enqueue(OutboxOp(key, OpKind.SendMessage, chatId.toLong(), codec().messageJson(message.copy(pending = false)).toString()))
        return key
    }

    /** Вложения с путями на сервере вместо путей на телефоне. */
    private fun withRemote(media: List<Attachment>, remote: Map<String, String>) =
        media.map { a -> remote[a.id]?.let { a.copy(path = it) } ?: a }

    private suspend fun send(op: OutboxOp): JSONObject? {
        val c = codec()
        val remote = uploads.doneFor(op.key).orEmpty()
        return when (op.kind) {
            OpKind.CreatePost -> {
                val post = c.postOf(JSONObject(op.payload))
                c.postJson(api.createPost(post.copy(media = withRemote(post.media, remote)), op.key))
            }
            OpKind.Like, OpKind.Unlike -> {
                api.like(op.target, op.kind == OpKind.Like, op.key)
                null
            }
            OpKind.SendMessage -> {
                val m = c.messageOf(JSONObject(op.payload))
                c.messageJson(api.sendMessage(op.target.toInt(), m.copy(attachments = withRemote(m.attachments, remote)), op.key))
            }
        }
    }

    /**
     * Один проход: догрузить, что можно (не больше maxUploads файлов за раз),
     * потом отправить готовое из очереди. Нет сети — вернётся offline.
     */
    suspend fun pump(maxUploads: Int = 3, onProgress: (UploadJob) -> Unit = {}): PumpResult {
        val touched = LinkedHashMap<String, UploadJob>()
        var offline = false
        for (i in 0 until maxUploads) {
            val j = uploads.runOnce { step -> touched[step.id] = step; onProgress(step) } ?: break
            touched[j.id] = j
            if (j.state == UploadState.Waiting && j.error == NetError.Kind.Offline.name) { offline = true; break }
        }
        val sent = mutableListOf<Pair<OutboxOp, JSONObject?>>()
        val failed = mutableListOf<OutboxOp>()
        if (!offline) {
            val r = outbox.flush(
                ready = { op -> op.kind == OpKind.Like || op.kind == OpKind.Unlike || uploads.doneFor(op.key) != null },
                failedDeps = { op -> uploads.failedFor(op.key) },
                send = ::send,
                onSent = { op, answer -> sent += op to answer; uploads.clearOwner(op.key) },
                onFailed = { op, _ -> failed += op },
            )
            offline = r.offline
        }
        return PumpResult(touched.values.toList(), sent, failed, offline)
    }

    /**
     * Сеть вернулась: что ждало из-за «нет сети», пробуем сразу, без
     * накопленной паузы (паузы после ошибок сервера — остаются).
     */
    fun wake() {
        outbox.all().filter { it.error == NetError.Kind.Offline.name && it.nextAt > 0 }.forEach { outbox.retryNow(it.key) }
        uploads.all().filter { it.state == UploadState.Waiting && it.error == NetError.Kind.Offline.name }
            .forEach { uploads.save(it.copy(nextAt = 0, attempts = 0)) }
    }

    /** Есть ли что делать: загрузки или очередь. */
    val busy: Boolean
        get() = outbox.count > 0 || uploads.all().any { it.state == UploadState.Waiting || it.state == UploadState.Uploading }

    /** Ближайший момент, когда есть смысл пробовать снова (мс); 0 — сейчас. */
    fun nextAttemptAt(): Long {
        val times = outbox.all().map { it.nextAt } + uploads.all().filter { it.state == UploadState.Waiting }.map { it.nextAt }
        return times.minOrNull() ?: 0
    }

    companion object {
        /** Демо: встроенный сервер и кэш в памяти — для тестов и пока нет аккаунта. */
        fun demo(clock: () -> Long, me: () -> User, cache: OfflineCache = MemoryCache(), chunkSize: Int = TusClient.DEFAULT_CHUNK): Net =
            Net(DemoServer(me, clock), cache, clock, me, chunkSize = chunkSize)
    }
}
