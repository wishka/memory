package ru.wishka.memory.data

/**
 * #хештеги и @упоминания в тексте записей, комментариев и PRISM.
 * Тег — буквы любого языка, цифры и «_» (#закат, #yerevan, #երևան);
 * упоминание — логин Memory (латиница, цифры, точка, «_»).
 */
object Tags {
    private val TAG = Regex("(?<![\\p{L}\\p{N}_&/])#([\\p{L}\\p{N}_]{2,40})")
    private val MENTION = Regex("(?<![\\p{L}\\p{N}_.@/])@([a-zA-Z0-9._]{3,20})(?<!\\.)")
    private val URL = Regex("https?://[^\\s]+")

    /** Теги текста без «#», строчными, без повторов: «#Закат #закат» → [закат]. */
    fun hashtags(text: String): List<String> =
        TAG.findAll(text).map { it.groupValues[1].lowercase() }.distinct().toList()

    /** Логины, упомянутые в тексте, строчными, без повторов. */
    fun mentions(text: String): List<String> =
        MENTION.findAll(text).map { it.groupValues[1].lowercase() }.distinct().toList()

    /** Кусок текста: обычный, тег, упоминание или ссылка. value — тег/логин без знака, или адрес. */
    data class Span(val start: Int, val end: Int, val kind: Kind, val value: String)

    enum class Kind { Tag, Mention, Url }

    /** Где в тексте теги, упоминания и ссылки — по порядку, без пересечений. */
    fun spans(text: String): List<Span> {
        val all = URL.findAll(text).map { Span(it.range.first, it.range.last + 1, Kind.Url, it.value.trimEnd('.', ',', ')', '!', '?')) }
            .map { it.copy(end = it.start + it.value.length) }.toList() +
            TAG.findAll(text).map { Span(it.range.first, it.range.last + 1, Kind.Tag, it.groupValues[1].lowercase()) } +
            MENTION.findAll(text).map { Span(it.range.first, it.range.last + 1, Kind.Mention, it.groupValues[1].lowercase()) }
        val out = mutableListOf<Span>()
        for (s in all.sortedBy { it.start }) if (out.none { s.start < it.end && it.start < s.end }) out += s
        return out
    }

    /**
     * Подсказка при наборе: человек печатает «@ве» — какой кусок дописывается.
     * Возвращает начало упоминания (индекс «@») и введённую часть, или null.
     */
    fun typingMention(text: String, cursor: Int = text.length): Pair<Int, String>? {
        val before = text.substring(0, cursor.coerceIn(0, text.length))
        val at = before.lastIndexOf('@')
        if (at < 0) return null
        if (at > 0 && (before[at - 1].isLetterOrDigit() || before[at - 1] == '_')) return null
        val part = before.substring(at + 1)
        if (part.length > 20 || part.any { !(it.isLetterOrDigit() || it == '.' || it == '_') }) return null
        return at to part
    }

    /** Вставить выбранный логин вместо набранного «@ве…». */
    fun completeMention(text: String, at: Int, cursor: Int, handle: String): String =
        text.substring(0, at) + "@" + handle + " " + text.substring(cursor.coerceIn(at, text.length)).trimStart()
}

/** Что нашлось: люди, записи, группы и теги. */
data class SearchResults(
    val people: List<User> = emptyList(),
    val posts: List<Post> = emptyList(),
    val groups: List<Group> = emptyList(),
    val tags: List<Pair<String, Int>> = emptyList(),
) {
    val empty: Boolean get() = people.isEmpty() && posts.isEmpty() && groups.isEmpty() && tags.isEmpty()
}

/**
 * Поиск по всему Memory: люди (имя, логин, город, интересы), записи
 * (текст и теги), группы и теги. Без сервера ищет по тому, что есть на
 * телефоне; с сервером тот же интерфейс получит ответы оттуда.
 */
object Search {
    private fun norm(s: String) = s.lowercase().replace('ё', 'е').trim()

    private fun words(query: String) = norm(query).removePrefix("#").removePrefix("@").split(Regex("\\s+")).filter { it.isNotBlank() }

    /** Люди: точный логин — первым, потом начало имени, потом остальное. */
    fun people(query: String, people: List<User>): List<User> {
        val q = norm(query).removePrefix("@")
        if (q.isEmpty()) return emptyList()
        val ws = words(query)
        return people.mapNotNull { u ->
            val name = norm(u.name)
            val handle = u.handle.lowercase()
            val body = listOf(name, handle, norm(u.city), norm(u.bio), u.interests.joinToString(" ") { norm(it) }).joinToString(" ")
            if (!ws.all { it in body }) return@mapNotNull null
            val score = when {
                handle == q -> 100
                handle.startsWith(q) -> 80
                name.startsWith(q) || name.split(' ').any { it.startsWith(q) } -> 60
                else -> 10
            }
            u to score
        }.sortedWith(compareByDescending<Pair<User, Int>> { it.second }.thenBy { it.first.name }).map { it.first }
    }

    /** Записи: «#тег» — только с этим тегом; иначе все слова — в тексте или чек-ине. Новые сверху. */
    fun posts(query: String, posts: List<Post>): List<Post> {
        val q = norm(query)
        if (q.isEmpty()) return emptyList()
        if (q.startsWith("#") && ' ' !in q) {
            val tag = q.removePrefix("#")
            return posts.filter { tag in Tags.hashtags(it.text) }.sortedByDescending { it.createdAt }
        }
        val ws = words(query)
        return posts.filter { p ->
            val body = norm(p.text + " " + (p.checkIn?.let { it.subject + " " + it.displayTitle } ?: ""))
            ws.all { it in body }
        }.sortedByDescending { it.createdAt }
    }

    fun groups(query: String, groups: List<Group>): List<Group> {
        val ws = words(query)
        if (ws.isEmpty()) return emptyList()
        return groups.filter { g -> norm(g.name + " " + g.description + " " + g.topic).let { b -> ws.all { it in b } } }
    }

    /** Теги, которые начинаются с набранного, — с числом записей. */
    fun tags(query: String, posts: List<Post>): List<Pair<String, Int>> {
        val q = norm(query).removePrefix("#")
        if (q.isEmpty()) return emptyList()
        return popularTags(posts, limit = 50).filter { it.first.startsWith(q) }.take(8)
    }

    /** Популярные теги — для пустого поиска. */
    fun popularTags(posts: List<Post>, limit: Int = 12): List<Pair<String, Int>> =
        posts.flatMap { Tags.hashtags(it.text) }.groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit).map { it.key to it.value }

    fun all(query: String, people: List<User>, posts: List<Post>, groups: List<Group>): SearchResults {
        val q = query.trim()
        if (q.isEmpty()) return SearchResults()
        return when {
            q.startsWith("#") -> SearchResults(posts = posts(q, posts), tags = tags(q, posts))
            q.startsWith("@") -> SearchResults(people = people(q, people))
            else -> SearchResults(people(q, people), posts(q, posts), groups(q, groups), tags(q, posts))
        }
    }

    /** Кого предложить после «@»: друзья первыми, потом остальные. */
    fun mentionCandidates(part: String, people: List<User>, friends: Set<Int>, limit: Int = 5): List<User> {
        val q = part.lowercase()
        return people.filter { u -> q.isEmpty() || u.handle.lowercase().startsWith(q) || norm(u.name).split(' ').any { it.startsWith(norm(q)) } }
            .sortedWith(compareByDescending<User> { it.id in friends }.thenBy { it.name })
            .take(limit)
    }

    /** История поиска: новый запрос — первым, без повторов, не больше limit. */
    fun remember(history: List<String>, query: String, limit: Int = 10): List<String> {
        val q = query.trim()
        if (q.length < 2) return history
        return (listOf(q) + history.filter { !it.equals(q, ignoreCase = true) }).take(limit)
    }
}
