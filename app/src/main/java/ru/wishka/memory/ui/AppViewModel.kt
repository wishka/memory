package ru.wishka.memory.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
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
import ru.wishka.memory.data.MusicTrack
import ru.wishka.memory.data.Playlist
import ru.wishka.memory.data.Sticker
import ru.wishka.memory.data.Notifications
import ru.wishka.memory.data.Post
import ru.wishka.memory.data.Privacy
import ru.wishka.memory.data.User
import ru.wishka.memory.data.Cache
import ru.wishka.memory.data.CacheLog
import ru.wishka.memory.data.DealStatus
import ru.wishka.memory.data.DrawStroke
import ru.wishka.memory.data.LatLon
import ru.wishka.memory.data.MissionFind
import ru.wishka.memory.data.Outing
import ru.wishka.memory.data.ShareItem
import ru.wishka.memory.data.ShareKind
import ru.wishka.memory.data.ShareStatus
import ru.wishka.memory.data.Tip
import ru.wishka.memory.data.TogetherDemo
import ru.wishka.memory.data.TogetherSection
import ru.wishka.memory.data.TogetherState
import ru.wishka.memory.data.minutesTitle
import ru.wishka.memory.data.CHECK_IN_ACTIVITIES
import ru.wishka.memory.data.CheckIn
import ru.wishka.memory.data.CheckInTemplate
import ru.wishka.memory.data.Nudge
import ru.wishka.memory.data.NudgeTarget
import ru.wishka.memory.data.Nudges
import ru.wishka.memory.data.Recap
import ru.wishka.memory.data.RecapPeriod
import ru.wishka.memory.data.checkIn
import ru.wishka.memory.data.isKilometers
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.random.Random
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Разделы профиля за счётчиками: записи, подписчики, подписки, группы, сохранённое. */
enum class ProfileSection(private val ru: String) {
    Posts("Записи"),
    Followers("Подписчики"),
    Following("Подписки"),
    Groups("Группы"),
    Saved("Сохранённое"),
    Music("Моя музыка"),
    Stickers("Студия"),
    Recap("Итоги"),
    Requests("Заявки на подписку"),
    ;

    /** Название на выбранном языке. */
    val title: String get() = t(ru)
}

/** Страницы настроек за шестерёнкой в профиле. Main — сам список настроек. */
enum class SettingsPage(private val ru: String) {
    Main("Настройки"),
    Security("Безопасность"),
    Privacy("Приватность"),
    Blacklist("Чёрный список"),
    Notifications("Уведомления"),
    TextSize("Размер текста"),
    Language("Язык"),
    About("О приложении"),
    ;

    val title: String get() = t(ru)
}

/** Вкладки нижней панели — в порядке слева направо. */
enum class Tab(private val ru: String) {
    Feed("Лента"),
    Stories("Истории"),
    Prism("PRISM"),
    Chats("Чаты"),
    Groups("Группы"),
    Together("Вместе"),
    Profile("Профиль"),
    ;

    val title: String get() = t(ru)
}

data class UiState(
    val tab: Tab = Tab.Feed,
    val me: User = Demo.me,
    val posts: List<Post> = Demo.posts,
    val chats: List<Chat> = Demo.chats,
    /** Открытая переписка поверх вкладки «Чаты»; null — список чатов. */
    val openChatId: Int? = null,
    val notifications: Notifications = Notifications(),
    val privacy: Privacy = Privacy(),
    /** Чёрный список: их записи, комментарии и сообщения не видны. */
    val blocked: Set<Int> = emptySet(),
    /** Комментарии, на которые пожаловались: у пожаловавшегося они скрыты. */
    val reportedComments: Set<Long> = emptySet(),
    /** Свои значки, смайлы и стикеры из «Студии» — новые первыми. */
    val stickers: List<Sticker> = emptyList(),
    /** Записи, закреплённые в профиле (витрина), — до трёх. */
    val profilePins: List<Long> = emptyList(),
    /** «Моя музыка»: треки и плейлисты — только на этом телефоне. */
    val tracks: List<MusicTrack> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    /** Создание своего PRISM — камера, редактор, публикация — поверх всего. */
    val creatingPrism: Boolean = false,
    /** Настройки поверх вкладки «Профиль»; null — закрыты. */
    val settingsPage: SettingsPage? = null,
    /** Раздел чужого профиля (записи, подписчики, подписки) поверх его страницы. */
    val personSection: ProfileSection? = null,
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
    /** Настройки открытой группы — за шестерёнкой. */
    val groupSettingsOpen: Boolean = false,
    /** Свои чек-ины: шаблоны, которые человек создал сам. */
    val checkInTemplates: List<CheckInTemplate> = emptyList(),
    /** Заявки на подписку к вам (закрытый профиль) — новые первыми. */
    val followRequests: List<Int> = emptyList(),
    /** Кого вы одобрили: подписчики сверх демо-списка. */
    val approvedFollowers: Set<Int> = emptySet(),
    /** Ваши заявки к закрытым профилям, пока их не приняли. */
    val sentRequests: Set<Int> = emptySet(),
    /** За какой период уже напомнили об Итогах: «2026-09», «2026». */
    val recapNotified: Set<String> = emptySet(),
    /** Уведомление поверх приложения, пока оно открыто; null — нет. */
    val banner: Nudge? = null,
    /** Итоги: открыт месяц или год. */
    val recapPeriod: RecapPeriod = RecapPeriod.Month,
    /** Итоги: на сколько месяцев или лет назад от текущего. */
    val recapBack: Int = 0,
    /** Вкладка «Вместе»: цели, навыки, дневники, миссии, клады, вылазки, рисунок, обмен. */
    val together: TogetherState = TogetherDemo.initial(),
    val togetherSection: TogetherSection = TogetherSection.Challenges,
) {
    val openPerson: User? get() = openPersonId?.let { Demo.person(it) }
    val openGroup: Group? get() = groups.firstOrNull { it.id == openGroupId }
    val myGroups: List<Group> get() = groups.filter { it.joined }
    fun postsOf(userId: Int): List<Post> = posts.filter { it.author.id == userId }
    val openChat: Chat? get() = chats.firstOrNull { it.id == openChatId }
    /** Счётчик на вкладке «Чаты»: переписки без звука не считаются. */
    val unreadTotal: Int get() = chats.filterNot { it.muted }.sumOf { it.unread }
    /** Список чатов: закреплённые сверху, остальные — по последнему сообщению. */
    val chatList: List<Chat> get() = chats.sortedByDescending { it.onTop }
    /**
     * Лента без записей тех, кто в чёрном списке, и без записей закрытых
     * профилей, на которые вы не подписаны.
     */
    val feed: List<Post> get() = posts.filter { it.author.id !in blocked && canSee(it.author) }
    /** Записи человека видны: профиль открыт, вы подписаны или это вы. */
    fun canSee(user: User): Boolean = !user.closed || user.id in following || user.id == me.id
    /** Люди, приславшие заявку на подписку. */
    val requestPeople: List<User> get() = followRequests.mapNotNull { Demo.person(it) }
    val blockedPeople: List<User> get() = Demo.people.filter { it.id in blocked }
    fun isBlocked(userId: Int): Boolean = userId in blocked
    /**
     * Подписчики другого человека. Сервера нет, поэтому видны только
     * знакомые по демо-данным — и вы, если подписаны.
     */
    fun followersOf(user: User): List<User> =
        (if (user.id in following) listOf(me) else emptyList()) +
            Demo.people.filter { it.id != user.id && it.id !in blocked && (it.id + user.id) % 2 == 1 }
    /** На кого подписан другой человек — тоже из знакомых; вы — если он подписан на вас. */
    fun followingOf(user: User): List<User> =
        (if (user.id in Demo.followers) listOf(me) else emptyList()) +
            Demo.people.filter { it.id != user.id && it.id !in blocked && (it.id + user.id) % 2 == 0 }
    val myPosts: List<Post> get() = posts.filter { it.author.id == me.id }
    /** «Истории»: свои записи и записи тех, на кого подписан, — новые сверху, как и в ленте. */
    val stories: List<Post> get() = posts.filter { (it.author.id in following || it.author.id == me.id) && it.author.id !in blocked }
    val followedPeople: List<User> get() = Demo.people.filter { it.id in following }
    /** Кто подписан на вас: демо-люди и те, чьи заявки вы одобрили. */
    val followers: List<User> get() = Demo.people.filter { it.id in Demo.followers || it.id in approvedFollowers }
    /** «Сохранённое» в профиле: PRISM в закладках. */
    val savedPrisms: List<Prism> get() = prisms.filter { it.saved }
    fun track(id: String): MusicTrack? = tracks.firstOrNull { it.id == id }
    fun playlistTracks(playlist: Playlist): List<MusicTrack> = playlist.trackIds.mapNotNull { track(it) }
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
                groupSettingsOpen = false,
                openPersonId = null,
                personSection = null,
                returnToChatId = null,
                // Нажатие на «Профиль» внизу всегда ведёт на сам профиль
                profileSection = null,
                settingsPage = null,
            )
        }
    }

    /** Раздел профиля: нажатие на «записи», «подписчики», «подписки», «группы» или «Сохранённое». */
    fun openProfileSection(section: ProfileSection) {
        // Поверх может быть переписка, группа или настройки — уходят, иначе раздел не виден
        _state.update {
            it.copy(
                tab = Tab.Profile, profileSection = section, openPersonId = null, personSection = null,
                settingsPage = null, openChatId = null, openGroupId = null, groupSettingsOpen = false,
            )
        }
    }

    fun closeProfileSection() {
        _state.update { it.copy(profileSection = null) }
    }

    /** Настройки: шестерёнка в профиле открывает список, пункт списка — свою страницу. */
    fun openSettings(page: SettingsPage = SettingsPage.Main) {
        _state.update { it.copy(tab = Tab.Profile, settingsPage = page, profileSection = null, openPersonId = null) }
    }

    /** «Назад» в настройках: со страницы — в список, из списка — в профиль. */
    fun settingsBack() {
        _state.update { it.copy(settingsPage = if (it.settingsPage == SettingsPage.Main) null else SettingsPage.Main) }
    }

    /** Записи, подписчики или подписки другого человека — нажатием на счётчик на его странице. */
    fun openPersonSection(section: ProfileSection) {
        _state.update { it.copy(personSection = section) }
    }

    fun closePersonSection() {
        _state.update { it.copy(personSection = null) }
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
            if (it.openChatId != null) it.copy(openPersonId = userId, personSection = null, returnToChatId = it.openChatId, openChatId = null)
            else it.copy(openPersonId = userId, personSection = null)
        }
    }

    fun closePerson() {
        _state.update { it.copy(openPersonId = null, personSection = null, openChatId = it.returnToChatId ?: it.openChatId, returnToChatId = null) }
    }

    /**
     * Системное «назад». true — обработано внутри приложения: закрыта
     * переписка или возврат на ленту; false — пора закрывать приложение.
     */
    fun back(): Boolean {
        val s = _state.value
        return when {
            s.creatingPrism -> { closePrismCreator(); true }
            s.viewing != null -> { closeMedia(); true }
            s.commentsPostId != null || s.commentsPrismId != null -> { closeComments(); true }
            s.openChatId != null -> { closeChat(); true }
            s.personSection != null && s.openPersonId != null -> { closePersonSection(); true }
            s.openPersonId != null -> { closePerson(); true }
            s.groupSettingsOpen -> { closeGroupSettings(); true }
            s.openGroupId != null -> { closeGroup(); true }
            s.settingsPage != null -> { settingsBack(); true }
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
    fun publish(text: String, media: List<Attachment> = emptyList(), checkIn: CheckIn? = null) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && media.isEmpty() && checkIn == null) return
        _state.update { s ->
            s.copy(
                posts = listOf(Post(nextId++, s.me, trimmed, 0, media = media.take(MAX_MEDIA), createdAt = clock(), checkIn = checkIn)) + s.posts,
                // Чек-ин сам засчитывается в общие цели «Вместе»
                together = if (checkIn != null) s.together.afterCheckIn(checkIn) else s.together,
            )
        }
    }

    // ==================== ЧЕК-ИНЫ ====================

    /** Свой чек-ин. null — создан; иначе — что не так. */
    fun createCheckInTemplate(emoji: String, title: String, unit: String, hint: String): String? {
        val name = title.trim().take(40)
        if (name.isEmpty()) return t("Назовите чек-ин")
        val s = _state.value
        if (s.checkInTemplates.any { it.title.equals(name, ignoreCase = true) } ||
            CHECK_IN_ACTIVITIES.any { t(it.title).equals(name, ignoreCase = true) }
        ) return t("Такой чек-ин уже есть")
        val template = CheckInTemplate("custom-" + UUID.randomUUID().toString().take(8), emoji.ifBlank { "⭐" }, name, unit.trim().take(12), hint.trim().take(60))
        _state.update { it.copy(checkInTemplates = listOf(template) + it.checkInTemplates) }
        return null
    }

    fun deleteCheckInTemplate(id: String) {
        _state.update { s -> s.copy(checkInTemplates = s.checkInTemplates.filterNot { it.id == id }) }
    }

    /** Комментарий к записи в ленте или в группе. */
    fun addComment(postId: Long, text: String, replyTo: String = "", sticker: Attachment? = null) {
        val trimmed = text.trim().take(1000)
        if (trimmed.isEmpty() && sticker == null) return
        _state.update { s ->
            val comment = Comment(nextId++, s.me, trimmed, createdAt = clock(), replyTo = replyTo, sticker = sticker)
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

    /** «Нравится» комментарию к записи — в ленте или в группе. */
    fun toggleCommentLike(postId: Long, commentId: Long) {
        _state.update { s ->
            fun Post.flip() = if (id != postId) this else copy(commentList = commentList.map { it.flipLike(commentId) })
            s.copy(
                posts = s.posts.map { it.flip() },
                groups = s.groups.map { g -> g.copy(posts = g.posts.map { it.flip() }) },
            )
        }
    }

    /**
     * Пожаловаться на чужой комментарий: он скрывается у вас. Когда
     * появится сервер, жалоба уйдёт модераторам.
     */
    fun reportComment(commentId: Long) {
        _state.update { it.copy(reportedComments = it.reportedComments + commentId) }
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

    /**
     * Подписаться или отписаться: записи человека появятся в «Историях» или
     * уйдут оттуда. На закрытый профиль уходит заявка; повторное нажатие —
     * отозвать её. В демо заявку принимают через несколько секунд.
     */
    fun toggleFollow(userId: Int) {
        val s0 = _state.value
        if (userId == s0.me.id) return
        val person = Demo.person(userId)
        if (person != null && person.closed && userId !in s0.following) {
            if (userId in s0.sentRequests) {
                acceptJobs.remove(userId)?.cancel()
                _state.update { it.copy(sentRequests = it.sentRequests - userId) }
                return
            }
            _state.update { it.copy(sentRequests = it.sentRequests + userId) }
            scheduleAcceptance(userId)
            return
        }
        _state.update { s ->
            s.copy(following = if (userId in s.following) s.following - userId else s.following + userId)
        }
    }

    /** Таймеры демо-ответа на заявки: один на человека. */
    private val acceptJobs = mutableMapOf<Int, Job>()

    /** В демо закрытый профиль принимает заявку через 12–30 секунд. */
    private fun scheduleAcceptance(userId: Int) {
        if (!demoReplies) return
        acceptJobs.remove(userId)?.cancel()
        acceptJobs[userId] = viewModelScope.launch {
            delay(Random.nextLong(12_000, 30_000))
            acceptJobs.remove(userId)
            acceptSentRequest(userId)
        }
    }

    /** Закрытый профиль принял вашу заявку: вы подписаны, записи видны. */
    fun acceptSentRequest(userId: Int) {
        if (userId !in _state.value.sentRequests) return
        _state.update { it.copy(sentRequests = it.sentRequests - userId, following = it.following + userId) }
        Demo.person(userId)?.let { notify(Nudges.requestAccepted(it)) }
    }

    /** Одобрить заявку: человек становится подписчиком. */
    fun approveRequest(userId: Int) {
        _state.update { it.copy(followRequests = it.followRequests - userId, approvedFollowers = it.approvedFollowers + userId) }
    }

    /** Отклонить заявку: человек не узнает об этом, просто останется без доступа. */
    fun declineRequest(userId: Int) {
        _state.update { it.copy(followRequests = it.followRequests - userId) }
    }

    /** Новая заявка к вам — когда профиль закрыт. */
    fun receiveFollowRequest(userId: Int) {
        val s = _state.value
        val person = Demo.person(userId) ?: return
        if (!s.privacy.closedProfile || userId in s.followRequests || userId in s.blocked ||
            s.followers.any { it.id == userId }
        ) return
        _state.update { it.copy(followRequests = listOf(userId) + it.followRequests) }
        notify(Nudges.followRequest(person, person.interests.filter { it in s.me.interests }))
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
            _state.value.chats.firstOrNull { it.id == chatId }?.let { chat ->
                // Открытая переписка — ответ и так на экране
                if (!chat.muted && _state.value.openChatId != chatId) notify(Nudges.message(chat.peer, reply, chatId))
            }
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
                else chat.copy(
                    messages = chat.messages.filterNot { it.id == messageId },
                    // Удалили закреплённое — полоска вверху уходит
                    pinnedId = chat.pinnedId?.takeIf { it != messageId },
                )
            })
        }
        if (forEveryone) deletedForEveryone += messageId
    }

    /** Закрепить сообщение вверху переписки; повторно — открепить. Закреплённое одно. */
    fun togglePin(chatId: Int, messageId: Long) {
        _state.update { s ->
            s.copy(chats = s.chats.map {
                if (it.id != chatId) it else it.copy(pinnedId = if (it.pinnedId == messageId) null else messageId)
            })
        }
    }

    fun unpin(chatId: Int) {
        _state.update { s -> s.copy(chats = s.chats.map { if (it.id == chatId) it.copy(pinnedId = null) else it }) }
    }

    // ---- Меню переписки в списке чатов (долгое нажатие) ----

    /** Закрепить переписку вверху списка; повторно — открепить. */
    fun toggleChatOnTop(chatId: Int) = updateChat(chatId) { it.copy(onTop = !it.onTop) }

    /** Без звука: уведомлений нет, непрочитанные не попадают в счётчик на вкладке. */
    fun toggleChatMute(chatId: Int) = updateChat(chatId) { it.copy(muted = !it.muted) }

    fun markRead(chatId: Int) = updateChat(chatId) { it.copy(unread = 0) }

    /** Отметить непрочитанной — чтобы вернуться к переписке позже. */
    fun markUnread(chatId: Int) = updateChat(chatId) { it.copy(unread = maxOf(it.unread, 1)) }

    /** Очистить историю: сообщения уходят, сама переписка остаётся в списке. */
    fun clearHistory(chatId: Int) = updateChat(chatId) { it.copy(messages = emptyList(), pinnedId = null, unread = 0) }

    /** Удалить переписку целиком; открытая — закрывается. */
    fun deleteChat(chatId: Int) {
        _state.update { s ->
            s.copy(
                chats = s.chats.filterNot { it.id == chatId },
                openChatId = s.openChatId?.takeIf { it != chatId },
                returnToChatId = s.returnToChatId?.takeIf { it != chatId },
            )
        }
    }

    private fun updateChat(chatId: Int, change: (Chat) -> Chat) {
        _state.update { s -> s.copy(chats = s.chats.map { if (it.id == chatId) change(it) else it }) }
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

    fun togglePrismCommentLike(prismId: Long, commentId: Long) {
        updatePrism(prismId) { p -> p.copy(commentList = p.commentList.map { it.flipLike(commentId) }) }
    }

    fun togglePrismLike(prismId: Long) {
        updatePrism(prismId) { it.copy(liked = !it.liked, likes = it.likes + if (it.liked) -1 else 1) }
    }

    /** «Сохранить»: закладка, ролик появляется в «Сохранённом» в профиле. */
    fun togglePrismSave(prismId: Long) {
        updatePrism(prismId) { it.copy(saved = !it.saved) }
    }

    fun addPrismComment(prismId: Long, text: String, replyTo: String = "", sticker: Attachment? = null) {
        val trimmed = text.trim().take(1000)
        if (trimmed.isEmpty() && sticker == null) return
        val me = _state.value.me
        val comment = Comment(nextId++, me, trimmed, createdAt = clock(), replyTo = replyTo, sticker = sticker)
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

    /** Свой PRISM: камера → редактор → публикация. */
    fun openPrismCreator() {
        _state.update { it.copy(creatingPrism = true) }
    }

    fun closePrismCreator() {
        _state.update { it.copy(creatingPrism = false) }
    }

    /**
     * Опубликовать свой ролик: готовое видео из редактора, обложка,
     * подпись и что звучит. Ролик встаёт первым во вкладке PRISM.
     */
    fun publishPrism(video: Attachment, cover: Attachment?, caption: String, music: String, durationMs: Int) {
        _state.update { s ->
            val prism = Prism(
                id = nextId++, author = s.me, caption = caption.trim().take(300),
                music = music.ifBlank { tf("Оригинальный звук · %s", s.me.name) },
                seed = (nextId % 97).toInt(), likes = 0, comments = 0,
                durationMs = durationMs.coerceAtLeast(1000), video = video, cover = cover, createdAt = clock(),
            )
            s.copy(prisms = listOf(prism) + s.prisms, creatingPrism = false, tab = Tab.Prism, prismStartId = prism.id)
        }
    }

    /** Удалить свой ролик. Файлы удаляет экран — модель о файлах не знает. */
    fun deletePrism(prismId: Long) {
        _state.update { s ->
            s.copy(prisms = s.prisms.filterNot { it.id == prismId && it.author.id == s.me.id })
        }
    }

    fun consumePrismStart() {
        _state.update { it.copy(prismStartId = null) }
    }

    private fun updatePrism(prismId: Long, change: (Prism) -> Prism) {
        _state.update { s -> s.copy(prisms = s.prisms.map { if (it.id == prismId) change(it) else it }) }
    }

    // ==================== МОЯ МУЗЫКА ====================

    /** Новые треки — в начало списка; уже загруженные (тот же файл) не повторяются. */
    fun addTracks(tracks: List<MusicTrack>) {
        _state.update { s ->
            val known = s.tracks.map { it.path }.toSet()
            s.copy(tracks = tracks.filter { it.path !in known } + s.tracks)
        }
    }

    /** Удалить трек: он уходит и из всех плейлистов. */
    fun deleteTrack(trackId: String) {
        _state.update { s ->
            s.copy(
                tracks = s.tracks.filterNot { it.id == trackId },
                playlists = s.playlists.map { p -> p.copy(trackIds = p.trackIds - trackId) },
            )
        }
    }

    /** Новый плейлист; ответ — его номер, null — пустое название. */
    fun createPlaylist(name: String): Long? {
        val title = name.trim().take(60)
        if (title.isEmpty()) return null
        val id = nextId++
        _state.update { s -> s.copy(playlists = s.playlists + Playlist(id, title)) }
        return id
    }

    fun renamePlaylist(playlistId: Long, name: String) {
        val title = name.trim().take(60)
        if (title.isEmpty()) return
        updatePlaylist(playlistId) { it.copy(name = title) }
    }

    fun deletePlaylist(playlistId: Long) {
        _state.update { s -> s.copy(playlists = s.playlists.filterNot { it.id == playlistId }) }
    }

    /** Добавить трек в плейлист; уже добавленный не повторяется. */
    fun addToPlaylist(playlistId: Long, trackId: String) {
        if (_state.value.track(trackId) == null) return
        updatePlaylist(playlistId) { if (trackId in it.trackIds) it else it.copy(trackIds = it.trackIds + trackId) }
    }

    fun removeFromPlaylist(playlistId: Long, trackId: String) {
        updatePlaylist(playlistId) { it.copy(trackIds = it.trackIds - trackId) }
    }

    /** Сдвинуть трек в плейлисте на шаг вверх (-1) или вниз (+1). */
    fun moveInPlaylist(playlistId: Long, trackId: String, step: Int) {
        updatePlaylist(playlistId) { p ->
            val i = p.trackIds.indexOf(trackId)
            val j = i + step
            if (i < 0 || j !in p.trackIds.indices) p
            else p.copy(trackIds = p.trackIds.toMutableList().also { it.add(j, it.removeAt(i)) })
        }
    }

    private fun updatePlaylist(playlistId: Long, change: (Playlist) -> Playlist) {
        _state.update { s -> s.copy(playlists = s.playlists.map { if (it.id == playlistId) change(it) else it }) }
    }

    // ==================== СТУДИЯ И ВИТРИНА ====================

    /** Новый стикер — первым в «Студии». */
    fun addSticker(sticker: Sticker) {
        _state.update { it.copy(stickers = listOf(sticker) + it.stickers.filterNot { s -> s.id == sticker.id }) }
    }

    /** Убрать стикер из «Студии». Файл удаляет экран. */
    fun deleteSticker(stickerId: String) {
        _state.update { it.copy(stickers = it.stickers.filterNot { s -> s.id == stickerId }) }
    }

    /** Закрепить свою запись в витрине профиля (до трёх) или открепить. */
    fun toggleProfilePin(postId: Long) {
        _state.update { s ->
            when {
                postId in s.profilePins -> s.copy(profilePins = s.profilePins - postId)
                s.posts.none { it.id == postId && it.author.id == s.me.id } -> s
                else -> s.copy(profilePins = (listOf(postId) + s.profilePins).take(MAX_PINS))
            }
        }
    }

    /** Удалить свою запись из ленты. Файлы удаляет экран. */
    fun deletePost(postId: Long) {
        _state.update { s ->
            s.copy(
                posts = s.posts.filterNot { it.id == postId && it.author.id == s.me.id },
                profilePins = s.profilePins - postId,
            )
        }
    }

    // ==================== ГРУППЫ ====================

    /** Группа поверх вкладки; из раздела профиля «назад» вернёт в него. */
    fun openGroup(groupId: Int) {
        _state.update {
            it.copy(
                tab = if (it.tab == Tab.Profile && it.profileSection != null) it.tab else Tab.Groups,
                openGroupId = groupId,
                groupSettingsOpen = false,
                openPersonId = null,
            )
        }
    }

    fun closeGroup() {
        _state.update { it.copy(openGroupId = null, groupSettingsOpen = false) }
    }

    fun openGroupSettings() {
        _state.update { it.copy(groupSettingsOpen = it.openGroupId != null) }
    }

    fun closeGroupSettings() {
        _state.update { it.copy(groupSettingsOpen = false) }
    }

    /** Название, описание и тема своей группы. Ошибка — текст для экрана, null — сохранено. */
    fun updateGroup(groupId: Int, name: String, description: String, topic: String): String? {
        val title = name.trim()
        if (title.length < 3) return t("Название — от 3 символов")
        if (title.length > 40) return t("Название — не длиннее 40 символов")
        if (_state.value.groups.any { it.id != groupId && it.name.equals(title, ignoreCase = true) }) return tf("Группа «%s» уже есть", title)
        updateOwnGroup(groupId) { it.copy(name = title, description = description.trim().take(500), topic = topic.ifBlank { it.topic }) }
        return null
    }

    /** Новая заставка группы (фото) или null — вернуть туманность. Старый файл удаляет экран. */
    fun setGroupCover(groupId: Int, cover: Attachment?) {
        updateOwnGroup(groupId) { it.copy(cover = cover) }
    }

    fun setGroupOnlyOwnerPosts(groupId: Int, only: Boolean) {
        updateOwnGroup(groupId) { it.copy(onlyOwnerPosts = only) }
    }

    /** Закрепить запись в своей группе; та же запись ещё раз — открепить. */
    fun togglePinGroupPost(groupId: Int, postId: Long) {
        updateOwnGroup(groupId) { g ->
            if (g.posts.none { it.id == postId }) g
            else g.copy(pinnedPostId = if (g.pinnedPostId == postId) null else postId)
        }
    }

    /** Удалить запись в группе: свою — всегда, любую — в своей группе. Файлы удаляет экран. */
    fun deleteGroupPost(groupId: Int, postId: Long) {
        val meId = _state.value.me.id
        _state.update { s ->
            s.copy(groups = s.groups.map { g ->
                if (g.id != groupId) g
                else {
                    val post = g.posts.firstOrNull { it.id == postId }
                    if (post == null || (post.author.id != meId && !g.mine)) g
                    else g.copy(posts = g.posts - post, pinnedPostId = g.pinnedPostId.takeIf { it != postId })
                }
            })
        }
    }

    private fun updateOwnGroup(groupId: Int, change: (Group) -> Group) {
        _state.update { s -> s.copy(groups = s.groups.map { if (it.id == groupId && it.mine) change(it) else it }) }
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

    /** Отключить или включить уведомления группы. */
    fun toggleGroupMute(groupId: Int) {
        _state.update { s -> s.copy(groups = s.groups.map { if (it.id == groupId) it.copy(muted = !it.muted) else it }) }
    }

    /** Новая группа: создатель сразу в ней. Ошибка — текст для экрана, null — успех. */
    fun createGroup(name: String, description: String, topic: String): String? {
        val title = name.trim()
        if (title.length < 3) return t("Название — от 3 символов")
        if (title.length > 40) return t("Название — не длиннее 40 символов")
        if (_state.value.groups.any { it.name.equals(title, ignoreCase = true) }) return tf("Группа «%s» уже есть", title)
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
        _state.update { s ->
            s.copy(groups = s.groups.filterNot { it.id == groupId && it.mine }, openGroupId = null, groupSettingsOpen = false)
        }
    }

    /**
     * Запись в группе — только для участников (а если так решил создатель —
     * только для него): текст и вложения — фото, видео, документы, музыка.
     */
    fun postInGroup(groupId: Int, text: String, media: List<Attachment> = emptyList()) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && media.isEmpty()) return
        _state.update { s ->
            s.copy(groups = s.groups.map {
                if (it.id != groupId || !it.joined || (it.onlyOwnerPosts && !it.mine)) it
                else it.copy(posts = listOf(Post(nextId++, s.me, trimmed, 0, media = media.take(MAX_MEDIA), createdAt = clock())) + it.posts)
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
                website = info.website, interests = info.interests, avatar = info.avatar,
            )
            s.withMe(me)
        }
    }

    /** После выхода состояние сбрасывается: следующий вошедший не увидит чужие черновики и переписку. */
    fun reset() {
        lifeJob?.cancel()
        lifeJob = null
        acceptJobs.values.forEach { it.cancel() }
        acceptJobs.clear()
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
                avatar = info.avatar,
            )
            // Свои записи в ленте показывают новое имя
            s.withMe(me)
        }
    }

    /** Своё фото в профиле; "" — вернуть аватар из букв. */
    fun setAvatar(path: String) {
        _state.update { s -> s.withMe(s.me.copy(avatar = path)) }
    }

    /** Сведения профиля как есть сейчас — чтобы сохранить их в аккаунт. */
    fun profileInfo(): ProfileInfo = _state.value.me.let {
        ProfileInfo(it.bio, it.city, it.birthday, it.website, it.interests, it.avatar)
    }

    fun setNotifications(notifications: Notifications) {
        _state.update { it.copy(notifications = notifications) }
    }

    /** Открыли профиль — ждущие заявки одобряются сами: теперь подписаться может любой. */
    fun setPrivacy(privacy: Privacy) {
        _state.update {
            if (!privacy.closedProfile && it.followRequests.isNotEmpty())
                it.copy(privacy = privacy, approvedFollowers = it.approvedFollowers + it.followRequests, followRequests = emptyList())
            else it.copy(privacy = privacy)
        }
    }

    /**
     * В чёрный список: записи, комментарии и сообщения человека больше не
     * видны, подписка на него снимается. Своя страница не блокируется.
     */
    fun block(userId: Int) {
        if (userId == _state.value.me.id) return
        _state.update { s -> s.copy(blocked = s.blocked + userId, following = s.following - userId) }
    }

    fun unblock(userId: Int) {
        _state.update { s -> s.copy(blocked = s.blocked - userId) }
    }

    // ==================== УВЕДОМЛЕНИЯ И ИТОГИ ====================

    /** Очередь: уведомление дождётся, пока экран начнёт их принимать (после заставки). */
    private val _nudges = Channel<Nudge>(Channel.BUFFERED)

    /**
     * Персональные уведомления. MainActivity показывает их системным
     * уведомлением, когда приложение свёрнуто, а пока оно открыто —
     * плашкой сверху (state.banner).
     */
    val nudges: Flow<Nudge> = _nudges.receiveAsFlow()

    /** Отправить уведомление, если такой раздел не выключен в настройках. */
    fun notify(nudge: Nudge) {
        if (!Nudges.allowed(nudge, _state.value.notifications)) return
        _nudges.trySend(nudge)
    }

    fun showBanner(nudge: Nudge) {
        _state.update { it.copy(banner = nudge) }
    }

    fun dismissBanner() {
        _state.update { it.copy(banner = null) }
    }

    /** Нажатие на уведомление: туда, о чём оно. */
    fun openNudge(nudge: Nudge) {
        dismissBanner()
        when (nudge.target) {
            NudgeTarget.Post -> {
                val post = _state.value.findPost(nudge.targetId)
                if (post != null && post.author.id == _state.value.me.id) openProfileSection(ProfileSection.Posts)
                else selectTab(Tab.Feed)
            }
            NudgeTarget.Group -> { selectTab(Tab.Groups); openGroup(nudge.targetId.toInt()) }
            NudgeTarget.Person -> openPerson(nudge.targetId.toInt())
            NudgeTarget.Chat -> if (_state.value.chats.any { it.id == nudge.targetId.toInt() }) openChat(nudge.targetId.toInt()) else selectTab(Tab.Chats)
            NudgeTarget.Recap -> {
                // targetId — год × 100 + месяц; месяц 0 — итоги года
                val year = (nudge.targetId / 100).toInt()
                val month = (nudge.targetId % 100).toInt()
                val today = LocalDate.now()
                if (year == 0) openRecap(RecapPeriod.Month)
                else if (month == 0) openRecap(RecapPeriod.Year, (today.year - year).coerceAtLeast(0))
                else openRecap(RecapPeriod.Month, ((today.year * 12 + today.monthValue) - (year * 12 + month)).coerceAtLeast(0))
            }
            NudgeTarget.Requests -> openProfileSection(ProfileSection.Requests)
            NudgeTarget.Together -> {
                selectTab(Tab.Together)
                TogetherSection.entries.getOrNull(nudge.targetId.toInt())?.let(::selectTogetherSection)
            }
        }
    }

    /** Итоги месяца или года; back — на сколько периодов назад от текущего. */
    fun openRecap(period: RecapPeriod, back: Int = 0) {
        openProfileSection(ProfileSection.Recap)
        _state.update { it.copy(recapPeriod = period, recapBack = back) }
    }

    fun setRecapPeriod(period: RecapPeriod) {
        _state.update { it.copy(recapPeriod = period, recapBack = 0) }
    }

    fun setRecapBack(back: Int) {
        _state.update { it.copy(recapBack = back.coerceAtLeast(0)) }
    }

    /** Итоги за период: прошлый (законченный) или текущий месяц, год. */
    fun recap(period: RecapPeriod, year: Int, month: Int): Recap =
        _state.value.let { Recap.compute(it.me, it.posts, it.prisms, period, year, month) }

    /**
     * В начале месяца — уведомление об итогах прошлого (если было что
     * считать), в январе — ещё и об итогах года. Один раз на период.
     */
    fun checkRecapReady(today: LocalDate = LocalDate.now()) {
        val (y, m) = Recap.previousMonth(today)
        val list = buildList {
            add(recap(RecapPeriod.Month, y, m))
            if (today.monthValue == 1) add(recap(RecapPeriod.Year, today.year - 1, 0))
        }
        for (r in list) {
            if (r.key in _state.value.recapNotified || r.empty) continue
            _state.update { it.copy(recapNotified = it.recapNotified + r.key) }
            notify(Nudges.recap(r))
        }
    }

    private var lifeJob: Job? = null

    /**
     * Жизнь вокруг, пока нет сервера: друзья отмечают чек-ины, в группы
     * приходят люди, вашим записям ставят «нравится», при закрытом
     * профиле приходят заявки. Каждое событие — с персональным
     * уведомлением. В тестах не запускается.
     */
    fun startDemoLife() {
        if (lifeJob != null) return
        // Заявки, отправленные до перезапуска, тоже дождутся ответа
        _state.value.sentRequests.forEach { scheduleAcceptance(it) }
        lifeJob = viewModelScope.launch {
            delay(25_000)
            while (true) {
                demoEvent(Random.Default)
                delay(Random.nextLong(35_000, 75_000))
                togetherEvent(Random.Default)
                delay(Random.nextLong(35_000, 75_000))
            }
        }
    }

    /** Всё, что вы сами писали: записи, комментарии, сообщения — чтобы понять, что вы обсуждали. */
    fun myTexts(): List<String> {
        val s = _state.value
        val all = s.posts + s.groups.flatMap { it.posts }
        return all.filter { it.author.id == s.me.id }.map { it.text } +
            all.flatMap { it.commentList }.filter { it.author.id == s.me.id }.map { it.text } +
            s.prisms.flatMap { it.commentList }.filter { it.author.id == s.me.id }.map { it.text } +
            s.chats.flatMap { c -> c.messages.filter { it.fromMe }.map { it.text } }
    }

    /** Ваши километры за текущий месяц — для «не отставайте». */
    private fun myKmThisMonth(): Double {
        val s = _state.value
        val now = Instant.ofEpochMilli(clock()).atZone(ZoneId.systemDefault()).toLocalDate()
        return s.posts.filter { p ->
            p.author.id == s.me.id && p.createdAt > 0 &&
                Instant.ofEpochMilli(p.createdAt).atZone(ZoneId.systemDefault()).toLocalDate().let { it.year == now.year && it.month == now.month }
        }.mapNotNull { it.checkIn }.filter { isKilometers(it.unit) }.sumOf { it.amount }
    }

    /** Одно событие демо-жизни. Отдельно от цикла — чтобы проверять в тестах. */
    fun demoEvent(random: Random) {
        val s = _state.value
        val friends = s.followedPeople.filter { it.id !in s.blocked }
        val joined = s.groups.filter { it.joined && !it.muted }
        val mine = s.myPosts
        val requesters = Demo.people.filter { p -> p.id !in s.blocked && p.id !in s.followRequests && s.followers.none { it.id == p.id } }
        val options = buildList {
            if (friends.isNotEmpty()) repeat(4) { add(0) }
            if (joined.isNotEmpty()) repeat(2) { add(1) }
            if (mine.isNotEmpty()) repeat(2) { add(2) }
            if (s.privacy.closedProfile && requesters.isNotEmpty()) add(3)
        }
        if (options.isEmpty()) return
        when (options[random.nextInt(options.size)]) {
            0 -> friendCheckIn(friends[random.nextInt(friends.size)], random)
            1 -> {
                val group = joined[random.nextInt(joined.size)]
                val name = Demo.newcomers[random.nextInt(Demo.newcomers.size)]
                val person = User(500 + random.nextInt(1000), name, name.lowercase().replace(' ', '.'), hue = random.nextFloat())
                _state.update { st -> st.copy(groups = st.groups.map { if (it.id == group.id) it.copy(members = it.members + 1) else it }) }
                notify(Nudges.groupMember(group, person))
            }
            2 -> {
                val post = mine[random.nextInt(mine.size)]
                val fans = Demo.people.filter { it.id !in s.blocked }
                val fan = fans[random.nextInt(fans.size)]
                _state.update { st -> st.copy(posts = st.posts.map { if (it.id == post.id) it.copy(likes = it.likes + 1) else it }) }
                notify(Nudges.liked(fan, post))
            }
            else -> receiveFollowRequest(requesters[random.nextInt(requesters.size)].id)
        }
    }

    /** Друг отметил чек-ин: запись в ленте и уведомление — с тем, что вы обсуждали, если такое есть. */
    private fun friendCheckIn(friend: User, random: Random) {
        val texts = myTexts()
        val kinds = listOf("movie", "movie", "series", "book", "run", "bike", "walk", "coffee", "place", "concert")
        val kind = kinds[random.nextInt(kinds.size)]
        val activity = CHECK_IN_ACTIVITIES.first { it.id == kind }
        fun pick(list: List<String>): String {
            // Что вы обсуждали — в приоритете: ради этого и уведомление
            val talked = list.filter { Nudges.discussed(it, texts) }
            return if (talked.isNotEmpty() && random.nextFloat() < 0.75f) talked[random.nextInt(talked.size)]
            else list[random.nextInt(list.size)]
        }
        val subject = when (kind) {
            "movie" -> pick(Demo.movies)
            "series" -> pick(Demo.series)
            "book" -> pick(Demo.books)
            "coffee", "place" -> pick(Demo.places)
            "concert" -> pick(listOf("Сплин", "Мумий Тролль", "Земфира", "Би-2"))
            else -> pick(listOf("Набережная", "Парк", "Вокруг дома"))
        }
        val amount = when (kind) {
            "run" -> 3 + random.nextInt(10).toDouble()
            "bike" -> 10 + random.nextInt(30).toDouble()
            "walk" -> 2 + random.nextInt(8).toDouble()
            "book" -> 20 + random.nextInt(80).toDouble()
            else -> 0.0
        }
        val c = activity.checkIn(subject, amount)
        val id = nextId++
        _state.update { st ->
            st.copy(posts = listOf(Post(id, friend, "", 0, likes = random.nextInt(6), createdAt = clock(), checkIn = c)) + st.posts)
        }
        notify(Nudges.friendCheckIn(friend, c, Nudges.discussed(subject, texts), myKmThisMonth(), id))
    }

    // ==================== ВМЕСТЕ ====================

    fun selectTogetherSection(section: TogetherSection) {
        _state.update { it.copy(togetherSection = section) }
    }

    private fun together(change: (TogetherState) -> TogetherState?): Boolean {
        var ok = false
        _state.update { s -> change(s.together)?.let { ok = true; s.copy(together = it) } ?: s }
        return ok
    }

    private fun meId() = _state.value.me.id

    /** Позже, в демо: ответ «с той стороны». В тестах сразу не срабатывает. */
    private fun later(from: Long, to: Long, block: () -> Unit) {
        if (!demoReplies) return
        viewModelScope.launch {
            delay(Random.nextLong(from, to))
            block()
        }
    }

    /**
     * Сообщение человеку от вашего имени — без открытия переписки: так
     * договорённость об обмене или вылазке остаётся в чате.
     */
    private fun messageTo(userId: Int, text: String) {
        val person = Demo.person(userId) ?: return
        val existing = _state.value.chats.firstOrNull { it.peer.id == userId }
        val chatId = existing?.id ?: ((_state.value.chats.maxOfOrNull { it.id } ?: 0) + 1).also { id ->
            _state.update { s -> s.copy(chats = listOf(Chat(id, person, emptyList())) + s.chats) }
        }
        append(chatId, Message(nextId++, fromMe = true, text = text, time = now()))
    }

    /** Где вы: демо-точки один раз переезжают к вам. */
    fun setLocation(here: LatLon) {
        together { it.relocate(here, meId()) }
    }

    // ---- Цели ----

    fun joinGoal(id: String) { together { it.joinGoal(id) } }

    fun contribute(id: String, amount: Double) { together { it.contribute(id, amount) } }

    // ---- Навыки ----

    fun addSkill(teach: Boolean, skill: String) { together { it.addSkill(teach, skill) } }

    fun removeSkill(teach: Boolean, skill: String) { together { it.removeSkill(teach, skill) } }

    /**
     * Предложить обмен. give — чему научите вы, get — чему научат вас (""
     * — ничему). null — предложено; иначе — что не так (не хватает часов).
     */
    fun proposeDeal(personId: Int, give: String, get: String, minutes: Int): String? {
        val id = nextId++
        val ok = together { it.propose(id, personId, give, get, minutes, clock()) }
        if (!ok) return if (give.isEmpty() && get.isNotEmpty()) t("Не хватает часов в банке времени — научите кого-нибудь сами") else t("Выберите, чему учите или чему хотите научиться")
        messageTo(
            personId,
            when {
                give.isNotEmpty() && get.isNotEmpty() -> tf("Привет! Давай обмен: я научу «%s», а ты меня — «%s». По %s.", give, get, minutesTitle(minutes))
                get.isNotEmpty() -> tf("Привет! Научишь меня «%s»? %s — расплачусь часами из банка времени.", get, minutesTitle(minutes))
                else -> tf("Привет! Могу научить «%s» — %s.", give, minutesTitle(minutes))
            },
        )
        // В демо человек соглашается через полминуты
        later(15_000, 40_000) {
            val deal = _state.value.together.deals.firstOrNull { it.id == id } ?: return@later
            if (deal.status != DealStatus.Proposed) return@later
            together { it.setDeal(id, DealStatus.Agreed) }
            Demo.person(personId)?.let { notify(Nudges.dealAgreed(it, deal)) }
        }
        return null
    }

    fun setDeal(id: Long, status: DealStatus) { together { it.setDeal(id, status) } }

    // ---- Дневники ----

    fun startJourney(emoji: String, title: String, days: Int): Boolean =
        together { it.startJourney(nextId++, meId(), emoji, title, days, clock()) }

    fun addJourneyEntry(journeyId: Long, text: String, media: List<Attachment>) {
        val entryId = nextId++
        together { it.addEntry(journeyId, entryId, text, media, clock()) }
        // В демо к новой записи приходит совет
        later(20_000, 60_000) { demoTip(journeyId, entryId, Random.Default) }
    }

    private fun demoTip(journeyId: Long, entryId: Long, random: Random) {
        val journey = _state.value.together.journeys.firstOrNull { it.id == journeyId } ?: return
        val entry = journey.entries.firstOrNull { it.id == entryId } ?: return
        val adviser = Demo.people.filter { it.id !in _state.value.blocked && it.id != journey.authorId }.let { if (it.isEmpty()) return else it[random.nextInt(it.size)] }
        val tip = Tip(nextId++, adviser.id, TogetherDemo.tips[random.nextInt(TogetherDemo.tips.size)], createdAt = clock())
        together { it.addTip(journeyId, entryId, tip) }
        notify(Nudges.tipReceived(adviser, journey, entry.day))
    }

    fun addTip(journeyId: Long, entryId: Long, text: String) {
        together { it.addTip(journeyId, entryId, Tip(nextId++, meId(), text.trim().take(500), createdAt = clock())) }
    }

    fun toggleHelpful(journeyId: Long, entryId: Long, tipId: Long) {
        together { it.toggleHelpful(journeyId, entryId, tipId) }
    }

    // ---- Миссии ----

    fun joinMission(id: String) { together { it.joinMission(id) } }

    fun addFind(missionId: String, note: String, at: LatLon, photo: Attachment?) {
        together { it.addFind(missionId, MissionFind(nextId++, meId(), note.trim().take(200), at, photo, clock())) }
    }

    // ---- Клады ----

    fun hideCache(title: String, hint: String, difficulty: Int, size: String, at: LatLon): Boolean {
        val id = nextId++
        val ok = together { it.hideCache(Cache(id, meId(), title.trim().take(60), hint.trim().take(300), at, difficulty.coerceIn(1, 5), size, clock())) }
        // В демо ваш клад кто-нибудь находит
        if (ok) later(60_000, 180_000) {
            val finder = Demo.people.filter { it.id !in _state.value.blocked }.randomOrNull() ?: return@later
            if (_state.value.together.caches.none { it.id == id }) return@later
            _state.update { s ->
                s.copy(together = s.together.copy(caches = s.together.caches.map {
                    if (it.id == id) it.copy(logs = it.logs + CacheLog(nextId++, finder.id, t("Нашёл! Отличное место."), clock())) else it
                }))
            }
            _state.value.together.caches.firstOrNull { it.id == id }?.let { notify(Nudges.cacheFound(finder, it)) }
        }
        return ok
    }

    /** true — отметились; false — вы далеко или уже нашли. */
    fun findCache(id: Long, text: String, here: LatLon?): Boolean =
        together { it.findCache(id, CacheLog(nextId++, meId(), text.trim().ifEmpty { t("Нашёл!") }.take(300), clock()), here) }

    // ---- Вылазки ----

    fun createOuting(emoji: String, title: String, description: String, place: String, startsAt: Long, capacity: Int, at: LatLon?): Boolean {
        val id = nextId++
        val me = meId()
        val ok = together { it.createOuting(Outing(id, me, emoji, title.trim().take(60), description.trim().take(500), place.trim().take(80), startsAt, capacity, listOf(me), at)) }
        // В демо к вылазке присоединяются
        if (ok) later(30_000, 90_000) {
            val outing = _state.value.together.outings.firstOrNull { it.id == id } ?: return@later
            if (outing.full) return@later
            val guest = Demo.people.firstOrNull { it.id !in outing.participants && it.id !in _state.value.blocked } ?: return@later
            together { it.copy(outings = it.outings.map { o -> if (o.id == id && !o.full) o.copy(participants = o.participants + guest.id) else o }) }
            notify(Nudges.outingJoined(guest, outing))
        }
        return ok
    }

    fun toggleOuting(id: Long) { together { it.toggleOuting(id, meId()) } }

    fun cancelOuting(id: Long) { together { it.cancelOuting(id, meId()) } }

    // ---- Рисунок ----

    fun startDrawing(title: String, turns: Int): Long? {
        val id = nextId++
        return if (together { it.startDrawing(id, title, turns, meId(), clock()) }) id else null
    }

    /** Свой ход: передать дальше. В демо друг дорисует и вернёт. */
    fun drawTurn(id: Long, strokes: List<DrawStroke>, passTo: Int?) {
        together { it.drawTurn(id, meId(), strokes, passTo) }
        val drawing = _state.value.together.drawings.firstOrNull { it.id == id } ?: return
        val friendId = drawing.waitingFor ?: return
        if (friendId == meId()) return
        later(20_000, 50_000) {
            val friend = Demo.person(friendId) ?: return@later
            val colors = listOf(0xFFBC92CD.toInt(), 0xFF8CC4FF.toInt(), 0xFFFF9EDE.toInt(), 0xFFFFD86B.toInt(), 0xFF7BE0B0.toInt())
            val random = Random(clock())
            together { it.drawTurn(id, friendId, TogetherDemo.demoStrokes(random, colors[random.nextInt(colors.size)]), meId()) }
            _state.value.together.drawings.firstOrNull { it.id == id }?.let { notify(Nudges.drawingTurn(friend, it)) }
        }
    }

    // ---- Обмен ----

    fun addShare(kind: ShareKind, title: String, description: String, photo: Attachment?): Boolean {
        val me = meId()
        return together { it.addShare(ShareItem(nextId++, kind, title.trim().take(80), description.trim().take(500), me, me, photo = photo, createdAt = clock())) }
    }

    /** Попросить книгу или семена. В демо владелец передаёт через полминуты. */
    fun requestShare(id: Long) {
        val me = meId()
        together { it.requestShare(id, me) }
        val item = _state.value.together.shares.firstOrNull { it.id == id } ?: return
        if (item.requestedBy != me) return
        messageTo(item.holderId, tf("Привет! Можно мне «%s»? Заберу, когда удобно.", item.title))
        later(15_000, 45_000) {
            val current = _state.value.together.shares.firstOrNull { it.id == id } ?: return@later
            if (current.status != ShareStatus.Requested || current.requestedBy != me) return@later
            val giver = Demo.person(current.holderId) ?: return@later
            together { it.handOver(id, me) }
            notify(Nudges.shareHanded(giver, current))
        }
    }

    fun handOver(id: Long, to: Int) {
        together { it.handOver(id, to) }
    }

    fun offerHelp(id: Long) {
        together { it.offerHelp(id, meId()) }
        _state.value.together.shares.firstOrNull { it.id == id }?.let {
            messageTo(it.ownerId, tf("Привет! Могу помочь починить «%s». Когда удобно?", it.title))
        }
    }

    fun markFixed(id: Long) { together { it.markFixed(id) } }

    /** Событие «Вместе» в демо: шкалы растут, к вашим вещам и вылазкам приходят люди. */
    fun togetherEvent(random: Random) {
        // Общие шкалы растут всегда — сообщество не спит
        _state.update { s ->
            s.copy(together = s.together.copy(goals = s.together.goals.map { g ->
                g.copy(total = (g.total + g.target * (0.0002 + random.nextDouble() * 0.0008)).coerceAtMost(g.target), participants = g.participants + random.nextInt(4))
            }))
        }
        val s = _state.value
        val me = s.me.id
        val people = Demo.people.filter { it.id !in s.blocked }
        val myEntries = s.together.journeys.filter { it.authorId == me }.flatMap { j -> j.entries.map { j.id to it.id } }
        val myShares = s.together.shares.filter { it.holderId == me && it.status == ShareStatus.Available && it.kind != ShareKind.Repair }
        val options = buildList {
            if (myEntries.isNotEmpty()) add(0)
            if (myShares.isNotEmpty()) add(1)
        }
        if (options.isEmpty() || people.isEmpty()) return
        when (options[random.nextInt(options.size)]) {
            0 -> myEntries[random.nextInt(myEntries.size)].let { (j, e) -> demoTip(j, e, random) }
            else -> {
                val item = myShares[random.nextInt(myShares.size)]
                val asker = people[random.nextInt(people.size)]
                together { it.copy(shares = it.shares.map { x -> if (x.id == item.id) x.copy(status = ShareStatus.Requested, requestedBy = asker.id) else x }) }
                notify(Nudges.shareRequested(asker, item))
            }
        }
    }

    // ==================== СОХРАНЕНИЕ ====================

    /** То, что сохраняется на телефоне между запусками (см. StateStore). */
    fun snapshot(): Snapshot = _state.value.let {
        Snapshot(
            it.posts, it.chats, it.groups, it.prisms, it.following, it.notifications, nextId, it.privacy, it.blocked,
            it.tracks, it.playlists, it.reportedComments, it.stickers, it.profilePins,
            it.checkInTemplates, it.followRequests, it.approvedFollowers, it.sentRequests, it.recapNotified,
            it.together,
        )
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
                privacy = snapshot.privacy,
                blocked = snapshot.blocked - s.me.id,
                tracks = snapshot.tracks,
                playlists = snapshot.playlists,
                reportedComments = snapshot.reportedComments,
                stickers = snapshot.stickers,
                profilePins = snapshot.profilePins,
                checkInTemplates = snapshot.checkInTemplates,
                followRequests = snapshot.followRequests,
                approvedFollowers = snapshot.approvedFollowers,
                sentRequests = snapshot.sentRequests,
                recapNotified = snapshot.recapNotified,
                together = snapshot.together ?: s.together,
            ).withMe(s.me)
        }
    }

    private fun now(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

    companion object {
        /** Сколько фото и видео можно приложить к одной записи или сообщению. */
        const val MAX_MEDIA = 10

        /** Сколько записей можно закрепить в витрине профиля. */
        const val MAX_PINS = 3
    }
}

/** Текст сообщения для списков и цитат; без текста — что приложено. */
fun messagePreview(message: Message): String {
    if (message.text.isNotEmpty()) return message.text
    val a = message.attachments
    return when {
        a.isEmpty() -> ""
        a.size == 1 && a[0].voice -> t("Голосовое сообщение") + " · " + ru.wishka.memory.data.durationTitle(a[0].durationMs)
        a.all { it.sticker } -> t("Стикер")
        a.all { it.kind == ru.wishka.memory.data.MediaKind.Photo } -> if (a.size == 1) t("Фото") else tf("%s фото", a.size)
        a.all { it.kind == ru.wishka.memory.data.MediaKind.Video } -> if (a.size == 1) t("Видео") else tf("%s видео", a.size)
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
    val notifications: Notifications,
    val nextId: Long,
    val privacy: Privacy = Privacy(),
    val blocked: Set<Int> = emptySet(),
    val tracks: List<MusicTrack> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val reportedComments: Set<Long> = emptySet(),
    val stickers: List<Sticker> = emptyList(),
    val profilePins: List<Long> = emptyList(),
    val checkInTemplates: List<CheckInTemplate> = emptyList(),
    val followRequests: List<Int> = emptyList(),
    val approvedFollowers: Set<Int> = emptySet(),
    val sentRequests: Set<Int> = emptySet(),
    val recapNotified: Set<String> = emptySet(),
    val together: TogetherState? = null,
)

/** Переключить «нравится» у комментария с номером commentId. */
private fun Comment.flipLike(commentId: Long): Comment =
    if (id != commentId) this else copy(liked = !liked, likes = (likes + if (liked) -1 else 1).coerceAtLeast(0))

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
