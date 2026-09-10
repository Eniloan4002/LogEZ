package com.enil.logez.feature.workout.finish

import androidx.lifecycle.SavedStateHandle
import com.enil.logez.core.common.PolylineEncoding
import com.enil.logez.core.data.entity.ActivityTrackEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.model.WorkoutStructure
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.fakes.FakeActivityTrackRepository
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakePersonalRecordsRepository
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeWorkoutRepository
import kotlinx.coroutines.Dispatchers
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
 * Post-save summary stats — the Reps total (same `isIncluded` list as the Sets stat) and the M11
 * circuit fields (structure passthrough plus History's post-purge rounds derivation).
 */
class WorkoutSummaryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `totalReps sums included sets only — warm-ups excluded, like the Sets stat`() = runTest {
        val vm = viewModel(
            FakeWorkoutRepository(
                workouts = listOf(workout("w1")),
                exercises = listOf(workoutExercise("we1", "w1")),
                sets = listOf(
                    aSet("s1", "we1", 0, reps = 8),
                    aSet("s2", "we1", 1, reps = 10),
                    aSet("s3", "we1", 2, reps = 12),
                    aSet("s4", "we1", 3, reps = 20, setType = SetType.WARMUP), // excluded by default
                ),
            ),
        )

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(30, state.totalReps) // 8 + 10 + 12, hard-coded — never recomputed
        assertEquals(3, state.completedSetCount)
    }

    @Test
    fun `a GPS-tracked walk with no weight or reps logged hides Volume and Reps and reports distance`() = runTest {
        val vm = viewModel(
            FakeWorkoutRepository(
                workouts = listOf(workout("w1")),
                exercises = listOf(workoutExercise("we1", "w1")),
                sets = listOf(aSet("s1", "we1", 0, reps = null, weightKg = null, distanceMeters = 2_000.0)),
            ),
        )

        val state = vm.uiState.value
        assertFalse(state.hasVolume)
        assertFalse(state.hasReps)
        assertEquals(2_000.0, state.totalDistanceMeters, 1e-9)
        // Distance-tracked is asserted via totalDistanceMeters above; hasDistance mirrors it.
        assertEquals(true, state.hasDistance)
    }

    @Test
    fun `a strength set with logged weight and reps shows Volume and Reps, no distance`() = runTest {
        val vm = viewModel(
            FakeWorkoutRepository(
                workouts = listOf(workout("w1")),
                exercises = listOf(workoutExercise("we1", "w1")),
                sets = listOf(aSet("s1", "we1", 0, reps = 8)), // aSet defaults weightKg = 50.0
            ),
        )

        val state = vm.uiState.value
        assertEquals(true, state.hasVolume)
        assertEquals(true, state.hasReps)
        assertFalse(state.hasDistance)
    }

    @Test
    fun `a GPS-tracked workout's saved route decodes onto the summary`() = runTest {
        val trackRepo = FakeActivityTrackRepository(
            listOf(
                ActivityTrackEntity(
                    id = "track-1",
                    workoutSetId = "s1",
                    routePolyline = PolylineEncoding.encode(listOf(14.5995 to 120.9842, 14.6 to 120.99)),
                    pointCount = 2,
                    avgAccuracyM = 8.0,
                ),
            ),
        )
        val vm = WorkoutSummaryViewModel(
            savedStateHandle = SavedStateHandle(mapOf(WorkoutSummaryViewModel.WORKOUT_ID_ARG to "w1")),
            workoutRepository = FakeWorkoutRepository(
                workouts = listOf(workout("w1")),
                exercises = listOf(workoutExercise("we1", "w1")),
                sets = listOf(aSet("s1", "we1", 0, reps = null, weightKg = null, distanceMeters = 500.0)),
            ),
            exerciseRepository = FakeExerciseRepository(listOf(exercise("ex-1"))),
            personalRecordsRepository = FakePersonalRecordsRepository(),
            settingsRepository = FakeSettingsRepository(),
            activityTrackRepository = trackRepo,
            clock = FakeClock(),
        )

        val points = vm.uiState.value.routePoints
        assertEquals(2, points.size)
        assertEquals(14.5995, points[0].first, 1e-4)
        assertEquals(120.9842, points[0].second, 1e-4)
    }

    @Test
    fun `a workout with no GPS track has an empty route`() = runTest {
        val vm = viewModel(
            FakeWorkoutRepository(
                workouts = listOf(workout("w1")),
                exercises = listOf(workoutExercise("we1", "w1")),
                sets = listOf(aSet("s1", "we1", 0, reps = 8)),
            ),
        )

        assertTrue(vm.uiState.value.routePoints.isEmpty())
    }

    @Test
    fun `rep-less sets contribute a real zero, never a fabricated count`() = runTest {
        val vm = viewModel(
            FakeWorkoutRepository(
                workouts = listOf(workout("w1")),
                exercises = listOf(workoutExercise("we1", "w1")),
                sets = listOf(
                    aSet("s1", "we1", 0, reps = null), // cardio-style set: no reps logged
                    aSet("s2", "we1", 1, reps = 5),
                ),
            ),
        )

        assertEquals(5, vm.uiState.value.totalReps)
        assertEquals(2, vm.uiState.value.completedSetCount)
    }

    @Test
    fun `a circuit with a purged middle round reports the surviving round count, like History`() = runTest {
        // Round 2 was purged (its sets were never completed): each block keeps rows at
        // orderIndex 0 and 2 only, so the post-purge MAX-across-blocks round count is 2.
        val vm = viewModel(
            FakeWorkoutRepository(
                workouts = listOf(workout("w1", structure = WorkoutStructure.CIRCUIT)),
                exercises = listOf(
                    workoutExercise("we-squat", "w1", orderIndex = 0),
                    workoutExercise("we-curl", "w1", orderIndex = 1),
                ),
                sets = listOf(
                    aSet("s1", "we-squat", 0, reps = 5),
                    aSet("s2", "we-squat", 2, reps = 5),
                    aSet("s3", "we-curl", 0, reps = 10),
                    aSet("s4", "we-curl", 2, reps = 10),
                ),
            ),
        )

        val state = vm.uiState.value
        assertEquals(WorkoutStructure.CIRCUIT, state.structure)
        assertEquals(2, state.rounds)
    }

    @Test
    fun `a regular workout passes its structure through unchanged`() = runTest {
        val vm = viewModel(
            FakeWorkoutRepository(
                workouts = listOf(workout("w1")),
                exercises = listOf(workoutExercise("we1", "w1")),
                sets = listOf(aSet("s1", "we1", 0, reps = 8), aSet("s2", "we1", 1, reps = 8)),
            ),
        )

        val state = vm.uiState.value
        assertEquals(WorkoutStructure.REGULAR, state.structure)
        assertEquals(2, state.rounds) // derived either way; only CIRCUIT summaries render it
    }

    // --- fixture ---

    private fun viewModel(workoutRepo: FakeWorkoutRepository) = WorkoutSummaryViewModel(
        savedStateHandle = SavedStateHandle(mapOf(WorkoutSummaryViewModel.WORKOUT_ID_ARG to "w1")),
        workoutRepository = workoutRepo,
        exerciseRepository = FakeExerciseRepository(listOf(exercise("ex-1"))),
        personalRecordsRepository = FakePersonalRecordsRepository(),
        settingsRepository = FakeSettingsRepository(),
        activityTrackRepository = FakeActivityTrackRepository(),
        clock = FakeClock(),
    )

    private fun workout(id: String, structure: WorkoutStructure = WorkoutStructure.REGULAR) = WorkoutEntity(
        id = id, routineId = null, title = "Session $id", notes = null, status = WorkoutStatus.COMPLETED,
        startedAt = 1_000L, endedAt = 2_000L, durationSeconds = 1, createdAt = 1_000L, updatedAt = 1_000L,
        structure = structure,
    )

    private fun workoutExercise(id: String, workoutId: String, orderIndex: Int = 0) = WorkoutExerciseEntity(
        id = id, workoutId = workoutId, exerciseId = "ex-1", orderIndex = orderIndex, supersetGroup = null, restTimerSeconds = null, notes = null,
    )

    private fun aSet(
        id: String,
        workoutExerciseId: String,
        orderIndex: Int,
        reps: Int?,
        setType: SetType = SetType.NORMAL,
        weightKg: Double? = 50.0,
        distanceMeters: Double? = null,
    ) = WorkoutSetEntity(
        id = id, workoutExerciseId = workoutExerciseId, orderIndex = orderIndex, setType = setType,
        weightKg = weightKg, reps = reps, durationSeconds = null, distanceMeters = distanceMeters, rpe = null,
        customMetric = null, isCompleted = true, completedAt = 1L,
    )

    private fun exercise(id: String) = Exercise(
        id = id, name = "Ex $id", exerciseType = ExerciseType.WEIGHT_REPS, primaryMuscleGroup = MuscleGroup.CHEST,
        secondaryMuscleGroups = emptyList(), equipment = Equipment.BARBELL, instructions = "", mediaPath = null,
        isCustom = false, isBodyweightVolumeEligible = false, isDeleted = false, createdAt = 0, updatedAt = 0,
    )
}
