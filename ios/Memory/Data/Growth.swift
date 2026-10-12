import Foundation

/// Петли роста: приглашение, привязанное к действию («дорисуй мой
/// рисунок», «возьми книгу»), и журнал событий, из которого считается
/// K-фактор. Пока нет сервера, журнал живёт на телефоне; сервер потом
/// заберёт его как есть. Та же логика — в Growth.kt.

/// К чему зовут. ru — что сделать: «дорисовать рисунок».
enum InviteAction: String, Codable, CaseIterable {
    case app
    case drawing
    case book
    case outing
    case lesson
    case shelf
    case journey
    case cache

    var id: String { rawValue }

    private var ru: String {
        switch self {
        case .app: return "присоединиться к Memory"
        case .drawing: return "дорисовать общий рисунок"
        case .book: return "взять книгу из книжного обмена"
        case .outing: return "пойти на вылазку"
        case .lesson: return "обменяться навыками"
        case .shelf: return "посмотреть мою полку"
        case .journey: return "поддержать мой челлендж советом"
        case .cache: return "найти мой клад"
        }
    }

    var title: String { t(ru) }

    static func of(_ id: String) -> InviteAction { InviteAction(rawValue: id) ?? .app }

    /// Незнакомое действие в сохранённом файле — «присоединиться», а не ошибка чтения.
    init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        self = InviteAction(rawValue: raw) ?? .app
    }
}

/// Приглашение: чей код, к чему и к какому объекту (номер рисунка, книги…).
struct Invite: Equatable, Codable, Identifiable {
    var code: String
    var action: InviteAction
    var targetId: String = ""
    var from: String = ""

    var id: String { code + ":" + action.id + ":" + targetId }
}

enum Invites {
    /// Страница-приглашение (GitHub Pages репозитория, папка docs/invite):
    /// открывает приложение, а если его нет — объясняет, как поставить.
    static let web = "https://wishka.github.io/memory/invite/"
    static let scheme = "memory"

    private static let alphabet = Array("ABCDEFGHJKLMNPQRSTUVWXYZ23456789")

    /// Свой код приглашения: шесть знаков, всегда один и тот же для логина.
    /// Та же арифметика, что на Android (Long с переполнением), — коды совпадают.
    static func code(_ login: String) -> String {
        var h: Int64 = 1_125_899_906_842_597
        for unit in login.lowercased().utf16 {
            h = 31 &* h &+ Int64(unit)
        }
        let v = h & Int64.max
        return String((0..<6).map { i in alphabet[Int((v >> Int64(i * 5)) & 31)] })
    }

    private static func query(_ i: Invite) -> String {
        [("c", i.code), ("a", i.action.id), ("id", i.targetId), ("n", i.from)]
            .filter { !$0.1.isEmpty }
            .map { $0.0 + "=" + formEncode($0.1) }
            .joined(separator: "&")
    }

    /// Ссылка для мессенджера: открывается в браузере и ведёт в приложение.
    static func webLink(_ i: Invite) -> String { web + "?" + query(i) }

    /// Ссылка прямо в приложение (её открывает страница-приглашение).
    static func appLink(_ i: Invite) -> String { "\(scheme)://invite?" + query(i) }

    /// Разобрать ссылку: и memory://invite?…, и https://…/invite/?…; nil — не приглашение.
    static func parse(_ link: String) -> Invite? {
        let s = link.trimmingCharacters(in: .whitespacesAndNewlines)
        let isApp = s.hasPrefix("\(scheme)://invite")
        let isWeb = s.hasPrefix(web) || s.hasPrefix(String(web.dropLast()))
        if !isApp && !isWeb { return nil }
        var q = ""
        if let mark = s.firstIndex(of: "?") {
            q = String(s[s.index(after: mark)...])
        }
        if let hash = q.firstIndex(of: "#") { q = String(q[..<hash]) }
        var params: [String: String] = [:]
        for part in q.split(separator: "&", omittingEmptySubsequences: false) where part.contains("=") {
            let eq = part.firstIndex(of: "=")!
            let key = String(part[..<eq])
            let value = String(part[part.index(after: eq)...])
            params[key] = formDecode(value) ?? ""
        }
        let code = String((params["c"] ?? "").uppercased().filter { alphabet.contains($0) })
        if code.count != 6 { return nil }
        return Invite(code: code, action: InviteAction.of(params["a"] ?? ""),
                      targetId: String((params["id"] ?? "").prefix(40)), from: String((params["n"] ?? "").prefix(40)))
    }

    /// Текст сообщения: «Андрей зовёт вас дорисовать общий рисунок «Город» в Memory: ссылка».
    static func message(_ i: Invite, title: String = "") -> String {
        let what = title.isEmpty ? i.action.title : "\(i.action.title) «\(title)»"
        return tf("%s зовёт вас %s в Memory", i.from, what) + ": " + webLink(i)
    }
}

/// Как URLEncoder на Android: пробел — «+», без кодирования только
/// буквы и цифры латиницы и «. - * _»; остальное — %XX по байтам UTF-8.
func formEncode(_ s: String) -> String {
    var out = ""
    for byte in s.utf8 {
        switch byte {
        case 0x41...0x5A, 0x61...0x7A, 0x30...0x39, 0x2E, 0x2D, 0x2A, 0x5F:
            out.append(Character(UnicodeScalar(byte)))
        case 0x20:
            out.append("+")
        default:
            out.append(String(format: "%%%02X", UInt32(byte)))
        }
    }
    return out
}

/// Как URLDecoder на Android: «+» — пробел, %XX — байт UTF-8. nil — испорченная строка.
func formDecode(_ s: String) -> String? {
    var bytes: [UInt8] = []
    let utf8 = Array(s.utf8)
    var i = 0
    while i < utf8.count {
        let b = utf8[i]
        if b == 0x2B {
            bytes.append(0x20)
            i += 1
        } else if b == 0x25 {
            guard i + 2 < utf8.count,
                  let hex = UInt8(String(decoding: utf8[(i + 1)...(i + 2)], as: UTF8.self), radix: 16) else { return nil }
            bytes.append(hex)
            i += 3
        } else {
            bytes.append(b)
            i += 1
        }
    }
    return String(decoding: bytes, as: UTF8.self)
}

// MARK: - Журнал событий

enum GrowthEventType: String, Codable {
    /// Отправили приглашение (detail — к чему).
    case inviteSent = "InviteSent"
    /// Открыли приложение по чужой ссылке (detail — код пригласившего).
    case inviteOpened = "InviteOpened"
    /// Зарегистрировались по приглашению.
    case joinedByInvite = "JoinedByInvite"
    /// Поделились карточкой (detail — что за карточка).
    case cardShared = "CardShared"
    /// Знакомство «выберите любимое» пройдено.
    case onboardingDone = "OnboardingDone"
    case checkIn = "CheckIn"
    /// Предложили идею или сообщили об ошибке.
    case feedback = "Feedback"
}

struct GrowthEvent: Equatable, Codable {
    var type: GrowthEventType
    var at: Date
    var detail: String = ""
}

/// Журнал не растёт бесконечно: последние 500 событий.
let growthLogLimit = 500

extension Array where Element == GrowthEvent {
    func logged(_ e: GrowthEvent) -> [GrowthEvent] { Array((self + [e]).suffix(growthLogLimit)) }
}

/// Цифры роста. K = i × c: i — сколько приглашений отправляет человек,
/// c — какая доля приглашённых приходит. i считается на телефоне; c
/// знает только сервер (кто пришёл по вашим ссылкам), поэтому до него —
/// оценка по средней конверсии, и честно помечено.
struct GrowthStats: Equatable {
    let invitesSent: Int
    let cardsShared: Int
    let checkIns: Int
    let joinedByInvite: Bool
    /// Приглашения по видам действий: «drawing» → 3.
    let byAction: [String: Int]

    /// Оценка K при конверсии c (по умолчанию 10%: столько из приглашённых обычно приходит).
    func kEstimate(conversion: Double = 0.1) -> Double { Double(invitesSent) * conversion }
}

func growthStats(_ events: [GrowthEvent]) -> GrowthStats {
    var byAction: [String: Int] = [:]
    for e in events where e.type == .inviteSent {
        let action = e.detail.split(separator: ":", maxSplits: 1, omittingEmptySubsequences: false).first.map(String.init) ?? ""
        byAction[action, default: 0] += 1
    }
    return GrowthStats(
        invitesSent: events.filter { $0.type == .inviteSent }.count,
        cardsShared: events.filter { $0.type == .cardShared }.count,
        checkIns: events.filter { $0.type == .checkIn }.count,
        joinedByInvite: events.contains { $0.type == .joinedByInvite },
        byAction: byAction
    )
}

/// Предложить идею или сообщить об ошибке: новая задача в репозитории приложения.
enum Feedback {
    static let issues = "https://github.com/wishka/memory/issues/new"

    static func link(idea: Bool, text: String, version: String) -> String {
        let firstLine = text.components(separatedBy: .newlines).first ?? ""
        let title = (idea ? "Идея: " : "Ошибка: ") + String(firstLine.prefix(80))
        let body = text + "\n\n—\nMemory " + version
        return issues + "?title=" + formEncode(title) + "&body=" + formEncode(body) + "&labels=" + formEncode(idea ? "idea" : "bug")
    }
}
