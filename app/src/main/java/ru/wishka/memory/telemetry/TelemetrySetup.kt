package ru.wishka.memory.telemetry

import android.content.Context
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import ru.wishka.memory.BuildConfig
import ru.wishka.memory.data.CrashReport
import ru.wishka.memory.data.Telemetry
import ru.wishka.memory.data.TelemetryPrefs
import ru.wishka.memory.data.TelemetrySink
import ru.wishka.memory.data.auth.KeyValueStore
import java.io.File
import java.time.LocalDate

/** Обычные настройки телефона как «ключ → строка». */
class PlainPrefs(context: Context, name: String) : KeyValueStore {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    override fun remove(key: String) { prefs.edit().remove(key).apply() }
}

/** Последний сбой — файлом на телефоне: виден в «Приватности», отправить можно вручную. */
class CrashLog(context: Context) {
    private val file = File(context.filesDir, "last-crash.txt")
    private val time = File(context.filesDir, "last-crash.time")

    fun save(report: CrashReport) {
        runCatching {
            file.writeText(report.text())
            time.writeText(report.at.toString())
        }
    }

    fun last(): CrashReport? = runCatching {
        if (!file.exists()) return null
        CrashReport.parse(time.readText().trim().toLongOrNull() ?: file.lastModified(), file.readText())
    }.getOrNull()

    fun clear() {
        file.delete()
        time.delete()
    }
}

/**
 * Отчёты о сбоях и статистика через Sentry — только с согласия и только
 * если в сборке есть ключ (sentry.dsn в local.properties или переменная
 * SENTRY_DSN; в CI — секрет). Без ключа всё остаётся на телефоне.
 *
 * Sentry не собирает ничего лишнего: без имени, логина и почты, без
 * снимков экрана, из «хлебных крошек» — только наши события (AppEvent).
 */
object TelemetrySetup : TelemetrySink {
    private var started = false
    private lateinit var prefs: TelemetryPrefs
    private lateinit var crashes: CrashLog

    val hasKey: Boolean get() = BuildConfig.SENTRY_DSN.isNotBlank()

    /** При запуске приложения — до любого экрана. */
    fun init(context: Context) {
        val app = context.applicationContext
        prefs = TelemetryPrefs(PlainPrefs(app, "telemetry"))
        crashes = CrashLog(app)
        prefs.apply()
        // Сбой записывается на телефоне всегда; дальше — обработчик системы (и Sentry, если включён)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            crashes.save(CrashReport.of(error, System.currentTimeMillis()))
            previous?.uncaughtException(thread, error)
        }
        refresh(app)
        // Статистика за прошлый день — одним отчётом
        prefs.roll(LocalDate.now().toString())?.let { (day, counts) -> usage(day, counts) }
    }

    /** Человек изменил согласие. */
    fun update(context: Context, crashesAllowed: Boolean, analyticsAllowed: Boolean) {
        prefs.set(crashesAllowed, analyticsAllowed)
        refresh(context.applicationContext)
    }

    val asked: Boolean get() = ::prefs.isInitialized && prefs.asked
    val crashesOn: Boolean get() = ::prefs.isInitialized && prefs.crashes
    val analyticsOn: Boolean get() = ::prefs.isInitialized && prefs.analytics

    fun lastCrash(): CrashReport? = if (::crashes.isInitialized) crashes.last() else null

    fun clearCrash() {
        if (::crashes.isInitialized) crashes.clear()
    }

    /** Сохранить накопленную статистику (при сворачивании приложения). */
    fun flush() {
        if (!::prefs.isInitialized) return
        prefs.roll(LocalDate.now().toString())?.let { (day, counts) -> usage(day, counts) }
    }

    /**
     * Отправить последний сбой вручную — по нажатию человека, даже если
     * отчёты выключены: это разовое явное согласие. false — ключа нет.
     */
    fun sendLastCrash(context: Context): Boolean {
        val report = lastCrash() ?: return false
        if (!hasKey) return false
        val wasStarted = started
        if (!started) start(context.applicationContext, handleCrashes = false)
        crash(report)
        if (!wasStarted) stop()
        crashes.clear()
        return true
    }

    private fun refresh(context: Context) {
        val want = hasKey && (Telemetry.crashesAllowed || Telemetry.analyticsAllowed)
        if (want && !started) start(context, handleCrashes = Telemetry.crashesAllowed)
        if (!want && started) stop()
        Telemetry.sink = if (started) this else null
    }

    private fun start(context: Context, handleCrashes: Boolean) {
        runCatching {
            SentryAndroid.init(context) { o ->
                o.dsn = BuildConfig.SENTRY_DSN
                o.release = "memory@" + BuildConfig.VERSION_NAME
                o.environment = if (BuildConfig.DEBUG) "debug" else "release"
                o.isEnableUncaughtExceptionHandler = handleCrashes
                o.isEnableAutoSessionTracking = false
                // Только наши события — без кликов, экранов и системных событий
                o.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { b, _ -> if (b.category == "memory") b else null }
            }
            started = true
        }
    }

    private fun stop() {
        runCatching { Sentry.close() }
        started = false
        Telemetry.sink = null
    }

    override fun crash(report: CrashReport) {
        if (!started) return
        Sentry.captureMessage(report.title + "\n" + report.stack, SentryLevel.ERROR)
    }

    override fun usage(day: String, counts: Map<String, Int>) {
        if (!started || counts.isEmpty() || !Telemetry.analyticsAllowed) return
        Sentry.withScope { scope ->
            scope.setTag("usage_day", day)
            counts.forEach { (k, v) -> scope.setTag("n_$k", v.toString()) }
            Sentry.captureMessage("usage", SentryLevel.INFO)
        }
    }

    override fun breadcrumb(event: String) {
        if (!started) return
        Sentry.addBreadcrumb(Breadcrumb().apply { category = "memory"; message = event })
    }
}
