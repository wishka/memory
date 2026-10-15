import CoreGraphics
import Foundation

/// Вкладка «Вместе»: не соревнование, а общее дело. Восемь разделов —
/// общие цели, обмен навыками, дневники процесса, городские миссии, клады,
/// вылазки малой группой, рисунок по эстафете и обмен вещами без денег.
///
/// Всё состояние — TogetherState: неизменяемое, правила — его методы
/// (возвращают новое состояние). Те же правила — в Together.kt на Android.
enum TogetherSection: String, CaseIterable, Identifiable {
    case challenges = "Цели"
    case skills = "Навыки"
    case journeys = "Дневники"
    case missions = "Миссии"
    case caches = "Клады"
    case outings = "Вылазки"
    case drawing = "Рисунок"
    case sharing = "Обмен"

    var id: String { rawValue }
    var title: String { t(rawValue) }

    var emoji: String {
        switch self {
        case .challenges: return "🎯"
        case .skills: return "🤝"
        case .journeys: return "📓"
        case .missions: return "🗺️"
        case .caches: return "💎"
        case .outings: return "🥾"
        case .drawing: return "🎨"
        case .sharing: return "♻️"
        }
    }

    /// Номер раздела по порядку — targetId уведомления (как ordinal на Android).
    var index: Int { Self.allCases.firstIndex(of: self) ?? 0 }
}

/// Точка на карте.
struct LatLon: Codable, Equatable {
    var lat: Double
    var lon: Double

    /// Сдвиг на метры: на восток и на север.
    func offset(east: Double, north: Double) -> LatLon {
        LatLon(lat: lat + north / 111_320.0, lon: lon + east / (111_320.0 * cos(lat * Double.pi / 180)))
    }

    /// Расстояние в метрах (гаверсинус).
    func distance(to o: LatLon) -> Double {
        let r = 6_371_000.0
        let dLat = (o.lat - lat) * Double.pi / 180
        let dLon = (o.lon - lon) * Double.pi / 180
        let a = sin(dLat / 2) * sin(dLat / 2)
            + cos(lat * Double.pi / 180) * cos(o.lat * Double.pi / 180) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    /// Центр Москвы — пока телефон не знает, где вы.
    static let moscow = LatLon(lat: 55.7558, lon: 37.6173)
}

/// «350 м», «2,4 км».
func distanceTitle(_ meters: Double) -> String {
    if meters < 1000 { return "\(Int(meters)) \(t("м"))" }
    return "\(amountNumber((meters / 100).rounded() / 10)) \(t("км"))"
}

// MARK: - 1. Общие цели

/// Чем считается вклад: чек-ины засчитываются сами.
enum GoalMetric: String, Codable { case km, books, hours, trees, other }

/// Общая цель сообщества: «Вместе пробежим 1 000 000 км». Не соревнование —
/// все двигают одну шкалу. mine — ваш вклад.
struct CommunityGoal: Identifiable, Equatable, Codable {
    let id: String
    var emoji: String
    var title: String
    var description: String
    var unit: String
    var target: Double
    var total: Double
    var participants: Int
    var metric: GoalMetric
    var joined: Bool = false
    var mine: Double = 0
    /// Кто предложил: 0 — сообщество; свой номер — ваша цель.
    var authorId: Int = 0

    var progress: Double { target <= 0 ? 0 : min(1, max(0, total / target)) }
}

/// Новые поля — необязательные в файле: старые сохранения читаются без них.
extension CommunityGoal {
    private enum CodingKeys: String, CodingKey {
        case id, emoji, title, description, unit, target, total, participants, metric, joined, mine, authorId
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        emoji = try c.decode(String.self, forKey: .emoji)
        title = try c.decode(String.self, forKey: .title)
        description = try c.decode(String.self, forKey: .description)
        unit = try c.decode(String.self, forKey: .unit)
        target = try c.decode(Double.self, forKey: .target)
        total = try c.decode(Double.self, forKey: .total)
        participants = try c.decode(Int.self, forKey: .participants)
        metric = try c.decode(GoalMetric.self, forKey: .metric)
        joined = try c.decodeIfPresent(Bool.self, forKey: .joined) ?? false
        mine = try c.decodeIfPresent(Double.self, forKey: .mine) ?? 0
        authorId = try c.decodeIfPresent(Int.self, forKey: .authorId) ?? 0
    }
}

// MARK: - 2. Банк времени

/// Что человек готов дать и что хочет получить.
struct SkillOffer: Equatable, Codable {
    var personId: Int
    var teaches: [String]
    var wants: [String]
    var about: String = ""
}

enum DealStatus: String, Codable { case proposed, agreed, done, declined }

/// Договорённость. give — чему учите вы ("" — не учите), get — чему учат
/// вас ("" — ничему). Обмен 1:1 баланс не меняет; урок «за часы» — тратит
/// или приносит минуты банка времени.
struct SkillDeal: Identifiable, Equatable, Codable {
    let id: Int64
    var personId: Int
    var give: String
    var get: String
    var minutes: Int
    var status: DealStatus = .proposed
    var createdAt: Date? = nil

    /// Как занятие меняет баланс, когда прошло.
    var balanceChange: Int {
        let plus = give.isEmpty ? 0 : minutes
        let minus = self.get.isEmpty ? 0 : minutes
        return plus - minus
    }
}

/// Насколько подходит предложение: 2 — взаимный обмен, 1 — в одну сторону, 0 — нет.
func matchScore(_ offer: SkillOffer, _ myTeach: [String], _ myWant: [String]) -> Int {
    func has(_ list: [String], _ x: String) -> Bool { list.contains { $0.lowercased() == x.lowercased() } }
    let theyTeachMe = offer.teaches.contains { has(myWant, $0) }
    let iTeachThem = offer.wants.contains { has(myTeach, $0) }
    if theyTeachMe && iTeachThem { return 2 }
    if theyTeachMe || iTeachThem { return 1 }
    return 0
}

/// «1 ч 30 мин», «45 мин», «0 мин».
func minutesTitle(_ minutes: Int) -> String {
    let m = abs(minutes)
    let sign = minutes < 0 ? "−" : ""
    if m >= 60 && m % 60 != 0 { return "\(sign)\(m / 60) \(t("ч")) \(m % 60) \(t("мин"))" }
    if m >= 60 { return "\(sign)\(m / 60) \(t("ч"))" }
    return "\(sign)\(m) \(t("мин"))"
}

// MARK: - 3. Дневники процесса

/// Совет к записи дневника: вместо «нравится» — помощь.
struct Tip: Identifiable, Equatable, Codable {
    let id: Int64
    var authorId: Int
    var text: String
    var helpful: Int = 0
    var markedByMe: Bool = false
    var createdAt: Date? = nil
}

/// День процесса: «День 3: учусь жонглировать».
struct JourneyEntry: Identifiable, Equatable, Codable {
    let id: Int64
    var day: Int
    var text: String
    var media: [Attachment] = []
    var createdAt: Date? = nil
    var tips: [Tip] = []
}

/// Короткий челлендж: человек выкладывает не результат, а процесс.
struct Journey: Identifiable, Equatable, Codable {
    let id: Int64
    var authorId: Int
    var emoji: String
    var title: String
    var days: Int
    var entries: [JourneyEntry] = []
    var createdAt: Date? = nil

    var nextDay: Int { (entries.map(\.day).max() ?? 0) + 1 }
}

// MARK: - 4. Городские миссии

/// Находка миссии: фото и точка на карте.
struct MissionFind: Identifiable, Equatable, Codable {
    let id: Int64
    var authorId: Int
    var note: String
    var at: LatLon
    var photo: Attachment? = nil
    var createdAt: Date? = nil
}

/// Задание по городу: «Сфотографируй 5 мозаик на стенах домов».
struct Mission: Identifiable, Equatable, Codable {
    let id: String
    var emoji: String
    var title: String
    var description: String
    var target: Int
    var joined: Bool = false
    var finds: [MissionFind] = []
    /// Откроется после стольких км бега, ходьбы и велосипеда; 0 — открыта сразу.
    var unlockKm: Double = 0
    /// Кто придумал: 0 — сообщество; свой номер — ваша миссия района.
    var authorId: Int = 0

    func mineCount(_ meId: Int) -> Int { finds.filter { $0.authorId == meId }.count }
    func locked(_ movedKm: Double) -> Bool { unlockKm > 0 && movedKm < unlockKm }
}

/// Новые поля — необязательные в файле: старые сохранения читаются без них.
extension Mission {
    private enum CodingKeys: String, CodingKey {
        case id, emoji, title, description, target, joined, finds, unlockKm, authorId
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        emoji = try c.decode(String.self, forKey: .emoji)
        title = try c.decode(String.self, forKey: .title)
        description = try c.decode(String.self, forKey: .description)
        target = try c.decode(Int.self, forKey: .target)
        joined = try c.decodeIfPresent(Bool.self, forKey: .joined) ?? false
        finds = try c.decodeIfPresent([MissionFind].self, forKey: .finds) ?? []
        unlockKm = try c.decodeIfPresent(Double.self, forKey: .unlockKm) ?? 0
        authorId = try c.decodeIfPresent(Int.self, forKey: .authorId) ?? 0
    }
}

// MARK: - 5. Клады

struct CacheLog: Identifiable, Equatable, Codable {
    let id: Int64
    var authorId: Int
    var text: String
    var createdAt: Date? = nil
}

/// Клад: маленький контейнер, спрятанный в городе, и подсказка к нему.
/// (На Android — Cache; здесь имя длиннее, чтобы не путать с Layout.Cache.)
struct GeoCache: Identifiable, Equatable, Codable {
    let id: Int64
    var authorId: Int
    var title: String
    var hint: String
    var at: LatLon
    var difficulty: Int
    var size: String
    var createdAt: Date? = nil
    var logs: [CacheLog] = []
    var foundByMe: Bool = false
}

/// Отметиться можно, только если вы рядом: GPS ошибается на десятки метров.
let cacheRadiusM = 150.0

// MARK: - 6. Вылазки

/// Вылазка малой группой: до 10 человек.
struct Outing: Identifiable, Equatable, Codable {
    let id: Int64
    var organizerId: Int
    var emoji: String
    var title: String
    var description: String
    var place: String
    var startsAt: Date
    var capacity: Int
    var participants: [Int]
    var at: LatLon? = nil

    var full: Bool { participants.count >= capacity }

    static let maxPeople = 10
}

// MARK: - 7. Рисунок по эстафете

struct DrawingTurn: Equatable, Codable {
    var authorId: Int
    var strokes: [DrawStroke]
}

/// Совместный рисунок: каждый добавляет своё и передаёт следующему.
/// waitingFor — чей ход (номер человека); nil — рисунок готов.
struct CollabDrawing: Identifiable, Equatable, Codable {
    let id: Int64
    var title: String
    var maxTurns: Int
    var turns: [DrawingTurn] = []
    var waitingFor: Int? = nil
    var createdAt: Date? = nil

    var finished: Bool { turns.count >= maxTurns }
    var strokes: [DrawStroke] { turns.flatMap(\.strokes) }
}

// MARK: - 8. Обмен без денег

enum ShareKind: String, Codable, CaseIterable, Identifiable {
    case book = "Книги"
    case seeds = "Семена и растения"
    case repair = "Ремонт"

    var id: String { rawValue }
    var title: String { t(rawValue) }

    var emoji: String {
        switch self {
        case .book: return "📚"
        case .seeds: return "🌱"
        case .repair: return "🛠️"
        }
    }
}

enum ShareStatus: String, Codable { case available, requested, given, fixed }

/// Вещь в обмене. Книга ходит по кругу: holderId — у кого она сейчас,
/// history — у кого побывала. Семена отдают насовсем. Вещь «Ремонт» ждёт
/// тех, кто поможет починить (helpers).
struct ShareItem: Identifiable, Equatable, Codable {
    let id: Int64
    var kind: ShareKind
    var title: String
    var description: String
    var ownerId: Int
    var holderId: Int
    var status: ShareStatus = .available
    var requestedBy: Int? = nil
    var history: [Int] = []
    var helpers: [Int] = []
    var photo: Attachment? = nil
    var createdAt: Date? = nil
}

/// Пусто ли после обрезки пробелов — как isBlank на Android.
private func blank(_ s: String) -> Bool { s.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

/// Обрезать пробелы и длину — как trim().take(n) на Android.
private func clip(_ s: String, _ n: Int) -> String { String(s.trimmingCharacters(in: .whitespacesAndNewlines).prefix(n)) }

/// Всё во вкладке «Вместе». anchor — где вы (последнее известное место):
/// демо-клады, находки и вылазки стоят вокруг него.
struct TogetherState: Equatable, Codable {
    var goals: [CommunityGoal] = []
    var offers: [SkillOffer] = []
    var deals: [SkillDeal] = []
    var myTeach: [String] = []
    var myWant: [String] = []
    /// Банк времени, минуты. Новичку — час в подарок.
    var balance: Int = 60
    var journeys: [Journey] = []
    var missions: [Mission] = []
    var caches: [GeoCache] = []
    var outings: [Outing] = []
    var drawings: [CollabDrawing] = []
    var shares: [ShareItem] = []
    var anchor: LatLon = .moscow
    /// Демо-точки уже перенесены туда, где вы.
    var anchored: Bool = false

    /// Копия с изменениями.
    private func with(_ change: (inout TogetherState) -> Void) -> TogetherState {
        var s = self
        change(&s)
        return s
    }

    // MARK: Цели

    func joinGoal(_ id: String) -> TogetherState {
        with { s in
            s.goals = s.goals.map { g in
                var g = g
                if g.id == id && !g.joined {
                    g.joined = true
                    g.participants += 1
                }
                return g
            }
        }
    }

    /// Вклад в цель: вручную или из чек-ина.
    func contribute(_ id: String, _ amount: Double) -> TogetherState {
        if amount <= 0 { return self }
        return with { s in
            s.goals = s.goals.map { g in
                var g = g
                if g.id == id {
                    if !g.joined { g.participants += 1 }
                    g.joined = true
                    g.mine += amount
                    g.total += amount
                }
                return g
            }
        }
    }

    /// Чек-ин сам засчитывается в цели, где вы участвуете: километры, книги.
    func afterCheckIn(_ c: CheckIn) -> TogetherState {
        var s = self
        for g in goals where g.joined {
            let amount: Double
            switch g.metric {
            case .km: amount = isKilometers(c.unit) ? c.amount : 0
            case .books: amount = c.activity == "book" ? 1 : 0
            case .hours: amount = c.unit == "мин" && ["gym", "yoga", "study"].contains(c.activity) ? c.amount / 60 : 0
            case .trees, .other: amount = 0
            }
            s = s.contribute(g.id, amount)
        }
        return s
    }

    // MARK: Навыки

    func addSkill(teach: Bool, _ skill: String) -> TogetherState {
        let name = clip(skill, 40)
        if name.isEmpty { return self }
        let list = teach ? myTeach : myWant
        if list.contains(where: { $0.lowercased() == name.lowercased() }) { return self }
        return with { s in
            if teach { s.myTeach.append(name) } else { s.myWant.append(name) }
        }
    }

    func removeSkill(teach: Bool, _ skill: String) -> TogetherState {
        with { s in
            if teach { s.myTeach.removeAll { $0 == skill } } else { s.myWant.removeAll { $0 == skill } }
        }
    }

    /// Предложения — сначала взаимные (при равенстве — в прежнем порядке).
    func matches() -> [(offer: SkillOffer, score: Int)] {
        offers.enumerated()
            .map { (index: $0.offset, offer: $0.element, score: matchScore($0.element, myTeach, myWant)) }
            .sorted { $0.score != $1.score ? $0.score > $1.score : $0.index < $1.index }
            .map { (offer: $0.offer, score: $0.score) }
    }

    func propose(id: Int64, personId: Int, give: String, get: String, minutes: Int, now: Date?) -> TogetherState? {
        if give.isEmpty && get.isEmpty { return nil }
        // Урок «за часы» — только если хватает баланса: за вычетом уже обещанных уроков
        let promised = deals.filter { $0.status == .proposed || $0.status == .agreed }
            .reduce(0) { $0 + max(0, -$1.balanceChange) }
        if give.isEmpty && balance - promised < minutes { return nil }
        let deal = SkillDeal(id: id, personId: personId, give: give, get: get, minutes: minutes, createdAt: now)
        return with { $0.deals.insert(deal, at: 0) }
    }

    func setDeal(_ id: Int64, _ status: DealStatus) -> TogetherState {
        guard let deal = deals.first(where: { $0.id == id }) else { return self }
        // Прошедшее и отменённое — окончательно
        if deal.status == .done || deal.status == .declined { return self }
        let change = status == .done ? deal.balanceChange : 0
        return with { s in
            s.deals = s.deals.map { d in
                var d = d
                if d.id == id { d.status = status }
                return d
            }
            s.balance += change
        }
    }

    // MARK: Дневники

    func startJourney(id: Int64, me: Int, emoji: String, title: String, days: Int, now: Date?) -> TogetherState? {
        let name = clip(title, 60)
        if name.isEmpty { return nil }
        let journey = Journey(id: id, authorId: me, emoji: emoji, title: name, days: min(100, max(3, days)), createdAt: now)
        return with { $0.journeys.insert(journey, at: 0) }
    }

    func addEntry(_ journeyId: Int64, entryId: Int64, text: String, media: [Attachment], now: Date?) -> TogetherState {
        if blank(text) && media.isEmpty { return self }
        return with { s in
            s.journeys = s.journeys.map { j in
                var j = j
                if j.id == journeyId {
                    j.entries.append(JourneyEntry(id: entryId, day: j.nextDay, text: clip(text, 1000), media: media, createdAt: now))
                }
                return j
            }
        }
    }

    func addTip(_ journeyId: Int64, _ entryId: Int64, _ tip: Tip) -> TogetherState {
        if blank(tip.text) { return self }
        return updateEntry(journeyId, entryId) { $0.tips.append(tip) }
    }

    /// «Полезно» — повторное нажатие снимает отметку.
    func toggleHelpful(_ journeyId: Int64, _ entryId: Int64, _ tipId: Int64) -> TogetherState {
        updateEntry(journeyId, entryId) { e in
            e.tips = e.tips.map { tp in
                var tp = tp
                if tp.id == tipId {
                    tp.helpful = max(0, tp.helpful + (tp.markedByMe ? -1 : 1))
                    tp.markedByMe.toggle()
                }
                return tp
            }
        }
    }

    private func updateEntry(_ journeyId: Int64, _ entryId: Int64, _ change: (inout JourneyEntry) -> Void) -> TogetherState {
        with { s in
            s.journeys = s.journeys.map { j in
                var j = j
                if j.id == journeyId {
                    j.entries = j.entries.map { e in
                        var e = e
                        if e.id == entryId { change(&e) }
                        return e
                    }
                }
                return j
            }
        }
    }

    // MARK: Миссии

    /// Своя миссия района — право, которое открывают находки (Unlock.ownMission).
    func createMission(_ m: Mission) -> TogetherState? {
        if blank(m.title) || missions.contains(where: { $0.id == m.id }) { return nil }
        var mission = m
        mission.joined = true
        mission.target = min(20, max(1, m.target))
        return with { $0.missions.insert(mission, at: 0) }
    }

    /// Своя общая цель — право за вклад в чужие (Unlock.ownGoal).
    func createGoal(_ g: CommunityGoal) -> TogetherState? {
        if blank(g.title) || g.target <= 0 || goals.contains(where: { $0.id == g.id }) { return nil }
        var goal = g
        goal.joined = true
        goal.participants = 1
        return with { $0.goals.append(goal) }
    }

    /// После обновления приложения: новые демо-миссии и цели появляются и в сохранённом.
    func withNewDemo(_ demo: TogetherState) -> TogetherState {
        with { s in
            s.missions += demo.missions.filter { d in !missions.contains { $0.id == d.id } }
            s.goals += demo.goals.filter { d in !goals.contains { $0.id == d.id } }
        }
    }

    func joinMission(_ id: String) -> TogetherState {
        with { s in
            s.missions = s.missions.map { m in
                var m = m
                if m.id == id { m.joined = true }
                return m
            }
        }
    }

    func addFind(_ missionId: String, _ find: MissionFind) -> TogetherState {
        with { s in
            s.missions = s.missions.map { m in
                var m = m
                if m.id == missionId {
                    m.joined = true
                    m.finds.append(find)
                }
                return m
            }
        }
    }

    // MARK: Клады

    func hideCache(_ cache: GeoCache) -> TogetherState? {
        if blank(cache.title) || blank(cache.hint) { return nil }
        return with { $0.caches.insert(cache, at: 0) }
    }

    /// Нашли клад: запись в журнале. nil — вы слишком далеко или уже отмечались.
    func findCache(_ id: Int64, _ log: CacheLog, here: LatLon?) -> TogetherState? {
        guard let cache = caches.first(where: { $0.id == id }) else { return nil }
        if cache.foundByMe || cache.authorId == log.authorId { return nil }
        guard let here, here.distance(to: cache.at) <= cacheRadiusM else { return nil }
        return with { s in
            s.caches = s.caches.map { c in
                var c = c
                if c.id == id {
                    c.foundByMe = true
                    c.logs.append(log)
                }
                return c
            }
        }
    }

    // MARK: Вылазки

    func createOuting(_ o: Outing) -> TogetherState? {
        if blank(o.title) || blank(o.place) { return nil }
        var outing = o
        outing.capacity = min(Outing.maxPeople, max(2, o.capacity))
        let list = [outing] + outings
        // Сортировка устойчивая, как sortedBy на Android
        let sorted = list.enumerated()
            .sorted { $0.element.startsAt != $1.element.startsAt ? $0.element.startsAt < $1.element.startsAt : $0.offset < $1.offset }
            .map(\.element)
        return with { $0.outings = sorted }
    }

    /// Записаться или выйти; в полную вылазку не записаться.
    func toggleOuting(_ id: Int64, me: Int) -> TogetherState {
        with { s in
            s.outings = s.outings.map { o in
                var o = o
                guard o.id == id else { return o }
                if o.participants.contains(me) && o.organizerId != me {
                    o.participants.removeAll { $0 == me }
                } else if !o.participants.contains(me) && !o.full {
                    o.participants.append(me)
                }
                return o
            }
        }
    }

    func cancelOuting(_ id: Int64, me: Int) -> TogetherState {
        with { $0.outings.removeAll { $0.id == id && $0.organizerId == me } }
    }

    // MARK: Рисунок

    func startDrawing(id: Int64, title: String, turns: Int, me: Int, now: Date?) -> TogetherState? {
        let name = clip(title, 40)
        if name.isEmpty { return nil }
        let drawing = CollabDrawing(id: id, title: name, maxTurns: min(8, max(2, turns)), waitingFor: me, createdAt: now)
        return with { $0.drawings.insert(drawing, at: 0) }
    }

    /// Свой ход: штрихи и кому передать (nil — если ходов больше нет).
    func drawTurn(_ id: Int64, me: Int, strokes: [DrawStroke], passTo: Int?) -> TogetherState {
        with { s in
            s.drawings = s.drawings.map { d in
                guard d.id == id, d.waitingFor == me, !strokes.isEmpty else { return d }
                var next = d
                next.turns.append(DrawingTurn(authorId: me, strokes: strokes))
                next.waitingFor = next.finished ? nil : passTo
                return next
            }
        }
    }

    // MARK: Обмен

    func addShare(_ item: ShareItem) -> TogetherState? {
        if blank(item.title) { return nil }
        return with { $0.shares.insert(item, at: 0) }
    }

    /// Попросить: книгу почитать, семена забрать.
    func requestShare(_ id: Int64, me: Int) -> TogetherState {
        updateShares { x in
            if x.id == id && x.status == .available && x.holderId != me {
                x.status = .requested
                x.requestedBy = me
            }
        }
    }

    /// Передать: книга идёт к тому, кто попросил (или кому решили), семена — насовсем.
    func handOver(_ id: Int64, to: Int) -> TogetherState {
        updateShares { x in
            guard x.id == id else { return }
            switch x.kind {
            case .book:
                x.history.append(x.holderId)
                x.holderId = to
                x.status = .available
                x.requestedBy = nil
            case .seeds:
                x.history.append(x.holderId)
                x.holderId = to
                x.status = .given
                x.requestedBy = nil
            case .repair:
                break
            }
        }
    }

    func offerHelp(_ id: Int64, me: Int) -> TogetherState {
        updateShares { x in
            if x.id == id && x.kind == .repair && !x.helpers.contains(me) && x.ownerId != me {
                x.helpers.append(me)
            }
        }
    }

    func markFixed(_ id: Int64) -> TogetherState {
        updateShares { x in
            if x.id == id && x.kind == .repair { x.status = .fixed }
        }
    }

    private func updateShares(_ change: (inout ShareItem) -> Void) -> TogetherState {
        with { s in
            s.shares = s.shares.map { item in
                var x = item
                change(&x)
                return x
            }
        }
    }

    /// Демо-точки — вокруг того места, где вы на самом деле: один раз, при первом известном месте.
    func relocate(_ here: LatLon, meId: Int) -> TogetherState {
        if anchored { return with { $0.anchor = here } }
        let dLat = here.lat - anchor.lat
        let dLon = here.lon - anchor.lon
        func shift(_ p: LatLon) -> LatLon { LatLon(lat: p.lat + dLat, lon: p.lon + dLon) }
        return with { s in
            s.anchor = here
            s.anchored = true
            s.caches = s.caches.map { c in
                var c = c
                if c.authorId != meId { c.at = shift(c.at) }
                return c
            }
            s.missions = s.missions.map { m in
                var m = m
                m.finds = m.finds.map { f in
                    var f = f
                    if f.authorId != meId { f.at = shift(f.at) }
                    return f
                }
                return m
            }
            s.outings = s.outings.map { o in
                var o = o
                if o.organizerId != meId { o.at = o.at.map(shift) }
                return o
            }
        }
    }
}
