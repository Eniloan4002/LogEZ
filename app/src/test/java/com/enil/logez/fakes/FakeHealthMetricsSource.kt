package com.enil.logez.fakes

import com.enil.logez.core.wellness.DailyStepCount
import com.enil.logez.core.wellness.DailyTotals
import com.enil.logez.core.wellness.HealthConnectAvailability
import com.enil.logez.core.wellness.HealthDataType
import com.enil.logez.core.wellness.HealthMetricsSource
import com.enil.logez.core.wellness.HeartRateSample
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation

/** In-memory fake (PHASE2_PLAN.md §10.1 rule 2). */
class FakeHealthMetricsSource(
    /** Mutable so a test can simulate Health Connect being installed or updated while the app is away. */
    var availabilityValue: HealthConnectAvailability = HealthConnectAvailability.Unavailable,
    /** Mutable so a test can simulate a grant made in Health Connect's own settings. */
    var permissionsGranted: Boolean = false,
    /** Overrides [permissionsGranted] with an exact partial grant; null means all-or-nothing per [permissionsGranted]. */
    var grantedTypesOverride: Set<HealthDataType>? = null,
    private val totals: DailyTotals = DailyTotals(steps = 0L, caloriesBurned = null),
    private val latestHeartRate: HeartRateSample? = null,
    /** Mutable so a test can simulate readings that reach Health Connect after a workout was saved. */
    var heartRateSamples: List<HeartRateSample> = emptyList(),
    private val stepsHistory: List<DailyStepCount> = emptyList(),
    private val throwOnReadHeartRateSamples: Throwable? = null,
    private val throwOnReadLatestHeartRate: Throwable? = null,
    private val throwOnRevoke: Throwable? = null,
    /** Tests that launch Health Connect's real permission contract pass real permission names, which it checks. */
    override val requiredPermissions: Set<String> = setOf("fake.permission.READ_STEPS", "fake.permission.READ_HEART_RATE"),
) : HealthMetricsSource {

    /** Every (start, end) window this fake was asked for -- lets a test assert the exact query range used. */
    val queriedRanges = mutableListOf<Pair<Instant, Instant>>()

    /** Every (start, end) date range [readStepsHistory] was asked for. */
    val queriedStepsRanges = mutableListOf<Pair<LocalDate, LocalDate>>()

    /** How many times [readLatestHeartRate] was called -- lets a test confirm a poller actually polls repeatedly. */
    var readLatestHeartRateCallCount = 0
        private set

    /** How many times [onPermissionsRegranted] was called. */
    var regrantedCallCount = 0
        private set

    /** How many times the grants were read, through [grantedTypes] or [grantedTypesOrNull]. */
    var grantedTypesCallCount = 0
        private set

    /**
     * When true, asking Health Connect for the grants fails the way the real source's does: it
     * catches the failure, so [grantedTypes] answers empty and [grantedTypesOrNull] null.
     */
    var grantedTypesReadFails: Boolean = false

    /**
     * Answers for the next grants reads, one per read in order: each read waits for its own, so a
     * test can finish two overlapping reads in either order. Empty means answer at once.
     */
    val pendingGrantedTypes = ArrayDeque<CompletableDeferred<Set<HealthDataType>>>()

    /** When true, [grantedTypes] suspends until cancelled, standing in for a Health Connect that never answers. */
    var grantedTypesNeverReturns: Boolean = false

    override fun availability(): HealthConnectAvailability = availabilityValue

    override fun onPermissionsRegranted() {
        regrantedCallCount++
    }

    override fun permissionFor(type: HealthDataType): String = "fake.permission.READ_${type.name}"
    /** How many times [revokeAllPermissions] was called. */
    var revokeCallCount = 0
        private set

    // Mirrors the real source: nothing is granted while Health Connect is not usable.
    override suspend fun grantedTypes(): Set<HealthDataType> = grantedTypesOrNull() ?: emptySet()

    override suspend fun grantedTypesOrNull(): Set<HealthDataType>? {
        grantedTypesCallCount++
        pendingGrantedTypes.removeFirstOrNull()?.let { return it.await() }
        if (grantedTypesNeverReturns) awaitCancellation()
        if (availabilityValue != HealthConnectAvailability.Available) return emptySet()
        if (grantedTypesReadFails) return null
        return grantedTypesOverride ?: if (permissionsGranted) HealthDataType.entries.toSet() else emptySet()
    }

    // Mirrors the real source: an ungranted type reads as 0 steps / null calories.
    override suspend fun readTodayTotals(): DailyTotals {
        val granted = grantedTypes()
        return DailyTotals(
            steps = if (HealthDataType.STEPS in granted) totals.steps else 0L,
            caloriesBurned = if (HealthDataType.CALORIES in granted) totals.caloriesBurned else null,
        )
    }

    override suspend fun revokeAllPermissions() {
        revokeCallCount++
        throwOnRevoke?.let { throw it }
    }

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
