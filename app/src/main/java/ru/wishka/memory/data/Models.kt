package ru.wishka.memory.data

/** Человек в Memory. hue — 0..1, оттенок его аватара-градиента. */
data class User(
    val id: Int,
    val name: String,
    val handle: String,
    val bio: String = "",
    val hue: Float = 0.5f,
    val online: Boolean = false,
) {
    val initials: String
        get() = name.split(' ').filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
}

/**
 * Запись в ленте. artSeed != null — к записи приложена «картинка»;
 * пока сервера нет, она рисуется процедурно (туманность), см. Nebula.
 */
data class Post(
    val id: Long,
    val author: User,
    val text: String,
    val minutesAgo: Int,
    val artSeed: Int? = null,
    val likes: Int = 0,
    val liked: Boolean = false,
    val comments: Int = 0,
)

data class Message(
    val id: Long,
    val fromMe: Boolean,
    val text: String,
    val time: String,
)

data class Chat(
    val id: Int,
    val peer: User,
    val messages: List<Message>,
    val unread: Int = 0,
    val typing: Boolean = false,
) {
    val last: Message? get() = messages.lastOrNull()
}

/** «5 мин», «2 ч», «вчера», «3 дн» — время записи в ленте. */
fun agoTitle(minutes: Int): String = when {
    minutes < 1 -> "сейчас"
    minutes < 60 -> "$minutes мин"
    minutes < 60 * 24 -> "${minutes / 60} ч"
    minutes < 60 * 48 -> "вчера"
    else -> "${minutes / (60 * 24)} дн"
}

/** Русское склонение: plural(5, "запись", "записи", "записей") → «5 записей». */
fun plural(n: Int, one: String, few: String, many: String): String {
    val mod10 = n % 10
    val mod100 = n % 100
    val word = when {
        mod10 == 1 && mod100 != 11 -> one
        mod10 in 2..4 && mod100 !in 12..14 -> few
        else -> many
    }
    return "$n $word"
}
