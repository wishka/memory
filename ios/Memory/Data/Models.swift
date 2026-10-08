import Foundation

/// Человек в Memory. hue — 0...1, оттенок его аватара-градиента.
/// followers / followingCount — числа для карточки; у «себя» подписки
/// считаются по настоящему списку.
struct User: Identifiable, Equatable, Codable {
    let id: Int
    var name: String
    var handle: String
    var bio: String = ""
    var hue: Double = 0.5
    var online: Bool = false
    var city: String = ""
    /// «14 марта» — без года: возраст человек раскрывает сам, если хочет.
    var birthday: String = ""
    var website: String = ""
    var interests: [String] = []
    /// «сентябрь 2026»
    var joined: String = ""
    var followers: Int = 0
    var followingCount: Int = 0

    var initials: String {
        let letters = name.split(separator: " ").prefix(2).compactMap { $0.first }.map { String($0).uppercased() }
        let joinedLetters = letters.joined()
        return joinedLetters.isEmpty ? "?" : joinedLetters
    }

    /// Первое слово имени — подпись под аватаром.
    var firstName: String { name.split(separator: " ").first.map(String.init) ?? name }
}

/// Что приложено к записи или сообщению.
enum MediaKind: String, Codable {
    case photo, video, document
}

/// Вложение: фото, видео или документ. Пока сервера нет, файл копируется
/// в память приложения (см. MediaFiles), fileName — имя копии в папке
/// вложений. Так вложение переживает перезапуск и не пропадает, если
/// человек удалит оригинал из галереи.
///
/// Хранится имя файла, а не полный путь: путь к папке приложения на
/// iPhone меняется после обновления.
struct Attachment: Identifiable, Equatable, Codable, Hashable {
    let id: String
    let kind: MediaKind
    let fileName: String
    /// Имя для документов: «Договор.pdf».
    var name: String = ""
    var size: Int64 = 0
}

/// Комментарий к записи или PRISM. createdAt — момент написания; nil —
/// у демо-комментариев, тогда время из minutesAgo.
struct Comment: Identifiable, Equatable, Codable {
    let id: Int64
    var author: User
    var text: String
    var minutesAgo: Int = 0
    var createdAt: Date? = nil
}

/// Запись в ленте. artSeed != nil — к записи приложена процедурная
/// «картинка» (туманность, см. Nebula) — так выглядят демо-записи.
/// media — настоящие фото и видео, которые приложил человек.
///
/// comments — сколько всего комментариев (у демо-записей их больше, чем
/// загружено), commentList — те, что видны в обсуждении.
struct Post: Identifiable, Equatable, Codable {
    let id: Int64
    var author: User
    var text: String
    var minutesAgo: Int
    var artSeed: Int? = nil
    var likes: Int = 0
    var liked: Bool = false
    var comments: Int = 0
    var media: [Attachment] = []
    var commentList: [Comment] = []
    /// Момент публикации у своих записей; у демо — nil, время из minutesAgo.
    var createdAt: Date? = nil
}

/// Минуты с момента createdAt, а для демо-данных — заданные заранее.
func minutesSince(_ createdAt: Date?, _ minutesAgo: Int, now: Date = Date()) -> Int {
    guard let createdAt else { return minutesAgo }
    return max(0, Int(now.timeIntervalSince(createdAt) / 60))
}

/// Сообщение; text может быть пустым, если отправлены только вложения.
struct Message: Identifiable, Equatable, Codable {
    let id: Int64
    let fromMe: Bool
    let text: String
    let time: String
    var attachments: [Attachment] = []
}

struct Chat: Identifiable, Equatable, Codable {
    let id: Int
    var peer: User
    var messages: [Message]
    var unread: Int = 0
    var typing: Bool = false

    var last: Message? { messages.last }
}

/// «5 мин», «2 ч», «вчера», «3 дн» — время записи в ленте.
func agoTitle(_ minutes: Int) -> String {
    if minutes < 1 { return "сейчас" }
    if minutes < 60 { return "\(minutes) мин" }
    if minutes < 60 * 24 { return "\(minutes / 60) ч" }
    if minutes < 60 * 48 { return "вчера" }
    return "\(minutes / (60 * 24)) дн"
}

/// Русское склонение: plural(5, "запись", "записи", "записей") → «5 записей».
func plural(_ n: Int, _ one: String, _ few: String, _ many: String) -> String {
    let mod10 = n % 10
    let mod100 = n % 100
    let word: String
    if mod10 == 1 && mod100 != 11 {
        word = one
    } else if (2...4).contains(mod10) && !(12...14).contains(mod100) {
        word = few
    } else {
        word = many
    }
    return "\(n) \(word)"
}

/// Группа по интересам. seed — обложка (процедурная туманность).
struct MemoryGroup: Identifiable, Equatable, Codable {
    let id: Int
    var name: String
    var description: String
    var topic: String
    var members: Int
    var seed: Int
    var joined: Bool = false
    var mine: Bool = false
    var posts: [Post] = []
}

/// Темы групп — для создания и поиска.
let groupTopics = ["Музыка", "Фото", "Путешествия", "Книги", "Природа", "Искусство", "Кино", "Спорт"]

/// PRISM — короткое вертикальное видео. Пока сервера и загрузки видео нет,
/// «кадр» рисуется анимацией (см. PrismArt) по seed — один и тот же ролик
/// всегда выглядит одинаково.
///
/// saved — в закладках («Сохранённое» в профиле).
struct Prism: Identifiable, Equatable, Codable {
    let id: Int64
    var author: User
    var caption: String
    var music: String
    var seed: Int
    var likes: Int
    var comments: Int
    var liked: Bool = false
    var durationMs: Double = 9_000
    var saved: Bool = false
    var commentList: [Comment] = []
}

/// То, что человек рассказывает о себе в профиле. Сохраняется вместе с аккаунтом.
struct ProfileInfo: Equatable {
    var bio: String = ""
    var city: String = ""
    var birthday: String = ""
    var website: String = ""
    var interests: [String] = []
}

/// Повторяемый генератор случайных чисел: одна и та же картинка по
/// одному seed при каждом запуске (SplitMix64).
struct SeededRandom {
    private var state: UInt64

    init(_ seed: Int) {
        state = UInt64(bitPattern: Int64(seed)) &+ 0x9E37_79B9_7F4A_7C15
    }

    mutating func next() -> UInt64 {
        state = state &+ 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }

    /// 0 ..< 1
    mutating func nextDouble() -> Double {
        Double(next() >> 11) / Double(UInt64(1) << 53)
    }

    /// 0 ..< bound
    mutating func nextInt(_ bound: Int) -> Int {
        Int(nextDouble() * Double(bound))
    }
}
