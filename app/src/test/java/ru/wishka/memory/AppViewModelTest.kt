package ru.wishka.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.wishka.memory.data.ProfileInfo
import ru.wishka.memory.data.agoTitle
import ru.wishka.memory.data.plural
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.Tab

class AppViewModelTest {

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
        val vm = AppViewModel()
        val s = vm.state.value
        assertTrue(s.stories.isNotEmpty())
        assertTrue(s.stories.all { it.author.id in s.following })
        // Подписка на Марка (3) добавляет его записи, отписка — убирает
        assertFalse(s.stories.any { it.author.id == 3 })
        vm.toggleFollow(3)
        assertTrue(vm.state.value.stories.any { it.author.id == 3 })
        vm.toggleFollow(3)
        assertFalse(vm.state.value.stories.any { it.author.id == 3 })
        // На себя подписаться нельзя
        vm.toggleFollow(vm.state.value.me.id)
        assertFalse(vm.state.value.me.id in vm.state.value.following)
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
    fun reelLike() {
        val vm = AppViewModel()
        val reel = vm.state.value.reels.first { !it.liked }
        vm.toggleReelLike(reel.id)
        assertEquals(reel.likes + 1, vm.state.value.reels.first { it.id == reel.id }.likes)
    }

    @Test
    fun texts() {
        assertEquals("5 мин", agoTitle(5))
        assertEquals("2 ч", agoTitle(125))
        assertEquals("вчера", agoTitle(60 * 30))
        assertEquals("21 запись", plural(21, "запись", "записи", "записей"))
        assertEquals("12 записей", plural(12, "запись", "записи", "записей"))
    }
}
