package ru.wishka.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.wishka.memory.ui.splash.AtomField
import ru.wishka.memory.ui.splash.SplashMode

/** Правила заставки: распад с конца слова, сборка — с начала. */
class AtomFieldTest {

    // Атом 0 — у левого края слова (буква M), атом 1 — у правого (Y), атом 2 — посередине
    private val xs = floatArrayOf(100f, 900f, 500f)
    private val ys = floatArrayOf(300f, 300f, 300f)

    private val dissolve = AtomField(xs, ys, SplashMode.Dissolve)
    private val assemble = AtomField(xs, ys, SplashMode.Assemble)
    private val t = dissolve.timing

    @Test
    fun dissolveStartsFromLastLetter() {
        assertTrue(dissolve.pivotAt(1) < dissolve.pivotAt(2))
        assertTrue(dissolve.pivotAt(2) < dissolve.pivotAt(0))
        assertTrue(dissolve.pivotAt(1) <= t.appearMs + t.holdMs + t.jitterMs)
        assertTrue(dissolve.pivotAt(0) >= t.appearMs + t.holdMs + t.sweepMs)
    }

    @Test
    fun dissolveAtomsStayUntilReleaseThenFlyUpAndFade() {
        val before = dissolve.pivotAt(0) - 1
        assertEquals(100f, dissolve.x(0, before), 0.001f)
        assertEquals(300f, dissolve.y(0, before), 0.001f)
        val mid = dissolve.pivotAt(1) + t.lifeMs / 2
        assertTrue("атом поднимается", dissolve.y(1, mid) < 300f)
        assertTrue(dissolve.alpha(1, mid) in 0.01f..0.99f)
    }

    @Test
    fun dissolveWordAppearsThenEverythingVanishes() {
        assertEquals(0f, dissolve.alpha(0, 0), 0.001f)
        assertEquals(1f, dissolve.alpha(0, t.appearMs + 10), 0.001f)
        for (i in 0 until dissolve.size) assertFalse(dissolve.alive(i, dissolve.totalMs))
    }

    @Test
    fun assembleStartsFromFirstLetter() {
        assertTrue(assemble.pivotAt(0) < assemble.pivotAt(2))
        assertTrue(assemble.pivotAt(2) < assemble.pivotAt(1))
    }

    @Test
    fun assembleAtomsArriveAndStay() {
        // В начале атомов не видно — они ещё далеко
        for (i in 0 until assemble.size) assertEquals(0f, assemble.alpha(i, 0), 0.001f)
        // Подлетая, атом ещё не на месте и полупрозрачен
        val approach = assemble.pivotAt(0) - t.lifeMs / 2
        assertTrue(assemble.alpha(0, approach) in 0.01f..0.99f)
        assertTrue(assemble.x(0, approach) != 100f || assemble.y(0, approach) != 300f)
        // Встав на место, атом остаётся там до конца заставки
        for (time in listOf(assemble.pivotAt(0), assemble.totalMs)) {
            assertEquals(100f, assemble.x(0, time), 0.001f)
            assertEquals(300f, assemble.y(0, time), 0.001f)
            assertEquals(1f, assemble.alpha(0, time), 0.001f)
        }
        assertTrue(assemble.totalMs > assemble.wordCompleteMs)
    }

    @Test
    fun emptyFieldStillFinishes() {
        assertTrue(AtomField(FloatArray(0), FloatArray(0)).totalMs > 0)
        assertTrue(AtomField(FloatArray(0), FloatArray(0), SplashMode.Assemble).totalMs > 0)
    }
}
