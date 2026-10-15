import Foundation

/// Событие живого канала (WebSocket /v1/realtime, docs/REALTIME.md): новое
/// сообщение, «печатает», «прочитано», состав группы. Один JSON на кадр:
/// {"type": "...", "chat": 12, ...}. То же — RtEvent в Realtime.kt.
enum RtEvent {
    /// Новое сообщение; message — JSON в формате API (ApiCodec).
    case newMessage(chatId: Int, message: JSONObject)
    case typing(chatId: Int, userId: Int, on: Bool)
    /// userId прочитал всё, что сервер принял до upTo (мс) включительно.
    case read(chatId: Int, userId: Int, upTo: Int64)
    /// Сменился состав или название группы.
    case groupChanged(chatId: Int, title: String, memberIds: [Int], adminIds: [Int])
    /// Подписка: state — "request" (заявка ко мне), "follow" (подписались), "accepted" (мою заявку приняли).
    case follow(userId: Int, state: String)
    /// Комментарий к моей записи.
    case newComment(postId: Int64, comment: JSONObject)

    var chatId: Int {
        switch self {
        case .newMessage(let chatId, _): return chatId
        case .typing(let chatId, _, _): return chatId
        case .read(let chatId, _, _): return chatId
        case .groupChanged(let chatId, _, _, _): return chatId
        case .follow, .newComment: return 0
        }
    }

    func json() -> JSONObject {
        var o: JSONObject = [:]
        o["chat"] = chatId
        switch self {
        case .newMessage(_, let message):
            o["type"] = "message"
            o["message"] = message
        case .typing(_, let userId, let on):
            o["type"] = "typing"
            o["user"] = userId
            o["on"] = on
        case .read(_, let userId, let upTo):
            o["type"] = "read"
            o["user"] = userId
            o["upTo"] = upTo
        case .groupChanged(_, let title, let memberIds, let adminIds):
            o["type"] = "group"
            o["title"] = title
            o["members"] = memberIds
            o["admins"] = adminIds
        case .follow(let userId, let state):
            o.removeValue(forKey: "chat")
            o["type"] = "follow"
            o["user"] = userId
            o["state"] = state
        case .newComment(let postId, let comment):
            o.removeValue(forKey: "chat")
            o["type"] = "comment"
            o["post"] = postId
            o["comment"] = comment
        }
        return o
    }

    static func of(_ o: JSONObject) -> RtEvent? {
        let chat = Json.int(o, "chat")
        switch Json.string(o, "type") {
        case "message":
            guard let message = Json.object(o, "message") else { return nil }
            return .newMessage(chatId: chat, message: message)
        case "typing":
            return .typing(chatId: chat, userId: Json.int(o, "user"), on: Json.bool(o, "on"))
        case "read":
            return .read(chatId: chat, userId: Json.int(o, "user"), upTo: Json.long(o, "upTo"))
        case "group":
            let members = Json.ints(o, "members")
            let admins = Json.ints(o, "admins")
            return .groupChanged(chatId: chat, title: Json.string(o, "title"), memberIds: members, adminIds: admins)
        case "follow":
            return .follow(userId: Json.int(o, "user"), state: Json.string(o, "state"))
        case "comment":
            guard let comment = Json.object(o, "comment") else { return nil }
            return .newComment(postId: Json.long(o, "post"), comment: comment)
        default:
            return nil
        }
    }

    static func parse(_ text: String) -> RtEvent? {
        guard let o = Json.parse(text) else { return nil }
        return of(o)
    }
}

extension RtEvent: Equatable {
    static func == (a: RtEvent, b: RtEvent) -> Bool {
        switch (a, b) {
        case let (.newMessage(c1, m1), .newMessage(c2, m2)):
            return c1 == c2 && NSDictionary(dictionary: m1).isEqual(to: m2)
        case let (.typing(c1, u1, on1), .typing(c2, u2, on2)):
            return c1 == c2 && u1 == u2 && on1 == on2
        case let (.read(c1, u1, t1), .read(c2, u2, t2)):
            return c1 == c2 && u1 == u2 && t1 == t2
        case let (.groupChanged(c1, t1, m1, a1), .groupChanged(c2, t2, m2, a2)):
            return c1 == c2 && t1 == t2 && m1 == m2 && a1 == a2
        case let (.follow(u1, s1), .follow(u2, s2)):
            return u1 == u2 && s1 == s2
        case let (.newComment(p1, j1), .newComment(p2, j2)):
            return p1 == p2 && NSDictionary(dictionary: j1).isEqual(to: j2)
        default:
            return false
        }
    }

    var isTyping: Bool {
        if case .typing = self { return true }
        return false
    }
}

/// Живой канал: сервер шлёт события сам, без опроса. Клиент шлёт только
/// «печатает» и «прочитано». Обрыв — переподключение с паузой (Backoff).
/// На iPhone — URLSessionRealtime (AppleNet.swift), в демо — DemoRealtime.
protocol Realtime: AnyObject {
    var connected: Bool { get }
    func connect(_ onEvent: @escaping (RtEvent) -> Void)
    func send(_ event: RtEvent)
    func close()
    /// Подключиться заново сейчас (после входа — с новым токеном).
    func reconnect()
}

extension Realtime {
    /// По умолчанию — ничего: демо-каналу переподключаться не к чему.
    func reconnect() {}
}

/// Живой канал демо-сервера: события, которые «пришли бы» с сервера,
/// копятся с временем и выдаются tick(now) — в приложении раз в секунду,
/// в тестах — когда нужно. Так демо-друзья читают и печатают.
final class DemoRealtime: Realtime {
    private let clock: () -> Int64
    private let lock = NSLock()
    private var queue: [(at: Int64, seq: Int, event: RtEvent)] = []
    private var seq = 0
    private var listener: ((RtEvent) -> Void)?
    private var _sent: [RtEvent] = []

    init(clock: @escaping () -> Int64) {
        self.clock = clock
    }

    /// Что клиент отправил (для тестов).
    var sent: [RtEvent] {
        lock.lock()
        defer { lock.unlock() }
        return _sent
    }

    var connected: Bool {
        lock.lock()
        defer { lock.unlock() }
        return listener != nil
    }

    func connect(_ onEvent: @escaping (RtEvent) -> Void) {
        lock.lock()
        listener = onEvent
        lock.unlock()
    }

    func send(_ event: RtEvent) {
        lock.lock()
        _sent.append(event)
        lock.unlock()
    }

    func close() {
        lock.lock()
        listener = nil
        lock.unlock()
    }

    /// Сервер «пришлёт» событие через afterMs.
    func schedule(_ event: RtEvent, afterMs: Int64) {
        let at: Int64 = clock() + afterMs
        lock.lock()
        seq += 1
        queue.append((at: at, seq: seq, event: event))
        lock.unlock()
    }

    /// Выдать созревшие события (по времени, при равном — по порядку). Ответ — сколько выдали.
    @discardableResult
    func tick() -> Int { tick(clock()) }

    @discardableResult
    func tick(_ now: Int64) -> Int {
        lock.lock()
        let due = queue.filter { $0.at <= now }.sorted { a, b in
            if a.at != b.at { return a.at < b.at }
            return a.seq < b.seq
        }
        queue.removeAll { $0.at <= now }
        let l = listener
        lock.unlock()
        // Слушатель — без замка: он может сам слать в канал
        guard let l else { return 0 }
        for item in due { l(item.event) }
        return due.count
    }

    var pending: Int {
        lock.lock()
        defer { lock.unlock() }
        return queue.count
    }
}
