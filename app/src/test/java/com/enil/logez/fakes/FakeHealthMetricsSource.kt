package com.enil.logez.fakes

import com.enil.logez.feature.wellness.DailyTotals
import com.enil.logez.feature.wellness.HealthConnectAvailability
import com.enil.logez.feature.wellness.HealthMetricsSource

/** In-memory fake (PHASE2_PLAN.md §10.1 rule 2). */
class FakeHealthMetricsSource(
    private val availabilityValue: HealthConnectAvailability = HealthConnectAvailability.Unavailable,
    private val permissionsGranted: Boolean = false,
    private val totals: DailyTotals = DailyTotals(steps = 0L, caloriesBurned = null),
) : HealthMetricsSource {
    override val requiredPermissions: Set<String> = setOf("fake.permission.READ_STEPS")
    override fun availability(): HealthConnectAvailability = availabilityValue
    override suspend fun hasAllPermissions(): Boolean = permissionsGranted
    override suspend fun readTodayTotals(): DailyTotals = totals
}
