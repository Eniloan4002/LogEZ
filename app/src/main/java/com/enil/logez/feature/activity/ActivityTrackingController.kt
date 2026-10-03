package com.enil.logez.feature.activity

import com.enil.logez.core.common.Clock
import com.enil.logez.core.common.GeoDistance
import com.enil.logez.core.common.PolylineEncoding
import com.enil.logez.core.data.entity.ActivityTrackEntity
import com.enil.logez.core.domain.calc.PauseRanges
import com.enil.logez.core.domain.model.DistanceUnit
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

/** How good the GPS signal is right now, for the tracking screen's chip. Derived, see [ActivityTrackingState.gpsSignal]. */
enum class GpsSignal { FINDING, GOOD, WEAK }

data class ActivityTrackingState(
    val workoutId: String? = null,
    val workoutSetId: String? = null,
    val startedAtMillis: Long? = null,
    val distanceMeters: Double = 0.0,
    /** Same accepted-fix sequence [finishTracking] encodes into `route_polyline` -- exposed live
     * here too so the live tracking screen's map can draw it as it grows. */
    val routePoints: List<Pair<Double, Double>> = emptyList(),
    /** Seconds since start (on the clock, paused time included) for each of [routePoints]; what `route_times` saves. */
    val routeTimes: List<Long> = emptyList(),
    /** Tracked distance at each of [routePoints], so "pace now" can be read off the last minute. */
    val routeDistances: List<Double> = emptyList(),
    /** The newest fix of usable accuracy, moving or not and paused or not: where the map's dot sits. */
    val currentPosition: Pair<Double, Double>? = null,
    val isPaused: Boolean = false,
    /** When the current pause began, null while moving. */
    val pausedAtMillis: Long? = null,
    /** Finished pauses as (start, end) seconds since start on the clock; what `pause_ranges` saves. */
    val pauseRanges: List<Pair<Long, Long>> = emptyList(),
    /** Milliseconds paused in finished pauses, so moving time is exact and not rounded per pause. */
    val pausedMillisTotal: Long = 0L,
    /** When the newest fix within the accuracy limit arrived; null until the first one. */
    val lastGoodFixMillis: Long? = null,
    /** While recovering from a weak signal: how many good fixes in a row have arrived since (needs two), else 0. */
    val weakRecoveryFixes: Int = 0,
) {
    val isTracking: Boolean get() = workoutId != null

    /** Moving time: since the start, minus every pause (a pause still open counts up to [nowMillis]). */
    fun elapsedSeconds(nowMillis: Long): Int {
        val startedAt = startedAtMillis ?: return 0
        val openPause = pausedAtMillis?.let { (nowMillis - it).coerceAtLeast(0L) } ?: 0L
        return ((nowMillis - startedAt - pausedMillisTotal - openPause) / 1000).toInt().coerceAtLeast(0)
    }

    /** Seconds the current pause has lasted, or null while moving. */
    fun pausedForSeconds(nowMillis: Long): Int? = pausedAtMillis?.let { ((nowMillis - it) / 1000).toInt().coerceAtLeast(0) }

    /**
     * One threshold, so the chip is never between states: a usable fix within [GPS_WEAK_AFTER_MS] is
     * good; before the first one it is still finding; after a gap it stays weak until two good fixes
     * in a row have arrived, so a 3 s fix cadence cannot make it flicker.
     */
    fun gpsSignal(nowMillis: Long): GpsSignal {
        val last = lastGoodFixMillis ?: return GpsSignal.FINDING
        return if (weakRecoveryFixes > 0 || nowMillis - last > GPS_WEAK_AFTER_MS) GpsSignal.WEAK else GpsSignal.GOOD
    }
}

/** No usable fix for this long and the signal is weak (the chip says so, and "pace now" reads "-"). */
const val GPS_WEAK_AFTER_MS = 15_000L

data class FinishedTrack(val workoutId: String, val distanceMeters: Double, val durationSeconds: Int)

/**
 * M21a. Framework-free GPS session state machine, the same split-of-concerns as
 * [com.enil.logez.feature.workout.session.WorkoutSessionController] (Service = platform glue
 * only, Controller = the testable state machine), shared as a Hilt singleton between
 * `ActivityTrackingService` and the live-tracking ViewModel.
 *
 * v1 scope, deliberately: no process-death rehydration (if the process dies mid-run, the in-progress
 * track is lost -- a known, accepted limitation, unlike `WorkoutSessionController`'s full recovery
 * story). Pause/resume (2026-10-01) is in-memory too; the strength session clock mirrors it, which
 * is what the interrupted-run recovery reads.
 *
 * While paused the location listener keeps running (decision 8: the GPS chip stays honest and Resume
 * does not wait for a fix) but its fixes add no distance and no route point; on Resume the distance
 * re-anchors to the newest fix, so the paused gap adds none.
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

    // Mutated only under [lock]. onFix runs in fixJob's single collector coroutine, but pause() and
    // resume() arrive from the UI thread: without the lock a fix landing as the user taps Pause could
    // add a route point inside the paused range, which the hop-crossing maths would then not see.
    // finishTracking() reads these after the accumulated total, so it must cancelAndJoin() the job
    // first to avoid racing a still-landing fix. cancelTracking() throws the accumulated state away
    // entirely rather than reading it, so a plain (non-suspending) cancel() is sufficient there.
    private val lock = Any()
    private val routePoints = mutableListOf<Pair<Double, Double>>()

    /** Seconds since start for each entry of [routePoints], appended together so the lists never drift apart. */
    private val routeTimes = mutableListOf<Long>()
    private val routeDistances = mutableListOf<Double>()
    private var lastAccepted: LocationFix? = null
    private var accuracySumMeters = 0.0
    private var fixJob: Job? = null

    /** The newest usable fix seen during the current pause and when it arrived: Resume's re-anchor point. */
    private var pauseAnchor: Pair<LocationFix, Long>? = null

    fun startTracking(workoutId: String, workoutSetId: String) {
        synchronized(lock) {
            routePoints.clear()
            routeTimes.clear()
            routeDistances.clear()
            lastAccepted = null
            pauseAnchor = null
            accuracySumMeters = 0.0
            _state.value = ActivityTrackingState(
                workoutId = workoutId,
                workoutSetId = workoutSetId,
                startedAtMillis = clock.now().toEpochMilliseconds(),
            )
        }
        fixJob = scope.launch { locationSource.fixes().collect(::onFix) }
    }

    /**
     * Stops time and distance. Does nothing without a session or when already paused. The caller
     * ([ActivityTrackingViewModel]) pauses the strength session clock with it.
     */
    fun pause() {
        synchronized(lock) {
            val current = _state.value
            if (!current.isTracking || current.isPaused) return
            pauseAnchor = null
            _state.update { it.copy(isPaused = true, pausedAtMillis = clock.now().toEpochMilliseconds()) }
        }
    }

    /**
     * Counts the pause into moving time's bookkeeping and re-anchors distance: to the newest fix seen
     * while paused when it is recent, else the next accepted fix starts the new stretch without
     * adding the gap. The next route point after this is the start of a new line, see [PauseRanges].
     */
    fun resume() {
        synchronized(lock) {
            val current = _state.value
            val pausedAt = current.pausedAtMillis ?: return
            val startedAt = current.startedAtMillis ?: return
            val now = clock.now().toEpochMilliseconds()
            val anchor = pauseAnchor
            lastAccepted = anchor?.takeIf { now - it.second <= GPS_WEAK_AFTER_MS }?.first
            pauseAnchor = null
            _state.update {
                it.copy(
                    isPaused = false,
                    pausedAtMillis = null,
                    pauseRanges = it.pauseRanges + ((pausedAt - startedAt) / 1000 to (now - startedAt) / 1000),
                    pausedMillisTotal = it.pausedMillisTotal + (now - pausedAt).coerceAtLeast(0L),
                )
            }
        }
    }

    /** Accuracy/stationary-drift filtering (rev. 3 plan §2.3): discard poor fixes and near-zero movement. */
    private fun onFix(fix: LocationFix) {
        if (fix.accuracyMeters > MAX_ACCEPTABLE_ACCURACY_METERS) return
        synchronized(lock) {
            val current = _state.value
            val startedAt = current.startedAtMillis ?: return
            val now = clock.now().toEpochMilliseconds()

            // The GPS chip counts every fix within the accuracy limit, even one dropped below for
            // moving under 3 m (standing at a crossing is good signal) or ignored while paused.
            val previousGood = current.lastGoodFixMillis
            val recovering = when {
                previousGood == null -> 0
                now - previousGood > GPS_WEAK_AFTER_MS -> 1
                current.weakRecoveryFixes > 0 -> current.weakRecoveryFixes + 1
                else -> 0
            }.let { if (it >= WEAK_RECOVERY_GOOD_FIXES) 0 else it }
            val position = fix.latitude to fix.longitude

            if (current.isPaused) {
                pauseAnchor = fix to now
                _state.update { it.copy(lastGoodFixMillis = now, weakRecoveryFixes = recovering, currentPosition = position) }
                return
            }

            val last = lastAccepted
            var distance = current.distanceMeters
            if (last != null) {
                val delta = GeoDistance.metersBetween(last.latitude, last.longitude, fix.latitude, fix.longitude)
                if (delta < MIN_MOVEMENT_METERS) {
                    _state.update { it.copy(lastGoodFixMillis = now, weakRecoveryFixes = recovering, currentPosition = position) }
                    return
                }
                distance += delta
            }
            lastAccepted = fix
            routePoints += position
            // On the clock, pauses included: the same time base heart-rate samples are keyed by.
            routeTimes += ((now - startedAt) / 1000).coerceAtLeast(0L)
            routeDistances += distance
            accuracySumMeters += fix.accuracyMeters

            // A defensive copy: the lists keep growing in place, so a stale emitted state must not
            // alias the same backing list a later fix would silently mutate out from under it.
            _state.update {
                it.copy(
                    distanceMeters = distance,
                    routePoints = routePoints.toList(),
                    routeTimes = routeTimes.toList(),
                    routeDistances = routeDistances.toList(),
                    currentPosition = position,
                    lastGoodFixMillis = now,
                    weakRecoveryFixes = recovering,
                )
            }
        }
    }

    /** Moving time: pauses are not counted. */
    fun elapsedSeconds(nowMillis: Long = clock.now().toEpochMilliseconds()): Int = _state.value.elapsedSeconds(nowMillis)

    /** What the live screen shows each second, in [unit]: moving time, "pace now", the GPS signal and the pause length. */
    fun liveStats(unit: DistanceUnit): Flow<LiveTrackingStats> = flow {
        while (true) {
            val state = _state.value
            if (state.isTracking) emit(state.liveStats(unit, clock.now().toEpochMilliseconds()))
            delay(1_000)
        }
    }

    /**
     * Writes distance/duration onto the existing `WorkoutSetEntity` and persists the encoded
     * route. Returns `null` if no session was active. The duration is MOVING time (decision 7); a
     * pause still open at Finish is closed here, so it is saved like any other.
     *
     * M21 redesign (2026-09-11): Finish now goes straight to the Save Workout screen instead of
     * handing off into the strength Logger first (decisions.md same date) -- the Logger was the
     * only place that used to mark this set `isCompleted` (its own checkmark tap) and freeze the
     * live elapsed time onto the parent `WorkoutEntity.durationSeconds`
     * (`WorkoutLoggerViewModel.prepareForFinish()`). Both now happen here instead, since nothing
     * downstream of this call visits the Logger anymore to do it: `WorkoutFinisher.finish()`'s own
     * save transaction purges any *uncompleted* set before rebuilding PRs, which would otherwise
     * silently delete this workout's one and only set.
     */
    suspend fun finishTracking(): FinishedTrack? {
        val startState = _state.value
        val workoutId = startState.workoutId ?: return null
        val workoutSetId = startState.workoutSetId ?: return null
        fixJob?.cancelAndJoin()

        // Re-read AFTER the join, not the pre-join snapshot above: a fix already in flight when
        // finishTracking() was called can still land in onFix() during cancelAndJoin()'s wait,
        // updating _state -- using the stale startState here would silently drop that last fix's
        // distance contribution from the saved total.
        val finalState = _state.value
        val now = clock.now().toEpochMilliseconds()
        val durationSeconds = finalState.elapsedSeconds(now)
        val distanceMeters = finalState.distanceMeters
        val pauseRanges = finalState.pauseRanges + listOfNotNull(
            finalState.pausedAtMillis?.let { pausedAt ->
                val startedAt = finalState.startedAtMillis ?: pausedAt
                (pausedAt - startedAt) / 1000 to (now - startedAt) / 1000
            },
        )
        workoutRepository.updateWorkoutSetDistance(workoutSetId, distanceMeters)
        workoutRepository.updateWorkoutSetDuration(workoutSetId, durationSeconds)
        workoutRepository.updateWorkoutSetCompletion(workoutSetId, true, now)
        activityTrackRepository.upsert(
            ActivityTrackEntity(
                id = UUID.randomUUID().toString(),
                workoutSetId = workoutSetId,
                routePolyline = routePoints.takeIf { it.isNotEmpty() }?.let(PolylineEncoding::encode),
                pointCount = routePoints.size,
                avgAccuracyM = routePoints.takeIf { it.isNotEmpty() }?.let { accuracySumMeters / it.size },
                // The time each point was recorded, so the summary can rebuild splits and a pace
                // chart after the fact (2026-09-26). On the clock, pauses included.
                routeTimes = routeTimes.takeIf { it.isNotEmpty() }?.let(PolylineEncoding::encodeDeltas),
                // Null for a run that was never paused, which reads exactly as before v11.
                pauseRanges = PauseRanges.encode(pauseRanges),
            ),
        )
        workoutRepository.getById(workoutId)?.let { workout ->
            workoutRepository.updateWorkout(workout.copy(durationSeconds = durationSeconds, updatedAt = now))
        }
        _state.value = ActivityTrackingState()
        return FinishedTrack(workoutId, distanceMeters, durationSeconds)
    }

    /** Discards the in-progress track entirely -- the caller is responsible for discarding the underlying `Workout` row too. */
    fun cancelTracking() {
        fixJob?.cancel()
        _state.value = ActivityTrackingState()
    }

    private companion object {
        const val MAX_ACCEPTABLE_ACCURACY_METERS = 20f
        const val MIN_MOVEMENT_METERS = 3.0
        const val WEAK_RECOVERY_GOOD_FIXES = 2
    }
}
