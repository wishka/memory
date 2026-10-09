package ru.wishka.memory.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.wishka.memory.data.Chat
import ru.wishka.memory.data.Demo
import ru.wishka.memory.data.GROUP_TOPICS
import ru.wishka.memory.data.Group
import ru.wishka.memory.data.ProfileInfo
import ru.wishka.memory.data.Prism
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.Comment
import ru.wishka.memory.data.ReplyRef
import ru.wishka.memory.data.Message
import ru.wishka.memory.data.Post
import ru.wishka.memory.data.User
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Разделы профиля за счётчиками: записи, подписчики, подписки, группы, сохранённое. */
enum class ProfileSection(val title: String) {
    Posts("Записи"),
    Followers("Подписчики"),
    Following("Подписки"),
    Groups("Группы"),
    Saved("Сохранённое"),
}

/** Вкладки нижней панели — в порядке слева направо. */
enum class Tab(val title: String) {
    Feed("Лента"),
    Stories("Истории"),
    Prism("PRISM"),
    Chats("Чаты"),
    Groups("Группы"),
    Profile("Профиль"),
}

data class UiState(
    val tab: Tab = Tab.Feed,
    val me: User = Demo.me,
    val posts: List<Post> = Demo.posts,
    val chats: List<Chat> = Demo.chats,
    /** Открытая переписка поверх вкладки «Чаты»; null — список чатов. */
    val openChatId: Int? = null,
    val notifications: Boolean = true,
    /** На кого подписан человек: их записи — во вкладке «Истории». */
    val following: Set<Int> = Demo.following,
    val prisms: List<Prism> = Demo.prisms,
    /** Открытое обсуждение записи (в ленте, историях, группе); null — закрыто. */
    val commentsPostId: Long? = null,
    /** Открытое обсуждение PRISM; null — закрыто. */
    val commentsPrismId: Long? = null,
    /** Фото или видео на весь экран; null — не открыто. */
    val viewing: Attachment? = null,
    /** Раздел профиля поверх вкладки «Профиль»; null — сам профиль. */
    val profileSection: ProfileSection? = null,
    /** Переписка, из которой открыли страницу человека: «назад» вернёт в неё. */
    val returnToChatId: Int? = null,
    /** PRISM, с которого открыть вкладку (из «Сохранённого»); null — с первого. */
    val prismStartId: Long? = null,
    val groups: List<Group> = Demo.groups,
    /** Страница человека поверх текущей вкладки; null — закрыта. */
    val openPersonId: Int? = null,
    /** Страница группы поверх вкладки «Группы»; null — список групп. */
    val openGroupId: Int? = null,
) {
    val openPerson: User? get() = openPersonId?.let { Demo.person(it) }
    val openGroup: Group? get() = groups.firstOrNull { it.id == openGroupId }
    val myGroups: List<Group> get() = groups.filter { it.joined }
    fun postsOf(userId: Int): List<Post> = posts.filter { it.author.id == userId }
    val openChat: Chat? get() = chats.firstOrNull { it.id == openChatId }
    val unreadTotal: Int get() = chats.sumOf { it.unread }
    val myPosts: List<Post> get() = posts.filter { it.author.id == me.id }
    /** «Истории»: записи тех, на кого подписан, — новые сверху, как и в ленте. */
    val stories: List<Post> get() = posts.filter { it.author.id in following }
    val followedPeople: List<User> get() = Demo.people.filter { it.id in following }
    /** Кто подписан на вас (пока — демо-люди). */
    val followers: List<User> get() = Demo.people.filter { it.id in Demo.followers }
    /** «Сохранённое» в профиле: PRISM в закладках. */
    val savedPrisms: List<Prism> get() = prisms.filter { it.saved }
    /** Запись по номеру — в ленте или в группе: для обсуждения. */
    fun findPost(id: Long): Post? =
        posts.firstOrNull { it.id == id } ?: groups.firstNotNullOfOrNull { g -> g.posts.firstOrNull { it.id == id } }
    fun findPrism(id: Long): Prism? = prisms.firstOrNull { it.id == id }
}

/**
 * Состояние всего приложения.
 *
 * Как в angelsheart: одно состояние и свой простой переход между экранами
 * без библиотеки навигации — экранов мало, и так переходы видны целиком.
 */
class AppViewModel(
    /** Часы — для времени своих записей и комментариев; в тестах подменяются. */
    private val clock: () -> Long = System::currentTimeMillis,
    /** Демо-собеседник отвечает на сообщения; в тестах выключен — там нет главного потока. */
    private val demoReplies: Boolean = true,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var nextId = 10_000L
    private var replyIndex = 0

    // ==================== НАВИГАЦИЯ ====================

    fun selectTab(tab: Tab) {
        _state.update {
            it.copy(
                tab = tab,
                openChatId = if (tab == Tab.Chats) it.openChatId else null,
                openGroupId = if (tab == Tab.Groups) it.openGroupId else null,
                openPersonId = null,
                returnToChatId = null,
                // Нажатие на «Профиль» внизу всегда ведёт на сам профиль
                profileSection = null,
            )
        }
    }

    /** Раздел профиля: нажатие на «записи», «подписчики», «подписки», «группы» или «Сохранённое». */
    fun openProfileSection(section: ProfileSection) {
        _state.update { it.copy(tab = Tab.Profile, profileSection = section, openPersonId = null) }
    }

    fun closeProfileSection() {
        _state.update { it.copy(profileSection = null) }
    }

    /**
     * Страница автора. Своё имя ведёт в «Профиль». Из переписки страница
     * открывается сразу поверх неё, а «назад» возвращает в переписку.
     */
    fun openPerson(userId: Int) {
        if (userId == _state.value.me.id) {
            selectTab(Tab.Profile)
            return
        }
        _state.update {
            if (it.openChatId != null) it.copy(openPersonId = userId, returnToChatId = it.openChatId, openChatId = null)
            else it.copy(openPersonId = userId)
        }
    }

    fun closePerson() {
        _state.update { it.copy(openPersonId = null, openChatId = it.returnToChatId ?: it.openChatId, returnToChatId = null) }
    }

    /**
     * Системное «назад». true — обработано внутри приложения: закрыта
     * переписка или возврат на ленту; false — пора закрывать приложение.
     */
    fun back(): Boolean {
        val s = _state.value
        return when {
            s.viewing != null -> { closeMedia(); true }
            s.commentsPostId != null || s.commentsPrismId != null -> { closeComments(); true }
            s.openChatId != null -> { closeChat(); true }
            s.openPersonId != null -> { closePerson(); true }
            s.openGroupId != null -> { closeGroup(); true }
            s.profileSection != null -> { closeProfileSection(); true }
            s.tab != Tab.Feed -> { selectTab(Tab.Feed); true }
            else -> false
        }
    }

    // ==================== ЛЕНТА ====================

    fun toggleLike(postId: Long) {
        _state.update { s ->
            s.copy(posts = s.posts.map {
                if (it.id != postId) it
                else it.copy(liked = !it.liked, likes = it.likes + if (it.liked) -1 else 1)
            })
        }
    }

    /** Новая запись: текст, фото и видео. Пустую (без текста и вложений) не публикуем. */
    fun publish(text: String, media: List<Attachment> = emptyList()) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && media.isEmpty()) return
        _state.update { s ->
            s.copy(posts = listOf(Post(nextId++, s.me, trimmed, 0, media = media.take(MAX_MEDIA), createdAt = clock())) + s.posts)
        }
    }

    /** Комментарий к записи в ленте или в группе. */
    fun addComment(postId: Long, text: String) {
        val trimmed = text.trim().take(1000)
        if (trimmed.isEmpty()) return
        _state.update { s ->
            val comment = Comment(nextId++, s.me, trimmed, createdAt = clock())
            fun Post.withComment() = if (id == postId) copy(comments = comments + 1, commentList = commentList + comment) else this
            s.copy(
                posts = s.posts.map { it.withComment() },
                groups = s.groups.map { g -> g.copy(posts = g.posts.map { it.withComment() }) },
            )
        }
    }

    /** Свой комментарий можно удалить. */
    fun deleteComment(postId: Long, commentId: Long) {
        _state.update { s ->
            fun Post.without() =
                if (id == postId && commentList.any { it.id == commentId && it.author.id == s.me.id })
                    copy(comments = (comments - 1).coerceAtLeast(0), commentList = commentList.filterNot { it.id == commentId })
                else this
            s.copy(
                posts = s.posts.map { it.without() },
                groups = s.groups.map { g -> g.copy(posts = g.posts.map { it.without() }) },
            )
        }
    }

    fun openComments(postId: Long) {
        _state.update { it.copy(commentsPostId = postId, commentsPrismId = null) }
    }

    fun openPrismComments(prismId: Long) {
        _state.update { it.copy(commentsPrismId = prismId, commentsPostId = null) }
    }

    fun closeComments() {
        _state.update { it.copy(commentsPostId = null, commentsPrismId = null) }
    }

    fun openMedia(attachment: Attachment) {
        _state.update { it.copy(viewing = attachment) }
    }

    fun closeMedia() {
        _state.update { it.copy(viewing = null) }
    }

    /** Подписаться или отписаться: записи человека появятся в «Историях» или уйдут оттуда. */
    fun toggleFollow(userId: Int) {
        if (userId == _state.value.me.id) return
        _state.update { s ->
            s.copy(following = if (userId in s.following) s.following - userId else s.following + userId)
        }
    }

    // ==================== ЧАТЫ ====================

    fun openChat(id: Int) {
        _state.update { s ->
            s.copy(
                tab = Tab.Chats,
                openChatId = id,
                // Страница человека остаётся под перепиской: «назад» вернёт на неё
                returnToChatId = null,
                chats = s.chats.map { if (it.id == id) it.copy(unread = 0) else it },
            )
        }
    }

    fun closeChat() {
        _state.update { it.copy(openChatId = null) }
    }

    /** «Написать» со страницы человека: существующая переписка или новая. */
    fun writeTo(userId: Int) {
        val person = Demo.person(userId) ?: return
        val existing = _state.value.chats.firstOrNull { it.peer.id == userId }
        if (existing != null) {
            openChat(existing.id)
            return
        }
        val id = (_state.value.chats.maxOfOrNull { it.id } ?: 0) + 1
        _state.update { s -> s.copy(chats = listOf(Chat(id, person, emptyList())) + s.chats) }
        openChat(id)
    }

    /**
     * Отправка. В демо собеседник «печатает» и через пару секунд отвечает —
     * чтобы переписку можно было почувствовать без сервера.
     */
    fun send(chatId: Int, text: String, attachments: List<Attachment> = emptyList(), replyTo: ReplyRef? = null) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && attachments.isEmpty()) return
        append(
            chatId,
            Message(nextId++, fromMe = true, text = trimmed, time = now(), attachments = attachments.take(MAX_MEDIA), replyTo = replyTo),
        )
        if (!demoReplies) return
        viewModelScope.launch {
            delay(700)
            setTyping(chatId, true)
            delay(1500)
            setTyping(chatId, false)
            val reply = Demo.replies[replyIndex++ % Demo.replies.size]
            append(chatId, Message(nextId++, fromMe = false, text = reply, time = now()))
        }
    }

    /**
     * Удалить сообщение. forEveryone = false — только у себя: у собеседника
     * оно останется. true — у обоих. Пока сервера нет, собеседник — демо, и
     * на этом телефоне оба варианта убирают сообщение из переписки; с
     * сервером «у всех» уйдёт собеседнику командой удаления.
     */
    fun deleteMessage(chatId: Int, messageId: Long, forEveryone: Boolean) {
        _state.update { s ->
            s.copy(chats = s.chats.map { chat ->
                if (chat.id != chatId) chat
                else chat.copy(messages = chat.messages.filterNot { it.id == messageId })
            })
        }
        if (forEveryone) deletedForEveryone += messageId
    }

    /** Цитата для ответа на сообщение: автор и начало текста (или что приложено). */
    fun replyRef(chatId: Int, message: Message): ReplyRef {
        val chat = _state.value.chats.firstOrNull { it.id == chatId }
        val author = if (message.fromMe) _state.value.me.name else chat?.peer?.name.orEmpty()
        return ReplyRef(message.id, author, messagePreview(message).take(120))
    }

    /**
     * Переслать сообщение в другую переписку: текст и вложения уходят от
     * вашего имени с пометкой, чьё оно было. Пересланное открывается в
     * переписке-получателе.
     */
    fun forward(fromChatId: Int, messageId: Long, toChatId: Int) {
        val s = _state.value
        val from = s.chats.firstOrNull { it.id == fromChatId } ?: return
        val original = from.messages.firstOrNull { it.id == messageId } ?: return
        if (s.chats.none { it.id == toChatId }) return
        val author = original.forwardedFrom ?: if (original.fromMe) s.me.name else from.peer.name
        append(
            toChatId,
            Message(nextId++, fromMe = true, text = original.text, time = now(), attachments = original.attachments, forwardedFrom = author),
        )
        openChat(toChatId)
    }

    /** Какие сообщения удалены «у всех» — для будущей отправки на сервер. */
    val deletedForEveryone = mutableSetOf<Long>()

    private fun append(chatId: Int, message: Message) {
        _state.update { s ->
            val chat = s.chats.firstOrNull { it.id == chatId } ?: return@update s
            val reading = s.openChatId == chatId
            val updated = chat.copy(
                messages = chat.messages + message,
                unread = if (message.fromMe || reading) chat.unread else chat.unread + 1,
            )
            // Чат с новым сообщением поднимается наверх списка
            s.copy(chats = listOf(updated) + s.chats.filterNot { it.id == chatId })
        }
    }

    private fun setTyping(chatId: Int, typing: Boolean) {
        _state.update { s ->
            s.copy(chats = s.chats.map { if (it.id == chatId) it.copy(typing = typing) else it })
        }
    }

    // ==================== PRISM ====================

    fun togglePrismLike(prismId: Long) {
        updatePrism(prismId) { it.copy(liked = !it.liked, likes = it.likes + if (it.liked) -1 else 1) }
    }

    /** «Сохранить»: закладка, ролик появляется в «Сохранённом» в профиле. */
    fun togglePrismSave(prismId: Long) {
        updatePrism(prismId) { it.copy(saved = !it.saved) }
    }

    fun addPrismComment(prismId: Long, text: String) {
        val trimmed = text.trim().take(1000)
        if (trimmed.isEmpty()) return
        val me = _state.value.me
        val comment = Comment(nextId++, me, trimmed, createdAt = clock())
        updatePrism(prismId) { it.copy(comments = it.comments + 1, commentList = it.commentList + comment) }
    }

    fun deletePrismComment(prismId: Long, commentId: Long) {
        val meId = _state.value.me.id
        updatePrism(prismId) { p ->
            if (p.commentList.none { it.id == commentId && it.author.id == meId }) p
            else p.copy(comments = (p.comments - 1).coerceAtLeast(0), commentList = p.commentList.filterNot { it.id == commentId })
        }
    }

    /** Открыть вкладку PRISM на нужном ролике — из «Сохранённого». */
    fun openPrism(prismId: Long) {
        selectTab(Tab.Prism)
        _state.update { it.copy(prismStartId = prismId) }
    }

    fun consumePrismStart() {
        _state.update { it.copy(prismStartId = null) }
    }

    private fun updatePrism(prismId: Long, change: (Prism) -> Prism) {
        _state.update { s -> s.copy(prisms = s.prisms.map { if (it.id == prismId) change(it) else it }) }
    }

    // ==================== ГРУППЫ ====================

    /** Группа поверх вкладки; из раздела профиля «назад» вернёт в него. */
    fun openGroup(groupId: Int) {
        _state.update {
            it.copy(
                tab = if (it.tab == Tab.Profile && it.profileSection != null) it.tab else Tab.Groups,
                openGroupId = groupId,
                openPersonId = null,
            )
        }
    }

    fun closeGroup() {
        _state.update { it.copy(openGroupId = null) }
    }

    /** Вступить или выйти. Из своей группы выйти нельзя — её можно только удалить. */
    fun toggleGroup(groupId: Int) {
        _state.update { s ->
            s.copy(groups = s.groups.map {
                if (it.id != groupId || it.mine) it
                else it.copy(joined = !it.joined, members = it.members + if (it.joined) -1 else 1)
            })
        }
    }

    /** Новая группа: создатель сразу в ней. Ошибка — текст для экрана, null — успех. */
    fun createGroup(name: String, description: String, topic: String): String? {
        val title = name.trim()
        if (title.length < 3) return "Название — от 3 символов"
        if (title.length > 40) return "Название — не длиннее 40 символов"
        if (_state.value.groups.any { it.name.equals(title, ignoreCase = true) }) return "Группа «$title» уже есть"
        val id = (_state.value.groups.maxOfOrNull { it.id } ?: 0) + 1
        val group = Group(
            id, title, description.trim(), topic.ifBlank { GROUP_TOPICS.last() },
            members = 1, seed = 100 + id, joined = true, mine = true,
        )
        _state.update { s -> s.copy(groups = listOf(group) + s.groups) }
        openGroup(id)
        return null
    }

    fun deleteGroup(groupId: Int) {
        _state.update { s -> s.copy(groups = s.groups.filterNot { it.id == groupId && it.mine }, openGroupId = null) }
    }

    /** Запись в группе — только для участников. */
    fun postInGroup(groupId: Int, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        _state.update { s ->
            s.copy(groups = s.groups.map {
                if (it.id != groupId || !it.joined) it
                else it.copy(posts = listOf(Post(nextId++, s.me, trimmed, 0, createdAt = clock())) + it.posts)
            })
        }
    }

    fun toggleGroupPostLike(groupId: Int, postId: Long) {
        _state.update { s ->
            s.copy(groups = s.groups.map { g ->
                if (g.id != groupId) g
                else g.copy(posts = g.posts.map {
                    if (it.id != postId) it
                    else it.copy(liked = !it.liked, likes = it.likes + if (it.liked) -1 else 1)
                })
            })
        }
    }

    // ==================== ПРОФИЛЬ ====================

    /**
     * Вошедший человек: его имя и логин — в профиле и в его записях.
     * Демо-данные остаются общими, поэтому номер у «себя» всегда тот же.
     */
    fun setAccount(username: String, displayName: String, info: ProfileInfo = ProfileInfo()) {
        _state.update { s ->
            val me = s.me.copy(
                name = displayName, handle = username,
                bio = info.bio, city = info.city, birthday = info.birthday,
                website = info.website, interests = info.interests,
            )
            s.withMe(me)
        }
    }

    /** После выхода состояние сбрасывается: следующий вошедший не увидит чужие черновики и переписку. */
    fun reset() {
        _state.value = UiState()
    }

    fun updateProfile(name: String, info: ProfileInfo) {
        val newName = name.trim().ifEmpty { return }
        _state.update { s ->
            val me = s.me.copy(
                name = newName,
                bio = info.bio.trim(),
                city = info.city.trim(),
                birthday = info.birthday.trim(),
                website = info.website.trim(),
                interests = info.interests,
            )
            // Свои записи в ленте показывают новое имя
            s.withMe(me)
        }
    }

    fun setNotifications(enabled: Boolean) {
        _state.update { it.copy(notifications = enabled) }
    }

    // ==================== СОХРАНЕНИЕ ====================

    /** То, что сохраняется на телефоне между запусками (см. StateStore). */
    fun snapshot(): Snapshot = _state.value.let {
        Snapshot(it.posts, it.chats, it.groups, it.prisms, it.following, it.notifications, nextId)
    }

    /**
     * Вернуть сохранённое состояние после входа. Свои записи и
     * комментарии получают текущее имя и сведения из профиля.
     */
    fun restore(snapshot: Snapshot) {
        nextId = maxOf(nextId, snapshot.nextId)
        _state.update { s ->
            s.copy(
                posts = snapshot.posts,
                chats = snapshot.chats,
                groups = snapshot.groups,
                prisms = snapshot.prisms,
                following = snapshot.following - s.me.id,
                notifications = snapshot.notifications,
            ).withMe(s.me)
        }
    }

    private fun now(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

    companion object {
        /** Сколько фото и видео можно приложить к одной записи или сообщению. */
        const val MAX_MEDIA = 10
    }
}

/** Текст сообщения для списков и цитат; без текста — что приложено. */
fun messagePreview(message: Message): String {
    if (message.text.isNotEmpty()) return message.text
    val a = message.attachments
    return when {
        a.isEmpty() -> ""
        a.all { it.kind == ru.wishka.memory.data.MediaKind.Photo } -> if (a.size == 1) "Фото" else "${a.size} фото"
        a.all { it.kind == ru.wishka.memory.data.MediaKind.Video } -> if (a.size == 1) "Видео" else "${a.size} видео"
        a.all { it.kind == ru.wishka.memory.data.MediaKind.Document } ->
            a.singleOrNull()?.name?.ifEmpty { null } ?: ru.wishka.memory.data.plural(a.size, "файл", "файла", "файлов")
        else -> ru.wishka.memory.data.plural(a.size, "вложение", "вложения", "вложений")
    }
}

/** Сохраняемая часть состояния. */
data class Snapshot(
    val posts: List<Post>,
    val chats: List<Chat>,
    val groups: List<Group>,
    val prisms: List<Prism>,
    val following: Set<Int>,
    val notifications: Boolean,
    val nextId: Long,
)

/** Новое имя и сведения — во всех своих записях и комментариях. */
private fun UiState.withMe(me: User): UiState {
    fun Comment.fix() = if (author.id == me.id) copy(author = me) else this
    fun Post.fix() = copy(author = if (author.id == me.id) me else author, commentList = commentList.map { it.fix() })
    return copy(
        me = me,
        posts = posts.map { it.fix() },
        groups = groups.map { g -> g.copy(posts = g.posts.map { it.fix() }) },
        prisms = prisms.map { p -> p.copy(commentList = p.commentList.map { it.fix() }) },
    )
}
