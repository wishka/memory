package ru.wishka.memory.data

import ru.wishka.memory.data.auth.KeyValueStore
import java.util.concurrent.ConcurrentHashMap

/**
 * Что считает статистика использования — только эти события, без
 * текстов, имён и адресов: «опубликовали запись», а не что в ней.
 */
enum class AppEvent(val id: String) {
    AppOpen("app_open"),
    SignUp("sign_up"),
    RecoveryUsed("recovery_used"),
    PostPublished("post_published"),
    PostEdited("post_edited"),
    CommentSent("comment_sent"),
    MessageSent("message_sent"),
    PrismPublished("prism_published"),
    SearchUsed("search_used"),
    TagOpened("tag_opened"),
    ActivityOpened("activity_opened"),
    LinkOpened("link_opened"),
    LinkShared("link_shared"),
    DataExported("data_exported"),
    LiveStarted("live_started"),
    LiveWatched("live_watched"),
    SecretChat("secret_chat"),
    Disappearing("disappearing_set"),
    StoryPublished("story_published"),
    Reposted("reposted"),
    VoiceCall("voice_call"),
    VideoCall("video_call"),
}

/** Отчёт о сбое: когда, какая ошибка и где — без данных человека (см. Scrub). */
data class CrashReport(val at: Long, val type: String, val message: String, val stack: String) {
    /** Первая строка — для «Последний сбой: …». */
    val title: String get() = type.substringAfterLast('.') + if (message.isNotBlank()) ": " + message.take(80) else ""

    fun text(): String = "$type: $message\n$stack"

    companion object {
        fun of(t: Throwable, at: Long): CrashReport =
            CrashReport(at, t.javaClass.name, Scrub.text(t.message.orEmpty()), Scrub.text(t.stackTraceToString().lines().drop(1).take(60).joinToString("\n")))

        /** Разобрать сохранённый отчёт (text()). */
        fun parse(at: Long, raw: String): CrashReport {
            val first = raw.substringBefore('\n')
            return CrashReport(at, first.substringBefore(": "), first.substringAfter(": ", ""), raw.substringAfter('\n', ""))
        }
    }
}

/**
 * Куда уходят отчёты и статистика. На Android — Sentry (telemetry/SentrySink),
 * если в сборке есть DSN; иначе никуда: всё остаётся на телефоне.
 */
interface TelemetrySink {
    fun crash(report: CrashReport)
    fun usage(day: String, counts: Map<String, Int>)
    fun breadcrumb(event: String)
}

/**
 * Убрать из текста ошибки то, что может указать на человека: почту,
 * @логины, номера телефонов и длинные числа, ссылки с параметрами.
 */
object Scrub {
    private val EMAIL = Regex("[\\w.+-]+@[\\w-]+\\.[\\w.]+")
    private val HANDLE = Regex("(?<![\\w])@[a-zA-Z0-9._]{3,}")
    private val LONG_NUMBER = Regex("\\+?\\d[\\d ()-]{6,}\\d")
    private val QUERY = Regex("(https?://[^\\s?]+)\\?[^\\s]*")
    private val PATH_USER = Regex("(state|memory)-[a-z0-9._]+\\.(json|zip)")

    fun text(s: String): String = s
        .replace(QUERY, "$1?…")
        .replace(EMAIL, "<email>")
        .replace(HANDLE, "@<login>")
        .replace(LONG_NUMBER, "<number>")
        .replace(PATH_USER, "$1-<login>.$2")
}

/**
 * Отчёты о сбоях и статистика — только с согласия, отдельно на каждое.
 * Согласие хранится на телефоне (TelemetryPrefs); по умолчанию — выключено.
 *
 * Сбой всегда записывается локально (последний отчёт виден в
 * «Приватности», его можно отправить вручную), а сам уходит только при
 * согласии на отчёты.
 */
object Telemetry {
    @Volatile var crashesAllowed: Boolean = false
    @Volatile var analyticsAllowed: Boolean = false
    @Volatile var sink: TelemetrySink? = null

    private val counts = ConcurrentHashMap<String, Int>()

    /** Событие для статистики: считается, только если человек разрешил. */
    fun track(event: AppEvent) {
        if (!analyticsAllowed) return
        counts.merge(event.id, 1, Int::plus)
        sink?.breadcrumb(event.id)
    }

    /** Накопленное с прошлого сохранения — и обнулить. */
    fun drain(): Map<String, Int> {
        val out = HashMap(counts)
        out.keys.forEach { counts.remove(it) }
        return out
    }

    /** Сбой: отправить, если разрешено. Ответ — ушёл ли он. */
    fun crash(report: CrashReport): Boolean {
        val s = sink ?: return false
        if (!crashesAllowed) return false
        s.crash(report)
        return true
    }

    /** Отказ от статистики — накопленное стирается. */
    fun forgetUsage() {
        counts.clear()
    }
}

/**
 * Согласие и счётчики статистики на телефоне. Счётчики копятся за день и
 * уходят одним отчётом на следующий день: по одному событию ничего не
 * отправляется, и по времени нельзя восстановить, что человек делал.
 */
class TelemetryPrefs(private val store: KeyValueStore) {
    val asked: Boolean get() = store.get("tm.asked") == "1"
    val crashes: Boolean get() = store.get("tm.crashes") == "1"
    val analytics: Boolean get() = store.get("tm.analytics") == "1"

    fun set(crashes: Boolean, analytics: Boolean) {
        store.put("tm.asked", "1")
        store.put("tm.crashes", if (crashes) "1" else "0")
        store.put("tm.analytics", if (analytics) "1" else "0")
        if (!analytics) {
            store.remove("tm.day")
            store.remove("tm.counts")
        }
        apply()
    }

    /** Перенести согласие в Telemetry — при запуске и после изменения. */
    fun apply() {
        Telemetry.crashesAllowed = crashes
        Telemetry.analyticsAllowed = analytics
        if (!analytics) Telemetry.forgetUsage()
    }

    /**
     * Сложить накопленное в Telemetry с сохранённым за день. Если день
     * сменился — вернуть отчёт за прошлый день (его пора отправить).
     */
    fun roll(today: String): Pair<String, Map<String, Int>>? {
        val fresh = Telemetry.drain()
        if (!analytics) return null
        val day = store.get("tm.day")
        val saved = decode(store.get("tm.counts").orEmpty())
        if (day != null && day != today && saved.isNotEmpty()) {
            store.put("tm.day", today)
            store.put("tm.counts", encode(fresh))
            return day to saved
        }
        val merged = (saved.keys + fresh.keys).associateWith { (saved[it] ?: 0) + (fresh[it] ?: 0) }
        store.put("tm.day", today)
        store.put("tm.counts", encode(merged))
        return null
    }

    private fun encode(m: Map<String, Int>) = m.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }

    private fun decode(s: String): Map<String, Int> =
        s.split(',').filter { '=' in it }.associate { it.substringBefore('=') to (it.substringAfter('=').toIntOrNull() ?: 0) }
}
