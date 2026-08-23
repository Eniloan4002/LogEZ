package com.enil.logez.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One exercise slot within a routine (PHASE2_PLAN.md §3.2). `restTimerSeconds`: null = app
 * default at add-time, 0 = off. `supersetGroup`: null = not in a superset; members of the same
 * superset share an Int, unique within the routine.
 */
@Entity(
    tableName = "routine_exercises",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("routine_id"), Index("exercise_id")],
)
data class RoutineExerciseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "routine_id") val routineId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    @ColumnInfo(name = "superset_group") val supersetGroup: Int?,
    @ColumnInfo(name = "rest_timer_seconds") val restTimerSeconds: Int?,
    val notes: String?,
)
