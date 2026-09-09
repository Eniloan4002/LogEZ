package com.enil.logez.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class PolylineEncodingTest {
    @Test
    fun `encode matches Google's own canonical documented example`() {
        // https://developers.google.com/maps/documentation/utilities/polylinealgorithm — the
        // worked example on that page, unrelated to this implementation.
        val points = listOf(38.5 to -120.2, 40.7 to -120.95, 43.252 to -126.453)
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", PolylineEncoding.encode(points))
    }

    @Test
    fun `decode matches Google's own canonical documented example`() {
        val decoded = PolylineEncoding.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, decoded.size)
        assertEquals(38.5, decoded[0].first, 1e-5)
        assertEquals(-120.2, decoded[0].second, 1e-5)
        assertEquals(40.7, decoded[1].first, 1e-5)
        assertEquals(-120.95, decoded[1].second, 1e-5)
        assertEquals(43.252, decoded[2].first, 1e-5)
        assertEquals(-126.453, decoded[2].second, 1e-5)
    }

    @Test
    fun `encode then decode round-trips a route within precision-5 rounding`() {
        val points = listOf(14.5995 to 120.9842, 14.6001 to 120.9850, 14.6010 to 120.9838)
        val roundTripped = PolylineEncoding.decode(PolylineEncoding.encode(points))
        assertEquals(points.size, roundTripped.size)
        points.zip(roundTripped).forEach { (original, decoded) ->
            assertEquals(original.first, decoded.first, 1e-5)
            assertEquals(original.second, decoded.second, 1e-5)
        }
    }

    @Test
    fun `an empty route encodes to an empty string`() {
        assertEquals("", PolylineEncoding.encode(emptyList()))
        assertEquals(emptyList<Pair<Double, Double>>(), PolylineEncoding.decode(""))
    }
}
