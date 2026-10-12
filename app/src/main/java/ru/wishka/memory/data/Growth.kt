package ru.wishka.memory.data

import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Петли роста: приглашение, привязанное к действию («дорисуй мой
 * рисунок», «возьми книгу»), и журнал событий, из которого считается
 * K-фактор. Пока нет сервера, журнал живёт на телефоне; сервер потом
 * заберёт его как есть. Та же логика — в Growth.swift.
 */

/** К чему зовут. ru — что сделать: «дорисовать рисунок». */
enum class InviteAction(val id: String, private val ru: String) {
    App("app", "присоединиться к Memory"),
    Drawing("drawing", "дорисовать общий рисунок"),
    Book("book", "взять книгу из книжного обмена"),
    Outing("outing", "пойти на вылазку"),
    Lesson("lesson", "обменяться навыками"),
    Shelf("shelf", "посмотреть мою полку"),
    Journey("journey", "поддержать мой челлендж советом"),
    Cache("cache", "найти мой клад");

    val title: String get() = t(ru)

    companion object {
        fun of(id: String): InviteAction = entries.firstOrNull { it.id == id } ?: App
    }
}

/** Приглашение: чей код, к чему и к какому объекту (номер рисунка, книги…). */
data class Invite(val code: String, val action: InviteAction, val targetId: String = "", val from: String = "")

object Invites {
    /**
     * Страница-приглашение (GitHub Pages репозитория, папка docs/invite):
     * открывает приложение, а если его нет — объясняет, как поставить.
     */
    const val WEB = "https://wishka.github.io/memory/invite/"
    const val SCHEME = "memory"

    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** Свой код приглашения: шесть знаков, всегда один и тот же для логина. */
    fun code(login: String): String {
        var h = 1125899906842597L
        login.lowercase().forEach { h = 31 * h + it.code }
        val v = h and Long.MAX_VALUE
        return (0 until 6).map { ALPHABET[((v shr (it * 5)) and 31).toInt()] }.joinToString("")
    }

    private fun query(i: Invite): String = listOf(
        "c" to i.code, "a" to i.action.id, "id" to i.targetId, "n" to i.from,
    ).filter { it.second.isNotEmpty() }.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }

    /** Ссылка для мессенджера: открывается в браузере и ведёт в приложение. */
    fun webLink(i: Invite): String = WEB + "?" + query(i)

    /** Ссылка прямо в приложение (её открывает страница-приглашение). */
    fun appLink(i: Invite): String = "$SCHEME://invite?" + query(i)

    /** Разобрать ссылку: и memory://invite?…, и https://…/invite/?…; null — не приглашение. */
    fun parse(link: String): Invite? {
        val s = link.trim()
        val isApp = s.startsWith("$SCHEME://invite")
        val isWeb = s.startsWith(WEB) || s.startsWith(WEB.removeSuffix("/"))
        if (!isApp && !isWeb) return null
        val q = s.substringAfter('?', "").substringBefore('#')
        val params = q.split('&').filter { '=' in it }.associate {
            it.substringBefore('=') to runCatching { URLDecoder.decode(it.substringAfter('='), "UTF-8") }.getOrDefault("")
        }
        val code = params["c"].orEmpty().uppercase().filter { it in ALPHABET }
        if (code.length != 6) return null
        return Invite(code, InviteAction.of(params["a"].orEmpty()), params["id"].orEmpty().take(40), params["n"].orEmpty().take(40))
    }

    /** Текст сообщения: «Андрей зовёт вас дорисовать общий рисунок «Город» в Memory: ссылка». */
    fun message(i: Invite, title: String = ""): String {
        val what = if (title.isNotEmpty()) "${i.action.title} «$title»" else i.action.title
        return tf("%s зовёт вас %s в Memory", i.from, what) + ": " + webLink(i)
    }
}

// ---------- Журнал событий ----------

enum class GrowthEventType {
    /** Отправили приглашение (detail — к чему). */
    InviteSent,
    /** Открыли приложение по чужой ссылке (detail — код пригласившего). */
    InviteOpened,
    /** Зарегистрировались по приглашению. */
    JoinedByInvite,
    /** Поделились карточкой (detail — что за карточка). */
    CardShared,
    /** Знакомство «выберите любимое» пройдено. */
    OnboardingDone,
    CheckIn,
    /** Предложили идею или сообщили об ошибке. */
    Feedback,
}

data class GrowthEvent(val type: GrowthEventType, val at: Long, val detail: String = "")

/** Журнал не растёт бесконечно: последние 500 событий. */
const val GROWTH_LOG_LIMIT = 500

fun List<GrowthEvent>.logged(e: GrowthEvent): List<GrowthEvent> = (this + e).takeLast(GROWTH_LOG_LIMIT)

/**
 * Цифры роста. K = i × c: i — сколько приглашений отправляет человек,
 * c — какая доля приглашённых приходит. i считается на телефоне; c
 * знает только сервер (кто пришёл по вашим ссылкам), поэтому до него —
 * оценка по средней конверсии, и честно помечено.
 */
data class GrowthStats(
    val invitesSent: Int,
    val cardsShared: Int,
    val checkIns: Int,
    val joinedByInvite: Boolean,
    /** Приглашения по видам действий: «drawing» → 3. */
    val byAction: Map<String, Int>,
) {
    /** Оценка K при конверсии c (по умолчанию 10%: столько из приглашённых обычно приходит). */
    fun kEstimate(conversion: Double = 0.1): Double = invitesSent * conversion
}

fun growthStats(events: List<GrowthEvent>): GrowthStats = GrowthStats(
    invitesSent = events.count { it.type == GrowthEventType.InviteSent },
    cardsShared = events.count { it.type == GrowthEventType.CardShared },
    checkIns = events.count { it.type == GrowthEventType.CheckIn },
    joinedByInvite = events.any { it.type == GrowthEventType.JoinedByInvite },
    byAction = events.filter { it.type == GrowthEventType.InviteSent }.groupingBy { it.detail.substringBefore(':') }.eachCount(),
)

/** Предложить идею или сообщить об ошибке: новая задача в репозитории приложения. */
object Feedback {
    const val ISSUES = "https://github.com/wishka/memory/issues/new"

    fun link(idea: Boolean, text: String, version: String): String {
        val title = (if (idea) "Идея: " else "Ошибка: ") + text.lineSequence().first().take(80)
        val body = text + "\n\n—\nMemory " + version
        return ISSUES + "?title=" + URLEncoder.encode(title, "UTF-8") + "&body=" + URLEncoder.encode(body, "UTF-8") +
            "&labels=" + URLEncoder.encode(if (idea) "idea" else "bug", "UTF-8")
    }
}
