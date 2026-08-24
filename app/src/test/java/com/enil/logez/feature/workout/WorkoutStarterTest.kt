package com.enil.logez.feature.workout

import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.RoutineExerciseEntity
import com.enil.logez.core.data.entity.RoutineSetEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
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

    @Test
    fun `startFromWorkout pre-fills the logged values, not routine targets, and carries the routine link`() = runTest {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(
                WorkoutEntity(
                    id = "w-source", routineId = "r1", title = "Push Day", notes = "felt great",
                    status = WorkoutStatus.COMPLETED, startedAt = 1_000L, endedAt = 5_000L,
                    durationSeconds = 4000, createdAt = 1_000L, updatedAt = 1_000L,
                ),
            ),
            exercises = listOf(
                WorkoutExerciseEntity(id = "we1", workoutId = "w-source", exerciseId = "ex-1", orderIndex = 0, supersetGroup = 1, restTimerSeconds = 90, notes = "shoulder tight"),
            ),
            sets = listOf(
                WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 102.5, reps = 6, durationSeconds = null, distanceMeters = null, rpe = 8.5, customMetric = null, isCompleted = true, completedAt = 2_000L),
                WorkoutSetEntity(id = "s2", workoutExerciseId = "we1", orderIndex = 1, setType = SetType.NORMAL, weightKg = 102.5, reps = 5, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null),
            ),
        )
        val starter = WorkoutStarter(workoutRepo, FakeRoutineRepository(), FakeClock(currentMillis = 9_000L))

        val newId = starter.startFromWorkout("w-source")

        val workout = workoutRepo.getById(newId)!!
        assertEquals("Push Day", workout.title)
        assertEquals("r1", workout.routineId) // stays routine-linked, same Update-Routine prompt applies later
        assertEquals(WorkoutStatus.IN_PROGRESS, workout.status)
        assertNull(workout.notes) // last session's narrative is not this session's

        val exercises = workoutRepo.getExercisesForWorkout(newId)
        assertEquals(1, exercises.size)
        assertNotEquals("we1", exercises[0].id) // fresh id, no shared identity with the source row
        assertEquals(1, exercises[0].supersetGroup)
        assertEquals(90, exercises[0].restTimerSeconds)
        assertNull(exercises[0].notes) // session note doesn't carry over either

        val sets = workoutRepo.getSetsForWorkoutExercise(exercises[0].id).sortedBy { it.orderIndex }
        assertEquals(2, sets.size)
        assertEquals(102.5, sets[0].weightKg)
        assertEquals(6, sets[0].reps) // pre-filled from what was actually logged, not a routine target
        assertEquals(5, sets[1].reps) // even the never-completed set's value carries forward as a starting point
        assertEquals(false, sets[0].isCompleted) // a fresh session — nothing pre-checked
        assertNull(sets[0].rpe) // this session's own RPE, not last time's
    }

    @Test
    fun `startFromWorkout falls back to an empty session when the source workout is gone`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val starter = WorkoutStarter(workoutRepo, FakeRoutineRepository(), FakeClock(currentMillis = 1_000L))

        val id = starter.startFromWorkout("does-not-exist")

        val workout = workoutRepo.getById(id)!!
        assertEquals(WorkoutStatus.IN_PROGRESS, workout.status)
        assertNull(workout.routineId)
    }

    @Test
    fun `startFromWorkoutOrConflict surfaces the existing workout instead of creating a second one`() = runTest {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(
                WorkoutEntity(id = "w-source", routineId = null, title = "Legs", notes = null, status = WorkoutStatus.COMPLETED, startedAt = 1_000L, endedAt = 2_000L, durationSeconds = 1000, createdAt = 1_000L, updatedAt = 1_000L),
            ),
        )
        val starter = WorkoutStarter(workoutRepo, FakeRoutineRepository(), FakeClock())
        val firstId = starter.startEmpty()

        val result = starter.startFromWorkoutOrConflict("w-source")

        assertEquals(StartResult.AlreadyInProgress(firstId), result)
    }
}
