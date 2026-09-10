package com.enil.logez.feature.wellness

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

/**
 * M21e. `HealthConnectMetricsSource` is the production binding (talks to the real Health Connect
 * app over local Binder IPC, same no-INTERNET-risk shape as `FusedLocationSource` -- confirmed via
 * direct AAR/manifest inspection, decisions.md 2026-09-10); `FakeHealthMetricsSource` (test source
 * set) drives ViewModel tests. Read-only for now (steps + calories) -- matches M21e's scope; a live
 * BPM scorecard (M21f) and any write-back (M21i) are separate, later milestones.
 */
interface HealthMetricsSource {
    /** The exact permission strings the UI's permission launcher must request. */
    val requiredPermissions: Set<String>

    fun availability(): HealthConnectAvailability

    suspend fun hasAllPermissions(): Boolean

    /** Today's local-calendar-day totals. Zero steps / null calories if Health Connect has nothing for today yet. */
    suspend fun readTodayTotals(): DailyTotals
}
