package com.enil.logez.feature.workout

import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.SetType

/**
 * Live-session UI mirror of `workout_exercises`/`workout_sets` (PHASE2_PLAN.md §5.1.3). Unlike
 * the Routine Builder's [com.enil.logez.feature.routines.RoutineDraft], this is write-through —
 * every mutator in [WorkoutLoggerViewModel] updates this in-memory copy *and* persists to Room
 * in the same call, so process death only ever loses the in-flight keystroke, never a completed
 * set (spine).
 */
data class WorkoutExerciseUiModel(
    val id: String,
    val exerciseId: String,
    val exerciseName: String,
    val exerciseType: ExerciseType,
    val supersetGroup: Int? = null,
    val restTimerSeconds: Int? = null,
    val notes: String = "",
    val previousSessionNote: String? = null,
    val sets: List<WorkoutSetUiModel> = emptyList(),
)

data class WorkoutSetUiModel(
    val id: String,
    val setType: SetType = SetType.NORMAL,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    val customMetric: Double? = null,
    val rpe: Double? = null,
    val isCompleted: Boolean = false,
    /** Formatted via `PreviousValueFormatter`, resolved once per exercise at load (§8.10). "—" when none. */
    val previousLabel: String = "—",
    /** §5.1.3 check-off validation: a FAILURE set checked with 0/blank reps is rejected, not silently accepted. */
    val failureError: Boolean = false,
)
