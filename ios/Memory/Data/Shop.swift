import Foundation

/// Витрина группы: каталог товаров с фото, ценой, описанием и наличием.
/// Оплаты внутри приложения нет: покупатель пишет продавцу (товар
/// прикрепляется к сообщению) или идёт на сайт магазина. Те же правила —
/// в Shop.kt.
struct Product: Identifiable, Equatable, Codable {
    var id: Int64
    var title: String
    /// Цена в рублях; 0 — «по запросу».
    var price: Int64 = 0
    var description: String = ""
    var photos: [Attachment] = []
    var category: String = ""
    var inStock: Bool = true
    /// Страница товара на сайте магазина; "" — нет.
    var url: String = ""
    /// Туманность вместо фото, пока его нет.
    var seed: Int = 0

    var priceTitle: String { formatPrice(price) }
}

extension Product {
    private enum CodingKeys: String, CodingKey {
        case id, title, price, description, photos, category, inStock, url, seed
    }

    /// Чтение с запасом: пропущенное поле — значение по умолчанию.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(Int64.self, forKey: .id) ?? 0
        title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        price = try c.decodeIfPresent(Int64.self, forKey: .price) ?? 0
        description = try c.decodeIfPresent(String.self, forKey: .description) ?? ""
        photos = try c.decodeIfPresent([Attachment].self, forKey: .photos) ?? []
        category = try c.decodeIfPresent(String.self, forKey: .category) ?? ""
        inStock = try c.decodeIfPresent(Bool.self, forKey: .inStock) ?? true
        url = try c.decodeIfPresent(String.self, forKey: .url) ?? ""
        seed = try c.decodeIfPresent(Int.self, forKey: .seed) ?? 0
    }
}

/// Внутри Product одноимённое свойство заслоняет функцию — зовём через эту.
private func formatPrice(_ price: Int64) -> String { priceTitle(price) }

/// «1 290 ₽» (неразрывные пробелы); 0 — «Цена по запросу».
func priceTitle(_ price: Int64) -> String {
    if price <= 0 { return t("Цена по запросу") }
    let digits = Array(String(price))
    var out = ""
    for (i, ch) in digits.enumerated() {
        if i > 0 && (digits.count - i) % 3 == 0 { out.append("\u{00A0}") }
        out.append(ch)
    }
    return out + "\u{00A0}₽"
}

/// Цена из того, что ввёл продавец, в рублях: «1 290», «1290 ₽», «1 290 руб.»,
/// «1290,50» и «1290.50» (копейки отбрасываются), «1,290» и «1.290» (разряды).
/// Пусто — 0 (по запросу); не число — nil. Те же правила — parsePrice в Shop.kt.
func parsePrice(_ text: String) -> Int64? {
    var s = text.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    s = s.replacingOccurrences(of: "(руб(лей|ля|ль)?|р)\\.?$", with: "", options: .regularExpression)
    s = s.replacingOccurrences(of: "₽", with: "")
    s = s.replacingOccurrences(of: "[\\s\u{00A0}\u{202F}]", with: "", options: .regularExpression)
    if s.isEmpty { return 0 }
    // Последняя точка или запятая с 1–2 цифрами после — копейки; остальные — разряды
    if let tail = s.range(of: "[.,][0-9]{1,2}$", options: .regularExpression) {
        s = String(s[..<tail.lowerBound])
    }
    s = s.replacingOccurrences(of: ".", with: "").replacingOccurrences(of: ",", with: "")
    if s.isEmpty || !s.allSatisfy({ $0.isASCII && $0.isNumber }) { return nil }
    guard let value = Int64(s), (0...100_000_000).contains(value) else { return nil }
    return value
}

/// Ссылка на сайт: без схемы — https://. Пусто — пусто.
func normalizeUrl(_ text: String) -> String {
    let s = text.trimmingCharacters(in: .whitespacesAndNewlines)
    if s.isEmpty { return "" }
    let lower = s.lowercased()
    return lower.hasPrefix("http://") || lower.hasPrefix("https://") ? s : "https://" + s
}

/// Дополнительно о группе: подробное описание, сайт и контакты. Пустое — не показывается.
struct GroupInfo: Equatable, Codable {
    var about: String = ""
    var website: String = ""
    var phone: String = ""
    var email: String = ""
    var address: String = ""
    var hours: String = ""

    var isEmpty: Bool {
        [about, website, phone, email, address, hours].allSatisfy { $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    }
}

extension GroupInfo {
    private enum CodingKeys: String, CodingKey {
        case about, website, phone, email, address, hours
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        about = try c.decodeIfPresent(String.self, forKey: .about) ?? ""
        website = try c.decodeIfPresent(String.self, forKey: .website) ?? ""
        phone = try c.decodeIfPresent(String.self, forKey: .phone) ?? ""
        email = try c.decodeIfPresent(String.self, forKey: .email) ?? ""
        address = try c.decodeIfPresent(String.self, forKey: .address) ?? ""
        hours = try c.decodeIfPresent(String.self, forKey: .hours) ?? ""
    }
}

/// Цвет группы: кнопки, цены, метки — под стиль магазина.
enum GroupAccent: String, CaseIterable, Identifiable {
    case lilac = ""
    case orchid
    case sky
    case mint
    case gold
    case coral
    case pink

    var id: String { rawValue }

    /// 0xRRGGBB — те же цвета, что на Android.
    var hex: UInt32 {
        switch self {
        case .lilac: return 0xBC92CD
        case .orchid: return 0x844CAD
        case .sky: return 0x8CC4FF
        case .mint: return 0x7BE0B0
        case .gold: return 0xFFD86B
        case .coral: return 0xFF8A65
        case .pink: return 0xFF9EDE
        }
    }

    var rgb: RGB { RGB(hex) }

    private var ru: String {
        switch self {
        case .lilac: return "Сиреневый"
        case .orchid: return "Орхидея"
        case .sky: return "Небо"
        case .mint: return "Мята"
        case .gold: return "Золото"
        case .coral: return "Коралл"
        case .pink: return "Розовый"
        }
    }

    var title: String { t(ru) }

    static func of(_ id: String?) -> GroupAccent { allCases.first { $0.rawValue == id } ?? .lilac }
}

extension Array where Element == Product {
    /// Категории витрины по порядку появления; пустая — «Без категории».
    func categories() -> [String] {
        var seen: Set<String> = []
        var out: [String] = []
        for p in self {
            let c = p.category.trimmingCharacters(in: .whitespacesAndNewlines)
            if !c.isEmpty && !seen.contains(c) {
                seen.insert(c)
                out.append(c)
            }
        }
        return out
    }
}

extension MemoryGroup {
    /// Цвет группы.
    var accentColor: GroupAccent { GroupAccent.of(accent) }

    /// Создатель группы: вы, если группа ваша, иначе — её владелец из демо.
    func ownerIdFor(_ meId: Int) -> Int { mine ? meId : ownerId }
}
