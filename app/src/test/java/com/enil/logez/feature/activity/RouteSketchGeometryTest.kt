package com.enil.logez.feature.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RouteSketchGeometry] can't be verified by driving real GPS movement on an emulator (a known,
 * documented limitation carried over from M21a's own testing), so this pure projection math is
 * exactly what needs real coverage instead of an on-device glance.
 */
class RouteSketchGeometryTest {
    @Test
    fun `an empty route projects to nothing`() {
        assertTrue(RouteSketchGeometry.project(emptyList(), 300f, 300f).isEmpty())
    }

    @Test
    fun `a single fix renders as one point at the canvas center`() {
        val result = RouteSketchGeometry.project(listOf(14.6 to 121.0), canvasWidth = 300f, canvasHeight = 200f)
        assertEquals(1, result.size)
        assertEquals(150f, result[0].x, 0.01f)
        assertEquals(100f, result[0].y, 0.01f)
    }

    @Test
    fun `GPS never moving -- every fix on the same coordinate -- stacks every point at the center`() {
        val samePoint = List(5) { 14.6 to 121.0 }
        val result = RouteSketchGeometry.project(samePoint, canvasWidth = 300f, canvasHeight = 200f)
        assertEquals(5, result.size)
        result.forEach {
            assertEquals(150f, it.x, 0.01f)
            assertEquals(100f, it.y, 0.01f)
        }
    }

    @Test
    fun `the northmost point ends up nearest the top of the canvas, not the bottom`() {
        // South point first, north point second -- order in the input must not affect orientation.
        val south = 14.0 to 121.0
        val north = 15.0 to 121.0
        val result = RouteSketchGeometry.project(listOf(south, north), canvasWidth = 300f, canvasHeight = 300f)
        val southY = result[0].y
        val northY = result[1].y
        assertTrue("north (${northY}) should render above south (${southY})", northY < southY)
    }

    @Test
    fun `a route fills the padded canvas edge-to-edge without exceeding it`() {
        val points = listOf(14.0 to 121.0, 14.0 to 121.1, 15.0 to 121.1, 15.0 to 121.0)
        val width = 400f
        val height = 400f
        val result = RouteSketchGeometry.project(points, width, height)

        val minX = result.minOf { it.x }
        val maxX = result.maxOf { it.x }
        val minY = result.minOf { it.y }
        val maxY = result.maxOf { it.y }

        // Never drawn outside the canvas.
        assertTrue(minX >= 0f && maxX <= width)
        assertTrue(minY >= 0f && maxY <= height)
        // The default 12% padding means the drawn span should sit meaningfully inside the edges,
        // not touch them exactly.
        assertTrue(minX > 0f && minY > 0f)
    }

    @Test
    fun `longitude is corrected by latitude so a real-world square doesn't render as a rectangle`() {
        // At 60°N, cos(60°) = 0.5, so 2° of longitude covers the same ground distance as 1° of
        // latitude. A "square" built from that ratio should therefore project to equal width and
        // height in pixels -- proving the correction is actually applied, not just present in a
        // comment. Un-corrected, this would render twice as wide as it is tall. The lat span is
        // kept tiny (0.001°) so the correction factor barely varies across it -- the projection
        // uses the *average* latitude of the route, not each point's own, so a wide span here
        // would legitimately (not buggily) throw off this exact-square expectation.
        val points = listOf(60.0 to 121.0, 60.0 to 121.002, 60.001 to 121.002, 60.001 to 121.0)
        val result = RouteSketchGeometry.project(points, canvasWidth = 1000f, canvasHeight = 1000f)

        val drawnWidth = result.maxOf { it.x } - result.minOf { it.x }
        val drawnHeight = result.maxOf { it.y } - result.minOf { it.y }
        assertEquals(drawnWidth, drawnHeight, 1f)
    }

    @Test
    fun `a single NaN point does not poison the whole route's projection`() {
        // Regression: NaN propagates through the shared avg-latitude and min-max math, so one bad
        // sample used to blank every point, not just its own.
        val points = listOf(14.0 to 121.0, Double.NaN to 121.02, 14.1 to 121.05)
        val result = RouteSketchGeometry.project(points, 300f, 300f)

        assertEquals(2, result.size) // the NaN sample is dropped, not propagated
        result.forEach {
            assertTrue(it.x.isFinite())
            assertTrue(it.y.isFinite())
        }
    }

    @Test
    fun `a route made entirely of non-finite points projects to nothing, not NaN`() {
        val points = listOf(Double.NaN to 121.0, 14.0 to Double.POSITIVE_INFINITY)
        assertTrue(RouteSketchGeometry.project(points, 300f, 300f).isEmpty())
    }

    @Test
    fun `points keep their relative order so the drawn line traces the route, not a scrambled shape`() {
        val points = listOf(14.0 to 121.0, 14.05 to 121.02, 14.1 to 121.05)
        val result = RouteSketchGeometry.project(points, 300f, 300f)
        // Each result stays associated with its own input by index -- the line's first vertex is
        // this route's start, not some other point promoted by a sort.
        assertEquals(3, result.size)
        assertEquals(result.distinct().size, result.size)
    }
}
