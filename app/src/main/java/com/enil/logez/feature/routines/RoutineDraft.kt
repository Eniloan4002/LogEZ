package com.enil.logez.feature.routines

import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.SetType

/**
 * In-memory Routine Builder state (PHASE2_PLAN.md §5.1.2) — mirrors `routines` +
 * `routine_exercises` + `routine_sets`, flattened to entities only on Save.
 */
data class RoutineDraft(
    val id: String,
    val folderId: String?,
    val createdAt: Long,
    val title: String = "",
    /** Round-tripped but not editable from this screen — the builder's regions have no notes field (§5.1.2). */
    val notes: String? = null,
    /** Edit mode only — preserved so Save doesn't re-home the routine to the top of its bucket. Ignored on create. */
    val orderIndex: Int = 0,
    val exercises: List<RoutineExerciseDraft> = emptyList(),
)

data class RoutineExerciseDraft(
    val id: String,
    val exerciseId: String,
    val exerciseName: String,
    val exerciseType: ExerciseType,
    val supersetGroup: Int? = null,
    val restTimerSeconds: Int? = null,
    val notes: String? = null,
    /** Exercise-level REPS-header toggle (§5.1.2) — applies to every set in this exercise's table. */
    val isRepRangeMode: Boolean = false,
    val sets: List<RoutineSetDraft> = emptyList(),
)

data class RoutineSetDraft(
    val id: String,
    val setType: SetType = SetType.NORMAL,
    val targetWeightKg: Double? = null,
    val targetReps: Int? = null,
    val targetRepRangeMin: Int? = null,
    val targetRepRangeMax: Int? = null,
    val targetDurationSeconds: Int? = null,
    val targetDistanceMeters: Double? = null,
)
