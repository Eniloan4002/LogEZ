package com.enil.logez.feature.activity

import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.GpsActivity
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.fakes.FakeActiveSessionRepository
import com.enil.logez.fakes.FakeActivityTrackRepository
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeElapsedRealtimeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeHealthMetricsSource
import com.enil.logez.fakes.FakeLocationSource
import com.enil.logez.fakes.FakeRoutineRepository
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeWorkoutRepository
import com.enil.logez.feature.workout.SessionDiscarder
import com.enil.logez.feature.workout.WorkoutStarter
import com.enil.logez.feature.workout.session.WorkoutSessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M21 redesign (2026-09-11): Finish now goes straight to the Save Workout screen instead of
 * through the strength Logger, which used to be the only thing that called
 * `sessionController.endSession()` (`WorkoutLoggerViewModel.prepareForFinish()`) once it was
 * reached. Without ending it here instead, the mini-bar's "in-progress, tap to resume" state would
 * keep pointing at a workoutId that's about to become COMPLETED, since nothing else on the new
 * direct path ever clears it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityTrackingViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun blankSet() = WorkoutSetEntity(
        id = "set-1", workoutExerciseId = "we-1", orderIndex = 0, setType = SetType.NORMAL,
        weightKg = null, reps = null, durationSeconds = null, distanceMeters = null,
        rpe = null, customMetric = null, isCompleted = false, completedAt = null,
    )

    private class Fixture(
        val viewModel: ActivityTrackingViewModel,
        val trackingController: ActivityTrackingController,
        val sessionController: WorkoutSessionController,
    )

    private fun fixture(
        clock: FakeClock = FakeClock(),
        exercises: List<WorkoutExerciseEntity> = emptyList(),
        library: List<Exercise> = emptyList(),
    ): Fixture {
        val workoutRepo = FakeWorkoutRepository(exercises = exercises, sets = listOf(blankSet()))
        val trackingController = ActivityTrackingController(
            workoutRepo, FakeActivityTrackRepository(), FakeLocationSource(), clock, CoroutineScope(UnconfinedTestDispatcher()),
        )
        val sessionController = WorkoutSessionController(
            FakeActiveSessionRepository(), clock, FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()),
        )
        val workoutStarter = WorkoutStarter(workoutRepo, FakeRoutineRepository(), clock)
        val viewModel = ActivityTrackingViewModel(
            trackingController, SessionDiscarder(workoutStarter, sessionController, trackingController),
            sessionController, FakeSettingsRepository(), workoutRepo, FakeExerciseRepository(library),
            FakeHealthMetricsSource(), clock, com.enil.logez.core.common.AppLogger.NoOp,
        )
        return Fixture(viewModel, trackingController, sessionController)
    }

    @Test
    fun `pause stops the tracking clock and the strength session clock together, and resume restarts both`() = runTest {
        val clock = FakeClock(1_000_000L)
        val f = fixture(clock)
        f.trackingController.startTracking(workoutId = "w-1", workoutSetId = "set-1")
        f.sessionController.startSession("w-1")

        clock.currentMillis += 60_000L
        f.viewModel.pause()
        clock.currentMillis += 300_000L
        assertTrue(f.trackingController.state.value.isPaused)
        assertTrue(f.sessionController.state.value.isPaused)
        assertEquals(60, f.trackingController.elapsedSeconds())
        assertEquals(60L, f.sessionController.elapsedSeconds())

        f.viewModel.resume()
        clock.currentMillis += 30_000L
        assertFalse(f.trackingController.state.value.isPaused)
        assertFalse(f.sessionController.state.value.isPaused)
        assertEquals(90, f.trackingController.elapsedSeconds())
        assertEquals(90L, f.sessionController.elapsedSeconds())
    }

    @Test
    fun `finish while paused still saves, and ends the session`() = runTest {
        val clock = FakeClock(1_000_000L)
        val f = fixture(clock)
        f.trackingController.startTracking(workoutId = "w-1", workoutSetId = "set-1")
        f.sessionController.startSession("w-1")
        clock.currentMillis += 60_000L
        f.viewModel.pause()
        clock.currentMillis += 300_000L

        val result = f.viewModel.finish()

        assertEquals(60, result!!.durationSeconds)
        assertFalse(f.sessionController.state.value.hasActiveSession)
    }

    @Test
    fun `activityFor tells a run from a walk from the seed exercise, and falls back to the name`() = runTest {
        fun block(exerciseId: String) = WorkoutExerciseEntity(
            id = "we-1", workoutId = "w-1", exerciseId = exerciseId, orderIndex = 0,
            supersetGroup = null, restTimerSeconds = null, notes = null,
        )
        assertEquals(GpsActivity.RUN, fixture(exercises = listOf(block(GpsActivity.RUNNING_OUTDOOR_EXERCISE_ID))).viewModel.activityFor("w-1"))
        assertEquals(GpsActivity.WALK, fixture(exercises = listOf(block(GpsActivity.WALKING_OUTDOOR_EXERCISE_ID))).viewModel.activityFor("w-1"))
        // A custom exercise the user named "Trail run" is still a run.
        val trail = Exercise(
            id = "custom-1", name = "Trail run", exerciseType = ExerciseType.DISTANCE_DURATION, primaryMuscleGroup = MuscleGroup.CARDIO,
            secondaryMuscleGroups = emptyList(), equipment = Equipment.NONE, instructions = "", mediaPath = null,
            isCustom = true, isBodyweightVolumeEligible = false, isDeleted = false, createdAt = 0L, updatedAt = 0L,
        )
        assertEquals(GpsActivity.RUN, fixture(exercises = listOf(block("custom-1")), library = listOf(trail)).viewModel.activityFor("w-1"))
        assertEquals(GpsActivity.OTHER, fixture().viewModel.activityFor("w-1"))
    }

    @Test
    fun `finish ends the shared workout session, mirroring cancel`() = runTest {
        val f = fixture()
        f.trackingController.startTracking(workoutId = "w-1", workoutSetId = "set-1")
        f.sessionController.startSession("w-1")
        assertTrue(f.sessionController.state.value.hasActiveSession)

        val result = f.viewModel.finish()

        assertNotNull(result)
        assertFalse(f.sessionController.state.value.hasActiveSession)
    }

    @Test
    fun `finish returns null and still ends the session when no tracking was active`() = runTest {
        val f = fixture()
        f.sessionController.startSession("w-1") // e.g. a stale session from a prior workout

        val result = f.viewModel.finish()

        assertNull(result)
        assertFalse(f.sessionController.state.value.hasActiveSession)
    }

    // ---- the screen's actions run in the ViewModel, not the composition ----

    @Test
    fun `onPause and onResume stop and restart both clocks, in the order tapped`() = runTest {
        val clock = FakeClock(1_000_000L)
        val f = fixture(clock)
        f.trackingController.startTracking(workoutId = "w-1", workoutSetId = "set-1")
        f.sessionController.startSession("w-1")

        clock.currentMillis += 60_000L
        f.viewModel.onPause()
        clock.currentMillis += 300_000L
        f.viewModel.onResume()
        clock.currentMillis += 30_000L

        assertFalse(f.trackingController.state.value.isPaused)
        assertFalse(f.sessionController.state.value.isPaused)
        assertEquals(90, f.trackingController.elapsedSeconds())
        assertEquals(90L, f.sessionController.elapsedSeconds())
        assertEquals(listOf(60L to 360L), f.trackingController.state.value.pauseRanges)
    }

    @Test
    fun `onFinish saves the run and reports it once, held until a screen collects`() = runTest {
        val clock = FakeClock(1_000_000L)
        val f = fixture(clock)
        f.trackingController.startTracking(workoutId = "w-1", workoutSetId = "set-1")
        f.sessionController.startSession("w-1")
        clock.currentMillis += 60_000L

        f.viewModel.onFinish()
        f.viewModel.onFinish() // a second tap, or a recreated screen tapping again

        // Nobody was collecting when it landed (a recreation), and it is still delivered.
        assertEquals(TrackingEnd.Finished("w-1"), f.viewModel.ended.first())
        assertTrue(f.viewModel.ending.value)
        assertFalse(f.sessionController.state.value.hasActiveSession)
        assertNull(withTimeoutOrNull(1_000) { f.viewModel.ended.first() })
    }

    @Test
    fun `onDiscard throws the run away and reports it cancelled`() = runTest {
        val f = fixture()
        f.trackingController.startTracking(workoutId = "w-1", workoutSetId = "set-1")
        f.sessionController.startSession("w-1")

        f.viewModel.onDiscard()
        f.viewModel.onFinish() // ignored: the run is already ending

        assertEquals(TrackingEnd.Cancelled, f.viewModel.ended.first())
        assertFalse(f.trackingController.state.value.isTracking)
        assertNull(withTimeoutOrNull(1_000) { f.viewModel.ended.first() })
    }

    @Test
    fun `onFinish with nothing tracked reports cancelled, so the screen still leaves`() = runTest {
        val f = fixture()
        f.viewModel.onFinish()
        assertEquals(TrackingEnd.Cancelled, f.viewModel.ended.first())
    }
}
