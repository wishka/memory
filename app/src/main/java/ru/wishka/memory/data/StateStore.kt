package ru.wishka.memory.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ru.wishka.memory.ui.Snapshot
import java.io.File

/**
 * Записи, переписка, комментарии, группы и закладки — на телефоне, в
 * файле files/state-<логин>.json. Пока у Memory нет сервера, так своё не
 * пропадает после перезапуска. У каждого аккаунта на телефоне свой файл.
 *
 * Люди хранятся номером: при чтении номер превращается обратно в человека
 * из демо-данных, «я» — в вошедшего человека.
 */
class StateStore(private val context: Context) {

    private fun file(login: String) = File(context.filesDir, "state-${login.filter { it.isLetterOrDigit() || it in "._" }}.json")

    fun load(login: String, me: User): Snapshot? = runCatching {
        val f = file(login)
        if (!f.exists()) return null
        val root = JSONObject(f.readText())
        if (root.optInt("version") != VERSION) return null
        Codec(me).snapshot(root)
    }.getOrNull()

    /** Запись — во временный файл и замена целиком: оборванная запись не испортит сохранённое. */
    fun save(login: String, snapshot: Snapshot, me: User) {
        val target = file(login)
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(Codec(me).json(snapshot).toString())
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    /** Удалить сохранённое аккаунта — при удалении аккаунта. */
    fun delete(login: String) {
        file(login).delete()
    }

    private class Codec(private val me: User) {
        private fun person(id: Int): User = if (id == me.id) me else Demo.person(id) ?: User(id, "Неизвестный", "unknown")

        fun json(s: Snapshot): JSONObject = JSONObject()
            .put("version", VERSION)
            .put("nextId", s.nextId)
            .put("notify", notify(s.notifications))
            .put("privacy", privacy(s.privacy))
            .put("blocked", JSONArray(s.blocked.toList()))
            .put("tracks", JSONArray(s.tracks.map { track(it) }))
            .put("playlists", JSONArray(s.playlists.map { playlist(it) }))
            .put("following", JSONArray(s.following.toList()))
            .put("posts", JSONArray(s.posts.map { post(it) }))
            .put("chats", JSONArray(s.chats.map { chat(it) }))
            .put("groups", JSONArray(s.groups.map { group(it) }))
            .put("prisms", JSONArray(s.prisms.map { prism(it) }))

        fun snapshot(o: JSONObject) = Snapshot(
            posts = o.getJSONArray("posts").objects().map { post(it) },
            chats = o.getJSONArray("chats").objects().map { chat(it) },
            groups = o.getJSONArray("groups").objects().map { group(it) },
            prisms = o.getJSONArray("prisms").objects().map { prism(it) },
            following = o.getJSONArray("following").let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() },
            // Старый файл: только общий выключатель «notifications»
            notifications = o.optJSONObject("notify")?.let { notify(it) }
                ?: Notifications(all = o.optBoolean("notifications", true)),
            nextId = o.optLong("nextId", 10_000),
            privacy = o.optJSONObject("privacy")?.let { privacy(it) } ?: Privacy(),
            blocked = o.optJSONArray("blocked")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: emptySet(),
            tracks = o.optJSONArray("tracks").objects().map { track(it) },
            playlists = o.optJSONArray("playlists").objects().map { playlist(it) },
        )

        private fun track(t: MusicTrack) = JSONObject()
            .put("id", t.id).put("title", t.title).put("artist", t.artist).put("path", t.path)
            .put("durationMs", t.durationMs).put("addedAt", t.addedAt)

        private fun track(o: JSONObject) = MusicTrack(
            o.getString("id"), o.optString("title"), o.optString("artist"), o.getString("path"),
            o.optLong("durationMs"), o.optLong("addedAt"),
        )

        private fun playlist(p: Playlist) = JSONObject()
            .put("id", p.id).put("name", p.name).put("trackIds", JSONArray(p.trackIds))

        private fun playlist(o: JSONObject) = Playlist(
            o.getLong("id"), o.optString("name"),
            o.optJSONArray("trackIds")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
        )

        private fun notify(n: Notifications) = JSONObject()
            .put("all", n.all).put("messages", n.messages).put("reactions", n.reactions)
            .put("groups", n.groups).put("follows", n.follows)

        private fun notify(o: JSONObject) = Notifications(
            all = o.optBoolean("all", true), messages = o.optBoolean("messages", true),
            reactions = o.optBoolean("reactions", true), groups = o.optBoolean("groups", true),
            follows = o.optBoolean("follows", true),
        )

        private fun privacy(p: Privacy) = JSONObject()
            .put("profileInfo", p.profileInfo.name).put("followLists", p.followLists.name)
            .put("messages", p.messages.name).put("comments", p.comments.name)
            .put("showOnline", p.showOnline).put("showBirthday", p.showBirthday)

        private fun privacy(o: JSONObject) = Privacy(
            profileInfo = audience(o.optString("profileInfo")),
            followLists = audience(o.optString("followLists")),
            messages = audience(o.optString("messages")),
            comments = audience(o.optString("comments")),
            showOnline = o.optBoolean("showOnline", true),
            showBirthday = o.optBoolean("showBirthday", true),
        )

        private fun audience(name: String) = Audience.entries.firstOrNull { it.name == name } ?: Audience.Everyone

        private fun attachment(a: Attachment) = JSONObject()
            .put("id", a.id).put("kind", a.kind.name).put("path", a.path)
            .put("name", a.name).put("size", a.size).put("mime", a.mime)

        private fun attachment(o: JSONObject) = Attachment(
            o.getString("id"), MediaKind.valueOf(o.getString("kind")), o.getString("path"),
            o.optString("name"), o.optLong("size"), o.optString("mime"),
        )

        private fun comment(c: Comment) = JSONObject()
            .put("id", c.id).put("author", c.author.id).put("text", c.text)
            .put("minutesAgo", c.minutesAgo).put("createdAt", c.createdAt)
            .put("likes", c.likes).put("liked", c.liked)

        private fun comment(o: JSONObject) = Comment(
            o.getLong("id"), person(o.getInt("author")), o.getString("text"),
            o.optInt("minutesAgo"), o.optLong("createdAt"),
            likes = o.optInt("likes"), liked = o.optBoolean("liked"),
        )

        private fun post(p: Post) = JSONObject()
            .put("id", p.id).put("author", p.author.id).put("text", p.text)
            .put("minutesAgo", p.minutesAgo).put("artSeed", p.artSeed ?: JSONObject.NULL)
            .put("likes", p.likes).put("liked", p.liked).put("comments", p.comments)
            .put("createdAt", p.createdAt)
            .put("media", JSONArray(p.media.map { attachment(it) }))
            .put("commentList", JSONArray(p.commentList.map { comment(it) }))

        private fun post(o: JSONObject) = Post(
            id = o.getLong("id"),
            author = person(o.getInt("author")),
            text = o.getString("text"),
            minutesAgo = o.optInt("minutesAgo"),
            artSeed = if (o.isNull("artSeed")) null else o.getInt("artSeed"),
            likes = o.optInt("likes"),
            liked = o.optBoolean("liked"),
            comments = o.optInt("comments"),
            media = o.optJSONArray("media").objects().map { attachment(it) },
            commentList = o.optJSONArray("commentList").objects().map { comment(it) },
            createdAt = o.optLong("createdAt"),
        )

        private fun message(m: Message) = JSONObject()
            .put("id", m.id).put("fromMe", m.fromMe).put("text", m.text).put("time", m.time)
            .put("attachments", JSONArray(m.attachments.map { attachment(it) }))
            .put("replyTo", m.replyTo?.let { JSONObject().put("messageId", it.messageId).put("author", it.author).put("preview", it.preview) } ?: JSONObject.NULL)
            .put("forwardedFrom", m.forwardedFrom ?: JSONObject.NULL)

        private fun message(o: JSONObject) = Message(
            o.getLong("id"), o.getBoolean("fromMe"), o.getString("text"), o.getString("time"),
            o.optJSONArray("attachments").objects().map { attachment(it) },
            replyTo = o.optJSONObject("replyTo")?.let { ReplyRef(it.getLong("messageId"), it.optString("author"), it.optString("preview")) },
            forwardedFrom = if (o.isNull("forwardedFrom")) null else o.optString("forwardedFrom"),
        )

        private fun chat(c: Chat) = JSONObject()
            .put("id", c.id).put("peer", c.peer.id).put("unread", c.unread)
            .put("pinnedId", c.pinnedId ?: JSONObject.NULL)
            .put("onTop", c.onTop).put("muted", c.muted)
            .put("messages", JSONArray(c.messages.map { message(it) }))

        private fun chat(o: JSONObject) = Chat(
            o.getInt("id"), person(o.getInt("peer")),
            o.getJSONArray("messages").objects().map { message(it) },
            unread = o.optInt("unread"),
            pinnedId = if (o.isNull("pinnedId")) null else o.getLong("pinnedId"),
            onTop = o.optBoolean("onTop"),
            muted = o.optBoolean("muted"),
        )

        private fun group(g: Group) = JSONObject()
            .put("id", g.id).put("name", g.name).put("description", g.description).put("topic", g.topic)
            .put("members", g.members).put("seed", g.seed).put("joined", g.joined).put("mine", g.mine)
            .put("muted", g.muted)
            .put("posts", JSONArray(g.posts.map { post(it) }))

        private fun group(o: JSONObject) = Group(
            o.getInt("id"), o.getString("name"), o.optString("description"), o.optString("topic"),
            o.optInt("members"), o.optInt("seed"), o.optBoolean("joined"), o.optBoolean("mine"),
            o.getJSONArray("posts").objects().map { post(it) },
            muted = o.optBoolean("muted"),
        )

        private fun prism(p: Prism) = JSONObject()
            .put("id", p.id).put("author", p.author.id).put("caption", p.caption).put("music", p.music)
            .put("seed", p.seed).put("likes", p.likes).put("comments", p.comments).put("liked", p.liked)
            .put("durationMs", p.durationMs).put("saved", p.saved)
            .put("video", p.video?.let { attachment(it) } ?: JSONObject.NULL)
            .put("cover", p.cover?.let { attachment(it) } ?: JSONObject.NULL)
            .put("createdAt", p.createdAt)
            .put("commentList", JSONArray(p.commentList.map { comment(it) }))

        private fun prism(o: JSONObject) = Prism(
            o.getLong("id"), person(o.getInt("author")), o.getString("caption"), o.optString("music"),
            o.optInt("seed"), o.optInt("likes"), o.optInt("comments"), o.optBoolean("liked"),
            o.optInt("durationMs", 9_000), o.optBoolean("saved"),
            o.optJSONArray("commentList").objects().map { comment(it) },
            video = o.optJSONObject("video")?.let { attachment(it) },
            cover = o.optJSONObject("cover")?.let { attachment(it) },
            createdAt = o.optLong("createdAt"),
        )

        private fun JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
    }

    private companion object {
        /** Меняется, когда меняется устройство файла: старый файл тогда не читается. */
        const val VERSION = 1
    }
}
