package com.enil.logez.feature.activity

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * M21 redesign (2026-09-11, decisions.md same date): Finish goes straight to the Save Workout
 * screen (`onFinished`), never through the strength Logger — a GPS-tracked walk/run has nothing
 * for the Logger's sets/reps table to show, and routing through it just added a screen and a
 * detour into `WorkoutSessionService` (the strength-session foreground service, which the
 * Logger-hand-off path used to start via `rememberStartWorkoutSession`) that this flow no longer
 * needs. `ActivityTrackingService` is the only foreground service this screen ever runs, stopped
 * right below on both Finish and Cancel.
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

    fun finish() = scope.launch {
        val result = viewModel.finish()
        stopActivityTrackingService(context)
        if (result != null) onFinished(result.workoutId) else onCancelled()
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Timer, distance and (when available) BPM share one row (Owner request, 2026-09-11)
            // rather than stacking above/below the map -- each stat is its own centered column so
            // the row reads the same as a two- or three-up stat card. BPM's own column is omitted
            // entirely, not shown empty, whenever Health Connect has nothing to show (not connected,
            // no permission, no wearable data) -- same graceful-degrade rule as the Profile wellness
            // card and the Logger's HeartRateChip.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatElapsed(elapsedSeconds), style = MaterialTheme.typography.displayMedium)
                    Text(
                        stringResource(R.string.activity_tracking_elapsed_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.activity_tracking_distance_value, formatKm(state.distanceMeters)),
                        style = MaterialTheme.typography.displayMedium,
                    )
                    Text(
                        stringResource(R.string.activity_tracking_distance_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (liveBpm != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.workout_bpm_value, liveBpm!!),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            stringResource(R.string.activity_tracking_bpm_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // M21c: the real offline map, camera following the newest GPS fix as it arrives --
            // upgraded from the framework-free Canvas sketch (RouteSketchGeometry) once that spike
            // proved the concept, per the Owner's explicit choice (P-125/decisions.md 2026-09-10).
            // Fills all remaining vertical space (Owner request, 2026-09-11) rather than a fixed
            // 220dp box -- the live tracking screen is map-first now; the Finish-summary and History
            // Detail Route cards keep their own fixed, smaller aspect-ratio sizing untouched.
            OfflineMapView(
                routePoints = state.routePoints,
                followLatest = true,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(top = Spacing.lg).clip(RoundedCornerShape(Radius.sm)),
            )

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
