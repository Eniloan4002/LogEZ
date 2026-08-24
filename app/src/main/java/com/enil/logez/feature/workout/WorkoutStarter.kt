package com.enil.logez.feature.workout

import com.enil.logez.core.common.Clock
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.repository.RoutineRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * §5.1.1 "Start Empty Workout"/"Start Routine": shared between the Workout tab and Routine
 * Detail (both entry points per the plan). Enforces the spine rule — at most one `IN_PROGRESS`
 * workout at a time — by having the caller check [getInProgressId] first and, if non-null,
 * show the Resume/Discard dialog before calling [startEmpty]/[startFromRoutine].
 */
@Singleton
class WorkoutStarter @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val routineRepository: RoutineRepository,
    private val clock: Clock,
) {
    suspend fun getInProgressId(): String? = workoutRepository.getInProgress()?.id

    suspend fun discardInProgress() {
        workoutRepository.getInProgress()?.let { workoutRepository.deleteById(it.id) }
    }

    suspend fun startEmpty(): String {
        val now = clock.now().toEpochMilliseconds()
        val id = UUID.randomUUID().toString()
        workoutRepository.insertFullWorkout(
            WorkoutEntity(
                id = id, routineId = null, title = "New Workout", notes = null, status = WorkoutStatus.IN_PROGRESS,
                startedAt = now, endedAt = null, durationSeconds = 0, createdAt = now, updatedAt = now,
            ),
            emptyList(),
            emptyList(),
        )
        return id
    }

    /** Structure + targets copy from the routine template (§5.1.1) — rep-range targets start blank, not a fake exact value. */
    suspend fun startFromRoutine(routineId: String): String {
        val routine = routineRepository.getRoutineById(routineId) ?: return startEmpty()
        val now = clock.now().toEpochMilliseconds()
        val workoutId = UUID.randomUUID().toString()
        val routineExercises = routineRepository.getExercisesForRoutine(routineId)
        val idMap = routineExercises.associate { it.id to UUID.randomUUID().toString() }

        val workoutExercises = routineExercises.map { re ->
            WorkoutExerciseEntity(
                id = idMap.getValue(re.id), workoutId = workoutId, exerciseId = re.exerciseId, orderIndex = re.orderIndex,
                supersetGroup = re.supersetGroup, restTimerSeconds = re.restTimerSeconds, notes = re.notes,
            )
        }
        val workoutSets = routineExercises.flatMap { re ->
            routineRepository.getSetsForRoutineExercise(re.id).map { rs ->
                WorkoutSetEntity(
                    id = UUID.randomUUID().toString(), workoutExerciseId = idMap.getValue(re.id), orderIndex = rs.orderIndex,
                    setType = rs.setType, weightKg = rs.targetWeightKg, reps = rs.targetReps, durationSeconds = rs.targetDurationSeconds,
                    distanceMeters = rs.targetDistanceMeters, rpe = null, customMetric = null, isCompleted = false, completedAt = null,
                )
            }
        }

        workoutRepository.insertFullWorkout(
            WorkoutEntity(
                id = workoutId, routineId = routineId, title = routine.name, notes = null, status = WorkoutStatus.IN_PROGRESS,
                startedAt = now, endedAt = null, durationSeconds = 0, createdAt = now, updatedAt = now,
            ),
            workoutExercises,
            workoutSets,
        )
        return workoutId
    }

    /**
     * §5.2 Workout Detail "Copy Workout": pre-fills a new IN_PROGRESS session from a past
     * workout's actually-logged values (not routine targets) — repeating the same session rather
     * than starting a blank one. `routineId` carries over: a copy of a routine-based workout stays
     * routine-based, so the same Update-Routine-Values prompt logic applies when it's finished.
     * The session note and RPE are the session's own commentary, not last time's — both start
     * blank rather than carrying stale text forward.
     */
    suspend fun startFromWorkout(sourceWorkoutId: String): String {
        val source = workoutRepository.getById(sourceWorkoutId) ?: return startEmpty()
        val now = clock.now().toEpochMilliseconds()
        val workoutId = UUID.randomUUID().toString()
        val sourceExercises = workoutRepository.getExercisesForWorkout(sourceWorkoutId)
        val idMap = sourceExercises.associate { it.id to UUID.randomUUID().toString() }

        val workoutExercises = sourceExercises.map { we ->
            WorkoutExerciseEntity(
                id = idMap.getValue(we.id), workoutId = workoutId, exerciseId = we.exerciseId, orderIndex = we.orderIndex,
                supersetGroup = we.supersetGroup, restTimerSeconds = we.restTimerSeconds, notes = null,
            )
        }
        val workoutSets = sourceExercises.flatMap { we ->
            workoutRepository.getSetsForWorkoutExercise(we.id).map { ws ->
                WorkoutSetEntity(
                    id = UUID.randomUUID().toString(), workoutExerciseId = idMap.getValue(we.id), orderIndex = ws.orderIndex,
                    setType = ws.setType, weightKg = ws.weightKg, reps = ws.reps, durationSeconds = ws.durationSeconds,
                    distanceMeters = ws.distanceMeters, rpe = null, customMetric = ws.customMetric, isCompleted = false, completedAt = null,
                )
            }
        }

        workoutRepository.insertFullWorkout(
            WorkoutEntity(
                id = workoutId, routineId = source.routineId, title = source.title, notes = null, status = WorkoutStatus.IN_PROGRESS,
                startedAt = now, endedAt = null, durationSeconds = 0, createdAt = now, updatedAt = now,
            ),
            workoutExercises,
            workoutSets,
        )
        return workoutId
    }

    /** §5.1.1 spine: one IN_PROGRESS workout at a time — surfaces the conflict instead of silently creating a second one. */
    suspend fun startEmptyOrConflict(): StartResult {
        val existing = getInProgressId()
        return if (existing != null) StartResult.AlreadyInProgress(existing) else StartResult.Started(startEmpty())
    }

    suspend fun startFromRoutineOrConflict(routineId: String): StartResult {
        val existing = getInProgressId()
        return if (existing != null) StartResult.AlreadyInProgress(existing) else StartResult.Started(startFromRoutine(routineId))
    }

    suspend fun startFromWorkoutOrConflict(sourceWorkoutId: String): StartResult {
        val existing = getInProgressId()
        return if (existing != null) StartResult.AlreadyInProgress(existing) else StartResult.Started(startFromWorkout(sourceWorkoutId))
    }
}

sealed class StartResult {
    data class Started(val workoutId: String) : StartResult()
    data class AlreadyInProgress(val workoutId: String) : StartResult()
}
