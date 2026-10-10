import XCTest
@testable import Memory

/// Правила приложения: лайки, записи, чаты, подписки, группы, профиль.
/// Те же проверки, что AppViewModelTest на Android.
final class AppModelTests: XCTestCase {

    override func setUp() {
        super.setUp()
        // Строки в проверках — русские, независимо от языка симулятора
        L10n.shared.setLanguage(.ru)
    }

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

    func testPrismLikeSaveAndComments() throws {
        let vm = AppModel()
        let prism = try XCTUnwrap(vm.state.prisms.first { !$0.liked })
        vm.togglePrismLike(prism.id)
        XCTAssertEqual(vm.state.findPrism(prism.id)?.likes, prism.likes + 1)
        // «Сохранить» — закладка в профиле, повторно — убрать
        XCTAssertTrue(vm.state.savedPrisms.isEmpty)
        vm.togglePrismSave(prism.id)
        XCTAssertEqual(vm.state.savedPrisms.map(\.id), [prism.id])
        vm.togglePrismSave(prism.id)
        XCTAssertTrue(vm.state.savedPrisms.isEmpty)
        // Комментарий: в обсуждении и в счётчике; свой можно удалить
        vm.addPrismComment(prism.id, "  Красиво  ")
        let withComment = try XCTUnwrap(vm.state.findPrism(prism.id))
        XCTAssertEqual(withComment.comments, prism.comments + 1)
        XCTAssertEqual(withComment.commentList.last?.text, "Красиво")
        let myComment = try XCTUnwrap(withComment.commentList.last)
        vm.deletePrismComment(prism.id, myComment.id)
        XCTAssertEqual(vm.state.findPrism(prism.id)?.comments, prism.comments)
        // Открыть из «Сохранённого» — вкладка PRISM на нужном ролике
        vm.openPrism(prism.id)
        XCTAssertEqual(vm.state.tab, .prism)
        XCTAssertEqual(vm.state.prismStartId, prism.id)
    }

    func testCommentsOnFeedAndGroupPosts() throws {
        let vm = AppModel()
        let post = try XCTUnwrap(vm.state.posts.first)
        vm.addComment(post.id, "Отличный кадр")
        vm.addComment(post.id, "   ")
        let updated = try XCTUnwrap(vm.state.findPost(post.id))
        XCTAssertEqual(updated.comments, post.comments + 1)
        XCTAssertEqual(updated.commentList.count, post.commentList.count + 1)
        XCTAssertEqual(updated.commentList.last?.author.id, vm.state.me.id)
        // Чужой комментарий удалить нельзя, свой — можно
        if let foreign = post.commentList.first { vm.deleteComment(post.id, foreign.id) }
        XCTAssertEqual(vm.state.findPost(post.id)?.commentList.count, post.commentList.count + 1)
        let mine = try XCTUnwrap(updated.commentList.last)
        vm.deleteComment(post.id, mine.id)
        XCTAssertEqual(vm.state.findPost(post.id)?.commentList, post.commentList)
        // Запись в группе обсуждается так же
        let groupPost = try XCTUnwrap(vm.state.groups.first { !$0.posts.isEmpty }?.posts.first)
        vm.addComment(groupPost.id, "И я там был")
        XCTAssertEqual(vm.state.findPost(groupPost.id)?.commentList.last?.text, "И я там был")
    }

    func testPublishWithMediaOnly() throws {
        let vm = AppModel()
        let photo = Attachment(id: "a", kind: .photo, fileName: "a.jpg")
        vm.publish("", media: [photo])
        let top = try XCTUnwrap(vm.state.posts.first)
        XCTAssertEqual(top.media, [photo])
        XCTAssertEqual(top.text, "")
        // Совсем пустую запись не публикуем
        let count = vm.state.posts.count
        vm.publish("  ", media: [])
        XCTAssertEqual(vm.state.posts.count, count)
    }

    func testSendAttachmentsAndDeleteMessages() throws {
        let vm = AppModel(demoReplies: false)
        let chat = try XCTUnwrap(vm.state.chats.first)
        let doc = Attachment(id: "d", kind: .document, fileName: "d.pdf", name: "Договор.pdf", size: 2048)
        vm.send(chat.id, "", attachments: [doc])
        let sent = try XCTUnwrap(vm.state.chats.first { $0.id == chat.id }?.messages.last)
        XCTAssertEqual(sent.attachments, [doc])
        XCTAssertEqual(previewOf(sent), "Договор.pdf")
        // Удалить у себя
        vm.deleteMessage(chat.id, sent.id, forEveryone: false)
        XCTAssertFalse(vm.state.chats.first { $0.id == chat.id }?.messages.contains { $0.id == sent.id } ?? true)
        XCTAssertFalse(vm.deletedForEveryone.contains(sent.id))
        // Удалить у обоих — запоминается для отправки на сервер
        let other = try XCTUnwrap(vm.state.chats.first { $0.id == chat.id }?.messages.first)
        vm.deleteMessage(chat.id, other.id, forEveryone: true)
        XCTAssertTrue(vm.deletedForEveryone.contains(other.id))
    }

    func testSnapshotSurvivesRestartAndKeepsCurrentName() throws {
        let vm = AppModel()
        vm.setAccount(username: "vera.moon", displayName: "Вера")
        vm.publish("Мой момент", media: [Attachment(id: "p", kind: .photo, fileName: "p.jpg")])
        let firstPrism = try XCTUnwrap(vm.state.prisms.first)
        vm.togglePrismSave(firstPrism.id)

        // Запись в файл и чтение — как после перезапуска
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let store = StateStore(directory: dir)
        store.save("vera.moon", vm.snapshot())
        let loaded = try XCTUnwrap(store.load("vera.moon"))
        XCTAssertNil(store.load("someone.else"))

        let next = AppModel()
        next.setAccount(username: "vera.moon", displayName: "Вера Новая")
        next.restore(loaded)
        XCTAssertEqual(next.state.myPosts.first?.text, "Мой момент")
        XCTAssertEqual(next.state.myPosts.first?.media.first?.fileName, "p.jpg")
        XCTAssertEqual(next.state.myPosts.first?.author.name, "Вера Новая")
        XCTAssertEqual(next.state.savedPrisms.count, 1)
        // Новые номера не совпадают с сохранёнными
        next.publish("Ещё")
        XCTAssertEqual(Set(next.state.myPosts.map(\.id)).count, 2)
        try? FileManager.default.removeItem(at: dir)
    }

    func testPersonFromChatOpensAtOnceAndBackReturnsToChat() throws {
        let vm = AppModel()
        let chat = try XCTUnwrap(vm.state.chats.first)
        vm.openChat(chat.id)
        vm.openPerson(chat.peer.id)
        // Страница человека сразу на экране — переписка её не закрывает
        XCTAssertNil(vm.state.openChat)
        XCTAssertEqual(vm.state.openPerson?.id, chat.peer.id)
        XCTAssertTrue(vm.back())
        XCTAssertEqual(vm.state.openChatId, chat.id)
        XCTAssertNil(vm.state.openPersonId)
    }

    func testReplyAndForward() throws {
        let vm = AppModel(demoReplies: false)
        let chat = try XCTUnwrap(vm.state.chats.first)
        let original = try XCTUnwrap(chat.messages.first { !$0.fromMe })
        let ref = vm.replyRef(chat.id, original)
        XCTAssertEqual(ref.author, chat.peer.name)
        vm.send(chat.id, "Отвечаю", replyTo: ref)
        let reply = try XCTUnwrap(vm.state.chats.first { $0.id == chat.id }?.messages.last)
        XCTAssertEqual(reply.replyTo?.messageId, original.id)
        // Переслать в другую переписку — с пометкой автора, и она откроется
        let target = try XCTUnwrap(vm.state.chats.first { $0.id != chat.id })
        vm.forward(chat.id, original.id, to: target.id)
        let forwarded = try XCTUnwrap(vm.state.chats.first { $0.id == target.id }?.messages.last)
        XCTAssertEqual(forwarded.text, original.text)
        XCTAssertEqual(forwarded.forwardedFrom, chat.peer.name)
        XCTAssertTrue(forwarded.fromMe)
        XCTAssertEqual(vm.state.openChatId, target.id)
    }

    func testProfileSectionsAndBack() throws {
        let vm = AppModel()
        XCTAssertFalse(vm.state.followers.isEmpty)
        vm.openProfileSection(.groups)
        XCTAssertEqual(vm.state.tab, .profile)
        // Группа из раздела профиля: «назад» — снова в раздел
        let group = try XCTUnwrap(vm.state.myGroups.first)
        vm.openGroup(group.id)
        XCTAssertEqual(vm.state.tab, .profile)
        XCTAssertTrue(vm.back())
        XCTAssertEqual(vm.state.profileSection, .groups)
        XCTAssertTrue(vm.back())
        XCTAssertNil(vm.state.profileSection)
        // Другая вкладка — раздел закрывается
        vm.openProfileSection(.saved)
        vm.selectTab(.feed)
        XCTAssertNil(vm.state.profileSection)
    }

    /// Старый файл без ответов и пересылок читается: новые поля необязательные.
    func testOldMessagesStillDecode() throws {
        let json = #"{"id": 1, "fromMe": true, "text": "Привет", "time": "10:00", "attachments": []}"#
        let message = try JSONDecoder().decode(Message.self, from: Data(json.utf8))
        XCTAssertNil(message.replyTo)
        XCTAssertNil(message.forwardedFrom)
    }

    func testPinAndUnpin() throws {
        let vm = AppModel()
        let chat = try XCTUnwrap(vm.state.chats.first)
        let message = try XCTUnwrap(chat.messages.first)
        vm.togglePin(chat.id, message.id)
        XCTAssertEqual(vm.state.chats.first { $0.id == chat.id }?.pinned, message)
        // Повторно — открепить
        vm.togglePin(chat.id, message.id)
        XCTAssertNil(vm.state.chats.first { $0.id == chat.id }?.pinnedId)
        // Удалили закреплённое — закрепление снимается
        vm.togglePin(chat.id, message.id)
        vm.deleteMessage(chat.id, message.id, forEveryone: false)
        XCTAssertNil(vm.state.chats.first { $0.id == chat.id }?.pinnedId)
    }

    func testOverlaysCloseFirstOnBack() throws {
        let vm = AppModel()
        let post = try XCTUnwrap(vm.state.posts.first)
        vm.openComments(post.id)
        vm.openMedia(Attachment(id: "p", kind: .photo, fileName: "p.jpg"))
        XCTAssertTrue(vm.back())
        XCTAssertNil(vm.state.viewing)
        XCTAssertEqual(vm.state.commentsPostId, post.id)
        XCTAssertTrue(vm.back())
        XCTAssertNil(vm.state.commentsPostId)
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
        XCTAssertEqual(sizeTitle(2 * 1024 * 1024), "2,0 МБ")
        XCTAssertEqual(sizeTitle(12 * 1024), "12 КБ")
        // Время своей записи считается от момента публикации
        XCTAssertEqual(minutesSince(nil, 3), 3)
        let start = Date(timeIntervalSince1970: 60)
        XCTAssertEqual(minutesSince(start, 0, now: Date(timeIntervalSince1970: 360)), 5)
    }

    func testCommentLikes() throws {
        let vm = AppModel()
        let post = try XCTUnwrap(vm.state.posts.first { !$0.commentList.isEmpty })
        let comment = try XCTUnwrap(post.commentList.first)
        vm.toggleCommentLike(post.id, comment.id)
        let liked = try XCTUnwrap(vm.state.findPost(post.id)?.commentList.first { $0.id == comment.id })
        XCTAssertTrue(liked.liked)
        XCTAssertEqual(liked.likes, comment.likes + 1)
        vm.toggleCommentLike(post.id, comment.id)
        XCTAssertEqual(vm.state.findPost(post.id)?.commentList.first { $0.id == comment.id }?.likes, comment.likes)
        // И у PRISM
        let prism = try XCTUnwrap(vm.state.prisms.first { !$0.commentList.isEmpty })
        let pc = try XCTUnwrap(prism.commentList.first)
        vm.togglePrismCommentLike(prism.id, pc.id)
        XCTAssertEqual(vm.state.findPrism(prism.id)?.commentList.first { $0.id == pc.id }?.liked, true)
    }

    func testSettingsNavigation() {
        let vm = AppModel()
        vm.openSettings()
        XCTAssertEqual(vm.state.tab, .profile)
        XCTAssertEqual(vm.state.settingsPage, .main)
        vm.openSettings(.privacy)
        // «Назад»: со страницы — в список, из списка — в профиль
        XCTAssertTrue(vm.back())
        XCTAssertEqual(vm.state.settingsPage, .main)
        XCTAssertTrue(vm.back())
        XCTAssertNil(vm.state.settingsPage)
        // Вкладка внизу закрывает настройки
        vm.openSettings(.language)
        vm.selectTab(.profile)
        XCTAssertNil(vm.state.settingsPage)
    }

    func testPersonSections() throws {
        let vm = AppModel()
        let person = try XCTUnwrap(vm.state.posts.first { $0.author.id != vm.state.me.id }?.author)
        vm.openPerson(person.id)
        vm.openPersonSection(.followers)
        XCTAssertEqual(vm.state.personSection, .followers)
        // «Назад» сначала закрывает раздел, потом страницу
        XCTAssertTrue(vm.back())
        XCTAssertNil(vm.state.personSection)
        XCTAssertEqual(vm.state.openPersonId, person.id)
        // Подписались — вы среди его подписчиков
        if !vm.state.following.contains(person.id) { vm.toggleFollow(person.id) }
        XCTAssertTrue(vm.state.followersOf(person).contains { $0.id == vm.state.me.id })
        XCTAssertFalse(vm.state.followersOf(person).contains { $0.id == person.id })
    }

    func testBlockHidesPostsAndUnfollows() throws {
        let vm = AppModel()
        let author = try XCTUnwrap(vm.state.stories.first?.author)
        vm.block(author.id)
        XCTAssertTrue(vm.state.isBlocked(author.id))
        XCTAssertFalse(vm.state.following.contains(author.id))
        XCTAssertFalse(vm.state.feed.contains { $0.author.id == author.id })
        XCTAssertFalse(vm.state.stories.contains { $0.author.id == author.id })
        XCTAssertEqual(vm.state.blockedPeople.map(\.id), [author.id])
        vm.unblock(author.id)
        XCTAssertTrue(vm.state.feed.contains { $0.author.id == author.id })
        // Себя заблокировать нельзя
        vm.block(vm.state.me.id)
        XCTAssertTrue(vm.state.blocked.isEmpty)
    }

    func testChatListMenu() throws {
        let vm = AppModel()
        let chats = vm.state.chats
        let last = try XCTUnwrap(chats.last)
        vm.toggleChatOnTop(last.id)
        XCTAssertEqual(vm.state.chatList.first?.id, last.id)
        // Без звука — не в счётчике вкладки
        let unread = try XCTUnwrap(chats.first { $0.unread > 0 })
        let before = vm.state.unreadTotal
        vm.toggleChatMute(unread.id)
        XCTAssertEqual(vm.state.unreadTotal, before - unread.unread)
        vm.markRead(unread.id)
        XCTAssertEqual(vm.state.chats.first { $0.id == unread.id }?.unread, 0)
        vm.markUnread(unread.id)
        XCTAssertEqual(vm.state.chats.first { $0.id == unread.id }?.unread, 1)
        vm.clearHistory(last.id)
        XCTAssertEqual(vm.state.chats.first { $0.id == last.id }?.messages.isEmpty, true)
        vm.openChat(last.id)
        vm.deleteChat(last.id)
        XCTAssertFalse(vm.state.chats.contains { $0.id == last.id })
        XCTAssertNil(vm.state.openChatId)
    }

    func testGroupMuteAndSettingsSurviveSnapshot() throws {
        let vm = AppModel()
        let group = try XCTUnwrap(vm.state.groups.first)
        vm.toggleGroupMute(group.id)
        vm.setPrivacy(Privacy(messages: .following, showOnline: false))
        vm.setNotifications(Notifications(all: false))
        vm.block(try XCTUnwrap(vm.state.stories.first?.author.id))
        vm.setAvatar("a.jpg")
        // Через JSON — как в файле на телефоне
        let data = try JSONEncoder().encode(vm.snapshot())
        let snapshot = try JSONDecoder().decode(Snapshot.self, from: data)
        let other = AppModel()
        other.restore(snapshot)
        XCTAssertEqual(other.state.groups.first { $0.id == group.id }?.muted, true)
        XCTAssertEqual(other.state.privacy.messages, .following)
        XCTAssertFalse(other.state.privacy.showOnline)
        XCTAssertFalse(other.state.notifications.all)
        XCTAssertEqual(other.state.blocked.count, 1)
        XCTAssertEqual(vm.profileInfo().avatar, "a.jpg")
    }

    /// Старый файл без новых полей читается: они необязательные.
    func testOldChatAndCommentStillDecode() throws {
        let chat = #"{"id": 1, "peer": {"id": 2, "name": "Вера", "handle": "vera", "bio": "", "hue": 0.5, "online": false, "city": "", "birthday": "", "website": "", "interests": [], "joined": "", "followers": 0, "followingCount": 0}, "messages": [], "unread": 0, "typing": false}"#
        let decoded = try JSONDecoder().decode(Chat.self, from: Data(chat.utf8))
        XCTAssertFalse(decoded.onTop)
        XCTAssertFalse(decoded.muted)
        XCTAssertFalse(decoded.peer.hasAvatar)
    }

    func testCropKeepsCircleCovered() {
        // Фото 2000 × 1000, круг 500: при zoom 1 квадрат — вся высота
        let g = CropGeometry(imageSize: CGSize(width: 2000, height: 1000), circle: 500)
        let square = g.sourceSquare(zoom: 1, pan: .zero)
        XCTAssertEqual(square.width, 1000, accuracy: 0.5)
        XCTAssertEqual(square.minX, 500, accuracy: 0.5)
        XCTAssertEqual(square.minY, 0, accuracy: 0.5)
        // Сдвиг дальше края не уводит квадрат за фото
        XCTAssertEqual(g.sourceSquare(zoom: 1, pan: CGSize(width: 10_000, height: 0)).minX, 0, accuracy: 0.5)
        // Увеличение вдвое — квадрат вдвое меньше
        XCTAssertEqual(g.sourceSquare(zoom: 2, pan: .zero).width, 500, accuracy: 0.5)
    }

    func testEnglishTexts() {
        L10n.shared.setLanguage(.en)
        defer { L10n.shared.setLanguage(.ru) }
        XCTAssertEqual(agoTitle(5), "5 min")
        XCTAssertEqual(plural(1, "запись", "записи", "записей"), "1 post")
        XCTAssertEqual(plural(21, "запись", "записи", "записей"), "21 posts")
        XCTAssertEqual(sizeTitle(2 * 1024 * 1024), "2.0 MB")
    }

    func testPublishAndDeleteOwnPrism() throws {
        let vm = AppModel()
        let video = Attachment(id: "v", kind: .video, fileName: "v.mp4")
        vm.openPrismCreator()
        XCTAssertTrue(vm.state.creatingPrism)
        vm.publishPrism(video: video, cover: nil, caption: "  Закат  ", music: "", durationMs: 12_000)
        let top = try XCTUnwrap(vm.state.prisms.first)
        XCTAssertFalse(vm.state.creatingPrism)
        XCTAssertEqual(vm.state.tab, .prism)
        XCTAssertEqual(top.caption, "Закат")
        XCTAssertEqual(top.video, video)
        XCTAssertEqual(vm.state.prismStartId, top.id)
        XCTAssertTrue(top.music.contains(vm.state.me.name))
        // Чужой ролик удалить нельзя, свой — можно
        let other = try XCTUnwrap(vm.state.prisms.first { $0.author.id != vm.state.me.id })
        vm.deletePrism(other.id)
        XCTAssertTrue(vm.state.prisms.contains { $0.id == other.id })
        vm.deletePrism(top.id)
        XCTAssertFalse(vm.state.prisms.contains { $0.id == top.id })
    }

    func testMusicLibraryAndPlaylists() throws {
        let vm = AppModel()
        let a = MusicTrack(id: "a", title: "Луна", artist: "Вера", fileName: "a.mp3", durationMs: 180_000)
        let b = MusicTrack(id: "b", title: "Море", artist: "", fileName: "b.mp3", durationMs: 200_000)
        vm.addTracks([a, b])
        // Тот же файл второй раз не добавляется
        vm.addTracks([MusicTrack(id: "a2", title: "Луна", artist: "Вера", fileName: "a.mp3")])
        XCTAssertEqual(vm.state.tracks.count, 2)
        let pid = try XCTUnwrap(vm.createPlaylist("  Дорога "))
        XCTAssertNil(vm.createPlaylist("   "))
        vm.addToPlaylist(pid, "a")
        vm.addToPlaylist(pid, "b")
        vm.addToPlaylist(pid, "a")
        vm.addToPlaylist(pid, "нет такого")
        let p = try XCTUnwrap(vm.state.playlists.first { $0.id == pid })
        XCTAssertEqual(p.name, "Дорога")
        XCTAssertEqual(p.trackIds, ["a", "b"])
        vm.moveInPlaylist(pid, "b", step: -1)
        XCTAssertEqual(vm.state.playlists.first { $0.id == pid }?.trackIds, ["b", "a"])
        vm.renamePlaylist(pid, "Ночь")
        XCTAssertEqual(vm.state.playlists.first { $0.id == pid }?.name, "Ночь")
        // Удалённый трек уходит и из плейлиста
        vm.deleteTrack("b")
        XCTAssertEqual(vm.state.playlists.first { $0.id == pid }?.trackIds, ["a"])
        // Всё переживает сохранение
        let other = AppModel()
        other.restore(vm.snapshot())
        XCTAssertEqual(other.state.tracks.map(\.id), ["a"])
        XCTAssertEqual(other.state.playlists.map(\.name), ["Ночь"])
        vm.deletePlaylist(pid)
        XCTAssertTrue(vm.state.playlists.isEmpty)
    }

    func testVideoEditRules() {
        var e = VideoEdit(sourceURL: URL(fileURLWithPath: "/tmp/s.mp4"), sourceDurationMs: 90_000, sourceWidth: 1920, sourceHeight: 1080)
        XCTAssertEqual(e.trimEndMs, 60_000)
        // По умолчанию — вертикальный кадр 1080 × 1920
        XCTAssertTrue(e.outputSize() == (1080, 1920))
        e.aspect = .tall
        XCTAssertTrue(e.outputSize() == (1080, 1350))
        e.aspect = .square
        XCTAssertTrue(e.outputSize() == (1080, 1080))
        // «Оригинал» — как снято; после поворота стороны меняются
        e.aspect = .original
        XCTAssertTrue(e.outputSize() == (1920, 1080))
        e.rotation = 90
        XCTAssertTrue(e.outputSize() == (1080, 1920))
        // Скорость меняет длительность
        e.speed = 2
        XCTAssertEqual(e.outputDurationMs, 30_000)
        e.trimStartMs = 10_000
        e.coverMs = 14_000
        XCTAssertEqual(e.coverOutputMs, 2_000)
        XCTAssertTrue(e.colorIsIdentity)
        e.filter = .noir
        XCTAssertFalse(e.colorIsIdentity)
    }

    func testColorMatrices() {
        var e = VideoEdit(sourceURL: URL(fileURLWithPath: "/tmp/s.mp4"), sourceDurationMs: 10_000, sourceWidth: 1080, sourceHeight: 1920)
        // Без правок цвет не меняется
        let (r, g, b) = ColorMatrix.apply(e.colorMatrix, 0.2, 0.5, 0.8)
        XCTAssertEqual(r, 0.2, accuracy: 1e-6)
        XCTAssertEqual(g, 0.5, accuracy: 1e-6)
        XCTAssertEqual(b, 0.8, accuracy: 1e-6)
        // Ч/Б — все каналы равны
        let (nr, ng, nb) = ColorMatrix.apply(VideoFilter.noir.matrix, 0.9, 0.1, 0.3)
        XCTAssertEqual(nr, ng, accuracy: 1e-6)
        XCTAssertEqual(ng, nb, accuracy: 1e-6)
        // Яркость поднимает все каналы
        e.brightness = 0.5
        XCTAssertGreaterThan(ColorMatrix.apply(e.colorMatrix, 0.2, 0.5, 0.8).0, 0.2)
        // Тепло: красного больше, синего меньше
        e.brightness = 0
        e.warmth = 1
        let (wr, _, wb) = ColorMatrix.apply(e.colorMatrix, 0.5, 0.5, 0.5)
        XCTAssertTrue(wr > 0.5 && wb < 0.5)
    }

    func testCommentReplyAndReport() throws {
        let vm = AppModel()
        let post = try XCTUnwrap(vm.state.posts.first { !$0.commentList.isEmpty })
        let other = try XCTUnwrap(post.commentList.first { $0.author.id != vm.state.me.id })
        vm.addComment(post.id, "Согласна!", replyTo: other.author.name)
        let mine = try XCTUnwrap(vm.state.findPost(post.id)?.commentList.last)
        XCTAssertEqual(mine.replyTo, other.author.name)
        vm.reportComment(other.id)
        XCTAssertTrue(vm.state.reportedComments.contains(other.id))
        let restored = AppModel()
        restored.restore(vm.snapshot())
        XCTAssertTrue(restored.state.reportedComments.contains(other.id))
    }

    func testGroupSettingsPinAndPosts() throws {
        let vm = AppModel()
        XCTAssertNil(vm.createGroup(name: "Кофе и звёзды", description: "Ночные разговоры", topic: "Природа"))
        let id = try XCTUnwrap(vm.state.openGroupId)
        vm.openGroupSettings()
        XCTAssertTrue(vm.state.groupSettingsOpen)
        XCTAssertEqual(vm.updateGroup(id, name: "ab", description: "", topic: "Фото"), "Название — от 3 символов")
        XCTAssertNil(vm.updateGroup(id, name: "  Кофе ночью ", description: "Новое описание", topic: "Фото"))
        XCTAssertEqual(vm.state.groups.first { $0.id == id }?.name, "Кофе ночью")
        let song = Attachment(id: "s", kind: .document, fileName: "song.mp3", name: "Луна.mp3")
        XCTAssertTrue(song.isAudio)
        vm.postInGroup(id, "", media: [song])
        let post = try XCTUnwrap(vm.state.groups.first { $0.id == id }?.posts.first)
        XCTAssertEqual(post.media, [song])
        vm.togglePinGroupPost(id, post.id)
        XCTAssertEqual(vm.state.groups.first { $0.id == id }?.pinnedPostId, post.id)
        vm.togglePinGroupPost(id, post.id)
        XCTAssertNil(vm.state.groups.first { $0.id == id }?.pinnedPostId)
        vm.setGroupOnlyOwnerPosts(id, true)
        vm.postInGroup(id, "Только для меня")
        XCTAssertEqual(vm.state.groups.first { $0.id == id }?.posts.count, 2)
        vm.deleteGroupPost(id, post.id)
        XCTAssertEqual(vm.state.groups.first { $0.id == id }?.posts.count, 1)
        let foreign = try XCTUnwrap(vm.state.groups.first { !$0.mine })
        vm.updateGroup(foreign.id, name: "Чужое имя", description: "", topic: "")
        XCTAssertEqual(vm.state.groups.first { $0.id == foreign.id }?.name, foreign.name)
        _ = vm.back()
        XCTAssertFalse(vm.state.groupSettingsOpen)
    }

    func testStickersPinsAndAchievements() throws {
        let vm = AppModel()
        XCTAssertFalse(vm.state.achievements.first { $0.title == "Художник" }?.earned ?? true)
        vm.addSticker(Sticker(id: "a", fileName: "a.png", kind: .smiley))
        vm.addSticker(Sticker(id: "b", fileName: "b.png", kind: .badge))
        XCTAssertEqual(vm.state.stickers.map(\.id), ["b", "a"])
        XCTAssertTrue(vm.state.achievements.first { $0.title == "Художник" }?.earned ?? false)
        vm.deleteSticker("a")
        XCTAssertEqual(vm.state.stickers.map(\.id), ["b"])
        for i in 0..<4 { vm.publish("Момент \(i)") }
        let mine = vm.state.myPosts
        mine.forEach { vm.toggleProfilePin($0.id) }
        XCTAssertEqual(vm.state.profilePins.count, AppModel.maxPins)
        let foreign = try XCTUnwrap(vm.state.posts.first { $0.author.id != vm.state.me.id })
        vm.toggleProfilePin(foreign.id)
        XCTAssertFalse(vm.state.profilePins.contains(foreign.id))
        let last = try XCTUnwrap(mine.last)
        vm.deletePost(last.id)
        XCTAssertFalse(vm.state.profilePins.contains(last.id))
        XCTAssertEqual(vm.state.activity().count, 14)
        XCTAssertGreaterThanOrEqual(vm.state.activity().last ?? 0, 3)
        let restored = AppModel()
        restored.restore(vm.snapshot())
        XCTAssertEqual(restored.state.stickers.map(\.id), ["b"])
        XCTAssertEqual(restored.state.profilePins, vm.state.profilePins)
    }

    func testCatalogLicenses() {
        XCTAssertEqual(MusicCatalog.licenseFromURL("http://creativecommons.org/licenses/by-nc-sa/3.0/"), "CC BY-NC-SA 3.0")
        XCTAssertEqual(MusicCatalog.licenseFromURL("https://creativecommons.org/licenses/by/4.0/"), "CC BY 4.0")
        XCTAssertEqual(MusicCatalog.licenseFromURL("https://creativecommons.org/publicdomain/zero/1.0/"), "CC0")
        XCTAssertEqual(MusicCatalog.licenseLabel("cc0", "1.0"), "CC0")
        XCTAssertEqual(MusicCatalog.licenseLabel("by", "4.0"), "CC BY 4.0")
        XCTAssertEqual(MusicCatalog.licenseLabel("", ""), "")
    }
}
