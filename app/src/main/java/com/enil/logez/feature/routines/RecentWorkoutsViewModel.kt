package com.enil.logez.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.feature.workout.InProgressWorkout
import com.enil.logez.feature.workout.InProgressWorkoutResolver
import com.enil.logez.feature.workout.SessionDiscarder
import com.enil.logez.feature.workout.StartResult
import com.enil.logez.feature.workout.WorkoutStarter
import com.enil.logez.feature.workout.session.WorkoutSessionController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * P-205: "Recent" — any completed workout, routine-backed or ad hoc, with a one-tap way to start a
 * new session carrying the same exercises. Shared by the Workout tab's inline card (top 3) and the
 * "See all" screen ([RecentWorkoutsScreen]) — each screen gets its own instance via `hiltViewModel()`,
 * both backed by the same [WorkoutRepository.observeCompleted] flow, so there's no real duplication.
 *
 * "Start" reuses [WorkoutStarter.startFromWorkout] verbatim — the exact function History's own
 * "Copy Workout" action already calls (`WorkoutDetailViewModel.startCopy`) — rather than inventing
 * new copy semantics: the new session opens pre-filled with what was actually logged last time
 * (weights and reps included, sets re-numbered fresh), the same "repeat this" behaviour a workout
 * already offers from its own detail screen. A routine-backed entry copies from that specific past
 * session, not from the routine's current template — "Recent" is about repeating a session you
 * actually ran, not re-entering the routine (folders already do that).
 */
@HiltViewModel
class RecentWorkoutsViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val workoutStarter: WorkoutStarter,
    private val sessionController: WorkoutSessionController,
    private val sessionDiscarder: SessionDiscarder,
    private val inProgressWorkoutResolver: InProgressWorkoutResolver,
) : ViewModel() {

    val uiState: StateFlow<List<RecentWorkoutCardModel>> = workoutRepository.observeCompleted()
        .map { workouts ->
            workouts.map { workout ->
                RecentWorkoutCardModel(
                    workoutId = workout.id,
                    title = workout.title,
                    startedAtMillis = workout.startedAt,
                    durationSeconds = workout.durationSeconds,
                    // Small N (a handful of recent rows, not the whole feed) -- a per-row lookup
                    // here is the History-feed-batch optimisation's opposite case, not worth its
                    // own bulk query. See HistoryViewModel.buildCards's KDoc for the case where it is.
                    exerciseCount = workoutRepository.getExercisesForWorkout(workout.id).size,
                    isFromRoutine = workout.routineId != null,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun start(workoutId: String): StartResult {
        val result = workoutStarter.startFromWorkoutOrConflict(workoutId)
        if (result is StartResult.Started) sessionController.startSession(result.workoutId)
        return result
    }

    /** Mirrors `WorkoutDetailViewModel.discardInProgressAndStartCopy` -- same two calls, same order. */
    suspend fun discardInProgressAndStart(workoutId: String): String {
        sessionDiscarder.discardInProgress()
        val id = workoutStarter.startFromWorkout(workoutId)
        sessionController.startSession(id)
        return id
    }

    suspend fun inProgressWorkout(): InProgressWorkout? = inProgressWorkoutResolver.resolve()
}

data class RecentWorkoutCardModel(
    val workoutId: String,
    val title: String,
    val startedAtMillis: Long,
    val durationSeconds: Int,
    val exerciseCount: Int,
    val isFromRoutine: Boolean,
)
