package com.enil.logez.feature.workout

import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.RoutineExerciseEntity
import com.enil.logez.core.data.entity.RoutineSetEntity
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeRoutineRepository
import com.enil.logez.fakes.FakeWorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutStarterTest {
    @Test
    fun `startEmpty creates an IN_PROGRESS workout with no exercises`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val starter = WorkoutStarter(workoutRepo, FakeRoutineRepository(), FakeClock(currentMillis = 1_000L))

        val id = starter.startEmpty()

        val workout = workoutRepo.getById(id)!!
        assertEquals(WorkoutStatus.IN_PROGRESS, workout.status)
        assertNull(workout.routineId)
        assertEquals(1_000L, workout.startedAt)
        assertEquals(0, workoutRepo.getExercisesForWorkout(id).size)
    }

    @Test
    fun `startFromRoutine copies structure and exact-rep targets, leaving rep-range sets blank`() = runTest {
        val routineRepo = FakeRoutineRepository(
            routines = listOf(RoutineEntity(id = "r1", folderId = null, name = "Push Day", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0)),
            exercises = listOf(RoutineExerciseEntity(id = "re1", routineId = "r1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = 45, notes = "form cue")),
            sets = listOf(
                RoutineSetEntity(id = "s1", routineExerciseId = "re1", orderIndex = 0, setType = SetType.NORMAL, targetWeightKg = 60.0, targetReps = 8, targetRepRangeMin = null, targetRepRangeMax = null, targetDurationSeconds = null, targetDistanceMeters = null),
                RoutineSetEntity(id = "s2", routineExerciseId = "re1", orderIndex = 1, setType = SetType.NORMAL, targetWeightKg = 60.0, targetReps = null, targetRepRangeMin = 6, targetRepRangeMax = 8, targetDurationSeconds = null, targetDistanceMeters = null),
            ),
        )
        val workoutRepo = FakeWorkoutRepository()
        val starter = WorkoutStarter(workoutRepo, routineRepo, FakeClock(currentMillis = 2_000L))

        val workoutId = starter.startFromRoutine("r1")

        val workout = workoutRepo.getById(workoutId)!!
        assertEquals("Push Day", workout.title)
        assertEquals("r1", workout.routineId)
        assertEquals(WorkoutStatus.IN_PROGRESS, workout.status)

        val exercises = workoutRepo.getExercisesForWorkout(workoutId)
        assertEquals(1, exercises.size)
        assertNotEquals("re1", exercises[0].id) // fresh id, not the routine's row id
        assertEquals(45, exercises[0].restTimerSeconds)
        assertEquals("form cue", exercises[0].notes)

        val sets = workoutRepo.getSetsForWorkoutExercise(exercises[0].id).sortedBy { it.orderIndex }
        assertEquals(2, sets.size)
        assertEquals(8, sets[0].reps) // exact target carried over
        assertNull(sets[1].reps) // rep-range target starts blank, not a fabricated exact value
        assertEquals(60.0, sets[0].weightKg)
        assertEquals(false, sets[0].isCompleted)
    }

    @Test
    fun `startEmptyOrConflict surfaces the existing workout instead of creating a second one`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val starter = WorkoutStarter(workoutRepo, FakeRoutineRepository(), FakeClock())
        val firstId = starter.startEmpty()

        val result = starter.startEmptyOrConflict()

        assertEquals(StartResult.AlreadyInProgress(firstId), result)
    }

    @Test
    fun `discardInProgress removes the IN_PROGRESS workout so a new one can start`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val starter = WorkoutStarter(workoutRepo, FakeRoutineRepository(), FakeClock())
        starter.startEmpty()

        starter.discardInProgress()
        val result = starter.startEmptyOrConflict()

        assertNotNull((result as? StartResult.Started)?.workoutId)
    }
}
