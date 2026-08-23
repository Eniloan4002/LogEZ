package com.enil.logez.core.domain.calc

/**
 * PHASE2_PLAN.md §8.2 — Hevy's exact 30-entry percentage lookup table (research/followup-0.md),
 * recovered from Hevy's production web bundle. NOT Epley or Brzycki. WEIGHT_REPS exercises only
 * — no other ExerciseType ever computes 1RM.
 */
object OneRepMax {
    /** Index = reps - 1. */
    private val PCT = doubleArrayOf(
        1.00, 0.97, 0.94, 0.92, 0.89, 0.86, 0.83, 0.81, 0.78, 0.75,
        0.73, 0.71, 0.70, 0.68, 0.67, 0.65, 0.64, 0.63, 0.61, 0.60,
        0.59, 0.58, 0.57, 0.56, 0.55, 0.54, 0.53, 0.52, 0.51, 0.50,
    )

    /** Raw estimate — 0 reps -> 0.0; reps > 30 clamp to the 0.50 factor. Unrounded. */
    fun estimate(weightKg: Double, reps: Int): Double =
        if (reps == 0) 0.0 else weightKg / (if (reps > 30) 0.50 else PCT[reps - 1])

    /** Display rule (§8.2): convert to the user's unit first, then round to 1 decimal. */
    fun roundForDisplay(valueInDisplayUnit: Double): Double =
        Math.round(valueInDisplayUnit * 10) / 10.0

    /**
     * Per-workout chart aggregation (§8.2): the included set with the highest raw 1RM becomes
     * that workout's point; ties broken by lowest orderIndex. One point per workout.
     */
    fun bestPerWorkout(sets: List<StatSet>, includeWarmupsInStats: Boolean): Map<String, Double> =
        sets
            .filter { isIncluded(it, includeWarmupsInStats) && it.weightKg != null && it.reps != null }
            .groupBy { it.workoutId }
            .mapValues { (_, workoutSets) ->
                workoutSets
                    .sortedBy { it.orderIndex }
                    .maxOf { estimate(it.weightKg!!, it.reps!!) }
            }
}
