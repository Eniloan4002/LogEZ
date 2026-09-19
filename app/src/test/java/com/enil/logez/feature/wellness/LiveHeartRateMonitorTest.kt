package com.enil.logez.feature.wellness

import com.enil.logez.fakes.FakeHealthMetricsSource
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [liveHeartRateFlow] is a **cold** flow -- these tests collect a bounded number of emissions via
 * `.take(n).toList()` under `runTest`'s virtual time, never leaving an uncollected/uncancelled
 * poller running past the test. A leaked eager poller (started outside of collection, e.g. from a
 * ViewModel's `init` block instead of a cold flow) is exactly what hung `WorkoutLoggerViewModelTest`
 * before this was redesigned -- see `liveHeartRateFlow`'s own doc comment for the full story.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveHeartRateMonitorTest {
    @Test
    fun `emits the latest heart rate on every poll while available and granted`() = runTest {
        val sample = HeartRateSample(time = Instant.ofEpochSecond(1_000), bpm = 92L)
        val source = FakeHealthMetricsSource(
            availabilityValue = HealthConnectAvailability.Available,
            permissionsGranted = true,
            latestHeartRate = sample,
        )

        val emissions = liveHeartRateFlow(source, pollIntervalMs = 5_000L).take(3).toList()

        assertEquals(listOf(sample, sample, sample), emissions)
        assertEquals(3, source.readLatestHeartRateCallCount)
    }

    @Test
    fun `emits null on every poll when Health Connect isn't available -- graceful degrade, not a crash`() = runTest {
        val source = FakeHealthMetricsSource(availabilityValue = HealthConnectAvailability.Unavailable, permissionsGranted = false)

        val emissions = liveHeartRateFlow(source, pollIntervalMs = 1_000L).take(2).toList()

        assertEquals(listOf(null, null), emissions)
    }

    @Test
    fun `emits null when available but not yet granted`() = runTest {
        val source = FakeHealthMetricsSource(
            availabilityValue = HealthConnectAvailability.Available,
            permissionsGranted = false,
            latestHeartRate = HeartRateSample(time = Instant.ofEpochSecond(1_000), bpm = 100L),
        )

        val first = liveHeartRateFlow(source, pollIntervalMs = 1_000L).take(1).toList().single()

        assertNull(first)
    }

    @Test
    fun `a transient read failure degrades to null for that tick instead of terminating the flow`() = runTest {
        val source = FakeHealthMetricsSource(
            availabilityValue = HealthConnectAvailability.Available,
            permissionsGranted = true,
            latestHeartRate = HeartRateSample(time = Instant.ofEpochSecond(1_000), bpm = 88L),
            throwOnReadLatestHeartRate = IllegalStateException("Health Connect IPC hiccup"),
        )

        val emissions = liveHeartRateFlow(source, pollIntervalMs = 1_000L).take(2).toList()

        assertEquals(listOf(null, null), emissions)
    }

    @Test
    fun `stops polling as soon as collection stops -- a cold flow, not a leaked background poller`() = runTest {
        val source = FakeHealthMetricsSource(
            availabilityValue = HealthConnectAvailability.Available,
            permissionsGranted = true,
            latestHeartRate = HeartRateSample(time = Instant.ofEpochSecond(1_000), bpm = 70L),
        )

        liveHeartRateFlow(source, pollIntervalMs = 1_000L).take(4).toList()

        assertEquals("take(4) must poll exactly 4 times, never more", 4, source.readLatestHeartRateCallCount)
    }
}
