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
import com.enil.logez.feature.workout.session.SetCompletionUseCase
import com.enil.logez.feature.workout.session.WorkoutNotificationContent
import com.enil.logez.feature.workout.session.WorkoutSessionController
import com.enil.logez.feature.workout.session.WorkoutSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * PHASE2_PLAN.md §5.1.3/§5.1.4/§9.2-§9.7 Live Workout Logger — M4a (core logging) + M4b (timers &
 * foreground service) scope. Deliberately not built here (M4c): the Save Workout screen /
 * Update-Routine prompt / summary; live PR detection + banner (the `pr_alerts` channel and PR
 * sound hook are scaffolded — §9.3/§9.7 — but nothing decides when to fire them yet); RPE column
 * + picker, Plate Calculator, Warm-up Calculator, Update Bodyweight (all deferred — none change
 * stored data shape).
 */
@HiltViewModel
class WorkoutLoggerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionController: WorkoutSessionController,
    private val setCompletionUseCase: SetCompletionUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val workoutId: String = checkNotNull(savedStateHandle[WORKOUT_ID_ARG])

    private val exercises = MutableStateFlow<List<WorkoutExerciseUiModel>>(emptyList())
    private val isLoading = MutableStateFlow(true)
    private val workout = MutableStateFlow<WorkoutEntity?>(null)
    private val supersetSource = MutableStateFlow<String?>(null)
    private val reorderModeActive = MutableStateFlow(false)
    private val keepAwakeEnabled = MutableStateFlow(true)
    private val inlineTimerEnabled = MutableStateFlow(true)

    private val _scrollToExercise = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** §5.1.3 step 7 "Smart Superset Scrolling" — the Screen collects this to `animateScrollToItem`. */
    val scrollToExercise: Flow<String> = _scrollToExercise.asSharedFlow()

    /**
     * Spine rule (PHASE2_PLAN.md §5.1: "Fast-tick values... are exposed as separate Flows from
     * the ViewModel/service and collected only inside the leaf composable that renders the digits
     * — never folded into the screen-level UiState"). `elapsedSecondsFlow`/`restRemainingMillisFlow`/
     * `inlineTimerSecondsFlow` are per-second self-ticking `flow { while(true) {...; delay(1000)} }`
     * builders (see [WorkoutSessionController]) — folding them into `uiState` would make every
     * collector of `uiState` (an Eagerly-shared StateFlow, collected the instant this ViewModel is
     * constructed) implicitly start that ticking loop for the ViewModel's entire lifetime,
     * including in plain unit tests with no Compose collector ever watching the digits. That's
     * exactly the class of bug M4a already hit once (an unbounded ticker hanging the test suite) —
     * here it resurfaces as a *unit-test hang* even though nothing about these fields is tested,
     * simply because constructing the ViewModel is enough to start them. Exposing them as
     * unaffiliated public `Flow` properties instead means they only ever run if something (a real
     * Composable) actually collects them.
     */
    val elapsedSecondsFlow: Flow<Long> = sessionController.elapsedSecondsFlow
    val restRemainingMillisFlow: Flow<Long?> = sessionController.restRemainingMillisFlow
    val inlineTimerSecondsFlow: Flow<Int?> = sessionController.inlineTimerSecondsFlow

    val uiState: StateFlow<WorkoutLoggerUiState> = combine(
        exercises, isLoading, workout, supersetSource, reorderModeActive, keepAwakeEnabled, inlineTimerEnabled, sessionController.state,
    ) { flows ->
        @Suppress("UNCHECKED_CAST")
        val ex = flows[0] as List<WorkoutExerciseUiModel>
        val loading = flows[1] as Boolean
        val w = flows[2] as WorkoutEntity?
        val supersetSourceId = flows[3] as String?
        val reordering = flows[4] as Boolean
        val keepAwake = flows[5] as Boolean
        val inlineTimer = flows[6] as Boolean
        val session = flows[7] as WorkoutSessionState
        val allSets = ex.flatMap { it.sets }
        WorkoutLoggerUiState(
            isLoading = loading,
            title = w?.title.orEmpty(),
            notes = w?.notes.orEmpty(),
            completedSetCount = allSets.count { it.isCompleted },
            totalVolumeKg = allSets.filter { it.isCompleted }.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) },
            exercises = ex,
            supersetSelectionActive = supersetSourceId != null,
            supersetSourceExerciseId = supersetSourceId,
            reorderModeActive = reordering,
            isPaused = session.isPaused,
            restExerciseId = session.restExerciseId,
            keepAwakeEnabled = keepAwake,
            inlineTimerEnabled = inlineTimer,
            inlineTimerExerciseId = session.inlineTimer?.exerciseId,
            inlineTimerSetId = session.inlineTimer?.setId,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WorkoutLoggerUiState())

    init {
        viewModelScope.launch {
            sessionController.rehydrate()
            val w = workoutRepository.getById(workoutId)
            workout.value = w
            val currentSettings = settingsRepository.settings.first()
            keepAwakeEnabled.value = currentSettings.keepAwake
            inlineTimerEnabled.value = currentSettings.inlineTimerEnabled
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
        }

        // Keeps the ongoing notification's content current while this screen is open (§9.3) —
        // see the class doc: while mini-barred with no ViewModel alive, the last-pushed content
        // simply holds until the Logger (and this collector) is alive again.
        viewModelScope.launch {
            combine(exercises, sessionController.state.map { it.restExerciseId }.distinctUntilChanged()) { ex, restId -> ex to restId }
                .collect { (ex, restId) -> pushNotificationContent(ex, restId) }
        }

        // Mirrors a notification-driven "Complete set" action (§9.3 — the Service persists to
        // Room directly since it must work with no ViewModel alive) into this screen's own
        // write-through in-memory state, so an already-open Logger doesn't go stale.
        viewModelScope.launch {
            sessionController.setCompletedExternally.collect { (weId, setId) ->
                updateExercises { list ->
                    list.map { ex -> if (ex.id != weId) ex else ex.copy(sets = ex.sets.map { if (it.id == setId) it.copy(isCompleted = true, failureError = false) else it }) }
                }
            }
        }
    }

    private fun pushNotificationContent(ex: List<WorkoutExerciseUiModel>, restingExerciseId: String?) {
        if (ex.isEmpty()) {
            sessionController.updateNotificationContent(null)
            return
        }
        val current = ex.find { it.id == restingExerciseId } ?: ex.find { e -> e.sets.any { !it.isCompleted } } ?: ex.last()
        val nextSet = current.sets.firstOrNull { !it.isCompleted }
        val completedInExercise = current.sets.count { it.isCompleted }
        val title = "${current.exerciseName} · set ${(completedInExercise + 1).coerceAtMost(current.sets.size.coerceAtLeast(1))} of ${current.sets.size}"
        val isResting = restingExerciseId != null
        val text = when {
            isResting -> "Resting…"
            nextSet != null -> "Next: ${formatSetTarget(nextSet)}"
            else -> "All sets complete"
        }
        sessionController.updateNotificationContent(
            WorkoutNotificationContent(
                title = title,
                text = text,
                isResting = isResting,
                actionableExerciseId = if (!isResting) current.id else null,
                actionableSetId = if (!isResting) nextSet?.id else null,
            ),
        )
    }

    private fun formatSetTarget(set: WorkoutSetUiModel): String {
        val parts = mutableListOf<String>()
        set.weightKg?.let { parts.add("${formatTargetNumber(it)}kg") }
        set.reps?.let { parts.add("× $it") }
        set.durationSeconds?.let { parts.add(formatMmSs(it)) }
        set.distanceMeters?.let { parts.add("${formatTargetNumber(it)}m") }
        return if (parts.isEmpty()) "—" else parts.joinToString(" ")
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

    /**
     * §5.1.3 check gesture. Completing runs through [SetCompletionUseCase] (shared with the
     * notification's "Complete set" action) so the dropset-exception/rest-timer-resolution rule
     * lives in exactly one place; un-completing is local-only (no rest timer to reconsider — the
     * spec doesn't require cancelling a running timer just because an earlier set was reopened).
     * Returns false if rejected (FAILURE set, 0 reps).
     */
    fun toggleCheck(exerciseId: String, setId: String): Boolean {
        val set = findSet(exerciseId, setId) ?: return false
        if (!set.isCompleted && set.setType == SetType.FAILURE && (set.reps == null || set.reps == 0)) {
            updateExercises { list ->
                list.map { ex -> if (ex.id != exerciseId) ex else ex.copy(sets = ex.sets.map { if (it.id == setId) it.copy(failureError = true) else it }) }
            }
            return false
        }
        val nowCompleting = !set.isCompleted
        if (nowCompleting) {
            // §5.1.3 Inline Timer: completing a set with its own running stopwatch must stop and
            // commit it first — the play/pause control only renders for uncompleted sets (it
            // disappears the instant isCompleted flips), so afterward there would be no UI path
            // left to stop it and its elapsed value would never reach durationSeconds.
            sessionController.stopInlineTimer(exerciseId, setId)?.let { seconds -> updateDuration(exerciseId, setId, seconds) }
        }
        updateExercises { list ->
            list.map { ex -> if (ex.id != exerciseId) ex else ex.copy(sets = ex.sets.map { if (it.id == setId) it.copy(isCompleted = nowCompleting, failureError = false) else it }) }
        }
        if (nowCompleting) {
            viewModelScope.launch {
                setCompletionUseCase.completeSet(workoutId, exerciseId, setId)
                maybeScrollToNextSupersetMember(exerciseId)
            }
        } else {
            viewModelScope.launch { workoutRepository.updateWorkoutSetCompletion(setId, false, null) }
        }
        return true
    }

    /** §5.1.3 step 7 — Smart Superset Scrolling, gated by the setting. */
    private suspend fun maybeScrollToNextSupersetMember(exerciseId: String) {
        if (!settingsRepository.settings.first().smartSupersetScrolling) return
        val list = exercises.value
        val current = list.find { it.id == exerciseId } ?: return
        val group = current.supersetGroup ?: return
        val members = list.filter { it.supersetGroup == group }
        if (members.size < 2) return
        val index = members.indexOfFirst { it.id == exerciseId }
        if (index < 0) return
        val next = members[(index + 1) % members.size]
        _scrollToExercise.tryEmit(next.id)
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
        // Clears an orphaned inline-timer pointer if this exact set's stopwatch was running —
        // it's about to be deleted, so nothing needs committing, just stopped (no-op otherwise).
        sessionController.stopInlineTimer(exerciseId, setId)
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

    // --- Timers (M4b: §5.1.3/§5.1.4/§9.4) ---

    /** TopAppBar elapsed-duration tap menu: "Pause Workout Timer"/"Resume Workout Timer". */
    fun togglePause() {
        viewModelScope.launch {
            if (uiState.value.isPaused) sessionController.resume() else sessionController.pause()
        }
    }

    fun adjustRestTimer(deltaSeconds: Int) = sessionController.adjustRestTimer(deltaSeconds)
    fun skipRestTimer() = sessionController.skipRestTimer()

    /** §5.1.3 Inline Timer — one at a time; starting a new one implicitly abandons any other running (spec is silent on a conflict UI, and the UI only exposes one play button at a time regardless). */
    fun startInlineTimer(exerciseId: String, setId: String) = sessionController.startInlineTimer(exerciseId, setId)

    fun stopInlineTimer(exerciseId: String, setId: String) {
        val seconds = sessionController.stopInlineTimer(exerciseId, setId) ?: return
        updateDuration(exerciseId, setId, seconds)
    }

    // --- Finish / Discard ---

    /** M4a/M4b minimal finish: mark COMPLETED, stop the session/service. The Save Workout screen / PR rebuild / summary are M4c. */
    suspend fun finish(): Boolean {
        val w = workout.value ?: return false
        val now = clock.now().toEpochMilliseconds()
        val duration = ((now - w.startedAt) / 1000).toInt()
        // endSession() first (synchronous) so a rest timer that's already mid-fire in the Service
        // can't play sound/haptics/a heads-up notification for a workout that's finishing/gone —
        // it cancels the Service's pending deadline-wait before the (suspending) Room write starts.
        sessionController.endSession()
        workoutRepository.updateWorkout(w.copy(status = WorkoutStatus.COMPLETED, endedAt = now, durationSeconds = duration, updatedAt = now))
        return true
    }

    suspend fun discard() {
        sessionController.endSession()
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
    val completedSetCount: Int = 0,
    val totalVolumeKg: Double = 0.0,
    val exercises: List<WorkoutExerciseUiModel> = emptyList(),
    val supersetSelectionActive: Boolean = false,
    val supersetSourceExerciseId: String? = null,
    val reorderModeActive: Boolean = false,
    val isPaused: Boolean = false,
    /** Which exercise's card should show the rest-timer bar — the numeric countdown itself is a separate leaf-collected Flow (see [WorkoutLoggerViewModel.restRemainingMillisFlow]). */
    val restExerciseId: String? = null,
    val keepAwakeEnabled: Boolean = true,
    val inlineTimerEnabled: Boolean = true,
    val inlineTimerExerciseId: String? = null,
    val inlineTimerSetId: String? = null,
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

private fun formatTargetNumber(value: Double): String = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

private fun formatMmSs(totalSeconds: Int): String = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
