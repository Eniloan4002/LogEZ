package com.enil.logez.feature.routines

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.common.Clock
import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.RoutineExerciseEntity
import com.enil.logez.core.data.entity.RoutineSetEntity
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.RoutineRepository
import com.enil.logez.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * PHASE2_PLAN.md §5.1.2 Routine Builder. Holds an in-memory [RoutineDraft]; Save flattens it to
 * `routines`/`routine_exercises`/`routine_sets` in one transaction (§10 rule: no partial writes).
 */
@HiltViewModel
class RoutineBuilderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routineRepository: RoutineRepository,
    private val exerciseRepository: ExerciseRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    val isEditMode: Boolean = savedStateHandle.get<String>(ROUTINE_ID_ARG) != null

    private val draft = MutableStateFlow(RoutineDraft(id = "", folderId = null, createdAt = 0))
    private val isLoading = MutableStateFlow(true)
    private val supersetSource = MutableStateFlow<String?>(null)
    private val reorderModeActive = MutableStateFlow(false)
    private var loadedSnapshot: RoutineDraft? = null

    val uiState: StateFlow<RoutineBuilderUiState> = combine(
        draft,
        isLoading,
        settingsRepository.settings,
        supersetSource,
        reorderModeActive,
    ) { d, loading, settings, supersetSourceId, reorderMode ->
        RoutineBuilderUiState(
            isLoading = loading,
            isEditMode = isEditMode,
            title = d.title,
            exercises = d.exercises,
            defaultRestTimerSeconds = settings.defaultRestTimerSeconds,
            isDirty = !loading && loadedSnapshot != null && d != loadedSnapshot,
            canSave = d.title.isNotBlank() && d.exercises.isNotEmpty(),
            supersetSelectionActive = supersetSourceId != null,
            supersetSourceExerciseId = supersetSourceId,
            reorderModeActive = reorderMode,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RoutineBuilderUiState())

    init {
        val routineId = savedStateHandle.get<String>(ROUTINE_ID_ARG)
        val folderIdArg = savedStateHandle.get<String>(FOLDER_ID_ARG)
        viewModelScope.launch {
            val loaded = if (routineId != null) {
                loadExistingDraft(routineId)
            } else {
                RoutineDraft(id = UUID.randomUUID().toString(), folderId = folderIdArg, createdAt = clock.now().toEpochMilliseconds())
            }
            draft.value = loaded
            loadedSnapshot = loaded
            isLoading.value = false
        }
    }

    private suspend fun loadExistingDraft(routineId: String): RoutineDraft {
        val routine = routineRepository.getRoutineById(routineId)
            ?: return RoutineDraft(id = UUID.randomUUID().toString(), folderId = null, createdAt = clock.now().toEpochMilliseconds())
        val routineExercises = routineRepository.getExercisesForRoutine(routineId)
        val exerciseDrafts = routineExercises.map { re ->
            val exercise = exerciseRepository.getById(re.exerciseId)
            val sets = routineRepository.getSetsForRoutineExercise(re.id)
            RoutineExerciseDraft(
                id = re.id,
                exerciseId = re.exerciseId,
                exerciseName = exercise?.name.orEmpty(),
                exerciseType = exercise?.exerciseType ?: ExerciseType.WEIGHT_REPS,
                supersetGroup = re.supersetGroup,
                restTimerSeconds = re.restTimerSeconds,
                notes = re.notes,
                isRepRangeMode = sets.any { it.targetRepRangeMin != null },
                sets = sets.map { s ->
                    RoutineSetDraft(
                        id = s.id,
                        setType = s.setType,
                        targetWeightKg = s.targetWeightKg,
                        targetReps = s.targetReps,
                        targetRepRangeMin = s.targetRepRangeMin,
                        targetRepRangeMax = s.targetRepRangeMax,
                        targetDurationSeconds = s.targetDurationSeconds,
                        targetDistanceMeters = s.targetDistanceMeters,
                    )
                },
            )
        }
        return RoutineDraft(
            id = routine.id,
            folderId = routine.folderId,
            createdAt = routine.createdAt,
            title = routine.name,
            notes = routine.notes,
            orderIndex = routine.orderIndex,
            exercises = exerciseDrafts,
        )
    }

    private fun updateDraft(transform: (RoutineDraft) -> RoutineDraft) = draft.update(transform)

    fun onTitleChange(text: String) = updateDraft { it.copy(title = text) }

    fun addExercises(exercises: List<Exercise>) = updateDraft { d ->
        d.copy(
            exercises = d.exercises + exercises.map { e ->
                RoutineExerciseDraft(
                    id = UUID.randomUUID().toString(),
                    exerciseId = e.id,
                    exerciseName = e.name,
                    exerciseType = e.exerciseType,
                    sets = listOf(RoutineSetDraft(id = UUID.randomUUID().toString())),
                )
            },
        )
    }

    fun removeExercise(exerciseId: String) = updateDraft { d ->
        d.copy(exercises = cleanupOrphanSupersets(d.exercises.filterNot { it.id == exerciseId }))
    }

    fun reorderExercises(orderedIds: List<String>) = updateDraft { d ->
        val byId = d.exercises.associateBy { it.id }
        d.copy(exercises = orderedIds.mapNotNull { byId[it] })
    }

    fun toggleReorderMode() = reorderModeActive.update { !it }

    fun replaceExercise(exerciseDraftId: String, newExercise: Exercise) = updateDraft { d ->
        d.copy(
            exercises = d.exercises.map { ex ->
                if (ex.id != exerciseDraftId) return@map ex
                val stillHasReps = TargetField.REPS in newExercise.exerciseType.targetFields()
                ex.copy(
                    exerciseId = newExercise.id,
                    exerciseName = newExercise.name,
                    exerciseType = newExercise.exerciseType,
                    isRepRangeMode = ex.isRepRangeMode && stillHasReps,
                    sets = ex.sets.map { it.carryOverTo(ex.exerciseType, newExercise.exerciseType) },
                )
            },
        )
    }

    fun updateExerciseNotes(exerciseId: String, text: String) = updateDraft { d ->
        d.copy(exercises = d.exercises.map { if (it.id == exerciseId) it.copy(notes = text.ifBlank { null }) else it })
    }

    /** null = app default, 0 = off (§5.1.2 spine mapping). */
    fun updateRestTimer(exerciseId: String, seconds: Int?) = updateDraft { d ->
        d.copy(exercises = d.exercises.map { if (it.id == exerciseId) it.copy(restTimerSeconds = seconds) else it })
    }

    fun addSet(exerciseId: String) = updateDraft { d ->
        d.copy(
            exercises = d.exercises.map { ex ->
                if (ex.id != exerciseId) return@map ex
                val last = ex.sets.lastOrNull()
                val newSet = RoutineSetDraft(
                    id = UUID.randomUUID().toString(),
                    setType = SetType.NORMAL,
                    targetWeightKg = last?.targetWeightKg,
                    targetReps = last?.targetReps,
                    targetRepRangeMin = last?.targetRepRangeMin,
                    targetRepRangeMax = last?.targetRepRangeMax,
                    targetDurationSeconds = last?.targetDurationSeconds,
                    targetDistanceMeters = last?.targetDistanceMeters,
                )
                ex.copy(sets = ex.sets + newSet)
            },
        )
    }

    fun removeSet(exerciseId: String, setId: String) = updateDraft { d ->
        d.copy(exercises = d.exercises.map { if (it.id != exerciseId) it else it.copy(sets = it.sets.filterNot { s -> s.id == setId }) })
    }

    fun updateSetType(exerciseId: String, setId: String, type: SetType) = updateSet(exerciseId, setId) { it.copy(setType = type) }
    fun updateWeight(exerciseId: String, setId: String, kg: Double?) = updateSet(exerciseId, setId) { it.copy(targetWeightKg = kg) }
    fun updateReps(exerciseId: String, setId: String, reps: Int?) = updateSet(exerciseId, setId) { it.copy(targetReps = reps) }
    fun updateRepRangeMin(exerciseId: String, setId: String, min: Int?) = updateSet(exerciseId, setId) { it.copy(targetRepRangeMin = min) }
    fun updateRepRangeMax(exerciseId: String, setId: String, max: Int?) = updateSet(exerciseId, setId) { it.copy(targetRepRangeMax = max) }
    fun updateDuration(exerciseId: String, setId: String, seconds: Int?) = updateSet(exerciseId, setId) { it.copy(targetDurationSeconds = seconds) }
    fun updateDistance(exerciseId: String, setId: String, meters: Double?) = updateSet(exerciseId, setId) { it.copy(targetDistanceMeters = meters) }

    private fun updateSet(exerciseId: String, setId: String, transform: (RoutineSetDraft) -> RoutineSetDraft) = updateDraft { d ->
        d.copy(
            exercises = d.exercises.map { ex ->
                if (ex.id != exerciseId) ex else ex.copy(sets = ex.sets.map { if (it.id == setId) transform(it) else it })
            },
        )
    }

    /** REPS-header tap (§5.1.2): seeds range-min from the exact value and back again, per set. */
    fun toggleRepRangeMode(exerciseId: String) = updateDraft { d ->
        d.copy(
            exercises = d.exercises.map { ex ->
                if (ex.id != exerciseId) return@map ex
                val toRange = !ex.isRepRangeMode
                ex.copy(
                    isRepRangeMode = toRange,
                    sets = ex.sets.map { s ->
                        if (toRange) {
                            s.copy(targetRepRangeMin = s.targetReps ?: s.targetRepRangeMin, targetReps = null)
                        } else {
                            s.copy(targetReps = s.targetRepRangeMin ?: s.targetReps, targetRepRangeMin = null, targetRepRangeMax = null)
                        }
                    },
                )
            },
        )
    }

    fun startSupersetSelection(sourceExerciseId: String) = supersetSource.update { sourceExerciseId }
    fun cancelSupersetSelection() = supersetSource.update { null }

    fun confirmSupersetTarget(targetExerciseId: String) {
        val sourceId = supersetSource.value ?: return
        updateDraft { d ->
            val existingGroup = d.exercises.find { it.id == targetExerciseId }?.supersetGroup
            val group = existingGroup ?: ((d.exercises.mapNotNull { it.supersetGroup }.maxOrNull() ?: -1) + 1)
            d.copy(
                exercises = d.exercises.map { ex ->
                    if (ex.id == sourceId || ex.id == targetExerciseId) ex.copy(supersetGroup = group) else ex
                },
            )
        }
        supersetSource.value = null
    }

    fun removeFromSuperset(exerciseId: String) = updateDraft { d ->
        d.copy(exercises = cleanupOrphanSupersets(d.exercises.map { if (it.id == exerciseId) it.copy(supersetGroup = null) else it }))
    }

    private fun cleanupOrphanSupersets(exercises: List<RoutineExerciseDraft>): List<RoutineExerciseDraft> {
        val counts = exercises.mapNotNull { it.supersetGroup }.groupingBy { it }.eachCount()
        return exercises.map { if (it.supersetGroup != null && counts[it.supersetGroup] == 1) it.copy(supersetGroup = null) else it }
    }

    /** Returns the saved routine's id, or null if validation failed (title blank / zero exercises — §5.1.2 edge case). */
    suspend fun save(): String? {
        val current = draft.value
        if (current.title.isBlank() || current.exercises.isEmpty()) return null

        val now = clock.now().toEpochMilliseconds()
        val routineEntity = RoutineEntity(
            id = current.id,
            folderId = current.folderId,
            name = current.title.trim(),
            notes = current.notes,
            // createRoutineAtTop recomputes this for create mode; edit mode's updateRoutineStructure uses it as-is.
            orderIndex = current.orderIndex,
            createdAt = current.createdAt,
            updatedAt = now,
        )
        val exerciseEntities = current.exercises.mapIndexed { index, ex ->
            RoutineExerciseEntity(
                id = ex.id,
                routineId = current.id,
                exerciseId = ex.exerciseId,
                orderIndex = index,
                supersetGroup = ex.supersetGroup,
                restTimerSeconds = ex.restTimerSeconds,
                notes = ex.notes,
            )
        }
        val setEntities = current.exercises.flatMap { ex ->
            ex.sets.mapIndexed { index, s ->
                RoutineSetEntity(
                    id = s.id,
                    routineExerciseId = ex.id,
                    orderIndex = index,
                    setType = s.setType,
                    targetWeightKg = s.targetWeightKg,
                    targetReps = s.targetReps,
                    targetRepRangeMin = s.targetRepRangeMin,
                    targetRepRangeMax = s.targetRepRangeMax,
                    targetDurationSeconds = s.targetDurationSeconds,
                    targetDistanceMeters = s.targetDistanceMeters,
                )
            }
        }

        if (isEditMode) {
            routineRepository.updateRoutineStructure(routineEntity, exerciseEntities, setEntities)
        } else {
            routineRepository.createRoutineAtTop(routineEntity, exerciseEntities, setEntities)
        }
        loadedSnapshot = current
        return current.id
    }

    companion object {
        const val ROUTINE_ID_ARG = "routineId"
        const val FOLDER_ID_ARG = "folderId"
    }
}

data class RoutineBuilderUiState(
    val isLoading: Boolean = true,
    val isEditMode: Boolean = false,
    val title: String = "",
    val exercises: List<RoutineExerciseDraft> = emptyList(),
    val defaultRestTimerSeconds: Int = 90,
    val isDirty: Boolean = false,
    val canSave: Boolean = false,
    val supersetSelectionActive: Boolean = false,
    val supersetSourceExerciseId: String? = null,
    val reorderModeActive: Boolean = false,
)
