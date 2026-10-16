import Foundation

/// Клиент API Memory (контракт — docs/openapi.yaml). Знает пути и формат,
/// но не то, как запрос летит: это Transport (URLSession или демо-сервер).
///
/// Записи и сообщения в JSON — тем же форматом, что у Android
/// (StateStore.Codec: автор — номером, время — мс, вложение — path): один
/// формат для обеих платформ. См. ApiCodec ниже.
///
/// Все изменения несут Idempotency-Key: если ответ потерялся и очередь
/// повторит запрос, сервер вернёт тот же результат, а не создаст дубль.
final class Api {
    static let page = 20
    static let json = "application/json; charset=utf-8"

    private let transport: Transport
    private let me: () -> User
    /// Вход на сервер (токены); nil — без входа (тесты сети без авторизации).
    let session: Session?

    init(transport: Transport, me: @escaping () -> User, session: Session? = nil) {
        self.transport = transport
        self.me = me
        self.session = session
    }

    private func codec() -> ApiCodec { ApiCodec(me: me()) }

    /// Запрос с токеном; не 2xx — NetError, нет сети — NetError(.offline).
    /// 401 — обновить токен и повторить один раз; не обновился — сессия
    /// стёрта, наружу NetError(.auth).
    func call(_ request: HttpRequest) async throws -> HttpResponse {
        let first = try await raw(request)
        if first.code != 401 || request.path.hasPrefix("/v1/auth/") { return try checked(first) }
        guard let s = session, s.refresh != nil else { return try checked(first) }
        let renewed = try await refreshTokens()
        if !renewed { return try checked(first) }
        let second = try await raw(request)
        return try checked(second)
    }

    private func raw(_ request: HttpRequest) async throws -> HttpResponse {
        var req = request
        if let tok = session?.access { req.headers["Authorization"] = "Bearer \(tok)" }
        if req.header("Accept") == nil { req.headers["Accept"] = "application/json" }
        do {
            return try await transport.send(req)
        } catch {
            if error is CancellationError { throw error }
            throw NetError.of(error)
        }
    }

    private func checked(_ r: HttpResponse) throws -> HttpResponse {
        if !r.ok { throw NetError.of(r) }
        return r
    }

    /// Новая пара токенов по токену обновления. false — не вышло (вышли с этого устройства).
    @discardableResult
    func refreshTokens() async throws -> Bool {
        guard let s = session, let token = s.refresh else { return false }
        let body: JSONObject = ["refresh": token]
        let request = HttpRequest(method: "POST", path: "/v1/auth/refresh", body: Json.data(body), contentType: Self.json)
        let r = try await raw(request)
        if r.code == 401 || r.code == 403 {
            s.clear()
            return false
        }
        _ = try checked(r)
        s.save(try parse(r))
        return true
    }

    // MARK: - Вход и устройства

    /// Войти: логин, пароль, это устройство («iPhone», «ios»).
    func signIn(login: String, password: String, device: String, platform: String) async throws {
        let deviceJson: JSONObject = ["name": device, "platform": platform]
        var body: JSONObject = [:]
        body["login"] = login
        body["password"] = password
        body["device"] = deviceJson
        let request = HttpRequest(method: "POST", path: "/v1/auth/login", body: Json.data(body), contentType: Self.json)
        let r = try await call(request)
        let tokens = try parse(r)
        session?.save(tokens)
    }

    /// Новый аккаунт на сервере (настоящем; демо-серверу не нужен — он
    /// пускает любого). 409 — логин занят: NetError(.conflict).
    func register(login: String, password: String, name: String, device: String, platform: String) async throws {
        let deviceJson: JSONObject = ["name": device, "platform": platform]
        var body: JSONObject = [:]
        body["login"] = login
        body["password"] = password
        body["name"] = name
        body["device"] = deviceJson
        let request = HttpRequest(method: "POST", path: "/v1/auth/register", body: Json.data(body), contentType: Self.json)
        let r = try await call(request)
        let tokens = try parse(r)
        session?.save(tokens)
    }

    /// Выйти с этого устройства: токены на сервере отзываются.
    func signOut() async {
        _ = try? await call(HttpRequest(method: "POST", path: "/v1/auth/logout"))
        session?.clear()
    }

    func devices() async throws -> [DeviceInfo] {
        let o = try await getJson("/v1/devices")
        return Json.objects(o, "items").map { d in
            DeviceInfo(
                id: Json.string(d, "id"), name: Json.string(d, "name"), platform: Json.string(d, "platform"),
                lastSeen: Json.long(d, "lastSeen"), current: Json.bool(d, "current")
            )
        }
    }

    func revokeDevice(_ id: String) async throws {
        _ = try await call(HttpRequest(method: "DELETE", path: "/v1/devices/\(id)"))
    }

    func revokeOtherDevices() async throws {
        _ = try await call(HttpRequest(method: "POST", path: "/v1/devices/revoke-others"))
    }

    /// Профиль с сервера; nil — на сервере его ещё нет (первый вход).
    func profile() async throws -> ProfileSync? {
        do {
            let o = try await getJson("/v1/me")
            return ProfileSync(json: o, updatedAt: Json.long(o, "updatedAt"))
        } catch let e as NetError where e.code == 404 {
            return nil
        }
    }

    // MARK: - Люди

    /// Поиск людей на сервере (от 2 знаков): они же попадают в справочник.
    func searchUsers(_ query: String) async throws -> [User] {
        let o = try await getJson("/v1/users", ["q": query])
        return Json.objects(o, "items").compactMap { Directory.remember($0) }
    }

    /// Человек по номеру (имя, ключ шифрования).
    @discardableResult
    func user(_ id: Int) async throws -> User? {
        let o = try await getJson("/v1/users/\(id)")
        return Directory.remember(o)
    }

    /// Личная переписка с человеком: номер выбирает сервер, у пары она одна.
    func directChat(_ userId: Int) async throws -> Int {
        let body: JSONObject = ["user": userId]
        let o = try await sendJson("POST", "/v1/chats/direct", body, key: nil)
        guard o["id"] != nil else { throw NetError(.badData, message: "Нет номера переписки") }
        return Json.int(o, "id")
    }

    /// Свой открытый ключ — чтобы собеседники могли писать в секретную переписку.
    func putKey(_ publicKey: [UInt8]) async throws {
        let body: JSONObject = ["publicKey": Data(publicKey).base64EncodedString()]
        _ = try await sendJson("PUT", "/v1/me/key", body, key: nil)
    }

    /// Подписаться: ответ — "following" (открытый профиль) или "requested" (заявка).
    func follow(_ userId: Int) async throws -> String {
        let o = try await sendJson("PUT", "/v1/users/\(userId)/follow", [:], key: nil)
        return Json.string(o, "state")
    }

    private func people(_ path: String) async throws -> [User] {
        let o = try await getJson(path)
        return Json.objects(o, "items").compactMap { Directory.remember($0) }
    }

    /// На кого я подписан.
    func following() async throws -> [User] { try await people("/v1/me/following") }

    /// Мои заявки, которые ещё ждут ответа.
    func sentRequests() async throws -> [User] { try await people("/v1/me/sent") }

    /// Заявки ко мне.
    func followRequests() async throws -> [User] { try await people("/v1/me/requests") }

    /// «Интересное»: записи людей, на которых я не подписан.
    func explore(_ cursor: String?, limit: Int = Api.page) async throws -> Page<Post> {
        let o = try await getJson("/v1/explore", query(cursor, limit))
        return posts(o)
    }

    // MARK: - Комментарии и истории

    /// Комментарии к записи по порядку (до 100 — больше под одной записью не показываем).
    func comments(_ postId: Int64) async throws -> [Comment] {
        let o = try await getJson("/v1/posts/\(postId)/comments", ["limit": "100"])
        let c = codec()
        return Json.objects(o, "items").compactMap { c.commentOf($0) }
    }

    /// Комментарий: ответ — он же с номером сервера.
    func addComment(_ postId: Int64, _ comment: Comment, key: String) async throws -> Comment {
        let c = codec()
        var body: JSONObject = [:]
        body["text"] = comment.text
        body["replyTo"] = comment.replyTo ?? ""
        if let sticker = comment.sticker { body["sticker"] = c.attachmentJson(sticker) }
        let o = try await sendJson("POST", "/v1/posts/\(postId)/comments", body, key: key)
        guard let saved = c.commentOf(o) else { throw NetError(.badData, message: "Не комментарий") }
        return saved
    }

    /// Живые истории тех, на кого подписан, и свои; seen — смотрел ли я её.
    func stories() async throws -> [(story: Story, seen: Bool)] {
        let o = try await getJson("/v1/stories")
        let c = codec()
        return Json.objects(o, "items").map { item in
            (story: c.storyOf(item), seen: Json.bool(item, "seen"))
        }
    }

    /// Удалить аккаунт на сервере со всем, что в нём (нужен пароль).
    func deleteMe(_ password: String) async throws {
        let body: JSONObject = ["password": password]
        _ = try await sendJson("DELETE", "/v1/me", body, key: nil)
    }

    /// Сменить пароль на сервере; остальные устройства выходят.
    func changePassword(current: String, new: String) async throws {
        let body: JSONObject = ["current": current, "new": new]
        _ = try await sendJson("PUT", "/v1/me/password", body, key: nil)
    }

    /// Кого я заблокировал (номера на сервере).
    func blocked() async throws -> [Int] {
        let o = try await getJson("/v1/me/blocked")
        return Json.ints(o, "ids")
    }

        /// Мои переписки на сервере (личные и группы) — JSON как есть.
    func chats() async throws -> [JSONObject] {
        let o = try await getJson("/v1/chats")
        return Json.objects(o, "items")
    }

    /// Скачать загруженный файл (GET /v1/uploads/{id}) — фото чужой записи или сообщения.
    func download(_ path: String) async throws -> Data {
        let request = HttpRequest(method: "GET", path: path, headers: ["Accept": "*/*"])
        let r = try await call(request)
        return r.body
    }

    private func getJson(_ path: String, _ query: [String: String] = [:]) async throws -> JSONObject {
        let r = try await call(HttpRequest(method: "GET", path: path, query: query))
        return try parse(r)
    }

    private func sendJson(_ method: String, _ path: String, _ body: JSONObject, key: String?) async throws -> JSONObject {
        var headers: [String: String] = [:]
        if let key { headers["Idempotency-Key"] = key }
        let request = HttpRequest(method: method, path: path, headers: headers, body: Json.data(body), contentType: Self.json)
        let r = try await call(request)
        if r.body.isEmpty { return [:] }
        return try parse(r)
    }

    private func parse(_ r: HttpResponse) throws -> JSONObject {
        guard let o = Json.parse(r.body) else { throw NetError(.badData, code: r.code, message: "Не JSON") }
        return o
    }

    private func query(_ cursor: String?, _ limit: Int) -> [String: String] {
        var q: [String: String] = ["limit": String(limit)]
        if let cursor { q["cursor"] = cursor }
        return q
    }

    /// Курсор следующей страницы: null, пусто или нет — дальше ничего.
    private func nextCursor(_ o: JSONObject) -> String? {
        if Json.isNull(o, "next") { return nil }
        let next = Json.string(o, "next")
        return next.isEmpty ? nil : next
    }

    private func posts(_ o: JSONObject) -> Page<Post> {
        let c = codec()
        let items = Json.objects(o, "items").compactMap { c.postOf($0) }
        return Page(items: items, next: nextCursor(o))
    }

    // MARK: - Записи

    /// Лента: новые сверху, по limit штук; cursor — из прошлой страницы.
    func feed(_ cursor: String?, limit: Int = Api.page) async throws -> Page<Post> {
        let o = try await getJson("/v1/feed", query(cursor, limit))
        return posts(o)
    }

    func userPosts(_ userId: Int, _ cursor: String?, limit: Int = Api.page) async throws -> Page<Post> {
        let o = try await getJson("/v1/users/\(userId)/posts", query(cursor, limit))
        return posts(o)
    }

    /// Опубликовать. media — уже загруженные файлы: путь вложения заменён
    /// адресом загрузки (Upload-Location tus). Ответ — запись с номером сервера.
    func createPost(_ post: Post, key: String) async throws -> Post {
        let c = codec()
        let o = try await sendJson("POST", "/v1/posts", c.postJson(post), key: key)
        guard let saved = c.postOf(o) else { throw NetError(.badData, message: "Не запись") }
        return saved
    }

    func like(_ postId: Int64, liked: Bool, key: String) async throws {
        _ = try await sendJson(liked ? "PUT" : "DELETE", "/v1/posts/\(postId)/like", [:], key: key)
    }

    // MARK: - Переписка

    /// История переписки: от новых к старым, cursor — номер последнего отданного.
    func messages(_ chatId: Int, _ cursor: String?, limit: Int = Api.page) async throws -> Page<Message> {
        let o = try await getJson("/v1/chats/\(chatId)/messages", query(cursor, limit))
        let c = codec()
        let items = Json.objects(o, "items").compactMap { c.messageOf($0) }
        return Page(items: items, next: nextCursor(o))
    }

    /// Любой запрос из очереди (группы, «прочитано», профиль): метод, путь, тело.
    func send(_ method: String, _ path: String, body: JSONObject?, key: String) async throws -> JSONObject {
        guard let body else {
            let r = try await call(HttpRequest(method: method, path: path, headers: ["Idempotency-Key": key]))
            if r.body.isEmpty { return [:] }
            return try parse(r)
        }
        return try await sendJson(method, path, body, key: key)
    }

    /// Сообщение как есть (JSON) — ответ сервера тоже JSON: с номером и sentAt.
    func sendRaw(_ chatId: Int, _ message: JSONObject, key: String) async throws -> JSONObject {
        try await sendJson("POST", "/v1/chats/\(chatId)/messages", message, key: key)
    }

    func sendMessage(_ chatId: Int, _ message: Message, key: String) async throws -> Message {
        let c = codec()
        let o = try await sendJson("POST", "/v1/chats/\(chatId)/messages", c.messageJson(message), key: key)
        guard let saved = c.messageOf(o) else { throw NetError(.badData, message: "Не сообщение") }
        return saved
    }
}

/// Записи и сообщения ⇄ JSON в формате Android (StateStore.Codec в
/// StateStore.kt): автор и собеседник — номером, время — мс с 1970 (0 —
/// нет), вид вложения — «Photo»/«Video»/«Document», файл — «path». Сохранение
/// на iPhone (Codable) — своё; это — только для сети.
struct ApiCodec {
    let me: User

    private func person(_ raw: Int) -> User {
        // Свой номер на сервере — это я
        let id = Directory.local(raw)
        if id == me.id { return me }
        return Demo.person(id) ?? User(id: id, name: "Неизвестный", handle: "unknown")
    }

    /// «12:05» по часам телефона; 0 — пусто.
    static func clockTime(_ ms: Int64) -> String {
        if ms <= 0 { return "" }
        let at = Date(timeIntervalSince1970: Double(ms) / 1000)
        return clockFormatter.string(from: at)
    }

    /// Один на всё приложение: создавать форматтер на каждое сообщение дорого.
    /// Пояс и язык — текущие (autoupdating), как Locale.getDefault() на Android.
    private static let clockFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale.autoupdatingCurrent
        f.timeZone = TimeZone.autoupdatingCurrent
        f.dateFormat = "HH:mm"
        return f
    }()

    // MARK: Вложения

    static func kindName(_ kind: MediaKind) -> String {
        switch kind {
        case .photo: return "Photo"
        case .video: return "Video"
        case .document: return "Document"
        }
    }

    static func kindOf(_ name: String) -> MediaKind? {
        switch name.lowercased() {
        case "photo": return .photo
        case "video": return .video
        case "document": return .document
        default: return nil
        }
    }

    func attachmentJson(_ a: Attachment) -> JSONObject {
        var o: JSONObject = [:]
        o["id"] = a.id
        o["kind"] = Self.kindName(a.kind)
        o["path"] = a.fileName
        o["name"] = a.name
        o["size"] = a.size
        o["mime"] = ""
        o["voice"] = a.voice
        o["durationMs"] = a.durationMs ?? 0
        o["waveform"] = a.waveform ?? []
        o["sticker"] = a.sticker
        o["animation"] = a.animation
        o["frames"] = a.frames
        o["fps"] = a.fps
        var tags: [JSONObject] = []
        for tag in a.tags {
            var t: JSONObject = [:]
            t["user"] = tag.userId
            t["x"] = tag.x
            t["y"] = tag.y
            tags.append(t)
        }
        o["tags"] = tags
        return o
    }

    func attachmentOf(_ o: JSONObject) -> Attachment? {
        let id = Json.string(o, "id")
        let path = Json.string(o, "path")
        guard o["id"] != nil, o["path"] != nil, let kind = Self.kindOf(Json.string(o, "kind")) else { return nil }
        var a = Attachment(id: id, kind: kind, fileName: path)
        a.name = Json.string(o, "name")
        a.size = Json.long(o, "size")
        a.voiceFlag = Json.bool(o, "voice") ? true : nil
        let duration = Json.long(o, "durationMs")
        a.durationMs = duration > 0 ? duration : nil
        let wave = Json.ints(o, "waveform")
        a.waveform = wave.isEmpty ? nil : wave
        a.stickerFlag = Json.bool(o, "sticker") ? true : nil
        a.animation = Json.string(o, "animation")
        a.frames = Json.strings(o, "frames")
        a.fps = Json.int(o, "fps")
        a.tags = Json.objects(o, "tags").map { t in
            PhotoTag(userId: Json.int(t, "user"), x: Json.double(t, "x", 0.5), y: Json.double(t, "y", 0.5))
        }
        return a
    }

    // MARK: Мелкие части

    private func placeJson(_ p: SharedPlace) -> JSONObject {
        ["lat": p.lat, "lon": p.lon, "title": p.title]
    }

    private func placeOf(_ o: JSONObject) -> SharedPlace {
        SharedPlace(lat: Json.double(o, "lat"), lon: Json.double(o, "lon"), title: Json.string(o, "title"))
    }

    private func checkInJson(_ c: CheckIn) -> JSONObject {
        var o: JSONObject = [:]
        o["activity"] = c.activity
        o["emoji"] = c.emoji
        o["title"] = c.title
        o["subject"] = c.subject
        o["amount"] = c.amount
        o["unit"] = c.unit
        o["rating"] = c.rating
        o["status"] = c.status
        o["minutes"] = c.minutes
        return o
    }

    private func checkInOf(_ o: JSONObject) -> CheckIn? {
        guard o["activity"] != nil else { return nil }
        var c = CheckIn(activity: Json.string(o, "activity"), emoji: Json.string(o, "emoji"), title: Json.string(o, "title"))
        c.subject = Json.string(o, "subject")
        c.amount = Json.double(o, "amount")
        c.unit = Json.string(o, "unit")
        c.rating = Json.int(o, "rating")
        c.status = Json.string(o, "status")
        c.minutes = Json.int(o, "minutes")
        return c
    }

    private func pollJson(_ p: Poll) -> JSONObject {
        var o: JSONObject = [:]
        o["id"] = p.id
        o["question"] = p.question
        o["multiple"] = p.multiple
        o["closed"] = p.closed
        o["mine"] = p.mine.sorted()
        var options: [JSONObject] = []
        for option in p.options {
            let item: JSONObject = ["text": option.text, "votes": option.votes]
            options.append(item)
        }
        o["options"] = options
        return o
    }

    private func pollOf(_ o: JSONObject) -> Poll {
        let options = Json.objects(o, "options").map { PollOption(text: Json.string($0, "text"), votes: Json.int($0, "votes")) }
        var p = Poll(id: Json.long(o, "id"), question: Json.string(o, "question"), options: options)
        p.multiple = Json.bool(o, "multiple")
        p.mine = Set(Json.ints(o, "mine"))
        p.closed = Json.bool(o, "closed")
        return p
    }

    private func postRefJson(_ r: PostRef) -> JSONObject {
        var o: JSONObject = [:]
        o["postId"] = r.postId
        o["author"] = r.authorId
        o["name"] = r.authorName
        o["text"] = r.text
        o["photo"] = r.photo
        o["seed"] = r.seed
        o["createdAt"] = Json.ms(r.createdAt)
        return o
    }

    private func postRefOf(_ o: JSONObject) -> PostRef {
        var r = PostRef(postId: Json.long(o, "postId"), authorId: Json.int(o, "author"),
                        authorName: Json.string(o, "name"), text: Json.string(o, "text"))
        r.photo = Json.string(o, "photo")
        r.seed = Json.int(o, "seed")
        r.createdAt = Json.date(Json.long(o, "createdAt"))
        return r
    }

    private func storyRefJson(_ r: StoryRef) -> JSONObject {
        var o: JSONObject = [:]
        o["storyId"] = r.storyId
        o["author"] = r.authorId
        o["preview"] = r.preview
        o["seed"] = r.seed
        o["photo"] = r.photo
        o["bg"] = r.bg
        return o
    }

    private func storyRefOf(_ o: JSONObject) -> StoryRef {
        var r = StoryRef(storyId: Json.long(o, "storyId"), authorId: Json.int(o, "author"), preview: Json.string(o, "preview"))
        r.seed = Json.int(o, "seed")
        r.photo = Json.string(o, "photo")
        r.bg = Json.int(o, "bg")
        return r
    }

    private func callJson(_ c: CallRecord) -> JSONObject {
        var o: JSONObject = [:]
        o["id"] = c.id
        o["peer"] = c.peerId
        o["video"] = c.video
        o["outgoing"] = c.outgoing
        o["at"] = Json.ms(c.at)
        o["durationMs"] = c.durationMs
        o["missed"] = c.missed
        o["declined"] = c.declined
        return o
    }

    private func callOf(_ o: JSONObject) -> CallRecord {
        let at = Date(timeIntervalSince1970: Double(Json.long(o, "at")) / 1000)
        var c = CallRecord(id: Json.long(o, "id"), peerId: Json.int(o, "peer"), video: Json.bool(o, "video"),
                           outgoing: Json.bool(o, "outgoing"), at: at)
        c.durationMs = Json.long(o, "durationMs")
        c.missed = Json.bool(o, "missed")
        c.declined = Json.bool(o, "declined")
        return c
    }

    func commentJson(_ c: Comment) -> JSONObject {
        var o: JSONObject = [:]
        o["id"] = c.id
        o["author"] = c.author.id
        o["text"] = c.text
        o["minutesAgo"] = c.minutesAgo
        o["createdAt"] = Json.ms(c.createdAt)
        o["likes"] = c.likes
        o["liked"] = c.liked
        if let replyTo = c.replyTo { o["replyTo"] = replyTo }
        o["sticker"] = c.sticker.map { attachmentJson($0) as Any } ?? NSNull()
        return o
    }

    func commentOf(_ o: JSONObject) -> Comment? {
        guard o["id"] != nil, o["author"] != nil else { return nil }
        Directory.remember(Json.object(o, "authorInfo"))
        var c = Comment(id: Json.long(o, "id"), author: person(Json.int(o, "author")), text: Json.string(o, "text"))
        c.minutesAgo = Json.int(o, "minutesAgo")
        c.createdAt = Json.date(Json.long(o, "createdAt"))
        c.likes = Json.int(o, "likes")
        c.liked = Json.bool(o, "liked")
        let reply = Json.string(o, "replyTo")
        c.replyTo = reply.isEmpty ? nil : reply
        c.sticker = Json.object(o, "sticker").flatMap { attachmentOf($0) }
        return c
    }

    // MARK: Истории

    func storyJson(_ s: Story) -> JSONObject {
        var o: JSONObject = [:]
        o["id"] = s.id
        o["author"] = s.authorId
        o["createdAt"] = Json.ms(s.createdAt)
        o["media"] = s.media.map { attachmentJson($0) as Any } ?? NSNull()
        o["text"] = s.text
        o["bg"] = s.bg
        o["seed"] = s.seed
        o["close"] = s.closeFriends
        var views: [JSONObject] = []
        for v in s.views {
            let item: JSONObject = ["user": v.userId, "at": Json.ms(v.at), "liked": v.liked]
            views.append(item)
        }
        o["views"] = views
        return o
    }

    /// Автор и зрители с сервера: свой номер — это я (Directory.local).
    func storyOf(_ o: JSONObject) -> Story {
        Directory.remember(Json.object(o, "authorInfo"))
        let at = Date(timeIntervalSince1970: Double(Json.long(o, "createdAt")) / 1000)
        var s = Story(id: Json.long(o, "id"), authorId: Directory.local(Json.int(o, "author")), createdAt: at)
        s.media = Json.object(o, "media").flatMap { attachmentOf($0) }
        s.text = Json.string(o, "text")
        s.bg = Json.int(o, "bg")
        s.seed = Json.int(o, "seed")
        s.closeFriends = Json.bool(o, "close")
        s.views = Json.objects(o, "views").map { v in
            let seen = Date(timeIntervalSince1970: Double(Json.long(v, "at")) / 1000)
            return StoryView(userId: Directory.local(Json.int(v, "user")), at: seen, liked: Json.bool(v, "liked"))
        }
        return s
    }

    // MARK: Записи

    func postJson(_ p: Post) -> JSONObject {
        var o: JSONObject = [:]
        o["id"] = p.id
        o["author"] = p.author.id
        o["text"] = p.text
        o["minutesAgo"] = p.minutesAgo
        o["artSeed"] = p.artSeed.map { $0 as Any } ?? NSNull()
        o["likes"] = p.likes
        o["liked"] = p.liked
        o["comments"] = p.comments
        o["createdAt"] = Json.ms(p.createdAt)
        o["editedAt"] = Json.ms(p.editedAt)
        o["repostOf"] = p.repostOf.map { postRefJson($0) as Any } ?? NSNull()
        o["reposts"] = p.reposts
        o["place"] = p.place.map { placeJson($0) as Any } ?? NSNull()
        o["outboxKey"] = p.outboxKey
        o["commentsOff"] = p.commentsOff
        o["checkIn"] = p.checkIn.map { checkInJson($0) as Any } ?? NSNull()
        o["poll"] = p.poll.map { pollJson($0) as Any } ?? NSNull()
        o["media"] = p.media.map { attachmentJson($0) }
        o["commentList"] = p.commentList.map { commentJson($0) }
        return o
    }

    func postOf(_ o: JSONObject) -> Post? {
        guard o["id"] != nil, o["author"] != nil, o["text"] is String else { return nil }
        Directory.remember(Json.object(o, "authorInfo"))
        var p = Post(id: Json.long(o, "id"), author: person(Json.int(o, "author")),
                     text: Json.string(o, "text"), minutesAgo: Json.int(o, "minutesAgo"))
        p.artSeed = Json.isNull(o, "artSeed") ? nil : Json.int(o, "artSeed")
        p.likes = Json.int(o, "likes")
        p.liked = Json.bool(o, "liked")
        p.comments = Json.int(o, "comments")
        p.media = Json.objects(o, "media").compactMap { attachmentOf($0) }
        p.commentList = Json.objects(o, "commentList").compactMap { commentOf($0) }
        p.createdAt = Json.date(Json.long(o, "createdAt"))
        p.checkIn = Json.object(o, "checkIn").flatMap { checkInOf($0) }
        p.poll = Json.object(o, "poll").map { pollOf($0) }
        p.editedAt = Json.date(Json.long(o, "editedAt"))
        p.repostOf = Json.object(o, "repostOf").map { postRefOf($0) }
        p.reposts = Json.int(o, "reposts")
        p.place = Json.object(o, "place").map { placeOf($0) }
        p.outboxKey = Json.string(o, "outboxKey")
        p.commentsOff = Json.bool(o, "commentsOff")
        return p
    }

    // MARK: Сообщения

    func messageJson(_ m: Message) -> JSONObject {
        var o: JSONObject = [:]
        o["id"] = m.id
        o["fromMe"] = m.fromMe
        o["text"] = m.text
        o["time"] = m.time
        o["attachments"] = m.attachments.map { attachmentJson($0) }
        if let r = m.replyTo {
            let reply: JSONObject = ["messageId": r.messageId, "author": r.author, "preview": r.preview]
            o["replyTo"] = reply
        } else {
            o["replyTo"] = NSNull()
        }
        o["forwardedFrom"] = m.forwardedFrom.map { $0 as Any } ?? NSNull()
        o["poll"] = m.poll.map { pollJson($0) as Any } ?? NSNull()
        o["place"] = m.place.map { placeJson($0) as Any } ?? NSNull()
        if let p = m.product {
            var product: JSONObject = [:]
            product["groupId"] = p.groupId
            product["productId"] = p.productId
            product["title"] = p.title
            product["price"] = p.price
            product["photo"] = p.photo
            product["seed"] = p.seed
            o["product"] = product
        } else {
            o["product"] = NSNull()
        }
        o["secret"] = m.secret
        o["expiresAt"] = Json.ms(m.expiresAt)
        o["story"] = m.story.map { storyRefJson($0) as Any } ?? NSNull()
        o["post"] = m.post.map { postRefJson($0) as Any } ?? NSNull()
        o["call"] = m.call.map { callJson($0) as Any } ?? NSNull()
        o["pending"] = m.pending
        o["authorId"] = m.authorId
        o["delivered"] = m.delivered
        o["readBy"] = m.readBy
        o["sentAt"] = m.sentAt
        return o
    }

    func messageOf(_ o: JSONObject) -> Message? {
        guard o["id"] != nil, o["fromMe"] != nil, o["text"] is String else { return nil }
        Directory.remember(Json.object(o, "authorInfo"))
        // С сервера время приходит числом (sentAt): «12:05» — по часам этого телефона
        let given = Json.string(o, "time")
        let time: String = given.isEmpty ? Self.clockTime(Json.long(o, "sentAt")) : given
        var m = Message(id: Json.long(o, "id"), fromMe: Json.bool(o, "fromMe"),
                        text: Json.string(o, "text"), time: time)
        m.attachments = Json.objects(o, "attachments").compactMap { attachmentOf($0) }
        if let r = Json.object(o, "replyTo"), r["messageId"] != nil {
            m.replyTo = ReplyRef(messageId: Json.long(r, "messageId"), author: Json.string(r, "author"), preview: Json.string(r, "preview"))
        }
        m.forwardedFrom = Json.isNull(o, "forwardedFrom") ? nil : Json.string(o, "forwardedFrom")
        m.poll = Json.object(o, "poll").map { pollOf($0) }
        m.place = Json.object(o, "place").map { placeOf($0) }
        if let p = Json.object(o, "product") {
            var product = ProductRef(groupId: Json.int(p, "groupId"), productId: Json.long(p, "productId"),
                                     title: Json.string(p, "title"), price: Json.long(p, "price"))
            product.photo = Json.string(p, "photo")
            product.seed = Json.int(p, "seed")
            m.product = product
        }
        m.secret = Json.bool(o, "secret")
        m.expiresAt = Json.date(Json.long(o, "expiresAt"))
        m.story = Json.object(o, "story").map { storyRefOf($0) }
        m.post = Json.object(o, "post").map { postRefOf($0) }
        m.call = Json.object(o, "call").map { callOf($0) }
        m.pending = Json.bool(o, "pending")
        m.authorId = Directory.local(Json.int(o, "authorId"))
        m.delivered = Json.bool(o, "delivered")
        m.readBy = Json.ints(o, "readBy")
        m.sentAt = Json.long(o, "sentAt")
        return m
    }

    // MARK: Приватность (профиль на сервере, /v1/me)

    /// Имена — как у Android (Audience.name): Everyone, Following, Nobody.
    static func audienceName(_ a: Audience) -> String {
        switch a {
        case .everyone: return "Everyone"
        case .following: return "Following"
        case .nobody: return "Nobody"
        }
    }

    static func audienceOf(_ name: String) -> Audience {
        switch name {
        case "Following": return .following
        case "Nobody": return .nobody
        default: return .everyone
        }
    }

    func privacyJson(_ p: Privacy) -> JSONObject {
        var o: JSONObject = [:]
        o["profileInfo"] = Self.audienceName(p.profileInfo)
        o["followLists"] = Self.audienceName(p.followLists)
        o["messages"] = Self.audienceName(p.messages)
        o["comments"] = Self.audienceName(p.comments)
        o["showOnline"] = p.showOnline
        o["showBirthday"] = p.showBirthday
        o["closedProfile"] = p.closedProfile
        o["personalRecs"] = p.personalRecs
        o["calls"] = Self.audienceName(p.calls)
        o["hiddenWords"] = p.hiddenWords
        o["hideOffensive"] = p.hideOffensive
        o["hideSpam"] = p.hideSpam
        return o
    }

    func privacyOf(_ o: JSONObject) -> Privacy {
        var p = Privacy()
        p.profileInfo = Self.audienceOf(Json.string(o, "profileInfo"))
        p.followLists = Self.audienceOf(Json.string(o, "followLists"))
        p.messages = Self.audienceOf(Json.string(o, "messages"))
        p.comments = Self.audienceOf(Json.string(o, "comments"))
        p.showOnline = Json.bool(o, "showOnline", true)
        p.showBirthday = Json.bool(o, "showBirthday", true)
        p.closedProfile = Json.bool(o, "closedProfile", false)
        p.personalRecs = Json.bool(o, "personalRecs", true)
        p.calls = Self.audienceOf(Json.string(o, "calls"))
        p.hiddenWords = Json.strings(o, "hiddenWords")
        p.hideOffensive = Json.bool(o, "hideOffensive", true)
        p.hideSpam = Json.bool(o, "hideSpam", true)
        return p
    }
}
