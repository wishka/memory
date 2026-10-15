import Foundation

/// Раздел уведомлений — по нему работают выключатели в настройках.
/// follows — заявки в друзья (имя прежнее: так хранятся настройки и звуки).
enum NudgeKind: String, CaseIterable { case friends, groups, reactions, follows, messages, recap, prism }

/// Куда ведёт нажатие на уведомление.
enum NudgeTarget: String { case post, group, person, chat, recap, requests, together, prism }

/// Персональное уведомление: не «Вы давно не заходили», а что именно
/// случилось и почему это вам интересно — «Ваша подруга Вера только что
/// отметила фильм «Дюна», который вы обсуждали». Тексты собирает Nudges —
/// одинаково для локальных уведомлений и для пушей APNs (сервер
/// присылает только факты). То же — в Nudges.kt.
struct Nudge: Equatable, Identifiable {
    let id = UUID()
    let kind: NudgeKind
    let title: String
    let text: String
    let target: NudgeTarget
    var targetId: Int64 = 0

    static func == (a: Nudge, b: Nudge) -> Bool { a.id == b.id }
}

enum Nudges {
    /// Мужские имена на «-а» и «-я»: у остальных такие окончания — женские.
    private static let maleA: Set<String> = ["илья", "никита", "фома", "кузьма", "савва", "лука", "миша", "саша", "женя", "ваня", "петя", "дима", "коля", "серёжа", "лёша", "паша", "гоша", "толя", "юра"]

    /// Женское имя — по окончанию; нужно для «отметил / отметила».
    static func female(_ user: User) -> Bool {
        let n = user.firstName.lowercased()
        return (n.hasSuffix("а") || n.hasSuffix("я")) && !maleA.contains(n)
    }

    private static func g(_ user: User, _ male: String, _ female: String) -> String {
        Self.female(user) ? female : male
    }

    /// Обсуждали ли вы это: название встречается в ваших записях,
    /// комментариях или сообщениях. «Дюна» — «дюну»: без последней гласной.
    static func discussed(_ subject: String, _ myTexts: [String]) -> Bool {
        let s = subject.trimmingCharacters(in: .whitespaces).lowercased().replacingOccurrences(of: "ё", with: "е")
        guard s.count >= 3 else { return false }
        var stem = s
        if s.count >= 4, let last = s.last, "аяоеиыуюйь".contains(last) { stem.removeLast() }
        return myTexts.contains { $0.lowercased().replacingOccurrences(of: "ё", with: "е").contains(stem) }
    }

    /// Друг отметил чек-ин. myKm — сколько вы сами набегали за месяц.
    static func friendCheckIn(_ friend: User, _ c: CheckIn, discussed: Bool, myKm: Double, postId: Int64) -> Nudge {
        let name = friend.firstName
        let subject = c.subject
        let amount = amountNumber(c.amount)
        let text: String
        switch c.activity {
        case "movie":
            if subject.isEmpty {
                text = tf("%s смотрит кино — спросите, что именно", name)
            } else if discussed {
                text = tf(g(friend, "Ваш друг %s только что отметил фильм «%s», который вы обсуждали", "Ваша подруга %s только что отметила фильм «%s», который вы обсуждали"), name, subject)
            } else {
                text = tf("%s смотрит «%s». Видели?", name, subject)
            }
        case "series":
            text = discussed && !subject.isEmpty
                ? tf(g(friend, "Ваш друг %s взялся за сериал «%s», который вы обсуждали", "Ваша подруга %s взялась за сериал «%s», который вы обсуждали"), name, subject)
                : tf("%s смотрит сериал «%s»", name, subject.isEmpty ? "…" : subject)
        case "book":
            text = discussed && !subject.isEmpty
                ? tf(g(friend, "Ваш друг %s начал читать «%s» — книгу, которую вы обсуждали", "Ваша подруга %s начала читать «%s» — книгу, которую вы обсуждали"), name, subject)
                : tf("%s читает «%s»", name, subject.isEmpty ? "…" : subject)
        case "run", "bike", "walk", "swim":
            let did: String
            switch c.activity {
            case "run": did = tf(g(friend, "%s пробежал %s км", "%s пробежала %s км"), name, amount)
            case "bike": did = tf(g(friend, "%s проехал %s км на велосипеде", "%s проехала %s км на велосипеде"), name, amount)
            case "walk": did = tf(g(friend, "%s прошёл %s км пешком", "%s прошла %s км пешком"), name, amount)
            default: did = tf(g(friend, "%s проплыл %s км", "%s проплыла %s км"), name, amount)
            }
            text = myKm > 0 ? tf("%s. У вас за месяц — %s км, не отставайте!", did, amountNumber(myKm)) : did
        case "coffee", "food", "place", "travel", "concert":
            if subject.isEmpty {
                text = "\(name): \(c.emoji) \(c.displayTitle)"
            } else if discussed {
                text = tf("%s сейчас в «%s» — вы как раз это обсуждали", name, subject)
            } else {
                text = tf("%s сейчас здесь: %s", name, subject)
            }
        default:
            let parts = [c.displayTitle, subject, c.amountTitle].filter { !$0.isEmpty }.joined(separator: " · ")
            text = "\(name): \(c.emoji) \(parts)"
        }
        return Nudge(kind: .friends, title: friend.name, text: text, target: .post, targetId: postId)
    }

    static func groupMember(_ group: MemoryGroup, _ person: User) -> Nudge {
        Nudge(kind: .groups, title: group.name,
              text: tf("В вашей группе «%s» новый участник — %s", group.name, person.name),
              target: .group, targetId: Int64(group.id))
    }

    static func liked(_ person: User, _ post: Post) -> Nudge {
        let name = person.firstName
        let text: String
        if let c = post.checkIn {
            text = tf(g(person, "%s оценил ваш чек-ин «%s»", "%s оценила ваш чек-ин «%s»"), name, c.subject.isEmpty ? c.displayTitle : c.subject)
        } else {
            text = tf(g(person, "%s оценил вашу запись «%s»", "%s оценила вашу запись «%s»"), name, preview(post))
        }
        return Nudge(kind: .reactions, title: person.name, text: text, target: .post, targetId: post.id)
    }

    /// Входящий звонок, пока приложение свёрнуто.
    static func incomingCall(_ friend: User, video: Bool) -> Nudge {
        Nudge(kind: .messages, title: friend.name, text: video ? t("Видеозвонок") : t("Звонок"),
              target: .person, targetId: Int64(friend.id))
    }

    /// Пропущенный звонок — нажатие открывает переписку.
    static func missedCall(_ friend: User, video: Bool, chatId: Int) -> Nudge {
        Nudge(kind: .messages, title: friend.name, text: video ? t("Пропущенный видеозвонок") : t("Пропущенный звонок"),
              target: .chat, targetId: Int64(chatId))
    }

    /// Друг начал прямой эфир. targetId — друг: нажатие откроет его эфир, если он ещё идёт.
    static func liveStarted(_ friend: User, _ title: String) -> Nudge {
        Nudge(kind: .friends, title: friend.name,
              text: tf(g(friend, "%s начал эфир: %s", "%s начала эфир: %s"), friend.firstName, title),
              target: .person, targetId: Int64(friend.id))
    }

    /// Комментарий к вашей записи (или упоминание в нём).
    static func commented(_ person: User, _ post: Post, _ text: String) -> Nudge {
        Nudge(kind: .reactions, title: person.name,
              text: tf(g(person, "%s прокомментировал: %s", "%s прокомментировала: %s"), person.firstName, String(text.prefix(80))),
              target: .post, targetId: post.id)
    }

    /// Заявка в друзья ко мне; common — общие интересы, если есть.
    static func followRequest(_ person: User, common: [String]) -> Nudge {
        let text = common.isEmpty
            ? tf("%s хочет добавить вас в друзья", person.name)
            : tf("%s хочет добавить вас в друзья — у вас общие интересы: %s", person.name, common.prefix(2).map { t($0) }.joined(separator: ", "))
        return Nudge(kind: .follows, title: person.name, text: text, target: .requests)
    }

    /// Человек принял мою заявку в друзья.
    static func requestAccepted(_ person: User) -> Nudge {
        Nudge(kind: .follows, title: person.name,
              text: tf(g(person, "%s принял вашу заявку в друзья", "%s приняла вашу заявку в друзья"), person.firstName),
              target: .person, targetId: Int64(person.id))
    }

    static func message(_ peer: User, _ text: String, chatId: Int) -> Nudge {
        Nudge(kind: .messages, title: peer.name, text: String(text.prefix(120)), target: .chat, targetId: Int64(chatId))
    }

    /// targetId — год × 100 + месяц (0 — итоги года): нажатие откроет именно этот период.
    static func recap(_ recap: Recap) -> Nudge {
        Nudge(kind: .recap, title: tf("Итоги: %s", recap.title),
              text: recap.period == .year ? tf("%s. Посмотрите, каким был ваш год", recap.headline)
                                          : tf("%s. Посмотрите, каким был ваш месяц", recap.headline),
              target: .recap, targetId: Int64(recap.year * 100 + recap.month))
    }

    // MARK: Вместе: targetId — раздел вкладки (TogetherSection.index)

    private static func together(_ person: User, _ text: String, _ section: TogetherSection) -> Nudge {
        Nudge(kind: .friends, title: person.name, text: text, target: .together, targetId: Int64(section.index))
    }

    static func dealAgreed(_ person: User, _ deal: SkillDeal) -> Nudge {
        let text: String
        if !deal.give.isEmpty && !deal.get.isEmpty {
            text = tf("Договорились об обмене: вы учите «%s», вас — «%s»", deal.give, deal.get)
        } else if !deal.get.isEmpty {
            text = tf("Договорились: урок «%s» за %s из банка времени", deal.get, minutesTitle(deal.minutes))
        } else {
            text = tf("Договорились: вы учите «%s» — в банк времени придёт %s", deal.give, minutesTitle(deal.minutes))
        }
        return together(person, text, .skills)
    }

    static func tipReceived(_ person: User, _ journey: Journey, day: Int) -> Nudge {
        together(person, tf("%s даёт совет к «День %s: %s»", person.firstName, day, journey.title), .journeys)
    }

    static func drawingTurn(_ person: User, _ drawing: CollabDrawing) -> Nudge {
        let text = drawing.finished
            ? tf(g(person, "%s дорисовал «%s» — рисунок готов", "%s дорисовала «%s» — рисунок готов"), person.firstName, drawing.title)
            : tf(g(person, "%s дорисовал «%s» — ваша очередь", "%s дорисовала «%s» — ваша очередь"), person.firstName, drawing.title)
        return together(person, text, .drawing)
    }

    static func outingJoined(_ person: User, _ outing: Outing) -> Nudge {
        together(person, tf("%s идёт с вами: «%s»", person.firstName, outing.title), .outings)
    }

    static func shareHanded(_ person: User, _ item: ShareItem) -> Nudge {
        together(person, tf("%s передаёт вам «%s»", person.firstName, item.title), .sharing)
    }

    static func shareRequested(_ person: User, _ item: ShareItem) -> Nudge {
        together(person, tf("%s просит «%s»", person.firstName, item.title), .sharing)
    }

    static func cacheFound(_ person: User, _ cache: GeoCache) -> Nudge {
        together(person, tf(g(person, "%s нашёл ваш клад «%s»", "%s нашла ваш клад «%s»"), person.firstName, cache.title), .caches)
    }

    /// Можно ли показывать: общий выключатель и выключатель раздела.
    static func allowed(_ n: Nudge, _ s: Notifications) -> Bool {
        guard s.all else { return false }
        switch n.kind {
        case .friends: return s.friends
        case .groups: return s.groups
        case .reactions: return s.reactions
        case .follows: return s.follows
        case .messages: return s.messages
        case .recap: return s.recap
        case .prism: return s.prism
        }
    }

    /// Друг выложил PRISM: «Вера выложила PRISM «Огни Невы»». targetId — номер ролика.
    static func prismPublished(_ friend: User, _ prism: Prism) -> Nudge {
        var caption = prism.caption.replacingOccurrences(of: "\n", with: " ").trimmingCharacters(in: .whitespaces)
        if caption.count > 40 { caption = String(caption.prefix(40)).trimmingCharacters(in: .whitespaces) + "…" }
        let text = caption.isEmpty
            ? tf(g(friend, "%s выложил новый PRISM", "%s выложила новый PRISM"), friend.firstName)
            : tf(g(friend, "%s выложил PRISM «%s»", "%s выложила PRISM «%s»"), friend.firstName, caption)
        return Nudge(kind: .prism, title: "PRISM", text: text, target: .prism, targetId: prism.id)
    }

    /// Опрос: «За ваш опрос «Куда едем?» проголосовали ещё 3».
    /// groupId — опрос в группе: открывается в группе, в ленте его нет.
    static func pollVotes(_ question: String, _ added: Int, postId: Int64, groupId: Int? = nil) -> Nudge {
        Nudge(kind: .reactions, title: t("Опрос"), text: tf("«%s»: новых голосов — %s", String(question.prefix(40)), added),
              target: groupId != nil ? .group : .post, targetId: groupId.map(Int64.init) ?? postId)
    }

    /// Друг поделился местом в переписке.
    static func placeShared(_ friend: User, chatId: Int) -> Nudge {
        Nudge(kind: .messages, title: friend.name,
              text: tf(g(friend, "%s поделился местом на карте", "%s поделилась местом на карте"), friend.firstName),
              target: .chat, targetId: Int64(chatId))
    }

    private static func preview(_ post: Post) -> String {
        let text = post.text.replacingOccurrences(of: "\n", with: " ").trimmingCharacters(in: .whitespaces)
        if text.isEmpty { return t("фото") }
        if text.count <= 40 { return text }
        return String(text.prefix(40)).trimmingCharacters(in: .whitespaces) + "…"
    }

    /// Пуш с сервера: в данных только факты — текст складывается здесь, на
    /// языке телефона. Неизвестный тип — берём готовые title / body. См. docs/PUSH.md.
    static func fromPush(_ data: [String: String]) -> Nudge? {
        let actor = User(id: Int(data["actorId"] ?? "") ?? 0, name: data["actor"] ?? "", handle: "")
        switch data["type"] {
        case "friend_checkin":
            let activity = data["activity"] ?? ""
            let builtIn = checkInActivities.first { $0.id == activity }
            let c = CheckIn(
                activity: activity, emoji: data["emoji"] ?? builtIn?.emoji ?? "✦",
                title: builtIn?.title ?? data["activityTitle"] ?? "",
                subject: data["subject"] ?? "", amount: Double(data["amount"] ?? "") ?? 0,
                unit: builtIn?.unit ?? data["unit"] ?? ""
            )
            return friendCheckIn(actor, c, discussed: data["discussed"] == "1", myKm: Double(data["myKm"] ?? "") ?? 0,
                                 postId: Int64(data["postId"] ?? "") ?? 0)
        case "group_member":
            let group = MemoryGroup(id: Int(data["groupId"] ?? "") ?? 0, name: data["group"] ?? "", description: "", topic: "", members: 0, seed: 0)
            return groupMember(group, actor)
        case "follow_request":
            return followRequest(actor, common: (data["common"] ?? "").split(separator: ",").map(String.init))
        case "request_accepted":
            return requestAccepted(actor)
        case "message":
            return message(actor, data["body"] ?? "", chatId: Int(data["chatId"] ?? "") ?? 0)
        case "prism":
            let prism = Prism(id: Int64(data["prismId"] ?? "") ?? 0, author: actor, caption: data["caption"] ?? "",
                              music: "", seed: 0, likes: 0, comments: 0)
            return prismPublished(actor, prism)
        default:
            guard let body = data["body"], !body.isEmpty else { return nil }
            return Nudge(kind: .friends, title: data["title"] ?? "Memory", text: body, target: .post)
        }
    }
}
