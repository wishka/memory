import Foundation

// Memory+ — премиум-доступ. Тарифы, цены, пробный период и что в какой
// тариф входит. Описание для людей — в документе «Memory+: тарифы и
// премиум-функции». Те же тарифы и правила — в Premium.kt.
//
// Пока сервера нет, подписка хранится на телефоне, а оплата —
// демонстрационная (деньги не списываются). Настоящую оплату подключают
// через Google Play Billing и StoreKit: товары memory_plus_month,
// memory_plus_year, memory_business_month, memory_business_year.

/// Тариф.
enum PlanTier: String, CaseIterable, Identifiable {
    case free, plus, business

    var id: String { rawValue }

    private var ru: String {
        switch self {
        case .free: return "Бесплатно"
        case .plus: return "Memory+"
        case .business: return "Memory+ Бизнес"
        }
    }

    var title: String { t(ru) }

    var rank: Int {
        switch self {
        case .free: return 0
        case .plus: return 1
        case .business: return 2
        }
    }

    /// Этот тариф включает всё из other: Бизнес — и Memory+.
    func covers(_ other: PlanTier) -> Bool { rank >= other.rank }

    static func of(_ id: String?) -> PlanTier { allCases.first { $0.rawValue == id } ?? .free }
}

/// Срок оплаты.
enum BillingPeriod: String, CaseIterable, Identifiable {
    case month, year

    var id: String { rawValue }

    private var ru: String { self == .month ? "Месяц" : "Год" }

    var title: String { t(ru) }

    var days: Int { self == .month ? 30 : 365 }

    static func of(_ id: String?) -> BillingPeriod { allCases.first { $0.rawValue == id } ?? .month }
}

/// Сутки в миллисекундах — как DAY_MS на Android.
let dayMs: Int64 = 24 * 60 * 60 * 1000
/// Сутки в секундах — для Date.
let daySeconds: TimeInterval = 24 * 60 * 60

/// Подписка аккаунта. until — до какого момента действует; trial — идёт
/// пробный период; autoRenew — продлится сама; trialUsed — пробный период
/// уже брали (он один на аккаунт).
struct MemorySubscription: Equatable, Codable {
    var tier: PlanTier = .free
    var period: BillingPeriod = .month
    var until: Date = Date(timeIntervalSince1970: 0)
    var trial = false
    var autoRenew = false
    var trialUsed = false

    init(
        tier: PlanTier = .free, period: BillingPeriod = .month, until: Date = Date(timeIntervalSince1970: 0),
        trial: Bool = false, autoRenew: Bool = false, trialUsed: Bool = false
    ) {
        self.tier = tier
        self.period = period
        self.until = until
        self.trial = trial
        self.autoRenew = autoRenew
        self.trialUsed = trialUsed
    }

    func active(_ now: Date) -> Bool { tier != .free && now < until }

    /// Какой тариф действует сейчас: закончившийся — уже бесплатный.
    func tierAt(_ now: Date) -> PlanTier { active(now) ? tier : .free }

    /// Сколько полных и неполных дней осталось: 2.1 дня — «3 дня».
    func daysLeft(_ now: Date) -> Int {
        if !active(now) { return 0 }
        let ms = Int64((until.timeIntervalSince(now) * 1000).rounded())
        return Int((ms + dayMs - 1) / dayMs)
    }

    // Тариф и срок хранятся кодами: незнакомый код — бесплатный тариф, а не
    // нечитаемый файл. Все поля необязательные.
    private enum CodingKeys: String, CodingKey {
        case tier, period, until, trial, autoRenew, trialUsed
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        tier = PlanTier.of(try c.decodeIfPresent(String.self, forKey: .tier))
        period = BillingPeriod.of(try c.decodeIfPresent(String.self, forKey: .period))
        until = try c.decodeIfPresent(Date.self, forKey: .until) ?? Date(timeIntervalSince1970: 0)
        trial = try c.decodeIfPresent(Bool.self, forKey: .trial) ?? false
        autoRenew = try c.decodeIfPresent(Bool.self, forKey: .autoRenew) ?? false
        trialUsed = try c.decodeIfPresent(Bool.self, forKey: .trialUsed) ?? false
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(tier.rawValue, forKey: .tier)
        try c.encode(period.rawValue, forKey: .period)
        try c.encode(until, forKey: .until)
        try c.encode(trial, forKey: .trial)
        try c.encode(autoRenew, forKey: .autoRenew)
        try c.encode(trialUsed, forKey: .trialUsed)
    }
}

/// Что даёт премиум: для шторки «Это в Memory+» и значков PLUS.
enum PremiumFeature: String, CaseIterable, Identifiable {
    case stickerCutout, stickerLive, stickerFrames, stickerPacks
    case backdrops, chatBackdrop, sounds
    case prismLong, prismFilters, prismAdjust
    case playlists, pins, recs, recapHistory, recapHiRes
    case shopUnlimited, groupBackdrop, groupContacts

    var id: String { rawValue }

    var tier: PlanTier {
        switch self {
        case .shopUnlimited, .groupBackdrop, .groupContacts: return .business
        default: return .plus
        }
    }

    private var ru: String {
        switch self {
        case .stickerCutout: return "Вырезка объекта"
        case .stickerLive: return "Живые стикеры"
        case .stickerFrames: return "Покадровая анимация"
        case .stickerPacks: return "Коллекции стикеров"
        case .backdrops: return "Все фоны переписок"
        case .chatBackdrop: return "Фон для отдельного чата"
        case .sounds: return "Все звуки Memory"
        case .prismLong: return "Ролики до 60 секунд"
        case .prismFilters: return "Все фильтры PRISM"
        case .prismAdjust: return "Цветокоррекция"
        case .playlists: return "Плейлисты без ограничений"
        case .pins: return "Шесть записей на витрине"
        case .recs: return "Больше советов по вкусу"
        case .recapHistory: return "Итоги за любой период"
        case .recapHiRes: return "Итоги в высоком разрешении"
        case .shopUnlimited: return "Витрина без ограничений"
        case .groupBackdrop: return "Фон страницы группы"
        case .groupContacts: return "Контакты группы"
        }
    }

    private var ruWhat: String {
        switch self {
        case .stickerCutout: return "Объект с фото вырезается сам — человек, питомец, предмет."
        case .stickerLive: return "Эффекты — контур, комикс, объём, блеск — и движение: прыжок, пульс, парение."
        case .stickerFrames: return "Рисунок из нескольких кадров — до 12, со скоростью 4–12 к/с."
        case .stickerPacks: return "Бесплатно — две коллекции, в Memory+ — сколько угодно."
        case .backdrops: return "Все 10 фонов коллекции и своё фото."
        case .chatBackdrop: return "У каждой переписки — свой фон."
        case .sounds: return "57 звуков уведомлений в девяти стилях: маримба, ханг, лоу-фай, синт, 8-бит, природа…"
        case .prismLong: return "Бесплатно — 15 и 30 секунд."
        case .prismFilters: return "12 фильтров вместо четырёх."
        case .prismAdjust: return "Яркость, контраст, насыщенность и тепло ролика."
        case .playlists: return "Бесплатно — три плейлиста."
        case .pins: return "Бесплатно на витрине профиля — три записи."
        case .recs: return "До 12 советов «Для вас» вместо трёх."
        case .recapHistory: return "Бесплатно — текущий и прошлый месяц или год."
        case .recapHiRes: return "Картинка 2160 × 2700 вместо 1080 × 1350."
        case .shopUnlimited: return "Бесплатно — до 10 товаров в витрине группы."
        case .groupBackdrop: return "Цвета, градиенты или своё фото — группа в стиле вашего магазина."
        case .groupContacts: return "Телефон, почта, адрес и часы работы в карточке «О группе»."
        }
    }

    var title: String { t(ru) }
    var what: String { t(ruWhat) }
}

/// Лимиты бесплатного тарифа, цены и состав тарифов.
enum Premium {
    /// Memory+ включён. Пока выключен — всё бесплатно: премиум-функции открыты
    /// всем, экрана тарифов, карточки в профиле и шторки нет, темы о Memory+
    /// в гиде скрыты. Включить — true (и проверить оплату через StoreKit).
    /// Тот же флаг — Premium.enabled на Android.
    static var enabled = false

    /// Действующий тариф, пока Memory+ выключен: открыто всё.
    static var openTier: PlanTier { .business }

    static let trialDays = 3

    static let freePins = 3
    static let plusPins = 6
    static let freePlaylists = 3
    static let freePacks = 2
    static let freeRecs = 3
    static let plusRecs = 12
    static let freeProducts = 10

    /// Итоги: бесплатно — текущий и прошлый период (шаг назад 0 и 1).
    static let freeRecapBack = 1

    /// Множитель размера картинки итогов в Memory+.
    static let hiRes = 2

    static let freeBackdrops: Set<BackdropStyle> = [.cosmos, .nebula, .aurora, .plain]
    static let freeFilters: Set<VideoFilter> = [.none, .crystal, .lilac, .night, .noir]
    static let freeLengths: Set<Int> = [15, 30]

    /// Цена в рублях.
    static func price(_ tier: PlanTier, _ period: BillingPeriod) -> Int {
        switch tier {
        case .free: return 0
        case .plus: return period == .month ? 199 : 1490
        case .business: return period == .month ? 490 : 3990
        }
    }

    /// На сколько процентов год выгоднее двенадцати месяцев.
    static func yearSaving(_ tier: PlanTier) -> Int {
        let month = price(tier, .month) * 12
        if month == 0 { return 0 }
        return Int((100.0 * Double(month - price(tier, .year)) / Double(month)).rounded())
    }

    /// «199 ₽/мес», «1 490 ₽/год».
    static func priceTitle(_ tier: PlanTier, _ period: BillingPeriod) -> String {
        formatRubles(Int64(price(tier, period))) + (period == .month ? t("/мес") : t("/год"))
    }

    /// Что входит в тариф — по пунктам, для экрана тарифов.
    static func features(_ tier: PlanTier) -> [PremiumFeature] {
        switch tier {
        case .free: return []
        case .plus: return PremiumFeature.allCases.filter { $0.tier == .plus }
        case .business: return PremiumFeature.allCases.filter { $0.tier == .business }
        }
    }

    /// Код товара в магазине приложений.
    static func productId(_ tier: PlanTier, _ period: BillingPeriod) -> String { "memory_\(tier.id)_\(period.id)" }
}

/// Внутри Premium одноимённая функция заслоняет общую — зовём через эту.
func formatRubles(_ price: Int64) -> String { priceTitle(price) }
