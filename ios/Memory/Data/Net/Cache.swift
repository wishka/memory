import Foundation

/// Сохранённое: значение (JSON) и когда положили (мс).
struct CacheEntry {
    let value: String
    let savedAt: Int64
}

/// Офлайн-кэш: страницы лент и переписок, очередь отправки, незаконченные
/// загрузки. Ключи с приставкой: «page:…», «op:…», «up:…».
///
/// На iPhone — SQLite (SqliteCache в AppleNet.swift), в тестах — в памяти.
/// Каждый аккаунт — своя база: чужое не перепутается. То же — в Cache.kt.
protocol OfflineCache: AnyObject {
    func get(_ key: String) -> CacheEntry?
    func put(_ key: String, _ value: String, at: Int64)
    func remove(_ key: String)
    /// Все ключи с приставкой — по порядку добавления (для очереди это важно).
    func keys(_ prefix: String) -> [String]
    /// Сколько места занимают страницы (без очереди и загрузок).
    func sizeBytes() -> Int64
    /// Стереть страницы; очередь и загрузки не трогаем — иначе потеряется неотправленное.
    func clearPages()
}

/// Кэш в памяти — для тестов и пока аккаунт не открыт.
final class MemoryCache: OfflineCache {
    private let lock = NSLock()
    private var map: [String: CacheEntry] = [:]
    private var added: [String: Int64] = [:]
    private var order: Int64 = 0

    init() {}

    func get(_ key: String) -> CacheEntry? {
        lock.lock()
        defer { lock.unlock() }
        return map[key]
    }

    func put(_ key: String, _ value: String, at: Int64) {
        lock.lock()
        defer { lock.unlock() }
        map[key] = CacheEntry(value: value, savedAt: at)
        if added[key] == nil {
            order += 1
            added[key] = order
        }
    }

    func remove(_ key: String) {
        lock.lock()
        defer { lock.unlock() }
        map.removeValue(forKey: key)
        added.removeValue(forKey: key)
    }

    func keys(_ prefix: String) -> [String] {
        lock.lock()
        defer { lock.unlock() }
        let mine = map.keys.filter { $0.hasPrefix(prefix) }
        return mine.sorted { (added[$0] ?? 0) < (added[$1] ?? 0) }
    }

    func sizeBytes() -> Int64 {
        lock.lock()
        defer { lock.unlock() }
        var total: Int64 = 0
        for (key, entry) in map where key.hasPrefix(CacheKeys.page) {
            total += Int64(key.utf16.count) + Int64(entry.value.utf16.count)
        }
        return total * 2
    }

    func clearPages() {
        lock.lock()
        defer { lock.unlock() }
        for key in map.keys where key.hasPrefix(CacheKeys.page) {
            map.removeValue(forKey: key)
            added.removeValue(forKey: key)
        }
    }
}

/// Приставки ключей кэша.
enum CacheKeys {
    static let page = "page:"
    static let op = "op:"
    static let upload = "up:"
    static let feed = "page:feed"
    static func chat(_ chatId: Int) -> String { "page:chat:\(chatId)" }
    static func user(_ userId: Int) -> String { "page:user:\(userId)" }
}
