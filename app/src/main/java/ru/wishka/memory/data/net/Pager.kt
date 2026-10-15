package ru.wishka.memory.data.net

import org.json.JSONArray
import org.json.JSONObject

/** Что сейчас с подгружаемым списком — для экрана. */
data class PagerState<T>(
    val items: List<T> = emptyList(),
    val next: String? = null,
    /** Сервер сказал «дальше пусто». */
    val end: Boolean = false,
    val loading: Boolean = false,
    /** Показано сохранённое, свежего с сервера ещё не было. */
    val fromCache: Boolean = false,
    /** Когда сохранённое положили в кэш (мс); 0 — не из кэша. */
    val cachedAt: Long = 0,
    val error: NetError? = null,
)

/**
 * Список, который подгружается страницами по курсору: лента, записи
 * человека, история переписки. Сначала — что было в кэше (мгновенно и без
 * сети), потом свежее с сервера. Повторы по id отбрасываются: если между
 * страницами появилась новая запись, старые не задвоятся.
 *
 * Сам ничего не запускает: экран зовёт refresh() и loadMore(), когда
 * долистали до конца. Без сети — ошибка Offline, а сохранённое остаётся.
 */
class Pager<T>(
    private val cacheKey: String?,
    private val cache: OfflineCache?,
    private val idOf: (T) -> Long,
    private val encode: (T) -> JSONObject,
    private val decode: (JSONObject) -> T?,
    private val clock: () -> Long,
    private val load: suspend (cursor: String?) -> Page<T>,
) {
    var state = PagerState<T>()
        private set

    /** Сколько держать в кэше: первые страницы — то, что откроют снова. */
    private val keep = 100

    /** Показать сохранённое (без сети). true — было что показать. */
    fun restore(): Boolean {
        val entry = cacheKey?.let { cache?.get(it) } ?: return false
        val o = runCatching { JSONObject(entry.value) }.getOrNull() ?: return false
        val arr = o.optJSONArray("items") ?: JSONArray()
        val items = (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { runCatching { decode(it) }.getOrNull() } }
        state = PagerState(items, o.optString("next").ifEmpty { null }, end = o.optBoolean("end"), fromCache = true, cachedAt = entry.savedAt)
        return items.isNotEmpty()
    }

    /** С самого начала: потянули вниз или открыли экран. */
    suspend fun refresh(): NetError? = fetch(null, replace = true)

    /** Следующая страница: долистали до конца. Ничего не делает, если уже грузится или дальше пусто. */
    suspend fun loadMore(): NetError? {
        val s = state
        if (s.loading || s.end) return null
        if (s.fromCache || s.items.isEmpty()) return refresh()
        return fetch(s.next, replace = false)
    }

    private suspend fun fetch(cursor: String?, replace: Boolean): NetError? {
        state = state.copy(loading = true, error = null)
        return try {
            val page = load(cursor)
            val merged = if (replace) page.items.distinctBy(idOf) else {
                val seen = state.items.map(idOf).toHashSet()
                state.items + page.items.filter { seen.add(idOf(it)) }
            }
            state = PagerState(merged, page.next, end = page.next == null)
            save()
            null
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val err = NetError.of(e)
            state = state.copy(loading = false, error = err)
            err
        }
    }

    /** Убрать элемент (удалили, заблокировали автора) — и из кэша. */
    fun drop(id: Long) {
        state = state.copy(items = state.items.filter { idOf(it) != id })
        save()
    }

    /** Заменить элемент (лайк, правка) — и в кэше. */
    fun update(id: Long, change: (T) -> T) {
        state = state.copy(items = state.items.map { if (idOf(it) == id) change(it) else it })
        save()
    }

    private fun save() {
        val key = cacheKey ?: return
        val c = cache ?: return
        if (state.fromCache) return
        val kept = state.items.take(keep)
        val o = JSONObject()
            .put("items", JSONArray(kept.map(encode)))
            .put("next", if (kept.size < state.items.size) "" else state.next ?: "")
            .put("end", state.end && kept.size == state.items.size)
        c.put(key, o.toString(), clock())
    }
}
