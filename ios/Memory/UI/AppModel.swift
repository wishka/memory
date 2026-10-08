import Foundation
import Observation

/// Вкладки нижней панели — в порядке слева направо.
enum AppTab: String, CaseIterable, Identifiable {
    case feed = "Лента"
    case stories = "Истории"
    case reels = "Reels"
    case chats = "Чаты"
    case groups = "Группы"
    case profile = "Профиль"

    var id: String { rawValue }
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
    var reels: [Reel] = Demo.reels
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

    private var nextId: Int64 = 10_000
    private var replyIndex = 0

    private func newId() -> Int64 {
        nextId += 1
        return nextId
    }

    // MARK: - Навигация

    func selectTab(_ tab: AppTab) {
        state.openChatId = tab == .chats ? state.openChatId : nil
        state.openGroupId = tab == .groups ? state.openGroupId : nil
        state.openPersonId = nil
        state.tab = tab
    }

    /// Страница автора. Своё имя ведёт в «Профиль».
    func openPerson(_ userId: Int) {
        if userId == state.me.id {
            selectTab(.profile)
            return
        }
        state.openPersonId = userId
    }

    func closePerson() {
        state.openPersonId = nil
    }

    /// «Назад»: переписка → страница человека → группа → лента.
    /// true — обработано внутри приложения.
    @discardableResult
    func back() -> Bool {
        if state.openChatId != nil { closeChat(); return true }
        if state.openPersonId != nil { closePerson(); return true }
        if state.openGroupId != nil { closeGroup(); return true }
        if state.tab != .feed { selectTab(.feed); return true }
        return false
    }

    // MARK: - Лента

    func toggleLike(_ postId: Int64) {
        state.posts = state.posts.map { Self.toggled($0, postId) }
    }

    func publish(_ text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return }
        state.posts.insert(Post(id: newId(), author: state.me, text: trimmed, minutesAgo: 0), at: 0)
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
    func send(_ chatId: Int, _ text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return }
        append(chatId, Message(id: newId(), fromMe: true, text: trimmed, time: Self.now()))
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

    // MARK: - Reels

    func toggleReelLike(_ reelId: Int64) {
        state.reels = state.reels.map { reel in
            var r = reel
            if r.id == reelId {
                r.likes += r.liked ? -1 : 1
                r.liked.toggle()
            }
            return r
        }
    }

    // MARK: - Группы

    func openGroup(_ groupId: Int) {
        state.tab = .groups
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
            topic: cleanTopic.isEmpty ? groupTopics[0] : cleanTopic,
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
                g.posts.insert(Post(id: newId(), author: me, text: trimmed, minutesAgo: 0), at: 0)
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

    /// Свои записи в ленте показывают новое имя.
    private func applyMe(_ me: User) {
        state.me = me
        state.posts = state.posts.map { post in
            var p = post
            if p.author.id == me.id { p.author = me }
            return p
        }
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
