package com.enil.logez.feature.activity

import com.enil.logez.core.common.PolylineEncoding
import com.enil.logez.core.domain.calc.RouteSplitsCalculator
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.fakes.FakeActivityTrackRepository
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeLocationSource
import com.enil.logez.fakes.FakeWorkoutRepository
import com.enil.logez.feature.activity.location.LocationFix
import com.enil.logez.feature.activity.location.fixAgeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The GPS sanity filter in [ActivityTrackingController.onFix]: a stale fix (older than 10 s) and a
 * hop faster than 12 m/s from the last sane fix are dropped, so distance, route, "pace now" and
 * splits never see them. Expected values are literal: one degree of latitude is 111,194.9 m on the
 * 6,371 km sphere [com.enil.logez.core.common.GeoDistance] uses, so 0.001 deg is 111.19 m, 0.00045
 * deg is 50.04 m, 0.09 deg is 10,007.5 m, 0.18 deg is 20,015.1 m and 0.045 deg is 5,003.8 m.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GpsSanityFilterTest {
    private val start = 1_000_000L
    private val home = 14.5995
    private val lon = 120.9842

    private val locationSource = FakeLocationSource()
    private val clock = FakeClock(start)
    private val trackRepo = FakeActivityTrackRepository()
    private val controller = ActivityTrackingController(
        FakeWorkoutRepository(sets = listOf(blankSet())), trackRepo, locationSource, clock, CoroutineScope(UnconfinedTestDispatcher()),
    )

    private fun blankSet() = WorkoutSetEntity(
        id = "set-1", workoutExerciseId = "we-1", orderIndex = 0, setType = SetType.NORMAL,
        weightKg = null, reps = null, durationSeconds = null, distanceMeters = null,
        rpe = null, customMetric = null, isCompleted = false, completedAt = null,
    )

    /** Moves the clock to [seconds] after the start and delivers a fix there that was taken [ageMillis] ago. */
    private fun fixAt(seconds: Long, lat: Double, ageMillis: Long = 0L, accuracy: Float = 5f) {
        clock.currentMillis = start + seconds * 1000L
        locationSource.emit(LocationFix(lat, lon, accuracy, 0L, ageMillis))
    }

    private val state get() = controller.state.value

    // ---- Stale fixes ----

    @Test
    fun `a stale first fix 10 km away is ignored, so the run is not credited the 848 km per hour hop`() = runTest {
        controller.startTracking("w-1", "set-1")
        fixAt(2, home + 0.09, ageMillis = 600_000L) // cached 10 min ago, 10,007 m north of the start
        fixAt(42, home) // the first real fix: 10,007 m / 40 s would have been 250 m/s
        fixAt(72, home - 0.001) // 111.19 m south in 30 s

        assertEquals(listOf(home to lon, (home - 0.001) to lon), state.routePoints)
        assertEquals(111.19, state.distanceMeters, 0.05)
        val finished = controller.finishTracking()!!
        assertEquals(111.19, finished.distanceMeters, 0.05)
        assertEquals(72, finished.durationSeconds)
        // Average speed 111.19 m over 72 s is 5.56 km/h, not 848.
        assertEquals(5.56, finished.distanceMeters / finished.durationSeconds * 3.6, 0.01)
    }

    @Test
    fun `a fix exactly 10 seconds old is kept and one 10001 ms old is dropped`() = runTest {
        controller.startTracking("w-1", "set-1")
        fixAt(5, home, ageMillis = 10_001L)
        assertEquals(emptyList<Pair<Double, Double>>(), state.routePoints)
        assertNull(state.currentPosition)

        fixAt(8, home, ageMillis = 10_000L)
        assertEquals(listOf(home to lon), state.routePoints)
    }

    @Test
    fun `a stale fix is not signal either, so the chip stays finding`() = runTest {
        controller.startTracking("w-1", "set-1")
        fixAt(3, home, ageMillis = 30_000L)

        assertNull(state.lastGoodFixMillis)
        assertEquals(GpsSignal.FINDING, state.gpsSignal(clock.currentMillis))
    }

    @Test
    fun `an emulator replaying its last fix with the old timestamp goes stale and the chip says weak`() = runTest {
        controller.startTracking("w-1", "set-1")
        fixAt(0, home)
        fixAt(30, home, ageMillis = 30_000L) // the same reading again, still stamped 30 s ago

        assertEquals(start, state.lastGoodFixMillis) // only the real fix counted
        assertEquals(GpsSignal.WEAK, state.gpsSignal(clock.currentMillis))
        assertEquals(0.0, state.distanceMeters, 0.001)
    }

    @Test
    fun `a replayed fix stamped fresh at the same spot is just a stationary fix`() = runTest {
        controller.startTracking("w-1", "set-1")
        fixAt(0, home)
        fixAt(3, home)
        fixAt(6, home)

        assertEquals(1, state.routePoints.size)
        assertEquals(start + 6_000L, state.lastGoodFixMillis)
        assertEquals(GpsSignal.GOOD, state.gpsSignal(clock.currentMillis))
    }

    @Test
    fun `a fix age is the gap between now and its own timestamp, never negative, and zero when unset`() {
        assertEquals(2_500L, fixAgeMillis(nowElapsedRealtimeNanos = 12_000_000_000L, fixElapsedRealtimeNanos = 9_500_000_000L))
        assertEquals(0L, fixAgeMillis(nowElapsedRealtimeNanos = 9_000_000_000L, fixElapsedRealtimeNanos = 9_500_000_000L)) // ahead of now
        assertEquals(0L, fixAgeMillis(nowElapsedRealtimeNanos = 9_000_000_000L, fixElapsedRealtimeNanos = 0L)) // provider set none
    }

    // ---- Impossible hops ----

    @Test
    fun `a fresh but wrong first fix 10 km away is dropped from, then replaced once three fixes agree`() = runTest {
        controller.startTracking("w-1", "set-1")
        fixAt(2, home + 0.09, accuracy = 15f) // fresh and within accuracy, but really a stale-looking cache hit
        fixAt(42, home) // 10,007 m in 40 s: 250 m/s, a jump
        assertEquals(listOf(home + 0.09 to lon), state.routePoints) // the first rejected fix changes nothing
        assertEquals(0.0, state.distanceMeters, 0.001)
        assertEquals(home + 0.09 to lon, state.currentPosition) // the dot stays on the last sane fix
        assertEquals(start + 42_000L, state.lastGoodFixMillis) // but the signal counts

        fixAt(45, home - 0.0001) // agrees with the previous rejected fix: 11 m in 3 s
        assertEquals(0.0, state.distanceMeters, 0.001)

        fixAt(48, home - 0.0002) // third agreeing fix: re-anchor here, the bad anchor was the whole route
        assertEquals(listOf(home - 0.0002 to lon), state.routePoints)
        assertEquals(listOf(0.0), state.routeDistances)
        assertEquals(listOf(48L), state.routeTimes)
        assertEquals(0.0, state.distanceMeters, 0.001)
        assertEquals(home - 0.0002 to lon, state.currentPosition)

        fixAt(78, home - 0.0012) // 111.19 m in 30 s
        assertEquals(111.19, state.distanceMeters, 0.05)
        assertEquals(2, state.routePoints.size)
        controller.finishTracking()
        // The accuracy average covers the two kept points (5 m each) only: the bad first fix's 15 m is gone with its point.
        val track = trackRepo.getByWorkoutSetId("set-1")!!
        assertEquals(2, track.pointCount)
        assertEquals(5.0, track.avgAccuracyM!!, 1e-9)
    }

    @Test
    fun `a jump mid-run is dropped and the run carries on from the last sane fix`() = runTest {
        controller.startTracking("w-1", "set-1")
        fixAt(0, home)
        fixAt(30, home - 0.001) // 111.19 m
        fixAt(33, home - 0.001 + 0.0045) // 500 m north in 3 s: a glitch
        assertEquals(111.19, state.distanceMeters, 0.05)
        assertEquals(2, state.routePoints.size)
        assertEquals(home - 0.001 to lon, state.currentPosition)

        fixAt(60, home - 0.002) // 111.19 m further than the real position, 30 s on
        assertEquals(222.39, state.distanceMeters, 0.1)
        assertEquals(3, state.routePoints.size)
    }

    @Test
    fun `just under 12 m per second is accepted and just over is dropped`() = runTest {
        controller.startTracking("w-1", "set-1")
        fixAt(0, home)
        fixAt(3, home - 0.0003237) // 35.996 m in 3 s: 11.9987 m/s
        assertEquals(35.996, state.distanceMeters, 0.01)

        fixAt(6, home - 0.0003237 - 0.0003242) // 36.050 m in 3 s: 12.017 m/s
        assertEquals(35.996, state.distanceMeters, 0.01)
        assertEquals(2, state.routePoints.size)
    }

    @Test
    fun `a gap with no fix is judged over its real length, so a tunnel at running speed is not a jump`() = runTest {
        controller.startTracking("w-1", "set-1")
        fixAt(0, home)
        fixAt(60, home - 0.0054) // 600.45 m in 60 s: 10.0 m/s after a minute with no fix
        assertEquals(600.45, state.distanceMeters, 0.1)
    }

    @Test
    fun `the same 600 m in 40 s is 15 m per second and dropped`() = runTest {
        controller.startTracking("w-1", "set-1")
        fixAt(0, home)
        fixAt(40, home - 0.0054)
        assertEquals(0.0, state.distanceMeters, 0.001)
        assertEquals(1, state.routePoints.size)
    }

    @Test
    fun `standing still does not hide a later jump, the last sane fix is what it is judged against`() = runTest {
        controller.startTracking("w-1", "set-1")
        fixAt(0, home)
        // Ten minutes of fixes 1.1 m apart: each is under the 3 m floor, so none is a route point.
        for (minute in 1..10) fixAt(minute * 60L, home - 0.00001)
        // 5,003.8 m in 3 s. Judged from the last route point (10 min ago) it would be 8.3 m/s, a walk.
        fixAt(603, home - 0.045)

        assertEquals(0.0, state.distanceMeters, 0.001)
        assertEquals(1, state.routePoints.size)
    }

    @Test
    fun `an agreeing run of fixes a long way off does not reanchor while the hops between them are also too fast`() = runTest {
        controller.startTracking("w-1", "set-1")
        fixAt(0, home)
        // A vehicle at 15 m/s: 45 m every 3 s. Every fix is a jump from the anchor, and from the one before.
        for (i in 1..6) fixAt(i * 3L, home - i * 0.000404)

        assertEquals(0.0, state.distanceMeters, 0.001)
        assertEquals(1, state.routePoints.size)
    }

    // ---- Zero, tiny and backwards time ----

    @Test
    fun `two fixes at the same instant 111 m apart cannot both be right, the second is dropped`() = runTest {
        controller.startTracking("w-1", "set-1")
        fixAt(0, home)
        fixAt(0, home - 0.001)

        assertEquals(0.0, state.distanceMeters, 0.001)
        assertEquals(1, state.routePoints.size)
    }

    @Test
    fun `two fixes at the same instant 11 m apart are judged over one second and kept`() = runTest {
        controller.startTracking("w-1", "set-1")
        fixAt(0, home)
        fixAt(0, home - 0.0001) // 11.12 m, under the 12 m a one-second floor allows

        assertEquals(11.12, state.distanceMeters, 0.01)
        assertEquals(2, state.routePoints.size)
    }

    @Test
    fun `two fixes at the same instant 13 m apart are dropped, so the one-second floor is not longer than a second`() = runTest {
        controller.startTracking("w-1", "set-1")
        fixAt(0, home)
        fixAt(0, home - 0.000117) // 13.01 m, just over the 12 m a one-second floor allows

        assertEquals(0.0, state.distanceMeters, 0.001)
        assertEquals(1, state.routePoints.size)
    }

    @Test
    fun `when the clock steps back a small move is kept and a big one is still dropped`() = runTest {
        controller.startTracking("w-1", "set-1")
        fixAt(100, home)
        fixAt(40, home - 0.0001) // an hour-style step back: 60 s earlier than the last fix, 11.12 m away
        assertEquals(11.12, state.distanceMeters, 0.01)

        fixAt(39, home - 0.0001 - 0.00045) // 50.04 m, one second earlier still
        assertEquals(11.12, state.distanceMeters, 0.01)
        assertEquals(2, state.routePoints.size)
    }

    @Test
    fun `a delivery delay does not make a hop look faster, the fixes' own times are used`() = runTest {
        controller.startTracking("w-1", "set-1")
        fixAt(0, home)
        // Delivered at 13 s but taken at 5 s: 55.6 m in 5 s is 11.1 m/s. Judged from delivery it would be 4.3 m/s.
        fixAt(13, home - 0.0005, ageMillis = 8_000L)
        assertEquals(55.6, state.distanceMeters, 0.05)
    }

    @Test
    fun `a delivery delay is not forgiven either, a slow arrival does not hide a real jump`() = runTest {
        controller.startTracking("w-1", "set-1")
        fixAt(0, home)
        // Arrives 13 s in, which alone would make 55.6 m a walk, but it was taken only 2 s after the first fix.
        fixAt(13, home - 0.0005, ageMillis = 9_000L) // taken at 4 s: 55.6 m in 4 s = 13.9 m/s
        assertEquals(0.0, state.distanceMeters, 0.001)
    }

    // ---- Pauses ----

    @Test
    fun `a person driven 20 km while paused adds no distance and the first fix after Resume is not a jump`() = runTest {
        controller.startTracking("w-1", "set-1")
        fixAt(0, home)
        fixAt(30, home - 0.001)
        clock.currentMillis = start + 40_000L
        controller.pause()
        fixAt(300, home + 0.18) // 20,015 m away, while paused: no hop check while paused
        clock.currentMillis = start + 305_000L
        controller.resume() // the fix 5 s ago is recent: re-anchor to it
        fixAt(345, home + 0.18 - 0.001) // 111.19 m in 45 s

        assertEquals(111.19 + 111.19, state.distanceMeters, 0.1)
        assertEquals(3, state.routePoints.size)
        assertEquals(listOf(40L to 305L), state.pauseRanges)
    }

    @Test
    fun `a first fix 20 km away after a long pause with no fix is judged over the whole time, dropped, then re-anchored after three agree`() = runTest {
        controller.startTracking("w-1", "set-1")
        fixAt(0, home)
        fixAt(30, home - 0.001)
        clock.currentMillis = start + 40_000L
        controller.pause()
        clock.currentMillis = start + 640_000L
        controller.resume()
        fixAt(641, home + 0.18) // 20,015 m from the fix at 30 s, 611 s earlier: 32.8 m/s even counting the pause
        assertEquals(111.19, state.distanceMeters, 0.05)
        assertEquals(2, state.routePoints.size)

        fixAt(644, home + 0.18 - 0.0001) // agrees with it
        fixAt(647, home + 0.18 - 0.0002) // third agreeing fix: the person really was taken there, re-anchor, no distance for it
        assertEquals(111.19, state.distanceMeters, 0.05)
        assertEquals(3, state.routePoints.size) // the re-anchor fix joins the route as a point of its own

        fixAt(677, home + 0.18 - 0.0012) // 111.19 m from the re-anchor in 30 s
        assertEquals(222.39, state.distanceMeters, 0.1)
        assertEquals(4, state.routePoints.size)
        assertEquals(listOf(40L to 640L), state.pauseRanges)
    }

    @Test
    fun `after a long pause with no fix, a first fix a walk away is kept and adds no distance`() = runTest {
        controller.startTracking("w-1", "set-1")
        fixAt(0, home)
        fixAt(30, home - 0.001)
        clock.currentMillis = start + 40_000L
        controller.pause()
        clock.currentMillis = start + 640_000L
        controller.resume()
        fixAt(641, home - 0.001 - 0.0045) // 500 m from the fix at 30 s in 611 s: 0.8 m/s

        assertEquals(111.19, state.distanceMeters, 0.05) // the gap is not counted
        assertEquals(3, state.routePoints.size)

        fixAt(671, home - 0.001 - 0.0055) // then 111.19 m on in 30 s
        assertEquals(222.39, state.distanceMeters, 0.1)
    }

    @Test
    fun `a ghost fix just after a pause too short to catch any fix is dropped, the pre-pause fix is kept to compare with`() = runTest {
        controller.startTracking("w-1", "set-1")
        fixAt(0, home)
        fixAt(3, home - 0.0001) // 11.12 m
        clock.currentMillis = start + 4_000L
        controller.pause()
        clock.currentMillis = start + 6_000L
        controller.resume() // no fix arrived in between
        fixAt(7, home - 0.0001 + 0.0045) // 500 m away 4 s after the last sane fix

        assertEquals(11.12, state.distanceMeters, 0.01)
        assertEquals(2, state.routePoints.size)

        // A real fix: the first after Resume starts the new stretch, so it is a route point with no distance of its own.
        fixAt(10, home - 0.0002)
        assertEquals(11.12, state.distanceMeters, 0.01)
        assertEquals(3, state.routePoints.size)
        fixAt(40, home - 0.0012) // then 111.19 m on in 30 s
        assertEquals(122.31, state.distanceMeters, 0.05)
    }

    @Test
    fun `a fresh paused fix is the re-anchor, and the first fix after Resume is still checked against it`() = runTest {
        controller.startTracking("w-1", "set-1")
        fixAt(0, home)
        fixAt(30, home - 0.001)
        clock.currentMillis = start + 40_000L
        controller.pause()
        fixAt(50, home) // paused, 111 m back up the road
        clock.currentMillis = start + 52_000L
        controller.resume() // 2 s later: the fix at 50 s is the anchor
        fixAt(55, home + 0.0045) // 500 m from the anchor in 5 s: a jump

        assertEquals(111.19, state.distanceMeters, 0.05)
        assertEquals(2, state.routePoints.size)

        fixAt(85, home - 0.001) // 111.19 m from the anchor in 35 s: a walk
        assertEquals(222.39, state.distanceMeters, 0.1)
        assertEquals(3, state.routePoints.size)
    }

    @Test
    fun `a fix that was already stale at Resume is not used as the re-anchor`() = runTest {
        controller.startTracking("w-1", "set-1")
        fixAt(0, home)
        fixAt(30, home - 0.001)
        clock.currentMillis = start + 40_000L
        controller.pause()
        fixAt(50, home + 0.18)
        clock.currentMillis = start + 400_000L // 350 s later with nothing newer
        controller.resume()
        // 20 km from that old paused fix, but it is not the anchor: the fix from before the pause is, and this is
        // 111 m from that one over 371 s. Kept as the start of the new stretch, with no distance for the gap.
        fixAt(401, home)

        assertEquals(111.19, state.distanceMeters, 0.05)
        assertEquals(3, state.routePoints.size)
    }

    // ---- A re-anchor in the middle of a run ----

    /**
     * 111.19 m of run, then the tracker loses the runner: three agreeing fixes 5,003.8 m away (0.045 deg),
     * which is a re-anchor with two route points already saved. The test then walks 111.19 m on from there.
     */
    private fun runWithAMidRunReanchor() {
        controller.startTracking("w-1", "set-1")
        fixAt(0, home)
        fixAt(30, home - 0.001)
        fixAt(33, home + 0.045) // jump 1
        fixAt(36, home + 0.045 - 0.0001) // agrees with it: 11 m in 3 s
        fixAt(39, home + 0.045 - 0.0002) // third: re-anchor here
    }

    @Test
    fun `a re-anchor mid-run adds no distance, and the next fix counts only its own hop from the new place`() = runTest {
        runWithAMidRunReanchor()
        assertEquals(111.19, state.distanceMeters, 0.05)
        assertEquals(3, state.routePoints.size) // the re-anchor fix is a route point, so the next hop is in the saved line
        assertEquals(home + 0.045 - 0.0002 to lon, state.currentPosition) // the dot moves to the new place
        assertEquals(listOf(0.0, 111.19, 111.19), state.routeDistances.map { Math.round(it * 100) / 100.0 })

        fixAt(69, home + 0.045 - 0.0012) // 111.19 m from the re-anchor fix in 30 s
        assertEquals(222.39, state.distanceMeters, 0.1)
        assertEquals(4, state.routePoints.size)
        assertEquals(listOf(0.0, 111.19, 111.19, 222.39), state.routeDistances.map { Math.round(it * 100) / 100.0 })
        assertEquals(listOf(0L, 30L, 39L, 69L), state.routeTimes)
    }

    @Test
    fun `after a mid-run re-anchor the saved splits and pace read the real stretches, not the jump between them`() = runTest {
        runWithAMidRunReanchor()
        fixAt(69, home + 0.045 - 0.0012)
        val finished = controller.finishTracking()!!
        assertEquals(222.39, finished.distanceMeters, 0.1)

        val track = trackRepo.getByWorkoutSetId("set-1")!!
        val points = PolylineEncoding.decode(track.routePolyline!!)
        val times = PolylineEncoding.decodeDeltas(track.routeTimes!!)
        assertEquals(4, points.size)
        assertEquals(listOf(0L, 30L, 39L, 69L), times)

        // The saved line does jump 5.1 km between points 2 and 3 (that is what the map draws) ...
        val unscaled = RouteSplitsCalculator.cumulativeMeters(points)
        assertEquals(5_315.1, unscaled.last(), 1.0) // 111.19 + 5,092.7 + 111.19
        // ... but measured for splits it is the two real hops of 111 m, the jump counts nothing, and nothing is scaled down.
        val cumulative = RouteSplitsCalculator.cumulativeMeters(
            points, trackedDistanceMeters = finished.distanceMeters, timesSeconds = times.map { it.toDouble() },
        )
        assertEquals(0.0, cumulative[0], 1e-9)
        assertEquals(111.19, cumulative[1], 0.6)
        assertEquals(cumulative[1], cumulative[2], 1e-9)
        assertEquals(222.39, cumulative[3], 0.01)

        // Splits over the saved data: one partial of the 222 m actually run, over its 69 s (310 s per km).
        val splits = RouteSplitsCalculator.splits(
            points, times.map { it.toInt() }, DistanceUnit.KM, start, trackedDistanceMeters = finished.distanceMeters,
        )
        assertEquals(1, splits.size)
        assertEquals(222.39, splits[0].distanceMeters, 0.1)
        assertEquals(69, splits[0].durationSeconds)
        assertEquals(310.3, splits[0].paceSecondsPerUnit, 0.5)
    }

    @Test
    fun `the controller's jump speed and the calculator's are the same number`() {
        assertEquals(MAX_PLAUSIBLE_SPEED_MPS, RouteSplitsCalculator.JUMP_SPEED_METERS_PER_SECOND, 0.0)
        // Its slack covers a fix at the age limit plus a second of rounding in the saved whole-second times.
        assertEquals(MAX_FIX_AGE_MS / 1000.0 + 1.0, RouteSplitsCalculator.JUMP_TIME_SLACK_SECONDS, 0.0)
    }

    // ---- What the filter keeps consistent ----

    @Test
    fun `distance, route, pace now and the saved distance all ignore a dropped jump`() = runTest {
        controller.startTracking("w-1", "set-1")
        // 50.04 m every 10 s (5.0 m/s) for three minutes, with a 5 km jump delivered at the 90 s mark.
        for (i in 0..18) {
            if (i == 9) fixAt(i * 10L, home - i * 0.00045 + 0.045)
            fixAt(i * 10L, home - i * 0.00045)
        }

        assertEquals(19, state.routePoints.size)
        assertEquals(900.68, state.distanceMeters, 0.05)
        assertEquals(state.distanceMeters, state.routeDistances.last(), 0.0)
        assertEquals((0..18).map { it * 10L }, state.routeTimes)
        // The last minute (120 s to 180 s) covered six steps, 300.23 m: 60 s / 0.30023 km = 199.85 s per km.
        val stats = state.liveStats(DistanceUnit.KM, start + 180_000L)
        assertEquals(199.85, stats.paceNowSecondsPerUnit!!, 0.1)

        val finished = controller.finishTracking()!!
        assertEquals(900.68, finished.distanceMeters, 0.05)
        val track = trackRepo.getByWorkoutSetId("set-1")!!
        assertEquals(19, track.pointCount)
        assertEquals(19, PolylineEncoding.decode(track.routePolyline!!).size)
        assertEquals((0..18).map { it * 10L }, PolylineEncoding.decodeDeltas(track.routeTimes!!))
    }
}
