package ru.wishka.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.wishka.memory.data.CHECK_IN_ACTIVITIES
import ru.wishka.memory.data.Cache
import ru.wishka.memory.data.CacheLog
import ru.wishka.memory.data.DealStatus
import ru.wishka.memory.data.DrawStroke
import ru.wishka.memory.data.LatLon
import ru.wishka.memory.data.Outing
import ru.wishka.memory.data.ShareKind
import ru.wishka.memory.data.ShareStatus
import ru.wishka.memory.data.SkillOffer
import ru.wishka.memory.data.TogetherDemo
import ru.wishka.memory.data.TogetherSection
import ru.wishka.memory.data.TogetherState
import ru.wishka.memory.data.checkIn
import ru.wishka.memory.data.matchScore
import ru.wishka.memory.data.minutesTitle
import ru.wishka.memory.i18n.L10n
import ru.wishka.memory.i18n.Lang
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.Tab

/** Вкладка «Вместе»: цели, банк времени, клады, вылазки, рисунок, обмен. */
class Round11Test {
    @Before
    fun russian() {
        L10n.choice = Lang.Ru
    }

    private val demo = TogetherDemo.initial(0)
    private val stroke = listOf(DrawStroke(listOf(0.1f to 0.1f, 0.5f to 0.5f), 0, 0.02f))

    @Test
    fun checkInsMoveJoinedGoalsOnly() {
        val run = CHECK_IN_ACTIVITIES.first { it.id == "run" }.checkIn("Парк", 5.0)
        val km = demo.goals.first { it.id == "km" }
        // Не участвуете — шкала стоит
        assertEquals(km.total, demo.afterCheckIn(run).goals.first { it.id == "km" }.total, 0.0)
        val joined = demo.joinGoal("km").afterCheckIn(run).goals.first { it.id == "km" }
        assertEquals(km.total + 5, joined.total, 0.001)
        assertEquals(5.0, joined.mine, 0.001)
        assertEquals(km.participants + 1, joined.participants)
        val yoga = CHECK_IN_ACTIVITIES.first { it.id == "yoga" }.checkIn("Утро", 90.0)
        assertEquals(1.5, demo.joinGoal("hours").afterCheckIn(yoga).goals.first { it.id == "hours" }.mine, 0.001)
        assertEquals(demo, demo.contribute("trees", -3.0))
    }

    @Test
    fun timeBankBalance() {
        val s = TogetherState(balance = 30)
        assertNull(s.propose(1, 2, "", "", 30, 0))
        // Урок «за часы» дороже баланса — нельзя
        assertNull(s.propose(1, 2, "", "Гитара", 60, 0))
        val learn = s.propose(1, 2, "", "Гитара", 30, 0)!!
        assertEquals(DealStatus.Proposed, learn.deals.single().status)
        assertEquals(0, learn.setDeal(1, DealStatus.Done).balance)
        // Повторное «прошло» не списывает второй раз
        assertEquals(0, learn.setDeal(1, DealStatus.Done).setDeal(1, DealStatus.Done).balance)
        val teach = s.propose(2, 3, "Фото", "", 45, 0)!!.setDeal(2, DealStatus.Done)
        assertEquals(75, teach.balance)
        // Обмен 1:1 баланс не трогает
        assertEquals(30, s.propose(3, 4, "Фото", "Гитара", 30, 0)!!.setDeal(3, DealStatus.Done).balance)
        // Обещанные уроки уже «заняли» часы: второй такой же не пройдёт
        assertNull(learn.propose(4, 5, "", "Походы", 30, 0))
        // Отказ окончателен и баланс не трогает
        val declined = learn.setDeal(1, DealStatus.Declined)
        assertEquals(declined, declined.setDeal(1, DealStatus.Done))
        assertEquals("1 ч 30 мин", minutesTitle(90))
        assertEquals("−45 мин", minutesTitle(-45))
    }

    @Test
    fun skillMatching() {
        val offer = SkillOffer(3, listOf("Гитара"), listOf("Фотография"))
        assertEquals(2, matchScore(offer, listOf("фотография"), listOf("гитара")))
        assertEquals(1, matchScore(offer, emptyList(), listOf("Гитара")))
        assertEquals(0, matchScore(offer, listOf("Йога"), listOf("Танцы")))
        assertEquals(3, demo.matches().first().first.personId)
        assertEquals(demo.myTeach, demo.addSkill(true, " фотография ").myTeach)
    }

    @Test
    fun cacheNeedsToBeNearby() {
        val c = Cache(1, 5, "Клад", "Под камнем", LatLon.DEFAULT, 2, "Микро")
        val s = TogetherState(caches = listOf(c))
        val log = CacheLog(10, 1, "Нашёл!")
        assertNull(s.findCache(1, log, null))
        assertNull(s.findCache(1, log, LatLon.DEFAULT.offset(400.0, 0.0)))
        val found = s.findCache(1, log, LatLon.DEFAULT.offset(60.0, 40.0))!!
        assertTrue(found.caches.single().foundByMe)
        assertNull(found.findCache(1, log.copy(id = 11), LatLon.DEFAULT))
        // Свой клад не «находят»
        assertNull(s.findCache(1, log.copy(authorId = 5), LatLon.DEFAULT))
        assertEquals(1000.0, LatLon.DEFAULT.distanceTo(LatLon.DEFAULT.offset(0.0, 1000.0)), 5.0)
    }

    @Test
    fun outingsHaveCapacity() {
        val o = Outing(1, 2, "🍄", "Грибы", "", "Метро", 100, 50, listOf(2))
        val s = TogetherState().createOuting(o)!!
        assertEquals(Outing.MAX_PEOPLE, s.outings.single().capacity)
        assertNull(TogetherState().createOuting(o.copy(place = " ")))
        val small = TogetherState().createOuting(o.copy(capacity = 2))!!
        val joined = small.toggleOuting(1, 1)
        assertEquals(listOf(2, 1), joined.outings.single().participants)
        assertEquals(joined, joined.toggleOuting(1, 3))
        assertEquals(listOf(2), joined.toggleOuting(1, 1).outings.single().participants)
        // Организатор не выходит, а отменяет
        assertEquals(joined, joined.toggleOuting(1, 2))
        assertTrue(joined.cancelOuting(1, 2).outings.isEmpty())
        assertEquals(1, joined.cancelOuting(1, 1).outings.size)
    }

    @Test
    fun drawingPassesAround() {
        val s = TogetherState().startDrawing(1, "Город", 2, 1, 0)!!
        assertEquals(1, s.drawings.single().waitingFor)
        // Не ваш ход и пустой ход — не считаются
        assertEquals(s, s.drawTurn(1, 2, stroke, 3))
        assertEquals(s, s.drawTurn(1, 1, emptyList(), 3))
        val passed = s.drawTurn(1, 1, stroke, 4)
        assertEquals(4, passed.drawings.single().waitingFor)
        val done = passed.drawTurn(1, 4, stroke, 1).drawings.single()
        assertTrue(done.finished)
        assertNull(done.waitingFor)
        assertEquals(2, done.strokes.size)
    }

    @Test
    fun booksTravelSeedsAreGiven() {
        val book = demo.shares.first { it.id == 9802L }
        val asked = demo.requestShare(book.id, 1)
        assertEquals(ShareStatus.Requested, asked.shares.first { it.id == book.id }.status)
        val moved = asked.handOver(book.id, 1).shares.first { it.id == book.id }
        assertEquals(1, moved.holderId)
        assertEquals(listOf(2), moved.history)
        assertEquals(ShareStatus.Available, moved.status)
        assertNull(moved.requestedBy)
        val seeds = demo.handOver(9803, 1).shares.first { it.id == 9803L }
        assertEquals(ShareStatus.Given, seeds.status)
        val repair = demo.offerHelp(9805, 1).offerHelp(9805, 1).shares.first { it.id == 9805L }
        assertEquals(listOf(1), repair.helpers)
        assertEquals(ShareStatus.Fixed, demo.markFixed(9805).shares.first { it.id == 9805L }.status)
        // Свою вещь не просят
        assertEquals(demo, demo.requestShare(9803, 5))
        assertEquals(ShareKind.Repair, repair.kind)
    }

    @Test
    fun demoPointsMoveToYouOnce() {
        val here = LatLon(59.9343, 30.3351)
        val moved = demo.relocate(here, 1)
        val before = demo.caches.first().at.distanceTo(demo.anchor)
        assertEquals(before, moved.caches.first().at.distanceTo(here), 50.0)
        assertTrue(moved.anchored)
        val again = moved.relocate(LatLon.DEFAULT, 1)
        assertEquals(moved.caches, again.caches)
        assertEquals(LatLon.DEFAULT, again.anchor)
    }

    @Test
    fun viewModelTogether() {
        val vm = AppViewModel(demoReplies = false)
        vm.selectTab(Tab.Together)
        vm.selectTogetherSection(TogetherSection.Sharing)
        assertEquals(TogetherSection.Sharing, vm.state.value.togetherSection)
        vm.joinGoal("km")
        vm.publish("", checkIn = CHECK_IN_ACTIVITIES.first { it.id == "run" }.checkIn("Парк", 3.0))
        assertEquals(3.0, vm.state.value.together.goals.first { it.id == "km" }.mine, 0.001)
        assertNull(vm.proposeDeal(3, "Фотография", "Гитара", 30))
        assertEquals(1, vm.state.value.together.deals.size)
        assertNotNull(vm.proposeDeal(3, "", "", 30))
        val id = vm.startDrawing("Море", 3)
        assertNotNull(id)
        assertFalse(vm.state.value.together.drawings.first { it.id == id }.finished)
    }
}
