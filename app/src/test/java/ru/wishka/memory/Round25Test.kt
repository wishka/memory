package ru.wishka.memory

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.wishka.memory.data.Demo
import ru.wishka.memory.data.Directory
import ru.wishka.memory.data.NudgeKind
import ru.wishka.memory.data.NudgeTarget
import ru.wishka.memory.data.Nudges
import ru.wishka.memory.data.StateStore
import ru.wishka.memory.data.Story
import ru.wishka.memory.data.net.OpKind
import ru.wishka.memory.data.net.RtEvent
import ru.wishka.memory.i18n.L10n
import ru.wishka.memory.i18n.Lang
import ru.wishka.memory.ui.AppViewModel

/**
 * Раунд 25: подписки, «Интересное», комментарии и истории с сервера, пуши.
 * Здесь — то, что проверяется без сервера; с сервером — интеграционная
 * проверка против server/ (Core).
 */
class Round25Test {
    private var now = 1_800_000_000_000L

    @Before
    fun setUp() {
        L10n.choice = Lang.Ru
        Directory.clear()
        Directory.selfId = 0
    }

    @After
    fun tearDown() {
        Directory.clear()
        Directory.selfId = 0
    }

    @Test
    fun pushesFromServerBecomeNudges() {
        val follower = Nudges.fromPush(mapOf("type" to "new_follower", "actor" to "Лия Ким", "actorId" to "100002"))!!
        assertEquals(NudgeKind.Follows, follower.kind)
        assertEquals("Лия подписалась на вас", follower.text)
        assertEquals(NudgeTarget.Person, follower.target)

        val comment = Nudges.fromPush(mapOf("type" to "comment", "actor" to "Ян", "actorId" to "100003", "postId" to "10000001", "body" to "Класс"))!!
        assertEquals(NudgeKind.Reactions, comment.kind)
        assertEquals("Ян прокомментировал: Класс", comment.text)
        assertEquals(10_000_001L, comment.targetId)

        // Секретная переписка: текста в пуше нет
        val secret = Nudges.fromPush(mapOf("type" to "message", "actor" to "Ян", "chatId" to "5", "body" to ""))!!
        assertEquals("Новое сообщение", secret.text)
    }

    @Test
    fun realtimeFollowAndCommentFrames() {
        val f = RtEvent.parse("""{"type":"follow","user":100002,"state":"request"}""")
        assertEquals(RtEvent.Follow(100002, "request"), f)
        val c = RtEvent.parse("""{"type":"comment","post":10000001,"comment":{"id":7,"author":100002,"text":"Привет"}}""") as RtEvent.NewComment
        assertEquals(10_000_001L, c.postId)
        assertEquals("Привет", c.comment.getString("text"))
        assertEquals(RtEvent.parse(f!!.json().toString()), f)
    }

    @Test
    fun serverStoriesAndCommentsDecode() {
        Directory.selfId = 100_001
        val codec = StateStore.Codec(Demo.me)
        val story = codec.storyOf(JSONObject().put("id", 3).put("author", 100_002).put("createdAt", now).put("text", "Утро").put("bg", 2)
            .put("authorInfo", JSONObject().put("id", 100_002).put("name", "Лия").put("handle", "liya"))
            .put("views", org.json.JSONArray().put(JSONObject().put("user", 100_001).put("at", now))))
        assertEquals(100_002, story.authorId)
        assertEquals("Лия", Demo.person(100_002)?.name)
        // Свой номер на сервере в «кто смотрел» — это я
        assertEquals(Demo.me.id, story.views.single().userId)
        val back: Story = codec.storyOf(codec.storyJson(story))
        assertEquals(story.copy(views = back.views), back)

        val comment = codec.commentOf(JSONObject().put("id", 9).put("author", 100_003).put("text", "Класс")
            .put("authorInfo", JSONObject().put("id", 100_003).put("name", "Ян").put("handle", "yan")))
        assertEquals("Ян", comment.author.name)
        assertEquals(OpKind.CreateStory, OpKind.of("story"))
    }

    @Test
    fun likesAndCommentsReachServerFeedPosts() = runBlocking {
        val vm = AppViewModel(clock = { now }, demoReplies = false)
        vm.loadMoreFeedNow()
        // Запись из ленты сервера (демо-сервер): раньше «нравится» её не меняло
        val older = vm.state.value.olderFeed.first()
        vm.toggleLike(older.id)
        val liked = vm.state.value.olderFeed.first { it.id == older.id }
        assertEquals(!older.liked, liked.liked)
        assertNotNull(vm.state.value.findPost(older.id))
        vm.addComment(older.id, "Отлично")
        val withComment = vm.state.value.findPost(older.id)!!
        assertEquals(older.comments + 1, withComment.comments)
        assertEquals("Отлично", withComment.commentList.last().text)
        vm.deleteComment(older.id, withComment.commentList.last().id)
        assertEquals(older.comments, vm.state.value.findPost(older.id)!!.comments)
    }

    @Test
    fun exploreHidesFriendsAndBlocked() {
        val vm = AppViewModel(clock = { now }, demoReplies = false)
        val s = vm.state.value
        val friend = s.following.first()
        val stranger = Demo.people.first { it.id !in s.following }
        val p1 = Demo.posts.first().copy(id = 50_000_001, author = Demo.person(friend)!!)
        val p2 = Demo.posts.first().copy(id = 50_000_002, author = stranger)
        val state = s.copy(explore = listOf(p1, p2))
        assertEquals(listOf(50_000_002L), state.exploreVisible.map { it.id })
        assertTrue(state.copy(blocked = setOf(stranger.id)).exploreVisible.isEmpty())
    }
}
