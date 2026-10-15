package ru.wishka.memory.data

import ru.wishka.memory.i18n.t

/** Кто посмотрел историю и когда; liked — поставил сердечко. */
data class StoryView(val userId: Int, val at: Long, val liked: Boolean = false)

/**
 * История: фото, видео или текст на градиенте — живёт сутки, потом
 * уходит в архив автора (из архива её можно добавить в «Актуальное»).
 * closeFriends — видят только «Близкие друзья» автора.
 * seed — демо-картинка (туманность), пока у истории нет своего фото.
 */
data class Story(
    val id: Long,
    val authorId: Int,
    val createdAt: Long,
    val media: Attachment? = null,
    val text: String = "",
    /** Фон текстовой истории — номер градиента в Stories.BACKGROUNDS. */
    val bg: Int = 0,
    val seed: Int = 0,
    val closeFriends: Boolean = false,
    val views: List<StoryView> = emptyList(),
) {
    fun active(now: Long): Boolean = now - createdAt < Stories.TTL_MS
    val likes: Int get() = views.count { it.liked }
}

/** «Актуальное» в профиле: подборка историй с названием; живёт, пока не удалят. */
data class Highlight(val id: Long, val ownerId: Int, val title: String, val storyIds: List<Long>, val coverId: Long = storyIds.firstOrNull() ?: 0)

/** На какую историю ответили в переписке: копия сути, чтобы читалось и после того, как она исчезла. */
data class StoryRef(val storyId: Long, val authorId: Int, val preview: String, val seed: Int = 0, val photo: String = "", val bg: Int = 0)

/** Кружок в ряду историй: чей, его истории по порядку, все ли просмотрены, есть ли «для близких». */
data class StoryCircle(val user: User, val stories: List<Story>, val seen: Boolean, val close: Boolean)

/** Что открыто в просмотре: по кругу авторов (или одна подборка «Актуального»). */
data class StoryViewer(
    val authorIds: List<Int>,
    val author: Int,
    val index: Int,
    /** Открыта подборка «Актуального» — её истории, без перехода к другим авторам. */
    val highlightId: Long? = null,
)

object Stories {
    const val TTL_MS = 24L * 60 * 60 * 1000
    /** Сколько показывается одна история без касаний. */
    const val SHOW_MS = 5_000L
    const val MAX_TEXT = 300
    const val MAX_TITLE = 24

    /** Градиенты текстовых историй: пары цветов ARGB — одинаковые на обеих платформах. */
    val BACKGROUNDS: List<Pair<Long, Long>> = listOf(
        0xFF844CAD to 0xFF195699,
        0xFFFF9EDE to 0xFF844CAD,
        0xFF307EC0 to 0xFF0F153A,
        0xFFBC92CD to 0xFF5F3F96,
        0xFF1F7A6E to 0xFF195699,
        0xFFE58F5B to 0xFF844CAD,
    )

    /**
     * Видит ли человек историю: свою — всегда; чужую — если он друг автора,
     * а «для близких» — только если автор добавил его в близкие.
     */
    fun visible(story: Story, viewerId: Int, isFriend: Boolean, inAuthorsClose: Boolean): Boolean =
        story.authorId == viewerId || (isFriend && (!story.closeFriends || inAuthorsClose))

    /**
     * Ряд кружков: сначала с непросмотренными, потом просмотренные; внутри —
     * по свежести последней истории. Свой кружок отдельно (первым в интерфейсе).
     */
    fun rings(stories: List<Story>, people: List<User>, meId: Int, seen: Set<Long>, now: Long): List<StoryCircle> =
        stories.filter { it.active(now) && it.authorId != meId }
            .groupBy { it.authorId }
            .mapNotNull { (id, list) ->
                val user = people.firstOrNull { it.id == id } ?: return@mapNotNull null
                val sorted = list.sortedBy { it.createdAt }
                StoryCircle(user, sorted, sorted.all { it.id in seen }, sorted.any { it.closeFriends })
            }
            .sortedWith(compareBy<StoryCircle> { it.seen }.thenByDescending { r -> r.stories.maxOf { it.createdAt } })

    /** С какой истории начать: с первой непросмотренной, а если все видел — с первой. */
    fun startIndex(stories: List<Story>, seen: Set<Long>): Int =
        stories.indexOfFirst { it.id !in seen }.takeIf { it >= 0 } ?: 0

    /** Записать просмотр: один на человека, сердечко не теряется. */
    fun view(story: Story, userId: Int, at: Long, liked: Boolean = false): Story {
        val old = story.views.firstOrNull { it.userId == userId }
        val v = StoryView(userId, old?.at ?: at, liked || old?.liked == true)
        return story.copy(views = story.views.filter { it.userId != userId } + v)
    }

    /** Подпись истории для цитаты в переписке. */
    fun preview(story: Story): String = story.text.take(60).ifEmpty {
        when {
            story.media?.kind == MediaKind.Video -> t("Видео")
            story.media != null -> t("Фото")
            else -> t("История")
        }
    }

    fun ref(story: Story): StoryRef = StoryRef(story.id, story.authorId, preview(story), story.seed, story.media?.path.orEmpty(), story.bg)

    val demoTexts = listOf("Доброе утро!", "Кофе и дождь", "Лучший закат недели", "Угадайте, где я", "Новая книга — не оторваться", "Тренировка сделана")
}
