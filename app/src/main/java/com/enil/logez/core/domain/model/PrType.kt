package com.enil.logez.core.domain.model

/**
 * PHASE2_PLAN.md §3.1 / §8.4 — the 9 personal-record kinds. Applicability per [ExerciseType] is
 * the PrType-per-ExerciseType matrix in §8.4, implemented by `PrCalculator.applicablePrTypes`.
 */
enum class PrType {
    HEAVIEST_WEIGHT,
    BEST_1RM,
    BEST_SET_VOLUME,
    BEST_SESSION_VOLUME,
    MOST_REPS_SET,
    MOST_SESSION_REPS,
    LONGEST_DISTANCE,
    LONGEST_TIME,
    BEST_TIME,
}
