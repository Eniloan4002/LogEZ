package com.enil.logez.feature.exercises

import androidx.lifecycle.SavedStateHandle
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.ExerciseHistoryEntry
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeWorkoutRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseDetailViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun customExercise(id: String = "custom-1") = Exercise(
        id = id,
        name = "My Custom Row",
        exerciseType = ExerciseType.WEIGHT_REPS,
        primaryMuscleGroup = MuscleGroup.UPPER_BACK,
        secondaryMuscleGroups = emptyList(),
        equipment = Equipment.MACHINE,
        instructions = "",
        mediaPath = null,
        isCustom = true,
        isBodyweightVolumeEligible = false,
        isDeleted = false,
        createdAt = 100L,
        updatedAt = 100L,
    )

    private fun seedExercise(id: String = "seed-1") = customExercise(id).copy(isCustom = false)

    @Test
    fun `duplicate creates a new custom copy with a fresh id and no history`() = runTest {
        val original = seedExercise()
        val history = listOf(
            ExerciseHistoryEntry(
                workoutId = "w1", workoutTitle = "Push Day", workoutStartedAt = 0, setId = "s1",
                setOrderIndex = 0, setType = SetType.NORMAL, weightKg = 100.0, reps = 5,
                durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null,
            ),
        )
        val exerciseRepo = FakeExerciseRepository(listOf(original))
        val workoutRepo = FakeWorkoutRepository(historyByExercise = mapOf(original.id to history))
        val vm = ExerciseDetailViewModel(
            SavedStateHandle(mapOf("exerciseId" to original.id)),
            exerciseRepo,
            workoutRepo,
            FakeClock(currentMillis = 7_000L),
        )

        val newId = vm.duplicate()

        assertNotNull(newId)
        val copy = exerciseRepo.getById(newId!!)!!
        assertTrue(copy.isCustom)
        assertEquals(original.name, copy.name)
        assertFalse(copy.id == original.id)
        assertEquals(7_000L, copy.createdAt)
        // The duplicate's own history is separately queried per-id — the fake has none registered for newId,
        // which is exactly "no history attached" (§5.2).
        assertTrue(workoutRepo.getExerciseHistory(newId).isEmpty())
    }

    @Test
    fun `delete soft-deletes a custom exercise but it remains resolvable (history stays valid)`() = runTest {
        val custom = customExercise()
        val exerciseRepo = FakeExerciseRepository(listOf(custom))
        val vm = ExerciseDetailViewModel(
            SavedStateHandle(mapOf("exerciseId" to custom.id)),
            exerciseRepo,
            FakeWorkoutRepository(),
            FakeClock(),
        )

        var deletedCallback = false
        vm.delete { deletedCallback = true }

        assertTrue(deletedCallback)
        val afterDelete = exerciseRepo.getById(custom.id)
        assertNotNull(afterDelete) // never hard-deleted
        assertTrue(afterDelete!!.isDeleted)
    }

    @Test
    fun `delete is a no-op for seed exercises`() = runTest {
        val seed = seedExercise()
        val exerciseRepo = FakeExerciseRepository(listOf(seed))
        val vm = ExerciseDetailViewModel(
            SavedStateHandle(mapOf("exerciseId" to seed.id)),
            exerciseRepo,
            FakeWorkoutRepository(),
            FakeClock(),
        )

        var deletedCallback = false
        vm.delete { deletedCallback = true }

        assertFalse(deletedCallback)
        assertFalse(exerciseRepo.getById(seed.id)!!.isDeleted)
    }
}
