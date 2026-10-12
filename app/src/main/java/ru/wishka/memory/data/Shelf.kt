package ru.wishka.memory.data

import ru.wishka.memory.i18n.t
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Полка — профиль, который собирается сам: из чек-инов («посмотрел
 * фильм», «пробежал 7 км») и из отметок без записи в ленте (любимое при
 * знакомстве, «хочу посмотреть» из рекомендаций). Человек ничего не
 * заполняет вручную. Та же логика — в Shelf.swift.
 */

/** Отметка на полке без записи в ленте. activity — «movie» или «book». status — как у книги; у фильма «want» — в планах. */
data class ShelfMark(
    val activity: String,
    val subject: String,
    val rating: Int = 0,
    val status: String = "",
    val at: Long = 0,
) {
    val key: String get() = shelfKey(activity, subject)
}

/** Фильм и сериал — одна полка «Кино». */
fun shelfKind(activity: String): String = when (activity) {
    "movie", "series" -> "film"
    "book" -> "book"
    else -> activity
}

fun shelfKey(activity: String, subject: String): String = shelfKind(activity) + ":" + subjectKey(subject)

/** Чек-ин с моментом, когда его отметили. */
data class Dated(val checkIn: CheckIn, val at: Long)

/** Фильм или книга на полке. */
data class ShelfItem(
    val activity: String,
    val subject: String,
    val emoji: String,
    /** Последняя поставленная оценка; 0 — без оценки. */
    val rating: Int,
    /** Книга: последний статус; фильм: «want» — пока только в планах. */
    val status: String,
    /** Сколько раз отмечали (пересмотр, чтение по главам). */
    val times: Int,
    val lastAt: Long,
    /** Книга: прочитано страниц по чек-инам. */
    val pages: Double = 0.0,
) {
    val key: String get() = shelfKey(activity, subject)
    val planned: Boolean get() = status == BookStatus.Want.id
}

/** Бег, велосипед, ходьба, плавание — суммы и рекорды. */
data class SportStats(
    val activity: String,
    val emoji: String,
    val count: Int,
    val totalKm: Double,
    val monthKm: Double,
    val longestKm: Double,
    /** Лучший темп, секунд на километр; null — время ни разу не указали. */
    val bestPace: Int?,
    val minutes: Int,
)

/** Место: кофейня, ресторан, концерт, поездка. */
data class PlaceItem(val subject: String, val emoji: String, val count: Int, val lastAt: Long, val rating: Int)

data class Shelf(
    val films: List<ShelfItem> = emptyList(),
    val books: List<ShelfItem> = emptyList(),
    val sport: List<SportStats> = emptyList(),
    val places: List<PlaceItem> = emptyList(),
    /** Свои и прочие занятия: значок, название, сколько раз. */
    val other: List<Triple<String, String, Int>> = emptyList(),
) {
    val watched: List<ShelfItem> get() = films.filterNot { it.planned }
    val plannedFilms: List<ShelfItem> get() = films.filter { it.planned }
    fun books(status: BookStatus): List<ShelfItem> = books.filter { it.status == status.id }
    /** Ключи всего, что уже на полке: в рекомендациях это не показываем. */
    val keys: Set<String> get() = (films + books).map { it.key }.toSet()
    val isEmpty: Boolean get() = films.isEmpty() && books.isEmpty() && sport.isEmpty() && places.isEmpty() && other.isEmpty()

    /** Оценки для подбора вкуса: всё, что оценено. */
    val rated: List<Rated>
        get() = (films + books).filter { it.rating > 0 }.map { Rated(shelfKind(it.activity), it.subject, it.rating) }

    companion object {
        private val PLACE_ACTIVITIES = setOf("coffee", "food", "place", "travel", "concert")

        fun build(checkIns: List<Dated>, marks: List<ShelfMark>, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Shelf {
            val sorted = checkIns.sortedBy { it.at }
            // ---- Кино и книги: чек-ины и отметки вперемешку, по времени
            data class Event(val activity: String, val subject: String, val emoji: String, val rating: Int, val status: String, val at: Long, val pages: Double, val counts: Boolean, val fromCheckIn: Boolean = false)
            val events = sorted.filter { shelfKind(it.checkIn.activity) in setOf("film", "book") && it.checkIn.subject.isNotBlank() }.map {
                val c = it.checkIn
                val status = when {
                    c.activity != "book" -> ""
                    c.status.isNotEmpty() -> c.status
                    else -> BookStatus.Reading.id
                }
                Event(c.activity, c.subject.trim(), c.emoji, c.rating, status, it.at, if (c.activity == "book" && isPages(c.unit)) c.amount else 0.0, true, fromCheckIn = true)
            } + marks.filter { it.subject.isNotBlank() }.map {
                val emoji = if (shelfKind(it.activity) == "book") "📖" else "🎬"
                Event(it.activity, it.subject.trim(), emoji, it.rating, it.status, it.at, 0.0, it.status != BookStatus.Want.id)
            }
            val items = events.sortedBy { it.at }.groupBy { shelfKey(it.activity, it.subject) }.map { (_, list) ->
                val last = list.last()
                // Посмотрели фильм после «хочу посмотреть» — он больше не в планах
                val watchedFilm = shelfKind(last.activity) == "film" && list.any { it.counts && it.status != BookStatus.Want.id }
                ShelfItem(
                    activity = list.firstOrNull { it.counts }?.activity ?: last.activity,
                    subject = list.first().subject,
                    emoji = list.firstOrNull { it.counts }?.emoji ?: last.emoji,
                    rating = list.lastOrNull { it.rating > 0 }?.rating ?: 0,
                    status = when {
                        shelfKind(last.activity) == "book" -> list.lastOrNull { it.status.isNotEmpty() }?.status ?: BookStatus.Reading.id
                        watchedFilm -> ""
                        else -> last.status
                    },
                    // Отметка без записи («Смотрел», оценка с полки) — это «хотя бы раз», а не ещё один просмотр
                    times = list.count { it.fromCheckIn && it.counts && it.status != BookStatus.Want.id }
                        .coerceAtLeast(if (list.any { !it.fromCheckIn && it.counts && it.status != BookStatus.Want.id }) 1 else 0),
                    lastAt = last.at,
                    pages = list.sumOf { it.pages },
                )
            }.sortedByDescending { it.lastAt }
            // ---- Спорт
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val sport = sorted.filter { it.checkIn.activity in TIMED_ACTIVITIES || it.checkIn.activity in setOf("gym", "yoga") }
                .groupBy { it.checkIn.activity }.map { (activity, list) ->
                    val km = list.filter { isKilometers(it.checkIn.unit) }
                    SportStats(
                        activity = activity,
                        emoji = list.last().checkIn.emoji,
                        count = list.size,
                        totalKm = km.sumOf { it.checkIn.amount },
                        monthKm = km.filter { sameMonth(it.at, today, zone) }.sumOf { it.checkIn.amount },
                        longestKm = km.maxOfOrNull { it.checkIn.amount } ?: 0.0,
                        bestPace = if (activity == "bike") null else km.mapNotNull { paceSeconds(it.checkIn.amount, it.checkIn.minutes) }.minOrNull(),
                        minutes = list.sumOf { if (it.checkIn.unit == "мин") it.checkIn.amount.toInt() else it.checkIn.minutes },
                    )
                }.sortedByDescending { it.count }
            // ---- Места
            val places = sorted.filter { it.checkIn.activity in PLACE_ACTIVITIES && it.checkIn.subject.isNotBlank() }
                .groupBy { subjectKey(it.checkIn.subject) }.map { (_, list) ->
                    PlaceItem(list.first().checkIn.subject.trim(), list.last().checkIn.emoji, list.size, list.last().at, list.lastOrNull { it.checkIn.rating > 0 }?.checkIn?.rating ?: 0)
                }.sortedByDescending { it.lastAt }
            // ---- Остальное: свои чек-ины и прочие занятия
            val counted = setOf("film", "book") + TIMED_ACTIVITIES + setOf("gym", "yoga") + PLACE_ACTIVITIES
            val other = sorted.filter { shelfKind(it.checkIn.activity) !in counted && it.checkIn.activity !in counted }
                .groupBy { it.checkIn.activity }.map { (_, list) -> Triple(list.last().checkIn.emoji, list.last().checkIn.displayTitle, list.size) }
                .sortedByDescending { it.third }
            return Shelf(
                films = items.filter { shelfKind(it.activity) == "film" },
                books = items.filter { shelfKind(it.activity) == "book" },
                sport = sport,
                places = places,
                other = other,
            )
        }

        private fun isPages(unit: String) = unit.trim().lowercase().trimEnd('.') in setOf("стр", "pages", "p")

        private fun sameMonth(at: Long, today: LocalDate, zone: ZoneId): Boolean {
            val d = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
            return d.year == today.year && d.month == today.month
        }
    }
}

/** Свои чек-ины с датами — из своих записей. */
fun List<Post>.datedCheckIns(meId: Int): List<Dated> =
    filter { it.author.id == meId && it.checkIn != null }.map { Dated(it.checkIn!!, it.createdAt) }

/** «5:30» из секунд на километр. */
fun paceClock(secPerKm: Int): String = "%d:%02d".format(secPerKm / 60, secPerKm % 60)

// ---------- Воспоминания: «Год назад вы…» ----------

/** Воспоминание: своя запись и сколько с тех пор прошло. */
data class MemoryOfDay(val post: Post, val label: String, val ago: Long)

/**
 * Записи «в этот день»: год назад (и раньше), месяц назад, неделю назад.
 * Показываем самую давнюю группу — она ценнее: год важнее недели.
 */
fun memoriesOfDay(posts: List<Post>, meId: Int, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): List<MemoryOfDay> {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val mine = posts.filter { it.author.id == meId && it.createdAt > 0 }
    fun dateOf(p: Post) = Instant.ofEpochMilli(p.createdAt).atZone(zone).toLocalDate()
    val years = mine.mapNotNull { p ->
        val d = dateOf(p)
        val n = today.year - d.year
        if (n >= 1 && d.monthValue == today.monthValue && d.dayOfMonth == today.dayOfMonth) MemoryOfDay(p, yearsAgo(n), n * 365L) else null
    }
    if (years.isNotEmpty()) return years.sortedByDescending { it.ago }
    val months = mine.mapNotNull { p ->
        val d = dateOf(p)
        val n = ChronoUnit.MONTHS.between(d.withDayOfMonth(1), today.withDayOfMonth(1))
        if (n in 1..11 && d.dayOfMonth == today.dayOfMonth) MemoryOfDay(p, monthsAgo(n.toInt()), n * 30) else null
    }
    if (months.isNotEmpty()) return months.sortedByDescending { it.ago }
    return mine.filter { ChronoUnit.DAYS.between(dateOf(it), today) == 7L }.map { MemoryOfDay(it, t("Неделю назад"), 7) }
}

private fun yearsAgo(n: Int) = if (n == 1) t("Год назад") else plural(n, "год", "года", "лет") + " " + t("назад")
private fun monthsAgo(n: Int) = if (n == 1) t("Месяц назад") else plural(n, "месяц", "месяца", "месяцев") + " " + t("назад")
