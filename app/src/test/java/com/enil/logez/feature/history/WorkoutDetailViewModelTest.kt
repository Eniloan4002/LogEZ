package com.enil.logez.feature.history

import androidx.lifecycle.SavedStateHandle
import com.enil.logez.core.data.entity.ActivityTrackEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.fakes.FakeActiveSessionRepository
import com.enil.logez.fakes.FakeActivityTrackRepository
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeElapsedRealtimeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeMeasurementRepository
import com.enil.logez.fakes.FakePersonalRecordsRepository
import com.enil.logez.fakes.FakeRoutineRepository
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeTransactionRunner
import com.enil.logez.fakes.FakeWorkoutRepository
import com.enil.logez.feature.workout.WorkoutStarter
import com.enil.logez.feature.workout.finish.PersonalRecordsUpdater
import com.enil.logez.feature.workout.session.WorkoutSessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * First test coverage for this ViewModel (adversarial review, 2026-09-10 flagged its total
 * absence as a high-severity gap once hasVolume/hasDistance/hasRoute logic landed here with
 * nothing exercising it). Scoped to `reload()`'s stat-gating and route-presence logic -- the
 * screen's copy/save/delete actions already have their own dedicated collaborator classes and
 * aren't re-tested here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutDetailViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a GPS-tracked walk with no weight logged hides Volume and reports its distance`() = runTest {
        val vm = viewModel(
            workoutRepo = FakeWorkoutRepository(
                workouts = listOf(workout("w1")),
                exercises = listOf(workoutExercise("we1", "w1")),
                sets = listOf(aSet("s1", "we1", weightKg = null, reps = null, distanceMeters = 2_000.0)),
            ),
        )

        val state = vm.uiState.value
        assertFalse(state.hasVolume)
        assertTrue(state.hasDistance)
        assertEquals(2_000.0, state.distanceMeters, 1e-9)
    }

    @Test
    fun `a strength workout with logged weight shows Volume and has no distance`() = runTest {
        val vm = viewModel(
            workoutRepo = FakeWorkoutRepository(
                workouts = listOf(workout("w1")),
                exercises = listOf(workoutExercise("we1", "w1")),
                sets = listOf(aSet("s1", "we1", weightKg = 100.0, reps = 5)),
            ),
        )

        val state = vm.uiState.value
        assertTrue(state.hasVolume)
        assertFalse(state.hasDistance)
    }

    @Test
    fun `hasRoute is true when the workout's set has a saved GPS track`() = runTest {
        val vm = viewModel(
            workoutRepo = FakeWorkoutRepository(
                workouts = listOf(workout("w1")),
                exercises = listOf(workoutExercise("we1", "w1")),
                sets = listOf(aSet("s1", "we1", weightKg = null, reps = null, distanceMeters = 500.0)),
            ),
            trackRepo = FakeActivityTrackRepository(listOf(track("s1"))),
        )

        assertTrue(vm.uiState.value.hasRoute)
    }

    @Test
    fun `hasRoute stays true even when the tracked set is excluded from stats as a warm-up`() = runTest {
        // Regression (adversarial review, 2026-09-10): hasRoute deliberately scans the unfiltered
        // set list, not the isIncluded-filtered one hasVolume/hasDistance use -- a recorded GPS
        // track is a fact about what happened, not a stats-inclusion choice. Re-tagging the
        // tracked set as a warm-up (with warm-ups excluded from stats) must hide the Distance
        // stat, but must NOT also hide the Route card -- the two are intentionally independent.
        val vm = viewModel(
            workoutRepo = FakeWorkoutRepository(
                workouts = listOf(workout("w1")),
                exercises = listOf(workoutExercise("we1", "w1")),
                sets = listOf(
                    aSet("s1", "we1", weightKg = null, reps = null, distanceMeters = 500.0, setType = SetType.WARMUP),
                ),
            ),
            trackRepo = FakeActivityTrackRepository(listOf(track("s1"))),
            settingsRepo = FakeSettingsRepository(UserSettings(includeWarmupsInStats = false)),
        )

        val state = vm.uiState.value
        assertFalse("the warm-up set is excluded from stats", state.hasDistance)
        assertTrue("the route it recorded must still show", state.hasRoute)
    }

    @Test
    fun `hasRoute is false for an ordinary strength workout with no GPS track`() = runTest {
        val vm = viewModel(
            workoutRepo = FakeWorkoutRepository(
                workouts = listOf(workout("w1")),
                exercises = listOf(workoutExercise("we1", "w1")),
                sets = listOf(aSet("s1", "we1", weightKg = 100.0, reps = 5)),
            ),
        )

        assertFalse(vm.uiState.value.hasRoute)
    }

    @Test
    fun `a workout that no longer exists reports isMissing instead of an error`() = runTest {
        val vm = viewModel(workoutRepo = FakeWorkoutRepository())

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isMissing)
    }

    // --- fixture ---

    private fun viewModel(
        workoutRepo: FakeWorkoutRepository,
        trackRepo: FakeActivityTrackRepository = FakeActivityTrackRepository(),
        settingsRepo: FakeSettingsRepository = FakeSettingsRepository(),
    ): WorkoutDetailViewModel {
        val exerciseRepo = FakeExerciseRepository(listOf())
        val personalRecordsRepo = FakePersonalRecordsRepository()
        val personalRecordsUpdater = PersonalRecordsUpdater(
            workoutRepo, exerciseRepo, personalRecordsRepo, FakeMeasurementRepository(), settingsRepo,
        )
        return WorkoutDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf(WorkoutDetailViewModel.WORKOUT_ID_ARG to "w1")),
            workoutRepository = workoutRepo,
            exerciseRepository = exerciseRepo,
            routineRepository = FakeRoutineRepository(),
            personalRecordsRepository = personalRecordsRepo,
            settingsRepository = settingsRepo,
            workoutDeleter = WorkoutDeleter(workoutRepo, personalRecordsUpdater, FakeTransactionRunner()),
            workoutToRoutineConverter = WorkoutToRoutineConverter(workoutRepo, FakeRoutineRepository(), FakeClock()),
            workoutStarter = WorkoutStarter(workoutRepo, FakeRoutineRepository(), FakeClock()),
            sessionController = WorkoutSessionController(
                FakeActiveSessionRepository(), FakeClock(), FakeElapsedRealtimeClock(), CoroutineScope(dispatcher),
            ),
            activityTrackRepository = trackRepo,
        )
    }

    private fun workout(id: String) = WorkoutEntity(
        id = id, routineId = null, title = "Session $id", notes = null, status = WorkoutStatus.COMPLETED,
        startedAt = 1_000L, endedAt = 2_000L, durationSeconds = 60, createdAt = 1_000L, updatedAt = 1_000L,
    )

    private fun workoutExercise(id: String, workoutId: String) = WorkoutExerciseEntity(
        id = id, workoutId = workoutId, exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null,
    )

    private fun aSet(
        id: String,
        workoutExerciseId: String,
        weightKg: Double?,
        reps: Int?,
        distanceMeters: Double? = null,
        setType: SetType = SetType.NORMAL,
    ) = WorkoutSetEntity(
        id = id, workoutExerciseId = workoutExerciseId, orderIndex = 0, setType = setType,
        weightKg = weightKg, reps = reps, durationSeconds = null, distanceMeters = distanceMeters, rpe = null,
        customMetric = null, isCompleted = true, completedAt = 1L,
    )

    private fun track(workoutSetId: String) = ActivityTrackEntity(
        id = "track-$workoutSetId", workoutSetId = workoutSetId, routePolyline = "abc", pointCount = 1, avgAccuracyM = 5.0,
    )
}
