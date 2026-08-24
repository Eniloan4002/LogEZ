package com.enil.logez.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.data.entity.PersonalRecordEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.domain.calc.StatSet
import com.enil.logez.core.domain.calc.VolumeCalculator
import com.enil.logez.core.domain.calc.isIncluded
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.PersonalRecordsRepository
import com.enil.logez.core.domain.repository.RoutineRepository
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.feature.workout.StartResult
import com.enil.logez.feature.workout.WorkoutStarter
import com.enil.logez.feature.workout.session.WorkoutSessionController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** PHASE2_PLAN.md §5.2 "Workout Detail": read-only record of one completed workout. */
@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val routineRepository: RoutineRepository,
    private val personalRecordsRepository: PersonalRecordsRepository,
    private val settingsRepository: SettingsRepository,
    private val workoutDeleter: WorkoutDeleter,
    private val workoutToRoutineConverter: WorkoutToRoutineConverter,
    private val workoutStarter: WorkoutStarter,
    private val sessionController: WorkoutSessionController,
) : ViewModel() {
    private val workoutId: String = checkNotNull(savedStateHandle[WORKOUT_ID_ARG])

    private val _uiState = MutableStateFlow(WorkoutDetailUiState())
    val uiState: StateFlow<WorkoutDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { reload() }
    }

    /** Re-reads from Room — the screen calls this on RESUME, since an edit rewrites what it shows. */
    fun refresh() {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val workout = workoutRepository.getById(workoutId)
        if (workout == null) {
            // Reachable if this workout was just deleted from another surface (or its own overflow
            // menu, whose onDeleted callback pops this screen before this ever runs) — a missing
            // row is not an error, it just means there is nothing left to show.
            _uiState.update { it.copy(isLoading = false, isMissing = true) }
            return
        }

        val includeWarmups = settingsRepository.settings.first().includeWarmupsInStats
        val routineName = workout.routineId?.let { routineRepository.getRoutineById(it)?.name }

        val workoutExercises = workoutRepository.getExercisesForWorkout(workoutId).sortedBy { it.orderIndex }
        // Fetched once: session-scoped PrTypes (BEST_SESSION_VOLUME, MOST_SESSION_REPS) carry a
        // null workoutSetId and never appear on a set badge, but they still count as a record this
        // workout earned — hasRecords below must see them too, not just the set-scoped subset.
        val workoutPrs = personalRecordsRepository.getForWorkout(workoutId)
        val prsBySetId = workoutPrs.filter { it.workoutSetId != null }.associateBy { it.workoutSetId }

        val exerciseBlocks = workoutExercises.map { we ->
            val exercise = exerciseRepository.getById(we.exerciseId)
            val sets = workoutRepository.getSetsForWorkoutExercise(we.id).sortedBy { it.orderIndex }
            DetailExerciseBlock(
                workoutExercise = we,
                exercise = exercise,
                sets = sets.map { ws ->
                    DetailSetRow(
                        setId = ws.id,
                        orderIndex = ws.orderIndex,
                        setType = ws.setType,
                        weightKg = ws.weightKg,
                        reps = ws.reps,
                        durationSeconds = ws.durationSeconds,
                        distanceMeters = ws.distanceMeters,
                        customMetric = ws.customMetric,
                        rpe = ws.rpe,
                        isCompleted = ws.isCompleted,
                        pr = prsBySetId[ws.id],
                    )
                },
            )
        }

        val rows = workoutRepository.getSetsWithExerciseForWorkout(workoutId)
        val included = rows.filter { isIncluded(it.set, includeWarmups) }
        val volumeKg = included.sumOf { row -> setVolume(row.exerciseId, row.set) }

        _uiState.value = WorkoutDetailUiState(
            isLoading = false,
            workout = workout,
            routineName = routineName,
            durationSeconds = workout.durationSeconds,
            volumeKg = volumeKg,
            completedSetCount = included.size,
            hasRecords = workoutPrs.isNotEmpty(),
            exerciseBlocks = exerciseBlocks,
        )
    }

    private suspend fun setVolume(exerciseId: String, set: StatSet): Double {
        val exercise = exerciseRepository.getById(exerciseId) ?: return 0.0
        return VolumeCalculator.setVolume(
            exerciseType = exercise.exerciseType,
            isBodyweightVolumeEligible = exercise.isBodyweightVolumeEligible,
            weightKg = set.weightKg,
            reps = set.reps,
            bodyweightKg = null,
        )
    }

    /** §5.2 "Copy Workout" — mirrors [com.enil.logez.feature.routines.RoutineDetailViewModel.startRoutine]. */
    suspend fun startCopy(): StartResult {
        val result = workoutStarter.startFromWorkoutOrConflict(workoutId)
        if (result is StartResult.Started) sessionController.startSession(result.workoutId)
        return result
    }

    suspend fun discardInProgressAndStartCopy(): String {
        workoutStarter.discardInProgress()
        sessionController.endSession()
        val id = workoutStarter.startFromWorkout(workoutId)
        sessionController.startSession(id)
        return id
    }

    /** §5.2 "Save as Routine" — returns the new routine's id so the caller can open its editor. */
    suspend fun saveAsRoutine(): String? = workoutToRoutineConverter.convert(workoutId)

    /** §5.2 "Delete Workout" — hard delete + PR rebuild for every exercise the workout touched. */
    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            workoutDeleter.delete(workoutId)
            onDeleted()
        }
    }

    companion object {
        const val WORKOUT_ID_ARG = "workoutId"
    }
}

data class WorkoutDetailUiState(
    val isLoading: Boolean = true,
    val isMissing: Boolean = false,
    val workout: WorkoutEntity? = null,
    val routineName: String? = null,
    val durationSeconds: Int = 0,
    val volumeKg: Double = 0.0,
    val completedSetCount: Int = 0,
    val hasRecords: Boolean = false,
    val exerciseBlocks: List<DetailExerciseBlock> = emptyList(),
)

data class DetailExerciseBlock(
    val workoutExercise: WorkoutExerciseEntity,
    val exercise: Exercise?,
    val sets: List<DetailSetRow>,
)

data class DetailSetRow(
    val setId: String,
    val orderIndex: Int,
    val setType: com.enil.logez.core.domain.model.SetType,
    val weightKg: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
    val distanceMeters: Double?,
    val customMetric: Double?,
    val rpe: Double?,
    val isCompleted: Boolean,
    val pr: PersonalRecordEntity?,
)
