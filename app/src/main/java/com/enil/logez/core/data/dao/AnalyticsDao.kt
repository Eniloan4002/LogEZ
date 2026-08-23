package com.enil.logez.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import com.enil.logez.core.domain.model.MuscleGroup

/**
 * Cross-entity aggregate queries backing §8.7/§8.8 (streak, calendar, muscle stats) and the
 * History-feed card stats. Kept minimal at M1 — extended as the consuming M5/M6 screens land,
 * per the plan's own "DAOs... one per aggregate, extended incrementally" architecture note.
 */
@Dao
interface AnalyticsDao {
    /** Distinct calendar dates (device-local, computed by the repository) with >=1 completed workout. */
    @Query("SELECT started_at FROM workouts WHERE status = 'COMPLETED' ORDER BY started_at DESC")
    suspend fun getCompletedWorkoutTimestamps(): List<Long>

    @Query(
        """
        SELECT e.primary_muscle_group AS muscleGroup, w.started_at AS workoutStartedAt, ws.set_type AS setType, ws.is_completed AS isCompleted
        FROM workout_sets ws
        JOIN workout_exercises we ON we.id = ws.workout_exercise_id
        JOIN workouts w ON w.id = we.workout_id
        JOIN exercises e ON e.id = we.exercise_id
        WHERE w.status = 'COMPLETED'
        """,
    )
    suspend fun getMuscleSetRows(): List<MuscleSetRow>
}

/** Flat projection backing [AnalyticsDao.getMuscleSetRows] — mapped to `MuscleStatsCalculator.MuscleSetInput`. */
data class MuscleSetRow(
    val muscleGroup: MuscleGroup,
    val workoutStartedAt: Long,
    val setType: com.enil.logez.core.domain.model.SetType,
    val isCompleted: Boolean,
)
