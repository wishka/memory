package ru.wishka.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.wishka.memory.data.ActiveCall
import ru.wishka.memory.data.CallPhase
import ru.wishka.memory.data.CallTransport
import ru.wishka.memory.data.Demo
import ru.wishka.memory.data.net.RtEvent
import ru.wishka.memory.i18n.L10n
import ru.wishka.memory.i18n.Lang
import ru.wishka.memory.ui.AppViewModel

/**
 * Раунд 28: звонки и эфиры через LiveKit. Здесь — то, что проверяется без
 * сервера; с сервером — интеграционная проверка против server/.
 */
class Round28Test {
    private var now = 1_800_000_000_000L

    @Before
    fun setUp() {
        L10n.choice = Lang.Ru
    }

    @Test
    fun callAndLiveEventsParse() {
        val call = RtEvent.parse("""{"type":"call","id":1792157000001,"state":"ringing","from":100002,"video":true,"user":{"id":100002,"name":"Борис"}}""") as RtEvent.Call
        assertEquals(1792157000001L, call.id)
        assertEquals("ringing", call.state)
        assertEquals(100002, call.from)
        assertTrue(call.video)
        assertEquals("Борис", call.user?.optString("name"))
        val live = RtEvent.parse("""{"type":"live","id":7,"state":"counts","viewers":3,"hearts":12}""") as RtEvent.Live
        assertEquals(3, live.viewers); assertEquals(12, live.hearts)
        val c = RtEvent.parse("""{"type":"live_comment","id":7,"comment":{"id":9,"author":5,"name":"Вера","text":"Привет","at":1}}""") as RtEvent.LiveComment
        assertEquals(7L, c.liveId); assertEquals("Привет", c.comment.getString("text"))
        // Туда и обратно — то же
        assertEquals(call.copy(user = null).json().toString(), RtEvent.parse(call.copy(user = null).json().toString())!!.json().toString())
    }

    @Test
    fun serverCallRoomUsesServerId() {
        val local = ActiveCall(5, 2, true, true, CallPhase.Calling, now)
        assertEquals("call-5", local.room)
        assertEquals("call-1792157000001", local.copy(serverId = 1792157000001).room)
    }

    @Test
    fun demoCallDoesNotNeedServer() {
        val vm = AppViewModel(clock = { now }, demoReplies = false)
        val tokens = mutableListOf<String>()
        vm.callTransport = object : CallTransport {
            override fun connect(url: String, room: String, token: String, video: Boolean) { tokens += token }
            override fun setMuted(muted: Boolean) {}
            override fun setCamera(on: Boolean, front: Boolean) {}
            override fun hangUp() {}
            override val connected: Boolean = false
        }
        val friend = Demo.people.first()
        assertNull(vm.startCall(friend.id, false))
        // Демо: связь без токена, звонок — на телефоне
        assertEquals(listOf(""), tokens)
        assertEquals(0L, vm.state.value.call!!.serverId)
        vm.endCall()
        assertNull(vm.state.value.call)
    }
}
