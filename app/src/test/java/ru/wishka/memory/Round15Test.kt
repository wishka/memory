package ru.wishka.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.wishka.memory.data.Backdrop
import ru.wishka.memory.data.BackdropStyle
import ru.wishka.memory.data.GroupAccent
import ru.wishka.memory.data.GroupInfo
import ru.wishka.memory.data.Product
import ru.wishka.memory.data.categories
import ru.wishka.memory.data.normalizeUrl
import ru.wishka.memory.data.parsePrice
import ru.wishka.memory.data.priceTitle
import ru.wishka.memory.i18n.L10n
import ru.wishka.memory.i18n.Lang
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.messagePreview

/** Витрина, сведения и оформление групп. */
class Round15Test {
    @Before
    fun russian() {
        L10n.choice = Lang.Ru
    }

    private fun vm() = AppViewModel(clock = { 1_790_000_000_000L }, demoReplies = false)

    private fun ownGroup(vm: AppViewModel): Int {
        assertNull(vm.createGroup("Моя мастерская", "Свечи ручной работы", "Рукоделие"))
        return vm.state.value.groups.first { it.mine }.id
    }

    @Test
    fun pricesAndLinks() {
        assertEquals("1 290 ₽", priceTitle(1290))
        assertEquals("Цена по запросу", priceTitle(0))
        assertEquals(1290L, parsePrice(" 1 290 ₽"))
        assertEquals(1290L, parsePrice("1290,50"))
        assertEquals(0L, parsePrice(""))
        assertNull(parsePrice("дорого"))
        assertEquals("https://shop.ru", normalizeUrl(" shop.ru "))
        assertEquals("http://a.b", normalizeUrl("http://a.b"))
        assertEquals("", normalizeUrl("  "))
    }

    @Test
    fun ownerManagesTheShop() {
        val vm = vm()
        val id = ownGroup(vm)
        assertEquals("Добавьте название товара", vm.saveProduct(id, Product(0, "  ")))
        assertNull(vm.saveProduct(id, Product(0, "Свеча", 500, category = "Свечи", url = "shop.ru/svecha")))
        assertNull(vm.saveProduct(id, Product(0, "Подставка", 300, category = "Декор")))
        var shop = vm.state.value.groups.first { it.id == id }.shop
        assertEquals(listOf("Подставка", "Свеча"), shop.map { it.title })
        assertEquals("https://shop.ru/svecha", shop.last().url)
        assertEquals(listOf("Декор", "Свечи"), shop.categories())
        vm.moveProduct(id, shop.last().id, -1)
        shop = vm.state.value.groups.first { it.id == id }.shop
        assertEquals("Свеча", shop.first().title)
        assertNull(vm.saveProduct(id, shop.first().copy(price = 650, inStock = false)))
        assertEquals(650L, vm.findProduct(id, shop.first().id)!!.price)
        vm.deleteProduct(id, shop.first().id)
        assertEquals(1, vm.state.value.groups.first { it.id == id }.shop.size)
    }

    @Test
    fun othersCannotEditSomeoneElsesShop() {
        val vm = vm()
        val demo = vm.state.value.groups.first { it.shop.isNotEmpty() && !it.mine }
        vm.saveProduct(demo.id, Product(0, "Чужой"))
        vm.setGroupAccent(demo.id, GroupAccent.Coral)
        assertEquals(demo.shop.size, vm.state.value.groups.first { it.id == demo.id }.shop.size)
        assertEquals(demo.accent, vm.state.value.groups.first { it.id == demo.id }.accent)
    }

    @Test
    fun infoAndLook() {
        val vm = vm()
        val id = ownGroup(vm)
        vm.updateGroupInfo(id, GroupInfo(about = "  Свечи  ", website = "candles.ru", phone = "+7 900"))
        val info = vm.state.value.groups.first { it.id == id }.info
        assertEquals("Свечи", info.about)
        assertEquals("https://candles.ru", info.website)
        vm.setGroupAccent(id, GroupAccent.Gold)
        vm.setGroupBackdrop(id, Backdrop(BackdropStyle.Aurora.id))
        val g = vm.state.value.groups.first { it.id == id }
        assertEquals(GroupAccent.Gold, g.accentColor)
        assertEquals(BackdropStyle.Aurora, g.backdrop!!.kind)
        vm.setGroupBackdrop(id, null)
        assertNull(vm.state.value.groups.first { it.id == id }.backdrop)
    }

    @Test
    fun askingTheSellerSendsTheProduct() {
        val vm = vm()
        val demo = vm.state.value.groups.first { it.shop.isNotEmpty() && !it.mine }
        val product = demo.shop.first()
        val chatId = vm.askSeller(demo.id, product.id)
        assertNotNull(chatId)
        val chat = vm.state.value.chats.first { it.id == chatId }
        assertEquals(demo.ownerId, chat.peer.id)
        val msg = chat.messages.last()
        assertEquals(product.id, msg.product!!.productId)
        assertTrue(msg.text.contains(product.title))
        assertEquals(chatId, vm.state.value.openChatId)
        assertTrue(messagePreview(msg.copy(text = "")).startsWith("Товар"))
        // Из сообщения — к товару в группе
        vm.openProduct(demo.id, product.id)
        assertEquals(demo.id to product.id, vm.state.value.openProduct)
        assertEquals(demo.id, vm.state.value.openGroupId)
        assertNull(vm.state.value.openChatId)
        // В своей группе писать некому
        val own = ownGroup(vm)
        vm.saveProduct(own, Product(0, "Свеча", 500))
        assertNull(vm.askSeller(own, vm.state.value.groups.first { it.id == own }.shop.first().id))
    }
}
