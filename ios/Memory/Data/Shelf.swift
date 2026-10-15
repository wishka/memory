import Foundation

/// Полка — профиль, который собирается сам: из чек-инов («посмотрел
/// фильм», «пробежал 7 км») и из отметок без записи в ленте (любимое при
/// знакомстве, «хочу посмотреть» из рекомендаций). Человек ничего не
/// заполняет вручную. Та же логика — в Shelf.kt.

/// Отметка на полке без записи в ленте. activity — «movie» или «book».
/// status — как у книги; у фильма «want» — в планах.
struct ShelfMark: Equatable, Codable {
    var activity: String
    var subject: String
    var rating: Int = 0
    var status: String = ""
    var at: Date = Date(timeIntervalSince1970: 0)

    var key: String { shelfKey(activity, subject) }
}

/// Фильм и сериал — одна полка «Кино».
func shelfKind(_ activity: String) -> String {
    switch activity {
    case "movie", "series": return "film"
    case "book": return "book"
    default: return activity
    }
}

func shelfKey(_ activity: String, _ subject: String) -> String { shelfKind(activity) + ":" + subjectKey(subject) }

/// Чек-ин с моментом, когда его отметили.
struct Dated: Equatable {
    let checkIn: CheckIn
    let at: Date
}

/// Фильм или книга на полке.
struct ShelfItem: Equatable {
    let activity: String
    let subject: String
    let emoji: String
    /// Последняя поставленная оценка; 0 — без оценки.
    let rating: Int
    /// Книга: последний статус; фильм: «want» — пока только в планах.
    let status: String
    /// Сколько раз отмечали (пересмотр, чтение по главам).
    let times: Int
    let lastAt: Date
    /// Книга: прочитано страниц по чек-инам.
    var pages: Double = 0

    var key: String { shelfKey(activity, subject) }
    var planned: Bool { status == BookStatus.want.id }
}

/// Бег, велосипед, ходьба, плавание — суммы и рекорды.
struct SportStats: Equatable {
    let activity: String
    let emoji: String
    let count: Int
    let totalKm: Double
    let monthKm: Double
    let longestKm: Double
    /// Лучший темп, секунд на километр; nil — время ни разу не указали.
    let bestPace: Int?
    let minutes: Int
}

/// Место: кофейня, ресторан, концерт, поездка.
struct PlaceItem: Equatable {
    let subject: String
    let emoji: String
    let count: Int
    let lastAt: Date
    let rating: Int
}

/// Свои и прочие занятия: значок, название, сколько раз.
struct OtherActivity: Equatable {
    let emoji: String
    let title: String
    let count: Int
}

extension Array {
    /// Устойчивая сортировка — как sortedBy / sortedWith на Android:
    /// равные элементы остаются в прежнем порядке.
    func stableSorted(by less: (Element, Element) -> Bool) -> [Element] {
        enumerated()
            .sorted { a, b in
                if less(a.element, b.element) { return true }
                if less(b.element, a.element) { return false }
                return a.offset < b.offset
            }
            .map(\.element)
    }

    /// Группы в порядке первого появления ключа — как groupBy на Android.
    func groupedInOrder<K: Hashable>(by key: (Element) -> K) -> [(key: K, items: [Element])] {
        var order: [K] = []
        var groups: [K: [Element]] = [:]
        for element in self {
            let k = key(element)
            if groups[k] == nil {
                order.append(k)
                groups[k] = []
            }
            groups[k]?.append(element)
        }
        return order.map { (key: $0, items: groups[$0] ?? []) }
    }
}

/// Календарь в заданном часовом поясе: даты считаются так, как их видит человек.
func calendarIn(_ zone: TimeZone) -> Calendar {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = zone
    return calendar
}

struct Shelf: Equatable {
    var films: [ShelfItem] = []
    var books: [ShelfItem] = []
    var sport: [SportStats] = []
    var places: [PlaceItem] = []
    /// Свои и прочие занятия: значок, название, сколько раз.
    var other: [OtherActivity] = []

    var watched: [ShelfItem] { films.filter { !$0.planned } }
    var plannedFilms: [ShelfItem] { films.filter { $0.planned } }
    func booksBy(_ status: BookStatus) -> [ShelfItem] { books.filter { $0.status == status.id } }
    /// Ключи всего, что уже на полке: в рекомендациях это не показываем.
    var keys: Set<String> { Set((films + books).map(\.key)) }
    var isEmpty: Bool { films.isEmpty && books.isEmpty && sport.isEmpty && places.isEmpty && other.isEmpty }

    /// Оценки для подбора вкуса: всё, что оценено.
    var rated: [Rated] {
        (films + books).filter { $0.rating > 0 }.map { Rated(kind: shelfKind($0.activity), subject: $0.subject, rating: $0.rating) }
    }

    private static let placeActivities: Set<String> = ["coffee", "food", "place", "travel", "concert"]

    /// Событие полки: чек-ин или отметка — вперемешку, по времени.
    private struct Event {
        let activity: String
        let subject: String
        let emoji: String
        let rating: Int
        let status: String
        let at: Date
        let pages: Double
        let counts: Bool
        /// Из чек-ина (а не отметки на полке): только они считаются просмотрами.
        var fromCheckIn: Bool = false
    }

    static func build(_ checkIns: [Dated], _ marks: [ShelfMark], now: Date = Date(), zone: TimeZone = .current) -> Shelf {
        let calendar = calendarIn(zone)
        let sorted = checkIns.stableSorted { $0.at < $1.at }
        let blank: (String) -> Bool = { $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        func trim(_ s: String) -> String { s.trimmingCharacters(in: .whitespacesAndNewlines) }

        // ---- Кино и книги: чек-ины и отметки вперемешку, по времени
        let fromCheckIns: [Event] = sorted
            .filter { ["film", "book"].contains(shelfKind($0.checkIn.activity)) && !blank($0.checkIn.subject) }
            .map { d -> Event in
                let c = d.checkIn
                let status: String
                if c.activity != "book" {
                    status = ""
                } else if !c.status.isEmpty {
                    status = c.status
                } else {
                    status = BookStatus.reading.id
                }
                return Event(activity: c.activity, subject: trim(c.subject), emoji: c.emoji, rating: c.rating, status: status, at: d.at,
                             pages: c.activity == "book" && isPages(c.unit) ? c.amount : 0, counts: true, fromCheckIn: true)
            }
        let fromMarks: [Event] = marks.filter { !blank($0.subject) }.map { m -> Event in
            let emoji = shelfKind(m.activity) == "book" ? "📖" : "🎬"
            return Event(activity: m.activity, subject: trim(m.subject), emoji: emoji, rating: m.rating, status: m.status, at: m.at,
                         pages: 0, counts: m.status != BookStatus.want.id)
        }
        let events = (fromCheckIns + fromMarks).stableSorted { $0.at < $1.at }
        let items: [ShelfItem] = events.groupedInOrder { shelfKey($0.activity, $0.subject) }.compactMap { group -> ShelfItem? in
            let list = group.items
            guard let last = list.last, let first = list.first else { return nil }
            let firstCounting = list.first { $0.counts }
            // Посмотрели фильм после «хочу посмотреть» — он больше не в планах
            let watchedFilm = shelfKind(last.activity) == "film" && list.contains { $0.counts && $0.status != BookStatus.want.id }
            let status: String
            if shelfKind(last.activity) == "book" {
                status = list.last { !$0.status.isEmpty }?.status ?? BookStatus.reading.id
            } else if watchedFilm {
                status = ""
            } else {
                status = last.status
            }
            return ShelfItem(
                activity: firstCounting?.activity ?? last.activity,
                subject: first.subject,
                emoji: firstCounting?.emoji ?? last.emoji,
                rating: list.last { $0.rating > 0 }?.rating ?? 0,
                status: status,
                // Просмотры — это чек-ины; отметки на полке (оценка, «Смотрел») — не больше одного раза
                times: max(list.filter { $0.fromCheckIn && $0.counts && $0.status != BookStatus.want.id }.count,
                           list.contains { !$0.fromCheckIn && $0.counts && $0.status != BookStatus.want.id } ? 1 : 0),
                lastAt: last.at,
                pages: list.reduce(0) { $0 + $1.pages }
            )
        }.stableSorted { $0.lastAt > $1.lastAt }

        // ---- Спорт
        let today = calendar.dateComponents([.year, .month], from: now)
        func sameMonth(_ at: Date) -> Bool {
            let d = calendar.dateComponents([.year, .month], from: at)
            return d.year == today.year && d.month == today.month
        }
        let sport: [SportStats] = sorted
            .filter { timedActivities.contains($0.checkIn.activity) || ["gym", "yoga"].contains($0.checkIn.activity) }
            .groupedInOrder { $0.checkIn.activity }
            .compactMap { group -> SportStats? in
                let list = group.items
                guard let last = list.last else { return nil }
                let km = list.filter { isKilometers($0.checkIn.unit) }
                let paces = km.compactMap { paceSeconds($0.checkIn.amount, $0.checkIn.minutes) }
                return SportStats(
                    activity: group.key,
                    emoji: last.checkIn.emoji,
                    count: list.count,
                    totalKm: km.reduce(0) { $0 + $1.checkIn.amount },
                    monthKm: km.filter { sameMonth($0.at) }.reduce(0) { $0 + $1.checkIn.amount },
                    longestKm: km.map(\.checkIn.amount).max() ?? 0,
                    bestPace: group.key == "bike" ? nil : paces.min(),
                    minutes: list.reduce(0) { $0 + ($1.checkIn.unit == "мин" ? Int($1.checkIn.amount) : $1.checkIn.minutes) }
                )
            }
            .stableSorted { $0.count > $1.count }

        // ---- Места
        let places: [PlaceItem] = sorted
            .filter { placeActivities.contains($0.checkIn.activity) && !blank($0.checkIn.subject) }
            .groupedInOrder { subjectKey($0.checkIn.subject) }
            .compactMap { group -> PlaceItem? in
                let list = group.items
                guard let first = list.first, let last = list.last else { return nil }
                return PlaceItem(subject: trim(first.checkIn.subject), emoji: last.checkIn.emoji, count: list.count, lastAt: last.at,
                                 rating: list.last { $0.checkIn.rating > 0 }?.checkIn.rating ?? 0)
            }
            .stableSorted { $0.lastAt > $1.lastAt }

        // ---- Остальное: свои чек-ины и прочие занятия
        let counted: Set<String> = Set(["film", "book"]).union(timedActivities).union(["gym", "yoga"]).union(placeActivities)
        let other: [OtherActivity] = sorted
            .filter { !counted.contains(shelfKind($0.checkIn.activity)) && !counted.contains($0.checkIn.activity) }
            .groupedInOrder { $0.checkIn.activity }
            .compactMap { group -> OtherActivity? in
                guard let last = group.items.last else { return nil }
                return OtherActivity(emoji: last.checkIn.emoji, title: last.checkIn.displayTitle, count: group.items.count)
            }
            .stableSorted { $0.count > $1.count }

        return Shelf(
            films: items.filter { shelfKind($0.activity) == "film" },
            books: items.filter { shelfKind($0.activity) == "book" },
            sport: sport,
            places: places,
            other: other
        )
    }

    private static func isPages(_ unit: String) -> Bool {
        var u = unit.trimmingCharacters(in: .whitespaces).lowercased()
        while u.hasSuffix(".") { u.removeLast() }
        return ["стр", "pages", "p"].contains(u)
    }
}

extension Array where Element == Post {
    /// Свои чек-ины с датами — из своих записей.
    func datedCheckIns(_ meId: Int) -> [Dated] {
        filter { $0.author.id == meId && $0.checkIn != nil }.compactMap { p -> Dated? in
            p.checkIn.map { Dated(checkIn: $0, at: p.createdAt ?? Date(timeIntervalSince1970: 0)) }
        }
    }
}

/// «5:30» из секунд на километр.
func paceClock(_ secPerKm: Int) -> String { String(format: "%d:%02d", secPerKm / 60, secPerKm % 60) }

// MARK: - Воспоминания: «Год назад вы…»

/// Воспоминание: своя запись и сколько с тех пор прошло (дней, примерно).
struct MemoryOfDay: Equatable {
    let post: Post
    let label: String
    let ago: Int
}

/// Записи «в этот день»: год назад (и раньше), месяц назад, неделю назад.
/// Показываем самую давнюю группу — она ценнее: год важнее недели.
func memoriesOfDay(_ posts: [Post], meId: Int, now: Date = Date(), zone: TimeZone = .current) -> [MemoryOfDay] {
    let calendar = calendarIn(zone)
    let today = calendar.dateComponents([.year, .month, .day], from: now)
    let todayStart = calendar.startOfDay(for: now)
    let epoch = Date(timeIntervalSince1970: 0)
    let mine: [(post: Post, at: Date)] = posts.compactMap { p -> (post: Post, at: Date)? in
        guard p.author.id == meId, let at = p.createdAt, at > epoch else { return nil }
        return (post: p, at: at)
    }
    let tY = today.year ?? 0
    let tM = today.month ?? 0
    let tD = today.day ?? 0
    let years: [MemoryOfDay] = mine.compactMap { item -> MemoryOfDay? in
        let d = calendar.dateComponents([.year, .month, .day], from: item.at)
        let n = tY - (d.year ?? 0)
        guard n >= 1, d.month == tM, d.day == tD else { return nil }
        return MemoryOfDay(post: item.post, label: yearsAgo(n), ago: n * 365)
    }
    if !years.isEmpty { return years.stableSorted { $0.ago > $1.ago } }
    let months: [MemoryOfDay] = mine.compactMap { item -> MemoryOfDay? in
        let d = calendar.dateComponents([.year, .month, .day], from: item.at)
        let n = (tY * 12 + tM) - ((d.year ?? 0) * 12 + (d.month ?? 0))
        guard (1...11).contains(n), d.day == tD else { return nil }
        return MemoryOfDay(post: item.post, label: monthsAgo(n), ago: n * 30)
    }
    if !months.isEmpty { return months.stableSorted { $0.ago > $1.ago } }
    return mine.filter { item in
        calendar.dateComponents([.day], from: calendar.startOfDay(for: item.at), to: todayStart).day == 7
    }.map { MemoryOfDay(post: $0.post, label: t("Неделю назад"), ago: 7) }
}

private func yearsAgo(_ n: Int) -> String {
    n == 1 ? t("Год назад") : plural(n, "год", "года", "лет") + " " + t("назад")
}

private func monthsAgo(_ n: Int) -> String {
    n == 1 ? t("Месяц назад") : plural(n, "месяц", "месяца", "месяцев") + " " + t("назад")
}

/// «2026-10-12» — день, как его видит человек: для «Скрыть до завтра».
func dayKey(_ date: Date = Date(), zone: TimeZone = .current) -> String {
    let c = calendarIn(zone).dateComponents([.year, .month, .day], from: date)
    return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
}
