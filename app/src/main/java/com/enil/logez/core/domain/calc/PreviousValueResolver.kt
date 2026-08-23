package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.PreviousValuesMode

/**
 * PHASE2_PLAN.md §8.10 — resolves the live logger's PREVIOUS column source: the most recent
 * COMPLETED workout containing this exercise (ANY_WORKOUT), or the most recent one sharing the
 * current workout's `routineId` (SAME_ROUTINE, no fallback to ANY_WORKOUT if none matches — a
 * silent fallback would contradict what the setting promises). [rows] must already be sorted by
 * workout recency descending, sets ascending within each workout — [WorkoutDao.getStatRowsForExercise]'s
 * existing order — since this only groups by first-match, it doesn't re-sort.
 */
fun resolvePreviousWorkoutSets(
    rows: List<StatSet>,
    mode: PreviousValuesMode,
    currentRoutineId: String?,
): List<StatSet> {
    val candidates = if (mode == PreviousValuesMode.SAME_ROUTINE) {
        rows.filter { it.routineId == currentRoutineId }
    } else {
        rows
    }
    val mostRecentWorkoutId = candidates.firstOrNull()?.workoutId ?: return emptyList()
    return candidates.filter { it.workoutId == mostRecentWorkoutId }
}
