package com.enil.logez.feature.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.feature.wellness.HealthMetricsSource
import com.enil.logez.feature.wellness.HeartRateSample
import com.enil.logez.feature.wellness.liveHeartRateFlow
import com.enil.logez.feature.workout.WorkoutStarter
import com.enil.logez.feature.workout.session.WorkoutSessionController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

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
    private val settingsRepository: SettingsRepository,
    healthMetricsSource: HealthMetricsSource,
) : ViewModel() {
    val state: StateFlow<ActivityTrackingState> = controller.state
    val elapsedSecondsFlow: Flow<Int> = controller.elapsedSecondsFlow

    /** M21f: cold flow, same spine-rule shape as `elapsedSecondsFlow` -- see `liveHeartRateFlow`'s
     * own doc comment for why this must not be an eagerly-started poller. */
    val liveBpmFlow: Flow<HeartRateSample?> = liveHeartRateFlow(healthMetricsSource)

    /** Distance unit (for pace) and max heart rate (for the live zone) -- the two settings the
     * screen's pace/zone display needs. */
    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    /**
     * M21 redesign (2026-09-11): Finish now goes straight to the Save Workout screen instead of
     * handing off into the strength Logger (which used to be the thing that later called
     * `sessionController.endSession()` itself, in `WorkoutLoggerViewModel.prepareForFinish()`).
     * Ending it here instead, mirroring [cancel]'s existing call -- otherwise the mini-bar's
     * "in-progress, tap to resume" state would keep pointing at a workoutId that's about to become
     * COMPLETED, since nothing else on the new direct path ever clears it.
     */
    suspend fun finish(): FinishedTrack? {
        val result = controller.finishTracking()
        sessionController.endSession()
        return result
    }

    suspend fun cancel() {
        controller.cancelTracking()
        workoutStarter.discardInProgress()
        sessionController.endSession()
    }
}
