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

/// Разделы профиля за счётчиками: записи, подписчики, подписки, группы, сохранённое.
enum ProfileSection: String, CaseIterable {
    case posts = "Записи"
    case followers = "Подписчики"
    case following = "Подписки"
    case groups = "Группы"
    case saved = "Сохранённое"
    case music = "Моя музыка"
    case stickers = "Студия"
    case recap = "Итоги"
    case requests = "Заявки на подписку"

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

    var title: String { t(rawValue) }
}

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
    /// Раздел чужого профиля (записи, подписчики, подписки) поверх его страницы.
    var personSection: ProfileSection? = nil
    /// На кого подписан человек: их записи — во вкладке «Истории».
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
    /// Заявки на подписку к вам (закрытый профиль) — новые первыми.
    var followRequests: [Int] = []
    /// Кого вы одобрили: подписчики сверх демо-списка.
    var approvedFollowers: Set<Int> = []
    /// Ваши заявки к закрытым профилям, пока их не приняли.
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

    var openPerson: User? { openPersonId.flatMap { Demo.person($0) } }
    var openGroup: MemoryGroup? { groups.first { $0.id == openGroupId } }
    var myGroups: [MemoryGroup] { groups.filter { $0.joined } }
    func postsOf(_ userId: Int) -> [Post] { posts.filter { $0.author.id == userId } }
    var openChat: Chat? { chats.first { $0.id == openChatId } }
    /// Счётчик на вкладке «Чаты»: переписки без звука не считаются.
    var unreadTotal: Int { chats.filter { !$0.muted }.reduce(0) { $0 + $1.unread } }
    /// Список чатов: закреплённые сверху, остальные — по последнему сообщению.
    var chatList: [Chat] { chats.filter(\.onTop) + chats.filter { !$0.onTop } }
    /// Лента без записей тех, кто в чёрном списке, и без записей закрытых
    /// профилей, на которые вы не подписаны.
    var feed: [Post] { posts.filter { !blocked.contains($0.author.id) && canSee($0.author) } }
    /// Записи человека видны: профиль открыт, вы подписаны или это вы.
    /// Закрытость берётся из демо-данных: в сохранённых записях автор может быть старым.
    func canSee(_ user: User) -> Bool {
        let closed = Demo.person(user.id)?.closed ?? user.closed
        return !closed || following.contains(user.id) || user.id == me.id
    }
    /// Люди, приславшие заявку на подписку.
    var requestPeople: [User] { followRequests.compactMap { Demo.person($0) } }
    var blockedPeople: [User] { Demo.people.filter { blocked.contains($0.id) } }
    func isBlocked(_ userId: Int) -> Bool { blocked.contains(userId) }
    /// Подписчики другого человека. Сервера нет, поэтому видны только
    /// знакомые по демо-данным — и вы, если подписаны.
    func followersOf(_ user: User) -> [User] {
        let you: [User] = following.contains(user.id) ? [me] : []
        let known = Demo.people.filter { person in
            person.id != user.id && !blocked.contains(person.id) && (person.id + user.id) % 2 == 1
        }
        return you + known
    }
    /// На кого подписан другой человек — тоже из знакомых; вы — если он подписан на вас.
    func followingOf(_ user: User) -> [User] {
        let you: [User] = Demo.followers.contains(user.id) ? [me] : []
        let known = Demo.people.filter { person in
            person.id != user.id && !blocked.contains(person.id) && (person.id + user.id) % 2 == 0
        }
        return you + known
    }
    var myPosts: [Post] { posts.filter { $0.author.id == me.id } }
    /// «Истории»: свои записи и записи тех, на кого подписан, — новые сверху.
    var stories: [Post] {
        posts.filter { (following.contains($0.author.id) || $0.author.id == me.id) && !blocked.contains($0.author.id) }
    }
    var followedPeople: [User] { Demo.people.filter { following.contains($0.id) } }
    /// Кто подписан на вас: демо-люди и те, чьи заявки вы одобрили.
    var followers: [User] { Demo.people.filter { Demo.followers.contains($0.id) || approvedFollowers.contains($0.id) } }
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
    var followRequests: [Int]? = nil
    var approvedFollowers: Set<Int>? = nil
    var sentRequests: Set<Int>? = nil
    var recapNotified: Set<String>? = nil
    var together: TogetherState? = nil

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

    @ObservationIgnored private var nextId: Int64 = 10_000
    @ObservationIgnored private var replyIndex = 0
    /// Часы — для времени своих записей и комментариев; в тестах подменяются.
    @ObservationIgnored private let clock: () -> Date
    /// Демо-собеседник отвечает на сообщения; в тестах можно выключить.
    @ObservationIgnored private let demoReplies: Bool

    init(clock: @escaping () -> Date = { Date() }, demoReplies: Bool = true) {
        self.clock = clock
        self.demoReplies = demoReplies
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
        state.tab = tab
    }

    /// Раздел профиля: нажатие на «записи», «подписчики», «подписки», «группы» или «Сохранённое».
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
        state.settingsPage = page
    }

    /// «Назад» в настройках: со страницы — в список, из списка — в профиль.
    func settingsBack() {
        state.settingsPage = state.settingsPage == .main ? nil : .main
    }

    /// Записи, подписчики или подписки другого человека — нажатием на счётчик на его странице.
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
        if state.viewing != nil { closeMedia(); return true }
        if state.commentsPostId != nil || state.commentsPrismId != nil { closeComments(); return true }
        if state.openChatId != nil { closeChat(); return true }
        if state.personSection != nil && state.openPersonId != nil { closePersonSection(); return true }
        if state.openPersonId != nil { closePerson(); return true }
        if state.groupSettingsOpen { closeGroupSettings(); return true }
        if state.openGroupId != nil { closeGroup(); return true }
        if state.settingsPage != nil { settingsBack(); return true }
        if state.profileSection != nil { closeProfileSection(); return true }
        if state.tab != .feed { selectTab(.feed); return true }
        return false
    }

    // MARK: - Лента

    func toggleLike(_ postId: Int64) {
        state.posts = state.posts.map { Self.toggled($0, postId) }
    }

    /// Новая запись: текст, фото и видео. Пустую (без текста и вложений) не публикуем.
    func publish(_ text: String, media: [Attachment] = [], checkIn: CheckIn? = nil) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty && media.isEmpty && checkIn == nil { return }
        let post = Post(
            id: newId(), author: state.me, text: trimmed, minutesAgo: 0,
            media: Array(media.prefix(Self.maxMedia)), createdAt: clock(), checkIn: checkIn
        )
        state.posts.insert(post, at: 0)
        // Чек-ин сам засчитывается в общие цели «Вместе»
        if let checkIn { state.together = state.together.afterCheckIn(checkIn) }
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
        let trimmed = String(text.trimmingCharacters(in: .whitespacesAndNewlines).prefix(1000))
        if trimmed.isEmpty && sticker == nil { return }
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

    /// Подписаться или отписаться: записи человека появятся в «Историях» или
    /// уйдут оттуда. На закрытый профиль уходит заявка; повторное нажатие —
    /// отозвать её. В демо заявку принимают через несколько секунд.
    func toggleFollow(_ userId: Int) {
        if userId == state.me.id { return }
        if let person = Demo.person(userId), person.closed, !state.following.contains(userId) {
            if state.sentRequests.contains(userId) {
                acceptTasks.removeValue(forKey: userId)?.cancel()
                state.sentRequests.remove(userId)
                return
            }
            state.sentRequests.insert(userId)
            scheduleAcceptance(userId)
            return
        }
        if state.following.contains(userId) {
            state.following.remove(userId)
        } else {
            state.following.insert(userId)
        }
    }

    /// Таймеры демо-ответа на заявки: один на человека.
    @ObservationIgnored private var acceptTasks: [Int: Task<Void, Never>] = [:]

    /// В демо закрытый профиль принимает заявку через 12–30 секунд.
    private func scheduleAcceptance(_ userId: Int) {
        guard demoReplies else { return }
        acceptTasks.removeValue(forKey: userId)?.cancel()
        acceptTasks[userId] = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: UInt64.random(in: 12_000_000_000...30_000_000_000))
            guard !Task.isCancelled, let self else { return }
            self.acceptTasks[userId] = nil
            self.acceptSentRequest(userId)
        }
    }

    /// Закрытый профиль принял вашу заявку: вы подписаны, записи видны.
    func acceptSentRequest(_ userId: Int) {
        guard state.sentRequests.contains(userId) else { return }
        state.sentRequests.remove(userId)
        state.following.insert(userId)
        if let person = Demo.person(userId) { notify(Nudges.requestAccepted(person)) }
    }

    /// Одобрить заявку: человек становится подписчиком.
    func approveRequest(_ userId: Int) {
        state.followRequests.removeAll { $0 == userId }
        state.approvedFollowers.insert(userId)
    }

    /// Отклонить заявку: человек просто останется без доступа.
    func declineRequest(_ userId: Int) {
        state.followRequests.removeAll { $0 == userId }
    }

    /// Новая заявка к вам — когда профиль закрыт.
    func receiveFollowRequest(_ userId: Int) {
        guard let person = Demo.person(userId), state.privacy.closedProfile,
              !state.followRequests.contains(userId), !state.blocked.contains(userId),
              !state.followers.contains(where: { $0.id == userId }) else { return }
        state.followRequests.insert(userId, at: 0)
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
    func send(_ chatId: Int, _ text: String, attachments: [Attachment] = [], replyTo: ReplyRef? = nil) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty && attachments.isEmpty { return }
        append(chatId, Message(
            id: newId(), fromMe: true, text: trimmed, time: Self.now(),
            attachments: Array(attachments.prefix(Self.maxMedia)),
            replyTo: replyTo
        ))
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
            // Открытая переписка — ответ и так на экране
            if let chat = self.state.chats.first(where: { $0.id == chatId }), !chat.muted, self.state.openChatId != chatId {
                self.notify(Nudges.message(chat.peer, reply, chatId: chatId))
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
        append(toChatId, Message(
            id: newId(), fromMe: true, text: original.text, time: Self.now(),
            attachments: original.attachments, forwardedFrom: author
        ))
        openChat(toChatId)
    }

    /// Какие сообщения удалены «у всех» — для будущей отправки на сервер.
    @ObservationIgnored private(set) var deletedForEveryone: Set<Int64> = []

    private func append(_ chatId: Int, _ message: Message) {
        guard var chat = state.chats.first(where: { $0.id == chatId }) else { return }
        let reading = state.openChatId == chatId
        chat.messages.append(message)
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

    /// Закрепить свою запись в витрине профиля (до трёх) или открепить.
    func toggleProfilePin(_ postId: Int64) {
        if state.profilePins.contains(postId) {
            state.profilePins.removeAll { $0 == postId }
        } else if state.posts.contains(where: { $0.id == postId && $0.author.id == state.me.id }) {
            state.profilePins = Array(([postId] + state.profilePins).prefix(Self.maxPins))
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
    func postInGroup(_ groupId: Int, _ text: String, media: [Attachment] = []) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty && media.isEmpty { return }
        let me = state.me
        state.groups = state.groups.map { group in
            var g = group
            if g.id == groupId && g.joined && (!g.onlyOwnerPosts || g.mine) {
                g.posts.insert(Post(
                    id: newId(), author: me, text: trimmed, minutesAgo: 0,
                    media: Array(media.prefix(Self.maxMedia)), createdAt: clock()
                ), at: 0)
            }
            return g
        }
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
        lifeTask?.cancel()
        lifeTask = nil
        acceptTasks.values.forEach { $0.cancel() }
        acceptTasks = [:]
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

    /// Открыли профиль — ждущие заявки одобряются сами: подписаться может любой.
    func setPrivacy(_ privacy: Privacy) {
        if !privacy.closedProfile && !state.followRequests.isEmpty {
            state.approvedFollowers.formUnion(state.followRequests)
            state.followRequests = []
        }
        state.privacy = privacy
    }

    /// В чёрный список: записи, комментарии и сообщения человека больше не
    /// видны, подписка на него снимается. Себя заблокировать нельзя.
    func block(_ userId: Int) {
        if userId == state.me.id { return }
        state.blocked.insert(userId)
        state.following.remove(userId)
    }

    func unblock(_ userId: Int) {
        state.blocked.remove(userId)
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
            openPerson(Int(nudge.targetId))
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

    func setRecapBack(_ back: Int) { state.recapBack = max(0, back) }

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
    /// приходят люди, вашим записям ставят «нравится», при закрытом
    /// профиле приходят заявки. Каждое событие — с персональным уведомлением.
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
        let friends = state.followedPeople.filter { !state.blocked.contains($0.id) }
        let joined = state.groups.filter { $0.joined && !$0.muted }
        let mine = state.myPosts
        let requesters = Demo.people.filter { p in
            !state.blocked.contains(p.id) && !state.followRequests.contains(p.id) && !state.followers.contains { $0.id == p.id }
        }
        var options: [Int] = []
        if !friends.isEmpty { options += [0, 0, 0, 0] }
        if !joined.isEmpty { options += [1, 1] }
        if !mine.isEmpty { options += [2, 2] }
        if state.privacy.closedProfile && !requesters.isEmpty { options.append(3) }
        guard let pick = options.randomElement(using: &rng) else { return }
        switch pick {
        case 0:
            friendCheckIn(friends.randomElement(using: &rng)!, using: &rng)
        case 1:
            let group = joined.randomElement(using: &rng)!
            let name = Demo.newcomers.randomElement(using: &rng)!
            let person = User(id: 500 + Int.random(in: 0..<1000, using: &rng), name: name,
                              handle: name.lowercased().replacingOccurrences(of: " ", with: "."))
            state.groups = state.groups.map { g in
                var g = g
                if g.id == group.id { g.members += 1 }
                return g
            }
            notify(Nudges.groupMember(group, person))
        case 2:
            let post = mine.randomElement(using: &rng)!
            let fans = Demo.people.filter { !state.blocked.contains($0.id) }
            let fan = fans.randomElement(using: &rng)!
            state.posts = state.posts.map { p in
                var p = p
                if p.id == post.id { p.likes += 1 }
                return p
            }
            notify(Nudges.liked(fan, post))
        default:
            receiveFollowRequest(requesters.randomElement(using: &rng)!.id)
        }
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
        let find = MissionFind(id: newId(), authorId: meId, note: String(note.trimmingCharacters(in: .whitespacesAndNewlines).prefix(200)),
                               at: at, photo: photo, createdAt: clock())
        together { $0.addFind(missionId, find) }
    }

    // MARK: Клады

    @discardableResult
    func hideCache(title: String, hint: String, difficulty: Int, size: String, at: LatLon) -> Bool {
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
            approvedFollowers: state.approvedFollowers, sentRequests: state.sentRequests,
            recapNotified: state.recapNotified, together: state.together
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
            return c
        }
        s.groups = snapshot.groups
        s.prisms = snapshot.prisms
        s.following = snapshot.following.subtracting([s.me.id])
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
        s.followRequests = snapshot.followRequests ?? []
        s.approvedFollowers = snapshot.approvedFollowers ?? []
        s.sentRequests = snapshot.sentRequests ?? []
        s.recapNotified = snapshot.recapNotified ?? []
        s.together = snapshot.together ?? s.together
        s.applyMe(s.me)
        state = s
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
