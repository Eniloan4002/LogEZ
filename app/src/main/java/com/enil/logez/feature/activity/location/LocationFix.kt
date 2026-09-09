package com.enil.logez.feature.activity.location

/** M21a. A single GPS reading, decoupled from `android.location.Location` so the controller stays plain-JVM-testable. */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val elapsedRealtimeMillis: Long,
)
