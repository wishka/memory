package ru.wishka.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.wishka.memory.data.auth.AuthResult
import ru.wishka.memory.data.auth.KeyValueStore
import ru.wishka.memory.data.auth.LocalAuth
import ru.wishka.memory.data.auth.MailSender
import ru.wishka.memory.data.auth.RecoveryCodes
import ru.wishka.memory.data.Activity
import ru.wishka.memory.data.ActivityItem
import ru.wishka.memory.data.ActivityKind
import ru.wishka.memory.data.Demo
import ru.wishka.memory.data.LinkTarget
import ru.wishka.memory.data.Links
import ru.wishka.memory.data.Search
import ru.wishka.memory.data.Tags
import ru.wishka.memory.i18n.L10n
import ru.wishka.memory.i18n.Lang
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.ProfileSection
import ru.wishka.memory.ui.Tab
import kotlin.random.Random
import ru.wishka.memory.data.AccountExport
import ru.wishka.memory.data.AppEvent
import ru.wishka.memory.data.ChatCrypto
import ru.wishka.memory.data.ChatKeyPair
import ru.wishka.memory.data.ChatKeys
import ru.wishka.memory.data.CrashReport
import ru.wishka.memory.data.DataExport
import ru.wishka.memory.data.Disappear
import ru.wishka.memory.data.Lives
import ru.wishka.memory.data.ProfileInfo
import ru.wishka.memory.data.Scrub
import ru.wishka.memory.data.Telemetry
import ru.wishka.memory.data.TelemetryPrefs
import ru.wishka.memory.data.TelemetrySink
import ru.wishka.memory.data.X25519
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import ru.wishka.memory.ui.components.label

/**
 * Раунд 20: восстановление доступа, лента активности, поиск, хештеги и
 * упоминания, правка записи, ссылки, выгрузка данных, отчёты о сбоях,
 * доступность, исчезающие и зашифрованные сообщения, эфиры.
 */
class Round20Test {
    private class MapStore : KeyValueStore {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
    }

    private var now = 1_000_000L
    private val store = MapStore()
    private val sent = mutableListOf<Pair<String, String>>()
    private val mail = MailSender { email, code -> sent += email to code }
    private fun auth() = LocalAuth(store, clock = { now }, iterations = 1_000, mail = mail)
    private fun AuthResult.error() = (this as AuthResult.Error).message

    @Before
    fun russian() {
        L10n.choice = Lang.Ru
        L10n.systemLanguage = "ru"
    }

    // ── 1. Восстановление доступа ─────────────────────────────────────

    @Test
    fun recoveryCodesAreOneTimeAndHashed() {
        val a = auth()
        assertTrue(a.register("anna", "Анна", "secret123", "secret123") is AuthResult.Ok)
        val codes = a.newRecoveryCodes("anna")
        assertEquals(LocalAuth.RECOVERY_CODES, codes.size)
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.all { Regex("^[A-Z2-9]{4}-[A-Z2-9]{4}$").matches(it) })
        // Сами коды нигде не лежат
        assertTrue(store.map.values.none { v -> codes.any { RecoveryCodes.normalize(it) in v || it in v } })
        a.logout()

        // Код подходит в любом написании — и только один раз
        val typed = codes[3].lowercase().replace("-", " ")
        assertTrue(a.recoverWithCode("anna", typed, "newpass99", "newpass99") is AuthResult.Ok)
        assertEquals(9, a.recoveryLeft("anna"))
        a.logout()
        assertEquals("Неверный логин или код", a.recoverWithCode("anna", codes[3], "other1234", "other1234").error())
        // Новый пароль работает, старый — нет
        assertTrue(a.login("anna", "newpass99") is AuthResult.Ok)
        a.logout()
        assertTrue(a.login("anna", "secret123") is AuthResult.Error)
    }

    @Test
    fun recoveryChecksPasswordRulesAndLocks() {
        val a = auth()
        a.register("boris", "Борис", "secret123", "secret123")
        val codes = a.newRecoveryCodes("boris")
        a.logout()
        assertEquals("Пароль — от 8 символов", a.recoverWithCode("boris", codes[0], "short1", "short1").error())
        assertEquals("Пароли не совпадают", a.recoverWithCode("boris", codes[0], "secret999", "secret998").error())
        // Подбор кода упирается в ту же блокировку, что и вход
        repeat(LocalAuth.MAX_FAILURES) { a.recoverWithCode("boris", "AAAA-AAAA", "secret999", "secret999") }
        assertTrue(a.recoverWithCode("boris", codes[0], "secret999", "secret999").error().startsWith("Слишком много попыток"))
        now += LocalAuth.LOCK_MS + 1
        assertTrue(a.recoverWithCode("boris", codes[0], "secret999", "secret999") is AuthResult.Ok)
        // Новые коды заменяют старые
        val fresh = a.newRecoveryCodes("boris")
        a.logout()
        assertTrue(a.recoverWithCode("boris", codes[1], "secret777", "secret777") is AuthResult.Error)
        assertTrue(a.recoverWithCode("boris", fresh[1], "secret777", "secret777") is AuthResult.Ok)
    }

    @Test
    fun emailIsConfirmedByCodeAndRestoresAccess() {
        val a = auth()
        a.register("vera", "Вера", "secret123", "secret123")
        assertEquals("Похоже, в адресе почты ошибка", a.setEmail("vera", "vera@"))
        assertNull(a.setEmail("vera", " Vera@Mail.ru "))
        assertEquals("vera@mail.ru", a.email("vera"))
        assertFalse(a.emailConfirmed("vera"))
        val code = sent.last().second
        assertEquals("vera@mail.ru", sent.last().first)
        assertEquals("Код не подходит", a.confirmEmail("vera", if (code == "000000") "111111" else "000000"))
        assertNull(a.confirmEmail("vera", code))
        assertTrue(a.emailConfirmed("vera"))
        assertEquals("v•••@mail.ru", a.maskedEmail("vera"))
        a.logout()

        // Ответ одинаковый, есть почта или нет; письмо — только при подтверждённой
        val before = sent.size
        val reply = a.startEmailRecovery("nobody")
        assertEquals(reply, a.startEmailRecovery("vera"))
        assertEquals(before + 1, sent.size)
        val reset = sent.last().second
        assertTrue(a.recoverWithEmail("vera", reset, "newpass99", "newpass99") is AuthResult.Ok)
        // Код из письма одноразовый
        a.logout()
        assertTrue(a.recoverWithEmail("vera", reset, "newpass77", "newpass77") is AuthResult.Error)
    }

    @Test
    fun emailCodeExpiresAndLimitsTries() {
        val a = auth()
        a.register("gleb", "Глеб", "secret123", "secret123")
        a.setEmail("gleb", "gleb@mail.ru")
        now += LocalAuth.CODE_TTL_MS + 1
        assertEquals("Код устарел — запросите новый", a.confirmEmail("gleb", sent.last().second))
        a.setEmail("gleb", "gleb@mail.ru")
        val good = sent.last().second
        val bad = if (good == "123456") "654321" else "123456"
        repeat(LocalAuth.CODE_TRIES - 1) { assertEquals("Код не подходит", a.confirmEmail("gleb", bad)) }
        assertEquals("Слишком много попыток — запросите новый код", a.confirmEmail("gleb", bad))
        assertNotNull(a.confirmEmail("gleb", good))
    }

    @Test
    fun deletingAccountForgetsRecovery() {
        val a = auth()
        a.register("dina", "Дина", "secret123", "secret123")
        a.newRecoveryCodes("dina")
        a.setEmail("dina", "dina@mail.ru")
        assertNull(a.deleteAccount("dina", "secret123"))
        assertTrue(store.map.keys.none { it.startsWith("acc.dina.") })
    }

    // ── 2. Лента активности ───────────────────────────────────────────

    @Test
    fun activityGroupsLikesAndCountsUnread() {
        val me = Demo.me
        val vera = Demo.people[0]
        val mark = Demo.people[1]
        val day = Activity.MERGE_MS
        val items = listOf(
            ActivityItem(1, ActivityKind.Like, vera.id, 1000, targetId = 5, preview = "Закат"),
            ActivityItem(2, ActivityKind.Like, mark.id, 2000, targetId = 5, preview = "Закат"),
            ActivityItem(3, ActivityKind.Like, vera.id, 3000, targetId = 5, preview = "Закат"),
            ActivityItem(4, ActivityKind.Comment, mark.id, 4000, targetId = 5, preview = "Красиво"),
            ActivityItem(5, ActivityKind.Like, mark.id, 1000 + 2 * day, targetId = 5),
        )
        val groups = Activity.group(items, me)
        // Свежий лайк через двое суток — отдельно; три старых — одной строкой, без повтора Веры
        assertEquals(3, groups.size)
        val merged = groups.first { it.kind == ActivityKind.Like && it.ids.size == 3 }
        assertEquals(2, merged.actors.size)
        assertTrue(Activity.text(merged).startsWith(Activity.who(merged.actors) + " оценили вашу запись"))
        val single = Activity.text(groups.first { it.kind == ActivityKind.Comment })
        assertTrue(single, single.contains("прокомментировал") && single.contains("«Красиво»"))
        assertEquals(2, Activity.unread(items, 3000))
        // Заблокированный пропадает из ленты и из счётчика
        assertEquals(0, Activity.unread(items, 3000, blocked = setOf(mark.id)))
        assertEquals(2, Activity.unread(items, 0, blocked = setOf(mark.id)))
        assertTrue(Activity.group(items, me, setOf(mark.id)).none { g -> g.actors.any { it.id == mark.id } })
        val names = listOf(vera, mark, Demo.people[2], Demo.people[3]).map { ru.wishka.memory.data.Nudges.firstName(it) }
        assertEquals("${names[0]}, ${names[1]} и ещё 2", Activity.who(listOf(vera, mark, Demo.people[2], Demo.people[3])))
    }

    @Test
    fun demoLifeFillsActivityAndOpeningMarksRead() {
        var now = 5_000_000L
        val vm = AppViewModel(clock = { now }, demoReplies = false)
        vm.publish("Закат над рекой #закат")
        repeat(300) { vm.demoEvent(Random(it)) }
        val s = vm.state.value
        assertTrue(s.activity.any { it.kind == ActivityKind.Like })
        assertTrue(s.activity.any { it.kind == ActivityKind.Comment || it.kind == ActivityKind.Mention })
        assertTrue(s.activity.any { it.kind == ActivityKind.FriendRequest })
        assertTrue(s.activityUnread > 0)
        vm.openActivity()
        now += 1000
        vm.closeActivity()
        assertEquals(0, vm.state.value.activityUnread)
        // Упоминание — с вашим логином в тексте комментария
        vm.state.value.activity.firstOrNull { it.kind == ActivityKind.Mention }?.let {
            assertTrue(it.preview.contains("@" + vm.state.value.me.handle))
        }
        // Нажатие на заявку ведёт в «Заявки»
        val req = Activity.group(vm.state.value.activity, vm.state.value.me).first { it.kind == ActivityKind.FriendRequest }
        vm.openActivityItem(req)
        assertEquals(ProfileSection.Requests, vm.state.value.profileSection)
        assertFalse(vm.state.value.activityOpen)
    }

    // ── 3. Поиск, теги, упоминания ────────────────────────────────────

    @Test
    fun tagsAndMentionsAreFound() {
        val text = "Вечер с @vera.moon и @Mark_7 #Закат #закат #yerevan_2026 #երևան email a@b.ru, цвет #1 и https://example.com/#x"
        assertEquals(listOf("закат", "yerevan_2026", "երևան"), Tags.hashtags(text))
        assertEquals(listOf("vera.moon", "mark_7"), Tags.mentions(text))
        val spans = Tags.spans(text)
        assertTrue(spans.any { it.kind == Tags.Kind.Url && it.value == "https://example.com/#x" })
        // Спаны не пересекаются и идут по порядку
        spans.zipWithNext().forEach { (a, b) -> assertTrue(a.end <= b.start) }
        // Подсказка упоминания при наборе
        assertEquals(4 to "ве", Tags.typingMention("Это @ве"))
        assertNull(Tags.typingMention("почта a@ве"))
        assertEquals("Это @vera.moon ", Tags.completeMention("Это @ве", 4, 7, "vera.moon"))
    }

    @Test
    fun searchFindsPeoplePostsTagsAndGroups() {
        val vm = AppViewModel(demoReplies = false)
        vm.publish("Первый снег в горах #зима #горы")
        vm.publish("Чай и плед #зима")
        val s = vm.state.value
        val vera = Demo.people[0]
        // Логином, с «@» и по началу имени
        assertEquals(vera.id, Search.people(vera.handle, s.searchablePeople).first().id)
        assertEquals(vera.id, Search.people("@" + vera.handle.take(3), s.searchablePeople).first().id)
        assertTrue(Search.people(vera.name.take(3).lowercase(), s.searchablePeople).any { it.id == vera.id })
        // Тег — только записи с ним
        assertEquals(2, Search.posts("#зима", s.searchablePosts).size)
        assertEquals(1, Search.posts("#горы", s.searchablePosts).size)
        assertEquals("зима" to 2, Search.tags("#зи", s.searchablePosts).first())
        assertEquals("зима", Search.popularTags(s.searchablePosts).first().first)
        // Обычные слова — все должны встретиться
        assertEquals(1, Search.posts("снег горах", s.searchablePosts).size)
        assertTrue(Search.all("ничегоподобного", s.searchablePeople, s.searchablePosts, s.groups).empty)
        // Заблокированных не найти
        vm.block(vera.id)
        assertTrue(vm.state.value.searchablePeople.none { it.id == vera.id })
        // История: без повторов, новые первыми
        val h = Search.remember(Search.remember(Search.remember(emptyList(), "зима"), "горы"), "Зима")
        assertEquals(listOf("Зима", "горы"), h)
    }

    @Test
    fun tapOnTagAndMentionNavigates() {
        val vm = AppViewModel(demoReplies = false)
        vm.openTag("#Закат")
        assertTrue(vm.state.value.searchOpen)
        assertEquals("#закат", vm.state.value.searchQuery)
        assertEquals("#закат", vm.state.value.searchHistory.first())
        val vera = Demo.people[0]
        vm.openHandle(vera.handle.uppercase())
        assertEquals(vera.id, vm.state.value.openPersonId)
        // Назад: со страницы человека — в поиск, из поиска — в ленту
        vm.back()
        assertTrue(vm.state.value.searchOpen)
        vm.back()
        assertFalse(vm.state.value.searchOpen)
        vm.openHandle("nobody_here")
        assertEquals("@nobody_here", vm.state.value.searchQuery)
        // Подсказки после «@»: друзья первыми
        if (vm.state.value.following.isNotEmpty()) assertTrue(vm.mentionCandidates("").first().id in vm.state.value.following)
    }

    // ── 4. Правка записи ──────────────────────────────────────────────

    @Test
    fun editOwnPostOnly() {
        var now = 7_000_000L
        val vm = AppViewModel(clock = { now }, demoReplies = false)
        vm.publish("Опечатка в тексте")
        val mine = vm.state.value.myPosts.first()
        now += 60_000
        vm.startEdit(mine.id)
        assertEquals(mine.id, vm.state.value.editingPostId)
        assertNull(vm.saveEdit(mine.id, "  Исправленный текст  ", mine.media))
        val edited = vm.state.value.findPost(mine.id)!!
        assertEquals("Исправленный текст", edited.text)
        assertEquals(now, edited.editedAt)
        assertNull(vm.state.value.editingPostId)
        // Без изменений — не помечается
        val other = vm.state.value.posts.first { it.author.id != vm.state.value.me.id }
        vm.startEdit(other.id)
        assertNull(vm.state.value.editingPostId)
        assertEquals("Править можно только свои записи", vm.saveEdit(other.id, "взлом", emptyList()))
        assertEquals("Запись не может быть пустой", vm.saveEdit(mine.id, "   ", emptyList()))
        // Правка переживает сохранение
        val vm2 = AppViewModel(demoReplies = false)
        vm2.restore(vm.snapshot())
        assertEquals(now, vm2.state.value.findPost(mine.id)!!.editedAt)
    }

    // ── 5. Ссылки ─────────────────────────────────────────────────────

    @Test
    fun linksRoundTrip() {
        val targets = listOf(LinkTarget.PostLink(12345), LinkTarget.Profile("vera.moon"), LinkTarget.PrismLink(7), LinkTarget.GroupLink(3), LinkTarget.TagLink("закат"))
        for (t in targets) {
            assertEquals(t, Links.parse(Links.web(t)))
            assertEquals(t, Links.parse(Links.app(t)))
        }
        assertEquals(LinkTarget.Profile("vera"), Links.parse("https://wishka.github.io/memory/l/?u=@Vera"))
        assertNull(Links.parse("https://example.com/?p=1"))
        assertNull(Links.parse("memory://u/<script>"))
        assertNull(Links.parse("memory://invite?c=ABCDEF"))
        assertTrue(Links.shareText(Demo.posts.first()).endsWith(Links.post(Demo.posts.first().id)))
    }

    @Test
    fun openLinkRoutes() {
        val vm = AppViewModel(demoReplies = false)
        val post = vm.state.value.feed.first()
        assertTrue(vm.openLink(LinkTarget.PostLink(post.id)))
        assertEquals(post.id, vm.state.value.openPostId)
        vm.back()
        assertNull(vm.state.value.openPostId)
        assertFalse(vm.openLink(LinkTarget.PostLink(999_999_999)))
        assertTrue(vm.openLink(LinkTarget.Profile(Demo.people[1].handle)))
        assertEquals(Demo.people[1].id, vm.state.value.openPersonId)
        assertFalse(vm.openLink(LinkTarget.Profile("nobody_here")))
        assertTrue(vm.openLink(LinkTarget.TagLink("закат")))
        assertTrue(vm.state.value.searchOpen)
        val prism = vm.state.value.prisms.first()
        assertTrue(vm.openLink(LinkTarget.PrismLink(prism.id)))
        assertEquals(Tab.Prism, vm.state.value.tab)
    }

    // ── 6. Мои данные ─────────────────────────────────────────────────

    @Test
    fun dataExportZipHasEverything() {
        val vm = AppViewModel(demoReplies = false)
        val dir = kotlin.io.path.createTempDirectory("memory-export").toFile()
        val photo = File(dir, "photo.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val voice = File(dir, "photo.m4a").apply { writeBytes(byteArrayOf(4, 5)) }
        vm.publish("С фото", listOf(ru.wishka.memory.data.Attachment("a", ru.wishka.memory.data.MediaKind.Photo, photo.path)))
        val chat = vm.state.value.chats.first()
        vm.send(chat.id, "", listOf(ru.wishka.memory.data.Attachment("v", ru.wishka.memory.data.MediaKind.Document, voice.path, voice = true)))
        val snap = vm.snapshot()
        val files = DataExport.files(snap, avatar = "")
        assertTrue(photo.path in files && voice.path in files)
        assertEquals(5L, DataExport.mediaBytes(files))
        val account = AccountExport("anna", "Анна", ProfileInfo(bio = "Привет"), "anna@mail.ru", true, "2026-10-14", 1000, 9)
        val out = ByteArrayOutputStream()
        val count = DataExport.write(out, account, "{\"posts\":[]}", files + "/нет/такого.jpg", withMedia = true, now = 2_000_000)
        assertEquals(2, count)
        val names = mutableListOf<String>()
        ZipInputStream(out.toByteArray().inputStream()).use { z -> generateSequence { z.nextEntry }.forEach { names += it.name } }
        assertEquals(listOf("README.txt", "account.json", "memory.json", "media/photo.jpg", "media/photo.m4a"), names)
        // Без медиа — только описание и данные
        val light = ByteArrayOutputStream()
        assertEquals(0, DataExport.write(light, account, "{}", files, withMedia = false, now = 2_000_000))
        val readme = DataExport.readme(account, 2_000_000, 0, false)
        assertTrue(readme.contains("@anna") && readme.contains("не вошли"))
        assertTrue(DataExport.fileName("anna", 2_000_000).matches(Regex("memory-anna-\\d{4}-\\d{2}-\\d{2}\\.zip")))
        dir.deleteRecursively()
    }

    // ── 7. Сбои и статистика ──────────────────────────────────────────

    @Test
    fun telemetryOnlyWithConsent() {
        val sent = mutableListOf<String>()
        val sink = object : TelemetrySink {
            override fun crash(report: CrashReport) { sent += "crash:" + report.type }
            override fun usage(day: String, counts: Map<String, Int>) { sent += "usage:$day:$counts" }
            override fun breadcrumb(event: String) { sent += "crumb:$event" }
        }
        Telemetry.sink = sink
        val prefs = TelemetryPrefs(store)
        prefs.apply()
        assertFalse(prefs.asked)
        // Без согласия ничего не считается и не уходит
        Telemetry.track(AppEvent.PostPublished)
        assertFalse(Telemetry.crash(CrashReport.of(IllegalStateException("x"), 1)))
        assertTrue(sent.isEmpty())
        prefs.set(crashes = true, analytics = true)
        assertTrue(prefs.asked)
        Telemetry.track(AppEvent.PostPublished)
        Telemetry.track(AppEvent.PostPublished)
        Telemetry.track(AppEvent.SearchUsed)
        assertTrue(Telemetry.crash(CrashReport.of(IllegalStateException("x"), 1)))
        // Статистика копится за день и уходит на следующий
        assertNull(prefs.roll("2026-10-14"))
        Telemetry.track(AppEvent.AppOpen)
        val report = prefs.roll("2026-10-15")
        assertEquals("2026-10-14", report!!.first)
        assertEquals(mapOf("post_published" to 2, "search_used" to 1), report.second)
        // Отказ — счётчики стираются
        prefs.set(crashes = false, analytics = false)
        Telemetry.track(AppEvent.AppOpen)
        assertNull(prefs.roll("2026-10-16"))
        assertFalse(Telemetry.crash(CrashReport.of(IllegalStateException("y"), 2)))
        Telemetry.sink = null
    }

    @Test
    fun crashReportsAreScrubbed() {
        val s = Scrub.text("Нет записи для anna@mail.ru и @vera.moon, тел +374 91 123456, файл state-anna.json, https://x.am/p?token=abc")
        assertFalse(s.contains("anna@mail.ru") || s.contains("vera.moon") || s.contains("123456") || s.contains("state-anna") || s.contains("token=abc"))
        val r = CrashReport.of(IllegalArgumentException("ключ anna@mail.ru"), 5)
        assertEquals(r, CrashReport.parse(5, r.text()))
        assertTrue(r.title.startsWith("IllegalArgumentException: ключ <email>"))
    }

    // ── 8. Доступность ────────────────────────────────────────────────

    @Test
    fun iconOnlyButtonsHaveLabels() {
        val root = File("src/main/java/ru/wishka/memory/ui").takeIf { it.exists() } ?: File("app/src/main/java/ru/wishka/memory/ui")
        val bad = mutableListOf<String>()
        val pattern = Regex("""\.clickable[^\n]*\n(?:[^\n]*\n){0,3}?\s*contentAlignment = Alignment\.Center,\n\s*\) \{ Glyph\(([^\n]*)\)""")
        root.walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            pattern.findAll(f.readText()).forEach { m -> if ("description" !in m.groupValues[1]) bad += f.name + ": " + m.groupValues[1] }
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
        // У каждого значка есть название для диктора на всех трёх языках
        ru.wishka.memory.ui.components.LineIcon.entries.forEach { icon ->
            L10n.choice = Lang.Ru
            val ru = icon.label
            assertTrue(icon.name, ru.isNotBlank())
            L10n.choice = Lang.En
            assertTrue("en: $ru", icon.label != ru || ru == "PRISM")
            L10n.choice = Lang.Hy
            assertTrue("hy: $ru", icon.label != ru || ru == "PRISM")
        }
        L10n.choice = Lang.Ru
    }

    // ── 9. Шифрование и исчезающие сообщения ──────────────────────────

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

    @Test
    fun x25519MatchesRfc7748() {
        val alice = hex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a")
        val bob = hex("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb")
        assertEquals("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a", X25519.publicKey(alice).hex())
        assertEquals("de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f", X25519.publicKey(bob).hex())
        val shared = "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742"
        assertEquals(shared, X25519.sharedSecret(alice, X25519.publicKey(bob)).hex())
        assertEquals(shared, X25519.sharedSecret(bob, X25519.publicKey(alice)).hex())
        // RFC 7748, 5.2: одно умножение
        assertEquals(
            "c3da55379de9c6908e94ea4df28d084f32eccf03491c71f754b4075577a28552",
            X25519.scalarMult(
                hex("a546e36bf0527c9d3b16154b82465edd62144c0ac1fc5a18506a2244ba449ac4"),
                hex("e6db6867583030db3594c1a424b15f7c726624ec26b3353b10a903a6d0ab1c4c"),
            ).hex(),
        )
    }

    /** Тот же вектор проверяет iPhone (CryptoKit) — конверты понятны обеим платформам. */
    @Test
    fun envelopeInteropVector() {
        val a = ChatKeyPair.of(hex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a"))
        val b = ChatKeyPair.of(hex("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb"))
        val key = ChatCrypto.sessionKey(a, b.publicKey)
        assertEquals("4c5cb8e873937bc60ae7614087877146a9c5193ad72322af4d802a9d1649f5e2", key.hex())
        assertEquals("23383 46330 71713 51949 49409 91008 18830 17904 66910 68117 93253 68369", ChatCrypto.safetyNumber(a.publicKey, b.publicKey))
        assertEquals("Привет, Վերա!", ChatCrypto.open(key, "AAECAwQFBgcICQoLAAgW1Sh0HWCpbT0kb/62CgbIjAH8LIjfdV96rgitGfkmPMPGT5iw", "memory-chat:anna|vera"))
        assertEquals("d70cbf2d1757aeb59b25ade516e884df48ab3215336f7aaa8b61aee1b9afa031", ChatCrypto.demoPeer(1).publicKey.hex())
    }

    @Test
    fun hkdfMatchesRfc5869() {
        // RFC 5869, тест 1
        val okm = ChatCrypto.hkdf(hex("0b".repeat(22)), hex("000102030405060708090a0b0c"), hex("f0f1f2f3f4f5f6f7f8f9"), 42)
        assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865", okm.hex())
    }

    @Test
    fun sealedMessagesOpenOnlyForTheTwo() {
        val a = ChatKeyPair.generate()
        val b = ChatKeyPair.generate()
        val eve = ChatKeyPair.generate()
        val kA = ChatCrypto.sessionKey(a, b.publicKey)
        assertEquals(kA.hex(), ChatCrypto.sessionKey(b, a.publicKey).hex())
        val env = ChatCrypto.seal(kA, "Привет, это секрет", "chat:1")
        assertFalse(String(java.util.Base64.getDecoder().decode(env), Charsets.UTF_8).contains("секрет"))
        assertEquals("Привет, это секрет", ChatCrypto.open(ChatCrypto.sessionKey(b, a.publicKey), env, "chat:1"))
        assertNull(ChatCrypto.open(ChatCrypto.sessionKey(eve, a.publicKey), env, "chat:1"))
        assertNull(ChatCrypto.open(kA, env, "chat:2"))
        // Подделанный конверт не откроется
        val raw = java.util.Base64.getDecoder().decode(env)
        raw[20] = (raw[20].toInt() xor 1).toByte()
        assertNull(ChatCrypto.open(kA, java.util.Base64.getEncoder().encodeToString(raw), "chat:1"))
        // Код безопасности одинаков у обоих и выглядит как 12 групп по 5 цифр
        val n = ChatCrypto.safetyNumber(a.publicKey, b.publicKey)
        assertEquals(n, ChatCrypto.safetyNumber(b.publicKey, a.publicKey))
        assertTrue(n.matches(Regex("(\\d{5} ){11}\\d{5}")))
        assertFalse(n == ChatCrypto.safetyNumber(a.publicKey, eve.publicKey))
        // Свой ключ переживает перезапуск и хранится в зашифрованной части
        val keys = ChatKeys(store)
        val k1 = keys.identity("Anna")
        assertEquals(k1.publicKey.hex(), keys.identity("anna").publicKey.hex())
        assertTrue(store.map.keys.any { it.startsWith("session.e2e.") })
    }

    @Test
    fun secretChatSendsOnlyEnvelopes() {
        val vm = AppViewModel(demoReplies = false)
        val chat = vm.state.value.chats.first()
        vm.setSecret(chat.id, true)
        vm.send(chat.id, "Пароль от вайфая 12345678")
        val env = vm.lastEnvelope!!
        assertFalse(String(java.util.Base64.getDecoder().decode(env), Charsets.ISO_8859_1).contains("вайфая"))
        val last = vm.state.value.chats.first { it.id == chat.id }.messages.last()
        assertTrue(last.secret)
        assertEquals("Пароль от вайфая 12345678", last.text)
        val code = vm.safetyNumber(chat.id)!!
        assertEquals(code, vm.safetyNumber(chat.id))
        vm.markVerified(chat.id)
        assertTrue(vm.state.value.chats.first { it.id == chat.id }.verified)
        // Выключили — отметка о сверке снимается
        vm.setSecret(chat.id, false)
        assertFalse(vm.state.value.chats.first { it.id == chat.id }.verified)
    }

    @Test
    fun disappearingMessagesExpire() {
        var now = 10_000_000L
        val vm = AppViewModel(clock = { now }, demoReplies = false)
        val chat = vm.state.value.chats.first()
        val before = vm.state.value.chats.first { it.id == chat.id }.messages.size
        vm.send(chat.id, "Останется")
        vm.setDisappearing(chat.id, Disappear.FiveMinutes)
        vm.send(chat.id, "Исчезнет")
        val msgs = vm.state.value.chats.first { it.id == chat.id }.messages
        assertEquals(0L, msgs[msgs.size - 2].expiresAt)
        assertEquals(now + Disappear.FiveMinutes.ms, msgs.last().expiresAt)
        now += Disappear.FiveMinutes.ms - 1
        assertTrue(vm.sweepExpired().isEmpty())
        assertEquals(before + 2, vm.state.value.chats.first { it.id == chat.id }.messages.size)
        now += 2
        vm.sweepExpired()
        val after = vm.state.value.chats.first { it.id == chat.id }.messages
        assertEquals(before + 1, after.size)
        assertEquals("Останется", after.last().text)
        // Настройки переписки переживают сохранение
        val vm2 = AppViewModel(demoReplies = false)
        vm2.restore(vm.snapshot())
        assertEquals(Disappear.FiveMinutes.ms, vm2.state.value.chats.first { it.id == chat.id }.disappearMs)
    }

    // ── 10. Эфиры ─────────────────────────────────────────────────────

    @Test
    fun liveLifecycle() {
        var now = 20_000_000L
        val vm = AppViewModel(clock = { now }, demoReplies = false)
        assertEquals("Назовите эфир — так друзья поймут, о чём он", vm.startLive("  ", false))
        assertNull(vm.startLive("Закат с крыши", friendsOnly = true))
        val live = vm.state.value.myLive!!
        assertEquals(live.id, vm.state.value.watchingLiveId)
        assertEquals("Эфир уже идёт", vm.startLive("Ещё один", false))
        repeat(30) { now += 2_000; vm.liveTick(Random(it)) }
        val going = vm.state.value.myLive!!
        assertTrue(going.peakViewers >= going.viewers)
        assertTrue(going.hearts > 0 && going.comments.isNotEmpty())
        vm.sendLiveComment("Всем привет")
        assertEquals("Всем привет", vm.state.value.myLive!!.comments.last().text)
        // «Назад» у ведущего — вопрос, а не выход
        vm.back()
        assertTrue(vm.state.value.confirmEndLive)
        assertNotNull(vm.state.value.myLive)
        vm.endLive()
        assertNull(vm.state.value.myLive)
        assertNull(vm.state.value.watchingLiveId)
        val summary = vm.state.value.liveSummary!!
        assertEquals(60_000L, summary.durationMs(now))
        assertTrue(Lives.summary(summary, now).startsWith("Эфир шёл 1:00"))
    }

    @Test
    fun friendLiveCanBeWatched() {
        val vm = AppViewModel(demoReplies = false)
        val friend = vm.state.value.friends.first()
        vm.friendGoesLive(friend, Random(1))
        val live = vm.state.value.visibleLives.single()
        assertEquals(friend.id, live.hostId)
        assertTrue(vm.state.value.activity.any { it.kind == ActivityKind.Live && it.targetId == live.id })
        vm.watchLive(live.id)
        assertEquals(live.viewers + 1, vm.state.value.watchingLive!!.viewers)
        vm.sendHeart()
        assertEquals(live.hearts + 1, vm.state.value.watchingLive!!.hearts)
        vm.back()
        assertNull(vm.state.value.watchingLiveId)
        // Заблокировали — эфир не виден
        vm.block(friend.id)
        assertTrue(vm.state.value.visibleLives.isEmpty())
        assertEquals("1:02:05", Lives.clock(3_725_000))
    }
}
