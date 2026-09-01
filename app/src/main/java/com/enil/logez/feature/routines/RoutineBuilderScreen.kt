package com.enil.logez.feature.routines

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.Radius
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.domain.model.WorkoutStructure
import com.enil.logez.feature.exercises.ExercisePickerMode
import com.enil.logez.feature.exercises.ExercisePickerSheet
import java.util.Locale
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
    var showRemoveRoundConfirm by remember { mutableStateOf(false) }

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
                    // M11: structure choice near the title — pickable at create, greyed with a
                    // hint when editing (immutable after creation, like an exercise's type).
                    item {
                        StructureRow(
                            structure = uiState.structure,
                            enabled = !uiState.isEditMode,
                            onSelect = viewModel::setStructure,
                        )
                    }
                    if (uiState.structure == WorkoutStructure.CIRCUIT) {
                        item {
                            RoundsStepperCard(
                                rounds = uiState.rounds,
                                onAddRound = viewModel::addRound,
                                onRemoveRound = {
                                    if (viewModel.lastRoundHasTargets()) showRemoveRoundConfirm = true else viewModel.removeLastRound()
                                },
                            )
                        }
                    }
                    items(items = uiState.exercises, key = { it.id }) { exercise ->
                        val index = uiState.exercises.indexOf(exercise)
                        RoutineExerciseCard(
                            exercise = exercise,
                            isCircuit = uiState.structure == WorkoutStructure.CIRCUIT,
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

    // M11: stepping the round count down discards the last round's targets on every exercise —
    // confirmed only when any of those targets are actually filled in.
    if (showRemoveRoundConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveRoundConfirm = false },
            title = { Text(stringResource(R.string.routine_builder_remove_round_title, uiState.rounds)) },
            text = { Text(stringResource(R.string.routine_builder_remove_round_body)) },
            confirmButton = {
                TextButton(onClick = { showRemoveRoundConfirm = false; viewModel.removeLastRound() }) {
                    Text(stringResource(R.string.routine_builder_remove_round))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveRoundConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/**
 * M11 structure choice — pill/mono-caps selection chips in the v4.0 chip vocabulary
 * (ShareSummarySheet's FormatChip). Greyed with the immutable hint when editing.
 */
@Composable
private fun StructureRow(structure: WorkoutStructure, enabled: Boolean, onSelect: (WorkoutStructure) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                stringResource(R.string.routine_structure_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = Spacing.xs),
            )
            StructureChip(R.string.routine_structure_regular, selected = structure == WorkoutStructure.REGULAR, enabled = enabled) {
                onSelect(WorkoutStructure.REGULAR)
            }
            StructureChip(R.string.routine_structure_circuit, selected = structure == WorkoutStructure.CIRCUIT, enabled = enabled) {
                onSelect(WorkoutStructure.CIRCUIT)
            }
        }
        if (!enabled) {
            Text(
                stringResource(R.string.routine_structure_immutable_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
        }
    }
}

/** Pill/mono-caps selection chip (v4.0 vocabulary — mirrors ShareSummarySheet's FormatChip, plus a disabled state). */
@Composable
private fun StructureChip(@StringRes labelRes: Int, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.pill)
    val primary = MaterialTheme.colorScheme.primary
    val fill = when {
        selected && enabled -> primary
        selected -> primary.copy(alpha = 0.38f)
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .clip(shape)
            .background(fill)
            .let { if (selected) it else it.border(1.dp, MaterialTheme.colorScheme.outline, shape) }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        Text(
            stringResource(labelRes).uppercase(Locale.getDefault()),
            style = LogEzMono.dataSmall.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.08.em,
                color = when {
                    selected -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            ),
        )
    }
}

/** M11: routine-level round count for circuits — the one structural control a circuit template has. */
@Composable
private fun RoundsStepperCard(rounds: Int, onAddRound: () -> Unit, onRemoveRound: () -> Unit) {
    LogEzCard(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.routine_rounds_label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemoveRound, enabled = rounds > 1) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.routine_builder_remove_round))
            }
            Text(rounds.toString(), style = LogEzMono.dataLarge, modifier = Modifier.padding(horizontal = Spacing.sm))
            IconButton(onClick = onAddRound) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.routine_builder_add_round))
            }
        }
    }
}
