package com.enil.logez.core.data.repository

import com.enil.logez.core.data.RoomDatabaseTestBase
import com.enil.logez.core.data.dao.ExerciseDao
import com.enil.logez.core.data.entity.BodyMeasurementEntity
import com.enil.logez.core.data.entity.DailyWellnessTotalEntity
import com.enil.logez.core.data.entity.ExerciseEntity
import com.enil.logez.core.data.entity.GoalDefinitionEntity
import com.enil.logez.core.data.entity.ProgressPhotoEntity
import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.RoutineFolderEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.GoalMetric
import com.enil.logez.core.domain.model.GoalPeriod
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.WorkoutStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomUserDataProbeTest : RoomDatabaseTestBase() {
    private val probe get() = RoomUserDataProbe(database.backupDao(), database.exerciseDao())

    private fun exercise(id: String, isCustom: Boolean = false, isDeleted: Boolean = false) = ExerciseEntity(
        id = id, name = "Exercise $id", exerciseType = ExerciseType.WEIGHT_REPS,
        primaryMuscleGroup = MuscleGroup.CHEST, secondaryMuscleGroups = emptyList(),
        equipment = Equipment.BARBELL, instructions = "", mediaPath = null,
        isCustom = isCustom, isBodyweightVolumeEligible = false, isDeleted = isDeleted,
        createdAt = 1, updatedAt = 1,
    )

    private suspend fun seedLibrary() {
        database.exerciseDao().insertIgnore((1..5).map { exercise("seed-$it") })
    }

    @Test
    fun `an empty database has no user content`() = runTest {
        assertFalse(probe.hasUserContent())
    }

    @Test
    fun `a database holding only the seeded library has no user content`() = runTest {
        seedLibrary()
        assertFalse(probe.hasUserContent())
    }

    @Test
    fun `derived tables alone are not user content`() = runTest {
        seedLibrary()
        database.backupDao().insertWellnessTotals(
            listOf(DailyWellnessTotalEntity(date = "2026-01-05", steps = 4_000L, caloriesBurned = null, updatedAt = 1L)),
        )
        assertFalse(probe.hasUserContent())
    }

    @Test
    fun `a custom exercise is user content`() = runTest {
        seedLibrary()
        database.exerciseDao().upsert(exercise("mine", isCustom = true))
        assertTrue(probe.hasUserContent())
    }

    @Test
    fun `an edited seed exercise is user content`() = runTest {
        seedLibrary()
        // Saving an edit to a seeded exercise rewrites the same id with is_custom = 1.
        database.exerciseDao().upsert(exercise("seed-3", isCustom = true))
        assertTrue(probe.hasUserContent())
    }

    @Test
    fun `a soft-deleted custom exercise still counts`() = runTest {
        database.exerciseDao().upsert(exercise("mine", isCustom = true, isDeleted = true))
        assertTrue(probe.hasUserContent())
    }

    @Test
    fun `customCount counts only is_custom rows`() = runTest {
        seedLibrary()
        database.exerciseDao().upsert(exercise("mine", isCustom = true))
        database.exerciseDao().upsert(exercise("seed-2", isCustom = true))
        assertEquals(2, database.exerciseDao().customCount())
    }

    @Test
    fun `the probe's exercise signal is customCount alone, never count minus seedCount`() = runTest {
        // Two separate SELECTs can straddle the seed commit (see RoomUserDataProbe); this fails if
        // the probe goes back to them.
        val real = database.exerciseDao()
        val guarded = object : ExerciseDao by real {
            override suspend fun count(): Int = error("the probe must not use count()")
            override suspend fun seedCount(): Int = error("the probe must not use seedCount()")
        }
        seedLibrary()
        database.exerciseDao().upsert(exercise("mine", isCustom = true))

        assertTrue(RoomUserDataProbe(database.backupDao(), guarded).hasUserContent())
    }

    @Test
    fun `a routine folder is user content`() = runTest {
        database.backupDao().insertRoutineFolders(listOf(RoutineFolderEntity("f1", "Push", 0, 1, 1)))
        assertTrue(probe.hasUserContent())
    }

    @Test
    fun `a routine is user content`() = runTest {
        database.backupDao().insertRoutines(listOf(RoutineEntity("r1", null, "Legs", null, 0, 1, 1)))
        assertTrue(probe.hasUserContent())
    }

    @Test
    fun `a goal is user content`() = runTest {
        database.backupDao().insertGoals(
            listOf(GoalDefinitionEntity("g1", GoalMetric.entries.first(), GoalPeriod.entries.first(), 3.0, 1, 1)),
        )
        assertTrue(probe.hasUserContent())
    }

    @Test
    fun `a body measurement is user content`() = runTest {
        database.backupDao().insertBodyMeasurements(
            listOf(
                BodyMeasurementEntity(
                    date = "2026-01-05", weightKg = 80.0, leanMassKg = null, fatPercent = null,
                    neckCm = null, shoulderCm = null, chestCm = null, leftBicepCm = null, rightBicepCm = null,
                    leftForearmCm = null, rightForearmCm = null, abdomenCm = null, waistCm = null, hipsCm = null,
                    leftThighCm = null, rightThighCm = null, leftCalfCm = null, rightCalfCm = null, updatedAt = 1,
                ),
            ),
        )
        assertTrue(probe.hasUserContent())
    }

    @Test
    fun `a progress photo is user content`() = runTest {
        database.backupDao().insertProgressPhotos(listOf(ProgressPhotoEntity("p1", "2026-01-05", "photos/p1.jpg", 1)))
        assertTrue(probe.hasUserContent())
    }

    @Test
    fun `an unfinished workout alone is user content`() = runTest {
        seedLibrary()
        database.backupDao().insertWorkoutsBulk(
            listOf(WorkoutEntity("w1", null, "Workout", null, WorkoutStatus.IN_PROGRESS, 100, null, 0, 100, 100)),
        )
        assertTrue(probe.hasUserContent())
    }

    @Test
    fun `a completed workout is user content`() = runTest {
        database.backupDao().insertWorkoutsBulk(
            listOf(WorkoutEntity("w1", null, "Workout", null, WorkoutStatus.COMPLETED, 100, 200, 100, 100, 200)),
        )
        assertTrue(probe.hasUserContent())
    }
}
