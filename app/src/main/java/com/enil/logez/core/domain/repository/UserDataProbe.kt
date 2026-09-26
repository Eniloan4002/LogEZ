package com.enil.logez.core.domain.repository

/**
 * Answers one question for the first-run gate: does this install already hold anything the user
 * made? Setup is only for an install that holds nothing of theirs.
 */
interface UserDataProbe {
    /**
     * True if there is any workout (of any status, so an unfinished one counts), routine, routine
     * folder, body measurement, progress photo or goal, or any custom or edited exercise.
     *
     * Derived data (daily wellness totals, heart-rate samples, personal records) never counts,
     * and neither do the seeded exercises.
     */
    suspend fun hasUserContent(): Boolean
}
