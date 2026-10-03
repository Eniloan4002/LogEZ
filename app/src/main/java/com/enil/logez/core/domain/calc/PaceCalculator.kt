package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.DistanceUnit

/**
 * Live pace for the GPS activity-tracking screen -- minutes:seconds per km/mile, the
 * distance-tracking convention (as opposed to speed) every mainstream running app shows.
 */
object PaceCalculator {
    /** Meters below this render no pace at all (an honest "not enough data yet" rather than a
     * wildly noisy number over the first few GPS fixes -- same threshold-gated-absence rule as
     * every other Health Connect/GPS-dependent stat in this app). */
    private const val MIN_METERS_FOR_PACE = 50.0

    /** Seconds-per-unit-distance, or null before [MIN_METERS_FOR_PACE] has accumulated. */
    fun paceSecondsPerUnit(distanceMeters: Double, elapsedSeconds: Int, unit: DistanceUnit): Double? {
        if (distanceMeters < MIN_METERS_FOR_PACE) return null
        return elapsedSeconds / (distanceMeters / DistanceDisplay.unitMeters(unit))
    }
}
