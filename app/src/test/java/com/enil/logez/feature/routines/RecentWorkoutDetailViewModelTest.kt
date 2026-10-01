package com.enil.logez.feature.routines

import androidx.lifecycle.SavedStateHandle
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
import com.enil.logez.fakes.FakeActiveSessionRepository
import com.enil.logez.fakes.FakeActivityTrackRepository
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeElapsedRealtimeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeLocationSource
import com.enil.logez.fakes.FakeRoutineRepository
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeWorkoutRepository
import com.enil.logez.feature.activity.ActivityTrackingController
import com.enil.logez.feature.history.WorkoutToRoutineConverter
import com.enil.logez.feature.workout.InProgressWorkoutResolver
import com.enil.logez.feature.workout.SessionDiscarder
import com.enil.logez.feature.workout.StartResult
import com.enil.logez.feature.workout.WorkoutStarter
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** R-1 "Recent workout detail": what the screen is built from, and that Start and Save as Routine reuse the existing paths. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecentWorkoutDetailViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val workoutRepo = FakeWorkoutRepository(
        workouts = listOf(
            workout("w1", notes = "Left elbow felt tight."),
            workout("circuit", structure = WorkoutStructure.CIRCUIT),
            workout("empty"),
            workout("live", status = WorkoutStatus.IN_PROGRESS),
            workout("twice"),
        ),
        exercises = listOf(
            // Listed out of order on purpose: the screen must sort by orderIndex.
            workoutExercise("we2", "w1", "ex-row", orderIndex = 1, supersetGroup = 0, notes = "Slow negatives."),
            workoutExercise("we1", "w1", "ex-bench", orderIndex = 0, supersetGroup = 0),
            workoutExercise("we3", "w1", "ex-gone", orderIndex = 2),
            workoutExercise("wc1", "circuit", "ex-bench", orderIndex = 0),
            workoutExercise("t1", "twice", "ex-bench", orderIndex = 0),
            workoutExercise("t2", "twice", "ex-row", orderIndex = 1),
            workoutExercise("t3", "twice", "ex-bench", orderIndex = 2),
        ),
        sets = listOf(
            aSet("s1b", "we1", orderIndex = 1, weightKg = 80.0, reps = 8, rpe = 9.0, completed = false),
            aSet("s1a", "we1", orderIndex = 0, weightKg = 40.0, reps = 10, setType = SetType.WARMUP),
            aSet("s2", "we2", orderIndex = 0, weightKg = 50.0, reps = 12),
            aSet("s3", "we3", orderIndex = 0, weightKg = 15.0, reps = 15),
            aSet("sc1", "wc1", orderIndex = 0, weightKg = null, reps = 15),
        ),
    )
    private val exerciseRepo = FakeExerciseRepository(
        listOf(exercise("ex-bench", "Bench Press (Barbell)"), exercise("ex-row", "Seated Cable Row (Machine)"), exercise("ex-gone", "Face Pull (Cable)", isDeleted = true)),
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads the blocks in order with each set's values, no effort and no record, and a set that was never ticked still shows its numbers`() = runTest {
        val vm = viewModel("w1")
        vm.refresh()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Session w1", state.summary?.title)
        assertEquals(3, state.summary?.exerciseCount)
        assertEquals("Left elbow felt tight.", state.notes)
        assertEquals(listOf("we1", "we2", "we3"), state.blocks.map { it.workoutExercise.id })
        val bench = state.blocks[0]
        assertEquals(listOf("s1a", "s1b"), bench.sets.map { it.setId })
        assertEquals(40.0, bench.sets[0].weightKg!!, 1e-9)
        assertEquals(SetType.WARMUP, bench.sets[0].setType)
        // Start blanks effort, so the rows never show it, and a never-ticked row still shows what Start would copy.
        assertTrue(bench.sets.all { it.rpe == null && it.pr == null && it.isCompleted })
        assertEquals("Bench Press (Barbell)", bench.exercise?.name)
        assertEquals("Slow negatives.", state.blocks[1].workoutExercise.notes)
        assertEquals(0, state.blocks[1].workoutExercise.supersetGroup)
    }

    @Test
    fun `an exercise deleted from the library still loads, flagged deleted`() = runTest {
        val vm = viewModel("w1")
        vm.refresh()

        val gone = vm.uiState.value.blocks.last()
        assertTrue(gone.exercise!!.isDeleted)
        assertEquals(15, gone.sets.single().reps)
    }

    @Test
    fun `a circuit workout reports itself as a circuit`() = runTest {
        val vm = viewModel("circuit")
        vm.refresh()

        assertTrue(vm.uiState.value.isCircuit)
        assertFalse(viewModel("w1").also { it.refresh() }.uiState.value.isCircuit)
    }

    @Test
    fun `a workout with no exercises loads with no blocks`() = runTest {
        val vm = viewModel("empty")
        vm.refresh()

        assertTrue(vm.uiState.value.blocks.isEmpty())
        assertNull(vm.saveAsRoutine())
    }

    @Test
    fun `an exercise that appears in two blocks is looked up once`() = runTest {
        // The view model reads once on creation.
        val vm = viewModel("twice")

        assertEquals(listOf("t1", "t2", "t3"), vm.uiState.value.blocks.map { it.workoutExercise.id })
        assertEquals("Bench Press (Barbell)", vm.uiState.value.blocks[2].exercise?.name)
        // Bench appears twice and row once: two lookups, not three.
        assertEquals(2, exerciseRepo.getByIdCallCount)
    }

    @Test
    fun `a workout that is missing or still in progress is reported missing`() = runTest {
        val gone = viewModel("nope")
        gone.refresh()
        assertTrue(gone.uiState.value.isMissing)

        val live = viewModel("live")
        live.refresh()
        assertTrue(live.uiState.value.isMissing)
    }

    @Test
    fun `start opens a copy with the same exercises and leaves the original alone`() = runTest {
        val vm = viewModel("w1")
        workoutRepo.deleteById("live") // the fixture's running session would be a conflict, which has its own test

        val result = vm.start("w1")

        assertTrue(result is StartResult.Started)
        val newId = (result as StartResult.Started).workoutId
        assertEquals(newId, workoutRepo.getInProgress()!!.id)
        assertEquals(3, workoutRepo.getExercisesForWorkout(newId).size)
        assertEquals(3, workoutRepo.getExercisesForWorkout("w1").size)
    }

    @Test
    fun `start while another workout is running reports the conflict`() = runTest {
        val vm = viewModel("w1")

        assertEquals(StartResult.AlreadyInProgress("live"), vm.start("w1"))
    }

    @Test
    fun `save as routine builds a routine from the workout`() = runTest {
        val routineRepo = FakeRoutineRepository()
        val vm = viewModel("w1", routineRepo)

        val routineId = vm.saveAsRoutine()

        assertNotNull(routineId)
        assertEquals("Session w1", routineRepo.getRoutineById(routineId!!)?.name)
    }

    private fun viewModel(workoutId: String, routineRepo: FakeRoutineRepository = FakeRoutineRepository()): RecentWorkoutDetailViewModel {
        fun controller() = WorkoutSessionController(FakeActiveSessionRepository(), FakeClock(), FakeElapsedRealtimeClock(), CoroutineScope(dispatcher))
        fun tracking() = ActivityTrackingController(workoutRepo, FakeActivityTrackRepository(), FakeLocationSource(), FakeClock(), CoroutineScope(dispatcher))
        val starter = WorkoutStarter(workoutRepo, routineRepo, FakeClock())
        return RecentWorkoutDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf(RecentWorkoutDetailViewModel.WORKOUT_ID_ARG to workoutId)),
            workoutRepository = workoutRepo,
            exerciseRepository = exerciseRepo,
            settingsRepository = FakeSettingsRepository(),
            workoutToRoutineConverter = WorkoutToRoutineConverter(workoutRepo, routineRepo, FakeClock()),
            starter = RecentWorkoutStarter(
                workoutStarter = starter,
                sessionController = controller(),
                sessionDiscarder = SessionDiscarder(starter, controller(), tracking()),
                inProgressWorkoutResolver = InProgressWorkoutResolver(workoutRepo, tracking()),
            ),
        )
    }

    private fun workout(
        id: String,
        status: WorkoutStatus = WorkoutStatus.COMPLETED,
        structure: WorkoutStructure = WorkoutStructure.REGULAR,
        notes: String? = null,
    ) = WorkoutEntity(
        id = id, routineId = null, title = "Session $id", notes = notes, status = status,
        startedAt = 1_000L, endedAt = if (status == WorkoutStatus.COMPLETED) 61_000L else null,
        durationSeconds = if (status == WorkoutStatus.COMPLETED) 60 else 0, createdAt = 1_000L, updatedAt = 1_000L,
        structure = structure,
    )

    private fun workoutExercise(id: String, workoutId: String, exerciseId: String, orderIndex: Int, supersetGroup: Int? = null, notes: String? = null) =
        WorkoutExerciseEntity(
            id = id, workoutId = workoutId, exerciseId = exerciseId, orderIndex = orderIndex,
            supersetGroup = supersetGroup, restTimerSeconds = null, notes = notes,
        )

    private fun exercise(id: String, name: String, isDeleted: Boolean = false) = Exercise(
        id = id, name = name, exerciseType = ExerciseType.WEIGHT_REPS, primaryMuscleGroup = MuscleGroup.CHEST,
        secondaryMuscleGroups = emptyList(), equipment = Equipment.BARBELL, instructions = "", mediaPath = null,
        isCustom = false, isBodyweightVolumeEligible = false, isDeleted = isDeleted, createdAt = 0, updatedAt = 0,
    )

    private fun aSet(
        id: String,
        workoutExerciseId: String,
        orderIndex: Int,
        weightKg: Double?,
        reps: Int?,
        setType: SetType = SetType.NORMAL,
        rpe: Double? = null,
        completed: Boolean = true,
    ) = WorkoutSetEntity(
        id = id, workoutExerciseId = workoutExerciseId, orderIndex = orderIndex, setType = setType,
        weightKg = weightKg, reps = reps, durationSeconds = null, distanceMeters = null, rpe = rpe,
        customMetric = null, isCompleted = completed, completedAt = if (completed) 1L else null,
    )
}
