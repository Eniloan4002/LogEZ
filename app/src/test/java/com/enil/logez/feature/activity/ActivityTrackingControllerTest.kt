package com.enil.logez.feature.activity

import com.enil.logez.core.common.PolylineEncoding
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.calc.PauseRanges
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.fakes.FakeActivityTrackRepository
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeLocationSource
import com.enil.logez.fakes.FakeWorkoutRepository
import com.enil.logez.feature.activity.location.LocationFix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityTrackingControllerTest {
    private fun blankSet() = WorkoutSetEntity(
        id = "set-1", workoutExerciseId = "we-1", orderIndex = 0, setType = SetType.NORMAL,
        weightKg = null, reps = null, durationSeconds = null, distanceMeters = null,
        rpe = null, customMetric = null, isCompleted = false, completedAt = null,
    )

    private fun newController(
        workoutRepo: FakeWorkoutRepository = FakeWorkoutRepository(sets = listOf(blankSet())),
        trackRepo: FakeActivityTrackRepository = FakeActivityTrackRepository(),
        locationSource: FakeLocationSource = FakeLocationSource(),
        clock: FakeClock = FakeClock(currentMillis = 1_000_000L),
    ) = ActivityTrackingController(workoutRepo, trackRepo, locationSource, clock, CoroutineScope(UnconfinedTestDispatcher()))

    @Test
    fun `startTracking marks isTracking and records the workout and set ids`() = runTest {
        val controller = newController()
        controller.startTracking(workoutId = "w-1", workoutSetId = "set-1")

        val state = controller.state.value
        assertEquals(true, state.isTracking)
        assertEquals("w-1", state.workoutId)
        assertEquals("set-1", state.workoutSetId)
        assertEquals(0.0, state.distanceMeters, 0.001)
    }

    @Test
    fun `finishTracking writes accumulated distance and duration onto the existing set`() = runTest {
        val workoutRepo = FakeWorkoutRepository(sets = listOf(blankSet()))
        val trackRepo = FakeActivityTrackRepository()
        val locationSource = FakeLocationSource()
        val clock = FakeClock(currentMillis = 1_000_000L)
        val controller = newController(workoutRepo, trackRepo, locationSource, clock)

        controller.startTracking(workoutId = "w-1", workoutSetId = "set-1")
        locationSource.emit(LocationFix(latitude = 14.5995, longitude = 120.9842, accuracyMeters = 5f, elapsedRealtimeMillis = 0L))
        clock.currentMillis = 1_000_000L + 30_000L // ~111 m in 30 s is 3.7 m/s: a walk, not a jump
        locationSource.emit(LocationFix(latitude = 14.5985, longitude = 120.9842, accuracyMeters = 5f, elapsedRealtimeMillis = 3_000L)) // ~111.32m south
        clock.currentMillis = 1_000_000L + 60_000L

        val result = controller.finishTracking()

        assertNotNull(result)
        assertEquals("w-1", result!!.workoutId)
        assertEquals(111.32, result.distanceMeters, 1.0)
        assertEquals(60, result.durationSeconds)
        val updatedSet = workoutRepo.getSetsForWorkoutExercise("we-1").single()
        assertEquals(111.32, updatedSet.distanceMeters!!, 1.0)
        assertEquals(60, updatedSet.durationSeconds)
    }

    /**
     * M21 redesign (2026-09-11): Finish now goes straight to the Save Workout screen instead of
     * through the strength Logger, which used to be the only place that marked this set completed
     * (its checkmark tap) before `WorkoutFinisher.finish()`'s save transaction runs -- that
     * transaction purges any *uncompleted* set first, so without this the GPS-tracked set (and its
     * now-empty exercise) would be silently deleted on save.
     */
    @Test
    fun `finishTracking marks the workout set completed, so WorkoutFinisher's purge step can't delete it`() = runTest {
        val workoutRepo = FakeWorkoutRepository(sets = listOf(blankSet()))
        val clock = FakeClock(currentMillis = 1_000_000L)
        val controller = newController(workoutRepo = workoutRepo, clock = clock)
        controller.startTracking(workoutId = "w-1", workoutSetId = "set-1")

        controller.finishTracking()

        val updatedSet = workoutRepo.getSetsForWorkoutExercise("we-1").single()
        assertEquals(true, updatedSet.isCompleted)
        assertEquals(1_000_000L, updatedSet.completedAt)
    }

    /**
     * M21 redesign (2026-09-11): the live elapsed duration used to get frozen onto the parent
     * `WorkoutEntity` by `WorkoutLoggerViewModel.prepareForFinish()`, which only ran once the
     * strength Logger was reached -- `FinishWorkoutViewModel` reads `WorkoutEntity.durationSeconds`
     * directly and trusts it's already correct, so skipping the Logger means this has to happen
     * here instead.
     */
    @Test
    fun `finishTracking freezes the elapsed duration onto the parent WorkoutEntity`() = runTest {
        val workout = WorkoutEntity(
            id = "w-1", routineId = null, title = "Walking (Outdoor)", notes = null,
            status = WorkoutStatus.IN_PROGRESS, startedAt = 1_000_000L, endedAt = null,
            durationSeconds = 0, createdAt = 1_000_000L, updatedAt = 1_000_000L,
        )
        val workoutRepo = FakeWorkoutRepository(workouts = listOf(workout), sets = listOf(blankSet()))
        val clock = FakeClock(currentMillis = 1_000_000L)
        val controller = newController(workoutRepo = workoutRepo, clock = clock)
        controller.startTracking(workoutId = "w-1", workoutSetId = "set-1")

        clock.currentMillis = 1_000_000L + 60_000L
        controller.finishTracking()

        assertEquals(60, workoutRepo.getById("w-1")!!.durationSeconds)
    }

    @Test
    fun `accepted fixes accumulate live in state, not only at finish`() = runTest {
        // The live tracking screen's map draws state.routePoints while tracking is in progress -- it must grow
        // fix-by-fix, the same way distanceMeters already does, not sit empty until finishTracking().
        val locationSource = FakeLocationSource()
        val clock = FakeClock(currentMillis = 1_000_000L)
        val controller = newController(locationSource = locationSource, clock = clock)
        controller.startTracking(workoutId = "w-1", workoutSetId = "set-1")

        assertEquals(emptyList<Pair<Double, Double>>(), controller.state.value.routePoints)

        locationSource.emit(LocationFix(14.5995, 120.9842, 5f, 0L))
        assertEquals(listOf(14.5995 to 120.9842), controller.state.value.routePoints)

        clock.currentMillis = 1_030_000L
        locationSource.emit(LocationFix(14.5985, 120.9842, 5f, 3_000L)) // ~111m south — accepted
        assertEquals(listOf(14.5995 to 120.9842, 14.5985 to 120.9842), controller.state.value.routePoints)

        locationSource.emit(LocationFix(14.59849, 120.9842, accuracyMeters = 50f, elapsedRealtimeMillis = 6_000L)) // rejected: poor accuracy
        assertEquals(2, controller.state.value.routePoints.size)
    }

    @Test
    fun `a previously-read routePoints snapshot does not grow after later fixes -- no shared-list aliasing`() = runTest {
        // Regression guard for the defensive copy in onFix (routePoints.toList()): without it,
        // every earlier ActivityTrackingState.routePoints would alias the same backing list the
        // controller keeps mutating, so a value a collector already read would silently grow too.
        val locationSource = FakeLocationSource()
        val clock = FakeClock(currentMillis = 1_000_000L)
        val controller = newController(locationSource = locationSource, clock = clock)
        controller.startTracking(workoutId = "w-1", workoutSetId = "set-1")

        locationSource.emit(LocationFix(14.5995, 120.9842, 5f, 0L))
        val snapshotAfterFirstFix = controller.state.value.routePoints
        assertEquals(1, snapshotAfterFirstFix.size)

        clock.currentMillis = 1_030_000L
        locationSource.emit(LocationFix(14.5985, 120.9842, 5f, 3_000L)) // ~111m south — accepted

        assertEquals("an already-read snapshot must not be mutated by a later fix", 1, snapshotAfterFirstFix.size)
        assertEquals(2, controller.state.value.routePoints.size)
    }

    @Test
    fun `finishTracking persists an encoded route with the accepted fix count`() = runTest {
        val trackRepo = FakeActivityTrackRepository()
        val locationSource = FakeLocationSource()
        val clock = FakeClock(currentMillis = 1_000_000L)
        val controller = newController(trackRepo = trackRepo, locationSource = locationSource, clock = clock)

        controller.startTracking(workoutId = "w-1", workoutSetId = "set-1")
        locationSource.emit(LocationFix(14.5995, 120.9842, 5f, 0L))
        clock.currentMillis = 1_030_000L
        locationSource.emit(LocationFix(14.5985, 120.9842, 5f, 3_000L))
        controller.finishTracking()

        val track = trackRepo.getByWorkoutSetId("set-1")
        assertNotNull(track)
        assertEquals(2, track!!.pointCount)
        assertNotNull(track.routePolyline)
        assertEquals(5.0, track.avgAccuracyM!!, 0.001)
    }

    @Test
    fun `finishTracking saves the time each accepted point was recorded, one per point`() = runTest {
        val trackRepo = FakeActivityTrackRepository()
        val locationSource = FakeLocationSource()
        val clock = FakeClock(currentMillis = 1_000_000L)
        val controller = newController(trackRepo = trackRepo, locationSource = locationSource, clock = clock)

        controller.startTracking(workoutId = "w-1", workoutSetId = "set-1")
        clock.currentMillis = 1_002_000L
        locationSource.emit(LocationFix(14.5995, 120.9842, 5f, 0L))
        clock.currentMillis = 1_005_000L
        locationSource.emit(LocationFix(14.5995, 120.9842, 5f, 0L)) // under 3 m: rejected, no time saved
        clock.currentMillis = 1_038_000L // ~111 m in 33 s since the last fix: a walk
        locationSource.emit(LocationFix(14.5985, 120.9842, 5f, 0L))
        controller.finishTracking()

        val track = trackRepo.getByWorkoutSetId("set-1")!!
        assertEquals(listOf(2L, 38L), PolylineEncoding.decodeDeltas(track.routeTimes!!))
        assertEquals(track.pointCount, PolylineEncoding.decodeDeltas(track.routeTimes!!).size)
    }

    @Test
    fun `a run with no accepted fix saves no route times`() = runTest {
        val trackRepo = FakeActivityTrackRepository()
        val controller = newController(trackRepo = trackRepo)
        controller.startTracking(workoutId = "w-1", workoutSetId = "set-1")
        controller.finishTracking()
        assertNull(trackRepo.getByWorkoutSetId("set-1")!!.routeTimes)
    }

    @Test
    fun `finishTracking returns null when no session is active`() = runTest {
        val controller = newController()
        assertNull(controller.finishTracking())
    }

    @Test
    fun `onFix discards a fix whose accuracy is worse than the threshold`() = runTest {
        val workoutRepo = FakeWorkoutRepository(sets = listOf(blankSet()))
        val locationSource = FakeLocationSource()
        val clock = FakeClock(currentMillis = 1_000_000L)
        val controller = newController(workoutRepo = workoutRepo, locationSource = locationSource, clock = clock)

        controller.startTracking(workoutId = "w-1", workoutSetId = "set-1")
        locationSource.emit(LocationFix(14.5995, 120.9842, 5f, 0L))
        // 30 s later (3.7 m/s, so the jump check passes): accuracy is the only reason this fix can be dropped.
        clock.currentMillis = 1_030_000L
        locationSource.emit(LocationFix(14.5985, 120.9842, accuracyMeters = 50f, elapsedRealtimeMillis = 3_000L)) // ~111m away but too imprecise
        assertEquals(0.0, controller.state.value.distanceMeters, 0.001) // the imprecise fix never counted

        // The same position with good accuracy does count, so the 50 m accuracy was what dropped it.
        locationSource.emit(LocationFix(14.5985, 120.9842, accuracyMeters = 20f, elapsedRealtimeMillis = 3_000L))
        assertEquals(111.19, controller.state.value.distanceMeters, 0.05)
    }

    @Test
    fun `onFix discards a fix within the minimum-movement threshold, avoiding stationary GPS drift`() = runTest {
        val locationSource = FakeLocationSource()
        val controller = newController(locationSource = locationSource)

        controller.startTracking(workoutId = "w-1", workoutSetId = "set-1")
        locationSource.emit(LocationFix(14.5995, 120.9842, 5f, 0L))
        locationSource.emit(LocationFix(14.59949, 120.9842, 5f, 3_000L)) // ~1.1m south — below the 3m floor

        val result = controller.finishTracking()
        assertEquals(0.0, result!!.distanceMeters, 0.001)
    }

    @Test
    fun `cancelTracking clears state without writing to the workout set`() = runTest {
        val workoutRepo = FakeWorkoutRepository(sets = listOf(blankSet()))
        val locationSource = FakeLocationSource()
        val controller = newController(workoutRepo = workoutRepo, locationSource = locationSource)

        controller.startTracking(workoutId = "w-1", workoutSetId = "set-1")
        locationSource.emit(LocationFix(14.5995, 120.9842, 5f, 0L))
        locationSource.emit(LocationFix(14.5985, 120.9842, 5f, 3_000L))

        controller.cancelTracking()

        assertFalse(controller.state.value.isTracking)
        assertNull(workoutRepo.getSetsForWorkoutExercise("we-1").single().distanceMeters)
    }

    @Test
    fun `elapsedSeconds computes from the tracking start time`() = runTest {
        val clock = FakeClock(currentMillis = 1_000_000L)
        val controller = newController(clock = clock)
        controller.startTracking(workoutId = "w-1", workoutSetId = "set-1")

        clock.currentMillis = 1_000_000L + 45_000L

        assertEquals(45, controller.elapsedSeconds())
    }

    // ---- Pause / Resume (2026-10-01) ----

    private val start = 1_000_000L
    private fun fix(lat: Double, accuracy: Float = 5f) = LocationFix(lat, 120.9842, accuracy, 0L)

    // Each step is ~111 m south of the one before, so every fix clears the 3 m floor.
    private val p0 = 14.5995
    private val p1 = 14.5985
    private val p2 = 14.5975
    private val p3 = 14.5965

    @Test
    fun `pausing stops moving time and resuming starts it again`() = runTest {
        val clock = FakeClock(start)
        val controller = newController(clock = clock)
        controller.startTracking("w-1", "set-1")

        clock.currentMillis = start + 60_000L
        controller.pause()
        clock.currentMillis = start + 300_000L // five minutes paused
        assertEquals(60, controller.elapsedSeconds())
        assertEquals(240, controller.state.value.pausedForSeconds(clock.currentMillis))

        controller.resume()
        clock.currentMillis = start + 330_000L
        assertEquals(90, controller.elapsedSeconds()) // 60 s before the pause, 30 s after it
        assertFalse(controller.state.value.isPaused)
        assertNull(controller.state.value.pausedForSeconds(clock.currentMillis))
    }

    @Test
    fun `pause and resume do nothing without a session, when already paused, or when not paused`() = runTest {
        val controller = newController()
        controller.pause()
        controller.resume()
        assertFalse(controller.state.value.isPaused)

        controller.startTracking("w-1", "set-1")
        controller.resume() // not paused
        assertFalse(controller.state.value.isPaused)
        assertEquals(emptyList<Pair<Long, Long>>(), controller.state.value.pauseRanges)

        controller.pause()
        val pausedAt = controller.state.value.pausedAtMillis
        controller.pause() // already paused: the first pause's start is kept
        assertEquals(pausedAt, controller.state.value.pausedAtMillis)
    }

    @Test
    fun `fixes while paused add no distance and no route point`() = runTest {
        val locationSource = FakeLocationSource()
        val clock = FakeClock(start)
        val controller = newController(locationSource = locationSource, clock = clock)
        controller.startTracking("w-1", "set-1")
        locationSource.emit(fix(p0))
        clock.currentMillis = start + 30_000L
        locationSource.emit(fix(p1))
        val distanceBefore = controller.state.value.distanceMeters

        clock.currentMillis = start + 40_000L
        controller.pause()
        clock.currentMillis = start + 50_000L
        locationSource.emit(fix(p2))
        locationSource.emit(fix(p3))

        assertEquals(distanceBefore, controller.state.value.distanceMeters, 0.001)
        assertEquals(2, controller.state.value.routePoints.size)
        // But the dot still follows the runner: the map shows where you are, paused or not.
        assertEquals(p3 to 120.9842, controller.state.value.currentPosition)
    }

    @Test
    fun `resuming re-anchors to the newest fix, so the paused gap adds no distance`() = runTest {
        val locationSource = FakeLocationSource()
        val clock = FakeClock(start)
        val controller = newController(locationSource = locationSource, clock = clock)
        controller.startTracking("w-1", "set-1")
        locationSource.emit(fix(p0))
        clock.currentMillis = start + 30_000L
        locationSource.emit(fix(p1))
        val distanceBefore = controller.state.value.distanceMeters // ~111 m

        clock.currentMillis = start + 40_000L
        controller.pause()
        clock.currentMillis = start + 50_000L
        locationSource.emit(fix(p3)) // walked ~222 m further while paused
        clock.currentMillis = start + 55_000L
        controller.resume()
        clock.currentMillis = start + 90_000L
        locationSource.emit(fix(p3 - 0.001)) // then ~111 m more once moving again, 40 s after the re-anchor

        // Only the 111 m after resuming is added, not the 222 m moved while paused.
        assertEquals(distanceBefore + 111.3, controller.state.value.distanceMeters, 2.0)
    }

    @Test
    fun `resuming with no recent fix lets the next fix start the new stretch without adding the gap`() = runTest {
        val locationSource = FakeLocationSource()
        val clock = FakeClock(start)
        val controller = newController(locationSource = locationSource, clock = clock)
        controller.startTracking("w-1", "set-1")
        locationSource.emit(fix(p0))
        clock.currentMillis = start + 30_000L
        locationSource.emit(fix(p1))
        val distanceBefore = controller.state.value.distanceMeters

        clock.currentMillis = start + 40_000L
        controller.pause() // no fix arrives while paused
        clock.currentMillis = start + 600_000L
        controller.resume()
        locationSource.emit(fix(p3)) // 222 m from where the pause began

        assertEquals(distanceBefore, controller.state.value.distanceMeters, 0.001)
        assertEquals(3, controller.state.value.routePoints.size) // but it is a point, the start of a new line
    }

    @Test
    fun `route times stay on the clock with pauses included, and the saved pause ranges say where they are`() = runTest {
        val trackRepo = FakeActivityTrackRepository()
        val locationSource = FakeLocationSource()
        val clock = FakeClock(start)
        val controller = newController(trackRepo = trackRepo, locationSource = locationSource, clock = clock)
        controller.startTracking("w-1", "set-1")
        clock.currentMillis = start + 10_000L
        locationSource.emit(fix(p0))
        clock.currentMillis = start + 40_000L
        locationSource.emit(fix(p1))
        controller.pause()
        clock.currentMillis = start + 340_000L // paused for 300 s
        controller.resume()
        clock.currentMillis = start + 345_000L
        locationSource.emit(fix(p2))
        clock.currentMillis = start + 400_000L
        val finished = controller.finishTracking()

        val track = trackRepo.getByWorkoutSetId("set-1")!!
        assertEquals(listOf(10L, 40L, 345L), PolylineEncoding.decodeDeltas(track.routeTimes!!))
        assertEquals(listOf(40L to 340L), PauseRanges.decode(track.pauseRanges))
        // Moving time: 400 s on the clock minus the 300 s pause.
        assertEquals(100, finished!!.durationSeconds)
    }

    @Test
    fun `a run that was never paused saves no pause ranges`() = runTest {
        val trackRepo = FakeActivityTrackRepository()
        val controller = newController(trackRepo = trackRepo)
        controller.startTracking("w-1", "set-1")
        controller.finishTracking()
        assertNull(trackRepo.getByWorkoutSetId("set-1")!!.pauseRanges)
    }

    @Test
    fun `finishing while paused closes the open pause, and the duration is moving time`() = runTest {
        val workout = WorkoutEntity(
            id = "w-1", routineId = null, title = "Running (Outdoor)", notes = null,
            status = WorkoutStatus.IN_PROGRESS, startedAt = start, endedAt = null,
            durationSeconds = 0, createdAt = start, updatedAt = start,
        )
        val workoutRepo = FakeWorkoutRepository(workouts = listOf(workout), sets = listOf(blankSet()))
        val trackRepo = FakeActivityTrackRepository()
        val clock = FakeClock(start)
        val controller = newController(workoutRepo = workoutRepo, trackRepo = trackRepo, clock = clock)
        controller.startTracking("w-1", "set-1")
        clock.currentMillis = start + 120_000L
        controller.pause()
        clock.currentMillis = start + 420_000L // still paused when Finish is tapped

        val finished = controller.finishTracking()

        assertEquals(120, finished!!.durationSeconds)
        assertEquals(120, workoutRepo.getById("w-1")!!.durationSeconds)
        assertEquals(120, workoutRepo.getSetsForWorkoutExercise("we-1").single().durationSeconds)
        assertEquals(listOf(120L to 420L), PauseRanges.decode(trackRepo.getByWorkoutSetId("set-1")!!.pauseRanges))
    }

    @Test
    fun `a pause and resume within one second is still saved, so the hop across it is still a break`() = runTest {
        val trackRepo = FakeActivityTrackRepository()
        val locationSource = FakeLocationSource()
        val clock = FakeClock(start)
        val controller = newController(trackRepo = trackRepo, locationSource = locationSource, clock = clock)
        controller.startTracking("w-1", "set-1")
        clock.currentMillis = start + 20_000L
        locationSource.emit(fix(p0))
        clock.currentMillis = start + 20_300L
        controller.pause()
        clock.currentMillis = start + 20_600L
        controller.resume()
        clock.currentMillis = start + 25_000L
        locationSource.emit(fix(p1))
        controller.finishTracking()

        assertEquals(listOf(20L to 20L), PauseRanges.decode(trackRepo.getByWorkoutSetId("set-1")!!.pauseRanges))
    }

    // ---- GPS signal ----

    @Test
    fun `the signal is finding until the first usable fix, then good`() = runTest {
        val locationSource = FakeLocationSource()
        val clock = FakeClock(start)
        val controller = newController(locationSource = locationSource, clock = clock)
        controller.startTracking("w-1", "set-1")
        assertEquals(GpsSignal.FINDING, controller.state.value.gpsSignal(clock.currentMillis))

        locationSource.emit(fix(p0, accuracy = 50f)) // too imprecise to count
        assertEquals(GpsSignal.FINDING, controller.state.value.gpsSignal(clock.currentMillis))

        locationSource.emit(fix(p0))
        assertEquals(GpsSignal.GOOD, controller.state.value.gpsSignal(clock.currentMillis))
    }

    @Test
    fun `fifteen seconds with no usable fix is weak, and it takes two good fixes in a row to clear it`() = runTest {
        val locationSource = FakeLocationSource()
        val clock = FakeClock(start)
        val controller = newController(locationSource = locationSource, clock = clock)
        controller.startTracking("w-1", "set-1")
        locationSource.emit(fix(p0))

        clock.currentMillis = start + 15_000L
        assertEquals(GpsSignal.GOOD, controller.state.value.gpsSignal(clock.currentMillis)) // exactly at the threshold
        clock.currentMillis = start + 15_001L
        assertEquals(GpsSignal.WEAK, controller.state.value.gpsSignal(clock.currentMillis))

        clock.currentMillis = start + 30_000L
        locationSource.emit(fix(p1)) // the first fix back
        assertEquals(GpsSignal.WEAK, controller.state.value.gpsSignal(clock.currentMillis))
        clock.currentMillis = start + 33_000L
        locationSource.emit(fix(p2)) // the second
        assertEquals(GpsSignal.GOOD, controller.state.value.gpsSignal(clock.currentMillis))
    }

    @Test
    fun `a second gap while recovering starts the recovery over`() = runTest {
        val locationSource = FakeLocationSource()
        val clock = FakeClock(start)
        val controller = newController(locationSource = locationSource, clock = clock)
        controller.startTracking("w-1", "set-1")
        locationSource.emit(fix(p0))
        clock.currentMillis = start + 30_000L
        locationSource.emit(fix(p1)) // first good fix after a gap
        clock.currentMillis = start + 50_000L
        locationSource.emit(fix(p2)) // 20 s later: another gap, so this is the first good fix again
        assertEquals(GpsSignal.WEAK, controller.state.value.gpsSignal(clock.currentMillis))
        clock.currentMillis = start + 52_000L
        locationSource.emit(fix(p3))
        assertEquals(GpsSignal.GOOD, controller.state.value.gpsSignal(clock.currentMillis))
    }

    @Test
    fun `standing still keeps the signal good, though those fixes add no distance`() = runTest {
        val locationSource = FakeLocationSource()
        val clock = FakeClock(start)
        val controller = newController(locationSource = locationSource, clock = clock)
        controller.startTracking("w-1", "set-1")
        locationSource.emit(fix(p0))
        for (i in 1..10) {
            clock.currentMillis = start + i * 10_000L
            locationSource.emit(fix(p0 - 0.00001)) // ~1 m: below the 3 m floor, but a good fix
        }
        assertEquals(GpsSignal.GOOD, controller.state.value.gpsSignal(clock.currentMillis))
        assertEquals(0.0, controller.state.value.distanceMeters, 0.001)
        assertEquals(1, controller.state.value.routePoints.size)
    }

    @Test
    fun `the signal stays good while paused, because the listener keeps running`() = runTest {
        val locationSource = FakeLocationSource()
        val clock = FakeClock(start)
        val controller = newController(locationSource = locationSource, clock = clock)
        controller.startTracking("w-1", "set-1")
        locationSource.emit(fix(p0))
        controller.pause()
        for (i in 1..4) {
            clock.currentMillis = start + i * 10_000L
            locationSource.emit(fix(p0))
        }
        assertEquals(GpsSignal.GOOD, controller.state.value.gpsSignal(clock.currentMillis))
    }

    // ---- live stats ----

    @Test
    fun `live stats read moving time, the pause length and no pace now while paused`() = runTest {
        val locationSource = FakeLocationSource()
        val clock = FakeClock(start)
        val controller = newController(locationSource = locationSource, clock = clock)
        controller.startTracking("w-1", "set-1")
        // A steady ~50 m every 10 s (5 m/s) for three minutes.
        for (i in 0..18) {
            clock.currentMillis = start + i * 10_000L
            locationSource.emit(fix(p0 - i * 0.00045))
        }
        clock.currentMillis = start + 190_000L
        val moving = controller.state.value.liveStats(DistanceUnit.KM, clock.currentMillis)
        assertEquals(190, moving.elapsedSeconds)
        assertNull(moving.pausedForSeconds)
        assertEquals(GpsSignal.GOOD, moving.gps)
        // 5 m/s is 3:20/km, but the last fix was 10 s ago and the distance is held flat since it, so the
        // minute reads 250 m: 4:00/km. That is the documented way "now" is measured.
        assertEquals(240.0, moving.paceNowSecondsPerUnit!!, 5.0)

        controller.pause()
        clock.currentMillis = start + 250_000L
        val paused = controller.state.value.liveStats(DistanceUnit.KM, clock.currentMillis)
        assertEquals(190, paused.elapsedSeconds)
        assertEquals(60, paused.pausedForSeconds)
        assertNull(paused.paceNowSecondsPerUnit)
    }

    @Test
    fun `the live stats flow reports in the unit it is asked for, and does nothing before a run starts`() = runTest {
        val locationSource = FakeLocationSource()
        val clock = FakeClock(start)
        val controller = newController(locationSource = locationSource, clock = clock)
        controller.startTracking("w-1", "set-1")
        for (i in 0..18) {
            clock.currentMillis = start + i * 10_000L
            locationSource.emit(fix(p0 - i * 0.00045))
        }
        clock.currentMillis = start + 190_000L

        val km = controller.liveStats(DistanceUnit.KM).first()
        val miles = controller.liveStats(DistanceUnit.MILES).first()

        assertEquals(190, km.elapsedSeconds)
        assertEquals(190, miles.elapsedSeconds)
        // The same minute of running, per mile instead of per km.
        assertEquals(km.paceNowSecondsPerUnit!! * 1.609344, miles.paceNowSecondsPerUnit!!, 1.0)

        controller.cancelTracking()
        // Nothing tracking: the flow emits nothing, rather than a row of zeros.
        assertNull(withTimeoutOrNull(2_500) { controller.liveStats(DistanceUnit.KM).first() })
    }

    @Test
    fun `pace now reads null whenever the signal is not good`() = runTest {
        val locationSource = FakeLocationSource()
        val clock = FakeClock(start)
        val controller = newController(locationSource = locationSource, clock = clock)
        controller.startTracking("w-1", "set-1")
        for (i in 0..18) {
            clock.currentMillis = start + i * 10_000L
            locationSource.emit(fix(p0 - i * 0.00045))
        }
        clock.currentMillis = start + 210_000L // the last fix was 30 s ago: weak
        val stats = controller.state.value.liveStats(DistanceUnit.KM, clock.currentMillis)
        assertEquals(GpsSignal.WEAK, stats.gps)
        assertNull(stats.paceNowSecondsPerUnit)
    }

    @Test
    fun `startTracking resets pause, signal and route lists from a previous run`() = runTest {
        val locationSource = FakeLocationSource()
        val clock = FakeClock(start)
        val controller = newController(locationSource = locationSource, clock = clock)
        controller.startTracking("w-1", "set-1")
        locationSource.emit(fix(p0))
        controller.pause()
        controller.cancelTracking()

        controller.startTracking("w-2", "set-1")
        val state = controller.state.value
        assertFalse(state.isPaused)
        assertNull(state.lastGoodFixMillis)
        assertNull(state.currentPosition)
        assertEquals(emptyList<Long>(), state.routeTimes)
        assertEquals(emptyList<Double>(), state.routeDistances)
        assertEquals(emptyList<Pair<Long, Long>>(), state.pauseRanges)
    }
}
