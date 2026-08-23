package com.enil.logez.fakes

import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.calc.StatSet
import com.enil.logez.core.domain.model.ExerciseHistoryEntry
import com.enil.logez.core.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory fake (PHASE2_PLAN.md §10.1 rule 2). Minimal today; extended as later milestones need it. */
class FakeWorkoutRepository(
    workouts: List<WorkoutEntity> = emptyList(),
    private val exercisesByWorkout: Map<String, List<WorkoutExerciseEntity>> = emptyMap(),
    private val setsByWorkoutExercise: Map<String, List<WorkoutSetEntity>> = emptyMap(),
    private val statSetsByExercise: Map<String, List<StatSet>> = emptyMap(),
    private val historyByExercise: Map<String, List<ExerciseHistoryEntry>> = emptyMap(),
    private val recentUsage: Map<String, Long> = emptyMap(),
) : WorkoutRepository {
    private val workoutsState = MutableStateFlow(workouts.associateBy { it.id })

    override suspend fun getInProgress(): WorkoutEntity? = workoutsState.value.values.find { it.status.name == "IN_PROGRESS" }
    override fun observeInProgress(): Flow<WorkoutEntity?> = workoutsState.map { m -> m.values.find { it.status.name == "IN_PROGRESS" } }
    override fun observeCompleted(): Flow<List<WorkoutEntity>> = workoutsState.map { m -> m.values.filter { it.status.name == "COMPLETED" } }
    override suspend fun getById(id: String): WorkoutEntity? = workoutsState.value[id]
    override fun observeById(id: String): Flow<WorkoutEntity?> = workoutsState.map { it[id] }
    override suspend fun deleteById(id: String) { workoutsState.value = workoutsState.value - id }

    override suspend fun getExercisesForWorkout(workoutId: String): List<WorkoutExerciseEntity> =
        exercisesByWorkout[workoutId].orEmpty()

    override suspend fun getSetsForWorkoutExercise(workoutExerciseId: String): List<WorkoutSetEntity> =
        setsByWorkoutExercise[workoutExerciseId].orEmpty()

    override suspend fun insertFullWorkout(
        workout: WorkoutEntity,
        exercises: List<WorkoutExerciseEntity>,
        sets: List<WorkoutSetEntity>,
    ) {
        workoutsState.value = workoutsState.value + (workout.id to workout)
    }

    override suspend fun getStatSetsForExercise(exerciseId: String): List<StatSet> = statSetsByExercise[exerciseId].orEmpty()

    override suspend fun getExerciseHistory(exerciseId: String): List<ExerciseHistoryEntry> = historyByExercise[exerciseId].orEmpty()

    override suspend fun getRecentUsageTimestamps(): Map<String, Long> = recentUsage
}
