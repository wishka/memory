package ru.wishka.memory.server

/** Хранилище в памяти — для тестов и запуска без базы (данные пропадают при перезапуске). */
class MemoryStore : Store {
    private val lock = Any()
    private val users = LinkedHashMap<Int, UserRow>()
    private val devices = LinkedHashMap<String, DeviceRow>()
    private val posts = LinkedHashMap<Long, PostRow>()
    private val likes = HashSet<Pair<Long, Int>>()
    private val chats = LinkedHashMap<Int, ChatRow>()
    private val members = ArrayList<MemberRow>()
    private val messages = ArrayList<MessageRow>()
    private val uploads = LinkedHashMap<String, UploadRow>()
    private val idem = HashMap<Pair<Int, String>, IdemRow>()
    private var userSeq = 100_000
    private var postSeq = 10_000_000L
    private var messageSeq = 1L

    override fun createUser(login: String, name: String, hue: Float, passHash: String, salt: String, now: Long): UserRow? = synchronized(lock) {
        if (users.values.any { it.login == login }) return null
        val row = UserRow(++userSeq, login, name, hue, passHash, salt, created = now)
        users[row.id] = row
        row
    }

    override fun user(id: Int) = synchronized(lock) { users[id] }
    override fun userByLogin(login: String) = synchronized(lock) { users.values.firstOrNull { it.login == login } }

    override fun searchUsers(query: String, limit: Int): List<UserRow> = synchronized(lock) {
        val q = query.lowercase()
        users.values.filter { it.login.contains(q) || it.name.lowercase().contains(q) }.take(limit)
    }

    override fun updateUser(row: UserRow) = synchronized(lock) { users[row.id] = row }

    override fun saveDevice(row: DeviceRow) = synchronized(lock) { devices[row.id] = row }
    override fun device(id: String) = synchronized(lock) { devices[id] }
    override fun deviceByAccess(hash: String) = synchronized(lock) { devices.values.firstOrNull { it.accessHash == hash } }
    override fun deviceByRefresh(hash: String) = synchronized(lock) { devices.values.firstOrNull { it.refreshHash == hash } }
    override fun devices(userId: Int) = synchronized(lock) { devices.values.filter { it.userId == userId } }
    override fun deleteDevice(id: String) = synchronized(lock) { devices.remove(id); Unit }

    override fun createPost(author: Int, created: Long, body: String): PostRow = synchronized(lock) {
        val row = PostRow(++postSeq, author, created, body, 0, false)
        posts[row.id] = row
        row
    }

    override fun post(id: Long) = synchronized(lock) { posts[id] }

    override fun posts(author: Int?, beforeCreated: Long?, beforeId: Long?, limit: Int): List<PostRow> = synchronized(lock) {
        posts.values
            .filter { author == null || it.author == author }
            .filter { beforeCreated == null || it.created < beforeCreated || (it.created == beforeCreated && it.id < (beforeId ?: Long.MAX_VALUE)) }
            .sortedWith(compareByDescending<PostRow> { it.created }.thenByDescending { it.id })
            .take(limit)
    }

    override fun setCommentsOff(id: Long, off: Boolean) = synchronized(lock) { posts[id]?.let { posts[id] = it.copy(commentsOff = off) }; Unit }

    override fun setLike(postId: Long, userId: Int, liked: Boolean): Int = synchronized(lock) {
        if (liked) likes += postId to userId else likes -= postId to userId
        val n = likes.count { it.first == postId }
        posts[postId]?.let { posts[postId] = it.copy(likes = n) }
        n
    }

    override fun likedBy(userId: Int, postIds: List<Long>): Set<Long> = synchronized(lock) {
        postIds.filter { (it to userId) in likes }.toSet()
    }

    override fun createChat(row: ChatRow, members: List<MemberRow>): Boolean = synchronized(lock) {
        if (chats.containsKey(row.id)) return false
        chats[row.id] = row
        this.members += members
        true
    }

    override fun chat(id: Int) = synchronized(lock) { chats[id] }

    override fun directChat(a: Int, b: Int): ChatRow? = synchronized(lock) {
        chats.values.firstOrNull { c ->
            c.direct && members.filter { it.chatId == c.id }.map { it.userId }.toSet() == setOf(a, b)
        }
    }

    override fun chatsOf(userId: Int): List<ChatRow> = synchronized(lock) {
        members.filter { it.userId == userId }.mapNotNull { chats[it.chatId] }
    }

    override fun updateChatTitle(id: Int, title: String) = synchronized(lock) { chats[id]?.let { chats[id] = it.copy(title = title) }; Unit }
    override fun members(chatId: Int) = synchronized(lock) { members.filter { it.chatId == chatId } }

    override fun saveMember(row: MemberRow) = synchronized(lock) {
        members.removeAll { it.chatId == row.chatId && it.userId == row.userId }
        members += row
        Unit
    }

    override fun removeMember(chatId: Int, userId: Int) = synchronized(lock) { members.removeAll { it.chatId == chatId && it.userId == userId }; Unit }

    override fun addMessage(chatId: Int, author: Int, sentAt: Long, body: String): MessageRow = synchronized(lock) {
        val row = MessageRow(messageSeq++, chatId, author, sentAt, body)
        messages += row
        row
    }

    override fun messages(chatId: Int, before: Long?, limit: Int): List<MessageRow> = synchronized(lock) {
        messages.filter { it.chatId == chatId && (before == null || it.id < before) }.sortedByDescending { it.id }.take(limit)
    }

    override fun createUpload(row: UploadRow) = synchronized(lock) { uploads[row.id] = row }
    override fun upload(id: String) = synchronized(lock) { uploads[id] }
    override fun setUploadOffset(id: String, offset: Long) = synchronized(lock) { uploads[id]?.let { uploads[id] = it.copy(offset = offset) }; Unit }
    override fun deleteUpload(id: String) = synchronized(lock) { uploads.remove(id); Unit }

    override fun idem(userId: Int, key: String) = synchronized(lock) { idem[userId to key] }
    override fun saveIdem(userId: Int, key: String, row: IdemRow, now: Long) = synchronized(lock) { idem[userId to key] = row }
}
