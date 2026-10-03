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

/**
 * A fix whose own timestamp is older than this when it arrives is ignored entirely: no distance, no
 * route point, and it does not count towards the GPS signal chip either. The fused provider's first
 * callback can be a cached position from the last time any app asked for one, possibly kilometres
 * from where the run starts; counting it put about 10 km on a 40 s run (848 km/h on Avg speed). A
 * fix this old is a memory, not a reading. Ten seconds is over three of the 3 s update intervals,
 * so a live fix delayed by a busy device still passes.
 */
const val MAX_FIX_AGE_MS = 10_000L

/**
 * A fix that implies moving faster than this (12 m/s, about 43 km/h) from the last sane fix is
 * dropped as a GPS jump. It is above a sprint (a fast 100 m is about 10 m/s), so a jump of a few
 * hundred metres in seconds is caught while every walk and run passes. It is a jump filter, not a
 * vehicle filter: a bus or jeepney at 8 to 11 m/s passes every check and its distance is recorded.
 * Only a sustained speed above this is dropped (a car on a motorway, a fast descent on a bike),
 * which a walk and run tracker does not want either.
 */
const val MAX_PLAUSIBLE_SPEED_MPS = 12.0

/**
 * The shortest elapsed time a hop is judged over, in ms. Two fixes stamped the same millisecond, or
 * out of order after the clock stepped back, would otherwise divide by zero or a negative: a hop
 * over such a gap is judged as if one second had passed, so 12 m is still allowed and 50 m is not.
 */
const val MIN_HOP_ELAPSED_MS = 1_000L

/**
 * After this many fixes in a row that were each dropped as a jump but agree with one another, the
 * tracker accepts that its anchor was the wrong one (a bad first fix that was fresh and within the
 * accuracy limit) and re-anchors to the newest of them without adding the gap. Without it, one bad
 * anchor would reject every real fix for the rest of the run.
 */
const val HOP_RESYNC_FIXES = 3

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

    /**
     * A fix with the clock time it was taken at (its arrival time minus its age), not when it was
     * delivered. This is the controller's [Clock], the same time base as route times, pauses and the
     * GPS chip: a wall-clock step backwards mid-run makes the next fixes look simultaneous and costs
     * a few fixes before the resync, a known and bounded limitation.
     */
    private class TimedFix(val fix: LocationFix, val takenAtMillis: Long)

    /**
     * The newest fix that passed the stale and jump checks, whether or not it moved enough to be a
     * route point: what the next fix's speed is judged against. Kept apart from [lastAccepted] so
     * that standing still for ten minutes does not make a 5 km jump look like ten minutes of walking.
     */
    private var lastSane: TimedFix? = null

    /** The newest fix dropped as a jump, and how many agreeing ones in a row: see [HOP_RESYNC_FIXES]. */
    private var jumpCandidate: TimedFix? = null
    private var jumpStreak = 0

    /** The newest usable fix seen during the current pause: Resume's re-anchor point. */
    private var pauseAnchor: TimedFix? = null

    fun startTracking(workoutId: String, workoutSetId: String) {
        synchronized(lock) {
            routePoints.clear()
            routeTimes.clear()
            routeDistances.clear()
            lastAccepted = null
            lastSane = null
            jumpCandidate = null
            jumpStreak = 0
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
            jumpCandidate = null
            jumpStreak = 0
            _state.update { it.copy(isPaused = true, pausedAtMillis = clock.now().toEpochMilliseconds()) }
        }
    }

    /**
     * Counts the pause into moving time's bookkeeping and re-anchors distance: to the newest fix seen
     * while paused when it is recent, else the next accepted fix starts the new stretch without
     * adding the gap. The next route point after this is the start of a new line, see [PauseRanges].
     *
     * With a recent paused fix the speed check starts from that re-anchor, so a person who was driven
     * somewhere while paused does not look like an impossible hop. With no recent paused fix (a pause
     * shorter than a fix interval, or no signal during it) the check keeps the last sane fix from
     * before the pause and judges the first fix after Resume over the whole time since, pause
     * included: that only widens what is allowed, and it still catches a ghost fix just after a short
     * pause. If the person really was taken far away in that time, three agreeing fixes later the
     * tracker re-anchors (see [HOP_RESYNC_FIXES]) and the move adds no distance.
     */
    fun resume() {
        synchronized(lock) {
            val current = _state.value
            val pausedAt = current.pausedAtMillis ?: return
            val startedAt = current.startedAtMillis ?: return
            val now = clock.now().toEpochMilliseconds()
            val anchor = pauseAnchor?.takeIf { now - it.takenAtMillis <= GPS_WEAK_AFTER_MS }
            lastAccepted = anchor?.fix
            lastSane = anchor ?: lastSane
            jumpCandidate = null
            jumpStreak = 0
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

    /**
     * Fix filtering, in this order: poor accuracy (rev. 3 plan §2.3), a stale fix ([MAX_FIX_AGE_MS]),
     * a jump faster than [MAX_PLAUSIBLE_SPEED_MPS] from the last sane fix, then near-zero movement
     * (stationary drift). Distance, the route, "pace now" and the splits all read only what gets
     * through, so they cannot disagree about a fix that was dropped.
     */
    private fun onFix(fix: LocationFix) {
        if (fix.accuracyMeters > MAX_ACCEPTABLE_ACCURACY_METERS) return
        // A cached position is not a reading of where the person is now, so it is not even signal.
        if (fix.ageMillis > MAX_FIX_AGE_MS) return
        synchronized(lock) {
            val current = _state.value
            val startedAt = current.startedAtMillis ?: return
            val now = clock.now().toEpochMilliseconds()
            // When the fix was taken: a delivery delay must not stretch or shrink the time it is judged over.
            val taken = TimedFix(fix, now - fix.ageMillis.coerceAtLeast(0L))

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
                pauseAnchor = taken
                _state.update { it.copy(lastGoodFixMillis = now, weakRecoveryFixes = recovering, currentPosition = position) }
                return
            }

            // Speed since the last sane fix (not the last route point): a gap with no fix, like a
            // tunnel, is judged over its real length, so only a hop faster than the limit is dropped.
            val sane = lastSane
            if (sane != null) {
                if (isImpossibleHop(sane, taken)) {
                    val candidate = jumpCandidate
                    jumpStreak = if (candidate != null && !isImpossibleHop(candidate, taken)) jumpStreak + 1 else 1
                    jumpCandidate = taken
                    if (jumpStreak >= HOP_RESYNC_FIXES) {
                        // The fixes since the jump agree with each other and not with the anchor: the anchor
                        // was the bad one. Re-anchor here, adding no distance for the gap.
                        lastSane = taken
                        lastAccepted = fix
                        jumpCandidate = null
                        jumpStreak = 0
                        if (routePoints.size == 1) {
                            // The bad anchor was the whole route so far: start the route over from this fix
                            // rather than leave a stray point (and a line to it) kilometres away.
                            routePoints.clear()
                            routeTimes.clear()
                            routeDistances.clear()
                            routePoints += position
                            routeTimes += ((now - startedAt) / 1000).coerceAtLeast(0L)
                            routeDistances += 0.0
                            accuracySumMeters = fix.accuracyMeters.toDouble()
                            _state.update {
                                it.copy(
                                    distanceMeters = 0.0,
                                    routePoints = routePoints.toList(),
                                    routeTimes = routeTimes.toList(),
                                    routeDistances = routeDistances.toList(),
                                    currentPosition = position,
                                    lastGoodFixMillis = now,
                                    weakRecoveryFixes = recovering,
                                )
                            }
                        } else {
                            // Earlier points are real, so the route keeps them, and this fix joins it as a point
                            // with no distance of its own: the next fix's hop is measured from it, so the saved
                            // polyline holds that hop exactly. The line from the last point to this one is the
                            // jump. The map shows it; RouteSplitsCalculator measures a hop faster than
                            // MAX_PLAUSIBLE_SPEED_MPS as 0 m, so splits and the pace chart skip it too.
                            routePoints += position
                            routeTimes += ((now - startedAt) / 1000).coerceAtLeast(0L)
                            routeDistances += current.distanceMeters
                            accuracySumMeters += fix.accuracyMeters
                            _state.update {
                                it.copy(
                                    routePoints = routePoints.toList(),
                                    routeTimes = routeTimes.toList(),
                                    routeDistances = routeDistances.toList(),
                                    currentPosition = position,
                                    lastGoodFixMillis = now,
                                    weakRecoveryFixes = recovering,
                                )
                            }
                        }
                    } else {
                        // The map's dot stays where the last sane fix put it; the signal still counts.
                        _state.update { it.copy(lastGoodFixMillis = now, weakRecoveryFixes = recovering) }
                    }
                    return
                }
            }
            jumpCandidate = null
            jumpStreak = 0
            lastSane = taken

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

    /**
     * Whether [to] is further from [from] than [MAX_PLAUSIBLE_SPEED_MPS] allows in the time between
     * them. The time is the real time between the two fixes, never less than [MIN_HOP_ELAPSED_MS],
     * so a zero or backwards gap is neither a free pass nor a divide by zero. When [resume] had a
     * recent paused fix the two fixes never straddle a pause; when it had none they can, and the
     * pause only makes the allowance larger.
     */
    private fun isImpossibleHop(from: TimedFix, to: TimedFix): Boolean {
        val meters = GeoDistance.metersBetween(from.fix.latitude, from.fix.longitude, to.fix.latitude, to.fix.longitude)
        val elapsedMillis = (to.takenAtMillis - from.takenAtMillis).coerceAtLeast(MIN_HOP_ELAPSED_MS)
        return meters > MAX_PLAUSIBLE_SPEED_MPS * elapsedMillis / 1000.0
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
