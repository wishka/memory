import Foundation

/// Что скрывать в комментариях к своим записям, в сообщениях и запросах на
/// переписку: свои «скрытые слова», оскорбления, спам. Решает телефон
/// (сервер дополнит своей проверкой). Своё и сказанное друзьями в
/// переписке не трогаем — только то, что приходит от других. То же — в Moderation.kt.
struct ModerationPrefs: Equatable {
    /// Свои слова и фразы: всё, где они есть, сворачивается.
    var hiddenWords: [String] = []
    /// Скрывать оскорбления (встроенный список на трёх языках).
    var hideOffensive: Bool = true
    /// Скрывать похожее на спам: ссылки пачкой, «заработок», повторы.
    var hideSpam: Bool = true
}

/// Почему скрыто — чтобы честно написать в свёрнутой строке.
enum HiddenReason: String, Equatable {
    case word = "Word"
    case offensive = "Offensive"
    case spam = "Spam"
}

enum Moderation {
    static let maxWords = 100
    static let maxWord = 40

    /// Корни оскорблений: ловят и формы слова («дурак», «дураки»). Список
    /// намеренно короткий и мягкий — это первый фильтр, не цензура.
    private static let offensive: [String] = [
        // русский
        "дурак", "дура ", "идиот", "дебил", "тупиц", "урод", "кретин", "придур", "мраз", "сволоч", "ублюд", "чмо",
        // английский
        "idiot", "stupid", "moron", "loser", "dumbass", "retard", "bitch",
        // армянский
        "ապուշ", "հիմար", "տխմար", "անասուն",
    ]

    private static let spam: [String] = [
        "заработок", "заработай", "пассивный доход", "казино", "ставки на спорт", "крипт", "бесплатно получи", "переходи по ссылке",
        "earn money", "free money", "casino", "click the link", "crypto giveaway",
    ]

    private static let link: NSRegularExpression? = try? NSRegularExpression(pattern: "(https?://|www\\.)\\S+", options: [.caseInsensitive])

    /// Латиница-двойники и цифры → кириллица (только в тексте с кириллицей).
    private static let lookalikes: [Character: Character] = [
        "0": "о", "3": "е", "@": "а", "4": "ч", "6": "б", "1": "и",
        "a": "а", "e": "е", "o": "о", "p": "р", "c": "с", "x": "х", "y": "у", "k": "к", "m": "м", "t": "т", "h": "н", "b": "в",
    ]

    /// Есть ли кириллица а…я (без ё — её к этому времени уже заменили).
    private static func hasCyrillic(_ s: String) -> Bool {
        for scalar in s.unicodeScalars {
            let v: UInt32 = scalar.value
            if v >= 0x430 && v <= 0x44F { return true }
        }
        return false
    }

    /// Привести к виду, где обходы не работают: строчные, ё→е, латиница-
    /// двойники и цифры → буквы («д0раk» → «дорак»), повторы букв — одна.
    static func normalize(_ text: String) -> String {
        let lower: String = text.lowercased().replacingOccurrences(of: "ё", with: "е")
        let cyrillic: Bool = hasCyrillic(lower)
        var out: [Character] = []
        var prev: Character = " "
        for ch in lower {
            var c: Character = ch
            if cyrillic, let mapped = lookalikes[ch] { c = mapped }
            let letter: Bool = c.isLetter || c.isNumber
            let next: Character = letter ? c : " "
            if next == prev && next != " " { continue }
            if next == " " && prev == " " { continue }
            out.append(next)
            prev = next
        }
        let body: String = String(out).trimmingCharacters(in: .whitespaces)
        return " " + body + " "
    }

    private static func normWord(_ w: String) -> String {
        normalize(w).trimmingCharacters(in: .whitespaces)
    }

    /// Добавить скрытое слово: без пустых и повторов, не длиннее 40 знаков.
    static func addWord(_ list: [String], _ word: String) -> [String] {
        let trimmed: String = word.trimmingCharacters(in: .whitespacesAndNewlines)
        let w = String(trimmed.prefix(maxWord))
        if w.isEmpty { return list }
        let key = normWord(w)
        if list.contains(where: { normWord($0) == key }) { return list }
        return Array((list + [w]).prefix(maxWords))
    }

    private static func linkCount(_ text: String) -> Int {
        guard let link else { return 0 }
        let range = NSRange(text.startIndex..<text.endIndex, in: text)
        return link.numberOfMatches(in: text, options: [], range: range)
    }

    static func isSpam(_ text: String) -> Bool {
        let n = normalize(text)
        let links = linkCount(text)
        if links >= 3 { return true }
        var hits = 0
        for phrase in spam {
            let p = normWord(phrase)
            if !p.isEmpty && n.contains(p) { hits += 1 }
        }
        if links >= 1 && hits >= 1 { return true }
        if hits >= 2 { return true }
        // «ааааааааа», «!!!!!!!!» и одно слово много раз подряд
        let words: [String] = n.split(separator: " ").map(String.init).filter { !$0.isEmpty }
        if words.count >= 6 {
            var counts: [String: Int] = [:]
            for w in words { counts[w, default: 0] += 1 }
            let top: Int = counts.values.max() ?? 0
            let limit: Int = words.count * 2 / 3
            if top >= limit { return true }
        }
        return false
    }

    static func isOffensive(_ text: String) -> Bool {
        let n = normalize(text)
        for root in offensive {
            var r: String = normalize(root)
            while r.hasSuffix(" ") { r.removeLast() }
            let needle: String = root.hasSuffix(" ") ? r + " " : r
            if n.contains(needle) { return true }
        }
        return false
    }

    /// Скрыть ли текст: nil — показывать. Свои слова ищутся с границами
    /// слова в начале («кот» не ловит «скот», но ловит «коты»).
    static func check(_ text: String, _ prefs: ModerationPrefs) -> HiddenReason? {
        if text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return nil }
        let n = normalize(text)
        for w in prefs.hiddenWords {
            let nw = normWord(w)
            if !nw.isEmpty && n.contains(" " + nw) { return .word }
        }
        if prefs.hideOffensive && isOffensive(text) { return .offensive }
        if prefs.hideSpam && isSpam(text) { return .spam }
        return nil
    }
}

/// Не чаще maxEvents раз за windowMs — от случайного флуда и залипшей
/// кнопки. Сервер ограничит и сам; здесь — чтобы человек сразу видел.
final class RateLimiter {
    private let maxEvents: Int
    private let windowMs: Int64
    private var times: [Int64] = []
    private let lock = NSLock()

    init(maxEvents: Int, windowMs: Int64) {
        self.maxEvents = maxEvents
        self.windowMs = windowMs
    }

    func allow(_ now: Int64) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        while let first = times.first, now - first >= windowMs {
            times.removeFirst()
        }
        if times.count >= maxEvents { return false }
        times.append(now)
        return true
    }
}
