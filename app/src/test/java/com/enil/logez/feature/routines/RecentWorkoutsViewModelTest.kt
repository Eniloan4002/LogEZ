package com.enil.logez.feature.routines

import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.fakes.FakeActiveSessionRepository
import com.enil.logez.fakes.FakeActivityTrackRepository
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeElapsedRealtimeClock
import com.enil.logez.fakes.FakeLocationSource
import com.enil.logez.fakes.FakeRoutineRepository
import com.enil.logez.fakes.FakeWorkoutRepository
import com.enil.logez.feature.activity.ActivityTrackingController
import com.enil.logez.feature.workout.InProgressWorkoutResolver
import com.enil.logez.feature.workout.SessionDiscarder
import com.enil.logez.feature.workout.StartResult
import com.enil.logez.feature.workout.WorkoutStarter
import com.enil.logez.feature.workout.session.WorkoutSessionController
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** P-205 "Recent": the feed it builds, and that Start reuses History's Copy Workout semantics. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecentWorkoutsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `lists completed workouts newest first, with exercise counts and routine provenance`() = runTest {
        val repo = FakeWorkoutRepository(
            workouts = listOf(
                completed("old", startedAt = 1_000L, routineId = "r1", title = "Push Day"),
                completed("new", startedAt = 5_000L, routineId = null, title = "New Workout"),
                inProgress("live", startedAt = 9_000L),
            ),
            exercises = listOf(
                exercise("we1", "old", 0), exercise("we2", "old", 1), exercise("we3", "old", 2),
                exercise("we4", "new", 0),
            ),
        )
        val vm = viewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        val cards = vm.uiState.value
        // The in-progress session is not "recent" -- only finished ones are.
        assertEquals(listOf("new", "old"), cards.map { it.workoutId })
        assertEquals(1, cards[0].exerciseCount)
        assertFalse(cards[0].isFromRoutine)
        assertEquals(3, cards[1].exerciseCount)
        assertTrue(cards[1].isFromRoutine)
        assertEquals("Push Day", cards[1].title)
    }

    @Test
    fun `a GPS walk or run is left out of both the card and the full list`() = runTest {
        val repo = FakeWorkoutRepository(
            workouts = listOf(
                completed("lift", startedAt = 1_000L, routineId = null, title = "Push Day"),
                completed("run", startedAt = 9_000L, routineId = null, title = "Run").copy(kind = WorkoutKind.GPS_TRACKED),
            ),
            exercises = listOf(exercise("we1", "lift", 0), exercise("we2", "run", 0)),
        )
        val vm = viewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.cardState.collect {} }

        assertEquals(listOf("lift"), vm.uiState.value.map { it.workoutId })
        assertEquals(listOf("lift"), vm.cardState.value.map { it.workoutId })
    }

    @Test
    fun `the card holds the newest three while the list holds all of them`() = runTest {
        val repo = FakeWorkoutRepository(
            workouts = (1..5).map { completed("w$it", startedAt = it * 1_000L, routineId = null, title = "Workout $it") },
            exercises = (1..5).map { exercise("we$it", "w$it", 0) },
        )
        val vm = viewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.cardState.collect {} }

        assertEquals(listOf("w5", "w4", "w3"), vm.cardState.value.map { it.workoutId })
        assertEquals(listOf("w5", "w4", "w3", "w2", "w1"), vm.uiState.value.map { it.workoutId })
    }

    @Test
    fun `start opens a new in-progress copy with the same exercises and last time's values`() = runTest {
        val repo = FakeWorkoutRepository(
            workouts = listOf(completed("w1", startedAt = 1_000L, routineId = null, title = "New Workout")),
            exercises = listOf(exercise("we1", "w1", 0)),
            sets = listOf(set("s1", "we1", weightKg = 13.6, reps = 10, completed = true)),
        )
        val vm = viewModel(repo)

        val result = vm.start("w1")

        assertTrue(result is StartResult.Started)
        val newId = (result as StartResult.Started).workoutId
        val live = repo.getInProgress()!!
        assertEquals(newId, live.id)
        assertEquals("New Workout", live.title)
        val copiedExercises = repo.getExercisesForWorkout(newId)
        assertEquals(1, copiedExercises.size)
        val copiedSets = repo.getSetsForWorkoutExercise(copiedExercises.single().id)
        assertEquals(1, copiedSets.size)
        assertEquals(13.6, copiedSets.single().weightKg!!, 1e-9)
        assertEquals(10, copiedSets.single().reps)
        // A copy starts unfinished, whatever the source's state was.
        assertFalse(copiedSets.single().isCompleted)
    }

    @Test
    fun `start while a workout is running reports the conflict and creates nothing`() = runTest {
        val repo = FakeWorkoutRepository(
            workouts = listOf(
                completed("w1", startedAt = 1_000L, routineId = null, title = "New Workout"),
                inProgress("live", startedAt = 2_000L),
            ),
            exercises = listOf(exercise("we1", "w1", 0)),
        )
        val vm = viewModel(repo)

        val result = vm.start("w1")

        assertEquals(StartResult.AlreadyInProgress("live"), result)
        assertEquals("live", repo.getInProgress()!!.id)
    }

    @Test
    fun `discard and start replaces the running workout with a copy`() = runTest {
        val repo = FakeWorkoutRepository(
            workouts = listOf(
                completed("w1", startedAt = 1_000L, routineId = null, title = "New Workout"),
                inProgress("live", startedAt = 2_000L),
            ),
            exercises = listOf(exercise("we1", "w1", 0)),
        )
        val vm = viewModel(repo)

        val newId = vm.discardInProgressAndStart("w1")

        assertNull(repo.getById("live"))
        assertEquals(newId, repo.getInProgress()!!.id)
        assertEquals(1, repo.getExercisesForWorkout(newId).size)
    }

    // --- recentGroupOf: rolling 7-day buckets, expected values hard-coded (project testing rule) ---

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 9, 30)

    /** Epoch millis at noon UTC on [date]. */
    private fun noon(date: LocalDate) = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `today and six days ago are this week`() {
        assertEquals(RecentGroup.THIS_WEEK, recentGroupOf(noon(LocalDate.of(2026, 9, 30)), today, zone))
        assertEquals(RecentGroup.THIS_WEEK, recentGroupOf(noon(LocalDate.of(2026, 9, 24)), today, zone))
    }

    @Test
    fun `seven through thirteen days ago are last week`() {
        assertEquals(RecentGroup.LAST_WEEK, recentGroupOf(noon(LocalDate.of(2026, 9, 23)), today, zone))
        assertEquals(RecentGroup.LAST_WEEK, recentGroupOf(noon(LocalDate.of(2026, 9, 17)), today, zone))
    }

    @Test
    fun `fourteen days ago and earlier are older`() {
        assertEquals(RecentGroup.OLDER, recentGroupOf(noon(LocalDate.of(2026, 9, 16)), today, zone))
        assertEquals(RecentGroup.OLDER, recentGroupOf(noon(LocalDate.of(2025, 1, 1)), today, zone))
    }

    // --- fixtures ---

    private fun viewModel(repo: FakeWorkoutRepository): RecentWorkoutsViewModel {
        fun controller() = WorkoutSessionController(FakeActiveSessionRepository(), FakeClock(), FakeElapsedRealtimeClock(), CoroutineScope(dispatcher))
        fun tracking() = ActivityTrackingController(repo, FakeActivityTrackRepository(), FakeLocationSource(), FakeClock(), CoroutineScope(dispatcher))
        val starter = WorkoutStarter(repo, FakeRoutineRepository(), FakeClock())
        return RecentWorkoutsViewModel(
            workoutRepository = repo,
            starter = RecentWorkoutStarter(
                workoutStarter = starter,
                sessionController = controller(),
                sessionDiscarder = SessionDiscarder(starter, controller(), tracking()),
                inProgressWorkoutResolver = InProgressWorkoutResolver(repo, tracking()),
            ),
        )
    }

    private fun completed(id: String, startedAt: Long, routineId: String?, title: String) = WorkoutEntity(
        id = id, routineId = routineId, title = title, notes = null, status = WorkoutStatus.COMPLETED,
        startedAt = startedAt, endedAt = startedAt + 60_000L, durationSeconds = 60, createdAt = startedAt, updatedAt = startedAt,
    )

    private fun inProgress(id: String, startedAt: Long) = WorkoutEntity(
        id = id, routineId = null, title = "New Workout", notes = null, status = WorkoutStatus.IN_PROGRESS,
        startedAt = startedAt, endedAt = null, durationSeconds = 0, createdAt = startedAt, updatedAt = startedAt,
    )

    private fun exercise(id: String, workoutId: String, orderIndex: Int) = WorkoutExerciseEntity(
        id = id, workoutId = workoutId, exerciseId = "ex-$id", orderIndex = orderIndex,
        supersetGroup = null, restTimerSeconds = null, notes = null,
    )

    private fun set(id: String, workoutExerciseId: String, weightKg: Double?, reps: Int?, completed: Boolean) = WorkoutSetEntity(
        id = id, workoutExerciseId = workoutExerciseId, orderIndex = 0, setType = SetType.NORMAL,
        weightKg = weightKg, reps = reps, durationSeconds = null, distanceMeters = null, rpe = null,
        customMetric = null, isCompleted = completed, completedAt = if (completed) 1_000L else null,
    )
}
