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

/** Что приложено к записи или сообщению. */
enum class MediaKind { Photo, Video, Document }

/**
 * Вложение: фото, видео или документ. Пока сервера нет, файл копируется
 * в память приложения (см. MediaFiles), path — путь к копии. Так вложение
 * переживает перезапуск и не пропадает, если человек удалит оригинал из
 * галереи.
 */
data class Attachment(
    val id: String,
    val kind: MediaKind,
    val path: String,
    /** Имя для документов: «Договор.pdf». */
    val name: String = "",
    val size: Long = 0,
    val mime: String = "",
)

/** Комментарий к записи или PRISM. createdAt — мс; 0 — у демо-комментариев, тогда время из minutesAgo. */
data class Comment(
    val id: Long,
    val author: User,
    val text: String,
    val minutesAgo: Int = 0,
    val createdAt: Long = 0,
)

/**
 * Запись в ленте. artSeed != null — к записи приложена процедурная
 * «картинка» (туманность, см. Nebula) — так выглядят демо-записи.
 * media — настоящие фото и видео, которые приложил человек.
 *
 * comments — сколько всего комментариев (у демо-записей их больше, чем
 * загружено), commentList — те, что видны в обсуждении.
 * createdAt — мс, у своих записей; у демо — 0, время из minutesAgo.
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
    val media: List<Attachment> = emptyList(),
    val commentList: List<Comment> = emptyList(),
    val createdAt: Long = 0,
)

/** Минуты с момента createdAt, а для демо-данных — заданные заранее. */
fun minutesSince(createdAt: Long, minutesAgo: Int, now: Long = System.currentTimeMillis()): Int =
    if (createdAt > 0) ((now - createdAt) / 60_000).toInt().coerceAtLeast(0) else minutesAgo

/** На какое сообщение отвечают: номер, чьё оно и начало текста — для цитаты над ответом. */
data class ReplyRef(
    val messageId: Long,
    val author: String,
    val preview: String,
)

/**
 * Сообщение; text может быть пустым, если отправлены только вложения.
 * replyTo — ответ на другое сообщение; forwardedFrom — имя автора
 * пересланного сообщения.
 */
data class Message(
    val id: Long,
    val fromMe: Boolean,
    val text: String,
    val time: String,
    val attachments: List<Attachment> = emptyList(),
    val replyTo: ReplyRef? = null,
    val forwardedFrom: String? = null,
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

/** Темы групп — для создания и поиска, по алфавиту. */
val GROUP_TOPICS = listOf(
    "Авто и мото", "Анимация и комиксы", "Архитектура", "Астрономия", "Бизнес", "Блогинг",
    "Дизайн", "Дети и родители", "Дом и интерьер", "Еда и кулинария", "Животные", "Здоровье",
    "Игры", "Иностранные языки", "Искусство", "История", "Кино", "Книги", "Красота и мода",
    "Музыка", "Наука", "Образование", "Психология", "Природа", "Программирование", "Путешествия",
    "Работа и карьера", "Рукоделие", "Сад и огород", "Спорт", "Танцы", "Театр", "Технологии",
    "Фитнес", "Философия", "Финансы", "Фото", "Юмор", "Другое",
)

/**
 * PRISM — короткое вертикальное видео. Пока сервера и загрузки видео нет,
 * «кадр» рисуется анимацией (см. PrismArt) по seed — один и тот же ролик
 * всегда выглядит одинаково.
 *
 * saved — в закладках («Сохранённое» в профиле).
 */
data class Prism(
    val id: Long,
    val author: User,
    val caption: String,
    val music: String,
    val seed: Int,
    val likes: Int,
    val comments: Int,
    val liked: Boolean = false,
    val durationMs: Int = 9_000,
    val saved: Boolean = false,
    val commentList: List<Comment> = emptyList(),
)

/** То, что человек рассказывает о себе в профиле. Сохраняется вместе с аккаунтом. */
data class ProfileInfo(
    val bio: String = "",
    val city: String = "",
    val birthday: String = "",
    val website: String = "",
    val interests: List<String> = emptyList(),
)
