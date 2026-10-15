package ru.wishka.memory.data.net

import org.json.JSONObject
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.MediaKind
import ru.wishka.memory.data.Message
import ru.wishka.memory.data.Post
import ru.wishka.memory.data.StateStore
import ru.wishka.memory.data.User
import ru.wishka.memory.data.FileCrypto
import ru.wishka.memory.data.FileKey
import ru.wishka.memory.data.auth.KeyValueStore
import java.io.File
import java.util.UUID

/** Отправленная запись или история и номер, который ей дал сервер. */
data class SentId(val kind: OpKind, val localId: Long, val serverId: Long, val key: String)

/** Что изменилось за один проход Net.pump — модели этого хватает, чтобы обновить экран. */
data class PumpResult(
    val uploads: List<UploadJob>,
    /** Отправленные действия и ответ сервера (запись или сообщение с номером сервера). */
    val sent: List<Pair<OutboxOp, JSONObject?>>,
    val failed: List<OutboxOp>,
    val offline: Boolean,
    /** Сервер не принял вход — нужно войти снова (очередь сохранена). */
    val authLost: Boolean = false,
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
    /** Где хранить токены входа (SecurePrefs); null — без входа. */
    tokens: KeyValueStore? = null,
    login: String = "",
    prepare: suspend (UploadJob) -> UploadJob = { it },
    source: (String) -> ByteSource? = { p -> java.io.File(p).takeIf { it.exists() }?.let(::FileSource) },
    chunkSize: Int = TusClient.DEFAULT_CHUNK,
    /** Живой канал; null — демо (события от демо-сервера). */
    realtime: Realtime? = null,
    /** Куда класть зашифрованные копии файлов секретных переписок. */
    private val tempDir: File = File(System.getProperty("java.io.tmpdir") ?: "."),
) {
    val session = Session(tokens, login)
    val api = Api(transport, me, session)
    val tus = TusClient(api, chunkSize)
    val uploads = Uploads(cache, tus, clock, { job -> encryptIfSecret(prepare(job)) }, source)
    val outbox = Outbox(cache, clock)
    val realtime: Realtime = realtime ?: DemoRealtime(clock).also { rt -> (transport as? DemoServer)?.realtime = rt }

    /**
     * Секретная переписка: файл шифруется на телефоне (FileCrypto, MEMF1)
     * после сжатия — сервер хранит только шифротекст.
     */
    private fun encryptIfSecret(job: UploadJob): UploadJob {
        if (job.secretKey.isEmpty()) return job
        val key = FileKey.decode(job.secretKey) ?: return job
        val src = File(job.uploadPath.ifEmpty { job.path })
        if (!src.exists()) return job
        val dst = File(tempDir.apply { mkdirs() }, "${job.id}.memf")
        FileCrypto.encryptFile(src, dst, key)
        // Сжатая копия больше не нужна — уходит зашифрованная
        if (job.uploadPath.isNotEmpty() && job.uploadPath != job.path) File(job.uploadPath).delete()
        return job.copy(uploadPath = dst.path, size = dst.length())
    }

    /** Скачанные чужие файлы (фото и видео записей и сообщений). */
    private val mediaDir = File(tempDir.parentFile ?: tempDir, "media")

    /** Файл лежит на сервере (а не на этом телефоне). */
    fun isRemote(path: String): Boolean = path.contains("/v1/uploads/")

    /** Где на телефоне будет копия файла с сервера. */
    fun mediaFile(path: String, ext: String = ""): File {
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(path.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(mediaDir, hash.take(32) + ext.filter { it.isLetterOrDigit() || it == '.' }.take(6))
    }

    /**
     * Скачать файл с сервера (уже скачанный — сразу). key — файл секретной
     * переписки: на сервере шифротекст, расшифровывается здесь.
     */
    suspend fun fetchMedia(path: String, key: FileKey? = null, ext: String = ""): File {
        val f = mediaFile(path, ext)
        if (f.exists() && f.length() > 0) return f
        val bytes = api.download(path)
        val plain = if (key != null) FileCrypto.decrypt(bytes, key) else bytes
        mediaDir.mkdirs()
        val part = File(mediaDir, f.name + ".part")
        part.writeBytes(plain)
        if (!part.renameTo(f)) { f.writeBytes(plain); part.delete() }
        return f
    }

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
    private fun queueMedia(owner: String, media: List<Attachment>, secretKeys: Map<String, String> = emptyMap()) {
        media.filter { a ->
            val secret = a.id in secretKeys
            // Секретное — любые файлы (и документы, и голосовые): открытым на сервер не уходит ничего
            (secret || ((a.kind == MediaKind.Photo || a.kind == MediaKind.Video) && !a.sticker)) && a.path.isNotEmpty() && !a.path.startsWith("/v1/")
        }.forEach { a ->
            uploads.add(UploadJob(newKey(), a.id, owner, a.path, video = a.kind == MediaKind.Video, secretKey = secretKeys[a.id].orEmpty()))
        }
    }

    /** «Интересное» страницами — как лента, со своим кэшем. */
    fun explorePager(): Pager<Post> {
        val c = codec()
        return Pager(CacheKeys.EXPLORE, cache, { it.id }, { c.postJson(it) }, { runCatching { c.postOf(it) }.getOrNull() }, clock) { cursor ->
            api.explore(cursor)
        }
    }

    /** Новая история: файл — в загрузки, история — в очередь (уйдёт после него). */
    fun queueStory(story: ru.wishka.memory.data.Story): String {
        val key = newKey()
        queueMedia(key, listOfNotNull(story.media))
        outbox.enqueue(OutboxOp(key, OpKind.CreateStory, story.id, codec().storyJson(story).toString()))
        return key
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

    /**
     * Сообщение в очередь. secretKeys — ключи файлов секретной переписки
     * (attachmentId → FileKey.encoded): такие вложения шифруются перед загрузкой.
     */
    fun queueMessage(chatId: Int, message: Message, secretKeys: Map<String, String> = emptyMap()): String {
        val key = newKey()
        queueMedia(key, message.attachments, secretKeys)
        outbox.enqueue(OutboxOp(key, OpKind.SendMessage, chatId.toLong(), codec().messageJson(message.copy(pending = false)).toString()))
        return key
    }

    /**
     * Любой запрос — в очередь: группы, «прочитано», профиль. coalesce —
     * «важно только последнее» (например, "me" — профиль): прежний такой же снимается.
     */
    fun queueCall(method: String, path: String, body: JSONObject? = null, coalesce: String? = null): String {
        val key = newKey()
        val payload = JSONObject().put("method", method).put("path", path)
            .put("body", body ?: JSONObject.NULL).put("coalesce", coalesce ?: "")
        outbox.enqueue(OutboxOp(key, OpKind.Call, 0, payload.toString()))
        return key
    }

    /** Войти на сервер с этого телефона. */
    suspend fun signIn(login: String, password: String, device: String, platform: String) = api.signIn(login, password, device, platform)

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
                api.sendRaw(op.target.toInt(), c.messageJson(m.copy(attachments = withRemote(m.attachments, remote))), op.key)
            }
            OpKind.CreateStory -> {
                val story = c.storyOf(JSONObject(op.payload))
                val media = story.media?.let { withRemote(listOf(it), remote).first() }
                val body = JSONObject().put("text", story.text).put("bg", story.bg).put("seed", story.seed).put("close", story.closeFriends)
                media?.let { body.put("media", c.attachmentJson(it)) }
                api.send("POST", "/v1/stories", body, op.key)
            }
            OpKind.Call -> {
                val p = JSONObject(op.payload)
                api.send(p.getString("method"), p.getString("path"), p.optJSONObject("body"), op.key)
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
        var authLost = false
        for (i in 0 until maxUploads) {
            val j = uploads.runOnce { step -> touched[step.id] = step; onProgress(step) } ?: break
            touched[j.id] = j
            if (j.state == UploadState.Waiting && j.error == NetError.Kind.Offline.name) { offline = true; break }
        }
        val sent = mutableListOf<Pair<OutboxOp, JSONObject?>>()
        val failed = mutableListOf<OutboxOp>()
        if (!offline) {
            val r = outbox.flush(
                ready = { op -> op.kind == OpKind.Like || op.kind == OpKind.Unlike || op.kind == OpKind.Call || uploads.doneFor(op.key) != null },
                failedDeps = { op -> uploads.failedFor(op.key) },
                send = ::send,
                onSent = { op, answer ->
                    sent += op to answer
                    uploads.clearOwner(op.key)
                    rememberServerId(op, answer)
                },
                onFailed = { op, _ -> failed += op },
            )
            offline = r.offline
            authLost = r.authLost
        }
        return PumpResult(touched.values.toList(), sent, failed, offline, authLost)
    }

    /**
     * Номер, который сервер дал записи или истории, — запоминается в кэше:
     * отправка могла пройти в фоне (NetWorker), без экрана, и приложение
     * подхватит номер при следующем открытии (takeServerIds).
     */
    private fun rememberServerId(op: OutboxOp, answer: JSONObject?) {
        if (op.kind != OpKind.CreatePost && op.kind != OpKind.CreateStory) return
        val id = answer?.optLong("id") ?: return
        if (id <= 0) return
        val o = JSONObject().put("kind", op.kind.id).put("local", op.target).put("server", id).put("key", op.key)
        runCatching { cache.put(SENT + op.key, o.toString(), clock()) }
    }

    /** Отправленные записи и истории: (вид, номер на телефоне, номер сервера, ключ очереди). Забираются один раз. */
    fun takeServerIds(): List<SentId> {
        val keys = runCatching { cache.keys(SENT) }.getOrDefault(emptyList())
        return keys.mapNotNull { k ->
            val o = cache.get(k)?.value?.let { runCatching { JSONObject(it) }.getOrNull() }
            cache.remove(k)
            o?.let { SentId(OpKind.of(it.optString("kind")) ?: return@let null, it.optLong("local"), it.optLong("server"), it.optString("key")) }
        }
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
        /** Ключи кэша: номер сервера у отправленного. */
        const val SENT = "sent:"

        /** Демо: встроенный сервер и кэш в памяти — для тестов и пока нет аккаунта. */
        fun demo(clock: () -> Long, me: () -> User, cache: OfflineCache = MemoryCache(), chunkSize: Int = TusClient.DEFAULT_CHUNK, tokens: KeyValueStore? = null, login: String = "demo"): Net =
            Net(DemoServer(me, clock), cache, clock, me, tokens = tokens, login = login, chunkSize = chunkSize)
    }
}
