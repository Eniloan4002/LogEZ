package com.enil.logez.core.domain.repository

import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.calc.StatSet
import com.enil.logez.core.domain.model.ExerciseHistoryEntry
import kotlinx.coroutines.flow.Flow

interface WorkoutRepository {
    suspend fun getInProgress(): WorkoutEntity?
    fun observeInProgress(): Flow<WorkoutEntity?>
    fun observeCompleted(): Flow<List<WorkoutEntity>>
    suspend fun getById(id: String): WorkoutEntity?
    fun observeById(id: String): Flow<WorkoutEntity?>
    suspend fun deleteById(id: String)

    suspend fun getExercisesForWorkout(workoutId: String): List<WorkoutExerciseEntity>
    suspend fun getSetsForWorkoutExercise(workoutExerciseId: String): List<WorkoutSetEntity>

    suspend fun insertFullWorkout(
        workout: WorkoutEntity,
        exercises: List<WorkoutExerciseEntity>,
        sets: List<WorkoutSetEntity>,
    )

    /** §8.1's join, already mapped to the calc-engine input type. */
    suspend fun getStatSetsForExercise(exerciseId: String): List<StatSet>

    /** Exercise Detail's History tab (§5.2): every COMPLETED session containing the exercise, newest first. */
    suspend fun getExerciseHistory(exerciseId: String): List<ExerciseHistoryEntry>

    /** Exercise Library's "recently logged first" sort (§5.2): exerciseId -> most recent completedAt. */
    suspend fun getRecentUsageTimestamps(): Map<String, Long>
}
