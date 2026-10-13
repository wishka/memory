package ru.wishka.memory.data

import ru.wishka.memory.i18n.L10n

/**
 * Гид по Memory — справка внутри приложения: главы, темы, шаги.
 *
 * Текст лежит в docs/guide/guide.json сразу на двух языках; из него
 * scripts/sync_guide.py собирает GuideContent.kt и GuideContent.swift.
 * Поэтому гид не идёт через общий словарь t(): у каждой строки своя пара.
 */
data class GuideText(val ru: String, val en: String) {
    /** На выбранном языке интерфейса. */
    val text: String get() = if (L10n.english) en else ru
}

/** Куда ведёт кнопка темы «Открыть …». */
enum class GuideAction {
    Feed, Stories, Prism, NewPrism, Chats, Groups, Together, Profile,
    Friends, Requests, Saved, Music, Studio, Shelf, Recap,
    Settings, Privacy, Security, Blacklist, Notifications, Sounds,
    Wallpaper, Language, Legal, Growth,
}

/** Тема: заголовок, шаги по порядку, совет и куда перейти. */
data class GuideTopic(
    val id: String,
    val title: GuideText,
    val steps: List<GuideText>,
    val tip: GuideText? = null,
    val action: GuideAction? = null,
)

/** Глава гида: значок, название, подзаголовок и темы. */
data class GuideChapter(
    val id: String,
    val emoji: String,
    val title: GuideText,
    val lead: GuideText,
    val topics: List<GuideTopic>,
) {
    /** Ключ темы для отметки «прочитано»: «глава/тема». */
    fun key(topic: GuideTopic): String = "$id/${topic.id}"
}

/** Найденная тема: глава и тема. */
data class GuideHit(val chapter: GuideChapter, val topic: GuideTopic)

object Guide {
    val chapters: List<GuideChapter> get() = GuideContent.chapters

    /** Всего тем — для «прочитано N из M». */
    val topicCount: Int get() = chapters.sumOf { it.topics.size }

    fun chapter(id: String?): GuideChapter? = chapters.firstOrNull { it.id == id }

    /** Все ключи тем — чтобы отметки старых, убранных тем не считались. */
    val keys: Set<String> get() = chapters.flatMap { c -> c.topics.map { c.key(it) } }.toSet()

    /**
     * Поиск по темам на обоих языках: каждое слово запроса должно
     * встретиться в заголовке, шагах или совете. Сначала — совпадения
     * в заголовке.
     */
    fun search(query: String): List<GuideHit> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val hits = chapters.flatMap { c ->
            c.topics.mapNotNull { t ->
                val title = "${t.title.ru} ${t.title.en}".lowercase()
                val body = buildString {
                    append(title).append(' ')
                    append(c.title.ru).append(' ').append(c.title.en).append(' ')
                    t.steps.forEach { append(it.ru).append(' ').append(it.en).append(' ') }
                    t.tip?.let { append(it.ru).append(' ').append(it.en) }
                }.lowercase()
                if (words.all { it in body }) GuideHit(c, t) to words.count { it in title } else null
            }
        }
        return hits.sortedByDescending { it.second }.map { it.first }
    }
}
