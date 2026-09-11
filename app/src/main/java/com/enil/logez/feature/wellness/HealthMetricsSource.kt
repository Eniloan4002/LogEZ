package com.enil.logez.feature.wellness

import java.time.Instant

/** Whether Health Connect can actually be used on this device right now. */
sealed interface HealthConnectAvailability {
    /** No Health Connect provider installed, or the platform doesn't support it at all. */
    data object Unavailable : HealthConnectAvailability

    /** A Health Connect provider is present but needs a Play Store update before it can be used. */
    data object UpdateRequired : HealthConnectAvailability

    /** Ready to read from. */
    data object Available : HealthConnectAvailability
}

/** Today's all-day totals, as Health Connect's own aggregate already reports them. */
data class DailyTotals(val steps: Long, val caloriesBurned: Double?)

/** One Health-Connect-sourced heart-rate reading, plain-typed (no `Energy`-style unit wrapper -- `bpm` is already a beats-per-minute count). */
data class HeartRateSample(val time: Instant, val bpm: Long)

/**
 * M21e/M21f. `HealthConnectMetricsSource` is the production binding (talks to the real Health
 * Connect app over local Binder IPC, same no-INTERNET-risk shape as `FusedLocationSource` --
 * confirmed via direct AAR/manifest inspection, decisions.md 2026-09-10); `FakeHealthMetricsSource`
 * (test source set) drives ViewModel/controller tests. Read-only -- matches both milestones' scope;
 * any write-back (M21i) is a separate, later milestone.
 */
interface HealthMetricsSource {
    /** The exact permission strings the UI's permission launcher must request. */
    val requiredPermissions: Set<String>

    fun availability(): HealthConnectAvailability

    suspend fun hasAllPermissions(): Boolean

    /** Today's local-calendar-day totals. Zero steps / null calories if Health Connect has nothing for today yet. */
    suspend fun readTodayTotals(): DailyTotals

    /** M21f: the single most recent heart-rate sample within the last [withinSeconds], or null if none -- drives the live scorecard. */
    suspend fun readLatestHeartRate(withinSeconds: Long = 60): Long?

    /** M21f: every heart-rate sample Health Connect has for [start]..[end] (a finished workout's own window), oldest first -- drives the post-workout historical chart. */
    suspend fun readHeartRateSamples(start: Instant, end: Instant): List<HeartRateSample>
}
