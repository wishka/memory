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
        vm.toggleFollow(3)
        assertTrue(3 in vm.state.value.sentRequests)
        assertFalse(3 in vm.state.value.following)
        vm.acceptSentRequest(3)
        assertTrue(3 in vm.state.value.following)
        assertTrue(vm.state.value.feed.any { it.author.id == 3 })
    }

    @Test
    fun followRequestsWhenProfileClosed() {
        val vm = AppViewModel(demoReplies = false)
        vm.receiveFollowRequest(6)
        assertTrue(vm.state.value.followRequests.isEmpty())
        vm.setPrivacy(Privacy(closedProfile = true))
        vm.receiveFollowRequest(6)
        assertEquals(listOf(6), vm.state.value.followRequests)
        vm.approveRequest(6)
        assertTrue(vm.state.value.followers.any { it.id == 6 })
        vm.setPrivacy(Privacy(closedProfile = true))
        // Открыли профиль — оставшиеся заявки одобряются
        val vm2 = AppViewModel(demoReplies = false)
        vm2.setPrivacy(Privacy(closedProfile = true))
        vm2.receiveFollowRequest(6)
        vm2.setPrivacy(Privacy(closedProfile = false))
        assertTrue(vm2.state.value.followRequests.isEmpty())
        assertTrue(vm2.state.value.followers.any { it.id == 6 })
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
