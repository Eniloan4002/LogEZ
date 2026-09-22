package com.enil.logez.feature.activity.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.enil.logez.MainActivity
import com.enil.logez.R
import com.enil.logez.feature.activity.ActivityTrackingController
import com.enil.logez.feature.activity.ActivityTrackingState
import com.enil.logez.feature.workout.service.WorkoutNotificationChannels
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * M21a. Thin shell mirroring [com.enil.logez.feature.workout.service.WorkoutSessionService]'s
 * split of concerns: [ActivityTrackingController] is the testable state machine, this class only
 * wires it to the platform APIs a Service uniquely has access to (the foreground notification).
 *
 * v1 scope, deliberately: starting/finishing tracking is driven by the caller (the live-tracking
 * ViewModel calls the controller directly, then starts/stops this service to own the FGS
 * lifecycle) — this service does not itself call `startTracking`/`finishTracking`, and unlike
 * `WorkoutSessionService` it does not attempt process-death rehydration (a known, accepted v1
 * limitation for GPS tracking specifically — rev. 3 plan §2.2).
 */
@AndroidEntryPoint
class ActivityTrackingService : Service() {
    @Inject lateinit var controller: ActivityTrackingController

    // `var`, not `val`: stopSelfCleanly() cancels the job, and a cancelled SupervisorJob can never
    // run anything again, so a reused Service instance has to rebuild both. All three are touched
    // only from onStartCommand/onTaskRemoved/onDestroy, which are main-thread lifecycle callbacks,
    // so they need no synchronization.
    private var serviceJob = SupervisorJob()
    private var serviceScope = CoroutineScope(serviceJob)
    private var collectorStarted = false

    // The one-shot stop-watcher armed below, kept separately from [collectorStarted] and
    // re-armed on every onStartCommand -- see [ensureCollectorStarted]'s doc comment for why a
    // stale watcher left over from a discarded session is a real hazard, not a hypothetical one.
    private var stopWatcherJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        WorkoutNotificationChannels.ensureCreated(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(buildNotification(controller.state.value))
        ensureCollectorStarted()

        when (intent?.action) {
            ACTION_STOP -> {
                stopSelfCleanly()
                return START_NOT_STICKY
            }
            ACTION_START -> Unit // tracking already started by the caller before startForegroundService()
            else -> {
                // No rehydration story for GPS — a bare/unrecognized redelivery just stops, and
                // must not then ask the framework to start it again.
                stopSelfCleanly()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    /**
     * The user swiped the task away. Without this the service survives it: being a foreground
     * service it keeps the process alive, while its only two stop call sites live on the
     * live-tracking screen, which is now unreachable — so it would hold GPS until reboot.
     *
     * `cancelTracking()` is the call that actually stops the GPS: `fixJob` runs on the
     * controller's application scope, not [serviceScope], so cancelling the service job alone
     * would leave the location collector running after the service is gone.
     *
     * Deliberately asymmetric with `WorkoutSessionService`, which has no `onTaskRemoved` and must
     * not gain one: a typed session is recoverable, and surviving task removal is its whole
     * recovery story. A GPS session is not recoverable, so it should stop cleanly and let the
     * interrupted-run dialog decide what happens to the row.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        controller.cancelTracking()
        stopSelfCleanly()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun stopSelfCleanly() {
        collectorStarted = false
        stopWatcherJob = null
        serviceJob.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Called from every onStartCommand, not just the first: `startActivityTrackingService` is
     * re-sent whenever a resume dialog's "Discard" choice immediately starts a *different*
     * exercise's GPS session (`WorkoutTabViewModel.discardInProgressAndStartActivityTracking`),
     * and Android redelivers that to this same Service instance rather than creating a new one.
     * `collectorStarted` still guards the distance/notification collector -- restarting that is
     * harmless but pointless -- but the one-shot stop-watcher below cannot share that guard: it
     * is *watching for* the discard's `cancelTracking()` to flip `isTracking` false, and that
     * happens on the very session boundary this method is being re-entered for. Left armed, the
     * stale watcher fires on that transient false, tears the service down via `stopSelfCleanly()`
     * sometime after the new session has already flipped `isTracking` back to true, and the new
     * caller -- whose own `onStartCommand` no-opped on `collectorStarted` -- has no watcher armed
     * to notice it just lost its foreground service. Re-arming it every call closes that: the
     * fresh collection only starts once this session's state is already `isTracking == true`
     * (`discardInProgressAndStartActivityTracking` always calls `startTracking` before this
     * service is (re)started), so it can only ever fire on a real, later stop.
     */
    private fun ensureCollectorStarted() {
        // A cancelled SupervisorJob stays cancelled, so every later launch on it silently does
        // nothing. Android can hand a fresh start to a Service instance whose stopSelf() is still
        // pending, so the scope has to be rebuilt rather than just re-flagged.
        if (!serviceJob.isActive) {
            serviceJob = SupervisorJob()
            serviceScope = CoroutineScope(serviceJob)
        }

        if (!collectorStarted) {
            collectorStarted = true
            serviceScope.launch {
                controller.state.map { it.distanceMeters }.distinctUntilChanged()
                    .collect { postNotification(buildNotification(controller.state.value)) }
            }
        }

        // Stop with the session, not only with the screen. Every path that ends tracking --
        // finish, cancel, and discarding the workout from any resume dialog -- clears the
        // controller, while the only two explicit stop calls live on one screen the user may no
        // longer be able to reach. Cancel any watcher armed for a now-superseded session first.
        stopWatcherJob?.cancel()
        stopWatcherJob = serviceScope.launch {
            controller.state.map { it.isTracking }.distinctUntilChanged().filter { !it }.first()
            withContext(Dispatchers.Main.immediate) { stopSelfCleanly() }
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("MissingPermission") // notify() safely no-ops if POST_NOTIFICATIONS was denied — the FGS itself keeps running either way
    private fun postNotification(notification: Notification) {
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(state: ActivityTrackingState): Notification {
        val km = state.distanceMeters / 1000.0
        val text = getString(R.string.activity_tracking_notification_text, formatKm(km))
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this, REQUEST_OPEN_APP, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, WorkoutNotificationChannels.WORKOUT_ONGOING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.activity_tracking_notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis() - controller.elapsedSeconds() * 1000L)
            .setContentIntent(contentIntent)
            .build()
    }

    // Locale.ROOT: the default-locale overload renders "1,20" on comma-decimal devices.
    private fun formatKm(km: Double): String = "%.2f".format(Locale.ROOT, (km * 100).roundToInt() / 100.0)

    companion object {
        const val ACTION_START = "com.enil.logez.action.ACTIVITY_TRACKING_START"
        const val ACTION_STOP = "com.enil.logez.action.ACTIVITY_TRACKING_STOP"

        private const val NOTIFICATION_ID = 2001
        private const val REQUEST_OPEN_APP = 101
    }
}
