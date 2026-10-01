package com.enil.logez.core.data.repository

import com.enil.logez.core.data.RoomDatabaseTestBase
import com.enil.logez.core.data.entity.ExerciseEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.repository.RecentWorkout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Recent list through the real path: Room's query, [WorkoutRepositoryImpl]'s mapping (a null
 * limit becomes "no limit"), out as [RecentWorkout]. The view-model tests run on a fake repository,
 * so only this proves the GPS-run filter, the limit and the field mapping end to end.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutRepositoryImplRecentTest : RoomDatabaseTestBase() {
    private val repository get() = WorkoutRepositoryImpl(database.workoutDao(), database.analyticsDao())

    private suspend fun seed() {
        database.exerciseDao().insertIgnore(
            listOf(
                ExerciseEntity(
                    id = "ex-1", name = "Bench Press (Barbell)", exerciseType = ExerciseType.WEIGHT_REPS,
                    primaryMuscleGroup = MuscleGroup.CHEST, secondaryMuscleGroups = emptyList(),
                    equipment = Equipment.BARBELL, instructions = "x", mediaPath = null, isCustom = false,
                    isBodyweightVolumeEligible = false, isDeleted = false, createdAt = 0, updatedAt = 0,
                ),
            ),
        )
        fun workout(id: String, startedAt: Long, durationSeconds: Int, routineId: String? = null, kind: WorkoutKind = WorkoutKind.STRENGTH) = WorkoutEntity(
            id = id, routineId = routineId, title = "Title $id", notes = null, status = WorkoutStatus.COMPLETED,
            startedAt = startedAt, endedAt = startedAt + durationSeconds * 1_000L, durationSeconds = durationSeconds,
            createdAt = startedAt, updatedAt = startedAt, kind = kind,
        )
        fun we(id: String, workoutId: String, order: Int) =
            WorkoutExerciseEntity(id = id, workoutId = workoutId, exerciseId = "ex-1", orderIndex = order, supersetGroup = null, restTimerSeconds = null, notes = null)
        val dao = database.workoutDao()
        dao.insertFullWorkout(workout("a", 1_000L, 1_800), listOf(we("a1", "a", 0)), emptyList())
        dao.insertFullWorkout(workout("b", 2_000L, 2_700, routineId = null), listOf(we("b1", "b", 0), we("b2", "b", 1)), emptyList())
        dao.insertFullWorkout(workout("run", 3_000L, 900, kind = WorkoutKind.GPS_TRACKED), listOf(we("r1", "run", 0)), emptyList())
        dao.insertFullWorkout(workout("c", 4_000L, 3_600), listOf(we("c1", "c", 0), we("c2", "c", 1), we("c3", "c", 2)), emptyList())
        dao.insertFullWorkout(workout("d", 5_000L, 600), listOf(we("d1", "d", 0)), emptyList())
    }

    @Test
    fun `no limit lists every finished strength workout newest first, with each one's duration and exercise count`() = runTest {
        seed()

        val all = repository.observeRecentStrengthWorkouts(null).first()

        assertEquals(listOf("d", "c", "b", "a"), all.map { it.workoutId })
        assertEquals(listOf(600, 3_600, 2_700, 1_800), all.map { it.durationSeconds })
        assertEquals(listOf(1, 3, 2, 1), all.map { it.exerciseCount })
        assertEquals(RecentWorkout("c", null, "Title c", 4_000L, 3_600, 3), all[1])
    }

    @Test
    fun `a limit of 3 gives the newest three strength workouts, with the GPS run skipped`() = runTest {
        seed()

        assertEquals(listOf("d", "c", "b"), repository.observeRecentStrengthWorkouts(3).first().map { it.workoutId })
    }
}
