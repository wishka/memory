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
import ru.wishka.memory.data.Activity
import ru.wishka.memory.data.ActiveCall
import ru.wishka.memory.data.CallPhase
import ru.wishka.memory.data.CallRecord
import ru.wishka.memory.data.CallTransport
import ru.wishka.memory.data.Calls
import ru.wishka.memory.data.DemoCallTransport
import ru.wishka.memory.data.Highlight
import ru.wishka.memory.data.PostRef
import ru.wishka.memory.data.Stories
import ru.wishka.memory.data.Story
import ru.wishka.memory.data.StoryCircle
import ru.wishka.memory.data.StoryViewer
import ru.wishka.memory.data.DemoLiveTransport
import ru.wishka.memory.data.LiveComment
import ru.wishka.memory.data.LiveStream
import ru.wishka.memory.data.LiveTransport
import ru.wishka.memory.data.Lives
import ru.wishka.memory.data.ChatCrypto
import ru.wishka.memory.data.ChatKeyPair
import ru.wishka.memory.data.ChatKeys
import ru.wishka.memory.data.Disappear
import ru.wishka.memory.data.AppEvent
import ru.wishka.memory.data.Telemetry
import ru.wishka.memory.data.LinkTarget
import ru.wishka.memory.data.Search
import ru.wishka.memory.data.SearchResults
import ru.wishka.memory.data.ActivityGroup
import ru.wishka.memory.data.ActivityItem
import ru.wishka.memory.data.ActivityKind
import ru.wishka.memory.data.DAY_MS
import ru.wishka.memory.data.Premium
import ru.wishka.memory.data.PremiumFeature
import ru.wishka.memory.data.Subscription
import ru.wishka.memory.data.BillingPeriod
import ru.wishka.memory.data.PlanTier
import ru.wishka.memory.data.GuideTopic
import ru.wishka.memory.data.GuideChapter
import ru.wishka.memory.data.GuideAction
import ru.wishka.memory.data.Guide
import ru.wishka.memory.data.Chat
import ru.wishka.memory.data.Backdrop
import ru.wishka.memory.data.normalizeUrl
import ru.wishka.memory.data.ProductRef
import ru.wishka.memory.data.Product
import ru.wishka.memory.data.GroupAccent
import ru.wishka.memory.data.GroupInfo
import ru.wishka.memory.data.LegalDoc
import ru.wishka.memory.data.Report
import ru.wishka.memory.data.ReportKind
import ru.wishka.memory.data.ReportReason
import ru.wishka.memory.data.ReportTarget
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
import ru.wishka.memory.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.random.Random
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import java.time.LocalTime
import ru.wishka.memory.data.net.MediaQuality
import ru.wishka.memory.data.net.DemoRealtime
import ru.wishka.memory.data.net.DeviceInfo
import ru.wishka.memory.data.net.RtEvent
import ru.wishka.memory.data.net.Net
import ru.wishka.memory.data.net.NetError
import ru.wishka.memory.data.net.OpKind
import ru.wishka.memory.data.net.Pager
import ru.wishka.memory.data.net.UploadJob
import ru.wishka.memory.data.net.UploadState
import java.time.format.DateTimeFormatter

/** Разделы профиля за счётчиками: записи, друзья, группы, сохранённое, заявки в друзья. */
enum class ProfileSection(private val ru: String) {
    Posts("Записи"),
    /** Друзья (взаимно): у себя — с «Написать» и удалением из друзей, у другого — его друзья. */
    Friends("Друзья"),
    Groups("Группы"),
    Saved("Сохранённое"),
    Music("Моя музыка"),
    Stickers("Студия"),
    Recap("Итоги"),
    Shelf("Полка"),
    /** Входящие заявки в друзья и отправленные мной. */
    Requests("Заявки в друзья"),
    /** Memory+: тарифы, пробный период, смена тарифа. */
    Premium("Memory+"),
    /** Записи, где меня отметили на фото. */
    Tagged("Отметки"),
    ;

    /** Название на выбранном языке. */
    val title: String get() = t(ru)
}

/** Связь с сервером — для полоски «нет сети». */
enum class NetStatus { Online, Offline, ServerDown }

/** Как подгружается старое в ленте. */
data class FeedPaging(
    val loading: Boolean = false,
    /** Дальше ничего нет. */
    val end: Boolean = false,
    /** Показано сохранённое, свежего с сервера ещё не было. */
    val fromCache: Boolean = false,
    val cachedAt: Long = 0,
    /** Не загрузилось: «нет сети», «сервер не отвечает». */
    val error: NetError.Kind? = null,
)

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
    Growth("Друзья и идеи"),
    Sounds("Звуки"),
    Wallpaper("Фон переписок"),
    Legal("Документы"),
    /** Гид по Memory: главы → темы с шагами. */
    Guide("Гид по Memory"),
    /** Оформление: как в системе, тёмное или светлое. */
    Theme("Оформление"),
    /** Качество фото и видео при отправке, загрузки, очередь, кэш. */
    Network("Данные и сеть"),
    /** Скрытые слова, фильтр оскорблений и спама. */
    HiddenWords("Скрытые слова"),
    /** Устройства, с которых вошли. */
    Devices("Устройства"),
    ;

    val title: String get() = t(ru)
}

/** Разделы полки. */
enum class ShelfTab(private val ru: String, val emoji: String) {
    ForYou("Для вас", "✨"),
    Films("Кино", "🎬"),
    Books("Книги", "📖"),
    Sport("Спорт", "🏃"),
    Places("Места", "📍"),
    Progress("Права", "🔓"),
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
    /** Раздел чужого профиля (записи, друзья) поверх его страницы. */
    val personSection: ProfileSection? = null,
    /**
     * Мои друзья — дружба взаимная. Имя поля прежнее (было «подписки»):
     * так его хранят сохранения. Записи друзей — во вкладке «Истории»,
     * закрытые профили друзей видны.
     */
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
    /** Входящие заявки в друзья ко мне — новые первыми. Имя поля прежнее — для сохранений. */
    val followRequests: List<Int> = emptyList(),
    /** Мои исходящие заявки в друзья, пока их не приняли. */
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
    /** Полка: отметки без записи в ленте — любимое при знакомстве, «хочу посмотреть». */
    val shelfMarks: List<ShelfMark> = emptyList(),
    /** Рекомендации, от которых отказались («не моё»): ключи «film:дюна». */
    val dismissedRecs: Set<String> = emptySet(),
    /** Отклик на только что отмеченный чек-ин; null — не показываем. */
    val echo: CheckInEcho? = null,
    /** Журнал событий роста: приглашения, карточки, чек-ины. */
    val growth: List<GrowthEvent> = emptyList(),
    /** Кто вас позвал в Memory (по ссылке). */
    val invitedBy: Invite? = null,
    /** Приглашение, по которому только что открыли приложение: показать, к чему зовут. */
    val openedInvite: Invite? = null,
    /** День, когда закрыли «В этот день»: «2026-10-12». */
    val memorySeen: String = "",
    /** Подписка Memory+ — как хранится. */
    val subscription: Subscription = Subscription(),
    /** Какой тариф действует сейчас (закончившаяся подписка — Free). Обновляет refreshPlan(). */
    val plan: PlanTier = if (Premium.enabled) PlanTier.Free else Premium.OPEN_TIER,
    /** Шторка «Это в Memory+»: что пытались открыть без подписки. */
    val paywall: PremiumFeature? = null,
    /** Открытая глава гида; null — список глав. */
    val guideChapter: String? = null,
    /** Прочитанные темы гида: «глава/тема». */
    val guideRead: Set<String> = emptySet(),
    /** Карточку «Гид по Memory» в ленте закрыли. */
    val guideHidden: Boolean = false,
    /** Гид открыли не из настроек — «назад» из списка глав вернёт на эту вкладку. */
    val guideReturn: Tab? = null,
    /** Полка: открытый раздел. */
    val shelfTab: ShelfTab = ShelfTab.Films,
    /** Коллекции стикеров, как наборы в iOS. */
    val stickerPacks: List<StickerPack> = emptyList(),
    /** Общий фон переписок. */
    val backdrop: Backdrop = Backdrop.DEFAULT,
    /** Недавние гифки — первыми в выборе. */
    val recentGifs: List<Attachment> = emptyList(),
    /** Жалобы, которые ждут отправки модераторам (с сервером — уйдут им). */
    val reports: List<Report> = emptyList(),
    /** На что сейчас жалуются: открыт выбор причины. */
    val reporting: ReportTarget? = null,
    /** Открытый документ: соглашение, политика, правила… */
    val legalDoc: LegalDoc? = null,
    /** Открытый товар витрины: группа и товар; null — нет. */
    val openProduct: Pair<Int, Long>? = null,
    /** Лента активности: «нравится», комментарии, упоминания, заявки — новые сверху. */
    val activity: List<ActivityItem> = emptyList(),
    /** Когда ленту активности открывали в последний раз (мс): что позже — «новое». */
    val activitySeen: Long = 0,
    /** Открыта лента активности. */
    val activityOpen: Boolean = false,
    /** Открыт поиск и что в нём набрано. */
    val searchOpen: Boolean = false,
    val searchQuery: String = "",
    /** Недавние запросы — новые первыми. */
    val searchHistory: List<String> = emptyList(),
    /** Открытая по ссылке запись — отдельным экраном. */
    val openPostId: Long? = null,
    /** Своя запись, которую сейчас правят. */
    val editingPostId: Long? = null,
    /** Эфиры друзей, которые идут сейчас. */
    val lives: List<LiveStream> = emptyList(),
    /** Свой эфир, пока он идёт. */
    val myLive: LiveStream? = null,
    /** Какой эфир открыт на экране (свой или друга). */
    val watchingLiveId: Long? = null,
    /** Открыт запуск эфира: название и кто видит. */
    val liveStarting: Boolean = false,
    /** Итог только что закончившегося своего эфира. */
    val liveSummary: LiveStream? = null,
    /** Ведущий нажал «назад» — спросить, закончить ли эфир. */
    val confirmEndLive: Boolean = false,
    /** Истории: свои (и в архиве) и друзей — за сутки и из «Актуального». */
    val stories: List<Story> = emptyList(),
    /** Какие истории я уже смотрел. */
    val seenStories: Set<Long> = emptySet(),
    /** «Близкие друзья»: им видны истории «для близких». */
    val closeFriends: Set<Int> = emptySet(),
    /** «Актуальное»: свои подборки и подборки друзей. */
    val highlights: List<Highlight> = emptyList(),
    /** Открыт просмотр историй. */
    val storyViewer: StoryViewer? = null,
    /** Открыта новая история. */
    val storyComposer: Boolean = false,
    /** Кто посмотрел мою историю — открыт список. */
    val storyViewersOf: Long? = null,
    /** История звонков. */
    val callLog: List<CallRecord> = emptyList(),
    /** Звонок, который идёт сейчас. */
    val call: ActiveCall? = null,
    /** Репост: какую запись репостят (открыта шторка). */
    val repostingPostId: Long? = null,
    /** «Отправить в чат»: какую запись отправляют (открыт выбор переписки). */
    val sharingPostId: Long? = null,
    /** Связь: есть, нет сети, сервер не отвечает. */
    val netStatus: NetStatus = NetStatus.Online,
    /** Старые записи ленты — подгружены с сервера (или из кэша) страницами. */
    val olderFeed: List<Post> = emptyList(),
    /** Справочник людей с сервера сменился (Directory.version) — перерисовать. */
    val peopleVersion: Int = 0,
    /** «Интересное» под лентой друзей: записи людей, на которых вы не подписаны (с сервера). */
    val explore: List<Post> = emptyList(),
    val explorePaging: FeedPaging = FeedPaging(),
    /** Приложение работает с настоящим сервером (не встроенным демо). */
    val serverMode: Boolean = false,
    val feedPaging: FeedPaging = FeedPaging(),
    /** Загрузки фото и видео: что идёт, что ждёт, что на паузе. */
    val uploads: List<UploadJob> = emptyList(),
    /** Сколько действий ждут отправки. */
    val outboxCount: Int = 0,
    /** Качество отправки (настройка телефона — DevicePrefs). */
    val mediaQuality: MediaQuality = MediaQuality.Standard,
    /** Демо: «как будто нет сети» — проверить очередь и кэш. */
    val demoOffline: Boolean = false,
    /** Сколько места занимает сохранённое для офлайна. */
    val cacheBytes: Long = 0,
    /** Что сказать о сети: «запись не отправлена» и т. п.; null — нечего. */
    val netNotice: String? = null,
    /** Устройства, с которых вошли в аккаунт. */
    val devices: List<DeviceInfo> = emptyList(),
    /** Короткое сообщение внизу экрана («Слишком часто…»); null — нечего. */
    val notice: String? = null,
    /** Вход на сервер закрыт (вышли с этого устройства на другом) — нужно войти снова. */
    val signedOut: Boolean = false,
) {
    /** Кружки историй друзей (свой — отдельно). */
    fun storyCircles(now: Long): List<StoryCircle> =
        Stories.rings(
            stories.filter { st -> st.authorId in following && st.authorId !in blocked && Stories.visible(st, me.id, true, true) },
            Demo.people, me.id, seenStories, now,
        )

    /** Свои истории за сутки — по порядку. */
    fun myActiveStories(now: Long): List<Story> = stories.filter { it.authorId == me.id && it.active(now) }.sortedBy { it.createdAt }

    /** Архив своих историй — новые сверху. */
    val storyArchive: List<Story> get() = stories.filter { it.authorId == me.id }.sortedByDescending { it.createdAt }

    fun highlightsOf(userId: Int): List<Highlight> = highlights.filter { it.ownerId == userId && it.storyIds.isNotEmpty() }

    fun story(id: Long): Story? = stories.firstOrNull { it.id == id }

    /** Эфир на экране. */
    val watchingLive: LiveStream? get() = watchingLiveId?.let { id -> myLive?.takeIf { it.id == id } ?: lives.firstOrNull { it.id == id } }

    /** Эфиры, которые можно смотреть: без заблокированных. */
    val visibleLives: List<LiveStream> get() = lives.filter { it.live && it.hostId !in blocked }

    /** Люди, которых можно найти и упомянуть: все, кроме себя и заблокированных. */
    val searchablePeople: List<User> get() = knownPeople.filter { it.id !in blocked }

    /** Все, кого знает телефон: демо-люди и люди с сервера. */
    val knownPeople: List<User> get() = (Demo.people + Directory.all()).distinctBy { it.id }

    /** Записи, которые можно найти: лента, свои и группы. */
    val searchablePosts: List<Post> get() = (feed + groups.flatMap { it.posts }).filter { it.author.id !in blocked }.distinctBy { it.id }

    /** Результаты текущего запроса. */
    val searchResults: SearchResults get() = Search.all(searchQuery, searchablePeople, searchablePosts, groups)

    /** Новых событий в ленте активности — точка на колокольчике. */
    val activityUnread: Int get() = Activity.unread(activity, activitySeen, blocked)

    /** Полка — собирается сама из чек-инов и отметок. */
    val shelf: Shelf get() = Shelf.build(posts.datedCheckIns(me.id), shelfMarks)
    val myCheckIns: List<Dated> get() = posts.datedCheckIns(me.id)
    /** Подборка: kind — «film» или «book». */
    fun recommendations(kind: String, limit: Int = 6, shelf: Shelf = this.shelf): List<Recommendation> {
        // Персональные рекомендации выключены в «Приватности» — ничего не подбираем
        if (!privacy.personalRecs) return emptyList()
        return recommend(shelf.rated, shelf.keys + dismissedRecs, DemoTaste.ratings.filterKeys { it !in blocked }, kind, limit)
    }
    val runPlan: RunPlan get() = ru.wishka.memory.data.runPlan(myCheckIns)
    val unlocks: List<UnlockProgress> get() = unlockProgress(together, me.id)
    fun unlocked(u: Unlock): Boolean = together.unlocked(u, me.id)
    val movedKm: Double get() = ru.wishka.memory.data.movedKm(myCheckIns)
    val inviteCode: String get() = Invites.code(me.handle)
    val openPerson: User? get() = openPersonId?.let { Demo.person(it) }
    val openGroup: Group? get() = groups.firstOrNull { it.id == openGroupId }
    val myGroups: List<Group> get() = groups.filter { it.joined }
    fun postsOf(userId: Int): List<Post> = posts.filter { it.author.id == userId }
    val openChat: Chat? get() = chats.firstOrNull { it.id == openChatId }
    /** Счётчик на вкладке «Чаты»: переписки без звука не считаются. */
    val unreadTotal: Int get() = chats.filterNot { it.muted || it.request }.sumOf { it.unread }
    /** Список чатов: закреплённые сверху, остальные — по последнему сообщению. */
    val chatList: List<Chat> get() = chats.filterNot { it.request }.sortedByDescending { it.onTop }

    /** Запросы на переписку: написали не друзья. */
    val requestChats: List<Chat> get() = chats.filter { it.request }

    /**
     * Скрыть ли текст фильтром (скрытые слова, оскорбления, спам): своё и
     * сказанное другом в переписке на двоих — не скрываем.
     */
    fun hiddenReason(text: String, authorId: Int, chat: Chat? = null): HiddenReason? {
        if (authorId == me.id || authorId == AppViewModel.SYSTEM_AUTHOR) return null
        if (chat != null && !chat.isGroup && !chat.request && chat.peer.id in following) return null
        return Moderation.check(text, privacy.moderation)
    }

    /** «Вера печатает…», «Вера и Марк печатают…» — для группы; для двоих — просто «печатает…». */
    fun typingTitle(chat: Chat): String? {
        val ids = chat.typingIds.filter { it != me.id }
        if (ids.isEmpty()) return if (chat.typing) t("печатает…") else null
        if (!chat.isGroup) return t("печатает…")
        val names = ids.mapNotNull { Demo.person(it)?.name?.substringBefore(' ') }
        return when (names.size) {
            0 -> t("печатает…")
            1 -> tf("%s печатает…", names[0])
            else -> tf("%s и %s печатают…", names[0], names[1])
        }
    }
    /**
     * Лента без записей тех, кто в чёрном списке, и без записей закрытых
     * профилей тех, кто вам не друг.
     */
    val feed: List<Post> get() = posts.filter { it.author.id !in blocked && canSee(it.author) }
    /** Записи человека видны: профиль открыт, вы друзья или это вы. */
    fun canSee(user: User): Boolean = !user.closed || user.id in following || user.id == me.id

    /** Лента целиком: свежее с телефона, ниже — старое с сервера (без повторов и заблокированных). */
    val feedWithOlder: List<Post>
        get() {
            val local = feed
            val ids = local.map { it.id }.toHashSet()
            return local + olderFeed.filter { it.id !in ids && it.author.id !in blocked && canSee(it.author) }
        }

    /** Прогресс отправки записи или сообщения по ключу очереди (0…1); нет загрузок — null. */
    fun uploadProgress(owner: String): Float? {
        val mine = uploads.filter { it.owner == owner }
        if (mine.isEmpty()) return null
        val total = mine.sumOf { maxOf(it.size, 1) }
        return (mine.sumOf { if (it.state == UploadState.Done) maxOf(it.size, 1) else it.offset }.toFloat() / total).coerceIn(0f, 1f)
    }
    /** Люди, приславшие мне заявку в друзья. */
    val requestPeople: List<User> get() = followRequests.mapNotNull { Demo.person(it) }
    /** Люди, которым я отправил заявку в друзья. */
    val sentRequestPeople: List<User> get() = sentRequests.mapNotNull { Demo.person(it) }
    /** Что между мной и человеком: друзья, заявка от меня, заявка ко мне или ничего. */
    fun friendState(userId: Int): FriendState = when (userId) {
        in following -> FriendState.Friends
        in sentRequests -> FriendState.Requested
        in followRequests -> FriendState.Incoming
        else -> FriendState.None
    }
    val blockedPeople: List<User> get() = knownPeople.filter { it.id in blocked }
    fun isBlocked(userId: Int): Boolean = userId in blocked
    /**
     * Друзья другого человека. Сервера нет, поэтому видны только знакомые
     * по демо-данным — и вы, если вы друзья.
     */
    fun friendsOf(user: User): List<User> =
        (if (user.id in following) listOf(me) else emptyList()) +
            Demo.people.filter { it.id != user.id && it.id !in blocked }
    val myPosts: List<Post> get() = posts.filter { it.author.id == me.id }
    /** Вкладка «Истории» под кружками: свои записи и записи друзей — новые сверху, как и в ленте. */
    val friendPosts: List<Post> get() = posts.filter { (it.author.id in following || it.author.id == me.id) && it.author.id !in blocked }
    /** Мои друзья — люди. */
    val friends: List<User> get() = knownPeople.filter { it.id in following }

    /** Записи, где меня отметили на фото, — «Отметки» в профиле. */
    val taggedPosts: List<Post>
        get() = (posts + groups.flatMap { it.posts }).filter { p -> p.author.id !in blocked && p.media.any { a -> a.tags.any { it.userId == me.id } } }
            .distinctBy { it.id }.sortedByDescending { it.createdAt }
    /** «Сохранённое» в профиле: PRISM в закладках. */
    val savedPrisms: List<Prism> get() = prisms.filter { it.saved }
    fun track(id: String): MusicTrack? = tracks.firstOrNull { it.id == id }
    fun playlistTracks(playlist: Playlist): List<MusicTrack> = playlist.trackIds.mapNotNull { track(it) }
    /** Запись по номеру — в ленте или в группе: для обсуждения. */
    fun findPost(id: Long): Post? =
        posts.firstOrNull { it.id == id } ?: groups.firstNotNullOfOrNull { g -> g.posts.firstOrNull { it.id == id } }
            ?: olderFeed.firstOrNull { it.id == id } ?: explore.firstOrNull { it.id == id }

    /** «Интересное» без заблокированных и без тех, кто уже в ленте. */
    val exploreVisible: List<Post>
        get() {
            val ids = feedWithOlder.map { it.id }.toHashSet()
            return explore.filter { it.id !in ids && it.author.id !in blocked && it.author.id !in following }
        }
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
                openProduct = null,
                openPersonId = null,
                personSection = null,
                returnToChatId = null,
                // Нажатие на «Профиль» внизу всегда ведёт на сам профиль
                profileSection = null,
                settingsPage = null,
                guideChapter = null,
                guideReturn = null,
            )
        }
    }

    /** Раздел профиля: нажатие на «записи», «друзья», «группы» или «Сохранённое». */
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
        _state.update {
            it.copy(
                tab = Tab.Profile, settingsPage = page, profileSection = null, openPersonId = null,
                openChatId = null, openGroupId = null, groupSettingsOpen = false, openProduct = null,
                guideChapter = null, guideReturn = null,
            )
        }
    }

    /**
     * «Назад» в настройках: со страницы — в список, из списка — в профиль.
     * В гиде: из главы — к списку глав; гид, открытый из ленты, — обратно в ленту.
     */
    fun settingsBack() {
        _state.update {
            when {
                it.settingsPage == SettingsPage.Guide && it.guideChapter != null -> it.copy(guideChapter = null)
                it.settingsPage == SettingsPage.Guide && it.guideReturn != null ->
                    it.copy(settingsPage = null, tab = it.guideReturn, guideReturn = null)
                else -> it.copy(settingsPage = if (it.settingsPage == SettingsPage.Main) null else SettingsPage.Main)
            }
        }
    }

    // ==================== MEMORY+ ====================

    /** Тариф, который действует сейчас. */
    fun plan(): PlanTier = if (!Premium.enabled) Premium.OPEN_TIER else _state.value.subscription.tierAt(clock())

    /** Есть ли доступ к премиум-функции — без шторки. */
    fun has(feature: PremiumFeature): Boolean = plan().covers(feature.tier)

    /**
     * Проверка перед премиум-действием: есть доступ — true; нет —
     * открывается шторка «Это в Memory+» и ответ false.
     */
    fun require(feature: PremiumFeature): Boolean {
        if (!Premium.enabled) return true
        refreshPlan()
        if (has(feature)) return true
        _state.update { it.copy(paywall = feature) }
        return false
    }

    fun closePaywall() {
        _state.update { it.copy(paywall = null) }
    }

    /** Экран «Memory+» в профиле: тарифы, пробный период, смена тарифа. */
    fun openPremium() {
        if (!Premium.enabled) return
        refreshPlan()
        _state.update { it.copy(paywall = null) }
        openProfileSection(ProfileSection.Premium)
    }

    /**
     * Сверить подписку с часами: закончилась — тариф бесплатный.
     * Оплаченная с продлением в демо продлевается сама — так сделает магазин.
     */
    fun refreshPlan() {
        val now = clock()
        _state.update { s ->
            var sub = s.subscription
            if (sub.tier != PlanTier.Free && !sub.active(now) && sub.autoRenew && !sub.trial) {
                var until = sub.until
                while (until <= now) until += sub.period.days * DAY_MS
                sub = sub.copy(until = until)
            }
            val plan = if (Premium.enabled) sub.tierAt(now) else Premium.OPEN_TIER
            if (sub == s.subscription && plan == s.plan) s else s.copy(subscription = sub, plan = plan)
        }
    }

    /**
     * Пробный период — 3 дня, один раз на аккаунт, на любой тариф.
     * Ответ — текст ошибки или null.
     */
    fun startTrial(tier: PlanTier): String? {
        if (tier == PlanTier.Free) return t("Выберите тариф")
        val now = clock()
        val s = _state.value.subscription
        if (s.trialUsed) return t("Пробный период уже использован")
        if (s.active(now) && !s.trial) return t("Подписка уже оформлена")
        _state.update {
            it.copy(
                subscription = Subscription(tier, BillingPeriod.Month, now + Premium.TRIAL_DAYS * DAY_MS, trial = true, autoRenew = false, trialUsed = true),
                paywall = null,
            )
        }
        refreshPlan()
        return null
    }

    /**
     * Оформить или сменить тариф и срок. Новый действует сразу, срок
     * считается заново. Пока оплата демонстрационная — деньги не списываются.
     */
    fun subscribe(tier: PlanTier, period: BillingPeriod) {
        if (tier == PlanTier.Free) {
            cancelRenewal()
            return
        }
        val now = clock()
        _state.update {
            it.copy(
                subscription = Subscription(tier, period, now + period.days * DAY_MS, trial = false, autoRenew = true, trialUsed = it.subscription.trialUsed),
                paywall = null,
            )
        }
        refreshPlan()
    }

    /** Отменить продление: премиум работает до конца срока. */
    fun cancelRenewal() {
        _state.update { s -> if (s.subscription.trial) s else s.copy(subscription = s.subscription.copy(autoRenew = false)) }
    }

    /** Вернуть продление, пока срок не кончился. */
    fun resumeRenewal() {
        val now = clock()
        _state.update { s ->
            val sub = s.subscription
            if (sub.trial || !sub.active(now)) s else s.copy(subscription = sub.copy(autoRenew = true))
        }
    }

    // ==================== ГИД ====================

    /** Гид: список глав или сразу глава. Открытый не из настроек помнит, куда вернуться. */
    fun openGuide(chapterId: String? = null) {
        val from = _state.value.let { if (it.settingsPage == null) it.tab else null }
        openSettings(SettingsPage.Guide)
        _state.update { it.copy(guideChapter = Guide.chapter(chapterId)?.id, guideReturn = from) }
    }

    fun openGuideChapter(chapterId: String) {
        if (Guide.chapter(chapterId) == null) return
        _state.update { it.copy(guideChapter = chapterId) }
    }

    fun closeGuideChapter() {
        _state.update { it.copy(guideChapter = null) }
    }

    /** Тему раскрыли — она прочитана. */
    fun markGuideRead(chapter: GuideChapter, topic: GuideTopic) {
        val key = chapter.key(topic)
        if (key in _state.value.guideRead) return
        _state.update { it.copy(guideRead = it.guideRead + key) }
    }

    /** Убрать карточку гида из ленты; сам гид остаётся в настройках. */
    fun hideGuideCard() {
        _state.update { it.copy(guideHidden = true) }
    }

    /** Кнопка темы «Открыть …»: туда, о чём тема. */
    fun guideGo(action: GuideAction) {
        when (action) {
            GuideAction.Feed -> selectTab(Tab.Feed)
            GuideAction.Stories -> selectTab(Tab.Stories)
            GuideAction.Prism -> selectTab(Tab.Prism)
            GuideAction.NewPrism -> { selectTab(Tab.Prism); openPrismCreator() }
            GuideAction.Chats -> { selectTab(Tab.Chats); _state.update { it.copy(openChatId = null) } }
            GuideAction.Groups -> { selectTab(Tab.Groups); _state.update { it.copy(openGroupId = null) } }
            GuideAction.Together -> selectTab(Tab.Together)
            GuideAction.Profile -> selectTab(Tab.Profile)
            GuideAction.Friends -> openProfileSection(ProfileSection.Friends)
            GuideAction.Requests -> openProfileSection(ProfileSection.Requests)
            GuideAction.Saved -> openProfileSection(ProfileSection.Saved)
            GuideAction.Music -> openProfileSection(ProfileSection.Music)
            GuideAction.Studio -> openProfileSection(ProfileSection.Stickers)
            GuideAction.Shelf -> openProfileSection(ProfileSection.Shelf)
            GuideAction.Recap -> openProfileSection(ProfileSection.Recap)
            GuideAction.Settings -> openSettings(SettingsPage.Main)
            GuideAction.Privacy -> openSettings(SettingsPage.Privacy)
            GuideAction.Security -> openSettings(SettingsPage.Security)
            GuideAction.Blacklist -> openSettings(SettingsPage.Blacklist)
            GuideAction.Notifications -> openSettings(SettingsPage.Notifications)
            GuideAction.Sounds -> openSettings(SettingsPage.Sounds)
            GuideAction.Wallpaper -> openSettings(SettingsPage.Wallpaper)
            GuideAction.Language -> openSettings(SettingsPage.Language)
            GuideAction.Legal -> openSettings(SettingsPage.Legal)
            GuideAction.Growth -> openSettings(SettingsPage.Growth)
            GuideAction.Premium -> openPremium()
        }
    }

    /** Записи или друзья другого человека — нажатием на счётчик на его странице. */
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
            // Во время звонка «назад» не сбрасывает звонок — для этого красная кнопка
            s.call != null -> true
            s.storyViewersOf != null -> { closeStoryViewers(); true }
            s.storyViewer != null -> { closeStories(); true }
            s.storyComposer -> { closeStoryComposer(); true }
            s.repostingPostId != null || s.sharingPostId != null -> { closeRepost(); true }
            s.watchingLiveId != null && s.myLive?.id == s.watchingLiveId -> { _state.update { it.copy(confirmEndLive = true) }; true }
            s.watchingLiveId != null -> { leaveLive(); true }
            s.liveStarting -> { closeLiveStart(); true }
            s.activityOpen -> { closeActivity(); true }
            s.editingPostId != null -> { cancelEdit(); true }
            s.openProduct != null -> { closeProduct(); true }
            s.viewing != null -> { closeMedia(); true }
            s.commentsPostId != null || s.commentsPrismId != null -> { closeComments(); true }
            s.openPostId != null -> { closePost(); true }
            s.openChatId != null -> { closeChat(); true }
            s.personSection != null && s.openPersonId != null -> { closePersonSection(); true }
            s.openPersonId != null -> { closePerson(); true }
            s.groupSettingsOpen -> { closeGroupSettings(); true }
            s.openGroupId != null -> { closeGroup(); true }
            s.searchOpen -> { closeSearch(); true }
            s.settingsPage != null -> { settingsBack(); true }
            s.profileSection != null -> { closeProfileSection(); true }
            s.tab != Tab.Feed -> { selectTab(Tab.Feed); true }
            else -> false
        }
    }

    // ==================== АКТИВНОСТЬ ====================

    /** Записать событие в ленту активности. person == null — несколько человек (голоса). */
    fun record(kind: ActivityKind, person: User?, targetId: Long = 0, preview: String = "", count: Int = 1) {
        val s = _state.value
        if (person != null && (person.id == s.me.id || person.id in s.blocked)) return
        val item = ActivityItem(
            nextId++, kind, person?.id ?: 0, clock(), targetId, preview.trim().take(80), count,
            actorName = if (person != null && Demo.person(person.id) == null) person.name else "",
            actorHue = person?.hue ?: 0.5f,
        )
        _state.update { it.copy(activity = Activity.add(it.activity, item)) }
    }

    fun openActivity() {
        Telemetry.track(AppEvent.ActivityOpened)
        _state.update { it.copy(activityOpen = true, openPersonId = null, openGroupId = null, personSection = null) }
    }

    /** Закрыли ленту — всё в ней прочитано. */
    fun closeActivity() {
        _state.update { it.copy(activityOpen = false, activitySeen = clock()) }
    }

    /** Отметить всё прочитанным, не закрывая. */
    fun markActivitySeen() {
        _state.update { it.copy(activitySeen = clock()) }
    }

    /** Нажатие на событие: туда, о чём оно. */
    fun openActivityItem(g: ActivityGroup) {
        val s = _state.value
        _state.update { it.copy(activityOpen = false, activitySeen = clock()) }
        when (g.kind) {
            ActivityKind.Like, ActivityKind.Comment, ActivityKind.Reply, ActivityKind.Mention,
            ActivityKind.CommentLike, ActivityKind.PollVotes ->
                if (s.findPost(g.targetId) != null) openComments(g.targetId) else g.actors.firstOrNull()?.let { openPerson(it.id) }
            ActivityKind.PrismLike, ActivityKind.PrismComment -> {
                selectTab(Tab.Prism)
                if (s.prisms.any { it.id == g.targetId }) _state.update { it.copy(prismStartId = g.targetId) }
            }
            ActivityKind.FriendRequest -> openProfileSection(ProfileSection.Requests)
            ActivityKind.FriendAccepted -> g.actors.firstOrNull()?.let { openPerson(it.id) }
            ActivityKind.GroupJoin -> { selectTab(Tab.Groups); openGroup(g.targetId.toInt()) }
            ActivityKind.Live ->
                if (s.lives.any { it.id == g.targetId && it.live }) watchLive(g.targetId)
                else g.actors.firstOrNull()?.let { openPerson(it.id) }
            ActivityKind.Repost, ActivityKind.Tagged ->
                if (s.findPost(g.targetId) != null) _state.update { it.copy(openPostId = g.targetId) } else g.actors.firstOrNull()?.let { openPerson(it.id) }
            ActivityKind.StoryReply, ActivityKind.MissedCall ->
                g.actors.firstOrNull()?.let { a -> s.chats.firstOrNull { it.peer.id == a.id }?.let { openChat(it.id) } ?: openPerson(a.id) }
        }
    }

    /** Демо: друг комментирует вашу запись; иногда — упоминает вас. */
    private fun demoComment(post: Post, fan: User, random: Random) {
        val me = _state.value.me
        val lines = listOf("Как красиво!", "Это где?", "Хочу так же", "Сохранила себе", "Вот это да", "Отличный момент")
        val mention = random.nextInt(3) == 0
        val text = if (mention) tf("@%s, помнишь, мы туда собирались?", me.handle) else t(lines[random.nextInt(lines.size)])
        val c = Comment(nextId++, fan, text, createdAt = clock())
        _state.update { st -> st.copy(posts = st.posts.map { if (it.id == post.id) it.copy(comments = it.comments + 1, commentList = it.commentList + c) else it }) }
        record(if (mention) ActivityKind.Mention else ActivityKind.Comment, fan, post.id, text)
        notify(Nudges.commented(fan, post, text))
    }

    // ==================== ПОИСК ====================

    /** Открыть поиск; query — сразу с запросом (нажали на #тег). */
    fun openSearch(query: String = "") {
        _state.update {
            it.copy(
                searchOpen = true, searchQuery = query, activityOpen = false,
                openPersonId = null, personSection = null, openGroupId = null, groupSettingsOpen = false,
                commentsPostId = null, commentsPrismId = null, openChatId = null,
                searchHistory = if (query.isNotBlank()) Search.remember(it.searchHistory, query) else it.searchHistory,
            )
        }
    }

    fun closeSearch() {
        _state.update { it.copy(searchOpen = false, searchQuery = "") }
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query.take(80)) }
        searchServer(query.trim().removePrefix("@"))
    }

    private var searchJob: Job? = null

    /** Люди на сервере: через 0,4 с после последней буквы (демо-серверу искать некого). */
    private fun searchServer(q: String) {
        searchJob?.cancel()
        if (q.length < 2 || net.demo != null) return
        searchJob = viewModelScope.launch {
            delay(400)
            runCatching { net.api.searchUsers(q) }
            peopleChanged()
        }
    }

    /** Справочник людей сменился — экранам перерисоваться. */
    private fun peopleChanged() {
        val v = Directory.version
        if (_state.value.peopleVersion != v) _state.update { it.copy(peopleVersion = v) }
    }

    /** Запрос «состоялся» (нажали на результат или «Найти») — в историю. */
    fun commitSearch(query: String = _state.value.searchQuery) {
        Telemetry.track(AppEvent.SearchUsed)
        _state.update { it.copy(searchHistory = Search.remember(it.searchHistory, query)) }
    }

    fun clearSearchHistory() {
        _state.update { it.copy(searchHistory = emptyList()) }
    }

    /** Нажали на «#тег» в тексте — поиск по тегу. */
    fun openTag(tag: String) {
        Telemetry.track(AppEvent.TagOpened)
        openSearch("#" + tag.removePrefix("#").lowercase())
    }

    /** Нажали на «@логин» — страница человека; незнакомый логин — поиск по нему. */
    fun openHandle(handle: String) {
        val h = handle.removePrefix("@").lowercase()
        val s = _state.value
        when {
            h == s.me.handle.lowercase() -> { _state.update { it.copy(commentsPostId = null, commentsPrismId = null) }; selectTab(Tab.Profile) }
            else -> {
                val person = Demo.people.firstOrNull { it.handle.lowercase() == h }
                if (person != null) {
                    _state.update { it.copy(commentsPostId = null, commentsPrismId = null) }
                    openPerson(person.id)
                } else openSearch("@$h")
            }
        }
    }

    /** Кого подсказать после «@» в поле ввода. */
    fun mentionCandidates(part: String): List<User> = _state.value.let { Search.mentionCandidates(part, it.searchablePeople, it.following) }

    // ==================== ЛЕНТА ====================

    /** «Нравится» у записи, где бы она ни была: в ленте или в группе. */
    fun likeAny(postId: Long) {
        val group = _state.value.groups.firstOrNull { g -> g.posts.any { it.id == postId } }
        if (group != null) toggleGroupPostLike(group.id, postId) else toggleLike(postId)
    }

    /** Запись — где бы она ни была: свои, в группах, лента с сервера, «Интересное». */
    private fun updatePost(postId: Long, change: (Post) -> Post) {
        // И в страницах ленты и «Интересного»: иначе перерисовка (скачалось фото) вернула бы старое
        feedPager.update(postId, change)
        explorePager.update(postId, change)
        _state.update { s ->
            fun List<Post>.upd() = map { if (it.id == postId) change(it) else it }
            s.copy(
                posts = s.posts.upd(),
                groups = s.groups.map { g -> g.copy(posts = g.posts.upd()) },
                olderFeed = s.olderFeed.upd(),
                explore = s.explore.upd(),
            )
        }
    }

    /** Работаем с настоящим сервером (не встроенным демо). */
    private val serverMode: Boolean get() = net.demo == null

    /** Запись на сервере: пришла оттуда или уже ушла туда (номер сервера). */
    private fun isServerPost(postId: Long): Boolean = serverMode && postId >= SERVER_ID_BASE

    /** Комментарий уже на сервере (у ещё не отправленного — номер телефона, он меньше). */
    private fun isServerComment(commentId: Long): Boolean = commentId >= SERVER_ID_BASE

    fun toggleLike(postId: Long) {
        updatePost(postId) { it.copy(liked = !it.liked, likes = it.likes + if (it.liked) -1 else 1) }
        _state.value.findPost(postId)?.let { p ->
            // Своя ещё не ушедшая запись — серверу пока нечего отмечать
            if (p.outboxKey.isEmpty()) runCatching { net.queueLike(postId, p.liked) }
        }
        refreshNetCounters()
        kickNet()
    }

    /** Новая запись: текст, фото и видео. Пустую (без текста и вложений) не публикуем. */
    fun publish(text: String, media: List<Attachment> = emptyList(), checkIn: CheckIn? = null, poll: PollDraft? = null, place: SharedPlace? = null) {
        Telemetry.track(AppEvent.PostPublished)
        val trimmed = text.trim()
        val built = poll?.build(nextId++)
        if (trimmed.isEmpty() && media.isEmpty() && checkIn == null && built == null) return
        if (!postLimiter.allow(clock())) return tooOften()
        // Отмечать на фото можно только друзей — остальные отметки отбрасываются
        val friendIds = _state.value.following
        val cleanMedia = media.map { a -> if (a.tags.isEmpty()) a else a.copy(tags = a.tags.filter { it.userId in friendIds }.distinctBy { it.userId }) }
        media.filter { it.isGif }.forEach(::rememberGif)
        // На сервер — через очередь: без сети запись видна сразу и уйдёт, когда сеть появится
        val draft = Post(nextId++, _state.value.me, trimmed, 0, media = cleanMedia.take(MAX_MEDIA), createdAt = clock(), checkIn = checkIn, poll = built, place = place)
        val key = runCatching { net.queuePost(draft) }.getOrDefault("")
        _state.update { s ->
            val next = s.copy(
                posts = listOf(draft.copy(outboxKey = key)) + s.posts,
                // Чек-ин сам засчитывается в общие цели «Вместе»
                together = if (checkIn != null) s.together.afterCheckIn(checkIn) else s.together,
                growth = if (checkIn != null) s.growth.logged(GrowthEvent(GrowthEventType.CheckIn, clock(), checkIn.activity)) else s.growth,
            )
            // Отклик: что изменилось от чек-ина — полка, цели, план, что откроется дальше
            if (checkIn == null) next else next.copy(echo = echoFor(checkIn, s, next))
        }
        refreshNetCounters()
        kickNet()
    }

    private fun echoFor(c: CheckIn, before: UiState, after: UiState): CheckInEcho {
        val kind = shelfKind(c.activity)
        val shelfAfter = after.shelf
        val rec = if (kind == "film" || kind == "book") after.recommendations(kind, 1, shelfAfter).firstOrNull() else null
        return checkInEcho(
            c, before.shelf, shelfAfter, before.together, after.together,
            if (c.activity == "run") after.runPlan else null,
            before.movedKm, after.movedKm, rec,
        ) { id -> Demo.person(id)?.name?.substringBefore(' ').orEmpty() }
    }

    fun dismissEcho() {
        _state.update { it.copy(echo = null) }
    }

    // ==================== ПОЛКА, ПАМЯТЬ, РЕКОМЕНДАЦИИ ====================

    fun openShelf(tab: ShelfTab = ShelfTab.Films) {
        openProfileSection(ProfileSection.Shelf)
        _state.update { it.copy(shelfTab = tab) }
    }

    fun selectShelfTab(tab: ShelfTab) {
        _state.update { it.copy(shelfTab = tab) }
    }

    /** «Хочу посмотреть» / «Хочу прочитать» — в планы на полке, без записи в ленте. */
    fun planOnShelf(kind: String, subject: String) {
        val activity = if (kind == "book") "book" else "movie"
        _state.update { s ->
            if (s.shelf.keys.contains(shelfKey(activity, subject))) s
            else s.copy(shelfMarks = s.shelfMarks + ShelfMark(activity, subject.trim(), 0, BookStatus.Want.id, clock()))
        }
    }

    /** Оценка на полке без записи: поставить звёзды уже отмеченному. */
    fun rateOnShelf(activity: String, subject: String, rating: Int) {
        val r = rating.coerceIn(1, 5)
        _state.update { s ->
            val status = if (shelfKind(activity) == "book") BookStatus.Done.id else ""
            s.copy(shelfMarks = s.shelfMarks + ShelfMark(activity, subject.trim(), r, status, clock()))
        }
    }

    /** «Не моё» — больше не советовать. */
    fun dismissRecommendation(key: String) {
        _state.update { it.copy(dismissedRecs = it.dismissedRecs + key) }
    }

    /** Любимое из знакомства: на полку с пятью звёздами — первые рекомендации сразу. */
    fun addFavorites(picks: List<CatalogTitle>) {
        if (picks.isEmpty()) return
        _state.update { s ->
            val now = clock()
            val marks = picks.filter { p -> s.shelfMarks.none { it.key == shelfKey(if (p.kind == "book") "book" else "movie", p.title) } }.map {
                ShelfMark(if (it.kind == "book") "book" else "movie", it.title, 5, if (it.kind == "book") BookStatus.Done.id else "", now)
            }
            s.copy(shelfMarks = s.shelfMarks + marks, growth = s.growth.logged(GrowthEvent(GrowthEventType.OnboardingDone, now, picks.size.toString())))
        }
    }

    /** «В этот день» закрыли — до завтра не показываем. */
    fun dismissMemory() {
        _state.update { it.copy(memorySeen = LocalDate.now().toString()) }
    }

    // ==================== ПРИГЛАШЕНИЯ И ЖУРНАЛ РОСТА ====================

    /** Приглашение к действию: текст со ссылкой для «Поделиться»; в журнал — что отправили. */
    fun invite(action: InviteAction, targetId: String = "", title: String = ""): String {
        val s = _state.value
        // Демо-аккаунт зовут «Гость» — от его имени не подписываемся
        val from = if (s.me.handle == ru.wishka.memory.data.auth.LocalAuth.DEMO_LOGIN) "" else s.me.name.substringBefore(' ')
        val invite = Invite(s.inviteCode, action, targetId, from)
        log(GrowthEventType.InviteSent, action.id + if (targetId.isNotEmpty()) ":$targetId" else "")
        return Invites.message(invite, title)
    }

    fun logCardShared(what: String) = log(GrowthEventType.CardShared, what)

    fun logFeedback(idea: Boolean) = log(GrowthEventType.Feedback, if (idea) "idea" else "bug")

    private fun log(type: GrowthEventType, detail: String) {
        _state.update { it.copy(growth = it.growth.logged(GrowthEvent(type, clock(), detail))) }
    }

    /**
     * Открыли приложение по ссылке-приглашению. Своя ссылка — просто
     * открываем, к чему она; чужая — запоминаем, кто позвал (если это
     * новый человек без записей — он пришёл по приглашению).
     */
    fun openInvite(invite: Invite) {
        val s = _state.value
        val own = invite.code == s.inviteCode
        val now = clock()
        _state.update {
            var growth = it.growth.logged(GrowthEvent(GrowthEventType.InviteOpened, now, invite.code))
            var invitedBy = it.invitedBy
            if (!own && invitedBy == null && it.myPosts.isEmpty()) {
                invitedBy = invite
                growth = growth.logged(GrowthEvent(GrowthEventType.JoinedByInvite, now, invite.code))
            }
            it.copy(growth = growth, invitedBy = invitedBy, openedInvite = if (own) null else invite)
        }
        routeInvite(invite)
    }

    fun dismissInvite() {
        _state.update { it.copy(openedInvite = null) }
    }

    /** К чему зовёт приглашение — туда и ведём. */
    private fun routeInvite(invite: Invite) {
        val section = when (invite.action) {
            InviteAction.Drawing -> TogetherSection.Drawing
            InviteAction.Book -> TogetherSection.Sharing
            InviteAction.Outing -> TogetherSection.Outings
            InviteAction.Lesson -> TogetherSection.Skills
            InviteAction.Journey -> TogetherSection.Journeys
            InviteAction.Cache -> TogetherSection.Caches
            InviteAction.Shelf -> { openShelf(ShelfTab.ForYou); return }
            InviteAction.App -> return
        }
        selectTab(Tab.Together)
        selectTogetherSection(section)
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
        Telemetry.track(AppEvent.CommentSent)
        val trimmed = text.trim().take(1000)
        if (trimmed.isEmpty() && sticker == null) return
        val target = _state.value.findPost(postId)
        if (target != null && target.commentsOff && target.author.id != _state.value.me.id) {
            _state.update { it.copy(notice = t("Автор выключил комментарии")) }
            return
        }
        if (!commentLimiter.allow(clock())) return tooOften()
        val comment = Comment(nextId++, _state.value.me, trimmed, createdAt = clock(), replyTo = replyTo, sticker = sticker)
        updatePost(postId) { it.copy(comments = it.comments + 1, commentList = it.commentList + comment) }
        if (isServerPost(postId)) sendComment(postId, comment)
    }

    /** Комментарий — на сервер; ответ — тот же комментарий с номером сервера. Не ушёл — убрать. */
    private fun sendComment(postId: Long, comment: Comment) {
        viewModelScope.launch {
            val saved = try { net.api.addComment(postId, comment, net.newKey()) } catch (e: NetError) {
                updatePost(postId) { p -> p.copy(comments = (p.comments - 1).coerceAtLeast(0), commentList = p.commentList.filterNot { it.id == comment.id }) }
                _state.update { it.copy(notice = t("Комментарий не отправлен — проверьте сеть")) }
                return@launch
            }
            updatePost(postId) { p -> p.copy(commentList = p.commentList.map { if (it.id == comment.id) saved else it }) }
        }
    }

    /** Комментарии записи с сервера — при открытии обсуждения. */
    private fun loadComments(postId: Long) {
        if (!isServerPost(postId)) return
        viewModelScope.launch {
            val list = runCatching { net.api.comments(postId) }.getOrNull() ?: return@launch
            // Свои, что ещё отправляются (номер телефона), — остаются в конце
            updatePost(postId) { p ->
                val pending = p.commentList.filter { !isServerComment(it.id) && it.author.id == _state.value.me.id }
                p.copy(commentList = list + pending, comments = list.size + pending.size)
            }
            peopleChanged()
        }
    }

    /** Свой комментарий можно удалить. */
    fun deleteComment(postId: Long, commentId: Long) {
        val me = _state.value.me.id
        val post = _state.value.findPost(postId) ?: return
        // Свой — всегда; под своей записью — любой
        if (post.commentList.none { it.id == commentId && (it.author.id == me || post.author.id == me) }) return
        updatePost(postId) { p -> p.copy(comments = (p.comments - 1).coerceAtLeast(0), commentList = p.commentList.filterNot { it.id == commentId }) }
        if (isServerPost(postId) && isServerComment(commentId)) runCatching { net.queueCall("DELETE", "/v1/comments/$commentId") }
        kickNet()
    }

    /** «Нравится» комментарию к записи — в ленте или в группе. */
    fun toggleCommentLike(postId: Long, commentId: Long) {
        updatePost(postId) { p -> p.copy(commentList = p.commentList.map { it.flipLike(commentId) }) }
        if (!isServerPost(postId) || !isServerComment(commentId)) return
        val liked = _state.value.findPost(postId)?.commentList?.firstOrNull { it.id == commentId }?.liked ?: return
        runCatching { net.queueCall(if (liked) "PUT" else "DELETE", "/v1/comments/$commentId/like", coalesce = "clike:$commentId") }
        kickNet()
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
        loadComments(postId)
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
     * Дружба взаимная. Уже друзья — удалить из друзей (интерфейс сначала
     * спрашивает «Удалить из друзей?»); заявка ждёт ответа — отозвать её;
     * человек сам прислал заявку — сразу друзья; иначе уходит заявка.
     * В демо её принимает любой человек через несколько секунд.
     */
    fun toggleFriend(userId: Int) {
        val s0 = _state.value
        if (userId == s0.me.id || userId in s0.blocked) return
        if (isServerPerson(userId)) return toggleServerFriend(userId)
        when (userId) {
            in s0.following -> removeFriend(userId)
            in s0.sentRequests -> {
                acceptJobs.remove(userId)?.cancel()
                _state.update { it.copy(sentRequests = it.sentRequests - userId) }
            }
            in s0.followRequests -> approveRequest(userId)
            else -> {
                _state.update { it.copy(sentRequests = it.sentRequests + userId) }
                scheduleAcceptance(userId)
            }
        }
    }

    /** Удалить из друзей: записи закрытого профиля и «Истории» человека уходят. */
    fun removeFriend(userId: Int) {
        declined += userId
        _state.update { it.copy(following = it.following - userId, closeFriends = it.closeFriends - userId) }
        if (isServerPerson(userId)) {
            // Дружба взаимная: удалить — и отписаться, и убрать его из подписчиков (мои записи и истории ему больше не видны)
            runCatching {
                net.queueCall("DELETE", "/v1/users/$userId/follow", coalesce = "follow:$userId")
                net.queueCall("DELETE", "/v1/me/followers/$userId", coalesce = "follower:$userId")
            }
            pushCloseFriends()
            kickNet()
        }
    }

    /** «Близкие друзья» — на сервер целиком (только люди с сервера). */
    private fun pushCloseFriends() {
        if (!serverMode) return
        val ids = _state.value.closeFriends.filter { isServerPerson(it) }
        runCatching { net.queueCall("PUT", "/v1/me/close-friends", org.json.JSONObject().put("ids", org.json.JSONArray(ids)), coalesce = "close-friends") }
    }

    /**
     * Подписка на человека с сервера. Дружба в Memory взаимная, поэтому
     * «Принять» заявку — это и принять, и подписаться в ответ.
     */
    private fun toggleServerFriend(userId: Int) {
        val s0 = _state.value
        when (userId) {
            in s0.following -> removeFriend(userId)
            in s0.sentRequests -> {
                _state.update { it.copy(sentRequests = it.sentRequests - userId) }
                runCatching { net.queueCall("DELETE", "/v1/users/$userId/follow", coalesce = "follow:$userId") }
            }
            in s0.followRequests -> approveRequest(userId)
            else -> {
                _state.update { it.copy(sentRequests = it.sentRequests + userId) }
                followOnServer(userId)
            }
        }
        kickNet()
    }

    /** Подписаться: открытый профиль — сразу друзья, закрытый — заявка ждёт ответа. */
    private fun followOnServer(userId: Int) {
        viewModelScope.launch {
            val state = try { net.api.follow(userId) } catch (e: NetError) {
                // Нет сети — уйдёт из очереди; ответ узнаем при следующей сверке
                runCatching { net.queueCall("PUT", "/v1/users/$userId/follow", coalesce = "follow:$userId") }
                return@launch
            }
            if (state == "following") _state.update {
                it.copy(sentRequests = it.sentRequests - userId, following = it.following + userId)
            }
            refreshFeedNow()
        }
    }

    /**
     * Подписки с сервера: на кого подписан, чьи заявки ждут, мои заявки.
     * Демо-друзья остаются как есть.
     */
    suspend fun syncFollows() {
        val following = net.api.following().map { it.id }.toSet()
        val sent = net.api.sentRequests().map { it.id }.toSet()
        val incoming = net.api.followRequests().map { it.id }
        val blockedOnServer = runCatching { net.api.blocked() }.getOrDefault(emptyList()).map(Directory::local).toSet()
        _state.update { s ->
            fun Set<Int>.local() = filter { !isServerPerson(it) }.toSet()
            s.copy(
                following = s.following.local() + following,
                sentRequests = s.sentRequests.local() + sent,
                followRequests = s.followRequests.filter { !isServerPerson(it) } + incoming.filter { it !in s.blocked },
                // С сервера — целиком (разблокировали на другом телефоне — и здесь); демо-люди — как были
                blocked = s.blocked.filter { !isServerPerson(it) }.toSet() + blockedOnServer,
            )
        }
        peopleChanged()
    }

    /** Таймеры демо-ответа на заявки: один на человека. */
    private val acceptJobs = mutableMapOf<Int, Job>()

    /** В демо любой человек принимает заявку в друзья через 4–15 секунд. */
    private fun scheduleAcceptance(userId: Int) {
        if (!demoReplies) return
        acceptJobs.remove(userId)?.cancel()
        acceptJobs[userId] = viewModelScope.launch {
            delay(Random.nextLong(4_000, 15_000))
            acceptJobs.remove(userId)
            acceptSentRequest(userId)
        }
    }

    /** Человек принял вашу заявку: вы друзья, записи видны, приходит уведомление. */
    fun acceptSentRequest(userId: Int) {
        if (userId !in _state.value.sentRequests) return
        _state.update {
            it.copy(sentRequests = it.sentRequests - userId, followRequests = it.followRequests - userId, following = it.following + userId)
        }
        Demo.person(userId)?.let {
            record(ActivityKind.FriendAccepted, it)
            notify(Nudges.requestAccepted(it))
        }
    }

    /** Принять заявку в друзья: человек становится другом. */
    fun approveRequest(userId: Int) {
        acceptJobs.remove(userId)?.cancel()
        _state.update {
            it.copy(followRequests = it.followRequests - userId, sentRequests = it.sentRequests - userId, following = it.following + userId)
        }
        if (isServerPerson(userId)) {
            runCatching {
                net.queueCall("POST", "/v1/me/requests/$userId/accept")
                net.queueCall("PUT", "/v1/users/$userId/follow", coalesce = "follow:$userId")
            }
            kickNet()
        }
    }

    /** Отклонить заявку: человек не узнает об этом, заявка просто исчезает. */
    fun declineRequest(userId: Int) {
        declined += userId
        _state.update { it.copy(followRequests = it.followRequests - userId) }
        if (isServerPerson(userId)) {
            runCatching { net.queueCall("DELETE", "/v1/me/requests/$userId") }
            kickNet()
        }
    }

    /** Кого отклонили или удалили из друзей: в демо они больше не присылают заявок сами. */
    private val declined = mutableSetOf<Int>()

    /** Новая заявка в друзья ко мне — от любого, кто не в чёрном списке и ещё не друг. */
    fun receiveFriendRequest(userId: Int) {
        val s = _state.value
        val person = Demo.person(userId) ?: return
        if (userId == s.me.id || userId in s.followRequests || userId in s.blocked ||
            userId in s.following || userId in s.sentRequests
        ) return
        _state.update { it.copy(followRequests = listOf(userId) + it.followRequests) }
        record(ActivityKind.FriendRequest, person)
        notify(Nudges.followRequest(person, person.interests.filter { it in s.me.interests }))
    }

    // ==================== ЧАТЫ ====================

    fun openChat(id: Int) {
        _state.update { s ->
            s.copy(
                openProduct = null,
                tab = Tab.Chats,
                openChatId = id,
                // Страница человека остаётся под перепиской: «назад» вернёт на неё
                returnToChatId = null,
                chats = s.chats.map { if (it.id == id) it.copy(unread = 0) else it },
            )
        }
        sendRead(id)
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
        if (isServerPerson(userId)) {
            // Человек с сервера: номер переписки выдаёт сервер (у пары она одна)
            viewModelScope.launch {
                val id = try { net.api.directChat(userId) } catch (e: NetError) {
                    _state.update { it.copy(notice = t("Не получилось — проверьте сеть")) }
                    return@launch
                }
                if (_state.value.chats.none { it.id == id }) _state.update { s -> s.copy(chats = listOf(Chat(id, person, emptyList())) + s.chats) }
                openChat(id)
            }
            return
        }
        val id = (_state.value.chats.maxOfOrNull { it.id } ?: 0) + 1
        _state.update { s -> s.copy(chats = listOf(Chat(id, person, emptyList())) + s.chats) }
        openChat(id)
    }

    /** Человек с настоящего сервера (а не из демо). */
    private fun isServerPerson(userId: Int): Boolean = Directory.get(userId) != null && Demo.people.none { it.id == userId }

    /**
     * Отправка. В демо собеседник «печатает» и через пару секунд отвечает —
     * чтобы переписку можно было почувствовать без сервера.
     */
    fun send(chatId: Int, text: String, attachments: List<Attachment> = emptyList(), replyTo: ReplyRef? = null, poll: PollDraft? = null, place: SharedPlace? = null) {
        Telemetry.track(AppEvent.MessageSent)
        val trimmed = text.trim()
        val built = poll?.build(nextId++)
        if (trimmed.isEmpty() && attachments.isEmpty() && built == null && place == null) return
        if (!messageLimiter.allow(clock())) return tooOften()
        // Секретная переписка: на сервер ушёл бы только конверт; демо-собеседник его расшифровывает своим ключом
        val secretChat = _state.value.chats.firstOrNull { it.id == chatId }?.takeIf { it.secret }
        if (secretChat != null && trimmed.isNotEmpty()) {
            val envelope = sealFor(secretChat, trimmed)
            lastEnvelope = envelope
            if (envelope == null || peerOpen(secretChat, envelope) != trimmed) return
        }
        val outgoing = Message(nextId++, fromMe = true, text = trimmed, time = now(), attachments = attachments.take(MAX_MEDIA), replyTo = replyTo, poll = built, place = place, pending = true)
        append(chatId, outgoing)
        // На сервер — через очередь. Секретная переписка: уходит только конверт, без вложений
        // (шифрование файлов — следующий шаг), так что сервер ничего не прочтёт
        runCatching {
            // Секретная переписка: файлы шифруются на телефоне своим ключом (FileCrypto),
            // а ключ едет в имени вложения, запечатанный ключом переписки
            val keys = HashMap<String, String>()
            val wireAttachments = if (secretChat == null) outgoing.attachments else outgoing.attachments.mapNotNull { a ->
                val key = ru.wishka.memory.data.FileKey.random().encoded()
                val sealed = sealFor(secretChat, key) ?: return@mapNotNull null
                keys[a.id] = key
                a.copy(name = ru.wishka.memory.data.FileCrypto.SEALED + sealed, mime = "", tags = emptyList())
            }
            lastFileKeys = keys
            val wire = if (secretChat != null) outgoing.copy(text = if (trimmed.isNotEmpty()) lastEnvelope.orEmpty() else "", attachments = wireAttachments) else outgoing
            if (wire.text.isNotEmpty() || wire.attachments.isNotEmpty() || wire.poll != null || wire.place != null) net.queueMessage(chatId, wire, keys)
            else updateMessage(chatId, outgoing.id) { it.copy(pending = false) }
        }
        refreshNetCounters()
        kickNet()
        attachments.filter { it.isGif }.forEach(::rememberGif)
        // Опрос в переписке: собеседник отвечает голосом через пару секунд
        if (built != null) later(4_000, 12_000) {
            val pick = Random.nextInt(built.options.size)
            updateMessagePoll(chatId, built.id) { it.addVotes(mapOf(pick to 1)) }
        }
        if (!demoReplies) return
        viewModelScope.launch {
            delay(700)
            setTyping(chatId, true)
            delay(1500)
            setTyping(chatId, false)
            val reply = Demo.replies[replyIndex++ % Demo.replies.size]
            append(chatId, Message(nextId++, fromMe = false, text = reply, time = now()))
            _state.value.chats.firstOrNull { it.id == chatId }?.let { chat ->
                // Открытая переписка — ответ и так на экране; у секретной текст в уведомление не попадает
                if (!chat.muted && _state.value.openChatId != chatId) notify(Nudges.message(chat.peer, if (chat.secret) t("Новое сообщение") else reply, chatId))
            }
        }
    }

    // ==================== ИСТОРИИ ====================

    fun openStoryComposer() {
        _state.update { it.copy(storyComposer = true) }
    }

    fun closeStoryComposer() {
        _state.update { it.copy(storyComposer = false) }
    }

    /**
     * Новая история: фото или видео либо текст на градиенте. closeFriends —
     * только для «Близких друзей». Ответ — текст ошибки или null.
     */
    fun publishStory(media: Attachment?, text: String, bg: Int, closeFriends: Boolean): String? {
        val clean = text.trim().take(Stories.MAX_TEXT)
        if (media == null && clean.isEmpty()) return t("Добавьте фото, видео или текст")
        Telemetry.track(AppEvent.StoryPublished)
        val story = Story(nextId++, _state.value.me.id, clock(), media, clean, bg.coerceIn(0, Stories.BACKGROUNDS.lastIndex), closeFriends = closeFriends)
        _state.update { it.copy(stories = it.stories + story, storyComposer = false) }
        if (serverMode) {
            runCatching { net.queueStory(story) }
            kickNet()
        }
        return null
    }

    /** Открыть истории человека; дальше — по кругу остальных с историями. */
    fun openStories(authorId: Int) {
        val s = _state.value
        val now = clock()
        val circles = s.storyCircles(now)
        val order = (if (s.myActiveStories(now).isNotEmpty()) listOf(s.me.id) else emptyList()) + circles.map { it.user.id }
        if (authorId !in order) return
        val list = storiesOf(authorId)
        _state.update { it.copy(storyViewer = StoryViewer(order, authorId, Stories.startIndex(list, it.seenStories))) }
        currentStory()?.let { seeStory(it.id) }
    }

    /** Открыть подборку «Актуального». */
    fun openHighlight(highlightId: Long) {
        val h = _state.value.highlights.firstOrNull { it.id == highlightId } ?: return
        if (h.storyIds.none { _state.value.story(it) != null }) return
        _state.update { it.copy(storyViewer = StoryViewer(listOf(h.ownerId), h.ownerId, 0, highlightId)) }
    }

    fun closeStories() {
        _state.update { it.copy(storyViewer = null, storyViewersOf = null) }
    }

    /** Истории, которые сейчас листаются у этого автора. */
    fun storiesOf(authorId: Int): List<Story> {
        val s = _state.value
        val v = s.storyViewer
        if (v?.highlightId != null) {
            val h = s.highlights.firstOrNull { it.id == v.highlightId } ?: return emptyList()
            return h.storyIds.mapNotNull { s.story(it) }
        }
        val now = clock()
        return if (authorId == s.me.id) s.myActiveStories(now)
        else s.storyCircles(now).firstOrNull { it.user.id == authorId }?.stories.orEmpty()
    }

    fun currentStory(): Story? {
        val v = _state.value.storyViewer ?: return null
        return storiesOf(v.author).getOrNull(v.index)
    }

    /** Следующая история; кончились у автора — к следующему автору; у всех — закрыть. */
    fun nextStory() {
        val v = _state.value.storyViewer ?: return
        val list = storiesOf(v.author)
        if (v.index + 1 < list.size) {
            _state.update { it.copy(storyViewer = v.copy(index = v.index + 1)) }
        } else {
            val next = v.authorIds.getOrNull(v.authorIds.indexOf(v.author) + 1)
            if (next == null || v.highlightId != null) { closeStories(); return }
            val nextList = storiesOf(next)
            _state.update { it.copy(storyViewer = v.copy(author = next, index = Stories.startIndex(nextList, it.seenStories))) }
        }
        currentStory()?.let { seeStory(it.id) }
    }

    fun previousStory() {
        val v = _state.value.storyViewer ?: return
        if (v.index > 0) {
            _state.update { it.copy(storyViewer = v.copy(index = v.index - 1)) }
        } else {
            val prev = v.authorIds.getOrNull(v.authorIds.indexOf(v.author) - 1)
            if (prev != null && v.highlightId == null) _state.update { it.copy(storyViewer = v.copy(author = prev, index = 0)) }
        }
    }

    /** История показана: отметить просмотренной (и, если чужая, — что я её видел). */
    fun seeStory(storyId: Long) {
        val me = _state.value.me.id
        val first = storyId !in _state.value.seenStories
        _state.value.story(storyId)?.let { st ->
            // История с сервера — отметить там (автор увидит в «Кто смотрел»)
            if (first && st.authorId != me && isServerPerson(st.authorId)) {
                runCatching { net.queueCall("POST", "/v1/stories/$storyId/view", org.json.JSONObject().put("liked", false), coalesce = "view:$storyId") }
                kickNet()
            }
        }
        _state.update { s ->
            s.copy(
                seenStories = s.seenStories + storyId,
                stories = s.stories.map { if (it.id == storyId && it.authorId != me) Stories.view(it, me, clock()) else it },
            )
        }
    }

    /** Сердечко на чужую историю: автору — «❤️» в переписку с цитатой истории. */
    fun likeStory(storyId: Long) {
        val story = _state.value.story(storyId) ?: return
        val me = _state.value.me.id
        if (story.authorId == me || story.views.any { it.userId == me && it.liked }) return
        _state.update { s -> s.copy(stories = s.stories.map { if (it.id == storyId) Stories.view(it, me, clock(), liked = true) else it }) }
        if (isServerPerson(story.authorId)) {
            runCatching { net.queueCall("POST", "/v1/stories/$storyId/view", org.json.JSONObject().put("liked", true), coalesce = "view:$storyId") }
        }
        replyToStory(storyId, "❤️")
    }

    /** Ответ на историю — сообщением автору, с цитатой истории над ним. */
    fun replyToStory(storyId: Long, text: String) {
        val story = _state.value.story(storyId) ?: return
        val clean = text.trim().take(1000)
        if (clean.isEmpty() || story.authorId == _state.value.me.id) return
        val chatId = chatWith(story.authorId) ?: return
        append(chatId, Message(nextId++, fromMe = true, text = clean, time = now(), story = Stories.ref(story)))
    }

    /** Переписка с человеком: есть — её номер, нет — создать (не открывая). */
    private fun chatWith(userId: Int): Int? {
        val person = Demo.person(userId) ?: return null
        _state.value.chats.firstOrNull { it.peer.id == userId }?.let { return it.id }
        val id = (_state.value.chats.maxOfOrNull { it.id } ?: 0) + 1
        _state.update { s -> s.copy(chats = listOf(Chat(id, person, emptyList())) + s.chats) }
        return id
    }

    /** Удалить свою историю — и из «Актуального». Файл удаляет экран. */
    fun deleteStory(storyId: Long) {
        val me = _state.value.me.id
        if (storyId in serverStoryIds) {
            runCatching { net.queueCall("DELETE", "/v1/stories/$storyId") }
            kickNet()
        }
        _state.update { s ->
            s.copy(
                stories = s.stories.filterNot { it.id == storyId && it.authorId == me },
                highlights = s.highlights.map { h -> if (h.ownerId == me) h.copy(storyIds = h.storyIds - storyId, coverId = if (h.coverId == storyId) (h.storyIds - storyId).firstOrNull() ?: 0 else h.coverId) else h }
                    .filter { it.ownerId != me || it.storyIds.isNotEmpty() },
            )
        }
        val v = _state.value.storyViewer ?: return
        if (storiesOf(v.author).isEmpty()) closeStories()
        else if (v.index >= storiesOf(v.author).size) _state.update { it.copy(storyViewer = v.copy(index = storiesOf(v.author).lastIndex)) }
    }

    fun openStoryViewers(storyId: Long) {
        _state.update { it.copy(storyViewersOf = storyId) }
    }

    fun closeStoryViewers() {
        _state.update { it.copy(storyViewersOf = null) }
    }

    /** Добавить друга в «Близкие друзья» или убрать. */
    fun toggleCloseFriend(userId: Int) {
        if (userId !in _state.value.following) return
        _state.update { s -> s.copy(closeFriends = if (userId in s.closeFriends) s.closeFriends - userId else s.closeFriends + userId) }
        pushCloseFriends()
        kickNet()
    }

    /** Мои истории, которые уже на сервере (номер сервера). */
    private val serverStoryIds = HashSet<Long>()

    /**
     * Истории с сервера: чужие заменяют прежние чужие с сервера; у своих —
     * свежий список «Кто смотрел». Фото и видео чужих — качаются.
     */
    suspend fun syncStories() {
        val list = net.api.stories()
        val me = _state.value.me.id
        val mine = list.filter { it.first.authorId == me }.associate { it.first.id to it.first }
        serverStoryIds += mine.keys
        val others = list.filter { it.first.authorId != me && it.first.authorId !in _state.value.blocked }
        val seen = others.filter { it.second }.map { it.first.id }
        _state.update { s ->
            val kept = s.stories.filter { it.authorId == me || !isServerPerson(it.authorId) }
                .map { st -> mine[st.id]?.let { srv -> st.copy(views = srv.views) } ?: st }
            s.copy(stories = kept + others.map { it.first }, seenStories = s.seenStories + seen)
        }
        peopleChanged()
        for ((st, _) in others) {
            val a = st.media ?: continue
            if (!net.isRemote(a.path)) continue
            val f = runCatching { net.fetchMedia(a.path, ext = mediaExt(a)) }.getOrNull() ?: continue
            _state.update { s -> s.copy(stories = s.stories.map { if (it.id == st.id) it.copy(media = a.copy(path = f.path)) else it }) }
        }
    }

    /** Новая подборка «Актуального» из своих историй. Ответ — её номер или null. */
    fun createHighlight(title: String, storyIds: List<Long>): Long? {
        val s = _state.value
        val mine = storyIds.filter { id -> s.stories.any { it.id == id && it.authorId == s.me.id } }.distinct()
        if (mine.isEmpty()) return null
        val h = Highlight(nextId++, s.me.id, title.trim().take(Stories.MAX_TITLE).ifEmpty { t("Актуальное") }, mine)
        _state.update { it.copy(highlights = it.highlights + h) }
        return h.id
    }

    fun renameHighlight(id: Long, title: String) = updateHighlight(id) { it.copy(title = title.trim().take(Stories.MAX_TITLE).ifEmpty { it.title }) }

    fun addToHighlight(id: Long, storyId: Long) = updateHighlight(id) { if (storyId in it.storyIds) it else it.copy(storyIds = it.storyIds + storyId) }

    fun removeFromHighlight(id: Long, storyId: Long) = updateHighlight(id) { it.copy(storyIds = it.storyIds - storyId, coverId = if (it.coverId == storyId) (it.storyIds - storyId).firstOrNull() ?: 0 else it.coverId) }

    fun setHighlightCover(id: Long, storyId: Long) = updateHighlight(id) { if (storyId in it.storyIds) it.copy(coverId = storyId) else it }

    fun deleteHighlight(id: Long) {
        val me = _state.value.me.id
        _state.update { s -> s.copy(highlights = s.highlights.filterNot { it.id == id && it.ownerId == me }) }
    }

    private fun updateHighlight(id: Long, change: (Highlight) -> Highlight) {
        val me = _state.value.me.id
        _state.update { s ->
            s.copy(highlights = s.highlights.map { if (it.id == id && it.ownerId == me) change(it) else it }.filter { it.ownerId != me || it.storyIds.isNotEmpty() })
        }
    }

    /**
     * Демо-истории друзей: у кого нет свежей — появляется; у друзей без
     * «Актуального» — пара подборок из старых историй. Вызывается после входа.
     */
    fun ensureDemoStories(random: Random = Random.Default) {
        val s = _state.value
        val now = clock()
        val added = mutableListOf<Story>()
        val hs = mutableListOf<Highlight>()
        for (f in Demo.people) {
            if (s.stories.none { it.authorId == f.id && it.active(now) } && random.nextInt(3) != 0) {
                repeat(1 + random.nextInt(3)) { i ->
                    added += demoStory(f.id, now - random.nextLong(10 * 60_000, 20 * 60 * 60_000L) + i, random, close = random.nextInt(5) == 0)
                }
            }
            if (s.highlights.none { it.ownerId == f.id }) {
                listOf(t("Путешествия"), t("Друзья"), t("Еда")).shuffled(random).take(1 + random.nextInt(2)).forEach { title ->
                    val old = List(2 + random.nextInt(3)) { demoStory(f.id, now - random.nextLong(3L, 60L) * Stories.TTL_MS, random) }
                    added += old
                    hs += Highlight(nextId++, f.id, title, old.map { it.id })
                }
            }
        }
        if (added.isEmpty() && hs.isEmpty()) return
        _state.update { it.copy(stories = it.stories + added, highlights = it.highlights + hs) }
    }

    private fun demoStory(authorId: Int, at: Long, random: Random, close: Boolean = false): Story =
        Story(
            nextId++, authorId, at,
            text = if (random.nextInt(3) == 0) t(Stories.demoTexts[random.nextInt(Stories.demoTexts.size)]) else "",
            bg = random.nextInt(Stories.BACKGROUNDS.size), seed = random.nextInt(1000), closeFriends = close,
        )

    /**
     * Демо-жизнь историй: друг выкладывает новую, мою историю смотрят
     * (близкие — «для близких»), иногда отвечают на неё в личку.
     */
    fun storyEvent(friend: User, random: Random) {
        val s = _state.value
        val now = clock()
        val mine = s.myActiveStories(now)
        when {
            mine.isNotEmpty() && random.nextInt(3) != 0 -> {
                val story = mine[random.nextInt(mine.size)]
                if (story.closeFriends && friend.id !in s.closeFriends) return
                val liked = random.nextInt(4) == 0
                _state.update { st -> st.copy(stories = st.stories.map { if (it.id == story.id) Stories.view(it, friend.id, now, liked) else it }) }
                if (random.nextInt(5) == 0) {
                    val text = t(listOf("Красота!", "Где это?", "Хочу туда же", "😍")[random.nextInt(4)])
                    val chatId = chatWith(friend.id) ?: return
                    append(chatId, Message(nextId++, fromMe = false, text = text, time = now(), story = Stories.ref(story)))
                    record(ActivityKind.StoryReply, friend, story.id, text)
                    if (_state.value.openChatId != chatId) notify(Nudges.message(friend, text, chatId))
                }
            }
            s.stories.none { it.authorId == friend.id && it.active(now) } ->
                _state.update { it.copy(stories = it.stories + demoStory(friend.id, now, random, close = random.nextInt(5) == 0)) }
        }
        // Чужие истории старше суток, не попавшие в «Актуальное», больше не нужны
        pruneStories(now)
    }

    /** Убрать чужие истории, которые истекли и не лежат в «Актуальном». */
    fun pruneStories(now: Long = clock()) {
        val me = _state.value.me.id
        _state.update { s ->
            val kept = s.highlights.flatMap { it.storyIds }.toSet()
            val stories = s.stories.filter { it.authorId == me || it.active(now) || it.id in kept }
            if (stories.size == s.stories.size) s else s.copy(stories = stories, seenStories = s.seenStories intersect stories.map { it.id }.toSet())
        }
    }

    // ==================== ГРУППЫ ====================

    /**
     * Новая группа: название и друзья. Номер выбирает телефон (случайный,
     * сервер примет или ответит 409) — так группа живёт и без сети.
     * Ответ — номер переписки или текст ошибки (Left/Right не нужен — две функции).
     */
    fun createGroup(title: String, memberIds: List<Int>): Int? {
        val s = _state.value
        val clean = title.trim().take(ChatGroup.MAX_TITLE)
        val ids = memberIds.filter { it in s.following && it !in s.blocked && it != s.me.id }.distinct()
        if (clean.isEmpty() || ids.isEmpty()) return null
        if (ids.size + 1 > ChatGroup.MAX_MEMBERS) return null
        var id: Int
        do { id = 1_000_000 + Random.nextInt(1_000_000_000) } while (s.chats.any { it.id == id })
        val g = ChatGroup(clean, listOf(s.me.id) + ids, setOf(s.me.id), s.me.id, hue = Random.nextFloat())
        val first = systemMessage(tf("Вы создали группу «%s»", clean))
        _state.update { it.copy(chats = listOf(Chat(id, g.asUser(id), listOf(first), group = g)) + it.chats) }
        runCatching {
            net.queueCall("POST", "/v1/chats", org.json.JSONObject().put("id", id).put("title", clean).put("members", org.json.JSONArray(g.memberIds)))
        }
        kickNet()
        return id
    }

    /** Служебная строка в переписке: «Вера добавлена», «Вы создали группу». */
    private fun systemMessage(text: String) = Message(nextId++, fromMe = false, text = text, time = now(), authorId = SYSTEM_AUTHOR, delivered = true)

    private fun updateGroup(chatId: Int, change: (ChatGroup) -> ChatGroup) {
        _state.update { s ->
            s.copy(chats = s.chats.map { c -> val g = c.group; if (c.id != chatId || g == null) c else { val ng = change(g); c.copy(group = ng, peer = ng.asUser(c.id)) } })
        }
    }

    private fun isGroupAdmin(chatId: Int): Boolean {
        val s = _state.value
        return s.chats.firstOrNull { it.id == chatId }?.group?.isAdmin(s.me.id) == true
    }

    fun renameGroup(chatId: Int, title: String) {
        val clean = title.trim().take(ChatGroup.MAX_TITLE)
        if (clean.isEmpty() || !isGroupAdmin(chatId)) return
        updateGroup(chatId) { it.copy(title = clean) }
        append(chatId, systemMessage(tf("Новое название: «%s»", clean)))
        runCatching { net.queueCall("PATCH", "/v1/chats/$chatId", org.json.JSONObject().put("title", clean), coalesce = "title:$chatId") }
        kickNet()
    }

    fun addGroupMembers(chatId: Int, userIds: List<Int>) {
        if (!isGroupAdmin(chatId)) return
        val s = _state.value
        val g = s.chats.firstOrNull { it.id == chatId }?.group ?: return
        val add = userIds.filter { it !in g.memberIds && it in s.following && it !in s.blocked }.take(ChatGroup.MAX_MEMBERS - g.memberIds.size)
        if (add.isEmpty()) return
        updateGroup(chatId) { it.copy(memberIds = it.memberIds + add) }
        add.forEach { uid ->
            Demo.person(uid)?.let { p -> append(chatId, systemMessage(tf("%s теперь в группе", p.name.substringBefore(' ')))) }
            runCatching { net.queueCall("PUT", "/v1/chats/$chatId/members/$uid") }
        }
        kickNet()
    }

    fun removeGroupMember(chatId: Int, userId: Int) {
        val g = _state.value.chats.firstOrNull { it.id == chatId }?.group ?: return
        if (!isGroupAdmin(chatId) || userId == g.ownerId || userId !in g.memberIds) return
        updateGroup(chatId) { it.copy(memberIds = it.memberIds - userId, adminIds = it.adminIds - userId) }
        Demo.person(userId)?.let { p -> append(chatId, systemMessage(tf("%s больше не в группе", p.name.substringBefore(' ')))) }
        runCatching { net.queueCall("DELETE", "/v1/chats/$chatId/members/$userId") }
        kickNet()
    }

    /** Сделать админом или снять — только создатель. */
    fun setGroupAdmin(chatId: Int, userId: Int, admin: Boolean) {
        val s = _state.value
        val g = s.chats.firstOrNull { it.id == chatId }?.group ?: return
        if (g.ownerId != s.me.id || userId == g.ownerId || userId !in g.memberIds) return
        updateGroup(chatId) { it.copy(adminIds = if (admin) it.adminIds + userId else it.adminIds - userId) }
        runCatching { net.queueCall(if (admin) "PUT" else "DELETE", "/v1/chats/$chatId/admins/$userId") }
        kickNet()
    }

    /** Выйти из группы: переписка уходит из списка. */
    fun leaveGroup(chatId: Int) {
        val s = _state.value
        s.chats.firstOrNull { it.id == chatId }?.group ?: return
        _state.update { st -> st.copy(chats = st.chats.filterNot { it.id == chatId }, openChatId = if (st.openChatId == chatId) null else st.openChatId) }
        runCatching { net.queueCall("DELETE", "/v1/chats/$chatId/members/${s.me.id}") }
        kickNet()
    }

    // ==================== ЖИВОЙ КАНАЛ ====================

    private val typingSentAt = HashMap<Int, Long>()

    /** Печатаю в переписке: собеседникам — «печатает…» (не чаще раза в 3 секунды). */
    fun typing(chatId: Int) {
        val now = clock()
        if (now - (typingSentAt[chatId] ?: 0) < 3_000) return
        typingSentAt[chatId] = now
        runCatching { net.realtime.send(RtEvent.Typing(chatId, _state.value.me.id, true)) }
    }

    /** Открыл переписку — «прочитано» до последнего пришедшего. */
    private fun sendRead(chatId: Int) {
        val chat = _state.value.chats.firstOrNull { it.id == chatId } ?: return
        // Запрос ещё не принят — человек не должен знать, что прочитали
        if (chat.request) return
        val upTo = chat.messages.filter { !it.fromMe }.maxOfOrNull { it.sentAt } ?: return
        if (upTo <= 0) return
        runCatching {
            if (net.realtime.connected) net.realtime.send(RtEvent.Read(chatId, _state.value.me.id, upTo))
            else net.queueCall("POST", "/v1/chats/$chatId/read", org.json.JSONObject().put("upTo", upTo), coalesce = "read:$chatId")
        }
    }

    /** Событие живого канала: сообщение, «печатает», «прочитано», состав группы. */
    fun onRealtime(event: RtEvent) {
        val s = _state.value
        // Свой номер на сервере — это «я»
        val ev = when (event) {
            is RtEvent.Typing -> event.copy(userId = Directory.local(event.userId))
            is RtEvent.Read -> event.copy(userId = Directory.local(event.userId))
            is RtEvent.GroupChanged -> event.copy(memberIds = event.memberIds.map(Directory::local), adminIds = event.adminIds.map(Directory::local))
            is RtEvent.NewMessage -> event
            is RtEvent.Follow -> event.copy(userId = Directory.local(event.userId))
            is RtEvent.NewComment -> event
        }
        when (ev) {
            is RtEvent.NewMessage -> {
                val m = runCatching { StateStore.Codec(s.me).messageOf(ev.message) }.getOrNull() ?: return
                val author = m.authorId
                if (author == s.me.id || author in s.blocked) return
                val received = m.copy(id = nextId++, fromMe = false, pending = false, delivered = true)
                val known = s.chats.firstOrNull { it.id == ev.chatId }
                    ?: s.chats.firstOrNull { !it.isGroup && it.peer.id == author }
                val chatId = if (known != null) known.id else {
                    // Новый человек пишет: друзьям — сразу в переписки, остальным — «Запросы»
                    val person = Demo.person(author) ?: return
                    val friend = author in s.following
                    when (s.privacy.messages) {
                        Audience.Nobody -> return
                        Audience.Following -> if (!friend) return
                        Audience.Everyone -> Unit
                    }
                    _state.update { it.copy(chats = listOf(Chat(ev.chatId, person, emptyList(), request = !friend)) + it.chats) }
                    ev.chatId
                }
                peopleChanged()
                val incoming = openIncoming(chatId, received)
                append(chatId, incoming)
                fetchMessageMedia(chatId, incoming)
                // Пришло — значит, больше не печатает
                _state.update { st -> st.copy(chats = st.chats.map { if (it.id == chatId) it.copy(typingIds = it.typingIds - author) else it }) }
                val chat = _state.value.chats.firstOrNull { it.id == chatId } ?: return
                if (_state.value.openChatId == chatId) sendRead(chatId)
                else if (!chat.muted && !chat.request) {
                    val shown = if (chat.secret) t("Новое сообщение") else incoming.text
                    notify(Nudges.message(Demo.person(author) ?: chat.peer, shown, chatId))
                }
            }
            is RtEvent.Typing -> _state.update { st ->
                st.copy(chats = st.chats.map { c ->
                    if (c.id != ev.chatId || ev.userId == st.me.id) c
                    else c.copy(typingIds = if (ev.typing) c.typingIds + ev.userId else c.typingIds - ev.userId)
                })
            }
            is RtEvent.Read -> _state.update { st ->
                st.copy(chats = st.chats.map { c ->
                    if (c.id != ev.chatId) c else c.copy(messages = c.messages.map { m ->
                        if (m.fromMe && m.sentAt in 1..ev.upTo && ev.userId !in m.readBy) m.copy(readBy = m.readBy + ev.userId) else m
                    })
                })
            }
            is RtEvent.Follow -> onFollowEvent(ev)
            is RtEvent.NewComment -> {
                val c = runCatching { StateStore.Codec(s.me).commentOf(ev.comment) }.getOrNull() ?: return
                if (c.author.id in s.blocked) return
                val post = s.findPost(ev.postId)
                if (post != null && post.commentList.none { it.id == c.id }) {
                    updatePost(ev.postId) { it.copy(comments = it.comments + 1, commentList = it.commentList + c) }
                }
                peopleChanged()
                if (post != null) notify(Nudges.commented(c.author, post, c.text)) else notify(Nudges.commentedOn(c.author, ev.postId, c.text))
            }
            is RtEvent.GroupChanged -> {
                if (s.me.id !in ev.memberIds && ev.memberIds.isNotEmpty()) {
                    // Нас убрали из группы
                    _state.update { st -> st.copy(chats = st.chats.filterNot { it.id == ev.chatId && it.isGroup }) }
                } else updateGroup(ev.chatId) { g ->
                    g.copy(title = ev.title.ifEmpty { g.title }, memberIds = ev.memberIds.ifEmpty { g.memberIds }, adminIds = ev.adminIds.toSet().ifEmpty { g.adminIds })
                }
            }
        }
    }

    /** Подписка по живому каналу: заявка ко мне, новый подписчик, мою заявку приняли. */
    private fun onFollowEvent(ev: RtEvent.Follow) {
        val uid = ev.userId
        if (uid in _state.value.blocked) return
        viewModelScope.launch {
            // Кто это — спросить у сервера, если ещё не знаем
            if (Demo.person(uid) == null) runCatching { net.api.user(uid) }
            val person = Demo.person(uid) ?: return@launch
            peopleChanged()
            when (ev.state) {
                "request" -> {
                    _state.update { it.copy(followRequests = (it.followRequests - uid) + uid) }
                    notify(Nudges.followRequest(person, emptyList()))
                }
                "follow" -> notify(Nudges.newFollower(person))
                "accepted" -> {
                    _state.update { it.copy(sentRequests = it.sentRequests + uid) }
                    acceptSentRequest(uid)
                    refreshFeedNow()
                }
            }
        }
    }

    /** Демо: выдать созревшие события живого канала (в приложении — раз в секунду). */
    fun tickRealtime(): Int = (net.realtime as? DemoRealtime)?.tick() ?: 0

    /** Подключить живой канал и рассказать демо-серверу, кто в каких переписках. */
    private fun connectRealtime() {
        val n = net
        n.demo?.let { d -> _state.value.chats.forEach { c -> d.registerChat(c.id, c.group?.memberIds ?: listOf(_state.value.me.id, c.peer.id)) } }
        runCatching { n.realtime.connect { ev -> onRealtime(ev) } }
    }

    // ==================== ЗАПРОСЫ НА ПЕРЕПИСКУ ====================

    /** Принять: переписка переезжает к остальным, человек видит «прочитано». */
    fun acceptRequest(chatId: Int) {
        _state.update { s -> s.copy(chats = s.chats.map { if (it.id == chatId) it.copy(request = false) else it }) }
        runCatching { net.queueCall("POST", "/v1/chats/$chatId/accept") }
        sendRead(chatId)
        kickNet()
    }

    /** Удалить запрос: человек не узнает. */
    fun deleteRequest(chatId: Int) {
        _state.update { s -> s.copy(chats = s.chats.filterNot { it.id == chatId && it.request }, openChatId = if (s.openChatId == chatId) null else s.openChatId) }
    }

    fun blockRequest(chatId: Int) {
        val chat = _state.value.chats.firstOrNull { it.id == chatId } ?: return
        deleteRequest(chatId)
        block(chat.peer.id)
    }

    // ==================== МОДЕРАЦИЯ ====================

    private val commentLimiter = RateLimiter(10, 60_000)
    private val messageLimiter = RateLimiter(30, 60_000)
    private val postLimiter = RateLimiter(10, 10 * 60_000)

    fun addHiddenWord(word: String) {
        _state.update { it.copy(privacy = it.privacy.copy(hiddenWords = Moderation.addWord(it.privacy.hiddenWords, word))) }
        pushProfile()
    }

    fun removeHiddenWord(word: String) {
        _state.update { it.copy(privacy = it.privacy.copy(hiddenWords = it.privacy.hiddenWords - word)) }
        pushProfile()
    }

    /** Выключить или включить комментарии к своей записи. */
    fun toggleComments(postId: Long) {
        val me = _state.value.me.id
        var off = false
        _state.update { s -> s.copy(posts = s.posts.map { if (it.id == postId && it.author.id == me) it.copy(commentsOff = !it.commentsOff).also { p -> off = p.commentsOff } else it }) }
        val post = _state.value.posts.firstOrNull { it.id == postId } ?: return
        if (post.outboxKey.isEmpty()) runCatching { net.queueCall("PATCH", "/v1/posts/$postId", org.json.JSONObject().put("commentsOff", off), coalesce = "comments:$postId") }
        kickNet()
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    private fun tooOften() {
        _state.update { it.copy(notice = t("Слишком часто — подождите минуту")) }
    }

    // ==================== ВХОД НА СЕРВЕР И УСТРОЙСТВА ====================

    /** Подходит ли пароль Memory (проверка на телефоне; задаёт MainActivity). */
    var passwordCheck: (String) -> Boolean = { true }

    /** Идёт вход с плашки «Войдите на сервер». */
    private var serverSignIn: Job? = null

    /**
     * Плашка «Войдите на сервер»: пароль спрашивается один раз (телефон хранит
     * только его отпечаток). Неверный пароль на сервер не уходит — иначе
     * сервер завёл бы аккаунт с ним.
     */
    fun signInToServer(password: String) {
        if (serverSignIn?.isActive == true) return
        serverSignIn = viewModelScope.launch {
            // Проверка пароля нарочно медленная (PBKDF2) — не в главном потоке
            val ok = password.isNotEmpty() && kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { passwordCheck(password) }
            if (!ok) {
                _state.update { it.copy(notice = t("Пароль не подходит")) }
                return@launch
            }
            net.session.clear()
            runCatching { startSessionNow(_state.value.me.handle, password, fresh = false) }
            if (_state.value.signedOut || !net.session.signedIn) _state.update { it.copy(notice = t("Не получилось — проверьте сеть")) }
            else {
                _state.update { it.copy(notice = t("Готово — вы на сервере")) }
                refreshFeedNow()
            }
        }
    }

    /**
     * Удалить аккаунт и на сервере (до удаления на телефоне). null — можно
     * удалять дальше; иначе текст ошибки (нет связи — ничего не удаляем).
     */
    suspend fun deleteOnServer(password: String): String? {
        if (!serverMode) return null
        if (!net.session.signedIn) {
            try {
                net.signIn(_state.value.me.handle, password, deviceName, "android")
            } catch (e: NetError) {
                // Войти нельзя: такого аккаунта на сервере нет (заведён до сервера и туда не попал) — удалять там нечего
                if (e.kind == NetError.Kind.Auth) return null
                return if (e.code == 403) t("Аккаунт заблокирован модератором — напишите в поддержку, чтобы удалить его на сервере.")
                else t("Нет связи с сервером — аккаунт не удалён. Попробуйте позже.")
            }
        }
        return try {
            net.api.deleteMe(password)
            net.session.clear()
            null
        } catch (e: NetError) {
            // Сервер ответил, но пароль у него другой — ничего не удаляем, иначе аккаунт остался бы на сервере
            if (e.code == 403) t("Пароль на сервере другой — аккаунт не удалён. Войдите на сервер и повторите.")
            else t("Нет связи с сервером — аккаунт не удалён. Попробуйте позже.")
        }
    }

    /** Сменить пароль и на сервере (до смены на телефоне). null — можно менять дальше. */
    suspend fun changeServerPassword(current: String, new: String): String? {
        if (!serverMode) return null
        if (!net.session.signedIn) {
            try {
                net.signIn(_state.value.me.handle, current, deviceName, "android")
            } catch (e: NetError) {
                // Аккаунта на сервере нет — меняем только на телефоне
                if (e.kind == NetError.Kind.Auth || e.code == 403) return null
                return t("Нет связи с сервером — пароль не сменён. Попробуйте позже.")
            }
        }
        return try {
            net.api.changePassword(current, new)
            null
        } catch (e: NetError) {
            if (e.code == 403) t("Пароль на сервере другой — пароль не сменён. Войдите на сервер и повторите.")
            else t("Нет связи с сервером — пароль не сменён. Попробуйте позже.")
        }
    }

    /** Имя этого телефона для списка устройств («Pixel 8»). */
    var deviceName: String = "Android"

    /**
     * Войти на сервер (если ещё не вошли) и сверить профиль. fresh — на
     * этом телефоне аккаунт новый: берём профиль с сервера, иначе — шлём свой.
     */
    fun startSession(login: String, password: String?, fresh: Boolean) {
        if (!demoReplies) return
        viewModelScope.launch { runCatching { startSessionNow(login, password, fresh) } }
    }

    suspend fun startSessionNow(login: String, password: String?, fresh: Boolean) {
        suspend fun register(): Boolean = !password.isNullOrEmpty() && try {
            net.api.register(login, password, _state.value.me.name, deviceName, "android")
            true
        } catch (e: NetError) {
            false
        }
        suspend fun signIn(): Boolean = try {
            net.signIn(login, password.orEmpty(), deviceName, "android")
            true
        } catch (e: NetError) {
            // Настоящий сервер этот аккаунт ещё не знает (заведён на телефоне) — завести там
            val registered = e.kind == NetError.Kind.Auth && register()
            if (!registered && (e.kind == NetError.Kind.Auth || e.kind == NetError.Kind.Client)) _state.update { it.copy(signedOut = true) }
            registered
        }
        if (!net.session.signedIn && !signIn()) return
        // Свой номер — сразу: даже если профиль дальше не придёт, свои записи останутся своими
        if (net.session.userId > 0) Directory.selfId = net.session.userId
        val remote = try { net.api.profile() } catch (e: NetError) {
            // Сохранённые токены сервер не знает (демо-сервер начинает с нуля при каждом запуске,
            // настоящий — вышли с этого телефона на другом): войти ещё раз
            if (e.kind != NetError.Kind.Auth) {
                // Нет сети или сервер занят: профиль сверим в другой раз, остальное — сейчас
                onSignedIn()
                return
            }
            net.session.clear()
            if (!signIn()) return
            try { net.api.profile() } catch (e2: NetError) { onSignedIn(); return }
        }
        _state.update { it.copy(signedOut = false) }
        if (remote == null || !fresh) pushProfile() else applyProfile(remote.json)
        onSignedIn()
    }

    /**
     * Вошли: свой номер на сервере, живой канал — с новым токеном, свой
     * открытый ключ — на сервер, переписки — сверить (пришедшее без нас).
     */
    private suspend fun onSignedIn() {
        if (net.session.userId > 0) Directory.selfId = net.session.userId
        runCatching { net.realtime.reconnect() }
        runCatching { net.queueCall("PUT", "/v1/me/key", org.json.JSONObject().put("publicKey", java.util.Base64.getEncoder().encodeToString(myKeys().publicKey)), coalesce = "key") }
        kickNet()
        if (net.demo == null) {
            runCatching { syncChats() }
            runCatching { syncFollows() }
            runCatching { syncStories() }
            refreshExplore()
        }
        pushToken?.let(::setPushToken)
        peopleChanged()
    }

    /** Токен пушей этого телефона (FCM); сервер шлёт на него, когда приложение закрыто. */
    private var pushToken: String? = null

    fun setPushToken(token: String?) {
        if (token.isNullOrEmpty()) return
        pushToken = token
        if (!serverMode || !net.session.signedIn) return
        runCatching { net.queueCall("PUT", "/v1/devices/current/push", org.json.JSONObject().put("token", token), coalesce = "push") }
        kickNet()
    }

    /**
     * Переписки с сервера: новые (кто-то написал, пока телефон был выключен;
     * позвали в группу) — добавить; в известных — дописать пропущенные
     * сообщения собеседников.
     */
    suspend fun syncChats() {
        for (o in net.api.chats()) {
            val chatId = o.optInt("id")
            if (chatId == 0) continue
            val members = o.optJSONArray("members")?.let { a -> (0 until a.length()).map { Directory.local(a.optInt(it)) } }.orEmpty()
            val local = _state.value.chats.firstOrNull { it.id == chatId }
            if (local == null) {
                val chat = if (o.optString("kind") == "group") {
                    val admins = o.optJSONArray("admins")?.let { a -> (0 until a.length()).map { Directory.local(a.optInt(it)) } }.orEmpty()
                    for (id in members) if (Demo.person(id) == null) runCatching { net.api.user(id) }
                    val g = ChatGroup(o.optString("title"), members, admins.toSet(), Directory.local(o.optInt("owner")), hue = Random.nextFloat())
                    Chat(chatId, g.asUser(chatId), emptyList(), group = g)
                } else {
                    val person = Directory.remember(o.optJSONObject("user")) ?: continue
                    if (person.id in _state.value.blocked) continue
                    Chat(chatId, person, emptyList(), request = !o.optBoolean("accepted", true))
                }
                _state.update { it.copy(chats = listOf(chat) + it.chats) }
            }
            // Пропущенное: сообщения собеседников новее последнего известного
            val known = _state.value.chats.firstOrNull { it.id == chatId } ?: continue
            val last = known.messages.filter { !it.fromMe }.maxOfOrNull { it.sentAt } ?: 0L
            val page = runCatching { net.api.messages(chatId, null) }.getOrNull() ?: continue
            page.items.filter { !it.fromMe && it.sentAt > last && it.authorId !in _state.value.blocked }.sortedBy { it.sentAt }.forEach { m ->
                val incoming = openIncoming(chatId, m.copy(id = nextId++, pending = false, delivered = true))
                append(chatId, incoming)
                fetchMessageMedia(chatId, incoming)
            }
        }
        peopleChanged()
    }

    /** Свой профиль и приватность — на сервер (для других своих телефонов). */
    private fun pushProfile() {
        val s = _state.value
        val o = org.json.JSONObject()
            .put("name", s.me.name).put("bio", s.me.bio).put("city", s.me.city).put("birthday", s.me.birthday)
            .put("website", s.me.website).put("interests", org.json.JSONArray(s.me.interests))
            .put("privacy", StateStore.Codec(s.me).privacyJson(s.privacy))
        runCatching { net.queueCall("PUT", "/v1/me", o, coalesce = "me") }
        kickNet()
    }

    /** Профиль с сервера — на новый телефон. */
    private fun applyProfile(o: org.json.JSONObject) {
        _state.update { s ->
            fun str(k: String, d: String) = o.optString(k).ifEmpty { d }
            val arr = o.optJSONArray("interests")
            val interests = if (arr == null) s.me.interests else (0 until arr.length()).map { arr.optString(it) }
            val me = s.me.copy(name = str("name", s.me.name), bio = str("bio", s.me.bio), city = str("city", s.me.city),
                birthday = str("birthday", s.me.birthday), website = str("website", s.me.website), interests = interests)
            val privacy = o.optJSONObject("privacy")?.let { StateStore.Codec(me).privacyOf(it) } ?: s.privacy
            s.withMe(me).copy(privacy = privacy)
        }
    }

    fun loadDevices() {
        if (!demoReplies) return
        viewModelScope.launch { loadDevicesNow() }
    }

    suspend fun loadDevicesNow() {
        try {
            val list = net.api.devices()
            _state.update { it.copy(devices = list.sortedWith(compareByDescending<DeviceInfo> { d -> d.current }.thenByDescending { d -> d.lastSeen })) }
        } catch (e: NetError) {
            _state.update { it.copy(notice = if (e.kind == NetError.Kind.Offline) t("Нет сети — список устройств загрузится позже") else t("Не удалось загрузить устройства")) }
            if (e.kind == NetError.Kind.Auth) _state.update { it.copy(signedOut = true) }
        }
    }

    fun revokeDevice(id: String) {
        if (!demoReplies) return
        viewModelScope.launch { revokeDeviceNow(id) }
    }

    suspend fun revokeDeviceNow(id: String) {
        runCatching { net.api.revokeDevice(id) }.onFailure { _state.update { it.copy(notice = t("Не получилось — проверьте сеть")) } }
        loadDevicesNow()
    }

    fun revokeOtherDevices() {
        if (!demoReplies) return
        viewModelScope.launch {
            runCatching { net.api.revokeOtherDevices() }.onFailure { _state.update { it.copy(notice = t("Не получилось — проверьте сеть")) } }
            loadDevicesNow()
        }
    }

    // ==================== ФОНОВАЯ ОТПРАВКА ====================

    /**
     * Пока приложение было закрыто, очередь могла уйти в фоне (NetWorker).
     * Сверить: записи, которых в очереди уже нет, — отправлены; сообщения
     * переписок без очереди — тоже.
     */
    /**
     * С сервером своя запись и история получают его номер: по нему
     * комментарии, «нравится», просмотры, и лента не задваивает. Номер мог
     * прийти и при фоновой отправке — тогда он ждёт в кэше сети.
     */
    private fun adoptServerIds() {
        val sent = runCatching { net.takeServerIds() }.getOrDefault(emptyList())
        if (sent.isEmpty() || !serverMode) return
        val posts = sent.filter { it.kind == OpKind.CreatePost }.associate { it.localId to it.serverId }
        val stories = sent.filter { it.kind == OpKind.CreateStory }.associate { it.localId to it.serverId }
        serverStoryIds += stories.values
        val me = _state.value.me.id
        _state.update { s ->
            s.copy(
                posts = s.posts.map { p -> posts[p.id]?.takeIf { p.author.id == me }?.let { p.copy(id = it, outboxKey = "") } ?: p },
                stories = s.stories.map { st -> stories[st.id]?.takeIf { st.authorId == me }?.let { st.copy(id = it) } ?: st },
            )
        }
    }

    private fun reconcileOutbox() {
        adoptServerIds()
        val ops = runCatching { net.outbox.all() }.getOrDefault(emptyList())
        val keys = ops.map { it.key }.toSet()
        val chatsWithOps = ops.filter { it.kind == OpKind.SendMessage }.map { it.target.toInt() }.toSet()
        _state.update { s ->
            s.copy(
                posts = s.posts.map { if (it.outboxKey.isNotEmpty() && it.outboxKey !in keys) it.copy(outboxKey = "") else it },
                chats = s.chats.map { c ->
                    if (c.id in chatsWithOps || c.messages.none { it.pending }) c
                    else c.copy(messages = c.messages.map { if (it.pending) it.copy(pending = false, delivered = true) else it })
                },
            )
        }
    }

    // ==================== СЕТЬ ====================

    /**
     * Всё сетевое: API, очередь отправки, загрузки, кэш. Пока аккаунт не
     * открыт (и в тестах) — встроенный демо-сервер и кэш в памяти;
     * MainActivity подставляет настоящий (NetSetup.forAccount).
     */
    var net: Net = Net.demo(clock, { _state.value.me })
        private set

    private var feedPager: Pager<Post> = net.feedPager()

    // Демо-сеть по умолчанию (и в тестах) — тоже с живым каналом
    init {
        connectRealtime()
    }
    private var netJob: Job? = null
    private var tickJob: Job? = null
    private val netKick = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    /** Есть ли сеть у телефона (NetMonitor). */
    private var deviceOnline = true

    /** Сжатая копия файла больше не нужна (на телефоне — MediaCompressor.cleanup). */
    var uploadCleanup: (UploadJob) -> Unit = {}

    /** Подключить сеть аккаунта: сохранённая лента — сразу, без сети. */
    fun attachNet(n: Net) {
        net = n
        // Свой номер на сервере — до того, как читать кэш: записи с ним — мои
        Directory.selfId = n.session.userId
        _state.update { it.copy(serverMode = n.demo == null) }
        feedPager = n.feedPager()
        feedPager.restore()
        explorePager = n.explorePager()
        publishFeed()
        reconcileOutbox()
        connectRealtime()
        refreshNetCounters()
        applyOnline()
    }

    /** Отключить (выход из аккаунта): назад к демо в памяти. */
    private fun detachNet() {
        netJob?.cancel()
        netJob = null
        tickJob?.cancel()
        tickJob = null
        runCatching { net.realtime.close() }
        Directory.selfId = 0
        _state.update { it.copy(serverMode = false) }
        // Люди с сервера — у каждого аккаунта свои (вернутся с его сохранённым состоянием)
        Directory.clear()
        net = Net.demo(clock, { _state.value.me })
        feedPager = net.feedPager()
        explorePager = net.explorePager()
        serverStoryIds.clear()
    }

    private fun publishFeed() {
        val p = feedPager.state
        val items = withLocalMedia(p.items)
        _state.update {
            it.copy(
                olderFeed = items,
                peopleVersion = Directory.version,
                feedPaging = FeedPaging(p.loading, p.end, p.fromCache, p.cachedAt, p.error?.kind),
            )
        }
    }

    /** Какие файлы с сервера уже качаются — чтобы не дважды. */
    private val fetchingMedia = HashSet<String>()

    /**
     * Фото и видео чужих записей лежат на сервере: показываем скачанную
     * копию, а чего ещё нет — качаем и потом перерисовываем ленту.
     */
    private fun withLocalMedia(posts: List<Post>): List<Post> = posts.map { p ->
        if (p.media.none { net.isRemote(it.path) }) p
        else p.copy(media = p.media.map { a ->
            if (!net.isRemote(a.path)) a else {
                val f = net.mediaFile(a.path, mediaExt(a))
                if (f.exists() && f.length() > 0) a.copy(path = f.path) else { fetchFeedMedia(a); a }
            }
        })
    }

    private fun fetchFeedMedia(a: Attachment) {
        if (!fetchingMedia.add(a.path)) return
        val n = net
        viewModelScope.launch {
            val ok = runCatching { n.fetchMedia(a.path, ext = mediaExt(a)) }.isSuccess
            if (ok && n === net) {
                publishFeed()
                publishExplore()
            }
            // Не вышло (нет сети) — попробуем при следующем показе ленты
            if (!ok) fetchingMedia.remove(a.path)
        }
    }

    private fun refreshNetCounters() {
        _state.update { it.copy(uploads = net.uploads.all(), outboxCount = net.outbox.count, cacheBytes = runCatching { net.cache.sizeBytes() }.getOrDefault(0)) }
    }

    /** Есть ли сеть — от NetMonitor. Появилась — сразу отправить накопленное. */
    fun setDeviceOnline(online: Boolean) {
        deviceOnline = online
        applyOnline()
    }

    fun setDemoOffline(on: Boolean) {
        _state.update { it.copy(demoOffline = on) }
        applyOnline()
    }

    fun setMediaQuality(q: MediaQuality) {
        _state.update { it.copy(mediaQuality = q) }
    }

    private fun applyOnline() {
        val online = deviceOnline && !_state.value.demoOffline
        net.demo?.online = online
        _state.update {
            it.copy(netStatus = when {
                !online -> NetStatus.Offline
                it.netStatus == NetStatus.ServerDown -> NetStatus.ServerDown
                else -> NetStatus.Online
            })
        }
        if (online) {
            net.wake()
            kickNet()
        }
    }

    /** Разбудить отправку: что-то добавили в очередь или появилась сеть. */
    fun kickNet() {
        netKick.trySend(Unit)
    }

    /**
     * Фоновая отправка: пока приложение открыто, догружает и отправляет.
     * Ждёт пинка (kickNet) или ближайшей повторной попытки.
     */
    fun startNetLoop() {
        if (netJob != null || !demoReplies) return
        // Демо-сервер «присылает» события живого канала — выдаём их раз в секунду
        tickJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                runCatching { tickRealtime() }
            }
        }
        netJob = viewModelScope.launch {
            while (true) {
                // Приложение свёрнуто, а сервер настоящий — очередь отправляет NetWorker,
                // не мешаем ему (с демо-сервером — только мы)
                val workerOwns = net.demo == null && !ru.wishka.memory.push.AppVisibility.foreground
                if (_state.value.netStatus != NetStatus.Offline && net.busy && !workerOwns) {
                    runCatching { pumpNet() }
                }
                val wait = if (net.busy) (net.nextAttemptAt() - clock()).coerceIn(1_000, 30_000) else 60_000
                kotlinx.coroutines.withTimeoutOrNull(wait) { netKick.receive() }
            }
        }
    }

    /** Один проход отправки: загрузки, потом очередь. Результат — на экраны. */
    suspend fun pumpNet() {
        val r = net.pump(onProgress = { _state.update { s -> s.copy(uploads = net.uploads.all()) } })
        val c = StateStore.Codec(_state.value.me)
        for ((op, answer) in r.sent) {
            when (op.kind) {
                OpKind.CreatePost -> _state.update { s -> s.copy(posts = s.posts.map { if (it.outboxKey == op.key) it.copy(outboxKey = "") else it }) }
                OpKind.SendMessage -> {
                    val localId = runCatching { org.json.JSONObject(op.payload).getLong("id") }.getOrNull()
                    val sentAt = answer?.optLong("sentAt") ?: 0L
                    if (localId != null) updateMessage(op.target.toInt(), localId) { it.copy(pending = false, delivered = true, sentAt = sentAt) }
                }
                else -> Unit
            }
        }
        adoptServerIds()
        r.uploads.filter { it.state == UploadState.Done }.forEach(uploadCleanup)
        for (op in r.failed) {
            when (op.kind) {
                OpKind.CreatePost -> {
                    _state.update { s -> s.copy(posts = s.posts.map { if (it.outboxKey == op.key) it.copy(outboxKey = "") else it }, netNotice = t("Запись не отправлена: файл не нашёлся. Она осталась только на этом телефоне.")) }
                }
                OpKind.SendMessage -> {
                    val localId = runCatching { org.json.JSONObject(op.payload).getLong("id") }.getOrNull()
                    if (localId != null) updateMessage(op.target.toInt(), localId) { it.copy(pending = false) }
                    _state.update { it.copy(netNotice = t("Сообщение не отправлено: вложение не нашлось.")) }
                }
                else -> Unit
            }
        }
        _state.update {
            it.copy(
                netStatus = when {
                    !deviceOnline || it.demoOffline -> NetStatus.Offline
                    r.offline -> NetStatus.ServerDown
                    else -> NetStatus.Online
                },
                signedOut = it.signedOut || r.authLost,
            )
        }
        refreshNetCounters()
    }

    private fun updateMessage(chatId: Int, messageId: Long, change: (Message) -> Message) {
        _state.update { s ->
            s.copy(chats = s.chats.map { ch -> if (ch.id != chatId) ch else ch.copy(messages = ch.messages.map { if (it.id == messageId) change(it) else it }) })
        }
    }

    /** Долистали ленту до конца — подгрузить старое (в тестах — loadMoreFeedNow). */
    fun loadMoreFeed() {
        if (!demoReplies || _state.value.feedPaging.loading || _state.value.feedPaging.end) return
        viewModelScope.launch { loadMoreFeedNow() }
    }

    suspend fun loadMoreFeedNow() {
        _state.update { it.copy(feedPaging = it.feedPaging.copy(loading = true, error = null)) }
        val err = feedPager.loadMore()
        publishFeed()
        if (err?.kind == NetError.Kind.Offline && deviceOnline && !_state.value.demoOffline) _state.update { it.copy(netStatus = NetStatus.ServerDown) }
        if (err == null && _state.value.netStatus == NetStatus.ServerDown) _state.update { it.copy(netStatus = NetStatus.Online) }
        refreshNetCounters()
    }

    /** Потянули ленту вниз: заново с сервера. */
    fun refreshFeed() {
        if (!demoReplies) return
        viewModelScope.launch { refreshFeedNow() }
    }

    /** Лента заново с первой страницы (потянули вниз). */
    suspend fun refreshFeedNow() {
        feedPager.refresh()
        publishFeed()
        refreshNetCounters()
        if (serverMode) {
            runCatching { syncStories() }
            refreshExplore()
        }
    }

    private var explorePager: Pager<Post> = net.explorePager()

    private fun publishExplore() {
        val p = explorePager.state
        _state.update { it.copy(explore = withLocalMedia(p.items), explorePaging = FeedPaging(p.loading, p.end, p.fromCache, p.cachedAt, p.error?.kind)) }
    }

    /** «Интересное» заново (с сервером; у демо его нет). */
    suspend fun refreshExplore() {
        if (!serverMode) return
        explorePager.refresh()
        publishExplore()
    }

    /** Долистали «Интересное» до конца — следующая страница. */
    fun loadMoreExplore() {
        if (!serverMode || _state.value.explorePaging.loading || _state.value.explorePaging.end) return
        viewModelScope.launch {
            explorePager.loadMore()
            publishExplore()
        }
    }

    fun pauseUpload(id: String) {
        net.uploads.pause(id)
        refreshNetCounters()
    }

    fun resumeUpload(id: String) {
        net.uploads.resume(id)
        refreshNetCounters()
        kickNet()
    }

    /**
     * Отменить загрузку: её запись или сообщение не уйдёт (останется
     * только на этом телефоне), остальные загрузки того же — тоже снимаются.
     */
    fun cancelUpload(id: String) {
        val job = net.uploads.get(id) ?: return
        net.uploads.all().filter { it.owner == job.owner }.forEach { j -> uploadCleanup(j); net.uploads.remove(j.id) }
        net.outbox.get(job.owner)?.let { op ->
            net.outbox.remove(op.key)
            if (op.kind == OpKind.CreatePost) _state.update { s -> s.copy(posts = s.posts.map { if (it.outboxKey == op.key) it.copy(outboxKey = "") else it }) }
            if (op.kind == OpKind.SendMessage) {
                val localId = runCatching { org.json.JSONObject(op.payload).getLong("id") }.getOrNull()
                if (localId != null) updateMessage(op.target.toInt(), localId) { it.copy(pending = false) }
            }
        }
        refreshNetCounters()
    }

    /** Стереть сохранённые страницы (очередь и загрузки — не трогаем). */
    fun clearNetCache() {
        net.cache.clearPages()
        feedPager = net.feedPager()
        explorePager = net.explorePager()
        publishFeed()
        publishExplore()
        refreshNetCounters()
    }

    fun dismissNetNotice() {
        _state.update { it.copy(netNotice = null) }
    }

    // ==================== РЕПОСТЫ ====================

    /** Копия записи для репоста или сообщения; у репоста без слов — сам оригинал. */
    fun postRef(post: Post): PostRef {
        val root = post.repostOf?.takeIf { post.text.isBlank() && post.media.isEmpty() }
        if (root != null) return root
        return PostRef(
            post.id, post.author.id, post.author.name, post.text.take(500),
            photo = post.media.firstOrNull { it.kind == MediaKind.Photo && !it.sticker }?.path.orEmpty(),
            seed = post.artSeed ?: 0, createdAt = post.createdAt,
        )
    }

    fun openRepost(postId: Long) {
        _state.update { it.copy(repostingPostId = postId, sharingPostId = null) }
    }

    fun openSendToChat(postId: Long) {
        _state.update { it.copy(sharingPostId = postId, repostingPostId = null) }
    }

    fun closeRepost() {
        _state.update { it.copy(repostingPostId = null, sharingPostId = null) }
    }

    /**
     * Репост в свою ленту: оригинал карточкой, comment — свои слова (может
     * быть пустым). Закрытые профили и свои записи без слов не репостятся.
     */
    fun repost(postId: Long, comment: String = ""): String? {
        val s = _state.value
        val post = s.findPost(postId) ?: return t("Запись не найдена")
        if (post.author.closed && post.author.id != s.me.id) return t("Записями закрытого профиля нельзя поделиться")
        val ref = postRef(post)
        if (ref.authorId == s.me.id && comment.isBlank()) return t("Добавьте пару слов к своей записи")
        Telemetry.track(AppEvent.Reposted)
        val mine = Post(nextId++, s.me, comment.trim().take(2000), 0, createdAt = clock(), repostOf = ref)
        _state.update { st ->
            fun bump(p: Post) = if (p.id == ref.postId) p.copy(reposts = p.reposts + 1) else p
            st.copy(
                posts = listOf(mine) + st.posts.map(::bump),
                groups = st.groups.map { g -> g.copy(posts = g.posts.map(::bump)) },
                repostingPostId = null,
            )
        }
        return null
    }

    /** Отправить запись в переписку — карточкой, со своими словами. */
    fun sendPostToChat(postId: Long, chatId: Int, text: String = "") {
        val post = _state.value.findPost(postId) ?: return
        Telemetry.track(AppEvent.LinkShared)
        append(chatId, Message(nextId++, fromMe = true, text = text.trim().take(1000), time = now(), post = postRef(post)))
        _state.update { it.copy(sharingPostId = null) }
    }

    /** Нажали на запись внутри репоста или сообщения. false — её нет на этом телефоне. */
    fun openPostRef(ref: PostRef): Boolean = openLink(LinkTarget.PostLink(ref.postId))

    // ==================== ОТМЕТКИ ====================

    /** Записи, где меня отметили на фото (у себя на телефоне — в своих и друзей). */
    val taggedPosts: List<Post> get() = _state.value.taggedPosts

    /** Убрать отметку с собой на чужом фото (с сервером — запрос автору записи не нужен). */
    fun untagMe(postId: Long) {
        val me = _state.value.me.id
        fun clean(p: Post) = if (p.id != postId) p else p.copy(media = p.media.map { a -> a.copy(tags = a.tags.filter { it.userId != me }) })
        _state.update { s -> s.copy(posts = s.posts.map(::clean), groups = s.groups.map { g -> g.copy(posts = g.posts.map(::clean)) }) }
    }

    // ==================== ЗВОНКИ ====================

    /** Связь звонков: демо, пока нет сервера. */
    var callTransport: CallTransport = DemoCallTransport

    private var callJob: Job? = null

    /** Позвонить. Ответ — текст ошибки или null. */
    fun startCall(peerId: Int, video: Boolean): String? {
        val s = _state.value
        if (s.call != null) return t("Звонок уже идёт")
        if (peerId in s.blocked) return t("Вы заблокировали этого человека")
        Demo.person(peerId) ?: return t("Не удалось позвонить")
        Telemetry.track(if (video) AppEvent.VideoCall else AppEvent.VoiceCall)
        val call = ActiveCall(nextId++, peerId, video, outgoing = true, phase = CallPhase.Calling, startedAt = clock(), cameraOn = video)
        callTransport.connect(call.room, "", video)
        _state.update { it.copy(call = call, openChatId = it.openChatId) }
        if (demoReplies) {
            callJob?.cancel()
            callJob = viewModelScope.launch {
                // Демо: чаще всего друг отвечает через пару секунд, иногда — нет
                if (Random.nextInt(8) == 0) { delay(12_000); callTimeout() } else { delay(Random.nextLong(2_000, 5_000)); callAnswered() }
            }
        }
        return null
    }

    /** Собеседник ответил — разговор пошёл. */
    fun callAnswered() {
        val c = _state.value.call ?: return
        if (c.phase != CallPhase.Calling) return
        _state.update { it.copy(call = c.copy(phase = CallPhase.Active, connectedAt = clock())) }
    }

    /** Звонят мне (демо — друг; с сервером — пуш). Ничего не делает, если звонки от него запрещены. */
    fun incomingCall(peerId: Int, video: Boolean) {
        val s = _state.value
        if (s.call != null || !Calls.allowed(peerId, s.following, s.blocked, s.privacy.calls)) return
        val person = Demo.person(peerId) ?: return
        val call = ActiveCall(nextId++, peerId, video, outgoing = false, phase = CallPhase.Incoming, startedAt = clock(), cameraOn = video)
        _state.update { it.copy(call = call) }
        if (demoReplies) {
            callJob?.cancel()
            callJob = viewModelScope.launch { delay(Calls.RING_MS); callTimeout() }
        }
        // Приложение открыто — звонок и так на весь экран; свёрнуто — уведомлением
        if (!ru.wishka.memory.push.AppVisibility.foreground) notify(Nudges.incomingCall(person, video))
    }

    /** Принять входящий. */
    fun acceptCall() {
        val c = _state.value.call ?: return
        if (c.phase != CallPhase.Incoming) return
        callJob?.cancel()
        callTransport.connect(c.room, "", c.video)
        _state.update { it.copy(call = c.copy(phase = CallPhase.Active, connectedAt = clock())) }
    }

    /** Отклонить входящий. */
    fun declineCall() {
        val c = _state.value.call ?: return
        if (c.phase != CallPhase.Incoming) return
        finishCall(Calls.record(c, clock(), declined = true))
    }

    /** Положить трубку / отменить исходящий. */
    fun endCall() {
        val c = _state.value.call ?: return
        finishCall(Calls.record(c, clock()))
    }

    /** Никто не ответил: исходящий — «нет ответа», входящий — пропущенный. */
    fun callTimeout() {
        val c = _state.value.call ?: return
        if (c.phase == CallPhase.Active) return
        val record = Calls.record(c, clock())
        finishCall(record)
        if (!c.outgoing) {
            Demo.person(c.peerId)?.let { person ->
                record(ActivityKind.MissedCall, person, c.id)
                notify(Nudges.missedCall(person, c.video, chatWith(c.peerId) ?: 0))
            }
        }
    }

    private fun finishCall(record: CallRecord) {
        callJob?.cancel()
        callTransport.hangUp()
        val chatId = chatWith(record.peerId)
        _state.update { it.copy(call = null, callLog = Calls.add(it.callLog, record)) }
        // Строка звонка — в переписке с человеком
        if (chatId != null) append(chatId, Message(nextId++, fromMe = record.outgoing, text = "", time = now(), call = record))
    }

    fun toggleMute() = updateCall { c -> c.copy(muted = !c.muted).also { callTransport.setMuted(it.muted) } }

    fun toggleCamera() = updateCall { c -> c.copy(cameraOn = !c.cameraOn).also { callTransport.setCamera(it.cameraOn, it.frontCamera) } }

    fun flipCamera() = updateCall { c -> c.copy(frontCamera = !c.frontCamera).also { callTransport.setCamera(it.cameraOn, it.frontCamera) } }

    fun toggleSpeaker() = updateCall { it.copy(speaker = !it.speaker) }

    private fun updateCall(change: (ActiveCall) -> ActiveCall) {
        _state.update { s -> s.call?.let { s.copy(call = change(it)) } ?: s }
    }

    /** Перезвонить из истории звонков или переписки. */
    fun callBack(record: CallRecord) = startCall(record.peerId, record.video)

    fun clearCallLog() {
        _state.update { it.copy(callLog = emptyList()) }
    }

    // ==================== ЭФИРЫ ====================

    /** Видеосвязь эфиров: демо, пока нет сервера LiveKit. */
    var liveTransport: LiveTransport = DemoLiveTransport

    private var liveJob: Job? = null

    fun openLiveStart() {
        _state.update { it.copy(liveStarting = true) }
    }

    fun closeLiveStart() {
        _state.update { it.copy(liveStarting = false) }
    }

    /** Начать свой эфир. Ответ — текст ошибки или null. */
    fun startLive(title: String, friendsOnly: Boolean): String? {
        if (_state.value.myLive != null) return t("Эфир уже идёт")
        val clean = title.trim().take(Lives.MAX_TITLE)
        if (clean.isEmpty()) return t("Назовите эфир — так друзья поймут, о чём он")
        val live = LiveStream(nextId++, _state.value.me.id, clean, clock(), friendsOnly = friendsOnly, seed = Random.nextInt(1000))
        Telemetry.track(AppEvent.LiveStarted)
        liveTransport.publish(live.room, "")
        _state.update { it.copy(myLive = live, watchingLiveId = live.id, liveStarting = false, liveSummary = null) }
        ensureLiveTicker()
        return null
    }

    /** Закончить свой эфир — итог на экране. */
    fun endLive() {
        val live = _state.value.myLive ?: return
        liveTransport.leave()
        val ended = live.copy(endedAt = clock(), viewers = 0)
        _state.update { it.copy(myLive = null, watchingLiveId = null, liveSummary = ended, confirmEndLive = false) }
    }

    /** Вернуться на экран своего идущего эфира. */
    fun returnToMyLive() {
        val live = _state.value.myLive ?: return
        _state.update { it.copy(watchingLiveId = live.id) }
    }

    fun cancelEndLive() {
        _state.update { it.copy(confirmEndLive = false) }
    }

    fun dismissLiveSummary() {
        _state.update { it.copy(liveSummary = null) }
    }

    /** Смотреть эфир друга. */
    fun watchLive(liveId: Long) {
        val live = _state.value.visibleLives.firstOrNull { it.id == liveId } ?: return
        Telemetry.track(AppEvent.LiveWatched)
        liveTransport.join(live.room, "")
        _state.update { s ->
            s.copy(
                watchingLiveId = liveId, activityOpen = false, searchOpen = false,
                lives = s.lives.map { if (it.id == liveId) Lives.viewers(it, 1) else it },
            )
        }
        ensureLiveTicker()
    }

    /** Выйти из эфира друга. */
    fun leaveLive() {
        val id = _state.value.watchingLiveId ?: return
        if (_state.value.myLive?.id == id) return
        liveTransport.leave()
        _state.update { s -> s.copy(watchingLiveId = null, lives = s.lives.map { if (it.id == id) Lives.viewers(it, -1) else it }) }
    }

    private fun updateLive(id: Long, change: (LiveStream) -> LiveStream) {
        _state.update { s ->
            s.copy(
                myLive = s.myLive?.let { if (it.id == id) change(it) else it },
                lives = s.lives.map { if (it.id == id) change(it) else it },
            )
        }
    }

    /** Комментарий в открытом эфире. */
    fun sendLiveComment(text: String) {
        val id = _state.value.watchingLiveId ?: return
        val clean = text.trim().take(Lives.MAX_COMMENT)
        if (clean.isEmpty()) return
        val me = _state.value.me
        updateLive(id) { Lives.addComment(it, LiveComment(nextId++, me.id, me.name, clean, clock(), me.hue)) }
    }

    /** Сердечко в открытом эфире. */
    fun sendHeart() {
        val id = _state.value.watchingLiveId ?: return
        updateLive(id) { it.copy(hearts = it.hearts + 1) }
    }

    /** Друг начал эфир: в «Историях», в ленте активности и уведомлением. */
    fun friendGoesLive(friend: User, random: Random) {
        val s = _state.value
        if (friend.id in s.blocked || s.lives.any { it.hostId == friend.id && it.live }) return
        val title = Lives.demoTitle(random.nextInt(100))
        val live = LiveStream(nextId++, friend.id, title, clock(), viewers = 3 + random.nextInt(20), seed = random.nextInt(1000))
        _state.update { it.copy(lives = listOf(Lives.viewers(live, 0)) + it.lives) }
        record(ActivityKind.Live, friend, live.id, title)
        notify(Nudges.liveStarted(friend, title))
        ensureLiveTicker()
    }

    /**
     * Шаг демо-эфиров: в свой эфир приходят зрители, сердечки и
     * комментарии; эфиры друзей живут и через несколько минут кончаются.
     */
    fun liveTick(random: Random) {
        val now = clock()
        val people = Demo.people.filter { it.id !in _state.value.blocked }
        _state.update { s ->
            var mine = s.myLive
            if (mine != null) {
                mine = Lives.viewers(mine, random.nextInt(-1, 4))
                mine = mine.copy(hearts = mine.hearts + random.nextInt(0, 6))
                if (people.isNotEmpty() && random.nextInt(3) == 0) {
                    val p = people[random.nextInt(people.size)]
                    mine = Lives.addComment(mine, LiveComment(nextId++, p.id, p.name, Lives.demoComment(random.nextInt(100)), now, p.hue))
                }
            }
            val lives = s.lives.mapNotNull { l ->
                if (!l.live) return@mapNotNull null
                // Эфир друга идёт 3–10 минут; смотрите — не кончится, пока вы в нём
                if (s.watchingLiveId != l.id && now - l.startedAt > 3 * 60_000 && random.nextInt(8) == 0) return@mapNotNull null
                var x = Lives.viewers(l, random.nextInt(-2, 3)).copy(hearts = l.hearts + random.nextInt(0, 4))
                if (people.isNotEmpty() && random.nextInt(2) == 0) {
                    val p = people[random.nextInt(people.size)]
                    if (p.id != l.hostId) x = Lives.addComment(x, LiveComment(nextId++, p.id, p.name, Lives.demoComment(random.nextInt(100)), now, p.hue))
                }
                x
            }
            s.copy(myLive = mine, lives = lives, watchingLiveId = s.watchingLiveId?.takeIf { id -> mine?.id == id || lives.any { it.id == id } })
        }
    }

    private fun ensureLiveTicker() {
        if (!demoReplies || liveJob?.isActive == true) return
        liveJob = viewModelScope.launch {
            while (_state.value.myLive != null || _state.value.lives.isNotEmpty()) {
                delay(Random.nextLong(1_500, 3_000))
                liveTick(Random.Default)
            }
        }
    }

    // ==================== СЕКРЕТНАЯ И ИСЧЕЗАЮЩАЯ ПЕРЕПИСКА ====================

    /** Хранилище своего ключа шифрования (SecurePrefs); без него — в памяти, для тестов. */
    private var keyStore: ru.wishka.memory.data.auth.KeyValueStore? = null
    private var memoryKey: ChatKeyPair? = null

    /** Последний конверт, который ушёл бы на сервер, — для проверки, что текста в нём нет. */
    @Volatile
    var lastEnvelope: String? = null

    /** Ключи файлов последнего секретного сообщения (attachmentId → FileKey) — для проверки. */
    var lastFileKeys: Map<String, String> = emptyMap()
        private set

    fun attachKeys(store: ru.wishka.memory.data.auth.KeyValueStore) {
        keyStore = store
    }

    /** Свой ключ: открытый показывается в коде безопасности, закрытый не покидает телефон. */
    fun myKeys(): ChatKeyPair {
        val store = keyStore
        return if (store != null) ChatKeys(store).identity(_state.value.me.handle)
        else memoryKey ?: ChatKeyPair.generate().also { memoryKey = it }
    }

    /** Открытый ключ собеседника: с сервера (Directory), у демо-людей — демо-ключ. */
    private fun peerPublic(chat: Chat): ByteArray = Directory.key(chat.peer.id)
        // Человек с сервера без ключа: шифровать нечем (демо-ключ здесь был бы ошибкой)
        ?: if (isServerPerson(chat.peer.id)) error("Нет ключа собеседника") else ChatCrypto.demoPeer(chat.peer.id).publicKey

    /** Ключ переписки (X25519 + HKDF): у обоих телефонов выходит один и тот же. */
    private fun chatKey(chat: Chat): ByteArray = ChatCrypto.sessionKey(myKeys(), peerPublic(chat))

    private fun aad(chat: Chat) = "memory-chat:" + listOf(_state.value.me.handle, chat.peer.handle).sorted().joinToString("|")

    private fun sealFor(chat: Chat, text: String): String? = runCatching {
        ChatCrypto.seal(chatKey(chat), text, aad(chat))
    }.getOrNull()

    /**
     * Проверка конверта перед отправкой: демо-собеседник открывает его своим
     * ключом; настоящий — тем же ключом переписки, что и у нас (X25519 симметричен).
     */
    private fun peerOpen(chat: Chat, envelope: String): String? = runCatching {
        if (Directory.key(chat.peer.id) != null) ChatCrypto.open(chatKey(chat), envelope, aad(chat))
        else ChatCrypto.open(ChatCrypto.sessionKey(ChatCrypto.demoPeer(chat.peer.id), myKeys().publicKey), envelope, aad(chat))
    }.getOrNull()

    /**
     * Пришедшее сообщение: конверт секретной переписки — открыть (тогда и
     * переписка становится секретной: собеседник её включил); ключи файлов
     * в именах вложений — тоже.
     */
    private fun openIncoming(chatId: Int, m: Message): Message {
        val chat = _state.value.chats.firstOrNull { it.id == chatId } ?: return m
        if (chat.isGroup || Directory.key(chat.peer.id) == null) return m
        val key = runCatching { chatKey(chat) }.getOrNull() ?: return m
        val text = if (m.text.isEmpty()) "" else ChatCrypto.open(key, m.text, aad(chat))
        val sealedFiles = m.attachments.any { it.name.startsWith(ru.wishka.memory.data.FileCrypto.SEALED) }
        if (text == null && !sealedFiles) return m
        if (!chat.secret) updateChat(chatId) { it.copy(secret = true) }
        return m.copy(text = text ?: m.text)
    }

    /** Чужие фото и видео сообщения — скачать (секретные — расшифровать) и показать. */
    private fun fetchMessageMedia(chatId: Int, m: Message) {
        val remote = m.attachments.filter { net.isRemote(it.path) }
        if (remote.isEmpty()) return
        val chat = _state.value.chats.firstOrNull { it.id == chatId } ?: return
        viewModelScope.launch {
            for (a in remote) {
                val sealed = a.name.removePrefix(ru.wishka.memory.data.FileCrypto.SEALED).takeIf { a.name.startsWith(ru.wishka.memory.data.FileCrypto.SEALED) }
                val fileKey = sealed?.let { env -> runCatching { ChatCrypto.open(chatKey(chat), env, aad(chat)) }.getOrNull()?.let { ru.wishka.memory.data.FileKey.decode(it) } }
                if (sealed != null && fileKey == null) continue
                val file = runCatching { net.fetchMedia(a.path, fileKey, mediaExt(a)) }.getOrNull() ?: continue
                updateMessage(chatId, m.id) { msg ->
                    msg.copy(attachments = msg.attachments.map { if (it.id == a.id) it.copy(path = file.path, name = if (sealed != null) "" else it.name) else it })
                }
            }
        }
    }

    private fun mediaExt(a: Attachment): String = when (a.kind) {
        MediaKind.Photo -> ".jpg"
        MediaKind.Video -> ".mp4"
        else -> a.name.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length <= 5 }?.let { ".$it" } ?: ""
    }

    /** Включить или выключить сквозное шифрование переписки. */
    fun setSecret(chatId: Int, on: Boolean) {
        // Группы — без сквозного шифрования (ключи на каждого участника — следующий шаг)
        val chat = _state.value.chats.firstOrNull { it.id == chatId } ?: return
        if (chat.isGroup) return
        // Человек с сервера ещё не прислал ключ (старая версия приложения) — шифровать нечем
        if (on && isServerPerson(chat.peer.id) && Directory.key(chat.peer.id) == null) {
            _state.update { it.copy(notice = t("У собеседника пока нет ключа шифрования")) }
            return
        }
        if (on) Telemetry.track(AppEvent.SecretChat)
        updateChat(chatId) { it.copy(secret = on, verified = if (on) it.verified else false) }
    }

    /** Код безопасности переписки — 12 групп по 5 цифр. */
    fun safetyNumber(chatId: Int): String? {
        val chat = _state.value.chats.firstOrNull { it.id == chatId } ?: return null
        return runCatching { ChatCrypto.safetyNumber(myKeys().publicKey, peerPublic(chat)) }.getOrNull()
    }

    /** Человек сверил код с собеседником — отметка «проверено». */
    fun markVerified(chatId: Int, verified: Boolean = true) = updateChat(chatId) { it.copy(verified = verified) }

    /** Исчезающие сообщения: новые исчезнут через ttl. Уже отправленные не меняются. */
    fun setDisappearing(chatId: Int, ttl: Disappear) {
        if (ttl != Disappear.Off) Telemetry.track(AppEvent.Disappearing)
        updateChat(chatId) { it.copy(disappearMs = ttl.ms) }
    }

    /**
     * Убрать исчезнувшие сообщения. Ответ — их вложения (файлы удаляет
     * вызывающий: здесь нет доступа к диску).
     */
    fun sweepExpired(now: Long = clock()): List<Attachment> {
        val gone = mutableListOf<Attachment>()
        _state.update { s ->
            if (s.chats.none { c -> c.messages.any { it.expiresAt in 1..now } }) return@update s
            s.copy(chats = s.chats.map { c ->
                val (dead, alive) = c.messages.partition { it.expiresAt in 1..now }
                if (dead.isEmpty()) c else {
                    dead.forEach { gone += it.attachments }
                    c.copy(messages = alive, pinnedId = c.pinnedId?.takeIf { id -> alive.any { it.id == id } })
                }
            })
        }
        return gone
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
            Message(
                nextId++, fromMe = true, text = original.text, time = now(), attachments = original.attachments, forwardedFrom = author,
                // Пересланный опрос — новый, с чистыми голосами; место — как есть
                poll = original.poll?.let { p -> p.copy(id = nextId++, options = p.options.map { it.copy(votes = 0) }, mine = emptySet(), closed = false) },
                place = original.place,
                product = original.product,
            ),
        )
        openChat(toChatId)
    }

    /** Какие сообщения удалены «у всех» — для будущей отправки на сервер. */
    val deletedForEveryone = mutableSetOf<Long>()

    private fun append(chatId: Int, message: Message) {
        _state.update { s ->
            val chat = s.chats.firstOrNull { it.id == chatId } ?: return@update s
            val reading = s.openChatId == chatId
            // Настройки переписки — на каждое новое сообщение: шифрование и срок жизни
            val stamped = message.copy(
                secret = message.secret || chat.secret,
                expiresAt = if (message.expiresAt == 0L && chat.disappearMs > 0) clock() + chat.disappearMs else message.expiresAt,
            )
            val updated = chat.copy(
                messages = chat.messages + stamped,
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
        Telemetry.track(AppEvent.PrismPublished)
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
        // Бесплатно — три плейлиста
        if (_state.value.playlists.size >= Premium.FREE_PLAYLISTS && !require(PremiumFeature.Playlists)) return null
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

    /**
     * Закрепить свою запись в витрине профиля или открепить. Бесплатно —
     * три записи (четвёртая открывает шторку Memory+), в Memory+ — шесть
     * (седьмая вытесняет самую давнюю).
     */
    fun toggleProfilePin(postId: Long) {
        val s0 = _state.value
        val adding = postId !in s0.profilePins && s0.posts.any { it.id == postId && it.author.id == s0.me.id }
        if (adding && s0.profilePins.size >= Premium.FREE_PINS && !require(PremiumFeature.Pins)) return
        val limit = if (has(PremiumFeature.Pins)) Premium.PLUS_PINS else MAX_PINS
        _state.update { s ->
            when {
                postId in s.profilePins -> s.copy(profilePins = s.profilePins - postId)
                s.posts.none { it.id == postId && it.author.id == s.me.id } -> s
                else -> s.copy(profilePins = (listOf(postId) + s.profilePins).take(limit))
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

    // ==================== ПРАВКА ЗАПИСИ ====================

    /** Править можно только свою запись — в ленте или в группе. */
    fun startEdit(postId: Long) {
        val post = _state.value.findPost(postId) ?: return
        if (post.author.id != _state.value.me.id) return
        _state.update { it.copy(editingPostId = postId) }
    }

    fun cancelEdit() {
        _state.update { it.copy(editingPostId = null) }
    }

    /**
     * Сохранить правку: новый текст и оставшиеся вложения (убрать можно,
     * добавить — новой записью). Пустая запись без вложений, чек-ина и
     * опроса не сохраняется. Ответ — текст ошибки или null.
     * Вложения, которые убрали, возвращаются — их файлы удаляет экран.
     */
    fun saveEdit(postId: Long, text: String, media: List<Attachment>): String? {
        val s0 = _state.value
        val post = s0.findPost(postId) ?: return t("Запись не найдена")
        if (post.author.id != s0.me.id) return t("Править можно только свои записи")
        val clean = text.trim().take(2000)
        val kept = post.media.filter { a -> media.any { it.id == a.id } }
        if (clean.isEmpty() && kept.isEmpty() && post.checkIn == null && post.poll == null) return t("Запись не может быть пустой")
        if (clean == post.text && kept.size == post.media.size) {
            cancelEdit()
            return null
        }
        val edited = post.copy(text = clean, media = kept, editedAt = clock())
        Telemetry.track(AppEvent.PostEdited)
        _state.update { s ->
            s.copy(
                posts = s.posts.map { if (it.id == postId) edited else it },
                groups = s.groups.map { g -> if (g.posts.none { it.id == postId }) g else g.copy(posts = g.posts.map { if (it.id == postId) edited else it }) },
                editingPostId = null,
            )
        }
        return null
    }

    // ==================== ССЫЛКИ ====================

    /** Открыть ссылку Memory: запись, профиль, PRISM, группу или тег. false — такого на телефоне нет. */
    fun openLink(target: LinkTarget): Boolean {
        Telemetry.track(AppEvent.LinkOpened)
        val s = _state.value
        return when (target) {
            is LinkTarget.PostLink -> {
                val post = s.findPost(target.id) ?: return false
                if (post.author.id in s.blocked || !s.canSee(post.author)) return false
                _state.update { it.copy(openPostId = target.id, activityOpen = false, searchOpen = false, openChatId = null) }
                true
            }
            is LinkTarget.Profile -> {
                val h = target.handle.lowercase()
                if (h != s.me.handle.lowercase() && Demo.people.none { it.handle.lowercase() == h }) return false
                openHandle(h)
                true
            }
            is LinkTarget.PrismLink -> {
                if (s.prisms.none { it.id == target.id }) return false
                selectTab(Tab.Prism)
                _state.update { it.copy(prismStartId = target.id) }
                true
            }
            is LinkTarget.GroupLink -> {
                if (s.groups.none { it.id == target.id }) return false
                selectTab(Tab.Groups)
                openGroup(target.id)
                true
            }
            is LinkTarget.TagLink -> { openTag(target.tag); true }
        }
    }

    fun closePost() {
        _state.update { it.copy(openPostId = null) }
    }

    // ==================== ГРУППЫ ====================

    /** Группа поверх вкладки; из раздела профиля «назад» вернёт в него. */
    fun openGroup(groupId: Int) {
        _state.update {
            it.copy(
                tab = if (it.tab == Tab.Profile && it.profileSection != null) it.tab else Tab.Groups,
                openGroupId = groupId,
                groupSettingsOpen = false,
                openProduct = it.openProduct?.takeIf { p -> p.first == groupId },
                openPersonId = null,
            )
        }
    }

    fun closeGroup() {
        _state.update { it.copy(openGroupId = null, groupSettingsOpen = false, openProduct = null) }
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

    // ---------- Витрина, сведения и оформление группы ----------

    /** Подробное описание, сайт и контакты своей группы. Ссылка без схемы получает https://. */
    fun updateGroupInfo(groupId: Int, info: GroupInfo) {
        var clean = GroupInfo(
            about = info.about.trim().take(2000),
            website = normalizeUrl(info.website).take(200),
            phone = info.phone.trim().take(40),
            email = info.email.trim().take(80),
            address = info.address.trim().take(160),
            hours = info.hours.trim().take(80),
        )
        // Телефон, почта, адрес и часы — в Бизнесе. Без него прежние контакты остаются как были
        if (!has(PremiumFeature.GroupContacts)) {
            val old = _state.value.groups.firstOrNull { it.id == groupId }?.info ?: GroupInfo()
            val contacts = GroupInfo(phone = clean.phone, email = clean.email, address = clean.address, hours = clean.hours)
            if (contacts != GroupInfo(phone = old.phone, email = old.email, address = old.address, hours = old.hours)) {
                require(PremiumFeature.GroupContacts)
            }
            clean = clean.copy(phone = old.phone, email = old.email, address = old.address, hours = old.hours)
        }
        updateOwnGroup(groupId) { it.copy(info = clean) }
    }

    /** Фон страницы своей группы; null — как везде. */
    fun setGroupBackdrop(groupId: Int, backdrop: Backdrop?) {
        // Фон страницы группы — Бизнес; убрать фон можно всегда
        // Тот же фон, что уже стоит (меняют только затемнение), — можно и без подписки
        val cur = _state.value.groups.firstOrNull { it.id == groupId }?.backdrop
        if (backdrop != null && !(backdrop.style == cur?.style && backdrop.photo == cur.photo) && !require(PremiumFeature.GroupBackdrop)) return
        val old = _state.value.groups.firstOrNull { it.id == groupId }?.backdrop?.photo.orEmpty()
        updateOwnGroup(groupId) { it.copy(backdrop = backdrop) }
        // Своё фото фона, которое больше не стоит, — удалить копию
        if (old.isNotEmpty() && old != backdrop?.photo && old !in backdropPhotos(_state.value)) runCatching { java.io.File(old).delete() }
    }

    fun setGroupAccent(groupId: Int, accent: GroupAccent) {
        updateOwnGroup(groupId) { it.copy(accent = accent.id) }
    }

    /**
     * Добавить или изменить товар своей группы. Ответ — текст ошибки или null.
     * Новый товар — первым в витрине.
     */
    fun saveProduct(groupId: Int, product: Product): String? {
        val title = product.title.trim()
        if (title.isEmpty()) return t("Добавьте название товара")
        if (product.price < 0) return t("Цена не может быть отрицательной")
        // Бесплатно — до 10 товаров; изменить уже добавленные можно всегда
        val shop = _state.value.groups.firstOrNull { it.id == groupId }?.shop.orEmpty()
        if (product.id == 0L && shop.size >= Premium.FREE_PRODUCTS && !require(PremiumFeature.ShopUnlimited)) {
            return tf("Бесплатно — до %s товаров. Больше — в Memory+ Бизнес", Premium.FREE_PRODUCTS)
        }
        val clean = product.copy(
            id = if (product.id == 0L) nextId++ else product.id,
            title = title.take(80),
            description = product.description.trim().take(2000),
            category = product.category.trim().take(40),
            url = normalizeUrl(product.url).take(300),
            photos = product.photos.take(MAX_PRODUCT_PHOTOS),
            seed = if (product.seed == 0) (nextId % 97).toInt() + 1 else product.seed,
        )
        updateOwnGroup(groupId) { g ->
            if (g.shop.any { it.id == clean.id }) g.copy(shop = g.shop.map { if (it.id == clean.id) clean else it })
            else g.copy(shop = listOf(clean) + g.shop)
        }
        return null
    }

    fun deleteProduct(groupId: Int, productId: Long) {
        updateOwnGroup(groupId) { g -> g.copy(shop = g.shop.filterNot { it.id == productId }) }
    }

    /** Переставить товар на одно место выше (-1) или ниже (+1). */
    fun moveProduct(groupId: Int, productId: Long, delta: Int) {
        updateOwnGroup(groupId) { g ->
            val i = g.shop.indexOfFirst { it.id == productId }
            val j = i + delta
            if (i < 0 || j !in g.shop.indices) g
            else g.copy(shop = g.shop.toMutableList().apply { add(j, removeAt(i)) })
        }
    }

    fun findProduct(groupId: Int, productId: Long): Product? =
        _state.value.groups.firstOrNull { it.id == groupId }?.shop?.firstOrNull { it.id == productId }

    /**
     * «Написать продавцу»: переписка с создателем группы и сообщение с
     * товаром. Своя группа — некому писать (null). Ответ — номер переписки.
     */
    fun askSeller(groupId: Int, productId: Long, text: String = ""): Int? {
        val s = _state.value
        val group = s.groups.firstOrNull { it.id == groupId } ?: return null
        if (group.mine) return null
        val product = group.shop.firstOrNull { it.id == productId } ?: return null
        val seller = Demo.person(group.ownerId) ?: return null
        if (seller.id in s.blocked) return null
        val chatId = s.chats.firstOrNull { it.peer.id == seller.id }?.id ?: run {
            val id = (s.chats.maxOfOrNull { it.id } ?: 0) + 1
            _state.update { st -> st.copy(chats = listOf(Chat(id, seller, emptyList())) + st.chats) }
            id
        }
        val ref = ProductRef(groupId, product.id, product.title, product.price, product.photos.firstOrNull()?.path.orEmpty(), product.seed)
        val question = text.trim().ifEmpty { tf("Здравствуйте! Интересует «%s». Он в наличии?", product.title) }
        append(chatId, Message(nextId++, fromMe = true, text = question, time = now(), product = ref))
        openChat(chatId)
        if (demoReplies) later(2_500, 6_000) {
            val reply = if (product.inStock) t("Здравствуйте! Да, в наличии. Могу отправить сегодня — напишите город и как удобнее получить.")
            else t("Здравствуйте! Сейчас нет в наличии, сделаю под заказ за неделю. Подходит?")
            append(chatId, Message(nextId++, fromMe = false, text = reply, time = now()))
            _state.value.chats.firstOrNull { it.id == chatId }?.let { chat ->
                if (!chat.muted && _state.value.openChatId != chatId) notify(Nudges.message(chat.peer, reply, chatId))
            }
        }
        return chatId
    }

    /** Открыть товар из сообщения: группа с витриной и сам товар. */
    fun openProduct(groupId: Int, productId: Long): Boolean {
        // Товар убрали — открывать нечего (иначе «назад» съест нажатие впустую)
        if (findProduct(groupId, productId) == null) return false
        _state.update {
            it.copy(
                openChatId = null, openPersonId = null, personSection = null, returnToChatId = null,
                tab = Tab.Groups, openGroupId = groupId, groupSettingsOpen = false, openProduct = groupId to productId,
            )
        }
        return true
    }

    fun closeProduct() {
        _state.update { it.copy(openProduct = null) }
    }

    fun showProduct(groupId: Int, productId: Long) {
        _state.update { it.copy(openProduct = groupId to productId) }
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
    fun postInGroup(groupId: Int, text: String, media: List<Attachment> = emptyList(), poll: PollDraft? = null) {
        val trimmed = text.trim()
        val built = poll?.build(nextId++)
        if (trimmed.isEmpty() && media.isEmpty() && built == null) return
        _state.update { s ->
            s.copy(groups = s.groups.map {
                if (it.id != groupId || !it.joined || (it.onlyOwnerPosts && !it.mine)) it
                else it.copy(posts = listOf(Post(nextId++, s.me, trimmed, 0, media = media.take(MAX_MEDIA), createdAt = clock(), poll = built)) + it.posts)
            })
        }
        media.filter { it.isGif }.forEach(::rememberGif)
    }

    // ==================== ОПРОСЫ ====================

    /** Изменить опрос записи — в ленте или в группе. */
    /** Своя ли запись — у своих опросов есть «Завершить». */
    fun isMine(post: Post): Boolean = post.author.id == _state.value.me.id

    private fun updatePostPoll(postId: Long, change: (Poll) -> Poll) {
        _state.update { s ->
            fun Post.fix() = if (id == postId && poll != null) copy(poll = change(poll)) else this
            s.copy(posts = s.posts.map { it.fix() }, groups = s.groups.map { g -> g.copy(posts = g.posts.map { it.fix() }) })
        }
    }

    private fun updateMessagePoll(chatId: Int, pollId: Long, change: (Poll) -> Poll) {
        _state.update { s ->
            s.copy(chats = s.chats.map { c ->
                if (c.id != chatId) c
                else c.copy(messages = c.messages.map { m -> if (m.poll?.id == pollId) m.copy(poll = change(m.poll)) else m })
            })
        }
    }

    /** Голос в опросе записи (ленты или группы). */
    fun votePost(postId: Long, option: Int) = updatePostPoll(postId) { it.vote(option) }

    /** Голос в опросе сообщения. */
    fun voteMessage(chatId: Int, pollId: Long, option: Int) = updateMessagePoll(chatId, pollId) { it.vote(option) }

    /** Закрыть свой опрос: голосовать больше нельзя, итоги видны всем. */
    fun closePostPoll(postId: Long) = updatePostPoll(postId) { it.close() }

    fun closeMessagePoll(chatId: Int, pollId: Long) = updateMessagePoll(chatId, pollId) { it.close() }

    // ==================== ГИФКИ, КОЛЛЕКЦИИ СТИКЕРОВ, ФОНЫ ====================

    /** Отправленная гифка — в «Недавние» (до 30, новые первыми). */
    fun rememberGif(gif: Attachment) {
        if (!gif.isGif) return
        _state.update { s ->
            val name = gif.name.ifEmpty { gif.path.substringAfterLast('/') }
            s.copy(recentGifs = (listOf(gif) + s.recentGifs.filterNot { it.name.isNotEmpty() && it.name == name }).take(30))
        }
    }

    /** Новая коллекция стикеров; null — пустое имя или такая уже есть. */
    fun createStickerPack(name: String): StickerPack? {
        val n = name.trim().take(30)
        if (n.isEmpty() || _state.value.stickerPacks.any { it.name.equals(n, ignoreCase = true) }) return null
        // Бесплатно — две коллекции
        if (_state.value.stickerPacks.size >= Premium.FREE_PACKS && !require(PremiumFeature.StickerPacks)) return null
        val pack = StickerPack("pack-" + UUID.randomUUID().toString().take(8), n)
        _state.update { it.copy(stickerPacks = it.stickerPacks + pack) }
        return pack
    }

    fun renameStickerPack(id: String, name: String) {
        val n = name.trim().take(30)
        if (n.isEmpty()) return
        _state.update { s -> s.copy(stickerPacks = s.stickerPacks.map { if (it.id == id) it.copy(name = n) else it }) }
    }

    /** Удалить коллекцию: стикеры остаются, просто без коллекции. */
    fun deleteStickerPack(id: String) {
        _state.update { s ->
            s.copy(stickerPacks = s.stickerPacks.filterNot { it.id == id }, stickers = s.stickers.map { if (it.packId == id) it.copy(packId = "") else it })
        }
    }

    fun moveSticker(stickerId: String, packId: String) {
        _state.update { s -> s.copy(stickers = s.stickers.map { if (it.id == stickerId) it.copy(packId = packId) else it }) }
    }

    /** Общий фон переписок. */
    fun setBackdrop(backdrop: Backdrop) {
        // Бесплатно — четыре фона; остальные и своё фото — Memory+
        // Тот же фон, что уже стоит (меняют только затемнение), — можно и без подписки
        val cur = _state.value.backdrop
        val same = backdrop.style == cur.style && backdrop.photo == cur.photo
        if (backdrop.kind !in Premium.FREE_BACKDROPS && !same && !require(PremiumFeature.Backdrops)) return
        val before = backdropPhotos(_state.value)
        _state.update { it.copy(backdrop = backdrop) }
        dropBackdropPhotos(before)
    }

    /** Свой фон у переписки; null — как у всех. */
    fun setChatBackdrop(chatId: Int, backdrop: Backdrop?) {
        // Свой фон у переписки — Memory+; вернуть «как у всех» можно всегда
        // Тот же фон, что уже стоит у переписки (меняют только затемнение), — можно и без подписки
        val cur = _state.value.chats.firstOrNull { it.id == chatId }?.backdrop
        if (backdrop != null && !(backdrop.style == cur?.style && backdrop.photo == cur.photo) && !require(PremiumFeature.ChatBackdrop)) return
        val before = backdropPhotos(_state.value)
        _state.update { s -> s.copy(chats = s.chats.map { if (it.id == chatId) it.copy(backdrop = backdrop) else it }) }
        dropBackdropPhotos(before)
    }

    private fun backdropPhotos(s: UiState): Set<String> =
        (listOf(s.backdrop) + s.chats.mapNotNull { it.backdrop }).map { it.photo }.filter { it.isNotEmpty() }.toSet()

    // ==================== ЖАЛОБЫ И ДОКУМЕНТЫ ====================

    /** Открыть выбор причины жалобы. */
    fun startReport(target: ReportTarget) {
        _state.update { it.copy(reporting = target) }
    }

    fun reportPost(post: Post) = startReport(ReportTarget(ReportKind.Post, post.id, post.author.id, post.author.name, preview = post.text.take(80)))

    fun reportPrism(prism: Prism) = startReport(ReportTarget(ReportKind.Prism, prism.id, prism.author.id, prism.author.name, preview = prism.caption.take(80)))

    fun reportComment(comment: Comment) = startReport(ReportTarget(ReportKind.Comment, comment.id, comment.author.id, comment.author.name, preview = comment.text.take(80)))

    fun reportMessage(chatId: Int, message: Message) {
        val peer = _state.value.chats.firstOrNull { it.id == chatId }?.peer ?: return
        if (message.fromMe) return
        reportedSentAt[message.id] = message.sentAt
        startReport(ReportTarget(ReportKind.Message, message.id, peer.id, peer.name, chatId, messagePreview(message).take(80)))
    }

    fun reportPerson(user: User) = startReport(ReportTarget(ReportKind.Person, user.id.toLong(), user.id, user.name, preview = "@" + user.handle))

    fun cancelReport() {
        _state.update { it.copy(reporting = null) }
    }

    /**
     * Жалоба: в очередь модераторам и сразу скрыть у себя то, на что
     * пожаловались. block — заодно заблокировать автора.
     */
    fun report(target: ReportTarget, reason: ReportReason, note: String = "", block: Boolean = false) {
        val entry = Report(nextId++, target, reason, note.trim().take(500), clock())
        _state.update { s ->
            val next = s.copy(reports = (s.reports + entry).takeLast(200), reporting = null)
            when (target.kind) {
                ReportKind.Comment -> next.copy(reportedComments = next.reportedComments + target.id)
                ReportKind.Post -> next.copy(
                    olderFeed = next.olderFeed.filterNot { it.id == target.id },
                    explore = next.explore.filterNot { it.id == target.id },
                    posts = next.posts.filterNot { it.id == target.id },
                    groups = next.groups.map { g -> g.copy(posts = g.posts.filterNot { it.id == target.id }) },
                    commentsPostId = if (next.commentsPostId == target.id) null else next.commentsPostId,
                )
                ReportKind.Prism -> next.copy(
                    prisms = next.prisms.filterNot { it.id == target.id },
                    commentsPrismId = if (next.commentsPrismId == target.id) null else next.commentsPrismId,
                )
                ReportKind.Message -> next.copy(chats = next.chats.map { c ->
                    if (c.id != target.chatId) c
                    else c.copy(messages = c.messages.filterNot { it.id == target.id }, pinnedId = if (c.pinnedId == target.id) null else c.pinnedId)
                })
                ReportKind.Person -> next
            }
        }
        if (target.kind == ReportKind.Post) {
            feedPager.drop(target.id)
            explorePager.drop(target.id)
        }
        sendReport(target, reason, note)
        if (block && target.authorId != 0 && target.authorId != _state.value.me.id) {
            block(target.authorId)
        }
    }

    /** Время сервера у сообщений, на которые жалуются (сообщение из переписки уже убрано). */
    private val reportedSentAt = HashMap<Long, Long>()

    /**
     * Жалоба — модераторам на сервер (только на то, что пришло с сервера:
     * на демо-людей жаловаться некому). У записей и комментариев сервер знает
     * их номер; у сообщений и PRISM модератор увидит preview.
     */
    private fun sendReport(target: ReportTarget, reason: ReportReason, note: String) {
        if (!serverMode || !isServerPerson(target.authorId)) return
        val kind = when (target.kind) {
            ReportKind.Post -> if (target.id >= SERVER_ID_BASE) "post" else return
            ReportKind.Comment -> "comment"
            ReportKind.Prism -> "prism"
            ReportKind.Message -> "message"
            ReportKind.Person -> "person"
        }
        val body = org.json.JSONObject().put("kind", kind).put("target", target.id).put("author", target.authorId)
            .put("reason", reason.id).put("note", note.trim().take(500)).put("preview", target.preview.take(300))
        // Сообщение сервер найдёт сам — по переписке и времени сервера; автор и текст будут его, а не со слов
        if (target.kind == ReportKind.Message) {
            body.put("chat", target.chatId).put("sentAt", reportedSentAt[target.id] ?: 0L)
        }
        runCatching { net.queueCall("POST", "/v1/reports", body) }
        kickNet()
    }

    fun openLegal(doc: LegalDoc) {
        _state.update { it.copy(legalDoc = doc) }
    }

    fun closeLegal() {
        _state.update { it.copy(legalDoc = null) }
    }

    /** Своё фото фона, которое больше нигде не стоит, — удалить копию. */
    private fun dropBackdropPhotos(before: Set<String>) {
        val now = backdropPhotos(_state.value)
        (before - now).forEach { runCatching { java.io.File(it).delete() } }
    }

    /** Поделиться местом в переписке. */
    fun sendPlace(chatId: Int, at: LatLon, title: String = "") {
        send(chatId, "", place = SharedPlace(at.lat, at.lon, title.trim().take(80)))
    }

    /**
     * Ещё одна демо-жизнь: голоса в ваших опросах, новый PRISM друга,
     * место от друга в переписке. Голоса приходят только в открытые опросы.
     */
    fun socialEvent(random: Random) {
        val s = _state.value
        val friends = Demo.people.filter { it.id !in s.blocked }
        if (friends.isEmpty()) return
        val myPolls = (s.posts + s.groups.flatMap { it.posts }).filter { it.author.id == s.me.id && it.poll?.closed == false }
        when (if (myPolls.isNotEmpty()) random.nextInt(3) else 1 + random.nextInt(2)) {
            0 -> {
                val post = myPolls[random.nextInt(myPolls.size)]
                val poll = post.poll ?: return
                val added = 1 + random.nextInt(4)
                val votes = (0 until added).groupingBy { random.nextInt(poll.options.size) }.eachCount()
                updatePostPoll(post.id) { it.addVotes(votes) }
                record(ActivityKind.PollVotes, null, post.id, poll.question, count = added)
                notify(Nudges.pollVotes(poll.question, added, post.id, s.groups.firstOrNull { g -> g.posts.any { it.id == post.id } }?.id))
            }
            1 -> {
                val friend = friends[random.nextInt(friends.size)]
                val captions = listOf("Огни ночного города", "Закат над рекой", "Первый снег", "Звёзды над крышей", "Кофе и дождь")
                val prism = Prism(nextId++, friend, t(captions[random.nextInt(captions.size)]), tf("Оригинальный звук · %s", friend.name), random.nextInt(1000), random.nextInt(40), 0, createdAt = clock())
                _state.update { it.copy(prisms = listOf(prism) + it.prisms) }
                notify(Nudges.prismPublished(friend, prism))
            }
            else -> {
                val chat = s.chats.filter { it.peer.id !in s.blocked && !it.muted }.randomOrNull(random) ?: return
                val here = s.together.anchor.offset(random.nextDouble(-900.0, 900.0), random.nextDouble(-900.0, 900.0))
                append(chat.id, Message(nextId++, fromMe = false, text = t("Я тут, подходи"), time = now(), place = SharedPlace(here.lat, here.lon, t("Я здесь"))))
                if (s.openChatId != chat.id) notify(Nudges.placeShared(chat.peer, chat.id))
            }
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
        detachNet()
        lifeJob?.cancel()
        lifeJob = null
        liveJob?.cancel()
        liveJob = null
        if (_state.value.myLive != null || _state.value.watchingLiveId != null) liveTransport.leave()
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
        pushProfile()
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

    /** Приватность. Заявки в друзья от неё не зависят: открыли профиль — они всё так же ждут ответа. */
    fun setPrivacy(privacy: Privacy) {
        _state.update { it.copy(privacy = privacy) }
        pushProfile()
    }

    /**
     * В чёрный список: записи, комментарии и сообщения человека больше не
     * видны, он уходит из друзей, заявки с обеих сторон снимаются. Своя
     * страница не блокируется.
     */
    fun block(userId: Int) {
        if (userId == _state.value.me.id) return
        acceptJobs.remove(userId)?.cancel()
        _state.update { s ->
            s.copy(
                blocked = s.blocked + userId, following = s.following - userId,
                followRequests = s.followRequests - userId, sentRequests = s.sentRequests - userId,
                closeFriends = s.closeFriends - userId,
            )
        }
        // Человек с сервера: блокировка на сервере — подписки в обе стороны снимаются,
        // он больше не видит ваши записи, истории и комментарии и не может написать
        if (isServerPerson(userId)) {
            runCatching { net.queueCall("PUT", "/v1/users/$userId/block", coalesce = "block:$userId") }
            pushCloseFriends()
            kickNet()
        }
    }

    fun unblock(userId: Int) {
        _state.update { s -> s.copy(blocked = s.blocked - userId) }
        if (isServerPerson(userId)) {
            runCatching { net.queueCall("DELETE", "/v1/users/$userId/block", coalesce = "block:$userId") }
            kickNet()
        }
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
            NudgeTarget.Person -> {
                // Друг в эфире — сразу в эфир
                val live = _state.value.visibleLives.firstOrNull { it.hostId == nudge.targetId.toInt() }
                if (live != null) watchLive(live.id) else openPerson(nudge.targetId.toInt())
            }
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
            NudgeTarget.Prism -> {
                selectTab(Tab.Prism)
                if (_state.value.prisms.any { it.id == nudge.targetId }) _state.update { it.copy(prismStartId = nudge.targetId) }
            }
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
        // Бесплатно — текущий и прошлый период; дальше в прошлое — Memory+
        if (back > Premium.FREE_RECAP_BACK && back > _state.value.recapBack && !require(PremiumFeature.RecapHistory)) return
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
     * приходят люди, вашим записям ставят «нравится», приходят
     * заявки в друзья. Каждое событие — с персональным
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
                socialEvent(Random.Default)
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
        val friends = s.friends.filter { it.id !in s.blocked }
        val joined = s.groups.filter { it.joined && !it.muted }
        val mine = s.myPosts
        val requesters = Demo.people.filter { p ->
            p.id !in s.blocked && p.id !in s.followRequests && p.id !in s.following && p.id !in s.sentRequests && p.id !in declined
        }
        val options = buildList {
            if (friends.isNotEmpty()) repeat(4) { add(0) }
            if (joined.isNotEmpty()) repeat(2) { add(1) }
            if (mine.isNotEmpty()) repeat(2) { add(2) }
            if (requesters.isNotEmpty()) add(3)
            if (friends.isNotEmpty()) add(4)
            if (friends.isNotEmpty()) repeat(2) { add(5) }
            if (mine.isNotEmpty() && friends.isNotEmpty()) add(6)
            if (friends.isNotEmpty() && s.call == null) add(7)
            // Пишет незнакомый — в «Запросы»; пишут в группе
            if (requesters.isNotEmpty() && net.demo != null) add(8)
            if (s.chats.any { it.isGroup } && net.demo != null) repeat(2) { add(9) }
        }
        if (options.isEmpty()) return
        when (options[random.nextInt(options.size)]) {
            0 -> friendCheckIn(friends[random.nextInt(friends.size)], random)
            1 -> {
                val group = joined[random.nextInt(joined.size)]
                val name = Demo.newcomers[random.nextInt(Demo.newcomers.size)]
                val person = User(500 + random.nextInt(1000), name, name.lowercase().replace(' ', '.'), hue = random.nextFloat())
                _state.update { st -> st.copy(groups = st.groups.map { if (it.id == group.id) it.copy(members = it.members + 1) else it }) }
                if (group.mine) record(ActivityKind.GroupJoin, person, group.id.toLong(), group.name)
                notify(Nudges.groupMember(group, person))
            }
            2 -> {
                val post = mine[random.nextInt(mine.size)]
                val fans = Demo.people.filter { it.id !in s.blocked }
                val fan = fans[random.nextInt(fans.size)]
                if (random.nextInt(3) == 0) {
                    // Иногда — не «нравится», а комментарий, порой с упоминанием
                    demoComment(post, fan, random)
                } else {
                    _state.update { st -> st.copy(posts = st.posts.map { if (it.id == post.id) it.copy(likes = it.likes + 1) else it }) }
                    record(ActivityKind.Like, fan, post.id, post.text)
                    notify(Nudges.liked(fan, post))
                }
            }
            3 -> receiveFriendRequest(requesters[random.nextInt(requesters.size)].id)
            4 -> friendGoesLive(friends[random.nextInt(friends.size)], random)
            5 -> storyEvent(friends[random.nextInt(friends.size)], random)
            6 -> {
                // Друг репостнул вашу запись
                val post = mine[random.nextInt(mine.size)]
                val fan = friends[random.nextInt(friends.size)]
                _state.update { st -> st.copy(posts = st.posts.map { if (it.id == post.id) it.copy(reposts = it.reposts + 1) else it }) }
                record(ActivityKind.Repost, fan, post.id, post.text)
            }
            7 -> incomingCall(friends[random.nextInt(friends.size)].id, video = random.nextBoolean())
            8 -> {
                val stranger = requesters[random.nextInt(requesters.size)]
                val text = Demo.strangerTexts[random.nextInt(Demo.strangerTexts.size)]
                demoDeliver(STRANGER_CHAT_BASE + stranger.id, stranger.id, text, 0)
            }
            else -> {
                val group = s.chats.filter { it.isGroup }.let { it[random.nextInt(it.size)] }
                val others = group.group!!.memberIds.filter { it != s.me.id && it !in s.blocked }
                if (others.isEmpty()) return
                val who = others[random.nextInt(others.size)]
                net.demo?.realtime?.schedule(RtEvent.Typing(group.id, who, true), 0)
                demoDeliver(group.id, who, Demo.replies[random.nextInt(Demo.replies.size)], 2_500)
            }
        }
    }

    /** Демо: сервер «присылает» сообщение по живому каналу. */
    private fun demoDeliver(chatId: Int, authorId: Int, text: String, afterMs: Long) {
        val d = net.demo ?: return
        val m = Message(0, fromMe = false, text = text, time = now(), authorId = authorId)
        d.deliver(chatId, StateStore.Codec(_state.value.me).messageJson(m), afterMs)
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
        val s = _state.value
        // Маршрут, который открывают километры, до них закрыт
        if (s.together.missions.firstOrNull { it.id == missionId }?.locked(s.movedKm) == true) return
        together { it.addFind(missionId, MissionFind(nextId++, meId(), note.trim().take(200), at, photo, clock())) }
    }

    /** Своя миссия района — право за пять находок. null — создана; иначе что не так. */
    fun createMission(emoji: String, title: String, description: String, target: Int): String? {
        if (!_state.value.unlocked(Unlock.OwnMission)) return Unlock.OwnMission.how
        if (title.isBlank()) return t("Назовите миссию")
        val ok = together { it.createMission(Mission("own-" + nextId++, emoji, title.trim().take(60), description.trim().take(300), target, authorId = meId())) }
        return if (ok) null else t("Не получилось — попробуйте другое название")
    }

    /** Своя общая цель — право за вклад в две чужие. null — создана. */
    fun createGoal(emoji: String, title: String, description: String, unit: String, target: Double): String? {
        if (!_state.value.unlocked(Unlock.OwnGoal)) return Unlock.OwnGoal.how
        if (title.isBlank() || unit.isBlank()) return t("Укажите название и единицу")
        if (target <= 0) return t("Сколько нужно собрать?")
        val ok = together {
            it.createGoal(CommunityGoal("own-" + nextId++, emoji, title.trim().take(60), description.trim().take(300), unit.trim().take(12), target, 0.0, 1, GoalMetric.Other, authorId = meId()))
        }
        return if (ok) null else t("Не получилось — попробуйте другое название")
    }

    // ---- Клады ----

    fun hideCache(title: String, hint: String, difficulty: Int, size: String, at: LatLon): Boolean {
        // Прятать — право: сначала найдите три чужих (Unlock.HideCache)
        if (!_state.value.unlocked(Unlock.HideCache)) return false
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
            it.checkInTemplates, it.followRequests, it.sentRequests, it.recapNotified,
            it.together, it.shelfMarks, it.dismissedRecs, it.growth, it.invitedBy, it.memorySeen,
            it.stickerPacks, it.backdrop, it.recentGifs, it.reports,
            it.guideRead, it.guideHidden, it.subscription,
            activity = it.activity, activitySeen = it.activitySeen,
            searchHistory = it.searchHistory,
            stories = it.stories, seenStories = it.seenStories, closeFriends = it.closeFriends,
            highlights = it.highlights, callLog = it.callLog,
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
                // Демо-группы из старого сохранения получают витрину и сведения из новых демо-данных
                groups = snapshot.groups.map { g ->
                    val demo = Demo.groups.firstOrNull { it.id == g.id }
                    if (demo != null && !g.mine && g.ownerId == 0 && demo.ownerId != 0)
                        g.copy(ownerId = demo.ownerId, info = demo.info, shop = demo.shop, accent = demo.accent, backdrop = demo.backdrop)
                    else g
                },
                prisms = snapshot.prisms,
                // Старые «одобренные подписчики» StateStore уже слил в друзей
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
                followRequests = snapshot.followRequests.filter { it !in snapshot.following && it != s.me.id },
                sentRequests = snapshot.sentRequests - snapshot.following - s.me.id,
                recapNotified = snapshot.recapNotified,
                together = snapshot.together?.withNewDemo(s.together) ?: s.together,
                shelfMarks = snapshot.shelfMarks + s.shelfMarks.filter { m -> snapshot.shelfMarks.none { it.key == m.key } },
                dismissedRecs = snapshot.dismissedRecs,
                // Повторное восстановление (сменили имя — аккаунт перечитан) не удваивает журнал
                growth = (snapshot.growth + s.growth.filter { it !in snapshot.growth }).sortedBy { it.at }.takeLast(GROWTH_LOG_LIMIT),
                invitedBy = snapshot.invitedBy ?: s.invitedBy,
                memorySeen = snapshot.memorySeen,
                stickerPacks = snapshot.stickerPacks,
                backdrop = snapshot.backdrop,
                recentGifs = snapshot.recentGifs,
                reports = snapshot.reports,
                guideRead = snapshot.guideRead intersect Guide.keys,
                guideHidden = snapshot.guideHidden,
                subscription = snapshot.subscription,
                activity = snapshot.activity,
                activitySeen = snapshot.activitySeen,
                searchHistory = snapshot.searchHistory,
                stories = snapshot.stories,
                seenStories = snapshot.seenStories,
                closeFriends = snapshot.closeFriends,
                highlights = snapshot.highlights,
                callLog = snapshot.callLog,
            ).withMe(s.me)
        }
        refreshPlan()
    }

    private fun now(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

    companion object {
        /** С этого номера — записи, комментарии и истории сервера (у телефона номера меньше). */
        const val SERVER_ID_BASE = 10_000_000L

        /** Служебные строки переписки (не человек): «Вы создали группу». */
        const val SYSTEM_AUTHOR = -1
        /** Демо: номера переписок, которые начинают незнакомые. */
        const val STRANGER_CHAT_BASE = 2_000_000
        /** Сколько фото и видео можно приложить к одной записи или сообщению. */
        const val MAX_MEDIA = 10

        /** Сколько фото у товара витрины. */
        const val MAX_PRODUCT_PHOTOS = 5

        /** Сколько записей можно закрепить в витрине профиля. */
        const val MAX_PINS = 3
    }
}

/** Текст сообщения для списков и цитат; без текста — что приложено. */
fun messagePreview(message: Message): String {
    if (message.text.isNotEmpty()) return message.text
    message.poll?.let { return t("Опрос") + ": " + it.question }
    message.place?.let { return t("Геолокация") + (if (it.title.isNotEmpty()) ": " + it.title else "") }
    message.product?.let { return t("Товар") + ": " + it.title }
    val a = message.attachments
    return when {
        a.isEmpty() -> ""
        a.all { it.isGif } -> "GIF"
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
    /** Мои друзья (взаимно); имя прежнее — так оно лежит в файле. */
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
    /** Входящие заявки в друзья — новые первыми. */
    val followRequests: List<Int> = emptyList(),
    /** Мои отправленные заявки в друзья. */
    val sentRequests: Set<Int> = emptySet(),
    val recapNotified: Set<String> = emptySet(),
    val together: TogetherState? = null,
    val shelfMarks: List<ShelfMark> = emptyList(),
    val dismissedRecs: Set<String> = emptySet(),
    val growth: List<GrowthEvent> = emptyList(),
    val invitedBy: Invite? = null,
    val memorySeen: String = "",
    val stickerPacks: List<StickerPack> = emptyList(),
    val backdrop: Backdrop = Backdrop.DEFAULT,
    val recentGifs: List<Attachment> = emptyList(),
    val reports: List<Report> = emptyList(),
    val guideRead: Set<String> = emptySet(),
    val guideHidden: Boolean = false,
    val subscription: Subscription = Subscription(),
    val activity: List<ActivityItem> = emptyList(),
    val activitySeen: Long = 0,
    val searchHistory: List<String> = emptyList(),
    val stories: List<Story> = emptyList(),
    val seenStories: Set<Long> = emptySet(),
    val closeFriends: Set<Int> = emptySet(),
    val highlights: List<Highlight> = emptyList(),
    val callLog: List<CallRecord> = emptyList(),
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

/** Что между мной и другим человеком. */
enum class FriendState {
    /** Не друзья, заявок нет. */
    None,
    /** Я отправил заявку, ждём ответа. */
    Requested,
    /** Человек прислал мне заявку. */
    Incoming,
    /** Друзья. */
    Friends,
}
