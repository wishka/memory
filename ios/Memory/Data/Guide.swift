import Foundation

// Гид по Memory — справка внутри приложения: главы, темы, шаги.
//
// Текст лежит в docs/guide/guide.json сразу на трёх языках (ru, en, hy); из него
// scripts/sync_guide.py собирает GuideContent.kt и GuideContent.swift.
// Поэтому гид не идёт через общий словарь t(): у каждой строки своя пара.

/// Строка гида на трёх языках.
struct GuideText: Equatable {
    let ru: String
    let en: String
    let hy: String

    /// Без армянского перевода — английский текст.
    init(ru: String, en: String, hy: String? = nil) {
        self.ru = ru
        self.en = en
        self.hy = hy ?? en
    }

    /// На выбранном языке интерфейса.
    var text: String { L10n.shared.pick(ru: ru, en: en, hy: hy) }
}

/// Куда ведёт кнопка темы «Открыть …».
enum GuideAction: String, CaseIterable {
    case feed, stories, prism, newPrism, chats, groups, together, profile
    case friends, requests, saved, music, studio, shelf, recap
    case settings, privacy, security, blacklist, notifications, sounds
    case wallpaper, language, legal, growth
    case premium
}

/// Тема: заголовок, шаги по порядку, совет и куда перейти.
struct GuideTopic: Equatable {
    let id: String
    let title: GuideText
    let steps: [GuideText]
    var tip: GuideText? = nil
    var action: GuideAction? = nil
}

/// Глава гида: значок, название, подзаголовок и темы.
struct GuideChapter: Equatable, Identifiable {
    let id: String
    let emoji: String
    let title: GuideText
    let lead: GuideText
    let topics: [GuideTopic]

    /// Ключ темы для отметки «прочитано»: «глава/тема».
    func key(_ topic: GuideTopic) -> String { "\(id)/\(topic.id)" }
}

/// Найденная тема: глава и тема.
struct GuideHit: Equatable, Identifiable {
    let chapter: GuideChapter
    let topic: GuideTopic

    var id: String { chapter.key(topic) }
}

enum Guide {
    /// Главы; пока Memory+ выключен, темы о нём скрыты.
    static var chapters: [GuideChapter] {
        if Premium.enabled { return GuideContent.chapters }
        return GuideContent.chapters.map { c in
            GuideChapter(id: c.id, emoji: c.emoji, title: c.title, lead: c.lead,
                         topics: c.topics.filter { $0.action != .premium })
        }
    }

    /// Всего тем — для «прочитано N из M».
    static var topicCount: Int { chapters.reduce(0) { $0 + $1.topics.count } }

    static func chapter(_ id: String?) -> GuideChapter? {
        guard let id else { return nil }
        return chapters.first { $0.id == id }
    }

    /// Все ключи тем — чтобы отметки старых, убранных тем не считались.
    static var keys: Set<String> {
        Set(chapters.flatMap { c in c.topics.map { c.key($0) } })
    }

    /// Поиск по темам на всех трёх языках: каждое слово запроса должно
    /// встретиться в заголовке, шагах или совете. Сначала — совпадения
    /// в заголовке.
    static func search(_ query: String) -> [GuideHit] {
        let words = query.lowercased().split(whereSeparator: { $0.isWhitespace }).map(String.init).filter { !$0.isEmpty }
        if words.isEmpty { return [] }
        var hits: [(hit: GuideHit, score: Int)] = []
        for c in chapters {
            for topic in c.topics {
                let title = "\(topic.title.ru) \(topic.title.en) \(topic.title.hy)".lowercased()
                var body = title + " " + c.title.ru + " " + c.title.en + " " + c.title.hy + " "
                for step in topic.steps { body += step.ru + " " + step.en + " " + step.hy + " " }
                if let tip = topic.tip { body += tip.ru + " " + tip.en + " " + tip.hy }
                body = body.lowercased()
                if words.allSatisfy({ body.contains($0) }) {
                    hits.append((GuideHit(chapter: c, topic: topic), words.filter { title.contains($0) }.count))
                }
            }
        }
        return hits.stableSorted { $0.score > $1.score }.map { $0.hit }
    }
}
