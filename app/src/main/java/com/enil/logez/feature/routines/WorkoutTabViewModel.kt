package com.enil.logez.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.common.Clock
import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.RoutineFolderEntity
import com.enil.logez.core.domain.calc.DashboardAggregator
import com.enil.logez.core.domain.calc.StreakCalculator
import com.enil.logez.core.domain.repository.RoutineRepository
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.feature.workout.StartResult
import com.enil.logez.feature.workout.WorkoutStarter
import com.enil.logez.feature.workout.session.WorkoutSessionController
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** PHASE2_PLAN.md §5.1.1 Workout tab: folders + routines, unified from three Room `Flow`s. */
@HiltViewModel
class WorkoutTabViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val workoutRepository: WorkoutRepository,
    private val settingsRepository: SettingsRepository,
    private val workoutStarter: WorkoutStarter,
    private val sessionController: WorkoutSessionController,
    private val clock: Clock,
) : ViewModel() {
    val uiState: StateFlow<WorkoutTabUiState> = combine(
        routineRepository.observeFolders(),
        routineRepository.observeAllRoutines(),
        routineRepository.observeRoutineExercisePreviews(),
        workoutRepository.observeInProgress(),
        // M8c heatmap: nested so the outer combine stays within kotlinx.coroutines' 5-flow typed
        // overload. Reuses `observeCompleted()` (already Flow-based) rather than the suspend-only
        // `getCompletedWorkoutTimestamps()`, so the heatmap stays reactive with no separate
        // refresh-on-resume load of its own.
        combine(workoutRepository.observeCompleted(), settingsRepository.settings) { completed, settings ->
            settings.firstDayOfWeek to completed
        },
    ) { folders, routines, previewRows, inProgress, heatmapInput ->
        val previewByRoutine = previewRows.groupBy { it.routineId }
        fun cardFor(routine: RoutineEntity): RoutineCardModel {
            val names = previewByRoutine[routine.id].orEmpty().sortedBy { it.orderIndex }.map { it.exerciseName }
            return RoutineCardModel(routine = routine, exercisePreview = buildExercisePreview(names))
        }
        val routinesByFolder = routines.filter { it.folderId != null }.groupBy { it.folderId }

        // M8c heatmap: "today" re-derives on every recombination (any Room change touching this
        // tab), not on a lifecycle timer — a passive progress widget, not date-critical business
        // logic like StreakCalculator's other consumers (Calendar/Profile), which resolve it on
        // RESUME specifically to survive a real midnight/timezone change mid-visit.
        val (firstDayOfWeek, completed) = heatmapInput
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(clock.now().toEpochMilliseconds()).atZone(zone).toLocalDate()
        val heatmapCounts = StreakCalculator.countsByDate(completed.map { DashboardAggregator.localDate(it.startedAt, zone) })

        WorkoutTabUiState(
            isLoading = false,
            folders = folders.map { f ->
                FolderSection(folder = f, routines = routinesByFolder[f.id].orEmpty().sortedBy { it.orderIndex }.map(::cardFor))
            },
            rootRoutines = routines.filter { it.folderId == null }.sortedBy { it.orderIndex }.map(::cardFor),
            inProgressWorkoutId = inProgress?.id,
            inProgressWorkoutTitle = inProgress?.title,
            heatmapCounts = heatmapCounts,
            heatmapToday = today,
            heatmapFirstDayOfWeek = firstDayOfWeek,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WorkoutTabUiState())

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val now = clock.now().toEpochMilliseconds()
            routineRepository.createFolderAtTop(
                RoutineFolderEntity(id = UUID.randomUUID().toString(), name = name.trim(), orderIndex = 0, createdAt = now, updatedAt = now),
            )
        }
    }

    fun renameFolder(id: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { routineRepository.renameFolder(id, name.trim(), clock.now().toEpochMilliseconds()) }
    }

    fun deleteFolder(folder: RoutineFolderEntity) {
        viewModelScope.launch { routineRepository.deleteFolder(folder) }
    }

    fun reorderFolders(orderedIds: List<String>) {
        viewModelScope.launch { routineRepository.reorderFolders(orderedIds) }
    }

    fun reorderRoutines(orderedIds: List<String>) {
        viewModelScope.launch { routineRepository.reorderRoutines(orderedIds) }
    }

    fun moveRoutineToFolder(routineId: String, folderId: String?) {
        viewModelScope.launch { routineRepository.moveRoutineToFolder(routineId, folderId, clock.now().toEpochMilliseconds()) }
    }

    fun deleteRoutine(id: String) {
        viewModelScope.launch { routineRepository.deleteRoutineById(id) }
    }

    /** §5.1.1 routine three-dots "Duplicate": full structural copy, fresh ids, no history attached. */
    suspend fun duplicateRoutine(routineId: String): String? {
        val original = routineRepository.getRoutineById(routineId) ?: return null
        val originalExercises = routineRepository.getExercisesForRoutine(routineId)
        val now = clock.now().toEpochMilliseconds()
        val newRoutineId = UUID.randomUUID().toString()
        val exerciseIdMap = originalExercises.associate { it.id to UUID.randomUUID().toString() }

        val newExercises = originalExercises.map { re -> re.copy(id = exerciseIdMap.getValue(re.id), routineId = newRoutineId) }
        val newSets = originalExercises.flatMap { re ->
            routineRepository.getSetsForRoutineExercise(re.id).map { s ->
                s.copy(id = UUID.randomUUID().toString(), routineExerciseId = exerciseIdMap.getValue(re.id))
            }
        }
        val newRoutine = original.copy(id = newRoutineId, name = "${original.name} (copy)", createdAt = now, updatedAt = now)

        routineRepository.createRoutineAtTop(newRoutine, newExercises, newSets)
        return newRoutineId
    }

    suspend fun startEmptyWorkout(): StartResult {
        val result = workoutStarter.startEmptyOrConflict()
        if (result is StartResult.Started) sessionController.startSession(result.workoutId)
        return result
    }

    suspend fun startRoutine(routineId: String): StartResult {
        val result = workoutStarter.startFromRoutineOrConflict(routineId)
        if (result is StartResult.Started) sessionController.startSession(result.workoutId)
        return result
    }

    suspend fun discardInProgressAndStartEmpty(): String {
        workoutStarter.discardInProgress()
        sessionController.endSession()
        val id = workoutStarter.startEmpty()
        sessionController.startSession(id)
        return id
    }

    suspend fun discardInProgressAndStartRoutine(routineId: String): String {
        workoutStarter.discardInProgress()
        sessionController.endSession()
        val id = workoutStarter.startFromRoutine(routineId)
        sessionController.startSession(id)
        return id
    }
}

data class WorkoutTabUiState(
    val isLoading: Boolean = true,
    val folders: List<FolderSection> = emptyList(),
    val rootRoutines: List<RoutineCardModel> = emptyList(),
    /** §9.5 process-death recovery surfaced directly on the tab — no need to fail a Start tap first to discover it. */
    val inProgressWorkoutId: String? = null,
    val inProgressWorkoutTitle: String? = null,
    /** M8c progress heatmap. */
    val heatmapCounts: Map<LocalDate, Int> = emptyMap(),
    val heatmapToday: LocalDate = LocalDate.EPOCH,
    val heatmapFirstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
)

data class FolderSection(val folder: RoutineFolderEntity, val routines: List<RoutineCardModel>)
data class RoutineCardModel(val routine: RoutineEntity, val exercisePreview: String)

/** §5.1.1 routine card subtitle: "Bench Press, Incline DB Press, +3 more". Pure — unit-tested directly. */
internal fun buildExercisePreview(names: List<String>): String = when {
    names.isEmpty() -> ""
    names.size <= 2 -> names.joinToString(", ")
    else -> "${names.take(2).joinToString(", ")}, +${names.size - 2} more"
}
