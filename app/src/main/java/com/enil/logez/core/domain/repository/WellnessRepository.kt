package com.enil.logez.core.domain.repository

import kotlinx.coroutines.flow.Flow

/** Persists per-day Health Connect wellness totals (steps, calories) read into the app. */
interface WellnessRepository {
    suspend fun upsert(total: DailyWellnessTotal)
    suspend fun getByDate(date: String): DailyWellnessTotal?
    fun observeByDate(date: String): Flow<DailyWellnessTotal?>
}

/** Domain model mirroring [com.enil.logez.core.data.entity.DailyWellnessTotalEntity] field-for-field. */
data class DailyWellnessTotal(
    val date: String,
    val steps: Long,
    val caloriesBurned: Double?,
    val updatedAt: Long,
)
