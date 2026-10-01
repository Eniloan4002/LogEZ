package com.enil.logez.feature.workout.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Service's wake-lock timeout: time left plus 10 s slack, capped per timer. Literal values throughout. */
class TimerWakeLockTest {
    @Test
    fun `no deadline asks for no lock`() {
        assertNull(TimerWakeLock.timeoutMs(null, null, 5_000L))
    }

    @Test
    fun `a rest timer holds the lock for the time left plus 10 seconds`() {
        assertEquals(100_000L, TimerWakeLock.timeoutMs(95_000L, null, 5_000L))
    }

    @Test
    fun `a rest timer keeps its original 5 minute cap`() {
        assertEquals(300_000L, TimerWakeLock.timeoutMs(5_000L + 600_000L, null, 5_000L))
    }

    @Test
    fun `a 20 minute countdown is not cut to the rest cap`() {
        assertEquals(1_210_000L, TimerWakeLock.timeoutMs(null, 1_205_000L, 5_000L))
    }

    @Test
    fun `a mistyped day-long countdown is capped at 4 hours`() {
        assertEquals(14_400_000L, TimerWakeLock.timeoutMs(null, 86_400_000L, 0L))
    }

    @Test
    fun `a deadline already past still asks for the slack so the expiry can be handled`() {
        assertEquals(10_000L, TimerWakeLock.timeoutMs(null, 3_000L, 9_000L))
    }

    @Test
    fun `if both timers somehow have deadlines the longer request wins`() {
        assertEquals(1_210_000L, TimerWakeLock.timeoutMs(65_000L, 1_205_000L, 5_000L))
    }
}
