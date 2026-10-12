package ru.wishka.memory.data

import android.content.Context
import org.json.JSONObject
import ru.wishka.memory.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/**
 * Гифки: встроенный набор Memory (assets/gifs, рисует scripts/media_assets.py),
 * свои из галереи и поиск GIPHY. Выбранная гифка копируется в память
 * приложения обычным вложением-фото с mime image/gif — так она переживёт
 * перезапуск и уйдёт в «Поделиться» как есть. Те же правила — в Gifs.swift.
 */
data class BuiltInGif(val id: String, val ru: String)

object Gifs {
    /** В том же порядке, что в scripts/media_assets.py. */
    val BUILT_IN = listOf(
        BuiltInGif("hello", "Привет!"), BuiltInGif("thanks", "Спасибо"), BuiltInGif("love", "Люблю"),
        BuiltInGif("wow", "Ого!"), BuiltInGif("haha", "Ха-ха"), BuiltInGif("hug", "Обнимаю"),
        BuiltInGif("party", "Ура!"), BuiltInGif("yes", "Да!"), BuiltInGif("morning", "Доброе утро"),
        BuiltInGif("night", "Спокойной ночи"), BuiltInGif("rocket", "Погнали!"), BuiltInGif("crystal", "Memory"),
    )

    fun assetPath(id: String) = "gifs/$id.gif"

    /** Встроенная гифка для показа в выборе: копия в кэше (ImageDecoder читает файл). */
    fun previewFile(context: Context, gif: BuiltInGif): File? = runCatching {
        val file = File(File(context.cacheDir, "gifs").apply { mkdirs() }, "${gif.id}.gif")
        if (!file.exists() || file.length() == 0L) {
            // Сначала во временный файл: оборванная копия не выдаст себя за готовую
            val part = File(file.path + ".part")
            context.assets.open(assetPath(gif.id)).use { input -> part.outputStream().use { input.copyTo(it) } }
            if (!part.renameTo(file)) error("rename")
        }
        file
    }.getOrNull()

    /** Встроенная гифка — копией во вложения. */
    fun builtIn(context: Context, gif: BuiltInGif): Attachment? = runCatching {
        val target = MediaFiles.newFile(context, "gif")
        context.assets.open(assetPath(gif.id)).use { input -> target.outputStream().use { input.copyTo(it) } }
        Attachment(target.nameWithoutExtension, MediaKind.Photo, target.absolutePath, "${gif.id}.gif", target.length(), "image/gif")
    }.getOrNull()
}

/** Гифка из поиска GIPHY. */
data class GiphyGif(val id: String, val title: String, val previewUrl: String, val url: String, val width: Int, val height: Int)

sealed class GiphyResult {
    data class Gifs(val gifs: List<GiphyGif>) : GiphyResult()
    data class Failure(val message: String) : GiphyResult()
}

/**
 * Поиск GIPHY (developers.giphy.com). Ключ — при сборке: local.properties
 * (giphy.apiKey) или переменная GIPHY_API_KEY; в репозитории его нет.
 * Условие GIPHY — подпись «Powered by GIPHY» рядом с результатами.
 */
object Giphy {
    private const val API = "https://api.giphy.com/v1/gifs/"
    private const val USER_AGENT = "Memory/1.0 (Android; github.com/wishka/memory)"

    val key: String get() = BuildConfig.GIPHY_API_KEY
    val available: Boolean get() = key.isNotBlank()

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", USER_AGENT)
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Пустой запрос — популярное сейчас. Вызывать не в главном потоке. */
    fun search(query: String, offset: Int = 0): GiphyResult {
        if (!available) return GiphyResult.Failure("no-key")
        val q = query.trim()
        val url = if (q.isEmpty()) "${API}trending?api_key=$key&limit=24&offset=$offset&rating=pg-13"
        else "${API}search?api_key=$key&q=${URLEncoder.encode(q, "UTF-8")}&limit=24&offset=$offset&rating=pg-13&lang=ru"
        return try { GiphyResult.Gifs(parse(get(url))) } catch (e: Exception) { GiphyResult.Failure(e.message.orEmpty()) }
    }

    /** Разбор ответа: превью — fixed_width_small, сама гифка — fixed_width (до ~200 px, лёгкая). */
    fun parse(json: String): List<GiphyGif> {
        val data = JSONObject(json).optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { i ->
            val o = data.optJSONObject(i) ?: return@mapNotNull null
            val images = o.optJSONObject("images") ?: return@mapNotNull null
            val full = images.optJSONObject("fixed_width") ?: images.optJSONObject("downsized") ?: return@mapNotNull null
            val small = images.optJSONObject("fixed_width_small") ?: full
            val url = full.optString("url")
            if (url.isEmpty()) return@mapNotNull null
            GiphyGif(o.optString("id"), o.optString("title"), small.optString("url", url), url, full.optString("width").toIntOrNull() ?: 200, full.optString("height").toIntOrNull() ?: 200)
        }
    }

    /** Превью из поиска — в кэш (по имени из адреса), чтобы показать гифкой. Не в главном потоке. */
    fun cachePreview(context: Context, url: String): File? {
        val file = File(File(context.cacheDir, "giphy").apply { mkdirs() }, url.hashCode().toUInt().toString(16) + ".gif")
        if (file.exists() && file.length() > 0) return file
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            try {
                if (conn.responseCode !in 200..299) return null
                val part = File(file.path + ".part")
                conn.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
                if (!part.renameTo(file)) return null
            } finally {
                conn.disconnect()
            }
            file
        } catch (e: Exception) {
            file.delete()
            null
        }
    }

    /** Скачать выбранную гифку во вложения. Вызывать не в главном потоке. */
    fun download(context: Context, gif: GiphyGif): Attachment? {
        val target = MediaFiles.newFile(context, "gif")
        return try {
            val conn = URL(gif.url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            try {
                if (conn.responseCode !in 200..299) return null
                conn.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
            } finally {
                conn.disconnect()
            }
            Attachment(UUID.randomUUID().toString(), MediaKind.Photo, target.absolutePath, "giphy-${gif.id}.gif", target.length(), "image/gif")
        } catch (e: Exception) {
            File(target.path).delete()
            null
        }
    }
}
