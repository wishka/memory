import Foundation
import Observation

/// Вкладки нижней панели — в порядке слева направо.
enum AppTab: String, CaseIterable, Identifiable {
    case feed = "Лента"
    case stories = "Истории"
    case prism = "PRISM"
    case chats = "Чаты"
    case groups = "Группы"
    case profile = "Профиль"

    var id: String { rawValue }
    var title: String { rawValue }
}

/// Разделы профиля за счётчиками: записи, подписчики, подписки, группы, сохранённое.
enum ProfileSection: String, CaseIterable {
    case posts = "Записи"
    case followers = "Подписчики"
    case following = "Подписки"
    case groups = "Группы"
    case saved = "Сохранённое"

    var title: String { rawValue }
}

/// Всё, что видно на экране. Те же поля и правила, что UiState на Android.
struct UiState: Equatable {
    var tab: AppTab = .feed
    var me: User = Demo.me
    var posts: [Post] = Demo.posts
    var chats: [Chat] = Demo.chats
    /// Открытая переписка поверх вкладки «Чаты»; nil — список чатов.
    var openChatId: Int? = nil
    var notifications: Bool = true
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

    var openPerson: User? { openPersonId.flatMap { Demo.person($0) } }
    var openGroup: MemoryGroup? { groups.first { $0.id == openGroupId } }
    var myGroups: [MemoryGroup] { groups.filter { $0.joined } }
    func postsOf(_ userId: Int) -> [Post] { posts.filter { $0.author.id == userId } }
    var openChat: Chat? { chats.first { $0.id == openChatId } }
    var unreadTotal: Int { chats.reduce(0) { $0 + $1.unread } }
    var myPosts: [Post] { posts.filter { $0.author.id == me.id } }
    /// «Истории»: записи тех, на кого подписан, — новые сверху, как и в ленте.
    var stories: [Post] { posts.filter { following.contains($0.author.id) } }
    var followedPeople: [User] { Demo.people.filter { following.contains($0.id) } }
    /// Кто подписан на вас (пока — демо-люди).
    var followers: [User] { Demo.people.filter { Demo.followers.contains($0.id) } }
    /// «Сохранённое» в профиле: PRISM в закладках.
    var savedPrisms: [Prism] { prisms.filter { $0.saved } }
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
        state.openPersonId = nil
        state.returnToChatId = nil
        // Нажатие на «Профиль» внизу всегда ведёт на сам профиль
        state.profileSection = nil
        state.tab = tab
    }

    /// Раздел профиля: нажатие на «записи», «подписчики», «подписки», «группы» или «Сохранённое».
    func openProfileSection(_ section: ProfileSection) {
        state.tab = .profile
        state.openPersonId = nil
        state.profileSection = section
    }

    func closeProfileSection() {
        state.profileSection = nil
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
        state.openPersonId = userId
    }

    func closePerson() {
        state.openPersonId = nil
        if let chatId = state.returnToChatId {
            state.openChatId = chatId
            state.returnToChatId = nil
        }
    }

    /// «Назад»: переписка → страница человека → группа → лента.
    /// true — обработано внутри приложения.
    @discardableResult
    func back() -> Bool {
        if state.viewing != nil { closeMedia(); return true }
        if state.commentsPostId != nil || state.commentsPrismId != nil { closeComments(); return true }
        if state.openChatId != nil { closeChat(); return true }
        if state.openPersonId != nil { closePerson(); return true }
        if state.openGroupId != nil { closeGroup(); return true }
        if state.profileSection != nil { closeProfileSection(); return true }
        if state.tab != .feed { selectTab(.feed); return true }
        return false
    }

    // MARK: - Лента

    func toggleLike(_ postId: Int64) {
        state.posts = state.posts.map { Self.toggled($0, postId) }
    }

    /// Новая запись: текст, фото и видео. Пустую (без текста и вложений) не публикуем.
    func publish(_ text: String, media: [Attachment] = []) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty && media.isEmpty { return }
        let post = Post(
            id: newId(), author: state.me, text: trimmed, minutesAgo: 0,
            media: Array(media.prefix(Self.maxMedia)), createdAt: clock()
        )
        state.posts.insert(post, at: 0)
    }

    /// Комментарий к записи в ленте или в группе.
    func addComment(_ postId: Int64, _ text: String) {
        let trimmed = String(text.trimmingCharacters(in: .whitespacesAndNewlines).prefix(1000))
        if trimmed.isEmpty { return }
        let comment = Comment(id: newId(), author: state.me, text: trimmed, createdAt: clock())
        updatePosts(postId) { p in
            p.comments += 1
            p.commentList.append(comment)
        }
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

    /// Подписаться или отписаться: записи человека появятся в «Историях» или уйдут оттуда.
    func toggleFollow(_ userId: Int) {
        if userId == state.me.id { return }
        if state.following.contains(userId) {
            state.following.remove(userId)
        } else {
            state.following.insert(userId)
        }
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

    func addPrismComment(_ prismId: Int64, _ text: String) {
        let trimmed = String(text.trimmingCharacters(in: .whitespacesAndNewlines).prefix(1000))
        if trimmed.isEmpty { return }
        let comment = Comment(id: newId(), author: state.me, text: trimmed, createdAt: clock())
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

    // MARK: - Группы

    /// Группа поверх вкладки; из раздела профиля «назад» вернёт в него.
    func openGroup(_ groupId: Int) {
        if !(state.tab == .profile && state.profileSection != nil) {
            state.tab = .groups
        }
        state.openGroupId = groupId
        state.openPersonId = nil
    }

    func closeGroup() {
        state.openGroupId = nil
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

    /// Новая группа: создатель сразу в ней. Ошибка — текст для экрана, nil — успех.
    @discardableResult
    func createGroup(name: String, description: String, topic: String) -> String? {
        let title = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if title.count < 3 { return "Название — от 3 символов" }
        if title.count > 40 { return "Название — не длиннее 40 символов" }
        if state.groups.contains(where: { $0.name.lowercased() == title.lowercased() }) {
            return "Группа «\(title)» уже есть"
        }
        let id = (state.groups.map(\.id).max() ?? 0) + 1
        let cleanTopic = topic.trimmingCharacters(in: .whitespaces)
        let group = MemoryGroup(
            id: id, name: title,
            description: description.trimmingCharacters(in: .whitespacesAndNewlines),
            topic: cleanTopic.isEmpty ? (groupTopics.last ?? "Другое") : cleanTopic,
            members: 1, seed: 100 + id, joined: true, mine: true
        )
        state.groups.insert(group, at: 0)
        openGroup(id)
        return nil
    }

    func deleteGroup(_ groupId: Int) {
        state.groups.removeAll { $0.id == groupId && $0.mine }
        state.openGroupId = nil
    }

    /// Запись в группе — только для участников.
    func postInGroup(_ groupId: Int, _ text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return }
        let me = state.me
        state.groups = state.groups.map { group in
            var g = group
            if g.id == groupId && g.joined {
                g.posts.insert(Post(id: newId(), author: me, text: trimmed, minutesAgo: 0, createdAt: clock()), at: 0)
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
        applyMe(me)
    }

    /// После выхода состояние сбрасывается: следующий вошедший не увидит чужие черновики и переписку.
    func reset() {
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
        applyMe(me)
    }

    func setNotifications(_ enabled: Bool) {
        state.notifications = enabled
    }

    /// Свои записи и комментарии показывают новое имя.
    private func applyMe(_ me: User) {
        state.applyMe(me)
    }

    // MARK: - Сохранение

    /// То, что сохраняется на телефоне между запусками (см. StateStore).
    func snapshot() -> Snapshot {
        Snapshot(
            posts: state.posts, chats: state.chats, groups: state.groups, prisms: state.prisms,
            following: state.following, notifications: state.notifications, nextId: nextId
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
        s.notifications = snapshot.notifications
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

    private static let timeFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        return f
    }()

    private static func now() -> String { timeFormatter.string(from: Date()) }
}
