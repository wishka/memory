package ru.wishka.memory.data

/**
 * Опрос — в записи ленты, в группе и в сообщении. Вариантов 2–10;
 * multiple — можно выбрать несколько. mine — за что проголосовали вы
 * (номера вариантов): голос можно поменять, пока опрос не закрыт. Та же
 * логика — в Polls.swift.
 */
data class PollOption(val text: String, val votes: Int = 0)

data class Poll(
    val id: Long,
    val question: String,
    val options: List<PollOption>,
    val multiple: Boolean = false,
    val mine: Set<Int> = emptySet(),
    val closed: Boolean = false,
) {
    val total: Int get() = options.sumOf { it.votes }
    val voted: Boolean get() = mine.isNotEmpty()
    /** Сколько человек проголосовало: при нескольких ответах голосов больше, чем людей — берём наибольший вариант как нижнюю оценку. */
    val voters: Int get() = if (multiple) options.maxOfOrNull { it.votes } ?: 0 else total

    /** Доля варианта, 0…100, целыми; у всех вместе — ровно 100. */
    fun percents(): List<Int> {
        val sum = total
        if (sum == 0) return options.map { 0 }
        val raw = options.map { it.votes * 100.0 / sum }
        val floors = raw.map { it.toInt() }.toMutableList()
        var left = 100 - floors.sum()
        // Остаток — вариантам с самой большой дробной частью
        raw.withIndex().sortedByDescending { it.value - it.value.toInt() }.forEach { (i, _) ->
            if (left > 0 && options[i].votes > 0) { floors[i]++; left-- }
        }
        return floors
    }

    fun percent(i: Int): Int = percents().getOrElse(i) { 0 }

    /** Голос за вариант i: один ответ — переносит голос; несколько — включает и выключает. */
    fun vote(i: Int): Poll {
        if (closed || i !in options.indices) return this
        val next = when {
            multiple -> if (i in mine) mine - i else mine + i
            i in mine -> emptySet()
            else -> setOf(i)
        }
        val opts = options.mapIndexed { k, o ->
            val was = k in mine
            val now = k in next
            o.copy(votes = (o.votes + (if (now) 1 else 0) - (if (was) 1 else 0)).coerceAtLeast(0))
        }
        return copy(options = opts, mine = next)
    }

    /** Чужие голоса (в демо — друзья голосуют, позже — с сервера). */
    fun addVotes(counts: Map<Int, Int>): Poll =
        if (closed) this else copy(options = options.mapIndexed { k, o -> o.copy(votes = o.votes + (counts[k] ?: 0).coerceAtLeast(0)) })

    fun close(): Poll = copy(closed = true)

    /** Победитель — вариант с большинством голосов; ничья — первый из них. */
    val leader: Int? get() = options.withIndex().maxByOrNull { it.value.votes }?.takeIf { it.value.votes > 0 }?.index

    companion object {
        const val MAX_OPTIONS = 10
        const val MIN_OPTIONS = 2

        /** Новый опрос из того, что ввёл человек: пустые варианты отброшены, повторы — тоже. null — так нельзя. */
        fun create(id: Long, question: String, options: List<String>, multiple: Boolean): Poll? {
            val q = question.trim().take(200)
            val opts = options.map { it.trim().take(100) }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(MAX_OPTIONS)
            if (q.isEmpty() || opts.size < MIN_OPTIONS) return null
            return Poll(id, q, opts.map { PollOption(it) }, multiple)
        }
    }
}

/** Ширина полоски результата: 0…1, у лидера — полная доля. */
fun Poll.barFraction(i: Int): Float = if (total == 0) 0f else (options[i].votes.toFloat() / total).coerceIn(0f, 1f)

/** Опрос, который человек составил, но ещё не отправил: номер даёт модель. */
data class PollDraft(val question: String, val options: List<String>, val multiple: Boolean = false) {
    fun build(id: Long): Poll? = Poll.create(id, question, options, multiple)
}
