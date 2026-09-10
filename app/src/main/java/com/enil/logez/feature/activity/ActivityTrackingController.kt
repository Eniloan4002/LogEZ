package com.enil.logez.feature.activity

import com.enil.logez.core.common.Clock
import com.enil.logez.core.common.GeoDistance
import com.enil.logez.core.common.PolylineEncoding
import com.enil.logez.core.data.entity.ActivityTrackEntity
import com.enil.logez.core.domain.repository.ActivityTrackRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.feature.activity.location.LocationFix
import com.enil.logez.feature.activity.location.LocationSource
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActivityTrackingState(
    val workoutId: String? = null,
    val workoutSetId: String? = null,
    val startedAtMillis: Long? = null,
    val distanceMeters: Double = 0.0,
    /** Same accepted-fix sequence [finishTracking] encodes into `route_polyline` -- exposed live
     * here too so the live tracking screen's map can draw it as it grows. */
    val routePoints: List<Pair<Double, Double>> = emptyList(),
) {
    val isTracking: Boolean get() = workoutId != null
}

data class FinishedTrack(val workoutId: String, val distanceMeters: Double, val durationSeconds: Int)

/**
 * M21a. Framework-free GPS session state machine, the same split-of-concerns as
 * [com.enil.logez.feature.workout.session.WorkoutSessionController] (Service = platform glue
 * only, Controller = the testable state machine), shared as a Hilt singleton between
 * `ActivityTrackingService` and the live-tracking ViewModel.
 *
 * v1 scope, deliberately: no pause/resume (a tracked run/walk is start-to-finish continuous) and
 * no process-death rehydration (if the process dies mid-run, the in-progress track is lost — a
 * known, accepted limitation, unlike `WorkoutSessionController`'s full recovery story).
 */
@Singleton
class ActivityTrackingController @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val activityTrackRepository: ActivityTrackRepository,
    private val locationSource: LocationSource,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ActivityTrackingState())
    val state: StateFlow<ActivityTrackingState> get() = _state

    // Mutated only from onFix, which only ever runs inside fixJob's single collector coroutine.
    // finishTracking() reads these after the accumulated total, so it must cancelAndJoin() the job
    // first to avoid racing a still-landing fix. cancelTracking() throws the accumulated state away
    // entirely rather than reading it, so a plain (non-suspending) cancel() is sufficient there.
    private val routePoints = mutableListOf<Pair<Double, Double>>()
    private var lastAccepted: LocationFix? = null
    private var accuracySumMeters = 0.0
    private var fixJob: Job? = null

    fun startTracking(workoutId: String, workoutSetId: String) {
        routePoints.clear()
        lastAccepted = null
        accuracySumMeters = 0.0
        _state.value = ActivityTrackingState(
            workoutId = workoutId,
            workoutSetId = workoutSetId,
            startedAtMillis = clock.now().toEpochMilliseconds(),
        )
        fixJob = scope.launch { locationSource.fixes().collect(::onFix) }
    }

    /** Accuracy/stationary-drift filtering (rev. 3 plan §2.3): discard poor fixes and near-zero movement. */
    private fun onFix(fix: LocationFix) {
        if (fix.accuracyMeters > MAX_ACCEPTABLE_ACCURACY_METERS) return
        val last = lastAccepted
        if (last != null) {
            val delta = GeoDistance.metersBetween(last.latitude, last.longitude, fix.latitude, fix.longitude)
            if (delta < MIN_MOVEMENT_METERS) return
            _state.update { it.copy(distanceMeters = it.distanceMeters + delta) }
        }
        lastAccepted = fix
        routePoints += fix.latitude to fix.longitude
        accuracySumMeters += fix.accuracyMeters
        // A defensive copy: routePoints keeps growing in place, so a stale emitted state must not
        // alias the same backing list a later fix would silently mutate out from under it.
        _state.update { it.copy(routePoints = routePoints.toList()) }
    }

    fun elapsedSeconds(nowMillis: Long = clock.now().toEpochMilliseconds()): Int {
        val startedAt = _state.value.startedAtMillis ?: return 0
        return ((nowMillis - startedAt) / 1000).toInt().coerceAtLeast(0)
    }

    /** Ticks once/sec while collected (cold — no cost when nobody's watching), mirroring `WorkoutSessionController.elapsedSecondsFlow`. */
    val elapsedSecondsFlow: Flow<Int> = flow {
        while (true) {
            if (_state.value.isTracking) emit(elapsedSeconds())
            delay(1_000)
        }
    }

    /** Writes distance/duration onto the existing `WorkoutSetEntity` and persists the encoded route. Returns `null` if no session was active. */
    suspend fun finishTracking(): FinishedTrack? {
        val startState = _state.value
        val workoutId = startState.workoutId ?: return null
        val workoutSetId = startState.workoutSetId ?: return null
        fixJob?.cancelAndJoin()

        // Re-read AFTER the join, not the pre-join snapshot above: a fix already in flight when
        // finishTracking() was called can still land in onFix() during cancelAndJoin()'s wait,
        // updating _state — using the stale startState here would silently drop that last fix's
        // distance contribution from the saved total.
        val durationSeconds = elapsedSeconds()
        val distanceMeters = _state.value.distanceMeters
        workoutRepository.updateWorkoutSetDistance(workoutSetId, distanceMeters)
        workoutRepository.updateWorkoutSetDuration(workoutSetId, durationSeconds)
        activityTrackRepository.upsert(
            ActivityTrackEntity(
                id = UUID.randomUUID().toString(),
                workoutSetId = workoutSetId,
                routePolyline = routePoints.takeIf { it.isNotEmpty() }?.let(PolylineEncoding::encode),
                pointCount = routePoints.size,
                avgAccuracyM = routePoints.takeIf { it.isNotEmpty() }?.let { accuracySumMeters / it.size },
            ),
        )
        _state.value = ActivityTrackingState()
        return FinishedTrack(workoutId, distanceMeters, durationSeconds)
    }

    /** Discards the in-progress track entirely — the caller is responsible for discarding the underlying `Workout` row too. */
    fun cancelTracking() {
        fixJob?.cancel()
        _state.value = ActivityTrackingState()
    }

    private companion object {
        const val MAX_ACCEPTABLE_ACCURACY_METERS = 20f
        const val MIN_MOVEMENT_METERS = 3.0
    }
}
