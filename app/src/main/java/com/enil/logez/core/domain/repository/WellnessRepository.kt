package com.enil.logez.core.domain.repository

import com.enil.logez.core.data.entity.DailyWellnessTotalEntity
import kotlinx.coroutines.flow.Flow

/** M21e. */
interface WellnessRepository {
    suspend fun upsert(total: DailyWellnessTotalEntity)
    suspend fun getByDate(date: String): DailyWellnessTotalEntity?
    fun observeByDate(date: String): Flow<DailyWellnessTotalEntity?>
}
