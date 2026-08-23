package com.enil.logez.feature.exercises

import androidx.lifecycle.SavedStateHandle
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeExerciseMediaStore
import com.enil.logez.fakes.FakeExerciseRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class CustomExerciseEditorViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun seedExercise(id: String = "existing-1") = Exercise(
        id = id,
        name = "Bench Press (Barbell)",
        exerciseType = ExerciseType.WEIGHT_REPS,
        primaryMuscleGroup = MuscleGroup.CHEST,
        secondaryMuscleGroups = listOf(MuscleGroup.TRICEPS),
        equipment = Equipment.BARBELL,
        instructions = "",
        mediaPath = null,
        isCustom = true,
        isBodyweightVolumeEligible = false,
        isDeleted = false,
        createdAt = 500L,
        updatedAt = 500L,
    )

    @Test
    fun `create mode saves a new custom exercise with the entered fields`() = runTest {
        val repo = FakeExerciseRepository()
        val clock = FakeClock(currentMillis = 9_000L)
        val vm = CustomExerciseEditorViewModel(SavedStateHandle(), repo, FakeExerciseMediaStore(), clock)

        vm.onNameChange("Cable Row")
        vm.onEquipmentChange(Equipment.MACHINE)
        vm.onPrimaryMuscleChange(MuscleGroup.UPPER_BACK)
        vm.onExerciseTypeChange(ExerciseType.REPS_ONLY)

        var saved = false
        vm.save { saved = true }

        assertTrue(saved)
        val stored = repo.allIncludingDeleted().single()
        assertEquals("Cable Row", stored.name)
        assertEquals(Equipment.MACHINE, stored.equipment)
        assertEquals(MuscleGroup.UPPER_BACK, stored.primaryMuscleGroup)
        assertEquals(ExerciseType.REPS_ONLY, stored.exerciseType)
        assertTrue(stored.isCustom)
        assertFalse(stored.isBodyweightVolumeEligible)
        assertEquals(9_000L, stored.createdAt)
        assertEquals(9_000L, stored.updatedAt)
    }

    @Test
    fun `blank name is rejected and nothing is saved`() = runTest {
        val repo = FakeExerciseRepository()
        val vm = CustomExerciseEditorViewModel(SavedStateHandle(), repo, FakeExerciseMediaStore(), FakeClock())

        var saved = false
        vm.save { saved = true }

        assertFalse(saved)
        assertTrue(vm.uiState.value.nameError)
        assertTrue(repo.allIncludingDeleted().isEmpty())
    }

    @Test
    fun `edit mode loads the existing exercise's fields`() = runTest {
        val repo = FakeExerciseRepository(listOf(seedExercise()))
        val vm = CustomExerciseEditorViewModel(
            SavedStateHandle(mapOf("exerciseId" to "existing-1")),
            repo,
            FakeExerciseMediaStore(),
            FakeClock(),
        )

        assertTrue(vm.isEditMode)
        assertEquals("Bench Press (Barbell)", vm.uiState.value.name)
        assertEquals(Equipment.BARBELL, vm.uiState.value.equipment)
        assertEquals(MuscleGroup.CHEST, vm.uiState.value.primaryMuscleGroup)
        assertEquals(ExerciseType.WEIGHT_REPS, vm.uiState.value.exerciseType)
    }

    @Test
    fun `exercise type is immutable in edit mode`() = runTest {
        val repo = FakeExerciseRepository(listOf(seedExercise()))
        val vm = CustomExerciseEditorViewModel(
            SavedStateHandle(mapOf("exerciseId" to "existing-1")),
            repo,
            FakeExerciseMediaStore(),
            FakeClock(),
        )

        vm.onExerciseTypeChange(ExerciseType.REPS_ONLY) // no-op — type can't change after creation

        assertEquals(ExerciseType.WEIGHT_REPS, vm.uiState.value.exerciseType)
    }

    @Test
    fun `editing preserves the original id and createdAt while updating updatedAt`() = runTest {
        val repo = FakeExerciseRepository(listOf(seedExercise()))
        val clock = FakeClock(currentMillis = 20_000L)
        val vm = CustomExerciseEditorViewModel(
            SavedStateHandle(mapOf("exerciseId" to "existing-1")),
            repo,
            FakeExerciseMediaStore(),
            clock,
        )

        vm.onNameChange("Bench Press (Barbell) — renamed")
        vm.save {}

        val stored = repo.getById("existing-1")!!
        assertEquals("existing-1", stored.id)
        assertEquals(500L, stored.createdAt) // unchanged
        assertEquals(20_000L, stored.updatedAt)
        assertEquals(1, repo.allIncludingDeleted().size) // no duplicate row created
    }

    @Test
    fun `prefill name seeds the create form from a library search query`() = runTest {
        val vm = CustomExerciseEditorViewModel(
            SavedStateHandle(mapOf("prefillName" to "Nordic Curl")),
            FakeExerciseRepository(),
            FakeExerciseMediaStore(),
            FakeClock(),
        )

        assertEquals("Nordic Curl", vm.uiState.value.name)
        assertFalse(vm.isEditMode)
    }
}
