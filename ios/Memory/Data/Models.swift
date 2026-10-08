import Foundation

/// Человек в Memory. hue — 0...1, оттенок его аватара-градиента.
/// followers / followingCount — числа для карточки; у «себя» подписки
/// считаются по настоящему списку.
struct User: Identifiable, Equatable {
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

/// Запись в ленте. artSeed != nil — к записи приложена «картинка»;
/// пока сервера нет, она рисуется процедурно (туманность), см. Nebula.
struct Post: Identifiable, Equatable {
    let id: Int64
    var author: User
    var text: String
    var minutesAgo: Int
    var artSeed: Int? = nil
    var likes: Int = 0
    var liked: Bool = false
    var comments: Int = 0
}

struct Message: Identifiable, Equatable {
    let id: Int64
    let fromMe: Bool
    let text: String
    let time: String
}

struct Chat: Identifiable, Equatable {
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
struct MemoryGroup: Identifiable, Equatable {
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

/// Короткое вертикальное видео. Пока сервера и загрузки видео нет, «кадр»
/// рисуется анимацией (см. ReelArt) по seed — один и тот же Reels всегда
/// выглядит одинаково.
struct Reel: Identifiable, Equatable {
    let id: Int64
    var author: User
    var caption: String
    var music: String
    var seed: Int
    var likes: Int
    var comments: Int
    var liked: Bool = false
    var durationMs: Double = 9_000
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
