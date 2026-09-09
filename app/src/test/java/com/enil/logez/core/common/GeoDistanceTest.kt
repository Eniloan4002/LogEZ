package com.enil.logez.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoDistanceTest {
    @Test
    fun `identical points are zero meters apart`() {
        assertEquals(0.0, GeoDistance.metersBetween(14.5995, 120.9842, 14.5995, 120.9842), 0.001)
    }

    @Test
    fun `New York to Los Angeles matches the commonly published great-circle distance`() {
        // Published great-circle distance ~3,936 km (e.g. https://www.distance.to/New-York/Los-Angeles).
        val meters = GeoDistance.metersBetween(40.7128, -74.0060, 34.0522, -118.2437)
        assertEquals(3_936_000.0, meters, 20_000.0) // within 20km of the published figure
    }

    @Test
    fun `a small north-south displacement matches the textbook 111,320 meters-per-degree-of-latitude figure`() {
        val meters = GeoDistance.metersBetween(14.5995, 120.9842, 14.5985, 120.9842) // 0.001 degree south
        assertEquals(111.32, meters, 1.0) // 0.001 * 111,320 m/degree
    }
}
