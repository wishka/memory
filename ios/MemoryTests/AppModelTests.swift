import XCTest
@testable import Memory

/// Правила приложения: лайки, записи, чаты, подписки, группы, профиль.
/// Те же проверки, что AppViewModelTest на Android.
final class AppModelTests: XCTestCase {

    func testLikeTogglesCount() throws {
        let vm = AppModel()
        let post = try XCTUnwrap(vm.state.posts.first { !$0.liked })
        vm.toggleLike(post.id)
        let liked = try XCTUnwrap(vm.state.posts.first { $0.id == post.id })
        XCTAssertTrue(liked.liked)
        XCTAssertEqual(liked.likes, post.likes + 1)
        vm.toggleLike(post.id)
        XCTAssertEqual(vm.state.posts.first { $0.id == post.id }?.likes, post.likes)
    }

    func testPublishPutsPostOnTopAndInProfile() throws {
        let vm = AppModel()
        vm.publish("  Первый момент  ")
        let top = try XCTUnwrap(vm.state.posts.first)
        XCTAssertEqual(top.text, "Первый момент")
        XCTAssertEqual(top.author.id, vm.state.me.id)
        XCTAssertEqual(vm.state.myPosts, [top])
        vm.publish("   ")
        XCTAssertEqual(vm.state.myPosts.count, 1)
    }

    func testOpeningChatClearsUnreadAndBackCloses() throws {
        let vm = AppModel()
        let chat = try XCTUnwrap(vm.state.chats.first { $0.unread > 0 })
        vm.openChat(chat.id)
        XCTAssertEqual(vm.state.tab, .chats)
        XCTAssertEqual(vm.state.chats.first { $0.id == chat.id }?.unread, 0)
        XCTAssertTrue(vm.back())
        XCTAssertNil(vm.state.openChatId)
        XCTAssertTrue(vm.back())                 // с «Чатов» — на ленту
        XCTAssertEqual(vm.state.tab, .feed)
        XCTAssertFalse(vm.back())                // с ленты — некуда
    }

    func testRenameShowsOnOwnPosts() {
        let vm = AppModel()
        vm.publish("Запись")
        vm.updateProfile(name: "Новое имя", info: ProfileInfo(bio: "о себе", city: "Казань", interests: ["Фото"]))
        XCTAssertEqual(vm.state.me.name, "Новое имя")
        XCTAssertEqual(vm.state.myPosts.first?.author.name, "Новое имя")
        XCTAssertEqual(vm.state.me.city, "Казань")
        XCTAssertEqual(vm.state.me.interests, ["Фото"])
        vm.updateProfile(name: "  ", info: ProfileInfo())
        XCTAssertEqual(vm.state.me.name, "Новое имя")
    }

    func testStoriesShowOnlyFollowed() {
        let vm = AppModel()
        let s = vm.state
        XCTAssertFalse(s.stories.isEmpty)
        XCTAssertTrue(s.stories.allSatisfy { s.following.contains($0.author.id) })
        // Подписка на Марка (3) добавляет его записи, отписка — убирает
        XCTAssertFalse(s.stories.contains { $0.author.id == 3 })
        vm.toggleFollow(3)
        XCTAssertTrue(vm.state.stories.contains { $0.author.id == 3 })
        vm.toggleFollow(3)
        XCTAssertFalse(vm.state.stories.contains { $0.author.id == 3 })
        // На себя подписаться нельзя
        vm.toggleFollow(vm.state.me.id)
        XCTAssertFalse(vm.state.following.contains(vm.state.me.id))
    }

    func testAccountNameAndReset() {
        let vm = AppModel()
        vm.setAccount(username: "vera.moon", displayName: "Вера")
        vm.publish("Привет")
        XCTAssertEqual(vm.state.myPosts.first?.author.name, "Вера")
        XCTAssertEqual(vm.state.me.handle, "vera.moon")
        vm.reset()
        XCTAssertTrue(vm.state.myPosts.isEmpty)
    }

    func testPersonPageAndBackOrder() {
        let vm = AppModel()
        vm.selectTab(.stories)
        vm.openPerson(2)
        XCTAssertEqual(vm.state.openPerson?.name, "Вера Лунная")
        // «Написать» открывает переписку поверх страницы, «назад» возвращает на неё
        vm.writeTo(2)
        XCTAssertEqual(vm.state.openChat?.peer.id, 2)
        XCTAssertTrue(vm.back())
        XCTAssertEqual(vm.state.openPersonId, 2)
        XCTAssertTrue(vm.back())
        XCTAssertNil(vm.state.openPersonId)
        // Своё имя ведёт в профиль
        vm.openPerson(vm.state.me.id)
        XCTAssertEqual(vm.state.tab, .profile)
        XCTAssertNil(vm.state.openPersonId)
    }

    func testWriteToNewPersonCreatesChat() {
        let vm = AppModel()
        let before = vm.state.chats.count
        vm.writeTo(5) // с Тимуром переписки ещё нет
        XCTAssertEqual(vm.state.chats.count, before + 1)
        XCTAssertEqual(vm.state.openChat?.peer.id, 5)
        vm.writeTo(5) // повторно — та же переписка
        XCTAssertEqual(vm.state.chats.count, before + 1)
    }

    func testGroupsJoinCreatePostDelete() throws {
        let vm = AppModel()
        let other = try XCTUnwrap(vm.state.groups.first { !$0.joined })
        vm.toggleGroup(other.id)
        XCTAssertEqual(vm.state.groups.first { $0.id == other.id }?.joined, true)
        XCTAssertEqual(vm.state.groups.first { $0.id == other.id }?.members, other.members + 1)

        XCTAssertEqual(vm.createGroup(name: "ab", description: "", topic: "Фото"), "Название — от 3 символов")
        XCTAssertNil(vm.createGroup(name: "Кофе и звёзды", description: "Ночные разговоры", topic: "Природа"))
        let mine = try XCTUnwrap(vm.state.openGroup)
        XCTAssertTrue(mine.mine && mine.joined)
        XCTAssertTrue(vm.createGroup(name: "кофе и звёзды", description: "", topic: "Фото")?.contains("уже есть") == true)

        vm.postInGroup(mine.id, "Первая запись")
        XCTAssertEqual(vm.state.openGroup?.posts.first?.text, "Первая запись")
        // Из своей группы не выйти — только удалить
        vm.toggleGroup(mine.id)
        XCTAssertEqual(vm.state.openGroup?.joined, true)
        vm.deleteGroup(mine.id)
        XCTAssertFalse(vm.state.groups.contains { $0.id == mine.id })
    }

    func testCannotPostInGroupWithoutJoining() throws {
        let vm = AppModel()
        let other = try XCTUnwrap(vm.state.groups.first { !$0.joined })
        vm.postInGroup(other.id, "привет")
        XCTAssertEqual(vm.state.groups.first { $0.id == other.id }?.posts, other.posts)
    }

    func testReelLike() throws {
        let vm = AppModel()
        let reel = try XCTUnwrap(vm.state.reels.first { !$0.liked })
        vm.toggleReelLike(reel.id)
        XCTAssertEqual(vm.state.reels.first { $0.id == reel.id }?.likes, reel.likes + 1)
    }

    func testDemoReplyArrives() {
        let vm = AppModel()
        vm.replyDelays = (0.01, 0.01)
        let chat = vm.state.chats[1]
        vm.openChat(chat.id)
        vm.send(chat.id, "Привет")
        let replied = expectation(description: "ответ собеседника")
        Task { @MainActor in
            for _ in 0..<100 {
                if vm.state.chats.first(where: { $0.id == chat.id })?.messages.last?.fromMe == false { break }
                try? await Task.sleep(nanoseconds: 20_000_000)
            }
            replied.fulfill()
        }
        wait(for: [replied], timeout: 5)
        let messages = vm.state.chats.first { $0.id == chat.id }?.messages ?? []
        XCTAssertEqual(messages.suffix(2).map(\.fromMe), [true, false])
        // Переписка открыта — ответ сразу прочитан
        XCTAssertEqual(vm.state.chats.first { $0.id == chat.id }?.unread, 0)
    }

    func testTexts() {
        XCTAssertEqual(agoTitle(5), "5 мин")
        XCTAssertEqual(agoTitle(125), "2 ч")
        XCTAssertEqual(agoTitle(60 * 30), "вчера")
        XCTAssertEqual(plural(21, "запись", "записи", "записей"), "21 запись")
        XCTAssertEqual(plural(12, "запись", "записи", "записей"), "12 записей")
        XCTAssertEqual(compact(1284), "1,3 тыс.")
        XCTAssertEqual(compact(15_000), "15 тыс.")
    }
}
