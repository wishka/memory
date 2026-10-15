import Foundation

/// Что за действие ждёт отправки. Имена — как у Android (OpKind.id).
enum OpKind: String {
    case createPost = "post"
    case like = "like"
    case unlike = "unlike"
    case sendMessage = "message"
    /// История: payload — Story в JSON (ApiCodec.storyJson), вложение — после загрузки.
    case createStory = "story"
    /// Любой запрос: группы, «прочитано», профиль. payload — {method, path, body, coalesce}.
    case call = "call"

    static func of(_ id: String?) -> OpKind? { id.flatMap { OpKind(rawValue: $0) } }
}

/// Действие, которое ждёт отправки: запись, «нравится», сообщение.
/// key — Idempotency-Key: тот же при каждом повторе, поэтому сервер не
/// создаст дубль, даже если первый раз ответ потерялся в пути.
/// target — номер записи (для «нравится») или переписки (для сообщения).
/// payload — что отправить (JSON записи или сообщения в формате API).
struct OutboxOp: Equatable {
    let key: String
    let kind: OpKind
    var target: Int64 = 0
    var payload: String = ""
    var createdAt: Int64 = 0
    var attempts: Int = 0
    var nextAt: Int64 = 0
    var error: String = ""

    func json() -> JSONObject {
        var o: JSONObject = [:]
        o["key"] = key
        o["kind"] = kind.rawValue
        o["target"] = target
        o["payload"] = payload
        o["createdAt"] = createdAt
        o["attempts"] = attempts
        o["nextAt"] = nextAt
        o["error"] = error
        return o
    }

    static func of(_ o: JSONObject) -> OutboxOp? {
        guard let kind = OpKind.of(Json.string(o, "kind")), let key = o["key"] as? String else { return nil }
        var op = OutboxOp(key: key, kind: kind)
        op.target = Json.long(o, "target")
        op.payload = Json.string(o, "payload")
        op.createdAt = Json.long(o, "createdAt")
        op.attempts = Json.int(o, "attempts")
        op.nextAt = Json.long(o, "nextAt")
        op.error = Json.string(o, "error")
        return op
    }
}

/// Итог прохода по очереди.
struct FlushResult: Equatable {
    let sent: Int
    let waiting: Int
    let failed: Int
    let offline: Bool
    /// Сервер не принял вход — нужно войти снова (очередь сохранена).
    var authLost: Bool = false
}

/// Очередь отправки: что сделали без сети (или пока сеть моргнула), уходит
/// по порядку, когда сервер доступен. Лежит в кэше — переживает перезапуск.
///
/// Порядок важен для переписки: следующее сообщение в той же переписке не
/// обгоняет предыдущее. «Нравится» к одной записи схлопываются — уходит
/// только последнее нажатие. То же — в Outbox.kt.
final class Outbox {
    private let cache: OfflineCache
    private let clock: () -> Int64

    init(cache: OfflineCache, clock: @escaping () -> Int64) {
        self.cache = cache
        self.clock = clock
    }

    private func cacheKey(_ key: String) -> String { CacheKeys.op + key }

    private func decode(_ entry: CacheEntry?) -> OutboxOp? {
        guard let entry, let o = Json.parse(entry.value) else { return nil }
        return OutboxOp.of(o)
    }

    func all() -> [OutboxOp] {
        cache.keys(CacheKeys.op).compactMap { decode(cache.get($0)) }
    }

    var count: Int { cache.keys(CacheKeys.op).count }

    func get(_ key: String) -> OutboxOp? { decode(cache.get(cacheKey(key))) }

    func enqueue(_ op: OutboxOp) {
        if op.kind == .like || op.kind == .unlike {
            for old in all() where (old.kind == .like || old.kind == .unlike) && old.target == op.target {
                remove(old.key)
            }
        }
        // Профиль, «прочитано»: важно только последнее — прежнее такое же снимаем
        if let c = coalesceOf(op) {
            for old in all() where old.kind == .call && coalesceOf(old) == c {
                remove(old.key)
            }
        }
        var stamped = op
        if stamped.createdAt == 0 { stamped.createdAt = clock() }
        save(stamped)
    }

    func remove(_ key: String) {
        cache.remove(cacheKey(key))
    }

    private func coalesceOf(_ op: OutboxOp) -> String? {
        if op.kind != .call { return nil }
        guard let o = Json.parse(op.payload) else { return nil }
        let c = Json.string(o, "coalesce")
        return c.isEmpty ? nil : c
    }

    /// Снять паузу перед повтором (сеть вернулась).
    func retryNow(_ key: String) {
        guard var op = get(key) else { return }
        op.nextAt = 0
        op.attempts = 0
        save(op)
    }

    private func save(_ op: OutboxOp) {
        cache.put(cacheKey(op.key), Json.string(op.json()), at: clock())
    }

    /// Отправить, что готово. send — как отправить одно действие (знает Net):
    /// ответ сервера или NetError. ready — готово ли (вложения загружены).
    /// Нет сети — проход останавливается: остальным тоже не дойти.
    func flush(
        ready: (OutboxOp) -> Bool,
        failedDeps: (OutboxOp) -> Bool,
        send: (OutboxOp) async throws -> JSONObject?,
        onSent: (OutboxOp, JSONObject?) -> Void,
        onFailed: (OutboxOp, NetError) -> Void
    ) async -> FlushResult {
        let now = clock()
        var sent = 0
        var waiting = 0
        var failed = 0
        var blockedChats = Set<Int64>()
        for op in all() {
            let chatOrdered = op.kind == .sendMessage
            if chatOrdered && blockedChats.contains(op.target) {
                waiting += 1
                continue
            }
            if failedDeps(op) {
                remove(op.key)
                failed += 1
                onFailed(op, NetError(.client, message: "upload"))
                continue
            }
            if op.nextAt > now || !ready(op) {
                waiting += 1
                if chatOrdered { blockedChats.insert(op.target) }
                continue
            }
            do {
                let answer = try await send(op)
                remove(op.key)
                sent += 1
                onSent(op, answer)
            } catch {
                if error is CancellationError {
                    return FlushResult(sent: sent, waiting: count, failed: failed, offline: false)
                }
                let err = NetError.of(error)
                if err.kind == .auth {
                    // Вход закрыт — ничего не теряем: ждём, пока войдут снова
                    var wait = op
                    wait.attempts = op.attempts + 1
                    wait.nextAt = now + Backoff.maxMs
                    wait.error = err.kind.rawValue
                    save(wait)
                    return FlushResult(sent: sent, waiting: count, failed: failed, offline: false, authLost: true)
                }
                if err.retryable {
                    var retry = op
                    retry.attempts = op.attempts + 1
                    retry.nextAt = now + Backoff.delayMs(op.attempts, retryAfterMs: err.retryAfterMs)
                    retry.error = err.kind.rawValue
                    save(retry)
                    waiting += 1
                    if chatOrdered { blockedChats.insert(op.target) }
                    if err.kind == .offline {
                        return FlushResult(sent: sent, waiting: count, failed: failed, offline: true)
                    }
                } else {
                    remove(op.key)
                    failed += 1
                    onFailed(op, err)
                }
            }
        }
        return FlushResult(sent: sent, waiting: waiting, failed: failed, offline: false)
    }
}
