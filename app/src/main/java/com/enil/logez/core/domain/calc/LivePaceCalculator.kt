package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.DistanceUnit

/**
 * "Pace now" for the live tracking screen: the pace over the last [RouteSplitsCalculator.PACE_WINDOW_SECONDS]
 * of MOVING time ending now, so a pause never lowers it and a stop is not hidden by a stale average.
 *
 * Not [RouteSplitsCalculator.paceSeries], which stops at the last accepted fix and so would freeze at
 * the last moving value while the runner stands still. Here the distance at "now" is the last accepted
 * fix's distance, flat since then, and the distance a minute ago is interpolated between fixes.
 */
object LivePaceCalculator {
    /**
     * @param cumulativeMeters tracked distance at each accepted route point.
     * @param clockSeconds the clock time (pauses included) of each of those points.
     * @param nowMovingSeconds moving time at the moment of asking.
     * @return seconds per unit, or null with less than a full window of moving time since the first
     *   fix, or when the window covered less than [RouteSplitsCalculator.MIN_PACE_WINDOW_METERS].
     */
    fun paceNowSecondsPerUnit(
        cumulativeMeters: List<Double>,
        clockSeconds: List<Long>,
        pauseRanges: List<Pair<Long, Long>>,
        nowMovingSeconds: Double,
        unit: DistanceUnit,
    ): Double? {
        if (cumulativeMeters.isEmpty() || cumulativeMeters.size != clockSeconds.size) return null
        val moving = clockSeconds.map { PauseRanges.movingSeconds(it.toDouble(), pauseRanges) }
        val window = RouteSplitsCalculator.PACE_WINDOW_SECONDS.toDouble()
        val windowStart = nowMovingSeconds - window
        if (windowStart < moving.first()) return null

        val distanceNow = cumulativeMeters.last()
        val distanceThen = when {
            windowStart >= moving.last() -> cumulativeMeters.last()
            else -> {
                val i = moving.indexOfFirst { it >= windowStart }
                if (i <= 0) {
                    cumulativeMeters.first()
                } else {
                    val span = moving[i] - moving[i - 1]
                    val fraction = if (span > 0.0) (windowStart - moving[i - 1]) / span else 1.0
                    cumulativeMeters[i - 1] + fraction * (cumulativeMeters[i] - cumulativeMeters[i - 1])
                }
            }
        }
        val covered = distanceNow - distanceThen
        if (covered < RouteSplitsCalculator.MIN_PACE_WINDOW_METERS) return null
        return window / (covered / DistanceDisplay.unitMeters(unit))
    }
}
