package com.enil.logez.feature.routines

import com.enil.logez.core.data.dao.RoutineExercisePreviewRow
import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.RoutineExerciseEntity
import com.enil.logez.core.data.entity.RoutineFolderEntity
import com.enil.logez.core.data.entity.RoutineSetEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.fakes.FakeActiveSessionRepository
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeElapsedRealtimeClock
import com.enil.logez.fakes.FakeRoutineRepository
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeWorkoutRepository
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutTabViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newViewModel(
        routineRepo: FakeRoutineRepository,
        clock: FakeClock = FakeClock(),
        workoutRepo: FakeWorkoutRepository = FakeWorkoutRepository(),
    ): WorkoutTabViewModel {
        val sessionController = WorkoutSessionController(FakeActiveSessionRepository(), clock, FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))
        return WorkoutTabViewModel(routineRepo, workoutRepo, FakeSettingsRepository(), WorkoutStarter(workoutRepo, routineRepo, clock), sessionController, clock)
    }

    @Test
    fun `M8c heatmap counts a completed workout on its own local date`() = runTest {
        val startedAt = 1_700_000_000_000L // arbitrary fixed instant
        val expectedDate = java.time.Instant.ofEpochMilli(startedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(
                WorkoutEntity(
                    id = "w1", routineId = null, title = "Workout", notes = null,
                    status = WorkoutStatus.COMPLETED, startedAt = startedAt, endedAt = startedAt + 1000,
                    durationSeconds = 1000, createdAt = startedAt, updatedAt = startedAt,
                ),
            ),
        )
        val vm = newViewModel(FakeRoutineRepository(), workoutRepo = workoutRepo)

        assertEquals(1, vm.uiState.value.heatmapCounts[expectedDate])
    }

    @Test
    fun `M8c heatmap has no counts when there are no completed workouts`() = runTest {
        val vm = newViewModel(FakeRoutineRepository())
        assertEquals(emptyMap<java.time.LocalDate, Int>(), vm.uiState.value.heatmapCounts)
    }

    @Test
    fun `buildExercisePreview joins up to two names then counts the rest`() {
        assertEquals("", buildExercisePreview(emptyList()))
        assertEquals("Bench Press", buildExercisePreview(listOf("Bench Press")))
        assertEquals("Bench Press, Squat", buildExercisePreview(listOf("Bench Press", "Squat")))
        assertEquals("Bench Press, Squat, +3 more", buildExercisePreview(listOf("Bench Press", "Squat", "Row", "Curl", "Fly")))
    }

    @Test
    fun `folders and root routines are grouped correctly with exercise preview attached`() = runTest {
        val repo = FakeRoutineRepository(
            folders = listOf(RoutineFolderEntity(id = "f1", name = "Push Pull", orderIndex = 0, createdAt = 0, updatedAt = 0)),
            routines = listOf(
                RoutineEntity(id = "r1", folderId = "f1", name = "Push Day", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0),
                RoutineEntity(id = "r2", folderId = null, name = "Full Body", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0),
            ),
            previewRows = listOf(
                RoutineExercisePreviewRow(routineId = "r1", exerciseName = "Bench Press", orderIndex = 0),
                RoutineExercisePreviewRow(routineId = "r1", exerciseName = "Incline Press", orderIndex = 1),
            ),
        )
        val vm = newViewModel(repo)

        val state = vm.uiState.value
        assertEquals(1, state.folders.size)
        assertEquals("r1", state.folders[0].routines.single().routine.id)
        assertEquals("Bench Press, Incline Press", state.folders[0].routines.single().exercisePreview)
        assertEquals("r2", state.rootRoutines.single().routine.id)
        assertEquals("", state.rootRoutines.single().exercisePreview) // no preview rows for r2
    }

    @Test
    fun `creating a folder inserts it at the top and shifts existing folders down`() = runTest {
        val repo = FakeRoutineRepository(folders = listOf(RoutineFolderEntity(id = "f1", name = "Existing", orderIndex = 0, createdAt = 0, updatedAt = 0)))
        val vm = newViewModel(repo)

        vm.createFolder("New Folder")

        val folders = vm.uiState.value.folders.map { it.folder }
        assertEquals("New Folder", folders[0].name)
        assertEquals(0, folders[0].orderIndex)
        assertEquals("Existing", folders[1].name)
        assertEquals(1, folders[1].orderIndex)
    }

    @Test
    fun `duplicating a routine copies its exercises and sets with fresh ids and a copy suffix`() = runTest {
        val repo = FakeRoutineRepository(
            routines = listOf(RoutineEntity(id = "r1", folderId = null, name = "Push Day", notes = null, orderIndex = 0, createdAt = 100L, updatedAt = 100L)),
            exercises = listOf(RoutineExerciseEntity(id = "re1", routineId = "r1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(RoutineSetEntity(id = "s1", routineExerciseId = "re1", orderIndex = 0, setType = SetType.NORMAL, targetWeightKg = 100.0, targetReps = 5, targetRepRangeMin = null, targetRepRangeMax = null, targetDurationSeconds = null, targetDistanceMeters = null)),
        )
        val vm = newViewModel(repo, FakeClock(currentMillis = 5_000L))

        val newId = vm.duplicateRoutine("r1")

        assertNotNull(newId)
        assertNotEquals("r1", newId)
        val copy = repo.getRoutineById(newId!!)!!
        assertEquals("Push Day (copy)", copy.name)
        assertEquals(0, copy.orderIndex) // new copy at top
        assertEquals(1, repo.getRoutineById("r1")!!.orderIndex) // original pushed down

        val copyExercises = repo.getExercisesForRoutine(newId)
        assertEquals(1, copyExercises.size)
        assertNotEquals("re1", copyExercises[0].id)
        val copySets = repo.getSetsForRoutineExercise(copyExercises[0].id)
        assertEquals(100.0, copySets.single().targetWeightKg)
        assertEquals(5, copySets.single().targetReps)

        // Original untouched.
        assertEquals(1, repo.getExercisesForRoutine("r1").size)
        assertEquals("re1", repo.getExercisesForRoutine("r1").single().id)
    }

    @Test
    fun `deleting a folder moves its routines to root instead of deleting them`() = runTest {
        val repo = FakeRoutineRepository(
            folders = listOf(RoutineFolderEntity(id = "f1", name = "Push Pull", orderIndex = 0, createdAt = 0, updatedAt = 0)),
            routines = listOf(RoutineEntity(id = "r1", folderId = "f1", name = "Push Day", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0)),
        )
        val vm = newViewModel(repo)

        vm.deleteFolder(repo.getFolderById("f1")!!)

        assertNull(repo.getFolderById("f1"))
        assertNull(repo.getRoutineById("r1")!!.folderId) // survives, moved to root
    }
}
