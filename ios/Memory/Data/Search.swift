import Foundation

/// #хештеги и @упоминания в тексте записей, комментариев и PRISM.
/// Тег — буквы любого языка, цифры и «_» (#закат, #yerevan, #երևան);
/// упоминание — логин Memory (латиница, цифры, точка, «_»).
///
/// Индексы (Span.start / end, начало упоминания) — в единицах UTF-16,
/// как у NSString и NSRange (и как в Kotlin): по ним удобно красить
/// NSAttributedString. То же — в Search.kt.
enum Tags {
    private static let tag = try! NSRegularExpression(pattern: "(?<![\\p{L}\\p{N}_&/])#([\\p{L}\\p{N}_]{2,40})")
    private static let mention = try! NSRegularExpression(pattern: "(?<![\\p{L}\\p{N}_.@/])@([a-zA-Z0-9._]{3,20})(?<!\\.)")
    private static let url = try! NSRegularExpression(pattern: "https?://[^\\s]+")

    /// Совпадения: весь кусок и первая группа.
    private static func matches(_ re: NSRegularExpression, _ text: String) -> [(range: NSRange, group: String)] {
        let ns = text as NSString
        return re.matches(in: text, range: NSRange(location: 0, length: ns.length)).map { m -> (range: NSRange, group: String) in
            let g = m.numberOfRanges > 1 && m.range(at: 1).location != NSNotFound ? ns.substring(with: m.range(at: 1)) : ns.substring(with: m.range)
            return (range: m.range, group: g)
        }
    }

    private static func distinct(_ list: [String]) -> [String] {
        var seen = Set<String>()
        return list.filter { seen.insert($0).inserted }
    }

    /// Теги текста без «#», строчными, без повторов: «#Закат #закат» → [закат].
    static func hashtags(_ text: String) -> [String] {
        distinct(matches(tag, text).map { $0.group.lowercased() })
    }

    /// Логины, упомянутые в тексте, строчными, без повторов.
    static func mentions(_ text: String) -> [String] {
        distinct(matches(mention, text).map { $0.group.lowercased() })
    }

    enum Kind: Equatable { case tag, mention, url }

    /// Кусок текста: тег, упоминание или ссылка. value — тег/логин без знака, или адрес.
    struct Span: Equatable {
        let start: Int
        let end: Int
        let kind: Kind
        let value: String
    }

    /// Где в тексте теги, упоминания и ссылки — по порядку, без пересечений.
    static func spans(_ text: String) -> [Span] {
        let ns = text as NSString
        let urls: [Span] = url.matches(in: text, range: NSRange(location: 0, length: ns.length)).map { m -> Span in
            var value = ns.substring(with: m.range)
            while let last = value.last, ".,)!?".contains(last) { value.removeLast() }
            return Span(start: m.range.location, end: m.range.location + value.utf16.count, kind: .url, value: value)
        }
        let tags = matches(tag, text).map {
            Span(start: $0.range.location, end: $0.range.location + $0.range.length, kind: .tag, value: $0.group.lowercased())
        }
        let mentions = matches(mention, text).map {
            Span(start: $0.range.location, end: $0.range.location + $0.range.length, kind: .mention, value: $0.group.lowercased())
        }
        var out: [Span] = []
        for s in (urls + tags + mentions).stableSorted(by: { $0.start < $1.start }) {
            if !out.contains(where: { s.start < $0.end && $0.start < s.end }) { out.append(s) }
        }
        return out
    }

    /// Подсказка при наборе: человек печатает «@ве» — какой кусок дописывается.
    /// Возвращает начало упоминания (индекс «@» в UTF-16) и введённую часть, или nil.
    /// cursor — позиция курсора в UTF-16; nil — конец текста.
    static func typingMention(_ text: String, cursor: Int? = nil) -> (at: Int, part: String)? {
        let units = Array(text.utf16)
        let c = min(max(cursor ?? units.count, 0), units.count)
        let before = String(decoding: units[0..<c], as: UTF16.self)
        guard let atIndex = before.lastIndex(of: "@") else { return nil }
        if atIndex > before.startIndex {
            let prev = before[before.index(before: atIndex)]
            if prev.isLetter || prev.isNumber || prev == "_" { return nil }
        }
        let part = String(before[before.index(after: atIndex)...])
        if part.utf16.count > 20 || part.contains(where: { !($0.isLetter || $0.isNumber || $0 == "." || $0 == "_") }) { return nil }
        return (atIndex.utf16Offset(in: before), part)
    }

    /// Вставить выбранный логин вместо набранного «@ве…». at и cursor — в UTF-16.
    static func completeMention(_ text: String, at: Int, cursor: Int, handle: String) -> String {
        let units = Array(text.utf16)
        let a = min(max(at, 0), units.count)
        let c = min(max(cursor, a), units.count)
        let head = String(decoding: units[0..<a], as: UTF16.self)
        let tail = String(decoding: units[c...], as: UTF16.self)
        return head + "@" + handle + " " + String(tail.drop(while: { $0.isWhitespace }))
    }
}

/// Тег и сколько записей с ним.
struct TagCount: Equatable, Hashable, Identifiable {
    let tag: String
    let count: Int

    var id: String { tag }
}

/// Что нашлось: люди, записи, группы и теги.
struct SearchResults: Equatable {
    var people: [User] = []
    var posts: [Post] = []
    var groups: [MemoryGroup] = []
    var tags: [TagCount] = []

    var empty: Bool { people.isEmpty && posts.isEmpty && groups.isEmpty && tags.isEmpty }
}

/// Поиск по всему Memory: люди (имя, логин, город, интересы), записи
/// (текст и теги), группы и теги. Без сервера ищет по тому, что есть на
/// телефоне; с сервером тот же интерфейс получит ответы оттуда.
enum Search {
    private static func norm(_ s: String) -> String {
        s.lowercased().replacingOccurrences(of: "ё", with: "е").trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func dropPrefix(_ s: String, _ prefix: String) -> String {
        s.hasPrefix(prefix) ? String(s.dropFirst(prefix.count)) : s
    }

    private static func words(_ query: String) -> [String] {
        dropPrefix(dropPrefix(norm(query), "#"), "@")
            .split(whereSeparator: { $0.isWhitespace })
            .map(String.init)
            .filter { !$0.isEmpty }
    }

    /// Люди: точный логин — первым, потом начало имени, потом остальное.
    static func people(_ query: String, _ people: [User]) -> [User] {
        let q = dropPrefix(norm(query), "@")
        if q.isEmpty { return [] }
        let ws = words(query)
        let scored: [(user: User, score: Int)] = people.compactMap { u -> (user: User, score: Int)? in
            let name = norm(u.name)
            let handle = u.handle.lowercased()
            let body = [name, handle, norm(u.city), norm(u.bio), u.interests.map { norm($0) }.joined(separator: " ")].joined(separator: " ")
            if !ws.allSatisfy({ body.contains($0) }) { return nil }
            let score: Int
            if handle == q {
                score = 100
            } else if handle.hasPrefix(q) {
                score = 80
            } else if name.hasPrefix(q) || name.split(separator: " ").contains(where: { $0.hasPrefix(q) }) {
                score = 60
            } else {
                score = 10
            }
            return (user: u, score: score)
        }
        return scored.stableSorted(by: { a, b in
            if a.score != b.score { return a.score > b.score }
            return a.user.name < b.user.name
        }).map { $0.user }
    }

    /// Новые сверху; у демо-записей без даты — после своих.
    private static func newestFirst(_ posts: [Post]) -> [Post] {
        posts.stableSorted(by: { ($0.createdAt ?? .distantPast) > ($1.createdAt ?? .distantPast) })
    }

    /// Записи: «#тег» — только с этим тегом; иначе все слова — в тексте или чек-ине. Новые сверху.
    static func posts(_ query: String, _ posts: [Post]) -> [Post] {
        let q = norm(query)
        if q.isEmpty { return [] }
        if q.hasPrefix("#") && !q.contains(" ") {
            let tag = dropPrefix(q, "#")
            return newestFirst(posts.filter { Tags.hashtags($0.text).contains(tag) })
        }
        let ws = words(query)
        return newestFirst(posts.filter { p in
            let extra: String = p.checkIn.map { $0.subject + " " + $0.displayTitle } ?? ""
            let place: String = p.place?.title ?? ""
            let original: String = p.repostOf?.text ?? ""
            let body = norm([p.text, extra, place, original].joined(separator: " "))
            return ws.allSatisfy { body.contains($0) }
        })
    }

    static func groups(_ query: String, _ groups: [MemoryGroup]) -> [MemoryGroup] {
        let ws = words(query)
        if ws.isEmpty { return [] }
        return groups.filter { g in
            let body = norm(g.name + " " + g.description + " " + g.topic)
            return ws.allSatisfy { body.contains($0) }
        }
    }

    /// Теги, которые начинаются с набранного, — с числом записей.
    static func tags(_ query: String, _ posts: [Post]) -> [TagCount] {
        let q = dropPrefix(norm(query), "#")
        if q.isEmpty { return [] }
        return Array(popularTags(posts, limit: 50).filter { $0.tag.hasPrefix(q) }.prefix(8))
    }

    /// Популярные теги — для пустого поиска.
    static func popularTags(_ posts: [Post], limit: Int = 12) -> [TagCount] {
        var counts: [String: Int] = [:]
        for p in posts {
            for tag in Tags.hashtags(p.text) { counts[tag, default: 0] += 1 }
        }
        return Array(counts.map { TagCount(tag: $0.key, count: $0.value) }
            .sorted { a, b in a.count != b.count ? a.count > b.count : a.tag < b.tag }
            .prefix(limit))
    }

    static func all(_ query: String, _ people: [User], _ posts: [Post], _ groups: [MemoryGroup]) -> SearchResults {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if q.isEmpty { return SearchResults() }
        if q.hasPrefix("#") { return SearchResults(posts: Self.posts(q, posts), tags: tags(q, posts)) }
        if q.hasPrefix("@") { return SearchResults(people: Self.people(q, people)) }
        return SearchResults(people: Self.people(q, people), posts: Self.posts(q, posts), groups: Self.groups(q, groups), tags: tags(q, posts))
    }

    /// Кого предложить после «@»: друзья первыми, потом остальные.
    static func mentionCandidates(_ part: String, _ people: [User], friends: Set<Int>, limit: Int = 5) -> [User] {
        let q = part.lowercased()
        let nq = norm(q)
        let found = people.filter { u in
            q.isEmpty || u.handle.lowercased().hasPrefix(q) || norm(u.name).split(separator: " ").contains(where: { $0.hasPrefix(nq) })
        }
        let sorted = found.stableSorted(by: { a, b in
            let fa = friends.contains(a.id), fb = friends.contains(b.id)
            if fa != fb { return fa }
            return a.name < b.name
        })
        return Array(sorted.prefix(limit))
    }

    /// История поиска: новый запрос — первым, без повторов, не больше limit.
    static func remember(_ history: [String], _ query: String, limit: Int = 10) -> [String] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if q.count < 2 { return history }
        return Array(([q] + history.filter { $0.lowercased() != q.lowercased() }).prefix(limit))
    }
}
