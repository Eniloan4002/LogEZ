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

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob)
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
            else -> stopSelfCleanly() // no rehydration story in v1 — a bare/unrecognized redelivery just stops
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun stopSelfCleanly() {
        serviceJob.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureCollectorStarted() {
        if (collectorStarted) return
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
