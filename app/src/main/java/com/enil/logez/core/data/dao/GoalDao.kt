package com.enil.logez.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.enil.logez.core.data.entity.GoalDefinitionEntity
import kotlinx.coroutines.flow.Flow

/** M8d — plain user-authored CRUD, same shape as [RoutineDao]'s routine methods. */
@Dao
interface GoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: GoalDefinitionEntity)

    @Query("SELECT * FROM goal_definitions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): GoalDefinitionEntity?

    @Query("SELECT * FROM goal_definitions ORDER BY created_at ASC")
    fun observeAll(): Flow<List<GoalDefinitionEntity>>

    @Query("DELETE FROM goal_definitions WHERE id = :id")
    suspend fun deleteById(id: String)
}
