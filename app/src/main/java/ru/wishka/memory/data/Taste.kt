package ru.wishka.memory.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/**
 * Вкус и рекомендации. Не «самое популярное», а то, что высоко оценили
 * люди, чьи оценки совпадают с вашими: этого не знает ни один чат-бот —
 * у него нет ни вашей полки, ни ваших друзей. Та же логика — в Taste.swift.
 */

/** Оценка: kind — «film» или «book». */
data class Rated(val kind: String, val subject: String, val rating: Int) {
    val key: String get() = kind + ":" + subjectKey(subject)
}

/** Фильм или книга из каталога знакомства. */
data class CatalogTitle(val kind: String, val title: String, val emoji: String, val by: String)

object Catalog {
    val films = listOf(
        CatalogTitle("film", "Интерстеллар", "🪐", "Кристофер Нолан"),
        CatalogTitle("film", "Дюна", "🏜️", "Дени Вильнёв"),
        CatalogTitle("film", "Прибытие", "🛸", "Дени Вильнёв"),
        CatalogTitle("film", "Бегущий по лезвию 2049", "🌆", "Дени Вильнёв"),
        CatalogTitle("film", "Солярис", "🌊", "Андрей Тарковский"),
        CatalogTitle("film", "Москва слезам не верит", "🎞️", "Владимир Меньшов"),
        CatalogTitle("film", "Брат", "🧥", "Алексей Балабанов"),
        CatalogTitle("film", "Унесённые призраками", "🐉", "Хаяо Миядзаки"),
        CatalogTitle("film", "Амели", "🍓", "Жан-Пьер Жёне"),
        CatalogTitle("film", "Начало", "🌀", "Кристофер Нолан"),
        CatalogTitle("film", "Ла-Ла Ленд", "🎹", "Дэмьен Шазелл"),
        CatalogTitle("film", "Паразиты", "🪜", "Пон Джун-хо"),
        CatalogTitle("film", "Вечное сияние чистого разума", "💭", "Мишель Гондри"),
        CatalogTitle("film", "Остров собак", "🐕", "Уэс Андерсон"),
        CatalogTitle("film", "Отель «Гранд Будапешт»", "🏨", "Уэс Андерсон"),
        CatalogTitle("film", "Матрица", "💊", "Вачовски"),
        CatalogTitle("film", "Сталкер", "🌿", "Андрей Тарковский"),
        CatalogTitle("film", "Всё везде и сразу", "🥯", "Дэниелы"),
    )
    val books = listOf(
        CatalogTitle("book", "Мастер и Маргарита", "🐈‍⬛", "Михаил Булгаков"),
        CatalogTitle("book", "Пикник на обочине", "🔮", "Стругацкие"),
        CatalogTitle("book", "Задача трёх тел", "☀️", "Лю Цысинь"),
        CatalogTitle("book", "Маленький принц", "🌹", "Антуан де Сент-Экзюпери"),
        CatalogTitle("book", "1984", "👁️", "Джордж Оруэлл"),
        CatalogTitle("book", "Сто лет одиночества", "🦋", "Габриэль Гарсиа Маркес"),
        CatalogTitle("book", "Понедельник начинается в субботу", "🧪", "Стругацкие"),
        CatalogTitle("book", "Цветы для Элджернона", "🐭", "Дэниел Киз"),
        CatalogTitle("book", "Дюна", "🏜️", "Фрэнк Герберт"),
        CatalogTitle("book", "Гарри Поттер и философский камень", "⚡", "Джоан Роулинг"),
        CatalogTitle("book", "Преступление и наказание", "🪓", "Фёдор Достоевский"),
        CatalogTitle("book", "Марсианин", "🥔", "Энди Уир"),
        CatalogTitle("book", "Атлант расправил плечи", "🗽", "Айн Рэнд"),
        CatalogTitle("book", "Норвежский лес", "🌲", "Харуки Мураками"),
        CatalogTitle("book", "Лавр", "🕯️", "Евгений Водолазкин"),
        CatalogTitle("book", "Проект «Аве Мария»", "🕷️", "Энди Уир"),
    )
    val all: List<CatalogTitle> get() = films + books

    fun find(kind: String, subject: String): CatalogTitle? =
        all.firstOrNull { it.kind == kind && subjectKey(it.title) == subjectKey(subject) }

    fun emoji(kind: String, subject: String): String = find(kind, subject)?.emoji ?: if (kind == "book") "📖" else "🎬"
}

/** Оценки демо-друзей, пока нет сервера: у каждого свой вкус. */
object DemoTaste {
    private fun f(s: String, r: Int) = Rated("film", s, r)
    private fun b(s: String, r: Int) = Rated("book", s, r)

    val ratings: Map<Int, List<Rated>> = mapOf(
        // Вера: космос, тишина, Вильнёв и Тарковский
        2 to listOf(f("Интерстеллар", 5), f("Дюна", 5), f("Прибытие", 5), f("Солярис", 5), f("Сталкер", 4), f("Бегущий по лезвию 2049", 4), f("Ла-Ла Ленд", 2),
            b("Задача трёх тел", 5), b("Пикник на обочине", 5), b("Маленький принц", 4), b("Проект «Аве Мария»", 5)),
        // Марк: музыка, мрачное и умное
        3 to listOf(f("Бегущий по лезвию 2049", 5), f("Матрица", 5), f("Ла-Ла Ленд", 4), f("Брат", 5), f("Паразиты", 4), f("Амели", 2),
            b("1984", 5), b("Понедельник начинается в субботу", 4), b("Атлант расправил плечи", 2)),
        // Алина: свет, цвет, Миядзаки и Андерсон
        4 to listOf(f("Унесённые призраками", 5), f("Амели", 5), f("Отель «Гранд Будапешт»", 5), f("Остров собак", 4), f("Вечное сияние чистого разума", 5), f("Брат", 2),
            b("Маленький принц", 5), b("Сто лет одиночества", 5), b("Норвежский лес", 4)),
        // Тимур: приключения и выживание
        5 to listOf(f("Интерстеллар", 5), f("Начало", 5), f("Матрица", 4), f("Дюна", 4), f("Всё везде и сразу", 3),
            b("Марсианин", 5), b("Проект «Аве Мария»", 5), b("Дюна", 4), b("Гарри Поттер и философский камень", 4)),
        // София: путешествия, плёнка, большие романы
        6 to listOf(f("Солярис", 4), f("Москва слезам не верит", 5), f("Амели", 4), f("Паразиты", 5), f("Всё везде и сразу", 5),
            b("Сто лет одиночества", 5), b("Мастер и Маргарита", 5), b("Лавр", 5), b("Пикник на обочине", 4), b("Цветы для Элджернона", 5)),
    )
}

/** Насколько вкус совпадает: сколько общих оценок и доля совпадения 0..1. */
data class TasteMatch(val common: Int, val similarity: Double) {
    /** «совпадает на 87%»: показываем, только если общих хотя бы две. */
    val percent: Int get() = (similarity * 100).toInt()
}

fun tasteMatch(mine: List<Rated>, theirs: List<Rated>): TasteMatch {
    val my = mine.associateBy { it.key }
    val pairs = theirs.mapNotNull { r -> my[r.key]?.let { it.rating to r.rating } }
    if (pairs.isEmpty()) return TasteMatch(0, 0.0)
    return TasteMatch(pairs.size, pairs.sumOf { (a, b) -> 1.0 - abs(a - b) / 4.0 } / pairs.size)
}

/** Рекомендация: что, кто больше всех «за» и почему. */
data class Recommendation(
    val kind: String,
    val subject: String,
    val emoji: String,
    val score: Double,
    /** Друг, чья оценка весит больше всех. */
    val personId: Int,
    val theirRating: Int,
    val match: TasteMatch,
    /** Сколько друзей поставили 4–5. */
    val friends: Int,
) {
    val key: String get() = kind + ":" + subjectKey(subject)
}

/**
 * Подборка: то, что друзья оценили на 4–5, а у вас ещё нет. Вес друга —
 * совпадение вкуса (чем больше общих оценок, тем увереннее); у новичка
 * без оценок — небольшой общий вес, чтобы подборка не была пустой.
 */
fun recommend(
    mine: List<Rated>,
    exclude: Set<String>,
    friends: Map<Int, List<Rated>>,
    kind: String,
    limit: Int = 6,
): List<Recommendation> {
    data class Vote(val personId: Int, val rating: Int, val weight: Double, val match: TasteMatch)
    val votes = mutableMapOf<String, MutableList<Vote>>()
    val subjects = mutableMapOf<String, String>()
    for ((personId, list) in friends) {
        val match = tasteMatch(mine, list)
        val confidence = minOf(match.common, 3) / 3.0
        val weight = 0.15 + match.similarity * confidence
        // Друг с противоположным вкусом не советует
        if (match.common >= 2 && match.similarity < 0.5) continue
        for (r in list.filter { it.kind == kind && it.rating >= 4 && it.key !in exclude }) {
            votes.getOrPut(r.key) { mutableListOf() }.add(Vote(personId, r.rating, weight, match))
            subjects.putIfAbsent(r.key, r.subject)
        }
    }
    return votes.map { (key, list) ->
        val top = list.maxWith(compareBy<Vote>({ it.weight }, { it.rating }, { -it.personId }))
        val subject = subjects.getValue(key)
        Recommendation(kind, subject, Catalog.emoji(kind, subject), list.sumOf { it.weight * (it.rating - 3) }, top.personId, top.rating, top.match, list.size)
    }.sortedWith(compareByDescending<Recommendation> { it.score }.thenBy { it.subject }).take(limit)
}

// ---------- План бега на неделю ----------

enum class PlanReason { Start, Grow, Return }

/**
 * План на неделю из прошлых четырёх: прирост не больше 10% к прошлой
 * неделе — так растут без травм. Пропустили неделю — начинаем мягче.
 */
data class RunPlan(val targetKm: Double, val doneKm: Double, val weeksKm: List<Double>, val reason: PlanReason) {
    val leftKm: Double get() = (targetKm - doneKm).coerceAtLeast(0.0)
    val done: Boolean get() = doneKm >= targetKm
    val progress: Float get() = if (targetKm <= 0) 0f else (doneKm / targetKm).toFloat().coerceIn(0f, 1f)
}

fun runPlan(checkIns: List<Dated>, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): RunPlan {
    val monday = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val runs = checkIns.filter { it.checkIn.activity == "run" && isKilometers(it.checkIn.unit) && it.checkIn.amount > 0 }
    fun weekKm(weeksBack: Long): Double {
        val start = monday.minusWeeks(weeksBack)
        val end = start.plusWeeks(1)
        return runs.filter {
            val d = Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate()
            !d.isBefore(start) && d.isBefore(end)
        }.sumOf { it.checkIn.amount }
    }
    val weeks = (4L downTo 1L).map { weekKm(it) }
    val last = weeks.last()
    val avg = weeks.average()
    fun half(x: Double) = Math.round(x * 2) / 2.0
    val (target, reason) = when {
        weeks.all { it == 0.0 } -> 5.0 to PlanReason.Start
        last > 0 -> half(last * 1.1).coerceAtLeast(last + 0.5) to PlanReason.Grow
        else -> half(avg * 0.8).coerceAtLeast(3.0) to PlanReason.Return
    }
    return RunPlan(target, weekKm(0), weeks, reason)
}
