package com.enil.logez.feature.activity

import androidx.lifecycle.ViewModel
import com.enil.logez.feature.wellness.HealthMetricsSource
import com.enil.logez.feature.wellness.liveHeartRateFlow
import com.enil.logez.feature.workout.WorkoutStarter
import com.enil.logez.feature.workout.session.WorkoutSessionController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * M21a. A thin wrapper around the shared [ActivityTrackingController] singleton — tracking itself
 * (both this controller and [WorkoutSessionController]'s shared elapsed-time state) was already
 * started in `WorkoutTabViewModel` before navigating here.
 */
@HiltViewModel
class ActivityTrackingViewModel @Inject constructor(
    private val controller: ActivityTrackingController,
    private val workoutStarter: WorkoutStarter,
    private val sessionController: WorkoutSessionController,
    healthMetricsSource: HealthMetricsSource,
) : ViewModel() {
    val state: StateFlow<ActivityTrackingState> = controller.state
    val elapsedSecondsFlow: Flow<Int> = controller.elapsedSecondsFlow

    /** M21f: cold flow, same spine-rule shape as `elapsedSecondsFlow` -- see `liveHeartRateFlow`'s
     * own doc comment for why this must not be an eagerly-started poller. */
    val liveBpmFlow: Flow<Long?> = liveHeartRateFlow(healthMetricsSource)

    /** Finish deliberately leaves `sessionController`'s state alone — the caller hands off into
     * the Logger next, which needs it to still point at this workoutId. */
    suspend fun finish(): FinishedTrack? = controller.finishTracking()

    suspend fun cancel() {
        controller.cancelTracking()
        workoutStarter.discardInProgress()
        sessionController.endSession()
    }
}
