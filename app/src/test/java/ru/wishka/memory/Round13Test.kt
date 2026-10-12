package ru.wishka.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.Backdrop
import ru.wishka.memory.data.BackdropStyle
import ru.wishka.memory.data.LatLon
import ru.wishka.memory.data.MediaKind
import ru.wishka.memory.data.NotifySound
import ru.wishka.memory.data.NudgeKind
import ru.wishka.memory.data.Poll
import ru.wishka.memory.data.PollDraft
import ru.wishka.memory.data.PollOption
import ru.wishka.memory.data.Sticker
import ru.wishka.memory.data.StickerAnim
import ru.wishka.memory.data.StickerKind
import ru.wishka.memory.data.backdropOr
import ru.wishka.memory.data.barFraction
import ru.wishka.memory.data.pose
import ru.wishka.memory.i18n.L10n
import ru.wishka.memory.i18n.Lang
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.messagePreview
import kotlin.random.Random

/** Опросы, гифки, коллекции стикеров, живые стикеры, фоны, звуки, место в переписке. */
class Round13Test {
    @Before
    fun russian() {
        L10n.choice = Lang.Ru
    }

    private fun vm() = AppViewModel(clock = { 1_790_000_000_000L }, demoReplies = false)

    // ---------- Опросы ----------

    @Test
    fun pollNeedsQuestionAndTwoDistinctOptions() {
        assertNull(Poll.create(1, "  ", listOf("а", "б"), false))
        assertNull(Poll.create(1, "Куда?", listOf("Кино", " кино "), false))
        val poll = Poll.create(1, " Куда идём? ", listOf(" Кино", "", "Парк", "кино"), false)
        assertNotNull(poll)
        assertEquals("Куда идём?", poll!!.question)
        assertEquals(listOf("Кино", "Парк"), poll.options.map { it.text })
        val many = Poll.create(1, "?", (1..15).map { "в$it" }, false)!!
        assertEquals(Poll.MAX_OPTIONS, many.options.size)
    }

    @Test
    fun percentsAlwaysSumTo100() {
        val poll = Poll(1, "?", listOf(PollOption("a", 1), PollOption("b", 1), PollOption("c", 1)))
        assertEquals(100, poll.percents().sum())
        assertEquals(listOf(34, 33, 33), poll.percents())
        val empty = Poll(1, "?", listOf(PollOption("a"), PollOption("b")))
        assertEquals(listOf(0, 0), empty.percents())
        assertEquals(0f, empty.barFraction(0))
    }

    @Test
    fun singleVoteMovesMultipleToggles() {
        val single = Poll.create(1, "?", listOf("a", "b"), false)!!
        val first = single.vote(0)
        assertEquals(setOf(0), first.mine)
        val moved = first.vote(1)
        assertEquals(setOf(1), moved.mine)
        assertEquals(listOf(0, 1), moved.options.map { it.votes })
        assertTrue(moved.vote(1).mine.isEmpty())

        val multi = Poll.create(2, "?", listOf("a", "b", "c"), true)!!.vote(0).vote(2)
        assertEquals(setOf(0, 2), multi.mine)
        assertEquals(1, multi.voters)
        assertEquals(setOf(2), multi.vote(0).mine)
    }

    @Test
    fun closedPollRefusesVotes() {
        val closed = Poll.create(1, "?", listOf("a", "b"), false)!!.addVotes(mapOf(1 to 3)).close()
        assertEquals(closed, closed.vote(0))
        assertEquals(1, closed.leader)
    }

    @Test
    fun publishedPollCanBeVotedAndClosedByAuthor() {
        val vm = vm()
        vm.publish("", poll = PollDraft("Кино или парк?", listOf("Кино", "Парк")))
        val post = vm.state.value.posts.first()
        assertNotNull(post.poll)
        assertTrue(vm.isMine(post))
        vm.votePost(post.id, 1)
        assertEquals(setOf(1), vm.state.value.posts.first().poll!!.mine)
        vm.closePostPoll(post.id)
        vm.votePost(post.id, 0)
        val after = vm.state.value.posts.first().poll!!
        assertTrue(after.closed)
        assertEquals(setOf(1), after.mine)
    }

    @Test
    fun invalidPollAloneIsNotPublished() {
        val vm = vm()
        val before = vm.state.value.posts.size
        vm.publish("", poll = PollDraft("?", listOf("один")))
        assertEquals(before, vm.state.value.posts.size)
    }

    @Test
    fun chatPollAndPlace() {
        val vm = vm()
        val chat = vm.state.value.chats.first()
        vm.send(chat.id, "", poll = PollDraft("Во сколько?", listOf("18:00", "19:00")))
        val message = vm.state.value.chats.first { it.id == chat.id }.messages.last()
        val poll = message.poll!!
        vm.voteMessage(chat.id, poll.id, 0)
        assertEquals(setOf(0), vm.state.value.chats.first { it.id == chat.id }.messages.last().poll!!.mine)
        assertEquals("Опрос: Во сколько?", messagePreview(message))

        vm.sendPlace(chat.id, LatLon(55.75, 37.62), "Парк")
        val place = vm.state.value.chats.first { it.id == chat.id }.messages.last()
        assertEquals(55.75, place.place!!.lat, 1e-9)
        assertEquals("Геолокация: Парк", messagePreview(place))
    }

    @Test
    fun forwardedPollStartsClean() {
        val vm = vm()
        val chats = vm.state.value.chats
        val from = chats[0]
        val to = chats[1]
        vm.send(from.id, "", poll = PollDraft("?", listOf("a", "b")))
        val original = vm.state.value.chats.first { it.id == from.id }.messages.last()
        vm.voteMessage(from.id, original.poll!!.id, 1)
        vm.forward(from.id, original.id, to.id)
        val copy = vm.state.value.chats.first { it.id == to.id }.messages.last()
        assertNotNull(copy.poll)
        assertTrue(copy.poll!!.mine.isEmpty())
        assertEquals(0, copy.poll!!.total)
        assertFalse(copy.poll!!.id == original.poll!!.id)
    }

    // ---------- Гифки ----------

    @Test
    fun sentGifsGoToRecentWithoutDuplicates() {
        val vm = vm()
        val chat = vm.state.value.chats.first()
        val gif = Attachment("g1", MediaKind.Photo, "/x/hello.gif", "hello.gif", 10, "image/gif")
        assertTrue(gif.isGif)
        vm.send(chat.id, "", listOf(gif))
        vm.publish("", listOf(gif.copy(id = "g2")))
        vm.send(chat.id, "", listOf(Attachment("p", MediaKind.Photo, "/x/a.jpg", "a.jpg", 10, "image/jpeg")))
        assertEquals(1, vm.state.value.recentGifs.size)
        assertEquals("GIF", messagePreview(vm.state.value.chats.first { it.id == chat.id }.messages.first { it.attachments.firstOrNull()?.isGif == true }))
    }

    // ---------- Стикеры ----------

    @Test
    fun stickerPacks() {
        val vm = vm()
        val pack = vm.createStickerPack(" Котики ")!!
        assertEquals("Котики", pack.name)
        assertNull(vm.createStickerPack("котики"))
        assertNull(vm.createStickerPack("  "))
        vm.addSticker(Sticker("s1", "/x/s1.png", StickerKind.Cutout, 1, packId = pack.id))
        vm.addSticker(Sticker("s2", "/x/s2.png", StickerKind.Drawing, 2, animation = "bounce", frames = listOf("/a", "/b"), fps = 6))
        vm.moveSticker("s2", pack.id)
        assertEquals(2, vm.state.value.stickers.count { it.packId == pack.id })
        vm.renameStickerPack(pack.id, "Коты")
        assertEquals("Коты", vm.state.value.stickerPacks.single().name)
        vm.deleteStickerPack(pack.id)
        assertTrue(vm.state.value.stickerPacks.isEmpty())
        assertTrue(vm.state.value.stickers.all { it.packId.isEmpty() })
        assertTrue(vm.state.value.stickers.first { it.id == "s2" }.animated)
    }

    @Test
    fun stickerMotion() {
        assertEquals(1f, StickerAnim.None.pose(0.3f).scale)
        assertEquals(180f, StickerAnim.Spin.pose(0.5f).rotation, 0.01f)
        assertEquals(0.4f, StickerAnim.Shimmer.pose(0.4f).shine, 0.0001f)
        assertTrue(StickerAnim.Bounce.pose(0.5f).dy < 0f)
        assertEquals(StickerAnim.Pulse, StickerAnim.of("pulse"))
        assertEquals(StickerAnim.None, StickerAnim.of("???"))
    }

    // ---------- Фоны и звуки ----------

    @Test
    fun backdrops() {
        val vm = vm()
        assertEquals(BackdropStyle.Cosmos, Backdrop.DEFAULT.kind)
        assertEquals(BackdropStyle.Cosmos, Backdrop(BackdropStyle.Photo.id).kind)
        assertFalse(BackdropStyle.COLLECTION.contains(BackdropStyle.Photo))
        vm.setBackdrop(Backdrop(BackdropStyle.Aurora.id))
        val chat = vm.state.value.chats.first()
        assertEquals(BackdropStyle.Aurora, chat.backdropOr(vm.state.value.backdrop).kind)
        vm.setChatBackdrop(chat.id, Backdrop(BackdropStyle.Prism.id))
        val own = vm.state.value.chats.first { it.id == chat.id }
        assertEquals(BackdropStyle.Prism, own.backdropOr(vm.state.value.backdrop).kind)
        vm.setChatBackdrop(chat.id, null)
        assertEquals(BackdropStyle.Aurora, vm.state.value.chats.first { it.id == chat.id }.backdropOr(vm.state.value.backdrop).kind)
    }

    @Test
    fun everyKindHasItsOwnSound() {
        val defaults = NudgeKind.entries.map { NotifySound.default(it) }
        assertEquals(defaults.size, defaults.toSet().size)
        assertNull(NotifySound.Silent.file)
        assertNull(NotifySound.System.file)
        assertEquals("snd_crystal", NotifySound.Crystal.file)
    }

    // ---------- Демо-жизнь ----------

    @Test
    fun socialEventsAddVotesPrismsOrPlaces() {
        val vm = vm()
        vm.publish("", poll = PollDraft("Куда?", listOf("a", "b")))
        val prisms = vm.state.value.prisms.size
        val messages = vm.state.value.chats.sumOf { it.messages.size }
        val random = Random(7)
        repeat(12) { vm.socialEvent(random) }
        val s = vm.state.value
        val votes = s.posts.first().poll!!.total
        assertTrue(votes > 0 || s.prisms.size > prisms || s.chats.sumOf { it.messages.size } > messages)
        // Закрытый опрос голосов больше не получает
        vm.closePostPoll(s.posts.first().id)
        val closedTotal = vm.state.value.posts.first().poll!!.total
        repeat(12) { vm.socialEvent(random) }
        assertEquals(closedTotal, vm.state.value.posts.first().poll!!.total)
    }
}
