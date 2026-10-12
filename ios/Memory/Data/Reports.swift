import Foundation

/// Жалобы на контент и людей. Пока сервера нет, жалоба копится в очереди
/// (Snapshot.reports) и скрывает пожаловавшемуся то, на что он пожаловался;
/// с сервером очередь уйдёт модераторам — разобрать в течение суток.
/// Те же правила — Reports.kt на Android.
enum ReportReason: String, CaseIterable, Identifiable, Codable {
    case spam
    case abuse
    case hate
    case adult
    case illegal
    case minors
    case selfHarm = "self_harm"
    case copyright
    case impersonation
    case other

    var id: String { rawValue }

    private var ru: String {
        switch self {
        case .spam: return "Спам или мошенничество"
        case .abuse: return "Оскорбления или травля"
        case .hate: return "Ненависть или призывы к насилию"
        case .adult: return "Откровенный контент"
        case .illegal: return "Запрещено законом"
        case .minors: return "Опасно для детей"
        case .selfHarm: return "Самоповреждение или суицид"
        case .copyright: return "Нарушение авторских прав"
        case .impersonation: return "Выдаёт себя за другого"
        case .other: return "Другое"
        }
    }

    var title: String { t(ru) }

    /// Причина по id; незнакомая — «Другое».
    static func of(_ id: String?) -> ReportReason {
        id.flatMap { ReportReason(rawValue: $0) } ?? .other
    }
}

extension ReportReason {
    /// Незнакомая причина из файла (новее приложения) — «Другое», а не ошибка чтения.
    init(from decoder: Decoder) throws {
        self = .of(try? decoder.singleValueContainer().decode(String.self))
    }
}

enum ReportKind: String, CaseIterable, Codable {
    case post, comment, prism, message, person

    static func of(_ id: String?) -> ReportKind {
        id.flatMap { ReportKind(rawValue: $0) } ?? .post
    }
}

extension ReportKind {
    init(from decoder: Decoder) throws {
        self = .of(try? decoder.singleValueContainer().decode(String.self))
    }
}

/// На что жалоба. id — номер записи, комментария, PRISM или сообщения
/// (для человека — его номер); chatId — переписка сообщения; authorId —
/// кого заблокировать по желанию; preview — пара слов для модератора.
struct ReportTarget: Identifiable, Equatable, Codable {
    let kind: ReportKind
    let id: Int64
    let authorId: Int
    var authorName: String = ""
    var chatId: Int = 0
    var preview: String = ""
}

struct Report: Identifiable, Equatable, Codable {
    let id: Int64
    let target: ReportTarget
    let reason: ReportReason
    var note: String = ""
    /// Когда отправлена; у часов приложения (в тестах — подменённых).
    var createdAt: Date = Date(timeIntervalSince1970: 0)
}
