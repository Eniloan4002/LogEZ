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
import com.enil.logez.core.domain.model.TimerMode
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.fakes.FakeActiveSessionRepository
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeElapsedRealtimeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeHealthMetricsSource
import com.enil.logez.fakes.FakeMeasurementRepository
import com.enil.logez.fakes.FakePersonalRecordsRepository
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeTransactionRunner
import com.enil.logez.fakes.FakeWorkoutRepository
import com.enil.logez.feature.history.WorkoutEditor
import com.enil.logez.feature.workout.finish.LivePrDetector
import com.enil.logez.feature.workout.finish.PersonalRecordsUpdater
import com.enil.logez.feature.workout.session.SetCompletionUseCase
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The per-exercise stopwatch / countdown set timer (Owner, 2026-10-01): its mode is stored on this
 * workout's copy of the exercise and the timer logs what it held. Elapsed time comes from a fake
 * elapsedRealtime clock, expected values are literal.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutLoggerTimerModeTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val elapsedClock = FakeElapsedRealtimeClock(currentMillis = 0L)
    private val activeSessions = FakeActiveSessionRepository()
    private val sessionController = WorkoutSessionController(activeSessions, FakeClock(currentMillis = 10_000L), elapsedClock, CoroutineScope(UnconfinedTestDispatcher()))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun exercise(id: String, name: String, type: ExerciseType) = Exercise(
        id = id, name = name, exerciseType = type, primaryMuscleGroup = MuscleGroup.ABDOMINALS, secondaryMuscleGroups = emptyList(),
        equipment = Equipment.NONE, instructions = "", mediaPath = null, isCustom = false, isBodyweightVolumeEligible = false,
        isDeleted = false, createdAt = 0, updatedAt = 0,
    )

    private fun aSet(id: String, exerciseId: String, order: Int, seconds: Int?, completed: Boolean = false) = WorkoutSetEntity(
        id = id, workoutExerciseId = exerciseId, orderIndex = order, setType = SetType.NORMAL, weightKg = null, reps = null,
        durationSeconds = seconds, distanceMeters = null, rpe = null, customMetric = null, isCompleted = completed, completedAt = if (completed) 1L else null,
    )

    private fun aWeightExercise(id: String, exerciseId: String, order: Int, timerMode: String? = null) =
        WorkoutExerciseEntity(id = id, workoutId = "w1", exerciseId = exerciseId, orderIndex = order, supersetGroup = null, restTimerSeconds = 0, notes = null, timerMode = timerMode)

    /** Push Day: Plank (we1: 60 s, 60 s, blank) above Wall Sit (we2: 45 s). */
    private fun fixture(plankMode: String? = null, previous: Map<String, List<StatSet>> = emptyMap()) = FakeWorkoutRepository(
        workouts = listOf(
            WorkoutEntity(
                id = "w1", routineId = null, title = "Push Day", notes = null, status = WorkoutStatus.IN_PROGRESS,
                startedAt = 5_000L, endedAt = null, durationSeconds = 0, createdAt = 5_000L, updatedAt = 5_000L,
            ),
        ),
        exercises = listOf(aWeightExercise("we1", "ex-plank", 0, plankMode), aWeightExercise("we2", "ex-wall", 1)),
        sets = listOf(aSet("s1", "we1", 0, 60), aSet("s2", "we1", 1, 60), aSet("s3", "we1", 2, null), aSet("w0", "we2", 0, 45)),
        statSetsByExercise = previous,
    )

    private val exercises = FakeExerciseRepository(
        listOf(exercise("ex-plank", "Plank", ExerciseType.DURATION), exercise("ex-wall", "Wall Sit", ExerciseType.DURATION), exercise("ex-hang", "Dead Hang", ExerciseType.DURATION)),
    )

    private fun newViewModel(workoutRepo: FakeWorkoutRepository, settingsRepo: FakeSettingsRepository = FakeSettingsRepository(), isEditMode: Boolean = false): WorkoutLoggerViewModel {
        val clock = FakeClock(currentMillis = 10_000L)
        val setCompletionUseCase = SetCompletionUseCase(workoutRepo, settingsRepo, sessionController, clock)
        val livePrDetector = LivePrDetector(workoutRepo, exercises, FakePersonalRecordsRepository(), FakeMeasurementRepository(), clock)
        val updater = PersonalRecordsUpdater(workoutRepo, exercises, FakePersonalRecordsRepository(), FakeMeasurementRepository(), settingsRepo)
        val editor = WorkoutEditor(workoutRepo, updater, FakeTransactionRunner(), clock)
        return WorkoutLoggerViewModel(
            SavedStateHandle(buildMap<String, Any> { put("workoutId", "w1"); if (isEditMode) put(WorkoutLoggerViewModel.EDIT_MODE_ARG, true) }),
            workoutRepo, exercises, settingsRepo, sessionController, setCompletionUseCase, livePrDetector, editor, FakeHealthMetricsSource(), clock,
        )
    }

    private suspend fun startSession() = sessionController.startSession("w1")

    private fun WorkoutLoggerViewModel.durationOf(setId: String) = uiState.value.exercises.flatMap { it.sets }.first { it.id == setId }.durationSeconds

    @Test
    fun `choosing a countdown is stored on that workout exercise only and the other exercise stays a stopwatch`() = runTest {
        val repo = fixture()
        val vm = newViewModel(repo)

        vm.setTimerMode("we1", TimerMode.COUNTDOWN)

        assertEquals("COUNTDOWN", repo.getExercisesForWorkout("w1").first { it.id == "we1" }.timerMode)
        assertNull(repo.getExercisesForWorkout("w1").first { it.id == "we2" }.timerMode)
        assertEquals(listOf("COUNTDOWN", null), vm.uiState.value.exercises.map { it.timerMode })

        vm.setTimerMode("we1", TimerMode.STOPWATCH)
        assertNull(repo.getExercisesForWorkout("w1").first { it.id == "we1" }.timerMode)
    }

    @Test
    fun `a stored mode this app does not know behaves as a stopwatch and is not rewritten by other edits`() = runTest {
        val repo = fixture(plankMode = "INTERVALS")
        val vm = newViewModel(repo)
        startSession()

        assertEquals(TimerMode.STOPWATCH, vm.uiState.value.exercises[0].timerModeValue)
        assertEquals("INTERVALS", vm.uiState.value.exercises[0].timerMode)
        vm.startInlineTimer("we1", "s1")
        assertEquals(TimerMode.STOPWATCH, sessionController.state.value.inlineTimer?.mode)
        vm.updateExerciseNotes("we1", "hold the line")
        assertEquals("INTERVALS", repo.getExercisesForWorkout("w1").first { it.id == "we1" }.timerMode)
    }

    @Test
    fun `the mode cannot be switched while one of the exercise's sets is being timed`() = runTest {
        val repo = fixture()
        val vm = newViewModel(repo)
        startSession()
        vm.startInlineTimer("we1", "s1")

        vm.setTimerMode("we1", TimerMode.COUNTDOWN)

        assertNull(repo.getExercisesForWorkout("w1").first { it.id == "we1" }.timerMode)
    }

    @Test
    fun `the mode cannot be switched while editing a finished workout`() = runTest {
        val repo = fixture()
        val vm = newViewModel(repo, isEditMode = true)

        vm.setTimerMode("we1", TimerMode.COUNTDOWN)

        assertNull(repo.getExercisesForWorkout("w1").first { it.id == "we1" }.timerMode)
    }

    @Test
    fun `a countdown starts from the set's TIME`() = runTest {
        val vm = newViewModel(fixture(plankMode = "COUNTDOWN"))
        startSession()

        vm.startInlineTimer("we1", "s1")

        val timer = sessionController.state.value.inlineTimer!!
        assertEquals(TimerMode.COUNTDOWN, timer.mode)
        assertEquals(60, timer.targetSeconds)
        assertEquals(60_000L, timer.deadlineElapsedRealtimeMillis)
    }

    @Test
    fun `a countdown with a blank TIME counts down from the PREVIOUS time and fills it in`() = runTest {
        val previous = mapOf(
            "ex-plank" to listOf(
                StatSet(setId = "p1", workoutId = "wOld", workoutStartedAt = 1_000L, orderIndex = 0, setType = SetType.NORMAL, weightKg = null, reps = null, durationSeconds = 60, distanceMeters = null, customMetric = null, isCompleted = true, rpe = null, routineId = null),
                StatSet(setId = "p2", workoutId = "wOld", workoutStartedAt = 1_000L, orderIndex = 1, setType = SetType.NORMAL, weightKg = null, reps = null, durationSeconds = 60, distanceMeters = null, customMetric = null, isCompleted = true, rpe = null, routineId = null),
                StatSet(setId = "p3", workoutId = "wOld", workoutStartedAt = 1_000L, orderIndex = 2, setType = SetType.NORMAL, weightKg = null, reps = null, durationSeconds = 45, distanceMeters = null, customMetric = null, isCompleted = true, rpe = null, routineId = null),
            ),
        )
        val vm = newViewModel(fixture(plankMode = "COUNTDOWN", previous = previous))
        startSession()

        vm.startInlineTimer("we1", "s3")

        assertEquals(45, sessionController.state.value.inlineTimer?.targetSeconds)
        assertEquals(45, vm.durationOf("s3"))
        assertNull(vm.uiState.value.timeHintSetId)
    }

    @Test
    fun `a countdown with no TIME and no PREVIOUS does not start, and asks for a time until one is typed`() = runTest {
        val vm = newViewModel(fixture(plankMode = "COUNTDOWN"))
        startSession()

        vm.startInlineTimer("we1", "s3")

        assertNull(sessionController.state.value.inlineTimer)
        assertEquals("s3", vm.uiState.value.timeHintSetId)

        vm.updateDuration("we1", "s3", 30)
        assertNull(vm.uiState.value.timeHintSetId)
    }

    @Test
    fun `a stopwatch starts with a blank TIME and replaces the typed number with the elapsed seconds`() = runTest {
        val vm = newViewModel(fixture())
        startSession()

        vm.startInlineTimer("we1", "s3")
        elapsedClock.currentMillis = 38_900L
        vm.stopInlineTimer("we1", "s3")

        assertEquals(38, vm.durationOf("s3"))
        assertNull(vm.uiState.value.timeHintSetId)
    }

    @Test
    fun `a countdown stopped early logs the seconds actually held, not the target`() = runTest {
        val repo = fixture(plankMode = "COUNTDOWN")
        val vm = newViewModel(repo)
        startSession()

        vm.startInlineTimer("we1", "s1")
        elapsedClock.currentMillis = 19_400L
        vm.stopInlineTimer("we1", "s1")

        assertEquals(19, vm.durationOf("s1"))
        assertEquals(19, repo.getSetsForWorkoutExercise("we1").first { it.id == "s1" }.durationSeconds)
    }

    @Test
    fun `playing a second set stops the first and logs its time instead of dropping it`() = runTest {
        val repo = fixture()
        val vm = newViewModel(repo)
        startSession()

        vm.startInlineTimer("we1", "s1")
        elapsedClock.currentMillis = 20_000L
        vm.startInlineTimer("we1", "s2")

        assertEquals(20, repo.getSetsForWorkoutExercise("we1").first { it.id == "s1" }.durationSeconds)
        assertEquals("s2", sessionController.state.value.inlineTimer?.setId)
        assertEquals(20_000L, sessionController.state.value.inlineTimer?.startElapsedRealtimeMillis)
    }

    @Test
    fun `checking a different set while a timer runs logs the timer first, so there is one deadline at a time`() = runTest {
        val repo = fixture()
        val vm = newViewModel(repo)
        startSession()

        vm.startInlineTimer("we1", "s1")
        elapsedClock.currentMillis = 33_000L
        vm.toggleCheck("we2", "w0")

        assertNull(sessionController.state.value.inlineTimer)
        assertEquals(33, repo.getSetsForWorkoutExercise("we1").first { it.id == "s1" }.durationSeconds)
    }

    @Test
    fun `starting a set timer ends a running rest timer`() = runTest {
        val vm = newViewModel(fixture())
        startSession()
        sessionController.startRestTimer("we2", 90)

        vm.startInlineTimer("we1", "s1")

        assertNull(sessionController.state.value.restDeadlineElapsedRealtimeMillis)
        assertEquals("s1", sessionController.state.value.inlineTimer?.setId)
    }

    @Test
    fun `Replace Exercise puts the card back on a stopwatch, in memory and in the row`() = runTest {
        val repo = fixture(plankMode = "COUNTDOWN")
        val vm = newViewModel(repo)

        vm.replaceExercise("we1", exercise("ex-hang", "Dead Hang", ExerciseType.DURATION))

        assertNull(vm.uiState.value.exercises[0].timerMode)
        assertEquals("ex-hang", vm.uiState.value.exercises[0].exerciseId)
        assertNull(repo.getExercisesForWorkout("w1").first { it.id == "we1" }.timerMode)
    }

    @Test
    fun `Finish logs a running timer into its set before the Save screen reads it`() = runTest {
        val repo = fixture(plankMode = "COUNTDOWN")
        val vm = newViewModel(repo)
        startSession()

        vm.startInlineTimer("we1", "s1")
        elapsedClock.currentMillis = 41_000L
        assertTrue(vm.prepareForFinish())

        assertNull(sessionController.state.value.inlineTimer)
        assertEquals(41, repo.getSetsForWorkoutExercise("we1").first { it.id == "s1" }.durationSeconds)
    }

    @Test
    fun `turning the Inline timer setting off stops a running timer and logs what it held`() = runTest {
        val repo = fixture()
        val settings = FakeSettingsRepository()
        val vm = newViewModel(repo, settings)
        startSession()

        vm.startInlineTimer("we1", "s1")
        elapsedClock.currentMillis = 12_000L
        settings.setInlineTimerEnabled(false)

        assertNull(sessionController.state.value.inlineTimer)
        assertEquals(12, repo.getSetsForWorkoutExercise("we1").first { it.id == "s1" }.durationSeconds)
    }

    @Test
    fun `a running timer is saved with the session, so process death does not lose it`() = runTest {
        val vm = newViewModel(fixture(plankMode = "COUNTDOWN"))
        startSession()

        vm.startInlineTimer("we1", "s1")

        val saved = activeSessions.snapshot.inlineTimer!!
        assertEquals("we1", saved.exerciseId)
        assertEquals("s1", saved.setId)
        assertEquals("COUNTDOWN", saved.mode)
        assertEquals(60, saved.targetSeconds)
    }

    // --- review fixes ---

    @Test
    fun `a second Play with TIME still blank raises the hint again, so TIME is focused again`() = runTest {
        val vm = newViewModel(fixture(plankMode = "COUNTDOWN"))
        startSession()

        vm.startInlineTimer("we1", "s3")
        val first = vm.uiState.value.timeHintToken
        vm.startInlineTimer("we1", "s3")

        assertEquals("s3", vm.uiState.value.timeHintSetId)
        assertTrue(vm.uiState.value.timeHintToken > first)
    }

    @Test
    fun `checking the set clears its time hint`() = runTest {
        val vm = newViewModel(fixture(plankMode = "COUNTDOWN"))
        startSession()
        vm.startInlineTimer("we1", "s3")
        assertEquals("s3", vm.uiState.value.timeHintSetId)

        vm.toggleCheck("we1", "s3")

        assertNull(vm.uiState.value.timeHintSetId)
    }

    @Test
    fun `Replace Exercise clears a time hint left on the old exercise`() = runTest {
        val vm = newViewModel(fixture(plankMode = "COUNTDOWN"))
        startSession()
        vm.startInlineTimer("we1", "s3")

        vm.replaceExercise("we1", exercise("ex-hang", "Dead Hang", ExerciseType.DURATION))

        assertNull(vm.uiState.value.timeHintSetId)
    }

    @Test
    fun `Play on a checked set does nothing`() = runTest {
        val vm = newViewModel(fixture())
        startSession()
        vm.toggleCheck("we1", "s1")

        vm.startInlineTimer("we1", "s1")

        assertNull(sessionController.state.value.inlineTimer)
    }

    @Test
    fun `no timer starts while editing a finished workout`() = runTest {
        val vm = newViewModel(fixture(), isEditMode = true)

        vm.startInlineTimer("we1", "s1")

        assertNull(sessionController.state.value.inlineTimer)
    }

    @Test
    fun `removing the exercise whose set is being timed stops the timer`() = runTest {
        val vm = newViewModel(fixture())
        startSession()
        vm.startInlineTimer("we1", "s1")

        vm.removeExercise("we1")

        assertNull(sessionController.state.value.inlineTimer)
        assertNull(activeSessions.snapshot.inlineTimer)
    }

    @Test
    fun `discarding the workout clears a running timer from memory and from the saved session`() = runTest {
        val vm = newViewModel(fixture())
        startSession()
        vm.startInlineTimer("we1", "s1")

        vm.discard()

        assertNull(sessionController.state.value.inlineTimer)
        assertNull(activeSessions.snapshot.inlineTimer)
    }

    @Test
    fun `a time the Service wrote when a countdown ended is mirrored into the open logger`() = runTest {
        val vm = newViewModel(fixture(plankMode = "COUNTDOWN"))
        startSession()
        vm.startInlineTimer("we1", "s1")
        elapsedClock.currentMillis = 60_000L

        sessionController.finishCountdown()

        assertEquals(60, vm.durationOf("s1"))
        assertEquals(false, vm.uiState.value.exercises[0].sets[0].isCompleted)
    }

    @Test
    fun `a time the notification's Complete set wrote for a stopped timer is mirrored into the open logger`() = runTest {
        val vm = newViewModel(fixture())
        startSession()

        sessionController.notifyInlineTimerLoggedExternally(com.enil.logez.feature.workout.session.InlineTimerLog("we1", "s2", 27))

        assertEquals(27, vm.durationOf("s2"))
    }

    @Test
    fun `opening the logger finishes a countdown that ran out while the app was dead and logs its full time`() = runTest {
        val repo = fixture(plankMode = "COUNTDOWN")
        val clock = FakeClock(currentMillis = 1_000_000L)
        val elapsed = FakeElapsedRealtimeClock(currentMillis = 50_000L)
        val first = WorkoutSessionController(activeSessions, clock, elapsed, CoroutineScope(UnconfinedTestDispatcher()))
        first.startSession("w1")
        first.startInlineTimer("we1", "s1", TimerMode.COUNTDOWN, 45)
        // 10 minutes later in a new process: a new controller reads the saved timer.
        clock.currentMillis = 1_600_000L
        elapsed.currentMillis = 650_000L
        val reborn = WorkoutSessionController(activeSessions, clock, elapsed, CoroutineScope(UnconfinedTestDispatcher()))
        val workoutRepo = repo
        val settingsRepo = FakeSettingsRepository()
        val setCompletionUseCase = SetCompletionUseCase(workoutRepo, settingsRepo, reborn, clock)
        val livePrDetector = LivePrDetector(workoutRepo, exercises, FakePersonalRecordsRepository(), FakeMeasurementRepository(), clock)
        val updater = PersonalRecordsUpdater(workoutRepo, exercises, FakePersonalRecordsRepository(), FakeMeasurementRepository(), settingsRepo)
        val editor = WorkoutEditor(workoutRepo, updater, FakeTransactionRunner(), clock)
        val vm = WorkoutLoggerViewModel(
            SavedStateHandle(mapOf("workoutId" to "w1")),
            workoutRepo, exercises, settingsRepo, reborn, setCompletionUseCase, livePrDetector, editor, FakeHealthMetricsSource(), clock,
        )

        assertNull(reborn.state.value.inlineTimer)
        assertEquals(45, repo.getSetsForWorkoutExercise("we1").first { it.id == "s1" }.durationSeconds)
        assertEquals(45, vm.durationOf("s1"))
    }
}
