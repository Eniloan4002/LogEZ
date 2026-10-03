package com.enil.logez.feature.activity

import com.enil.logez.core.domain.calc.LivePaceCalculator
import com.enil.logez.core.domain.model.DistanceUnit

/**
 * What the live tracking screen recomputes once a second.
 *
 * @property elapsedSeconds moving time (decision 7): paused time is not in it.
 * @property pausedForSeconds how long the current pause has lasted, null while moving.
 * @property paceNowSecondsPerUnit the pace over the last minute of moving time; null (the screen
 *   shows a dash) while paused, whenever [gps] is not [GpsSignal.GOOD], or when the last minute
 *   covered under 20 m, so it never shows a stale number as current.
 */
data class LiveTrackingStats(
    val elapsedSeconds: Int,
    val pausedForSeconds: Int?,
    val paceNowSecondsPerUnit: Double?,
    val gps: GpsSignal,
    /** When these were computed, so a reading's age can be judged without another clock. */
    val nowMillis: Long = 0L,
)

fun ActivityTrackingState.liveStats(unit: DistanceUnit, nowMillis: Long): LiveTrackingStats {
    val gps = gpsSignal(nowMillis)
    val elapsed = elapsedSeconds(nowMillis)
    // Exact milliseconds, not the whole-second [elapsed], so the window ends where now really is.
    val paceNow = if (isPaused || gps != GpsSignal.GOOD) {
        null
    } else {
        val startedAt = startedAtMillis ?: nowMillis
        val movingSeconds = (nowMillis - startedAt - pausedMillisTotal) / 1000.0
        LivePaceCalculator.paceNowSecondsPerUnit(routeDistances, routeTimes, pauseRanges, movingSeconds, unit)
    }
    return LiveTrackingStats(elapsed, pausedForSeconds(nowMillis), paceNow, gps, nowMillis)
}
