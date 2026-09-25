package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.ExerciseType

/**
 * PHASE2_PLAN.md §8.3 — kg-load per set, with the bodyweight rules
 * (research/followup-1.md). Branches ONLY on [isBodyweightVolumeEligible] (set true for exactly
 * the seed 100%-bodyweight movement families, including their weighted/assisted variants, per
 * §3.2/§7.3) — never on `isCustom`.
 */
object VolumeCalculator {
    fun setVolume(
        exerciseType: ExerciseType,
        isBodyweightVolumeEligible: Boolean,
        weightKg: Double?,
        reps: Int?,
        bodyweightKg: Double?,
    ): Double {
        val r = reps ?: return 0.0
        return when (exerciseType) {
            ExerciseType.WEIGHT_REPS -> (weightKg ?: return 0.0) * r
            ExerciseType.REPS_ONLY ->
                if (isBodyweightVolumeEligible) (bodyweightKg ?: return 0.0) * r else 0.0
            ExerciseType.BODYWEIGHT_WEIGHTED -> {
                val w = weightKg ?: return 0.0
                val effective = if (isBodyweightVolumeEligible) (bodyweightKg ?: 0.0) + w else w
                effective * r
            }
            ExerciseType.BODYWEIGHT_ASSISTED -> {
                if (!isBodyweightVolumeEligible) return 0.0
                val bw = bodyweightKg ?: return 0.0
                val assistance = weightKg ?: return 0.0
                maxOf(bw - assistance, 0.0) * r
            }
            ExerciseType.DURATION,
            ExerciseType.WEIGHT_DURATION,
            ExerciseType.DISTANCE_DURATION,
            ExerciseType.WEIGHT_DISTANCE,
            ExerciseType.FLOORS_DURATION,
            ExerciseType.STEPS_DURATION,
            -> 0.0
        }
    }

    /** Session/workout volume = sum of included sets' [setVolume] (§8.3). */
    fun sessionVolume(volumes: List<Double>): Double = volumes.sum()
}
