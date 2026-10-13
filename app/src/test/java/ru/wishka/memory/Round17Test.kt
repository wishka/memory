package ru.wishka.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.wishka.memory.data.Backdrop
import ru.wishka.memory.data.BackdropStyle
import ru.wishka.memory.data.BillingPeriod
import ru.wishka.memory.data.DAY_MS
import ru.wishka.memory.data.GroupInfo
import ru.wishka.memory.data.PlanTier
import ru.wishka.memory.data.Premium
import ru.wishka.memory.data.PremiumFeature
import ru.wishka.memory.data.Product
import ru.wishka.memory.data.Subscription
import ru.wishka.memory.i18n.L10n
import ru.wishka.memory.i18n.Lang
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.ProfileSection
import ru.wishka.memory.ui.Tab

/** Memory+: тарифы, пробный период, смена тарифа и что закрыто без подписки. */
class Round17Test {
    @Before
    fun russian() {
        L10n.choice = Lang.Ru
    }

    private var now = 1_790_000_000_000L
    private fun vm() = AppViewModel(clock = { now }, demoReplies = false)

    @Test
    fun pricesAndTiers() {
        assertEquals(199, Premium.price(PlanTier.Plus, BillingPeriod.Month))
        assertEquals(1490, Premium.price(PlanTier.Plus, BillingPeriod.Year))
        assertEquals(490, Premium.price(PlanTier.Business, BillingPeriod.Month))
        assertEquals(3990, Premium.price(PlanTier.Business, BillingPeriod.Year))
        assertEquals(38, Premium.yearSaving(PlanTier.Plus))
        assertEquals(32, Premium.yearSaving(PlanTier.Business))
        assertEquals("199\u00A0₽/мес", Premium.priceTitle(PlanTier.Plus, BillingPeriod.Month))
        assertEquals("1\u00A0490\u00A0₽/год", Premium.priceTitle(PlanTier.Plus, BillingPeriod.Year))
        assertEquals("memory_business_year", Premium.productId(PlanTier.Business, BillingPeriod.Year))
        assertTrue(PlanTier.Business.covers(PlanTier.Plus))
        assertFalse(PlanTier.Plus.covers(PlanTier.Business))
        // У каждой функции есть тариф, Бизнес — только для продавцов
        assertEquals(
            setOf(PremiumFeature.ShopUnlimited, PremiumFeature.GroupBackdrop, PremiumFeature.GroupContacts),
            Premium.features(PlanTier.Business).toSet(),
        )
    }

    @Test
    fun trialOnceThreeDays() {
        val vm = vm()
        assertEquals(PlanTier.Free, vm.state.value.plan)
        assertNull(vm.startTrial(PlanTier.Plus))
        var s = vm.state.value
        assertEquals(PlanTier.Plus, s.plan)
        assertTrue(s.subscription.trial)
        assertEquals(3, s.subscription.daysLeft(now))
        // Второй раз — нельзя, даже на другой тариф
        assertEquals("Пробный период уже использован", vm.startTrial(PlanTier.Business))
        // Через три дня — бесплатный тариф, пробный не продлевается
        now += 3 * DAY_MS + 1
        vm.refreshPlan()
        s = vm.state.value
        assertEquals(PlanTier.Free, s.plan)
        assertTrue(s.subscription.trialUsed)
        assertFalse(vm.has(PremiumFeature.Backdrops))
    }

    @Test
    fun subscribeSwitchCancel() {
        val vm = vm()
        vm.startTrial(PlanTier.Plus)
        // Оформление во время пробного — пробный заканчивается
        vm.subscribe(PlanTier.Plus, BillingPeriod.Month)
        var sub = vm.state.value.subscription
        assertFalse(sub.trial)
        assertTrue(sub.autoRenew)
        assertTrue(sub.trialUsed)
        assertEquals(now + 30 * DAY_MS, sub.until)
        // Смена тарифа — сразу, срок заново
        now += 10 * DAY_MS
        vm.subscribe(PlanTier.Business, BillingPeriod.Year)
        sub = vm.state.value.subscription
        assertEquals(PlanTier.Business, vm.state.value.plan)
        assertEquals(now + 365 * DAY_MS, sub.until)
        // Отмена продления — работает до конца срока
        vm.cancelRenewal()
        assertFalse(vm.state.value.subscription.autoRenew)
        now += 364 * DAY_MS
        vm.refreshPlan()
        assertEquals(PlanTier.Business, vm.state.value.plan)
        vm.resumeRenewal()
        assertTrue(vm.state.value.subscription.autoRenew)
        // С продлением — продлевается (в демо сам)
        now += 2 * DAY_MS
        vm.refreshPlan()
        assertEquals(PlanTier.Business, vm.state.value.plan)
        // Без продления — заканчивается
        vm.cancelRenewal()
        now += 366 * DAY_MS
        vm.refreshPlan()
        assertEquals(PlanTier.Free, vm.state.value.plan)
        // Отменённую и закончившуюся не возобновить
        vm.resumeRenewal()
        assertFalse(vm.state.value.subscription.autoRenew)
    }

    @Test
    fun paywallAndLimits() {
        val vm = vm()
        // Фоны: четыре бесплатно
        vm.setBackdrop(Backdrop(BackdropStyle.Aurora.id))
        assertEquals(BackdropStyle.Aurora, vm.state.value.backdrop.kind)
        vm.setBackdrop(Backdrop(BackdropStyle.Prism.id))
        assertEquals(BackdropStyle.Aurora, vm.state.value.backdrop.kind)
        assertEquals(PremiumFeature.Backdrops, vm.state.value.paywall)
        vm.closePaywall()
        assertNull(vm.state.value.paywall)
        // Плейлисты: три бесплатно
        repeat(3) { assertNotNull(vm.createPlaylist("Плейлист $it")) }
        assertNull(vm.createPlaylist("Четвёртый"))
        assertEquals(PremiumFeature.Playlists, vm.state.value.paywall)
        // Коллекции стикеров: две
        assertNotNull(vm.createStickerPack("Раз"))
        assertNotNull(vm.createStickerPack("Два"))
        assertNull(vm.createStickerPack("Три"))
        // Витрина профиля: три записи бесплатно, шесть в Memory+
        repeat(7) { vm.publish("Момент $it") }
        val mine = vm.state.value.myPosts
        mine.take(4).forEach { vm.toggleProfilePin(it.id) }
        assertEquals(Premium.FREE_PINS, vm.state.value.profilePins.size)
        assertEquals(PremiumFeature.Pins, vm.state.value.paywall)
        // Итоги: дальше прошлого периода — Memory+
        vm.setRecapBack(1)
        assertEquals(1, vm.state.value.recapBack)
        vm.setRecapBack(2)
        assertEquals(1, vm.state.value.recapBack)
        // Шторка ведёт на экран тарифов
        vm.openPremium()
        assertNull(vm.state.value.paywall)
        assertEquals(Tab.Profile, vm.state.value.tab)
        assertEquals(ProfileSection.Premium, vm.state.value.profileSection)
        // С Memory+ — всё открыто
        vm.startTrial(PlanTier.Plus)
        assertNotNull(vm.createPlaylist("Четвёртый"))
        assertNotNull(vm.createStickerPack("Три"))
        mine.drop(3).forEach { vm.toggleProfilePin(it.id) }
        assertEquals(Premium.PLUS_PINS, vm.state.value.profilePins.size)
        vm.setRecapBack(5)
        assertEquals(5, vm.state.value.recapBack)
        vm.setBackdrop(Backdrop(BackdropStyle.Prism.id))
        assertEquals(BackdropStyle.Prism, vm.state.value.backdrop.kind)
        // Закончился — созданное остаётся
        now += 4 * DAY_MS
        vm.refreshPlan()
        assertEquals(4, vm.state.value.playlists.size)
        assertEquals(BackdropStyle.Prism, vm.state.value.backdrop.kind)
        assertEquals(Premium.PLUS_PINS, vm.state.value.profilePins.size)
        // Тот же премиум-фон: менять затемнение можно и без подписки, другой премиум-фон — нет
        vm.setBackdrop(Backdrop(BackdropStyle.Prism.id, "", 0.6f))
        assertEquals(0.6f, vm.state.value.backdrop.dim)
        vm.setBackdrop(Backdrop(BackdropStyle.Crystals.id))
        assertEquals(BackdropStyle.Prism, vm.state.value.backdrop.kind)
    }

    @Test
    fun businessForSellers() {
        val vm = vm()
        assertNull(vm.createGroup("Мастерская", "Свечи", "Рукоделие"))
        val id = vm.state.value.groups.first { it.mine }.id
        repeat(Premium.FREE_PRODUCTS) { assertNull(vm.saveProduct(id, Product(0, "Свеча $it", 300))) }
        assertNotNull(vm.saveProduct(id, Product(0, "Одиннадцатая", 300)))
        assertEquals(PremiumFeature.ShopUnlimited, vm.state.value.paywall)
        // Изменить уже добавленный — можно
        val first = vm.state.value.groups.first { it.id == id }.shop.first()
        assertNull(vm.saveProduct(id, first.copy(price = 350)))
        // Контакты и фон — Бизнес; описание и сайт — бесплатно
        vm.updateGroupInfo(id, GroupInfo(about = "Свечи", website = "candles.ru", phone = "+7 900"))
        var info = vm.state.value.groups.first { it.id == id }.info
        assertEquals("Свечи", info.about)
        assertEquals("", info.phone)
        assertEquals(PremiumFeature.GroupContacts, vm.state.value.paywall)
        vm.setGroupBackdrop(id, Backdrop(BackdropStyle.Aurora.id))
        assertNull(vm.state.value.groups.first { it.id == id }.backdrop)
        // Memory+ — мало: это Бизнес
        vm.startTrial(PlanTier.Plus)
        assertNotNull(vm.saveProduct(id, Product(0, "Одиннадцатая", 300)))
        vm.subscribe(PlanTier.Business, BillingPeriod.Month)
        assertNull(vm.saveProduct(id, Product(0, "Одиннадцатая", 300)))
        vm.updateGroupInfo(id, GroupInfo(about = "Свечи", phone = "+7 900"))
        info = vm.state.value.groups.first { it.id == id }.info
        assertEquals("+7 900", info.phone)
        vm.setGroupBackdrop(id, Backdrop(BackdropStyle.Aurora.id))
        assertNotNull(vm.state.value.groups.first { it.id == id }.backdrop)
        // Бизнес включает Memory+
        assertTrue(vm.has(PremiumFeature.Backdrops))
    }

    @Test
    fun subscriptionSurvivesRestart() {
        val vm = vm()
        vm.subscribe(PlanTier.Plus, BillingPeriod.Year)
        val again = vm()
        again.restore(vm.snapshot())
        assertEquals(PlanTier.Plus, again.state.value.plan)
        assertEquals(vm.state.value.subscription, again.state.value.subscription)
        // Пустое сохранение — бесплатный тариф
        assertEquals(Subscription(), vm.snapshot().copy(subscription = Subscription()).subscription)
    }
}
