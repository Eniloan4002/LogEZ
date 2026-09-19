package com.enil.logez.fakes

import com.enil.logez.core.wellness.DailyStepCount
import com.enil.logez.core.wellness.DailyTotals
import com.enil.logez.core.wellness.HealthConnectAvailability
import com.enil.logez.core.wellness.HealthMetricsSource
import com.enil.logez.core.wellness.HeartRateSample
import java.time.Instant
import java.time.LocalDate

/** In-memory fake (PHASE2_PLAN.md §10.1 rule 2). */
class FakeHealthMetricsSource(
    private val availabilityValue: HealthConnectAvailability = HealthConnectAvailability.Unavailable,
    private val permissionsGranted: Boolean = false,
    private val totals: DailyTotals = DailyTotals(steps = 0L, caloriesBurned = null),
    private val latestHeartRate: HeartRateSample? = null,
    private val heartRateSamples: List<HeartRateSample> = emptyList(),
    private val stepsHistory: List<DailyStepCount> = emptyList(),
    private val throwOnReadHeartRateSamples: Throwable? = null,
    private val throwOnReadLatestHeartRate: Throwable? = null,
) : HealthMetricsSource {
    override val requiredPermissions: Set<String> = setOf("fake.permission.READ_STEPS", "fake.permission.READ_HEART_RATE")

    /** Every (start, end) window this fake was asked for -- lets a test assert the exact query range used. */
    val queriedRanges = mutableListOf<Pair<Instant, Instant>>()

    /** Every (start, end) date range [readStepsHistory] was asked for. */
    val queriedStepsRanges = mutableListOf<Pair<LocalDate, LocalDate>>()

    /** How many times [readLatestHeartRate] was called -- lets a test confirm a poller actually polls repeatedly. */
    var readLatestHeartRateCallCount = 0
        private set

    override fun availability(): HealthConnectAvailability = availabilityValue
    override suspend fun hasAllPermissions(): Boolean = permissionsGranted
    override suspend fun readTodayTotals(): DailyTotals = totals

    override suspend fun readLatestHeartRate(withinSeconds: Long): HeartRateSample? {
        readLatestHeartRateCallCount++
        throwOnReadLatestHeartRate?.let { throw it }
        return latestHeartRate
    }

    override suspend fun readHeartRateSamples(start: Instant, end: Instant): List<HeartRateSample> {
        queriedRanges += start to end
        throwOnReadHeartRateSamples?.let { throw it }
        return heartRateSamples.filter { it.time >= start && it.time <= end }
    }

    override suspend fun readStepsHistory(start: LocalDate, end: LocalDate): List<DailyStepCount> {
        queriedStepsRanges += start to end
        return stepsHistory.filter { it.date >= start && it.date <= end }
    }
}
