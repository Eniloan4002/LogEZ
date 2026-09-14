package com.enil.logez.core.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateZoneCalculatorTest {
    @Test
    fun `null maxHeartRateBpm means no zone`() {
        assertNull(HeartRateZoneCalculator.zoneFor(bpm = 150, maxHeartRateBpm = null))
    }

    @Test
    fun `non-positive maxHeartRateBpm means no zone`() {
        assertNull(HeartRateZoneCalculator.zoneFor(bpm = 150, maxHeartRateBpm = 0))
        assertNull(HeartRateZoneCalculator.zoneFor(bpm = 150, maxHeartRateBpm = -190))
    }

    @Test
    fun `well below 60 percent is zone 1`() {
        assertEquals(HeartRateZone.ZONE_1, HeartRateZoneCalculator.zoneFor(bpm = 100, maxHeartRateBpm = 200))
    }

    @Test
    fun `exactly at a boundary rounds up to the next zone`() {
        // 120/200 = 60% exactly -> Zone 2, not Zone 1.
        assertEquals(HeartRateZone.ZONE_2, HeartRateZoneCalculator.zoneFor(bpm = 120, maxHeartRateBpm = 200))
    }

    @Test
    fun `just under a boundary stays in the lower zone`() {
        assertEquals(HeartRateZone.ZONE_1, HeartRateZoneCalculator.zoneFor(bpm = 119, maxHeartRateBpm = 200))
    }

    @Test
    fun `70-80 percent is zone 3`() {
        assertEquals(HeartRateZone.ZONE_3, HeartRateZoneCalculator.zoneFor(bpm = 150, maxHeartRateBpm = 200))
    }

    @Test
    fun `80-90 percent is zone 4`() {
        assertEquals(HeartRateZone.ZONE_4, HeartRateZoneCalculator.zoneFor(bpm = 170, maxHeartRateBpm = 200))
    }

    @Test
    fun `90 percent and above is zone 5, including over 100 percent`() {
        assertEquals(HeartRateZone.ZONE_5, HeartRateZoneCalculator.zoneFor(bpm = 190, maxHeartRateBpm = 200))
        assertEquals(HeartRateZone.ZONE_5, HeartRateZoneCalculator.zoneFor(bpm = 210, maxHeartRateBpm = 200))
    }
}
