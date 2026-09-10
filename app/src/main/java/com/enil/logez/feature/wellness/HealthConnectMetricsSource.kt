package com.enil.logez.feature.wellness

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * M21e, steps only. Wraps `HealthConnectClient`, the same local-IPC-to-an-already-installed-app
 * shape as `FusedLocationSource` -- confirmed via direct AAR/manifest inspection
 * (decisions.md 2026-09-10), not assumed from the library's category. Uses real wall-clock time
 * directly (`LocalDate.now()`), not the injected `Clock` -- same exemption as
 * `FusedLocationSource`'s `SystemClock` use: this is a thin framework-boundary wrapper around a
 * live external data source, not testable business logic, so it isn't unit-tested directly (see
 * `FakeHealthMetricsSource` for what drives ViewModel tests).
 *
 * Calories deliberately NOT requested/read yet, a real scope reduction from the plan's original
 * "steps + calories" M21e (decisions.md 2026-09-10): androidx.health.connect.client.units.Energy's
 * own accessors (`.kilocalories`, even the plain Java `.getKilocalories()`) fail to resolve from
 * this module against connect-client 1.1.0 -- confirmed empirically (both forms compile-fail with
 * "unresolved reference" against a method javap confirms exists, unmangled, on the class), not a
 * shortcut of convenience. Rather than guess at Energy's raw storage unit (a wrong guess would
 * silently show a wildly incorrect calorie count -- this app never fabricates a number, see the
 * Volume/Distance stat-gating precedent), the `READ_TOTAL_CALORIES_BURNED` permission isn't even
 * requested until this is root-caused, so the app never asks for access it can't yet act on.
 */
class HealthConnectMetricsSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : HealthMetricsSource {
    override val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
    )

    override fun availability(): HealthConnectAvailability =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.UpdateRequired
            else -> HealthConnectAvailability.Unavailable
        }

    override suspend fun hasAllPermissions(): Boolean =
        client().permissionController.getGrantedPermissions().containsAll(requiredPermissions)

    override suspend fun readTodayTotals(): DailyTotals {
        val startOfToday = LocalDate.now().atStartOfDay()
        val result = client().aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startOfToday, LocalDateTime.now()),
            ),
        )
        val steps: Long = result.get(StepsRecord.COUNT_TOTAL) ?: 0L
        return DailyTotals(steps = steps, caloriesBurned = null)
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)
}
