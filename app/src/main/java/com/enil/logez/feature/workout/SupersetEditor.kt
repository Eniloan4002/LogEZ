package com.enil.logez.feature.workout

import com.enil.logez.core.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Superset grouping operations (start/confirm/cancel selection, remove-from-group, and orphan
 * cleanup) for the live workout logger — pulled out of [WorkoutLoggerViewModel] (2026-09-19 debt
 * audit). Operates directly on the ViewModel's own shared [exercises]/[supersetSource] state and
 * routes every write through the same [persist] the ViewModel itself uses for every other edit on
 * this screen. [cleanupOrphans] is also called from the ViewModel's own `removeExercise` (removing
 * an exercise can orphan a superset it was half of), so it's exposed rather than kept private.
 */
class SupersetEditor(
    private val exercises: MutableStateFlow<List<WorkoutExerciseUiModel>>,
    private val supersetSource: MutableStateFlow<String?>,
    private val workoutRepository: WorkoutRepository,
    private val persist: (suspend () -> Unit) -> Unit,
) {
    fun startSelection(sourceExerciseId: String) {
        supersetSource.update { sourceExerciseId }
    }

    fun cancelSelection() {
        supersetSource.update { null }
    }

    fun confirmTarget(targetExerciseId: String) {
        val sourceId = supersetSource.value ?: return
        val current = exercises.value
        val existingGroup = current.find { it.id == targetExerciseId }?.supersetGroup
        val group = existingGroup ?: ((current.mapNotNull { it.supersetGroup }.maxOrNull() ?: -1) + 1)
        exercises.update { list -> list.map { if (it.id == sourceId || it.id == targetExerciseId) it.copy(supersetGroup = group) else it } }
        persist {
            workoutRepository.updateWorkoutExerciseSuperset(sourceId, group)
            workoutRepository.updateWorkoutExerciseSuperset(targetExerciseId, group)
        }
        supersetSource.value = null
    }

    fun removeFromSuperset(exerciseId: String) {
        exercises.update { list -> cleanupOrphans(list.map { if (it.id == exerciseId) it.copy(supersetGroup = null) else it }) }
        persist { workoutRepository.updateWorkoutExerciseSuperset(exerciseId, null) }
    }

    fun cleanupOrphans(list: List<WorkoutExerciseUiModel>): List<WorkoutExerciseUiModel> {
        val counts = list.mapNotNull { it.supersetGroup }.groupingBy { it }.eachCount()
        val cleaned = list.map { if (it.supersetGroup != null && counts[it.supersetGroup] == 1) it.copy(supersetGroup = null) else it }
        val orphaned = list.filter { it.supersetGroup != null && counts[it.supersetGroup] == 1 }
        if (orphaned.isNotEmpty()) {
            persist { orphaned.forEach { workoutRepository.updateWorkoutExerciseSuperset(it.id, null) } }
        }
        return cleaned
    }
}
