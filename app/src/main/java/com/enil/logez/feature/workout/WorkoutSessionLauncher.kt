package com.enil.logez.feature.workout

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.enil.logez.R
import com.enil.logez.feature.workout.service.WorkoutSessionService

/**
 * PHASE2_PLAN.md §9.3 — "requested contextually on the first Start Workout tap". Returns a
 * function that, given a freshly-started workoutId (the caller has already called
 * `WorkoutSessionController.startSession` and `WorkoutStarter`), requests POST_NOTIFICATIONS if
 * needed, starts `WorkoutSessionService`, then navigates — "never blocks logging": navigation
 * happens either way, notification permission only gates whether the ongoing notification is visible.
 */
@Composable
fun rememberStartWorkoutSession(onNavigateToLogger: (workoutId: String) -> Unit): (String) -> Unit {
    val context = LocalContext.current
    var pendingWorkoutId by remember { mutableStateOf<String?>(null) }
    var showRationale by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingWorkoutId?.let { launchServiceAndNavigate(context, it, onNavigateToLogger) }
        pendingWorkoutId = null
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = {
                showRationale = false
                pendingWorkoutId?.let { launchServiceAndNavigate(context, it, onNavigateToLogger) }
                pendingWorkoutId = null
            },
            title = { Text(stringResource(R.string.workout_notification_permission_title)) },
            text = { Text(stringResource(R.string.workout_notification_permission_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text(stringResource(R.string.workout_notification_permission_allow)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRationale = false
                    pendingWorkoutId?.let { launchServiceAndNavigate(context, it, onNavigateToLogger) }
                    pendingWorkoutId = null
                }) { Text(stringResource(R.string.workout_notification_permission_not_now)) }
            },
        )
    }

    return { workoutId ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingWorkoutId = workoutId
            showRationale = true
        } else {
            launchServiceAndNavigate(context, workoutId, onNavigateToLogger)
        }
    }
}

private fun launchServiceAndNavigate(context: Context, workoutId: String, onNavigateToLogger: (String) -> Unit) {
    val intent = Intent(context, WorkoutSessionService::class.java).setAction(WorkoutSessionService.ACTION_START)
    ContextCompat.startForegroundService(context, intent)
    onNavigateToLogger(workoutId)
}

/** Called from the Logger screen on Finish/Discard — the one explicit, deterministic stop path (research recommendation: don't rely on the Service noticing idle state). */
fun stopWorkoutSessionService(context: Context) {
    val intent = Intent(context, WorkoutSessionService::class.java).setAction(WorkoutSessionService.ACTION_STOP)
    context.startService(intent)
}
