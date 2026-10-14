package ru.wishka.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.wishka.memory.data.NotifySound
import ru.wishka.memory.data.NudgeKind
import ru.wishka.memory.data.SoundStyle
import ru.wishka.memory.i18n.L10n
import ru.wishka.memory.i18n.Lang
import java.io.File

/** Звуки уведомлений: 57 своих в девяти стилях, бесплатные в каждом стиле, файлы на месте. */
class Round18Test {
    @Before
    fun russian() {
        L10n.choice = Lang.Ru
    }

    @Test
    fun catalog() {
        val own = NotifySound.entries.filter { it.file != null }
        assertEquals(57, own.size)
        assertEquals(own.size, own.map { it.id }.toSet().size)
        // В каждом стиле есть и бесплатные, и звуки Memory+
        SoundStyle.entries.filter { it != SoundStyle.Phone }.forEach { style ->
            val list = NotifySound.inStyle(style)
            assertTrue(style.id, list.size >= 3)
            assertTrue(style.id, list.any { it.free })
        }
        assertTrue(own.any { !it.free })
        assertTrue(NotifySound.System.free && NotifySound.Silent.free)
        // Прежние id сохранились — выбор людей не сбился
        listOf("crystal", "stardust", "comet", "moon", "prism", "nebula", "orbit", "droplet").forEach {
            assertEquals(SoundStyle.Glass, NotifySound.of(it)!!.style)
        }
        // Звуки по умолчанию разные
        val defaults = NudgeKind.entries.map { NotifySound.default(it) }
        assertEquals(defaults.size, defaults.toSet().size)
        assertEquals("Маримба", NotifySound.of("marimba")!!.title)
        L10n.choice = Lang.En
        assertEquals("Marimba", NotifySound.of("marimba")!!.title)
        assertEquals("Pops & bubbles", SoundStyle.Pop.title)
    }

    @Test
    fun filesExist() {
        val raw = listOf(File("src/main/res/raw"), File("app/src/main/res/raw"), File("mem/proj/app/src/main/res/raw")).firstOrNull { it.isDirectory } ?: return
        NotifySound.entries.mapNotNull { it.file }.forEach { name ->
            assertTrue(name, File(raw, "$name.ogg").isFile)
            assertFalse(name, File(raw, "$name.wav").exists())
        }
    }
}
