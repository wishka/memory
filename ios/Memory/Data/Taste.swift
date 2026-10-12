import Foundation

/// Вкус и рекомендации. Не «самое популярное», а то, что высоко оценили
/// люди, чьи оценки совпадают с вашими: этого не знает ни один чат-бот —
/// у него нет ни вашей полки, ни ваших друзей. Та же логика — в Taste.kt.

/// Оценка: kind — «film» или «book».
struct Rated: Equatable {
    let kind: String
    let subject: String
    let rating: Int

    var key: String { kind + ":" + subjectKey(subject) }
}

/// Фильм или книга из каталога знакомства.
struct CatalogTitle: Equatable, Identifiable {
    let kind: String
    let title: String
    let emoji: String
    let by: String

    var id: String { kind + ":" + title }
}

enum Catalog {
    static let films: [CatalogTitle] = [
        CatalogTitle(kind: "film", title: "Интерстеллар", emoji: "🪐", by: "Кристофер Нолан"),
        CatalogTitle(kind: "film", title: "Дюна", emoji: "🏜️", by: "Дени Вильнёв"),
        CatalogTitle(kind: "film", title: "Прибытие", emoji: "🛸", by: "Дени Вильнёв"),
        CatalogTitle(kind: "film", title: "Бегущий по лезвию 2049", emoji: "🌆", by: "Дени Вильнёв"),
        CatalogTitle(kind: "film", title: "Солярис", emoji: "🌊", by: "Андрей Тарковский"),
        CatalogTitle(kind: "film", title: "Москва слезам не верит", emoji: "🎞️", by: "Владимир Меньшов"),
        CatalogTitle(kind: "film", title: "Брат", emoji: "🧥", by: "Алексей Балабанов"),
        CatalogTitle(kind: "film", title: "Унесённые призраками", emoji: "🐉", by: "Хаяо Миядзаки"),
        CatalogTitle(kind: "film", title: "Амели", emoji: "🍓", by: "Жан-Пьер Жёне"),
        CatalogTitle(kind: "film", title: "Начало", emoji: "🌀", by: "Кристофер Нолан"),
        CatalogTitle(kind: "film", title: "Ла-Ла Ленд", emoji: "🎹", by: "Дэмьен Шазелл"),
        CatalogTitle(kind: "film", title: "Паразиты", emoji: "🪜", by: "Пон Джун-хо"),
        CatalogTitle(kind: "film", title: "Вечное сияние чистого разума", emoji: "💭", by: "Мишель Гондри"),
        CatalogTitle(kind: "film", title: "Остров собак", emoji: "🐕", by: "Уэс Андерсон"),
        CatalogTitle(kind: "film", title: "Отель «Гранд Будапешт»", emoji: "🏨", by: "Уэс Андерсон"),
        CatalogTitle(kind: "film", title: "Матрица", emoji: "💊", by: "Вачовски"),
        CatalogTitle(kind: "film", title: "Сталкер", emoji: "🌿", by: "Андрей Тарковский"),
        CatalogTitle(kind: "film", title: "Всё везде и сразу", emoji: "🥯", by: "Дэниелы"),
    ]

    static let books: [CatalogTitle] = [
        CatalogTitle(kind: "book", title: "Мастер и Маргарита", emoji: "🐈‍⬛", by: "Михаил Булгаков"),
        CatalogTitle(kind: "book", title: "Пикник на обочине", emoji: "🔮", by: "Стругацкие"),
        CatalogTitle(kind: "book", title: "Задача трёх тел", emoji: "☀️", by: "Лю Цысинь"),
        CatalogTitle(kind: "book", title: "Маленький принц", emoji: "🌹", by: "Антуан де Сент-Экзюпери"),
        CatalogTitle(kind: "book", title: "1984", emoji: "👁️", by: "Джордж Оруэлл"),
        CatalogTitle(kind: "book", title: "Сто лет одиночества", emoji: "🦋", by: "Габриэль Гарсиа Маркес"),
        CatalogTitle(kind: "book", title: "Понедельник начинается в субботу", emoji: "🧪", by: "Стругацкие"),
        CatalogTitle(kind: "book", title: "Цветы для Элджернона", emoji: "🐭", by: "Дэниел Киз"),
        CatalogTitle(kind: "book", title: "Дюна", emoji: "🏜️", by: "Фрэнк Герберт"),
        CatalogTitle(kind: "book", title: "Гарри Поттер и философский камень", emoji: "⚡", by: "Джоан Роулинг"),
        CatalogTitle(kind: "book", title: "Преступление и наказание", emoji: "🪓", by: "Фёдор Достоевский"),
        CatalogTitle(kind: "book", title: "Марсианин", emoji: "🥔", by: "Энди Уир"),
        CatalogTitle(kind: "book", title: "Атлант расправил плечи", emoji: "🗽", by: "Айн Рэнд"),
        CatalogTitle(kind: "book", title: "Норвежский лес", emoji: "🌲", by: "Харуки Мураками"),
        CatalogTitle(kind: "book", title: "Лавр", emoji: "🕯️", by: "Евгений Водолазкин"),
        CatalogTitle(kind: "book", title: "Проект «Аве Мария»", emoji: "🕷️", by: "Энди Уир"),
    ]

    static var all: [CatalogTitle] { films + books }

    static func find(_ kind: String, _ subject: String) -> CatalogTitle? {
        all.first { $0.kind == kind && subjectKey($0.title) == subjectKey(subject) }
    }

    static func emoji(_ kind: String, _ subject: String) -> String {
        find(kind, subject)?.emoji ?? (kind == "book" ? "📖" : "🎬")
    }
}

/// Оценки демо-друзей, пока нет сервера: у каждого свой вкус.
enum DemoTaste {
    private static func f(_ s: String, _ r: Int) -> Rated { Rated(kind: "film", subject: s, rating: r) }
    private static func b(_ s: String, _ r: Int) -> Rated { Rated(kind: "book", subject: s, rating: r) }

    static let ratings: [Int: [Rated]] = [
        // Вера: космос, тишина, Вильнёв и Тарковский
        2: [f("Интерстеллар", 5), f("Дюна", 5), f("Прибытие", 5), f("Солярис", 5), f("Сталкер", 4), f("Бегущий по лезвию 2049", 4), f("Ла-Ла Ленд", 2),
            b("Задача трёх тел", 5), b("Пикник на обочине", 5), b("Маленький принц", 4), b("Проект «Аве Мария»", 5)],
        // Марк: музыка, мрачное и умное
        3: [f("Бегущий по лезвию 2049", 5), f("Матрица", 5), f("Ла-Ла Ленд", 4), f("Брат", 5), f("Паразиты", 4), f("Амели", 2),
            b("1984", 5), b("Понедельник начинается в субботу", 4), b("Атлант расправил плечи", 2)],
        // Алина: свет, цвет, Миядзаки и Андерсон
        4: [f("Унесённые призраками", 5), f("Амели", 5), f("Отель «Гранд Будапешт»", 5), f("Остров собак", 4), f("Вечное сияние чистого разума", 5), f("Брат", 2),
            b("Маленький принц", 5), b("Сто лет одиночества", 5), b("Норвежский лес", 4)],
        // Тимур: приключения и выживание
        5: [f("Интерстеллар", 5), f("Начало", 5), f("Матрица", 4), f("Дюна", 4), f("Всё везде и сразу", 3),
            b("Марсианин", 5), b("Проект «Аве Мария»", 5), b("Дюна", 4), b("Гарри Поттер и философский камень", 4)],
        // София: путешествия, плёнка, большие романы
        6: [f("Солярис", 4), f("Москва слезам не верит", 5), f("Амели", 4), f("Паразиты", 5), f("Всё везде и сразу", 5),
            b("Сто лет одиночества", 5), b("Мастер и Маргарита", 5), b("Лавр", 5), b("Пикник на обочине", 4), b("Цветы для Элджернона", 5)],
    ]
}

/// Насколько вкус совпадает: сколько общих оценок и доля совпадения 0…1.
struct TasteMatch: Equatable {
    let common: Int
    let similarity: Double

    /// «совпадает на 87%»: показываем, только если общих хотя бы две.
    var percent: Int { Int(similarity * 100) }
}

func tasteMatch(_ mine: [Rated], _ theirs: [Rated]) -> TasteMatch {
    var my: [String: Rated] = [:]
    for r in mine { my[r.key] = r }
    let pairs: [(Int, Int)] = theirs.compactMap { r in my[r.key].map { ($0.rating, r.rating) } }
    if pairs.isEmpty { return TasteMatch(common: 0, similarity: 0) }
    let sum = pairs.reduce(0.0) { $0 + (1.0 - Double(abs($1.0 - $1.1)) / 4.0) }
    return TasteMatch(common: pairs.count, similarity: sum / Double(pairs.count))
}

/// Рекомендация: что, кто больше всех «за» и почему.
struct Recommendation: Equatable {
    let kind: String
    let subject: String
    let emoji: String
    let score: Double
    /// Друг, чья оценка весит больше всех.
    let personId: Int
    let theirRating: Int
    let match: TasteMatch
    /// Сколько друзей поставили 4–5.
    let friends: Int

    var key: String { kind + ":" + subjectKey(subject) }
}

/// Подборка: то, что друзья оценили на 4–5, а у вас ещё нет. Вес друга —
/// совпадение вкуса (чем больше общих оценок, тем увереннее); у новичка
/// без оценок — небольшой общий вес, чтобы подборка не была пустой.
func recommend(_ mine: [Rated], exclude: Set<String>, friends: [Int: [Rated]], kind: String, limit: Int = 6) -> [Recommendation] {
    struct Vote {
        let personId: Int
        let rating: Int
        let weight: Double
        let match: TasteMatch
    }
    var order: [String] = []
    var votes: [String: [Vote]] = [:]
    var subjects: [String: String] = [:]
    // Друзья по номеру — как в упорядоченном словаре на Android
    for personId in friends.keys.sorted() {
        let list = friends[personId] ?? []
        let match = tasteMatch(mine, list)
        let confidence = Double(min(match.common, 3)) / 3.0
        let weight = 0.15 + match.similarity * confidence
        // Друг с противоположным вкусом не советует
        if match.common >= 2 && match.similarity < 0.5 { continue }
        for r in list where r.kind == kind && r.rating >= 4 && !exclude.contains(r.key) {
            if votes[r.key] == nil {
                votes[r.key] = []
                order.append(r.key)
            }
            votes[r.key]?.append(Vote(personId: personId, rating: r.rating, weight: weight, match: match))
            if subjects[r.key] == nil { subjects[r.key] = r.subject }
        }
    }
    let all: [Recommendation] = order.compactMap { key -> Recommendation? in
        guard let list = votes[key], var top = list.first, let subject = subjects[key] else { return nil }
        // Больший вес, затем большая оценка, затем меньший номер
        for v in list.dropFirst() {
            if v.weight > top.weight
                || (v.weight == top.weight && v.rating > top.rating)
                || (v.weight == top.weight && v.rating == top.rating && v.personId < top.personId) {
                top = v
            }
        }
        let score = list.reduce(0.0) { $0 + $1.weight * Double($1.rating - 3) }
        return Recommendation(kind: kind, subject: subject, emoji: Catalog.emoji(kind, subject), score: score,
                              personId: top.personId, theirRating: top.rating, match: top.match, friends: list.count)
    }
    let sorted = all.stableSorted { a, b in
        if a.score != b.score { return a.score > b.score }
        return a.subject < b.subject
    }
    return Array(sorted.prefix(limit))
}

// MARK: - План бега на неделю

enum PlanReason: Equatable { case start, grow, comeback }

/// План на неделю из прошлых четырёх: прирост не больше 10% к прошлой
/// неделе — так растут без травм. Пропустили неделю — начинаем мягче.
struct RunPlan: Equatable {
    let targetKm: Double
    let doneKm: Double
    let weeksKm: [Double]
    let reason: PlanReason

    var leftKm: Double { max(0, targetKm - doneKm) }
    var done: Bool { doneKm >= targetKm }
    var progress: Double { targetKm <= 0 ? 0 : min(1, max(0, doneKm / targetKm)) }
}

func runPlan(_ checkIns: [Dated], now: Date = Date(), zone: TimeZone = .current) -> RunPlan {
    let calendar = calendarIn(zone)
    let todayStart = calendar.startOfDay(for: now)
    // Понедельник этой недели (или сегодня, если понедельник): weekday 1 — воскресенье, 2 — понедельник
    let weekday = calendar.component(.weekday, from: todayStart)
    let sinceMonday = (weekday + 5) % 7
    let monday = calendar.date(byAdding: .day, value: -sinceMonday, to: todayStart) ?? todayStart
    let runs = checkIns.filter { $0.checkIn.activity == "run" && isKilometers($0.checkIn.unit) && $0.checkIn.amount > 0 }
    func weekKm(_ weeksBack: Int) -> Double {
        let start = calendar.date(byAdding: .day, value: -7 * weeksBack, to: monday) ?? monday
        let end = calendar.date(byAdding: .day, value: 7, to: start) ?? start
        return runs.filter { r in
            let d = calendar.startOfDay(for: r.at)
            return d >= start && d < end
        }.reduce(0) { $0 + $1.checkIn.amount }
    }
    let weeks = [4, 3, 2, 1].map { weekKm($0) }
    let last = weeks.last ?? 0
    let avg = weeks.reduce(0, +) / Double(weeks.count)
    func half(_ x: Double) -> Double { (x * 2).rounded() / 2 }
    let target: Double
    let reason: PlanReason
    if weeks.allSatisfy({ $0 == 0 }) {
        target = 5
        reason = .start
    } else if last > 0 {
        target = max(half(last * 1.1), last + 0.5)
        reason = .grow
    } else {
        target = max(half(avg * 0.8), 3)
        reason = .comeback
    }
    return RunPlan(targetKm: target, doneKm: weekKm(0), weeksKm: weeks, reason: reason)
}
