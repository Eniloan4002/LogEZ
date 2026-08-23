package com.enil.logez.core.domain.model

/**
 * PHASE2_PLAN.md §3.1. Determines which [com.enil.logez.core.data.entity.WorkoutSetEntity] /
 * [com.enil.logez.core.data.entity.RoutineSetEntity] columns are populated for an exercise —
 * immutable once an exercise is created (§5.2 Exercise Library).
 *
 * The 8 values above the line are user-selectable when creating a custom exercise; the 2 below
 * are seed-only (stair-machine exercises), logged via `customMetric` + duration.
 */
enum class ExerciseType {
    WEIGHT_REPS,
    REPS_ONLY,
    BODYWEIGHT_WEIGHTED,
    BODYWEIGHT_ASSISTED,
    DURATION,
    WEIGHT_DURATION,
    DISTANCE_DURATION,
    WEIGHT_DISTANCE,

    // Seed-only — never offered in the custom-exercise type picker.
    FLOORS_DURATION,
    STEPS_DURATION,

    ;

    companion object
}

/** The 8 types selectable when creating a custom exercise (excludes the 2 seed-only types). */
val ExerciseType.Companion.userSelectable: List<ExerciseType>
    get() = listOf(
        ExerciseType.WEIGHT_REPS,
        ExerciseType.REPS_ONLY,
        ExerciseType.BODYWEIGHT_WEIGHTED,
        ExerciseType.BODYWEIGHT_ASSISTED,
        ExerciseType.DURATION,
        ExerciseType.WEIGHT_DURATION,
        ExerciseType.DISTANCE_DURATION,
        ExerciseType.WEIGHT_DISTANCE,
    )
