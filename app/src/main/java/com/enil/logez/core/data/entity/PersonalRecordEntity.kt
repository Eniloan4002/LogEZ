package com.enil.logez.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.enil.logez.core.domain.model.PrType

/**
 * DERIVED CACHE, not source of truth (PHASE2_PLAN.md §3.2, §8.4). Rebuilt wholesale for
 * affected exercises by `PersonalRecordsRepository.rebuildFor` on workout save/edit/delete,
 * a warm-up-inclusion toggle, or a bodyweight change — never incrementally patched. One row per
 * (exerciseId, prType); [workoutSetId] is null for the two session-scoped PrTypes.
 */
@Entity(
    tableName = "personal_records",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["exercise_id", "pr_type"], unique = true),
        Index("workout_id"),
    ],
)
data class PersonalRecordEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "workout_id") val workoutId: String,
    @ColumnInfo(name = "workout_set_id") val workoutSetId: String?,
    @ColumnInfo(name = "pr_type") val prType: PrType,
    val value: Double,
    @ColumnInfo(name = "achieved_at") val achievedAt: Long,
)
