package ru.wishka.memory.data

import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf

/**
 * Что случилось вокруг вас: «нравится», комментарии, упоминания, заявки в
 * друзья, голоса в опросах. Лента активности — колокольчик в «Ленте».
 *
 * Хранятся только факты (кто, что, к чему), а текст складывается при
 * показе — на текущем языке интерфейса. Так же устроены и пуши: сервер
 * пришлёт факты, телефон соберёт фразу.
 */
enum class ActivityKind(val id: String) {
    Like("like"),
    Comment("comment"),
    Reply("reply"),
    Mention("mention"),
    CommentLike("comment_like"),
    PrismLike("prism_like"),
    PrismComment("prism_comment"),
    FriendRequest("request"),
    FriendAccepted("accepted"),
    PollVotes("votes"),
    GroupJoin("join"),
    Live("live"),
    Repost("repost"),
    Tagged("tagged"),
    StoryReply("story_reply"),
    MissedCall("missed_call"),
    ;

    /** Такие события сливаются в одно: «Вера и ещё 3 оценили вашу запись». */
    val groups: Boolean get() = this == Like || this == PrismLike || this == CommentLike || this == GroupJoin || this == Repost

    companion object {
        fun of(id: String): ActivityKind? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Одно событие. actorId — кто (0 — несколько человек, как у голосов);
 * actorName — имя на случай, если человека нет среди знакомых (новичок
 * группы); targetId — запись, PRISM, группа или эфир; preview — начало
 * текста записи или комментария; count — сколько (голосов).
 */
data class ActivityItem(
    val id: Long,
    val kind: ActivityKind,
    val actorId: Int,
    val at: Long,
    val targetId: Long = 0,
    val preview: String = "",
    val count: Int = 1,
    val actorName: String = "",
    val actorHue: Float = 0.5f,
)

/** Несколько одинаковых событий вместе: кто (по порядку, последний — первым), когда, к чему. */
data class ActivityGroup(
    val kind: ActivityKind,
    val actors: List<User>,
    val at: Long,
    val targetId: Long,
    val preview: String,
    val count: Int,
    val ids: List<Long>,
) {
    val key: String get() = "${kind.id}:${ids.first()}"
}

object Activity {
    /** Сколько событий хранить: старше — забываются. */
    const val LIMIT = 300

    /** За какое окно сливать «нравится» к одной записи. */
    const val MERGE_MS = 24L * 60 * 60 * 1000

    fun actor(item: ActivityItem, me: User): User =
        Demo.person(item.actorId)?.takeIf { it.id != me.id }
            ?: User(item.actorId, item.actorName.ifEmpty { t("Кто-то") }, item.actorName.lowercase().replace(' ', '.'), hue = item.actorHue)

    /**
     * Свернуть ленту: «нравится» к одной записи за сутки — одной строкой,
     * без повторов людей. Порядок — новые сверху. Заблокированных не видно.
     */
    fun group(items: List<ActivityItem>, me: User, blocked: Set<Int> = emptySet()): List<ActivityGroup> {
        val out = mutableListOf<ActivityGroup>()
        for (item in items.sortedByDescending { it.at }) {
            if (item.actorId != 0 && item.actorId in blocked) continue
            val person = actor(item, me)
            val idx = if (!item.kind.groups) -1 else out.indexOfLast { g ->
                g.kind == item.kind && g.targetId == item.targetId && g.at - item.at <= MERGE_MS
            }
            if (idx < 0) {
                out += ActivityGroup(item.kind, listOf(person), item.at, item.targetId, item.preview, item.count, listOf(item.id))
            } else {
                val g = out[idx]
                out[idx] = g.copy(
                    actors = if (g.actors.any { it.id == person.id && it.name == person.name }) g.actors else g.actors + person,
                    count = g.count + item.count,
                    ids = g.ids + item.id,
                )
            }
        }
        return out
    }

    /** Новые — с момента, когда вы последний раз открывали ленту активности. */
    fun unread(items: List<ActivityItem>, seenAt: Long, blocked: Set<Int> = emptySet()): Int =
        items.count { it.at > seenAt && (it.actorId == 0 || it.actorId !in blocked) }

    /** «Вера», «Вера и Марк», «Вера, Марк и ещё 3». */
    fun who(actors: List<User>): String {
        val names = actors.map { Nudges.firstName(it) }
        return when (names.size) {
            0 -> t("Кто-то")
            1 -> names[0]
            2 -> tf("%s и %s", names[0], names[1])
            else -> tf("%s, %s и ещё %s", names[0], names[1], names.size - 2)
        }
    }

    /** Фраза события на текущем языке. */
    fun text(g: ActivityGroup): String {
        val single = g.actors.size == 1
        val one = g.actors.firstOrNull()
        fun verb(plural: String, male: String, female: String) =
            if (!single || one == null) plural else if (Nudges.female(one)) female else male
        val who = who(g.actors)
        val quote = if (g.preview.isNotBlank()) " «${g.preview.take(60)}»" else ""
        return when (g.kind) {
            ActivityKind.Like -> tf(verb("%s оценили вашу запись", "%s оценил вашу запись", "%s оценила вашу запись"), who) + quote
            ActivityKind.Comment -> tf(verb("%s прокомментировали вашу запись", "%s прокомментировал вашу запись", "%s прокомментировала вашу запись"), who) + quote
            ActivityKind.Reply -> tf(verb("%s ответили на ваш комментарий", "%s ответил на ваш комментарий", "%s ответила на ваш комментарий"), who) + quote
            ActivityKind.Mention -> tf(verb("%s упомянули вас", "%s упомянул вас", "%s упомянула вас"), who) + quote
            ActivityKind.CommentLike -> tf(verb("%s оценили ваш комментарий", "%s оценил ваш комментарий", "%s оценила ваш комментарий"), who) + quote
            ActivityKind.PrismLike -> tf(verb("%s оценили ваш PRISM", "%s оценил ваш PRISM", "%s оценила ваш PRISM"), who) + quote
            ActivityKind.PrismComment -> tf(verb("%s прокомментировали ваш PRISM", "%s прокомментировал ваш PRISM", "%s прокомментировала ваш PRISM"), who) + quote
            ActivityKind.FriendRequest -> tf("%s хочет дружить с вами", who)
            ActivityKind.FriendAccepted -> tf(verb("%s приняли вашу заявку в друзья", "%s принял вашу заявку в друзья", "%s приняла вашу заявку в друзья"), who)
            ActivityKind.PollVotes -> tf("В вашем опросе%s — новые голоса: %s", quote, g.count)
            ActivityKind.GroupJoin -> tf(verb("%s вступили в вашу группу", "%s вступил в вашу группу", "%s вступила в вашу группу"), who) + quote
            ActivityKind.Live -> tf(verb("%s начали прямой эфир", "%s начал прямой эфир", "%s начала прямой эфир"), who) + quote
            ActivityKind.Repost -> tf(verb("%s поделились вашей записью", "%s поделился вашей записью", "%s поделилась вашей записью"), who) + quote
            ActivityKind.Tagged -> tf(verb("%s отметили вас на фото", "%s отметил вас на фото", "%s отметила вас на фото"), who) + quote
            ActivityKind.StoryReply -> tf(verb("%s ответили на вашу историю", "%s ответил на вашу историю", "%s ответила на вашу историю"), who) + quote
            ActivityKind.MissedCall -> tf("Пропущенный звонок: %s", who)
        }
    }

    /** Добавить событие в начало и не хранить лишнего. */
    fun add(items: List<ActivityItem>, item: ActivityItem): List<ActivityItem> = (listOf(item) + items).take(LIMIT)
}
