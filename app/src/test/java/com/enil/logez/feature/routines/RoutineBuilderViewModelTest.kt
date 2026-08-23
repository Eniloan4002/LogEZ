package com.enil.logez.feature.routines

import androidx.lifecycle.SavedStateHandle
import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.RoutineExerciseEntity
import com.enil.logez.core.data.entity.RoutineFolderEntity
import com.enil.logez.core.data.entity.RoutineSetEntity
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeRoutineRepository
import com.enil.logez.fakes.FakeSettingsRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineBuilderViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun exercise(id: String, name: String, type: ExerciseType = ExerciseType.WEIGHT_REPS) = Exercise(
        id = id,
        name = name,
        exerciseType = type,
        primaryMuscleGroup = MuscleGroup.CHEST,
        secondaryMuscleGroups = emptyList(),
        equipment = Equipment.BARBELL,
        instructions = "",
        mediaPath = null,
        isCustom = false,
        isBodyweightVolumeEligible = false,
        isDeleted = false,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun newViewModel(
        routineId: String? = null,
        folderId: String? = null,
        routineRepo: FakeRoutineRepository = FakeRoutineRepository(),
        exerciseRepo: FakeExerciseRepository = FakeExerciseRepository(),
        clock: FakeClock = FakeClock(currentMillis = 5_000L),
    ) = RoutineBuilderViewModel(
        SavedStateHandle(buildMap { routineId?.let { put("routineId", it) }; folderId?.let { put("folderId", it) } }),
        routineRepo,
        exerciseRepo,
        FakeSettingsRepository(),
        clock,
    )

    @Test
    fun `create mode save is blocked with no title or no exercises`() = runTest {
        val repo = FakeRoutineRepository()
        val vm = newViewModel(routineRepo = repo)

        assertNull(vm.save()) // blank title, zero exercises

        vm.onTitleChange("Push Day")
        assertNull(vm.save()) // title set but still zero exercises

        vm.addExercises(listOf(exercise("ex-1", "Bench Press")))
        val savedId = vm.save()
        assertNotNull(savedId)
    }

    @Test
    fun `create mode save writes a routine at the top of its bucket with correct exercise and set entities`() = runTest {
        val repo = FakeRoutineRepository(routines = listOf(RoutineEntity(id = "existing", folderId = null, name = "Old", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0)))
        val vm = newViewModel(routineRepo = repo, clock = FakeClock(currentMillis = 9_000L))

        vm.onTitleChange("Push Day")
        vm.addExercises(listOf(exercise("ex-1", "Bench Press")))
        val savedId = vm.save()

        assertNotNull(savedId)
        val saved = repo.getRoutineById(savedId!!)!!
        assertEquals("Push Day", saved.name)
        assertEquals(0, saved.orderIndex) // new routine at top
        assertEquals(1, repo.getRoutineById("existing")!!.orderIndex) // existing pushed down
        assertEquals(9_000L, saved.createdAt)

        val exercises = repo.getExercisesForRoutine(savedId)
        assertEquals(1, exercises.size)
        assertEquals("ex-1", exercises[0].exerciseId)
        val sets = repo.getSetsForRoutineExercise(exercises[0].id)
        assertEquals(1, sets.size) // one empty set seeded on add
    }

    @Test
    fun `edit mode loads existing structure and save replaces it in place without moving position`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press")))
        val routineRepo = FakeRoutineRepository(
            routines = listOf(RoutineEntity(id = "r1", folderId = null, name = "Push Day", notes = "old notes", orderIndex = 3, createdAt = 100L, updatedAt = 100L)),
            exercises = listOf(RoutineExerciseEntity(id = "re1", routineId = "r1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(RoutineSetEntity(id = "s1", routineExerciseId = "re1", orderIndex = 0, setType = SetType.NORMAL, targetWeightKg = 60.0, targetReps = 8, targetRepRangeMin = null, targetRepRangeMax = null, targetDurationSeconds = null, targetDistanceMeters = null)),
        )
        val vm = newViewModel(routineId = "r1", routineRepo = routineRepo, exerciseRepo = exerciseRepo, clock = FakeClock(currentMillis = 20_000L))

        assertTrue(vm.isEditMode)
        assertEquals("Push Day", vm.uiState.value.title)
        assertEquals(1, vm.uiState.value.exercises.size)
        assertEquals(60.0, vm.uiState.value.exercises[0].sets[0].targetWeightKg)

        vm.onTitleChange("Push Day (renamed)")
        val savedId = vm.save()

        assertEquals("r1", savedId)
        val saved = routineRepo.getRoutineById("r1")!!
        assertEquals("Push Day (renamed)", saved.name)
        assertEquals(3, saved.orderIndex) // unchanged — edit-mode save doesn't re-home the routine
        assertEquals(100L, saved.createdAt) // unchanged
        assertEquals("old notes", saved.notes) // round-tripped even though the builder has no notes field
    }

    @Test
    fun `toggling rep range mode seeds min from the exact value, and back again keeps min as the exact value`() = runTest {
        val vm = newViewModel()
        vm.addExercises(listOf(exercise("ex-1", "Bench Press")))
        val exerciseId = vm.uiState.value.exercises[0].id
        val setId = vm.uiState.value.exercises[0].sets[0].id
        vm.updateReps(exerciseId, setId, 8)

        vm.toggleRepRangeMode(exerciseId)

        var set = vm.uiState.value.exercises[0].sets[0]
        assertTrue(vm.uiState.value.exercises[0].isRepRangeMode)
        assertEquals(8, set.targetRepRangeMin)
        assertNull(set.targetReps)

        vm.updateRepRangeMax(exerciseId, setId, 12)
        vm.toggleRepRangeMode(exerciseId) // back to exact

        set = vm.uiState.value.exercises[0].sets[0]
        assertFalse(vm.uiState.value.exercises[0].isRepRangeMode)
        assertEquals(8, set.targetReps) // "toggling back keeps min as targetReps" (§5.1.2)
        assertNull(set.targetRepRangeMin)
        assertNull(set.targetRepRangeMax)
    }

    @Test
    fun `add set copies the previous set's targets with type reset to normal`() = runTest {
        val vm = newViewModel()
        vm.addExercises(listOf(exercise("ex-1", "Bench Press")))
        val exerciseId = vm.uiState.value.exercises[0].id
        val firstSetId = vm.uiState.value.exercises[0].sets[0].id
        vm.updateWeight(exerciseId, firstSetId, 100.0)
        vm.updateReps(exerciseId, firstSetId, 5)
        vm.updateSetType(exerciseId, firstSetId, SetType.WARMUP)

        vm.addSet(exerciseId)

        val sets = vm.uiState.value.exercises[0].sets
        assertEquals(2, sets.size)
        assertEquals(100.0, sets[1].targetWeightKg)
        assertEquals(5, sets[1].targetReps)
        assertEquals(SetType.NORMAL, sets[1].setType) // reset, not copied
    }

    @Test
    fun `replace exercise preserves shared target fields and clears the rest`() = runTest {
        val vm = newViewModel()
        vm.addExercises(listOf(exercise("ex-1", "Bench Press", ExerciseType.WEIGHT_REPS)))
        val exerciseId = vm.uiState.value.exercises[0].id
        val setId = vm.uiState.value.exercises[0].sets[0].id
        vm.updateWeight(exerciseId, setId, 60.0)
        vm.updateReps(exerciseId, setId, 10)

        vm.replaceExercise(exerciseId, exercise("ex-2", "Plank Hold", ExerciseType.WEIGHT_DURATION))

        val updated = vm.uiState.value.exercises[0]
        assertEquals("ex-2", updated.exerciseId)
        assertEquals(ExerciseType.WEIGHT_DURATION, updated.exerciseType)
        assertEquals(60.0, updated.sets[0].targetWeightKg) // WEIGHT shared by both types — kept
        assertNull(updated.sets[0].targetReps) // REPS dropped by the new type — cleared
        assertNull(updated.sets[0].targetDurationSeconds) // new type has DURATION but old value never existed
    }

    @Test
    fun `replace exercise to a type without reps turns off rep range mode`() = runTest {
        val vm = newViewModel()
        vm.addExercises(listOf(exercise("ex-1", "Bench Press", ExerciseType.WEIGHT_REPS)))
        val exerciseId = vm.uiState.value.exercises[0].id
        vm.toggleRepRangeMode(exerciseId)
        assertTrue(vm.uiState.value.exercises[0].isRepRangeMode)

        vm.replaceExercise(exerciseId, exercise("ex-2", "Plank Hold", ExerciseType.DURATION))

        assertFalse(vm.uiState.value.exercises[0].isRepRangeMode)
    }

    @Test
    fun `confirming a superset target groups both exercises under the same group`() = runTest {
        val vm = newViewModel()
        vm.addExercises(listOf(exercise("ex-1", "Bench Press"), exercise("ex-2", "Incline Press")))
        val (first, second) = vm.uiState.value.exercises

        vm.startSupersetSelection(first.id)
        assertTrue(vm.uiState.value.supersetSelectionActive)
        vm.confirmSupersetTarget(second.id)

        val exercises = vm.uiState.value.exercises
        assertNotNull(exercises[0].supersetGroup)
        assertEquals(exercises[0].supersetGroup, exercises[1].supersetGroup)
        assertFalse(vm.uiState.value.supersetSelectionActive)
    }

    @Test
    fun `removing an exercise from a two-member superset clears the tag from the remaining solo member`() = runTest {
        val vm = newViewModel()
        vm.addExercises(listOf(exercise("ex-1", "Bench Press"), exercise("ex-2", "Incline Press")))
        val (first, second) = vm.uiState.value.exercises
        vm.startSupersetSelection(first.id)
        vm.confirmSupersetTarget(second.id)

        vm.removeExercise(first.id)

        val remaining = vm.uiState.value.exercises.single()
        assertNull(remaining.supersetGroup) // orphaned solo group cleaned up
    }

    @Test
    fun `isDirty is false right after load and true after any edit`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press")))
        val routineRepo = FakeRoutineRepository(
            routines = listOf(RoutineEntity(id = "r1", folderId = null, name = "Push Day", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0)),
        )
        val vm = newViewModel(routineId = "r1", routineRepo = routineRepo, exerciseRepo = exerciseRepo)

        assertFalse(vm.uiState.value.isDirty)
        vm.onTitleChange("Push Day v2")
        assertTrue(vm.uiState.value.isDirty)
    }

    @Test
    fun `new routine is created with the folderId passed via SavedStateHandle`() = runTest {
        val repo = FakeRoutineRepository(folders = listOf(RoutineFolderEntity(id = "f1", name = "Push Pull", orderIndex = 0, createdAt = 0, updatedAt = 0)))
        val vm = newViewModel(folderId = "f1", routineRepo = repo)

        vm.onTitleChange("Push Day")
        vm.addExercises(listOf(exercise("ex-1", "Bench Press")))
        val savedId = vm.save()!!

        assertEquals("f1", repo.getRoutineById(savedId)!!.folderId)
    }
}
