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
    val isEmptyWorkoutTimerMode: Boolean = false,
    val restDeadlineElapsedRealtimeMillis: Long? = null,
    val restExerciseId: String? = null,
    /** The running set timer (2026-10-01), or null. Dropped on restore if a reboot or clock change makes its anchors disagree. */
    val inlineTimer: ActiveInlineTimerSnapshot? = null,
)

/**
 * A running inline set timer, as saved with the active session. [startWallMillis] and
 * [startElapsedRealtimeMillis] are the same instant on both clocks, so a restore can tell whether
 * the device rebooted (or the wall clock moved) since: the two clocks then no longer advance in
 * step and the timer is dropped rather than shown with a wrong time. [mode] is the stored
 * [TimerMode] text (null = stopwatch); [targetSeconds] is the countdown's length, null for a stopwatch.
 */
data class ActiveInlineTimerSnapshot(
    val exerciseId: String,
    val setId: String,
    val mode: String?,
    val startWallMillis: Long,
    val startElapsedRealtimeMillis: Long,
    val targetSeconds: Int?,
)
