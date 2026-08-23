package com.enil.logez.feature.routines

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
fun RoutineBuilderScreen(
    onBack: () -> Unit,
    onSaved: (routineId: String) -> Unit,
    onExerciseClick: (exerciseId: String) -> Unit,
    onCreateExercise: (prefillName: String?) -> Unit,
    viewModel: RoutineBuilderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var pickerMode by remember { mutableStateOf<ExercisePickerMode?>(null) }
    var replaceTargetId by remember { mutableStateOf<String?>(null) }
    var restTimerTargetId by remember { mutableStateOf<String?>(null) }

    fun handleBack() {
        if (uiState.isDirty) showDiscardConfirm = true else onBack()
    }

    // The system back gesture/button bypasses the TopAppBar's arrow entirely (Nav's default pop),
    // so the discard-confirm needs its own interception here too — not just on the on-screen icon.
    BackHandler(onBack = ::handleBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = uiState.title,
                        onValueChange = viewModel::onTitleChange,
                        placeholder = { Text(stringResource(R.string.routine_builder_title_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = ::handleBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = { scope.launch { viewModel.save()?.let(onSaved) } }, enabled = uiState.canSave) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.supersetSelectionActive) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.routine_builder_superset_banner), modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::cancelSupersetSelection) { Text(stringResource(R.string.action_cancel)) }
                    }
                }
            }
            if (uiState.reorderModeActive) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.routine_builder_reorder_banner), modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::toggleReorderMode) { Text(stringResource(R.string.routine_builder_reorder_done)) }
                    }
                }
            }

            if (!uiState.isLoading) {
                LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                    items(items = uiState.exercises, key = { it.id }) { exercise ->
                        val index = uiState.exercises.indexOf(exercise)
                        RoutineExerciseCard(
                            exercise = exercise,
                            defaultRestTimerSeconds = uiState.defaultRestTimerSeconds,
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
                            onRestTimerClick = { restTimerTargetId = exercise.id },
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

    restTimerTargetId?.let { targetId ->
        val target = uiState.exercises.find { it.id == targetId }
        if (target != null) {
            RestTimerPickerSheet(
                currentSeconds = target.restTimerSeconds,
                defaultSeconds = uiState.defaultRestTimerSeconds,
                onSelect = { viewModel.updateRestTimer(targetId, it) },
                onDismiss = { restTimerTargetId = null },
            )
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(stringResource(R.string.routine_builder_discard_title)) },
            text = { Text(stringResource(R.string.routine_builder_discard_body)) },
            confirmButton = {
                TextButton(onClick = { showDiscardConfirm = false; onBack() }) {
                    Text(stringResource(R.string.routine_builder_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
