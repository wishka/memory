package ru.wishka.memory.data

import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Итоги за месяц или за год. */
enum class RecapPeriod { Month, Year }

/** Плитка итогов: «🎬 4 фильма», «🏃 23 км». */
data class RecapTile(val emoji: String, val value: String, val label: String)

/**
 * Итоги: личная статистика за месяц или год — сколько фильмов, книг,
 * километров, мест, записей и отметок «нравится». Считаются из своих
 * записей и чек-инов на телефоне. Те же правила — в Recap.swift.
 */
data class Recap(
    val period: RecapPeriod,
    val year: Int,
    /** 1…12 для месяца; 0 — итоги года. */
    val month: Int,
    val tiles: List<RecapTile>,
    val checkIns: Int,
    val posts: Int,
    /** Самое частое занятие: «🎬 Смотрю фильм»; null — чек-инов не было. */
    val favorite: String?,
) {
    val title: String
        get() = if (period == RecapPeriod.Year) tf("%s год", year) else "${t(MONTHS[month - 1])} $year"

    val empty: Boolean get() = tiles.isEmpty()

    /** Ключ периода: «2026-09», «2026». */
    val key: String get() = if (period == RecapPeriod.Year) "$year" else "$year-${month.toString().padStart(2, '0')}"

    /** Две главные цифры — для уведомления: «4 фильма, 23 км». */
    val headline: String get() = tiles.take(2).joinToString(", ") { "${it.value} ${it.label}" }

    companion object {
        val MONTHS = listOf(
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
        )

        /**
         * Посчитать итоги. Свои записи без даты (демо) не считаются: у них
         * нет настоящего времени.
         */
        fun compute(
            me: User,
            posts: List<Post>,
            prisms: List<Prism>,
            period: RecapPeriod,
            year: Int,
            month: Int,
            zone: ZoneId = ZoneId.systemDefault(),
        ): Recap {
            fun inPeriod(ms: Long): Boolean {
                if (ms <= 0) return false
                val d = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
                return d.year == year && (period == RecapPeriod.Year || d.monthValue == month)
            }
            val mine = posts.filter { it.author.id == me.id && inPeriod(it.createdAt) }
            val checks = mine.mapNotNull { it.checkIn }
            fun of(vararg ids: String) = checks.filter { it.activity in ids }
            fun distinctCount(list: List<CheckIn>) =
                list.map { it.subject.trim().lowercase() }.let { s -> s.filter { it.isNotEmpty() }.toSet().size + s.count { it.isEmpty() } }
            fun metric(m: RecapMetric) = checks.filter { it.builtIn?.metric == m }

            val tiles = mutableListOf<RecapTile>()
            fun add(emoji: String, n: Int, one: String, few: String, many: String) {
                if (n > 0) tiles += RecapTile(emoji, "$n", plural(n, one, few, many).substringAfter(' '))
            }
            add("🎬", of("movie").size, "фильм", "фильма", "фильмов")
            add("📺", distinctCount(of("series")), "сериал", "сериала", "сериалов")
            add("📖", distinctCount(of("book")), "книга", "книги", "книг")
            val km = checks.filter { isKilometers(it.unit) }.sumOf { it.amount }
            if (km > 0) tiles += RecapTile("🏃", amountNumber(km), t("км"))
            val pages = of("book").sumOf { it.amount }.toInt()
            add("📄", pages, "страница", "страницы", "страниц")
            add("💪", metric(RecapMetric.Workouts).size + metric(RecapMetric.Distance).size, "тренировка", "тренировки", "тренировок")
            add("📍", distinctCount(metric(RecapMetric.Places)), "место", "места", "мест")
            add("🎮", distinctCount(of("game")), "игра", "игры", "игр")
            // Свои чек-ины: сумма в своей единице или сколько раз
            checks.filter { it.builtIn == null && !isKilometers(it.unit) }.groupBy { it.activity }.values.forEach { list ->
                val c = list.last()
                val sum = list.sumOf { it.amount }
                tiles += if (sum > 0 && c.unit.isNotBlank()) RecapTile(c.emoji, amountNumber(sum), "${c.unit} · ${c.title}")
                else RecapTile(c.emoji, "${list.size}", "× ${c.title}")
            }
            add("✍️", mine.size, "запись", "записи", "записей")
            add("❤️", mine.sumOf { it.likes }, "отметка «нравится»", "отметки «нравится»", "отметок «нравится»")
            add("🎞️", prisms.count { it.author.id == me.id && inPeriod(it.createdAt) }, "ролик PRISM", "ролика PRISM", "роликов PRISM")

            val favorite = checks.groupBy { it.activity }.maxByOrNull { it.value.size }?.value?.first()
                ?.let { "${it.emoji} ${it.displayTitle}" }
            return Recap(period, year, month, tiles, checks.size, mine.size, favorite)
        }

        /** Итоги прошлого месяца — их показываем и о них напоминаем в начале месяца. */
        fun previousMonth(today: LocalDate = LocalDate.now()): Pair<Int, Int> =
            today.minusMonths(1).let { it.year to it.monthValue }
    }
}
