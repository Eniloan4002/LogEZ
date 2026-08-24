package com.enil.logez.feature.history

import com.enil.logez.core.common.Clock
import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.RoutineExerciseEntity
import com.enil.logez.core.data.entity.RoutineSetEntity
import com.enil.logez.core.domain.repository.RoutineRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PHASE2_PLAN.md §5.2 Workout Detail "Save as Routine": "creates a routine from the exercise/set
 * structure, opening the routine editor pre-filled."
 *
 * The session's logged values become the routine's targets, which is the whole point — a routine
 * built from a workout should start you where you actually finished. Rep ranges are the one thing
 * a session cannot express, so they are simply absent rather than invented from the reps performed
 * (the same rule §8.10 applies when a finished workout updates its source routine's values).
 */
@Singleton
class WorkoutToRoutineConverter @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val routineRepository: RoutineRepository,
    private val clock: Clock,
) {
    /** Returns the new routine's id, or null if the workout has vanished. */
    suspend fun convert(workoutId: String): String? {
        val workout = workoutRepository.getById(workoutId) ?: return null
        val workoutExercises = workoutRepository.getExercisesForWorkout(workoutId).sortedBy { it.orderIndex }
        if (workoutExercises.isEmpty()) return null

        val now = clock.now().toEpochMilliseconds()
        val routineId = UUID.randomUUID().toString()

        val routineExercises = workoutExercises.mapIndexed { index, we ->
            RoutineExerciseEntity(
                id = UUID.randomUUID().toString(),
                routineId = routineId,
                exerciseId = we.exerciseId,
                orderIndex = index,
                supersetGroup = we.supersetGroup,
                restTimerSeconds = we.restTimerSeconds,
                // The session note ("shoulder tight today") is commentary on one workout, not a
                // standing coaching cue — WorkoutExerciseEntity documents the two as distinct, and
                // the finish flow's structural rewrite makes the same call.
                notes = null,
            )
        }

        val routineSets = workoutExercises.flatMapIndexed { index: Int, we ->
            workoutRepository.getSetsForWorkoutExercise(we.id)
                .sortedBy { it.orderIndex }
                .mapIndexed { setIndex, ws ->
                    RoutineSetEntity(
                        id = UUID.randomUUID().toString(),
                        routineExerciseId = routineExercises[index].id,
                        orderIndex = setIndex,
                        setType = ws.setType,
                        targetWeightKg = ws.weightKg,
                        targetReps = ws.reps,
                        targetRepRangeMin = null,
                        targetRepRangeMax = null,
                        targetDurationSeconds = ws.durationSeconds,
                        targetDistanceMeters = ws.distanceMeters,
                    )
                }
        }

        routineRepository.createRoutineAtTop(
            RoutineEntity(
                id = routineId,
                folderId = null,
                name = workout.title,
                notes = null,
                orderIndex = 0,
                createdAt = now,
                updatedAt = now,
            ),
            dropOrphanSupersets(routineExercises),
            routineSets,
        )
        return routineId
    }

    /**
     * A superset group with one member is not a valid state anywhere in the app. It can arise here
     * because the workout's own save purged a partner whose sets were all left uncompleted.
     */
    private fun dropOrphanSupersets(exercises: List<RoutineExerciseEntity>): List<RoutineExerciseEntity> {
        val memberCount = exercises.mapNotNull { it.supersetGroup }.groupingBy { it }.eachCount()
        return exercises.map {
            if (it.supersetGroup != null && (memberCount[it.supersetGroup] ?: 0) < 2) it.copy(supersetGroup = null) else it
        }
    }
}
