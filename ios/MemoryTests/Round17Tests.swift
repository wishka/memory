import XCTest
@testable import Memory

/// Memory+: тарифы, пробный период, смена тарифа и что закрыто без
/// подписки. Те же проверки, что Round17Test на Android, и пара своих.
final class Round17Tests: XCTestCase {

    /// Часы модели: тест двигает время вперёд.
    private var now = Date(timeIntervalSince1970: 1_790_000_000)

    override func setUp() {
        super.setUp()
        L10n.shared.setLanguage(.ru)
        // Memory+ пока скрыт; здесь проверяем, как он работает включённым
        Premium.enabled = true
    }

    override func tearDown() {
        Premium.enabled = false
        L10n.shared.setLanguage(.ru)
        super.tearDown()
    }

    private func makeModel() -> AppModel {
        AppModel(clock: { [unowned self] in self.now }, demoReplies: false)
    }

    private func later(days: Double, plusMs: Double = 0) {
        now = now.addingTimeInterval(days * daySeconds + plusMs / 1000)
    }

    func testPricesAndTiers() {
        XCTAssertEqual(199, Premium.price(.plus, .month))
        XCTAssertEqual(1490, Premium.price(.plus, .year))
        XCTAssertEqual(490, Premium.price(.business, .month))
        XCTAssertEqual(3990, Premium.price(.business, .year))
        XCTAssertEqual(38, Premium.yearSaving(.plus))
        XCTAssertEqual(32, Premium.yearSaving(.business))
        XCTAssertEqual("199\u{00A0}₽/мес", Premium.priceTitle(.plus, .month))
        XCTAssertEqual("1\u{00A0}490\u{00A0}₽/год", Premium.priceTitle(.plus, .year))
        XCTAssertEqual("memory_business_year", Premium.productId(.business, .year))
        XCTAssertTrue(PlanTier.business.covers(.plus))
        XCTAssertFalse(PlanTier.plus.covers(.business))
        // У каждой функции есть тариф, Бизнес — только для продавцов
        XCTAssertEqual(
            Set<PremiumFeature>([.shopUnlimited, .groupBackdrop, .groupContacts]),
            Set(Premium.features(.business))
        )
    }

    func testTrialOnceThreeDays() {
        let vm = makeModel()
        XCTAssertEqual(PlanTier.free, vm.state.plan)
        XCTAssertNil(vm.startTrial(.plus))
        XCTAssertEqual(PlanTier.plus, vm.state.plan)
        XCTAssertTrue(vm.state.subscription.trial)
        XCTAssertEqual(3, vm.state.subscription.daysLeft(now))
        // Второй раз — нельзя, даже на другой тариф
        XCTAssertEqual("Пробный период уже использован", vm.startTrial(.business))
        // Через три дня — бесплатный тариф, пробный не продлевается
        later(days: 3, plusMs: 1)
        vm.refreshPlan()
        XCTAssertEqual(PlanTier.free, vm.state.plan)
        XCTAssertTrue(vm.state.subscription.trialUsed)
        XCTAssertFalse(vm.has(.backdrops))
    }

    func testSubscribeSwitchCancel() {
        let vm = makeModel()
        vm.startTrial(.plus)
        // Оформление во время пробного — пробный заканчивается
        vm.subscribe(.plus, .month)
        var sub = vm.state.subscription
        XCTAssertFalse(sub.trial)
        XCTAssertTrue(sub.autoRenew)
        XCTAssertTrue(sub.trialUsed)
        XCTAssertEqual(now.addingTimeInterval(30 * daySeconds), sub.until)
        // Смена тарифа — сразу, срок заново
        later(days: 10)
        vm.subscribe(.business, .year)
        sub = vm.state.subscription
        XCTAssertEqual(PlanTier.business, vm.state.plan)
        XCTAssertEqual(now.addingTimeInterval(365 * daySeconds), sub.until)
        // Отмена продления — работает до конца срока
        vm.cancelRenewal()
        XCTAssertFalse(vm.state.subscription.autoRenew)
        later(days: 364)
        vm.refreshPlan()
        XCTAssertEqual(PlanTier.business, vm.state.plan)
        vm.resumeRenewal()
        XCTAssertTrue(vm.state.subscription.autoRenew)
        // С продлением — продлевается (в демо сам)
        later(days: 2)
        vm.refreshPlan()
        XCTAssertEqual(PlanTier.business, vm.state.plan)
        // Без продления — заканчивается
        vm.cancelRenewal()
        later(days: 366)
        vm.refreshPlan()
        XCTAssertEqual(PlanTier.free, vm.state.plan)
        // Отменённую и закончившуюся не возобновить
        vm.resumeRenewal()
        XCTAssertFalse(vm.state.subscription.autoRenew)
    }

    func testPaywallAndLimits() throws {
        let vm = makeModel()
        // Фоны: четыре бесплатно
        vm.setBackdrop(Backdrop(style: BackdropStyle.aurora.id))
        XCTAssertEqual(BackdropStyle.aurora, vm.state.backdrop.kind)
        vm.setBackdrop(Backdrop(style: BackdropStyle.prism.id))
        XCTAssertEqual(BackdropStyle.aurora, vm.state.backdrop.kind)
        XCTAssertEqual(PremiumFeature.backdrops, vm.state.paywall)
        vm.closePaywall()
        XCTAssertNil(vm.state.paywall)
        // Плейлисты: три бесплатно
        for i in 0..<3 { XCTAssertNotNil(vm.createPlaylist("Плейлист \(i)")) }
        XCTAssertNil(vm.createPlaylist("Четвёртый"))
        XCTAssertEqual(PremiumFeature.playlists, vm.state.paywall)
        // Коллекции стикеров: две
        XCTAssertNotNil(vm.createStickerPack("Раз"))
        XCTAssertNotNil(vm.createStickerPack("Два"))
        XCTAssertNil(vm.createStickerPack("Три"))
        // Витрина профиля: три записи бесплатно, шесть в Memory+
        for i in 0..<7 { vm.publish("Момент \(i)") }
        let mine = vm.state.myPosts
        mine.prefix(4).forEach { vm.toggleProfilePin($0.id) }
        XCTAssertEqual(Premium.freePins, vm.state.profilePins.count)
        XCTAssertEqual(PremiumFeature.pins, vm.state.paywall)
        // Итоги: дальше прошлого периода — Memory+
        vm.setRecapBack(1)
        XCTAssertEqual(1, vm.state.recapBack)
        vm.setRecapBack(2)
        XCTAssertEqual(1, vm.state.recapBack)
        // Шторка ведёт на экран тарифов
        vm.openPremium()
        XCTAssertNil(vm.state.paywall)
        XCTAssertEqual(AppTab.profile, vm.state.tab)
        XCTAssertEqual(ProfileSection.premium, vm.state.profileSection)
        // С Memory+ — всё открыто
        vm.startTrial(.plus)
        XCTAssertNotNil(vm.createPlaylist("Четвёртый"))
        XCTAssertNotNil(vm.createStickerPack("Три"))
        mine.dropFirst(3).forEach { vm.toggleProfilePin($0.id) }
        XCTAssertEqual(Premium.plusPins, vm.state.profilePins.count)
        vm.setRecapBack(5)
        XCTAssertEqual(5, vm.state.recapBack)
        vm.setBackdrop(Backdrop(style: BackdropStyle.prism.id))
        XCTAssertEqual(BackdropStyle.prism, vm.state.backdrop.kind)
        // Закончился — созданное остаётся
        later(days: 4)
        vm.refreshPlan()
        XCTAssertEqual(4, vm.state.playlists.count)
        XCTAssertEqual(BackdropStyle.prism, vm.state.backdrop.kind)
        XCTAssertEqual(Premium.plusPins, vm.state.profilePins.count)
        // Тот же премиум-фон: менять затемнение можно и без подписки, другой премиум-фон — нет
        vm.setBackdrop(Backdrop(style: BackdropStyle.prism.id, photo: "", dim: 0.6))
        XCTAssertEqual(0.6, vm.state.backdrop.dim, accuracy: 0.0001)
        vm.setBackdrop(Backdrop(style: BackdropStyle.crystals.id))
        XCTAssertEqual(BackdropStyle.prism, vm.state.backdrop.kind)
    }

    func testBusinessForSellers() throws {
        let vm = makeModel()
        XCTAssertNil(vm.createGroup(name: "Мастерская", description: "Свечи", topic: "Рукоделие"))
        let id = try XCTUnwrap(vm.state.groups.first { $0.mine }).id
        for i in 0..<Premium.freeProducts { XCTAssertNil(vm.saveProduct(id, Product(id: 0, title: "Свеча \(i)", price: 300))) }
        XCTAssertNotNil(vm.saveProduct(id, Product(id: 0, title: "Одиннадцатая", price: 300)))
        XCTAssertEqual(PremiumFeature.shopUnlimited, vm.state.paywall)
        // Изменить уже добавленный — можно
        var first = try XCTUnwrap(vm.state.groups.first { $0.id == id }?.shop.first)
        first.price = 350
        XCTAssertNil(vm.saveProduct(id, first))
        // Контакты и фон — Бизнес; описание и сайт — бесплатно
        vm.updateGroupInfo(id, GroupInfo(about: "Свечи", website: "candles.ru", phone: "+7 900"))
        var info = try XCTUnwrap(vm.state.groups.first { $0.id == id }).info
        XCTAssertEqual("Свечи", info.about)
        XCTAssertEqual("", info.phone)
        XCTAssertEqual(PremiumFeature.groupContacts, vm.state.paywall)
        vm.setGroupBackdrop(id, Backdrop(style: BackdropStyle.aurora.id))
        XCTAssertNil(try XCTUnwrap(vm.state.groups.first { $0.id == id }).backdrop)
        // Memory+ — мало: это Бизнес
        vm.startTrial(.plus)
        XCTAssertNotNil(vm.saveProduct(id, Product(id: 0, title: "Одиннадцатая", price: 300)))
        vm.subscribe(.business, .month)
        XCTAssertNil(vm.saveProduct(id, Product(id: 0, title: "Одиннадцатая", price: 300)))
        vm.updateGroupInfo(id, GroupInfo(about: "Свечи", phone: "+7 900"))
        info = try XCTUnwrap(vm.state.groups.first { $0.id == id }).info
        XCTAssertEqual("+7 900", info.phone)
        vm.setGroupBackdrop(id, Backdrop(style: BackdropStyle.aurora.id))
        XCTAssertNotNil(try XCTUnwrap(vm.state.groups.first { $0.id == id }).backdrop)
        // Бизнес включает Memory+
        XCTAssertTrue(vm.has(.backdrops))
    }

    func testSubscriptionSurvivesRestart() {
        let vm = makeModel()
        vm.subscribe(.plus, .year)
        let again = makeModel()
        again.restore(vm.snapshot())
        XCTAssertEqual(PlanTier.plus, again.state.plan)
        XCTAssertEqual(vm.state.subscription, again.state.subscription)
        // Пустое сохранение — бесплатный тариф
        var old = vm.snapshot()
        old.subscription = nil
        let fresh = makeModel()
        fresh.restore(old)
        XCTAssertEqual(MemorySubscription(), fresh.state.subscription)
        XCTAssertEqual(PlanTier.free, fresh.state.plan)
    }

    // MARK: - Свои проверки

    func testSubscriptionCodableAndOldFiles() throws {
        let sub = MemorySubscription(tier: .business, period: .year, until: now.addingTimeInterval(daySeconds), trial: false, autoRenew: true, trialUsed: true)
        let decoded = try JSONDecoder().decode(MemorySubscription.self, from: JSONEncoder().encode(sub))
        XCTAssertEqual(decoded.tier, .business)
        XCTAssertEqual(decoded.period, .year)
        XCTAssertEqual(decoded.until.timeIntervalSince1970, sub.until.timeIntervalSince1970, accuracy: 0.001)
        XCTAssertTrue(decoded.autoRenew)
        XCTAssertTrue(decoded.trialUsed)
        // Незнакомый тариф и пустой объект — бесплатно, файл читается
        let odd = try JSONDecoder().decode(MemorySubscription.self, from: Data(#"{"tier":"gold","period":"week"}"#.utf8))
        XCTAssertEqual(odd.tier, .free)
        XCTAssertEqual(odd.period, .month)
        XCTAssertEqual(try JSONDecoder().decode(MemorySubscription.self, from: Data("{}".utf8)), MemorySubscription())
        // Сохранение без подписки (старый файл) читается
        let vm = makeModel()
        var json = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(vm.snapshot())) as? [String: Any])
        json.removeValue(forKey: "subscription")
        let snapshot = try JSONDecoder().decode(Snapshot.self, from: JSONSerialization.data(withJSONObject: json))
        XCTAssertNil(snapshot.subscription)
    }

    func testDaysLeftAndStatus() {
        let vm = makeModel()
        vm.startTrial(.plus)
        let sub = vm.state.subscription
        XCTAssertEqual(subscriptionStatus(sub, vm.state.plan, now: now), "Memory+ · пробный, ещё 3 дня")
        later(days: 0.9)
        XCTAssertEqual(3, sub.daysLeft(now))
        later(days: 0.2)
        XCTAssertEqual(2, sub.daysLeft(now))
        later(days: 2)
        vm.refreshPlan()
        XCTAssertEqual(0, vm.state.subscription.daysLeft(now))
        XCTAssertEqual(subscriptionStatus(vm.state.subscription, vm.state.plan, now: now), "Пробный период закончился")
        XCTAssertEqual(subscriptionStatus(MemorySubscription(), .free, now: now), "Бесплатный тариф")
        // Гид ведёт на экран тарифов
        vm.guideGo(.premium)
        XCTAssertEqual(ProfileSection.premium, vm.state.profileSection)
        XCTAssertEqual(GuideAction.premium.title, "Memory+")
    }

    func testEnglishPriceAndSaving() {
        L10n.shared.setLanguage(.en)
        XCTAssertEqual("199\u{00A0}₽/mo", Premium.priceTitle(.plus, .month))
        // В строке после %s остаётся знак процента
        XCTAssertEqual(tf("%s · выгоднее до %s%", BillingPeriod.year.title, Premium.yearSaving(.plus)).hasSuffix("38%"), true)
    }
}
