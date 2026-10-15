import Foundation
import Observation

/// Вкладки нижней панели — в порядке слева направо.
enum AppTab: String, CaseIterable, Identifiable {
    case feed = "Лента"
    case stories = "Истории"
    case prism = "PRISM"
    case chats = "Чаты"
    case groups = "Группы"
    case together = "Вместе"
    case profile = "Профиль"

    var id: String { rawValue }
    /// Название на выбранном языке.
    var title: String { t(rawValue) }
}

/// Разделы профиля за счётчиками: записи, друзья, группы, сохранённое, заявки в друзья.
enum ProfileSection: String, CaseIterable {
    case posts = "Записи"
    /// Друзья (взаимно): у себя — с «Написать» и удалением из друзей, у другого — его друзья.
    case friends = "Друзья"
    case groups = "Группы"
    case saved = "Сохранённое"
    case music = "Моя музыка"
    case stickers = "Студия"
    case recap = "Итоги"
    case shelf = "Полка"
    /// Входящие заявки в друзья и отправленные мной.
    case requests = "Заявки в друзья"
    /// Memory+: тарифы, пробный период, смена тарифа.
    case premium = "Memory+"
    /// Записи, где меня отметили на фото.
    case tagged = "Отметки"

    var title: String { t(rawValue) }
}

/// Страницы настроек за шестерёнкой в профиле. main — сам список настроек.
enum SettingsPage: String, CaseIterable {
    case main = "Настройки"
    case security = "Безопасность"
    case privacy = "Приватность"
    case blacklist = "Чёрный список"
    case notifications = "Уведомления"
    case textSize = "Размер текста"
    case language = "Язык"
    case about = "О приложении"
    case growth = "Друзья и идеи"
    case sounds = "Звуки"
    case wallpaper = "Фон переписок"
    case legal = "Документы"
    /// Гид по Memory: главы → темы с шагами.
    case guide = "Гид по Memory"
    /// Оформление: как в системе, тёмное или светлое.
    case theme = "Оформление"
    /// Качество фото и видео при отправке, загрузки, очередь, кэш.
    case network = "Данные и сеть"
    /// Скрытые слова, фильтр оскорблений и спама.
    case hiddenWords = "Скрытые слова"
    /// Устройства, с которых вошли.
    case devices = "Устройства"

    var title: String { t(rawValue) }
}

/// Связь с сервером — для полоски «нет сети».
enum NetStatus: Equatable {
    case online, offline, serverDown
}

/// Как подгружается старое в ленте.
struct FeedPaging: Equatable {
    var loading = false
    /// Дальше ничего нет.
    var end = false
    /// Показано сохранённое, свежего с сервера ещё не было.
    var fromCache = false
    var cachedAt: Int64 = 0
    /// Не загрузилось: «нет сети», «сервер не отвечает».
    var error: NetError.Kind? = nil
}

/// Разделы полки.
enum ShelfTab: String, CaseIterable, Identifiable {
    case forYou = "Для вас"
    case films = "Кино"
    case books = "Книги"
    case sport = "Спорт"
    case places = "Места"
    case progress = "Права"

    var id: String { rawValue }
    var title: String { t(rawValue) }

    var emoji: String {
        switch self {
        case .forYou: return "✨"
        case .films: return "🎬"
        case .books: return "📖"
        case .sport: return "🏃"
        case .places: return "📍"
        case .progress: return "🔓"
        }
    }
}

/// Что между мной и другим человеком.
enum FriendState: Equatable {
    /// Не друзья, заявок нет.
    case notFriends
    /// Я отправил заявку, ждём ответа.
    case requested
    /// Человек прислал мне заявку.
    case incoming
    /// Друзья.
    case friends
}

/// Открытый товар витрины: группа и товар.
struct ProductKey: Equatable {
    let groupId: Int
    let productId: Int64
}

// Внутри UiState одноимённые свойства заслоняют функции — зовём через эти.
private func computeRunPlan(_ checkIns: [Dated]) -> RunPlan { runPlan(checkIns) }
private func computeMovedKm(_ checkIns: [Dated]) -> Double { movedKm(checkIns) }

/// Всё, что видно на экране. Те же поля и правила, что UiState на Android.
struct UiState: Equatable {
    var tab: AppTab = .feed
    var me: User = Demo.me
    var posts: [Post] = Demo.posts
    var chats: [Chat] = Demo.chats
    /// Открытая переписка поверх вкладки «Чаты»; nil — список чатов.
    var openChatId: Int? = nil
    var notifications = Notifications()
    var privacy = Privacy()
    /// Чёрный список: их записи, комментарии и сообщения не видны.
    var blocked: Set<Int> = []
    /// Комментарии, на которые пожаловались: у пожаловавшегося они скрыты.
    var reportedComments: Set<Int64> = []
    /// Свои значки, смайлы и стикеры из «Студии» — новые первыми.
    var stickers: [Sticker] = []
    /// Записи, закреплённые в профиле (витрина), — до трёх.
    var profilePins: [Int64] = []
    /// «Моя музыка»: треки и плейлисты — только на этом телефоне.
    var tracks: [MusicTrack] = []
    var playlists: [Playlist] = []
    /// Создание своего PRISM — камера, редактор, публикация — поверх всего.
    var creatingPrism = false
    /// Настройки поверх вкладки «Профиль»; nil — закрыты.
    var settingsPage: SettingsPage? = nil
    /// Раздел чужого профиля (записи, друзья) поверх его страницы.
    var personSection: ProfileSection? = nil
    /// Мои друзья — дружба взаимная. Имя поля прежнее (было «подписки»):
    /// так его хранят сохранения. Записи друзей — во вкладке «Истории»,
    /// закрытые профили друзей видны.
    var following: Set<Int> = Demo.following
    var prisms: [Prism] = Demo.prisms
    /// PRISM, с которого открыть вкладку (из «Сохранённого»); nil — с первого.
    var prismStartId: Int64? = nil
    /// Открытое обсуждение записи (в ленте, историях, группе); nil — закрыто.
    var commentsPostId: Int64? = nil
    /// Открытое обсуждение PRISM; nil — закрыто.
    var commentsPrismId: Int64? = nil
    /// Фото или видео на весь экран; nil — не открыто.
    var viewing: Attachment? = nil
    /// Раздел профиля поверх вкладки «Профиль»; nil — сам профиль.
    var profileSection: ProfileSection? = nil
    /// Переписка, из которой открыли страницу человека: «назад» вернёт в неё.
    var returnToChatId: Int? = nil
    var groups: [MemoryGroup] = Demo.groups
    /// Страница человека поверх текущей вкладки; nil — закрыта.
    var openPersonId: Int? = nil
    /// Страница группы поверх вкладки «Группы»; nil — список групп.
    var openGroupId: Int? = nil
    /// Настройки открытой группы — за шестерёнкой.
    var groupSettingsOpen = false
    /// Свои чек-ины: шаблоны, которые человек создал сам.
    var checkInTemplates: [CheckInTemplate] = []
    /// Входящие заявки в друзья ко мне — новые первыми. Имя поля прежнее — для сохранений.
    var followRequests: [Int] = []
    /// Мои исходящие заявки в друзья, пока их не приняли.
    var sentRequests: Set<Int> = []
    /// За какой период уже напомнили об Итогах: «2026-09», «2026».
    var recapNotified: Set<String> = []
    /// Уведомление поверх приложения, пока оно открыто; nil — нет.
    var banner: Nudge? = nil
    /// Итоги: открыт месяц или год.
    var recapPeriod: RecapPeriod = .month
    /// Итоги: на сколько месяцев или лет назад от текущего.
    var recapBack = 0
    /// Вкладка «Вместе»: цели, банк времени, дневники, миссии, клады, вылазки, рисунок, обмен.
    var together: TogetherState = TogetherDemo.initial()
    /// Открытый раздел вкладки «Вместе».
    var togetherSection: TogetherSection = .challenges
    /// Полка: отметки без записи в ленте — любимое при знакомстве, «хочу посмотреть».
    var shelfMarks: [ShelfMark] = []
    /// Рекомендации, от которых отказались («не моё»): ключи «film:дюна».
    var dismissedRecs: Set<String> = []
    /// Отклик на только что отмеченный чек-ин; nil — не показываем.
    var echo: CheckInEcho? = nil
    /// Журнал событий роста: приглашения, карточки, чек-ины.
    var growth: [GrowthEvent] = []
    /// Кто вас позвал в Memory (по ссылке).
    var invitedBy: Invite? = nil
    /// Приглашение, по которому только что открыли приложение: показать, к чему зовут.
    var openedInvite: Invite? = nil
    /// День, когда закрыли «В этот день»: «2026-10-12».
    var memorySeen: String = ""
    /// Полка: открытый раздел.
    var shelfTab: ShelfTab = .films
    /// Коллекции стикеров, как наборы в iOS.
    var stickerPacks: [StickerPack] = []
    /// Общий фон переписок.
    var backdrop: Backdrop = Backdrop.default
    /// Недавние гифки — первыми в выборе.
    var recentGifs: [Attachment] = []
    /// Жалобы, которые ждут отправки модераторам (с сервером — уйдут им).
    var reports: [Report] = []
    /// На что сейчас жалуются: открыт выбор причины.
    var reporting: ReportTarget? = nil
    /// Открытый документ: соглашение, политика, правила…
    var legalDoc: LegalDoc? = nil
    /// Открытый товар витрины: группа и товар; nil — нет.
    var openProduct: ProductKey? = nil
    /// Гид: открытая глава; nil — список глав.
    var guideChapter: String? = nil
    /// Прочитанные темы гида: «глава/тема».
    var guideRead: Set<String> = []
    /// Карточку гида в ленте скрыли.
    var guideHidden = false
    /// Вкладка, из которой открыли гид (не из настроек): «назад» вернёт в неё.
    var guideReturn: AppTab? = nil
    /// Подписка Memory+ — как хранится.
    var subscription = MemorySubscription()
    /// Какой тариф действует сейчас (закончившаяся подписка — free). Обновляет refreshPlan().
    var plan: PlanTier = Premium.enabled ? .free : Premium.openTier
    /// Шторка «Это в Memory+»: что пытались открыть без подписки.
    var paywall: PremiumFeature? = nil
    /// Лента активности: «нравится», комментарии, упоминания, заявки — новые сверху.
    var activity: [ActivityItem] = []
    /// Когда ленту активности открывали в последний раз: что позже — «новое».
    var activitySeen: Date = Date(timeIntervalSince1970: 0)
    /// Открыта лента активности.
    var activityOpen = false
    /// Открыт поиск и что в нём набрано.
    var searchOpen = false
    var searchQuery = ""
    /// Недавние запросы — новые первыми.
    var searchHistory: [String] = []
    /// Открытая по ссылке запись — отдельным экраном.
    var openPostId: Int64? = nil
    /// Своя запись, которую сейчас правят.
    var editingPostId: Int64? = nil
    /// Эфиры друзей, которые идут сейчас.
    var lives: [LiveStream] = []
    /// Свой эфир, пока он идёт.
    var myLive: LiveStream? = nil
    /// Какой эфир открыт на экране (свой или друга).
    var watchingLiveId: Int64? = nil
    /// Открыт запуск эфира: название и кто видит.
    var liveStarting = false
    /// Итог только что закончившегося своего эфира.
    var liveSummary: LiveStream? = nil
    /// Ведущий нажал «назад» — спросить, закончить ли эфир.
    var confirmEndLive = false
    /// Истории: свои (и в архиве) и друзей — за сутки и из «Актуального».
    var stories: [Story] = []
    /// Какие истории я уже смотрел.
    var seenStories: Set<Int64> = []
    /// «Близкие друзья»: им видны истории «для близких».
    var closeFriends: Set<Int> = []
    /// «Актуальное»: свои подборки и подборки друзей.
    var highlights: [Highlight] = []
    /// Открыт просмотр историй.
    var storyViewer: StoryViewer? = nil
    /// Открыта новая история.
    var storyComposer = false
    /// Кто посмотрел мою историю — открыт список.
    var storyViewersOf: Int64? = nil
    /// История звонков.
    var callLog: [CallRecord] = []
    /// Звонок, который идёт сейчас.
    var call: ActiveCall? = nil
    /// Репост: какую запись репостят (открыта шторка).
    var repostingPostId: Int64? = nil
    /// «Отправить в чат»: какую запись отправляют (открыт выбор переписки).
    var sharingPostId: Int64? = nil
    /// Связь: есть, нет сети, сервер не отвечает.
    var netStatus: NetStatus = .online
    /// Старые записи ленты — подгружены с сервера (или из кэша) страницами.
    var olderFeed: [Post] = []
    var feedPaging = FeedPaging()
    /// Загрузки фото и видео: что идёт, что ждёт, что на паузе.
    var uploads: [UploadJob] = []
    /// Сколько действий ждут отправки.
    var outboxCount = 0
    /// Качество отправки (настройка телефона — DevicePrefs).
    var mediaQuality: MediaQuality = .standard
    /// Демо: «как будто нет сети» — проверить очередь и кэш.
    var demoOffline = false
    /// Сколько места занимает сохранённое для офлайна.
    var cacheBytes: Int64 = 0
    /// Что сказать о сети: «запись не отправлена» и т. п.; nil — нечего.
    var netNotice: String? = nil
    /// Устройства, с которых вошли в аккаунт.
    var devices: [DeviceInfo] = []
    /// Короткое сообщение внизу экрана («Слишком часто…»); nil — нечего.
    var notice: String? = nil
    /// Вход на сервер закрыт (вышли с этого устройства на другом) — нужно войти снова.
    var signedOut = false

    /// Кружки историй друзей (свой — отдельно).
    func storyCircles(_ now: Date) -> [StoryCircle] {
        Stories.rings(
            stories.filter { st in
                following.contains(st.authorId) && !blocked.contains(st.authorId)
                    && Stories.visible(st, viewerId: me.id, isFriend: true, inAuthorsClose: true)
            },
            Demo.people, meId: me.id, seen: seenStories, now: now
        )
    }

    /// Свои истории за сутки — по порядку.
    func myActiveStories(_ now: Date) -> [Story] {
        stories.filter { $0.authorId == me.id && $0.active(now) }.stableSorted { $0.createdAt < $1.createdAt }
    }

    /// Архив своих историй — новые сверху.
    var storyArchive: [Story] {
        stories.filter { $0.authorId == me.id }.stableSorted { $0.createdAt > $1.createdAt }
    }

    func highlightsOf(_ userId: Int) -> [Highlight] { highlights.filter { $0.ownerId == userId && !$0.storyIds.isEmpty } }

    func story(_ id: Int64) -> Story? { stories.first { $0.id == id } }

    /// Эфир на экране.
    var watchingLive: LiveStream? {
        guard let id = watchingLiveId else { return nil }
        if let mine = myLive, mine.id == id { return mine }
        return lives.first { $0.id == id }
    }

    /// Эфиры, которые можно смотреть: без заблокированных.
    var visibleLives: [LiveStream] { lives.filter { $0.live && !blocked.contains($0.hostId) } }

    /// Люди, которых можно найти и упомянуть: все, кроме себя и заблокированных.
    var searchablePeople: [User] { Demo.people.filter { !blocked.contains($0.id) } }

    /// Записи, которые можно найти: лента, свои и группы.
    var searchablePosts: [Post] {
        var seen = Set<Int64>()
        return (feed + groups.flatMap(\.posts)).filter { !blocked.contains($0.author.id) && seen.insert($0.id).inserted }
    }

    /// Результаты текущего запроса.
    var searchResults: SearchResults { Search.all(searchQuery, searchablePeople, searchablePosts, groups) }

    /// Новых событий в ленте активности — точка на колокольчике.
    var activityUnread: Int { Activity.unread(activity, activitySeen, blocked: blocked) }

    /// Полка — собирается сама из чек-инов и отметок.
    var shelf: Shelf { Shelf.build(posts.datedCheckIns(me.id), shelfMarks) }
    var myCheckIns: [Dated] { posts.datedCheckIns(me.id) }
    /// Подборка: kind — «film» или «book».
    func recommendations(_ kind: String, limit: Int = 6, shelf precomputed: Shelf? = nil) -> [Recommendation] {
        // Персональные рекомендации выключены в «Приватности» — ничего не подбираем
        if !privacy.personalRecs { return [] }
        let shelf = precomputed ?? self.shelf
        return recommend(shelf.rated, exclude: shelf.keys.union(dismissedRecs),
                         friends: DemoTaste.ratings.filter { !blocked.contains($0.key) }, kind: kind, limit: limit)
    }
    var runPlan: RunPlan { computeRunPlan(myCheckIns) }
    var unlocks: [UnlockProgress] { unlockProgress(together, me.id) }
    func unlocked(_ u: Unlock) -> Bool { together.unlocked(u, meId: me.id) }
    var movedKm: Double { computeMovedKm(myCheckIns) }
    var inviteCode: String { Invites.code(me.handle) }

    var openPerson: User? { openPersonId.flatMap { Demo.person($0) } }
    var openGroup: MemoryGroup? { groups.first { $0.id == openGroupId } }
    var myGroups: [MemoryGroup] { groups.filter { $0.joined } }
    func postsOf(_ userId: Int) -> [Post] { posts.filter { $0.author.id == userId } }
    var openChat: Chat? { chats.first { $0.id == openChatId } }
    /// Счётчик на вкладке «Чаты»: переписки без звука и запросы на переписку не считаются.
    var unreadTotal: Int { chats.filter { !$0.muted && !$0.request }.reduce(0) { $0 + $1.unread } }
    /// Список чатов: закреплённые сверху, остальные — по последнему сообщению. Без запросов.
    var chatList: [Chat] {
        let visible: [Chat] = chats.filter { !$0.request }
        return visible.filter(\.onTop) + visible.filter { !$0.onTop }
    }
    /// Запросы на переписку: написали не друзья.
    var requestChats: [Chat] { chats.filter(\.request) }

    /// Скрыть ли текст фильтром (скрытые слова, оскорбления, спам): своё и
    /// сказанное другом в переписке на двоих — не скрываем.
    func hiddenReason(_ text: String, _ authorId: Int, chat: Chat? = nil) -> HiddenReason? {
        if authorId == me.id || authorId == AppModel.systemAuthor { return nil }
        if let chat, !chat.isGroup, !chat.request, following.contains(chat.peer.id) { return nil }
        return Moderation.check(text, privacy.moderation)
    }

    /// «Вера печатает…», «Вера и Марк печатают…» — для группы; для двоих — просто «печатает…».
    func typingTitle(_ chat: Chat) -> String? {
        let meId = me.id
        let ids: [Int] = chat.typingIds.filter { $0 != meId }.sorted()
        if ids.isEmpty { return chat.typing ? t("печатает…") : nil }
        if !chat.isGroup { return t("печатает…") }
        let names: [String] = ids.compactMap { Demo.person($0)?.firstName }
        if names.isEmpty { return t("печатает…") }
        if names.count == 1 { return tf("%s печатает…", names[0]) }
        return tf("%s и %s печатают…", names[0], names[1])
    }
    /// Лента без записей тех, кто в чёрном списке, и без записей закрытых
    /// профилей тех, кто вам не друг.
    var feed: [Post] { posts.filter { !blocked.contains($0.author.id) && canSee($0.author) } }
    /// Лента целиком: свежее с телефона, ниже — старое с сервера (без повторов и заблокированных).
    var feedWithOlder: [Post] {
        let local = feed
        let ids = Set(local.map(\.id))
        let older = olderFeed.filter { !ids.contains($0.id) && !blocked.contains($0.author.id) && canSee($0.author) }
        return local + older
    }

    /// Прогресс отправки записи или сообщения по ключу очереди (0…1); нет загрузок — nil.
    func uploadProgress(_ owner: String) -> Float? {
        let mine = uploads.filter { $0.owner == owner }
        if mine.isEmpty { return nil }
        var total: Int64 = 0
        var done: Int64 = 0
        for job in mine {
            let size = max(job.size, 1)
            total += size
            done += job.state == .done ? size : job.offset
        }
        let ratio = Float(done) / Float(total)
        return min(max(ratio, 0), 1)
    }
    /// Записи человека видны: профиль открыт, вы друзья или это вы.
    /// Закрытость берётся из демо-данных: в сохранённых записях автор может быть старым.
    func canSee(_ user: User) -> Bool {
        let closed = Demo.person(user.id)?.closed ?? user.closed
        return !closed || following.contains(user.id) || user.id == me.id
    }
    /// Люди, приславшие мне заявку в друзья.
    var requestPeople: [User] { followRequests.compactMap { Demo.person($0) } }
    /// Люди, которым я отправил заявку в друзья.
    var sentRequestPeople: [User] { sentRequests.sorted().compactMap { Demo.person($0) } }
    /// Что между мной и человеком: друзья, заявка от меня, заявка ко мне или ничего.
    func friendState(_ userId: Int) -> FriendState {
        if following.contains(userId) { return .friends }
        if sentRequests.contains(userId) { return .requested }
        if followRequests.contains(userId) { return .incoming }
        return .notFriends
    }
    var blockedPeople: [User] { Demo.people.filter { blocked.contains($0.id) } }
    func isBlocked(_ userId: Int) -> Bool { blocked.contains(userId) }
    /// Друзья другого человека. Сервера нет, поэтому видны только знакомые
    /// по демо-данным — и вы, если вы друзья.
    func friendsOf(_ user: User) -> [User] {
        let you: [User] = following.contains(user.id) ? [me] : []
        return you + Demo.people.filter { $0.id != user.id && !blocked.contains($0.id) }
    }
    var myPosts: [Post] { posts.filter { $0.author.id == me.id } }
    /// Вкладка «Истории» под кружками: свои записи и записи друзей — новые сверху, как и в ленте.
    var friendPosts: [Post] {
        posts.filter { (following.contains($0.author.id) || $0.author.id == me.id) && !blocked.contains($0.author.id) }
    }
    /// Мои друзья — люди.
    var friends: [User] { Demo.people.filter { following.contains($0.id) } }
    /// Записи, где меня отметили на фото, — «Отметки» в профиле.
    var taggedPosts: [Post] {
        var seen = Set<Int64>()
        let epoch = Date(timeIntervalSince1970: 0)
        return (posts + groups.flatMap(\.posts))
            .filter { p in !blocked.contains(p.author.id) && p.media.contains { a in a.tags.contains { $0.userId == me.id } } }
            .filter { seen.insert($0.id).inserted }
            .stableSorted { ($0.createdAt ?? epoch) > ($1.createdAt ?? epoch) }
    }
    /// «Сохранённое» в профиле: PRISM в закладках.
    var savedPrisms: [Prism] { prisms.filter { $0.saved } }
    func track(_ id: String) -> MusicTrack? { tracks.first { $0.id == id } }
    func playlistTracks(_ playlist: Playlist) -> [MusicTrack] { playlist.trackIds.compactMap { track($0) } }
    /// Запись по номеру — в ленте или в группе: для обсуждения.
    func findPost(_ id: Int64) -> Post? {
        if let post = posts.first(where: { $0.id == id }) { return post }
        for g in groups {
            if let post = g.posts.first(where: { $0.id == id }) { return post }
        }
        return nil
    }
    func findPrism(_ id: Int64) -> Prism? { prisms.first { $0.id == id } }
    /// Товар витрины по группе и номеру.
    func findProduct(_ groupId: Int, _ productId: Int64) -> Product? {
        groups.first { $0.id == groupId }?.shop.first { $0.id == productId }
    }

    /// Новое имя и сведения — во всех своих записях и комментариях.
    mutating func applyMe(_ me: User) {
        self.me = me
        func fixComments(_ list: [Comment]) -> [Comment] {
            list.map { c in
                var c = c
                if c.author.id == me.id { c.author = me }
                return c
            }
        }
        func fix(_ post: Post) -> Post {
            var p = post
            if p.author.id == me.id { p.author = me }
            p.commentList = fixComments(p.commentList)
            return p
        }
        posts = posts.map(fix)
        groups = groups.map { g in
            var g = g
            g.posts = g.posts.map(fix)
            return g
        }
        prisms = prisms.map { p in
            var p = p
            p.commentList = fixComments(p.commentList)
            return p
        }
    }
}

/// Сохраняемая часть состояния (см. StateStore).
struct Snapshot: Codable, Equatable {
    var version: Int = Snapshot.currentVersion
    var posts: [Post]
    var chats: [Chat]
    var groups: [MemoryGroup]
    var prisms: [Prism]
    var following: Set<Int>
    var notifications: Bool
    var nextId: Int64
    /// Новые поля — необязательные: старые файлы читаются без них.
    var notify: Notifications? = nil
    var privacy: Privacy? = nil
    var blocked: Set<Int>? = nil
    var tracks: [MusicTrack]? = nil
    var playlists: [Playlist]? = nil
    var reportedComments: Set<Int64>? = nil
    var stickers: [Sticker]? = nil
    var profilePins: [Int64]? = nil
    var checkInTemplates: [CheckInTemplate]? = nil
    /// Входящие заявки в друзья — новые первыми.
    var followRequests: [Int]? = nil
    /// Из старых файлов: «одобренные подписчики» — теперь друзья (сливаются
    /// в following при восстановлении). Больше не записывается.
    var approvedFollowers: Set<Int>? = nil
    /// Мои отправленные заявки в друзья.
    var sentRequests: Set<Int>? = nil
    var recapNotified: Set<String>? = nil
    var together: TogetherState? = nil
    var shelfMarks: [ShelfMark]? = nil
    var dismissedRecs: Set<String>? = nil
    var growth: [GrowthEvent]? = nil
    var invitedBy: Invite? = nil
    var memorySeen: String? = nil
    var stickerPacks: [StickerPack]? = nil
    var backdrop: Backdrop? = nil
    var recentGifs: [Attachment]? = nil
    var reports: [Report]? = nil
    /// Прочитанные темы гида и скрытая карточка гида в ленте.
    var guideRead: [String]? = nil
    var guideHidden: Bool? = nil
    /// Подписка Memory+.
    var subscription: MemorySubscription? = nil
    /// Лента активности (непонятные события новых версий пропускаются), когда её смотрели, история поиска.
    var activity: LossyList<ActivityItem>? = nil
    var activitySeen: Date? = nil
    var searchHistory: [String]? = nil
    /// Истории, просмотренные, «Близкие друзья», «Актуальное», история звонков.
    var stories: [Story]? = nil
    var seenStories: Set<Int64>? = nil
    var closeFriends: Set<Int>? = nil
    var highlights: [Highlight]? = nil
    var callLog: [CallRecord]? = nil

    /// Меняется, когда меняется устройство файла: старый файл тогда не читается.
    static let currentVersion = 1
}

/// Состояние всего приложения.
///
/// Как на Android: одно состояние и свой простой переход между экранами
/// без NavigationStack — экранов мало, и так переходы видны целиком.
@Observable
final class AppModel {
    var state = UiState()

    /// Задержки демо-ответа в чате; в тестах — нулевые.
    var replyDelays: (typing: Double, reply: Double) = (0.7, 1.5)

    /// Сколько фото и видео можно приложить к одной записи или сообщению.
    static let maxMedia = 10
    /// Сколько записей можно закрепить в витрине профиля.
    static let maxPins = 3
    /// Сколько фото у товара витрины.
    static let maxProductPhotos = 5
    /// Служебные строки переписки (не человек): «Вы создали группу».
    static let systemAuthor = -1
    /// Демо: номера переписок, которые начинают незнакомые.
    static let strangerChatBase = 2_000_000

    @ObservationIgnored private var nextId: Int64 = 10_000
    @ObservationIgnored private var replyIndex = 0
    /// Часы — для времени своих записей и комментариев; в тестах подменяются.
    @ObservationIgnored private let clock: () -> Date
    /// Демо-собеседник отвечает на сообщения; в тестах можно выключить.
    @ObservationIgnored private let demoReplies: Bool

    init(clock: @escaping () -> Date = { Date() }, demoReplies: Bool = true) {
        self.clock = clock
        self.demoReplies = demoReplies
        net = makeDemoNet()
        feedPager = net.feedPager()
        // Демо-сеть по умолчанию (и в тестах) — тоже с живым каналом
        connectRealtime()
    }

    private func newId() -> Int64 {
        nextId += 1
        return nextId
    }

    // MARK: - Навигация

    func selectTab(_ tab: AppTab) {
        state.openChatId = tab == .chats ? state.openChatId : nil
        state.openGroupId = tab == .groups ? state.openGroupId : nil
        state.groupSettingsOpen = false
        state.openPersonId = nil
        state.personSection = nil
        state.returnToChatId = nil
        // Нажатие на «Профиль» внизу всегда ведёт на сам профиль
        state.profileSection = nil
        state.settingsPage = nil
        state.openProduct = nil
        state.guideChapter = nil
        state.guideReturn = nil
        state.tab = tab
    }

    /// Раздел профиля: нажатие на «записи», «друзья», «группы» или «Сохранённое».
    func openProfileSection(_ section: ProfileSection) {
        // Поверх может быть переписка, группа или настройки — уходят, иначе раздел не виден
        state.tab = .profile
        state.openPersonId = nil
        state.personSection = nil
        state.settingsPage = nil
        state.openChatId = nil
        state.openGroupId = nil
        state.groupSettingsOpen = false
        state.profileSection = section
    }

    func closeProfileSection() {
        state.profileSection = nil
    }

    /// Настройки: шестерёнка в профиле открывает список, пункт списка — свою страницу.
    func openSettings(_ page: SettingsPage = .main) {
        state.tab = .profile
        state.profileSection = nil
        state.openPersonId = nil
        state.openChatId = nil
        state.openGroupId = nil
        state.groupSettingsOpen = false
        state.openProduct = nil
        state.guideChapter = nil
        state.guideReturn = nil
        state.settingsPage = page
    }

    /// «Назад» в настройках: со страницы — в список, из списка — в профиль.
    /// В гиде: из главы — к списку глав; гид, открытый из ленты, — обратно в ленту.
    func settingsBack() {
        if state.settingsPage == .guide && state.guideChapter != nil {
            state.guideChapter = nil
        } else if state.settingsPage == .guide, let from = state.guideReturn {
            state.settingsPage = nil
            state.tab = from
            state.guideReturn = nil
        } else {
            state.settingsPage = state.settingsPage == .main ? nil : .main
        }
    }

    // MARK: - Гид

    /// Гид: список глав или сразу глава. Открытый не из настроек помнит, куда вернуться.
    func openGuide(_ chapterId: String? = nil) {
        let from: AppTab? = state.settingsPage == nil ? state.tab : nil
        openSettings(.guide)
        state.guideChapter = Guide.chapter(chapterId)?.id
        state.guideReturn = from
    }

    func openGuideChapter(_ chapterId: String) {
        guard Guide.chapter(chapterId) != nil else { return }
        state.guideChapter = chapterId
    }

    func closeGuideChapter() {
        state.guideChapter = nil
    }

    /// Тему раскрыли — она прочитана.
    func markGuideRead(_ chapter: GuideChapter, _ topic: GuideTopic) {
        let key = chapter.key(topic)
        if state.guideRead.contains(key) { return }
        state.guideRead.insert(key)
    }

    /// Убрать карточку гида из ленты; сам гид остаётся в настройках.
    func hideGuideCard() {
        state.guideHidden = true
    }

    /// Кнопка темы «Открыть …»: туда, о чём тема.
    func guideGo(_ action: GuideAction) {
        switch action {
        case .feed: selectTab(.feed)
        case .stories: selectTab(.stories)
        case .prism: selectTab(.prism)
        case .newPrism:
            selectTab(.prism)
            openPrismCreator()
        case .chats:
            selectTab(.chats)
            state.openChatId = nil
        case .groups:
            selectTab(.groups)
            state.openGroupId = nil
        case .together: selectTab(.together)
        case .profile: selectTab(.profile)
        case .friends: openProfileSection(.friends)
        case .requests: openProfileSection(.requests)
        case .saved: openProfileSection(.saved)
        case .music: openProfileSection(.music)
        case .studio: openProfileSection(.stickers)
        case .shelf: openProfileSection(.shelf)
        case .recap: openProfileSection(.recap)
        case .settings: openSettings(.main)
        case .privacy: openSettings(.privacy)
        case .security: openSettings(.security)
        case .blacklist: openSettings(.blacklist)
        case .notifications: openSettings(.notifications)
        case .sounds: openSettings(.sounds)
        case .wallpaper: openSettings(.wallpaper)
        case .language: openSettings(.language)
        case .legal: openSettings(.legal)
        case .growth: openSettings(.growth)
        case .premium: openPremium()
        }
    }

    // MARK: - Memory+

    /// Тариф, который действует сейчас.
    func plan() -> PlanTier { Premium.enabled ? state.subscription.tierAt(clock()) : Premium.openTier }

    /// Есть ли доступ к премиум-функции — без шторки.
    func has(_ feature: PremiumFeature) -> Bool { plan().covers(feature.tier) }

    /// Проверка перед премиум-действием: есть доступ — true; нет —
    /// открывается шторка «Это в Memory+» и ответ false.
    @discardableResult
    func require(_ feature: PremiumFeature) -> Bool {
        if !Premium.enabled { return true }
        refreshPlan()
        if has(feature) { return true }
        state.paywall = feature
        return false
    }

    func closePaywall() {
        state.paywall = nil
    }

    /// Экран «Memory+» в профиле: тарифы, пробный период, смена тарифа.
    func openPremium() {
        if !Premium.enabled { return }
        refreshPlan()
        state.paywall = nil
        openProfileSection(.premium)
    }

    /// Сверить подписку с часами: закончилась — тариф бесплатный.
    /// Оплаченная с продлением в демо продлевается сама — так сделает магазин.
    func refreshPlan() {
        let now = clock()
        var sub = state.subscription
        if sub.tier != .free && !sub.active(now) && sub.autoRenew && !sub.trial {
            var until = sub.until
            while until <= now { until = until.addingTimeInterval(Double(sub.period.days) * daySeconds) }
            sub.until = until
        }
        let tier = Premium.enabled ? sub.tierAt(now) : Premium.openTier
        // Без лишних изменений состояния — экраны не перерисовываются зря
        if sub != state.subscription { state.subscription = sub }
        if tier != state.plan { state.plan = tier }
    }

    /// Пробный период — 3 дня, один раз на аккаунт, на любой тариф.
    /// Ответ — текст ошибки или nil.
    @discardableResult
    func startTrial(_ tier: PlanTier) -> String? {
        if tier == .free { return t("Выберите тариф") }
        let now = clock()
        let s = state.subscription
        if s.trialUsed { return t("Пробный период уже использован") }
        if s.active(now) && !s.trial { return t("Подписка уже оформлена") }
        state.subscription = MemorySubscription(
            tier: tier, period: .month, until: now.addingTimeInterval(Double(Premium.trialDays) * daySeconds),
            trial: true, autoRenew: false, trialUsed: true
        )
        state.paywall = nil
        refreshPlan()
        return nil
    }

    /// Оформить или сменить тариф и срок. Новый действует сразу, срок
    /// считается заново. Пока оплата демонстрационная — деньги не списываются.
    func subscribe(_ tier: PlanTier, _ period: BillingPeriod) {
        if tier == .free {
            cancelRenewal()
            return
        }
        let now = clock()
        state.subscription = MemorySubscription(
            tier: tier, period: period, until: now.addingTimeInterval(Double(period.days) * daySeconds),
            trial: false, autoRenew: true, trialUsed: state.subscription.trialUsed
        )
        state.paywall = nil
        refreshPlan()
    }

    /// Отменить продление: премиум работает до конца срока.
    func cancelRenewal() {
        if state.subscription.trial { return }
        state.subscription.autoRenew = false
    }

    /// Вернуть продление, пока срок не кончился.
    func resumeRenewal() {
        let sub = state.subscription
        if sub.trial || !sub.active(clock()) { return }
        state.subscription.autoRenew = true
    }

    /// Записи или друзья другого человека — нажатием на счётчик на его странице.
    func openPersonSection(_ section: ProfileSection) {
        state.personSection = section
    }

    func closePersonSection() {
        state.personSection = nil
    }

    /// Страница автора. Своё имя ведёт в «Профиль». Из переписки страница
    /// открывается сразу поверх неё, а «назад» возвращает в переписку.
    func openPerson(_ userId: Int) {
        if userId == state.me.id {
            selectTab(.profile)
            return
        }
        if let chatId = state.openChatId {
            state.returnToChatId = chatId
            state.openChatId = nil
        }
        state.personSection = nil
        state.openPersonId = userId
    }

    func closePerson() {
        state.openPersonId = nil
        state.personSection = nil
        if let chatId = state.returnToChatId {
            state.openChatId = chatId
            state.returnToChatId = nil
        }
    }

    /// «Назад»: переписка → страница человека → группа → лента.
    /// true — обработано внутри приложения.
    @discardableResult
    func back() -> Bool {
        if state.creatingPrism { closePrismCreator(); return true }
        // Во время звонка «назад» не сбрасывает звонок — для этого красная кнопка
        if state.call != nil { return true }
        if state.storyViewersOf != nil { closeStoryViewers(); return true }
        if state.storyViewer != nil { closeStories(); return true }
        if state.storyComposer { closeStoryComposer(); return true }
        if state.repostingPostId != nil || state.sharingPostId != nil { closeRepost(); return true }
        // «Назад» у ведущего эфира — вопрос «Закончить эфир?», а не выход
        if let watching = state.watchingLiveId, state.myLive?.id == watching { state.confirmEndLive = true; return true }
        if state.watchingLiveId != nil { leaveLive(); return true }
        if state.liveStarting { closeLiveStart(); return true }
        if state.activityOpen { closeActivity(); return true }
        if state.editingPostId != nil { cancelEdit(); return true }
        if state.openProduct != nil { closeProduct(); return true }
        if state.viewing != nil { closeMedia(); return true }
        if state.commentsPostId != nil || state.commentsPrismId != nil { closeComments(); return true }
        if state.openPostId != nil { closePost(); return true }
        if state.openChatId != nil { closeChat(); return true }
        if state.personSection != nil && state.openPersonId != nil { closePersonSection(); return true }
        if state.openPersonId != nil { closePerson(); return true }
        if state.groupSettingsOpen { closeGroupSettings(); return true }
        if state.openGroupId != nil { closeGroup(); return true }
        if state.searchOpen { closeSearch(); return true }
        if state.settingsPage != nil { settingsBack(); return true }
        if state.profileSection != nil { closeProfileSection(); return true }
        if state.tab != .feed { selectTab(.feed); return true }
        return false
    }

    // MARK: - Активность

    /// Записать событие в ленту активности. person == nil — несколько человек (голоса).
    func record(_ kind: ActivityKind, _ person: User?, targetId: Int64 = 0, preview: String = "", count: Int = 1) {
        if let person, person.id == state.me.id || state.blocked.contains(person.id) { return }
        let actorName: String
        if let person, Demo.person(person.id) == nil { actorName = person.name } else { actorName = "" }
        let item = ActivityItem(
            id: newId(), kind: kind, actorId: person?.id ?? 0, at: clock(), targetId: targetId,
            preview: String(preview.trimmingCharacters(in: .whitespacesAndNewlines).prefix(80)), count: count,
            actorName: actorName, actorHue: person?.hue ?? 0.5
        )
        state.activity = Activity.add(state.activity, item)
    }

    func openActivity() {
        Telemetry.track(.activityOpened)
        var s = state
        s.activityOpen = true
        s.openPersonId = nil
        s.openGroupId = nil
        s.personSection = nil
        state = s
    }

    /// Закрыли ленту — всё в ней прочитано.
    func closeActivity() {
        var s = state
        s.activityOpen = false
        s.activitySeen = clock()
        state = s
    }

    /// Отметить всё прочитанным, не закрывая.
    func markActivitySeen() {
        state.activitySeen = clock()
    }

    /// Нажатие на событие: туда, о чём оно.
    func openActivityItem(_ g: ActivityGroup) {
        let s = state
        closeActivity()
        switch g.kind {
        case .like, .comment, .reply, .mention, .commentLike, .pollVotes:
            if s.findPost(g.targetId) != nil {
                openComments(g.targetId)
            } else if let actor = g.actors.first {
                openPerson(actor.id)
            }
        case .prismLike, .prismComment:
            selectTab(.prism)
            if s.prisms.contains(where: { $0.id == g.targetId }) { state.prismStartId = g.targetId }
        case .friendRequest:
            openProfileSection(.requests)
        case .friendAccepted:
            if let actor = g.actors.first { openPerson(actor.id) }
        case .groupJoin:
            selectTab(.groups)
            openGroup(Int(g.targetId))
        case .live:
            if s.lives.contains(where: { $0.id == g.targetId && $0.live }) {
                watchLive(g.targetId)
            } else if let actor = g.actors.first {
                openPerson(actor.id)
            }
        case .repost, .tagged:
            if s.findPost(g.targetId) != nil {
                state.openPostId = g.targetId
            } else if let actor = g.actors.first {
                openPerson(actor.id)
            }
        case .storyReply, .missedCall:
            if let actor = g.actors.first {
                if let chat = s.chats.first(where: { $0.peer.id == actor.id }) {
                    openChat(chat.id)
                } else {
                    openPerson(actor.id)
                }
            }
        }
    }

    /// Демо: друг комментирует вашу запись; иногда — упоминает вас.
    private func demoComment<G: RandomNumberGenerator>(_ post: Post, _ fan: User, using rng: inout G) {
        let me = state.me
        let lines = ["Как красиво!", "Это где?", "Хочу так же", "Сохранила себе", "Вот это да", "Отличный момент"]
        let mention = Int.random(in: 0..<3, using: &rng) == 0
        let text = mention
            ? tf("@%s, помнишь, мы туда собирались?", me.handle)
            : t(lines[Int.random(in: 0..<lines.count, using: &rng)])
        let comment = Comment(id: newId(), author: fan, text: text, createdAt: clock())
        state.posts = state.posts.map { p in
            var p = p
            if p.id == post.id {
                p.comments += 1
                p.commentList.append(comment)
            }
            return p
        }
        record(mention ? .mention : .comment, fan, targetId: post.id, preview: text)
        notify(Nudges.commented(fan, post, text))
    }

    // MARK: - Поиск

    /// Открыть поиск; query — сразу с запросом (нажали на #тег).
    func openSearch(_ query: String = "") {
        var s = state
        s.searchOpen = true
        s.searchQuery = query
        s.activityOpen = false
        s.openPersonId = nil
        s.personSection = nil
        s.openGroupId = nil
        s.groupSettingsOpen = false
        s.commentsPostId = nil
        s.commentsPrismId = nil
        s.openChatId = nil
        if !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            s.searchHistory = Search.remember(s.searchHistory, query)
        }
        state = s
    }

    func closeSearch() {
        var s = state
        s.searchOpen = false
        s.searchQuery = ""
        state = s
    }

    func setSearchQuery(_ query: String) {
        state.searchQuery = String(query.prefix(80))
    }

    /// Запрос «состоялся» (нажали на результат или «Найти») — в историю. nil — то, что набрано.
    func commitSearch(_ query: String? = nil) {
        Telemetry.track(.searchUsed)
        state.searchHistory = Search.remember(state.searchHistory, query ?? state.searchQuery)
    }

    func clearSearchHistory() {
        state.searchHistory = []
    }

    /// Нажали на «#тег» в тексте — поиск по тегу.
    func openTag(_ tag: String) {
        Telemetry.track(.tagOpened)
        let clean = tag.hasPrefix("#") ? String(tag.dropFirst()) : tag
        openSearch("#" + clean.lowercased())
    }

    /// Нажали на «@логин» — страница человека; незнакомый логин — поиск по нему.
    func openHandle(_ handle: String) {
        let h = (handle.hasPrefix("@") ? String(handle.dropFirst()) : handle).lowercased()
        if h == state.me.handle.lowercased() {
            state.commentsPostId = nil
            state.commentsPrismId = nil
            selectTab(.profile)
        } else if let person = Demo.people.first(where: { $0.handle.lowercased() == h }) {
            state.commentsPostId = nil
            state.commentsPrismId = nil
            openPerson(person.id)
        } else {
            openSearch("@" + h)
        }
    }

    /// Кого подсказать после «@» в поле ввода.
    func mentionCandidates(_ part: String) -> [User] {
        Search.mentionCandidates(part, state.searchablePeople, friends: state.following)
    }

    // MARK: - Правка записи

    /// Править можно только свою запись — в ленте или в группе.
    func startEdit(_ postId: Int64) {
        guard let post = state.findPost(postId), post.author.id == state.me.id else { return }
        state.editingPostId = postId
    }

    func cancelEdit() {
        state.editingPostId = nil
    }

    /// Сохранить правку: новый текст и оставшиеся вложения (убрать можно,
    /// добавить — новой записью). Пустая запись без вложений, чек-ина и
    /// опроса не сохраняется. Ответ — текст ошибки или nil.
    /// Файлы убранных вложений удаляет экран.
    @discardableResult
    func saveEdit(_ postId: Int64, _ text: String, _ media: [Attachment]) -> String? {
        guard let post = state.findPost(postId) else { return t("Запись не найдена") }
        if post.author.id != state.me.id { return t("Править можно только свои записи") }
        let clean = String(text.trimmingCharacters(in: .whitespacesAndNewlines).prefix(2000))
        let kept = post.media.filter { a in media.contains(where: { $0.id == a.id }) }
        if clean.isEmpty && kept.isEmpty && post.checkIn == nil && post.poll == nil { return t("Запись не может быть пустой") }
        if clean == post.text && kept.count == post.media.count {
            cancelEdit()
            return nil
        }
        var edited = post
        edited.text = clean
        edited.media = kept
        edited.editedAt = clock()
        Telemetry.track(.postEdited)
        var s = state
        s.posts = s.posts.map { $0.id == postId ? edited : $0 }
        s.groups = s.groups.map { group in
            guard group.posts.contains(where: { $0.id == postId }) else { return group }
            var g = group
            g.posts = g.posts.map { $0.id == postId ? edited : $0 }
            return g
        }
        s.editingPostId = nil
        state = s
        return nil
    }

    // MARK: - Ссылки

    /// Открыть ссылку Memory: запись, профиль, PRISM, группу или тег. false — такого на телефоне нет.
    @discardableResult
    func openLink(_ target: LinkTarget) -> Bool {
        Telemetry.track(.linkOpened)
        let s = state
        switch target {
        case .postLink(let id):
            guard let post = s.findPost(id), !s.blocked.contains(post.author.id), s.canSee(post.author) else { return false }
            var next = state
            next.openPostId = id
            next.activityOpen = false
            next.searchOpen = false
            next.openChatId = nil
            state = next
            return true
        case .profile(let handle):
            let h = handle.lowercased()
            if h != s.me.handle.lowercased() && !Demo.people.contains(where: { $0.handle.lowercased() == h }) { return false }
            openHandle(h)
            return true
        case .prismLink(let id):
            guard s.prisms.contains(where: { $0.id == id }) else { return false }
            selectTab(.prism)
            state.prismStartId = id
            return true
        case .groupLink(let id):
            guard s.groups.contains(where: { $0.id == id }) else { return false }
            selectTab(.groups)
            openGroup(id)
            return true
        case .tagLink(let tag):
            openTag(tag)
            return true
        }
    }

    func closePost() {
        state.openPostId = nil
    }

    // MARK: - Истории

    func openStoryComposer() {
        state.storyComposer = true
    }

    func closeStoryComposer() {
        state.storyComposer = false
    }

    /// Новая история: фото или видео либо текст на градиенте. closeFriends —
    /// только для «Близких друзей». Ответ — текст ошибки или nil.
    @discardableResult
    func publishStory(_ media: Attachment?, _ text: String, bg: Int, closeFriends: Bool) -> String? {
        let clean = String(text.trimmingCharacters(in: .whitespacesAndNewlines).prefix(Stories.maxText))
        if media == nil && clean.isEmpty { return t("Добавьте фото, видео или текст") }
        Telemetry.track(.storyPublished)
        let story = Story(id: newId(), authorId: state.me.id, createdAt: clock(), media: media, text: clean,
                          bg: min(max(bg, 0), Stories.backgrounds.count - 1), closeFriends: closeFriends)
        var s = state
        s.stories.append(story)
        s.storyComposer = false
        state = s
        return nil
    }

    /// Открыть истории человека; дальше — по кругу остальных с историями.
    func openStories(_ authorId: Int) {
        let s = state
        let now = clock()
        let circles = s.storyCircles(now)
        let order = (s.myActiveStories(now).isEmpty ? [] : [s.me.id]) + circles.map(\.user.id)
        guard order.contains(authorId) else { return }
        let list = storiesOf(authorId)
        state.storyViewer = StoryViewer(authorIds: order, author: authorId, index: Stories.startIndex(list, state.seenStories))
        if let story = currentStory() { seeStory(story.id) }
    }

    /// Открыть подборку «Актуального».
    func openHighlight(_ highlightId: Int64) {
        guard let h = state.highlights.first(where: { $0.id == highlightId }) else { return }
        if !h.storyIds.contains(where: { state.story($0) != nil }) { return }
        state.storyViewer = StoryViewer(authorIds: [h.ownerId], author: h.ownerId, index: 0, highlightId: highlightId)
    }

    func closeStories() {
        var s = state
        s.storyViewer = nil
        s.storyViewersOf = nil
        state = s
    }

    /// Истории, которые сейчас листаются у этого автора.
    func storiesOf(_ authorId: Int) -> [Story] {
        let s = state
        if let highlightId = s.storyViewer?.highlightId {
            guard let h = s.highlights.first(where: { $0.id == highlightId }) else { return [] }
            return h.storyIds.compactMap { s.story($0) }
        }
        let now = clock()
        if authorId == s.me.id { return s.myActiveStories(now) }
        return s.storyCircles(now).first { $0.user.id == authorId }?.stories ?? []
    }

    func currentStory() -> Story? {
        guard let v = state.storyViewer else { return nil }
        let list = storiesOf(v.author)
        return list.indices.contains(v.index) ? list[v.index] : nil
    }

    /// Следующая история; кончились у автора — к следующему автору; у всех — закрыть.
    func nextStory() {
        guard let v = state.storyViewer else { return }
        let list = storiesOf(v.author)
        if v.index + 1 < list.count {
            var next = v
            next.index = v.index + 1
            state.storyViewer = next
        } else {
            let i = (v.authorIds.firstIndex(of: v.author) ?? -1) + 1
            guard v.authorIds.indices.contains(i), v.highlightId == nil else {
                closeStories()
                return
            }
            let author = v.authorIds[i]
            let nextList = storiesOf(author)
            var next = v
            next.author = author
            next.index = Stories.startIndex(nextList, state.seenStories)
            state.storyViewer = next
        }
        if let story = currentStory() { seeStory(story.id) }
    }

    func previousStory() {
        guard let v = state.storyViewer else { return }
        if v.index > 0 {
            var prev = v
            prev.index = v.index - 1
            state.storyViewer = prev
        } else {
            let i = (v.authorIds.firstIndex(of: v.author) ?? -1) - 1
            if v.authorIds.indices.contains(i) && v.highlightId == nil {
                var prev = v
                prev.author = v.authorIds[i]
                prev.index = 0
                state.storyViewer = prev
            }
        }
    }

    /// История показана: отметить просмотренной (и, если чужая, — что я её видел).
    func seeStory(_ storyId: Int64) {
        let me = state.me.id
        let now = clock()
        var s = state
        s.seenStories.insert(storyId)
        s.stories = s.stories.map { $0.id == storyId && $0.authorId != me ? Stories.view($0, me, now) : $0 }
        state = s
    }

    /// Сердечко на чужую историю: автору — «❤️» в переписку с цитатой истории.
    func likeStory(_ storyId: Int64) {
        guard let story = state.story(storyId) else { return }
        let me = state.me.id
        if story.authorId == me || story.views.contains(where: { $0.userId == me && $0.liked }) { return }
        let now = clock()
        state.stories = state.stories.map { $0.id == storyId ? Stories.view($0, me, now, liked: true) : $0 }
        replyToStory(storyId, "❤️")
    }

    /// Ответ на историю — сообщением автору, с цитатой истории над ним.
    func replyToStory(_ storyId: Int64, _ text: String) {
        guard let story = state.story(storyId) else { return }
        let clean = String(text.trimmingCharacters(in: .whitespacesAndNewlines).prefix(1000))
        if clean.isEmpty || story.authorId == state.me.id { return }
        guard let chatId = chatWith(story.authorId) else { return }
        append(chatId, Message(id: newId(), fromMe: true, text: clean, time: Self.now(), story: Stories.ref(story)))
    }

    /// Переписка с человеком: есть — её номер, нет — создать (не открывая).
    private func chatWith(_ userId: Int) -> Int? {
        guard let person = Demo.person(userId) else { return nil }
        if let existing = state.chats.first(where: { $0.peer.id == userId }) { return existing.id }
        let id = (state.chats.map(\.id).max() ?? 0) + 1
        state.chats.insert(Chat(id: id, peer: person, messages: []), at: 0)
        return id
    }

    /// Удалить свою историю — и из «Актуального». Файл удаляет экран.
    func deleteStory(_ storyId: Int64) {
        let me = state.me.id
        var s = state
        s.stories.removeAll { $0.id == storyId && $0.authorId == me }
        s.highlights = s.highlights.map { h -> Highlight in
            guard h.ownerId == me else { return h }
            var x = h
            x.storyIds.removeAll { $0 == storyId }
            if x.coverId == storyId { x.coverId = x.storyIds.first ?? 0 }
            return x
        }.filter { $0.ownerId != me || !$0.storyIds.isEmpty }
        state = s
        guard let v = state.storyViewer else { return }
        let list = storiesOf(v.author)
        if list.isEmpty {
            closeStories()
        } else if v.index >= list.count {
            var next = v
            next.index = list.count - 1
            state.storyViewer = next
        }
    }

    func openStoryViewers(_ storyId: Int64) {
        state.storyViewersOf = storyId
    }

    func closeStoryViewers() {
        state.storyViewersOf = nil
    }

    /// Добавить друга в «Близкие друзья» или убрать.
    func toggleCloseFriend(_ userId: Int) {
        guard state.following.contains(userId) else { return }
        if state.closeFriends.contains(userId) {
            state.closeFriends.remove(userId)
        } else {
            state.closeFriends.insert(userId)
        }
    }

    /// Новая подборка «Актуального» из своих историй. Ответ — её номер или nil.
    @discardableResult
    func createHighlight(_ title: String, _ storyIds: [Int64]) -> Int64? {
        let s = state
        var seen = Set<Int64>()
        let mine = storyIds.filter { id in
            s.stories.contains { $0.id == id && $0.authorId == s.me.id } && seen.insert(id).inserted
        }
        if mine.isEmpty { return nil }
        let clean = String(title.trimmingCharacters(in: .whitespacesAndNewlines).prefix(Stories.maxTitle))
        let h = Highlight(id: newId(), ownerId: s.me.id, title: clean.isEmpty ? t("Актуальное") : clean, storyIds: mine)
        state.highlights.append(h)
        return h.id
    }

    func renameHighlight(_ id: Int64, _ title: String) {
        let clean = String(title.trimmingCharacters(in: .whitespacesAndNewlines).prefix(Stories.maxTitle))
        updateHighlight(id) { h in
            if !clean.isEmpty { h.title = clean }
        }
    }

    func addToHighlight(_ id: Int64, _ storyId: Int64) {
        updateHighlight(id) { h in
            if !h.storyIds.contains(storyId) { h.storyIds.append(storyId) }
        }
    }

    func removeFromHighlight(_ id: Int64, _ storyId: Int64) {
        updateHighlight(id) { h in
            h.storyIds.removeAll { $0 == storyId }
            if h.coverId == storyId { h.coverId = h.storyIds.first ?? 0 }
        }
    }

    func setHighlightCover(_ id: Int64, _ storyId: Int64) {
        updateHighlight(id) { h in
            if h.storyIds.contains(storyId) { h.coverId = storyId }
        }
    }

    func deleteHighlight(_ id: Int64) {
        let me = state.me.id
        state.highlights.removeAll { $0.id == id && $0.ownerId == me }
    }

    /// Изменить свою подборку; опустевшая — убирается.
    private func updateHighlight(_ id: Int64, _ change: (inout Highlight) -> Void) {
        let me = state.me.id
        state.highlights = state.highlights.map { h -> Highlight in
            var x = h
            if x.id == id && x.ownerId == me { change(&x) }
            return x
        }.filter { $0.ownerId != me || !$0.storyIds.isEmpty }
    }

    /// Демо-истории друзей: у кого нет свежей — появляется; у друзей без
    /// «Актуального» — пара подборок из старых историй. Вызывается после входа.
    func ensureDemoStories() {
        var rng = SystemRandomNumberGenerator()
        ensureDemoStories(using: &rng)
    }

    func ensureDemoStories<G: RandomNumberGenerator>(using rng: inout G) {
        let s = state
        let now = clock()
        var added: [Story] = []
        var hs: [Highlight] = []
        for f in Demo.people {
            if !s.stories.contains(where: { $0.authorId == f.id && $0.active(now) }) && Int.random(in: 0..<3, using: &rng) != 0 {
                let count = 1 + Int.random(in: 0..<3, using: &rng)
                for i in 0..<count {
                    let low: Int64 = 600_000          // 10 минут
                    let high: Int64 = 72_000_000      // 20 часов
                    let ago: Int64 = Int64.random(in: low..<high, using: &rng) - Int64(i)
                    let close = Int.random(in: 0..<5, using: &rng) == 0
                    added.append(demoStory(f.id, at: now.addingTimeInterval(-Double(ago) / 1000), close: close, using: &rng))
                }
            }
            if !s.highlights.contains(where: { $0.ownerId == f.id }) {
                let titles = [t("Путешествия"), t("Друзья"), t("Еда")].shuffled(using: &rng)
                let take = 1 + Int.random(in: 0..<2, using: &rng)
                for title in titles.prefix(take) {
                    let size = 2 + Int.random(in: 0..<3, using: &rng)
                    var old: [Story] = []
                    for _ in 0..<size {
                        let days = Int64.random(in: 3..<60, using: &rng)
                        old.append(demoStory(f.id, at: now.addingTimeInterval(-Double(days * Stories.ttlMs) / 1000), using: &rng))
                    }
                    added += old
                    hs.append(Highlight(id: newId(), ownerId: f.id, title: title, storyIds: old.map(\.id)))
                }
            }
        }
        if added.isEmpty && hs.isEmpty { return }
        var next = state
        next.stories += added
        next.highlights += hs
        state = next
    }

    private func demoStory<G: RandomNumberGenerator>(_ authorId: Int, at: Date, close: Bool = false, using rng: inout G) -> Story {
        let withText = Int.random(in: 0..<3, using: &rng) == 0
        let text = withText ? t(Stories.demoTexts[Int.random(in: 0..<Stories.demoTexts.count, using: &rng)]) : ""
        let bg = Int.random(in: 0..<Stories.backgrounds.count, using: &rng)
        let seed = Int.random(in: 0..<1000, using: &rng)
        return Story(id: newId(), authorId: authorId, createdAt: at, text: text, bg: bg, seed: seed, closeFriends: close)
    }

    /// Демо-жизнь историй: друг выкладывает новую, мою историю смотрят
    /// (близкие — «для близких»), иногда отвечают на неё в личку.
    func storyEvent<G: RandomNumberGenerator>(_ friend: User, using rng: inout G) {
        let s = state
        let now = clock()
        let mine = s.myActiveStories(now)
        if !mine.isEmpty && Int.random(in: 0..<3, using: &rng) != 0 {
            let story = mine[Int.random(in: 0..<mine.count, using: &rng)]
            if story.closeFriends && !s.closeFriends.contains(friend.id) { return }
            let liked = Int.random(in: 0..<4, using: &rng) == 0
            state.stories = state.stories.map { $0.id == story.id ? Stories.view($0, friend.id, now, liked: liked) : $0 }
            if Int.random(in: 0..<5, using: &rng) == 0 {
                let lines = ["Красота!", "Где это?", "Хочу туда же", "😍"]
                let text = t(lines[Int.random(in: 0..<lines.count, using: &rng)])
                guard let chatId = chatWith(friend.id) else { return }
                append(chatId, Message(id: newId(), fromMe: false, text: text, time: Self.now(), story: Stories.ref(story)))
                record(.storyReply, friend, targetId: story.id, preview: text)
                if state.openChatId != chatId { notify(Nudges.message(friend, text, chatId: chatId)) }
            }
        } else if !s.stories.contains(where: { $0.authorId == friend.id && $0.active(now) }) {
            let close = Int.random(in: 0..<5, using: &rng) == 0
            let story = demoStory(friend.id, at: now, close: close, using: &rng)
            state.stories.append(story)
        }
        // Чужие истории старше суток, не попавшие в «Актуальное», больше не нужны
        pruneStories(now)
    }

    /// Убрать чужие истории, которые истекли и не лежат в «Актуальном».
    func pruneStories(_ now: Date? = nil) {
        let moment = now ?? clock()
        let me = state.me.id
        let kept = Set(state.highlights.flatMap(\.storyIds))
        let stories = state.stories.filter { $0.authorId == me || $0.active(moment) || kept.contains($0.id) }
        if stories.count == state.stories.count { return }
        var s = state
        s.stories = stories
        s.seenStories = s.seenStories.intersection(stories.map(\.id))
        state = s
    }

    // MARK: - Репосты

    /// Копия записи для репоста или сообщения; у репоста без слов — сам оригинал.
    func postRef(_ post: Post) -> PostRef {
        if let root = post.repostOf, post.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, post.media.isEmpty {
            return root
        }
        return PostRef(
            postId: post.id, authorId: post.author.id, authorName: post.author.name, text: String(post.text.prefix(500)),
            photo: post.media.first(where: { $0.kind == .photo && !$0.sticker })?.fileName ?? "",
            seed: post.artSeed ?? 0, createdAt: post.createdAt
        )
    }

    func openRepost(_ postId: Int64) {
        var s = state
        s.repostingPostId = postId
        s.sharingPostId = nil
        state = s
    }

    func openSendToChat(_ postId: Int64) {
        var s = state
        s.sharingPostId = postId
        s.repostingPostId = nil
        state = s
    }

    func closeRepost() {
        var s = state
        s.repostingPostId = nil
        s.sharingPostId = nil
        state = s
    }

    /// Репост в свою ленту: оригинал карточкой, comment — свои слова (может
    /// быть пустым). Закрытые профили и свои записи без слов не репостятся.
    /// Ответ — текст ошибки или nil.
    @discardableResult
    func repost(_ postId: Int64, _ comment: String = "") -> String? {
        let s = state
        guard let post = s.findPost(postId) else { return t("Запись не найдена") }
        if post.author.closed && post.author.id != s.me.id { return t("Записями закрытого профиля нельзя поделиться") }
        let ref = postRef(post)
        let clean = comment.trimmingCharacters(in: .whitespacesAndNewlines)
        if ref.authorId == s.me.id && clean.isEmpty { return t("Добавьте пару слов к своей записи") }
        Telemetry.track(.reposted)
        let mine = Post(id: newId(), author: s.me, text: String(clean.prefix(2000)), minutesAgo: 0, createdAt: clock(), repostOf: ref)
        func bump(_ p: Post) -> Post {
            guard p.id == ref.postId else { return p }
            var x = p
            x.reposts += 1
            return x
        }
        var next = state
        next.posts = [mine] + next.posts.map(bump)
        next.groups = next.groups.map { g in
            var x = g
            x.posts = g.posts.map(bump)
            return x
        }
        next.repostingPostId = nil
        state = next
        return nil
    }

    /// Отправить запись в переписку — карточкой, со своими словами.
    func sendPostToChat(_ postId: Int64, _ chatId: Int, _ text: String = "") {
        guard let post = state.findPost(postId) else { return }
        Telemetry.track(.linkShared)
        let clean = String(text.trimmingCharacters(in: .whitespacesAndNewlines).prefix(1000))
        append(chatId, Message(id: newId(), fromMe: true, text: clean, time: Self.now(), post: postRef(post)))
        state.sharingPostId = nil
    }

    /// Нажали на запись внутри репоста или сообщения. false — её нет на этом телефоне.
    @discardableResult
    func openPostRef(_ ref: PostRef) -> Bool { openLink(.postLink(ref.postId)) }

    // MARK: - Отметки на фото

    /// Записи, где меня отметили на фото (у себя на телефоне — в своих и друзей).
    var taggedPosts: [Post] { state.taggedPosts }

    /// Убрать отметку с собой на чужом фото (с сервером — запрос автору записи не нужен).
    func untagMe(_ postId: Int64) {
        let me = state.me.id
        updatePosts(postId) { p in
            p.media = p.media.map { a in
                var x = a
                x.tags.removeAll { $0.userId == me }
                return x
            }
        }
    }

    // MARK: - Звонки

    /// Связь звонков: демо, пока нет сервера.
    @ObservationIgnored var callTransport: CallTransport = DemoCallTransport.shared
    @ObservationIgnored private var callTask: Task<Void, Never>?
    /// Приложение на экране: тогда входящий звонок и так во весь экран, а
    /// свёрнутому — уведомлением. Задаёт MemoryApp; без него — «свёрнуто».
    @ObservationIgnored var isForeground: () -> Bool = { false }

    /// Позвонить. Ответ — текст ошибки или nil.
    @discardableResult
    func startCall(_ peerId: Int, video: Bool) -> String? {
        let s = state
        if s.call != nil { return t("Звонок уже идёт") }
        if s.blocked.contains(peerId) { return t("Вы заблокировали этого человека") }
        guard Demo.person(peerId) != nil else { return t("Не удалось позвонить") }
        Telemetry.track(video ? .videoCall : .voiceCall)
        let call = ActiveCall(id: newId(), peerId: peerId, video: video, outgoing: true, phase: .calling,
                              startedAt: clock(), cameraOn: video)
        callTransport.connect(room: call.room, token: "", video: video)
        state.call = call
        if demoReplies {
            callTask?.cancel()
            // Демо: чаще всего друг отвечает через пару секунд, иногда — нет
            let noAnswer = Int.random(in: 0..<8) == 0
            let delay: UInt64 = noAnswer ? 12_000_000_000 : UInt64.random(in: 2_000_000_000..<5_000_000_000)
            callTask = Task { @MainActor [weak self] in
                try? await Task.sleep(nanoseconds: delay)
                guard !Task.isCancelled, let self else { return }
                if noAnswer { self.callTimeout() } else { self.callAnswered() }
            }
        }
        return nil
    }

    /// Собеседник ответил — разговор пошёл.
    func callAnswered() {
        guard var c = state.call, c.phase == .calling else { return }
        c.phase = .active
        c.connectedAt = clock()
        state.call = c
    }

    /// Звонят мне (демо — друг; с сервером — пуш). Ничего не делает, если звонки от него запрещены.
    func incomingCall(_ peerId: Int, video: Bool) {
        let s = state
        if s.call != nil || !Calls.allowed(peerId, friends: s.following, blocked: s.blocked, who: s.privacy.calls) { return }
        guard let person = Demo.person(peerId) else { return }
        let call = ActiveCall(id: newId(), peerId: peerId, video: video, outgoing: false, phase: .incoming,
                              startedAt: clock(), cameraOn: video)
        state.call = call
        if demoReplies {
            callTask?.cancel()
            callTask = Task { @MainActor [weak self] in
                try? await Task.sleep(nanoseconds: UInt64(Calls.ringMs) * 1_000_000)
                guard !Task.isCancelled, let self else { return }
                self.callTimeout()
            }
        }
        // Приложение открыто — звонок и так на весь экран; свёрнуто — уведомлением
        if !isForeground() { notify(Nudges.incomingCall(person, video: video)) }
    }

    /// Принять входящий.
    func acceptCall() {
        guard var c = state.call, c.phase == .incoming else { return }
        callTask?.cancel()
        callTask = nil
        callTransport.connect(room: c.room, token: "", video: c.video)
        c.phase = .active
        c.connectedAt = clock()
        state.call = c
    }

    /// Отклонить входящий.
    func declineCall() {
        guard let c = state.call, c.phase == .incoming else { return }
        finishCall(Calls.record(c, clock(), declined: true))
    }

    /// Положить трубку / отменить исходящий.
    func endCall() {
        guard let c = state.call else { return }
        finishCall(Calls.record(c, clock()))
    }

    /// Никто не ответил: исходящий — «нет ответа», входящий — пропущенный.
    func callTimeout() {
        guard let c = state.call, c.phase != .active else { return }
        finishCall(Calls.record(c, clock()))
        if !c.outgoing, let person = Demo.person(c.peerId) {
            record(.missedCall, person, targetId: c.id)
            notify(Nudges.missedCall(person, video: c.video, chatId: chatWith(c.peerId) ?? 0))
        }
    }

    private func finishCall(_ entry: CallRecord) {
        callTask?.cancel()
        callTask = nil
        callTransport.hangUp()
        let chatId = chatWith(entry.peerId)
        var s = state
        s.call = nil
        s.callLog = Calls.add(s.callLog, entry)
        state = s
        // Строка звонка — в переписке с человеком
        if let chatId {
            append(chatId, Message(id: newId(), fromMe: entry.outgoing, text: "", time: Self.now(), call: entry))
        }
    }

    func toggleMute() {
        updateCall { c in
            c.muted.toggle()
            callTransport.setMuted(c.muted)
        }
    }

    func toggleCamera() {
        updateCall { c in
            c.cameraOn.toggle()
            callTransport.setCamera(on: c.cameraOn, front: c.frontCamera)
        }
    }

    func flipCamera() {
        updateCall { c in
            c.frontCamera.toggle()
            callTransport.setCamera(on: c.cameraOn, front: c.frontCamera)
        }
    }

    func toggleSpeaker() {
        updateCall { $0.speaker.toggle() }
    }

    private func updateCall(_ change: (inout ActiveCall) -> Void) {
        guard var c = state.call else { return }
        change(&c)
        state.call = c
    }

    /// Перезвонить из истории звонков или переписки. Ответ — текст ошибки или nil.
    @discardableResult
    func callBack(_ entry: CallRecord) -> String? { startCall(entry.peerId, video: entry.video) }

    func clearCallLog() {
        state.callLog = []
    }

    // MARK: - Эфиры

    /// Видеосвязь эфиров: демо, пока нет сервера LiveKit.
    @ObservationIgnored var liveTransport: LiveTransport = DemoLiveTransport.shared
    @ObservationIgnored private var liveTask: Task<Void, Never>?

    func openLiveStart() {
        state.liveStarting = true
    }

    func closeLiveStart() {
        state.liveStarting = false
    }

    /// Начать свой эфир. Ответ — текст ошибки или nil.
    @discardableResult
    func startLive(_ title: String, friendsOnly: Bool) -> String? {
        if state.myLive != nil { return t("Эфир уже идёт") }
        let clean = String(title.trimmingCharacters(in: .whitespacesAndNewlines).prefix(Lives.maxTitle))
        if clean.isEmpty { return t("Назовите эфир — так друзья поймут, о чём он") }
        let live = LiveStream(id: newId(), hostId: state.me.id, title: clean, startedAt: clock(),
                              friendsOnly: friendsOnly, seed: Int.random(in: 0..<1000))
        Telemetry.track(.liveStarted)
        liveTransport.publish(room: live.room, token: "")
        var s = state
        s.myLive = live
        s.watchingLiveId = live.id
        s.liveStarting = false
        s.liveSummary = nil
        state = s
        ensureLiveTicker()
        return nil
    }

    /// Закончить свой эфир — итог на экране.
    func endLive() {
        guard var live = state.myLive else { return }
        liveTransport.leave()
        live.endedAt = clock()
        live.viewers = 0
        var s = state
        s.myLive = nil
        s.watchingLiveId = nil
        s.liveSummary = live
        s.confirmEndLive = false
        state = s
    }

    /// Вернуться на экран своего идущего эфира.
    func returnToMyLive() {
        guard let live = state.myLive else { return }
        state.watchingLiveId = live.id
    }

    func cancelEndLive() {
        state.confirmEndLive = false
    }

    func dismissLiveSummary() {
        state.liveSummary = nil
    }

    /// Смотреть эфир друга.
    func watchLive(_ liveId: Int64) {
        guard let live = state.visibleLives.first(where: { $0.id == liveId }) else { return }
        Telemetry.track(.liveWatched)
        liveTransport.join(room: live.room, token: "")
        var s = state
        s.watchingLiveId = liveId
        s.activityOpen = false
        s.searchOpen = false
        s.lives = s.lives.map { $0.id == liveId ? Lives.viewers($0, 1) : $0 }
        state = s
        ensureLiveTicker()
    }

    /// Выйти из эфира друга.
    func leaveLive() {
        guard let id = state.watchingLiveId else { return }
        if state.myLive?.id == id { return }
        liveTransport.leave()
        var s = state
        s.watchingLiveId = nil
        s.lives = s.lives.map { $0.id == id ? Lives.viewers($0, -1) : $0 }
        state = s
    }

    private func updateLive(_ id: Int64, _ change: (LiveStream) -> LiveStream) {
        var s = state
        if let mine = s.myLive, mine.id == id { s.myLive = change(mine) }
        s.lives = s.lives.map { $0.id == id ? change($0) : $0 }
        state = s
    }

    /// Комментарий в открытом эфире.
    func sendLiveComment(_ text: String) {
        guard let id = state.watchingLiveId else { return }
        let clean = String(text.trimmingCharacters(in: .whitespacesAndNewlines).prefix(Lives.maxComment))
        if clean.isEmpty { return }
        let me = state.me
        let comment = LiveComment(id: newId(), authorId: me.id, name: me.name, text: clean, at: clock(), hue: me.hue)
        updateLive(id) { Lives.addComment($0, comment) }
    }

    /// Сердечко в открытом эфире.
    func sendHeart() {
        guard let id = state.watchingLiveId else { return }
        updateLive(id) { live in
            var x = live
            x.hearts += 1
            return x
        }
    }

    /// Друг начал эфир: в «Историях», в ленте активности и уведомлением.
    func friendGoesLive<G: RandomNumberGenerator>(_ friend: User, using rng: inout G) {
        if state.blocked.contains(friend.id) || state.lives.contains(where: { $0.hostId == friend.id && $0.live }) { return }
        let title = Lives.demoTitle(Int.random(in: 0..<100, using: &rng))
        let live = LiveStream(id: newId(), hostId: friend.id, title: title, startedAt: clock(),
                              viewers: 3 + Int.random(in: 0..<20, using: &rng), seed: Int.random(in: 0..<1000, using: &rng))
        state.lives = [Lives.viewers(live, 0)] + state.lives
        record(.live, friend, targetId: live.id, preview: title)
        notify(Nudges.liveStarted(friend, title))
        ensureLiveTicker()
    }

    /// Шаг демо-эфиров: в свой эфир приходят зрители, сердечки и
    /// комментарии; эфиры друзей живут и через несколько минут кончаются.
    func liveTick<G: RandomNumberGenerator>(using rng: inout G) {
        let now = clock()
        var s = state
        let people = Demo.people.filter { !s.blocked.contains($0.id) }
        var mine = s.myLive
        if var m = mine {
            m = Lives.viewers(m, Int.random(in: -1..<4, using: &rng))
            m.hearts += Int.random(in: 0..<6, using: &rng)
            if !people.isEmpty && Int.random(in: 0..<3, using: &rng) == 0 {
                let p = people[Int.random(in: 0..<people.count, using: &rng)]
                let text = Lives.demoComment(Int.random(in: 0..<100, using: &rng))
                m = Lives.addComment(m, LiveComment(id: newId(), authorId: p.id, name: p.name, text: text, at: now, hue: p.hue))
            }
            mine = m
        }
        var lives: [LiveStream] = []
        for l in s.lives where l.live {
            // Эфир друга идёт 3–10 минут; смотрите — не кончится, пока вы в нём
            if s.watchingLiveId != l.id && now.timeIntervalSince(l.startedAt) > 3 * 60 && Int.random(in: 0..<8, using: &rng) == 0 {
                continue
            }
            var x = Lives.viewers(l, Int.random(in: -2..<3, using: &rng))
            x.hearts = l.hearts + Int.random(in: 0..<4, using: &rng)
            if !people.isEmpty && Int.random(in: 0..<2, using: &rng) == 0 {
                let p = people[Int.random(in: 0..<people.count, using: &rng)]
                if p.id != l.hostId {
                    let text = Lives.demoComment(Int.random(in: 0..<100, using: &rng))
                    x = Lives.addComment(x, LiveComment(id: newId(), authorId: p.id, name: p.name, text: text, at: now, hue: p.hue))
                }
            }
            lives.append(x)
        }
        s.myLive = mine
        s.lives = lives
        if let id = s.watchingLiveId {
            let stillThere = mine?.id == id || lives.contains(where: { $0.id == id })
            if !stillThere { s.watchingLiveId = nil }
        }
        state = s
    }

    /// Демо-эфиры живут сами, пока идёт хоть один. В тестах (без демо-ответов) — только liveTick вручную.
    private func ensureLiveTicker() {
        guard demoReplies, liveTask == nil else { return }
        liveTask = Task { @MainActor [weak self] in
            while !Task.isCancelled {
                guard let self else { return }
                if self.state.myLive == nil && self.state.lives.isEmpty {
                    self.liveTask = nil
                    return
                }
                try? await Task.sleep(nanoseconds: UInt64.random(in: 1_500_000_000...3_000_000_000))
                if Task.isCancelled { return }
                var rng = SystemRandomNumberGenerator()
                self.liveTick(using: &rng)
            }
        }
    }

    // MARK: - Секретная и исчезающая переписка

    /// Хранилище своего ключа шифрования (SecureStore); без него — в памяти, для тестов.
    @ObservationIgnored private var keyStore: KeyValueStore?
    @ObservationIgnored private var memoryKey: ChatKeyPair?

    /// Последний конверт, который ушёл бы на сервер, — для проверки, что текста в нём нет.
    @ObservationIgnored private(set) var lastEnvelope: String?

    /// Ключи файлов последнего секретного сообщения (attachmentId → FileKey.encoded) — для проверки.
    @ObservationIgnored private(set) var lastFileKeys: [String: String] = [:]

    func attachKeys(_ store: KeyValueStore) {
        keyStore = store
    }

    /// Свой ключ: открытый показывается в коде безопасности, закрытый не покидает телефон.
    func myKeys() -> ChatKeyPair {
        if let store = keyStore { return ChatKeys(store).identity(state.me.handle) }
        if let key = memoryKey { return key }
        let key = ChatKeyPair.generate()
        memoryKey = key
        return key
    }

    /// Открытый ключ собеседника. Пока сервера нет — его демо-ключ.
    private func peerKeys(_ chat: Chat) -> ChatKeyPair { ChatCrypto.demoPeer(chat.peer.id) }

    private func aad(_ chat: Chat) -> String {
        "memory-chat:" + [state.me.handle, chat.peer.handle].sorted().joined(separator: "|")
    }

    private func sealFor(_ chat: Chat, _ text: String) -> String? {
        guard let key = try? ChatCrypto.sessionKey(myKeys(), peerKeys(chat).publicKey) else { return nil }
        return try? ChatCrypto.seal(key, text, aad: aad(chat))
    }

    /// «Телефон собеседника» открывает конверт своим ключом.
    private func peerOpen(_ chat: Chat, _ envelope: String) -> String? {
        guard let key = try? ChatCrypto.sessionKey(peerKeys(chat), myKeys().publicKey) else { return nil }
        return ChatCrypto.open(key, envelope, aad: aad(chat))
    }

    /// Включить или выключить сквозное шифрование переписки. Выключили — отметка о сверке снимается.
    func setSecret(_ chatId: Int, _ on: Bool) {
        // Группы — без сквозного шифрования (ключи на каждого участника — следующий шаг)
        if state.chats.first(where: { $0.id == chatId })?.isGroup == true { return }
        if on { Telemetry.track(.secretChat) }
        updateChat(chatId) { c in
            c.secret = on
            if !on { c.verified = false }
        }
    }

    /// Код безопасности переписки — 12 групп по 5 цифр.
    func safetyNumber(_ chatId: Int) -> String? {
        guard let chat = state.chats.first(where: { $0.id == chatId }) else { return nil }
        return ChatCrypto.safetyNumber(myKeys().publicKey, peerKeys(chat).publicKey)
    }

    /// Человек сверил код с собеседником — отметка «проверено».
    func markVerified(_ chatId: Int, _ verified: Bool = true) {
        updateChat(chatId) { $0.verified = verified }
    }

    /// Исчезающие сообщения: новые исчезнут через ttl. Уже отправленные не меняются.
    func setDisappearing(_ chatId: Int, _ ttl: Disappear) {
        if ttl != .off { Telemetry.track(.disappearing) }
        updateChat(chatId) { $0.disappearMs = ttl.ms }
    }

    /// Убрать исчезнувшие сообщения. Ответ — их вложения (файлы удаляет
    /// вызывающий: модель о файлах не знает).
    @discardableResult
    func sweepExpired(now: Date? = nil) -> [Attachment] {
        let moment = now ?? clock()
        func expired(_ m: Message) -> Bool {
            guard let at = m.expiresAt else { return false }
            return at <= moment
        }
        guard state.chats.contains(where: { c in c.messages.contains(where: expired) }) else { return [] }
        var gone: [Attachment] = []
        state.chats = state.chats.map { chat in
            let dead = chat.messages.filter(expired)
            if dead.isEmpty { return chat }
            var c = chat
            for m in dead { gone += m.attachments }
            c.messages = chat.messages.filter { !expired($0) }
            if let pinned = c.pinnedId, !c.messages.contains(where: { $0.id == pinned }) { c.pinnedId = nil }
            return c
        }
        return gone
    }

    // MARK: - Лента

    /// «Нравится» у записи, где бы она ни была: в ленте или в группе.
    func likeAny(_ postId: Int64) {
        if let group = state.groups.first(where: { g in g.posts.contains(where: { $0.id == postId }) }) {
            toggleGroupPostLike(group.id, postId)
        } else {
            toggleLike(postId)
        }
    }

    func toggleLike(_ postId: Int64) {
        state.posts = state.posts.map { Self.toggled($0, postId) }
        if let p = state.posts.first(where: { $0.id == postId }), p.outboxKey.isEmpty {
            // Своя ещё не ушедшая запись — серверу пока нечего отмечать
            net.queueLike(postId, liked: p.liked)
        }
        refreshNetCounters()
        kickNet()
    }

    /// Новая запись: текст, фото и видео, чек-ин, опрос, место. Пустую (без текста и вложений) не публикуем.
    func publish(_ text: String, media: [Attachment] = [], checkIn: CheckIn? = nil, poll: PollDraft? = nil, place: SharedPlace? = nil) {
        Telemetry.track(.postPublished)
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let built = poll.flatMap { $0.build(newId()) }
        if trimmed.isEmpty && media.isEmpty && checkIn == nil && built == nil { return }
        if !postLimiter.allow(nowMs()) {
            tooOften()
            return
        }
        // Отмечать на фото можно только друзей — остальные отметки отбрасываются
        let friendIds = state.following
        let cleanMedia: [Attachment] = media.map { a in
            if a.tags.isEmpty { return a }
            var x = a
            var seen = Set<Int>()
            x.tags = a.tags.filter { friendIds.contains($0.userId) && seen.insert($0.userId).inserted }
            return x
        }
        media.filter(\.isGif).forEach(rememberGif)
        var post = Post(
            id: newId(), author: state.me, text: trimmed, minutesAgo: 0,
            media: Array(cleanMedia.prefix(Self.maxMedia)), createdAt: clock(), checkIn: checkIn, poll: built, place: place
        )
        // На сервер — через очередь: без сети запись видна сразу и уйдёт, когда сеть появится
        post.outboxKey = net.queuePost(post)
        let before = state
        var next = state
        next.posts.insert(post, at: 0)
        if let checkIn {
            // Чек-ин сам засчитывается в общие цели «Вместе»
            next.together = next.together.afterCheckIn(checkIn)
            next.growth = next.growth.logged(GrowthEvent(type: .checkIn, at: clock(), detail: checkIn.activity))
            // Отклик: что изменилось от чек-ина — полка, цели, план, что откроется дальше
            next.echo = echoFor(checkIn, before: before, after: next)
        }
        state = next
        refreshNetCounters()
        kickNet()
    }

    private func echoFor(_ c: CheckIn, before: UiState, after: UiState) -> CheckInEcho {
        let kind = shelfKind(c.activity)
        let shelfAfter = after.shelf
        let rec = kind == "film" || kind == "book" ? after.recommendations(kind, limit: 1, shelf: shelfAfter).first : nil
        return checkInEcho(
            c, shelfBefore: before.shelf, shelfAfter: shelfAfter,
            togetherBefore: before.together, togetherAfter: after.together,
            plan: c.activity == "run" ? after.runPlan : nil,
            kmBefore: before.movedKm, kmAfter: after.movedKm, recommendation: rec
        ) { id in Demo.person(id)?.firstName ?? "" }
    }

    func dismissEcho() {
        state.echo = nil
    }

    // MARK: - Полка, память, рекомендации

    func openShelf(_ tab: ShelfTab = .films) {
        openProfileSection(.shelf)
        state.shelfTab = tab
    }

    func selectShelfTab(_ tab: ShelfTab) {
        state.shelfTab = tab
    }

    /// «Хочу посмотреть» / «Хочу прочитать» — в планы на полке, без записи в ленте.
    func planOnShelf(_ kind: String, _ subject: String) {
        let activity = kind == "book" ? "book" : "movie"
        if state.shelf.keys.contains(shelfKey(activity, subject)) { return }
        state.shelfMarks.append(ShelfMark(activity: activity, subject: subject.trimmingCharacters(in: .whitespacesAndNewlines),
                                          rating: 0, status: BookStatus.want.id, at: clock()))
    }

    /// Оценка на полке без записи: поставить звёзды уже отмеченному.
    func rateOnShelf(_ activity: String, _ subject: String, _ rating: Int) {
        let r = min(5, max(1, rating))
        let status = shelfKind(activity) == "book" ? BookStatus.done.id : ""
        state.shelfMarks.append(ShelfMark(activity: activity, subject: subject.trimmingCharacters(in: .whitespacesAndNewlines),
                                          rating: r, status: status, at: clock()))
    }

    /// «Не моё» — больше не советовать.
    func dismissRecommendation(_ key: String) {
        state.dismissedRecs.insert(key)
    }

    /// Любимое из знакомства: на полку с пятью звёздами — первые рекомендации сразу.
    func addFavorites(_ picks: [CatalogTitle]) {
        if picks.isEmpty { return }
        let now = clock()
        let existing = state.shelfMarks
        let marks: [ShelfMark] = picks.filter { p in
            !existing.contains { $0.key == shelfKey(p.kind == "book" ? "book" : "movie", p.title) }
        }.map { p in
            ShelfMark(activity: p.kind == "book" ? "book" : "movie", subject: p.title, rating: 5,
                      status: p.kind == "book" ? BookStatus.done.id : "", at: now)
        }
        state.shelfMarks += marks
        state.growth = state.growth.logged(GrowthEvent(type: .onboardingDone, at: now, detail: "\(picks.count)"))
    }

    /// «В этот день» закрыли — до завтра не показываем.
    func dismissMemory() {
        state.memorySeen = dayKey(clock())
    }

    // MARK: - Приглашения и журнал роста

    /// Приглашение к действию: текст со ссылкой для «Поделиться»; в журнал — что отправили.
    @discardableResult
    func invite(_ action: InviteAction, targetId: String = "", title: String = "") -> String {
        let s = state
        let invite = Invite(code: s.inviteCode, action: action, targetId: targetId, from: s.me.firstName)
        log(.inviteSent, action.id + (targetId.isEmpty ? "" : ":\(targetId)"))
        return Invites.message(invite, title: title)
    }

    func logCardShared(_ what: String) { log(.cardShared, what) }

    func logFeedback(_ idea: Bool) { log(.feedback, idea ? "idea" : "bug") }

    private func log(_ type: GrowthEventType, _ detail: String) {
        state.growth = state.growth.logged(GrowthEvent(type: type, at: clock(), detail: detail))
    }

    /// Открыли приложение по ссылке-приглашению. Своя ссылка — просто
    /// открываем, к чему она; чужая — запоминаем, кто позвал (если это
    /// новый человек без записей — он пришёл по приглашению).
    func openInvite(_ invite: Invite) {
        let own = invite.code == state.inviteCode
        let now = clock()
        var growth = state.growth.logged(GrowthEvent(type: .inviteOpened, at: now, detail: invite.code))
        var invitedBy = state.invitedBy
        if !own && invitedBy == nil && state.myPosts.isEmpty {
            invitedBy = invite
            growth = growth.logged(GrowthEvent(type: .joinedByInvite, at: now, detail: invite.code))
        }
        state.growth = growth
        state.invitedBy = invitedBy
        state.openedInvite = own ? nil : invite
        routeInvite(invite)
    }

    func dismissInvite() {
        state.openedInvite = nil
    }

    /// К чему зовёт приглашение — туда и ведём.
    private func routeInvite(_ invite: Invite) {
        let section: TogetherSection
        switch invite.action {
        case .drawing: section = .drawing
        case .book: section = .sharing
        case .outing: section = .outings
        case .lesson: section = .skills
        case .journey: section = .journeys
        case .cache: section = .caches
        case .shelf:
            openShelf(.forYou)
            return
        case .app:
            return
        }
        selectTab(.together)
        selectTogetherSection(section)
    }

    // MARK: - Чек-ины

    /// Свой чек-ин. nil — создан; иначе — что не так.
    @discardableResult
    func createCheckInTemplate(emoji: String, title: String, unit: String, hint: String) -> String? {
        let name = String(title.trimmingCharacters(in: .whitespacesAndNewlines).prefix(40))
        if name.isEmpty { return t("Назовите чек-ин") }
        let exists = state.checkInTemplates.contains { $0.title.lowercased() == name.lowercased() }
            || checkInActivities.contains { t($0.title).lowercased() == name.lowercased() }
        if exists { return t("Такой чек-ин уже есть") }
        let template = CheckInTemplate(
            id: "custom-" + String(UUID().uuidString.prefix(8)).lowercased(),
            emoji: emoji.isEmpty ? "⭐" : emoji, title: name,
            unit: String(unit.trimmingCharacters(in: .whitespaces).prefix(12)),
            hint: String(hint.trimmingCharacters(in: .whitespaces).prefix(60))
        )
        state.checkInTemplates.insert(template, at: 0)
        return nil
    }

    func deleteCheckInTemplate(_ id: String) {
        state.checkInTemplates.removeAll { $0.id == id }
    }

    /// Комментарий к записи в ленте или в группе.
    func addComment(_ postId: Int64, _ text: String, replyTo: String? = nil, sticker: Attachment? = nil) {
        Telemetry.track(.commentSent)
        let trimmed = String(text.trimmingCharacters(in: .whitespacesAndNewlines).prefix(1000))
        if trimmed.isEmpty && sticker == nil { return }
        if let target = state.findPost(postId), target.commentsOff, target.author.id != state.me.id {
            state.notice = t("Автор выключил комментарии")
            return
        }
        if !commentLimiter.allow(nowMs()) {
            tooOften()
            return
        }
        let comment = Comment(id: newId(), author: state.me, text: trimmed, createdAt: clock(), replyTo: replyTo, sticker: sticker)
        updatePosts(postId) { p in
            p.comments += 1
            p.commentList.append(comment)
        }
    }

    /// Пожаловаться на чужой комментарий: он скрывается у вас. Когда
    /// появится сервер, жалоба уйдёт модераторам.
    func reportComment(_ commentId: Int64) {
        state.reportedComments.insert(commentId)
    }

    /// Свой комментарий можно удалить.
    func deleteComment(_ postId: Int64, _ commentId: Int64) {
        let meId = state.me.id
        updatePosts(postId) { p in
            guard p.commentList.contains(where: { $0.id == commentId && $0.author.id == meId }) else { return }
            p.commentList.removeAll { $0.id == commentId }
            p.comments = max(0, p.comments - 1)
        }
    }

    /// «Нравится» комментарию к записи — в ленте или в группе.
    func toggleCommentLike(_ postId: Int64, _ commentId: Int64) {
        updatePosts(postId) { p in
            p.commentList = p.commentList.map { Self.flipLike($0, commentId) }
        }
    }

    /// Изменить запись — где бы она ни была: в ленте или в группе.
    private func updatePosts(_ postId: Int64, _ change: (inout Post) -> Void) {
        state.posts = state.posts.map { post in
            var p = post
            if p.id == postId { change(&p) }
            return p
        }
        state.groups = state.groups.map { group in
            var g = group
            g.posts = g.posts.map { post in
                var p = post
                if p.id == postId { change(&p) }
                return p
            }
            return g
        }
    }

    func openComments(_ postId: Int64) {
        state.commentsPrismId = nil
        state.commentsPostId = postId
    }

    func openPrismComments(_ prismId: Int64) {
        state.commentsPostId = nil
        state.commentsPrismId = prismId
    }

    func closeComments() {
        state.commentsPostId = nil
        state.commentsPrismId = nil
    }

    func openMedia(_ attachment: Attachment) {
        state.viewing = attachment
    }

    func closeMedia() {
        state.viewing = nil
    }

    /// Дружба взаимная. Уже друзья — удалить из друзей (интерфейс сначала
    /// спрашивает «Удалить из друзей?»); заявка ждёт ответа — отозвать её;
    /// человек сам прислал заявку — сразу друзья; иначе уходит заявка.
    /// В демо её принимает любой человек через несколько секунд.
    func toggleFriend(_ userId: Int) {
        if userId == state.me.id || state.blocked.contains(userId) { return }
        if state.following.contains(userId) {
            removeFriend(userId)
        } else if state.sentRequests.contains(userId) {
            acceptTasks.removeValue(forKey: userId)?.cancel()
            state.sentRequests.remove(userId)
        } else if state.followRequests.contains(userId) {
            approveRequest(userId)
        } else {
            state.sentRequests.insert(userId)
            scheduleAcceptance(userId)
        }
    }

    /// Удалить из друзей: записи закрытого профиля и «Истории» человека уходят.
    func removeFriend(_ userId: Int) {
        declined.insert(userId)
        state.following.remove(userId)
    }

    /// Таймеры демо-ответа на заявки: один на человека.
    @ObservationIgnored private var acceptTasks: [Int: Task<Void, Never>] = [:]

    /// В демо любой человек принимает заявку в друзья через 4–15 секунд.
    private func scheduleAcceptance(_ userId: Int) {
        guard demoReplies else { return }
        acceptTasks.removeValue(forKey: userId)?.cancel()
        acceptTasks[userId] = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: UInt64.random(in: 4_000_000_000...15_000_000_000))
            guard !Task.isCancelled, let self else { return }
            self.acceptTasks[userId] = nil
            self.acceptSentRequest(userId)
        }
    }

    /// Человек принял вашу заявку: вы друзья, записи видны, приходит уведомление.
    func acceptSentRequest(_ userId: Int) {
        guard state.sentRequests.contains(userId) else { return }
        state.sentRequests.remove(userId)
        state.followRequests.removeAll { $0 == userId }
        state.following.insert(userId)
        if let person = Demo.person(userId) {
            record(.friendAccepted, person)
            notify(Nudges.requestAccepted(person))
        }
    }

    /// Принять заявку в друзья: человек становится другом.
    func approveRequest(_ userId: Int) {
        acceptTasks.removeValue(forKey: userId)?.cancel()
        state.followRequests.removeAll { $0 == userId }
        state.sentRequests.remove(userId)
        state.following.insert(userId)
    }

    /// Отклонить заявку: человек не узнает об этом, заявка просто исчезает.
    func declineRequest(_ userId: Int) {
        declined.insert(userId)
        state.followRequests.removeAll { $0 == userId }
    }

    /// Кого отклонили или удалили из друзей: в демо они больше не присылают заявок сами.
    @ObservationIgnored private var declined: Set<Int> = []

    /// Новая заявка в друзья ко мне — от любого, кто не в чёрном списке и ещё не друг.
    func receiveFriendRequest(_ userId: Int) {
        guard let person = Demo.person(userId), userId != state.me.id,
              !state.followRequests.contains(userId), !state.blocked.contains(userId),
              !state.following.contains(userId), !state.sentRequests.contains(userId) else { return }
        state.followRequests.insert(userId, at: 0)
        record(.friendRequest, person)
        notify(Nudges.followRequest(person, common: person.interests.filter { state.me.interests.contains($0) }))
    }

    // MARK: - Чаты

    func openChat(_ id: Int) {
        state.tab = .chats
        state.openChatId = id
        // Страница человека остаётся под перепиской: «назад» вернёт на неё
        state.returnToChatId = nil
        state.chats = state.chats.map { chat in
            var c = chat
            if c.id == id { c.unread = 0 }
            return c
        }
        sendRead(id)
    }

    func closeChat() {
        state.openChatId = nil
    }

    /// «Написать» со страницы человека: существующая переписка или новая.
    func writeTo(_ userId: Int) {
        guard let person = Demo.person(userId) else { return }
        if let existing = state.chats.first(where: { $0.peer.id == userId }) {
            openChat(existing.id)
            return
        }
        let id = (state.chats.map(\.id).max() ?? 0) + 1
        state.chats.insert(Chat(id: id, peer: person, messages: []), at: 0)
        openChat(id)
    }

    /// Отправка. В демо собеседник «печатает» и через пару секунд отвечает —
    /// чтобы переписку можно было почувствовать без сервера.
    func send(_ chatId: Int, _ text: String, attachments: [Attachment] = [], replyTo: ReplyRef? = nil,
              poll: PollDraft? = nil, place: SharedPlace? = nil) {
        Telemetry.track(.messageSent)
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let built = poll.flatMap { $0.build(newId()) }
        if trimmed.isEmpty && attachments.isEmpty && built == nil && place == nil { return }
        if !messageLimiter.allow(nowMs()) {
            tooOften()
            return
        }
        // Секретная переписка: на сервер ушёл бы только конверт; демо-собеседник его расшифровывает своим ключом
        let secretChat: Chat? = state.chats.first(where: { $0.id == chatId && $0.secret })
        if let secretChat, !trimmed.isEmpty {
            let sealed = sealFor(secretChat, trimmed)
            lastEnvelope = sealed
            guard let envelope = sealed, peerOpen(secretChat, envelope) == trimmed else { return }
        }
        var outgoing = Message(
            id: newId(), fromMe: true, text: trimmed, time: Self.now(),
            attachments: Array(attachments.prefix(Self.maxMedia)),
            replyTo: replyTo, poll: built, place: place
        )
        outgoing.pending = true
        append(chatId, outgoing)
        // На сервер — через очередь. Секретная переписка: текст — конвертом, файлы
        // шифруются на телефоне своим ключом (FileCrypto), а ключ едет в имени
        // вложения, запечатанный ключом переписки, — сервер ничего не прочтёт
        var wire = outgoing
        var fileKeys: [String: String] = [:]
        if let secretChat {
            wire.text = trimmed.isEmpty ? "" : (lastEnvelope ?? "")
            var sealedAttachments: [Attachment] = []
            for a in outgoing.attachments {
                let key: String = FileKey.random().encoded()
                guard let sealed = sealFor(secretChat, key) else { continue }
                fileKeys[a.id] = key
                var x = a
                x.name = FileCrypto.sealed + sealed
                x.tags = []
                sealedAttachments.append(x)
            }
            wire.attachments = sealedAttachments
        }
        lastFileKeys = fileKeys
        let hasText: Bool = !wire.text.isEmpty || !wire.attachments.isEmpty
        let hasExtra: Bool = wire.poll != nil || wire.place != nil
        let hasContent: Bool = hasText || hasExtra
        if hasContent {
            net.queueMessage(chatId, wire, secretKeys: fileKeys)
        } else {
            updateMessage(chatId, outgoing.id) { $0.pending = false }
        }
        refreshNetCounters()
        kickNet()
        attachments.filter(\.isGif).forEach(rememberGif)
        // Опрос в переписке: собеседник отвечает голосом через пару секунд
        if let built {
            later(4, 12) { model in
                let pick = Int.random(in: 0..<built.options.count)
                model.updateMessagePoll(chatId, built.id) { $0.addVotes([pick: 1]) }
            }
        }
        guard demoReplies else { return }
        let delays = replyDelays
        Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(delays.typing * 1_000_000_000))
            self?.setTyping(chatId, true)
            try? await Task.sleep(nanoseconds: UInt64(delays.reply * 1_000_000_000))
            guard let self else { return }
            self.setTyping(chatId, false)
            let reply = Demo.replies[self.replyIndex % Demo.replies.count]
            self.replyIndex += 1
            self.append(chatId, Message(id: self.newId(), fromMe: false, text: reply, time: Self.now()))
            // Открытая переписка — ответ и так на экране; у секретной текст в уведомление не попадает
            if let chat = self.state.chats.first(where: { $0.id == chatId }), !chat.muted, self.state.openChatId != chatId {
                self.notify(Nudges.message(chat.peer, chat.secret ? t("Новое сообщение") : reply, chatId: chatId))
            }
        }
    }

    /// Удалить сообщение. forEveryone = false — только у себя: у собеседника
    /// оно останется. true — у обоих. Пока сервера нет, собеседник — демо, и
    /// на этом телефоне оба варианта убирают сообщение из переписки; с
    /// сервером «у всех» уйдёт собеседнику командой удаления.
    func deleteMessage(_ chatId: Int, _ messageId: Int64, forEveryone: Bool) {
        state.chats = state.chats.map { chat in
            var c = chat
            if c.id == chatId {
                c.messages.removeAll { $0.id == messageId }
                // Удалили закреплённое — полоска вверху уходит
                if c.pinnedId == messageId { c.pinnedId = nil }
            }
            return c
        }
        if forEveryone { deletedForEveryone.insert(messageId) }
    }

    /// Закрепить сообщение вверху переписки; повторно — открепить. Закреплённое одно.
    func togglePin(_ chatId: Int, _ messageId: Int64) {
        state.chats = state.chats.map { chat in
            var c = chat
            if c.id == chatId { c.pinnedId = c.pinnedId == messageId ? nil : messageId }
            return c
        }
    }

    func unpin(_ chatId: Int) {
        state.chats = state.chats.map { chat in
            var c = chat
            if c.id == chatId { c.pinnedId = nil }
            return c
        }
    }

    // MARK: Меню переписки в списке чатов (долгое нажатие)

    /// Закрепить переписку вверху списка; повторно — открепить.
    func toggleChatOnTop(_ chatId: Int) { updateChat(chatId) { $0.onTop.toggle() } }

    /// Без звука: уведомлений нет, непрочитанные не попадают в счётчик на вкладке.
    func toggleChatMute(_ chatId: Int) { updateChat(chatId) { $0.muted.toggle() } }

    func markRead(_ chatId: Int) { updateChat(chatId) { $0.unread = 0 } }

    /// Отметить непрочитанной — чтобы вернуться к переписке позже.
    func markUnread(_ chatId: Int) { updateChat(chatId) { $0.unread = max($0.unread, 1) } }

    /// Очистить историю: сообщения уходят, сама переписка остаётся в списке.
    func clearHistory(_ chatId: Int) {
        updateChat(chatId) { c in
            c.messages = []
            c.pinnedId = nil
            c.unread = 0
        }
    }

    /// Удалить переписку целиком; открытая — закрывается.
    func deleteChat(_ chatId: Int) {
        state.chats.removeAll { $0.id == chatId }
        if state.openChatId == chatId { state.openChatId = nil }
        if state.returnToChatId == chatId { state.returnToChatId = nil }
    }

    private func updateChat(_ chatId: Int, _ change: (inout Chat) -> Void) {
        state.chats = state.chats.map { chat in
            var c = chat
            if c.id == chatId { change(&c) }
            return c
        }
    }

    /// Цитата для ответа на сообщение: автор и начало текста (или что приложено).
    func replyRef(_ chatId: Int, _ message: Message) -> ReplyRef {
        let peer = state.chats.first { $0.id == chatId }?.peer.name ?? ""
        let author = message.fromMe ? state.me.name : peer
        return ReplyRef(messageId: message.id, author: author, preview: String(previewOf(message).prefix(120)))
    }

    /// Переслать сообщение в другую переписку: текст и вложения уходят от
    /// вашего имени с пометкой, чьё оно было. Открывается переписка-получатель.
    func forward(_ fromChatId: Int, _ messageId: Int64, to toChatId: Int) {
        guard let from = state.chats.first(where: { $0.id == fromChatId }),
              let original = from.messages.first(where: { $0.id == messageId }),
              state.chats.contains(where: { $0.id == toChatId }) else { return }
        let author = original.forwardedFrom ?? (original.fromMe ? state.me.name : from.peer.name)
        // Пересланный опрос — новый, с чистыми голосами; место — как есть
        let poll = original.poll.map { $0.fresh(id: newId()) }
        append(toChatId, Message(
            id: newId(), fromMe: true, text: original.text, time: Self.now(),
            attachments: original.attachments, forwardedFrom: author,
            poll: poll, place: original.place, product: original.product
        ))
        openChat(toChatId)
    }

    /// Какие сообщения удалены «у всех» — для будущей отправки на сервер.
    @ObservationIgnored private(set) var deletedForEveryone: Set<Int64> = []

    private func append(_ chatId: Int, _ message: Message) {
        guard var chat = state.chats.first(where: { $0.id == chatId }) else { return }
        let reading = state.openChatId == chatId
        // Настройки переписки — на каждое новое сообщение: шифрование и срок жизни
        var stamped = message
        if chat.secret { stamped.secret = true }
        if stamped.expiresAt == nil && chat.disappearMs > 0 {
            stamped.expiresAt = clock().addingTimeInterval(Double(chat.disappearMs) / 1000)
        }
        chat.messages.append(stamped)
        if !message.fromMe && !reading { chat.unread += 1 }
        // Чат с новым сообщением поднимается наверх списка
        state.chats = [chat] + state.chats.filter { $0.id != chatId }
    }

    private func setTyping(_ chatId: Int, _ typing: Bool) {
        state.chats = state.chats.map { chat in
            var c = chat
            if c.id == chatId { c.typing = typing }
            return c
        }
    }

    // MARK: - Группы (переписки)

    /// Новая групповая переписка: название и друзья. Номер выбирает телефон
    /// (случайный, сервер примет или ответит 409) — так группа живёт и без
    /// сети. Ответ — номер переписки; nil — без названия или без участников.
    /// (Не путать с createGroup(name:description:topic:) — группой по интересам.)
    func createGroup(_ title: String, _ memberIds: [Int]) -> Int? {
        let s = state
        let clean = String(title.trimmingCharacters(in: .whitespacesAndNewlines).prefix(ChatGroup.maxTitle))
        var ids: [Int] = []
        for id in memberIds where s.following.contains(id) && !s.blocked.contains(id) && id != s.me.id && !ids.contains(id) {
            ids.append(id)
        }
        if clean.isEmpty || ids.isEmpty { return nil }
        if ids.count + 1 > ChatGroup.maxMembers { return nil }
        let taken = Set(s.chats.map(\.id))
        var id = 1_000_000 + Int.random(in: 0..<1_000_000_000)
        while taken.contains(id) { id = 1_000_000 + Int.random(in: 0..<1_000_000_000) }
        let members: [Int] = [s.me.id] + ids
        let g = ChatGroup(title: clean, memberIds: members, adminIds: [s.me.id], ownerId: s.me.id, hue: Double.random(in: 0..<1))
        let first = systemMessage(tf("Вы создали группу «%s»", clean))
        var chat = Chat(id: id, peer: g.asUser(id), messages: [first])
        chat.group = g
        state.chats.insert(chat, at: 0)
        var body: JSONObject = [:]
        body["id"] = id
        body["title"] = clean
        body["members"] = members
        net.queueCall("POST", "/v1/chats", body: body)
        refreshNetCounters()
        kickNet()
        return id
    }

    /// Служебная строка в переписке: «Вера теперь в группе», «Вы создали группу».
    private func systemMessage(_ text: String) -> Message {
        var m = Message(id: newId(), fromMe: false, text: text, time: Self.now())
        m.authorId = Self.systemAuthor
        m.delivered = true
        return m
    }

    private func updateChatGroup(_ chatId: Int, _ change: (ChatGroup) -> ChatGroup) {
        state.chats = state.chats.map { chat in
            guard chat.id == chatId, let g = chat.group else { return chat }
            var c = chat
            let next = change(g)
            c.group = next
            c.peer = next.asUser(c.id)
            return c
        }
    }

    private func chatGroup(_ chatId: Int) -> ChatGroup? {
        state.chats.first(where: { $0.id == chatId })?.group
    }

    private func isGroupAdmin(_ chatId: Int) -> Bool {
        chatGroup(chatId)?.isAdmin(state.me.id) ?? false
    }

    /// Переименовать группу — только админ.
    func renameGroup(_ chatId: Int, _ title: String) {
        let clean = String(title.trimmingCharacters(in: .whitespacesAndNewlines).prefix(ChatGroup.maxTitle))
        if clean.isEmpty || !isGroupAdmin(chatId) { return }
        updateChatGroup(chatId) { g in
            var x = g
            x.title = clean
            return x
        }
        append(chatId, systemMessage(tf("Новое название: «%s»", clean)))
        let body: JSONObject = ["title": clean]
        net.queueCall("PATCH", "/v1/chats/\(chatId)", body: body, coalesce: "title:\(chatId)")
        refreshNetCounters()
        kickNet()
    }

    /// Добавить друзей в группу — только админ, не больше 200 человек.
    func addGroupMembers(_ chatId: Int, _ userIds: [Int]) {
        guard isGroupAdmin(chatId), let g = chatGroup(chatId) else { return }
        let s = state
        var add: [Int] = []
        for uid in userIds where !g.memberIds.contains(uid) && s.following.contains(uid) && !s.blocked.contains(uid) && !add.contains(uid) {
            add.append(uid)
        }
        let room = max(0, ChatGroup.maxMembers - g.memberIds.count)
        add = Array(add.prefix(room))
        if add.isEmpty { return }
        updateChatGroup(chatId) { old in
            var x = old
            x.memberIds += add
            return x
        }
        for uid in add {
            if let p = Demo.person(uid) { append(chatId, systemMessage(tf("%s теперь в группе", p.firstName))) }
            net.queueCall("PUT", "/v1/chats/\(chatId)/members/\(uid)")
        }
        refreshNetCounters()
        kickNet()
    }

    /// Убрать участника — админ; создателя не убрать.
    func removeGroupMember(_ chatId: Int, _ userId: Int) {
        guard let g = chatGroup(chatId) else { return }
        if !isGroupAdmin(chatId) || userId == g.ownerId || !g.memberIds.contains(userId) { return }
        updateChatGroup(chatId) { old in
            var x = old
            x.memberIds.removeAll { $0 == userId }
            x.adminIds.remove(userId)
            return x
        }
        if let p = Demo.person(userId) { append(chatId, systemMessage(tf("%s больше не в группе", p.firstName))) }
        net.queueCall("DELETE", "/v1/chats/\(chatId)/members/\(userId)")
        refreshNetCounters()
        kickNet()
    }

    /// Сделать админом или снять — только создатель.
    func setGroupAdmin(_ chatId: Int, _ userId: Int, _ admin: Bool) {
        guard let g = chatGroup(chatId) else { return }
        if g.ownerId != state.me.id || userId == g.ownerId || !g.memberIds.contains(userId) { return }
        updateChatGroup(chatId) { old in
            var x = old
            if admin {
                x.adminIds.insert(userId)
            } else {
                x.adminIds.remove(userId)
            }
            return x
        }
        let method = admin ? "PUT" : "DELETE"
        net.queueCall(method, "/v1/chats/\(chatId)/admins/\(userId)")
        refreshNetCounters()
        kickNet()
    }

    /// Выйти из группы: переписка уходит из списка.
    func leaveGroup(_ chatId: Int) {
        guard chatGroup(chatId) != nil else { return }
        let meId = state.me.id
        state.chats.removeAll { $0.id == chatId }
        if state.openChatId == chatId { state.openChatId = nil }
        net.queueCall("DELETE", "/v1/chats/\(chatId)/members/\(meId)")
        refreshNetCounters()
        kickNet()
    }

    // MARK: - Живой канал

    /// Когда последний раз сказали «печатаю» в переписке (мс).
    @ObservationIgnored private var typingSentAt: [Int: Int64] = [:]

    /// Печатаю в переписке: собеседникам — «печатает…» (не чаще раза в 3 секунды).
    func typing(_ chatId: Int) {
        let now = nowMs()
        let last: Int64 = typingSentAt[chatId] ?? 0
        if now - last < 3_000 { return }
        typingSentAt[chatId] = now
        net.realtime.send(.typing(chatId: chatId, userId: state.me.id, on: true))
    }

    /// Открыл переписку — «прочитано» до последнего пришедшего.
    private func sendRead(_ chatId: Int) {
        guard let chat = state.chats.first(where: { $0.id == chatId }) else { return }
        // Запрос ещё не принят — человек не должен знать, что прочитали
        if chat.request { return }
        let times: [Int64] = chat.messages.filter { !$0.fromMe }.map(\.sentAt)
        let upTo: Int64 = times.max() ?? 0
        if upTo <= 0 { return }
        let meId = state.me.id
        if net.realtime.connected {
            net.realtime.send(.read(chatId: chatId, userId: meId, upTo: upTo))
        } else {
            let body: JSONObject = ["upTo": upTo]
            net.queueCall("POST", "/v1/chats/\(chatId)/read", body: body, coalesce: "read:\(chatId)")
            refreshNetCounters()
        }
    }

    /// Событие живого канала: сообщение, «печатает», «прочитано», состав группы.
    func onRealtime(_ ev: RtEvent) {
        switch ev {
        case .newMessage(let chatId, let message):
            receiveMessage(chatId, message)
        case .typing(let chatId, let userId, let on):
            if userId == state.me.id { return }
            updateChat(chatId) { c in
                if on {
                    _ = c.typingIds.insert(userId)
                } else {
                    _ = c.typingIds.remove(userId)
                }
            }
        case .read(let chatId, let userId, let upTo):
            markReadBy(chatId, userId, upTo)
        case .groupChanged(let chatId, let title, let memberIds, let adminIds):
            applyGroupChange(chatId, title, memberIds, adminIds)
        }
    }

    /// Пришло сообщение: в свою переписку, от нового человека — в переписки
    /// (друг) или в «Запросы» (не друг); «Никто не может писать» — не приходит.
    private func receiveMessage(_ evChatId: Int, _ json: JSONObject) {
        let s = state
        guard let m = ApiCodec(me: s.me).messageOf(json) else { return }
        let author = m.authorId
        if author == s.me.id || s.blocked.contains(author) { return }
        var incoming = Message(
            id: newId(), fromMe: false, text: m.text, time: m.time,
            attachments: m.attachments, replyTo: m.replyTo, forwardedFrom: m.forwardedFrom,
            poll: m.poll, place: m.place, product: m.product
        )
        incoming.story = m.story
        incoming.post = m.post
        incoming.call = m.call
        incoming.authorId = author
        incoming.delivered = true
        incoming.sentAt = m.sentAt
        let byId: Chat? = s.chats.first(where: { $0.id == evChatId })
        let byPeer: Chat? = s.chats.first(where: { !$0.isGroup && $0.peer.id == author })
        let known: Chat? = byId ?? byPeer
        let chatId: Int
        if let known {
            chatId = known.id
        } else {
            guard let person = Demo.person(author) else { return }
            let friend = s.following.contains(author)
            switch s.privacy.messages {
            case .nobody: return
            case .following: if !friend { return }
            case .everyone: break
            }
            var chat = Chat(id: evChatId, peer: person, messages: [])
            chat.request = !friend
            state.chats.insert(chat, at: 0)
            chatId = evChatId
        }
        append(chatId, incoming)
        // Пришло — значит, больше не печатает
        updateChat(chatId) { c in
            _ = c.typingIds.remove(author)
        }
        guard let chat = state.chats.first(where: { $0.id == chatId }) else { return }
        if state.openChatId == chatId {
            sendRead(chatId)
        } else if !chat.muted && !chat.request {
            let shown = chat.secret ? t("Новое сообщение") : incoming.text
            notify(Nudges.message(Demo.person(author) ?? chat.peer, shown, chatId: chatId))
        }
    }

    /// Собеседник (участник группы) прочитал всё до upTo — ✓✓ у своих сообщений.
    private func markReadBy(_ chatId: Int, _ userId: Int, _ upTo: Int64) {
        updateChat(chatId) { c in
            c.messages = c.messages.map { (m: Message) -> Message in
                guard m.fromMe, m.sentAt >= 1, m.sentAt <= upTo, !m.readBy.contains(userId) else { return m }
                var x = m
                x.readBy.append(userId)
                return x
            }
        }
    }

    private func applyGroupChange(_ chatId: Int, _ title: String, _ memberIds: [Int], _ adminIds: [Int]) {
        let meId = state.me.id
        if !memberIds.isEmpty && !memberIds.contains(meId) {
            // Нас убрали из группы
            state.chats.removeAll { $0.id == chatId && $0.isGroup }
            if state.openChatId == chatId { state.openChatId = nil }
            return
        }
        updateChatGroup(chatId) { g in
            var x = g
            if !title.isEmpty { x.title = title }
            if !memberIds.isEmpty { x.memberIds = memberIds }
            if !adminIds.isEmpty { x.adminIds = Set(adminIds) }
            return x
        }
    }

    /// Демо: выдать созревшие события живого канала (в приложении — раз в секунду).
    @discardableResult
    func tickRealtime() -> Int {
        guard let demo = net.realtime as? DemoRealtime else { return 0 }
        return demo.tick()
    }

    /// Подключить живой канал и рассказать демо-серверу, кто в каких переписках.
    private func connectRealtime() {
        let n = net
        if let d = n.demo {
            let meId = state.me.id
            for c in state.chats {
                let ids: [Int] = c.group?.memberIds ?? [meId, c.peer.id]
                d.registerChat(c.id, ids)
            }
        }
        // Демо выдаёт события в главном потоке (tick); настоящий канал — со своего
        let direct = n.realtime is DemoRealtime
        n.realtime.connect { [weak self] ev in
            if direct {
                self?.onRealtime(ev)
            } else {
                DispatchQueue.main.async { self?.onRealtime(ev) }
            }
        }
    }

    // MARK: - Запросы на переписку

    /// Принять: переписка переезжает к остальным, человек видит «прочитано».
    func acceptRequest(_ chatId: Int) {
        updateChat(chatId) { $0.request = false }
        net.queueCall("POST", "/v1/chats/\(chatId)/accept")
        sendRead(chatId)
        refreshNetCounters()
        kickNet()
    }

    /// Удалить запрос: человек не узнает.
    func deleteRequest(_ chatId: Int) {
        state.chats.removeAll { $0.id == chatId && $0.request }
        if state.openChatId == chatId { state.openChatId = nil }
    }

    /// Удалить запрос и заблокировать написавшего.
    func blockRequest(_ chatId: Int) {
        guard let chat = state.chats.first(where: { $0.id == chatId }) else { return }
        deleteRequest(chatId)
        block(chat.peer.id)
    }

    // MARK: - Модерация

    @ObservationIgnored private let commentLimiter = RateLimiter(maxEvents: 10, windowMs: 60_000)
    @ObservationIgnored private let messageLimiter = RateLimiter(maxEvents: 30, windowMs: 60_000)
    @ObservationIgnored private let postLimiter = RateLimiter(maxEvents: 10, windowMs: 10 * 60_000)

    /// Скрытое слово: комментарии и сообщения с ним сворачиваются (на всех своих телефонах).
    func addHiddenWord(_ word: String) {
        state.privacy.hiddenWords = Moderation.addWord(state.privacy.hiddenWords, word)
        pushProfile()
    }

    func removeHiddenWord(_ word: String) {
        state.privacy.hiddenWords.removeAll { $0 == word }
        pushProfile()
    }

    /// Выключить или включить комментарии к своей записи.
    func toggleComments(_ postId: Int64) {
        let meId = state.me.id
        var off = false
        var found = false
        state.posts = state.posts.map { (p: Post) -> Post in
            guard p.id == postId && p.author.id == meId else { return p }
            var x = p
            x.commentsOff.toggle()
            off = x.commentsOff
            found = true
            return x
        }
        guard found, let post = state.posts.first(where: { $0.id == postId }) else { return }
        // Ещё не ушедшая запись уйдёт уже с нужным значением
        if post.outboxKey.isEmpty {
            let body: JSONObject = ["commentsOff": off]
            net.queueCall("PATCH", "/v1/posts/\(postId)", body: body, coalesce: "comments:\(postId)")
        }
        refreshNetCounters()
        kickNet()
    }

    func dismissNotice() {
        state.notice = nil
    }

    private func tooOften() {
        state.notice = t("Слишком часто — подождите минуту")
    }

    // MARK: - Вход на сервер и устройства

    /// Имя этого телефона для списка устройств («iPhone») — ставит MemoryApp.
    @ObservationIgnored var deviceName: String = "iPhone"

    /// Войти на сервер (если ещё не вошли) и сверить профиль. fresh — на
    /// этом телефоне аккаунт новый: берём профиль с сервера, иначе — шлём свой.
    func startSession(login: String, password: String?, fresh: Bool) {
        if !demoReplies { return }
        Task { @MainActor [weak self] in
            await self?.startSessionNow(login: login, password: password, fresh: fresh)
        }
    }

    @MainActor func startSessionNow(login: String, password: String?, fresh: Bool) async {
        let n = net
        // Вторая попытка — если сохранённые токены сервер уже не знает
        // (демо-сервер новый при каждом запуске, вышли на другом устройстве)
        for attempt in 0..<2 {
            if !n.session.signedIn {
                do {
                    try await n.signIn(login: login, password: password ?? "", device: deviceName, platform: "ios")
                } catch {
                    let e = NetError.of(error)
                    if e.kind == .auth || e.kind == .client { state.signedOut = true }
                    return
                }
                // Что ждало входа — отправить сразу; живой канал — уже с токеном
                n.retryAuth()
                kickNet()
                if !(n.realtime is DemoRealtime) {
                    n.realtime.close()
                    connectRealtime()
                }
            }
            state.signedOut = false
            let remote: ProfileSync?
            do {
                remote = try await n.api.profile()
            } catch {
                let e = NetError.of(error)
                // Сохранённые токены сервер не знает (демо-сервер новый при каждом
                // запуске, настоящий — вышли с этого телефона на другом): войти ещё раз
                if e.kind != .auth || attempt > 0 { return }
                n.session.clear()
                continue
            }
            if let remote, fresh {
                applyProfile(remote.json)
            } else {
                pushProfile()
            }
            return
        }
    }

    /// Свой профиль и приватность — на сервер (для других своих телефонов).
    private func pushProfile() {
        let me = state.me
        var o: JSONObject = [:]
        o["name"] = me.name
        o["bio"] = me.bio
        o["city"] = me.city
        o["birthday"] = me.birthday
        o["website"] = me.website
        o["interests"] = me.interests
        o["privacy"] = ApiCodec(me: me).privacyJson(state.privacy)
        net.queueCall("PUT", "/v1/me", body: o, coalesce: "me")
        refreshNetCounters()
        kickNet()
    }

    /// Профиль с сервера — на новый телефон.
    private func applyProfile(_ o: JSONObject) {
        var me = state.me
        func str(_ key: String, _ fallback: String) -> String {
            let v = Json.string(o, key)
            return v.isEmpty ? fallback : v
        }
        me.name = str("name", me.name)
        me.bio = str("bio", me.bio)
        me.city = str("city", me.city)
        me.birthday = str("birthday", me.birthday)
        me.website = str("website", me.website)
        if o["interests"] is [Any] { me.interests = Json.strings(o, "interests") }
        let codec = ApiCodec(me: me)
        let privacy: Privacy = Json.object(o, "privacy").map { codec.privacyOf($0) } ?? state.privacy
        applyMe(me)
        state.privacy = privacy
    }

    func loadDevices() {
        if !demoReplies { return }
        Task { @MainActor [weak self] in
            await self?.loadDevicesNow()
        }
    }

    /// Устройства: это — первым, остальные — кто был в сети недавно.
    @MainActor func loadDevicesNow() async {
        do {
            let list = try await net.api.devices()
            state.devices = list.sorted { a, b in
                if a.current != b.current { return a.current }
                return a.lastSeen > b.lastSeen
            }
        } catch {
            let e = NetError.of(error)
            if e.kind == .offline {
                state.notice = t("Нет сети — список устройств загрузится позже")
            } else {
                state.notice = t("Не удалось загрузить устройства")
            }
            if e.kind == .auth { state.signedOut = true }
        }
    }

    func revokeDevice(_ id: String) {
        if !demoReplies { return }
        Task { @MainActor [weak self] in
            await self?.revokeDeviceNow(id)
        }
    }

    @MainActor func revokeDeviceNow(_ id: String) async {
        do {
            try await net.api.revokeDevice(id)
        } catch {
            state.notice = t("Не получилось — проверьте сеть")
        }
        await loadDevicesNow()
    }

    func revokeOtherDevices() {
        if !demoReplies { return }
        Task { @MainActor [weak self] in
            await self?.revokeOtherDevicesNow()
        }
    }

    @MainActor func revokeOtherDevicesNow() async {
        do {
            try await net.api.revokeOtherDevices()
        } catch {
            state.notice = t("Не получилось — проверьте сеть")
        }
        await loadDevicesNow()
    }

    // MARK: - Фоновая отправка

    /// Пока приложение было закрыто, очередь могла уйти в фоне (BackgroundNet).
    /// Сверить: записи, которых в очереди уже нет, — отправлены; сообщения
    /// переписок без очереди — тоже.
    private func reconcileOutbox() {
        let ops = net.outbox.all()
        let keys = Set(ops.map(\.key))
        var chatsWithOps = Set<Int>()
        for op in ops where op.kind == .sendMessage { chatsWithOps.insert(Int(op.target)) }
        let stalePosts = state.posts.contains { !$0.outboxKey.isEmpty && !keys.contains($0.outboxKey) }
        if stalePosts {
            state.posts = state.posts.map { (p: Post) -> Post in
                guard !p.outboxKey.isEmpty, !keys.contains(p.outboxKey) else { return p }
                var x = p
                x.outboxKey = ""
                return x
            }
        }
        let staleChats = state.chats.contains { c in !chatsWithOps.contains(c.id) && c.messages.contains(where: \.pending) }
        if staleChats {
            state.chats = state.chats.map { (c: Chat) -> Chat in
                if chatsWithOps.contains(c.id) || !c.messages.contains(where: \.pending) { return c }
                var x = c
                x.messages = c.messages.map { (m: Message) -> Message in
                    guard m.pending else { return m }
                    var y = m
                    y.pending = false
                    y.delivered = true
                    return y
                }
                return x
            }
        }
    }

    /// Свернули приложение — дослать, что осталось (beginBackgroundTask):
    /// проходы отправки, пока очередь не опустеет, нет сети или не кончилось
    /// время. Ответ — всё ли ушло.
    @MainActor func drainNet(maxRounds: Int = 40) async -> Bool {
        for _ in 0..<maxRounds {
            if Task.isCancelled { return false }
            if !net.busy { return true }
            if state.netStatus == .offline || state.signedOut { return false }
            // Всё на паузе после ошибок — подождать ближайшего повтора, но недолго
            let wait: Int64 = net.nextAttemptAt() - nowMs()
            if wait > 20_000 { return false }
            if wait > 0 { try? await Task.sleep(nanoseconds: UInt64(wait) * 1_000_000) }
            await pumpNet()
            if state.netStatus == .serverDown { return false }
        }
        return !net.busy
    }

    // MARK: - Сеть

    /// Всё сетевое: API, очередь отправки, загрузки, кэш. Пока аккаунт не
    /// открыт (и в тестах) — встроенный демо-сервер и кэш в памяти;
    /// MemoryApp подставляет сеть аккаунта (NetSetup.forAccount).
    @ObservationIgnored private(set) var net: Net = Net.demo(clock: { 0 }, me: { Demo.me })
    @ObservationIgnored private var feedPager: Pager<Post>?
    @ObservationIgnored private var netTask: Task<Void, Never>?
    /// Демо: раз в секунду выдаёт события живого канала.
    @ObservationIgnored private var tickTask: Task<Void, Never>?
    @ObservationIgnored private let netKick = NetKicker()
    /// Есть ли сеть у телефона (NetMonitor).
    @ObservationIgnored private var deviceOnline = true
    /// Сжатая копия файла больше не нужна (в приложении — MediaCompressor.cleanup).
    @ObservationIgnored var uploadCleanup: (UploadJob) -> Void = { _ in }

    /// Часы модели в мс — для сети.
    private func nowMs() -> Int64 { Json.ms(clock()) }

    private func makeDemoNet() -> Net {
        let clock = self.clock
        let ms: () -> Int64 = { Json.ms(clock()) }
        return Net.demo(clock: ms, me: { [weak self] in self?.state.me ?? Demo.me })
    }

    /// Подключить сеть аккаунта: сохранённая лента — сразу, без сети.
    func attachNet(_ n: Net) {
        // Прежний живой канал больше не нужен
        if net !== n { net.realtime.close() }
        net = n
        let pager = n.feedPager()
        pager.restore()
        feedPager = pager
        publishFeed()
        // Пока приложение было закрыто, очередь могла уйти в фоне
        reconcileOutbox()
        connectRealtime()
        refreshNetCounters()
        applyOnline()
    }

    /// Отключить (выход из аккаунта): назад к демо в памяти.
    private func detachNet() {
        netTask?.cancel()
        netTask = nil
        tickTask?.cancel()
        tickTask = nil
        netKick.kick()
        net.realtime.close()
        net = makeDemoNet()
        feedPager = net.feedPager()
        connectRealtime()
    }

    private func publishFeed() {
        guard let p = feedPager?.state else { return }
        let paging = FeedPaging(loading: p.loading, end: p.end, fromCache: p.fromCache, cachedAt: p.cachedAt, error: p.error?.kind)
        state.olderFeed = p.items
        state.feedPaging = paging
    }

    private func refreshNetCounters() {
        let jobs = net.uploads.all()
        let count = net.outbox.count
        let bytes = net.cache.sizeBytes()
        if state.uploads != jobs { state.uploads = jobs }
        if state.outboxCount != count { state.outboxCount = count }
        if state.cacheBytes != bytes { state.cacheBytes = bytes }
    }

    /// Есть ли сеть — от NetMonitor. Появилась — сразу отправить накопленное.
    func setDeviceOnline(_ online: Bool) {
        deviceOnline = online
        applyOnline()
    }

    func setDemoOffline(_ on: Bool) {
        state.demoOffline = on
        applyOnline()
    }

    func setMediaQuality(_ q: MediaQuality) {
        state.mediaQuality = q
    }

    private func applyOnline() {
        let online = deviceOnline && !state.demoOffline
        net.demo?.online = online
        let status: NetStatus
        if !online {
            status = .offline
        } else if state.netStatus == .serverDown {
            status = .serverDown
        } else {
            status = .online
        }
        if state.netStatus != status { state.netStatus = status }
        if online {
            net.wake()
            kickNet()
        }
    }

    /// Разбудить отправку: что-то добавили в очередь или появилась сеть.
    func kickNet() {
        netKick.kick()
    }

    /// Фоновая отправка: пока приложение открыто, догружает и отправляет.
    /// Ждёт пинка (kickNet) или ближайшей повторной попытки.
    func startNetLoop() {
        guard netTask == nil, demoReplies else { return }
        // Демо-сервер «присылает» события живого канала — выдаём их раз в секунду
        tickTask = Task { @MainActor [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                guard let self else { return }
                self.tickRealtime()
            }
        }
        let kick = netKick
        netTask = Task { @MainActor [weak self] in
            while !Task.isCancelled {
                guard let wait = await self?.netStep() else { return }
                if Task.isCancelled { return }
                await kick.wait(timeoutMs: wait)
            }
        }
    }

    /// Проход отправки, если есть что слать; ответ — сколько ждать до следующего (мс).
    @MainActor private func netStep() async -> Int64 {
        if state.netStatus != .offline && net.busy {
            await pumpNet()
        }
        if !net.busy { return 60_000 }
        let delta: Int64 = net.nextAttemptAt() - nowMs()
        return min(max(delta, 1_000), 30_000)
    }

    /// Один проход отправки: загрузки, потом очередь. Результат — на экраны.
    @MainActor func pumpNet() async {
        let n = net
        let r = await n.pump(onProgress: { [weak self] _ in
            let jobs = n.uploads.all()
            // В @Sendable-замыкание — не слабую переменную self, а постоянную ссылку
            guard let model = self else { return }
            DispatchQueue.main.async { model.state.uploads = jobs }
        })
        for item in r.sent {
            let op = item.op
            switch op.kind {
            case .createPost: clearOutboxKey(op.key)
            case .sendMessage: markSent(op, answer: item.answer)
            case .like, .unlike, .call: break
            }
        }
        for job in r.uploads where job.state == .done {
            uploadCleanup(job)
        }
        for op in r.failed {
            switch op.kind {
            case .createPost:
                clearOutboxKey(op.key)
                state.netNotice = t("Запись не отправлена: файл не нашёлся. Она осталась только на этом телефоне.")
            case .sendMessage:
                markDelivered(op)
                state.netNotice = t("Сообщение не отправлено: вложение не нашлось.")
            case .like, .unlike, .call: break
            }
        }
        let status: NetStatus
        if !deviceOnline || state.demoOffline {
            status = .offline
        } else if r.offline {
            status = .serverDown
        } else {
            status = .online
        }
        if state.netStatus != status { state.netStatus = status }
        if r.authLost && !state.signedOut { state.signedOut = true }
        refreshNetCounters()
    }

    /// Сообщение дошло до сервера: ✓ и время сервера (по нему — «прочитано до»).
    private func markSent(_ op: OutboxOp, answer: JSONObject?) {
        guard let o = Json.parse(op.payload), o["id"] != nil else { return }
        let localId = Json.long(o, "id")
        let sentAt: Int64 = answer.map { Json.long($0, "sentAt") } ?? 0
        updateMessage(Int(op.target), localId) { m in
            m.pending = false
            m.delivered = true
            m.sentAt = sentAt
        }
    }

    /// Запись ушла (или не уйдёт никогда) — больше не «ждёт сети».
    private func clearOutboxKey(_ key: String) {
        guard state.posts.contains(where: { $0.outboxKey == key }) else { return }
        state.posts = state.posts.map { p -> Post in
            guard p.outboxKey == key else { return p }
            var x = p
            x.outboxKey = ""
            return x
        }
    }

    /// Сообщение больше не ждёт очереди: ушло или не уйдёт никогда.
    private func markDelivered(_ op: OutboxOp) {
        guard let o = Json.parse(op.payload), o["id"] != nil else { return }
        let localId = Json.long(o, "id")
        updateMessage(Int(op.target), localId) { $0.pending = false }
    }

    private func updateMessage(_ chatId: Int, _ messageId: Int64, _ change: (inout Message) -> Void) {
        updateChat(chatId) { c in
            c.messages = c.messages.map { m -> Message in
                guard m.id == messageId else { return m }
                var x = m
                change(&x)
                return x
            }
        }
    }

    /// Долистали ленту до конца — подгрузить старое (в тестах — loadMoreFeedNow).
    func loadMoreFeed() {
        if !demoReplies || state.feedPaging.loading || state.feedPaging.end { return }
        Task { @MainActor [weak self] in
            await self?.loadMoreFeedNow()
        }
    }

    @MainActor func loadMoreFeedNow() async {
        guard let pager = feedPager else { return }
        state.feedPaging.loading = true
        state.feedPaging.error = nil
        let err = await pager.loadMore()
        publishFeed()
        if err?.kind == .offline && deviceOnline && !state.demoOffline { state.netStatus = .serverDown }
        if err == nil && state.netStatus == .serverDown { state.netStatus = .online }
        refreshNetCounters()
    }

    /// Потянули ленту вниз: заново с сервера.
    func refreshFeed() {
        if !demoReplies { return }
        Task { @MainActor [weak self] in
            await self?.refreshFeedNow()
        }
    }

    @MainActor func refreshFeedNow() async {
        guard let pager = feedPager else { return }
        await pager.refresh()
        publishFeed()
        refreshNetCounters()
    }

    func pauseUpload(_ id: String) {
        net.uploads.pause(id)
        refreshNetCounters()
    }

    func resumeUpload(_ id: String) {
        net.uploads.resume(id)
        refreshNetCounters()
        kickNet()
    }

    /// Отменить загрузку: её запись или сообщение не уйдёт (останется
    /// только на этом телефоне), остальные загрузки того же — тоже снимаются.
    func cancelUpload(_ id: String) {
        guard let job = net.uploads.get(id) else { return }
        for j in net.uploads.all() where j.owner == job.owner {
            uploadCleanup(j)
            net.uploads.remove(j.id)
        }
        if let op = net.outbox.get(job.owner) {
            net.outbox.remove(op.key)
            if op.kind == .createPost { clearOutboxKey(op.key) }
            if op.kind == .sendMessage { markDelivered(op) }
        }
        refreshNetCounters()
    }

    /// Стереть сохранённые страницы (очередь и загрузки — не трогаем).
    func clearNetCache() {
        net.cache.clearPages()
        feedPager = net.feedPager()
        publishFeed()
        refreshNetCounters()
    }

    func dismissNetNotice() {
        state.netNotice = nil
    }

    // MARK: - PRISM

    func togglePrismCommentLike(_ prismId: Int64, _ commentId: Int64) {
        updatePrism(prismId) { p in
            p.commentList = p.commentList.map { Self.flipLike($0, commentId) }
        }
    }

    func togglePrismLike(_ prismId: Int64) {
        updatePrism(prismId) { p in
            p.likes += p.liked ? -1 : 1
            p.liked.toggle()
        }
    }

    /// «Сохранить»: закладка, ролик появляется в «Сохранённом» в профиле.
    func togglePrismSave(_ prismId: Int64) {
        updatePrism(prismId) { $0.saved.toggle() }
    }

    func addPrismComment(_ prismId: Int64, _ text: String, replyTo: String? = nil, sticker: Attachment? = nil) {
        let trimmed = String(text.trimmingCharacters(in: .whitespacesAndNewlines).prefix(1000))
        if trimmed.isEmpty && sticker == nil { return }
        let comment = Comment(id: newId(), author: state.me, text: trimmed, createdAt: clock(), replyTo: replyTo, sticker: sticker)
        updatePrism(prismId) { p in
            p.comments += 1
            p.commentList.append(comment)
        }
    }

    func deletePrismComment(_ prismId: Int64, _ commentId: Int64) {
        let meId = state.me.id
        updatePrism(prismId) { p in
            guard p.commentList.contains(where: { $0.id == commentId && $0.author.id == meId }) else { return }
            p.commentList.removeAll { $0.id == commentId }
            p.comments = max(0, p.comments - 1)
        }
    }

    /// Открыть вкладку PRISM на нужном ролике — из «Сохранённого».
    func openPrism(_ prismId: Int64) {
        selectTab(.prism)
        state.prismStartId = prismId
    }

    /// Свой PRISM: камера → редактор → публикация.
    func openPrismCreator() { state.creatingPrism = true }

    func closePrismCreator() { state.creatingPrism = false }

    /// Опубликовать свой ролик: готовое видео из редактора, обложка,
    /// подпись и что звучит. Ролик встаёт первым во вкладке PRISM.
    func publishPrism(video: Attachment, cover: Attachment?, caption: String, music: String, durationMs: Double) {
        Telemetry.track(.prismPublished)
        let id = newId()
        let text = String(caption.trimmingCharacters(in: .whitespacesAndNewlines).prefix(300))
        let sound = music.trimmingCharacters(in: .whitespaces)
        let prism = Prism(
            id: id, author: state.me, caption: text,
            music: sound.isEmpty ? tf("Оригинальный звук · %s", state.me.name) : sound,
            seed: Int(id % 97), likes: 0, comments: 0,
            durationMs: max(1000, durationMs), video: video, cover: cover, createdAt: clock()
        )
        state.prisms.insert(prism, at: 0)
        state.creatingPrism = false
        selectTab(.prism)
        state.prismStartId = id
    }

    /// Удалить свой ролик. Файлы удаляет экран — модель о файлах не знает.
    func deletePrism(_ prismId: Int64) {
        let meId = state.me.id
        state.prisms.removeAll { $0.id == prismId && $0.author.id == meId }
    }

    func consumePrismStart() {
        state.prismStartId = nil
    }

    private func updatePrism(_ prismId: Int64, _ change: (inout Prism) -> Void) {
        state.prisms = state.prisms.map { prism in
            var p = prism
            if p.id == prismId { change(&p) }
            return p
        }
    }

    // MARK: - Студия и витрина

    /// Новый стикер — первым в «Студии».
    func addSticker(_ sticker: Sticker) {
        state.stickers = [sticker] + state.stickers.filter { $0.id != sticker.id }
    }

    /// Убрать стикер из «Студии». Файл удаляет экран.
    func deleteSticker(_ stickerId: String) {
        state.stickers.removeAll { $0.id == stickerId }
    }

    /// Закрепить свою запись в витрине профиля или открепить. Бесплатно —
    /// три записи (четвёртая открывает шторку Memory+), в Memory+ — шесть
    /// (седьмая вытесняет самую давнюю).
    func toggleProfilePin(_ postId: Int64) {
        let meId = state.me.id
        let adding = !state.profilePins.contains(postId) && state.posts.contains(where: { $0.id == postId && $0.author.id == meId })
        if adding && state.profilePins.count >= Premium.freePins && !require(.pins) { return }
        let limit = has(.pins) ? Premium.plusPins : Self.maxPins
        if state.profilePins.contains(postId) {
            state.profilePins.removeAll { $0 == postId }
        } else if state.posts.contains(where: { $0.id == postId && $0.author.id == meId }) {
            state.profilePins = Array(([postId] + state.profilePins).prefix(limit))
        }
    }

    /// Удалить свою запись из ленты. Файлы удаляет экран.
    func deletePost(_ postId: Int64) {
        let meId = state.me.id
        state.posts.removeAll { $0.id == postId && $0.author.id == meId }
        state.profilePins.removeAll { $0 == postId }
    }

    // MARK: - Моя музыка

    /// Новые треки — в начало списка; уже загруженные (тот же файл) не повторяются.
    func addTracks(_ tracks: [MusicTrack]) {
        let known = Set(state.tracks.map(\.fileName))
        state.tracks = tracks.filter { !known.contains($0.fileName) } + state.tracks
    }

    /// Удалить трек: он уходит и из всех плейлистов.
    func deleteTrack(_ trackId: String) {
        state.tracks.removeAll { $0.id == trackId }
        state.playlists = state.playlists.map { p in
            var p = p
            p.trackIds.removeAll { $0 == trackId }
            return p
        }
    }

    /// Новый плейлист; ответ — его номер, nil — пустое название.
    @discardableResult
    func createPlaylist(_ name: String) -> Int64? {
        let title = String(name.trimmingCharacters(in: .whitespacesAndNewlines).prefix(60))
        if title.isEmpty { return nil }
        // Бесплатно — три плейлиста
        if state.playlists.count >= Premium.freePlaylists && !require(.playlists) { return nil }
        let id = newId()
        state.playlists.append(Playlist(id: id, name: title))
        return id
    }

    func renamePlaylist(_ playlistId: Int64, _ name: String) {
        let title = String(name.trimmingCharacters(in: .whitespacesAndNewlines).prefix(60))
        if title.isEmpty { return }
        updatePlaylist(playlistId) { $0.name = title }
    }

    func deletePlaylist(_ playlistId: Int64) {
        state.playlists.removeAll { $0.id == playlistId }
    }

    /// Добавить трек в плейлист; уже добавленный не повторяется.
    func addToPlaylist(_ playlistId: Int64, _ trackId: String) {
        guard state.track(trackId) != nil else { return }
        updatePlaylist(playlistId) { p in
            if !p.trackIds.contains(trackId) { p.trackIds.append(trackId) }
        }
    }

    func removeFromPlaylist(_ playlistId: Int64, _ trackId: String) {
        updatePlaylist(playlistId) { $0.trackIds.removeAll { $0 == trackId } }
    }

    /// Сдвинуть трек в плейлисте на шаг вверх (-1) или вниз (+1).
    func moveInPlaylist(_ playlistId: Int64, _ trackId: String, step: Int) {
        updatePlaylist(playlistId) { p in
            guard let i = p.trackIds.firstIndex(of: trackId) else { return }
            let j = i + step
            guard p.trackIds.indices.contains(j) else { return }
            p.trackIds.swapAt(i, j)
        }
    }

    private func updatePlaylist(_ playlistId: Int64, _ change: (inout Playlist) -> Void) {
        state.playlists = state.playlists.map { p in
            var p = p
            if p.id == playlistId { change(&p) }
            return p
        }
    }

    // MARK: - Группы

    /// Группа поверх вкладки; из раздела профиля «назад» вернёт в него.
    func openGroup(_ groupId: Int) {
        if !(state.tab == .profile && state.profileSection != nil) {
            state.tab = .groups
        }
        state.openGroupId = groupId
        state.groupSettingsOpen = false
        state.openPersonId = nil
    }

    func closeGroup() {
        state.openGroupId = nil
        state.groupSettingsOpen = false
    }

    func openGroupSettings() {
        state.groupSettingsOpen = state.openGroupId != nil
    }

    func closeGroupSettings() {
        state.groupSettingsOpen = false
    }

    /// Название, описание и тема своей группы. Ошибка — текст для экрана, nil — сохранено.
    @discardableResult
    func updateGroup(_ groupId: Int, name: String, description: String, topic: String) -> String? {
        let title = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if title.count < 3 { return t("Название — от 3 символов") }
        if title.count > 40 { return t("Название — не длиннее 40 символов") }
        if state.groups.contains(where: { $0.id != groupId && $0.name.lowercased() == title.lowercased() }) {
            return tf("Группа «%s» уже есть", title)
        }
        updateOwnGroup(groupId) { g in
            g.name = title
            g.description = String(description.trimmingCharacters(in: .whitespacesAndNewlines).prefix(500))
            if !topic.isEmpty { g.topic = topic }
        }
        return nil
    }

    /// Новая заставка (фото) или nil — вернуть туманность. Старый файл удаляет экран.
    func setGroupCover(_ groupId: Int, _ cover: Attachment?) {
        updateOwnGroup(groupId) { $0.cover = cover }
    }

    func setGroupOnlyOwnerPosts(_ groupId: Int, _ only: Bool) {
        updateOwnGroup(groupId) { $0.onlyOwnerPosts = only }
    }

    /// Закрепить запись в своей группе; та же запись ещё раз — открепить.
    func togglePinGroupPost(_ groupId: Int, _ postId: Int64) {
        updateOwnGroup(groupId) { g in
            guard g.posts.contains(where: { $0.id == postId }) else { return }
            g.pinnedPostId = g.pinnedPostId == postId ? nil : postId
        }
    }

    /// Удалить запись в группе: свою — всегда, любую — в своей группе. Файлы удаляет экран.
    func deleteGroupPost(_ groupId: Int, _ postId: Int64) {
        let meId = state.me.id
        state.groups = state.groups.map { group in
            var g = group
            if g.id == groupId, let post = g.posts.first(where: { $0.id == postId }), post.author.id == meId || g.mine {
                g.posts.removeAll { $0.id == postId }
                if g.pinnedPostId == postId { g.pinnedPostId = nil }
            }
            return g
        }
    }

    private func updateOwnGroup(_ groupId: Int, _ change: (inout MemoryGroup) -> Void) {
        state.groups = state.groups.map { group in
            var g = group
            if g.id == groupId && g.mine { change(&g) }
            return g
        }
    }

    // MARK: Витрина, сведения и оформление группы

    /// Подробное описание, сайт и контакты своей группы. Ссылка без схемы получает https://.
    func updateGroupInfo(_ groupId: Int, _ info: GroupInfo) {
        func clip(_ s: String, _ n: Int) -> String { String(s.trimmingCharacters(in: .whitespacesAndNewlines).prefix(n)) }
        var clean = GroupInfo(
            about: clip(info.about, 2000),
            website: String(normalizeUrl(info.website).prefix(200)),
            phone: clip(info.phone, 40),
            email: clip(info.email, 80),
            address: clip(info.address, 160),
            hours: clip(info.hours, 80)
        )
        // Телефон, почта, адрес и часы — в Бизнесе. Без него прежние контакты остаются как были
        if !has(.groupContacts) {
            let old = state.groups.first { $0.id == groupId }?.info ?? GroupInfo()
            if clean.phone != old.phone || clean.email != old.email || clean.address != old.address || clean.hours != old.hours {
                require(.groupContacts)
            }
            clean.phone = old.phone
            clean.email = old.email
            clean.address = old.address
            clean.hours = old.hours
        }
        let saved = clean
        updateOwnGroup(groupId) { $0.info = saved }
    }

    /// Фон страницы своей группы; nil — как везде.
    func setGroupBackdrop(_ groupId: Int, _ backdrop: Backdrop?) {
        // Фон страницы группы — Бизнес; убрать фон можно всегда
        // Тот же фон, что уже стоит (меняют только затемнение), — можно и без подписки
        let current = state.groups.first { $0.id == groupId }?.backdrop
        if let b = backdrop, !(b.style == current?.style && b.photo == current?.photo), !require(.groupBackdrop) { return }
        let before = backdropPhotos()
        updateOwnGroup(groupId) { $0.backdrop = backdrop }
        // Своё фото фона, которое больше нигде не стоит, — удалить копию
        dropBackdropPhotos(before)
    }

    func setGroupAccent(_ groupId: Int, _ accent: GroupAccent) {
        updateOwnGroup(groupId) { $0.accent = accent.id }
    }

    /// Добавить или изменить товар своей группы. Ответ — текст ошибки или nil.
    /// Новый товар — первым в витрине.
    @discardableResult
    func saveProduct(_ groupId: Int, _ product: Product) -> String? {
        let title = product.title.trimmingCharacters(in: .whitespacesAndNewlines)
        if title.isEmpty { return t("Добавьте название товара") }
        if product.price < 0 { return t("Цена не может быть отрицательной") }
        // Бесплатно — до 10 товаров; изменить уже добавленные можно всегда
        let shop = state.groups.first { $0.id == groupId }?.shop ?? []
        if product.id == 0 && shop.count >= Premium.freeProducts && !require(.shopUnlimited) {
            return tf("Бесплатно — до %s товаров. Больше — в Memory+ Бизнес", Premium.freeProducts)
        }
        var clean = product
        if clean.id == 0 { clean.id = newId() }
        clean.title = String(title.prefix(80))
        clean.description = String(product.description.trimmingCharacters(in: .whitespacesAndNewlines).prefix(2000))
        clean.category = String(product.category.trimmingCharacters(in: .whitespacesAndNewlines).prefix(40))
        clean.url = String(normalizeUrl(product.url).prefix(300))
        clean.photos = Array(product.photos.prefix(Self.maxProductPhotos))
        if clean.seed == 0 { clean.seed = Int(nextId % 97) + 1 }
        let saved = clean
        updateOwnGroup(groupId) { g in
            if let i = g.shop.firstIndex(where: { $0.id == saved.id }) {
                g.shop[i] = saved
            } else {
                g.shop.insert(saved, at: 0)
            }
        }
        return nil
    }

    func deleteProduct(_ groupId: Int, _ productId: Int64) {
        updateOwnGroup(groupId) { $0.shop.removeAll { $0.id == productId } }
    }

    /// Переставить товар на одно место выше (-1) или ниже (+1).
    func moveProduct(_ groupId: Int, _ productId: Int64, _ delta: Int) {
        updateOwnGroup(groupId) { g in
            guard let i = g.shop.firstIndex(where: { $0.id == productId }) else { return }
            let j = i + delta
            guard g.shop.indices.contains(j) else { return }
            let item = g.shop.remove(at: i)
            g.shop.insert(item, at: j)
        }
    }

    func findProduct(_ groupId: Int, _ productId: Int64) -> Product? {
        state.findProduct(groupId, productId)
    }

    /// «Написать продавцу»: переписка с создателем группы и сообщение с
    /// товаром. Своя группа — некому писать (nil). Ответ — номер переписки.
    @discardableResult
    func askSeller(_ groupId: Int, _ productId: Int64, text: String = "") -> Int? {
        guard let group = state.groups.first(where: { $0.id == groupId }), !group.mine,
              let product = group.shop.first(where: { $0.id == productId }),
              let seller = Demo.person(group.ownerId), !state.blocked.contains(seller.id) else { return nil }
        let chatId: Int
        if let existing = state.chats.first(where: { $0.peer.id == seller.id }) {
            chatId = existing.id
        } else {
            chatId = (state.chats.map(\.id).max() ?? 0) + 1
            state.chats.insert(Chat(id: chatId, peer: seller, messages: []), at: 0)
        }
        let ref = ProductRef(groupId: groupId, productId: product.id, title: product.title, price: product.price,
                             photo: product.photos.first?.fileName ?? "", seed: product.seed)
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let question = trimmed.isEmpty ? tf("Здравствуйте! Интересует «%s». Он в наличии?", product.title) : trimmed
        append(chatId, Message(id: newId(), fromMe: true, text: question, time: Self.now(), product: ref))
        openChat(chatId)
        if demoReplies {
            let inStock = product.inStock
            later(2.5, 6) { model in
                let reply = inStock
                    ? t("Здравствуйте! Да, в наличии. Могу отправить сегодня — напишите город и как удобнее получить.")
                    : t("Здравствуйте! Сейчас нет в наличии, сделаю под заказ за неделю. Подходит?")
                model.append(chatId, Message(id: model.newId(), fromMe: false, text: reply, time: Self.now()))
                if let chat = model.state.chats.first(where: { $0.id == chatId }), !chat.muted, model.state.openChatId != chatId {
                    model.notify(Nudges.message(chat.peer, reply, chatId: chatId))
                }
            }
        }
        return chatId
    }

    /// Открыть товар из сообщения: группа с витриной и сам товар.
    /// Товар убрали — открывать нечего (false; иначе «назад» съест нажатие впустую).
    @discardableResult
    func openProduct(_ groupId: Int, _ productId: Int64) -> Bool {
        guard findProduct(groupId, productId) != nil else { return false }
        state.openChatId = nil
        state.openPersonId = nil
        state.personSection = nil
        state.returnToChatId = nil
        state.tab = .groups
        state.openGroupId = groupId
        state.groupSettingsOpen = false
        state.openProduct = ProductKey(groupId: groupId, productId: productId)
        return true
    }

    func closeProduct() {
        state.openProduct = nil
    }

    func showProduct(_ groupId: Int, _ productId: Int64) {
        state.openProduct = ProductKey(groupId: groupId, productId: productId)
    }

    /// Вступить или выйти. Из своей группы выйти нельзя — её можно только удалить.
    func toggleGroup(_ groupId: Int) {
        state.groups = state.groups.map { group in
            var g = group
            if g.id == groupId && !g.mine {
                g.members += g.joined ? -1 : 1
                g.joined.toggle()
            }
            return g
        }
    }

    /// Отключить или включить уведомления группы.
    func toggleGroupMute(_ groupId: Int) {
        state.groups = state.groups.map { group in
            var g = group
            if g.id == groupId { g.muted.toggle() }
            return g
        }
    }

    /// Новая группа: создатель сразу в ней. Ошибка — текст для экрана, nil — успех.
    @discardableResult
    func createGroup(name: String, description: String, topic: String) -> String? {
        let title = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if title.count < 3 { return t("Название — от 3 символов") }
        if title.count > 40 { return t("Название — не длиннее 40 символов") }
        if state.groups.contains(where: { $0.name.lowercased() == title.lowercased() }) {
            return tf("Группа «%s» уже есть", title)
        }
        let id = (state.groups.map(\.id).max() ?? 0) + 1
        let cleanTopic = topic.trimmingCharacters(in: .whitespaces)
        let group = MemoryGroup(
            id: id, name: title,
            description: description.trimmingCharacters(in: .whitespacesAndNewlines),
            topic: cleanTopic.isEmpty ? (groupTopics.last ?? t("Другое")) : cleanTopic,
            members: 1, seed: 100 + id, joined: true, mine: true
        )
        state.groups.insert(group, at: 0)
        openGroup(id)
        return nil
    }

    func deleteGroup(_ groupId: Int) {
        state.groups.removeAll { $0.id == groupId && $0.mine }
        state.openGroupId = nil
        state.groupSettingsOpen = false
    }

    /// Запись в группе — для участников (или только для создателя, если он
    /// так решил): текст и вложения — фото, видео, документы, музыка.
    func postInGroup(_ groupId: Int, _ text: String, media: [Attachment] = [], poll: PollDraft? = nil) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let built = poll.flatMap { $0.build(newId()) }
        if trimmed.isEmpty && media.isEmpty && built == nil { return }
        let me = state.me
        state.groups = state.groups.map { group in
            var g = group
            if g.id == groupId && g.joined && (!g.onlyOwnerPosts || g.mine) {
                g.posts.insert(Post(
                    id: newId(), author: me, text: trimmed, minutesAgo: 0,
                    media: Array(media.prefix(Self.maxMedia)), createdAt: clock(), poll: built
                ), at: 0)
            }
            return g
        }
        media.filter(\.isGif).forEach(rememberGif)
    }

    // MARK: - Опросы

    /// Своя ли запись — у своих опросов есть «Завершить».
    func isMine(_ post: Post) -> Bool { post.author.id == state.me.id }

    /// Изменить опрос записи — в ленте или в группе.
    private func updatePostPoll(_ postId: Int64, _ change: (Poll) -> Poll) {
        updatePosts(postId) { p in
            if let poll = p.poll { p.poll = change(poll) }
        }
    }

    private func updateMessagePoll(_ chatId: Int, _ pollId: Int64, _ change: (Poll) -> Poll) {
        updateChat(chatId) { c in
            c.messages = c.messages.map { m in
                var m = m
                if let poll = m.poll, poll.id == pollId { m.poll = change(poll) }
                return m
            }
        }
    }

    /// Голос в опросе записи (ленты или группы).
    func votePost(_ postId: Int64, _ option: Int) { updatePostPoll(postId) { $0.vote(option) } }

    /// Голос в опросе сообщения.
    func voteMessage(_ chatId: Int, _ pollId: Int64, _ option: Int) { updateMessagePoll(chatId, pollId) { $0.vote(option) } }

    /// Закрыть свой опрос: голосовать больше нельзя, итоги видны всем.
    func closePostPoll(_ postId: Int64) { updatePostPoll(postId) { $0.close() } }

    func closeMessagePoll(_ chatId: Int, _ pollId: Int64) { updateMessagePoll(chatId, pollId) { $0.close() } }

    // MARK: - Гифки, коллекции стикеров, фоны

    /// Отправленная гифка — в «Недавние» (до 30, новые первыми).
    func rememberGif(_ gif: Attachment) {
        guard gif.isGif else { return }
        let name = gif.name.isEmpty ? gif.fileName : gif.name
        state.recentGifs = Array(([gif] + state.recentGifs.filter { !($0.name.isEmpty == false && $0.name == name) }).prefix(30))
    }

    /// Новая коллекция стикеров; nil — пустое имя или такая уже есть.
    @discardableResult
    func createStickerPack(_ name: String) -> StickerPack? {
        let n = String(name.trimmingCharacters(in: .whitespacesAndNewlines).prefix(30))
        if n.isEmpty || state.stickerPacks.contains(where: { $0.name.lowercased() == n.lowercased() }) { return nil }
        // Бесплатно — две коллекции
        if state.stickerPacks.count >= Premium.freePacks && !require(.stickerPacks) { return nil }
        let pack = StickerPack(id: "pack-" + String(UUID().uuidString.prefix(8)).lowercased(), name: n)
        state.stickerPacks.append(pack)
        return pack
    }

    func renameStickerPack(_ id: String, _ name: String) {
        let n = String(name.trimmingCharacters(in: .whitespacesAndNewlines).prefix(30))
        if n.isEmpty { return }
        state.stickerPacks = state.stickerPacks.map { p in
            var p = p
            if p.id == id { p.name = n }
            return p
        }
    }

    /// Удалить коллекцию: стикеры остаются, просто без коллекции.
    func deleteStickerPack(_ id: String) {
        state.stickerPacks.removeAll { $0.id == id }
        state.stickers = state.stickers.map { s in
            var s = s
            if s.packId == id { s.packId = "" }
            return s
        }
    }

    func moveSticker(_ stickerId: String, _ packId: String) {
        state.stickers = state.stickers.map { s in
            var s = s
            if s.id == stickerId { s.packId = packId }
            return s
        }
    }

    /// Общий фон переписок.
    func setBackdrop(_ backdrop: Backdrop) {
        // Бесплатно — четыре фона; остальные и своё фото — Memory+
        // Тот же фон, что уже стоит (меняют только затемнение), — можно и без подписки
        let same = backdrop.style == state.backdrop.style && backdrop.photo == state.backdrop.photo
        if !Premium.freeBackdrops.contains(backdrop.kind) && !same && !require(.backdrops) { return }
        let before = backdropPhotos()
        state.backdrop = backdrop
        dropBackdropPhotos(before)
    }

    /// Свой фон у переписки; nil — как у всех.
    func setChatBackdrop(_ chatId: Int, _ backdrop: Backdrop?) {
        // Свой фон у переписки — Memory+; вернуть «как у всех» можно всегда
        // Тот же фон, что уже стоит у переписки (меняют только затемнение), — можно и без подписки
        let current = state.chats.first { $0.id == chatId }?.backdrop
        if let b = backdrop, !(b.style == current?.style && b.photo == current?.photo), !require(.chatBackdrop) { return }
        let before = backdropPhotos()
        updateChat(chatId) { $0.backdrop = backdrop }
        dropBackdropPhotos(before)
    }

    /// Свои фото фонов, которые сейчас где-то стоят: общий фон, фоны переписок и страниц групп.
    private func backdropPhotos() -> Set<String> {
        Set(([state.backdrop] + state.chats.compactMap(\.backdrop) + state.groups.compactMap(\.backdrop))
            .map(\.photo).filter { !$0.isEmpty })
    }

    /// Своё фото фона, которое больше нигде не стоит, — удалить копию.
    private func dropBackdropPhotos(_ before: Set<String>) {
        for name in before.subtracting(backdropPhotos()) {
            try? FileManager.default.removeItem(at: MediaFiles.directory.appendingPathComponent(name))
        }
    }

    /// Поделиться местом в переписке.
    func sendPlace(_ chatId: Int, _ at: LatLon, title: String = "") {
        send(chatId, "", place: SharedPlace(lat: at.lat, lon: at.lon,
                                            title: String(title.trimmingCharacters(in: .whitespacesAndNewlines).prefix(80))))
    }

    func toggleGroupPostLike(_ groupId: Int, _ postId: Int64) {
        state.groups = state.groups.map { group in
            var g = group
            if g.id == groupId { g.posts = g.posts.map { Self.toggled($0, postId) } }
            return g
        }
    }

    // MARK: - Профиль

    /// Вошедший человек: его имя и логин — в профиле и в его записях.
    func setAccount(username: String, displayName: String, info: ProfileInfo = ProfileInfo()) {
        var me = state.me
        me.name = displayName
        me.handle = username
        me.bio = info.bio
        me.city = info.city
        me.birthday = info.birthday
        me.website = info.website
        me.interests = info.interests
        me.avatar = info.avatar
        applyMe(me)
    }

    /// После выхода состояние сбрасывается: следующий вошедший не увидит чужие черновики и переписку.
    func reset() {
        detachNet()
        lifeTask?.cancel()
        lifeTask = nil
        liveTask?.cancel()
        liveTask = nil
        if state.myLive != nil || state.watchingLiveId != nil { liveTransport.leave() }
        acceptTasks.values.forEach { $0.cancel() }
        acceptTasks = [:]
        // Вышли из аккаунта посреди звонка — звонок кладётся
        callTask?.cancel()
        callTask = nil
        if state.call != nil { callTransport.hangUp() }
        state = UiState()
    }

    func updateProfile(name: String, info: ProfileInfo) {
        let newName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if newName.isEmpty { return }
        var me = state.me
        me.name = newName
        me.bio = info.bio.trimmingCharacters(in: .whitespacesAndNewlines)
        me.city = info.city.trimmingCharacters(in: .whitespacesAndNewlines)
        me.birthday = info.birthday.trimmingCharacters(in: .whitespacesAndNewlines)
        me.website = info.website.trimmingCharacters(in: .whitespacesAndNewlines)
        me.interests = info.interests
        me.avatar = info.avatar
        applyMe(me)
        pushProfile()
    }

    /// Своё фото в профиле; "" — вернуть аватар из букв.
    func setAvatar(_ fileName: String) {
        var me = state.me
        me.avatar = fileName
        applyMe(me)
    }

    /// Сведения профиля как есть сейчас — чтобы сохранить их в аккаунт.
    func profileInfo() -> ProfileInfo {
        let me = state.me
        return ProfileInfo(bio: me.bio, city: me.city, birthday: me.birthday, website: me.website,
                           interests: me.interests, avatar: me.avatar ?? "")
    }

    func setNotifications(_ notifications: Notifications) {
        state.notifications = notifications
    }

    /// Заявки в друзья от закрытости профиля не зависят — ждут ответа как есть.
    func setPrivacy(_ privacy: Privacy) {
        state.privacy = privacy
        pushProfile()
    }

    /// В чёрный список: записи, комментарии и сообщения человека больше не
    /// видны, он уходит из друзей, заявки с обеих сторон снимаются. Себя
    /// заблокировать нельзя.
    func block(_ userId: Int) {
        if userId == state.me.id { return }
        acceptTasks.removeValue(forKey: userId)?.cancel()
        state.blocked.insert(userId)
        state.following.remove(userId)
        state.followRequests.removeAll { $0 == userId }
        state.sentRequests.remove(userId)
    }

    func unblock(_ userId: Int) {
        state.blocked.remove(userId)
    }

    // MARK: - Жалобы и документы

    /// Сколько жалоб держим в очереди до отправки.
    static let maxReports = 200

    /// Открыть выбор причины жалобы.
    func startReport(_ target: ReportTarget) {
        state.reporting = target
    }

    func reportPost(_ post: Post) {
        startReport(ReportTarget(kind: .post, id: post.id, authorId: post.author.id, authorName: post.author.name,
                                 preview: String(post.text.prefix(80))))
    }

    func reportPrism(_ prism: Prism) {
        startReport(ReportTarget(kind: .prism, id: prism.id, authorId: prism.author.id, authorName: prism.author.name,
                                 preview: String(prism.caption.prefix(80))))
    }

    func reportComment(_ comment: Comment) {
        startReport(ReportTarget(kind: .comment, id: comment.id, authorId: comment.author.id, authorName: comment.author.name,
                                 preview: String(comment.text.prefix(80))))
    }

    /// На свои сообщения не жалуются.
    func reportMessage(_ chatId: Int, _ message: Message) {
        guard let peer = state.chats.first(where: { $0.id == chatId })?.peer, !message.fromMe else { return }
        startReport(ReportTarget(kind: .message, id: message.id, authorId: peer.id, authorName: peer.name,
                                 chatId: chatId, preview: String(previewOf(message).prefix(80))))
    }

    func reportPerson(_ user: User) {
        startReport(ReportTarget(kind: .person, id: Int64(user.id), authorId: user.id, authorName: user.name,
                                 preview: "@" + user.handle))
    }

    func cancelReport() {
        state.reporting = nil
    }

    /// Жалоба: в очередь модераторам и сразу скрыть у себя то, на что
    /// пожаловались. block — заодно заблокировать автора.
    func report(_ target: ReportTarget, _ reason: ReportReason, note: String = "", block: Bool = false) {
        let text = String(note.trimmingCharacters(in: .whitespacesAndNewlines).prefix(500))
        let entry = Report(id: newId(), target: target, reason: reason, note: text, createdAt: clock())
        var s = state
        s.reports = Array((s.reports + [entry]).suffix(Self.maxReports))
        s.reporting = nil
        switch target.kind {
        case .comment:
            s.reportedComments.insert(target.id)
        case .post:
            s.posts.removeAll { $0.id == target.id }
            s.groups = s.groups.map { group in
                var g = group
                g.posts.removeAll { $0.id == target.id }
                return g
            }
            if s.commentsPostId == target.id { s.commentsPostId = nil }
        case .prism:
            s.prisms.removeAll { $0.id == target.id }
            if s.commentsPrismId == target.id { s.commentsPrismId = nil }
        case .message:
            s.chats = s.chats.map { chat in
                guard chat.id == target.chatId else { return chat }
                var c = chat
                c.messages.removeAll { $0.id == target.id }
                if c.pinnedId == target.id { c.pinnedId = nil }
                return c
            }
        case .person:
            break
        }
        state = s
        if block && target.authorId != 0 && target.authorId != state.me.id {
            self.block(target.authorId)
        }
    }

    func openLegal(_ doc: LegalDoc) {
        state.legalDoc = doc
    }

    func closeLegal() {
        state.legalDoc = nil
    }

    /// Свои записи и комментарии показывают новое имя.
    private func applyMe(_ me: User) {
        state.applyMe(me)
    }

    // MARK: - Уведомления и итоги

    /// Куда уходят персональные уведомления. MemoryApp показывает их
    /// системным уведомлением, когда приложение свёрнуто, а пока оно
    /// открыто — плашкой сверху (state.banner).
    @ObservationIgnored var onNudge: ((Nudge) -> Void)?

    /// Отправить уведомление, если такой раздел не выключен в настройках.
    func notify(_ nudge: Nudge) {
        guard Nudges.allowed(nudge, state.notifications) else { return }
        if let onNudge { onNudge(nudge) } else { state.banner = nudge }
    }

    func showBanner(_ nudge: Nudge) { state.banner = nudge }

    func dismissBanner() { state.banner = nil }

    /// Нажатие на уведомление: туда, о чём оно.
    func openNudge(_ nudge: Nudge) {
        dismissBanner()
        switch nudge.target {
        case .post:
            if let post = state.findPost(nudge.targetId), post.author.id == state.me.id {
                openProfileSection(.posts)
            } else {
                selectTab(.feed)
            }
        case .group:
            selectTab(.groups)
            openGroup(Int(nudge.targetId))
        case .person:
            // Друг в эфире — сразу в эфир
            if let live = state.visibleLives.first(where: { $0.hostId == Int(nudge.targetId) }) {
                watchLive(live.id)
            } else {
                openPerson(Int(nudge.targetId))
            }
        case .chat:
            if state.chats.contains(where: { $0.id == Int(nudge.targetId) }) { openChat(Int(nudge.targetId)) } else { selectTab(.chats) }
        case .recap:
            // targetId — год × 100 + месяц; месяц 0 — итоги года
            let year = Int(nudge.targetId / 100)
            let month = Int(nudge.targetId % 100)
            let now = Calendar.current.dateComponents([.year, .month], from: Date())
            let thisYear = now.year ?? year
            let thisMonth = now.month ?? month
            if year == 0 {
                openRecap(.month)
            } else if month == 0 {
                openRecap(.year, back: max(0, thisYear - year))
            } else {
                openRecap(.month, back: max(0, (thisYear * 12 + thisMonth) - (year * 12 + month)))
            }
        case .requests:
            openProfileSection(.requests)
        case .prism:
            selectTab(.prism)
            if state.prisms.contains(where: { $0.id == nudge.targetId }) { state.prismStartId = nudge.targetId }
        case .together:
            selectTab(.together)
            let sections = TogetherSection.allCases
            let index = Int(nudge.targetId)
            if sections.indices.contains(index) { selectTogetherSection(sections[index]) }
        }
    }

    /// Итоги месяца или года; back — на сколько периодов назад от текущего.
    func openRecap(_ period: RecapPeriod, back: Int = 0) {
        openProfileSection(.recap)
        state.recapPeriod = period
        state.recapBack = back
    }

    func setRecapPeriod(_ period: RecapPeriod) {
        state.recapPeriod = period
        state.recapBack = 0
    }

    func setRecapBack(_ back: Int) {
        // Бесплатно — текущий и прошлый период; дальше в прошлое — Memory+
        if back > Premium.freeRecapBack && back > state.recapBack && !require(.recapHistory) { return }
        state.recapBack = max(0, back)
    }

    /// Итоги за период.
    func recap(_ period: RecapPeriod, year: Int, month: Int) -> Recap {
        Recap.compute(me: state.me, posts: state.posts, prisms: state.prisms, period: period, year: year, month: month)
    }

    /// В начале месяца — уведомление об итогах прошлого (если было что
    /// считать), в январе — ещё и об итогах года. Один раз на период.
    func checkRecapReady(today: Date = Date()) {
        let previous = Recap.previousMonth(today)
        var list = [recap(.month, year: previous.year, month: previous.month)]
        let c = Calendar.current.dateComponents([.year, .month], from: today)
        if c.month == 1 { list.append(recap(.year, year: (c.year ?? 2026) - 1, month: 0)) }
        for r in list where !state.recapNotified.contains(r.key) && !r.empty {
            state.recapNotified.insert(r.key)
            notify(Nudges.recap(r))
        }
    }

    @ObservationIgnored private var lifeTask: Task<Void, Never>?

    /// Жизнь вокруг, пока нет сервера: друзья отмечают чек-ины, в группы
    /// приходят люди, вашим записям ставят «нравится», приходят заявки в
    /// друзья. Каждое событие — с персональным уведомлением.
    func startDemoLife() {
        guard lifeTask == nil else { return }
        // Заявки, отправленные до перезапуска, тоже дождутся ответа
        state.sentRequests.forEach { scheduleAcceptance($0) }
        lifeTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 25_000_000_000)
            while !Task.isCancelled {
                guard let self else { return }
                var rng = SystemRandomNumberGenerator()
                self.demoEvent(using: &rng)
                try? await Task.sleep(nanoseconds: UInt64.random(in: 35_000_000_000...75_000_000_000))
                if Task.isCancelled { return }
                self.togetherEvent(using: &rng)
                try? await Task.sleep(nanoseconds: UInt64.random(in: 35_000_000_000...75_000_000_000))
                if Task.isCancelled { return }
                self.socialEvent(using: &rng)
                try? await Task.sleep(nanoseconds: UInt64.random(in: 35_000_000_000...75_000_000_000))
            }
        }
    }

    /// Всё, что вы сами писали, — чтобы понять, что вы обсуждали.
    func myTexts() -> [String] {
        let meId = state.me.id
        let all: [Post] = state.posts + state.groups.flatMap(\.posts)
        var texts: [String] = []
        for post in all {
            if post.author.id == meId { texts.append(post.text) }
            for c in post.commentList where c.author.id == meId { texts.append(c.text) }
        }
        for prism in state.prisms {
            for c in prism.commentList where c.author.id == meId { texts.append(c.text) }
        }
        for chat in state.chats {
            for m in chat.messages where m.fromMe { texts.append(m.text) }
        }
        return texts
    }

    /// Ваши километры за текущий месяц — для «не отставайте».
    private func myKmThisMonth() -> Double {
        let now = Calendar.current.dateComponents([.year, .month], from: clock())
        return state.posts.filter { p in
            guard p.author.id == state.me.id, let d = p.createdAt else { return false }
            let c = Calendar.current.dateComponents([.year, .month], from: d)
            return c.year == now.year && c.month == now.month
        }
        .compactMap(\.checkIn).filter { isKilometers($0.unit) }.reduce(0) { $0 + $1.amount }
    }

    /// Одно событие демо-жизни. Отдельно от цикла — чтобы проверять в тестах.
    func demoEvent<G: RandomNumberGenerator>(using rng: inout G) {
        let friends = state.friends.filter { !state.blocked.contains($0.id) }
        let joined = state.groups.filter { $0.joined && !$0.muted }
        let mine = state.myPosts
        let requesters = Demo.people.filter { p in
            !state.blocked.contains(p.id) && !state.followRequests.contains(p.id) && !state.following.contains(p.id) && !state.sentRequests.contains(p.id)
                && !declined.contains(p.id)
        }
        var options: [Int] = []
        if !friends.isEmpty { options += [0, 0, 0, 0] }
        if !joined.isEmpty { options += [1, 1] }
        if !mine.isEmpty { options += [2, 2] }
        if !requesters.isEmpty { options.append(3) }
        if !friends.isEmpty { options.append(4) }
        if !friends.isEmpty { options += [5, 5] }
        if !mine.isEmpty && !friends.isEmpty { options.append(6) }
        if !friends.isEmpty && state.call == nil { options.append(7) }
        // Пишет незнакомый — в «Запросы»; пишут в группе
        let hasDemo = net.demo != nil
        let groupChats: [Chat] = state.chats.filter(\.isGroup)
        if !requesters.isEmpty && hasDemo { options.append(8) }
        if !groupChats.isEmpty && hasDemo { options += [9, 9] }
        guard let pick = options.randomElement(using: &rng) else { return }
        switch pick {
        case 0:
            friendCheckIn(friends.randomElement(using: &rng)!, using: &rng)
        case 1:
            let group = joined.randomElement(using: &rng)!
            let name = Demo.newcomers.randomElement(using: &rng)!
            let person = User(id: 500 + Int.random(in: 0..<1000, using: &rng), name: name,
                              handle: name.lowercased().replacingOccurrences(of: " ", with: "."),
                              hue: Double.random(in: 0..<1, using: &rng))
            state.groups = state.groups.map { g in
                var g = g
                if g.id == group.id { g.members += 1 }
                return g
            }
            if group.mine { record(.groupJoin, person, targetId: Int64(group.id), preview: group.name) }
            notify(Nudges.groupMember(group, person))
        case 2:
            let post = mine.randomElement(using: &rng)!
            let fans = Demo.people.filter { !state.blocked.contains($0.id) }
            let fan = fans.randomElement(using: &rng)!
            if Int.random(in: 0..<3, using: &rng) == 0 {
                // Иногда — не «нравится», а комментарий, порой с упоминанием
                demoComment(post, fan, using: &rng)
            } else {
                state.posts = state.posts.map { p in
                    var p = p
                    if p.id == post.id { p.likes += 1 }
                    return p
                }
                record(.like, fan, targetId: post.id, preview: post.text)
                notify(Nudges.liked(fan, post))
            }
        case 3:
            receiveFriendRequest(requesters.randomElement(using: &rng)!.id)
        case 4:
            friendGoesLive(friends.randomElement(using: &rng)!, using: &rng)
        case 5:
            storyEvent(friends.randomElement(using: &rng)!, using: &rng)
        case 6:
            // Друг репостнул вашу запись
            let post = mine.randomElement(using: &rng)!
            let fan = friends.randomElement(using: &rng)!
            state.posts = state.posts.map { p in
                var p = p
                if p.id == post.id { p.reposts += 1 }
                return p
            }
            record(.repost, fan, targetId: post.id, preview: post.text)
        case 7:
            let friend = friends.randomElement(using: &rng)!
            incomingCall(friend.id, video: Bool.random(using: &rng))
        case 8:
            let stranger = requesters.randomElement(using: &rng)!
            let text = Demo.strangerTexts.randomElement(using: &rng)!
            demoDeliver(Self.strangerChatBase + stranger.id, stranger.id, text, afterMs: 0)
        default:
            let group = groupChats.randomElement(using: &rng)!
            let meId = state.me.id
            let blocked = state.blocked
            let others: [Int] = (group.group?.memberIds ?? []).filter { $0 != meId && !blocked.contains($0) }
            guard let who = others.randomElement(using: &rng) else { return }
            net.demo?.realtime?.schedule(.typing(chatId: group.id, userId: who, on: true), afterMs: 0)
            let reply = Demo.replies.randomElement(using: &rng)!
            demoDeliver(group.id, who, reply, afterMs: 2_500)
        }
    }

    /// Демо: сервер «присылает» сообщение по живому каналу.
    private func demoDeliver(_ chatId: Int, _ authorId: Int, _ text: String, afterMs: Int64) {
        guard let d = net.demo else { return }
        var m = Message(id: 0, fromMe: false, text: text, time: Self.now())
        m.authorId = authorId
        d.deliver(chatId, ApiCodec(me: state.me).messageJson(m), afterMs: afterMs)
    }

    /// Друг отметил чек-ин: запись в ленте и уведомление — с тем, что вы обсуждали.
    private func friendCheckIn<G: RandomNumberGenerator>(_ friend: User, using rng: inout G) {
        let texts = myTexts()
        let kinds = ["movie", "movie", "series", "book", "run", "bike", "walk", "coffee", "place", "concert"]
        let kind = kinds.randomElement(using: &rng)!
        guard let activity = checkInActivities.first(where: { $0.id == kind }) else { return }
        func pick(_ list: [String]) -> String {
            // Что вы обсуждали — в приоритете: ради этого и уведомление
            let talked = list.filter { Nudges.discussed($0, texts) }
            if !talked.isEmpty && Double.random(in: 0..<1, using: &rng) < 0.75 { return talked.randomElement(using: &rng)! }
            return list.randomElement(using: &rng)!
        }
        let subject: String
        switch kind {
        case "movie": subject = pick(Demo.movies)
        case "series": subject = pick(Demo.series)
        case "book": subject = pick(Demo.books)
        case "coffee", "place": subject = pick(Demo.places)
        case "concert": subject = pick(["Сплин", "Мумий Тролль", "Земфира", "Би-2"])
        default: subject = pick(["Набережная", "Парк", "Вокруг дома"])
        }
        let amount: Double
        switch kind {
        case "run": amount = Double(3 + Int.random(in: 0..<10, using: &rng))
        case "bike": amount = Double(10 + Int.random(in: 0..<30, using: &rng))
        case "walk": amount = Double(2 + Int.random(in: 0..<8, using: &rng))
        case "book": amount = Double(20 + Int.random(in: 0..<80, using: &rng))
        default: amount = 0
        }
        let c = activity.checkIn(subject, amount)
        let id = newId()
        state.posts.insert(Post(id: id, author: friend, text: "", minutesAgo: 0, likes: Int.random(in: 0..<6, using: &rng), createdAt: clock(), checkIn: c), at: 0)
        notify(Nudges.friendCheckIn(friend, c, discussed: Nudges.discussed(subject, texts), myKm: myKmThisMonth(), postId: id))
    }

    /// Ещё одна демо-жизнь: голоса в ваших опросах, новый PRISM друга,
    /// место от друга в переписке. Голоса приходят только в открытые опросы.
    func socialEvent<G: RandomNumberGenerator>(using rng: inout G) {
        let s = state
        let friends = Demo.people.filter { !s.blocked.contains($0.id) }
        if friends.isEmpty { return }
        let myPolls = (s.posts + s.groups.flatMap(\.posts)).filter { $0.author.id == s.me.id && $0.poll?.closed == false }
        let pick = myPolls.isEmpty ? 1 + Int.random(in: 0..<2, using: &rng) : Int.random(in: 0..<3, using: &rng)
        switch pick {
        case 0:
            let post = myPolls[Int.random(in: 0..<myPolls.count, using: &rng)]
            guard let poll = post.poll else { return }
            let added = 1 + Int.random(in: 0..<4, using: &rng)
            var votes: [Int: Int] = [:]
            for _ in 0..<added { votes[Int.random(in: 0..<poll.options.count, using: &rng), default: 0] += 1 }
            updatePostPoll(post.id) { $0.addVotes(votes) }
            record(.pollVotes, nil, targetId: post.id, preview: poll.question, count: added)
            let groupId = s.groups.first { g in g.posts.contains { $0.id == post.id } }?.id
            notify(Nudges.pollVotes(poll.question, added, postId: post.id, groupId: groupId))
        case 1:
            let friend = friends[Int.random(in: 0..<friends.count, using: &rng)]
            let captions = ["Огни ночного города", "Закат над рекой", "Первый снег", "Звёзды над крышей", "Кофе и дождь"]
            let prism = Prism(
                id: newId(), author: friend, caption: t(captions[Int.random(in: 0..<captions.count, using: &rng)]),
                music: tf("Оригинальный звук · %s", friend.name), seed: Int.random(in: 0..<1000, using: &rng),
                likes: Int.random(in: 0..<40, using: &rng), comments: 0, createdAt: clock()
            )
            state.prisms.insert(prism, at: 0)
            notify(Nudges.prismPublished(friend, prism))
        default:
            let chats = s.chats.filter { !s.blocked.contains($0.peer.id) && !$0.muted }
            guard let chat = chats.randomElement(using: &rng) else { return }
            let here = s.together.anchor.offset(east: Double.random(in: -900..<900, using: &rng), north: Double.random(in: -900..<900, using: &rng))
            append(chat.id, Message(id: newId(), fromMe: false, text: t("Я тут, подходи"), time: Self.now(),
                                    place: SharedPlace(lat: here.lat, lon: here.lon, title: t("Я здесь"))))
            if s.openChatId != chat.id { notify(Nudges.placeShared(chat.peer, chatId: chat.id)) }
        }
    }

    // MARK: - Вместе

    func selectTogetherSection(_ section: TogetherSection) {
        state.togetherSection = section
    }

    /// Изменить состояние «Вместе»; nil из правила — ничего не меняется. true — изменилось.
    @discardableResult
    private func together(_ change: (TogetherState) -> TogetherState?) -> Bool {
        guard let next = change(state.together) else { return false }
        state.together = next
        return true
    }

    private var meId: Int { state.me.id }

    /// Позже, в демо: ответ «с той стороны». В тестах не срабатывает.
    private func later(_ from: Double, _ to: Double, _ block: @escaping (AppModel) -> Void) {
        guard demoReplies else { return }
        Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(Double.random(in: from...to) * 1_000_000_000))
            guard let self else { return }
            block(self)
        }
    }

    /// Сообщение человеку от вашего имени — без открытия переписки: так
    /// договорённость об обмене или вылазке остаётся в чате.
    private func messageTo(_ userId: Int, _ text: String) {
        guard let person = Demo.person(userId) else { return }
        let chatId: Int
        if let existing = state.chats.first(where: { $0.peer.id == userId }) {
            chatId = existing.id
        } else {
            chatId = (state.chats.map(\.id).max() ?? 0) + 1
            state.chats.insert(Chat(id: chatId, peer: person, messages: []), at: 0)
        }
        append(chatId, Message(id: newId(), fromMe: true, text: text, time: Self.now()))
    }

    /// Где вы: демо-точки один раз переезжают к вам.
    func setLocation(_ here: LatLon) {
        let me = meId
        together { $0.relocate(here, meId: me) }
    }

    // MARK: Цели

    func joinGoal(_ id: String) { together { $0.joinGoal(id) } }

    func contribute(_ id: String, _ amount: Double) { together { $0.contribute(id, amount) } }

    // MARK: Навыки

    func addSkill(teach: Bool, _ skill: String) { together { $0.addSkill(teach: teach, skill) } }

    func removeSkill(teach: Bool, _ skill: String) { together { $0.removeSkill(teach: teach, skill) } }

    /// Предложить обмен. give — чему научите вы, get — чему научат вас (""
    /// — ничему). nil — предложено; иначе — что не так (не хватает часов).
    @discardableResult
    func proposeDeal(_ personId: Int, give: String, get: String, minutes: Int) -> String? {
        let id = newId()
        let now = clock()
        let ok = together { $0.propose(id: id, personId: personId, give: give, get: get, minutes: minutes, now: now) }
        if !ok {
            return give.isEmpty && !get.isEmpty
                ? t("Не хватает часов в банке времени — научите кого-нибудь сами")
                : t("Выберите, чему учите или чему хотите научиться")
        }
        let text: String
        if !give.isEmpty && !get.isEmpty {
            text = tf("Привет! Давай обмен: я научу «%s», а ты меня — «%s». По %s.", give, get, minutesTitle(minutes))
        } else if !get.isEmpty {
            text = tf("Привет! Научишь меня «%s»? %s — расплачусь часами из банка времени.", get, minutesTitle(minutes))
        } else {
            text = tf("Привет! Могу научить «%s» — %s.", give, minutesTitle(minutes))
        }
        messageTo(personId, text)
        // В демо человек соглашается через полминуты
        later(15, 40) { model in
            guard let deal = model.state.together.deals.first(where: { $0.id == id }), deal.status == .proposed else { return }
            model.together { $0.setDeal(id, .agreed) }
            if let person = Demo.person(personId) { model.notify(Nudges.dealAgreed(person, deal)) }
        }
        return nil
    }

    func setDeal(_ id: Int64, _ status: DealStatus) { together { $0.setDeal(id, status) } }

    // MARK: Дневники

    @discardableResult
    func startJourney(emoji: String, title: String, days: Int) -> Bool {
        let id = newId()
        let me = meId
        let now = clock()
        return together { $0.startJourney(id: id, me: me, emoji: emoji, title: title, days: days, now: now) }
    }

    func addJourneyEntry(_ journeyId: Int64, text: String, media: [Attachment]) {
        let entryId = newId()
        let now = clock()
        together { $0.addEntry(journeyId, entryId: entryId, text: text, media: media, now: now) }
        // В демо к новой записи приходит совет
        later(20, 60) { model in
            var rng = SystemRandomNumberGenerator()
            model.demoTip(journeyId, entryId, using: &rng)
        }
    }

    private func demoTip<G: RandomNumberGenerator>(_ journeyId: Int64, _ entryId: Int64, using rng: inout G) {
        guard let journey = state.together.journeys.first(where: { $0.id == journeyId }),
              let entry = journey.entries.first(where: { $0.id == entryId }),
              let adviser = Demo.people.filter({ !state.blocked.contains($0.id) && $0.id != journey.authorId }).randomElement(using: &rng),
              let advice = TogetherDemo.tips.randomElement(using: &rng) else { return }
        let tip = Tip(id: newId(), authorId: adviser.id, text: advice, createdAt: clock())
        together { $0.addTip(journeyId, entryId, tip) }
        notify(Nudges.tipReceived(adviser, journey, day: entry.day))
    }

    func addTip(_ journeyId: Int64, _ entryId: Int64, _ text: String) {
        let tip = Tip(id: newId(), authorId: meId, text: String(text.trimmingCharacters(in: .whitespacesAndNewlines).prefix(500)), createdAt: clock())
        together { $0.addTip(journeyId, entryId, tip) }
    }

    func toggleHelpful(_ journeyId: Int64, _ entryId: Int64, _ tipId: Int64) {
        together { $0.toggleHelpful(journeyId, entryId, tipId) }
    }

    // MARK: Миссии

    func joinMission(_ id: String) { together { $0.joinMission(id) } }

    func addFind(_ missionId: String, note: String, at: LatLon, photo: Attachment?) {
        // Маршрут, который открывают километры, до них закрыт
        if state.together.missions.first(where: { $0.id == missionId })?.locked(state.movedKm) == true { return }
        let find = MissionFind(id: newId(), authorId: meId, note: String(note.trimmingCharacters(in: .whitespacesAndNewlines).prefix(200)),
                               at: at, photo: photo, createdAt: clock())
        together { $0.addFind(missionId, find) }
    }

    /// Своя миссия района — право за пять находок. nil — создана; иначе что не так.
    @discardableResult
    func createMission(emoji: String, title: String, description: String, target: Int) -> String? {
        if !state.unlocked(.ownMission) { return Unlock.ownMission.how }
        func clip(_ s: String, _ n: Int) -> String { String(s.trimmingCharacters(in: .whitespacesAndNewlines).prefix(n)) }
        if clip(title, 60).isEmpty { return t("Назовите миссию") }
        let mission = Mission(id: "own-\(newId())", emoji: emoji, title: clip(title, 60), description: clip(description, 300),
                              target: target, authorId: meId)
        let ok = together { $0.createMission(mission) }
        return ok ? nil : t("Не получилось — попробуйте другое название")
    }

    /// Своя общая цель — право за вклад в две чужие. nil — создана.
    @discardableResult
    func createGoal(emoji: String, title: String, description: String, unit: String, target: Double) -> String? {
        if !state.unlocked(.ownGoal) { return Unlock.ownGoal.how }
        func clip(_ s: String, _ n: Int) -> String { String(s.trimmingCharacters(in: .whitespacesAndNewlines).prefix(n)) }
        if clip(title, 60).isEmpty || clip(unit, 12).isEmpty { return t("Укажите название и единицу") }
        if target <= 0 { return t("Сколько нужно собрать?") }
        let goal = CommunityGoal(id: "own-\(newId())", emoji: emoji, title: clip(title, 60), description: clip(description, 300),
                                 unit: clip(unit, 12), target: target, total: 0, participants: 1, metric: .other, authorId: meId)
        let ok = together { $0.createGoal(goal) }
        return ok ? nil : t("Не получилось — попробуйте другое название")
    }

    // MARK: Клады

    @discardableResult
    func hideCache(title: String, hint: String, difficulty: Int, size: String, at: LatLon) -> Bool {
        // Прятать — право: сначала найдите три чужих (Unlock.hideCache)
        if !state.unlocked(.hideCache) { return false }
        let id = newId()
        let cache = GeoCache(
            id: id, authorId: meId,
            title: String(title.trimmingCharacters(in: .whitespacesAndNewlines).prefix(60)),
            hint: String(hint.trimmingCharacters(in: .whitespacesAndNewlines).prefix(300)),
            at: at, difficulty: min(5, max(1, difficulty)), size: size, createdAt: clock()
        )
        let ok = together { $0.hideCache(cache) }
        // В демо ваш клад кто-нибудь находит
        if ok {
            later(60, 180) { model in
                guard let finder = Demo.people.filter({ !model.state.blocked.contains($0.id) }).randomElement(),
                      model.state.together.caches.contains(where: { $0.id == id }) else { return }
                let log = CacheLog(id: model.newId(), authorId: finder.id, text: t("Нашёл! Отличное место."), createdAt: model.clock())
                model.state.together.caches = model.state.together.caches.map { c in
                    var c = c
                    if c.id == id { c.logs.append(log) }
                    return c
                }
                if let found = model.state.together.caches.first(where: { $0.id == id }) {
                    model.notify(Nudges.cacheFound(finder, found))
                }
            }
        }
        return ok
    }

    /// true — отметились; false — вы далеко или уже нашли.
    @discardableResult
    func findCache(_ id: Int64, text: String, here: LatLon?) -> Bool {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let log = CacheLog(id: newId(), authorId: meId, text: String((trimmed.isEmpty ? t("Нашёл!") : trimmed).prefix(300)), createdAt: clock())
        return together { $0.findCache(id, log, here: here) }
    }

    // MARK: Вылазки

    @discardableResult
    func createOuting(emoji: String, title: String, description: String, place: String, startsAt: Date, capacity: Int, at: LatLon?) -> Bool {
        let id = newId()
        let me = meId
        func clip(_ s: String, _ n: Int) -> String { String(s.trimmingCharacters(in: .whitespacesAndNewlines).prefix(n)) }
        let outing = Outing(id: id, organizerId: me, emoji: emoji, title: clip(title, 60), description: clip(description, 500),
                            place: clip(place, 80), startsAt: startsAt, capacity: capacity, participants: [me], at: at)
        let ok = together { $0.createOuting(outing) }
        // В демо к вылазке присоединяются
        if ok {
            later(30, 90) { model in
                guard let current = model.state.together.outings.first(where: { $0.id == id }), !current.full,
                      let guest = Demo.people.first(where: { !current.participants.contains($0.id) && !model.state.blocked.contains($0.id) })
                else { return }
                model.state.together.outings = model.state.together.outings.map { o in
                    var o = o
                    if o.id == id && !o.full { o.participants.append(guest.id) }
                    return o
                }
                model.notify(Nudges.outingJoined(guest, current))
            }
        }
        return ok
    }

    func toggleOuting(_ id: Int64) {
        let me = meId
        together { $0.toggleOuting(id, me: me) }
    }

    func cancelOuting(_ id: Int64) {
        let me = meId
        together { $0.cancelOuting(id, me: me) }
    }

    // MARK: Рисунок

    /// Новый рисунок; ответ — его номер, nil — пустая тема.
    @discardableResult
    func startDrawing(title: String, turns: Int) -> Int64? {
        let id = newId()
        let me = meId
        let now = clock()
        let ok = together { $0.startDrawing(id: id, title: title, turns: turns, me: me, now: now) }
        return ok ? id : nil
    }

    /// Свой ход: передать дальше. В демо друг дорисует и вернёт.
    func drawTurn(_ id: Int64, strokes: [DrawStroke], passTo: Int?) {
        let me = meId
        together { $0.drawTurn(id, me: me, strokes: strokes, passTo: passTo) }
        guard let drawing = state.together.drawings.first(where: { $0.id == id }),
              let friendId = drawing.waitingFor, friendId != me else { return }
        later(20, 50) { model in
            guard let friend = Demo.person(friendId) else { return }
            let colors: [UInt32] = [0xBC92CD, 0x8CC4FF, 0xFF9EDE, 0xFFD86B, 0x7BE0B0]
            var random = SeededRandom(Int(model.clock().timeIntervalSince1970 * 1000))
            let color = colors[random.nextInt(colors.count)]
            let strokes = TogetherDemo.demoStrokes(&random, color: color)
            model.together { $0.drawTurn(id, me: friendId, strokes: strokes, passTo: me) }
            if let updated = model.state.together.drawings.first(where: { $0.id == id }) {
                model.notify(Nudges.drawingTurn(friend, updated))
            }
        }
    }

    // MARK: Обмен

    @discardableResult
    func addShare(kind: ShareKind, title: String, description: String, photo: Attachment?) -> Bool {
        let me = meId
        let item = ShareItem(
            id: newId(), kind: kind,
            title: String(title.trimmingCharacters(in: .whitespacesAndNewlines).prefix(80)),
            description: String(description.trimmingCharacters(in: .whitespacesAndNewlines).prefix(500)),
            ownerId: me, holderId: me, photo: photo, createdAt: clock()
        )
        return together { $0.addShare(item) }
    }

    /// Попросить книгу или семена. В демо владелец передаёт через полминуты.
    func requestShare(_ id: Int64) {
        let me = meId
        together { $0.requestShare(id, me: me) }
        guard let item = state.together.shares.first(where: { $0.id == id }), item.requestedBy == me else { return }
        messageTo(item.holderId, tf("Привет! Можно мне «%s»? Заберу, когда удобно.", item.title))
        later(15, 45) { model in
            guard let current = model.state.together.shares.first(where: { $0.id == id }),
                  current.status == .requested, current.requestedBy == me,
                  let giver = Demo.person(current.holderId) else { return }
            model.together { $0.handOver(id, to: me) }
            model.notify(Nudges.shareHanded(giver, current))
        }
    }

    func handOver(_ id: Int64, to: Int) {
        together { $0.handOver(id, to: to) }
    }

    func offerHelp(_ id: Int64) {
        let me = meId
        together { $0.offerHelp(id, me: me) }
        if let item = state.together.shares.first(where: { $0.id == id }) {
            messageTo(item.ownerId, tf("Привет! Могу помочь починить «%s». Когда удобно?", item.title))
        }
    }

    func markFixed(_ id: Int64) { together { $0.markFixed(id) } }

    /// Событие «Вместе» в демо: шкалы растут, к вашим вещам и записям приходят люди.
    func togetherEvent<G: RandomNumberGenerator>(using rng: inout G) {
        // Общие шкалы растут всегда — сообщество не спит
        var goals = state.together.goals
        for i in goals.indices {
            let step = goals[i].target * (0.0002 + Double.random(in: 0..<1, using: &rng) * 0.0008)
            goals[i].total = min(goals[i].target, goals[i].total + step)
            goals[i].participants += Int.random(in: 0..<4, using: &rng)
        }
        state.together.goals = goals
        let me = meId
        let people = Demo.people.filter { !state.blocked.contains($0.id) }
        let myEntries: [(Int64, Int64)] = state.together.journeys.filter { $0.authorId == me }
            .flatMap { j in j.entries.map { (j.id, $0.id) } }
        let myShares = state.together.shares.filter { $0.holderId == me && $0.status == .available && $0.kind != .repair }
        var options: [Int] = []
        if !myEntries.isEmpty { options.append(0) }
        if !myShares.isEmpty { options.append(1) }
        guard !people.isEmpty, let pick = options.randomElement(using: &rng) else { return }
        if pick == 0 {
            guard let pair = myEntries.randomElement(using: &rng) else { return }
            demoTip(pair.0, pair.1, using: &rng)
        } else {
            guard let item = myShares.randomElement(using: &rng), let asker = people.randomElement(using: &rng) else { return }
            state.together.shares = state.together.shares.map { x in
                var x = x
                if x.id == item.id {
                    x.status = .requested
                    x.requestedBy = asker.id
                }
                return x
            }
            notify(Nudges.shareRequested(asker, item))
        }
    }

    // MARK: - Сохранение

    /// То, что сохраняется на телефоне между запусками (см. StateStore).
    func snapshot() -> Snapshot {
        Snapshot(
            posts: state.posts, chats: state.chats, groups: state.groups, prisms: state.prisms,
            following: state.following, notifications: state.notifications.all, nextId: nextId,
            notify: state.notifications, privacy: state.privacy, blocked: state.blocked,
            tracks: state.tracks, playlists: state.playlists, reportedComments: state.reportedComments,
            stickers: state.stickers, profilePins: state.profilePins,
            checkInTemplates: state.checkInTemplates, followRequests: state.followRequests,
            sentRequests: state.sentRequests,
            recapNotified: state.recapNotified, together: state.together,
            shelfMarks: state.shelfMarks, dismissedRecs: state.dismissedRecs, growth: state.growth,
            invitedBy: state.invitedBy, memorySeen: state.memorySeen,
            stickerPacks: state.stickerPacks, backdrop: state.backdrop, recentGifs: state.recentGifs,
            reports: state.reports,
            guideRead: state.guideRead.sorted(), guideHidden: state.guideHidden,
            subscription: state.subscription,
            activity: LossyList(state.activity), activitySeen: state.activitySeen,
            searchHistory: state.searchHistory,
            stories: state.stories, seenStories: state.seenStories, closeFriends: state.closeFriends,
            highlights: state.highlights, callLog: state.callLog
        )
    }

    /// Вернуть сохранённое состояние после входа. Свои записи и
    /// комментарии получают текущее имя и сведения из профиля.
    func restore(_ snapshot: Snapshot) {
        nextId = max(nextId, snapshot.nextId)
        var s = state
        s.posts = snapshot.posts
        s.chats = snapshot.chats.map { chat in
            var c = chat
            c.typing = false
            c.typingIds = []
            return c
        }
        // Демо-группы из старого сохранения получают витрину и сведения из новых демо-данных
        s.groups = snapshot.groups.map { saved in
            guard let demo = Demo.groups.first(where: { $0.id == saved.id }), !saved.mine, saved.ownerId == 0, demo.ownerId != 0 else { return saved }
            var g = saved
            g.ownerId = demo.ownerId
            g.info = demo.info
            g.shop = demo.shop
            g.accent = demo.accent
            g.backdrop = demo.backdrop
            return g
        }
        s.prisms = snapshot.prisms
        // following — друзья; старые «одобренные подписчики» — тоже друзья
        let friends = snapshot.following.union(snapshot.approvedFollowers ?? [])
        s.following = friends.subtracting([s.me.id])
        // Старый файл: только общий выключатель уведомлений
        s.notifications = snapshot.notify ?? Notifications(all: snapshot.notifications)
        s.privacy = snapshot.privacy ?? Privacy()
        s.blocked = (snapshot.blocked ?? []).subtracting([s.me.id])
        s.tracks = snapshot.tracks ?? []
        s.playlists = snapshot.playlists ?? []
        s.reportedComments = snapshot.reportedComments ?? []
        s.stickers = snapshot.stickers ?? []
        s.profilePins = snapshot.profilePins ?? []
        s.checkInTemplates = snapshot.checkInTemplates ?? []
        let meId = s.me.id
        s.followRequests = (snapshot.followRequests ?? []).filter { !friends.contains($0) && $0 != meId }
        s.sentRequests = (snapshot.sentRequests ?? []).subtracting(friends).subtracting([meId])
        s.recapNotified = snapshot.recapNotified ?? []
        let demo = s.together
        s.together = snapshot.together.map { $0.withNewDemo(demo) } ?? demo
        let savedMarks = snapshot.shelfMarks ?? []
        s.shelfMarks = savedMarks + s.shelfMarks.filter { m in !savedMarks.contains { $0.key == m.key } }
        s.dismissedRecs = snapshot.dismissedRecs ?? []
        // Повторное восстановление (аккаунт перечитан) не удваивает журнал
        let savedGrowth = snapshot.growth ?? []
        let merged = savedGrowth + s.growth.filter { !savedGrowth.contains($0) }
        s.growth = Array(merged.stableSorted { $0.at < $1.at }.suffix(growthLogLimit))
        s.invitedBy = snapshot.invitedBy ?? s.invitedBy
        s.memorySeen = snapshot.memorySeen ?? ""
        s.stickerPacks = snapshot.stickerPacks ?? []
        s.backdrop = snapshot.backdrop ?? Backdrop.default
        s.recentGifs = snapshot.recentGifs ?? []
        s.reports = snapshot.reports ?? []
        // Отметки убранных тем не считаются
        s.guideRead = Set(snapshot.guideRead ?? []).intersection(Guide.keys)
        s.guideHidden = snapshot.guideHidden ?? false
        s.subscription = snapshot.subscription ?? MemorySubscription()
        s.activity = snapshot.activity?.items ?? []
        s.activitySeen = snapshot.activitySeen ?? Date(timeIntervalSince1970: 0)
        s.searchHistory = snapshot.searchHistory ?? []
        s.stories = snapshot.stories ?? []
        s.seenStories = snapshot.seenStories ?? []
        s.closeFriends = snapshot.closeFriends ?? []
        s.highlights = snapshot.highlights ?? []
        s.callLog = snapshot.callLog ?? []
        s.applyMe(s.me)
        state = s
        refreshPlan()
    }

    private static func toggled(_ post: Post, _ postId: Int64) -> Post {
        guard post.id == postId else { return post }
        var p = post
        p.likes += p.liked ? -1 : 1
        p.liked.toggle()
        return p
    }

    /// Переключить «нравится» у комментария с номером commentId.
    private static func flipLike(_ comment: Comment, _ commentId: Int64) -> Comment {
        guard comment.id == commentId else { return comment }
        var c = comment
        c.likes = max(0, c.likes + (c.liked ? -1 : 1))
        c.liked.toggle()
        return c
    }

    private static let timeFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        return f
    }()

    private static func now() -> String { timeFormatter.string(from: Date()) }
}
