package com.enil.logez.feature.activity.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiLineString

/** The route the map draws: one line per moving stretch, so nothing crosses a paused gap. */
class RouteFeatureTest {
    private val points = (0..5).map { 14.60 - it * 0.001 to 120.98 }

    @Test
    fun `no breaks is one plain line through every point, in longitude then latitude order`() {
        val line = routeFeature(points, emptySet())!!.geometry() as LineString
        assertEquals(6, line.coordinates().size)
        assertEquals(120.98, line.coordinates().first().longitude(), 1e-9)
        assertEquals(14.60, line.coordinates().first().latitude(), 1e-9)
    }

    @Test
    fun `a break splits the route into two lines with no segment between them`() {
        val multi = routeFeature(points, setOf(3))!!.geometry() as MultiLineString
        assertEquals(listOf(3, 3), multi.coordinates().map { it.size })
        // The last point of the first stretch and the first of the second are not joined.
        assertEquals(14.598, multi.coordinates()[0].last().latitude(), 1e-9)
        assertEquals(14.597, multi.coordinates()[1].first().latitude(), 1e-9)
    }

    @Test
    fun `a stretch of one point draws nothing and is dropped`() {
        // Breaks at 2 and 3 leave point 2 alone between them.
        val multi = routeFeature(points, setOf(2, 3))!!.geometry() as MultiLineString
        assertEquals(listOf(2, 3), multi.coordinates().map { it.size })
    }

    @Test
    fun `no line until a stretch has two points`() {
        assertNull(routeFeature(emptyList(), emptySet()))
        assertNull(routeFeature(points.take(1), emptySet()))
        // Two points with a break between them are two one-point stretches.
        assertNull(routeFeature(points.take(2), setOf(1)))
        assertTrue(routeFeature(points.take(2), emptySet()) != null)
    }
}
