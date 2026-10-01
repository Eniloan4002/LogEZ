package com.enil.logez.feature.workout.session

import com.enil.logez.core.common.Clock
import com.enil.logez.core.common.ElapsedRealtimeClock
import com.enil.logez.core.domain.model.ActiveInlineTimerSnapshot
import com.enil.logez.core.domain.model.TimerMode
import com.enil.logez.core.domain.repository.ActiveSessionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class WorkoutNotificationContent(
    val title: String,
    val text: String,
    val isResting: Boolean,
    /** §9.3 "Complete set" action target — the exercise/set the notification's primary action would complete. */
    val actionableExerciseId: String? = null,
    val actionableSetId: String? = null,
    /**
     * What the ongoing notification shows instead of [title]/[text] while a set timer runs: it
     * follows the TIMED set, wherever it is in the workout (not the first exercise with an open set).
     * Null when no set timer runs. Only used while [WorkoutSessionState.inlineTimer] is for the same set.
     */
    val timedSet: TimedSetNotificationContent? = null,
)

/** Names the timed set for the ongoing notification and the countdown-done heads-up: [title] "Plank · set 2 of 3", [label] "Plank · set 2". */
data class TimedSetNotificationContent(val exerciseId: String, val setId: String, val title: String, val label: String)

/**
 * A running inline set timer. [targetSeconds] is a countdown's length (null for a stopwatch);
 * [startWallMillis] is the start on the wall clock, saved beside the elapsedRealtime start so a
 * restore can tell a reboot from a process death ([InlineTimerEngine.isRestorable]).
 */
data class InlineTimerState(
    val exerciseId: String,
    val setId: String,
    val startElapsedRealtimeMillis: Long,
    val mode: TimerMode = TimerMode.STOPWATCH,
    val targetSeconds: Int? = null,
    val startWallMillis: Long = 0L,
) {
    /** The moment a countdown reaches 0:00, or null for a stopwatch. */
    val deadlineElapsedRealtimeMillis: Long? get() = InlineTimerEngine.deadline(mode, startElapsedRealtimeMillis, targetSeconds)
}

/** A set timer that ended: the seconds to write into the set's TIME. */
data class InlineTimerLog(val exerciseId: String, val setId: String, val seconds: Int)

data class WorkoutSessionState(
    val workoutId: String? = null,
    val isPaused: Boolean = false,
    val accumulatedActiveSeconds: Long = 0L,
    val lastResumedAtMillis: Long? = null,
    val isEmptyWorkoutTimerMode: Boolean = false,
    val restDeadlineElapsedRealtimeMillis: Long? = null,
    val restExerciseId: String? = null,
    val inlineTimer: InlineTimerState? = null,
    val notificationContent: WorkoutNotificationContent? = null,
) {
    val hasActiveSession: Boolean get() = workoutId != null
}

/**
 * PHASE2_PLAN.md §9.2 — single source of truth for the live workout's fast-tick timer state,
 * shared (Hilt `@Singleton`, not a Service binder — Service and ViewModel already live in the
 * same process) between [com.enil.logez.feature.workout.WorkoutLoggerViewModel] and
 * `WorkoutSessionService`. Deliberately framework-free beyond the injected [Clock]/
 * [ElapsedRealtimeClock] abstractions, so it's directly unit-testable under virtual time
 * (§10.6). Rest-timer expiry detection and anything needing a live `PowerManager`/
 * `NotificationManager` (§9.3/§9.4) is the Service's job, not this class's — it only exposes the
 * deadline for the Service to watch.
 */
@Singleton
class WorkoutSessionController @Inject constructor(
    private val activeSessionRepository: ActiveSessionRepository,
    private val clock: Clock,
    private val elapsedRealtimeClock: ElapsedRealtimeClock,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(WorkoutSessionState())
    val state: StateFlow<WorkoutSessionState> get() = _state

    private val _restTimerFired = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** Emits the owning exerciseId once, each time a rest timer reaches zero (fired by the Service — see class doc). */
    val restTimerFired: Flow<String> = _restTimerFired.asSharedFlow()

    private val _setCompletedExternally = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 4)
    /**
     * (workoutExerciseId, setId) each time the Service completes a set on Room's behalf (the
     * notification's "Complete set" action, which must work with no ViewModel alive — §9.3). An
     * already-open Logger screen subscribes to this to mirror the change into its own in-memory
     * write-through state instead of going stale until the next full reload.
     */
    val setCompletedExternally: Flow<Pair<String, String>> = _setCompletedExternally.asSharedFlow()
    fun notifySetCompletedExternally(workoutExerciseId: String, setId: String) {
        _setCompletedExternally.tryEmit(workoutExerciseId to setId)
    }

    private val _countdownFinished = MutableSharedFlow<InlineTimerLog>(extraBufferCapacity = 4)
    /**
     * Emits once when a countdown reaches 0:00 (fired by the Service, which owns watching the
     * deadline, like the rest timer). The Service writes the full time to Room and plays the alert;
     * an open logger mirrors the time into its own state.
     */
    val countdownFinished: Flow<InlineTimerLog> = _countdownFinished.asSharedFlow()

    private val _inlineTimerLoggedExternally = MutableSharedFlow<InlineTimerLog>(extraBufferCapacity = 4)
    /** A set timer stopped by something other than the logger's own Stop (the notification's "Complete set"): the Service has written [InlineTimerLog.seconds] to Room, and an open logger mirrors it. */
    val inlineTimerLoggedExternally: Flow<InlineTimerLog> = _inlineTimerLoggedExternally.asSharedFlow()
    fun notifyInlineTimerLoggedExternally(log: InlineTimerLog) {
        _inlineTimerLoggedExternally.tryEmit(log)
    }

    private var rehydrated = false

    /** §9.5 process-death recovery: loads the persisted snapshot once. Safe to call repeatedly (no-ops after the first). */
    suspend fun rehydrate() {
        if (rehydrated) return
        rehydrated = true
        val snapshot = activeSessionRepository.getSnapshot()
        if (snapshot.workoutId != null) {
            _state.value = WorkoutSessionState(
                workoutId = snapshot.workoutId,
                isPaused = snapshot.isPaused,
                accumulatedActiveSeconds = snapshot.accumulatedActiveSeconds,
                lastResumedAtMillis = snapshot.lastResumedAtMillis,
                isEmptyWorkoutTimerMode = snapshot.isEmptyWorkoutTimerMode,
                restDeadlineElapsedRealtimeMillis = snapshot.restDeadlineElapsedRealtimeMillis,
                restExerciseId = snapshot.restExerciseId,
                inlineTimer = restoredInlineTimer(snapshot.inlineTimer),
            )
        }
    }

    /**
     * The saved set timer if it can still be trusted. After a reboot (or a clock change) the two
     * clocks disagree and it is dropped, saved as gone, rather than shown with a wrong time. A
     * countdown whose deadline passed while the app was dead is kept: the Service (or the logger,
     * whichever is up first) then finishes it at once, logging the full time. A stopwatch older
     * than [InlineTimerEngine.MAX_RESTORED_STOPWATCH_MS] is dropped too: a plank held for a day is
     * not a measurement, and logging it would put a bogus duration into records.
     */
    private fun restoredInlineTimer(saved: ActiveInlineTimerSnapshot?): InlineTimerState? {
        if (saved == null) return null
        val nowWall = clock.now().toEpochMilliseconds()
        val nowElapsed = elapsedRealtimeClock.elapsedRealtimeMillis()
        if (!InlineTimerEngine.isRestorable(saved, nowWall, nowElapsed)) {
            // Persisted as gone, so it is not offered again. The write reads the live state, see persistInlineTimer.
            persistInlineTimer()
            return null
        }
        return InlineTimerState(
            exerciseId = saved.exerciseId,
            setId = saved.setId,
            startElapsedRealtimeMillis = saved.startElapsedRealtimeMillis,
            mode = TimerMode.of(saved.mode),
            targetSeconds = saved.targetSeconds,
            startWallMillis = saved.startWallMillis,
        )
    }

    /**
     * `suspend` and awaits the persist directly (not fire-and-forget) — a process death right
     * after this returns must never leave the persisted snapshot without a workoutId while Room's
     * workout row is genuinely IN_PROGRESS: [rehydrate] only ever repairs a null in-memory state
     * from a non-null persisted one, so a lost fire-and-forget write here would have made the
     * controller believe no session was active for the rest of the process's life (breaking the
     * notification's "Complete set" action and freezing the elapsed-time display) with no way to
     * recover short of restarting the app.
     */
    suspend fun startSession(workoutId: String, waitForFirstExercise: Boolean = false) {
        val now = clock.now().toEpochMilliseconds()
        val resumedAt = now.takeUnless { waitForFirstExercise }
        _state.value = WorkoutSessionState(
            workoutId = workoutId,
            isPaused = waitForFirstExercise,
            lastResumedAtMillis = resumedAt,
            isEmptyWorkoutTimerMode = waitForFirstExercise,
        )
        activeSessionRepository.startSession(workoutId, resumedAt, waitForFirstExercise)
    }

    /** Finish/discard — clears both in-memory and persisted session state. */
    fun endSession() {
        _state.value = WorkoutSessionState()
        scope.launch { activeSessionRepository.clearSession() }
    }

    /** `suspend` for the same reason as [startSession] — a lost write here rehydrates a stale
     * pre-pause snapshot after a process death, inflating the live elapsed-time display by however
     * long the process was gone (Room's own persisted `durationSeconds`, computed independently
     * from `startedAt` at finish time, is unaffected — this only corrupts the live UI). */
    suspend fun pause() {
        val s = _state.value
        if (s.workoutId == null || s.isPaused) return
        val now = clock.now().toEpochMilliseconds()
        val accumulated = WorkoutDurationEngine.accumulateOnPause(s.accumulatedActiveSeconds, s.lastResumedAtMillis, now)
        _state.update { it.copy(isPaused = true, accumulatedActiveSeconds = accumulated, lastResumedAtMillis = null) }
        activeSessionRepository.updateDurationBookkeeping(true, accumulated, null)
    }

    suspend fun resume() {
        val s = _state.value
        if (s.workoutId == null || !s.isPaused) return
        val now = clock.now().toEpochMilliseconds()
        _state.update { it.copy(isPaused = false, lastResumedAtMillis = now) }
        activeSessionRepository.updateDurationBookkeeping(false, s.accumulatedActiveSeconds, now)
    }

    /** Restarts the visible session clock while preserving the workout and empty-workout mode. */
    suspend fun resetElapsedTime(paused: Boolean) {
        val s = _state.value
        if (s.workoutId == null) return
        val resumedAt = clock.now().toEpochMilliseconds().takeUnless { paused }
        _state.update {
            it.copy(isPaused = paused, accumulatedActiveSeconds = 0L, lastResumedAtMillis = resumedAt)
        }
        activeSessionRepository.updateDurationBookkeeping(paused, 0L, resumedAt)
    }

    fun elapsedSeconds(nowMillis: Long = clock.now().toEpochMilliseconds()): Long {
        val s = _state.value
        return WorkoutDurationEngine.elapsedSeconds(s.accumulatedActiveSeconds, s.isPaused, s.lastResumedAtMillis, nowMillis)
    }

    /** Ticks once/sec while collected (cold — no cost when nobody's watching); freezes automatically once paused. */
    val elapsedSecondsFlow: Flow<Long> = flow {
        while (true) {
            if (_state.value.workoutId != null) emit(elapsedSeconds())
            delay(1_000)
        }
    }

    // --- Rest timer (§5.1.4/§9.4) ---

    /** `durationSeconds <= 0` means "off" (§5.1.4: `restTimerSeconds = 0` -> no bar, no sound) — treated as an immediate skip. */
    fun startRestTimer(exerciseId: String, durationSeconds: Int) {
        if (durationSeconds <= 0) {
            skipRestTimer()
            return
        }
        val deadline = RestTimerEngine.startDeadline(elapsedRealtimeClock.elapsedRealtimeMillis(), durationSeconds)
        _state.update { it.copy(restDeadlineElapsedRealtimeMillis = deadline, restExerciseId = exerciseId) }
        scope.launch { activeSessionRepository.updateRestTimer(deadline, exerciseId) }
    }

    fun adjustRestTimer(deltaSeconds: Int) {
        val s = _state.value
        val deadline = s.restDeadlineElapsedRealtimeMillis ?: return
        val adjusted = RestTimerEngine.adjustDeadline(deadline, deltaSeconds, elapsedRealtimeClock.elapsedRealtimeMillis())
        if (adjusted == null) {
            skipRestTimer()
            return
        }
        _state.update { it.copy(restDeadlineElapsedRealtimeMillis = adjusted) }
        scope.launch { activeSessionRepository.updateRestTimer(adjusted, s.restExerciseId) }
    }

    fun skipRestTimer() {
        if (_state.value.restDeadlineElapsedRealtimeMillis == null) return
        _state.update { it.copy(restDeadlineElapsedRealtimeMillis = null, restExerciseId = null) }
        scope.launch { activeSessionRepository.updateRestTimer(null, null) }
    }

    /** Called by the Service when its own watcher observes the deadline has passed — clears state and fires the alert exactly once. */
    fun markRestTimerFired() {
        val exerciseId = _state.value.restExerciseId ?: return
        _state.update { it.copy(restDeadlineElapsedRealtimeMillis = null, restExerciseId = null) }
        scope.launch { activeSessionRepository.updateRestTimer(null, null) }
        _restTimerFired.tryEmit(exerciseId)
    }

    /** Ticks once/sec while a rest timer is active; emits null (and stops ticking meaningfully) once it clears. */
    val restRemainingMillisFlow: Flow<Long?> = flow {
        while (true) {
            val deadline = _state.value.restDeadlineElapsedRealtimeMillis
            emit(if (deadline == null) null else RestTimerEngine.remainingMillis(deadline, elapsedRealtimeClock.elapsedRealtimeMillis()))
            delay(1_000)
        }
    }

    // --- Inline timer (§5.1.3: a DURATION-family TIME-cell timer, one at a time -- a stopwatch or a countdown, per workout exercise) ---

    /**
     * Starts a set timer. A [TimerMode.COUNTDOWN] needs a positive [targetSeconds] (its length);
     * without one nothing starts and this returns false. A stopwatch ignores [targetSeconds]. It
     * replaces any running timer without logging it, so the caller stops and logs that one first
     * ([stopRunningInlineTimer]). Saved with the session, so it survives process death.
     */
    fun startInlineTimer(exerciseId: String, setId: String, mode: TimerMode = TimerMode.STOPWATCH, targetSeconds: Int? = null): Boolean {
        if (mode == TimerMode.COUNTDOWN && (targetSeconds ?: 0) <= 0) return false
        val timer = InlineTimerState(
            exerciseId = exerciseId,
            setId = setId,
            startElapsedRealtimeMillis = elapsedRealtimeClock.elapsedRealtimeMillis(),
            mode = mode,
            targetSeconds = targetSeconds.takeIf { mode == TimerMode.COUNTDOWN },
            startWallMillis = clock.now().toEpochMilliseconds(),
        )
        _state.update { it.copy(inlineTimer = timer) }
        persistInlineTimer()
        return true
    }

    /** Returns elapsed seconds to commit into `durationSeconds`, or null if no inline timer was running for this exact set. */
    fun stopInlineTimer(exerciseId: String, setId: String): Int? {
        val running = _state.value.inlineTimer ?: return null
        if (running.exerciseId != exerciseId || running.setId != setId) return null
        return stopRunningInlineTimer()?.seconds
    }

    /** Stops whichever set timer is running and returns what to log into that set's TIME, or null when none runs. */
    fun stopRunningInlineTimer(): InlineTimerLog? {
        val running = _state.value.inlineTimer ?: return null
        val seconds = InlineTimerEngine.loggedSeconds(
            running.mode, running.startElapsedRealtimeMillis, running.targetSeconds, elapsedRealtimeClock.elapsedRealtimeMillis(),
        )
        // Only the caller that actually clears this timer logs it: a stop on one thread and a
        // countdown finishing on another can both have read it, and the loser gets null.
        if (!clearInlineTimerIf(running)) return null
        persistInlineTimer()
        return InlineTimerLog(running.exerciseId, running.setId, seconds)
    }

    /** Clears the running timer only if it is still [running], atomically. False when something else already cleared or replaced it. */
    private fun clearInlineTimerIf(running: InlineTimerState): Boolean {
        while (true) {
            val current = _state.value
            if (current.inlineTimer != running) return false
            if (_state.compareAndSet(current, current.copy(inlineTimer = null))) return true
        }
    }

    /**
     * Called by the Service when its watcher sees a countdown's deadline pass (or finds it already
     * past after a restart). Clears the timer and emits the full target time once on [countdownFinished].
     * Returns null, and does nothing, if no countdown is running or it has not reached 0:00 yet.
     */
    fun finishCountdown(): InlineTimerLog? {
        val running = _state.value.inlineTimer ?: return null
        val target = running.targetSeconds ?: return null
        if (!InlineTimerEngine.isFinished(running.mode, running.startElapsedRealtimeMillis, target, elapsedRealtimeClock.elapsedRealtimeMillis())) return null
        if (!clearInlineTimerIf(running)) return null
        persistInlineTimer()
        val log = InlineTimerLog(running.exerciseId, running.setId, target)
        _countdownFinished.tryEmit(log)
        return log
    }

    /** Serialises the saves of the running timer, see [persistInlineTimer]. */
    private val inlineTimerWriteLock = Mutex()

    /**
     * Saves the timer as it is when the write runs, not as it was when this was called. The scope
     * is multi-threaded, so two quick calls (stop one set's timer, start another's) can reach the
     * store in either order; each write takes the lock and reads the live state, so whichever runs
     * last always writes the latest, and a late "remove" can never erase a newer timer.
     */
    private fun persistInlineTimer() {
        scope.launch {
            inlineTimerWriteLock.withLock {
                val snapshot = _state.value.inlineTimer?.let {
                    ActiveInlineTimerSnapshot(
                        exerciseId = it.exerciseId,
                        setId = it.setId,
                        mode = it.mode.stored,
                        startWallMillis = it.startWallMillis,
                        startElapsedRealtimeMillis = it.startElapsedRealtimeMillis,
                        targetSeconds = it.targetSeconds,
                    )
                }
                activeSessionRepository.updateInlineTimer(snapshot)
            }
        }
    }

    /**
     * What the TIME cell shows while a set timer runs: whole seconds elapsed for a stopwatch, seconds
     * left for a countdown. Wakes on each display change, not on a fixed tick, so it does not drift.
     */
    val inlineTimerSecondsFlow: Flow<Int?> = flow {
        while (true) {
            val running = _state.value.inlineTimer
            val now = elapsedRealtimeClock.elapsedRealtimeMillis()
            emit(running?.let { InlineTimerEngine.displaySeconds(it.mode, it.startElapsedRealtimeMillis, it.targetSeconds, now) })
            delay(running?.let { InlineTimerEngine.millisUntilNextDisplayChange(it.mode, it.startElapsedRealtimeMillis, it.targetSeconds, now) } ?: 1_000L)
        }
    }

    /** §9.3 — pushed by the ViewModel whenever the displayable "current exercise" summary changes; read by the Service to build the notification. */
    fun updateNotificationContent(content: WorkoutNotificationContent?) {
        _state.update { it.copy(notificationContent = content) }
    }
}
