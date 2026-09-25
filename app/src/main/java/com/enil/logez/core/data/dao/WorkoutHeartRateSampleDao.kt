package com.enil.logez.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.enil.logez.core.data.entity.WorkoutHeartRateSampleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutHeartRateSampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(samples: List<WorkoutHeartRateSampleEntity>)

    @Query("SELECT * FROM workout_heart_rate_samples WHERE workout_id = :workoutId ORDER BY recorded_at ASC")
    suspend fun getForWorkout(workoutId: String): List<WorkoutHeartRateSampleEntity>

    @Query("SELECT * FROM workout_heart_rate_samples WHERE workout_id = :workoutId ORDER BY recorded_at ASC")
    fun observeForWorkout(workoutId: String): Flow<List<WorkoutHeartRateSampleEntity>>

    @Query("DELETE FROM workout_heart_rate_samples")
    suspend fun deleteAll()
}
