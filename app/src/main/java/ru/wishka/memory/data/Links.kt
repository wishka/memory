package ru.wishka.memory.data

import java.net.URLDecoder
import java.net.URLEncoder

/** Куда ведёт ссылка Memory. */
sealed class LinkTarget {
    data class PostLink(val id: Long) : LinkTarget()
    data class Profile(val handle: String) : LinkTarget()
    data class PrismLink(val id: Long) : LinkTarget()
    data class GroupLink(val id: Int) : LinkTarget()
    data class TagLink(val tag: String) : LinkTarget()
}

/**
 * Ссылки на запись, профиль, PRISM, группу и тег.
 *
 * Для мессенджеров — веб-ссылка на страницу docs/l (GitHub Pages): она
 * открывает приложение по memory://…, а если его нет — рассказывает, как
 * поставить. Внутри Memory такие ссылки открываются сразу, без браузера.
 *
 *   https://wishka.github.io/memory/l/?p=12345   — запись
 *   https://wishka.github.io/memory/l/?u=vera     — профиль @vera
 *   memory://post/12345, memory://u/vera, memory://prism/7, memory://group/3, memory://tag/закат
 */
object Links {
    const val WEB = "https://wishka.github.io/memory/l/"
    const val SCHEME = "memory"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun post(id: Long) = WEB + "?p=" + id
    fun profile(handle: String) = WEB + "?u=" + enc(handle.removePrefix("@").lowercase())
    fun prism(id: Long) = WEB + "?r=" + id
    fun group(id: Int) = WEB + "?g=" + id
    fun tag(tag: String) = WEB + "?t=" + enc(tag.removePrefix("#").lowercase())

    /** Та же ссылка, но сразу в приложение (её открывает страница docs/l). */
    fun app(target: LinkTarget): String = when (target) {
        is LinkTarget.PostLink -> "$SCHEME://post/${target.id}"
        is LinkTarget.Profile -> "$SCHEME://u/${enc(target.handle)}"
        is LinkTarget.PrismLink -> "$SCHEME://prism/${target.id}"
        is LinkTarget.GroupLink -> "$SCHEME://group/${target.id}"
        is LinkTarget.TagLink -> "$SCHEME://tag/${enc(target.tag)}"
    }

    fun web(target: LinkTarget): String = when (target) {
        is LinkTarget.PostLink -> post(target.id)
        is LinkTarget.Profile -> profile(target.handle)
        is LinkTarget.PrismLink -> prism(target.id)
        is LinkTarget.GroupLink -> group(target.id)
        is LinkTarget.TagLink -> tag(target.tag)
    }

    private val HANDLE = Regex("^[a-z0-9._]{3,20}$")

    /** Разобрать ссылку: веб или memory://. null — это не ссылка Memory (или приглашение — его разбирает Invites). */
    fun parse(link: String): LinkTarget? {
        val s = link.trim()
        val web = WEB.removeSuffix("/")
        return when {
            s.startsWith(web) -> {
                val q = s.substringAfter('?', "").substringBefore('#')
                val params = q.split('&').filter { '=' in it }.associate {
                    it.substringBefore('=') to runCatching { URLDecoder.decode(it.substringAfter('='), "UTF-8") }.getOrDefault("")
                }
                when {
                    params["p"] != null -> params["p"]?.toLongOrNull()?.let { LinkTarget.PostLink(it) }
                    params["u"] != null -> cleanHandle(params["u"].orEmpty())?.let { LinkTarget.Profile(it) }
                    params["r"] != null -> params["r"]?.toLongOrNull()?.let { LinkTarget.PrismLink(it) }
                    params["g"] != null -> params["g"]?.toIntOrNull()?.let { LinkTarget.GroupLink(it) }
                    params["t"] != null -> cleanTag(params["t"].orEmpty())?.let { LinkTarget.TagLink(it) }
                    else -> null
                }
            }
            s.startsWith("$SCHEME://") -> {
                val rest = s.removePrefix("$SCHEME://").substringBefore('?').substringBefore('#').trimEnd('/')
                val kind = rest.substringBefore('/')
                val value = runCatching { URLDecoder.decode(rest.substringAfter('/', ""), "UTF-8") }.getOrDefault("")
                when (kind) {
                    "post" -> value.toLongOrNull()?.let { LinkTarget.PostLink(it) }
                    "u" -> cleanHandle(value)?.let { LinkTarget.Profile(it) }
                    "prism" -> value.toLongOrNull()?.let { LinkTarget.PrismLink(it) }
                    "group" -> value.toIntOrNull()?.let { LinkTarget.GroupLink(it) }
                    "tag" -> cleanTag(value)?.let { LinkTarget.TagLink(it) }
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun cleanHandle(raw: String): String? = raw.removePrefix("@").lowercase().takeIf { HANDLE.matches(it) }

    private fun cleanTag(raw: String): String? = raw.removePrefix("#").lowercase().takeIf { t -> t.length in 2..40 && t.all { it.isLetterOrDigit() || it == '_' } }

    /** Текст для «Поделиться»: начало записи и ссылка. */
    fun shareText(p: Post): String {
        val preview = p.text.replace('\n', ' ').trim().let { if (it.length > 100) it.take(100).trimEnd() + "…" else it }
        return listOf(preview, post(p.id)).filter { it.isNotBlank() }.joinToString("\n")
    }
}
