package com.enil.logez.feature.workout.session

/**
 * How long the Service's bounded PARTIAL_WAKE_LOCK is held while a rest or set countdown runs
 * (§9.4 point 4). Pure, so the sizing is unit-tested without a PowerManager.
 *
 * Each timer asks for the time it has left plus slack, capped at its own bound: a rest timer keeps
 * the 5 minute bound it always had, a set countdown may be longer, so it gets 4 hours (the cap only
 * stops a mistyped huge TIME from asking for a day-long lock). The two never overlap (starting a set
 * timer ends the rest timer), but if they did, the longer request wins.
 */
object TimerWakeLock {
    const val SLACK_MS = 10_000L
    const val MAX_REST_MS = 5 * 60_000L
    const val MAX_COUNTDOWN_MS = 4 * 60 * 60_000L

    /** The lock's timeout, or null when neither timer has a deadline and no lock is needed. */
    fun timeoutMs(restDeadlineElapsedRealtimeMillis: Long?, countdownDeadlineElapsedRealtimeMillis: Long?, nowElapsedRealtimeMillis: Long): Long? {
        val rest = restDeadlineElapsedRealtimeMillis?.let { sized(it, nowElapsedRealtimeMillis, MAX_REST_MS) }
        val countdown = countdownDeadlineElapsedRealtimeMillis?.let { sized(it, nowElapsedRealtimeMillis, MAX_COUNTDOWN_MS) }
        return listOfNotNull(rest, countdown).maxOrNull()
    }

    private fun sized(deadline: Long, now: Long, cap: Long): Long = ((deadline - now).coerceAtLeast(0) + SLACK_MS).coerceAtMost(cap)
}
