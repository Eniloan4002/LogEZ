package com.enil.logez.core.domain.calc

import com.enil.logez.core.common.PolylineEncoding

/**
 * The stretches of a walk/run the user paused, as (start, end) whole seconds since the run started
 * on the CLOCK (pauses included), the same time base as `activity_tracks.route_times`. Stored in
 * `activity_tracks.pause_ranges` (v11, 2026-10-01); null there means the run was never paused.
 *
 * Route times stay on the clock so heart-rate samples, which are keyed by clock time, keep matching.
 * This is what lets splits and pace leave the paused time out, and lets the map and the distance
 * maths skip the one hop that crosses a pause (the user may have moved while paused).
 */
object PauseRanges {
    /** Null for no ranges, so a run that was never paused stores nothing. Flat start,end,start,end... in [PolylineEncoding.encodeDeltas] form. */
    fun encode(ranges: List<Pair<Long, Long>>): String? =
        ranges.takeIf { it.isNotEmpty() }?.flatMap { listOf(it.first, it.second) }?.let(PolylineEncoding::encodeDeltas)

    /** Empty for null or blank. A trailing start with no end (never written by [encode]) is dropped. */
    fun decode(encoded: String?): List<Pair<Long, Long>> {
        if (encoded.isNullOrEmpty()) return emptyList()
        return PolylineEncoding.decodeDeltas(encoded).chunked(2).filter { it.size == 2 }.map { it[0] to it[1] }
    }

    fun totalSeconds(ranges: List<Pair<Long, Long>>): Long = ranges.sumOf { (start, end) -> (end - start).coerceAtLeast(0L) }

    /** [clockSeconds] minus the paused time before it: the time the user was actually moving. */
    fun movingSeconds(clockSeconds: Double, ranges: List<Pair<Long, Long>>): Double {
        var paused = 0.0
        for ((start, end) in ranges) {
            if (clockSeconds <= start) break
            paused += minOf(clockSeconds, end.toDouble()) - start
        }
        return clockSeconds - paused
    }

    /** The inverse of [movingSeconds]; a moving time exactly at a pause's start maps to the start. */
    fun clockSeconds(movingSeconds: Double, ranges: List<Pair<Long, Long>>): Double {
        var clock = movingSeconds
        for ((start, end) in ranges) {
            if (clock > start) clock += (end - start) else break
        }
        return clock
    }

    /**
     * The indices `i` of route points whose hop from point `i - 1` crosses a pause, i.e. where a
     * new moving stretch begins. [timesSeconds] is one clock time per point. A hop that crosses a
     * pause counts no distance and draws no line.
     *
     * A range of no length is ignored: ranges are whole seconds, so a pause shorter than a second
     * (a quick double tap) is stored as (s, s), which would otherwise also match the hop that merely
     * ends at a fix recorded in that same second.
     */
    fun breakIndices(timesSeconds: List<Number>, ranges: List<Pair<Long, Long>>): Set<Int> {
        if (ranges.isEmpty() || timesSeconds.size < 2) return emptySet()
        val result = mutableSetOf<Int>()
        for (i in 1 until timesSeconds.size) {
            val before = timesSeconds[i - 1].toDouble()
            val after = timesSeconds[i].toDouble()
            if (ranges.any { (start, end) -> end > start && before <= start && end <= after }) result += i
        }
        return result
    }

    /** Splits [points] into the continuous stretches between [breaks] (see [breakIndices]); stretches of one point are dropped, as they draw nothing. */
    fun segments(points: List<Pair<Double, Double>>, breaks: Set<Int>): List<List<Pair<Double, Double>>> {
        if (points.isEmpty()) return emptyList()
        val result = mutableListOf<List<Pair<Double, Double>>>()
        var start = 0
        for (i in 1..points.size) {
            if (i == points.size || i in breaks) {
                if (i - start >= 2) result += points.subList(start, i)
                start = i
            }
        }
        return result
    }
}
