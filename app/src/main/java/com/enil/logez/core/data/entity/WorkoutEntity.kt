package com.enil.logez.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.model.WorkoutStructure

/**
 * A live or completed logging session (PHASE2_PLAN.md §3.2). At most one row with
 * `status = IN_PROGRESS` at a time — this is the process-death recovery anchor (§9.5).
 * `durationSeconds` is explicit and editable, not derived, so pause/backdating both work.
 */
@Entity(
    tableName = "workouts",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("routine_id"), Index("status"), Index("started_at")],
)
data class WorkoutEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "routine_id") val routineId: String?,
    val title: String,
    val notes: String?,
    val status: WorkoutStatus,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** M11 circuits: copied from the source routine (or workout) at start; round k of exercise i
     * IS the `workout_sets` row with `orderIndex = k-1` under block i — no separate circuit
     * tables. `defaultValue` must match `MIGRATION_4_5`'s SQL default exactly (see
     * [RoutineEntity.structure]). */
    @ColumnInfo(name = "structure", defaultValue = "'REGULAR'") val structure: WorkoutStructure = WorkoutStructure.REGULAR,
    /** Which live surface owns this row across a process death — see [WorkoutKind]. `defaultValue`
     * must match `MIGRATION_8_9`'s SQL default exactly (see [structure]). */
    @ColumnInfo(name = "kind", defaultValue = "'STRENGTH'") val kind: WorkoutKind = WorkoutKind.STRENGTH,
)
