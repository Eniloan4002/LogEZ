package com.enil.logez.feature.activity

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.Radius
import com.enil.logez.core.designsystem.ScreenTitle
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.feature.activity.map.OfflineMapView
import com.enil.logez.feature.workout.rememberStartWorkoutSession
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * M21a. `onFinished` is what actually enters the Logger — routed through
 * [rememberStartWorkoutSession] (not called directly) so that landing there starts
 * `WorkoutSessionService` exactly the same way every other "enter the Logger" path does (its own
 * notification-permission dialog included). Only one foreground service runs at a time:
 * `ActivityTrackingService` here, handed off to `WorkoutSessionService` at Finish.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityTrackingScreen(
    onFinished: (workoutId: String) -> Unit,
    onCancelled: () -> Unit,
    viewModel: ActivityTrackingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val elapsedSeconds by viewModel.elapsedSecondsFlow.collectAsStateWithLifecycle(initialValue = 0)
    val liveBpm by viewModel.liveBpmFlow.collectAsStateWithLifecycle(initialValue = null)
    var showCancelConfirm by remember { mutableStateOf(false) }
    val enterLogger = rememberStartWorkoutSession(onFinished)

    fun finish() = scope.launch {
        val result = viewModel.finish()
        stopActivityTrackingService(context)
        if (result != null) enterLogger(result.workoutId) else onCancelled()
    }

    fun cancel() = scope.launch {
        viewModel.cancel()
        stopActivityTrackingService(context)
        onCancelled()
    }

    BackHandler { showCancelConfirm = true }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { ScreenTitle(stringResource(R.string.activity_tracking_screen_title)) },
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.lg),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(formatElapsed(elapsedSeconds), style = MaterialTheme.typography.displayLarge)
            Text(
                stringResource(R.string.activity_tracking_elapsed_label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // M21c: the real offline map, camera following the newest GPS fix as it arrives --
            // upgraded from the framework-free Canvas sketch (RouteSketchGeometry) once that spike
            // proved the concept, per the Owner's explicit choice (P-125/decisions.md 2026-09-10).
            OfflineMapView(
                routePoints = state.routePoints,
                followLatest = true,
                modifier = Modifier.fillMaxWidth().height(220.dp).padding(top = Spacing.lg).clip(RoundedCornerShape(Radius.sm)),
            )

            Text(
                stringResource(R.string.activity_tracking_distance_value, formatKm(state.distanceMeters)),
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(top = Spacing.xl),
            )
            Text(
                stringResource(R.string.activity_tracking_distance_label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // M21f: absent whenever Health Connect has nothing to show (not connected, no
            // permission, no wearable data) -- graceful degrade, same rule as the Profile wellness
            // card and the Logger's HeartRateChip.
            if (liveBpm != null) {
                Text(
                    stringResource(R.string.workout_bpm_value, liveBpm!!),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Spacing.md),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xxl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                OutlinedButton(onClick = { showCancelConfirm = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.activity_tracking_cancel))
                }
                Button(onClick = ::finish, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.activity_tracking_finish))
                }
            }
        }
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text(stringResource(R.string.activity_tracking_cancel_confirm_title)) },
            text = { Text(stringResource(R.string.activity_tracking_cancel_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { showCancelConfirm = false; cancel() }) {
                    Text(stringResource(R.string.activity_tracking_cancel_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private fun formatElapsed(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s) else "%d:%02d".format(Locale.ROOT, m, s)
}

// Locale.ROOT: the default-locale overload renders "1,20" on comma-decimal devices. Returns just
// the number -- the "km" unit lives in R.string.activity_tracking_distance_value, not hardcoded here.
private fun formatKm(distanceMeters: Double): String {
    val km = distanceMeters / 1000.0
    return "%.2f".format(Locale.ROOT, (km * 100).roundToInt() / 100.0)
}
