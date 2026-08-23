package com.enil.logez.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.enil.logez.core.domain.model.SetType

/**
 * A logged set (PHASE2_PLAN.md §3.2, §8.1). Which value columns are populated is dictated by
 * the parent exercise's [com.enil.logez.core.domain.model.ExerciseType]. `rpe` is restricted to
 * {6, 7, 7.5, 8, 8.5, 9, 9.5, 10} — validated in the domain layer, not the DB. `customMetric`
 * is used only by the two seed-only stair-machine exercise types.
 */
@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workout_exercise_id")],
)
data class WorkoutSetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workout_exercise_id") val workoutExerciseId: String,
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    @ColumnInfo(name = "set_type") val setType: SetType,
    @ColumnInfo(name = "weight_kg") val weightKg: Double?,
    val reps: Int?,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int?,
    @ColumnInfo(name = "distance_meters") val distanceMeters: Double?,
    val rpe: Double?,
    @ColumnInfo(name = "custom_metric") val customMetric: Double?,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
)
