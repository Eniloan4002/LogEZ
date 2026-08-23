package com.enil.logez.core.data.seed

import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PHASE2_PLAN.md §10.5 — runs against the raw seed asset, a plain JVM test with no Room/Android
 * dependency. Validates the CURRENT placeholder file (§7.9's frozen 400-exercise library is a
 * later sub-step); once that lands, the eligible-ID-set and entry-count assertions below get
 * updated to the frozen literals — the structural checks themselves don't change.
 */
class SeedLibraryValidationTest {
    private val json = Json { ignoreUnknownKeys = true }

    /** Exactly the 6 rows this placeholder deliberately marks eligible (see the JSON's own comments). */
    private val expectedEligibleIds = setOf(
        "seed-pull-up", "seed-chin-up", "seed-dips", "seed-handstand-push-up",
        "seed-pull-up-weighted", "seed-pull-up-assisted",
    )

    private fun loadSeedFile(): ExerciseSeedFile {
        val file = File("src/main/assets/seed/exercises_seed.json")
        assertTrue("seed asset not found at ${file.absolutePath}", file.exists())
        return json.decodeFromString(ExerciseSeedFile.serializer(), file.readText())
    }

    @Test
    fun `file parses and every entry has a well-formed id and non-blank fields`() {
        val seedFile = loadSeedFile()
        assertTrue(seedFile.exercises.isNotEmpty())
        seedFile.exercises.forEach { e ->
            assertTrue("blank id", e.id.isNotBlank())
            assertTrue("blank name for ${e.id}", e.name.isNotBlank())
            assertTrue("no instructions for ${e.id}", e.instructions.isNotEmpty())
            e.instructions.forEach { step -> assertTrue("blank instruction step in ${e.id}", step.isNotBlank()) }
        }
    }

    @Test
    fun `every enum field maps to a real domain enum value`() {
        loadSeedFile().exercises.forEach { e ->
            ExerciseType.valueOf(e.exerciseType) // throws IllegalArgumentException on a bad value
            MuscleGroup.valueOf(e.primaryMuscleGroup)
            e.secondaryMuscleGroups.forEach { MuscleGroup.valueOf(it) }
            Equipment.valueOf(e.equipment)
        }
    }

    @Test
    fun `ids and names are unique`() {
        val exercises = loadSeedFile().exercises
        assertEquals(exercises.size, exercises.map { it.id }.distinct().size)
        assertEquals(exercises.size, exercises.map { it.name }.distinct().size)
    }

    @Test
    fun `isBodyweightVolumeEligible is set on exactly the intended rows`() {
        val actual = loadSeedFile().exercises.filter { it.isBodyweightVolumeEligible }.map { it.id }.toSet()
        assertEquals(expectedEligibleIds, actual)
    }

    @Test
    fun `every eligible row is REPS_ONLY or a weighted-assisted variant, never WEIGHT_REPS`() {
        loadSeedFile().exercises.filter { it.isBodyweightVolumeEligible }.forEach { e ->
            val type = ExerciseType.valueOf(e.exerciseType)
            assertTrue(
                "${e.id} is eligible but has type $type",
                type == ExerciseType.REPS_ONLY || type == ExerciseType.BODYWEIGHT_WEIGHTED || type == ExerciseType.BODYWEIGHT_ASSISTED,
            )
        }
    }
}
