package com.enil.logez.feature.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.common.Clock
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.calc.PreviousValueFormatter
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.feature.routines.TargetField
import com.enil.logez.feature.routines.targetFields
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * PHASE2_PLAN.md §5.1.3 Live Workout Logger — M4a scope only (core logging). Deliberately not
 * built here (later sub-milestones / explicitly out of scope for a first pass): the foreground
 * service, rest timer, elapsed-time-owned-by-service, sounds, live PR banner, smart superset
 * auto-scroll, mini-bar (all M4b); the Save Workout screen / Update-Routine prompt / summary
 * (M4c); RPE column + picker, Plate Calculator, Warm-up Calculator, Update Bodyweight, and the
 * previous-session-note greyed preview (all deferred — none change stored data shape, so none
 * block a later pass).
 */
@HiltViewModel
class WorkoutLoggerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val workoutId: String = checkNotNull(savedStateHandle[WORKOUT_ID_ARG])

    private val exercises = MutableStateFlow<List<WorkoutExerciseUiModel>>(emptyList())
    private val isLoading = MutableStateFlow(true)
    private val workout = MutableStateFlow<WorkoutEntity?>(null)
    private val elapsedSeconds = MutableStateFlow(0L)
    private val supersetSource = MutableStateFlow<String?>(null)
    private val reorderModeActive = MutableStateFlow(false)

    val uiState: StateFlow<WorkoutLoggerUiState> = combine(
        exercises,
        isLoading,
        workout,
        elapsedSeconds,
        supersetSource,
        reorderModeActive,
    ) { flows ->
        @Suppress("UNCHECKED_CAST")
        val ex = flows[0] as List<WorkoutExerciseUiModel>
        val loading = flows[1] as Boolean
        val w = flows[2] as WorkoutEntity?
        val elapsed = flows[3] as Long
        val supersetSourceId = flows[4] as String?
        val reordering = flows[5] as Boolean
        val allSets = ex.flatMap { it.sets }
        WorkoutLoggerUiState(
            isLoading = loading,
            title = w?.title.orEmpty(),
            notes = w?.notes.orEmpty(),
            elapsedSeconds = elapsed,
            completedSetCount = allSets.count { it.isCompleted },
            totalVolumeKg = allSets.filter { it.isCompleted }.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) },
            exercises = ex,
            supersetSelectionActive = supersetSourceId != null,
            supersetSourceExerciseId = supersetSourceId,
            reorderModeActive = reordering,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WorkoutLoggerUiState())

    init {
        viewModelScope.launch {
            val w = workoutRepository.getById(workoutId)
            workout.value = w
            val currentSettings = settingsRepository.settings.first()
            val workoutExercises = workoutRepository.getExercisesForWorkout(workoutId)
            exercises.value = workoutExercises.map { we ->
                val exercise = exerciseRepository.getById(we.exerciseId)
                val previousRows = workoutRepository.getPreviousWorkoutSets(we.exerciseId, currentSettings.previousValuesMode, w?.routineId)
                    .sortedBy { it.orderIndex }
                val sets = workoutRepository.getSetsForWorkoutExercise(we.id).sortedBy { it.orderIndex }
                WorkoutExerciseUiModel(
                    id = we.id,
                    exerciseId = we.exerciseId,
                    exerciseName = exercise?.name.orEmpty(),
                    exerciseType = exercise?.exerciseType ?: ExerciseType.WEIGHT_REPS,
                    supersetGroup = we.supersetGroup,
                    restTimerSeconds = we.restTimerSeconds,
                    notes = we.notes.orEmpty(),
                    sets = sets.mapIndexed { index, s ->
                        val previous = previousRows.getOrNull(index)
                        s.toUiModel(
                            previousLabel = previous?.let {
                                PreviousValueFormatter.format(it, exercise?.exerciseType ?: ExerciseType.WEIGHT_REPS, currentSettings.weightUnit, currentSettings.distanceUnit)
                            } ?: "—",
                        )
                    },
                )
            }
            isLoading.value = false
            // A live-ticking clock is explicitly the foreground service's job (§5.1.3/§5.1.4,
            // M4b) — this is a one-time snapshot, not a self-driven timer, so M4a doesn't run an
            // unbounded loop in viewModelScope that M4b would need to rip out anyway.
            elapsedSeconds.value = ((clock.now().toEpochMilliseconds() - (w?.startedAt ?: clock.now().toEpochMilliseconds())) / 1000).coerceAtLeast(0)
        }
    }

    private fun updateExercises(transform: (List<WorkoutExerciseUiModel>) -> List<WorkoutExerciseUiModel>) {
        exercises.update { transform(it) }
    }

    private fun findSet(exerciseId: String, setId: String): WorkoutSetUiModel? =
        exercises.value.find { it.id == exerciseId }?.sets?.find { it.id == setId }

    // --- Set-field edits (write-through) ---

    fun updateWeight(exerciseId: String, setId: String, kg: Double?) =
        updateSetField(exerciseId, setId, { it.copy(weightKg = kg) }) { workoutRepository.updateWorkoutSetWeight(setId, kg) }
    fun updateReps(exerciseId: String, setId: String, reps: Int?) =
        updateSetField(exerciseId, setId, { it.copy(reps = reps) }) { workoutRepository.updateWorkoutSetReps(setId, reps) }
    fun updateDuration(exerciseId: String, setId: String, seconds: Int?) =
        updateSetField(exerciseId, setId, { it.copy(durationSeconds = seconds) }) { workoutRepository.updateWorkoutSetDuration(setId, seconds) }
    fun updateDistance(exerciseId: String, setId: String, meters: Double?) =
        updateSetField(exerciseId, setId, { it.copy(distanceMeters = meters) }) { workoutRepository.updateWorkoutSetDistance(setId, meters) }
    fun updateCustomMetric(exerciseId: String, setId: String, value: Double?) =
        updateSetField(exerciseId, setId, { it.copy(customMetric = value) }) { workoutRepository.updateWorkoutSetCustomMetric(setId, value) }

    /** [persist] is a targeted single-column DAO write — never a whole-row reconstruction, which would need
     * fields (orderIndex, completedAt, ...) this UI model doesn't track and would silently clobber them. */
    private fun updateSetField(exerciseId: String, setId: String, uiTransform: (WorkoutSetUiModel) -> WorkoutSetUiModel, persist: suspend () -> Unit) {
        updateExercises { list ->
            list.map { ex ->
                if (ex.id != exerciseId) ex else ex.copy(sets = ex.sets.map { if (it.id == setId) uiTransform(it) else it })
            }
        }
        viewModelScope.launch { persist() }
    }

    fun updateSetType(exerciseId: String, setId: String, type: SetType) {
        updateExercises { list ->
            list.map { ex -> if (ex.id != exerciseId) ex else ex.copy(sets = ex.sets.map { if (it.id == setId) it.copy(setType = type, failureError = false) else it }) }
        }
        viewModelScope.launch { workoutRepository.updateWorkoutSetType(setId, type) }
    }

    /** §5.1.3 check gesture — commits, validates FAILURE sets, marks completed. Returns false if rejected. */
    fun toggleCheck(exerciseId: String, setId: String): Boolean {
        val set = findSet(exerciseId, setId) ?: return false
        if (!set.isCompleted && set.setType == SetType.FAILURE && (set.reps == null || set.reps == 0)) {
            updateExercises { list ->
                list.map { ex -> if (ex.id != exerciseId) ex else ex.copy(sets = ex.sets.map { if (it.id == setId) it.copy(failureError = true) else it }) }
            }
            return false
        }
        val nowCompleted = !set.isCompleted
        val completedAt = if (nowCompleted) clock.now().toEpochMilliseconds() else null
        updateExercises { list ->
            list.map { ex -> if (ex.id != exerciseId) ex else ex.copy(sets = ex.sets.map { if (it.id == setId) it.copy(isCompleted = nowCompleted, failureError = false) else it }) }
        }
        viewModelScope.launch { workoutRepository.updateWorkoutSetCompletion(setId, nowCompleted, completedAt) }
        return true
    }

    fun addSet(exerciseId: String) {
        val exercise = exercises.value.find { it.id == exerciseId } ?: return
        val last = exercise.sets.lastOrNull()
        val newSet = WorkoutSetUiModel(
            id = UUID.randomUUID().toString(),
            setType = SetType.NORMAL,
            weightKg = last?.weightKg,
            reps = last?.reps,
            durationSeconds = last?.durationSeconds,
            distanceMeters = last?.distanceMeters,
            customMetric = last?.customMetric,
        )
        updateExercises { list -> list.map { if (it.id != exerciseId) it else it.copy(sets = it.sets + newSet) } }
        viewModelScope.launch {
            workoutRepository.insertWorkoutSet(newSet.toEntity(exerciseId).copy(orderIndex = exercise.sets.size))
        }
    }

    fun removeSet(exerciseId: String, setId: String) {
        updateExercises { list -> list.map { if (it.id != exerciseId) it else it.copy(sets = it.sets.filterNot { s -> s.id == setId }) } }
        viewModelScope.launch { workoutRepository.deleteWorkoutSet(setId) }
    }

    // --- Notes ---

    fun updateWorkoutNotes(text: String) {
        val w = workout.value ?: return
        workout.value = w.copy(notes = text)
        viewModelScope.launch { workoutRepository.updateWorkout(w.copy(notes = text, updatedAt = clock.now().toEpochMilliseconds())) }
    }

    fun updateExerciseNotes(exerciseId: String, text: String) {
        updateExercises { list -> list.map { if (it.id == exerciseId) it.copy(notes = text) else it } }
        viewModelScope.launch { workoutRepository.updateWorkoutExerciseNotes(exerciseId, text.ifBlank { null }) }
    }

    // --- Exercise ops ---

    /** Adds exercises with auto-fill from the last COMPLETED session, or one blank set if never logged (§5.1.3). */
    fun addExercises(picked: List<Exercise>) {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val startIndex = exercises.value.size
            val newModels = mutableListOf<WorkoutExerciseUiModel>()
            val newExerciseEntities = mutableListOf<WorkoutExerciseEntity>()
            val newSetEntities = mutableListOf<WorkoutSetEntity>()

            picked.forEachIndexed { offset, exercise ->
                val weId = UUID.randomUUID().toString()
                val previous = workoutRepository.getPreviousWorkoutSets(exercise.id, settings.previousValuesMode, workout.value?.routineId)
                    .sortedBy { it.orderIndex }
                val sets = if (previous.isNotEmpty()) {
                    previous.mapIndexed { i, p ->
                        WorkoutSetUiModel(
                            id = UUID.randomUUID().toString(), setType = SetType.NORMAL,
                            weightKg = p.weightKg, reps = p.reps, durationSeconds = p.durationSeconds,
                            distanceMeters = p.distanceMeters, customMetric = p.customMetric,
                            previousLabel = PreviousValueFormatter.format(p, exercise.exerciseType, settings.weightUnit, settings.distanceUnit),
                        )
                    }
                } else {
                    listOf(WorkoutSetUiModel(id = UUID.randomUUID().toString()))
                }
                newModels += WorkoutExerciseUiModel(
                    id = weId, exerciseId = exercise.id, exerciseName = exercise.name, exerciseType = exercise.exerciseType, sets = sets,
                )
                newExerciseEntities += WorkoutExerciseEntity(
                    id = weId, workoutId = workoutId, exerciseId = exercise.id, orderIndex = startIndex + offset,
                    supersetGroup = null, restTimerSeconds = null, notes = null,
                )
                sets.forEachIndexed { i, s -> newSetEntities += s.toEntity(weId).copy(orderIndex = i) }
            }

            updateExercises { it + newModels }
            workoutRepository.insertWorkoutExercises(newExerciseEntities)
            workoutRepository.insertWorkoutSets(newSetEntities)
        }
    }

    fun removeExercise(exerciseId: String) {
        updateExercises { list -> cleanupOrphanSupersets(list.filterNot { it.id == exerciseId }) }
        viewModelScope.launch { workoutRepository.deleteWorkoutExercise(exerciseId) }
    }

    /**
     * §5.1.9 Replace mode: "completed sets are discarded after confirm — re-attribution is NOT
     * offered." The confirm dialog itself is deferred (M4a scope trim, noted in the class doc);
     * the underlying reset (uncomplete every set) is spec-mandated data behavior, not UI polish,
     * so it's unconditional here regardless of whether a dialog warned the user first.
     */
    fun replaceExercise(exerciseId: String, newExercise: Exercise) {
        val oldExercise = exercises.value.find { it.id == exerciseId } ?: return
        val carriedSets = oldExercise.sets.map {
            it.carryOverTo(oldExercise.exerciseType, newExercise.exerciseType).copy(isCompleted = false)
        }
        updateExercises { list ->
            list.map { ex ->
                if (ex.id != exerciseId) ex else ex.copy(exerciseId = newExercise.id, exerciseName = newExercise.name, exerciseType = newExercise.exerciseType, sets = carriedSets)
            }
        }
        viewModelScope.launch {
            val entities = carriedSets.mapIndexed { index, s -> s.toEntity(exerciseId).copy(orderIndex = index, isCompleted = false, completedAt = null) }
            workoutRepository.replaceWorkoutExerciseExercise(exerciseId, newExercise.id, entities)
        }
    }

    fun reorderExercises(orderedIds: List<String>) {
        updateExercises { list ->
            val byId = list.associateBy { it.id }
            orderedIds.mapNotNull { byId[it] }
        }
        viewModelScope.launch { orderedIds.forEachIndexed { index, id -> workoutRepository.updateWorkoutExerciseOrderIndex(id, index) } }
    }

    fun toggleReorderMode() = reorderModeActive.update { !it }

    fun startSupersetSelection(sourceExerciseId: String) = supersetSource.update { sourceExerciseId }
    fun cancelSupersetSelection() = supersetSource.update { null }

    fun confirmSupersetTarget(targetExerciseId: String) {
        val sourceId = supersetSource.value ?: return
        val current = exercises.value
        val existingGroup = current.find { it.id == targetExerciseId }?.supersetGroup
        val group = existingGroup ?: ((current.mapNotNull { it.supersetGroup }.maxOrNull() ?: -1) + 1)
        updateExercises { list -> list.map { if (it.id == sourceId || it.id == targetExerciseId) it.copy(supersetGroup = group) else it } }
        viewModelScope.launch {
            workoutRepository.updateWorkoutExerciseSuperset(sourceId, group)
            workoutRepository.updateWorkoutExerciseSuperset(targetExerciseId, group)
        }
        supersetSource.value = null
    }

    fun removeFromSuperset(exerciseId: String) {
        updateExercises { list -> cleanupOrphanSupersets(list.map { if (it.id == exerciseId) it.copy(supersetGroup = null) else it }) }
        viewModelScope.launch { workoutRepository.updateWorkoutExerciseSuperset(exerciseId, null) }
    }

    private fun cleanupOrphanSupersets(list: List<WorkoutExerciseUiModel>): List<WorkoutExerciseUiModel> {
        val counts = list.mapNotNull { it.supersetGroup }.groupingBy { it }.eachCount()
        val cleaned = list.map { if (it.supersetGroup != null && counts[it.supersetGroup] == 1) it.copy(supersetGroup = null) else it }
        val orphaned = list.filter { it.supersetGroup != null && counts[it.supersetGroup] == 1 }
        if (orphaned.isNotEmpty()) {
            viewModelScope.launch { orphaned.forEach { workoutRepository.updateWorkoutExerciseSuperset(it.id, null) } }
        }
        return cleaned
    }

    fun updateRestTimer(exerciseId: String, seconds: Int?) {
        updateExercises { list -> list.map { if (it.id == exerciseId) it.copy(restTimerSeconds = seconds) else it } }
        viewModelScope.launch { workoutRepository.updateWorkoutExerciseRestTimer(exerciseId, seconds) }
    }

    // --- Finish / Discard ---

    /** M4a minimal finish: mark COMPLETED and stop. The Save Workout screen / PR rebuild / summary are M4c. */
    suspend fun finish(): Boolean {
        val w = workout.value ?: return false
        val now = clock.now().toEpochMilliseconds()
        val duration = ((now - w.startedAt) / 1000).toInt()
        workoutRepository.updateWorkout(w.copy(status = WorkoutStatus.COMPLETED, endedAt = now, durationSeconds = duration, updatedAt = now))
        return true
    }

    suspend fun discard() {
        workoutRepository.deleteById(workoutId)
    }

    companion object {
        const val WORKOUT_ID_ARG = "workoutId"
    }
}

data class WorkoutLoggerUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val notes: String = "",
    val elapsedSeconds: Long = 0,
    val completedSetCount: Int = 0,
    val totalVolumeKg: Double = 0.0,
    val exercises: List<WorkoutExerciseUiModel> = emptyList(),
    val supersetSelectionActive: Boolean = false,
    val supersetSourceExerciseId: String? = null,
    val reorderModeActive: Boolean = false,
)

private fun WorkoutSetEntity.toUiModel(previousLabel: String) = WorkoutSetUiModel(
    id = id, setType = setType, weightKg = weightKg, reps = reps, durationSeconds = durationSeconds,
    distanceMeters = distanceMeters, customMetric = customMetric, rpe = rpe, isCompleted = isCompleted,
    previousLabel = previousLabel,
)

private fun WorkoutSetUiModel.toEntity(workoutExerciseId: String) = WorkoutSetEntity(
    id = id, workoutExerciseId = workoutExerciseId, orderIndex = 0, setType = setType, weightKg = weightKg,
    reps = reps, durationSeconds = durationSeconds, distanceMeters = distanceMeters, rpe = rpe,
    customMetric = customMetric, isCompleted = isCompleted, completedAt = null,
)

/** Reuses the M3 Routine Builder's field-preservation rule (§5.1.2/§5.1.3 share the same Replace Exercise semantics). */
private fun WorkoutSetUiModel.carryOverTo(oldType: ExerciseType, newType: ExerciseType): WorkoutSetUiModel {
    val kept = oldType.targetFields() intersect newType.targetFields()
    return copy(
        weightKg = if (TargetField.WEIGHT in kept) weightKg else null,
        reps = if (TargetField.REPS in kept) reps else null,
        durationSeconds = if (TargetField.DURATION in kept) durationSeconds else null,
        distanceMeters = if (TargetField.DISTANCE in kept) distanceMeters else null,
    )
}
