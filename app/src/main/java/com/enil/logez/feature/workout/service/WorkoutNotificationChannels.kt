package com.enil.logez.feature.workout.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.enil.logez.R

/**
 * PHASE2_PLAN.md §9.3 — created once at app start (idempotent — `createNotificationChannels` is a
 * no-op for channels that already exist). All three are deliberately silent at the channel level:
 * Hevy's independent Timer/Set-Complete/PR volume settings are incompatible with system-managed
 * channel sounds, so audio is app-rendered via [com.enil.logez.feature.workout.audio.WorkoutAudioPlayer] instead.
 */
object WorkoutNotificationChannels {
    const val WORKOUT_ONGOING = "workout_ongoing"
    const val REST_TIMER = "rest_timer"
    const val PR_ALERTS = "pr_alerts"

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val ongoing = NotificationChannel(
            WORKOUT_ONGOING,
            context.getString(R.string.notification_channel_workout_ongoing_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_workout_ongoing_description)
            setSound(null, null)
            setShowBadge(false)
        }
        val restTimer = NotificationChannel(
            REST_TIMER,
            context.getString(R.string.notification_channel_rest_timer_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_rest_timer_description)
            setSound(null, null)
            enableVibration(true)
        }
        val prAlerts = NotificationChannel(
            PR_ALERTS,
            context.getString(R.string.notification_channel_pr_alerts_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_pr_alerts_description)
            setSound(null, null)
        }
        manager.createNotificationChannels(listOf(ongoing, restTimer, prAlerts))
    }
}
