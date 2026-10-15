package ru.wishka.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.MediaKind
import ru.wishka.memory.data.ProfileInfo
import ru.wishka.memory.data.minutesSince
import ru.wishka.memory.data.sizeTitle
import ru.wishka.memory.data.agoTitle
import ru.wishka.memory.data.plural
import ru.wishka.memory.data.Audience
import ru.wishka.memory.data.Aspect
import ru.wishka.memory.data.ColorMatrix
import ru.wishka.memory.data.MusicTrack
import ru.wishka.memory.data.MusicCatalog
import ru.wishka.memory.data.Sticker
import ru.wishka.memory.data.StickerKind
import ru.wishka.memory.ui.profile.achievements
import ru.wishka.memory.ui.profile.activity
import ru.wishka.memory.data.VideoEdit
import ru.wishka.memory.data.VideoFilter
import ru.wishka.memory.data.Notifications
import ru.wishka.memory.data.Privacy
import ru.wishka.memory.i18n.L10n
import ru.wishka.memory.i18n.Lang
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.ProfileSection
import ru.wishka.memory.ui.SettingsPage
import ru.wishka.memory.ui.Tab
import ru.wishka.memory.ui.profile.CropGeometry
import androidx.compose.ui.geometry.Offset
import org.junit.Before

class AppViewModelTest {

    @Before
    fun russian() {
        // Строки в проверках — русские, независимо от языка машины
        L10n.choice = Lang.Ru
    }

    @Test
    fun likeTogglesCount() {
        val vm = AppViewModel()
        val post = vm.state.value.posts.first { !it.liked }
        vm.toggleLike(post.id)
        val liked = vm.state.value.posts.first { it.id == post.id }
        assertTrue(liked.liked)
        assertEquals(post.likes + 1, liked.likes)
        vm.toggleLike(post.id)
        assertEquals(post.likes, vm.state.value.posts.first { it.id == post.id }.likes)
    }

    @Test
    fun publishPutsPostOnTopAndInProfile() {
        val vm = AppViewModel()
        vm.publish("  Первый момент  ")
        val top = vm.state.value.posts.first()
        assertEquals("Первый момент", top.text)
        assertEquals(vm.state.value.me.id, top.author.id)
        assertEquals(listOf(top), vm.state.value.myPosts)
        vm.publish("   ")
        assertEquals(1, vm.state.value.myPosts.size)
    }

    @Test
    fun openingChatClearsUnreadAndBackCloses() {
        val vm = AppViewModel()
        val chat = vm.state.value.chats.first { it.unread > 0 }
        vm.openChat(chat.id)
        assertEquals(Tab.Chats, vm.state.value.tab)
        assertEquals(0, vm.state.value.chats.first { it.id == chat.id }.unread)
        assertTrue(vm.back())
        assertNull(vm.state.value.openChatId)
        assertTrue(vm.back())                 // с «Чатов» — на ленту
        assertEquals(Tab.Feed, vm.state.value.tab)
        assertFalse(vm.back())                // с ленты — выход
    }

    @Test
    fun renameShowsOnOwnPosts() {
        val vm = AppViewModel()
        vm.publish("Запись")
        vm.updateProfile("Новое имя", ProfileInfo(bio = "о себе", city = "Казань", interests = listOf("Фото")))
        assertEquals("Новое имя", vm.state.value.me.name)
        assertEquals("Новое имя", vm.state.value.myPosts.first().author.name)
        assertEquals("Казань", vm.state.value.me.city)
        assertEquals(listOf("Фото"), vm.state.value.me.interests)
        vm.updateProfile("  ", ProfileInfo())
        assertEquals("Новое имя", vm.state.value.me.name)
    }

    @Test
    fun storiesShowOnlyFollowed() {
        val vm = AppViewModel(demoReplies = false)
        val s = vm.state.value
        assertTrue(s.friendPosts.isNotEmpty())
        assertTrue(s.friendPosts.all { it.author.id in s.following })
        // Заявка Тимуру (5): пока не принята — его записей нет; принял — есть;
        // удалили из друзей — снова нет
        assertFalse(s.friendPosts.any { it.author.id == 5 })
        vm.toggleFriend(5)
        assertTrue(5 in vm.state.value.sentRequests)
        assertFalse(vm.state.value.friendPosts.any { it.author.id == 5 })
        vm.acceptSentRequest(5)
        assertTrue(vm.state.value.friendPosts.any { it.author.id == 5 })
        vm.toggleFriend(5)
        assertFalse(5 in vm.state.value.following)
        assertFalse(vm.state.value.friendPosts.any { it.author.id == 5 })
        // Себя в друзья не добавить
        vm.toggleFriend(vm.state.value.me.id)
        assertFalse(vm.state.value.me.id in vm.state.value.following)
        assertFalse(vm.state.value.me.id in vm.state.value.sentRequests)
    }

    @Test
    fun accountNameAndReset() {
        val vm = AppViewModel()
        vm.setAccount("vera.moon", "Вера")
        vm.publish("Привет")
        assertEquals("Вера", vm.state.value.myPosts.first().author.name)
        assertEquals("vera.moon", vm.state.value.me.handle)
        vm.reset()
        assertTrue(vm.state.value.myPosts.isEmpty())
    }

    @Test
    fun personPageAndBackOrder() {
        val vm = AppViewModel()
        vm.selectTab(Tab.Stories)
        vm.openPerson(2)
        assertEquals("Вера Лунная", vm.state.value.openPerson?.name)
        // «Написать» открывает переписку поверх страницы, «назад» возвращает на неё
        vm.writeTo(2)
        assertEquals(2, vm.state.value.openChat?.peer?.id)
        assertTrue(vm.back())
        assertEquals(2, vm.state.value.openPersonId)
        assertTrue(vm.back())
        assertNull(vm.state.value.openPersonId)
        // Своё имя ведёт в профиль
        vm.openPerson(vm.state.value.me.id)
        assertEquals(Tab.Profile, vm.state.value.tab)
        assertNull(vm.state.value.openPersonId)
    }

    @Test
    fun writeToNewPersonCreatesChat() {
        val vm = AppViewModel()
        val before = vm.state.value.chats.size
        vm.writeTo(5) // с Тимуром переписки ещё нет
        assertEquals(before + 1, vm.state.value.chats.size)
        assertEquals(5, vm.state.value.openChat?.peer?.id)
        vm.writeTo(5) // повторно — та же переписка
        assertEquals(before + 1, vm.state.value.chats.size)
    }

    @Test
    fun groupsJoinCreatePostDelete() {
        val vm = AppViewModel()
        val other = vm.state.value.groups.first { !it.joined }
        vm.toggleGroup(other.id)
        assertTrue(vm.state.value.groups.first { it.id == other.id }.joined)
        assertEquals(other.members + 1, vm.state.value.groups.first { it.id == other.id }.members)

        assertEquals("Название — от 3 символов", vm.createGroup("ab", "", "Фото"))
        assertNull(vm.createGroup("Кофе и звёзды", "Ночные разговоры", "Природа"))
        val mine = vm.state.value.openGroup!!
        assertTrue(mine.mine && mine.joined)
        assertTrue(vm.createGroup("кофе и звёзды", "", "Фото")!!.contains("уже есть"))

        vm.postInGroup(mine.id, "Первая запись")
        assertEquals("Первая запись", vm.state.value.openGroup!!.posts.first().text)
        // Из своей группы не выйти — только удалить
        vm.toggleGroup(mine.id)
        assertTrue(vm.state.value.openGroup!!.joined)
        vm.deleteGroup(mine.id)
        assertTrue(vm.state.value.groups.none { it.id == mine.id })
    }

    @Test
    fun cannotPostInGroupWithoutJoining() {
        val vm = AppViewModel()
        val other = vm.state.value.groups.first { !it.joined }
        vm.postInGroup(other.id, "привет")
        assertEquals(other.posts, vm.state.value.groups.first { it.id == other.id }.posts)
    }

    @Test
    fun prismLikeSaveAndComments() {
        val vm = AppViewModel()
        val prism = vm.state.value.prisms.first { !it.liked }
        vm.togglePrismLike(prism.id)
        assertEquals(prism.likes + 1, vm.state.value.findPrism(prism.id)!!.likes)
        // «Сохранить» — закладка в профиле, повторно — убрать
        assertTrue(vm.state.value.savedPrisms.isEmpty())
        vm.togglePrismSave(prism.id)
        assertEquals(listOf(prism.id), vm.state.value.savedPrisms.map { it.id })
        vm.togglePrismSave(prism.id)
        assertTrue(vm.state.value.savedPrisms.isEmpty())
        // Комментарий: в обсуждении и в счётчике; свой можно удалить
        vm.addPrismComment(prism.id, "  Красиво  ")
        val withComment = vm.state.value.findPrism(prism.id)!!
        assertEquals(prism.comments + 1, withComment.comments)
        assertEquals("Красиво", withComment.commentList.last().text)
        vm.deletePrismComment(prism.id, withComment.commentList.last().id)
        assertEquals(prism.comments, vm.state.value.findPrism(prism.id)!!.comments)
        // Открыть из «Сохранённого» — вкладка PRISM на нужном ролике
        vm.openPrism(prism.id)
        assertEquals(Tab.Prism, vm.state.value.tab)
        assertEquals(prism.id, vm.state.value.prismStartId)
    }

    @Test
    fun commentsOnFeedAndGroupPosts() {
        val vm = AppViewModel(clock = { 1_000_000L })
        val post = vm.state.value.posts.first()
        vm.addComment(post.id, "Отличный кадр")
        vm.addComment(post.id, "   ")
        val updated = vm.state.value.findPost(post.id)!!
        assertEquals(post.comments + 1, updated.comments)
        assertEquals(post.commentList.size + 1, updated.commentList.size)
        assertEquals(vm.state.value.me.id, updated.commentList.last().author.id)
        // Чужой комментарий удалить нельзя, свой — можно
        post.commentList.firstOrNull()?.let { vm.deleteComment(post.id, it.id) }
        assertEquals(post.commentList.size + 1, vm.state.value.findPost(post.id)!!.commentList.size)
        vm.deleteComment(post.id, updated.commentList.last().id)
        assertEquals(post.commentList, vm.state.value.findPost(post.id)!!.commentList)
        // Запись в группе обсуждается так же
        val groupPost = vm.state.value.groups.first { it.posts.isNotEmpty() }.posts.first()
        vm.addComment(groupPost.id, "И я там был")
        assertEquals("И я там был", vm.state.value.findPost(groupPost.id)!!.commentList.last().text)
    }

    @Test
    fun publishWithMediaOnly() {
        val vm = AppViewModel()
        val photo = Attachment("a", MediaKind.Photo, "/tmp/a.jpg")
        vm.publish("", listOf(photo))
        val top = vm.state.value.posts.first()
        assertEquals(listOf(photo), top.media)
        assertEquals("", top.text)
        // Совсем пустую запись не публикуем
        val count = vm.state.value.posts.size
        vm.publish("  ", emptyList())
        assertEquals(count, vm.state.value.posts.size)
    }

    @Test
    fun sendAttachmentsAndDeleteMessages() {
        val vm = AppViewModel(demoReplies = false)
        val chat = vm.state.value.chats.first()
        val doc = Attachment("d", MediaKind.Document, "/tmp/d.pdf", "Договор.pdf", 2048, "application/pdf")
        vm.send(chat.id, "", listOf(doc))
        val sent = vm.state.value.chats.first { it.id == chat.id }.messages.last()
        assertEquals(listOf(doc), sent.attachments)
        // Удалить у себя
        vm.deleteMessage(chat.id, sent.id, forEveryone = false)
        assertTrue(vm.state.value.chats.first { it.id == chat.id }.messages.none { it.id == sent.id })
        assertFalse(sent.id in vm.deletedForEveryone)
        // Удалить у обоих — запоминается для отправки на сервер
        val other = vm.state.value.chats.first { it.id == chat.id }.messages.first()
        vm.deleteMessage(chat.id, other.id, forEveryone = true)
        assertTrue(other.id in vm.deletedForEveryone)
        assertTrue(vm.state.value.chats.first { it.id == chat.id }.messages.none { it.id == other.id })
    }

    @Test
    fun snapshotRestoresAndKeepsCurrentName() {
        val vm = AppViewModel()
        vm.setAccount("vera.moon", "Вера")
        vm.publish("Мой момент")
        vm.togglePrismSave(vm.state.value.prisms.first().id)
        val snapshot = vm.snapshot()

        val next = AppViewModel()
        next.setAccount("vera.moon", "Вера Новая")
        next.restore(snapshot)
        assertEquals("Мой момент", next.state.value.myPosts.first().text)
        assertEquals("Вера Новая", next.state.value.myPosts.first().author.name)
        assertEquals(1, next.state.value.savedPrisms.size)
        // Новые номера не совпадают с сохранёнными
        next.publish("Ещё")
        assertEquals(2, next.state.value.myPosts.map { it.id }.toSet().size)
    }

    @Test
    fun personFromChatOpensAtOnceAndBackReturnsToChat() {
        val vm = AppViewModel()
        val chat = vm.state.value.chats.first()
        vm.openChat(chat.id)
        vm.openPerson(chat.peer.id)
        // Страница человека сразу на экране — переписка её не закрывает
        assertNull(vm.state.value.openChat)
        assertEquals(chat.peer.id, vm.state.value.openPerson?.id)
        assertTrue(vm.back())
        assertEquals(chat.id, vm.state.value.openChatId)
        assertNull(vm.state.value.openPersonId)
    }

    @Test
    fun replyAndForward() {
        val vm = AppViewModel(demoReplies = false)
        val chat = vm.state.value.chats.first()
        val original = chat.messages.first { !it.fromMe }
        val ref = vm.replyRef(chat.id, original)
        assertEquals(chat.peer.name, ref.author)
        vm.send(chat.id, "Отвечаю", replyTo = ref)
        val reply = vm.state.value.chats.first { it.id == chat.id }.messages.last()
        assertEquals(original.id, reply.replyTo?.messageId)
        // Переслать в другую переписку — с пометкой автора, и она откроется
        val target = vm.state.value.chats.first { it.id != chat.id }
        vm.forward(chat.id, original.id, target.id)
        val forwarded = vm.state.value.chats.first { it.id == target.id }.messages.last()
        assertEquals(original.text, forwarded.text)
        assertEquals(chat.peer.name, forwarded.forwardedFrom)
        assertTrue(forwarded.fromMe)
        assertEquals(target.id, vm.state.value.openChatId)
    }

    @Test
    fun profileSectionsAndBack() {
        val vm = AppViewModel()
        assertTrue(vm.state.value.friends.isNotEmpty())
        vm.openProfileSection(ProfileSection.Groups)
        assertEquals(Tab.Profile, vm.state.value.tab)
        // Группа из раздела профиля: «назад» — снова в раздел
        vm.openGroup(vm.state.value.myGroups.first().id)
        assertEquals(Tab.Profile, vm.state.value.tab)
        assertTrue(vm.back())
        assertEquals(ProfileSection.Groups, vm.state.value.profileSection)
        assertTrue(vm.back())
        assertNull(vm.state.value.profileSection)
        // Другая вкладка — раздел закрывается
        vm.openProfileSection(ProfileSection.Saved)
        vm.selectTab(Tab.Feed)
        assertNull(vm.state.value.profileSection)
    }

    @Test
    fun pinAndUnpin() {
        val vm = AppViewModel()
        val chat = vm.state.value.chats.first()
        val message = chat.messages.first()
        vm.togglePin(chat.id, message.id)
        assertEquals(message, vm.state.value.chats.first { it.id == chat.id }.pinned)
        // Повторно — открепить
        vm.togglePin(chat.id, message.id)
        assertNull(vm.state.value.chats.first { it.id == chat.id }.pinnedId)
        // Удалили закреплённое — закрепление снимается
        vm.togglePin(chat.id, message.id)
        vm.deleteMessage(chat.id, message.id, forEveryone = false)
        assertNull(vm.state.value.chats.first { it.id == chat.id }.pinnedId)
    }

    @Test
    fun overlaysCloseFirstOnBack() {
        val vm = AppViewModel()
        val post = vm.state.value.posts.first()
        vm.openComments(post.id)
        vm.openMedia(Attachment("p", MediaKind.Photo, "/tmp/p.jpg"))
        assertTrue(vm.back())
        assertNull(vm.state.value.viewing)
        assertEquals(post.id, vm.state.value.commentsPostId)
        assertTrue(vm.back())
        assertNull(vm.state.value.commentsPostId)
    }

    @Test
    fun texts() {
        assertEquals("5 мин", agoTitle(5))
        assertEquals("2 ч", agoTitle(125))
        assertEquals("вчера", agoTitle(60 * 30))
        assertEquals("21 запись", plural(21, "запись", "записи", "записей"))
        assertEquals("12 записей", plural(12, "запись", "записи", "записей"))
        assertEquals("2,0 МБ", sizeTitle(2L * 1024 * 1024))
        assertEquals("12 КБ", sizeTitle(12 * 1024))
        // Время своей записи считается от момента публикации
        assertEquals(3, minutesSince(createdAt = 0, minutesAgo = 3, now = 999))
        assertEquals(5, minutesSince(createdAt = 60_000, minutesAgo = 0, now = 6 * 60_000))
    }

    @Test
    fun commentLikes() {
        val vm = AppViewModel()
        val post = vm.state.value.posts.first { it.commentList.isNotEmpty() }
        val comment = post.commentList.first()
        vm.toggleCommentLike(post.id, comment.id)
        val liked = vm.state.value.findPost(post.id)!!.commentList.first { it.id == comment.id }
        assertTrue(liked.liked)
        assertEquals(comment.likes + 1, liked.likes)
        vm.toggleCommentLike(post.id, comment.id)
        assertEquals(comment.likes, vm.state.value.findPost(post.id)!!.commentList.first { it.id == comment.id }.likes)
        // И у PRISM
        val prism = vm.state.value.prisms.first { it.commentList.isNotEmpty() }
        val pc = prism.commentList.first()
        vm.togglePrismCommentLike(prism.id, pc.id)
        assertTrue(vm.state.value.findPrism(prism.id)!!.commentList.first { it.id == pc.id }.liked)
    }

    @Test
    fun settingsNavigation() {
        val vm = AppViewModel()
        vm.openSettings()
        assertEquals(Tab.Profile, vm.state.value.tab)
        assertEquals(SettingsPage.Main, vm.state.value.settingsPage)
        vm.openSettings(SettingsPage.Privacy)
        // «Назад»: со страницы — в список, из списка — в профиль
        assertTrue(vm.back())
        assertEquals(SettingsPage.Main, vm.state.value.settingsPage)
        assertTrue(vm.back())
        assertNull(vm.state.value.settingsPage)
        // Вкладка внизу закрывает настройки
        vm.openSettings(SettingsPage.Language)
        vm.selectTab(Tab.Profile)
        assertNull(vm.state.value.settingsPage)
    }

    @Test
    fun personSections() {
        val vm = AppViewModel(demoReplies = false)
        val person = vm.state.value.posts.first { it.author.id != vm.state.value.me.id }.author
        vm.openPerson(person.id)
        vm.openPersonSection(ProfileSection.Friends)
        assertEquals(ProfileSection.Friends, vm.state.value.personSection)
        // «Назад» сначала закрывает раздел, потом страницу
        assertTrue(vm.back())
        assertNull(vm.state.value.personSection)
        assertEquals(person.id, vm.state.value.openPersonId)
        // Друзья — вы среди его друзей
        if (person.id !in vm.state.value.following) {
            vm.toggleFriend(person.id)
            vm.acceptSentRequest(person.id)
        }
        assertTrue(vm.state.value.friendsOf(person).any { it.id == vm.state.value.me.id })
        assertTrue(vm.state.value.friendsOf(person).none { it.id == person.id })
        // Удалили из друзей — вас в его списке нет
        vm.removeFriend(person.id)
        assertTrue(vm.state.value.friendsOf(person).none { it.id == vm.state.value.me.id })
    }

    @Test
    fun blockHidesPostsAndUnfollows() {
        val vm = AppViewModel()
        val author = vm.state.value.friendPosts.first().author
        vm.block(author.id)
        val s = vm.state.value
        assertTrue(s.isBlocked(author.id))
        assertFalse(author.id in s.following)
        assertTrue(s.feed.none { it.author.id == author.id })
        assertTrue(s.friendPosts.none { it.author.id == author.id })
        assertEquals(listOf(author.id), s.blockedPeople.map { it.id })
        vm.unblock(author.id)
        assertTrue(vm.state.value.feed.any { it.author.id == author.id })
        // Себя заблокировать нельзя
        vm.block(vm.state.value.me.id)
        assertTrue(vm.state.value.blocked.isEmpty())
    }

    @Test
    fun chatListMenu() {
        val vm = AppViewModel()
        val chats = vm.state.value.chats
        val last = chats.last()
        vm.toggleChatOnTop(last.id)
        assertEquals(last.id, vm.state.value.chatList.first().id)
        // Без звука — не в счётчике вкладки
        val unread = chats.first { it.unread > 0 }
        val before = vm.state.value.unreadTotal
        vm.toggleChatMute(unread.id)
        assertEquals(before - unread.unread, vm.state.value.unreadTotal)
        vm.markRead(unread.id)
        assertEquals(0, vm.state.value.chats.first { it.id == unread.id }.unread)
        vm.markUnread(unread.id)
        assertEquals(1, vm.state.value.chats.first { it.id == unread.id }.unread)
        vm.clearHistory(last.id)
        assertTrue(vm.state.value.chats.first { it.id == last.id }.messages.isEmpty())
        vm.openChat(last.id)
        vm.deleteChat(last.id)
        assertTrue(vm.state.value.chats.none { it.id == last.id })
        assertNull(vm.state.value.openChatId)
    }

    @Test
    fun groupMuteAndSettingsSurviveSnapshot() {
        val vm = AppViewModel()
        val group = vm.state.value.groups.first()
        vm.toggleGroupMute(group.id)
        vm.setPrivacy(Privacy(messages = Audience.Following, showOnline = false))
        vm.setNotifications(Notifications(all = false))
        vm.block(vm.state.value.friendPosts.first().author.id)
        vm.setAvatar("/tmp/a.jpg")
        val snapshot = vm.snapshot()
        val other = AppViewModel()
        other.restore(snapshot)
        val s = other.state.value
        assertTrue(s.groups.first { it.id == group.id }.muted)
        assertEquals(Audience.Following, s.privacy.messages)
        assertFalse(s.privacy.showOnline)
        assertFalse(s.notifications.all)
        assertEquals(1, s.blocked.size)
        assertEquals("/tmp/a.jpg", vm.profileInfo().avatar)
    }

    @Test
    fun cropKeepsCircleCovered() {
        // Фото 2000 × 1000, круг 500 px: при zoom 1 квадрат — вся высота
        val g = CropGeometry(2000, 1000, 500f)
        val (x, y, side) = g.sourceSquare(1f, Offset(0f, 0f))
        assertEquals(1000, side)
        assertEquals(500, x)
        assertEquals(0, y)
        // Сдвиг дальше края не уводит квадрат за фото
        val (x2, _, _) = g.sourceSquare(1f, Offset(10_000f, 0f))
        assertEquals(0, x2)
        // Увеличение вдвое — квадрат вдвое меньше
        assertEquals(500, g.sourceSquare(2f, Offset(0f, 0f)).third)
    }

    @Test
    fun englishTexts() {
        L10n.choice = Lang.En
        try {
            assertEquals("5 min", agoTitle(5))
            assertEquals("1 post", plural(1, "запись", "записи", "записей"))
            assertEquals("21 posts", plural(21, "запись", "записи", "записей"))
            assertEquals("2.0 MB", sizeTitle(2L * 1024 * 1024))
        } finally {
            L10n.choice = Lang.Ru
        }
    }

    @Test
    fun publishAndDeleteOwnPrism() {
        val vm = AppViewModel()
        val video = Attachment("v", MediaKind.Video, "/tmp/v.mp4")
        vm.openPrismCreator()
        assertTrue(vm.state.value.creatingPrism)
        vm.publishPrism(video, null, "  Закат  ", "", 12_000)
        val s = vm.state.value
        val top = s.prisms.first()
        assertFalse(s.creatingPrism)
        assertEquals(Tab.Prism, s.tab)
        assertEquals("Закат", top.caption)
        assertEquals(video, top.video)
        assertEquals(top.id, s.prismStartId)
        assertTrue(top.music.contains(s.me.name))
        // Чужой ролик удалить нельзя, свой — можно
        val other = s.prisms.first { it.author.id != s.me.id }
        vm.deletePrism(other.id)
        assertTrue(vm.state.value.prisms.any { it.id == other.id })
        vm.deletePrism(top.id)
        assertTrue(vm.state.value.prisms.none { it.id == top.id })
    }

    @Test
    fun musicLibraryAndPlaylists() {
        val vm = AppViewModel()
        val a = MusicTrack("a", "Луна", "Вера", "/tmp/a.mp3", 180_000)
        val b = MusicTrack("b", "Море", "", "/tmp/b.mp3", 200_000)
        vm.addTracks(listOf(a, b))
        vm.addTracks(listOf(a.copy(id = "a2")))
        assertEquals(2, vm.state.value.tracks.size)
        val pid = vm.createPlaylist("  Дорога ")!!
        assertEquals(null, vm.createPlaylist("   "))
        vm.addToPlaylist(pid, "a")
        vm.addToPlaylist(pid, "b")
        vm.addToPlaylist(pid, "a")
        vm.addToPlaylist(pid, "нет такого")
        val p = vm.state.value.playlists.first { it.id == pid }
        assertEquals("Дорога", p.name)
        assertEquals(listOf("a", "b"), p.trackIds)
        vm.moveInPlaylist(pid, "b", -1)
        assertEquals(listOf("b", "a"), vm.state.value.playlists.first { it.id == pid }.trackIds)
        vm.renamePlaylist(pid, "Ночь")
        assertEquals("Ночь", vm.state.value.playlists.first { it.id == pid }.name)
        // Удалённый трек уходит и из плейлиста
        vm.deleteTrack("b")
        assertEquals(listOf("a"), vm.state.value.playlists.first { it.id == pid }.trackIds)
        // Всё переживает сохранение
        val other = AppViewModel()
        other.restore(vm.snapshot())
        assertEquals(listOf("a"), other.state.value.tracks.map { it.id })
        assertEquals("Ночь", other.state.value.playlists.single().name)
        vm.deletePlaylist(pid)
        assertTrue(vm.state.value.playlists.isEmpty())
    }

    @Test
    fun videoEditRules() {
        val e = VideoEdit("/tmp/s.mp4", 90_000, 1920, 1080, trimEndMs = 60_000)
        // По умолчанию — вертикальный кадр 1080 × 1920
        assertEquals(1080 to 1920, e.outputSize())
        assertEquals(1080 to 1350, e.copy(aspect = Aspect.Tall).outputSize())
        assertEquals(1080 to 1080, e.copy(aspect = Aspect.Square).outputSize())
        // «Оригинал» — как снято; после поворота стороны меняются
        assertEquals(1920 to 1080, e.copy(aspect = Aspect.Original).outputSize())
        assertEquals(1080 to 1920, e.copy(aspect = Aspect.Original, rotation = 90).outputSize())
        // Скорость меняет длительность
        assertEquals(30_000L, e.copy(speed = 2f).outputDurationMs)
        assertEquals(2_000L, e.copy(trimStartMs = 10_000, coverMs = 14_000, speed = 2f).coverOutputMs())
        assertTrue(e.colorIsIdentity)
        assertFalse(e.copy(filter = VideoFilter.Noir).colorIsIdentity)
    }

    @Test
    fun colorMatrices() {
        val e = VideoEdit("/tmp/s.mp4", 10_000, 1080, 1920)
        // Без правок цвет не меняется
        val (r, g, b) = ColorMatrix.apply(e.colorMatrix(), 0.2f, 0.5f, 0.8f)
        assertEquals(0.2f, r, 1e-4f)
        assertEquals(0.5f, g, 1e-4f)
        assertEquals(0.8f, b, 1e-4f)
        // Ч/Б — все каналы равны
        val (nr, ng, nb) = ColorMatrix.apply(VideoFilter.Noir.matrix, 0.9f, 0.1f, 0.3f)
        assertEquals(nr, ng, 1e-4f)
        assertEquals(ng, nb, 1e-4f)
        // Яркость поднимает все каналы
        val (br, _, _) = ColorMatrix.apply(e.copy(brightness = 0.5f).colorMatrix(), 0.2f, 0.5f, 0.8f)
        assertTrue(br > 0.2f)
        // Тепло: красного больше, синего меньше
        val (wr, _, wb) = ColorMatrix.apply(e.copy(warmth = 1f).colorMatrix(), 0.5f, 0.5f, 0.5f)
        assertTrue(wr > 0.5f && wb < 0.5f)
        // Матрица для OpenGL — по столбцам, сдвиг — в четвёртом столбце
        val gl = ColorMatrix.toGl(ColorMatrix.offset(0.1f, 0.2f, 0.3f))
        assertEquals(0.1f, gl[12], 1e-6f)
        assertEquals(0.3f, gl[14], 1e-6f)
        assertEquals(1f, gl[15], 1e-6f)
    }

    @Test
    fun commentReplyAndReport() {
        val vm = AppViewModel()
        val post = vm.state.value.posts.first { it.commentList.isNotEmpty() }
        val other = post.commentList.first { it.author.id != vm.state.value.me.id }
        vm.addComment(post.id, "Согласна!", replyTo = other.author.name)
        val mine = vm.state.value.findPost(post.id)!!.commentList.last()
        assertEquals(other.author.name, mine.replyTo)
        vm.reportComment(other.id)
        assertTrue(other.id in vm.state.value.reportedComments)
        // Жалобы переживают сохранение
        val restored = AppViewModel()
        restored.restore(vm.snapshot())
        assertTrue(other.id in restored.state.value.reportedComments)
    }

    @Test
    fun groupSettingsPinAndPosts() {
        val vm = AppViewModel()
        assertNull(vm.createGroup("Кофе и звёзды", "Ночные разговоры", "Природа"))
        val id = vm.state.value.openGroupId!!
        vm.openGroupSettings()
        assertTrue(vm.state.value.groupSettingsOpen)
        assertEquals("Название — от 3 символов", vm.updateGroup(id, "ab", "", "Фото"))
        assertNull(vm.updateGroup(id, "  Кофе ночью ", "Новое описание", "Фото"))
        val g = vm.state.value.groups.first { it.id == id }
        assertEquals("Кофе ночью", g.name)
        assertEquals("Фото", g.topic)
        // Запись с музыкой и закрепление
        val song = Attachment("s", MediaKind.Document, "/tmp/song.mp3", "Луна.mp3")
        assertTrue(song.isAudio)
        vm.postInGroup(id, "", listOf(song))
        val post = vm.state.value.groups.first { it.id == id }.posts.first()
        assertEquals(listOf(song), post.media)
        vm.togglePinGroupPost(id, post.id)
        assertEquals(post.id, vm.state.value.groups.first { it.id == id }.pinnedPostId)
        vm.togglePinGroupPost(id, post.id)
        assertNull(vm.state.value.groups.first { it.id == id }.pinnedPostId)
        // «Только я»: создатель пишет, участники — нет
        vm.setGroupOnlyOwnerPosts(id, true)
        vm.postInGroup(id, "Только для меня")
        assertEquals(2, vm.state.value.groups.first { it.id == id }.posts.size)
        vm.deleteGroupPost(id, post.id)
        assertEquals(1, vm.state.value.groups.first { it.id == id }.posts.size)
        // Чужую группу нельзя править
        val foreign = vm.state.value.groups.first { !it.mine }
        vm.updateGroup(foreign.id, "Чужое имя", "", "")
        assertEquals(foreign.name, vm.state.value.groups.first { it.id == foreign.id }.name)
        vm.back()
        assertFalse(vm.state.value.groupSettingsOpen)
    }

    @Test
    fun stickersPinsAndAchievements() {
        val vm = AppViewModel()
        assertFalse(achievements(vm.state.value).first { it.title == "Художник" }.earned)
        vm.addSticker(Sticker("a", "/tmp/a.png", StickerKind.Smiley, 1))
        vm.addSticker(Sticker("b", "/tmp/b.png", StickerKind.Badge, 2))
        assertEquals(listOf("b", "a"), vm.state.value.stickers.map { it.id })
        assertTrue(achievements(vm.state.value).first { it.title == "Художник" }.earned)
        vm.deleteSticker("a")
        assertEquals(listOf("b"), vm.state.value.stickers.map { it.id })
        // Витрина — только свои записи, до трёх
        repeat(4) { vm.publish("Момент $it") }
        val mine = vm.state.value.myPosts
        mine.forEach { vm.toggleProfilePin(it.id) }
        // Бесплатно — три; пока Memory+ скрыт, открыто всё (шесть)
        val pinLimit = if (ru.wishka.memory.data.Premium.enabled) AppViewModel.MAX_PINS else ru.wishka.memory.data.Premium.PLUS_PINS
        assertEquals(minOf(mine.size, pinLimit), vm.state.value.profilePins.size)
        val foreign = vm.state.value.posts.first { it.author.id != vm.state.value.me.id }
        vm.toggleProfilePin(foreign.id)
        assertFalse(foreign.id in vm.state.value.profilePins)
        vm.deletePost(mine.last().id)
        assertFalse(mine.last().id in vm.state.value.profilePins)
        // Сегодняшние записи — в последнем столбике активности
        assertEquals(14, activity(vm.state.value).size)
        assertTrue(activity(vm.state.value).last() >= 3)
        val restored = AppViewModel()
        restored.restore(vm.snapshot())
        assertEquals(listOf("b"), restored.state.value.stickers.map { it.id })
        assertEquals(vm.state.value.profilePins, restored.state.value.profilePins)
    }

    @Test
    fun catalogLicenses() {
        assertEquals("CC BY-NC-SA 3.0", MusicCatalog.licenseFromUrl("http://creativecommons.org/licenses/by-nc-sa/3.0/"))
        assertEquals("CC BY 4.0", MusicCatalog.licenseFromUrl("https://creativecommons.org/licenses/by/4.0/"))
        assertEquals("CC0", MusicCatalog.licenseFromUrl("https://creativecommons.org/publicdomain/zero/1.0/"))
        assertEquals("CC0", MusicCatalog.licenseLabel("cc0", "1.0"))
        assertEquals("CC BY 4.0", MusicCatalog.licenseLabel("by", "4.0"))
        assertEquals("", MusicCatalog.licenseLabel("", ""))
    }
}
