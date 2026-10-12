package ru.wishka.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.wishka.memory.data.BookStatus
import ru.wishka.memory.data.CHECK_IN_ACTIVITIES
import ru.wishka.memory.data.Catalog
import ru.wishka.memory.data.Dated
import ru.wishka.memory.data.Demo
import ru.wishka.memory.data.DemoTaste
import ru.wishka.memory.data.GrowthEvent
import ru.wishka.memory.data.GrowthEventType
import ru.wishka.memory.data.Invite
import ru.wishka.memory.data.InviteAction
import ru.wishka.memory.data.Invites
import ru.wishka.memory.data.PlanReason
import ru.wishka.memory.data.Post
import ru.wishka.memory.data.Rated
import ru.wishka.memory.data.Shelf
import ru.wishka.memory.data.ShelfMark
import ru.wishka.memory.data.TogetherDemo
import ru.wishka.memory.data.Unlock
import ru.wishka.memory.data.checkIn
import ru.wishka.memory.data.growthStats
import ru.wishka.memory.data.logged
import ru.wishka.memory.data.memoriesOfDay
import ru.wishka.memory.data.paceTitle
import ru.wishka.memory.data.recommend
import ru.wishka.memory.data.runPlan
import ru.wishka.memory.data.tasteMatch
import ru.wishka.memory.data.unlockProgress
import ru.wishka.memory.data.TogetherSection
import ru.wishka.memory.i18n.L10n
import ru.wishka.memory.i18n.Lang
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.Tab
import java.time.LocalDate
import java.time.ZoneId

/** Полка, память, рекомендации, план бега, права, отклик, приглашения. */
class Round12Test {
    @Before
    fun russian() {
        L10n.choice = Lang.Ru
    }

    private val zone = ZoneId.of("Europe/Moscow")
    private val day = 24 * 3_600_000L
    /** Среда, 14 октября 2026, полдень. */
    private val now = LocalDate.of(2026, 10, 14).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private fun act(id: String) = CHECK_IN_ACTIVITIES.first { it.id == id }
    private fun run(km: Double, minutes: Int = 0, at: Long = now) = Dated(act("run").checkIn("Парк", km, minutes = minutes), at)
    private fun movie(subject: String, rating: Int = 0, at: Long = now) = Dated(act("movie").checkIn(subject, 0.0, rating = rating), at)
    private fun book(subject: String, status: String, pages: Double = 0.0, at: Long = now) = Dated(act("book").checkIn(subject, pages, status = status), at)

    @Test
    fun paceAndStructuredCheckIns() {
        assertEquals("5:00 /км", paceTitle("run", 6.0, 30))
        assertEquals("20 км/ч", paceTitle("bike", 20.0, 60))
        assertEquals("", paceTitle("run", 6.0, 0))
        // Оценка — только там, где оценивают; статус — только у книги
        assertEquals(0, act("run").checkIn("Парк", 5.0, rating = 4).rating)
        assertEquals(5, act("movie").checkIn("Дюна", 0.0, rating = 9).rating)
        assertEquals("", act("movie").checkIn("Дюна", 0.0, status = "done").status)
        assertEquals(0, act("movie").checkIn("Дюна", 0.0, minutes = 120).minutes)
        assertEquals("Прочитал", act("book").checkIn("1984", 0.0, status = "done").statusTitle)
    }

    @Test
    fun shelfBuildsItself() {
        val shelf = Shelf.build(
            listOf(
                movie("Дюна", 4, now - 3 * day), movie(" дюна ", 5, now - day), movie("Прибытие", 0, now),
                book("1984", "reading", 120.0, now - 2 * day), book("1984", "done", 80.0, now - day), book("Лавр", "", 30.0, now),
                run(5.0, 30, now - 40 * day), run(10.0, 55, now - day), run(3.0, 14, now),
                Dated(act("coffee").checkIn("Зерно", 0.0, rating = 4), now),
                Dated(act("coffee").checkIn("зерно", 0.0), now - day),
            ),
            listOf(ShelfMark("movie", "Солярис", status = BookStatus.Want.id, at = now - 5 * day), ShelfMark("movie", "Прибытие", status = "want", at = now - 9 * day)),
            now, zone,
        )
        val dune = shelf.films.first { it.subject == "Дюна" }
        assertEquals(2, dune.times)
        assertEquals(5, dune.rating)
        // Посмотрели после «хочу» — уже не в планах
        assertEquals(listOf("Солярис"), shelf.plannedFilms.map { it.subject })
        assertEquals(2, shelf.watched.size)
        assertEquals(listOf("1984"), shelf.books(BookStatus.Done).map { it.subject })
        assertEquals(200.0, shelf.books.first { it.subject == "1984" }.pages, 0.01)
        assertEquals(listOf("Лавр"), shelf.books(BookStatus.Reading).map { it.subject })
        val runs = shelf.sport.single()
        assertEquals(18.0, runs.totalKm, 0.01)
        assertEquals(13.0, runs.monthKm, 0.01)
        assertEquals(10.0, runs.longestKm, 0.01)
        assertEquals(280, runs.bestPace)
        assertEquals(2, shelf.places.single().count)
        assertEquals(4, shelf.places.single().rating)
        assertEquals(setOf("film:дюна", "film:прибытие", "film:солярис", "book:1984", "book:лавр"), shelf.keys)
    }

    @Test
    fun memoriesOfTheDay() {
        val me = Demo.me
        fun post(id: Long, at: Long) = Post(id, me, "Запись $id", 0, createdAt = at)
        val yearAgo = LocalDate.of(2025, 10, 14).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val monthAgo = LocalDate.of(2026, 8, 14).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val weekAgo = now - 7 * day
        val other = Post(9, Demo.people.first(), "Чужая", 0, createdAt = yearAgo)
        val all = memoriesOfDay(listOf(post(1, yearAgo), post(2, monthAgo), post(3, weekAgo), other), me.id, now, zone)
        assertEquals(listOf(1L), all.map { it.post.id })
        assertEquals("Год назад", all.single().label)
        assertEquals("2 месяца назад", memoriesOfDay(listOf(post(2, monthAgo), post(3, weekAgo)), me.id, now, zone).single().label)
        assertEquals("Неделю назад", memoriesOfDay(listOf(post(3, weekAgo)), me.id, now, zone).single().label)
        assertTrue(memoriesOfDay(listOf(post(4, now - 3 * day)), me.id, now, zone).isEmpty())
    }

    @Test
    fun tasteAndRecommendations() {
        val mine = listOf(Rated("film", "Интерстеллар", 5), Rated("film", "Дюна", 5), Rated("film", "Прибытие", 5))
        val vera = tasteMatch(mine, DemoTaste.ratings.getValue(2))
        assertEquals(3, vera.common)
        assertEquals(100, vera.percent)
        val recs = recommend(mine, mine.map { it.key }.toSet(), DemoTaste.ratings, "film")
        // Вера с тем же вкусом — её «Солярис» первым; уже оценённое не советуем
        assertEquals("Солярис", recs.first().subject)
        assertEquals(2, recs.first().personId)
        assertTrue(recs.none { it.subject == "Дюна" })
        // Противоположный вкус не советует: Алина ставит «Брату» 2, Марк — 5
        val brat = listOf(Rated("film", "Брат", 5), Rated("film", "Амели", 1), Rated("film", "Ла-Ла Ленд", 4))
        val forBrat = recommend(brat, brat.map { it.key }.toSet(), DemoTaste.ratings, "film")
        assertTrue(forBrat.none { it.personId == 4 })
        // Новичок без оценок всё равно получает подборку
        assertTrue(recommend(emptyList(), emptySet(), DemoTaste.ratings, "book").isNotEmpty())
        assertNotNull(Catalog.find("book", "мастер и маргарита"))
    }

    @Test
    fun runPlanGrowsTenPercent() {
        assertEquals(PlanReason.Start, runPlan(emptyList(), now, zone).reason)
        assertEquals(5.0, runPlan(emptyList(), now, zone).targetKm, 0.0)
        // Прошлая неделя (5–11 октября): 20 км → план 22
        val grow = runPlan(listOf(run(10.0, at = now - 5 * day), run(10.0, at = now - 4 * day), run(3.0, at = now)), now, zone)
        assertEquals(PlanReason.Grow, grow.reason)
        assertEquals(22.0, grow.targetKm, 0.0)
        assertEquals(3.0, grow.doneKm, 0.0)
        assertEquals(19.0, grow.leftKm, 0.0)
        // Пропустили прошлую неделю — мягче, чем в среднем
        val back = runPlan(listOf(run(20.0, at = now - 16 * day)), now, zone)
        assertEquals(PlanReason.Return, back.reason)
        assertEquals(4.0, back.targetKm, 0.0)
    }

    @Test
    fun unlocksAreEarned() {
        val t = TogetherDemo.initial(0)
        assertFalse(unlockProgress(t, 1).first { it.unlock == Unlock.HideCache }.open)
        val found = t.copy(caches = t.caches.map { it.copy(foundByMe = true) })
        assertTrue(unlockProgress(found, 1).first { it.unlock == Unlock.HideCache }.open)
        val goals = t.contribute("km", 3.0).contribute("books", 1.0)
        assertTrue(unlockProgress(goals, 1).first { it.unlock == Unlock.OwnGoal }.open)
        assertEquals(1, unlockProgress(t.contribute("km", 3.0), 1).first { it.unlock == Unlock.OwnGoal }.left)
        val route = t.missions.first { it.id == "route" }
        assertTrue(route.locked(19.0))
        assertFalse(route.locked(20.0))
    }

    @Test
    fun invitesRoundTrip() {
        val code = Invites.code("wishka")
        assertEquals(6, code.length)
        assertEquals(code, Invites.code("WISHKA"))
        val invite = Invite(code, InviteAction.Drawing, "9701", "Андрей Б")
        val web = Invites.webLink(invite)
        assertTrue(web.startsWith(Invites.WEB))
        assertEquals(invite, Invites.parse(web))
        assertEquals(invite, Invites.parse(Invites.appLink(invite)))
        // Страница-приглашение кодирует пробел как %20
        assertEquals("Андрей Б", Invites.parse("memory://invite?c=$code&a=book&n=%D0%90%D0%BD%D0%B4%D1%80%D0%B5%D0%B9%20%D0%91")?.from)
        assertNull(Invites.parse("https://example.com/?c=$code"))
        assertNull(Invites.parse("memory://invite?c=ABC"))
        assertEquals(InviteAction.App, Invites.parse("memory://invite?c=$code&a=nonsense")?.action)
        assertTrue(Invites.message(invite, "Город").contains("дорисовать общий рисунок «Город»"))
    }

    @Test
    fun growthLog() {
        var log = emptyList<GrowthEvent>()
        repeat(600) { log = log.logged(GrowthEvent(GrowthEventType.InviteSent, it.toLong(), if (it % 2 == 0) "drawing:1" else "book")) }
        assertEquals(500, log.size)
        val stats = growthStats(log + GrowthEvent(GrowthEventType.JoinedByInvite, 0))
        assertEquals(500, stats.invitesSent)
        assertEquals(250, stats.byAction["drawing"])
        assertTrue(stats.joinedByInvite)
        assertEquals(50.0, stats.kEstimate(), 0.001)
    }

    @Test
    fun viewModelEchoShelfAndInvites() {
        val vm = AppViewModel(demoReplies = false)
        vm.joinGoal("km")
        vm.publish("", checkIn = act("run").checkIn("Парк", 5.0, minutes = 25))
        val echo = vm.state.value.echo
        assertNotNull(echo)
        assertTrue(echo!!.lines.toString(), echo.lines.any { it.text.contains("+5 км") })
        assertTrue(echo.lines.toString(), echo.lines.any { it.text.contains("План недели выполнен") })
        assertTrue(echo.lines.toString(), echo.lines.any { it.text.contains("Огни набережных") })
        vm.dismissEcho()
        assertNull(vm.state.value.echo)
        // Знакомство: любимое на полке — и подборка уже по вкусу
        vm.addFavorites(Catalog.films.filter { it.title in setOf("Интерстеллар", "Дюна", "Прибытие") })
        assertEquals(3, vm.state.value.shelf.watched.size)
        assertEquals("Солярис", vm.state.value.recommendations("film").first().subject)
        vm.planOnShelf("film", "Солярис")
        assertEquals(listOf("Солярис"), vm.state.value.shelf.plannedFilms.map { it.subject })
        assertTrue(vm.state.value.recommendations("film").none { it.subject == "Солярис" })
        vm.dismissRecommendation("film:сталкер")
        assertTrue(vm.state.value.recommendations("film").none { it.subject == "Сталкер" })
        // Права: прятать клады и свою миссию — не сразу
        assertFalse(vm.hideCache("Клад", "Под камнем", 2, "Микро", ru.wishka.memory.data.LatLon.DEFAULT))
        assertNotNull(vm.createMission("🗺️", "Почтовые ящики", "", 5))
        // Чужое приглашение: кто позвал, и ведём к рисунку
        val invite = Invite("ABCDEF", InviteAction.Drawing, "9701", "Вера")
        val fresh = AppViewModel(demoReplies = false)
        fresh.openInvite(invite)
        assertEquals(invite, fresh.state.value.invitedBy)
        assertEquals(Tab.Together, fresh.state.value.tab)
        assertEquals(TogetherSection.Drawing, fresh.state.value.togetherSection)
        assertTrue(fresh.state.value.growth.any { it.type == GrowthEventType.JoinedByInvite })
        // Своя ссылка — не «пригласили»
        val own = AppViewModel(demoReplies = false)
        own.openInvite(Invite(own.state.value.inviteCode, InviteAction.App))
        assertNull(own.state.value.invitedBy)
        assertTrue(own.invite(InviteAction.Book, "9802", "Задача трёх тел").contains(Invites.WEB))
        assertEquals(1, growthStats(own.state.value.growth).invitesSent)
    }

    @Test
    fun reviewFixes() {
        // Оценка с полки и «Смотрел» — не лишний просмотр; «Прочитал» после чек-ина «Читаю» — книга прочитана один раз
        val shelf = Shelf.build(
            listOf(movie("Дюна", at = now - day), book("Лавр", BookStatus.Reading.id, at = now - day)),
            listOf(ShelfMark("movie", "Дюна", 5, "", now), ShelfMark("book", "Лавр", 4, BookStatus.Done.id, now), ShelfMark("movie", "Брат", 4, "", now)),
            now, zone,
        )
        assertEquals(1, shelf.films.first { it.subject == "Дюна" }.times)
        assertEquals(5, shelf.films.first { it.subject == "Дюна" }.rating)
        assertEquals(1, shelf.films.first { it.subject == "Брат" }.times)
        assertEquals(1, shelf.books.single().times)
        assertEquals(BookStatus.Done.id, shelf.books.single().status)
        // Повторное восстановление (аккаунт перечитан после смены имени) не удваивает журнал
        val vm = AppViewModel(demoReplies = false)
        vm.publish("", checkIn = act("run").checkIn("Парк", 5.0))
        val snap = vm.snapshot()
        vm.restore(snap)
        vm.restore(snap)
        assertEquals(snap.growth, vm.state.value.growth)
    }
}
