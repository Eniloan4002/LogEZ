package com.enil.logez.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.feature.workout.session.WorkoutSessionController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * PHASE2_PLAN.md §5.1.3 in-app mini-bar — "docked above the bottom tab bar on all tab screens".
 * Lives at the [com.enil.logez.LogEzApp] root (Activity-scoped, outside the Logger's own nav
 * back-stack entry) so it survives navigating away from the Logger, which — per M4a's own
 * `handleBack` — pops that destination off the stack (destroying its ViewModel); Room + the
 * controller-backed session state are the actual source of truth either way (spine write-through
 * rule). Elapsed/rest-countdown are exposed as separate `Flow`s, not folded into [uiState] — see
 * [WorkoutLoggerViewModel]'s equivalent doc: folding a per-second self-ticking flow into an
 * Eagerly-shared StateFlow starts it the instant this ViewModel is constructed (Activity-scoped —
 * i.e. for the app's entire foreground lifetime) and recomposes the whole bar every second instead
 * of just its digits.
 */
@HiltViewModel
class MiniBarViewModel @Inject constructor(
    workoutRepository: WorkoutRepository,
    private val sessionController: WorkoutSessionController,
) : ViewModel() {
    init {
        // §9.5 cold-start recovery safety net: if nothing else has rehydrated the controller yet
        // (the Logger/Service may not have run this app process), the elapsed/rest flows would
        // otherwise silently show nothing for an IN_PROGRESS workout.
        viewModelScope.launch { sessionController.rehydrate() }
    }

    val elapsedSecondsFlow: Flow<Long> = sessionController.elapsedSecondsFlow
    val restRemainingMillisFlow: Flow<Long?> = sessionController.restRemainingMillisFlow

    val uiState: StateFlow<MiniBarUiState> = workoutRepository.observeInProgress()
        .map { workout -> if (workout == null) MiniBarUiState() else MiniBarUiState(visible = true, workoutId = workout.id, title = workout.title) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MiniBarUiState())
}

data class MiniBarUiState(
    val visible: Boolean = false,
    val workoutId: String? = null,
    val title: String = "",
)
