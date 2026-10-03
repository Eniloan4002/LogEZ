package com.enil.logez.feature.activity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.LineChart
import com.enil.logez.core.designsystem.LineChartPoint
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.MetricChip
import com.enil.logez.core.designsystem.Radius
import com.enil.logez.core.designsystem.ScreenTitle
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.formatElapsedClock
import com.enil.logez.core.designsystem.formatPace
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import com.enil.logez.core.designsystem.rememberClockTimeFormatter
import com.enil.logez.core.domain.calc.DistanceDisplay
import com.enil.logez.core.domain.calc.HeartRateZoneCalculator
import com.enil.logez.core.domain.calc.PaceCalculator
import com.enil.logez.core.domain.calc.PauseRanges
import com.enil.logez.core.domain.calc.RouteSplitsCalculator
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.GpsActivity
import com.enil.logez.core.wellness.HealthDataType
import com.enil.logez.core.wellness.HeartRateAccess
import com.enil.logez.core.wellness.HeartRateSample
import com.enil.logez.core.wellness.openHealthConnectInPlayStore
import com.enil.logez.core.wellness.openHealthConnectSettings
import com.enil.logez.core.wellness.rememberRequestHealthPermissions
import com.enil.logez.feature.activity.map.RouteMapView
import com.enil.logez.feature.workout.finish.CapsLabel
import com.enil.logez.feature.workout.finish.CardHeadingRow
import com.enil.logez.feature.workout.finish.PaceCardContent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

/**
 * The screen you see while a GPS walk or run records (Owner-approved mockups, 2026-10-01).
 *
 * - **Stats first, Map behind a chip** (decision 3; this reverses the map-as-main-view the Owner asked
 *   for on 2026-09-11). Taking the map out of the scrolling stats also removes the map-versus-scroll
 *   gesture conflict the old layout had to work around.
 * - **Back hides, tracking carries on** (decision 2): there is no BackHandler here, so Back is the
 *   navigation's own pop, the same as the down arrow, and the mini-bar brings the screen back. Nothing
 *   on this screen ends a run by accident.
 * - **Two-step end** (decision 1): while moving there is only Pause. Once paused, Resume takes Pause's
 *   exact place, Finish sits above it and ignores taps for [FINISH_GUARD_MILLIS], and Discard asks first.
 * - Heart rate with nothing to show is one line, not a card of dashes (decision 6; this reverses the
 *   Owner's 2026-09-23 ask to keep every section visible).
 *
 * Finish goes straight to the Save Workout screen (`onFinished`), never through the strength Logger
 * (M21 redesign, 2026-09-11). `ActivityTrackingService` is the only foreground service this screen
 * runs, stopped on Finish and on Discard.
 */
@Composable
fun ActivityTrackingScreen(
    onFinished: (workoutId: String) -> Unit,
    onHide: () -> Unit,
    onCancelled: () -> Unit,
    viewModel: ActivityTrackingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val liveStats by viewModel.liveStats.collectAsStateWithLifecycle(initialValue = null)
    val ending by viewModel.ending.collectAsStateWithLifecycle()
    val unit = settings.distanceUnit
    // Read inside the leaf composables only, so the once-a-second tick recomposes the digits and
    // not the whole scrolling column with its charts. Until the first tick lands it is derived from
    // the controller state, so the screen never opens on zeros.
    val stats: () -> LiveTrackingStats = { liveStats ?: state.liveStats(unit, System.currentTimeMillis()) }

    // Saved, so a recreation (rotation, font scale) does not blank the title while the read repeats.
    var activity by rememberSaveable { mutableStateOf<GpsActivity?>(null) }
    LaunchedEffect(state.workoutId) { state.workoutId?.let { activity = viewModel.activityFor(it) } }
    val noun = activityNoun(activity)

    // How the run ended arrives from the ViewModel, which finishes the work whatever happens to this
    // composition. Held until collected, so an end that lands mid-recreation still navigates.
    val onFinishedNow by rememberUpdatedState(onFinished)
    val onCancelledNow by rememberUpdatedState(onCancelled)
    LaunchedEffect(viewModel) {
        viewModel.ended.collect { end ->
            stopActivityTrackingService(context)
            when (end) {
                is TrackingEnd.Finished -> onFinishedNow(end.workoutId)
                TrackingEnd.Cancelled -> onCancelledNow()
            }
        }
    }

    // Asks for heart rate alone. If Health Connect answers without heart rate (it was refused
    // before, so no dialog showed), the button switches to opening Health Connect's settings,
    // where it can still be turned on; otherwise the button could silently do nothing. All of it
    // lives here, above the Stats/Map switch, so switching views does not forget a refusal or make
    // the heart-rate card vanish and pop back in.
    val heartRateAccess by viewModel.heartRateAccessFlow.collectAsStateWithLifecycle(initialValue = null)
    val heartRatePermission = remember { viewModel.healthMetricsSource.permissionFor(HealthDataType.HEART_RATE) }
    var heartRateRequestRefused by rememberSaveable { mutableStateOf(false) }
    val requestHeartRate = rememberRequestHealthPermissions(setOf(heartRatePermission)) { granted ->
        val allowed = heartRatePermission in granted
        heartRateRequestRefused = !allowed
        viewModel.onHeartRatePermissionResult(allowed)
    }
    // Heart-rate history is Health-Connect-sourced (re-queried each poll, see liveHeartRateHistoryFlow).
    val startedAtMillis = state.startedAtMillis
    val heartRateHistory: List<HeartRateSample> by remember(startedAtMillis) {
        startedAtMillis?.let(viewModel::heartRateHistoryFlow) ?: flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    ActivityTrackingContent(
        state = state,
        unit = unit,
        maxHeartRateBpm = settings.maxHeartRateBpm,
        stats = stats,
        activity = activity,
        ending = ending,
        heartRate = TrackingHeartRateInputs(
            access = heartRateAccess,
            history = heartRateHistory,
            requestRefused = heartRateRequestRefused,
            onAllow = { if (heartRateRequestRefused) openHealthConnectSettings(context) else requestHeartRate() },
            onOpenSettings = { openHealthConnectSettings(context) },
            onGetHealthConnect = { openHealthConnectInPlayStore(context) },
        ),
        onPause = viewModel::onPause,
        onResume = viewModel::onResume,
        onFinish = viewModel::onFinish,
        onDiscard = viewModel::onDiscard,
        onHide = onHide,
        routeMap = { modifier ->
            val breaks = remember(state.workoutId, state.routeTimes.size, state.pauseRanges) {
                PauseRanges.breakIndices(state.routeTimes, state.pauseRanges)
            }
            RouteMapView(
                routePoints = state.routePoints,
                routeBreaks = breaks,
                followLatest = true,
                showCurrentPosition = true,
                currentPosition = state.currentPosition,
                loadErrorText = stringResource(R.string.activity_tracking_map_error, noun),
                loadingLabel = stringResource(R.string.activity_tracking_map_loading),
                // The credit stays visible without a tap (OpenStreetMap requires it), inset from the corner.
                attributionPadding = PaddingValues(Spacing.xs),
                modifier = modifier,
            )
        },
    )
}

/** What the heart-rate section needs from Health Connect, gathered by the screen so the views stay stateless. */
internal class TrackingHeartRateInputs(
    val access: HeartRateAccess?,
    val history: List<HeartRateSample>,
    val requestRefused: Boolean,
    val onAllow: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onGetHealthConnect: () -> Unit,
)

@Composable
private fun activityNoun(activity: GpsActivity?): String = stringResource(
    when (activity) {
        GpsActivity.RUN -> R.string.activity_tracking_noun_run
        GpsActivity.WALK -> R.string.activity_tracking_noun_walk
        GpsActivity.OTHER, null -> R.string.activity_tracking_noun_other
    },
)

/**
 * The screen without its ViewModel, so the Finish guard, the Discard dialog, Back/hide and the Stats/Map
 * switch can be exercised together in a test. [routeMap] is the live map (a slot, because MapLibre
 * cannot run in a JVM test). [ending] is true from the first Finish or Discard tap until the screen is
 * left: every control is off and Back is held, so the screen cannot be popped before the end event that
 * takes it to Save Workout has been delivered.
 */
@Composable
internal fun ActivityTrackingContent(
    state: ActivityTrackingState,
    unit: DistanceUnit,
    maxHeartRateBpm: Int?,
    stats: () -> LiveTrackingStats,
    activity: GpsActivity?,
    heartRate: TrackingHeartRateInputs,
    ending: Boolean = false,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
    onHide: () -> Unit,
    routeMap: @Composable (modifier: Modifier) -> Unit,
) {
    val noun = activityNoun(activity)
    var showMap by rememberSaveable { mutableStateOf(false) }
    // Once the map has been opened it stays composed (under the Stats view) so switching back to it is
    // instant, not a new map that reloads its style and waits out the load delay every time.
    var mapOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(showMap) { if (showMap) mapOpened = true }
    var showDiscardConfirm by rememberSaveable { mutableStateOf(false) }
    val statsScroll = rememberScrollState()

    val finishArmed = rememberFinishArmed(state.isPaused)
    // Only while ending: Back is otherwise the navigation's own pop (decision 2), so nothing is
    // registered while moving or paused.
    BackHandler(enabled = ending) { }
    val pausedBanner: (@Composable (Modifier) -> Unit)? = if (state.isPaused) {
        { modifier ->
            PausedBanner(
                pausedForSeconds = { stats().pausedForSeconds ?: 0 },
                onDiscard = { showDiscardConfirm = true },
                enabled = !ending,
                modifier = modifier,
            )
        }
    } else {
        null
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = {
                    val title = when (activity) {
                        GpsActivity.RUN -> stringResource(R.string.activity_tracking_title_run)
                        GpsActivity.WALK -> stringResource(R.string.activity_tracking_title_walk)
                        GpsActivity.OTHER -> stringResource(R.string.activity_tracking_screen_title)
                        null -> ""
                    }
                    ScreenTitle(title)
                },
                navigationIcon = {
                    IconButton(onClick = onHide, enabled = !ending) {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(R.string.activity_tracking_hide))
                    }
                },
                actions = {
                    GpsChip(signal = { stats().gps })
                    Spacer(Modifier.width(Spacing.md))
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = logEzTopAppBarColors(),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.selectableGroup().padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                MetricChip(stringResource(R.string.activity_tracking_tab_stats), selected = !showMap, onClick = { showMap = false })
                MetricChip(stringResource(R.string.activity_tracking_tab_map), selected = showMap, onClick = { showMap = true })
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (showMap || mapOpened) {
                    // Under the Stats view while hidden, which is opaque and takes every touch; hidden
                    // from TalkBack too, so a screen reader never lands on a map it cannot see.
                    Box(modifier = Modifier.fillMaxSize().let { if (showMap) it else it.clearAndSetSemantics { } }) {
                        TrackingMapView(state = state, unit = unit, stats = stats, pausedBanner = pausedBanner, routeMap = routeMap)
                    }
                }
                if (!showMap) {
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        TrackingStatsView(
                            state = state,
                            unit = unit,
                            maxHeartRateBpm = maxHeartRateBpm,
                            stats = stats,
                            heartRate = heartRate,
                            noun = noun,
                            scroll = statsScroll,
                            pausedBanner = pausedBanner,
                        )
                    }
                }
            }

            ActionBar(
                isPaused = state.isPaused,
                enabled = !ending,
                onPause = onPause,
                onResume = onResume,
                onFinish = { if (finishArmed) onFinish() },
            )
        }
    }

    if (showDiscardConfirm) {
        // A direct AlertDialog, not ConfirmDialog: that one only has plain buttons, and Discard is red.
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(stringResource(R.string.activity_tracking_discard_title, noun)) },
            text = { Text(stringResource(R.string.activity_tracking_discard_body)) },
            confirmButton = {
                TextButton(onClick = { showDiscardConfirm = false; onDiscard() }) {
                    Text(stringResource(R.string.activity_tracking_discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text(stringResource(R.string.activity_tracking_discard_keep)) }
            },
        )
    }
}

/** How long Finish ignores taps after Pause. */
internal const val FINISH_GUARD_MILLIS = 500L

/**
 * Whether Finish takes taps: false while moving and for [FINISH_GUARD_MILLIS] after a pause begins,
 * so a fast second tap on the button that has just become Resume's neighbour cannot land on it. Not a
 * disabled button: nothing changes visually, the tap is simply ignored.
 */
@Composable
internal fun rememberFinishArmed(isPaused: Boolean): Boolean {
    // Keyed on [isPaused], so every Pause and every Resume starts from "not armed" in the very
    // composition that sees the change, not a frame later when an effect would reset it.
    var armed by remember(isPaused) { mutableStateOf(false) }
    LaunchedEffect(isPaused) {
        if (isPaused) {
            delay(FINISH_GUARD_MILLIS)
            armed = true
        }
    }
    return armed && isPaused
}

/** A reading older than this gets the "your watch syncs in batches" note under it. */
private const val STALE_READING_MILLIS = 5 * 60_000L

/** The chip's words always name the state, so it never relies on colour alone. */
@Composable
internal fun GpsChip(signal: () -> GpsSignal) {
    val current = signal()
    val label = stringResource(
        when (current) {
            GpsSignal.GOOD -> R.string.activity_tracking_gps_good
            GpsSignal.FINDING -> R.string.activity_tracking_gps_finding
            GpsSignal.WEAK -> R.string.activity_tracking_gps_weak
        },
    )
    val goodDescription = stringResource(R.string.activity_tracking_gps_good_description)
    val shape = RoundedCornerShape(Radius.pill)
    val colors = MaterialTheme.colorScheme
    // The mockup: Finding is the quiet grey, Weak is as bright as a good signal but drawn as a ring, so
    // the two non-good states differ and neither relies on colour alone.
    val tone = if (current == GpsSignal.FINDING) colors.onSurfaceVariant else colors.onSurface
    Row(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, colors.outlineVariant, shape)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
            // A signal that changes is spoken once, politely; the good state reads as a sentence.
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                if (current == GpsSignal.GOOD) contentDescription = goodDescription
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val dot = Modifier.size(8.dp)
        if (current == GpsSignal.GOOD) {
            Box(dot.background(colors.primary, CircleShape))
        } else {
            Box(dot.border(1.5.dp, tone, CircleShape))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.5.sp),
            color = tone,
            maxLines = 1,
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

/** "Paused for 0:42 · time and distance stopped", with Discard at its far end. Shown on both views. */
@Composable
internal fun PausedBanner(pausedForSeconds: () -> Int, onDiscard: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.md)
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(primary.copy(alpha = 0.14f))
            .border(BorderStroke(1.dp, primary.copy(alpha = 0.35f)), shape)
            .padding(start = Spacing.md, top = Spacing.xs, bottom = Spacing.xs, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.activity_tracking_paused_title).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = primary,
                // The state change is spoken; the ticking time under it is not (that would read every second).
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            val clockStyle = SpanStyle(
                fontFamily = LogEzMono.dataMedium.fontFamily,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val template = stringResource(R.string.activity_tracking_paused_detail, CLOCK_TOKEN)
            val clock = formatElapsedClock(pausedForSeconds())
            Text(
                text = buildAnnotatedString {
                    val at = template.indexOf(CLOCK_TOKEN)
                    if (at < 0) {
                        append(template)
                    } else {
                        append(template.substring(0, at))
                        withStyle(clockStyle) { append(clock) }
                        append(template.substring(at + CLOCK_TOKEN.length))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onDiscard, enabled = enabled) {
            Text(stringResource(R.string.activity_tracking_discard), color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Stands in for the clock inside the translated sentence, so the clock alone can take its own style. */
private const val CLOCK_TOKEN = "￼"

/**
 * Pause alone while moving. Paused: Finish (outlined, 48dp) above Resume (filled, 56dp), Resume in
 * the exact place Pause was, so a fast second tap on Pause can only resume. The heights are minimums,
 * so a large system font grows the buttons instead of clipping their labels.
 */
@Composable
internal fun ActionBar(isPaused: Boolean, onPause: () -> Unit, onResume: () -> Unit, onFinish: () -> Unit, enabled: Boolean = true) {
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (isPaused) {
                OutlinedButton(
                    onClick = onFinish,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    // The theme's outlined button is a grey on grey, which read as disabled; Finish is the
                    // run's one real exit, so it takes the brand colour (the mockup's lime text and icon).
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Outlined.Flag, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.activity_tracking_finish), modifier = Modifier.padding(start = Spacing.xs))
                }
                Button(onClick = onResume, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                    Text(stringResource(R.string.activity_tracking_resume), modifier = Modifier.padding(start = Spacing.xs))
                }
            } else {
                Button(onClick = onPause, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Icon(Icons.Outlined.Pause, contentDescription = null, modifier = Modifier.size(22.dp))
                    Text(stringResource(R.string.activity_tracking_pause), modifier = Modifier.padding(start = Spacing.xs))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Stats view
// ---------------------------------------------------------------------------------------------

@Composable
private fun TrackingStatsView(
    state: ActivityTrackingState,
    unit: DistanceUnit,
    maxHeartRateBpm: Int?,
    stats: () -> LiveTrackingStats,
    heartRate: TrackingHeartRateInputs,
    noun: String,
    scroll: androidx.compose.foundation.ScrollState,
    pausedBanner: (@Composable (Modifier) -> Unit)?,
) {
    val km = unit == DistanceUnit.KM
    val startedAtMillis = state.startedAtMillis
    val heartRateHistory = heartRate.history
    val heartRateChartPoints = heartRateHistory.map { LineChartPoint(x = it.time.toEpochMilli(), y = it.bpm.toDouble()) }
    // The newest reading of this session, however old: a watch's heart rate reaches Health Connect
    // in batches, so the "as of" time under it says how far behind it is.
    val liveBpm = heartRateHistory.lastOrNull()
    val heartRateZone = liveBpm?.let { HeartRateZoneCalculator.zoneFor(it.bpm, maxHeartRateBpm) }
    val clockFormatter = rememberClockTimeFormatter()

    // Live splits and the pace chart come from the same calculator the summary uses, over the route
    // so far, so the two cannot disagree. Recomputed when a fix adds a point (every few seconds at
    // most), not every second.
    val heartRatePairs = heartRateHistory.map { it.time.toEpochMilli() to it.bpm }
    val splits = remember(state.workoutId, state.routePoints.size, state.pauseRanges, unit, heartRatePairs) {
        RouteSplitsCalculator.splits(
            state.routePoints, state.routeTimes.map { it.toInt() }, unit, startedAtMillis ?: 0L,
            heartRatePairs, state.distanceMeters.takeIf { it > 0.0 }, state.pauseRanges,
        )
    }
    val paceSeries = remember(state.workoutId, state.routePoints.size, state.pauseRanges, unit) {
        RouteSplitsCalculator.paceSeries(
            state.routePoints, state.routeTimes.map { it.toInt() }, unit, startedAtMillis ?: 0L,
            state.distanceMeters.takeIf { it > 0.0 }, state.pauseRanges,
        )
    }
    var selectedHrIndex by rememberSaveable(heartRateChartPoints) { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll)
            .padding(start = Spacing.md, end = Spacing.md, top = Spacing.xs, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // First in the list, so at a large font it scrolls away instead of taking a pinned slice of a short screen.
        pausedBanner?.invoke(Modifier)

        DistanceHero(distanceMeters = state.distanceMeters, unit = unit, stats = stats)

        StatsCard(distanceMeters = state.distanceMeters, unit = unit, stats = stats)

        HeartRateSection(
            stats = stats,
            access = heartRate.access,
            liveBpm = liveBpm,
            zone = heartRateZone,
            hasMaxHeartRate = (maxHeartRateBpm ?: 0) > 0,
            requestRefused = heartRate.requestRefused,
            formatter = clockFormatter,
            noun = noun,
            onAllow = heartRate.onAllow,
            onOpenSettings = heartRate.onOpenSettings,
            onGetHealthConnect = heartRate.onGetHealthConnect,
        )

        PaceCardContent(
            splits = splits,
            paceSeries = paceSeries,
            startedAtMillis = startedAtMillis ?: 0L,
            distanceUnit = unit,
            footnote = if (splits.lastOrNull()?.isPartial == true) {
                stringResource(if (km) R.string.activity_tracking_pace_footnote_km else R.string.activity_tracking_pace_footnote_mi)
            } else {
                null
            },
            emptyText = stringResource(R.string.activity_tracking_pace_chart_empty),
        )

        // Only once readings exist: in every other state the heart-rate card or line above already
        // says what is missing.
        if (heartRateChartPoints.isNotEmpty()) {
            LogEzCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    CardHeadingRow(
                        title = stringResource(R.string.summary_gps_heart_rate_header),
                        meta = stringResource(R.string.summary_gps_heart_rate_source),
                    )
                    LineChart(
                        points = heartRateChartPoints,
                        yLabel = { "${it.toInt()}" },
                        xLabel = { formatElapsedClock((((it - (startedAtMillis ?: it)) / 1000).coerceAtLeast(0L)).toInt()) },
                        selectedIndex = selectedHrIndex,
                        onPointTap = { selectedHrIndex = it },
                        showPoints = false,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
        }
    }
}

/** Reads the live stats itself, so only this small leaf recomposes each second. */
@Composable
private fun HeartRateSection(
    stats: () -> LiveTrackingStats,
    access: HeartRateAccess?,
    liveBpm: HeartRateSample?,
    zone: com.enil.logez.core.domain.calc.HeartRateZone?,
    hasMaxHeartRate: Boolean,
    requestRefused: Boolean,
    formatter: DateTimeFormatter,
    noun: String,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onGetHealthConnect: () -> Unit,
) {
    val nowMillis = stats().nowMillis
    val isStale = liveBpm != null && nowMillis - liveBpm.time.toEpochMilli() > STALE_READING_MILLIS
    val heartRateState = trackingHeartRateState(access, liveBpm != null, hasMaxHeartRate, isStale, requestRefused)
    TrackingHeartRate(
        state = heartRateState,
        bpm = liveBpm?.bpm,
        zone = zone,
        asOf = liveBpm?.let { formatClockTime(it.time, formatter) },
        noun = noun,
        onAllow = onAllow,
        onOpenSettings = onOpenSettings,
        onGetHealthConnect = onGetHealthConnect,
    )
}

/**
 * Distance at 96sp, readable at arm's length mid-stride (the summary uses 60sp), shrinking only when
 * a large system font would otherwise wrap it. Below it, one note line whose height is always kept,
 * empty while GPS is good, so the stats card never jumps when the signal drops or returns.
 */
@Composable
internal fun DistanceHero(distanceMeters: Double, unit: DistanceUnit, stats: () -> LiveTrackingStats) {
    val signal = stats().gps
    val number = formatDistanceNumber(distanceMeters, unit)
    val unitLabel = stringResource(if (unit == DistanceUnit.KM) R.string.activity_tracking_unit_km else R.string.activity_tracking_unit_mi)
    // "0.00" in the muted colour until the first usable fix: it is a placeholder, not a measurement.
    val numberColor = if (signal == GpsSignal.FINDING && distanceMeters == 0.0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
            CapsLabel(stringResource(R.string.summary_gps_distance))
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    number,
                    style = LogEzMono.dataLarge.copy(fontSize = 96.sp, lineHeight = 1.05.em, letterSpacing = (-0.03).em),
                    color = numberColor,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 40.sp, maxFontSize = 96.sp),
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    unitLabel,
                    style = LogEzMono.dataLarge.copy(fontSize = 24.sp, lineHeight = 30.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs),
                )
            }
        }
        val finding = stringResource(R.string.activity_tracking_note_finding)
        val weak = stringResource(R.string.activity_tracking_note_weak)
        // Both notes are laid out, invisibly, so the box is as tall as the taller one at whatever font
        // size and width: the real note is drawn over them and the card below never moves.
        Box(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xxs)) {
            NoteRow(finding, Modifier.alpha(0f).clearAndSetSemantics { })
            NoteRow(weak, Modifier.alpha(0f).clearAndSetSemantics { })
            when (signal) {
                GpsSignal.FINDING -> NoteRow(finding, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                GpsSignal.WEAK -> NoteRow(weak, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                GpsSignal.GOOD -> Unit
            }
        }
    }
}

@Composable
private fun NoteRow(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp).size(16.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

/** Time and Pace now on top, Avg pace and Avg speed below, at 36sp: a 2x2 card. */
@Composable
internal fun StatsCard(distanceMeters: Double, unit: DistanceUnit, stats: () -> LiveTrackingStats) {
    val km = unit == DistanceUnit.KM
    val live = stats()
    val averagePace = PaceCalculator.paceSecondsPerUnit(distanceMeters, live.elapsedSeconds, unit)
    val averageSpeed = averagePace?.takeIf { it > 0.0 }?.let { 3600.0 / it }
    LogEzCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = Spacing.xs)) {
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                BigStat(formatElapsedClock(live.elapsedSeconds), stringResource(R.string.activity_tracking_elapsed_label), Modifier.weight(1f))
                VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = Spacing.xs), color = MaterialTheme.colorScheme.outlineVariant)
                BigStat(
                    live.paceNowSecondsPerUnit?.let(::formatPace) ?: PLACEHOLDER,
                    stringResource(if (km) R.string.activity_tracking_pace_now_km else R.string.activity_tracking_pace_now_mi),
                    Modifier.weight(1f),
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md), color = MaterialTheme.colorScheme.outlineVariant)
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                BigStat(
                    averagePace?.let(::formatPace) ?: PLACEHOLDER,
                    stringResource(if (km) R.string.summary_gps_avg_pace_km else R.string.summary_gps_avg_pace_mi),
                    Modifier.weight(1f),
                )
                VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = Spacing.xs), color = MaterialTheme.colorScheme.outlineVariant)
                BigStat(
                    averageSpeed?.let { "%.1f".format(Locale.ROOT, it) } ?: PLACEHOLDER,
                    stringResource(if (km) R.string.summary_gps_avg_speed_km else R.string.summary_gps_avg_speed_mi),
                    Modifier.weight(1f),
                )
            }
        }
    }
}

/** A dash is read as "no value yet", not as the word for the character; the number and its label are one stop. */
@Composable
private fun StatValue(value: String, style: androidx.compose.ui.text.TextStyle, minFontSize: androidx.compose.ui.unit.TextUnit, maxFontSize: androidx.compose.ui.unit.TextUnit, color: androidx.compose.ui.graphics.Color) {
    val none = stringResource(R.string.activity_tracking_stat_none)
    Text(
        value,
        style = style,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        // An hour-plus "1:02:30" at a large system font shrinks instead of breaking mid-number.
        autoSize = TextAutoSize.StepBased(minFontSize = minFontSize, maxFontSize = maxFontSize),
        modifier = if (value == PLACEHOLDER) Modifier.semantics { contentDescription = none } else Modifier,
    )
}

@Composable
private fun BigStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {}.padding(horizontal = Spacing.xs, vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatValue(
            value,
            style = LogEzMono.dataLarge.copy(fontSize = 36.sp, lineHeight = 1.15.em),
            minFontSize = 18.sp,
            maxFontSize = 36.sp,
            // A dash in the brand colour reads as a bar, not an empty slot.
            color = if (value == PLACEHOLDER) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Map view
// ---------------------------------------------------------------------------------------------

/**
 * The map fills the width between the chips and a Distance / Time / Avg pace strip: the route, one
 * line per moving stretch, and a "you are here" dot. It follows you until you drag it; then the map's
 * own "Recenter on my location" button shows. A map that fails to load says the run is still being
 * recorded, because a failed map must not look like a failed run.
 *
 * The paused banner, the map and the strip are laid out in one column that takes the room left, with
 * the map as the flexible part but never under [MIN_MAP_HEIGHT]: where the other two do not leave that
 * much (a short phone at a large font), the column scrolls instead of squeezing the map to a sliver.
 */
@Composable
private fun TrackingMapView(
    state: ActivityTrackingState,
    unit: DistanceUnit,
    stats: () -> LiveTrackingStats,
    pausedBanner: (@Composable (Modifier) -> Unit)?,
    routeMap: @Composable (modifier: Modifier) -> Unit,
) {
    val minMapPx = with(LocalDensity.current) { MIN_MAP_HEIGHT.roundToPx() }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportPx = constraints.maxHeight
        Layout(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            content = {
                Box { pausedBanner?.invoke(Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.xs, bottom = Spacing.sm)) }
                routeMap(Modifier.fillMaxSize())
                MapStrip(
                    distanceMeters = state.distanceMeters,
                    unit = unit,
                    stats = stats,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                )
            },
        ) { measurables, incoming ->
            val width = incoming.maxWidth
            val loose = Constraints(maxWidth = width)
            val top = measurables[0].measure(loose)
            val strip = measurables[2].measure(loose)
            val mapHeight = (viewportPx - top.height - strip.height).coerceAtLeast(minMapPx)
            val map = measurables[1].measure(Constraints.fixed(width, mapHeight))
            layout(width, top.height + mapHeight + strip.height) {
                top.placeRelative(0, 0)
                map.placeRelative(0, top.height)
                strip.placeRelative(0, top.height + mapHeight)
            }
        }
    }
}

/** The least height the live map is given; below it the Map view scrolls. */
private val MIN_MAP_HEIGHT = 200.dp

@Composable
internal fun MapStrip(distanceMeters: Double, unit: DistanceUnit, stats: () -> LiveTrackingStats, modifier: Modifier = Modifier) {
    val km = unit == DistanceUnit.KM
    val live = stats()
    val averagePace = PaceCalculator.paceSecondsPerUnit(distanceMeters, live.elapsedSeconds, unit)
    val distance = formatDistanceNumber(distanceMeters, unit)
    val distanceLabel = stringResource(if (km) R.string.activity_tracking_distance_label_km else R.string.activity_tracking_distance_label_mi)
    val time = formatElapsedClock(live.elapsedSeconds)
    val timeLabel = stringResource(R.string.activity_tracking_elapsed_label)
    val pace = averagePace?.let(::formatPace) ?: PLACEHOLDER
    val paceLabel = stringResource(if (km) R.string.summary_gps_avg_pace_km else R.string.summary_gps_avg_pace_mi)
    LogEzCard(modifier = modifier.fillMaxWidth()) {
        if (LocalDensity.current.fontScale >= STACKED_STRIP_FONT_SCALE) {
            // Three columns of about 100dp cannot hold "Distance (km)" at this size without breaking
            // the word, so the stats take a row each and the card gets as tall as it needs.
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
                StripStat(distance, distanceLabel, Modifier.fillMaxWidth())
                HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md), color = MaterialTheme.colorScheme.outlineVariant)
                StripStat(time, timeLabel, Modifier.fillMaxWidth())
                HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md), color = MaterialTheme.colorScheme.outlineVariant)
                StripStat(pace, paceLabel, Modifier.fillMaxWidth())
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = Spacing.xs)) {
                StripStat(distance, distanceLabel, Modifier.weight(1f))
                VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = Spacing.xs), color = MaterialTheme.colorScheme.outlineVariant)
                StripStat(time, timeLabel, Modifier.weight(1f))
                VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = Spacing.xs), color = MaterialTheme.colorScheme.outlineVariant)
                StripStat(pace, paceLabel, Modifier.weight(1f))
            }
        }
    }
}

/** From this system font scale up, the map strip stacks its three stats (see [MapStrip]). */
internal const val STACKED_STRIP_FONT_SCALE = 1.3f

@Composable
private fun StripStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {}.padding(horizontal = Spacing.xxs, vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatValue(
            value,
            style = LogEzMono.dataLarge.copy(fontSize = 28.sp, lineHeight = 34.sp),
            minFontSize = 12.sp,
            maxFontSize = 28.sp,
            color = if (value == PLACEHOLDER) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        // Wraps, like the stats card's labels, so a large system font keeps the unit readable.
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** What a stat reads while it has nothing to show yet -- the same dash every other stat surface uses. */
private const val PLACEHOLDER = "—"

// Locale.ROOT: the default-locale overload renders "1,20" on comma-decimal devices. Returns just
// the number in the user's distance unit -- the "km"/"mi" lives in its own label. Always two
// decimals while it ticks ("0.00", then "0.05"), matching the walk/run hundredths convention
// (Owner request, 2026-09-23).
internal fun formatDistanceNumber(distanceMeters: Double, unit: DistanceUnit): String {
    val display = DistanceDisplay.toDisplay(distanceMeters, unit)
    return "%.2f".format(Locale.ROOT, (display * 100).roundToInt() / 100.0)
}

/** "7:44 PM" or "19:44", following the phone's 12/24-hour setting (see [rememberClockTimeFormatter]). */
private fun formatClockTime(instant: Instant, formatter: DateTimeFormatter): String =
    instant.atZone(ZoneId.systemDefault()).format(formatter)
