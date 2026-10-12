package ru.wishka.memory.data

import ru.wishka.memory.i18n.t
import java.util.Locale

/**
 * Чек-ин — запись «чем я сейчас занят»: смотрю фильм, читаю книгу,
 * пробежал 5 км, я в кофейне. activity — номер занятия: встроенного
 * («movie», «run»…) или своего («custom-…»). Название, значок и единица
 * сохраняются в самой записи — она не сломается, если свой шаблон потом
 * удалят. Из чек-инов складываются Итоги месяца и года.
 */
data class CheckIn(
    val activity: String,
    val emoji: String,
    /** Название занятия: у встроенных — русская строка словаря, у своих — как ввёл человек. */
    val title: String,
    /** Что именно: фильм, книга, место. */
    val subject: String = "",
    /** Сколько: километры, страницы, минуты; 0 — не указано. */
    val amount: Double = 0.0,
    /** Единица: «км», «стр.», «мин» или своя. */
    val unit: String = "",
    /** Оценка 1–5 (фильм, книга, игра, место); 0 — без оценки. */
    val rating: Int = 0,
    /** Книга: «want» — хочу прочитать, «reading» — читаю, «done» — прочитал; "" — не указано. */
    val status: String = "",
    /** Сколько длилось, минуты (пробежка, заезд): из этого — темп; 0 — не указано. */
    val minutes: Int = 0,
) {
    val builtIn: CheckInActivity? get() = CHECK_IN_ACTIVITIES.firstOrNull { it.id == activity }

    /** Название на выбранном языке: встроенные переводятся, свои — как есть. */
    val displayTitle: String get() = if (builtIn != null) t(title) else title

    val displayUnit: String get() = if (builtIn != null && unit.isNotEmpty()) t(unit) else unit

    /** «7 км», «120 стр.»; "" — без количества. */
    val amountTitle: String
        get() = if (amount <= 0) "" else "${amountNumber(amount)} $displayUnit".trim()

    /** «5:30 /км» для бега и ходьбы, «18 км/ч» для велосипеда; "" — не посчитать. */
    val paceTitle: String get() = paceTitle(activity, amount, minutes, unit)

    /** «Прочитал», «Читаю», «Хочу прочитать»; "" — без статуса. */
    val statusTitle: String get() = BookStatus.of(status)?.title ?: ""

    /** Ключ для полки: одна и та же книга или фильм, как бы ни написали регистр. */
    val subjectKey: String get() = subjectKey(subject)
}

/** «Дюна » и «дюна» — одно и то же. */
fun subjectKey(subject: String): String = subject.trim().lowercase().replace('ё', 'е').replace(Regex("\\s+"), " ")

/** Статус книги в чек-ине. */
enum class BookStatus(val id: String, private val ru: String) {
    Want("want", "Хочу прочитать"),
    Reading("reading", "Читаю"),
    Done("done", "Прочитал");

    val title: String get() = t(ru)

    companion object {
        fun of(id: String): BookStatus? = entries.firstOrNull { it.id == id }
    }
}

/** Активности, где время превращается в темп или скорость. */
val TIMED_ACTIVITIES = setOf("run", "walk", "bike", "swim")

/** Что оценивают звёздами. */
val RATED_ACTIVITIES = setOf("movie", "series", "book", "game", "concert", "coffee", "food")

/** Темп: минуты на километр (бег, ходьба, плавание) или скорость (велосипед). */
fun paceTitle(activity: String, km: Double, minutes: Int, unit: String = "км"): String {
    if (km <= 0 || minutes <= 0 || !isKilometers(unit)) return ""
    return if (activity == "bike") {
        "${amountNumber(Math.round(km / (minutes / 60.0) * 10) / 10.0)} ${t("км/ч")}"
    } else {
        val secPerKm = Math.round(minutes * 60.0 / km).toInt()
        "%d:%02d /%s".format(secPerKm / 60, secPerKm % 60, t("км"))
    }
}

/** Секунды на километр; null — без времени или расстояния. */
fun paceSeconds(km: Double, minutes: Int): Int? = if (km > 0 && minutes > 0) Math.round(minutes * 60.0 / km).toInt() else null

/** «7», «7,5» — без лишних нулей. */
fun amountNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else String.format(Locale.US, "%.1f", value).trimEnd('0').trimEnd('.').let {
        if (ru.wishka.memory.i18n.L10n.english) it else it.replace('.', ',')
    }

/** Что считается в Итогах. */
enum class RecapMetric { Films, Series, Books, Distance, Workouts, Places, Music, Games, Food, Other }

/**
 * Встроенное занятие. title, hint и unit — русские строки словаря:
 * переводятся при показе.
 */
data class CheckInActivity(
    val id: String,
    val emoji: String,
    val title: String,
    /** Подсказка в поле «что именно». */
    val hint: String,
    /** Единица количества; "" — количество не спрашиваем. */
    val unit: String = "",
    val metric: RecapMetric = RecapMetric.Other,
)

/** Встроенные чек-ины — в том порядке, как в выборе. */
val CHECK_IN_ACTIVITIES = listOf(
    CheckInActivity("movie", "🎬", "Смотрю фильм", "Какой фильм?", metric = RecapMetric.Films),
    CheckInActivity("series", "📺", "Смотрю сериал", "Какой сериал?", metric = RecapMetric.Series),
    CheckInActivity("book", "📖", "Читаю книгу", "Какая книга?", "стр.", RecapMetric.Books),
    CheckInActivity("run", "🏃", "Бегаю", "Где бегаете?", "км", RecapMetric.Distance),
    CheckInActivity("bike", "🚴", "Катаюсь на велосипеде", "Куда едете?", "км", RecapMetric.Distance),
    CheckInActivity("walk", "🚶", "Гуляю", "Где гуляете?", "км", RecapMetric.Distance),
    CheckInActivity("swim", "🏊", "Плаваю", "Где плаваете?", "км", RecapMetric.Distance),
    CheckInActivity("gym", "🏋️", "Тренируюсь", "Что за тренировка?", "мин", RecapMetric.Workouts),
    CheckInActivity("yoga", "🧘", "Занимаюсь йогой", "Какая практика?", "мин", RecapMetric.Workouts),
    CheckInActivity("music", "🎧", "Слушаю музыку", "Что слушаете?", metric = RecapMetric.Music),
    CheckInActivity("concert", "🎤", "На концерте", "Чей концерт?", metric = RecapMetric.Places),
    CheckInActivity("game", "🎮", "Играю", "Во что играете?", metric = RecapMetric.Games),
    CheckInActivity("coffee", "☕", "Пью кофе", "Где?", metric = RecapMetric.Places),
    CheckInActivity("food", "🍽️", "Обедаю", "Где?", metric = RecapMetric.Food),
    CheckInActivity("cook", "🍳", "Готовлю", "Что готовите?", metric = RecapMetric.Food),
    CheckInActivity("travel", "✈️", "Путешествую", "Куда?", metric = RecapMetric.Places),
    CheckInActivity("place", "📍", "Я здесь", "Где вы?", metric = RecapMetric.Places),
    CheckInActivity("study", "📚", "Учусь", "Что изучаете?", "мин"),
    CheckInActivity("work", "💻", "Работаю", "Над чем?"),
    CheckInActivity("mood", "✨", "Настроение", "Какое настроение?"),
)

/**
 * Свой чек-ин: название, значок (эмодзи), единица количества и подсказка.
 * Хранится на телефоне вместе с аккаунтом.
 */
data class CheckInTemplate(
    val id: String,
    val emoji: String,
    val title: String,
    val unit: String = "",
    val hint: String = "",
) {
    fun checkIn(subject: String, amount: Double) =
        CheckIn(id, emoji, title, subject.trim(), if (unit.isBlank()) 0.0 else amount, unit.trim())
}

/** Готовые сценарии над полем записи: вместо «чистого листа» — одно нажатие. */
val QUICK_SCENARIOS = listOf("movie", "run", "book", "place")

fun CheckInActivity.checkIn(subject: String, amount: Double, rating: Int = 0, status: String = "", minutes: Int = 0) =
    CheckIn(
        id, emoji, title, subject.trim(), if (unit.isEmpty()) 0.0 else amount, unit,
        rating = if (id in RATED_ACTIVITIES) rating.coerceIn(0, 5) else 0,
        status = if (id == "book") status else "",
        minutes = if (id in TIMED_ACTIVITIES) minutes.coerceIn(0, 24 * 60) else 0,
    )

/** Сценарий быстрого чек-ина: встроенное занятие по номеру. */
fun activity(id: String): CheckInActivity? = CHECK_IN_ACTIVITIES.firstOrNull { it.id == id }

/** Эмодзи для своих чек-инов — на выбор. */
val CHECK_IN_EMOJI = listOf(
    "⭐", "🔥", "💪", "🎯", "🌿", "🧗", "⛷️", "🏂", "⚽", "🏀", "🎾", "🥾",
    "🎨", "📷", "🎸", "🎹", "✍️", "🧩", "🍷", "🍰", "🌙", "☀️", "🐾", "❤️",
)

/** Километры: «км», «km» в своей единице тоже считаются в Итогах. */
fun isKilometers(unit: String): Boolean = unit.trim().lowercase().trimEnd('.') in setOf("км", "km")
