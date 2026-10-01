package com.enil.logez.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.domain.repository.RecentWorkout
import com.enil.logez.core.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * P-205: "Recent" — any completed strength workout, routine-backed or ad hoc, with a one-tap way to
 * start a new session carrying the same exercises. Shared by the Workout tab's inline card (the
 * newest 3, [cardState]) and the "See all" screen ([RecentWorkoutsScreen], every one, [uiState]) —
 * each screen gets its own instance via `hiltViewModel()` and only the flow it collects runs.
 *
 * GPS walks and runs are left out (R-3, 2026-10-01): the "Track a walk/run" card already starts one
 * in a tap, and a run stays in History with its map. Both flows come from one query that counts each
 * workout's exercises (it used to ask once per workout), and the card's has `LIMIT 3`.
 *
 * "Start" reuses [com.enil.logez.feature.workout.WorkoutStarter.startFromWorkout] verbatim — the
 * exact function History's own "Copy Workout" action already calls — through [RecentWorkoutStarter]:
 * the new session opens pre-filled with what was actually logged last time (weights and reps
 * included, sets re-numbered fresh). A routine-backed entry copies from that specific past session,
 * not from the routine's current template — "Recent" is about repeating a session you actually ran.
 */
@HiltViewModel
class RecentWorkoutsViewModel @Inject constructor(
    workoutRepository: WorkoutRepository,
    starter: RecentWorkoutStarter,
) : ViewModel(), RecentStartActions by starter {

    /** The Workout tab card: the newest [CARD_SIZE]. */
    val cardState: StateFlow<List<RecentWorkoutCardModel>> = workoutRepository.observeRecentStrengthWorkouts(CARD_SIZE)
        .map { rows -> rows.map(::toCard) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The "See all" list: every finished strength workout. */
    val uiState: StateFlow<List<RecentWorkoutCardModel>> = workoutRepository.observeRecentStrengthWorkouts(null)
        .map { rows -> rows.map(::toCard) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun toCard(row: RecentWorkout) = RecentWorkoutCardModel(
        workoutId = row.workoutId,
        title = row.title,
        startedAtMillis = row.startedAt,
        durationSeconds = row.durationSeconds,
        exerciseCount = row.exerciseCount,
        isFromRoutine = row.routineId != null,
    )

    companion object {
        const val CARD_SIZE = 3
    }
}

data class RecentWorkoutCardModel(
    val workoutId: String,
    val title: String,
    val startedAtMillis: Long,
    val durationSeconds: Int,
    val exerciseCount: Int,
    val isFromRoutine: Boolean,
)
