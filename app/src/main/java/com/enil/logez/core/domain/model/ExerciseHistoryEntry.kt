package com.enil.logez.core.domain.model

/**
 * One completed set of a given exercise, with enough workout context to render the Exercise
 * Detail History tab (PHASE2_PLAN.md §5.2): "every session containing the exercise... card per
 * workout (date, workout title) listing that session's sets".
 */
data class ExerciseHistoryEntry(
    val workoutId: String,
    val workoutTitle: String,
    val workoutStartedAt: Long,
    val setId: String,
    val setOrderIndex: Int,
    val setType: SetType,
    val weightKg: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
    val distanceMeters: Double?,
    val rpe: Double?,
    val customMetric: Double?,
)
