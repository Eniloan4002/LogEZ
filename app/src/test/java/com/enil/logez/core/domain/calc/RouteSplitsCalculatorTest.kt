package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.DistanceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSplitsCalculatorTest {
    private val startedAt = 10_000_000L

    /** Points due north every 100 m along a meridian, so distances are exact enough to reason about. */
    private fun northbound(count: Int): List<Pair<Double, Double>> {
        val degreesPer100m = 100.0 / 111_195.0 // GeoDistance's mean-earth-radius metres per degree
        return (0 until count).map { (14.6 + it * degreesPer100m) to 121.06 }
    }

    @Test
    fun `a run of 2_5 km gives two full km splits and a partial, with their own paces`() {
        val points = northbound(26) // 0 to 2,500 m
        // 6:00/km for the first km (36 s per 100 m), then 5:00/km (30 s per 100 m).
        val times = points.indices.map { i -> if (i <= 10) i * 36 else 360 + (i - 10) * 30 }
        val splits = RouteSplitsCalculator.splits(points, times, DistanceUnit.KM, startedAt)

        assertEquals(3, splits.size)
        assertEquals(360, splits[0].durationSeconds)
        assertEquals(360.0, splits[0].paceSecondsPerUnit, 1.0)
        assertEquals(300, splits[1].durationSeconds)
        assertFalse(splits[1].isPartial)
        assertTrue(splits[2].isPartial)
        assertEquals(500.0, splits[2].distanceMeters, 2.0)
        assertEquals(150, splits[2].durationSeconds)
        assertEquals(300.0, splits[2].paceSecondsPerUnit, 2.0)
    }

    @Test
    fun `a trailing piece under 50 m is left out rather than shown as a tiny split`() {
        val points = northbound(11) + ((14.6 + 1030.0 / 111_195.0) to 121.06) // 1,030 m
        val times = points.indices.map { it * 30 }
        val splits = RouteSplitsCalculator.splits(points, times, DistanceUnit.KM, startedAt)
        assertEquals(1, splits.size)
        assertFalse(splits[0].isPartial)
    }

    @Test
    fun `mile splits use 1609 m`() {
        val points = northbound(34) // 3,300 m, just over two miles
        val times = points.indices.map { it * 30 }
        val splits = RouteSplitsCalculator.splits(points, times, DistanceUnit.MILES, startedAt)
        assertEquals(3, splits.size) // two full miles and a partial of ~81 m
        assertEquals(483, splits[0].durationSeconds) // 1609.344 m at 30 s per 100 m
    }

    @Test
    fun `each split's heart rate averages only the samples inside it`() {
        val points = northbound(21)
        val times = points.indices.map { it * 30 } // 300 s per km
        val heartRate = listOf(
            (startedAt + 100_000) to 140L, (startedAt + 200_000) to 150L, // first km
            (startedAt + 400_000) to 170L, // second km
        )
        val splits = RouteSplitsCalculator.splits(points, times, DistanceUnit.KM, startedAt, heartRate)
        assertEquals(145L, splits[0].averageBpm)
        assertEquals(170L, splits[1].averageBpm)
    }

    @Test
    fun `no splits without a time for every point, as for a run tracked before times were saved`() {
        val points = northbound(21)
        assertTrue(RouteSplitsCalculator.splits(points, emptyList(), DistanceUnit.KM, startedAt).isEmpty())
        assertTrue(RouteSplitsCalculator.splits(points, listOf(0, 30), DistanceUnit.KM, startedAt).isEmpty())
        assertTrue(RouteSplitsCalculator.paceSeries(points, emptyList(), DistanceUnit.KM, startedAt).isEmpty())
    }

    @Test
    fun `the pace series is the pace over the preceding minute, timestamped from the workout start`() {
        val points = northbound(21)
        val times = points.indices.map { it * 30 } // steady 5:00/km
        val series = RouteSplitsCalculator.paceSeries(points, times, DistanceUnit.KM, startedAt)
        assertTrue(series.size >= 2)
        assertEquals(startedAt + 60_000L, series.first().first)
        series.forEach { (_, pace) -> assertEquals(300.0, pace, 1.0) }
    }

    @Test
    fun `standing still produces no pace point for that minute`() {
        val points = northbound(3) + northbound(3).last() + northbound(3).last()
        val times = listOf(0, 30, 60, 120, 180) // 200 m, then a two-minute stop
        val series = RouteSplitsCalculator.paceSeries(points, times, DistanceUnit.KM, startedAt)
        assertTrue(series.none { it.first == startedAt + 180_000L })
    }

    @Test
    fun `a partial split has no heart rate when no sample falls in it`() {
        val points = northbound(16)
        val times = points.indices.map { it * 30 }
        val splits = RouteSplitsCalculator.splits(points, times, DistanceUnit.KM, startedAt, listOf((startedAt + 10_000) to 120L))
        assertNull(splits.last().averageBpm)
    }

    @Test
    fun `splits are scaled to the distance the tracker saved, so they sum to the headline distance`() {
        val points = northbound(21) // measures 2,000 m
        val times = points.indices.map { it * 30 }
        // The tracker saved 1,990 m (the rounded route measured 0.5% long): two splits become one full and a partial.
        val splits = RouteSplitsCalculator.splits(points, times, DistanceUnit.KM, startedAt, trackedDistanceMeters = 1_990.0)
        assertEquals(2, splits.size)
        assertTrue(splits[1].isPartial)
        assertEquals(990.0, splits[1].distanceMeters, 1.0)
        assertEquals(1_990.0, RouteSplitsCalculator.cumulativeMeters(points, 1_990.0).last(), 1e-6)
    }

    // ---- pauses (v11, 2026-10-01) ----

    /**
     * 21 points 100 m apart, 30 s each (a steady 5:00/km). The runner pauses at point 5 (150 s) for five
     * minutes and resumes at 450 s, where point 6 is recorded. The tracker counted nothing for the hop
     * between them, so the route is 1,900 m long.
     */
    private fun pausedRun(): Triple<List<Pair<Double, Double>>, List<Int>, List<Pair<Long, Long>>> {
        val points = northbound(21)
        val times = points.indices.map { i -> if (i <= 5) i * 30 else i * 30 + 270 }
        return Triple(points, times, listOf(150L to 450L))
    }

    @Test
    fun `a pause is left out of every split's time`() {
        val (points, times, ranges) = pausedRun()
        val splits = RouteSplitsCalculator.splits(points, times, DistanceUnit.KM, startedAt, pauseRanges = ranges)
        // Moving at a steady 5:00/km: the 300 s pause must not make the first km 10:00.
        assertEquals(2, splits.size)
        assertEquals(300, splits[0].durationSeconds)
        assertEquals(300.0, splits[0].paceSecondsPerUnit, 1.0)
        assertTrue(splits[1].isPartial)
        assertEquals(900.0, splits[1].distanceMeters, 2.0) // 1,900 m in all: the paused hop counted none
        assertEquals(300.0, splits[1].paceSecondsPerUnit, 1.0)
    }

    @Test
    fun `without the ranges the same run reads slower, which is what the column is for`() {
        val (points, times, _) = pausedRun()
        val splits = RouteSplitsCalculator.splits(points, times, DistanceUnit.KM, startedAt)
        assertEquals(570, splits[0].durationSeconds) // the 300 s pause is in it
    }

    @Test
    fun `the hop that crosses a pause counts no distance, even if the runner moved while paused`() {
        // 400 m of walking between point 5 and 6 happened while paused, and was never counted.
        val points = northbound(6) + (northbound(21).drop(9)) // a 400 m jump between points 5 and 6
        val times = points.indices.map { i -> if (i <= 5) i * 30 else i * 30 + 270 }
        val ranges = listOf(150L to 450L)
        val breaks = PauseRanges.breakIndices(times, ranges)
        assertEquals(setOf(6), breaks)
        val withBreak = RouteSplitsCalculator.cumulativeMeters(points, breakIndices = breaks)
        val without = RouteSplitsCalculator.cumulativeMeters(points)
        assertEquals(500.0, withBreak[5], 2.0)
        assertEquals(500.0, withBreak[6], 2.0) // no distance for the paused hop
        assertTrue(without[6] > 800.0)
    }

    @Test
    fun `a split's heart rate window stays on the clock, so samples taken after a pause still land in it`() {
        val (points, times, ranges) = pausedRun()
        // The first km ends at clock 600 s (point 11); the partial after it runs to 870 s.
        val heartRate = listOf((startedAt + 700_000L) to 160L) // clock 700 s: inside the second split, after the pause
        val splits = RouteSplitsCalculator.splits(points, times, DistanceUnit.KM, startedAt, heartRate, pauseRanges = ranges)
        assertNull(splits[0].averageBpm)
        assertEquals(160L, splits[1].averageBpm)
    }

    @Test
    fun `a heart rate sample taken while paused is left out of the split it falls in`() {
        val (points, times, ranges) = pausedRun()
        // Clock 100 s and 500 s are moving; clock 300 s is inside the pause (150 to 450 s) at a resting 90.
        val heartRate = listOf((startedAt + 100_000L) to 150L, (startedAt + 300_000L) to 90L, (startedAt + 500_000L) to 170L)
        val splits = RouteSplitsCalculator.splits(points, times, DistanceUnit.KM, startedAt, heartRate, pauseRanges = ranges)
        assertEquals(160L, splits[0].averageBpm)
    }

    @Test
    fun `the pace series skips the paused minutes and keeps its x values on the clock`() {
        val (points, times, ranges) = pausedRun()
        val series = RouteSplitsCalculator.paceSeries(points, times, DistanceUnit.KM, startedAt, pauseRanges = ranges)
        assertTrue(series.size >= 2)
        // A steady 5:00/km before and after: the pause must not produce a slow stretch.
        series.forEach { (_, pace) -> assertEquals(300.0, pace, 2.0) }
        // No point is stamped inside the pause (clock 150 to 450 s).
        assertTrue(series.none { (at, _) -> at > startedAt + 150_000L && at < startedAt + 450_000L })
        // And it goes on past the pause, on the clock.
        assertTrue(series.last().first > startedAt + 450_000L)
    }

    // ---- a jump the tracker did not count (a mid-run re-anchor) ----

    /**
     * 1,000 m of 100 m hops every 30 s (clock 0 to 300 s), then a 5,000 m jump to a point recorded 3 s
     * later (the tracker lost the runner, re-anchored, and counted nothing for the gap), then another
     * 1,000 m at the same pace. The tracker saved 2,000 m.
     */
    private fun runWithAJump(): Pair<List<Pair<Double, Double>>, List<Int>> {
        val degreesPer100m = 100.0 / 111_195.0
        val before = northbound(11) // 0 to 1,000 m
        val after = (1..11).map { i -> (14.6 + 5_000.0 / 111_195.0 + (10 + i) * degreesPer100m) to 121.06 }
        val times = (0..10).map { it * 30 } + (0..10).map { 303 + it * 30 }
        return (before + after) to times
    }

    @Test
    fun `a hop faster than 12 m per second counts no distance, so the splits are the run and not the jump`() {
        val (points, times) = runWithAJump()
        val splits = RouteSplitsCalculator.splits(points, times, DistanceUnit.KM, startedAt, trackedDistanceMeters = 2_000.0)

        assertEquals(2, splits.size)
        // The 1,000 m boundary sits on a vertex, so float rounding can place it at 300 s or at the 303 s after the
        // jump: the two splits together always take 603 s. Without the jump handled, the scale (2,000 / 7,100)
        // would have made the first km 0.28 of it and its pace 1,000 s per km.
        assertEquals(603, splits.sumOf { it.durationSeconds })
        assertEquals(300.0, splits[0].paceSecondsPerUnit, 3.1)
        assertEquals(300.0, splits[1].paceSecondsPerUnit, 3.1)
        assertEquals(1_000.0, splits[0].distanceMeters, 1e-6)
        assertFalse(splits[1].isPartial)
    }

    @Test
    fun `the cumulative distance skips the jump and ends at the tracked distance`() {
        val (points, times) = runWithAJump()
        val cumulative = RouteSplitsCalculator.cumulativeMeters(
            points, trackedDistanceMeters = 2_000.0, timesSeconds = times.map { it.toDouble() },
        )
        assertEquals(1_000.0, cumulative[10], 2.0)
        assertEquals(cumulative[10], cumulative[11], 1e-9) // the jump into point 11 adds nothing
        assertEquals(2_000.0, cumulative.last(), 1e-6)
    }

    @Test
    fun `without times the jump is measured as before, which is what a caller with no times gets`() {
        val (points, _) = runWithAJump()
        assertEquals(7_100.0, RouteSplitsCalculator.cumulativeMeters(points).last(), 5.0)
    }

    @Test
    fun `the pace series is not distorted by the jump either`() {
        val (points, times) = runWithAJump()
        val series = RouteSplitsCalculator.paceSeries(points, times, DistanceUnit.KM, startedAt, trackedDistanceMeters = 2_000.0)
        assertTrue(series.size >= 2)
        series.forEach { (_, pace) -> assertTrue("pace $pace", pace in 295.0..320.0) }
    }

    @Test
    fun `the jump line is 12 m per second plus eleven seconds of slack, so a hop the tracker accepted is never zeroed`() {
        val a = 14.6 to 121.06
        // Same second: 12 m/s * (0 + 11 s) = 132 m allowed. 130 m counts, 140 m does not.
        val near = RouteSplitsCalculator.cumulativeMeters(listOf(a, (14.6 + 130.0 / 111_195.0) to 121.06), timesSeconds = listOf(0.0, 0.0))
        assertEquals(130.0, near[1], 0.01)
        val far = RouteSplitsCalculator.cumulativeMeters(listOf(a, (14.6 + 140.0 / 111_195.0) to 121.06), timesSeconds = listOf(0.0, 0.0))
        assertEquals(0.0, far[1], 1e-9)
        // 3 s apart: 12 * 14 = 168 m allowed. 36 m (a 12 m/s hop the tracker keeps) is far inside it.
        val walk = RouteSplitsCalculator.cumulativeMeters(listOf(a, (14.6 + 36.0 / 111_195.0) to 121.06), timesSeconds = listOf(0.0, 3.0))
        assertEquals(36.0, walk[1], 0.01)
    }
}
