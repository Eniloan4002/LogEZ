package com.enil.logez.feature.routines

import com.enil.logez.core.domain.model.ExerciseType

/** One target-column kind from the PHASE2_PLAN.md §5.1.2 set-table matrix. */
internal enum class TargetField { WEIGHT, REPS, DURATION, DISTANCE }

/** Which target columns a given [ExerciseType] shows (§5.1.2's per-type column table). */
internal fun ExerciseType.targetFields(): Set<TargetField> = when (this) {
    ExerciseType.WEIGHT_REPS -> setOf(TargetField.WEIGHT, TargetField.REPS)
    ExerciseType.REPS_ONLY -> setOf(TargetField.REPS)
    ExerciseType.BODYWEIGHT_WEIGHTED -> setOf(TargetField.WEIGHT, TargetField.REPS)
    ExerciseType.BODYWEIGHT_ASSISTED -> setOf(TargetField.WEIGHT, TargetField.REPS)
    ExerciseType.DURATION -> setOf(TargetField.DURATION)
    ExerciseType.WEIGHT_DURATION -> setOf(TargetField.WEIGHT, TargetField.DURATION)
    ExerciseType.DISTANCE_DURATION -> setOf(TargetField.DISTANCE, TargetField.DURATION)
    ExerciseType.WEIGHT_DISTANCE -> setOf(TargetField.WEIGHT, TargetField.DISTANCE)
    ExerciseType.FLOORS_DURATION, ExerciseType.STEPS_DURATION -> setOf(TargetField.DURATION)
}

/**
 * §5.1.2 Replace Exercise: "targets preserved where the new ExerciseType shares fields, clearing
 * the rest." Reps-range mode is cleared whenever the new type drops REPS, since a range with no
 * REPS column to display it in is meaningless.
 */
internal fun RoutineSetDraft.carryOverTo(oldType: ExerciseType, newType: ExerciseType): RoutineSetDraft {
    val kept = oldType.targetFields() intersect newType.targetFields()
    return copy(
        targetWeightKg = if (TargetField.WEIGHT in kept) targetWeightKg else null,
        targetReps = if (TargetField.REPS in kept) targetReps else null,
        targetRepRangeMin = if (TargetField.REPS in kept) targetRepRangeMin else null,
        targetRepRangeMax = if (TargetField.REPS in kept) targetRepRangeMax else null,
        targetDurationSeconds = if (TargetField.DURATION in kept) targetDurationSeconds else null,
        targetDistanceMeters = if (TargetField.DISTANCE in kept) targetDistanceMeters else null,
    )
}
