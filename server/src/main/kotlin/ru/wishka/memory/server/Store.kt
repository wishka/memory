package ru.wishka.memory.server

/** Человек на сервере. Номера — с 100 000: не пересекаются с демо-людьми приложения. */
data class UserRow(
    val id: Int,
    val login: String,
    val name: String,
    val hue: Float,
    val passHash: String,
    val salt: String,
    val publicKey: String = "",
    /** Профиль и приватность (JSON, как PUT /v1/me); "" — ещё не присылали. */
    val profile: String = "",
    val profileUpdated: Long = 0,
    val created: Long = 0,
    /** Закрытый профиль: подписка — по заявке, записи и истории — только подписчикам. */
    val closed: Boolean = false,
    /** Заблокирован модератором: войти нельзя, его не видно. */
    val banned: Boolean = false,
)

/** Устройство, с которого вошли; токены хранятся только отпечатками (SHA-256). */
data class DeviceRow(
    val id: String,
    val userId: Int,
    val name: String,
    val platform: String,
    val lastSeen: Long,
    val refreshHash: String,
    val accessHash: String,
    val accessUntil: Long,
    /** Токен пушей FCM этого телефона; "" — пуши не нужны или ещё не прислал. */
    val pushToken: String = "",
)

/** Запись: body — JSON записи в формате API (без автора и счётчиков — их сервер подставляет сам). */
data class PostRow(val id: Long, val author: Int, val created: Long, val body: String, val likes: Int, val commentsOff: Boolean)

data class ChatRow(val id: Int, val kind: String, val title: String, val owner: Int, val created: Long) {
    val direct: Boolean get() = kind == "direct"
}

data class MemberRow(val chatId: Int, val userId: Int, val admin: Boolean, val accepted: Boolean)

data class MessageRow(val id: Long, val chatId: Int, val author: Int, val sentAt: Long, val body: String)

data class UploadRow(val id: String, val owner: Int, val length: Long, val offset: Long, val meta: String, val created: Long)

/** Подписка follower → followee; accepted = false — заявка к закрытому профилю. */
data class FollowRow(val follower: Int, val followee: Int, val accepted: Boolean, val created: Long)

/** Комментарий: body — JSON (text, replyTo, sticker) как у приложения. */
data class CommentRow(val id: Long, val postId: Long, val author: Int, val created: Long, val body: String, val likes: Int)

/** История: живёт до expires; body — JSON (media, text, bg, seed). */
data class StoryRow(val id: Long, val author: Int, val created: Long, val expires: Long, val body: String, val closeFriends: Boolean)

data class StoryViewRow(val storyId: Long, val userId: Int, val at: Long, val liked: Boolean)

/**
 * Жалоба. kind — post, comment, story, prism, message, person; target —
 * номер того, на что жалуются (у сообщений и PRISM — номер телефона, тогда
 * модератор смотрит preview); author — на кого.
 */
data class ReportRow(
    val id: Long,
    val reporter: Int,
    val kind: String,
    val target: Long,
    val author: Int,
    val reason: String,
    val note: String,
    val preview: String,
    val created: Long,
    /** open — ждёт модератора; dismissed, removed, banned — решено. */
    val status: String = "open",
)

/** Сохранённый ответ на запрос с Idempotency-Key: повтор получит его же. */
data class IdemRow(val status: Int, val body: String)

/**
 * Хранилище сервера. Две реализации: PgStore (PostgreSQL — в Docker) и
 * MemoryStore (тесты и запуск без базы). Логика — в Core, здесь только
 * чтение и запись.
 */
interface Store {
    // Люди
    fun createUser(login: String, name: String, hue: Float, passHash: String, salt: String, now: Long): UserRow?
    fun user(id: Int): UserRow?
    fun userByLogin(login: String): UserRow?
    fun searchUsers(query: String, limit: Int): List<UserRow>
    fun updateUser(row: UserRow)
    fun updatePassword(id: Int, passHash: String, salt: String)

    // Устройства
    fun saveDevice(row: DeviceRow)
    fun device(id: String): DeviceRow?
    fun deviceByAccess(hash: String): DeviceRow?
    fun deviceByRefresh(hash: String): DeviceRow?
    fun devices(userId: Int): List<DeviceRow>
    fun deleteDevice(id: String)
    /** Токен пушей больше не действует (FCM: UNREGISTERED) — забыть. */
    fun clearPushToken(token: String)

    // Записи
    fun createPost(author: Int, created: Long, body: String): PostRow
    fun post(id: Long): PostRow?
    /** Новые сверху; после (createdAt, id) — следующая страница. author == null — все. */
    fun posts(author: Int?, beforeCreated: Long?, beforeId: Long?, limit: Int): List<PostRow>
    fun setCommentsOff(id: Long, off: Boolean)
    /** Поставить или снять «нравится»; ответ — сколько теперь. */
    fun setLike(postId: Long, userId: Int, liked: Boolean): Int
    fun likedBy(userId: Int, postIds: List<Long>): Set<Long>

    /** Записи этих авторов (лента друзей). */
    fun postsBy(authors: Collection<Int>, beforeCreated: Long?, beforeId: Long?, limit: Int): List<PostRow>
    /** «Интересное»: записи открытых профилей, кроме этих авторов. */
    fun explore(exclude: Collection<Int>, beforeCreated: Long?, beforeId: Long?, limit: Int): List<PostRow>

    // Подписки
    fun follow(follower: Int, followee: Int): FollowRow?
    fun saveFollow(row: FollowRow)
    fun removeFollow(follower: Int, followee: Int)
    /** На кого подписан человек (accepted) или куда отправил заявки (!accepted). */
    fun following(userId: Int, accepted: Boolean = true): List<Int>
    /** Подписчики (accepted) или входящие заявки (!accepted). */
    fun followers(userId: Int, accepted: Boolean = true): List<Int>

    // Комментарии
    fun createComment(postId: Long, author: Int, created: Long, body: String): CommentRow
    fun comment(id: Long): CommentRow?
    /** По порядку (старые сверху), после afterId. */
    fun comments(postId: Long, afterId: Long?, limit: Int): List<CommentRow>
    /** Последние n — для превью под записью (старые сверху). */
    fun lastComments(postId: Long, n: Int): List<CommentRow>
    fun commentCounts(postIds: Collection<Long>): Map<Long, Int>
    fun deleteComment(id: Long)
    fun setCommentLike(commentId: Long, userId: Int, liked: Boolean): Int
    fun commentsLikedBy(userId: Int, ids: Collection<Long>): Set<Long>

    // Истории
    fun createStory(author: Int, created: Long, expires: Long, body: String, closeFriends: Boolean): StoryRow
    fun story(id: Long): StoryRow?
    /** Живые (expires > now) истории этих авторов, по времени. */
    fun stories(authors: Collection<Int>, now: Long): List<StoryRow>
    fun deleteStory(id: Long)
    fun saveStoryView(row: StoryViewRow)
    fun storyViews(storyId: Long): List<StoryViewRow>
    fun storiesSeenBy(userId: Int, ids: Collection<Long>): Set<Long>
    fun closeFriends(owner: Int): Set<Int>
    fun setCloseFriends(owner: Int, ids: Collection<Int>)

    // Переписки
    fun createChat(row: ChatRow, members: List<MemberRow>): Boolean
    fun chat(id: Int): ChatRow?
    fun directChat(a: Int, b: Int): ChatRow?
    /** Все переписки, где человек участник. */
    fun chatsOf(userId: Int): List<ChatRow>
    fun updateChatTitle(id: Int, title: String)
    fun members(chatId: Int): List<MemberRow>
    fun saveMember(row: MemberRow)
    fun removeMember(chatId: Int, userId: Int)
    fun addMessage(chatId: Int, author: Int, sentAt: Long, body: String): MessageRow
    /** Новые сверху; before — номер сообщения (следующая страница). */
    fun messages(chatId: Int, before: Long?, limit: Int): List<MessageRow>
    /** Сообщение по переписке, автору и времени сервера (для жалоб). */
    fun messageAt(chatId: Int, author: Int, sentAt: Long): MessageRow?

    // Загрузки
    fun createUpload(row: UploadRow)
    fun upload(id: String): UploadRow?
    fun setUploadOffset(id: String, offset: Long)
    fun deleteUpload(id: String)

    // Модерация
    fun createReport(row: ReportRow): ReportRow
    fun report(id: Long): ReportRow?
    /** status == null — все; новые сверху. */
    fun reports(status: String?, limit: Int): List<ReportRow>
    fun setReportStatus(id: Long, status: String)
    fun setBlock(blocker: Int, blocked: Int, on: Boolean)
    /** Кого заблокировал человек. */
    fun blockedBy(userId: Int): Set<Int>
    /** Есть ли блокировка в любую сторону. */
    fun blockedEither(a: Int, b: Int): Boolean
    fun deletePost(id: Long)
    fun uploadsOf(owner: Int): List<UploadRow>
    /** Удалить человека со всем, что его (устройства, записи, подписки, истории…). */
    fun deleteUser(id: Int)

    // Повторы
    fun idem(userId: Int, key: String): IdemRow?
    fun saveIdem(userId: Int, key: String, row: IdemRow, now: Long)
}
