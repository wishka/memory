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
        m.pending = false
        var saved = c.messageJson(m)
        saved["id"] = nextId
        saved["sentAt"] = clock()
        nextId += 1
        messages[chat, default: []].append(saved)
        if let key { byKey[key] = saved }
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
