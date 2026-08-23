package com.enil.logez.feature.exercises

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.common.Clock
import com.enil.logez.core.data.media.ExerciseMediaStore
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.core.domain.repository.ExerciseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * PHASE2_PLAN.md §5.2 "Custom exercise creation" / "Edit (custom only)". [exerciseId] absent
 * (or the value is null) = create mode; present = edit mode, where [ExerciseType] becomes
 * immutable — "greyed with a 'Type can't be changed' hint" (§5.2, library-analytics.md §1).
 */
@HiltViewModel
class CustomExerciseEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val exerciseRepository: ExerciseRepository,
    private val mediaStore: ExerciseMediaStore,
    private val clock: Clock,
) : ViewModel() {
    private val editingId: String? = savedStateHandle.get<String>(EXERCISE_ID_ARG)
    val isEditMode: Boolean = editingId != null

    private var loadedCreatedAt: Long = clock.now().toEpochMilliseconds()

    private val _uiState = MutableStateFlow(
        CustomExerciseEditorUiState(
            isEditMode = isEditMode,
            isLoading = isEditMode,
            name = savedStateHandle.get<String>(PREFILL_NAME_ARG).orEmpty(),
        ),
    )
    val uiState: StateFlow<CustomExerciseEditorUiState> = _uiState.asStateFlow()

    init {
        val id = editingId
        if (id != null) {
            viewModelScope.launch {
                val existing = exerciseRepository.getById(id)
                if (existing != null) {
                    loadedCreatedAt = existing.createdAt
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            name = existing.name,
                            equipment = existing.equipment,
                            primaryMuscleGroup = existing.primaryMuscleGroup,
                            secondaryMuscleGroups = existing.secondaryMuscleGroups.toSet(),
                            exerciseType = existing.exerciseType,
                            mediaPath = existing.mediaPath,
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, loadError = true) }
                }
            }
        }
    }

    fun onNameChange(name: String) = _uiState.update { it.copy(name = name, nameError = false) }

    fun onEquipmentChange(equipment: Equipment) = _uiState.update { it.copy(equipment = equipment) }

    fun onPrimaryMuscleChange(muscle: MuscleGroup) = _uiState.update {
        it.copy(primaryMuscleGroup = muscle, secondaryMuscleGroups = it.secondaryMuscleGroups - muscle)
    }

    fun onSecondaryMuscleToggle(muscle: MuscleGroup) = _uiState.update {
        if (muscle == it.primaryMuscleGroup) return@update it
        val updated = if (muscle in it.secondaryMuscleGroups) it.secondaryMuscleGroups - muscle else it.secondaryMuscleGroups + muscle
        it.copy(secondaryMuscleGroups = updated)
    }

    /** No-op in edit mode — exercise type is immutable after creation (§5.2). */
    fun onExerciseTypeChange(type: ExerciseType) {
        if (isEditMode) return
        _uiState.update { it.copy(exerciseType = type) }
    }

    fun onImagePicked(uri: Uri) {
        viewModelScope.launch {
            val path = mediaStore.copyToAppStorage(uri)
            if (path != null) _uiState.update { it.copy(mediaPath = path) }
        }
    }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(nameError = true) }
            return
        }
        viewModelScope.launch {
            val exercise = Exercise(
                id = editingId ?: UUID.randomUUID().toString(),
                name = state.name.trim(),
                exerciseType = state.exerciseType,
                primaryMuscleGroup = state.primaryMuscleGroup,
                secondaryMuscleGroups = state.secondaryMuscleGroups.toList(),
                equipment = state.equipment,
                instructions = "",
                mediaPath = state.mediaPath,
                isCustom = true,
                isBodyweightVolumeEligible = false,
                isDeleted = false,
                createdAt = loadedCreatedAt,
                updatedAt = clock.now().toEpochMilliseconds(),
            )
            exerciseRepository.upsertCustom(exercise)
            onSaved()
        }
    }

    companion object {
        const val EXERCISE_ID_ARG = "exerciseId"
        const val PREFILL_NAME_ARG = "prefillName"
    }
}

data class CustomExerciseEditorUiState(
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val loadError: Boolean = false,
    val name: String = "",
    val nameError: Boolean = false,
    val equipment: Equipment = Equipment.NONE,
    val primaryMuscleGroup: MuscleGroup = MuscleGroup.OTHER,
    val secondaryMuscleGroups: Set<MuscleGroup> = emptySet(),
    val exerciseType: ExerciseType = ExerciseType.WEIGHT_REPS,
    val mediaPath: String? = null,
)
