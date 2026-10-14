import Foundation

/// Комментарий в эфире: кто и что, без истории — эфирный чат живёт, пока идёт эфир.
struct LiveComment: Identifiable, Equatable {
    let id: Int64
    let authorId: Int
    let name: String
    let text: String
    let at: Date
    var hue: Double = 0.5
}

/// Прямой эфир. room — комната видеосервера (LiveKit), в неё публикует
/// ведущий и подключаются зрители. Пока сервера нет — демо: видео ведущего
/// видит только он сам, зрители и комментарии приходят из демо-жизни.
/// То же — в Live.kt.
struct LiveStream: Identifiable, Equatable {
    let id: Int64
    let hostId: Int
    var title: String
    let startedAt: Date
    var viewers: Int = 0
    var peakViewers: Int = 0
    var hearts: Int = 0
    var comments: [LiveComment] = []
    /// Когда закончился; nil — идёт.
    var endedAt: Date? = nil
    /// Видят только друзья.
    var friendsOnly: Bool = false
    /// Картинка демо-эфира (туманность) — пока видео не идёт с сервера.
    var seed: Int = 0

    var live: Bool { endedAt == nil }
    var room: String { "live-\(id)" }

    /// Сколько шёл (или идёт) эфир, мс.
    func durationMs(_ now: Date) -> Int64 {
        let end = endedAt ?? now
        return max(0, Int64((end.timeIntervalSince(startedAt) * 1000).rounded()))
    }
}

/// Видеосвязь эфира. Демо — ничего не передаёт. С сервером — LiveKit:
/// ведущий публикует камеру и микрофон в комнату stream.room, зрители
/// подписываются; токен комнаты выдаёт сервер Memory (POST /live/token).
/// Адрес сервера LiveKit — LIVEKIT_URL, см. docs/LIVE.md.
protocol LiveTransport: AnyObject {
    /// Начать публикацию камеры и микрофона.
    func publish(room: String, token: String)
    /// Подключиться зрителем.
    func join(room: String, token: String)
    /// Выйти из комнаты (ведущий — закончить публикацию).
    func leave()
    /// Идёт ли настоящая видеосвязь (false — демо).
    var connected: Bool { get }
}

/// Пока сервера нет: эфир идёт «на телефоне» — интерфейс, зрители и чат из демо.
final class DemoLiveTransport: LiveTransport {
    static let shared = DemoLiveTransport()

    func publish(room: String, token: String) {}
    func join(room: String, token: String) {}
    func leave() {}
    var connected: Bool { false }
}

enum Lives {
    static let maxTitle = 80
    static let maxComment = 200
    /// Сколько комментариев держать на экране эфира.
    static let keepComments = 100

    /// «12:05» или «1:02:05».
    static func clock(_ ms: Int64) -> String {
        let s = max(0, ms / 1000)
        let h = s / 3600
        let m = (s % 3600) / 60
        let sec = s % 60
        func two(_ n: Int64) -> String { n < 10 ? "0\(n)" : "\(n)" }
        return h > 0 ? "\(h):\(two(m)):\(two(sec))" : "\(m):\(two(sec))"
    }

    /// Итог эфира: «Эфир шёл 12:05 · зрителей до 34 · ♥ 210 · 18 комментариев».
    static func summary(_ s: LiveStream, _ now: Date) -> String {
        tf("Эфир шёл %s", clock(s.durationMs(now))) + " · " +
            tf("зрителей до %s", s.peakViewers) + " · ♥ " + String(s.hearts) + " · " +
            plural(s.comments.count, "комментарий", "комментария", "комментариев")
    }

    /// Добавить комментарий и не держать лишнего.
    static func addComment(_ s: LiveStream, _ c: LiveComment) -> LiveStream {
        var x = s
        x.comments = Array((s.comments + [c]).suffix(keepComments))
        return x
    }

    /// Пришли или ушли зрители: пик запоминается.
    static func viewers(_ s: LiveStream, _ delta: Int) -> LiveStream {
        var x = s
        x.viewers = max(0, s.viewers + delta)
        x.peakViewers = max(s.peakViewers, x.viewers)
        return x
    }

    static let demoTitles = ["Вечерняя прогулка по городу", "Готовлю ужин — заходите", "Разбираю новые пластинки", "Отвечаю на вопросы", "Закат с крыши", "Рисую в прямом эфире"]
    static let demoComments = ["Привет!", "Как красиво", "Откуда эфир?", "🔥🔥🔥", "Давно ждала", "Покажи ещё", "Звук отличный", "Ура, эфир!", "Сохраню себе", "❤️"]

    private static func floorMod(_ a: Int, _ n: Int) -> Int { ((a % n) + n) % n }

    static func demoTitle(_ seed: Int) -> String { t(demoTitles[floorMod(seed, demoTitles.count)]) }
    static func demoComment(_ seed: Int) -> String { t(demoComments[floorMod(seed, demoComments.count)]) }
}
