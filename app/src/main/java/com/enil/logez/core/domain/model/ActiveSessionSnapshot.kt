package com.enil.logez.core.domain.model

/**
 * PHASE2_PLAN.md §9.5: process-death recovery state for the live workout session — deliberately
 * NOT in Room (§3.3/§9.5: "no extra Room columns"). `lastResumedAtMillis`/`accumulatedActiveSeconds`
 * are wall-clock (survive a reboot, mirror `workouts.startedAt`); `restDeadlineElapsedRealtimeMillis`
 * is elapsedRealtime-anchored (§9.4 — a rest timer is short enough that reboot-survival doesn't matter).
 */
data class ActiveSessionSnapshot(
    val workoutId: String? = null,
    val isPaused: Boolean = false,
    val accumulatedActiveSeconds: Long = 0L,
    val lastResumedAtMillis: Long? = null,
    val restDeadlineElapsedRealtimeMillis: Long? = null,
    val restExerciseId: String? = null,
)
