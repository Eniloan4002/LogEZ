package com.enil.logez

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.feature.activity.ActivityTrackingController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * What the app should do about an IN_PROGRESS workout found at cold start. A GPS run and a typed
 * session need opposite handling, and getting it wrong starts a second foreground service
 * alongside a still-running location one — so the decision is modelled explicitly rather than
 * carried as a bare workout id.
 */
sealed interface StartupRecovery {
    data object None : StartupRecovery

    /** §9.5 as originally written: genuinely resumable from the ActiveSession store. */
    data class ResumeStrength(val workoutId: String) : StartupRecovery

    /**
     * The Activity died but the process did not — which the location foreground service makes
     * likely, since it keeps the process alive across a swipe from Recents. Tracking is still
     * running; just re-enter the screen, and start nothing.
     */
    data object ResumeLiveTracking : StartupRecovery

    /**
     * The process died mid-run. Route, distance and start time lived only in
     * [ActivityTrackingController]'s memory and are gone; Room has the workout row and one blank
     * set. Unrecoverable, so the user has to choose what happens to it.
     */
    data class InterruptedRun(val workoutId: String, val startedAt: Long) : StartupRecovery
}

/**
 * PHASE2_PLAN.md §9.5 cold-start recovery: "App swiped from recents / force-stopped / crashed...
 * On next cold start, MainActivity checks for an IN_PROGRESS workout."
 *
 * Activity-scoped (created once per process via [LogEzApp]'s `hiltViewModel()`), so this check
 * runs exactly once per cold start, not on every recomposition.
 */
@HiltViewModel
class AppStartupViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val activityTrackingController: ActivityTrackingController,
) : ViewModel() {
    private val _recovery = MutableStateFlow<StartupRecovery>(StartupRecovery.None)
    val recovery: StateFlow<StartupRecovery> = _recovery

    init {
        viewModelScope.launch {
            val workout = workoutRepository.getInProgress()
            _recovery.value = when {
                workout == null -> StartupRecovery.None
                workout.kind == WorkoutKind.STRENGTH -> StartupRecovery.ResumeStrength(workout.id)
                // The id, not just isTracking: a controller left pointing at a different,
                // already-finished workout must not be read as this row's live session.
                activityTrackingController.state.value.workoutId == workout.id -> StartupRecovery.ResumeLiveTracking
                else -> StartupRecovery.InterruptedRun(workout.id, workout.startedAt)
            }
        }
    }

    /** Called once the recovery has been acted on so it doesn't repeat on recomposition. */
    fun consumeRecovery() {
        _recovery.value = StartupRecovery.None
    }
}
