package ru.wishka.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.wishka.memory.data.CHECK_IN_ACTIVITIES
import ru.wishka.memory.data.Demo
import ru.wishka.memory.data.NudgeKind
import ru.wishka.memory.data.Nudges
import ru.wishka.memory.data.Notifications
import ru.wishka.memory.data.Privacy
import ru.wishka.memory.data.Recap
import ru.wishka.memory.data.RecapPeriod
import ru.wishka.memory.data.User
import ru.wishka.memory.data.checkIn
import ru.wishka.memory.i18n.L10n
import ru.wishka.memory.i18n.Lang
import ru.wishka.memory.ui.AppViewModel
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/** Чек-ины, Итоги, закрытый профиль и персональные уведомления. */
class Round10Test {
    @Before
    fun russian() {
        L10n.choice = Lang.Ru
    }

    private fun movie(subject: String) = CHECK_IN_ACTIVITIES.first { it.id == "movie" }.checkIn(subject, 0.0)
    private fun run(km: Double) = CHECK_IN_ACTIVITIES.first { it.id == "run" }.checkIn("Парк", km)

    @Test
    fun checkInPublishesWithoutText() {
        val vm = AppViewModel(demoReplies = false)
        vm.publish("", checkIn = movie("Дюна"))
        val post = vm.state.value.myPosts.first()
        assertEquals("Дюна", post.checkIn?.subject)
        assertEquals("Смотрю фильм", post.checkIn?.displayTitle)
    }

    @Test
    fun customTemplateCreatedOnceAndDeleted() {
        val vm = AppViewModel(demoReplies = false)
        assertNull(vm.createCheckInTemplate("🧗", "Скалодром", "мин", "Где?"))
        assertNotNull(vm.createCheckInTemplate("🧗", "скалодром", "", ""))
        assertNotNull(vm.createCheckInTemplate("⭐", "Смотрю фильм", "", ""))
        val tpl = vm.state.value.checkInTemplates.single()
        assertEquals(45.0, tpl.checkIn("Зал", 45.0).amount, 0.0)
        vm.deleteCheckInTemplate(tpl.id)
        assertTrue(vm.state.value.checkInTemplates.isEmpty())
    }

    @Test
    fun recapCountsFilmsKmAndPosts() {
        val now = LocalDate.of(2026, 9, 15).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val vm = AppViewModel(clock = { now }, demoReplies = false)
        vm.publish("", checkIn = movie("Дюна"))
        vm.publish("", checkIn = movie("Солярис"))
        vm.publish("утро", checkIn = run(5.5))
        vm.publish("вечер", checkIn = run(2.0))
        val r = vm.recap(RecapPeriod.Month, 2026, 9)
        assertEquals(4, r.checkIns)
        assertEquals("2", r.tiles.first { it.emoji == "🎬" }.value)
        assertEquals("7,5", r.tiles.first { it.emoji == "🏃" }.value)
        assertEquals("фильма", r.tiles.first { it.emoji == "🎬" }.label)
        assertTrue(r.headline.startsWith("2 фильма"))
        assertTrue(vm.recap(RecapPeriod.Month, 2026, 8).empty)
        assertEquals(4, vm.recap(RecapPeriod.Year, 2026, 0).checkIns)
        assertEquals("Сентябрь 2026", r.title)
    }

    @Test
    fun recapNudgeOncePerMonth() {
        val now = LocalDate.of(2026, 9, 15).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val vm = AppViewModel(clock = { now }, demoReplies = false)
        vm.publish("", checkIn = movie("Дюна"))
        vm.checkRecapReady(LocalDate.of(2026, 10, 2))
        assertTrue("2026-09" in vm.state.value.recapNotified)
        val before = vm.state.value.recapNotified
        vm.checkRecapReady(LocalDate.of(2026, 10, 3))
        assertEquals(before, vm.state.value.recapNotified)
    }

    @Test
    fun closedPeopleHiddenUntilAccepted() {
        val vm = AppViewModel(demoReplies = false)
        val mark = Demo.person(3)!!
        assertTrue(mark.closed)
        assertFalse(vm.state.value.feed.any { it.author.id == 3 })
        vm.toggleFriend(3)
        assertTrue(3 in vm.state.value.sentRequests)
        assertFalse(3 in vm.state.value.following)
        vm.acceptSentRequest(3)
        assertTrue(3 in vm.state.value.following)
        assertTrue(vm.state.value.feed.any { it.author.id == 3 })
    }

    @Test
    fun friendRequestsRegardlessOfClosedProfile() {
        val vm = AppViewModel(demoReplies = false)
        // Открытый профиль — заявка в друзья всё равно приходит
        vm.receiveFriendRequest(5)
        assertEquals(listOf(5), vm.state.value.followRequests)
        assertEquals(ru.wishka.memory.ui.FriendState.Incoming, vm.state.value.friendState(5))
        // Повторная — не дублируется; от друга (6) — не приходит
        vm.receiveFriendRequest(5)
        vm.receiveFriendRequest(6)
        assertEquals(listOf(5), vm.state.value.followRequests)
        vm.approveRequest(5)
        assertTrue(5 in vm.state.value.following)
        assertTrue(vm.state.value.friends.any { it.id == 5 })
        assertTrue(vm.state.value.followRequests.isEmpty())
        // Закрыли и снова открыли профиль — ждущая заявка не одобряется сама
        val vm2 = AppViewModel(demoReplies = false)
        vm2.setPrivacy(Privacy(closedProfile = true))
        vm2.receiveFriendRequest(3)
        vm2.setPrivacy(Privacy(closedProfile = false))
        assertEquals(listOf(3), vm2.state.value.followRequests)
        assertFalse(3 in vm2.state.value.following)
        // Отклонили — заявка исчезает, в друзья не попадает
        vm2.declineRequest(3)
        assertTrue(vm2.state.value.followRequests.isEmpty())
        assertFalse(3 in vm2.state.value.following)
        // Из чёрного списка заявки не приходят
        vm2.block(5)
        vm2.receiveFriendRequest(5)
        assertTrue(vm2.state.value.followRequests.isEmpty())
    }

    @Test
    fun sendingRequestToRequesterMakesFriendsAtOnce() {
        val vm = AppViewModel(demoReplies = false)
        vm.receiveFriendRequest(5)
        vm.toggleFriend(5)
        val s = vm.state.value
        assertTrue(5 in s.following)
        assertTrue(s.followRequests.isEmpty())
        assertTrue(s.sentRequests.isEmpty())
    }

    @Test
    fun openProfileRequestAcceptedAndCancelled() {
        val vm = AppViewModel(demoReplies = false)
        // Открытый профиль (Тимур) — тоже по заявке
        assertFalse(Demo.person(5)!!.closed)
        vm.toggleFriend(5)
        assertEquals(ru.wishka.memory.ui.FriendState.Requested, vm.state.value.friendState(5))
        assertFalse(5 in vm.state.value.following)
        // Повторное нажатие — заявка отозвана; принять отозванную нельзя
        vm.toggleFriend(5)
        assertTrue(vm.state.value.sentRequests.isEmpty())
        vm.acceptSentRequest(5)
        assertFalse(5 in vm.state.value.following)
        // Отправили снова — приняли: друзья и уведомление о принятой заявке
        vm.toggleFriend(5)
        vm.acceptSentRequest(5)
        assertEquals(ru.wishka.memory.ui.FriendState.Friends, vm.state.value.friendState(5))
        assertEquals("Тимур принял вашу заявку в друзья", Nudges.requestAccepted(Demo.person(5)!!).text)
        assertEquals("Алина приняла вашу заявку в друзья", Nudges.requestAccepted(Demo.person(4)!!).text)
        assertEquals("Тимур хочет добавить вас в друзья", Nudges.followRequest(Demo.person(5)!!, emptyList()).text)
    }

    @Test
    fun friendsSurviveSnapshotAndDemoRequestsCome() {
        val vm = AppViewModel(demoReplies = false)
        vm.receiveFriendRequest(3)
        vm.toggleFriend(5)
        val snap = vm.snapshot()
        val vm2 = AppViewModel(demoReplies = false)
        vm2.restore(snap)
        assertEquals(listOf(3), vm2.state.value.followRequests)
        assertEquals(setOf(5), vm2.state.value.sentRequests)
        assertEquals(vm.state.value.following, vm2.state.value.following)
        // Демо-жизнь шлёт заявки и при открытом профиле
        val vm3 = AppViewModel(demoReplies = false)
        repeat(200) { vm3.demoEvent(Random(it)) }
        assertTrue(vm3.state.value.followRequests.isNotEmpty())
        assertTrue(vm3.state.value.followRequests.none { it in vm3.state.value.following })
    }

    @Test
    fun nudgeMentionsDiscussedFilm() {
        val vera = Demo.person(2)!!
        assertTrue(Nudges.female(vera))
        assertFalse(Nudges.female(User(9, "Илья", "i")))
        assertTrue(Nudges.discussed("Дюна", listOf("Вчера пересмотрел дюну, мощно")))
        val n = Nudges.friendCheckIn(vera, movie("Дюна"), discussed = true, myKm = 0.0, postId = 1)
        assertEquals("Ваша подруга Вера только что отметила фильм «Дюна», который вы обсуждали", n.text)
        val r = Nudges.friendCheckIn(Demo.person(5)!!, run(7.0), discussed = false, myKm = 12.0, postId = 2)
        assertEquals("Тимур пробежал 7 км. У вас за месяц — 12 км, не отставайте!", r.text)
    }

    @Test
    fun nudgeRespectsSettings() {
        val n = Nudges.groupMember(Demo.groups.first(), User(9, "Мария", "m"))
        assertEquals("В вашей группе «Ночное небо» новый участник — Мария", n.text)
        assertTrue(Nudges.allowed(n, Notifications()))
        assertFalse(Nudges.allowed(n, Notifications(groups = false)))
        assertFalse(Nudges.allowed(n, Notifications(all = false)))
        assertEquals(NudgeKind.Groups, n.kind)
    }

    @Test
    fun demoFriendCheckInPrefersDiscussedFilm() {
        val vm = AppViewModel(demoReplies = false)
        vm.publish("Кто смотрел Солярис? Хочу обсудить")
        repeat(200) { vm.demoEvent(Random(it)) }
        val checks = vm.state.value.posts.mapNotNull { it.checkIn }.filter { it.activity == "movie" }
        assertTrue(checks.isNotEmpty())
        assertTrue(checks.any { it.subject == "Солярис" })
    }

    @Test
    fun pushPayloadBuildsText() {
        val n = Nudges.fromPush(mapOf("type" to "friend_checkin", "actor" to "Вера Лунная", "activity" to "book", "subject" to "Пикник на обочине", "discussed" to "1"))
        assertEquals("Ваша подруга Вера начала читать «Пикник на обочине» — книгу, которую вы обсуждали", n?.text)
        assertNull(Nudges.fromPush(mapOf("type" to "unknown")))
    }

    @Test
    fun voiceAndStickerPreview() {
        val voice = ru.wishka.memory.data.Attachment("v", ru.wishka.memory.data.MediaKind.Document, "/x.m4a", voice = true, durationMs = 12_000)
        assertFalse(voice.isAudio)
        val msg = ru.wishka.memory.data.Message(1, true, "", "12:00", listOf(voice))
        assertEquals("Голосовое сообщение · 0:12", ru.wishka.memory.ui.messagePreview(msg))
        assertEquals(listOf(5, 9), ru.wishka.memory.ui.components.VoiceRecorder.squeeze(listOf(1, 5, 9, 2), 2))
    }
}
