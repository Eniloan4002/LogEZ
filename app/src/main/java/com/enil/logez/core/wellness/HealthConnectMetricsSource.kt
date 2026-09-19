package com.enil.logez.feature.wellness

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import javax.inject.Inject

/**
 * M21e (steps + calories) + M21f (heart rate). Wraps `HealthConnectClient`, the same
 * local-IPC-to-an-already-installed-app shape as `FusedLocationSource` -- confirmed via direct
 * AAR/manifest inspection (decisions.md 2026-09-10), not assumed from the library's category. Uses
 * real wall-clock time directly (`LocalDate.now()`/`Instant.now()`), not the injected `Clock` --
 * same exemption as `FusedLocationSource`'s `SystemClock` use: this is a thin framework-boundary
 * wrapper around a live external data source, not testable business logic, so it isn't unit-tested
 * directly (see `FakeHealthMetricsSource` for what drives ViewModel/controller tests).
 *
 * Calories root-caused and fixed 2026-09-11 (M21g), after M21e originally cut it from scope. The
 * real bug: `Energy.kilocalories`/`.getKilocalories()` genuinely exist in the connect-client 1.1.0
 * AAR's bytecode -- `javap` shows a clean, unmangled, public `getKilocalories()` -- but that member
 * is a deprecated-hidden legacy alias, invisible to Kotlin *source* resolution even though it's
 * still callable from raw bytecode/Java; Kotlin's compiler reads visibility from the artifact's own
 * `@Metadata` annotation, not the JVM access flags, so `javap` alone can't tell public-and-live apart
 * from public-bytecode-but-hidden. The real, currently-resolvable accessor is `inKilocalories`
 * (matching this library's `inXxx` convention used by its other unit-wrapper classes). Confirmed
 * empirically, not guessed: `.kilocalories` reproduced the exact "unresolved reference" failure
 * against this same real AAR, `.inKilocalories` compiled clean on the first try.
 */
class HealthConnectMetricsSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : HealthMetricsSource {
    override val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
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
                metrics = setOf(StepsRecord.COUNT_TOTAL, TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startOfToday, LocalDateTime.now()),
            ),
        )
        val steps: Long = result.get(StepsRecord.COUNT_TOTAL) ?: 0L
        val caloriesBurned: Double? = result.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories
        return DailyTotals(steps = steps, caloriesBurned = caloriesBurned)
    }

    override suspend fun readLatestHeartRate(withinSeconds: Long): HeartRateSample? {
        val now = Instant.now()
        return readHeartRateSamples(now.minusSeconds(withinSeconds), now).maxByOrNull { it.time }
    }

    override suspend fun readHeartRateSamples(start: Instant, end: Instant): List<HeartRateSample> {
        val response = client().readRecords(
            ReadRecordsRequest(HeartRateRecord::class, TimeRangeFilter.between(start, end)),
        )
        // HeartRateRecord.Sample.beatsPerMinute is a plain Long, not a units-wrapper class like
        // Energy -- confirmed via the same javap inspection that caught the calories blocker, so
        // this path doesn't carry the same interop risk.
        return response.records
            .flatMap { record -> record.samples.map { HeartRateSample(time = it.time, bpm = it.beatsPerMinute) } }
            .sortedBy { it.time }
    }

    override suspend fun readStepsHistory(start: LocalDate, end: LocalDate): List<DailyStepCount> {
        val response = client().aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start.atStartOfDay(), end.plusDays(1).atStartOfDay()),
                timeRangeSlicer = Period.ofDays(1),
            ),
        )
        return response.map { bucket ->
            DailyStepCount(date = bucket.startTime.toLocalDate(), steps = bucket.result.get(StepsRecord.COUNT_TOTAL) ?: 0L)
        }
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)
}
