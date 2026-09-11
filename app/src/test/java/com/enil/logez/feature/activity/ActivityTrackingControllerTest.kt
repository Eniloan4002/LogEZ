package com.enil.logez.feature.activity

import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.fakes.FakeActivityTrackRepository
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeLocationSource
import com.enil.logez.fakes.FakeWorkoutRepository
import com.enil.logez.feature.activity.location.LocationFix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
        val controller = newController(locationSource = locationSource)
        controller.startTracking(workoutId = "w-1", workoutSetId = "set-1")

        assertEquals(emptyList<Pair<Double, Double>>(), controller.state.value.routePoints)

        locationSource.emit(LocationFix(14.5995, 120.9842, 5f, 0L))
        assertEquals(listOf(14.5995 to 120.9842), controller.state.value.routePoints)

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
        val controller = newController(locationSource = locationSource)
        controller.startTracking(workoutId = "w-1", workoutSetId = "set-1")

        locationSource.emit(LocationFix(14.5995, 120.9842, 5f, 0L))
        val snapshotAfterFirstFix = controller.state.value.routePoints
        assertEquals(1, snapshotAfterFirstFix.size)

        locationSource.emit(LocationFix(14.5985, 120.9842, 5f, 3_000L)) // ~111m south — accepted

        assertEquals("an already-read snapshot must not be mutated by a later fix", 1, snapshotAfterFirstFix.size)
        assertEquals(2, controller.state.value.routePoints.size)
    }

    @Test
    fun `finishTracking persists an encoded route with the accepted fix count`() = runTest {
        val trackRepo = FakeActivityTrackRepository()
        val locationSource = FakeLocationSource()
        val controller = newController(trackRepo = trackRepo, locationSource = locationSource)

        controller.startTracking(workoutId = "w-1", workoutSetId = "set-1")
        locationSource.emit(LocationFix(14.5995, 120.9842, 5f, 0L))
        locationSource.emit(LocationFix(14.5985, 120.9842, 5f, 3_000L))
        controller.finishTracking()

        val track = trackRepo.getByWorkoutSetId("set-1")
        assertNotNull(track)
        assertEquals(2, track!!.pointCount)
        assertNotNull(track.routePolyline)
        assertEquals(5.0, track.avgAccuracyM!!, 0.001)
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
        val controller = newController(workoutRepo = workoutRepo, locationSource = locationSource)

        controller.startTracking(workoutId = "w-1", workoutSetId = "set-1")
        locationSource.emit(LocationFix(14.5995, 120.9842, 5f, 0L))
        locationSource.emit(LocationFix(14.5985, 120.9842, accuracyMeters = 50f, elapsedRealtimeMillis = 3_000L)) // ~111m away but too imprecise

        val result = controller.finishTracking()
        assertEquals(0.0, result!!.distanceMeters, 0.001) // the imprecise fix never counted
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
}
