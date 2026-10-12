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

    var builtIn: CheckInActivity? { checkInActivities.first { $0.id == activity } }

    /// Название на выбранном языке: встроенные переводятся, свои — как есть.
    var displayTitle: String { builtIn != nil ? t(title) : title }

    var displayUnit: String { builtIn != nil && !unit.isEmpty ? t(unit) : unit }

    /// «7 км», «120 стр.»; "" — без количества.
    var amountTitle: String {
        amount <= 0 ? "" : "\(amountNumber(amount)) \(displayUnit)".trimmingCharacters(in: .whitespaces)
    }
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

    func checkIn(_ subject: String, _ amount: Double) -> CheckIn {
        CheckIn(activity: id, emoji: emoji, title: title, subject: subject.trimmingCharacters(in: .whitespaces),
                amount: unit.isEmpty ? 0 : amount, unit: unit)
    }
}

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
