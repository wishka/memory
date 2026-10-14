// Сгенерировано scripts/make_sounds.py из scripts/sounds/catalog.py — не править руками.
import Foundation

/// Стили звуков — разделы в выборе звука.
enum SoundStyle: String, CaseIterable, Identifiable {
    case glass = "glass"
    case wood = "wood"
    case pop = "pop"
    case keys = "keys"
    case synth = "synth"
    case ambient = "ambient"
    case eightBit = "8bit"
    case minimal = "minimal"
    case nature = "nature"
    case phone = "phone"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .glass: return L10n.shared.pick(ru: "Стекло 2.0", en: "Glass", hy: "Ապակի")
        case .wood: return L10n.shared.pick(ru: "Маримба и калимба", en: "Marimba & kalimba", hy: "Մարիմբա և կալիմբա")
        case .pop: return L10n.shared.pick(ru: "Поп и пузыри", en: "Pops & bubbles", hy: "Փոփ և պղպջակներ")
        case .keys: return L10n.shared.pick(ru: "Электропиано и лоу-фай", en: "Keys & lo-fi", hy: "Էլեկտրադաշնամուր և լո-ֆայ")
        case .synth: return L10n.shared.pick(ru: "Синт", en: "Synth", hy: "Սինթ")
        case .ambient: return L10n.shared.pick(ru: "Амбиент", en: "Ambient", hy: "Էմբիենթ")
        case .eightBit: return L10n.shared.pick(ru: "8-бит", en: "8-bit", hy: "8-բիթ")
        case .minimal: return L10n.shared.pick(ru: "Минимал", en: "Minimal", hy: "Մինիմալ")
        case .nature: return L10n.shared.pick(ru: "Природа", en: "Nature", hy: "Բնություն")
        case .phone: return L10n.shared.pick(ru: "Телефон", en: "Phone", hy: "Հեռախոս")
        }
    }
}

/// Звуки уведомлений Memory. Файлы — Sounds/snd_<id>.caf. free — доступен
/// без Memory+ (звук по умолчанию для вида доступен всегда).
enum NotifySound: String, CaseIterable, Identifiable {
    case crystal = "crystal"
    case stardust = "stardust"
    case moon = "moon"
    case prism = "prism"
    case droplet = "droplet"
    case marimba = "marimba"
    case marimba3 = "marimba3"
    case kalimba = "kalimba"
    case vibes = "vibes"
    case bamboo = "bamboo"
    case pop = "pop"
    case bubble = "bubble"
    case bloop = "bloop"
    case taptap = "taptap"
    case boing = "boing"
    case swoosh = "swoosh"
    case rhodes = "rhodes"
    case lofi = "lofi"
    case jazz = "jazz"
    case fmbell = "fmbell"
    case spark = "spark"
    case neon = "neon"
    case pulse = "pulse"
    case future = "future"
    case glide = "glide"
    case dawn = "dawn"
    case aurora = "aurora"
    case deep = "deep"
    case coin = "coin"
    case levelup = "levelup"
    case powerup = "powerup"
    case pixel = "pixel"
    case tick = "tick"
    case knock = "knock"
    case softtap = "softtap"
    case ping = "ping"
    case waterdrop = "waterdrop"
    case bird = "bird"
    case windchime = "windchime"
    case stringSound = "string"
    case comet = "comet"
    case nebula = "nebula"
    case orbit = "orbit"
    case handpan = "handpan"
    case xylophone = "xylophone"
    case musicbox = "musicbox"
    case popchime = "popchime"
    case celesta = "celesta"
    case laser = "laser"
    case wave = "wave"
    case space = "space"
    case chipmsg = "chipmsg"
    case chest = "chest"
    case switchSound = "switch"
    case ding = "ding"
    case cricket = "cricket"
    case harp = "harp"
    case system = "system"
    case silent = "silent"

    var id: String { rawValue }

    private var names: (ru: String, en: String, hy: String) {
        switch self {
        case .crystal: return ("Кристалл", "Crystal", "Բյուրեղ")
        case .stardust: return ("Звёздная пыль", "Stardust", "Աստղափոշի")
        case .moon: return ("Луна", "Moon", "Լուսին")
        case .prism: return ("Призма", "Prism", "Պրիզմա")
        case .droplet: return ("Капля света", "Light drop", "Լույսի կաթիլ")
        case .marimba: return ("Маримба", "Marimba", "Մարիմբա")
        case .marimba3: return ("Маримба: три ноты", "Marimba trio", "Մարիմբա․ երեք նոտա")
        case .kalimba: return ("Калимба", "Kalimba", "Կալիմբա")
        case .vibes: return ("Вибрафон", "Vibes", "Վիբրաֆոն")
        case .bamboo: return ("Бамбук", "Bamboo", "Բամբուկ")
        case .pop: return ("Поп", "Pop", "Փոփ")
        case .bubble: return ("Пузырь", "Bubble", "Պղպջակ")
        case .bloop: return ("Блуп-блуп", "Bloop", "Բլուպ-բլուպ")
        case .taptap: return ("Тап-тап", "Tap-tap", "Թափ-թափ")
        case .boing: return ("Пружинка", "Spring", "Զսպանակ")
        case .swoosh: return ("Отправлено", "Sent", "Ուղարկված է")
        case .rhodes: return ("Родес", "Rhodes", "Ռոդս")
        case .lofi: return ("Лоу-фай", "Lo-fi", "Լո-ֆայ")
        case .jazz: return ("Джаз", "Jazz", "Ջազ")
        case .fmbell: return ("Цифровой колокол", "Digital bell", "Թվային զանգ")
        case .spark: return ("Искра", "Spark", "Կայծ")
        case .neon: return ("Неон", "Neon", "Նեոն")
        case .pulse: return ("Пульс", "Pulse", "Զարկ")
        case .future: return ("Фьючер", "Future", "Ֆյուչեր")
        case .glide: return ("Скольжение", "Glide", "Սահք")
        case .dawn: return ("Рассвет", "Dawn", "Արշալույս")
        case .aurora: return ("Сияние", "Aurora", "Բևեռափայլ")
        case .deep: return ("Глубина", "Deep", "Խորություն")
        case .coin: return ("Монетка", "Coin", "Մետաղադրամ")
        case .levelup: return ("Новый уровень", "Level up", "Նոր մակարդակ")
        case .powerup: return ("Бонус", "Power-up", "Բոնուս")
        case .pixel: return ("Пиксель", "Pixel", "Պիքսել")
        case .tick: return ("Тик", "Tick", "Տիկ")
        case .knock: return ("Тук-тук", "Knock", "Թակ-թակ")
        case .softtap: return ("Мягкий тап", "Soft tap", "Փափուկ հպում")
        case .ping: return ("Пинг", "Ping", "Պինգ")
        case .waterdrop: return ("Капля", "Water drop", "Կաթիլ")
        case .bird: return ("Птичка", "Bird", "Թռչնակ")
        case .windchime: return ("Колокольчики ветра", "Wind chimes", "Քամու զանգակներ")
        case .stringSound: return ("Струна", "String", "Լար")
        case .comet: return ("Комета", "Comet", "Գիսաստղ")
        case .nebula: return ("Туманность", "Nebula", "Միգամածություն")
        case .orbit: return ("Орбита", "Orbit", "Ուղեծիր")
        case .handpan: return ("Ханг", "Handpan", "Հանգ")
        case .xylophone: return ("Ксилофон", "Xylophone", "Քսիլոֆոն")
        case .musicbox: return ("Шкатулка", "Music box", "Երաժշտական տուփ")
        case .popchime: return ("Поп-звон", "Pop chime", "Փոփ-զանգ")
        case .celesta: return ("Челеста", "Celesta", "Չելեստա")
        case .laser: return ("Лазер", "Laser", "Լազեր")
        case .wave: return ("Волна", "Wave", "Ալիք")
        case .space: return ("Космос", "Space", "Տիեզերք")
        case .chipmsg: return ("Пиксельное сообщение", "Pixel message", "Պիքսելային հաղորդագրություն")
        case .chest: return ("Сундук", "Treasure", "Սնդուկ")
        case .switchSound: return ("Переключатель", "Switch", "Անջատիչ")
        case .ding: return ("Дзынь", "Ding", "Զնգոց")
        case .cricket: return ("Сверчок", "Cricket", "Ծղրիդ")
        case .harp: return ("Арфа", "Harp", "Տավիղ")
        case .system: return ("Как в телефоне", "Phone default", "Ինչպես հեռախոսում")
        case .silent: return ("Без звука", "Silent", "Առանց ձայնի")
        }
    }

    var title: String { L10n.shared.pick(ru: names.ru, en: names.en, hy: names.hy) }

    var style: SoundStyle {
        switch self {
        case .crystal, .stardust, .moon, .prism, .droplet, .comet, .nebula, .orbit: return .glass
        case .marimba, .marimba3, .kalimba, .vibes, .bamboo, .handpan, .xylophone, .musicbox: return .wood
        case .pop, .bubble, .bloop, .taptap, .boing, .swoosh, .popchime: return .pop
        case .rhodes, .lofi, .jazz, .fmbell, .celesta: return .keys
        case .spark, .neon, .pulse, .future, .glide, .laser, .wave: return .synth
        case .dawn, .aurora, .deep, .space: return .ambient
        case .coin, .levelup, .powerup, .pixel, .chipmsg, .chest: return .eightBit
        case .tick, .knock, .softtap, .ping, .switchSound, .ding: return .minimal
        case .waterdrop, .bird, .windchime, .stringSound, .cricket, .harp: return .nature
        case .system, .silent: return .phone
        }
    }

    var free: Bool {
        switch self {
        case .crystal, .droplet, .marimba, .bamboo, .pop, .bubble, .rhodes, .jazz, .pulse, .glide, .dawn, .coin, .tick, .ping, .waterdrop, .bird, .system, .silent: return true
        default: return false
        }
    }

    /// Имя файла без расширения; nil — звук телефона или тишина.
    var file: String? { self == .system || self == .silent ? nil : "snd_\(rawValue)" }

    static func of(_ id: String?) -> NotifySound? { allCases.first { $0.rawValue == id } }

    /// Звуки стиля — в порядке каталога.
    static func inStyle(_ style: SoundStyle) -> [NotifySound] { allCases.filter { $0.style == style } }

    /// Звук по умолчанию: у каждого вида — свой, чтобы различать на слух.
    static func `default`(_ kind: NudgeKind) -> NotifySound {
        switch kind {
        case .messages: return .crystal
        case .prism: return .prism
        case .reactions: return .stardust
        case .follows: return .orbit
        case .groups: return .moon
        case .friends: return .comet
        case .recap: return .nebula
        }
    }
}
