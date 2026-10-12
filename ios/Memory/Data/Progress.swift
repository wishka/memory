import Foundation

/// Прогрессия: вчерашние действия меняют завтрашние возможности. Не значки
/// ради значков, а права: нашли три клада — можете прятать свои, провели
/// пять занятий — вас первым видят в подборе. Та же логика — в Progress.kt.
enum Unlock: String, CaseIterable, Identifiable {
    case hideCache = "HideCache"
    case ownMission = "OwnMission"
    case ownGoal = "OwnGoal"
    case mentor = "Mentor"
    case keeper = "Keeper"

    var id: String { rawValue }

    var emoji: String {
        switch self {
        case .hideCache: return "💎"
        case .ownMission: return "🗺️"
        case .ownGoal: return "🎯"
        case .mentor: return "🎓"
        case .keeper: return "📚"
        }
    }

    private var ru: String {
        switch self {
        case .hideCache: return "Прятать клады"
        case .ownMission: return "Своя миссия"
        case .ownGoal: return "Своя общая цель"
        case .mentor: return "Наставник"
        case .keeper: return "Хранитель книг"
        }
    }

    private var howRu: String {
        switch self {
        case .hideCache: return "Найдите %s клада"
        case .ownMission: return "Отметьте %s находок в миссиях"
        case .ownGoal: return "Внесите вклад в %s общие цели"
        case .mentor: return "Проведите %s занятий в банке времени"
        case .keeper: return "Передайте дальше %s книги"
        }
    }

    private var gives: String {
        switch self {
        case .hideCache: return "Свои клады на карте района"
        case .ownMission: return "Задание для своего района"
        case .ownGoal: return "Предложить сообществу свою цель"
        case .mentor: return "Вас первым видят в подборе навыков"
        case .keeper: return "Ваши книги — первыми в обмене"
        }
    }

    var need: Int {
        switch self {
        case .hideCache: return 3
        case .ownMission: return 5
        case .ownGoal: return 2
        case .mentor: return 5
        case .keeper: return 3
        }
    }

    var title: String { t(ru) }
    var how: String { tf(howRu, need) }
    var reward: String { t(gives) }
}

/// Где вы на пути к праву: сделано из нужного.
struct UnlockProgress: Equatable, Identifiable {
    let unlock: Unlock
    let done: Int

    var id: String { unlock.rawValue }
    var open: Bool { done >= unlock.need }
    var left: Int { max(0, unlock.need - done) }
    var fraction: Double { min(1, max(0, Double(done) / Double(unlock.need))) }
}

func unlockProgress(_ together: TogetherState, _ meId: Int) -> [UnlockProgress] {
    Unlock.allCases.map { u -> UnlockProgress in
        let done: Int
        switch u {
        case .hideCache: done = together.caches.filter(\.foundByMe).count
        case .ownMission: done = together.missions.reduce(0) { $0 + $1.mineCount(meId) }
        case .ownGoal: done = together.goals.filter { $0.mine > 0 }.count
        case .mentor: done = together.deals.filter { $0.status == .done && !$0.give.isEmpty }.count
        case .keeper: done = together.shares.filter { $0.kind == .book && $0.history.contains(meId) }.count
        }
        return UnlockProgress(unlock: u, done: done)
    }
}

extension TogetherState {
    func unlocked(_ u: Unlock, meId: Int) -> Bool {
        unlockProgress(self, meId).first { $0.unlock == u }?.open ?? false
    }
}

/// Километры, которые открывают маршруты: бег, ходьба, велосипед.
func movedKm(_ checkIns: [Dated]) -> Double {
    checkIns.filter { ["run", "walk", "bike"].contains($0.checkIn.activity) && isKilometers($0.checkIn.unit) }
        .reduce(0) { $0 + $1.checkIn.amount }
}

// MARK: - Отклик на чек-ин: действие → значимая награда → предвкушение

/// Строка отклика: значок и текст.
struct EchoLine: Equatable {
    let emoji: String
    let text: String
}

/// Что изменилось от чек-ина: полка, общие цели, план, что откроется дальше, совет.
struct CheckInEcho: Equatable {
    let checkIn: CheckIn
    let lines: [EchoLine]
}

/// Округлить до десятых — как Math.round(x * 10) / 10.0 на Android.
func tenths(_ x: Double) -> Double { (x * 10).rounded() / 10 }

/// Собрать отклик. before/after — полка и «Вместе» до и после чек-ина;
/// friendName — имя по номеру (для совета «Вера ставит 5★»).
func checkInEcho(
    _ c: CheckIn,
    shelfBefore: Shelf,
    shelfAfter: Shelf,
    togetherBefore: TogetherState,
    togetherAfter: TogetherState,
    plan: RunPlan?,
    kmBefore: Double,
    kmAfter: Double,
    recommendation: Recommendation?,
    friendName: (Int) -> String
) -> CheckInEcho {
    var lines: [EchoLine] = []
    // Полка
    switch shelfKind(c.activity) {
    case "film":
        if let item = shelfAfter.films.first(where: { $0.key == shelfKey(c.activity, c.subject) }) {
            let stars = item.rating > 0 ? " · " + String(repeating: "★", count: item.rating) : ""
            lines.append(EchoLine(emoji: "🎬", text: item.times > 1
                ? tf("«%s» — на полке, смотрите уже %s-й раз%s", item.subject, item.times, stars)
                : tf("«%s» — на вашей полке%s", item.subject, stars)))
        }
        lines.append(EchoLine(emoji: "📚", text: tf("Фильмов на полке: %s", shelfAfter.watched.count)))
    case "book":
        if let item = shelfAfter.books.first(where: { $0.key == shelfKey(c.activity, c.subject) }) {
            lines.append(EchoLine(emoji: "📖", text: tf("«%s» — %s", item.subject, BookStatus.of(item.status)?.title.lowercased() ?? "")))
        }
        let done = shelfAfter.booksBy(.done).count
        if done > shelfBefore.booksBy(.done).count {
            lines.append(EchoLine(emoji: "🏁", text: tf("Прочитано книг: %s", done)))
        }
    default:
        break
    }
    if timedActivities.contains(c.activity) && c.amount > 0 {
        let before = shelfBefore.sport.first { $0.activity == c.activity }
        if let after = shelfAfter.sport.first(where: { $0.activity == c.activity }) {
            lines.append(EchoLine(emoji: c.emoji, text: tf("За месяц: %s км", amountNumber(tenths(after.monthKm)))))
            if let before, before.count > 0, after.longestKm > before.longestKm {
                lines.append(EchoLine(emoji: "🏆", text: tf("Новый рекорд дистанции: %s км", amountNumber(after.longestKm))))
            }
            if let pb = before?.bestPace, let pa = after.bestPace, pa < pb {
                lines.append(EchoLine(emoji: "⚡", text: tf("Лучший темп: %s /км", paceClock(pa))))
            }
        }
    }
    // Общие цели
    for g in togetherAfter.goals {
        let was = togetherBefore.goals.first { $0.id == g.id }?.mine ?? 0
        if g.mine > was {
            lines.append(EchoLine(emoji: g.emoji, text: tf("+%s %s в цель «%s»", amountNumber(tenths(g.mine - was)), t(g.unit), g.title)))
        }
    }
    // План недели
    if let plan, c.activity == "run" {
        lines.append(plan.done
            ? EchoLine(emoji: "✅", text: tf("План недели выполнен: %s из %s км", amountNumber(plan.doneKm), amountNumber(plan.targetKm)))
            : EchoLine(emoji: "📈", text: tf("До плана недели — %s км", amountNumber(tenths(plan.leftKm)))))
    }
    // Что открылось и что откроется
    if ["run", "walk", "bike"].contains(c.activity) {
        for m in togetherAfter.missions where m.unlockKm > 0 {
            if kmBefore < m.unlockKm && kmAfter >= m.unlockKm {
                lines.append(EchoLine(emoji: "🔓", text: tf("Открыта миссия «%s»", m.title)))
            } else if kmAfter < m.unlockKm {
                lines.append(EchoLine(emoji: "🔒", text: tf("Ещё %s км — откроется миссия «%s»", amountNumber(tenths(m.unlockKm - kmAfter)), m.title)))
            }
        }
    }
    // Совет из вкуса друзей
    if let r = recommendation {
        lines.append(EchoLine(emoji: r.emoji, text: tf("Похоже на ваше: «%s» — %s ставит %s★", r.subject, friendName(r.personId), r.theirRating)))
    }
    return CheckInEcho(checkIn: c, lines: Array(lines.prefix(6)))
}
