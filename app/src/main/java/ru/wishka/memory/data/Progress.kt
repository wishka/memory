package ru.wishka.memory.data

import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf

/**
 * Прогрессия: вчерашние действия меняют завтрашние возможности. Не значки
 * ради значков, а права: нашли три клада — можете прятать свои, провели
 * пять занятий — вас первым видят в подборе. Та же логика — в Progress.swift.
 */
enum class Unlock(val emoji: String, private val ru: String, private val howRu: String, private val gives: String, val need: Int) {
    HideCache("💎", "Прятать клады", "Найдите %s клада", "Свои клады на карте района", 3),
    OwnMission("🗺️", "Своя миссия", "Отметьте %s находок в миссиях", "Задание для своего района", 5),
    OwnGoal("🎯", "Своя общая цель", "Внесите вклад в %s общие цели", "Предложить сообществу свою цель", 2),
    Mentor("🎓", "Наставник", "Проведите %s занятий в банке времени", "Вас первым видят в подборе навыков", 5),
    Keeper("📚", "Хранитель книг", "Передайте дальше %s книги", "Ваши книги — первыми в обмене", 3);

    val title: String get() = t(ru)
    val how: String get() = tf(howRu, need)
    val reward: String get() = t(gives)
}

/** Где вы на пути к праву: сделано из нужного. */
data class UnlockProgress(val unlock: Unlock, val done: Int) {
    val open: Boolean get() = done >= unlock.need
    val left: Int get() = (unlock.need - done).coerceAtLeast(0)
    val fraction: Float get() = (done.toFloat() / unlock.need).coerceIn(0f, 1f)
}

fun unlockProgress(together: TogetherState, meId: Int): List<UnlockProgress> = Unlock.entries.map { u ->
    UnlockProgress(
        u,
        when (u) {
            Unlock.HideCache -> together.caches.count { it.foundByMe }
            Unlock.OwnMission -> together.missions.sumOf { it.mineCount(meId) }
            Unlock.OwnGoal -> together.goals.count { it.mine > 0 }
            Unlock.Mentor -> together.deals.count { it.status == DealStatus.Done && it.give.isNotEmpty() }
            Unlock.Keeper -> together.shares.count { it.kind == ShareKind.Book && meId in it.history }
        },
    )
}

fun TogetherState.unlocked(u: Unlock, meId: Int): Boolean = unlockProgress(this, meId).first { it.unlock == u }.open

/** Километры, которые открывают маршруты: бег, ходьба, велосипед. */
fun movedKm(checkIns: List<Dated>): Double =
    checkIns.filter { it.checkIn.activity in setOf("run", "walk", "bike") && isKilometers(it.checkIn.unit) }.sumOf { it.checkIn.amount }

// ---------- Отклик на чек-ин: действие → значимая награда → предвкушение ----------

/** Строка отклика: значок и текст. */
data class EchoLine(val emoji: String, val text: String)

/** Что изменилось от чек-ина: полка, общие цели, план, что откроется дальше, совет. */
data class CheckInEcho(val checkIn: CheckIn, val lines: List<EchoLine>)

/**
 * Собрать отклик. before/after — полка и «Вместе» до и после чек-ина;
 * friendName — имя по номеру (для совета «Вера поставила 5★»).
 */
fun checkInEcho(
    c: CheckIn,
    shelfBefore: Shelf,
    shelfAfter: Shelf,
    togetherBefore: TogetherState,
    togetherAfter: TogetherState,
    plan: RunPlan?,
    kmBefore: Double,
    kmAfter: Double,
    recommendation: Recommendation?,
    friendName: (Int) -> String,
): CheckInEcho {
    val lines = mutableListOf<EchoLine>()
    // Полка
    when (shelfKind(c.activity)) {
        "film" -> {
            val item = shelfAfter.films.firstOrNull { it.key == shelfKey(c.activity, c.subject) }
            if (item != null) {
                val stars = if (item.rating > 0) " · " + "★".repeat(item.rating) else ""
                lines += EchoLine("🎬", if (item.times > 1) tf("«%s» — на полке, смотрите уже %s-й раз%s", item.subject, item.times, stars) else tf("«%s» — на вашей полке%s", item.subject, stars))
            }
            lines += EchoLine("📚", tf("Фильмов на полке: %s", shelfAfter.watched.size))
        }
        "book" -> {
            val item = shelfAfter.books.firstOrNull { it.key == shelfKey(c.activity, c.subject) }
            if (item != null) lines += EchoLine("📖", tf("«%s» — %s", item.subject, BookStatus.of(item.status)?.title?.lowercase() ?: ""))
            val done = shelfAfter.books(BookStatus.Done).size
            if (done > shelfBefore.books(BookStatus.Done).size) lines += EchoLine("🏁", tf("Прочитано книг: %s", done))
        }
    }
    if (c.activity in TIMED_ACTIVITIES && c.amount > 0) {
        val before = shelfBefore.sport.firstOrNull { it.activity == c.activity }
        val after = shelfAfter.sport.firstOrNull { it.activity == c.activity }
        if (after != null) {
            lines += EchoLine(c.emoji, tf("За месяц: %s км", amountNumber(Math.round(after.monthKm * 10) / 10.0)))
            if (before != null && before.count > 0 && after.longestKm > before.longestKm) lines += EchoLine("🏆", tf("Новый рекорд дистанции: %s км", amountNumber(after.longestKm)))
            val pb = before?.bestPace
            val pa = after.bestPace
            if (pb != null && pa != null && pa < pb) lines += EchoLine("⚡", tf("Лучший темп: %s /км", paceClock(pa)))
        }
    }
    // Общие цели
    togetherAfter.goals.forEach { g ->
        val was = togetherBefore.goals.firstOrNull { it.id == g.id }?.mine ?: 0.0
        if (g.mine > was) lines += EchoLine(g.emoji, tf("+%s %s в цель «%s»", amountNumber(Math.round((g.mine - was) * 10) / 10.0), t(g.unit), g.title))
    }
    // План недели
    if (plan != null && c.activity == "run") {
        lines += if (plan.done) EchoLine("✅", tf("План недели выполнен: %s из %s км", amountNumber(plan.doneKm), amountNumber(plan.targetKm)))
        else EchoLine("📈", tf("До плана недели — %s км", amountNumber(Math.round(plan.leftKm * 10) / 10.0)))
    }
    // Что открылось и что откроется
    if (c.activity in setOf("run", "walk", "bike")) togetherAfter.missions.filter { it.unlockKm > 0 }.forEach { m ->
        if (kmBefore < m.unlockKm && kmAfter >= m.unlockKm) lines += EchoLine("🔓", tf("Открыта миссия «%s»", m.title))
        else if (kmAfter < m.unlockKm) lines += EchoLine("🔒", tf("Ещё %s км — откроется миссия «%s»", amountNumber(Math.round((m.unlockKm - kmAfter) * 10) / 10.0), m.title))
    }
    // Совет из вкуса друзей
    recommendation?.let { r ->
        lines += EchoLine(r.emoji, tf("Похоже на ваше: «%s» — %s ставит %s★", r.subject, friendName(r.personId), r.theirRating))
    }
    return CheckInEcho(c, lines.take(6))
}
