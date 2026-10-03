package com.enil.logez.feature.activity.location

import android.annotation.SuppressLint
import android.location.Location
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * M21a. Talks to `FusedLocationProviderClient` over local Binder IPC to the already-installed,
 * already-networked Play services process — the location path never opens a socket from this app
 * (verified by direct AAR manifest inspection, decisions.md 2026-09-09: zero permissions declared
 * by `play-services-location`, including no INTERNET). The app's only network use is map tiles.
 */
class FusedLocationSource @Inject constructor(
    private val fusedLocationClient: FusedLocationProviderClient,
) : LocationSource {
    @SuppressLint("MissingPermission") // caller (ActivityTrackingService) only starts this after the runtime grant is confirmed
    override fun fixes(): Flow<LocationFix> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                trySend(location.toLocationFix(SystemClock.elapsedRealtimeNanos()))
            }
        }
        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { fusedLocationClient.removeLocationUpdates(callback) }
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 3_000L
        const val MIN_UPDATE_INTERVAL_MS = 2_000L
    }
}

/**
 * The reading as the tracker sees it. [nowElapsedRealtimeNanos] is the device's monotonic clock now,
 * so the age is how long ago the provider says this position was taken: what lets the tracker drop
 * a cached position the fused provider hands over first. Separate from the callback so a test can set it.
 */
internal fun Location.toLocationFix(nowElapsedRealtimeNanos: Long): LocationFix = LocationFix(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracy,
    elapsedRealtimeMillis = nowElapsedRealtimeNanos / 1_000_000L,
    ageMillis = fixAgeMillis(nowElapsedRealtimeNanos, elapsedRealtimeNanos),
)
