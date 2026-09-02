package com.enil.logez.feature.workout

import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.PlateEquipment
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.model.defaultPlateEquipment

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
    /** M17: the Plate Calculator affordance exists only on [Equipment.BARBELL] rows (§5.1.5). */
    val equipment: Equipment = Equipment.NONE,
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
    /**
     * When this set was checked off. Tracked here — not just in the entity — because §5.1.10's edit
     * save rewrites every set row from this model, and a model that didn't carry it would write
     * `is_completed = 1, completed_at = NULL`, silently dropping the workout out of the Exercise
     * Library's "recently logged first" tier (which reads MAX(completed_at)).
     */
    val completedAt: Long? = null,
    /** Formatted via `PreviousValueFormatter`, resolved once per exercise at load (§8.10). "—" when none. */
    val previousLabel: String = "—",
    /** §5.1.3 check-off validation: a FAILURE set checked with 0/blank reps is rejected, not silently accepted. */
    val failureError: Boolean = false,
)

/**
 * M17: everything the set tables need to decide on and render the Plate Calculator (§5.1.5) —
 * the setting gate, the owned bars/plates, and the display unit (solve always runs in kg; LB is
 * a display/entry conversion inside the sheet only). Kept current by the ViewModel's settings
 * collector so a mid-session Settings change applies immediately.
 */
data class PlateCalculatorConfig(
    val enabled: Boolean = true,
    val equipment: PlateEquipment = defaultPlateEquipment,
    val weightUnit: WeightUnit = WeightUnit.KG,
)
