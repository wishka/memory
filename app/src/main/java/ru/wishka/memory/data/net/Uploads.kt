package ru.wishka.memory.data.net

import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.Base64

/** Откуда читать загружаемое: кусками, с любого места (для докачки). */
interface ByteSource {
    val size: Long
    fun read(offset: Long, max: Int): ByteArray
}

class FileSource(private val file: File) : ByteSource {
    override val size: Long get() = file.length()
    override fun read(offset: Long, max: Int): ByteArray = RandomAccessFile(file, "r").use { f ->
        f.seek(offset)
        val n = minOf(max.toLong(), size - offset).toInt().coerceAtLeast(0)
        val buf = ByteArray(n)
        f.readFully(buf)
        buf
    }
}

class BytesSource(private val bytes: ByteArray) : ByteSource {
    override val size: Long get() = bytes.size.toLong()
    override fun read(offset: Long, max: Int): ByteArray =
        bytes.copyOfRange(offset.toInt(), minOf(bytes.size.toLong(), offset + max).toInt())
}

enum class UploadState(val id: String) {
    /** В очереди: ждёт сжатия или своей очереди. */
    Waiting("waiting"),
    Uploading("uploading"),
    /** Поставили на паузу сами. */
    Paused("paused"),
    Done("done"),
    /** Не вышло и повторять бессмысленно (файл пропал, сервер отказал). */
    Failed("failed");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: Waiting
    }
}

/**
 * Одна загрузка: файл вложения → сервер по tus. Переживает обрыв сети и
 * перезапуск приложения: адрес загрузки (url) и сколько уже ушло (offset)
 * лежат в кэше, а сервер на HEAD отвечает, с какого байта продолжать.
 *
 * owner — ключ записи или сообщения в очереди отправки: она уйдёт, когда
 * загрузятся все её вложения.
 */
data class UploadJob(
    val id: String,
    val attachmentId: String,
    val owner: String,
    /** Исходный файл на телефоне. */
    val path: String,
    /** Что уходит на сервер — сжатая копия; "" — ещё не сжимали. */
    val uploadPath: String = "",
    val video: Boolean = false,
    val size: Long = 0,
    /** Адрес загрузки на сервере (tus Location); "" — ещё не создана. */
    val url: String = "",
    val offset: Long = 0,
    val state: UploadState = UploadState.Waiting,
    val error: String = "",
    val createdAt: Long = 0,
    /** Попыток подряд без успеха — для паузы перед следующей. */
    val attempts: Int = 0,
    /** Раньше этого времени не пробовать (мс). */
    val nextAt: Long = 0,
) {
    val progress: Float get() = if (size <= 0) 0f else (offset.toFloat() / size).coerceIn(0f, 1f)

    fun json(): JSONObject = JSONObject()
        .put("id", id).put("attachment", attachmentId).put("owner", owner).put("path", path)
        .put("uploadPath", uploadPath).put("video", video).put("size", size).put("url", url)
        .put("offset", offset).put("state", state.id).put("error", error).put("createdAt", createdAt)
        .put("attempts", attempts).put("nextAt", nextAt)

    companion object {
        fun of(o: JSONObject) = UploadJob(
            o.getString("id"), o.optString("attachment"), o.optString("owner"), o.optString("path"),
            o.optString("uploadPath"), o.optBoolean("video"), o.optLong("size"), o.optString("url"),
            o.optLong("offset"), UploadState.of(o.optString("state")), o.optString("error"), o.optLong("createdAt"),
            o.optInt("attempts"), o.optLong("nextAt"),
        )
    }
}

/**
 * Клиент tus 1.0 (tus.io): создать загрузку, узнать смещение, дослать
 * кусок. Готовые серверы — tusd, а также tus-node-server и др.
 */
class TusClient(private val api: Api, val chunkSize: Int = DEFAULT_CHUNK) {

    suspend fun create(size: Long, name: String, type: String): String {
        val meta = "filename ${b64(name)},filetype ${b64(type)}"
        val r = api.call(HttpRequest("POST", "/v1/uploads", headers = mapOf(
            "Tus-Resumable" to VERSION, "Upload-Length" to size.toString(), "Upload-Metadata" to meta,
        )))
        return r.header("Location") ?: throw NetError(NetError.Kind.BadData, r.code, "Нет Location")
    }

    suspend fun offset(url: String): Long {
        val r = api.call(HttpRequest("HEAD", url, headers = mapOf("Tus-Resumable" to VERSION, "Cache-Control" to "no-store")))
        return r.header("Upload-Offset")?.toLongOrNull() ?: throw NetError(NetError.Kind.BadData, r.code, "Нет Upload-Offset")
    }

    suspend fun patch(url: String, offset: Long, bytes: ByteArray): Long {
        val r = api.call(HttpRequest("PATCH", url, headers = mapOf(
            "Tus-Resumable" to VERSION, "Upload-Offset" to offset.toString(),
        ), body = bytes, contentType = "application/offset+octet-stream"))
        return r.header("Upload-Offset")?.toLongOrNull() ?: (offset + bytes.size)
    }

    /**
     * Досылать, пока не кончится файл. save — после каждого куска (смещение
     * в кэш: оборвётся — продолжим отсюда). Ошибка сети — NetError наружу,
     * сохранённое уже на месте.
     */
    suspend fun upload(job: UploadJob, source: ByteSource, save: (UploadJob) -> Unit): UploadJob {
        var j = job.copy(size = source.size, state = UploadState.Uploading)
        if (j.url.isEmpty()) {
            j = j.copy(url = create(source.size, File(j.uploadPath.ifEmpty { j.path }).name, if (j.video) "video/mp4" else "image/jpeg"), offset = 0)
            save(j)
        } else {
            // Сервер знает лучше, сколько дошло (последний кусок мог дойти без ответа)
            val known = try {
                offset(j.url)
            } catch (e: NetError) {
                // Загрузку на сервере уже убрали (истёк срок) — начать заново
                if (e.code == 404 || e.code == 410) {
                    j = j.copy(url = "", offset = 0)
                    return upload(j, source, save)
                }
                throw e
            }
            j = j.copy(offset = known)
            save(j)
        }
        while (j.offset < source.size) {
            val chunk = source.read(j.offset, chunkSize)
            val next = try {
                patch(j.url, j.offset, chunk)
            } catch (e: NetError) {
                // Разошлись в смещении — спросить сервер и продолжить с его места
                if (e.kind == NetError.Kind.Conflict) offset(j.url) else throw e
            }
            j = j.copy(offset = next)
            save(j)
        }
        return j.copy(state = UploadState.Done, error = "", attempts = 0, nextAt = 0)
    }

    private fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))

    companion object {
        const val VERSION = "1.0.0"
        /** 1 МБ: на плохой сети теряем немного, на хорошей — мало запросов. */
        const val DEFAULT_CHUNK = 1 shl 20
    }
}

/**
 * Очередь загрузок: хранится в кэше, идёт по одной, с паузой и отменой.
 * prepare — сжать перед отправкой (на телефоне — MediaCompressor);
 * source — чем читать файл.
 */
class Uploads(
    private val cache: OfflineCache,
    private val tus: TusClient,
    private val clock: () -> Long,
    private val prepare: suspend (UploadJob) -> UploadJob = { it },
    private val source: (String) -> ByteSource? = { p -> File(p).takeIf { it.exists() }?.let(::FileSource) },
) {
    private fun key(id: String) = CacheKeys.UPLOAD + id

    fun all(): List<UploadJob> = cache.keys(CacheKeys.UPLOAD).mapNotNull { k ->
        cache.get(k)?.let { runCatching { UploadJob.of(JSONObject(it.value)) }.getOrNull() }
    }

    fun get(id: String): UploadJob? = cache.get(key(id))?.let { runCatching { UploadJob.of(JSONObject(it.value)) }.getOrNull() }

    fun save(job: UploadJob) = cache.put(key(job.id), job.json().toString(), clock())

    fun add(job: UploadJob) = save(job.copy(createdAt = if (job.createdAt == 0L) clock() else job.createdAt))

    fun pause(id: String) = get(id)?.takeIf { it.state == UploadState.Waiting || it.state == UploadState.Uploading }?.let { save(it.copy(state = UploadState.Paused)) }

    fun resume(id: String) = get(id)?.takeIf { it.state == UploadState.Paused || it.state == UploadState.Failed }
        ?.let { save(it.copy(state = UploadState.Waiting, attempts = 0, nextAt = 0, error = "")) }

    fun remove(id: String) = cache.remove(key(id))

    /** Готовые вложения владельца: attachmentId → адрес на сервере. null — ещё не все готовы. */
    fun doneFor(owner: String): Map<String, String>? {
        val mine = all().filter { it.owner == owner }
        if (mine.any { it.state != UploadState.Done }) return null
        return mine.associate { it.attachmentId to it.url }
    }

    /** Что-то у владельца не загрузится никогда. */
    fun failedFor(owner: String): Boolean = all().any { it.owner == owner && it.state == UploadState.Failed }

    /** Убрать готовые загрузки владельца — после того как запись ушла. */
    fun clearOwner(owner: String) = all().filter { it.owner == owner }.forEach { remove(it.id) }

    /**
     * Одна следующая загрузка. Ответ — что с ней стало (null — делать нечего).
     * Нет сети — загрузка ждёт с паузой (1, 2, 4… с), Paused не трогаем.
     */
    suspend fun runOnce(onProgress: (UploadJob) -> Unit = {}): UploadJob? {
        val now = clock()
        // «Шла» во время прошлого запуска приложения — значит, оборвалась
        val job = all().firstOrNull { (it.state == UploadState.Waiting || it.state == UploadState.Uploading) && it.nextAt <= now } ?: return null
        var j = job
        try {
            if (j.uploadPath.isEmpty()) {
                j = prepare(j)
                if (j.uploadPath.isEmpty()) j = j.copy(uploadPath = j.path)
                save(j)
            }
            val src = source(j.uploadPath) ?: run {
                val failed = j.copy(state = UploadState.Failed, error = "Файл не найден")
                save(failed)
                return failed
            }
            val done = tus.upload(j, src) { step ->
                // Пока качали, могли поставить на паузу или отменить
                val current = get(step.id)
                if (current == null || current.state == UploadState.Paused) throw PausedException()
                save(step)
                onProgress(step)
            }
            save(done)
            onProgress(done)
            return done
        } catch (e: PausedException) {
            return get(j.id)
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val err = NetError.of(e)
            val latest = get(j.id) ?: return null
            val next = if (err.retryable) {
                latest.copy(state = UploadState.Waiting, attempts = latest.attempts + 1, nextAt = now + Backoff.delayMs(latest.attempts, retryAfterMs = err.retryAfterMs), error = err.kind.name)
            } else {
                latest.copy(state = UploadState.Failed, error = err.message.orEmpty())
            }
            save(next)
            onProgress(next)
            return next
        }
    }

    private class PausedException : Exception()
}
