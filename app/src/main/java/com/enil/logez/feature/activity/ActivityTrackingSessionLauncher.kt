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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.enil.logez.R
import com.enil.logez.feature.activity.service.ActivityTrackingService

/**
 * M21a. Unlike [com.enil.logez.feature.workout.rememberStartWorkoutSession]'s notification
 * permission (optional — logging still works if denied), GPS tracking genuinely cannot function
 * without location, so a denial calls [onDenied] instead of proceeding anyway (rev. 3 plan §2,
 * "Runtime permission request pattern" — a deliberate deviation from the notification-permission
 * "proceed either way" precedent, not an oversight).
 */
@Composable
fun rememberRequestLocationForTracking(onGranted: () -> Unit, onDenied: () -> Unit): () -> Unit {
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) onGranted() else onDenied()
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false; onDenied() },
            title = { Text(stringResource(R.string.activity_tracking_location_rationale_title)) },
            text = { Text(stringResource(R.string.activity_tracking_location_rationale_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }) { Text(stringResource(R.string.activity_tracking_location_rationale_allow)) }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false; onDenied() }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            onGranted()
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
