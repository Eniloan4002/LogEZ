package com.enil.logez.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkout(workout: WorkoutEntity)

    @Update
    suspend fun updateWorkout(workout: WorkoutEntity)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkoutById(id: String)

    @Query("SELECT * FROM workouts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<WorkoutEntity?>

    /** Spine anchor: at most one IN_PROGRESS row at a time — process-death recovery reads this. */
    @Query("SELECT * FROM workouts WHERE status = 'IN_PROGRESS' LIMIT 1")
    suspend fun getInProgress(): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE status = 'IN_PROGRESS' LIMIT 1")
    fun observeInProgress(): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workouts WHERE status = 'COMPLETED' ORDER BY started_at DESC")
    fun observeCompleted(): Flow<List<WorkoutEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutExercises(exercises: List<WorkoutExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutSets(sets: List<WorkoutSetEntity>)

    @Update
    suspend fun updateWorkoutSet(set: WorkoutSetEntity)

    @Query("SELECT * FROM workout_exercises WHERE workout_id = :workoutId ORDER BY order_index ASC")
    suspend fun getExercisesForWorkout(workoutId: String): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM workout_exercises WHERE workout_id = :workoutId ORDER BY order_index ASC")
    fun observeExercisesForWorkout(workoutId: String): Flow<List<WorkoutExerciseEntity>>

    @Query("SELECT * FROM workout_sets WHERE workout_exercise_id = :workoutExerciseId ORDER BY order_index ASC")
    suspend fun getSetsForWorkoutExercise(workoutExerciseId: String): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_sets WHERE workout_exercise_id = :workoutExerciseId ORDER BY order_index ASC")
    fun observeSetsForWorkoutExercise(workoutExerciseId: String): Flow<List<WorkoutSetEntity>>

    /**
     * Raw joined rows for one exercise across COMPLETED workouts — the source data for §8's
     * `StatSet` DTO (volume/1RM/PR/chart engines) and §8.10's PREVIOUS-values resolution.
     * Repository maps rows to `StatSet`; kept as a flat projection so no new Room POJO is needed.
     */
    @Query(
        """
        SELECT ws.id AS setId, w.id AS workoutId, w.started_at AS workoutStartedAt, w.routine_id AS routineId,
               ws.order_index AS orderIndex, ws.set_type AS setType, ws.weight_kg AS weightKg, ws.reps AS reps,
               ws.duration_seconds AS durationSeconds, ws.distance_meters AS distanceMeters,
               ws.custom_metric AS customMetric, ws.is_completed AS isCompleted, ws.rpe AS rpe
        FROM workout_sets ws
        JOIN workout_exercises we ON we.id = ws.workout_exercise_id
        JOIN workouts w ON w.id = we.workout_id
        WHERE we.exercise_id = :exerciseId AND w.status = 'COMPLETED'
        ORDER BY w.started_at DESC, ws.order_index ASC
        """,
    )
    suspend fun getStatRowsForExercise(exerciseId: String): List<ExerciseStatRow>

    /** Whole-structure insert (workout + exercises + sets) — used by tests and the M4c save transaction. */
    @Transaction
    suspend fun insertFullWorkout(
        workout: WorkoutEntity,
        exercises: List<WorkoutExerciseEntity>,
        sets: List<WorkoutSetEntity>,
    ) {
        upsertWorkout(workout)
        insertWorkoutExercises(exercises)
        insertWorkoutSets(sets)
    }
}

/** Flat projection backing [WorkoutDao.getStatRowsForExercise] — mapped to `StatSet` in the repository. */
data class ExerciseStatRow(
    val setId: String,
    val workoutId: String,
    val workoutStartedAt: Long,
    val routineId: String?,
    val orderIndex: Int,
    val setType: com.enil.logez.core.domain.model.SetType,
    val weightKg: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
    val distanceMeters: Double?,
    val customMetric: Double?,
    val isCompleted: Boolean,
    val rpe: Double?,
)
