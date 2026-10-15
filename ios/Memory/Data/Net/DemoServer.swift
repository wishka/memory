import Foundation

/// Сервер Memory прямо на телефоне — пока настоящего нет. Отвечает по тому
/// же контракту (docs/openapi.yaml), что и будущий: лента страницами,
/// публикация с Idempotency-Key, «нравится», переписка, загрузки tus.
///
/// Умеет притворяться плохой сетью: online = false — «нет сети»,
/// latencyMs — задержка, failNext — столько следующих запросов ответят 503,
/// dropAfterBytes — оборвать загрузку на середине куска (проверка докачки).
///
/// Загруженное хранится в dir (на телефоне — папка в кэше приложения),
/// без dir — в памяти (тесты). То же — в DemoServer.kt.
final class DemoServer: Transport {
    static let maxUpload: Int64 = 2 * 1024 * 1024 * 1024

    private let lock = NSLock()
    private let me: () -> User
    private let clock: () -> Int64
    private let dir: URL?

    private var _online = true
    private var _latencyMs: Int64 = 0
    private var _failNext = 0
    private var _dropAfterBytes: Int64 = -1
    private var _requests = 0

    private var posts: [Post] = []
    private var byKey: [String: JSONObject] = [:]
    private var liked: Set<Int64> = []
    private var messages: [Int: [JSONObject]] = [:]
    private var uploads: [String: Blob] = [:]
    private var nextId: Int64 = 6_000_000
    private var uploadSeq = 0

    // Живой канал и демо-собеседники
    private var _realtime: DemoRealtime?
    private var _readDelayMs: Int64 = 2_500
    private var _accessTtlMs: Int64 = 15 * 60_000

    // Вход и устройства
    private final class Device {
        let id: String
        let name: String
        let platform: String
        var lastSeen: Int64
        var refresh: String
        var access: String
        var accessUntil: Int64

        init(id: String, name: String, platform: String, lastSeen: Int64, refresh: String = "", access: String = "", accessUntil: Int64 = 0) {
            self.id = id
            self.name = name
            self.platform = platform
            self.lastSeen = lastSeen
            self.refresh = refresh
            self.access = access
            self.accessUntil = accessUntil
        }
    }
    private var devices: [Device] = []
    private var tokenSeq = 0
    // Профиль (синхронизация между телефонами)
    private var profile: JSONObject?
    // Участники переписок: кто «прочитает» и кому дойдёт
    private var members: [Int: [Int]] = [:]
    private var groups: [Int: JSONObject] = [:]

    /// Загрузка tus: сколько ждём, что уже пришло.
    private final class Blob {
        let length: Int64
        let meta: String
        let file: URL?
        var memory = Data()
        var offset: Int64 = 0

        init(length: Int64, meta: String, file: URL?) {
            self.length = length
            self.meta = meta
            self.file = file
        }

        func append(_ bytes: Data) {
            if let file = self.file {
                if !FileManager.default.fileExists(atPath: file.path) {
                    FileManager.default.createFile(atPath: file.path, contents: nil)
                }
                if let handle = try? FileHandle(forWritingTo: file) {
                    _ = try? handle.seekToEnd()
                    try? handle.write(contentsOf: bytes)
                    try? handle.close()
                }
            } else {
                memory.append(bytes)
            }
            offset += Int64(bytes.count)
        }

        func bytes() -> Data {
            if let file = self.file { return (try? Data(contentsOf: file)) ?? Data() }
            return memory
        }
    }

    /// historySize — сколько старых записей друзей «на сервере» (для ленты страницами).
    init(me: @escaping () -> User, clock: @escaping () -> Int64, dir: URL? = nil, historySize: Int = 150) {
        self.me = me
        self.clock = clock
        self.dir = dir
        let day: Int64 = 24 * 60 * 60 * 1000
        let start: Int64 = clock() - 2 * day
        let people = Demo.people
        for i in 0..<max(0, historySize) {
            let author = people[i % people.count]
            let hours: Int64 = Int64(i) * 3 * 60 * 60 * 1000
            let minutes: Int64 = Int64(i % 5) * 60_000
            let at: Int64 = start - hours - minutes
            var p = Post(id: 5_000_000 + Int64(i), author: author, text: Self.history[i % Self.history.count], minutesAgo: 0)
            if i % 3 == 0 {
                let seed: Int = 7000 + i
                p.artSeed = seed
            }
            p.likes = (i * 7) % 40
            p.comments = (i * 3) % 9
            p.createdAt = Json.date(at)
            posts.append(p)
        }
    }

    // MARK: - Плохая сеть (для тестов и «Данных и сети»)

    var online: Bool {
        get { locked { _online } }
        set { locked { _online = newValue } }
    }

    var latencyMs: Int64 {
        get { locked { _latencyMs } }
        set { locked { _latencyMs = newValue } }
    }

    var failNext: Int {
        get { locked { _failNext } }
        set { locked { _failNext = newValue } }
    }

    var dropAfterBytes: Int64 {
        get { locked { _dropAfterBytes } }
        set { locked { _dropAfterBytes = newValue } }
    }

    /// Живой канал: сюда сервер «шлёт» события (прочитано, новые сообщения).
    var realtime: DemoRealtime? {
        get { locked { _realtime } }
        set { locked { _realtime = newValue } }
    }

    /// Через сколько демо-собеседник «прочитает» (мс).
    var readDelayMs: Int64 {
        get { locked { _readDelayMs } }
        set { locked { _readDelayMs = newValue } }
    }

    /// Сколько живёт токен доступа (мс) — в тестах можно «состарить».
    var accessTtlMs: Int64 {
        get { locked { _accessTtlMs } }
        set { locked { _accessTtlMs = newValue } }
    }

    /// Сколько запросов пришло — для тестов и экрана «Данные и сеть».
    var requests: Int { locked { _requests } }

    private func locked<R>(_ body: () throws -> R) rethrows -> R {
        lock.lock()
        defer { lock.unlock() }
        return try body()
    }

    func send(_ request: HttpRequest) async throws -> HttpResponse {
        let delay = latencyMs
        if delay > 0 { try await Task.sleep(nanoseconds: UInt64(delay) * 1_000_000) }
        return try handle(request)
    }

    private func handle(_ request: HttpRequest) throws -> HttpResponse {
        try locked { () throws -> HttpResponse in
            if !_online { throw URLError(.notConnectedToInternet) }
            _requests += 1
            if _failNext > 0 {
                _failNext -= 1
                return HttpResponse(code: 503, headers: ["Retry-After": "1"], body: Data("Сервер занят".utf8))
            }
            return try route(request, Self.localPath(request.path))
        }
    }

    /// Полный адрес (Location загрузки) → путь от корня.
    static func localPath(_ path: String) -> String {
        guard let scheme = path.range(of: "://") else { return path }
        let rest = path[scheme.upperBound...]
        guard let slash = rest.firstIndex(of: "/") else { return "/" }
        return String(rest[slash...])
    }

    private func route(_ r: HttpRequest, _ path: String) throws -> HttpResponse {
        let p: [String] = path.split(separator: "/").map(String.init)
        // Вход и всё «своё» — только с токеном
        if path.hasPrefix("/v1/auth/") { return auth(r, path) }
        let needsAuth = path == "/v1/me" || path.hasPrefix("/v1/devices")
        let device = deviceOf(r)
        if needsAuth && device == nil { return HttpResponse(code: 401, body: Data("Нужен вход".utf8)) }
        device?.lastSeen = clock()
        if let mine = accountRoute(r, path, p, device) { return mine }
        if let chats = chatRoute(r, path, p) { return chats }
        if r.method == "GET" && path == "/v1/feed" { return page(posts, r) }
        if r.method == "GET" && p.count == 4 && p[1] == "users" && p[3] == "posts" {
            let userId = Int(p[2])
            return page(posts.filter { $0.author.id == userId }, r)
        }
        if r.method == "POST" && path == "/v1/posts" { return createPost(r) }
        if p.count == 4 && p[1] == "posts" && p[3] == "like" {
            guard let id = Int64(p[2]) else { return notFound() }
            switch r.method {
            case "PUT": liked.insert(id)
            case "DELETE": liked.remove(id)
            default: return HttpResponse(code: 405)
            }
            return HttpResponse(code: 204)
        }
        if p.count == 4 && p[1] == "chats" && p[3] == "messages" {
            guard let chat = Int(p[2]) else { return notFound() }
            return r.method == "POST" ? sendMessage(chat, r) : chatPage(chat, r)
        }
        if r.method == "OPTIONS" && path == "/v1/uploads" {
            var headers: [String: String] = [:]
            headers["Tus-Version"] = TusClient.version
            headers["Tus-Max-Size"] = String(Self.maxUpload)
            headers["Tus-Extension"] = "creation"
            return HttpResponse(code: 204, headers: headers)
        }
        if r.method == "POST" && path == "/v1/uploads" { return createUpload(r) }
        if p.count == 3 && p[1] == "uploads" { return try upload(p[2], r) }
        return notFound()
    }

    private func notFound() -> HttpResponse { HttpResponse(code: 404, body: Data("Не найдено".utf8)) }

    private func bodyJson(_ r: HttpRequest) -> JSONObject? { Json.parse(r.body) }

    // MARK: - Профиль, устройства, запись

    private func accountRoute(_ r: HttpRequest, _ path: String, _ p: [String], _ device: Device?) -> HttpResponse? {
        if path == "/v1/me" && r.method == "GET" {
            guard let profile else { return notFound() }
            return json(profile)
        }
        if path == "/v1/me" && r.method == "PUT" {
            guard var body = bodyJson(r) else { return HttpResponse(code: 400) }
            body["updatedAt"] = clock()
            profile = body
            return json(body)
        }
        if path == "/v1/devices" && r.method == "GET" {
            var items: [JSONObject] = []
            for d in devices {
                var o: JSONObject = [:]
                o["id"] = d.id
                o["name"] = d.name
                o["platform"] = d.platform
                o["lastSeen"] = d.lastSeen
                o["current"] = d === device
                items.append(o)
            }
            let out: JSONObject = ["items": items]
            return json(out)
        }
        if path == "/v1/devices/revoke-others" && r.method == "POST" {
            devices.removeAll { $0 !== device }
            return HttpResponse(code: 204)
        }
        if p.count == 3 && p[1] == "devices" && r.method == "DELETE" {
            let before = devices.count
            devices.removeAll { $0.id == p[2] && $0 !== device }
            return devices.count < before ? HttpResponse(code: 204) : notFound()
        }
        // Своя запись: включить или выключить комментарии
        if p.count == 3 && p[1] == "posts" && r.method == "PATCH" {
            guard let id = Int64(p[2]), let i = posts.firstIndex(where: { $0.id == id }) else { return notFound() }
            if let body = bodyJson(r), body["commentsOff"] != nil { posts[i].commentsOff = Json.bool(body, "commentsOff") }
            return HttpResponse(code: 204)
        }
        return nil
    }

    // MARK: - Группы, «прочитано», запросы

    private func chatRoute(_ r: HttpRequest, _ path: String, _ p: [String]) -> HttpResponse? {
        if r.method == "POST" && path == "/v1/chats" { return createChat(r) }
        let isChat = p.count >= 3 && p[1] == "chats"
        if !isChat { return nil }
        if p.count == 3 && r.method == "PATCH" {
            guard let id = Int(p[2]), var g = groups[id] else { return notFound() }
            let title = bodyJson(r).map { Json.string($0, "title") } ?? ""
            if !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { g["title"] = title }
            groups[id] = g
            groupChanged(id)
            return json(g)
        }
        if p.count == 5 && (p[3] == "members" || p[3] == "admins") {
            guard let id = Int(p[2]), let uid = Int(p[4]), var g = groups[id] else { return notFound() }
            let key = p[3]
            var list = Json.ints(g, key)
            switch r.method {
            case "PUT":
                if !list.contains(uid) { list.append(uid) }
            case "DELETE":
                list.removeAll { $0 == uid }
            default:
                return HttpResponse(code: 405)
            }
            g[key] = list
            groups[id] = g
            if key == "members" { members[id] = list }
            groupChanged(id)
            return HttpResponse(code: 204)
        }
        if p.count == 4 && (p[3] == "read" || p[3] == "accept") && r.method == "POST" {
            return HttpResponse(code: 204)
        }
        return nil
    }

    private func createChat(_ r: HttpRequest) -> HttpResponse {
        let key = r.header("Idempotency-Key")
        if let key, let earlier = byKey[key] { return json(earlier, 200) }
        guard let body = bodyJson(r) else { return HttpResponse(code: 400) }
        let id = Json.int(body, "id")
        if id <= 0 { return HttpResponse(code: 400, body: Data("Нужен id".utf8)) }
        if groups[id] != nil { return HttpResponse(code: 409) }
        var unique: [Int] = []
        for uid in Json.ints(body, "members") where !unique.contains(uid) { unique.append(uid) }
        let list = Array(unique.prefix(ChatGroup.maxMembers))
        let owner = me().id
        var g: JSONObject = [:]
        g["id"] = id
        g["title"] = String(Json.string(body, "title").prefix(ChatGroup.maxTitle))
        g["members"] = list
        g["admins"] = [owner]
        g["owner"] = owner
        groups[id] = g
        members[id] = list
        if let key { byKey[key] = g }
        return json(g, 201)
    }

    private func groupChanged(_ id: Int) {
        guard let g = groups[id] else { return }
        let event = RtEvent.groupChanged(chatId: id, title: Json.string(g, "title"), memberIds: Json.ints(g, "members"), adminIds: Json.ints(g, "admins"))
        _realtime?.schedule(event, afterMs: 0)
    }

    // MARK: - Вход

    private func deviceOf(_ r: HttpRequest) -> Device? {
        guard let header = r.header("Authorization") else { return nil }
        var token = header
        if token.hasPrefix("Bearer ") { token = String(token.dropFirst(7)) }
        token = token.trimmingCharacters(in: .whitespaces)
        let now = clock()
        return devices.first { $0.access == token && $0.accessUntil > now }
    }

    private func tokens(_ d: Device) -> JSONObject {
        tokenSeq += 1
        d.access = "a.\(tokenSeq).\(d.id)"
        tokenSeq += 1
        d.refresh = "r.\(tokenSeq).\(d.id)"
        d.accessUntil = clock() + _accessTtlMs
        var o: JSONObject = [:]
        o["access"] = d.access
        o["refresh"] = d.refresh
        o["expiresIn"] = _accessTtlMs / 1000
        o["deviceId"] = d.id
        return o
    }

    private func auth(_ r: HttpRequest, _ path: String) -> HttpResponse {
        switch path {
        case "/v1/auth/login":
            let body = bodyJson(r) ?? [:]
            if Json.string(body, "login").trimmingCharacters(in: .whitespaces).isEmpty { return HttpResponse(code: 400) }
            // Демо: пароль проверяет телефон (LocalAuth); здесь — любой
            let now = clock()
            if devices.isEmpty {
                // «Другие устройства» этого человека — чтобы было что показать в списке
                let day: Int64 = 86_400_000
                devices.append(Device(id: "d-ipad", name: "iPad", platform: "ios", lastSeen: now - 3 * day, refresh: "r.old1"))
                devices.append(Device(id: "d-web", name: "Chrome · macOS", platform: "web", lastSeen: now - 20 * day, refresh: "r.old2"))
            }
            let dev = Json.object(body, "device") ?? [:]
            let given = Json.string(dev, "name")
            let name = given.isEmpty ? "Телефон" : given
            let id = "d\(devices.count + 1)x\(now % 10_000)"
            let d = Device(id: id, name: name, platform: Json.string(dev, "platform"), lastSeen: now)
            devices.append(d)
            return json(tokens(d))
        case "/v1/auth/refresh":
            let token = bodyJson(r).map { Json.string($0, "refresh") } ?? ""
            guard !token.isEmpty, let d = devices.first(where: { $0.refresh == token }) else {
                return HttpResponse(code: 401, body: Data("Сессия закрыта".utf8))
            }
            d.lastSeen = clock()
            return json(tokens(d))
        case "/v1/auth/logout":
            if let d = deviceOf(r) { devices.removeAll { $0 === d } }
            return HttpResponse(code: 204)
        default:
            return notFound()
        }
    }

    /// Токен доступа «истёк» — для проверки обновления.
    func expireAccessTokens() {
        locked {
            for d in devices { d.accessUntil = 0 }
        }
    }

    func deviceCount() -> Int { locked { devices.count } }

    /// Демо: сервер узнаёт участников переписок, которые были до него.
    func registerChat(_ chatId: Int, _ memberIds: [Int]) {
        locked {
            if members[chatId] == nil { members[chatId] = memberIds }
        }
    }

    /// Демо: кто-то со стороны пишет — через живой канал, как настоящий сервер.
    func deliver(_ chatId: Int, _ message: JSONObject, afterMs: Int64 = 0) {
        var m = message
        m["sentAt"] = clock() + afterMs
        realtime?.schedule(.newMessage(chatId: chatId, message: m), afterMs: afterMs)
    }

    private func json(_ o: JSONObject, _ code: Int = 200) -> HttpResponse {
        HttpResponse(code: code, headers: ["Content-Type": Api.json], body: Json.data(o))
    }

    private func codec() -> ApiCodec { ApiCodec(me: me()) }

    // MARK: - Лента

    /// Курсор — «время:номер» последней отданной записи: новые записи не сдвигают страницы.
    private func page(_ source: [Post], _ r: HttpRequest) -> HttpResponse {
        let asked = Int(r.query["limit"] ?? "") ?? Api.page
        let limit = min(max(asked, 1), 100)
        let sorted = source.sorted { a, b in
            let ta = Json.ms(a.createdAt)
            let tb = Json.ms(b.createdAt)
            if ta != tb { return ta > tb }
            return a.id > b.id
        }
        var rest = sorted
        if let raw = r.query["cursor"], let after = Self.decodeCursor(raw) {
            rest = sorted.filter { p in
                let t = Json.ms(p.createdAt)
                return t < after.at || (t == after.at && p.id < after.id)
            }
        }
        let chunk: [Post] = rest.prefix(limit).map { p -> Post in
            guard liked.contains(p.id) && !p.liked else { return p }
            var marked = p
            marked.liked = true
            marked.likes += 1
            return marked
        }
        var next: String? = nil
        if rest.count > limit, let last = chunk.last {
            next = Self.encodeCursor(Json.ms(last.createdAt), last.id)
        }
        let c = codec()
        var o: JSONObject = [:]
        o["items"] = chunk.map { c.postJson($0) }
        o["next"] = next.map { $0 as Any } ?? NSNull()
        return json(o)
    }

    private func createPost(_ r: HttpRequest) -> HttpResponse {
        let key = r.header("Idempotency-Key")
        if let key, let earlier = byKey[key] { return json(earlier, 200) }
        guard let body = Json.parse(r.body) else { return HttpResponse(code: 400, body: Data("Нужен JSON".utf8)) }
        let c = codec()
        guard let post = c.postOf(body) else { return HttpResponse(code: 422) }
        // Вложения — только уже загруженные целиком
        let unfinished = post.media.contains { a in
            guard a.fileName.hasPrefix("/v1/uploads/") else { return false }
            let id = (a.fileName as NSString).lastPathComponent
            guard let blob = uploads[id] else { return true }
            return blob.offset != blob.length
        }
        if unfinished { return HttpResponse(code: 422, body: Data("Загрузка не закончена".utf8)) }
        var o = c.postJson(post)
        o["id"] = nextId
        o["author"] = me().id
        o["createdAt"] = clock()
        o["outboxKey"] = ""
        nextId += 1
        guard let saved = c.postOf(o) else { return HttpResponse(code: 422) }
        posts.append(saved)
        let out = c.postJson(saved)
        if let key { byKey[key] = out }
        return json(out, 201)
    }

    // MARK: - Переписка

    private func sendMessage(_ chat: Int, _ r: HttpRequest) -> HttpResponse {
        let key = r.header("Idempotency-Key")
        if let key, let earlier = byKey[key] { return json(earlier, 200) }
        guard let body = Json.parse(r.body) else { return HttpResponse(code: 400) }
        let c = codec()
        guard var m = c.messageOf(body) else { return HttpResponse(code: 422) }
        let sentAt = clock()
        let meId = me().id
        m.pending = false
        m.delivered = true
        m.authorId = meId
        m.sentAt = sentAt
        var saved = c.messageJson(m)
        saved["id"] = nextId
        nextId += 1
        messages[chat, default: []].append(saved)
        if let key { byKey[key] = saved }
        // Демо: участники «читают» через пару секунд — событием живого канала
        let readers = (members[chat] ?? []).filter { $0 != meId }
        for (i, uid) in readers.enumerated() {
            let delay: Int64 = _readDelayMs + Int64(i) * 700
            _realtime?.schedule(.read(chatId: chat, userId: uid, upTo: sentAt), afterMs: delay)
        }
        return json(saved, 201)
    }

    private func chatPage(_ chat: Int, _ r: HttpRequest) -> HttpResponse {
        let asked = Int(r.query["limit"] ?? "") ?? Api.page
        let limit = min(max(asked, 1), 100)
        let all = (messages[chat] ?? []).sorted { Json.long($0, "id") > Json.long($1, "id") }
        var rest = all
        if let raw = r.query["cursor"], let before = Int64(raw) {
            rest = all.filter { Json.long($0, "id") < before }
        }
        let chunk = Array(rest.prefix(limit))
        var next: String? = nil
        if rest.count > limit, let last = chunk.last { next = String(Json.long(last, "id")) }
        var o: JSONObject = [:]
        o["items"] = chunk
        o["next"] = next.map { $0 as Any } ?? NSNull()
        return json(o)
    }

    // MARK: - Загрузки (tus 1.0)

    private func createUpload(_ r: HttpRequest) -> HttpResponse {
        if r.header("Tus-Resumable") != TusClient.version {
            return HttpResponse(code: 412, headers: ["Tus-Version": TusClient.version])
        }
        guard let raw = r.header("Upload-Length"), let length = Int64(raw) else { return HttpResponse(code: 400) }
        if length > Self.maxUpload { return HttpResponse(code: 413) }
        uploadSeq += 1
        let id = "u\(uploadSeq)x\(clock() % 100_000)"
        var file: URL? = nil
        if let dir = self.dir {
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            file = dir.appendingPathComponent(id)
        }
        uploads[id] = Blob(length: length, meta: r.header("Upload-Metadata") ?? "", file: file)
        let headers: [String: String] = ["Location": "/v1/uploads/\(id)", "Tus-Resumable": TusClient.version]
        return HttpResponse(code: 201, headers: headers)
    }

    private func upload(_ id: String, _ r: HttpRequest) throws -> HttpResponse {
        guard let blob = uploads[id] else { return notFound() }
        let tus: [String: String] = ["Tus-Resumable": TusClient.version]
        switch r.method {
        case "HEAD":
            var headers = tus
            headers["Upload-Offset"] = String(blob.offset)
            headers["Upload-Length"] = String(blob.length)
            headers["Cache-Control"] = "no-store"
            return HttpResponse(code: 200, headers: headers)
        case "PATCH":
            let type = r.contentType ?? r.header("Content-Type") ?? ""
            if !type.hasPrefix("application/offset+octet-stream") { return HttpResponse(code: 415) }
            guard let raw = r.header("Upload-Offset"), let offset = Int64(raw) else { return HttpResponse(code: 400) }
            if offset != blob.offset { return HttpResponse(code: 409, headers: tus, body: Data("Смещение не совпало".utf8)) }
            let bytes = r.body ?? Data()
            if blob.offset + Int64(bytes.count) > blob.length { return HttpResponse(code: 400) }
            let drop = _dropAfterBytes
            if drop >= 0 && drop < Int64(bytes.count) {
                // Связь оборвалась посреди куска: часть дошла, ответа не будет
                blob.append(bytes.prefix(Int(drop)))
                _dropAfterBytes = -1
                throw URLError(.networkConnectionLost)
            }
            blob.append(bytes)
            var headers = tus
            headers["Upload-Offset"] = String(blob.offset)
            return HttpResponse(code: 204, headers: headers)
        case "DELETE":
            if let file = uploads.removeValue(forKey: id)?.file { try? FileManager.default.removeItem(at: file) }
            return HttpResponse(code: 204, headers: tus)
        default:
            return HttpResponse(code: 405)
        }
    }

    // MARK: - Для тестов

    /// Что лежит в загрузке.
    func uploaded(_ id: String) -> Data? {
        locked {
            uploads[(id as NSString).lastPathComponent]?.bytes()
        }
    }

    /// Имя файла из Upload-Metadata.
    func uploadName(_ id: String) -> String? {
        locked { () -> String? in
            guard let meta = uploads[(id as NSString).lastPathComponent]?.meta else { return nil }
            for pair in meta.split(separator: ",") {
                let fields = pair.trimmingCharacters(in: .whitespaces).split(separator: " ").map(String.init)
                guard fields.first == "filename", fields.count > 1 else { continue }
                guard let data = Data(base64Encoded: fields[1]) else { return nil }
                return String(decoding: data, as: UTF8.self)
            }
            return nil
        }
    }

    func postCount() -> Int { locked { posts.count } }

    func messagesIn(_ chat: Int) -> Int { locked { messages[chat]?.count ?? 0 } }

    func isLiked(_ postId: Int64) -> Bool { locked { liked.contains(postId) } }

    // MARK: - Курсор

    /// base64url без «=» от «время:номер».
    static func encodeCursor(_ at: Int64, _ id: Int64) -> String {
        let raw = Data("\(at):\(id)".utf8).base64EncodedString()
        return raw.replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    static func decodeCursor(_ s: String) -> (at: Int64, id: Int64)? {
        if s.contains("+") || s.contains("/") { return nil }
        var b64 = s.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        while b64.count % 4 != 0 { b64 += "=" }
        guard let data = Data(base64Encoded: b64), let raw = String(data: data, encoding: .utf8) else { return nil }
        let before: Substring
        let after: Substring
        if let colon = raw.firstIndex(of: ":") {
            before = raw[..<colon]
            after = raw[raw.index(after: colon)...]
        } else {
            before = raw[...]
            after = raw[...]
        }
        guard let at = Int64(before), let id = Int64(after) else { return nil }
        return (at, id)
    }

    /// Тексты старых записей друзей — то, что видно, когда листаешь ленту далеко вниз.
    private static let history: [String] = [
        "Утро начинается с кофе и хорошей книги",
        "Наконец-то выбрались за город — воздух совсем другой",
        "Кто знает хорошую пекарню в центре?",
        "Пробежала 8 км, ноги гудят, настроение отличное",
        "Пересмотрел «Солярис» — каждый раз как впервые",
        "Новый плейлист на неделю готов",
        "Дождь весь день, зато дома тепло",
        "Закат с крыши — лучшая награда за длинный день",
        "Приготовила толму по бабушкиному рецепту",
        "Записался на курсы фотографии, страшно и интересно",
        "Вечером — настолки с друзьями, кто с нами?",
        "Горы зовут: в субботу на Арагац",
    ]
}
