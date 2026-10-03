package com.enil.logez.feature.activity

import com.enil.logez.core.wellness.HeartRateAccess
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which heart-rate form the tracking screen shows (decision 6; `tracking-states-sheet.png`, states 1 to 8). */
class TrackingHeartRateStateTest {
    private fun state(
        access: HeartRateAccess? = HeartRateAccess.GRANTED,
        hasReading: Boolean = false,
        hasMax: Boolean = true,
        stale: Boolean = false,
        refused: Boolean = false,
    ) = trackingHeartRateState(access, hasReading, hasMax, stale, refused)

    @Test
    fun `a fresh reading is live, with or without a max heart rate for the zone`() {
        assertEquals(TrackingHeartRateState.LIVE, state(hasReading = true))
        assertEquals(TrackingHeartRateState.LIVE_NO_MAX, state(hasReading = true, hasMax = false))
    }

    @Test
    fun `a reading over five minutes old is stale, whatever else is true`() {
        assertEquals(TrackingHeartRateState.STALE, state(hasReading = true, stale = true))
        assertEquals(TrackingHeartRateState.STALE, state(hasReading = true, stale = true, hasMax = false))
    }

    @Test
    fun `a reading wins over an access state that would otherwise show a card`() {
        assertEquals(TrackingHeartRateState.LIVE, state(access = HeartRateAccess.NOT_GRANTED, hasReading = true))
    }

    @Test
    fun `allowed but nothing synced is one waiting line`() {
        assertEquals(TrackingHeartRateState.WAITING, state())
    }

    @Test
    fun `a phone that can never run Health Connect is the unavailable line`() {
        assertEquals(TrackingHeartRateState.UNAVAILABLE, state(access = HeartRateAccess.UNAVAILABLE))
    }

    @Test
    fun `the states a tap can fix keep their card`() {
        assertEquals(TrackingHeartRateState.NEEDS_HEALTH_CONNECT, state(access = HeartRateAccess.NEEDS_INSTALL_OR_UPDATE))
        assertEquals(TrackingHeartRateState.NOT_ALLOWED, state(access = HeartRateAccess.NOT_GRANTED))
        assertEquals(TrackingHeartRateState.STILL_OFF, state(access = HeartRateAccess.NOT_GRANTED, refused = true))
    }

    @Test
    fun `an access that is not known yet shows nothing, so a card does not pop in and away`() {
        assertEquals(TrackingHeartRateState.UNKNOWN, state(access = null))
    }
}
