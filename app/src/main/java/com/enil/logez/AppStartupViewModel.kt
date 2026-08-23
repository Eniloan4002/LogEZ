package com.enil.logez

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * PHASE2_PLAN.md §9.5 cold-start recovery: "App swiped from recents / force-stopped / crashed...
 * On next cold start, MainActivity checks for an IN_PROGRESS workout: if found, it restarts
 * WorkoutSessionService... and lands the user in the Logger with a 'Workout resumed' snackbar."
 * Activity-scoped (created once per process via [LogEzApp]'s `hiltViewModel()`), so this check
 * runs exactly once per cold start, not on every recomposition.
 */
@HiltViewModel
class AppStartupViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {
    private val _recoveredWorkoutId = MutableStateFlow<String?>(null)
    val recoveredWorkoutId: StateFlow<String?> = _recoveredWorkoutId

    init {
        viewModelScope.launch {
            _recoveredWorkoutId.value = workoutRepository.getInProgress()?.id
        }
    }

    /** Called once the recovery has been acted on (service restarted, navigated, snackbar shown) so it doesn't repeat on recomposition. */
    fun consumeRecovery() {
        _recoveredWorkoutId.value = null
    }
}
