package ru.wishka.memory.net

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runInterruptible
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import ru.wishka.memory.BuildConfig
import ru.wishka.memory.data.User
import ru.wishka.memory.data.net.CacheEntry
import ru.wishka.memory.data.net.CacheKeys
import ru.wishka.memory.data.net.DemoServer
import ru.wishka.memory.data.net.HttpRequest
import ru.wishka.memory.data.net.HttpResponse
import ru.wishka.memory.data.net.MediaQuality
import ru.wishka.memory.data.net.Net
import ru.wishka.memory.data.net.OfflineCache
import ru.wishka.memory.data.net.Transport
import java.io.File
import java.time.Duration

/**
 * Запросы к настоящему серверу — OkHttp. baseUrl — API_URL из сборки
 * (local.properties api.url или секрет API_URL в CI).
 */
class OkHttpTransport(baseUrl: String, private val client: OkHttpClient = defaultClient) : Transport {
    private val base = baseUrl.trimEnd('/')

    override suspend fun send(request: HttpRequest): HttpResponse = runInterruptible(Dispatchers.IO) {
        val url = (if (request.path.startsWith("http")) request.path else base + request.path).toHttpUrl()
            .newBuilder().apply { request.query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val needsBody = request.method == "POST" || request.method == "PUT" || request.method == "PATCH"
        val body = request.body?.toRequestBody(request.contentType?.toMediaTypeOrNull())
            ?: if (needsBody) ByteArray(0).toRequestBody(null) else null
        val built = Request.Builder().url(url).method(request.method, body)
            .apply { request.headers.forEach { (k, v) -> header(k, v) } }
            .build()
        client.newCall(built).execute().use { r ->
            HttpResponse(r.code, r.headers.names().associateWith { r.headers[it].orEmpty() }, r.body?.bytes() ?: ByteArray(0))
        }
    }

    companion object {
        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(15))
                .readTimeout(Duration.ofSeconds(30))
                // Кусок загрузки 1 МБ на плохой сети может идти долго
                .writeTimeout(Duration.ofSeconds(60))
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}

/**
 * Офлайн-кэш в SQLite: одна таблица «ключ — значение — когда — порядок».
 * Своя база на каждый аккаунт (net-<логин>.db).
 */
class SqliteCache(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 1), OfflineCache {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE kv (k TEXT PRIMARY KEY, v TEXT NOT NULL, at INTEGER NOT NULL, seq INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX kv_seq ON kv(seq)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {}

    @Synchronized
    override fun get(key: String): CacheEntry? =
        readableDatabase.rawQuery("SELECT v, at FROM kv WHERE k = ?", arrayOf(key)).use { c ->
            if (c.moveToFirst()) CacheEntry(c.getString(0).orEmpty(), c.getLong(1)) else null
        }

    @Synchronized
    override fun put(key: String, value: String, at: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val upd = db.compileStatement("UPDATE kv SET v = ?, at = ? WHERE k = ?")
            upd.bindString(1, value); upd.bindLong(2, at); upd.bindString(3, key)
            val changed = upd.executeUpdateDelete()
            upd.close()
            if (changed == 0) {
                // Порядок добавления — для очереди: что раньше встало, то раньше уйдёт
                db.execSQL("INSERT INTO kv (k, v, at, seq) VALUES (?, ?, ?, (SELECT IFNULL(MAX(seq), 0) + 1 FROM kv))", arrayOf(key, value, at))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    override fun remove(key: String) = writableDatabase.execSQL("DELETE FROM kv WHERE k = ?", arrayOf(key))

    @Synchronized
    override fun keys(prefix: String): List<String> =
        readableDatabase.rawQuery("SELECT k FROM kv WHERE k >= ? AND k < ? ORDER BY seq", arrayOf(prefix, prefix + "￿")).use { c ->
            val out = mutableListOf<String>()
            while (c.moveToNext()) c.getString(0)?.let(out::add)
            out
        }

    @Synchronized
    override fun sizeBytes(): Long =
        readableDatabase.rawQuery("SELECT IFNULL(SUM(LENGTH(k) + LENGTH(v)), 0) FROM kv WHERE k >= ? AND k < ?", arrayOf(CacheKeys.PAGE, CacheKeys.PAGE + "￿")).use { c ->
            if (c.moveToFirst()) c.getLong(0) * 2 else 0
        }

    @Synchronized
    override fun clearPages() = writableDatabase.execSQL("DELETE FROM kv WHERE k >= ? AND k < ?", arrayOf(CacheKeys.PAGE, CacheKeys.PAGE + "￿"))
}

/**
 * Есть ли сеть и бесплатная ли она (Wi‑Fi): от этого — «нет сети» в
 * интерфейсе, когда очереди снова пробовать и можно ли слать оригиналы.
 */
class NetMonitor(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val _online = MutableStateFlow(true)
    private val _unmetered = MutableStateFlow(false)
    val online: StateFlow<Boolean> = _online.asStateFlow()
    val unmetered: StateFlow<Boolean> = _unmetered.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = apply(caps)
        override fun onLost(network: Network) {
            _online.value = false
            _unmetered.value = false
        }
    }

    private fun apply(caps: NetworkCapabilities?) {
        _online.value = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        _unmetered.value = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
    }

    fun start() {
        runCatching {
            apply(cm.getNetworkCapabilities(cm.activeNetwork))
            cm.registerDefaultNetworkCallback(callback)
        }
    }

    fun stop() {
        runCatching { cm.unregisterNetworkCallback(callback) }
    }
}

/** Собрать сеть для вошедшего аккаунта. */
object NetSetup {
    /** Сервер настоящий, если в сборке есть адрес; иначе — демо на телефоне. */
    val usesDemo: Boolean get() = BuildConfig.API_URL.isBlank()

    /** Удалили аккаунт — стереть его базу сети и сжатые копии. */
    fun deleteAccount(context: Context, login: String) {
        val safe = login.filter { it.isLetterOrDigit() || it in "._" }
        val app = context.applicationContext
        app.deleteDatabase("net-$safe.db")
        File(app.cacheDir, "demo-server/$safe").deleteRecursively()
        File(app.cacheDir, "uploads").deleteRecursively()
    }

    fun forAccount(
        context: Context,
        login: String,
        me: () -> User,
        clock: () -> Long,
        quality: () -> MediaQuality,
        unmetered: () -> Boolean,
    ): Net {
        val safe = login.filter { it.isLetterOrDigit() || it in "._" }
        val app = context.applicationContext
        val cache = SqliteCache(app, "net-$safe.db")
        val transport: Transport = if (usesDemo) {
            // Демо-сервер живёт, пока открыто приложение: его загрузки — во временной папке
            val dir = File(app.cacheDir, "demo-server/$safe").apply { deleteRecursively(); mkdirs() }
            DemoServer(me, clock, dir)
        } else {
            OkHttpTransport(BuildConfig.API_URL)
        }
        return Net(
            transport, cache, clock, me,
            prepare = { job -> MediaCompressor.prepare(app, job, quality(), unmetered()) },
        )
    }
}
