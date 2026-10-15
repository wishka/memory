import Foundation

/// Запрос к серверу Memory. path — от корня API («/v1/feed») или полный
/// адрес (Location загрузки tus), query — параметры строки запроса. Тело —
/// байты: JSON или кусок файла (tus). То же — в Http.kt.
struct HttpRequest {
    var method: String
    var path: String
    var query: [String: String] = [:]
    var headers: [String: String] = [:]
    var body: Data? = nil
    var contentType: String? = nil

    func header(_ name: String) -> String? {
        headers.first { $0.key.caseInsensitiveCompare(name) == .orderedSame }?.value
    }
}

/// Ответ сервера: код, заголовки, тело.
struct HttpResponse {
    let code: Int
    var headers: [String: String] = [:]
    var body: Data = Data()

    var ok: Bool { (200...299).contains(code) }
    var text: String { String(decoding: body, as: UTF8.self) }

    func header(_ name: String) -> String? {
        headers.first { $0.key.caseInsensitiveCompare(name) == .orderedSame }?.value
    }
}

/// Как запрос доходит до сервера. В приложении — URLSession
/// (URLSessionTransport) или встроенный демо-сервер (DemoServer), в тестах —
/// тоже демо-сервер. Нет сети — URLError.
protocol Transport: AnyObject {
    func send(_ request: HttpRequest) async throws -> HttpResponse
}

/// Что пошло не так при обращении к серверу.
struct NetError: Error, Equatable {
    /// Имена — как у Android (NetError.Kind): так они лежат в очереди и загрузках.
    enum Kind: String {
        case offline = "Offline"
        case timeout = "Timeout"
        case server = "Server"
        case client = "Client"
        case auth = "Auth"
        case conflict = "Conflict"
        case badData = "BadData"
    }

    let kind: Kind
    let code: Int
    let message: String
    let retryAfterMs: Int64

    init(_ kind: Kind, code: Int = 0, message: String = "", retryAfterMs: Int64 = 0) {
        self.kind = kind
        self.code = code
        self.message = message.isEmpty ? kind.rawValue : message
        self.retryAfterMs = retryAfterMs
    }

    /// Можно ли повторить позже: сеть, таймаут, сбой сервера, «слишком часто».
    var retryable: Bool {
        kind == .offline || kind == .timeout || kind == .server || code == 429 || code == 408
    }

    static func of(_ response: HttpResponse) -> NetError {
        let kind: Kind
        switch response.code {
        case 401, 403: kind = .auth
        case 409: kind = .conflict
        case 408: kind = .timeout
        case 500...599: kind = .server
        default: kind = .client
        }
        var retryAfter: Int64 = 0
        if let raw = response.header("Retry-After"), let seconds = Int64(raw.trimmingCharacters(in: .whitespaces)) {
            retryAfter = seconds * 1000
        }
        let text = String(response.text.prefix(200))
        return NetError(kind, code: response.code, message: text, retryAfterMs: retryAfter)
    }

    static func of(_ error: Error) -> NetError {
        if let e = error as? NetError { return e }
        if let u = error as? URLError {
            if u.code == .timedOut { return NetError(.timeout, message: u.localizedDescription) }
            return NetError(.offline, message: u.localizedDescription)
        }
        return NetError(.badData, message: String(describing: error))
    }
}

/// Когда повторять: 1, 2, 4, 8… с, но не реже раза в 5 минут; плюс
/// случайный разброс до 20 %, чтобы тысячи телефонов после сбоя не
/// постучались в одну и ту же секунду. Retry-After сервера — главнее.
enum Backoff {
    static let baseMs: Int64 = 1_000
    static let maxMs: Int64 = 5 * 60_000

    static func delayMs(_ attempt: Int, jitter: Double = 0, retryAfterMs: Int64 = 0) -> Int64 {
        if retryAfterMs > 0 { return min(retryAfterMs, maxMs) }
        let shift = Int64(min(max(attempt, 0), 20))
        let exp: Int64 = baseMs << shift
        let capped: Int64 = min(exp, maxMs)
        let spread: Double = 1.0 + 0.2 * min(max(jitter, 0), 1)
        let scaled: Double = Double(capped) * spread
        return min(Int64(scaled), maxMs)
    }
}

/// Страница списка: элементы и курсор следующей; next == nil — дальше ничего.
struct Page<T> {
    let items: [T]
    let next: String?
}

/// JSON как словарь — тот же формат, что у Android (org.json).
typealias JSONObject = [String: Any]

/// Чтение и запись JSON-словарей с запасом, как optLong/optString на Android.
enum Json {
    static func parse(_ data: Data?) -> JSONObject? {
        guard let data, !data.isEmpty,
              let any = try? JSONSerialization.jsonObject(with: data, options: []) else { return nil }
        return any as? JSONObject
    }

    static func parse(_ text: String) -> JSONObject? { parse(Data(text.utf8)) }

    static func data(_ value: Any) -> Data {
        guard JSONSerialization.isValidJSONObject(value),
              let data = try? JSONSerialization.data(withJSONObject: value, options: []) else { return Data("{}".utf8) }
        return data
    }

    static func string(_ value: Any) -> String { String(decoding: data(value), as: UTF8.self) }

    static func isNull(_ o: JSONObject, _ key: String) -> Bool {
        guard let v = o[key] else { return true }
        return v is NSNull
    }

    static func long(_ o: JSONObject, _ key: String, _ fallback: Int64 = 0) -> Int64 {
        if let n = o[key] as? NSNumber { return n.int64Value }
        if let s = o[key] as? String, let v = Int64(s) { return v }
        return fallback
    }

    static func int(_ o: JSONObject, _ key: String, _ fallback: Int = 0) -> Int {
        Int(truncatingIfNeeded: long(o, key, Int64(fallback)))
    }

    static func double(_ o: JSONObject, _ key: String, _ fallback: Double = 0) -> Double {
        if let n = o[key] as? NSNumber { return n.doubleValue }
        if let s = o[key] as? String, let v = Double(s) { return v }
        return fallback
    }

    /// fallback — если поля нет (как optBoolean(key, fallback) на Android).
    static func bool(_ o: JSONObject, _ key: String, _ fallback: Bool = false) -> Bool {
        if let n = o[key] as? NSNumber { return n.boolValue }
        if let s = o[key] as? String { return s.lowercased() == "true" }
        return fallback
    }

    static func string(_ o: JSONObject, _ key: String, _ fallback: String = "") -> String {
        if let s = o[key] as? String { return s }
        if let n = o[key] as? NSNumber { return n.stringValue }
        return fallback
    }

    static func object(_ o: JSONObject, _ key: String) -> JSONObject? { o[key] as? JSONObject }

    static func objects(_ o: JSONObject, _ key: String) -> [JSONObject] {
        guard let arr = o[key] as? [Any] else { return [] }
        return arr.compactMap { $0 as? JSONObject }
    }

    static func strings(_ o: JSONObject, _ key: String) -> [String] {
        guard let arr = o[key] as? [Any] else { return [] }
        return arr.compactMap { $0 as? String }
    }

    static func ints(_ o: JSONObject, _ key: String) -> [Int] {
        guard let arr = o[key] as? [Any] else { return [] }
        return arr.compactMap { ($0 as? NSNumber)?.intValue }
    }

    /// Миллисекунды с 1970 — так время лежит в JSON у Android; nil — 0.
    static func ms(_ date: Date?) -> Int64 {
        guard let date else { return 0 }
        let value: Double = (date.timeIntervalSince1970 * 1000).rounded()
        return Int64(value)
    }

    /// 0 — «нет времени».
    static func date(_ ms: Int64) -> Date? {
        if ms == 0 { return nil }
        return Date(timeIntervalSince1970: Double(ms) / 1000)
    }
}
