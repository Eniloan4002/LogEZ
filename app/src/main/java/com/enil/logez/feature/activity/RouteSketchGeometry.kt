package com.enil.logez.feature.activity

import kotlin.math.cos

/**
 * Pure geometry for [RouteSketch] -- projects raw lat/lng points onto a canvas-sized plane,
 * longitude-corrected and auto-scaled/centered with padding. Kept framework-free (plain
 * doubles/floats, no Compose types) so the projection math is unit-testable without Robolectric,
 * the same "plain, testable state machine" split [ActivityTrackingController] already uses.
 */
object RouteSketchGeometry {
    data class Point(val x: Float, val y: Float)

    /**
     * Returns one screen-space [Point] per input point, in the same order, ready to draw directly
     * inside a canvas of [canvasWidth] x [canvasHeight]. Empty input returns an empty list -- the
     * caller decides what "no route yet" looks like. A single point, or every point landing on the
     * exact same coordinate (e.g. GPS never moved), returns that many points stacked at the canvas
     * center -- there's no "shape" to fit edge-to-edge in that case.
     */
    fun project(
        points: List<Pair<Double, Double>>,
        canvasWidth: Float,
        canvasHeight: Float,
        paddingFraction: Float = 0.12f,
    ): List<Point> {
        if (points.isEmpty()) return emptyList()

        // A single non-finite lat/lng (a rare location-provider glitch, or a corrupted saved
        // polyline) would otherwise poison every point's projection, not just its own -- NaN
        // propagates through the shared avg-latitude/lonScale/min-max math below, silently
        // blanking the whole sketch instead of just dropping the one bad sample.
        val finitePoints = points.filter { (lat, lng) -> lat.isFinite() && lng.isFinite() }
        if (finitePoints.isEmpty()) return emptyList()

        // Longitude degrees are shorter than latitude degrees away from the equator -- correcting
        // by cos(avg latitude) keeps the sketch's proportions honest instead of stretching it
        // east-west. Small at Metro Manila's ~14.6°N (~3%), but free to apply generally.
        val avgLatRad = Math.toRadians(finitePoints.sumOf { it.first } / finitePoints.size)
        val lonScale = cos(avgLatRad).coerceAtLeast(0.1)

        // x = corrected east/west; y = latitude as-is for now, flipped to Canvas's y-down
        // convention in the final map() below -- keeping the bounds math above intuitive.
        val raw = finitePoints.map { (lat, lng) -> Point(x = (lng * lonScale).toFloat(), y = lat.toFloat()) }

        val minX = raw.minOf { it.x }
        val maxX = raw.maxOf { it.x }
        val minY = raw.minOf { it.y }
        val maxY = raw.maxOf { it.y }
        val rawSpanX = maxX - minX
        val rawSpanY = maxY - minY

        if (rawSpanX < MIN_SPAN && rawSpanY < MIN_SPAN) {
            val center = Point(canvasWidth / 2f, canvasHeight / 2f)
            return finitePoints.map { center }
        }

        val spanX = rawSpanX.coerceAtLeast(MIN_SPAN)
        val spanY = rawSpanY.coerceAtLeast(MIN_SPAN)
        val padding = minOf(canvasWidth, canvasHeight) * paddingFraction
        val availableW = (canvasWidth - padding * 2).coerceAtLeast(MIN_SPAN)
        val availableH = (canvasHeight - padding * 2).coerceAtLeast(MIN_SPAN)
        val scale = minOf(availableW / spanX, availableH / spanY)

        val drawnW = spanX * scale
        val drawnH = spanY * scale
        val offsetX = padding + (availableW - drawnW) / 2
        val offsetY = padding + (availableH - drawnH) / 2

        return raw.map { p ->
            Point(
                x = offsetX + (p.x - minX) * scale,
                // y-flip: latitude increases northward (up); Canvas y increases downward.
                y = offsetY + (maxY - p.y) * scale,
            )
        }
    }

    private const val MIN_SPAN = 1e-6f
}
