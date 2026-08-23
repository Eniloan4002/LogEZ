package com.enil.logez.core.domain.repository

import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {
    fun observeActive(): Flow<List<Exercise>>
    fun observeById(id: String): Flow<Exercise?>
    suspend fun getById(id: String): Exercise?
    suspend fun getAllActive(): List<Exercise>

    /** Custom-exercise create/edit (PHASE2_PLAN.md §5.2 — M2). Exercise type is immutable on edit. */
    suspend fun upsertCustom(exercise: Exercise)
    suspend fun softDeleteCustom(id: String)

    suspend fun count(): Int
}

/** Domain model mirroring [com.enil.logez.core.data.entity.ExerciseEntity] field-for-field. */
data class Exercise(
    val id: String,
    val name: String,
    val exerciseType: ExerciseType,
    val primaryMuscleGroup: MuscleGroup,
    val secondaryMuscleGroups: List<MuscleGroup>,
    val equipment: Equipment,
    val instructions: String,
    val mediaPath: String?,
    val isCustom: Boolean,
    val isBodyweightVolumeEligible: Boolean,
    val isDeleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
