package com.enil.logez.core.data.dao

import com.enil.logez.core.data.RoomDatabaseTestBase
import com.enil.logez.core.data.entity.ExerciseEntity
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric 4.13's max supported SDK is 34; this app targets 36 (Fiterval precedent — same pin).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExerciseDaoTest : RoomDatabaseTestBase() {
    private val dao get() = database.exerciseDao()

    private fun seedExercise(
        id: String = "seed-1",
        name: String = "Bench Press (Barbell)",
        secondary: List<MuscleGroup> = listOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS),
        eligible: Boolean = false,
    ) = ExerciseEntity(
        id = id,
        name = name,
        exerciseType = ExerciseType.WEIGHT_REPS,
        primaryMuscleGroup = MuscleGroup.CHEST,
        secondaryMuscleGroups = secondary,
        equipment = Equipment.BARBELL,
        instructions = "Step 1.\nStep 2.",
        mediaPath = null,
        isCustom = false,
        isBodyweightVolumeEligible = eligible,
        isDeleted = false,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    @Test
    fun `insertIgnore inserts new rows and reports conflicts as -1`() = runTest {
        val results = dao.insertIgnore(listOf(seedExercise(id = "a"), seedExercise(id = "b")))
        assertFalse(results.contains(-1L))

        val conflictResults = dao.insertIgnore(listOf(seedExercise(id = "a", name = "Renamed")))
        assertEquals(listOf(-1L), conflictResults)
        // Conflict means the row was NOT overwritten by insert — original name survives.
        assertEquals("Bench Press (Barbell)", dao.getById("a")!!.name)
    }

    @Test
    fun `secondaryMuscleGroups round-trips through the JSON converter`() = runTest {
        dao.insertIgnore(listOf(seedExercise(id = "a", secondary = listOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS))))
        val loaded = dao.getById("a")!!
        assertEquals(listOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS), loaded.secondaryMuscleGroups)
    }

    @Test
    fun `enum columns round-trip as names, not ordinals`() = runTest {
        dao.insertIgnore(listOf(seedExercise(id = "a")))
        val loaded = dao.getById("a")!!
        assertEquals(ExerciseType.WEIGHT_REPS, loaded.exerciseType)
        assertEquals(MuscleGroup.CHEST, loaded.primaryMuscleGroup)
        assertEquals(Equipment.BARBELL, loaded.equipment)
    }

    @Test
    fun `updateSeedFields changes correction-safe fields but never exercise_type`() = runTest {
        dao.insertIgnore(listOf(seedExercise(id = "a", eligible = false)))

        dao.updateSeedFields(
            id = "a",
            name = "Bench Press (Barbell) — corrected",
            primaryMuscleGroup = MuscleGroup.CHEST,
            secondaryMuscleGroups = listOf(MuscleGroup.TRICEPS),
            equipment = Equipment.BARBELL,
            instructions = "New instructions.",
            isBodyweightVolumeEligible = true,
            isDeleted = false,
            updatedAt = 2_000L,
        )

        val updated = dao.getById("a")!!
        assertEquals("Bench Press (Barbell) — corrected", updated.name)
        assertTrue(updated.isBodyweightVolumeEligible)
        assertEquals(ExerciseType.WEIGHT_REPS, updated.exerciseType) // unchanged — immutable
        assertEquals(2_000L, updated.updatedAt)
    }

    @Test
    fun `updateSeedFields never touches a custom exercise`() = runTest {
        val custom = seedExercise(id = "custom-1").copy(isCustom = true)
        dao.upsert(custom)

        dao.updateSeedFields(
            id = "custom-1",
            name = "Should not apply",
            primaryMuscleGroup = MuscleGroup.CHEST,
            secondaryMuscleGroups = emptyList(),
            equipment = Equipment.BARBELL,
            instructions = "x",
            isBodyweightVolumeEligible = false,
            isDeleted = false,
            updatedAt = 9_999L,
        )

        assertEquals(custom.name, dao.getById("custom-1")!!.name)
    }

    @Test
    fun `soft delete only affects custom exercises and is reflected in observeAllActive`() = runTest {
        dao.insertIgnore(listOf(seedExercise(id = "seed-a")))
        dao.upsert(seedExercise(id = "custom-a").copy(isCustom = true))

        dao.softDeleteCustom("seed-a", 5_000L) // WHERE is_custom = 1 — no-op on a seed row
        assertFalse(dao.getById("seed-a")!!.isDeleted)

        dao.softDeleteCustom("custom-a", 5_000L)
        assertTrue(dao.getById("custom-a")!!.isDeleted)
        assertEquals(listOf("seed-a"), dao.getAllActive().map { it.id })
    }

    @Test
    fun `count and seedCount reflect custom vs seed rows`() = runTest {
        dao.insertIgnore(listOf(seedExercise(id = "seed-a"), seedExercise(id = "seed-b")))
        dao.upsert(seedExercise(id = "custom-a").copy(isCustom = true))

        assertEquals(3, dao.count())
        assertEquals(2, dao.seedCount())
    }
}
