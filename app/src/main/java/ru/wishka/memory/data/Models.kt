package ru.wishka.memory.data

/**
 * Человек в Memory. hue — 0..1, оттенок его аватара-градиента.
 * followers / followingCount — числа для карточки; у «себя» подписки
 * считаются по настоящему списку.
 */
data class User(
    val id: Int,
    val name: String,
    val handle: String,
    val bio: String = "",
    val hue: Float = 0.5f,
    val online: Boolean = false,
    val city: String = "",
    /** «14 марта» — без года: возраст человек раскрывает сам, если хочет. */
    val birthday: String = "",
    val website: String = "",
    val interests: List<String> = emptyList(),
    /** «сентябрь 2026» */
    val joined: String = "",
    val followers: Int = 0,
    val followingCount: Int = 0,
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

/** Группа по интересам. seed — обложка (процедурная туманность). */
data class Group(
    val id: Int,
    val name: String,
    val description: String,
    val topic: String,
    val members: Int,
    val seed: Int,
    val joined: Boolean = false,
    val mine: Boolean = false,
    val posts: List<Post> = emptyList(),
)

/** Темы групп — для создания и поиска. */
val GROUP_TOPICS = listOf("Музыка", "Фото", "Путешествия", "Книги", "Природа", "Искусство", "Кино", "Спорт")

/**
 * Короткое вертикальное видео. Пока сервера и загрузки видео нет, «кадр»
 * рисуется анимацией (см. ReelArt) по seed — один и тот же Reels всегда
 * выглядит одинаково.
 */
data class Reel(
    val id: Long,
    val author: User,
    val caption: String,
    val music: String,
    val seed: Int,
    val likes: Int,
    val comments: Int,
    val liked: Boolean = false,
    val durationMs: Int = 9_000,
)

/** То, что человек рассказывает о себе в профиле. Сохраняется вместе с аккаунтом. */
data class ProfileInfo(
    val bio: String = "",
    val city: String = "",
    val birthday: String = "",
    val website: String = "",
    val interests: List<String> = emptyList(),
)
