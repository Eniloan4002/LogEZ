package com.enil.logez.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.enil.logez.core.data.entity.ActivityTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityTrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: ActivityTrackEntity)

    @Query("SELECT * FROM activity_tracks WHERE workout_set_id = :workoutSetId LIMIT 1")
    suspend fun getByWorkoutSetId(workoutSetId: String): ActivityTrackEntity?

    @Query("SELECT * FROM activity_tracks WHERE workout_set_id = :workoutSetId LIMIT 1")
    fun observeByWorkoutSetId(workoutSetId: String): Flow<ActivityTrackEntity?>
}
