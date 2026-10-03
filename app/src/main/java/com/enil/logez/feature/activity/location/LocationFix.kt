package com.enil.logez.feature.activity.location

/** M21a. A single GPS reading, decoupled from `android.location.Location` so the controller stays plain-JVM-testable. */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val elapsedRealtimeMillis: Long,
    /**
     * How old the reading was when it was delivered, from the fix's own timestamp. The fused
     * provider can hand over a cached position first, so this is what tells a stale one from a live
     * one. 0 means taken just now.
     */
    val ageMillis: Long = 0L,
)

/**
 * The age of a reading in ms, from its own monotonic timestamp. A timestamp of 0 or less means the
 * provider (a mock one, say) did not set it, so there is nothing to judge: such a reading counts as
 * fresh rather than dropping every fix of a run. A timestamp ahead of [nowElapsedRealtimeNanos]
 * (clock skew between providers) is also fresh, never negative.
 */
fun fixAgeMillis(nowElapsedRealtimeNanos: Long, fixElapsedRealtimeNanos: Long): Long {
    if (fixElapsedRealtimeNanos <= 0L) return 0L
    return ((nowElapsedRealtimeNanos - fixElapsedRealtimeNanos) / 1_000_000L).coerceAtLeast(0L)
}
