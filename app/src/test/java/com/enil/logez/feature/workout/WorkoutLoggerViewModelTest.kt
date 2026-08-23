package com.enil.logez.feature.workout

import androidx.lifecycle.SavedStateHandle
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.calc.StatSet
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.fakes.FakeActiveSessionRepository
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeElapsedRealtimeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeWorkoutRepository
import com.enil.logez.feature.workout.session.SetCompletionUseCase
import com.enil.logez.feature.workout.session.WorkoutSessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutLoggerViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun exercise(id: String, name: String, type: ExerciseType = ExerciseType.WEIGHT_REPS) = Exercise(
        id = id, name = name, exerciseType = type, primaryMuscleGroup = MuscleGroup.CHEST, secondaryMuscleGroups = emptyList(),
        equipment = Equipment.BARBELL, instructions = "", mediaPath = null, isCustom = false, isBodyweightVolumeEligible = false,
        isDeleted = false, createdAt = 0, updatedAt = 0,
    )

    private fun newViewModel(
        workoutId: String = "w1",
        workoutRepo: FakeWorkoutRepository = FakeWorkoutRepository(workouts = listOf(anInProgressWorkout(workoutId))),
        exerciseRepo: FakeExerciseRepository = FakeExerciseRepository(),
        settingsRepo: FakeSettingsRepository = FakeSettingsRepository(),
        clock: FakeClock = FakeClock(currentMillis = 10_000L),
        sessionController: WorkoutSessionController = WorkoutSessionController(FakeActiveSessionRepository(), clock, FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher())),
    ): WorkoutLoggerViewModel {
        val setCompletionUseCase = SetCompletionUseCase(workoutRepo, settingsRepo, sessionController, clock)
        return WorkoutLoggerViewModel(
            SavedStateHandle(mapOf("workoutId" to workoutId)), workoutRepo, exerciseRepo, settingsRepo, sessionController, setCompletionUseCase, clock,
        )
    }

    private fun anInProgressWorkout(id: String, routineId: String? = null, startedAt: Long = 5_000L) = WorkoutEntity(
        id = id, routineId = routineId, title = "Push Day", notes = null, status = WorkoutStatus.IN_PROGRESS,
        startedAt = startedAt, endedAt = null, durationSeconds = 0, createdAt = startedAt, updatedAt = startedAt,
    )

    @Test
    fun `loads exercises and sets from the workout, sorted by orderIndex`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press")))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 60.0, reps = 8, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null)),
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo)

        val state = vm.uiState.value
        assertEquals("Push Day", state.title)
        assertEquals(1, state.exercises.size)
        assertEquals("Bench Press", state.exercises[0].exerciseName)
        assertEquals(60.0, state.exercises[0].sets[0].weightKg)
        assertEquals(8, state.exercises[0].sets[0].reps)
    }

    @Test
    fun `toggleCheck marks a normal set completed and persists it`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press")))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 60.0, reps = 8, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null)),
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo, clock = FakeClock(currentMillis = 20_000L))

        val accepted = vm.toggleCheck("we1", "s1")

        assertTrue(accepted)
        assertTrue(vm.uiState.value.exercises[0].sets[0].isCompleted)
        val persisted = workoutRepo.getSetsForWorkoutExercise("we1").single()
        assertTrue(persisted.isCompleted)
        assertEquals(20_000L, persisted.completedAt)
    }

    @Test
    fun `toggleCheck rejects a FAILURE set with zero or blank reps`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press")))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.FAILURE, weightKg = 60.0, reps = null, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null)),
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo)

        val accepted = vm.toggleCheck("we1", "s1")

        assertFalse(accepted)
        assertFalse(vm.uiState.value.exercises[0].sets[0].isCompleted)
        assertTrue(vm.uiState.value.exercises[0].sets[0].failureError)
        assertFalse(workoutRepo.getSetsForWorkoutExercise("we1").single().isCompleted)
    }

    @Test
    fun `editing weight does not disturb the set's orderIndex or completion state`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press")))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(
                WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 60.0, reps = 8, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = true, completedAt = 999L),
                WorkoutSetEntity(id = "s2", workoutExerciseId = "we1", orderIndex = 1, setType = SetType.NORMAL, weightKg = 65.0, reps = 6, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null),
            ),
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo)

        vm.updateWeight("we1", "s2", 70.0)

        val s1 = workoutRepo.getSetsForWorkoutExercise("we1").first { it.id == "s1" }
        val s2 = workoutRepo.getSetsForWorkoutExercise("we1").first { it.id == "s2" }
        assertTrue(s1.isCompleted) // untouched set's completion survives editing a sibling
        assertEquals(999L, s1.completedAt)
        assertEquals(1, s2.orderIndex) // the edited set's own position is untouched
        assertEquals(70.0, s2.weightKg)
    }

    @Test
    fun `addSet copies the previous set's values with a fresh id`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press")))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 60.0, reps = 8, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = true, completedAt = 1L)),
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo)

        vm.addSet("we1")

        val sets = vm.uiState.value.exercises[0].sets
        assertEquals(2, sets.size)
        assertEquals(60.0, sets[1].weightKg)
        assertEquals(8, sets[1].reps)
        assertFalse(sets[1].isCompleted) // new row starts uncompleted even though copied from a completed one
    }

    @Test
    fun `replaceExercise carries over shared fields, clears the rest, and uncompletes every set`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press", ExerciseType.WEIGHT_REPS)))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 60.0, reps = 8, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = true, completedAt = 1L)),
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo)

        vm.replaceExercise("we1", exercise("ex-2", "Plank", ExerciseType.WEIGHT_DURATION))

        val updated = vm.uiState.value.exercises[0]
        assertEquals("ex-2", updated.exerciseId)
        assertEquals(60.0, updated.sets[0].weightKg) // WEIGHT shared by both types
        assertNull(updated.sets[0].reps) // REPS dropped by the new type
        assertFalse(updated.sets[0].isCompleted) // §5.1.9: completed sets are discarded on replace, not re-attributed

        val persisted = workoutRepo.getSetsForWorkoutExercise("we1").single()
        assertFalse(persisted.isCompleted)
        assertNull(persisted.completedAt)
        assertEquals("ex-2", workoutRepo.getExercisesForWorkout("w1").single().exerciseId)
    }

    @Test
    fun `superset grouping and removal cleans up orphaned solo groups`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"), exercise("ex-2", "Incline Press")))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            exercises = listOf(
                WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null),
                WorkoutExerciseEntity(id = "we2", workoutId = "w1", exerciseId = "ex-2", orderIndex = 1, supersetGroup = null, restTimerSeconds = null, notes = null),
            ),
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo)

        vm.startSupersetSelection("we1")
        vm.confirmSupersetTarget("we2")
        val grouped = vm.uiState.value.exercises
        assertNotNull(grouped[0].supersetGroup)
        assertEquals(grouped[0].supersetGroup, grouped[1].supersetGroup)

        vm.removeExercise("we2")
        val remaining = vm.uiState.value.exercises.single()
        assertNull(remaining.supersetGroup) // orphaned solo member cleaned up
        assertNull(workoutRepo.getExercisesForWorkout("w1").single().supersetGroup)
    }

    @Test
    fun `reorderExercises persists the new order`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"), exercise("ex-2", "Incline Press")))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            exercises = listOf(
                WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null),
                WorkoutExerciseEntity(id = "we2", workoutId = "w1", exerciseId = "ex-2", orderIndex = 1, supersetGroup = null, restTimerSeconds = null, notes = null),
            ),
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo)

        vm.reorderExercises(listOf("we2", "we1"))

        assertEquals(listOf("we2", "we1"), vm.uiState.value.exercises.map { it.id })
        assertEquals(0, workoutRepo.getExercisesForWorkout("w1").first { it.id == "we2" }.orderIndex)
        assertEquals(1, workoutRepo.getExercisesForWorkout("w1").first { it.id == "we1" }.orderIndex)
    }

    @Test
    fun `finish marks the workout COMPLETED with a computed duration`() = runTest {
        val workoutRepo = FakeWorkoutRepository(workouts = listOf(anInProgressWorkout("w1", startedAt = 10_000L)))
        val vm = newViewModel(workoutId = "w1", workoutRepo = workoutRepo, clock = FakeClock(currentMillis = 70_000L))

        val ok = vm.finish()

        assertTrue(ok)
        val workout = workoutRepo.getById("w1")!!
        assertEquals(WorkoutStatus.COMPLETED, workout.status)
        assertEquals(70_000L, workout.endedAt)
        assertEquals(60, workout.durationSeconds) // (70_000 - 10_000) / 1000
    }

    @Test
    fun `discard deletes the workout entirely`() = runTest {
        val workoutRepo = FakeWorkoutRepository(workouts = listOf(anInProgressWorkout("w1")))
        val vm = newViewModel(workoutId = "w1", workoutRepo = workoutRepo)

        vm.discard()

        assertNull(workoutRepo.getById("w1"))
    }

    @Test
    fun `addExercises auto-fills from the previous COMPLETED session when one exists`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press")))
        val previous = mapOf(
            "ex-1" to listOf(
                StatSet(setId = "old1", workoutId = "wOld", workoutStartedAt = 1L, orderIndex = 0, setType = SetType.NORMAL, weightKg = 55.0, reps = 9, durationSeconds = null, distanceMeters = null, customMetric = null, isCompleted = true, rpe = null, routineId = null),
            ),
        )
        val workoutRepo = FakeWorkoutRepository(workouts = listOf(anInProgressWorkout("w1")), statSetsByExercise = previous)
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo)

        vm.addExercises(listOf(exercise("ex-1", "Bench Press")))

        val added = vm.uiState.value.exercises.single()
        assertEquals(55.0, added.sets[0].weightKg)
        assertEquals(9, added.sets[0].reps)
        assertFalse(added.sets[0].isCompleted)
    }

    @Test
    fun `addExercises seeds one blank set when the exercise has never been logged`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press")))
        val vm = newViewModel(exerciseRepo = exerciseRepo)

        vm.addExercises(listOf(exercise("ex-1", "Bench Press")))

        val added = vm.uiState.value.exercises.single()
        assertEquals(1, added.sets.size)
        assertNull(added.sets[0].weightKg)
    }

    @Test
    fun `toggleCheck completing a set starts a rest timer end-to-end through the session controller`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press")))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = 60, notes = null)),
            sets = listOf(WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 60.0, reps = 8, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null)),
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo)

        vm.toggleCheck("we1", "s1")

        assertEquals(60_000L, vm.restRemainingMillisFlow.first())
    }

    @Test
    fun `togglePause pauses and resumes the session's elapsed timer`() = runTest {
        val clock = FakeClock(currentMillis = 0L)
        val workoutRepo = FakeWorkoutRepository(workouts = listOf(anInProgressWorkout("w1", startedAt = 0L)))
        val sessionController = WorkoutSessionController(FakeActiveSessionRepository(), clock, FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))
        sessionController.startSession("w1")
        val vm = newViewModel(workoutId = "w1", workoutRepo = workoutRepo, clock = clock, sessionController = sessionController)

        clock.currentMillis = 20_000L
        vm.togglePause()
        assertTrue(vm.uiState.value.isPaused)
        clock.currentMillis = 999_000L // must not count while paused
        assertEquals(20L, vm.elapsedSecondsFlow.first())

        vm.togglePause()
        assertFalse(vm.uiState.value.isPaused)
    }

    @Test
    fun `finish clears the session so the mini-bar and service both stand down`() = runTest {
        val workoutRepo = FakeWorkoutRepository(workouts = listOf(anInProgressWorkout("w1", startedAt = 10_000L)))
        val sessionController = WorkoutSessionController(FakeActiveSessionRepository(), FakeClock(currentMillis = 70_000L), FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))
        sessionController.startSession("w1")
        val vm = newViewModel(workoutId = "w1", workoutRepo = workoutRepo, clock = FakeClock(currentMillis = 70_000L), sessionController = sessionController)

        vm.finish()

        assertNull(sessionController.state.value.workoutId)
    }

    @Test
    fun `discard clears the session so the mini-bar and service both stand down`() = runTest {
        val workoutRepo = FakeWorkoutRepository(workouts = listOf(anInProgressWorkout("w1")))
        val sessionController = WorkoutSessionController(FakeActiveSessionRepository(), FakeClock(), FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))
        sessionController.startSession("w1")
        val vm = newViewModel(workoutId = "w1", workoutRepo = workoutRepo, sessionController = sessionController)

        vm.discard()

        assertNull(sessionController.state.value.workoutId)
    }

    @Test
    fun `discard clears the running rest timer before deleting, so it can't fire for a gone workout`() = runTest {
        val workoutRepo = FakeWorkoutRepository(workouts = listOf(anInProgressWorkout("w1")))
        val sessionController = WorkoutSessionController(FakeActiveSessionRepository(), FakeClock(), FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))
        sessionController.startSession("w1")
        sessionController.startRestTimer("we1", 90)
        val vm = newViewModel(workoutId = "w1", workoutRepo = workoutRepo, sessionController = sessionController)

        vm.discard()

        assertNull(sessionController.state.value.restDeadlineElapsedRealtimeMillis)
        assertNull(workoutRepo.getById("w1"))
    }

    @Test
    fun `completing a set stops and commits its running inline timer instead of orphaning it`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Plank", ExerciseType.DURATION)))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = 0, notes = null)),
            sets = listOf(WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = null, reps = null, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null)),
        )
        val elapsedClock = FakeElapsedRealtimeClock(currentMillis = 0L)
        val sessionController = WorkoutSessionController(FakeActiveSessionRepository(), FakeClock(), elapsedClock, CoroutineScope(UnconfinedTestDispatcher()))
        sessionController.startSession("w1")
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo, sessionController = sessionController)

        vm.startInlineTimer("we1", "s1")
        elapsedClock.currentMillis = 42_000L
        vm.toggleCheck("we1", "s1")

        // Without the stop-on-complete, the timer would keep ticking with no UI control left to
        // stop it (the play/pause button only renders for uncompleted sets) and its elapsed value
        // would never reach durationSeconds.
        assertNull(sessionController.state.value.inlineTimer)
        assertEquals(42, workoutRepo.getSetsForWorkoutExercise("we1").single().durationSeconds)
    }

    @Test
    fun `deleting a set clears its running inline timer instead of leaving a dangling pointer`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Plank", ExerciseType.DURATION)))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = null, reps = null, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null)),
        )
        val sessionController = WorkoutSessionController(FakeActiveSessionRepository(), FakeClock(), FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))
        sessionController.startSession("w1")
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo, sessionController = sessionController)

        vm.startInlineTimer("we1", "s1")
        vm.removeSet("we1", "s1")

        assertNull(sessionController.state.value.inlineTimer)
    }
}
