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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.ConfirmDialog
import com.enil.logez.core.designsystem.LineChart
import com.enil.logez.core.designsystem.LineChartPoint
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.Radius
import com.enil.logez.core.designsystem.ScreenTitle
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.formatMmSs
import com.enil.logez.core.designsystem.formatPace
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import com.enil.logez.core.domain.calc.HeartRateZone
import com.enil.logez.core.domain.calc.HeartRateZoneCalculator
import com.enil.logez.core.domain.calc.PaceCalculator
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.wellness.HeartRateSample
import com.enil.logez.feature.activity.map.MapTilerView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.flowOf
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
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showCancelConfirm by remember { mutableStateOf(false) }

    val paceSecondsPerUnit = PaceCalculator.paceSecondsPerUnit(state.distanceMeters, elapsedSeconds, settings.distanceUnit)
    val heartRateZone = liveBpm?.let { HeartRateZoneCalculator.zoneFor(it.bpm, settings.maxHeartRateBpm) }

    // The vitals card's two historical charts. Heart-rate history is Health-Connect-sourced (re-
    // queried each poll, see liveHeartRateHistoryFlow's own doc comment); pace history is derived
    // from the controller's own periodic distance samples, one PaceCalculator call per consecutive
    // pair rather than the cumulative total -- a long run's lifetime-average pace barely moves, so
    // only a recent-window delta shows an actually useful trend on the chart.
    val startedAtMillis = state.startedAtMillis
    val heartRateHistory: List<HeartRateSample> by remember(startedAtMillis) {
        startedAtMillis?.let(viewModel::heartRateHistoryFlow) ?: flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val heartRateChartPoints = heartRateHistory.map { LineChartPoint(x = it.time.toEpochMilli(), y = it.bpm.toDouble()) }
    val paceChartPoints = state.distanceHistory.zipWithNext { (t1, d1), (t2, d2) ->
        PaceCalculator.windowedPaceSecondsPerUnit(d2 - d1, ((t2 - t1) / 1000).toInt(), settings.distanceUnit)?.let { LineChartPoint(x = t2, y = it) }
    }.filterNotNull()
    val showVitalsCard = liveBpm != null || heartRateChartPoints.isNotEmpty() || paceChartPoints.isNotEmpty()
    // Keyed on the point list, not a bare `remember {}`: liveHeartRateHistoryFlow re-queries the
    // whole window every poll specifically so a delayed Health Connect sync can backfill an
    // earlier sample into the middle of the list, which shifts every later sample's index -- the
    // same stale-index-into-a-reshuffled-list hazard ExerciseDetailScreen's own selectedPointIndex
    // already guards against (`remember(summary.points) {...}`). A structurally-equal list on a
    // repeat poll (the common case) compares equal, so the selection only actually resets when the
    // content genuinely changes, not on every tick.
    var selectedHrIndex by rememberSaveable(heartRateChartPoints) { mutableStateOf<Int?>(null) }
    var selectedPaceIndex by rememberSaveable(paceChartPoints) { mutableStateOf<Int?>(null) }
    fun elapsedLabel(epochMillis: Long): String =
        formatMmSs((((epochMillis - (startedAtMillis ?: epochMillis)) / 1000).coerceAtLeast(0L)).toInt())

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
                colors = logEzTopAppBarColors(),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Timer, distance and pace share one row (Owner request, 2026-09-11 extended
            // 2026-09-12 with pace) rather than stacking above/below the map -- each stat is its
            // own centered column so the row reads the same as a two- or three-up stat card.
            // Pace's own column is omitted entirely, not shown as "--:--", before enough distance
            // has accumulated to mean anything (PaceCalculator.MIN_METERS_FOR_PACE) -- same
            // honest-absence rule the BPM/zone row below already followed.
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
                if (paceSecondsPerUnit != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(formatPace(paceSecondsPerUnit), style = MaterialTheme.typography.displayMedium)
                        Text(
                            stringResource(
                                if (settings.distanceUnit == DistanceUnit.MILES) R.string.activity_tracking_pace_label_mi else R.string.activity_tracking_pace_label_km,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // M21c: the real offline map, camera following the newest GPS fix as it arrives --
            // upgraded from the framework-free Canvas sketch (RouteSketchGeometry) once that spike
            // proved the concept, per the Owner's explicit choice (P-125/decisions.md 2026-09-10).
            // Originally filled all remaining vertical space (Owner request, 2026-09-11); revised
            // 2026-09-22 to a tall fixed height instead, since the vitals card below now needs
            // that space too. The Finish-summary and History Detail Route cards keep their own
            // fixed, smaller aspect-ratio sizing, untouched by this.
            //
            // Deliberately NOT wrapped in a shared verticalScroll with the card below: MapTilerView
            // is a native AndroidView with its own pan/zoom/scroll gestures fully enabled (a prior,
            // deliberate fix for a "can't move the map" report), and an embedded platform View's
            // touch handling claims the gesture stream ahead of an ancestor Compose scroll with no
            // nested-scroll bridge in this codebase to hand it back -- a drag starting on the map
            // would always pan it, never scroll the page, if the two shared one scrollable ancestor
            // (adversarial review, 2026-09-22). Giving the map a fixed, non-scrolling slot and the
            // card below it its own independent scroll region keeps both gestures unambiguous: a
            // drag on the map always pans the map, a drag on the card always scrolls the card.
            Column(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = Spacing.lg)) {
                MapTilerView(
                    routePoints = state.routePoints,
                    followLatest = true,
                    modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(Radius.sm)),
                )

                // BPM, its live zone, and their two historical charts, all one card below the map,
                // scrolling within its own bounded space rather than sharing the map's ancestor --
                // see the comment above. Omitted entirely (not shown empty/dashed), same graceful-
                // degrade rule as the Profile wellness card and the Logger's HeartRateChip,
                // whenever there's genuinely nothing yet to show (Health Connect has no BPM and
                // not enough distance/time has passed for either chart to have a point).
                if (showVitalsCard) {
                    LogEzCard(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = Spacing.lg)) {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(Spacing.md)) {
                            Text(stringResource(R.string.activity_tracking_vitals_header), style = MaterialTheme.typography.titleMedium)

                            // The zone label needs its own further condition -- a max heart rate
                            // set in Settings -- so BPM alone (no zone) is a real, common state
                            // too, not a bug.
                            if (liveBpm != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            stringResource(R.string.workout_bpm_value, liveBpm!!.bpm),
                                            style = MaterialTheme.typography.displayMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            stringResource(R.string.activity_tracking_bpm_label),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        // Owner-reported 2026-09-12: a wearable's readings reach
                                        // Health Connect through a multi-hop sync (watch -> its
                                        // companion app -> Health Connect), not in real time, so
                                        // this can genuinely be several minutes old even while the
                                        // watch face itself shows something fresher. Labeling it
                                        // honestly beats implying live-instant accuracy it can't
                                        // actually guarantee.
                                        Text(
                                            stringResource(R.string.activity_tracking_bpm_as_of, formatClockTime(liveBpm!!.time)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (heartRateZone != null) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                stringResource(R.string.activity_tracking_zone_value, heartRateZone.number),
                                                style = MaterialTheme.typography.displayMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Text(
                                                heartRateZoneLabel(heartRateZone),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }

                            if (heartRateChartPoints.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.activity_tracking_heart_rate_chart_header),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(top = Spacing.md),
                                )
                                LineChart(
                                    points = heartRateChartPoints,
                                    yLabel = { "${it.toInt()}" },
                                    xLabel = ::elapsedLabel,
                                    selectedIndex = selectedHrIndex,
                                    onPointTap = { selectedHrIndex = it },
                                    modifier = Modifier.padding(top = Spacing.sm),
                                )
                            }

                            if (paceChartPoints.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.activity_tracking_pace_chart_header),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(top = Spacing.md),
                                )
                                LineChart(
                                    points = paceChartPoints,
                                    yLabel = { formatPace(it) },
                                    xLabel = ::elapsedLabel,
                                    selectedIndex = selectedPaceIndex,
                                    onPointTap = { selectedPaceIndex = it },
                                    modifier = Modifier.padding(top = Spacing.sm),
                                )
                            }
                        }
                    }
                }
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
        ConfirmDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = stringResource(R.string.activity_tracking_cancel_confirm_title),
            body = stringResource(R.string.activity_tracking_cancel_confirm_body),
            confirmLabel = stringResource(R.string.activity_tracking_cancel_confirm_action),
            onConfirm = ::cancel,
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }
}

@Composable
private fun heartRateZoneLabel(zone: HeartRateZone): String = stringResource(
    when (zone) {
        HeartRateZone.ZONE_1 -> R.string.activity_tracking_zone_1
        HeartRateZone.ZONE_2 -> R.string.activity_tracking_zone_2
        HeartRateZone.ZONE_3 -> R.string.activity_tracking_zone_3
        HeartRateZone.ZONE_4 -> R.string.activity_tracking_zone_4
        HeartRateZone.ZONE_5 -> R.string.activity_tracking_zone_5
    },
)

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

/** "7:44 PM" -- same `h:mm a` clock-time convention `HistoryScreen.formatCardDateTime` uses for its own time-of-day portion. */
private fun formatClockTime(instant: Instant): String =
    instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a"))
