import Foundation

/// Опрос — в записи ленты, в группе и в сообщении. Вариантов 2–10;
/// multiple — можно выбрать несколько. mine — за что проголосовали вы
/// (номера вариантов): голос можно поменять, пока опрос не закрыт. Та же
/// логика — в Polls.kt.
struct PollOption: Equatable, Codable {
    var text: String
    var votes: Int = 0
}

struct Poll: Equatable, Codable {
    var id: Int64
    var question: String
    var options: [PollOption]
    var multiple: Bool = false
    var mine: Set<Int> = []
    var closed: Bool = false

    static let maxOptions = 10
    static let minOptions = 2

    var total: Int { options.reduce(0) { $0 + $1.votes } }
    var voted: Bool { !mine.isEmpty }
    /// Сколько человек проголосовало: при нескольких ответах голосов больше,
    /// чем людей — берём наибольший вариант как нижнюю оценку.
    var voters: Int { multiple ? (options.map(\.votes).max() ?? 0) : total }

    /// Доля варианта, 0…100, целыми; у всех вместе — ровно 100.
    func percents() -> [Int] {
        let sum = total
        if sum == 0 { return options.map { _ in 0 } }
        let raw = options.map { Double($0.votes) * 100 / Double(sum) }
        var floors = raw.map { Int($0) }
        var left = 100 - floors.reduce(0, +)
        // Остаток — вариантам с самой большой дробной частью (при равных — по порядку)
        let order = raw.indices.sorted { a, b in
            let fa = raw[a] - Double(Int(raw[a]))
            let fb = raw[b] - Double(Int(raw[b]))
            return fa != fb ? fa > fb : a < b
        }
        for i in order where left > 0 && options[i].votes > 0 {
            floors[i] += 1
            left -= 1
        }
        return floors
    }

    func percent(_ i: Int) -> Int {
        let p = percents()
        return p.indices.contains(i) ? p[i] : 0
    }

    /// Голос за вариант i: один ответ — переносит голос; несколько — включает и выключает.
    func vote(_ i: Int) -> Poll {
        if closed || !options.indices.contains(i) { return self }
        let next: Set<Int>
        if multiple {
            next = mine.contains(i) ? mine.subtracting([i]) : mine.union([i])
        } else if mine.contains(i) {
            next = []
        } else {
            next = [i]
        }
        var copy = self
        copy.options = options.enumerated().map { k, o in
            let was = mine.contains(k) ? 1 : 0
            let now = next.contains(k) ? 1 : 0
            return PollOption(text: o.text, votes: max(0, o.votes + now - was))
        }
        copy.mine = next
        return copy
    }

    /// Чужие голоса (в демо — друзья голосуют, позже — с сервера).
    /// Закрытый опрос голосов больше не принимает.
    func addVotes(_ counts: [Int: Int]) -> Poll {
        if closed { return self }
        var copy = self
        copy.options = options.enumerated().map { k, o in
            PollOption(text: o.text, votes: o.votes + max(0, counts[k] ?? 0))
        }
        return copy
    }

    func close() -> Poll {
        var copy = self
        copy.closed = true
        return copy
    }

    /// Победитель — вариант с большинством голосов; ничья — первый из них.
    var leader: Int? {
        var best: Int?
        for (i, o) in options.enumerated() where o.votes > (best.map { options[$0].votes } ?? 0) {
            best = i
        }
        return best
    }

    /// Ширина полоски результата: 0…1.
    func barFraction(_ i: Int) -> Double {
        guard total > 0, options.indices.contains(i) else { return 0 }
        return min(1, max(0, Double(options[i].votes) / Double(total)))
    }

    /// Новый опрос из того, что ввёл человек: пустые варианты отброшены,
    /// повторы — тоже. nil — так нельзя.
    static func create(id: Int64, question: String, options: [String], multiple: Bool) -> Poll? {
        let q = String(question.trimmingCharacters(in: .whitespacesAndNewlines).prefix(200))
        var seen: Set<String> = []
        var opts: [String] = []
        for raw in options {
            let o = String(raw.trimmingCharacters(in: .whitespacesAndNewlines).prefix(100))
            if o.isEmpty || seen.contains(o.lowercased()) { continue }
            seen.insert(o.lowercased())
            opts.append(o)
        }
        opts = Array(opts.prefix(maxOptions))
        if q.isEmpty || opts.count < minOptions { return nil }
        return Poll(id: id, question: q, options: opts.map { PollOption(text: $0) }, multiple: multiple)
    }

    /// Копия для пересылки: новый номер, чистые голоса, снова открыт.
    func fresh(id: Int64) -> Poll {
        Poll(id: id, question: question, options: options.map { PollOption(text: $0.text) }, multiple: multiple)
    }
}

/// Опрос, который человек составил, но ещё не отправил: номер даёт модель.
struct PollDraft: Equatable {
    var question: String
    var options: [String]
    var multiple: Bool = false

    func build(_ id: Int64) -> Poll? { Poll.create(id: id, question: question, options: options, multiple: multiple) }
}
