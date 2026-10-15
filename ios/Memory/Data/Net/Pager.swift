import Foundation

/// Что сейчас с подгружаемым списком — для экрана.
struct PagerState<T> {
    var items: [T] = []
    var next: String? = nil
    /// Сервер сказал «дальше пусто».
    var end = false
    var loading = false
    /// Показано сохранённое, свежего с сервера ещё не было.
    var fromCache = false
    /// Когда сохранённое положили в кэш (мс); 0 — не из кэша.
    var cachedAt: Int64 = 0
    var error: NetError? = nil
}

/// Список, который подгружается страницами по курсору: лента, записи
/// человека, история переписки. Сначала — что было в кэше (мгновенно и без
/// сети), потом свежее с сервера. Повторы по id отбрасываются: если между
/// страницами появилась новая запись, старые не задвоятся.
///
/// Сам ничего не запускает: экран зовёт refresh() и loadMore(), когда
/// долистали до конца. Без сети — ошибка Offline, а сохранённое остаётся.
/// То же — в Pager.kt.
final class Pager<T> {
    private(set) var state = PagerState<T>()

    private let cacheKey: String?
    private let cache: OfflineCache?
    private let idOf: (T) -> Int64
    private let encode: (T) -> JSONObject
    private let decode: (JSONObject) -> T?
    private let clock: () -> Int64
    private let load: (String?) async throws -> Page<T>

    /// Сколько держать в кэше: первые страницы — то, что откроют снова.
    private let keep = 100

    init(
        cacheKey: String?,
        cache: OfflineCache?,
        idOf: @escaping (T) -> Int64,
        encode: @escaping (T) -> JSONObject,
        decode: @escaping (JSONObject) -> T?,
        clock: @escaping () -> Int64,
        load: @escaping (String?) async throws -> Page<T>
    ) {
        self.cacheKey = cacheKey
        self.cache = cache
        self.idOf = idOf
        self.encode = encode
        self.decode = decode
        self.clock = clock
        self.load = load
    }

    /// Показать сохранённое (без сети). true — было что показать.
    @discardableResult
    func restore() -> Bool {
        guard let key = cacheKey, let cache = self.cache, let entry = cache.get(key) else { return false }
        guard let o = Json.parse(entry.value) else { return false }
        let items: [T] = Json.objects(o, "items").compactMap { decode($0) }
        let next = Json.string(o, "next")
        var restored = PagerState<T>()
        restored.items = items
        restored.next = next.isEmpty ? nil : next
        restored.end = Json.bool(o, "end")
        restored.fromCache = true
        restored.cachedAt = entry.savedAt
        state = restored
        return !items.isEmpty
    }

    /// С самого начала: потянули вниз или открыли экран.
    @discardableResult
    func refresh() async -> NetError? {
        await fetch(nil, replace: true)
    }

    /// Следующая страница: долистали до конца. Ничего не делает, если уже грузится или дальше пусто.
    @discardableResult
    func loadMore() async -> NetError? {
        let s = state
        if s.loading || s.end { return nil }
        if s.fromCache || s.items.isEmpty { return await refresh() }
        return await fetch(s.next, replace: false)
    }

    private func fetch(_ cursor: String?, replace: Bool) async -> NetError? {
        state.loading = true
        state.error = nil
        do {
            let page = try await load(cursor)
            var seen = Set<Int64>()
            var merged: [T] = []
            if !replace {
                merged = state.items
                for item in merged { seen.insert(idOf(item)) }
            }
            for item in page.items where seen.insert(idOf(item)).inserted {
                merged.append(item)
            }
            var fresh = PagerState<T>()
            fresh.items = merged
            fresh.next = page.next
            fresh.end = page.next == nil
            state = fresh
            save()
            return nil
        } catch {
            if error is CancellationError {
                state.loading = false
                return nil
            }
            let err = NetError.of(error)
            state.loading = false
            state.error = err
            return err
        }
    }

    /// Убрать элемент (удалили, заблокировали автора) — и из кэша.
    func drop(_ id: Int64) {
        state.items = state.items.filter { idOf($0) != id }
        save()
    }

    /// Заменить элемент (лайк, правка) — и в кэше.
    func update(_ id: Int64, _ change: (T) -> T) {
        state.items = state.items.map { idOf($0) == id ? change($0) : $0 }
        save()
    }

    private func save() {
        guard let key = cacheKey, let cache = self.cache else { return }
        if state.fromCache { return }
        let kept = Array(state.items.prefix(keep))
        let whole = kept.count == state.items.count
        var o: JSONObject = [:]
        o["items"] = kept.map { encode($0) }
        o["next"] = whole ? (state.next ?? "") : ""
        o["end"] = state.end && whole
        cache.put(key, Json.string(o), at: clock())
    }
}
