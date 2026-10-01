package com.enil.logez.feature.workout.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.enil.logez.MainActivity
import com.enil.logez.R
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.common.ElapsedRealtimeClock
import com.enil.logez.core.designsystem.formatElapsedClock
import com.enil.logez.core.domain.model.TimerMode
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.feature.workout.audio.WorkoutAudioPlayer
import com.enil.logez.feature.workout.audio.WorkoutHapticsPlayer
import com.enil.logez.feature.workout.session.CountdownFinisher
import com.enil.logez.feature.workout.session.InlineTimerLog
import com.enil.logez.feature.workout.session.InlineTimerState
import com.enil.logez.feature.workout.session.SetCompletionUseCase
import com.enil.logez.feature.workout.session.TimedSetNotificationContent
import com.enil.logez.feature.workout.session.TimerWakeLock
import com.enil.logez.feature.workout.session.WorkoutSessionController
import com.enil.logez.feature.workout.session.WorkoutSessionState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * PHASE2_PLAN.md §9.2 — thin shell. All actual timer/duration logic lives in
 * [WorkoutSessionController] (framework-free, directly testable); this class only wires that
 * state to the platform APIs a Service uniquely has access to: the foreground notification, a
 * `PowerManager` wake lock bounding the rest-timer's doze insurance (§9.4), and the audio/haptic
 * side effects (§9.7). Runs only while a workout is `IN_PROGRESS` — never a second source of truth.
 */
@AndroidEntryPoint
class WorkoutSessionService : Service() {
    @Inject lateinit var sessionController: WorkoutSessionController
    @Inject lateinit var setCompletionUseCase: SetCompletionUseCase
    @Inject lateinit var workoutRepository: WorkoutRepository
    @Inject lateinit var exerciseRepository: ExerciseRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var audioPlayer: WorkoutAudioPlayer
    @Inject lateinit var hapticsPlayer: WorkoutHapticsPlayer
    @Inject lateinit var elapsedRealtimeClock: ElapsedRealtimeClock
    @Inject lateinit var logger: AppLogger

    // `var`, not `val`: stopSelfCleanly() cancels the job, and a cancelled SupervisorJob can never
    // run anything again, so a reused Service instance has to rebuild both. Unlike [wakeLock]
    // below these are touched only from main-thread lifecycle callbacks, so they need no guard.
    private var serviceJob = SupervisorJob()
    private var serviceScope = CoroutineScope(serviceJob)
    private var collectorsStarted = false
    /** True while this instance holds a live foreground session; see the notification-action check. */
    private var promoted = false
    /**
     * The name of the timed set: the last one the logger pushed while a set timer ran, or, when no
     * logger is alive (a timer restored after process death), one read from Room. Written from one
     * service coroutine and read from others, hence @Volatile.
     */
    @Volatile private var lastTimedSet: TimedSetNotificationContent? = null
    private val countdownFinisher by lazy { CountdownFinisher(workoutRepository, settingsRepository, audioPlayer, hapticsPlayer) }

    private var wakeLock: PowerManager.WakeLock? = null
    /** Guards [wakeLock]: the wake-lock collector runs on [serviceScope]'s (non-main) dispatcher
     * while [onDestroy] runs on the main thread — without this, acquire-then-assign in
     * [acquireWakeLock] can race a concurrent [releaseWakeLock] reading the field before the
     * assignment lands, orphaning a held lock past the service's own lifetime. */
    private val wakeLockGuard = Any()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        WorkoutNotificationChannels.ensureCreated(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Stop first, before any foreground promotion: stopWorkoutSessionService() sends this
        // through a plain startService(), so there is no startForegroundService() contract to
        // honour, and promoting a service only to tear it down again posted a notification for
        // nothing (and, on a service that was not already running, briefly started a new one).
        if (intent?.action == ACTION_STOP) {
            stopSelfCleanly()
            return START_NOT_STICKY
        }

        // A notification action (complete set, rest adjust/skip) that reaches an instance with no
        // live session: the tap raced Finish/Discard stopping the service. These arrive through a
        // plain startService(), so there is no promotion owed. Promoting here used to leave an
        // undismissable blank "workout" notification with nothing behind it (2026-09-25 review).
        if (intent?.action in NOTIFICATION_ACTIONS && !promoted) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (!startForegroundSafely(buildNotification(sessionController.state.value))) {
            // Android refused the foreground promotion -- realistically a START_STICKY restart
            // after the process was killed, which Android 12+ treats as a background start. The
            // workout itself is safe: it is an IN_PROGRESS row in Room, and the Workout tab's
            // resume banner reopens it the next time the app is launched. Staying started but not
            // foreground would only get the service killed again within a minute, and letting the
            // exception escape would crash the process, so stop and do not ask to be restarted.
            stopSelfCleanly()
            return START_NOT_STICKY
        }
        promoted = true
        ensureCollectorsStarted()

        when (intent?.action) {
            ACTION_COMPLETE_SET -> {
                val workoutId = sessionController.state.value.workoutId
                val workoutExerciseId = intent.getStringExtra(EXTRA_WORKOUT_EXERCISE_ID)
                val setId = intent.getStringExtra(EXTRA_SET_ID)
                if (isForCurrentSession(intent) && workoutId != null && workoutExerciseId != null && setId != null) {
                    serviceScope.launch {
                        NotificationManagerCompat.from(this@WorkoutSessionService).cancel(COUNTDOWN_DONE_NOTIFICATION_ID)
                        // A stale action (the "Countdown done" heads-up stays for 15 s) for a set the user
                        // has since checked in the app must not re-stamp it or restart its rest timer.
                        val alreadyDone = workoutRepository.getSetsForWorkoutExercise(workoutExerciseId).find { it.id == setId }?.isCompleted == true
                        if (alreadyDone) return@launch
                        // The set's own running timer stops and logs what it held first: the check that
                        // follows would otherwise lose the time. A timer on ANOTHER set is left running.
                        if (sessionController.state.value.inlineTimer?.setId == setId) {
                            sessionController.stopRunningInlineTimer()?.let { log ->
                                workoutRepository.updateWorkoutSetDuration(log.setId, log.seconds)
                                sessionController.notifyInlineTimerLoggedExternally(log)
                            }
                        }
                        if (setCompletionUseCase.completeSet(workoutId, workoutExerciseId, setId)) {
                            sessionController.notifySetCompletedExternally(workoutExerciseId, setId)
                        }
                    }
                }
            }
            ACTION_REST_ADJUST -> if (isForCurrentSession(intent)) sessionController.adjustRestTimer(intent.getIntExtra(EXTRA_DELTA_SECONDS, 0))
            ACTION_REST_SKIP -> if (isForCurrentSession(intent)) sessionController.skipRestTimer()
            ACTION_START -> Unit // session already started by the caller before startForegroundService()
            else -> {
                // §9.5: a null-intent system redelivery after process death — rehydrate from the
                // active-session store and verify the workout is still genuinely IN_PROGRESS
                // before staying foregrounded (it may have been finished/discarded meanwhile).
                serviceScope.launch {
                    sessionController.rehydrate()
                    val workoutId = sessionController.state.value.workoutId
                    val inProgress = workoutId?.let { workoutRepository.getById(it) }
                    if (workoutId == null || inProgress == null || inProgress.status.name != "IN_PROGRESS") {
                        stopSelfCleanly()
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        serviceJob.cancel()
        super.onDestroy()
    }

    /**
     * Cancels the collectors *before* tearing down the notification/service — otherwise a
     * collector already suspended on a matching state change (e.g. a rest deadline that just
     * elapsed) can still run to completion afterward and re-post a notification / play a sound
     * for a session that's being stopped, since Android may not call [onDestroy] for a while.
     */
    private fun stopSelfCleanly() {
        promoted = false
        collectorsStarted = false
        serviceJob.cancel()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureCollectorsStarted() {
        if (collectorsStarted) return
        // A cancelled SupervisorJob stays cancelled, so a Service instance reused after a
        // stopSelf() has to rebuild the scope or every collector below silently never runs.
        if (!serviceJob.isActive) {
            serviceJob = SupervisorJob()
            serviceScope = CoroutineScope(serviceJob)
        }
        collectorsStarted = true

        // Re-post only on an actual content/mode change (§9.3) — the chronometer itself renders
        // live seconds natively; we must not call notify() every tick.
        serviceScope.launch {
            sessionController.state
                .map { it.notificationContent?.timedSet to it.inlineTimer }
                .distinctUntilChanged()
                .collect { (pushed, timer) ->
                    if (pushed != null) {
                        lastTimedSet = pushed
                    } else if (timer != null && lastTimedSet?.setId != timer.setId) {
                        // No logger has named this set (a timer restored after process death): read it from Room.
                        val workoutId = sessionController.state.value.workoutId
                        val resolved = workoutId?.let { resolveTimedSet(it, timer.exerciseId, timer.setId) }
                        if (resolved != null) {
                            lastTimedSet = resolved
                            postNotification(buildNotification(sessionController.state.value))
                        }
                    }
                }
        }
        // A new set timer replaces the 15 s "Countdown done" heads-up of the one before it.
        serviceScope.launch {
            sessionController.state.map { it.inlineTimer?.setId }.distinctUntilChanged().collect { setId ->
                if (setId != null) NotificationManagerCompat.from(this@WorkoutSessionService).cancel(COUNTDOWN_DONE_NOTIFICATION_ID)
            }
        }
        serviceScope.launch {
            sessionController.state
                .map { NotificationDisplayKey(it.notificationContent, it.isPaused, it.restDeadlineElapsedRealtimeMillis != null, it.accumulatedActiveSeconds, it.inlineTimer) }
                .distinctUntilChanged()
                .collect { postNotification(buildNotification(sessionController.state.value)) }
        }

        // Rest-timer expiry: deadline-based, so a late wakeup after a doze stall still fires
        // correctly (the deadline math doesn't drift — only the alert timing can slip, per §9.4).
        serviceScope.launch {
            sessionController.state.map { it.restDeadlineElapsedRealtimeMillis }.distinctUntilChanged().collectLatest { deadline ->
                if (deadline != null) {
                    delay((deadline - elapsedRealtimeClock.elapsedRealtimeMillis()).coerceAtLeast(0))
                    sessionController.markRestTimerFired()
                }
            }
        }

        // A set countdown's expiry, watched the same way as the rest deadline: the Service owns it,
        // so a countdown that ends with the app closed still logs. After a restart a deadline that
        // already passed fires at once (the full time, one late alert).
        serviceScope.launch {
            sessionController.state.map { it.inlineTimer?.deadlineElapsedRealtimeMillis }.distinctUntilChanged().collectLatest { deadline ->
                if (deadline != null) {
                    while (true) {
                        val remaining = deadline - elapsedRealtimeClock.elapsedRealtimeMillis()
                        if (remaining <= 0) break
                        delay(remaining)
                    }
                    // The write, sound, buzz and heads-up run off the log this returns, not off a flow
                    // subscription: see [CountdownFinisher]. Null when the timer was stopped meanwhile.
                    // Launched on its own, not run inline: finishing clears the timer, which makes this
                    // collectLatest's deadline flow emit null and CANCEL this block, and with it the write.
                    sessionController.finishCountdown()?.let { log -> serviceScope.launch { countdownFinisher.finish(log, ::postCountdownDoneHeadsUp) } }
                }
            }
        }

        // §9.4 point 4: a bounded PARTIAL_WAKE_LOCK only while a rest or set countdown is active,
        // sized by [TimerWakeLock].
        serviceScope.launch {
            sessionController.state
                .map { s -> s.restDeadlineElapsedRealtimeMillis to s.inlineTimer?.deadlineElapsedRealtimeMillis }
                .distinctUntilChanged()
                .collect { (restDeadline, countdownDeadline) ->
                    releaseWakeLock()
                    TimerWakeLock.timeoutMs(restDeadline, countdownDeadline, elapsedRealtimeClock.elapsedRealtimeMillis())?.let { acquireWakeLock(it) }
                }
        }

        // §9.7: sound + vibration + a short-lived heads-up companion notification on rest-end.
        serviceScope.launch {
            sessionController.restTimerFired.collect {
                val settings = settingsRepository.settings.first()
                audioPlayer.playTimerSound(settings.timerSound, settings.timerVolume)
                hapticsPlayer.vibrateRestEnd()
                postRestEndHeadsUp()
            }
        }
    }

    private fun acquireWakeLock(timeoutMs: Long) = synchronized(wakeLockGuard) {
        val powerManager = getSystemService(POWER_SERVICE) as? PowerManager ?: return@synchronized
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "logez:timers").apply {
            setReferenceCounted(false)
            acquire(timeoutMs)
        }
    }

    private fun releaseWakeLock() = synchronized(wakeLockGuard) {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    /**
     * Promotes this service with the `health` foreground-service type, which is the type Android
     * documents for exercise trackers. It replaced `specialUse` on 2026-09-25 (Play-readiness
     * audit): Play reviews a specialUse declaration by hand and lists fitness sessions under
     * `health`, so specialUse invited a rejection the app had no need to risk.
     *
     * The explicit type only exists from API 34, and that is also the first release that checks
     * a type's runtime prerequisites. The manifest's HIGH_SAMPLING_RATE_SENSORS declaration is the
     * prerequisite this app meets: it is a normal, install-time permission, so a workout never
     * depends on Health Connect or a body-sensor grant the user may have declined. On API 26-33
     * the two-argument call promotes with the manifest's declared type.
     *
     * Returns false instead of throwing when Android refuses, see the caller for why that is safe.
     */
    private fun startForegroundSafely(notification: Notification): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        true
    } catch (e: IllegalStateException) {
        // ForegroundServiceStartNotAllowedException (API 31+) is an IllegalStateException.
        logger.e(TAG, "Foreground promotion refused; stopping the workout session service", e)
        false
    } catch (e: SecurityException) {
        // API 34+: a foreground-service type whose prerequisite is not met.
        logger.e(TAG, "Foreground service type prerequisite not met; stopping the workout session service", e)
        false
    }

    @SuppressLint("MissingPermission") // §9.3: NotificationManagerCompat.notify() safely no-ops if POST_NOTIFICATIONS was denied — the FGS itself keeps running either way.
    private fun postNotification(notification: Notification) {
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    @SuppressLint("MissingPermission")
    private fun postRestEndHeadsUp() {
        val notification = NotificationCompat.Builder(this, WorkoutNotificationChannels.REST_TIMER)
            .setSmallIcon(R.drawable.ic_stat_logez)
            .setContentTitle(getString(R.string.workout_rest_timer_label))
            // The one notification whose entire job is reaching the user mid-set, on a screen that
            // is almost certainly locked. Without this it defaults to VISIBILITY_PRIVATE and is
            // redacted on a secure lock screen, unlike both ongoing notifications.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent())
            .setTimeoutAfter(REST_END_HEADS_UP_TIMEOUT_MS)
            .build()
        NotificationManagerCompat.from(this).notify(REST_END_NOTIFICATION_ID, notification)
    }

    /** "Countdown done" / "Plank · set 2 · 1:00", with a Complete set action for the set that was timed. */
    @SuppressLint("MissingPermission")
    private suspend fun postCountdownDoneHeadsUp(log: InlineTimerLog) {
        val workoutId = sessionController.state.value.workoutId
        // The timer is already cleared and the logger may have dropped the timed-set content, so the
        // name comes from the last one seen while the timer ran, else from Room.
        val named = (sessionController.state.value.notificationContent?.timedSet?.takeIf { it.setId == log.setId })
            ?: lastTimedSet?.takeIf { it.setId == log.setId }
            ?: workoutId?.let { resolveTimedSet(it, log.exerciseId, log.setId) }
        val label = named?.label ?: getString(R.string.notification_workout_fallback_title)
        val builder = NotificationCompat.Builder(this, WorkoutNotificationChannels.REST_TIMER)
            .setSmallIcon(R.drawable.ic_stat_logez)
            .setContentTitle(getString(R.string.notification_countdown_done_title))
            .setContentText(getString(R.string.notification_countdown_done_text, label, formatElapsedClock(log.seconds)))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent())
            .setTimeoutAfter(REST_END_HEADS_UP_TIMEOUT_MS)
        if (workoutId != null) {
            builder.addAction(0, getString(R.string.notification_action_complete_set), completeSetPendingIntent(workoutId, log.exerciseId, log.setId, REQUEST_COUNTDOWN_COMPLETE_SET))
        }
        NotificationManagerCompat.from(this).notify(COUNTDOWN_DONE_NOTIFICATION_ID, builder.build())
    }

    /** "Plank · set 2 of 3" for a set, read from Room: the fallback when no logger has named it. Null if it cannot be found. */
    private suspend fun resolveTimedSet(workoutId: String, workoutExerciseId: String, setId: String): TimedSetNotificationContent? {
        val workoutExercise = workoutRepository.getExercisesForWorkout(workoutId).find { it.id == workoutExerciseId } ?: return null
        val name = exerciseRepository.getById(workoutExercise.exerciseId)?.name ?: return null
        val sets = workoutRepository.getSetsForWorkoutExercise(workoutExerciseId).sortedBy { it.orderIndex }
        val index = sets.indexOfFirst { it.id == setId }
        if (index < 0) return null
        return TimedSetNotificationContent(
            exerciseId = workoutExerciseId,
            setId = setId,
            title = "$name · set ${index + 1} of ${sets.size}",
            label = "$name · set ${index + 1}",
        )
    }

    private fun buildNotification(state: WorkoutSessionState): Notification {
        val content = state.notificationContent
        val timer = state.inlineTimer
        // While a set timer runs, the title follows the TIMED set. The VM names it in
        // [content.timedSet]; with no ViewModel alive (a timer restored after process death) it
        // falls back to the generic title, but the clock and Complete set still follow the timer.
        val timedSet = timer?.let { t -> content?.timedSet?.takeIf { it.setId == t.setId } ?: lastTimedSet?.takeIf { it.setId == t.setId } }
        val title = if (timer != null) timedSet?.title ?: getString(R.string.notification_workout_fallback_title) else content?.title ?: getString(R.string.notification_workout_fallback_title)
        val text = when {
            timer == null -> content?.text.orEmpty()
            timer.mode == TimerMode.COUNTDOWN && timer.targetSeconds != null -> getString(R.string.notification_timer_countdown_from, formatElapsedClock(timer.targetSeconds))
            else -> getString(R.string.notification_timer_stopwatch)
        }
        val builder = NotificationCompat.Builder(this, WorkoutNotificationChannels.WORKOUT_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_logez)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppPendingIntent())

        val restDeadline = state.restDeadlineElapsedRealtimeMillis
        val workoutId = state.workoutId
        when {
            // A running set timer wins: its clock counts down to the deadline (a countdown) or up
            // from its start (a stopwatch), and Complete set stops it and checks THAT set.
            timer != null -> {
                addTimerChronometer(builder, timer)
                if (workoutId != null) {
                    builder.addAction(0, getString(R.string.notification_action_complete_set), completeSetPendingIntent(workoutId, timer.exerciseId, timer.setId))
                }
            }
            // Actions are only offered once a session id exists to scope them to — without it a
            // tapped action could not be validated against the session it was built for.
            restDeadline != null -> {
                val wallClockDeadline = System.currentTimeMillis() + (restDeadline - elapsedRealtimeClock.elapsedRealtimeMillis())
                builder.setUsesChronometer(true).setChronometerCountDown(true).setWhen(wallClockDeadline)
                if (workoutId != null) {
                    builder.addAction(0, getString(R.string.notification_action_rest_minus_15), restAdjustPendingIntent(workoutId, -15))
                    builder.addAction(0, getString(R.string.notification_action_rest_plus_15), restAdjustPendingIntent(workoutId, 15))
                    builder.addAction(0, getString(R.string.notification_action_rest_skip), restSkipPendingIntent(workoutId))
                }
            }
            !state.isPaused -> {
                val effectiveStart = System.currentTimeMillis() - state.accumulatedActiveSeconds * 1000
                builder.setUsesChronometer(true).setChronometerCountDown(false).setWhen(effectiveStart)
                val exerciseId = content?.actionableExerciseId
                val setId = content?.actionableSetId
                if (workoutId != null && exerciseId != null && setId != null) {
                    builder.addAction(0, getString(R.string.notification_action_complete_set), completeSetPendingIntent(workoutId, exerciseId, setId))
                }
            }
            else -> builder.setUsesChronometer(false)
        }

        return builder.build()
    }

    private fun addTimerChronometer(builder: NotificationCompat.Builder, timer: InlineTimerState) {
        val now = elapsedRealtimeClock.elapsedRealtimeMillis()
        val wallNow = System.currentTimeMillis()
        val deadline = timer.deadlineElapsedRealtimeMillis
        if (deadline != null) {
            builder.setUsesChronometer(true).setChronometerCountDown(true).setWhen(wallNow + (deadline - now))
        } else {
            builder.setUsesChronometer(true).setChronometerCountDown(false).setWhen(wallNow - (now - timer.startElapsedRealtimeMillis))
        }
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(this, REQUEST_OPEN_APP, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun completeSetPendingIntent(workoutId: String, workoutExerciseId: String, setId: String, requestCode: Int = REQUEST_COMPLETE_SET): PendingIntent =
        servicePendingIntent(requestCode, ACTION_COMPLETE_SET, workoutId) {
            putExtra(EXTRA_WORKOUT_EXERCISE_ID, workoutExerciseId)
            putExtra(EXTRA_SET_ID, setId)
        }

    private fun restAdjustPendingIntent(workoutId: String, deltaSeconds: Int): PendingIntent =
        servicePendingIntent(if (deltaSeconds < 0) REQUEST_REST_MINUS else REQUEST_REST_PLUS, ACTION_REST_ADJUST, workoutId) {
            putExtra(EXTRA_DELTA_SECONDS, deltaSeconds)
        }

    private fun restSkipPendingIntent(workoutId: String): PendingIntent =
        servicePendingIntent(REQUEST_REST_SKIP, ACTION_REST_SKIP, workoutId) {}

    /** Every action intent is stamped with the workout it was built for — see [isForCurrentSession]. */
    private fun servicePendingIntent(requestCode: Int, action: String, workoutId: String, extras: Intent.() -> Unit): PendingIntent {
        val intent = Intent(this, WorkoutSessionService::class.java).setAction(action)
            .putExtra(EXTRA_WORKOUT_ID, workoutId)
            .apply(extras)
        return PendingIntent.getService(this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /**
     * A notification action the user tapped is already queued in the OS by the time we see it, so
     * `FLAG_UPDATE_CURRENT` can't retract it — if the workout was discarded-and-restarted in that
     * window, an unscoped action would silently apply to the *new* workout's timer. Comparing the
     * stamped id against the live session drops those stale actions instead.
     */
    private fun isForCurrentSession(intent: Intent): Boolean =
        intent.getStringExtra(EXTRA_WORKOUT_ID) == sessionController.state.value.workoutId

    private data class NotificationDisplayKey(
        val content: com.enil.logez.feature.workout.session.WorkoutNotificationContent?,
        val isPaused: Boolean,
        val isResting: Boolean,
        val accumulatedActiveSeconds: Long,
        val inlineTimer: InlineTimerState?,
    )

    companion object {
        const val ACTION_START = "com.enil.logez.action.WORKOUT_SESSION_START"
        const val ACTION_STOP = "com.enil.logez.action.WORKOUT_SESSION_STOP"
        const val ACTION_COMPLETE_SET = "com.enil.logez.action.WORKOUT_SESSION_COMPLETE_SET"
        const val ACTION_REST_ADJUST = "com.enil.logez.action.WORKOUT_SESSION_REST_ADJUST"
        const val ACTION_REST_SKIP = "com.enil.logez.action.WORKOUT_SESSION_REST_SKIP"

        const val EXTRA_WORKOUT_ID = "workoutId"
        const val EXTRA_WORKOUT_EXERCISE_ID = "workoutExerciseId"
        const val EXTRA_SET_ID = "setId"
        const val EXTRA_DELTA_SECONDS = "deltaSeconds"

        private const val TAG = "WorkoutSessionService"
        private val NOTIFICATION_ACTIONS = setOf(ACTION_COMPLETE_SET, ACTION_REST_ADJUST, ACTION_REST_SKIP)
        private const val NOTIFICATION_ID = 1001
        private const val REST_END_NOTIFICATION_ID = 1002
        private const val COUNTDOWN_DONE_NOTIFICATION_ID = 1003
        private const val REST_END_HEADS_UP_TIMEOUT_MS = 15_000L

        private const val REQUEST_OPEN_APP = 1
        private const val REQUEST_COMPLETE_SET = 2
        private const val REQUEST_REST_MINUS = 3
        private const val REQUEST_REST_PLUS = 4
        private const val REQUEST_REST_SKIP = 5
        private const val REQUEST_COUNTDOWN_COMPLETE_SET = 6
    }
}
