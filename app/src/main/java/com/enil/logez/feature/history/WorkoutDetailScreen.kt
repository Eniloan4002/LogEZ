package com.enil.logez.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import com.enil.logez.feature.workout.finish.HeartRateCard
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.CircuitChip
import com.enil.logez.core.designsystem.ConfirmDialog
import com.enil.logez.core.designsystem.EffortExplainerSheet
import com.enil.logez.core.designsystem.Gold500
import com.enil.logez.core.designsystem.HistorySetTable
import com.enil.logez.core.designsystem.HistorySetTableRow
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.Radius
import com.enil.logez.core.designsystem.SetLegend
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.StatCell
import com.enil.logez.core.designsystem.SupersetPalette
import com.enil.logez.core.designsystem.formatWeight
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import com.enil.logez.core.designsystem.currentLocale
import com.enil.logez.core.designsystem.historyColumnsFor
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.EffortScale
import com.enil.logez.core.domain.model.SetDisplayLabel
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutStructure
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.feature.activity.map.RouteMapView
import com.enil.logez.feature.workout.StartResult
import com.enil.logez.feature.workout.circuitSetLabel
import com.enil.logez.feature.workout.finish.labelRes
import com.enil.logez.feature.activity.InterruptedTrackingDialog
import com.enil.logez.feature.workout.InProgressWorkout
import com.enil.logez.feature.workout.rememberStartWorkoutSession
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import com.enil.logez.core.designsystem.rememberClockTimeFormatter

/** PHASE2_PLAN.md §5.2 "Workout Detail": read-only record of one completed workout. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    onBack: () -> Unit,
    onEdit: (workoutId: String) -> Unit,
    onSavedAsRoutine: (routineId: String) -> Unit,
    onNavigateToLogger: (workoutId: String) -> Unit,
    onExerciseClick: (exerciseId: String) -> Unit,
    onNavigateToActivityTracking: () -> Unit,
    onNavigateToFinish: (workoutId: String) -> Unit,
    viewModel: WorkoutDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val startSession = rememberStartWorkoutSession(onNavigateToLogger)

    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showResumeDialog by remember { mutableStateOf(false) }
    var conflictingWorkoutId by remember { mutableStateOf<String?>(null) }
    var interruptedRun by remember { mutableStateOf<Pair<String, Long>?>(null) }
    // P-211 §6: one explainer for the screen, from the legend's effort line or any table's ⓘ.
    var showEffortExplainer by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.isMissing) { if (uiState.isMissing) onBack() }

    // Edit mode saves in place and pops straight back here, so the data this screen loaded in
    // init is stale the moment it returns. Re-read on every RESUME rather than on first
    // composition only.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun copyWorkout() = scope.launch {
        menuExpanded = false
        when (val result = viewModel.startCopy()) {
            is StartResult.Started -> startSession(result.workoutId)
            is StartResult.AlreadyInProgress -> { showResumeDialog = true; conflictingWorkoutId = result.workoutId }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = logEzTopAppBarColors(),
                title = { Text(uiState.workout?.title.orEmpty(), color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    val loadedWorkoutId = uiState.workout?.id
                    if (loadedWorkoutId != null) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.history_detail_edit)) },
                                onClick = { menuExpanded = false; onEdit(loadedWorkoutId) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.history_detail_copy)) },
                                onClick = { copyWorkout() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.history_detail_save_as_routine)) },
                                onClick = {
                                    menuExpanded = false
                                    scope.launch { viewModel.saveAsRoutine()?.let(onSavedAsRoutine) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete)) },
                                onClick = { menuExpanded = false; showDeleteConfirm = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val workout = uiState.workout
        if (workout == null || uiState.isLoading) return@Scaffold
        val isCircuit = workout.structure == WorkoutStructure.CIRCUIT
        // M11: post-purge round count — the largest surviving round number; unequal blocks (a
        // skipped exercise in some round) simply produce rounds with fewer entries.
        val detailRounds = if (isCircuit) buildDetailRounds(uiState.exerciseBlocks) else emptyList()

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.md)) {
            item {
                Column(modifier = Modifier.padding(top = Spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val started = Instant.ofEpochMilli(workout.startedAt).atZone(ZoneId.systemDefault())
                        Text(
                            stringResource(
                                R.string.history_card_date_time,
                                started.format(DateTimeFormatter.ofPattern("d MMM yyyy", currentLocale())),
                                started.format(rememberClockTimeFormatter()),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (isCircuit) CircuitChip()
                    }
                    if (!workout.notes.isNullOrBlank()) {
                        Text(workout.notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.sm))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        DetailStatCell(stringResource(R.string.summary_duration), formatDetailDuration(uiState.durationSeconds))
                        // A GPS-tracked walk/run never logged weight -- "0kg Volume" would be noise
                        // next to its real distance, so the cell is gated on whether it was tracked.
                        if (uiState.hasVolume) DetailStatCell(stringResource(R.string.summary_volume), formatDetailVolume(uiState.volumeKg, uiState.weightUnit))
                        DetailStatCell(stringResource(R.string.summary_sets), uiState.completedSetCount.toString())
                        if (uiState.hasDistance) DetailStatCell(stringResource(R.string.summary_distance), formatDetailDistance(uiState.distanceMeters, uiState.distanceUnit))
                        if (isCircuit) DetailStatCell(stringResource(R.string.routine_rounds_label), detailRounds.size.toString())
                        // Owner, 2026-10-01: the trophy alone, no "Personal records" caption -- with
                        // duration, volume, sets and distance all showing, the caption crowded the row.
                        // TalkBack still reads the name.
                        if (uiState.hasRecords) {
                            Icon(
                                Icons.Outlined.EmojiEvents,
                                contentDescription = stringResource(R.string.summary_prs_header),
                                tint = Gold500,
                                modifier = Modifier.align(Alignment.CenterVertically).size(28.dp),
                            )
                        }
                    }

                    // P-211 decision 5: the key to the tables below. Only the badge types this
                    // workout holds, the trophy when a set carries one, and the effort line when
                    // some set has a value (in the saved scale even while tracking is Off, D4).
                    val sets = uiState.exerciseBlocks.flatMap { it.sets }
                    SetLegend(
                        setTypes = sets.mapTo(mutableSetOf()) { it.setType },
                        showPersonalRecord = sets.any { it.pr != null },
                        effortScale = uiState.effortScale.takeIf { sets.any { it.rpe != null } },
                        onEffortInfoClick = { showEffortExplainer = true },
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                }
            }

            // M21b/c: the offline map with the recorded GPS route drawn on it, fit to the route's
            // bounds -- only rendered for a GPS-tracked workout (uiState.hasRoute), never a strength one.
            if (uiState.hasRoute) {
                item {
                    RouteCard(routePoints = uiState.routePoints)
                }
            }

            // Heart rate saved for this workout, the same card the walk/run summary shows. It can
            // appear on a later visit: opening the workout asks Health Connect for readings the
            // watch synced after Save (2026-09-26).
            uiState.heartRateSummary?.let { summary ->
                item {
                    HeartRateCard(summary, workout.startedAt, Modifier.padding(vertical = Spacing.xs))
                }
            }

            if (isCircuit) {
                // M11: round-grouped record — mirrors the circuit logger's view of the same rows.
                items(items = detailRounds, key = { it.roundNumber }) { round ->
                    DetailRoundCard(round, uiState.tableUnits(), onEffortInfoClick = { showEffortExplainer = true }, onExerciseClick)
                }
            } else {
                items(items = uiState.exerciseBlocks, key = { it.workoutExercise.id }) { block ->
                    ExerciseBlockCard(block, uiState.tableUnits(), onEffortInfoClick = { showEffortExplainer = true }, onExerciseClick)
                }
            }
        }
    }

    if (showEffortExplainer) {
        EffortExplainerSheet(uiState.effortScale, onDismiss = { showEffortExplainer = false })
    }

    interruptedRun?.let { (workoutId, startedAt) ->
        InterruptedTrackingDialog(
            workoutId = workoutId,
            startedAt = startedAt,
            onKeptTime = { id -> interruptedRun = null; onNavigateToFinish(id) },
            onDiscarded = { interruptedRun = null },
        )
    }

    if (showResumeDialog) {
        AlertDialog(
            onDismissRequest = { showResumeDialog = false },
            title = { Text(stringResource(R.string.workout_resume_title)) },
            text = { Text(stringResource(R.string.workout_resume_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showResumeDialog = false
                    scope.launch {
                        // Only the strength branch may go through startSession: it starts
                        // WorkoutSessionService, which for a GPS run means a second foreground
                        // service next to the location one, and the wrong screen.
                        when (val inProgress = viewModel.inProgressWorkout()) {
                            null -> Unit
                            is InProgressWorkout.Strength -> startSession(inProgress.id)
                            is InProgressWorkout.LiveGpsRun -> onNavigateToActivityTracking()
                            is InProgressWorkout.InterruptedGpsRun ->
                                interruptedRun = inProgress.id to inProgress.startedAt
                        }
                    }
                }) {
                    Text(stringResource(R.string.workout_resume_action))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showResumeDialog = false
                    scope.launch { startSession(viewModel.discardInProgressAndStartCopy()) }
                }) { Text(stringResource(R.string.workout_resume_discard_action)) }
            },
        )
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = stringResource(R.string.history_detail_delete_title),
            body = stringResource(R.string.history_detail_delete_body),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { viewModel.delete(onBack) },
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }
}

@Composable
private fun DetailStatCell(label: String, value: String) {
    StatCell(
        value = value,
        label = label,
        horizontalAlignment = Alignment.CenterHorizontally,
        valueStyle = LogEzMono.dataLarge,
        labelStyle = MaterialTheme.typography.labelSmall,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** M11: one round of a completed circuit — the exercises' rows at the same orderIndex, sequence order. */
internal data class DetailRound(val roundNumber: Int, val entries: List<DetailRoundEntry>)
internal data class DetailRoundEntry(val block: DetailExerciseBlock, val set: DetailSetRow?)

/**
 * Groups a circuit workout's post-purge blocks round-first, keyed by each row's `orderIndex`
 * (== its round), NOT its list position: the finish flow's uncompleted-set purge deletes skipped
 * rows without re-indexing, so a skipped MIDDLE round leaves a gap ({0,2}) that positional
 * grouping would silently shift — attributing round 3's performance to "ROUND 2" forever.
 * Defensive by construction: a block with no surviving row at a round contributes a null
 * (rendered "—") entry — gaps and unequal counts must render, never crash.
 */
internal fun buildDetailRounds(blocks: List<DetailExerciseBlock>): List<DetailRound> {
    val rounds = blocks.maxOfOrNull { block -> block.sets.maxOfOrNull { it.orderIndex + 1 } ?: 0 } ?: 0
    return (0 until rounds).map { roundIndex ->
        DetailRound(
            roundNumber = roundIndex + 1,
            entries = blocks.map { block -> DetailRoundEntry(block, block.sets.find { it.orderIndex == roundIndex }) },
        )
    }
}

/** What every set table on this screen formats with: the settings' units and effort scale. */
internal data class DetailTableUnits(val effortScale: EffortScale, val weightUnit: WeightUnit, val distanceUnit: DistanceUnit)

private fun WorkoutDetailUiState.tableUnits() = DetailTableUnits(effortScale, weightUnit, distanceUnit)

/**
 * M11 round card. P-211 §2 follows the logger's `CircuitRoundCard` rule: when every exercise in
 * the round has the same columns, one header under "ROUND N" covers all of them and each name
 * sits above its row; a mixed round (Push-up with Plank) gets a header per exercise, since one
 * header can't label both. Rows keep the round's number (decision 9 leaves circuits alone).
 *
 * [preview] is the Recent workout detail's reading (see [ExerciseBlockCard]): an exercise deleted
 * from the library is muted and not a link, and the card says once that Start still adds it.
 */
@Composable
internal fun DetailRoundCard(
    round: DetailRound,
    units: DetailTableUnits,
    onEffortInfoClick: () -> Unit,
    onExerciseClick: (String) -> Unit,
    preview: Boolean = false,
) {
    val hasDeleted = preview && round.entries.any { val exercise = it.block.exercise; exercise == null || exercise.isDeleted }
    LogEzCard(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.workout_round_header, round.roundNumber).uppercase(currentLocale()),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
            val sharedColumns = round.entries
                .filter { it.set != null }
                .map { historyColumnsFor(it.block.exercise?.exerciseType) }
                .distinct()
                .singleOrNull()
            if (sharedColumns != null) {
                HistorySetTable(
                    exerciseType = null,
                    rows = round.entries.map { it.tableRow() },
                    effortScale = units.effortScale,
                    weightUnit = units.weightUnit,
                    distanceUnit = units.distanceUnit,
                    onEffortInfoClick = onEffortInfoClick,
                    columns = sharedColumns,
                    labels = round.entries.map { it.label(round.roundNumber) },
                    aboveRow = { index -> RoundEntryName(round.entries[index].block.exercise, onExerciseClick, preview = preview) },
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            } else {
                round.entries.forEach { entry ->
                    RoundEntryName(entry.block.exercise, onExerciseClick, Modifier.padding(top = Spacing.sm), preview = preview)
                    HistorySetTable(
                        exerciseType = entry.block.exercise?.exerciseType,
                        rows = listOf(entry.tableRow()),
                        effortScale = units.effortScale,
                        weightUnit = units.weightUnit,
                        distanceUnit = units.distanceUnit,
                        onEffortInfoClick = onEffortInfoClick,
                        labels = listOf(entry.label(round.roundNumber)),
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
            if (hasDeleted) {
                Text(
                    stringResource(R.string.recent_detail_deleted_exercise),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun RoundEntryName(exercise: Exercise?, onExerciseClick: (String) -> Unit, modifier: Modifier = Modifier, preview: Boolean = false) {
    val isDeleted = preview && (exercise == null || exercise.isDeleted)
    Text(
        exercise?.name.orEmpty(),
        style = MaterialTheme.typography.bodyMedium,
        color = if (isDeleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        modifier = modifier.then(if (exercise != null && !isDeleted) Modifier.clickable { onExerciseClick(exercise.id) } else Modifier),
    )
}

/**
 * A round's row for one exercise. Defensive slot: an exercise with no surviving row in this round
 * reads as a row of "—" (a not-completed row), so gaps and unequal counts render, never crash.
 */
private fun DetailRoundEntry.tableRow(): HistorySetTableRow = set?.toTableRow() ?: HistorySetTableRow(setType = SetType.NORMAL, isCompleted = false)

private fun DetailRoundEntry.label(roundNumber: Int): SetDisplayLabel = circuitSetLabel(set?.setType ?: SetType.NORMAL, roundNumber)

private fun DetailSetRow.toTableRow() = HistorySetTableRow(
    setType = setType,
    weightKg = weightKg,
    reps = reps,
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    customMetric = customMetric,
    rpe = rpe,
    isCompleted = isCompleted,
    prLabelRes = pr?.prType?.labelRes(),
)

/** M21b/c: the offline Metro Manila map with the recorded GPS route drawn on it, camera fit to the route's bounds. */
@Composable
private fun RouteCard(routePoints: List<Pair<Double, Double>>) {
    LogEzCard(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(stringResource(R.string.workout_detail_route_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            RouteMapView(
                routePoints = routePoints,
                followLatest = false,
                modifier = Modifier.fillMaxWidth().height(220.dp).padding(top = Spacing.sm).clip(RoundedCornerShape(Radius.sm)),
            )
        }
    }
}

/**
 * P-211 §2 (Owner, 2026-09-30): the exercise's sets as a headed table (SET | KG | REPS | RPE ⓘ for
 * lifts, SET | TIME | DISTANCE for a run) in place of the "Set 2: 80kg · 8 reps · @8.0" lines.
 * The superset stripe is drawn behind the content rather than as a sibling of an
 * IntrinsicSize.Min row: the table measures its columns in a BoxWithConstraints, which can't
 * answer an intrinsic-size query.
 *
 * [preview] is the Recent workout detail's reading of the same card (R-1, 2026-10-01): what Start
 * would add rather than a record. A superset also carries the logger's "Superset" label above the
 * name, and an exercise deleted from the library is muted, says "Start still adds it" and isn't
 * a link. The caller strips effort and trophies from the rows it passes.
 */
@Composable
internal fun ExerciseBlockCard(
    block: DetailExerciseBlock,
    units: DetailTableUnits,
    onEffortInfoClick: () -> Unit,
    onExerciseClick: (String) -> Unit,
    preview: Boolean = false,
) {
    val supersetColor = block.workoutExercise.supersetGroup?.let { SupersetPalette[it % SupersetPalette.size] }
    val exercise = block.exercise
    val isDeleted = preview && (exercise == null || exercise.isDeleted)
    LogEzCard(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (supersetColor != null) {
                        Modifier
                            .drawBehind { drawRect(supersetColor, size = Size(SUPERSET_STRIPE_WIDTH.toPx(), size.height)) }
                            .padding(start = SUPERSET_STRIPE_WIDTH)
                    } else {
                        Modifier
                    },
                )
                .padding(Spacing.md),
        ) {
            if (preview && supersetColor != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Spacing.xs)) {
                    Box(modifier = Modifier.size(10.dp).background(supersetColor, CircleShape))
                    Text(
                        stringResource(R.string.routine_builder_superset_chip),
                        style = MaterialTheme.typography.labelSmall,
                        color = supersetColor,
                        modifier = Modifier.padding(start = Spacing.xxs),
                    )
                }
            }
            Text(
                exercise?.name.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = if (isDeleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                modifier = if (exercise != null && !isDeleted) Modifier.clickable { onExerciseClick(exercise.id) } else Modifier,
            )
            if (isDeleted) {
                Text(
                    stringResource(R.string.recent_detail_deleted_exercise),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }
            if (!block.workoutExercise.notes.isNullOrBlank()) {
                Text(
                    block.workoutExercise.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }
            HistorySetTable(
                exerciseType = exercise?.exerciseType,
                rows = block.sets.map { it.toTableRow() },
                effortScale = units.effortScale,
                weightUnit = units.weightUnit,
                distanceUnit = units.distanceUnit,
                onEffortInfoClick = onEffortInfoClick,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

private val SUPERSET_STRIPE_WIDTH = 4.dp

private fun formatDetailDuration(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun formatDetailVolume(kg: Double, unit: WeightUnit): String = formatWeight(kg, unit)

private fun formatDetailDistance(meters: Double, unit: DistanceUnit): String = com.enil.logez.core.designsystem.formatDistance(meters, unit)
