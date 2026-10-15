package ru.wishka.memory.data

import ru.wishka.memory.i18n.t

/**
 * Жалобы на контент и людей. Пока сервера нет, жалоба копится в очереди
 * (Snapshot.reports) и скрывает пожаловавшемуся то, на что он пожаловался;
 * с сервером очередь уйдёт модераторам — разобрать в течение суток.
 * Те же правила — в Reports.swift.
 */
enum class ReportReason(val id: String, private val ru: String) {
    Spam("spam", "Спам или мошенничество"),
    Abuse("abuse", "Оскорбления или травля"),
    Hate("hate", "Ненависть или призывы к насилию"),
    Adult("adult", "Откровенный контент"),
    Illegal("illegal", "Запрещено законом"),
    Minors("minors", "Опасно для детей"),
    SelfHarm("self_harm", "Самоповреждение или суицид"),
    Copyright("copyright", "Нарушение авторских прав"),
    Impersonation("impersonation", "Выдаёт себя за другого"),
    Other("other", "Другое"),
    ;

    val title: String get() = t(ru)

    companion object {
        fun of(id: String?): ReportReason = entries.firstOrNull { it.id == id } ?: Other
    }
}

enum class ReportKind(val id: String) {
    Post("post"), Comment("comment"), Prism("prism"), Message("message"), Person("person");

    companion object {
        fun of(id: String?): ReportKind = entries.firstOrNull { it.id == id } ?: Post
    }
}

/**
 * На что жалоба. id — номер записи, комментария, PRISM или сообщения
 * (для человека — его номер); chatId — переписка сообщения; authorId —
 * кого заблокировать по желанию; preview — пара слов для модератора.
 */
data class ReportTarget(
    val kind: ReportKind,
    val id: Long,
    val authorId: Int,
    val authorName: String = "",
    val chatId: Int = 0,
    val preview: String = "",
)

data class Report(
    val id: Long,
    val target: ReportTarget,
    val reason: ReportReason,
    val note: String = "",
    val createdAt: Long = 0,
)
