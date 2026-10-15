import Foundation

/// Что сейчас со звонком.
enum CallPhase: Equatable {
    /// Я звоню — у собеседника звонит.
    case calling
    /// Звонят мне — принять или отклонить.
    case incoming
    /// Разговор идёт.
    case active
}

/// Звонок в истории и в переписке. missed — не ответили (мне — пропущенный,
/// я звонил — «не ответил»), declined — отклонили. То же — в Calls.kt.
struct CallRecord: Identifiable, Equatable, Codable {
    let id: Int64
    let peerId: Int
    let video: Bool
    let outgoing: Bool
    let at: Date
    var durationMs: Int64 = 0
    var missed: Bool = false
    var declined: Bool = false
}

extension CallRecord {
    /// Ключи — как у Android (StateStore.kt): id, peer, video, outgoing, at, durationMs, missed, declined.
    private enum CodingKeys: String, CodingKey {
        case id
        case peerId = "peer"
        case video, outgoing, at, durationMs, missed, declined
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(Int64.self, forKey: .id) ?? 0
        peerId = try c.decodeIfPresent(Int.self, forKey: .peerId) ?? 0
        video = try c.decodeIfPresent(Bool.self, forKey: .video) ?? false
        outgoing = try c.decodeIfPresent(Bool.self, forKey: .outgoing) ?? false
        at = try c.decodeIfPresent(Date.self, forKey: .at) ?? Date(timeIntervalSince1970: 0)
        durationMs = try c.decodeIfPresent(Int64.self, forKey: .durationMs) ?? 0
        missed = try c.decodeIfPresent(Bool.self, forKey: .missed) ?? false
        declined = try c.decodeIfPresent(Bool.self, forKey: .declined) ?? false
    }
}

/// Звонок, который идёт сейчас: этап, когда соединились, кнопки.
struct ActiveCall: Identifiable, Equatable {
    let id: Int64
    let peerId: Int
    let video: Bool
    let outgoing: Bool
    var phase: CallPhase
    let startedAt: Date
    /// Когда соединились; nil — ещё не соединились.
    var connectedAt: Date? = nil
    var muted: Bool = false
    var cameraOn: Bool = true
    var frontCamera: Bool = true
    var speaker: Bool = false

    var room: String { "call-\(id)" }

    /// Сколько идёт разговор, мс; до соединения — 0.
    func durationMs(_ now: Date) -> Int64 {
        guard let connectedAt else { return 0 }
        return max(0, Stories.ms(from: connectedAt, to: now))
    }
}

/// Связь звонка. Демо — ничего не передаёт. С сервером — LiveKit, как у
/// эфиров: комната call-<id> на двоих, токен выдаёт сервер Memory
/// (POST /calls/token), входящий звонок приходит пушем (VoIP-пуш на iPhone,
/// high-priority FCM на Android). См. docs/LIVE.md.
protocol CallTransport: AnyObject {
    func connect(room: String, token: String, video: Bool)
    func setMuted(_ muted: Bool)
    func setCamera(on: Bool, front: Bool)
    func hangUp()
    /// Идёт ли настоящая связь (false — демо).
    var connected: Bool { get }
}

/// Пока сервера нет: звонок идёт «на телефоне» — интерфейс и демо-собеседник.
final class DemoCallTransport: CallTransport {
    static let shared = DemoCallTransport()

    func connect(room: String, token: String, video: Bool) {}
    func setMuted(_ muted: Bool) {}
    func setCamera(on: Bool, front: Bool) {}
    func hangUp() {}
    var connected: Bool { false }
}

enum Calls {
    /// Сколько звонит, пока не станет «нет ответа».
    static let ringMs: Int64 = 30_000
    /// Сколько звонков хранить в истории.
    static let logLimit = 100

    /// «Видеозвонок · 3:12», «Пропущенный звонок», «Отклонён», «Нет ответа».
    static func title(_ r: CallRecord) -> String {
        let kind = r.video ? t("Видеозвонок") : t("Звонок")
        if r.declined { return tf("%s отклонён", kind) }
        if r.missed && !r.outgoing { return r.video ? t("Пропущенный видеозвонок") : t("Пропущенный звонок") }
        if r.missed { return tf("%s — нет ответа", kind) }
        return kind + " · " + Lives.clock(r.durationMs)
    }

    /// Звонок закончился — запись для истории.
    static func record(_ c: ActiveCall, _ now: Date, declined: Bool = false) -> CallRecord {
        CallRecord(id: c.id, peerId: c.peerId, video: c.video, outgoing: c.outgoing, at: c.startedAt,
                   durationMs: c.durationMs(now), missed: c.connectedAt == nil && !declined, declined: declined)
    }

    /// Может ли человек позвонить мне при этих настройках.
    static func allowed(_ from: Int, friends: Set<Int>, blocked: Set<Int>, who: Audience) -> Bool {
        if blocked.contains(from) { return false }
        switch who {
        case .everyone: return true
        case .following: return friends.contains(from)
        case .nobody: return false
        }
    }

    static func add(_ log: [CallRecord], _ r: CallRecord) -> [CallRecord] {
        Array(([r] + log.filter { $0.id != r.id }).prefix(logLimit))
    }
}
