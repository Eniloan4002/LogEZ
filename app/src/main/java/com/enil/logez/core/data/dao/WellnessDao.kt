package com.enil.logez.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.enil.logez.core.data.entity.DailyWellnessTotalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WellnessDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(total: DailyWellnessTotalEntity)

    @Query("SELECT * FROM daily_wellness_totals WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): DailyWellnessTotalEntity?

    @Query("SELECT * FROM daily_wellness_totals WHERE date = :date LIMIT 1")
    fun observeByDate(date: String): Flow<DailyWellnessTotalEntity?>

    @Query("DELETE FROM daily_wellness_totals")
    suspend fun deleteAll()
}
