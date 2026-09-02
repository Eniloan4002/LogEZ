package com.enil.logez.feature.workout

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.Radius
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.domain.model.WorkoutStructure
import com.enil.logez.feature.exercises.ExercisePickerMode
import com.enil.logez.feature.exercises.ExercisePickerSheet
import com.enil.logez.feature.workout.finish.fromDatePickerMillis
import com.enil.logez.feature.workout.finish.labelRes
import com.enil.logez.feature.workout.finish.toDatePickerMillis
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutLoggerScreen(
    onExit: () -> Unit,
    onNavigateToFinish: () -> Unit,
    onDiscarded: () -> Unit,
    onExerciseClick: (exerciseId: String) -> Unit,
    onCreateExercise: (prefillName: String?) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: WorkoutLoggerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pickerMode by remember { mutableStateOf<ExercisePickerMode?>(null) }
    var replaceTargetId by remember { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var timerMenuExpanded by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var isFinishing by remember { mutableStateOf(false) }
    var showDiscardEditConfirm by remember { mutableStateOf(false) }
    var showEditIncompleteConfirm by remember { mutableStateOf(false) }
    var showEditDatePicker by remember { mutableStateOf(false) }
    // M11: which round's "Remove Round" is awaiting confirmation (0-based), if any.
    var pendingRemoveRoundIndex by remember { mutableStateOf<Int?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val editSaveState by viewModel.editSaveState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // §5.1.10: "Returns to Workout Detail." Driven off ViewModel state, not the tap, so a save
    // that outlives an Activity recreation still navigates when it lands.
    val editSaveFailedMessage = stringResource(R.string.workout_edit_save_failed)
    LaunchedEffect(editSaveState) {
        when (editSaveState) {
            is EditSaveState.Saved -> onExit()
            is EditSaveState.Failed -> {
                snackbarHostState.showSnackbar(editSaveFailedMessage)
                viewModel.clearEditSaveError()
            }
            else -> Unit
        }
    }

    fun handleBack() {
        // §5.1.10: in edit mode nothing has been persisted, so leaving genuinely discards — which
        // is worth confirming. Live logging is the opposite: it is already write-through, so
        // navigating away just leaves the workout IN_PROGRESS (spine); the service keeps it
        // foregrounded and the mini-bar surfaces it globally, so this exits without ceremony.
        if (uiState.isEditMode) showDiscardEditConfirm = true else onExit()
    }
    BackHandler(onBack = ::handleBack)

    // §9.6 Keep-awake: scoped strictly to this screen, cleared on dispose/navigate-away — no wakelock.
    val view = LocalView.current
    DisposableEffect(uiState.keepAwakeEnabled) {
        val window = (view.context as? Activity)?.window
        if (uiState.keepAwakeEnabled) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // §5.1.3 step 7: Smart Superset Scrolling.
    LaunchedEffect(Unit) {
        viewModel.scrollToExercise.collect { exerciseId ->
            val index = uiState.exercises.indexOfFirst { it.id == exerciseId }
            if (index >= 0) listState.scrollToItem(index)
        }
    }

    // M11: the circuit analog — round-card indices map 1:1 onto the circuit list's items.
    LaunchedEffect(Unit) {
        viewModel.scrollToRound.collect { roundIndex ->
            if (roundIndex >= 0) listState.scrollToItem(roundIndex)
        }
    }

    // §5.1.3 step 6 / §8.4: the live PR banner.
    val prBannerPrefix = stringResource(R.string.pr_banner, "")
    LaunchedEffect(Unit) {
        viewModel.prBanner.collect { prTypes ->
            val names = prTypes.joinToString(", ") { context.getString(it.labelRes()) }
            snackbarHostState.showSnackbar(prBannerPrefix.trimEnd() + " " + names)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (uiState.isEditMode) {
                                stringResource(R.string.workout_edit_title, formatEditDate(uiState.editedStartedAtMillis))
                            } else {
                                uiState.title
                            },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // §5.1.10: no elapsed ticking in edit mode — the stopwatch's slot carries
                        // the same set/volume figures without the live timer that has nothing to
                        // count, and date/duration become editable rows in the body instead.
                        if (uiState.isEditMode) {
                            Text(
                                stringResource(R.string.workout_edit_stats, uiState.completedSetCount, formatVolumeShort(uiState.totalVolumeKg)),
                                style = LogEzMono.dataSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                maxLines = 1,
                            )
                        } else {
                            // Owner: this row was reading as stacked, not side-by-side -- it was
                            // always a Row, but AssistChip's Material3 spec padding/min-height made
                            // it visually heavy next to the compact mono stats text, and the stats
                            // text had no maxLines cap, so a long "elapsed · sets · volume" string
                            // could push the chip out of the TopAppBar title slot's limited width.
                            // Box(weight) + a maxLines/ellipsis cap + a hand-built compact chip
                            // (not AssistChip's padding) both fix that.
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Box(modifier = Modifier.weight(1f, fill = false).clickable { timerMenuExpanded = true }) {
                                    WorkoutStatsText(
                                        elapsedSecondsFlow = viewModel.elapsedSecondsFlow,
                                        completedSetCount = uiState.completedSetCount,
                                        totalVolumeKg = uiState.totalVolumeKg,
                                    )
                                }
                                DropdownMenu(expanded = timerMenuExpanded, onDismissRequest = { timerMenuExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(if (uiState.isPaused) R.string.workout_resume_timer else R.string.workout_pause_timer)) },
                                        onClick = { timerMenuExpanded = false; viewModel.togglePause() },
                                    )
                                }
                                if (uiState.restExerciseId != null) {
                                    RestTimerChip(viewModel.restRemainingMillisFlow)
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = ::handleBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (uiState.isEditMode) {
                        TextButton(
                            enabled = uiState.canSaveEdit && editSaveState !is EditSaveState.Saving,
                            onClick = {
                                if (viewModel.uncompletedSetCount() > 0) showEditIncompleteConfirm = true else viewModel.saveEdit()
                            },
                        ) { Text(stringResource(R.string.action_save)) }
                    } else {
                        // A filled green box, not a plain text action (Owner) -- Finish is the
                        // screen's one high-stakes action, so it gets the same vibrant-primary
                        // treatment as every other primary CTA in the app.
                        Button(
                            // Guarded: prepareForFinish does a Room write and the service stop is
                            // an IPC, so the first tap is slow enough to double-tap. Two runs would
                            // end the session, take the no-session duration fallback on the second,
                            // and push a second Save screen onto the back stack.
                            enabled = !isFinishing,
                            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                            modifier = Modifier.padding(end = Spacing.xs),
                            onClick = {
                                // Freeze the live duration into the row, stop the service, then
                                // hand off to the Save screen — the workout stays IN_PROGRESS
                                // until it saves there.
                                isFinishing = true
                                scope.launch {
                                    if (viewModel.prepareForFinish()) {
                                        stopWorkoutSessionService(context)
                                        onNavigateToFinish()
                                    }
                                    isFinishing = false
                                }
                            },
                        ) { Text(stringResource(R.string.workout_finish)) }
                        IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options)) }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            // M16: mid-session settings access (rest timer default, sounds, …).
                            // Plain navigation — the session is write-through and foregrounded by
                            // the service, and the ViewModel (kept alive beneath Settings) observes
                            // the settings Flow, so edits made there apply live on return.
                            DropdownMenuItem(text = { Text(stringResource(R.string.settings_title)) }, onClick = { menuExpanded = false; onSettingsClick() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.workout_discard)) }, onClick = { menuExpanded = false; showDiscardConfirm = true })
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.supersetSelectionActive) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(modifier = Modifier.fillMaxWidth().padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.routine_builder_superset_banner), modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::cancelSupersetSelection) { Text(stringResource(R.string.action_cancel)) }
                    }
                }
            }
            if (uiState.reorderModeActive) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(modifier = Modifier.fillMaxWidth().padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.routine_builder_reorder_banner), modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::toggleReorderMode) { Text(stringResource(R.string.routine_builder_reorder_done)) }
                    }
                }
            }

            if (!uiState.isLoading) {
                // §5.1.10: "In place of the stopwatch, editable Date & time and Duration rows
                // (same pickers as 5.1.8a)."
                if (uiState.isEditMode) {
                    EditDateDurationRows(
                        startedAtMillis = uiState.editedStartedAtMillis,
                        durationSeconds = uiState.editedDurationSeconds,
                        onOpenDatePicker = { showEditDatePicker = true },
                        onDurationChange = viewModel::updateEditedDuration,
                    )
                }

                val isCircuit = uiState.structure == WorkoutStructure.CIRCUIT

                // M11: in circuit mode the per-exercise cards' rest bar has no single home (a
                // round card holds every exercise), so the countdown + controls dock here instead,
                // pinned above the round list. Same engine, same flow, same controls.
                if (isCircuit && uiState.restExerciseId != null && !uiState.isEditMode) {
                    Column(modifier = Modifier.padding(horizontal = Spacing.md)) {
                        RestTimerBar(
                            remainingMillisFlow = viewModel.restRemainingMillisFlow,
                            onMinus15 = { viewModel.adjustRestTimer(-15) },
                            onPlus15 = { viewModel.adjustRestTimer(15) },
                            onSkip = viewModel::skipRestTimer,
                        )
                    }
                }

                if (isCircuit) {
                    // M11: round-grouped rendering — one card per round, exercises in sequence
                    // order inside it. Grouping is pure (buildCircuitRounds) and never repairs
                    // data: unequal set counts render as inert "—" slots.
                    val rounds = buildCircuitRounds(uiState.exercises)
                    LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                        // Keyed by the round's first surviving set id, not its position: removing
                        // an earlier round must not re-attach later cards' remembered UI state
                        // (open menus, unparsed cell text) to a different round.
                        items(
                            items = rounds,
                            key = { r -> r.entries.firstNotNullOfOrNull { it.set?.id } ?: "round-${r.roundNumber}" },
                        ) { round ->
                            CircuitRoundCard(
                                round = round,
                                viewModel = viewModel,
                                onExerciseClick = onExerciseClick,
                                onOpenReplacePicker = { weId -> replaceTargetId = weId; pickerMode = ExercisePickerMode.REPLACE },
                                onRemoveRound = {
                                    val roundIndex = round.roundNumber - 1
                                    if (viewModel.roundHasLoggedValues(roundIndex)) {
                                        pendingRemoveRoundIndex = roundIndex
                                    } else {
                                        viewModel.removeRound(roundIndex)
                                    }
                                },
                                canRemoveRound = rounds.size > 1,
                                rpeTrackingEnabled = uiState.rpeTrackingEnabled,
                                inlineTimerEnabled = uiState.inlineTimerEnabled,
                                inlineTimerExerciseId = uiState.inlineTimerExerciseId,
                                inlineTimerSetId = uiState.inlineTimerSetId,
                                inlineTimerSecondsFlow = viewModel.inlineTimerSecondsFlow,
                                isEditMode = uiState.isEditMode,
                                plateCalculator = uiState.plateCalculator,
                            )
                        }
                    }

                    // M11: + Add Round replaces per-exercise + Add Set — pinned beside Add Exercise.
                    Row(modifier = Modifier.fillMaxWidth().padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Button(onClick = viewModel::addRound, modifier = Modifier.weight(1f), enabled = uiState.exercises.isNotEmpty()) {
                            Text(stringResource(R.string.workout_add_round))
                        }
                        Button(onClick = { pickerMode = ExercisePickerMode.ADD }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.routine_builder_add_exercise))
                        }
                    }
                    return@Column
                }

                LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                    items(items = uiState.exercises, key = { it.id }) { exercise ->
                        val index = uiState.exercises.indexOf(exercise)
                        WorkoutExerciseCard(
                            exercise = exercise,
                            reorderModeActive = uiState.reorderModeActive,
                            canMoveUp = index > 0,
                            canMoveDown = index < uiState.exercises.lastIndex,
                            onMoveUp = {
                                val ids = uiState.exercises.map { it.id }.toMutableList()
                                ids[index] = ids[index - 1].also { ids[index - 1] = ids[index] }
                                viewModel.reorderExercises(ids)
                            },
                            onMoveDown = {
                                val ids = uiState.exercises.map { it.id }.toMutableList()
                                ids[index] = ids[index + 1].also { ids[index + 1] = ids[index] }
                                viewModel.reorderExercises(ids)
                            },
                            supersetSelectionActive = uiState.supersetSelectionActive,
                            isSupersetSource = exercise.id == uiState.supersetSourceExerciseId,
                            viewModel = viewModel,
                            onExerciseClick = { onExerciseClick(exercise.exerciseId) },
                            onOpenReplacePicker = { replaceTargetId = exercise.id; pickerMode = ExercisePickerMode.REPLACE },
                            rpeTrackingEnabled = uiState.rpeTrackingEnabled,
                            onRpeChange = { setId, rpe -> viewModel.updateRpe(exercise.id, setId, rpe) },
                            showRestTimer = uiState.restExerciseId == exercise.id,
                            restRemainingMillisFlow = viewModel.restRemainingMillisFlow,
                            onRestAdjust = viewModel::adjustRestTimer,
                            onRestSkip = viewModel::skipRestTimer,
                            inlineTimerEnabled = uiState.inlineTimerEnabled,
                            inlineTimerSetId = if (uiState.inlineTimerExerciseId == exercise.id) uiState.inlineTimerSetId else null,
                            inlineTimerSecondsFlow = viewModel.inlineTimerSecondsFlow,
                            onStartInlineTimer = { setId -> viewModel.startInlineTimer(exercise.id, setId) },
                            onStopInlineTimer = { setId -> viewModel.stopInlineTimer(exercise.id, setId) },
                            isEditMode = uiState.isEditMode,
                            plateCalculator = uiState.plateCalculator,
                        )
                    }
                }

                Button(
                    onClick = { pickerMode = ExercisePickerMode.ADD },
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                ) {
                    Text(stringResource(R.string.routine_builder_add_exercise))
                }
            }
        }
    }

    pickerMode?.let { mode ->
        ExercisePickerSheet(
            mode = mode,
            onDismiss = { pickerMode = null; replaceTargetId = null },
            onAddCommitted = { exercises -> viewModel.addExercises(exercises) },
            onExercisePicked = { exercise -> replaceTargetId?.let { viewModel.replaceExercise(it, exercise) } },
            onCreateExercise = onCreateExercise,
        )
    }

    if (showEditDatePicker) {
        // Same UTC-vs-local conversion the finish flow needed (§5.1.8a): the picker speaks
        // UTC-midnight millis while the row renders in the device zone, and mixing the two put
        // backdating on the wrong day for anyone east or west of UTC.
        val zone = remember { ZoneId.systemDefault() }
        val startedAt = uiState.editedStartedAtMillis
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = toDatePickerMillis(startedAt, zone))
        DatePickerDialog(
            onDismissRequest = { showEditDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { picked ->
                        viewModel.updateEditedStartedAt(fromDatePickerMillis(picked, startedAt, zone))
                    }
                    showEditDatePicker = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { showEditDatePicker = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showEditIncompleteConfirm) {
        AlertDialog(
            onDismissRequest = { showEditIncompleteConfirm = false },
            title = {
                val count = viewModel.uncompletedSetCount()
                Text(pluralStringResource(R.plurals.finish_incomplete_title, count, count))
            },
            text = { Text(stringResource(R.string.finish_incomplete_body)) },
            confirmButton = {
                TextButton(onClick = { showEditIncompleteConfirm = false; viewModel.saveEdit() }) {
                    Text(stringResource(R.string.finish_incomplete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditIncompleteConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showDiscardEditConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardEditConfirm = false },
            title = { Text(stringResource(R.string.workout_edit_discard_title)) },
            text = { Text(stringResource(R.string.workout_edit_discard_body)) },
            confirmButton = {
                TextButton(onClick = { showDiscardEditConfirm = false; onExit() }) {
                    Text(stringResource(R.string.workout_edit_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardEditConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    // M4c: no confirm dialog here any more — Finish opens §5.1.8's Save Workout screen, which is
    // itself the review-and-confirm step (and owns the incomplete-sets / no-sets warnings).

    // M11: Remove Round holds logged values somewhere — confirm before deleting the whole slice.
    pendingRemoveRoundIndex?.let { roundIndex ->
        AlertDialog(
            onDismissRequest = { pendingRemoveRoundIndex = null },
            title = { Text(stringResource(R.string.workout_remove_round_title, roundIndex + 1)) },
            text = { Text(stringResource(R.string.workout_remove_round_body)) },
            confirmButton = {
                TextButton(onClick = { pendingRemoveRoundIndex = null; viewModel.removeRound(roundIndex) }) {
                    Text(stringResource(R.string.workout_remove_round))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveRoundIndex = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(stringResource(R.string.workout_discard_confirm_title)) },
            text = { Text(stringResource(R.string.workout_discard_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    scope.launch { viewModel.discard(); stopWorkoutSessionService(context); onDiscarded() }
                }) {
                    Text(stringResource(R.string.workout_discard))
                }
            },
            dismissButton = { TextButton(onClick = { showDiscardConfirm = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Leaf composable (spine rule) — the only thing that recomposes every second is this Text, not the whole TopAppBar. */
@Composable
private fun WorkoutStatsText(elapsedSecondsFlow: Flow<Long>, completedSetCount: Int, totalVolumeKg: Double) {
    val elapsedSeconds by elapsedSecondsFlow.collectAsStateWithLifecycle(0L)
    Text(
        stringResource(R.string.workout_logger_stats, formatElapsed(elapsedSeconds), completedSetCount, formatVolume(totalVolumeKg)),
        style = LogEzMono.dataSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Leaf composable — the compact top-bar rest countdown chip. Hand-built rather than Material3's
 * `AssistChip`: that component's spec padding/min-height read as visually heavy next to
 * [WorkoutStatsText]'s small mono readout and pushed the row wider than the TopAppBar's title slot
 * has room for (Owner: "make it fit").
 */
@Composable
private fun RestTimerChip(restRemainingMillisFlow: Flow<Long?>, modifier: Modifier = Modifier) {
    val remainingMillis by restRemainingMillisFlow.collectAsStateWithLifecycle(null)
    val remainingSeconds = remainingMillis?.let { (it + 999) / 1000 } ?: return
    Surface(
        shape = RoundedCornerShape(Radius.pill),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
        modifier = modifier,
    ) {
        Text(
            "${stringResource(R.string.workout_rest_timer_label)} ${formatElapsed(remainingSeconds)}",
            style = LogEzMono.dataSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        )
    }
}

private fun formatElapsed(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatVolume(kg: Double): String = if (kg == kg.toLong().toDouble()) "${kg.toLong()}kg" else "%.1fkg".format(kg)

/**
 * §5.1.10's replacement for the live stopwatch: the workout's date and duration, both editable.
 * Reuses the finish screen's pickers and its digits-only/length-capped duration handling rather
 * than re-deriving them — the same Int-overflow and display-vs-saved-value divergence would apply.
 */
@Composable
private fun EditDateDurationRows(
    startedAtMillis: Long,
    durationSeconds: Int,
    onOpenDatePicker: () -> Unit,
    onDurationChange: (Int) -> Unit,
) {
    var durationText by rememberSaveable { mutableStateOf((durationSeconds / 60).toString()) }
    Column(modifier = Modifier.padding(horizontal = Spacing.md)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDatePicker).padding(vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.finish_date_time_label), modifier = Modifier.weight(1f))
            Text(formatEditDateTime(startedAtMillis), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.finish_duration_label), modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = durationText,
                onValueChange = { new ->
                    val digits = new.filter { it.isDigit() }.take(5)
                    durationText = digits
                    val minutes = digits.toLongOrNull() ?: 0L
                    if (minutes != durationSeconds / 60L) {
                        onDurationChange((minutes * 60).coerceIn(0L, 99_999L * 60L).toInt())
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                suffix = { Text("min") },
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider()
    }
}

private fun formatEditDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy"))

private fun formatEditDateTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))

private fun formatVolumeShort(kg: Double): String =
    if (kg == kg.toLong().toDouble()) "${kg.toLong()}kg" else "%.1fkg".format(kg)
