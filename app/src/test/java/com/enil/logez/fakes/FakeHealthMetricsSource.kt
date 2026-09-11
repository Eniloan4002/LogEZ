package com.enil.logez.fakes

import com.enil.logez.feature.wellness.DailyTotals
import com.enil.logez.feature.wellness.HealthConnectAvailability
import com.enil.logez.feature.wellness.HealthMetricsSource
import com.enil.logez.feature.wellness.HeartRateSample
import java.time.Instant

/** In-memory fake (PHASE2_PLAN.md §10.1 rule 2). */
class FakeHealthMetricsSource(
    private val availabilityValue: HealthConnectAvailability = HealthConnectAvailability.Unavailable,
    private val permissionsGranted: Boolean = false,
    private val totals: DailyTotals = DailyTotals(steps = 0L, caloriesBurned = null),
    private val latestHeartRate: Long? = null,
    private val heartRateSamples: List<HeartRateSample> = emptyList(),
) : HealthMetricsSource {
    override val requiredPermissions: Set<String> = setOf("fake.permission.READ_STEPS", "fake.permission.READ_HEART_RATE")

    /** Every (start, end) window this fake was asked for -- lets a test assert the exact query range used. */
    val queriedRanges = mutableListOf<Pair<Instant, Instant>>()

    /** How many times [readLatestHeartRate] was called -- lets a test confirm a poller actually polls repeatedly. */
    var readLatestHeartRateCallCount = 0
        private set

    override fun availability(): HealthConnectAvailability = availabilityValue
    override suspend fun hasAllPermissions(): Boolean = permissionsGranted
    override suspend fun readTodayTotals(): DailyTotals = totals

    override suspend fun readLatestHeartRate(withinSeconds: Long): Long? {
        readLatestHeartRateCallCount++
        return latestHeartRate
    }

    override suspend fun readHeartRateSamples(start: Instant, end: Instant): List<HeartRateSample> {
        queriedRanges += start to end
        return heartRateSamples.filter { it.time >= start && it.time <= end }
    }
}
