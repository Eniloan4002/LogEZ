package com.enil.logez.feature.routines

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.EffortScale
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.model.WorkoutStructure
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.feature.history.DetailExerciseBlock
import com.enil.logez.feature.history.DetailSetRow
import com.enil.logez.feature.history.WorkoutToRoutineConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * R-1 (2026-10-01): a Recent workout opened like a routine -- its exercise blocks and sets as Start
 * would fill them in. The numbers are last time's values, so the rows carry no effort and no
 * trophies (Start blanks effort, and a record belongs to History's record of the session).
 *
 * Reads: the workout, its exercises, each block's sets, and one lookup per distinct exercise
 * (History's detail does the same, minus the records and heart-rate queries it needs).
 * "Start" is [RecentStartActions] -- the same confirmation, conflict and Copy Workout semantics as
 * the Start pill on the list row.
 */
@HiltViewModel
class RecentWorkoutDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val settingsRepository: SettingsRepository,
    private val workoutToRoutineConverter: WorkoutToRoutineConverter,
    starter: RecentWorkoutStarter,
) : ViewModel(), RecentStartActions by starter {

    private val workoutId: String = checkNotNull(savedStateHandle[WORKOUT_ID_ARG])

    private val _uiState = MutableStateFlow(RecentWorkoutDetailUiState())
    val uiState: StateFlow<RecentWorkoutDetailUiState> = _uiState.asStateFlow()

    /**
     * Re-reads from Room. Runs once on creation, and the screen calls it on every RESUME: Open in
     * History can edit or delete the workout, and this screen is what the user returns to.
     */
    fun refresh() {
        // A newer read replaces an older one still in flight, so a slow read can't land last.
        loadJob?.cancel()
        loadJob = viewModelScope.launch { load() }
    }

    private var loadJob: Job? = null

    init {
        refresh()
    }

    private suspend fun load() {
        val workout = workoutRepository.getById(workoutId)
        // Recent only lists finished workouts; a row that is gone, or not finished, has nothing to show.
        if (workout == null || workout.status != WorkoutStatus.COMPLETED) {
            _uiState.value = RecentWorkoutDetailUiState(isLoading = false, isMissing = true)
            return
        }
        val settings = settingsRepository.settings.first()
        val workoutExercises = workoutRepository.getExercisesForWorkout(workoutId).sortedBy { it.orderIndex }
        val exercisesById = workoutExercises.map { it.exerciseId }.distinct()
            .mapNotNull { id -> exerciseRepository.getById(id)?.let { id to it } }
            .toMap()
        val blocks = workoutExercises.map { we ->
            DetailExerciseBlock(
                workoutExercise = we,
                exercise = exercisesById[we.exerciseId],
                sets = workoutRepository.getSetsForWorkoutExercise(we.id).sortedBy { it.orderIndex }.map { ws ->
                    DetailSetRow(
                        setId = ws.id,
                        orderIndex = ws.orderIndex,
                        setType = ws.setType,
                        weightKg = ws.weightKg,
                        reps = ws.reps,
                        durationSeconds = ws.durationSeconds,
                        distanceMeters = ws.distanceMeters,
                        customMetric = ws.customMetric,
                        // What Start fills in: every row shows its value, with no effort and no record.
                        rpe = null,
                        isCompleted = true,
                        pr = null,
                    )
                },
            )
        }
        _uiState.value = RecentWorkoutDetailUiState(
            isLoading = false,
            summary = RecentWorkoutCardModel(
                workoutId = workout.id,
                title = workout.title,
                startedAtMillis = workout.startedAt,
                durationSeconds = workout.durationSeconds,
                exerciseCount = blocks.size,
                isFromRoutine = workout.routineId != null,
            ),
            notes = workout.notes?.takeIf { it.isNotBlank() },
            isCircuit = workout.structure == WorkoutStructure.CIRCUIT,
            weightUnit = settings.weightUnit,
            distanceUnit = settings.distanceUnit,
            effortScale = settings.effortScale,
            blocks = blocks,
        )
    }

    /** "Save as Routine": the existing converter, returning the new routine's id for the builder. */
    suspend fun saveAsRoutine(): String? = workoutToRoutineConverter.convert(workoutId)

    companion object {
        const val WORKOUT_ID_ARG = "workoutId"
    }
}

data class RecentWorkoutDetailUiState(
    val isLoading: Boolean = true,
    val isMissing: Boolean = false,
    val summary: RecentWorkoutCardModel? = null,
    val notes: String? = null,
    val isCircuit: Boolean = false,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val effortScale: EffortScale = EffortScale.RPE,
    val blocks: List<DetailExerciseBlock> = emptyList(),
)
