package ru.wishka.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.wishka.memory.ui.splash.AtomField

/** Правила заставки: слово рассыпается с конца и исчезает целиком. */
class AtomFieldTest {

    // Атом 0 — у левого края слова (буква M), атом 1 — у правого (Y), атом 2 — посередине
    private val field = AtomField(
        xs = floatArrayOf(100f, 900f, 500f),
        ys = floatArrayOf(300f, 300f, 300f),
    )
    private val t = field.timing

    @Test
    fun lastLetterBreaksFirst() {
        assertTrue(field.releaseAt(1) < field.releaseAt(2))
        assertTrue(field.releaseAt(2) < field.releaseAt(0))
        // Правый край — сразу после паузы, левый — в конце волны
        assertTrue(field.releaseAt(1) <= t.appearMs + t.holdMs + t.jitterMs)
        assertTrue(field.releaseAt(0) >= t.appearMs + t.holdMs + t.sweepMs)
    }

    @Test
    fun atomsStayInPlaceUntilRelease() {
        val before = field.releaseAt(0) - 1
        assertEquals(100f, field.x(0, before), 0.001f)
        assertEquals(300f, field.y(0, before), 0.001f)
        assertEquals(0f, field.decay(0, before), 0f)
    }

    @Test
    fun releasedAtomsFlyUpAndFade() {
        val mid = field.releaseAt(1) + t.lifeMs / 2
        assertTrue("атом поднимается", field.y(1, mid) < 300f)
        assertTrue(field.alpha(1, mid) in 0.01f..0.99f)
    }

    @Test
    fun wordAppearsThenEverythingVanishes() {
        assertEquals(0f, field.alpha(0, 0), 0.001f)
        assertEquals(1f, field.alpha(0, t.appearMs + 10), 0.001f)
        for (i in 0 until field.size) assertFalse(field.alive(i, field.totalMs))
    }

    @Test
    fun emptyFieldStillFinishes() {
        val empty = AtomField(FloatArray(0), FloatArray(0))
        assertTrue(empty.totalMs > 0)
    }
}
