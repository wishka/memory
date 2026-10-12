import XCTest
@testable import Memory

/// Полка, память, рекомендации, план бега, права, отклик, приглашения.
/// Те же проверки, что Round12Test на Android.
final class Round12Tests: XCTestCase {

    override func setUp() {
        super.setUp()
        L10n.shared.setLanguage(.ru)
    }

    private let zone = TimeZone(identifier: "Europe/Moscow")!
    private let day: TimeInterval = 24 * 3600

    /// Среда, 14 октября 2026, полдень по Москве.
    private var now: Date { date(2026, 10, 14, 12) }

    private func date(_ year: Int, _ month: Int, _ day: Int, _ hour: Int) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        return calendar.date(from: DateComponents(year: year, month: month, day: day, hour: hour))!
    }

    private func act(_ id: String) -> CheckInActivity { checkInActivities.first { $0.id == id }! }

    private func run(_ km: Double, minutes: Int = 0, at: Date? = nil) -> Dated {
        Dated(checkIn: act("run").checkIn("Парк", km, minutes: minutes), at: at ?? now)
    }

    private func movie(_ subject: String, rating: Int = 0, at: Date? = nil) -> Dated {
        Dated(checkIn: act("movie").checkIn(subject, 0, rating: rating), at: at ?? now)
    }

    private func book(_ subject: String, _ status: String, pages: Double = 0, at: Date? = nil) -> Dated {
        Dated(checkIn: act("book").checkIn(subject, pages, status: status), at: at ?? now)
    }

    func testPaceAndStructuredCheckIns() {
        XCTAssertEqual("5:00 /км", paceTitle("run", 6, 30))
        XCTAssertEqual("20 км/ч", paceTitle("bike", 20, 60))
        XCTAssertEqual("", paceTitle("run", 6, 0))
        // Оценка — только там, где оценивают; статус — только у книги
        XCTAssertEqual(0, act("run").checkIn("Парк", 5, rating: 4).rating)
        XCTAssertEqual(5, act("movie").checkIn("Дюна", 0, rating: 9).rating)
        XCTAssertEqual("", act("movie").checkIn("Дюна", 0, status: "done").status)
        XCTAssertEqual(0, act("movie").checkIn("Дюна", 0, minutes: 120).minutes)
        XCTAssertEqual("Прочитал", act("book").checkIn("1984", 0, status: "done").statusTitle)
    }

    func testOldCheckInsDecodeWithoutNewFields() throws {
        let json = #"{"activity":"run","emoji":"🏃","title":"Бегаю","subject":"Парк","amount":5,"unit":"км"}"#
        let c = try JSONDecoder().decode(CheckIn.self, from: Data(json.utf8))
        XCTAssertEqual(0, c.rating)
        XCTAssertEqual("", c.status)
        XCTAssertEqual(0, c.minutes)
    }

    func testShelfBuildsItself() throws {
        let shelf = Shelf.build(
            [
                movie("Дюна", rating: 4, at: now - 3 * day), movie(" дюна ", rating: 5, at: now - day), movie("Прибытие", rating: 0, at: now),
                book("1984", "reading", pages: 120, at: now - 2 * day), book("1984", "done", pages: 80, at: now - day), book("Лавр", "", pages: 30, at: now),
                run(5, minutes: 30, at: now - 40 * day), run(10, minutes: 55, at: now - day), run(3, minutes: 14, at: now),
                Dated(checkIn: act("coffee").checkIn("Зерно", 0, rating: 4), at: now),
                Dated(checkIn: act("coffee").checkIn("зерно", 0), at: now - day),
            ],
            [
                ShelfMark(activity: "movie", subject: "Солярис", status: BookStatus.want.id, at: now - 5 * day),
                ShelfMark(activity: "movie", subject: "Прибытие", status: "want", at: now - 9 * day),
            ],
            now: now, zone: zone
        )
        let dune = try XCTUnwrap(shelf.films.first { $0.subject == "Дюна" })
        XCTAssertEqual(2, dune.times)
        XCTAssertEqual(5, dune.rating)
        // Посмотрели после «хочу» — уже не в планах
        XCTAssertEqual(["Солярис"], shelf.plannedFilms.map(\.subject))
        XCTAssertEqual(2, shelf.watched.count)
        XCTAssertEqual(["1984"], shelf.booksBy(.done).map(\.subject))
        XCTAssertEqual(200, shelf.books.first { $0.subject == "1984" }?.pages ?? -1, accuracy: 0.01)
        XCTAssertEqual(["Лавр"], shelf.booksBy(.reading).map(\.subject))
        XCTAssertEqual(1, shelf.sport.count)
        let runs = try XCTUnwrap(shelf.sport.first)
        XCTAssertEqual(18, runs.totalKm, accuracy: 0.01)
        XCTAssertEqual(13, runs.monthKm, accuracy: 0.01)
        XCTAssertEqual(10, runs.longestKm, accuracy: 0.01)
        XCTAssertEqual(280, runs.bestPace)
        XCTAssertEqual(1, shelf.places.count)
        XCTAssertEqual(2, shelf.places.first?.count)
        XCTAssertEqual(4, shelf.places.first?.rating)
        XCTAssertEqual(["film:дюна", "film:прибытие", "film:солярис", "book:1984", "book:лавр"], shelf.keys)
    }

    func testMemoriesOfTheDay() {
        let me = Demo.me
        func post(_ id: Int64, _ at: Date) -> Post { Post(id: id, author: me, text: "Запись \(id)", minutesAgo: 0, createdAt: at) }
        let yearAgo = date(2025, 10, 14, 9)
        let monthAgo = date(2026, 8, 14, 9)
        let weekAgo = now - 7 * day
        let other = Post(id: 9, author: Demo.people[0], text: "Чужая", minutesAgo: 0, createdAt: yearAgo)
        let all = memoriesOfDay([post(1, yearAgo), post(2, monthAgo), post(3, weekAgo), other], meId: me.id, now: now, zone: zone)
        XCTAssertEqual([1], all.map(\.post.id))
        XCTAssertEqual("Год назад", all.first?.label)
        XCTAssertEqual(["2 месяца назад"], memoriesOfDay([post(2, monthAgo), post(3, weekAgo)], meId: me.id, now: now, zone: zone).map(\.label))
        XCTAssertEqual(["Неделю назад"], memoriesOfDay([post(3, weekAgo)], meId: me.id, now: now, zone: zone).map(\.label))
        XCTAssertTrue(memoriesOfDay([post(4, now - 3 * day)], meId: me.id, now: now, zone: zone).isEmpty)
    }

    func testTasteAndRecommendations() throws {
        let mine = [Rated(kind: "film", subject: "Интерстеллар", rating: 5), Rated(kind: "film", subject: "Дюна", rating: 5), Rated(kind: "film", subject: "Прибытие", rating: 5)]
        let vera = tasteMatch(mine, DemoTaste.ratings[2] ?? [])
        XCTAssertEqual(3, vera.common)
        XCTAssertEqual(100, vera.percent)
        let recs = recommend(mine, exclude: Set(mine.map(\.key)), friends: DemoTaste.ratings, kind: "film")
        // Вера с тем же вкусом — её «Солярис» первым; уже оценённое не советуем
        XCTAssertEqual("Солярис", recs.first?.subject)
        XCTAssertEqual(2, recs.first?.personId)
        XCTAssertTrue(recs.allSatisfy { $0.subject != "Дюна" })
        // Противоположный вкус не советует: Алина ставит «Брату» 2, Марк — 5
        let brat = [Rated(kind: "film", subject: "Брат", rating: 5), Rated(kind: "film", subject: "Амели", rating: 1), Rated(kind: "film", subject: "Ла-Ла Ленд", rating: 4)]
        let forBrat = recommend(brat, exclude: Set(brat.map(\.key)), friends: DemoTaste.ratings, kind: "film")
        XCTAssertTrue(forBrat.allSatisfy { $0.personId != 4 })
        // Новичок без оценок всё равно получает подборку
        XCTAssertFalse(recommend([], exclude: [], friends: DemoTaste.ratings, kind: "book").isEmpty)
        XCTAssertNotNil(Catalog.find("book", "мастер и маргарита"))
    }

    func testRunPlanGrowsTenPercent() {
        XCTAssertEqual(.start, runPlan([], now: now, zone: zone).reason)
        XCTAssertEqual(5, runPlan([], now: now, zone: zone).targetKm, accuracy: 0)
        // Прошлая неделя (5–11 октября): 20 км → план 22
        let grow = runPlan([run(10, at: now - 5 * day), run(10, at: now - 4 * day), run(3, at: now)], now: now, zone: zone)
        XCTAssertEqual(.grow, grow.reason)
        XCTAssertEqual(22, grow.targetKm, accuracy: 0)
        XCTAssertEqual(3, grow.doneKm, accuracy: 0)
        XCTAssertEqual(19, grow.leftKm, accuracy: 0)
        // Пропустили прошлую неделю — мягче, чем в среднем
        let back = runPlan([run(20, at: now - 16 * day)], now: now, zone: zone)
        XCTAssertEqual(.comeback, back.reason)
        XCTAssertEqual(4, back.targetKm, accuracy: 0)
    }

    func testUnlocksAreEarned() throws {
        let t = TogetherDemo.initial(now: Date(timeIntervalSince1970: 0))
        XCTAssertFalse(unlockProgress(t, 1).first { $0.unlock == .hideCache }?.open ?? true)
        var found = t
        found.caches = t.caches.map { c in
            var c = c
            c.foundByMe = true
            return c
        }
        XCTAssertTrue(unlockProgress(found, 1).first { $0.unlock == .hideCache }?.open ?? false)
        let goals = t.contribute("km", 3).contribute("books", 1)
        XCTAssertTrue(unlockProgress(goals, 1).first { $0.unlock == .ownGoal }?.open ?? false)
        XCTAssertEqual(1, unlockProgress(t.contribute("km", 3), 1).first { $0.unlock == .ownGoal }?.left)
        let route = try XCTUnwrap(t.missions.first { $0.id == "route" })
        XCTAssertTrue(route.locked(19))
        XCTAssertFalse(route.locked(20))
    }

    func testInvitesRoundTrip() {
        let code = Invites.code("wishka")
        XCTAssertEqual(6, code.count)
        XCTAssertEqual(code, Invites.code("WISHKA"))
        let invite = Invite(code: code, action: .drawing, targetId: "9701", from: "Андрей Б")
        let web = Invites.webLink(invite)
        XCTAssertTrue(web.hasPrefix(Invites.web))
        XCTAssertEqual(invite, Invites.parse(web))
        XCTAssertEqual(invite, Invites.parse(Invites.appLink(invite)))
        // Страница-приглашение кодирует пробел как %20
        XCTAssertEqual("Андрей Б", Invites.parse("memory://invite?c=\(code)&a=book&n=%D0%90%D0%BD%D0%B4%D1%80%D0%B5%D0%B9%20%D0%91")?.from)
        XCTAssertNil(Invites.parse("https://example.com/?c=\(code)"))
        XCTAssertNil(Invites.parse("memory://invite?c=ABC"))
        XCTAssertEqual(.app, Invites.parse("memory://invite?c=\(code)&a=nonsense")?.action)
        XCTAssertTrue(Invites.message(invite, title: "Город").contains("дорисовать общий рисунок «Город»"))
        // Как URLEncoder на Android: пробел — «+», кириллица — байтами UTF-8
        XCTAssertEqual("%D0%90+b.-*_", formEncode("А b.-*_"))
    }

    func testGrowthLog() {
        var log: [GrowthEvent] = []
        for i in 0..<600 {
            log = log.logged(GrowthEvent(type: .inviteSent, at: Date(timeIntervalSince1970: TimeInterval(i)), detail: i % 2 == 0 ? "drawing:1" : "book"))
        }
        XCTAssertEqual(500, log.count)
        let stats = growthStats(log + [GrowthEvent(type: .joinedByInvite, at: Date(timeIntervalSince1970: 0))])
        XCTAssertEqual(500, stats.invitesSent)
        XCTAssertEqual(250, stats.byAction["drawing"])
        XCTAssertTrue(stats.joinedByInvite)
        XCTAssertEqual(50, stats.kEstimate(), accuracy: 0.001)
    }

    func testModelEchoShelfAndInvites() throws {
        let vm = AppModel(demoReplies: false)
        vm.joinGoal("km")
        vm.publish("", checkIn: act("run").checkIn("Парк", 5, minutes: 25))
        let echo = try XCTUnwrap(vm.state.echo)
        let texts = echo.lines.map(\.text)
        XCTAssertTrue(texts.contains { $0.contains("+5 км") }, "\(texts)")
        XCTAssertTrue(texts.contains { $0.contains("План недели выполнен") }, "\(texts)")
        XCTAssertTrue(texts.contains { $0.contains("Огни набережных") }, "\(texts)")
        vm.dismissEcho()
        XCTAssertNil(vm.state.echo)
        // Знакомство: любимое на полке — и подборка уже по вкусу
        vm.addFavorites(Catalog.films.filter { ["Интерстеллар", "Дюна", "Прибытие"].contains($0.title) })
        XCTAssertEqual(3, vm.state.shelf.watched.count)
        XCTAssertEqual("Солярис", vm.state.recommendations("film").first?.subject)
        vm.planOnShelf("film", "Солярис")
        XCTAssertEqual(["Солярис"], vm.state.shelf.plannedFilms.map(\.subject))
        XCTAssertTrue(vm.state.recommendations("film").allSatisfy { $0.subject != "Солярис" })
        vm.dismissRecommendation("film:сталкер")
        XCTAssertTrue(vm.state.recommendations("film").allSatisfy { $0.subject != "Сталкер" })
        // Права: прятать клады и свою миссию — не сразу
        XCTAssertFalse(vm.hideCache(title: "Клад", hint: "Под камнем", difficulty: 2, size: "Микро", at: .moscow))
        XCTAssertNotNil(vm.createMission(emoji: "🗺️", title: "Почтовые ящики", description: "", target: 5))
        // Чужое приглашение: кто позвал, и ведём к рисунку
        let invite = Invite(code: "ABCDEF", action: .drawing, targetId: "9701", from: "Вера")
        let fresh = AppModel(demoReplies: false)
        fresh.openInvite(invite)
        XCTAssertEqual(invite, fresh.state.invitedBy)
        XCTAssertEqual(.together, fresh.state.tab)
        XCTAssertEqual(.drawing, fresh.state.togetherSection)
        XCTAssertTrue(fresh.state.growth.contains { $0.type == .joinedByInvite })
        // Своя ссылка — не «пригласили»
        let own = AppModel(demoReplies: false)
        own.openInvite(Invite(code: own.state.inviteCode, action: .app))
        XCTAssertNil(own.state.invitedBy)
        XCTAssertTrue(own.invite(.book, targetId: "9802", title: "Задача трёх тел").contains(Invites.web))
        XCTAssertEqual(1, growthStats(own.state.growth).invitesSent)
    }

    func testReviewFixes() throws {
        // Оценка с полки и «Смотрел» — не лишний просмотр; «Прочитал» после чек-ина «Читаю» — книга прочитана один раз
        let shelf = Shelf.build(
            [movie("Дюна", at: now - day), book("Лавр", BookStatus.reading.id, at: now - day)],
            [
                ShelfMark(activity: "movie", subject: "Дюна", rating: 5, status: "", at: now),
                ShelfMark(activity: "book", subject: "Лавр", rating: 4, status: BookStatus.done.id, at: now),
                ShelfMark(activity: "movie", subject: "Брат", rating: 4, status: "", at: now),
            ],
            now: now, zone: zone
        )
        XCTAssertEqual(1, shelf.films.first { $0.subject == "Дюна" }?.times)
        XCTAssertEqual(5, shelf.films.first { $0.subject == "Дюна" }?.rating)
        XCTAssertEqual(1, shelf.films.first { $0.subject == "Брат" }?.times)
        XCTAssertEqual(1, shelf.books.count)
        XCTAssertEqual(1, shelf.books.first?.times)
        XCTAssertEqual(BookStatus.done.id, shelf.books.first?.status)
        // Повторное восстановление (аккаунт перечитан после смены имени) не удваивает журнал
        let vm = AppModel(demoReplies: false)
        vm.publish("", checkIn: act("run").checkIn("Парк", 5))
        let snap = vm.snapshot()
        vm.restore(snap)
        vm.restore(snap)
        XCTAssertEqual(snap.growth, vm.state.growth)
    }

    func testNewFieldsSurviveSaving() throws {
        let vm = AppModel(demoReplies: false)
        vm.planOnShelf("book", "Лавр")
        vm.dismissRecommendation("film:брат")
        _ = vm.invite(.app)
        let data = try JSONEncoder().encode(vm.snapshot())
        let snapshot = try JSONDecoder().decode(Snapshot.self, from: data)
        let other = AppModel(demoReplies: false)
        other.restore(snapshot)
        XCTAssertEqual(vm.state.shelfMarks, other.state.shelfMarks)
        XCTAssertEqual(["film:брат"], other.state.dismissedRecs)
        XCTAssertEqual(1, growthStats(other.state.growth).invitesSent)
    }
}
