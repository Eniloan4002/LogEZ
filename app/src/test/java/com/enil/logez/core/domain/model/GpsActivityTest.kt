package com.enil.logez.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GpsActivityTest {
    @Test
    fun `the seed exercises resolve by id, whatever the user renamed them to`() {
        assertEquals(GpsActivity.RUN, GpsActivity.resolve(GpsActivity.RUNNING_OUTDOOR_EXERCISE_ID, "Morning loop"))
        assertEquals(GpsActivity.WALK, GpsActivity.resolve(GpsActivity.WALKING_OUTDOOR_EXERCISE_ID, "Evening stroll"))
    }

    @Test
    fun `any other exercise falls back to its name, then to OTHER`() {
        assertEquals(GpsActivity.WALK, GpsActivity.resolve("custom-1", "Hill Walk"))
        assertEquals(GpsActivity.RUN, GpsActivity.resolve("custom-2", "Trail Running"))
        assertEquals(GpsActivity.OTHER, GpsActivity.resolve("custom-3", "Cycling"))
        assertEquals(GpsActivity.OTHER, GpsActivity.resolve(null, null))
    }
}
