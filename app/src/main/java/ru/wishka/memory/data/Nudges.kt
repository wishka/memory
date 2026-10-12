package ru.wishka.memory.data

import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf

/** Раздел уведомлений — по нему работают выключатели в настройках. */
enum class NudgeKind { Friends, Groups, Reactions, Follows, Messages, Recap }

/** Куда ведёт нажатие на уведомление. */
enum class NudgeTarget { Post, Group, Person, Chat, Recap, Requests, Together }

/**
 * Персональное уведомление: не «Вы давно не заходили», а что именно
 * случилось и почему это вам интересно — «Ваша подруга Вера только что
 * отметила фильм «Дюна», который вы обсуждали». Тексты собирает Nudges —
 * одинаково для локальных уведомлений и для пушей с сервера (FCM/APNs
 * присылают только факты, текст складывается на телефоне на его языке).
 */
data class Nudge(
    val kind: NudgeKind,
    val title: String,
    val text: String,
    val target: NudgeTarget,
    val targetId: Long = 0,
)

object Nudges {
    /** Мужские имена на «-а» и «-я»: у остальных такие окончания — женские. */
    private val MALE_A = setOf("илья", "никита", "фома", "кузьма", "савва", "лука", "миша", "саша", "женя", "ваня", "петя", "дима", "коля", "серёжа", "лёша", "паша", "гоша", "толя", "юра")

    fun firstName(user: User): String = user.name.substringBefore(' ').ifEmpty { user.name }

    /** Женское имя — по окончанию; нужно для «отметил / отметила». */
    fun female(user: User): Boolean {
        val n = firstName(user).lowercase()
        return (n.endsWith("а") || n.endsWith("я")) && n !in MALE_A
    }

    private fun g(user: User, male: String, female: String) = if (female(user)) female else male

    /**
     * Обсуждали ли вы это: название фильма, книги или места встречается в
     * ваших записях, комментариях или сообщениях.
     */
    fun discussed(subject: String, myTexts: List<String>): Boolean {
        val s = subject.trim().lowercase().replace('ё', 'е')
        if (s.length < 3) return false
        // «Дюна» — «дюну», «дюной»: сравниваем без последней гласной
        val stem = if (s.length >= 4 && s.last() in "аяоеиыуюйь") s.dropLast(1) else s
        return myTexts.any { it.lowercase().replace('ё', 'е').contains(stem) }
    }

    /** Друг отметил чек-ин. myKm — сколько вы сами набегали за месяц (для спортивных). */
    fun friendCheckIn(friend: User, c: CheckIn, discussed: Boolean, myKm: Double, postId: Long): Nudge {
        val name = firstName(friend)
        val subject = c.subject
        val amount = amountNumber(c.amount)
        val text = when (c.activity) {
            "movie" -> when {
                subject.isEmpty() -> tf("%s смотрит кино — спросите, что именно", name)
                discussed -> tf(g(friend, "Ваш друг %s только что отметил фильм «%s», который вы обсуждали", "Ваша подруга %s только что отметила фильм «%s», который вы обсуждали"), name, subject)
                else -> tf("%s смотрит «%s». Видели?", name, subject)
            }
            "series" -> if (discussed && subject.isNotEmpty())
                tf(g(friend, "Ваш друг %s взялся за сериал «%s», который вы обсуждали", "Ваша подруга %s взялась за сериал «%s», который вы обсуждали"), name, subject)
            else tf("%s смотрит сериал «%s»", name, subject.ifEmpty { "…" })
            "book" -> if (discussed && subject.isNotEmpty())
                tf(g(friend, "Ваш друг %s начал читать «%s» — книгу, которую вы обсуждали", "Ваша подруга %s начала читать «%s» — книгу, которую вы обсуждали"), name, subject)
            else tf("%s читает «%s»", name, subject.ifEmpty { "…" })
            "run", "bike", "walk", "swim" -> {
                val did = when (c.activity) {
                    "run" -> tf(g(friend, "%s пробежал %s км", "%s пробежала %s км"), name, amount)
                    "bike" -> tf(g(friend, "%s проехал %s км на велосипеде", "%s проехала %s км на велосипеде"), name, amount)
                    "walk" -> tf(g(friend, "%s прошёл %s км пешком", "%s прошла %s км пешком"), name, amount)
                    else -> tf(g(friend, "%s проплыл %s км", "%s проплыла %s км"), name, amount)
                }
                if (myKm > 0) tf("%s. У вас за месяц — %s км, не отставайте!", did, amountNumber(myKm)) else did
            }
            "coffee", "food", "place", "travel", "concert" -> when {
                subject.isEmpty() -> "$name: ${c.emoji} ${c.displayTitle}"
                discussed -> tf("%s сейчас в «%s» — вы как раз это обсуждали", name, subject)
                else -> tf("%s сейчас здесь: %s", name, subject)
            }
            else -> listOf(c.displayTitle, subject, c.amountTitle).filter { it.isNotEmpty() }
                .joinToString(" · ").let { "$name: ${c.emoji} $it" }
        }
        return Nudge(NudgeKind.Friends, friend.name, text, NudgeTarget.Post, postId)
    }

    fun groupMember(group: Group, person: User) = Nudge(
        NudgeKind.Groups, group.name,
        tf("В вашей группе «%s» новый участник — %s", group.name, person.name),
        NudgeTarget.Group, group.id.toLong(),
    )

    fun groupPost(group: Group, author: User, preview: String) = Nudge(
        NudgeKind.Groups, group.name,
        tf("%s пишет: %s", firstName(author), preview.take(80)),
        NudgeTarget.Group, group.id.toLong(),
    )

    fun liked(person: User, post: Post): Nudge {
        val name = firstName(person)
        val c = post.checkIn
        val text = if (c != null) tf(g(person, "%s оценил ваш чек-ин «%s»", "%s оценила ваш чек-ин «%s»"), name, c.subject.ifEmpty { c.displayTitle })
        else tf(g(person, "%s оценил вашу запись «%s»", "%s оценила вашу запись «%s»"), name, preview(post))
        return Nudge(NudgeKind.Reactions, person.name, text, NudgeTarget.Post, post.id)
    }

    /** Заявка на подписку; common — общие интересы, если есть. */
    fun followRequest(person: User, common: List<String>) = Nudge(
        NudgeKind.Follows, person.name,
        if (common.isEmpty()) tf("%s хочет подписаться на вас", person.name)
        else tf("%s хочет подписаться на вас — у вас общие интересы: %s", person.name, common.take(2).joinToString(", ") { t(it) }),
        NudgeTarget.Requests,
    )

    fun requestAccepted(person: User) = Nudge(
        NudgeKind.Follows, person.name,
        tf(g(person, "%s принял вашу заявку — теперь вам видны его записи", "%s приняла вашу заявку — теперь вам видны её записи"), firstName(person)),
        NudgeTarget.Person, person.id.toLong(),
    )

    fun message(peer: User, text: String, chatId: Int) =
        Nudge(NudgeKind.Messages, peer.name, text.take(120), NudgeTarget.Chat, chatId.toLong())

    /** targetId — год × 100 + месяц (0 — итоги года): нажатие откроет именно этот период. */
    fun recap(recap: Recap) = Nudge(
        NudgeKind.Recap,
        tf("Итоги: %s", recap.title),
        if (recap.period == RecapPeriod.Year) tf("%s. Посмотрите, каким был ваш год", recap.headline)
        else tf("%s. Посмотрите, каким был ваш месяц", recap.headline),
        NudgeTarget.Recap,
        recap.year * 100L + recap.month,
    )

    // ---------- Вместе: targetId — раздел вкладки (TogetherSection.ordinal) ----------

    private fun together(person: User, text: String, section: TogetherSection) =
        Nudge(NudgeKind.Friends, person.name, text, NudgeTarget.Together, section.ordinal.toLong())

    fun dealAgreed(person: User, deal: SkillDeal): Nudge = together(
        person,
        when {
            deal.give.isNotEmpty() && deal.get.isNotEmpty() -> tf("Договорились об обмене: вы учите «%s», вас — «%s»", deal.give, deal.get)
            deal.get.isNotEmpty() -> tf("Договорились: урок «%s» за %s из банка времени", deal.get, minutesTitle(deal.minutes))
            else -> tf("Договорились: вы учите «%s» — в банк времени придёт %s", deal.give, minutesTitle(deal.minutes))
        },
        TogetherSection.Skills,
    )

    fun tipReceived(person: User, journey: Journey, day: Int) =
        together(person, tf("%s даёт совет к «День %s: %s»", firstName(person), day, journey.title), TogetherSection.Journeys)

    fun drawingTurn(person: User, drawing: CollabDrawing) = together(
        person,
        if (drawing.finished) tf(g(person, "%s дорисовал «%s» — рисунок готов", "%s дорисовала «%s» — рисунок готов"), firstName(person), drawing.title)
        else tf(g(person, "%s дорисовал «%s» — ваша очередь", "%s дорисовала «%s» — ваша очередь"), firstName(person), drawing.title),
        TogetherSection.Drawing,
    )

    fun outingJoined(person: User, outing: Outing) =
        together(person, tf("%s идёт с вами: «%s»", firstName(person), outing.title), TogetherSection.Outings)

    fun shareHanded(person: User, item: ShareItem) =
        together(person, tf("%s передаёт вам «%s»", firstName(person), item.title), TogetherSection.Sharing)

    fun shareRequested(person: User, item: ShareItem) =
        together(person, tf("%s просит «%s»", firstName(person), item.title), TogetherSection.Sharing)

    fun cacheFound(person: User, cache: Cache) =
        together(person, tf(g(person, "%s нашёл ваш клад «%s»", "%s нашла ваш клад «%s»"), firstName(person), cache.title), TogetherSection.Caches)

    /** Можно ли показывать: общий выключатель и выключатель раздела. */
    fun allowed(n: Nudge, s: Notifications): Boolean = s.all && when (n.kind) {
        NudgeKind.Friends -> s.friends
        NudgeKind.Groups -> s.groups
        NudgeKind.Reactions -> s.reactions
        NudgeKind.Follows -> s.follows
        NudgeKind.Messages -> s.messages
        NudgeKind.Recap -> s.recap
    }

    private fun preview(post: Post): String {
        val text = post.text.replace('\n', ' ').trim()
        return when {
            text.isEmpty() -> t("фото")
            text.length <= 40 -> text
            else -> text.take(40).trimEnd() + "…"
        }
    }

    /**
     * Пуш с сервера: в данных только факты (кто, что, где) — текст
     * складывается здесь же, на языке телефона. Неизвестный тип — берём
     * готовые title / body из пуша. См. docs/PUSH.md.
     */
    fun fromPush(data: Map<String, String>): Nudge? {
        val actor = User(data["actorId"]?.toIntOrNull() ?: 0, data["actor"].orEmpty(), "")
        val title = data["title"].orEmpty()
        val body = data["body"].orEmpty()
        return when (data["type"]) {
            "friend_checkin" -> {
                val activity = data["activity"].orEmpty()
                val builtIn = CHECK_IN_ACTIVITIES.firstOrNull { it.id == activity }
                val c = CheckIn(
                    activity, data["emoji"] ?: builtIn?.emoji ?: "✦", builtIn?.title ?: data["activityTitle"].orEmpty(),
                    data["subject"].orEmpty(), data["amount"]?.toDoubleOrNull() ?: 0.0, builtIn?.unit ?: data["unit"].orEmpty(),
                )
                friendCheckIn(actor, c, data["discussed"] == "1", data["myKm"]?.toDoubleOrNull() ?: 0.0, data["postId"]?.toLongOrNull() ?: 0)
            }
            "group_member" -> groupMember(Group(data["groupId"]?.toIntOrNull() ?: 0, data["group"].orEmpty(), "", "", 0, 0), actor)
            "follow_request" -> followRequest(actor, data["common"]?.split(',')?.filter { it.isNotBlank() }.orEmpty())
            "request_accepted" -> requestAccepted(actor)
            "message" -> message(actor, body, data["chatId"]?.toIntOrNull() ?: 0)
            else -> if (body.isNotEmpty()) Nudge(NudgeKind.Friends, title.ifEmpty { "Memory" }, body, NudgeTarget.Post) else null
        }
    }
}
