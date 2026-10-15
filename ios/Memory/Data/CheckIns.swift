import Foundation

/// Чек-ин — запись «чем я сейчас занят»: смотрю фильм, читаю книгу,
/// пробежал 5 км, я в кофейне. activity — номер занятия: встроенного
/// («movie», «run»…) или своего («custom-…»). Название, значок и единица
/// сохраняются в самой записи — она не сломается, если свой шаблон потом
/// удалят. Из чек-инов складываются Итоги. То же — в CheckIns.kt.
struct CheckIn: Equatable, Codable {
    var activity: String
    var emoji: String
    /// Название: у встроенных — русская строка словаря, у своих — как ввёл человек.
    var title: String
    /// Что именно: фильм, книга, место.
    var subject: String = ""
    /// Сколько: километры, страницы, минуты; 0 — не указано.
    var amount: Double = 0
    /// Единица: «км», «стр.», «мин» или своя.
    var unit: String = ""
    /// Оценка 1–5 (фильм, книга, игра, место); 0 — без оценки.
    var rating: Int = 0
    /// Книга: «want» — хочу прочитать, «reading» — читаю, «done» — прочитал; "" — не указано.
    var status: String = ""
    /// Сколько длилось, минуты (пробежка, заезд): из этого — темп; 0 — не указано.
    var minutes: Int = 0

    var builtIn: CheckInActivity? { checkInActivities.first { $0.id == activity } }

    /// Название на выбранном языке: встроенные переводятся, свои — как есть.
    var displayTitle: String { builtIn != nil ? t(title) : title }

    var displayUnit: String { builtIn != nil && !unit.isEmpty ? t(unit) : unit }

    /// «7 км», «120 стр.»; "" — без количества.
    var amountTitle: String {
        amount <= 0 ? "" : "\(amountNumber(amount)) \(displayUnit)".trimmingCharacters(in: .whitespaces)
    }

    /// «5:30 /км» для бега и ходьбы, «18 км/ч» для велосипеда; "" — не посчитать.
    var paceTitle: String { checkInPace(activity, amount, minutes, unit) }

    /// «Прочитал», «Читаю», «Хочу прочитать»; "" — без статуса.
    var statusTitle: String { BookStatus.of(status)?.title ?? "" }

    /// Ключ для полки: одна и та же книга или фильм, как бы ни написали регистр.
    var subjectKey: String { checkInSubjectKey(subject) }
}

/// Новые поля — необязательные в файле: старые сохранения читаются без них.
extension CheckIn {
    private enum CodingKeys: String, CodingKey {
        case activity, emoji, title, subject, amount, unit, rating, status, minutes
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        activity = try c.decode(String.self, forKey: .activity)
        emoji = try c.decode(String.self, forKey: .emoji)
        title = try c.decode(String.self, forKey: .title)
        subject = try c.decodeIfPresent(String.self, forKey: .subject) ?? ""
        amount = try c.decodeIfPresent(Double.self, forKey: .amount) ?? 0
        unit = try c.decodeIfPresent(String.self, forKey: .unit) ?? ""
        rating = try c.decodeIfPresent(Int.self, forKey: .rating) ?? 0
        status = try c.decodeIfPresent(String.self, forKey: .status) ?? ""
        minutes = try c.decodeIfPresent(Int.self, forKey: .minutes) ?? 0
    }
}

// Внутри CheckIn одноимённые свойства заслоняют функции — зовём через эти.
private func checkInPace(_ activity: String, _ km: Double, _ minutes: Int, _ unit: String) -> String {
    paceTitle(activity, km, minutes, unit: unit)
}

private func checkInSubjectKey(_ subject: String) -> String { subjectKey(subject) }

/// «Дюна » и «дюна» — одно и то же.
func subjectKey(_ subject: String) -> String {
    let lower = subject.trimmingCharacters(in: .whitespacesAndNewlines).lowercased().replacingOccurrences(of: "ё", with: "е")
    return lower.split(whereSeparator: { $0.isWhitespace }).joined(separator: " ")
}

/// Статус книги в чек-ине.
enum BookStatus: String, CaseIterable, Identifiable {
    case want = "Хочу прочитать"
    case reading = "Читаю"
    case done = "Прочитал"

    var id: String {
        switch self {
        case .want: return "want"
        case .reading: return "reading"
        case .done: return "done"
        }
    }

    var title: String { t(rawValue) }

    static func of(_ id: String) -> BookStatus? { allCases.first { $0.id == id } }
}

/// Активности, где время превращается в темп или скорость.
let timedActivities: Set<String> = ["run", "walk", "bike", "swim"]

/// Что оценивают звёздами.
let ratedActivities: Set<String> = ["movie", "series", "book", "game", "concert", "coffee", "food"]

/// Темп: минуты на километр (бег, ходьба, плавание) или скорость (велосипед).
func paceTitle(_ activity: String, _ km: Double, _ minutes: Int, unit: String = "км") -> String {
    if km <= 0 || minutes <= 0 || !isKilometers(unit) { return "" }
    if activity == "bike" {
        return "\(amountNumber((km / (Double(minutes) / 60) * 10).rounded() / 10)) \(t("км/ч"))"
    }
    let secPerKm = Int((Double(minutes) * 60 / km).rounded())
    return String(format: "%d:%02d /%@", secPerKm / 60, secPerKm % 60, t("км"))
}

/// Секунды на километр; nil — без времени или расстояния.
func paceSeconds(_ km: Double, _ minutes: Int) -> Int? {
    km > 0 && minutes > 0 ? Int((Double(minutes) * 60 / km).rounded()) : nil
}

/// «7», «7,5» — без лишних нулей.
func amountNumber(_ value: Double) -> String {
    if value.truncatingRemainder(dividingBy: 1) == 0 { return String(Int64(value)) }
    var text = String(format: "%.1f", locale: Locale(identifier: "en_US_POSIX"), value)
    while text.hasSuffix("0") { text.removeLast() }
    if text.hasSuffix(".") { text.removeLast() }
    return L10n.shared.english ? text : text.replacingOccurrences(of: ".", with: ",")
}

/// Что считается в Итогах.
enum RecapMetric { case films, series, books, distance, workouts, places, music, games, food, other }

/// Встроенное занятие. title, hint и unit — русские строки словаря:
/// переводятся при показе.
struct CheckInActivity: Identifiable, Equatable {
    let id: String
    let emoji: String
    let title: String
    /// Подсказка в поле «что именно».
    let hint: String
    /// Единица количества; "" — количество не спрашиваем.
    var unit: String = ""
    var metric: RecapMetric = .other

    /// Оценка — только там, где оценивают; статус — только у книги; время — у бега, ходьбы, велосипеда и плавания.
    func checkIn(_ subject: String, _ amount: Double, rating: Int = 0, status: String = "", minutes: Int = 0) -> CheckIn {
        CheckIn(activity: id, emoji: emoji, title: title, subject: subject.trimmingCharacters(in: .whitespaces),
                amount: unit.isEmpty ? 0 : amount, unit: unit,
                rating: ratedActivities.contains(id) ? min(5, max(0, rating)) : 0,
                status: id == "book" ? status : "",
                minutes: timedActivities.contains(id) ? min(24 * 60, max(0, minutes)) : 0)
    }
}

/// Готовые сценарии над полем записи: вместо «чистого листа» — одно нажатие.
let quickScenarios = ["movie", "run", "book", "place"]

/// Сценарий быстрого чек-ина: встроенное занятие по номеру.
func activity(_ id: String) -> CheckInActivity? { checkInActivities.first { $0.id == id } }

/// Встроенные чек-ины — в том порядке, как в выборе.
let checkInActivities: [CheckInActivity] = [
    CheckInActivity(id: "movie", emoji: "🎬", title: "Смотрю фильм", hint: "Какой фильм?", metric: .films),
    CheckInActivity(id: "series", emoji: "📺", title: "Смотрю сериал", hint: "Какой сериал?", metric: .series),
    CheckInActivity(id: "book", emoji: "📖", title: "Читаю книгу", hint: "Какая книга?", unit: "стр.", metric: .books),
    CheckInActivity(id: "run", emoji: "🏃", title: "Бегаю", hint: "Где бегаете?", unit: "км", metric: .distance),
    CheckInActivity(id: "bike", emoji: "🚴", title: "Катаюсь на велосипеде", hint: "Куда едете?", unit: "км", metric: .distance),
    CheckInActivity(id: "walk", emoji: "🚶", title: "Гуляю", hint: "Где гуляете?", unit: "км", metric: .distance),
    CheckInActivity(id: "swim", emoji: "🏊", title: "Плаваю", hint: "Где плаваете?", unit: "км", metric: .distance),
    CheckInActivity(id: "gym", emoji: "🏋️", title: "Тренируюсь", hint: "Что за тренировка?", unit: "мин", metric: .workouts),
    CheckInActivity(id: "yoga", emoji: "🧘", title: "Занимаюсь йогой", hint: "Какая практика?", unit: "мин", metric: .workouts),
    CheckInActivity(id: "music", emoji: "🎧", title: "Слушаю музыку", hint: "Что слушаете?", metric: .music),
    CheckInActivity(id: "concert", emoji: "🎤", title: "На концерте", hint: "Чей концерт?", metric: .places),
    CheckInActivity(id: "game", emoji: "🎮", title: "Играю", hint: "Во что играете?", metric: .games),
    CheckInActivity(id: "coffee", emoji: "☕", title: "Пью кофе", hint: "Где?", metric: .places),
    CheckInActivity(id: "food", emoji: "🍽️", title: "Обедаю", hint: "Где?", metric: .food),
    CheckInActivity(id: "cook", emoji: "🍳", title: "Готовлю", hint: "Что готовите?", metric: .food),
    CheckInActivity(id: "travel", emoji: "✈️", title: "Путешествую", hint: "Куда?", metric: .places),
    CheckInActivity(id: "place", emoji: "📍", title: "Я здесь", hint: "Где вы?", metric: .places),
    CheckInActivity(id: "study", emoji: "📚", title: "Учусь", hint: "Что изучаете?", unit: "мин"),
    CheckInActivity(id: "work", emoji: "💻", title: "Работаю", hint: "Над чем?"),
    CheckInActivity(id: "mood", emoji: "✨", title: "Настроение", hint: "Какое настроение?"),
]

/// Свой чек-ин: название, значок (эмодзи), единица и подсказка.
struct CheckInTemplate: Identifiable, Equatable, Codable {
    let id: String
    var emoji: String
    var title: String
    var unit: String = ""
    var hint: String = ""

    func checkIn(_ subject: String, _ amount: Double) -> CheckIn {
        let u = unit.trimmingCharacters(in: .whitespaces)
        return CheckIn(activity: id, emoji: emoji, title: title, subject: subject.trimmingCharacters(in: .whitespaces),
                       amount: u.isEmpty ? 0 : amount, unit: u)
    }
}

/// Эмодзи для своих чек-инов — на выбор.
let checkInEmoji = [
    "⭐", "🔥", "💪", "🎯", "🌿", "🧗", "⛷️", "🏂", "⚽", "🏀", "🎾", "🥾",
    "🎨", "📷", "🎸", "🎹", "✍️", "🧩", "🍷", "🍰", "🌙", "☀️", "🐾", "❤️",
]

/// Километры: «км», «km» в своей единице тоже считаются в Итогах.
func isKilometers(_ unit: String) -> Bool {
    var u = unit.trimmingCharacters(in: .whitespaces).lowercased()
    while u.hasSuffix(".") { u.removeLast() }
    return u == "км" || u == "km"
}
