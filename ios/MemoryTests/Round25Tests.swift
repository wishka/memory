import XCTest
@testable import Memory

/// Раунд 25: подписки, «Интересное», комментарии и истории с сервера, пуши.
/// Здесь — то, что проверяется без сервера. Те же проверки, что Round25Test на Android.
final class Round25Tests: XCTestCase {
    private var clock = TestClock(Date(timeIntervalSince1970: 1_800_000_000))

    /// Часы сети — в мс, как у Android.
    private var now: Int64 { Json.ms(clock.now) }

    override func setUp() {
        super.setUp()
        L10n.shared.setLanguage(.ru)
        L10n.shared.systemLanguage = "ru"
        clock = TestClock(Date(timeIntervalSince1970: 1_800_000_000))
        Directory.clear()
        Directory.selfId = 0
    }

    override func tearDown() {
        Directory.clear()
        Directory.selfId = 0
        super.tearDown()
    }

    private func makeModel() -> AppModel {
        let clock = self.clock
        return AppModel(clock: { clock.now }, demoReplies: false)
    }

    func testPushesFromServerBecomeNudges() throws {
        let followerData: [String: String] = ["type": "new_follower", "actor": "Лия Ким", "actorId": "100002"]
        let follower = try XCTUnwrap(Nudges.fromPush(followerData))
        XCTAssertEqual(.follows, follower.kind)
        XCTAssertEqual("Лия подписалась на вас", follower.text)
        XCTAssertEqual(.person, follower.target)

        var commentData: [String: String] = ["type": "comment", "actor": "Ян", "actorId": "100003"]
        commentData["postId"] = "10000001"
        commentData["body"] = "Класс"
        let comment = try XCTUnwrap(Nudges.fromPush(commentData))
        XCTAssertEqual(.reactions, comment.kind)
        XCTAssertEqual("Ян прокомментировал: Класс", comment.text)
        XCTAssertEqual(10_000_001, comment.targetId)

        // Секретная переписка: текста в пуше нет
        let secretData: [String: String] = ["type": "message", "actor": "Ян", "chatId": "5", "body": ""]
        let secret = try XCTUnwrap(Nudges.fromPush(secretData))
        XCTAssertEqual("Новое сообщение", secret.text)
    }

    func testRealtimeFollowAndCommentFrames() throws {
        let f = RtEvent.parse(#"{"type":"follow","user":100002,"state":"request"}"#)
        XCTAssertEqual(RtEvent.follow(userId: 100_002, state: "request"), f)
        let frame = #"{"type":"comment","post":10000001,"comment":{"id":7,"author":100002,"text":"Привет"}}"#
        guard case .newComment(let postId, let json)? = RtEvent.parse(frame) else {
            return XCTFail("не комментарий")
        }
        XCTAssertEqual(10_000_001, postId)
        XCTAssertEqual("Привет", Json.string(json, "text"))
        let follow = try XCTUnwrap(f)
        XCTAssertEqual(follow, RtEvent.parse(Json.string(follow.json())))
    }

    func testServerStoriesAndCommentsDecode() throws {
        Directory.selfId = 100_001
        let codec = ApiCodec(me: Demo.me)
        let info: JSONObject = ["id": 100_002, "name": "Лия", "handle": "liya"]
        let view: JSONObject = ["user": 100_001, "at": now]
        var o: JSONObject = ["id": 3, "author": 100_002, "createdAt": now, "text": "Утро", "bg": 2]
        o["authorInfo"] = info
        o["views"] = [view]
        let story = codec.storyOf(o)
        XCTAssertEqual(100_002, story.authorId)
        XCTAssertEqual("Лия", Demo.person(100_002)?.name)
        // Свой номер на сервере в «кто смотрел» — это я
        XCTAssertEqual(1, story.views.count)
        XCTAssertEqual(Demo.me.id, story.views.first?.userId)
        let back = codec.storyOf(codec.storyJson(story))
        var expected = story
        expected.views = back.views
        XCTAssertEqual(expected, back)

        let commentInfo: JSONObject = ["id": 100_003, "name": "Ян", "handle": "yan"]
        var co: JSONObject = ["id": 9, "author": 100_003, "text": "Класс"]
        co["authorInfo"] = commentInfo
        let comment = try XCTUnwrap(codec.commentOf(co))
        XCTAssertEqual("Ян", comment.author.name)
        XCTAssertEqual(OpKind.createStory, OpKind.of("story"))
    }

    func testLikesAndCommentsReachServerFeedPosts() async throws {
        let vm = makeModel()
        await vm.loadMoreFeedNow()
        // Запись из ленты сервера (демо-сервер): раньше «нравится» её не меняло
        let older = try XCTUnwrap(vm.state.olderFeed.first)
        vm.toggleLike(older.id)
        let liked = try XCTUnwrap(vm.state.olderFeed.first { $0.id == older.id })
        XCTAssertEqual(!older.liked, liked.liked)
        XCTAssertNotNil(vm.state.findPost(older.id))
        vm.addComment(older.id, "Отлично")
        let withComment = try XCTUnwrap(vm.state.findPost(older.id))
        XCTAssertEqual(older.comments + 1, withComment.comments)
        XCTAssertEqual("Отлично", withComment.commentList.last?.text)
        let commentId = try XCTUnwrap(withComment.commentList.last).id
        vm.deleteComment(older.id, commentId)
        XCTAssertEqual(older.comments, vm.state.findPost(older.id)?.comments)
    }

    func testExploreHidesFriendsAndBlocked() throws {
        let vm = makeModel()
        let s = vm.state
        let friend = try XCTUnwrap(s.following.sorted().first)
        let following = s.following
        let stranger = try XCTUnwrap(Demo.people.first { !following.contains($0.id) })
        let base = try XCTUnwrap(Demo.posts.first)
        let friendUser = try XCTUnwrap(Demo.person(friend))
        let p1 = Post(id: 50_000_001, author: friendUser, text: base.text, minutesAgo: base.minutesAgo)
        let p2 = Post(id: 50_000_002, author: stranger, text: base.text, minutesAgo: base.minutesAgo)
        var state = s
        state.explore = [p1, p2]
        XCTAssertEqual([50_000_002], state.exploreVisible.map(\.id))
        state.blocked = [stranger.id]
        XCTAssertTrue(state.exploreVisible.isEmpty)
    }
}
