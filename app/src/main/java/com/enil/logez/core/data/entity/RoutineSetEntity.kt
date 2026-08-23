package com.enil.logez.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.enil.logez.core.domain.model.SetType

/**
 * A target set within a routine exercise (PHASE2_PLAN.md §3.2, §5.1.2). `targetReps` and the
 * `targetRepRange*` pair are mutually exclusive (routine builder's REPS-header toggle); rep-range
 * targets never auto-update from performance (§8.10). No RPE column — the consumer routine
 * builder has none (research/followup-2.md).
 */
@Entity(
    tableName = "routine_sets",
    foreignKeys = [
        ForeignKey(
            entity = RoutineExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routine_exercise_id")],
)
data class RoutineSetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "routine_exercise_id") val routineExerciseId: String,
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    @ColumnInfo(name = "set_type") val setType: SetType,
    @ColumnInfo(name = "target_weight_kg") val targetWeightKg: Double?,
    @ColumnInfo(name = "target_reps") val targetReps: Int?,
    @ColumnInfo(name = "target_rep_range_min") val targetRepRangeMin: Int?,
    @ColumnInfo(name = "target_rep_range_max") val targetRepRangeMax: Int?,
    @ColumnInfo(name = "target_duration_seconds") val targetDurationSeconds: Int?,
    @ColumnInfo(name = "target_distance_meters") val targetDistanceMeters: Double?,
)
