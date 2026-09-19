package com.enil.logez.feature.wellness

import java.time.Instant
import java.time.LocalDate

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

/** One calendar day's step total, as Health Connect's own per-day aggregate reports it. */
data class DailyStepCount(val date: LocalDate, val steps: Long)

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

    /**
     * The single most recent heart-rate sample within the last [withinSeconds], or null if none --
     * drives the live scorecard. Returns the whole [HeartRateSample] (not just the bpm) so the UI
     * can show how stale it actually is -- a wearable's readings reach Health Connect through a
     * multi-hop sync (watch -> its companion app -> Health Connect), not in real time, so what's
     * "latest" here can genuinely be several minutes old even while the watch face itself shows a
     * fresher on-wrist reading. [withinSeconds] default widened from M21f's original 60s (Owner
     * report 2026-09-12: BPM often went blank entirely, or showed a number that looked wrong next
     * to the watch, purely because that sync lag routinely exceeds one minute) -- 5 minutes is
     * still "recent enough to mean something" without going blank on every ordinary sync gap.
     */
    suspend fun readLatestHeartRate(withinSeconds: Long = 300): HeartRateSample?

    /** M21f: every heart-rate sample Health Connect has for [start]..[end] (a finished workout's own window), oldest first -- drives the post-workout historical chart. */
    suspend fun readHeartRateSamples(start: Instant, end: Instant): List<HeartRateSample>

    /**
     * One entry per calendar day in [start]..[end] inclusive that Health Connect actually has data
     * for -- drives the Statistics steps chart. Health Connect itself caps how far back a normal
     * read grant can see (its own permission screen says "the app can read new data and data from
     * the past 30 days"), so callers should not ask for a wider range expecting more to come back.
     */
    suspend fun readStepsHistory(start: LocalDate, end: LocalDate): List<DailyStepCount>
}
