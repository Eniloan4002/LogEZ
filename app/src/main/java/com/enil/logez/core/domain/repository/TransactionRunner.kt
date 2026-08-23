package com.enil.logez.core.domain.repository

/**
 * A database transaction boundary that spans more than one DAO.
 *
 * Room's `@Transaction` only covers the methods of a single DAO, which is not enough for
 * PHASE2_PLAN.md §5.1.8: the save transaction flips a workout to COMPLETED (WorkoutDao), rewrites
 * routine targets (RoutineDao), and rebuilds `personal_records` (RecordsDao). Those must commit or
 * roll back together — a partial save leaves a COMPLETED workout whose routine and PR cache were
 * never updated, and nothing can ever retry it because the finish screen is only reachable while
 * the workout is still IN_PROGRESS.
 *
 * Abstracted rather than injecting the database into feature code so the domain layer stays
 * framework-free and tests can substitute a pass-through.
 */
interface TransactionRunner {
    /** Runs [block] inside one transaction; any throw rolls the whole thing back. */
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}
