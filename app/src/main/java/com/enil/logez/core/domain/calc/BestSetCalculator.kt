package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.ExerciseType

/**
 * P-211 decision 6 (Owner, 2026-09-30): the one set a History card names as an exercise's best,
 * the "BEST SET" column beside "3 × Bench Press (Barbell)".
 *
 * Only the sets that line already counts are candidates ([isIncluded], the same predicate as the
 * "3 ×"), so a warm-up is never the best set unless the stats setting includes warm-ups. The
 * ranking per type follows what [PrCalculator.applicablePrTypes] treats as progress for it:
 *
 * | Type | Best | Tie-break |
 * |---|---|---|
 * | WEIGHT_REPS | highest [OneRepMax.estimate] | earlier set |
 * | BODYWEIGHT_WEIGHTED | heaviest added weight | more reps |
 * | BODYWEIGHT_ASSISTED | most reps | less assistance |
 * | REPS_ONLY | most reps | earlier set |
 * | DURATION, FLOORS_DURATION, STEPS_DURATION | longest time | earlier set |
 * | WEIGHT_DURATION | heaviest weight | longer time |
 * | DISTANCE_DURATION | longest distance; none logged → longest time | shorter time |
 * | WEIGHT_DISTANCE | heaviest weight | longer distance |
 *
 * Any tie left after that goes to the earlier set (lowest orderIndex). Assisted sets are never
 * ranked by weight: there the weight is help, so the most-assisted set would be the easiest one.
 * A set missing the field its type ranks on is not a candidate. Pure: the text ("80kg × 8") is
 * [com.enil.logez.core.designsystem.SetFormatting.bestSet].
 */
object BestSetCalculator {
    /** The best set of one exercise block, or null when none qualifies (the card shows "—"). */
    fun best(type: ExerciseType, sets: List<StatSet>, includeWarmupsInStats: Boolean): StatSet? {
        val included = sets.filter { isIncluded(it, includeWarmupsInStats) }.sortedBy { it.orderIndex }
        return when (type) {
            ExerciseType.WEIGHT_REPS -> included
                .filter { it.weightKg != null && it.reps != null }
                .maxEarliest(compareBy { OneRepMax.estimate(it.weightKg!!, it.reps!!) })
            ExerciseType.BODYWEIGHT_WEIGHTED -> included
                .filter { it.weightKg != null || it.reps != null }
                .maxEarliest(compareBy<StatSet> { it.weightKg ?: 0.0 }.thenBy { it.reps ?: 0 })
            // Less assistance is the harder set: a smaller weight ranks higher, none at all highest.
            ExerciseType.BODYWEIGHT_ASSISTED -> included
                .filter { it.reps != null }
                .maxEarliest(compareBy<StatSet> { it.reps!! }.thenByDescending { it.weightKg ?: 0.0 })
            ExerciseType.REPS_ONLY -> included
                .filter { it.reps != null }
                .maxEarliest(compareBy { it.reps!! })
            ExerciseType.DURATION, ExerciseType.FLOORS_DURATION, ExerciseType.STEPS_DURATION -> included
                .filter { it.durationSeconds != null }
                .maxEarliest(compareBy { it.durationSeconds!! })
            ExerciseType.WEIGHT_DURATION -> included
                .filter { it.weightKg != null }
                .maxEarliest(compareBy<StatSet> { it.weightKg!! }.thenBy { it.durationSeconds ?: 0 })
            ExerciseType.DISTANCE_DURATION -> {
                val withDistance = included.filter { (it.distanceMeters ?: 0.0) > 0.0 }
                if (withDistance.isNotEmpty()) {
                    // The same distance covered faster wins the tie; a set with no time loses it.
                    withDistance.maxEarliest(
                        compareBy<StatSet> { it.distanceMeters!! }.thenByDescending { it.durationSeconds ?: Int.MAX_VALUE },
                    )
                } else {
                    included.filter { it.durationSeconds != null }.maxEarliest(compareBy { it.durationSeconds!! })
                }
            }
            ExerciseType.WEIGHT_DISTANCE -> included
                .filter { it.weightKg != null }
                .maxEarliest(compareBy<StatSet> { it.weightKg!! }.thenBy { it.distanceMeters ?: 0.0 })
        }
    }

    /**
     * The greatest set, the earliest one on a tie: [maxWithOrNull] keeps the first maximum it
     * meets, and [best] hands it the candidates in orderIndex order.
     */
    private fun List<StatSet>.maxEarliest(comparator: Comparator<StatSet>): StatSet? = maxWithOrNull(comparator)
}
