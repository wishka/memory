import XCTest
@testable import Memory

/// Раунд 21: истории (просмотры, ответы, «Актуальное», «Близкие друзья»),
/// репосты и «Отправить в чат», звонки, место и отметки на фото, светлая
/// тема. Те же проверки, что Round21Test на Android, и сверх них — чтение
/// старых сохранений и ключи JSON, общие с Android.
final class Round21Tests: XCTestCase {
    private var clock = TestClock(Date(timeIntervalSince1970: 1_800_000_000))

    private var now: Date { clock.now }

    private func makeModel() -> AppModel {
        let clock = self.clock
        return AppModel(clock: { clock.now }, demoReplies: false)
    }

    private func advance(_ ms: Int64) {
        clock.now = clock.now.addingTimeInterval(Double(ms) / 1000)
    }

    private func ms(_ value: Int64) -> Date { Date(timeIntervalSince1970: Double(value) / 1000) }

    private func resetTheme() {
        let theme = ThemeState.shared
        theme.mode = .system
        theme.systemDark = true
        theme.stageDark = false
        theme.overlayDark = false
    }

    override func setUp() {
        super.setUp()
        L10n.shared.setLanguage(.ru)
        L10n.shared.systemLanguage = "ru"
        clock = TestClock(Date(timeIntervalSince1970: 1_800_000_000))
        resetTheme()
    }

    override func tearDown() {
        resetTheme()
        super.tearDown()
    }

    // MARK: - Светлая тема

    func testThemeFollowsSystemAndChoice() {
        let theme = ThemeState.shared
        XCTAssertFalse(theme.light)
        theme.systemDark = false
        XCTAssertTrue(theme.light)
        theme.mode = .dark
        XCTAssertFalse(theme.light)
        theme.mode = .light
        theme.systemDark = true
        XCTAssertTrue(theme.light)
        // Звонок, PRISM, история — всегда ночные, но выбор человека не меняется
        theme.overlayDark = true
        XCTAssertFalse(theme.light)
        XCTAssertTrue(theme.chosenLight)
        theme.overlayDark = false
        theme.stageDark = true
        XCTAssertFalse(theme.light)
        XCTAssertEqual(ThemeMode.light, ThemeMode.of("light"))
        XCTAssertEqual(ThemeMode.system, ThemeMode.of("???"))
        XCTAssertEqual(ThemeMode.system, ThemeMode.of(nil))
    }

    func testThemeChoiceIsSavedOnThisPhone() {
        let defaults = UserDefaults(suiteName: "round21-theme")!
        defaults.removePersistentDomain(forName: "round21-theme")
        let prefs = DevicePrefs(defaults: defaults)
        prefs.setTheme(.light)
        XCTAssertEqual(ThemeMode.light, ThemeState.shared.mode)
        ThemeState.shared.mode = .system
        prefs.load()
        XCTAssertEqual(ThemeMode.light, ThemeState.shared.mode)
        // Светлая палитра — та же, что на Android
        XCTAssertEqual(RGB(0x7A47A8), Palette.light.lilac)
        XCTAssertEqual(RGB(0xBC92CD), Palette.dark.lilac)
        defaults.removePersistentDomain(forName: "round21-theme")
    }

    // MARK: - Истории

    func testStoryLivesADayThenStaysInArchive() {
        let vm = makeModel()
        XCTAssertNotNil(vm.publishStory(nil, "   ", bg: 0, closeFriends: false))
        XCTAssertNil(vm.publishStory(nil, "Привет", bg: 99, closeFriends: false))
        let active = vm.state.myActiveStories(now)
        XCTAssertEqual(1, active.count)
        let story = active[0]
        XCTAssertEqual(Stories.backgrounds.count - 1, story.bg)
        advance(Stories.ttlMs + 1)
        XCTAssertTrue(vm.state.myActiveStories(now).isEmpty)
        vm.pruneStories(now)
        // Своя — в архиве и после суток
        XCTAssertEqual([story.id], vm.state.storyArchive.map(\.id))
    }

    func testCloseFriendsStoriesVisibleOnlyToClose() {
        let story = Story(id: 1, authorId: 2, createdAt: now, text: "x", closeFriends: true)
        XCTAssertFalse(Stories.visible(story, viewerId: 3, isFriend: true, inAuthorsClose: false))
        XCTAssertTrue(Stories.visible(story, viewerId: 3, isFriend: true, inAuthorsClose: true))
        var open = story
        open.closeFriends = false
        XCTAssertFalse(Stories.visible(open, viewerId: 3, isFriend: false, inAuthorsClose: false))
        XCTAssertTrue(Stories.visible(story, viewerId: 2, isFriend: false, inAuthorsClose: false))
        // Близкими можно сделать только друзей
        let vm = makeModel()
        let friend = vm.state.following.sorted().first!
        let stranger = Demo.people.first { !vm.state.following.contains($0.id) }!.id
        vm.toggleCloseFriend(friend)
        vm.toggleCloseFriend(stranger)
        XCTAssertEqual([friend], vm.state.closeFriends)
        vm.toggleCloseFriend(friend)
        XCTAssertTrue(vm.state.closeFriends.isEmpty)
    }

    func testRingsPutUnseenFirstAndStartAtFirstUnseen() {
        let people = Demo.people
        let a = people[0].id
        let b = people[1].id
        let at = { (back: Double) in self.now.addingTimeInterval(-back / 1000) }
        let stories = [
            Story(id: 1, authorId: a, createdAt: at(3000)), Story(id: 2, authorId: a, createdAt: at(2000)),
            Story(id: 3, authorId: b, createdAt: at(1000)),
            Story(id: 4, authorId: b, createdAt: at(Double(Stories.ttlMs + 5))),
        ]
        let rings = Stories.rings(stories, people, meId: -1, seen: [3], now: now)
        XCTAssertEqual([a, b], rings.map(\.user.id))
        XCTAssertTrue(rings[1].seen)
        XCTAssertEqual([3], rings[1].stories.map(\.id))
        XCTAssertEqual(1, Stories.startIndex(rings[0].stories, [1]))
        XCTAssertEqual(0, Stories.startIndex(rings[0].stories, [1, 2]))
        // Просмотр — один на человека, сердечко не теряется
        var st = Story(id: 9, authorId: a, createdAt: now)
        st = Stories.view(st, 7, now, liked: true)
        st = Stories.view(st, 7, now.addingTimeInterval(0.005))
        XCTAssertEqual(1, st.views.count)
        XCTAssertTrue(st.views[0].liked)
        XCTAssertEqual(now, st.views[0].at)
        XCTAssertEqual(1, st.likes)
    }

    func testViewingRepliesAndLikesGoToChat() throws {
        let vm = makeModel()
        var seed: UInt64 = 3
        // Кружки у друзей появляются не всегда — пробуем, пока не появятся
        while vm.state.storyCircles(now).isEmpty && seed < 40 {
            var rng = Round20Rng(seed: seed)
            vm.ensureDemoStories(using: &rng)
            seed += 1
        }
        let circle = try XCTUnwrap(vm.state.storyCircles(now).first)
        vm.openStories(circle.user.id)
        let story = try XCTUnwrap(vm.currentStory())
        XCTAssertTrue(vm.state.seenStories.contains(story.id))
        XCTAssertTrue(vm.state.story(story.id)!.views.contains { $0.userId == vm.state.me.id })
        vm.replyToStory(story.id, "Красиво!")
        vm.likeStory(story.id)
        vm.likeStory(story.id) // второй раз — ничего
        let chat = try XCTUnwrap(vm.state.chats.first { $0.peer.id == circle.user.id })
        let replies = chat.messages.filter { $0.story?.storyId == story.id }
        XCTAssertEqual(["Красиво!", "❤️"], replies.map(\.text))
        // Листаем до конца — просмотр закрывается
        for _ in 0..<50 { vm.nextStory() }
        XCTAssertNil(vm.state.storyViewer)
    }

    func testHighlightsKeepStoriesAndFollowDeletion() throws {
        let vm = makeModel()
        vm.publishStory(nil, "Раз", bg: 0, closeFriends: false)
        vm.publishStory(nil, "Два", bg: 1, closeFriends: false)
        let ids = vm.state.myActiveStories(now).map(\.id)
        XCTAssertNil(vm.createHighlight("Чужое", [-5]))
        let h = try XCTUnwrap(vm.createHighlight("  Лето  ", ids))
        let me = vm.state.me.id
        XCTAssertEqual("Лето", vm.state.highlightsOf(me).first?.title)
        XCTAssertEqual(1, vm.state.highlightsOf(me).count)
        vm.renameHighlight(h, "Очень длинное название подборки историй")
        XCTAssertEqual(Stories.maxTitle, vm.state.highlightsOf(me).first?.title.count)
        // Сутки прошли — истории остаются в подборке и открываются
        advance(Stories.ttlMs * 3)
        vm.pruneStories(now)
        vm.openHighlight(h)
        XCTAssertEqual(ids.first, vm.currentStory()?.id)
        vm.closeStories()
        vm.deleteStory(ids[0])
        XCTAssertEqual([ids[1]], vm.state.highlightsOf(me).first?.storyIds)
        vm.deleteStory(ids[1])
        XCTAssertTrue(vm.state.highlightsOf(me).isEmpty)
    }

    // MARK: - Репосты

    func testRepostWrapsOriginalAndCountsIt() throws {
        let vm = makeModel()
        let s = vm.state
        let original = try XCTUnwrap(s.feed.first { $0.author.id != s.me.id && !$0.author.closed && $0.artSeed == nil })
        XCTAssertNil(vm.repost(original.id))
        let mine = vm.state.posts[0]
        XCTAssertEqual(original.id, mine.repostOf?.postId)
        XCTAssertEqual(1, vm.state.findPost(original.id)?.reposts)
        // Репост репоста без слов — указывает на первоисточник
        XCTAssertEqual(original.id, vm.postRef(mine).postId)
        XCTAssertNil(vm.repost(mine.id, "Согласен"))
        let second = vm.state.posts[0]
        XCTAssertEqual(original.id, second.repostOf?.postId)
        XCTAssertEqual("Согласен", second.text)
        // Свою запись — только со своими словами
        vm.publish("Моя")
        let own = vm.state.posts[0]
        XCTAssertNotNil(vm.repost(own.id))
        XCTAssertNil(vm.repost(own.id, "Напоминаю"))
        // Поиск находит и по тексту оригинала
        let word = try XCTUnwrap(original.text.split(separator: " ").first { $0.count > 3 }.map(String.init))
        XCTAssertTrue(Search.posts(word, vm.state.posts).contains { $0.repostOf != nil })
    }

    func testSendPostToChatLeavesCard() throws {
        let vm = makeModel()
        let post = try XCTUnwrap(vm.state.feed.first)
        let chat = try XCTUnwrap(vm.state.chats.first)
        vm.openSendToChat(post.id)
        XCTAssertEqual(post.id, vm.state.sharingPostId)
        vm.sendPostToChat(post.id, chat.id, "Смотри")
        XCTAssertNil(vm.state.sharingPostId)
        let m = try XCTUnwrap(vm.state.chats.first { $0.id == chat.id }?.messages.last)
        XCTAssertEqual(post.id, m.post?.postId)
        XCTAssertEqual("Смотри", m.text)
        XCTAssertTrue(!vm.back() || vm.state.sharingPostId == nil)
        // Шторки репоста и «в чат» — одна за раз, «назад» закрывает
        vm.openRepost(post.id)
        XCTAssertEqual(post.id, vm.state.repostingPostId)
        XCTAssertTrue(vm.back())
        XCTAssertNil(vm.state.repostingPostId)
    }

    // MARK: - Звонки

    func testOutgoingCallLifecycle() throws {
        let vm = makeModel()
        let friend = vm.state.following.sorted().first!
        XCTAssertNil(vm.startCall(friend, video: true))
        XCTAssertNotNil(vm.startCall(friend, video: false)) // уже идёт
        XCTAssertEqual(CallPhase.calling, vm.state.call?.phase)
        // «Назад» звонок не сбрасывает
        XCTAssertTrue(vm.back())
        XCTAssertNotNil(vm.state.call)
        vm.callAnswered()
        advance(125_000)
        vm.toggleMute()
        vm.flipCamera()
        XCTAssertEqual(true, vm.state.call?.muted)
        XCTAssertEqual(false, vm.state.call?.frontCamera)
        vm.endCall()
        XCTAssertNil(vm.state.call)
        XCTAssertEqual(1, vm.state.callLog.count)
        let r = vm.state.callLog[0]
        XCTAssertTrue(r.outgoing && r.video && !r.missed)
        XCTAssertEqual(125_000, r.durationMs)
        XCTAssertEqual("Видеозвонок · 2:05", Calls.title(r))
        let chat = try XCTUnwrap(vm.state.chats.first { $0.peer.id == friend })
        XCTAssertEqual(r.id, chat.messages.last?.call?.id)
    }

    func testIncomingCallRespectsPrivacyAndMissedGoesToActivity() {
        let vm = makeModel()
        let friend = vm.state.following.sorted().first!
        let stranger = Demo.people.first { !vm.state.following.contains($0.id) }!.id
        var privacy = vm.state.privacy
        privacy.calls = .following
        vm.setPrivacy(privacy)
        vm.incomingCall(stranger, video: false)
        XCTAssertNil(vm.state.call)
        vm.incomingCall(friend, video: false)
        XCTAssertEqual(CallPhase.incoming, vm.state.call?.phase)
        vm.callTimeout()
        XCTAssertEqual(1, vm.state.callLog.count)
        let r = vm.state.callLog[0]
        XCTAssertTrue(r.missed)
        XCTAssertEqual(ActivityKind.missedCall, vm.state.activity.first?.kind)
        XCTAssertTrue(Calls.title(r).hasPrefix("Пропущенный"))
        // Отклонённый
        vm.incomingCall(friend, video: true)
        vm.declineCall()
        XCTAssertEqual(true, vm.state.callLog.first?.declined)
        // Никому
        privacy.calls = .nobody
        vm.setPrivacy(privacy)
        vm.incomingCall(friend, video: true)
        XCTAssertNil(vm.state.call)
        XCTAssertFalse(Calls.allowed(friend, friends: [friend], blocked: [friend], who: .everyone))
        vm.clearCallLog()
        XCTAssertTrue(vm.state.callLog.isEmpty)
    }

    func testActiveCallDuration() {
        let c = ActiveCall(id: 1, peerId: 2, video: false, outgoing: true, phase: .calling, startedAt: ms(100))
        XCTAssertEqual(0, c.durationMs(ms(5000)))
        var connected = c
        connected.phase = .active
        connected.connectedAt = ms(1000)
        XCTAssertEqual(4000, connected.durationMs(ms(5000)))
        XCTAssertEqual("call-1", c.room)
    }

    // MARK: - Место и отметки

    func testPublishKeepsPlaceAndOnlyFriendsTags() throws {
        let vm = makeModel()
        let friend = vm.state.following.sorted().first!
        let stranger = Demo.people.first { !vm.state.following.contains($0.id) }!.id
        let photo = Attachment(id: "p1", kind: .photo, fileName: "/tmp/x.jpg",
                               tags: [PhotoTag(userId: friend, x: 0.3, y: 0.4), PhotoTag(userId: stranger, x: 0.5, y: 0.5)])
        vm.publish("У моря", media: [photo], place: SharedPlace(lat: 40.18, lon: 44.51, title: "Ереван"))
        let p = vm.state.posts[0]
        XCTAssertEqual("Ереван", p.place?.title)
        XCTAssertEqual([friend], try XCTUnwrap(p.media.first).tags.map(\.userId))
        XCTAssertTrue(Search.posts("ереван", vm.state.posts).contains { $0.id == p.id })
    }

    func testTaggedSectionShowsPostsWithMe() throws {
        let vm = makeModel()
        let s = vm.state
        let friendPost = try XCTUnwrap(s.feed.first { $0.author.id != s.me.id })
        var tagged = friendPost
        tagged.media = [Attachment(id: "t", kind: .photo, fileName: "/tmp/t.jpg", tags: [PhotoTag(userId: s.me.id, x: 0.5, y: 0.5)])]
        var snapshot = vm.snapshot()
        snapshot.posts = snapshot.posts.map { $0.id == friendPost.id ? tagged : $0 }
        vm.restore(snapshot)
        XCTAssertEqual([friendPost.id], vm.state.taggedPosts.map(\.id))
        XCTAssertEqual(vm.state.taggedPosts, vm.taggedPosts)
        vm.untagMe(friendPost.id)
        XCTAssertTrue(vm.state.taggedPosts.isEmpty)
    }

    // MARK: - Сохранение

    func testSnapshotKeepsRound21State() throws {
        let vm = makeModel()
        vm.publishStory(nil, "Сохрани меня", bg: 2, closeFriends: true)
        let story = try XCTUnwrap(vm.state.myActiveStories(now).first)
        vm.createHighlight("Лучшее", [story.id])
        let friend = vm.state.following.sorted().first!
        vm.toggleCloseFriend(friend)
        vm.startCall(friend, video: false)
        vm.endCall()
        let vm2 = makeModel()
        vm2.restore(vm.snapshot())
        let a = vm.state
        let b = vm2.state
        XCTAssertEqual(a.stories, b.stories)
        XCTAssertEqual(a.highlights, b.highlights)
        XCTAssertEqual(a.closeFriends, b.closeFriends)
        XCTAssertEqual(a.callLog, b.callLog)
        XCTAssertEqual(true, b.stories.first?.closeFriends)

        // И через файл на телефоне
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("round21-\(UUID().uuidString)")
        let store = StateStore(directory: dir)
        store.save("anna", vm.snapshot())
        let loaded = try XCTUnwrap(store.load("anna"))
        XCTAssertEqual(a.stories, loaded.stories)
        XCTAssertEqual(a.highlights, loaded.highlights)
        XCTAssertEqual(a.callLog, loaded.callLog)
        XCTAssertEqual(a.closeFriends, loaded.closeFriends)
        try? FileManager.default.removeItem(at: dir)
    }

    func testOldSavesStillDecodeAndKeysMatchAndroid() throws {
        let e = JSONEncoder()
        e.dateEncodingStrategy = .millisecondsSince1970
        let d = JSONDecoder()
        d.dateDecodingStrategy = .millisecondsSince1970

        // Запись и вложение из старой версии — без репоста, места и отметок
        var post = Demo.posts[0]
        post.media = [Attachment(id: "a", kind: .photo, fileName: "a.jpg")]
        var obj = try XCTUnwrap(try JSONSerialization.jsonObject(with: e.encode(post)) as? [String: Any])
        obj.removeValue(forKey: "repostOf")
        obj.removeValue(forKey: "reposts")
        obj.removeValue(forKey: "place")
        var media = try XCTUnwrap(obj["media"] as? [[String: Any]])
        media[0].removeValue(forKey: "tags")
        obj["media"] = media
        let old = try d.decode(Post.self, from: JSONSerialization.data(withJSONObject: obj))
        XCTAssertNil(old.repostOf)
        XCTAssertEqual(0, old.reposts)
        XCTAssertNil(old.place)
        XCTAssertTrue(old.media[0].tags.isEmpty)
        XCTAssertEqual(post.text, old.text)

        // Новые поля переживают запись и чтение
        var full = post
        full.repostOf = PostRef(postId: 7, authorId: 2, authorName: "Вера", text: "оригинал")
        full.reposts = 3
        full.place = SharedPlace(lat: 40.18, lon: 44.51, title: "Ереван")
        full.media = [Attachment(id: "b", kind: .photo, fileName: "b.jpg", tags: [PhotoTag(userId: 2, x: 0.25, y: 0.75)])]
        XCTAssertEqual(full, try d.decode(Post.self, from: e.encode(full)))

        // Приватность: «кто может звонить» по умолчанию — все
        XCTAssertEqual(Audience.everyone, try d.decode(Privacy.self, from: e.encode(Privacy())).calls)

        // История и звонок — с ключами, как у Android
        let storyJson = """
        {"id": 7, "author": 3, "createdAt": 1800000000000, "text": "hi", "bg": 2, "close": true,
         "views": [{"user": 2, "at": 1800000000000, "liked": true}]}
        """
        let story = try d.decode(Story.self, from: Data(storyJson.utf8))
        XCTAssertEqual(3, story.authorId)
        XCTAssertTrue(story.closeFriends)
        XCTAssertEqual(1, story.likes)
        XCTAssertEqual(now, story.createdAt)
        let callJson = """
        {"id": 9, "peer": 4, "video": true, "outgoing": false, "at": 1800000000000, "durationMs": 0, "missed": true}
        """
        let call = try d.decode(CallRecord.self, from: Data(callJson.utf8))
        XCTAssertEqual(4, call.peerId)
        XCTAssertFalse(call.declined)
        XCTAssertEqual("Пропущенный видеозвонок", Calls.title(call))
        let highlight = try d.decode(Highlight.self, from: Data(#"{"id": 1, "owner": 3, "title": "Лето", "stories": [5, 6]}"#.utf8))
        XCTAssertEqual([5, 6], highlight.storyIds)
        XCTAssertEqual(3, highlight.ownerId)
        XCTAssertEqual(highlight, try d.decode(Highlight.self, from: e.encode(highlight)))
    }
}
