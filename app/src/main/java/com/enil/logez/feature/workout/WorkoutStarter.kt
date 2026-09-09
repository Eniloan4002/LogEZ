package com.enil.logez.feature.workout

import com.enil.logez.core.common.Clock
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.model.SetType
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
                // M11: a circuit routine starts a circuit session — the copied set rows are
                // already per-round, so only the discriminator needs carrying.
                structure = routine.structure,
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
        // Re-indexed contiguously (0..n-1), NOT copied verbatim: the source is COMPLETED, and the
        // finish flow's uncompleted-set purge deletes skipped rows without re-indexing, so its
        // surviving orderIndex can carry gaps ({0,2}). Copying those gaps into a live session
        // would collide with addSet/addRound's "next index = current row count" (two rows at the
        // same orderIndex, nondeterministic order after a reload) and break the circuit logger's
        // row-index == round invariant. A copy is a fresh session, so it starts freshly numbered.
        val workoutSets = sourceExercises.flatMap { we ->
            workoutRepository.getSetsForWorkoutExercise(we.id).sortedBy { it.orderIndex }.mapIndexed { index, ws ->
                WorkoutSetEntity(
                    id = UUID.randomUUID().toString(), workoutExerciseId = idMap.getValue(we.id), orderIndex = index,
                    setType = ws.setType, weightKg = ws.weightKg, reps = ws.reps, durationSeconds = ws.durationSeconds,
                    distanceMeters = ws.distanceMeters, rpe = null, customMetric = ws.customMetric, isCompleted = false, completedAt = null,
                )
            }
        }

        workoutRepository.insertFullWorkout(
            WorkoutEntity(
                id = workoutId, routineId = source.routineId, title = source.title, notes = null, status = WorkoutStatus.IN_PROGRESS,
                startedAt = now, endedAt = null, durationSeconds = 0, createdAt = now, updatedAt = now,
                // M11: a copy of a circuit workout stays a circuit — same rows, same grouping.
                structure = source.structure,
            ),
            workoutExercises,
            workoutSets,
        )
        return workoutId
    }

    /**
     * M21a "Track a walk/run": a one-exercise ad-hoc workout, exactly [startEmpty]'s shape plus a
     * single pre-added [WorkoutExerciseEntity]/[WorkoutSetEntity] pair so the user lands straight in
     * the Logger with Running/Walking already on the board — distance and duration are typed there
     * through the exact same `DISTANCE_DURATION` cells every other exercise uses, so they start
     * blank here rather than being collected in a separate dialog.
     */
    suspend fun startQuickTrack(exerciseId: String, title: String): String {
        val now = clock.now().toEpochMilliseconds()
        val workoutId = UUID.randomUUID().toString()
        val workoutExerciseId = UUID.randomUUID().toString()
        workoutRepository.insertFullWorkout(
            WorkoutEntity(
                id = workoutId, routineId = null, title = title, notes = null, status = WorkoutStatus.IN_PROGRESS,
                startedAt = now, endedAt = null, durationSeconds = 0, createdAt = now, updatedAt = now,
            ),
            listOf(
                WorkoutExerciseEntity(
                    id = workoutExerciseId, workoutId = workoutId, exerciseId = exerciseId, orderIndex = 0,
                    supersetGroup = null, restTimerSeconds = null, notes = null,
                ),
            ),
            listOf(
                WorkoutSetEntity(
                    id = UUID.randomUUID().toString(), workoutExerciseId = workoutExerciseId, orderIndex = 0,
                    setType = SetType.NORMAL, weightKg = null, reps = null, durationSeconds = null,
                    distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null,
                ),
            ),
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

    suspend fun startQuickTrackOrConflict(exerciseId: String, title: String): StartResult {
        val existing = getInProgressId()
        return if (existing != null) StartResult.AlreadyInProgress(existing) else StartResult.Started(startQuickTrack(exerciseId, title))
    }
}

sealed class StartResult {
    data class Started(val workoutId: String) : StartResult()
    data class AlreadyInProgress(val workoutId: String) : StartResult()
}
