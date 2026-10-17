package ru.wishka.memory.data

import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf

/** Комментарий в эфире: кто и что, без истории — эфирный чат живёт, пока идёт эфир. */
data class LiveComment(
    val id: Long,
    val authorId: Int,
    val name: String,
    val text: String,
    val at: Long,
    val hue: Float = 0.5f,
)

/**
 * Прямой эфир. room — комната видеосервера (LiveKit), в неё публикует
 * ведущий и подключаются зрители. Пока сервера нет — демо: видео ведущего
 * видит только он сам, зрители и комментарии приходят из демо-жизни.
 */
data class LiveStream(
    val id: Long,
    val hostId: Int,
    val title: String,
    val startedAt: Long,
    val viewers: Int = 0,
    val peakViewers: Int = 0,
    val hearts: Int = 0,
    val comments: List<LiveComment> = emptyList(),
    val endedAt: Long = 0,
    /** Видят только друзья. */
    val friendsOnly: Boolean = false,
    /** Картинка демо-эфира (туманность) — пока видео не идёт с сервера. */
    val seed: Int = 0,
    /** Эфир на сервере (номер — серверный): зрители, сердечки и чат — оттуда, не из демо. */
    val server: Boolean = false,
) {
    val live: Boolean get() = endedAt == 0L
    val room: String get() = "live-$id"

    /** Сколько шёл (или идёт) эфир, мс. */
    fun durationMs(now: Long): Long = ((if (endedAt > 0) endedAt else now) - startedAt).coerceAtLeast(0)
}

/**
 * Видеосвязь эфира. Демо — ничего не передаёт. С сервером — LiveKit:
 * ведущий публикует камеру и микрофон в комнату stream.room, зрители
 * подписываются; токен комнаты выдаёт сервер Memory (POST /live/token).
 * Адрес сервера LiveKit — LIVEKIT_URL (local.properties livekit.url,
 * в CI — секрет), см. docs/LIVE.md.
 */
interface LiveTransport {
    /** Начать публикацию камеры и микрофона (url — адрес LiveKit; пустой токен — демо). */
    fun publish(url: String, room: String, token: String)

    /** Подключиться зрителем. */
    fun join(url: String, room: String, token: String)

    /** Выйти из комнаты (ведущий — закончить публикацию). */
    fun leave()

    /** Идёт ли настоящая видеосвязь (false — демо). */
    val connected: Boolean
}

/** Пока сервера нет: эфир идёт «на телефоне» — интерфейс, зрители и чат из демо. */
object DemoLiveTransport : LiveTransport {
    override fun publish(url: String, room: String, token: String) {}
    override fun join(url: String, room: String, token: String) {}
    override fun leave() {}
    override val connected: Boolean = false
}

object Lives {
    const val MAX_TITLE = 80
    const val MAX_COMMENT = 200
    /** Сколько комментариев держать на экране эфира. */
    const val KEEP_COMMENTS = 100

    /** «12:05» или «1:02:05». */
    fun clock(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = (s % 60).toString().padStart(2, '0')
        return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$sec" else "$m:$sec"
    }

    /** Итог эфира: «Эфир шёл 12:05 · зрителей до 34 · ♥ 210 · 18 комментариев». */
    fun summary(s: LiveStream, now: Long): String =
        tf("Эфир шёл %s", clock(s.durationMs(now))) + " · " +
            tf("зрителей до %s", s.peakViewers) + " · ♥ " + s.hearts + " · " +
            plural(s.comments.size, "комментарий", "комментария", "комментариев")

    /** Добавить комментарий и не держать лишнего. */
    fun addComment(s: LiveStream, c: LiveComment): LiveStream = s.copy(comments = (s.comments + c).takeLast(KEEP_COMMENTS))

    /** Пришли или ушли зрители: пик запоминается. */
    fun viewers(s: LiveStream, delta: Int): LiveStream {
        val v = (s.viewers + delta).coerceAtLeast(0)
        return s.copy(viewers = v, peakViewers = maxOf(s.peakViewers, v))
    }

    val demoTitles = listOf("Вечерняя прогулка по городу", "Готовлю ужин — заходите", "Разбираю новые пластинки", "Отвечаю на вопросы", "Закат с крыши", "Рисую в прямом эфире")
    val demoComments = listOf("Привет!", "Как красиво", "Откуда эфир?", "🔥🔥🔥", "Давно ждала", "Покажи ещё", "Звук отличный", "Ура, эфир!", "Сохраню себе", "❤️")

    fun demoTitle(seed: Int): String = t(demoTitles[Math.floorMod(seed, demoTitles.size)])
    fun demoComment(seed: Int): String = t(demoComments[Math.floorMod(seed, demoComments.size)])
}
