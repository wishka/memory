import Foundation

/// Что случилось вокруг вас: «нравится», комментарии, упоминания, заявки в
/// друзья, голоса в опросах. Лента активности — колокольчик в «Ленте».
///
/// Хранятся только факты (кто, что, к чему), а текст складывается при
/// показе — на текущем языке интерфейса. Так же устроены и пуши: сервер
/// пришлёт факты, телефон соберёт фразу. То же — в Activity.kt.
enum ActivityKind: String, Codable, CaseIterable {
    case like = "like"
    case comment = "comment"
    case reply = "reply"
    case mention = "mention"
    case commentLike = "comment_like"
    case prismLike = "prism_like"
    case prismComment = "prism_comment"
    case friendRequest = "request"
    case friendAccepted = "accepted"
    case pollVotes = "votes"
    case groupJoin = "join"
    case live = "live"

    var id: String { rawValue }

    /// Такие события сливаются в одно: «Вера и ещё 3 оценили вашу запись».
    var groups: Bool { self == .like || self == .prismLike || self == .commentLike || self == .groupJoin }

    static func of(_ id: String) -> ActivityKind? { ActivityKind(rawValue: id) }
}

/// Одно событие. actorId — кто (0 — несколько человек, как у голосов);
/// actorName — имя на случай, если человека нет среди знакомых (новичок
/// группы); targetId — запись, PRISM, группа или эфир; preview — начало
/// текста записи или комментария; count — сколько (голосов).
struct ActivityItem: Identifiable, Equatable, Codable {
    let id: Int64
    let kind: ActivityKind
    let actorId: Int
    let at: Date
    var targetId: Int64 = 0
    var preview: String = ""
    var count: Int = 1
    var actorName: String = ""
    var actorHue: Double = 0.5
}

extension ActivityItem {
    /// Ключи — как у Android (StateStore.kt): id, kind, actor, at, target, preview, count, name, hue.
    private enum CodingKeys: String, CodingKey {
        case id, kind
        case actorId = "actor"
        case at
        case targetId = "target"
        case preview, count
        case actorName = "name"
        case actorHue = "hue"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(Int64.self, forKey: .id) ?? 0
        kind = try c.decode(ActivityKind.self, forKey: .kind)
        actorId = try c.decodeIfPresent(Int.self, forKey: .actorId) ?? 0
        at = try c.decodeIfPresent(Date.self, forKey: .at) ?? Date(timeIntervalSince1970: 0)
        targetId = try c.decodeIfPresent(Int64.self, forKey: .targetId) ?? 0
        preview = try c.decodeIfPresent(String.self, forKey: .preview) ?? ""
        count = try c.decodeIfPresent(Int.self, forKey: .count) ?? 1
        actorName = try c.decodeIfPresent(String.self, forKey: .actorName) ?? ""
        actorHue = try c.decodeIfPresent(Double.self, forKey: .actorHue) ?? 0.5
    }
}

/// Список, который при чтении пропускает непонятные элементы — например,
/// событие нового вида из более свежей версии приложения. Иначе один
/// такой элемент испортил бы чтение всего сохранённого.
struct LossyList<Element: Codable & Equatable>: Codable, Equatable {
    var items: [Element]

    init(_ items: [Element]) { self.items = items }

    init(from decoder: Decoder) throws {
        var c = try decoder.unkeyedContainer()
        var out: [Element] = []
        while !c.isAtEnd {
            if let x = try? c.decode(Element.self) {
                out.append(x)
            } else if (try? c.decode(Skip.self)) == nil {
                break
            }
        }
        items = out
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.unkeyedContainer()
        try c.encode(contentsOf: items)
    }

    /// Пропуск элемента: читается из чего угодно.
    private struct Skip: Decodable {
        init(from decoder: Decoder) throws {}
    }
}

/// Несколько одинаковых событий вместе: кто (по порядку, последний — первым), когда, к чему.
struct ActivityGroup: Identifiable, Equatable {
    let kind: ActivityKind
    var actors: [User]
    let at: Date
    let targetId: Int64
    let preview: String
    var count: Int
    var ids: [Int64]

    var key: String { "\(kind.id):\(ids.first ?? 0)" }
    var id: String { key }
}

enum Activity {
    /// Сколько событий хранить: старше — забываются.
    static let limit = 300

    /// За какое окно сливать «нравится» к одной записи, мс.
    static let mergeMs: Int64 = 24 * 60 * 60 * 1000

    static func actor(_ item: ActivityItem, _ me: User) -> User {
        if let known = Demo.person(item.actorId), known.id != me.id { return known }
        return User(
            id: item.actorId, name: item.actorName.isEmpty ? t("Кто-то") : item.actorName,
            handle: item.actorName.lowercased().replacingOccurrences(of: " ", with: "."), hue: item.actorHue
        )
    }

    /// Свернуть ленту: «нравится» к одной записи за сутки — одной строкой,
    /// без повторов людей. Порядок — новые сверху. Заблокированных не видно.
    static func group(_ items: [ActivityItem], _ me: User, blocked: Set<Int> = []) -> [ActivityGroup] {
        var out: [ActivityGroup] = []
        for item in items.stableSorted(by: { $0.at > $1.at }) {
            if item.actorId != 0 && blocked.contains(item.actorId) { continue }
            let person = actor(item, me)
            let idx: Int? = item.kind.groups
                ? out.lastIndex(where: { g in
                    g.kind == item.kind && g.targetId == item.targetId
                        && g.at.timeIntervalSince(item.at) * 1000 <= Double(mergeMs)
                })
                : nil
            if let idx {
                var g = out[idx]
                if !g.actors.contains(where: { $0.id == person.id && $0.name == person.name }) { g.actors.append(person) }
                g.count += item.count
                g.ids.append(item.id)
                out[idx] = g
            } else {
                out.append(ActivityGroup(kind: item.kind, actors: [person], at: item.at, targetId: item.targetId,
                                         preview: item.preview, count: item.count, ids: [item.id]))
            }
        }
        return out
    }

    /// Новые — с момента, когда вы последний раз открывали ленту активности.
    static func unread(_ items: [ActivityItem], _ seenAt: Date, blocked: Set<Int> = []) -> Int {
        items.filter { $0.at > seenAt && ($0.actorId == 0 || !blocked.contains($0.actorId)) }.count
    }

    /// «Вера», «Вера и Марк», «Вера, Марк и ещё 3».
    static func who(_ actors: [User]) -> String {
        let names = actors.map(\.firstName)
        switch names.count {
        case 0: return t("Кто-то")
        case 1: return names[0]
        case 2: return tf("%s и %s", names[0], names[1])
        default: return tf("%s, %s и ещё %s", names[0], names[1], names.count - 2)
        }
    }

    /// Фраза события на текущем языке.
    static func text(_ g: ActivityGroup) -> String {
        let single = g.actors.count == 1
        let one = g.actors.first
        func verb(_ plural: String, _ male: String, _ female: String) -> String {
            guard single, let one else { return plural }
            return Nudges.female(one) ? female : male
        }
        let names = Self.who(g.actors)
        let quote = g.preview.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "" : " «\(g.preview.prefix(60))»"
        switch g.kind {
        case .like:
            return tf(verb("%s оценили вашу запись", "%s оценил вашу запись", "%s оценила вашу запись"), names) + quote
        case .comment:
            return tf(verb("%s прокомментировали вашу запись", "%s прокомментировал вашу запись", "%s прокомментировала вашу запись"), names) + quote
        case .reply:
            return tf(verb("%s ответили на ваш комментарий", "%s ответил на ваш комментарий", "%s ответила на ваш комментарий"), names) + quote
        case .mention:
            return tf(verb("%s упомянули вас", "%s упомянул вас", "%s упомянула вас"), names) + quote
        case .commentLike:
            return tf(verb("%s оценили ваш комментарий", "%s оценил ваш комментарий", "%s оценила ваш комментарий"), names) + quote
        case .prismLike:
            return tf(verb("%s оценили ваш PRISM", "%s оценил ваш PRISM", "%s оценила ваш PRISM"), names) + quote
        case .prismComment:
            return tf(verb("%s прокомментировали ваш PRISM", "%s прокомментировал ваш PRISM", "%s прокомментировала ваш PRISM"), names) + quote
        case .friendRequest:
            return tf("%s хочет дружить с вами", names)
        case .friendAccepted:
            return tf(verb("%s приняли вашу заявку в друзья", "%s принял вашу заявку в друзья", "%s приняла вашу заявку в друзья"), names)
        case .pollVotes:
            return tf("В вашем опросе%s — новые голоса: %s", quote, g.count)
        case .groupJoin:
            return tf(verb("%s вступили в вашу группу", "%s вступил в вашу группу", "%s вступила в вашу группу"), names) + quote
        case .live:
            return tf(verb("%s начали прямой эфир", "%s начал прямой эфир", "%s начала прямой эфир"), names) + quote
        }
    }

    /// Добавить событие в начало и не хранить лишнего.
    static func add(_ items: [ActivityItem], _ item: ActivityItem) -> [ActivityItem] {
        Array(([item] + items).prefix(limit))
    }
}
