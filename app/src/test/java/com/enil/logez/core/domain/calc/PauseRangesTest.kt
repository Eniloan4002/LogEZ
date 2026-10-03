package com.enil.logez.core.domain.calc

import com.enil.logez.core.common.PolylineEncoding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseRangesTest {
    private val ranges = listOf(100L to 160L, 400L to 430L)

    @Test
    fun `encode then decode returns the same ranges, in order`() {
        assertEquals(ranges, PauseRanges.decode(PauseRanges.encode(ranges)))
    }

    @Test
    fun `no ranges encode to null and null or blank decode to none, so a never-paused run stores nothing`() {
        assertNull(PauseRanges.encode(emptyList()))
        assertTrue(PauseRanges.decode(null).isEmpty())
        assertTrue(PauseRanges.decode("").isEmpty())
    }

    @Test
    fun `a zero-length pause survives the round trip`() {
        assertEquals(listOf(50L to 50L), PauseRanges.decode(PauseRanges.encode(listOf(50L to 50L))))
    }

    @Test
    fun `total seconds adds every range`() {
        assertEquals(90L, PauseRanges.totalSeconds(ranges))
        assertEquals(0L, PauseRanges.totalSeconds(emptyList()))
    }

    @Test
    fun `moving seconds leaves out the paused time before a moment`() {
        assertEquals(50.0, PauseRanges.movingSeconds(50.0, ranges), 0.001) // before any pause
        assertEquals(100.0, PauseRanges.movingSeconds(130.0, ranges), 0.001) // inside the first pause: frozen at its start
        assertEquals(140.0, PauseRanges.movingSeconds(200.0, ranges), 0.001) // after the first pause (60 s out)
        assertEquals(340.0, PauseRanges.movingSeconds(430.0, ranges), 0.001) // at the end of the second pause: 90 s out
        assertEquals(380.0, PauseRanges.movingSeconds(470.0, ranges), 0.001) // 40 s after it, 90 s still out of 470
    }

    @Test
    fun `clock seconds is the inverse of moving seconds outside a pause`() {
        for (clock in listOf(10.0, 99.0, 170.0, 399.0, 450.0, 900.0)) {
            val moving = PauseRanges.movingSeconds(clock, ranges)
            assertEquals(clock, PauseRanges.clockSeconds(moving, ranges), 0.001)
        }
    }

    @Test
    fun `break indices mark only the hop that crosses a pause`() {
        // Points at 90, 100 (the pause starts), 160 (it ends), 170.
        val times = listOf(90, 100, 160, 170)
        assertEquals(setOf(2), PauseRanges.breakIndices(times, listOf(100L to 160L)))
    }

    @Test
    fun `a hop that merely contains no pause is not a break, and a point inside the pause makes none`() {
        assertTrue(PauseRanges.breakIndices(listOf(0, 10, 20), listOf(100L to 160L)).isEmpty())
        // A point recorded inside the pause (the race the controller's lock prevents) crosses nothing cleanly.
        assertTrue(PauseRanges.breakIndices(listOf(90, 120, 170), listOf(100L to 160L)).isEmpty())
        assertTrue(PauseRanges.breakIndices(listOf(1, 2, 3), emptyList()).isEmpty())
    }

    @Test
    fun `a pause under a second is stored as a zero-length range and breaks nothing`() {
        // A quick double tap: Pause and Resume in the same whole second as a fix. Both hops around that
        // fix would match a zero-length range, though the user lost no ground, so neither is a break.
        assertEquals(emptySet<Int>(), PauseRanges.breakIndices(listOf(40, 41), listOf(40L to 40L)))
        assertEquals(emptySet<Int>(), PauseRanges.breakIndices(listOf(97, 100, 102), listOf(100L to 100L)))
        // A real pause beside it is still found.
        assertEquals(setOf(2), PauseRanges.breakIndices(listOf(97, 100, 140), listOf(100L to 100L, 105L to 135L)))
    }

    @Test
    fun `decode drops a trailing start with no end, and reads nothing from an odd or blank string`() {
        val odd = PolylineEncoding.encodeDeltas(listOf(10L, 20L, 30L))
        assertEquals(listOf(10L to 20L), PauseRanges.decode(odd))
        assertEquals(emptyList<Pair<Long, Long>>(), PauseRanges.decode(""))
    }

    @Test
    fun `segments split at the breaks and drop one-point stretches`() {
        val points = (0..5).map { it.toDouble() to 0.0 }
        assertEquals(listOf(points.subList(0, 3), points.subList(3, 6)), PauseRanges.segments(points, setOf(3)))
        // Breaks at 2 and 3 leave point 2 alone between them: it draws nothing and is dropped.
        assertEquals(listOf(points.subList(0, 2), points.subList(3, 6)), PauseRanges.segments(points, setOf(2, 3)))
        assertEquals(listOf(points), PauseRanges.segments(points, emptySet()))
        assertTrue(PauseRanges.segments(emptyList(), emptySet()).isEmpty())
    }
}
