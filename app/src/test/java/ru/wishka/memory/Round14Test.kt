package ru.wishka.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.wishka.memory.data.Legal
import ru.wishka.memory.data.LegalDoc
import ru.wishka.memory.data.ReportKind
import ru.wishka.memory.data.ReportReason
import ru.wishka.memory.i18n.L10n
import ru.wishka.memory.i18n.Lang
import ru.wishka.memory.ui.AppViewModel

/** Жалобы, рекомендации по согласию, документы. */
class Round14Test {
    @Before
    fun russian() {
        L10n.choice = Lang.Ru
    }

    private fun vm() = AppViewModel(clock = { 1_790_000_000_000L }, demoReplies = false)

    @Test
    fun reportingAPostHidesItAndQueuesTheReport() {
        val vm = vm()
        val post = vm.state.value.feed.first { !vm.isMine(it) }
        vm.reportPost(post)
        val target = vm.state.value.reporting!!
        assertEquals(ReportKind.Post, target.kind)
        vm.report(target, ReportReason.Spam, "  реклама  ", block = false)
        val s = vm.state.value
        assertNull(s.reporting)
        assertTrue(s.posts.none { it.id == post.id })
        assertEquals("реклама", s.reports.single().note)
        assertFalse(post.author.id in s.blocked)
    }

    @Test
    fun reportWithBlockBlocksTheAuthor() {
        val vm = vm()
        val prism = vm.state.value.prisms.first { it.author.id != vm.state.value.me.id }
        vm.reportPrism(prism)
        vm.report(vm.state.value.reporting!!, ReportReason.Abuse, block = true)
        assertTrue(vm.state.value.prisms.none { it.id == prism.id })
        assertTrue(prism.author.id in vm.state.value.blocked)
    }

    @Test
    fun reportingAMessageRemovesOnlyIt() {
        val vm = vm()
        val chat = vm.state.value.chats.first { c -> c.messages.any { !it.fromMe } }
        val msg = chat.messages.first { !it.fromMe }
        val mine = chat.messages.firstOrNull { it.fromMe }
        mine?.let { vm.reportMessage(chat.id, it) }
        assertNull(vm.state.value.reporting) // на свои не жалуются
        vm.reportMessage(chat.id, msg)
        vm.report(vm.state.value.reporting!!, ReportReason.Other)
        val after = vm.state.value.chats.first { it.id == chat.id }
        assertTrue(after.messages.none { it.id == msg.id })
        assertEquals(chat.messages.size - 1, after.messages.size)
    }

    @Test
    fun reportingACommentHidesIt() {
        val vm = vm()
        val post = vm.state.value.posts.first { p -> p.commentList.any { it.author.id != vm.state.value.me.id } }
        val c = post.commentList.first { it.author.id != vm.state.value.me.id }
        vm.reportComment(c)
        vm.report(vm.state.value.reporting!!, ReportReason.Hate)
        assertTrue(c.id in vm.state.value.reportedComments)
    }

    @Test
    fun personalRecommendationsCanBeTurnedOff() {
        val vm = vm()
        vm.setPrivacy(vm.state.value.privacy.copy(personalRecs = false))
        assertTrue(vm.state.value.recommendations("film").isEmpty())
        assertTrue(vm.state.value.recommendations("book").isEmpty())
    }

    @Test
    fun legalDefaults() {
        assertEquals(16, Legal.MIN_AGE)
        assertFalse(Legal.filled)
        assertEquals("privacy.md", LegalDoc.Privacy.file)
        assertEquals(ReportReason.Other, ReportReason.of("???"))
    }
}
