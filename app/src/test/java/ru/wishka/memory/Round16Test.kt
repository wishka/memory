package ru.wishka.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.wishka.memory.data.Guide
import ru.wishka.memory.data.GuideAction
import ru.wishka.memory.i18n.L10n
import ru.wishka.memory.i18n.Lang
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.ProfileSection
import ru.wishka.memory.ui.SettingsPage
import ru.wishka.memory.ui.Tab

/** Гид по Memory: содержание, поиск, навигация, отметки «прочитано». */
class Round16Test {
    @Before
    fun russian() {
        L10n.choice = Lang.Ru
    }

    private fun vm() = AppViewModel(clock = { 1_790_000_000_000L }, demoReplies = false)

    @Test
    fun contentIsCompleteInBothLanguages() {
        assertTrue(Guide.chapters.size >= 10)
        assertTrue(Guide.topicCount >= 50)
        assertEquals(Guide.chapters.size, Guide.chapters.map { it.id }.toSet().size)
        for (c in Guide.chapters) {
            assertEquals(c.id, c.topics.size, c.topics.map { it.id }.toSet().size)
            for (t in c.topics) {
                assertTrue(c.key(t), t.steps.isNotEmpty())
                (listOf(t.title) + t.steps + listOfNotNull(t.tip)).forEach {
                    assertTrue(c.key(t), it.ru.isNotBlank() && it.en.isNotBlank())
                }
            }
        }
        // Темы о новом — на месте
        assertTrue(Guide.chapter("groups")!!.topics.any { it.id == "shop-buyer" })
        assertTrue(Guide.chapter("friends")!!.topics.any { it.id == "requests" })
    }

    @Test
    fun textFollowsLanguage() {
        val title = Guide.chapters.first().title
        assertEquals(title.ru, title.text)
        L10n.choice = Lang.En
        assertEquals(title.en, title.text)
    }

    @Test
    fun searchFindsInBothLanguages() {
        val ru = Guide.search("витрина продавцу")
        assertTrue(ru.isNotEmpty())
        assertEquals("groups", ru.first().chapter.id)
        assertTrue(Guide.search("password").any { it.topic.id == "password" })
        assertTrue(Guide.search("  ").isEmpty())
        assertTrue(Guide.search("абракадабра").isEmpty())
        // Совпадение в заголовке — выше
        assertEquals("voice", Guide.search("голосовые").first().topic.id)
    }

    @Test
    fun navigationAndBack() {
        val vm = vm()
        vm.openGuide()
        var s = vm.state.value
        assertEquals(SettingsPage.Guide, s.settingsPage)
        assertEquals(Tab.Profile, s.tab)
        assertEquals(Tab.Feed, s.guideReturn)
        vm.openGuideChapter("prism")
        assertEquals("prism", vm.state.value.guideChapter)
        vm.openGuideChapter("нет-такой")
        assertEquals("prism", vm.state.value.guideChapter)
        // «Назад»: глава → список глав → лента, откуда пришли
        assertTrue(vm.back())
        assertNull(vm.state.value.guideChapter)
        assertTrue(vm.back())
        s = vm.state.value
        assertNull(s.settingsPage)
        assertEquals(Tab.Feed, s.tab)
        // Из настроек — назад в настройки
        vm.openSettings()
        vm.openGuide("chats")
        assertEquals("chats", vm.state.value.guideChapter)
        vm.back(); vm.back()
        assertEquals(SettingsPage.Main, vm.state.value.settingsPage)
    }

    @Test
    fun actionsLeadToScreens() {
        val vm = vm()
        vm.openGuide("groups")
        vm.guideGo(GuideAction.Groups)
        var s = vm.state.value
        assertNull(s.settingsPage)
        assertEquals(Tab.Groups, s.tab)
        vm.guideGo(GuideAction.Requests)
        s = vm.state.value
        assertEquals(Tab.Profile, s.tab)
        assertEquals(ProfileSection.Requests, s.profileSection)
        vm.guideGo(GuideAction.Privacy)
        assertEquals(SettingsPage.Privacy, vm.state.value.settingsPage)
        assertNull(vm.state.value.profileSection)
        vm.guideGo(GuideAction.NewPrism)
        s = vm.state.value
        assertEquals(Tab.Prism, s.tab)
        assertTrue(s.creatingPrism)
        // Каждое действие куда-то ведёт и не падает
        GuideAction.entries.forEach { vm.guideGo(it) }
    }

    @Test
    fun readMarksAndCardSurviveRestart() {
        val vm = vm()
        val chapter = Guide.chapter("start")!!
        vm.markGuideRead(chapter, chapter.topics[0])
        vm.markGuideRead(chapter, chapter.topics[0])
        vm.markGuideRead(chapter, chapter.topics[1])
        assertEquals(2, vm.state.value.guideRead.size)
        assertFalse(vm.state.value.guideHidden)
        vm.hideGuideCard()
        val snap = vm.snapshot().copy(guideRead = vm.snapshot().guideRead + "старая/тема")
        val again = vm()
        again.restore(snap)
        assertTrue(again.state.value.guideHidden)
        // Отметки убранных тем не считаются
        assertEquals(setOf("start/signup", "start/taste"), again.state.value.guideRead)
    }
}
