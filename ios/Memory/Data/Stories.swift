import Foundation

/// Кто посмотрел историю и когда; liked — поставил сердечко.
struct StoryView: Equatable, Codable {
    let userId: Int
    let at: Date
    var liked: Bool = false
}

extension StoryView {
    /// Ключи — как у Android (StateStore.kt): user, at, liked.
    private enum CodingKeys: String, CodingKey {
        case userId = "user"
        case at, liked
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        userId = try c.decodeIfPresent(Int.self, forKey: .userId) ?? 0
        at = try c.decodeIfPresent(Date.self, forKey: .at) ?? Date(timeIntervalSince1970: 0)
        liked = try c.decodeIfPresent(Bool.self, forKey: .liked) ?? false
    }
}

/// История: фото, видео или текст на градиенте — живёт сутки, потом
/// уходит в архив автора (из архива её можно добавить в «Актуальное»).
/// closeFriends — видят только «Близкие друзья» автора.
/// seed — демо-картинка (туманность), пока у истории нет своего фото.
/// То же — в Stories.kt.
struct Story: Identifiable, Equatable, Codable {
    let id: Int64
    let authorId: Int
    let createdAt: Date
    var media: Attachment? = nil
    var text: String = ""
    /// Фон текстовой истории — номер градиента в Stories.backgrounds.
    var bg: Int = 0
    var seed: Int = 0
    var closeFriends: Bool = false
    var views: [StoryView] = []

    func active(_ now: Date) -> Bool { Stories.ms(from: createdAt, to: now) < Stories.ttlMs }

    var likes: Int { views.filter(\.liked).count }
}

extension Story {
    /// Ключи — как у Android (StateStore.kt): id, author, createdAt, media, text, bg, seed, close, views.
    private enum CodingKeys: String, CodingKey {
        case id
        case authorId = "author"
        case createdAt, media, text, bg, seed
        case closeFriends = "close"
        case views
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(Int64.self, forKey: .id) ?? 0
        authorId = try c.decodeIfPresent(Int.self, forKey: .authorId) ?? 0
        createdAt = try c.decodeIfPresent(Date.self, forKey: .createdAt) ?? Date(timeIntervalSince1970: 0)
        media = try c.decodeIfPresent(Attachment.self, forKey: .media)
        text = try c.decodeIfPresent(String.self, forKey: .text) ?? ""
        bg = try c.decodeIfPresent(Int.self, forKey: .bg) ?? 0
        seed = try c.decodeIfPresent(Int.self, forKey: .seed) ?? 0
        closeFriends = try c.decodeIfPresent(Bool.self, forKey: .closeFriends) ?? false
        views = try c.decodeIfPresent([StoryView].self, forKey: .views) ?? []
    }
}

/// «Актуальное» в профиле: подборка историй с названием; живёт, пока не удалят.
struct Highlight: Identifiable, Equatable, Codable {
    let id: Int64
    let ownerId: Int
    var title: String
    var storyIds: [Int64]
    var coverId: Int64

    /// coverId == nil — обложка первая история подборки.
    init(id: Int64, ownerId: Int, title: String, storyIds: [Int64], coverId: Int64? = nil) {
        self.id = id
        self.ownerId = ownerId
        self.title = title
        self.storyIds = storyIds
        self.coverId = coverId ?? storyIds.first ?? 0
    }
}

extension Highlight {
    /// Ключи — как у Android (StateStore.kt): id, owner, title, stories, cover.
    private enum CodingKeys: String, CodingKey {
        case id
        case ownerId = "owner"
        case title
        case storyIds = "stories"
        case coverId = "cover"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(Int64.self, forKey: .id) ?? 0
        ownerId = try c.decodeIfPresent(Int.self, forKey: .ownerId) ?? 0
        title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        storyIds = try c.decodeIfPresent([Int64].self, forKey: .storyIds) ?? []
        coverId = try c.decodeIfPresent(Int64.self, forKey: .coverId) ?? 0
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(ownerId, forKey: .ownerId)
        try c.encode(title, forKey: .title)
        try c.encode(storyIds, forKey: .storyIds)
        try c.encode(coverId, forKey: .coverId)
    }
}

/// На какую историю ответили в переписке: копия сути, чтобы читалось и после того, как она исчезла.
/// photo — имя файла фото или видео истории в папке вложений; "" — туманность по seed или градиент bg.
struct StoryRef: Equatable, Codable {
    var storyId: Int64
    var authorId: Int
    var preview: String
    var seed: Int = 0
    var photo: String = ""
    var bg: Int = 0
}

extension StoryRef {
    /// Ключи — как у Android (StateStore.kt): storyId, author, preview, seed, photo, bg.
    private enum CodingKeys: String, CodingKey {
        case storyId
        case authorId = "author"
        case preview, seed, photo, bg
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        storyId = try c.decodeIfPresent(Int64.self, forKey: .storyId) ?? 0
        authorId = try c.decodeIfPresent(Int.self, forKey: .authorId) ?? 0
        preview = try c.decodeIfPresent(String.self, forKey: .preview) ?? ""
        seed = try c.decodeIfPresent(Int.self, forKey: .seed) ?? 0
        photo = try c.decodeIfPresent(String.self, forKey: .photo) ?? ""
        bg = try c.decodeIfPresent(Int.self, forKey: .bg) ?? 0
    }
}

/// Кружок в ряду историй: чей, его истории по порядку, все ли просмотрены, есть ли «для близких».
struct StoryCircle: Identifiable, Equatable {
    let user: User
    let stories: [Story]
    let seen: Bool
    let close: Bool

    var id: Int { user.id }
}

/// Что открыто в просмотре: по кругу авторов (или одна подборка «Актуального»).
struct StoryViewer: Equatable {
    var authorIds: [Int]
    var author: Int
    var index: Int
    /// Открыта подборка «Актуального» — её истории, без перехода к другим авторам.
    var highlightId: Int64? = nil
}

enum Stories {
    static let ttlMs: Int64 = 24 * 60 * 60 * 1000
    /// Сколько показывается одна история без касаний.
    static let showMs: Int64 = 5_000
    static let maxText = 300
    static let maxTitle = 24

    /// Градиенты текстовых историй: пары цветов ARGB — одинаковые на обеих платформах.
    /// Цвет для экрана — RGB(argb & 0xFFFFFF).
    static let backgrounds: [(UInt32, UInt32)] = [
        (0xFF844CAD, 0xFF195699),
        (0xFFFF9EDE, 0xFF844CAD),
        (0xFF307EC0, 0xFF0F153A),
        (0xFFBC92CD, 0xFF5F3F96),
        (0xFF1F7A6E, 0xFF195699),
        (0xFFE58F5B, 0xFF844CAD),
    ]

    /// Миллисекунды между двумя моментами (целые — как Long на Android).
    static func ms(from: Date, to: Date) -> Int64 {
        Int64((to.timeIntervalSince(from) * 1000).rounded())
    }

    /// Видит ли человек историю: свою — всегда; чужую — если он друг автора,
    /// а «для близких» — только если автор добавил его в близкие.
    static func visible(_ story: Story, viewerId: Int, isFriend: Bool, inAuthorsClose: Bool) -> Bool {
        story.authorId == viewerId || (isFriend && (!story.closeFriends || inAuthorsClose))
    }

    /// Ряд кружков: сначала с непросмотренными, потом просмотренные; внутри —
    /// по свежести последней истории. Свой кружок отдельно (первым в интерфейсе).
    static func rings(_ stories: [Story], _ people: [User], meId: Int, seen: Set<Int64>, now: Date) -> [StoryCircle] {
        var order: [Int] = []
        var byAuthor: [Int: [Story]] = [:]
        for story in stories where story.active(now) && story.authorId != meId {
            if byAuthor[story.authorId] == nil { order.append(story.authorId) }
            byAuthor[story.authorId, default: []].append(story)
        }
        let circles: [StoryCircle] = order.compactMap { id -> StoryCircle? in
            guard let user = people.first(where: { $0.id == id }) else { return nil }
            let sorted = (byAuthor[id] ?? []).stableSorted { $0.createdAt < $1.createdAt }
            return StoryCircle(
                user: user, stories: sorted,
                seen: sorted.allSatisfy { seen.contains($0.id) },
                close: sorted.contains { $0.closeFriends }
            )
        }
        func latest(_ c: StoryCircle) -> Date { c.stories.map(\.createdAt).max() ?? Date(timeIntervalSince1970: 0) }
        return circles.stableSorted { a, b in
            if a.seen != b.seen { return !a.seen }
            return latest(a) > latest(b)
        }
    }

    /// С какой истории начать: с первой непросмотренной, а если все видел — с первой.
    static func startIndex(_ stories: [Story], _ seen: Set<Int64>) -> Int {
        stories.firstIndex { !seen.contains($0.id) } ?? 0
    }

    /// Записать просмотр: один на человека, сердечко не теряется.
    static func view(_ story: Story, _ userId: Int, _ at: Date, liked: Bool = false) -> Story {
        let old = story.views.first { $0.userId == userId }
        let v = StoryView(userId: userId, at: old?.at ?? at, liked: liked || old?.liked == true)
        var s = story
        s.views = story.views.filter { $0.userId != userId } + [v]
        return s
    }

    /// Подпись истории для цитаты в переписке.
    static func preview(_ story: Story) -> String {
        let text = String(story.text.prefix(60))
        if !text.isEmpty { return text }
        if story.media?.kind == .video { return t("Видео") }
        if story.media != nil { return t("Фото") }
        return t("История")
    }

    static func ref(_ story: Story) -> StoryRef {
        StoryRef(storyId: story.id, authorId: story.authorId, preview: preview(story), seed: story.seed,
                 photo: story.media?.fileName ?? "", bg: story.bg)
    }

    static let demoTexts = ["Доброе утро!", "Кофе и дождь", "Лучший закат недели", "Угадайте, где я", "Новая книга — не оторваться", "Тренировка сделана"]
}
