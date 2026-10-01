package com.enil.logez.feature.workout.session

import com.enil.logez.core.domain.model.ActiveInlineTimerSnapshot
import com.enil.logez.core.domain.model.TimerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The set timer's rounding, in both modes: elapsed rounds down, time left rounds up, so a countdown never reads 0:00 early. */
class InlineTimerEngineTest {
    private val stopwatch = TimerMode.STOPWATCH
    private val countdown = TimerMode.COUNTDOWN

    @Test
    fun `a stopwatch reads whole seconds elapsed, rounded down`() {
        assertEquals(0, InlineTimerEngine.displaySeconds(stopwatch, 1_000L, null, 1_999L))
        assertEquals(1, InlineTimerEngine.displaySeconds(stopwatch, 1_000L, null, 2_000L))
        assertEquals(38, InlineTimerEngine.displaySeconds(stopwatch, 10_000L, null, 48_900L))
    }

    @Test
    fun `a countdown reads time left rounded up and reaches 0 only at the deadline`() {
        assertEquals(60, InlineTimerEngine.displaySeconds(countdown, 0L, 60, 0L))
        assertEquals(60, InlineTimerEngine.displaySeconds(countdown, 0L, 60, 1L))
        assertEquals(59, InlineTimerEngine.displaySeconds(countdown, 0L, 60, 1_000L))
        assertEquals(42, InlineTimerEngine.displaySeconds(countdown, 0L, 60, 18_500L))
        assertEquals(41, InlineTimerEngine.displaySeconds(countdown, 0L, 60, 19_000L))
        assertEquals(1, InlineTimerEngine.displaySeconds(countdown, 0L, 60, 59_999L))
        assertEquals(0, InlineTimerEngine.displaySeconds(countdown, 0L, 60, 60_000L))
        assertEquals(0, InlineTimerEngine.displaySeconds(countdown, 0L, 60, 75_000L))
    }

    @Test
    fun `only a countdown with a positive target has a deadline`() {
        assertEquals(63_000L, InlineTimerEngine.deadline(countdown, 3_000L, 60))
        assertNull(InlineTimerEngine.deadline(stopwatch, 3_000L, 60))
        assertNull(InlineTimerEngine.deadline(countdown, 3_000L, null))
        assertNull(InlineTimerEngine.deadline(countdown, 3_000L, 0))
    }

    @Test
    fun `a countdown stopped early logs the seconds held and never more than its target`() {
        assertEquals(19, InlineTimerEngine.loggedSeconds(countdown, 0L, 60, 19_900L))
        assertEquals(60, InlineTimerEngine.loggedSeconds(countdown, 0L, 60, 60_000L))
        assertEquals(60, InlineTimerEngine.loggedSeconds(countdown, 0L, 60, 300_000L))
        assertEquals(125, InlineTimerEngine.loggedSeconds(stopwatch, 0L, null, 125_400L))
    }

    @Test
    fun `isFinished is true from the deadline on, and never for a stopwatch`() {
        assertFalse(InlineTimerEngine.isFinished(countdown, 0L, 60, 59_999L))
        assertTrue(InlineTimerEngine.isFinished(countdown, 0L, 60, 60_000L))
        assertFalse(InlineTimerEngine.isFinished(stopwatch, 0L, null, 9_999_999L))
    }

    @Test
    fun `the next display change lands on the boundary, not a second after the last wake`() {
        // Stopwatch 0.25 s into a second: 0.75 s to the next digit.
        assertEquals(750L, InlineTimerEngine.millisUntilNextDisplayChange(stopwatch, 0L, null, 10_250L))
        // Countdown with 41.5 s left shows 42 until 41.0 s left: 500 ms.
        assertEquals(500L, InlineTimerEngine.millisUntilNextDisplayChange(countdown, 0L, 60, 18_500L))
        // Exactly on a whole second left (41.000 s), the digit holds for a full second.
        assertEquals(1_000L, InlineTimerEngine.millisUntilNextDisplayChange(countdown, 0L, 60, 19_000L))
        // Past the deadline it never asks for less than 1 ms.
        assertTrue(InlineTimerEngine.millisUntilNextDisplayChange(countdown, 0L, 60, 70_000L) >= 1L)
    }

    @Test
    fun `a saved timer is restorable while the wall clock and elapsedRealtime agree about its age, and dropped after a reboot`() {
        val saved = ActiveInlineTimerSnapshot("we1", "s1", "COUNTDOWN", startWallMillis = 1_000_000L, startElapsedRealtimeMillis = 50_000L, targetSeconds = 60)
        // 30 s later on both clocks: a process death.
        assertTrue(InlineTimerEngine.isRestorable(saved, nowWallMillis = 1_030_000L, nowElapsedRealtimeMillis = 80_000L))
        // elapsedRealtime restarted from a small number after a reboot: it is now behind the saved start.
        assertFalse(InlineTimerEngine.isRestorable(saved, nowWallMillis = 1_300_000L, nowElapsedRealtimeMillis = 20_000L))
        // The wall clock jumped an hour forward while elapsedRealtime moved 30 s.
        assertFalse(InlineTimerEngine.isRestorable(saved, nowWallMillis = 4_630_000L, nowElapsedRealtimeMillis = 80_000L))
        // The wall clock went backwards.
        assertFalse(InlineTimerEngine.isRestorable(saved, nowWallMillis = 900_000L, nowElapsedRealtimeMillis = 80_000L))
    }

    @Test
    fun `a stopwatch older than four hours is not restored, a countdown of any age is`() {
        val stopwatch = ActiveInlineTimerSnapshot("we1", "s1", null, startWallMillis = 1_000_000L, startElapsedRealtimeMillis = 50_000L, targetSeconds = null)
        val countdown = ActiveInlineTimerSnapshot("we1", "s1", "COUNTDOWN", startWallMillis = 1_000_000L, startElapsedRealtimeMillis = 50_000L, targetSeconds = 60)
        // 3 h 59 min and 4 h 1 min later, both clocks in step.
        assertTrue(InlineTimerEngine.isRestorable(stopwatch, 1_000_000L + 14_340_000L, 50_000L + 14_340_000L))
        assertFalse(InlineTimerEngine.isRestorable(stopwatch, 1_000_000L + 14_460_000L, 50_000L + 14_460_000L))
        assertTrue(InlineTimerEngine.isRestorable(countdown, 1_000_000L + 14_460_000L, 50_000L + 14_460_000L))
    }
}
