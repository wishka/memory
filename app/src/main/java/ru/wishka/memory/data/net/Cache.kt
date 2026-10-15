package ru.wishka.memory.data.net

import java.util.concurrent.ConcurrentHashMap

/** Сохранённое: значение (JSON) и когда положили. */
data class CacheEntry(val value: String, val savedAt: Long)

/**
 * Офлайн-кэш: страницы лент и переписок, очередь отправки, незаконченные
 * загрузки. Ключи с приставкой: «page:…», «op:…», «up:…».
 *
 * На Android — SQLite (SqliteCache), на iPhone — тоже SQLite; в тестах —
 * в памяти. Каждый аккаунт — своя база: чужое не перепутается.
 */
interface OfflineCache {
    fun get(key: String): CacheEntry?
    fun put(key: String, value: String, at: Long)
    fun remove(key: String)
    /** Все ключи с приставкой — по порядку добавления (для очереди это важно). */
    fun keys(prefix: String): List<String>
    /** Сколько места занимают страницы (без очереди и загрузок). */
    fun sizeBytes(): Long
    /** Стереть страницы; очередь и загрузки не трогаем — иначе потеряется неотправленное. */
    fun clearPages()
}

/** Кэш в памяти — для тестов и пока аккаунт не открыт. */
class MemoryCache : OfflineCache {
    private val map = ConcurrentHashMap<String, CacheEntry>()
    private val order = java.util.concurrent.atomic.AtomicLong()
    private val added = ConcurrentHashMap<String, Long>()

    override fun get(key: String) = map[key]
    override fun put(key: String, value: String, at: Long) {
        map[key] = CacheEntry(value, at)
        added.putIfAbsent(key, order.incrementAndGet())
    }
    override fun remove(key: String) {
        map.remove(key)
        added.remove(key)
    }
    override fun keys(prefix: String): List<String> = map.keys.filter { it.startsWith(prefix) }.sortedBy { added[it] ?: 0 }
    override fun sizeBytes(): Long = map.entries.filter { it.key.startsWith(PAGE) }.sumOf { it.key.length + it.value.value.length.toLong() } * 2
    override fun clearPages() = map.keys.filter { it.startsWith(PAGE) }.forEach(::remove)

    companion object {
        const val PAGE = "page:"
    }
}

/** Приставки ключей кэша. */
object CacheKeys {
    const val PAGE = "page:"
    const val OP = "op:"
    const val UPLOAD = "up:"
    const val FEED = "page:feed"
    fun chat(chatId: Int) = "page:chat:$chatId"
    fun user(userId: Int) = "page:user:$userId"
}
