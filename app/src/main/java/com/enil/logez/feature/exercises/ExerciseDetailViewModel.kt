package com.enil.logez.feature.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.common.Clock
import com.enil.logez.core.domain.model.ExerciseHistoryEntry
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * PHASE2_PLAN.md §5.2 "Exercise Detail". M2 builds the How-to and History tabs; the Summary tab
 * (metric graphs, PRs, Set Records) lands in M6 once the calc engines have real logged data to
 * chart — this ViewModel exposes only what M2 needs.
 */
@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val clock: Clock,
) : ViewModel() {
    private val exerciseId: String = checkNotNull(savedStateHandle[EXERCISE_ID_ARG])

    private val _uiState = MutableStateFlow(ExerciseDetailUiState())
    val uiState: StateFlow<ExerciseDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val exercise = exerciseRepository.getById(exerciseId)
            val history = if (exercise != null) workoutRepository.getExerciseHistory(exerciseId) else emptyList()
            _uiState.update { it.copy(isLoading = false, exercise = exercise, history = history) }
        }
    }

    /** §5.2: any exercise can be duplicated into a custom copy with no history attached. Returns the new id. */
    suspend fun duplicate(): String? {
        val original = _uiState.value.exercise ?: return null
        val newId = UUID.randomUUID().toString()
        val now = clock.now().toEpochMilliseconds()
        exerciseRepository.upsertCustom(
            original.copy(
                id = newId,
                isCustom = true,
                isBodyweightVolumeEligible = false,
                isDeleted = false,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return newId
    }

    /** §5.2: custom exercises only — soft delete keeps history queryable. */
    fun delete(onDeleted: () -> Unit) {
        val exercise = _uiState.value.exercise ?: return
        if (!exercise.isCustom) return
        viewModelScope.launch {
            exerciseRepository.softDeleteCustom(exercise.id)
            onDeleted()
        }
    }

    companion object {
        const val EXERCISE_ID_ARG = "exerciseId"
    }
}

data class ExerciseDetailUiState(
    val isLoading: Boolean = true,
    val exercise: Exercise? = null,
    val history: List<ExerciseHistoryEntry> = emptyList(),
)
