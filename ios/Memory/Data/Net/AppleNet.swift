import Foundation
import Network
import SQLite3

/// Запросы к настоящему серверу — URLSession. baseURL — API_URL из
/// Secrets.plist (в CI — секрет). То же — OkHttpTransport в AndroidNet.kt.
final class URLSessionTransport: Transport {
    private let base: String
    private let session: URLSession

    init(baseURL: String, session: URLSession = URLSessionTransport.defaultSession) {
        var trimmed = baseURL.trimmingCharacters(in: .whitespacesAndNewlines)
        while trimmed.hasSuffix("/") { trimmed.removeLast() }
        self.base = trimmed
        self.session = session
    }

    static let defaultSession: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        // Кусок загрузки 1 МБ на плохой сети может идти долго
        config.timeoutIntervalForResource = 120
        config.waitsForConnectivity = false
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        return URLSession(configuration: config)
    }()

    func send(_ request: HttpRequest) async throws -> HttpResponse {
        // Location загрузки tus может быть полным адресом
        let absolute: Bool = request.path.hasPrefix("http://") || request.path.hasPrefix("https://")
        let full: String = absolute ? request.path : base + request.path
        guard var components = URLComponents(string: full) else { throw URLError(.badURL) }
        if !request.query.isEmpty {
            var items = components.queryItems ?? []
            for key in request.query.keys.sorted() {
                items.append(URLQueryItem(name: key, value: request.query[key]))
            }
            components.queryItems = items
        }
        guard let url = components.url else { throw URLError(.badURL) }
        var r = URLRequest(url: url)
        r.httpMethod = request.method
        for (key, value) in request.headers { r.setValue(value, forHTTPHeaderField: key) }
        if let type = request.contentType { r.setValue(type, forHTTPHeaderField: "Content-Type") }
        let needsBody = request.method == "POST" || request.method == "PUT" || request.method == "PATCH"
        if let body = request.body {
            r.httpBody = body
        } else if needsBody {
            r.httpBody = Data()
        }
        let (data, response) = try await session.data(for: r)
        guard let http = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
        var headers: [String: String] = [:]
        for (key, value) in http.allHeaderFields {
            guard let name = key as? String else { continue }
            headers[name] = "\(value)"
        }
        return HttpResponse(code: http.statusCode, headers: headers, body: data)
    }
}

/// Офлайн-кэш в SQLite: одна таблица «ключ — значение — когда — порядок».
/// Своя база на каждый аккаунт (net-<логин>.sqlite в Application Support).
/// Та же схема, что SqliteCache в AndroidNet.kt.
final class SqliteCache: OfflineCache {
    private var db: OpaquePointer?
    private let lock = NSLock()
    /// Строка до всех ключей с приставкой (как у Android): приставка + U+FFFF.
    private static let top = "\u{FFFF}"
    private static let transient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

    init?(url: URL) {
        try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        var handle: OpaquePointer?
        let flags = SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX
        guard sqlite3_open_v2(url.path, &handle, flags, nil) == SQLITE_OK, let handle else {
            if let handle { sqlite3_close(handle) }
            return nil
        }
        db = handle
        exec("CREATE TABLE IF NOT EXISTS kv (k TEXT PRIMARY KEY, v TEXT NOT NULL, at INTEGER NOT NULL, seq INTEGER NOT NULL)")
        exec("CREATE INDEX IF NOT EXISTS kv_seq ON kv(seq)")
        try? FileManager.default.setAttributes(
            [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication], ofItemAtPath: url.path
        )
    }

    deinit {
        if let handle = db { sqlite3_close(handle) }
    }

    /// Закрыть базу (перед удалением файла аккаунта). Дальше — пусто, без ошибок.
    func close() {
        lock.lock()
        defer { lock.unlock() }
        if let handle = db { sqlite3_close(handle) }
        db = nil
    }

    /// Файл базы аккаунта.
    static func url(login: String) -> URL {
        let safe = login.filter { $0.isLetter || $0.isNumber || $0 == "." || $0 == "_" }
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("net-\(safe).sqlite")
    }

    // MARK: Обёртки над C API

    private func exec(_ sql: String) {
        sqlite3_exec(db, sql, nil, nil, nil)
    }

    private enum Bind {
        case text(String)
        case int(Int64)
    }

    /// Выполнить запрос; row — для каждой строки результата.
    @discardableResult
    private func run(_ sql: String, _ binds: [Bind], row: (OpaquePointer) -> Void = { _ in }) -> Bool {
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK, let stmt else { return false }
        defer { sqlite3_finalize(stmt) }
        for (i, bind) in binds.enumerated() {
            let index = Int32(i + 1)
            switch bind {
            case .text(let s): sqlite3_bind_text(stmt, index, s, -1, Self.transient)
            case .int(let v): sqlite3_bind_int64(stmt, index, v)
            }
        }
        while true {
            let rc = sqlite3_step(stmt)
            if rc == SQLITE_ROW {
                row(stmt)
                continue
            }
            return rc == SQLITE_DONE
        }
    }

    private static func text(_ stmt: OpaquePointer, _ column: Int32) -> String {
        guard let raw = sqlite3_column_text(stmt, column) else { return "" }
        return String(cString: raw)
    }

    // MARK: OfflineCache

    func get(_ key: String) -> CacheEntry? {
        lock.lock()
        defer { lock.unlock() }
        var found: CacheEntry?
        run("SELECT v, at FROM kv WHERE k = ?", [.text(key)]) { stmt in
            found = CacheEntry(value: Self.text(stmt, 0), savedAt: sqlite3_column_int64(stmt, 1))
        }
        return found
    }

    func put(_ key: String, _ value: String, at: Int64) {
        lock.lock()
        defer { lock.unlock() }
        // База закрыта (аккаунт удаляют) — sqlite3_changes(NULL) уронил бы приложение
        guard db != nil else { return }
        exec("BEGIN IMMEDIATE")
        run("UPDATE kv SET v = ?, at = ? WHERE k = ?", [.text(value), .int(at), .text(key)])
        if sqlite3_changes(db) == 0 {
            // Порядок добавления — для очереди: что раньше встало, то раньше уйдёт
            run("INSERT INTO kv (k, v, at, seq) VALUES (?, ?, ?, (SELECT IFNULL(MAX(seq), 0) + 1 FROM kv))",
                [.text(key), .text(value), .int(at)])
        }
        exec("COMMIT")
    }

    func remove(_ key: String) {
        lock.lock()
        defer { lock.unlock() }
        run("DELETE FROM kv WHERE k = ?", [.text(key)])
    }

    func keys(_ prefix: String) -> [String] {
        lock.lock()
        defer { lock.unlock() }
        var out: [String] = []
        run("SELECT k FROM kv WHERE k >= ? AND k < ? ORDER BY seq", [.text(prefix), .text(prefix + Self.top)]) { stmt in
            out.append(Self.text(stmt, 0))
        }
        return out
    }

    func sizeBytes() -> Int64 {
        lock.lock()
        defer { lock.unlock() }
        var total: Int64 = 0
        let sql = "SELECT IFNULL(SUM(LENGTH(k) + LENGTH(v)), 0) FROM kv WHERE k >= ? AND k < ?"
        run(sql, [.text(CacheKeys.page), .text(CacheKeys.page + Self.top)]) { stmt in
            total = sqlite3_column_int64(stmt, 0)
        }
        return total * 2
    }

    func clearPages() {
        lock.lock()
        defer { lock.unlock() }
        run("DELETE FROM kv WHERE k >= ? AND k < ?", [.text(CacheKeys.page), .text(CacheKeys.page + Self.top)])
    }
}

/// Есть ли сеть и бесплатная ли она (не сотовая, не «экономия данных»): от
/// этого — «нет сети» в интерфейсе, когда очереди снова пробовать и можно ли
/// слать оригиналы. То же — NetMonitor в AndroidNet.kt.
final class NetMonitor {
    static let shared = NetMonitor()

    private let lock = NSLock()
    private let queue = DispatchQueue(label: "memory.net-monitor")
    private var monitor: NWPathMonitor?
    private var _online = true
    private var _unmetered = false

    /// Сеть появилась или пропала — на главной очереди.
    var onChange: ((Bool) -> Void)?

    var online: Bool {
        lock.lock()
        defer { lock.unlock() }
        return _online
    }

    var unmetered: Bool {
        lock.lock()
        defer { lock.unlock() }
        return _unmetered
    }

    func start() {
        guard monitor == nil else { return }
        // NWPathMonitor после cancel() не перезапускается — каждый раз новый
        let m = NWPathMonitor()
        m.pathUpdateHandler = { [weak self] path in self?.apply(path) }
        monitor = m
        m.start(queue: queue)
    }

    func stop() {
        monitor?.cancel()
        monitor = nil
    }

    private func apply(_ path: NWPath) {
        let isOnline = path.status == .satisfied
        let free = isOnline && !path.isExpensive && !path.isConstrained
        lock.lock()
        let changed = _online != isOnline
        _online = isOnline
        _unmetered = free
        lock.unlock()
        guard changed else { return }
        DispatchQueue.main.async { [weak self] in self?.onChange?(isOnline) }
    }
}

/// Собрать сеть для вошедшего аккаунта.
enum NetSetup {
    /// Адрес сервера из Secrets.plist (API_URL); "" — нет.
    static var apiURL: String {
        guard let url = Bundle.main.url(forResource: "Secrets", withExtension: "plist"),
              let dict = NSDictionary(contentsOf: url),
              let value = dict["API_URL"] as? String else { return "" }
        return value.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Сервер настоящий, если в сборке есть адрес; иначе — демо на телефоне.
    static var usesDemo: Bool { apiURL.isEmpty }

    /// Для «Данных и сети»: «api.example.com» или пусто (демо).
    static var serverHost: String {
        let raw = apiURL
        guard !raw.isEmpty else { return "" }
        return URLComponents(string: raw)?.host ?? raw
    }

    /// Удалили аккаунт — стереть его базу сети, демо-сервер и сжатые копии.
    /// Базу перед этим закрыть (SqliteCache.close). То же — NetSetup.deleteAccount в AndroidNet.kt.
    static func deleteAccount(login: String) {
        let fm = FileManager.default
        let safe = login.filter { $0.isLetter || $0.isNumber || $0 == "." || $0 == "_" }
        let db = SqliteCache.url(login: login)
        for suffix in ["", "-journal", "-wal", "-shm"] {
            try? fm.removeItem(at: URL(fileURLWithPath: db.path + suffix))
        }
        let caches = fm.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        try? fm.removeItem(at: caches.appendingPathComponent("demo-server", isDirectory: true).appendingPathComponent(safe, isDirectory: true))
        try? fm.removeItem(at: caches.appendingPathComponent("uploads", isDirectory: true))
    }

    static func forAccount(
        login: String,
        me: @escaping () -> User,
        clock: @escaping () -> Int64,
        quality: @escaping () -> MediaQuality,
        unmetered: @escaping () -> Bool
    ) -> Net {
        let safe = login.filter { $0.isLetter || $0.isNumber || $0 == "." || $0 == "_" }
        // База не открылась (нет места, испорчена) — хотя бы в памяти, чтобы приложение работало
        let cache: OfflineCache
        if let sqlite = SqliteCache(url: SqliteCache.url(login: login)) {
            cache = sqlite
        } else {
            cache = MemoryCache()
        }
        let transport: Transport
        if usesDemo {
            // Демо-сервер живёт, пока открыто приложение: его загрузки — во временной папке
            let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            let dir = caches.appendingPathComponent("demo-server", isDirectory: true).appendingPathComponent(safe, isDirectory: true)
            try? FileManager.default.removeItem(at: dir)
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            transport = DemoServer(me: me, clock: clock, dir: dir)
        } else {
            transport = URLSessionTransport(baseURL: apiURL)
        }
        return Net(
            transport: transport, cache: cache, clock: clock, me: me,
            prepare: { job in await MediaCompressor.prepare(job, quality: quality(), wifi: unmetered()) }
        )
    }
}
