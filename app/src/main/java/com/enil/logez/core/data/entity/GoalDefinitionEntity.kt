package com.enil.logez.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.enil.logez.core.domain.model.GoalMetric
import com.enil.logez.core.domain.model.GoalPeriod

/**
 * M8d — a user-authored daily/weekly/monthly target over one [GoalMetric], across all workouts
 * (not scoped to a single exercise — the Owner's ask was overall reps/volume/duration/workout-
 * count, not per-exercise targets). Plain user-authored row, same shape as [RoutineEntity]:
 * created directly by the user, mutated only by their own edits — never bulk-rewritten by a
 * background computation. Progress against a goal is computed live from existing logged data
 * (`GoalProgressCalculator`), not persisted here or anywhere else.
 */
@Entity(tableName = "goal_definitions")
data class GoalDefinitionEntity(
    @PrimaryKey val id: String,
    val metric: GoalMetric,
    val period: GoalPeriod,
    @ColumnInfo(name = "target_value") val targetValue: Double,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
