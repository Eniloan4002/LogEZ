package com.enil.logez.feature.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.feature.exercises.ExercisePickerMode
import com.enil.logez.feature.exercises.ExercisePickerSheet
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
    var pickerMode by remember { mutableStateOf<ExercisePickerMode?>(null) }
    var replaceTargetId by remember { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showFinishConfirm by remember { mutableStateOf(false) }

    fun handleBack() {
        // Live logger data is already write-through persisted — navigating away just leaves it
        // IN_PROGRESS (spine); a full collapse-to-mini-bar surface is M4b, so for M4a this simply
        // exits back to the Workout tab, resumable via the tab's IN_PROGRESS detection.
        onFinished()
    }
    BackHandler(onBack = ::handleBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(uiState.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.workout_logger_stats, formatElapsed(uiState.elapsedSeconds), uiState.completedSetCount, formatVolume(uiState.totalVolumeKg)),
                            style = MaterialTheme.typography.labelSmall,
                        )
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
                LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)) {
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
                TextButton(onClick = { showFinishConfirm = false; scope.launch { if (viewModel.finish()) onFinished() } }) {
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
                TextButton(onClick = { showDiscardConfirm = false; scope.launch { viewModel.discard(); onDiscarded() } }) {
                    Text(stringResource(R.string.workout_discard))
                }
            },
            dismissButton = { TextButton(onClick = { showDiscardConfirm = false }) { Text(stringResource(R.string.action_cancel)) } },
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
