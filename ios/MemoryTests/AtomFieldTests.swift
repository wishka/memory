import XCTest
@testable import Memory

/// Правила заставки: распад с конца слова, сборка — с начала. Те же
/// проверки, что AtomFieldTest на Android.
final class AtomFieldTests: XCTestCase {
    // Атом 0 — у левого края слова (буква M), атом 1 — у правого (Y), атом 2 — посередине
    private let xs: [Double] = [100, 900, 500]
    private let ys: [Double] = [300, 300, 300]

    private lazy var dissolve = AtomField(xs: xs, ys: ys, mode: .dissolve)
    private lazy var assemble = AtomField(xs: xs, ys: ys, mode: .assemble)
    private lazy var center = AtomField(xs: xs, ys: ys, mode: .assembleFromCenter)
    private var t: AtomField.Timing { dissolve.timing }

    func testDissolveStartsFromLastLetter() {
        XCTAssertLessThan(dissolve.pivotAt(1), dissolve.pivotAt(2))
        XCTAssertLessThan(dissolve.pivotAt(2), dissolve.pivotAt(0))
        XCTAssertLessThanOrEqual(dissolve.pivotAt(1), t.appearMs + t.holdMs + t.jitterMs)
        XCTAssertGreaterThanOrEqual(dissolve.pivotAt(0), t.appearMs + t.holdMs + t.sweepMs)
    }

    func testDissolveAtomsStayUntilReleaseThenFlyUpAndFade() {
        let before = dissolve.pivotAt(0) - 1
        XCTAssertEqual(dissolve.x(0, before), 100, accuracy: 0.001)
        XCTAssertEqual(dissolve.y(0, before), 300, accuracy: 0.001)
        let mid = dissolve.pivotAt(1) + t.lifeMs / 2
        XCTAssertLessThan(dissolve.y(1, mid), 300, "атом поднимается")
        XCTAssertTrue((0.01...0.99).contains(dissolve.alpha(1, mid)))
    }

    func testDissolveWordAppearsThenEverythingVanishes() {
        XCTAssertEqual(dissolve.alpha(0, 0), 0, accuracy: 0.001)
        XCTAssertEqual(dissolve.alpha(0, t.appearMs + 10), 1, accuracy: 0.001)
        for i in 0..<dissolve.size {
            XCTAssertFalse(dissolve.alive(i, dissolve.totalMs))
        }
    }

    func testAssembleStartsFromFirstLetter() {
        XCTAssertLessThan(assemble.pivotAt(0), assemble.pivotAt(2))
        XCTAssertLessThan(assemble.pivotAt(2), assemble.pivotAt(1))
    }

    func testAssembleAtomsArriveAndStay() {
        // В начале атомов не видно — они ещё далеко
        for i in 0..<assemble.size {
            XCTAssertEqual(assemble.alpha(i, 0), 0, accuracy: 0.001)
        }
        // Подлетая, атом ещё не на месте и полупрозрачен
        let approach = assemble.pivotAt(0) - t.lifeMs / 2
        XCTAssertTrue((0.01...0.99).contains(assemble.alpha(0, approach)))
        XCTAssertTrue(assemble.x(0, approach) != 100 || assemble.y(0, approach) != 300)
        // Встав на место, атом остаётся там до конца заставки
        for time in [assemble.pivotAt(0), assemble.totalMs] {
            XCTAssertEqual(assemble.x(0, time), 100, accuracy: 0.001)
            XCTAssertEqual(assemble.y(0, time), 300, accuracy: 0.001)
        }
        XCTAssertEqual(assemble.alpha(0, assemble.pivotAt(0)), 1, accuracy: 0.001)
        // К концу слово целиком стало стеклом
        XCTAssertEqual(assemble.crystal(assemble.totalMs), 1, accuracy: 0.001)
        XCTAssertGreaterThan(assemble.totalMs, assemble.wordCompleteMs)
    }

    func testCenterAssemblyGoesOutwardBothWays() {
        // Середина собирается первой, края — позже
        XCTAssertLessThan(center.pivotAt(2), center.pivotAt(0))
        XCTAssertLessThan(center.pivotAt(2), center.pivotAt(1))
        // Атомы краёв прилетают со своей стороны: левый — слева, правый — справа
        let half = t.lifeMs / 2
        XCTAssertLessThan(center.x(0, center.pivotAt(0) - half), 100)
        XCTAssertGreaterThan(center.x(1, center.pivotAt(1) - half), 900)
    }

    func testCenterAssemblyTurnsIntoCrystalWithShimmer() {
        let done = center.wordCompleteMs
        XCTAssertEqual(center.crystal(done), 0, accuracy: 0.001)
        XCTAssertEqual(center.crystal(done + AtomField.crystallizeMs), 1, accuracy: 0.001)
        // Когда слово стало стеклом, атомов уже не видно
        XCTAssertEqual(center.alpha(0, done + AtomField.crystallizeMs), 0, accuracy: 0.001)
        XCTAssertNil(center.shimmer(done))
        let mid = center.shimmer(done + AtomField.crystallizeMs + AtomField.shimmerMs / 2)
        XCTAssertEqual(mid ?? -1, 0.5, accuracy: 0.01)
        XCTAssertGreaterThan(center.totalMs, done + AtomField.crystallizeMs + AtomField.shimmerMs)
    }

    func testEmptyFieldStillFinishes() {
        XCTAssertGreaterThan(AtomField(xs: [], ys: []).totalMs, 0)
        XCTAssertGreaterThan(AtomField(xs: [], ys: [], mode: .assemble).totalMs, 0)
    }

    /// Заставка короткая: около трёх секунд, как на Android.
    func testSplashLastsAboutThreeSeconds() {
        let field = AtomField(xs: [0, 500, 1000], ys: [0, 0, 0], mode: .assemble)
        XCTAssertLessThan(field.totalMs, 3_500)
        XCTAssertGreaterThan(field.totalMs, 2_000)
    }
}
