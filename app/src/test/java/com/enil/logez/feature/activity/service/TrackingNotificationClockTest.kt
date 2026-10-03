package com.enil.logez.feature.activity.service

import com.enil.logez.feature.activity.ActivityTrackingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingNotificationClockTest {
    private val started = 1_000_000L

    @Test
    fun `a run that was never paused counts from when it started`() {
        val state = ActivityTrackingState(workoutId = "w", startedAtMillis = started)
        val clock = trackingNotificationClock(state, nowMillis = started + 125_000L)
        assertTrue(clock.running)
        assertEquals(started, clock.baseMillis)
    }

    @Test
    fun `after a pause the chronometer counts moving time, so the base moves later by the paused time`() {
        val state = ActivityTrackingState(workoutId = "w", startedAtMillis = started, pausedMillisTotal = 40_000L)
        val clock = trackingNotificationClock(state, nowMillis = started + 125_000L)
        assertTrue(clock.running)
        assertEquals(started + 40_000L, clock.baseMillis)
    }

    @Test
    fun `while paused the chronometer is not running, and the moving time behind its base stays 60 s`() {
        val state = ActivityTrackingState(workoutId = "w", startedAtMillis = started, isPaused = true, pausedAtMillis = started + 60_000L)
        val atPause = trackingNotificationClock(state, nowMillis = started + 60_000L)
        val later = trackingNotificationClock(state, nowMillis = started + 100_000L)
        assertFalse(atPause.running)
        assertFalse(later.running)
        // Moving time is 60 s whenever it is asked while paused: the base trails "now" by exactly that.
        assertEquals(60_000L, (started + 60_000L) - atPause.baseMillis)
        assertEquals(60_000L, (started + 100_000L) - later.baseMillis)
    }
}
