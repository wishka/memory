import Foundation

/// Итоги за месяц или за год.
enum RecapPeriod: String { case month, year }

/// Плитка итогов: «🎬 4 фильма», «🏃 23 км».
struct RecapTile: Equatable, Hashable {
    let emoji: String
    let value: String
    let label: String
}

/// Итоги: личная статистика за месяц или год — фильмы, книги, километры,
/// места, записи и отметки «нравится». Считаются из своих записей и
/// чек-инов на телефоне. Те же правила — в Recap.kt.
struct Recap: Equatable {
    let period: RecapPeriod
    let year: Int
    /// 1…12 для месяца; 0 — итоги года.
    let month: Int
    let tiles: [RecapTile]
    let checkIns: Int
    let posts: Int
    /// Самое частое занятие: «🎬 Смотрю фильм»; nil — чек-инов не было.
    let favorite: String?

    static let months = [
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
    ]

    var title: String {
        period == .year ? tf("%s год", year) : "\(t(Self.months[month - 1])) \(year)"
    }

    var empty: Bool { tiles.isEmpty }

    /// Ключ периода: «2026-09», «2026».
    var key: String { period == .year ? "\(year)" : String(format: "%d-%02d", year, month) }

    /// Две главные цифры — для уведомления: «4 фильма, 23 км».
    var headline: String { tiles.prefix(2).map { "\($0.value) \($0.label)" }.joined(separator: ", ") }

    /// Посчитать итоги. Записи без даты (демо) не считаются.
    static func compute(me: User, posts: [Post], prisms: [Prism], period: RecapPeriod, year: Int, month: Int,
                        calendar: Calendar = .current) -> Recap {
        func inPeriod(_ date: Date?) -> Bool {
            guard let date else { return false }
            let c = calendar.dateComponents([.year, .month], from: date)
            return c.year == year && (period == .year || c.month == month)
        }
        let mine = posts.filter { $0.author.id == me.id && inPeriod($0.createdAt) }
        let checks = mine.compactMap(\.checkIn)
        func of(_ ids: String...) -> [CheckIn] { checks.filter { ids.contains($0.activity) } }
        func distinctCount(_ list: [CheckIn]) -> Int {
            let subjects = list.map { $0.subject.trimmingCharacters(in: .whitespaces).lowercased() }
            return Set(subjects.filter { !$0.isEmpty }).count + subjects.filter(\.isEmpty).count
        }
        func metric(_ m: RecapMetric) -> [CheckIn] { checks.filter { $0.builtIn?.metric == m } }

        var tiles: [RecapTile] = []
        func add(_ emoji: String, _ n: Int, _ one: String, _ few: String, _ many: String) {
            guard n > 0 else { return }
            let word = plural(n, one, few, many)
            let label = word.firstIndex(of: " ").map { String(word[word.index(after: $0)...]) } ?? word
            tiles.append(RecapTile(emoji: emoji, value: "\(n)", label: label))
        }
        add("🎬", of("movie").count, "фильм", "фильма", "фильмов")
        add("📺", distinctCount(of("series")), "сериал", "сериала", "сериалов")
        add("📖", distinctCount(of("book")), "книга", "книги", "книг")
        let km = checks.filter { isKilometers($0.unit) }.reduce(0) { $0 + $1.amount }
        if km > 0 { tiles.append(RecapTile(emoji: "🏃", value: amountNumber(km), label: t("км"))) }
        add("📄", Int(of("book").reduce(0) { $0 + $1.amount }), "страница", "страницы", "страниц")
        add("💪", metric(.workouts).count + metric(.distance).count, "тренировка", "тренировки", "тренировок")
        add("📍", distinctCount(metric(.places)), "место", "места", "мест")
        add("🎮", distinctCount(of("game")), "игра", "игры", "игр")
        // Свои чек-ины: сумма в своей единице или сколько раз
        let custom = checks.filter { $0.builtIn == nil && !isKilometers($0.unit) }
        var order: [String] = []
        for c in custom where !order.contains(c.activity) { order.append(c.activity) }
        for id in order {
            let list = custom.filter { $0.activity == id }
            guard let c = list.last else { continue }
            let sum = list.reduce(0) { $0 + $1.amount }
            if sum > 0 && !c.unit.trimmingCharacters(in: .whitespaces).isEmpty {
                tiles.append(RecapTile(emoji: c.emoji, value: amountNumber(sum), label: "\(c.unit) · \(c.title)"))
            } else {
                tiles.append(RecapTile(emoji: c.emoji, value: "\(list.count)", label: "× \(c.title)"))
            }
        }
        add("✍️", mine.count, "запись", "записи", "записей")
        add("❤️", mine.reduce(0) { $0 + $1.likes }, "отметка «нравится»", "отметки «нравится»", "отметок «нравится»")
        add("🎞️", prisms.filter { $0.author.id == me.id && inPeriod($0.createdAt) }.count, "ролик PRISM", "ролика PRISM", "роликов PRISM")

        // Самое частое занятие; при равенстве — то, что встретилось раньше (как на Android)
        var counts: [String: Int] = [:]
        var seen: [String] = []
        for c in checks {
            if counts[c.activity] == nil { seen.append(c.activity) }
            counts[c.activity, default: 0] += 1
        }
        let favorite = seen.max { (counts[$0] ?? 0) < (counts[$1] ?? 0) }
            .flatMap { top in checks.first { $0.activity == top } }
            .map { "\($0.emoji) \($0.displayTitle)" }
        return Recap(period: period, year: year, month: month, tiles: tiles, checkIns: checks.count, posts: mine.count, favorite: favorite)
    }

    /// Прошлый месяц — о его итогах напоминаем в начале месяца.
    static func previousMonth(_ today: Date = Date(), calendar: Calendar = .current) -> (year: Int, month: Int) {
        let date = calendar.date(byAdding: .month, value: -1, to: today) ?? today
        let c = calendar.dateComponents([.year, .month], from: date)
        return (c.year ?? 2026, c.month ?? 1)
    }
}
