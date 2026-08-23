package com.enil.logez.core.data.repository

import com.enil.logez.core.data.dao.ExerciseStatRow
import com.enil.logez.core.data.dao.WorkoutDao
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.calc.StatSet
import com.enil.logez.core.domain.model.ExerciseHistoryEntry
import com.enil.logez.core.domain.repository.WorkoutRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class WorkoutRepositoryImpl @Inject constructor(
    private val dao: WorkoutDao,
) : WorkoutRepository {
    override suspend fun getInProgress(): WorkoutEntity? = dao.getInProgress()
    override fun observeInProgress(): Flow<WorkoutEntity?> = dao.observeInProgress()
    override fun observeCompleted(): Flow<List<WorkoutEntity>> = dao.observeCompleted()
    override suspend fun getById(id: String): WorkoutEntity? = dao.getById(id)
    override fun observeById(id: String): Flow<WorkoutEntity?> = dao.observeById(id)
    override suspend fun deleteById(id: String) = dao.deleteWorkoutById(id)

    override suspend fun getExercisesForWorkout(workoutId: String): List<WorkoutExerciseEntity> =
        dao.getExercisesForWorkout(workoutId)

    override suspend fun getSetsForWorkoutExercise(workoutExerciseId: String): List<WorkoutSetEntity> =
        dao.getSetsForWorkoutExercise(workoutExerciseId)

    override suspend fun insertFullWorkout(
        workout: WorkoutEntity,
        exercises: List<WorkoutExerciseEntity>,
        sets: List<WorkoutSetEntity>,
    ) = dao.insertFullWorkout(workout, exercises, sets)

    override suspend fun getStatSetsForExercise(exerciseId: String): List<StatSet> =
        dao.getStatRowsForExercise(exerciseId).map { it.toStatSet() }

    override suspend fun getExerciseHistory(exerciseId: String): List<ExerciseHistoryEntry> =
        dao.getStatRowsForExercise(exerciseId).map { it.toHistoryEntry() }

    override suspend fun getRecentUsageTimestamps(): Map<String, Long> =
        dao.getMostRecentUsagePerExercise()
            .mapNotNull { row -> row.lastCompletedAt?.let { row.exerciseId to it } }
            .toMap()
}

private fun ExerciseStatRow.toStatSet() = StatSet(
    setId = setId,
    workoutId = workoutId,
    workoutStartedAt = workoutStartedAt,
    orderIndex = orderIndex,
    setType = setType,
    weightKg = weightKg,
    reps = reps,
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    customMetric = customMetric,
    isCompleted = isCompleted,
    rpe = rpe,
)

private fun ExerciseStatRow.toHistoryEntry() = ExerciseHistoryEntry(
    workoutId = workoutId,
    workoutTitle = workoutTitle,
    workoutStartedAt = workoutStartedAt,
    setId = setId,
    setOrderIndex = orderIndex,
    setType = setType,
    weightKg = weightKg,
    reps = reps,
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    rpe = rpe,
    customMetric = customMetric,
)
