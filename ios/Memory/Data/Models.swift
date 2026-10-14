import Foundation

/// Человек в Memory. hue — 0...1, оттенок его аватара-градиента.
/// followers / followingCount — числа для карточки; followingCount —
/// сколько у человека друзей (у «себя» друзья считаются по настоящему списку).
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
    /// Своё фото вместо букв: имя файла круглой обрезки в папке вложений;
    /// nil или "" — буквы. Необязательное: старые сохранения читаются без него.
    var avatar: String? = nil
    /// Закрытый профиль: записи и чек-ины видят только друзья.
    /// Необязательное — для старых сохранений; снаружи — closed.
    var closedFlag: Bool? = nil

    var hasAvatar: Bool { !(avatar ?? "").isEmpty }
    var closed: Bool { closedFlag ?? false }

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
    /// Голосовое сообщение: запись с микрофона, играет в пузыре с волной.
    /// Новые поля необязательные — старые сохранения читаются без них.
    var voiceFlag: Bool? = nil
    /// Длительность звука, мс — у голосовых.
    var durationMs: Int64? = nil
    /// Волна голосового: уровни громкости 0…100 по порядку.
    var waveform: [Int]? = nil
    /// Свой стикер из «Студии»: показывается без рамки, как картинка.
    var stickerFlag: Bool? = nil
    /// Живой стикер: как он двигается (StickerAnim.id); nil или "" — неподвижный.
    var animationId: String? = nil
    /// Покадровый стикер: имена файлов кадров по порядку (первый — fileName);
    /// nil или пусто — один кадр.
    var frameFiles: [String]? = nil
    /// Кадров в секунду у покадрового стикера.
    var frameRate: Int? = nil
    /// Отмеченные на фото люди: где на снимке (доли 0…1) и кто.
    var tags: [PhotoTag] = []

    var voice: Bool { voiceFlag ?? false }
    var sticker: Bool { stickerFlag ?? false }

    var animation: String {
        get { animationId ?? "" }
        set { animationId = newValue.isEmpty ? nil : newValue }
    }
    var frames: [String] {
        get { frameFiles ?? [] }
        set { frameFiles = newValue.isEmpty ? nil : newValue }
    }
    var fps: Int {
        get { frameRate ?? 0 }
        set { frameRate = newValue == 0 ? nil : newValue }
    }

    /// Гифка: играет сама, без рамки «видео».
    var isGif: Bool {
        kind == .photo && ((fileName as NSString).pathExtension.lowercased() == "gif" || (name as NSString).pathExtension.lowercased() == "gif")
    }

    /// Стикер, который двигается: эффект или кадры.
    var animated: Bool { !animation.isEmpty || frames.count > 1 }

    /// Музыка — документ со звуком: в записи он играет прямо в карточке.
    var isAudio: Bool {
        kind == .document && !voice && audioExtensions.contains((fileName as NSString).pathExtension.lowercased())
    }
}

extension Attachment {
    private enum CodingKeys: String, CodingKey {
        case id, kind, fileName, name, size, voiceFlag, durationMs, waveform, stickerFlag, animationId, frameFiles, frameRate, tags
    }

    /// Чтение с запасом: отметки на фото появились позже — старые сохранения читаются без них.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        kind = try c.decode(MediaKind.self, forKey: .kind)
        fileName = try c.decode(String.self, forKey: .fileName)
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? ""
        size = try c.decodeIfPresent(Int64.self, forKey: .size) ?? 0
        voiceFlag = try c.decodeIfPresent(Bool.self, forKey: .voiceFlag)
        durationMs = try c.decodeIfPresent(Int64.self, forKey: .durationMs)
        waveform = try c.decodeIfPresent([Int].self, forKey: .waveform)
        stickerFlag = try c.decodeIfPresent(Bool.self, forKey: .stickerFlag)
        animationId = try c.decodeIfPresent(String.self, forKey: .animationId)
        frameFiles = try c.decodeIfPresent([String].self, forKey: .frameFiles)
        frameRate = try c.decodeIfPresent(Int.self, forKey: .frameRate)
        tags = try c.decodeIfPresent([PhotoTag].self, forKey: .tags) ?? []
    }
}

/// Человек, отмеченный на фото: точка на снимке в долях ширины и высоты.
struct PhotoTag: Equatable, Codable, Hashable {
    let userId: Int
    let x: Double
    let y: Double
}

extension PhotoTag {
    /// Ключи — как у Android (StateStore.kt): user, x, y.
    private enum CodingKeys: String, CodingKey {
        case userId = "user"
        case x, y
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        userId = try c.decodeIfPresent(Int.self, forKey: .userId) ?? 0
        x = try c.decodeIfPresent(Double.self, forKey: .x) ?? 0.5
        y = try c.decodeIfPresent(Double.self, forKey: .y) ?? 0.5
    }
}

/// Расширения звуковых файлов — для вложений-«музыки».
let audioExtensions: Set<String> = ["mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "aiff", "caf"]

/// Комментарий к записи или PRISM. createdAt — момент написания; nil —
/// у демо-комментариев, тогда время из minutesAgo.
struct Comment: Identifiable, Equatable, Codable {
    let id: Int64
    var author: User
    var text: String
    var minutesAgo: Int = 0
    var createdAt: Date? = nil
    /// Лайки комментария. Хранятся необязательными — старые сохранения
    /// читаются без них; снаружи — likes и liked.
    var likeCount: Int? = nil
    var likedByMe: Bool? = nil
    /// Ответ кому-то в обсуждении: имя того, кому ответили; nil — просто комментарий.
    var replyTo: String? = nil
    /// Стикер вместо текста или вместе с ним.
    var sticker: Attachment? = nil

    var likes: Int {
        get { likeCount ?? 0 }
        set { likeCount = newValue }
    }
    var liked: Bool {
        get { likedByMe ?? false }
        set { likedByMe = newValue }
    }
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
    /// Чек-ин: «смотрю фильм», «пробежал 5 км»… nil — обычная запись.
    var checkIn: CheckIn? = nil
    /// Опрос в записи; nil — нет.
    var poll: Poll? = nil
    /// Когда запись правили; nil — не правили. Видно как «изменено».
    var editedAt: Date? = nil
    /// Репост: чью запись показываем внутри; text — свои слова к ней (может быть пустым).
    var repostOf: PostRef? = nil
    /// Сколько раз запись репостнули.
    var reposts: Int = 0
    /// Где это было: место, отмеченное в записи; nil — без места.
    var place: SharedPlace? = nil
}

extension Post {
    private enum CodingKeys: String, CodingKey {
        case id, author, text, minutesAgo, artSeed, likes, liked, comments, media, commentList, createdAt,
             checkIn, poll, editedAt, repostOf, reposts, place
    }

    /// Чтение с запасом: репосты и место появились позже — старые сохранения читаются без них.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int64.self, forKey: .id)
        author = try c.decode(User.self, forKey: .author)
        text = try c.decodeIfPresent(String.self, forKey: .text) ?? ""
        minutesAgo = try c.decodeIfPresent(Int.self, forKey: .minutesAgo) ?? 0
        artSeed = try c.decodeIfPresent(Int.self, forKey: .artSeed)
        likes = try c.decodeIfPresent(Int.self, forKey: .likes) ?? 0
        liked = try c.decodeIfPresent(Bool.self, forKey: .liked) ?? false
        comments = try c.decodeIfPresent(Int.self, forKey: .comments) ?? 0
        media = try c.decodeIfPresent([Attachment].self, forKey: .media) ?? []
        commentList = try c.decodeIfPresent([Comment].self, forKey: .commentList) ?? []
        createdAt = try c.decodeIfPresent(Date.self, forKey: .createdAt)
        checkIn = try c.decodeIfPresent(CheckIn.self, forKey: .checkIn)
        poll = try c.decodeIfPresent(Poll.self, forKey: .poll)
        editedAt = try c.decodeIfPresent(Date.self, forKey: .editedAt)
        repostOf = try c.decodeIfPresent(PostRef.self, forKey: .repostOf)
        reposts = try c.decodeIfPresent(Int.self, forKey: .reposts) ?? 0
        place = try c.decodeIfPresent(SharedPlace.self, forKey: .place)
    }
}

/// Запись внутри репоста или сообщения: копия сути на момент репоста —
/// чтобы читалось, даже если оригинал потом изменят или удалят.
/// photo — имя файла первого фото в папке вложений; "" — туманность по seed.
struct PostRef: Equatable, Codable {
    var postId: Int64
    var authorId: Int
    var authorName: String
    var text: String
    var photo: String = ""
    var seed: Int = 0
    /// Когда опубликован оригинал; nil — демо-запись без времени.
    var createdAt: Date? = nil
}

extension PostRef {
    /// Ключи — как у Android (StateStore.kt): postId, author, name, text, photo, seed, createdAt.
    private enum CodingKeys: String, CodingKey {
        case postId
        case authorId = "author"
        case authorName = "name"
        case text, photo, seed, createdAt
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        postId = try c.decodeIfPresent(Int64.self, forKey: .postId) ?? 0
        authorId = try c.decodeIfPresent(Int.self, forKey: .authorId) ?? 0
        authorName = try c.decodeIfPresent(String.self, forKey: .authorName) ?? ""
        text = try c.decodeIfPresent(String.self, forKey: .text) ?? ""
        photo = try c.decodeIfPresent(String.self, forKey: .photo) ?? ""
        seed = try c.decodeIfPresent(Int.self, forKey: .seed) ?? 0
        createdAt = try c.decodeIfPresent(Date.self, forKey: .createdAt)
    }
}

/// Минуты с момента createdAt, а для демо-данных — заданные заранее.
func minutesSince(_ createdAt: Date?, _ minutesAgo: Int, now: Date = Date()) -> Int {
    guard let createdAt else { return minutesAgo }
    return max(0, Int(now.timeIntervalSince(createdAt) / 60))
}

/// На какое сообщение отвечают: номер, чьё оно и начало текста — для цитаты над ответом.
struct ReplyRef: Equatable, Codable {
    let messageId: Int64
    let author: String
    let preview: String
}

/// Сообщение; text может быть пустым, если отправлены только вложения.
/// replyTo — ответ на другое сообщение; forwardedFrom — имя автора
/// пересланного сообщения. Оба необязательные: старые сохранённые
/// переписки читаются без них.
struct Message: Identifiable, Equatable, Codable {
    let id: Int64
    let fromMe: Bool
    let text: String
    let time: String
    var attachments: [Attachment] = []
    var replyTo: ReplyRef? = nil
    var forwardedFrom: String? = nil
    /// Опрос в сообщении; nil — нет.
    var poll: Poll? = nil
    /// Геолокация: место на карте; nil — нет.
    var place: SharedPlace? = nil
    /// Товар с витрины группы — вопрос продавцу; nil — нет.
    var product: ProductRef? = nil
    /// Ушло зашифрованным (секретная переписка): сервер видит только конверт.
    /// Необязательное — старые сохранения читаются без него; снаружи — secret.
    var secretFlag: Bool? = nil
    /// Когда исчезнет; nil — не исчезает.
    var expiresAt: Date? = nil
    /// Ответ на историю: на какую; nil — обычное сообщение.
    var story: StoryRef? = nil
    /// Запись, которой поделились в переписке; nil — нет.
    var post: PostRef? = nil
    /// Звонок: строка «Видеозвонок · 3:12» или «Пропущенный звонок»; nil — не звонок.
    var call: CallRecord? = nil

    var secret: Bool {
        get { secretFlag ?? false }
        set { secretFlag = newValue ? true : nil }
    }
}

/// Товар в сообщении: копия названия, цены и фото на момент вопроса — чтобы
/// сообщение читалось, даже если товар потом изменят или уберут.
/// photo — имя файла первого фото в папке вложений; "" — туманность по seed.
struct ProductRef: Equatable, Codable {
    var groupId: Int
    var productId: Int64
    var title: String
    var price: Int64
    var photo: String = ""
    var seed: Int = 0
}

extension ProductRef {
    private enum CodingKeys: String, CodingKey {
        case groupId, productId, title, price, photo, seed
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        groupId = try c.decodeIfPresent(Int.self, forKey: .groupId) ?? 0
        productId = try c.decodeIfPresent(Int64.self, forKey: .productId) ?? 0
        title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        price = try c.decodeIfPresent(Int64.self, forKey: .price) ?? 0
        photo = try c.decodeIfPresent(String.self, forKey: .photo) ?? ""
        seed = try c.decodeIfPresent(Int.self, forKey: .seed) ?? 0
    }
}

/// Место, которым поделились в переписке: точка и подпись («Я здесь», адрес).
struct SharedPlace: Equatable, Codable {
    var lat: Double
    var lon: Double
    var title: String = ""

    var at: LatLon { LatLon(lat: lat, lon: lon) }

    /// Координаты для подписи: 55.75580, 37.61730.
    var coords: String { String(format: "%.5f, %.5f", locale: Locale(identifier: "en_US_POSIX"), lat, lon) }
}

/// Переписка. pinnedId — закреплённое сообщение: видно полоской вверху
/// (необязательное — старые сохранённые переписки читаются без него).
/// onTop — сама переписка закреплена вверху списка; muted — без звука.
struct Chat: Identifiable, Equatable, Codable {
    let id: Int
    var peer: User
    var messages: [Message]
    var unread: Int = 0
    var typing: Bool = false
    var pinnedId: Int64? = nil
    var onTopFlag: Bool? = nil
    var mutedFlag: Bool? = nil
    /// Свой фон этой переписки; nil — общий из настроек.
    var backdrop: Backdrop? = nil
    /// Сквозное шифрование, сверка кода и исчезающие сообщения —
    /// необязательные, для старых сохранений; снаружи — secret, verified, disappearMs.
    var secretFlag: Bool? = nil
    var verifiedFlag: Bool? = nil
    var disappearMsValue: Int64? = nil

    /// Сквозное шифрование: новые сообщения уходят конвертами, которые читают только два телефона.
    var secret: Bool {
        get { secretFlag ?? false }
        set { secretFlag = newValue }
    }
    /// Код безопасности сверен с собеседником.
    var verified: Bool {
        get { verifiedFlag ?? false }
        set { verifiedFlag = newValue }
    }
    /// Исчезающие сообщения: через сколько мс исчезают новые; 0 — выключено.
    var disappearMs: Int64 {
        get { disappearMsValue ?? 0 }
        set { disappearMsValue = newValue == 0 ? nil : newValue }
    }

    var onTop: Bool {
        get { onTopFlag ?? false }
        set { onTopFlag = newValue }
    }
    var muted: Bool {
        get { mutedFlag ?? false }
        set { mutedFlag = newValue }
    }

    var last: Message? { messages.last }
    var pinned: Message? { pinnedId.flatMap { id in messages.first { $0.id == id } } }
}

/// «5 мин», «2 ч», «вчера», «3 дн» — время записи в ленте.
func agoTitle(_ minutes: Int) -> String {
    if minutes < 1 { return t("сейчас") }
    if minutes < 60 { return tf("%s мин", minutes) }
    if minutes < 60 * 24 { return tf("%s ч", minutes / 60) }
    if minutes < 60 * 48 { return t("вчера") }
    return tf("%s дн", minutes / (60 * 24))
}

/// Склонение: plural(5, "запись", "записи", "записей") → «5 записей».
/// По-английски — «1 post» / «5 posts»: формы берутся из словаря.
func plural(_ n: Int, _ one: String, _ few: String, _ many: String) -> String {
    if L10n.shared.english { return "\(n) \(t(n == 1 ? one : many))" }
    // По-армянски после числа — единственное число: «5 գրառում»
    if L10n.shared.armenian { return "\(n) \(t(one))" }
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
    /// Уведомления группы выключены; необязательное — для старых сохранений.
    var mutedFlag: Bool? = nil
    /// Своя заставка — фото; nil — туманность по seed.
    var cover: Attachment? = nil
    /// Закреплённая запись — первой на странице группы.
    var pinnedPostId: Int64? = nil
    /// Писать в группу может только создатель; необязательное — для старых сохранений.
    var onlyOwnerPostsFlag: Bool? = nil
    /// Кто создал (для чужих групп — кому писать о товарах); 0 — неизвестно.
    var ownerId: Int = 0
    /// Подробное описание, сайт и контакты.
    var info: GroupInfo = GroupInfo()
    /// Витрина: товары группы.
    var shop: [Product] = []
    /// Фон страницы группы; nil — как везде.
    var backdrop: Backdrop? = nil
    /// Цвет группы (GroupAccent.id); "" — сиреневый, как везде.
    var accent: String = ""

    var muted: Bool {
        get { mutedFlag ?? false }
        set { mutedFlag = newValue }
    }

    var onlyOwnerPosts: Bool {
        get { onlyOwnerPostsFlag ?? false }
        set { onlyOwnerPostsFlag = newValue }
    }
}

extension MemoryGroup {
    private enum CodingKeys: String, CodingKey {
        case id, name, description, topic, members, seed, joined, mine, posts, mutedFlag, cover, pinnedPostId,
             onlyOwnerPostsFlag, ownerId, info, shop, backdrop, accent
    }

    /// Чтение с запасом: витрина, сведения и оформление появились позже —
    /// старые сохранения читаются без них.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name)
        description = try c.decodeIfPresent(String.self, forKey: .description) ?? ""
        topic = try c.decodeIfPresent(String.self, forKey: .topic) ?? ""
        members = try c.decodeIfPresent(Int.self, forKey: .members) ?? 0
        seed = try c.decodeIfPresent(Int.self, forKey: .seed) ?? 0
        joined = try c.decodeIfPresent(Bool.self, forKey: .joined) ?? false
        mine = try c.decodeIfPresent(Bool.self, forKey: .mine) ?? false
        posts = try c.decodeIfPresent([Post].self, forKey: .posts) ?? []
        mutedFlag = try c.decodeIfPresent(Bool.self, forKey: .mutedFlag)
        cover = try c.decodeIfPresent(Attachment.self, forKey: .cover)
        pinnedPostId = try c.decodeIfPresent(Int64.self, forKey: .pinnedPostId)
        onlyOwnerPostsFlag = try c.decodeIfPresent(Bool.self, forKey: .onlyOwnerPostsFlag)
        ownerId = try c.decodeIfPresent(Int.self, forKey: .ownerId) ?? 0
        info = try c.decodeIfPresent(GroupInfo.self, forKey: .info) ?? GroupInfo()
        shop = try c.decodeIfPresent([Product].self, forKey: .shop) ?? []
        backdrop = try c.decodeIfPresent(Backdrop.self, forKey: .backdrop)
        accent = try c.decodeIfPresent(String.self, forKey: .accent) ?? ""
    }
}

/// Интересы в профиле — те же темы, без «Другое».
var interestTopics: [String] { groupTopics.filter { $0 != "Другое" } }

/// Темы групп — для создания и поиска, по алфавиту.
let groupTopics = [
    "Авто и мото", "Анимация и комиксы", "Архитектура", "Астрономия", "Бизнес", "Блогинг",
    "Дизайн", "Дети и родители", "Дом и интерьер", "Еда и кулинария", "Животные", "Здоровье",
    "Игры", "Иностранные языки", "Искусство", "История", "Кино", "Книги", "Красота и мода",
    "Музыка", "Наука", "Образование", "Психология", "Природа", "Программирование", "Путешествия",
    "Работа и карьера", "Рукоделие", "Сад и огород", "Спорт", "Танцы", "Театр", "Технологии",
    "Фитнес", "Философия", "Финансы", "Фото", "Юмор", "Другое",
]

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
    /// Свой ролик: готовое видео после редактора; nil — демо, «кадр»
    /// рисует PrismArt. Необязательные — старые сохранения читаются без них.
    var video: Attachment? = nil
    /// Обложка своего ролика — кадр, выбранный в редакторе.
    var cover: Attachment? = nil
    var createdAt: Date? = nil
}

/// Трек в «Моей музыке». Файл — копия в папке приложения (fileName), его
/// видит и слушает только владелец: пока сервера нет, это личная
/// библиотека на телефоне.
struct MusicTrack: Identifiable, Equatable, Codable, Hashable {
    let id: String
    var title: String
    var artist: String
    var fileName: String
    var durationMs: Int64 = 0
    var addedAt: Date? = nil
    /// Лицензия трека из медиатеки («CC BY 4.0»); nil — свой файл.
    var license: String? = nil
    /// Страница трека на Jamendo или Openverse — для указания автора.
    var sourceUrl: String? = nil

    var url: URL { MediaFiles.directory.appendingPathComponent(fileName) }
}

/// Плейлист: название и треки по порядку.
struct Playlist: Identifiable, Equatable, Codable {
    let id: Int64
    var name: String
    var trackIds: [String] = []
}

/// «3:07» — длительность трека или ролика.
func durationTitle(_ ms: Int64) -> String {
    let total = max(0, ms / 1000)
    return "\(total / 60):" + String(format: "%02d", Int(total % 60))
}

/// То, что человек рассказывает о себе в профиле. Сохраняется вместе с аккаунтом.
struct ProfileInfo: Equatable {
    var bio: String = ""
    var city: String = ""
    var birthday: String = ""
    var website: String = ""
    var interests: [String] = []
    /// Имя файла своего фото; "" — аватар из букв.
    var avatar: String = ""
}

/// Кому видно или кому можно: всем, друзьям, никому. following — друзья:
/// имя и сохранённое значение прежние, чтобы читались старые настройки.
enum Audience: String, Codable, CaseIterable {
    case everyone = "Все"
    case following = "Мои подписки"
    case nobody = "Никто"

    var title: String {
        switch self {
        case .everyone: return t("Все")
        case .following: return t("Друзья")
        case .nobody: return t("Никто")
        }
    }
}

/// Настройки приватности. Пока сервера нет, хранятся на телефоне вместе с аккаунтом.
struct Privacy: Equatable, Codable {
    /// Кто видит город, день рождения, сайт и интересы.
    var profileInfo: Audience = .everyone
    /// Кто видит ваших друзей.
    var followLists: Audience = .everyone
    /// Кто может написать вам.
    var messages: Audience = .everyone
    /// Кто может комментировать ваши записи.
    var comments: Audience = .everyone
    /// Показывать другим, что вы в сети.
    var showOnline: Bool = true
    /// Показывать день рождения.
    var showBirthday: Bool = true
    /// Закрытый профиль: записи и чек-ины видят только друзья. Заявки
    /// в друзья от этого не зависят. Необязательное — для старых сохранений.
    var closedProfileFlag: Bool? = nil
    /// Персональные рекомендации на полке (по оценкам и вкусу похожих
    /// людей). Необязательное — для старых сохранений; снаружи — personalRecs.
    var personalRecsFlag: Bool? = nil
    /// Кто может мне звонить. Необязательное — для старых сохранений; снаружи — calls.
    var callsAudience: Audience? = nil

    /// Кто может мне звонить.
    var calls: Audience {
        get { callsAudience ?? .everyone }
        set { callsAudience = newValue }
    }

    var closedProfile: Bool {
        get { closedProfileFlag ?? false }
        set { closedProfileFlag = newValue }
    }

    /// Выключены — полка ничего не подбирает (отказ от рекомендаций).
    var personalRecs: Bool {
        get { personalRecsFlag ?? true }
        set { personalRecsFlag = newValue }
    }
}

/// Какие уведомления присылать. all = false выключает все разом.
struct Notifications: Equatable, Codable {
    var all: Bool = true
    var messages: Bool = true
    var reactions: Bool = true
    var groups: Bool = true
    var follows: Bool = true
    /// Что делают друзья и итоги — необязательные, для старых сохранений.
    var friendsFlag: Bool? = nil
    var recapFlag: Bool? = nil
    /// Новые PRISM друзей — необязательное, для старых сохранений.
    var prismFlag: Bool? = nil

    /// Новые PRISM друзей.
    var prism: Bool {
        get { prismFlag ?? true }
        set { prismFlag = newValue }
    }

    /// Что делают друзья: чек-ины, фильмы, книги, тренировки.
    var friends: Bool {
        get { friendsFlag ?? true }
        set { friendsFlag = newValue }
    }
    /// Итоги месяца и года.
    var recap: Bool {
        get { recapFlag ?? true }
        set { recapFlag = newValue }
    }
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
