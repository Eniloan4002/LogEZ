package com.enil.logez.feature.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.common.Clock
import com.enil.logez.core.domain.model.GpsActivity
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.wellness.HealthMetricsSource
import com.enil.logez.core.wellness.HeartRateSample
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.wellness.HeartRateAccess
import com.enil.logez.core.wellness.heartRateAccessFlow
import com.enil.logez.core.wellness.liveHeartRateHistoryFlow
import com.enil.logez.feature.workout.SessionDiscarder
import com.enil.logez.feature.workout.session.WorkoutSessionController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** How the screen's run ended, once: the screen navigates on it and stops the foreground service. */
sealed interface TrackingEnd {
    /** Saved: on to the Save Workout screen. */
    data class Finished(val workoutId: String) : TrackingEnd

    /** Discarded, or there was nothing to save. */
    data object Cancelled : TrackingEnd
}

/**
 * M21a. A thin wrapper around the shared [ActivityTrackingController] singleton — tracking itself
 * (both this controller and [WorkoutSessionController]'s shared elapsed-time state) was already
 * started in `WorkoutTabViewModel` before navigating here.
 */
@HiltViewModel
class ActivityTrackingViewModel @Inject constructor(
    private val controller: ActivityTrackingController,
    private val sessionDiscarder: SessionDiscarder,
    private val sessionController: WorkoutSessionController,
    private val settingsRepository: SettingsRepository,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    val healthMetricsSource: HealthMetricsSource,
    private val clock: Clock,
    private val logger: AppLogger,
) : ViewModel() {
    val state: StateFlow<ActivityTrackingState> = controller.state

    /**
     * Whether heart rate can be read, so the Vitals card can say "not allowed" rather than "no
     * data" (2026-09-26). The BPM itself is now the newest sample in [heartRateHistoryFlow]: a
     * separate "last 5 minutes" poll went blank whenever the watch's sync ran late, which with
     * Samsung Health is most of the time, and cost a second Health Connect read every tick.
     */
    val heartRateAccessFlow: Flow<HeartRateAccess> = heartRateAccessFlow(healthMetricsSource)

    /** The user answered the in-card permission request; reads may resume after a disconnect. */
    fun onHeartRatePermissionResult(anyGranted: Boolean) {
        if (anyGranted) healthMetricsSource.onPermissionsRegranted()
    }

    /**
     * The vitals card's heart-rate chart. A plain function, not a property initialized once at
     * construction time, because [ActivityTrackingState.startedAtMillis] isn't known until
     * tracking has actually started -- the caller supplies it once `state.startedAtMillis` is
     * non-null, keyed via `remember(startedAtMillis)` so a fresh flow (and fresh poll) starts only
     * if a genuinely new session's start time ever appears.
     */
    fun heartRateHistoryFlow(startedAtMillis: Long): Flow<List<HeartRateSample>> =
        liveHeartRateHistoryFlow(healthMetricsSource, startedAtMillis, clock, logger = logger)

    /** Distance unit (for pace) and max heart rate (for the live zone) -- the two settings the
     * screen's pace/zone display needs. */
    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    /**
     * Moving time, "pace now", the GPS signal and the pause length, once a second, in the unit
     * Settings says. Cold, so nothing ticks while the screen is hidden.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val liveStats: Flow<LiveTrackingStats> = settingsRepository.settings
        .map { it.distanceUnit }
        .distinctUntilChanged()
        .flatMapLatest { unit -> controller.liveStats(unit) }

    /**
     * Pauses time, distance and the route, and the strength session clock with them (so the mini-bar
     * time matches, and a run killed while paused saves the time it had). Both writes land before
     * this returns.
     */
    suspend fun pause() {
        controller.pause()
        sessionController.pause()
    }

    suspend fun resume() {
        controller.resume()
        sessionController.resume()
    }

    // Pause, Resume, Finish and Discard run here, not in the screen's composition scope: the Activity
    // is recreated on a rotation or a font-scale change, which cancels that scope part-way through a
    // write. One at a time, in the order they were tapped.
    private val actions = Mutex()
    private val _ending = MutableStateFlow(false)

    /** True from the first Finish or Discard tap, so a second tap cannot end the run twice. Survives a recreation with the ViewModel. */
    val ending: StateFlow<Boolean> = _ending

    private val _ended = Channel<TrackingEnd>(Channel.BUFFERED)

    /** Delivered once, and held until a screen is collecting, so an end that lands mid-recreation is not lost. */
    val ended: Flow<TrackingEnd> = _ended.receiveAsFlow()

    fun onPause() {
        viewModelScope.launch { actions.withLock { withContext(NonCancellable) { pause() } } }
    }

    fun onResume() {
        viewModelScope.launch { actions.withLock { withContext(NonCancellable) { resume() } } }
    }

    /** Saves the run, then reports it on [ended]. */
    fun onFinish() = end { finish()?.let { TrackingEnd.Finished(it.workoutId) } ?: TrackingEnd.Cancelled }

    /** Throws the run away, then reports it on [ended]. */
    fun onDiscard() = end {
        cancel()
        TrackingEnd.Cancelled
    }

    private fun end(action: suspend () -> TrackingEnd) {
        if (!_ending.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            val outcome = try {
                actions.withLock { withContext(NonCancellable) { action() } }
            } catch (t: Throwable) {
                // Let the user try again rather than leave Finish dead; the failure itself still surfaces.
                _ending.value = false
                throw t
            }
            _ended.send(outcome)
        }
    }

    /**
     * Whether this run is a RUN, a WALK or neither, for the title and the discard dialog: resolved
     * the way the summary does (the seed exercise's id first, then its name).
     */
    suspend fun activityFor(workoutId: String): GpsActivity {
        val exerciseId = workoutRepository.getExercisesForWorkout(workoutId).minByOrNull { it.orderIndex }?.exerciseId
        return GpsActivity.resolve(exerciseId, exerciseId?.let { exerciseRepository.getById(it)?.name })
    }

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

    suspend fun cancel() = sessionDiscarder.discardInProgress()
}
