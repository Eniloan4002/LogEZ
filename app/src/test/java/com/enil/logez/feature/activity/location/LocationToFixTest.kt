package com.enil.logez.feature.activity.location

import android.location.Location
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The one place a platform `Location` becomes a [LocationFix]: what feeds the tracker's stale check.
 * If the age were not read from the reading's own timestamp, a cached position would pass as live.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocationToFixTest {
    private fun location(elapsedRealtimeNanos: Long) = Location("fused").apply {
        latitude = 14.5995
        longitude = 120.9842
        accuracy = 7.5f
        this.elapsedRealtimeNanos = elapsedRealtimeNanos
    }

    @Test
    fun `a position taken 12 seconds ago arrives 12000 ms old, over the tracker's 10 s limit`() {
        val fix = location(elapsedRealtimeNanos = 88_000_000_000L).toLocationFix(nowElapsedRealtimeNanos = 100_000_000_000L)

        assertEquals(14.5995, fix.latitude, 0.0)
        assertEquals(120.9842, fix.longitude, 0.0)
        assertEquals(7.5f, fix.accuracyMeters, 0.0f)
        assertEquals(12_000L, fix.ageMillis)
        assertEquals(100_000L, fix.elapsedRealtimeMillis)
    }

    @Test
    fun `a position stamped at the moment it is delivered is 0 ms old`() {
        val fix = location(elapsedRealtimeNanos = 100_000_000_000L).toLocationFix(nowElapsedRealtimeNanos = 100_000_000_000L)
        assertEquals(0L, fix.ageMillis)
    }

    @Test
    fun `a provider that sets no timestamp reads as fresh instead of dropping every fix`() {
        val fix = location(elapsedRealtimeNanos = 0L).toLocationFix(nowElapsedRealtimeNanos = 100_000_000_000L)
        assertEquals(0L, fix.ageMillis)
    }
}
