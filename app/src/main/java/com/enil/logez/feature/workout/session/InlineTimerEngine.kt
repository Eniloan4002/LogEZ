package com.enil.logez.feature.workout.session

import com.enil.logez.core.domain.model.ActiveInlineTimerSnapshot
import com.enil.logez.core.domain.model.TimerMode
import kotlin.math.abs

/**
 * Pure math for the logger's inline set timer, in both modes, from explicit elapsedRealtime inputs
 * (like [RestTimerEngine]), so it runs under virtual time with no Android framework.
 *
 * Rounding is deliberate, in two directions. A stopwatch shows whole elapsed seconds rounded DOWN
 * (it reads 0:00 until a full second has passed). A countdown shows time left rounded UP, so it
 * never reads 0:00 before the deadline: it shows 0:01 for the last second and 0:00 only once done.
 */
object InlineTimerEngine {
    /** How far the wall clock and elapsedRealtime may disagree about the time since a saved timer began, before it is dropped as unreliable. */
    private const val RESTORE_CLOCK_TOLERANCE_MS = 60_000L

    /** A stopwatch older than this when the app comes back is dropped, not restored. */
    const val MAX_RESTORED_STOPWATCH_MS = 4 * 60 * 60_000L

    fun elapsedMillis(startElapsedRealtimeMillis: Long, nowElapsedRealtimeMillis: Long): Long =
        (nowElapsedRealtimeMillis - startElapsedRealtimeMillis).coerceAtLeast(0)

    /** A countdown's deadline, or null for a stopwatch (or a countdown with no target). */
    fun deadline(mode: TimerMode, startElapsedRealtimeMillis: Long, targetSeconds: Int?): Long? =
        if (mode == TimerMode.COUNTDOWN && targetSeconds != null && targetSeconds > 0) startElapsedRealtimeMillis + targetSeconds * 1000L else null

    /** What the TIME cell shows: elapsed seconds (down) for a stopwatch, seconds left (up) for a countdown. */
    fun displaySeconds(mode: TimerMode, startElapsedRealtimeMillis: Long, targetSeconds: Int?, nowElapsedRealtimeMillis: Long): Int {
        val elapsed = elapsedMillis(startElapsedRealtimeMillis, nowElapsedRealtimeMillis)
        val deadline = deadline(mode, startElapsedRealtimeMillis, targetSeconds) ?: return (elapsed / 1000).toInt()
        val remainingMs = (deadline - nowElapsedRealtimeMillis).coerceAtLeast(0)
        return ((remainingMs + 999) / 1000).toInt()
    }

    /**
     * The seconds written into TIME when the timer stops: whole elapsed seconds (down), and for a
     * countdown never more than its target. A countdown stopped with 0:41 left of 1:00 logs the 19
     * seconds actually held.
     */
    fun loggedSeconds(mode: TimerMode, startElapsedRealtimeMillis: Long, targetSeconds: Int?, nowElapsedRealtimeMillis: Long): Int {
        val elapsedSeconds = (elapsedMillis(startElapsedRealtimeMillis, nowElapsedRealtimeMillis) / 1000).toInt()
        return if (deadline(mode, startElapsedRealtimeMillis, targetSeconds) != null) elapsedSeconds.coerceAtMost(targetSeconds!!) else elapsedSeconds
    }

    fun isFinished(mode: TimerMode, startElapsedRealtimeMillis: Long, targetSeconds: Int?, nowElapsedRealtimeMillis: Long): Boolean {
        val deadline = deadline(mode, startElapsedRealtimeMillis, targetSeconds) ?: return false
        return nowElapsedRealtimeMillis >= deadline
    }

    /** Milliseconds until [displaySeconds] next changes, so a ticking flow wakes on the boundary instead of drifting against it. Never below 1. */
    fun millisUntilNextDisplayChange(mode: TimerMode, startElapsedRealtimeMillis: Long, targetSeconds: Int?, nowElapsedRealtimeMillis: Long): Long {
        val deadline = deadline(mode, startElapsedRealtimeMillis, targetSeconds)
        val next = if (deadline != null) {
            val remainingMs = deadline - nowElapsedRealtimeMillis
            if (remainingMs <= 0) 1_000L else remainingMs - ((remainingMs - 1) / 1000L) * 1000L
        } else {
            1_000L - elapsedMillis(startElapsedRealtimeMillis, nowElapsedRealtimeMillis) % 1_000L
        }
        return next.coerceAtLeast(1L)
    }

    /**
     * Whether a timer saved with the session can be trusted after a restart. The two clocks run in
     * step across a process death, so they agree on the time since the start; a reboot resets
     * elapsedRealtime (and a clock change moves the wall clock), and they then disagree. A timer
     * whose clocks disagree is dropped, never shown with a guessed time.
     */
    fun isRestorable(saved: ActiveInlineTimerSnapshot, nowWallMillis: Long, nowElapsedRealtimeMillis: Long): Boolean {
        val sinceStartElapsed = nowElapsedRealtimeMillis - saved.startElapsedRealtimeMillis
        val sinceStartWall = nowWallMillis - saved.startWallMillis
        if (sinceStartElapsed < 0 || sinceStartWall < 0) return false
        if (abs(sinceStartElapsed - sinceStartWall) > RESTORE_CLOCK_TOLERANCE_MS) return false
        // A stopwatch has no end of its own, so one left running for hours is a forgotten timer,
        // not a set. A countdown is bounded by its target and is kept (it finishes with that time).
        return !(TimerMode.of(saved.mode) == TimerMode.STOPWATCH && sinceStartElapsed > MAX_RESTORED_STOPWATCH_MS)
    }
}
