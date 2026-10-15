import Foundation
#if canImport(Sentry)
import Sentry
#endif

/// Обычные настройки телефона как «ключ → строка».
final class PlainPrefs: KeyValueStore {
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func get(_ key: String) -> String? { defaults.string(forKey: key) }
    func put(_ key: String, _ value: String) { defaults.set(value, forKey: key) }
    func remove(_ key: String) { defaults.removeObject(forKey: key) }
}

/// Последний сбой — файлом на телефоне: виден в «Приватности», отправить можно вручную.
final class CrashLog {
    private let file: URL
    private let time: URL

    init(directory: URL? = nil) {
        let dir = directory ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        file = dir.appendingPathComponent("last-crash.txt")
        time = dir.appendingPathComponent("last-crash.time")
    }

    func save(_ report: CrashReport) {
        let ms = Int64((report.at.timeIntervalSince1970 * 1000).rounded())
        try? report.text().write(to: file, atomically: true, encoding: .utf8)
        try? String(ms).write(to: time, atomically: true, encoding: .utf8)
    }

    func last() -> CrashReport? {
        guard let raw = try? String(contentsOf: file, encoding: .utf8) else { return nil }
        let saved = (try? String(contentsOf: time, encoding: .utf8))
            .flatMap { Int64($0.trimmingCharacters(in: .whitespacesAndNewlines)) }
        let at: Date
        if let saved {
            at = Date(timeIntervalSince1970: Double(saved) / 1000)
        } else {
            at = ((try? FileManager.default.attributesOfItem(atPath: file.path))?[.modificationDate] as? Date) ?? Date()
        }
        return CrashReport.parse(at, raw)
    }

    func clear() {
        try? FileManager.default.removeItem(at: file)
        try? FileManager.default.removeItem(at: time)
    }
}

/// Отчёты о сбоях и статистика через Sentry — только с согласия и только
/// если в сборке есть ключ (SENTRY_DSN в Secrets.plist; в CI — секрет) и
/// подключён пакет Sentry. Без ключа или пакета всё остаётся на телефоне.
///
/// Sentry не собирает ничего лишнего: без имени, логина и почты, без
/// снимков экрана, из «хлебных крошек» — только наши события (AppEvent).
/// То же — в telemetry/TelemetrySetup.kt.
enum TelemetrySetup {
    private static var started = false
    private static var prefs: TelemetryPrefs?
    private static var crashes: CrashLog?
    private static var previousHandler: (@convention(c) (NSException) -> Void)?
    private static let sink = SentrySink()

    /// Ключ Sentry из Secrets.plist; "" — нет.
    static var dsn: String {
        guard let url = Bundle.main.url(forResource: "Secrets", withExtension: "plist"),
              let dict = NSDictionary(contentsOf: url),
              let key = dict["SENTRY_DSN"] as? String else { return "" }
        return key.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Можно ли вообще что-то отправить: есть ключ и пакет Sentry.
    static var hasKey: Bool {
        #if canImport(Sentry)
        return !dsn.isEmpty
        #else
        return false
        #endif
    }

    /// При запуске приложения — до любого экрана (MemoryApp.init).
    static func initialize() {
        let p = TelemetryPrefs(PlainPrefs())
        prefs = p
        crashes = CrashLog()
        p.apply()
        // Сбой записывается на телефоне всегда; дальше — прежний обработчик (и Sentry, если включён)
        previousHandler = NSGetUncaughtExceptionHandler()
        NSSetUncaughtExceptionHandler { exception in
            TelemetrySetup.saveCrash(exception)
        }
        refresh()
        // Статистика за прошлый день — одним отчётом
        if let report = p.roll(today()) { sink.usage(day: report.day, counts: report.counts) }
    }

    private static func saveCrash(_ exception: NSException) {
        crashes?.save(CrashReport.of(exception: exception, at: Date()))
        previousHandler?(exception)
    }

    /// Человек изменил согласие.
    static func update(crashes allowCrashes: Bool, analytics allowAnalytics: Bool) {
        prefs?.set(crashes: allowCrashes, analytics: allowAnalytics)
        refresh()
    }

    static var asked: Bool { prefs?.asked ?? false }
    static var crashesOn: Bool { prefs?.crashes ?? false }
    static var analyticsOn: Bool { prefs?.analytics ?? false }

    static func lastCrash() -> CrashReport? { crashes?.last() }

    static func clearCrash() { crashes?.clear() }

    /// Сохранить накопленную статистику (при сворачивании приложения).
    static func flush() {
        guard let p = prefs else { return }
        if let report = p.roll(today()) { sink.usage(day: report.day, counts: report.counts) }
    }

    /// Отправить последний сбой вручную — по нажатию человека, даже если
    /// отчёты выключены: это разовое явное согласие. false — ключа нет.
    @discardableResult
    static func sendLastCrash() -> Bool {
        guard let report = lastCrash(), hasKey else { return false }
        let wasStarted = started
        if !started { start(handleCrashes: false) }
        guard started else { return false }
        sink.crash(report)
        if !wasStarted { stop() }
        crashes?.clear()
        return true
    }

    private static func today() -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = .current
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: Date())
    }

    private static func refresh() {
        let want = hasKey && (Telemetry.crashesAllowed || Telemetry.analyticsAllowed)
        if want && !started { start(handleCrashes: Telemetry.crashesAllowed) }
        if !want && started { stop() }
        Telemetry.sink = started ? sink : nil
    }

    private static func start(handleCrashes: Bool) {
        #if canImport(Sentry)
        let key = dsn
        guard !key.isEmpty else { return }
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        SentrySDK.start { options in
            options.dsn = key
            options.releaseName = "memory@" + version
            #if DEBUG
            options.environment = "debug"
            #else
            options.environment = "release"
            #endif
            options.sendDefaultPii = false
            options.enableCrashHandler = handleCrashes
            options.enableAutoSessionTracking = false
            // Только наши события — без нажатий, экранов и системных событий
            options.beforeBreadcrumb = { crumb in crumb.category == "memory" ? crumb : nil }
        }
        started = true
        #endif
    }

    private static func stop() {
        #if canImport(Sentry)
        SentrySDK.close()
        #endif
        started = false
        Telemetry.sink = nil
    }

    fileprivate static var isStarted: Bool { started }
}

/// Отправка в Sentry; без пакета — ничего не делает.
private final class SentrySink: TelemetrySink {
    func crash(_ report: CrashReport) {
        guard TelemetrySetup.isStarted else { return }
        #if canImport(Sentry)
        SentrySDK.capture(message: report.title + "\n" + report.stack) { scope in
            scope.setLevel(.error)
        }
        #endif
    }

    func usage(day: String, counts: [String: Int]) {
        guard TelemetrySetup.isStarted, !counts.isEmpty, Telemetry.analyticsAllowed else { return }
        #if canImport(Sentry)
        SentrySDK.capture(message: "usage") { scope in
            scope.setLevel(.info)
            scope.setTag(value: day, key: "usage_day")
            for (k, v) in counts { scope.setTag(value: String(v), key: "n_" + k) }
        }
        #endif
    }

    func breadcrumb(_ event: String) {
        guard TelemetrySetup.isStarted else { return }
        #if canImport(Sentry)
        let crumb = Breadcrumb(level: .info, category: "memory")
        crumb.message = event
        SentrySDK.addBreadcrumb(crumb)
        #endif
    }
}
