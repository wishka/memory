package ru.wishka.memory.data

import ru.wishka.memory.i18n.L10n
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf

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
    /** Своё фото вместо букв: путь к круглой обрезке в памяти приложения; "" — буквы. */
    val avatar: String = "",
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
) {
    /** Музыка — документ со звуком: в записи он играет прямо в карточке. */
    val isAudio: Boolean
        get() = kind == MediaKind.Document &&
            (mime.startsWith("audio/") || path.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS)
}

/** Расширения звуковых файлов — для вложений-«музыки». */
val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus")

/** Комментарий к записи или PRISM. createdAt — мс; 0 — у демо-комментариев, тогда время из minutesAgo. */
data class Comment(
    val id: Long,
    val author: User,
    val text: String,
    val minutesAgo: Int = 0,
    val createdAt: Long = 0,
    val likes: Int = 0,
    val liked: Boolean = false,
    /** Ответ кому-то в обсуждении: имя того, кому ответили; "" — просто комментарий. */
    val replyTo: String = "",
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

/**
 * Переписка. pinnedId — закреплённое сообщение: видно полоской вверху.
 * onTop — сама переписка закреплена вверху списка чатов; muted — без звука.
 */
data class Chat(
    val id: Int,
    val peer: User,
    val messages: List<Message>,
    val unread: Int = 0,
    val typing: Boolean = false,
    val pinnedId: Long? = null,
    val onTop: Boolean = false,
    val muted: Boolean = false,
) {
    val pinned: Message? get() = pinnedId?.let { id -> messages.firstOrNull { it.id == id } }

    val last: Message? get() = messages.lastOrNull()
}

/** «5 мин», «2 ч», «вчера», «3 дн» — время записи в ленте. */
fun agoTitle(minutes: Int): String = when {
    minutes < 1 -> t("сейчас")
    minutes < 60 -> tf("%s мин", minutes)
    minutes < 60 * 24 -> tf("%s ч", minutes / 60)
    minutes < 60 * 48 -> t("вчера")
    else -> tf("%s дн", minutes / (60 * 24))
}

/**
 * Склонение: plural(5, "запись", "записи", "записей") → «5 записей».
 * По-английски — «1 post» / «5 posts»: формы берутся из словаря.
 */
fun plural(n: Int, one: String, few: String, many: String): String {
    if (L10n.english) return "$n ${t(if (n == 1) one else many)}"
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
    /** Уведомления группы выключены. */
    val muted: Boolean = false,
    /** Своя заставка — фото; null — туманность по seed. */
    val cover: Attachment? = null,
    /** Закреплённая запись — первой на странице группы. */
    val pinnedPostId: Long? = null,
    /** Писать в группу могут только её создатели; false — все участники. */
    val onlyOwnerPosts: Boolean = false,
)

/** Интересы в профиле — те же темы, без «Другое». */
val INTERESTS: List<String> get() = GROUP_TOPICS.filter { it != "Другое" }

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
    /** Свой ролик: готовое видео после редактора; null — демо, «кадр» рисует PrismArt. */
    val video: Attachment? = null,
    /** Обложка своего ролика — кадр, выбранный в редакторе. */
    val cover: Attachment? = null,
    val createdAt: Long = 0,
)

/**
 * Трек в «Моей музыке». Файл — копия в памяти приложения (path), его
 * видит и слушает только владелец: пока сервера нет, это личная
 * библиотека на телефоне.
 */
data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val path: String,
    val durationMs: Long = 0,
    val addedAt: Long = 0,
    /** Лицензия трека из медиатеки («CC BY 4.0»); "" — свой файл. */
    val license: String = "",
    /** Страница трека на Jamendo или Openverse — для указания автора. */
    val sourceUrl: String = "",
)

/** Плейлист: название и треки по порядку. */
data class Playlist(
    val id: Long,
    val name: String,
    val trackIds: List<String> = emptyList(),
)

/** «3:07» — длительность трека или ролика. */
fun durationTitle(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/** То, что человек рассказывает о себе в профиле. Сохраняется вместе с аккаунтом. */
data class ProfileInfo(
    val bio: String = "",
    val city: String = "",
    val birthday: String = "",
    val website: String = "",
    val interests: List<String> = emptyList(),
    /** Путь к своему фото; "" — аватар из букв. */
    val avatar: String = "",
)

/** Кому видно или кому можно: всем, тем, на кого вы подписаны, никому. */
enum class Audience(private val ru: String) {
    Everyone("Все"),
    Following("Мои подписки"),
    Nobody("Никто"),
    ;

    val title: String get() = t(ru)
}

/** Настройки приватности. Пока сервера нет, хранятся на телефоне вместе с аккаунтом. */
data class Privacy(
    /** Кто видит город, день рождения, сайт и интересы. */
    val profileInfo: Audience = Audience.Everyone,
    /** Кто видит ваши подписки и подписчиков. */
    val followLists: Audience = Audience.Everyone,
    /** Кто может написать вам. */
    val messages: Audience = Audience.Everyone,
    /** Кто может комментировать ваши записи. */
    val comments: Audience = Audience.Everyone,
    /** Показывать другим, что вы в сети. */
    val showOnline: Boolean = true,
    /** Показывать день рождения. */
    val showBirthday: Boolean = true,
)

/** Какие уведомления присылать. all = false выключает все разом. */
data class Notifications(
    val all: Boolean = true,
    val messages: Boolean = true,
    val reactions: Boolean = true,
    val groups: Boolean = true,
    val follows: Boolean = true,
)
