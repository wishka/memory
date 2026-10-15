import Foundation

/// Что считает статистика использования — только эти события, без
/// текстов, имён и адресов: «опубликовали запись», а не что в ней.
enum AppEvent: String, CaseIterable {
    case appOpen = "app_open"
    case signUp = "sign_up"
    case recoveryUsed = "recovery_used"
    case postPublished = "post_published"
    case postEdited = "post_edited"
    case commentSent = "comment_sent"
    case messageSent = "message_sent"
    case prismPublished = "prism_published"
    case searchUsed = "search_used"
    case tagOpened = "tag_opened"
    case activityOpened = "activity_opened"
    case linkOpened = "link_opened"
    case linkShared = "link_shared"
    case dataExported = "data_exported"
    case liveStarted = "live_started"
    case liveWatched = "live_watched"
    case secretChat = "secret_chat"
    case disappearing = "disappearing_set"
    case storyPublished = "story_published"
    case reposted = "reposted"
    case voiceCall = "voice_call"
    case videoCall = "video_call"

    var id: String { rawValue }
}

/// Отчёт о сбое: когда, какая ошибка и где — без данных человека (см. Scrub).
struct CrashReport: Equatable {
    let at: Date
    let type: String
    let message: String
    let stack: String

    /// Первая строка — для «Последний сбой: …».
    var title: String {
        let short = type.split(separator: ".").last.map(String.init) ?? type
        return short + (message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "" : ": " + String(message.prefix(80)))
    }

    func text() -> String { "\(type): \(message)\n\(stack)" }

    /// Ошибка Swift: тип — с модулем («Memory.StoreError»), текст — без данных человека.
    static func of(_ error: Error, at: Date) -> CrashReport {
        let typeName = String(reflecting: Swift.type(of: error))
        let message: String
        if let described = error as? CustomStringConvertible {
            message = described.description
        } else {
            message = (error as NSError).localizedDescription
        }
        return CrashReport(at: at, type: typeName, message: Scrub.text(oneLine(message)),
                           stack: Scrub.text(Thread.callStackSymbols.dropFirst().prefix(60).joined(separator: "\n")))
    }

    /// Необработанное исключение Objective-C.
    static func of(exception: NSException, at: Date) -> CrashReport {
        CrashReport(at: at, type: exception.name.rawValue, message: Scrub.text(oneLine(exception.reason ?? "")),
                    stack: Scrub.text(exception.callStackSymbols.prefix(60).joined(separator: "\n")))
    }

    /// Разобрать сохранённый отчёт (text()).
    static func parse(_ at: Date, _ raw: String) -> CrashReport {
        let first: String
        let stack: String
        if let nl = raw.firstIndex(of: "\n") {
            first = String(raw[..<nl])
            stack = String(raw[raw.index(after: nl)...])
        } else {
            first = raw
            stack = ""
        }
        if let sep = first.range(of: ": ") {
            return CrashReport(at: at, type: String(first[..<sep.lowerBound]), message: String(first[sep.upperBound...]), stack: stack)
        }
        return CrashReport(at: at, type: first, message: "", stack: stack)
    }

    private static func oneLine(_ s: String) -> String {
        s.replacingOccurrences(of: "\r", with: " ").replacingOccurrences(of: "\n", with: " ")
    }
}

/// Куда уходят отчёты и статистика. На iPhone — Sentry (TelemetrySetup),
/// если в сборке есть ключ; иначе никуда: всё остаётся на телефоне.
protocol TelemetrySink: AnyObject {
    func crash(_ report: CrashReport)
    func usage(day: String, counts: [String: Int])
    func breadcrumb(_ event: String)
}

/// Убрать из текста ошибки то, что может указать на человека: почту,
/// @логины, номера телефонов и длинные числа, ссылки с параметрами.
enum Scrub {
    private static let email = try! NSRegularExpression(pattern: "[A-Za-z0-9_.+-]+@[A-Za-z0-9_-]+\\.[A-Za-z0-9_.]+")
    private static let handle = try! NSRegularExpression(pattern: "(?<![A-Za-z0-9_])@[a-zA-Z0-9._]{3,}")
    private static let longNumber = try! NSRegularExpression(pattern: "\\+?[0-9][0-9 ()-]{6,}[0-9]")
    private static let query = try! NSRegularExpression(pattern: "(https?://[^\\s?]+)\\?[^\\s]*")
    private static let pathUser = try! NSRegularExpression(pattern: "(state|memory)-[a-z0-9._]+\\.(json|zip)")

    private static func replace(_ re: NSRegularExpression, _ s: String, _ template: String) -> String {
        re.stringByReplacingMatches(in: s, range: NSRange(location: 0, length: (s as NSString).length), withTemplate: template)
    }

    static func text(_ s: String) -> String {
        var out = replace(query, s, "$1?…")
        out = replace(email, out, "<email>")
        out = replace(handle, out, "@<login>")
        out = replace(longNumber, out, "<number>")
        out = replace(pathUser, out, "$1-<login>.$2")
        return out
    }
}

/// Отчёты о сбоях и статистика — только с согласия, отдельно на каждое.
/// Согласие хранится на телефоне (TelemetryPrefs); по умолчанию — выключено.
///
/// Сбой всегда записывается локально (последний отчёт виден в
/// «Приватности», его можно отправить вручную), а сам уходит только при
/// согласии на отчёты. То же — в Telemetry.kt.
enum Telemetry {
    private static let lock = NSLock()
    private static var allowCrashes = false
    private static var allowAnalytics = false
    private static var currentSink: TelemetrySink?
    private static var counts: [String: Int] = [:]

    static var crashesAllowed: Bool {
        get { lock.lock(); defer { lock.unlock() }; return allowCrashes }
        set { lock.lock(); allowCrashes = newValue; lock.unlock() }
    }

    static var analyticsAllowed: Bool {
        get { lock.lock(); defer { lock.unlock() }; return allowAnalytics }
        set { lock.lock(); allowAnalytics = newValue; lock.unlock() }
    }

    static var sink: TelemetrySink? {
        get { lock.lock(); defer { lock.unlock() }; return currentSink }
        set { lock.lock(); currentSink = newValue; lock.unlock() }
    }

    /// Событие для статистики: считается, только если человек разрешил.
    static func track(_ event: AppEvent) {
        lock.lock()
        guard allowAnalytics else { lock.unlock(); return }
        counts[event.id, default: 0] += 1
        let s = currentSink
        lock.unlock()
        s?.breadcrumb(event.id)
    }

    /// Накопленное с прошлого сохранения — и обнулить.
    static func drain() -> [String: Int] {
        lock.lock(); defer { lock.unlock() }
        let out = counts
        counts = [:]
        return out
    }

    /// Сбой: отправить, если разрешено. Ответ — ушёл ли он.
    @discardableResult
    static func crash(_ report: CrashReport) -> Bool {
        guard let s = sink, crashesAllowed else { return false }
        s.crash(report)
        return true
    }

    /// Отказ от статистики — накопленное стирается.
    static func forgetUsage() {
        lock.lock(); defer { lock.unlock() }
        counts = [:]
    }
}

/// Согласие и счётчики статистики на телефоне. Счётчики копятся за день и
/// уходят одним отчётом на следующий день: по одному событию ничего не
/// отправляется, и по времени нельзя восстановить, что человек делал.
final class TelemetryPrefs {
    private let store: KeyValueStore

    init(_ store: KeyValueStore) {
        self.store = store
    }

    var asked: Bool { store.get("tm.asked") == "1" }
    var crashes: Bool { store.get("tm.crashes") == "1" }
    var analytics: Bool { store.get("tm.analytics") == "1" }

    func set(crashes: Bool, analytics: Bool) {
        store.put("tm.asked", "1")
        store.put("tm.crashes", crashes ? "1" : "0")
        store.put("tm.analytics", analytics ? "1" : "0")
        if !analytics {
            store.remove("tm.day")
            store.remove("tm.counts")
        }
        apply()
    }

    /// Перенести согласие в Telemetry — при запуске и после изменения.
    func apply() {
        Telemetry.crashesAllowed = crashes
        Telemetry.analyticsAllowed = analytics
        if !analytics { Telemetry.forgetUsage() }
    }

    /// Сложить накопленное в Telemetry с сохранённым за день. Если день
    /// сменился — вернуть отчёт за прошлый день (его пора отправить).
    func roll(_ today: String) -> (day: String, counts: [String: Int])? {
        let fresh = Telemetry.drain()
        if !analytics { return nil }
        let day = store.get("tm.day")
        let saved = Self.decode(store.get("tm.counts") ?? "")
        if let day, day != today, !saved.isEmpty {
            store.put("tm.day", today)
            store.put("tm.counts", Self.encode(fresh))
            return (day, saved)
        }
        var merged = saved
        for (k, v) in fresh { merged[k, default: 0] += v }
        store.put("tm.day", today)
        store.put("tm.counts", Self.encode(merged))
        return nil
    }

    private static func encode(_ m: [String: Int]) -> String {
        m.sorted { $0.key < $1.key }.map { "\($0.key)=\($0.value)" }.joined(separator: ",")
    }

    private static func decode(_ s: String) -> [String: Int] {
        var out: [String: Int] = [:]
        for part in s.split(separator: ",") {
            guard let eq = part.firstIndex(of: "=") else { continue }
            out[String(part[..<eq])] = Int(part[part.index(after: eq)...]) ?? 0
        }
        return out
    }
}
