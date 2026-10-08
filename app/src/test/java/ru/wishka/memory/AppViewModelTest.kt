package ru.wishka.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
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
        vm.updateProfile("Новое имя", "о себе")
        assertEquals("Новое имя", vm.state.value.me.name)
        assertEquals("Новое имя", vm.state.value.myPosts.first().author.name)
        vm.updateProfile("  ", "")
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
    fun texts() {
        assertEquals("5 мин", agoTitle(5))
        assertEquals("2 ч", agoTitle(125))
        assertEquals("вчера", agoTitle(60 * 30))
        assertEquals("21 запись", plural(21, "запись", "записи", "записей"))
        assertEquals("12 записей", plural(12, "запись", "записи", "записей"))
    }
}
