package ru.wishka.memory.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import org.json.JSONObject
import ru.wishka.memory.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/** Откуда трек медиатеки. */
enum class CatalogSource(val title: String) { Jamendo("Jamendo"), Openverse("Openverse") }

/**
 * Трек медиатеки PRISM: слушается потоком, в «Мою музыку» скачивается,
 * если лицензия и площадка это разрешают. license — «CC BY-NC 3.0»;
 * его и автора нужно указывать, когда трек звучит в ролике.
 */
data class CatalogTrack(
    val id: String,
    val source: CatalogSource,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val streamUrl: String,
    val downloadUrl: String,
    val imageUrl: String,
    val license: String,
    val licenseUrl: String,
    val pageUrl: String,
) {
    val downloadable: Boolean get() = downloadUrl.isNotEmpty()
    /** Подпись для ролика и «Моей музыки»: «Автор — Название». */
    val credit: String get() = if (artist.isBlank()) title else "$artist — $title"
}

/** Ответ медиатеки: треки или текст ошибки для экрана. */
sealed class CatalogResult {
    data class Tracks(val tracks: List<CatalogTrack>, val more: Boolean) : CatalogResult()
    data class Failure(val message: String) : CatalogResult()
}

/**
 * Медиатека PRISM — открытая музыка из двух источников:
 *  - Jamendo (api.jamendo.com/v3.0) — независимые музыканты, лицензии
 *    Creative Commons; нужен ключ client_id (devportal.jamendo.com).
 *    Бесплатный ключ — для некоммерческого приложения;
 *  - Openverse (api.openverse.org) — каталог открытого аудио (Freesound,
 *    Wikimedia Commons и др.) без ключа; берём CC0 и CC BY.
 * Ключ Jamendo приходит при сборке: local.properties (jamendo.clientId)
 * или переменная JAMENDO_CLIENT_ID — в репозитории его нет.
 */
object MusicCatalog {
    const val PAGE = 20
    private const val USER_AGENT = "Memory/1.0 (Android; github.com/wishka/memory)"

    val jamendoKey: String get() = BuildConfig.JAMENDO_CLIENT_ID
    val hasJamendoKey: Boolean get() = jamendoKey.isNotBlank()

    /** Жанры-метки: Jamendo ищет по тегам, Openverse — по словам. */
    val GENRES = listOf(
        "lofi" to "Lo-fi", "ambient" to "Эмбиент", "electronic" to "Электроника", "pop" to "Поп",
        "rock" to "Рок", "hiphop" to "Хип-хоп", "jazz" to "Джаз", "classical" to "Классика",
        "cinematic" to "Кино", "acoustic" to "Акустика", "chillout" to "Чилаут", "dance" to "Танцевальная",
    )

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Поиск: query — слова, genre — метка из GENRES; оба пустые — популярное за неделю. Вызывать не в главном потоке. */
    fun search(source: CatalogSource, query: String, genre: String?, page: Int): CatalogResult = try {
        when (source) {
            CatalogSource.Jamendo -> jamendo(query, genre, page)
            CatalogSource.Openverse -> openverse(query, genre, page)
        }
    } catch (e: Exception) {
        CatalogResult.Failure(ru.wishka.memory.i18n.t("Нет связи с медиатекой. Проверьте интернет и попробуйте ещё раз."))
    }

    private fun jamendo(query: String, genre: String?, page: Int): CatalogResult {
        if (!hasJamendoKey) return CatalogResult.Failure(ru.wishka.memory.i18n.t("Jamendo не подключён: в сборке нет ключа JAMENDO_CLIENT_ID."))
        val url = buildString {
            append("https://api.jamendo.com/v3.0/tracks/?format=json&audioformat=mp32&imagesize=200")
            append("&client_id=").append(enc(jamendoKey))
            append("&limit=").append(PAGE).append("&offset=").append(page * PAGE)
            if (query.isNotBlank()) append("&search=").append(enc(query.trim())).append("&boost=popularity_month")
            else append("&order=popularity_week")
            if (genre != null) append("&fuzzytags=").append(enc(genre))
        }
        val root = JSONObject(get(url))
        val headers = root.optJSONObject("headers")
        if (headers != null && headers.optString("status") != "success") {
            return CatalogResult.Failure(headers.optString("error_message").ifBlank { ru.wishka.memory.i18n.t("Jamendo не ответил") })
        }
        val results = root.optJSONArray("results") ?: return CatalogResult.Tracks(emptyList(), false)
        val tracks = (0 until results.length()).map { i ->
            val o = results.getJSONObject(i)
            val ccUrl = o.optString("license_ccurl")
            CatalogTrack(
                id = "jamendo-" + o.optString("id"),
                source = CatalogSource.Jamendo,
                title = o.optString("name"),
                artist = o.optString("artist_name"),
                durationMs = o.optLong("duration") * 1000,
                streamUrl = o.optString("audio"),
                downloadUrl = if (o.optBoolean("audiodownload_allowed", true)) o.optString("audiodownload") else "",
                imageUrl = o.optString("image").ifBlank { o.optString("album_image") },
                license = licenseFromUrl(ccUrl),
                licenseUrl = ccUrl,
                pageUrl = o.optString("shareurl"),
            )
        }.filter { it.streamUrl.isNotBlank() }
        return CatalogResult.Tracks(tracks, results.length() == PAGE)
    }

    private fun openverse(query: String, genre: String?, page: Int): CatalogResult {
        val words = listOfNotNull(query.trim().ifBlank { null }, genre).joinToString(" ").ifBlank { "music" }
        val url = "https://api.openverse.org/v1/audio/?category=music&license=cc0,by&mature=false" +
            "&page_size=$PAGE&page=${page + 1}&q=${enc(words)}"
        val root = JSONObject(get(url))
        val results = root.optJSONArray("results") ?: return CatalogResult.Tracks(emptyList(), false)
        val tracks = (0 until results.length()).map { i ->
            val o = results.getJSONObject(i)
            val license = o.optString("license")
            val version = o.optString("license_version")
            val file = o.optString("url")
            CatalogTrack(
                id = "openverse-" + o.optString("id"),
                source = CatalogSource.Openverse,
                title = o.optString("title").ifBlank { ru.wishka.memory.i18n.t("Без названия") },
                artist = o.optString("creator"),
                durationMs = o.optLong("duration"),
                streamUrl = file,
                downloadUrl = file,
                imageUrl = o.optString("thumbnail"),
                license = licenseLabel(license, version),
                licenseUrl = o.optString("license_url"),
                pageUrl = o.optString("foreign_landing_url"),
            )
        }.filter { it.streamUrl.startsWith("https://") } // обычный http Android не пропустит
        val pages = root.optInt("page_count", 1)
        return CatalogResult.Tracks(tracks, page + 1 < pages)
    }

    /** «cc0» → «CC0», «by-nc», «4.0» → «CC BY-NC 4.0». */
    fun licenseLabel(code: String, version: String): String = when {
        code.isBlank() -> ""
        code.equals("cc0", true) -> "CC0"
        code.equals("pdm", true) -> ru.wishka.memory.i18n.t("Общественное достояние")
        else -> "CC ${code.uppercase()} $version".trim()
    }

    /** «…/licenses/by-nc-sa/3.0/» → «CC BY-NC-SA 3.0». */
    fun licenseFromUrl(url: String): String {
        val parts = url.substringAfter("/licenses/", "").trim('/').split('/')
        if (parts.isEmpty() || parts[0].isBlank()) return if (url.contains("publicdomain")) "CC0" else ""
        return licenseLabel(parts[0], parts.getOrElse(1) { "" })
    }

    /**
     * Скачать трек в «Мою музыку». Только если площадка разрешает
     * скачивание; размер — до 200 МБ. null — не получилось.
     */
    fun download(context: Context, track: CatalogTrack, now: Long): MusicTrack? {
        if (!track.downloadable) return null
        val dir = File(context.filesDir, "media").apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        // Расширение — как у файла: по нему плеер понимает формат (mp3, ogg, flac…)
        val ext = track.downloadUrl.substringBefore('?').substringAfterLast('.', "mp3").lowercase().takeIf { it.length in 2..5 } ?: "mp3"
        val target = File(dir, "$id.$ext")
        return try {
            val conn = URL(track.downloadUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            try {
                if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                var copied = 0L
                conn.inputStream.use { input ->
                    target.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            copied += n
                            if (copied > MediaFiles.MAX_BYTES) error("too big")
                            out.write(buf, 0, n)
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            MusicTrack(
                id = id,
                title = track.title.take(120),
                artist = track.artist.take(120),
                path = target.absolutePath,
                durationMs = track.durationMs,
                addedAt = now,
                license = track.license,
                sourceUrl = track.pageUrl,
            )
        } catch (e: Exception) {
            target.delete()
            null
        }
    }

    private val covers = LruCache<String, Bitmap>(60)

    /** Обложка трека — уменьшенная, с кэшем в памяти. Вызывать не в главном потоке. */
    fun cover(url: String): Bitmap? {
        if (url.isBlank()) return null
        covers.get(url)?.let { return it }
        return runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            try {
                if (conn.responseCode !in 200..299) return null
                val bytes = conn.inputStream.use { it.readBytes() }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } finally {
                conn.disconnect()
            }
        }.getOrNull()?.also { covers.put(url, it) }
    }
}
