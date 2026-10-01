package com.enil.logez.core.data.dao

import com.enil.logez.core.data.RoomDatabaseTestBase
import com.enil.logez.core.data.entity.ExerciseEntity
import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.model.WorkoutStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric 4.13's max supported SDK is 34; this app targets 36 (Fiterval precedent — same pin).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutDaoTest : RoomDatabaseTestBase() {
    private val dao get() = database.workoutDao()
    private val exerciseDao get() = database.exerciseDao()
    private val routineDao get() = database.routineDao()

    private suspend fun anExercise(id: String = "ex-1") {
        exerciseDao.insertIgnore(
            listOf(
                ExerciseEntity(
                    id = id, name = "Bench Press (Barbell)", exerciseType = ExerciseType.WEIGHT_REPS,
                    primaryMuscleGroup = MuscleGroup.CHEST, secondaryMuscleGroups = emptyList(),
                    equipment = Equipment.BARBELL, instructions = "x", mediaPath = null, isCustom = false,
                    isBodyweightVolumeEligible = false, isDeleted = false, createdAt = 0, updatedAt = 0,
                ),
            ),
        )
    }

    private fun aWorkout(id: String, status: WorkoutStatus, routineId: String? = null) = WorkoutEntity(
        id = id, routineId = routineId, title = "Push Day", notes = null, status = status,
        startedAt = 1_000L, endedAt = if (status == WorkoutStatus.COMPLETED) 4_600L else null,
        durationSeconds = 3_600, createdAt = 1_000L, updatedAt = 1_000L,
    )

    @Test
    fun `getInProgress finds the single IN_PROGRESS workout`() = runTest {
        dao.upsertWorkout(aWorkout("w1", WorkoutStatus.COMPLETED))
        dao.upsertWorkout(aWorkout("w2", WorkoutStatus.IN_PROGRESS))

        assertEquals("w2", dao.getInProgress()?.id)
    }

    @Test
    fun `deleting a workout cascades to its exercises and sets`() = runTest {
        anExercise()
        dao.insertFullWorkout(
            workout = aWorkout("w1", WorkoutStatus.COMPLETED),
            exercises = listOf(
                WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null),
            ),
            sets = listOf(
                WorkoutSetEntity(id = "ws1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 100.0, reps = 5, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = true, completedAt = 2_000L),
            ),
        )

        dao.deleteWorkoutById("w1")

        assertTrue(dao.getExercisesForWorkout("w1").isEmpty())
        assertTrue(dao.getSetsForWorkoutExercise("we1").isEmpty())
    }

    @Test
    fun `a timer mode is written to one workout exercise only, and Replace Exercise puts the row back on a stopwatch`() = runTest {
        anExercise()
        dao.insertFullWorkout(
            workout = aWorkout("w1", WorkoutStatus.IN_PROGRESS),
            exercises = listOf(
                WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = 90, notes = "n"),
                WorkoutExerciseEntity(id = "we2", workoutId = "w1", exerciseId = "ex-1", orderIndex = 1, supersetGroup = null, restTimerSeconds = null, notes = null),
            ),
            sets = emptyList(),
        )

        dao.updateWorkoutExerciseTimerMode("we1", "COUNTDOWN")

        val byId = dao.getExercisesForWorkout("w1").associateBy { it.id }
        assertEquals("COUNTDOWN", byId.getValue("we1").timerMode)
        assertNull(byId.getValue("we2").timerMode) // same exercise, same workout, untouched
        assertEquals(90, byId.getValue("we1").restTimerSeconds)
        assertEquals("n", byId.getValue("we1").notes)

        dao.replaceWorkoutExerciseExercise("we1", "ex-1", emptyList())

        assertNull(dao.getExercisesForWorkout("w1").first { it.id == "we1" }.timerMode)
    }

    @Test
    fun `a set timed by a countdown exports the same CSV row as the same set on a stopwatch`() = runTest {
        anExercise()
        fun workoutWith(workoutId: String, exerciseId: String, setId: String, timerMode: String?) = Triple(
            aWorkout(workoutId, WorkoutStatus.COMPLETED),
            listOf(WorkoutExerciseEntity(id = exerciseId, workoutId = workoutId, exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null, timerMode = timerMode)),
            listOf(WorkoutSetEntity(id = setId, workoutExerciseId = exerciseId, orderIndex = 0, setType = SetType.NORMAL, weightKg = null, reps = null, durationSeconds = 90, distanceMeters = null, rpe = null, customMetric = null, isCompleted = true, completedAt = 2_000L)),
        )
        val (wa, ea, sa) = workoutWith("wa", "wea", "sa", null)
        val (wb, eb, sb) = workoutWith("wb", "web", "sb", "COUNTDOWN")
        dao.insertFullWorkout(wa, ea, sa)
        dao.insertFullWorkout(wb, eb, sb)

        val rows = dao.getWorkoutCsvRows()

        assertEquals(2, rows.size)
        assertEquals(90, rows[0].durationSeconds)
        assertEquals(rows[0], rows[1])
    }

    @Test
    fun `deleting a routine SETs NULL on the workouts started from it`() = runTest {
        routineDao.upsertRoutine(RoutineEntity(id = "r1", folderId = null, name = "Push Day", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0))
        dao.upsertWorkout(aWorkout("w1", WorkoutStatus.COMPLETED, routineId = "r1"))

        routineDao.deleteRoutineById("r1")

        assertNull(dao.getById("w1")?.routineId)
    }

    @Test
    fun `getStatRowsForExercise joins completed workouts only and includes rpe`() = runTest {
        anExercise()
        dao.insertFullWorkout(
            workout = aWorkout("w-completed", WorkoutStatus.COMPLETED),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w-completed", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(WorkoutSetEntity(id = "ws1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 100.0, reps = 5, durationSeconds = null, distanceMeters = null, rpe = 8.5, customMetric = null, isCompleted = true, completedAt = 2_000L)),
        )
        dao.insertFullWorkout(
            workout = aWorkout("w-in-progress", WorkoutStatus.IN_PROGRESS),
            exercises = listOf(WorkoutExerciseEntity(id = "we2", workoutId = "w-in-progress", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(WorkoutSetEntity(id = "ws2", workoutExerciseId = "we2", orderIndex = 0, setType = SetType.NORMAL, weightKg = 999.0, reps = 1, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = true, completedAt = 2_000L)),
        )

        val rows = dao.getStatRowsForExercise("ex-1")

        assertEquals(1, rows.size) // the IN_PROGRESS workout's set never appears
        assertEquals("ws1", rows.first().setId)
        assertEquals(8.5, rows.first().rpe)
    }

    @Test
    fun `getSetsWithExerciseForWorkout distinguishes two blocks of the same exercise`() = runTest {
        anExercise()
        dao.insertFullWorkout(
            workout = aWorkout("w1", WorkoutStatus.COMPLETED),
            exercises = listOf(
                WorkoutExerciseEntity(id = "we-main", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null),
                WorkoutExerciseEntity(id = "we-burnout", workoutId = "w1", exerciseId = "ex-1", orderIndex = 1, supersetGroup = null, restTimerSeconds = null, notes = null),
            ),
            sets = listOf(
                aSet("s-main", "we-main", 0, 100.0, 5),
                aSet("s-burn", "we-burnout", 0, 40.0, 20),
            ),
        )

        val rows = dao.getSetsWithExerciseForWorkout("w1")

        // Both sets share exerciseId AND orderIndex — only the block identity tells them apart.
        assertEquals(listOf("we-main", "we-burnout"), rows.map { it.workoutExerciseId })
        assertEquals(listOf(0, 1), rows.map { it.exerciseOrderIndex })
    }

    @Test
    fun `countCompletedWorkoutsUpTo gives tied timestamps distinct ordinals`() = runTest {
        // A plain `started_at <= x` counted both sides of a tie, so two workouts saved at the same
        // instant both reported N and the ordinal N-1 was never assigned to anything.
        dao.upsertWorkout(aWorkout("w-a", WorkoutStatus.COMPLETED))
        dao.upsertWorkout(aWorkout("w-b", WorkoutStatus.COMPLETED))

        val a = dao.countCompletedWorkoutsUpTo(1_000L, "w-a")
        val b = dao.countCompletedWorkoutsUpTo(1_000L, "w-b")

        assertEquals(1, a)
        assertEquals(2, b)
    }

    @Test
    fun `countCompletedWorkoutsUpTo ignores later and non-completed workouts`() = runTest {
        dao.upsertWorkout(aWorkout("w-old", WorkoutStatus.COMPLETED).copy(startedAt = 500L))
        dao.upsertWorkout(aWorkout("w-this", WorkoutStatus.COMPLETED))
        dao.upsertWorkout(aWorkout("w-later", WorkoutStatus.COMPLETED).copy(startedAt = 9_000L))
        dao.upsertWorkout(aWorkout("w-live", WorkoutStatus.IN_PROGRESS))

        assertEquals(2, dao.countCompletedWorkoutsUpTo(1_000L, "w-this"))
    }

    private fun aSet(id: String, workoutExerciseId: String, orderIndex: Int, weightKg: Double, reps: Int) =
        WorkoutSetEntity(
            id = id, workoutExerciseId = workoutExerciseId, orderIndex = orderIndex, setType = SetType.NORMAL,
            weightKg = weightKg, reps = reps, durationSeconds = null, distanceMeters = null, rpe = null,
            customMetric = null, isCompleted = true, completedAt = 2_000L,
        )

    @Test
    fun `the Recent query lists finished strength workouts newest first with their exercise counts, drops GPS runs, and honours the limit`() = runTest {
        anExercise()
        fun we(id: String, workoutId: String, order: Int) =
            WorkoutExerciseEntity(id = id, workoutId = workoutId, exerciseId = "ex-1", orderIndex = order, supersetGroup = null, restTimerSeconds = null, notes = null)
        dao.insertFullWorkout(aWorkout("old", WorkoutStatus.COMPLETED).copy(startedAt = 1_000L), listOf(we("o1", "old", 0), we("o2", "old", 1)), emptyList())
        dao.insertFullWorkout(aWorkout("run", WorkoutStatus.COMPLETED).copy(startedAt = 9_000L, kind = WorkoutKind.GPS_TRACKED), listOf(we("r1", "run", 0)), emptyList())
        dao.insertFullWorkout(aWorkout("live", WorkoutStatus.IN_PROGRESS).copy(startedAt = 8_000L), listOf(we("l1", "live", 0)), emptyList())
        dao.insertFullWorkout(aWorkout("mid", WorkoutStatus.COMPLETED).copy(startedAt = 3_000L, routineId = null), listOf(we("m1", "mid", 0)), emptyList())
        dao.insertFullWorkout(aWorkout("empty", WorkoutStatus.COMPLETED).copy(startedAt = 5_000L), emptyList(), emptyList())
        dao.insertFullWorkout(aWorkout("older", WorkoutStatus.COMPLETED).copy(startedAt = 500L), listOf(we("x1", "older", 0)), emptyList())

        val all = dao.observeRecentStrengthWorkouts(-1).first()
        assertEquals(listOf("empty", "mid", "old", "older"), all.map { it.workoutId })
        assertEquals(listOf(0, 1, 2, 1), all.map { it.exerciseCount })
        assertEquals(listOf("empty", "mid", "old"), dao.observeRecentStrengthWorkouts(3).first().map { it.workoutId })
    }
}
