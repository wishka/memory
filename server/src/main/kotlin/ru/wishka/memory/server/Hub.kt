package ru.wishka.memory.server

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Живой канал (docs/REALTIME.md): кто сейчас подключён по WebSocket.
 * У человека может быть несколько телефонов — шлём на все.
 */
class Hub {
    /** Одно соединение: Ktor-сессия (в тестах — список строк). */
    interface Peer {
        fun send(text: String)
        /** Закрыть соединение (код 4401: вход больше не действует). */
        fun close() {}
    }

    private val peers = ConcurrentHashMap<Int, CopyOnWriteArraySet<Peer>>()

    fun join(userId: Int, peer: Peer) {
        peers.getOrPut(userId) { CopyOnWriteArraySet() } += peer
    }

    fun leave(userId: Int, peer: Peer) {
        peers[userId]?.let { set ->
            set -= peer
            if (set.isEmpty()) peers.remove(userId, set)
        }
    }

    fun online(userId: Int): Boolean = peers[userId]?.isNotEmpty() == true

    /** Кадр всем телефонам человека; упавшее соединение не мешает остальным. */
    fun send(userId: Int, text: String) {
        peers[userId]?.forEach { p -> runCatching { p.send(text) } }
    }

    fun sendAll(userIds: Collection<Int>, text: String) = userIds.distinct().forEach { send(it, text) }

    /**
     * Закрыть все соединения человека: бан, удаление аккаунта, смена пароля,
     * выход устройства. Кто ещё вправе — переподключится с новым токеном.
     */
    fun kick(userId: Int) {
        peers.remove(userId)?.forEach { p -> runCatching { p.close() } }
    }
}
