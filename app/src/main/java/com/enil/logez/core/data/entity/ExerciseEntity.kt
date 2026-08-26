package com.enil.logez.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.MuscleHead

/**
 * The exercise library — 400 seeded + unlimited custom (PHASE2_PLAN.md §3.2, §7).
 * Soft-deleted (never hard-deleted) so past workouts/routines/PRs referencing an exercise
 * always resolve; [RoutineExerciseEntity] and [WorkoutExerciseEntity] FK-RESTRICT this table.
 */
@Entity(
    tableName = "exercises",
    indices = [
        Index("primary_muscle_group"),
        Index("equipment"),
        Index("is_deleted"),
    ],
)
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "exercise_type") val exerciseType: ExerciseType,
    @ColumnInfo(name = "primary_muscle_group") val primaryMuscleGroup: MuscleGroup,
    @ColumnInfo(name = "secondary_muscle_groups") val secondaryMuscleGroups: List<MuscleGroup>,
    val equipment: Equipment,
    val instructions: String,
    @ColumnInfo(name = "media_path") val mediaPath: String?,
    @ColumnInfo(name = "is_custom") val isCustom: Boolean,
    @ColumnInfo(name = "is_bodyweight_volume_eligible") val isBodyweightVolumeEligible: Boolean,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** M8e: sub-head refinement (e.g. "Lateral Delt" within SHOULDERS) — an exercise can work more
     * than one head of the same group (e.g. a compound press hitting both anterior and lateral
     * delt), so this is a checklist, not a single pick; empty for every exercise that doesn't
     * specify any, including every exercise that existed before this column. Defaulted so this
     * addition doesn't break any existing named-arg entity construction. */
    @ColumnInfo(name = "muscle_heads") val muscleHeads: List<MuscleHead> = emptyList(),
)
