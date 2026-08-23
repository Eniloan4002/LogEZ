package com.enil.logez.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.enil.logez.core.data.entity.PersonalRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * `personal_records` is a derived cache (PHASE2_PLAN.md §3.2, §8.4): [rebuildFor] is the only
 * writer, always delete-then-reinsert per exercise — never an incremental patch.
 */
@Dao
interface RecordsDao {
    @Query("DELETE FROM personal_records WHERE exercise_id = :exerciseId")
    suspend fun deleteForExercise(exerciseId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<PersonalRecordEntity>)

    @Transaction
    suspend fun rebuildFor(exerciseId: String, records: List<PersonalRecordEntity>) {
        deleteForExercise(exerciseId)
        insertAll(records)
    }

    @Query("SELECT * FROM personal_records WHERE workout_id = :workoutId")
    suspend fun getForWorkout(workoutId: String): List<PersonalRecordEntity>

    @Query("SELECT * FROM personal_records WHERE workout_id = :workoutId")
    fun observeForWorkout(workoutId: String): Flow<List<PersonalRecordEntity>>

    @Query("SELECT * FROM personal_records WHERE exercise_id = :exerciseId")
    fun observeForExercise(exerciseId: String): Flow<List<PersonalRecordEntity>>
}
