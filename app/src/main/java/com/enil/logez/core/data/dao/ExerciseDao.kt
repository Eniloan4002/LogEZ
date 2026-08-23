package com.enil.logez.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.enil.logez.core.data.entity.ExerciseEntity
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.MuscleGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    /** Seed insert (PHASE2_PLAN.md §7.8): new IDs land, existing IDs are silently ignored — result[i] is -1 for a conflict. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(exercises: List<ExerciseEntity>): List<Long>

    /**
     * Seed correction-safe update (§7.8): name/muscles/equipment/instructions/eligibility/
     * isDeleted only — `exercise_type` is deliberately excluded (immutable after creation) and
     * this only ever touches seed rows (`is_custom = 0`), never user-created exercises.
     */
    @Query(
        """
        UPDATE exercises SET
            name = :name,
            primary_muscle_group = :primaryMuscleGroup,
            secondary_muscle_groups = :secondaryMuscleGroups,
            equipment = :equipment,
            instructions = :instructions,
            is_bodyweight_volume_eligible = :isBodyweightVolumeEligible,
            is_deleted = :isDeleted,
            updated_at = :updatedAt
        WHERE id = :id AND is_custom = 0
        """,
    )
    suspend fun updateSeedFields(
        id: String,
        name: String,
        primaryMuscleGroup: MuscleGroup,
        secondaryMuscleGroups: List<MuscleGroup>,
        equipment: Equipment,
        instructions: String,
        isBodyweightVolumeEligible: Boolean,
        isDeleted: Boolean,
        updatedAt: Long,
    )

    /** Custom-exercise create/edit (PHASE2_PLAN.md §5.2 — M2 consumer). */
    @Upsert
    suspend fun upsert(exercise: ExerciseEntity)

    @Query("SELECT * FROM exercises WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercises WHERE is_deleted = 0 ORDER BY name ASC")
    fun observeAllActive(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE is_deleted = 0 ORDER BY name ASC")
    suspend fun getAllActive(): List<ExerciseEntity>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM exercises WHERE is_custom = 0")
    suspend fun seedCount(): Int

    /** Soft delete (custom exercises only — spine entity 1). */
    @Query("UPDATE exercises SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id AND is_custom = 1")
    suspend fun softDeleteCustom(id: String, updatedAt: Long)
}
