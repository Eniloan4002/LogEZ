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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
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
        serviceJob.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureCollectorStarted() {
        if (collectorStarted) return
        // A cancelled SupervisorJob stays cancelled, so every later launch on it silently does
        // nothing. Android can hand a fresh start to a Service instance whose stopSelf() is still
        // pending, so the scope has to be rebuilt rather than just re-flagged.
        if (!serviceJob.isActive) {
            serviceJob = SupervisorJob()
            serviceScope = CoroutineScope(serviceJob)
        }
        collectorStarted = true
        serviceScope.launch {
            controller.state.map { it.distanceMeters }.distinctUntilChanged()
                .collect { postNotification(buildNotification(controller.state.value)) }
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
