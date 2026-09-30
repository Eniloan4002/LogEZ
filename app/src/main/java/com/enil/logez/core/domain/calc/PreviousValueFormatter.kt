package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.WeightUnit

/**
 * PHASE2_PLAN.md §8.10 — formats the live logger's PREVIOUS column. Purely a display formatter.
 * [format] returns the value line ("50 kg × 10"). The effort under it (Owner, 2026-09-03: on its
 * own line, not appended inline) is not formatted here: P-211 (Owner, 2026-09-30) keeps the
 * previous set's raw RPE in the UI model and draws it in the user's current scale
 * (`effortValueLine`), so a mid-workout switch to RIR relabels it without re-querying.
 */
object PreviousValueFormatter {
    fun format(set: StatSet, type: ExerciseType, weightUnit: WeightUnit, distanceUnit: DistanceUnit): String {
        val base = when (type) {
            ExerciseType.WEIGHT_REPS, ExerciseType.BODYWEIGHT_WEIGHTED, ExerciseType.BODYWEIGHT_ASSISTED -> {
                val w = set.weightKg?.let { convertWeight(it, weightUnit) }
                val r = set.reps
                if (w != null && r != null) "${formatNumber(w)} ${weightUnit.label()} $TIMES $r" else "—"
            }
            ExerciseType.REPS_ONLY -> set.reps?.let { "$it reps" } ?: "—"
            ExerciseType.DURATION, ExerciseType.FLOORS_DURATION, ExerciseType.STEPS_DURATION ->
                set.durationSeconds?.let { formatDuration(it) } ?: "—"
            ExerciseType.WEIGHT_DURATION -> {
                val w = set.weightKg?.let { convertWeight(it, weightUnit) }
                val d = set.durationSeconds
                if (w != null && d != null) "${formatNumber(w)} ${weightUnit.label()} $TIMES ${formatDuration(d)}" else "—"
            }
            ExerciseType.DISTANCE_DURATION -> {
                val dist = set.distanceMeters?.let { convertDistance(it, distanceUnit) }
                val d = set.durationSeconds
                if (dist != null && d != null) "${formatNumber(dist)} ${distanceUnit.label()} / ${formatDuration(d)}" else "—"
            }
            ExerciseType.WEIGHT_DISTANCE -> {
                val w = set.weightKg?.let { convertWeight(it, weightUnit) }
                val dist = set.distanceMeters?.let { convertDistance(it, distanceUnit) }
                if (w != null && dist != null) "${formatNumber(w)} ${weightUnit.label()} $TIMES ${formatNumber(dist)} ${distanceUnit.label()}" else "—"
            }
        }
        return base
    }

    /** P-211 small fix 10h: the multiplication sign History writes ("80kg × 8"), not an ASCII "x". */
    private const val TIMES = "×"

    private fun convertWeight(kg: Double, unit: WeightUnit) = WeightDisplay.toDisplay(kg, unit)
    private fun convertDistance(m: Double, unit: DistanceUnit) = DistanceDisplay.toDisplay(m, unit)
    // Locale.ROOT: the default-locale overload renders "42,5" on comma-decimal devices (the same
    // bug class M17's review caught in the plate sheet) — PREVIOUS must match the cells' dot style.
    // Rounded to one decimal before the whole-number check: a logged 185 lb is stored as
    // 83.914588 kg and converts back to 184.99999…, which printed "185.0 lb" and, with PREVIOUS
    // narrowed while the effort column shows (P-211 fix 10e), filled the cell edge to edge.
    private fun formatNumber(v: Double): String {
        val rounded = Math.round(v * 10) / 10.0
        return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else "%.1f".format(java.util.Locale.ROOT, rounded)
    }
    private fun formatDuration(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%d:%02d".format(m, s)
    }
    private fun WeightUnit.label() = if (this == WeightUnit.KG) "kg" else "lb"
    private fun DistanceUnit.label() = if (this == DistanceUnit.KM) "km" else "mi"
}
