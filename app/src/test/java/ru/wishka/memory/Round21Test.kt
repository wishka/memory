package ru.wishka.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.wishka.memory.data.ActiveCall
import ru.wishka.memory.data.ActivityKind
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.Audience
import ru.wishka.memory.data.CallPhase
import ru.wishka.memory.data.Calls
import ru.wishka.memory.data.Demo
import ru.wishka.memory.data.MediaKind
import ru.wishka.memory.data.PhotoTag
import ru.wishka.memory.data.Search
import ru.wishka.memory.data.SharedPlace
import ru.wishka.memory.data.Stories
import ru.wishka.memory.data.Story
import ru.wishka.memory.i18n.L10n
import ru.wishka.memory.i18n.Lang
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.theme.ThemeMode
import ru.wishka.memory.ui.theme.ThemeState

/**
 * Раунд 21: истории (просмотры, ответы, «Актуальное», «Близкие друзья»),
 * репосты и «Отправить в чат», звонки, место и отметки на фото, светлая тема.
 */
class Round21Test {
    private var now = 1_800_000_000_000L
    private fun vm() = AppViewModel(clock = { now }, demoReplies = false)

    @Before
    fun setUp() {
        L10n.choice = Lang.Ru
        ThemeState.mode = ThemeMode.System
        ThemeState.systemDark = true
        ThemeState.stageDark = false
        ThemeState.overlayDark = false
    }

    // ── Светлая тема ─────────────────────────────────────────────────

    @Test
    fun themeFollowsSystemAndChoice() {
        assertFalse(ThemeState.light)
        ThemeState.systemDark = false
        assertTrue(ThemeState.light)
        ThemeState.mode = ThemeMode.Dark
        assertFalse(ThemeState.light)
        ThemeState.mode = ThemeMode.Light
        ThemeState.systemDark = true
        assertTrue(ThemeState.light)
        // Звонок, PRISM, история — всегда ночные, но выбор человека не меняется
        ThemeState.overlayDark = true
        assertFalse(ThemeState.light)
        assertTrue(ThemeState.chosenLight)
        ThemeState.overlayDark = false
        ThemeState.stageDark = true
        assertFalse(ThemeState.light)
        assertEquals(ThemeMode.Light, ThemeMode.of("light"))
        assertEquals(ThemeMode.System, ThemeMode.of("???"))
        assertEquals(ThemeMode.System, ThemeMode.of(null))
    }

    // ── Истории ──────────────────────────────────────────────────────

    @Test
    fun storyLivesADayThenStaysInArchive() {
        val vm = vm()
        assertNotNull(vm.publishStory(null, "   ", 0, closeFriends = false))
        assertNull(vm.publishStory(null, "Привет", 99, closeFriends = false))
        val s = vm.state.value
        val story = s.myActiveStories(now).single()
        assertEquals(Stories.BACKGROUNDS.lastIndex, story.bg)
        now += Stories.TTL_MS + 1
        assertTrue(vm.state.value.myActiveStories(now).isEmpty())
        vm.pruneStories(now)
        // Своя — в архиве и после суток
        assertEquals(listOf(story.id), vm.state.value.storyArchive.map { it.id })
    }

    @Test
    fun closeFriendsStoriesVisibleOnlyToClose() {
        val story = Story(1, authorId = 2, createdAt = now, text = "x", closeFriends = true)
        assertFalse(Stories.visible(story, viewerId = 3, isFriend = true, inAuthorsClose = false))
        assertTrue(Stories.visible(story, viewerId = 3, isFriend = true, inAuthorsClose = true))
        assertFalse(Stories.visible(story.copy(closeFriends = false), viewerId = 3, isFriend = false, inAuthorsClose = false))
        assertTrue(Stories.visible(story, viewerId = 2, isFriend = false, inAuthorsClose = false))
        // Близкими можно сделать только друзей
        val vm = vm()
        val friend = vm.state.value.following.first()
        val stranger = Demo.people.first { it.id !in vm.state.value.following }.id
        vm.toggleCloseFriend(friend)
        vm.toggleCloseFriend(stranger)
        assertEquals(setOf(friend), vm.state.value.closeFriends)
        vm.toggleCloseFriend(friend)
        assertTrue(vm.state.value.closeFriends.isEmpty())
    }

    @Test
    fun ringsPutUnseenFirstAndStartAtFirstUnseen() {
        val people = Demo.people
        val a = people[0].id
        val b = people[1].id
        val stories = listOf(
            Story(1, a, now - 3000), Story(2, a, now - 2000),
            Story(3, b, now - 1000),
            Story(4, b, now - Stories.TTL_MS - 5),
        )
        val rings = Stories.rings(stories, people, meId = -1, seen = setOf(3), now = now)
        assertEquals(listOf(a, b), rings.map { it.user.id })
        assertTrue(rings[1].seen)
        assertEquals(listOf(3L), rings[1].stories.map { it.id })
        assertEquals(1, Stories.startIndex(rings[0].stories, setOf(1)))
        assertEquals(0, Stories.startIndex(rings[0].stories, setOf(1, 2)))
        // Просмотр — один на человека, сердечко не теряется
        var st = Story(9, a, now)
        st = Stories.view(st, 7, now, liked = true)
        st = Stories.view(st, 7, now + 5)
        assertEquals(1, st.views.size)
        assertTrue(st.views.single().liked)
        assertEquals(now, st.views.single().at)
    }

    @Test
    fun viewingRepliesAndLikesGoToChat() {
        val vm = vm()
        vm.ensureDemoStories(kotlin.random.Random(3))
        val circle = vm.state.value.storyCircles(now).first()
        vm.openStories(circle.user.id)
        val story = vm.currentStory()!!
        assertTrue(story.id in vm.state.value.seenStories)
        assertTrue(vm.state.value.story(story.id)!!.views.any { it.userId == vm.state.value.me.id })
        vm.replyToStory(story.id, "Красиво!")
        vm.likeStory(story.id)
        vm.likeStory(story.id) // второй раз — ничего
        val chat = vm.state.value.chats.first { it.peer.id == circle.user.id }
        val replies = chat.messages.filter { it.story?.storyId == story.id }
        assertEquals(listOf("Красиво!", "❤️"), replies.map { it.text })
        // Листаем до конца — просмотр закрывается
        repeat(50) { vm.nextStory() }
        assertNull(vm.state.value.storyViewer)
    }

    @Test
    fun highlightsKeepStoriesAndFollowDeletion() {
        val vm = vm()
        vm.publishStory(null, "Раз", 0, false)
        vm.publishStory(null, "Два", 1, false)
        val ids = vm.state.value.myActiveStories(now).map { it.id }
        assertNull(vm.createHighlight("Чужое", listOf(-5L)))
        val h = vm.createHighlight("  Лето  ", ids)!!
        val me = vm.state.value.me.id
        assertEquals("Лето", vm.state.value.highlightsOf(me).single().title)
        vm.renameHighlight(h, "Очень длинное название подборки историй")
        assertEquals(Stories.MAX_TITLE, vm.state.value.highlightsOf(me).single().title.length)
        // Сутки прошли — истории остаются в подборке и открываются
        now += Stories.TTL_MS * 3
        vm.pruneStories(now)
        vm.openHighlight(h)
        assertEquals(ids.first(), vm.currentStory()?.id)
        vm.closeStories()
        vm.deleteStory(ids[0])
        assertEquals(listOf(ids[1]), vm.state.value.highlightsOf(me).single().storyIds)
        vm.deleteStory(ids[1])
        assertTrue(vm.state.value.highlightsOf(me).isEmpty())
    }

    // ── Репосты ──────────────────────────────────────────────────────

    @Test
    fun repostWrapsOriginalAndCountsIt() {
        val vm = vm()
        val s = vm.state.value
        val original = s.feed.first { it.author.id != s.me.id && !it.author.closed && it.artSeed == null }
        assertNull(vm.repost(original.id))
        val mine = vm.state.value.posts.first()
        assertEquals(original.id, mine.repostOf?.postId)
        assertEquals(1, vm.state.value.findPost(original.id)!!.reposts)
        // Репост репоста без слов — указывает на первоисточник
        assertEquals(original.id, vm.postRef(mine).postId)
        assertNull(vm.repost(mine.id, "Согласен"))
        val second = vm.state.value.posts.first()
        assertEquals(original.id, second.repostOf?.postId)
        assertEquals("Согласен", second.text)
        // Свою запись — только со своими словами
        vm.publish("Моя")
        val own = vm.state.value.posts.first()
        assertNotNull(vm.repost(own.id))
        assertNull(vm.repost(own.id, "Напоминаю"))
        // Поиск находит и по тексту оригинала
        assertTrue(Search.posts(original.text.split(' ').first { it.length > 3 }, vm.state.value.posts).any { it.repostOf != null })
    }

    @Test
    fun sendPostToChatLeavesCard() {
        val vm = vm()
        val post = vm.state.value.feed.first()
        val chat = vm.state.value.chats.first()
        vm.openSendToChat(post.id)
        assertEquals(post.id, vm.state.value.sharingPostId)
        vm.sendPostToChat(post.id, chat.id, "Смотри")
        assertNull(vm.state.value.sharingPostId)
        val m = vm.state.value.chats.first { it.id == chat.id }.messages.last()
        assertEquals(post.id, m.post?.postId)
        assertEquals("Смотри", m.text)
        assertTrue(vm.back().not() || vm.state.value.sharingPostId == null)
    }

    // ── Звонки ───────────────────────────────────────────────────────

    @Test
    fun outgoingCallLifecycle() {
        val vm = vm()
        val friend = vm.state.value.following.first()
        assertNull(vm.startCall(friend, video = true))
        assertNotNull(vm.startCall(friend, video = false)) // уже идёт
        assertEquals(CallPhase.Calling, vm.state.value.call!!.phase)
        // «Назад» звонок не сбрасывает
        assertTrue(vm.back())
        assertNotNull(vm.state.value.call)
        vm.callAnswered()
        now += 125_000
        vm.toggleMute()
        vm.flipCamera()
        assertTrue(vm.state.value.call!!.muted)
        assertFalse(vm.state.value.call!!.frontCamera)
        vm.endCall()
        assertNull(vm.state.value.call)
        val r = vm.state.value.callLog.single()
        assertTrue(r.outgoing && r.video && !r.missed)
        assertEquals(125_000L, r.durationMs)
        assertEquals("Видеозвонок · 2:05", Calls.title(r))
        val chat = vm.state.value.chats.first { it.peer.id == friend }
        assertEquals(r.id, chat.messages.last().call?.id)
    }

    @Test
    fun incomingCallRespectsPrivacyAndMissedGoesToActivity() {
        val vm = vm()
        val friend = vm.state.value.following.first()
        val stranger = Demo.people.first { it.id !in vm.state.value.following }.id
        vm.setPrivacy(vm.state.value.privacy.copy(calls = Audience.Following))
        vm.incomingCall(stranger, video = false)
        assertNull(vm.state.value.call)
        vm.incomingCall(friend, video = false)
        assertEquals(CallPhase.Incoming, vm.state.value.call!!.phase)
        vm.callTimeout()
        val r = vm.state.value.callLog.single()
        assertTrue(r.missed)
        assertEquals(ActivityKind.MissedCall, vm.state.value.activity.first().kind)
        assertTrue(Calls.title(r).startsWith("Пропущенный"))
        // Отклонённый
        vm.incomingCall(friend, video = true)
        vm.declineCall()
        assertTrue(vm.state.value.callLog.first().declined)
        // Никому
        vm.setPrivacy(vm.state.value.privacy.copy(calls = Audience.Nobody))
        vm.incomingCall(friend, video = true)
        assertNull(vm.state.value.call)
        assertFalse(Calls.allowed(friend, setOf(friend), setOf(friend), Audience.Everyone))
        vm.clearCallLog()
        assertTrue(vm.state.value.callLog.isEmpty())
    }

    @Test
    fun activeCallDuration() {
        val c = ActiveCall(1, 2, video = false, outgoing = true, phase = CallPhase.Calling, startedAt = 100)
        assertEquals(0, c.durationMs(5000))
        assertEquals(4000, c.copy(phase = CallPhase.Active, connectedAt = 1000).durationMs(5000))
        assertEquals("call-1", c.room)
    }

    // ── Место и отметки ──────────────────────────────────────────────

    @Test
    fun publishKeepsPlaceAndOnlyFriendsTags() {
        val vm = vm()
        val friend = vm.state.value.following.first()
        val stranger = Demo.people.first { it.id !in vm.state.value.following }.id
        val photo = Attachment("p1", MediaKind.Photo, "/tmp/x.jpg", tags = listOf(PhotoTag(friend, 0.3f, 0.4f), PhotoTag(stranger, 0.5f, 0.5f)))
        vm.publish("У моря", listOf(photo), place = SharedPlace(40.18, 44.51, "Ереван"))
        val p = vm.state.value.posts.first()
        assertEquals("Ереван", p.place?.title)
        assertEquals(listOf(friend), p.media.single().tags.map { it.userId })
        assertTrue(Search.posts("ереван", vm.state.value.posts).any { it.id == p.id })
    }

    @Test
    fun taggedSectionShowsPostsWithMe() {
        val vm = vm()
        val s = vm.state.value
        val friendPost = s.feed.first { it.author.id != s.me.id }
        val tagged = friendPost.copy(media = listOf(Attachment("t", MediaKind.Photo, "/tmp/t.jpg", tags = listOf(PhotoTag(s.me.id, 0.5f, 0.5f)))))
        vm.restore(vm.snapshot().copy(posts = vm.snapshot().posts.map { if (it.id == friendPost.id) tagged else it }))
        assertEquals(listOf(friendPost.id), vm.state.value.taggedPosts.map { it.id })
        assertEquals(vm.state.value.taggedPosts, vm.taggedPosts)
        vm.untagMe(friendPost.id)
        assertTrue(vm.state.value.taggedPosts.isEmpty())
    }

    // ── Сохранение ───────────────────────────────────────────────────

    @Test
    fun snapshotKeepsRound21State() {
        val vm = vm()
        vm.publishStory(null, "Сохрани меня", 2, closeFriends = true)
        val story = vm.state.value.myActiveStories(now).single()
        vm.createHighlight("Лучшее", listOf(story.id))
        vm.toggleCloseFriend(vm.state.value.following.first())
        vm.startCall(vm.state.value.following.first(), video = false)
        vm.endCall()
        val vm2 = vm()
        vm2.restore(vm.snapshot())
        val a = vm.state.value
        val b = vm2.state.value
        assertEquals(a.stories, b.stories)
        assertEquals(a.highlights, b.highlights)
        assertEquals(a.closeFriends, b.closeFriends)
        assertEquals(a.callLog, b.callLog)
        assertTrue(b.stories.single().closeFriends)
    }
}
