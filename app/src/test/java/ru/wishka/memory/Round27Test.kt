package ru.wishka.memory

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.Demo
import ru.wishka.memory.data.Group
import ru.wishka.memory.data.GroupInfo
import ru.wishka.memory.data.MediaKind
import ru.wishka.memory.data.Post
import ru.wishka.memory.data.Prism
import ru.wishka.memory.data.Product
import ru.wishka.memory.data.StateStore
import ru.wishka.memory.data.net.MemoryCache
import ru.wishka.memory.data.net.Net
import ru.wishka.memory.data.net.OpKind
import ru.wishka.memory.data.net.Outbox
import ru.wishka.memory.data.net.OutboxOp
import ru.wishka.memory.i18n.L10n
import ru.wishka.memory.i18n.Lang
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.UiState
import java.nio.file.Files

/**
 * Раунд 27: сообщества, PRISM и «Актуальное» на сервере. Здесь — то, что
 * проверяется без сервера; с сервером — интеграционная проверка против server/.
 */
class Round27Test {
    private var now = 1_800_000_000_000L

    @Before
    fun setUp() {
        L10n.choice = Lang.Ru
    }

    private val codec = StateStore.Codec(Demo.me)

    @Test
    fun groupAndPrismSurviveJson() {
        val cover = Attachment("c1", MediaKind.Photo, "/v1/uploads/abc")
        val g = Group(50_000_007, "Читатели", "о книгах", "Книги", 3, 7, joined = true, cover = cover,
            info = GroupInfo(website = "https://ex.am"), shop = listOf(Product(1, "Книга", 1500, photos = listOf(cover))), accent = "mint")
        val back = codec.groupOf(codec.groupJson(g))
        assertEquals(g, back)
        val p = Prism(10_000_001, Demo.me, "Закат", "трек", 3, 1, 0, video = Attachment("v1", MediaKind.Video, "/v1/uploads/v"), createdAt = now)
        assertEquals(p, codec.prismOf(codec.prismJson(p)))
    }

    @Test
    fun communityBodyKeepsOnlyWhatServerStores() {
        val net = Net.demo({ now }, { Demo.me })
        val g = Group(50_000_007, "Читатели", "о книгах", "Книги", 3, 7, joined = true, mine = true, ownerId = 1,
            posts = listOf(Post(1, Demo.me, "x", 0)))
        val body = net.api.communityBody(g)
        for (k in listOf("posts", "members", "joined", "mine", "muted", "ownerId", "id")) assertFalse(k, body.has(k))
        assertEquals("Читатели", body.getString("name"))
        assertEquals("о книгах", body.getString("description"))
    }

    @Test
    fun communityPhotosUploadOnce() {
        val tmp = Files.createTempDirectory("r27").toFile()
        val net = Net.demo({ now }, { Demo.me })
        val local = java.io.File(tmp, "cover.jpg").apply { writeBytes(ByteArray(10)) }
        // Файл с сервера — второй раз не загружается; свой новый — загружается
        assertEquals("/v1/uploads/abc", net.remoteFor("/v1/uploads/abc"))
        assertNull(net.remoteFor(local.path))
        val g = Group(50_000_007, "Читатели", "", "Книги", 1, 7, mine = true,
            cover = Attachment("c1", MediaKind.Photo, local.path),
            shop = listOf(Product(1, "Книга", photos = listOf(Attachment("p1", MediaKind.Photo, "/v1/uploads/old")))))
        val key = net.queueCommunity(g)
        assertEquals(listOf("c1"), net.uploads.all().filter { it.owner == key }.map { it.attachmentId })
        val op = net.outbox.get(key)!!
        assertEquals(50_000_007L, op.target)
        assertTrue(JSONObject(op.payload).getJSONArray("posts").length() == 0)
    }

    @Test
    fun personPostsShowOnTheirPage() {
        val person = Demo.people.first()
        val theirs = Post(10_000_123, person, "с сервера", 0)
        val s = UiState(posts = emptyList(), personPosts = mapOf(person.id to listOf(theirs)))
        assertEquals(listOf(theirs), s.postsOf(person.id))
        assertEquals(theirs, s.findPost(theirs.id))
    }

    @Test
    fun demoGroupsKeepSmallIds() {
        val vm = AppViewModel(clock = { now }, demoReplies = false)
        assertNull(vm.createGroup("Новая группа", "", "Книги"))
        val g = vm.state.value.groups.first { it.name == "Новая группа" }
        assertTrue(g.id <= AppViewModel.COMMUNITY_ID_BASE)
        assertTrue(g.mine && g.joined)
    }

    @Test
    fun communitySavesGoInOrder() = kotlinx.coroutines.runBlocking {
        val outbox = Outbox(MemoryCache()) { now }
        outbox.enqueue(OutboxOp("a", OpKind.SaveCommunity, 50_000_007, "{}"))
        now += 1
        outbox.enqueue(OutboxOp("b", OpKind.SaveCommunity, 50_000_007, "{}"))
        outbox.enqueue(OutboxOp("c", OpKind.SaveCommunity, 50_000_008, "{}"))
        val sent = mutableListOf<String>()
        // «a» ждёт загрузку фото — «b» той же группы не обгоняет, другая группа уходит
        outbox.flush(ready = { it.key != "a" }, failedDeps = { false }, send = { null }, onSent = { op, _ -> sent += op.key }, onFailed = { _, _ -> })
        assertEquals(listOf("c"), sent)
        outbox.flush(ready = { true }, failedDeps = { false }, send = { null }, onSent = { op, _ -> sent += op.key }, onFailed = { _, _ -> })
        assertEquals(listOf("c", "a", "b"), sent)
    }

    @Test
    fun discardedPendingPostNeverLeaves() {
        val net = Net.demo({ now }, { Demo.me })
        val key = net.queuePost(Post(5, Demo.me, "передумаю", 0))
        net.discard(key)
        assertNull(net.outbox.get(key))
    }
}
