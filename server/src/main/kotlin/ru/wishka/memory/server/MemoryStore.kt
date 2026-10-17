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
    private val follows = LinkedHashMap<Pair<Int, Int>, FollowRow>()
    private val commentRows = LinkedHashMap<Long, CommentRow>()
    private val commentLikes = HashSet<Pair<Long, Int>>()
    private val storyRows = LinkedHashMap<Long, StoryRow>()
    private val views = LinkedHashMap<Pair<Long, Int>, StoryViewRow>()
    private val close = HashMap<Int, Set<Int>>()
    private val reportRows = LinkedHashMap<Long, ReportRow>()
    private val blocks = HashSet<Pair<Int, Int>>()
    private var reportSeq = 1L
    // Номера сервера — с 10 000 001, как у записей: у телефона свои, меньше
    private var commentSeq = 10_000_001L
    private var storySeq = 10_000_001L
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
    override fun updatePassword(id: Int, passHash: String, salt: String) = synchronized(lock) {
        users[id]?.let { users[id] = it.copy(passHash = passHash, salt = salt) }
        Unit
    }

    override fun saveDevice(row: DeviceRow) = synchronized(lock) { devices[row.id] = row }
    override fun device(id: String) = synchronized(lock) { devices[id] }
    override fun deviceByAccess(hash: String) = synchronized(lock) { devices.values.firstOrNull { it.accessHash == hash } }
    override fun deviceByRefresh(hash: String) = synchronized(lock) { devices.values.firstOrNull { it.refreshHash == hash } }
    override fun devices(userId: Int) = synchronized(lock) { devices.values.filter { it.userId == userId } }
    override fun deleteDevice(id: String) = synchronized(lock) { devices.remove(id); Unit }

    override fun clearPushToken(token: String) = synchronized(lock) {
        devices.values.filter { it.pushToken == token }.forEach { devices[it.id] = it.copy(pushToken = "") }
    }

    override fun createPost(author: Int, created: Long, body: String, community: Int, kind: String): PostRow = synchronized(lock) {
        val row = PostRow(++postSeq, author, created, body, 0, false, community, kind)
        posts[row.id] = row
        row
    }

    override fun updatePostBody(id: Long, body: String) = synchronized(lock) { posts[id]?.let { posts[id] = it.copy(body = body) }; Unit }

    /** Личная запись (не в сообществе и не PRISM). */
    private val PostRow.plain: Boolean get() = community == 0 && kind == "post"

    override fun communityPosts(community: Int, beforeCreated: Long?, beforeId: Long?, limit: Int) = synchronized(lock) {
        posts.values.filter { it.community == community && it.kind == "post" && before(it, beforeCreated, beforeId) }.sortedWith(newest).take(limit)
    }

    override fun prisms(beforeCreated: Long?, beforeId: Long?, limit: Int) = synchronized(lock) {
        posts.values.filter { it.kind == "prism" && before(it, beforeCreated, beforeId) }.sortedWith(newest).take(limit)
    }

    private val communityRows = LinkedHashMap<Int, CommunityRow>()
    private val communityMembers = LinkedHashMap<Pair<Int, Int>, Long>()
    private val highlightRows = HashMap<Int, String>()
    private var communitySeq = 50_000_000

    override fun createCommunity(owner: Int, name: String, topic: String, created: Long, body: String) = synchronized(lock) {
        val row = CommunityRow(++communitySeq, owner, name, topic, created, body)
        communityRows[row.id] = row
        communityMembers[row.id to owner] = created
        row
    }

    override fun community(id: Int) = synchronized(lock) { communityRows[id] }

    override fun updateCommunity(id: Int, name: String, topic: String, body: String) = synchronized(lock) {
        communityRows[id]?.let { communityRows[id] = it.copy(name = name, topic = topic, body = body) }
        Unit
    }

    override fun deleteCommunity(id: Int) = synchronized(lock) {
        communityRows.remove(id)
        communityMembers.keys.removeAll { it.first == id }
        posts.values.filter { it.community == id }.map { it.id }.forEach(::deletePost)
    }

    override fun communities(query: String?, topic: String?, limit: Int) = synchronized(lock) {
        val q = query?.lowercase()
        communityRows.values.filter { (q == null || it.name.lowercase().contains(q)) && (topic == null || it.topic == topic) }
            .sortedWith(compareByDescending<CommunityRow> { c -> communityMembers.keys.count { it.first == c.id } }.thenByDescending { it.id })
            .take(limit)
    }

    override fun setMember(community: Int, userId: Int, on: Boolean, at: Long) = synchronized(lock) {
        if (on) communityMembers.putIfAbsent(community to userId, at) else communityMembers.remove(community to userId)
        Unit
    }

    override fun isMember(community: Int, userId: Int) = synchronized(lock) { (community to userId) in communityMembers }
    override fun memberCount(community: Int) = synchronized(lock) { communityMembers.keys.count { it.first == community } }
    override fun communitiesOf(userId: Int) = synchronized(lock) {
        communityMembers.keys.filter { it.second == userId }.mapNotNull { communityRows[it.first] }
    }

    override fun highlights(owner: Int) = synchronized(lock) { highlightRows[owner] ?: "[]" }
    override fun setHighlights(owner: Int, body: String) = synchronized(lock) { highlightRows[owner] = body }

    override fun post(id: Long) = synchronized(lock) { posts[id] }

    override fun posts(author: Int?, beforeCreated: Long?, beforeId: Long?, limit: Int): List<PostRow> = synchronized(lock) {
        posts.values
            .filter { it.plain && (author == null || it.author == author) }
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

    private val newest = compareByDescending<PostRow> { it.created }.thenByDescending { it.id }

    private fun before(p: PostRow, beforeCreated: Long?, beforeId: Long?) =
        beforeCreated == null || p.created < beforeCreated || (p.created == beforeCreated && p.id < (beforeId ?: Long.MAX_VALUE))

    override fun postsBy(authors: Collection<Int>, beforeCreated: Long?, beforeId: Long?, limit: Int): List<PostRow> = synchronized(lock) {
        val set = authors.toSet()
        posts.values.filter { it.plain && it.author in set && before(it, beforeCreated, beforeId) }.sortedWith(newest).take(limit)
    }

    override fun explore(exclude: Collection<Int>, beforeCreated: Long?, beforeId: Long?, limit: Int): List<PostRow> = synchronized(lock) {
        val set = exclude.toSet()
        posts.values.filter { it.plain && it.author !in set && users[it.author]?.closed == false && before(it, beforeCreated, beforeId) }
            .sortedWith(newest).take(limit)
    }

    override fun follow(follower: Int, followee: Int) = synchronized(lock) { follows[follower to followee] }
    override fun saveFollow(row: FollowRow) = synchronized(lock) { follows[row.follower to row.followee] = row }
    override fun removeFollow(follower: Int, followee: Int) = synchronized(lock) { follows.remove(follower to followee); Unit }
    override fun following(userId: Int, accepted: Boolean) = synchronized(lock) {
        follows.values.filter { it.follower == userId && it.accepted == accepted }.map { it.followee }
    }
    override fun followers(userId: Int, accepted: Boolean) = synchronized(lock) {
        follows.values.filter { it.followee == userId && it.accepted == accepted }.map { it.follower }
    }

    override fun createComment(postId: Long, author: Int, created: Long, body: String): CommentRow = synchronized(lock) {
        val row = CommentRow(commentSeq++, postId, author, created, body, 0)
        commentRows[row.id] = row
        row
    }

    override fun comment(id: Long) = synchronized(lock) { commentRows[id] }

    override fun comments(postId: Long, afterId: Long?, limit: Int) = synchronized(lock) {
        commentRows.values.filter { it.postId == postId && (afterId == null || it.id > afterId) }.sortedBy { it.id }.take(limit)
    }

    override fun lastComments(postId: Long, n: Int) = synchronized(lock) {
        commentRows.values.filter { it.postId == postId }.sortedBy { it.id }.takeLast(n)
    }

    override fun commentCounts(postIds: Collection<Long>): Map<Long, Int> = synchronized(lock) {
        val set = postIds.toSet()
        commentRows.values.filter { it.postId in set }.groupingBy { it.postId }.eachCount()
    }

    override fun deleteComment(id: Long) = synchronized(lock) {
        commentRows.remove(id)
        commentLikes.removeAll { it.first == id }
        Unit
    }

    override fun setCommentLike(commentId: Long, userId: Int, liked: Boolean): Int = synchronized(lock) {
        if (liked) commentLikes += commentId to userId else commentLikes -= commentId to userId
        val n = commentLikes.count { it.first == commentId }
        commentRows[commentId]?.let { commentRows[commentId] = it.copy(likes = n) }
        n
    }

    override fun commentsLikedBy(userId: Int, ids: Collection<Long>) = synchronized(lock) { ids.filter { (it to userId) in commentLikes }.toSet() }

    override fun createStory(author: Int, created: Long, expires: Long, body: String, closeFriends: Boolean): StoryRow = synchronized(lock) {
        val row = StoryRow(storySeq++, author, created, expires, body, closeFriends)
        storyRows[row.id] = row
        row
    }

    override fun story(id: Long) = synchronized(lock) { storyRows[id] }

    override fun stories(authors: Collection<Int>, now: Long) = synchronized(lock) {
        val set = authors.toSet()
        storyRows.values.filter { it.author in set && it.expires > now }.sortedBy { it.created }
    }

    override fun deleteStory(id: Long) = synchronized(lock) {
        storyRows.remove(id)
        views.keys.removeAll { it.first == id }
        Unit
    }

    override fun saveStoryView(row: StoryViewRow) = synchronized(lock) { views[row.storyId to row.userId] = row }
    override fun storyViews(storyId: Long) = synchronized(lock) { views.values.filter { it.storyId == storyId }.sortedBy { it.at } }
    override fun storiesSeenBy(userId: Int, ids: Collection<Long>) = synchronized(lock) { ids.filter { views.containsKey(it to userId) }.toSet() }
    override fun closeFriends(owner: Int) = synchronized(lock) { close[owner].orEmpty() }
    override fun setCloseFriends(owner: Int, ids: Collection<Int>) = synchronized(lock) { close[owner] = ids.toSet() }

    override fun createReport(row: ReportRow): ReportRow = synchronized(lock) {
        val r = row.copy(id = reportSeq++)
        reportRows[r.id] = r
        r
    }

    override fun report(id: Long) = synchronized(lock) { reportRows[id] }

    override fun reports(status: String?, limit: Int) = synchronized(lock) {
        reportRows.values.filter { status == null || it.status == status }.sortedByDescending { it.id }.take(limit)
    }

    override fun setReportStatus(id: Long, status: String) = synchronized(lock) { reportRows[id]?.let { reportRows[id] = it.copy(status = status) }; Unit }

    override fun setBlock(blocker: Int, blocked: Int, on: Boolean) = synchronized(lock) {
        if (on) blocks += blocker to blocked else blocks -= blocker to blocked
        Unit
    }

    override fun blockedBy(userId: Int) = synchronized(lock) { blocks.filter { it.first == userId }.map { it.second }.toSet() }
    override fun blockedEither(a: Int, b: Int) = synchronized(lock) { (a to b) in blocks || (b to a) in blocks }

    override fun deletePost(id: Long) = synchronized(lock) {
        posts.remove(id)
        likes.removeAll { it.first == id }
        val gone = commentRows.values.filter { it.postId == id }.map { it.id }.toSet()
        commentRows.keys.removeAll(gone)
        commentLikes.removeAll { it.first in gone }
        Unit
    }

    override fun uploadsOf(owner: Int) = synchronized(lock) { uploads.values.filter { it.owner == owner } }

    override fun deleteUser(id: Int) = synchronized(lock) {
        users.remove(id)
        communityRows.values.filter { it.owner == id }.map { it.id }.forEach(::deleteCommunity)
        communityMembers.keys.removeAll { it.second == id }
        highlightRows.remove(id)
        messages.removeAll { it.author == id }
        reportRows.values.removeAll { it.reporter == id || it.author == id }
        // Свои группы — следующему участнику (сначала админам); пустые — удалить
        chats.values.filter { it.owner == id }.forEach { c ->
            val heir = members.filter { it.chatId == c.id && it.userId != id }.sortedWith(compareByDescending<MemberRow> { it.admin }.thenBy { it.userId }).firstOrNull()
            if (heir == null) chats.remove(c.id) else {
                chats[c.id] = c.copy(owner = heir.userId)
                members.replaceAll { if (it === heir) it.copy(admin = true) else it }
            }
        }
        // «Нравится» этого человека уходят — счётчики пересчитать
        val likedPosts = likes.filter { it.second == id }.map { it.first }.toSet()
        val likedComments = commentLikes.filter { it.second == id }.map { it.first }.toSet()
        devices.values.removeAll { it.userId == id }
        posts.values.filter { it.author == id }.map { it.id }.forEach { pid ->
            posts.remove(pid)
            likes.removeAll { it.first == pid }
            commentRows.values.removeAll { it.postId == pid }
        }
        likes.removeAll { it.second == id }
        likedPosts.forEach { pid -> posts[pid]?.let { posts[pid] = it.copy(likes = likes.count { l -> l.first == pid }) } }
        commentRows.values.removeAll { it.author == id }
        commentLikes.removeAll { it.second == id }
        likedComments.forEach { cid -> commentRows[cid]?.let { commentRows[cid] = it.copy(likes = commentLikes.count { l -> l.first == cid }) } }
        follows.keys.removeAll { it.first == id || it.second == id }
        storyRows.values.filter { it.author == id }.map { it.id }.forEach { sid -> storyRows.remove(sid); views.keys.removeAll { it.first == sid } }
        views.keys.removeAll { it.second == id }
        close.remove(id)
        close.keys.toList().forEach { k -> close[k] = close[k].orEmpty() - id }
        members.removeAll { it.userId == id }
        uploads.values.removeAll { it.owner == id }
        blocks.removeAll { it.first == id || it.second == id }
        idem.keys.removeAll { it.first == id }
        Unit
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

    override fun messageAt(chatId: Int, author: Int, sentAt: Long) = synchronized(lock) {
        messages.firstOrNull { it.chatId == chatId && it.author == author && it.sentAt == sentAt }
    }

    override fun createUpload(row: UploadRow) = synchronized(lock) { uploads[row.id] = row }
    override fun upload(id: String) = synchronized(lock) { uploads[id] }
    override fun setUploadOffset(id: String, offset: Long) = synchronized(lock) { uploads[id]?.let { uploads[id] = it.copy(offset = offset) }; Unit }
    override fun deleteUpload(id: String) = synchronized(lock) { uploads.remove(id); Unit }

    override fun idem(userId: Int, key: String) = synchronized(lock) { idem[userId to key] }
    override fun saveIdem(userId: Int, key: String, row: IdemRow, now: Long) = synchronized(lock) { idem[userId to key] = row }
}
