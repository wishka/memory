import CryptoKit
import Foundation

/// Что изменилось за один проход Net.pump — модели этого хватает, чтобы обновить экран.
struct PumpResult {
    let uploads: [UploadJob]
    /// Отправленные действия и ответ сервера (запись или сообщение с номером сервера).
    let sent: [(op: OutboxOp, answer: JSONObject?)]
    let failed: [OutboxOp]
    let offline: Bool
    /// Сервер не принял вход — нужно войти снова (очередь сохранена).
    var authLost: Bool = false
}

/// Всё сетевое вместе: API, очередь отправки, загрузки, кэш. Одна штука на
/// вошедший аккаунт. Пока сервера нет — поверх DemoServer; с сервером —
/// поверх URLSessionTransport (адрес — API_URL в Secrets.plist). То же — в Net.kt.
final class Net {
    let transport: Transport
    let cache: OfflineCache
    let api: Api
    let tus: TusClient
    let uploads: Uploads
    let outbox: Outbox
    /// Вход на сервер: токены в Связке ключей (SecureStore), без хранилища — в памяти.
    let session: Session
    /// Живой канал: сообщения, «печатает», «прочитано», состав групп.
    let realtime: Realtime
    private let clock: () -> Int64
    private let me: () -> User
    /// Скачанные чужие файлы (фото и видео записей и сообщений): та же папка,
    /// где их ищет MediaFiles.resolve («netmedia/…»).
    var mediaDir: URL { MediaFiles.netDirectory }

    /// tokens — где хранить токены входа (SecureStore); nil — только в памяти.
    /// realtime — живой канал; nil — демо (события от демо-сервера).
    /// tempDir — куда класть зашифрованные копии файлов секретных переписок.
    init(
        transport: Transport,
        cache: OfflineCache,
        clock: @escaping () -> Int64,
        me: @escaping () -> User,
        tokens: KeyValueStore? = nil,
        login: String = "",
        prepare: @escaping (UploadJob) async -> UploadJob = { $0 },
        source: @escaping (String) -> ByteSource? = { UploadFiles.source($0) },
        chunkSize: Int = TusClient.defaultChunk,
        realtime: Realtime? = nil,
        tempDir: URL = FileManager.default.temporaryDirectory
    ) {
        self.transport = transport
        self.cache = cache
        self.clock = clock
        self.me = me
        let session = Session(store: tokens, login: login)
        let api = Api(transport: transport, me: me, session: session)
        let tus = TusClient(api: api, chunkSize: chunkSize)
        self.session = session
        self.api = api
        self.tus = tus
        let encrypting: (UploadJob) async -> UploadJob = { job in
            let prepared = await prepare(job)
            return Net.encryptIfSecret(prepared, tempDir: tempDir)
        }
        self.uploads = Uploads(cache: cache, tus: tus, clock: clock, prepare: encrypting, source: source)
        self.outbox = Outbox(cache: cache, clock: clock)
        if let realtime {
            self.realtime = realtime
        } else {
            let demoChannel = DemoRealtime(clock: clock)
            (transport as? DemoServer)?.realtime = demoChannel
            self.realtime = demoChannel
        }
    }

    /// Демо: встроенный сервер и кэш в памяти — для тестов и пока нет аккаунта.
    static func demo(
        clock: @escaping () -> Int64,
        me: @escaping () -> User,
        cache: OfflineCache = MemoryCache(),
        chunkSize: Int = TusClient.defaultChunk,
        tokens: KeyValueStore? = nil,
        login: String = "demo"
    ) -> Net {
        let server = DemoServer(me: me, clock: clock)
        return Net(transport: server, cache: cache, clock: clock, me: me, tokens: tokens, login: login, chunkSize: chunkSize)
    }

    /// Секретная переписка: файл шифруется на телефоне (FileCrypto, MEMF1)
    /// после сжатия — сервер хранит только шифротекст.
    static func encryptIfSecret(_ job: UploadJob, tempDir: URL) -> UploadJob {
        if job.secretKey.isEmpty { return job }
        guard let key = FileKey.decode(job.secretKey) else { return job }
        let srcPath: String = job.uploadPath.isEmpty ? job.path : job.uploadPath
        let src = UploadFiles.url(srcPath)
        guard FileManager.default.fileExists(atPath: src.path) else { return job }
        let dst = tempDir.appendingPathComponent("\(job.id).memf")
        do {
            try FileCrypto.encryptFile(src, dst, key)
        } catch {
            // Не зашифровалось — открытым не отправляем: загрузка не найдёт файл и не уйдёт
            var broken = job
            broken.uploadPath = dst.path + ".missing"
            return broken
        }
        // Сжатая копия больше не нужна — уходит зашифрованная
        if !job.uploadPath.isEmpty && job.uploadPath != job.path {
            try? FileManager.default.removeItem(at: UploadFiles.url(job.uploadPath))
        }
        var out = job
        out.uploadPath = dst.path
        let attrs = try? FileManager.default.attributesOfItem(atPath: dst.path)
        out.size = (attrs?[.size] as? NSNumber)?.int64Value ?? 0
        return out
    }

    /// Встроенный демо-сервер, если работаем с ним (для «Данные и сеть»).
    var demo: DemoServer? { transport as? DemoServer }

    // MARK: - Файлы с сервера

    /// Файл лежит на сервере (а не на этом телефоне).
    func isRemote(_ path: String) -> Bool { path.contains("/v1/uploads/") }

    /// Где на телефоне будет копия файла с сервера.
    func mediaFile(_ path: String, ext: String = "") -> URL {
        let digest = SHA256.hash(data: Data(path.utf8))
        let hex: String = digest.map { String(format: "%02x", $0) }.joined()
        let clean: String = String(ext.filter { $0.isLetter || $0.isNumber || $0 == "." }.prefix(6))
        let name: String = String(hex.prefix(32)) + clean
        return mediaDir.appendingPathComponent(name)
    }

    /// Скачанная копия в состоянии: «netmedia/<имя>», без полного пути (MediaFiles.resolve).
    func mediaRef(_ file: URL) -> String { MediaFiles.netPrefix + file.lastPathComponent }

    /// Скачать файл с сервера (уже скачанный — сразу). key — файл секретной
    /// переписки: на сервере шифротекст, расшифровывается здесь.
    @discardableResult
    func fetchMedia(_ path: String, key: FileKey? = nil, ext: String = "") async throws -> URL {
        let f = mediaFile(path, ext: ext)
        let fm = FileManager.default
        let attrs = try? fm.attributesOfItem(atPath: f.path)
        let size: Int64 = (attrs?[.size] as? NSNumber)?.int64Value ?? 0
        if size > 0 { return f }
        let bytes = try await api.download(path)
        let plain: Data
        if let key {
            plain = try FileCrypto.decrypt(bytes, key)
        } else {
            plain = bytes
        }
        try? fm.createDirectory(at: mediaDir, withIntermediateDirectories: true)
        // Целиком во временный файл и замена: оборванная запись не оставит половину
        try plain.write(to: f, options: .atomic)
        return f
    }

    private func codec() -> ApiCodec { ApiCodec(me: me()) }

    func newKey() -> String { UUID().uuidString.lowercased() }

    /// Лента страницами: старое — с сервера и из кэша.
    func feedPager() -> Pager<Post> {
        let c = codec()
        let api = self.api
        return Pager<Post>(
            cacheKey: CacheKeys.feed,
            cache: cache,
            idOf: { $0.id },
            encode: { c.postJson($0) },
            decode: { c.postOf($0) },
            clock: clock,
            load: { cursor in try await api.feed(cursor) }
        )
    }

    // MARK: - Постановка в очередь

    /// Фото и видео записи/сообщения — в загрузки (стикеры и гифки — как есть, они маленькие).
    /// secretKeys — ключи файлов секретной переписки (attachmentId → FileKey.encoded):
    /// такие уходят любые (и документы, и голосовые) и только зашифрованными.
    private func queueMedia(_ owner: String, _ media: [Attachment], secretKeys: [String: String] = [:]) {
        for a in media {
            let secretKey: String = secretKeys[a.id] ?? ""
            let secret = !secretKey.isEmpty
            let visual = (a.kind == .photo || a.kind == .video) && !a.sticker
            if !(secret || visual) { continue }
            if a.fileName.isEmpty || a.fileName.hasPrefix("/v1/") { continue }
            var job = UploadJob(id: newKey(), attachmentId: a.id, owner: owner, path: a.fileName, video: a.kind == .video)
            job.secretKey = secretKey
            uploads.add(job)
        }
    }

    /// Новая запись: вложения — в загрузки, сама запись — в очередь (уйдёт после них).
    @discardableResult
    func queuePost(_ post: Post) -> String {
        let key = post.outboxKey.isEmpty ? newKey() : post.outboxKey
        queueMedia(key, post.media)
        var clean = post
        clean.outboxKey = ""
        let payload = Json.string(codec().postJson(clean))
        outbox.enqueue(OutboxOp(key: key, kind: .createPost, target: post.id, payload: payload))
        return key
    }

    func queueLike(_ postId: Int64, liked: Bool) {
        outbox.enqueue(OutboxOp(key: newKey(), kind: liked ? .like : .unlike, target: postId))
    }

    /// Сообщение в очередь. secretKeys — ключи файлов секретной переписки
    /// (attachmentId → FileKey.encoded): такие вложения шифруются перед загрузкой.
    @discardableResult
    func queueMessage(_ chatId: Int, _ message: Message, secretKeys: [String: String] = [:]) -> String {
        let key = newKey()
        queueMedia(key, message.attachments, secretKeys: secretKeys)
        var clean = message
        clean.pending = false
        let payload = Json.string(codec().messageJson(clean))
        outbox.enqueue(OutboxOp(key: key, kind: .sendMessage, target: Int64(chatId), payload: payload))
        return key
    }

    /// Любой запрос — в очередь: группы, «прочитано», профиль. coalesce —
    /// «важно только последнее» (например, "me" — профиль): прежний такой же снимается.
    @discardableResult
    func queueCall(_ method: String, _ path: String, body: JSONObject? = nil, coalesce: String? = nil) -> String {
        let key = newKey()
        var payload: JSONObject = [:]
        payload["method"] = method
        payload["path"] = path
        payload["body"] = body.map { $0 as Any } ?? NSNull()
        payload["coalesce"] = coalesce ?? ""
        outbox.enqueue(OutboxOp(key: key, kind: .call, target: 0, payload: Json.string(payload)))
        return key
    }

    /// Войти на сервер с этого телефона.
    func signIn(login: String, password: String, device: String, platform: String) async throws {
        try await api.signIn(login: login, password: password, device: device, platform: platform)
    }

    /// Снова вошли: что ждало из-за закрытого входа — пробовать сразу.
    func retryAuth() {
        let auth = NetError.Kind.auth.rawValue
        for op in outbox.all() where op.error == auth {
            outbox.retryNow(op.key)
        }
        for job in uploads.all() where job.state == .waiting && job.error == auth {
            var j = job
            j.nextAt = 0
            j.attempts = 0
            uploads.save(j)
        }
    }

    /// Вложения с путями на сервере вместо путей на телефоне.
    private func withRemote(_ media: [Attachment], _ remote: [String: String]) -> [Attachment] {
        media.map { a in
            guard let url = remote[a.id] else { return a }
            var x = a
            x.fileName = url
            return x
        }
    }

    private func send(_ op: OutboxOp) async throws -> JSONObject? {
        let c = codec()
        let remote = uploads.doneFor(op.key) ?? [:]
        switch op.kind {
        case .createPost:
            guard let o = Json.parse(op.payload), var post = c.postOf(o) else { throw NetError(.badData, message: "payload") }
            post.media = withRemote(post.media, remote)
            let saved = try await api.createPost(post, key: op.key)
            return c.postJson(saved)
        case .like, .unlike:
            try await api.like(op.target, liked: op.kind == .like, key: op.key)
            return nil
        case .sendMessage:
            guard let o = Json.parse(op.payload), var m = c.messageOf(o) else { throw NetError(.badData, message: "payload") }
            m.attachments = withRemote(m.attachments, remote)
            // Ответ сервера как есть: с номером и sentAt
            return try await api.sendRaw(Int(op.target), c.messageJson(m), key: op.key)
        case .call:
            guard let p = Json.parse(op.payload) else { throw NetError(.badData, message: "payload") }
            let method = Json.string(p, "method")
            let path = Json.string(p, "path")
            return try await api.send(method, path, body: Json.object(p, "body"), key: op.key)
        }
    }

    /// Один проход: догрузить, что можно (не больше maxUploads файлов за раз),
    /// потом отправить готовое из очереди. Нет сети — вернётся offline.
    func pump(maxUploads: Int = 3, onProgress: (UploadJob) -> Void = { _ in }) async -> PumpResult {
        var order: [String] = []
        var touched: [String: UploadJob] = [:]
        var offline = false
        var authLost = false
        for _ in 0..<maxUploads {
            let step = await uploads.runOnce { job in
                if touched[job.id] == nil { order.append(job.id) }
                touched[job.id] = job
                onProgress(job)
            }
            guard let j = step else { break }
            if touched[j.id] == nil { order.append(j.id) }
            touched[j.id] = j
            if j.state == .waiting && j.error == NetError.Kind.offline.rawValue {
                offline = true
                break
            }
            if j.state == .waiting && j.error == NetError.Kind.auth.rawValue {
                authLost = true
                break
            }
        }
        var sent: [(op: OutboxOp, answer: JSONObject?)] = []
        var failed: [OutboxOp] = []
        if !offline {
            let uploads = self.uploads
            let r = await outbox.flush(
                ready: { op in
                    let simple = op.kind == .like || op.kind == .unlike || op.kind == .call
                    return simple || uploads.doneFor(op.key) != nil
                },
                failedDeps: { op in uploads.failedFor(op.key) },
                send: { op in try await self.send(op) },
                onSent: { op, answer in
                    sent.append((op: op, answer: answer))
                    uploads.clearOwner(op.key)
                },
                onFailed: { op, _ in failed.append(op) }
            )
            offline = r.offline
            authLost = authLost || r.authLost
        }
        let jobs: [UploadJob] = order.compactMap { touched[$0] }
        return PumpResult(uploads: jobs, sent: sent, failed: failed, offline: offline, authLost: authLost)
    }

    /// Сеть вернулась: что ждало из-за «нет сети», пробуем сразу, без
    /// накопленной паузы (паузы после ошибок сервера — остаются).
    func wake() {
        let offline = NetError.Kind.offline.rawValue
        for op in outbox.all() where op.error == offline && op.nextAt > 0 {
            outbox.retryNow(op.key)
        }
        for job in uploads.all() where job.state == .waiting && job.error == offline {
            var j = job
            j.nextAt = 0
            j.attempts = 0
            uploads.save(j)
        }
    }

    /// Есть ли что делать: загрузки или очередь.
    var busy: Bool {
        if outbox.count > 0 { return true }
        return uploads.all().contains { $0.state == .waiting || $0.state == .uploading }
    }

    /// Ближайший момент, когда есть смысл пробовать снова (мс); 0 — сейчас.
    func nextAttemptAt() -> Int64 {
        var times: [Int64] = outbox.all().map { $0.nextAt }
        for job in uploads.all() where job.state == .waiting { times.append(job.nextAt) }
        return times.min() ?? 0
    }
}

/// Пинок фоновой отправке: что-то добавили в очередь или появилась сеть.
/// Пинки схлопываются (как Channel.CONFLATED на Android): пока никто не
/// ждёт, запоминается один. wait — до пинка или до таймаута.
final class NetKicker {
    private let lock = NSLock()
    private var pending = false
    private var waiter: CheckedContinuation<Void, Never>?
    private var generation = 0

    func kick() {
        lock.lock()
        if let w = waiter {
            waiter = nil
            lock.unlock()
            w.resume()
        } else {
            pending = true
            lock.unlock()
        }
    }

    func wait(timeoutMs: Int64) async {
        await withCheckedContinuation { (c: CheckedContinuation<Void, Never>) in
            begin(c, timeoutMs: timeoutMs)
        }
    }

    private func begin(_ c: CheckedContinuation<Void, Never>, timeoutMs: Int64) {
        lock.lock()
        if pending {
            pending = false
            lock.unlock()
            c.resume()
            return
        }
        // Прежний ждущий (не должно быть) — отпустить, чтобы не завис
        let previous = waiter
        generation += 1
        let gen = generation
        waiter = c
        lock.unlock()
        previous?.resume()
        let nanos = UInt64(max(timeoutMs, 0)) * 1_000_000
        Task { [weak self] in
            try? await Task.sleep(nanoseconds: nanos)
            self?.timeout(gen)
        }
    }

    private func timeout(_ gen: Int) {
        lock.lock()
        guard gen == generation, let w = waiter else {
            lock.unlock()
            return
        }
        waiter = nil
        lock.unlock()
        w.resume()
    }
}
