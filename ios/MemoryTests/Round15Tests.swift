import XCTest
@testable import Memory

/// Витрина, сведения и оформление групп. Те же проверки, что Round15Test
/// на Android, и пара своих: чтение старых сохранений и «назад».
final class Round15Tests: XCTestCase {

    override func setUp() {
        super.setUp()
        L10n.shared.setLanguage(.ru)
    }

    private func makeModel() -> AppModel {
        AppModel(clock: { Date(timeIntervalSince1970: 1_790_000_000) }, demoReplies: false)
    }

    private func ownGroup(_ model: AppModel) throws -> Int {
        XCTAssertNil(model.createGroup(name: "Моя мастерская", description: "Свечи ручной работы", topic: "Рукоделие"))
        return try XCTUnwrap(model.state.groups.first { $0.mine }).id
    }

    private func group(_ model: AppModel, _ id: Int) throws -> MemoryGroup {
        try XCTUnwrap(model.state.groups.first { $0.id == id })
    }

    func testPricesAndLinks() {
        XCTAssertEqual(priceTitle(1290), "1\u{00A0}290\u{00A0}₽")
        XCTAssertEqual(priceTitle(0), "Цена по запросу")
        XCTAssertEqual(parsePrice(" 1 290 ₽"), 1290)
        XCTAssertEqual(parsePrice("1290,50"), 1290)
        XCTAssertEqual(parsePrice(""), 0)
        XCTAssertNil(parsePrice("дорого"))
        XCTAssertEqual(normalizeUrl(" shop.ru "), "https://shop.ru")
        XCTAssertEqual(normalizeUrl("http://a.b"), "http://a.b")
        XCTAssertEqual(normalizeUrl("  "), "")
    }

    func testOwnerManagesTheShop() throws {
        let vm = makeModel()
        let id = try ownGroup(vm)
        XCTAssertEqual(vm.saveProduct(id, Product(id: 0, title: "  ")), "Добавьте название товара")
        XCTAssertNil(vm.saveProduct(id, Product(id: 0, title: "Свеча", price: 500, category: "Свечи", url: "shop.ru/svecha")))
        XCTAssertNil(vm.saveProduct(id, Product(id: 0, title: "Подставка", price: 300, category: "Декор")))
        var shop = try group(vm, id).shop
        XCTAssertEqual(shop.map(\.title), ["Подставка", "Свеча"])
        XCTAssertEqual(shop.last?.url, "https://shop.ru/svecha")
        XCTAssertEqual(shop.categories(), ["Декор", "Свечи"])
        let candle = try XCTUnwrap(shop.last)
        vm.moveProduct(id, candle.id, -1)
        shop = try group(vm, id).shop
        XCTAssertEqual(shop.first?.title, "Свеча")
        var changed = try XCTUnwrap(shop.first)
        changed.price = 650
        changed.inStock = false
        XCTAssertNil(vm.saveProduct(id, changed))
        XCTAssertEqual(vm.findProduct(id, changed.id)?.price, 650)
        XCTAssertEqual(vm.findProduct(id, changed.id)?.inStock, false)
        vm.deleteProduct(id, changed.id)
        XCTAssertEqual(try group(vm, id).shop.count, 1)
    }

    func testOthersCannotEditSomeoneElsesShop() throws {
        let vm = makeModel()
        let demo = try XCTUnwrap(vm.state.groups.first { !$0.shop.isEmpty && !$0.mine })
        vm.saveProduct(demo.id, Product(id: 0, title: "Чужой"))
        vm.setGroupAccent(demo.id, .coral)
        XCTAssertEqual(try group(vm, demo.id).shop.count, demo.shop.count)
        XCTAssertEqual(try group(vm, demo.id).accent, demo.accent)
    }

    func testInfoAndLook() throws {
        let vm = makeModel()
        let id = try ownGroup(vm)
        vm.updateGroupInfo(id, GroupInfo(about: "  Свечи  ", website: "candles.ru", phone: "+7 900"))
        let info = try group(vm, id).info
        XCTAssertEqual(info.about, "Свечи")
        XCTAssertEqual(info.website, "https://candles.ru")
        vm.setGroupAccent(id, .gold)
        vm.setGroupBackdrop(id, Backdrop(style: BackdropStyle.aurora.id))
        let g = try group(vm, id)
        XCTAssertEqual(g.accentColor, .gold)
        XCTAssertEqual(g.backdrop?.kind, .aurora)
        vm.setGroupBackdrop(id, nil)
        XCTAssertNil(try group(vm, id).backdrop)
    }

    func testAskingTheSellerSendsTheProduct() throws {
        let vm = makeModel()
        let demo = try XCTUnwrap(vm.state.groups.first { !$0.shop.isEmpty && !$0.mine })
        let product = try XCTUnwrap(demo.shop.first)
        let chatId = try XCTUnwrap(vm.askSeller(demo.id, product.id))
        let chat = try XCTUnwrap(vm.state.chats.first { $0.id == chatId })
        XCTAssertEqual(chat.peer.id, demo.ownerId)
        let msg = try XCTUnwrap(chat.messages.last)
        XCTAssertEqual(msg.product?.productId, product.id)
        XCTAssertTrue(msg.text.contains(product.title))
        XCTAssertEqual(vm.state.openChatId, chatId)
        let bare = Message(id: msg.id, fromMe: msg.fromMe, text: "", time: msg.time, product: msg.product)
        XCTAssertTrue(previewOf(bare).hasPrefix("Товар"))
        // Из сообщения — к товару в группе
        vm.openProduct(demo.id, product.id)
        XCTAssertEqual(vm.state.openProduct, ProductKey(groupId: demo.id, productId: product.id))
        XCTAssertEqual(vm.state.openGroupId, demo.id)
        XCTAssertNil(vm.state.openChatId)
        // В своей группе писать некому
        let own = try ownGroup(vm)
        vm.saveProduct(own, Product(id: 0, title: "Свеча", price: 500))
        let mine = try XCTUnwrap(try group(vm, own).shop.first)
        XCTAssertNil(vm.askSeller(own, mine.id))
    }

    // MARK: - Свои проверки

    func testBackClosesTheProductFirstAndForwardKeepsIt() throws {
        let vm = makeModel()
        let demo = try XCTUnwrap(vm.state.groups.first { !$0.shop.isEmpty && !$0.mine })
        let product = try XCTUnwrap(demo.shop.first)
        let chatId = try XCTUnwrap(vm.askSeller(demo.id, product.id))
        let msg = try XCTUnwrap(vm.state.chats.first { $0.id == chatId }?.messages.last)
        // Переслали — товар остался в сообщении
        let target = try XCTUnwrap(vm.state.chats.first { $0.id != chatId })
        vm.forward(chatId, msg.id, to: target.id)
        XCTAssertEqual(vm.state.chats.first { $0.id == target.id }?.messages.last?.product, msg.product)
        // «Назад» сначала закрывает товар, потом группу
        vm.openProduct(demo.id, product.id)
        XCTAssertTrue(vm.back())
        XCTAssertNil(vm.state.openProduct)
        XCTAssertEqual(vm.state.openGroupId, demo.id)
        XCTAssertTrue(vm.back())
        XCTAssertNil(vm.state.openGroupId)
    }

    func testShopSurvivesSnapshotAndOldGroupsGetTheDemoShop() throws {
        let vm = makeModel()
        let id = try ownGroup(vm)
        vm.saveProduct(id, Product(id: 0, title: "Свеча", price: 500, category: "Свечи"))
        vm.updateGroupInfo(id, GroupInfo(about: "Свечи", hours: "Пн–Пт"))
        vm.setGroupAccent(id, .mint)
        let data = try JSONEncoder().encode(vm.snapshot())
        let decoded = try JSONDecoder().decode(Snapshot.self, from: data)
        let vm2 = makeModel()
        vm2.restore(decoded)
        let g = try group(vm2, id)
        XCTAssertEqual(g.shop.map(\.title), ["Свеча"])
        XCTAssertEqual(g.info.hours, "Пн–Пт")
        XCTAssertEqual(g.accentColor, .mint)

        // Старое сохранение: у групп нет витрины и владельца — демо-группа получает их из демо-данных
        let old = #"{"id": 4, "name": "Свет и стекло", "description": "", "topic": "Искусство", "members": 870, "seed": 64, "joined": false, "mine": false, "posts": []}"#
        let oldGroup = try JSONDecoder().decode(MemoryGroup.self, from: Data(old.utf8))
        XCTAssertEqual(oldGroup.ownerId, 0)
        XCTAssertTrue(oldGroup.shop.isEmpty)
        XCTAssertEqual(oldGroup.accent, "")
        var snap = vm.snapshot()
        snap.groups = [oldGroup]
        let vm3 = makeModel()
        vm3.restore(snap)
        let restored = try group(vm3, 4)
        XCTAssertEqual(restored.ownerId, 4)
        XCTAssertFalse(restored.shop.isEmpty)
        XCTAssertEqual(restored.accentColor, .sky)
    }

    func testOldMessagesReadWithoutProduct() throws {
        let json = #"{"id": 1, "fromMe": true, "text": "Привет", "time": "12:00", "attachments": []}"#
        let msg = try JSONDecoder().decode(Message.self, from: Data(json.utf8))
        XCTAssertNil(msg.product)
    }
}
