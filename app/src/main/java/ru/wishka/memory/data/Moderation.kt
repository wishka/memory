package ru.wishka.memory.data

/**
 * Что скрывать в комментариях к своим записям, в сообщениях и запросах на
 * переписку: свои «скрытые слова», оскорбления, спам. Решает телефон
 * (сервер дополнит своей проверкой). Своё и сказанное друзьями в
 * переписке не трогаем — только то, что приходит от других.
 */
data class ModerationPrefs(
    /** Свои слова и фразы: всё, где они есть, сворачивается. */
    val hiddenWords: List<String> = emptyList(),
    /** Скрывать оскорбления (встроенный список на трёх языках). */
    val hideOffensive: Boolean = true,
    /** Скрывать похожее на спам: ссылки пачкой, «заработок», повторы. */
    val hideSpam: Boolean = true,
)

/** Почему скрыто — чтобы честно написать в свёрнутой строке. */
enum class HiddenReason { Word, Offensive, Spam }

object Moderation {
    const val MAX_WORDS = 100
    const val MAX_WORD = 40

    /**
     * Корни оскорблений: ловят и формы слова («дурак», «дураки»). Список
     * намеренно короткий и мягкий — это первый фильтр, не цензура.
     */
    private val OFFENSIVE = listOf(
        // русский
        "дурак", "дура ", "идиот", "дебил", "тупиц", "урод", "кретин", "придур", "мраз", "сволоч", "ублюд", "чмо",
        // английский
        "idiot", "stupid", "moron", "loser", "dumbass", "retard", "bitch",
        // армянский
        "ապուշ", "հիմար", "տխմար", "անասուն",
    )

    private val SPAM = listOf(
        "заработок", "заработай", "пассивный доход", "казино", "ставки на спорт", "крипт", "бесплатно получи", "переходи по ссылке",
        "earn money", "free money", "casino", "click the link", "crypto giveaway",
    )

    private val LINK = Regex("(https?://|www\\.)\\S+", RegexOption.IGNORE_CASE)

    /**
     * Привести к виду, где обходы не работают: строчные, ё→е, латиница-
     * двойники и цифры → буквы («д0раk» → «дорак»), повторы букв — одна.
     */
    fun normalize(text: String): String {
        val map = mapOf(
            '0' to 'о', '3' to 'е', '@' to 'а', '4' to 'ч', '6' to 'б', '1' to 'и',
            'a' to 'а', 'e' to 'е', 'o' to 'о', 'p' to 'р', 'c' to 'с', 'x' to 'х', 'y' to 'у', 'k' to 'к', 'm' to 'м', 't' to 'т', 'h' to 'н', 'b' to 'в',
        )
        val lower = text.lowercase().replace('ё', 'е')
        val sb = StringBuilder()
        var prev = ' '
        for (ch in lower) {
            val c = if (lower.any { it in 'а'..'я' }) map[ch] ?: ch else ch
            val out = if (c.isLetterOrDigit()) c else ' '
            if (out == prev && out != ' ') continue
            if (out == ' ' && prev == ' ') continue
            sb.append(out)
            prev = out
        }
        return " " + sb.toString().trim() + " "
    }

    private fun normWord(w: String): String = normalize(w).trim()

    /** Добавить скрытое слово: без пустых и повторов, не длиннее 40 знаков. */
    fun addWord(list: List<String>, word: String): List<String> {
        val w = word.trim().take(MAX_WORD)
        if (w.isEmpty() || list.any { normWord(it) == normWord(w) }) return list
        return (list + w).take(MAX_WORDS)
    }

    fun isSpam(text: String): Boolean {
        val n = normalize(text)
        val links = LINK.findAll(text).count()
        if (links >= 3) return true
        if (links >= 1 && SPAM.any { n.contains(normalize(it).trim()) }) return true
        if (SPAM.count { n.contains(normalize(it).trim()) } >= 2) return true
        // «ааааааааа», «!!!!!!!!» и одно слово много раз подряд
        val words = n.trim().split(' ').filter { it.isNotEmpty() }
        if (words.size >= 6 && words.groupingBy { it }.eachCount().values.max() >= words.size * 2 / 3) return true
        return false
    }

    fun isOffensive(text: String): Boolean {
        val n = normalize(text)
        return OFFENSIVE.any { root -> val r = normalize(root).trimEnd(); n.contains(if (root.endsWith(' ')) r + " " else r) }
    }

    /**
     * Скрыть ли текст: null — показывать. Свои слова ищутся с границами
     * слова в начале («кот» не ловит «скот», но ловит «коты»).
     */
    fun check(text: String, prefs: ModerationPrefs): HiddenReason? {
        if (text.isBlank()) return null
        val n = normalize(text)
        if (prefs.hiddenWords.any { w -> val nw = normWord(w); nw.isNotEmpty() && n.contains(" $nw") }) return HiddenReason.Word
        if (prefs.hideOffensive && isOffensive(text)) return HiddenReason.Offensive
        if (prefs.hideSpam && isSpam(text)) return HiddenReason.Spam
        return null
    }
}

/**
 * Не чаще maxEvents раз за windowMs — от случайного флуда и залипшей
 * кнопки. Сервер ограничит и сам; здесь — чтобы человек сразу видел.
 */
class RateLimiter(private val maxEvents: Int, private val windowMs: Long) {
    private val times = ArrayDeque<Long>()

    @Synchronized
    fun allow(now: Long): Boolean {
        while (times.isNotEmpty() && now - times.first() >= windowMs) times.removeFirst()
        if (times.size >= maxEvents) return false
        times.addLast(now)
        return true
    }
}
