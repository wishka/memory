import Foundation

/// Откуда читать загружаемое: кусками, с любого места (для докачки).
protocol ByteSource {
    var size: Int64 { get }
    func read(_ offset: Int64, _ max: Int) throws -> Data
}

final class FileSource: ByteSource {
    private let url: URL

    init(_ url: URL) { self.url = url }

    var size: Int64 {
        let attrs = try? FileManager.default.attributesOfItem(atPath: url.path)
        return (attrs?[.size] as? NSNumber)?.int64Value ?? 0
    }

    func read(_ offset: Int64, _ max: Int) throws -> Data {
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        try handle.seek(toOffset: UInt64(offset))
        let left: Int64 = size - offset
        let n = Int(Swift.max(0, Swift.min(Int64(max), left)))
        if n == 0 { return Data() }
        return try handle.read(upToCount: n) ?? Data()
    }
}

final class BytesSource: ByteSource {
    private let bytes: Data

    init(_ bytes: Data) { self.bytes = bytes }

    var size: Int64 { Int64(bytes.count) }

    func read(_ offset: Int64, _ max: Int) throws -> Data {
        let start = Int(offset)
        let end = Swift.min(bytes.count, start + max)
        if start >= end { return Data() }
        return bytes.subdata(in: start..<end)
    }
}

/// Где на iPhone лежит файл загрузки: путь от корня («/…» — сжатая копия в
/// кэше, в тестах — что угодно) или имя вложения в папке MediaFiles.
enum UploadFiles {
    static func url(_ path: String) -> URL {
        if path.hasPrefix("/") { return URL(fileURLWithPath: path) }
        return MediaFiles.directory.appendingPathComponent(path)
    }

    static func source(_ path: String) -> ByteSource? {
        if path.isEmpty { return nil }
        let file = url(path)
        guard FileManager.default.fileExists(atPath: file.path) else { return nil }
        return FileSource(file)
    }

    /// Имя файла для сервера: «IMG_0001.jpg».
    static func name(_ path: String) -> String { (path as NSString).lastPathComponent }
}

enum UploadState: String, Equatable {
    /// В очереди: ждёт сжатия или своей очереди.
    case waiting
    case uploading
    /// Поставили на паузу сами.
    case paused
    case done
    /// Не вышло и повторять бессмысленно (файл пропал, сервер отказал).
    case failed

    static func of(_ id: String?) -> UploadState { id.flatMap { UploadState(rawValue: $0) } ?? .waiting }
}

/// Одна загрузка: файл вложения → сервер по tus. Переживает обрыв сети и
/// перезапуск приложения: адрес загрузки (url) и сколько уже ушло (offset)
/// лежат в кэше, а сервер на HEAD отвечает, с какого байта продолжать.
///
/// owner — ключ записи или сообщения в очереди отправки: она уйдёт, когда
/// загрузятся все её вложения. То же — в Uploads.kt.
struct UploadJob: Equatable, Identifiable {
    let id: String
    var attachmentId: String
    var owner: String
    /// Исходный файл: имя вложения в папке MediaFiles (или путь от корня).
    var path: String
    /// Что уходит на сервер — сжатая копия; "" — ещё не сжимали.
    var uploadPath: String = ""
    var video: Bool = false
    var size: Int64 = 0
    /// Адрес загрузки на сервере (tus Location); "" — ещё не создана.
    var url: String = ""
    var offset: Int64 = 0
    var state: UploadState = .waiting
    var error: String = ""
    var createdAt: Int64 = 0
    /// Попыток подряд без успеха — для паузы перед следующей.
    var attempts: Int = 0
    /// Раньше этого времени не пробовать (мс).
    var nextAt: Int64 = 0

    var progress: Float {
        if size <= 0 { return 0 }
        let p = Float(offset) / Float(size)
        return min(max(p, 0), 1)
    }

    func json() -> JSONObject {
        var o: JSONObject = [:]
        o["id"] = id
        o["attachment"] = attachmentId
        o["owner"] = owner
        o["path"] = path
        o["uploadPath"] = uploadPath
        o["video"] = video
        o["size"] = size
        o["url"] = url
        o["offset"] = offset
        o["state"] = state.rawValue
        o["error"] = error
        o["createdAt"] = createdAt
        o["attempts"] = attempts
        o["nextAt"] = nextAt
        return o
    }

    static func of(_ o: JSONObject) -> UploadJob? {
        guard let id = o["id"] as? String else { return nil }
        var j = UploadJob(id: id, attachmentId: Json.string(o, "attachment"), owner: Json.string(o, "owner"), path: Json.string(o, "path"))
        j.uploadPath = Json.string(o, "uploadPath")
        j.video = Json.bool(o, "video")
        j.size = Json.long(o, "size")
        j.url = Json.string(o, "url")
        j.offset = Json.long(o, "offset")
        j.state = UploadState.of(Json.string(o, "state"))
        j.error = Json.string(o, "error")
        j.createdAt = Json.long(o, "createdAt")
        j.attempts = Json.int(o, "attempts")
        j.nextAt = Json.long(o, "nextAt")
        return j
    }
}

/// Клиент tus 1.0 (tus.io): создать загрузку, узнать смещение, дослать
/// кусок. Готовые серверы — tusd, а также tus-node-server и др.
final class TusClient {
    static let version = "1.0.0"
    /// 1 МБ: на плохой сети теряем немного, на хорошей — мало запросов.
    static let defaultChunk = 1 << 20

    private let api: Api
    let chunkSize: Int

    init(api: Api, chunkSize: Int = TusClient.defaultChunk) {
        self.api = api
        self.chunkSize = chunkSize
    }

    func create(_ size: Int64, name: String, type: String) async throws -> String {
        let meta = "filename \(b64(name)),filetype \(b64(type))"
        var headers: [String: String] = [:]
        headers["Tus-Resumable"] = Self.version
        headers["Upload-Length"] = String(size)
        headers["Upload-Metadata"] = meta
        let r = try await api.call(HttpRequest(method: "POST", path: "/v1/uploads", headers: headers))
        guard let location = r.header("Location") else { throw NetError(.badData, code: r.code, message: "Нет Location") }
        return location
    }

    func offset(_ url: String) async throws -> Int64 {
        let headers: [String: String] = ["Tus-Resumable": Self.version, "Cache-Control": "no-store"]
        let r = try await api.call(HttpRequest(method: "HEAD", path: url, headers: headers))
        guard let raw = r.header("Upload-Offset"), let value = Int64(raw) else {
            throw NetError(.badData, code: r.code, message: "Нет Upload-Offset")
        }
        return value
    }

    func patch(_ url: String, _ offset: Int64, _ bytes: Data) async throws -> Int64 {
        let headers: [String: String] = ["Tus-Resumable": Self.version, "Upload-Offset": String(offset)]
        let request = HttpRequest(method: "PATCH", path: url, headers: headers, body: bytes, contentType: "application/offset+octet-stream")
        let r = try await api.call(request)
        if let raw = r.header("Upload-Offset"), let value = Int64(raw) { return value }
        return offset + Int64(bytes.count)
    }

    /// Досылать, пока не кончится файл. save — после каждого куска (смещение
    /// в кэш: оборвётся — продолжим отсюда). Ошибка сети — NetError наружу,
    /// сохранённое уже на месте.
    func upload(_ job: UploadJob, _ source: ByteSource, _ save: (UploadJob) throws -> Void) async throws -> UploadJob {
        var j = job
        j.size = source.size
        j.state = .uploading
        if j.url.isEmpty {
            let name = UploadFiles.name(j.uploadPath.isEmpty ? j.path : j.uploadPath)
            j.url = try await create(source.size, name: name, type: j.video ? "video/mp4" : "image/jpeg")
            j.offset = 0
            try save(j)
        } else {
            // Сервер знает лучше, сколько дошло (последний кусок мог дойти без ответа)
            let known: Int64
            do {
                known = try await offset(j.url)
            } catch let e as NetError {
                // Загрузку на сервере уже убрали (истёк срок) — начать заново
                if e.code == 404 || e.code == 410 {
                    j.url = ""
                    j.offset = 0
                    return try await upload(j, source, save)
                }
                throw e
            }
            j.offset = known
            try save(j)
        }
        while j.offset < source.size {
            let chunk = try source.read(j.offset, chunkSize)
            let next: Int64
            do {
                next = try await patch(j.url, j.offset, chunk)
            } catch let e as NetError {
                // Разошлись в смещении — спросить сервер и продолжить с его места
                if e.kind != .conflict { throw e }
                next = try await offset(j.url)
            }
            j.offset = next
            try save(j)
        }
        j.state = .done
        j.error = ""
        j.attempts = 0
        j.nextAt = 0
        return j
    }

    private func b64(_ s: String) -> String { Data(s.utf8).base64EncodedString() }
}

/// Очередь загрузок: хранится в кэше, идёт по одной, с паузой и отменой.
/// prepare — сжать перед отправкой (на iPhone — MediaCompressor);
/// source — чем читать файл.
final class Uploads {
    private let cache: OfflineCache
    private let tus: TusClient
    private let clock: () -> Int64
    private let prepare: (UploadJob) async -> UploadJob
    private let source: (String) -> ByteSource?

    init(
        cache: OfflineCache,
        tus: TusClient,
        clock: @escaping () -> Int64,
        prepare: @escaping (UploadJob) async -> UploadJob = { $0 },
        source: @escaping (String) -> ByteSource? = { UploadFiles.source($0) }
    ) {
        self.cache = cache
        self.tus = tus
        self.clock = clock
        self.prepare = prepare
        self.source = source
    }

    private struct Paused: Error {}

    private func key(_ id: String) -> String { CacheKeys.upload + id }

    private func decode(_ entry: CacheEntry?) -> UploadJob? {
        guard let entry, let o = Json.parse(entry.value) else { return nil }
        return UploadJob.of(o)
    }

    func all() -> [UploadJob] {
        cache.keys(CacheKeys.upload).compactMap { decode(cache.get($0)) }
    }

    func get(_ id: String) -> UploadJob? { decode(cache.get(key(id))) }

    func save(_ job: UploadJob) {
        cache.put(key(job.id), Json.string(job.json()), at: clock())
    }

    func add(_ job: UploadJob) {
        var j = job
        if j.createdAt == 0 { j.createdAt = clock() }
        save(j)
    }

    func pause(_ id: String) {
        guard var j = get(id), j.state == .waiting || j.state == .uploading else { return }
        j.state = .paused
        save(j)
    }

    func resume(_ id: String) {
        guard var j = get(id), j.state == .paused || j.state == .failed else { return }
        j.state = .waiting
        j.attempts = 0
        j.nextAt = 0
        j.error = ""
        save(j)
    }

    func remove(_ id: String) {
        cache.remove(key(id))
    }

    /// Готовые вложения владельца: attachmentId → адрес на сервере. nil — ещё не все готовы.
    func doneFor(_ owner: String) -> [String: String]? {
        let mine = all().filter { $0.owner == owner }
        if mine.contains(where: { $0.state != .done }) { return nil }
        var out: [String: String] = [:]
        for j in mine { out[j.attachmentId] = j.url }
        return out
    }

    /// Что-то у владельца не загрузится никогда.
    func failedFor(_ owner: String) -> Bool {
        all().contains { $0.owner == owner && $0.state == .failed }
    }

    /// Убрать готовые загрузки владельца — после того как запись ушла.
    func clearOwner(_ owner: String) {
        for j in all() where j.owner == owner { remove(j.id) }
    }

    /// Одна следующая загрузка. Ответ — что с ней стало (nil — делать нечего).
    /// Нет сети — загрузка ждёт с паузой (1, 2, 4… с), Paused не трогаем.
    @discardableResult
    func runOnce(onProgress: (UploadJob) -> Void = { _ in }) async -> UploadJob? {
        let now = clock()
        // «Шла» во время прошлого запуска приложения — значит, оборвалась
        let ready = all().first { ($0.state == .waiting || $0.state == .uploading) && $0.nextAt <= now }
        guard let job = ready else { return nil }
        var j = job
        do {
            if j.uploadPath.isEmpty {
                j = await prepare(j)
                if j.uploadPath.isEmpty { j.uploadPath = j.path }
                save(j)
            }
            guard let src = source(j.uploadPath) else {
                var failed = j
                failed.state = .failed
                failed.error = "Файл не найден"
                save(failed)
                return failed
            }
            let done = try await tus.upload(j, src) { step in
                // Пока качали, могли поставить на паузу или отменить
                let current = get(step.id)
                if current == nil || current?.state == .paused { throw Paused() }
                save(step)
                onProgress(step)
            }
            save(done)
            onProgress(done)
            return done
        } catch is Paused {
            return get(j.id)
        } catch {
            if error is CancellationError { return get(j.id) }
            let err = NetError.of(error)
            guard var next = get(j.id) else { return nil }
            if err.retryable {
                next.state = .waiting
                next.nextAt = now + Backoff.delayMs(next.attempts, retryAfterMs: err.retryAfterMs)
                next.attempts += 1
                next.error = err.kind.rawValue
            } else {
                next.state = .failed
                next.error = err.message
            }
            save(next)
            onProgress(next)
            return next
        }
    }
}
