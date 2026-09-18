package com.enil.logez.core.domain.repository

import com.enil.logez.core.data.entity.DailyWellnessTotalEntity
import kotlinx.coroutines.flow.Flow

/** Persists per-day Health Connect wellness totals (steps, calories) read into the app. */
interface WellnessRepository {
    suspend fun upsert(total: DailyWellnessTotalEntity)
    suspend fun getByDate(date: String): DailyWellnessTotalEntity?
    fun observeByDate(date: String): Flow<DailyWellnessTotalEntity?>
}
