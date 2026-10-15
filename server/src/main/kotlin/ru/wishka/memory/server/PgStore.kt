package ru.wishka.memory.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * Хранилище в PostgreSQL — чистый JDBC, без ORM: запросов немного, и
 * каждый виден целиком. Схема — resources/schema.sql, применяется при запуске.
 */
class PgStore(url: String, user: String, password: String) : Store {
    private val ds = HikariDataSource(HikariConfig().apply {
        jdbcUrl = url
        username = user
        this.password = password
        maximumPoolSize = 10
        // База в соседнем контейнере может подняться позже сервера — подождать
        initializationFailTimeout = 60_000
    })

    init {
        val sql = PgStore::class.java.getResource("/schema.sql")!!.readText()
        tx { c -> c.createStatement().use { st -> sql.split(";").map { it.trim() }.filter { it.isNotEmpty() && !it.lines().all { l -> l.isBlank() || l.trimStart().startsWith("--") } }.forEach(st::execute) } }
    }

    private fun <T> tx(block: (Connection) -> T): T = ds.connection.use { c ->
        c.autoCommit = false
        try {
            block(c).also { c.commit() }
        } catch (e: Exception) {
            c.rollback()
            throw e
        }
    }

    private fun <T> query(sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> = ds.connection.use { c ->
        c.prepareStatement(sql).use { st ->
            bind(st, args)
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(map(rs)) } }
        }
    }

    private fun update(sql: String, vararg args: Any?): Int = ds.connection.use { c ->
        c.prepareStatement(sql).use { st -> bind(st, args); st.executeUpdate() }
    }

    private fun bind(st: PreparedStatement, args: Array<out Any?>) {
        args.forEachIndexed { i, a ->
            when (a) {
                null -> st.setObject(i + 1, null)
                is Int -> st.setInt(i + 1, a)
                is Long -> st.setLong(i + 1, a)
                is Float -> st.setFloat(i + 1, a)
                is Boolean -> st.setBoolean(i + 1, a)
                is String -> st.setString(i + 1, a)
                else -> st.setObject(i + 1, a)
            }
        }
    }

    // ── Строки ───────────────────────────────────────────────────────

    private val userCols = "id, login, name, hue, pass_hash, salt, public_key, profile, profile_updated, created"
    private fun userOf(r: ResultSet) = UserRow(
        r.getInt(1), r.getString(2), r.getString(3), r.getFloat(4), r.getString(5), r.getString(6),
        r.getString(7), r.getString(8), r.getLong(9), r.getLong(10),
    )

    private val deviceCols = "id, user_id, name, platform, last_seen, refresh_hash, access_hash, access_until"
    private fun deviceOf(r: ResultSet) = DeviceRow(r.getString(1), r.getInt(2), r.getString(3), r.getString(4), r.getLong(5), r.getString(6), r.getString(7), r.getLong(8))

    private val postCols = "id, author, created, body, likes, comments_off"
    private fun postOf(r: ResultSet) = PostRow(r.getLong(1), r.getInt(2), r.getLong(3), r.getString(4), r.getInt(5), r.getBoolean(6))

    private val chatCols = "id, kind, title, owner, created"
    private fun chatOf(r: ResultSet) = ChatRow(r.getInt(1), r.getString(2), r.getString(3), r.getInt(4), r.getLong(5))

    private fun memberOf(r: ResultSet) = MemberRow(r.getInt(1), r.getInt(2), r.getBoolean(3), r.getBoolean(4))
    private fun messageOf(r: ResultSet) = MessageRow(r.getLong(1), r.getInt(2), r.getInt(3), r.getLong(4), r.getString(5))
    private fun uploadOf(r: ResultSet) = UploadRow(r.getString(1), r.getInt(2), r.getLong(3), r.getLong(4), r.getString(5), r.getLong(6))

    // ── Люди ─────────────────────────────────────────────────────────

    override fun createUser(login: String, name: String, hue: Float, passHash: String, salt: String, now: Long): UserRow? =
        query(
            "INSERT INTO users (login, name, hue, pass_hash, salt, created) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (login) DO NOTHING RETURNING $userCols",
            login, name, hue, passHash, salt, now, map = ::userOf,
        ).firstOrNull()

    override fun user(id: Int) = query("SELECT $userCols FROM users WHERE id = ?", id, map = ::userOf).firstOrNull()
    override fun userByLogin(login: String) = query("SELECT $userCols FROM users WHERE login = ?", login, map = ::userOf).firstOrNull()

    override fun searchUsers(query: String, limit: Int): List<UserRow> = search(query, limit)

    private fun search(text: String, limit: Int): List<UserRow> {
        val like = "%" + text.lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        return query("SELECT $userCols FROM users WHERE login LIKE ? OR lower(name) LIKE ? ORDER BY login LIMIT ?", like, like, limit, map = ::userOf)
    }

    override fun updateUser(row: UserRow) {
        update(
            "UPDATE users SET name = ?, hue = ?, public_key = ?, profile = ?, profile_updated = ? WHERE id = ?",
            row.name, row.hue, row.publicKey, row.profile, row.profileUpdated, row.id,
        )
    }

    // ── Устройства ───────────────────────────────────────────────────

    override fun saveDevice(row: DeviceRow) {
        update(
            """INSERT INTO devices ($deviceCols) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
               ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, platform = EXCLUDED.platform, last_seen = EXCLUDED.last_seen,
               refresh_hash = EXCLUDED.refresh_hash, access_hash = EXCLUDED.access_hash, access_until = EXCLUDED.access_until""",
            row.id, row.userId, row.name, row.platform, row.lastSeen, row.refreshHash, row.accessHash, row.accessUntil,
        )
    }

    override fun device(id: String) = query("SELECT $deviceCols FROM devices WHERE id = ?", id, map = ::deviceOf).firstOrNull()
    override fun deviceByAccess(hash: String) = query("SELECT $deviceCols FROM devices WHERE access_hash = ?", hash, map = ::deviceOf).firstOrNull()
    override fun deviceByRefresh(hash: String) = query("SELECT $deviceCols FROM devices WHERE refresh_hash = ?", hash, map = ::deviceOf).firstOrNull()
    override fun devices(userId: Int) = query("SELECT $deviceCols FROM devices WHERE user_id = ?", userId, map = ::deviceOf)
    override fun deleteDevice(id: String) { update("DELETE FROM devices WHERE id = ?", id) }

    // ── Записи ───────────────────────────────────────────────────────

    override fun createPost(author: Int, created: Long, body: String): PostRow =
        query("INSERT INTO posts (author, created, body) VALUES (?, ?, ?) RETURNING $postCols", author, created, body, map = ::postOf).first()

    override fun post(id: Long) = query("SELECT $postCols FROM posts WHERE id = ?", id, map = ::postOf).firstOrNull()

    override fun posts(author: Int?, beforeCreated: Long?, beforeId: Long?, limit: Int): List<PostRow> {
        val where = ArrayList<String>()
        val args = ArrayList<Any?>()
        if (author != null) { where += "author = ?"; args += author }
        if (beforeCreated != null) {
            where += "(created < ? OR (created = ? AND id < ?))"
            args += beforeCreated; args += beforeCreated; args += beforeId ?: Long.MAX_VALUE
        }
        args += limit
        val w = if (where.isEmpty()) "" else "WHERE " + where.joinToString(" AND ")
        return query("SELECT $postCols FROM posts $w ORDER BY created DESC, id DESC LIMIT ?", *args.toTypedArray(), map = ::postOf)
    }

    override fun setCommentsOff(id: Long, off: Boolean) { update("UPDATE posts SET comments_off = ? WHERE id = ?", off, id) }

    override fun setLike(postId: Long, userId: Int, liked: Boolean): Int = tx { c ->
        val sql = if (liked) "INSERT INTO likes (post_id, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING" else "DELETE FROM likes WHERE post_id = ? AND user_id = ?"
        c.prepareStatement(sql).use { st -> st.setLong(1, postId); st.setInt(2, userId); st.executeUpdate() }
        c.prepareStatement("UPDATE posts SET likes = (SELECT count(*) FROM likes WHERE post_id = ?) WHERE id = ? RETURNING likes").use { st ->
            st.setLong(1, postId); st.setLong(2, postId)
            st.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    override fun likedBy(userId: Int, postIds: List<Long>): Set<Long> {
        if (postIds.isEmpty()) return emptySet()
        val marks = postIds.joinToString(",") { "?" }
        return query("SELECT post_id FROM likes WHERE user_id = ? AND post_id IN ($marks)", userId, *postIds.toTypedArray()) { it.getLong(1) }.toSet()
    }

    // ── Переписки ────────────────────────────────────────────────────

    override fun createChat(row: ChatRow, members: List<MemberRow>): Boolean = tx { c ->
        val inserted = c.prepareStatement("INSERT INTO chats ($chatCols) VALUES (?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING").use { st ->
            st.setInt(1, row.id); st.setString(2, row.kind); st.setString(3, row.title); st.setInt(4, row.owner); st.setLong(5, row.created)
            st.executeUpdate()
        }
        if (inserted == 0) return@tx false
        c.prepareStatement("INSERT INTO members (chat_id, user_id, admin, accepted) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING").use { st ->
            for (m in members) {
                st.setInt(1, m.chatId); st.setInt(2, m.userId); st.setBoolean(3, m.admin); st.setBoolean(4, m.accepted)
                st.addBatch()
            }
            st.executeBatch()
        }
        true
    }

    override fun chat(id: Int) = query("SELECT $chatCols FROM chats WHERE id = ?", id, map = ::chatOf).firstOrNull()

    override fun directChat(a: Int, b: Int): ChatRow? = query(
        """SELECT c.id, c.kind, c.title, c.owner, c.created FROM chats c
           JOIN members x ON x.chat_id = c.id AND x.user_id = ?
           JOIN members y ON y.chat_id = c.id AND y.user_id = ?
           WHERE c.kind = 'direct' LIMIT 1""",
        a, b, map = ::chatOf,
    ).firstOrNull()

    override fun chatsOf(userId: Int): List<ChatRow> = query(
        "SELECT c.id, c.kind, c.title, c.owner, c.created FROM chats c JOIN members m ON m.chat_id = c.id WHERE m.user_id = ?",
        userId, map = ::chatOf,
    )

    override fun updateChatTitle(id: Int, title: String) { update("UPDATE chats SET title = ? WHERE id = ?", title, id) }

    override fun members(chatId: Int) =
        query("SELECT chat_id, user_id, admin, accepted FROM members WHERE chat_id = ? ORDER BY admin DESC, user_id", chatId, map = ::memberOf)
            .let { ms -> chat(chatId)?.owner?.let { owner -> ms.sortedBy { if (it.userId == owner) 0 else 1 } } ?: ms }

    override fun saveMember(row: MemberRow) {
        update(
            "INSERT INTO members (chat_id, user_id, admin, accepted) VALUES (?, ?, ?, ?) ON CONFLICT (chat_id, user_id) DO UPDATE SET admin = EXCLUDED.admin, accepted = EXCLUDED.accepted",
            row.chatId, row.userId, row.admin, row.accepted,
        )
    }

    override fun removeMember(chatId: Int, userId: Int) { update("DELETE FROM members WHERE chat_id = ? AND user_id = ?", chatId, userId) }

    override fun addMessage(chatId: Int, author: Int, sentAt: Long, body: String): MessageRow = query(
        "INSERT INTO messages (chat_id, author, sent_at, body) VALUES (?, ?, ?, ?) RETURNING id, chat_id, author, sent_at, body",
        chatId, author, sentAt, body, map = ::messageOf,
    ).first()

    override fun messages(chatId: Int, before: Long?, limit: Int): List<MessageRow> =
        if (before == null) query("SELECT id, chat_id, author, sent_at, body FROM messages WHERE chat_id = ? ORDER BY id DESC LIMIT ?", chatId, limit, map = ::messageOf)
        else query("SELECT id, chat_id, author, sent_at, body FROM messages WHERE chat_id = ? AND id < ? ORDER BY id DESC LIMIT ?", chatId, before, limit, map = ::messageOf)

    // ── Загрузки ─────────────────────────────────────────────────────

    override fun createUpload(row: UploadRow) {
        update("INSERT INTO uploads (id, owner, length, \"offset\", meta, created) VALUES (?, ?, ?, ?, ?, ?)", row.id, row.owner, row.length, row.offset, row.meta, row.created)
    }

    override fun upload(id: String) = query("SELECT id, owner, length, \"offset\", meta, created FROM uploads WHERE id = ?", id, map = ::uploadOf).firstOrNull()
    override fun setUploadOffset(id: String, offset: Long) { update("UPDATE uploads SET \"offset\" = ? WHERE id = ?", offset, id) }
    override fun deleteUpload(id: String) { update("DELETE FROM uploads WHERE id = ?", id) }

    // ── Повторы ──────────────────────────────────────────────────────

    override fun idem(userId: Int, key: String) = query("SELECT status, body FROM idem WHERE user_id = ? AND key = ?", userId, key) { IdemRow(it.getInt(1), it.getString(2)) }.firstOrNull()

    override fun saveIdem(userId: Int, key: String, row: IdemRow, now: Long) {
        update("INSERT INTO idem (user_id, key, status, body, created) VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING", userId, key, row.status, row.body, now)
        // Ключи старше суток больше не нужны
        update("DELETE FROM idem WHERE created < ?", now - 86_400_000L)
    }

    /** Только для тестов на пустой базе: стереть всё. */
    fun wipe() {
        tx { c ->
            c.createStatement().use { st ->
                st.execute("TRUNCATE idem, uploads, messages, members, chats, likes, posts, devices, users RESTART IDENTITY")
                st.execute("ALTER SEQUENCE user_seq RESTART WITH 100001")
                st.execute("ALTER SEQUENCE post_seq RESTART WITH 10000001")
            }
        }
    }
}
