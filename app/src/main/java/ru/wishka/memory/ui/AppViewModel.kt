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
import ru.wishka.memory.data.Message
import ru.wishka.memory.data.Post
import ru.wishka.memory.data.User
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Вкладки нижней панели — в порядке слева направо. */
enum class Tab(val title: String) {
    Feed("Лента"),
    Chats("Чаты"),
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
) {
    val openChat: Chat? get() = chats.firstOrNull { it.id == openChatId }
    val unreadTotal: Int get() = chats.sumOf { it.unread }
    val myPosts: List<Post> get() = posts.filter { it.author.id == me.id }
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
        _state.update { it.copy(tab = tab, openChatId = if (tab == Tab.Chats) it.openChatId else null) }
    }

    /**
     * Системное «назад». true — обработано внутри приложения: закрыта
     * переписка или возврат на ленту; false — пора закрывать приложение.
     */
    fun back(): Boolean {
        val s = _state.value
        return when {
            s.openChatId != null -> { closeChat(); true }
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

    // ==================== ПРОФИЛЬ ====================

    fun updateProfile(name: String, bio: String) {
        val newName = name.trim().ifEmpty { return }
        _state.update { s ->
            val me = s.me.copy(name = newName, bio = bio.trim())
            // Свои записи в ленте показывают новое имя
            s.copy(me = me, posts = s.posts.map { if (it.author.id == me.id) it.copy(author = me) else it })
        }
    }

    fun setNotifications(enabled: Boolean) {
        _state.update { it.copy(notifications = enabled) }
    }

    private fun now(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
}
