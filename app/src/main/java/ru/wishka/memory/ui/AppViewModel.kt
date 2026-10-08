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
import ru.wishka.memory.data.Reel
import ru.wishka.memory.data.Message
import ru.wishka.memory.data.Post
import ru.wishka.memory.data.User
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Вкладки нижней панели — в порядке слева направо. */
enum class Tab(val title: String) {
    Feed("Лента"),
    Stories("Истории"),
    Reels("Reels"),
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
    val reels: List<Reel> = Demo.reels,
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
}

/**
 * Состояние всего приложения.
 *
 * Как в angelsheart: одно состояние и свой простой переход между экранами
 * без библиотеки навигации — экранов мало, и так переходы видны целиком.
 */
class AppViewModel : ViewModel() {

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
            )
        }
    }

    /** Страница автора. Своё имя ведёт в «Профиль». */
    fun openPerson(userId: Int) {
        if (userId == _state.value.me.id) {
            selectTab(Tab.Profile)
            return
        }
        _state.update { it.copy(openPersonId = userId) }
    }

    fun closePerson() {
        _state.update { it.copy(openPersonId = null) }
    }

    /**
     * Системное «назад». true — обработано внутри приложения: закрыта
     * переписка или возврат на ленту; false — пора закрывать приложение.
     */
    fun back(): Boolean {
        val s = _state.value
        return when {
            s.openChatId != null -> { closeChat(); true }
            s.openPersonId != null -> { closePerson(); true }
            s.openGroupId != null -> { closeGroup(); true }
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

    fun publish(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        _state.update { s ->
            s.copy(posts = listOf(Post(nextId++, s.me, trimmed, 0)) + s.posts)
        }
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
    fun send(chatId: Int, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        append(chatId, Message(nextId++, fromMe = true, text = trimmed, time = now()))
        viewModelScope.launch {
            delay(700)
            setTyping(chatId, true)
            delay(1500)
            setTyping(chatId, false)
            val reply = Demo.replies[replyIndex++ % Demo.replies.size]
            append(chatId, Message(nextId++, fromMe = false, text = reply, time = now()))
        }
    }

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

    // ==================== REELS ====================

    fun toggleReelLike(reelId: Long) {
        _state.update { s ->
            s.copy(reels = s.reels.map {
                if (it.id != reelId) it
                else it.copy(liked = !it.liked, likes = it.likes + if (it.liked) -1 else 1)
            })
        }
    }

    // ==================== ГРУППЫ ====================

    fun openGroup(groupId: Int) {
        _state.update { it.copy(tab = Tab.Groups, openGroupId = groupId, openPersonId = null) }
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
            id, title, description.trim(), topic.ifBlank { GROUP_TOPICS.first() },
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
                else it.copy(posts = listOf(Post(nextId++, s.me, trimmed, 0)) + it.posts)
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
            s.copy(me = me, posts = s.posts.map { if (it.author.id == me.id) it.copy(author = me) else it })
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
            s.copy(me = me, posts = s.posts.map { if (it.author.id == me.id) it.copy(author = me) else it })
        }
    }

    fun setNotifications(enabled: Boolean) {
        _state.update { it.copy(notifications = enabled) }
    }

    private fun now(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
}
