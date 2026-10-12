package ru.wishka.memory.data

import ru.wishka.memory.i18n.t
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Вкладка «Вместе»: не соревнование, а общее дело. Восемь разделов —
 * общие цели, обмен навыками, дневники процесса, городские миссии, клады,
 * вылазки малой группой, рисунок по эстафете и обмен вещами без денег.
 *
 * Всё состояние — TogetherState: неизменяемое, правила — его методы
 * (возвращают новое состояние). Так их легко проверять тестами, а на
 * iPhone — повторить один в один (Together.swift).
 */
enum class TogetherSection(val ru: String, val emoji: String) {
    Challenges("Цели", "🎯"),
    Skills("Навыки", "🤝"),
    Journeys("Дневники", "📓"),
    Missions("Миссии", "🗺️"),
    Caches("Клады", "💎"),
    Outings("Вылазки", "🥾"),
    Drawing("Рисунок", "🎨"),
    Sharing("Обмен", "♻️"),
    ;

    val title: String get() = t(ru)
}

/** Точка на карте. */
data class LatLon(val lat: Double, val lon: Double) {
    /** Сдвиг на метры: на восток и на север. */
    fun offset(eastM: Double, northM: Double): LatLon =
        LatLon(lat + northM / 111_320.0, lon + eastM / (111_320.0 * cos(lat * PI / 180)))

    /** Расстояние в метрах (гаверсинус). */
    fun distanceTo(o: LatLon): Double {
        val r = 6_371_000.0
        val dLat = (o.lat - lat) * PI / 180
        val dLon = (o.lon - lon) * PI / 180
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(lat * PI / 180) * cos(o.lat * PI / 180) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    companion object {
        /** Центр Москвы — пока телефон не знает, где вы. */
        val DEFAULT = LatLon(55.7558, 37.6173)
    }
}

/** «350 м», «2,4 км». */
fun distanceTitle(meters: Double): String =
    if (meters < 1000) "${meters.toInt()} ${t("м")}" else "${amountNumber(Math.round(meters / 100.0) / 10.0)} ${t("км")}"

// ---------- 1. Общие цели ----------

/** Чем считается вклад: чек-ины засчитываются сами. */
enum class GoalMetric { Km, Books, Hours, Trees, Other }

/**
 * Общая цель сообщества: «Вместе пробежим 1 000 000 км». Не соревнование —
 * все двигают одну шкалу. mine — ваш вклад.
 */
data class CommunityGoal(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val unit: String,
    val target: Double,
    val total: Double,
    val participants: Int,
    val metric: GoalMetric,
    val joined: Boolean = false,
    val mine: Double = 0.0,
) {
    val progress: Float get() = (total / target).toFloat().coerceIn(0f, 1f)
}

// ---------- 2. Банк времени ----------

/** Что человек готов дать и что хочет получить. */
data class SkillOffer(val personId: Int, val teaches: List<String>, val wants: List<String>, val about: String = "")

enum class DealStatus { Proposed, Agreed, Done, Declined }

/**
 * Договорённость. give — чему учите вы ("" — не учите), get — чему учат
 * вас ("" — ничему). Обмен 1:1 баланс не меняет; урок «за часы» — тратит
 * или приносит минуты банка времени.
 */
data class SkillDeal(
    val id: Long,
    val personId: Int,
    val give: String,
    val get: String,
    val minutes: Int,
    val status: DealStatus = DealStatus.Proposed,
    val createdAt: Long = 0,
) {
    /** Как занятие меняет баланс, когда прошло. */
    val balanceChange: Int
        get() = (if (give.isNotEmpty()) minutes else 0) - (if (get.isNotEmpty()) minutes else 0)
}

/** Насколько подходит предложение: 2 — взаимный обмен, 1 — в одну сторону, 0 — нет. */
fun matchScore(offer: SkillOffer, myTeach: List<String>, myWant: List<String>): Int {
    fun has(list: List<String>, x: String) = list.any { it.equals(x, ignoreCase = true) }
    val theyTeachMe = offer.teaches.any { has(myWant, it) }
    val iTeachThem = offer.wants.any { has(myTeach, it) }
    return when {
        theyTeachMe && iTeachThem -> 2
        theyTeachMe || iTeachThem -> 1
        else -> 0
    }
}

/** «1 ч 30 мин», «45 мин», «0 мин». */
fun minutesTitle(minutes: Int): String {
    val m = abs(minutes)
    val sign = if (minutes < 0) "−" else ""
    return when {
        m >= 60 && m % 60 != 0 -> "$sign${m / 60} ${t("ч")} ${m % 60} ${t("мин")}"
        m >= 60 -> "$sign${m / 60} ${t("ч")}"
        else -> "$sign$m ${t("мин")}"
    }
}

// ---------- 3. Дневники процесса ----------

/** Совет к записи дневника: вместо «нравится» — помощь. */
data class Tip(val id: Long, val authorId: Int, val text: String, val helpful: Int = 0, val markedByMe: Boolean = false, val createdAt: Long = 0)

/** День процесса: «День 3: учусь жонглировать». */
data class JourneyEntry(
    val id: Long,
    val day: Int,
    val text: String,
    val media: List<Attachment> = emptyList(),
    val createdAt: Long = 0,
    val tips: List<Tip> = emptyList(),
)

/** Короткий челлендж: человек выкладывает не результат, а процесс. */
data class Journey(
    val id: Long,
    val authorId: Int,
    val emoji: String,
    val title: String,
    val days: Int,
    val entries: List<JourneyEntry> = emptyList(),
    val createdAt: Long = 0,
) {
    val nextDay: Int get() = (entries.maxOfOrNull { it.day } ?: 0) + 1
}

// ---------- 4. Городские миссии ----------

/** Находка миссии: фото и точка на карте. */
data class MissionFind(
    val id: Long,
    val authorId: Int,
    val note: String,
    val at: LatLon,
    val photo: Attachment? = null,
    val createdAt: Long = 0,
)

/** Задание по городу: «Сфотографируй 5 мозаик на стенах домов». */
data class Mission(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val target: Int,
    val joined: Boolean = false,
    val finds: List<MissionFind> = emptyList(),
) {
    fun mineCount(meId: Int) = finds.count { it.authorId == meId }
}

// ---------- 5. Клады ----------

data class CacheLog(val id: Long, val authorId: Int, val text: String, val createdAt: Long = 0)

/** Клад: маленький контейнер, спрятанный в городе, и подсказка к нему. */
data class Cache(
    val id: Long,
    val authorId: Int,
    val title: String,
    val hint: String,
    val at: LatLon,
    val difficulty: Int,
    val size: String,
    val createdAt: Long = 0,
    val logs: List<CacheLog> = emptyList(),
    val foundByMe: Boolean = false,
)

/** Отметиться можно, только если вы рядом: GPS ошибается на десятки метров. */
const val CACHE_RADIUS_M = 150.0

// ---------- 6. Вылазки ----------

/** Вылазка малой группой: до 10 человек. */
data class Outing(
    val id: Long,
    val organizerId: Int,
    val emoji: String,
    val title: String,
    val description: String,
    val place: String,
    val startsAt: Long,
    val capacity: Int,
    val participants: List<Int>,
    val at: LatLon? = null,
) {
    val full: Boolean get() = participants.size >= capacity

    companion object {
        const val MAX_PEOPLE = 10
    }
}

// ---------- 7. Рисунок по эстафете ----------

data class DrawingTurn(val authorId: Int, val strokes: List<DrawStroke>)

/**
 * Совместный рисунок: каждый добавляет своё и передаёт следующему.
 * waitingFor — чей ход (номер человека); null — рисунок готов.
 */
data class CollabDrawing(
    val id: Long,
    val title: String,
    val maxTurns: Int,
    val turns: List<DrawingTurn> = emptyList(),
    val waitingFor: Int? = null,
    val createdAt: Long = 0,
) {
    val finished: Boolean get() = turns.size >= maxTurns
    val strokes: List<DrawStroke> get() = turns.flatMap { it.strokes }
}

// ---------- 8. Обмен без денег ----------

enum class ShareKind(val ru: String, val emoji: String) {
    Book("Книги", "📚"), Seeds("Семена и растения", "🌱"), Repair("Ремонт", "🛠️");

    val title: String get() = t(ru)
}

enum class ShareStatus { Available, Requested, Given, Fixed }

/**
 * Вещь в обмене. Книга ходит по кругу: holderId — у кого она сейчас,
 * history — у кого побывала. Семена отдают насовсем. Вещь «Ремонт» ждёт
 * тех, кто поможет починить (helpers).
 */
data class ShareItem(
    val id: Long,
    val kind: ShareKind,
    val title: String,
    val description: String,
    val ownerId: Int,
    val holderId: Int,
    val status: ShareStatus = ShareStatus.Available,
    val requestedBy: Int? = null,
    val history: List<Int> = emptyList(),
    val helpers: List<Int> = emptyList(),
    val photo: Attachment? = null,
    val createdAt: Long = 0,
)

/**
 * Всё во вкладке «Вместе». anchor — где вы (последнее известное место):
 * демо-клады, находки и вылазки стоят вокруг него.
 */
data class TogetherState(
    val goals: List<CommunityGoal> = emptyList(),
    val offers: List<SkillOffer> = emptyList(),
    val deals: List<SkillDeal> = emptyList(),
    val myTeach: List<String> = emptyList(),
    val myWant: List<String> = emptyList(),
    /** Банк времени, минуты. Новичку — час в подарок. */
    val balance: Int = 60,
    val journeys: List<Journey> = emptyList(),
    val missions: List<Mission> = emptyList(),
    val caches: List<Cache> = emptyList(),
    val outings: List<Outing> = emptyList(),
    val drawings: List<CollabDrawing> = emptyList(),
    val shares: List<ShareItem> = emptyList(),
    val anchor: LatLon = LatLon.DEFAULT,
    /** Демо-точки уже перенесены туда, где вы. */
    val anchored: Boolean = false,
) {
    // ---------- Цели ----------

    fun joinGoal(id: String) = copy(goals = goals.map { if (it.id == id && !it.joined) it.copy(joined = true, participants = it.participants + 1) else it })

    /** Вклад в цель: вручную или из чек-ина. */
    fun contribute(id: String, amount: Double) =
        if (amount <= 0) this
        else copy(goals = goals.map { if (it.id == id) it.copy(joined = true, mine = it.mine + amount, total = it.total + amount, participants = it.participants + if (it.joined) 0 else 1) else it })

    /** Чек-ин сам засчитывается в цели, где вы участвуете: километры, книги. */
    fun afterCheckIn(c: CheckIn): TogetherState {
        var s = this
        for (g in goals.filter { it.joined }) {
            val amount = when (g.metric) {
                GoalMetric.Km -> if (isKilometers(c.unit)) c.amount else 0.0
                GoalMetric.Books -> if (c.activity == "book") 1.0 else 0.0
                GoalMetric.Hours -> if (c.unit == "мин" && c.activity in setOf("gym", "yoga", "study")) c.amount / 60 else 0.0
                else -> 0.0
            }
            s = s.contribute(g.id, amount)
        }
        return s
    }

    // ---------- Навыки ----------

    fun addSkill(teach: Boolean, skill: String): TogetherState {
        val name = skill.trim().take(40)
        if (name.isEmpty()) return this
        val list = if (teach) myTeach else myWant
        if (list.any { it.equals(name, ignoreCase = true) }) return this
        return if (teach) copy(myTeach = myTeach + name) else copy(myWant = myWant + name)
    }

    fun removeSkill(teach: Boolean, skill: String) =
        if (teach) copy(myTeach = myTeach - skill) else copy(myWant = myWant - skill)

    /** Предложения — сначала взаимные. */
    fun matches(): List<Pair<SkillOffer, Int>> =
        offers.map { it to matchScore(it, myTeach, myWant) }.sortedByDescending { it.second }

    fun propose(id: Long, personId: Int, give: String, get: String, minutes: Int, now: Long): TogetherState? {
        if (give.isEmpty() && get.isEmpty()) return null
        // Урок «за часы» — только если хватает баланса: за вычетом уже обещанных уроков
        val promised = deals.filter { it.status == DealStatus.Proposed || it.status == DealStatus.Agreed }.sumOf { (-it.balanceChange).coerceAtLeast(0) }
        if (give.isEmpty() && balance - promised < minutes) return null
        return copy(deals = listOf(SkillDeal(id, personId, give, get, minutes, createdAt = now)) + deals)
    }

    fun setDeal(id: Long, status: DealStatus): TogetherState {
        val deal = deals.firstOrNull { it.id == id } ?: return this
        // Прошедшее и отменённое — окончательно
        if (deal.status == DealStatus.Done || deal.status == DealStatus.Declined) return this
        val change = if (status == DealStatus.Done) deal.balanceChange else 0
        return copy(deals = deals.map { if (it.id == id) it.copy(status = status) else it }, balance = balance + change)
    }

    // ---------- Дневники ----------

    fun startJourney(id: Long, me: Int, emoji: String, title: String, days: Int, now: Long): TogetherState? {
        val name = title.trim().take(60)
        if (name.isEmpty()) return null
        return copy(journeys = listOf(Journey(id, me, emoji, name, days.coerceIn(3, 100), createdAt = now)) + journeys)
    }

    fun addEntry(journeyId: Long, entryId: Long, text: String, media: List<Attachment>, now: Long): TogetherState {
        if (text.isBlank() && media.isEmpty()) return this
        return copy(journeys = journeys.map { j ->
            if (j.id != journeyId) j
            else j.copy(entries = j.entries + JourneyEntry(entryId, j.nextDay, text.trim().take(1000), media, now))
        })
    }

    fun addTip(journeyId: Long, entryId: Long, tip: Tip): TogetherState {
        if (tip.text.isBlank()) return this
        return copy(journeys = journeys.map { j ->
            if (j.id != journeyId) j
            else j.copy(entries = j.entries.map { e -> if (e.id == entryId) e.copy(tips = e.tips + tip) else e })
        })
    }

    /** «Полезно» — повторное нажатие снимает отметку. */
    fun toggleHelpful(journeyId: Long, entryId: Long, tipId: Long) = copy(journeys = journeys.map { j ->
        if (j.id != journeyId) j
        else j.copy(entries = j.entries.map { e ->
            if (e.id != entryId) e
            else e.copy(tips = e.tips.map { tp ->
                if (tp.id != tipId) tp
                else tp.copy(markedByMe = !tp.markedByMe, helpful = (tp.helpful + if (tp.markedByMe) -1 else 1).coerceAtLeast(0))
            })
        })
    })

    // ---------- Миссии ----------

    fun joinMission(id: String) = copy(missions = missions.map { if (it.id == id) it.copy(joined = true) else it })

    fun addFind(missionId: String, find: MissionFind) =
        copy(missions = missions.map { if (it.id == missionId) it.copy(joined = true, finds = it.finds + find) else it })

    // ---------- Клады ----------

    fun hideCache(cache: Cache): TogetherState? =
        if (cache.title.isBlank() || cache.hint.isBlank()) null else copy(caches = listOf(cache) + caches)

    /** Нашли клад: запись в журнале. null — вы слишком далеко или уже отмечались. */
    fun findCache(id: Long, log: CacheLog, here: LatLon?): TogetherState? {
        val cache = caches.firstOrNull { it.id == id } ?: return null
        if (cache.foundByMe || cache.authorId == log.authorId) return null
        if (here == null || here.distanceTo(cache.at) > CACHE_RADIUS_M) return null
        return copy(caches = caches.map { if (it.id == id) it.copy(foundByMe = true, logs = it.logs + log) else it })
    }

    // ---------- Вылазки ----------

    fun createOuting(o: Outing): TogetherState? =
        if (o.title.isBlank() || o.place.isBlank()) null
        else copy(outings = (listOf(o.copy(capacity = o.capacity.coerceIn(2, Outing.MAX_PEOPLE))) + outings).sortedBy { it.startsAt })

    /** Записаться или выйти; в полную вылазку не записаться. */
    fun toggleOuting(id: Long, me: Int) = copy(outings = outings.map { o ->
        when {
            o.id != id -> o
            me in o.participants && o.organizerId != me -> o.copy(participants = o.participants - me)
            me !in o.participants && !o.full -> o.copy(participants = o.participants + me)
            else -> o
        }
    })

    fun cancelOuting(id: Long, me: Int) = copy(outings = outings.filterNot { it.id == id && it.organizerId == me })

    // ---------- Рисунок ----------

    fun startDrawing(id: Long, title: String, turns: Int, me: Int, now: Long): TogetherState? {
        val name = title.trim().take(40)
        if (name.isEmpty()) return null
        return copy(drawings = listOf(CollabDrawing(id, name, turns.coerceIn(2, 8), waitingFor = me, createdAt = now)) + drawings)
    }

    /** Свой ход: штрихи и кому передать (null — если ходов больше нет). */
    fun drawTurn(id: Long, me: Int, strokes: List<DrawStroke>, passTo: Int?) = copy(drawings = drawings.map { d ->
        if (d.id != id || d.waitingFor != me || strokes.isEmpty()) d
        else {
            val next = d.copy(turns = d.turns + DrawingTurn(me, strokes))
            next.copy(waitingFor = if (next.finished) null else passTo)
        }
    })

    // ---------- Обмен ----------

    fun addShare(item: ShareItem): TogetherState? =
        if (item.title.isBlank()) null else copy(shares = listOf(item) + shares)

    /** Попросить: книгу почитать, семена забрать. */
    fun requestShare(id: Long, me: Int) = copy(shares = shares.map {
        if (it.id == id && it.status == ShareStatus.Available && it.holderId != me) it.copy(status = ShareStatus.Requested, requestedBy = me) else it
    })

    /** Передать: книга идёт к тому, кто попросил (или кому решили), семена — насовсем. */
    fun handOver(id: Long, to: Int) = copy(shares = shares.map {
        if (it.id != id) it
        else when (it.kind) {
            ShareKind.Book -> it.copy(holderId = to, history = it.history + it.holderId, status = ShareStatus.Available, requestedBy = null)
            ShareKind.Seeds -> it.copy(holderId = to, history = it.history + it.holderId, status = ShareStatus.Given, requestedBy = null)
            ShareKind.Repair -> it
        }
    })

    fun offerHelp(id: Long, me: Int) = copy(shares = shares.map {
        if (it.id == id && it.kind == ShareKind.Repair && me !in it.helpers && it.ownerId != me) it.copy(helpers = it.helpers + me) else it
    })

    fun markFixed(id: Long) = copy(shares = shares.map { if (it.id == id && it.kind == ShareKind.Repair) it.copy(status = ShareStatus.Fixed) else it })

    /** Демо-точки — вокруг того места, где вы на самом деле: один раз, при первом известном месте. */
    fun relocate(here: LatLon, meId: Int): TogetherState {
        if (anchored) return copy(anchor = here)
        val dLat = here.lat - anchor.lat
        val dLon = here.lon - anchor.lon
        fun LatLon.shift() = LatLon(lat + dLat, lon + dLon)
        return copy(
            anchor = here,
            anchored = true,
            caches = caches.map { if (it.authorId == meId) it else it.copy(at = it.at.shift()) },
            missions = missions.map { m -> m.copy(finds = m.finds.map { if (it.authorId == meId) it else it.copy(at = it.at.shift()) }) },
            outings = outings.map { if (it.organizerId == meId) it else it.copy(at = it.at?.shift()) },
        )
    }
}
