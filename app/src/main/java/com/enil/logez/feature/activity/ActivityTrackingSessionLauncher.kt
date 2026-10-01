package com.enil.logez.feature.activity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.enil.logez.R
import com.enil.logez.feature.activity.service.ActivityTrackingService
import com.enil.logez.core.common.PermissionDenial
import com.enil.logez.core.common.classifyDenial
import com.enil.logez.feature.workout.NotificationPromptMemory
import com.enil.logez.feature.workout.shouldAskForNotifications
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * M21a. Unlike [com.enil.logez.feature.workout.rememberStartWorkoutSession]'s notification
 * permission (optional — logging still works if denied), GPS tracking genuinely cannot function
 * without location, so a denial calls [onDenied] instead of proceeding anyway (rev. 3 plan §2,
 * "Runtime permission request pattern" — a deliberate deviation from the notification-permission
 * "proceed either way" precedent, not an oversight).
 *
 * First-run plan O1h: once location is granted, and before tracking starts, Android 13+ asks once
 * whether to show the walk or run on the lock screen. Without it a user who only runs never saw
 * the tracking notification. The ask shares [NotificationPromptMemory] with the strength prompt,
 * so a "Not now" or a denial on either path stops both, and [onGranted] runs whatever the answer:
 * the notification is optional, location is not.
 *
 * The returned function takes the walk or run to start, and it is kept here as saved state, so a
 * rotation while a prompt is open still starts that walk or run once the prompt is answered.
 *
 * The ask comes before [onGranted], so it comes before the caller's check for a workout already in
 * progress: a user can answer it and then choose to resume that workout instead. The answer is
 * still about notifications, not about this walk, so it is kept either way.
 */
@Composable
fun rememberRequestLocationForTracking(
    onGranted: (exerciseId: String, title: String) -> Unit,
    onDenied: (PermissionDenial) -> Unit,
): (exerciseId: String, title: String) -> Unit {
    val context = LocalContext.current
    var pendingExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var showRationale by rememberSaveable { mutableStateOf(false) }
    var showNotificationAsk by rememberSaveable { mutableStateOf(false) }

    fun startPending() {
        val exerciseId = pendingExerciseId
        val title = pendingTitle
        pendingExerciseId = null
        pendingTitle = null
        if (exerciseId != null && title != null) onGranted(exerciseId, title)
    }

    fun locationGranted() {
        if (shouldAskForNotifications(context)) showNotificationAsk = true else startPending()
    }

    fun locationDenied(denial: PermissionDenial) {
        pendingExerciseId = null
        pendingTitle = null
        onDenied(denial)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        when {
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true -> locationGranted()
            // Android 12+ lets the user grant only approximate location, which cannot measure a
            // route. It used to read as a plain denial with no hint that "Precise" was the fix.
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> locationDenied(PermissionDenial.ApproximateOnly)
            else -> locationDenied(classifyDenial(context, Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) NotificationPromptMemory.markDeclined(context)
        startPending()
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false; locationDenied(PermissionDenial.Declined) },
            title = { Text(stringResource(R.string.activity_tracking_location_rationale_title)) },
            text = { Text(stringResource(R.string.activity_tracking_location_rationale_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }) { Text(stringResource(R.string.activity_tracking_location_rationale_allow)) }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false; locationDenied(PermissionDenial.Declined) }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    fun notificationNotNow() {
        showNotificationAsk = false
        NotificationPromptMemory.markDeclined(context)
        startPending()
    }

    if (showNotificationAsk) {
        AlertDialog(
            // Back or a tap outside counts as "Not now", so the ask really comes once (Decision 9).
            // The strength prompt still records nothing on a dismissal (plan finding 8); O1h leaves
            // it unchanged. The walk or run has already been chosen, so it starts either way.
            onDismissRequest = { notificationNotNow() },
            title = { Text(stringResource(R.string.tracking_notification_permission_title)) },
            text = { Text(stringResource(R.string.tracking_notification_permission_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showNotificationAsk = false
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text(stringResource(R.string.workout_notification_permission_allow)) }
            },
            dismissButton = {
                TextButton(onClick = { notificationNotNow() }) {
                    Text(stringResource(R.string.workout_notification_permission_not_now))
                }
            },
        )
    }

    return { exerciseId, title ->
        pendingExerciseId = exerciseId
        pendingTitle = title
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationGranted()
        } else {
            showRationale = true
        }
    }
}

fun startActivityTrackingService(context: Context) {
    val intent = Intent(context, ActivityTrackingService::class.java).setAction(ActivityTrackingService.ACTION_START)
    ContextCompat.startForegroundService(context, intent)
}

/** Called from the live-tracking screen on Finish/Cancel — the one explicit, deterministic stop path. */
fun stopActivityTrackingService(context: Context) {
    val intent = Intent(context, ActivityTrackingService::class.java).setAction(ActivityTrackingService.ACTION_STOP)
    context.startService(intent)
}
