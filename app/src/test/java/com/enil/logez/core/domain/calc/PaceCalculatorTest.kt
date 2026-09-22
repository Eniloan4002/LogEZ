package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.DistanceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaceCalculatorTest {
    @Test
    fun `below the minimum distance renders no pace at all`() {
        assertNull(PaceCalculator.paceSecondsPerUnit(distanceMeters = 10.0, elapsedSeconds = 60, unit = DistanceUnit.KM))
    }

    @Test
    fun `exactly 1km in 5 minutes is a 5-00 per-km pace`() {
        val pace = PaceCalculator.paceSecondsPerUnit(distanceMeters = 1000.0, elapsedSeconds = 300, unit = DistanceUnit.KM)
        assertEquals(300.0, pace!!, 1e-9)
    }

    @Test
    fun `miles convert using the exact 1609,344m definition`() {
        val pace = PaceCalculator.paceSecondsPerUnit(distanceMeters = 1609.344, elapsedSeconds = 480, unit = DistanceUnit.MILES)
        assertEquals(480.0, pace!!, 1e-6)
    }

    @Test
    fun `same distance and time gives a slower per-km number than per-mile`() {
        // A mile is longer than a km, so the same pace-per-distance-run means more seconds per km.
        val perKm = PaceCalculator.paceSecondsPerUnit(distanceMeters = 5000.0, elapsedSeconds = 1500, unit = DistanceUnit.KM)!!
        val perMile = PaceCalculator.paceSecondsPerUnit(distanceMeters = 5000.0, elapsedSeconds = 1500, unit = DistanceUnit.MILES)!!
        assert(perMile > perKm)
    }

    /**
     * [windowedPaceSecondsPerUnit] exists because [paceSecondsPerUnit]'s own 50m floor -- correct
     * for gating the whole session's live pace number -- would silently empty a pace-history
     * chart built from ~15-second windows: 50m in 15s needs >=12 km/h, faster than typical walking
     * or jogging (adversarial review, 2026-09-22, caught before this ever reached a device).
     */
    @Test
    fun `a brisk walk's distance in a 15-second window still renders a windowed pace`() {
        // ~1.4 m/s, a brisk walk -- 21m in 15s, comfortably under paceSecondsPerUnit's 50m floor
        // but real, sustained movement worth showing on a chart.
        val pace = PaceCalculator.windowedPaceSecondsPerUnit(deltaMeters = 21.0, deltaSeconds = 15, unit = DistanceUnit.KM)
        assertEquals(15 / (21.0 / 1000.0), pace!!, 1e-9)
    }

    @Test
    fun `below the windowed minimum renders no windowed pace -- GPS jitter, not real movement`() {
        assertNull(PaceCalculator.windowedPaceSecondsPerUnit(deltaMeters = 2.0, deltaSeconds = 15, unit = DistanceUnit.KM))
    }

    @Test
    fun `a zero or negative window renders no windowed pace instead of dividing by it`() {
        assertNull(PaceCalculator.windowedPaceSecondsPerUnit(deltaMeters = 20.0, deltaSeconds = 0, unit = DistanceUnit.KM))
    }
}
