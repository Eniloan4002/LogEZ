package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.DistanceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LivePaceCalculatorTest {
    /** A fix every 10 s, 33.3 m apart: a steady 5:00/km. */
    private val times = (0..12).map { it * 10L }
    private val distances = (0..12).map { it * 33.333333 }

    @Test
    fun `pace now is the pace over the last minute`() {
        val pace = LivePaceCalculator.paceNowSecondsPerUnit(distances, times, emptyList(), 120.0, DistanceUnit.KM)
        assertEquals(300.0, pace!!, 0.5)
    }

    @Test
    fun `it reads null before a full minute has passed since the first fix`() {
        assertNull(LivePaceCalculator.paceNowSecondsPerUnit(distances, times, emptyList(), 50.0, DistanceUnit.KM))
    }

    @Test
    fun `it holds the last distance flat, so standing still falls to null instead of freezing on the last pace`() {
        // 40 s after the last fix the window still has 20 s of walking in it...
        val stillMoving = LivePaceCalculator.paceNowSecondsPerUnit(distances, times, emptyList(), 160.0, DistanceUnit.KM)
        assertEquals(60.0 / (66.666 / 1000.0), stillMoving!!, 1.0)
        // ...and a minute after it, the window covered nothing.
        assertNull(LivePaceCalculator.paceNowSecondsPerUnit(distances, times, emptyList(), 185.0, DistanceUnit.KM))
    }

    @Test
    fun `a window under 20 m reads null`() {
        val slow = (0..12).map { it * 1.0 } // 12 m in two minutes
        assertNull(LivePaceCalculator.paceNowSecondsPerUnit(slow, times, emptyList(), 120.0, DistanceUnit.KM))
    }

    @Test
    fun `paused time does not count as a slow minute`() {
        // The same steady run, paused from 60 s to 180 s: the fixes after it are 120 s later on the clock.
        val clock = times.map { if (it >= 70) it + 120 else it }
        val pace = LivePaceCalculator.paceNowSecondsPerUnit(distances, clock, listOf(60L to 180L), 120.0, DistanceUnit.KM)
        assertEquals(300.0, pace!!, 0.5)
    }

    @Test
    fun `miles use 1609 m`() {
        val pace = LivePaceCalculator.paceNowSecondsPerUnit(distances, times, emptyList(), 120.0, DistanceUnit.MILES)
        assertEquals(300.0 * 1.609344, pace!!, 1.0)
    }

    @Test
    fun `no fixes read null, and mismatched lists read null`() {
        assertNull(LivePaceCalculator.paceNowSecondsPerUnit(emptyList(), emptyList(), emptyList(), 120.0, DistanceUnit.KM))
        assertNull(LivePaceCalculator.paceNowSecondsPerUnit(distances, times.dropLast(1), emptyList(), 120.0, DistanceUnit.KM))
    }
}
