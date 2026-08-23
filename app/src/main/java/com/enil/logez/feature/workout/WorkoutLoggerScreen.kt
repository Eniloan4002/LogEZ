package com.enil.logez.feature.workout

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.feature.exercises.ExercisePickerMode
import com.enil.logez.feature.exercises.ExercisePickerSheet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutLoggerScreen(
    onFinished: () -> Unit,
    onDiscarded: () -> Unit,
    onExerciseClick: (exerciseId: String) -> Unit,
    onCreateExercise: (prefillName: String?) -> Unit,
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
    var showFinishConfirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun handleBack() {
        // Live logger data is already write-through persisted — navigating away just leaves it
        // IN_PROGRESS (spine); the service keeps it foregrounded and the Workout tab's mini-bar
        // (§5.1.3) surfaces it globally, so this just exits back to wherever the mini-bar lives.
        onFinished()
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
            if (index >= 0) listState.animateScrollToItem(index)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(uiState.title, style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.clickable { timerMenuExpanded = true }) {
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
                                RestTimerChip(viewModel.restRemainingMillisFlow, modifier = Modifier.padding(start = Spacing.xs))
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
                    TextButton(onClick = { showFinishConfirm = true }) { Text(stringResource(R.string.workout_finish)) }
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options)) }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.workout_discard)) }, onClick = { menuExpanded = false; showDiscardConfirm = true })
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
                            showRestTimer = uiState.restExerciseId == exercise.id,
                            restRemainingMillisFlow = viewModel.restRemainingMillisFlow,
                            onRestAdjust = viewModel::adjustRestTimer,
                            onRestSkip = viewModel::skipRestTimer,
                            inlineTimerEnabled = uiState.inlineTimerEnabled,
                            inlineTimerSetId = if (uiState.inlineTimerExerciseId == exercise.id) uiState.inlineTimerSetId else null,
                            inlineTimerSecondsFlow = viewModel.inlineTimerSecondsFlow,
                            onStartInlineTimer = { setId -> viewModel.startInlineTimer(exercise.id, setId) },
                            onStopInlineTimer = { setId -> viewModel.stopInlineTimer(exercise.id, setId) },
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

    if (showFinishConfirm) {
        AlertDialog(
            onDismissRequest = { showFinishConfirm = false },
            title = { Text(stringResource(R.string.workout_finish_confirm_title)) },
            text = { Text(stringResource(R.string.workout_finish_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showFinishConfirm = false
                    scope.launch { if (viewModel.finish()) { stopWorkoutSessionService(context); onFinished() } }
                }) {
                    Text(stringResource(R.string.workout_finish))
                }
            },
            dismissButton = { TextButton(onClick = { showFinishConfirm = false }) { Text(stringResource(R.string.action_cancel)) } },
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
        style = MaterialTheme.typography.labelSmall,
    )
}

/** Leaf composable — the compact top-bar rest countdown chip. */
@Composable
private fun RestTimerChip(restRemainingMillisFlow: Flow<Long?>, modifier: Modifier = Modifier) {
    val remainingMillis by restRemainingMillisFlow.collectAsStateWithLifecycle(null)
    val remainingSeconds = remainingMillis?.let { (it + 999) / 1000 } ?: return
    AssistChip(
        onClick = {},
        label = { Text("${stringResource(R.string.workout_rest_timer_label)} ${formatElapsed(remainingSeconds)}") },
        modifier = modifier,
    )
}

private fun formatElapsed(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatVolume(kg: Double): String = if (kg == kg.toLong().toDouble()) "${kg.toLong()}kg" else "%.1fkg".format(kg)
