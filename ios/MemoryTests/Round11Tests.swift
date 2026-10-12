import CoreGraphics
import XCTest
@testable import Memory

/// Вкладка «Вместе»: цели, банк времени, клады, вылазки, рисунок, обмен.
/// Те же проверки, что Round11Test на Android.
final class Round11Tests: XCTestCase {

    override func setUp() {
        super.setUp()
        L10n.shared.setLanguage(.ru)
    }

    private let demo = TogetherDemo.initial(now: Date(timeIntervalSince1970: 0))
    private let stroke = [DrawStroke(points: [CGPoint(x: 0.1, y: 0.1), CGPoint(x: 0.5, y: 0.5)], color: 0, width: 0.02)]

    private func activity(_ id: String) -> CheckInActivity {
        checkInActivities.first { $0.id == id }!
    }

    func testCheckInsMoveJoinedGoalsOnly() throws {
        let run = activity("run").checkIn("Парк", 5)
        let km = try XCTUnwrap(demo.goals.first { $0.id == "km" })
        // Не участвуете — шкала стоит
        XCTAssertEqual(km.total, demo.afterCheckIn(run).goals.first { $0.id == "km" }?.total ?? -1, accuracy: 0)
        let joined = try XCTUnwrap(demo.joinGoal("km").afterCheckIn(run).goals.first { $0.id == "km" })
        XCTAssertEqual(km.total + 5, joined.total, accuracy: 0.001)
        XCTAssertEqual(5, joined.mine, accuracy: 0.001)
        XCTAssertEqual(km.participants + 1, joined.participants)
        let yoga = activity("yoga").checkIn("Утро", 90)
        XCTAssertEqual(1.5, demo.joinGoal("hours").afterCheckIn(yoga).goals.first { $0.id == "hours" }?.mine ?? -1, accuracy: 0.001)
        XCTAssertEqual(demo, demo.contribute("trees", -3))
    }

    func testTimeBankBalance() throws {
        let s = TogetherState(balance: 30)
        XCTAssertNil(s.propose(id: 1, personId: 2, give: "", get: "", minutes: 30, now: nil))
        // Урок «за часы» дороже баланса — нельзя
        XCTAssertNil(s.propose(id: 1, personId: 2, give: "", get: "Гитара", minutes: 60, now: nil))
        let learn = try XCTUnwrap(s.propose(id: 1, personId: 2, give: "", get: "Гитара", minutes: 30, now: nil))
        XCTAssertEqual(learn.deals.count, 1)
        XCTAssertEqual(learn.deals.first?.status, .proposed)
        XCTAssertEqual(0, learn.setDeal(1, .done).balance)
        // Повторное «прошло» не списывает второй раз
        XCTAssertEqual(0, learn.setDeal(1, .done).setDeal(1, .done).balance)
        let teach = try XCTUnwrap(s.propose(id: 2, personId: 3, give: "Фото", get: "", minutes: 45, now: nil)).setDeal(2, .done)
        XCTAssertEqual(75, teach.balance)
        // Обмен 1:1 баланс не трогает
        XCTAssertEqual(30, try XCTUnwrap(s.propose(id: 3, personId: 4, give: "Фото", get: "Гитара", minutes: 30, now: nil)).setDeal(3, .done).balance)
        // Обещанные уроки «за часы» уже заняли баланс
        XCTAssertNil(learn.propose(id: 4, personId: 5, give: "", get: "Йога", minutes: 30, now: nil))
        // Отменённое — окончательно: ни нового статуса, ни списания
        let declined = learn.setDeal(1, .declined)
        XCTAssertEqual(.declined, declined.setDeal(1, .done).deals.first?.status)
        XCTAssertEqual(30, declined.setDeal(1, .done).balance)
        XCTAssertNotNil(declined.propose(id: 4, personId: 5, give: "", get: "Йога", minutes: 30, now: nil))
        XCTAssertEqual("1 ч 30 мин", minutesTitle(90))
        XCTAssertEqual("−45 мин", minutesTitle(-45))
    }

    func testSkillMatching() {
        let offer = SkillOffer(personId: 3, teaches: ["Гитара"], wants: ["Фотография"])
        XCTAssertEqual(2, matchScore(offer, ["фотография"], ["гитара"]))
        XCTAssertEqual(1, matchScore(offer, [], ["Гитара"]))
        XCTAssertEqual(0, matchScore(offer, ["Йога"], ["Танцы"]))
        XCTAssertEqual(3, demo.matches().first?.offer.personId)
        XCTAssertEqual(demo.myTeach, demo.addSkill(teach: true, " фотография ").myTeach)
    }

    func testCacheNeedsToBeNearby() throws {
        let c = GeoCache(id: 1, authorId: 5, title: "Клад", hint: "Под камнем", at: .moscow, difficulty: 2, size: "Микро")
        let s = TogetherState(caches: [c])
        let log = CacheLog(id: 10, authorId: 1, text: "Нашёл!")
        XCTAssertNil(s.findCache(1, log, here: nil))
        XCTAssertNil(s.findCache(1, log, here: LatLon.moscow.offset(east: 400, north: 0)))
        let found = try XCTUnwrap(s.findCache(1, log, here: LatLon.moscow.offset(east: 60, north: 40)))
        XCTAssertTrue(found.caches.first?.foundByMe ?? false)
        XCTAssertNil(found.findCache(1, CacheLog(id: 11, authorId: 1, text: "Нашёл!"), here: .moscow))
        // Свой клад не «находят»
        XCTAssertNil(s.findCache(1, CacheLog(id: 10, authorId: 5, text: "Нашёл!"), here: .moscow))
        XCTAssertEqual(1000, LatLon.moscow.distance(to: LatLon.moscow.offset(east: 0, north: 1000)), accuracy: 5)
    }

    func testOutingsHaveCapacity() throws {
        let o = Outing(id: 1, organizerId: 2, emoji: "🍄", title: "Грибы", description: "", place: "Метро",
                       startsAt: Date(timeIntervalSince1970: 0.1), capacity: 50, participants: [2])
        let s = try XCTUnwrap(TogetherState().createOuting(o))
        XCTAssertEqual(Outing.maxPeople, s.outings.first?.capacity)
        var blank = o
        blank.place = " "
        XCTAssertNil(TogetherState().createOuting(blank))
        var two = o
        two.capacity = 2
        let small = try XCTUnwrap(TogetherState().createOuting(two))
        let joined = small.toggleOuting(1, me: 1)
        XCTAssertEqual([2, 1], joined.outings.first?.participants)
        XCTAssertEqual(joined, joined.toggleOuting(1, me: 3))
        XCTAssertEqual([2], joined.toggleOuting(1, me: 1).outings.first?.participants)
        // Организатор не выходит, а отменяет
        XCTAssertEqual(joined, joined.toggleOuting(1, me: 2))
        XCTAssertTrue(joined.cancelOuting(1, me: 2).outings.isEmpty)
        XCTAssertEqual(1, joined.cancelOuting(1, me: 1).outings.count)
    }

    func testDrawingPassesAround() throws {
        let s = try XCTUnwrap(TogetherState().startDrawing(id: 1, title: "Город", turns: 2, me: 1, now: nil))
        XCTAssertEqual(1, s.drawings.first?.waitingFor)
        // Не ваш ход и пустой ход — не считаются
        XCTAssertEqual(s, s.drawTurn(1, me: 2, strokes: stroke, passTo: 3))
        XCTAssertEqual(s, s.drawTurn(1, me: 1, strokes: [], passTo: 3))
        let passed = s.drawTurn(1, me: 1, strokes: stroke, passTo: 4)
        XCTAssertEqual(4, passed.drawings.first?.waitingFor)
        let done = try XCTUnwrap(passed.drawTurn(1, me: 4, strokes: stroke, passTo: 1).drawings.first)
        XCTAssertTrue(done.finished)
        XCTAssertNil(done.waitingFor)
        XCTAssertEqual(2, done.strokes.count)
    }

    func testBooksTravelSeedsAreGiven() throws {
        let book = try XCTUnwrap(demo.shares.first { $0.id == 9802 })
        let asked = demo.requestShare(book.id, me: 1)
        XCTAssertEqual(.requested, asked.shares.first { $0.id == book.id }?.status)
        let moved = try XCTUnwrap(asked.handOver(book.id, to: 1).shares.first { $0.id == book.id })
        XCTAssertEqual(1, moved.holderId)
        XCTAssertEqual([2], moved.history)
        XCTAssertEqual(.available, moved.status)
        XCTAssertNil(moved.requestedBy)
        let seeds = try XCTUnwrap(demo.handOver(9803, to: 1).shares.first { $0.id == 9803 })
        XCTAssertEqual(.given, seeds.status)
        let repair = try XCTUnwrap(demo.offerHelp(9805, me: 1).offerHelp(9805, me: 1).shares.first { $0.id == 9805 })
        XCTAssertEqual([1], repair.helpers)
        XCTAssertEqual(.fixed, demo.markFixed(9805).shares.first { $0.id == 9805 }?.status)
        // Свою вещь не просят
        XCTAssertEqual(demo, demo.requestShare(9803, me: 5))
        XCTAssertEqual(.repair, repair.kind)
    }

    func testDemoPointsMoveToYouOnce() throws {
        let here = LatLon(lat: 59.9343, lon: 30.3351)
        let moved = demo.relocate(here, meId: 1)
        let before = try XCTUnwrap(demo.caches.first).at.distance(to: demo.anchor)
        XCTAssertEqual(before, try XCTUnwrap(moved.caches.first).at.distance(to: here), accuracy: 50)
        XCTAssertTrue(moved.anchored)
        let again = moved.relocate(.moscow, meId: 1)
        XCTAssertEqual(moved.caches, again.caches)
        XCTAssertEqual(LatLon.moscow, again.anchor)
    }

    func testModelTogether() throws {
        let vm = AppModel(demoReplies: false)
        vm.selectTab(.together)
        vm.selectTogetherSection(.sharing)
        XCTAssertEqual(.sharing, vm.state.togetherSection)
        vm.joinGoal("km")
        vm.publish("", checkIn: activity("run").checkIn("Парк", 3))
        XCTAssertEqual(3, vm.state.together.goals.first { $0.id == "km" }?.mine ?? -1, accuracy: 0.001)
        XCTAssertNil(vm.proposeDeal(3, give: "Фотография", get: "Гитара", minutes: 30))
        XCTAssertEqual(1, vm.state.together.deals.count)
        XCTAssertNotNil(vm.proposeDeal(3, give: "", get: "", minutes: 30))
        let id = try XCTUnwrap(vm.startDrawing(title: "Море", turns: 3))
        XCTAssertFalse(vm.state.together.drawings.first { $0.id == id }?.finished ?? true)
    }

    func testTogetherNudgeOpensSection() throws {
        let vera = try XCTUnwrap(Demo.person(2))
        let item = try XCTUnwrap(demo.shares.first { $0.id == 9802 })
        let n = Nudges.shareRequested(vera, item)
        XCTAssertEqual(n.text, "Вера просит ««Задача трёх тел»»")
        let vm = AppModel(demoReplies: false)
        vm.openNudge(n)
        XCTAssertEqual(.together, vm.state.tab)
        XCTAssertEqual(.sharing, vm.state.togetherSection)
    }

    func testTogetherSurvivesSnapshot() throws {
        let vm = AppModel(demoReplies: false)
        vm.joinGoal("books")
        let data = try JSONEncoder().encode(vm.snapshot())
        let snapshot = try JSONDecoder().decode(Snapshot.self, from: data)
        let restored = AppModel(demoReplies: false)
        restored.restore(snapshot)
        XCTAssertEqual(true, restored.state.together.goals.first { $0.id == "books" }?.joined)
        XCTAssertEqual(vm.state.together.drawings, restored.state.together.drawings)
    }
}
