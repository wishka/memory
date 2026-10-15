package ru.wishka.memory

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.wishka.memory.data.Guide
import ru.wishka.memory.data.GuideAction
import ru.wishka.memory.data.LegalDoc
import ru.wishka.memory.data.NotifySound
import ru.wishka.memory.data.PlanTier
import ru.wishka.memory.data.Premium
import ru.wishka.memory.data.PremiumFeature
import ru.wishka.memory.data.SoundStyle
import ru.wishka.memory.data.plural
import ru.wishka.memory.i18n.EN
import ru.wishka.memory.i18n.HY
import ru.wishka.memory.i18n.L10n
import ru.wishka.memory.i18n.Lang
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.ProfileSection
import java.io.File

/** Армянский язык, Memory+ скрыт (всё бесплатно), документы на трёх языках. */
class Round19Test {
    @Before
    fun russian() {
        L10n.choice = Lang.Ru
        L10n.systemLanguage = "ru"
    }

    @After
    fun reset() {
        L10n.choice = Lang.Ru
        L10n.systemLanguage = "ru"
    }

    private val armenianLetters = Regex("[\\u0531-\\u0587]")
    private val placeholder = Regex("%(?:\\d+\\$)?[-+ 0#]*\\d*(?:\\.\\d+)?[sdf]")

    @Test
    fun armenianCoversWholeInterface() {
        val missing = EN.keys.filter { it !in HY }
        assertTrue("нет перевода: ${missing.take(5)}", missing.isEmpty())
        HY.forEach { (ru, hy) ->
            assertEquals(ru, placeholder.findAll(ru).map { it.value }.sorted().toList(), placeholder.findAll(hy).map { it.value }.sorted().toList())
            assertFalse(ru, hy.isBlank())
        }
        assertTrue(HY.values.count { armenianLetters.containsMatchIn(it) } > HY.size * 0.9)
    }

    @Test
    fun languageChoice() {
        L10n.choice = Lang.Hy
        assertTrue(L10n.armenian)
        assertFalse(L10n.english)
        assertEquals(HY["Лента"], t("Лента"))
        assertTrue(armenianLetters.containsMatchIn(tf("Прочитано %s из %s", 3, 10)))
        // После числа по-армянски — единственное число
        assertEquals("5 " + HY["запись"], plural(5, "запись", "записи", "записей"))
        assertEquals("Հայերեն", Lang.Hy.title)
        // «Как в системе»: армянский телефон — армянский
        L10n.choice = Lang.System
        L10n.systemLanguage = "hy"
        assertEquals(Lang.Hy, L10n.lang)
        L10n.systemLanguage = "ru"
        assertEquals(Lang.Ru, L10n.lang)
        L10n.systemLanguage = "de"
        assertEquals(Lang.En, L10n.lang)
    }

    @Test
    fun guideAndSoundsInArmenian() {
        L10n.choice = Lang.Hy
        Guide.chapters.forEach { c ->
            // Бренды (PRISM) не переводятся
            assertTrue(c.id, c.title.hy == c.title.en || armenianLetters.containsMatchIn(c.title.text))
            c.topics.forEach { t -> t.steps.forEach { assertTrue(c.key(t), armenianLetters.containsMatchIn(it.text)) } }
        }
        assertTrue(Guide.search("Մարիմբա").isNotEmpty() || Guide.search("ընկեր").isNotEmpty())
        NotifySound.entries.forEach { assertTrue(it.id, armenianLetters.containsMatchIn(it.title)) }
        SoundStyle.entries.forEach { assertTrue(it.id, armenianLetters.containsMatchIn(it.title)) }
    }

    @Test
    fun premiumHiddenEverythingFree() {
        assertFalse(Premium.enabled)
        val vm = AppViewModel(clock = { 1_790_000_000_000L }, demoReplies = false)
        assertEquals(Premium.OPEN_TIER, vm.state.value.plan)
        PremiumFeature.entries.forEach { assertTrue(it.name, vm.has(it)) }
        assertTrue(vm.require(PremiumFeature.Sounds))
        assertEquals(null, vm.state.value.paywall)
        // Экран тарифов не открывается
        vm.openPremium()
        assertFalse(vm.state.value.profileSection == ProfileSection.Premium)
        // Что раньше было платным — работает: плейлисты без счёта, свой фон чата
        repeat(5) { assertTrue(vm.createPlaylist("Плейлист $it") != null) }
        // В гиде нет тем о Memory+
        assertTrue(Guide.chapters.flatMap { it.topics }.none { it.action == GuideAction.Premium })
        assertTrue(PlanTier.Business.covers(PlanTier.Plus))
    }

    @Test
    fun legalDocsInThreeLanguages() {
        val dir = listOf(File("../docs/legal"), File("docs/legal"), File("mem/proj/docs/legal")).firstOrNull { it.isDirectory } ?: return
        LegalDoc.entries.forEach { doc ->
            Lang.entries.filter { it != Lang.System }.forEach { lang ->
                val f = File(dir, doc.fileFor(lang))
                assertTrue(f.path, f.isFile)
                val text = f.readText()
                assertFalse(f.path, text.contains("152-ФЗ") || text.contains("Роскомнадзор"))
            }
            assertTrue(doc.id, armenianLetters.containsMatchIn(File(dir, doc.fileFor(Lang.Hy)).readText()))
        }
    }
}
