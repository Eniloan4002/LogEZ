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
import com.enil.logez.core.domain.model.SetType
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutSet(set: WorkoutSetEntity)

    @Update
    suspend fun updateWorkoutSet(set: WorkoutSetEntity)

    // Targeted single-field updates for the live logger's write-through edits (§5.1.3) — a
    // whole-row @Update reconstructed from the UI model would need every field (orderIndex,
    // completedAt, ...) the UI doesn't track, silently clobbering them on every keystroke.
    @Query("UPDATE workout_sets SET weight_kg = :kg WHERE id = :id")
    suspend fun updateWorkoutSetWeight(id: String, kg: Double?)

    @Query("UPDATE workout_sets SET reps = :reps WHERE id = :id")
    suspend fun updateWorkoutSetReps(id: String, reps: Int?)

    @Query("UPDATE workout_sets SET duration_seconds = :seconds WHERE id = :id")
    suspend fun updateWorkoutSetDuration(id: String, seconds: Int?)

    @Query("UPDATE workout_sets SET distance_meters = :meters WHERE id = :id")
    suspend fun updateWorkoutSetDistance(id: String, meters: Double?)

    @Query("UPDATE workout_sets SET custom_metric = :value WHERE id = :id")
    suspend fun updateWorkoutSetCustomMetric(id: String, value: Double?)

    @Query("UPDATE workout_sets SET set_type = :type WHERE id = :id")
    suspend fun updateWorkoutSetType(id: String, type: SetType)

    @Query("UPDATE workout_sets SET is_completed = :completed, completed_at = :completedAt WHERE id = :id")
    suspend fun updateWorkoutSetCompletion(id: String, completed: Boolean, completedAt: Long?)

    @Query("DELETE FROM workout_sets WHERE id = :id")
    suspend fun deleteWorkoutSetById(id: String)

    @Query("DELETE FROM workout_exercises WHERE id = :id")
    suspend fun deleteWorkoutExerciseById(id: String)

    @Query("UPDATE workout_exercises SET order_index = :orderIndex WHERE id = :id")
    suspend fun updateWorkoutExerciseOrderIndex(id: String, orderIndex: Int)

    @Query("UPDATE workout_exercises SET superset_group = :supersetGroup WHERE id = :id")
    suspend fun updateWorkoutExerciseSuperset(id: String, supersetGroup: Int?)

    @Query("UPDATE workout_exercises SET notes = :notes WHERE id = :id")
    suspend fun updateWorkoutExerciseNotes(id: String, notes: String?)

    @Query("UPDATE workout_exercises SET rest_timer_seconds = :seconds WHERE id = :id")
    suspend fun updateWorkoutExerciseRestTimer(id: String, seconds: Int?)

    @Query("UPDATE workout_exercises SET exercise_id = :newExerciseId WHERE id = :id")
    suspend fun updateWorkoutExerciseExerciseId(id: String, newExerciseId: String)

    /** §5.1.3 Replace Exercise: swaps the exercise id, rewrites the *same* set rows with carried-over values. */
    @Transaction
    suspend fun replaceWorkoutExerciseExercise(workoutExerciseId: String, newExerciseId: String, carriedOverSets: List<WorkoutSetEntity>) {
        updateWorkoutExerciseExerciseId(workoutExerciseId, newExerciseId)
        carriedOverSets.forEach { updateWorkoutSet(it) }
    }

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
     * `StatSet` DTO (volume/1RM/PR/chart engines), §8.10's PREVIOUS-values resolution, AND the
     * Exercise Detail History tab (§5.2, which additionally needs the workout title — carried
     * here rather than duplicating an almost-identical query). Repository maps rows to `StatSet`
     * or `ExerciseHistoryEntry` depending on the caller; kept as one flat projection.
     */
    @Query(
        """
        SELECT ws.id AS setId, w.id AS workoutId, w.title AS workoutTitle, w.started_at AS workoutStartedAt,
               w.routine_id AS routineId, ws.order_index AS orderIndex, ws.set_type AS setType,
               ws.weight_kg AS weightKg, ws.reps AS reps, ws.duration_seconds AS durationSeconds,
               ws.distance_meters AS distanceMeters, ws.custom_metric AS customMetric,
               ws.is_completed AS isCompleted, ws.rpe AS rpe
        FROM workout_sets ws
        JOIN workout_exercises we ON we.id = ws.workout_exercise_id
        JOIN workouts w ON w.id = we.workout_id
        WHERE we.exercise_id = :exerciseId AND w.status = 'COMPLETED'
        ORDER BY w.started_at DESC, ws.order_index ASC
        """,
    )
    suspend fun getStatRowsForExercise(exerciseId: String): List<ExerciseStatRow>

    /**
     * Most recent `completed_at` per exercise, across all COMPLETED-workout sets — backs the
     * Exercise Library's "recently logged first" sort (§5.2).
     */
    @Query(
        """
        SELECT we.exercise_id AS exerciseId, MAX(ws.completed_at) AS lastCompletedAt
        FROM workout_sets ws
        JOIN workout_exercises we ON we.id = ws.workout_exercise_id
        JOIN workouts w ON w.id = we.workout_id
        WHERE ws.is_completed = 1 AND w.status = 'COMPLETED'
        GROUP BY we.exercise_id
        """,
    )
    suspend fun getMostRecentUsagePerExercise(): List<ExerciseRecencyRow>

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

    // --- M4c finish flow (§5.1.8) ---

    /** §5.1.8 save transaction step 1: "delete uncompleted `workout_sets`". */
    @Query(
        """
        DELETE FROM workout_sets
        WHERE is_completed = 0
          AND workout_exercise_id IN (SELECT id FROM workout_exercises WHERE workout_id = :workoutId)
        """,
    )
    suspend fun deleteUncompletedSetsForWorkout(workoutId: String)

    /** Prunes exercises the uncompleted-set purge left empty — deleting sets does not cascade upward to their parent. */
    @Query(
        """
        DELETE FROM workout_exercises
        WHERE workout_id = :workoutId
          AND id NOT IN (SELECT DISTINCT workout_exercise_id FROM workout_sets)
        """,
    )
    suspend fun deleteEmptyExercisesForWorkout(workoutId: String)

    /**
     * §5.1.8's save transaction, atomic: purge uncompleted sets and any exercise they emptied,
     * then flip the workout to COMPLETED with its final (possibly user-edited, possibly
     * backdated) timestamps. Routine updates and the PR rebuild follow *after* this commits —
     * they read the COMPLETED rows this writes.
     */
    @Transaction
    suspend fun finishWorkout(workout: WorkoutEntity) {
        deleteUncompletedSetsForWorkout(workout.id)
        deleteEmptyExercisesForWorkout(workout.id)
        updateWorkout(workout)
    }

    /**
     * Every set of one workout with its exercise id, in one query — the finish summary's volume
     * and set counts, and the "which exercises need a PR rebuild" list, both need the whole
     * workout at once rather than the per-exercise N+1 the live logger uses.
     *
     * Carries `workoutExerciseId`/`exerciseOrderIndex` as well as `exerciseId`: the same exercise
     * can legitimately occupy two blocks in one session (a main lift plus a burnout block), so
     * anything pairing logged sets back to routine slots must key on the *block*, not the
     * exercise — keying on `exerciseId` alone silently merges the two.
     */
    @Query(
        """
        SELECT ws.id AS setId, we.exercise_id AS exerciseId, we.id AS workoutExerciseId,
               we.order_index AS exerciseOrderIndex, w.id AS workoutId, w.started_at AS workoutStartedAt,
               w.routine_id AS routineId, ws.order_index AS orderIndex, ws.set_type AS setType,
               ws.weight_kg AS weightKg, ws.reps AS reps, ws.duration_seconds AS durationSeconds,
               ws.distance_meters AS distanceMeters, ws.custom_metric AS customMetric,
               ws.is_completed AS isCompleted, ws.rpe AS rpe
        FROM workout_sets ws
        JOIN workout_exercises we ON we.id = ws.workout_exercise_id
        JOIN workouts w ON w.id = we.workout_id
        WHERE w.id = :workoutId
        ORDER BY we.order_index ASC, ws.order_index ASC
        """,
    )
    suspend fun getSetsWithExerciseForWorkout(workoutId: String): List<WorkoutSetWithExerciseRow>

    /**
     * Ordinal workout count for the summary's "Workout #47" line — COMPLETED only, counting this
     * one. Chronological rank by `started_at`, so a backdated session takes the number it would
     * have had at the time. Exact `started_at` ties break on `id` so the ordinal is a total order:
     * a plain `<=` counted both sides of a tie and handed the same number to two workouts.
     */
    @Query(
        """
        SELECT COUNT(*) FROM workouts
        WHERE status = 'COMPLETED'
          AND (started_at < :startedAt OR (started_at = :startedAt AND id <= :workoutId))
        """,
    )
    suspend fun countCompletedWorkoutsUpTo(startedAt: Long, workoutId: String): Int
}

/** Flat projection backing [WorkoutDao.getSetsWithExerciseForWorkout] — carries the exercise and its block identity, both of which `StatSet` deliberately omits. */
data class WorkoutSetWithExerciseRow(
    val setId: String,
    val exerciseId: String,
    val workoutExerciseId: String,
    val exerciseOrderIndex: Int,
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

/** Flat projection backing [WorkoutDao.getStatRowsForExercise] — mapped to `StatSet` or `ExerciseHistoryEntry`. */
data class ExerciseStatRow(
    val setId: String,
    val workoutId: String,
    val workoutTitle: String,
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

/** Flat projection backing [WorkoutDao.getMostRecentUsagePerExercise]. */
data class ExerciseRecencyRow(
    val exerciseId: String,
    val lastCompletedAt: Long?,
)
