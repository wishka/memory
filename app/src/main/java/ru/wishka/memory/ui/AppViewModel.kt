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
    Growth("Друзья и идеи"),
    Sounds("Звуки"),
    Wallpaper("Фон переписок"),
    Legal("Документы"),
    /** Гид по Memory: главы → темы с шагами. */
    Guide("Гид по Memory"),
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
) {
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
    val unreadTotal: Int get() = chats.filterNot { it.muted }.sumOf { it.unread }
    /** Список чатов: закреплённые сверху, остальные — по последнему сообщению. */
    val chatList: List<Chat> get() = chats.sortedByDescending { it.onTop }
    /**
     * Лента без записей тех, кто в чёрном списке, и без записей закрытых
     * профилей тех, кто вам не друг.
     */
    val feed: List<Post> get() = posts.filter { it.author.id !in blocked && canSee(it.author) }
    /** Записи человека видны: профиль открыт, вы друзья или это вы. */
    fun canSee(user: User): Boolean = !user.closed || user.id in following || user.id == me.id
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
    val blockedPeople: List<User> get() = Demo.people.filter { it.id in blocked }
    fun isBlocked(userId: Int): Boolean = userId in blocked
    /**
     * Друзья другого человека. Сервера нет, поэтому видны только знакомые
     * по демо-данным — и вы, если вы друзья.
     */
    fun friendsOf(user: User): List<User> =
        (if (user.id in following) listOf(me) else emptyList()) +
            Demo.people.filter { it.id != user.id && it.id !in blocked }
    val myPosts: List<Post> get() = posts.filter { it.author.id == me.id }
    /** «Истории»: свои записи и записи друзей — новые сверху, как и в ленте. */
    val stories: List<Post> get() = posts.filter { (it.author.id in following || it.author.id == me.id) && it.author.id !in blocked }
    /** Мои друзья — люди. */
    val friends: List<User> get() = Demo.people.filter { it.id in following }
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
            s.openProduct != null -> { closeProduct(); true }
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
    fun publish(text: String, media: List<Attachment> = emptyList(), checkIn: CheckIn? = null, poll: PollDraft? = null) {
        val trimmed = text.trim()
        val built = poll?.build(nextId++)
        if (trimmed.isEmpty() && media.isEmpty() && checkIn == null && built == null) return
        media.filter { it.isGif }.forEach(::rememberGif)
        _state.update { s ->
            val next = s.copy(
                posts = listOf(Post(nextId++, s.me, trimmed, 0, media = media.take(MAX_MEDIA), createdAt = clock(), checkIn = checkIn, poll = built)) + s.posts,
                // Чек-ин сам засчитывается в общие цели «Вместе»
                together = if (checkIn != null) s.together.afterCheckIn(checkIn) else s.together,
                growth = if (checkIn != null) s.growth.logged(GrowthEvent(GrowthEventType.CheckIn, clock(), checkIn.activity)) else s.growth,
            )
            // Отклик: что изменилось от чек-ина — полка, цели, план, что откроется дальше
            if (checkIn == null) next else next.copy(echo = echoFor(checkIn, s, next))
        }
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
        val invite = Invite(s.inviteCode, action, targetId, s.me.name.substringBefore(' '))
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
     * Дружба взаимная. Уже друзья — удалить из друзей (интерфейс сначала
     * спрашивает «Удалить из друзей?»); заявка ждёт ответа — отозвать её;
     * человек сам прислал заявку — сразу друзья; иначе уходит заявка.
     * В демо её принимает любой человек через несколько секунд.
     */
    fun toggleFriend(userId: Int) {
        val s0 = _state.value
        if (userId == s0.me.id || userId in s0.blocked) return
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
        _state.update { it.copy(following = it.following - userId) }
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
        Demo.person(userId)?.let { notify(Nudges.requestAccepted(it)) }
    }

    /** Принять заявку в друзья: человек становится другом. */
    fun approveRequest(userId: Int) {
        acceptJobs.remove(userId)?.cancel()
        _state.update {
            it.copy(followRequests = it.followRequests - userId, sentRequests = it.sentRequests - userId, following = it.following + userId)
        }
    }

    /** Отклонить заявку: человек не узнает об этом, заявка просто исчезает. */
    fun declineRequest(userId: Int) {
        declined += userId
        _state.update { it.copy(followRequests = it.followRequests - userId) }
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
    fun send(chatId: Int, text: String, attachments: List<Attachment> = emptyList(), replyTo: ReplyRef? = null, poll: PollDraft? = null, place: SharedPlace? = null) {
        val trimmed = text.trim()
        val built = poll?.build(nextId++)
        if (trimmed.isEmpty() && attachments.isEmpty() && built == null && place == null) return
        append(
            chatId,
            Message(nextId++, fromMe = true, text = trimmed, time = now(), attachments = attachments.take(MAX_MEDIA), replyTo = replyTo, poll = built, place = place),
        )
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
        if (block && target.authorId != 0 && target.authorId != _state.value.me.id) {
            block(target.authorId)
        }
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

    /** Приватность. Заявки в друзья от неё не зависят: открыли профиль — они всё так же ждут ответа. */
    fun setPrivacy(privacy: Privacy) {
        _state.update { it.copy(privacy = privacy) }
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
            )
        }
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
            else -> receiveFriendRequest(requesters[random.nextInt(requesters.size)].id)
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
            ).withMe(s.me)
        }
        refreshPlan()
    }

    private fun now(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

    companion object {
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
