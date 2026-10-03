package com.enil.logez.core.domain.calc

import com.enil.logez.core.common.GeoDistance
import com.enil.logez.core.domain.model.DistanceUnit
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * One per-km or per-mile split of a tracked walk/run.
 *
 * @property number 1-based; the partial split at the end carries the next number.
 * @property distanceMeters a full unit, or what was left after the last full one.
 * @property averageBpm mean of the heart-rate samples inside the split, or null with none.
 */
data class RouteSplit(
    val number: Int,
    val distanceMeters: Double,
    val durationSeconds: Int,
    val paceSecondsPerUnit: Double,
    val isPartial: Boolean,
    val averageBpm: Long?,
)

/**
 * Splits and a pace-over-time series, rebuilt after the fact from a saved route and the time each
 * of its points was recorded (`activity_tracks.route_times`, saved from 2026-09-26 on).
 *
 * Distance is re-measured along the decoded route, then scaled to the distance the tracker saved.
 * The saved route is rounded to about a metre per point, and on a walk's short hops that rounding
 * adds roughly 0.5% of length; unscaled, a walk shown as 4.98 km could list five full km splits.
 * Time runs from the first accepted GPS fix, which is also where the distance starts counting.
 */
object RouteSplitsCalculator {
    /** A trailing piece shorter than this is left out rather than shown as a "0.01 km" split. */
    const val MIN_PARTIAL_SPLIT_METERS = 50.0

    /** The pace series: each point is the pace over this much time up to it, which smooths GPS jitter. */
    const val PACE_WINDOW_SECONDS = 60

    /** How often the pace series takes a point. */
    const val PACE_STEP_SECONDS = 15

    /** A window covering less ground than this (standing at a crossing) gets no pace point. */
    const val MIN_PACE_WINDOW_METERS = 20.0

    /**
     * The speed above which a hop between two saved points is a jump the tracker did not count: the
     * same 12 m/s as `MAX_PLAUSIBLE_SPEED_MPS` in the tracker, which drops any fix faster than that
     * from its anchor. The only such hop it can still save is a re-anchor, after it lost the person
     * (driven somewhere mid-run), and it counted no distance for it.
     */
    const val JUMP_SPEED_METERS_PER_SECOND = 12.0

    /**
     * Extra seconds a hop is given before it is called a jump: 10 s for how old a fix may be when it
     * arrives (the tracker judges a hop over the fixes' own times, the saved times are arrival times)
     * and 1 s for the saved times being whole seconds. It guarantees no hop the tracker accepted is
     * ever called a jump, at the price of missing a small one (a jump within about 130 m of what 12 m/s allows).
     */
    const val JUMP_TIME_SLACK_SECONDS = 11.0

    /**
     * Cumulative meters at each point, [points] being (lat, lng), scaled so the total equals
     * [trackedDistanceMeters] when it is given and positive (see the class doc). A hop into a point
     * listed in [breakIndices] (it crosses a pause, see [PauseRanges.breakIndices]) counts 0 m: the
     * runner may have moved while paused, and the tracker never counted that stretch.
     *
     * With [timesSeconds] (one clock time per point) a hop faster than [JUMP_SPEED_METERS_PER_SECOND]
     * counts 0 m too, for the same reason: the tracker did not count it, so measuring it would make
     * the scale to the tracked distance shrink every real split.
     */
    fun cumulativeMeters(
        points: List<Pair<Double, Double>>,
        trackedDistanceMeters: Double? = null,
        breakIndices: Set<Int> = emptySet(),
        timesSeconds: List<Double>? = null,
    ): DoubleArray {
        val cumulative = DoubleArray(points.size)
        for (i in 1 until points.size) {
            val hop = if (i in breakIndices) {
                0.0
            } else {
                val (lat1, lng1) = points[i - 1]
                val (lat2, lng2) = points[i]
                val meters = GeoDistance.metersBetween(lat1, lng1, lat2, lng2)
                val seconds = timesSeconds?.let { (it[i] - it[i - 1]).coerceAtLeast(0.0) }
                if (seconds != null && meters > JUMP_SPEED_METERS_PER_SECOND * (seconds + JUMP_TIME_SLACK_SECONDS)) 0.0 else meters
            }
            cumulative[i] = cumulative[i - 1] + hop
        }
        val measured = cumulative.lastOrNull() ?: 0.0
        if (trackedDistanceMeters != null && trackedDistanceMeters > 0.0 && measured > 0.0) {
            val scale = trackedDistanceMeters / measured
            for (i in cumulative.indices) cumulative[i] *= scale
        }
        return cumulative
    }

    /**
     * Empty unless [timesSeconds] has one entry per point and there are at least two points.
     * [pauseRanges] (clock seconds, see [PauseRanges]) are left out of every split's time, while the
     * heart-rate window of a split stays on the clock, where the samples live, minus the samples taken during a pause.
     */
    fun splits(
        points: List<Pair<Double, Double>>,
        timesSeconds: List<Int>,
        unit: DistanceUnit,
        startedAtMillis: Long,
        heartRate: List<Pair<Long, Long>> = emptyList(),
        trackedDistanceMeters: Double? = null,
        pauseRanges: List<Pair<Long, Long>> = emptyList(),
    ): List<RouteSplit> {
        if (points.size < 2 || timesSeconds.size != points.size) return emptyList()
        val times = monotonic(timesSeconds).map { it.toDouble() }
        val cumulative = cumulativeMeters(points, trackedDistanceMeters, PauseRanges.breakIndices(times, pauseRanges), times)
        val unitMeters = DistanceDisplay.unitMeters(unit)
        val total = cumulative.last()

        val result = mutableListOf<RouteSplit>()
        var startDistance = 0.0
        var startTime = times.first()
        var number = 1
        while (startDistance + unitMeters <= total) {
            val boundary = startDistance + unitMeters
            val boundaryTime = timeAtDistance(cumulative, times, boundary)
            result += split(number, unitMeters, startTime, boundaryTime, unitMeters, isPartial = false, startedAtMillis, heartRate, pauseRanges)
            startDistance = boundary
            startTime = boundaryTime
            number++
        }
        val remaining = total - startDistance
        val endTime = times.last()
        if (remaining >= MIN_PARTIAL_SPLIT_METERS && endTime > startTime) {
            result += split(number, remaining, startTime, endTime, unitMeters, isPartial = true, startedAtMillis, heartRate, pauseRanges)
        }
        return result
    }

    /**
     * (epochMillis, seconds per unit) every [PACE_STEP_SECONDS], each the pace over the preceding
     * [PACE_WINDOW_SECONDS] of moving time (paused time is left out). Empty when fewer than two points
     * would result, so callers never draw a one-point chart. The x value is on the clock, so the chart
     * lines up with the heart-rate chart.
     */
    fun paceSeries(
        points: List<Pair<Double, Double>>,
        timesSeconds: List<Int>,
        unit: DistanceUnit,
        startedAtMillis: Long,
        trackedDistanceMeters: Double? = null,
        pauseRanges: List<Pair<Long, Long>> = emptyList(),
    ): List<Pair<Long, Double>> {
        if (points.size < 2 || timesSeconds.size != points.size) return emptyList()
        val clockTimes = monotonic(timesSeconds).map { it.toDouble() }
        val cumulative = cumulativeMeters(points, trackedDistanceMeters, PauseRanges.breakIndices(clockTimes, pauseRanges), clockTimes)
        val times = clockTimes.map { PauseRanges.movingSeconds(it, pauseRanges) }
        val unitMeters = DistanceDisplay.unitMeters(unit)
        val series = mutableListOf<Pair<Long, Double>>()
        var t = times.first() + PACE_WINDOW_SECONDS
        while (t <= times.last()) {
            val covered = distanceAtTime(cumulative, times, t) - distanceAtTime(cumulative, times, t - PACE_WINDOW_SECONDS)
            if (covered >= MIN_PACE_WINDOW_METERS) {
                val clock = PauseRanges.clockSeconds(t, pauseRanges)
                series += (startedAtMillis + (clock * 1000).roundToLong()) to PACE_WINDOW_SECONDS / (covered / unitMeters)
            }
            t += PACE_STEP_SECONDS
        }
        return if (series.size >= 2) series else emptyList()
    }

    private fun split(
        number: Int,
        distanceMeters: Double,
        startTime: Double,
        endTime: Double,
        unitMeters: Double,
        isPartial: Boolean,
        startedAtMillis: Long,
        heartRate: List<Pair<Long, Long>>,
        pauseRanges: List<Pair<Long, Long>>,
    ): RouteSplit {
        // Moving time: a pause inside the split is not part of how fast it was run. The heart-rate
        // window below stays on the clock, minus the samples that fall inside a pause.
        val seconds = PauseRanges.movingSeconds(endTime, pauseRanges) - PauseRanges.movingSeconds(startTime, pauseRanges)
        val fromMillis = startedAtMillis + (startTime * 1000).roundToLong()
        val toMillis = startedAtMillis + (endTime * 1000).roundToLong()
        // Samples taken while paused are a resting rate, not the effort of the stretch that was run.
        val bpm = heartRate
            .filter { (at, _) ->
                val offset = (at - startedAtMillis) / 1000.0
                at in fromMillis until toMillis && pauseRanges.none { (start, end) -> offset >= start && offset < end }
            }
            .map { it.second }
        return RouteSplit(
            number = number,
            distanceMeters = distanceMeters,
            durationSeconds = seconds.roundToInt(),
            paceSecondsPerUnit = seconds / (distanceMeters / unitMeters),
            isPartial = isPartial,
            averageBpm = bpm.takeIf { it.isNotEmpty() }?.average()?.roundToLong(),
        )
    }

    /** Times should already only grow; a clock step backwards is flattened rather than trusted. */
    private fun monotonic(times: List<Int>): List<Int> {
        var highest = Int.MIN_VALUE
        return times.map { t -> maxOf(t, highest).also { highest = it } }
    }

    private fun timeAtDistance(cumulative: DoubleArray, times: List<Double>, meters: Double): Double {
        val i = (1 until cumulative.size).firstOrNull { cumulative[it] >= meters } ?: return times.last()
        val span = cumulative[i] - cumulative[i - 1]
        val fraction = if (span > 0.0) (meters - cumulative[i - 1]) / span else 1.0
        return times[i - 1] + fraction * (times[i] - times[i - 1])
    }

    private fun distanceAtTime(cumulative: DoubleArray, times: List<Double>, seconds: Double): Double {
        if (seconds <= times.first()) return 0.0
        if (seconds >= times.last()) return cumulative.last()
        // The first index at or after [seconds]; times never decrease, so a binary search finds it.
        // The pace series asks this about twice per window over the whole route, on the main thread,
        // for every fix of a live run: a linear scan from the start made that quadratic.
        var low = 1
        var high = times.size - 1
        while (low < high) {
            val mid = (low + high) ushr 1
            if (times[mid] >= seconds) high = mid else low = mid + 1
        }
        val i = low
        val span = times[i] - times[i - 1]
        val fraction = if (span > 0.0) (seconds - times[i - 1]) / span else 1.0
        return cumulative[i - 1] + fraction * (cumulative[i] - cumulative[i - 1])
    }
}
